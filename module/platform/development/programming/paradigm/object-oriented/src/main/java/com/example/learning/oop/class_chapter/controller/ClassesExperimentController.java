package com.example.learning.oop.class_chapter.controller;

import com.example.learning.oop.class_chapter.service.ClassesExperimentService;
import static com.example.learning.oop.class_chapter.dto.ClassesExperimentDtos.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP boundary; simulation and domain rules live in the service/model. */
@RestController
@RequestMapping("/oop/classes")
public final class ClassesExperimentController {
    private final ClassesExperimentService service;

    public ClassesExperimentController(ClassesExperimentService service) {
        this.service = service;
    }

    @PostMapping("/inventory/experiment")
    public InventoryResult inventory(@RequestBody InventoryRequest request) {
        return service.simulateInventory(request);
    }

    @PostMapping("/workshops/experiment")
    public WorkshopResult workshops(@RequestBody WorkshopRequest request) {
        return service.simulateWorkshops(request);
    }

    @PostMapping("/bank/experiment")
    public BankResult bank(@RequestBody BankRequest request) {
        return service.simulateBank(request);
    }

}
