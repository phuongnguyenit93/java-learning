package com.example.learning.actuator;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/actuator-learning")
public class ActuatorLearningController {

    private static final Set<String> SUPPORTED_OUTCOMES = Set.of("success", "failure");

    private final ActuatorExperimentState state;
    private final MeterRegistry meterRegistry;
    private final LearningOperationsEndpoint learningOperationsEndpoint;

    public ActuatorLearningController(
            ActuatorExperimentState state,
            MeterRegistry meterRegistry,
            LearningOperationsEndpoint learningOperationsEndpoint
    ) {
        this.state = state;
        this.meterRegistry = meterRegistry;
        this.learningOperationsEndpoint = learningOperationsEndpoint;
    }

    /**
     * Related Knowledge:
     * readme/en/menu/4.Health/Health.md#custom-health-indicators
     * readme/vi/menu/4.Health/Health.md#custom-health-indicators
     */
    @PostMapping("/health/{available}")
    public Map<String, Object> setDependencyAvailable(@PathVariable boolean available) {
        state.setDependencyAvailable(available);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dependencyAvailable", available);
        result.put("observe", "/actuator/health/learningDependency");
        result.put("aggregate", "/actuator/health");
        return result;
    }

    /**
     * Related Knowledge:
     * readme/en/menu/7.MetricsMicrometer/MetricsMicrometer.md#tag-filtered-metrics
     * readme/vi/menu/7.MetricsMicrometer/MetricsMicrometer.md#tag-filtered-metrics
     */
    @PostMapping("/metrics/{outcome}")
    public Map<String, Object> recordMetric(@PathVariable String outcome) {
        String normalizedOutcome = outcome.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_OUTCOMES.contains(normalizedOutcome)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "outcome must be success or failure"
            );
        }

        Counter counter = Counter.builder("learning.requests")
                .description("Requests recorded by the Actuator learning experiment")
                .tag("outcome", normalizedOutcome)
                .register(meterRegistry);

        counter.increment();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outcome", normalizedOutcome);
        result.put("count", counter.count());
        result.put("observe", "/actuator/metrics/learning.requests?tag=outcome:" + normalizedOutcome);
        return result;
    }

    /**
     * Related Knowledge:
     * readme/en/menu/9.CustomEndpoints/CustomEndpoints.md#endpoint-operation-annotations
     * readme/vi/menu/9.CustomEndpoints/CustomEndpoints.md#endpoint-operation-annotations
     */
    @GetMapping("/custom-endpoint")
    public Map<String, Object> customEndpointState() {
        return learningOperationsEndpoint.readState();
    }

    /**
     * Related Knowledge:
     * readme/en/menu/9.CustomEndpoints/CustomEndpoints.md#technology-agnostic-custom-endpoints
     * readme/vi/menu/9.CustomEndpoints/CustomEndpoints.md#technology-agnostic-custom-endpoints
     */
    @PostMapping("/custom-endpoint/maintenance/{enabled}")
    public Map<String, Object> setMaintenanceMode(@PathVariable boolean enabled) {
        return learningOperationsEndpoint.setMaintenance(enabled);
    }
}
