---
video:
  url: ""
---

# Runtime customization boundaries

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

## When Is Runtime Customization Actually Necessary?

<!-- VIDEO_SECTION -->

### Scene 1 — When Is Runtime Customization Actually Necessary?

**Time:** `00:00–00:55`

**Visual:**

Progressive reveal on the chapter visual: start from one concrete unmet runtime requirement and walk property → supported Boot API → neighboring owner.

**Script:**

Before adding bootstrap code, the decision should start from a concrete behavior—startup mode, listener registration, application shape, initialization policy—not from a desire to "take control" of Boot. Runtime customization is justified when the application's required behavior differs from Boot's supported defaults and no simpler configuration surface expresses that requirement. Every programmatic customization moves part of the runtime contract into application code. That can be appropriate, but it is harder to discover than a standard property and may interact with auto-configuration or framework conventions. Before customizing, identify the owning layer and lifecycle phase. Many apparent SpringApplication problems are actually externalized configuration, auto-configuration, container configuration, executor configuration, or web-server configuration.

**Purpose:**

Require a concrete unmet runtime behavior before adding programmatic Boot customization.


## Why Prefer Supported Configuration Properties First?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:07`

**Visual:**

Keep the customization decision tree visible and move one level from property to `SpringApplication`, builder, lifecycle hook, or neighboring owner.

**Script:**

Before writing bootstrap code, check whether Boot already exposes the behavior as a supported property; properties keep the runtime contract discoverable and conventional.

**Purpose:**

Insert the property-first checkpoint before programmatic customization becomes the default response to every runtime requirement.

### Scene 2 — Why Prefer Supported Configuration Properties First?

**Time:** `01:07–02:01`

**Visual:**

Progressive reveal on the chapter visual: make the property branch the first choice and open code only when no supported property expresses the requirement.

**Script:**

At the property-first checkpoint, properties are visible in configuration metadata, participate in Boot's externalized-configuration model, and usually preserve auto-configuration's intended back-off and lifecycle behavior. Boot properties are the preferred first option when they already represent the desired behavior. Programmatic customization is appropriate when the property surface cannot express the requirement, or when the application must assemble `SpringApplication` itself before the normal context exists. Do not recreate property binding in `main()` merely to make configuration look explicit. This is also an ownership boundary: property precedence, profiles, and `@ConfigurationProperties` mechanics are taught in `externalized-configuration`. Here the concern is choosing the supported runtime surface before reaching for lower-level hooks.

**Purpose:**

Prefer discoverable Boot properties when they already express the requirement and preserve supported lifecycle/back-off behavior.


## What Can Be Customized Through `SpringApplication`?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:01–02:12`

**Visual:**

Keep the customization decision tree visible and move one level from property to `SpringApplication`, builder, lifecycle hook, or neighboring owner.

**Script:**

If a property cannot express a bootstrap-time requirement, the next supported level is an explicit `SpringApplication` instance with narrow settings.

**Purpose:**

Escalate from properties to `SpringApplication` only for bootstrap behavior that the supported property surface cannot express.

### Scene 3 — What Can Be Customized Through `SpringApplication`?

**Time:** `02:12–02:50`

**Visual:**

Progressive reveal on the chapter visual: show a tiny `SpringApplication` bootstrap snippet with one supported setting/listener/initializer and no business configuration.

**Script:**

If supported properties cannot express a bootstrap-time requirement, construct `SpringApplication` explicitly instead of relying only on the static shortcut. Keep the code narrow: set banner mode, add an early listener or initializer, choose application type, configure lazy initialization, then call `run(args)`. If `main()` starts registering business services or recreating container configuration, customization has crossed into Spring configuration or auto-configuration and should move back to that owner.

**Purpose:**

Show the narrow bootstrap settings that justify constructing `SpringApplication` directly without turning `main()` into application configuration.


## When Does `SpringApplicationBuilder` Help?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`

**Visual:**

Keep the customization decision tree visible and move one level from property to `SpringApplication`, builder, lifecycle hook, or neighboring owner.

**Script:**

When customization becomes composition—especially parent/child contexts—the builder adds value beyond the simple static `run` shortcut.

**Purpose:**

Introduce the builder only when composition or context hierarchy adds a real requirement beyond direct `SpringApplication` customization.

### Scene 4 — When Does `SpringApplicationBuilder` Help?

**Time:** `03:00–03:55`

**Visual:**

Progressive reveal on the chapter visual: draw parent/child contexts from `SpringApplicationBuilder`, shared Environment, and web-component placement constraints.

**Script:**

For context composition, its distinctive use case is building an `ApplicationContext` hierarchy with parent/child relationships, although it can also make ordinary bootstrap options easier to compose. `SpringApplicationBuilder` provides a fluent way to configure and run a `SpringApplication`. Hierarchies introduce constraints: parent and child contexts share an `Environment`, and web components must be placed in the child context according to Boot's documented restrictions. A hierarchy is therefore an architectural choice, not a stylistic alternative to one application context. Use the builder when the hierarchy or fluent assembly itself solves a real requirement. For a normal single-context application, the static `run` method or a small customized `SpringApplication` is usually easier to understand.

**Purpose:**

Reserve `SpringApplicationBuilder` for fluent composition and genuine parent/child context hierarchy requirements, including their constraints.


## How Do You Choose a Runtime Extension Point by Lifecycle Timing?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:55–04:07`

**Visual:**

Keep the customization decision tree visible and move one level from property to `SpringApplication`, builder, lifecycle hook, or neighboring owner.

**Script:**

At this point several extension points are available, so choose among them by lifecycle prerequisites and intent instead of choosing the earliest hook.

**Purpose:**

Convert the growing list of hooks into a lifecycle-timing decision rather than another API menu.

### Scene 5 — How Do You Choose a Runtime Extension Point by Lifecycle Timing?

**Time:** `04:07–04:51`

**Visual:**

Progressive reveal on the chapter visual: show the lifecycle selection table: event, early listener, runner, managed executor/scheduler, property/Application/builder.

**Script:**

Choose the latest safe hook that still satisfies the requirement. Observe a lifecycle transition with an application listener; if the event occurs before beans exist, register that listener early. Required startup work that needs normal beans belongs in an `ApplicationRunner` or `CommandLineRunner`. Ongoing work belongs on a managed executor or scheduler. A supported bootstrap setting belongs in a property first, then `SpringApplication` or the builder if code is truly required. Earlier is not better: earlier phases provide fewer guarantees and make dependencies harder to express.

**Purpose:**

Choose the latest safe extension point from prerequisites and intent instead of maximizing how early code can run.


## Which Customization Belongs to Configuration, Auto-Configuration, the Container, or Web Runtime Instead?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:51–05:05`

**Visual:**

Keep the customization decision tree visible and move one level from property to `SpringApplication`, builder, lifecycle hook, or neighboring owner.

**Script:**

The decision tree is only useful if it also says when to leave this module; finish by handing configuration, auto-configuration, container, and web concerns to their primary owners.

**Purpose:**

End customization at ownership boundaries so unrelated configuration, web, container, or build concerns do not accumulate here.

### Scene 6 — Which Customization Belongs to Configuration, Auto-Configuration, the Container, or Web Runtime Instead?

**Time:** `05:05–05:43`

**Visual:**

Progressive reveal on the chapter visual: route values/profiles, conditional beans, container internals, web runtime, diagnostics, and build behavior to their primary owners.

**Script:**

At the ownership boundary, configuration values and profiles belong to `externalized-configuration`. A customization can be technically possible in several layers, but ownership determines which choice remains understandable. Conditional bean creation, back-off rules, and reusable application defaults belong to `auto-configuration`. Generic bean lifecycle and context internals belong to Spring Framework. Boot web-server factories, server properties, connectors, TLS consumption, forwarded headers, and graceful shutdown belong to `web-runtime`. Production diagnostic exposure belongs to Actuator.

**Purpose:**

Keep application-runtime focused by routing configuration, conditional beans, container internals, web runtime, diagnostics, and build behavior to their owners.
