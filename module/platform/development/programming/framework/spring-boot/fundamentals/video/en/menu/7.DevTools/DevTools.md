---
video:
  url: ""
---

# Spring Boot DevTools

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

## Why Does Spring Boot DevTools Exist?

<!-- VIDEO_SECTION -->

### Scene 1 — Shorten the edit-to-observe loop

**Time:** `00:00–00:55`

**Visual:**

Show a developer loop around an IDE and terminal: `edit → build/update classpath → restart/refresh → observe`. Place `spring-boot-devtools` beside the loop with three callouts: automatic restart, LiveReload, development-time property defaults. Keep “business behavior” outside the DevTools box.

**Script:**

“Spring Boot DevTools is an optional development-time module. Its job is not to add business capability. Its job is to shorten the feedback loop between editing code and observing the result. Automatic restart can refresh the application after classpath changes, LiveReload can refresh a browser for relevant resource changes, and development-friendly property defaults can make edits easier to see. Remove DevTools and the application’s business behavior should remain.”

**Purpose:**

Frame DevTools as local development feedback support rather than a runtime requirement of the application.

## How Does Automatic Restart Use Two ClassLoaders?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Zoom into the “automatic restart” callout and split the runtime into two stacked classloader layers.

**Script:**

“The reason DevTools restart can be faster than a cold JVM restart is a two-classloader model.”

**Purpose:**

Move from DevTools’ overall purpose to the mechanism that explains restart behavior.

### Scene 2 — Base loader stays, restart loader is replaced

**Time:** `01:05–02:05`

**Visual:**

Draw `base classloader → stable dependency jars` and `restart classloader → application classes being developed`. Animate a classpath update: discard the restart loader, create a new one, keep the base loader. Then show the ApplicationContext restarting against the new restart loader.

**Script:**

“DevTools separates relatively stable dependencies from classes you are actively changing. Regular third-party jars typically live in a base classloader. Application classes being developed live in a restart classloader. When DevTools sees a relevant classpath update, it discards and recreates the restart classloader while keeping the populated base loader. The application context is restarted against the new loader, which is usually cheaper than rebuilding the entire process from scratch. This model also explains why classloader-identity assumptions can surface unusual issues, especially in multi-module projects.”

**Purpose:**

Explain both the performance benefit and the diagnostic significance of DevTools’ two-classloader restart model.

## How Do LiveReload and Development-Time Property Defaults Help?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:05–02:15`

**Visual:**

Move from Java-class changes to a browser showing a template/static resource edit.

**Script:**

“Not every edit needs an application restart. Resource changes have a different feedback path.”

**Purpose:**

Distinguish restart from browser/resource feedback before introducing LiveReload and property defaults.

### Scene 3 — Different tools for different feedback delays

**Time:** `02:15–03:10`

**Visual:**

Show three parallel lanes: classpath change → restart context; resource change → LiveReload server → compatible browser extension refresh; development defaults → cache/property adjustments. Display `spring.thymeleaf.cache=false`, `spring.devtools.add-properties=false`, and `spring.devtools.livereload.enabled=false` as concrete property examples.

**Script:**

“LiveReload solves a different problem from restart. DevTools can run an embedded LiveReload server that signals a compatible browser extension when resources change, so the browser can refresh automatically. DevTools also applies development-time property defaults; for supported integrations that can include disabling caches that would otherwise hide edits. These defaults are still controllable. You can disable the extra DevTools property set or disable the LiveReload server explicitly.”

**Purpose:**

Separate classpath restart, browser refresh, and development-friendly defaults while showing that each remains configurable.

## How Do You Add DevTools and Control Restart Scope?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:10–03:20`

**Visual:**

Return to the IDE and Gradle build file, with the runtime classpath highlighted.

**Script:**

“For restart to work, DevTools must be present for development and the edited content must actually reach the runtime classpath.”

**Purpose:**

Bridge mechanism understanding into correct local setup and evidence.

### Scene 4 — Development-only dependency and classpath-visible changes

**Time:** `03:20–04:20`

**Visual:**

Show `developmentOnly 'org.springframework.boot:spring-boot-devtools'`. Edit one Java source file, then animate “save source” without class output and show “no classpath change yet”. Next trigger an IDE build and show updated class output followed by a restart log indicator. Then reveal `spring.devtools.restart.additional-paths=../infrastructure/src/main/java` for a multi-module example.

**Script:**

“With Gradle, DevTools belongs in the developmentOnly configuration. One subtle point is that saving a source file is not universally enough: DevTools watches classpath directories, so the change must be compiled or copied into the runtime classpath. An IDE build can create that update; auto-build can automate it when configured appropriately. In multi-module or unusual layouts, additional paths can be watched. Include and exclude rules can also refine restart triggers or classloader placement. The evidence is straightforward: make a classpath-visible change and verify that a restart is reported.”

**Purpose:**

Show the causal chain from dependency setup through actual classpath update to observable restart, including the multi-module path case.

## What Is Remote DevTools and Why Is It Security-Sensitive?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:20–04:30`

**Visual:**

Move the local IDE and application apart, placing the application on a remote machine with a network line between them.

**Script:**

“DevTools also has a remote workflow, but the moment development support crosses a network boundary, security becomes the first concern.”

**Purpose:**

Shift from local development mechanics to the exceptional and security-sensitive remote mode.

### Scene 5 — Remote support is opt-in, exceptional, and not for production

**Time:** `04:30–05:25`

**Visual:**

Show “local client ↔ remote application with DevTools included” and `spring.devtools.remote.secret`. Place a large boundary card: “trusted network or SSL; never enable on production deployment”. Add a final note: “Spring Boot 3.3: remote DevTools not supported for WebFlux applications”.

**Script:**

“Remote DevTools is opt-in. The remotely running application must intentionally include DevTools and configure a remote secret so a local client can participate in the development workflow. That makes remote support an exception to the normal packaged-artifact model. Spring Boot explicitly warns that it can create a security risk: use it only on a trusted network or protect it with SSL, and never enable it on a production deployment. It is not a general deployment protocol. Also note the Spring Boot 3.3 boundary: remote DevTools is not available for Spring WebFlux applications.”

**Purpose:**

Make the remote-development security boundary more memorable than the convenience feature itself.

## How Does DevTools Differ from Production Operations Tooling?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:25–05:35`

**Visual:**

Replace the network diagram with a side-by-side DevTools versus Actuator table.

**Script:**

“That security boundary points to the final distinction: development feedback and production operations are different jobs.”

**Purpose:**

Carry the environment boundary into the DevTools-versus-Actuator mental model.

### Scene 6 — Developer feedback is not operational monitoring

**Time:** `05:35–06:20`

**Visual:**

Left column: DevTools → restart, LiveReload, development property defaults, normally excluded/disabled for packaged production use. Right column: Actuator → health, metrics integration, loggers, management/diagnostic endpoints, exposure/security decisions.

**Script:**

“DevTools helps answer, ‘How can I see the result of my edit sooner?’ Actuator helps answer, ‘What is the state of this running application in an operational environment?’ A fast local restart is not health monitoring, and LiveReload is not a production management endpoint. Keep DevTools in the development feedback loop. When the question moves to operational state, continue with the Actuator module.”

**Purpose:**

Close with a durable separation between development tooling and production-ready operational surfaces.
