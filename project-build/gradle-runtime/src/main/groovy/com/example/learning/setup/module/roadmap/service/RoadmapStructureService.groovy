package com.example.learning.setup.module.roadmap.service

import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger

import java.nio.file.Files
import java.nio.file.StandardCopyOption


class RoadmapStructureService {

    private static final String COMMENT_START =
            '# <roadmap-schema>'


    private static final String COMMENT_END =
            '# </roadmap-schema>'


    private final Logger logger

    private final RoadmapSchemaService schemaService


    RoadmapStructureService(
            Logger logger
    ) {

        this.logger =
                logger


        this.schemaService =
                new RoadmapSchemaService()
    }


    void setup(
            Project project
    ) {

        if (
                !ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_ROADMAP'
                )
        ) {

            logger.info(
                    '[ROADMAP-STRUCTURE] Skip {} because BUILD_ROADMAP != TRUE.',
                    project.path
            )


            return
        }


        List<String> languages =
                ProjectPropertyUtils
                        .getStringList(
                                project,
                                'MODULE_LANGUAGE'
                        )
                        .collect {
                            String language ->

                            language.toLowerCase(
                                    Locale.ROOT
                            )
                        }
                        .unique()


        if (languages.isEmpty()) {

            logger.info(
                    '[ROADMAP-STRUCTURE] No MODULE_LANGUAGE configured for {}.',
                    project.path
            )


            return
        }


        languages.each {
            String language ->

            synchronizeRoadmapFile(
                    new File(
                            project.projectDir,
                            "roadmap/${language}/roadmap.yml"
                    ),
                    language
            )
        }
    }


    private void synchronizeRoadmapFile(
            File roadmapFile,
            String language
    ) {

        if (
                roadmapFile.exists() &&
                        !roadmapFile.isFile()
        ) {

            throw new GradleException(
                    "Roadmap path exists but is not a file: ${roadmapFile.absolutePath}"
            )
        }


        if (!roadmapFile.exists()) {

            writeSafely(
                    roadmapFile,
                    schemaService.renderSkeleton(
                            language
                    )
            )


            logger.lifecycle(
                    '✨ [ROADMAP-STRUCTURE] Created: {}',
                    roadmapFile.absolutePath
            )


            return
        }


        String current =
                roadmapFile.getText(
                        'UTF-8'
                )


        String updated =
                replaceGeneratedComment(
                        current,
                        schemaService.renderCommentBlock(
                                language
                        ),
                        roadmapFile
                )


        if (current == updated) {

            logger.info(
                    '[ROADMAP-STRUCTURE] UP-TO-DATE: {}',
                    roadmapFile.absolutePath
            )


            return
        }


        writeSafely(
                roadmapFile,
                updated
        )


        logger.lifecycle(
                '📝 [ROADMAP-STRUCTURE] Synchronized generated schema comment: {}',
                roadmapFile.absolutePath
        )
    }


    private static String replaceGeneratedComment(
            String content,
            String generatedComment,
            File roadmapFile
    ) {

        int start =
                content.indexOf(
                        COMMENT_START
                )


        int end =
                content.indexOf(
                        COMMENT_END
                )


        if ((start >= 0) != (end >= 0)) {

            throw new GradleException(
                    "Malformed generated Roadmap schema comment markers: ${roadmapFile.absolutePath}"
            )
        }


        if (
                start >= 0 &&
                        end < start
        ) {

            throw new GradleException(
                    "Malformed generated Roadmap schema comment marker order: ${roadmapFile.absolutePath}"
            )
        }


        if (start < 0) {

            return generatedComment +
                    '\n\n' +
                    content
        }


        int endExclusive =
                end +
                        COMMENT_END.length()


        return content.substring(
                0,
                start
        ) +
                generatedComment +
                content.substring(
                        endExclusive
                )
    }


    private static void writeSafely(
            File file,
            String content
    ) {

        File parent =
                file.parentFile


        if (
                !parent.isDirectory() &&
                        !parent.mkdirs() &&
                        !parent.isDirectory()
        ) {

            throw new GradleException(
                    "Unable to create Roadmap directory: ${parent.absolutePath}"
            )
        }


        File temporaryFile =
                new File(
                        parent,
                        "${file.name}.tmp"
                )


        temporaryFile.setText(
                content,
                'UTF-8'
        )


        try {

            Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            )
        }
        finally {

            if (temporaryFile.exists()) {
                temporaryFile.delete()
            }
        }
    }
}
