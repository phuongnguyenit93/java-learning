package com.example.learning.oop.practice.controller;

import com.example.learning.oop.practice.service.PracticeExperimentService;
import static com.example.learning.oop.practice.dto.PracticeDtos.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin Servlet adapter for the practice chapter's executable exercises. */
@RestController
@RequestMapping("/oop/practice")
public final class PracticeExperimentController {
    private final PracticeExperimentService service;

    public PracticeExperimentController(PracticeExperimentService service) {
        this.service = service;
    }

    @PostMapping("/bank/experiment")
    public BankResult bank(@RequestBody BankRequest request) { return service.bank(request); }

    @PostMapping("/orders/experiment")
    public OrderResult orders(@RequestBody OrderRequest request) { return service.order(request); }

    @PostMapping("/library/experiment")
    public LibraryResult library(@RequestBody LibraryRequest request) { return service.library(request); }
}
