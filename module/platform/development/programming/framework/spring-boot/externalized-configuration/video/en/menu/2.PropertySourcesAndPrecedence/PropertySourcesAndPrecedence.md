---
video:
  url: ""
---

# Property Sources and Override Precedence

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

## How Spring Boot Orders Property Sources

<!-- VIDEO_SECTION -->

### Scene 1 — Precedence Is an Ordered Competition

**Time:** `00:00–00:45`

**Visual:**

Build a vertical ladder from lower to higher precedence: programmatic defaults, Config Data, OS environment, JVM system properties, `SPRING_APPLICATION_JSON`, command-line arguments, then test-only overrides. Place the same key on several rungs and highlight the highest participating value.

**Script:**

When several sources define the same key, Boot does not choose randomly. It keeps those candidates in ordered property sources, and a later source in the documented precedence can override an earlier one. The safe habit is to reason from the real source category and Boot's order instead of relying on shortcuts like “external always wins.”

**Purpose:**

Establish precedence as an ordered competition between property sources.

## Defaults, Config Data, and Later Overrides

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:45–00:55`

**Visual:**

Keep the precedence ladder visible and zoom into three layers: packaged file, external file, and command line.

**Script:**

Now we can apply that ordering to the most common deployment pattern: ship a default, override it externally, and optionally override it again at launch.

**Purpose:**

Move from the abstract order to a concrete default-and-override chain.

### Scene 2 — Default, Deployment Override, Launch Override

**Time:** `00:55–01:40`

**Visual:**

Show `app.region=us-east` inside the jar, `app.region=eu-west` in an external `application.properties`, and `--app.region=ap-south` on the command line. Highlight `ap-south`, then remove the CLI row and highlight `eu-west`.

**Script:**

The packaged file can carry a sensible default, the external file can adapt it to a deployment, and the command line can override both. With all three present, `ap-south` is effective. Remove the CLI option and the external value becomes visible. This layering lets one artifact carry defaults without hard-coding the deployment.

**Purpose:**

Show the fallback chain from packaged defaults through external Config Data to a later launch override.

## Environment Variables, System Properties, Inline JSON, and Command-Line Arguments

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:40–01:50`

**Visual:**

Replace the file examples with four runtime input cards and keep the same key on each card.

**Script:**

Runtime inputs also compete with one another, and their relative order matters just as much as their ability to override files.

**Purpose:**

Carry precedence reasoning into the common runtime-oriented sources.

### Scene 3 — Runtime Overrides Are Not Peers

**Time:** `01:50–02:35`

**Visual:**

Arrange `APP_REGION=eu`, `-Dapp.region=us`, `SPRING_APPLICATION_JSON`, and `--app.region=ap` in Boot 3.3 precedence order. Highlight the command-line value last.

**Script:**

Environment variables, Java system properties, inline JSON, and command-line options are all runtime inputs, but they are not at the same precedence. In Boot 3.3, system properties can override OS environment variables, inline JSON comes later, and command-line arguments later still. That power is useful, but unexplained launch overrides can make a deployment difficult to reproduce.

**Purpose:**

Teach the relative order of the major runtime property sources and why high-precedence overrides should remain explicit.

## Why @PropertySource Can Be Too Late for Early Boot Properties

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:35–02:45`

**Visual:**

Turn the precedence ladder into a startup timeline and place `@PropertySource` at application-context refresh time.

**Script:**

Precedence alone is not enough. A value also has to exist early enough for Boot to use it when an early startup decision is made.

**Purpose:**

Introduce lifecycle timing as a second dimension of configuration behavior.

### Scene 4 — Correct Value, Wrong Time

**Time:** `02:45–03:30`

**Visual:**

Mark `logging.*` and `spring.main.*` near the start of the timeline. Place `@PropertySource` later during context refresh and show a late property entering `Environment` after an early decision is already complete.

**Script:**

`@PropertySource` is added while the application context is being refreshed. Some Boot settings are read before that point, including early logging and `spring.main` decisions. So a property may appear in the Environment later and still be too late to affect the startup choice that already happened.

**Purpose:**

Show why early Boot properties require an early source, not merely a high-enough precedence.

## Reasoning About Which Value Wins

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:40`

**Visual:**

Freeze the startup timeline and open a compact diagnostic checklist beside it.

**Script:**

With source order and timing in mind, we can turn precedence into a repeatable debugging method.

**Purpose:**

Convert the conceptual rules into an operational diagnostic sequence.

### Scene 5 — Trace the Winner and the Fallback Chain

**Time:** `03:40–04:30`

**Visual:**

Use `app.mode` with packaged `standard`, environment `safe`, and CLI `fast`. Number the checks: exact key, candidates, loaded sources, precedence, profile/import participation, then binding.

**Script:**

Start with the exact canonical key, list every source that defines it, confirm those sources actually loaded, and then compare them using Boot precedence. Here `fast` wins. Remove the CLI option and `safe` appears; remove the environment variable and `standard` becomes visible. Only after that chain is clear should you investigate binding or consumption code.

**Purpose:**

Give the learner a deterministic way to explain both the winning value and its fallback chain.

## Where Test-Specific Property Sources Belong

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:30–04:40`

**Visual:**

Add a shaded test-only layer above the normal precedence ladder.

**Script:**

The same reasoning still applies in tests, but tests deliberately add their own high-precedence property sources.

**Purpose:**

Connect the normal precedence model to the testing boundary without expanding into test-context internals.

### Scene 6 — Tests Add Their Own Overrides

**Time:** `04:40–05:25`

**Visual:**

Place annotation `properties`, `@DynamicPropertySource`, and `@TestPropertySource` in the shaded test layer above normal application sources. Show a value differing between normal launch and test launch.

**Script:**

Boot tests can add high-precedence sources specifically so a test can replace normal application configuration. That is why a value seen in a test may differ from the same application launched normally. This module only needs that boundary-aware model; the detailed lifecycle of those sources belongs to the Testing module.

**Purpose:**

Recognize test-specific overrides as intentional additions to the precedence model and hand off their mechanics to Testing.
