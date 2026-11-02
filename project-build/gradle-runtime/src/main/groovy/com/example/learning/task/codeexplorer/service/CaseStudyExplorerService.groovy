package com.example.learning.task.codeexplorer.service

import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * STEP 8 completion gate: deterministic, read-only projections of Java source.
 * Runtime Java is never edited. A scope contains exactly one controller and
 * service endpoint and the explicit group/case Java packages.
 */
class CaseStudyExplorerService {
    private static final String CONFIG = 'content/code-explorer/casestudy.yml'
    private final JavaParser parser

    CaseStudyExplorerService() {
        def levels = ParserConfiguration.LanguageLevel.values().toList()
        def java21 = levels.find { it.name() == 'JAVA_21' } ?:
                levels.find { it.name() == 'BLEEDING_EDGE' } ?: levels.last()
        parser = new JavaParser(new ParserConfiguration().setLanguageLevel(java21))
    }

    Map execute(File repo, File moduleDir, boolean apply, boolean publish,
                Integer requestedGroup = null, Integer requestedCase = null) {
        File root = repo.canonicalFile
        File module = moduleDir.canonicalFile
        if (!module.toPath().startsWith(new File(root, 'module').canonicalFile.toPath()) ||
                !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('STEP8_EXPLORER requires a real Gradle module.')
        }
        File settings = new File(module, CONFIG)
        if (!settings.isFile() || settings.getText('UTF-8').replace('\r\n','\n').trim() !=
                'version: 1\ntype: CASE_STUDY_EXPLORER') {
            throw new GradleException('STEP8_EXPLORER requires canonical ' + CONFIG)
        }
        if ((requestedGroup == null) != (requestedCase == null) ||
                (requestedGroup != null && (requestedGroup < 1 || requestedCase < 1))) {
            throw new GradleException('STEP8_EXPLORER groupID and caseIndex must both be positive or both absent.')
        }
        if (publish && requestedGroup != null) {
            throw new GradleException('STEP8_EXPLORER pilot cannot overwrite published full Case Study material.')
        }
        Map master = new JsonSlurper().parse(new File(module, 'master.json')) as Map
        String serviceName = master.SERVICE_NAME?.VALUE?.toString()
        String packageName = master.JAVA_BASE_PACKAGE?.VALUE?.toString()
        if (!(serviceName ==~ /[A-Z0-9_-]{1,128}/) ||
                !(packageName ==~ /[a-zA-Z_$][\w$]*(\.[a-zA-Z_$][\w$]*)*/)) {
            throw new GradleException('Invalid STEP8_EXPLORER module package or SERVICE_NAME.')
        }
        String prefix = packageName.replace('.', '/') + '/casestudy/'
        File javaRoot = new File(module, 'src/main/java')
        File content = new File(module, 'content/casestudy/vi/casestudy.yml')
        if (!content.isFile()) throw new GradleException('STEP8_EXPLORER Case Study YAML missing.')
        def options = new LoaderOptions()
        options.allowDuplicateKeys = false
        Map document = new Yaml(new SafeConstructor(options)).load(content.getText('UTF-8')) as Map
        if (!(document?.caseStudy instanceof List) || document.caseStudy.isEmpty()) {
            throw new GradleException('STEP8_EXPLORER Case Study groups missing.')
        }
        List<Map> scopes = []
        int groupOrder = 0
        document.caseStudy.each { Object entry ->
            groupOrder++
            Map group = entry as Map
            if (group.groupID != groupOrder || !(group.cases instanceof List) ||
                    group.cases.isEmpty()) {
                throw new GradleException('STEP8_EXPLORER invalid Case Study group ' + groupOrder)
            }
            group.cases.eachWithIndex { Object item, int offset ->
                if (!(item instanceof Map)) {
                    throw new GradleException('STEP8_EXPLORER invalid case ' + groupOrder + '/' + (offset + 1))
                }
                scopes.add([groupID:groupOrder, caseIndex:offset + 1])
            }
        }
        if (requestedGroup != null) {
            scopes = scopes.findAll {
                it.groupID == requestedGroup && it.caseIndex == requestedCase
            }
            if (scopes.size() != 1) {
                throw new GradleException('STEP8_EXPLORER unknown groupID/caseIndex.')
            }
        }
        Map<String,byte[]> output = new TreeMap<>()
        List<Map> entries = []
        scopes.each { Map scope ->
            int g = scope.groupID as int
            int c = scope.caseIndex as int
            String scopeDir = "groups/${g}/cases/${c}/"
            String controllerPath = prefix + "controller/Group${g}Controller.java"
            String servicePath = prefix + "service/Group${g}Service.java"
            Map<String,byte[]> sources = new TreeMap<>()
            sources[controllerPath] = filtered(javaRoot, controllerPath,
                    "Group${g}Controller", "executeCase${c}")
            sources[servicePath] = filtered(javaRoot, servicePath,
                    "Group${g}Service", "executeCase${c}")
            File base = new File(javaRoot, prefix)
            if (!base.isDirectory()) throw new GradleException('STEP8_EXPLORER Java source missing: ' + base)
            def stream = Files.walk(base.toPath())
            try {
                stream.forEach { Path path ->
                    if (Files.isSymbolicLink(path)) {
                        throw new GradleException('STEP8_EXPLORER source symlink forbidden: ' + path)
                    }
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
                            !path.fileName.toString().endsWith('.java')) return
                    String relative = base.toPath().relativize(path).toString().replace('\\','/')
                    if (relative ==~ /[^\/]+\/group${g}\/case${c}\/(?:[^\/]+\/)*[^\/]+\.java/) {
                        sources[prefix + relative] = Files.readAllBytes(path)
                    }
                }
            } finally { stream.close() }
            // Shared JVM infrastructure is shown only for cases that invoke it.
            if (new String(sources[servicePath], StandardCharsets.UTF_8).contains('CaseStudyJvmIsolation')) {
                ['CaseStudyJvmIsolation.java', 'CaseStudyJvmBootstrap.java'].each { String name ->
                    String path = prefix + 'runtime/' + name
                    sources[path] = checkedRead(javaRoot, path)
                }
            }
            Map bundle = CodeExplorerMaterialService.buildFromSources(
                    "casestudy-g${g}-c${c}", sources)
            if (!(bundle.outputs['index.json'] as byte[])) {
                throw new GradleException('STEP8_EXPLORER failed generating scope ' + g + '/' + c)
            }
            Map navigation = new JsonSlurper().parseText(new String(
                    bundle.outputs['references.json'] as byte[], StandardCharsets.UTF_8)) as Map
            List calls = (navigation.references as List).findAll {
                it.file == controllerPath && it.kind == 'METHOD_CALL' &&
                        it.text == "executeCase${c}"
            }
            if (calls.size() != 1 || calls[0].status != 'RESOLVED' ||
                    calls[0].targets.size() != 1 ||
                    !(calls[0].targets[0] as String).startsWith(
                            packageName + ".casestudy.service.Group${g}Service#executeCase${c}(")) {
                throw new GradleException('STEP8_EXPLORER Controller -> Service navigation unresolved: ' +
                        g + '/' + c)
            }
            (bundle.outputs as Map<String,byte[]>).each { String path, byte[] data ->
                output[scopeDir + path] = data
            }
            entries.add([groupID:g, caseIndex:c, path:scopeDir + 'index.json',
                         files:bundle.fileCount])
        }
        boolean complete = requestedGroup == null
        output['index.json'] = CodeExplorerMaterialService.json([
                version:1, type:'CASE_STUDY_EXPLORER', complete:complete,
                cases:entries
        ])
        File local = new File(module, requestedGroup == null
                ? 'build/code-explorer/casestudy'
                : 'build/code-explorer/casestudy-pilot')
        File target = publish ? new File(root,
                "build/learning-data/java/module/${serviceName}/code-explorer/casestudy") : local
        if (apply) {
            CodeExplorerMaterialService.writeBundle(
                    publish ? root.toPath() : module.toPath(), target, output)
            if (publish) {
                CodeExplorerMaterialService.writeBundle(module.toPath(), local, output)
            }
        }
        return [status:apply ? 'APPLIED' : 'DRY_RUN', module:serviceName,
                complete:complete, cases:entries.size(), files:entries.sum { it.files } ?: 0,
                output:target.absolutePath]
    }

    private byte[] filtered(File root, String path, String expectedClass, String methodName) {
        byte[] source = checkedRead(root, path)
        CompilationUnit unit = parse(new String(source, StandardCharsets.UTF_8), path)
        List<ClassOrInterfaceDeclaration> classes = unit.findAll(ClassOrInterfaceDeclaration)
                .findAll { it.parentNode.orElse(null) == unit && it.nameAsString == expectedClass }
        if (classes.size() != 1 || unit.types.size() != 1) {
            throw new GradleException('STEP8_EXPLORER expected one top-level class in ' + path)
        }
        ClassOrInterfaceDeclaration clazz = classes[0]
        List<MethodDeclaration> matches = clazz.methods.findAll { it.nameAsString == methodName }
        if (matches.size() != 1) {
            throw new GradleException('STEP8_EXPLORER expected unique ' + methodName + ' in ' + path)
        }
        if (expectedClass.endsWith('Controller')) {
            String group = expectedClass.replace('Group', '').replace('Controller', '')
            String caseId = methodName.replace('executeCase', '')
            if (!clazz.annotations.any { it.nameAsString == 'RequestMapping' &&
                    it.toString().contains('"/case-studies/group/' + group + '"') } ||
                    !matches[0].annotations.any { it.nameAsString == 'GetMapping' &&
                            it.toString().contains('"/case/' + caseId + '/execute"') }) {
                throw new GradleException('STEP8_EXPLORER Controller HTTP mapping mismatch: ' + path)
            }
        }
        clazz.methods.toList().each { MethodDeclaration method ->
            if (method != matches[0]) method.remove()
        }
        // Imports are only retained when referenced by the projected AST.
        def imports = unit.imports.toList()
        imports.each { it.remove() }
        String body = unit.toString()
        imports.each { imp ->
            String shortName = imp.name.identifier
            if (imp.isAsterisk || imp.isStatic ||
                    (body =~ /(?<![\w$])${java.util.regex.Pattern.quote(shortName)}(?![\w$])/).find()) {
                unit.addImport(imp.clone())
            }
        }
        byte[] projected = (unit.toString().trim() + '\n').getBytes(StandardCharsets.UTF_8)
        parse(new String(projected, StandardCharsets.UTF_8), path)
        return projected
    }

    private CompilationUnit parse(String code, String path) {
        def parsed = parser.parse(code)
        if (!parsed.successful || !parsed.result.present) {
            throw new GradleException('STEP8_EXPLORER cannot parse ' + path + ': ' + parsed.problems)
        }
        return parsed.result.get()
    }

    private static byte[] checkedRead(File root, String path) {
        File file = new File(root, path)
        Path base = root.canonicalFile.toPath()
        Path target = file.toPath().toAbsolutePath().normalize()
        if (!target.startsWith(base) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new GradleException('STEP8_EXPLORER Java source missing or unsafe: ' + path)
        }
        Path current = base
        for (Path part : base.relativize(target)) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) {
                throw new GradleException('STEP8_EXPLORER source symlink forbidden: ' + path)
            }
        }
        return Files.readAllBytes(target)
    }
}
