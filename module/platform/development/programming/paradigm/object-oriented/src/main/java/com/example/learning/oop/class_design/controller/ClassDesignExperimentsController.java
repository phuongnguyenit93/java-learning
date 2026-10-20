package com.example.learning.oop.class_design.controller;

import com.example.learning.oop.class_design.service.CheckoutScenarioService;
import com.example.learning.oop.class_design.service.LibraryScenarioService;
import com.example.learning.oop.class_design.service.TransferScenarioService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static com.example.learning.oop.class_design.dto.ClassDesignDtos.*;

/** HTTP transport only; all business decisions belong to services and models. */
@RestController
@RequestMapping("/oop/class-design")
public final class ClassDesignExperimentsController {
    private final LibraryScenarioService library;
    private final CheckoutScenarioService checkout;
    private final TransferScenarioService transfers;

    public ClassDesignExperimentsController(LibraryScenarioService library,
                                             CheckoutScenarioService checkout,
                                             TransferScenarioService transfers) {
        this.library = library;
        this.checkout = checkout;
        this.transfers = transfers;
    }

    @PostMapping("/library/experiment")
    public LibraryResult library(@RequestBody LibraryRequest request) {
        return library.run(request);
    }

    @PostMapping("/checkout/experiment")
    public CheckoutResult checkout(@RequestBody CheckoutRequest request) {
        return checkout.checkout(request);
    }

    @PostMapping("/transfers/experiment")
    public TransferResult transfer(@RequestBody TransferRequest request) {
        return transfers.transfer(request);
    }
}
