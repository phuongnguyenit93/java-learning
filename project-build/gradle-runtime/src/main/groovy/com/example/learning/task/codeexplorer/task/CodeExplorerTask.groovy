package com.example.learning.task.codeexplorer.task

import com.example.learning.task.codeexplorer.service.CodeExplorerMaterialService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class CodeExplorerTask extends DefaultTask {
    @Input abstract Property<String> getMode()
    @Input abstract Property<Boolean> getPublish()

    @TaskAction
    void buildExplorer() {
        String selected = mode.get().toUpperCase(Locale.ROOT)
        if (!(selected in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException('codeExplorerMode must be DRY_RUN or APPLY.')
        }
        Map result = new CodeExplorerMaterialService().execute(
                project.rootProject.projectDir, project.projectDir,
                selected == 'APPLY', publish.get())
        logger.lifecycle('[CODE-EXPLORER] {} module={} files={} symbols={} ' +
                'resolved={} unresolved={} apis={} output={}', result.status,
                result.module, result.files, result.symbols, result.resolved,
                result.unresolved, result.apis, result.output)
    }
}
