---
video:
  url: ""
---

# Web application type detection

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

## NONE, SERVLET, and REACTIVE

<!-- VIDEO_SECTION -->

### Scene 1 — NONE, SERVLET, and REACTIVE

**Time:** `00:00–00:29`

**Visual:**

Use a NONE / SERVLET / REACTIVE table; feed classpath signals through the decision flow, then connect the selected type to the ApplicationContext and server-start outcome. For "NONE, SERVLET, and REACTIVE", place `WebApplicationType`, `NONE`, `SERVLET` at the exact node or connection it affects and fade unrelated paths.

**Script:**

`WebApplicationType` has three values:`NONE`,`SERVLET`, and`REACTIVE`.`NONE` means Boot should create a non-web application context and not start an embedded web server.`SERVLET` selects the Servlet web runtime.`REACTIVE` selects the reactive web runtime. Treat this value as an early bootstrap decision. It influences which kind of`ApplicationContext` Spring Boot creates and which conditional web-server auto-configurations are eligible later in startup.

**Purpose:**

Explain "NONE, SERVLET, and REACTIVE" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.

## Classpath-based WebApplicationType Deduction

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:29–00:41`

**Visual:**

Keep the NONE/SERVLET/REACTIVE decision board on screen. Fade the completed "NONE, SERVLET, and REACTIVE" annotation and animate focus to "Classpath-based WebApplicationType Deduction"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

That settles "NONE, SERVLET, and REACTIVE" at this layer. The next dependency is "Classpath-based WebApplicationType Deduction"; move the highlight there while preserving the server or context state already proved.

**Purpose:**

Show why "Classpath-based WebApplicationType Deduction" is the next dependency after "NONE, SERVLET, and REACTIVE", keeping ownership and runtime state continuous across the cut.

### Scene 2 — Classpath-based WebApplicationType Deduction

**Time:** `00:41–01:20`

**Visual:**

Use a NONE / SERVLET / REACTIVE table; feed classpath signals through the decision flow, then connect the selected type to the ApplicationContext and server-start outcome. For "Classpath-based WebApplicationType Deduction", place `SpringApplication`, `REACTIVE`, `SERVLET` at the exact node or connection it affects and fade unrelated paths.

**Script:**

If the type is not explicitly configured, `SpringApplication` deduces it from the classpath. Conceptually, a reactive-only web classpath yields`REACTIVE`, a Servlet-capable web classpath yields`SERVLET`, and a classpath without the required web indicators yields`NONE`. This is why adding or removing starters can alter runtime behavior even when`main` does not change. The dependency graph is one of Boot's decision inputs; the resulting type is then consumed by conditional auto-configuration and context creation. The exact detection code is intentionally an implementation detail.

**Purpose:**

Tie the input in "Classpath-based WebApplicationType Deduction" to the factory, server, context, or request state it changes so diagnosis starts in the correct layer.

## When Servlet and Reactive Signals Coexist

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:20–01:31`

**Visual:**

Keep the NONE/SERVLET/REACTIVE decision board on screen. Fade the completed "Classpath-based WebApplicationType Deduction" annotation and animate focus to "When Servlet and Reactive Signals Coexist"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

With "Classpath-based WebApplicationType Deduction" understood, test the next boundary: "When Servlet and Reactive Signals Coexist". Keep the prior result visible so the new mechanism follows from it.

**Purpose:**

Move from "Classpath-based WebApplicationType Deduction" to "When Servlet and Reactive Signals Coexist" without resetting the model, and make the changed input, owner, or output explicit.

### Scene 3 — When Servlet and Reactive Signals Coexist

**Time:** `01:31–02:04`

**Visual:**

Use a NONE / SERVLET / REACTIVE table; feed classpath signals through the decision flow, then connect the selected type to the ApplicationContext and server-start outcome. For "When Servlet and Reactive Signals Coexist", place `WebClient`, `REACTIVE` at the exact node or connection it affects and fade unrelated paths.

**Script:**

When both Spring MVC and Spring WebFlux are available, Spring Boot chooses the Servlet/MVC application model by default. This is deliberate because applications often add WebFlux only to use `WebClient` while remaining MVC applications. Therefore, seeing reactive libraries on the classpath does not by itself prove that the application is running as`REACTIVE`. If both web stacks are present and the application is intended to run as WebFlux, make that choice explicit.

**Purpose:**

Turn "When Servlet and Reactive Signals Coexist" into a concrete choice by showing what changes at runtime and which supported configuration or customization layer should be used next.

## Explicit Override with spring.main.web-application-type

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:04–02:15`

**Visual:**

Keep the NONE/SERVLET/REACTIVE decision board on screen. Fade the completed "When Servlet and Reactive Signals Coexist" annotation and animate focus to "Explicit Override with spring.main.web-application-type"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

We have established "When Servlet and Reactive Signals Coexist". Keep that result visible; now move to "Explicit Override with spring.main.web-application-type" and change only the next runtime decision.

**Purpose:**

Connect "When Servlet and Reactive Signals Coexist" to "Explicit Override with spring.main.web-application-type" so the next web-runtime decision follows from an established result.

### Scene 4 — Explicit Override with spring.main.web-application-type

**Time:** `02:15–02:41`

**Visual:**

Keep the chapter runtime diagram but show only the section input, Boot-owned decision, and server/context output; fade every unrelated node. For "Explicit Override with spring.main.web-application-type", place `spring.main.web-application-type`, `servlet`, `reactive` at the exact node or connection it affects and fade unrelated paths.

**Script:**

The property `spring.main.web-application-type` can force the bootstrap decision. Common values are`servlet`,`reactive`, and`none`. spring.main.web-application-type=reactive Use an override when the classpath is intentionally mixed, or when a web-capable classpath must run without a server.`none` is especially useful for command-line or batch-style modes that reuse application dependencies but should not expose an HTTP endpoint.

**Purpose:**

Show the concrete runtime consequence of "Explicit Override with spring.main.web-application-type" and give the viewer an observable checkpoint for the Boot-owned decision.

## How Application Type Changes Context and Server Startup

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:41–02:55`

**Visual:**

Keep the NONE/SERVLET/REACTIVE decision board on screen. Fade the completed "Explicit Override with spring.main.web-application-type" annotation and animate focus to "How Application Type Changes Context and Server Startup"; reveal only the new property, class, server element, or trust boundary needed by the next scene.

**Script:**

The previous section gives us evidence for "Explicit Override with spring.main.web-application-type". Follow the same runtime path into "How Application Type Changes Context and Server Startup" and watch which input, owner, or output changes.

**Purpose:**

Preserve the evidence from "Explicit Override with spring.main.web-application-type" while introducing only the new mechanism required for "How Application Type Changes Context and Server Startup".

### Scene 5 — How Application Type Changes Context and Server Startup

**Time:** `02:55–03:29`

**Visual:**

Use a NONE / SERVLET / REACTIVE table; feed classpath signals through the decision flow, then connect the selected type to the ApplicationContext and server-start outcome. For "How Application Type Changes Context and Server Startup", place `SERVLET`, `ServletWebServerFactory`, `REACTIVE` at the exact node or connection it affects and fade unrelated paths.

**Script:**

The selected type changes context construction before the server itself is created. `SERVLET` leads Boot toward a Servlet web application context and a`ServletWebServerFactory`;`REACTIVE` leads toward a reactive web application context and a`ReactiveWebServerFactory`;`NONE` uses a non-web context and does not follow embedded web-server startup. This also explains why changing only a server dependency is different from changing`WebApplicationType`. Replacing Tomcat with Jetty changes the implementation inside the same Servlet runtime model.

**Purpose:**

Explain "How Application Type Changes Context and Server Startup" at the layer Boot owns while making the handoff to framework, server internals, or infrastructure explicit.
