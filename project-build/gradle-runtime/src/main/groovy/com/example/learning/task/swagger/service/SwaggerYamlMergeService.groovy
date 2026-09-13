package com.example.learning.task.swagger.service

import com.example.learning.task.swagger.model.SwaggerApiMetadata
import com.example.learning.task.swagger.model.SwaggerDescriptionDefault
import com.example.learning.task.swagger.model.SwaggerScanResult
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SwaggerYamlMergeService {

    private static final Set<String> API_HUMAN_FIELDS = [
            'summary',
            'description',
            'videoYoutubeId',
            'videoYoutubeTitle'
    ] as Set

    private final Yaml reader

    private final Yaml writer


    SwaggerYamlMergeService() {

        DumperOptions options = new DumperOptions()
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
        options.setPrettyFlow(true)
        options.setIndent(2)

        this.writer = new Yaml(options)
        this.reader = new Yaml()
    }


    void merge(
            File swaggerDirectory,
            SwaggerScanResult scanResult,
            SwaggerDescriptionDefault defaults
    ) {

        if (
                !swaggerDirectory.exists() &&
                        !swaggerDirectory.mkdirs()
        ) {

            throw new IllegalStateException(
                    """
Unable to create Swagger directory:

${swaggerDirectory.absolutePath}
"""
            )
        }


        File apiFile =
                new File(
                        swaggerDirectory,
                        'api-descriptions.yml'
                )


        File paramsFile =
                new File(
                        swaggerDirectory,
                        'api-params.yml'
                )


        /*
         * LOAD TRƯỚC.
         *
         * Nếu YAML malformed thì throw ở đây.
         * Chưa có file nào bị overwrite.
         */
        Map<String, Object> apiData =
                loadMap(
                        apiFile
                )


        Map<String, Object> paramsData =
                loadMap(
                        paramsFile
                )


        mergeApiData(
                apiData,
                scanResult,
                defaults
        )


        mergeParamsData(
                paramsData,
                scanResult.parameterNames,
                defaults
        )


        /*
         * Dump cả hai trước khi write.
         */
        String apiYaml =
                writer.dump(
                        apiData
                )


        String paramsYaml =
                writer.dump(
                        paramsData
                )


        writeSafely(
                apiFile,
                apiYaml
        )


        writeSafely(
                paramsFile,
                paramsYaml
        )
    }


    private static void mergeApiData(
            Map<String, Object> apiData,
            SwaggerScanResult scanResult,
            SwaggerDescriptionDefault defaults
    ) {

        /*
         * Trước tiên coi toàn bộ API cũ là không còn sử dụng.
         *
         * API nào scan thấy lại sẽ được set true.
         */
        markAllApiUsage(
                apiData,
                false
        )


        scanResult.apiByController.each {
            String controllerName,
            Map<String, SwaggerApiMetadata> currentApis ->

                Map<String, Object> controllerData =
                        getOrCreateControllerData(
                                apiData,
                                controllerName
                        )


                Map<String, Integer> overloadCount =
                        currentApis
                                .values()
                                .countBy {
                                    it.methodName
                                }


                currentApis.each {
                    String methodSignature,
                    SwaggerApiMetadata metadata ->

                        Map<String, Object> apiEntry =
                                resolveApiEntry(
                                        controllerData,
                                        metadata,
                                        overloadCount
                                )


                        ensureApiHumanFields(
                                apiEntry,
                                defaults
                        )


                        /*
                         * GENERATED FIELDS
                         *
                         * Luôn update từ source code.
                         */
                        apiEntry[
                                'methodName'
                        ] =
                                metadata.methodName


                        apiEntry[
                                'params'
                        ] =
                                metadata.params


                        apiEntry[
                                'path'
                        ] =
                                metadata.path


                        apiEntry[
                                'mapping'
                        ] =
                                metadata.mapping


                        apiEntry[
                                'httpMethods'
                        ] =
                                metadata.httpMethods


                        apiEntry[
                                'mappingPaths'
                        ] =
                                metadata.mappingPaths


                        apiEntry[
                                'produces'
                        ] =
                                metadata.produces


                        apiEntry[
                                'consumes'
                        ] =
                                metadata.consumes


                        apiEntry[
                                'usage'
                        ] =
                                true
                }
        }
    }


    private static Map<String, Object> resolveApiEntry(
            Map<String, Object> controllerData,
            SwaggerApiMetadata metadata,
            Map<String, Integer> overloadCount
    ) {

        Object current =
                controllerData[
                        metadata.methodSignature
                ]


        if (current != null) {

            return requireMap(
                    current,
                    """
Invalid Swagger API entry:

${metadata.controllerName}
${metadata.methodSignature}
"""
            )
        }


        Map<String, Object> newEntry =
                new LinkedHashMap<>()


        /*
         * Migration từ YAML legacy:
         *
         * getUser:
         *   summary: ...
         *
         * sang:
         *
         * getUser(String):
         *   summary: ...
         *
         * Chỉ copy khi methodName KHÔNG overload.
         * Nếu overload thì không đoán.
         */
        if (
                overloadCount[
                        metadata.methodName
                ] == 1
        ) {

            Object legacy =
                    controllerData[
                            metadata.methodName
                    ]


            if (legacy instanceof Map) {

                copyHumanFields(
                        legacy as Map,
                        newEntry,
                        API_HUMAN_FIELDS
                )
            }
        }


        controllerData[
                metadata.methodSignature
        ] =
                newEntry


        return newEntry
    }


    private static void mergeParamsData(
            Map<String, Object> paramsData,
            Collection<String> currentParameters,
            SwaggerDescriptionDefault defaults
    ) {

        /*
         * Parameter cũ vẫn giữ lại,
         * chỉ đổi usage=false.
         */
        paramsData.each {
            String parameterName,
            Object value ->

                Map<String, Object> entry =
                        requireMap(
                                value,
                                """
Invalid Swagger parameter entry:

${parameterName}
"""
                        )


                entry[
                        'usage'
                ] =
                        false
        }


        currentParameters.each {
            String parameterName ->

                Object existing =
                        paramsData[
                                parameterName
                        ]


                Map<String, Object> entry


                if (existing == null) {

                    entry =
                            new LinkedHashMap<>()


                    paramsData[
                            parameterName
                    ] =
                            entry
                }
                else {

                    entry =
                            requireMap(
                                    existing,
                                    """
Invalid Swagger parameter entry:

${parameterName}
"""
                            )
                }


                /*
                 * HUMAN OWNED
                 */
                if (
                        !entry.containsKey(
                                'summary'
                        )
                ) {

                    entry[
                            'summary'
                    ] =
                            defaults.summary
                }


                if (
                        !entry.containsKey(
                                'description'
                        )
                ) {

                    entry[
                            'description'
                    ] =
                            defaults.description
                }


                entry[
                        'usage'
                ] =
                        true
        }
    }


    private static void ensureApiHumanFields(
            Map<String, Object> entry,
            SwaggerDescriptionDefault defaults
    ) {

        /*
         * Quan trọng:
         *
         * chỉ kiểm tra containsKey.
         *
         * Nếu user cố tình để:
         *
         * summary: ""
         *
         * thì generator cũng KHÔNG được overwrite.
         */

        if (
                !entry.containsKey(
                        'summary'
                )
        ) {

            entry[
                    'summary'
            ] =
                    defaults.summary
        }


        if (
                !entry.containsKey(
                        'description'
                )
        ) {

            entry[
                    'description'
            ] =
                    defaults.description
        }

        if (
                !entry.containsKey(
                        'enableVideoYoutube'
                )
        ) {

            entry[
                    'enableVideoYoutube'
            ] =
                    defaults.enableVideoYoutube
        }

        if (
                !entry.containsKey(
                        'videoYoutubeId'
                )
        ) {

            entry[
                    'videoYoutubeId'
            ] =
                    defaults.videoYoutubeId
        }

        if (
                !entry.containsKey(
                        'videoYoutubeTitle'
                )
        ) {

            entry[
                    'videoYoutubeTitle'
            ] =
                    defaults.videoYoutubeTitle
        }


    }


    private static void markAllApiUsage(
            Map<String, Object> apiData,
            boolean usage
    ) {

        apiData.each {
            String controllerName,
            Object controllerValue ->

                Map<String, Object> controllerData =
                        requireMap(
                                controllerValue,
                                """
Invalid Swagger controller entry:

${controllerName}
"""
                        )


                controllerData.each {
                    String methodIdentity,
                    Object apiValue ->

                        Map<String, Object> apiEntry =
                                requireMap(
                                        apiValue,
                                        """
Invalid Swagger API entry:

${controllerName}
${methodIdentity}
"""
                                )


                        apiEntry[
                                'usage'
                        ] =
                                usage
                }
        }
    }


    private static Map<String, Object> getOrCreateControllerData(
            Map<String, Object> apiData,
            String controllerName
    ) {

        Object existing =
                apiData[
                        controllerName
                ]


        if (existing == null) {

            Map<String, Object> created =
                    new LinkedHashMap<>()


            apiData[
                    controllerName
            ] =
                    created


            return created
        }


        return requireMap(
                existing,
                """
Invalid Swagger controller entry:

${controllerName}
"""
        )
    }


    private static void copyHumanFields(
            Map source,
            Map target,
            Collection<String> fields
    ) {

        fields.each {
            String field ->

                if (
                        source.containsKey(
                                field
                        )
                ) {

                    target[
                            field
                    ] =
                            source[
                                    field
                            ]
                }
        }
    }


    private Map<String, Object> loadMap(
            File file
    ) {

        if (
                !file.exists() ||
                        file.length() == 0
        ) {

            return new LinkedHashMap<>()
        }


        Object loaded


        try {

            loaded =
                    reader.load(
                            file.getText(
                                    'UTF-8'
                            )
                    )
        }
        catch (Exception exception) {

            throw new IllegalStateException(
                    """
Unable to parse Swagger YAML.

File:

${file.absolutePath}

The file will NOT be overwritten.

Cause:

${exception.message}
""",
                    exception
            )
        }


        if (loaded == null) {

            return new LinkedHashMap<>()
        }


        if (!(loaded instanceof Map)) {

            throw new IllegalStateException(
                    """
Invalid Swagger YAML structure.

Expected root object to be a map.

File:

${file.absolutePath}

The file will NOT be overwritten.
"""
            )
        }


        return loaded as Map<String, Object>
    }


    private static Map<String, Object> requireMap(
            Object value,
            String message
    ) {

        if (!(value instanceof Map)) {

            throw new IllegalStateException(
                    message.trim()
            )
        }


        return value as Map<String, Object>
    }


    private static void writeSafely(
            File outputFile,
            String content
    ) {

        if (
                !outputFile.parentFile.exists() &&
                        !outputFile.parentFile.mkdirs()
        ) {

            throw new IllegalStateException(
                    """
Unable to create directory:

${outputFile.parentFile.absolutePath}
"""
            )
        }


        File temporaryFile =
                new File(
                        outputFile.parentFile,
                        "${outputFile.name}.tmp"
                )


        temporaryFile.setText(
                content,
                'UTF-8'
        )


        try {

            Files.move(
                    temporaryFile.toPath(),
                    outputFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            )
        }
        finally {

            if (temporaryFile.exists()) {

                temporaryFile.delete()
            }
        }
    }
}