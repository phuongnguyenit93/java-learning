package com.example.learning.oop.language_mechanics.controller;

import com.example.learning.oop.language_mechanics.service.GenericBatchService;
import com.example.learning.oop.language_mechanics.service.ShippingScenarioService;
import com.example.learning.oop.language_mechanics.service.StockExperimentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static com.example.learning.oop.language_mechanics.dto.LanguageMechanicsDtos.*;

/** Delegates HTTP payloads to services; does not contain algorithms or business rules. */
@RestController
@RequestMapping("/oop/language-mechanics")
public final class LanguageMechanicsExperimentsController {
    private final GenericBatchService generics;
    private final StockExperimentService stock;
    private final ShippingScenarioService shipping;

    public LanguageMechanicsExperimentsController(GenericBatchService generics,
                                                   StockExperimentService stock,
                                                   ShippingScenarioService shipping) {
        this.generics = generics;
        this.stock = stock;
        this.shipping = shipping;
    }

    @PostMapping("/generics/experiment")
    public GenericBatchResult generics(@RequestBody GenericBatchRequest request) {
        return generics.process(request);
    }

    @PostMapping("/stock/experiment")
    public StockResult stock(@RequestBody StockRequest request) {
        return stock.execute(request);
    }

    @PostMapping("/shipping/experiment")
    public ShippingResult shipping(@RequestBody ShippingRequest request) {
        return shipping.quote(request);
    }
}
