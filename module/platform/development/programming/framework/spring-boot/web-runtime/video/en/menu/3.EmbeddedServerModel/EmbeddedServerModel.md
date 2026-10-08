---
video:
  url: ""
---

# Embedded server model

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

## Web Starters and Their Default Embedded Servers

<!-- VIDEO_SECTION -->

### Scene 1 — Web Starters and Their Default Embedded Servers

**Time:** `00:00–00:27`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "Web Starters and Their Default Embedded Servers", place `spring-boot-starter-web`, `spring-boot-starter-tomcat`, `spring-boot-starter-webflux` at the exact node or connection it affects and fade unrelated paths.

**Script:**

`spring-boot-starter-web` brings Tomcat through`spring-boot-starter-tomcat`, so Tomcat is the conventional embedded server for Servlet applications.`spring-boot-starter-webflux` brings Reactor Netty through`spring-boot-starter-reactor-netty`, so Reactor Netty is the conventional reactive server. These are starter defaults, not hard-coded requirements. Boot's web runtime is designed so that the server implementation can be replaced while the surrounding application remains a Boot application.

**Purpose:**

Explain "Web Starters and Their Default Embedded Servers" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Supported Server Choices for Servlet and Reactive Applications

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:27–00:42`

**Visual:**

Keep the starter → factory → server pipeline on screen. Fade the completed "Web Starters and Their Default Embedded Servers" annotation and animate focus to "Supported Server Choices for Servlet and Reactive Applications"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "Web Starters and Their Default Embedded Servers" at this layer. The next dependency is "Supported Server Choices for Servlet and Reactive Applications"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Supported Server Choices for Servlet and Reactive Applications" is the next dependency after "Web Starters and Their Default Embedded Servers", keeping ownership and runtime state continuous across the cut.

### Scene 2 — Supported Server Choices for Servlet and Reactive Applications

**Time:** `00:42–01:11`

**Visual:**

Draw the dependency/factory pipeline: starter or user bean → ordered customizers → WebServerFactory → embedded server; fade the auto-configured factory when back-off occurs. For "Supported Server Choices for Servlet and Reactive Applications", place `TomcatServletWebServerFactory`, `TomcatReactiveWebServerFactory` at the exact node or connection it affects and fade unrelated paths.

**Script:**

For the Servlet stack, Boot 3.3 supports embedded Tomcat, Jetty, and Undertow. For the reactive stack, it supports Reactor Netty as well as reactive adapters for Tomcat, Jetty, and Undertow. The same server name can therefore appear in different runtime models. `TomcatServletWebServerFactory` and`TomcatReactiveWebServerFactory`, for example, are different Boot integrations. Choose the web stack first, then the concrete server implementation within that stack.

**Purpose:**

Tie the input in "Supported Server Choices for Servlet and Reactive Applications" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.

## Replacing the Default Embedded Server Dependency

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:11–01:24`

**Visual:**

Keep the starter → factory → server pipeline on screen. Fade the completed "Supported Server Choices for Servlet and Reactive Applications" annotation and animate focus to "Replacing the Default Embedded Server Dependency"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

With "Supported Server Choices for Servlet and Reactive Applications" understood, test the next boundary: "Replacing the Default Embedded Server Dependency". Keep the prior result visible so the new mechanism follows from it.

**Purpose:**

Move from "Supported Server Choices for Servlet and Reactive Applications" to "Replacing the Default Embedded Server Dependency" without resetting the model, and make the changed input, owner, or output explicit.

### Scene 3 — Replacing the Default Embedded Server Dependency

**Time:** `01:24–02:01`

**Visual:**

Draw the dependency/factory pipeline: starter or user bean → ordered customizers → WebServerFactory → embedded server; fade the auto-configured factory when back-off occurs. For "Replacing the Default Embedded Server Dependency", place `spring-boot-starter-tomcat`, `spring-boot-starter-jetty` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Replacing a server is normally a dependency decision. Remove or replace the starter-provided default and add the starter for the server you want. For example, a Servlet application can replace `spring-boot-starter-tomcat` with`spring-boot-starter-jetty`; a WebFlux application can replace Reactor Netty with Undertow. This keeps Boot's auto-configuration model intact. The classpath now exposes a different supported server implementation, so the matching factory configuration becomes eligible. Avoid writing server bootstrap code merely to perform a dependency swap that Boot already supports.

**Purpose:**

Turn "Replacing the Default Embedded Server Dependency" into a concrete choice by showing what changes at runtime and which supported configuration or customization layer should be used next.

## The Role of WebServerFactory

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:01–02:12`

**Visual:**

Keep the starter → factory → server pipeline on screen. Fade the completed "Replacing the Default Embedded Server Dependency" annotation and animate focus to "The Role of WebServerFactory"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

We have established "Replacing the Default Embedded Server Dependency". Keep that result visible; now move to "The Role of WebServerFactory" and change only the next runtime decision.

**Purpose:**

Connect "Replacing the Default Embedded Server Dependency" to "The Role of WebServerFactory" so the next web-runtime decision follows from an established result.

### Scene 4 — The Role of WebServerFactory

**Time:** `02:12–02:42`

**Visual:**

Draw the dependency/factory pipeline: starter or user bean → ordered customizers → WebServerFactory → embedded server; fade the auto-configured factory when back-off occurs. For "The Role of WebServerFactory", place `WebServerFactory`, `WebServer`, `ServletWebServerFactory` at the exact node or connection it affects and fade unrelated paths.

**Script:**

`WebServerFactory` is Boot's abstraction for creating the runtime`WebServer`. Servlet applications work with`ServletWebServerFactory`; reactive applications work with`ReactiveWebServerFactory`. Concrete factories such as`TomcatServletWebServerFactory` or`NettyReactiveWebServerFactory` adapt that abstraction to a server implementation. The factory is the customization point before the server exists. Properties and`WebServerFactoryCustomizer` beans modify the factory; the web application context later asks the factory to create the actual server.

**Purpose:**

Show the concrete runtime consequence of "The Role of WebServerFactory" and give the viewer an observable checkpoint for the Boot-owned decision.

## How the Web Application Context Starts the Server

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:56`

**Visual:**

Keep the starter → factory → server pipeline on screen. Fade the completed "The Role of WebServerFactory" annotation and animate focus to "How the Web Application Context Starts the Server"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

The previous section gives us evidence for "The Role of WebServerFactory". Follow the same runtime path into "How the Web Application Context Starts the Server" and watch which input, owner, or output changes.

**Purpose:**

Preserve the evidence from "The Role of WebServerFactory" while introducing only the new mechanism required for "How the Web Application Context Starts the Server".

### Scene 5 — How the Web Application Context Starts the Server

**Time:** `02:56–03:29`

**Visual:**

Use a lifecycle timeline: SpringApplication → web-context refresh → factory lookup/customization → server create/start; for shutdown, reverse the timeline and highlight the close phase. For "How the Web Application Context Starts the Server", place `ApplicationContext`, `WebServer`, `server.port=0` at the exact node or connection it affects and fade unrelated paths.

**Script:**

A web-aware Boot `ApplicationContext` coordinates server creation with context refresh. Once the required factory and application infrastructure are ready, the context obtains a`WebServer` from the factory and starts it as part of Boot's managed lifecycle. The server's actual bound port may only be known after initialization, especially with`server.port=0`. Boot publishes a`WebServerInitializedEvent` after the server is ready, and the`WebServerApplicationContext` exposes the server for runtime inspection.

**Purpose:**

Explain "How the Web Application Context Starts the Server" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

### Scene 6 — Runtime evidence

**Time:** `03:29–03:53`

**Visual:**

Run the application, call GET /spring-boot/web-runtime/server, and place the response beside WebRuntimeExperimentService.embeddedServer(). Highlight applicationContextType, webServerType, and actualPort. For "How the Web Application Context Starts the Server", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

This is Step 5 runtime evidence. The /spring-boot/web-runtime/server endpoint reads the ServletWebServerApplicationContext serving the request, obtains its initialized WebServer, and returns the concrete context type, server type, and actual port. That observation connects the bootstrap diagram to a real server instance managed by Boot.

**Purpose:**

Use the Step 5 runtime endpoint to verify "How the Web Application Context Starts the Server" against the live context/server rather than treating the diagram as proof.

## Boot Selection versus Server Implementation Internals

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:53–04:08`

**Visual:**

Keep the starter → factory → server pipeline on screen. Fade the completed "How the Web Application Context Starts the Server" annotation and animate focus to "Boot Selection versus Server Implementation Internals"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "How the Web Application Context Starts the Server" at this layer. The next dependency is "Boot Selection versus Server Implementation Internals"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Boot Selection versus Server Implementation Internals" is the next dependency after "How the Web Application Context Starts the Server", keeping ownership and runtime state continuous across the cut.

### Scene 7 — Boot Selection versus Server Implementation Internals

**Time:** `04:08–04:46`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "Boot Selection versus Server Implementation Internals", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

Boot owns the selection and integration contract: which supported server factory is eligible, which configuration is applied, how customizers participate, and how the server joins application startup and shutdown. It does not redefine how Tomcat connectors, Jetty handlers, Undertow workers, or Netty event loops work internally. When server-specific internals matter, use them through the narrowest Boot customization hook that solves the requirement and continue deeper learning in the server/runtime owner. That preserves portability and keeps application code from depending on implementation details unnecessarily.

**Purpose:**

Tie the input in "Boot Selection versus Server Implementation Internals" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.
