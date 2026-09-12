package com.example.learning.setup.root.database.plugin

import com.example.learning.setup.root.database.service.BackupDatabaseService
import com.example.learning.setup.root.database.service.DatabaseBackupCoordinatorService
import com.example.learning.setup.root.database.service.DatabaseEnvironmentService
import com.example.learning.setup.root.database.service.DatabaseProjectResolverService
import com.example.learning.setup.root.database.service.mongo.MongoBackupDatabaseService
import com.example.learning.setup.root.database.service.mongo.MongoWhitelistService
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project


class DatabaseSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        // ====================================================
        // Root guard
        // ====================================================

        if (
                project !=
                        project.rootProject
        ) {

            throw new GradleException(
                    """
DatabaseSetupPlugin must only be applied to root project.

Current project:
${project.path}
""".stripIndent()
            )
        }


        // ====================================================
        // Shared services
        // ====================================================

        DatabaseEnvironmentService environmentService =
                new DatabaseEnvironmentService()


        DatabaseProjectResolverService projectResolverService =
                new DatabaseProjectResolverService()


        // ====================================================
        // Backup implementations
        // ====================================================

        List<BackupDatabaseService> backupServices =
                [
                        new MongoBackupDatabaseService(
                                project.logger,
                                environmentService
                        )
                ]


        DatabaseBackupCoordinatorService backupCoordinatorService =
                new DatabaseBackupCoordinatorService(
                        projectResolverService,
                        environmentService,
                        backupServices
                )


        // ====================================================
        // MongoDB specific
        // ====================================================

        MongoWhitelistService mongoWhitelistService =
                new MongoWhitelistService(
                        project.logger,
                        projectResolverService,
                        environmentService
                )


        // ====================================================
        // backupDatabase
        // ====================================================

        project.tasks.register(
                'backupDatabase'
        ) {

            group =
                    'database'


            description =
                    'Backup database selected by -PDATABASE=...'


            doLast {

                backupCoordinatorService.backup(
                        project
                )
            }
        }


        // ====================================================
        // updateMongoWhitelistIP
        // ====================================================

        project.tasks.register(
                'updateMongoWhitelistIP'
        ) {

            group =
                    'database'


            description =
                    'Add current public IP to MongoDB Atlas whitelist.'


            doLast {

                mongoWhitelistService.updateCurrentIp(
                        project
                )
            }
        }
    }
}