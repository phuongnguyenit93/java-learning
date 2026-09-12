package com.example.learning.orchestration

import com.example.learning.setup.root.catalog.plugin.CatalogSetupPlugin
import com.example.learning.setup.root.cleanup.plugin.CleanupSetupPlugin
import com.example.learning.setup.root.database.plugin.DatabaseSetupPlugin
import com.example.learning.setup.root.structure.plugin.StructureSetupPlugin
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project


class RootOrchestrationPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        if (
                project !=
                        project.rootProject
        ) {

            throw new GradleException(
                    """
RootOrchestrationPlugin must only be applied to root project.

Current project:
${project.path}
""".stripIndent()
            )
        }


        project.pluginManager.apply(
                CatalogSetupPlugin
        )


        project.pluginManager.apply(
                StructureSetupPlugin
        )


        project.pluginManager.apply(
                DatabaseSetupPlugin
        )

        project.pluginManager.apply(
                CleanupSetupPlugin
        )
    }
}