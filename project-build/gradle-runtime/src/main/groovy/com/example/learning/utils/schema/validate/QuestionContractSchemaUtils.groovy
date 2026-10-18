package com.example.learning.utils.schema.validate

import com.example.learning.utils.schema.ContractSchemaLoaderUtils
import org.gradle.api.GradleException

/**
 * Adapts the pinned, bundled learning-contract JSON Schema to the internal
 * descriptors used by the existing Quiz/Interview authoring services.
 * No schema is maintained in gradle-runtime/src/main/resources anymore.
 *
 * Fail closed on unsupported authoring constraints: a future contract change
 * must not silently relax validation in the module generators/projectors.
 */
final class QuestionContractSchemaUtils {

    private QuestionContractSchemaUtils() {}

    static Map<String, Object> load(String kind) {
        if (!(kind in ['quiz', 'interview'])) {
            throw new GradleException("Unsupported question contract: ${kind}")
        }
        Map schema = ContractSchemaLoaderUtils.read(kind)
        Map common = ContractSchemaLoaderUtils.read('common')
        if (schema['$schema'] != 'https://json-schema.org/draft/2020-12/schema' ||
                schema.type != 'object' || !(schema.properties instanceof Map) ||
                !((schema.required ?: []) as List).contains('questions')) {
            throw new GradleException("Unexpected ${kind} JSON authoring contract shape.")
        }
        if (!(schema['x-title'] instanceof Map) || !(schema['x-labels'] instanceof Map)) {
            throw new GradleException("Missing localized ${kind} contract metadata.")
        }
        Map<String, Object> questionRoot =
                (schema.properties as Map)['questions'] as Map<String, Object>
        if (!(questionRoot instanceof Map)) {
            throw new GradleException("Missing ${kind} questions list schema.")
        }
        // Comment header versions are independent of wire-format contracts.
        // Preserve current authored file comments while replacing only the
        // source of structural rules.
        return [
                version : kind == 'quiz' ? 3 : 2,
                title   : schema['x-title'],
                labels  : schema['x-labels'],
                document: [root: convert('questions', questionRoot, true, schema, common)]
        ] as Map<String, Object>
    }

    private static Map<String, Object> convert(String name, Map raw,
                                                boolean required, Map schema, Map common) {
        Map node = new LinkedHashMap(raw)
        if (node.containsKey('$ref')) {
            String ref = node['$ref']?.toString()
            Map target
            if (ref.startsWith('./common.schema.json#/$defs/')) {
                target = (common['$defs'] as Map)?.get(
                        ref.substring('./common.schema.json#/$defs/'.length())) as Map
            } else if (ref.startsWith('#/$defs/')) {
                target = (schema['$defs'] as Map)?.get(ref.substring('#/$defs/'.length())) as Map
            } else {
                throw new GradleException("Unsupported question contract reference: ${ref}")
            }
            if (!(target instanceof Map)) {
                throw new GradleException("Unresolved question contract reference: ${ref}")
            }
            node = new LinkedHashMap(target) + node.findAll { k, v -> k != '$ref' }
        }

        String type = node.type?.toString()
        if (!(type in ['array', 'object', 'string', 'boolean'])) {
            throw new GradleException("Unsupported question contract type for ${name}: ${type}")
        }
        Set<String> supported = [
                'type', 'required', 'default', 'description', 'x-description',
                'properties', 'items', 'pattern', 'enum', 'minItems', 'maxItems',
                'x-uniqueBy', 'additionalProperties', 'allOf', 'anyOf'
        ] as Set<String>
        Set unsupported = node.keySet() - supported
        if (!unsupported.isEmpty()) {
            throw new GradleException("Unsupported question contract constraints at ${name}: ${unsupported}")
        }
        if (node.containsKey('additionalProperties') && node.additionalProperties != true) {
            throw new GradleException("Unsupported additionalProperties restriction at ${name}.")
        }

        Map<String, Object> result = [name: name, required: required]
        result.type = type == 'array' ? 'list' : type
        if (node.enum instanceof List) {
            if (type != 'string') {
                throw new GradleException("Unsupported enum type at ${name}.")
            }
            result.type = 'enum'
            result.values = node.enum
        }
        if (node.containsKey('default')) result.default = node.default
        if (node['x-description'] instanceof Map) {
            result.description = node['x-description']
        } else if (node.description instanceof String) {
            result.description = [en: node.description, vi: node.description]
        }
        if (node.containsKey('pattern')) {
            if (node.pattern != '\\S' || type != 'string') {
                throw new GradleException("Unsupported question pattern at ${name}: ${node.pattern}")
            }
            result.nonBlank = true
        }
        if (type == 'array') {
            if (!(node.items instanceof Map)) {
                throw new GradleException("Missing question array item schema at ${name}")
            }
            if (node.containsKey('minItems') || node.containsKey('maxItems')) {
                if (!(node.minItems instanceof Number) || node.minItems != node.maxItems) {
                    throw new GradleException("Unsupported question item-count bounds at ${name}")
                }
                result.size = node.minItems
            }
            if (node.containsKey('x-uniqueBy')) {
                result.uniqueBy = node['x-uniqueBy']
            }
            result.item = convert("${name}[]", node.items as Map, false, schema, common)
            if (node.containsKey('allOf')) {
                // The currently pinned Quiz schema requires one each of A/B/C/D.
                // Four distinct answer IDs from the enum already enforce this.
                verifyExactAnswerSet(node, result, name)
            }
        } else if (type == 'object') {
            Map fields = node.properties as Map
            if (!(fields instanceof Map)) {
                throw new GradleException("Missing question object fields at ${name}")
            }
            List requiredFields = (node.required ?: []) as List
            if (!fields.keySet().containsAll(requiredFields)) {
                throw new GradleException("Unknown required fields at ${name}")
            }
            result.fields = new LinkedHashMap()
            fields.each { String key, Object value ->
                result.fields[key] = convert("${name}.${key}", value as Map,
                        requiredFields.contains(key), schema, common)
            }
            if (node.containsKey('anyOf')) {
                // The current Interview Sheet importer legitimately writes
                // a file-level Knowledge relation with an empty anchor.
                // Preserve legacy validation until those authoring sources
                // can supply exact anchors; validate the contract shape here
                // but do not tighten the existing module-file acceptance.
                verifyOptionalPair(node, name)
            }
        } else if (node.containsKey('anyOf') || node.containsKey('allOf')) {
            throw new GradleException("Unsupported question union at ${name}")
        }
        return result
    }

    private static void verifyExactAnswerSet(Map node, Map converted, String name) {
        List clauses = node.allOf as List
        List allowed = (converted.item.fields.id.values ?: []) as List
        List<String> identities = []
        clauses.each { Object clause ->
            Map c = clause as Map
            String identity = c.contains?.properties?.id?.const?.toString()
            if (c.contains?.type != 'object' || c.contains?.required != ['id'] ||
                    c.minContains != 1 || c.maxContains != 1 || !identity) {
                throw new GradleException("Unsupported exact-answer constraint at ${name}")
            }
            identities.add(identity)
        }
        if (converted.size != allowed.size() || converted.uniqueBy != 'id' ||
                identities.toSet() != allowed.toSet() || identities.size() != allowed.size()) {
            throw new GradleException("Quiz answer identity rules changed at ${name}")
        }
    }

    private static List<String> verifyOptionalPair(Map node, String name) {
        List branches = node.anyOf as List
        if (branches.size() != 2) {
            throw new GradleException("Unsupported optional relation at ${name}")
        }
        Map blank = branches[0]?.properties as Map
        Map filled = branches[1]?.properties as Map
        if (!(blank instanceof Map) || !(filled instanceof Map) ||
                blank.size() != 2 || blank.keySet() != filled.keySet() ||
                !blank.values().every { it.const == '' } ||
                !filled.values().every { it.pattern == '\\S' }) {
            throw new GradleException("Unsupported relation pair constraint at ${name}")
        }
        return blank.keySet().toList()
    }

}
