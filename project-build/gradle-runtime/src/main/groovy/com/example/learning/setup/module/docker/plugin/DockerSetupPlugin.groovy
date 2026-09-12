package com.example.learning.module.docker.plugin

import com.example.learning.module.docker.service.DockerComposeConfigurationService
import org.gradle.api.Plugin
import org.gradle.api.Project


class DockerSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        // ====================================================
        // Defensive guard
        // ====================================================

        File dockerComposeFile =
                new File(
                        project.projectDir,
                        'docker-compose.yml'
                )


        if (!dockerComposeFile.isFile()) {

            project.logger.info(
                    '[DOCKER-SETUP] Skip {} because docker-compose.yml was not found.',
                    project.path
            )

            return
        }


        // ====================================================
        // Configure
        // ====================================================

        DockerComposeConfigurationService service =
                new DockerComposeConfigurationService(
                        project.logger
                )


        service.configure(
                project
        )
    }
}