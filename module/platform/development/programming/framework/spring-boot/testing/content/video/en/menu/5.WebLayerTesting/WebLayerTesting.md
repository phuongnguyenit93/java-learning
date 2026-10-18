---
video:
  url: ""
---

# Focused Web Testing with `@WebMvcTest` and `@WebFluxTest`

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## What Does `@WebMvcTest` Select for an MVC Test?

<!-- VIDEO_SECTION -->

### Scene 1 — What Does `@WebMvcTest` Select for an MVC Test?

**Time:** `00:00–00:45`

**Visual:**

Draw the MVC slice graph: selected controller/advice/filter/converter/MVC infrastructure lit; service and repository layers dimmed outside the slice.

**Script:**

`@WebMvcTest` creates a focused Spring MVC test slice. It selects MVC-oriented components such as controllers and related web infrastructure while excluding most application services, repositories, and unrelated auto-configuration. That makes it suitable for verifying request mapping, validation integration, serialization, controller advice, filters that belong in the selected web slice, and MVC configuration at the Boot integration layer without starting the whole application.

**Purpose:**

Show exactly what `@WebMvcTest` keeps in an MVC slice so controller mapping, validation, serialization, advice, filters, and MVC configuration are tested without unrelated layers.

## How Are Controller Collaborators Supplied to an `@WebMvcTest`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:45–01:00`

**Visual:**

Keep the current context boundary visible, then resize it to expose the next scope decision: put the controller in the center, then replace an excluded service collaborator with `@MockBean` entering through normal dependency injection while the slice boundary stays fixed.

**Script:**

`@WebMvcTest` narrows the MVC layer, which immediately raises the question of how dependencies that controllers still need are supplied inside that restricted context.

**Purpose:**

Show how excluded controller collaborators re-enter a web slice explicitly rather than widening component scanning by accident.

### Scene 1 — How Are Controller Collaborators Supplied to an `@WebMvcTest`?

**Time:** `01:00–01:53`

**Visual:**

Put the controller in the center, then replace an excluded service collaborator with `@MockBean` entering through normal dependency injection while the slice boundary stays fixed.

**Script:**

Because services and repositories are typically outside the slice, controller collaborators must be supplyd explicitly. A common Boot-specific option is `@MockBean`, which adds or replaces a bean in the test context so the controller can be exercised without loading the collaborator's production layer. If the test keeps importing many production collaborators to make the slice work, reassess the boundary. The behavior may actually require a larger integration context rather than a heavily reconstructed web slice.

**Purpose:**

Explain how controller collaborators enter an MVC slice, distinguishing intentional test doubles/imports from components the slice deliberately excludes.

## What Does `@WebFluxTest` Select for a Reactive Web Test?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:53–02:08`

**Visual:**

Keep the current context boundary visible, then resize it to expose the next scope decision: draw the reactive web slice: selected WebFlux controller/router infrastructure and codecs inside; unrelated application layers outside.

**Script:**

Once MVC collaborators are understood, compare the reactive equivalent so the same focused-testing idea is not mistaken for identical infrastructure.

**Purpose:**

Contrast the MVC slice with the reactive slice while preserving the same focused-testing principle.

### Scene 1 — What Does `@WebFluxTest` Select for a Reactive Web Test?

**Time:** `02:08–02:51`

**Visual:**

Draw the reactive web slice: selected WebFlux controller/router infrastructure and codecs inside; unrelated application layers outside.

**Script:**

`@WebFluxTest` is the focused reactive-web counterpart. It loads WebFlux-oriented infrastructure and selected reactive web components while excluding unrelated application layers. Choose it when the objective is routing/controller behavior, codecs, validation integration, exception handling, or reactive web configuration at the framework boundary. Reactive pipeline semantics and Reactor behavior themselves remain outside Boot testing ownership.

**Purpose:**

Mirror the MVC model on the reactive stack by showing what `@WebFluxTest` keeps and why its boundary differs from a full reactive application context.

## Which Test Clients Are Auto-Configured by Web Slices?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:51–03:06`

**Visual:**

Keep the current context boundary visible, then resize it to expose the next scope decision: compare `@WebMvcTest` → `MockMvc` with `@WebFluxTest` → `WebTestClient`; keep both paths in-process with no live server socket.

**Script:**

Each web slice brings a matching execution model, so the next step is identifying the client Boot auto-configures for that model.

**Purpose:**

Tie each web slice to the client Boot auto-configures for its request-processing model.

### Scene 1 — Which Test Clients Are Auto-Configured by Web Slices?

**Time:** `03:06–03:49`

**Visual:**

Compare `@WebMvcTest` → `MockMvc` with `@WebFluxTest` → `WebTestClient`; keep both paths in-process with no live server socket.

**Script:**

`@WebMvcTest` auto-configures `MockMvc`, allowing MVC request processing to be exercised without starting an HTTP server. `@WebFluxTest` can auto-configure `WebTestClient` for the reactive web slice. Boot is responsible for making these clients available in the selected test context. The detailed request builder, exchange, expectation, and assertion APIs belong to Spring's web testing support.

**Purpose:**

Map the test clients auto-configured by web slices to their respective MVC or WebFlux execution models.

## When Is a Web Slice Enough and When Is a Real Server Necessary?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:49–04:04`

**Visual:**

Keep the current context boundary visible, then resize it to expose the next scope decision: place mock web processing and live-server HTTP on parallel lanes; highlight socket, connector, and server-configuration concerns as the reasons to widen the test.

**Script:**

A slice client stays inside the framework boundary; if the assertion depends on an actual server socket or connector, the test must widen to real-server fidelity.

**Purpose:**

Use the in-process client model to identify the exact risks that require widening the test to a real server.

### Scene 1 — When Is a Web Slice Enough and When Is a Real Server Necessary?

**Time:** `04:04–04:51`

**Visual:**

Place mock web processing and live-server HTTP on parallel lanes; highlight socket, connector, and server-configuration concerns as the reasons to widen the test.

**Script:**

A web slice is enough when the behavior can be proven entirely within the framework request-processing boundary: mappings, validation, serialization, advice, security integration configured for the slice, or controller collaboration. Use a real-server `@SpringBootTest` when the assertion depends on embedded-server behavior, actual network boundaries, server configuration, real HTTP client/server interaction, or cross-layer application wiring that the slice deliberately excludes.

**Purpose:**

Give a fidelity rule for deciding when a web slice proves enough and when a real embedded server is required for the behavior under test.

## Where Does Boot Web-Slice Configuration Hand Off to MVC or WebFlux Testing Mechanics?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:51–05:03`

**Visual:**

Keep the ownership lanes visible and move the next responsibility across the correct boundary: use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

That scope decision also marks an ownership handoff: Boot assembles the slice, while MVC or WebFlux testing owns the request-processing mechanics inside it.

**Purpose:**

Finish the current mechanism by assigning the next responsibility to the framework or library that actually owns it.

### Scene 1 — Where Does Boot Web-Slice Configuration Hand Off to MVC or WebFlux Testing Mechanics?

**Time:** `05:03–05:48`

**Visual:**

Use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

On the Boot side, the responsibility is which components and auto-configurations enter `@WebMvcTest` or `@WebFluxTest`, plus the auto-configuration of their test clients. Spring Framework owns how `MockMvc` and `WebTestClient` execute requests and assert framework behavior. The production MVC/WebFlux request pipeline belongs to the Spring Framework web modules. Boot testing only supplies the focused environment in which that pipeline is exercised.

**Purpose:**

Draw the ownership boundary between Boot's slice configuration and the deeper MVC/WebFlux testing mechanics that execute requests and assertions.