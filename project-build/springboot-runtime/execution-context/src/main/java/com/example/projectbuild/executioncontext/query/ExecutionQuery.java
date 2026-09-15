package com.example.projectbuild.executioncontext.query;

public record ExecutionQuery(
        Integer limit,
        Long fromEpochMilli,
        Long toEpochMilli,
        String search,
        String httpMethod,
        String path,
        String controller,
        String method,
        Integer status,
        Boolean failed,
        Long minDurationMillis,
        Long maxDurationMillis
) {

    public static ExecutionQuery recent(int limit) {
        return new ExecutionQuery(
                limit,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
