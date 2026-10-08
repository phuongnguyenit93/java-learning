<a id="back-to-top"></a>

# Docker Compose development-time integration

## Menu
- [Why Does Boot Integrate Docker Compose for Development?](#compose-development-purpose)
- [How Does Boot Discover and Manage a Compose Project?](#compose-discovery-lifecycle)
- [When Does Boot Create Service Connection Details?](#compose-service-connections)
- [How Do Mapped Ports and Connection-Detail Precedence Affect the Application?](#compose-mapped-ports-precedence)
- [How Does Boot Decide Whether a Compose Service Is Ready?](#compose-readiness)
- [How Do File Selection, Profiles, Skip Controls, and Lifecycle Policies Change Integration?](#compose-runtime-controls)
- [Where Does Docker Compose Development Support Hand Off to Testcontainers Testing?](#compose-testing-boundary)
- [What Remains Generic Docker and Compose Ownership?](#compose-docker-boundary)

## <a id="compose-development-purpose">Why Does Boot Integrate Docker Compose for Development?</a>

<details>
<summary>Click for details</summary>

A developer often needs a database, broker, cache, or other external service just to run the application locally. Spring Boot's Docker Compose support reduces the manual glue between "start the dependencies" and "start the application". The `spring-boot-docker-compose` development dependency can discover a Compose file, start supported services when necessary, and create connection details that Boot auto-configuration consumes.

This is a development-time integration. It is designed to make the local application runtime and its service dependencies start together; deployed applications are not expected to use this support as an infrastructure orchestrator.

The module therefore teaches Boot's discovery, lifecycle, readiness, and service-connection behavior. Docker images, networks, volumes, Compose syntax, and container operations remain in the containerization curriculum.

</details>

- [Back to top](#back-to-top)

---

## <a id="compose-discovery-lifecycle">How Does Boot Discover and Manage a Compose Project?</a>

<details>
<summary>Click for details</summary>

With the Docker Compose support module present, Boot searches the working directory for `compose.yml` and other common Compose filenames. If the project is not already running and lifecycle policy allows it, Boot invokes Docker Compose to start the project. On normal application shutdown, the default policy stops the Compose services it started.

If Boot discovers that the Compose services are already running, it creates supported service connections but does not run `docker compose up` again and does not take ownership of stopping that already-running project.

This ownership rule prevents Boot from casually shutting down infrastructure another process or developer started. When lifecycle behavior surprises you, ask two questions first: did Boot start this Compose project, and what `spring.docker.compose.lifecycle-management` policy is active?

</details>

- [Back to top](#back-to-top)

---

## <a id="compose-service-connections">When Does Boot Create Service Connection Details?</a>

<details>
<summary>Click for details</summary>

For recognized Compose services, Boot creates connection-detail beans that its auto-configuration can consume. A service connection describes how the application reaches a remote service; it is more specific than asking every auto-configuration to rediscover host, port, username, and similar values independently.

Detection normally uses the container image name, and custom images can use the documented `org.springframework.boot.service-connection` label to identify the service type. A container can be excluded from Boot's connection handling with the supported ignore label.

Service connections are an integration contract, not a replacement for generic Compose configuration. Boot only creates connection details for technologies it supports; other containers can still run in the Compose project without becoming Boot-managed service connections.

</details>

- [Back to top](#back-to-top)

---

## <a id="compose-mapped-ports-precedence">How Do Mapped Ports and Connection-Detail Precedence Affect the Application?</a>

<details>
<summary>Click for details</summary>

Compose commonly maps a fixed container port to an ephemeral or otherwise different host port. Boot's Docker Compose service connection uses the *mapped host port* so the application connects to the address that is actually reachable from the local JVM.

When a service connection is available, its connection details take precedence over ordinary connection-related configuration properties for the corresponding auto-configuration. That priority is intentional: a locally started container may receive a dynamic host port that static application configuration cannot know in advance.

If the application connects to an unexpected port, inspect the generated service connection and current Compose port mapping before editing datasource/client properties. Otherwise you can end up fighting the higher-priority runtime connection details.

</details>

- [Back to top](#back-to-top)

---

## <a id="compose-readiness">How Does Boot Decide Whether a Compose Service Is Ready?</a>

<details>
<summary>Click for details</summary>

Starting a container process does not guarantee that the service inside it is ready. Boot therefore waits for Compose services before considering the development integration ready. The preferred signal is a Compose `healthcheck` when one is defined. Without one, Boot can fall back to testing whether a TCP connection can be made to the mapped port.

The TCP readiness check can be disabled per container with Boot's documented Compose label, and connection/read timeout properties can adjust the check. There is also an overall readiness timeout.

These controls coordinate startup; they do not define the service's business-level health semantics. If a dependency requires a stronger readiness condition than "port accepts TCP", express that condition in the service's Compose healthcheck rather than teaching Boot generic service internals.

</details>

- [Back to top](#back-to-top)

---

## <a id="compose-runtime-controls">How Do File Selection, Profiles, Skip Controls, and Lifecycle Policies Change Integration?</a>

<details>
<summary>Click for details</summary>

Boot exposes runtime controls for cases where the default Compose behavior is not the desired local workflow. `spring.docker.compose.file` selects a nonstandard file, `spring.docker.compose.profiles.active` activates Compose profiles, and `spring.docker.compose.lifecycle-management` chooses whether Boot starts/stops the project.

The lifecycle values in Boot 3.3 are `none`, `start-only`, and `start-and-stop`. Start/stop command options and timeout settings further adjust how Boot invokes the Compose CLI. Tests skip Docker Compose support by default unless explicitly enabled.

Use these controls to define *Boot's relationship* with an existing Compose project. Defining networks, volumes, build contexts, image healthchecks, or deployment topology remains the Compose/Docker owner's job.

### References

- [Spring Boot 3.3 — Development-time Services: Docker Compose](https://docs.spring.io/spring-boot/3.3/reference/features/dev-services.html#features.dev-services.docker-compose)

</details>

- [Back to top](#back-to-top)

---

## <a id="compose-testing-boundary">Where Does Docker Compose Development Support Hand Off to Testcontainers Testing?</a>

<details>
<summary>Click for details</summary>

Spring Boot can use Docker Compose while developing and can also integrate Testcontainers for tests, but the learning purposes differ. This chapter owns the development-time Compose lifecycle attached to a locally running application.

Boot disables Docker Compose support in tests by default. It can be enabled explicitly, but the repository's dedicated `testing` module owns Boot's Testcontainers service connections and the design of repeatable automated integration tests.

The distinction is useful even when both approaches start the same database image. Development Compose optimizes a developer's local runtime workflow; Testcontainers testing optimizes isolated, test-controlled dependencies and reproducible test lifecycles. Choose based on the lifecycle owner, not only on the container technology.

</details>

- [Back to top](#back-to-top)

---

## <a id="compose-docker-boundary">What Remains Generic Docker and Compose Ownership?</a>

<details>
<summary>Click for details</summary>

Boot invokes Docker Compose and interprets supported service metadata, but Docker remains an external runtime. Image construction, Dockerfiles, layers, registries, networks, volumes, Compose merge semantics, resource limits, container security, and production orchestration are generic container concerns.

This boundary also separates Boot's development Compose support from Boot image production. Building an OCI image with `bootBuildImage` or Cloud Native Buildpacks belongs to `build-tooling-packaging`, while authoring generic Dockerfiles belongs to containerization.

When the Compose CLI itself fails, inspect Docker/Compose first. When Compose succeeds but Boot does not create the expected connection details or lifecycle behavior, return to this application-runtime integration layer.

</details>

- [Back to top](#back-to-top)
