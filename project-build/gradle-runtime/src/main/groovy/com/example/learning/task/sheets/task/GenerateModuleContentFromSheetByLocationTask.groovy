package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.SheetContentImportCoordinator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Module-local importer. Selected tab I3 must match this Gradle module.
 */
abstract class GenerateModuleContentFromSheetByLocationTask
        extends DefaultTask {

    @Input
    abstract Property<String> getModulePath()

    @Input
    @Optional
    abstract Property<String> getSheetId()

    @Input
    @Optional
    abstract Property<String> getFileUrl()

    @Input
    abstract Property<String> getMode()

    /** ALL imports Roadmap, README, Video and Interview. */
    @Input
    abstract Property<String> getImportPart()

    @TaskAction
    void generate() {

        if (!sheetId.orNull?.trim() || !fileUrl.orNull?.trim()) {
            throw new GradleException(
                    'Missing Google Sheets configuration. Pass -PfileUrl=<Google Sheets URL> ' +
                            'and -PsheetID=<numeric tab gid>, or configure both fileUrl and sheetId ' +
                            'in generateModuleContentFromSheetByLocation { ... } in the module build.gradle.'
            )
        }

        String selectedMode = mode.get().trim().toUpperCase(Locale.ROOT)
        if (!(selectedMode in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException("Invalid sheetMode '${mode.get()}'. Use DRY_RUN or APPLY.")
        }
        String selectedPart = importPart.get().trim().toUpperCase(Locale.ROOT)
        Map<String, Object> result = new SheetContentImportCoordinator().importPartByLocation(
                project.rootProject.projectDir,
                project.projectDir,
                fileUrl.get(),
                sheetId.get(),
                selectedMode == 'APPLY',
                selectedPart
        )
        if (selectedPart != 'ALL') {
            logger.lifecycle(
                    '[SHEETS-{}] {}: tab="{}" (gid={}), location={}, mode={}, result={}',
                    selectedPart, result.status, result.tab, result.gid,
                    result.location, selectedMode, result
            )
            return
        }
        if (result.status == 'SKIPPED_EMPTY') {
            logger.warn(
                    '[SHEETS-ROADMAP] SKIPPED: F3:H3 is empty on tab="{}" (gid={}); no Roadmap write.',
                    result.tab, result.gid
            )
        }
        else {
            logger.lifecycle(
                    '[SHEETS-ROADMAP] {}: tab="{}" (gid={}), location={}, milestones={}, cleared-old-files={}, file={}',
                    result.status, result.tab, result.gid,
                    result.location, result.milestones,
                    result.clearedOldFiles ?: 0, result.file
            )
        }
        Map readme = result.readme as Map
        if (readme.status.toString().startsWith('SKIPPED_')) {
            logger.warn(
                    '[SHEETS-README] {}: tab="{}" (gid={}), missing filename rows={}, no README write.',
                    readme.status, readme.tab, readme.gid, readme.missingRows
            )
        }
        else {
            logger.lifecycle(
                    '[SHEETS-README] {}: groups={}, lessons={}, cleared-old-files={}, directory={}, metadata-status={}, metadata-file={}',
                    readme.status, readme.groups, readme.lessons,
                    readme.clearedOldFiles ?: 0, readme.directory,
                    readme.metadataStatus ?: 'N/A', readme.metadataFile ?: 'N/A'
            )
        }
        Map video = result.video as Map
        logger.lifecycle(
                '[SHEETS-VIDEO] {}: video-scripts={}, empty-video-rows={}, cleared-old-files={}, written={}, pending={}, directory={}',
                video.status, video.videoCount ?: 0, video.skippedEmptyVideos ?: 0,
                video.clearedOldFiles ?: 0, video.writtenVideos ?: 0,
                video.pendingVideos ?: 0, video.directory
        )
        Map interview = result.interview as Map
        logger.lifecycle(
                '[SHEETS-INTERVIEW] {}: questions={}, chapters={}, cleared-old-files={}, directory={}',
                interview.status, interview.questionCount ?: 0,
                interview.chapterCount ?: 0, interview.clearedOldFiles ?: 0,
                interview.directory
        )
    }
}
