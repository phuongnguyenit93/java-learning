package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/classloader/context")
public class ContextClassLoaderController {

    @GetMapping("/temporary-tccl")
    public Map<String, Object> temporaryTccl() {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        ClassLoader temporary = new NamedContextClassLoader(previous, "classloader-learning-context");

        String during;
        boolean installed;

        try {
            thread.setContextClassLoader(temporary);
            ClassLoader current = thread.getContextClassLoader();
            during = describe(current);
            installed = current == temporary;
        } finally {
            thread.setContextClassLoader(previous);
        }

        ClassLoader after = thread.getContextClassLoader();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("before", describe(previous));
        result.put("during", during);
        result.put("after", describe(after));
        result.put("temporaryLoaderInstalled", installed);
        result.put("restored", after == previous);
        return result;
    }

    private String describe(ClassLoader loader) {
        if (loader == null) {
            return "<bootstrap>";
        }
        return loader.toString();
    }

    private static final class NamedContextClassLoader extends ClassLoader {

        private final String name;

        private NamedContextClassLoader(ClassLoader parent, String name) {
            super(parent);
            this.name = name;
        }

        @Override
        public String toString() {
            return name + "@" + Integer.toHexString(System.identityHashCode(this));
        }
    }
}
