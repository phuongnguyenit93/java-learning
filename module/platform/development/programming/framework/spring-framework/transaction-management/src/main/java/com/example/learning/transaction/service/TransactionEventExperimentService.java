package com.example.learning.transaction.service;

import com.example.learning.transaction.event.TransactionExperimentEvent;
import com.example.learning.transaction.support.ExperimentTraceRecorder;
import com.example.learning.transaction.support.TransactionExperimentStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TransactionEventExperimentService {

    private final ApplicationEventPublisher eventPublisher;
    private final TransactionExperimentStore store;
    private final ExperimentTraceRecorder traceRecorder;

    public TransactionEventExperimentService(
            ApplicationEventPublisher eventPublisher,
            TransactionExperimentStore store,
            ExperimentTraceRecorder traceRecorder
    ) {
        this.eventPublisher = eventPublisher;
        this.store = store;
        this.traceRecorder = traceRecorder;
    }

    @Transactional
    public void publishAndCommit(String scenarioId) {
        traceRecorder.record(
                scenarioId,
                "transactionActive=" + TransactionSynchronizationManager.isActualTransactionActive()
        );
        store.add(scenarioId, "commit-scenario-write");
        eventPublisher.publishEvent(new TransactionExperimentEvent(scenarioId));
        traceRecorder.record(scenarioId, "eventPublished=true");
    }

    @Transactional
    public void publishAndRollback(String scenarioId) {
        traceRecorder.record(
                scenarioId,
                "transactionActive=" + TransactionSynchronizationManager.isActualTransactionActive()
        );
        store.add(scenarioId, "rollback-scenario-write");
        eventPublisher.publishEvent(new TransactionExperimentEvent(scenarioId));
        traceRecorder.record(scenarioId, "eventPublished=true");
        throw new IllegalStateException("rollback event scenario");
    }
}
