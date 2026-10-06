---
video:
  url: ""
---

# Type-Safe Configuration with @ConfigurationProperties

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

## Why Group Configuration into a Typed Object?

<!-- VIDEO_SECTION -->

### Scene 1 — Turn Related Keys into One Contract

**Time:** `00:00–00:45`

**Visual:**

Show `mail.host`, `mail.port`, and `mail.retry-count` flowing into one `MailProperties` object. Add callouts for type safety, validation, metadata, and refactoring.

**Script:**

When several settings belong together, a typed configuration object gives them one explicit application-facing contract. Boot still resolves the winning values in the Environment first, then binds those values into the object. The benefit is not just fewer annotations; the group becomes easier to validate, document, and refactor as one unit.

**Purpose:**

Establish `@ConfigurationProperties` as the typed boundary for a coherent configuration domain.

## Prefixes and Configuration Namespaces

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:45–00:55`

**Visual:**

Frame the `mail.*` keys as one subtree and highlight the common `mail` prefix.

**Script:**

Once values form one contract, the prefix defines which part of the configuration belongs to that contract.

**Purpose:**

Move from grouping values conceptually to defining their namespace explicitly.

### Scene 2 — Design a Stable Namespace

**Time:** `00:55–01:40`

**Visual:**

Show `@ConfigurationProperties("mail")` above fields `host` and `retryCount`. Map `mail.host` and `mail.retry-count` into the fields. Contrast `mail` with an overly broad prefix such as `app`.

**Script:**

The prefix identifies the configuration namespace Boot binds. A stable domain-oriented prefix such as `mail` makes ownership clearer than a broad bucket such as `app`. Prefixes use canonical kebab case, and the fields beneath them describe the structure of that contract.

**Purpose:**

Teach prefixes as stable configuration namespaces rather than arbitrary class-derived labels.

## Registering Configuration Properties with Scanning or Explicit Enablement

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:40–01:50`

**Visual:**

Keep the property type on screen and add two paths into the Spring container.

**Script:**

Describing how a type should bind is not enough; Boot also needs to register that type as a configuration-properties bean.

**Purpose:**

Separate binding metadata on the type from bean registration.

### Scene 3 — Scan or Enable the Binding Target

**Time:** `01:50–02:35`

**Visual:**

Show `@ConfigurationPropertiesScan` discovering application-owned property classes and `@EnableConfigurationProperties(SomeProperties.class)` registering one type explicitly.

**Script:**

Scanning is convenient for application-owned property classes under a known package boundary. Explicit enablement is useful when configuration wants to declare exactly which property types participate, including library-owned configuration. Registration creates the binding target; it does not change precedence or the value that Environment resolved.

**Purpose:**

Clarify the two common registration mechanisms and keep registration separate from value resolution.

## JavaBean and Setter Binding

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:35–02:45`

**Visual:**

Open the registered type and expose mutable fields with setters.

**Script:**

With a property type registered, the next choice is how Boot constructs and populates it.

**Purpose:**

Move from registration to binding style.

### Scene 4 — Setter Binding Mutates a JavaBean Target

**Time:** `02:45–03:30`

**Visual:**

Show `MailProperties` with `host`, `port = 25`, getters, and setters. Animate `mail.host` and `mail.port` through conversion into the setters.

**Script:**

JavaBean binding uses a no-argument construction path and writable bean properties. Boot converts the resolved values to the target types and calls the setters. A field initializer such as port 25 is an object default used when nothing binds to that property; it is not a lower-precedence Environment source.

**Purpose:**

Explain classic mutable setter binding and distinguish target defaults from property-source defaults.

## Constructor Binding in Spring Boot 3.x

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:40`

**Visual:**

Replace setters with final fields and one parameterized constructor.

**Script:**

If the configuration should be immutable, Boot can construct the whole object from constructor parameters instead.

**Purpose:**

Contrast mutable setter binding with immutable constructor binding.

### Scene 5 — Construct Immutable Configuration in Boot 3.x

**Time:** `03:40–04:05`

**Visual:**

Show an immutable `MailProperties(String host, int port)` class. Highlight the single parameterized constructor, then add a note: multiple constructors -> `@ConstructorBinding` can identify the intended one.

**Script:**

Constructor binding creates the configuration object from constructor parameters instead of mutating it later. In Boot 3.x, a single parameterized constructor is used for binding without requiring `@ConstructorBinding`; with several constructors, the annotation can select the intended one.

**Purpose:**

Teach how Boot 3.x selects the constructor used for immutable configuration.

### Scene 6 — Registration and Parameter Names Are Part of the Contract

**Time:** `04:05–04:30`

**Visual:**

Keep the immutable class on screen. Add `@ConfigurationPropertiesScan` / `@EnableConfigurationProperties` on the left and a compiler flag card `-parameters` on the right. Under Maven, distinguish `spring-boot-starter-parent` from merely applying `spring-boot-maven-plugin`.

**Script:**

Constructor-bound property types still have lifecycle requirements. Register them through configuration-properties scanning or explicit enablement rather than treating them like ordinary component beans. Their constructor parameter names must also be available at runtime, so compilation needs `-parameters`. The Boot Gradle plugin configures that option, and Maven projects inherit it when they use `spring-boot-starter-parent`; a custom Maven build must configure it explicitly.

**Purpose:**

Make the registration and Java parameter-name requirements visible instead of presenting constructor binding as constructor selection alone.

## Records as Configuration Property Types

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:30–04:40`

**Visual:**

Collapse the immutable class into a Java record with the same logical fields.

**Script:**

Java records make the immutable case even more compact because their constructor and component names already describe the binding target.

**Purpose:**

Move from constructor-bound classes to records as a concise immutable option.

### Scene 7 — Records Are Compact, Not Magic

**Time:** `04:40–05:25`

**Visual:**

Show `@ConfigurationProperties("client") record ClientProperties(URI baseUrl, Duration timeout) {}` and map `client.base-url` and `client.timeout=2s` into the two components.

**Script:**

Records are a natural fit for immutable configuration data. Boot applies the same relaxed-name and conversion rules when binding their components. A record does not automatically make values required or valid, though; nullability, defaults, and validation still define the real contract.

**Purpose:**

Show records as concise immutable property types while preserving the normal binding and validation rules.

## Binding Configuration onto a Third-Party @Bean

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:25–05:35`

**Visual:**

Replace the application-owned record with a library class that cannot be annotated directly.

**Script:**

Sometimes the useful binding target comes from a library you do not own, so the binding annotation has to move to the bean factory boundary.

**Purpose:**

Introduce third-party binding as a separate use case from application-owned property types.

### Scene 8 — Bind a Third-Party JavaBean at `@Bean`

**Time:** `05:35–06:20`

**Visual:**

Show a `@Bean` method returning `ConnectionPoolSettings` with `@ConfigurationProperties("client.pool")` on the method. Animate resolved values into writable properties on the returned instance.

**Script:**

Putting `@ConfigurationProperties` on a `@Bean` method lets Boot bind external values onto the object returned by that method. This is useful for mutable third-party types and avoids copying every setting into a wrapper solely for binding. Because you do not own the target type, validation and metadata may be less expressive.

**Purpose:**

Explain the third-party `@Bean` binding pattern and its trade-offs.

## Configuration Properties Lifecycle and Bean Boundary

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:20–06:30`

**Visual:**

Turn the previous examples into a lifecycle pipeline from Environment values to runtime components.

**Script:**

No matter which binding style is used, configuration-properties beans should stay focused on configuration data rather than become general application services.

**Purpose:**

Close the chapter with the lifecycle and responsibility boundary of configuration-properties beans.

### Scene 9 — Bind First, Build Runtime Components After

**Time:** `06:30–07:15`

**Visual:**

Animate `Environment values -> @ConfigurationProperties binding -> validated configuration object -> separate @Bean/application component`. Put a red X over business-service calls or expensive runtime work inside the property object.

**Script:**

Boot may need configuration binding and conversion early, so keep property objects focused on data and lightweight validation. They should not become hidden service locators or depend on ordinary application services. Bind the values first, validate the configuration object, and let a separate configuration class or component use it to create runtime clients and services.

**Purpose:**

Define the lifecycle boundary that keeps configuration binding deterministic and application behavior in the proper layer.
