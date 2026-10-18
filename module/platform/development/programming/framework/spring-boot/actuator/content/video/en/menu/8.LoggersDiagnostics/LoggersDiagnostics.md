---
video:
  url: ""
---

# Loggers and Runtime Diagnostic Endpoints

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

## What Does the Loggers Endpoint Expose?

<!-- VIDEO_SECTION -->

### Scene 1 — What Does the Loggers Endpoint Expose?

**Time:** `00:00–00:30`

**Visual:**

Open `/actuator/loggers` with a small table of logger names/groups and `configuredLevel`/`effectiveLevel`. Keep `LoggingSystem` behind the endpoint as the source of runtime logging state.

**Script:**

The loggers endpoint exposes the runtime configuration known to Boot’s `LoggingSystem`. It can list loggers and logger groups, inspect one entry, and report configured and effective levels. Actuator is adding a management operation over an already-running logging system; it does not own logging bootstrap or the application’s normal logging configuration files.

**Purpose:**

Define the loggers endpoint as runtime inspection/control and preserve the boundary with Boot logging initialization.

## How Do Configured and Effective Logger Levels Differ?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:30–00:43`

**Visual:**

Keep the diagnostics board visible and move the focus for `How Do Configured and Effective Logger Levels Differ?` from logging state to runtime capture, analysis handoff, or exposure risk; never replace the board with a generic endpoint diagram.

**Script:**

Listing loggers is not enough when inheritance is involved. The next step is to separate what was configured on this logger from what it inherited.

**Purpose:**

Move from listing logger state to inheritance so configured and effective levels are interpreted correctly.

### Scene 1 — How Do Configured and Effective Logger Levels Differ?

**Time:** `00:43–01:14`

**Visual:**

Use a logger hierarchy: root=`INFO`, `com.example` has no configured level, child inherits `INFO`. Then set `DEBUG` directly on `com.example` and show both fields change.

**Script:**

Configured and effective levels answer different questions. `configuredLevel` tells you whether that exact logger has an explicit setting; `effectiveLevel` tells you what currently governs logging after inheritance. A package can therefore have no configured level and still log at `INFO` because the root supplies it. Inspect both fields before assuming the effective level was configured locally.

**Purpose:**

Make logger inheritance visible so runtime debugging follows the actual source of an effective level.

## How Can Logger Levels Be Changed at Runtime?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:14–01:28`

**Visual:**

Keep the diagnostics board visible and move the focus for `How Can Logger Levels Be Changed at Runtime?` from logging state to runtime capture, analysis handoff, or exposure risk; never replace the board with a generic endpoint diagram.

**Script:**

Once configured versus inherited state is clear, a controlled write shows why the endpoint is more than an inspector—and why that power must be temporary and narrow.

**Purpose:**

Use that level model to justify a bounded runtime write and its restore behavior during an incident.

### Scene 1 — How Can Logger Levels Be Changed at Runtime?

**Time:** `01:28–02:06`

**Visual:**

Send a loggers write request that sets one package to `DEBUG`, show new events appearing, then clear the configured level and watch it inherit again. Add a restart icon that restores normal configured state.

**Script:**

The web loggers endpoint can change a logger or logger group while the process is running. That is useful for a focused incident because no restart is required, but the change is operational state, not durable configuration. After the investigation, clear or restore the level; a restart normally rebuilds logging from the application’s normal sources. Keep the scope narrow because broad DEBUG or TRACE can increase I/O and expose sensitive data.

**Purpose:**

Demonstrate safe runtime logger control, including reversibility and the fact that the change is not a persistent configuration edit.

## What Does the Thread Dump Endpoint Deliver?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:06–02:20`

**Visual:**

Keep the diagnostics board visible and move the focus for `What Does the Thread Dump Endpoint Deliver?` from logging state to runtime capture, analysis handoff, or exposure risk; never replace the board with a generic endpoint diagram.

**Script:**

Logger controls explain emitted events; when the problem is execution state rather than log level, the management surface can collect a different kind of evidence: a thread snapshot.

**Purpose:**

Escalate from logging configuration to a JVM thread snapshot when the problem is execution state rather than log visibility.

### Scene 1 — What Does the Thread Dump Endpoint Deliver?

**Time:** `02:20–02:53`

**Visual:**

Call `/actuator/threaddump` and freeze a compact JSON excerpt showing thread name/id, state, lock information, and stack frames. Beside it, show a JVM-analysis tool receiving the captured snapshot.

**Script:**

The `threaddump` endpoint captures a point-in-time snapshot of JVM thread information and can return a structured representation with identities, states, locks, and stack frames. That answers “give me the evidence from this process.” It does not answer whether a wait is healthy, a lock is hot, or threads are deadlocked. Those interpretations belong to Java concurrency and JVM diagnostics.

**Purpose:**

Separate acquisition of thread evidence from interpretation so the endpoint is not presented as a deadlock analyzer.

## What Does the Heap Dump Endpoint Deliver?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:53–03:07`

**Visual:**

Keep the diagnostics board visible and move the focus for `What Does the Heap Dump Endpoint Deliver?` from logging state to runtime capture, analysis handoff, or exposure risk; never replace the board with a generic endpoint diagram.

**Script:**

A thread dump is structured execution evidence; a heap dump is much heavier memory evidence, so the next transition must also raise the cost and confidentiality stakes.

**Purpose:**

Move from lightweight thread evidence to heavier heap evidence while raising cost and confidentiality concerns.

### Scene 1 — What Does the Heap Dump Endpoint Deliver?

**Time:** `03:07–03:40`

**Visual:**

Download `/actuator/heapdump` as a binary file. Label HotSpot → HPROF and OpenJ9 → PHD, then show the file entering a heap analyzer with a warning badge for size, pause cost, and retained secrets/user data.

**Script:**

The heapdump web endpoint returns a binary heap snapshot: HPROF on HotSpot and PHD on OpenJ9. Dumps can be large, costly to generate, and full of retained production data such as strings, cached objects, credentials, or request content. Treat both generation and the downloaded artifact as privileged incident operations. Object-retention and leak analysis start after Actuator has delivered the file.

**Purpose:**

Explain the heapdump format, operational cost, sensitivity, and the handoff to dedicated memory-analysis tooling.

## Where Does Actuator Data Delivery End and JVM or Logging Analysis Begin?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:40–03:54`

**Visual:**

Keep the diagnostics board visible and move the focus for `Where Does Actuator Data Delivery End and JVM or Logging Analysis Begin?` from logging state to runtime capture, analysis handoff, or exposure risk; never replace the board with a generic endpoint diagram.

**Script:**

After seeing several diagnostics, the common pattern is clear: Actuator delivers evidence, and another discipline interprets it. Making that boundary explicit prevents overclaiming what the endpoint can diagnose.

**Purpose:**

Generalize logger/thread/heap examples into the boundary between evidence delivery and specialist interpretation.

### Scene 1 — Where Does Actuator Data Delivery End and JVM or Logging Analysis Begin?

**Time:** `03:54–04:27`

**Visual:**

Create a handoff table: loggers → logging analysis; threaddump → concurrency/JVM analysis; heapdump → memory/GC analysis. Put “Actuator delivers/controls evidence” on the left and “specialist tooling interprets cause” on the right.

**Script:**

These endpoints illustrate one recurring boundary. Actuator can show the effective logger level, collect a thread snapshot, or produce heap contents. It cannot decide whether the resulting log stream explains the incident, whether thread states are pathological, or which object dominates retained memory. The management layer gets trustworthy evidence out of the process; specialist tooling and domain knowledge explain it.

**Purpose:**

Unify the diagnostic endpoints around a clear acquisition-versus-analysis ownership boundary.

## Why Are Powerful Diagnostic Endpoints an Exposure Risk?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:27–04:41`

**Visual:**

Keep the diagnostics board visible and move the focus for `Why Are Powerful Diagnostic Endpoints an Exposure Risk?` from logging state to runtime capture, analysis handoff, or exposure risk; never replace the board with a generic endpoint diagram.

**Script:**

Because Actuator can collect such strong evidence, the final question is not technical capability but risk: what could each diagnostic reveal or cost if the wrong caller reaches it?

**Purpose:**

Use the power of those diagnostics to motivate minimal exposure and controlled incident access.

### Scene 1 — Why Are Powerful Diagnostic Endpoints an Exposure Risk?

**Time:** `04:41–05:16`

**Visual:**

Build a risk board with four columns: logger writes → log volume/data exposure; thread dump → code paths/synchronization; heap dump → secrets/user data + resource cost; env/config/mappings → internal structure. Place minimal exposure, network controls, authorization, and incident-only access around the board.

**Script:**

Powerful diagnostics concentrate both privileged information and operational cost. Logger controls can increase volume or reveal sensitive events; thread dumps expose execution paths and synchronization state; heap dumps can contain production data and stress the process; other diagnostics reveal internal structure. Authentication alone is not a reason to expose all of them broadly. Use minimal exposure, deliberate network placement, strong authorization, and controlled incident workflows.

**Purpose:**

Restore the complete risk picture across logger, thread, heap, and structural diagnostics so exposure decisions reflect both confidentiality and resource impact.
