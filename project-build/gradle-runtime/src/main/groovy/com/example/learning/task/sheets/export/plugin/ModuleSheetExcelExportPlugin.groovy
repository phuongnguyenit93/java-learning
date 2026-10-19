package com.example.learning.task.sheets.export.plugin

import com.example.learning.task.sheets.extension.ExportModuleSheetToExcelExtension
import com.example.learning.task.sheets.task.ExportModuleSheetToExcelTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Applied by SheetsTaskPlugin to modules eligible for task setup. */
class ModuleSheetExcelExportPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        ExportModuleSheetToExcelExtension config = project.extensions.create(
                'exportModuleSheetToExcel', ExportModuleSheetToExcelExtension)
        project.tasks.register('exportModuleSheetToExcel', ExportModuleSheetToExcelTask) { task ->
            task.group = 'sheets'
            task.description = 'Export only this module tab to content/sheet/{language}/module.xlsx (DRY_RUN by default).'
            task.fileURL.set(project.providers.gradleProperty('fileURL')
                    .orElse(project.providers.gradleProperty('fileUrl'))
                    .orElse(config.fileURL))
            task.sheetURL.set(project.providers.gradleProperty('sheetURL')
                    .orElse(project.providers.gradleProperty('sheetUrl'))
                    .orElse(config.sheetURL))
            task.language.set(project.providers.gradleProperty('language')
                    .orElse(config.language))
            task.mode.convention(project.providers.gradleProperty('sheetMode').orElse('DRY_RUN'))
        }
    }
}
