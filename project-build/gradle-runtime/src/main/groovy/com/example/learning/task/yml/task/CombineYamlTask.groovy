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
     * C
     * B
     * A (current module, merged separately at the end)
     *
     * Module gần root hơn có precedence cao hơn.
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

    @Input
    abstract Property<String> getBaseApplicationModulePath()


    /**
     * Track content của toàn bộ application-module.yml hiện có.
     *
     * Validation missing file vẫn được thực hiện explicit trong TaskAction
     * để message chỉ rõ module/dependency nào sai contract.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getApplicationModuleFiles()


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
                new File(
                        baseApplicationModulePath
                                .get()
                )


        if (!baseFile.isFile()) {

            throw new GradleException(
                    """
[YAML-MERGE] Current module is missing application-module.yml.

Service: ${service}
Expected:

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
        // 1. Validate + merge dependencies
        // ====================================================

        Map<String, Map> finalDocuments =
                new LinkedHashMap<>()

        List<String> dependencyPaths =
                dependencyApplicationPaths
                        .getOrElse([])


        if (dependencyPaths.size() != dependencyServices.size()) {

            throw new GradleException(
                    '[YAML-MERGE] Internal error: dependency service/path counts do not match.'
            )
        }


        dependencyPaths.eachWithIndex {
            String path,
            int index ->

                    File sourceFile =
                            new File(path)


                    String dependencyService =
                            dependencyServices[index]


                    if (!sourceFile.isFile()) {

                        throw new GradleException(
                                """
[YAML-MERGE] Dependency '${dependencyService}' is missing application-module.yml.

Expected:

${sourceFile.absolutePath}
"""
                        )
                    }


                    logger.lifecycle(
                            '   ➕ Dependency {}: {}',
                            dependencyService,
                            sourceFile.absolutePath
                    )


                    mergeService.merge(
                            finalDocuments,
                            sourceFile
                    )
                }


        // ====================================================
        // 2. Merge current module last
        // ====================================================

        logger.lifecycle(
                '   📥 Current {}: {}',
                service,
                baseFile.absolutePath
        )


        mergeService.merge(
                finalDocuments,
                baseFile
        )


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
# This file is generated only from application-module.yml files.
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
