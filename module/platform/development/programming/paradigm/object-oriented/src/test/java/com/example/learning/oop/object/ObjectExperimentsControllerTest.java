package com.example.learning.oop.object;

import com.example.learning.oop.object.dto.ObjectExperimentDto;
import com.example.learning.oop.object.service.ObjectSemanticsService;
import com.example.learning.oop.object.service.ObjectCheckoutService;
import com.example.learning.oop.object.controller.ObjectExperimentsController;
import com.example.learning.oop.object.exception.ObjectExperimentExceptionHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class ObjectExperimentsControllerTest {

    @Test
    void routesRemainUnderUniqueObjectsNamespace() {
        RequestMapping root = ObjectExperimentsController.class.getAnnotation(RequestMapping.class);
        assertNotNull(root);
        assertEquals("/oop/objects", root.value()[0]);
        Set<String> postPaths = Arrays.stream(ObjectExperimentsController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(mapping -> mapping != null)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .collect(Collectors.toSet());
        assertEquals(Set.of("/references", "/equality", "/copies", "/absence", "/checkout"), postPaths);
    }

    @Test
    void controllerDelegatesToServicesWithoutKeepingState() {
        var controller = new ObjectExperimentsController(new ObjectSemanticsService(), new ObjectCheckoutService());
        assertEquals(3, controller.references(new ObjectExperimentDto.ReferenceRequest("x", 2, 1))
                .afterThroughOriginal());
        var result = controller.checkout(new ObjectExperimentDto.CheckoutRequest(
                "BOOK", 2, 1, new BigDecimal("10.00"), true));
        assertEquals("COMPLETED", result.outcome());
        assertTrue(result.simulatedOnly());
    }

    @Test
    void invalidInputHasExplicitBadRequestResponse() {
        var controller = new ObjectExperimentsController(new ObjectSemanticsService(), new ObjectCheckoutService());
        var response = new ObjectExperimentExceptionHandler().invalidInput(new IllegalArgumentException("quantity must be positive"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("quantity must be positive", response.getBody().error());
    }
}
