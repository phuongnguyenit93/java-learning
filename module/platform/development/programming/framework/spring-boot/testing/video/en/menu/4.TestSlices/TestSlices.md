---
video:
  url: ""
---

# Choosing the Smallest Useful Boot Test Slice

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## What Problem Does a Boot Test Slice Solve?

<!-- VIDEO_SECTION -->

### Scene 1 — What Problem Does a Boot Test Slice Solve?

**Time:** `00:00–00:53`

**Visual:**

Start with the full application graph, gray out unrelated layers, and leave only the focused framework boundary plus the test infrastructure needed to exercise it.

**Script:**

A Boot test slice loads a deliberately restricted application context for one class of behavior. Instead of starting every application bean and every applicable auto-configuration, the slice selects the components and test infrastructure relevant to a focused layer such as MVC, WebFlux, JPA, or JDBC. The benefit is not only speed. A smaller context makes the boundary under test explicit and reduces unrelated failures. The trade-off is that behavior depending on omitted layers cannot be proven by that slice.

**Purpose:**

Explain the problem slices solve: preserve the framework behavior under test while excluding unrelated application layers and infrastructure.

## How Does a Slice Restrict Component Scanning and Context Scope?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:53–01:08`

**Visual:**

Keep the current context boundary visible, then resize it to expose the next scope decision: show component scanning entering a filter gate: allowed slice component types pass; unrelated services/repositories/configuration stay outside the context.

**Script:**

A slice is useful only because it narrows scope, so inspect the scanning and filtering rules that define what stays inside that boundary.

**Purpose:**

Turn the abstract slice boundary into the component filter that decides which application beans survive.

### Scene 1 — How Does a Slice Restrict Component Scanning and Context Scope?

**Time:** `01:08–01:55`

**Visual:**

Show component scanning entering a filter gate: allowed slice component types pass; unrelated services/repositories/configuration stay outside the context.

**Script:**

Slice annotations use type-exclusion filters and focused scanning rules so only relevant application components are discovered. A web slice, for example, selects controller-oriented infrastructure rather than every service and repository in the application. This restriction is intentional. When a collaborator is absent, first ask whether the slice was designed to exclude it before treating the missing bean as a broken application.

**Purpose:**

Show how slice scanning and type-exclusion rules deliberately restrict which application components enter the context.

## How Does a Slice Select Purpose-Specific Auto-Configuration?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:55–02:10`

**Visual:**

Keep the current context boundary visible, then resize it to expose the next scope decision: under the filtered component set, reveal the curated test auto-configuration imported by the slice annotation.

**Script:**

Filtering application components is only half the story; Boot also imports purpose-specific test auto-configuration to make the remaining slice usable.

**Purpose:**

Connect filtered application components to the curated test auto-configuration that makes the slice usable.

### Scene 1 — How Does a Slice Select Purpose-Specific Auto-Configuration?

**Time:** `02:10–02:55`

**Visual:**

Under the filtered component set, reveal the curated test auto-configuration imported by the slice annotation.

**Script:**

Each slice imports a curated set of test and application auto-configurations right to its goal. The exact list differs across annotations and is documented in Boot's test-auto-configuration appendix. This is why a slice can still feel “Boot-aware” even though it is not a full `@SpringBootTest` context: selected Boot configuration remains active, but unrelated auto-configuration is deliberately absent.

**Purpose:**

Make slice auto-configuration visible so learners can distinguish component filtering from the infrastructure Boot intentionally imports for that slice.

## Why Should a Test Start from One `@...Test` Slice?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:55–03:10`

**Visual:**

Keep the current context boundary visible, then resize it to expose the next scope decision: show one `@...Test` annotation as the root boundary, then add optional `@AutoConfigure...`, `@ImportAutoConfiguration`, `@Import`, or test beans around it instead of stacking another slice.

**Script:**

Because each slice already defines a coherent boundary and infrastructure set, the next rule is to start from one slice rather than stacking several together.

**Purpose:**

Turn the curated slice model into a composition rule: keep one primary slice boundary and extend it with focused support instead of stacking competing slice definitions.

### Scene 1 — Why Should a Test Start from One `@...Test` Slice?

**Time:** `03:10–04:01`

**Visual:**

Show one `@...Test` annotation as the root boundary, then add optional `@AutoConfigure...`, `@ImportAutoConfiguration`, `@Import`, or test beans around it instead of stacking another slice.

**Script:**

Boot does not support using multiple `@...Test` slice annotations on the same test. Choose one slice as the primary test boundary instead of stacking slices together. Choose the slice that best represents the behavior, then add narrowly targeted support from another slice through the relevant `@AutoConfigure...` annotation when available. Reach for `@ImportAutoConfiguration`, `@Import` for user configuration, or mock/test beans only when the test specifically needs that additional support.

**Purpose:**

Reinforce the one-slice starting point so a focused test does not accidentally become an improvised full context by stacking unrelated slice annotations.

## How Do You Choose the Smallest Slice That Still Proves the Behavior?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:01–04:16`

**Visual:**

Keep the current context boundary visible, then resize it to expose the next scope decision: use an assertion-driven decision tree: controller mapping → web slice; JPA query → data slice; cross-layer startup/wiring → full context.

**Script:**

With one slice selected, refine the decision by asking for the smallest boundary that still proves the required collaboration.

**Purpose:**

Move from “one slice” to “right-sized slice” by testing whether every collaboration required by the assertion still remains inside the selected boundary.

### Scene 1 — How Do You Choose the Smallest Slice That Still Proves the Behavior?

**Time:** `04:16–05:14`

**Visual:**

Use an assertion-driven decision tree: controller mapping → web slice; JPA query → data slice; cross-layer startup/wiring → full context.

**Script:**

Start from the observable behavior rather than the production package structure. If the test proves controller mapping and serialization, a web slice may be enough. If it proves JPA mappings and queries, a data slice is a better boundary. If it proves cross-layer startup and wiring, use the full context. The smallest useful slice is the smallest context that still contains every boundary whose cooperation the assertion depends on. Making a context smaller than that only produces false confidence or excessive mocking.

**Purpose:**

Give a decision rule for selecting the smallest slice that still contains the collaboration the assertion must prove.

## When Does a Slice Need to Hand Off to a Full Context?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:14–05:26`

**Visual:**

Keep the current context boundary visible, then resize it to expose the next scope decision: animate a slice growing as production imports accumulate; stop before it becomes a reconstructed application and replace it with one `@SpringBootTest` boundary.

**Script:**

A narrow boundary eventually stops being sufficient; the final question is when omitted cross-layer behavior forces a handoff to a full Boot context.

**Purpose:**

Expose the failure point of over-narrowing: once required cross-layer wiring sits outside the slice, the learner should switch models rather than keep importing production pieces back in.

### Scene 1 — When Does a Slice Need to Hand Off to a Full Context?

**Time:** `05:26–06:10`

**Visual:**

Animate a slice growing as production imports accumulate; stop before it becomes a reconstructed application and replace it with one `@SpringBootTest` boundary.

**Script:**

Move to `@SpringBootTest` when the behavior fundamentally crosses slice boundaries, depends on broad auto-configuration, requires production-like startup, or needs an actual web server. Avoid continuously importing more production configuration into a slice until it silently resembles the full application. The handoff is a design decision: slices prove focused integration; full contexts prove cooperation across a larger Boot application boundary.

**Purpose:**

Identify the point where a slice becomes dishonest because required cross-layer wiring is outside its boundary and a full context is the better test model.
