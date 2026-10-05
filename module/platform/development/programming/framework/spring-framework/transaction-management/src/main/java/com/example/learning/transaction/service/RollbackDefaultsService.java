package com.example.learning.transaction.service;

import com.example.learning.transaction.support.ExperimentTraceRecorder;
import com.example.learning.transaction.support.TransactionExperimentStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RollbackDefaultsService {

    private final TransactionExperimentStore store;
    private final ExperimentTraceRecorder traceRecorder;

    public RollbackDefaultsService(
            TransactionExperimentStore store,
            ExperimentTraceRecorder traceRecorder
    ) {
        this.store = store;
        this.traceRecorder = traceRecorder;
    }

    @Transactional
    public void failWithRuntimeException(String scenarioId) {
        traceRecorder.record(
                scenarioId,
                "transactionActive=" + TransactionSynchronizationManager.isActualTransactionActive()
        );
        store.add(scenarioId, "runtime-write");
        throw new IllegalStateException("runtime failure");
    }

    @Transactional
    public void failWithCheckedException(String scenarioId) throws CheckedExperimentException {
        traceRecorder.record(
                scenarioId,
                "transactionActive=" + TransactionSynchronizationManager.isActualTransactionActive()
        );
        store.add(scenarioId, "checked-write");
        throw new CheckedExperimentException("checked failure");
    }

    public static final class CheckedExperimentException extends Exception {

        public CheckedExperimentException(String message) {
            super(message);
        }
    }
}
