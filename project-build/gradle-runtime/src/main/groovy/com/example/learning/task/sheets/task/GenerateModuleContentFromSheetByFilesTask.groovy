package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.SheetContentImportCoordinator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Root-only batch task. It accepts a spreadsheet URL via -PsheetFileUrl;
 * deliberately NO Gradle extension and NO tab gid parameter.
 */
abstract class GenerateModuleContentFromSheetByFilesTask extends DefaultTask {

    @Input
    @Optional
    abstract Property<String> getFileUrl()

    @Input
    abstract Property<String> getMode()

    @TaskAction
    void generate() {
        if (project != project.rootProject) {
            throw new GradleException('ByFiles task may only run at repository root.')
        }
        if (!fileUrl.orNull?.trim()) {
            throw new GradleException(
                    'Missing spreadsheet URL. Pass -PsheetFileUrl=<Google Sheets URL>.'
            )
        }
        String requestedMode = mode.get().trim().toUpperCase(Locale.ROOT)
        if (!(requestedMode in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException(
                    "Invalid sheetMode '${mode.get()}'. Use DRY_RUN or APPLY."
            )
        }

        Map<String, Object> report = new SheetContentImportCoordinator().importByFiles(
                project.rootProject.projectDir,
                fileUrl.get(),
                requestedMode == 'APPLY'
        )

        logger.lifecycle(
                '[SHEETS-FILES] Spreadsheet="{}", mode={}, Template skipped={}',
                report.spreadsheet, requestedMode, report.skippedTemplate
        )
        (report.results as List<Map>).each { Map item ->
            if (item.status == 'FAILED') {
                logger.error(
                        '[SHEETS-FILES] FAILED tab="{}" (gid={}): {}',
                        item.tab, item.gid, item.error
                )
            }
            else if (item.status == 'SKIPPED_EMPTY') {
                logger.warn(
                        '[SHEETS-FILES] SKIPPED tab="{}" (gid={}): F3:H3 empty; no write.',
                        item.tab, item.gid
                )
            }
            else {
                logger.lifecycle(
                        '[SHEETS-FILES] {} tab="{}" (gid={}), milestones={}, target={}',
                        item.status, item.tab, item.gid, item.milestones, item.file
                )
                if (item.backup) {
                    logger.lifecycle('[SHEETS-FILES] Original Roadmap backup: {}', item.backup)
                }
            }
            if (item.readme) {
                Map readme = item.readme as Map
                if (readme.status.toString().startsWith('SKIPPED_')) {
                    logger.warn(
                            '[SHEETS-FILES-README] {} tab="{}" (gid={}), missing filename rows={}',
                            readme.status, item.tab, item.gid, readme.missingRows
                    )
                }
                else {
                    logger.lifecycle(
                            '[SHEETS-FILES-README] {} tab="{}" groups={} lessons={} cleared-old-files={} directory={}',
                            readme.status, item.tab, readme.groups, readme.lessons,
                            readme.clearedOldFiles ?: 0, readme.directory
                    )
                }
            }
        }
        logger.lifecycle(
                '[SHEETS-FILES] SUMMARY total={}, failed={}, skipped-empty={}, unchanged={}, dry-run={}, imported={}, updated={}',
                report.total,
                report.failed,
                report.results.count { it.status == 'SKIPPED_EMPTY' },
                report.results.count { it.status == 'UNCHANGED' },
                report.results.count { it.status == 'DRY_RUN' },
                report.results.count { it.status == 'IMPORTED' },
                report.results.count { it.status == 'UPDATED' }
        )
        if (report.failed > 0) {
            throw new GradleException(
                    "${report.failed} of ${report.total} Sheet tabs failed; see per-tab errors above."
            )
        }
    }
}
