package com.example.learning.module.coordination.pattern.controller;

import com.example.learning.module.coordination.pattern.service.ConsumerProducerService;
import com.example.learning.module.coordination.pattern.service.ExchangerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("pattern")
@RequiredArgsConstructor
public class PatternController {
    private final ConsumerProducerService consumerProducerService;
    private final ExchangerService exchangerService;

    @GetMapping("exchanger")
    public void exchangerExample(){
        exchangerService.exchangerExample();
    }

    @GetMapping("consumer-producer")
    public void consumerProducerPatternExample(){
        consumerProducerService.consumerProducerPatternExample();
    }
}
