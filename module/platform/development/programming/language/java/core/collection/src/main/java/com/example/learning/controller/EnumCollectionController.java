package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/collection/enum")
public class EnumCollectionController {

    enum OrderStatus {
        NEW,
        PAID,
        PACKING,
        SHIPPED,
        CANCELLED
    }

    @GetMapping("/specialized-collections")
    public Map<String, Object> specializedCollections() {
        EnumSet<OrderStatus> active = EnumSet.of(
                OrderStatus.SHIPPED,
                OrderStatus.NEW,
                OrderStatus.PAID
        );

        EnumMap<OrderStatus, Integer> limits = new EnumMap<>(OrderStatus.class);
        limits.put(OrderStatus.SHIPPED, 40);
        limits.put(OrderStatus.NEW, 10);
        limits.put(OrderStatus.PAID, 20);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enumDeclarationOrder", List.of(OrderStatus.values()));
        result.put("enumSetOrder", new ArrayList<>(active));
        result.put("enumSetContainsPaid", active.contains(OrderStatus.PAID));
        result.put("enumMapKeyOrder", new ArrayList<>(limits.keySet()));
        result.put("enumMapValuesByKey", new LinkedHashMap<>(limits));
        result.put("closedKeySpace", OrderStatus.values().length);
        return result;
    }
}
