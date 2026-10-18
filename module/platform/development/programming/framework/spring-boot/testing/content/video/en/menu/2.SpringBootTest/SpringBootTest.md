---
video:
  url: ""
---

# Full Application Contexts with `@SpringBootTest`

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## When Is a Full Boot Application Context the Right Test Boundary?

<!-- VIDEO_SECTION -->

### Scene 1 — When Is a Full Boot Application Context the Right Test Boundary?

**Time:** `00:00–00:54`

**Visual:**

Place unit, slice, and full-context scopes on one fidelity axis and highlight the first point where the behavior crosses multiple Boot-managed layers.

**Script:**

Choose a full Boot context when the behavior being verified depends on several application layers or on Boot's production-style configuration working together. Typical examples include configuration binding plus service wiring, cross-layer integration, security or messaging infrastructure, and startup conditions that a focused slice would deliberately omit. Full-context testing should be a deliberate fidelity choice. It is more expensive to start and typically exposes more infrastructure than a focused test needs, so it should prove behavior that smaller tests cannot establish reliably.

**Purpose:**

Define the exact situations where full-context fidelity is justified, so `@SpringBootTest` is chosen for cross-layer behavior rather than by default.

## What Does `@SpringBootTest` Load into the Test Context?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:54–01:09`

**Visual:**

Carry the observable result from the previous scene into the next mechanism: reveal the full-context graph: primary configuration, component scanning, auto-configuration, external properties, then a separate switch for whether a real server starts.

**Script:**

Choosing a full context only makes sense after we know what that choice actually loads, so inspect the application model `@SpringBootTest` constructs.

**Purpose:**

Turn the decision to use a full context into a concrete inventory of what that context loads before discussing configuration discovery.

### Scene 1 — What Does `@SpringBootTest` Load into the Test Context?

**Time:** `01:09–01:57`

**Visual:**

Reveal the full-context graph: primary configuration, component scanning, auto-configuration, external properties, then a separate switch for whether a real server starts.

**Script:**

By default, `@SpringBootTest` looks for the application's primary Boot configuration and asks `SpringApplication` to build the context. Application configuration, component scanning, auto-configuration, externalized properties, and other Boot startup facilities can therefore participate. The annotation does not necessarily start a network server. Its default `WebEnvironment.MOCK` uses a mock web environment when web infrastructure is available. Server startup is controlled separately by the `webEnvironment` attribute.

**Purpose:**

Show what `@SpringBootTest` actually assembles and what it does not imply, especially that loading the full context is separate from starting a real server.

## How Does Full-Context Testing Reuse Boot's Primary Configuration Discovery?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:57–02:11`

**Visual:**

Carry the current configuration graph forward and change only the next input or import: reuse the package-tree search and overlay the full-context test to show it starting from the same primary Boot configuration as the application.

**Script:**

Because that context starts from the application's primary configuration, the next diagnostic question is how Boot discovers that configuration for the test.

**Purpose:**

Connect the loaded full context to the primary configuration search so an incorrect root configuration becomes a diagnosable bootstrap problem.

### Scene 1 — How Does Full-Context Testing Reuse Boot's Primary Configuration Discovery?

**Time:** `02:11–03:03`

**Visual:**

Reuse the package-tree search and overlay the full-context test to show it starting from the same primary Boot configuration as the application.

**Script:**

Full-context tests typically reuse the same primary `@SpringBootApplication` / `@SpringBootConfiguration` entry point that Boot discovers for the application. This reduces duplication between production and test wiring and makes auto-configuration conditions evaluate against a realistic configuration model. If a test truly needs a different top-level configuration, it can supply explicit classes. Use that choice carefully: replacing the primary configuration changes the meaning of the test and can make it less representative of the application startup path.

**Purpose:**

Connect full-context tests to Boot's primary-configuration discovery so package placement and explicit classes can be diagnosed when startup selects the wrong model.

## What Fidelity and Startup Cost Come with a Full Context?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:03–03:18`

**Visual:**

Carry the current configuration graph forward and change only the next input or import: plot fidelity against startup/failure surface; show context-cache reuse for identical context shapes and cache splits when properties, profiles, mocks, or imports differ.

**Script:**

Reusing production configuration improves fidelity, but that benefit has a startup and cache cost that must be weighed before scaling the suite.

**Purpose:**

Expose the cost created by production-like wiring and context identity before deciding whether a smaller slice is a more honest boundary.

### Scene 1 — What Fidelity and Startup Cost Come with a Full Context?

**Time:** `03:18–04:13`

**Visual:**

Plot fidelity against startup/failure surface; show context-cache reuse for identical context shapes and cache splits when properties, profiles, mocks, or imports differ.

**Script:**

A full context gives high fidelity because many production beans and auto-configurations are present together. The cost is startup time, more potential external dependencies, and a larger failure surface. Spring's context cache can amortize that startup cost when tests share the same effective configuration. Every unnecessary variation in properties, profiles, mocks, or imported configuration can create a distinct cached context. Full-context tests therefore benefit from stable, reusable configuration just as much as they benefit from realistic wiring.

**Purpose:**

Make the fidelity-versus-cost trade-off explicit, including context-cache reuse and the configuration variations that fragment it.

## When Should a Focused Slice Replace `@SpringBootTest`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:13–04:28`

**Visual:**

Keep the current context boundary visible, then resize it to expose the next scope decision: compare a focused slice with the full application graph; highlight the collaboration required by the assertion and choose the smaller graph only while that collaboration remains inside it.

**Script:**

Once that cost is visible, a focused slice becomes the alternative when the behavior does not require the full application graph.

**Purpose:**

Use the fidelity/cost trade-off to justify the point where a focused slice should replace a full context.

### Scene 1 — When Should a Focused Slice Replace `@SpringBootTest`?

**Time:** `04:28–05:19`

**Visual:**

Compare a focused slice with the full application graph; highlight the collaboration required by the assertion and choose the smaller graph only while that collaboration remains inside it.

**Script:**

Choose a slice when the test objective belongs to one focused part of the application and unrelated infrastructure would add cost or noise. `@WebMvcTest`, `@WebFluxTest`, `@DataJpaTest`, and `@JdbcTest` deliberately restrict the context and import purpose-specific test auto-configuration. A slice is not “less correct” when it matches the test boundary. It becomes insufficient only when the behavior depends on omitted collaborators, cross-layer wiring, or full Boot startup semantics.

**Purpose:**

Teach when a focused slice is the more accurate boundary because unrelated infrastructure would only add startup cost and failure surface.

## How Does `WebEnvironment` Change the Full-Context Model?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:19–05:34`

**Visual:**

Carry the observable result from the previous scene into the next mechanism: show the smallest concrete code/configuration fragment needed for “How Does `WebEnvironment` Change the Full-Context Model?”, then connect that input to the resulting test-context or runtime boundary.

**Script:**

A full context and a real HTTP server are separate choices, so finish by seeing how `WebEnvironment` changes the same Boot context model.

**Purpose:**

Separate context scope from server startup before handing the learner to `WebEnvironment`; this prevents “full context” from being mistaken for “live HTTP server”.

### Scene 1 — How Does `WebEnvironment` Change the Full-Context Model?

**Time:** `05:34–06:21`

**Visual:**

Show the smallest concrete code/configuration fragment needed for “How Does `WebEnvironment` Change the Full-Context Model?”, then connect that input to the resulting test-context or runtime boundary.

**Script:**

`WebEnvironment` determines whether the full Boot context uses a mock web environment, starts an actual embedded server, or disables web infrastructure. `MOCK` is the default. `RANDOM_PORT` and `DEFINED_PORT` create a real server environment, while `NONE` creates a non-web context through `SpringApplication`. The next chapter focuses on the operational consequences of those modes: client choice, actual ports, and transaction boundaries when requests cross a real HTTP server.

**Purpose:**

Separate full-context loading from `WebEnvironment` server choices so learners can choose mock versus real-server fidelity deliberately.
