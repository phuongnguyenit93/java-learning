package com.example.learning.oop.relationships.service;

import com.example.learning.oop.relationships.dto.RelationshipDtos.TransferRequest;
import com.example.learning.oop.relationships.dto.RelationshipDtos.TransferResponse;
import com.example.learning.oop.relationships.model.DepartmentGraph;
import org.springframework.stereotype.Service;

@Service
public final class DepartmentTransferService {
    public TransferResponse simulate(TransferRequest request) {
        if (request == null) throw new IllegalArgumentException("Transfer request is required");
        DepartmentGraph graph = new DepartmentGraph(request.departments(), request.employees());
        var events = graph.transfer(request.actions());
        return new TransferResponse(events, graph.departments(), graph.employees(),
                graph.invariantHolds(), true);
    }
}
