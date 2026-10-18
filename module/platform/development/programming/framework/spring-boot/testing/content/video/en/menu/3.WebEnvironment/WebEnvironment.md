---
video:
  url: ""
---

# `WebEnvironment` and Real-Server Test Boundaries

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## What Does `WebEnvironment` Control?

<!-- VIDEO_SECTION -->

### Scene 1 — What Does `WebEnvironment` Control?

**Time:** `00:00–00:42`

**Visual:**

Show a four-column `WebEnvironment` matrix: `MOCK`, `RANDOM_PORT`, `DEFINED_PORT`, `NONE`, with context type, server state, and port behavior.

**Script:**

`SpringBootTest.WebEnvironment` controls the kind of web context used by a full Boot test and whether an embedded server is started. The four values are `MOCK`, `RANDOM_PORT`, `DEFINED_PORT`, and `NONE`. This is a test bootstrap choice, not a web-framework API. It decides how Boot establishes the environment in which MVC or WebFlux infrastructure will be tested.

**Purpose:**

Define `WebEnvironment` as the control that decides whether a full Boot test stays inside a mock web context or crosses a real server boundary.

## What Does `WebEnvironment.MOCK` Provide Without Starting a Server?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:42–00:57`

**Visual:**

Extend the existing lifecycle/timeline into the next phase instead of resetting the diagram: zoom into `MOCK`: web `ApplicationContext` present, no bound port. Branch to `MockMvc` for MVC and mocked `WebTestClient` for WebFlux; mark mocked `WebTestClient` as WebFlux-only in Boot 3.3.

**Script:**

`WebEnvironment` names the boundary; start with `MOCK` to see what a web-capable context provides before any socket is opened.

**Purpose:**

Bridge from the four WebEnvironment modes into the no-server path while pairing MVC with MockMvc and WebFlux with mocked WebTestClient.

### Scene 1 — What Does `WebEnvironment.MOCK` Provide Without Starting a Server?

**Time:** `00:57–01:48`

**Visual:**

Zoom into `MOCK`: web `ApplicationContext` present, no bound port. Branch to `MockMvc` for MVC and mocked `WebTestClient` for WebFlux; mark mocked `WebTestClient` as WebFlux-only in Boot 3.3.

**Script:**

`MOCK` is the default. When a supported web stack is present, Boot loads a web `ApplicationContext` with a mock web environment but does not start the embedded server. If the classpath has no web environment, Boot falls back to a normal non-web context. On MVC, use `MockMvc` for mock request processing. On WebFlux, Boot can auto-configure `WebTestClient` against the mocked reactive application. In Boot 3.3, mocked `WebTestClient` support belongs to the WebFlux path rather than being the MVC counterpart to `MockMvc`.

**Purpose:**

Make `MOCK` concrete: web infrastructure is available without binding a network port, so request testing remains inside the application process.

## How Do `RANDOM_PORT` and `DEFINED_PORT` Start a Real Embedded Server?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:48–02:03`

**Visual:**

Extend the existing lifecycle/timeline into the next phase instead of resetting the diagram: move to two live-server lanes: `RANDOM_PORT` binds an available port and exposes `@LocalServerPort`; `DEFINED_PORT` binds the configured/default port.

**Script:**

Once the no-server case is clear, contrast it with `RANDOM_PORT` and `DEFINED_PORT`, where the request crosses a real embedded server.

**Purpose:**

Contrast the no-server path with a live embedded server so client and transaction consequences have a concrete boundary.

### Scene 1 — How Do `RANDOM_PORT` and `DEFINED_PORT` Start a Real Embedded Server?

**Time:** `02:03–02:51`

**Visual:**

Move to two live-server lanes: `RANDOM_PORT` binds an available port and exposes `@LocalServerPort`; `DEFINED_PORT` binds the configured/default port.

**Script:**

Both `RANDOM_PORT` and `DEFINED_PORT` load a `WebServerApplicationContext` and start the embedded web server. `RANDOM_PORT` asks the server to listen on an available port; `DEFINED_PORT` uses the configured application port or the normal default. `RANDOM_PORT` is typically safer for automated suites because parallel runs do not compete for one fixed port. The actual port can be injected with `@LocalServerPort` when a custom client needs it.

**Purpose:**

Show that `RANDOM_PORT` and `DEFINED_PORT` create a real embedded-server boundary and therefore change clients, threading, and runtime observations.

## When Does `WebEnvironment.NONE` Fit?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:51–03:06`

**Visual:**

Extend the existing lifecycle/timeline into the next phase instead of resetting the diagram: remove the web-server and mock-web nodes while keeping `SpringApplication` → ordinary `ApplicationContext`, showing Boot configuration without Servlet/Reactive web infrastructure.

**Script:**

Real-server modes are not always needed, so `NONE` completes the model by showing a full Boot context with web infrastructure intentionally disabled.

**Purpose:**

Complete the WebEnvironment model with the deliberate non-web case before moving on to clients that require a running server.

### Scene 1 — When Does `WebEnvironment.NONE` Fit?

**Time:** `03:06–03:52`

**Visual:**

Remove the web-server and mock-web nodes while keeping `SpringApplication` → ordinary `ApplicationContext`, showing Boot configuration without Servlet/Reactive web infrastructure.

**Script:**

`NONE` still boots the application through `SpringApplication` but configures no web environment. It is useful when the test needs full Boot configuration and auto-configuration while deliberately excluding Servlet or reactive web runtime concerns. For example, a command-line application, scheduler, batch-oriented component, or configuration integration test may want Boot startup fidelity without allocating mock web infrastructure or a server.

**Purpose:**

Explain `NONE` as the deliberate non-web full-context option, useful when Boot configuration matters but web infrastructure should be absent.

## How Do Boot-Provided Test Clients Fit a Running-Server Test?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:52–04:07`

**Visual:**

Extend the existing lifecycle/timeline into the next phase instead of resetting the diagram: draw client → live-server arrows: `WebTestClient` when WebFlux is available and `TestRestTemplate` as the REST-style alternative; keep `WebEnvironment` as the control that created the server.

**Script:**

After choosing a running-server mode, the next practical concern is which Boot-provided client can drive that boundary.

**Purpose:**

Move from server creation to the client that crosses that server boundary, keeping environment choice separate from client convenience.

### Scene 1 — How Do Boot-Provided Test Clients Fit a Running-Server Test?

**Time:** `04:07–04:58`

**Visual:**

Draw client → live-server arrows: `WebTestClient` when WebFlux is available and `TestRestTemplate` as the REST-style alternative; keep `WebEnvironment` as the control that created the server.

**Script:**

For a real-server test, Boot can supply a `WebTestClient` that resolves relative URLs against the running server. If WebFlux is not available or should not be added for test-client use, Boot also supplies `TestRestTemplate` for REST-style calls. These clients are conveniences around the chosen web environment. They do not decide whether the server exists; `WebEnvironment` does. Client assertion and request APIs belong to their respective Spring testing facilities.

**Purpose:**

Match Boot-provided clients to real-server tests so the client is chosen from the same boundary the test is proving.

## Why Does a Real-Server Test Change Transaction Rollback Expectations?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:58–05:13`

**Visual:**

Extend the existing lifecycle/timeline into the next phase instead of resetting the diagram: split test thread and server thread. Put the test-managed transaction on the left and the application transaction on the right, then show only the test-thread transaction rolling back automatically.

**Script:**

A client crossing the server also crosses threads, which is why transaction rollback expectations change in real-server tests.

**Purpose:**

Use the thread split to explain why server-side commits sit outside the test-managed rollback.

### Scene 1 — Why Does a Real-Server Test Change Transaction Rollback Expectations?

**Time:** `05:13–06:06`

**Visual:**

Split test thread and server thread. Put the test-managed transaction on the left and the application transaction on the right, then show only the test-thread transaction rolling back automatically.

**Script:**

With `RANDOM_PORT` or `DEFINED_PORT`, the test client and the server process a request on different threads. A test-managed `@Transactional` transaction therefore does not automatically contain the transaction started by application code on the server side. The transaction around the test method can still roll back its own work, but changes committed by the server may remain. The exact semantics of test-managed transactions belong to Spring TestContext; the Boot-specific lesson is that starting a real server crosses that transaction boundary.

**Purpose:**

Expose the transaction-boundary consequence of real-server tests: test-thread rollback does not automatically roll back server-thread work.

## Where Does Testing Ownership Hand Off to Web Runtime and Spring Web Testing?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:06–06:18`

**Visual:**

Keep the ownership lanes visible and move the next responsibility across the correct boundary: use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

That transaction difference exposes the final boundary: Boot configures the environment, while web runtime and Spring web testing own the deeper mechanics.

**Purpose:**

Finish the current mechanism by assigning the next responsibility to the framework or library that actually owns it.

### Scene 1 — Where Does Testing Ownership Hand Off to Web Runtime and Spring Web Testing?

**Time:** `06:18–07:09`

**Visual:**

Use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

On the Boot side, the owned responsibility is the choice of `WebEnvironment`, Boot-supplyd running-server clients, and how those facilities integrate with a Boot test context. Production server selection, ports, TLS, forwarded headers, and other server runtime behavior belong to the Spring Boot web-runtime module. `MockMvc`, Spring MVC test APIs, `WebTestClient` request/assertion mechanics, and generic web-test framework behavior belong to Spring Framework testing. This module uses those tools without redefining them.

**Purpose:**

Mark the ownership handoff from Boot's web-environment setup to web runtime and Spring web-testing mechanics so later failures are debugged in the right layer.
