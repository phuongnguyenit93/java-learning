package com.example.learning.task.swagger.plugin

import com.example.learning.task.swagger.extension.GenerateApiSwaggerDescriptionExtension
import com.example.learning.task.swagger.task.GenerateApiSwaggerDescriptionTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class SwaggerTaskPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        GenerateApiSwaggerDescriptionExtension extension =
                project.extensions.create(
                        'generateApiSwaggerDescription',
                        GenerateApiSwaggerDescriptionExtension
                )


        project.tasks.register(
                'generateApiSwaggerDescription',
                GenerateApiSwaggerDescriptionTask
        ) { task ->

            task.group =
                    'documentation'

            task.description =
                    'Generate and synchronize Swagger API description metadata from Java controllers.'


            task.languages.set(
                    extension.languages
            )
        }
    }
}