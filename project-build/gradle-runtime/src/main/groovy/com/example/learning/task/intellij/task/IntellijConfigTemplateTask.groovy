package com.example.learning.task.intellij.task

import com.example.learning.task.intellij.service.IntellijConfigTemplateService
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction


abstract class IntellijConfigTemplateTask
        extends DefaultTask {

    // ==========================================
    // Input
    // ==========================================

    @Input
    abstract Property<String> getApplicationName()


    @Input
    abstract Property<String> getModuleId()


    @Input
    abstract Property<String> getMainClass()


    @Input
    abstract Property<String> getEnvPath()


    @Input
    abstract Property<String> getTemplateContent()


    // ==========================================
    // Output
    // ==========================================

    @OutputFile
    abstract RegularFileProperty getRunConfigFile()


    // ==========================================
    // Action
    // ==========================================

    @TaskAction
    void generate() {

        IntellijConfigTemplateService service =
                new IntellijConfigTemplateService()


        service.generate(
                templateContent.get(),
                runConfigFile.get().asFile,
                [
                        appName  : applicationName.get(),
                        moduleId : moduleId.get(),
                        mainClass: mainClass.get(),
                        envPath  : envPath.get()
                ]
        )


        logger.lifecycle(
                '✅ IntelliJ run configuration generated: {}',
                runConfigFile
                        .get()
                        .asFile
                        .absolutePath
        )
    }
}