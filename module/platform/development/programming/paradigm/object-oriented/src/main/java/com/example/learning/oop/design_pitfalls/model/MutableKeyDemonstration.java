package com.example.learning.oop.design_pitfalls.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Shows a deliberately unsafe, request-local key next to a stable-ID design. */
public final class MutableKeyDemonstration {
    private MutableKeyDemonstration() {}

    private static final class UnsafeKey {
        private String displayName;
        UnsafeKey(String displayName) { this.displayName = displayName; }
        void rename(String next) { displayName = next; }
        @Override public int hashCode() { return displayName.hashCode(); }
        @Override public boolean equals(Object other) {
            return other instanceof UnsafeKey key && displayName.equals(key.displayName);
        }
    }

    private record StableId(String id) {
        StableId { Objects.requireNonNull(id); }
    }

    private static final class Profile {
        private String displayName;
        Profile(String displayName) { this.displayName = displayName; }
        void rename(String next) { displayName = next; }
        String displayName() { return displayName; }
    }

    public record Observation(String originalName, String changedName,
                              int originalHash, int newHash,
                              boolean unsafeFoundBeforeRename, boolean unsafeFoundAfterRename,
                              boolean lookupByOldNameAfterRename,
                              int unsafeMapEntries,
                              boolean stableFoundBeforeRename, boolean stableFoundAfterRename,
                              String stableProfileNameAfterRename,
                              int stableMapEntries) {}

    public static Observation observe(String originalName, String changedName) {
        validateName(originalName);
        validateName(changedName);
        if (originalName.equals(changedName) || originalName.hashCode() == changedName.hashCode()) {
            throw new IllegalArgumentException("names must be distinct with different hashCodes to illustrate lost lookup");
        }

        UnsafeKey unsafeKey = new UnsafeKey(originalName);
        Map<UnsafeKey, String> unsafe = new HashMap<>();
        unsafe.put(unsafeKey, "PROFILE");
        int originalHash = unsafeKey.hashCode();
        boolean foundBefore = unsafe.containsKey(unsafeKey);
        unsafeKey.rename(changedName);
        int newHash = unsafeKey.hashCode();
        boolean foundAfter = unsafe.containsKey(unsafeKey);
        boolean oldNameFound = unsafe.containsKey(new UnsafeKey(originalName));

        StableId id = new StableId("CUSTOMER-1");
        Profile profile = new Profile(originalName);
        Map<StableId, Profile> safe = new HashMap<>();
        safe.put(id, profile);
        boolean safeBefore = safe.containsKey(new StableId("CUSTOMER-1"));
        profile.rename(changedName);
        boolean safeAfter = safe.containsKey(new StableId("CUSTOMER-1"));

        return new Observation(originalName, changedName, originalHash, newHash,
                foundBefore, foundAfter, oldNameFound, unsafe.size(),
                safeBefore, safeAfter, safe.get(id).displayName(), safe.size());
    }

    private static void validateName(String name) {
        if (name == null || !name.matches("[A-Z][A-Z0-9-]{0,23}")) {
            throw new IllegalArgumentException("name must be 1..24 uppercase ASCII letters, digits or hyphens");
        }
    }
}
