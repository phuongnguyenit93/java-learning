package com.example.learning.classloader.sample;

public final class LifecycleProbe {

    public static final String PROPERTY_PREFIX = "java.learning.classloader.lifecycle.";

    static {
        String key = PROPERTY_PREFIX + System.identityHashCode(LifecycleProbe.class.getClassLoader());
        System.setProperty(key, "initialized");
    }

    private LifecycleProbe() {
    }
}
