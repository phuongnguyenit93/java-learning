package com.example.learning.task.sheets.service

import com.example.learning.setup.module.readme.service.ReadmeKnowledgeParser
import com.example.learning.utils.schema.validate.ContractSchemaValidateUtils
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Read-only structural and relation preflight, shared by DRY_RUN and APPLY. */
class ModuleLanguageCloneValidationService {
    private static final String DOCS = 'content/readme/docs'
    private static final String SWAGGER = 'src/main/resources/swagger'

    static void validate(Map<String, Map<String, byte[]>> trees, File repositoryRoot) {
        Map<String, byte[]> docs = trees[DOCS]
        Map<String, Set<String>> sections = new LinkedHashMap<>()
        Map<String, Set<String>> chapterAnchors = new LinkedHashMap<>()
        Map<String, Map<String, Set<String>>> chapterSections = new LinkedHashMap<>()
        Set<String> chapterIds = new LinkedHashSet<>()
        ReadmeKnowledgeParser parser = new ReadmeKnowledgeParser(
                Logging.getLogger(ModuleLanguageCloneValidationService))
        docs.findAll { String path, byte[] bytes ->
            path ==~ /menu\/[^\/]+\.md/ ||
                    path ==~ /menu\/[^\/]+\/knowledge\/[^\/]+\.md/
        }.each { String path, byte[] bytes ->
            String source = path.substring('menu/'.length())
            def candidates = parser.parse(new File(source), content(bytes))
            if (!candidates || candidates.any { !it.valid }) {
                throw new GradleException('Invalid or missing VI Knowledge anchor: ' + source)
            }
            List<String> ids = candidates.collect { it.id as String }
            if (ids.toSet().size() != ids.size()) {
                throw new GradleException('Duplicate Knowledge anchors: ' + source)
            }
            String stem = source.substring(source.lastIndexOf('/') + 1)
                    .replaceFirst(/(?i)\.md$/, '')
            if (!chapterIds.add(stem.toLowerCase(Locale.ROOT))) {
                throw new GradleException('Duplicate Knowledge chapter identity: ' + stem)
            }
            sections[source] = ids as Set
            if (source.contains('/knowledge/')) {
                String chapter = source.substring(0, source.indexOf('/knowledge/'))
                Map<String, Set<String>> files = chapterSections.computeIfAbsent(
                        chapter) { new LinkedHashMap<>() }
                files[source] = sections[source]
                Set<String> known = chapterAnchors.computeIfAbsent(
                        chapter) { new LinkedHashSet<>() }
                if (!Collections.disjoint(known, sections[source])) {
                    throw new GradleException('Duplicate Knowledge anchor within chapter: ' + chapter)
                }
                known.addAll(sections[source])
            }
        }
        if (!sections) {
            throw new GradleException('Missing VI Knowledge Markdown files.')
        }
        chapterSections.each { String chapter, Map<String, Set<String>> files ->
            String path = 'menu/' + chapter + '/metadata/knowledge-metadata.yml'
            Map metadata = yaml(docs[path], path)
            Map values = metadata.containsKey('sections')
                    ? (metadata.sections instanceof Map ? metadata.sections as Map : null)
                    : metadata
            if (values == null ||
                    (metadata.containsKey('sections') &&
                            metadata.keySet() != (['sections'] as Set)) ||
                    values.keySet() != chapterAnchors[chapter] ||
                    values.values().any { !(it instanceof Map) }) {
                throw new GradleException('Knowledge metadata does not match README anchors: ' +
                        path)
            }
        }
        List<String> listed = []
        String list = content(docs['LIST.md'])
        def matcher = (list =~ /content\/readme\/docs\/en\/menu\/([^)\s]+)/)
        matcher.each { found ->
            String path = java.net.URLDecoder.decode(found[1].toString(), 'UTF-8')
            if (path ==~ /[^\/]+\.md/ ||
                    path ==~ /[^\/]+\/knowledge\/[^\/]+\.md/) {
                listed.add(path)
            }
        }
        if (listed.toSet() != sections.keySet() || listed.size() != sections.size()) {
            throw new GradleException('LIST.md EN links do not match the Knowledge tree.')
        }
        if (list.contains('content/readme/docs/vi/menu/')) {
            throw new GradleException('LIST.md still refers to VI paths.')
        }

        Map<String, byte[]> swagger = trees[SWAGGER]
        Map apiDescriptions = swagger.containsKey('api-descriptions.yml')
                ? yaml(swagger['api-descriptions.yml'], 'swagger/api-descriptions.yml') : [:]
        Map<String, byte[]> roadmap = trees['content/roadmap']
        if (!roadmap.isEmpty()) {
            byte[] data = roadmap['roadmap.yml']
            if (data == null) throw new GradleException('Missing VI roadmap.yml.')
            Map document = yaml(data, 'roadmap.yml')
            ContractSchemaValidateUtils.validateData('roadmap', document)
            Set<String> routes = routeCatalog(repositoryRoot)
            (document.roadmap as List).each { Map milestone ->
                (milestone.relatedKnowledge ?: []).each { Object relation ->
                    String value = relation?.toString()?.trim()
                    if (value && !chapterIds.contains(value.toLowerCase(Locale.ROOT)) &&
                            !sections.values().any { it.contains(value) }) {
                        throw new GradleException('Unresolved Roadmap relatedKnowledge: ' + value)
                    }
                }
                (milestone.relatedModules ?: []).each { Object relation ->
                    String routeId = relation instanceof Map
                            ? (relation.routeId ?: '').toString().trim() : ''
                    if (!routeId || !routes.contains(routeId)) {
                        throw new GradleException('Unresolved Roadmap relatedModules.routeId: ' +
                                routeId)
                    }
                }
            }
        }
        chapterAnchors.each { String chapter, Set<String> anchors ->
            String sectionPath = 'menu/' + chapter + '/summary/section.md'
            String summariesPath = 'menu/' + chapter + '/summary/summaries.yml'
            boolean sectionExists = docs.containsKey(sectionPath)
            boolean summariesExist = docs.containsKey(summariesPath)
            if (sectionExists != summariesExist) {
                throw new GradleException('Incomplete VI Summary chapter: ' + chapter)
            }
            if (summariesExist) {
                if (!content(docs[sectionPath]).trim()) {
                    throw new GradleException('Empty VI Summary section: ' + chapter)
                }
                Map summaryData = yaml(docs[summariesPath], summariesPath)
                if (summaryData.keySet() != (['summaries'] as Set) ||
                        !(summaryData.summaries instanceof List) ||
                        (summaryData.summaries as List).size() != anchors.size()) {
                    throw new GradleException('Invalid VI Summary lessons: ' + chapter)
                }
                List ids = (summaryData.summaries as List).collect { Object value ->
                    if (!(value instanceof Map) || !(value.id instanceof String) ||
                            !(value.title instanceof String) || !value.title.trim() ||
                            !(value.summary instanceof String) || !value.summary.trim()) {
                        throw new GradleException('Invalid VI Summary entry in ' + chapter)
                    }
                    value.id
                }
                if (ids != anchors.toList()) {
                    throw new GradleException('Summary IDs/order do not match Knowledge: ' + chapter)
                }
            }
            ['quiz', 'interview'].each { String kind ->
                String path = 'menu/' + chapter + '/' + kind + '/question.yml'
                if (!docs.containsKey(path)) return
                Map document = yaml(docs[path], path)
                ContractSchemaValidateUtils.validateData(kind, document)
                (document.questions as List).eachWithIndex { Map question, int index ->
                    checkChapterAnchor(question.readmeRelated, anchors,
                            path + ' #' + (index + 1))
                    checkApi(question.apiRelated, apiDescriptions,
                            path + ' #' + (index + 1))
                }
            }
        }
        docs.keySet().findAll { it ==~ /menu\/[^\/]+\/(quiz|interview)\/question\.yml/ }
                .each { String path ->
                    String chapter = path.split('/')[1]
                    if (!chapterAnchors.containsKey(chapter)) {
                        throw new GradleException('Question YAML has no Knowledge chapter: ' + path)
                    }
                }
        docs.keySet().findAll { it ==~ /menu\/[^\/]+\/summary\/[^\/]+/ }
                .each { String path ->
                    String chapter = path.split('/')[1]
                    if (!chapterAnchors.containsKey(chapter) ||
                            !(path.endsWith('/summary/section.md') ||
                                    path.endsWith('/summary/summaries.yml'))) {
                        throw new GradleException('Unexpected Summary file: ' + path)
                    }
                }
        swagger.each { String path, byte[] bytes ->
            if (!(path in ['api-descriptions.yml', 'api-execution.yml',
                           'api-cases.yml', 'controller-description.yml', 'api-params.yml'])) {
                throw new GradleException('Unexpected VI Swagger file: ' + path)
            }
            Map document = yaml(bytes, 'swagger/' + path)
            checkNestedRelations(document, sections, 'swagger/' + path)
        }
    }

    private static void checkNestedRelations(Object node, Map<String, Set<String>> sections,
                                             String owner) {
        if (node instanceof Map) {
            Map source = node as Map
            if (source.containsKey('readmeRelated')) {
                checkReadme(source.readmeRelated, sections, owner, true)
            }
            source.each { Object key, Object value ->
                if (key != 'readmeRelated') checkNestedRelations(value, sections, owner)
            }
        } else if (node instanceof Collection) {
            (node as Collection).each { checkNestedRelations(it, sections, owner) }
        }
    }

    private static void checkReadme(Object relation, Map<String, Set<String>> sections,
                                    String owner, boolean allowFileOnly) {
        if (relation == null) return
        if (!(relation instanceof Map)) {
            throw new GradleException('Invalid readmeRelated in ' + owner)
        }
        String file = (relation.file ?: '').toString().trim()
        String anchor = (relation.anchor ?: '').toString().trim()
        if (!file && !anchor) return
        if (!file || !sections.containsKey(file) ||
                (anchor && !sections[file].contains(anchor)) ||
                (!anchor && !allowFileOnly)) {
            throw new GradleException('Unresolved readmeRelated in ' + owner +
                    ': file=' + file + ', anchor=' + anchor)
        }
    }

    private static void checkChapterAnchor(Object relation, Set<String> anchors, String owner) {
        if (!(relation instanceof Map)) {
            throw new GradleException('Invalid readmeRelated in ' + owner)
        }
        if ((relation as Map).containsKey('file')) {
            throw new GradleException('Chapter readmeRelated must contain only anchor in ' + owner)
        }
        String anchor = ((relation as Map).anchor ?: '').toString().trim()
        if (anchor && !anchors.contains(anchor)) {
            throw new GradleException('Unresolved readmeRelated in ' + owner +
                    ': anchor=' + anchor)
        }
    }

    private static void checkApi(Object relation, Map apiDescriptions, String owner) {
        if (relation == null) return
        if (!(relation instanceof Map)) {
            throw new GradleException('Invalid apiRelated in ' + owner)
        }
        String controller = (relation.controller ?: '').toString().trim()
        String method = (relation.methodSignature ?: '').toString().trim()
        if (!controller && !method) return
        if (!controller || !method || !(apiDescriptions[controller] instanceof Map) ||
                !(apiDescriptions[controller][method] instanceof Map)) {
            throw new GradleException('Unresolved apiRelated in ' + owner +
                    ': ' + controller + '#' + method)
        }
    }

    private static Map yaml(byte[] bytes, String path) {
        if (bytes == null) throw new GradleException('Missing YAML: ' + path)
        Object document
        try {
            LoaderOptions options = new LoaderOptions()
            options.allowDuplicateKeys = false
            document = new Yaml(new SafeConstructor(options)).load(content(bytes))
        } catch (Exception error) {
            throw new GradleException('Invalid YAML ' + path + ': ' + error.message, error)
        }
        if (!(document instanceof Map)) {
            throw new GradleException('YAML must contain a mapping: ' + path)
        }
        return document as Map
    }

    private static String content(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8)
    }

    private static Set<String> routeCatalog(File repositoryRoot) {
        Path root = new File(repositoryRoot, 'module').toPath()
        if (!Files.isDirectory(root)) {
            throw new GradleException('Missing repository module/ route catalog.')
        }
        Set<String> routes = new LinkedHashSet<>()
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir != root && dir.fileName.toString() in
                        ['build', '.gradle', '.git', 'node_modules']) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.fileName.toString() == 'master.json' && attrs.isRegularFile()) {
                    try {
                        Object doc = new JsonSlurper().parse(file.toFile(), 'UTF-8')
                        String id = doc instanceof Map
                                ? (doc.SERVICE_NAME?.VALUE ?: '').toString().trim() : ''
                        if (id) routes.add(id)
                    } catch (Exception ex) {
                        throw new GradleException('Invalid module route master.json: ' +
                                file + ': ' + ex.message, ex)
                    }
                }
                return FileVisitResult.CONTINUE
            }
        })
        return routes
    }
}
