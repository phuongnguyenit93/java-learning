package com.example.learning.task.sheets.plugin

import com.example.learning.task.sheets.task.ModuleTranslationAuditTask
import com.example.learning.task.sheets.task.ModuleTranslationProgressTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

class ModuleTranslationAuditPluginTest {
    @Test
    void translationTasksAreModuleLocalAndDryRunByDefault() {
        def project = ProjectBuilder.builder().withName('demo').build()
        project.pluginManager.apply(SheetsTaskPlugin)
        [
                prepareModuleTranslation: 'PREPARE',
                validateModuleTranslation: 'VALIDATE'
        ].each { String name, String operation ->
            def task = project.tasks.named(name, ModuleTranslationAuditTask).get()
            assertEquals(operation, task.operation.get())
            assertEquals('DRY_RUN', task.mode.get())
            assertEquals('NORMAL', task.workerMode.get())
            assertEquals('', task.workerCount.get())
            assertEquals('sheets', task.group)
        }
        def tracking = project.tasks.named('trackModuleTranslation',
                ModuleTranslationProgressTask).get()
        assertEquals('SHOW', tracking.action.get())
        assertEquals('', tracking.workerId.get())
        assertEquals('', tracking.jobId.get())
        assertEquals('', tracking.revision.get())
        assertEquals('sheets', tracking.group)
    }
}
