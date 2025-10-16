package com.example.learning.controller;

import com.example.learning.annotation.AnnotationExample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

@RestController
@RequestMapping
public class AnnotationController {
    @GetMapping("/example")
    @AnnotationExample
    public void annotationExample() throws NoSuchMethodException {
        Class clazz = AnnotationController.class;
        Method method = clazz.getMethod("annotationExample");

        AnnotationExample annotation = method.getAnnotation(AnnotationExample.class);
        System.out.println(annotation.range());
        System.out.println(annotation.stringParam());
        System.out.println(annotation.isChecked());
        System.out.println(annotation.annoEnum());
    }
}
