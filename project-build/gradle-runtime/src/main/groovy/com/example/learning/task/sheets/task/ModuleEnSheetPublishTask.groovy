package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.ModuleEnSheetPublishService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** STEP 15: deterministic, AI-free, module-local Sheet EN publication. */
abstract class ModuleEnSheetPublishTask extends DefaultTask {
    @Input abstract Property<String> getPhase()
    @Input abstract Property<String> getFileUrl()
    @Input abstract Property<String> getViGid()
    @Input abstract Property<String> getMode()

    @TaskAction
    void publishEn() {
        String selected = mode.get().trim().toUpperCase(Locale.ROOT)
        if (!(selected in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException('sheetMode must be DRY_RUN or APPLY.')
        }
        Map result = new ModuleEnSheetPublishService().execute(
                project.rootProject.projectDir, project.projectDir, fileUrl.get(),
                viGid.get(), phase.get(), selected == 'APPLY')
        logger.lifecycle('[STEP-15] {} phase={} module={} gid={} title={} existing={} ' +
                'cells={} state={}', result.status, result.phase, result.module,
                result.gid, result.tab, result.existing,
                result.verified ?: result.changedCells ?: '-', result.state)
    }
}
