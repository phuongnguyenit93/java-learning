package com.example.learning.controller;

import com.example.learning.sample.field.PaymentFieldSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/reflection/fields")
public class FieldReflectionController {

    @GetMapping("/inspect-and-mutate")
    public Map<String, Object> inspectAndMutate() throws ReflectiveOperationException {
        PaymentFieldSample state = new PaymentFieldSample();
        Field field = PaymentFieldSample.class.getField("processedCount");

        int before = field.getInt(state);
        field.setInt(state, 5);
        int after = field.getInt(state);

        Field privateField = PaymentFieldSample.class.getDeclaredField("provider");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fieldName", field.getName());
        result.put("fieldType", field.getType().getName());
        result.put("before", before);
        result.put("after", after);
        result.put("privateFieldDiscovered", privateField.getName());
        result.put("privateFieldAccessibleWithoutOverride", privateField.canAccess(state));
        return result;
    }
}
