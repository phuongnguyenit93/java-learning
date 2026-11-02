package com.example.learning.casestudy.adapter.group9.case1;

import com.example.learning.casestudy.application.group9.case1.PricingService;
import com.example.learning.casestudy.domain.group9.case1.Plan;

public final class WebController {
    private final PricingService service;
    public WebController(PricingService service) { this.service = service; }
    public int quote(Plan plan) { return service.quote(plan, 1000); }
}
