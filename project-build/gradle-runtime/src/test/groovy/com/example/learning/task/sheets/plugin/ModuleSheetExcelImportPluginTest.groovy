package com.example.learning.task.sheets.plugin

import com.example.learning.task.sheets.task.ImportExcelFromModuleSheetTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

class ModuleSheetExcelImportPluginTest {
    @Test
    void registersImportTaskAndDedicatedTwoFieldExtension() {
        def project = ProjectBuilder.builder().withName('demo').build()
        project.pluginManager.apply(SheetsTaskPlugin)
        def extension = project.extensions.getByName('importExcelFromModuleSheet')
        extension.fileUrl.set('https://docs.google.com/spreadsheets/d/demo/edit')
        extension.language.set('vi')
        def task = project.tasks.named('importExcelFromModuleSheet',
                ImportExcelFromModuleSheetTask).get()
        assertEquals(extension.fileUrl.get(), task.fileUrl.get())
        assertEquals(extension.language.get(), task.language.get())
        assertEquals('DRY_RUN', task.mode.get())
        assertEquals('sheets', task.group)
    }
}
