package com.example.projectbuild.executioncontext.model;

public record RelatedSourceContext(
        String field,
        String className,
        String sourcePath,
        String source
) {
}
