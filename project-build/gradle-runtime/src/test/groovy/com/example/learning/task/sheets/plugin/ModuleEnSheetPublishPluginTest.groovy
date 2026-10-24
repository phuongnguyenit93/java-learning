package com.example.learning.task.sheets.plugin

import com.example.learning.task.sheets.task.ModuleEnSheetPublishTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

class ModuleEnSheetPublishPluginTest {
    @Test void modulePublishingTasksExistAndDefaultToDryRun() {
        def project = ProjectBuilder.builder().withName('demo').build()
        project.pluginManager.apply(SheetsTaskPlugin)
        [
                cloneModuleTemplateToEnTab:'CLONE',
                syncModuleEnToSheet:'SYNC',
                validateModuleEnSheet:'VERIFY',
                exportPublishedEnSheet:'EXPORT',
                publishModuleEnSheet:'PUBLISH'
        ].each { String name, String phase ->
            def task = project.tasks.named(name, ModuleEnSheetPublishTask).get()
            assertEquals(phase, task.phase.get())
            assertEquals('DRY_RUN',task.mode.get())
        }
    }
}
