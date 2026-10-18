---
video:
  url: ""
---

# Spring Boot web runtime

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** MM:SS–MM:SS

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** MM:SS–MM:SS

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

## What Does Spring Boot Own in the Web Runtime?

<!-- VIDEO_SECTION -->

### Scene 1 — What Does Spring Boot Own in the Web Runtime?

**Time:** `00:00–00:34`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "What Does Spring Boot Own in the Web Runtime?", place `ApplicationContext` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Spring Boot's web runtime is the integration layer that turns a normal Boot application into a process that owns and starts an HTTP server. It decides whether the application is web-capable, creates the appropriate web-aware `ApplicationContext`, auto-configures an embedded server factory, applies configuration and customizers, and manages the resulting server through startup and shutdown. That responsibility is narrower than "everything web". Boot connects application bootstrap, configuration, auto-configuration, and a supported server implementation.

**Purpose:**

Explain "What Does Spring Boot Own in the Web Runtime?" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Why Does Boot Need a Web Runtime Layer?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:34–00:50`

**Visual:**

Keep the Boot lifecycle/ownership diagram on screen. Fade the completed "What Does Spring Boot Own in the Web Runtime?" annotation and animate focus to "Why Does Boot Need a Web Runtime Layer?"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "What Does Spring Boot Own in the Web Runtime?" at this layer. The next dependency is "Why Does Boot Need a Web Runtime Layer?"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Why Does Boot Need a Web Runtime Layer?" is the next dependency after "What Does Spring Boot Own in the Web Runtime?", keeping ownership and runtime state continuous across the cut.

### Scene 2 — Why Does Boot Need a Web Runtime Layer?

**Time:** `00:50–01:25`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "Why Does Boot Need a Web Runtime Layer?", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

Without Boot's web runtime layer, an application would have to assemble several infrastructure decisions itself: which server implementation to use, how to construct it, how configuration reaches it, when it starts relative to the Spring context, and how it stops with the application. Boot turns those decisions into conventions that can usually be changed through dependencies and configuration instead of bespoke bootstrap code. This layer matters because "web framework" and "web server" are separate concerns.

**Purpose:**

Tie the input in "Why Does Boot Need a Web Runtime Layer?" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.

## From SpringApplication to a Running Embedded Server

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:25–01:39`

**Visual:**

Keep the Boot lifecycle/ownership diagram on screen. Fade the completed "Why Does Boot Need a Web Runtime Layer?" annotation and animate focus to "From SpringApplication to a Running Embedded Server"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

With "Why Does Boot Need a Web Runtime Layer?" understood, test the next boundary: "From SpringApplication to a Running Embedded Server". Keep the prior result visible so the new mechanism follows from it.

**Purpose:**

Move from "Why Does Boot Need a Web Runtime Layer?" to "From SpringApplication to a Running Embedded Server" without resetting the model, and make the changed input, owner, or output explicit.

### Scene 3 — From SpringApplication to a Running Embedded Server

**Time:** `01:39–02:13`

**Visual:**

Use a lifecycle timeline: SpringApplication → web-context refresh → factory lookup/customization → server create/start; for shutdown, reverse the timeline and highlight the close phase. For "From SpringApplication to a Running Embedded Server", place `main` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Start with `SpringApplication.run`. Boot first deduces, or uses the configured, `WebApplicationType`. That choice determines whether Boot creates a Servlet or reactive web application context. During context refresh, web-server auto-configuration supplies a `WebServerFactory`; `ServerProperties` and any `WebServerFactoryCustomizer` beans adjust that factory; then the context asks it to create and start the `WebServer`. The key point is ownership: the embedded server is part of the Boot-managed context lifecycle, not a separate server bootstrap beside Spring.

**Purpose:**

Turn "From SpringApplication to a Running Embedded Server" into a concrete choice by showing what changes at runtime and which supported configuration or customization layer should be used next.

## Servlet and Reactive as Runtime Models

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:13–02:25`

**Visual:**

Keep the Boot lifecycle/ownership diagram on screen. Fade the completed "From SpringApplication to a Running Embedded Server" annotation and animate focus to "Servlet and Reactive as Runtime Models"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

We have established "From SpringApplication to a Running Embedded Server". Keep that result visible; now move to "Servlet and Reactive as Runtime Models" and change only the next runtime decision.

**Purpose:**

Connect "From SpringApplication to a Running Embedded Server" to "Servlet and Reactive as Runtime Models" so the next web-runtime decision follows from an established result.

### Scene 4 — Servlet and Reactive as Runtime Models

**Time:** `02:25–03:02`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "Servlet and Reactive as Runtime Models", place `ServletWebServerApplicationContext`, `ReactiveWebServerApplicationContext` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Boot supports two web runtime models. A Servlet application uses a Servlet-capable server and a `ServletWebServerApplicationContext`. A Reactive application uses a reactive web server and a`ReactiveWebServerApplicationContext`. These choices affect server factories, auto-configuration, and server integration. They do not mean that this module owns Servlet API semantics or reactive request processing. The runtime choice answers "what kind of web application should Boot start?"; Spring MVC and Spring WebFlux answer "how does the framework process a request once the runtime is active?"

**Purpose:**

Show the concrete runtime consequence of "Servlet and Reactive as Runtime Models" and give the viewer an observable checkpoint for the Boot-owned decision.

## Where Boot Web Runtime Stops

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:02–03:15`

**Visual:**

Keep the Boot lifecycle/ownership diagram on screen. Fade the completed "Servlet and Reactive as Runtime Models" annotation and animate focus to "Where Boot Web Runtime Stops"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

The previous section gives us evidence for "Servlet and Reactive as Runtime Models". Follow the same runtime path into "Where Boot Web Runtime Stops" and watch which input, owner, or output changes.

**Purpose:**

Preserve the evidence from "Servlet and Reactive as Runtime Models" while introducing only the new mechanism required for "Where Boot Web Runtime Stops".

### Scene 5 — Where Boot Web Runtime Stops

**Time:** `03:15–03:44`

**Visual:**

Draw ownership swimlanes for Boot | Spring MVC/WebFlux | embedded server | network/TLS/proxy; place the behavior in its owning lane and show the handoff arrow. For "Where Boot Web Runtime Stops", place `server.*` at the exact node or connection it affects and fade unrelated paths.

**Script:**

This module owns Boot-specific decisions around web application type, embedded server selection, server auto-configuration, `server.*` properties, programmatic server customization, server-level HTTP features, TLS consumption, forwarded headers, and graceful shutdown. When a question becomes about handler mappings, controllers, filters in the framework request chain, codecs, reactive operators, Servlet container threading internals, HTTP protocol theory, certificate-chain theory, or reverse-proxy implementation, follow the owning curriculum instead.

**Purpose:**

Explain "Where Boot Web Runtime Stops" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## How the Web Runtime Chapters Fit Together

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:44–03:58`

**Visual:**

Keep the Boot lifecycle/ownership diagram on screen. Fade the completed "Where Boot Web Runtime Stops" annotation and animate focus to "How the Web Runtime Chapters Fit Together"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "Where Boot Web Runtime Stops" at this layer. The next dependency is "How the Web Runtime Chapters Fit Together"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "How the Web Runtime Chapters Fit Together" is the next dependency after "Where Boot Web Runtime Stops", keeping ownership and runtime state continuous across the cut.

### Scene 6 — How the Web Runtime Chapters Fit Together

**Time:** `03:58–04:38`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "How the Web Runtime Chapters Fit Together", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

The chapters follow the same order in which production decisions usually appear. First understand the application type and server chosen from the classpath. Then learn how Boot creates the corresponding server factory, how properties and customizers change it, and which server-level HTTP capabilities are portable. After the local server model is stable, add deployment concerns: TLS and SSL bundles, forwarded headers behind proxies, and graceful shutdown. The final chapter folds those decisions back into one end-to-end model and identifies where Spring Framework or infrastructure-specific learning continues.

**Purpose:**

Tie the input in "How the Web Runtime Chapters Fit Together" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.
