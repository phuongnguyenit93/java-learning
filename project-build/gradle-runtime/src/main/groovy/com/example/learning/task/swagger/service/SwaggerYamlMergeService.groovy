package com.example.learning.task.swagger.service

import com.example.learning.task.swagger.model.SwaggerApiMetadata
import com.example.learning.task.swagger.model.SwaggerDescriptionDefault
import com.example.learning.task.swagger.model.SwaggerScanResult
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern

class SwaggerYamlMergeService {

    private static final Logger LOGGER =
            Logging.getLogger(SwaggerYamlMergeService)

    private static final Set<String> API_HUMAN_FIELDS = [
            'summary',
            'description',
            'videoYoutubeId',
            'videoYoutubeTitle',
            'readmeRelated',
            'aiGenerated',
            'reviewed'
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


        File controllerDescriptionFile =
                new File(
                        swaggerDirectory,
                        'controller-description.yml'
                )


        File apiExecutionFile =
                new File(
                        swaggerDirectory,
                        'api-execution.yml'
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


        Map<String, Object> controllerDescriptionData =
                loadMap(
                        controllerDescriptionFile
                )


        Map<String, Object> apiExecutionData =
                loadMap(
                        apiExecutionFile
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


        mergeControllerDescriptionData(
                controllerDescriptionData,
                scanResult,
                defaults
        )


        mergeApiExecutionData(
                apiExecutionData,
                scanResult,
                defaults
        )


        validateReadmeRelationships(
                swaggerDirectory,
                controllerDescriptionData,
                apiData
        )


        /*
         * Dump toàn bộ trước khi write.
         */
        String apiYaml =
                writer.dump(
                        apiData
                )


        String paramsYaml =
                writer.dump(
                        paramsData
                )


        String controllerDescriptionYaml =
                writer.dump(
                        controllerDescriptionData
                )


        String apiExecutionYaml =
                writer.dump(
                        apiExecutionData
                )


        writeSafely(
                apiFile,
                apiYaml
        )


        writeSafely(
                paramsFile,
                paramsYaml
        )


        writeSafely(
                controllerDescriptionFile,
                controllerDescriptionYaml
        )


        writeSafely(
                apiExecutionFile,
                apiExecutionYaml
        )
    }


    private static void validateReadmeRelationships(
            File swaggerDirectory,
            Map<String, Object> controllerDescriptionData,
            Map<String, Object> apiData
    ) {

        File projectDirectory =
                resolveProjectDirectory(
                        swaggerDirectory
                )


        if (projectDirectory == null) {
            return
        }


        String language =
                swaggerDirectory.name


        File menuDirectory =
                new File(
                        projectDirectory,
                        "readme/${language}/menu"
                )


        controllerDescriptionData.each {
            String controllerName,
            Object controllerValue ->

                Map<String, Object> controllerEntry =
                        requireMap(
                                controllerValue,
                                "Invalid Swagger controller description entry: ${controllerName}"
                        )


                String controllerFile =
                        readReadmeField(
                                controllerEntry,
                                'file'
                        )


                if (
                        controllerFile != null &&
                                !controllerFile.isBlank() &&
                                !isValidReadmeFile(
                                        menuDirectory,
                                        controllerFile
                                )
                ) {

                    warnReadmeMapping(
                            language,
                            controllerName,
                            'INVALID_FILE',
                            controllerFile,
                            null
                    )
                }


                Object apiControllerValue =
                        apiData[
                                controllerName
                        ]


                if (apiControllerValue == null) {
                    return
                }


                Map<String, Object> apiController =
                        requireMap(
                                apiControllerValue,
                                "Invalid Swagger API controller entry: ${controllerName}"
                        )


                apiController.each {
                    String methodSignature,
                    Object methodValue ->

                        Map<String, Object> methodEntry =
                                requireMap(
                                        methodValue,
                                        "Invalid Swagger API entry: ${controllerName}#${methodSignature}"
                                )


                        String anchor =
                                readReadmeField(
                                        methodEntry,
                                        'anchor'
                                )


                        if (
                                anchor == null ||
                                        anchor.isBlank()
                        ) {
                            return
                        }


                        String methodFile =
                                readReadmeField(
                                        methodEntry,
                                        'file'
                                )


                        String resolvedFile =
                                methodFile != null &&
                                        !methodFile.isBlank()
                                        ? methodFile
                                        : controllerFile


                        if (
                                resolvedFile == null ||
                                        resolvedFile.isBlank() ||
                                        !isValidReadmeFile(
                                                menuDirectory,
                                                resolvedFile
                                        )
                        ) {

                            warnReadmeMapping(
                                    language,
                                    "${controllerName}#${methodSignature}",
                                    'INVALID_FILE',
                                    resolvedFile,
                                    anchor
                            )

                            return
                        }


                        File readmeFile =
                                new File(
                                        menuDirectory,
                                        normalizeReadmeFile(
                                                resolvedFile
                                        )
                                )


                        String markdown =
                                readmeFile.getText(
                                        'UTF-8'
                                )


                        if (
                                !containsReadmeAnchor(
                                        markdown,
                                        anchor
                                )
                        ) {

                            warnReadmeMapping(
                                    language,
                                    "${controllerName}#${methodSignature}",
                                    'INVALID_ANCHOR',
                                    resolvedFile,
                                    anchor
                            )
                        }
                }
        }
    }


    private static File resolveProjectDirectory(
            File swaggerDirectory
    ) {

        File current =
                swaggerDirectory


        for (int index = 0; index < 5; index++) {

            current =
                    current?.parentFile


            if (current == null) {
                return null
            }
        }


        return current
    }


    private static String readReadmeField(
            Map<String, Object> entry,
            String field
    ) {

        Object relatedValue =
                entry[
                        'readmeRelated'
                ]


        if (relatedValue == null) {
            return null
        }


        Map<String, Object> related =
                requireMap(
                        relatedValue,
                        'Invalid Swagger readmeRelated entry.'
                )


        Object value =
                related[
                        field
                ]


        return value == null
                ? null
                : value.toString().trim()
    }


    private static boolean isValidReadmeFile(
            File menuDirectory,
            String configuredFile
    ) {

        String normalized =
                normalizeReadmeFile(
                        configuredFile
                )


        if (
                normalized == null ||
                        normalized.isBlank() ||
                        normalized.startsWith('/') ||
                        normalized == '..' ||
                        normalized.contains('../')
        ) {
            return false
        }


        int separator =
                normalized.indexOf('/')


        if (
                separator <= 0 ||
                        separator >= normalized.length() - 1
        ) {
            return false
        }


        String folder =
                normalized.substring(
                        0,
                        separator
                )


        if (
                !(folder ==~ /\d+\..+/)
        ) {
            return false
        }


        return new File(
                menuDirectory,
                normalized
        ).isFile()
    }


    private static String normalizeReadmeFile(
            String configuredFile
    ) {

        return configuredFile == null
                ? null
                : configuredFile
                        .trim()
                        .replace(
                                '\\',
                                '/'
                        )
    }


    private static boolean containsReadmeAnchor(
            String markdown,
            String anchor
    ) {

        if (
                markdown == null ||
                        anchor == null ||
                        anchor.isBlank()
        ) {
            return false
        }


        Pattern pattern =
                Pattern.compile(
                        "<a\\s+[^>]*\\bid\\s*=\\s*([\\\"'])" +
                                Pattern.quote(
                                        anchor
                                ) +
                                "\\1[^>]*>",
                        Pattern.CASE_INSENSITIVE
                )


        return pattern
                .matcher(
                        markdown
                )
                .find()
    }


    private static void warnReadmeMapping(
            String language,
            String owner,
            String status,
            String file,
            String anchor
    ) {

        LOGGER.warn(
                '[SWAGGER-README] language={} owner={} status={} file={} anchor={}',
                language,
                owner,
                status,
                file,
                anchor
        )
    }


    private static void mergeApiExecutionData(
            Map<String, Object> apiExecutionData,
            SwaggerScanResult scanResult,
            SwaggerDescriptionDefault defaults
    ) {

        markAllApiUsage(
                apiExecutionData,
                false
        )


        scanResult.apiByController.each {
            String controllerName,
            Map<String, SwaggerApiMetadata> currentApis ->

                Map<String, Object> controllerData =
                        getOrCreateControllerData(
                                apiExecutionData,
                                controllerName
                        )


                currentApis.each {
                    String methodSignature,
                    SwaggerApiMetadata metadata ->

                        Object existing =
                                controllerData[
                                        methodSignature
                                ]


                        Map<String, Object> entry


                        if (existing == null) {

                            entry =
                                    new LinkedHashMap<>()


                            controllerData[
                                    methodSignature
                            ] =
                                    entry
                        }
                        else {

                            entry =
                                    requireMap(
                                            existing,
                                            """
Invalid Swagger API execution entry:

${controllerName}
${methodSignature}
"""
                                    )
                        }


                        if (
                                !entry.containsKey(
                                        'execution'
                                )
                        ) {

                            entry[
                                    'execution'
                            ] =
                                    defaults.execution
                        }


                        entry[
                                'usage'
                        ] =
                                true
                }
        }
    }


    private static void mergeControllerDescriptionData(
            Map<String, Object> controllerDescriptionData,
            SwaggerScanResult scanResult,
            SwaggerDescriptionDefault defaults
    ) {

        /*
         * controller-description.yml khác api-descriptions.yml:
         *
         * - controller còn tồn tại trong source -> phải có entry.
         * - controller không còn tồn tại -> xóa entry.
         * - description đã tồn tại -> HUMAN OWNED, tuyệt đối không overwrite.
         */
        controllerDescriptionData.each {
            String controllerName,
            Object value ->

                requireMap(
                        value,
                        """
Invalid Swagger controller description entry:

${controllerName}
"""
                )
        }


        Map<String, Object> merged =
                new LinkedHashMap<>()


        scanResult.apiByController.keySet().each {
            String controllerName ->

                Object existing =
                        controllerDescriptionData[
                                controllerName
                        ]


                Map<String, Object> entry =
                        existing == null
                                ? new LinkedHashMap<>()
                                : new LinkedHashMap<>(
                                        requireMap(
                                                existing,
                                                """
Invalid Swagger controller description entry:

${controllerName}
"""
                                        )
                                )


                if (
                        !entry.containsKey(
                                'description'
                        )
                ) {

                    entry[
                            'description'
                    ] =
                            buildControllerDescription(
                                    controllerName,
                                    defaults.controllerDescriptionParagraph
                            )
                }


                ensureReadmeRelatedFields(
                        entry,
                        [
                                'file'
                        ]
                )


                merged[
                        controllerName
                ] =
                        entry
        }


        controllerDescriptionData.clear()
        controllerDescriptionData.putAll(
                merged
        )
    }


    private static String buildControllerDescription(
            String controllerName,
            String descriptionParagraph
    ) {

        String displayName =
                controllerName
                        .replaceAll(
                                /([A-Z]+)([A-Z][a-z])/,
                                '$1 $2'
                        )
                        .replaceAll(
                                /([a-z0-9])([A-Z])/,
                                '$1 $2'
                        )


        return "<h2>${displayName}</h2>\n<p>${descriptionParagraph}</p>"
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


        if (
                !entry.containsKey(
                        'aiGenerated'
                )
        ) {

            entry[
                    'aiGenerated'
            ] =
                    defaults.aiGenerated
        }


        if (
                !entry.containsKey(
                        'reviewed'
                )
        ) {

            entry[
                    'reviewed'
            ] =
                    defaults.reviewed
        }


        ensureReadmeRelatedFields(
                entry,
                [
                        'file',
                        'anchor'
                ]
        )


    }


    private static void ensureReadmeRelatedFields(
            Map<String, Object> entry,
            Collection<String> fields
    ) {

        Object existing =
                entry[
                        'readmeRelated'
                ]


        Map<String, Object> readmeRelated


        if (existing == null) {

            readmeRelated =
                    new LinkedHashMap<>()


            entry[
                    'readmeRelated'
            ] =
                    readmeRelated
        }
        else {

            readmeRelated =
                    requireMap(
                            existing,
                            'Invalid Swagger readmeRelated entry.'
                    )
        }


        fields.each {
            String field ->

                if (
                        !readmeRelated.containsKey(
                                field
                        )
                ) {

                    readmeRelated[
                            field
                    ] =
                            ''
                }
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

                    Object value =
                            source[
                                    field
                            ]


                    target[
                            field
                    ] =
                            value instanceof Map
                                    ? new LinkedHashMap<>(
                                            value as Map
                                    )
                                    : value
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
