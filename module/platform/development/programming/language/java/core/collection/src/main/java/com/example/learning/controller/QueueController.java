package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/java/core/collection/queue")
public class QueueController {

    @GetMapping("/queue-operations")
    public Map<String, Object> queueOperations() {
        Deque<String> queue = new ArrayDeque<>();

        String pollOnEmpty = queue.poll();
        String peekOnEmpty = queue.peek();

        String removeOnEmptyException;
        try {
            queue.remove();
            removeOnEmptyException = "none";
        } catch (NoSuchElementException exception) {
            removeOnEmptyException = exception.getClass().getSimpleName();
        }

        String elementOnEmptyException;
        try {
            queue.element();
            elementOnEmptyException = "none";
        } catch (NoSuchElementException exception) {
            elementOnEmptyException = exception.getClass().getSimpleName();
        }

        boolean firstOfferAccepted = queue.offer("A");
        boolean secondOfferAccepted = queue.offer("B");
        String firstPolled = queue.poll();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pollOnEmpty", pollOnEmpty);
        result.put("peekOnEmpty", peekOnEmpty);
        result.put("removeOnEmptyException", removeOnEmptyException);
        result.put("elementOnEmptyException", elementOnEmptyException);
        result.put("firstOfferAccepted", firstOfferAccepted);
        result.put("secondOfferAccepted", secondOfferAccepted);
        result.put("firstPolled", firstPolled);
        result.put("remainingHead", queue.peek());
        return result;
    }
}
