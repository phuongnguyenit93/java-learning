package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.ModuleTranslationCatalogService
import com.example.learning.task.sheets.service.ModuleTranslationProgressService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** STEP 11: catalog/baseline and validate EN translation worker jobs. */
abstract class ModuleTranslationAuditTask extends DefaultTask {
    @Input abstract Property<String> getOperation()
    @Input abstract Property<String> getMode()
    @Input abstract Property<String> getRunId()
    @Input abstract Property<String> getWorkerMode()
    @Input abstract Property<String> getWorkerCount()
    @Input abstract Property<String> getTargetLanguage()

    @TaskAction
    void auditTranslation() {
        String requested = mode.get().toUpperCase(Locale.ROOT)
        if (!(requested in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException('translationMode must be DRY_RUN or APPLY.')
        }
        String countText = workerCount.get().trim()
        Integer count = null
        if (countText) {
            if (!(countText ==~ /\d+/)) {
                throw new GradleException('BLOCKER: INVALID_TRANSLATION_WORKER_COUNT')
            }
            try {
                count = Integer.parseInt(countText)
            } catch (NumberFormatException ignored) {
                throw new GradleException('BLOCKER: INVALID_TRANSLATION_WORKER_COUNT')
            }
        }
        if (operation.get() == 'PREPARE' && requested == 'APPLY') {
            new ModuleTranslationProgressService().preflightPrepare(
                    project.rootProject.projectDir, project.projectDir, runId.get())
        }
        Map result = new ModuleTranslationCatalogService().execute(
                project.rootProject.projectDir, project.projectDir,
                runId.get(), operation.get(), requested == 'APPLY',
                workerMode.get(), count, targetLanguage.get())
        // The journal is a Prime-side recovery checkpoint, not part of the
        // immutable baseline and never owned by translation workers.
        if (operation.get() == 'PREPARE' && requested == 'APPLY') {
            Map checkpoint = new ModuleTranslationProgressService().initialize(
                    project.rootProject.projectDir, project.projectDir, runId.get())
            logger.lifecycle('[STEP-11-PROGRESS] {} revision={} file={}',
                    checkpoint.result, checkpoint.revision, checkpoint.path)
        }
        logger.lifecycle('[STEP-11] {} module={} run={} language={} total={} jobs={} mode={} maxWorkers={} ' +
                'translated={} pending={} manifest={} semanticQA={}',
                result.status, result.module, result.runId, result.targetLanguage,
                result.total, result.jobCount, result.workerMode, result.maxWorkers,
                result.translated ?: 0, result.pending ?: 0,
                result.manifest ?: 'EXISTING', result.semanticQaRequired ?: false)
        if (result.files) {
            (result.files as List).each { Map item ->
                logger.lifecycle('[STEP-11-FILE] {} {} {} temp={}',
                        item.type, item.enPath, item.result, item.workPath ?: '-')
            }
        }
        (result.jobs ?: []).each { Map job ->
            logger.lifecycle('[STEP-11-JOB] {} rule={} files={}',
                    job.id, job.rule, job.files)
        }
    }
}
