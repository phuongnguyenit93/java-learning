package com.example.learning.actuator;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Endpoint(id = "learning")
public class LearningOperationsEndpoint {

    private final ActuatorExperimentState state;

    public LearningOperationsEndpoint(ActuatorExperimentState state) {
        this.state = state;
    }

    @ReadOperation
    public Map<String, Object> readState() {
        return snapshot();
    }

    @WriteOperation
    public Map<String, Object> setMaintenance(boolean enabled) {
        state.setMaintenanceMode(enabled);
        return snapshot();
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maintenance", state.isMaintenanceMode());
        result.put("dependencyAvailable", state.isDependencyAvailable());
        result.put("endpoint", "/actuator/learning");
        return result;
    }
}
