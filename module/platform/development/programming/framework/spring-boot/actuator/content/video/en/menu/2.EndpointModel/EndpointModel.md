---
video:
  url: ""
---

# Actuator Endpoint Model

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

## What Is an Actuator Endpoint?

<!-- VIDEO_SECTION -->

### Scene 1 — What Is an Actuator Endpoint?

**Time:** `00:00–00:32`

**Visual:**

Show one card labeled `health` with two layers: endpoint ID + operations. Then map that card to `/actuator/health` on HTTP and an MBean view on JMX; keep the ID unchanged as transports change.

**Script:**

An Actuator endpoint is a management capability identified by an endpoint ID and implemented by one or more operations. The ID is the stable identity; the HTTP path or JMX representation is just one transport view of it. That is why `health` should first be understood as an endpoint model, not as the literal string `/actuator/health`.

**Purpose:**

Anchor endpoint identity above transport so later path, exposure, and custom-endpoint discussions have a stable mental model.

## How Do Endpoint IDs and Operations Structure the Model?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:32–00:45`

**Visual:**

Evolve the endpoint card in place: keep the stable ID visible, then reveal the next layer—operations, catalog/prerequisites, transport adapters, or configuration ownership—required by `How Do Endpoint IDs and Operations Structure the Model?`.

**Script:**

Once the endpoint has a stable identity, the next layer is behavior: which operations belong to that identity, and how does a transport represent them?

**Purpose:**

Move from stable endpoint identity to the operations that give that identity management behavior.

### Scene 1 — How Do Endpoint IDs and Operations Structure the Model?

**Time:** `00:45–01:14`

**Visual:**

Open a tiny `@Endpoint(id = "learning")` class and highlight `@ReadOperation`, `@WriteOperation`, and `@DeleteOperation`. Beside it, map read/write/delete intent to HTTP methods and to JMX operations without changing the Java endpoint model.

**Script:**

The endpoint ID groups the management capability; operation annotations describe what the capability can do. `@ReadOperation`, `@WriteOperation`, and `@DeleteOperation` express management intent without hard-coding a web controller contract. HTTP and JMX adapters translate those operations into their own request or invocation model. So the useful chain is ID → operations → exposure adapter.

**Purpose:**

Show how operation intent stays transport-neutral and why endpoint code should not start from MVC annotations.

## How Should You Reason About the Built-in Endpoint Catalog?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:14–01:26`

**Visual:**

Evolve the endpoint card in place: keep the stable ID visible, then reveal the next layer—operations, catalog/prerequisites, transport adapters, or configuration ownership—required by `How Should You Reason About the Built-in Endpoint Catalog?`.

**Script:**

With ID and operations clear, the built-in catalog becomes easier to reason about: each endpoint is just another management capability with its own prerequisites.

**Purpose:**

Use the ID/operation model to classify the built-in catalog by purpose and prerequisite rather than memorize names.

### Scene 1 — How Should You Reason About the Built-in Endpoint Catalog?

**Time:** `01:26–01:57`

**Visual:**

Group built-in endpoint cards by operational question: health, metadata/configuration, metrics, logging, framework structure, startup/HTTP exchanges, and binary diagnostics. Add prerequisite badges to `startup`, `httpexchanges`, `prometheus`, `logfile`, and `heapdump`.

**Script:**

Do not memorize the built-in endpoints as one flat catalog. Classify them by the question they answer, then check prerequisites and exposure rules. `startup` needs buffered startup data, `httpexchanges` needs an `HttpExchangeRepository`, `prometheus` depends on the Prometheus registry, and some diagnostics are web-specific. That way the catalog becomes a troubleshooting tool instead of a vocabulary exercise.

**Purpose:**

Teach a classification strategy and make prerequisites visible so a missing endpoint is not mistaken for a routing bug.

## Why Are Actuator Endpoints Technology-Agnostic by Default?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:57–02:11`

**Visual:**

Evolve the endpoint card in place: keep the stable ID visible, then reveal the next layer—operations, catalog/prerequisites, transport adapters, or configuration ownership—required by `Why Are Actuator Endpoints Technology-Agnostic by Default?`.

**Script:**

The catalog shows many capabilities sharing one model. The reason that reuse works is that the core endpoint abstraction does not belong to MVC, WebFlux, Jersey, or JMX.

**Purpose:**

Explain why one catalog can serve several transports by making technology independence the next logical property.

### Scene 1 — Why Are Actuator Endpoints Technology-Agnostic by Default?

**Time:** `02:11–02:45`

**Visual:**

Place the same `@Endpoint` in the center and fan it out to MVC, WebFlux, Jersey, and JMX adapters. Cross out direct imports from those technologies inside the endpoint class.

**Script:**

The generic `@Endpoint` model is deliberately technology-agnostic. Boot discovers the endpoint and its operations first; web or JMX infrastructure exposes them later when that technology is available and the endpoint is included. This is valuable for reusable management code because the capability can survive a web-stack change and can be exposed through more than one transport without being rewritten as a controller.

**Purpose:**

Explain why generic endpoints are reusable and why transport-specific code belongs only where a real transport requirement exists.

## Which Endpoint Settings Belong to Actuator and Which Belong to Externalized Configuration?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:45–02:57`

**Visual:**

Evolve the endpoint card in place: keep the stable ID visible, then reveal the next layer—operations, catalog/prerequisites, transport adapters, or configuration ownership—required by `Which Endpoint Settings Belong to Actuator and Which Belong to Externalized Configuration?`.

**Script:**

Technology independence gives configuration a stable target. The final step is to separate those Actuator settings from the machinery that resolves their values.

**Purpose:**

Finish the endpoint abstraction by separating Actuator setting semantics from the property-resolution machinery that supplies values.

### Scene 1 — Which Endpoint Settings Belong to Actuator and Which Belong to Externalized Configuration?

**Time:** `02:57–03:29`

**Visual:**

Split the screen: left shows `management.endpoint.*` / `management.endpoints.*` semantics; right shows YAML, environment variables, CLI arguments, and property-source precedence feeding those values. Draw the handoff to the externalized-configuration module.

**Script:**

Actuator owns what management properties mean: enabling an endpoint, selecting exposure, changing the web base path, or controlling health detail. Externalized configuration owns where those values came from and which property source won. If `management.endpoints.web.exposure.include` has the wrong value, Actuator explains the effect; if an environment variable unexpectedly overrode YAML, the investigation belongs to Boot property resolution.

**Purpose:**

Separate endpoint configuration semantics from property-source resolution so the learner knows which module owns each debugging question.
