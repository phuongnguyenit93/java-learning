package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.ModuleSheetExcelImportService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/** Imports offline module.xlsx into a BRAND NEW tab, without requiring a gid. */
abstract class ImportExcelFromModuleSheetTask extends DefaultTask {
    @Input @Optional abstract Property<String> getFileUrl()
    @Input @Optional abstract Property<String> getLanguage()
    @Input abstract Property<String> getMode()

    @TaskAction
    void importWorkbook() {
        if (!fileUrl.orNull?.trim() || !language.orNull?.trim()) {
            throw new GradleException('importExcelFromModuleSheet requires fileUrl and language ' +
                    '(extension or -PfileUrl=<spreadsheet URL> -Planguage=vi).')
        }
        String selectedMode = mode.get().trim().toUpperCase(Locale.ROOT)
        if (!(selectedMode in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException('Invalid sheetMode: ' + selectedMode)
        }
        Map result = new ModuleSheetExcelImportService().execute(
                project.rootProject.projectDir, project.projectDir, fileUrl.get(),
                language.get(), selectedMode == 'APPLY')
        logger.lifecycle('[XLSX-SHEET] {}: module={}, source={}, newTab="{}", ' +
                'cells={}, batches={}, gid={}, url={}', result.status, result.location,
                result.file, result.tab, result.cells, result.batches,
                result.gid ?: 'N/A', result.tabUrl ?: 'N/A')
    }
}
