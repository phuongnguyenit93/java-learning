package com.example.learning.module.yml.service

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.language.jvm.tasks.ProcessResources


class YmlResourceConfigurationService {

    private final Logger logger


    YmlResourceConfigurationService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    void configure(
            Project project
    ) {

        /*
         * ModuleSetupPlugin hiện apply java-library cho mọi real module.
         *
         * withPlugin vẫn được dùng để service an toàn nếu YmlSetupPlugin
         * được apply trực tiếp ở nơi khác hoặc thứ tự plugin thay đổi.
         */
        project.pluginManager.withPlugin(
                'java'
        ) {

            project.tasks
                    .named(
                            'processResources',
                            ProcessResources
                    )
                    .configure {
                        ProcessResources task ->

                            configureResourcePolicy(
                                    task
                            )
                    }
        }
    }


    private void configureResourcePolicy(
            ProcessResources task
    ) {

        // ====================================================
        // Application profile files
        // ====================================================

        /*
         * application.yml được giữ nguyên.
         *
         * Chỉ loại các file dạng:
         *
         * application-dev.yml
         * application-loc.yml
         * application-prd.yml
         * application-merged.yml
         */
        task.exclude(
                '**/application-*.yml'
        )


        // ====================================================
        // Properties
        // ====================================================

        task.exclude(
                '**/*.properties'
        )


        logger.info(
                '[YML-SETUP] Resource policy configured for {}',
                task.project.path
        )
    }
}