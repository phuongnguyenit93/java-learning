package com.example.learning.task.sheets.plugin

import com.example.learning.task.sheets.task.ExportModuleSheetToExcelTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

class ModuleSheetExcelExportPluginTest {
    @Test
    void taskAndDedicatedExtensionAreRegisteredOnModule() {
        def project = ProjectBuilder.builder().withName('demo').build()
        project.pluginManager.apply(SheetsTaskPlugin)
        def extension = project.extensions.getByName('exportModuleSheetToExcel')
        extension.fileURL.set('https://docs.google.com/spreadsheets/d/testSpreadsheet/edit')
        extension.sheetURL.set('https://docs.google.com/spreadsheets/d/testSpreadsheet/edit#gid=42')
        extension.language.set('vi')
        def task = project.tasks.named('exportModuleSheetToExcel',
                ExportModuleSheetToExcelTask).get()
        assertEquals(extension.fileURL.get(), task.fileURL.get())
        assertEquals(extension.sheetURL.get(), task.sheetURL.get())
        assertEquals('vi', task.language.get())
        assertEquals('DRY_RUN', task.mode.get())
        assertEquals('sheets', task.group)
    }
}
