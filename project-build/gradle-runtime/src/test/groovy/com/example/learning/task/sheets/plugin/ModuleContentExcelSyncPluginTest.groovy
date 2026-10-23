package com.example.learning.task.sheets.plugin

import com.example.learning.task.sheets.task.SyncModuleContentToExcelTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

class ModuleContentExcelSyncPluginTest {
    @Test
    void registersAllIndependentReverseTasksAsDryRunByDefault() {
        def project = ProjectBuilder.builder().withName('demo').build()
        project.pluginManager.apply(SheetsTaskPlugin)
        [
                syncModuleIntroductionToExcel: 'INTRODUCTION',
                syncModuleRoadmapToExcel: 'ROADMAP',
                syncModuleReadmeToExcel: 'README',
                syncModuleInterviewToExcel: 'INTERVIEW',
                syncModuleQuizToExcel: 'QUIZ',
                syncModuleVideoToExcel: 'VIDEO',
                syncModuleContentToExcel: 'ALL'
        ].each { String name, String part ->
            def task = project.tasks.named(name, SyncModuleContentToExcelTask).get()
            assertEquals(part, task.part.get())
            assertEquals('DRY_RUN', task.mode.get())
            assertEquals('sheets', task.group)
        }
    }
}
