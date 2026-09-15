package com.example.learning.task.yml.service

import com.example.learning.generated.settings.ModuleListEnum
import com.example.learning.setup.module.config.model.ModuleType
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.Project


class YamlCapabilityDependencyService {

    /**
     * MODULE_TYPE -> YAML composition module(s).
     *
     * Đây là dependency mặc định theo web stack của application.
     * LIBRARY/PLATFORM không có bonus YAML dependency theo type.
     */
    private static final Map<ModuleType, List<ModuleListEnum>> MODULE_TYPE_DEPENDENCIES =
            [
                    (ModuleType.SERVLET): [
                            ModuleListEnum.SPRING_WEB
                    ],
                    (ModuleType.REACTIVE): [
                            ModuleListEnum.SPRING_REACTIVE
                    ]
            ].asImmutable()


    /**
     * Capability -> YAML composition module(s).
     *
     * Đây là nơi duy nhất cần mở rộng khi một BUILD_* capability
     * mới cần tự động kéo thêm application-module.yml của module khác.
     */
    private static final Map<String, List<ModuleListEnum>> CAPABILITY_DEPENDENCIES =
            [
                    BUILD_SWAGGER: [
                            ModuleListEnum.GLOBAL_SWAGGER_CONFIG
                    ]
            ].asImmutable()


    List<String> resolve(
            Project project
    ) {

        List<String> dependencies =
                []


        ModuleType moduleType =
                ModuleType.resolve(
                        project.findProperty(
                                'MODULE_TYPE'
                        )
                )


        MODULE_TYPE_DEPENDENCIES
                .getOrDefault(
                        moduleType,
                        []
                )
                .each {
                    ModuleListEnum module ->

                        dependencies.add(
                                module.name()
                        )
                }


        CAPABILITY_DEPENDENCIES.each {
            String propertyName,
            List<ModuleListEnum> modules ->

                if (
                        !ProjectPropertyUtils.isEnabled(
                                project,
                                propertyName
                        )
                ) {

                    return
                }


                modules.each {
                    ModuleListEnum module ->

                        dependencies.add(
                                module.name()
                        )
                }
        }


        return dependencies.unique()
    }
}
