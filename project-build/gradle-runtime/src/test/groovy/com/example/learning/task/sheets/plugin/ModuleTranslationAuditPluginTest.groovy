package com.example.learning.task.sheets.plugin

import com.example.learning.task.sheets.task.ModuleTranslationAuditTask
import com.example.learning.task.sheets.task.ModuleTranslationProgressTask
import com.example.learning.task.sheets.task.ModuleTranslationFileDeliveryTask
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
            assertEquals('en', task.targetLanguage.get())
            assertEquals('sheets', task.group)
        }
        def tracking = project.tasks.named('trackModuleTranslation',
                ModuleTranslationProgressTask).get()
        assertEquals('SHOW', tracking.action.get())
        assertEquals('', tracking.workerId.get())
        assertEquals('', tracking.jobId.get())
        assertEquals('', tracking.revision.get())
        assertEquals('sheets', tracking.group)
        def publisher = project.tasks.named('publishModuleTranslationFile',
                ModuleTranslationFileDeliveryTask).get()
        assertEquals('DRY_RUN', publisher.mode.get())
        assertEquals('en', publisher.targetLanguage.get())
        assertEquals('', publisher.workerId.get())
        assertEquals('', publisher.filePath.get())
        assertEquals('sheets', publisher.group)
        assertNull(project.tasks.findByName('extractModuleTranslationText'))
        assertNull(project.tasks.findByName('mergeModuleTranslationText'))
        assertNull(project.extensions.findByName('moduleTranslationText'))
    }
}
