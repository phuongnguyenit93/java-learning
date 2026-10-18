package com.example.projectbuild.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DynamicSwaggerRegistrarTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Test
    void usesServiceNameForSwaggerInfo() throws Exception {
        DynamicSwaggerRegistrar registrar = new DynamicSwaggerRegistrar();
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("test", Map.of("SERVICE_NAME", "EXAMPLE_SERVICE"))
        );
        registrar.setEnvironment(environment);

        Method factory = DynamicSwaggerRegistrar.class.getDeclaredMethod(
                "customerGlobalOpenApiCustomizer", String.class
        );
        factory.setAccessible(true);
        OpenApiCustomizer customizer =
                (OpenApiCustomizer) factory.invoke(registrar, "vi");

        OpenAPI openApi = new OpenAPI();
        Operation operation = new Operation().addTagsItem("ExampleController");
        operation.addExtension("x-controller-name", "ExampleController");
        operation.addExtension("x-method-signature", "run()");
        openApi.setPaths(
                new Paths().addPathItem("/sample", new PathItem().get(operation))
        );
        customizer.customise(openApi);

        assertEquals("EXAMPLE_SERVICE", openApi.getInfo().getDescription());
    }

    @Test
    void retainsApiDescriptionsYoutubeAndExecutionMetadata() throws Exception {
        DynamicSwaggerRegistrar registrar = new DynamicSwaggerRegistrar();
        JsonNode descriptions = yamlMapper.readTree("""
                DemoController:
                  run():
                    summary: Demo summary
                    description: Demo explanation
                    enableVideoYoutube: true
                    videoYoutubeId: dQw4w9WgXcQ
                    videoYoutubeTitle: Video guide
                """);
        JsonNode executions = yamlMapper.readTree("""
                DemoController:
                  run():
                    execution: <div>Execute demo</div>
                """);
        Operation operation = new Operation();

        Method mapDescriptions = DynamicSwaggerRegistrar.class.getDeclaredMethod(
                "mapMethodMetadata",
                Operation.class, JsonNode.class, String.class, String.class
        );
        mapDescriptions.setAccessible(true);
        mapDescriptions.invoke(registrar, operation, descriptions, "DemoController", "run()");

        assertEquals("Demo summary", operation.getSummary());
        assertEquals("Demo explanation", operation.getDescription());
        @SuppressWarnings("unchecked")
        Map<String, Object> video =
                (Map<String, Object>) operation.getExtensions().get("x-youtube");
        assertEquals("dQw4w9WgXcQ", video.get("videoId"));
        assertEquals("Video guide", video.get("title"));

        Method mapExecution = DynamicSwaggerRegistrar.class.getDeclaredMethod(
                "mapExecutionMetadata",
                Operation.class, JsonNode.class, String.class, String.class
        );
        mapExecution.setAccessible(true);
        mapExecution.invoke(registrar, operation, executions, "DemoController", "run()");
        assertEquals(
                "<div>Execute demo</div>",
                operation.getExtensions().get("x-api-execution-html")
        );

        Method mapParameter = DynamicSwaggerRegistrar.class.getDeclaredMethod(
                "mapParameterMetadata", Parameter.class, JsonNode.class
        );
        mapParameter.setAccessible(true);
        Parameter parameter = new Parameter().name("request");
        mapParameter.invoke(registrar, parameter, yamlMapper.readTree("""
                request:
                  summary: Parameter summary
                  description: Parameter explanation
                """));
        assertEquals(
                "**Parameter summary**: Parameter explanation",
                parameter.getDescription()
        );
    }

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
