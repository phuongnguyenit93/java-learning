package com.example.learning.classloader.support;

import java.io.IOException;
import java.io.InputStream;

public final class ClassBytes {

    private ClassBytes() {
    }

    public static byte[] read(Class<?> type) {
        String resourceName = type.getSimpleName() + ".class";

        try (InputStream input = type.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IllegalStateException("Class bytes not found for " + type.getName());
            }
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read class bytes for " + type.getName(), exception);
        }
    }
}
