package com.example.learning.setup.root.structure.service

import com.example.learning.utils.GradleBuildUtils
import com.example.learning.utils.ProjectPropertyUtils
import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.yaml.snakeyaml.Yaml

import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern


class PortalVideoProjectionService {

    private static final String OUTPUT_DIRECTORY =
            'project-portal/build/generated/portal-data/module'

    private static final String VIDEO_DIRECTORY =
            'video'

    private static final String MENU_DIRECTORY =
            'menu'

    private static final Pattern HTML_COMMENT_PATTERN =
            Pattern.compile(
                    '(?s)<!--.*?-->'
            )

    private static final Pattern HEADING_1_PATTERN =
            Pattern.compile(
                    '^#\\s+(.+?)\\s*$'
            )

    private static final Pattern HEADING_2_PATTERN =
            Pattern.compile(
                    '^##\\s+(.+?)\\s*$'
            )

    private static final Pattern HEADING_3_PATTERN =
            Pattern.compile(
                    '^###\\s+(.+?)\\s*$'
            )

    private static final Pattern FIELD_PATTERN =
            Pattern.compile(
                    '^\\*\\*(Time|Visual|Script|Purpose):\\*\\*\\s*(.*)$'
            )

    private static final Pattern ANCHORED_HEADING_PATTERN =
            Pattern.compile(
                    '(?is)^<a\\s+id\\s*=\\s*["\'][^"\']+["\']\\s*>(.*?)</a>\\s*$'
            )

    private static final Pattern SCENE_HEADING_PATTERN =
            Pattern.compile(
                    '(?i)^Scene(?:\\s+\\d+)?(?:\\s+[—-]\\s*(.+))?$'
            )

    private static final Pattern TRANSITION_HEADING_PATTERN =
            Pattern.compile(
                    '(?i)^Transition(?:\\s+[—-]\\s*(.+))?$'
            )


    private final Logger logger
    private final Yaml yamlReader


    PortalVideoProjectionService(
            Logger logger
    ) {

        this.logger =
                logger


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
                    "Project module directory was not found: ${moduleRoot.absolutePath}"
            )
        }


        File outputDirectory =
                new File(
                        rootProject.projectDir,
                        OUTPUT_DIRECTORY
                )


        Map<String, String> outputs =
                new LinkedHashMap<>()


        Set<String> routeIds =
                new LinkedHashSet<>()


        resolveRealModuleProjects(
                rootProject,
                moduleRoot
        ).each {
            Project moduleProject ->

            if (
                    !ProjectPropertyUtils.isEnabled(
                            moduleProject,
                            'BUILD_VIDEO'
                    )
            ) {
                return
            }


            List<LanguageSource> languageSources =
                    resolveLanguageSources(
                            moduleProject
                    )


            if (languageSources.isEmpty()) {
                return
            }


            String routeId =
                    resolveRouteId(
                            moduleProject
                    )


            if (!routeIds.add(routeId)) {

                throw new GradleException(
                        "Duplicate Portal Video route identifier detected: ${routeId}"
                )
            }


            languageSources.each {
                LanguageSource languageSource ->

                generateLanguageProjection(
                        routeId,
                        languageSource,
                        outputs
                )
            }
        }


        writeOutputs(
                outputDirectory,
                outputs
        )


        removeStaleFiles(
                outputDirectory,
                outputs.keySet()
        )


        logger.lifecycle(
                '🎬 [PORTAL-VIDEO] Generated {} Video projection files.',
                outputs.size()
        )
    }


    private void generateLanguageProjection(
            String routeId,
            LanguageSource languageSource,
            Map<String, String> outputs
    ) {

        List<Map> indexItems =
                []


        Set<String> categoryIds =
                new LinkedHashSet<>()


        languageSource.videoFiles.each {
            File videoFile ->

            VideoDocument document =
                    parseDocument(
                            routeId,
                            languageSource,
                            videoFile
                    )


            if (!document.hasAuthoredContent()) {
                return
            }


            validateDocument(
                    routeId,
                    languageSource.language,
                    document
            )


            String categoryId =
                    videoFile.name.substring(
                            0,
                            videoFile.name.length() - '.md'.length()
                    )


            if (
                    !categoryIds.add(
                            categoryId.toLowerCase(Locale.ROOT)
                    )
            ) {

                throw new GradleException(
                        """
Duplicate Portal Video category id detected.

Module:
${routeId}

Language:
${languageSource.language}

Category id:
${categoryId}
""".stripIndent()
                )
            }


            String encodedCategoryId =
                    encodePathSegment(
                            categoryId
                    )


            String contentPublicPath =
                    "/module/${encodePathSegment(routeId)}/video/${encodePathSegment(languageSource.language)}/content/${encodedCategoryId}/script.json"


            String contentOutputPath =
                    "${routeId}/video/${languageSource.language}/content/${categoryId}/script.json"


            Map scriptProjection =
                    [
                            version   : 1,
                            moduleId  : routeId,
                            language  : languageSource.language,
                            categoryId: categoryId,
                            title     : document.title,
                            sourcePath: document.sourcePath,
                            video     : [
                                    url: document.videoUrl
                            ],
                            sections  : document.sections.collect {
                                VideoSection section ->

                                [
                                        order: section.order,
                                        title: section.title,
                                        items: section.items.collect {
                                            VideoItem item ->

                                            [
                                                    type   : item.type,
                                                    title  : item.title,
                                                    time   : item.time,
                                                    visual : item.visual,
                                                    script : item.script,
                                                    purpose: item.purpose
                                            ].findAll {
                                                String key,
                                                Object value ->

                                                value != null
                                            }
                                        }
                                ]
                            }
                    ]


            putJsonOutput(
                    outputs,
                    contentOutputPath,
                    scriptProjection
            )


            indexItems.add(
                    [
                            categoryId     : categoryId,
                            title          : document.title,
                            sourcePath     : document.sourcePath,
                            content        : contentPublicPath,
                            url            : document.videoUrl,
                            sectionCount   : document.sections.size(),
                            sceneCount     : document.sceneCount(),
                            transitionCount: document.transitionCount()
                    ]
            )
        }


        if (indexItems.isEmpty()) {
            return
        }


        Map index =
                [
                        version   : 1,
                        moduleId  : routeId,
                        language  : languageSource.language,
                        videoCount: indexItems.size(),
                        items     : indexItems
                ]


        putJsonOutput(
                outputs,
                "${routeId}/video/${languageSource.language}/index.json",
                index
        )
    }


    private List<LanguageSource> resolveLanguageSources(
            Project moduleProject
    ) {

        File videoDirectory =
                new File(
                        moduleProject.projectDir,
                        VIDEO_DIRECTORY
                )


        if (!videoDirectory.isDirectory()) {
            return []
        }


        List<LanguageSource> result =
                []


        ProjectPropertyUtils
                .getStringList(
                        moduleProject,
                        'MODULE_LANGUAGE'
                )
                .collect {
                    String language ->

                    language
                            ?.trim()
                            ?.toLowerCase(Locale.ROOT)
                }
                .findAll {
                    String language ->

                    language != null &&
                            !language.isBlank()
                }
                .unique()
                .each {
                    String language ->

                    File menuDirectory =
                            new File(
                                    videoDirectory,
                                    "${language}/${MENU_DIRECTORY}"
                            )


                    if (!menuDirectory.isDirectory()) {
                        return
                    }


                    List<File> videoFiles =
                            collectMarkdownFiles(
                                    menuDirectory
                            )


                    if (videoFiles.isEmpty()) {
                        return
                    }


                    result.add(
                            new LanguageSource(
                                    language,
                                    menuDirectory,
                                    videoFiles
                            )
                    )
                }


        return result
    }


    private VideoDocument parseDocument(
            String routeId,
            LanguageSource languageSource,
            File videoFile
    ) {

        List<String> sourceLines =
                videoFile
                        .getText('UTF-8')
                        .replace('\r\n', '\n')
                        .replace('\r', '\n')
                        .split('\n', -1)
                        .toList()


        int bodyStart =
                0

        String videoUrl =
                ''


        if (
                !sourceLines.isEmpty() &&
                        sourceLines.first().trim() == '---'
        ) {

            int frontmatterEnd =
                    -1


            for (
                    int index = 1;
                    index < sourceLines.size();
                    index++
            ) {

                if (sourceLines[index].trim() == '---') {
                    frontmatterEnd = index
                    break
                }
            }


            if (frontmatterEnd < 0) {

                throw new GradleException(
                        "Unclosed Video frontmatter: ${videoFile.absolutePath}"
                )
            }


            Object frontmatter =
                    yamlReader.load(
                            sourceLines
                                    .subList(
                                            1,
                                            frontmatterEnd
                                    )
                                    .join('\n')
                    )


            if (frontmatter instanceof Map) {

                Object rawVideo =
                        (frontmatter as Map)['video']


                if (rawVideo instanceof Map) {

                    videoUrl =
                            ((rawVideo as Map)['url'] ?: '')
                                    .toString()
                                    .trim()
                }
            }


            bodyStart =
                    frontmatterEnd + 1
        }


        String body =
                sourceLines
                        .subList(
                                Math.min(bodyStart, sourceLines.size()),
                                sourceLines.size()
                        )
                        .join('\n')


        body =
                HTML_COMMENT_PATTERN
                        .matcher(body)
                        .replaceAll('')


        ParseState state =
                new ParseState()


        boolean fenced =
                false


        body
                .split('\n', -1)
                .each {
                    String line ->

                    String trimmed =
                            line.trim()


                    if (
                            trimmed.startsWith('```') ||
                                    trimmed.startsWith('~~~')
                    ) {

                        appendFieldLine(
                                state,
                                line
                        )


                        fenced =
                                !fenced


                        return
                    }


                    if (fenced) {

                        appendFieldLine(
                                state,
                                line
                        )


                        return
                    }


                    Matcher h1 =
                            HEADING_1_PATTERN.matcher(line)


                    if (h1.matches()) {

                        if (state.title == null) {

                            state.title =
                                    cleanHeading(
                                            h1.group(1)
                                    )
                        }


                        return
                    }


                    Matcher h2 =
                            HEADING_2_PATTERN.matcher(line)


                    if (h2.matches()) {

                        finishItem(state)


                        VideoSection section =
                                new VideoSection(
                                        state.sections.size() + 1,
                                        cleanHeading(
                                                h2.group(1)
                                        )
                                )


                        state.sections.add(section)
                        state.currentSection = section
                        state.currentField = null


                        return
                    }


                    Matcher h3 =
                            HEADING_3_PATTERN.matcher(line)


                    if (h3.matches()) {

                        finishItem(state)


                        if (state.currentSection == null) {
                            return
                        }


                        String heading =
                                h3.group(1).trim()


                        Matcher sceneMatcher =
                                SCENE_HEADING_PATTERN.matcher(heading)


                        Matcher transitionMatcher =
                                TRANSITION_HEADING_PATTERN.matcher(heading)


                        if (sceneMatcher.matches()) {

                            state.currentItem =
                                    new VideoItem(
                                            'SCENE',
                                            asNullableTrimmed(
                                                    sceneMatcher.group(1)
                                            )
                                    )
                        }
                        else if (transitionMatcher.matches()) {

                            state.currentItem =
                                    new VideoItem(
                                            'TRANSITION',
                                            asNullableTrimmed(
                                                    transitionMatcher.group(1)
                                            )
                                    )
                        }


                        return
                    }


                    Matcher fieldMatcher =
                            FIELD_PATTERN.matcher(line)


                    if (
                            fieldMatcher.matches() &&
                                    state.currentItem != null
                    ) {

                        state.currentField =
                                fieldMatcher.group(1)


                        String inlineValue =
                                fieldMatcher.group(2)


                        if (
                                inlineValue != null &&
                                        !inlineValue.isBlank()
                        ) {

                            state.currentItem
                                    .fieldBuffer(
                                            state.currentField
                                    )
                                    .append(
                                            inlineValue
                                    )
                        }


                        return
                    }


                    appendFieldLine(
                            state,
                            line
                    )
                }


        finishItem(state)


        if (
                state.title == null ||
                        state.title.isBlank()
        ) {

            throw new GradleException(
                    "Portal Video source is missing H1 title: ${videoFile.absolutePath}"
            )
        }


        return new VideoDocument(
                state.title,
                videoUrl,
                relativePath(
                        languageSource.menuDirectory,
                        videoFile
                ),
                state.sections
        )
    }


    private static void validateDocument(
            String routeId,
            String language,
            VideoDocument document
    ) {

        if (document.sections.isEmpty()) {

            throw invalidDocument(
                    routeId,
                    language,
                    document,
                    'No H2 Video sections were found.'
            )
        }


        document.sections.eachWithIndex {
            VideoSection section,
            int index ->

            int sceneCount =
                    section.items.count {
                        VideoItem item ->

                        item.type == 'SCENE'
                    }


            int transitionCount =
                    section.items.count {
                        VideoItem item ->

                        item.type == 'TRANSITION'
                    }


            if (sceneCount < 1) {

                throw invalidDocument(
                        routeId,
                        language,
                        document,
                        "Section ${index + 1} '${section.title}' requires at least 1 Scene."
                )
            }


            if (
                    index > 0 &&
                            transitionCount < 1
            ) {

                throw invalidDocument(
                        routeId,
                        language,
                        document,
                        "Section ${index + 1} '${section.title}' requires at least 1 Transition."
                )
            }


            section.items.eachWithIndex {
                VideoItem item,
                int itemIndex ->

                [
                        Time   : item.time,
                        Visual : item.visual,
                        Script : item.script,
                        Purpose: item.purpose
                ].each {
                    String field,
                    String value ->

                    if (
                            value == null ||
                                    value.isBlank()
                    ) {

                        throw invalidDocument(
                                routeId,
                                language,
                                document,
                                "Section ${index + 1} item ${itemIndex + 1} (${item.type}) is missing ${field}."
                        )
                    }
                }
            }
        }
    }


    private static GradleException invalidDocument(
            String routeId,
            String language,
            VideoDocument document,
            String message
    ) {

        return new GradleException(
                """
Invalid Portal Video source.

Module:
${routeId}

Language:
${language}

Source:
${document.sourcePath}

Reason:
${message}
""".stripIndent()
        )
    }


    private static void appendFieldLine(
            ParseState state,
            String line
    ) {

        if (
                state.currentItem == null ||
                        state.currentField == null
        ) {
            return
        }


        StringBuilder buffer =
                state.currentItem.fieldBuffer(
                        state.currentField
                )


        if (buffer.length() > 0) {
            buffer.append('\n')
        }


        buffer.append(line)
    }


    private static void finishItem(
            ParseState state
    ) {

        if (
                state.currentItem == null ||
                        state.currentSection == null
        ) {

            state.currentItem = null
            state.currentField = null
            return
        }


        state.currentItem.finish()


        state.currentSection.items.add(
                state.currentItem
        )


        state.currentItem = null
        state.currentField = null
    }


    private static String cleanHeading(
            String heading
    ) {

        String value =
                heading?.trim()


        if (
                value == null ||
                        value.isBlank()
        ) {
            return value
        }


        Matcher matcher =
                ANCHORED_HEADING_PATTERN.matcher(value)


        return matcher.matches()
                ? matcher.group(1).trim()
                : value
    }


    private static String asNullableTrimmed(
            String value
    ) {

        String trimmed =
                value?.trim()


        return trimmed == null ||
                trimmed.isBlank()
                ? null
                : trimmed
    }


    private static List<File> collectMarkdownFiles(
            File directory
    ) {

        List<File> result =
                []


        directory.eachFileRecurse {
            File file ->

            if (
                    file.isFile() &&
                            !file.name.startsWith('.') &&
                            file.name.toLowerCase(Locale.ROOT).endsWith('.md')
            ) {
                result.add(file)
            }
        }


        return result.sort {
            File left,
            File right ->

            compareNaturally(
                    relativePath(directory, left),
                    relativePath(directory, right)
            )
        }
    }


    private static int compareNaturally(
            String left,
            String right
    ) {

        List<String> leftParts =
                left.split('(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)').toList()


        List<String> rightParts =
                right.split('(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)').toList()


        int size =
                Math.min(
                        leftParts.size(),
                        rightParts.size()
                )


        for (
                int index = 0;
                index < size;
                index++
        ) {

            String leftPart =
                    leftParts[index]


            String rightPart =
                    rightParts[index]


            boolean leftNumber =
                    leftPart ==~ /\\d+/


            boolean rightNumber =
                    rightPart ==~ /\\d+/


            int comparison


            if (
                    leftNumber &&
                            rightNumber
            ) {

                comparison =
                        new BigInteger(leftPart) <=>
                                new BigInteger(rightPart)
            }
            else {

                comparison =
                        leftPart.compareToIgnoreCase(
                                rightPart
                        )
            }


            if (comparison != 0) {
                return comparison
            }
        }


        return leftParts.size() <=>
                rightParts.size()
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


                    projectPath.startsWith(moduleRootPath) &&
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
                        .findProperty('SERVICE_NAME')
                        ?.toString()
                        ?.trim()


        return serviceName != null &&
                !serviceName.isBlank()
                ? serviceName
                : moduleProject.projectDir.name
    }


    private static String relativePath(
            File root,
            File file
    ) {

        return GradleBuildUtils.normalizePath(
                root
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
    }


    private static String encodePathSegment(
            String value
    ) {

        return java.net.URLEncoder
                .encode(
                        value,
                        'UTF-8'
                )
                .replace('+', '%20')
    }


    private static void putJsonOutput(
            Map<String, String> outputs,
            String relativePath,
            Map payload
    ) {

        if (outputs.containsKey(relativePath)) {

            throw new GradleException(
                    "Duplicate Portal Video output detected: ${relativePath}"
            )
        }


        outputs[relativePath] =
                JsonOutput.prettyPrint(
                        JsonOutput.toJson(payload)
                ) + '\n'
    }


    private static void writeOutputs(
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


            File parent =
                    outputFile.parentFile


            if (
                    !parent.isDirectory() &&
                            !parent.mkdirs() &&
                            !parent.isDirectory()
            ) {

                throw new GradleException(
                        "Unable to create Portal Video output directory: ${parent.absolutePath}"
                )
            }


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


    private static void removeStaleFiles(
            File outputDirectory,
            Set<String> expectedRelativePaths
    ) {

        if (!outputDirectory.exists()) {
            return
        }


        if (!outputDirectory.isDirectory()) {

            throw new GradleException(
                    "Portal Video output path is not a directory: ${outputDirectory.absolutePath}"
            )
        }


        List<File> staleFiles =
                []


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


            List<String> segments =
                    relativePath
                            .split('/')
                            .toList()


            if (
                    segments.size() >= 3 &&
                            segments[1] == 'video' &&
                            !expectedRelativePaths.contains(relativePath)
            ) {
                staleFiles.add(file)
            }
        }


        staleFiles.each {
            File file ->

            if (
                    !file.delete() &&
                            file.exists()
            ) {

                throw new GradleException(
                        "Unable to remove stale Portal Video file: ${file.absolutePath}"
                )
            }
        }
    }


    private static class LanguageSource {

        final String language
        final File menuDirectory
        final List<File> videoFiles


        LanguageSource(
                String language,
                File menuDirectory,
                List<File> videoFiles
        ) {

            this.language = language
            this.menuDirectory = menuDirectory
            this.videoFiles = videoFiles
        }
    }


    private static class ParseState {

        String title
        final List<VideoSection> sections = []
        VideoSection currentSection
        VideoItem currentItem
        String currentField
    }


    private static class VideoDocument {

        final String title
        final String videoUrl
        final String sourcePath
        final List<VideoSection> sections


        VideoDocument(
                String title,
                String videoUrl,
                String sourcePath,
                List<VideoSection> sections
        ) {

            this.title = title
            this.videoUrl = videoUrl
            this.sourcePath = sourcePath
            this.sections = sections
        }


        boolean hasAuthoredContent() {

            return sceneCount() > 0 ||
                    transitionCount() > 0 ||
                    (
                            videoUrl != null &&
                                    !videoUrl.isBlank()
                    )
        }


        int sceneCount() {

            return sections.sum {
                VideoSection section ->

                section.items.count {
                    VideoItem item ->

                    item.type == 'SCENE'
                }
            } as int
        }


        int transitionCount() {

            return sections.sum {
                VideoSection section ->

                section.items.count {
                    VideoItem item ->

                    item.type == 'TRANSITION'
                }
            } as int
        }
    }


    private static class VideoSection {

        final int order
        final String title
        final List<VideoItem> items = []


        VideoSection(
                int order,
                String title
        ) {

            this.order = order
            this.title = title
        }
    }


    private static class VideoItem {

        final String type
        final String title
        final Map<String, StringBuilder> fields =
                [
                        Time   : new StringBuilder(),
                        Visual : new StringBuilder(),
                        Script : new StringBuilder(),
                        Purpose: new StringBuilder()
                ]

        String time
        String visual
        String script
        String purpose


        VideoItem(
                String type,
                String title
        ) {

            this.type = type
            this.title = title
        }


        StringBuilder fieldBuffer(
                String field
        ) {

            return fields[field]
        }


        void finish() {

            time =
                    cleanupTime(
                            fields.Time.toString()
                    )


            visual =
                    fields.Visual
                            .toString()
                            .trim()


            script =
                    fields.Script
                            .toString()
                            .trim()


            purpose =
                    fields.Purpose
                            .toString()
                            .trim()
        }


        private static String cleanupTime(
                String value
        ) {

            String result =
                    value.trim()


            if (
                    result.length() >= 2 &&
                            result.startsWith('`') &&
                            result.endsWith('`')
            ) {

                result =
                        result.substring(
                                1,
                                result.length() - 1
                        )
            }


            return result
        }
    }
}
