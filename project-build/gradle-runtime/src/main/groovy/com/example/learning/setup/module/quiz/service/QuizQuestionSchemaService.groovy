package com.example.learning.setup.module.quiz.service

import com.example.learning.utils.schema.validate.QuestionContractSchemaUtils
import com.example.learning.utils.schema.validate.ContractSchemaValidateUtils

import org.gradle.api.GradleException
import org.yaml.snakeyaml.Yaml


class QuizQuestionSchemaService {

    private static final String SCHEMA_RESOURCE =
            'learning-contract/schemas/quiz.schema.json'


    private final Map<String, Object> schema


    QuizQuestionSchemaService() {

        this.schema =
                loadSchema()
    }


    String renderCommentBlock(
            String language
    ) {

        String normalizedLanguage =
                normalizeLanguage(
                        language
                )


        Map<String, Object> labels =
                requireMap(
                        requireMap(
                                schema.labels,
                                'Quiz schema labels must be an object.'
                        )[
                                normalizedLanguage
                        ],
                        "Quiz schema labels are not configured for language: ${normalizedLanguage}"
                )


        String title =
                localizedText(
                        requireMap(
                                schema.title,
                                'Quiz schema title must be an object.'
                        ),
                        normalizedLanguage,
                        'title'
                )


        Map<String, Object> document =
                requireMap(
                        schema.document,
                        'Quiz schema document must be an object.'
                )


        Map<String, Object> root =
                requireMap(
                        document.root,
                        'Quiz schema document.root must be an object.'
                )


        List<String> lines =
                [
                        '# <quiz-schema>',
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


        lines.add(
                '# </quiz-schema>'
        )


        return lines.join('\n')
    }


    String renderSkeleton(
            String language
    ) {

        Map<String, Object> root =
                requireMap(
                        requireMap(
                                schema.document,
                                'Quiz schema document must be an object.'
                        ).root,
                        'Quiz schema document.root must be an object.'
                )


        String rootName =
                root.name
                        ?.toString()
                        ?.trim()


        if (
                rootName == null ||
                        rootName.isBlank()
        ) {

            throw new GradleException(
                    'Quiz schema document.root.name must not be blank.'
            )
        }


        if (
                root.type
                        ?.toString() !=
                        'list'
        ) {

            throw new GradleException(
                    'Quiz skeleton currently requires document.root.type=list.'
            )
        }


        return renderCommentBlock(
                language
        ) +
                "\n\n${rootName}: []\n"
    }


    Map<String, Object> requiredQuestionFieldDefaults() {

        Map<String, Object> root =
                requireMap(
                        requireMap(
                                schema.document,
                                'Quiz schema document must be an object.'
                        ).root,
                        'Quiz schema document.root must be an object.'
                )


        Map<String, Object> item =
                requireMap(
                        root.item,
                        'Quiz schema document.root.item must be an object.'
                )


        Map<String, Object> fields =
                requireMap(
                        item.fields,
                        'Quiz schema question item must define fields.'
                )


        Map<String, Object> defaults =
                new LinkedHashMap<>()


        fields.each {
            String fieldName,
            Object rawFieldSchema ->

            Map<String, Object> fieldSchema =
                    requireMap(
                            rawFieldSchema,
                            "Quiz schema field '${fieldName}' must be an object."
                    )


            if (!asBoolean(fieldSchema.required)) {
                return
            }


            if (!fieldSchema.containsKey('default')) {

                throw new GradleException(
                        "Quiz required field '${fieldName}' must define a default value in ${SCHEMA_RESOURCE}."
                )
            }


            defaults[
                    fieldName
            ] = fieldSchema.default
        }


        return defaults
    }


    String renderYamlDefaultValue(
            Object value
    ) {

        if (value == null) {
            return 'null'
        }


        if (value instanceof Boolean || value instanceof Number) {
            return value.toString().toLowerCase(Locale.ROOT)
        }


        if (value instanceof String) {

            String stringValue =
                    value.toString()


            if (stringValue.isEmpty()) {
                return '""'
            }


            return '"' +
                    stringValue
                            .replace('\\', '\\\\')
                            .replace('"', '\\"') +
                    '"'
        }


        if (value instanceof Collection) {

            if ((value as Collection).isEmpty()) {
                return '[]'
            }
        }


        if (value instanceof Map) {

            if ((value as Map).isEmpty()) {
                return '{}'
            }
        }


        throw new GradleException(
                "Unsupported Quiz default value type: ${value.getClass().simpleName}"
        )
    }


    List<String> renderYamlDefaultLines(
            String fieldName,
            Object value,
            int indentLevel
    ) {

        String indent =
                '  ' * indentLevel


        if (value instanceof Map) {

            Map mapValue =
                    value as Map


            if (mapValue.isEmpty()) {
                return [
                        "${indent}${fieldName}: {}"
                ]
            }


            List<String> lines =
                    [
                            "${indent}${fieldName}:"
                    ]


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


        return [
                "${indent}${fieldName}: ${renderYamlDefaultValue(value)}"
        ]
    }


    void validateQuestionFile(File file) {
        Object parsed
        try {
            parsed = new Yaml().load(file.getText('UTF-8'))
        } catch (Exception error) {
            throw new GradleException("Unable to parse Quiz YAML: ${file.absolutePath}", error)
        }
        if (!(parsed instanceof Map)) {
            throw new GradleException("Quiz YAML root must be an object: ${file.absolutePath}")
        }
        ContractSchemaValidateUtils.validateData('quiz', parsed,
                ContractSchemaValidateUtils.LEGACY_QUESTION_LINKS)
    }

    private static void appendNodeComment(
            List<String> lines,
            String name,
            Map<String, Object> node,
            String language,
            Map<String, Object> labels,
            int depth
    ) {

        String indent =
                '  ' * depth


        List<String> metadata = []


        if (node.type != null) {
            metadata.add(
                    "${labels.type}: ${node.type}"
            )
        }


        if (asBoolean(node.required)) {
            metadata.add(
                    labels.required.toString()
            )
        }


        if (node.size != null) {
            metadata.add(
                    "${labels.size}: ${node.size}"
            )
        }


        if (node.values instanceof Collection) {
            metadata.add(
                    "${labels.values}: ${(node.values as Collection).join(', ')}"
            )
        }


        if (node.containsKey('default')) {
            metadata.add(
                    "${labels.default}: ${renderDefaultForComment(node.default)}"
            )
        }


        if (
                node.uniqueBy != null &&
                        !node.uniqueBy.toString().isBlank()
        ) {
            metadata.add(
                    "${labels.uniqueBy}: ${node.uniqueBy}"
            )
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
                                "Quiz schema field '${fieldName}' must be an object."
                        ),
                        language,
                        labels,
                        depth + 1
                )
            }
        }


        if (node.item instanceof Map) {

            lines.add(
                    "# ${indent}  ${labels.item}:"
            )


            Map<String, Object> item =
                    node.item as Map<String, Object>


            if (item.fields instanceof Map) {

                (item.fields as Map).each {
                    Object fieldName,
                    Object rawField ->

                    appendNodeComment(
                            lines,
                            fieldName.toString(),
                            requireMap(
                                    rawField,
                                    "Quiz schema field '${fieldName}' must be an object."
                            ),
                            language,
                            labels,
                            depth + 2
                    )
                }
            }
        }
    }


    private static String renderDefaultForComment(
            Object value
    ) {

        if (value == null) {
            return 'null'
        }


        if (value instanceof String) {
            return value.toString().isEmpty()
                    ? '""'
                    : value.toString()
        }


        if (value instanceof Collection) {
            return (value as Collection).isEmpty()
                    ? '[]'
                    : value.toString()
        }


        if (value instanceof Map) {

            Map mapValue =
                    value as Map


            if (mapValue.isEmpty()) {
                return '{}'
            }


            return '{' +
                    mapValue.collect {
                        Object key,
                        Object childValue ->

                        "${key}: ${renderDefaultForComment(childValue)}"
                    }.join(', ') +
                    '}'
        }


        return value.toString()
    }


    private static String localizedText(
            Map values,
            String language,
            String field
    ) {

        Object value =
                values[
                        language
                ]


        if (
                value == null ||
                        value.toString().trim().isBlank()
        ) {

            throw new GradleException(
                    "Quiz schema localized '${field}' is not configured for language: ${language}"
            )
        }


        return value.toString().trim()
    }


    private static boolean asBoolean(
            Object value
    ) {

        return value != null &&
                value.toString().equalsIgnoreCase(
                        'true'
                )
    }


    private static String normalizeLanguage(
            String language
    ) {

        String normalized =
                language
                        ?.trim()
                        ?.toLowerCase(
                                Locale.ROOT
                        )


        if (
                normalized == null ||
                        normalized.isBlank()
        ) {

            throw new GradleException(
                    'Quiz schema language must not be blank.'
            )
        }


        return normalized
    }


    private static Map<String, Object> loadSchema() {
        return QuestionContractSchemaUtils.load('quiz')
    }

    private static Map<String, Object> requireMap(
            Object value,
            String message
    ) {

        if (!(value instanceof Map)) {
            throw new GradleException(
                    message
            )
        }


        return value as Map<String, Object>
    }
}
