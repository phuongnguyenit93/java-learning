package com.example.learning.task.env.generate.task

import com.example.learning.task.env.generate.service.GenerateEnvService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(
        because = 'Updates local environment files'
)
abstract class GenerateEnvTask
        extends DefaultTask {

    // ========================================================
    // Input
    // ========================================================

    @Input
    abstract Property<String> getServiceName()

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getApplicationFile()


    /**
     * Module có thể không có docker-compose.yml.
     */
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getDockerComposeFile()


    // ========================================================
    // Output
    // ========================================================

    @OutputFile
    abstract RegularFileProperty getEnvFile()


    @OutputFile
    abstract RegularFileProperty getEnvExampleFile()


    // ========================================================
    // Action
    // ========================================================

    @TaskAction
    void generate() {

        String service =
                serviceName.orNull


        if (
                service == null ||
                        service.isBlank()
        ) {

            throw new GradleException(
                    """
Missing SERVICE_NAME.

Please configure:

SERVICE_NAME=...

in gradle.properties.
"""
            )
        }


        // ====================================================
        // Services
        // ====================================================

        GenerateEnvService generationService =
                new GenerateEnvService(
                        project,
                )


        // ====================================================
        // Docker compose
        // ====================================================

        File dockerCompose =
                dockerComposeFile.isPresent()
                        ? dockerComposeFile.get().asFile
                        : null


        // ====================================================
        // Execute
        // ====================================================

        generationService.generate(
                service,
                applicationFile.get().asFile,
                dockerCompose,
                envFile.get().asFile,
                envExampleFile.get().asFile
        )
    }
}