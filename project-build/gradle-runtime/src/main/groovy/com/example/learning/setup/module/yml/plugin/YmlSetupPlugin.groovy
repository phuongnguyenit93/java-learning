package com.example.learning.module.yml.plugin

import com.example.learning.module.yml.service.ApplicationYmlInitializerService
import com.example.learning.module.yml.service.YmlResourceConfigurationService
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.Plugin
import org.gradle.api.Project


class YmlSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        // ====================================================
        // Resource policy
        // ====================================================

        YmlResourceConfigurationService resourceService =
                new YmlResourceConfigurationService(
                        project.logger
                )


        resourceService.configure(
                project
        )


        // ====================================================
        // application.yml initialization
        // ====================================================

        if (
                ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_YML'
                )
        ) {

            ApplicationYmlInitializerService initializerService =
                    new ApplicationYmlInitializerService(
                            project.logger
                    )


            initializerService.initialize(
                    project
            )
        }
    }
}