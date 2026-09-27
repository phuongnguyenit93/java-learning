package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/classloader/resource")
public class ResourceLoadingController {

    @GetMapping("/resolve")
    public Map<String, Object> resolve() {
        URL classRelative = ResourceLoadingController.class.getResource("classloader-demo.properties");
        URL classAbsolute = ResourceLoadingController.class.getResource(
                "/com/example/learning/controller/classloader-demo.properties"
        );

        ClassLoader loader = ResourceLoadingController.class.getClassLoader();
        URL loaderRoot = loader.getResource("com/example/learning/controller/classloader-demo.properties");
        URL wrongLeadingSlash = loader.getResource("/com/example/learning/controller/classloader-demo.properties");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classRelativeFound", classRelative != null);
        result.put("classAbsoluteFound", classAbsolute != null);
        result.put("classLoaderRootFound", loaderRoot != null);
        result.put("classLoaderLeadingSlashFound", wrongLeadingSlash != null);
        result.put("classRelativeUrl", externalForm(classRelative));
        result.put("classAbsoluteUrl", externalForm(classAbsolute));
        result.put("classLoaderRootUrl", externalForm(loaderRoot));
        result.put("sameResource", classRelative != null && classRelative.equals(loaderRoot));
        return result;
    }

    private String externalForm(URL url) {
        return url == null ? null : url.toExternalForm();
    }
}
