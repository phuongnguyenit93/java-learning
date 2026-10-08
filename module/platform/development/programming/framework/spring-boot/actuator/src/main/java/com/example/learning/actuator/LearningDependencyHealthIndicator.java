package com.example.learning.actuator;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("learningDependency")
public class LearningDependencyHealthIndicator implements HealthIndicator {

    private final ActuatorExperimentState state;

    public LearningDependencyHealthIndicator(ActuatorExperimentState state) {
        this.state = state;
    }

    @Override
    public Health health() {
        boolean available = state.isDependencyAvailable();

        Health.Builder builder = available
                ? Health.up()
                : Health.down();

        return builder
                .withDetail("dependency", "learning-dependency")
                .withDetail("available", available)
                .build();
    }
}
