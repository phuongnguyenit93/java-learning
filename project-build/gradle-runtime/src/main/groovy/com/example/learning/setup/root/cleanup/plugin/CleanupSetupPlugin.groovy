package com.example.learning.setup.root.cleanup.plugin

import com.example.learning.setup.root.cleanup.task.CleanupEmptyFoldersTask
import com.example.learning.setup.root.cleanup.task.CleanupFilesTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project


class CleanupSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        // ====================================================
        // Root only
        // ====================================================

        if (project != project.rootProject) {

            throw new GradleException(
                    """
CleanupSetupPlugin can only be applied to the root project.

Current project:
${project.path}
""".stripIndent()
            )
        }


        // ====================================================
        // cleanupFiles
        // ====================================================

        project.tasks.register(
                'cleanupFiles',
                CleanupFilesTask
        ) { task ->

            task.group =
                    'cleanup'


            task.description =
                    'Delete files by exact name. Use -PFileName=<name> and optional -PProjectName=<SERVICE_NAME>.'


            task.rootDirectory.set(
                    project.layout.projectDirectory
            )


            task.fileName.convention(
                    project.providers
                            .gradleProperty(
                                    'FileName'
                            )
                            .orElse(
                                    ''
                            )
            )


            task.projectName.convention(
                    project.providers
                            .gradleProperty(
                                    'ProjectName'
                            )
                            .orElse(
                                    ''
                            )
            )
        }


        // ====================================================
        // cleanupEmptyFolders
        // ====================================================

        project.tasks.register(
                'cleanupEmptyFolders',
                CleanupEmptyFoldersTask
        ) { task ->

            task.group =
                    'cleanup'


            task.description =
                    'Delete empty folders by exact name. Use -PFolderName=<name> and optional -PProjectName=<SERVICE_NAME>.'


            task.rootDirectory.set(
                    project.layout.projectDirectory
            )


            task.folderName.convention(
                    project.providers
                            .gradleProperty(
                                    'FolderName'
                            )
                            .orElse(
                                    ''
                            )
            )


            task.projectName.convention(
                    project.providers
                            .gradleProperty(
                                    'ProjectName'
                            )
                            .orElse(
                                    ''
                            )
            )
        }
    }
}