package com.example.learning.setup.module.executioncontext.plugin

import com.example.learning.setup.module.executioncontext.task.GenerateExecutionSourceContextTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer


class ExecutionContextSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        File generatedResourceDirectory =
                new File(
                        project.buildDir,
                        'generated/execution-context/resources'
                )


        def taskProvider =
                project.tasks.register(
                        'generateExecutionSourceContext',
                        GenerateExecutionSourceContextTask
                ) { task ->

                    task.group =
                            'documentation'


                    task.description =
                            'Generate deterministic Controller/related-source context for runtime execution analysis.'


                    task.sourceDirectory.set(
                            project.layout.projectDirectory.dir(
                                    'src/main/java'
                            )
                    )


                    task.outputFile.set(
                            new File(
                                    generatedResourceDirectory,
                                    'META-INF/execution-context/source-context.json'
                            )
                    )
                }


        project.pluginManager.withPlugin(
                'java'
        ) {

            SourceSetContainer sourceSets =
                    project.extensions.getByType(
                            SourceSetContainer
                    )


            sourceSets
                    .named(
                            'main'
                    ) {
                        resources.srcDir(
                                generatedResourceDirectory
                        )
                    }


            project.tasks
                    .named(
                            'processResources'
                    )
                    .configure {
                        dependsOn(
                                taskProvider
                        )
                    }
        }
    }
}
