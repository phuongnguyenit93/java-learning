package com.example.learning.module.http.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reactive/http")
public class ReactiveHttpDataController {

    /**
     * README: readme/en/menu/3.HttpDataFlow/ReactiveHttpDataFlow.md#webflux-streaming-vs-aggregation
     * Purpose: Observe elements arriving over time instead of waiting for one aggregated response value.
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> stream(
            @RequestParam(defaultValue = "3") int count,
            @RequestParam(defaultValue = "120") long delayMillis
    ) {
        if (count < 1 || count > 5) {
            return Flux.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "count must be between 1 and 5"
            ));
        }
        if (delayMillis < 50 || delayMillis > 500) {
            return Flux.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "delayMillis must be between 50 and 500"
            ));
        }

        return Flux.defer(() -> {
            long startedAt = System.nanoTime();

            return Flux.range(1, count)
                    .delayElements(Duration.ofMillis(delayMillis))
                    .map(sequence -> {
                        Map<String, Object> evidence = new LinkedHashMap<>();
                        evidence.put("sequence", sequence);
                        evidence.put(
                                "elapsedMillis",
                                Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
                        );
                        evidence.put("last", sequence == count);

                        return ServerSentEvent.<Map<String, Object>>builder()
                                .id(Integer.toString(sequence))
                                .event("webflux-stream")
                                .data(evidence)
                                .build();
                    });
        });
    }

}
