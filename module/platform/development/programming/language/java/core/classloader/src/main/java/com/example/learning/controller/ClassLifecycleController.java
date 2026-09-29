package com.example.learning.controller;

import com.example.learning.classloader.sample.LifecycleProbe;
import com.example.learning.classloader.support.ClassBytes;
import com.example.learning.classloader.support.SingleClassLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/classloader/lifecycle")
public class ClassLifecycleController {

    private static final String PROPERTY_PREFIX = "java.learning.classloader.lifecycle.";

    @GetMapping("/load-without-init")
    public Map<String, Object> loadWithoutInit() throws ClassNotFoundException {
        String binaryName = LifecycleProbe.class.getName();
        byte[] bytes = ClassBytes.read(LifecycleProbe.class);
        ClassLoader loader = new SingleClassLoader(
                ClassLoader.getPlatformClassLoader(),
                binaryName,
                bytes
        );

        String observationKey = PROPERTY_PREFIX + System.identityHashCode(loader);
        System.clearProperty(observationKey);

        try {
            Class<?> loaded = Class.forName(binaryName, false, loader);
            boolean initializedAfterLoad = System.getProperty(observationKey) != null;

            Class<?> initialized = Class.forName(binaryName, true, loader);
            boolean initializedAfterExplicitInit = System.getProperty(observationKey) != null;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("binaryName", binaryName);
            result.put("sameClassObject", loaded == initialized);
            result.put("definingLoaderIsExperimentLoader", loaded.getClassLoader() == loader);
            result.put("initializedAfterClassForNameFalse", initializedAfterLoad);
            result.put("initializedAfterClassForNameTrue", initializedAfterExplicitInit);
            result.put("observation", "loading creates/returns the runtime Class without requiring static initialization");
            return result;
        } finally {
            System.clearProperty(observationKey);
        }
    }
}
