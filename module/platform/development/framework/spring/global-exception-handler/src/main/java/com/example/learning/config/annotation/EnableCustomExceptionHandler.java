package com.example.learning.config.annotation;

import com.example.learning.config.component.ExceptionHandlerComponent;
import com.example.learning.module.global.handler.GlobalExceptionHandler;
import com.example.learning.module.mongo.handler.MongoExceptionHandler;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ExceptionHandlerComponent.class)
public @interface EnableCustomExceptionHandler {
}
