package com.example.learning.module.runtimeextensibility.experiment;

public final class ProviderConstructionProbe {

    private static final ThreadLocal<Integer> CONSTRUCTION_COUNT =
            ThreadLocal.withInitial(() -> 0);

    private ProviderConstructionProbe() {
    }

    public static void reset() {
        CONSTRUCTION_COUNT.set(0);
    }

    public static void providerConstructed() {
        CONSTRUCTION_COUNT.set(CONSTRUCTION_COUNT.get() + 1);
    }

    public static int count() {
        return CONSTRUCTION_COUNT.get();
    }

    public static void clear() {
        CONSTRUCTION_COUNT.remove();
    }
}
