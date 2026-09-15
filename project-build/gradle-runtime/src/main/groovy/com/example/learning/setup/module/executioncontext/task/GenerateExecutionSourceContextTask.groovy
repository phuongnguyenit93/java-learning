package com.example.learning.setup.module.executioncontext.task

import com.example.learning.setup.module.executioncontext.service.ExecutionSourceContextGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction


abstract class GenerateExecutionSourceContextTask
        extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract DirectoryProperty getSourceDirectory()


    @OutputFile
    abstract RegularFileProperty getOutputFile()


    @TaskAction
    void generate() {

        File sourceDirectoryFile =
                sourceDirectory
                        .get()
                        .asFile


        File output =
                outputFile
                        .get()
                        .asFile


        Map result =
                new ExecutionSourceContextGenerator()
                        .generate(
                                sourceDirectoryFile,
                                output
                        )


        logger.lifecycle(
                '[EXECUTION-CONTEXT] Generated source context for {} controller methods: {}',
                (result.methods as Map).size(),
                output.absolutePath
        )
    }
}
