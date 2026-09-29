package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/reflection/constructors")
public class ConstructorReflectionController {

    static final class PaymentService {
        private final String provider;

        public PaymentService(String provider) {
            this.provider = provider;
        }

        String provider() {
            return provider;
        }
    }

    @GetMapping("/construct")
    public Map<String, Object> construct() throws ReflectiveOperationException {
        Constructor<PaymentService> constructor = PaymentService.class.getConstructor(String.class);
        PaymentService service = constructor.newInstance("stripe");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("declaringClass", constructor.getDeclaringClass().getSimpleName());
        result.put("parameterTypes", Arrays.stream(constructor.getParameterTypes()).map(Class::getSimpleName).toList());
        result.put("parameterCount", constructor.getParameterCount());
        result.put("createdType", service.getClass().getSimpleName());
        result.put("provider", service.provider());
        return result;
    }
}
