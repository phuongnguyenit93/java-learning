package com.example.learning.setup.root.cleanup.task

import com.example.learning.setup.root.cleanup.service.ProjectCleanupService
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault


@DisableCachingByDefault(
        because = 'This task deletes empty folders from the project workspace.'
)
abstract class CleanupEmptyFoldersTask
        extends DefaultTask {

    // ========================================================
    // Input
    // ========================================================

    @Input
    abstract Property<String> getFolderName()


    @Input
    abstract Property<String> getProjectName()


    // ========================================================
    // Workspace
    // ========================================================

    @Internal
    abstract DirectoryProperty getRootDirectory()


    // ========================================================
    // Action
    // ========================================================

    @TaskAction
    void cleanup() {

        ProjectCleanupService service =
                new ProjectCleanupService(
                        logger
                )


        int deletedCount =
                service.cleanupEmptyFolders(
                        rootDirectory
                                .get()
                                .asFile,

                        folderName.get(),

                        projectName.get()
                )


        if (deletedCount > 0) {

            logger.lifecycle(
                    '✅ [CLEANUP] Deleted {} empty folder(s).',
                    deletedCount
            )
        }
    }
}