package com.example.learning.oop.modeling.model;

/** Expected domain rejection for a modeled rule, not an HTTP-layer error. */
public final class ModelRuleViolation extends IllegalStateException {
    private final String rule;
    public ModelRuleViolation(String rule, String message) {
        super(message);
        this.rule = rule;
    }
    public String rule() { return rule; }
}
