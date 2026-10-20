package com.example.learning.oop.encapsulation;

import com.example.learning.oop.encapsulation.service.EncapsulationExperimentService;
import com.example.learning.oop.encapsulation.controller.EncapsulationExperimentController;
import com.example.learning.oop.encapsulation.exception.EncapsulationExceptionHandler;
import static com.example.learning.oop.encapsulation.dto.EncapsulationDtos.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.junit.jupiter.api.Assertions.*;

class EncapsulationExperimentServiceTest {
    private final EncapsulationExperimentService service = new EncapsulationExperimentService();

    @Test
    void cartReportsSuccessFailedOperationsAndStableOldSnapshots() {
        CartResult result = service.simulateCart(new CartRequest(
                List.of(new CartLineInput("BOOK", 100L, 2)),
                List.of(new CartAction("put", "PEN", 50L, 1),
                        new CartAction("changeQuantity", "BOOK", null, 3),
                        new CartAction("changeQuantity", "BOOK", null, -1),
                        new CartAction("remove", "UNKNOWN", null, null))));
        assertEquals(200L, result.initial().totalVnd());
        assertEquals(350L, result.current().totalVnd());
        assertTrue(result.steps().get(0).accepted());
        assertFalse(result.steps().get(2).accepted());
        assertFalse(result.steps().get(3).accepted());
        assertEquals(result.steps().get(2).before(), result.steps().get(2).after());
        assertEquals(result.steps().get(3).before(), result.steps().get(3).after());
        assertThrows(UnsupportedOperationException.class, () -> result.current().lines().clear());
    }

    @Test
    void stockStepsPreserveStateOnInvalidReservation() {
        StockResult result = service.simulateStock(new StockRequest("BOX", 10, 0,
                List.of(new StockAction("reserve", 8),
                        new StockAction("reserve", 3),
                        new StockAction("ship", 4),
                        new StockAction("release", 2))));
        assertEquals(6, result.current().onHand());
        assertEquals(2, result.current().reserved());
        assertEquals(4, result.current().available());
        assertEquals(result.steps().get(1).before(), result.steps().get(1).after());
        assertFalse(result.steps().get(1).accepted());
    }

    @Test
    void eventShowsRealDefensiveCopyAgainstTwoWayMutation() {
        EventResult result = service.simulateEvent(new EventRequest(
                1000L, List.of("An", "Binh"), 2000L, "Changed", "New"));
        assertEquals(List.of("An", "Binh"), result.protectedSnapshot().attendeeNames());
        assertEquals(1000L, result.protectedSnapshot().startEpochMillis());
        assertEquals(2000L, result.mutatedDraft().startEpochMillis());
        assertEquals(List.of("Changed", "Binh", "New"), result.mutatedDraft().attendeeNames());
        assertEquals(result.protectedSnapshot(), result.afterGetterMutation());
        assertTrue(result.returnedListIsUnmodifiable());
    }

    @Test
    void orderRejectsIllegalLifecycleTransitionsAndKeepsState() {
        OrderResult result = service.simulateOrder(new OrderRequest(
                "O-1", List.of(new OrderItemInput("BOOK", 1)),
                List.of(new OrderAction("ship"), new OrderAction("markPaid"),
                        new OrderAction("cancel"), new OrderAction("ship"))));
        assertEquals("CREATED", result.initial().status());
        assertEquals("SHIPPED", result.current().status());
        assertFalse(result.steps().get(0).accepted());
        assertFalse(result.steps().get(2).accepted());
        assertEquals(result.steps().get(2).before(), result.steps().get(2).after());
        assertTrue(result.scope().contains("NO_REAL_PAYMENT"));
    }

    @Test
    void rejectsBadRequestsBeforeMutationAndLimitsWork() {
        assertThrows(IllegalArgumentException.class,
                () -> service.simulateCart(new CartRequest(null, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulateStock(new StockRequest("A", 10, 0,
                        List.of(new StockAction("unknown", 1)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulateEvent(new EventRequest(1L, List.of(), 2L, "A", "B")));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulateOrder(new OrderRequest("A", List.of(), List.of(new OrderAction("ship")))));
        List<StockAction> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) many.add(new StockAction("restock", 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulateStock(new StockRequest("BOX", 1, 0, many)));
    }

    @Test
    void controllerRoutesAndChapterScopedErrorsAreValid() {
        RequestMapping mapping = EncapsulationExperimentController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertEquals("/oop/encapsulation", mapping.value()[0]);
        Set<String> actions = Arrays.stream(EncapsulationExperimentController.class.getMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .collect(Collectors.toSet());
        assertEquals(Set.of("/cart/experiment", "/stock/experiment",
                "/event-snapshot/experiment", "/order/experiment"), actions);
        RestControllerAdvice scoped = EncapsulationExceptionHandler.class.getAnnotation(RestControllerAdvice.class);
        assertEquals(EncapsulationExperimentController.class, scoped.assignableTypes()[0]);
        var handler = new EncapsulationExceptionHandler();
        assertEquals(HttpStatus.BAD_REQUEST, handler.invalidInput(new IllegalArgumentException("bad")).getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                handler.rejectedOperation(new IllegalStateException("not allowed")).getStatusCode());
    }
}
