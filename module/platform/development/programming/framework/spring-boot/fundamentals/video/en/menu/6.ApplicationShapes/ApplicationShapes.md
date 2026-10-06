---
video:
  url: ""
---

# Application Shapes and the Embedded Server Mental Model

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

## What Application Shapes Can Spring Boot Start?

<!-- VIDEO_SECTION -->

### Scene 1 — Boot does not imply “web server”

**Time:** `00:00–00:55`

**Visual:**

Show `SpringApplication` in the center branching to three boxes: “non-web → plain ApplicationContext”, “Servlet web → Servlet web context + server integration”, “reactive web → reactive web context + reactive server integration”.

**Script:**

“Spring Boot can start more than one kind of application. The same SpringApplication abstraction can create a non-web context, a Servlet-based web application, or a reactive web application depending on classpath signals and explicit application settings. That corrects a common beginner shortcut: Boot does not mean ‘web server’. Boot is an application bootstrap and integration layer; a server appears only when the application has a web runtime shape.”

**Purpose:**

Establish the three high-level application shapes and remove the assumption that every Boot application is web-based.

## How Do Non-Web, Servlet, and Reactive Shapes Differ at a High Level?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Expand the three shape boxes into a side-by-side comparison.

**Script:**

“The next step is to attach just enough meaning to those three labels without drifting into MVC or reactive-programming internals.”

**Purpose:**

Move from names of shapes to their high-level runtime distinctions.

### Scene 2 — Three context/runtime orientations

**Time:** `01:05–02:00`

**Visual:**

Show three columns. Non-web: batch/command/background work, no HTTP listener. Servlet: Spring MVC label, Servlet-capable context. Reactive: Spring WebFlux label, reactive web context. Add a note: “If MVC and WebFlux are both present, MVC wins by default when Boot infers type.”

**Script:**

“A non-web application still gets Boot bootstrap, configuration, dependency injection, and other Spring integrations, but it does not need to listen for HTTP traffic. A Servlet application uses the Servlet web stack, commonly Spring MVC. A reactive application uses the reactive web stack, commonly Spring WebFlux. When Boot infers the type from the classpath and both MVC and WebFlux are present, MVC takes precedence by default. These labels describe application context and runtime shape, not the detailed request-processing model.”

**Purpose:**

Give each shape a useful mental model while keeping MVC/WebFlux mechanics in their owning curricula.

## What Does an Embedded Server Mean in a Boot Application?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:10`

**Visual:**

Keep the Servlet column and zoom into the “server integration” portion.

**Script:**

“For the web shapes, one Boot idea is especially visible: the server can live inside the same application process.”

**Purpose:**

Carry the shape model into the embedded-server mental model.

### Scene 3 — Server and context share the application process

**Time:** `02:10–03:00`

**Visual:**

Draw a process tree: `java process → Boot application → Spring ApplicationContext + embedded Servlet web server`. Then show `java -jar application.jar` starting that single process. Add a small note: “Boot integrates a supported server implementation; Boot does not invent the HTTP server.”

**Script:**

“Embedded means the HTTP server is launched and managed as part of the application process rather than requiring deployment into an externally operated server as the only model. A typical executable Servlet application contains the Spring ApplicationContext and the embedded server integration in the same Java process. Boot coordinates the server with the context lifecycle, but the server implementation itself is a supported server that Boot integrates. Ports, connectors, TLS, proxies, and graceful shutdown belong to web-runtime.”

**Purpose:**

Explain embedded-server integration at the process level without expanding into server configuration details.

## How Can a Web Starter Change the Runtime Shape?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:00–03:10`

**Visual:**

Return to a dependency tree for a non-web project and animate a web starter being added.

**Script:**

“Now connect the server model back to an earlier signal: changing dependencies can change which runtime shape is available.”

**Purpose:**

Link application shape to classpath-driven capability changes.

### Scene 4 — Dependency change, capability change, possible shape change

**Time:** `03:10–04:05`

**Visual:**

Animate `add starter → new libraries/classes → SpringApplication sees web capability → web context becomes eligible`. In parallel, show “auto-configuration may contribute embedded-server infrastructure”. End with a debugging card: “unexpected server? inspect dependency tree/classpath first”.

**Script:**

“Adding a web starter changes the classpath. That can make a project that previously had no web stack eligible for a web application context. SpringApplication uses those classpath signals when determining application type, while auto-configuration can contribute matching server infrastructure during context construction. The reverse is a useful debugging rule: if a supposedly non-web application unexpectedly starts a server, inspect the dependency tree and classpath for a web stack before assuming a server property is the root cause.”

**Purpose:**

Show a concrete classpath-to-runtime-shape relationship while preserving the SpringApplication versus auto-configuration responsibility split.

## Where Does the Embedded-Server Overview Hand Off?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:05–04:15`

**Visual:**

Place a boundary around the simple process diagram and reveal deeper server questions outside it.

**Script:**

“The Fundamentals model tells you that a server can be present and why the classpath can make that possible. It does not own the detailed server decision tree.”

**Purpose:**

Mark the point where the embedded-server mental model hands off to web-runtime.

### Scene 5 — Keep the transferable model, route server details

**Time:** `04:15–05:00`

**Visual:**

Show cards for “server implementation selection”, `server.*` configuration, SSL/TLS, forwarded headers/proxies, and graceful shutdown, all pointing to `web-runtime`. Keep a footer: “Fundamentals: non-web vs web; server inside process; classpath influences available shape”.

**Script:**

“Detailed web-application detection, embedded-server auto-configuration, server selection and customization, TLS, proxy handling, and graceful shutdown belong to web-runtime. The transferable Fundamentals model is smaller: a Boot application may be non-web or web; a web runtime can live inside the same process; and dependency and classpath choices can influence which shape is available. Carry that model forward and let web-runtime own the server mechanics.”

**Purpose:**

Close with a precise scope boundary and a reusable application-shape mental model.
