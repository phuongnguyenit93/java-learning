package com.example.projectbuild.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DynamicSwaggerRegistrarTest {

    static class SampleHandler {
        public void run() {}
    }

    @Test
    void disabledExecutionContextNeverAnnotatesSwaggerOperations() throws Exception {
        DynamicSwaggerRegistrar registrar = new DynamicSwaggerRegistrar();
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "explicitly-off", Map.of("execution-context.enabled", "false")));
        registrar.setEnvironment(environment);
        Operation operation = new Operation();
        registrar.addExtension().customize(operation,
                new HandlerMethod(new SampleHandler(), SampleHandler.class.getMethod("run")));
        assertFalse(operation.getExtensions() != null &&
                operation.getExtensions().containsKey("x-execution-context-enabled"));
    }

    @Test
    void executionContextMetadataRequiresExplicitRuntimeOptIn() throws Exception {
        HandlerMethod method = new HandlerMethod(new SampleHandler(), SampleHandler.class.getMethod("run"));
        DynamicSwaggerRegistrar registrar = new DynamicSwaggerRegistrar();
        StandardEnvironment environment = new StandardEnvironment();
        registrar.setEnvironment(environment);

        Operation defaultOff = new Operation();
        registrar.addExtension().customize(defaultOff, method);
        assertFalse(defaultOff.getExtensions() != null &&
                defaultOff.getExtensions().containsKey("x-execution-context-enabled"));

        environment.getPropertySources().addFirst(new MapPropertySource(
                "explicitly-on", Map.of("execution-context.enabled", "true")));
        Operation enabled = new Operation();
        registrar.addExtension().customize(enabled, method);
        assertEquals(Boolean.TRUE, enabled.getExtensions().get("x-execution-context-enabled"));
    }

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

    @Test
    void matchesSourceScannerSignatureForNestedDtoWhenUnambiguous() throws Exception {
        JsonNode metadata = yamlMapper.readTree("""
                BasicsExperimentController:
                  simulateAccount(AccountSimulationRequest):
                    value: basic-oop-cases
                  simulateOrder(OrderSimulationRequest):
                    value: order-cases
                """);

        JsonNode result = findMethodNode(
                metadata,
                "BasicsExperimentController",
                "simulateAccount(BasicOopDtos.AccountSimulationRequest)"
        );

        assertNotNull(result);
        assertEquals("basic-oop-cases", result.path("value").asText());
    }

    @Test
    void refusesAmbiguousNestedDtoSimpleNameFallback() throws Exception {
        JsonNode metadata = yamlMapper.readTree("""
                ExampleController:
                  run(FirstDtos.Request):
                    value: first
                  run(SecondDtos.Request):
                    value: second
                """);

        assertNull(findMethodNode(metadata, "ExampleController", "run(ThirdDtos.Request)"));
        assertEquals("first", findMethodNode(
                metadata, "ExampleController", "run(FirstDtos.Request)"
        ).path("value").asText());
    }

    @Test
    void exposesOnlyAuthoredApiCasesAsNativeSwaggerExamples() throws Exception {
        JsonNode cases = yamlMapper.readTree("""
                DemoController:
                  run(Request):
                    usage: true
                    cases:
                      - id: success
                        title: Valid request
                        description: Shows a successful result
                        detailDescription: '<p>Controller validates, then Service compares values.</p><ol><li>Both codes match.</li></ol>'
                        input:
                          body: {firstCode: A, secondCode: A}
                        status: 200
                        response: {valueEquals: true}
                      - id: invalid
                        title: Invalid request
                        input:
                          body: {firstCode: '', secondCode: A}
                        status: 400
                        response: {error: required}
                      - id: unfilled
                        title: ''
                        input: {body: null}
                        status: null
                        response: null
                      - id: partially_filled
                        title: 'TODO: Write a short title describing this case scenario'
                        description: 'TODO: Add a short note'
                        detailDescription: '<p>TODO: Explain code flow in HTML.</p>'
                        input: {body: {firstCode: A, secondCode: A}}
                        status: 200
                        response: {valueEquals: true}
                """);
        Operation operation = new Operation();
        operation.setRequestBody(new RequestBody().content(
                new Content().addMediaType("application/json", new MediaType())));

        Method mapper = DynamicSwaggerRegistrar.class.getDeclaredMethod(
                "mapApiCasesMetadata", Operation.class, JsonNode.class,
                String.class, String.class);
        mapper.setAccessible(true);
        mapper.invoke(new DynamicSwaggerRegistrar(), operation, cases,
                "DemoController", "run(Request)");

        @SuppressWarnings("unchecked")
        var casesExtension =
                (java.util.List<Map<String, Object>>) operation.getExtensions().get("x-api-cases");
        assertEquals(2, casesExtension.size());
        assertEquals(200, casesExtension.get(0).get("status"));
        assertEquals("<p>Controller validates, then Service compares values.</p><ol><li>Both codes match.</li></ol>",
                casesExtension.get(0).get("detailDescription"));
        assertEquals("Shows a successful result", casesExtension.get(0).get("description"));
        assertEquals("A", ((Map<?, ?>) ((Map<?, ?>)
                casesExtension.get(0).get("input")).get("body")).get("firstCode"));

        var requestExamples = operation.getRequestBody().getContent()
                .get("application/json").getExamples();
        assertEquals(2, requestExamples.size());
        assertEquals("Valid request", requestExamples.get("success").getSummary());
        assertEquals("Shows a successful result",
                requestExamples.get("success").getDescription());
        assertNotNull(operation.getResponses().get("200"));
        assertNotNull(operation.getResponses().get("400"));
        assertEquals(1, operation.getResponses().get("400").getContent()
                .get("application/json").getExamples().size());
        assertFalse(operation.getRequestBody().getContent()
                .get("application/json").getExamples().containsKey("unfilled"));
        assertFalse(operation.getRequestBody().getContent()
                .get("application/json").getExamples().containsKey("partially_filled"));
    }

    @Test
    void noAuthoredCasesLeaveSwaggerExamplesUnchanged() throws Exception {
        JsonNode placeholders = yamlMapper.readTree("""
                DemoController:
                  run(Request):
                    cases:
                      - id: case_001
                        title: 'TODO: Write a short descriptive title for this case'
                        description: 'TODO: Add a short annotation'
                        detailDescription: '<p>TODO: Explain actual code flow in HTML.</p>'
                        status: null
                        response: null
                """);
        Operation operation = new Operation();
        Method mapper = DynamicSwaggerRegistrar.class.getDeclaredMethod(
                "mapApiCasesMetadata", Operation.class, JsonNode.class,
                String.class, String.class);
        mapper.setAccessible(true);
        mapper.invoke(new DynamicSwaggerRegistrar(), operation, placeholders,
                "DemoController", "run(Request)");
        assertNull(operation.getExtensions());
        assertNull(operation.getResponses());
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
