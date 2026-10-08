---
video:
  url: ""
---

# Metrics Endpoint and the Micrometer Boundary

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

## How Does Boot Integrate Micrometer MeterRegistry Infrastructure?

<!-- VIDEO_SECTION -->

### Scene 1 — How Does Boot Integrate Micrometer MeterRegistry Infrastructure?

**Time:** `00:00–00:31`

**Visual:**

Draw `framework/application instrumentation → MeterRegistry → Actuator metrics endpoint`. Add a second arrow from the registry to an external registry/exporter. Label ownership: Micrometer meter model, Boot auto-configuration, Actuator inspection.

**Script:**

Metrics start in Micrometer, not in the Actuator endpoint. Boot auto-configures `MeterRegistry` infrastructure, binds many framework/JVM/process meters, and lets application code register more meters through the Spring-managed registry. Actuator then provides management views over that registry. Keeping those roles separate matters because meter design belongs to Micrometer and observability even though Boot makes the integration convenient.

**Purpose:**

Establish the registry as the source of metrics and Actuator as an inspection layer rather than the owner of the meter model.

## What Is the Actuator Metrics Endpoint For?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:31–00:43`

**Visual:**

Keep the `MeterRegistry` at the center and reroute the highlighted arrow for `What Is the Actuator Metrics Endpoint For?`—inspection, measurement identity, tag selection, export, or observability ownership—so the flow evolves rather than resets.

**Script:**

Once the registry is the source, Actuator’s job becomes specific: let an operator inspect what this process currently knows about its meters.

**Purpose:**

Move from registry ownership to the diagnostic endpoint that lets an operator inspect the registry of one process.

### Scene 1 — What Is the Actuator Metrics Endpoint For?

**Time:** `00:43–01:15`

**Visual:**

Call `/actuator/metrics`, select one meter name, then call `/actuator/metrics/{name}`. Show available measurements and tags, while a timeline database is deliberately absent from this screen.

**Script:**

The metrics endpoint is an on-demand diagnostic surface. The collection endpoint lists meter names known to the registry; selecting one name shows measurements and available tag dimensions. This is useful for questions such as “did this meter register?” or “which tags exist right now?” It is not a time-series query engine and does not replace a monitoring backend.

**Purpose:**

Define the metrics endpoint as current-process inspection and prevent it from being confused with long-term telemetry storage.

## How Do Meter Names and Measurements Appear in the Metrics Endpoint?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:28`

**Visual:**

Keep the `MeterRegistry` at the center and reroute the highlighted arrow for `How Do Meter Names and Measurements Appear in the Metrics Endpoint?`—inspection, measurement identity, tag selection, export, or observability ownership—so the flow evolves rather than resets.

**Script:**

The endpoint can list a meter, but a name alone may still combine several tagged identities. Measurements only make sense once that dimensional model is visible.

**Purpose:**

Reveal that one meter name can still represent several tagged identities before introducing tag filtering.

### Scene 1 — How Do Meter Names and Measurements Appear in the Metrics Endpoint?

**Time:** `01:28–02:01`

**Visual:**

Show one Micrometer name with several tagged meter identities. Aggregate `COUNT` and `TOTAL_TIME` at the unfiltered view, then display `availableTags` that reveal `outcome=success|failure`.

**Script:**

A metrics selector uses the Micrometer meter name as it exists in the application, even if a backend later normalizes that name. One name can represent multiple tagged meter identities, so measurements such as `COUNT`, `TOTAL_TIME`, or `VALUE` may be aggregated across all matching identities. `availableTags` is the clue that a single displayed name can still contain several dimensional series.

**Purpose:**

Teach how names, measurements, and tagged identities relate so an aggregate response is not mistaken for one physical meter instance.

## How Can Tags Narrow a Metrics Inspection?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:01–02:13`

**Visual:**

Keep the `MeterRegistry` at the center and reroute the highlighted arrow for `How Can Tags Narrow a Metrics Inspection?`—inspection, measurement identity, tag selection, export, or observability ownership—so the flow evolves rather than resets.

**Script:**

Available tags tell us dimensions exist; the live `learning.requests` experiment now shows how a management query selects one dimension without changing the metric itself.

**Purpose:**

Use the tagged meter model to justify a live filtered query against the repository’s `learning.requests` evidence.

### Scene 1 — How Can Tags Narrow a Metrics Inspection?

**Time:** `02:13–02:49`

**Visual:**

Run the repository experiment: call `POST /api/actuator-learning/metrics/success` twice and `.../failure` once. Inspect `/actuator/metrics/learning.requests`, then add `?tag=outcome:success` and show the narrowed measurement plus `availableTags`.

**Script:**

Tag filtering selects from meter identities that already exist; it does not create a new metric. In this module the learning API increments `learning.requests` with an `outcome` tag. The unfiltered endpoint can aggregate all outcomes, while `tag=outcome:success` narrows the inspection to the matching series. Multiple `tag=KEY:VALUE` parameters can narrow further. The observation is useful for diagnosis, but it does not make high-cardinality tag design safe.

**Purpose:**

Prove tag filtering with Step 5 runtime evidence and connect the narrowed response to Micrometer’s tagged meter model.

## How Is Inspecting Metrics Different from Exporting Them?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:49–03:01`

**Visual:**

Keep the `MeterRegistry` at the center and reroute the highlighted arrow for `How Is Inspecting Metrics Different from Exporting Them?`—inspection, measurement identity, tag selection, export, or observability ownership—so the flow evolves rather than resets.

**Script:**

Tag filtering improves one-process diagnosis. Long-term monitoring asks a different question entirely: how does telemetry leave the process and reach a backend?

**Purpose:**

Contrast one-process inspection with backend export once tag-level diagnosis is clear.

### Scene 1 — How Is Inspecting Metrics Different from Exporting Them?

**Time:** `03:01–03:35`

**Visual:**

Split the pipeline after `MeterRegistry`: left is `/actuator/metrics` for an operator request; right is a Prometheus registry exposing `/actuator/prometheus` for scraping, plus a generic push-registry arrow to an external backend.

**Script:**

Inspecting metrics and exporting them are different flows. `/actuator/metrics` answers an operator’s request against one running process. A monitoring registry or exporter publishes telemetry in the format and cadence its backend expects. Prometheus makes the distinction obvious: with its registry dependency, Boot exposes a dedicated Prometheus scrape endpoint, while the generic metrics endpoint remains a diagnostic view of Micrometer meters.

**Purpose:**

Restore both sides of the inspection-versus-export comparison and show why a monitoring backend should not poll the generic metrics endpoint as its storage pipeline.

## Which Metrics Design and Backend Concerns Belong Outside Actuator?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:35–03:47`

**Visual:**

Keep the `MeterRegistry` at the center and reroute the highlighted arrow for `Which Metrics Design and Backend Concerns Belong Outside Actuator?`—inspection, measurement identity, tag selection, export, or observability ownership—so the flow evolves rather than resets.

**Script:**

Export makes the ownership boundary unavoidable: Actuator can inspect or expose telemetry, but the metric model and backend lifecycle continue outside this module.

**Purpose:**

Use the export boundary to place meter design and backend architecture outside Actuator ownership.

### Scene 1 — Which Metrics Design and Backend Concerns Belong Outside Actuator?

**Time:** `03:47–04:21`

**Visual:**

Place meter naming, tag cardinality, instrument choice, histograms/SLOs, backend queries, dashboards, alerts, retention, and capacity planning outside the Actuator boundary. Highlight an unbounded `userId` tag exploding into many time series.

**Script:**

Actuator can reveal a poor metric design, but it does not own the fix. Choosing counters versus gauges or timers, defining low-cardinality dimensions, configuring histograms and SLOs, and giving measurements useful business meaning belong to instrumentation design. Backend queries, alerts, dashboards, retention, and capacity planning belong to the observability system. An unbounded tag remains costly even if `/actuator/metrics` can filter it perfectly.

**Purpose:**

Close the metrics chapter with ownership boundaries that prevent an endpoint tutorial from becoming metric-model or backend architecture guidance.
