package com.example.learning.setup.module.interview.service

import org.gradle.api.GradleException
import org.yaml.snakeyaml.Yaml


class InterviewQuestionSchemaService {

    private static final String SCHEMA_RESOURCE =
            'interview/question-schema.yml'

    private final Map<String, Object> schema


    InterviewQuestionSchemaService() {
        this.schema = loadSchema()
    }


    String renderCommentBlock(
            String language
    ) {

        String normalizedLanguage = normalizeLanguage(language)

        Map<String, Object> labels =
                requireMap(
                        requireMap(
                                schema.labels,
                                'Interview schema labels must be an object.'
                        )[normalizedLanguage],
                        "Interview schema labels are not configured for language: ${normalizedLanguage}"
                )

        String title =
                localizedText(
                        requireMap(
                                schema.title,
                                'Interview schema title must be an object.'
                        ),
                        normalizedLanguage,
                        'title'
                )

        Map<String, Object> root =
                requireMap(
                        requireMap(
                                schema.document,
                                'Interview schema document must be an object.'
                        ).root,
                        'Interview schema document.root must be an object.'
                )

        List<String> lines = [
                '# <interview-schema>',
                "# ${title}",
                "# schema-version: ${schema.version}",
                '#'
        ]

        appendNodeComment(
                lines,
                root.name?.toString(),
                root,
                normalizedLanguage,
                labels,
                0
        )

        lines.add('# </interview-schema>')
        return lines.join('\n')
    }


    String renderSkeleton(
            String language
    ) {

        Map<String, Object> root = rootSchema()
        String rootName = root.name?.toString()?.trim()

        if (rootName == null || rootName.isBlank()) {
            throw new GradleException(
                    'Interview schema document.root.name must not be blank.'
            )
        }

        if (root.type?.toString() != 'list') {
            throw new GradleException(
                    'Interview skeleton currently requires document.root.type=list.'
            )
        }

        return renderCommentBlock(language) +
                "\n\n${rootName}: []\n"
    }


    Map<String, Object> requiredQuestionFieldDefaults() {

        Map<String, Object> fields =
                requireMap(
                        requireMap(
                                rootSchema().item,
                                'Interview schema document.root.item must be an object.'
                        ).fields,
                        'Interview schema question item must define fields.'
                )

        Map<String, Object> defaults = new LinkedHashMap<>()

        fields.each {
            String fieldName,
            Object rawFieldSchema ->

            Map<String, Object> fieldSchema =
                    requireMap(
                            rawFieldSchema,
                            "Interview schema field '${fieldName}' must be an object."
                    )

            if (!asBoolean(fieldSchema.required)) {
                return
            }

            if (!fieldSchema.containsKey('default')) {
                throw new GradleException(
                        "Interview required field '${fieldName}' must define a default value in ${SCHEMA_RESOURCE}."
                )
            }

            defaults[fieldName] = fieldSchema.default
        }

        return defaults
    }


    List<String> renderYamlDefaultLines(
            String fieldName,
            Object value,
            int indentLevel
    ) {

        String indent = '  ' * indentLevel

        if (value instanceof Map) {
            Map mapValue = value as Map
            if (mapValue.isEmpty()) {
                return ["${indent}${fieldName}: {}"]
            }

            List<String> lines = ["${indent}${fieldName}:"]
            mapValue.each {
                Object childName,
                Object childValue ->

                lines.addAll(
                        renderYamlDefaultLines(
                                childName.toString(),
                                childValue,
                                indentLevel + 1
                        )
                )
            }
            return lines
        }

        return ["${indent}${fieldName}: ${renderYamlDefaultValue(value)}"]
    }


    void validateQuestionFile(
            File file
    ) {

        Object parsed

        try {
            parsed = new Yaml().load(file.getText('UTF-8'))
        }
        catch (Exception exception) {
            throw new GradleException(
                    "Unable to parse Interview YAML: ${file.absolutePath}",
                    exception
            )
        }

        if (!(parsed instanceof Map)) {
            throw new GradleException(
                    "Interview YAML root must be an object: ${file.absolutePath}"
            )
        }

        Map<String, Object> root = rootSchema()
        String rootName = root.name?.toString()?.trim()

        validateNamedNode(
                (parsed as Map)[rootName],
                root,
                rootName,
                (parsed as Map).containsKey(rootName)
        )
    }


    private Map<String, Object> rootSchema() {
        return requireMap(
                requireMap(
                        schema.document,
                        'Interview schema document must be an object.'
                ).root,
                'Interview schema document.root must be an object.'
        )
    }


    private void validateNamedNode(
            Object value,
            Map<String, Object> nodeSchema,
            String path,
            boolean present
    ) {

        if (asBoolean(nodeSchema.required) && !present) {
            throw new GradleException(
                    "Interview schema validation failed: missing required field '${path}'."
            )
        }

        if (!present) {
            return
        }

        validateNode(value, nodeSchema, path)
    }


    private void validateNode(
            Object value,
            Map<String, Object> nodeSchema,
            String path
    ) {

        String type = nodeSchema.type?.toString()?.trim()

        switch (type) {
            case 'string':
                if (!(value instanceof String)) {
                    failType(path, type, value)
                }
                if (asBoolean(nodeSchema.nonBlank) && value.toString().trim().isBlank()) {
                    throw new GradleException(
                            "Interview schema validation failed: '${path}' must not be blank."
                    )
                }
                return

            case 'boolean':
                if (!(value instanceof Boolean)) {
                    failType(path, type, value)
                }
                return

            case 'object':
                if (!(value instanceof Map)) {
                    failType(path, type, value)
                }

                Map<String, Object> fields =
                        requireMap(
                                nodeSchema.fields,
                                "Interview object schema '${path}' must define fields."
                        )

                fields.each {
                    String fieldName,
                    Object rawFieldSchema ->

                    Map<String, Object> fieldSchema =
                            requireMap(
                                    rawFieldSchema,
                                    "Interview schema field '${path}.${fieldName}' must be an object."
                            )

                    Map objectValue = value as Map
                    validateNamedNode(
                            objectValue[fieldName],
                            fieldSchema,
                            "${path}.${fieldName}",
                            objectValue.containsKey(fieldName)
                    )
                }
                return

            case 'list':
                if (!(value instanceof Collection)) {
                    failType(path, type, value)
                }

                if (nodeSchema.item instanceof Map) {
                    Map<String, Object> itemSchema = nodeSchema.item as Map<String, Object>
                    (value as Collection).eachWithIndex {
                        Object item,
                        int index ->

                        validateNode(item, itemSchema, "${path}[${index}]")
                    }
                }
                return

            default:
                throw new GradleException(
                        "Unsupported Interview schema type '${type}' at '${path}'."
                )
        }
    }


    private static void failType(
            String path,
            String expectedType,
            Object value
    ) {
        String actualType = value == null ? 'null' : value.getClass().simpleName
        throw new GradleException(
                "Interview schema validation failed: '${path}' expected ${expectedType}, found ${actualType}."
        )
    }


    private static void appendNodeComment(
            List<String> lines,
            String name,
            Map<String, Object> node,
            String language,
            Map<String, Object> labels,
            int depth
    ) {

        String indent = '  ' * depth
        List<String> metadata = []

        if (node.type != null) {
            metadata.add("${labels.type}: ${node.type}")
        }
        if (asBoolean(node.required)) {
            metadata.add(labels.required.toString())
        }
        if (node.containsKey('default')) {
            metadata.add("${labels.default}: ${renderDefaultForComment(node.default)}")
        }

        lines.add(
                "# ${indent}${name}${metadata.isEmpty() ? '' : ' [' + metadata.join('; ') + ']'}"
        )

        if (node.description instanceof Map) {
            lines.add(
                    "# ${indent}  ${localizedText(node.description as Map, language, name)}"
            )
        }

        if (node.fields instanceof Map) {
            (node.fields as Map).each {
                Object fieldName,
                Object rawField ->

                appendNodeComment(
                        lines,
                        fieldName.toString(),
                        requireMap(
                                rawField,
                                "Interview schema field '${fieldName}' must be an object."
                        ),
                        language,
                        labels,
                        depth + 1
                )
            }
        }

        if (node.item instanceof Map) {
            lines.add("# ${indent}  ${labels.item}:")
            Map<String, Object> item = node.item as Map<String, Object>
            if (item.fields instanceof Map) {
                (item.fields as Map).each {
                    Object fieldName,
                    Object rawField ->

                    appendNodeComment(
                            lines,
                            fieldName.toString(),
                            requireMap(
                                    rawField,
                                    "Interview schema field '${fieldName}' must be an object."
                            ),
                            language,
                            labels,
                            depth + 2
                    )
                }
            }
        }
    }


    private static String renderYamlDefaultValue(
            Object value
    ) {
        if (value == null) {
            return 'null'
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString().toLowerCase(Locale.ROOT)
        }
        if (value instanceof String) {
            String stringValue = value.toString()
            return stringValue.isEmpty()
                    ? '""'
                    : '"' + stringValue.replace('\\', '\\\\').replace('"', '\\"') + '"'
        }
        if (value instanceof Collection && (value as Collection).isEmpty()) {
            return '[]'
        }
        if (value instanceof Map && (value as Map).isEmpty()) {
            return '{}'
        }
        throw new GradleException(
                "Unsupported Interview default value type: ${value.getClass().simpleName}"
        )
    }


    private static String renderDefaultForComment(
            Object value
    ) {
        if (value == null) {
            return 'null'
        }
        if (value instanceof String) {
            return value.toString().isEmpty() ? '""' : value.toString()
        }
        if (value instanceof Collection) {
            return (value as Collection).isEmpty() ? '[]' : value.toString()
        }
        if (value instanceof Map) {
            Map mapValue = value as Map
            if (mapValue.isEmpty()) {
                return '{}'
            }
            return '{' + mapValue.collect {
                Object key,
                Object childValue ->

                "${key}: ${renderDefaultForComment(childValue)}"
            }.join(', ') + '}'
        }
        return value.toString()
    }


    private static String localizedText(
            Map values,
            String language,
            String field
    ) {
        Object value = values[language]
        if (value == null || value.toString().trim().isBlank()) {
            throw new GradleException(
                    "Interview schema localized '${field}' is not configured for language: ${language}"
            )
        }
        return value.toString().trim()
    }


    private static String normalizeLanguage(
            String language
    ) {
        String normalized = language?.trim()?.toLowerCase(Locale.ROOT)
        if (normalized == null || normalized.isBlank()) {
            throw new GradleException(
                    'Interview schema language must not be blank.'
            )
        }
        return normalized
    }


    private static boolean asBoolean(
            Object value
    ) {
        return value != null && value.toString().equalsIgnoreCase('true')
    }


    private static Map<String, Object> loadSchema() {
        InputStream inputStream =
                InterviewQuestionSchemaService.class.classLoader.getResourceAsStream(
                        SCHEMA_RESOURCE
                )

        if (inputStream == null) {
            throw new GradleException(
                    "Interview schema resource was not found: ${SCHEMA_RESOURCE}"
            )
        }

        try {
            return requireMap(
                    new Yaml().load(inputStream),
                    'Interview schema root must be an object.'
            )
        }
        finally {
            inputStream.close()
        }
    }


    private static Map<String, Object> requireMap(
            Object value,
            String message
    ) {
        if (!(value instanceof Map)) {
            throw new GradleException(message)
        }
        return value as Map<String, Object>
    }
}
