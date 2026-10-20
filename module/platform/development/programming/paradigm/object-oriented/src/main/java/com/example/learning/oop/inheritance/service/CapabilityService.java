package com.example.learning.oop.inheritance.service;

import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.CapabilitiesRequest;
import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.CapabilitiesResponse;
import com.example.learning.oop.inheritance.model.CapabilityResolution;
import org.springframework.stereotype.Service;

@Service
public final class CapabilityService {
    public CapabilitiesResponse resolve(CapabilitiesRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Capabilities request is required");
        }
        CapabilityResolution.Result result = CapabilityResolution.resolve(request.itemId(), request.tagsCsv());
        return new CapabilitiesResponse(result.explicitlyResolved(), result.classWins(),
                result.tags(), true);
    }
}
