package com.example.learning.task.yml.service

import com.example.learning.utils.ProjectPropertyUtils
import com.example.learning.utils.ModuleProjectUtils
import org.gradle.api.Project

class YamlDependencyResolverService {

    /**
     * Resolve toàn bộ BUILD_YML_MODULE_DEPEND
     * theo dạng recursive.
     *
     * Ví dụ:
     *
     * BANK
     * ├── DATABASE
     * │   └── HIKARI
     * └── KAFKA
     *     └── KAFKA_COMMON
     *
     * Kết quả:
     *
     * DATABASE
     * HIKARI
     * KAFKA
     * KAFKA_COMMON
     *
     * LinkedHashSet đảm bảo:
     *
     * - không duplicate
     * - giữ nguyên thứ tự
     * - tránh circular dependency
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


        LinkedHashSet<String> resolved =
                new LinkedHashSet<>()

        LinkedHashSet<String> visited =
                new LinkedHashSet<>()


        /*
         * Root được đánh dấu trước.
         *
         * Ví dụ:
         *
         * A -> B
         * B -> A
         *
         * thì A không bị add ngược trở lại
         * dependency list.
         */
        visited.add(
                rootServiceName
        )


        collectDependencies(
                project,
                rootServiceName,
                visited,
                resolved
        )


        return resolved.toList()
    }


    private void collectDependencies(
            Project project,
            String currentService,
            Set<String> visited,
            Set<String> resolved
    ) {

        Project currentProject =
                ModuleProjectUtils.findByServiceName(
                        project,
                        currentService
                )


        if (currentProject == null) {
            return
        }


        List<String> dependencies =
                getDirectDependencies(
                        currentProject
                )


        dependencies.each { String dependency ->

            /*
             * false nghĩa là đã từng gặp.
             *
             * Có thể do:
             *
             * - duplicate
             * - dependency graph hội tụ
             * - circular dependency
             */
            if (!visited.add(dependency)) {
                return
            }


            /*
             * Add trước rồi mới recursive.
             *
             * Giữ đúng behavior combineYaml cũ:
             *
             * A -> B -> C
             *
             * merge order:
             *
             * A(base)
             * B
             * C
             *
             * => C có precedence cao hơn B.
             */
            resolved.add(
                    dependency
            )


            collectDependencies(
                    project,
                    dependency,
                    visited,
                    resolved
            )
        }
    }


    private static List<String> getDirectDependencies(
            Project project
    ) {

        return ProjectPropertyUtils.getCsvList(
                project,
                'BUILD_YML_MODULE_DEPEND'
        )
    }
}