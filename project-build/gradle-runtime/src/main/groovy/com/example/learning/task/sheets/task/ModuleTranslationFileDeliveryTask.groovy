package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.ModuleTranslationFileDeliveryService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** Worker-owned delivery: validate and copy ONE whole file from temp to module. */
abstract class ModuleTranslationFileDeliveryTask extends DefaultTask {
    @Input abstract Property<String> getMode()
    @Input abstract Property<String> getRunId()
    @Input abstract Property<String> getTargetLanguage()
    @Input abstract Property<String> getFilePath()
    @Input abstract Property<String> getWorkerId()

    @TaskAction
    void deliver() {
        String requested = mode.get().trim().toUpperCase(Locale.ROOT)
        if (!(requested in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException('translationMode must be DRY_RUN or APPLY.')
        }
        Map result = new ModuleTranslationFileDeliveryService().execute(
                project.rootProject.projectDir, project.projectDir,
                runId.get(), targetLanguage.get(), filePath.get(),
                workerId.get(), requested == 'APPLY')
        logger.lifecycle('[STEP-11-FILE-DELIVERY] {} language={} target={} temp={} sha256Before={} sha256After={}',
                result.status, result.language, result.target, result.temp,
                result.beforeSha256, result.afterSha256)
    }
}
