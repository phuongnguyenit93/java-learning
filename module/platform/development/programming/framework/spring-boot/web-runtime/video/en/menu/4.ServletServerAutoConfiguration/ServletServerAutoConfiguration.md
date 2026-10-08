---
video:
  url: ""
---

# Servlet server auto-configuration

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

## When Servlet Web Server Auto-configuration Applies

<!-- VIDEO_SECTION -->

### Scene 1 — When Servlet Web Server Auto-configuration Applies

**Time:** `00:00–00:32`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "When Servlet Web Server Auto-configuration Applies", place `ServletWebServerFactoryAutoConfiguration` at the exact node or connection it affects and fade unrelated paths.

**Script:**

`ServletWebServerFactoryAutoConfiguration` participates when Boot is running a Servlet web application and the relevant server classes are present. Its job is to assemble Boot's embedded Servlet-server infrastructure, including configuration properties and the customizer processing needed by the selected factory. The important condition is the runtime model established earlier. A Servlet server factory should not appear merely because a server jar exists in an application that is intentionally non-web or reactive.

**Purpose:**

Explain "When Servlet Web Server Auto-configuration Applies" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Tomcat, Jetty, and Undertow Servlet Factories

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:32–00:46`

**Visual:**

Keep the Servlet auto-configuration → factory → customizers → server pipeline on screen. Fade the completed "When Servlet Web Server Auto-configuration Applies" annotation and animate focus to "Tomcat, Jetty, and Undertow Servlet Factories"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "When Servlet Web Server Auto-configuration Applies" at this layer. The next dependency is "Tomcat, Jetty, and Undertow Servlet Factories"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Tomcat, Jetty, and Undertow Servlet Factories" is the next dependency after "When Servlet Web Server Auto-configuration Applies", keeping ownership and runtime state continuous across the cut.

### Scene 2 — Tomcat, Jetty, and Undertow Servlet Factories

**Time:** `00:46–01:12`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "Tomcat, Jetty, and Undertow Servlet Factories", place `TomcatServletWebServerFactory`, `JettyServletWebServerFactory`, `UndertowServletWebServerFactory` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Boot provides concrete Servlet factories for its supported embedded containers: `TomcatServletWebServerFactory`,`JettyServletWebServerFactory`, and`UndertowServletWebServerFactory`. Classpath conditions determine which implementation-specific factory configuration is eligible. Applications normally reach these factories indirectly through starters. That is why replacing the server dependency is the preferred first step: it changes the eligible implementation without replacing Boot's lifecycle and configuration machinery.

**Purpose:**

Tie the input in "Tomcat, Jetty, and Undertow Servlet Factories" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.

## Where Server Configuration Fits into Servlet Factory Auto-configuration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:12–01:25`

**Visual:**

Keep the Servlet auto-configuration → factory → customizers → server pipeline on screen. Fade the completed "Tomcat, Jetty, and Undertow Servlet Factories" annotation and animate focus to "Where Server Configuration Fits into Servlet Factory Auto-configuration"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

With "Tomcat, Jetty, and Undertow Servlet Factories" understood, test the next boundary: "Where Server Configuration Fits into Servlet Factory Auto-configuration". Keep the prior result visible so the new mechanism follows from it.

**Purpose:**

Move from "Tomcat, Jetty, and Undertow Servlet Factories" to "Where Server Configuration Fits into Servlet Factory Auto-configuration" without resetting the model, and make the changed input, owner, or output explicit.

### Scene 3 — Where Server Configuration Fits into Servlet Factory Auto-configuration

**Time:** `01:25–01:57`

**Visual:**

Draw the dependency/factory pipeline: starter or user bean → ordered customizers → WebServerFactory → embedded server; fade the auto-configured factory when back-off occurs. For "Where Server Configuration Fits into Servlet Factory Auto-configuration", place `server.*`, `ServerProperties`, `externalized-configuration` at the exact node or connection it affects and fade unrelated paths.

**Script:**

The auto-configuration does more than instantiate a factory. Boot binds `server.*` configuration into `ServerProperties` and provides customizers that apply common and server-specific settings to the factory before the server is created. Think of the flow as `external configuration → ServerProperties → Boot customizers → ServletWebServerFactory → WebServer`. The details of property source precedence and relaxed binding remain in the `externalized-configuration` module; this module focuses on how the resulting server configuration affects the web runtime.

**Purpose:**

Turn "Where Server Configuration Fits into Servlet Factory Auto-configuration" into a concrete choice by showing what changes at runtime and which supported configuration or customization layer should be used next.

## Back-off When the Application Supplies a Servlet WebServerFactory

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:57–02:11`

**Visual:**

Keep the Servlet auto-configuration → factory → customizers → server pipeline on screen. Fade the completed "Where Server Configuration Fits into Servlet Factory Auto-configuration" annotation and animate focus to "Back-off When the Application Supplies a Servlet WebServerFactory"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

We have established "Where Server Configuration Fits into Servlet Factory Auto-configuration". Keep that result visible; now move to "Back-off When the Application Supplies a Servlet WebServerFactory" and change only the next runtime decision.

**Purpose:**

Connect "Where Server Configuration Fits into Servlet Factory Auto-configuration" to "Back-off When the Application Supplies a Servlet WebServerFactory" so the next web-runtime decision follows from an established result.

### Scene 4 — Back-off When the Application Supplies a Servlet WebServerFactory

**Time:** `02:11–02:49`

**Visual:**

Draw the dependency/factory pipeline: starter or user bean → ordered customizers → WebServerFactory → embedded server; fade the auto-configured factory when back-off occurs. For "Back-off When the Application Supplies a Servlet WebServerFactory", place `ServletWebServerFactory`, `WebServerFactoryCustomizer` at the exact node or connection it affects and fade unrelated paths.

**Script:**

The embedded Servlet factory configurations are designed to back off when the application provides its own `ServletWebServerFactory` bean. This is a powerful replacement hook because the application has taken responsibility for choosing and constructing the factory. Use that hook deliberately. Supplying a factory bean is a stronger intervention than setting properties or adding a customizer, and it can reduce portability. Boot's auto-configured`WebServerFactoryCustomizer` beans still apply to a custom factory, so a replacement factory does not imply that every Boot customization disappears.

**Purpose:**

Show the concrete runtime consequence of "Back-off When the Application Supplies a Servlet WebServerFactory" and give the viewer an observable checkpoint for the Boot-owned decision.

## Boot and User Customizers around the Servlet Factory

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:49–03:04`

**Visual:**

Keep the Servlet auto-configuration → factory → customizers → server pipeline on screen. Fade the completed "Back-off When the Application Supplies a Servlet WebServerFactory" annotation and animate focus to "Boot and User Customizers around the Servlet Factory"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

The previous section gives us evidence for "Back-off When the Application Supplies a Servlet WebServerFactory". Follow the same runtime path into "Boot and User Customizers around the Servlet Factory" and watch which input, owner, or output changes.

**Purpose:**

Preserve the evidence from "Back-off When the Application Supplies a Servlet WebServerFactory" while introducing only the new mechanism required for "Boot and User Customizers around the Servlet Factory".

### Scene 5 — Boot and User Customizers around the Servlet Factory

**Time:** `03:04–03:36`

**Visual:**

Draw the dependency/factory pipeline: starter or user bean → ordered customizers → WebServerFactory → embedded server; fade the auto-configured factory when back-off occurs. For "Boot and User Customizers around the Servlet Factory", place `ServletWebServerFactory`, `WebServerFactoryCustomizer`, `0` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Boot and application customizers are collected and applied to the chosen `ServletWebServerFactory` before it creates the server. Boot's own auto-configured`WebServerFactoryCustomizer` beans use order`0`; user customizers without a more specific order therefore run after those Boot customizers. Ordering matters when two customizers change the same setting. Prefer configuration properties for supported settings; use an explicitly ordered customizer only when code must intentionally refine or override the factory state.

**Purpose:**

Explain "Boot and User Customizers around the Servlet Factory" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Servlet Server Bootstrap versus Spring MVC Request Processing

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:36–03:52`

**Visual:**

Keep the Servlet auto-configuration → factory → customizers → server pipeline on screen. Fade the completed "Boot and User Customizers around the Servlet Factory" annotation and animate focus to "Servlet Server Bootstrap versus Spring MVC Request Processing"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "Boot and User Customizers around the Servlet Factory" at this layer. The next dependency is "Servlet Server Bootstrap versus Spring MVC Request Processing"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Servlet Server Bootstrap versus Spring MVC Request Processing" is the next dependency after "Boot and User Customizers around the Servlet Factory", keeping ownership and runtime state continuous across the cut.

### Scene 6 — Servlet Server Bootstrap versus Spring MVC Request Processing

**Time:** `03:52–04:23`

**Visual:**

Draw ownership swimlanes for Boot | Spring MVC/WebFlux | embedded server | network/TLS/proxy; place the behavior in its owning lane and show the handoff arrow. For "Servlet Server Bootstrap versus Spring MVC Request Processing", place `DispatcherServlet` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Servlet server auto-configuration gets a Servlet-capable server running and connects it to Boot's lifecycle. Spring MVC request processing begins at a different ownership boundary: `DispatcherServlet`, handler mappings, controllers, argument resolution, message conversion, interceptors, and MVC error handling belong to Spring Framework web learning. The distinction is useful in debugging. If the application never binds a port, inspect application type, server dependency, factory auto-configuration, and server settings.

**Purpose:**

Tie the input in "Servlet Server Bootstrap versus Spring MVC Request Processing" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.
