package com.example.learning.aspect;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Aspect
@Component
@Order(1)
public class AspectControllerCut {
    public void handleDefault(JoinPoint joinPoint) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        System.out.println("Method name: " + method.getName());
    }

    @Pointcut("execution(* com.example.learning.controller.AspectController..*(..))")
    public void aspectControllerCut() {};

    @Before("aspectControllerCut()")
    public void beforeAspectAnnotationCut(JoinPoint joinPoint) {
        System.out.println("Order 1");
        handleDefault(joinPoint);
        System.out.println("Before running Controller Aspect");
    }

    @After("aspectControllerCut()")
    public void afterAspectAnnotationCut(JoinPoint joinPoint) {
        System.out.println("Order 1");
        handleDefault(joinPoint);
        System.out.println("After running Controller Aspect");
    }
}
