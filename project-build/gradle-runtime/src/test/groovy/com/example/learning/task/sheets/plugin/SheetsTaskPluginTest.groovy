package com.example.learning.task.sheets.plugin

import com.example.learning.task.sheets.task.GenerateModuleContentFromSheetByLocationTask
import com.example.learning.task.sheets.task.FillIntroductionTask
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class SheetsTaskPluginTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder()


    @Test
    void registersFourIndependentModuleTasksAndCombinedTask() {
        def project = ProjectBuilder.builder().withName('module-under-test').build()
        project.pluginManager.apply(SheetsTaskPlugin)

        Map<String, String> expected = [
                generateModuleContentFromSheetByLocation: 'ALL',
                generateModuleRoadmapFromSheetByLocation: 'ROADMAP',
                generateModuleReadmeFromSheetByLocation : 'README',
                generateModuleVideoFromSheetByLocation  : 'VIDEO',
                generateModuleInterviewFromSheetByLocation: 'INTERVIEW',
                generateModuleAssessmentsFromSheetByLocation: 'ASSESSMENTS'
        ]
        expected.each { String taskName, String part ->
            def task = project.tasks.named(
                    taskName, GenerateModuleContentFromSheetByLocationTask
            ).get()
            assertEquals(part, task.importPart.get())
            assertEquals('DRY_RUN', task.mode.get())
            assertEquals('sheets', task.group)
        }
        assertEquals(6, expected.size())
        def fillIntro = project.tasks.named('fillIntroduction', FillIntroductionTask).get()
        assertEquals('DRY_RUN', fillIntro.mode.get())
        assertEquals('sheets', fillIntro.group)
        def extension = project.extensions.getByName(
                'generateModuleContentFromSheetByLocation'
        )
        extension.fileUrl.set('https://docs.google.com/spreadsheets/d/example/edit')
        extension.sheetId.set('42')
        assertEquals(extension.fileUrl.get(), fillIntro.fileUrl.get())
        assertEquals(extension.sheetId.get(), fillIntro.sheetId.get())
        expected.keySet().each { String taskName ->
            def task = project.tasks.named(taskName,
                    GenerateModuleContentFromSheetByLocationTask).get()
            assertEquals(extension.fileUrl.get(), task.fileUrl.get())
            assertEquals(extension.sheetId.get(), task.sheetId.get())
        }
    }

    @Test
    void commandLineSheetLocationOverridesExtensionForEveryIndependentTask() {
        File projectDirectory = temporary.newFolder('cli-override')
        new File(projectDirectory, 'settings.gradle').setText(
                "rootProject.name = 'cli-override'\n", 'UTF-8')
        new File(projectDirectory, 'build.gradle').setText('''
plugins { id 'com.example.learning.task.sheets.plugin' }
generateModuleContentFromSheetByLocation {
    sheetId = '999'
    fileUrl = 'https://docs.google.com/spreadsheets/d/extension-value/edit'
}
tasks.register('verifySheetSelection') {
    doLast {
        ['generateModuleRoadmapFromSheetByLocation',
         'generateModuleReadmeFromSheetByLocation',
         'generateModuleVideoFromSheetByLocation',
         'generateModuleInterviewFromSheetByLocation',
         'generateModuleContentFromSheetByLocation',
         'fillIntroduction'].each { name ->
            def selected = tasks.named(name).get()
            assert selected.sheetId.get() == '123456789'
            assert selected.fileUrl.get() == 'https://docs.google.com/spreadsheets/d/cli-value/edit'
        }
        println 'SHEET_SELECTION_PASS'
    }
}
''', 'UTF-8')
        def result = GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withArguments('verifySheetSelection',
                        '-PsheetID=123456789',
                        '-PfileUrl=https://docs.google.com/spreadsheets/d/cli-value/edit')
                .withPluginClasspath()
                .build()
        assertTrue(result.output.contains('SHEET_SELECTION_PASS'))
    }
}
