package com.example.learning.oop.inheritance.service;

import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.ConstructorRequest;
import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.ConstructorResponse;
import com.example.learning.oop.inheritance.model.ConstructorProbe;
import org.springframework.stereotype.Service;

@Service
public final class ConstructorTraceService {
    public ConstructorResponse trace(ConstructorRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Constructor request is required");
        }
        ConstructorProbe.Trace result = ConstructorProbe.run(request.initialLabel());
        return new ConstructorResponse(result.unsafe(), result.safe(),
                result.valueDuringParent(), result.valueAfterConstruction(), true);
    }
}
