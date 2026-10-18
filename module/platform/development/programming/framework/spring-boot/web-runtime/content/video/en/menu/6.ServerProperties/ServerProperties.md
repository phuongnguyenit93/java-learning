---
video:
  url: ""
---

# Server properties and configuration model

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

## ServerProperties as Boot's Server Configuration Model

<!-- VIDEO_SECTION -->

### Scene 1 — ServerProperties as Boot's Server Configuration Model

**Time:** `00:00–00:34`

**Visual:**

Place property sources on the left, ServerProperties in the center, and the server factory on the right; contrast portable server.* controls with one server-specific namespace. For "ServerProperties as Boot's Server Configuration Model", place `ServerProperties`, `server.*` at the exact node or connection it affects and fade unrelated paths.

**Script:**

`ServerProperties` is Boot's configuration-properties model for the embedded server. It gives auto-configured server customizers a structured view of values under`server.*`, including common settings and implementation-specific groups. This is a useful boundary: application configuration expresses desired server behavior,`ServerProperties` represents the bound Boot model, and customizers translate that model into the selected factory. Application code normally does not need to inject`ServerProperties` merely to change a setting that can already be declared externally.

**Purpose:**

Explain "ServerProperties as Boot's Server Configuration Model" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

### Scene 2 — Runtime evidence

**Time:** `00:34–00:58`

**Visual:**

Start with --server.port=0, call GET /spring-boot/web-runtime/server-properties, and place configuredPort beside actualPort. Also highlight configuredAddress, forwardHeadersStrategy, and shutdown in the response. For "ServerProperties as Boot's Server Configuration Model", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

Step 5 gives us a clean comparison. With server.port set to 0, ServerProperties still represents the configured value 0 while WebServer reports the actual port selected by the operating system after startup. The /spring-boot/web-runtime/server-properties endpoint therefore separates Boot's bound configuration model from the realized server state.

**Purpose:**

Use the Step 5 runtime endpoint to verify "ServerProperties as Boot's Server Configuration Model" against the live context/server rather than treating the diagram as proof.

## Portable Controls in the server.* Namespace

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:12`

**Visual:**

Keep the property sources → ServerProperties → factory pipeline on screen. Fade the completed "ServerProperties as Boot's Server Configuration Model" annotation and animate focus to "Portable Controls in the server.* Namespace"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "ServerProperties as Boot's Server Configuration Model" at this layer. The next dependency is "Portable Controls in the server.* Namespace"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Portable Controls in the server.* Namespace" is the next dependency after "ServerProperties as Boot's Server Configuration Model", keeping ownership and runtime state continuous across the cut.

### Scene 3 — Portable Controls in the server.* Namespace

**Time:** `01:12–01:37`

**Visual:**

Place property sources on the left, ServerProperties in the center, and the server factory on the right; contrast portable server.* controls with one server-specific namespace. For "Portable Controls in the server.* Namespace", place `server.*`, `server.port`, `server.address` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Portable settings live directly under `server.*` when Boot can model the same intent across supported servers. Examples include `server.port`,`server.address`,`server.compression.*`,`server.http2.enabled`,`server.max-http-request-header-size`,`server.shutdown`,`server.forward-headers-strategy`, and`server.ssl.*`. Portable does not mean every server implements the feature identically. It means Boot offers one configuration intent and adapts it where the selected server supports that capability.

**Purpose:**

Tie the input in "Portable Controls in the server.* Namespace" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.

## Port, Bind Address, and HTTP Endpoint Controls

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:37–01:50`

**Visual:**

Keep the property sources → ServerProperties → factory pipeline on screen. Fade the completed "Portable Controls in the server.* Namespace" annotation and animate focus to "Port, Bind Address, and HTTP Endpoint Controls"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

With "Portable Controls in the server.* Namespace" understood, test the next boundary: "Port, Bind Address, and HTTP Endpoint Controls". Keep the prior result visible so the new mechanism follows from it.

**Purpose:**

Move from "Portable Controls in the server.* Namespace" to "Port, Bind Address, and HTTP Endpoint Controls" without resetting the model, and make the changed input, owner, or output explicit.

### Scene 4 — Port, Bind Address, and HTTP Endpoint Controls

**Time:** `01:50–02:27`

**Visual:**

Place property sources on the left, ServerProperties in the center, and the server factory on the right; contrast portable server.* controls with one server-specific namespace. For "Port, Bind Address, and HTTP Endpoint Controls", place `8080`, `server.port`, `0` at the exact node or connection it affects and fade unrelated paths.

**Script:**

The main HTTP port defaults to `8080` in a standalone web application. Set`server.port` to a fixed value,`0` to ask the operating system for an available port, or`-1` to create a web application context without opening HTTP endpoints.`server.address` controls the network address to which the server binds. These settings are server runtime controls, not routing controls. Changing the port or bind address changes where the server listens; it does not change Spring MVC or WebFlux route mappings.

**Purpose:**

Turn "Port, Bind Address, and HTTP Endpoint Controls" into a concrete choice by showing what changes at runtime and which supported configuration or customization layer should be used next.

## server.tomcat.*, server.jetty.*, server.undertow.*, and server.netty.*

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:27–02:39`

**Visual:**

Keep the property sources → ServerProperties → factory pipeline on screen. Fade the completed "Port, Bind Address, and HTTP Endpoint Controls" annotation and animate focus to "server.tomcat.*, server.jetty.*, server.undertow.*, and server.netty.*"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

We have established "Port, Bind Address, and HTTP Endpoint Controls". Keep that result visible; now move to "server.tomcat.*, server.jetty.*, server.undertow.*, and server.netty.*" and change only the next runtime decision.

**Purpose:**

Connect "Port, Bind Address, and HTTP Endpoint Controls" to "server.tomcat.*, server.jetty.*, server.undertow.*, and server.netty.*" so the next web-runtime decision follows from an established result.

### Scene 5 — server.tomcat.*, server.jetty.*, server.undertow.*, and server.netty.*

**Time:** `02:39–03:09`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "server.tomcat.*, server.jetty.*, server.undertow.*, and server.netty.*", place `server.tomcat.*`, `server.jetty.*`, `server.undertow.*` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Server-specific namespaces expose capabilities that cannot be represented cleanly as one portable property model. Boot 3.3 provides groups such as `server.tomcat.*`,`server.jetty.*`,`server.undertow.*`, and `server.netty.*`. Examples include Tomcat connection queues and remote-IP valve settings, Jetty access-log settings, Undertow worker/options configuration, and Netty connection/resource settings. Once you enter one of these namespaces, the application is intentionally coupling that configuration to a particular embedded server family.

**Purpose:**

Show the concrete runtime consequence of "server.tomcat.*, server.jetty.*, server.undertow.*, and server.netty.*" and give the viewer an observable checkpoint for the Boot-owned decision.

## Choosing Common versus Server-specific Properties

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:09–03:22`

**Visual:**

Keep the property sources → ServerProperties → factory pipeline on screen. Fade the completed "server.tomcat.*, server.jetty.*, server.undertow.*, and server.netty.*" annotation and animate focus to "Choosing Common versus Server-specific Properties"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

The previous section gives us evidence for "server.tomcat.*, server.jetty.*, server.undertow.*, and server.netty.*". Follow the same runtime path into "Choosing Common versus Server-specific Properties" and watch which input, owner, or output changes.

**Purpose:**

Preserve the evidence from "server.tomcat.*, server.jetty.*, server.undertow.*, and server.netty.*" while introducing only the new mechanism required for "Choosing Common versus Server-specific Properties".

### Scene 6 — Choosing Common versus Server-specific Properties

**Time:** `03:22–03:57`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "Choosing Common versus Server-specific Properties", place `server.*`, `WebServerFactoryCustomizer` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Start with a common `server.*` property when it expresses the requirement. This keeps the application easier to move between supported servers and makes the intent visible without server API knowledge. Use a server-specific property when the requirement itself depends on that implementation or when the common model does not expose the needed control. If neither property level is sufficient, move to a `WebServerFactoryCustomizer`. This progression keeps customization as declarative and portable as the requirement allows.

**Purpose:**

Explain "Choosing Common versus Server-specific Properties" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Configuration Precedence and Binding Belong to Externalized Configuration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:57–04:11`

**Visual:**

Keep the property sources → ServerProperties → factory pipeline on screen. Fade the completed "Choosing Common versus Server-specific Properties" annotation and animate focus to "Configuration Precedence and Binding Belong to Externalized Configuration"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "Choosing Common versus Server-specific Properties" at this layer. The next dependency is "Configuration Precedence and Binding Belong to Externalized Configuration"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Configuration Precedence and Binding Belong to Externalized Configuration" is the next dependency after "Choosing Common versus Server-specific Properties", keeping ownership and runtime state continuous across the cut.

### Scene 7 — Configuration Precedence and Binding Belong to Externalized Configuration

**Time:** `04:11–04:46`

**Visual:**

Place property sources on the left, ServerProperties in the center, and the server factory on the right; contrast portable server.* controls with one server-specific namespace. For "Configuration Precedence and Binding Belong to Externalized Configuration", place `SERVER_PORT`, `server.port`, `externalized-configuration` at the exact node or connection it affects and fade unrelated paths.

**Script:**

This module consumes the result of Boot's externalized configuration system; it does not redefine configuration precedence, profile activation, relaxed binding, environment variables, or property-source ordering. Those rules determine which value wins before server customization uses it. For example, `SERVER_PORT` can bind to`server.port`, but why an environment variable overrides or loses to another source belongs to the`externalized-configuration` module. Here the learning question is what the resolved`server.port` value does to the embedded server.

**Purpose:**

Tie the input in "Configuration Precedence and Binding Belong to Externalized Configuration" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.
