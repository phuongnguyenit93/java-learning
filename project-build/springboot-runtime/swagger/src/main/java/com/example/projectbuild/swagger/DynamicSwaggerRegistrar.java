package com.example.projectbuild.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class DynamicSwaggerRegistrar implements ImportBeanDefinitionRegistrar , EnvironmentAware, ResourceLoaderAware {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DynamicSwaggerRegistrar.class);

    private Environment environment;
    private ResourceLoader resourceLoader;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private static final Pattern YOUTUBE_VIDEO_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final String EXECUTION_CONTEXT_SERVICE_CLASS =
            "com.example.projectbuild.executioncontext.service.ExecutionContextService";

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,BeanDefinitionRegistry registry) throws BeansException {
        String languages = environment.getProperty("swagger.languages", "");

        if (languages.isBlank()) return;
        String[] langArray = languages.split(",");

        for (String lang : langArray) {
            String trimmedLang = lang.trim();
            if (trimmedLang.isEmpty()) continue;
            String beanName = "customApiGroup_" + trimmedLang;

            // Tạo Definition cho Bean GroupedOpenApi
            GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
            beanDefinition.setBeanClass(GroupedOpenApi.class);

            // Sử dụng Supplier để khởi tạo logic phức tạp
            beanDefinition.setInstanceSupplier(() ->
                    GroupedOpenApi.builder()
                        .group(trimmedLang)
                        .displayName("Ngôn ngữ: " + trimmedLang.toUpperCase())
                        .pathsToMatch("/**")
                        .pathsToExclude("/execution-context/**")
                        .addOperationCustomizer(addExtension())
                        .addOpenApiCustomizer(customerGlobalOpenApiCustomizer(trimmedLang))
                        .build()
            );

            registry.registerBeanDefinition(beanName, beanDefinition);
        }
    }

    public OperationCustomizer addExtension() {
        return (operation, handlerMethod) -> {

            String controllerName =
                    handlerMethod.getBeanType().getSimpleName();

            operation.addExtension(
                    "x-controller-name",
                    controllerName
            );

            operation.addExtension(
                    "x-method-signature",
                    MethodSignatureResolver.resolve(
                            handlerMethod.getMethod()
                    )
            );

            if (ClassUtils.isPresent(
                    EXECUTION_CONTEXT_SERVICE_CLASS,
                    handlerMethod.getBeanType().getClassLoader()
            )) {
                operation.addExtension(
                        "x-execution-context-enabled",
                        true
                );
            }

            return operation;
        };
    }

    private OpenApiCustomizer customerGlobalOpenApiCustomizer(String language) {
        return openApi -> {
            if (openApi.getInfo() == null) {
                openApi.setInfo(new Info());
            }
            openApi.getInfo().setDescription(loadServiceName());

            // Preserve descriptions, execution metadata, parameter annotations
            // and embedded YouTube videos.
            JsonNode apiDescs = loadYamlResource("swagger/" + language + "/api-descriptions.yml");
            JsonNode apiExecutions = loadYamlResource("swagger/" + language + "/api-execution.yml");
            JsonNode apiParams = loadYamlResource("swagger/" + language + "/api-params.yml");
            JsonNode apiCases = loadYamlResource("swagger/" + language + "/api-cases.yml");
            JsonNode controllerDescs = loadYamlResource("swagger/" + language + "/controller-description.yml");
            Map<String, String> controllerByTag = new LinkedHashMap<>();

            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().forEach((path, pathItem) -> {
                pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                    String controllerName = operation.getExtensions() == null
                            ? null
                            : (String) operation.getExtensions().get("x-controller-name");
                    String methodSignature = operation.getExtensions() == null
                            ? null
                            : (String) operation.getExtensions().get("x-method-signature");

                    if (operation.getTags() != null) {
                        operation.getTags().forEach(tagName -> {
                            Tag tag = findOrCreateTag(openApi, tagName, controllerName);
                            if (controllerName != null && !controllerName.isBlank()) {
                                controllerByTag.putIfAbsent(tag.getName(), controllerName);
                            }
                        });
                    }

                    mapMethodMetadata(operation, apiDescs, controllerName, methodSignature);
                    mapExecutionMetadata(operation, apiExecutions, controllerName, methodSignature);
                    mapApiCasesMetadata(operation, apiCases, controllerName, methodSignature);
                    if (operation.getParameters() != null) {
                        operation.getParameters().forEach(parameter ->
                                mapParameterMetadata(parameter, apiParams));
                    }
                });
            });

            if (openApi.getTags() == null) {
                return;
            }
            openApi.getTags().forEach(tag -> {
                String controllerName = controllerByTag.get(tag.getName());
                if (controllerName == null || controllerName.isBlank()) {
                    return;
                }

                tag.setDescription(controllerName);
                JsonNode controllerNode = controllerDescs.get(controllerName);
                if (controllerNode == null) {
                    return;
                }
                JsonNode descriptionNode = controllerNode.get("description");
                if (descriptionNode != null) {
                    tag.addExtension("x-custom-html", descriptionNode.asText());
                }
            });
        };
    }

    private Tag findOrCreateTag(
            io.swagger.v3.oas.models.OpenAPI openApi,
            String tagName,
            String controllerName
    ) {

        List<Tag> tags = openApi.getTags();

        if (tags == null) {
            tags = new ArrayList<>();
            openApi.setTags(tags);
        }

        List<Tag> resolvedTags = tags;

        return resolvedTags
                .stream()
                .filter(tag -> tagName.equals(tag.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Tag newTag = new Tag();
                    newTag.setName(tagName);

                    if (controllerName != null &&
                            !controllerName.isBlank()) {
                        newTag.setDescription(controllerName);
                    }

                    resolvedTags.add(newTag);

                    return newTag;
                });
    }

    private String loadServiceName() {
        // SERVICE_NAME is a Gradle module property, not guaranteed to be in
        // Spring's runtime Environment. The module build packages its value.
        ClassPathResource resource =
                new ClassPathResource("META-INF/swagger/service-name.txt");
        if (resource.exists()) {
            try {
                String name = resource.getContentAsString(StandardCharsets.UTF_8).trim();
                if (!name.isEmpty()) {
                    return name;
                }
            } catch (IOException exception) {
                LOGGER.warn("[SWAGGER] Cannot read packaged SERVICE_NAME", exception);
            }
        }

        String fromEnvironment = environment.getProperty("SERVICE_NAME", "");
        if (!fromEnvironment.isBlank()) {
            return fromEnvironment.trim();
        }
        return environment.getProperty("spring.application.name", "Service");
    }

    private void mapMethodMetadata(
            Operation operation,
            JsonNode apiDescs,
            String controller,
            String methodSignature
    ) {

        JsonNode methodNode =
                findMethodNode(
                        apiDescs,
                        controller,
                        methodSignature
                );

        if (methodNode == null) {
            return;
        }

        // ==========================================
        // Summary & Description
        // ==========================================

        String summary =
                methodNode.path("summary").asText(null);

        String description =
                methodNode.path("description").asText(null);

        if (summary != null) {
            operation.setSummary(summary);
        }

        if (description != null) {
            operation.setDescription(description);
        }

        // ==========================================
        // Youtube
        // ==========================================


        boolean enableVideoYoutube =
                methodNode
                        .path("enableVideoYoutube")
                        .asBoolean(false);

        if (!enableVideoYoutube) {
            return;
        }

        String videoYoutubeId =
                methodNode
                        .path("videoYoutubeId")
                        .asText(null);

        String videoYoutubeTitle =
                methodNode
                        .path("videoYoutubeTitle")
                        .asText(null);



        if (!isValidYoutubeVideoIdFormat(videoYoutubeId)) {
            return;
        }

        Map<String, Object> youtubeExtension =
                new LinkedHashMap<>();

        youtubeExtension.put(
                "videoId",
                videoYoutubeId
        );

        if (videoYoutubeTitle != null &&
                !videoYoutubeTitle.isBlank()) {

            youtubeExtension.put(
                    "title",
                    videoYoutubeTitle
            );
        }

        operation.addExtension(
                "x-youtube",
                youtubeExtension
        );
    }

    private void mapExecutionMetadata(
            Operation operation,
            JsonNode apiExecutions,
            String controller,
            String methodSignature
    ) {
        JsonNode methodNode =
                findMethodNode(
                        apiExecutions,
                        controller,
                        methodSignature
                );

        if (methodNode == null) {
            return;
        }

        JsonNode executionNode = methodNode.get("execution");
        if (executionNode == null || executionNode.isNull()) {
            return;
        }

        operation.addExtension(
                "x-api-execution-html",
                executionNode.asText()
        );
    }

    /**
     * A case is an authored, verified contract, not a guessed example from a DTO.
     * OpenAPI's native named examples populate the Swagger UI request dropdown;
     * the extension keeps the request and expected outcome together for learners.
     * Incomplete generator placeholders are deliberately invisible.
     */
    private void mapApiCasesMetadata(
            Operation operation,
            JsonNode apiCases,
            String controller,
            String methodSignature
    ) {
        JsonNode endpoint = findMethodNode(apiCases, controller, methodSignature);
        if (endpoint == null || !endpoint.path("usage").asBoolean(true)) {
            return;
        }
        JsonNode authoredCases = endpoint.path("cases");
        if (!authoredCases.isArray()) {
            return;
        }

        List<Map<String, Object>> shown = new ArrayList<>();
        for (JsonNode item : authoredCases) {
            String id = item.path("id").asText("").trim();
            String title = item.path("title").asText("").trim();
            JsonNode statusNode = item.path("status");
            if (id.isBlank() || title.isBlank() || title.startsWith("TODO:")
                    || !statusNode.canConvertToInt()) {
                continue;
            }
            int status = statusNode.asInt();
            if (status < 100 || status > 599) {
                continue;
            }
            String description = item.path("description").asText("");
            // Detailed HTML teaching explanation is separate from the short
            // description used by Swagger's native request/response examples.
            String detailDescription = item.path("detailDescription").asText("");
            // Instructional YAML defaults must never leak into public examples
            // if an author fills the HTTP status before writing the explanation.
            if (description.trim().startsWith("TODO:")
                    || detailDescription.trim().startsWith("<p>TODO:")) {
                continue;
            }
            JsonNode inputNode = item.path("input");
            JsonNode bodyNode = inputNode.path("body");
            JsonNode responseNode = item.path("response");

            Map<String, Object> exampleCase = new LinkedHashMap<>();
            exampleCase.put("id", id);
            exampleCase.put("title", title);
            exampleCase.put("description", description);
            exampleCase.put("detailDescription", detailDescription);
            exampleCase.put("status", status);
            exampleCase.put("input", inputNode.isObject()
                    ? yamlMapper.convertValue(inputNode, Object.class)
                    : Map.of());
            exampleCase.put("response", responseNode.isMissingNode()
                    ? null : yamlMapper.convertValue(responseNode, Object.class));
            shown.add(exampleCase);

            if (!bodyNode.isMissingNode() && !bodyNode.isNull()
                    && operation.getRequestBody() != null
                    && operation.getRequestBody().getContent() != null) {
                operation.getRequestBody().getContent().forEach((mediaTypeName, mediaType) -> {
                    if (isJsonMediaType(mediaTypeName)) {
                        mediaType.addExamples(id, new Example()
                                .summary(title)
                                .description(description)
                                .value(yamlMapper.convertValue(bodyNode, Object.class)));
                    }
                });
            }

            ApiResponses responses = operation.getResponses();
            if (responses == null) {
                responses = new ApiResponses();
                operation.setResponses(responses);
            }
            String statusCode = Integer.toString(status);
            ApiResponse response = responses.get(statusCode);
            if (response == null) {
                response = new ApiResponse().description(title);
                responses.addApiResponse(statusCode, response);
            }
            if (!responseNode.isMissingNode() && !responseNode.isNull()) {
                Content content = response.getContent();
                if (content == null) {
                    content = new Content();
                    response.setContent(content);
                }
                MediaType json = content.get("application/json");
                if (json == null) {
                    json = new MediaType();
                    content.addMediaType("application/json", json);
                }
                json.addExamples(id, new Example()
                        .summary(title)
                        .description(description)
                        .value(yamlMapper.convertValue(responseNode, Object.class)));
            }
        }
        if (!shown.isEmpty()) {
            operation.addExtension("x-api-cases", shown);
        }
    }

    private static boolean isJsonMediaType(String mediaType) {
        String type = mediaType.toLowerCase(java.util.Locale.ROOT);
        return type.equals("application/json") || type.endsWith("+json");
    }

    private void mapParameterMetadata(Parameter parameter, JsonNode apiParams) {
        // Đường dẫn trong YAML: moduleName -> ParameterName
        JsonNode paramNode = apiParams.path(parameter.getName());

        if (!paramNode.isMissingNode()) {
            String summary = paramNode.path("summary").asText("");
            String description = paramNode.path("description").asText("");

            // Vì Swagger Parameter chỉ có field 'description',
            // ta nối Summary và Description lại để hiển thị đầy đủ thông tin.
            StringBuilder fullDesc = new StringBuilder();
            if (!summary.isEmpty()) {
                fullDesc.append("**").append(summary).append("**: ");
            }
            fullDesc.append(description);

            parameter.setDescription(fullDesc.toString());
        }
    }

    private JsonNode loadYamlResource(String fileName) {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + fileName);
            if (!resource.exists()) return yamlMapper.createObjectNode();
            try (InputStream is = resource.getInputStream()) {
                return yamlMapper.readTree(is);
            }
        } catch (Exception e) {
            return yamlMapper.createObjectNode();
        }
    }

    private boolean isValidYoutubeVideoIdFormat(
            String videoYoutubeId
    ) {

        if (videoYoutubeId == null ||
                videoYoutubeId.isBlank()) {

            return false;
        }

        return YOUTUBE_VIDEO_ID_PATTERN
                .matcher(videoYoutubeId.trim())
                .matches();
    }

    private JsonNode findMethodNode(
            JsonNode metadata,
            String controller,
            String methodSignature
    ) {

        if (controller == null ||
                controller.isBlank() ||
                methodSignature == null ||
                methodSignature.isBlank()) {
            return null;
        }

        JsonNode controllerNode =
                metadata.path(controller);

        if (controllerNode.isMissingNode() ||
                !controllerNode.isObject()) {
            return null;
        }

        JsonNode methodNode = controllerNode.get(methodSignature);
        if (methodNode != null && !methodNode.isMissingNode()) {
            return methodNode;
        }

        String normalizedSignature =
                MethodSignatureResolver.normalize(methodSignature);

        JsonNode normalizedMatch = null;
        Iterator<Map.Entry<String, JsonNode>> fields =
                controllerNode.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();

            if (!normalizedSignature.equals(
                    MethodSignatureResolver.normalize(field.getKey())
            )) {
                continue;
            }

            if (normalizedMatch != null) {
                return null;
            }

            normalizedMatch = field.getValue();
        }

        if (normalizedMatch != null) {
            return normalizedMatch;
        }

        // Java reflection includes the enclosing DTO type for nested records,
        // e.g. "simulateAccount(BasicOopDtos.AccountSimulationRequest)".
        // The source scanner may instead record "simulateAccount(AccountSimulationRequest)".
        // Fall back to the innermost type name only when the match is UNIQUE.
        // Exact / fully normalized signatures always take precedence above.
        String simpleSignature = simplifyNestedTypeNames(normalizedSignature);
        JsonNode simpleMatch = null;
        Iterator<Map.Entry<String, JsonNode>> candidates = controllerNode.fields();
        while (candidates.hasNext()) {
            Map.Entry<String, JsonNode> candidate = candidates.next();
            String candidateSignature = simplifyNestedTypeNames(
                    MethodSignatureResolver.normalize(candidate.getKey()));
            if (!simpleSignature.equals(candidateSignature)) {
                continue;
            }
            if (simpleMatch != null) {
                // Two distinct nested DTOs have the same innermost name.
                // Refuse to guess which endpoint owns the metadata.
                return null;
            }
            simpleMatch = candidate.getValue();
        }
        return simpleMatch;
    }

    private static String simplifyNestedTypeNames(String signature) {
        return signature.replaceAll(
                "(?<![\\w$])(?:[A-Z_$][\\w$]*\\.)+([A-Z_$][\\w$]*)",
                "$1");
    }
}
