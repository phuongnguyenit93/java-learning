package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/reflection/proxy")
public class DynamicProxyController {

    record PaymentRequest(String orderId, long amount) {
    }

    public interface PaymentGateway {
        String pay(PaymentRequest request);
    }

    static final class PaymentGatewayImpl implements PaymentGateway {
        @Override
        public String pay(PaymentRequest request) {
            return "paid:" + request.orderId();
        }
    }

    static final class TracingHandler implements InvocationHandler {
        private final PaymentGateway target;
        private final List<String> trace;

        TracingHandler(PaymentGateway target, List<String> trace) {
            this.target = target;
            this.trace = trace;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "PaymentGatewayProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                };
            }

            trace.add("before:" + method.getName());
            try {
                Object result = method.invoke(target, args);
                trace.add("after:" + method.getName());
                return result;
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }

    @GetMapping("/intercept")
    public Map<String, Object> intercept() {
        PaymentGateway target = new PaymentGatewayImpl();
        List<String> trace = new ArrayList<>();
        InvocationHandler handler = new TracingHandler(target, trace);

        PaymentGateway proxy = (PaymentGateway) Proxy.newProxyInstance(
                PaymentGateway.class.getClassLoader(),
                new Class<?>[]{PaymentGateway.class},
                handler
        );

        String value = proxy.pay(new PaymentRequest("ORD-42", 150_000L));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result", value);
        result.put("trace", trace);
        result.put("proxyClass", proxy.getClass().getName());
        result.put("isJdkProxyClass", Proxy.isProxyClass(proxy.getClass()));
        result.put("implementsPaymentGateway", proxy instanceof PaymentGateway);
        result.put("handlerType", Proxy.getInvocationHandler(proxy).getClass().getSimpleName());
        return result;
    }
}
