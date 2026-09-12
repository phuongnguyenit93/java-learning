package com.example.learning.task.env.plugin

import com.example.learning.task.env.generate.task.GenerateEnvTask
import com.example.learning.utils.GradleBuildUtils
import org.gradle.api.Plugin
import org.gradle.api.Project

class EnvTaskPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        // ====================================================
        // application.yml
        //
        // application.yml là runtime source of truth.
        // Không sử dụng application-merged.yml.
        // ====================================================

        File applicationDirectory =
                GradleBuildUtils.findBaseDirectory(
                        project
                )


        File applicationFile =
                new File(
                        applicationDirectory,
                        'application.yml'
                )


        // ====================================================
        // docker-compose.yml
        // ====================================================

        File dockerComposeFile =
                project.file(
                        'docker-compose.yml'
                )


        // ====================================================
        // Register task
        // ====================================================

        project.tasks.register(
                'generateEnvFile',
                GenerateEnvTask
        ) { task ->

            task.group =
                    'documentation'

            task.description =
                    'Extract environment variables from application.yml and docker-compose.yml.'


            // ------------------------------------------
            // SERVICE_NAME
            // ------------------------------------------

            task.serviceName.set(
                    project.provider {

                        project.findProperty(
                                'SERVICE_NAME'
                        )
                                ?.toString()
                                ?: ''
                    }
            )


            // ------------------------------------------
            // application.yml
            // ------------------------------------------

            task.applicationFile.set(
                    applicationFile
            )


            // ------------------------------------------
            // docker-compose.yml
            // ------------------------------------------

            if (dockerComposeFile.exists()) {

                task.dockerComposeFile.set(
                        dockerComposeFile
                )
            }


            // ------------------------------------------
            // .env
            // ------------------------------------------

            task.envFile.set(
                    project.layout
                            .projectDirectory
                            .file('.env')
            )


            // ------------------------------------------
            // .env.example
            // ------------------------------------------

            task.envExampleFile.set(
                    project.layout
                            .projectDirectory
                            .file('.env.example')
            )
        }
    }
}