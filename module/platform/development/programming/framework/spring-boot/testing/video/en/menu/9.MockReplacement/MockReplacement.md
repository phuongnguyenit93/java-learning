---
video:
  url: ""
---

# Replacing and Spying on Beans in Boot Tests

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## When Should `@MockBean` Add or Replace a Bean in the Test Context?

<!-- VIDEO_SECTION -->

### Scene 1 — When Should `@MockBean` Add or Replace a Bean in the Test Context?

**Time:** `00:00–00:55`

**Visual:**

Draw the bean graph before and after `@MockBean`: the target collaborator is replaced or added, and existing consumers are rewired to the Mockito mock through normal injection.

**Script:**

`@MockBean` integrates Mockito mocks with a Spring Boot test `ApplicationContext`. It can add a new bean when no matching bean exists or replace a single existing bean definition so Boot-managed components receive the mock through normal dependency injection. Choose it when the test boundary includes the Spring context but one collaborator should be controlled rather than started for real. A web slice commonly replaces service collaborators this way. Plain unit tests that do not need a Spring context should use Mockito directly instead.

**Purpose:**

Show when `@MockBean` is appropriate for replacing or supplying a collaborator inside the Spring test context rather than mocking an object outside Spring.

## When Should `@SpyBean` Wrap an Existing Bean?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:09`

**Visual:**

Carry the observable result from the previous scene into the next mechanism: wrap the existing bean with a Mockito spy while leaving its real dependencies and behavior in place; mark only the interactions being stubbed or verified.

**Script:**

A full mock replaces a collaborator completely; the next contrast is `@SpyBean`, which keeps the real bean and intercepts only selected behavior.

**Purpose:**

Contrast full collaborator replacement with wrapping a real bean so the learner chooses a spy only when real behavior must remain.

### Scene 1 — When Should `@SpyBean` Wrap an Existing Bean?

**Time:** `01:09–02:06`

**Visual:**

Wrap the existing bean with a Mockito spy while leaving its real dependencies and behavior in place; mark only the interactions being stubbed or verified.

**Script:**

`@SpyBean` keeps the real bean in the Boot context but wraps it with a Mockito spy. That becomes useful when most production behavior should run typically while the test observes interactions or overrides a small part of the behavior. Because the real bean and its dependencies still exist, a spy is not a cheaper substitute for a mock. It is an integration-oriented choice and may interact with Spring proxies or caching infrastructure; Mockito stubbing details and proxy unwrapping belong to their respective owners.

**Purpose:**

Differentiate `@SpyBean` from full replacement by showing how it wraps an existing bean while preserving most real behavior.

## How Does Bean Replacement Change the Boot Test Context?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:06–02:19`

**Visual:**

Carry the current configuration graph forward and change only the next input or import: compare two cached-context identities: same application configuration, different mock/spy definitions, therefore different effective bean graphs.

**Script:**

Whether mock or spy is used, the important consequence is that replacement changes the Spring bean graph rather than only local test code.

**Purpose:**

Move from mock-versus-spy choice to the shared consequence: both alter the effective Spring bean graph.

### Scene 1 — How Does Bean Replacement Change the Boot Test Context?

**Time:** `02:19–03:11`

**Visual:**

Compare two cached-context identities: same application configuration, different mock/spy definitions, therefore different effective bean graphs.

**Script:**

Mock and spy definitions are context customizers. They alter the effective bean graph that Spring creates for the test and therefore change the identity of the cached test context. This is valuable because the replacement participates in ordinary autowiring, but it also means two otherwise identical tests with different mock/spy definitions may not reuse the same context. Treat dependency replacement as part of the test-context design rather than as an invisible local variable.

**Purpose:**

Make bean replacement a context-level operation: injection targets, bean graph, and context identity all change when mocks or spies are introduced.

## Why Can `@MockBean` Not Reconfigure Behavior Needed During Context Refresh?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:11–03:26`

**Visual:**

Carry the observable result from the previous scene into the next mechanism: show the smallest concrete code/configuration fragment needed for “Why Can `@MockBean` Not Reconfigure Behavior Needed During Context Refresh?”, then connect that input to the resulting test-context or runtime boundary.

**Script:**

Because replacement participates in context construction, timing matters: some behavior needed during refresh cannot be supplied by a mock created for the finished context.

**Purpose:**

Shift from replacement semantics to initialization timing so the learner sees why a mock present in the context can still be configured too late for refresh-time behavior.

### Scene 1 — Why Can `@MockBean` Not Reconfigure Behavior Needed During Context Refresh?

**Time:** `03:26–04:17`

**Visual:**

Show the smallest concrete code/configuration fragment needed for “Why Can `@MockBean` Not Reconfigure Behavior Needed During Context Refresh?”, then connect that input to the resulting test-context or runtime boundary.

**Script:**

`@MockBean` creates the mock before context refresh, but test-method stubbing typically happens only after the context has already been refreshed. If another bean needs a specific mocked return value during its own initialization, configuring that behavior in the test method is too late. Boot's documentation recommends defining and configuring such a mock through a `@Bean` method in test configuration. That lets the behavior exist before dependent beans initialize.

**Purpose:**

Explain the refresh-time limitation of `@MockBean`, so configuration needed before the context is fully built is not incorrectly delegated to a mock that appears too late.

## How Do Mock and Spy Definitions Affect the Test Context Cache Key?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:17–04:32`

**Visual:**

Carry the current configuration graph forward and change only the next input or import: show the context-cache key gaining a mock/spy customizer component; change the replacement set and animate a cache miss/new context startup.

**Script:**

Once replacement is treated as context configuration, its effect on context-cache identity follows naturally.

**Purpose:**

Connect replacement as context configuration to the cache-key consequence that can multiply context startups.

### Scene 1 — How Do Mock and Spy Definitions Affect the Test Context Cache Key?

**Time:** `04:32–05:25`

**Visual:**

Show the context-cache key gaining a mock/spy customizer component; change the replacement set and animate a cache miss/new context startup.

**Script:**

Spring's context cache includes the effective context customizers, including Boot mock and spy definitions. Different replacement sets can therefore produce different cache keys and force additional context startups. A suite with many tests that each declare slightly different mocks can become unexpectedly slow even when every individual test is small. Reuse stable context shapes where practical, and prefer plain unit tests when Spring integration is not part of the behavior being proven.

**Purpose:**

Connect mock/spy definitions to the test context cache key so differing replacement sets are recognized as a source of extra context startups.

## Where Does Boot Bean Replacement End and Mockito Behavior Begin?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:25–05:40`

**Visual:**

Carry the observable result from the previous scene into the next mechanism: show the smallest concrete code/configuration fragment needed for “Where Does Boot Bean Replacement End and Mockito Behavior Begin?”, then connect that input to the resulting test-context or runtime boundary.

**Script:**

Cache behavior still does not make Boot the owner of mock semantics, so finish by separating Boot's bean integration from Mockito's stubbing and verification responsibilities.

**Purpose:**

Use the cache-key consequence to close the Boot integration story, then hand stubbing, verification, and spy semantics back to Mockito.

### Scene 1 — Where Does Boot Bean Replacement End and Mockito Behavior Begin?

**Time:** `05:40–06:31`

**Visual:**

Show the smallest concrete code/configuration fragment needed for “Where Does Boot Bean Replacement End and Mockito Behavior Begin?”, then connect that input to the resulting test-context or runtime boundary.

**Script:**

On the Boot side, the responsibility is the integration that registers Mockito-created mocks or spies as beans, injects them into the test, and resets mocks according to Boot's test support. Mockito remains responsible for how mocks are created, stubbed, verified, matched, and spied. Questions such as `when` versus `doReturn`, argument matchers, strictness, verification modes, or spy semantics belong to Mockito learning. Boot testing only explains how those test doubles enter a Spring application context.

**Purpose:**

Draw the ownership boundary: Boot integrates mock/spy beans into the context, while Mockito owns stubbing, verification, and test-double behavior.
