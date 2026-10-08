---
video:
  url: ""
---

# Boot web defaults and Spring Framework ownership boundaries

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

## The Boot Web Defaults to Recognize

<!-- VIDEO_SECTION -->

### Scene 1 — The Boot Web Defaults to Recognize

**Time:** `00:00–00:36`

**Visual:**

Draw ownership swimlanes for Boot | Spring MVC/WebFlux | embedded server | network/TLS/proxy; place the behavior in its owning lane and show the handoff arrow. For "The Boot Web Defaults to Recognize", place `WebApplicationType`, `spring-boot-starter-web`, `spring-boot-starter-webflux` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Several conventions explain the first successful run of a Boot web application: the classpath drives `WebApplicationType` unless overridden;`spring-boot-starter-web` selects Servlet/MVC with Tomcat by default;`spring-boot-starter-webflux` selects the reactive model with Reactor Netty when MVC is absent; the main HTTP port defaults to`8080`. Other defaults matter in production: response compression is disabled until enabled,`server.shutdown` is`immediate` in Boot 3.3, and forwarded-header processing is normally`NONE` except on supported cloud platforms where Boot defaults to`NATIVE`.

**Purpose:**

Explain "The Boot Web Defaults to Recognize" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Convention versus Explicit Web-runtime Control

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:36–00:50`

**Visual:**

Keep the Boot/framework/server/infrastructure ownership swimlane on screen. Fade the completed "The Boot Web Defaults to Recognize" annotation and animate focus to "Convention versus Explicit Web-runtime Control"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "The Boot Web Defaults to Recognize" at this layer. The next dependency is "Convention versus Explicit Web-runtime Control"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Convention versus Explicit Web-runtime Control" is the next dependency after "The Boot Web Defaults to Recognize", keeping ownership and runtime state continuous across the cut.

### Scene 2 — Convention versus Explicit Web-runtime Control

**Time:** `00:50–01:23`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "Convention versus Explicit Web-runtime Control", place `server.*` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Boot's web runtime works best when conventions express the common case and configuration makes deployment choices explicit. Dependencies select a supported server, `server.*` properties express common behavior, server-specific namespaces handle implementation details, and customizers cover gaps in the property model. Move toward explicit control when the deployment has a real reason: a mixed MVC/WebFlux classpath, a non-default server, a fixed bind address, TLS material, proxy forwarding, extra connectors, or graceful shutdown.

**Purpose:**

Tie the input in "Convention versus Explicit Web-runtime Control" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.

## Handoff to Spring MVC and Spring WebFlux

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:23–01:35`

**Visual:**

Keep the Boot/framework/server/infrastructure ownership swimlane on screen. Fade the completed "Convention versus Explicit Web-runtime Control" annotation and animate focus to "Handoff to Spring MVC and Spring WebFlux"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

With "Convention versus Explicit Web-runtime Control" understood, test the next boundary: "Handoff to Spring MVC and Spring WebFlux". Keep the prior result visible so the new mechanism follows from it.

**Purpose:**

Move from "Convention versus Explicit Web-runtime Control" to "Handoff to Spring MVC and Spring WebFlux" without resetting the model, and make the changed input, owner, or output explicit.

### Scene 3 — Handoff to Spring MVC and Spring WebFlux

**Time:** `01:35–02:14`

**Visual:**

Draw ownership swimlanes for Boot | Spring MVC/WebFlux | embedded server | network/TLS/proxy; place the behavior in its owning lane and show the handoff arrow. For "Handoff to Spring MVC and Spring WebFlux", place `DispatcherServlet` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Once Boot has selected the web runtime, started the server, and connected it to the application context, request-processing semantics belong to Spring Framework. MVC owns `DispatcherServlet`, controller mappings, converters, interceptors, and the Servlet web framework model. WebFlux owns reactive routing/handlers, codecs, filters, and its reactive processing model. Boot adds auto-configuration and sensible defaults around those frameworks, but this module stops at the runtime/server integration boundary. A server that is listening correctly can coexist with a broken handler mapping; the two failures belong to different layers.

**Purpose:**

Turn "Handoff to Spring MVC and Spring WebFlux" into a concrete choice by showing what changes at runtime and which supported configuration or customization layer should be used next.

## Handoff to Servlet and Reactive Server Internals

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:14–02:27`

**Visual:**

Keep the Boot/framework/server/infrastructure ownership swimlane on screen. Fade the completed "Handoff to Spring MVC and Spring WebFlux" annotation and animate focus to "Handoff to Servlet and Reactive Server Internals"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

We have established "Handoff to Spring MVC and Spring WebFlux". Keep that result visible; now move to "Handoff to Servlet and Reactive Server Internals" and change only the next runtime decision.

**Purpose:**

Connect "Handoff to Spring MVC and Spring WebFlux" to "Handoff to Servlet and Reactive Server Internals" so the next web-runtime decision follows from an established result.

### Scene 4 — Handoff to Servlet and Reactive Server Internals

**Time:** `02:27–02:58`

**Visual:**

Draw ownership swimlanes for Boot | Spring MVC/WebFlux | embedded server | network/TLS/proxy; place the behavior in its owning lane and show the handoff arrow. For "Handoff to Servlet and Reactive Server Internals", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

Tomcat, Jetty, Undertow, and Reactor Netty have their own connectors, handlers, workers, event loops, queues, protocol implementations, and tuning models. Boot provides properties, factories, and customizer hooks into those systems but does not make their internals portable. Learn enough server-specific API to configure a requirement through Boot, then move deep container/runtime reasoning to the appropriate owner. This keeps the Boot curriculum centered on selection, configuration, lifecycle, and integration.

**Purpose:**

Show the concrete runtime consequence of "Handoff to Servlet and Reactive Server Internals" and give the viewer an observable checkpoint for the Boot-owned decision.

## Handoff to HTTP, TLS, and Reverse-proxy Infrastructure

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:58–03:12`

**Visual:**

Keep the Boot/framework/server/infrastructure ownership swimlane on screen. Fade the completed "Handoff to Servlet and Reactive Server Internals" annotation and animate focus to "Handoff to HTTP, TLS, and Reverse-proxy Infrastructure"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

The previous section gives us evidence for "Handoff to Servlet and Reactive Server Internals". Follow the same runtime path into "Handoff to HTTP, TLS, and Reverse-proxy Infrastructure" and watch which input, owner, or output changes.

**Purpose:**

Preserve the evidence from "Handoff to Servlet and Reactive Server Internals" while introducing only the new mechanism required for "Handoff to HTTP, TLS, and Reverse-proxy Infrastructure".

### Scene 5 — Handoff to HTTP, TLS, and Reverse-proxy Infrastructure

**Time:** `03:12–03:42`

**Visual:**

Use a TLS handshake diagram with certificate material on the left, server.ssl or a named SslBundle in the center, and the HTTPS connector on the right; for SNI, let the hostname choose the certificate. For "Handoff to HTTP, TLS, and Reverse-proxy Infrastructure", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

Boot can enable compression or HTTP/2, attach certificate material, consume SSL bundles, interpret forwarded headers, and expose server-specific proxy settings. Those are integration points into larger domains. HTTP protocol mechanics, TLS/PKI theory, certificate operations, reverse-proxy routing, load-balancer behavior, network trust, and operating-system socket tuning remain infrastructure/security subjects. The Boot learner should know which property or hook connects to them and when deeper ownership begins.

**Purpose:**

Explain "Handoff to HTTP, TLS, and Reverse-proxy Infrastructure" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## End-to-end Boot Web Runtime Decision Model

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:42–03:56`

**Visual:**

Keep the Boot/framework/server/infrastructure ownership swimlane on screen. Fade the completed "Handoff to HTTP, TLS, and Reverse-proxy Infrastructure" annotation and animate focus to "End-to-end Boot Web Runtime Decision Model"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "Handoff to HTTP, TLS, and Reverse-proxy Infrastructure" at this layer. The next dependency is "End-to-end Boot Web Runtime Decision Model"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "End-to-end Boot Web Runtime Decision Model" is the next dependency after "Handoff to HTTP, TLS, and Reverse-proxy Infrastructure", keeping ownership and runtime state continuous across the cut.

### Scene 6 — End-to-end Boot Web Runtime Decision Model

**Time:** `03:56–04:44`

**Visual:**

Draw ownership swimlanes for Boot | Spring MVC/WebFlux | embedded server | network/TLS/proxy; place the behavior in its owning lane and show the handoff arrow. For "End-to-end Boot Web Runtime Decision Model", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

Use the model as a sequence of questions rather than a checklist to memorize. First choose the `WebApplicationType`, then confirm the supported embedded server and the matching Servlet or reactive factory auto-configuration. Next ask whether `server.*`, a server-specific property, or the narrowest `WebServerFactoryCustomizer` can express the requirement. After that, account for deployment features such as HTTP/2, TLS or SNI, forwarded headers, and the server's shutdown behavior. Finally, ask step eight: is the remaining problem actually owned by MVC or WebFlux, by server internals, or by network and security infrastructure? That last question is the handoff that keeps Boot runtime diagnosis in the correct layer.

**Purpose:**

Tie the input in "End-to-end Boot Web Runtime Decision Model" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.
