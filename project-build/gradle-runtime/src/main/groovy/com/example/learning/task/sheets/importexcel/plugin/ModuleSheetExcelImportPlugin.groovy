package com.example.learning.task.sheets.importexcel.plugin

import com.example.learning.task.sheets.extension.ImportExcelFromModuleSheetExtension
import com.example.learning.task.sheets.task.ImportExcelFromModuleSheetTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Applied by SheetsTaskPlugin to eligible module projects. */
class ModuleSheetExcelImportPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        ImportExcelFromModuleSheetExtension extension = project.extensions.create(
                'importExcelFromModuleSheet', ImportExcelFromModuleSheetExtension)
        project.tasks.register('importExcelFromModuleSheet', ImportExcelFromModuleSheetTask) { task ->
            task.group = 'sheets'
            task.description = 'Create a NEW Google Sheets tab from this module offline Excel (DRY_RUN by default).'
            task.fileUrl.set(project.providers.gradleProperty('fileUrl')
                    .orElse(project.providers.gradleProperty('fileURL'))
                    .orElse(extension.fileUrl))
            task.language.set(project.providers.gradleProperty('language')
                    .orElse(extension.language))
            task.mode.convention(project.providers.gradleProperty('sheetMode').orElse('DRY_RUN'))
        }
    }
}
