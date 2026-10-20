package com.example.learning.oop.lifecycle.service;

import com.example.learning.oop.lifecycle.dto.LifecycleDtos.CreationRequest;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.CreationResult;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.ReferenceRequest;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.ReferenceResult;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.ReferenceSnapshot;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.ResourceRequest;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.ResourceResult;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.PublicationRequest;
import com.example.learning.oop.lifecycle.dto.LifecycleDtos.PublicationResult;
import com.example.learning.oop.lifecycle.model.CourseEnrollment;
import com.example.learning.oop.lifecycle.model.PublicationScenario;
import com.example.learning.oop.lifecycle.model.ReferenceGraph;
import com.example.learning.oop.lifecycle.model.TrackedResource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public final class LifecycleExperimentService {
    public CreationResult create(CreationRequest request) {
        if (request == null || request.capacity() == null || request.style() == null) {
            throw new IllegalArgumentException("courseCode, capacity and style are required");
        }
        List<String> students = request.students() == null ? List.of() : request.students();
        CourseEnrollment course = switch (request.style()) {
            case CONSTRUCTOR -> students.isEmpty()
                    ? new CourseEnrollment(request.courseCode(), request.capacity())
                    : new CourseEnrollment(request.courseCode(), request.capacity(), students);
            case STATIC_FACTORY -> CourseEnrollment.of(request.courseCode(), request.capacity(), students);
            case BUILDER -> {
                CourseEnrollment.Builder builder =
                        CourseEnrollment.builder(request.courseCode(), request.capacity());
                students.forEach(builder::addStudent);
                yield builder.build();
            }
        };
        return new CreationResult(request.style(), course.courseCode(), course.capacity(),
                course.students(), course.availableSeats());
    }

    public ReferenceResult references(ReferenceRequest request) {
        if (request == null || request.steps() == null || request.steps().size() > 30) {
            throw new IllegalArgumentException("steps must be present and contain at most 30 entries");
        }
        ReferenceGraph graph = new ReferenceGraph(request.nodes(), request.initialRoots());
        List<ReferenceSnapshot> snapshots = new ArrayList<>();
        snapshots.add(graph.snapshot(0));
        for (int i = 0; i < request.steps().size(); i++) {
            graph.apply(request.steps().get(i));
            snapshots.add(graph.snapshot(i + 1));
        }
        return new ReferenceResult(graph.nodeCount(), List.copyOf(snapshots),
                "These are paths from the supplied logical roots, not observed JVM GC roots. "
                + "No garbage collection, collection timing or native resource cleanup is inferred.");
    }

    public ResourceResult resources(ResourceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Resource request is required");
        }
        List<String> events = new ArrayList<>();
        TrackedResource outer = null;
        TrackedResource inner = null;
        Throwable failure = null;
        try {
            try (TrackedResource a = new TrackedResource("outer", request.outerCloseFails(), events);
                 TrackedResource b = new TrackedResource("inner", request.innerCloseFails(), events)) {
                outer = a;
                inner = b;
                events.add("body:start");
                if (request.bodyFails()) {
                    throw new IllegalStateException("body failed");
                }
                events.add("body:success");
            }
        } catch (IllegalStateException ex) {
            failure = ex;
        }
        List<String> suppressed = failure == null ? List.of()
                : java.util.Arrays.stream(failure.getSuppressed())
                        .map(Throwable::getMessage).toList();
        Map<String, Boolean> closed = new LinkedHashMap<>();
        closed.put("outer", outer != null && outer.closed());
        closed.put("inner", inner != null && inner.closed());
        return new ResourceResult(List.copyOf(events),
                failure == null ? null : failure.getMessage(),
                suppressed, Map.copyOf(closed), failure == null);
    }

    public PublicationResult publication(PublicationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Publication request is required");
        }
        PublicationScenario.Observation observation = PublicationScenario.compare(request.label());
        return new PublicationResult(observation.unsafeOnRegister(),
                observation.unsafeAfterConstruction(), observation.safeOnRegister(),
                observation.safeAfterConstruction(), observation.unsafeSubscriptions(),
                observation.safeSubscriptions());
    }
}
