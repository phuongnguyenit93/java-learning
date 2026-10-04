package com.example.learning.setup.module.video.service

import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger


class VideoStructureService {

    private static final String VIDEO_DIRECTORY =
            'video'

    private static final String README_DIRECTORY =
            'readme'

    private static final String MENU_DIRECTORY =
            'menu'


    private final Logger logger


    VideoStructureService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    void setup(
            Project project
    ) {

        if (
                !ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_VIDEO'
                )
        ) {

            logger.info(
                    '[VIDEO-STRUCTURE] Skip {} because BUILD_VIDEO != TRUE.',
                    project.path
            )


            return
        }


        if (
                !ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_README'
                )
        ) {

            throw new GradleException(
                    """
BUILD_VIDEO requires BUILD_README=TRUE because README menu is the source of truth for Video structure.

Module:
${project.path}
""".stripIndent()
            )
        }


        List<String> languages =
                ProjectPropertyUtils
                        .getStringList(
                                project,
                                'MODULE_LANGUAGE'
                        )
                        .collect {
                            String language ->

                            language
                                    ?.trim()
                                    ?.toLowerCase(
                                            Locale.ROOT
                                    )
                        }
                        .findAll {
                            String language ->

                            language != null &&
                                    !language.isBlank()
                        }
                        .unique()


        if (languages.isEmpty()) {

            logger.info(
                    '[VIDEO-STRUCTURE] No MODULE_LANGUAGE configured for {}.',
                    project.path
            )


            return
        }


        languages.each {
            String language ->

            synchronizeLanguage(
                    project,
                    language
            )
        }
    }


    private void synchronizeLanguage(
            Project project,
            String language
    ) {

        File readmeMenuDirectory =
                new File(
                        project.projectDir,
                        "${README_DIRECTORY}/${language}/${MENU_DIRECTORY}"
                )


        if (!readmeMenuDirectory.isDirectory()) {

            throw new GradleException(
                    """
README menu directory does not exist for BUILD_VIDEO.

Module:
${project.path}

Language:
${language}

Expected:
${readmeMenuDirectory.absolutePath}
""".stripIndent()
            )
        }


        List<File> readmeFiles =
                collectMarkdownFiles(
                        readmeMenuDirectory
                )


        if (readmeFiles.isEmpty()) {

            throw new GradleException(
                    """
No README menu Markdown files were found for BUILD_VIDEO.

Module:
${project.path}

Language:
${language}

README menu:
${readmeMenuDirectory.absolutePath}
""".stripIndent()
            )
        }


        File videoMenuDirectory =
                new File(
                        project.projectDir,
                        "${VIDEO_DIRECTORY}/${language}/${MENU_DIRECTORY}"
                )


        ensureDirectory(
                videoMenuDirectory
        )


        Set<String> expectedRelativePaths =
                new LinkedHashSet<>()


        readmeFiles.each {
            File readmeFile ->

            String relativePath =
                    relativePath(
                            readmeMenuDirectory,
                            readmeFile
                    )


            expectedRelativePaths.add(
                    relativePath
            )


            File videoFile =
                    new File(
                            videoMenuDirectory,
                            relativePath
                    )


            if (videoFile.exists()) {

                if (!videoFile.isFile()) {

                    throw new GradleException(
                            """
Video skeleton path exists but is not a file:

${videoFile.absolutePath}
""".stripIndent()
                    )
                }


                logger.info(
                        '[VIDEO-STRUCTURE] Keep existing human-owned Video file: {}',
                        videoFile.absolutePath
                )


                return
            }


            VideoOutline outline =
                    parseOutline(
                            readmeFile
                    )


            ensureDirectory(
                    videoFile.parentFile
            )


            videoFile.setText(
                    renderSkeleton(
                            outline
                    ),
                    'UTF-8'
            )


            logger.lifecycle(
                    '✨ [VIDEO-STRUCTURE] Created: {}',
                    videoFile.absolutePath
            )
        }


        warnOrphanVideoFiles(
                project,
                language,
                videoMenuDirectory,
                expectedRelativePaths
        )
    }


    private void warnOrphanVideoFiles(
            Project project,
            String language,
            File videoMenuDirectory,
            Set<String> expectedRelativePaths
    ) {

        collectMarkdownFiles(
                videoMenuDirectory
        ).each {
            File videoFile ->

            String relativePath =
                    relativePath(
                            videoMenuDirectory,
                            videoFile
                    )


            if (
                    expectedRelativePaths.contains(
                            relativePath
                    )
            ) {

                return
            }


            logger.warn(
                    """
⚠️ [VIDEO-STRUCTURE] Orphan Video file detected. It is kept unchanged.

Module:
${project.path}

Language:
${language}

Video file:
${videoFile.absolutePath}

No matching README source:
${new File(project.projectDir, "${README_DIRECTORY}/${language}/${MENU_DIRECTORY}/${relativePath}").absolutePath}
""".stripIndent()
            )
        }
    }


    private static VideoOutline parseOutline(
            File readmeFile
    ) {

        String title =
                null

        List<String> sections =
                []

        boolean fenced =
                false


        readmeFile
                .getText(
                        'UTF-8'
                )
                .readLines()
                .each {
                    String line ->

                    String trimmed =
                            line.trim()


                    if (
                            trimmed.startsWith('```') ||
                                    trimmed.startsWith('~~~')
                    ) {

                        fenced =
                                !fenced


                        return
                    }


                    if (fenced) {
                        return
                    }


                    if (
                            title == null &&
                                    line ==~ /^#\s+.+$/
                    ) {

                        title =
                                cleanHeading(
                                        line.replaceFirst(
                                                /^#\s+/,
                                                ''
                                        )
                                )


                        return
                    }


                    if (line ==~ /^##\s+.+$/) {

                        String section =
                                cleanHeading(
                                        line.replaceFirst(
                                                /^##\s+/,
                                                ''
                                        )
                                )


                        if (
                                section != null &&
                                        !section.isBlank() &&
                                        !section.equalsIgnoreCase('Menu')
                        ) {

                            sections.add(
                                    section
                            )
                        }
                    }
                }


        if (
                title == null ||
                        title.isBlank()
        ) {

            throw new GradleException(
                    "README Knowledge file does not contain an H1 title: ${readmeFile.absolutePath}"
            )
        }


        if (sections.isEmpty()) {

            throw new GradleException(
                    "README Knowledge file does not contain any H2 section for Video skeleton: ${readmeFile.absolutePath}"
            )
        }


        return new VideoOutline(
                title,
                sections
        )
    }


    private static String cleanHeading(
            String rawHeading
    ) {

        String heading =
                rawHeading
                        ?.trim()


        if (
                heading == null ||
                        heading.isBlank()
        ) {

            return heading
        }


        def anchoredHeading =
                heading =~ /(?is)^<a\s+id\s*=\s*["'][^"']+["']\s*>(.*?)<\/a>\s*$/


        if (anchoredHeading.matches()) {

            return anchoredHeading
                    .group(1)
                    .trim()
        }


        return heading
    }


    private static String renderSkeleton(
            VideoOutline outline
    ) {

        StringBuilder content =
                new StringBuilder()


        content.append(
                '''---
video:
  url: ""
---

'''
        )


        content.append(
                "# ${outline.title}\n\n"
        )


        content.append(
                '''<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

'''
        )


        outline.sections.each {
            String section ->

            content.append(
                    "## ${section}\n\n"
            )


            content.append(
                    '<!-- VIDEO_SECTION -->\n\n'
            )
        }


        return content.toString()
    }


    private static List<File> collectMarkdownFiles(
            File directory
    ) {

        if (!directory.isDirectory()) {
            return []
        }


        File[] children =
                directory.listFiles()


        if (children == null) {

            throw new GradleException(
                    "Unable to read directory: ${directory.absolutePath}"
            )
        }


        List<File> result =
                []


        children
                .findAll {
                    File child ->

                    !child.name.startsWith('.')
                }
                .sort {
                    File left,
                    File right ->

                    left.name <=> right.name
                }
                .each {
                    File child ->

                    if (child.isDirectory()) {

                        result.addAll(
                                collectMarkdownFiles(
                                        child
                                )
                        )


                        return
                    }


                    if (
                            child.isFile() &&
                                    child.name.toLowerCase(Locale.ROOT).endsWith('.md')
                    ) {

                        result.add(
                                child
                        )
                    }
                }


        return result
    }


    private static String relativePath(
            File rootDirectory,
            File file
    ) {

        return rootDirectory
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
                .replace(
                        File.separator,
                        '/'
                )
    }


    private static void ensureDirectory(
            File directory
    ) {

        if (directory.isDirectory()) {
            return
        }


        if (directory.exists()) {

            throw new GradleException(
                    "Expected directory but found another filesystem entry: ${directory.absolutePath}"
            )
        }


        if (
                !directory.mkdirs() &&
                        !directory.isDirectory()
        ) {

            throw new GradleException(
                    "Unable to create directory: ${directory.absolutePath}"
            )
        }
    }


    private static class VideoOutline {

        final String title

        final List<String> sections


        VideoOutline(
                String title,
                List<String> sections
        ) {

            this.title =
                    title


            this.sections =
                    sections
        }
    }
}

