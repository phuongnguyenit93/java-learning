package com.example.learning.oop.refactoring.service;

import com.example.learning.oop.refactoring.exception.TemporaryDeliveryFailure;
import com.example.learning.oop.refactoring.model.AlertDispatcher;
import com.example.learning.oop.refactoring.model.RecordingMessageChannel;
import com.example.learning.oop.refactoring.model.RetryingChannel;
import java.util.Objects;
import org.springframework.stereotype.Service;
import static com.example.learning.oop.refactoring.dto.RefactoringDtos.*;

@Service
public final class AlertRefactoringService {
    public AlertResult execute(AlertRequest request) {
        Objects.requireNonNull(request, "request");
        // Fresh collaborator per HTTP call; neither attempt counters nor messages leak across requests.
        var recorder = new RecordingMessageChannel(request.channel(), request.transientFailures());
        var dispatcher = new AlertDispatcher(new RetryingChannel(recorder, request.maxAttempts()));
        try {
            dispatcher.sendCritical(request.recipient(), request.incident());
            return new AlertResult(true, recorder.attempts(), recorder.events(),
                    recorder.delivered(), "DELIVERED");
        } catch (TemporaryDeliveryFailure failure) {
            return new AlertResult(false, recorder.attempts(), recorder.events(),
                    recorder.delivered(), "RETRY_EXHAUSTED");
        }
    }
}
