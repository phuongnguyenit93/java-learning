package com.example.learning.oop.relationships.service;

import com.example.learning.oop.relationships.dto.RelationshipDtos.OwnershipRequest;
import com.example.learning.oop.relationships.dto.RelationshipDtos.OwnershipResponse;
import com.example.learning.oop.relationships.model.OwnershipScenario;
import org.springframework.stereotype.Service;

@Service
public final class OwnershipComparisonService {
    public OwnershipResponse compare(OwnershipRequest request) {
        if (request == null) throw new IllegalArgumentException("Ownership request is required");
        var outcome = OwnershipScenario.compare(request.teamId(), request.memberIds(),
                request.removeMemberId(), request.orderId(), request.orderLines(), request.closeOrder());
        return new OwnershipResponse(request.teamId(), outcome.teamMemberIds(),
                outcome.memberStillExists(), request.orderId(), outcome.totalBeforeClose(),
                outcome.linesBefore(), outcome.linesAfter(), outcome.closed(), true);
    }
}
