package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.SheetContentImportCoordinator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Module-local importer. Selected tab I3 must match this Gradle module.
 */
abstract class GenerateModuleContentFromSheetByLocationTask
        extends DefaultTask {

    @Input
    abstract Property<String> getModulePath()

    @Input
    @Optional
    abstract Property<String> getSheetId()

    @Input
    @Optional
    abstract Property<String> getFileUrl()

    @Input
    abstract Property<String> getMode()

    @TaskAction
    void generate() {

        if (!sheetId.orNull?.trim() || !fileUrl.orNull?.trim()) {
            throw new GradleException(
                    'Missing Google Sheets configuration. Configure both sheetId (tab gid) and fileUrl ' +
                            'in generateModuleContentFromSheetByLocation { ... } inside the module build.gradle.'
            )
        }

        String selectedMode = mode.get().trim().toUpperCase(Locale.ROOT)
        if (!(selectedMode in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException("Invalid sheetMode '${mode.get()}'. Use DRY_RUN or APPLY.")
        }
        Map<String, Object> result = new SheetContentImportCoordinator().importByLocation(
                project.rootProject.projectDir,
                project.projectDir,
                fileUrl.get(),
                sheetId.get(),
                selectedMode == 'APPLY'
        )
        if (result.status == 'SKIPPED_EMPTY') {
            logger.warn(
                    '[SHEETS-ROADMAP] SKIPPED: F3:H3 is empty on tab="{}" (gid={}); no files written.',
                    result.tab, result.gid
            )
            return
        }
        logger.lifecycle(
                '[SHEETS-ROADMAP] {}: tab="{}" (gid={}), location={}, milestones={}, file={}',
                result.status, result.tab, result.gid,
                result.location, result.milestones, result.file
        )
    }
}
