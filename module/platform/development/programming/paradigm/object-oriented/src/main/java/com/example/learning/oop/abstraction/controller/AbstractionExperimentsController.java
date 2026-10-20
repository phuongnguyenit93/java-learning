package com.example.learning.oop.abstraction.controller;

import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.CatalogSearchRequest;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.CatalogSearchResponse;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.LeaveEvaluationRequest;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.LeaveEvaluationResponse;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.SeatScenarioRequest;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.SeatScenarioResponse;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.ShippingQuoteRequest;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.ShippingQuoteResponse;
import com.example.learning.oop.abstraction.service.BookCatalogService;
import com.example.learning.oop.abstraction.service.LeaveEvaluationService;
import com.example.learning.oop.abstraction.service.SeatContractService;
import com.example.learning.oop.abstraction.service.ShippingQuoteService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Four independent deterministic experiments; no persistent application state. */
@RestController
@RequestMapping("/oop/abstraction")
public final class AbstractionExperimentsController {
    private final ShippingQuoteService shipping;
    private final SeatContractService seats;
    private final LeaveEvaluationService leave;
    private final BookCatalogService catalog;

    public AbstractionExperimentsController(
            ShippingQuoteService shipping, SeatContractService seats,
            LeaveEvaluationService leave, BookCatalogService catalog) {
        this.shipping = shipping;
        this.seats = seats;
        this.leave = leave;
        this.catalog = catalog;
    }

    @PostMapping("/shipping/quote")
    public ShippingQuoteResponse shippingQuote(@RequestBody ShippingQuoteRequest request) {
        return shipping.quote(request);
    }

    @PostMapping("/seats/simulate")
    public SeatScenarioResponse seatScenario(@RequestBody SeatScenarioRequest request) {
        return seats.simulate(request);
    }

    @PostMapping("/leave/evaluate")
    public LeaveEvaluationResponse leaveEvaluation(@RequestBody LeaveEvaluationRequest request) {
        return leave.evaluate(request);
    }

    @PostMapping("/catalog/search")
    public CatalogSearchResponse catalogSearch(@RequestBody CatalogSearchRequest request) {
        return catalog.search(request);
    }
}
