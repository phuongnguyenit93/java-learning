package com.example.learning.dop;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/paradigm/data-oriented/orders")
public class OrderPreviewController {

    private final OrderPreviewService service;

    public OrderPreviewController(OrderPreviewService service) {
        this.service = service;
    }

    @PostMapping(path = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> preview(
            @RequestBody Map<String, Object> order,
            @RequestParam(name = "discountPercent", defaultValue = "10") int discountPercent
    ) {
        OrderPreviewService.Outcome outcome = service.preview(order, discountPercent);
        return ResponseEntity.status(outcome.accepted() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY)
                .body(outcome.body());
    }
}
