package com.example.learning.utils.schema.validate

import com.example.learning.utils.schema.ContractSchemaLoaderUtils
import groovy.json.JsonOutput
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.Schema
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import org.gradle.api.GradleException

/** JSON Schema Draft 2020-12 validation for authored and published documents. */
final class ContractSchemaValidateUtils {
    static final String STRICT = 'STRICT'
    static final String LEGACY_QUESTION_LINKS = 'LEGACY_QUESTION_LINKS'
    private static final ObjectMapper JSON = new ObjectMapper()
    private static final SchemaRegistry REGISTRY =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
    private static final Map<String, Schema> CACHE = new java.util.concurrent.ConcurrentHashMap<>()

    private ContractSchemaValidateUtils() {}

    static void validateSchema(String name) {
        Map definition = ContractSchemaLoaderUtils.read(name)
        // Validate the contract itself against the official 2020-12 meta
        // schema, not merely that it parses or compiles successfully.
        Schema metaSchema = REGISTRY.getSchema(
                SchemaLocation.of('https://json-schema.org/draft/2020-12/schema'))
        def errors = metaSchema.validate(JSON.readTree(JsonOutput.toJson(definition)))
        if (errors) {
            throw new GradleException("Invalid ${name} JSON Schema: " +
                    errors.take(8).collect { "${it.instanceLocation}: ${it.message}" }.join(' | '))
        }
        compiled(name, STRICT)
    }

    static void validateData(String name, Object document, String mode = STRICT) {
        if (!(mode in [STRICT, LEGACY_QUESTION_LINKS])) {
            throw new GradleException("Unsupported contract validation mode: ${mode}")
        }
        if (mode == LEGACY_QUESTION_LINKS && !(name in ['quiz', 'interview'])) {
            throw new GradleException('Legacy link compatibility is only for Quiz and Interview')
        }
        Schema schema = compiled(name, mode)
        // Groovy's GString is not serialized by Jackson valueToTree as a
        // JSON string. Normalize Groovy authoring maps through JsonOutput.
        def violations = schema.validate(JSON.readTree(JsonOutput.toJson(document)))
        if (violations) {
            throw new GradleException(
                    "${name} JSON Schema validation failed: " +
                    violations.take(8).collect { error ->
                        "${error.instanceLocation}: ${error.message}"
                    }.join(' | '))
        }
        SchemaExtensionValidateUtils.validate(document, ContractSchemaLoaderUtils.read(name))
    }

    private static Schema compiled(String name, String mode) {
        String key = "${name}:${mode}"
        return CACHE.computeIfAbsent(key) { String ignored ->
            String path = ContractSchemaLoaderUtils.resourcePath(name)
            Map data = ContractSchemaLoaderUtils.read(name)
            if (mode == LEGACY_QUESTION_LINKS) {
                Map common = ContractSchemaLoaderUtils.read('common')
                // Preserve legacy accepted file-only Knowledge/API references.
                // Full strict contract validation remains the default elsewhere.
                Map properties = data.properties.questions.items.properties as Map
                ['readmeRelated': 'readmeRelation', 'apiRelated': 'apiRelation'].each {
                    String field, String definition ->
                    Map resolved = new LinkedHashMap(common['$defs'][definition] as Map)
                    resolved.remove('anyOf')
                    properties[field] = resolved
                }
            }
            // Supplying an absolute classpath schema location resolves relative
            // common.schema.json references without network or checkout access.
            return REGISTRY.getSchema(
                    SchemaLocation.of("classpath:${path}"),
                    JSON.valueToTree(data)
            )
        }
    }
}
