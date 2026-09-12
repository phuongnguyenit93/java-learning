package com.example.learning.utils

import org.gradle.api.GradleException
import org.gradle.api.Project


final class ModuleProjectUtils {

    private ModuleProjectUtils() {
    }


    /**
     * Lấy toàn bộ real module.
     *
     * Vẫn check gradle.properties để loại những Project
     * trung gian Gradle có thể tạo do project hierarchy.
     */
    static Set<Project> getModules(
            Project currentProject
    ) {

        List<Project> modules =
                currentProject
                        .rootProject
                        .subprojects
                        .findAll {
                            Project project ->

                                isModule(
                                        project
                                )
                        }
                        .sort {
                            Project first,
                            Project second ->

                                first.path <=> second.path
                        }


        return new LinkedHashSet<Project>(
                modules
        )
    }


    /**
     * Replacement cho:
     *
     * searchProjectByServiceName(...)
     */
    static Project findByServiceName(
            Project project,
            String serviceName
    ) {

        String normalizedServiceName =
                serviceName
                        ?.trim()


        if (
                normalizedServiceName == null ||
                        normalizedServiceName.isBlank()
        ) {

            throw new GradleException(
                    'SERVICE_NAME must not be blank.'
            )
        }


        Project result =
                getModules(
                        project
                ).find {
                    Project candidate ->

                        ProjectPropertyUtils.getString(
                                candidate,
                                'SERVICE_NAME'
                        ) ==
                                normalizedServiceName
                }


        if (result == null) {

            throw new GradleException(
                    """
Unable to find module by SERVICE_NAME.

SERVICE_NAME:
${normalizedServiceName}
""".stripIndent()
            )
        }


        return result
    }


    static Project findByKeyValue(
            Project project,
            String key,
            String value
    ) {

        String normalizedKey =
                key
                        ?.trim()


        String normalizedValue =
                value
                        ?.trim()


        if (
                normalizedKey == null ||
                        normalizedKey.isBlank()
        ) {

            throw new GradleException(
                    'Property key must not be blank.'
            )
        }


        if (
                normalizedValue == null ||
                        normalizedValue.isBlank()
        ) {

            throw new GradleException(
                    'Property value must not be blank.'
            )
        }


        Project result =
                getModules(
                        project
                ).find {
                    Project candidate ->

                        ProjectPropertyUtils.getString(
                                candidate,
                                normalizedKey
                        ) ==
                                normalizedValue
                }


        if (result == null) {

            throw new GradleException(
                    """
Unable to find module by property.

Property:
${normalizedKey}

Value:
${normalizedValue}
""".stripIndent()
            )
        }


        return result
    }


    static List<Project> findByServiceNameList(
            Project project,
            Collection<String> serviceNames
    ) {

        if (
                serviceNames == null ||
                        serviceNames.isEmpty()
        ) {

            return []
        }


        return serviceNames
                .collect {
                    String serviceName ->

                        findByServiceName(
                                project,
                                serviceName
                        )
                }
    }

    static boolean isModule(
            Project project
    ) {

        return new File(
                project.projectDir,
                'gradle.properties'
        ).isFile()
    }
}