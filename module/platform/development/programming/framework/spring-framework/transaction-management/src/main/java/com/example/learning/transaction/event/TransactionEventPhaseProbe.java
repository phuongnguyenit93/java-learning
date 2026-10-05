package com.example.learning.transaction.event;

import com.example.learning.transaction.support.ExperimentTraceRecorder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TransactionEventPhaseProbe {

    private final ExperimentTraceRecorder traceRecorder;

    public TransactionEventPhaseProbe(ExperimentTraceRecorder traceRecorder) {
        this.traceRecorder = traceRecorder;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void beforeCommit(TransactionExperimentEvent event) {
        traceRecorder.record(event.scenarioId(), "BEFORE_COMMIT");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(TransactionExperimentEvent event) {
        traceRecorder.record(event.scenarioId(), "AFTER_COMMIT");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void afterRollback(TransactionExperimentEvent event) {
        traceRecorder.record(event.scenarioId(), "AFTER_ROLLBACK");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void afterCompletion(TransactionExperimentEvent event) {
        traceRecorder.record(event.scenarioId(), "AFTER_COMPLETION");
    }
}
