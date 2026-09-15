package com.example.projectbuild.executioncontext.model;

import java.util.List;

public record ExecutionSourceContext(
        String controllerClass,
        String method,
        String signature,
        String sourcePath,
        String documentation,
        String controllerMethodSource,
        List<RelatedSourceContext> relatedSources
) {
}
