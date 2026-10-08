---
video:
  url: ""
---

# Health Endpoint and Health Contributors

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

## What Does the Health Endpoint Represent?

<!-- VIDEO_SECTION -->

### Scene 1 — What Does the Health Endpoint Represent?

**Time:** `00:00–00:34`

**Visual:**

Show `/actuator/health` returning only `status`, then expand the same response into components. Beside it, display consumers: load balancer, deployment platform, operator. Keep a warning that health is operational evidence, not a guarantee that every business transaction succeeds.

**Script:**

The health endpoint summarizes operational evidence from the running application and its components. Platforms and operators use it to decide whether the service is usable, but `UP` is not a proof that every domain transaction will succeed. Boot assembles the answer from registered health contributors and can expose only the aggregate status or richer component details depending on configuration and authorization.

**Purpose:**

Define what health can and cannot claim, and show why the same health model serves both probes and deeper operator diagnosis.

## How Do HealthContributor and HealthIndicator Fit Together?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:34–00:46`

**Visual:**

Keep the health tree on screen and transform only the layer needed by `How Do HealthContributor and HealthIndicator Fit Together?`: leaf contributor, composite branch, aggregate status, response visibility, or the live custom-indicator experiment.

**Script:**

The endpoint is an aggregate view. To understand where that aggregate comes from, we need the contributor contracts that supply each piece of evidence.

**Purpose:**

Move from the aggregate health response to the contributor contracts that produce each piece of health evidence.

### Scene 1 — How Do HealthContributor and HealthIndicator Fit Together?

**Time:** `00:46–01:19`

**Visual:**

Open `LearningDependencyHealthIndicator` beside a health tree. Highlight `HealthContributor` at the tree level and `HealthIndicator.health()` at a leaf returning `Health.up()` or `Health.down()` with bounded details.

**Script:**

`HealthContributor` is the common building block of the health tree. A `HealthIndicator` is a leaf contributor that computes one `Health` result: a status plus optional details. A good indicator checks one bounded operational condition and reports evidence; it should not repair the component, restart the process, or embed an entire monitoring policy. Reactive variants feed the same conceptual contributor tree.

**Purpose:**

Distinguish the tree contract from a leaf check and establish the side-effect-free responsibility of a health indicator.

## How Do Composite Health Contributors Form a Health Tree?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:19–01:30`

**Visual:**

Keep the health tree on screen and transform only the layer needed by `How Do Composite Health Contributors Form a Health Tree?`: leaf contributor, composite branch, aggregate status, response visibility, or the live custom-indicator experiment.

**Script:**

Individual indicators answer local questions; composition is how several checks become one operational subsystem without losing the ability to drill down.

**Purpose:**

Show how independent leaf checks become an operational hierarchy that can still be drilled into during an incident.

### Scene 1 — How Do Composite Health Contributors Form a Health Tree?

**Time:** `01:30–02:05`

**Visual:**

Draw a composite node `payments` with child indicators `database`, `queue`, and `remoteApi`. Collapse it to one parent status, then expand nested component paths only when component visibility is enabled.

**Script:**

A `CompositeHealthContributor` groups child contributors under one operational node, so health becomes a tree instead of a flat list. That hierarchy is useful when it answers an incident question such as “which part of payments is down?” It should not mirror packages for their own sake. When component visibility allows it, callers can navigate from the aggregate into nested contributors to locate the failing branch.

**Purpose:**

Show how composition improves diagnosis while warning against meaningless structural nesting.

## How Is the Overall Health Status Aggregated?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:05–02:17`

**Visual:**

Keep the health tree on screen and transform only the layer needed by `How Is the Overall Health Status Aggregated?`: leaf contributor, composite branch, aggregate status, response visibility, or the live custom-indicator experiment.

**Script:**

A health tree still needs one outward result. Aggregation explains which status wins; HTTP mapping explains what a caller sees on the wire.

**Purpose:**

Collapse the contributor tree into the aggregate status and HTTP signal that infrastructure actually consumes.

### Scene 1 — How Is the Overall Health Status Aggregated?

**Time:** `02:17–02:55`

**Visual:**

Animate leaf statuses into `StatusAggregator`, then into `HttpCodeStatusMapper`. Show `DOWN`/`OUT_OF_SERVICE` → 503 and `UP`/`UNKNOWN` → 200. Add a configuration card showing that a custom `management.endpoint.health.status.http-mapping.*` disables the default 503 mappings unless they are restated.

**Script:**

Actuator aggregates leaf statuses with a `StatusAggregator`, using an order where serious states such as `DOWN` and `OUT_OF_SERVICE` outrank `UP`. HTTP exposure then maps the aggregate status to a response code: by default `DOWN` and `OUT_OF_SERVICE` produce 503, while `UP` and `UNKNOWN` remain 200. One subtle Boot rule matters here: once you define any custom health status HTTP mapping, restate the default 503 mappings too if you still need them.

**Purpose:**

Connect contributor status to the HTTP signal platforms actually consume, including the easy-to-miss custom-mapping consequence.

## How Should Health Components and Details Be Exposed?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:55–03:06`

**Visual:**

Keep the health tree on screen and transform only the layer needed by `How Should Health Components and Details Be Exposed?`: leaf contributor, composite branch, aggregate status, response visibility, or the live custom-indicator experiment.

**Script:**

After aggregation tells us the result, the next decision is how much evidence each audience should be allowed to see.

**Purpose:**

Separate the outward status from the amount of component/detail evidence each audience is allowed to see.

### Scene 1 — How Should Health Components and Details Be Exposed?

**Time:** `03:06–03:42`

**Visual:**

Show three responses from the same health tree: status only, component names, and full details. Label `show-components`, `show-details`, `when-authorized`, and `management.endpoint.health.roles`; mask secret-like detail fields.

**Script:**

Health status and detail visibility are separate controls. `show-components` and `show-details` determine how much of the contributor tree is returned, with conservative defaults. `when-authorized` can use configured health roles, but authentication and role assignment still come from the application’s security configuration. A probe normally needs only status; operators may need component evidence. Secrets and large diagnostic payloads do not belong in health details even behind authentication.

**Purpose:**

Teach least-detail exposure and the boundary between Actuator visibility rules and Spring Security identity/roles.

## When Should You Add a Custom Health Indicator?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:42–03:56`

**Visual:**

Keep the health tree on screen and transform only the layer needed by `When Should You Add a Custom Health Indicator?`: leaf contributor, composite branch, aggregate status, response visibility, or the live custom-indicator experiment.

**Script:**

The contributor model becomes most useful when Boot cannot infer a domain-specific condition. A real custom indicator shows exactly how application evidence joins the standard health pipeline.

**Purpose:**

Use the contributor model in a real application-specific check so the learner sees how custom evidence joins standard health.

### Scene 1 — When Should You Add a Custom Health Indicator?

**Time:** `03:56–04:38`

**Visual:**

Run the real experiment: `POST /api/actuator-learning/health/false`, highlight `LearningDependencyHealthIndicator.health()` returning `DOWN`, then inspect `/actuator/health/learningDependency` and the aggregate `/actuator/health`; restore with `POST .../health/true`.

**Script:**

Use a custom indicator when the application has a real operational condition that Boot cannot infer. Here the learning API flips a controlled dependency state. The indicator reads that same state and reports `DOWN`; the component endpoint and aggregate health immediately reflect it. The useful observation is not just that custom code runs—it is that application-specific evidence enters the normal Actuator health tree. Keep such checks fast, bounded, side-effect free, and protected from slow remote calls with appropriate timeouts.

**Purpose:**

Prove the custom-health model with the repository’s deterministic Step 5 experiment and connect the observed management response back to indicator design.
