package com.example.learning.controller;

import com.example.learning.classloader.sample.FailingInitializationProbe;
import com.example.learning.classloader.support.ClassBytes;
import com.example.learning.classloader.support.SingleClassLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/classloader/initialization")
public class InitializationController {

    @GetMapping("/failure-state")
    public Map<String, Object> failureState() throws ClassNotFoundException {
        String binaryName = FailingInitializationProbe.class.getName();
        byte[] bytes = ClassBytes.read(FailingInitializationProbe.class);
        ClassLoader loader = new SingleClassLoader(
                ClassLoader.getPlatformClassLoader(),
                binaryName,
                bytes
        );

        Class<?> loaded = Class.forName(binaryName, false, loader);
        Throwable firstFailure = initialize(binaryName, loader);
        Throwable secondFailure = initialize(binaryName, loader);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loadedWithoutInitialization", loaded.getClassLoader() == loader);
        result.put("firstFailure", describe(firstFailure));
        result.put("firstCause", describe(firstFailure == null ? null : firstFailure.getCause()));
        result.put("secondFailure", describe(secondFailure));
        result.put("firstIsExceptionInInitializerError", firstFailure instanceof ExceptionInInitializerError);
        result.put("secondIsNoClassDefFoundError", secondFailure instanceof NoClassDefFoundError);
        result.put("observation", "the same runtime class identity is not initialized again after initialization fails");
        return result;
    }

    private Throwable initialize(String binaryName, ClassLoader loader) {
        try {
            Class.forName(binaryName, true, loader);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private String describe(Throwable failure) {
        if (failure == null) {
            return null;
        }
        return failure.getClass().getName() + ": " + failure.getMessage();
    }
}
