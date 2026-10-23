package com.example.learning.task.sheets.plugin

import com.example.learning.task.sheets.task.CloneModuleViToEnTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

class ModuleLanguageClonePluginTest {
    @Test
    void registersModuleLocalCloneWithDryRunDefault() {
        def project = ProjectBuilder.builder().withName('demo').build()
        project.pluginManager.apply(SheetsTaskPlugin)
        def task = project.tasks.named('cloneModuleViToEn', CloneModuleViToEnTask).get()
        assertEquals('DRY_RUN', task.mode.get())
        assertEquals('sheets', task.group)
    }
}
