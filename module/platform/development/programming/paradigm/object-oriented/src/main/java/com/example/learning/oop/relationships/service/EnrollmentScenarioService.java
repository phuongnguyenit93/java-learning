package com.example.learning.oop.relationships.service;

import com.example.learning.oop.relationships.dto.RelationshipDtos.EnrollmentEvent;
import com.example.learning.oop.relationships.dto.RelationshipDtos.EnrollmentRequest;
import com.example.learning.oop.relationships.dto.RelationshipDtos.EnrollmentResponse;
import com.example.learning.oop.relationships.model.EnrollmentRegistry;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public final class EnrollmentScenarioService {
    public EnrollmentResponse simulate(EnrollmentRequest request) {
        if (request == null) throw new IllegalArgumentException("Enrollment request is required");
        EnrollmentRegistry registry = new EnrollmentRegistry(request.studentIds(), request.courseIds());
        var validated = registry.validateActions(request.actions());
        AtomicInteger notifications = new AtomicInteger();
        EnrollmentRegistry.NotificationGateway gateway = enrollment -> notifications.incrementAndGet();
        var events = new ArrayList<EnrollmentEvent>();
        for (var action : validated) {
            events.add(registry.apply(events.size() + 1, action, gateway));
        }
        return new EnrollmentResponse(events, registry.active(), notifications.get(),
                registry.uniquePairs(), true);
    }
}
