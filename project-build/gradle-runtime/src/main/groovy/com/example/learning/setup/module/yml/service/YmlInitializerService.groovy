package com.example.learning.setup.module.yml.service

import com.example.learning.utils.GradleBuildUtils
import org.gradle.api.Project
import org.gradle.api.logging.Logger


class YmlInitializerService {

    private final Logger logger


    YmlInitializerService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    void initialize(
            Project project
    ) {

        File baseDirectory =
                GradleBuildUtils.findBaseDirectory(
                        project
                )


        List<String> profiles =
                resolveProfiles(
                        project
                )


        if (profiles.isEmpty()) {

            logger.warn(
                    '[YML-SETUP] No Gradle profiles found for {}. Skip YAML initialization.',
                    project.path
            )

            return
        }


        initializeFile(
                new File(
                        baseDirectory,
                        'application.yml'
                ),
                renderApplicationYml(
                        profiles
                ),
                'APPLICATION-YAML'
        )


        initializeFile(
                new File(
                        baseDirectory,
                        'application-module.yml'
                ),
                renderApplicationModuleYml(
                        profiles
                ),
                'APPLICATION-MODULE-YAML'
        )
    }


    private void initializeFile(
            File targetFile,
            String content,
            String logName
    ) {

        /*
         * Missing hoặc blank/whitespace-only được coi là chưa initialize.
         *
         * Một khi file đã có content thì file trở thành human-owned và
         * setup tuyệt đối không overwrite.
         */
        if (
                targetFile.isFile() &&
                        !targetFile
                                .getText(
                                        'UTF-8'
                                )
                                .trim()
                                .isEmpty()
        ) {

            logger.info(
                    '[{}] UP-TO-DATE: {}',
                    logName,
                    targetFile.absolutePath
            )

            return
        }


        if (
                targetFile.parentFile != null &&
                        !targetFile.parentFile.exists()
        ) {

            boolean created =
                    targetFile
                            .parentFile
                            .mkdirs()


            if (
                    !created &&
                            !targetFile.parentFile.exists()
            ) {

                throw new IllegalStateException(
                        "Unable to create YAML directory: ${targetFile.parentFile.absolutePath}"
                )
            }
        }


        targetFile.setText(
                content,
                'UTF-8'
        )


        logger.lifecycle(
                '✨ [{}] Initialized: {}',
                logName,
                targetFile.absolutePath
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
    // Skeletons
    // ========================================================

    private static String renderApplicationYml(
            List<String> profiles
    ) {

        return renderSkeleton(
                '''# Runtime configuration of this module.
# This skeleton is generated only when the file is missing or blank.
# After initialization this file is human-owned and will not be overwritten.
#
# application.yml is the runtime source of truth.

---
# Common runtime configuration
# Add common runtime configuration below this line.
''',
                'Runtime configuration',
                profiles
        )
    }


    private static String renderApplicationModuleYml(
            List<String> profiles
    ) {

        return renderSkeleton(
                '''# Module YAML composition contract.
# This skeleton is generated only when the file is missing or blank.
# After initialization this file is human-owned and will not be overwritten.
#
# combineYaml reads this file. Spring Boot does not use it as runtime config.

---
# Common module configuration exported for composition
# Add common exported configuration below this line.
''',
                'Module configuration',
                profiles
        )
    }


    private static String renderSkeleton(
            String header,
            String profileComment,
            List<String> profiles
    ) {

        StringBuilder content =
                new StringBuilder(
                        header.trim()
                )


        profiles.each {
            String profile ->

                content.append(
                        """


---
# ${profileComment} for profile ${profile.toUpperCase(Locale.ROOT)}
spring:
  config:
    activate:
      on-profile: ${profile}

# Add profile-specific configuration below this line.
"""
                )
        }


        return content
                .toString()
                .trim() +
                '\n'
    }
}
