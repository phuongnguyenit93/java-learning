package com.example.learning.casestudy.application.group5.case10;

import com.example.learning.casestudy.port.group5.case10.TextStrategy;
import java.util.Objects;

public final class Engine {
    private final TextStrategy strategy;
    public Engine(TextStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy);
    }
    public String process(String input) {
        return strategy.transform(input);
    }
}
