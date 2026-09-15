package com.example.projectbuild.executioncontext.servlet;

import com.example.projectbuild.executioncontext.context.ExecutionContextHolder;
import com.example.projectbuild.executioncontext.model.ExecutionHandlerSnapshot;
import com.example.projectbuild.executioncontext.service.ExecutionContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.stream.Collectors;

public class ExecutionContextHandlerInterceptor implements HandlerInterceptor {

    private final ExecutionContextService executionContextService;

    public ExecutionContextHandlerInterceptor(ExecutionContextService executionContextService) {
        this.executionContextService = executionContextService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String executionId = ExecutionContextHolder.currentExecutionId();
        if (executionId == null) {
            return true;
        }

        Method method = handlerMethod.getMethod();
        Class<?>[] parameterTypes = method.getParameterTypes();
        String signature = method.getName() + "(" + java.util.stream.IntStream
                .range(0, parameterTypes.length)
                .mapToObj(index -> {
                    Class<?> parameterType = parameterTypes[index];
                    if (method.isVarArgs() && index == parameterTypes.length - 1) {
                        return parameterType.getComponentType().getSimpleName() + "...";
                    }
                    return parameterType.getSimpleName();
                })
                .collect(Collectors.joining(",")) + ")";

        executionContextService.bindHandler(
                executionId,
                new ExecutionHandlerSnapshot(
                        handlerMethod.getBeanType().getName(),
                        method.getName(),
                        signature
                )
        );

        return true;
    }
}
