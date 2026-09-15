package com.example.projectbuild.executioncontext.servlet;

import com.example.projectbuild.executioncontext.query.ExecutionQuery;

import java.util.List;

public record ExecutionContextExportRequest(
        List<String> executionIds,
        ExecutionQuery query
) {
}
