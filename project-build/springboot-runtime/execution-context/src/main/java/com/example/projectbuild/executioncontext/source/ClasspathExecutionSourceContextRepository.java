package com.example.projectbuild.executioncontext.source;

import com.example.projectbuild.executioncontext.model.ExecutionSourceContext;
import com.example.projectbuild.executioncontext.model.RelatedSourceContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ClasspathExecutionSourceContextRepository implements ExecutionSourceContextRepository {

    public static final String RESOURCE_PATH = "META-INF/execution-context/source-context.json";

    private final Map<String, ExecutionSourceContext> contexts;

    public ClasspathExecutionSourceContextRepository(ObjectMapper objectMapper, ClassLoader classLoader) {
        this.contexts = loadContexts(objectMapper, classLoader);
    }

    @Override
    public Optional<ExecutionSourceContext> find(
            String controllerClass,
            String methodSignature
    ) {
        return Optional.ofNullable(
                contexts.get(
                        controllerClass + "#" + methodSignature
                )
        );
    }

    private static Map<String, ExecutionSourceContext> loadContexts(
            ObjectMapper objectMapper,
            ClassLoader classLoader
    ) {
        Map<String, ExecutionSourceContext> result = new LinkedHashMap<>();

        try {
            List<URL> resources = Collections.list(classLoader.getResources(RESOURCE_PATH));
            for (URL resource : resources) {
                try (InputStream inputStream = resource.openStream()) {
                    JsonNode root = objectMapper.readTree(inputStream);
                    JsonNode methods = root.path("methods");
                    if (!methods.isObject()) {
                        continue;
                    }

                    methods.fields().forEachRemaining(entry ->
                            result.put(entry.getKey(), toContext(entry.getValue()))
                    );
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load generated execution source context", exception);
        }

        return Map.copyOf(result);
    }

    private static ExecutionSourceContext toContext(JsonNode node) {
        List<RelatedSourceContext> relatedSources = new ArrayList<>();
        JsonNode related = node.path("relatedSources");
        if (related.isArray()) {
            related.forEach(item -> relatedSources.add(
                    new RelatedSourceContext(
                            text(item, "field"),
                            text(item, "className"),
                            text(item, "sourcePath"),
                            text(item, "source")
                    )
            ));
        }

        return new ExecutionSourceContext(
                text(node, "controllerClass"),
                text(node, "method"),
                text(node, "signature"),
                text(node, "sourcePath"),
                text(node, "documentation"),
                text(node, "controllerMethodSource"),
                relatedSources
        );
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
