package com.example.learning.transaction.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransactionExperimentStore {

    private final JdbcTemplate jdbcTemplate;

    public TransactionExperimentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void add(String scenarioId, String marker) {
        jdbcTemplate.update(
                "insert into tx_demo_entry(scenario_id, marker) values (?, ?)",
                scenarioId,
                marker
        );
    }

    public List<String> markers(String scenarioId) {
        return jdbcTemplate.queryForList(
                "select marker from tx_demo_entry where scenario_id = ? order by id",
                String.class,
                scenarioId
        );
    }

    public void delete(String scenarioId) {
        jdbcTemplate.update(
                "delete from tx_demo_entry where scenario_id = ?",
                scenarioId
        );
    }
}
