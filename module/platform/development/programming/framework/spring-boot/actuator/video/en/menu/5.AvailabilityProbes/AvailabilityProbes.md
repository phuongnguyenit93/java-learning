---
video:
  url: ""
---

# Liveness, Readiness, and Health Probes

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

## How Does Actuator Consume Boot Application Availability?

<!-- VIDEO_SECTION -->

### Scene 1 — How Does Actuator Consume Boot Application Availability?

**Time:** `00:00–00:32`

**Visual:**

Draw `ApplicationAvailability` outside the Actuator boundary. Feed `LivenessState` into `LivenessStateHealthIndicator` and `ReadinessState` into `ReadinessStateHealthIndicator`, then into the health tree.

**Script:**

Liveness and readiness are runtime states first, management data second. Application runtime owns `ApplicationAvailability` and decides when those states change. Actuator consumes the current states through dedicated health indicators and projects them into the normal health model. A probe request therefore observes lifecycle state; it does not cause runners to finish, make the application ready, or drive shutdown.

**Purpose:**

Establish the one-way handoff from lifecycle state ownership to Actuator health representation.

## How Do Liveness and Readiness Become Health Groups?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:32–00:44`

**Visual:**

Keep the liveness/readiness state diagram alive and move the highlight from `How Does Actuator Consume Boot Application Availability?` to `How Do Liveness and Readiness Become Health Groups?`—state source, health group, HTTP path, dependency policy, or lifecycle owner.

**Script:**

Once availability enters the health tree, grouping gives each state its own operational contract instead of mixing both into one generic health result.

**Purpose:**

Turn raw lifecycle states into focused health groups without moving ownership of the states into Actuator.

### Scene 1 — How Do Liveness and Readiness Become Health Groups?

**Time:** `00:44–01:18`

**Visual:**

Show two health-group nodes named `liveness` and `readiness`, each containing its matching availability indicator. Add a Kubernetes badge marked “auto-enabled”, `management.endpoint.health.probes.enabled` for explicit enablement elsewhere, and group-level knobs for included contributors, detail visibility, and status behavior.

**Script:**

Actuator represents the two availability signals as health groups named `liveness` and `readiness`, backed by the matching availability-state indicators. In Kubernetes, Boot enables these probe groups automatically; elsewhere they can be enabled explicitly with `management.endpoint.health.probes.enabled`. Because they are normal health groups, their included contributors, detail visibility, and status behavior can be configured at group level. Keep their contracts distinct: liveness asks whether restart is appropriate, while readiness asks whether traffic should be routed here.

**Purpose:**

Restore how probe groups are activated and configured while keeping liveness and readiness distinct operational contracts.

## What Operational Views Do Liveness and Readiness Probe Endpoints Provide?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:18–01:28`

**Visual:**

Keep the liveness/readiness state diagram alive and move the highlight from `How Do Liveness and Readiness Become Health Groups?` to `What Operational Views Do Liveness and Readiness Probe Endpoints Provide?`—state source, health group, HTTP path, dependency policy, or lifecycle owner.

**Script:**

Health groups define the meaning; probe paths turn that meaning into endpoints a deployment platform can actually call.

**Purpose:**

Move from group semantics to the concrete HTTP paths that deployment platforms call.

### Scene 1 — What Operational Views Do Liveness and Readiness Probe Endpoints Provide?

**Time:** `01:28–02:04`

**Visual:**

Show HTTP calls to `/actuator/health/liveness` and `/actuator/health/readiness`. Then enable additional probe paths and draw `/livez` and `/readyz` on the main server port beside a separate management port.

**Script:**

With the default management base path, the probe groups are exposed as `/actuator/health/liveness` and `/actuator/health/readiness` when those groups are enabled and health is exposed. If management runs on a separate port, Boot can also add the liveness and readiness paths to the main server port. That matters when the probe must validate the same listener that receives real application traffic rather than only a healthy management server.

**Purpose:**

Show the actual probe URLs and why main-port probe paths can avoid a false sense of availability with a separate management server.

## Why Should Probe Dependency Checks Be Chosen Carefully?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:04–02:16`

**Visual:**

Keep the liveness/readiness state diagram alive and move the highlight from `What Operational Views Do Liveness and Readiness Probe Endpoints Provide?` to `Why Should Probe Dependency Checks Be Chosen Carefully?`—state source, health group, HTTP path, dependency policy, or lifecycle owner.

**Script:**

Now that the platform can call the groups, the dangerous design choice is what evidence goes into each one—especially shared external dependencies.

**Purpose:**

Use real probe consumers to motivate why external dependencies must be assigned according to recovery behavior.

### Scene 1 — Why Should Probe Dependency Checks Be Chosen Carefully?

**Time:** `02:16–02:53`

**Visual:**

Use a three-instance service diagram sharing one database. Put the database check outside liveness; show a readiness option that may include it. Then fail the database and contrast “restart every pod” with “temporarily stop routing traffic.”

**Script:**

External dependencies require careful placement. Liveness should normally avoid databases, remote APIs, and caches because restarting the process cannot repair a shared dependency outage; putting that failure into liveness can trigger a restart storm. Readiness may include selected external dependencies when the service truly cannot accept traffic without them, but that choice is application-specific. Probe composition is an operational policy, not a rule to include every health check everywhere.

**Purpose:**

Explain the failure-domain consequence of dependency checks so probe groups are designed for recovery behavior, not completeness.

## Where Does Actuator Hand Availability-State Ownership Back to Application Runtime?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:53–03:06`

**Visual:**

Keep the liveness/readiness state diagram alive and move the highlight from `Why Should Probe Dependency Checks Be Chosen Carefully?` to `Where Does Actuator Hand Availability-State Ownership Back to Application Runtime?`—state source, health group, HTTP path, dependency policy, or lifecycle owner.

**Script:**

Probe composition tells us what is observed; the final distinction is who is allowed to change those states. That owner remains the application lifecycle, not Actuator.

**Purpose:**

Close the probe model by returning state-transition ownership to application runtime during startup and shutdown.

### Scene 1 — Where Does Actuator Hand Availability-State Ownership Back to Application Runtime?

**Time:** `03:06–03:41`

**Visual:**

Animate startup: liveness → `CORRECT`, runners still executing, then readiness → `ACCEPTING_TRAFFIC`. Animate shutdown in reverse with readiness moving away from accepting traffic before termination; keep Actuator as a read-only projection of those changes.

**Script:**

The ownership boundary becomes visible during lifecycle transitions. On startup, liveness can become `CORRECT` before readiness reaches `ACCEPTING_TRAFFIC` because application runners still have work to finish. During shutdown, runtime changes readiness as part of the lifecycle and Actuator reflects the resulting signal. If readiness unexpectedly becomes `REFUSING_TRAFFIC`, changing the health group does not repair the cause—trace the runtime state transition in the application-runtime domain.

**Purpose:**

Restore the full startup/shutdown ownership model and show when troubleshooting must leave Actuator for application runtime.
