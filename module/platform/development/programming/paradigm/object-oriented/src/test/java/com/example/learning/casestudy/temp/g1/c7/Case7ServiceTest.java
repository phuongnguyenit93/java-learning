package com.example.learning.casestudy.temp.g1.c7;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Case7ServiceTest {
    private final Case7Service service = new Case7Service();

    @Test
    void confirmedPaymentKeepsDomainAndAllRepresentationsConsistent() {
        Case7Service.RunResult result = service.execute(new Case7Service.RunRequest("P-3", true));

        assertFalse(result.initialResponse().completed());
        assertEquals("P-3", result.initialResponse().id());
        assertEquals("P-3", result.persistence().paymentId());
        assertEquals("PAID", result.persistence().dbStatus());
        assertEquals("P-3", result.api().id());
        assertTrue(result.api().completed());
        assertEquals("Đã thanh toán", result.presentation().title());
        assertEquals(Case7Service.Status.PAID, result.domainStatus());
    }

    @Test
    void pendingPaymentHasIndependentDatabaseApiAndViewValues() {
        Case7Service.RunResult result = service.execute(new Case7Service.RunRequest("P-19", false));

        assertEquals("P-19", result.persistence().paymentId());
        assertEquals("NEW", result.persistence().dbStatus());
        assertEquals("P-19", result.api().id());
        assertFalse(result.api().completed());
        assertEquals("Đang chờ", result.presentation().title());
        assertEquals(Case7Service.Status.NEW, result.domainStatus());
    }

    @Test
    void changingPresentationDoesNotChangePaymentDomainOrDatabaseMapping() {
        Case7Service.Payment payment = new Case7Service.Payment("P-5");
        payment.confirm();

        Case7Service.PaymentRow row = Case7Service.toRow(payment);
        Case7Service.PaymentResponse response = Case7Service.toResponse(payment);
        Case7Service.PaymentView view = Case7Service.toView(response);

        assertEquals("PAID", payment.status().name());
        assertEquals("PAID", row.dbStatus());
        assertTrue(response.completed());
        assertEquals("Đã thanh toán", view.title());
    }

    @Test
    void rejectsInvalidRequestsAndPaymentIds() {
        assertEquals("request", assertThrows(IllegalArgumentException.class,
                () -> service.execute(null)).getMessage());
        assertEquals("confirm", assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case7Service.RunRequest("P-3", null))).getMessage());
        assertEquals("id", assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case7Service.RunRequest(null, true))).getMessage());
        assertEquals("id", assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case7Service.RunRequest("  ", true))).getMessage());
    }
}
