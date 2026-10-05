package com.example.learning.transaction.service;

import com.example.learning.transaction.support.ExperimentTraceRecorder;
import com.example.learning.transaction.support.TransactionExperimentStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PropagationExperimentService {

    private final PropagationInnerService innerService;
    private final TransactionExperimentStore store;
    private final ExperimentTraceRecorder traceRecorder;

    public PropagationExperimentService(
            PropagationInnerService innerService,
            TransactionExperimentStore store,
            ExperimentTraceRecorder traceRecorder
    ) {
        this.innerService = innerService;
        this.store = store;
        this.traceRecorder = traceRecorder;
    }

    @Transactional
    public void catchInnerRequiredFailure(String scenarioId) {
        traceRecorder.record(
                scenarioId,
                "outerActive=" + TransactionSynchronizationManager.isActualTransactionActive()
        );
        store.add(scenarioId, "outer-before-inner");

        try {
            innerService.requiredFailure(scenarioId);
        } catch (IllegalStateException expected) {
            traceRecorder.record(scenarioId, "outerCaughtInnerFailure=true");
        }

        store.add(scenarioId, "outer-after-catch");
        traceRecorder.record(scenarioId, "outerReturnedNormally=true");
    }

    @Transactional
    public void rollbackOuterAfterRequiresNewCommit(String scenarioId) {
        traceRecorder.record(
                scenarioId,
                "outerActive=" + TransactionSynchronizationManager.isActualTransactionActive()
        );
        store.add(scenarioId, "outer-write-that-will-roll-back");
        innerService.requiresNewCommit(scenarioId);
        traceRecorder.record(scenarioId, "innerReturnedToOuter=true");
        throw new IllegalStateException("outer failure after REQUIRES_NEW commit");
    }
}
