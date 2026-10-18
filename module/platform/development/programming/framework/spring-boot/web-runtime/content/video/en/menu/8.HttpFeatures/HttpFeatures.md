---
video:
  url: ""
---

# HTTP features, compression, and connection settings

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

## HTTP Response Compression

<!-- VIDEO_SECTION -->

### Scene 1 — HTTP Response Compression

**Time:** `00:00–00:31`

**Visual:**

Show one HTTP exchange and change only the owned layer: response body for compression, connection/protocol for HTTP/2, request boundary for header limits, terminal output for access logging. For "HTTP Response Compression", place `server.compression.*`, `server.compression.enabled=true`, `2KB` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Boot exposes response compression through `server.compression.*`. It is disabled by default;`server.compression.enabled=true` enables the feature on supported embedded servers. Boot 3.3 supports response compression with Jetty, Tomcat, Reactor Netty, and Undertow. Compression also has eligibility rules. The default minimum response size is`2KB`, and only configured MIME types are compressed. Use`server.compression.min-response-size` and`server.compression.mime-types` to tune those decisions rather than assuming every response will be compressed.

**Purpose:**

Explain "HTTP Response Compression" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## HTTP/2 Enablement and Server Support

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:31–00:43`

**Visual:**

Keep the HTTP exchange and server-capability diagram on screen. Fade the completed "HTTP Response Compression" annotation and animate focus to "HTTP/2 Enablement and Server Support"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "HTTP Response Compression" at this layer. The next dependency is "HTTP/2 Enablement and Server Support"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "HTTP/2 Enablement and Server Support" is the next dependency after "HTTP Response Compression", keeping ownership and runtime state continuous across the cut.

### Scene 2 — HTTP/2 Enablement and Server Support

**Time:** `00:43–01:23`

**Visual:**

Place property sources on the left, ServerProperties in the center, and the server factory on the right; contrast portable server.* controls with one server-specific namespace. For "HTTP/2 Enablement and Server Support", place `server.http2.enabled=true`, `h2`, `h2c` at the exact node or connection it affects and fade unrelated paths.

**Script:**

`server.http2.enabled=true` is Boot's common switch for HTTP/2. With SSL enabled the server uses HTTP/2 over TLS (`h2`); without SSL, supported servers use clear-text HTTP/2 (`h2c`). This is a server capability decision, not a Spring MVC or WebFlux request-mapping feature. Support details still vary by server. Tomcat 10.1 used by Boot 3.3 supports`h2` and`h2c` out of the box, while Jetty requires its additional HTTP/2 server dependency. Check the selected server's requirements when a common Boot switch does not produce the expected protocol behavior.

**Purpose:**

Tie the input in "HTTP/2 Enablement and Server Support" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.

## Request Header Size Limits

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:23–01:34`

**Visual:**

Keep the HTTP exchange and server-capability diagram on screen. Fade the completed "HTTP/2 Enablement and Server Support" annotation and animate focus to "Request Header Size Limits"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

With "HTTP/2 Enablement and Server Support" understood, test the next boundary: "Request Header Size Limits". Keep the prior result visible so the new mechanism follows from it.

**Purpose:**

Move from "HTTP/2 Enablement and Server Support" to "Request Header Size Limits" without resetting the model, and make the changed input, owner, or output explicit.

### Scene 3 — Request Header Size Limits

**Time:** `01:34–02:08`

**Visual:**

Show one HTTP exchange and change only the owned layer: response body for compression, connection/protocol for HTTP/2, request boundary for header limits, terminal output for access logging. For "Request Header Size Limits", place `server.max-http-request-header-size` at the exact node or connection it affects and fade unrelated paths.

**Script:**

`server.max-http-request-header-size` expresses a common upper bound for HTTP request headers. It is useful for protecting the server from unexpectedly large request metadata and for aligning application limits with upstream proxies. Do not assume one common property covers every header-related limit in every server. Response-header limits and lower-level parser or connector controls can require server-specific properties or APIs. Start from Boot's portable request limit and move deeper only when the deployment needs it.

**Purpose:**

Turn "Request Header Size Limits" into a concrete choice by showing what changes at runtime and which supported configuration or customization layer should be used next.

## Connection and Server-level HTTP Settings

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:08–02:19`

**Visual:**

Keep the HTTP exchange and server-capability diagram on screen. Fade the completed "Request Header Size Limits" annotation and animate focus to "Connection and Server-level HTTP Settings"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

We have established "Request Header Size Limits". Keep that result visible; now move to "Connection and Server-level HTTP Settings" and change only the next runtime decision.

**Purpose:**

Connect "Request Header Size Limits" to "Connection and Server-level HTTP Settings" so the next web-runtime decision follows from an established result.

### Scene 4 — Connection and Server-level HTTP Settings

**Time:** `02:19–02:55`

**Visual:**

Show one HTTP exchange and change only the owned layer: response body for compression, connection/protocol for HTTP/2, request boundary for header limits, terminal output for access logging. For "Connection and Server-level HTTP Settings", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

Connection queues, idle behavior, worker/thread resources, low-level protocol options, and similar controls are often server-specific because the implementations expose different runtime models. Boot surfaces many of them under the corresponding server namespace and leaves uncommon cases to factory customizers. Tune these settings only after identifying the actual server and operational symptom. A property copied from Tomcat has no portable meaning for Reactor Netty, and a Netty event-loop setting should not be presented as a generic Spring Boot web rule.

**Purpose:**

Show the concrete runtime consequence of "Connection and Server-level HTTP Settings" and give the viewer an observable checkpoint for the Boot-owned decision.

## Embedded Server Access Logging

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:55–03:07`

**Visual:**

Keep the HTTP exchange and server-capability diagram on screen. Fade the completed "Connection and Server-level HTTP Settings" annotation and animate focus to "Embedded Server Access Logging"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

The previous section gives us evidence for "Connection and Server-level HTTP Settings". Follow the same runtime path into "Embedded Server Access Logging" and watch which input, owner, or output changes.

**Purpose:**

Preserve the evidence from "Connection and Server-level HTTP Settings" while introducing only the new mechanism required for "Embedded Server Access Logging".

### Scene 5 — Embedded Server Access Logging

**Time:** `03:07–03:41`

**Visual:**

Show one HTTP exchange and change only the owned layer: response body for compression, connection/protocol for HTTP/2, request boundary for header limits, terminal output for access logging. For "Embedded Server Access Logging", place `server.tomcat.accesslog.*`, `server.undertow.accesslog.*`, `server.jetty.accesslog.*` at the exact node or connection it affects and fade unrelated paths.

**Script:**

An embedded server access log records server-level request observations such as remote address, request line, status, timing, and bytes. It is separate from application logging performed inside controllers or filters. Access logging is configured per server. Boot's 3.3 how-to documents Tomcat under `server.tomcat.accesslog.*`, Undertow under `server.undertow.accesslog.*`, and Jetty under `server.jetty.accesslog.*`. Format tokens, file locations, rotation, and available fields are implementation concerns, so consult the selected server once Boot has enabled the feature.

**Purpose:**

Explain "Embedded Server Access Logging" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Portable HTTP Controls versus Server-specific Capabilities

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:41–03:54`

**Visual:**

Keep the HTTP exchange and server-capability diagram on screen. Fade the completed "Embedded Server Access Logging" annotation and animate focus to "Portable HTTP Controls versus Server-specific Capabilities"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "Embedded Server Access Logging" at this layer. The next dependency is "Portable HTTP Controls versus Server-specific Capabilities"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Portable HTTP Controls versus Server-specific Capabilities" is the next dependency after "Embedded Server Access Logging", keeping ownership and runtime state continuous across the cut.

### Scene 6 — Portable HTTP Controls versus Server-specific Capabilities

**Time:** `03:54–04:31`

**Visual:**

Place property sources on the left, ServerProperties in the center, and the server factory on the right; contrast portable server.* controls with one server-specific namespace. For "Portable HTTP Controls versus Server-specific Capabilities", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

A common Boot property means "one configuration intent across supported integrations". It does not promise identical internals, identical defaults below the Boot layer, or identical edge-case behavior. Compression, HTTP/2, request-header limits, and graceful shutdown all demonstrate this pattern. Use portable controls to express application intent and server-specific settings for implementation-dependent requirements. If a production decision depends on protocol theory, proxy behavior, kernel/network tuning, or container internals, hand that depth to the infrastructure owner rather than expanding the Boot integration layer indefinitely.

**Purpose:**

Tie the input in "Portable HTTP Controls versus Server-specific Capabilities" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.
