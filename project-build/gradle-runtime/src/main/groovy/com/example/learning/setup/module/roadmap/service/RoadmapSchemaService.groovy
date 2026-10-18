package com.example.learning.setup.module.roadmap.service

import com.example.learning.utils.schema.ContractSchemaLoaderUtils
import org.gradle.api.GradleException

/**
 * Renders authoring guidance for the Roadmap Sheet importer from the same
 * learning-contract JSON Schema used by validation and the root skeleton.
 * Does not create or synchronize any module-local roadmap.yml files.
 */
class RoadmapSchemaService {
    // Preserve the existing comment format; this is a comment revision, not
    // a separate source of field definitions or validation rules.
    private static final int COMMENT_VERSION = 1

    private final Map schema

    RoadmapSchemaService() {
        schema = ContractSchemaLoaderUtils.read('roadmap')
    }

    String renderCommentBlock(String language) {
        String lang = language?.trim()?.toLowerCase(Locale.ROOT)
        if (!lang) throw new GradleException('Roadmap language must not be blank.')

        Map root = requireMap(schema.properties?.roadmap, 'Roadmap contract is missing the roadmap field.')
        Map item = requireMap(root.items, 'Roadmap contract is missing milestone items.')
        Map fields = requireMap(item.properties, 'Roadmap contract is missing milestone fields.')
        Map guidance = requireMap(schema['x-guidance'], 'Roadmap contract is missing authoring guidance.')
        Map principles = requireMap(guidance.principles, 'Roadmap contract is missing principles.')
        List framework = requireList(guidance.thinkingFramework, 'Roadmap contract is missing thinking framework.')
        if (root.type != 'array' || item.type != 'object' ||
                !((schema.required ?: []) as List).contains('roadmap')) {
            throw new GradleException('Unexpected Roadmap JSON Schema root structure.')
        }

        List<String> lines = [
                '# <roadmap-schema>',
                "# ${localized(schema['x-title'], lang, 'x-title')}",
                "# schema-version: ${COMMENT_VERSION}",
                '#', '# YAML CONTRACT',
                "# roadmap: ${localized(root['x-description'], lang, 'roadmap.x-description')}",
                '#'
        ]

        List requiredFields = (item.required ?: []) as List
        fields.each { String name, Object definition ->
            Map field = requireMap(definition, "Roadmap field '${name}' is not an object.")
            String type = field.type == 'array' ? 'list' : field.type?.toString()
            String required = requiredFields.contains(name) ? 'required' : 'optional'
            lines.add("# - ${name} [${type ?: 'unknown'}, ${required}]: " +
                    localized(field['x-description'], lang, "${name}.x-description"))
        }

        lines.addAll(['#', '# ROADMAP THINKING FRAMEWORK'])
        framework.each { Object rawSection ->
            Map section = requireMap(rawSection, 'Roadmap framework section is not an object.')
            lines.add("# ${localized(section.title, lang, 'thinkingFramework.title')}")
            requireList(section.questions, 'Roadmap framework questions must be a list.').each {
                lines.add("#   - ${localized(it, lang, 'thinkingFramework.question')}")
            }
        }
        lines.add('#')
        ['ownership', 'order', 'flexibility'].each { String name ->
            lines.add("# ${localized(principles[name], lang, "principles.${name}")}")
        }
        lines.add('# </roadmap-schema>')
        return lines.join('\n')
    }

    private static String localized(Object source, String language, String path) {
        Map values = requireMap(source, "Roadmap contract '${path}' must be localized.")
        String value = values[language]?.toString()?.trim()
        if (!value) {
            throw new GradleException("Roadmap contract '${path}' has no text for language '${language}'.")
        }
        return value
    }

    private static Map requireMap(Object value, String error) {
        if (!(value instanceof Map)) throw new GradleException(error)
        return value as Map
    }

    private static List requireList(Object value, String error) {
        if (!(value instanceof List)) throw new GradleException(error)
        return value as List
    }
}
