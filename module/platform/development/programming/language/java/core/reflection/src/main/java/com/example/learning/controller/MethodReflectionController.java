package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/reflection/methods")
public class MethodReflectionController {

    record PaymentRequest(String orderId, long amount) {
    }

    static final class PaymentService {
        public String pay(PaymentRequest request) {
            return "paid:" + request.orderId();
        }

        public String reject() {
            throw new IllegalStateException("payment rejected");
        }
    }

    @GetMapping("/invoke")
    public Map<String, Object> invoke() throws ReflectiveOperationException {
        PaymentService service = new PaymentService();
        Method pay = PaymentService.class.getMethod("pay", PaymentRequest.class);
        Object success = pay.invoke(service, new PaymentRequest("ORD-42", 150_000L));

        Method reject = PaymentService.class.getMethod("reject");
        String wrapperType = "";
        String targetCauseType = "";
        String targetMessage = "";
        try {
            reject.invoke(service);
        } catch (InvocationTargetException exception) {
            wrapperType = exception.getClass().getSimpleName();
            targetCauseType = exception.getCause().getClass().getSimpleName();
            targetMessage = exception.getCause().getMessage();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", pay.getName());
        result.put("declaringClass", pay.getDeclaringClass().getSimpleName());
        result.put("returnType", pay.getReturnType().getSimpleName());
        result.put("parameterType", pay.getParameterTypes()[0].getSimpleName());
        result.put("successResult", success);
        result.put("failureWrapper", wrapperType);
        result.put("failureCause", targetCauseType);
        result.put("failureMessage", targetMessage);
        return result;
    }
}
