package com.example.learning.setup.root.catalog.plugin

import com.example.learning.setup.root.catalog.service.ModuleDependencyCatalogService
import org.gradle.api.Plugin
import org.gradle.api.Project


class CatalogSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        // ====================================================
        // Root project guard
        // ====================================================

        if (project != project.rootProject) {

            project.logger.warn(
                    '[DEPENDENCY-SETUP] Skip non-root project: {}',
                    project.path
            )

            return
        }


        ModuleDependencyCatalogService service =
                new ModuleDependencyCatalogService(
                        project.logger
                )


        // ====================================================
        // Wait for all module dependencies to be declared
        // ====================================================

        project.gradle.projectsEvaluated {

            service.generate(
                    project
            )
        }
    }
}