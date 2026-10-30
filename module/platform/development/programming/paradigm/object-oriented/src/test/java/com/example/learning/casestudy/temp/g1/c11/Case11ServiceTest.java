package com.example.learning.casestudy.temp.g1.c11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.learning.casestudy.temp.g1.c11.Case11Service.Case11Request;
import com.example.learning.casestudy.temp.g1.c11.Case11Service.OrderDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class Case11ServiceTest {
    private final Case11Service service = new Case11Service();

    @Test
    void paymentChangesStateWhilePreservingOrderIdentityAndOldDtoSnapshot() {
        var order = new Case11Service.Order("O-42");
        OrderDto before = Case11Service.toDto(order);
        order.markPaid();
        OrderDto after = Case11Service.toDto(order);

        assertNotSame(before, after);
        assertEquals("O-42", before.id());
        assertEquals(before.id(), after.id());
        assertEquals("NEW", before.status());
        assertEquals("PAID", after.status());
        assertEquals("O-42", order.id());
        assertEquals(Case11Service.Status.PAID, order.status());
    }

    @Test
    void executeProvidesStateSnapshotsForPaymentAndNoPayment() {
        var paid = service.execute(new Case11Request("O-42", 1));
        assertEquals(new OrderDto("O-42", "NEW"), paid.before());
        assertEquals(new OrderDto("O-42", "PAID"), paid.after());

        var notPaid = service.execute(new Case11Request("O-43", 0));
        assertEquals(new OrderDto("O-43", "NEW"), notPaid.before());
        assertEquals(new OrderDto("O-43", "NEW"), notPaid.after());
    }

    @Test
    void repeatedPaymentIsRejectedWithoutChangingTheOrderIdentity() {
        var order = new Case11Service.Order("O-42");
        order.markPaid();

        var invalid = assertThrows(IllegalStateException.class, order::markPaid);
        assertEquals("invalid transition", invalid.getMessage());
        assertEquals("O-42", order.id());
        assertEquals(Case11Service.Status.PAID, order.status());

        assertThrows(IllegalStateException.class,
                () -> service.execute(new Case11Request("O-42", 2)));
    }

    @Test
    void missingIdentityAndInvalidAttemptCountAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.execute(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case11Request(null, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case11Request("   ", 1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case11Request("O-42", -1)));
    }

    @Test
    void httpBoundaryReturnsSnapshotsAndMeaningfulErrors() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Case11Controller(service)).build();
        String path = "/api/v1/java/case-studies/groups/1/cases/11/execute";

        mvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"O-42\",\"paymentAttempts\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.before.id").value("O-42"))
                .andExpect(jsonPath("$.before.status").value("NEW"))
                .andExpect(jsonPath("$.after.id").value("O-42"))
                .andExpect(jsonPath("$.after.status").value("PAID"));

        mvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"O-42\",\"paymentAttempts\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

        mvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"\",\"paymentAttempts\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
