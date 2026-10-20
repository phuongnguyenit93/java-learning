package com.example.learning.oop.relationships.dto;

import java.util.List;

public final class RelationshipDtos {
    private RelationshipDtos() {
    }

    public record DepartmentInput(String code, int capacity) {}
    public record EmployeeInput(String id, String departmentCode) {}
    public record TransferAction(String employeeId, String targetDepartmentCode) {}
    public record TransferRequest(List<DepartmentInput> departments,
                                  List<EmployeeInput> employees, List<TransferAction> actions) {}
    public record TransferEvent(int step, String employeeId, String previousDepartment,
                                String targetDepartment, boolean accepted, String reason) {}
    public record DepartmentView(String code, int capacity, List<String> employees) {
        public DepartmentView { employees = List.copyOf(employees); }
    }
    public record EmployeeAssignment(String id, String departmentCode) {}
    public record TransferResponse(List<TransferEvent> events, List<DepartmentView> departments,
                                   List<EmployeeAssignment> employees, boolean invariantHolds,
                                   boolean simulatedOnly) {
        public TransferResponse {
            events = List.copyOf(events);
            departments = List.copyOf(departments);
            employees = List.copyOf(employees);
        }
    }

    public record EnrollmentAction(String operation, String studentId, String courseId) {}
    public record EnrollmentRequest(List<String> studentIds, List<String> courseIds,
                                    List<EnrollmentAction> actions) {}
    public record EnrollmentEvent(int step, String operation, String studentId, String courseId,
                                  boolean accepted, String outcome, boolean notificationSent) {}
    public record EnrollmentView(String studentId, String courseId, int enrollmentSequence) {}
    public record EnrollmentResponse(List<EnrollmentEvent> events, List<EnrollmentView> activeEnrollments,
                                     int notificationsSent, boolean uniquePairs,
                                     boolean simulatedOnly) {
        public EnrollmentResponse {
            events = List.copyOf(events);
            activeEnrollments = List.copyOf(activeEnrollments);
        }
    }

    public record OrderLineInput(String sku, int quantity, long unitPriceVnd) {}
    public record OwnershipRequest(String teamId, List<String> memberIds, String removeMemberId,
                                   String orderId, List<OrderLineInput> orderLines, boolean closeOrder) {}
    public record OwnershipResponse(String teamId, List<String> teamMembersAfterRemoval,
                                    boolean removedMemberStillExistsIndependently,
                                    String orderId, long orderTotalBeforeCloseVnd,
                                    int ownedLinesBeforeClose, int ownedLinesAfterAction,
                                    boolean orderClosed, boolean simulatedOnly) {
        public OwnershipResponse { teamMembersAfterRemoval = List.copyOf(teamMembersAfterRemoval); }
    }

    public record ApiError(String code, String message) {}
}
