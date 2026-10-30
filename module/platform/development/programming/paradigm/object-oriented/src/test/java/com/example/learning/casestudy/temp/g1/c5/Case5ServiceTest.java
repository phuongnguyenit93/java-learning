package com.example.learning.casestudy.temp.g1.c5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Case5ServiceTest {

    private final Case5Service service = new Case5Service();

    @Test
    void sendsCsvFromRepositoryThroughEmailGateway() {
        List<String> mailbox = new ArrayList<>();
        Case5Service.SalesRepository fakeRepo = () -> List.of(new Case5Service.Sale("S1", 900));

        new Case5Service.SendReport(fakeRepo, mailbox::add,
                new Case5Service.ReportFormatter()).execute();

        assertEquals(List.of("id,cents\nS1,900\n"), mailbox);
    }

    @Test
    void propagatesEmailFailureWithoutChangingPureFormatter() {
        Case5Service.SalesRepository fakeRepo = () -> List.of(new Case5Service.Sale("S1", 900));
        Case5Service.ReportFormatter formatter = new Case5Service.ReportFormatter();
        Case5Service.EmailGateway failing = ignored -> {
            throw new IllegalStateException("SMTP unavailable");
        };

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new Case5Service.SendReport(fakeRepo, failing, formatter).execute());

        assertEquals("SMTP unavailable", ex.getMessage());
        assertEquals("id,cents\nS1,900\n", formatter.format(fakeRepo.findAll()));
    }

    @Test
    void executeUsesSubmittedSalesAndSimulatesOneEmail() {
        Case5Service.ExecuteResponse result = service.execute(new Case5Service.ExecuteRequest(
                List.of(new Case5Service.Sale("S1", 900), new Case5Service.Sale("S2", 150)), false));

        assertEquals("id,cents\nS1,900\nS2,150\n", result.csv());
        assertEquals(1, result.simulatedEmailsSent());
    }

    @Test
    void emptyRepositoryStillProducesCsvHeader() {
        Case5Service.ExecuteResponse result = service.execute(
                new Case5Service.ExecuteRequest(List.of(), false));

        assertEquals("id,cents\n", result.csv());
        assertEquals(1, result.simulatedEmailsSent());
    }

    @Test
    void simulatedTransportFailurePropagatesOriginalError() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.execute(new Case5Service.ExecuteRequest(
                        List.of(new Case5Service.Sale("S1", 900)), true)));

        assertEquals("SMTP unavailable", ex.getMessage());
    }

    @Test
    void invalidInputsAreRejectedInsteadOfProducingAmbiguousCsv() {
        assertThrows(IllegalArgumentException.class, () -> service.execute(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case5Service.ExecuteRequest(null, false)));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case5Service.ExecuteRequest(
                        List.of(new Case5Service.Sale("", 20)), false)));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute(new Case5Service.ExecuteRequest(
                        List.of(new Case5Service.Sale("comma,id", 20)), false)));
    }
}
