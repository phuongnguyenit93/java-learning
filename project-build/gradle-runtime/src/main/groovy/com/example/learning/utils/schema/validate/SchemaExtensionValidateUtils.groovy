package com.example.learning.utils.schema.validate

import org.gradle.api.GradleException

/** Validates learning-contract's non-standard x-uniqueBy annotation. */
final class SchemaExtensionValidateUtils {
    private SchemaExtensionValidateUtils() {}

    static void validate(Object data, Map schema, String path = '$') {
        if (schema['x-uniqueBy'] != null && data instanceof Collection) {
            String field = schema['x-uniqueBy'].toString()
            Set seen = new HashSet()
            (data as Collection).eachWithIndex { item, int i ->
                if (!(item instanceof Map) || !(item as Map).containsKey(field) ||
                        (item as Map)[field] == null || !seen.add((item as Map)[field])) {
                    throw new GradleException(
                            "Schema x-uniqueBy violation at ${path}[${i}]: '${field}' must be present and unique")
                }
            }
        }
        if (data instanceof Map && schema.properties instanceof Map) {
            (schema.properties as Map).each { String key, Object value ->
                if ((data as Map).containsKey(key)) {
                    validate((data as Map)[key], value as Map, "${path}.${key}")
                }
            }
        } else if (data instanceof Collection && schema.items instanceof Map) {
            (data as Collection).eachWithIndex { Object value, int i ->
                validate(value, schema.items as Map, "${path}[${i}]")
            }
        }
    }
}
