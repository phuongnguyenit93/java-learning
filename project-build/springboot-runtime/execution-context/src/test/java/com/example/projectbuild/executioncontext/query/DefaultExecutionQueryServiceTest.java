package com.example.projectbuild.executioncontext.query;

import com.example.projectbuild.executioncontext.model.ExecutionExceptionSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionHandlerSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionRequestSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionResponseSnapshot;
import com.example.projectbuild.executioncontext.model.ExperimentExecution;
import com.example.projectbuild.executioncontext.service.DefaultExecutionContextService;
import com.example.projectbuild.executioncontext.service.ExecutionContextService;
import com.example.projectbuild.executioncontext.store.InMemoryExecutionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultExecutionQueryServiceTest {

    private InMemoryExecutionStore store;
    private ExecutionQueryService queryService;

    @BeforeEach
    void setUp() {
        store = new InMemoryExecutionStore(20);
        ExecutionContextService contextService = new DefaultExecutionContextService(
                store,
                (controllerClass, methodSignature) -> Optional.empty()
        );
        queryService = new DefaultExecutionQueryService(contextService);

        store.put(execution(
                "one",
                1_000,
                "GET",
                "/basic/one",
                "BasicController",
                "one",
                200,
                15,
                null
        ));
        store.put(execution(
                "two",
                2_000,
                "GET",
                "/basic/two",
                "BasicController",
                "two",
                500,
                30,
                new ExecutionExceptionSnapshot("java.lang.IllegalStateException", "boom")
        ));
        store.put(execution(
                "three",
                3_000,
                "POST",
                "/virtual-thread/test",
                "VirtualThreadController",
                "test",
                201,
                45,
                null
        ));
    }

    @Test
    void returnsMostRecentExecutionsFirstAndHonorsLimit() {
        assertThat(queryService.find(ExecutionQuery.recent(2)))
                .extracting(summary -> summary.executionId())
                .containsExactly("three", "two");
    }

    @Test
    void filtersByTimeRangeAndFreeTextSearch() {
        ExecutionQuery query = new ExecutionQuery(
                10,
                1_500L,
                3_000L,
                "basic",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(queryService.find(query))
                .extracting(summary -> summary.executionId())
                .containsExactly("two");
    }

    @Test
    void filtersByOperationStatusAndFailure() {
        ExecutionQuery query = new ExecutionQuery(
                10,
                null,
                null,
                null,
                "GET",
                "/basic/two",
                null,
                null,
                500,
                true,
                20L,
                40L
        );

        assertThat(queryService.find(query))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.executionId()).isEqualTo("two");
                    assertThat(summary.failed()).isTrue();
                    assertThat(summary.durationMillis()).isEqualTo(30);
                });
    }

    private static ExperimentExecution execution(
            String id,
            long startedAt,
            String httpMethod,
            String path,
            String controller,
            String method,
            int status,
            long duration,
            ExecutionExceptionSnapshot exception
    ) {
        return new ExperimentExecution(
                id,
                new ExecutionHandlerSnapshot(controller, method, method + "()"),
                new ExecutionRequestSnapshot(
                        httpMethod,
                        path,
                        null,
                        null,
                        Map.of(),
                        ""
                ),
                new ExecutionResponseSnapshot(status, "application/json", "{}"),
                List.of(),
                exception,
                startedAt,
                startedAt + duration,
                duration
        );
    }
}
