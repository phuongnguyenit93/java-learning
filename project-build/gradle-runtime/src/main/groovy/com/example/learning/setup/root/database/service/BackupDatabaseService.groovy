package com.example.learning.setup.root.database.service

import com.example.learning.generated.settings.DatabaseListEnum
import org.gradle.api.Project


interface BackupDatabaseService {

    boolean supports(
            DatabaseListEnum database
    )


    void backup(
            Project rootProject,
            Project databaseProject,
            Map<String, String> environment
    )
}