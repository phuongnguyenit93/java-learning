package com.example.learning.webruntime.experiment;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WebRuntimeExperimentService {

    private final ServletWebServerApplicationContext applicationContext;
    private final ServerProperties serverProperties;

    public WebRuntimeExperimentService(
            ServletWebServerApplicationContext applicationContext,
            ServerProperties serverProperties
    ) {
        this.applicationContext = applicationContext;
        this.serverProperties = serverProperties;
    }

    public Map<String, Object> embeddedServer() {
        WebServer webServer = applicationContext.getWebServer();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationContextType", applicationContext.getClass().getName());
        result.put("webServerType", webServer.getClass().getName());
        result.put("actualPort", webServer.getPort());
        return result;
    }

    public Map<String, Object> serverProperties() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configuredPort", serverProperties.getPort());
        result.put(
                "configuredAddress",
                serverProperties.getAddress() == null
                        ? null
                        : serverProperties.getAddress().getHostAddress()
        );
        result.put(
                "forwardHeadersStrategy",
                serverProperties.getForwardHeadersStrategy() == null
                        ? null
                        : serverProperties.getForwardHeadersStrategy().name()
        );
        result.put("shutdown", serverProperties.getShutdown().name());
        result.put("actualPort", applicationContext.getWebServer().getPort());
        return result;
    }

    public Map<String, Object> requestMetadata() {
        HttpServletRequest request = currentRequest();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scheme", request.getScheme());
        result.put("secure", request.isSecure());
        result.put("serverName", request.getServerName());
        result.put("serverPort", request.getServerPort());
        result.put("remoteAddress", request.getRemoteAddr());
        result.put("requestUrl", request.getRequestURL().toString());
        result.put("xForwardedFor", request.getHeader("X-Forwarded-For"));
        result.put("xForwardedProto", request.getHeader("X-Forwarded-Proto"));
        result.put("xForwardedHost", request.getHeader("X-Forwarded-Host"));
        result.put("xForwardedPort", request.getHeader("X-Forwarded-Port"));
        return result;
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }

        throw new IllegalStateException("No Servlet request is bound to the current thread");
    }
}
