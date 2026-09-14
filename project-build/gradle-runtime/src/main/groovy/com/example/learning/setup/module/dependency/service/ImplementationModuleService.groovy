package com.example.learning.setup.module.dependency.service

import com.example.learning.utils.ModuleProjectUtils
import com.example.learning.utils.ProjectDependencyUtils
import org.gradle.api.Project
import org.gradle.api.logging.Logger


class ImplementationModuleService {

    /*
     * Những configuration của dependency module
     * cần propagate sang consumer.
     *
     * Sau này nếu cần có thể thêm:
     *
     * compileOnly
     * developmentOnly
     * ...
     */
    private static final List<String> PROPAGATED_CONFIGURATIONS =
            [
                    'annotationProcessor'
            ]


    private final Logger logger


    ImplementationModuleService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    void configure(
            Project project,
            String serviceList
    ) {

        List<String> serviceNames =
                parseServiceList(
                        serviceList
                )


        serviceNames.each {
            String serviceName ->

                configureModuleDependency(
                        project,
                        serviceName
                )
        }
    }


    // ========================================================
    // Module dependency
    // ========================================================

    private void configureModuleDependency(
            Project targetProject,
            String serviceName
    ) {

        Project sourceProject =
                ModuleProjectUtils.findByServiceName(
                        targetProject,
                        serviceName
                )


        // ====================================================
        // implementation project(...)
        // ====================================================

        ProjectDependencyUtils.addProject(
                targetProject,
                'implementation',
                sourceProject
        )


        logger.lifecycle(
                '🔗 [MODULE-DEPENDENCY] [{}] implementation -> {}',
                targetProject.path,
                sourceProject.path
        )


        // ====================================================
        // Propagate special configurations
        // ====================================================

        PROPAGATED_CONFIGURATIONS.each {
            String configurationName ->

                List<String> copiedDependencies =
                        ProjectDependencyUtils.copyExternalDependencies(
                                sourceProject,
                                configurationName,
                                targetProject,
                                configurationName
                        )


                copiedDependencies.each {
                    String dependency ->

                        logger.lifecycle(
                                '   ↳ {} -> {}',
                                configurationName,
                                dependency
                        )
                }
        }
    }


    // ========================================================
    // Service list
    // ========================================================

    private static List<String> parseServiceList(
            String serviceList
    ) {

        if (
                serviceList == null ||
                        serviceList.isBlank()
        ) {

            return []
        }


        return serviceList
                .split(',')
                .collect {
                    String serviceName ->

                        serviceName.trim()
                }
                .findAll {
                    String serviceName ->

                        !serviceName.isBlank()
                }
                .unique()
    }
}
