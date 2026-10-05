package com.example.learning.module.web.async.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AsyncDispatchTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ATTRIBUTE =
            AsyncDispatchTraceFilter.class.getName() + ".TRACE";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().contains("/spring-web/async/");
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        List<String> trace = trace(request);
        trace.add(
                "filter:" + request.getDispatcherType()
                        + ":" + Thread.currentThread().getName()
        );
        filterChain.doFilter(request, response);
    }

    @SuppressWarnings("unchecked")
    public static List<String> trace(HttpServletRequest request) {
        Object existing = request.getAttribute(TRACE_ATTRIBUTE);
        if (existing instanceof List<?> list) {
            return (List<String>) list;
        }

        List<String> created = new CopyOnWriteArrayList<>();
        request.setAttribute(TRACE_ATTRIBUTE, created);
        return created;
    }
}
