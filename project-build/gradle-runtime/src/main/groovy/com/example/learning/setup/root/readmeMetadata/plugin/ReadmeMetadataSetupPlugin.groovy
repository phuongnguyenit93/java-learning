package com.example.learning.setup.root.readmeMetadata.plugin

import com.example.learning.setup.root.readmeMetadata.service.ReadmeMetadataSyncService
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project


class ReadmeMetadataSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        if (project != project.rootProject) {

            throw new GradleException(
                    """
ReadmeMetadataSetupPlugin must only be applied to root project.

Current project:
${project.path}
""".stripIndent()
            )
        }


        ReadmeMetadataSyncService service =
                new ReadmeMetadataSyncService(
                        project.logger
                )


        project.tasks.register(
                'syncMetadataReadme'
        ) {
            task ->


                task.group =
                        'documentation'


                task.description =
                        'Synchronize README knowledge metadata skeletons for every real learning module and discovered README language.'


                task.doLast {

                    service.sync(
                            project
                    )
                }
        }

    }
}
