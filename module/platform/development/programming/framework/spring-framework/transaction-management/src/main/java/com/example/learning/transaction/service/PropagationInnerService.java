package com.example.learning.transaction.service;

import com.example.learning.transaction.support.ExperimentTraceRecorder;
import com.example.learning.transaction.support.TransactionExperimentStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PropagationInnerService {

    private final TransactionExperimentStore store;
    private final ExperimentTraceRecorder traceRecorder;

    public PropagationInnerService(
            TransactionExperimentStore store,
            ExperimentTraceRecorder traceRecorder
    ) {
        this.store = store;
        this.traceRecorder = traceRecorder;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void requiredFailure(String scenarioId) {
        traceRecorder.record(
                scenarioId,
                "innerRequiredActive=" + TransactionSynchronizationManager.isActualTransactionActive()
        );
        store.add(scenarioId, "inner-required-write");
        throw new IllegalStateException("inner REQUIRED failure");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requiresNewCommit(String scenarioId) {
        traceRecorder.record(
                scenarioId,
                "innerRequiresNewActive=" + TransactionSynchronizationManager.isActualTransactionActive()
        );
        store.add(scenarioId, "inner-requires-new-committed");
    }
}
