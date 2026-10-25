package com.example.learning.setup.root.structure.service

import com.example.learning.utils.schema.validate.ContractSchemaValidateUtils
import com.example.learning.utils.GradleBuildUtils
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.yaml.snakeyaml.Yaml


class ProjectStructureService {

    private static final List<String> PORTAL_API_FILES =
            [
                    'api-descriptions.yml',
                    'api-execution.yml',
                    'api-params.yml',
                    'controller-description.yml'
            ]

    private static final String TEXT_STRUCTURE_PATH =
            'project-build/gradle-runtime/src/main/resources/structure/module-structure.txt'


    private static final String MARKDOWN_STRUCTURE_PATH =
            'STRUCTURE.md'


    private static final String PORTAL_CATALOG_PATH =
            LearningDataPaths.CATALOG_FILE


    private static final String PORTAL_MODULE_DIRECTORY =
            LearningDataPaths.MODULE_DIRECTORY


    private final Logger logger

    private final ModuleOrderService moduleOrderService
    private LocalRunFingerprintService localRunFingerprints


    ProjectStructureService(
            Logger logger
    ) {

        this.logger =
                logger


        this.moduleOrderService =
                new ModuleOrderService(
                        logger
                )
    }


    void generate(
            Project rootProject
    ) {

        // ====================================================
        // Source
        // ====================================================

        File moduleDirectory =
                new File(
                        rootProject.projectDir,
                        'module'
                )


        if (!moduleDirectory.isDirectory()) {

            throw new GradleException(
                    """
Project module directory was not found:

${moduleDirectory.absolutePath}
""".stripIndent()
            )
        }


        // ====================================================
        // Generate content
        // ====================================================

        StructureNode structure =
                buildStructure(
                        rootProject,
                        moduleDirectory
                )


        String textContent =
                generateTextStructure(
                        structure
                )


        String markdownContent =
                generateMarkdownStructure(
                        rootProject,
                        structure
                )


        // ====================================================
        // Outputs
        // ====================================================

        File textOutput =
                new File(
                        rootProject.projectDir,
                        TEXT_STRUCTURE_PATH
                )


        File markdownOutput =
                new File(
                        rootProject.projectDir,
                        MARKDOWN_STRUCTURE_PATH
                )


        // ====================================================
        // Write
        // ====================================================

        writeIfChanged(
                textOutput,
                textContent
        )


        writeIfChanged(
                markdownOutput,
                markdownContent
        )
    }


    void generatePortalData(
            Project rootProject
    ) {

        File moduleDirectory =
                new File(
                        rootProject.projectDir,
                        'module'
                )


        if (!moduleDirectory.isDirectory()) {

            throw new GradleException(
                    """
Project module directory was not found:

${moduleDirectory.absolutePath}
""".stripIndent()
            )
        }


        StructureNode structure =
                buildStructure(
                        rootProject,
                        moduleDirectory
                )


        String portalCatalogContent =
                generatePortalCatalog(
                        structure
                )


        File portalCatalogOutput =
                new File(
                        rootProject.projectDir,
                        PORTAL_CATALOG_PATH
                )


        writeIfChanged(
                portalCatalogOutput,
                portalCatalogContent
        )


        syncPortalOverviewOutputs(
                rootProject,
                structure
        )
    }


    // ========================================================
    // Shared structure model
    // ========================================================

    private StructureNode buildStructure(
            Project rootProject,
            File moduleDirectory
    ) {

        localRunFingerprints = new LocalRunFingerprintService(rootProject.projectDir)

        return buildStructureNode(
                rootProject,
                moduleDirectory,
                moduleDirectory
        )
    }


    private StructureNode buildStructureNode(
            Project rootProject,
            File moduleDirectory,
            File directory
    ) {

        File masterFile =
                new File(
                        directory,
                        'master.json'
                )


        Map master =
                masterFile.isFile()
                        ? readMasterFile(masterFile)
                        : [:]


        String relativePath =
                GradleBuildUtils.normalizePath(
                        rootProject
                                .projectDir
                                .toPath()
                                .toAbsolutePath()
                                .normalize()
                                .relativize(
                                        directory
                                                .toPath()
                                                .toAbsolutePath()
                                                .normalize()
                                )
                                .toString()
                )


        String relativeModulePath =
                GradleBuildUtils.normalizePath(
                        moduleDirectory
                                .toPath()
                                .toAbsolutePath()
                                .normalize()
                                .relativize(
                                        directory
                                                .toPath()
                                                .toAbsolutePath()
                                                .normalize()
                                )
                                .toString()
                )


        StructureNode node =
                new StructureNode(
                        directory: directory,
                        id: relativeModulePath.isBlank()
                                ? moduleDirectory.name
                                : relativeModulePath,
                        name: directory.name,
                        path: relativePath,
                        realModule: new File(directory, 'gradle.properties').isFile(),
                        serviceName: getMasterValue(master, 'SERVICE_NAME'),
                        moduleType: getMasterValue(master, 'MODULE_TYPE'),
                        javaBasePackage: getMasterValue(master, 'JAVA_BASE_PACKAGE'),
                        description: getMasterValue(master, 'SERVICE_NAME_DESCRIBE'),
                        moduleDepend: 'TRUE'.equalsIgnoreCase(
                                getMasterValue(master, 'IS_MODULE_DEPEND')
                        )
                )


        if (node.realModule) {

            node.codeExplorerAvailable =
                    new File(directory, 'content/code-explorer/material.yml').isFile()

            if (
                    'SERVLET'.equalsIgnoreCase(node.moduleType) ||
                            'REACTIVE'.equalsIgnoreCase(node.moduleType)
            ) {

                try {
                    node.sourceFingerprint = localRunFingerprints.fingerprint(relativePath)
                } catch (Exception exception) {
                    // Keep the content catalog usable for an uncommitted module,
                    // but never advertise an unverifiable executable JAR.
                    logger.warn('[PORTAL-LOCAL-RUN] Skip fingerprint for {}: {}', relativePath, exception.message)
                    node.sourceFingerprint = null
                }
            }

            List<String> moduleLanguages =
                    getMasterStringList(
                            master,
                            'MODULE_LANGUAGE'
                    )

            node.overviewSources =
                    findOverviewSources(
                            directory,
                            moduleLanguages
                    )


            node.knowledgeLanguages =
                    findKnowledgeLanguages(
                            directory,
                            moduleLanguages
                    )


            node.videoLanguages =
                    findVideoLanguages(
                            directory,
                            moduleLanguages
                    )

            node.summaryLanguages = findSummaryLanguages(directory, moduleLanguages)


            node.roadmapLanguages =
                    findRoadmapLanguages(
                            directory,
                            moduleLanguages
                    )


            node.referenceAvailable =
                    hasAuthoredReference(
                            directory
                    )


            node.apiLanguages =
                    findApiLanguages(
                            directory,
                            moduleLanguages
                    )


            node.quizQuestionCounts =
                    findQuestionCounts(
                            directory,
                            moduleLanguages,
                            'quiz'
                    )


            node.interviewQuestionCounts =
                    findQuestionCounts(
                            directory,
                            moduleLanguages,
                            'interview'
                    )
        }


        node.children =
                moduleOrderService
                        .getOrderedChildren(
                                directory
                        )
                        .collect {
                            File child ->

                                buildStructureNode(
                                        rootProject,
                                        moduleDirectory,
                                        child
                                )
                        }


        return node
    }


    // ========================================================
    // TXT structure
    // ========================================================

    private String generateTextStructure(
            StructureNode rootNode
    ) {

        StringBuilder builder =
                new StringBuilder()


        builder
                .append(
                        rootNode.name
                )
                .append(
                        '\n'
                )


        appendTextChildren(
                rootNode,
                '',
                builder
        )


        return builder.toString()
    }


    private void appendTextChildren(
            StructureNode node,
            String prefix,
            StringBuilder builder
    ) {

        List<StructureNode> children =
                node.children


        children.eachWithIndex {
            StructureNode child,
            int index ->

                boolean last =
                        index == children.size() - 1


                String displayName =
                        resolveDisplayName(
                                child
                        )


                builder
                        .append(
                                prefix
                        )
                        .append(
                                last
                                        ? '└── '
                                        : '├── '
                        )
                        .append(
                                displayName
                        )
                        .append(
                                '\n'
                        )


                appendTextChildren(
                        child,
                        prefix +
                                (
                                        last
                                                ? '    '
                                                : '│   '
                                ),
                        builder
                )
        }
    }


    private String resolveDisplayName(
            StructureNode node
    ) {

        String displayName =
                node.name


        if (
                node.serviceName != null &&
                        !node.serviceName.isBlank()
        ) {

            displayName +=
                    " [${node.serviceName}]"
        }


        if (
                node.moduleDepend
        ) {

            displayName +=
                    ' - [DEPEND]'
        }


        if (
                node.description != null &&
                        !node.description.isBlank()
        ) {

            displayName +=
                    " : ${node.description}"
        }


        return displayName
    }


    // ========================================================
    // Markdown structure
    // ========================================================

    private String generateMarkdownStructure(
            Project rootProject,
            StructureNode rootNode
    ) {

        StringBuilder builder =
                new StringBuilder()


        builder.append(
                '# Project Structure\n\n'
        )


        builder.append(
                'Sử dụng mũi tên để đóng/mở các phân cấp module.\n\n'
        )


        String rootRelativePath =
                buildRelativeLink(
                        rootProject,
                        rootNode.directory
                )


        builder.append(
                '<details open>\n'
        )


        builder.append(
                "  <summary><b><a href='${rootRelativePath}'>${rootNode.name} (Root)</a></b></summary>\n"
        )


        appendMarkdownChildren(
                rootProject,
                rootNode,
                builder
        )


        builder.append(
                '</details>\n'
        )


        return builder.toString()
    }


    private void appendMarkdownChildren(
            Project rootProject,
            StructureNode node,
            StringBuilder builder
    ) {

        List<StructureNode> children =
                node.children


        if (children.isEmpty()) {
            return
        }


        builder.append(
                '<ul>\n'
        )


        children.each {
            StructureNode child ->

                String relativePath =
                        buildRelativeLink(
                        rootProject,
                        child.directory
                )


                List<StructureNode> childDirectories =
                        child.children


                builder.append(
                        '<li>\n'
                )


                if (!childDirectories.isEmpty()) {

                    builder.append(
                            '<details>\n'
                    )


                    builder.append(
                            "  <summary><b><a href='${relativePath}'>📁 ${child.name}</a></b></summary>\n"
                    )


                    appendMarkdownChildren(
                            rootProject,
                            child,
                            builder
                    )


                    builder.append(
                            '</details>\n'
                    )
                }
                else {

                    builder.append(
                            "  <a href='${relativePath}'>🪄 ${child.name}</a>\n"
                    )
                }


                builder.append(
                        '</li>\n'
                )
        }


        builder.append(
                '</ul>\n'
        )
    }


    // ========================================================
    // Portal catalog
    // ========================================================

    private String generatePortalCatalog(
            StructureNode rootNode
    ) {

        Map catalog =
                [
                        version: 1,
                        root   : toPortalCatalogNode(rootNode)
                ]

        ContractSchemaValidateUtils.validateData('moduleCatalog', catalog)


        return JsonOutput.prettyPrint(
                JsonOutput.toJson(catalog)
        ) + '\n'
    }


    private static Map toPortalCatalogNode(
            StructureNode node
    ) {

        Map result =
                [
                        id      : node.id,
                        name    : node.name,
                        path    : node.path,
                        kind    : node.realModule
                                ? 'MODULE'
                                : 'GROUP',
                        children: node.children.collect {
                            StructureNode child ->

                                toPortalCatalogNode(child)
                        }
                ]


        if (node.realModule) {

            result.routeId =
                    resolveRouteId(
                            node
                    )

            if (node.codeExplorerAvailable) {
                result.codeExplorer =
                        "/module/${resolveRouteId(node)}/code-explorer/index.json"
            }


            if (!node.overviewSources.isEmpty()) {

                result.overview =
                        node.overviewSources
                                .keySet()
                                .toList()
                                .sort()
                                .collectEntries {
                                    String language ->

                                        [
                                                (language): "/module/${resolveRouteId(node)}/overview/${language}.md"
                                        ]
                                }
            }


            if (!node.knowledgeLanguages.isEmpty()) {

                result.knowledge =
                        node.knowledgeLanguages
                                .toList()
                                .sort()
                                .collectEntries {
                                    String language ->

                                        [
                                                (language): "/module/${resolveRouteId(node)}/knowledge/${language}/index.json"
                                        ]
                                }
            }


            if (!node.videoLanguages.isEmpty()) {

                result.video =
                        node.videoLanguages
                                .toList()
                                .sort()
                                .collectEntries {
                                    String language ->

                                        [
                                                (language): "/module/${resolveRouteId(node)}/video/${language}/index.json"
                                        ]
                                }
            }


            if (!node.roadmapLanguages.isEmpty()) {

                result.roadmap =
                        node.roadmapLanguages
                                .toList()
                                .sort()
                                .collectEntries {
                                    String language ->

                                        [
                                                (language): "/module/${resolveRouteId(node)}/roadmap/${language}/roadmap.yml"
                                        ]
                                }
            }

            if (!node.summaryLanguages.isEmpty()) {
                result.summary = node.summaryLanguages.toList().sort().collectEntries {
                    String language ->
                        [(language): "/module/${resolveRouteId(node)}/summary/${language}/index.json"]
                }
            }


            if (node.referenceAvailable) {
                result.reference =
                        "/module/${resolveRouteId(node)}/reference/references.yml"
            }


            if (!node.apiLanguages.isEmpty()) {

                result.api =
                        node.apiLanguages
                                .toList()
                                .sort()
                                .collectEntries {
                                    String language ->

                                        [
                                                (language): "/module/${resolveRouteId(node)}/api/${language}"
                                        ]
                                }
            }


            if (!node.quizQuestionCounts.isEmpty()) {

                result.quiz =
                        node.quizQuestionCounts.keySet()
                                .toList()
                                .sort()
                                .collectEntries {
                                    String language ->

                                    [
                                            (language): "/module/${resolveRouteId(node)}/quiz/${language}/index.json"
                                    ]
                                }
                result.quizQuestionCounts = node.quizQuestionCounts
            }


            if (!node.interviewQuestionCounts.isEmpty()) {

                result.interview =
                        node.interviewQuestionCounts.keySet()
                                .toList()
                                .sort()
                                .collectEntries {
                                    String language ->

                                    [
                                            (language): "/module/${resolveRouteId(node)}/interview/${language}/index.json"
                                    ]
                                }
                result.interviewQuestionCounts = node.interviewQuestionCounts
            }


            if (node.sourceFingerprint != null && !node.sourceFingerprint.isBlank()) {
                result.sourceFingerprint = node.sourceFingerprint
            }
        }


        if (node.serviceName != null && !node.serviceName.isBlank()) {
            result.serviceName = node.serviceName
        }


        if (node.moduleType != null && !node.moduleType.isBlank()) {
            result.moduleType = node.moduleType
        }


        if (node.javaBasePackage != null && !node.javaBasePackage.isBlank()) {
            result.javaBasePackage = node.javaBasePackage
        }


        if (node.description != null && !node.description.isBlank()) {
            result.description = node.description
        }


        if (node.moduleDepend) {
            result.moduleDepend = true
        }


        return result
    }



    private static String resolveRouteId(
            StructureNode node
    ) {

        return node.serviceName != null &&
                !node.serviceName.isBlank()
                ? node.serviceName
                : node.name
    }


    // ========================================================
    // Portal overview projection
    // ========================================================

    private static Map<String, File> findOverviewSources(
            File moduleDirectory,
            List<String> moduleLanguages
    ) {

        File readmeDirectory =
                new File(
                        moduleDirectory,
                        'content/readme/docs'
                )


        if (!readmeDirectory.isDirectory()) {
            return [:]
        }


        Map<String, File> result =
                new LinkedHashMap<>()


        moduleLanguages
                .each {
                    String language ->

                        File languageDirectory =
                                new File(
                                        readmeDirectory,
                                        language
                                )

                        File baseFile =
                                new File(
                                        languageDirectory,
                                        'BASE.md'
                                )


                        if (
                                baseFile.isFile() &&
                                        !baseFile.getText('UTF-8').isBlank()
                        ) {

                            result[
                                    language
                            ] =
                                    baseFile
                        }
                }


        return result
    }


    private static Set<String> findKnowledgeLanguages(
            File moduleDirectory,
            List<String> moduleLanguages
    ) {

        File readmeDirectory =
                new File(
                        moduleDirectory,
                        'content/readme/docs'
                )


        if (!readmeDirectory.isDirectory()) {
            return [] as Set<String>
        }


        Set<String> result =
                new LinkedHashSet<>()


        moduleLanguages
                .each {
                    String language ->

                        File languageDirectory =
                                new File(
                                        readmeDirectory,
                                        language
                                )

                        File menuDirectory =
                                new File(
                                        languageDirectory,
                                        'menu'
                                )


                        if (
                                menuDirectory.isDirectory() &&
                                        containsMarkdownFile(
                                                menuDirectory
                                        )
                        ) {

                            result.add(
                                    language
                            )
                        }
                }


        return result
    }


    private static Set<String> findRoadmapLanguages(
            File moduleDirectory,
            List<String> moduleLanguages
    ) {

        File roadmapDirectory =
                new File(
                        moduleDirectory,
                        'content/roadmap'
                )


        if (!roadmapDirectory.isDirectory()) {
            return [] as Set<String>
        }


        Set<String> result =
                new LinkedHashSet<>()


        moduleLanguages.each {
            String language ->

                File roadmapFile =
                        new File(
                                roadmapDirectory,
                                "${language}/roadmap.yml"
                        )


                if (
                        roadmapFile.isFile() &&
                                PortalRoadmapProjectionService.hasAuthoredRoadmap(
                                        roadmapFile
                                )
                ) {
                    result.add(
                            language
                    )
                }
        }


        return result
    }


    private static Set<String> findVideoLanguages(
            File moduleDirectory,
            List<String> moduleLanguages
    ) {

        Set<String> result =
                new LinkedHashSet<>()


        moduleLanguages.each {
            String language ->

            File menuDirectory =
                    new File(
                            moduleDirectory,
                            "content/readme/docs/${language}/menu"
                    )


            if (
                    menuDirectory.isDirectory() &&
                            containsAuthoredVideo(
                                    menuDirectory
                            )
            ) {
                result.add(language)
            }
        }


        return result
    }


    private static boolean hasAuthoredReference(
            File moduleDirectory
    ) {

        File referenceFile =
                new File(
                        moduleDirectory,
                        'content/reference/references.yml'
                )


        if (!referenceFile.isFile()) {
            return false
        }


        return PortalReferenceProjectionService.hasAuthoredReferences(referenceFile)
    }

    private static Set<String> findSummaryLanguages(File moduleDirectory, List<String> declared) {
        Set<String> result = new LinkedHashSet<>()
        declared.each { String lang ->
            File menu = new File(moduleDirectory, 'content/readme/docs/' + lang + '/menu')
            if (!menu.isDirectory()) return
            File[] chapters = menu.listFiles()
            if (chapters == null) throw new GradleException('Cannot list Summary menu: ' + menu)
            if (chapters.any { File chapter -> chapter.isDirectory() &&
                    new File(chapter, 'summary/section.md').isFile() &&
                    new File(chapter, 'summary/summaries.yml').isFile() }) {
                result.add(lang)
            }
        }
        return result
    }


    private static boolean containsAuthoredVideo(File directory) {
        File[] chapters = directory.listFiles()
        if (chapters == null) {
            throw new GradleException("Unable to list Video chapters: ${directory.absolutePath}")
        }
        Set<String> chapterIds = new LinkedHashSet<>()
        boolean found = false
        chapters.findAll { File chapter ->
            chapter.isDirectory() && chapter.name ==~ /[1-9][0-9]*\.[A-Za-z][A-Za-z0-9]*/
        }.each { File chapter ->
            File script = new File(chapter, 'video/video-script.yml')
            if (!script.isFile()) return
            String id = com.example.learning.utils.schema.validate.VideoYamlContentUtils
                    .categoryId(script).toLowerCase(Locale.ROOT)
            if (!chapterIds.add(id)) {
                throw new GradleException("Duplicate authored Video category: ${id} in ${directory}")
            }
            com.example.learning.utils.schema.validate.VideoYamlContentUtils.parse(
                    script.getText('UTF-8'))
            found = true
        }
        return found
    }


    private static Set<String> findApiLanguages(
            File moduleDirectory,
            List<String> moduleLanguages
    ) {

        File swaggerDirectory =
                new File(
                        moduleDirectory,
                        'src/main/resources/swagger'
                )


        if (!swaggerDirectory.isDirectory()) {
            return [] as Set<String>
        }


        Set<String> result =
                new LinkedHashSet<>()


        moduleLanguages
                .each {
                    String language ->

                        File languageDirectory =
                                new File(
                                        swaggerDirectory,
                                        language
                                )

                        boolean complete =
                                PORTAL_API_FILES.every {
                                    String fileName ->

                                        new File(
                                                languageDirectory,
                                                fileName
                                        ).isFile()
                                }


                        if (complete) {

                            result.add(
                                    language
                            )
                        }
                }


        return result
    }


    /**
     * The catalog links only languages that have authored chapter questions.
     * Empty chapter skeletons and legacy central YAML do not count as content.
     */
    private static Map<String, Integer> findQuestionCounts(
            File moduleDirectory,
            List<String> moduleLanguages,
            String kind
    ) {
        Map<String, Integer> counts = new LinkedHashMap<>()
        moduleLanguages.each { String language ->
            File menu = new File(moduleDirectory, "content/readme/docs/${language}/menu")
            if (!menu.isDirectory()) return
            File[] chapters = menu.listFiles()
            if (chapters == null) {
                throw new GradleException("Unable to list question chapters: ${menu}")
            }

            int total = 0
            Set<String> quizIds = new LinkedHashSet<>()
            chapters.findAll { File chapter ->
                chapter.isDirectory() && chapter.name ==~ /\d+\..+/
            }.each { File chapter ->
                File source = new File(chapter, "${kind}/question.yml")
                if (!source.isFile()) return
                Object document
                try {
                    document = new Yaml().load(source.getText('UTF-8'))
                } catch (Exception exception) {
                    throw new GradleException("Unable to parse ${kind} YAML: ${source}", exception)
                }
                if (!(document instanceof Map) || !((document as Map).questions instanceof List)) {
                    throw new GradleException("Invalid ${kind} questions root: ${source}")
                }
                ContractSchemaValidateUtils.validateData(kind, document)
                List questions = ((document as Map).questions as List)
                if (kind == 'quiz') {
                    questions.each { Map question ->
                        String id = question.id.toString().trim()
                        if (!quizIds.add(id)) {
                            throw new GradleException(
                                    "Duplicate Quiz id '${id}' across chapters in ${menu}")
                        }
                    }
                }
                total += questions.size()
            }
            if (total > 0) counts[language] = total
        }
        return counts
    }


    private static boolean containsMarkdownFile(
            File directory
    ) {

        File[] children =
                directory.listFiles()


        if (children == null) {

            throw new GradleException(
                    """
Unable to read README menu directory:

${directory.absolutePath}
""".stripIndent()
            )
        }


        return children.any {
            File child ->

                if (child.name.startsWith('.')) {
                    return false
                }


                if (child.isDirectory()) {

                    return containsMarkdownFile(
                            child
                    )
                }


                return child.isFile() &&
                        child.name.toLowerCase(Locale.ROOT).endsWith('.md')
        }
    }


    private void syncPortalOverviewOutputs(
            Project rootProject,
            StructureNode rootNode
    ) {

        File outputDirectory =
                new File(
                        rootProject.projectDir,
                        PORTAL_MODULE_DIRECTORY
                )


        Map<String, String> expectedOutputs =
                new LinkedHashMap<>()


        collectPortalOverviewOutputs(
                rootNode,
                expectedOutputs
        )


        expectedOutputs.each {
            String relativePath,
            String content ->

                writeIfChanged(
                        new File(
                                outputDirectory,
                                relativePath
                        ),
                        content
                )
        }


        removeStalePortalOverviewFiles(
                outputDirectory,
                expectedOutputs.keySet()
        )
    }


    private static void collectPortalOverviewOutputs(
            StructureNode node,
            Map<String, String> expectedOutputs
    ) {

        if (node.realModule) {

            String routeId =
                    resolveRouteId(
                            node
                    )


            node.overviewSources.each {
                String language,
                File sourceFile ->

                    String relativePath =
                            "${routeId}/overview/${language}.md"


                    if (expectedOutputs.containsKey(relativePath)) {

                        throw new GradleException(
                                """
Duplicate Portal overview output detected:

${relativePath}

Route identifiers must be unique for generated Portal overview content.
""".stripIndent()
                        )
                    }


                    expectedOutputs[
                            relativePath
                    ] =
                            sourceFile.getText(
                                    'UTF-8'
                            )
            }
        }


        node.children.each {
            StructureNode child ->

                collectPortalOverviewOutputs(
                        child,
                        expectedOutputs
                )
        }
    }


    private static void removeStalePortalOverviewFiles(
            File outputDirectory,
            Set<String> expectedRelativePaths
    ) {

        if (!outputDirectory.exists()) {
            return
        }


        if (!outputDirectory.isDirectory()) {

            throw new GradleException(
                    """
Portal overview output path is not a directory:

${outputDirectory.absolutePath}
""".stripIndent()
            )
        }


        outputDirectory.eachFileRecurse {
            File file ->

                if (!file.isFile() || !file.name.endsWith('.md')) {
                    return
                }


                String relativePath =
                        GradleBuildUtils.normalizePath(
                                outputDirectory
                                        .toPath()
                                        .toAbsolutePath()
                                        .normalize()
                                        .relativize(
                                                file
                                                        .toPath()
                                                        .toAbsolutePath()
                                                        .normalize()
                                        )
                                        .toString()
                        )


                if (
                        isPortalOverviewPath(relativePath) &&
                                !expectedRelativePaths.contains(relativePath)
                ) {

                    if (!file.delete() && file.exists()) {

                        throw new GradleException(
                                """
Unable to remove stale Portal overview file:

${file.absolutePath}
""".stripIndent()
                        )
                    }
                }
        }
    }


    private static boolean isPortalOverviewPath(
            String relativePath
    ) {

        List<String> pathSegments =
                relativePath
                        .split('/')
                        .toList()


        return pathSegments.size() >= 3 &&
                pathSegments[1] == 'overview'
    }


    // ========================================================
    // Master
    // ========================================================

    private static Map readMasterFile(
            File masterFile
    ) {

        try {

            return new JsonSlurper()
                    .parse(
                            masterFile
                    ) as Map
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to read module master file:

${masterFile.absolutePath}

Reason:
${exception.message}
""".stripIndent(),
                    exception
            )
        }
    }


    private static String getMasterValue(
            Map master,
            String key
    ) {

        Object value =
                master
                        ?.get(
                                key
                        )
                        ?.get(
                                'VALUE'
                        )


        return value
                ?.toString()
                ?.trim()
    }


    private static List<String> getMasterStringList(
            Map master,
            String key
    ) {

        Object value =
                master
                        ?.get(
                                key
                        )
                        ?.get(
                                'VALUE'
                        )


        Collection<?> values


        if (value instanceof Collection) {

            values =
                    value as Collection<?>
        }
        else if (value == null) {

            return []
        }
        else {

            values =
                    value
                            .toString()
                            .split(',')
                            .toList()
        }


        return values
                .collect {
                    Object item ->

                        item
                                ?.toString()
                                ?.trim()
                                ?.toLowerCase(
                                        Locale.ROOT
                                )
                }
                .findAll {
                    String item ->

                        item != null &&
                                !item.isBlank()
                }
                .unique()
    }


    // ========================================================
    // Link
    // ========================================================

    private static String buildRelativeLink(
            Project rootProject,
            File target
    ) {

        String relativePath =
                rootProject
                        .projectDir
                        .toPath()
                        .toAbsolutePath()
                        .normalize()
                        .relativize(
                                target
                                        .toPath()
                                        .toAbsolutePath()
                                        .normalize()
                        )
                        .toString()


        return './' +
                GradleBuildUtils.normalizePath(
                        relativePath
                )
    }


    // ========================================================
    // Output
    // ========================================================

    private void writeIfChanged(
            File outputFile,
            String content
    ) {

        if (
                outputFile.exists() &&
                        !outputFile.isFile()
        ) {

            throw new GradleException(
                    """
Project structure output path is not a file:

${outputFile.absolutePath}
""".stripIndent()
            )
        }


        if (
                outputFile.isFile() &&
                        outputFile.getText(
                                'UTF-8'
                        ) == content
        ) {

            logger.info(
                    '[PROJECT-STRUCTURE] UP-TO-DATE: {}',
                    outputFile.absolutePath
            )

            return
        }


        File parentDirectory =
                outputFile.parentFile


        if (
                !parentDirectory.isDirectory() &&
                        !parentDirectory.mkdirs() &&
                        !parentDirectory.isDirectory()
        ) {

            throw new GradleException(
                    """
Unable to create project structure output directory:

${parentDirectory.absolutePath}
""".stripIndent()
            )
        }


        try {

            outputFile.setText(
                    content,
                    'UTF-8'
            )
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to write project structure file:

${outputFile.absolutePath}

Reason:
${exception.message}
""".stripIndent(),
                    exception
            )
        }


        logger.lifecycle(
                '🌳 [PROJECT-STRUCTURE] Generated: {}',
                outputFile.absolutePath
        )
    }


    private static class StructureNode {

        File directory
        String id
        String name
        String path
        boolean realModule
        String serviceName
        String moduleType
        String javaBasePackage
        String description
        String sourceFingerprint
        boolean moduleDepend
        boolean codeExplorerAvailable
        Map<String, File> overviewSources = [:]
        Set<String> knowledgeLanguages = [] as Set<String>
        Set<String> summaryLanguages = [] as Set<String>
        Set<String> videoLanguages = [] as Set<String>
        Set<String> roadmapLanguages = [] as Set<String>
        boolean referenceAvailable
        Set<String> apiLanguages = [] as Set<String>
        Map<String, Integer> quizQuestionCounts = [:]
        Map<String, Integer> interviewQuestionCounts = [:]
        List<StructureNode> children = []
    }
}
