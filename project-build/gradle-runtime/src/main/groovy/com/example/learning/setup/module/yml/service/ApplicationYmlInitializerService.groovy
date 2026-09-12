package com.example.learning.module.yml.service

import com.example.learning.utils.ProjectPropertyUtils
import com.example.learning.utils.GradleBuildUtils
import org.gradle.api.Project
import org.gradle.api.logging.Logger


class ApplicationYmlInitializerService {

    private final Logger logger


    ApplicationYmlInitializerService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    void initialize(
            Project project
    ) {

        // ====================================================
        // BUILD_YML
        // ====================================================

        /*
         * Defensive check.
         *
         * Bình thường ProjectOrchestrationPlugin đã check
         * trước khi apply ApplicationYamlSetupPlugin.
         */
        if (
                !ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_YML'
                )
        ) {

            logger.info(
                    '[APPLICATION-YAML] Skip {} because BUILD_YML != TRUE.',
                    project.path
            )

            return
        }


        // ====================================================
        // Resolve target directory
        // ====================================================

        File baseDirectory =
                GradleBuildUtils.findBaseDirectory(
                        project
                )


        File applicationFile =
                new File(
                        baseDirectory,
                        'application.yml'
                )


        // ====================================================
        // Existing human-owned file
        // ====================================================

        /*
         * application.yml sau khi đã có nội dung
         * được coi là human-owned.
         *
         * Tuyệt đối không overwrite.
         */
        if (
                applicationFile.isFile() &&
                        !applicationFile
                                .getText(
                                        'UTF-8'
                                )
                                .trim()
                                .isEmpty()
        ) {

            logger.info(
                    '[APPLICATION-YAML] UP-TO-DATE: {}',
                    applicationFile.absolutePath
            )

            return
        }


        // ====================================================
        // Profiles
        // ====================================================

        List<String> profiles =
                resolveProfiles(
                        project
                )


        if (profiles.isEmpty()) {

            logger.warn(
                    '[APPLICATION-YAML] No Gradle profiles found for {}. Skip initialization.',
                    project.path
            )

            return
        }


        // ====================================================
        // Render
        // ====================================================

        String content =
                render(
                        profiles
                )


        // ====================================================
        // Ensure parent directory
        // ====================================================

        if (
                applicationFile.parentFile != null &&
                        !applicationFile.parentFile.exists()
        ) {

            boolean created =
                    applicationFile
                            .parentFile
                            .mkdirs()


            if (
                    !created &&
                            !applicationFile.parentFile.exists()
            ) {

                logger.warn(
                        '[APPLICATION-YAML] Unable to create directory: {}',
                        applicationFile.parentFile.absolutePath
                )

                return
            }
        }


        // ====================================================
        // Write
        // ====================================================

        applicationFile.setText(
                content,
                'UTF-8'
        )


        logger.lifecycle(
                '✨ [APPLICATION-YAML] Initialized: {}',
                applicationFile.absolutePath
        )


        logger.lifecycle(
                '   Profiles: {}',
                profiles.join(
                        ', '
                )
        )
    }


    // ========================================================
    // Profiles
    // ========================================================

    private static List<String> resolveProfiles(
            Project project
    ) {

        def extraProperties =
                project
                        .gradle
                        .extensions
                        .extraProperties


        if (
                !extraProperties.has(
                        'profiles'
                )
        ) {

            return []
        }


        Object rawProfiles =
                extraProperties.get(
                        'profiles'
                )


        if (!(rawProfiles instanceof Collection)) {

            return []
        }


        return (rawProfiles as Collection)
                .collect {
                    Object profile ->

                        profile
                                ?.toString()
                                ?.trim()
                }
                .findAll {
                    String profile ->

                        profile != null &&
                                !profile.isBlank()
                }
                .unique()
    }


    // ========================================================
    // Render
    // ========================================================

    private static String render(
            List<String> profiles
    ) {

        StringBuilder content =
                new StringBuilder()


        content.append(
                '''# File application.yml được khởi tạo tự động.
# Sau khi file có nội dung, hệ thống sẽ không tự động ghi đè.
---
# Cấu hình chung tại đây
'''
        )


        profiles.each {
            String profile ->

                content.append(
                        """

---
# Cấu hình cho profile ${profile.toUpperCase(Locale.ROOT)}
spring:
  config:
    activate:
      on-profile: ${profile}
"""
                )
        }


        return content
                .toString()
                .trim() +
                '\n'
    }
}