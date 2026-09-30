package com.example.learning.setup.root.structure.service

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer
import java.net.URLDecoder


class PortalKnowledgeSearchProjectionService {

    private static final String PORTAL_DATA_DIRECTORY =
            'project-portal/build/generated/portal-data'

    private static final String MODULE_DIRECTORY =
            "${PORTAL_DATA_DIRECTORY}/module"

    private static final String OUTPUT_DIRECTORY =
            "${PORTAL_DATA_DIRECTORY}/search"

    private static final int PREVIEW_LIMIT = 220

    private static final Set<String> SUPPORTED_NODE_KINDS =
            ['FOLDER', 'CATEGORY'] as Set<String>

    private final Logger logger
    private final JsonSlurper jsonSlurper


    PortalKnowledgeSearchProjectionService(
            Logger logger
    ) {

        this.logger = logger
        this.jsonSlurper = new JsonSlurper()
    }


    void generate(
            Project rootProject
    ) {

        File portalDataDirectory =
                new File(
                        rootProject.projectDir,
                        PORTAL_DATA_DIRECTORY
                )

        File moduleDirectory =
                new File(
                        rootProject.projectDir,
                        MODULE_DIRECTORY
                )

        if (!moduleDirectory.isDirectory()) {
            throw new GradleException(
                    """
Portal module projection directory was not found:

${moduleDirectory.absolutePath}

Run generatePortalKnowledge before generating the Knowledge search projection.
""".stripIndent()
            )
        }

        Map<String, List<Map<String, Object>>> documentsByLanguage =
                new TreeMap<>()

        List<File> moduleDirectories =
                moduleDirectory
                        .listFiles()
                        ?.findAll { File child -> child.isDirectory() }
                        ?.sort { File left, File right -> left.name <=> right.name }
                        ?: []

        moduleDirectories.each {
            File projectedModuleDirectory ->

                collectModuleKnowledge(
                        portalDataDirectory,
                        projectedModuleDirectory,
                        documentsByLanguage
                )
        }

        File outputDirectory =
                new File(
                        rootProject.projectDir,
                        OUTPUT_DIRECTORY
                )

        ensureDirectory(outputDirectory)

        Set<String> expectedFileNames =
                new LinkedHashSet<>()

        documentsByLanguage.each {
            String language,
            List<Map<String, Object>> documents ->

                String fileName =
                        "knowledge-search.${language}.json"

                expectedFileNames.add(fileName)

                Map<String, Object> payload =
                        [
                                version      : 1,
                                language     : language,
                                documentCount: documents.size(),
                                documents    : documents
                        ]

                writeIfChanged(
                        new File(outputDirectory, fileName),
                        JsonOutput.prettyPrint(
                                JsonOutput.toJson(payload)
                        ) + '\n'
                )
        }

        removeStaleKnowledgeSearchFiles(
                outputDirectory,
                expectedFileNames
        )

        int totalDocuments =
                documentsByLanguage
                        .values()
                        .sum { List<Map<String, Object>> documents -> documents.size() }
                        ?: 0

        logger.lifecycle(
                '🔎 [PORTAL-KNOWLEDGE-SEARCH] Generated {} documents across {} language index file(s).',
                totalDocuments,
                documentsByLanguage.size()
        )
    }


    private void collectModuleKnowledge(
            File portalDataDirectory,
            File projectedModuleDirectory,
            Map<String, List<Map<String, Object>>> documentsByLanguage
    ) {

        File knowledgeDirectory =
                new File(
                        projectedModuleDirectory,
                        'knowledge'
                )

        if (!knowledgeDirectory.isDirectory()) {
            return
        }

        List<File> languageDirectories =
                knowledgeDirectory
                        .listFiles()
                        ?.findAll { File child -> child.isDirectory() }
                        ?.sort { File left, File right -> left.name <=> right.name }
                        ?: []

        languageDirectories.each {
            File languageDirectory ->

                File indexFile =
                        new File(
                                languageDirectory,
                                'index.json'
                        )

                if (!indexFile.isFile()) {
                    return
                }

                Map index =
                        parseIndex(indexFile)

                String moduleId =
                        requireText(
                                index.moduleId,
                                'moduleId',
                                indexFile
                        )

                String language =
                        requireText(
                                index.language,
                                'language',
                                indexFile
                        )

                if (moduleId != projectedModuleDirectory.name) {
                    throw new GradleException(
                            """
Portal Knowledge index moduleId does not match its projection directory.

Index:
${indexFile.absolutePath}

Directory:
${projectedModuleDirectory.name}

moduleId:
${moduleId}
""".stripIndent()
                    )
                }

                if (language != languageDirectory.name) {
                    throw new GradleException(
                            """
Portal Knowledge index language does not match its projection directory.

Index:
${indexFile.absolutePath}

Directory:
${languageDirectory.name}

language:
${language}
""".stripIndent()
                    )
                }

                Object treeValue =
                        index.tree

                if (!(treeValue instanceof List)) {
                    throw new GradleException(
                            """
Portal Knowledge index tree must be a list.

Index:
${indexFile.absolutePath}
""".stripIndent()
                    )
                }

                List<Map<String, Object>> documents =
                        documentsByLanguage.computeIfAbsent(
                                language
                        ) {
                            []
                        }

                collectTreeDocuments(
                        portalDataDirectory,
                        moduleId,
                        language,
                        treeValue as List,
                        indexFile,
                        documents
                )
        }
    }


    private Map parseIndex(
            File indexFile
    ) {

        Object parsed

        try {
            parsed =
                    jsonSlurper.parse(
                            indexFile,
                            'UTF-8'
                    )
        } catch (Exception exception) {
            throw new GradleException(
                    """
Unable to parse Portal Knowledge index:

${indexFile.absolutePath}
""".stripIndent(),
                    exception
            )
        }

        if (!(parsed instanceof Map)) {
            throw new GradleException(
                    """
Portal Knowledge index root must be a JSON object:

${indexFile.absolutePath}
""".stripIndent()
            )
        }

        return parsed as Map
    }


    private void collectTreeDocuments(
            File portalDataDirectory,
            String moduleId,
            String language,
            List nodes,
            File indexFile,
            List<Map<String, Object>> documents
    ) {

        nodes.each {
            Object nodeValue ->

                if (!(nodeValue instanceof Map)) {
                    throw new GradleException(
                            """
Portal Knowledge tree contains a non-object node.

Index:
${indexFile.absolutePath}
""".stripIndent()
                    )
                }

                Map node =
                        nodeValue as Map

                String kind =
                        requireText(
                                node.kind,
                                'tree.kind',
                                indexFile
                        )

                if (!SUPPORTED_NODE_KINDS.contains(kind)) {
                    throw new GradleException(
                            """
Unsupported Portal Knowledge tree node kind.

Index:
${indexFile.absolutePath}

Kind:
${kind}
""".stripIndent()
                    )
                }

                if (kind == 'FOLDER') {
                    Object childrenValue = node.children

                    if (!(childrenValue instanceof List)) {
                        throw new GradleException(
                                """
Portal Knowledge folder children must be a list.

Index:
${indexFile.absolutePath}
""".stripIndent()
                        )
                    }

                    collectTreeDocuments(
                            portalDataDirectory,
                            moduleId,
                            language,
                            childrenValue as List,
                            indexFile,
                            documents
                    )

                    return
                }

                collectCategoryDocuments(
                        portalDataDirectory,
                        moduleId,
                        language,
                        node,
                        indexFile,
                        documents
                )
        }
    }


    private void collectCategoryDocuments(
            File portalDataDirectory,
            String moduleId,
            String language,
            Map category,
            File indexFile,
            List<Map<String, Object>> documents
    ) {

        String categoryId =
                requireText(
                        category.id,
                        'category.id',
                        indexFile
                )

        String categoryTitle =
                requireText(
                        category.title,
                        'category.title',
                        indexFile
                )

        String sourcePath =
                requireText(
                        category.sourcePath,
                        'category.sourcePath',
                        indexFile
                )

        String introductionMarkdown =
                readOptionalProjectedContent(
                        portalDataDirectory,
                        category.intro,
                        indexFile
                )

        String introductionText =
                markdownToPlainText(
                        introductionMarkdown
                )

        documents.add(
                [
                        documentId  : "${moduleId}:${language}:CATEGORY:${categoryId}",
                        type        : 'CATEGORY',
                        moduleId    : moduleId,
                        categoryId  : categoryId,
                        categoryTitle: categoryTitle,
                        title       : categoryTitle,
                        sourcePath  : sourcePath,
                        preview     : createPreview(introductionText),
                        searchText  : normalizeSearchText(
                                [
                                        categoryTitle,
                                        categoryId,
                                        introductionText
                                ].join(' ')
                        )
                ]
        )

        Object sectionsValue =
                category.sections

        if (!(sectionsValue instanceof List)) {
            throw new GradleException(
                    """
Portal Knowledge category sections must be a list.

Index:
${indexFile.absolutePath}

Category:
${categoryId}
""".stripIndent()
            )
        }

        sectionsValue.each {
            Object sectionValue ->

                if (!(sectionValue instanceof Map)) {
                    throw new GradleException(
                            """
Portal Knowledge category contains a non-object section.

Index:
${indexFile.absolutePath}

Category:
${categoryId}
""".stripIndent()
                    )
                }

                Map section =
                        sectionValue as Map

                String sectionId =
                        requireText(
                                section.id,
                                'section.id',
                                indexFile
                        )

                String sectionTitle =
                        requireText(
                                section.title,
                                'section.title',
                                indexFile
                        )

                String contentPath =
                        requireText(
                                section.content,
                                'section.content',
                                indexFile
                        )

                String sectionMarkdown =
                        readProjectedContent(
                                portalDataDirectory,
                                contentPath,
                                indexFile
                        )

                String sectionText =
                        markdownToPlainText(
                                sectionMarkdown
                        )

                Map<String, Object> document =
                        [
                                documentId   : "${moduleId}:${language}:SECTION:${categoryId}:${sectionId}",
                                type         : 'SECTION',
                                moduleId     : moduleId,
                                categoryId   : categoryId,
                                categoryTitle: categoryTitle,
                                sectionId    : sectionId,
                                title        : sectionTitle,
                                sourcePath   : sourcePath,
                                preview      : createPreview(sectionText),
                                searchText   : normalizeSearchText(
                                        [
                                                sectionTitle,
                                                sectionId,
                                                categoryTitle,
                                                categoryId,
                                                sectionText
                                        ].join(' ')
                                )
                        ]

                String difficulty =
                        section.difficulty
                                ?.toString()
                                ?.trim()

                if (difficulty) {
                    document.difficulty = difficulty
                }

                documents.add(document)
        }
    }


    private static String readOptionalProjectedContent(
            File portalDataDirectory,
            Object publicPathValue,
            File indexFile
    ) {

        String publicPath =
                publicPathValue
                        ?.toString()
                        ?.trim()

        if (!publicPath) {
            return ''
        }

        return readProjectedContent(
                portalDataDirectory,
                publicPath,
                indexFile
        )
    }


    private static String readProjectedContent(
            File portalDataDirectory,
            String publicPath,
            File indexFile
    ) {

        String relativePath =
                publicPath.startsWith('/')
                        ? publicPath.substring(1)
                        : publicPath

        String decodedRelativePath =
                relativePath
                        .split('/', -1)
                        .collect {
                            String segment ->

                                URLDecoder.decode(
                                        segment,
                                        'UTF-8'
                                )
                        }
                        .join('/')

        Path rootPath =
                portalDataDirectory
                        .toPath()
                        .toAbsolutePath()
                        .normalize()

        Path contentPath =
                rootPath
                        .resolve(decodedRelativePath)
                        .normalize()

        if (!contentPath.startsWith(rootPath)) {
            throw new GradleException(
                    """
Portal Knowledge content path escapes the generated Portal data directory.

Index:
${indexFile.absolutePath}

Path:
${publicPath}
""".stripIndent()
            )
        }

        File contentFile =
                contentPath.toFile()

        if (!contentFile.isFile()) {
            throw new GradleException(
                    """
Portal Knowledge content referenced by index was not found.

Index:
${indexFile.absolutePath}

Content:
${contentFile.absolutePath}
""".stripIndent()
            )
        }

        return contentFile.getText('UTF-8')
    }


    private static String markdownToPlainText(
            String markdown
    ) {

        if (markdown == null || markdown.isBlank()) {
            return ''
        }

        String value = markdown

        value = value.replaceAll('(?ms)<!--.*?-->', ' ')
        value = value.replaceAll('(?m)^\\s*```[^\\r\\n]*$', ' ')
        value = value.replaceAll('(?m)^\\s*~~~[^\\r\\n]*$', ' ')
        value = value.replaceAll('!\\[([^\\]]*)]\\([^)]*\\)', '$1')
        value = value.replaceAll('\\[([^\\]]+)]\\([^)]*\\)', '$1')
        value = value.replaceAll(
                '(?is)</?(?:a|p|br|strong|em|ul|ol|li|table|thead|tbody|tr|th|td|details|summary|div|span|img|h[1-6]|pre|code)\\b[^>]*>',
                ' '
        )
        value = value.replaceAll('(?m)^\\s{0,3}#{1,6}\\s*', '')
        value = value.replaceAll('(?m)^\\s*>+\\s?', '')
        value = value.replaceAll('(?m)^\\s*[-+*]\\s+', '')
        value = value.replaceAll('(?m)^\\s*\\d+[.)]\\s+', '')
        value = value.replace('`', '')
        value = value.replace('**', '')
        value = value.replace('~~', '')
        value = value.replace('|', ' ')

        value = decodeCommonHtmlEntities(value)

        return value
                .replaceAll('\\s+', ' ')
                .trim()
    }


    private static String decodeCommonHtmlEntities(
            String value
    ) {

        return value
                .replace('&nbsp;', ' ')
                .replace('&lt;', '<')
                .replace('&gt;', '>')
                .replace('&quot;', '"')
                .replace('&#39;', "'")
                .replace('&amp;', '&')
    }


    private static String normalizeSearchText(
            String value
    ) {

        if (value == null || value.isBlank()) {
            return ''
        }

        String decomposed =
                Normalizer.normalize(
                        value,
                        Normalizer.Form.NFD
                )

        return decomposed
                .replaceAll('\\p{M}+', '')
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll('[^\\p{L}\\p{N}]+', ' ')
                .replaceAll('\\s+', ' ')
                .trim()
    }


    private static String createPreview(
            String plainText
    ) {

        if (plainText == null || plainText.isBlank()) {
            return ''
        }

        if (plainText.length() <= PREVIEW_LIMIT) {
            return plainText
        }

        String candidate =
                plainText.substring(
                        0,
                        PREVIEW_LIMIT
                )

        int lastSpace =
                candidate.lastIndexOf(' ')

        if (lastSpace >= PREVIEW_LIMIT / 2) {
            candidate = candidate.substring(0, lastSpace)
        }

        return candidate.trim() + '…'
    }


    private static String requireText(
            Object value,
            String field,
            File indexFile
    ) {

        String text =
                value
                        ?.toString()
                        ?.trim()

        if (!text) {
            throw new GradleException(
                    """
Portal Knowledge search projection requires a non-blank field.

Index:
${indexFile.absolutePath}

Field:
${field}
""".stripIndent()
            )
        }

        return text
    }


    private static void ensureDirectory(
            File directory
    ) {

        if (directory.isDirectory()) {
            return
        }

        if (directory.exists()) {
            throw new GradleException(
                    """
Portal Knowledge search output path is not a directory:

${directory.absolutePath}
""".stripIndent()
            )
        }

        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new GradleException(
                    """
Unable to create Portal Knowledge search output directory:

${directory.absolutePath}
""".stripIndent()
            )
        }
    }


    private static void writeIfChanged(
            File outputFile,
            String content
    ) {

        byte[] nextBytes =
                content.getBytes(
                        StandardCharsets.UTF_8
                )

        if (
                outputFile.isFile() &&
                        Arrays.equals(
                                Files.readAllBytes(outputFile.toPath()),
                                nextBytes
                        )
        ) {
            return
        }

        Files.write(
                outputFile.toPath(),
                nextBytes
        )
    }


    private static void removeStaleKnowledgeSearchFiles(
            File outputDirectory,
            Set<String> expectedFileNames
    ) {

        File[] files =
                outputDirectory.listFiles()

        if (files == null) {
            throw new GradleException(
                    """
Unable to list Portal Knowledge search output directory:

${outputDirectory.absolutePath}
""".stripIndent()
            )
        }

        files
                .findAll {
                    File file ->

                        file.isFile() &&
                                file.name.startsWith('knowledge-search.') &&
                                file.name.endsWith('.json') &&
                                !expectedFileNames.contains(file.name)
                }
                .each {
                    File staleFile ->

                        if (!staleFile.delete() && staleFile.exists()) {
                            throw new GradleException(
                                    """
Unable to remove stale Portal Knowledge search index:

${staleFile.absolutePath}
""".stripIndent()
                            )
                        }
                }
    }
}
