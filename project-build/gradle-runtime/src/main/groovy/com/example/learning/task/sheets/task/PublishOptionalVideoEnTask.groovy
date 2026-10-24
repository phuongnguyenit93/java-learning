package com.example.learning.task.sheets.task

import com.example.learning.task.sheets.service.ModuleOptionalVideoEnPublishService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** Optional Video-only EN publishing after the mandatory STEP 15 / STEP_FINAL. */
abstract class PublishOptionalVideoEnTask extends DefaultTask {
    @Input abstract Property<String> getFileUrl()
    @Input abstract Property<String> getViGid()
    @Input abstract Property<String> getMode()

    @TaskAction
    void publishVideoEn() {
        String modeValue = mode.get().trim().toUpperCase(Locale.ROOT)
        if (!(modeValue in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException('sheetMode must be DRY_RUN or APPLY.')
        }
        Map result = new ModuleOptionalVideoEnPublishService().execute(
                project.rootProject.projectDir, project.projectDir,
                fileUrl.get(), viGid.get(), modeValue == 'APPLY')
        logger.lifecycle('[OPTIONAL-VIDEO-EN] {} module={} enGid={} chapters={} cells={}',
                result.status, result.module, result.enGid,
                result.chapters, result.cells)
    }
}
