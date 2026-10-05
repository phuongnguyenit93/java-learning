package com.example.learning.transaction.controller;

import com.example.learning.transaction.service.PropagationExperimentService;
import com.example.learning.transaction.service.ProxyBoundaryService;
import com.example.learning.transaction.service.RollbackDefaultsService;
import com.example.learning.transaction.service.TransactionEventExperimentService;
import com.example.learning.transaction.support.ExperimentTraceRecorder;
import com.example.learning.transaction.support.TransactionExperimentStore;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/spring-transactions")
public class TransactionExperimentController {

    private final ProxyBoundaryService proxyBoundaryService;
    private final RollbackDefaultsService rollbackDefaultsService;
    private final PropagationExperimentService propagationService;
    private final TransactionEventExperimentService eventService;
    private final TransactionExperimentStore store;
    private final ExperimentTraceRecorder traceRecorder;

    public TransactionExperimentController(
            ProxyBoundaryService proxyBoundaryService,
            RollbackDefaultsService rollbackDefaultsService,
            PropagationExperimentService propagationService,
            TransactionEventExperimentService eventService,
            TransactionExperimentStore store,
            ExperimentTraceRecorder traceRecorder
    ) {
        this.proxyBoundaryService = proxyBoundaryService;
        this.rollbackDefaultsService = rollbackDefaultsService;
        this.propagationService = propagationService;
        this.eventService = eventService;
        this.store = store;
        this.traceRecorder = traceRecorder;
    }

    @GetMapping("/proxy-boundary")
    public Map<String, Object> proxyBoundary() {
        String selfScenario = scenario("self");
        String proxiedScenario = scenario("proxied");
        try {
            proxyBoundaryService.invokeAnnotatedMethodOnThis(selfScenario);
            try {
                proxyBoundaryService.transactionalWriteThenFail(
                        proxiedScenario,
                        "proxied-write"
                );
            } catch (IllegalStateException expected) {
                traceRecorder.record(proxiedScenario, "proxiedExceptionCaught=true");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("selfInvocation", evidence(selfScenario));
            result.put("externalProxyInvocation", evidence(proxiedScenario));
            result.put(
                    "conclusion",
                    "Self-invocation bypasses proxy transaction advice. In this H2/Hikari demo, the " +
                            "non-transactional JDBC write uses the connection's default auto-commit and survives. " +
                            "The external proxy call starts a Spring transaction and rolls the same write pattern back."
            );
            return result;
        } finally {
            cleanup(selfScenario, proxiedScenario);
        }
    }

    @GetMapping("/rollback-defaults")
    public Map<String, Object> rollbackDefaults() {
        String runtimeScenario = scenario("runtime");
        String checkedScenario = scenario("checked");
        try {
            try {
                rollbackDefaultsService.failWithRuntimeException(runtimeScenario);
            } catch (IllegalStateException expected) {
                traceRecorder.record(runtimeScenario, "runtimeExceptionCaught=true");
            }

            try {
                rollbackDefaultsService.failWithCheckedException(checkedScenario);
            } catch (RollbackDefaultsService.CheckedExperimentException expected) {
                traceRecorder.record(checkedScenario, "checkedExceptionCaught=true");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("runtimeException", evidence(runtimeScenario));
            result.put("checkedException", evidence(checkedScenario));
            result.put(
                    "conclusion",
                    "With default @Transactional rules, RuntimeException rolls the JDBC write back, " +
                            "while the checked exception does not request rollback and the write commits."
            );
            return result;
        } finally {
            cleanup(runtimeScenario, checkedScenario);
        }
    }

    @GetMapping("/required-rollback-only")
    public Map<String, Object> requiredRollbackOnly() {
        String scenarioId = scenario("required");
        try {
            boolean unexpectedRollbackObserved = false;
            try {
                propagationService.catchInnerRequiredFailure(scenarioId);
            } catch (UnexpectedRollbackException expected) {
                unexpectedRollbackObserved = true;
                traceRecorder.record(scenarioId, "unexpectedRollbackException=true");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("unexpectedRollbackObserved", unexpectedRollbackObserved);
            result.put("evidence", evidence(scenarioId));
            result.put(
                    "conclusion",
                    "The inner REQUIRED scope marks the shared physical transaction rollback-only. " +
                            "Catching the inner failure does not restore commitability, so the outer commit attempt " +
                            "ends in UnexpectedRollbackException and every JDBC write is rolled back."
            );
            return result;
        } finally {
            cleanup(scenarioId);
        }
    }

    @GetMapping("/requires-new")
    public Map<String, Object> requiresNew() {
        String scenarioId = scenario("requires-new");
        try {
            try {
                propagationService.rollbackOuterAfterRequiresNewCommit(scenarioId);
            } catch (IllegalStateException expected) {
                traceRecorder.record(scenarioId, "outerExceptionCaught=true");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("evidence", evidence(scenarioId));
            result.put(
                    "conclusion",
                    "REQUIRES_NEW commits on its own physical transaction. The later outer rollback removes the " +
                            "outer write but cannot undo the already committed inner write."
            );
            return result;
        } finally {
            cleanup(scenarioId);
        }
    }

    @GetMapping("/event-phases")
    public Map<String, Object> eventPhases() {
        String commitScenario = scenario("event-commit");
        String rollbackScenario = scenario("event-rollback");
        try {
            eventService.publishAndCommit(commitScenario);
            try {
                eventService.publishAndRollback(rollbackScenario);
            } catch (IllegalStateException expected) {
                traceRecorder.record(rollbackScenario, "rollbackExceptionCaught=true");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("commitScenario", evidence(commitScenario));
            result.put("rollbackScenario", evidence(rollbackScenario));
            result.put(
                    "conclusion",
                    "A committed transaction triggers BEFORE_COMMIT, AFTER_COMMIT, and AFTER_COMPLETION. " +
                            "A rolled-back transaction triggers AFTER_ROLLBACK and AFTER_COMPLETION, while its JDBC write disappears."
            );
            return result;
        } finally {
            cleanup(commitScenario, rollbackScenario);
        }
    }

    private Map<String, Object> evidence(String scenarioId) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("trace", traceRecorder.snapshot(scenarioId));
        evidence.put("committedMarkers", store.markers(scenarioId));
        return evidence;
    }

    private String scenario(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private void cleanup(String... scenarioIds) {
        for (String scenarioId : scenarioIds) {
            store.delete(scenarioId);
            traceRecorder.clear(scenarioId);
        }
    }
}
