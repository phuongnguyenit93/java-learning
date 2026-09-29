package com.example.learning.controller;

import com.example.learning.sample.access.PaymentAccessSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/reflection/access")
public class AccessReflectionController {

    @GetMapping("/checks")
    public Map<String, Object> accessChecks() throws ReflectiveOperationException {
        PaymentAccessSample service = new PaymentAccessSample("stripe");
        service.pay();

        Method method = PaymentAccessSample.class.getDeclaredMethod("internalStatus");
        boolean before = method.canAccess(service);
        boolean opened = method.trySetAccessible();
        boolean after = method.canAccess(service);
        Object value = opened ? method.invoke(service) : "not-accessible";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", method.getName());
        result.put("canAccessBefore", before);
        result.put("trySetAccessible", opened);
        result.put("canAccessAfter", after);
        result.put("invocationResult", value);
        return result;
    }
}
