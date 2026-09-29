package com.example.learning.controller;

import com.example.learning.classloader.sample.IdentityProbe;
import com.example.learning.classloader.support.ClassBytes;
import com.example.learning.classloader.support.SingleClassLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/classloader/custom")
public class CustomClassLoaderController {

    @GetMapping("/same-name-identity")
    public Map<String, Object> sameNameIdentity() throws ClassNotFoundException {
        String binaryName = IdentityProbe.class.getName();
        byte[] bytes = ClassBytes.read(IdentityProbe.class);

        ClassLoader loaderA = isolatedLoader(binaryName, bytes);
        ClassLoader loaderB = isolatedLoader(binaryName, bytes);
        Class<?> typeA = loaderA.loadClass(binaryName);
        Class<?> typeB = loaderB.loadClass(binaryName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("binaryNameA", typeA.getName());
        result.put("binaryNameB", typeB.getName());
        result.put("sameBinaryName", typeA.getName().equals(typeB.getName()));
        result.put("sameClassObject", typeA == typeB);
        result.put("sameDefiningLoader", typeA.getClassLoader() == typeB.getClassLoader());
        result.put("typeAAssignableFromTypeB", typeA.isAssignableFrom(typeB));
        result.put("loaderA", describe(loaderA));
        result.put("loaderB", describe(loaderB));
        return result;
    }

    private ClassLoader isolatedLoader(String binaryName, byte[] bytes) {
        return new SingleClassLoader(ClassLoader.getPlatformClassLoader(), binaryName, bytes);
    }

    private String describe(ClassLoader loader) {
        return loader.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(loader));
    }
}
