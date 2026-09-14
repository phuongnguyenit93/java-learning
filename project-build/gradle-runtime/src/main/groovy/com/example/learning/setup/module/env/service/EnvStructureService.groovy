package com.example.learning.setup.module.env.service

import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.Project
import org.gradle.api.logging.Logger


class EnvStructureService {

    private final Logger logger


    EnvStructureService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    void setup(
            Project project
    ) {

        // ====================================================
        // Default .env
        // ====================================================

        if (
                ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_ENV'
                )
        ) {

            ensureDefaultEnv(
                    project
            )
        }


        // ====================================================
        // Profile .env files
        // ====================================================

        if (
                ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_ENV_PROFILE'
                )
        ) {

            ensureProfileEnvs(
                    project
            )
        }
    }


    // ========================================================
    // Default .env
    // ========================================================

    private void ensureDefaultEnv(
            Project project
    ) {

        String serviceName =
                ProjectPropertyUtils.getString(
                        project,
                        'SERVICE_NAME'
                ) ?: project.name


        File envFile =
                new File(
                        project.projectDir,
                        '.env'
                )


        /*
         * Human-owned.
         *
         * File đã tồn tại thì tuyệt đối không sửa,
         * kể cả file đang rỗng.
         */
        if (envFile.exists()) {

            if (!envFile.isFile()) {

                logger.warn(
                        '[ENV-STRUCTURE] {} exists but is not a file: {}',
                        serviceName,
                        envFile.absolutePath
                )

                return
            }


            logger.info(
                    '[ENV-STRUCTURE] Default ENV already exists: {}',
                    envFile.absolutePath
            )

            return
        }


        try {

            envFile.setText(
                    "# Environment variables for ${serviceName}\n",
                    'UTF-8'
            )


            logger.lifecycle(
                    '✨ [ENV-STRUCTURE] [{}] Initialized: {}',
                    serviceName,
                    envFile.absolutePath
            )
        }
        catch (Exception exception) {

            logger.error(
                    '❌ [ENV-STRUCTURE] Unable to initialize .env for {}: {}',
                    serviceName,
                    exception.message
            )
        }
    }


    // ========================================================
    // Profile .env
    // ========================================================

    private void ensureProfileEnvs(
            Project project
    ) {

        List<String> profiles =
                resolveProfiles(
                        project
                )


        if (profiles.isEmpty()) {

            logger.warn(
                    '[ENV-STRUCTURE] No profiles found for {}.',
                    project.path
            )

            return
        }


        int createdCount =
                0


        profiles.each {
            String profile ->

                File envProfile =
                        new File(
                                project.projectDir,
                                ".env.${profile}"
                        )


                /*
                 * File đã tồn tại:
                 *
                 * human-owned → không sửa.
                 */
                if (envProfile.exists()) {

                    if (!envProfile.isFile()) {

                        logger.warn(
                                '[ENV-STRUCTURE] Profile path exists but is not a file: {}',
                                envProfile.absolutePath
                        )
                    }

                    return
                }


                try {

                    /*
                     * Giữ nguyên behavior cũ:
                     *
                     * .env.<profile> được tạo rỗng.
                     */
                    if (envProfile.createNewFile()) {

                        createdCount++


                        logger.info(
                                '[ENV-STRUCTURE] Created profile ENV: {}',
                                envProfile.absolutePath
                        )
                    }
                }
                catch (Exception exception) {

                    logger.error(
                            '❌ [ENV-STRUCTURE] Unable to create {}: {}',
                            envProfile.name,
                            exception.message
                    )
                }
        }


        if (createdCount > 0) {

            logger.lifecycle(
                    '✨ [ENV-STRUCTURE] [{}] Created {} profile ENV file(s).',
                    project.path,
                    createdCount
            )
        }
        else {

            logger.info(
                    '[ENV-STRUCTURE] Profile ENV structure is complete: {}',
                    project.path
            )
        }
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
}
