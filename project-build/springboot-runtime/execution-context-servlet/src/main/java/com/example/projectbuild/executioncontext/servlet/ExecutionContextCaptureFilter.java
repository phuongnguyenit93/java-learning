package com.example.projectbuild.executioncontext.servlet;

import com.example.projectbuild.executioncontext.context.ExecutionContextHolder;
import com.example.projectbuild.executioncontext.model.ExecutionExceptionSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionRequestSnapshot;
import com.example.projectbuild.executioncontext.model.ExecutionResponseSnapshot;
import com.example.projectbuild.executioncontext.service.ExecutionContextService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExecutionContextCaptureFilter extends OncePerRequestFilter {

    public static final String EXECUTION_ID_MDC_KEY = "executionId";

    private final ExecutionContextService executionContextService;
    private final ExecutionContextProperties properties;

    public ExecutionContextCaptureFilter(
            ExecutionContextService executionContextService,
            ExecutionContextProperties properties
    ) {
        this.executionContextService = executionContextService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        ExecutionRequestSnapshot initialRequest = requestSnapshot(requestWrapper, "");
        String executionId = executionContextService.start(initialRequest);

        ExecutionContextHolder.set(executionId);
        MDC.put(EXECUTION_ID_MDC_KEY, executionId);

        if (properties.getResponseHeader() != null && !properties.getResponseHeader().isBlank()) {
            responseWrapper.setHeader(properties.getResponseHeader(), executionId);
        }

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (IOException | ServletException | RuntimeException exception) {
            executionContextService.fail(
                    executionId,
                    new ExecutionExceptionSnapshot(
                            exception.getClass().getName(),
                            exception.getMessage()
                    )
            );
            throw exception;
        } finally {
            try {
                String requestBody = toBody(
                        requestWrapper.getContentAsByteArray(),
                        charset(requestWrapper.getCharacterEncoding())
                );

                executionContextService.updateRequest(
                        executionId,
                        requestSnapshot(requestWrapper, requestBody)
                );

                if (executionContextService.hasHandler(executionId)) {
                    executionContextService.complete(
                            executionId,
                            new ExecutionResponseSnapshot(
                                    responseWrapper.getStatus(),
                                    responseWrapper.getContentType(),
                                    toBody(
                                            responseWrapper.getContentAsByteArray(),
                                            charset(responseWrapper.getCharacterEncoding())
                                    )
                            )
                    );
                } else {
                    executionContextService.discard(executionId);
                }
            } finally {
                responseWrapper.copyBodyToResponse();
                MDC.remove(EXECUTION_ID_MDC_KEY);
                ExecutionContextHolder.clear();
            }
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();

        String path = contextPath != null
                && !contextPath.isBlank()
                && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;

        return properties.getIgnoredPathPrefixes()
                .stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .map(String::trim)
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private ExecutionRequestSnapshot requestSnapshot(
            HttpServletRequest request,
            String body
    ) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) ->
                parameters.put(key, List.of(values))
        );

        return new ExecutionRequestSnapshot(
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getContentType(),
                parameters,
                body
        );
    }

    private String toBody(byte[] bytes, Charset charset) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        String body = new String(bytes, charset);
        int maxLength = Math.max(0, properties.getMaxBodyLength());
        if (maxLength == 0 || body.length() <= maxLength) {
            return body;
        }

        return body.substring(0, maxLength) + "\n...[truncated]";
    }

    private static Charset charset(String name) {
        if (name == null || name.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(name);
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }
}
