package com.example.projectbuild.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DynamicSwaggerRegistrarTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Test
    void findsExactSignatureBeforeNormalizedCompatibilityMatch() throws Exception {
        JsonNode metadata = yamlMapper.readTree("""
                ExampleController:
                  run(java.lang.String):
                    value: qualified
                  run(String):
                    value: exact
                """);

        JsonNode result = findMethodNode(
                metadata,
                "ExampleController",
                "run(String)"
        );

        assertEquals("exact", result.path("value").asText());
    }

    @Test
    void refusesAmbiguousNormalizedSignatureMatch() throws Exception {
        JsonNode metadata = yamlMapper.readTree("""
                ExampleController:
                  run(java.lang.String):
                    value: first
                  run(com.acme.String):
                    value: second
                """);

        assertNull(
                findMethodNode(
                        metadata,
                        "ExampleController",
                        "run(String)"
                )
        );
    }

    private static JsonNode findMethodNode(
            JsonNode metadata,
            String controller,
            String signature
    ) throws Exception {
        DynamicSwaggerRegistrar registrar = new DynamicSwaggerRegistrar();
        Method method = DynamicSwaggerRegistrar.class.getDeclaredMethod(
                "findMethodNode",
                JsonNode.class,
                String.class,
                String.class
        );
        method.setAccessible(true);
        return (JsonNode) method.invoke(
                registrar,
                metadata,
                controller,
                signature
        );
    }
}
