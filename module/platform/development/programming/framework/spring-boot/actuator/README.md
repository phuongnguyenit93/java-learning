# 📂 README MODULE STRUCTURE (EN)

* **1.ActuatorPurpose**
    * [ActuatorPurpose](readme/en/menu/1.ActuatorPurpose/ActuatorPurpose.md)
* **2.EndpointModel**
    * [EndpointModel](readme/en/menu/2.EndpointModel/EndpointModel.md)
* **3.EndpointAvailabilityExposure**
    * [EndpointAvailabilityExposure](readme/en/menu/3.EndpointAvailabilityExposure/EndpointAvailabilityExposure.md)
* **4.Health**
    * [Health](readme/en/menu/4.Health/Health.md)
* **5.AvailabilityProbes**
    * [AvailabilityProbes](readme/en/menu/5.AvailabilityProbes/AvailabilityProbes.md)
* **6.InfoEnvironment**
    * [InfoEnvironment](readme/en/menu/6.InfoEnvironment/InfoEnvironment.md)
* **7.MetricsMicrometer**
    * [MetricsMicrometer](readme/en/menu/7.MetricsMicrometer/MetricsMicrometer.md)
* **8.LoggersDiagnostics**
    * [LoggersDiagnostics](readme/en/menu/8.LoggersDiagnostics/LoggersDiagnostics.md)
* **9.CustomEndpoints**
    * [CustomEndpoints](readme/en/menu/9.CustomEndpoints/CustomEndpoints.md)
* **10.ManagementAccess**
    * [ManagementAccess](readme/en/menu/10.ManagementAccess/ManagementAccess.md)
* **11.ObservabilitySynthesis**
    * [ObservabilitySynthesis](readme/en/menu/11.ObservabilitySynthesis/ObservabilitySynthesis.md)

# Spring Boot Actuator

Spring Boot Actuator adds a production-oriented management surface to a running Boot application. This module explains how Actuator exposes selected operational state through endpoints, how endpoint enablement and exposure determine availability, and how health, metrics, logging diagnostics, and custom management operations fit together without turning Actuator into an observability platform.

## What You Will Learn

You will learn the Actuator endpoint abstraction and its web/JMX exposure model, reason about health contributors and aggregated status, connect liveness/readiness health groups to application availability, inspect operational info and environment-facing data safely, use the metrics endpoint at the Micrometer integration boundary, work with loggers and JVM-facing diagnostic endpoints, author custom management endpoints, and separate endpoint exposure from authorization and network placement.

## Prerequisites

You should already understand Spring Boot fundamentals, externalized configuration, and the application-runtime model. Basic HTTP, JMX, logging, metrics, and security vocabulary is useful, but this module does not re-teach generic observability theory, Spring Security mechanics, logging analysis, JVM dump analysis, or application lifecycle internals.

## Learning Flow

1. Establish why Actuator exists and what a production management surface owns.
2. Learn the endpoint abstraction before reasoning about enablement, availability, HTTP/JMX exposure, and the /actuator web surface.
3. Build the health model from contributors and status aggregation, then connect health groups to Boot liveness/readiness state.
4. Inspect info/environment-facing endpoints and treat sensitive values as an access concern.
5. Use the metrics endpoint while keeping meter design, telemetry export, and monitoring backends outside Actuator ownership.
6. Use loggers, thread dump, and heap dump endpoints as management/diagnostic surfaces without taking ownership of logging or JVM analysis.
7. Add custom endpoints only for operational management needs, then place the management surface deliberately with minimal exposure and clear authorization boundaries.
8. Synthesize Actuator with application-runtime state and external observability infrastructure into one troubleshooting model.

## Module Boundary

This module owns Spring Boot's production endpoint model, endpoint enablement/exposure integration, health endpoint model, Actuator-facing liveness/readiness views, info/environment-facing operational endpoints, metrics endpoint integration, logger and diagnostic endpoint surfaces, custom endpoint authoring, and management-surface access boundaries. Application lifecycle and availability transitions remain in application-runtime; configuration precedence and binding remain in externalized-configuration; authentication and authorization design remain in Spring Security; metric design, telemetry pipelines, alerting, dashboards, logging operations, and backend observability remain with observability owners; thread/heap analysis remains with JVM/runtime diagnostics.
