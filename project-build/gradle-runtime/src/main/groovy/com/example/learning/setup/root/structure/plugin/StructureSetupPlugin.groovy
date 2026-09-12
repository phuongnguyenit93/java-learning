package com.example.learning.setup.root.structure.plugin

import com.example.learning.setup.root.structure.service.ProjectStructureService
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project


class StructureSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        // ====================================================
        // Root guard
        // ====================================================

        if (project != project.rootProject) {

            throw new GradleException(
                    """
StructureSetupPlugin must only be applied to root project.

Current project:
${project.path}
""".stripIndent()
            )
        }


        ProjectStructureService service =
                new ProjectStructureService(
                        project.logger
                )


        // ====================================================
        // Generate after every module has been evaluated
        // ====================================================

        project.gradle.projectsEvaluated {

            service.generate(
                    project
            )
        }
    }
}