package com.example.learning.task.readme.internalMenu.task

import com.example.learning.task.readme.internalMenu.service.GenerateInternalReadmeMenuService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(
        because = 'Updates the configured Markdown file in place'
)
abstract class GenerateInternalReadmeMenuTask
        extends DefaultTask {

    @Input
    abstract Property<String> getLocation()


    @TaskAction
    void generate() {

        String configuredLocation =
                location.orNull
                        ?.trim()


        if (
                configuredLocation == null ||
                        configuredLocation.isBlank()
        ) {

            throw new GradleException(
                    """
Internal README menu location is not configured.

Example:

generateInternalReadmeMenu {

    location =
        'readme/vi/menu/1.Basic/BasicThread.md'
}
"""
            )
        }


        File configuredFile =
                new File(
                        configuredLocation
                )


        if (configuredFile.isAbsolute()) {

            throw new GradleException(
                    """
Internal README menu location must be relative to projectDir.

Configured:

${configuredLocation}

projectDir:

${project.projectDir.absolutePath}
"""
            )
        }


        File projectDirectory =
                project.projectDir
                        .canonicalFile


        File markdownFile =
                new File(
                        projectDirectory,
                        configuredLocation
                )
                        .canonicalFile


        /*
         * Không cho phép:
         *
         * ../../some-file.md
         *
         * đi ra ngoài projectDir.
         */
        if (
                !markdownFile
                        .toPath()
                        .startsWith(
                                projectDirectory.toPath()
                        )
        ) {

            throw new GradleException(
                    """
README location must be inside projectDir.

Configured:

${configuredLocation}

Resolved:

${markdownFile.absolutePath}
"""
            )
        }


        if (!markdownFile.exists()) {

            throw new GradleException(
                    """
README file does not exist:

${markdownFile.absolutePath}
"""
            )
        }


        if (!markdownFile.isFile()) {

            throw new GradleException(
                    """
README location is not a file:

${markdownFile.absolutePath}
"""
            )
        }


        if (
                !markdownFile.name
                        .toLowerCase()
                        .endsWith('.md')
        ) {

            throw new GradleException(
                    """
Internal README menu can only process Markdown files.

File:

${markdownFile.absolutePath}
"""
            )
        }


        GenerateInternalReadmeMenuService service =
                new GenerateInternalReadmeMenuService()


        service.generate(
                markdownFile
        )


        logger.lifecycle(
                'Internal README menu generated: {}',
                markdownFile.absolutePath
        )
    }
}