package com.example.learning.casestudy.application.group7.case11;

import com.example.learning.casestudy.domain.group7.case11.RequestConfig;

public final class Service11 {
    private final RequestConfig config;
    public Service11(RequestConfig config){
        this.config = config;

    }
    public int timeout(){
        return config.timeout();

    }
}
