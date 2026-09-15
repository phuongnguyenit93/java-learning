package com.example.projectbuild.executioncontext.servlet;

import com.example.projectbuild.executioncontext.model.ExecutionLogEntry;
import com.example.projectbuild.executioncontext.model.ExecutionSourceContext;
import com.example.projectbuild.executioncontext.model.ExperimentContext;
import com.example.projectbuild.executioncontext.model.RelatedSourceContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExecutionContextExportService {

    private final ObjectMapper objectMapper;

    public ExecutionContextExportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] export(List<ExperimentContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            throw new IllegalArgumentException("At least one execution context is required for export");
        }

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                writeManifest(zip, contexts);

                for (ExperimentContext context : contexts) {
                    writeContext(zip, context);
                }
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to export execution context bundle", exception);
        }
    }

    private void writeManifest(
            ZipOutputStream zip,
            List<ExperimentContext> contexts
    ) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", 1);
        manifest.put("exportedAt", Instant.now().toString());
        manifest.put("count", contexts.size());
        manifest.put(
                "executionIds",
                contexts.stream()
                        .map(context -> context.execution().executionId())
                        .toList()
        );

        writeJson(zip, "manifest.json", manifest);
    }

    private void writeContext(
            ZipOutputStream zip,
            ExperimentContext context
    ) throws IOException {
        String executionId = safePathPart(context.execution().executionId());
        String root = "executions/" + executionId + "/";

        writeJson(zip, root + "context.json", context);
        writeText(zip, root + "logs.txt", renderLogs(context.execution().logs()));

        ExecutionSourceContext source = context.sourceContext();
        if (source == null) {
            return;
        }

        if (source.controllerMethodSource() != null && !source.controllerMethodSource().isBlank()) {
            writeText(zip, root + "source/controller-method.java", source.controllerMethodSource());
        }
        if (source.documentation() != null && !source.documentation().isBlank()) {
            writeText(zip, root + "source/documentation.txt", source.documentation());
        }

        int index = 1;
        for (RelatedSourceContext related : source.relatedSources()) {
            String simpleName = simpleClassName(related.className());
            String fileName = String.format("%02d-%s.java", index++, safePathPart(simpleName));
            writeText(zip, root + "source/related/" + fileName, related.source());
        }
    }

    private String renderLogs(List<ExecutionLogEntry> logs) {
        StringBuilder result = new StringBuilder();
        for (ExecutionLogEntry log : logs) {
            result.append(log.timestamp())
                    .append(' ')
                    .append('[').append(log.level()).append(']')
                    .append(' ')
                    .append('[').append(log.thread()).append(']')
                    .append(' ')
                    .append(log.logger())
                    .append(" - ")
                    .append(log.message())
                    .append(System.lineSeparator());
        }
        return result.toString();
    }

    private void writeJson(
            ZipOutputStream zip,
            String path,
            Object value
    ) throws IOException {
        byte[] bytes = objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(value);
        writeBytes(zip, path, bytes);
    }

    private static void writeText(
            ZipOutputStream zip,
            String path,
            String value
    ) throws IOException {
        writeBytes(
                zip,
                path,
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static void writeBytes(
            ZipOutputStream zip,
            String path,
            byte[] bytes
    ) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String simpleClassName(String className) {
        if (className == null || className.isBlank()) {
            return "related-source";
        }
        int separator = className.lastIndexOf('.');
        return separator < 0 ? className : className.substring(separator + 1);
    }

    private static String safePathPart(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
