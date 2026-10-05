package com.example.learning.transaction.service;

import com.example.learning.transaction.support.ExperimentTraceRecorder;
import com.example.learning.transaction.support.TransactionExperimentStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ProxyBoundaryService {

    private final TransactionExperimentStore store;
    private final ExperimentTraceRecorder traceRecorder;

    public ProxyBoundaryService(
            TransactionExperimentStore store,
            ExperimentTraceRecorder traceRecorder
    ) {
        this.store = store;
        this.traceRecorder = traceRecorder;
    }

    public void invokeAnnotatedMethodOnThis(String scenarioId) {
        try {
            transactionalWriteThenFail(scenarioId, "self-invocation-write");
        } catch (IllegalStateException expected) {
            traceRecorder.record(scenarioId, "selfInvocationExceptionCaught=true");
        }
    }

    @Transactional
    public void transactionalWriteThenFail(String scenarioId, String marker) {
        traceRecorder.record(
                scenarioId,
                "transactionActive=" + TransactionSynchronizationManager.isActualTransactionActive()
        );
        store.add(scenarioId, marker);
        throw new IllegalStateException("intentional failure after write");
    }
}
