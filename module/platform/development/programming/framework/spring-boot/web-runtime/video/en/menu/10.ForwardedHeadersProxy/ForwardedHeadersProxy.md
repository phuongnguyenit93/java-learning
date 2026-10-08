---
video:
  url: ""
---

# Forwarded headers and reverse-proxy deployment

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

## Why Proxy Deployment Changes Request Metadata

<!-- VIDEO_SECTION -->

### Scene 1 — Why Proxy Deployment Changes Request Metadata

**Time:** `00:00–00:39`

**Visual:**

Draw client → reverse proxy → Boot app; compare host/scheme/remote address before and after the proxy, then place NONE/NATIVE/FRAMEWORK at the node that actually processes headers. For "Why Proxy Deployment Changes Request Metadata", place `10.0.0.5:8080`, `https://example.org`, `443` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Behind a reverse proxy, the connection observed by the embedded server can differ from the public request. The application may receive traffic on `10.0.0.5:8080` over HTTP while the client actually used`https://example.org` on port`443`. If that difference is ignored, generated redirects, links, scheme checks, and client-address observations can be wrong. Forwarded headers carry selected information about the original request across that proxy hop. Boot's job is to choose how the application/server consumes that metadata; proxy routing and header production remain infrastructure concerns.

**Purpose:**

Explain "Why Proxy Deployment Changes Request Metadata" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## NONE, NATIVE, and FRAMEWORK Strategies

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:39–00:53`

**Visual:**

Keep the client → trusted proxy → Boot request-metadata path on screen. Fade the completed "Why Proxy Deployment Changes Request Metadata" annotation and animate focus to "NONE, NATIVE, and FRAMEWORK Strategies"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "Why Proxy Deployment Changes Request Metadata" at this layer. The next dependency is "NONE, NATIVE, and FRAMEWORK Strategies"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "NONE, NATIVE, and FRAMEWORK Strategies" is the next dependency after "Why Proxy Deployment Changes Request Metadata", keeping ownership and runtime state continuous across the cut.

### Scene 2 — NONE, NATIVE, and FRAMEWORK Strategies

**Time:** `00:53–01:24`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "NONE, NATIVE, and FRAMEWORK Strategies", place `server.forward-headers-strategy`, `NONE`, `NATIVE` at the exact node or connection it affects and fade unrelated paths.

**Script:**

`server.forward-headers-strategy` chooses Boot's handling mode.`NONE` does not enable forwarded-header processing.`NATIVE` delegates to the embedded server's native mechanism.`FRAMEWORK` uses Spring Framework support in the application stack. Choose the mode from the deployment contract. If the proxy provides standard/common headers that the server handles correctly,`NATIVE` keeps processing close to the server. If the application needs the broader Spring Framework transformation model, use`FRAMEWORK`.

**Purpose:**

Tie the input in "NONE, NATIVE, and FRAMEWORK Strategies" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.

### Scene 3 — Runtime evidence

**Time:** `01:24–01:57`

**Visual:**

Open WebRuntimeExperimentController.requestMetadata() and WebRuntimeExperimentService.requestMetadata(). Beside the source, place NONE, NATIVE, and FRAMEWORK under server.forward-headers-strategy; circle the scheme, secure, serverName, serverPort, remoteAddress, and requestUrl fields returned by the endpoint. For "NONE, NATIVE, and FRAMEWORK Strategies", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

Step 5 gives us a useful observation point for all three strategies. The /spring-boot/web-runtime/request-metadata endpoint does not decide how forwarded headers are handled; it only reports the metadata visible on the request after the selected strategy has run. That lets us keep the endpoint fixed while changing the strategy and asking which layer owns the transformation. In the Spring Framework section, we will send a concrete proxy-style request and inspect the result.

**Purpose:**

Use the Step 5 runtime endpoint to verify "NONE, NATIVE, and FRAMEWORK Strategies" against the live context/server rather than treating the diagram as proof.

## When Native Server Forwarded-header Support Is Enough

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:57–02:09`

**Visual:**

Keep the client → trusted proxy → Boot request-metadata path on screen. Fade the completed "NONE, NATIVE, and FRAMEWORK Strategies" annotation and animate focus to "When Native Server Forwarded-header Support Is Enough"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

With "NONE, NATIVE, and FRAMEWORK Strategies" understood, test the next boundary: "When Native Server Forwarded-header Support Is Enough". Keep the prior result visible so the new mechanism follows from it.

**Purpose:**

Move from "NONE, NATIVE, and FRAMEWORK Strategies" to "When Native Server Forwarded-header Support Is Enough" without resetting the model, and make the changed input, owner, or output explicit.

### Scene 4 — When Native Server Forwarded-header Support Is Enough

**Time:** `02:09–02:39`

**Visual:**

Place property sources on the left, ServerProperties in the center, and the server factory on the right; contrast portable server.* controls with one server-specific namespace. For "When Native Server Forwarded-header Support Is Enough", place `NATIVE`, `X-Forwarded-For`, `X-Forwarded-Proto` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Boot's 3.3 guidance says `NATIVE` is sufficient when the proxy supplies commonly used`X-Forwarded-For` and`X-Forwarded-Proto` headers and the selected web server's native support matches the deployment needs. Native behavior is server-specific. Header names, trusted-proxy rules, remote-address rewriting, and other details can differ, so use the selected server's documentation when the common Boot strategy is correct but the exact native result needs tuning.

**Purpose:**

Turn "When Native Server Forwarded-header Support Is Enough" into a concrete choice by showing what changes at runtime and which supported configuration or customization layer should be used next.

## When to Use Spring Framework Forwarded-header Handling

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:39–02:52`

**Visual:**

Keep the client → trusted proxy → Boot request-metadata path on screen. Fade the completed "When Native Server Forwarded-header Support Is Enough" annotation and animate focus to "When to Use Spring Framework Forwarded-header Handling"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

We have established "When Native Server Forwarded-header Support Is Enough". Keep that result visible; now move to "When to Use Spring Framework Forwarded-header Handling" and change only the next runtime decision.

**Purpose:**

Connect "When Native Server Forwarded-header Support Is Enough" to "When to Use Spring Framework Forwarded-header Handling" so the next web-runtime decision follows from an established result.

### Scene 5 — When to Use Spring Framework Forwarded-header Handling

**Time:** `02:52–03:19`

**Visual:**

Draw client → reverse proxy → Boot app; compare host/scheme/remote address before and after the proxy, then place NONE/NATIVE/FRAMEWORK at the node that actually processes headers. For "When to Use Spring Framework Forwarded-header Handling", place `FRAMEWORK`, `ForwardedHeaderFilter`, `ForwardedHeaderTransformer` at the exact node or connection it affects and fade unrelated paths.

**Script:**

When native server handling is insufficient, `FRAMEWORK` activates Spring Framework's forwarded-header facilities:`ForwardedHeaderFilter` for Servlet applications and`ForwardedHeaderTransformer` for reactive applications. The Boot-owned decision is selecting this strategy. The detailed filtering/transformation behavior belongs to Spring Framework. This separation matters because changing the strategy can alter application-visible request metadata without changing the actual socket used by the embedded server.

**Purpose:**

Show the concrete runtime consequence of "When to Use Spring Framework Forwarded-header Handling" and give the viewer an observable checkpoint for the Boot-owned decision.

### Scene 6 — Runtime evidence

**Time:** `03:19–03:50`

**Visual:**

Start with --server.forward-headers-strategy=framework. Call GET /spring-boot/web-runtime/request-metadata with X-Forwarded-Proto=https, X-Forwarded-Host=example.test, X-Forwarded-Port=443, and X-Forwarded-For=203.0.113.10; place request and response side by side. For "When to Use Spring Framework Forwarded-header Handling", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

Now use the Step 5 experiment to observe the effect. With the FRAMEWORK strategy, /spring-boot/web-runtime/request-metadata reads the request after Spring Framework has applied forwarded-header handling. The scheme, secure flag, host, port, remote address, and URL visible to application code reflect the forwarded metadata even though the local socket still terminates at the embedded server. This is also why forwarding should be enabled only behind a trusted proxy path.

**Purpose:**

Use the Step 5 runtime endpoint to verify "When to Use Spring Framework Forwarded-header Handling" against the live context/server rather than treating the diagram as proof.

## Boot Defaults on Supported Cloud Platforms

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:04`

**Visual:**

Keep the client → trusted proxy → Boot request-metadata path on screen. Fade the completed "When to Use Spring Framework Forwarded-header Handling" annotation and animate focus to "Boot Defaults on Supported Cloud Platforms"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

The previous section gives us evidence for "When to Use Spring Framework Forwarded-header Handling". Follow the same runtime path into "Boot Defaults on Supported Cloud Platforms" and watch which input, owner, or output changes.

**Purpose:**

Preserve the evidence from "When to Use Spring Framework Forwarded-header Handling" while introducing only the new mechanism required for "Boot Defaults on Supported Cloud Platforms".

### Scene 7 — Boot Defaults on Supported Cloud Platforms

**Time:** `04:04–04:30`

**Visual:**

Place property sources on the left, ServerProperties in the center, and the server factory on the right; contrast portable server.* controls with one server-specific namespace. For "Boot Defaults on Supported Cloud Platforms", place `server.forward-headers-strategy`, `NATIVE`, `NONE` at the exact node or connection it affects and fade unrelated paths.

**Script:**

In Boot 3.3, `server.forward-headers-strategy` defaults to`NATIVE` when the application runs on a supported cloud platform. In other environments it defaults to`NONE`. Do not copy the cloud assumption blindly into another deployment. The safe configuration depends on whether all direct traffic reaches the application through a trusted proxy that sanitizes and supplies the expected forwarded headers.

**Purpose:**

Explain "Boot Defaults on Supported Cloud Platforms" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## TLS Termination, Redirects, and External Scheme Awareness

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:30–04:44`

**Visual:**

Keep the client → trusted proxy → Boot request-metadata path on screen. Fade the completed "Boot Defaults on Supported Cloud Platforms" annotation and animate focus to "TLS Termination, Redirects, and External Scheme Awareness"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "Boot Defaults on Supported Cloud Platforms" at this layer. The next dependency is "TLS Termination, Redirects, and External Scheme Awareness"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "TLS Termination, Redirects, and External Scheme Awareness" is the next dependency after "Boot Defaults on Supported Cloud Platforms", keeping ownership and runtime state continuous across the cut.

### Scene 8 — TLS Termination, Redirects, and External Scheme Awareness

**Time:** `04:44–05:19`

**Visual:**

Use a TLS handshake diagram with certificate material on the left, server.ssl or a named SslBundle in the center, and the HTTPS connector on the right; for SNI, let the hostname choose the certificate. For "TLS Termination, Redirects, and External Scheme Awareness", place `https`, `server.tomcat.redirect-context-root=false`, `X-Forwarded-Proto` at the exact node or connection it affects and fade unrelated paths.

**Script:**

TLS is often terminated at the proxy, leaving an HTTP hop between the proxy and the application. The server's local connection then appears insecure even though the client-facing request was HTTPS. Correct forwarded-protocol handling lets application-visible request metadata retain the external `https` scheme. This matters for redirects and generated absolute URLs. With Tomcat, Boot specifically documents`server.tomcat.redirect-context-root=false` when SSL terminates at the proxy so`X-Forwarded-Proto` can be honored before a context-root redirect is produced.

**Purpose:**

Tie the input in "TLS Termination, Redirects, and External Scheme Awareness" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.

## Server-specific Proxy and Remote-IP Settings

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:19–05:31`

**Visual:**

Keep the client → trusted proxy → Boot request-metadata path on screen. Fade the completed "TLS Termination, Redirects, and External Scheme Awareness" annotation and animate focus to "Server-specific Proxy and Remote-IP Settings"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

With "TLS Termination, Redirects, and External Scheme Awareness" understood, test the next boundary: "Server-specific Proxy and Remote-IP Settings". Keep the prior result visible so the new mechanism follows from it.

**Purpose:**

Move from "TLS Termination, Redirects, and External Scheme Awareness" to "Server-specific Proxy and Remote-IP Settings" without resetting the model, and make the changed input, owner, or output explicit.

### Scene 9 — Server-specific Proxy and Remote-IP Settings

**Time:** `05:31–05:55`

**Visual:**

Draw client → reverse proxy → Boot app; compare host/scheme/remote address before and after the proxy, then place NONE/NATIVE/FRAMEWORK at the node that actually processes headers. For "Server-specific Proxy and Remote-IP Settings", place `server.tomcat.remoteip.*`, `server.*` at the exact node or connection it affects and fade unrelated paths.

**Script:**

When the common strategy is not enough, server-specific namespaces expose deeper controls. Tomcat, for example, allows custom forwarded header names and trusted internal-proxy patterns under `server.tomcat.remoteip.*`. Use these settings only when the deployment contract requires them. They couple configuration to one server and should not be taught as portable `server.*` behavior.

**Purpose:**

Turn "Server-specific Proxy and Remote-IP Settings" into a concrete choice by showing what changes at runtime and which supported configuration or customization layer should be used next.

## Forwarded Headers and the Proxy Trust Boundary

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:55–06:07`

**Visual:**

Keep the client → trusted proxy → Boot request-metadata path on screen. Fade the completed "Server-specific Proxy and Remote-IP Settings" annotation and animate focus to "Forwarded Headers and the Proxy Trust Boundary"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

We have established "Server-specific Proxy and Remote-IP Settings". Keep that result visible; now move to "Forwarded Headers and the Proxy Trust Boundary" and change only the next runtime decision.

**Purpose:**

Connect "Server-specific Proxy and Remote-IP Settings" to "Forwarded Headers and the Proxy Trust Boundary" so the next web-runtime decision follows from an established result.

### Scene 10 — Forwarded Headers and the Proxy Trust Boundary

**Time:** `06:07–06:47`

**Visual:**

Draw client → reverse proxy → Boot app; compare host/scheme/remote address before and after the proxy, then place NONE/NATIVE/FRAMEWORK at the node that actually processes headers. For "Forwarded Headers and the Proxy Trust Boundary", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

Forwarded headers are trustworthy only when a trusted proxy controls them and direct untrusted clients cannot bypass that proxy path. Otherwise a client can send its own forwarded metadata and make the application believe a false scheme, host, or remote address. Boot documentation therefore recommends enabling forwarded-header support only when traffic comes from a trusted HTTP proxy or trusted network. The full proxy trust model belongs to infrastructure/security learning, but the Boot configuration must preserve that boundary rather than treating incoming forwarding headers as inherently authoritative.

**Purpose:**

Show the concrete runtime consequence of "Forwarded Headers and the Proxy Trust Boundary" and give the viewer an observable checkpoint for the Boot-owned decision.
