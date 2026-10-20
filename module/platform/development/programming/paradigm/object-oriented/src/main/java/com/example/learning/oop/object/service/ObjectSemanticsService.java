package com.example.learning.oop.object.service;

import com.example.learning.oop.object.dto.ObjectExperimentDto;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Deterministic examples; no objects created here are kept between HTTP requests. */
@Service
public class ObjectSemanticsService {

    public ObjectExperimentDto.ReferenceResult references(ObjectExperimentDto.ReferenceRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String name = requiredText("name", input.name());
        int initial = boundedInt("initialValue", input.initialValue(), -100_000, 100_000);
        int increment = boundedInt("increment", input.increment(), 1, 1_000);

        MutableCounter original = new MutableCounter(name, initial);
        MutableCounter alias = original; // Same instance; reference value copied.
        MutableCounter separatelyCreated = new MutableCounter(name, initial);
        boolean initiallyEqualState = original.name.equals(separatelyCreated.name)
                && original.value == separatelyCreated.value;

        addThroughParameter(alias, increment); // Mutates the shared object.
        replaceParameterLocally(alias); // Does not reassign the caller's variable.

        return new ObjectExperimentDto.ReferenceResult(
                original == alias,
                original != separatelyCreated,
                initiallyEqualState,
                original == alias,
                initial,
                original.value,
                alias.value,
                separatelyCreated.value,
                original.name);
    }

    public ObjectExperimentDto.EqualityResult equality(ObjectExperimentDto.EqualityRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String firstCode = requiredText("firstCode", input.firstCode());
        String secondCode = requiredText("secondCode", input.secondCode());

        ProductCode first = new ProductCode(firstCode);
        ProductCode second = new ProductCode(secondCode);
        ProductCode alias = first;
        Set<ProductCode> uniqueCodes = new HashSet<>();
        uniqueCodes.add(first);
        uniqueCodes.add(second);

        LegacyIdentityCode legacyFirst = new LegacyIdentityCode(firstCode);
        LegacyIdentityCode legacySecond = new LegacyIdentityCode(firstCode);
        return new ObjectExperimentDto.EqualityResult(
                first == second,
                first.equals(second),
                first.hashCode() == second.hashCode(),
                uniqueCodes.size(),
                legacyFirst.equals(legacySecond),
                alias == first);
    }

    public ObjectExperimentDto.CopyResult copies(ObjectExperimentDto.CopyRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String originalLabel = requiredText("originalLabel", input.originalLabel());
        String updatedLabel = requiredText("updatedLabel", input.updatedLabel());
        String addedLabel = requiredText("addedLabel", input.addedLabel());

        List<MutableItem> original = new ArrayList<>();
        original.add(new MutableItem(originalLabel));
        List<MutableItem> shallow = new ArrayList<>(original);
        List<MutableItem> deep = original.stream().map(item -> new MutableItem(item.label)).toList();
        List<MutableItem> unmodifiableSnapshot = List.copyOf(original);
        ItemValue immutableValue = new ItemValue(original.get(0).label);

        original.get(0).label = updatedLabel;
        original.add(new MutableItem(addedLabel));

        return new ObjectExperimentDto.CopyResult(
                original != shallow,
                original.get(0) == shallow.get(0),
                original.get(0) != deep.get(0),
                labels(original),
                labels(shallow),
                labels(deep),
                labels(unmodifiableSnapshot),
                immutableValue.label());
    }

    public ObjectExperimentDto.AbsenceResult absence(ObjectExperimentDto.AbsenceRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String lookupId = requiredText("lookupId", input.lookupId());
        if (input.optionalAddress() != null && input.optionalAddress().length() > 80) {
            throw new IllegalArgumentException("optionalAddress must have at most 80 characters");
        }
        String address = input.optionalAddress() == null ? null : input.optionalAddress().trim();
        Customer sample = new Customer("C01", "Lan", address == null || address.isEmpty() ? null : address);
        Map<String, Customer> repository = Map.of(sample.id(), sample);
        Optional<Customer> found = Optional.ofNullable(repository.get(lookupId));
        Optional<String> shippingAddress = found.flatMap(Customer::shippingAddress);
        List<String> addresses = shippingAddress.map(List::of).orElseGet(List::of);
        return new ObjectExperimentDto.AbsenceResult(
                lookupId,
                found.isPresent(),
                shippingAddress.isPresent(),
                found.map(Customer::name).orElse(null),
                addresses,
                addresses.isEmpty());
    }

    static String requiredText(String field, String value) {
        if (value == null || value.isBlank() || value.length() > 80) {
            throw new IllegalArgumentException(field + " must contain 1 to 80 nonblank characters");
        }
        return value.trim();
    }

    static int boundedInt(String field, Integer value, int min, int max) {
        if (value == null || value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static void addThroughParameter(MutableCounter parameter, int amount) {
        parameter.value += amount;
    }

    private static void replaceParameterLocally(MutableCounter parameter) {
        parameter = new MutableCounter(parameter.name, -1);
        // Reassignment is limited to this method's own parameter variable.
    }

    private static List<String> labels(List<MutableItem> items) {
        return items.stream().map(item -> item.label).toList();
    }

    private static final class MutableCounter {
        private final String name;
        private int value;

        private MutableCounter(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    // A record supplies field-based equals/hashCode, unlike Object's identity equality.
    private record ProductCode(String value) {
    }

    private static final class LegacyIdentityCode {
        @SuppressWarnings("unused")
        private final String code;

        private LegacyIdentityCode(String code) {
            this.code = code;
        }
    }

    private static final class MutableItem {
        private String label;

        private MutableItem(String label) {
            this.label = label;
        }
    }

    private record ItemValue(String label) {
    }

    private record Customer(String id, String name, String address) {
        private Optional<String> shippingAddress() {
            return Optional.ofNullable(address);
        }
    }
}
