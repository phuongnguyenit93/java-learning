package com.example.learning.task.yml.plugin

import com.example.learning.task.yml.service.YamlDependencyResolverService
import com.example.learning.task.yml.task.CombineYamlTask
import com.example.learning.utils.GradleBuildUtils
import com.example.learning.utils.ModuleProjectUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

class YmlTaskPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        // ====================================================
        // Services
        // ====================================================

        YamlDependencyResolverService dependencyResolver =
                new YamlDependencyResolverService()


        // ====================================================
        // Current module
        // ====================================================

        String serviceName =
                project.findProperty(
                        'SERVICE_NAME'
                )
                        ?.toString()
                        ?.trim()
                        ?: ''


        // ====================================================
        // Recursive dependencies
        // ====================================================

        List<String> dependencyServiceNames =
                dependencyResolver.resolve(
                        project,
                        serviceName
                )


        // ====================================================
        // Current module application-module.yml
        // ====================================================

        File baseResourceDirectory =
                GradleBuildUtils.findBaseDirectory(
                        project
                )


        File baseApplicationModuleFile =
                new File(
                        baseResourceDirectory,
                        'application-module.yml'
                )


        // ====================================================
        // Dependency application-module.yml
        //
        // Không dùng Set<Project>.
        //
        // Phải giữ nguyên order từ resolver vì order
        // quyết định YAML precedence.
        // ====================================================

        List<File> dependencyApplicationFiles =
                dependencyServiceNames
                        .collect { String dependencyServiceName ->

                            Project dependencyProject =
                                    ModuleProjectUtils
                                            .findByServiceName(
                                                    project,
                                                    dependencyServiceName
                                            )


                            File dependencyResourceDirectory =
                                    GradleBuildUtils
                                            .findBaseDirectory(
                                                    dependencyProject
                                            )


                            return new File(
                                    dependencyResourceDirectory,
                                    'application-module.yml'
                            )
                        }


        // ====================================================
        // combineYaml
        // ====================================================

        TaskProvider<CombineYamlTask> combineYaml =
                project.tasks.register(
                        'combineYaml',
                        CombineYamlTask
                ) { task ->

                    task.group =
                            'yml'


                    task.description =
                            'Merge application-module.yml files into application-merged.yml.'


                    // ------------------------------------------
                    // Metadata
                    // ------------------------------------------

                    task.serviceName.set(
                            serviceName
                    )


                    task.dependencyServiceNames.set(
                            dependencyServiceNames
                    )


                    // ------------------------------------------
                    // Base application-module.yml
                    // ------------------------------------------

                    task.baseApplicationModuleFile.set(
                            baseApplicationModuleFile
                    )


                    // ------------------------------------------
                    // Dependency order
                    // ------------------------------------------

                    task.dependencyApplicationPaths.set(
                            dependencyApplicationFiles.collect {
                                File sourceFile ->

                                    sourceFile.absolutePath
                            }
                    )


                    // ------------------------------------------
                    // Dependency file contents
                    // ------------------------------------------

                    task.dependencyApplicationFiles.from(
                            dependencyApplicationFiles
                    )


                    // ------------------------------------------
                    // Output
                    // ------------------------------------------

                    task.mergedApplicationFile.set(
                            new File(
                                    baseResourceDirectory,
                                    'application-merged.yml'
                            )
                    )
                }
    }
}