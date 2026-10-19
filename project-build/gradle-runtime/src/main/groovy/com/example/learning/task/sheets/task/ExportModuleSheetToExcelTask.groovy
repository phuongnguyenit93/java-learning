package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.ModuleSheetExcelExportService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/** Export exactly one verified module tab into content/sheet/{language}/module.xlsx. */
abstract class ExportModuleSheetToExcelTask extends DefaultTask {
    @Input @Optional abstract Property<String> getFileURL()
    @Input @Optional abstract Property<String> getSheetURL()
    @Input @Optional abstract Property<String> getLanguage()
    @Input abstract Property<String> getMode()

    @TaskAction
    void export() {
        if (!fileURL.orNull?.trim() || !sheetURL.orNull?.trim() ||
                !language.orNull?.trim()) {
            throw new GradleException('exportModuleSheetToExcel requires fileURL, sheetURL ' +
                    'and language. Configure exportModuleSheetToExcel { ... } or pass ' +
                    '-PfileURL=<spreadsheet URL> -PsheetURL=<tab URL with gid> -Planguage=vi.')
        }
        String selectedMode = mode.get().trim().toUpperCase(Locale.ROOT)
        if (!(selectedMode in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException("Invalid sheetMode '${mode.get()}'. Use DRY_RUN or APPLY.")
        }
        Map result = new ModuleSheetExcelExportService().execute(
                project.rootProject.projectDir,
                project.projectDir,
                fileURL.get(), sheetURL.get(), language.get(),
                selectedMode == 'APPLY'
        )
        logger.lifecycle('[SHEET-XLSX] {}: module={}, tab="{}" (gid={}), language={}, ' +
                'cells={}, unsupported-dropdowns={}, output={}', result.status, result.location,
                result.tab, result.gid, result.language, result.cells,
                result.unsupportedValidations, result.file)
    }
}
