package com.example.learning.task.codeexplorer.task

import com.example.learning.task.codeexplorer.service.CaseStudyExplorerService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

abstract class CaseStudyExplorerTask extends DefaultTask {
    @Input abstract Property<String> getMode()
    @Input abstract Property<Boolean> getPublish()
    @Optional @Input abstract Property<String> getGroupId()
    @Optional @Input abstract Property<String> getCaseIndex()

    @TaskAction
    void generate() {
        String choice = mode.get().toUpperCase(Locale.ROOT)
        if (!(choice in ['DRY_RUN', 'APPLY'])) {
            throw new GradleException('caseStudyExplorerMode must be DRY_RUN or APPLY.')
        }
        Integer group = number(groupId.orNull, 'groupID')
        Integer item = number(caseIndex.orNull, 'caseIndex')
        Map result = new CaseStudyExplorerService().execute(
                project.rootProject.projectDir, project.projectDir,
                choice == 'APPLY', publish.get(), group, item)
        logger.lifecycle('[STEP9-EXPLORER] {} module={} cases={} files={} complete={} output={}',
                result.status, result.module, result.cases, result.files,
                result.complete, result.output)
    }

    private static Integer number(String value, String label) {
        if (value == null) return null
        if (!(value ==~ /[1-9][0-9]*/)) {
            throw new GradleException('Invalid STEP9 ' + label + ': ' + value)
        }
        return Integer.parseInt(value)
    }
}
