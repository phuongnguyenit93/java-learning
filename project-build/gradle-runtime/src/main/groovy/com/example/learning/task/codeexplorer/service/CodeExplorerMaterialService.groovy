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
import org.gradle.api.GradleException

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
    /** STEP 8 engine: index exactly the case-scoped Java bytes shown in Monaco.
     * The old module-wide material.yml and Swagger API mapping pipeline is retired.
     */
    static Map buildFromSources(String materialId, Map<String,byte[]> sources) {
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
        sources.each { String name, byte[] bytes ->
            try {
                StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes))
            } catch (Exception error) {
                throw new GradleException('Non-UTF8 Java file: ' + name, error)
            }
            def result = parser.parse(new String(bytes, StandardCharsets.UTF_8))
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
        Map index = [version:1, type:'CODE_EXPLORER', id:materialId, language:'java',
                     files:files, tree:buildTree(files), symbolCount:symbols.size(),
                     referenceCount:references.size(),
                     resolvedReferences:references.count { it.status == 'RESOLVED' },
                     unresolvedReferences:references.count { it.status != 'RESOLVED' },
                     symbols:'symbols.json', references:'references.json']
        assets['index.json'] = json(index)
        assets['symbols.json'] = json([version:1, symbols:symbols])
        assets['references.json'] = json([version:1, references:references])
        return [outputs:assets, fileCount:files.size(), symbolCount:symbols.size(),
                resolved:index.resolvedReferences, unresolved:index.unresolvedReferences]
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

    static byte[] json(Object value) {
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

    /**
     * Only legacy V1 files are removed when their module no longer opts in.
     * Preserve the sibling casestudy/ subtree owned by STEP 8 Code Explorer.
     */
    static void cleanUnclaimedLegacyBundles(File repo, Set<String> activeRoutes) {
        File canonicalRepo = repo.canonicalFile
        Path root = canonicalRepo.toPath()
        File modules = new File(canonicalRepo, 'build/learning-data/java/module')
        if (!modules.isDirectory()) return
        modules.listFiles()?.findAll { it.isDirectory() && !activeRoutes.contains(it.name) }
                ?.each { File module ->
            File explorer = new File(module, 'code-explorer')
            if (!explorer.isDirectory()) return
            ['index.json', 'symbols.json', 'references.json', 'api-map.json', 'files'].each {
                String name ->
                Path owned = new File(explorer, name).toPath()
                safe(root, owned)
                if (!Files.exists(owned, LinkOption.NOFOLLOW_LINKS)) return
                if (Files.isDirectory(owned, LinkOption.NOFOLLOW_LINKS)) {
                    if (name != 'files') {
                        throw new GradleException('Unexpected legacy Explorer directory: ' + owned)
                    }
                    def walk = Files.walk(owned)
                    List<Path> paths = []
                    try {
                        walk.forEach { Path path ->
                            safe(root, path)
                            if (Files.isSymbolicLink(path)) {
                                throw new GradleException('Legacy Explorer symlink forbidden: ' + path)
                            }
                            paths.add(path)
                        }
                    } finally { walk.close() }
                    paths.sort { a,b -> b.nameCount <=> a.nameCount }
                    paths.each { Files.delete(it) }
                } else {
                    if (!Files.isRegularFile(owned, LinkOption.NOFOLLOW_LINKS)) {
                        throw new GradleException('Unexpected Explorer output: ' + owned)
                    }
                    Files.delete(owned)
                }
            }
        }
    }

    static void writeBundle(Path root, File directory, Map<String,byte[]> output) {
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
