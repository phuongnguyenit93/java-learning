package com.example.learning.oop.refactoring;

import com.example.learning.oop.refactoring.exception.TemporaryDeliveryFailure;
import com.example.learning.oop.refactoring.model.AlertDispatcher;
import com.example.learning.oop.refactoring.model.MessageChannel;
import com.example.learning.oop.refactoring.model.RecordingMessageChannel;
import com.example.learning.oop.refactoring.model.RetryingChannel;
import com.example.learning.oop.refactoring.service.AlertRefactoringService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.example.learning.oop.refactoring.dto.RefactoringDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class CompositionAlertTest {
    @Test
    void compositionAllowsReplaceableChannelsAndObservableRecipientMessage() {
        List<String> sent = new ArrayList<>();
        MessageChannel fake = (recipient, message) -> sent.add(recipient + "/" + message);
        var alert = new AlertDispatcher(fake);
        alert.sendCritical("oncall", "Disk full");
        assertEquals(List.of("oncall/[CRITICAL] Disk full"), sent);
        assertThrows(IllegalArgumentException.class, () -> alert.sendCritical(" ", "Disk full"));
        assertEquals(1, sent.size());
    }

    @Test
    void retryDecoratorOnlyRetriesTemporaryFailuresWithinBound() {
        var recorder = new RecordingMessageChannel("EMAIL", 2);
        new AlertDispatcher(new RetryingChannel(recorder, 3))
                .sendCritical("ops@example.org", "Service down");
        assertEquals(3, recorder.attempts());
        assertEquals(List.of("EMAIL:attempt=1:TEMPORARY_FAILURE",
                "EMAIL:attempt=2:TEMPORARY_FAILURE",
                "EMAIL:attempt=3:DELIVERED"), recorder.events());
        assertEquals(List.of("EMAIL->ops@example.org:[CRITICAL] Service down"),
                recorder.delivered());
    }

    @Test
    void exhaustedRetriesProduceNoSuccessfulDeliveryAndNoSharedState() {
        var service = new AlertRefactoringService();
        AlertResult failure = service.execute(
                new AlertRequest("SMS", "oncall", "Database down", 5, 3));
        assertFalse(failure.delivered());
        assertEquals(3, failure.attempts());
        assertEquals("RETRY_EXHAUSTED", failure.outcome());
        assertEquals(List.of(), failure.deliveredMessages());
        AlertResult next = service.execute(
                new AlertRequest("SMS", "oncall", "Database up", 0, 3));
        assertTrue(next.delivered());
        assertEquals(1, next.attempts());
        assertEquals(List.of("SMS->oncall:[CRITICAL] Database up"), next.deliveredMessages());
    }

    @Test
    void permanentFailureIsNotRetriedAndInvalidAttemptBoundsAreRejected() {
        final int[] callCount = {0};
        var retry = new RetryingChannel((recipient, body) -> {
            callCount[0]++;
            throw new IllegalStateException("Permanent failure");
        }, 5);
        assertThrows(IllegalStateException.class, () -> retry.send("oncall", "hello"));
        assertEquals(1, callCount[0]);
        assertThrows(IllegalArgumentException.class, () -> new RetryingChannel((a, b) -> {}, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new AlertRefactoringService().execute(new AlertRequest("EMAIL", "oncall", "Issue", 0, 6)));
        assertThrows(IllegalArgumentException.class, () ->
                new AlertRefactoringService().execute(new AlertRequest("POST", "oncall", "Issue", 0, 3)));
    }
}
