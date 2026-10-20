package com.example.learning.oop.relationships;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.learning.oop.relationships.controller.RelationshipsExperimentsController;
import com.example.learning.oop.relationships.exception.RelationshipsExceptionHandler;
import com.example.learning.oop.relationships.service.DepartmentTransferService;
import com.example.learning.oop.relationships.service.EnrollmentScenarioService;
import com.example.learning.oop.relationships.service.OwnershipComparisonService;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class RelationshipsMvcHttpTest {
    private static RelationshipsExperimentsController controller() {
        return new RelationshipsExperimentsController(new DepartmentTransferService(),
                new EnrollmentScenarioService(), new OwnershipComparisonService());
    }

    @Test
    void routesAndErrorAdviceAreScopedToRelationships() {
        var root = RelationshipsExperimentsController.class.getAnnotation(RequestMapping.class);
        assertNotNull(root);
        assertEquals("/oop/relationships", root.value()[0]);
        Set<String> routes = Arrays.stream(RelationshipsExperimentsController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(x -> x != null)
                .flatMap(x -> Arrays.stream(x.value()))
                .collect(Collectors.toSet());
        assertEquals(Set.of("/departments/transfer", "/enrollments/simulate", "/ownership/compare"), routes);
        var advice = RelationshipsExceptionHandler.class.getAnnotation(RestControllerAdvice.class);
        assertNotNull(advice);
        assertEquals(RelationshipsExperimentsController.class, advice.assignableTypes()[0]);
    }

    @Test
    void allThreeHttpEndpointsBindJsonAndReturnActualResults() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller())
                .setControllerAdvice(new RelationshipsExceptionHandler()).build();
        var transfer = mvc.perform(post("/oop/relationships/departments/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"departments\":[{\"code\":\"A\",\"capacity\":1},{\"code\":\"B\",\"capacity\":1}],\"employees\":[{\"id\":\"E1\",\"departmentCode\":\"A\"}],\"actions\":[{\"employeeId\":\"E1\",\"targetDepartmentCode\":\"B\"}]}"))
                .andReturn().getResponse();
        assertEquals(200, transfer.getStatus());
        assertTrue(transfer.getContentAsString().contains("\"invariantHolds\":true"));
        assertTrue(transfer.getContentAsString().contains("\"departmentCode\":\"B\""));

        var enrollment = mvc.perform(post("/oop/relationships/enrollments/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentIds\":[\"S1\"],\"courseIds\":[\"C1\"],\"actions\":[{\"operation\":\"ENROLL\",\"studentId\":\"S1\",\"courseId\":\"C1\"},{\"operation\":\"ENROLL\",\"studentId\":\"S1\",\"courseId\":\"C1\"}]}"))
                .andReturn().getResponse();
        assertEquals(200, enrollment.getStatus());
        assertTrue(enrollment.getContentAsString().contains("DUPLICATE_ENROLLMENT"));
        assertTrue(enrollment.getContentAsString().contains("\"notificationsSent\":1"));
        assertTrue(enrollment.getContentAsString().contains("\"uniquePairs\":true"));

        var ownership = mvc.perform(post("/oop/relationships/ownership/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"A\",\"memberIds\":[\"An\",\"Binh\"],\"removeMemberId\":\"An\",\"orderId\":\"O1\",\"orderLines\":[{\"sku\":\"BOOK\",\"quantity\":2,\"unitPriceVnd\":10000}],\"closeOrder\":true}"))
                .andReturn().getResponse();
        assertEquals(200, ownership.getStatus());
        assertTrue(ownership.getContentAsString().contains("\"orderTotalBeforeCloseVnd\":20000"));
        assertTrue(ownership.getContentAsString().contains("\"ownedLinesAfterAction\":0"));
        assertTrue(ownership.getContentAsString().contains("\"removedMemberStillExistsIndependently\":true"));
    }

    @Test
    void invalidInputAndOverflowGetExplicitErrorResponses() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller())
                .setControllerAdvice(new RelationshipsExceptionHandler()).build();
        var invalid = mvc.perform(post("/oop/relationships/enrollments/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentIds\":[\"S1\"],\"courseIds\":[\"C1\"],\"actions\":[{\"operation\":\"ENROLL\",\"studentId\":\"S1\",\"courseId\":\"C9\"}]}"))
                .andReturn().getResponse();
        assertEquals(400, invalid.getStatus());
        assertTrue(invalid.getContentAsString().contains("INVALID_INPUT"));
        var overflow = mvc.perform(post("/oop/relationships/ownership/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"A\",\"memberIds\":[\"An\"],\"removeMemberId\":\"An\",\"orderId\":\"O1\",\"orderLines\":[{\"sku\":\"BOOK\",\"quantity\":2,\"unitPriceVnd\":9223372036854775807}],\"closeOrder\":true}"))
                .andReturn().getResponse();
        assertEquals(422, overflow.getStatus());
        assertTrue(overflow.getContentAsString().contains("ARITHMETIC_OVERFLOW"));
    }

    @Test
    void scopedHandlerDoesNotRequireControllerLevelExceptionHandlers() {
        var advice = new RelationshipsExceptionHandler();
        assertEquals(HttpStatus.BAD_REQUEST,
                advice.invalidInput(new IllegalArgumentException("invalid")).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,
                advice.stateConflict(new IllegalStateException("conflict")).getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                advice.arithmeticOverflow(new ArithmeticException("overflow")).getStatusCode());
    }
}
