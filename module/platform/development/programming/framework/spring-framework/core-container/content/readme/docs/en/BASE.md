# Spring Core Container

The Spring Core Container is the foundation that manages an application's object graph in Spring Framework. This module focuses on how the container consumes configuration metadata, registers bean definitions, resolves dependencies, creates beans, manages scope and lifecycle, and exposes platform services through `ApplicationContext`.

## What You Will Learn

The journey starts with why IoC and Dependency Injection exist, then builds a mental model around `BeanFactory` and `ApplicationContext`. It proceeds through bean registration, dependency resolution, scopes, and lifecycle before expanding into `Environment`, `PropertySource`, profiles, SpEL, `Resource`, application events, internationalization, context hierarchies, and container extension points such as `BeanFactoryPostProcessor`, `BeanPostProcessor`, and `FactoryBean`.

The final chapters connect those mechanisms into an end-to-end model for deliberately designing object graphs and diagnosing startup or dependency failures instead of memorizing annotations.

## Prerequisites

You should already understand Java Core concepts such as classes and objects, interfaces, annotations, basic reflection, exceptions, generics, and collections. Spring Boot knowledge is not required; this module intentionally teaches Spring Framework container mechanics before Boot conveniences.

## Learning Flow

1. Understand why the Spring IoC Container and Dependency Injection exist.
2. Build the `BeanDefinition`, `BeanFactory`, and `ApplicationContext` mental model.
3. Learn how beans are registered and configured.
4. Understand dependency injection, candidate selection, and resolution failures.
5. Learn scopes, lazy creation, and scoped dependencies.
6. Follow bean lifecycle, startup/shutdown ordering, and callbacks.
7. Understand `Environment`, properties, profiles, and SpEL.
8. Explore `ApplicationContext` platform services: resources, events, internationalization, and hierarchy.
9. Learn the major container extension points.
10. Synthesize the model into end-to-end design and diagnosis.

## Module Boundary

This module does not own Spring Boot Config Data or auto-configuration, deep Spring AOP, transaction/cache semantics, MVC/WebFlux request processing, validation/data binding, or Spring Messaging/STOMP. Those concerns are handed off to their dedicated Spring Framework or Spring Boot modules.
