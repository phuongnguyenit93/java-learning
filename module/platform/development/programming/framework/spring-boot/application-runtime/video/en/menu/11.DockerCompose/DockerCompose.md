---
video:
  url: ""
---

# Docker Compose development-time integration

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

## Why Does Boot Integrate Docker Compose for Development?

<!-- VIDEO_SECTION -->

### Scene 1 — Why Does Boot Integrate Docker Compose for Development?

**Time:** `00:00–00:53`

**Visual:**

Progressive reveal on the chapter visual: show a developer laptop with Boot app plus database/broker/cache in Compose and stamp the flow DEVELOPMENT ONLY.

**Script:**

In local development, Spring Boot's Docker Compose support reduces the manual glue between "start the dependencies" and "start the application". Local development often needs a database, broker, cache, or other external service just to run the application locally. The `spring-boot-docker-compose` development dependency can discover a Compose file, start supported services when necessary, and create connection details that Boot auto-configuration consumes. This is a development-time integration. It is designed to make the local application runtime and its service dependencies start together; deployed applications are not expected to use this support as an infrastructure orchestrator. The module therefore teaches Boot's discovery, lifecycle, readiness, and service-connection behavior.

**Purpose:**

Frame Boot Docker Compose support as a development-time runtime integration rather than a production infrastructure orchestrator.


## How Does Boot Discover and Manage a Compose Project?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:53–01:07`

**Visual:**

Keep the local app ↔ service connection ↔ Compose topology visible and move the callout through lifecycle ownership, connection details, mapped ports, readiness, controls, and boundaries.

**Script:**

The development integration starts with lifecycle ownership: Boot must discover the Compose project and know whether it or someone else started it before deciding what it may stop.

**Purpose:**

Turn the development-time motivation into the lifecycle-ownership rule that controls whether Boot may start or stop a Compose project.

### Scene 2 — How Does Boot Discover and Manage a Compose Project?

**Time:** `01:07–02:06`

**Visual:**

Progressive reveal on the chapter visual: branch project discovery into “already running” versus “Boot starts it”, and assign stop ownership only to the latter.

**Script:**

At project discovery, if the project is not already running and lifecycle policy allows it, Boot invokes Docker Compose to start the project. With Docker Compose support present, Boot searches the working directory for `compose.yml` and other common Compose filenames. On normal application shutdown, the default policy stops the Compose services it started. If Boot discovers that the Compose services are already running, it creates supported service connections but does not run `docker compose up` again and does not take ownership of stopping that already-running project. This ownership rule prevents Boot from casually shutting down infrastructure another process or developer started. When lifecycle behavior surprises you, ask two questions first: did Boot start this Compose project, and what `spring.docker.compose.lifecycle-management` policy is active?

**Purpose:**

Make start/stop ownership explicit so Boot does not stop a Compose project it discovered already running.


## When Does Boot Create Service Connection Details?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:06–02:16`

**Visual:**

Keep the local app ↔ service connection ↔ Compose topology visible and move the callout through lifecycle ownership, connection details, mapped ports, readiness, controls, and boundaries.

**Script:**

Once the project is running, Boot’s value is turning recognized services into connection details that auto-configuration can consume.

**Purpose:**

Move from project lifecycle to the supported service metadata Boot converts into application connection details.

### Scene 3 — When Does Boot Create Service Connection Details?

**Time:** `02:16–03:13`

**Visual:**

Progressive reveal on the chapter visual: turn supported image/label metadata into typed service-connection details while unsupported containers remain unbound.

**Script:**

For a recognized service, a service connection describes how the application reaches a remote service; it is more specific than asking every auto-configuration to rediscover host, port, username, and similar values independently. For services Boot recognizes, Boot creates connection-detail beans that its auto-configuration can consume. Detection normally uses the container image name, and custom images can use the documented `org.springframework.Boot.service-connection` label to identify the service type. A container can be excluded from Boot's connection handling with the supported ignore label. Service connections are an integration contract, not a replacement for generic Compose configuration. Boot only creates connection details for technologies it supports; other containers can still run in the Compose project without becoming Boot-managed service connections.

**Purpose:**

Show when recognized Compose services become Boot service-connection beans and when unsupported containers remain merely part of the project.


## How Do Mapped Ports and Connection-Detail Precedence Affect the Application?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:13–03:26`

**Visual:**

Keep the local app ↔ service connection ↔ Compose topology visible and move the callout through lifecycle ownership, connection details, mapped ports, readiness, controls, and boundaries.

**Script:**

Those connection details matter because the host-facing port can differ from the container port, and Boot’s generated details take precedence over static connection properties.

**Purpose:**

Explain why those connection details matter by connecting them to mapped host ports and configuration precedence.

### Scene 4 — How Do Mapped Ports and Connection-Detail Precedence Affect the Application?

**Time:** `03:26–04:25`

**Visual:**

Progressive reveal on the chapter visual: draw container port → mapped host port → service connection → client auto-configuration and cross out conflicting static properties.

**Script:**

At the mapped-port boundary, Boot's Docker Compose service connection uses the mapped host port so the application connects to the address that is actually reachable from the local JVM. Compose often maps a fixed container port to an ephemeral or otherwise different host port. When a service connection is available, its connection details take precedence over ordinary connection-related configuration properties for the corresponding auto-configuration. That priority is intentional: a locally started container may receive a dynamic host port that static application configuration cannot know in advance. If the application connects to an unexpected port, inspect the generated service connection and current Compose port mapping before editing datasource/client properties. Otherwise you can end up fighting the higher-priority runtime connection details.

**Purpose:**

Explain why mapped host ports and service-connection precedence can override static client properties in local development.


## How Does Boot Decide Whether a Compose Service Is Ready?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:25–04:38`

**Visual:**

Keep the local app ↔ service connection ↔ Compose topology visible and move the callout through lifecycle ownership, connection details, mapped ports, readiness, controls, and boundaries.

**Script:**

A reachable port still does not prove the service is ready, so the next layer is healthcheck/TCP readiness and the timeout controls around it.

**Purpose:**

Distinguish a started container from a ready service before introducing healthcheck and TCP readiness behavior.

### Scene 5 — How Does Boot Decide Whether a Compose Service Is Ready?

**Time:** `04:38–05:25`

**Visual:**

Progressive reveal on the chapter visual: animate container started ≠ service ready; show healthcheck first, TCP fallback and timeout controls second.

**Script:**

Before a dependency is usable, Boot therefore waits for Compose services before considering the development integration ready. A started container process does not guarantee that the service inside it is ready. The preferred signal is a Compose `healthcheck` when one is defined. Without one, Boot can fall back to testing whether a TCP connection can be made to the mapped port. The TCP readiness check can be disabled per container with Boot's documented Compose label, and connection/read timeout properties can adjust the check. There is also an overall readiness timeout.

**Purpose:**

Distinguish container process start from service readiness and show the healthcheck/TCP fallback boundary.


## How Do File Selection, Profiles, Skip Controls, and Lifecycle Policies Change Integration?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:25–05:37`

**Visual:**

Keep the local app ↔ service connection ↔ Compose topology visible and move the callout through lifecycle ownership, connection details, mapped ports, readiness, controls, and boundaries.

**Script:**

Default discovery and lifecycle behavior will not fit every local workflow, which is why file selection, profiles, skip controls, and lifecycle policies come next.

**Purpose:**

Introduce runtime controls only after the default discovery/readiness behavior is clear, so each property has a concrete reason to exist.

### Scene 6 — How Do File Selection, Profiles, Skip Controls, and Lifecycle Policies Change Integration?

**Time:** `05:37–06:22`

**Visual:**

Progressive reveal on the chapter visual: surround the project with file/profile/lifecycle/command-timeout/test-skip controls.

**Script:**

When defaults do not fit, `spring.docker.compose.file` selects a nonstandard file, `spring.docker.compose.profiles.active` activates Compose profiles, and `spring.docker.compose.lifecycle-management` chooses whether Boot starts/stops the project. Boot exposes controls for cases where the default Compose behavior is not the desired local workflow. The lifecycle values in Boot 3.3 are `none`, `start-only`, and `start-and-stop`. Start/stop command options and timeout settings further adjust how Boot invokes the Compose CLI. Tests skip Docker Compose support by default unless explicitly enabled. Use these controls to define Boot's relationship with an existing Compose project.

**Purpose:**

Teach the runtime controls that change Boot’s relationship to a Compose project without teaching generic Compose topology.


## Where Does Docker Compose Development Support Hand Off to Testcontainers Testing?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:22–06:34`

**Visual:**

Keep the local app ↔ service connection ↔ Compose topology visible and move the callout through lifecycle ownership, connection details, mapped ports, readiness, controls, and boundaries.

**Script:**

Development Compose and automated tests may start the same image but have different lifecycle owners, so now split local runtime support from Testcontainers testing.

**Purpose:**

Separate developer runtime dependencies from test-owned dependencies before handing automated testing to Testcontainers.

### Scene 7 — Where Does Docker Compose Development Support Hand Off to Testcontainers Testing?

**Time:** `06:34–07:25`

**Visual:**

Progressive reveal on the chapter visual: place Development Compose and Testcontainers testing side by side with different lifecycle-owner badges.

**Script:**

At the testing handoff, this chapter covers the development-time Compose lifecycle attached to a locally running application. Boot can use Docker Compose while developing and can also integrate Testcontainers for tests, but the learning purposes differ. Boot disables Docker Compose support in tests by default. It can be enabled explicitly, but the repository's dedicated `testing` module owns Boot's Testcontainers service connections and the design of repeatable automated integration tests. The distinction is useful even when both approaches start the same database image. Development Compose optimizes a developer's local runtime workflow; Testcontainers testing optimizes isolated, test-controlled dependencies and reproducible test lifecycles.

**Purpose:**

Separate developer-owned Compose lifecycle from test-owned Testcontainers lifecycle even when both use the same service image.


## What Remains Generic Docker and Compose Ownership?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:25–07:37`

**Visual:**

Keep the local app ↔ service connection ↔ Compose topology visible and move the callout through lifecycle ownership, connection details, mapped ports, readiness, controls, and boundaries.

**Script:**

After Boot’s discovery, service connections, readiness, and lifecycle are accounted for, image building, networks, volumes, and production orchestration remain generic Docker/Compose concerns.

**Purpose:**

Close the chapter at the point where Boot-specific integration ends and generic Docker/Compose ownership begins.

### Scene 8 — What Remains Generic Docker and Compose Ownership?

**Time:** `07:37–08:29`

**Visual:**

Progressive reveal on the chapter visual: move Dockerfile/images/networks/volumes/security/production orchestration outside the Boot integration box.

**Script:**

At the generic Docker boundary, image construction, Dockerfiles, layers, registries, networks, volumes, Compose merge semantics, resource limits, container security, and production orchestration are generic container concerns. Boot invokes Compose and interprets supported service metadata, but Docker remains an external runtime. This boundary also separates Boot's development Compose support from Boot image production. Building an OCI image with `bootBuildImage` or Cloud Native Buildpacks belongs to `build-tooling-packaging`, while authoring generic Dockerfiles belongs to containerization. When the Compose CLI itself fails, inspect Docker/Compose first. When Compose succeeds but Boot does not create the expected connection details or lifecycle behavior, return to this application-runtime integration layer.

**Purpose:**

End Boot ownership at Compose invocation/metadata and route Dockerfiles, images, networks, volumes, security, and production orchestration to container owners.
