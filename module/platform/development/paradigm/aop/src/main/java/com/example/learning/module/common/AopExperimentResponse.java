package com.example.learning.module.common;

import java.util.List;
import java.util.Map;

public record AopExperimentResponse(
        Object result,
        List<String> events,
        Map<String, Object> facts
) {

    public static AopExperimentResponse of(
            Object result,
            List<String> events
    ) {
        return new AopExperimentResponse(
                result,
                events,
                Map.of()
        );
    }
}
