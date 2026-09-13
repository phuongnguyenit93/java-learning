package com.example.projectbuild.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
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

import java.io.InputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class DynamicSwaggerRegistrar implements ImportBeanDefinitionRegistrar , EnvironmentAware, ResourceLoaderAware {

    private Environment environment;
    private ResourceLoader resourceLoader;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private static final Pattern YOUTUBE_VIDEO_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{11}$");

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

            // Load 2 file YAML từ resources
            JsonNode apiDescs = loadYamlResource("swagger/" + languages + "/api-descriptions.yml");
            JsonNode apiParams = loadYamlResource("swagger/" + languages + "/api-params.yml");
            JsonNode controllerDescs = loadYamlResource("swagger/" + languages + "/controller-descriptions.yml");

            if (openApi.getPaths() == null) return;

            openApi.getPaths().forEach((path, pathItem) -> {
                pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                    String controllerName = (String) operation.getExtensions().get("x-controller-name");
                    String methodName = operation.getOperationId();

                    operation.getTags().forEach(tag -> {
                        // Tạo tag nếu chưa tồn tại
                        openApi.getTags()
                                .stream()
                                .filter(t -> tag.equals(t.getName()))
                                .findFirst()
                                .orElseGet(() -> {
                                    Tag newTag = new Tag();
                                    newTag.setName(tag);
                                    newTag.setDescription(controllerName);

                                    openApi.addTagsItem(newTag);

                                    return newTag;
                                });
                    });

                    // Map cho Method (Summary & Description)
                    mapMethodMetadata(operation, apiDescs, controllerName, methodName);

                    // 2. Map cho Parameters (Summary & Description)
                    if (operation.getParameters() != null) {
                        operation.getParameters().forEach(parameter -> {
                            mapParameterMetadata(parameter, apiParams);
                        });
                    }
                });
            });

            openApi.getTags().forEach(tag -> {

                String controllerName = tag.getDescription();

                JsonNode controllerNode =
                        controllerDescs.get(controllerName);

                if (controllerNode == null) {
                    return;
                }

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
        };
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
            String method
    ) {

        JsonNode methodNode =
                findMethodNode(
                        apiDescs,
                        controller,
                        method
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

        System.out.println("enableVideoYoutube");
        System.out.println(enableVideoYoutube);

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
            JsonNode apiDescs,
            String controller,
            String methodName
    ) {

        JsonNode controllerNode =
                apiDescs.path(controller);

        if (controllerNode.isMissingNode()) {
            return null;
        }

        for (JsonNode methodNode : controllerNode) {

            if (methodName.equals(
                    methodNode.path("methodName").asText()
            )) {
                return methodNode;
            }
        }

        return null;
    }
}
