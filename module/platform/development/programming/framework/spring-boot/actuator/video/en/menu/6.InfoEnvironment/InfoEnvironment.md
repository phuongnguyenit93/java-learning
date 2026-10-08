---
video:
  url: ""
---

# Info and Environment-facing Endpoints

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

## What Is the Info Endpoint For?

<!-- VIDEO_SECTION -->

### Scene 1 — What Is the Info Endpoint For?

**Time:** `00:00–00:34`

**Visual:**

Show `/actuator/info` with compact `build`, `git`, and one custom metadata block. Contrast it with a large debug dump crossed out.

**Script:**

The info endpoint is for small, stable metadata that helps operators identify what is running: build identity, source revision, deployment flavor, or similar non-transactional information. `InfoContributor` beans assemble the response. Treat it as an identity card, not a general debug dump—high-frequency state belongs in metrics, and secrets or detailed configuration do not become appropriate merely because an operator might want them.

**Purpose:**

Define a narrow operational role for info and distinguish metadata from diagnostics or telemetry.

## How Do InfoContributor-style Contributions Build Operational Metadata?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:34–00:44`

**Visual:**

Keep the management key/value panel on screen and change its source or protection layer for `How Do InfoContributor-style Contributions Build Operational Metadata?`: contributor, environment, bound config, masking, audience, or externalized-configuration handoff.

**Script:**

The endpoint is only a container; the useful question is which contributors populate that container and under what defaults.

**Purpose:**

Move from the `/info` container to the contributors and Boot defaults that determine what metadata appears in it.

### Scene 1 — How Do InfoContributor-style Contributions Build Operational Metadata?

**Time:** `00:44–01:17`

**Visual:**

Build an `InfoContributor` stack. Mark `build` and `git` enabled when their prerequisite files exist; mark `env`, `java`, `os`, and `process` disabled by default in Boot 3.3. Show `management.info.<id>.enabled` and `management.info.defaults.enabled` beside the stack.

**Script:**

Each `InfoContributor` adds a named piece of metadata. In Boot 3.3 the `build` and `git` contributors are enabled when `META-INF/build-info.properties` or `git.properties` is available, while `env`, `java`, `os`, and `process` contributors are disabled by default. Individual contributors can be toggled with `management.info.<id>.enabled`, independently of whether the info endpoint itself is exposed. Custom contributors should stay small and cheap to compute.

**Purpose:**

Restore the contributor defaults and show that contributor enablement is separate from endpoint exposure.

## What Can Environment-facing Endpoints Reveal About the Running Application?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:17–01:30`

**Visual:**

Keep the management key/value panel on screen and change its source or protection layer for `What Can Environment-facing Endpoints Reveal About the Running Application?`: contributor, environment, bound config, masking, audience, or externalized-configuration handoff.

**Script:**

Info tells us who this application is. When the incident asks “what configuration is this process actually using?”, the management surface shifts to env and configprops.

**Purpose:**

Shift from application identity metadata to configuration-oriented runtime inspection with env and configprops.

### Scene 1 — What Can Environment-facing Endpoints Reveal About the Running Application?

**Time:** `01:30–02:00`

**Visual:**

Place `/actuator/env` beside `/actuator/configprops`. For env, show property sources feeding a `ConfigurableEnvironment`; for configprops, show resolved values bound into a `@ConfigurationProperties` object.

**Script:**

Environment-facing endpoints answer configuration-oriented diagnostic questions from two angles. `env` exposes the running `ConfigurableEnvironment` and its property sources; `configprops` shows values that have already been bound into `ConfigurationProperties` beans. They help you inspect runtime state, but they do not replace the rules that decided property precedence, profile activation, Config Data imports, or binding behavior.

**Purpose:**

Differentiate raw environment/property-source inspection from bound configuration-object inspection and mark the externalized-configuration handoff.

## How Are Potentially Sensitive Values Treated?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:12`

**Visual:**

Keep the management key/value panel on screen and change its source or protection layer for `How Are Potentially Sensitive Values Treated?`: contributor, environment, bound config, masking, audience, or externalized-configuration handoff.

**Script:**

Once configuration is visible, the next problem is disclosure: useful diagnostics can reveal exactly the internal state we most need to protect.

**Purpose:**

Use configuration visibility to introduce masking and show-values before discussing who should receive the endpoints at all.

### Scene 1 — How Are Potentially Sensitive Values Treated?

**Time:** `02:12–02:47`

**Visual:**

Show an env response with values replaced by `******`, then toggle the endpoint `show-values` policy from `never` to `when-authorized`. For HTTP, connect `when-authorized` to authentication and configured roles. For JMX, show a separate callout: “all users treated as authorized for show-values” plus an independent JMX access-control boundary.

**Script:**

Configuration endpoints can mask values that may be sensitive, and their `show-values` policies decide when original values are returned. For HTTP, `when-authorized` depends on authentication and the configured endpoint roles. JMX is different: for this `show-values` decision Actuator treats every JMX user as authorized, so the JMX exposure and access path must be protected independently. Property names, source names, object structure, and unsanitized values can still reveal useful information. Limit exposure first, then use sanitization as defense in depth.

**Purpose:**

Make sanitization and value visibility concrete without presenting masking as a substitute for endpoint access control.

## Why Are Info and Environment Views an Access Decision?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:47–02:58`

**Visual:**

Keep the management key/value panel on screen and change its source or protection layer for `Why Are Info and Environment Views an Access Decision?`: contributor, environment, bound config, masking, audience, or externalized-configuration handoff.

**Script:**

Masking reduces accidental disclosure, but it does not decide who should receive the endpoint at all. That is an access-design question.

**Purpose:**

Escalate from value masking to the broader audience/network/authorization decision required for sensitive management data.

### Scene 1 — Why Are Info and Environment Views an Access Decision?

**Time:** `02:58–03:32`

**Visual:**

Compare two audiences: deployment automation reads curated `/info`; privileged operators inspect `/env` or `/configprops` through an internal management path. Surround the latter with network, exposure, authorization, and sanitization layers.

**Script:**

Info and environment views have different risk profiles. A carefully curated info response may be safe for a wider operational audience, while env and configprops usually deserve tighter network and authorization boundaries. Review both the values and the surrounding metadata before exposing them. Exposure, network placement, security rules, and sanitization should reinforce each other rather than being treated as interchangeable controls.

**Purpose:**

Turn data sensitivity into an explicit audience/access decision instead of a blanket “all Actuator endpoints are internal” rule.

## Where Does Endpoint Inspection Hand Off to Externalized Configuration?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:32–03:46`

**Visual:**

Keep the management key/value panel on screen and change its source or protection layer for `Where Does Endpoint Inspection Hand Off to Externalized Configuration?`: contributor, environment, bound config, masking, audience, or externalized-configuration handoff.

**Script:**

After deciding who may inspect configuration, the final question is where to go when the value itself is wrong. That investigation leaves Actuator and follows Boot’s configuration rules.

**Purpose:**

Hand an unexpected runtime configuration value from Actuator inspection to the externalized-configuration rules that produced it.

### Scene 1 — Where Does Endpoint Inspection Hand Off to Externalized Configuration?

**Time:** `03:46–04:20`

**Visual:**

Draw a troubleshooting handoff: Actuator shows property source/value or bound object → externalized configuration traces Config Data, profiles, environment variables, command-line arguments, precedence, and binding. Use one wrong-value example moving across the boundary.

**Script:**

Actuator tells you what configuration-related state the running process can report. Externalized configuration explains how Boot resolved that state. If `env` shows a property in an unexpected source or `configprops` shows an unexpected bound value, stop changing Actuator settings and follow the property-source precedence and binding rules instead. This keeps runtime inspection and configuration resolution as complementary tools rather than duplicate curricula.

**Purpose:**

Give configuration incidents a clean handoff from runtime evidence to the subsystem that owns value resolution.
