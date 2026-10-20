package com.example.learning.oop.abstraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.learning.oop.abstraction.controller.AbstractionExperimentsController;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.SeatAction;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.SeatScenarioRequest;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.ShippingQuoteRequest;
import com.example.learning.oop.abstraction.exception.AbstractionExceptionHandler;
import com.example.learning.oop.abstraction.service.BookCatalogService;
import com.example.learning.oop.abstraction.service.LeaveEvaluationService;
import com.example.learning.oop.abstraction.service.SeatContractService;
import com.example.learning.oop.abstraction.service.ShippingQuoteService;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class AbstractionExperimentsControllerTest {
    @Test
    void endpointsRemainScopedToThisChapter() {
        RequestMapping root = AbstractionExperimentsController.class.getAnnotation(RequestMapping.class);
        assertNotNull(root);
        assertEquals("/oop/abstraction", root.value()[0]);
        Set<String> postRoutes = Arrays.stream(AbstractionExperimentsController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .collect(Collectors.toSet());
        assertEquals(Set.of("/shipping/quote", "/seats/simulate", "/leave/evaluate", "/catalog/search"),
                postRoutes);
        RestControllerAdvice scope = AbstractionExceptionHandler.class.getAnnotation(RestControllerAdvice.class);
        assertEquals(AbstractionExperimentsController.class, scope.assignableTypes()[0]);
    }

    @Test
    void controllerDelegatesWithoutBusinessState() {
        var controller = new AbstractionExperimentsController(
                new ShippingQuoteService(), new SeatContractService(),
                new LeaveEvaluationService(), new BookCatalogService());
        assertEquals(15_000L, controller.shippingQuote(
                new ShippingQuoteRequest("ECONOMY", 5, 1500)).totalFeeVnd());
        assertEquals(4, controller.seatScenario(new SeatScenarioRequest(5,
                List.of(new SeatAction("RESERVE", 1)))).available());
    }

    @Test
    void chapterSpecificErrorsHaveClearHttpMeanings() {
        var advice = new AbstractionExceptionHandler();
        var invalid = advice.invalidArgument(new IllegalArgumentException("Invalid count"));
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        assertNotNull(invalid.getBody());
        assertEquals("INVALID_INPUT", invalid.getBody().code());
        var state = advice.invalidState(new IllegalStateException("Conflict"));
        assertEquals(HttpStatus.CONFLICT, state.getStatusCode());
        var overflow = advice.arithmeticOverflow(new ArithmeticException("Overflow"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, overflow.getStatusCode());
        assertTrue(overflow.getBody().message().contains("numeric"));
    }
}
