package com.example.learning.task.codeexplorer.plugin

import com.example.learning.task.codeexplorer.task.CaseStudyExplorerTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

class CodeExplorerPluginTest {
    @Test
    void registersOnlyCaseStudyBuildAndPublishTasks() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply(CodeExplorerPlugin)
        def build = project.tasks.named('generateCaseStudyExplorer',CaseStudyExplorerTask).get()
        def publish = project.tasks.named('publishCaseStudyExplorer',CaseStudyExplorerTask).get()
        assertFalse(build.publish.get())
        assertTrue(publish.publish.get())
        assertEquals('DRY_RUN',build.mode.get())
        assertEquals('DRY_RUN',publish.mode.get())
        assertNull(project.tasks.findByName('generateModuleCodeExplorer'))
        assertNull(project.tasks.findByName('publishModuleCodeExplorer'))
    }
}
