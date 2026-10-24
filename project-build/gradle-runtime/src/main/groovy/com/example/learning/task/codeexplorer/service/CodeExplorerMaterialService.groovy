package com.example.learning.task.codeexplorer.service

import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.Range
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.ThisExpr
import com.github.javaparser.ast.type.ClassOrInterfaceType
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * V1: static portable source bundle, safe direct symbol navigation only.
 * No Spring Bean, DI runtime, polymorphic dispatch or reflection inference.
 */
class CodeExplorerMaterialService {
    private static final int FILE_LIMIT = 1000
    private static final long BYTE_LIMIT = 512 * 1024

    Map execute(File repo, File moduleDir, boolean apply, boolean publish) {
        File root = repo.canonicalFile
        File module = moduleDir.canonicalFile
        Path modules = new File(root, 'module').canonicalFile.toPath()
        if (!module.toPath().startsWith(modules) || module.toPath() == modules ||
                !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('Code Explorer requires one real module.')
        }
        File settings = new File(module, 'content/code-explorer/material.yml')
        safe(module.toPath(), settings.toPath())
        if (!settings.isFile()) throw new GradleException('Missing Code Explorer material.yml.')
        Map cfg = yaml(settings)
        if (cfg.keySet() != (['version','type','id','language','sourceRoot','include',
                              'apiMapping'] as Set) ||
                cfg.version != 1 || cfg.type != 'CODE_EXPLORER' ||
                cfg.language != 'java' || cfg.sourceRoot != 'src/main/java' ||
                !(cfg.id instanceof String) ||
                !(cfg.id ==~ /[a-z0-9][a-z0-9_-]{0,63}/) ||
                !(cfg.include instanceof List) || cfg.include.isEmpty() ||
                cfg.include.size() > 30 ||
                cfg.apiMapping != [source: 'SWAGGER']) {
            throw new GradleException('Invalid V1 Code Explorer material.yml.')
        }
        File sourceRoot = new File(module, cfg.sourceRoot as String)
        if (!sourceRoot.isDirectory()) throw new GradleException('Missing Java sourceRoot.')
        List<String> patterns = (cfg.include as List).collect { Object item ->
            if (!(item instanceof String) ||
                    !(item ==~ /[A-Za-z0-9_*?\/.-]+/) ||
                    item.contains('..') || !item.endsWith('.java')) {
                throw new GradleException('Unsafe Code Explorer include pattern: ' + item)
            }
            return item
        }
        def matchers = patterns.collect {
            sourceRoot.toPath().fileSystem.getPathMatcher('glob:' + it)
        }
        List<File> selected = []
        def stream = Files.walk(sourceRoot.toPath())
        try {
            stream.forEach { Path path ->
                if (Files.isSymbolicLink(path)) {
                    throw new GradleException('Source symlink forbidden: ' + path)
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return
                Path relative = sourceRoot.toPath().relativize(path)
                if (path.fileName.toString().endsWith('.java') &&
                        matchers.any { it.matches(relative) }) {
                    if (Files.size(path) > BYTE_LIMIT) {
                        throw new GradleException('Code Explorer source too large: ' + path)
                    }
                    selected.add(path.toFile())
                }
            }
        } finally { stream.close() }
        selected.sort { File a, File b ->
            slash(sourceRoot.toPath().relativize(a.toPath())) <=>
                    slash(sourceRoot.toPath().relativize(b.toPath()))
        }
        if (selected.isEmpty() || selected.size() > FILE_LIMIT) {
            throw new GradleException('Code Explorer requires 1..1000 selected Java files.')
        }

        Map bundle = build(cfg, sourceRoot, selected,
                new File(module, 'src/main/resources/swagger/vi/api-descriptions.yml'))
        Map master = new JsonSlurper().parse(new File(module, 'master.json')) as Map
        String route = master.SERVICE_NAME?.VALUE?.toString()
        if (!(route ==~ /[A-Za-z0-9_-]{1,128}/)) {
            throw new GradleException('Invalid module SERVICE_NAME route.')
        }
        File local = new File(module, 'build/code-explorer')
        File portal = new File(root, 'build/learning-data/java/module/' +
                route + '/code-explorer')
        if (apply) {
            writeBundle(module.toPath(), local, bundle.outputs as Map)
            if (publish) writeBundle(root.toPath(), portal, bundle.outputs as Map)
        }
        return [status: apply ? 'APPLIED' : 'DRY_RUN',
                module: slash(root.toPath().relativize(module.toPath())),
                files: bundle.fileCount, symbols: bundle.symbolCount,
                resolved: bundle.resolved, unresolved: bundle.unresolved,
                apis: bundle.apiCount,
                output: (publish ? portal : local).absolutePath]
    }

    private static Map build(Map cfg, File sourceRoot, List<File> selected, File swagger) {
        // Gradle 8.x includes an older JavaParser in its own classloader.
        // Avoid a hard JAVA_21 enum reference when the Gradle-bundled parser
        // shadows the newer declared dependency.
        def levels = ParserConfiguration.LanguageLevel.values().toList()
        def compatible = levels.find { it.name() == 'JAVA_21' } ?:
                levels.find { it.name() == 'BLEEDING_EDGE' } ?: levels.last()
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(compatible))
        List<Map> files = []
        List<Map> types = []
        List<Map> symbols = []
        Map<String,byte[]> assets = new TreeMap<>()
        selected.each { File f ->
            String name = slash(sourceRoot.toPath().relativize(f.toPath()))
            byte[] bytes = Files.readAllBytes(f.toPath())
            try {
                StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes))
            } catch (Exception error) {
                throw new GradleException('Non-UTF8 Java file: ' + name, error)
            }
            def result = parser.parse(f)
            if (!result.successful || !result.result.present) {
                throw new GradleException('Cannot parse ' + name + ': ' + result.problems)
            }
            def unit = result.result.get()
            String pkg = unit.packageDeclaration.map { it.nameAsString }.orElse('')
            Map imports = [:]
            unit.imports.findAll { !it.isAsterisk && !it.isStatic }.each { imp ->
                imports[imp.name.identifier] = imp.nameAsString
            }
            files.add([path: name, bytes: bytes.length, sha256: sha(bytes)])
            assets['files/' + name] = bytes
            unit.findAll(TypeDeclaration).each { TypeDeclaration declaration ->
                String fqcn = typeName(declaration, pkg)
                types.add([fqcn: fqcn, name: declaration.nameAsString, file: name,
                           declaration: declaration, imports: imports, pkg: pkg])
                symbols.add(position(fqcn, 'TYPE', declaration.nameAsString, name,
                        declaration.name.range.orElse(null)))
                declaration.members.findAll { it instanceof MethodDeclaration }.each {
                    MethodDeclaration method = (MethodDeclaration) it
                    String id = methodId(fqcn, method)
                    symbols.add(position(id, 'METHOD', method.nameAsString, name,
                            method.name.range.orElse(null)) +
                            [owner: fqcn, arity: method.parameters.size(),
                             signature: method.signature.asString()])
                }
            }
        }
        Map<String,Map> typeIndex = types.collectEntries { [(it.fqcn): it] }
        Map<String,List<Map>> methodIndex = symbols.findAll { it.kind == 'METHOD' }
                .groupBy { it.owner + '#' + it.name + '/' + it.arity }
        List<Map> references = []
        types.each { Map owner ->
            TypeDeclaration declaration = owner.declaration as TypeDeclaration
            declaration.findAll(MethodCallExpr).each { MethodCallExpr call ->
                if (call.findAncestor(TypeDeclaration).orElse(null) != declaration) return
                String type = receiver(call, owner, typeIndex)
                List<Map> candidates = type ?
                        (methodIndex[type + '#' + call.nameAsString + '/' +
                                call.arguments.size()] ?: []) : []
                references.add(reference(owner.file as String, 'METHOD_CALL',
                        call.nameAsString, call.name.range.orElse(null), candidates))
            }
            declaration.findAll(ClassOrInterfaceType).each { ClassOrInterfaceType use ->
                if (use.findAncestor(TypeDeclaration).orElse(null) != declaration) return
                String type = lookup(use.nameAsString, owner, typeIndex)
                List<Map> candidates = type ?
                        symbols.findAll { it.kind == 'TYPE' && it.id == type } : []
                references.add(reference(owner.file as String, 'TYPE_USE',
                        use.nameAsString, use.name.range.orElse(null), candidates))
            }
        }
        symbols.sort { a,b -> a.id <=> b.id }
        references.sort { a,b ->
            (a.file <=> b.file) ?: (a.line <=> b.line) ?:
                    (a.column <=> b.column) ?: (a.kind <=> b.kind)
        }
        List<Map> endpoints = apiEntries(swagger, typeIndex, methodIndex)
        Map index = [version:1, type:'CODE_EXPLORER', id:cfg.id, language:'java',
                     files:files, tree:buildTree(files), symbolCount:symbols.size(),
                     referenceCount:references.size(),
                     resolvedReferences:references.count { it.status == 'RESOLVED' },
                     unresolvedReferences:references.count { it.status != 'RESOLVED' },
                     apiCount:endpoints.size(), symbols:'symbols.json',
                     references:'references.json', apiMap:'api-map.json']
        assets['index.json'] = json(index)
        assets['symbols.json'] = json([version:1, symbols:symbols])
        assets['references.json'] = json([version:1, references:references])
        assets['api-map.json'] = json([version:1, endpoints:endpoints])
        return [outputs:assets, fileCount:files.size(), symbolCount:symbols.size(),
                resolved:index.resolvedReferences, unresolved:index.unresolvedReferences,
                apiCount:endpoints.size()]
    }

    private static String receiver(MethodCallExpr call, Map owner, Map types) {
        def scope = call.scope.orElse(null)
        if (scope == null || scope instanceof ThisExpr) return owner.fqcn
        if (!(scope instanceof NameExpr)) return null
        String name = scope.nameAsString
        String declarationType = null
        def method = call.findAncestor(MethodDeclaration).orElse(null)
        if (method != null) {
            def parameter = method.parameters.find { it.nameAsString == name }
            if (parameter != null) declarationType = parameter.type.asString()
            if (declarationType == null) {
                def locals = method.findAll(VariableDeclarator).findAll {
                    it.nameAsString == name && it.range.present && call.range.present &&
                            it.range.get().begin.line <= call.range.get().begin.line
                }
                if (locals.size() == 1) declarationType = locals[0].type.asString()
            }
        }
        if (declarationType == null) {
            List fieldTypes = (owner.declaration as TypeDeclaration).members
                    .findAll { it instanceof com.github.javaparser.ast.body.FieldDeclaration }
                    .collectMany { it.variables.findAll { v -> v.nameAsString == name }
                            .collect { v -> v.type.asString() } }
            if (fieldTypes.size() == 1) declarationType = fieldTypes[0] as String
        }
        return lookup(declarationType ?: name, owner, types)
    }

    private static String lookup(String raw, Map owner, Map types) {
        if (!raw) return null
        String name = raw.replaceAll(/<.*$/, '').replaceAll(/\[\]$/, '').trim()
        if (types.containsKey(name)) return name
        String imported = (owner.imports as Map)[name]
        if (imported && types.containsKey(imported)) return imported
        String local = (owner.pkg ? owner.pkg + '.' : '') + name
        if (types.containsKey(local)) return local
        // A nested class or unique simple name is safe only without collision.
        List<String> found = types.keySet().findAll {
            it == name || it.endsWith('.' + name)
        }.toList()
        return found.size() == 1 ? found[0] : null
    }

    private static List<Map> apiEntries(File swagger, Map types, Map methods) {
        if (!swagger.isFile()) return []
        List<Map> result = []
        yaml(swagger).each { String controller, Object methodsNode ->
            if (!(methodsNode instanceof Map)) return
            (methodsNode as Map).each { String signature, Object item ->
                if (!(item instanceof Map)) return
                Map api = item as Map
                Map owner = types.values().find {
                    it.file == api.path && it.name == controller
                }
                if (owner == null) return // only material-owned controllers
                int count = api.params instanceof List ? api.params.size() : -1
                List<Map> candidates = count >= 0 ?
                        (methods[owner.fqcn + '#' + api.methodName + '/' + count] ?: []) : []
                List<String> targets = candidates.collect { it.id }.sort()
                List verbs = api.httpMethods instanceof List ? api.httpMethods : []
                List urls = api.mappingPaths instanceof List ? api.mappingPaths : []
                verbs.each { verb ->
                    urls.each { path ->
                        result.add([id:controller + '#' + signature + ':' + verb + ':' + path,
                                    controller:controller, signature:signature, httpMethod:verb,
                                    path:path, file:api.path,
                                    status:targets.size() == 1 ? 'RESOLVED' :
                                            (targets.size() > 1 ?
                                                    'MULTIPLE_TARGETS' : 'UNRESOLVED'),
                                    targets:targets])
                    }
                }
            }
        }
        result.sort { a,b -> a.id <=> b.id }
        return result
    }

    private static Map position(String id, String kind, String name, String file, Range pos) {
        if (pos == null) throw new GradleException('Missing Java position: ' + id)
        return [id:id,kind:kind,name:name,file:file,line:pos.begin.line,
                column:pos.begin.column,endLine:pos.end.line,endColumn:pos.end.column]
    }

    private static Map reference(String file, String kind, String name, Range pos,
                                 List<Map> candidates) {
        if (pos == null) throw new GradleException('Missing Java reference position.')
        List<String> targetIds = candidates.collect { it.id }.sort()
        String status = targetIds.size() == 1 ? 'RESOLVED' :
                (targetIds.size() > 1 ? 'MULTIPLE_TARGETS' : 'UNRESOLVED')
        return [file:file,kind:kind,text:name,line:pos.begin.line,
                column:pos.begin.column,endLine:pos.end.line,endColumn:pos.end.column,
                status:status,targets:targetIds]
    }

    private static String typeName(TypeDeclaration type, String pkg) {
        List<String> nesting = [type.nameAsString]
        def parent = type.findAncestor(TypeDeclaration).orElse(null)
        while (parent != null) {
            nesting.add(0,parent.nameAsString)
            parent = parent.findAncestor(TypeDeclaration).orElse(null)
        }
        return (pkg ? pkg + '.' : '') + nesting.join('.')
    }

    private static String methodId(String owner, MethodDeclaration method) {
        return owner + '#' + method.nameAsString + '(' +
                method.parameters.collect { it.type.asString() }.join(',') + ')'
    }

    private static List<Map> buildTree(List<Map> files) {
        Map<String,Map> nodes = [:]
        files.each { Map item ->
            List<String> segments = (item.path as String).split('/') as List<String>
            Map<String,Map> current = nodes
            String prefix = ''
            segments.eachWithIndex { String segment, int i ->
                prefix = prefix ? prefix + '/' + segment : segment
                if (!current.containsKey(segment)) {
                    current[segment] = [
                            name:segment,
                            path:prefix,
                            type:(i == segments.size() - 1 ? 'FILE' : 'DIRECTORY'),
                            children:[:]
                    ]
                }
                current = current[segment].children as Map<String,Map>
            }
        }
        return treeNodes(nodes)
    }

    private static List<Map> treeNodes(Map<String,Map> current) {
        current.values().sort { a,b ->
            (a.type == b.type ? 0 : (a.type == 'DIRECTORY' ? -1 : 1)) ?:
                    (a.name <=> b.name)
        }.collect { Map node ->
            Map<String,Map> children = node.children as Map<String,Map>
            return [name:node.name, path:node.path, type:node.type,
                    children:treeNodes(children)]
        }
    }

    private static Map yaml(File file) {
        try {
            LoaderOptions opts = new LoaderOptions()
            opts.allowDuplicateKeys = false
            def result = new Yaml(new SafeConstructor(opts)).load(file.getText('UTF-8'))
            if (!(result instanceof Map)) throw new GradleException('Expected YAML map.')
            return result as Map
        } catch (Exception ex) {
            throw new GradleException('Invalid Code Explorer YAML: ' + file, ex)
        }
    }

    private static byte[] json(Object value) {
        return (JsonOutput.prettyPrint(JsonOutput.toJson(value)) + '\n')
                .getBytes(StandardCharsets.UTF_8)
    }

    private static String sha(byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }

    private static String slash(Path value) {
        return value.toString().replace(File.separatorChar, '/' as char)
    }

    private static void safe(Path root, Path file) {
        Path base = root.toAbsolutePath().normalize()
        Path target = file.toAbsolutePath().normalize()
        if (!target.startsWith(base) || target == base) {
            throw new GradleException('Code Explorer path escapes root: ' + file)
        }
        Path current = base
        for (Path part : base.relativize(target)) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) {
                throw new GradleException('Symlink forbidden: ' + current)
            }
        }
    }

    private static void writeBundle(Path root, File directory, Map<String,byte[]> output) {
        safe(root,directory.toPath())
        Files.createDirectories(directory.toPath())
        output.each { String relative, byte[] data ->
            File file = new File(directory,relative)
            safe(root,file.toPath())
            Files.createDirectories(file.parentFile.toPath())
            Path temporary = Files.createTempFile(file.parentFile.toPath(),
                    '.code-explorer-', '.tmp')
            try {
                Files.write(temporary,data)
                try {
                    Files.move(temporary,file.toPath(),StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING)
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary,file.toPath(),StandardCopyOption.REPLACE_EXISTING)
                }
            } finally { Files.deleteIfExists(temporary) }
            if (!Arrays.equals(Files.readAllBytes(file.toPath()),data)) {
                throw new GradleException('Code Explorer output readback failed: ' + relative)
            }
        }
        // Remove stale files only under the owned material directory.
        def stream = Files.walk(directory.toPath())
        try {
            List<Path> all = []
            stream.forEach { all.add(it) }
            all.sort { a,b -> b.nameCount <=> a.nameCount }
            all.each { Path path ->
                if (Files.isSymbolicLink(path)) {
                    throw new GradleException('Output symlink forbidden: ' + path)
                }
                if (Files.isRegularFile(path) &&
                        !output.containsKey(slash(directory.toPath().relativize(path)))) {
                    Files.delete(path)
                } else if (Files.isDirectory(path) && path != directory.toPath()) {
                    def children = Files.list(path)
                    try {
                        if (!children.findAny().present) Files.delete(path)
                    } finally { children.close() }
                }
            }
        } finally { stream.close() }
    }
}
