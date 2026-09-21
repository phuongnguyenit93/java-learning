package com.example.learning.task.swagger.plugin

import com.example.learning.task.swagger.task.GenerateApiSwaggerDescriptionTask
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.Plugin
import org.gradle.api.Project

class SwaggerTaskPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        project.tasks.register(
                'generateApiSwaggerDescription',
                GenerateApiSwaggerDescriptionTask
        ) { task ->

            task.group =
                    'documentation'

            task.description =
                    'Generate and synchronize Swagger API description metadata from Java controllers.'


            task.languages.set(
                    project.provider {
                        ProjectPropertyUtils.getStringList(
                                project,
                                'MODULE_LANGUAGE'
                        )
                    }
            )
        }
    }
}
