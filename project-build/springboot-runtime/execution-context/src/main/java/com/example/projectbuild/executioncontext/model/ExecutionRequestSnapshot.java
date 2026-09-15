package com.example.projectbuild.executioncontext.model;

import java.util.List;
import java.util.Map;

public record ExecutionRequestSnapshot(
        String method,
        String path,
        String query,
        String contentType,
        Map<String, List<String>> parameters,
        String body
) {
}
