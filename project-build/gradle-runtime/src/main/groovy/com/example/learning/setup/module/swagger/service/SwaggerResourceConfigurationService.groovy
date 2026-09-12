package com.example.learning.module.swagger.service

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.language.jvm.tasks.ProcessResources


class SwaggerResourceConfigurationService {

    private final Logger logger


    SwaggerResourceConfigurationService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    void configure(
            Project project
    ) {

        project.pluginManager.withPlugin(
                'java'
        ) {

            project.tasks
                    .named(
                            'processResources',
                            ProcessResources
                    )
                    .configure {
                        ProcessResources task ->

                            configureRootReadme(
                                    project,
                                    task
                            )


                            configureReadmeDirectory(
                                    project,
                                    task
                            )
                    }
        }
    }


    // ========================================================
    // Root README
    // ========================================================

    private void configureRootReadme(
            Project project,
            ProcessResources task
    ) {

        File[] projectFiles =
                project.projectDir.listFiles()


        if (projectFiles == null) {
            return
        }


        List<File> readmeFiles =
                projectFiles
                        .findAll {
                            File file ->

                                file.isFile() &&
                                        (
                                                file.name == 'README.md' ||
                                                        file.name ==~ /README\.[^.]+\.md/
                                        )
                        }
                        .sort {
                            File first,
                            File second ->

                                first.name <=> second.name
                        }


        if (readmeFiles.isEmpty()) {
            return
        }


        task.from(
                readmeFiles
        ) {
            into(
                    'META-INF/swagger'
            )
        }


        logger.info(
                '[SWAGGER-SETUP] Added {} root README file(s) to {}',
                readmeFiles.size(),
                project.path
        )
    }


    // ========================================================
    // README structure
    // ========================================================

    private void configureReadmeDirectory(
            Project project,
            ProcessResources task
    ) {

        File readmeDirectory =
                new File(
                        project.projectDir,
                        'readme'
                )


        if (!readmeDirectory.isDirectory()) {
            return
        }


        task.from(
                readmeDirectory
        ) {

            include(
                    '**'
            )


            into(
                    'META-INF/swagger/readme'
            )
        }


        logger.info(
                '[SWAGGER-SETUP] Added README structure to {}',
                project.path
        )
    }
}