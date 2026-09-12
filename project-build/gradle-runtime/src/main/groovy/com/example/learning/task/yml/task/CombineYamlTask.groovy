package com.example.learning.task.yml.task

import com.example.learning.task.yml.service.YamlMergeService
import com.example.learning.utils.GradleBuildUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(
        because = 'Generates application-merged.yml as a local reference file'
)
abstract class CombineYamlTask
        extends DefaultTask {

    // ========================================================
    // Metadata
    // ========================================================

    @Input
    abstract Property<String> getServiceName()


    /**
     * Recursive dependency list.
     *
     * Thứ tự quan trọng vì:
     *
     * A -> B -> C
     *
     * merge order:
     *
     * BASE
     * A
     * B
     * C
     *
     * C có precedence cao nhất.
     */
    @Input
    abstract ListProperty<String> getDependencyServiceNames()


    /**
     * Dùng để giữ chính xác merge order.
     *
     * @InputFiles không nên được dùng làm nguồn
     * quyết định thứ tự merge.
     */
    @Input
    abstract ListProperty<String> getDependencyApplicationPaths()


    // ========================================================
    // YAML inputs
    // ========================================================

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getBaseApplicationModuleFile()


    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getDependencyApplicationFiles()


    // ========================================================
    // Output
    // ========================================================

    @OutputFile
    abstract RegularFileProperty getMergedApplicationFile()


    // ========================================================
    // Action
    // ========================================================

    @TaskAction
    void combine() {

        String service =
                serviceName
                        .getOrElse('')
                        .trim()


        if (service.isBlank()) {

            throw new GradleException(
                    """
Missing SERVICE_NAME.

Please configure:

SERVICE_NAME=...

in gradle.properties.
"""
            )
        }


        File baseFile =
                baseApplicationModuleFile
                        .get()
                        .asFile


        if (!baseFile.exists()) {

            throw new GradleException(
                    """
[YAML-MERGE] application-module.yml not found:

${baseFile.absolutePath}
"""
            )
        }


        List<String> dependencyServices =
                dependencyServiceNames
                        .getOrElse([])


        logger.lifecycle('')
        logger.lifecycle(
                '=================================================='
        )
        logger.lifecycle(
                '🔍 GENERATE APPLICATION-MERGED.YML'
        )
        logger.lifecycle(
                '=================================================='
        )

        logger.lifecycle(
                '🧩 [YAML-MERGE] Service: {}',
                service
        )


        if (dependencyServices.isEmpty()) {

            logger.lifecycle(
                    'ℹ️ [STANDALONE] {} has no YAML dependencies.',
                    service
            )
        }


        YamlMergeService mergeService =
                new YamlMergeService()


        // ====================================================
        // 1. Load module chính
        // ====================================================

        logger.lifecycle(
                '   📥 Loading Base: {}',
                baseFile.absolutePath
        )


        Map<String, Map> finalDocuments =
                mergeService.loadBase(
                        baseFile
                )


        // ====================================================
        // 2. Merge recursive dependencies
        // ====================================================

        dependencyApplicationPaths
                .getOrElse([])
                .each { String path ->

                    File sourceFile =
                            new File(path)


                    if (!sourceFile.exists()) {

                        logger.lifecycle(
                                '   ⚠️ Missing: {} (skip)',
                                sourceFile.absolutePath
                        )

                        return
                    }


                    logger.lifecycle(
                            '   ➕ Merging: {}',
                            sourceFile.absolutePath
                    )


                    mergeService.merge(
                            finalDocuments,
                            sourceFile
                    )
                }


        // ====================================================
        // 3. Output
        // ====================================================

        File outputFile =
                mergedApplicationFile
                        .get()
                        .asFile


        String outputRelativePath =
                GradleBuildUtils.normalizePath(
                        project.rootDir
                                .toPath()
                                .relativize(
                                        outputFile.toPath()
                                )
                                .toString()
                )


        String header =
                """\
# ================================================================
# AUTO-GENERATED FILE
# ================================================================
#
# This file is generated from application-module.yml files.
#
# DO NOT use this file directly as the runtime configuration.
#
# Use this file as a reference when maintaining application.yml.
#
# Source       : ${service}
# Dependencies : ${dependencyServices.join(', ')}
# Output       : ${outputRelativePath}
#
# ================================================================

"""


        mergeService.write(
                outputFile,
                finalDocuments.values(),
                header
        )


        logger.lifecycle(
                '✅ [SUCCESS] Merged {} profiles: {}',
                finalDocuments.size(),
                finalDocuments.keySet().join(', ')
        )


        logger.lifecycle(
                '   📍 Reference file: {}',
                outputFile.absolutePath
        )


        logger.lifecycle(
                '--------------------------------------------------'
        )

        logger.lifecycle('')
    }
}