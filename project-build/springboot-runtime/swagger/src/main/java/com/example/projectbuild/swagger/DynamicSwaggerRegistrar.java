package com.example.projectbuild.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
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
import java.util.Comparator;
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

        if (languages.isEmpty()) return;
        String[] langArray = languages.split(",");

        for (String lang : langArray) {
            String trimmedLang = lang.trim();
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

    private OpenApiCustomizer customerGlobalOpenApiCustomizer(String languages) {
        return openApi -> {
            String readme = loadReadme(languages);

            if (openApi.getInfo() == null) {
                openApi.setInfo(new Info());
            }

            openApi.getInfo().setDescription(readme);

            // Load Swagger metadata từ resources
            JsonNode apiDescs = loadYamlResource("swagger/" + languages + "/api-descriptions.yml");
            JsonNode apiExecutions = loadYamlResource("swagger/" + languages + "/api-execution.yml");
            JsonNode apiParams = loadYamlResource("swagger/" + languages + "/api-params.yml");
            JsonNode controllerDescs = loadYamlResource("swagger/" + languages + "/controller-description.yml");

            SwaggerReadmeMetadataResolver readmeResolver =
                    new SwaggerReadmeMetadataResolver(resourceLoader);

            Map<String, String> controllerByTag =
                    new LinkedHashMap<>();

            Map<String, SwaggerReadmeMetadataResolver.ReadmeReference> controllerReadmeByName =
                    new LinkedHashMap<>();

            Map<String, OperationReadmeOrder> pathReadmeOrder =
                    new LinkedHashMap<>();

            if (openApi.getPaths() == null) return;

            openApi.getPaths().forEach((path, pathItem) -> {
                pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                    String controllerName =
                            operation.getExtensions() == null
                                    ? null
                                    : (String) operation.getExtensions().get("x-controller-name");

                    String methodSignature =
                            operation.getExtensions() == null
                                    ? null
                                    : (String) operation.getExtensions().get("x-method-signature");

                    JsonNode controllerNode =
                            controllerName == null || controllerName.isBlank()
                                    ? null
                                    : controllerDescs.get(controllerName);

                    SwaggerReadmeMetadataResolver.ReadmeReference controllerReadme =
                            controllerName == null || controllerName.isBlank()
                                    ? null
                                    : controllerReadmeByName.computeIfAbsent(
                                            controllerName,
                                            ignored -> readmeResolver.resolveController(
                                                    controllerNode,
                                                    languages
                                            )
                                    );

                    if (operation.getTags() != null) {
                        operation.getTags().forEach(tagName -> {
                            Tag tag = findOrCreateTag(
                                    openApi,
                                    tagName,
                                    controllerName
                            );

                            if (controllerName != null &&
                                    !controllerName.isBlank()) {

                                controllerByTag.putIfAbsent(
                                        tag.getName(),
                                        controllerName
                                );
                            }
                        });
                    }

                    // Map cho Method (Summary & Description)
                    mapMethodMetadata(operation, apiDescs, controllerName, methodSignature);

                    JsonNode methodNode =
                            findMethodNode(
                                    apiDescs,
                                    controllerName,
                                    methodSignature
                            );

                    if (controllerReadme != null) {
                        SwaggerReadmeMetadataResolver.ReadmeReference methodReadme =
                                readmeResolver.resolveMethod(
                                        methodNode,
                                        controllerReadme,
                                        languages
                                );

                        operation.addExtension(
                                "x-readme-related",
                                methodReadme.toExtension()
                        );

                        warnInvalidReadmeMapping(
                                languages,
                                controllerName + "#" + methodSignature,
                                methodReadme
                        );

                        OperationReadmeOrder candidateOrder =
                                new OperationReadmeOrder(
                                        controllerName,
                                        methodSignature,
                                        controllerReadme,
                                        methodReadme
                                );

                        pathReadmeOrder.merge(
                                path,
                                candidateOrder,
                                DynamicSwaggerRegistrar::earlierOperationOrder
                        );
                    }

                    mapExecutionMetadata(
                            operation,
                            apiExecutions,
                            controllerName,
                            methodSignature
                    );

                    // 2. Map cho Parameters (Summary & Description)
                    if (operation.getParameters() != null) {
                        operation.getParameters().forEach(parameter -> {
                            mapParameterMetadata(parameter, apiParams);
                        });
                    }
                });
            });

            sortPathsByReadme(
                    openApi,
                    pathReadmeOrder
            );

            if (openApi.getTags() == null) {
                return;
            }

            openApi.getTags().forEach(tag -> {

                String controllerName =
                        controllerByTag.get(tag.getName());

                if (controllerName == null ||
                        controllerName.isBlank()) {
                    return;
                }

                JsonNode controllerNode =
                        controllerDescs.get(controllerName);

                if (controllerNode == null) {
                    return;
                }

                SwaggerReadmeMetadataResolver.ReadmeReference controllerReadme =
                        controllerReadmeByName.computeIfAbsent(
                                controllerName,
                                ignored -> readmeResolver.resolveController(
                                        controllerNode,
                                        languages
                                )
                        );

                Map<String, Object> tagReadmeExtension =
                        new LinkedHashMap<>(
                                controllerReadme.toExtension()
                        );

                tagReadmeExtension.put(
                        "controllerName",
                        controllerName
                );

                tag.addExtension(
                        "x-readme-related",
                        tagReadmeExtension
                );

                tag.setDescription(
                        buildControllerReadmeDescription(
                                controllerName,
                                controllerReadme
                        )
                );

                warnInvalidReadmeMapping(
                        languages,
                        controllerName,
                        controllerReadme
                );

                JsonNode descriptionNode =
                        controllerNode.get("description");

                if (descriptionNode == null) {
                    return;
                }

                String html =
                        descriptionNode.asText();

                tag.addExtension(
                        "x-custom-html",
                        html
                );
            });

            sortTagsByReadme(
                    openApi,
                    controllerByTag,
                    controllerReadmeByName
            );
        };
    }

    private static void sortTagsByReadme(
            io.swagger.v3.oas.models.OpenAPI openApi,
            Map<String, String> controllerByTag,
            Map<String, SwaggerReadmeMetadataResolver.ReadmeReference> controllerReadmeByName
    ) {
        if (openApi.getTags() == null || openApi.getTags().size() < 2) {
            return;
        }

        List<Tag> sortedTags =
                new ArrayList<>(openApi.getTags());

        sortedTags.sort((first, second) -> {
            String firstController =
                    controllerByTag.get(first.getName());

            String secondController =
                    controllerByTag.get(second.getName());

            int readmeComparison = compareControllerReadme(
                    firstController,
                    controllerReadmeByName.get(firstController),
                    secondController,
                    controllerReadmeByName.get(secondController)
            );

            if (readmeComparison != 0) {
                return readmeComparison;
            }

            return compareText(
                    first.getName(),
                    second.getName()
            );
        });

        openApi.setTags(sortedTags);
    }

    private static void sortPathsByReadme(
            io.swagger.v3.oas.models.OpenAPI openApi,
            Map<String, OperationReadmeOrder> pathReadmeOrder
    ) {
        Paths paths = openApi.getPaths();

        if (paths == null || paths.size() < 2) {
            return;
        }

        List<Map.Entry<String, PathItem>> entries =
                new ArrayList<>(paths.entrySet());

        entries.sort((first, second) -> {
            OperationReadmeOrder firstOrder =
                    pathReadmeOrder.get(first.getKey());

            OperationReadmeOrder secondOrder =
                    pathReadmeOrder.get(second.getKey());

            int orderComparison = compareOperationOrder(
                    firstOrder,
                    secondOrder
            );

            if (orderComparison != 0) {
                return orderComparison;
            }

            return compareText(
                    first.getKey(),
                    second.getKey()
            );
        });

        Paths sortedPaths = new Paths();

        entries.forEach(entry ->
                sortedPaths.addPathItem(
                        entry.getKey(),
                        entry.getValue()
                )
        );

        openApi.setPaths(sortedPaths);
    }

    private static OperationReadmeOrder earlierOperationOrder(
            OperationReadmeOrder first,
            OperationReadmeOrder second
    ) {
        return compareOperationOrder(first, second) <= 0
                ? first
                : second;
    }

    private static int compareOperationOrder(
            OperationReadmeOrder first,
            OperationReadmeOrder second
    ) {
        if (first == second) {
            return 0;
        }

        if (first == null) {
            return 1;
        }

        if (second == null) {
            return -1;
        }

        int controllerComparison = compareControllerReadme(
                first.controllerName(),
                first.controllerReadme(),
                second.controllerName(),
                second.controllerReadme()
        );

        if (controllerComparison != 0) {
            return controllerComparison;
        }

        SwaggerReadmeMetadataResolver.ReadmeReference firstMethod =
                first.methodReadme();

        SwaggerReadmeMetadataResolver.ReadmeReference secondMethod =
                second.methodReadme();

        boolean firstLinked =
                firstMethod != null && firstMethod.isLinked();

        boolean secondLinked =
                secondMethod != null && secondMethod.isLinked();

        if (firstLinked != secondLinked) {
            return firstLinked ? -1 : 1;
        }

        if (firstLinked) {
            int chapterComparison = compareNullableInteger(
                    firstMethod.chapterOrder(),
                    secondMethod.chapterOrder()
            );

            if (chapterComparison != 0) {
                return chapterComparison;
            }

            int sectionComparison = compareNullableInteger(
                    firstMethod.sectionOrder(),
                    secondMethod.sectionOrder()
            );

            if (sectionComparison != 0) {
                return sectionComparison;
            }
        }

        return compareText(
                first.methodSignature(),
                second.methodSignature()
        );
    }

    private static int compareControllerReadme(
            String firstController,
            SwaggerReadmeMetadataResolver.ReadmeReference firstReference,
            String secondController,
            SwaggerReadmeMetadataResolver.ReadmeReference secondReference
    ) {
        boolean firstLinked =
                firstReference != null && firstReference.isLinked();

        boolean secondLinked =
                secondReference != null && secondReference.isLinked();

        if (firstLinked != secondLinked) {
            return firstLinked ? -1 : 1;
        }

        if (firstLinked) {
            int chapterComparison = compareNullableInteger(
                    firstReference.chapterOrder(),
                    secondReference.chapterOrder()
            );

            if (chapterComparison != 0) {
                return chapterComparison;
            }
        }

        return compareText(
                firstController,
                secondController
        );
    }

    private static int compareNullableInteger(
            Integer first,
            Integer second
    ) {
        return Comparator.nullsLast(Integer::compareTo)
                .compare(first, second);
    }

    private static int compareText(
            String first,
            String second
    ) {
        return Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                .compare(first, second);
    }

    private static void warnInvalidReadmeMapping(
            String language,
            String owner,
            SwaggerReadmeMetadataResolver.ReadmeReference reference
    ) {
        if (reference == null ||
                SwaggerReadmeMetadataResolver.STATUS_LINKED.equals(reference.status()) ||
                SwaggerReadmeMetadataResolver.STATUS_UNLINKED.equals(reference.status())) {
            return;
        }

        LOGGER.warn(
                "[SWAGGER-README] language={} owner={} status={} file={} anchor={}",
                language,
                owner,
                reference.status(),
                reference.file(),
                reference.anchor()
        );
    }

    private static String buildControllerReadmeDescription(
            String controllerName,
            SwaggerReadmeMetadataResolver.ReadmeReference reference
    ) {
        if (reference != null &&
                reference.isLinked() &&
                reference.href() != null &&
                !reference.href().isBlank()) {

            return "[" + controllerName + "](" + reference.href() + ")";
        }

        return "_" +
                (reference == null
                        ? "README relationship unavailable"
                        : reference.displayText()) +
                "_";
    }

    private record OperationReadmeOrder(
            String controllerName,
            String methodSignature,
            SwaggerReadmeMetadataResolver.ReadmeReference controllerReadme,
            SwaggerReadmeMetadataResolver.ReadmeReference methodReadme
    ) {
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

    private String loadReadme(String language) {
        try {
            String normalizedLanguage = language == null
                    ? "en"
                    : language.trim().toLowerCase();

            String readmePath;

            if ("en".equals(normalizedLanguage)) {
                readmePath = "META-INF/swagger/README.md";
            } else {
                readmePath = String.format(
                        "META-INF/swagger/README.%s.md",
                        normalizedLanguage
                );
            }

            ClassPathResource resource =
                    new ClassPathResource(readmePath);

            return resource.getContentAsString(StandardCharsets.UTF_8);

        } catch (IOException e) {
            return "Không thể tải README.";
        }
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

        return normalizedMatch;
    }
}
