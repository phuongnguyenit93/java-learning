---
video:
  url: ""
---

# Test Properties and `@TestConfiguration`

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## Why Customize a Boot Test Context for One Scenario?

<!-- VIDEO_SECTION -->

### Scene 1 — Why Customize a Boot Test Context for One Scenario?

**Time:** `00:00–00:51`

**Visual:**

Show one production context shape branching into a single test scenario with a local override layer while unrelated tests keep the baseline.

**Script:**

Tests sometimes need a controlled variation of the application: a feature flag enabled, a timeout shortened, one external endpoint replaced, or a test-only bean added. Boot supplies local customization mechanisms so these changes can remain test-scoped instead of leaking into normal application configuration. Use the smallest customization that expresses the scenario. Every additional property or configuration class can also affect context reuse, so test customization is part of suite design rather than free setup.

**Purpose:**

Explain why a single test scenario may need local configuration differences without changing the production application or every other test context.

## How Do Boot Test Annotation Properties Override Configuration Locally?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:51–01:05`

**Visual:**

Carry the current configuration graph forward and change only the next input or import: overlay test annotation properties on the normal property set, then show the resulting effective Environment and context-cache identity.

**Script:**

Once a test needs scenario-specific behavior, the lightest customization is usually a local property override rather than a new application configuration.

**Purpose:**

Move from the need for scenario-local customization to the lightest override: properties that change only this test context.

### Scene 1 — How Do Boot Test Annotation Properties Override Configuration Locally?

**Time:** `01:05–01:50`

**Visual:**

Overlay test annotation properties on the normal property set, then show the resulting effective Environment and context-cache identity.

**Script:**

Boot test annotations such as `@SpringBootTest` accept a `properties` attribute for inline test-specific properties. Those values are added to the test environment for that test context and can override ordinary application configuration for the scenario. This is convenient for small local overrides. The broader precedence and binding model remains owned by externalized configuration and Spring TestContext property-source support.

**Purpose:**

Show how annotation-level properties override configuration only for the targeted Boot test and become part of that context's effective identity.

## What Problem Does `@TestConfiguration` Solve?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:04`

**Visual:**

Carry the current configuration graph forward and change only the next input or import: keep the primary application configuration in the center of the context, then add `@TestConfiguration` as an additive branch; for top-level reusable config, show `@Import` connecting it to the test.

**Script:**

Properties can change values, but some scenarios need extra beans or test-only wiring; that is the problem `@TestConfiguration` solves.

**Purpose:**

Show why value overrides are insufficient when the scenario needs additional test-only beans.

### Scene 1 — What Problem Does `@TestConfiguration` Solve?

**Time:** `02:04–02:46`

**Visual:**

Keep the primary application configuration in the center of the context, then add `@TestConfiguration` as an additive branch; for top-level reusable config, show `@Import` connecting it to the test.

**Script:**

`@TestConfiguration` marks configuration intended specifically for tests. Unlike a nested plain `@Configuration` used as the primary test configuration, a nested `@TestConfiguration` is added alongside the application's normal primary configuration. That becomes useful for test-only beans, alternative infrastructure adapters, or reusable support that should never be discovered as ordinary production configuration.

**Purpose:**

Define `@TestConfiguration` as additive test-only bean configuration rather than a replacement for the application's primary Boot configuration.

## How Is Test-Only Configuration Added Without Replacing the Primary Application Configuration?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:46–03:00`

**Visual:**

Carry the current configuration graph forward and change only the next input or import: show nested/imported `@TestConfiguration` joining the primary configuration before bean creation, with test-only beans clearly marked.

**Script:**

`@TestConfiguration` only helps when it is added without displacing the real primary application model, so the next step is how that configuration joins the context.

**Purpose:**

Carry the additive TestConfiguration idea into the exact way those beans join the existing primary application model.

### Scene 1 — How Is Test-Only Configuration Added Without Replacing the Primary Application Configuration?

**Time:** `03:00–03:44`

**Visual:**

Show nested/imported `@TestConfiguration` joining the primary configuration before bean creation, with test-only beans clearly marked.

**Script:**

A nested `@TestConfiguration` is automatically combined with the primary Boot configuration for that test arrangement. A top-level reusable `@TestConfiguration` can be imported explicitly with `@Import` when several tests need the same support. The important distinction is additive intent: test configuration supplements the production application model rather than silently becoming the application's main configuration.

**Purpose:**

Show how test-only configuration is imported or nested so extra beans join the existing application model without changing what Boot treats as the primary configuration.

## How Can Property and Configuration Differences Fragment Context Reuse?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:44–03:58`

**Visual:**

Carry the current configuration graph forward and change only the next input or import: display context-cache cards keyed by properties, profiles, imports, mocks/spies, and dynamic customizers; identical cards merge, differing inputs create new cache entries.

**Script:**

Every extra property or imported configuration changes the effective context description, which leads directly to the question of cache reuse and fragmentation.

**Purpose:**

Expose the cache consequence of local properties/imports so customization cost is visible at suite scale.

### Scene 1 — How Can Property and Configuration Differences Fragment Context Reuse?

**Time:** `03:58–04:45`

**Visual:**

Display context-cache cards keyed by properties, profiles, imports, mocks/spies, and dynamic customizers; identical cards merge, differing inputs create new cache entries.

**Script:**

Spring TestContext reuses contexts only when the effective test configuration matches. Different inline properties, profiles, imported configuration, mocks, and other context customizers can contribute to different cache keys. Therefore, many tiny one-off configuration variants can make a suite repeatedly start similar Boot contexts. Prefer shared test configurations and stable property sets when they express the same scenario boundary.

**Purpose:**

Make context-cache fragmentation visible by tying property, profile, import, and replacement differences to distinct effective test-context configurations.

## Where Do Test-Specific Overrides Hand Off to Externalized Configuration and Spring TestContext?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:45–04:57`

**Visual:**

Keep the ownership lanes visible and move the next responsibility across the correct boundary: use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

When reuse changes unexpectedly, diagnosis crosses two owners: Boot supplies the override hooks, while externalized configuration and Spring TestContext determine value resolution and cache behavior.

**Purpose:**

Finish the current mechanism by assigning the next responsibility to the framework or library that actually owns it.

### Scene 1 — Where Do Test-Specific Overrides Hand Off to Externalized Configuration and Spring TestContext?

**Time:** `04:57–05:41`

**Visual:**

Use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

On the Boot side, the responsibility is convenience attributes and `@TestConfiguration` integration used by Boot test annotations. Externalized configuration owns normal property sources, precedence, profiles, binding, and `@ConfigurationProperties` semantics. Spring TestContext is responsible for generic test property sources, dynamic property registration, context customization, and context caching. This module explains how Boot uses those capabilities without redefining their underlying mechanics.

**Purpose:**

Separate Boot's test-local override hooks from externalized-configuration precedence and Spring TestContext caching so ownership remains clear when values or reuse differ.