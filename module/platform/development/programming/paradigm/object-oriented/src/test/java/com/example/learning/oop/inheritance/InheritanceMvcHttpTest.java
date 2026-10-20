package com.example.learning.oop.inheritance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.learning.oop.inheritance.controller.InheritanceExperimentsController;
import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.PayrollRequest;
import com.example.learning.oop.inheritance.exception.InheritanceExceptionHandler;
import com.example.learning.oop.inheritance.service.CapabilityService;
import com.example.learning.oop.inheritance.service.ConstructorTraceService;
import com.example.learning.oop.inheritance.service.DeliveryComparisonService;
import com.example.learning.oop.inheritance.service.PayrollInheritanceService;
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

class InheritanceMvcHttpTest {
    @Test
    void routesAndAdviceAreRestrictedToTheChapter() {
        var root = InheritanceExperimentsController.class.getAnnotation(RequestMapping.class);
        assertNotNull(root);
        assertEquals("/oop/inheritance", root.value()[0]);
        var postRoutes = Arrays.stream(InheritanceExperimentsController.class.getDeclaredMethods())
                .map(m -> m.getAnnotation(PostMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .collect(Collectors.toSet());
        assertEquals(Set.of("/payroll/calculate", "/delivery/compare",
                "/constructors/trace", "/capabilities/resolve"), postRoutes);
        var advice = InheritanceExceptionHandler.class.getAnnotation(RestControllerAdvice.class);
        assertNotNull(advice);
        assertEquals(InheritanceExperimentsController.class, advice.assignableTypes()[0]);
    }

    @Test
    void fourRealPostEndpointsBindJsonAndReturnObservableOutputs() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller())
                .setControllerAdvice(new InheritanceExceptionHandler()).build();
        var salary = mvc.perform(post("/oop/inheritance/payroll/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"HOURLY\",\"employeeId\":\"H1\",\"employeeName\":\"Ada\",\"hourlyRateVnd\":130000,\"workedHours\":160}"))
                .andReturn().getResponse();
        assertEquals(200, salary.getStatus());
        assertTrue(salary.getContentAsString().contains("\"monthlyPayVnd\":20800000"));
        assertTrue(salary.getContentAsString().contains("\"declaredType\":\"Employee\""));

        var delivery = mvc.perform(post("/oop/inheritance/delivery/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"distanceKm\":3,\"priority\":true,\"holiday\":true}"))
                .andReturn().getResponse();
        assertEquals(200, delivery.getStatus());
        assertTrue(delivery.getContentAsString().contains("\"inheritanceFeeVnd\":25000"));
        assertTrue(delivery.getContentAsString().contains("\"compositionFeeVnd\":25000"));
        assertTrue(delivery.getContentAsString().contains("\"equal\":true"));

        var init = mvc.perform(post("/oop/inheritance/constructors/trace")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"initialLabel\":\"Ready\"}"))
                .andReturn().getResponse();
        assertEquals(200, init.getStatus());
        assertTrue(init.getContentAsString().contains("\"unsafeValueObservedByParent\":\"null\""));
        assertTrue(init.getContentAsString().contains("post-construction value: Ready"));

        var mixin = mvc.perform(post("/oop/inheritance/capabilities/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":\"Doc1\",\"tagsCsv\":\"Java, OOP\"}"))
                .andReturn().getResponse();
        assertEquals(200, mixin.getStatus());
        assertTrue(mixin.getContentAsString().contains("PRINT+SEARCH:Doc1"));
        assertTrue(mixin.getContentAsString().contains("CLASS_WINS"));
        assertTrue(mixin.getContentAsString().contains("\"normalizedTags\":[\"java\",\"oop\"]"));
    }

    @Test
    void badRequestAndOverflowHaveBoundedErrorMapping() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller())
                .setControllerAdvice(new InheritanceExceptionHandler()).build();
        var bad = mvc.perform(post("/oop/inheritance/delivery/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"distanceKm\":5,\"priority\":false,\"holiday\":true}"))
                .andReturn().getResponse();
        assertEquals(400, bad.getStatus());
        assertTrue(bad.getContentAsString().contains("INVALID_INPUT"));

        var overflow = mvc.perform(post("/oop/inheritance/payroll/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"HOURLY\",\"employeeId\":\"H1\",\"employeeName\":\"Ada\",\"hourlyRateVnd\":9223372036854775807,\"workedHours\":2}"))
                .andReturn().getResponse();
        assertEquals(422, overflow.getStatus());
        assertTrue(overflow.getContentAsString().contains("ARITHMETIC_OVERFLOW"));
    }

    @Test
    void directControllerAndExceptionAdviceHaveNoSharedState() {
        var c = controller();
        assertEquals(20_000L, c.calculatePay(new PayrollRequest(
                "SALARIED", "E", "Name", 20_000L, 0, 0)).monthlyPayVnd());
        var advice = new InheritanceExceptionHandler();
        assertEquals(HttpStatus.BAD_REQUEST,
                advice.badInput(new IllegalArgumentException("wrong")).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,
                advice.invalidState(new IllegalStateException("state")).getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                advice.calculationOverflow(new ArithmeticException("overflow")).getStatusCode());
    }

    private static InheritanceExperimentsController controller() {
        return new InheritanceExperimentsController(new PayrollInheritanceService(),
                new DeliveryComparisonService(), new ConstructorTraceService(),
                new CapabilityService());
    }
}
