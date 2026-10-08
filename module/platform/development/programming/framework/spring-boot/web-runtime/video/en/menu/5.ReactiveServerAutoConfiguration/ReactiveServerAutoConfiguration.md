---
video:
  url: ""
---

# Reactive server auto-configuration

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

## When Reactive Web Server Auto-configuration Applies

<!-- VIDEO_SECTION -->

### Scene 1 — When Reactive Web Server Auto-configuration Applies

**Time:** `00:00–00:28`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "When Reactive Web Server Auto-configuration Applies", place `ReactiveWebServerFactoryAutoConfiguration`, `REACTIVE` at the exact node or connection it affects and fade unrelated paths.

**Script:**

`ReactiveWebServerFactoryAutoConfiguration` is the reactive counterpart to the Servlet server path. It participates for a reactive web application when the required server/runtime classes are present, configures the reactive factory infrastructure, and registers the customizer processing used before the server starts. The`REACTIVE` application type is therefore a bootstrap input to server selection, not a label applied after a server is already running.

**Purpose:**

Explain "When Reactive Web Server Auto-configuration Applies" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Why Reactor Netty Is the Default Reactive Server

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:28–00:43`

**Visual:**

Keep the Reactive auto-configuration → factory → customizers → server pipeline on screen. Fade the completed "When Reactive Web Server Auto-configuration Applies" annotation and animate focus to "Why Reactor Netty Is the Default Reactive Server"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "When Reactive Web Server Auto-configuration Applies" at this layer. The next dependency is "Why Reactor Netty Is the Default Reactive Server"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Why Reactor Netty Is the Default Reactive Server" is the next dependency after "When Reactive Web Server Auto-configuration Applies", keeping ownership and runtime state continuous across the cut.

### Scene 2 — Why Reactor Netty Is the Default Reactive Server

**Time:** `00:43–01:08`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "Why Reactor Netty Is the Default Reactive Server", place `spring-boot-starter-webflux`, `spring-boot-starter-reactor-netty`, `NettyReactiveWebServerFactory` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Reactor Netty is the default because `spring-boot-starter-webflux` includes`spring-boot-starter-reactor-netty`. A normal WebFlux application therefore has the classes needed for Boot to auto-configure`NettyReactiveWebServerFactory` without extra server choices. "Default" is a dependency convention. It does not mean WebFlux requires Reactor Netty: Boot 3.3 can also run the reactive stack on supported Tomcat, Jetty, or Undertow integrations.

**Purpose:**

Tie the input in "Why Reactor Netty Is the Default Reactive Server" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.

## Reactive Factories for Netty, Tomcat, Jetty, and Undertow

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:08–01:22`

**Visual:**

Keep the Reactive auto-configuration → factory → customizers → server pipeline on screen. Fade the completed "Why Reactor Netty Is the Default Reactive Server" annotation and animate focus to "Reactive Factories for Netty, Tomcat, Jetty, and Undertow"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

With "Why Reactor Netty Is the Default Reactive Server" understood, test the next boundary: "Reactive Factories for Netty, Tomcat, Jetty, and Undertow". Keep the prior result visible so the new mechanism follows from it.

**Purpose:**

Move from "Why Reactor Netty Is the Default Reactive Server" to "Reactive Factories for Netty, Tomcat, Jetty, and Undertow" without resetting the model, and make the changed input, owner, or output explicit.

### Scene 3 — Reactive Factories for Netty, Tomcat, Jetty, and Undertow

**Time:** `01:22–01:50`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "Reactive Factories for Netty, Tomcat, Jetty, and Undertow", place `NettyReactiveWebServerFactory`, `TomcatReactiveWebServerFactory`, `JettyReactiveWebServerFactory` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Boot provides `NettyReactiveWebServerFactory`,`TomcatReactiveWebServerFactory`,`JettyReactiveWebServerFactory`, and`UndertowReactiveWebServerFactory`. The chosen implementation follows the reactive application model plus the compatible server classes available on the classpath. This is why "Tomcat" alone does not imply MVC. Tomcat can host a Servlet application or participate in a reactive server integration; the web application type and matching factory determine which Boot path is in use.

**Purpose:**

Turn "Reactive Factories for Netty, Tomcat, Jetty, and Undertow" into a concrete choice by showing what changes at runtime and which supported configuration or customization layer should be used next.

## Where Server Configuration Fits into Reactive Factory Auto-configuration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:04`

**Visual:**

Keep the Reactive auto-configuration → factory → customizers → server pipeline on screen. Fade the completed "Reactive Factories for Netty, Tomcat, Jetty, and Undertow" annotation and animate focus to "Where Server Configuration Fits into Reactive Factory Auto-configuration"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

We have established "Reactive Factories for Netty, Tomcat, Jetty, and Undertow". Keep that result visible; now move to "Where Server Configuration Fits into Reactive Factory Auto-configuration" and change only the next runtime decision.

**Purpose:**

Connect "Reactive Factories for Netty, Tomcat, Jetty, and Undertow" to "Where Server Configuration Fits into Reactive Factory Auto-configuration" so the next web-runtime decision follows from an established result.

### Scene 4 — Where Server Configuration Fits into Reactive Factory Auto-configuration

**Time:** `02:04–02:38`

**Visual:**

Draw the dependency/factory pipeline: starter or user bean → ordered customizers → WebServerFactory → embedded server; fade the auto-configured factory when back-off occurs. For "Where Server Configuration Fits into Reactive Factory Auto-configuration", place `ServerProperties`, `ReactiveWebServerFactoryCustomizer`, `server.*` at the exact node or connection it affects and fade unrelated paths.

**Script:**

`ServerProperties` also feeds the reactive server path. Boot supplies a`ReactiveWebServerFactoryCustomizer` for portable server settings and additional server-specific customizers for implementation-specific namespaces where supported. The resulting pattern is the same as the Servlet side: configuration is resolved first, customizers apply it to the selected factory, and only then does the reactive web application context create the server. This shared model lets later chapters discuss common`server.*` settings without duplicating them for both stacks.

**Purpose:**

Show the concrete runtime consequence of "Where Server Configuration Fits into Reactive Factory Auto-configuration" and give the viewer an observable checkpoint for the Boot-owned decision.

## Back-off When the Application Supplies a Reactive WebServerFactory

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:38–02:53`

**Visual:**

Keep the Reactive auto-configuration → factory → customizers → server pipeline on screen. Fade the completed "Where Server Configuration Fits into Reactive Factory Auto-configuration" annotation and animate focus to "Back-off When the Application Supplies a Reactive WebServerFactory"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

The previous section gives us evidence for "Where Server Configuration Fits into Reactive Factory Auto-configuration". Follow the same runtime path into "Back-off When the Application Supplies a Reactive WebServerFactory" and watch which input, owner, or output changes.

**Purpose:**

Preserve the evidence from "Where Server Configuration Fits into Reactive Factory Auto-configuration" while introducing only the new mechanism required for "Back-off When the Application Supplies a Reactive WebServerFactory".

### Scene 5 — Back-off When the Application Supplies a Reactive WebServerFactory

**Time:** `02:53–03:29`

**Visual:**

Draw the dependency/factory pipeline: starter or user bean → ordered customizers → WebServerFactory → embedded server; fade the auto-configured factory when back-off occurs. For "Back-off When the Application Supplies a Reactive WebServerFactory", place `ReactiveWebServerFactory` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Reactive server factory configuration backs off when the application supplies its own `ReactiveWebServerFactory` bean. Doing so replaces Boot's choice of factory, so it is appropriate only when properties and targeted customizers cannot represent the requirement. Boot's auto-configured factory customizers still apply to the custom factory. If a replacement factory appears to ignore or override application settings, inspect both the factory's initial state and the ordered customizer chain before assuming auto-configuration has been disabled entirely.

**Purpose:**

Explain "Back-off When the Application Supplies a Reactive WebServerFactory" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Reactive Server Bootstrap versus Spring WebFlux Request Processing

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:29–03:45`

**Visual:**

Keep the Reactive auto-configuration → factory → customizers → server pipeline on screen. Fade the completed "Back-off When the Application Supplies a Reactive WebServerFactory" annotation and animate focus to "Reactive Server Bootstrap versus Spring WebFlux Request Processing"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "Back-off When the Application Supplies a Reactive WebServerFactory" at this layer. The next dependency is "Reactive Server Bootstrap versus Spring WebFlux Request Processing"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Reactive Server Bootstrap versus Spring WebFlux Request Processing" is the next dependency after "Back-off When the Application Supplies a Reactive WebServerFactory", keeping ownership and runtime state continuous across the cut.

### Scene 6 — Reactive Server Bootstrap versus Spring WebFlux Request Processing

**Time:** `03:45–04:15`

**Visual:**

Draw ownership swimlanes for Boot | Spring MVC/WebFlux | embedded server | network/TLS/proxy; place the behavior in its owning lane and show the handoff arrow. For "Reactive Server Bootstrap versus Spring WebFlux Request Processing", place `HttpHandler` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Reactive server auto-configuration owns server bootstrap and lifecycle integration. Spring WebFlux owns the HTTP processing model above it: `HttpHandler`, routing, annotated controllers, codecs, filters, reactive composition, and back-pressure semantics. Keep this boundary visible when tuning the application. A port, TLS, compression, or server resource issue belongs first to the Boot/server runtime layer. A route, codec, or reactive pipeline issue belongs to WebFlux and Reactor learning.

**Purpose:**

Tie the input in "Reactive Server Bootstrap versus Spring WebFlux Request Processing" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.
