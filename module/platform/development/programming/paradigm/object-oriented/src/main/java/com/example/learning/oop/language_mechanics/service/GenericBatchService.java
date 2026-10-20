package com.example.learning.oop.language_mechanics.service;

import com.example.learning.oop.language_mechanics.model.NumericTransfers;
import com.example.learning.oop.language_mechanics.model.TypedCache;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import static com.example.learning.oop.language_mechanics.dto.LanguageMechanicsDtos.*;

@Service
public final class GenericBatchService {
    public GenericBatchResult process(GenericBatchRequest request) {
        Objects.requireNonNull(request, "request");
        var integers = requireList(request.integers(), "integers");
        var decimals = requireList(request.decimals(), "decimals");
        var entries = requireList(request.entries(), "entries");
        var lookups = requireList(request.lookups(), "lookups");
        if (integers.size() + decimals.size() > 100 || entries.size() > 50 || lookups.size() > 50) {
            throw new IllegalArgumentException("Too many values");
        }
        List<Number> combined = new ArrayList<>();
        NumericTransfers.copy(integers, combined);
        NumericTransfers.copy(decimals, combined);
        String sum = NumericTransfers.sum(combined).stripTrailingZeros().toPlainString();
        Integer largest = integers.isEmpty() ? null : NumericTransfers.greatest(integers);

        TypedCache<String, Integer> quantities = new TypedCache<>();
        for (CacheEntry entry : entries) {
            Objects.requireNonNull(entry, "entry");
            quantities.add(key(entry.key()), nonnegative(entry.quantity()));
        }
        Map<String, Integer> found = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String lookup : lookups) {
            String key = key(lookup);
            var result = quantities.lookup(key);
            if (result.isPresent()) {
                found.put(key, result.get());
            } else {
                missing.add(key);
            }
        }
        return new GenericBatchResult(combined.stream().map(Object::toString).toList(), sum,
                largest, Map.copyOf(found), List.copyOf(missing));
    }

    private static <T> List<T> requireList(List<T> list, String name) {
        return Objects.requireNonNull(list, name + " must be provided");
    }

    private static String key(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("Cache key must contain 1..40 alphanumeric/underscore/hyphen characters");
        }
        return key;
    }

    private static int nonnegative(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Cache quantity cannot be negative");
        }
        return quantity;
    }
}
