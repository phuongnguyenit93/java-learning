package com.example.learning.task.swagger.service

import com.example.learning.task.swagger.model.SwaggerApiMetadata
import com.example.learning.task.swagger.model.SwaggerScanResult
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Generates a fill-in-the-blanks use-case inventory beside Swagger metadata.
 * Example payloads, statuses and expected responses cannot be inferred safely.
 */
class SwaggerApiCasesMergeService {

    private final Yaml writer

    SwaggerApiCasesMergeService() {
        DumperOptions options = new DumperOptions()
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
        options.setPrettyFlow(true)
        options.setIndent(2)
        writer = new Yaml(options)
    }

    void merge(File swaggerDirectory, SwaggerScanResult scanResult) {
        File outputFile = new File(swaggerDirectory, 'api-cases.yml')
        Map<String, Object> document = loadDocument(outputFile)

        validateDocument(document)

        document.each { Object controller, Object value ->
            (value as Map).each { Object method, Object entry ->
                (entry as Map)['usage'] = false
            }
        }

        boolean vietnamese = swaggerDirectory.name.toLowerCase(Locale.ROOT) == 'vi' ||
                swaggerDirectory.name.toLowerCase(Locale.ROOT).endsWith('-vi')

        scanResult.apiByController.each {
            String controllerName, Map<String, SwaggerApiMetadata> methods ->
                Map<String, Object> controllerData =
                        (Map<String, Object>) document.computeIfAbsent(controllerName) {
                            new LinkedHashMap<String, Object>()
                        }

                methods.each {
                    String signature, SwaggerApiMetadata api ->
                        Map<String, Object> entry =
                                (Map<String, Object>) controllerData.computeIfAbsent(signature) {
                                    new LinkedHashMap<String, Object>()
                                }

                        entry['methodName'] = api.methodName
                        entry['httpMethods'] = api.httpMethods
                        entry['mappingPaths'] = api.mappingPaths
                        entry['usage'] = true

                        // Add one editable case to newly discovered endpoints.
                        // Never overwrite or re-seed existing authored case lists.
                        if (!entry.containsKey('cases')) {
                            entry['cases'] = [emptyCase(vietnamese)]
                        } else if (entry['cases'] instanceof List) {
                            // Add the new field even to authored/partially authored cases.
                            // An existing value (including null or empty HTML) is owned
                            // by the author and must never be replaced.
                            String guidance = null
                            (entry['cases'] as List).each { Object rawCase ->
                                if (rawCase instanceof Map &&
                                        !(rawCase as Map).containsKey('detailDescription')) {
                                    if (guidance == null) {
                                        guidance = emptyCase(vietnamese)['detailDescription'] as String
                                    }
                                    (rawCase as Map)['detailDescription'] = guidance
                                }
                            }
                        }
                }
        }

        String serialized = writer.dump(document)
        if (outputFile.isFile() && outputFile.getText('UTF-8') == serialized) {
            return
        }
        if (!swaggerDirectory.isDirectory() && !swaggerDirectory.mkdirs()) {
            throw new IllegalStateException(
                    "Cannot create API cases directory: ${swaggerDirectory.absolutePath}")
        }
        File temp = File.createTempFile('api-cases-', '.tmp', swaggerDirectory)
        try {
            temp.setText(serialized, 'UTF-8')
            try {
                Files.move(temp.toPath(), outputFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING)
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp.toPath(), outputFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temp.exists()) {
                temp.delete()
            }
        }
    }

    // Validate before editing the other Swagger files.
    void validate(File swaggerDirectory) {
        validateDocument(loadDocument(new File(swaggerDirectory, 'api-cases.yml')))
    }

    private static void validateDocument(Map<String, Object> document) {
        document.each { Object controller, Object methods ->
            Map controllerData = requireMap(
                    methods, "Invalid API cases controller: ${controller}")
            controllerData.each { Object method, Object value ->
                validateEntry(value, "${controller}#${method}")
            }
        }
    }

    private static Map<String, Object> loadDocument(File file) {
        if (!file.exists()) {
            return new LinkedHashMap<>()
        }
        Object content = new Yaml().load(file.getText('UTF-8'))
        if (content == null) {
            return new LinkedHashMap<>()
        }
        return (Map<String, Object>) requireMap(content,
                "Invalid API cases document: ${file.absolutePath}")
    }

    private static Map requireMap(Object value, String description) {
        if (!(value instanceof Map)) {
            throw new IllegalStateException(description + ' (expected YAML mapping)')
        }
        return (Map) value
    }

    private static void validateEntry(Object value, String location) {
        Map entry = requireMap(value, "Invalid API cases entry: ${location}")
        if (!entry.containsKey('cases')) {
            return
        }
        if (!(entry['cases'] instanceof List)) {
            throw new IllegalStateException(
                    "Invalid API cases at ${location}: cases must be a list")
        }
        Set<String> usedIds = new LinkedHashSet<>()
        (entry['cases'] as List).each { Object rawCase ->
            Map item = requireMap(rawCase, "Invalid API case at ${location}")
            String id = item['id'] instanceof String
                    ? (item['id'] as String).trim() : ''
            if (id.isEmpty() || !usedIds.add(id)) {
                throw new IllegalStateException(
                        "Missing or duplicate API case id at ${location}: ${id}")
            }
        }
    }

    private static Map<String, Object> emptyCase(boolean vietnamese) {
        return [
                id         : 'case_001',
                title      : vietnamese
                        ? 'TODO: Viết tiêu đề ngắn mô tả tình huống của case này'
                        : 'TODO: Write a short title describing this case scenario',
                description: vietnamese
                        ? 'TODO: Chú thích ngắn mục tiêu, ngữ cảnh và điểm cần quan sát của case này'
                        : 'TODO: Briefly explain this case purpose, context, and what to observe',
                detailDescription: vietnamese
                        ? '<p>TODO: Viết bằng HTML, giải thích vì sao case này có kết quả như trên.</p>'
                        : '<p>TODO: Use HTML to explain why this case produces this result.</p>',
                input      : [
                        pathParams : new LinkedHashMap<>(),
                        queryParams: new LinkedHashMap<>(),
                        headers    : new LinkedHashMap<>(),
                        body       : null
                ],
                status     : null,
                response   : null
        ] as LinkedHashMap<String, Object>
    }
}
