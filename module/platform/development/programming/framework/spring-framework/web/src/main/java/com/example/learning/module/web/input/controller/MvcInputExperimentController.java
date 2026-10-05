package com.example.learning.module.web.input.controller;

import com.example.learning.module.web.input.model.CreateOrderRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/spring-web/input")
public class MvcInputExperimentController {

    /**
     * README: readme/en/menu/5.MvcBindingValidation/MvcBindingValidation.md#validation-and-binding-result
     * Purpose: Observe request-body decoding followed by object validation.
     */
    @PostMapping("/validated-body")
    public Map<String, Object> validatedBody(@Valid @RequestBody CreateOrderRequest request) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("javaType", request.getClass().getSimpleName());
        evidence.put("item", request.item());
        evidence.put("quantity", request.quantity());
        evidence.put("validationPassed", true);
        return evidence;
    }

    /**
     * README: readme/en/menu/5.MvcBindingValidation/MvcBindingValidation.md#mvc-method-validation-6-1
     * Purpose: Observe Spring MVC 6.1 built-in method validation for a direct parameter constraint.
     */
    @GetMapping("/method-validation")
    public Map<String, Object> methodValidation(
            @RequestParam
            @Min(value = 1, message = "quantity must be at least 1")
            int quantity
    ) {
        return Map.of(
                "quantity", quantity,
                "methodInvoked", true,
                "validationPath", "mvc-handler-method"
        );
    }
}
