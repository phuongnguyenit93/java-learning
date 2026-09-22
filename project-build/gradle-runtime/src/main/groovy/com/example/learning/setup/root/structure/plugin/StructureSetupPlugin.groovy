package com.example.learning.setup.root.structure.plugin

import com.example.learning.setup.root.structure.service.ProjectStructureService
import com.example.learning.setup.root.structure.service.PortalApiProjectionService
import com.example.learning.setup.root.structure.service.PortalInterviewProjectionService
import com.example.learning.setup.root.structure.service.PortalKnowledgeProjectionService
import com.example.learning.setup.root.structure.service.PortalQuizProjectionService
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project


class StructureSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        // ====================================================
        // Root guard
        // ====================================================

        if (project != project.rootProject) {

            throw new GradleException(
                    """
StructureSetupPlugin must only be applied to root project.

Current project:
${project.path}
""".stripIndent()
            )
        }


        ProjectStructureService service =
                new ProjectStructureService(
                        project.logger
                )


        PortalKnowledgeProjectionService knowledgeService =
                new PortalKnowledgeProjectionService(
                        project.logger
                )


        PortalApiProjectionService apiService =
                new PortalApiProjectionService(
                        project.logger
                )


        PortalQuizProjectionService quizService =
                new PortalQuizProjectionService(
                        project.logger
                )


        PortalInterviewProjectionService interviewService =
                new PortalInterviewProjectionService(
                        project.logger
                )


        def portalDataInputs =
                project.fileTree(
                        new File(
                                project.projectDir,
                                'module'
                        )
                ) {
                    include '**/gradle.properties'
                    include '**/master.json'
                    include '**/readme/**'
                    include '**/src/main/resources/swagger/**'
                    include '**/src/main/resources/quiz/**'
                    include '**/src/main/resources/interview/**'

                    exclude '**/build/**'
                    exclude '**/.gradle/**'
                    exclude '**/out/**'
                    exclude '**/target/**'
                }


        def cleanupLegacyPortalData =
                project.tasks.register(
                        'cleanupLegacyPortalData'
                ) {
                    task ->

                        task.group =
                                'learning portal'


                        task.description =
                                'Remove obsolete Portal generated-data layouts from the build directory.'


                        task.doLast {

                            project.delete(
                                    new File(
                                            project.projectDir,
                                            'project-portal/build/generated/portal-data/data'
                                    ),
                                    new File(
                                            project.projectDir,
                                            'project-portal/build/generated/portal-data/overview'
                                    ),
                                    new File(
                                            project.projectDir,
                                            'project-portal/build/generated/portal-data/knowledge'
                                    )
                            )
                        }
                }


        def generatePortalModuleData =
                project.tasks.register(
                        'generatePortalModuleData'
                ) {
                    task ->

                        task.group =
                                'learning portal'


                        task.description =
                                'Generate the Portal module catalog and module overview projections.'


                        task.dependsOn(
                                cleanupLegacyPortalData
                        )


                        task.inputs.files(
                                portalDataInputs
                        )


                        task.doLast {

                            service.generatePortalData(
                                    project
                            )
                        }
                }


        def generatePortalKnowledge =
                project.tasks.register(
                        'generatePortalKnowledge'
                ) {
                    task ->

                        task.group =
                                'learning portal'


                        task.description =
                                'Generate static Portal knowledge metadata and Markdown section projections.'


                        task.dependsOn(
                                cleanupLegacyPortalData
                        )


                        task.inputs.files(
                                portalDataInputs
                        )


                        task.doLast {

                            knowledgeService.generate(
                                    project
                            )
                        }
                }


        def generatePortalApi =
                project.tasks.register(
                        'generatePortalApi'
                ) {
                    task ->

                        task.group =
                                'learning portal'


                        task.description =
                                'Copy complete localized Swagger metadata sets into static Portal API projections.'


                        task.dependsOn(
                                cleanupLegacyPortalData
                        )


                        task.inputs.files(
                                portalDataInputs
                        )


                        task.doLast {

                            apiService.generate(
                                    project
                            )
                        }
                }


        def generatePortalQuiz =
                project.tasks.register(
                        'generatePortalQuiz'
                ) {
                    task ->

                        task.group =
                                'learning portal'


                        task.description =
                                'Copy localized Quiz question.yml files into static Portal Quiz projections.'


                        task.dependsOn(
                                cleanupLegacyPortalData
                        )


                        task.inputs.files(
                                portalDataInputs
                        )


                        task.doLast {

                            quizService.generate(
                                    project
                            )
                        }
                }


        def generatePortalInterview =
                project.tasks.register(
                        'generatePortalInterview'
                ) {
                    task ->

                        task.group =
                                'learning portal'


                        task.description =
                                'Copy localized Interview question.yml files into static Portal Interview projections.'


                        task.dependsOn(
                                cleanupLegacyPortalData
                        )


                        task.inputs.files(
                                portalDataInputs
                        )


                        task.doLast {

                            interviewService.generate(
                                    project
                            )
                        }
                }


        generatePortalKnowledge.configure {
            task ->

                task.mustRunAfter(
                        generatePortalModuleData
                )
        }


        generatePortalApi.configure {
            task ->

                task.mustRunAfter(
                        generatePortalModuleData
                )
        }


        generatePortalQuiz.configure {
            task ->

                task.mustRunAfter(
                        generatePortalModuleData
                )
        }


        generatePortalInterview.configure {
            task ->

                task.mustRunAfter(
                        generatePortalModuleData
                )
        }


        project.tasks.register(
                'generatePortalData'
        ) {
            task ->

                task.group =
                        'learning portal'


                task.description =
                        'Generate all static Portal data projections.'


                task.dependsOn(
                        generatePortalModuleData,
                        generatePortalKnowledge,
                        generatePortalApi,
                        generatePortalQuiz,
                        generatePortalInterview
                )
        }


        // ====================================================
        // Generate after every module has been evaluated
        // ====================================================

        project.gradle.projectsEvaluated {

            service.generate(
                    project
            )
        }
    }
}
