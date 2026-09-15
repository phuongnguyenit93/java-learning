package com.example.projectbuild.executioncontext.servlet;

import com.example.projectbuild.executioncontext.model.ExecutionHandlerSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionLogEntry;
import com.example.projectbuild.executioncontext.model.ExecutionRequestSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionResponseSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionSourceContext;
import com.example.projectbuild.executioncontext.model.ExperimentContext;
import com.example.projectbuild.executioncontext.model.ExperimentExecution;
import com.example.projectbuild.executioncontext.model.RelatedSourceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionContextExportServiceTest {

    @Test
    void exportsPortableAiContextBundle() throws Exception {
        ExecutionContextExportService exportService = new ExecutionContextExportService(new ObjectMapper());

        ExperimentExecution execution = new ExperimentExecution(
                "execution-1",
                new ExecutionHandlerSnapshot("example.BasicController", "run", "run()"),
                new ExecutionRequestSnapshot("GET", "/basic/run", null, null, Map.of(), ""),
                new ExecutionResponseSnapshot(200, "application/json", "{\"ok\":true}"),
                List.of(new ExecutionLogEntry(10, "INFO", "demo", "worker-1", "hello")),
                null,
                1,
                20,
                19
        );

        ExecutionSourceContext sourceContext = new ExecutionSourceContext(
                "example.BasicController",
                "run",
                "run()",
                "example/BasicController.java",
                "README: Basic.md#run",
                "public void run() {}",
                List.of(new RelatedSourceContext(
                        "service",
                        "example.BasicService",
                        "example/BasicService.java",
                        "class BasicService {}"
                ))
        );

        byte[] archive = exportService.export(List.of(new ExperimentContext(execution, sourceContext)));

        List<String> entries = new ArrayList<>();
        Map<String, String> content = new java.util.LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
                content.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        assertThat(entries).contains(
                "manifest.json",
                "executions/execution-1/context.json",
                "executions/execution-1/logs.txt",
                "executions/execution-1/source/controller-method.java",
                "executions/execution-1/source/documentation.txt",
                "executions/execution-1/source/related/01-BasicService.java"
        );
        assertThat(content.get("executions/execution-1/logs.txt"))
                .contains("worker-1", "hello");
        assertThat(content.get("executions/execution-1/source/related/01-BasicService.java"))
                .contains("class BasicService");
    }
}
