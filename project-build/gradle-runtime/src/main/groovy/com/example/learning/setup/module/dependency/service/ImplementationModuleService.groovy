package com.example.learning.setup.module.dependency.service

import com.example.learning.utils.ModuleProjectUtils
import com.example.learning.utils.ProjectDependencyUtils
import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.logging.Logger


class ImplementationModuleService {

    /*
     * Những configuration của dependency module
     * cần propagate sang consumer.
     *
     * annotationProcessor:
     * - processor phải chạy tại consumer compile.
     *
     * developmentOnly:
     * - dependency như Spring Boot DevTools phải giữ đúng
     *   development-only semantics tại consumer, không bị biến thành
     *   implementation/runtime dependency chỉ vì đi qua module wrapper.
     */
    private static final List<String> PROPAGATED_CONFIGURATIONS =
            [
                    'annotationProcessor',
                    'developmentOnly'
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


                /*
                 * Configuration-on-demand có thể để source project ở trạng
                 * thái chưa evaluate dù target đang được configure.
                 *
                 * Khi đó source configuration chưa chứa dependency khai báo
                 * trong build.gradle. module-depend.json đã có contract riêng
                 * để preserve metadata của module chưa evaluate, nên dùng nó
                 * làm fallback thay vì phụ thuộc vào project evaluation order.
                 */
                if (
                        copiedDependencies.isEmpty() &&
                                !sourceProject.state.executed
                ) {

                    copiedDependencies =
                            copyCatalogDependencies(
                                    targetProject,
                                    serviceName,
                                    configurationName
                            )
                }


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


    private List<String> copyCatalogDependencies(
            Project targetProject,
            String serviceName,
            String configurationName
    ) {

        File catalogFile =
                new File(
                        targetProject.rootProject.projectDir,
                        'project-build/gradle-runtime/src/main/resources/module/module-depend.json'
                )


        if (!catalogFile.isFile()) {
            return []
        }


        try {

            Object parsed =
                    new JsonSlurper().parse(
                            catalogFile
                    )


            if (!(parsed instanceof Map)) {
                return []
            }


            Object moduleInfo =
                    ((Map) parsed)[serviceName]


            if (!(moduleInfo instanceof Map)) {
                return []
            }


            Object configuredDependencies =
                    ((Map) moduleInfo)[configurationName]


            if (!(configuredDependencies instanceof Collection)) {
                return []
            }


            def targetConfiguration =
                    targetProject.configurations.findByName(
                            configurationName
                    )


            if (targetConfiguration == null) {
                return []
            }


            List<String> copied =
                    []


            ((Collection) configuredDependencies).each {
                Object dependencyNotation ->

                    String notation =
                            dependencyNotation
                                    ?.toString()
                                    ?.trim()


                    if (notation == null || notation.isBlank()) {
                        return
                    }


                    List<String> parts =
                            notation.split(':') as List<String>


                    if (parts.size() < 2) {
                        return
                    }


                    boolean alreadyExists =
                            targetConfiguration
                                    .dependencies
                                    .any {
                                        dependency ->

                                            dependency.group == parts[0] &&
                                                    dependency.name == parts[1]
                                    }


                    if (alreadyExists) {
                        return
                    }


                    ProjectDependencyUtils.addExternal(
                            targetProject,
                            configurationName,
                            notation
                    )


                    copied << notation
            }


            return copied
        }
        catch (Exception exception) {

            logger.warn(
                    '[MODULE-DEPENDENCY] Unable to use catalog fallback for {} / {}: {}',
                    serviceName,
                    configurationName,
                    exception.message
            )


            return []
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
