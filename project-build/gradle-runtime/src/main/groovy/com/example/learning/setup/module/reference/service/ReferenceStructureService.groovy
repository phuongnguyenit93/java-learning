package com.example.learning.setup.module.reference.service

import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger


class ReferenceStructureService {

    private static final String REFERENCE_FILE =
            'reference/references.yml'


    private static final String SKELETON =
            '''# ============================================================
# MODULE REFERENCE STRUCTURE
# ============================================================
#
# This is the shared module-level reference catalog.
# It is intentionally not split by MODULE_LANGUAGE.
#
# Example structure:
#
# references:
#   - title: "Java Language Specification"
#     url: "https://docs.oracle.com/javase/specs/"
#     description: "Official Java language specification."
#
# Add only curated references that are useful for the module.
# The build system must not overwrite this file after authored content exists.
'''


    private final Logger logger


    ReferenceStructureService(
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
                        'BUILD_REFERENCE'
                )
        ) {

            logger.info(
                    '[REFERENCE-STRUCTURE] Skip {} because BUILD_REFERENCE != TRUE.',
                    project.path
            )


            return
        }


        File referenceFile =
                new File(
                        project.projectDir,
                        REFERENCE_FILE
                )


        boolean existedBefore =
                referenceFile.exists()


        if (
                referenceFile.exists() &&
                        !referenceFile.isFile()
        ) {

            throw new GradleException(
                    "Reference path exists but is not a file: ${referenceFile.absolutePath}"
            )
        }


        if (referenceFile.isFile()) {

            String current =
                    referenceFile.getText(
                            'UTF-8'
                    )


            if (!current.trim().isEmpty()) {

                logger.info(
                        '[REFERENCE-STRUCTURE] Keep existing non-empty Reference file unchanged: {}',
                        referenceFile.absolutePath
                )


                return
            }
        }


        ensureDirectory(
                referenceFile.parentFile
        )


        referenceFile.setText(
                SKELETON,
                'UTF-8'
        )


        logger.lifecycle(
                existedBefore
                        ? '📝 [REFERENCE-STRUCTURE] Initialized empty Reference file: {}'
                        : '✨ [REFERENCE-STRUCTURE] Created Reference file: {}',
                referenceFile.absolutePath
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
                    "Reference directory path exists but is not a directory: ${directory.absolutePath}"
            )
        }


        if (
                !directory.mkdirs() &&
                        !directory.isDirectory()
        ) {

            throw new GradleException(
                    "Unable to create Reference directory: ${directory.absolutePath}"
            )
        }
    }
}
