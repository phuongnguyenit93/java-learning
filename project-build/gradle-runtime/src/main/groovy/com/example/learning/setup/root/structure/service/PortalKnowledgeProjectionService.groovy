package com.example.learning.setup.root.structure.service

import com.example.learning.setup.module.readme.service.ReadmeKnowledgeParser
import com.example.learning.setup.root.readmeMetadata.service.ReadmeMetadataSyncService
import com.example.learning.utils.GradleBuildUtils
import com.example.learning.utils.ProjectPropertyUtils
import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.yaml.snakeyaml.Yaml

import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern


class PortalKnowledgeProjectionService {

    private static final String OUTPUT_DIRECTORY =
            'project-portal/build/generated/portal-data/module'


    private static final Pattern BACK_TO_TOP_ANCHOR_PATTERN =
            Pattern.compile(
                    '(?m)^\\s*<a\\s+id\\s*=\\s*["\']back-to-top["\']\\s*>\\s*</a>\\s*$'
            )


    private static final Pattern GENERATED_MENU_PATTERN =
            Pattern.compile(
                    '(?ms)' +
                            '^##\\s+Menu\\s*\\n' +
                            '(?:' +
                            '\\s*-\\s+\\[[^\\n]*\\]\\(#[^\\n)]*\\)\\s*\\n?' +
                            ')*'
            )


    private static final Pattern DETAILS_OPEN_PATTERN =
            Pattern.compile(
                    '(?s)^\\s*<details>\\s*' +
                            '<summary>Click for details</summary>\\s*'
            )


    private static final Pattern DETAILS_CLOSE_PATTERN =
            Pattern.compile(
                    '(?s)' +
                            '\\s*</details>\\s*' +
                            '(?:-\\s+\\[Quay lại đầu trang\\]\\(#back-to-top\\)\\s*)?' +
                            '(?:---\\s*)?' +
                            '$'
            )


    private static final Pattern BACK_TO_TOP_LINK_PATTERN =
            Pattern.compile(
                    '(?m)^\\s*-\\s+\\[Quay lại đầu trang\\]\\(#back-to-top\\)\\s*$'
            )


    private static final Pattern MARKDOWN_IMAGE_PATTERN =
            Pattern.compile(
                    '(!\\[[^\\]]*\\]\\()(<[^>]+>|[^\\s)]+)([^)]*\\))'
            )


    private static final Pattern HTML_IMAGE_PATTERN =
            Pattern.compile(
                    '(?i)(<img\\b[^>]*?\\bsrc\\s*=\\s*["\'])([^"\']+)(["\'])'
            )


    private static final Pattern MARKDOWN_LINK_PATTERN =
            Pattern.compile(
                    '(?<!\\!)\\[[^\\]]*\\]\\((<[^>]+>|[^\\s)]+)(?:\\s+[^)]*)?\\)'
            )


    private static final Pattern HTML_LINK_PATTERN =
            Pattern.compile(
                    '(?i)<a\\b[^>]*?\\bhref\\s*=\\s*["\']([^"\']+)["\']'
            )


    private final Logger logger
    private final ReadmeKnowledgeParser knowledgeParser
    private final Yaml yamlReader


    PortalKnowledgeProjectionService(
            Logger logger
    ) {

        this.logger =
                logger


        this.knowledgeParser =
                new ReadmeKnowledgeParser(
                        logger
                )


        this.yamlReader =
                new Yaml()
    }


    void generate(
            Project rootProject
    ) {

        File moduleRoot =
                new File(
                        rootProject.projectDir,
                        'module'
                )


        if (!moduleRoot.isDirectory()) {

            throw new GradleException(
                    """
Project module directory was not found:

${moduleRoot.absolutePath}
""".stripIndent()
            )
        }


        File outputDirectory =
                new File(
                        rootProject.projectDir,
                        OUTPUT_DIRECTORY
                )


        Map<String, String> textOutputs =
                new LinkedHashMap<>()


        Map<String, byte[]> binaryOutputs =
                new LinkedHashMap<>()


        Set<String> routeIds =
                new LinkedHashSet<>()


        List<Project> modules =
                resolveRealModuleProjects(
                        rootProject,
                        moduleRoot
                )


        modules.each {
            Project moduleProject ->

                String routeId =
                        resolveRouteId(
                                moduleProject
                        )


                List<LanguageSource> languageSources =
                        resolveLanguageSources(
                                moduleProject
                        )


                if (languageSources.isEmpty()) {
                    return
                }


                if (!routeIds.add(routeId)) {

                    throw new GradleException(
                            """
Duplicate Portal knowledge route identifier detected:

${routeId}

Route identifiers must be unique for modules that expose README knowledge.
""".stripIndent()
                    )
                }


                languageSources.each {
                    LanguageSource languageSource ->

                        generateLanguageProjection(
                                moduleProject,
                                routeId,
                                languageSource,
                                textOutputs,
                                binaryOutputs
                        )
                }
        }


        writeTextOutputs(
                outputDirectory,
                textOutputs
        )


        writeBinaryOutputs(
                outputDirectory,
                binaryOutputs
        )


        removeStaleFiles(
                outputDirectory,
                (
                        textOutputs.keySet() +
                                binaryOutputs.keySet()
                ) as Set<String>
        )


        logger.lifecycle(
                '📚 [PORTAL-KNOWLEDGE] Generated {} text files and {} binary assets.',
                textOutputs.size(),
                binaryOutputs.size()
        )
    }


    private void generateLanguageProjection(
            Project moduleProject,
            String routeId,
            LanguageSource languageSource,
            Map<String, String> textOutputs,
            Map<String, byte[]> binaryOutputs
    ) {

        Set<String> categoryIds =
                new LinkedHashSet<>()


        Map<String, String> categorySources =
                new LinkedHashMap<>()


        Set<String> sectionIds =
                new LinkedHashSet<>()


        Map<String, String> sectionSources =
                new LinkedHashMap<>()


        Counter counter =
                new Counter()


        Map<String, Object> knowledgeMetadata =
                loadKnowledgeMetadata(
                        moduleProject,
                        languageSource.language
                )


        List<Map> tree =
                buildTree(
                        moduleProject,
                        routeId,
                        languageSource,
                        languageSource.menuDirectory,
                        knowledgeMetadata,
                        categoryIds,
                        categorySources,
                        sectionIds,
                        sectionSources,
                        textOutputs,
                        binaryOutputs,
                        counter
                )


        Map index =
                [
                        version      : 1,
                        moduleId     : routeId,
                        language     : languageSource.language,
                        categoryCount: counter.categoryCount,
                        sectionCount : counter.sectionCount,
                        tree         : tree
                ]


        String indexPath =
                "${routeId}/knowledge/${languageSource.language}/index.json"


        putTextOutput(
                textOutputs,
                indexPath,
                JsonOutput.prettyPrint(
                        JsonOutput.toJson(index)
                ) + '\n'
        )
    }


    private List<Map> buildTree(
            Project moduleProject,
            String routeId,
            LanguageSource languageSource,
            File currentDirectory,
            Map<String, Object> knowledgeMetadata,
            Set<String> categoryIds,
            Map<String, String> categorySources,
            Set<String> sectionIds,
            Map<String, String> sectionSources,
            Map<String, String> textOutputs,
            Map<String, byte[]> binaryOutputs,
            Counter counter
    ) {

        List<Map> result = []


        getSortedChildren(
                currentDirectory
        ).each {
            File child ->

                if (child.name.startsWith('.')) {
                    return
                }


                if (child.isDirectory()) {

                    List<Map> children =
                            buildTree(
                                    moduleProject,
                                    routeId,
                                    languageSource,
                                    child,
                                    knowledgeMetadata,
                                    categoryIds,
                                    categorySources,
                                    sectionIds,
                                    sectionSources,
                                    textOutputs,
                                    binaryOutputs,
                                    counter
                            )


                    if (!children.isEmpty()) {

                        result.add(
                                [
                                        kind    : 'FOLDER',
                                        id      : relativePath(
                                                languageSource.menuDirectory,
                                                child
                                        ),
                                        title   : child.name,
                                        children: children
                                ]
                        )
                    }


                    return
                }


                if (
                        !child.isFile() ||
                                !child.name.toLowerCase(Locale.ROOT).endsWith('.md')
                ) {

                    return
                }


                Map category =
                        buildCategory(
                                moduleProject,
                                routeId,
                                languageSource,
                                child,
                                knowledgeMetadata,
                                categoryIds,
                                categorySources,
                                sectionIds,
                                sectionSources,
                                textOutputs,
                                binaryOutputs,
                                counter
                        )


                result.add(
                        category
                )
        }


        return result
    }


    private Map buildCategory(
            Project moduleProject,
            String routeId,
            LanguageSource languageSource,
            File markdownFile,
            Map<String, Object> knowledgeMetadata,
            Set<String> categoryIds,
            Map<String, String> categorySources,
            Set<String> sectionIds,
            Map<String, String> sectionSources,
            Map<String, String> textOutputs,
            Map<String, byte[]> binaryOutputs,
            Counter counter
    ) {

        String categoryId =
                markdownFile.name.substring(
                        0,
                        markdownFile.name.length() - '.md'.length()
                )


        String categoryKey =
                categoryId.toLowerCase(
                        Locale.ROOT
                )


        String sourcePath =
                relativePath(
                        languageSource.menuDirectory,
                        markdownFile
                )


        if (!categoryIds.add(categoryKey)) {

            throw new GradleException(
                    """
Duplicate Portal knowledge category id detected.

Module:
${routeId}

Language:
${languageSource.language}

Category id:
${categoryId}

First source:
${categorySources[categoryKey]}

Duplicate source:
${sourcePath}

Category ids are derived from Markdown filenames and must be unique within one module/language.
""".stripIndent()
            )
        }


        categorySources[categoryKey] =
                sourcePath


        String content =
                normalizeLineEnding(
                        markdownFile.getText(
                                'UTF-8'
                        )
                )


        List<ReadmeKnowledgeParser.SectionCandidate> candidates =
                knowledgeParser.parse(
                        markdownFile,
                        content
                )


        Map<String, Object> fileMetadata =
                resolveMetadataMap(
                        knowledgeMetadata[
                                sourcePath
                        ],
                        """
Invalid Portal README knowledge metadata file entry.

Module:
${routeId}

Language:
${languageSource.language}

README file:
${sourcePath}
""".stripIndent()
                )


        int introductionEnd =
                candidates.isEmpty()
                        ? content.length()
                        : candidates.first().start


        String introduction =
                cleanIntroduction(
                        content.substring(
                                0,
                                introductionEnd
                        )
                )


        String encodedCategoryId =
                encodePathSegment(
                        categoryId
                )


        String categoryOutputDirectory =
                "${routeId}/knowledge/${languageSource.language}/content/${categoryId}"


        String categoryPublicDirectory =
                "/module/${encodePathSegment(routeId)}/knowledge/${encodePathSegment(languageSource.language)}/content/${encodedCategoryId}"


        String introPublicPath =
                null


        if (!introduction.isBlank()) {

            String processedIntroduction =
                    processContentReferences(
                            moduleProject,
                            routeId,
                            languageSource,
                            markdownFile,
                            introduction,
                            binaryOutputs
                    )


            putTextOutput(
                    textOutputs,
                    "${categoryOutputDirectory}/intro.md",
                    ensureTrailingNewline(
                            processedIntroduction
                    )
            )


            introPublicPath =
                    "${categoryPublicDirectory}/intro.md"
        }


        List<Map> sections = []


        candidates.eachWithIndex {
            ReadmeKnowledgeParser.SectionCandidate candidate,
            int index ->

                int bodyEnd =
                        index + 1 < candidates.size()
                                ? candidates[index + 1].start
                                : content.length()


                if (!candidate.valid) {
                    return
                }


                String sectionId =
                        candidate.id


                if (!sectionIds.add(sectionId)) {

                    throw new GradleException(
                            """
Duplicate Portal knowledge section id detected.

Module:
${routeId}

Language:
${languageSource.language}

Section id:
${sectionId}

First source:
${sectionSources[sectionId]}

Duplicate source:
${sourcePath}

Section ids must be unique within one module/language.
""".stripIndent()
                    )
                }


                sectionSources[sectionId] =
                        sourcePath


                String body =
                        cleanSectionBody(
                                content.substring(
                                        candidate.end,
                                        bodyEnd
                                )
                        )


                body =
                        processContentReferences(
                                moduleProject,
                                routeId,
                                languageSource,
                                markdownFile,
                                body,
                                binaryOutputs
                        )


                String outputPath =
                        "${categoryOutputDirectory}/sections/${sectionId}.md"


                String publicPath =
                        "${categoryPublicDirectory}/sections/${encodePathSegment(sectionId)}.md"


                putTextOutput(
                        textOutputs,
                        outputPath,
                        ensureTrailingNewline(
                                body
                        )
                )


                Map<String, Object> sectionMetadata =
                        resolveSectionMetadata(
                                fileMetadata[
                                        sectionId
                                ],
                                routeId,
                                languageSource.language,
                                sourcePath,
                                sectionId
                        )


                sections.add(
                        [
                                id         : sectionId,
                                title      : candidate.title,
                                content    : publicPath,
                                difficulty : sectionMetadata.difficulty,
                                aiGenerated: sectionMetadata.aiGenerated,
                                reviewed   : sectionMetadata.reviewed
                        ]
                )


                counter.sectionCount++
        }


        counter.categoryCount++


        Map category =
                [
                        kind      : 'CATEGORY',
                        id        : categoryId,
                        title     : categoryId,
                        sourcePath: sourcePath,
                        sections  : sections
                ]


        if (introPublicPath != null) {
            category.intro = introPublicPath
        }


        return category
    }


    private Map<String, Object> loadKnowledgeMetadata(
            Project moduleProject,
            String language
    ) {

        File metadataFile =
                new File(
                        moduleProject.projectDir,
                        "src/main/resources/readme/${language}/${ReadmeMetadataSyncService.METADATA_FILE_NAME}"
                )


        if (
                !metadataFile.exists() ||
                        metadataFile.length() == 0
        ) {

            return new LinkedHashMap<>()
        }


        if (!metadataFile.isFile()) {

            throw new GradleException(
                    """
Portal README knowledge metadata path exists but is not a file:

${metadataFile.absolutePath}
""".stripIndent()
            )
        }


        Object loaded


        try {

            loaded =
                    yamlReader.load(
                            metadataFile.getText(
                                    'UTF-8'
                            )
                    )
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to parse Portal README knowledge metadata YAML.

File:
${metadataFile.absolutePath}

Cause:
${exception.message}
""".stripIndent(),
                    exception
            )
        }


        if (loaded == null) {

            return new LinkedHashMap<>()
        }


        if (!(loaded instanceof Map)) {

            throw new GradleException(
                    """
Invalid Portal README knowledge metadata YAML structure.

Expected root object to be a map.

File:
${metadataFile.absolutePath}
""".stripIndent()
            )
        }


        return loaded as Map<String, Object>
    }


    private static Map<String, Object> resolveMetadataMap(
            Object value,
            String message
    ) {

        if (value == null) {

            return new LinkedHashMap<>()
        }


        if (!(value instanceof Map)) {

            throw new GradleException(
                    message.trim()
            )
        }


        return value as Map<String, Object>
    }


    private static Map<String, Object> resolveSectionMetadata(
            Object value,
            String routeId,
            String language,
            String sourcePath,
            String sectionId
    ) {

        Map<String, Object> section =
                resolveMetadataMap(
                        value,
                        """
Invalid Portal README knowledge metadata section entry.

Module:
${routeId}

Language:
${language}

README file:
${sourcePath}

Section id:
${sectionId}
""".stripIndent()
                )


        Object difficultyValue =
                section[
                        'difficulty'
                ]


        String difficulty =
                difficultyValue == null ||
                        difficultyValue.toString().trim().isBlank()
                        ? ReadmeMetadataSyncService.DEFAULT_DIFFICULTY
                        : difficultyValue
                                .toString()
                                .trim()
                                .toUpperCase(
                                        Locale.ROOT
                                )


        if (
                !ReadmeMetadataSyncService.ALLOWED_DIFFICULTIES.contains(
                        difficulty
                )
        ) {

            throw new GradleException(
                    """
Invalid Portal README knowledge difficulty.

Module:
${routeId}

Language:
${language}

README file:
${sourcePath}

Section id:
${sectionId}

Value:
${difficultyValue}

Allowed values:
${ReadmeMetadataSyncService.ALLOWED_DIFFICULTIES.join(', ')}
""".stripIndent()
            )
        }


        boolean aiGenerated =
                resolveBooleanMetadata(
                        section,
                        'aiGenerated',
                        ReadmeMetadataSyncService.DEFAULT_AI_GENERATED,
                        routeId,
                        language,
                        sourcePath,
                        sectionId
                )


        boolean reviewed =
                resolveBooleanMetadata(
                        section,
                        'reviewed',
                        ReadmeMetadataSyncService.DEFAULT_REVIEWED,
                        routeId,
                        language,
                        sourcePath,
                        sectionId
                )


        return [
                difficulty : difficulty,
                aiGenerated: aiGenerated,
                reviewed   : reviewed
        ]
    }


    private static boolean resolveBooleanMetadata(
            Map<String, Object> section,
            String field,
            boolean defaultValue,
            String routeId,
            String language,
            String sourcePath,
            String sectionId
    ) {

        Object value =
                section[
                        field
                ]


        if (value == null) {

            return defaultValue
        }


        if (value instanceof Boolean) {

            return value as boolean
        }


        throw new GradleException(
                """
Invalid Portal README knowledge boolean metadata.

Module:
${routeId}

Language:
${language}

README file:
${sourcePath}

Section id:
${sectionId}

Field:
${field}

Value:
${value}

Expected YAML boolean true or false.
""".stripIndent()
        )
    }


    private String processContentReferences(
            Project moduleProject,
            String routeId,
            LanguageSource languageSource,
            File sourceFile,
            String content,
            Map<String, byte[]> binaryOutputs
    ) {

        String result =
                rewriteMarkdownImages(
                        moduleProject,
                        routeId,
                        languageSource,
                        sourceFile,
                        content,
                        binaryOutputs
                )


        result =
                rewriteHtmlImages(
                        moduleProject,
                        routeId,
                        languageSource,
                        sourceFile,
                        result,
                        binaryOutputs
                )


        validateRawMarkdownLinks(
                sourceFile,
                result
        )


        validateRawHtmlLinks(
                sourceFile,
                result
        )


        return result
    }


    private String rewriteMarkdownImages(
            Project moduleProject,
            String routeId,
            LanguageSource languageSource,
            File sourceFile,
            String content,
            Map<String, byte[]> binaryOutputs
    ) {

        Matcher matcher =
                MARKDOWN_IMAGE_PATTERN.matcher(
                        content
                )


        StringBuffer result =
                new StringBuffer()


        while (matcher.find()) {

            String rawTarget =
                    unwrapAngleBrackets(
                            matcher.group(2)
                    )


            String rewrittenTarget =
                    resolveAndCopyAsset(
                            moduleProject,
                            routeId,
                            languageSource,
                            sourceFile,
                            rawTarget,
                            binaryOutputs
                    )


            if (rewrittenTarget == null) {

                matcher.appendReplacement(
                        result,
                        Matcher.quoteReplacement(
                                matcher.group()
                        )
                )


                continue
            }


            String replacement =
                    matcher.group(1) +
                            rewrittenTarget +
                            matcher.group(3)


            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(
                            replacement
                    )
            )
        }


        matcher.appendTail(
                result
        )


        return result.toString()
    }


    private String rewriteHtmlImages(
            Project moduleProject,
            String routeId,
            LanguageSource languageSource,
            File sourceFile,
            String content,
            Map<String, byte[]> binaryOutputs
    ) {

        Matcher matcher =
                HTML_IMAGE_PATTERN.matcher(
                        content
                )


        StringBuffer result =
                new StringBuffer()


        while (matcher.find()) {

            String rewrittenTarget =
                    resolveAndCopyAsset(
                            moduleProject,
                            routeId,
                            languageSource,
                            sourceFile,
                            matcher.group(2),
                            binaryOutputs
                    )


            if (rewrittenTarget == null) {

                matcher.appendReplacement(
                        result,
                        Matcher.quoteReplacement(
                                matcher.group()
                        )
                )


                continue
            }


            String replacement =
                    matcher.group(1) +
                            rewrittenTarget +
                            matcher.group(3)


            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(
                            replacement
                    )
            )
        }


        matcher.appendTail(
                result
        )


        return result.toString()
    }


    private String resolveAndCopyAsset(
            Project moduleProject,
            String routeId,
            LanguageSource languageSource,
            File sourceFile,
            String rawTarget,
            Map<String, byte[]> binaryOutputs
    ) {

        if (!isRelativeLocalReference(rawTarget)) {
            return null
        }


        String pathPart =
                stripQueryAndFragment(
                        rawTarget
                )


        if (pathPart.isBlank()) {
            return null
        }


        File targetFile =
                new File(
                        sourceFile.parentFile,
                        pathPart
                )
                        .toPath()
                        .normalize()
                        .toFile()


        if (!targetFile.isFile()) {

            logger.warn(
                    '[PORTAL-KNOWLEDGE] Keep unresolved image/asset reference raw: {} -> {}',
                    sourceFile.absolutePath,
                    rawTarget
            )


            return null
        }


        Path modulePath =
                moduleProject.projectDir
                        .toPath()
                        .toAbsolutePath()
                        .normalize()


        Path targetPath =
                targetFile
                        .toPath()
                        .toAbsolutePath()
                        .normalize()


        if (!targetPath.startsWith(modulePath)) {

            logger.warn(
                    '[PORTAL-KNOWLEDGE] Keep asset outside module boundary raw: {} -> {}',
                    sourceFile.absolutePath,
                    rawTarget
            )


            return null
        }


        String relativeAssetPath =
                GradleBuildUtils.normalizePath(
                        modulePath
                                .relativize(
                                        targetPath
                                )
                                .toString()
                )


        String outputPath =
                "${routeId}/knowledge/${languageSource.language}/assets/${relativeAssetPath}"


        byte[] bytes =
                Files.readAllBytes(
                        targetPath
                )


        putBinaryOutput(
                binaryOutputs,
                outputPath,
                bytes
        )


        String suffix =
                rawTarget.substring(
                        pathPart.length()
                )


        return "/module/${encodePathSegment(routeId)}/knowledge/${encodePathSegment(languageSource.language)}/assets/${encodePath(relativeAssetPath)}${suffix}"
    }


    private void validateRawMarkdownLinks(
            File sourceFile,
            String content
    ) {

        Matcher matcher =
                MARKDOWN_LINK_PATTERN.matcher(
                        content
                )


        while (matcher.find()) {

            String target =
                    unwrapAngleBrackets(
                            matcher.group(1)
                    )


            validateRawLink(
                    sourceFile,
                    target
            )
        }
    }


    private void validateRawHtmlLinks(
            File sourceFile,
            String content
    ) {

        Matcher matcher =
                HTML_LINK_PATTERN.matcher(
                        content
                )


        while (matcher.find()) {

            validateRawLink(
                    sourceFile,
                    matcher.group(1)
            )
        }
    }


    private void validateRawLink(
            File sourceFile,
            String rawTarget
    ) {

        if (
                !isRelativeLocalReference(rawTarget) ||
                        rawTarget.startsWith('#')
        ) {

            return
        }


        String pathPart =
                stripQueryAndFragment(
                        rawTarget
                )


        if (pathPart.isBlank()) {
            return
        }


        File targetFile =
                new File(
                        sourceFile.parentFile,
                        pathPart
                )
                        .toPath()
                        .normalize()
                        .toFile()


        if (!targetFile.exists()) {

            logger.warn(
                    '[PORTAL-KNOWLEDGE] Keep unresolved relative link raw: {} -> {}',
                    sourceFile.absolutePath,
                    rawTarget
            )
        }
    }


    private static boolean isRelativeLocalReference(
            String target
    ) {

        if (
                target == null ||
                        target.isBlank()
        ) {

            return false
        }


        String normalized =
                target.trim()


        if (
                normalized.startsWith('/') ||
                        normalized.startsWith('\\\\') ||
                        normalized.matches('^[A-Za-z]:[\\\\/].*')
        ) {

            return false
        }


        int colonIndex =
                normalized.indexOf(':')


        int slashIndex =
                normalized.indexOf('/')


        return colonIndex < 0 ||
                (
                        slashIndex >= 0 &&
                                colonIndex > slashIndex
                )
    }


    private static String stripQueryAndFragment(
            String target
    ) {

        int queryIndex =
                target.indexOf('?')


        int fragmentIndex =
                target.indexOf('#')


        int end =
                target.length()


        if (queryIndex >= 0) {
            end = Math.min(end, queryIndex)
        }


        if (fragmentIndex >= 0) {
            end = Math.min(end, fragmentIndex)
        }


        return target.substring(
                0,
                end
        )
    }


    private static String unwrapAngleBrackets(
            String target
    ) {

        if (
                target != null &&
                        target.startsWith('<') &&
                        target.endsWith('>')
        ) {

            return target.substring(
                    1,
                    target.length() - 1
            )
        }


        return target
    }


    private static String cleanIntroduction(
            String content
    ) {

        String result =
                normalizeLineEnding(
                        content
                )


        result =
                BACK_TO_TOP_ANCHOR_PATTERN
                        .matcher(
                                result
                        )
                        .replaceAll(
                                ''
                        )


        result =
                GENERATED_MENU_PATTERN
                        .matcher(
                                result
                        )
                        .replaceAll(
                                ''
                        )


        return normalizeWhitespace(
                result
        ).trim()
    }


    private static String cleanSectionBody(
            String content
    ) {

        String result =
                normalizeLineEnding(
                        content
                )


        while (
                DETAILS_OPEN_PATTERN
                        .matcher(
                                result
                        )
                        .find()
        ) {

            result =
                    DETAILS_OPEN_PATTERN
                            .matcher(
                                    result
                            )
                            .replaceFirst(
                                    ''
                            )
        }


        while (
                DETAILS_CLOSE_PATTERN
                        .matcher(
                                result
                        )
                        .find()
        ) {

            result =
                    DETAILS_CLOSE_PATTERN
                            .matcher(
                                    result
                            )
                            .replaceFirst(
                                    ''
                            )
        }


        result =
                BACK_TO_TOP_LINK_PATTERN
                        .matcher(
                                result
                        )
                        .replaceAll(
                                ''
                        )


        return normalizeWhitespace(
                result
        ).trim()
    }


    private static List<Project> resolveRealModuleProjects(
            Project rootProject,
            File moduleRoot
    ) {

        Path moduleRootPath =
                moduleRoot
                        .toPath()
                        .toAbsolutePath()
                        .normalize()


        return rootProject
                .allprojects
                .findAll {
                    Project candidate ->

                        Path projectPath =
                                candidate
                                        .projectDir
                                        .toPath()
                                        .toAbsolutePath()
                                        .normalize()


                        projectPath.startsWith(
                                moduleRootPath
                        ) &&
                                new File(
                                        candidate.projectDir,
                                        'gradle.properties'
                                ).isFile()
                }
                .sort {
                    Project left,
                    Project right ->

                        left.projectDir.absolutePath <=>
                                right.projectDir.absolutePath
                }
    }


    private static String resolveRouteId(
            Project moduleProject
    ) {

        String serviceName =
                moduleProject
                        .findProperty(
                                'SERVICE_NAME'
                        )
                        ?.toString()
                        ?.trim()


        return serviceName != null &&
                !serviceName.isBlank()
                ? serviceName
                : moduleProject.projectDir.name
    }


    private static List<LanguageSource> resolveLanguageSources(
            Project moduleProject
    ) {

        File readmeDirectory =
                new File(
                        moduleProject.projectDir,
                        'readme'
                )


        if (!readmeDirectory.isDirectory()) {
            return []
        }


        return ProjectPropertyUtils
                .getStringList(
                        moduleProject,
                        'MODULE_LANGUAGE'
                )
                .collect {
                    String language ->

                        language.toLowerCase(
                                Locale.ROOT
                        )
                }
                .unique()
                .collect {
                    String language ->

                        File languageDirectory =
                                new File(
                                        readmeDirectory,
                                        language
                                )

                        new LanguageSource(
                                language,
                                languageDirectory,
                                new File(
                                        languageDirectory,
                                        'menu'
                                )
                        )
                }
                .findAll {
                    LanguageSource source ->

                        source.menuDirectory.isDirectory() &&
                                containsMarkdownFile(
                                        source.menuDirectory
                                )
                }
    }


    private static List<File> getSortedChildren(
            File directory
    ) {

        File[] children =
                directory.listFiles()


        if (children == null) {

            throw new GradleException(
                    """
Unable to read README knowledge directory:

${directory.absolutePath}
""".stripIndent()
            )
        }


        return children
                .toList()
                .sort {
                    File first,
                    File second ->

                        Integer firstNumber =
                                extractLeadingNumber(
                                        first.name
                                )


                        Integer secondNumber =
                                extractLeadingNumber(
                                        second.name
                                )


                        if (
                                firstNumber != null &&
                                        secondNumber != null
                        ) {

                            int numberCompare =
                                    firstNumber <=> secondNumber


                            if (numberCompare != 0) {
                                return numberCompare
                            }
                        }


                        return first.name <=> second.name
                }
    }


    private static Integer extractLeadingNumber(
            String name
    ) {

        Matcher matcher =
                Pattern.compile(
                        '^(\\d+)'
                ).matcher(
                        name
                )


        return matcher.find()
                ? matcher.group(1).toInteger()
                : null
    }


    private static boolean containsMarkdownFile(
            File directory
    ) {

        File[] children =
                directory.listFiles()


        if (children == null) {

            throw new GradleException(
                    """
Unable to read README knowledge directory:

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


    private static String relativePath(
            File root,
            File target
    ) {

        return GradleBuildUtils.normalizePath(
                root
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
        )
    }


    private static String encodePath(
            String path
    ) {

        return path
                .split(
                        '/',
                        -1
                )
                .collect {
                    String segment ->

                        encodePathSegment(
                                segment
                        )
                }
                .join(
                        '/'
                )
    }


    private static String encodePathSegment(
            String value
    ) {

        return URLEncoder
                .encode(
                        value,
                        'UTF-8'
                )
                .replace(
                        '+',
                        '%20'
                )
    }


    private static String normalizeLineEnding(
            String content
    ) {

        return content
                .replace(
                        '\r\n',
                        '\n'
                )
                .replace(
                        '\r',
                        '\n'
                )
    }


    private static String normalizeWhitespace(
            String content
    ) {

        String result =
                normalizeLineEnding(
                        content
                )


        result =
                result.replaceAll(
                        /(?m)^[ \t]+$/,
                        ''
                )


        result =
                result.replaceAll(
                        /\n{3,}/,
                        '\n\n'
                )


        return result
    }


    private static String ensureTrailingNewline(
            String content
    ) {

        String normalized =
                normalizeLineEnding(
                        content
                ).trim()


        return normalized.isBlank()
                ? ''
                : normalized + '\n'
    }


    private static void putTextOutput(
            Map<String, String> outputs,
            String relativePath,
            String content
    ) {

        if (outputs.containsKey(relativePath)) {

            throw new GradleException(
                    """
Duplicate Portal knowledge text output detected:

${relativePath}
""".stripIndent()
            )
        }


        outputs[relativePath] =
                content
    }


    private static void putBinaryOutput(
            Map<String, byte[]> outputs,
            String relativePath,
            byte[] content
    ) {

        if (!outputs.containsKey(relativePath)) {

            outputs[relativePath] =
                    content


            return
        }


        if (!Arrays.equals(outputs[relativePath], content)) {

            throw new GradleException(
                    """
Conflicting Portal knowledge binary output detected:

${relativePath}
""".stripIndent()
            )
        }
    }


    private void writeTextOutputs(
            File outputDirectory,
            Map<String, String> outputs
    ) {

        outputs.each {
            String relativePath,
            String content ->

                File outputFile =
                        new File(
                                outputDirectory,
                                relativePath
                        )


                ensureParentDirectory(
                        outputFile
                )


                if (
                        outputFile.isFile() &&
                                outputFile.getText('UTF-8') == content
                ) {

                    return
                }


                outputFile.setText(
                        content,
                        'UTF-8'
                )
        }
    }


    private void writeBinaryOutputs(
            File outputDirectory,
            Map<String, byte[]> outputs
    ) {

        outputs.each {
            String relativePath,
            byte[] content ->

                File outputFile =
                        new File(
                                outputDirectory,
                                relativePath
                        )


                ensureParentDirectory(
                        outputFile
                )


                if (
                        outputFile.isFile() &&
                                Arrays.equals(
                                        Files.readAllBytes(
                                                outputFile.toPath()
                                        ),
                                        content
                                )
                ) {

                    return
                }


                Files.write(
                        outputFile.toPath(),
                        content
                )
        }
    }


    private static void ensureParentDirectory(
            File outputFile
    ) {

        if (
                outputFile.exists() &&
                        !outputFile.isFile()
        ) {

            throw new GradleException(
                    """
Portal knowledge output path is not a file:

${outputFile.absolutePath}
""".stripIndent()
            )
        }


        File parent =
                outputFile.parentFile


        if (
                !parent.isDirectory() &&
                        !parent.mkdirs() &&
                        !parent.isDirectory()
        ) {

            throw new GradleException(
                    """
Unable to create Portal knowledge output directory:

${parent.absolutePath}
""".stripIndent()
            )
        }
    }


    private static void removeStaleFiles(
            File outputDirectory,
            Set<String> expectedRelativePaths
    ) {

        if (!outputDirectory.exists()) {
            return
        }


        if (!outputDirectory.isDirectory()) {

            throw new GradleException(
                    """
Portal knowledge output path is not a directory:

${outputDirectory.absolutePath}
""".stripIndent()
            )
        }


        List<File> staleFiles = []


        outputDirectory.eachFileRecurse {
            File file ->

                if (!file.isFile()) {
                    return
                }


                String relativePath =
                        relativePath(
                                outputDirectory,
                                file
                        )


                if (
                        isKnowledgeOutputPath(relativePath) &&
                                !expectedRelativePaths.contains(relativePath)
                ) {

                    staleFiles.add(
                            file
                    )
                }
        }


        staleFiles.each {
            File file ->

                if (!file.delete() && file.exists()) {

                    throw new GradleException(
                            """
Unable to remove stale Portal knowledge file:

${file.absolutePath}
""".stripIndent()
                    )
                }
        }


        removeEmptyDirectories(
                outputDirectory,
                outputDirectory
        )
    }


    private static boolean isKnowledgeOutputPath(
            String relativePath
    ) {

        List<String> pathSegments =
                relativePath
                        .split('/')
                        .toList()


        return pathSegments.size() >= 3 &&
                pathSegments[1] == 'knowledge'
    }


    private static void removeEmptyDirectories(
            File rootDirectory,
            File directory
    ) {

        File[] children =
                directory.listFiles()


        if (children == null) {

            throw new GradleException(
                    """
Unable to read Portal knowledge output directory:

${directory.absolutePath}
""".stripIndent()
            )
        }


        children
                .findAll {
                    File child ->

                        child.isDirectory()
                }
                .each {
                    File child ->

                        removeEmptyDirectories(
                                rootDirectory,
                                child
                        )
                }


        if (directory == rootDirectory) {
            return
        }


        String[] remaining =
                directory.list()


        if (
                remaining != null &&
                        remaining.length == 0 &&
                        !directory.delete() &&
                        directory.exists()
        ) {

            throw new GradleException(
                    """
Unable to remove empty Portal knowledge directory:

${directory.absolutePath}
""".stripIndent()
            )
        }
    }


    private static class LanguageSource {

        final String language
        final File languageDirectory
        final File menuDirectory


        LanguageSource(
                String language,
                File languageDirectory,
                File menuDirectory
        ) {

            this.language = language
            this.languageDirectory = languageDirectory
            this.menuDirectory = menuDirectory
        }
    }


    private static class Counter {

        int categoryCount = 0
        int sectionCount = 0
    }
}
