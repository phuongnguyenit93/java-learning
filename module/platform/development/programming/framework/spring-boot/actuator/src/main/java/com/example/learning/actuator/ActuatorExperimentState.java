package com.example.learning.actuator;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ActuatorExperimentState {

    private final AtomicBoolean dependencyAvailable = new AtomicBoolean(true);
    private final AtomicBoolean maintenanceMode = new AtomicBoolean(false);

    public boolean isDependencyAvailable() {
        return dependencyAvailable.get();
    }

    public void setDependencyAvailable(boolean available) {
        dependencyAvailable.set(available);
    }

    public boolean isMaintenanceMode() {
        return maintenanceMode.get();
    }

    public void setMaintenanceMode(boolean enabled) {
        maintenanceMode.set(enabled);
    }
}
