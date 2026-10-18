package com.example.learning.setup.module.swagger.service

import org.gradle.api.Project
import org.gradle.language.jvm.tasks.ProcessResources


class SwaggerResourceConfigurationService {

    void configure(
            Project project
    ) {

        project.pluginManager.withPlugin(
                'java'
        ) {

            String serviceName =
                    project.findProperty('SERVICE_NAME')?.toString()?.trim()
            if (!serviceName) {
                serviceName = project.name
            }
            final String finalServiceName = serviceName

            // Provide the module's SERVICE_NAME to the Swagger Info component.
            File generatedName = new File(
                    project.buildDir,
                    'generated/swagger-service-name/META-INF/swagger/service-name.txt'
            )
            def generateName = project.tasks.register(
                    'generateSwaggerServiceName'
            ) { task ->
                task.inputs.property('serviceName', finalServiceName)
                task.outputs.file(generatedName)
                task.doLast {
                    generatedName.parentFile.mkdirs()
                    generatedName.setText(finalServiceName + '\n', 'UTF-8')
                }
            }

            project.tasks
                    .named(
                            'processResources',
                            ProcessResources
                    )
                    .configure {
                        ProcessResources task ->

                            task.dependsOn(generateName)
                            task.from(
                                    new File(
                                            project.buildDir,
                                            'generated/swagger-service-name'
                                    )
                            )
                    }
        }
    }
}
