# Spring Boot Web Runtime

This module explains the Spring Boot layer that turns an application into a running web process: how Boot decides the web application type, selects and starts an embedded server, applies server configuration, exposes server-facing HTTP capabilities, consumes TLS configuration, adapts to reverse-proxy deployment, and shuts the server down predictably.

The expected foundation is the Spring Boot fundamentals, externalized configuration, auto-configuration, and application-runtime modules. Spring MVC and Spring WebFlux request-processing mechanics remain with the Spring Framework web/reactive curricula; this module focuses on the Boot-managed runtime around those stacks.

The learning flow starts with the web-runtime boundary and `WebApplicationType`, then follows embedded server selection into Servlet and Reactive server auto-configuration. It next moves through declarative `server.*` configuration and programmatic customization before covering HTTP server capabilities, server-side TLS, proxy/forwarded-header deployment, graceful shutdown, and a final synthesis of Boot web defaults and ownership handoffs.

Use configuration properties first when Boot already exposes the required server behavior. Move to `WebServerFactoryCustomizer` or a custom web-server factory only when the property model is insufficient, and keep implementation-specific settings tied to the selected server.

By the end of the module, the learner should be able to reason from application type and classpath to the server that Boot starts, understand which Boot layer configures that server, choose the appropriate configuration/customization mechanism, and recognize when a question belongs instead to Spring MVC/WebFlux, the servlet container, or generic HTTP/TLS/proxy infrastructure.
