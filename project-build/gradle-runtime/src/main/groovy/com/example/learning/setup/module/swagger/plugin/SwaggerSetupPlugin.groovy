package com.example.learning.setup.module.swagger.plugin

import com.example.learning.setup.module.swagger.service.SwaggerResourceConfigurationService
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.Plugin
import org.gradle.api.Project


class SwaggerSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        // ====================================================
        // Defensive guard
        // ====================================================

        if (
                !ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_SWAGGER'
                )
        ) {

            project.logger.info(
                    '[SWAGGER-SETUP] Skip {} because BUILD_SWAGGER != TRUE.',
                    project.path
            )

            return
        }


        // ====================================================
        // Resources
        // ====================================================

        SwaggerResourceConfigurationService resourceService =
                new SwaggerResourceConfigurationService(
                        project.logger
                )


        resourceService.configure(
                project
        )
    }
}
