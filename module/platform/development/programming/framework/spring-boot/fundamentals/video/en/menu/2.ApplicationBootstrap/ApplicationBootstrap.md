---
video:
  url: ""
---

# Application Bootstrap with SpringApplication

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

## What Is SpringApplication and Why Is It the Boot Entry Point?

<!-- VIDEO_SECTION -->

### Scene 1 — The Java entry point hands off to Boot

**Time:** `00:00–00:55`

**Visual:**

Open a minimal `DemoApplication.java`. Highlight `main(String[] args)`, then highlight only `SpringApplication.run(DemoApplication.class, args)`. Draw an arrow from the JVM to `main`, then from `main` to “Boot application bootstrap”.

**Script:**

“A Spring Boot application still begins with an ordinary Java main method. The Boot-specific handoff is SpringApplication.run. That call receives the primary source and command-line arguments, then takes responsibility for creating and starting the Spring application. Business logic does not belong in this bootstrap line. Think of SpringApplication as the bridge between Java process startup and a running Spring container.”

**Purpose:**

Anchor Boot startup in the familiar JVM `main` model and identify the exact point where Boot bootstrap begins.

## How SpringApplication.run Turns main into an ApplicationContext

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Zoom from the `SpringApplication.run` call into the return type `ConfigurableApplicationContext`.

**Script:**

“The important result of that handoff is not a mysterious Boot runtime. It is a real Spring ApplicationContext.”

**Purpose:**

Bridge the entry-point call to the concrete container it creates.

### Scene 2 — From primary source to running context

**Time:** `01:05–01:55`

**Visual:**

Show the flow `main → SpringApplication.run(primarySource, args) → prepare context → load configuration → refresh → running ApplicationContext`. Then reveal a small code line assigning the return value to `ConfigurableApplicationContext context`.

**Script:**

“The class passed to run is a primary source from which Boot and Spring begin discovering configuration. The arguments become application arguments and can also participate in environment configuration. At a high level, Boot creates the appropriate context, loads configuration, refreshes that context so singleton beans can be created, and returns the running ApplicationContext. You normally do not need to hold the reference yourself; seeing the return type simply proves the result is the Spring container you already know.”

**Purpose:**

Connect `SpringApplication.run` to the creation and refresh of a normal Spring `ApplicationContext`.

## What Happens During Bootstrap at a High Level?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:55–02:05`

**Visual:**

Replace the compact flow with a five-step vertical bootstrap timeline.

**Script:**

“Now we can expand the middle of that arrow just enough to reason about startup failures without memorizing every internal event.”

**Purpose:**

Move from the single bootstrap call to its beginner-level sequence of responsibilities.

### Scene 3 — A coarse bootstrap timeline

**Time:** `02:05–03:00`

**Visual:**

Reveal one item at a time: `primary source + args`; “prepare environment and settings”; “determine broad application type / choose context”; “load configuration sources”; “refresh context and create beans”; “running application”. Place classpath and configuration-input cards beside the timeline as signals.

**Script:**

“At Fundamentals level, bootstrap is a sequence of responsibilities. Boot prepares the environment and settings, determines the broad application type, creates the corresponding context, loads the configuration sources, and refreshes the context. Classpath and configuration inputs influence the result, and auto-configuration can react to those signals while the context is being built. This is enough to ask a first diagnostic question: did the JVM reach main, did SpringApplication begin bootstrap, and did the context refresh successfully?”

**Purpose:**

Give learners a coarse but technically accurate startup timeline they can use for basic diagnosis.

## How Does the Application Move Through Start, Run, and Orderly Stop?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:00–03:10`

**Visual:**

Extend the bootstrap timeline past “running application” to a “shutdown” endpoint.

**Script:**

“Startup is only the first part of the application’s life. The same context also has a running phase and an orderly close.”

**Purpose:**

Carry the bootstrap model into the application’s high-level lifecycle.

### Scene 4 — Start, stay alive, then close the context

**Time:** `03:10–04:05`

**Visual:**

Show a lifecycle diagram: process start → context start → application running → orderly JVM shutdown → context close. Fork the “running” box into “non-web work may complete” and “web server threads keep process alive”.

**Script:**

“After refresh, the application remains alive according to its runtime shape and active non-daemon work. A command-style non-web application may finish its work and naturally become eligible to exit. A web application normally stays alive because the embedded server and its runtime threads continue serving work. During normal JVM shutdown, Boot’s registered shutdown hook closes the context so Spring-managed destruction callbacks can run. This is a lifecycle mental model, not the full event contract.”

**Purpose:**

Explain why a Boot application has a meaningful start/run/stop lifecycle without expanding into detailed runtime events.

## Where Does the Fundamentals Lifecycle Model Hand Off?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:05–04:15`

**Visual:**

Fade the lifecycle diagram into a magnifying-glass icon over the “running” and “transition” phases.

**Script:**

“If you now ask exactly which event fires, which runner executes, or how readiness changes, you have crossed the Fundamentals boundary.”

**Purpose:**

Make the boundary between lifecycle orientation and detailed runtime mechanics explicit.

### Scene 5 — Hand off detailed runtime questions

**Time:** `04:15–05:00`

**Visual:**

Show cards for “events”, “ApplicationRunner / CommandLineRunner”, “availability”, “task execution / virtual threads”, “logging integration”, and “runtime services”, all pointing to `application-runtime`. Keep a simple triage strip at the bottom: before SpringApplication / during refresh / after running.

**Script:**

“Detailed Boot events, runners, availability states, task execution, virtual threads, logging integration, and runtime services belong to application-runtime. Fundamentals only needs one coarse triage model: did the failure happen before SpringApplication, during context bootstrap and refresh, or after the application was already running? Keep that orientation, then use the runtime module for the exact lifecycle vocabulary.”

**Purpose:**

End with a practical handoff that preserves the simple model while routing deeper lifecycle questions correctly.
