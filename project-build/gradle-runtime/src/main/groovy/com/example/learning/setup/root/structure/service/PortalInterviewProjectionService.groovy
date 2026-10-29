package com.example.learning.setup.root.structure.service

import com.example.learning.setup.module.readme.service.ReadmeKnowledgeParser
import com.example.learning.utils.GradleBuildUtils
import com.example.learning.utils.ProjectPropertyUtils
import com.example.learning.utils.schema.validate.ContractSchemaValidateUtils
import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import java.nio.file.Path

class PortalInterviewProjectionService {
    private final Logger logger

    PortalInterviewProjectionService(Logger logger) {
        this.logger = logger
    }

    void generate(Project rootProject) {
        ChapterQuestionProjectionSupport.generate(rootProject, 'interview', logger)
    }
}

/**
 * Shared chapter-local Interview/Quiz projection. Source YAML owns the anchor
 * only; published YAML includes the resolved Knowledge file for the current FE.
 * No source file is modified.
 */
class ChapterQuestionProjectionSupport {
    private static final String OUTPUT_DIRECTORY = LearningDataPaths.MODULE_DIRECTORY
    private static final String QUESTION_FILE = 'question.yml'

    static void generate(Project rootProject, String kind, Logger logger,
                         Closure questionValidator = null) {
        File moduleRoot = new File(rootProject.projectDir, 'module')
        if (!moduleRoot.isDirectory()) {
            throw new GradleException("Project module directory was not found: ${moduleRoot.absolutePath}")
        }

        File outputRoot = new File(rootProject.projectDir, OUTPUT_DIRECTORY)
        Map<String, String> outputs = new LinkedHashMap<>()
        Set<String> routes = new LinkedHashSet<>()

        Path modulePath = moduleRoot.canonicalFile.toPath()
        rootProject.allprojects.findAll { Project project ->
            project.projectDir.canonicalFile.toPath().startsWith(modulePath) &&
                    new File(project.projectDir, 'gradle.properties').isFile()
        }.sort { Project left, Project right ->
            left.projectDir.absolutePath <=> right.projectDir.absolutePath
        }.each { Project module ->
            String routeId = (module.findProperty('SERVICE_NAME') ?: module.projectDir.name)
                    .toString().trim()
            List<String> languages = ProjectPropertyUtils.getStringList(module, 'MODULE_LANGUAGE')
                    .collect { String language -> language.toLowerCase(Locale.ROOT) }
                    .unique()
            boolean hasQuestions = false

            languages.each { String language ->
                // STEP 6/7 module assessments take precedence over chapter-local
                // legacy files. Preserve the latter for unmigrated modules.
                File moduleSource = new File(module.projectDir,
                        "content/${kind}/${language}/${kind}.yml")
                if (moduleSource.isFile()) {
                    ModuleAssessmentProjectionSupport.addOutputs(
                            outputs, moduleSource, routeId, kind, language)
                    hasQuestions = true
                    return
                }
                if (kind == 'casestudy') return
                File menu = new File(module.projectDir, "content/readme/docs/${language}/menu")
                if (!menu.isDirectory()) return
                List<File> chapters = chapterDirectories(menu)
                List<Map> indexChapters = []
                Set<String> quizIds = new LinkedHashSet<>()
                Map<String, Map<String, List<String>>> anchorCache = new LinkedHashMap<>()
                int total = 0
                chapters.each { File chapter ->
                    File source = kind == 'interview'
                            ? new File(chapter, 'questions/interview.yml')
                            : new File(chapter, "${kind}/${QUESTION_FILE}")
                    // Read-only legacy compatibility while existing modules are
                    // migrated by the legacy Interview P:S importer. Never write legacy files.
                    if (kind == 'interview' && !source.isFile()) {
                        source = new File(chapter, 'interview/question.yml')
                    }
                    if (!source.isFile()) return

                    Map document = readAndValidate(source, kind)
                    List questions = document.questions as List
                    if (questions.isEmpty()) return
                    hasQuestions = true

                    questions.eachWithIndex { Object rawQuestion, int questionIndex ->
                        Map question = rawQuestion as Map
                        String questionKey = kind == 'quiz'
                                ? (question.id ?: '').toString().trim()
                                : "questions[${questionIndex}]"
                        if (kind == 'quiz' && !quizIds.add(questionKey)) {
                            throw new GradleException(
                                    "Duplicate Quiz id '${questionKey}' across chapters in ${module.path}/${language}: ${source}")
                        }
                        enrichReadmeRelation(question, chapter, chapters, menu,
                                anchorCache, logger, kind, language, questionKey)
                        if (questionValidator != null) {
                            questionValidator.call(module, language, questionKey, question)
                        }
                    }

                    // Verify that the final published document also satisfies its
                    // schema after the additional resolved file was introduced.
                    ContractSchemaValidateUtils.validateData(
                            kind, document, ContractSchemaValidateUtils.LEGACY_QUESTION_LINKS)
                    String relative = "${routeId}/${kind}/${language}/menu/${chapter.name}/${QUESTION_FILE}"
                    putOutput(outputs, relative, writeYaml(document))
                    indexChapters.add([
                            id           : chapter.name,
                            title        : chapter.name,
                            questionCount: questions.size(),
                            path         : "menu/${chapter.name}/${QUESTION_FILE}"
                    ])
                    total += questions.size()
                }

                // Skip empty authored skeletons entirely; availability in the
                // module catalog follows only published question content.
                if (total > 0) {
                    putOutput(outputs, "${routeId}/${kind}/${language}/index.json",
                            JsonOutput.prettyPrint(JsonOutput.toJson([
                                    chapters      : indexChapters,
                                    totalQuestions: total
                            ])) + '\n')
                }
            }
            if (hasQuestions && !routes.add(routeId)) {
                throw new GradleException("Duplicate Portal ${kind} route identifier: ${routeId}")
            }
        }

        writeOutputs(outputRoot, outputs)
        removeStaleFiles(outputRoot, kind, outputs.keySet())
        logger.lifecycle('[PORTAL-{}] Generated {} chapter/index files.', kind.toUpperCase(Locale.ROOT), outputs.size())
    }

    private static Map readAndValidate(File source, String kind) {
        Object value
        try {
            value = new Yaml().load(source.getText('UTF-8'))
        } catch (Exception exception) {
            throw new GradleException("Unable to parse ${kind} YAML: ${source.absolutePath}", exception)
        }
        ContractSchemaValidateUtils.validateData(kind, value)
        return value as Map
    }

    private static List<File> chapterDirectories(File menu) {
        File[] children = menu.listFiles()
        if (children == null) {
            throw new GradleException("Unable to list README chapter directory: ${menu.absolutePath}")
        }
        return children.findAll { File child ->
            child.isDirectory() && !child.name.startsWith('.') &&
                    child.name ==~ /\d+\..+/
        }.sort { File left, File right ->
            int leftNumber = Integer.parseInt(left.name.substring(0, left.name.indexOf('.')))
            int rightNumber = Integer.parseInt(right.name.substring(0, right.name.indexOf('.')))
            (leftNumber <=> rightNumber) ?: (left.name <=> right.name)
        }
    }

    private static void enrichReadmeRelation(
            Map question,
            File chapter,
            List<File> chapters,
            File menu,
            Map<String, Map<String, List<String>>> anchorCache,
            Logger logger,
            String kind,
            String language,
            String questionKey
    ) {
        Map relation = question.readmeRelated as Map
        String anchor = (relation.anchor ?: '').toString().trim()
        String authoredFile = (relation.file ?: '').toString().trim()
        if (!authoredFile.isBlank()) {
            throw new GradleException(
                    "Invalid ${kind} readmeRelated for ${chapter.name}/${language}/${questionKey}: " +
                            "source must contain only an anchor, found file='${authoredFile}'.")
        }

        if (anchor.isBlank()) {
            relation.file = ''
            return
        }

        Map<String, List<String>> localAnchors = anchorCache.computeIfAbsent(chapter.name) {
            scanChapterAnchors(chapter, menu, logger)
        }
        List<String> matches = localAnchors[anchor] ?: []
        if (matches.size() != 1) {
            String reason = matches.isEmpty() ? 'missing' : 'ambiguous'
            if (matches.isEmpty()) {
                boolean existsElsewhere = chapters.any { File otherChapter ->
                    if (otherChapter.name == chapter.name) return false
                    Map<String, List<String>> otherAnchors = anchorCache.computeIfAbsent(otherChapter.name) {
                        scanChapterAnchors(otherChapter, menu, logger)
                    }
                    return otherAnchors.containsKey(anchor)
                }
                if (existsElsewhere) reason = 'cross-chapter (anchor exists only in another chapter)'
            }
            throw new GradleException(
                    "Unresolved ${kind} readmeRelated for ${chapter.name}/${language}/${questionKey}: " +
                            "anchor='${anchor}' is ${reason} in sibling knowledge (matches: ${matches}).")
        }
        relation.file = matches.first()
    }

    private static Map<String, List<String>> scanChapterAnchors(
            File chapter, File menu, Logger logger
    ) {
        File knowledge = new File(chapter, 'knowledge')
        Map<String, List<String>> index = new LinkedHashMap<>()
        if (!knowledge.isDirectory()) return index
        Path knowledgeRoot = knowledge.canonicalFile.toPath()
        if (!knowledgeRoot.startsWith(chapter.canonicalFile.toPath()) ||
                !chapter.canonicalFile.toPath().startsWith(menu.canonicalFile.toPath())) {
            throw new GradleException("Chapter Knowledge path escapes its README menu: ${knowledge}")
        }
        ReadmeKnowledgeParser parser = new ReadmeKnowledgeParser(logger)
        List<File> markdown = []
        knowledge.eachFileRecurse { File file ->
            if (file.isFile() && file.name.toLowerCase(Locale.ROOT).endsWith('.md') &&
                    !file.name.startsWith('.')) markdown.add(file)
        }
        markdown.sort { File left, File right -> left.absolutePath <=> right.absolutePath }
                .each { File file ->
                    if (!file.canonicalFile.toPath().startsWith(knowledgeRoot)) {
                        throw new GradleException("Knowledge file escapes its chapter: ${file}")
                    }
                    String path = GradleBuildUtils.normalizePath(
                            menu.canonicalFile.toPath().relativize(file.canonicalFile.toPath()).toString())
                    parser.parse(file).findAll { it.valid }.each { section ->
                        index.computeIfAbsent(section.id) { [] }.add(path)
                    }
                }
        return index
    }

    private static String writeYaml(Map document) {
        DumperOptions options = new DumperOptions()
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        options.indent = 2
        options.width = 120
        return new Yaml(options).dump(document)
    }

    private static void putOutput(Map<String, String> outputs, String path, String content) {
        if (outputs.containsKey(path)) throw new GradleException("Duplicate Portal output: ${path}")
        outputs[path] = content
    }

    private static void writeOutputs(File outputRoot, Map<String, String> outputs) {
        outputs.each { String relativePath, String content ->
            File output = new File(outputRoot, relativePath)
            if (!output.parentFile.isDirectory() && !output.parentFile.mkdirs() &&
                    !output.parentFile.isDirectory()) {
                throw new GradleException("Unable to create Portal output directory: ${output.parentFile}")
            }
            if (!output.isFile() || output.getText('UTF-8') != content) {
                output.setText(content, 'UTF-8')
            }
        }
    }

    private static void removeStaleFiles(File outputRoot, String kind, Set<String> expected) {
        if (!outputRoot.exists()) return
        List<File> stale = []
        outputRoot.eachFileRecurse { File file ->
            if (!file.isFile()) return
            String relative = GradleBuildUtils.normalizePath(
                    outputRoot.toPath().toAbsolutePath().normalize().relativize(
                            file.toPath().toAbsolutePath().normalize()).toString())
            List<String> parts = relative.split('/').toList()
            if (parts.size() >= 3 && parts[1] == kind && !expected.contains(relative)) {
                stale.add(file)
            }
        }
        stale.each { File file ->
            if (!file.delete() && file.exists()) {
                throw new GradleException("Unable to delete stale Portal ${kind} file: ${file}")
            }
        }
    }
}
