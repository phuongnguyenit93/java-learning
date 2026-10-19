package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.QuizSheetNormalizeService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Root-only deterministic Google Sheets Quiz YAML normalizer
 * (accepts legacy JSON source cells).
 * Never writes module files and never calls an AI model.
 */
abstract class NormalizeQuizQuestionsFromSheetTask extends DefaultTask {

    @Input @Optional
    abstract Property<String> getFileUrl()

    @Input @Optional
    abstract Property<String> getSheetId()

    @Input
    abstract Property<String> getMode()

    @TaskAction
    void normalize() {
        if (project != project.rootProject) {
            throw new GradleException('Quiz normalization must run at repository root.')
        }
        if (!fileUrl.orNull?.trim() || !sheetId.orNull?.trim()) {
            throw new GradleException(
                    'Pass -PfileUrl=<Google Sheets URL> and -PsheetID=<numeric tab gid>.'
            )
        }
        String selectedMode = mode.get().trim().toUpperCase(Locale.ROOT)
        if (!(selectedMode in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException(
                    "Invalid quizMode '" + mode.get() + "'; use DRY_RUN or APPLY."
            )
        }
        Map report = new QuizSheetNormalizeService().execute(
                fileUrl.get(), sheetId.get(), selectedMode == 'APPLY'
        )
        logger.lifecycle(
                '[QUIZ-NORMALIZE] {} tab="{}" gid={} scanned={} populated={} ' +
                        'changed={} unchanged={} questions={} written={} readback={}',
                report.status, report.tab, report.gid, report.scanned,
                report.populated, report.changed, report.unchanged,
                report.questions, report.written, report.readback
        )
    }
}
