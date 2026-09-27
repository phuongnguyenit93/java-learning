package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/collection/immutability")
public class ImmutabilityController {

    @GetMapping("/view-vs-copy")
    public Map<String, Object> viewVsCopy() {
        List<String> source = new ArrayList<>(List.of("A", "B"));
        List<String> unmodifiableView = Collections.unmodifiableList(source);
        List<String> snapshot = List.copyOf(source);

        List<String> sourceBeforeMutation = List.copyOf(source);
        List<String> viewBeforeMutation = List.copyOf(unmodifiableView);
        List<String> snapshotBeforeMutation = List.copyOf(snapshot);

        source.add("C");

        String viewMutationException;
        try {
            unmodifiableView.add("D");
            viewMutationException = "none";
        } catch (UnsupportedOperationException exception) {
            viewMutationException = exception.getClass().getSimpleName();
        }

        String snapshotMutationException;
        try {
            snapshot.add("D");
            snapshotMutationException = "none";
        } catch (UnsupportedOperationException exception) {
            snapshotMutationException = exception.getClass().getSimpleName();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceBeforeMutation", sourceBeforeMutation);
        result.put("viewBeforeMutation", viewBeforeMutation);
        result.put("snapshotBeforeMutation", snapshotBeforeMutation);
        result.put("sourceAfterMutation", List.copyOf(source));
        result.put("viewAfterSourceMutation", List.copyOf(unmodifiableView));
        result.put("snapshotAfterSourceMutation", List.copyOf(snapshot));
        result.put("viewMutationException", viewMutationException);
        result.put("snapshotMutationException", snapshotMutationException);
        return result;
    }
}
