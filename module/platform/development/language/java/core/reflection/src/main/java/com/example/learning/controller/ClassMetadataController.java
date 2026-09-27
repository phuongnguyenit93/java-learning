package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/reflection/class-metadata")
public class ClassMetadataController {

    public interface PaymentProcessor {
    }

    public static final class PaymentService implements PaymentProcessor {
        public String pay() {
            return "paid";
        }
    }

    public record PaymentRequest(String orderId, long amount) {
    }

    @GetMapping("/inspect-type")
    public Map<String, Object> inspectType() {
        Class<?> type = PaymentService.class;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", type.getName());
        result.put("simpleName", type.getSimpleName());
        result.put("interface", type.isInterface());
        result.put("record", type.isRecord());
        result.put("array", type.isArray());
        result.put("public", Modifier.isPublic(type.getModifiers()));
        result.put("final", Modifier.isFinal(type.getModifiers()));
        result.put("accessFlags", type.accessFlags().stream().map(AccessFlag::name).sorted().toList());
        result.put("superclass", type.getSuperclass().getName());
        result.put("interfaces", Arrays.stream(type.getInterfaces()).map(Class::getName).sorted().toList());
        result.put("processorAssignableFromService", PaymentProcessor.class.isAssignableFrom(type));
        result.put("requestIsRecord", PaymentRequest.class.isRecord());
        result.put("declaredMethodNames", Arrays.stream(type.getDeclaredMethods()).map(method -> method.getName()).sorted().toList());
        return result;
    }
}
