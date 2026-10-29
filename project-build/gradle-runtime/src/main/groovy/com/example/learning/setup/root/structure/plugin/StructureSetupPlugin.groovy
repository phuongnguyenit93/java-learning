package com.example.learning.setup.root.structure.plugin

import com.example.learning.setup.root.structure.service.ProjectStructureService
import com.example.learning.setup.root.structure.service.PortalApiProjectionService
import com.example.learning.setup.root.structure.service.PortalInterviewProjectionService
import com.example.learning.setup.root.structure.service.PortalCaseStudyProjectionService
import com.example.learning.setup.root.structure.service.PortalKnowledgeProjectionService
import com.example.learning.setup.root.structure.service.PortalKnowledgeSearchProjectionService
import com.example.learning.setup.root.structure.service.PortalQuizProjectionService
import com.example.learning.setup.root.structure.service.PortalReferenceProjectionService
import com.example.learning.setup.root.structure.service.PortalSummaryProjectionService
import com.example.learning.setup.root.structure.service.PortalRoadmapProjectionService
import com.example.learning.setup.root.structure.service.PortalVideoProjectionService
import com.example.learning.task.codeexplorer.service.CodeExplorerMaterialService
import com.example.learning.setup.root.structure.task.GenerateModuleOrderTask
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


        PortalKnowledgeSearchProjectionService knowledgeSearchService =
                new PortalKnowledgeSearchProjectionService(
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
        PortalCaseStudyProjectionService caseStudyService =
                new PortalCaseStudyProjectionService(project.logger)


        PortalRoadmapProjectionService roadmapService =
                new PortalRoadmapProjectionService(
                        project.logger
                )


        PortalReferenceProjectionService referenceService =
                new PortalReferenceProjectionService(
                        project.logger
                )

        PortalSummaryProjectionService summaryService =
                new PortalSummaryProjectionService(project.logger)


        PortalVideoProjectionService videoService =
                new PortalVideoProjectionService(
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
                    include '**/module-order.yml'
                    include '**/content/**'
                    include '**/src/main/resources/swagger/**'

                    exclude '**/build/**'
                    exclude '**/.gradle/**'
                    exclude '**/out/**'
                    exclude '**/target/**'
                }


        project.tasks.register(
                'generateModuleOrder',
                GenerateModuleOrderTask
        ) { task ->

            task.group =
                    'build setup'


            task.description =
                    'Create or synchronize optional module-order.yml for direct children. Use --path=<parent-path-relative-to-module>.'


            task.rootDirectory.set(
                    project.layout.projectDirectory
            )


            task.targetPath.convention(
                    ''
            )
        }


        def cleanupLegacyPortalData =
                project.tasks.register(
                        'cleanupLegacyPortalData'
                ) {
                    task ->

                        task.group =
                                'learning portal'


            task.description =
                    'Remove obsolete generated-data layouts from the Java learning data output.'


                        task.doLast {

                            project.delete(
                                    new File(
                                            project.projectDir,
                                            'build/learning-data/java/data'
                                    ),
                                    new File(
                                            project.projectDir,
                                            'build/learning-data/java/overview'
                                    ),
                                    new File(
                                            project.projectDir,
                                            'build/learning-data/java/knowledge'
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


        def generatePortalKnowledgeSearch =
                project.tasks.register(
                        'generatePortalKnowledgeSearch'
                ) {
                    task ->

                        task.group =
                                'learning portal'


                        task.description =
                                'Generate localized cross-module Knowledge search indexes from Portal Knowledge projections.'


                        task.dependsOn(
                                generatePortalKnowledge
                        )


                        task.inputs.files(
                                portalDataInputs
                        )


                        task.doLast {

                            knowledgeSearchService.generate(
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

        def generatePortalCaseStudy = project.tasks.register('generatePortalCaseStudy') { task ->
            task.group = 'learning portal'
            task.description = 'Publish module-wide Case Study groups.'
            task.dependsOn(cleanupLegacyPortalData)
            task.inputs.files(portalDataInputs)
            task.doLast { caseStudyService.generate(project) }
        }


        def generatePortalRoadmap =
                project.tasks.register(
                        'generatePortalRoadmap'
                ) {
                    task ->

                        task.group =
                                'learning portal'


                        task.description =
                                'Copy localized Roadmap roadmap.yml files into static Portal Roadmap projections.'


                        task.dependsOn(
                                cleanupLegacyPortalData
                        )


                        task.inputs.files(
                                portalDataInputs
                        )


                        task.doLast {

                            roadmapService.generate(
                                    project
                            )
                        }
                }


        def generatePortalVideo =
                project.tasks.register(
                        'generatePortalVideo'
                ) {
                    task ->

                        task.group =
                                'learning portal'


                        task.description =
                                'Generate localized Video indexes and script projections for the Learning Portal.'


                        task.dependsOn(
                                cleanupLegacyPortalData
                        )


                        task.inputs.files(
                                portalDataInputs
                        )


                        task.doLast {

                            videoService.generate(
                                    project
                            )
                        }
                }


        def generatePortalReference =
                project.tasks.register(
                        'generatePortalReference'
                ) {
                    task ->

                        task.group =
                                'learning portal'


                        task.description =
                                'Copy authored shared module Reference files into static Portal Reference projections.'


                        task.dependsOn(
                                cleanupLegacyPortalData
                        )


                        task.inputs.files(
                                portalDataInputs
                        )


                        task.doLast {

                            referenceService.generate(
                                    project
                            )
                        }
                }


        def generatePortalSummary = project.tasks.register('generatePortalSummary') { task ->
            task.group = 'learning portal'
            task.description = 'Build read-only VI/EN chapter Summary JSON from summary/ files.'
            task.dependsOn(cleanupLegacyPortalData)
            task.inputs.files(portalDataInputs)
            task.doLast { summaryService.generate(project) }
        }

        generatePortalKnowledge.configure {
            task ->

                task.mustRunAfter(
                        generatePortalModuleData
                )
        }


        generatePortalKnowledgeSearch.configure {
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


        generatePortalRoadmap.configure {
            task ->

                task.mustRunAfter(
                        generatePortalModuleData
                )
        }


        generatePortalVideo.configure {
            task ->

                task.mustRunAfter(
                        generatePortalModuleData
                )
        }


        generatePortalReference.configure {
            task ->

                task.mustRunAfter(
                        generatePortalModuleData
                )
        }

        generatePortalSummary.configure { task ->
            task.mustRunAfter(generatePortalModuleData)
        }

        def generatePortalCodeExplorer =
                project.tasks.register('generatePortalCodeExplorer') { task ->
                    task.group = 'learning portal'
                    task.description = 'Generate and publish static Code Explorer bundles for opted-in modules.'
                    task.dependsOn(generatePortalModuleData)
                    task.doLast {
                        CodeExplorerMaterialService generator = new CodeExplorerMaterialService()
                        def configs = project.fileTree(new File(project.projectDir, 'module')) {
                            include '**/content/code-explorer/material.yml'
                            exclude '**/build/**'
                        }.files.toList().sort { it.absolutePath }
                        configs.each { File config ->
                            File moduleDirectory = config.parentFile.parentFile.parentFile
                            Map result = generator.execute(project.projectDir, moduleDirectory, true, true)
                            project.logger.lifecycle('[PORTAL-CODE-EXPLORER] {}: {} Java files, {} API entries',
                                    result.module, result.files, result.apis)
                        }
                    }
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
                        // Temporarily exclude the oversized cross-module search index.
                        // Keep the standalone task available for future restoration.
                        generatePortalApi,
                        generatePortalQuiz,
                        generatePortalInterview,
                        generatePortalCaseStudy,
                        generatePortalRoadmap,
                        generatePortalVideo,
                        generatePortalReference,
                        generatePortalSummary,
                        generatePortalCodeExplorer
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
