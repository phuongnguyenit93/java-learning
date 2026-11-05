package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.ModuleTranslationProgressService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** Prime's explicit, crash-resilient STEP 11 worker ledger operations. */
abstract class ModuleTranslationProgressTask extends DefaultTask {
    @Input abstract Property<String> getRunId()
    @Input abstract Property<String> getAction()
    @Input abstract Property<String> getJobId()
    @Input abstract Property<String> getWorkerId()
    @Input abstract Property<String> getNote()
    @Input abstract Property<String> getRevision()

    @TaskAction
    void track() {
        String selected = action.get().trim().toUpperCase(Locale.ROOT)
        String revisionText = revision.get().trim()
        Integer expected = null
        if (selected != 'SHOW' && selected != 'INIT') {
            if (!(revisionText ==~ /[0-9]+/)) {
                throw new GradleException('Provide -PtranslationProgressRevision=<SHOW revision>.')
            }
            try {
                expected = Integer.valueOf(revisionText)
            } catch (NumberFormatException e) {
                throw new GradleException('Invalid translationProgressRevision.', e)
            }
        }
        ModuleTranslationProgressService service = new ModuleTranslationProgressService()
        Map result = selected == 'INIT' ?
                service.initialize(project.rootProject.projectDir, project.projectDir,
                        runId.get()) :
                service.execute(project.rootProject.projectDir, project.projectDir,
                        runId.get(), selected, jobId.get(), workerId.get(),
                        note.get(), expected)
        logger.lifecycle('[STEP-11-PROGRESS] {} module={} run={} revision={} file={}',
                result.result, result.module, result.runId, result.revision, result.path)
        logger.lifecycle('[STEP-11-PROGRESS] total={} QA_PASS={} PENDING={} ASSIGNED={} ' +
                'REPORTED={} REVIEW={} BLOCKER={} recoveryRequired={}',
                result.total, result.qaPass, result.pending, result.active,
                result.reported, result.review, result.blocker, result.recoveryRequired)
        (result.jobs ?: []).each { Map job ->
            logger.lifecycle('[STEP-11-JOB] {} status={} owner={} attempts={} updatedAt={} ' +
                    'files={} note={}', job.id, job.status, job.workerId ?: '-', job.attempts,
                    job.updatedAt, job.files, job.note ?: '-')
        }
    }
}
