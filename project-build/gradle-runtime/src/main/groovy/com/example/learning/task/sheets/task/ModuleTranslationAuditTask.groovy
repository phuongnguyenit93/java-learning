package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.ModuleTranslationCatalogService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** STEP 14: catalog/backup or validate one-file-at-a-time translated EN content. */
abstract class ModuleTranslationAuditTask extends DefaultTask {
    @Input abstract Property<String> getOperation()
    @Input abstract Property<String> getMode()
    @Input abstract Property<String> getRunId()

    @TaskAction
    void auditTranslation() {
        String requested = mode.get().toUpperCase(Locale.ROOT)
        if (!(requested in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException('translationMode must be DRY_RUN or APPLY.')
        }
        Map result = new ModuleTranslationCatalogService().execute(
                project.rootProject.projectDir, project.projectDir,
                runId.get(), operation.get(), requested == 'APPLY')
        logger.lifecycle('[STEP-14] {} module={} run={} total={} translated={} pending={} ' +
                'manifest={} semanticQA={}', result.status, result.module, result.runId,
                result.total, result.translated ?: 0, result.pending ?: 0,
                result.manifest ?: 'EXISTING', result.semanticQaRequired ?: false)
        if (result.files) {
            (result.files as List).each { Map item ->
                logger.lifecycle('[STEP-14-FILE] {} {} {}', item.type, item.enPath,
                        item.result)
            }
        }
    }
}
