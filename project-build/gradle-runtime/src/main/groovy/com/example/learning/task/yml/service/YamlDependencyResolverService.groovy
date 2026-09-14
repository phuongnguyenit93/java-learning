package com.example.learning.task.yml.service

import com.example.learning.utils.ProjectPropertyUtils
import com.example.learning.utils.ModuleProjectUtils
import org.gradle.api.GradleException
import org.gradle.api.Project

class YamlDependencyResolverService {

    private final YamlCapabilityDependencyService capabilityDependencyService =
            new YamlCapabilityDependencyService()

    /**
     * Resolve toàn bộ YAML dependencies theo dạng recursive.
     *
     * Mỗi module có 2 nguồn dependency:
     *
     * 1. Explicit:
     *    BUILD_YML_MODULE_DEPEND
     *
     * 2. Capability-derived:
     *    ví dụ BUILD_SWAGGER=TRUE -> GLOBAL_SWAGGER_CONFIG
     *
     * Ví dụ:
     *
     * BANK
     * ├── DATABASE
     * │   └── HIKARI
     * └── KAFKA
     *     └── KAFKA_COMMON
     *
     * Kết quả theo post-order để merge dependency sâu trước:
     *
     * HIKARI
     * DATABASE
     * KAFKA_COMMON
     * KAFKA
     *
     * Rule:
     *
     * - không duplicate
     * - dependency sâu hơn có precedence thấp hơn
     * - dependency gần root được merge sau dependency sâu
     * - circular dependency -> fail
     * - dependency không resolve được -> fail
     */
    List<String> resolve(
            Project project,
            String rootServiceName
    ) {

        if (
                rootServiceName == null ||
                        rootServiceName.isBlank()
        ) {

            return []
        }


        Project rootProject


        try {

            rootProject =
                    ModuleProjectUtils.findByServiceName(
                            project,
                            rootServiceName
                    )

        } catch (GradleException exception) {

            throw new GradleException(
                    "[YAML-DEPENDENCY] Service '${rootServiceName}' could not be resolved to a Gradle project.",
                    exception
            )
        }


        LinkedHashSet<String> resolved =
                new LinkedHashSet<>()

        Set<String> visited =
                new LinkedHashSet<>()

        List<String> path =
                [rootServiceName]


        collectDependencies(
                project,
                rootProject,
                visited,
                resolved,
                path
        )


        return resolved.toList()
    }


    private void collectDependencies(
            Project project,
            Project currentProject,
            Set<String> visited,
            Set<String> resolved,
            List<String> path
    ) {


        List<String> dependencies =
                getDirectDependencies(
                        currentProject
                )


        dependencies.each { String dependency ->

            int cycleStart =
                    path.indexOf(
                            dependency
                    )


            if (cycleStart >= 0) {

                List<String> cycle =
                        new ArrayList<>(
                                path.subList(
                                        cycleStart,
                                        path.size()
                                )
                        )

                cycle.add(
                        dependency
                )


                throw new GradleException(
                        "[YAML-DEPENDENCY] Circular dependency detected: ${cycle.join(' -> ')}"
                )
            }


            if (visited.contains(dependency)) {
                return
            }


            Project dependencyProject

            String currentService =
                    currentProject.findProperty(
                            'SERVICE_NAME'
                    )
                            ?.toString()
                            ?.trim() ?: currentProject.path


            try {

                dependencyProject =
                        ModuleProjectUtils.findByServiceName(
                                project,
                                dependency
                        )

            } catch (GradleException exception) {

                throw new GradleException(
                        "[YAML-DEPENDENCY] '${currentService}' depends on unknown service '${dependency}'.",
                        exception
                )
            }


            path.add(
                    dependency
            )


            collectDependencies(
                    project,
                    dependencyProject,
                    visited,
                    resolved,
                    path
            )


            path.remove(
                    path.size() - 1
            )


            visited.add(
                    dependency
            )


            resolved.add(
                    dependency
            )
        }
    }


    private List<String> getDirectDependencies(
            Project project
    ) {

        List<String> explicitDependencies =
                ProjectPropertyUtils.getCsvList(
                        project,
                        'BUILD_YML_MODULE_DEPEND'
                )


        List<String> capabilityDependencies =
                capabilityDependencyService.resolve(
                        project
                )


        /*
         * Explicit dependency được giữ trước capability dependency.
         *
         * Linked order rất quan trọng vì resolver dùng post-order và
         * merge order quyết định precedence.
         *
         * unique() xử lý cả trường hợp user đã khai báo explicit một
         * module mà capability cũng tự động thêm module đó.
         */
        return (
                explicitDependencies +
                        capabilityDependencies
        ).unique()
    }
}
