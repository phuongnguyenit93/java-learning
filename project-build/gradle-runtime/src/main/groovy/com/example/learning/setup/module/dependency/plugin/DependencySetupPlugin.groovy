package com.example.learning.setup.module.dependency.plugin

import com.example.learning.setup.module.dependency.extension.ImplementationModuleExtension
import com.example.learning.setup.module.dependency.service.ImplementationModuleService
import org.gradle.api.Plugin
import org.gradle.api.Project


class DependencySetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        // ====================================================
        // Extension
        // ====================================================

        ImplementationModuleExtension extension =
                project.extensions.create(
                        'implementationModule',
                        ImplementationModuleExtension
                )


        extension
                .serviceList
                .convention(
                        ''
                )


        // ====================================================
        // Service
        // ====================================================

        ImplementationModuleService service =
                new ImplementationModuleService(
                        project.logger
                )


        // ====================================================
        // Wait until this project's build.gradle has configured
        // implementationModule.serviceList, but register dependencies
        // before the global projectsEvaluated listeners can resolve
        // compileClasspath (and lock implementation).
        // ====================================================

        project
                .afterEvaluate {

                    String serviceList =
                            extension
                                    .serviceList
                                    .get()


                    if (serviceList.isBlank()) {
                        return
                    }


                    service.configure(
                            project,
                            serviceList
                    )
                }
    }
}
