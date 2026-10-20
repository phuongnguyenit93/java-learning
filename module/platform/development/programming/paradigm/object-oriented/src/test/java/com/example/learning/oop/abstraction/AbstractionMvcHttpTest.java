package com.example.learning.oop.abstraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.learning.oop.abstraction.controller.AbstractionExperimentsController;
import com.example.learning.oop.abstraction.exception.AbstractionExceptionHandler;
import com.example.learning.oop.abstraction.service.BookCatalogService;
import com.example.learning.oop.abstraction.service.LeaveEvaluationService;
import com.example.learning.oop.abstraction.service.SeatContractService;
import com.example.learning.oop.abstraction.service.ShippingQuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Request mapping, JSON binding and chapter-scoped HTTP errors via Spring MockMvc. */
class AbstractionMvcHttpTest {
    @Test
    void allFourPostRoutesBindJsonAndPreserveBusinessSemantics() throws Exception {
        var controller = new AbstractionExperimentsController(
                new ShippingQuoteService(), new SeatContractService(),
                new LeaveEvaluationService(), new BookCatalogService());
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AbstractionExceptionHandler()).build();

        var shipping = mvc.perform(post("/oop/abstraction/shipping/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"EXPRESS\",\"distanceKm\":5,\"weightGrams\":1500}"))
                .andReturn().getResponse();
        assertEquals(200, shipping.getStatus());
        assertTrue(shipping.getContentAsString().contains("\"totalFeeVnd\":36000"));

        var seats = mvc.perform(post("/oop/abstraction/seats/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"capacity\":3,\"actions\":[{\"operation\":\"RESERVE\",\"seats\":2},{\"operation\":\"RESERVE\",\"seats\":2}]}"))
                .andReturn().getResponse();
        assertEquals(200, seats.getStatus());
        assertTrue(seats.getContentAsString().contains("\"available\":1"));
        assertTrue(seats.getContentAsString().contains("INSUFFICIENT_SEATS"));

        var leave = mvc.perform(post("/oop/abstraction/leave/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"policy\":\"TRIAL\",\"employeeId\":\"NV-01\",\"requestedDays\":3,\"remainingDays\":5}"))
                .andReturn().getResponse();
        assertEquals(200, leave.getStatus());
        assertTrue(leave.getContentAsString().contains("EXCEEDS_TRIAL_LIMIT"));

        var catalog = mvc.perform(post("/oop/abstraction/catalog/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"books\":[{\"isbn\":\"J-01\",\"title\":\"Java OOP\"}],\"keyword\":\"java\",\"limit\":5,\"lookupIsbn\":\"J-01\"}"))
                .andReturn().getResponse();
        assertEquals(200, catalog.getStatus());
        assertTrue(catalog.getContentAsString().contains("\"totalMatches\":1"));
        assertTrue(catalog.getContentAsString().contains("\"found\":true"));

        var invalid = mvc.perform(post("/oop/abstraction/shipping/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"EXPRESS\",\"distanceKm\":0,\"weightGrams\":1500}"))
                .andReturn().getResponse();
        assertEquals(400, invalid.getStatus());
        assertTrue(invalid.getContentAsString().contains("INVALID_INPUT"));
    }
}
