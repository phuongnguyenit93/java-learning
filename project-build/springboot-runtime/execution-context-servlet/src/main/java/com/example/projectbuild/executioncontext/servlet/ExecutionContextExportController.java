package com.example.projectbuild.executioncontext.servlet;

import com.example.projectbuild.executioncontext.model.ExperimentContext;
import com.example.projectbuild.executioncontext.query.ExecutionQueryService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/execution-context/api")
public class ExecutionContextExportController {

    private static final MediaType ZIP_MEDIA_TYPE = MediaType.parseMediaType("application/zip");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private final ExecutionQueryService executionQueryService;
    private final ExecutionContextExportService exportService;

    public ExecutionContextExportController(
            ExecutionQueryService executionQueryService,
            ExecutionContextExportService exportService
    ) {
        this.executionQueryService = executionQueryService;
        this.exportService = exportService;
    }

    @GetMapping(value = "/executions/{executionId}/export", produces = "application/zip")
    public ResponseEntity<byte[]> exportOne(@PathVariable String executionId) {
        return executionQueryService.findById(executionId)
                .map(context -> zipResponse(
                        exportService.export(List.of(context)),
                        "execution-context-" + executionId + ".zip"
                ))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/export", produces = "application/zip")
    public ResponseEntity<byte[]> export(@RequestBody ExecutionContextExportRequest request) {
        List<ExperimentContext> contexts;

        if (request != null && request.executionIds() != null && !request.executionIds().isEmpty()) {
            contexts = executionQueryService.findContexts(request.executionIds());
        } else if (request != null && request.query() != null) {
            contexts = executionQueryService.findContexts(request.query());
        } else {
            contexts = List.of();
        }

        if (contexts.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String filename = "execution-context-"
                + FILE_TIMESTAMP.format(Instant.now())
                + ".zip";

        return zipResponse(exportService.export(contexts), filename);
    }

    private static ResponseEntity<byte[]> zipResponse(byte[] body, String filename) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(ZIP_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(body.length)
                .body(body);
    }
}
