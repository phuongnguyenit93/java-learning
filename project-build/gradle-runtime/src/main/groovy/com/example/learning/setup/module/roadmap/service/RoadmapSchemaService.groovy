package com.example.learning.setup.module.roadmap.service

import org.gradle.api.GradleException
import org.yaml.snakeyaml.Yaml


class RoadmapSchemaService {

    private static final String SCHEMA_RESOURCE =
            'roadmap/roadmap-schema.yml'


    private final Map<String, Object> schema


    RoadmapSchemaService() {

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


        Map<String, Object> document =
                requireMap(
                        schema.document,
                        'Roadmap schema document must be an object.'
                )


        Map<String, Object> root =
                requireMap(
                        document.root,
                        'Roadmap schema document.root must be an object.'
                )


        Map<String, Object> item =
                requireMap(
                        root.item,
                        'Roadmap schema document.root.item must be an object.'
                )


        Map<String, Object> fields =
                requireMap(
                        item.fields,
                        'Roadmap schema milestone fields must be an object.'
                )


        List<String> lines =
                [
                        '# <roadmap-schema>',
                        "# ${localizedText(schema.title, normalizedLanguage, 'title')}",
                        "# schema-version: ${schema.version}",
                        '#',
                        '# YAML CONTRACT',
                        "# ${root.name}: ${localizedDescription(root, normalizedLanguage)}",
                        '#'
                ]


        fields.each {
            String fieldName,
            Object rawField ->

            Map<String, Object> field =
                    requireMap(
                            rawField,
                            "Roadmap schema field '${fieldName}' must be an object."
                    )


            String required =
                    asBoolean(
                            field.required
                    )
                            ? 'required'
                            : 'optional'


            lines.add(
                    "# - ${fieldName} [${field.type ?: 'unknown'}, ${required}]: ${localizedDescription(field, normalizedLanguage)}"
            )
        }


        lines.addAll(
                [
                        '#',
                        '# ROADMAP THINKING FRAMEWORK'
                ]
        )


        List<Object> framework =
                requireList(
                        schema.thinkingFramework,
                        'Roadmap schema thinkingFramework must be a list.'
                )


        framework.each {
            Object rawSection ->

            Map<String, Object> section =
                    requireMap(
                            rawSection,
                            'Roadmap thinkingFramework item must be an object.'
                    )


            lines.add(
                    "# ${localizedText(section.title, normalizedLanguage, 'thinkingFramework.title')}"
            )


            requireList(
                    section.questions,
                    'Roadmap thinkingFramework questions must be a list.'
            ).each {
                Object rawQuestion ->

                lines.add(
                        "#   - ${localizedText(rawQuestion, normalizedLanguage, 'thinkingFramework.question')}"
                )
            }
        }


        lines.add('#')


        Map<String, Object> principles =
                requireMap(
                        schema.principles,
                        'Roadmap schema principles must be an object.'
                )


        lines.add(
                "# ${localizedText(principles.ownership, normalizedLanguage, 'principles.ownership')}"
        )
        lines.add(
                "# ${localizedText(principles.order, normalizedLanguage, 'principles.order')}"
        )
        lines.add(
                "# ${localizedText(principles.flexibility, normalizedLanguage, 'principles.flexibility')}"
        )
        lines.add('# </roadmap-schema>')


        return lines.join(
                '\n'
        )
    }


    String renderSkeleton(
            String language
    ) {

        Map<String, Object> root =
                requireMap(
                        requireMap(
                                schema.document,
                                'Roadmap schema document must be an object.'
                        ).root,
                        'Roadmap schema document.root must be an object.'
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
                    'Roadmap schema document.root.name must not be blank.'
            )
        }


        if (
                root.type
                        ?.toString() !=
                        'list'
        ) {

            throw new GradleException(
                    'Roadmap skeleton currently requires document.root.type=list.'
            )
        }


        return renderCommentBlock(
                language
        ) +
                "\n\n${rootName}: []\n"
    }


    private Map<String, Object> loadSchema() {

        InputStream inputStream =
                RoadmapSchemaService
                        .classLoader
                        .getResourceAsStream(
                                SCHEMA_RESOURCE
                        )


        if (inputStream == null) {

            throw new GradleException(
                    "Roadmap schema resource was not found: ${SCHEMA_RESOURCE}"
            )
        }


        try {

            Object parsed =
                    new Yaml()
                            .load(
                                    inputStream
                            )


            return requireMap(
                    parsed,
                    'Roadmap schema root must be an object.'
            )
        }
        finally {

            inputStream.close()
        }
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
                    'Roadmap language must not be blank.'
            )
        }


        return normalized
    }


    private static String localizedDescription(
            Map<String, Object> node,
            String language
    ) {

        return localizedText(
                node.description,
                language,
                'description'
        )
    }


    private static String localizedText(
            Object localizedObject,
            String language,
            String fieldName
    ) {

        Map<String, Object> localized =
                requireMap(
                        localizedObject,
                        "Roadmap schema localized field '${fieldName}' must be an object."
                )


        Object value =
                localized[
                        language
                ]


        String text =
                value
                        ?.toString()
                        ?.trim()


        if (
                text == null ||
                        text.isBlank()
        ) {

            throw new GradleException(
                    "Roadmap schema '${fieldName}' is not configured for language: ${language}"
            )
        }


        return text
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


    private static List<Object> requireList(
            Object value,
            String message
    ) {

        if (!(value instanceof Collection)) {

            throw new GradleException(
                    message
            )
        }


        return (value as Collection)
                .toList()
    }


    private static boolean asBoolean(
            Object value
    ) {

        if (value instanceof Boolean) {
            return value as boolean
        }


        return value
                ?.toString()
                ?.equalsIgnoreCase(
                        'true'
                ) ?: false
    }
}
