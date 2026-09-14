package com.example.learning.task.yml.service

import com.example.learning.generated.settings.ModuleListEnum
import com.example.learning.setup.module.config.model.ModuleType
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.Project


class YamlCapabilityDependencyService {

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


        switch (moduleType) {

            case ModuleType.SERVLET:

                dependencies.add(
                        ModuleListEnum.SPRING_WEB.name()
                )

                break


            case ModuleType.REACTIVE:

                dependencies.add(
                        ModuleListEnum.SPRING_REACTIVE.name()
                )

                break
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
