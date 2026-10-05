# 📂 README MODULE STRUCTURE (EN)

* **1.Purpose**
    * [WebFluxPurpose](readme/en/menu/1.Purpose/WebFluxPurpose.md)
* **2.RuntimeArchitecture**
    * [WebFluxRuntime](readme/en/menu/2.RuntimeArchitecture/WebFluxRuntime.md)
* **3.HttpDataFlow**
    * [ReactiveHttpDataFlow](readme/en/menu/3.HttpDataFlow/ReactiveHttpDataFlow.md)
* **4.AnnotatedControllers**
    * [AnnotatedControllers](readme/en/menu/4.AnnotatedControllers/AnnotatedControllers.md)
* **5.FunctionalEndpoints**
    * [FunctionalEndpoints](readme/en/menu/5.FunctionalEndpoints/FunctionalEndpoints.md)
* **6.CrossCuttingProcessing**
    * [CrossCuttingProcessing](readme/en/menu/6.CrossCuttingProcessing/CrossCuttingProcessing.md)
* **7.WebClient**
    * [WebClient](readme/en/menu/7.WebClient/WebClient.md)
* **8.ProductionBoundaries**
    * [ProductionBoundaries](readme/en/menu/8.ProductionBoundaries/ProductionBoundaries.md)

# Spring WebFlux

This module explains Spring WebFlux as Spring Framework's reactive, non-blocking web stack. The goal is not to relearn Reactive Streams or Project Reactor from scratch, but to understand how Spring applies reactive principles to HTTP server processing, endpoint programming, cross-cutting web infrastructure, and outbound HTTP calls.

You should already be comfortable with Java, HTTP request/response fundamentals, and the basic reactive concepts of publishers, subscribers, asynchronous composition, and backpressure. The general reactive programming model belongs to the Reactive Programming module; this module focuses on WebFlux-specific mechanics.

The learning flow is:

1. understand why WebFlux exists and when its non-blocking model is useful;
2. learn the reactive HTTP runtime, WebHandler chain, DispatcherHandler, and result-processing architecture;
3. understand request/response bodies, codecs, streaming, multipart data, validation integration, and HTTP caching behavior;
4. learn annotated reactive controllers;
5. learn functional endpoints with WebFlux.fn;
6. understand filters, Reactor Context, sessions, error layers, configuration, and controlled blocking execution;
7. learn WebClient and the lifecycle of its underlying connector resources;
8. synthesize production trade-offs, failure modes, neighboring-module handoffs, and the end-to-end request flow.

This module deliberately stops at its ownership boundaries. Reusable validation, binding, conversion, and formatting belong to Spring Framework Validation and Data Binding. Reactive persistence mechanics belong to Spring Framework Data Access and Spring Data. Reactive transaction semantics belong to Transaction Management. Framework-level testing belongs to Spring Testing. Spring WebSocket client/server abstractions, WebSocket handler/session lifecycle, STOMP, and higher-level application messaging belong to Spring Messaging. Generic Reactor and Reactive Streams theory remains in the Reactive Programming module.

By the end, you should be able to trace a WebFlux request from the HTTP adapter through dispatch and endpoint handling to response completion, explain the consequences of blocking work and resource usage, choose between Spring MVC and WebFlux based on real constraints, and identify the correct owner when a concern leaves the WebFlux request-response boundary.
