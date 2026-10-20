package com.example.learning.oop.composition.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orders own immutable line snapshots. Business service uses a repository
 * collaborator; it does not extend the repository implementation.
 */
public final class OrderPlacement {
    private OrderPlacement() {
    }

    public record Line(String sku, int quantity, long unitPriceCents) {
        public Line {
            if (sku == null || !sku.matches("[A-Za-z0-9_-]{1,40}")) {
                throw new IllegalArgumentException("sku must be 1-40 letters, digits, '_' or '-'");
            }
            if (quantity < 1 || quantity > 1000) {
                throw new IllegalArgumentException("quantity must be 1-1000");
            }
            if (unitPriceCents < 0 || unitPriceCents > 100_000_000L) {
                throw new IllegalArgumentException("unitPriceCents must be 0-100000000");
            }
            Math.multiplyExact(quantity, unitPriceCents);
        }

        public long subtotalCents() {
            return Math.multiplyExact(quantity, unitPriceCents);
        }
    }

    public record Order(String id, List<Line> lines, long totalCents) {
        public Order {
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,40}")) {
                throw new IllegalArgumentException("order ID must be 1-40 letters, digits, '_' or '-'");
            }
            if (lines == null || lines.isEmpty() || lines.size() > 20) {
                throw new IllegalArgumentException("order must have 1-20 lines");
            }
            // Copy structure; Line is an immutable record with only scalars.
            lines = List.copyOf(lines);
            long calculated = 0;
            for (Line line : lines) {
                calculated = Math.addExact(calculated, Objects.requireNonNull(line).subtotalCents());
            }
            if (calculated != totalCents) {
                throw new IllegalArgumentException("totalCents must match line amounts");
            }
        }

        public static Order create(String id, List<Line> lines) {
            if (lines == null || lines.isEmpty() || lines.size() > 20) {
                throw new IllegalArgumentException("order must have 1-20 lines");
            }
            long total = 0;
            for (Line line : lines) {
                total = Math.addExact(total, Objects.requireNonNull(line).subtotalCents());
            }
            return new Order(id, lines, total);
        }
    }

    public interface OrderRepository {
        void save(Order order);
        List<Order> findAll();
        int saveAttempts();
    }

    public static final class InMemoryOrderRepository implements OrderRepository {
        private final List<Order> stored = new ArrayList<>();
        private final boolean rejectSave;
        private int attempts;

        public InMemoryOrderRepository(boolean rejectSave) {
            this.rejectSave = rejectSave;
        }

        @Override
        public void save(Order order) {
            attempts++;
            Objects.requireNonNull(order);
            if (rejectSave) {
                throw new IllegalStateException("Simulated repository failure before save");
            }
            stored.add(order);
        }

        @Override
        public List<Order> findAll() {
            return List.copyOf(stored);
        }

        @Override
        public int saveAttempts() {
            return attempts;
        }
    }

    public static final class OrderService {
        private final OrderRepository repository;

        public OrderService(OrderRepository repository) {
            this.repository = Objects.requireNonNull(repository);
        }

        public void place(Order order) {
            repository.save(Objects.requireNonNull(order));
        }
    }
}
