package com.example.learning.oop.relationships.model;

import com.example.learning.oop.relationships.dto.RelationshipDtos.OrderLineInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregated team members exist independently in a directory.
 * Composed order lines are created and owned exclusively by the order and
 * become unavailable when that order closes.
 */
public final class OwnershipScenario {
    private OwnershipScenario() {}

    public static Result compare(String teamId, List<String> memberIds, String memberToRemove,
                                 String orderId, List<OrderLineInput> requestedLines,
                                 boolean closeOrder) {
        RelationshipInputs.nonBlank("teamId", teamId);
        RelationshipInputs.nonBlank("orderId", orderId);
        String removedId = RelationshipInputs.nonBlank("removeMemberId", memberToRemove);
        List<String> names = RelationshipInputs.bounded("memberIds", memberIds, 50, false);
        List<OrderLineInput> inputs = RelationshipInputs.bounded("orderLines", requestedLines, 30, false);
        Map<String, Member> directory = new LinkedHashMap<>();
        for (String raw : names) {
            String id = RelationshipInputs.nonBlank("member id", raw);
            if (directory.putIfAbsent(id, new Member(id)) != null) {
                throw new IllegalArgumentException("Duplicate member: " + id);
            }
        }
        if (!directory.containsKey(removedId)) {
            throw new IllegalArgumentException("Member to remove is not part of the team");
        }
        // All validation and arithmetic occurs before changing the aggregates.
        Order order = new Order();
        for (OrderLineInput input : inputs) order.add(input);
        long totalBefore = order.total();
        int linesBefore = order.count();

        Team team = new Team();
        team.members.addAll(directory.values());
        team.members.remove(directory.get(removedId));
        if (closeOrder) order.close();
        return new Result(team.members.stream().map(x -> x.id).sorted().toList(),
                directory.containsKey(removedId), totalBefore, linesBefore,
                order.count(), order.closed);
    }

    public record Result(List<String> teamMemberIds, boolean memberStillExists,
                         long totalBeforeClose, int linesBefore, int linesAfter, boolean closed) {
        public Result { teamMemberIds = List.copyOf(teamMemberIds); }
    }

    private record Member(String id) {}

    private static final class Team {
        private final Set<Member> members = new LinkedHashSet<>();
    }

    private static final class Order {
        private final List<OrderLine> lines = new ArrayList<>();
        private final Set<String> skus = new LinkedHashSet<>();
        private boolean closed;

        void add(OrderLineInput input) {
            String sku = RelationshipInputs.nonBlank("sku", input.sku());
            if (input.quantity() <= 0 || input.quantity() > 1000
                    || input.unitPriceVnd() < 0 || !skus.add(sku)) {
                throw new IllegalArgumentException("Invalid or duplicate order line");
            }
            long total = Math.multiplyExact(input.quantity(), input.unitPriceVnd());
            lines.add(new OrderLine(sku, total));
        }

        long total() {
            long sum = 0;
            for (OrderLine line : lines) sum = Math.addExact(sum, line.subtotal);
            return sum;
        }

        int count() { return lines.size(); }

        void close() {
            closed = true;
            lines.clear();
            skus.clear();
        }
    }

    private record OrderLine(String sku, long subtotal) {}
}
