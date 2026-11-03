package com.example.learning.setup.module.reference.service

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger


class ReferenceStructureService {

    private static final String REFERENCE_DIR = 'content/reference'
    private static final List<String> LANGUAGES = ['vi', 'en'].asImmutable()


    private static String skeletonFor(String language) {
        return """# ============================================================
# MODULE REFERENCE STRUCTURE
# ============================================================
#
# Language: ${language}
# Author references for vi and en separately.
# Empty references list means no authored content for this language.
#
# Example structure:
#
# references:
#   - title: "Java Language Specification"
#     url: "https://docs.oracle.com/javase/specs/"
#     description: "Official Java language specification."
#
# Translate titles/descriptions with review, preserving URLs.
# The build system must not overwrite authored content.
references: []
"""
    }


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
        File root = new File(project.projectDir, REFERENCE_DIR)
        File legacy = new File(root, 'references.yml')
        if (legacy.exists()) {
            throw new GradleException(
                    "Legacy Reference exists at ${legacy.absolutePath}; " +
                            'migrate authored bytes to content/reference/vi/references.yml before Gradle sync.'
            )
        }
        Map<String, File> files = LANGUAGES.collectEntries { String language ->
            [(language): new File(root, "${language}/references.yml")]
        }


        // Preflight both targets before creating either one.
        files.values().each { File file ->
            if (file.exists() && !file.isFile()) {
                throw new GradleException(
                        "Reference path exists but is not a file: ${file.absolutePath}"
                )
            }
            if (file.parentFile.exists() && !file.parentFile.isDirectory()) {
                throw new GradleException(
                        "Reference directory path exists but is not a directory: ${file.parentFile.absolutePath}"
                )
            }
        }

        files.each { String language, File file ->
            if (file.isFile() && !file.getText('UTF-8').trim().isEmpty()) {
                logger.info(
                        '[REFERENCE-STRUCTURE] Keep existing non-empty {} Reference unchanged: {}',
                        language, file.absolutePath
                )
                return
            }
            boolean existedBefore = file.exists()
            ensureDirectory(file.parentFile)
            file.setText(skeletonFor(language), 'UTF-8')
            logger.lifecycle(
                    existedBefore
                            ? '[REFERENCE-STRUCTURE] Initialized empty {} Reference: {}'
                            : '[REFERENCE-STRUCTURE] Created {} Reference: {}',
                    language, file.absolutePath
            )
        }
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
