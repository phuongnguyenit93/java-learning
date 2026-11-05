package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.excel.ModuleExcelGenerationService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** Single module-local STEP 12 task; phases are services, not separate tasks. */
abstract class GenerateModuleExcelTask extends DefaultTask {
    @Input abstract ListProperty<String> getLanguageList()
    @Input abstract Property<String> getMode()

    @TaskAction
    void generate() {
        String selected = mode.get().trim().toUpperCase(Locale.ROOT)
        if (!(selected in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException('excelMode must be DRY_RUN or APPLY.')
        }
        List<String> languages = languageList.get()
        Map result = new ModuleExcelGenerationService().execute(
                project.rootProject.projectDir, project.projectDir,
                languages, selected == 'APPLY')
        result.files.each { Map file ->
            logger.lifecycle('[MODULE-XLSX] {} module={} language={} chapters={} lessons={} ' +
                    'interview={} cases={} quiz={} output={} sha256={}',
                    result.status, result.module, file.language,
                    file.chapters, file.lessons, file.interview,
                    file.cases, file.quiz, file.output, file.sha256)
        }
    }
}
