package com.example.learning.casestudy.adapter.group8.case2;

import com.example.learning.casestudy.domain.group8.case2.Order2;
import com.example.learning.casestudy.port.group8.case2.OrderRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public record JdbcOrders(Connection connection) implements OrderRepository {
    public Optional<Order2> find(String id) {
        try (PreparedStatement stmt = connection.prepareStatement(
        "SELECT order_id, cents FROM orders WHERE order_id = ?")) {
            stmt.setString(1, id);
            try (ResultSet rows = stmt.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new Order2(rows.getString(1), rows.getLong(2)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("order lookup failed", e);
        }
    }
}
