package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/java/core/collection/hashing")
public class HashingController {

    static final class MutableKey {
        private String id;

        MutableKey(String id) {
            this.id = id;
        }

        void setId(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MutableKey key)) {
                return false;
            }
            return Objects.equals(id, key.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }

    @GetMapping("/mutable-key")
    public Map<String, Object> mutableKey() {
        MutableKey key = new MutableKey("ORDER-1");
        Map<MutableKey, String> orders = new HashMap<>();
        orders.put(key, "PAID");

        boolean lookupBeforeMutation = orders.containsKey(key);
        String valueBeforeMutation = orders.get(key);

        key.setId("ORDER-2");

        boolean lookupSameReferenceAfterMutation = orders.containsKey(key);
        boolean lookupEquivalentNewKeyAfterMutation = orders.containsKey(new MutableKey("ORDER-2"));
        String entryKeyStillStored = orders.entrySet().iterator().next().getKey().id();
        String entryValueStillStored = orders.entrySet().iterator().next().getValue();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lookupBeforeMutation", lookupBeforeMutation);
        result.put("valueBeforeMutation", valueBeforeMutation);
        result.put("mutatedKeyId", key.id());
        result.put("lookupSameReferenceAfterMutation", lookupSameReferenceAfterMutation);
        result.put("lookupEquivalentNewKeyAfterMutation", lookupEquivalentNewKeyAfterMutation);
        result.put("entryKeyStillStored", entryKeyStillStored);
        result.put("entryValueStillStored", entryValueStillStored);
        result.put("lesson", "do not mutate fields used by equals/hashCode while an object is a hash key");
        return result;
    }
}
