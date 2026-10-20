package com.example.learning.oop.inheritance.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Independent interface defaults require explicit resolution, while a concrete
 * superclass method wins over an interface default with the same signature.
 */
public final class CapabilityResolution {
    private CapabilityResolution() {
    }

    public static Result resolve(String itemId, String tagsCsv) {
        if (itemId == null || itemId.isBlank() || itemId.length() > 80) {
            throw new IllegalArgumentException("itemId must contain 1..80 characters");
        }
        if (tagsCsv == null || tagsCsv.isBlank() || tagsCsv.length() > 200) {
            throw new IllegalArgumentException("tagsCsv must contain 1..200 characters");
        }
        var mixed = new MixedCapability(itemId.trim());
        var classWins = new ConcreteSuperclassWins();
        return new Result(mixed.label(), classWins.label(), mixed.parseTags(tagsCsv));
    }

    public record Result(String explicitlyResolved, String classWins, List<String> tags) {
        public Result {
            tags = List.copyOf(tags);
        }
    }

    private interface Printable {
        default String label() {
            return "PRINT";
        }
    }

    private interface Searchable {
        default String label() {
            return "SEARCH";
        }
    }

    private interface Taggable {
        default List<String> parseTags(String csv) {
            List<String> tags = new ArrayList<>();
            for (String raw : csv.split(",", -1)) {
                String tag = raw.trim().toLowerCase(Locale.ROOT);
                if (tag.isEmpty()) {
                    throw new IllegalArgumentException("Tags must not be empty");
                }
                tags.add(tag);
            }
            return List.copyOf(tags);
        }
    }

    private static final class MixedCapability implements Printable, Searchable, Taggable {
        private final String itemId;

        MixedCapability(String itemId) {
            this.itemId = itemId;
        }

        @Override
        public String label() {
            return Printable.super.label() + "+" + Searchable.super.label() + ":" + itemId;
        }
    }

    private static class ConcreteBase {
        public String label() {
            return "CLASS_WINS";
        }
    }

    private static final class ConcreteSuperclassWins extends ConcreteBase implements Printable {
        // Inherited concrete class implementation beats Printable.default label().
    }
}
