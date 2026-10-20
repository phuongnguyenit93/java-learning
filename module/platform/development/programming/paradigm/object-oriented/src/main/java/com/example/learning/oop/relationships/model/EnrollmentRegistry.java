package com.example.learning.oop.relationships.model;

import com.example.learning.oop.relationships.dto.RelationshipDtos.EnrollmentAction;
import com.example.learning.oop.relationships.dto.RelationshipDtos.EnrollmentEvent;
import com.example.learning.oop.relationships.dto.RelationshipDtos.EnrollmentView;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry is the single owner of N:N association state.
 * Student and Course remain independent: the link owns its sequence number.
 */
public final class EnrollmentRegistry {
    private final Set<String> students;
    private final Set<String> courses;
    private final Map<Key, EnrollmentView> active = new LinkedHashMap<>();
    private int sequence;

    public EnrollmentRegistry(List<String> studentIds, List<String> courseIds) {
        students = validateIds("studentIds", studentIds);
        courses = validateIds("courseIds", courseIds);
    }

    private static Set<String> validateIds(String name, List<String> ids) {
        List<String> checked = RelationshipInputs.bounded(name, ids, 50, false);
        Set<String> set = new LinkedHashSet<>();
        for (String raw : checked) {
            String id = RelationshipInputs.nonBlank(name, raw);
            if (!set.add(id)) throw new IllegalArgumentException("Duplicate entry in " + name + ": " + id);
        }
        return Set.copyOf(set);
    }

    public List<EnrollmentAction> validateActions(List<EnrollmentAction> actions) {
        List<EnrollmentAction> checked = RelationshipInputs.bounded("actions", actions, 50, false);
        for (EnrollmentAction action : checked) {
            if (action.operation() == null
                    || (!action.operation().equals("ENROLL") && !action.operation().equals("CANCEL"))) {
                throw new IllegalArgumentException("operation must be ENROLL or CANCEL");
            }
            if (!students.contains(RelationshipInputs.nonBlank("studentId", action.studentId()))
                    || !courses.contains(RelationshipInputs.nonBlank("courseId", action.courseId()))) {
                throw new IllegalArgumentException("Enrollment references an unknown student or course");
            }
        }
        return checked;
    }

    public EnrollmentEvent apply(int step, EnrollmentAction action, NotificationGateway notifier) {
        Key key = new Key(action.studentId(), action.courseId());
        if (action.operation().equals("ENROLL")) {
            if (active.containsKey(key)) {
                return new EnrollmentEvent(step, "ENROLL", key.studentId, key.courseId,
                        false, "DUPLICATE_ENROLLMENT", false);
            }
            var enrollment = new EnrollmentView(key.studentId, key.courseId, ++sequence);
            active.put(key, enrollment);
            // A method-parameter dependency, not a persistent association.
            notifier.sendConfirmation(enrollment);
            return new EnrollmentEvent(step, "ENROLL", key.studentId, key.courseId,
                    true, "CREATED", true);
        }
        if (active.remove(key) == null) {
            return new EnrollmentEvent(step, "CANCEL", key.studentId, key.courseId,
                    false, "NOT_ENROLLED", false);
        }
        return new EnrollmentEvent(step, "CANCEL", key.studentId, key.courseId,
                true, "CANCELLED", false);
    }

    public List<EnrollmentView> active() {
        return active.values().stream()
                .sorted(Comparator.comparing(EnrollmentView::studentId)
                        .thenComparing(EnrollmentView::courseId)).toList();
    }

    public boolean uniquePairs() { return active.size() == Set.copyOf(active.keySet()).size(); }

    public interface NotificationGateway {
        void sendConfirmation(EnrollmentView enrollment);
    }

    private record Key(String studentId, String courseId) {}
}
