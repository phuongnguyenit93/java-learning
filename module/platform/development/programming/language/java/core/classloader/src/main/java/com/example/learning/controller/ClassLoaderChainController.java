package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/classloader/chain")
public class ClassLoaderChainController {

    @GetMapping("/inspect")
    public Map<String, Object> inspectChain() {
        ClassLoader projectLoader = ClassLoaderChainController.class.getClassLoader();

        List<String> chain = new ArrayList<>();
        for (ClassLoader current = projectLoader; current != null; current = current.getParent()) {
            chain.add(describe(current));
        }
        chain.add("<bootstrap represented by null>");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectClassLoader", describe(projectLoader));
        result.put("systemClassLoader", describe(ClassLoader.getSystemClassLoader()));
        result.put("platformClassLoader", describe(ClassLoader.getPlatformClassLoader()));
        result.put("stringClassLoader", describe(String.class.getClassLoader()));
        result.put("stringUsesBootstrapRepresentation", String.class.getClassLoader() == null);
        result.put("parentChain", chain);
        return result;
    }

    private String describe(ClassLoader loader) {
        if (loader == null) {
            return "<bootstrap>";
        }
        return loader.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(loader));
    }
}
