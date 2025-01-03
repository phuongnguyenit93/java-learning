package com.example.learning.aspect;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Aspect
@Component
@Order(2)
public class AspectAnnotationCut {
    public void handleDefault(JoinPoint joinPoint) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        System.out.println("Method name: " + method.getName());
        System.out.println(request.getMethod());
    }

    @Pointcut ("@annotation(com.example.learning.annotation.AspectAnnotation)")
    public void aspectAnnotationCut() {};

    @Before("aspectAnnotationCut()")
    public void beforeAspectAnnotationCut(JoinPoint joinPoint) {
        System.out.println("Order 2");
        handleDefault(joinPoint);
        System.out.println("Before running Annotation Aspect");
    }

    @After("aspectAnnotationCut()")
    public void afterAspectAnnotationCut(JoinPoint joinPoint) {
        System.out.println("Order 2");
        handleDefault(joinPoint);
        System.out.println("After running Annotation Aspect");
    }

    @AfterReturning(pointcut = "aspectAnnotationCut()",returning = "result")
    public void afterAspectAnnotationCut(JoinPoint joinPoint,String result) {
        System.out.println("Order 2");
        handleDefault(joinPoint);
        System.out.println("After Returning Result" + result);
    }

    @AfterThrowing(pointcut = "aspectAnnotationCut()",throwing = "throwing")
    public void afterAspectAnnotationCut(JoinPoint joinPoint,Exception throwing) {
        System.out.println("Order 2");
        handleDefault(joinPoint);
        System.out.println("After Throwing error");
        System.out.println(throwing.getMessage());
    }

}
