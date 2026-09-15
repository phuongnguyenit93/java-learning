package com.example.projectbuild.executioncontext.servlet;

import com.example.projectbuild.executioncontext.context.ExecutionContextHolder;
import com.example.projectbuild.executioncontext.model.ExecutionRequestSnapshot;
import com.example.projectbuild.executioncontext.model.ExperimentExecution;
import com.example.projectbuild.executioncontext.service.DefaultExecutionContextService;
import com.example.projectbuild.executioncontext.service.ExecutionContextService;
import com.example.projectbuild.executioncontext.store.InMemoryExecutionStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionContextSystemStreamCaptureTest {

    @Test
    void capturesStdoutFromRequestThreadAndNewChildThread() throws Exception {
        String requestThreadName = Thread.currentThread().getName();

        ExecutionContextService executionContextService =
                new DefaultExecutionContextService(
                        new InMemoryExecutionStore(10),
                        (controllerClass, methodSignature) -> Optional.empty()
                );

        String executionId = executionContextService.start(
                new ExecutionRequestSnapshot(
                        "GET",
                        "/test",
                        null,
                        null,
                        Map.of(),
                        ""
                )
        );

        ExecutionContextSystemStreamCapture capture =
                new ExecutionContextSystemStreamCapture(
                        executionContextService
                );

        try {
            capture.afterPropertiesSet();
            ExecutionContextHolder.set(executionId);

            System.out.println("parent-line");

            Thread child = new Thread(
                    () -> System.out.println("child-line"),
                    "execution-context-child"
            );
            child.start();
            child.join();
        } finally {
            ExecutionContextHolder.clear();
            capture.destroy();
        }

        ExperimentExecution execution = executionContextService
                .findById(executionId)
                .orElseThrow();

        assertThat(execution.logs())
                .extracting(
                        log -> log.level() + ":" + log.thread() + ":" + log.message()
                )
                .containsExactly(
                        "STDOUT:" + requestThreadName + ":parent-line",
                        "STDOUT:execution-context-child:child-line"
                );
    }
}
