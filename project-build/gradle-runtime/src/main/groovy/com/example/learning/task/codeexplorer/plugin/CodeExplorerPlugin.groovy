package com.example.learning.task.codeexplorer.plugin

import com.example.learning.task.codeexplorer.task.CodeExplorerTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Optional module-local, AI-free source material. Only executes when the
 * module explicitly owns content/code-explorer/material.yml.
 */
class CodeExplorerPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        [
                generateModuleCodeExplorer: false,
                publishModuleCodeExplorer : true
        ].each { String name, boolean publish ->
            project.tasks.register(name, CodeExplorerTask) { task ->
                task.group = 'code explorer'
                task.description = publish ?
                        'Build and publish this module Code Explorer to static learning data.' :
                        'Build this module Code Explorer as a portable static bundle.'
                task.publish.convention(publish)
                task.mode.convention(project.providers.gradleProperty('codeExplorerMode')
                        .orElse('DRY_RUN'))
            }
        }
    }
}
