package com.example.learning.setup.root.structure.task

import com.example.learning.setup.root.structure.service.ModuleOrderGenerationService
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault


@DisableCachingByDefault(
        because = 'This task synchronizes human-maintained module-order.yml files in the project workspace.'
)
abstract class GenerateModuleOrderTask
        extends DefaultTask {

    // ========================================================
    // Input
    // ========================================================

    @Input
    abstract Property<String> getTargetPath()


    @Option(
            option = 'path',
            description = 'Parent path relative to module/. Omit to synchronize existing module-order.yml files only.'
    )
    void setPath(
            String value
    ) {

        targetPath.set(
                value ?: ''
        )
    }


    // ========================================================
    // Workspace
    // ========================================================

    @Internal
    abstract DirectoryProperty getRootDirectory()


    // ========================================================
    // Action
    // ========================================================

    @TaskAction
    void generate() {

        ModuleOrderGenerationService service =
                new ModuleOrderGenerationService(
                        logger
                )


        service.sync(
                rootDirectory
                        .get()
                        .asFile,

                targetPath.get()
        )
    }
}
