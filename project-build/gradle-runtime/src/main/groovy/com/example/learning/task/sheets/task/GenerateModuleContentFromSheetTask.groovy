package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.SheetsMetadataReaderService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Read-only connectivity check for a module-local Google Sheets import.
 *
 * Verifies the target tab and its Module Location (I3) before doing any work.
 * Knowledge/Video generation and file writes are intentionally not implemented yet.
 */
abstract class GenerateModuleContentFromSheetTask
        extends DefaultTask {

    @Input
    abstract Property<String> getModulePath()

    @Input
    @Optional
    abstract Property<String> getSheetId()

    @Input
    @Optional
    abstract Property<String> getFileUrl()

    @TaskAction
    void generate() {

        if (!sheetId.orNull?.trim() || !fileUrl.orNull?.trim()) {
            throw new GradleException(
                    'Missing Google Sheets configuration. Configure both sheetId (tab gid) and fileUrl ' +
                            'in generateModuleContentFromSheet { ... } inside the module build.gradle.'
            )
        }

        File rootDirectory = project.rootProject.projectDir.canonicalFile
        File moduleDirectory = project.projectDir.canonicalFile

        if (!moduleDirectory.toPath().startsWith(rootDirectory.toPath()) ||
                moduleDirectory == rootDirectory) {
            throw new GradleException(
                    "Task must run from a module directory inside the root project. " +
                            "Project: '${moduleDirectory}', root: '${rootDirectory}'."
            )
        }

        String expectedModuleLocation =
                rootDirectory.toPath()
                        .relativize(moduleDirectory.toPath())
                        .toString()
                        .replace(File.separatorChar, '/' as char)

        Map<String, String> metadata =
                new SheetsMetadataReaderService().verify(
                        fileUrl.get(),
                        sheetId.get(),
                        expectedModuleLocation
                )

        logger.lifecycle(
                '[SHEETS] Module Location verified: {} matches tab "{}" (gid={}), cell I3.',
                expectedModuleLocation,
                metadata.tabTitle,
                metadata.sheetId
        )

        logger.lifecycle(
                '[SHEETS] Read-only access OK for {}: spreadsheet "{}" ({}), tab "{}" (gid={}).',
                modulePath.get(),
                metadata.spreadsheetTitle,
                metadata.spreadsheetId,
                metadata.tabTitle,
                metadata.sheetId
        )

        logger.lifecycle(
                '[SHEETS] Knowledge/Video generation is not implemented yet; no files were written.'
        )
    }
}
