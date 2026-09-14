package com.example.learning.setup.module.yml.plugin

import com.example.learning.setup.module.yml.service.YmlInitializerService
import com.example.learning.setup.module.yml.service.YmlResourceConfigurationService
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.Plugin
import org.gradle.api.Project


class YmlSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        if (
                !ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_YML'
                )
        ) {

            project.logger.info(
                    '[YML-SETUP] Skip {} because BUILD_YML != TRUE.',
                    project.path
            )

            return
        }

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
        // YAML skeleton initialization
        // ====================================================

        YmlInitializerService initializerService =
                new YmlInitializerService(
                        project.logger
                )


        initializerService.initialize(
                project
        )
    }
}
