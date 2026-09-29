package com.example.learning.controller;

import com.example.learning.classloader.sample.IdentityProbe;
import com.example.learning.classloader.support.ClassBytes;
import com.example.learning.classloader.support.SingleClassLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/classloader/identity")
public class ClassIdentityController {

    @GetMapping("/cross-loader-cast")
    public Map<String, Object> crossLoaderCast()
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {

        String binaryName = IdentityProbe.class.getName();
        byte[] bytes = ClassBytes.read(IdentityProbe.class);

        ClassLoader loaderA = new SingleClassLoader(
                ClassLoader.getPlatformClassLoader(), binaryName, bytes
        );
        ClassLoader loaderB = new SingleClassLoader(
                ClassLoader.getPlatformClassLoader(), binaryName, bytes
        );

        Class<?> typeA = loaderA.loadClass(binaryName);
        Class<?> typeB = loaderB.loadClass(binaryName);
        Object instanceA = typeA.getDeclaredConstructor().newInstance();

        String failureType = null;
        String failureMessage = null;
        boolean castSucceeded;

        try {
            typeB.cast(instanceA);
            castSucceeded = true;
        } catch (ClassCastException exception) {
            castSucceeded = false;
            failureType = exception.getClass().getName();
            failureMessage = exception.getMessage();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sameBinaryName", typeA.getName().equals(typeB.getName()));
        result.put("sameRuntimeType", typeA == typeB);
        result.put("typeBSeesInstanceA", typeB.isInstance(instanceA));
        result.put("castSucceeded", castSucceeded);
        result.put("failureType", failureType);
        result.put("failureMessage", failureMessage);
        result.put("loaderA", describe(typeA.getClassLoader()));
        result.put("loaderB", describe(typeB.getClassLoader()));
        return result;
    }

    private String describe(ClassLoader loader) {
        return loader.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(loader));
    }
}
