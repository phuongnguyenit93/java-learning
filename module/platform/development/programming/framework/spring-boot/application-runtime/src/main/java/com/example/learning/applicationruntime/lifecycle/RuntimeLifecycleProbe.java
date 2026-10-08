package com.example.learning.applicationruntime.lifecycle;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RuntimeLifecycleProbe implements ApplicationRunner, ApplicationListener<ApplicationEvent> {

    private final ApplicationAvailability availability;
    private final CopyOnWriteArrayList<RuntimeObservation> observations = new CopyOnWriteArrayList<>();

    public RuntimeLifecycleProbe(ApplicationAvailability availability) {
        this.availability = availability;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationStartedEvent) {
            record("ApplicationStartedEvent");
            return;
        }

        if (event instanceof ApplicationReadyEvent) {
            record("ApplicationReadyEvent");
            return;
        }

        if (event instanceof AvailabilityChangeEvent<?> availabilityEvent) {
            if (availabilityEvent.getState() instanceof LivenessState livenessState) {
                record(
                        "LivenessState=" + livenessState,
                        livenessState.toString(),
                        availability.getReadinessState().toString()
                );
            } else if (availabilityEvent.getState() instanceof ReadinessState readinessState) {
                record(
                        "ReadinessState=" + readinessState,
                        availability.getLivenessState().toString(),
                        readinessState.toString()
                );
            }
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        record("ApplicationRunner");
    }

    public List<RuntimeObservation> snapshot() {
        return List.copyOf(observations);
    }

    private void record(String phase) {
        record(
                phase,
                availability.getLivenessState().toString(),
                availability.getReadinessState().toString()
        );
    }

    private void record(String phase, String liveness, String readiness) {
        observations.add(new RuntimeObservation(
                phase,
                liveness,
                readiness,
                Thread.currentThread().getName(),
                Instant.now().toString()
        ));
    }

    public record RuntimeObservation(
            String phase,
            String liveness,
            String readiness,
            String thread,
            String observedAt
    ) {
    }
}
