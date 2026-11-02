package com.example.learning.casestudy.domain.group7.case11;

public record RequestConfig(int timeout){
    public RequestConfig {
        if (timeout<=0)throw new IllegalArgumentException("timeout");

    }
}
