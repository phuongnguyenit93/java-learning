package com.example.learning.jms.experiment.service;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.TextMessage;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.SessionAwareMessageListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class JmsExperimentService {

    private static final Duration EXPERIMENT_TIMEOUT = Duration.ofSeconds(5);
    private static final long RECEIVE_TIMEOUT_MILLIS = 3_000L;
    private static final long EMPTY_RECEIVE_TIMEOUT_MILLIS = 200L;
    private static final int MAX_DRAIN_MESSAGES = 16;
    private static final String REQUEST_REPLY_DESTINATION =
            "learning.jms.request-reply";
    private static final String ROLLBACK_REDELIVERY_DESTINATION =
            "learning.jms.rollback-redelivery";
    private static final String OBSERVABILITY_PROCESS_DESTINATION =
            "learning.jms.observability-process";
    private static final String OBSERVABILITY_EMPTY_DESTINATION =
            "learning.jms.observability-empty";

    private final ConnectionFactory connectionFactory;

    public JmsExperimentService(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public synchronized Map<String, Object> observeRequestReplyRoundTrip() {
        String destinationName = REQUEST_REPLY_DESTINATION;
        drainDestination(destinationName);
        AtomicInteger listenerInvocations = new AtomicInteger();
        AtomicBoolean temporaryReplyDestination = new AtomicBoolean();
        AtomicReference<String> requestMessageId = new AtomicReference<>();

        DefaultMessageListenerContainer container = listenerContainer(
                destinationName,
                (SessionAwareMessageListener<Message>) (message, session) -> {
                    listenerInvocations.incrementAndGet();

                    Destination replyTo = message.getJMSReplyTo();
                    temporaryReplyDestination.set(
                            replyTo instanceof TemporaryQueue
                                    || replyTo instanceof TemporaryTopic
                    );
                    requestMessageId.set(message.getJMSMessageID());

                    String requestText = text(message);
                    TextMessage reply = session.createTextMessage(
                            "reply:" + requestText
                    );
                    reply.setJMSCorrelationID(message.getJMSMessageID());

                    MessageProducer producer = session.createProducer(replyTo);
                    try {
                        producer.send(reply);
                    }
                    finally {
                        producer.close();
                    }
                },
                false,
                null
        );
        try {
            container.start();

            JmsTemplate template = new JmsTemplate(connectionFactory);
            template.setReceiveTimeout(RECEIVE_TIMEOUT_MILLIS);

            long startedNanos = System.nanoTime();
            Message response = template.sendAndReceive(
                    destinationName,
                    session -> session.createTextMessage("ping")
            );
            long elapsedMillis = Duration.ofNanos(
                    System.nanoTime() - startedNanos
            ).toMillis();

            String responseText = response == null ? null : text(response);
            String responseCorrelationId = response == null
                    ? null
                    : response.getJMSCorrelationID();
            boolean replyCorrelatedToRequest = requestMessageId.get() != null
                    && requestMessageId.get().equals(responseCorrelationId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("requestPayload", "ping");
            result.put("responsePayload", responseText);
            result.put("listenerInvocations", listenerInvocations.get());
            result.put(
                    "temporaryReplyDestination",
                    temporaryReplyDestination.get()
            );
            result.put("elapsedMillis", elapsedMillis);
            result.put("responseReceived", response != null);
            result.put(
                    "replyCorrelatedToRequest",
                    replyCorrelatedToRequest
            );
            result.put(
                    "requestReplyObserved",
                    "reply:ping".equals(responseText)
                            && listenerInvocations.get() == 1
                            && temporaryReplyDestination.get()
                            && replyCorrelatedToRequest
            );
            return result;
        }
        catch (JMSException exception) {
            throw new IllegalStateException(
                    "Unable to inspect JMS request/reply evidence",
                    exception
            );
        }
        finally {
            try {
                shutdown(container);
            }
            finally {
                drainDestination(destinationName);
            }
        }
    }

    public synchronized Map<String, Object> observeRollbackRedelivery() {
        String destinationName = ROLLBACK_REDELIVERY_DESTINATION;
        drainDestination(destinationName);
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger businessEffectCount = new AtomicInteger();
        AtomicInteger listenerErrorCount = new AtomicInteger();
        AtomicReference<String> firstListenerError = new AtomicReference<>();
        List<Boolean> redeliveryFlags =
                Collections.synchronizedList(new ArrayList<>());
        CountDownLatch successfulAttempt = new CountDownLatch(1);

        DefaultMessageListenerContainer container = listenerContainer(
                destinationName,
                (SessionAwareMessageListener<Message>) (message, session) -> {
                    int attempt = attempts.incrementAndGet();
                    redeliveryFlags.add(message.getJMSRedelivered());

                    if (attempt == 1) {
                        throw new IllegalStateException(
                                "intentional first-attempt failure"
                        );
                    }

                    businessEffectCount.incrementAndGet();
                    successfulAttempt.countDown();
                },
                true,
                null
        );
        container.setErrorHandler(error -> {
            listenerErrorCount.incrementAndGet();
            firstListenerError.compareAndSet(
                    null,
                    error.getClass().getSimpleName()
            );
        });
        try {
            container.start();

            JmsTemplate template = new JmsTemplate(connectionFactory);
            template.convertAndSend(destinationName, "apply-once");

            boolean completedWithinTimeout = await(successfulAttempt);
            List<Boolean> observedFlags;
            synchronized (redeliveryFlags) {
                observedFlags = List.copyOf(redeliveryFlags);
            }

            boolean firstDeliveryRedelivered = flagAt(
                    observedFlags,
                    0
            );
            boolean secondDeliveryRedelivered = flagAt(
                    observedFlags,
                    1
            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("completedWithinTimeout", completedWithinTimeout);
            result.put("deliveryAttempts", attempts.get());
            result.put("redeliveryFlags", observedFlags);
            result.put(
                    "firstDeliveryRedelivered",
                    firstDeliveryRedelivered
            );
            result.put(
                    "secondDeliveryRedelivered",
                    secondDeliveryRedelivered
            );
            result.put("listenerErrorCount", listenerErrorCount.get());
            result.put("firstListenerError", firstListenerError.get());
            result.put("businessEffectCount", businessEffectCount.get());
            result.put(
                    "rollbackRedeliveryObserved",
                    completedWithinTimeout
                            && attempts.get() == 2
                            && !firstDeliveryRedelivered
                            && secondDeliveryRedelivered
                            && businessEffectCount.get() == 1
            );
            return result;
        }
        finally {
            try {
                shutdown(container);
            }
            finally {
                drainDestination(destinationName);
            }
        }
    }

    public synchronized Map<String, Object> observePublishProcessBoundary() {
        String processDestination = OBSERVABILITY_PROCESS_DESTINATION;
        String emptyDestination = OBSERVABILITY_EMPTY_DESTINATION;
        drainDestination(processDestination);
        drainDestination(emptyDestination);
        ObservationRegistry registry = ObservationRegistry.create();
        List<String> stoppedObservations = new CopyOnWriteArrayList<>();
        registry.observationConfig().observationHandler(
                new RecordingObservationHandler(stoppedObservations)
        );

        CountDownLatch processed = new CountDownLatch(1);
        DefaultMessageListenerContainer container = listenerContainer(
                processDestination,
                (MessageListener) message -> processed.countDown(),
                false,
                registry
        );
        try {
            container.start();

            JmsTemplate template = new JmsTemplate(connectionFactory);
            template.setObservationRegistry(registry);
            template.convertAndSend(processDestination, "observe-me");

            boolean processedWithinTimeout = await(processed);
            boolean processObservationCompleted = awaitObservation(
                    stoppedObservations,
                    "jms.message.process"
            );

            JmsTemplate boundedReceive = new JmsTemplate(connectionFactory);
            boundedReceive.setObservationRegistry(registry);
            boundedReceive.setReceiveTimeout(EMPTY_RECEIVE_TIMEOUT_MILLIS);
            Message emptyResult = boundedReceive.receive(emptyDestination);

            Set<String> uniqueObservationNames = new LinkedHashSet<>(
                    stoppedObservations
            );
            List<String> observationNames = new ArrayList<>(
                    uniqueObservationNames
            );
            Collections.sort(observationNames);

            boolean publishObserved = observationNames.contains(
                    "jms.message.publish"
            );
            boolean processObserved = processObservationCompleted
                    && observationNames.contains("jms.message.process");
            boolean receiveObserved = observationNames.contains(
                    "jms.message.receive"
            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("processedWithinTimeout", processedWithinTimeout);
            result.put("emptyReceiveReturnedNull", emptyResult == null);
            result.put("stoppedObservationNames", observationNames);
            result.put("publishObserved", publishObserved);
            result.put("processObserved", processObserved);
            result.put("receiveObserved", receiveObserved);
            result.put(
                    "spring61ObservationBoundaryObserved",
                    processedWithinTimeout
                            && emptyResult == null
                            && publishObserved
                            && processObserved
                            && !receiveObserved
            );
            return result;
        }
        finally {
            try {
                shutdown(container);
            }
            finally {
                try {
                    drainDestination(processDestination);
                }
                finally {
                    drainDestination(emptyDestination);
                }
            }
        }
    }

    private DefaultMessageListenerContainer listenerContainer(
            String destinationName,
            Object listener,
            boolean sessionTransacted,
            ObservationRegistry observationRegistry
    ) {
        DefaultMessageListenerContainer container =
                new DefaultMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setDestinationName(destinationName);
        container.setMessageListener(listener);
        container.setSessionTransacted(sessionTransacted);
        container.setConcurrentConsumers(1);
        container.setReceiveTimeout(200L);
        container.setRecoveryInterval(200L);
        if (observationRegistry != null) {
            container.setObservationRegistry(observationRegistry);
        }
        container.afterPropertiesSet();
        return container;
    }

    private boolean await(CountDownLatch latch) {
        try {
            return latch.await(
                    EXPERIMENT_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
            );
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean awaitObservation(
            List<String> observationNames,
            String expectedName
    ) {
        long deadline = System.nanoTime() + EXPERIMENT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (observationNames.contains(expectedName)) {
                return true;
            }
            try {
                Thread.sleep(10L);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return observationNames.contains(expectedName);
    }

    private boolean flagAt(List<Boolean> flags, int index) {
        return flags.size() > index && Boolean.TRUE.equals(flags.get(index));
    }

    private String text(Message message) throws JMSException {
        if (message instanceof TextMessage textMessage) {
            return textMessage.getText();
        }
        return String.valueOf(message);
    }

    private void drainDestination(String destinationName) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setReceiveTimeout(JmsTemplate.RECEIVE_TIMEOUT_NO_WAIT);
        for (int i = 0; i < MAX_DRAIN_MESSAGES; i++) {
            if (template.receive(destinationName) == null) {
                return;
            }
        }
        if (template.receive(destinationName) != null) {
            throw new IllegalStateException(
                    "Experiment queue contains more than "
                            + MAX_DRAIN_MESSAGES
                            + " stale messages: "
                            + destinationName
            );
        }
    }

    private void shutdown(DefaultMessageListenerContainer container) {
        try {
            container.stop();
        }
        finally {
            container.shutdown();
        }
    }

    private static final class RecordingObservationHandler
            implements ObservationHandler<Observation.Context> {

        private final List<String> stoppedObservations;

        private RecordingObservationHandler(
                List<String> stoppedObservations
        ) {
            this.stoppedObservations = stoppedObservations;
        }

        @Override
        public void onStop(Observation.Context context) {
            stoppedObservations.add(context.getName());
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }
}
