package com.example.learning.setup.root.contractSkeleton.plugin

import com.example.learning.setup.root.contractSkeleton.service.ContractSkeletonSyncService
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Root-only plugin, NOT a Gradle task and NOT a module plugin.
 * Runs during Gradle configuration, including IDE Gradle Sync.
 */
class ContractSkeletonSetupPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        if (project != project.rootProject) {
            throw new GradleException('ContractSkeletonSetupPlugin must be applied at root.')
        }
        new ContractSkeletonSyncService(project.logger).sync(project.projectDir)
    }
}
