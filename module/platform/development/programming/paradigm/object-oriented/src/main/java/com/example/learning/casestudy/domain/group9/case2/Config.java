package com.example.learning.casestudy.domain.group9.case2;

import com.example.learning.casestudy.domain.group9.case2.Environment;

public record Config(Environment environment, String endpoint) {
    public Config {
        if (environment == null || endpoint == null || endpoint.isBlank())
            throw new IllegalArgumentException("Missing config");
    }
}
