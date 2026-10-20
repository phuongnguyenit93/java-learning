package com.example.learning.oop.refactoring.controller;

import com.example.learning.oop.refactoring.service.AlertRefactoringService;
import com.example.learning.oop.refactoring.service.CheckoutRefactoringService;
import com.example.learning.oop.refactoring.service.DeliveryRefactoringService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static com.example.learning.oop.refactoring.dto.RefactoringDtos.*;

@RestController
@RequestMapping("/oop/refactoring")
public final class RefactoringExperimentsController {
    private final CheckoutRefactoringService checkout;
    private final DeliveryRefactoringService delivery;
    private final AlertRefactoringService alerts;

    public RefactoringExperimentsController(CheckoutRefactoringService checkout,
                                            DeliveryRefactoringService delivery,
                                            AlertRefactoringService alerts) {
        this.checkout = checkout;
        this.delivery = delivery;
        this.alerts = alerts;
    }

    @PostMapping("/checkout/compare")
    public CheckoutComparison checkout(@RequestBody CheckoutRequest input) {
        return checkout.compare(input);
    }

    @PostMapping("/delivery/compare")
    public DeliveryResult delivery(@RequestBody DeliveryRequest input) {
        return delivery.compare(input);
    }

    @PostMapping("/alerts/experiment")
    public AlertResult alerts(@RequestBody AlertRequest input) {
        return alerts.execute(input);
    }
}
