---
video:
  url: ""
---

# Programmatic server customization

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

## Properties First, Customizer Second, Factory Bean Last

<!-- VIDEO_SECTION -->

### Scene 1 — Properties First, Customizer Second, Factory Bean Last

**Time:** `00:00–00:35`

**Visual:**

Draw the dependency/factory pipeline: starter or user bean → ordered customizers → WebServerFactory → embedded server; fade the auto-configured factory when back-off occurs. For "Properties First, Customizer Second, Factory Bean Last", place `server.*`, `server.tomcat.*`, `server.jetty.*` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Use the least invasive customization that expresses the requirement. Start with portable `server.*` properties, then move to a server-specific namespace only when the requirement depends on that implementation. If properties are still not enough, use a `WebServerFactoryCustomizer`. Supplying the factory bean itself is the strongest intervention because the application now owns factory construction. Each step down that ladder increases coupling to server APIs, so the requirement—not familiarity with an API—should justify the escalation.

**Purpose:**

Explain "Properties First, Customizer Second, Factory Bean Last" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## WebServerFactoryCustomizer as the Main Programmatic Hook

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:35–00:49`

**Visual:**

Keep the property/customizer/factory escalation ladder on screen. Fade the completed "Properties First, Customizer Second, Factory Bean Last" annotation and animate focus to "WebServerFactoryCustomizer as the Main Programmatic Hook"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "Properties First, Customizer Second, Factory Bean Last" at this layer. The next dependency is "WebServerFactoryCustomizer as the Main Programmatic Hook"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "WebServerFactoryCustomizer as the Main Programmatic Hook" is the next dependency after "Properties First, Customizer Second, Factory Bean Last", keeping ownership and runtime state continuous across the cut.

### Scene 2 — WebServerFactoryCustomizer as the Main Programmatic Hook

**Time:** `00:49–01:24`

**Visual:**

Draw the dependency/factory pipeline: starter or user bean → ordered customizers → WebServerFactory → embedded server; fade the auto-configured factory when back-off occurs. For "WebServerFactoryCustomizer as the Main Programmatic Hook", place `WebServerFactoryCustomizer` at the exact node or connection it affects and fade unrelated paths.

**Script:**

`WebServerFactoryCustomizer` lets application code modify Boot's selected factory before the server exists. Its generic type narrows the hook to compatible factories, so a Tomcat-specific customizer is not applied to Jetty. When a property cannot express the requirement, the callback can use a server-specific factory API while Boot still owns creating and starting the resulting web server. Keep the Java example on screen, but narrate this lifecycle contract rather than reading the method line by line.

**Purpose:**

Tie the input in "WebServerFactoryCustomizer as the Main Programmatic Hook" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.

## Generic versus Server-specific Factory Targets

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:24–01:36`

**Visual:**

Keep the property/customizer/factory escalation ladder on screen. Fade the completed "WebServerFactoryCustomizer as the Main Programmatic Hook" annotation and animate focus to "Generic versus Server-specific Factory Targets"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

With "WebServerFactoryCustomizer as the Main Programmatic Hook" understood, test the next boundary: "Generic versus Server-specific Factory Targets". Keep the prior result visible so the new mechanism follows from it.

**Purpose:**

Move from "WebServerFactoryCustomizer as the Main Programmatic Hook" to "Generic versus Server-specific Factory Targets" without resetting the model, and make the changed input, owner, or output explicit.

### Scene 3 — Generic versus Server-specific Factory Targets

**Time:** `01:36–02:10`

**Visual:**

Draw the dependency/factory pipeline: starter or user bean → ordered customizers → WebServerFactory → embedded server; fade the auto-configured factory when back-off occurs. For "Generic versus Server-specific Factory Targets", place `ConfigurableWebServerFactory`, `TomcatServletWebServerFactory`, `NettyReactiveWebServerFactory` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Target the broadest factory type that still exposes the capability you need. A customizer for `ConfigurableWebServerFactory` can express portable concerns available on that abstraction. A customizer for`TomcatServletWebServerFactory` or`NettyReactiveWebServerFactory` intentionally opts into a specific server implementation and web stack. This distinction is useful during maintenance. A generic customizer can often survive a server swap; a server-specific customizer becomes part of the migration checklist because its API and behavior are tied to that implementation.

**Purpose:**

Turn "Generic versus Server-specific Factory Targets" into a concrete choice by showing what changes at runtime and which supported configuration or customization layer should be used next.

## How Boot and User Customizers Are Ordered

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:22`

**Visual:**

Keep the property/customizer/factory escalation ladder on screen. Fade the completed "Generic versus Server-specific Factory Targets" annotation and animate focus to "How Boot and User Customizers Are Ordered"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

We have established "Generic versus Server-specific Factory Targets". Keep that result visible; now move to "How Boot and User Customizers Are Ordered" and change only the next runtime decision.

**Purpose:**

Connect "Generic versus Server-specific Factory Targets" to "How Boot and User Customizers Are Ordered" so the next web-runtime decision follows from an established result.

### Scene 4 — How Boot and User Customizers Are Ordered

**Time:** `02:22–02:54`

**Visual:**

Draw the dependency/factory pipeline: starter or user bean → ordered customizers → WebServerFactory → embedded server; fade the auto-configured factory when back-off occurs. For "How Boot and User Customizers Are Ordered", place `WebServerFactoryCustomizer`, `0`, `Ordered` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Multiple customizers can target the same factory. Spring ordering rules determine the sequence, and Boot's auto-configured `WebServerFactoryCustomizer` beans use order`0`. A user customizer can implement`Ordered` or use`@Order` when it must run before or after another customization. Avoid relying on accidental bean discovery order. When two customizers touch the same field, make the ordering intent explicit or consolidate the responsibility so the final factory state is predictable.

**Purpose:**

Show the concrete runtime consequence of "How Boot and User Customizers Are Ordered" and give the viewer an observable checkpoint for the Boot-owned decision.

## Supplying a Custom WebServerFactory Bean

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:54–03:08`

**Visual:**

Keep the property/customizer/factory escalation ladder on screen. Fade the completed "How Boot and User Customizers Are Ordered" annotation and animate focus to "Supplying a Custom WebServerFactory Bean"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

The previous section gives us evidence for "How Boot and User Customizers Are Ordered". Follow the same runtime path into "Supplying a Custom WebServerFactory Bean" and watch which input, owner, or output changes.

**Purpose:**

Preserve the evidence from "How Boot and User Customizers Are Ordered" while introducing only the new mechanism required for "Supplying a Custom WebServerFactory Bean".

### Scene 5 — Supplying a Custom WebServerFactory Bean

**Time:** `03:08–03:40`

**Visual:**

Draw the dependency/factory pipeline: starter or user bean → ordered customizers → WebServerFactory → embedded server; fade the auto-configured factory when back-off occurs. For "Supplying a Custom WebServerFactory Bean", place `ServletWebServerFactory`, `ReactiveWebServerFactory`, `WebServerFactoryCustomizer` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Declaring your own `ServletWebServerFactory` or`ReactiveWebServerFactory` bean causes Boot's implementation-specific factory auto-configuration to back off. The application now chooses how that factory is constructed. This does not bypass the entire Boot customization pipeline. Auto-configured`WebServerFactoryCustomizer` beans are still applied to the custom factory. Treat a custom factory as the last resort when factory construction itself must change, and verify how Boot customizers interact with the initial state you supplied.

**Purpose:**

Explain "Supplying a Custom WebServerFactory Bean" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Extra Connectors, Listeners, and Other Advanced Server Topology

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:40–03:54`

**Visual:**

Keep the property/customizer/factory escalation ladder on screen. Fade the completed "Supplying a Custom WebServerFactory Bean" annotation and animate focus to "Extra Connectors, Listeners, and Other Advanced Server Topology"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "Supplying a Custom WebServerFactory Bean" at this layer. The next dependency is "Extra Connectors, Listeners, and Other Advanced Server Topology"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Extra Connectors, Listeners, and Other Advanced Server Topology" is the next dependency after "Supplying a Custom WebServerFactory Bean", keeping ownership and runtime state continuous across the cut.

### Scene 6 — Extra Connectors, Listeners, and Other Advanced Server Topology

**Time:** `03:54–04:34`

**Visual:**

Use a decision ladder: property → WebServerFactoryCustomizer → server-specific customizer → custom factory bean → connector/listener; highlight only the current rung. For "Extra Connectors, Listeners, and Other Advanced Server Topology", place `server.ssl.*`, `application.properties` at the exact node or connection it affects and fade unrelated paths.

**Script:**

Some topologies require server-specific code. A common example is exposing HTTPS through `server.ssl.*` while adding a second plain HTTP connector programmatically. Spring Boot does not model simultaneous HTTP and HTTPS connectors as a pair of ordinary `application.properties` settings, so the extra connector belongs in a server-specific customizer. The same principle applies to custom listeners, connector resources, protocol handlers, or other server objects that are outside Boot's portable property model. Keep such code isolated around the factory instead of spreading container APIs through application business code.

**Purpose:**

Tie the input in "Extra Connectors, Listeners, and Other Advanced Server Topology" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.
