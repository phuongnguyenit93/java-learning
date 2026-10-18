package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.FillIntroductionService
import com.example.learning.task.sheets.service.RoadmapSheetImportService
import com.example.learning.task.sheets.service.SheetsMetadataReaderService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/** Independent module-local STEP 2 introduction import, never part of ByFiles. */
abstract class FillIntroductionTask extends DefaultTask {
    @Input @Optional abstract Property<String> getFileUrl()
    @Input @Optional abstract Property<String> getSheetId()
    @Input abstract Property<String> getMode()

    @TaskAction
    void fill() {
        if (!fileUrl.orNull?.trim() || !sheetId.orNull?.trim()) {
            throw new GradleException('fillIntroduction requires -PfileUrl and -PsheetID.')
        }
        String selectedMode = mode.get().trim().toUpperCase(Locale.ROOT)
        if (!(selectedMode in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException("Invalid sheetMode '${selectedMode}'. Use DRY_RUN or APPLY.")
        }
        Map session = new SheetsMetadataReaderService().openTab(fileUrl.get(), sheetId.get())
        File root = project.rootProject.projectDir.canonicalFile
        File module = project.projectDir.canonicalFile
        String relative = root.toPath().relativize(module.toPath()).toString()
                .replace(File.separatorChar, '/' as char)
        RoadmapSheetImportService.resolveDestination(root, relative)
        if (session.moduleLocation != relative) {
            throw new GradleException("fillIntroduction I3 mismatch: module='${relative}', " +
                    "Sheet='${session.moduleLocation}'.")
        }
        String tab = "'" + session.tabTitle.toString().replace("'", "''") + "'"
        def values = session.client.spreadsheets().values()
                .get(session.spreadsheetId as String, "${tab}!B17")
                .setValueRenderOption('FORMULA').execute().values ?: []
        String content = values && values[0] ? values[0][0]?.toString() : ''
        if (content?.startsWith('=')) {
            throw new GradleException('Introduction B17 contains a formula instead of Markdown.')
        }
        Map report = FillIntroductionService.sync(root, module, content,
                selectedMode == 'APPLY')
        logger.lifecycle('[FILL-INTRODUCTION] {}: tab={}, gid={}, mode={}, file={}',
                report.status, session.tabTitle, session.sheetId, selectedMode, report.file)
    }
}
