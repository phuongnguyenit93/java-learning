# Spring Boot Fundamentals

Spring Boot Fundamentals introduces the mental model that makes the rest of the Spring Boot curriculum understandable. It explains why Boot exists, how a Java `main` method delegates bootstrap work to `SpringApplication`, what `@SpringBootApplication` contributes, how starters and the classpath affect available behavior, and how to read startup output instead of treating Boot as magic.

## What You Will Learn

You will connect Spring Framework with Boot's conventions, follow a simple application from bootstrap to a running context and orderly stop, understand the role of the primary configuration class, distinguish starters from auto-configuration, recognize non-web/Servlet/reactive application shapes, use DevTools as a development-time aid, and build a high-level packaging mental model.

## Prerequisites

You should already understand basic Java applications, classes, annotations, dependencies, and the Spring Framework container at an introductory level. Deep knowledge of auto-configuration, Config Data, embedded-server internals, Actuator, testing, or build plugins is not required; those are later Spring Boot modules.

## Learning Flow

1. Understand what Spring Boot is, why it exists, and how it relates to Spring Framework.
2. Follow `SpringApplication` from `main` to a running `ApplicationContext` and a high-level start/run/stop lifecycle.
3. Understand the primary configuration class and `@SpringBootApplication`.
4. Learn how starters, managed dependencies, and the classpath relate to Boot behavior.
5. Read startup output as evidence of what Boot actually created and selected.
6. Distinguish non-web, Servlet, and reactive application shapes and build the embedded-server mental model.
7. Understand the role and production boundary of DevTools.
8. Build the mental model for executable Boot packaging without diving into build-plugin internals.
9. Synthesize the model and hand each deeper concern to its owning Spring Boot module.

## Module Boundary

This module intentionally stays at the fundamentals level. Config Data/property precedence/binding belong to `externalized-configuration`; conditional decisions and custom auto-configuration belong to `auto-configuration`; detailed runtime lifecycle, logging, task execution and Docker Compose integration belong to `application-runtime`; server configuration/TLS/proxy/graceful shutdown belong to `web-runtime`; build plugins, Boot Loader details, layers and images belong to `build-tooling-packaging`; production endpoints, testing strategy and AOT/native execution belong to their dedicated modules.
