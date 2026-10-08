---
video:
  url: ""
---

# Graceful shutdown

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

## Immediate and Graceful Server Shutdown

<!-- VIDEO_SECTION -->

### Scene 1 — Immediate and Graceful Server Shutdown

**Time:** `00:00–00:33`

**Visual:**

Use a shutdown timeline: context close → stop accepting new requests → in-flight requests finish → phase timeout → server stops; keep readiness on a separate lane. For "Immediate and Graceful Server Shutdown", place `immediate`, `graceful` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Boot 3.3 supports two web-server shutdown modes: `immediate` and `graceful`, with `immediate` as the default. Set `server.shutdown` to `graceful` when planned termination should stop new work while giving in-flight requests a bounded chance to finish. The behavior is supported across Boot 3.3's four embedded server families and on both Servlet and reactive applications. This setting changes server shutdown behavior; it does not create a separate shutdown process outside the application context.

**Purpose:**

Explain "Immediate and Graceful Server Shutdown" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Where Graceful Shutdown Fits in ApplicationContext Closing

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:33–00:47`

**Visual:**

Keep the context-close/graceful-shutdown timeline on screen. Fade the completed "Immediate and Graceful Server Shutdown" annotation and animate focus to "Where Graceful Shutdown Fits in ApplicationContext Closing"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "Immediate and Graceful Server Shutdown" at this layer. The next dependency is "Where Graceful Shutdown Fits in ApplicationContext Closing"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Where Graceful Shutdown Fits in ApplicationContext Closing" is the next dependency after "Immediate and Graceful Server Shutdown", keeping ownership and runtime state continuous across the cut.

### Scene 2 — Where Graceful Shutdown Fits in ApplicationContext Closing

**Time:** `00:47–01:21`

**Visual:**

Use a lifecycle timeline: SpringApplication → web-context refresh → factory lookup/customization → server create/start; for shutdown, reverse the timeline and highlight the close phase. For "Where Graceful Shutdown Fits in ApplicationContext Closing", place `ApplicationContext`, `SmartLifecycle` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Graceful server shutdown happens as part of closing the `ApplicationContext`. Boot performs it in the earliest phase of stopping`SmartLifecycle` beans so the web entry point begins refusing new work while the rest of the application proceeds through its coordinated shutdown lifecycle. That ordering is why graceful shutdown is a web-runtime topic with an application-runtime handoff. The server participates in the same context closure rather than running an unrelated shutdown process outside Spring.

**Purpose:**

Tie the input in "Where Graceful Shutdown Fits in ApplicationContext Closing" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.

## Configuring the Shutdown Phase Timeout

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:21–01:33`

**Visual:**

Keep the context-close/graceful-shutdown timeline on screen. Fade the completed "Where Graceful Shutdown Fits in ApplicationContext Closing" annotation and animate focus to "Configuring the Shutdown Phase Timeout"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

With "Where Graceful Shutdown Fits in ApplicationContext Closing" understood, test the next boundary: "Configuring the Shutdown Phase Timeout". Keep the prior result visible so the new mechanism follows from it.

**Purpose:**

Move from "Where Graceful Shutdown Fits in ApplicationContext Closing" to "Configuring the Shutdown Phase Timeout" without resetting the model, and make the changed input, owner, or output explicit.

### Scene 3 — Configuring the Shutdown Phase Timeout

**Time:** `01:33–02:01`

**Visual:**

Use a shutdown timeline: context close → stop accepting new requests → in-flight requests finish → phase timeout → server stops; keep readiness on a separate lane. For "Configuring the Shutdown Phase Timeout", place `spring.lifecycle.timeout-per-shutdown-phase`, `SmartLifecycle` at the exact node or connection it affects and fade unrelated paths.

**Script:**

`spring.lifecycle.timeout-per-shutdown-phase` sets the time budget for a shutdown phase. With graceful web-server shutdown enabled, that phase budget is the window in which existing requests can finish before shutdown proceeds. A value such as twenty seconds is therefore an application-lifecycle budget, not an isolated HTTP timeout, and other `SmartLifecycle` participants in the same phase can be affected by the same setting.

**Purpose:**

Turn "Configuring the Shutdown Phase Timeout" into a concrete choice by showing what changes at runtime and which supported configuration or customization layer should be used next.

## How Supported Servers Stop Accepting New Work

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:01–02:13`

**Visual:**

Keep the context-close/graceful-shutdown timeline on screen. Fade the completed "Configuring the Shutdown Phase Timeout" annotation and animate focus to "How Supported Servers Stop Accepting New Work"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

We have established "Configuring the Shutdown Phase Timeout". Keep that result visible; now move to "How Supported Servers Stop Accepting New Work" and change only the next runtime decision.

**Purpose:**

Connect "Configuring the Shutdown Phase Timeout" to "How Supported Servers Stop Accepting New Work" so the next web-runtime decision follows from an established result.

### Scene 4 — How Supported Servers Stop Accepting New Work

**Time:** `02:13–02:49`

**Visual:**

Place property sources on the left, ServerProperties in the center, and the server factory on the right; contrast portable server.* controls with one server-specific namespace. For "How Supported Servers Stop Accepting New Work", place `503 Service Unavailable` at the exact node or connection it affects and fade unrelated paths.

**Script:**

The common Boot contract is "finish in-flight work while refusing new work", but the mechanism differs by server. In Boot 3.3, Jetty, Reactor Netty, and Tomcat stop accepting new requests at the network layer during graceful shutdown. Undertow behaves differently: it can continue accepting connections but responds to new requests with HTTP `503 Service Unavailable`. Persistent connections can also affect what a client observes, so do not use one server's wire behavior as the definition of graceful shutdown.

**Purpose:**

Show the concrete runtime consequence of "How Supported Servers Stop Accepting New Work" and give the viewer an observable checkpoint for the Boot-owned decision.

## The Grace Period for In-flight Requests

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:49–03:03`

**Visual:**

Keep the context-close/graceful-shutdown timeline on screen. Fade the completed "How Supported Servers Stop Accepting New Work" annotation and animate focus to "The Grace Period for In-flight Requests"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

The previous section gives us evidence for "How Supported Servers Stop Accepting New Work". Follow the same runtime path into "The Grace Period for In-flight Requests" and watch which input, owner, or output changes.

**Purpose:**

Preserve the evidence from "How Supported Servers Stop Accepting New Work" while introducing only the new mechanism required for "The Grace Period for In-flight Requests".

### Scene 5 — The Grace Period for In-flight Requests

**Time:** `03:03–03:35`

**Visual:**

Use a shutdown timeline: context close → stop accepting new requests → in-flight requests finish → phase timeout → server stops; keep readiness on a separate lane. For "The Grace Period for In-flight Requests", place the section input and observable runtime result at the exact node or connection it affects and fade unrelated paths.

**Script:**

Requests that were already being processed receive an opportunity to complete during the configured shutdown phase. Graceful shutdown therefore reduces avoidable failures during planned termination, rolling deployment, or instance replacement. It is still bounded shutdown, not an unlimited wait. Application work must respect the surrounding lifecycle budget, and external systems such as orchestrators or load balancers must provide enough termination time for the process to use that grace period.

**Purpose:**

Explain "The Grace Period for In-flight Requests" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Handoff to Application Runtime and Availability Concerns

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:35–03:49`

**Visual:**

Keep the context-close/graceful-shutdown timeline on screen. Fade the completed "The Grace Period for In-flight Requests" annotation and animate focus to "Handoff to Application Runtime and Availability Concerns"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "The Grace Period for In-flight Requests" at this layer. The next dependency is "Handoff to Application Runtime and Availability Concerns"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Handoff to Application Runtime and Availability Concerns" is the next dependency after "The Grace Period for In-flight Requests", keeping ownership and runtime state continuous across the cut.

### Scene 6 — Handoff to Application Runtime and Availability Concerns

**Time:** `03:49–04:26`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "Handoff to Application Runtime and Availability Concerns", place `SIGTERM` at the exact node or connection it affects and fade unrelated paths.

**Script:**

The web-runtime responsibility ends once the server's graceful-stop behavior and its lifecycle timing are clear. Application availability state, process signals, other lifecycle beans, background work, and orchestrator termination policy belong to the broader application-runtime and infrastructure owners. One practical boundary is the termination signal itself. Boot's documentation notes that IDE stop actions may be immediate when the IDE does not send an appropriate `SIGTERM`. Graceful shutdown can only participate when the process actually enters the normal context-closing path.

**Purpose:**

Tie the input in "Handoff to Application Runtime and Availability Concerns" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.
