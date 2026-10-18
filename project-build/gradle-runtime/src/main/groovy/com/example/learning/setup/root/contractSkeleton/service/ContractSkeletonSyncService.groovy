package com.example.learning.setup.root.contractSkeleton.service

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Project-level materialization of the *authoring* subset of learning-contract.
 * This runs when Gradle configures the root build (including IDE Gradle Sync).
 * It neither discovers modules nor writes anything below module/.
 */
class ContractSkeletonSyncService {
    static final String GENERATED_HEADER = '# <learning-contract-skeleton>'

    // Explicit allowlist: these schemas describe authored YAML sources.
    // Knowledge/Video/API/Manifest are JSON projections, not equivalent
    // Markdown/YAML authoring contracts.
    private static final Map<String, String> AUTHORING_SCHEMAS = [
            quiz     : 'quiz.yml',
            interview: 'interview.yml',
            roadmap  : 'roadmap.yml',
            reference: 'references.yml'
    ].asImmutable()

    private final Logger logger

    ContractSkeletonSyncService(Logger logger) {
        this.logger = logger
    }

    Map<String, Integer> sync(File repositoryRoot) {
        File contractRoot = new File(repositoryRoot, 'learning-contract')
        File contractFile = new File(contractRoot, 'contract.json')
        File commonFile = new File(contractRoot, 'schemas/common.schema.json')
        File outputRoot = new File(repositoryRoot, 'project-build/skeleton')
        if (!contractFile.isFile() || !commonFile.isFile()) {
            throw new GradleException(
                    "[CONTRACT-SKELETON] Missing pinned learning-contract submodule. " +
                            "Initialize learning-contract before Gradle Sync: ${contractRoot}"
            )
        }

        Map contract = readJson(contractFile)
        if (!(contract.documents instanceof Map)) {
            throw new GradleException('[CONTRACT-SKELETON] contract.json has no documents mapping.')
        }
        Map common = readJson(commonFile)
        Map<File, String> pending = new LinkedHashMap<>()
        List<File> obsoleteLanguageDirectories = []
        List<File> obsoleteQuestionFiles = []

        AUTHORING_SCHEMAS.each { String name, String outputFile ->
            String relative = contract.documents[name]?.toString()
            if (relative != "schemas/${name}.schema.json") {
                throw new GradleException(
                        "[CONTRACT-SKELETON] Unexpected schema path for ${name}: ${relative}"
                )
            }
            File schemaFile = new File(contractRoot, relative)
            Map schema = readJson(schemaFile)
            File target = new File(outputRoot, "${name}/${outputFile}")
            pending[target] = render(name, schema, common)
            if (name in ['quiz', 'interview']) {
                obsoleteQuestionFiles.add(new File(outputRoot, "${name}/question.yml"))
            }
            if (name != 'reference') {
                for (String language in ['vi', 'en']) {
                    obsoleteLanguageDirectories.add(
                            new File(outputRoot, "${name}/${language}")
                    )
                }
            }
        }

        // Preflight every generated destination before writing any file.
        pending.each { File file, String expected ->
            if (file.exists() && !file.isFile()) {
                throw new GradleException("[CONTRACT-SKELETON] Not a regular file: ${file}")
            }
            if (file.isFile()) {
                String current = file.getText('UTF-8')
                if (!current.startsWith(GENERATED_HEADER + '\n')) {
                    throw new GradleException(
                            "[CONTRACT-SKELETON] Refusing to overwrite non-generated file: ${file}"
                    )
                }
            }
        }
        obsoleteQuestionFiles.each { File file ->
            if (!file.exists()) return
            if (!file.isFile() ||
                    !file.getText('UTF-8').startsWith(GENERATED_HEADER + '\n')) {
                throw new GradleException(
                        "[CONTRACT-SKELETON] Refusing to remove non-generated legacy content: ${file}"
                )
            }
        }
        // Migrate the old bilingual layout without deleting author-maintained
        // files or any unexpected data inside the old language directories.
        obsoleteLanguageDirectories.each { File directory ->
            if (!directory.exists()) return
            if (!directory.isDirectory()) {
                throw new GradleException("[CONTRACT-SKELETON] Not a directory: ${directory}")
            }
            String filename = directory.parentFile.name == 'roadmap'
                    ? 'roadmap.yml' : 'question.yml'
            directory.listFiles().each { File candidate ->
                if (!candidate.isFile() ||
                        candidate.name != filename ||
                        !candidate.getText('UTF-8').startsWith(GENERATED_HEADER + '\n')) {
                    throw new GradleException(
                            "[CONTRACT-SKELETON] Refusing to remove non-generated legacy content: ${candidate}"
                    )
                }
            }
        }

        int updated = 0
        pending.each { File file, String expected ->
            if (file.isFile() && file.getText('UTF-8') == expected) {
                return
            }
            writeAtomically(file, expected)
            updated++
        }
        obsoleteQuestionFiles.each { File file ->
            if (file.isFile() && !file.delete()) {
                throw new GradleException("[CONTRACT-SKELETON] Cannot delete ${file}")
            }
        }
        obsoleteLanguageDirectories.each { File directory ->
            if (directory.isDirectory()) {
                directory.listFiles().each { File legacy ->
                    if (!legacy.delete()) {
                        throw new GradleException("[CONTRACT-SKELETON] Cannot delete ${legacy}")
                    }
                }
                if (!directory.delete()) {
                    throw new GradleException("[CONTRACT-SKELETON] Cannot remove ${directory}")
                }
            }
        }
        logger.lifecycle(
                '[CONTRACT-SKELETON] Synced {} YAML samples from pinned JSON schemas ({} updated).',
                pending.size(), updated
        )
        return [total: pending.size(), updated: updated]
    }

    String render(String name, Map schema, Map common) {
        if (!AUTHORING_SCHEMAS.containsKey(name)) {
            throw new GradleException("[CONTRACT-SKELETON] Unsupported source: ${name}")
        }
        final String language = 'en'
        if (schema.type != 'object' || !(schema.properties instanceof Map)) {
            throw new GradleException("[CONTRACT-SKELETON] ${name} must be an object schema.")
        }
        String rootKey = name == 'roadmap' ? 'roadmap'
                : name == 'reference' ? 'references' : 'questions'
        Map root = schema.properties[rootKey] as Map
        if (root?.type != 'array' ||
                (root.containsKey('default') &&
                        (!(root.default instanceof List) || !root.default.isEmpty())) ||
                !(root.items instanceof Map) ||
                root.items.type != 'object') {
            throw new GradleException(
                    "[CONTRACT-SKELETON] ${name} has no supported empty-list authoring root."
            )
        }

        List<String> lines = [
                GENERATED_HEADER,
                '# GENERATED FROM learning-contract; do not edit this file.',
                "# source: learning-contract/schemas/${name}.schema.json",
                "# language: ${name == 'reference' ? 'shared' : language}",
                '# Empty placeholders in the commented example are authoring hints,',
                '# not valid final published questions/milestones.',
                '#',
                "# ${localized(schema, language)}",
                '#',
                '# YAML FIELDS'
        ]
        appendFieldDescription(lines, rootKey, root, true, language, schema, common, 0)
        appendGuidance(lines, schema['x-guidance'], language)

        Map itemExample = exampleObject(root.items as Map, schema, common)
        DumperOptions options = new DumperOptions()
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        options.indent = 2
        options.width = 160
        options.allowUnicode = true
        options.splitLines = false
        String exampleYaml = new Yaml(options).dump([ (rootKey): [itemExample] ])
        lines.add('#')
        lines.add('# EXAMPLE (remove # comments to author an item):')
        exampleYaml.stripTrailing().split('\n').each { String line ->
            lines.add('# ' + line)
        }
        lines.add('# </learning-contract-skeleton>')
        lines.add('')
        // This YAML value is the direct JSON-Schema default, not an invented item.
        lines.add("${rootKey}: []")
        return lines.join('\n') + '\n'
    }

    private static void appendFieldDescription(List<String> lines, String key, Map field,
            boolean required, String lang, Map root, Map common, int depth) {
        Map effective = dereference(field, root, common)
        String type = effective.type ?: (effective.enum ? 'enum' : 'unknown')
        String description = localized(
                field.containsKey('x-description') || field.containsKey('description')
                        ? field : effective, lang
        )
        String defaultHint = field.containsKey('default')
                ? ", default=${new Yaml().dump(field.default).trim()}" : ''
        String uniqueHint = field['x-uniqueBy'] ? ", uniqueBy=${field['x-uniqueBy']}" : ''
        String countHint = field.minItems != null && field.maxItems != null
                ? ", size=${field.minItems}..${field.maxItems}" : ''
        String enumHint = effective.enum ? ", values=${effective.enum.join('/')}" : ''
        lines.add("# ${'  ' * depth}- ${key} [${type}, ${required ? 'required' : 'optional'}${uniqueHint}${countHint}${enumHint}${defaultHint}]: ${description}".stripTrailing())
        Map nested = effective.type == 'array' ? effective.items as Map : effective
        if (nested?.type == 'object' && nested.properties instanceof Map) {
            Set childRequired = (nested.required ?: []) as Set
            (nested.properties as Map).each { String child, Object node ->
                appendFieldDescription(lines, "${key}.${child}", node as Map,
                        childRequired.contains(child), lang, root, common, depth + 1)
            }
        }
    }

    private static void appendGuidance(List<String> lines, Object raw, String lang) {
        if (!(raw instanceof Map)) return
        Map guidance = raw as Map
        lines.add('#')
        lines.add('# ROADMAP THINKING FRAMEWORK')
        (guidance.principles instanceof Map ? guidance.principles : [:]).each {
            String key, Object value ->
                lines.add("# - ${key}: ${localized(value as Map, lang)}")
        }
        (guidance.thinkingFramework instanceof List ? guidance.thinkingFramework : []).each {
            Object value ->
                Map section = value as Map
                lines.add("# ${localized(section.title as Map, lang)}:")
                (section.questions ?: []).each { Object question ->
                    lines.add("#   - ${localized(question as Map, lang)}")
                }
        }
    }

    private static String localized(Object data, String lang) {
        if (!(data instanceof Map)) return ''
        Map m = data as Map
        Object texts = m['x-description'] ?: m['description'] ?: m
        if (texts instanceof Map) {
            return (texts[lang] ?: texts['en'] ?: '').toString().replace('\n', ' ')
        }
        return texts?.toString()?.replace('\n', ' ') ?: ''
    }

    private static Map dereference(Map schema, Map root, Map common) {
        String ref = schema['$ref']?.toString()
        if (ref == null) return schema
        String key
        Map target
        if (ref.startsWith('./common.schema.json#/$defs/')) {
            key = ref.substring('./common.schema.json#/$defs/'.length())
            target = common['$defs'] as Map
        } else if (ref.startsWith('#/$defs/')) {
            key = ref.substring('#/$defs/'.length())
            target = root['$defs'] as Map
        } else {
            throw new GradleException("[CONTRACT-SKELETON] Unsupported ref: ${ref}")
        }
        Map resolved = target[key] as Map
        if (!(resolved instanceof Map)) {
            throw new GradleException("[CONTRACT-SKELETON] Missing common ref: ${ref}")
        }
        return resolved
    }

    private static Map exampleObject(Map schema, Map root, Map common) {
        Map result = new LinkedHashMap()
        (schema.properties as Map).each { String key, Object value ->
            Map field = value as Map
            if (field.containsKey('default')) {
                Object defaultValue = field.default
                if (key == 'answers' && field.type == 'array' &&
                        field.minItems == 4 && field.maxItems == 4) {
                    List ids = (field.allOf ?: []).collect {
                        it.contains?.properties?.id?.const
                    }
                    if (ids.size() == 4 && ids.every { it instanceof String }) {
                        result[key] = ids.collect { String id ->
                            Map answer = exampleObject(field.items as Map, root, common)
                            answer.id = id
                            answer
                        }
                        return
                    }
                }
                result[key] = defaultValue
            } else {
                Map effective = dereference(field, root, common)
                if (effective.type == 'object' && effective.properties instanceof Map) {
                    result[key] = exampleObject(effective, root, common)
                } else if (effective.type == 'array') {
                    result[key] = []
                } else if (effective.type == 'boolean') {
                    result[key] = false
                } else {
                    result[key] = ''
                }
            }
        }
        return result
    }

    private static Map readJson(File file) {
        if (!file.isFile()) {
            throw new GradleException("[CONTRACT-SKELETON] Missing schema: ${file}")
        }
        try {
            Object parsed = new JsonSlurper().parse(file, 'UTF-8')
            if (parsed instanceof Map) return parsed as Map
            throw new GradleException("[CONTRACT-SKELETON] Invalid JSON root: ${file}")
        } catch (Exception ex) {
            throw new GradleException("[CONTRACT-SKELETON] Cannot read ${file}: ${ex.message}", ex)
        }
    }

    private static void writeAtomically(File target, String content) {
        Files.createDirectories(target.parentFile.toPath())
        def temporary = Files.createTempFile(target.parentFile.toPath(), '.contract-skeleton-', '.tmp')
        try {
            Files.write(temporary, content.getBytes(StandardCharsets.UTF_8))
            try {
                Files.move(temporary, target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
