package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.ModuleContentExcelSyncService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** Reverse the individual Sheet->module content importers into an EN Excel snapshot. */
abstract class SyncModuleContentToExcelTask extends DefaultTask {
    @Input abstract Property<String> getPart()
    @Input abstract Property<String> getMode()

    @TaskAction
    void sync() {
        String selected = mode.get().trim().toUpperCase(Locale.ROOT)
        if (!(selected in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException('sheetMode must be DRY_RUN or APPLY.')
        }
        Map result = new ModuleContentExcelSyncService().execute(
                project.rootProject.projectDir, project.projectDir,
                part.get(), selected == 'APPLY')
        logger.lifecycle('[MODULE-TO-XLSX] {}: part={}, source={}, output={}, ' +
                'chapters={}, items={}, changedCells={}', result.status, result.part,
                result.source, result.output, result.chapters, result.items,
                result.changedCells)
    }
}
