package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.ModuleOnlineSheetCleanupService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** STEP_FINAL Prime-only post-QA removal of the online tab identified by gid. */
abstract class DeleteModuleOnlineSheetTask extends DefaultTask {
    @Input abstract Property<String> getFileUrl()
    @Input abstract Property<String> getSheetId()
    @Input abstract Property<String> getMode()

    @TaskAction
    void cleanup() {
        String selected = mode.get().trim().toUpperCase(Locale.ROOT)
        if (!(selected in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException('sheetMode must be DRY_RUN or APPLY.')
        }
        Map result = new ModuleOnlineSheetCleanupService().execute(
                project.rootProject.projectDir, project.projectDir,
                fileUrl.get(), sheetId.get(), selected == 'APPLY')
        logger.lifecycle('[MODULE-ONLINE-SHEET-CLEANUP] {} module={} tab={} gid={} ' +
                'spreadsheet={} offlineLanguages={}',
                result.status, result.module, result.tab, result.gid,
                result.spreadsheetId,
                result.workbooks.collect { it.language })
    }
}
