package com.example.learning.utils

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency


class ProjectDependencyUtils {

    static void addExternal(
            Project project,
            String configurationName,
            Object notation
    ) {

        project.dependencies.add(
                configurationName,
                notation
        )
    }


    static void addProject(
            Project project,
            String configurationName,
            Project dependencyProject
    ) {

        project.dependencies.add(
                configurationName,
                project.dependencies.project(
                        path: dependencyProject.path
                )
        )
    }


    static List<String> copyExternalDependencies(
            Project sourceProject,
            String sourceConfigurationName,
            Project targetProject,
            String targetConfigurationName = sourceConfigurationName
    ) {

        Configuration sourceConfiguration =
                sourceProject
                        .configurations
                        .findByName(
                                sourceConfigurationName
                        )


        if (sourceConfiguration == null) {
            return []
        }


        Configuration targetConfiguration =
                targetProject
                        .configurations
                        .findByName(
                                targetConfigurationName
                        )


        if (targetConfiguration == null) {
            return []
        }


        List<String> copied =
                []


        sourceConfiguration
                .dependencies
                .each {
                    dependency ->

                        /*
                         * Chỉ propagate external module dependency.
                         *
                         * Không convert ProjectDependency thành
                         * group:name:version.
                         */
                        if (
                                dependency instanceof ProjectDependency ||
                                        !(dependency instanceof ExternalModuleDependency)
                        ) {

                            return
                        }


                        String dependencyKey =
                                buildDependencyKey(
                                        dependency
                                )


                        boolean alreadyExists =
                                targetConfiguration
                                        .dependencies
                                        .any {
                                            existing ->

                                                existing instanceof ExternalModuleDependency &&
                                                        buildDependencyKey(
                                                                existing
                                                        ) == dependencyKey
                                        }


                        if (alreadyExists) {
                            return
                        }


                        /*
                         * copy() tốt hơn tự dựng lại group:name:version:
                         *
                         * giữ được các metadata của dependency
                         * như excludes/transitive/artifacts...
                         */
                        addExternal(
                                targetProject,
                                targetConfigurationName,
                                dependency.copy()
                        )


                        copied <<
                                dependencyKey
                }


        return copied
    }


    private static String buildDependencyKey(
            dependency
    ) {

        String key =
                "${dependency.group}:${dependency.name}"


        if (
                dependency.version != null &&
                        !dependency.version.isBlank()
        ) {

            key +=
                    ":${dependency.version}"
        }


        return key
    }
}