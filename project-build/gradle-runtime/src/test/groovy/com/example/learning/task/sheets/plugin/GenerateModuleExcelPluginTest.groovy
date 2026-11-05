package com.example.learning.task.sheets.plugin

import com.example.learning.task.sheets.extension.GenerateModuleExcelExtension
import com.example.learning.task.sheets.task.GenerateModuleExcelTask
import com.example.learning.task.sheets.task.DeleteModuleOnlineSheetTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

class GenerateModuleExcelPluginTest {
    @Test
    void registersSingleOfflineStep12TaskWithLanguageListExtension() {
        def project = ProjectBuilder.builder().withName('demo').build()
        project.pluginManager.apply(SheetsTaskPlugin)
        GenerateModuleExcelExtension extension =
                project.extensions.getByType(GenerateModuleExcelExtension)
        def task = project.tasks.named('generateModuleExcel', GenerateModuleExcelTask).get()
        assertEquals('sheets', task.group)
        assertEquals('DRY_RUN', task.mode.get())
        assertEquals([], task.languageList.get())
        extension.languageList.set(['vi', 'en'])
        assertEquals(['vi', 'en'], task.languageList.get())
    }

    @Test
    void registersFinalOnlineTabCleanupDefaultingToDryRun() {
        def project = ProjectBuilder.builder().withName('demo').build()
        project.pluginManager.apply(SheetsTaskPlugin)
        def task = project.tasks.named('deleteModuleOnlineSheet',
                DeleteModuleOnlineSheetTask).get()
        assertEquals('sheets', task.group)
        assertEquals('DRY_RUN', task.mode.get())
        assertTrue(task.description.contains('STEP_FINAL'))
        assertEquals('', task.fileUrl.get())
        assertEquals('', task.sheetId.get())
    }
}
