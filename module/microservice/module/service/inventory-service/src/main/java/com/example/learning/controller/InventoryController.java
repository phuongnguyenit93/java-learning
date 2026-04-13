package com.example.learning.controller;

import com.example.learning.dto.InventoryRequest;
import com.example.learning.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;


    @GetMapping("/in-stock")
    @ResponseStatus(HttpStatus.OK)
    public boolean isInStock(@RequestParam String skuCode,@RequestParam Integer quantity) {
        System.out.println(inventoryService.getClass().getName());
        return inventoryService.isInStock(skuCode,quantity);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public void createStock(@RequestBody @Valid InventoryRequest inventoryRequest) {
        inventoryService.createStock(inventoryRequest);
    }
}
