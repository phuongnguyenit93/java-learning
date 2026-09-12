package com.example.learning.setup.root.database.service

import com.example.learning.generated.settings.DatabaseListEnum
import org.gradle.api.GradleException
import org.gradle.api.Project


class DatabaseBackupCoordinatorService {

    private final DatabaseProjectResolverService projectResolverService

    private final DatabaseEnvironmentService environmentService

    private final List<BackupDatabaseService> backupServices


    DatabaseBackupCoordinatorService(
            DatabaseProjectResolverService projectResolverService,
            DatabaseEnvironmentService environmentService,
            List<BackupDatabaseService> backupServices
    ) {

        this.projectResolverService =
                projectResolverService

        this.environmentService =
                environmentService

        this.backupServices =
                backupServices
    }


    void backup(
            Project rootProject
    ) {

        DatabaseListEnum database =
                projectResolverService
                        .resolveFromProperty(
                                rootProject
                        )


        BackupDatabaseService backupService =
                backupServices.find {
                    BackupDatabaseService service ->

                        service.supports(
                                database
                        )
                }


        if (backupService == null) {

            throw new GradleException(
                    """
Database backup is not supported yet.

Database:
${database.name()}

Type:
${database.databaseType}
""".stripIndent()
            )
        }


        Project databaseProject =
                projectResolverService
                        .resolveProject(
                                rootProject,
                                database
                        )


        Map<String, String> environment =
                environmentService
                        .load(
                                databaseProject
                        )


        backupService.backup(
                rootProject,
                databaseProject,
                environment
        )
    }
}