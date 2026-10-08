# 📂 README MODULE STRUCTURE (EN)

* **1.RuntimeModel**
    * [RuntimeModel](readme/en/menu/1.RuntimeModel/RuntimeModel.md)
* **2.ApplicationLifecycleEvents**
    * [ApplicationLifecycleEvents](readme/en/menu/2.ApplicationLifecycleEvents/ApplicationLifecycleEvents.md)
* **3.ArgumentsAndRunners**
    * [ArgumentsAndRunners](readme/en/menu/3.ArgumentsAndRunners/ArgumentsAndRunners.md)
* **4.AvailabilityFailureExit**
    * [AvailabilityFailureExit](readme/en/menu/4.AvailabilityFailureExit/AvailabilityFailureExit.md)
* **5.TaskExecutionScheduling**
    * [TaskExecutionScheduling](readme/en/menu/5.TaskExecutionScheduling/TaskExecutionScheduling.md)
* **6.VirtualThreads**
    * [VirtualThreads](readme/en/menu/6.VirtualThreads/VirtualThreads.md)
* **7.StartupOptimization**
    * [StartupOptimization](readme/en/menu/7.StartupOptimization/StartupOptimization.md)
* **8.RuntimeCustomization**
    * [RuntimeCustomization](readme/en/menu/8.RuntimeCustomization/RuntimeCustomization.md)
* **9.Logging**
    * [Logging](readme/en/menu/9.Logging/Logging.md)
* **10.SslBundles**
    * [SslBundles](readme/en/menu/10.SslBundles/SslBundles.md)
* **11.DockerCompose**
    * [DockerCompose](readme/en/menu/11.DockerCompose/DockerCompose.md)
* **12.RuntimeSynthesis**
    * [RuntimeSynthesis](readme/en/menu/12.RuntimeSynthesis/RuntimeSynthesis.md)

# Spring Boot Application Runtime

This module follows a Spring Boot application after `main()` delegates to `SpringApplication`. It builds a detailed runtime mental model around lifecycle events, startup runners, availability and exit state, Boot-managed task execution and scheduling, virtual-thread integration, startup tuning, logging bootstrap, reusable SSL bundles, development-time Docker Compose integration, and supported runtime customization points.

## What You Will Learn

You will learn to locate behavior on the SpringApplication timeline, choose an extension point based on lifecycle timing, distinguish liveness from readiness, understand Boot's executor/scheduler defaults and virtual-thread switch, tune startup without hiding failures, reason about Boot's early logging integration, reuse named SSL bundles, understand how Boot coordinates local Compose services, and diagnose which runtime concern belongs to Boot versus a neighboring framework or infrastructure layer.

## Prerequisites

You should already understand Spring Boot fundamentals, externalized configuration, and auto-configuration. This module assumes basic Spring container and Java concurrency vocabulary but does not re-teach Spring Framework lifecycle internals, `@Async`/`@Scheduled` semantics, Java virtual-thread mechanics, logging frameworks, TLS/PKI, or Docker/Compose fundamentals.

## Learning Flow

1. Establish the end-to-end SpringApplication runtime model and its ownership boundaries.
2. Follow lifecycle events, application arguments, and ordered startup runners.
3. Connect startup progress to availability, failure diagnostics, shutdown, and process exit.
4. Understand Boot-managed task execution/scheduling and the effect of virtual-thread enablement.
5. Tune startup and select runtime customization points using measured evidence.
6. Study Boot's early logging integration and reusable SSL bundle abstraction.
7. Understand development-time Docker Compose lifecycle and service-connection integration.
8. Synthesize runtime state, hooks, managed services, diagnostics, and module handoffs into one troubleshooting model.

## Module Boundary

This module owns detailed SpringApplication runtime behavior and Boot-managed runtime integrations. Web-server selection/configuration/TLS/graceful shutdown belong to `web-runtime`; production endpoint exposure belongs to `actuator`; configuration-source precedence and binding belong to `externalized-configuration`; auto-configuration authoring/diagnostics belong to `auto-configuration`; generic Spring lifecycle/concurrency semantics, Java concurrency and virtual-thread semantics, logging operations, TLS/PKI, and Docker mechanics remain with their dedicated curricula.
