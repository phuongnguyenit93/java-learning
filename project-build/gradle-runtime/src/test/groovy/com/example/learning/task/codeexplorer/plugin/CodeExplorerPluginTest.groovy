package com.example.learning.task.codeexplorer.plugin

import com.example.learning.task.codeexplorer.task.CodeExplorerTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

class CodeExplorerPluginTest {
    @Test
    void registersExplicitOptInBuildAndPublishTasks() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply(CodeExplorerPlugin)
        def build = project.tasks.named('generateModuleCodeExplorer',CodeExplorerTask).get()
        def publish = project.tasks.named('publishModuleCodeExplorer',CodeExplorerTask).get()
        assertFalse(build.publish.get())
        assertTrue(publish.publish.get())
        assertEquals('DRY_RUN',build.mode.get())
        assertEquals('DRY_RUN',publish.mode.get())
    }
}
