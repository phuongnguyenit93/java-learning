---
video:
  url: ""
---

# TLS, SSL bundles, and web-server certificates

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

## Boot Entry Points for Web-server TLS

<!-- VIDEO_SECTION -->

### Scene 1 — Boot Entry Points for Web-server TLS

**Time:** `00:00–00:36`

**Visual:**

Use a TLS handshake diagram with certificate material on the left, server.ssl or a named SslBundle in the center, and the HTTPS connector on the right; for SNI, let the hostname choose the certificate. For "Boot Entry Points for Web-server TLS", place `server.ssl.*`, `spring.ssl.bundle.*`, `server.ssl.bundle` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Boot can secure the embedded server in two main declarative ways. The first configures certificate/key material directly under `server.ssl.*`. The second defines reusable named material under `spring.ssl.bundle.*` and points the server at one bundle with `server.ssl.bundle`. Both paths configure the Boot-managed server; they do not change application routes. The choice is mainly about configuration ownership and reuse. A named bundle is useful when the same TLS material or options should be consumed consistently by multiple Boot-supported connections.

**Purpose:**

Explain "Boot Entry Points for Web-server TLS" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Keystore and PEM Configuration with server.ssl.*

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:36–00:50`

**Visual:**

Keep the certificate/bundle → HTTPS connector diagram on screen. Fade the completed "Boot Entry Points for Web-server TLS" annotation and animate focus to "Keystore and PEM Configuration with server.ssl.*"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "Boot Entry Points for Web-server TLS" at this layer. The next dependency is "Keystore and PEM Configuration with server.ssl.*"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Keystore and PEM Configuration with server.ssl.*" is the next dependency after "Boot Entry Points for Web-server TLS", keeping ownership and runtime state continuous across the cut.

### Scene 2 — Keystore and PEM Configuration with server.ssl.*

**Time:** `00:50–01:22`

**Visual:**

Use a TLS handshake diagram with certificate material on the left, server.ssl or a named SslBundle in the center, and the HTTPS connector on the right; for SNI, let the hostname choose the certificate. For "Keystore and PEM Configuration with server.ssl.*", place `server.ssl.*` at the exact node or connection it affects and fade unrelated paths.

**Script:**

For direct server configuration, `server.ssl.*` supports Java KeyStore material and PEM-encoded certificate/key material. A typical keystore setup provides a keystore location and passwords; a PEM setup provides the certificate and private-key locations. Enabling server SSL changes the main connector to HTTPS. Boot does not provide a pair of ordinary properties that simultaneously configure one HTTP connector and one HTTPS connector; add an extra connector programmatically when that topology is required.

**Purpose:**

Tie the input in "Keystore and PEM Configuration with server.ssl.*" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.

## Applying a Named SSL Bundle to the Web Server

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:22–01:36`

**Visual:**

Keep the certificate/bundle → HTTPS connector diagram on screen. Fade the completed "Keystore and PEM Configuration with server.ssl.*" annotation and animate focus to "Applying a Named SSL Bundle to the Web Server"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

With "Keystore and PEM Configuration with server.ssl.*" understood, test the next boundary: "Applying a Named SSL Bundle to the Web Server". Keep the prior result visible so the new mechanism follows from it.

**Purpose:**

Move from "Keystore and PEM Configuration with server.ssl.*" to "Applying a Named SSL Bundle to the Web Server" without resetting the model, and make the changed input, owner, or output explicit.

### Scene 3 — Applying a Named SSL Bundle to the Web Server

**Time:** `01:36–02:11`

**Visual:**

Use a TLS handshake diagram with certificate material on the left, server.ssl or a named SslBundle in the center, and the HTTPS connector on the right; for SNI, let the hostname choose the certificate. For "Applying a Named SSL Bundle to the Web Server", place `spring.ssl.bundle.jks.`, `spring.ssl.bundle.pem.`, `SslBundle` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Define a named SSL bundle under `spring.ssl.bundle.jks.<name>` or `spring.ssl.bundle.pem.<name>`, then point the embedded server at that name with `server.ssl.bundle`. A PEM bundle can reference the certificate and private-key resources once, and the server consumes the bundle by name instead of repeating key material in `server.ssl.*`. The generic `SslBundle` and `SslBundles` abstractions belong to the broader application runtime; this chapter owns the web-specific consumption point that attaches the selected bundle to the embedded HTTPS server.

**Purpose:**

Turn "Applying a Named SSL Bundle to the Web Server" into a concrete choice by showing what changes at runtime and which supported configuration or customization layer should be used next.

## SSL Bundle Options versus Discrete server.ssl Properties

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:11–02:25`

**Visual:**

Keep the certificate/bundle → HTTPS connector diagram on screen. Fade the completed "Applying a Named SSL Bundle to the Web Server" annotation and animate focus to "SSL Bundle Options versus Discrete server.ssl Properties"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

We have established "Applying a Named SSL Bundle to the Web Server". Keep that result visible; now move to "SSL Bundle Options versus Discrete server.ssl Properties" and change only the next runtime decision.

**Purpose:**

Connect "Applying a Named SSL Bundle to the Web Server" to "SSL Bundle Options versus Discrete server.ssl Properties" so the next web-runtime decision follows from an established result.

### Scene 4 — SSL Bundle Options versus Discrete server.ssl Properties

**Time:** `02:25–02:53`

**Visual:**

Use a TLS handshake diagram with certificate material on the left, server.ssl or a named SslBundle in the center, and the HTTPS connector on the right; for SNI, let the hostname choose the certificate. For "SSL Bundle Options versus Discrete server.ssl Properties", place `server.ssl.bundle`, `server.ssl`, `server.ssl.ciphers` at the exact node or connection it affects and fade unrelated paths.

**Script:**

`server.ssl.bundle` cannot be combined with the discrete Java KeyStore or PEM material options under`server.ssl`. When a bundle is used, properties such as`server.ssl.ciphers`,`server.ssl.enabled-protocols`, and`server.ssl.protocol` are ignored. Put those protocol and cipher options into the named bundle's`spring.ssl.bundle. . .options` configuration instead. This keeps the bundle self-contained and prevents two configuration models from competing for the same TLS state.

**Purpose:**

Show the concrete runtime consequence of "SSL Bundle Options versus Discrete server.ssl Properties" and give the viewer an observable checkpoint for the Boot-owned decision.

## Server Name Indication and Certificate Selection

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:53–03:07`

**Visual:**

Keep the certificate/bundle → HTTPS connector diagram on screen. Fade the completed "SSL Bundle Options versus Discrete server.ssl Properties" annotation and animate focus to "Server Name Indication and Certificate Selection"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

The previous section gives us evidence for "SSL Bundle Options versus Discrete server.ssl Properties". Follow the same runtime path into "Server Name Indication and Certificate Selection" and watch which input, owner, or output changes.

**Purpose:**

Preserve the evidence from "SSL Bundle Options versus Discrete server.ssl Properties" while introducing only the new mechanism required for "Server Name Indication and Certificate Selection".

### Scene 5 — Server Name Indication and Certificate Selection

**Time:** `03:07–03:41`

**Visual:**

Use a TLS handshake diagram with certificate material on the left, server.ssl or a named SslBundle in the center, and the HTTPS connector on the right; for SNI, let the hostname choose the certificate. For "Server Name Indication and Certificate Selection", place `server.ssl.server-name-bundles`, `server.ssl.bundle` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Boot 3.3 can map host names to additional SSL bundles through `server.ssl.server-name-bundles`. The bundle configured by`server.ssl.bundle` remains the default certificate material, while named host entries select alternatives for SNI-capable clients. Tomcat, Netty, and Undertow have Boot-managed SNI configuration. Jetty is different: Boot's explicit SNI mapping is not supported there, although Jetty can automatically configure SNI when multiple certificates are supplied. Treat this as a server capability difference, not a generic TLS rule.

**Purpose:**

Explain "Server Name Indication and Certificate Selection" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Web-server TLS Integration versus Generic TLS and PKI

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:41–03:56`

**Visual:**

Keep the certificate/bundle → HTTPS connector diagram on screen. Fade the completed "Server Name Indication and Certificate Selection" annotation and animate focus to "Web-server TLS Integration versus Generic TLS and PKI"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "Server Name Indication and Certificate Selection" at this layer. The next dependency is "Web-server TLS Integration versus Generic TLS and PKI"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Web-server TLS Integration versus Generic TLS and PKI" is the next dependency after "Server Name Indication and Certificate Selection", keeping ownership and runtime state continuous across the cut.

### Scene 6 — Web-server TLS Integration versus Generic TLS and PKI

**Time:** `03:56–04:30`

**Visual:**

Use a TLS handshake diagram with certificate material on the left, server.ssl or a named SslBundle in the center, and the HTTPS connector on the right; for SNI, let the hostname choose the certificate. For "Web-server TLS Integration versus Generic TLS and PKI", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

This module should teach where Boot receives certificate material, how it attaches that material to the embedded server, how named bundles are selected, and where server-specific limitations appear. It should also teach deployment interactions such as TLS termination at a reverse proxy. Certificate issuance, CA trust models, handshake cryptography, cipher design, certificate-chain validation, ACME protocol behavior, and PKI operations belong to security/network owners. Boot consumes those concepts through configuration; it does not redefine them.

**Purpose:**

Tie the input in "Web-server TLS Integration versus Generic TLS and PKI" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.
