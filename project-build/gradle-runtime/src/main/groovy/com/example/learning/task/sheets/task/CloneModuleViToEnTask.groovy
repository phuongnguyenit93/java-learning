package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.ModuleLanguageCloneService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** STEP 10: CLEAN -> FILL all canonical VI subtrees into EN without translation. */
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
        logger.lifecycle('[STEP-10] {} module={} files={} groups={} changes={} enOnlyRemoved={} tempCleanup={}',
                result.status, result.module, result.files, result.groups,
                result.changes, result.enOnlyRemoved, result.temporaryCleanup ?: 'N/A')
        logger.lifecycle('[STEP-10] filesByScope={}', result.filesByScope)
    }
}
