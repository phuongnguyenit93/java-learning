package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.ModuleLanguageCloneService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** STEP 13: clone VI module content to EN without translating any text. */
abstract class CloneModuleViToEnTask extends DefaultTask {
    @Input abstract Property<String> getMode()

    @TaskAction
    void cloneLanguage() {
        String selected = mode.get().trim().toUpperCase(Locale.ROOT)
        if (!(selected in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException('sheetMode must be DRY_RUN or APPLY.')
        }
        Map result = new ModuleLanguageCloneService().execute(
                project.rootProject.projectDir, project.projectDir, selected == 'APPLY')
        logger.lifecycle('[STEP-13] {} module={} files={} groups={} changes={} backup={}',
                result.status, result.module, result.files, result.groups,
                result.changes, result.backup ?: 'N/A')
    }
}
