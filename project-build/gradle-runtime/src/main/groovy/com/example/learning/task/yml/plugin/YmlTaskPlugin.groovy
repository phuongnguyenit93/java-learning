package com.example.learning.task.yml.plugin

import com.example.learning.task.yml.service.YamlDependencyResolverService
import com.example.learning.task.yml.task.CombineYamlTask
import com.example.learning.utils.GradleBuildUtils
import com.example.learning.utils.ModuleProjectUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
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

        Provider<List<String>> dependencyServiceNames =
                project.provider {

                    dependencyResolver.resolve(
                            project,
                            serviceName
                    )
                }


        // ====================================================
        // Current module composition source
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
        // Dependency application-module.yml sources
        //
        // Không dùng Set<Project>.
        //
        // Phải giữ nguyên order từ resolver vì order
        // quyết định YAML precedence.
        // ====================================================

        Provider<List<File>> dependencyApplicationFiles =
                dependencyServiceNames.map {
                    List<String> dependencyNames ->

                        dependencyNames.collect { String dependencyServiceName ->

                            Project dependencyProject =
                                    ModuleProjectUtils
                                            .findByServiceName(
                                                    project,
                                                    dependencyServiceName
                                            )


                            File dependencyResourceDirectory =
                                    GradleBuildUtils.findBaseDirectory(
                                            dependencyProject
                                    )


                            return new File(
                                    dependencyResourceDirectory,
                                    'application-module.yml'
                            )
                        }
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

                    task.baseApplicationModulePath.set(
                            baseApplicationModuleFile.absolutePath
                    )


                    // ------------------------------------------
                    // Dependency order
                    // ------------------------------------------

                    task.dependencyApplicationPaths.set(
                            dependencyApplicationFiles.map {
                                List<File> sourceFiles ->

                                    sourceFiles.collect {
                                        File sourceFile ->

                                            sourceFile.absolutePath
                                    }
                            }
                    )


                    // ------------------------------------------
                    // YAML source contents
                    // ------------------------------------------

                    task.applicationModuleFiles.from(
                            project.provider {

                                [baseApplicationModuleFile] +
                                        dependencyApplicationFiles.get()
                            }
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
