package com.example.learning.setup.root.database.service.mongo

import com.example.learning.generated.settings.DatabaseListEnum
import com.example.learning.setup.root.database.service.BackupDatabaseService
import com.example.learning.setup.root.database.service.DatabaseEnvironmentService
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class MongoBackupDatabaseService
        implements BackupDatabaseService {

    private static final String TEMP_DUMP_DIRECTORY =
            '/tmp/dump'


    private final Logger logger

    private final DatabaseEnvironmentService environmentService


    MongoBackupDatabaseService(
            Logger logger,
            DatabaseEnvironmentService environmentService
    ) {

        this.logger =
                logger

        this.environmentService =
                environmentService
    }


    @Override
    boolean supports(
            DatabaseListEnum database
    ) {

        return database
                .name()
                .equalsIgnoreCase(
                        'MONGODB'
                )
    }


    @Override
    void backup(
            Project rootProject,
            Project databaseProject,
            Map<String, String> environment
    ) {

        String containerName =
                environmentService.requireValue(
                        environment,
                        'MONGODB_CONTAINER_NAME'
                )


        String mongoUri =
                environmentService.requireValue(
                        environment,
                        'MONGODB_URI'
                )


        String databaseName =
                environmentService.requireValue(
                        environment,
                        'MONGODB_DATABASE'
                )


        logger.lifecycle(
                '💾 [DATABASE] Backup MongoDB database: {}',
                databaseName
        )


        cleanupTempDirectory(
                rootProject,
                containerName
        )


        try {

            // ==================================================
            // Mongo dump
            // ==================================================

            rootProject.exec {

                commandLine(
                        'docker',
                        'exec',
                        containerName,
                        'mongodump',
                        '--uri',
                        mongoUri,
                        '--db',
                        databaseName,
                        '--out',
                        TEMP_DUMP_DIRECTORY
                )


                ignoreExitValue =
                        false
            }


            // ==================================================
            // Verify dump
            // ==================================================

            String containerDatabaseDirectory =
                    "${TEMP_DUMP_DIRECTORY}/${databaseName}"


            def checkResult =
                    rootProject.exec {

                        commandLine(
                                'docker',
                                'exec',
                                containerName,
                                'test',
                                '-d',
                                containerDatabaseDirectory
                        )


                        ignoreExitValue =
                                true
                    }


            if (
                    checkResult.exitValue != 0
            ) {

                throw new GradleException(
                        """
MongoDB dump directory was not created.

Database:
${databaseName}

Container:
${containerName}

Expected directory:
${containerDatabaseDirectory}
""".stripIndent()
                )
            }


            // ==================================================
            // Backup directory
            // ==================================================

            String timestamp =
                    LocalDateTime
                            .now()
                            .format(
                                    DateTimeFormatter.ofPattern(
                                            'yyyyMMdd_HHmmss'
                                    )
                            )


            File backupDirectory =
                    new File(
                            rootProject.rootDir,
                            "backup/mongo/${databaseName}/full_backup_${timestamp}"
                    )


            if (
                    !backupDirectory.exists() &&
                            !backupDirectory.mkdirs()
            ) {

                throw new GradleException(
                        """
Unable to create backup directory:

${backupDirectory.absolutePath}
""".stripIndent()
                )
            }


            // ==================================================
            // Copy from container
            // ==================================================

            rootProject.exec {

                commandLine(
                        'docker',
                        'cp',
                        "${containerName}:${containerDatabaseDirectory}/.",
                        backupDirectory.absolutePath
                )


                ignoreExitValue =
                        false
            }


            logger.lifecycle(
                    '✅ [DATABASE] MongoDB backup completed: {}',
                    backupDirectory.absolutePath
            )
        }
        catch (GradleException exception) {

            throw exception
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
MongoDB backup failed.

Database:
${databaseName}

Container:
${containerName}
""".stripIndent(),
                    exception
            )
        }
        finally {

            cleanupTempDirectory(
                    rootProject,
                    containerName
            )
        }
    }


    private void cleanupTempDirectory(
            Project project,
            String containerName
    ) {

        try {

            project.exec {

                commandLine(
                        'docker',
                        'exec',
                        containerName,
                        'rm',
                        '-rf',
                        TEMP_DUMP_DIRECTORY
                )


                ignoreExitValue =
                        true
            }
        }
        catch (Exception ignored) {

            logger.info(
                    '[DATABASE] Unable to cleanup MongoDB temporary dump directory.'
            )
        }
    }
}