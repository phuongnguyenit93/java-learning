package com.example.learning.setup.module.docker.service

import com.example.learning.utils.ProjectPluginUtils
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger


class DockerComposeConfigurationService {

    private static final String DOCKER_COMPOSE_PLUGIN =
            'com.avast.gradle.docker-compose'


    private final Logger logger


    DockerComposeConfigurationService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    void configure(
            Project project
    ) {

        // ====================================================
        // Apply Docker Compose plugin
        // ====================================================

        ProjectPluginUtils.apply(
                project,
                DOCKER_COMPOSE_PLUGIN
        )


        // ====================================================
        // Docker profile
        // ====================================================

        String profile =
                ProjectPropertyUtils.getString(
                        project,
                        'DOCKER_PROFILE'
                )


        if (
                profile == null ||
                        profile.isBlank()
        ) {

            logger.lifecycle(
                    '🐳 [DOCKER-SETUP] [{}] Docker Compose ready (Profile: default)',
                    project.path
            )

            return
        }


        // ====================================================
        // Configure Docker Compose
        // ====================================================

        Object dockerComposeExtension =
                project.extensions.findByName(
                        'dockerCompose'
                )


        if (dockerComposeExtension == null) {

            throw new GradleException(
                    """
Docker Compose extension was not found after applying plugin:

${DOCKER_COMPOSE_PLUGIN}

Project:
${project.path}
""".stripIndent()
            )
        }


        try {

            dockerComposeExtension
                    .environment
                    .put(
                            'SPRING_PROFILES_ACTIVE',
                            profile
                    )
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to configure Docker Compose for project:

${project.path}

Profile:
${profile}

Reason:
${exception.message}
""".stripIndent(),
                    exception
            )
        }


        logger.lifecycle(
                '🐳 [DOCKER-SETUP] [{}] Docker Compose ready (Profile: {})',
                project.path,
                profile
        )
    }
}
