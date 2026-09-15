package com.example.projectbuild.executioncontext.source;

import com.example.projectbuild.executioncontext.model.ExecutionSourceContext;

import java.util.Optional;

public interface ExecutionSourceContextRepository {

    Optional<ExecutionSourceContext> find(
            String controllerClass,
            String methodSignature
    );
}
