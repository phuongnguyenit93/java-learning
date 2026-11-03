package com.example.learning.task.codeexplorer.plugin

import com.example.learning.task.codeexplorer.task.CaseStudyExplorerTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Case Study source explorer is enabled through BUILD_CASE_STUDY_EXPLORER
 * in each module's master.json. Shared Java indexing stays code-only.
 */
class CodeExplorerPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        [
                generateCaseStudyExplorer: false,
                publishCaseStudyExplorer : true
        ].each { String name, boolean publish ->
            project.tasks.register(name, CaseStudyExplorerTask) { task ->
                task.group = 'code explorer'
                task.description = 'STEP 8 completion: filter and index Case Study Java by group/case.'
                task.publish.convention(publish)
                task.mode.convention(project.providers.gradleProperty('caseStudyExplorerMode')
                        .orElse(project.providers.gradleProperty('mode').orElse('DRY_RUN')))
                task.groupId.convention(project.providers.gradleProperty('groupID'))
                task.caseIndex.convention(project.providers.gradleProperty('caseIndex'))
            }
        }
    }
}
