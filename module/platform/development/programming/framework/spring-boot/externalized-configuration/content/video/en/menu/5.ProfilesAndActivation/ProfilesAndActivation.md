---
video:
  url: ""
---

# Profiles and Profile-Specific Configuration

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

## What Profiles Solve and What They Do Not

<!-- VIDEO_SECTION -->

### Scene 1 — Use Profiles for Coherent Variants

**Time:** `00:00–00:45`

**Visual:**

Compare two cards: a `prod` profile that changes database, logging, and timeout settings together, and a single `client.timeout=3s` override supplied independently.

**Script:**

Profiles are useful when a named mode selects a coherent configuration variant such as development, staging, or production. They are less useful when only one ordinary value changes. In this module, profiles answer which configuration documents participate; ordinary property overrides still handle isolated deployment values more directly.

**Purpose:**

Define when profile-based activation is appropriate and when a normal property override is simpler.

## Active and Default Profiles

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:45–00:55`

**Visual:**

Keep the `prod` card and add controls for selecting active profiles at launch.

**Script:**

Once a profile represents a meaningful variant, Boot needs a clear way to decide which profiles are active for this launch.

**Purpose:**

Move from profile purpose to profile selection.

### Scene 2 — Select Active Profiles, Then Apply Precedence

**Time:** `00:55–01:40`

**Visual:**

Show `spring.profiles.active=dev,local`, then `--spring.profiles.active=prod`. Beneath them show the fallback `default` profile and `spring.profiles.default=none` as an alternative.

**Script:**

`spring.profiles.active` selects the active profiles and is itself an Environment property, so a higher-precedence source can replace a lower-precedence selection. If no profile is explicitly active, Spring uses the default profile unless you configure another default or `none`. Selecting a profile makes documents eligible; it does not bypass normal property precedence.

**Purpose:**

Explain active and default profile selection while preserving the distinction from value precedence.

## Profile-Specific Config Data Files

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:40–01:50`

**Visual:**

Turn the selected `prod` profile into a file lookup for `application-prod.properties` beside the baseline `application.properties`.

**Script:**

One way an active profile changes configuration is by making profile-specific application files participate.

**Purpose:**

Connect profile selection to profile-specific Config Data discovery.

### Scene 3 — Layer Profile Differences on a Baseline

**Time:** `01:50–02:35`

**Visual:**

Stack `application.properties` as the shared baseline and `application-prod.properties` above it with only two changed keys. Highlight the profile-specific values overriding the baseline.

**Script:**

Boot loads profile-specific variants from the same search locations and lets them override the non-profile-specific document. Keep shared defaults in the baseline file and only meaningful differences in the profile file. That keeps the variant readable and avoids copying the whole configuration into every environment.

**Purpose:**

Show profile-specific files as focused overlays rather than duplicated full configurations.

## Profile-Specific Documents

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:35–02:45`

**Visual:**

Merge the two files into one multi-document properties file separated by `#---`.

**Script:**

A profile-specific variant does not always need its own physical file. Boot can activate one logical document inside a larger file.

**Purpose:**

Move from profile-specific files to profile-specific documents inside one file.

### Scene 4 — One File, Multiple Logical Documents

**Time:** `02:45–03:30`

**Visual:**

Show `app.mode=standard`, then `#---`, then a second document with `spring.config.activate.on-profile=prod` and `app.mode=hardened`. Toggle `prod` on and off and show the second document join or leave the Config Data set.

**Script:**

Boot processes each logical document separately. The first document can stay unconditional while the second contributes only when its activation condition matches. This works well for a small variation that belongs close to the baseline; a larger variant may be clearer in a separate profile-specific file.

**Purpose:**

Explain multi-document Config Data as separate documents with independent activation.

## Document Activation with spring.config.activate.on-profile

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:40`

**Visual:**

Keep the second document and highlight only its `spring.config.activate.on-profile` condition.

**Script:**

That activation line has a different job from `spring.profiles.active`, and mixing those directions creates confusion.

**Purpose:**

Prepare the learner to distinguish selecting profiles from activating a document under already-selected profiles.

### Scene 5 — Selection and Document Activation Point in Opposite Directions

**Time:** `03:40–04:25`

**Visual:**

Show active profiles entering from the left and a YAML document on the right with `spring.config.activate.on-profile: "prod | staging"`. Animate the document joining Config Data only when the expression matches.

**Script:**

`spring.profiles.active` selects profiles for the application. `spring.config.activate.on-profile` asks whether this document should contribute under the profiles that are already active. Keeping those directions separate prevents a conditional document from trying to activate the very profile required for that document to exist.

**Purpose:**

Differentiate application profile selection from document-level profile activation.

## Profile Includes and Profile Groups

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:25–04:35`

**Visual:**

Expand one selected profile into several related profile cards.

**Script:**

Sometimes one selected mode needs several reusable profile units. Includes and groups provide two controlled ways to compose them.

**Purpose:**

Move from document activation to composition of profile sets.

### Scene 6 — Compose Understandable Profile Sets

**Time:** `04:35–05:20`

**Visual:**

Show `spring.profiles.include` adding `common` and `observability`. Then show a group `production -> proddb + prodmq` and animate `--spring.profiles.active=production` expanding to both members.

**Script:**

Includes add profiles alongside the selected ones. Profile groups give several fine-grained profiles one logical name so callers can activate a meaningful mode such as `production`. Keep these relationships small enough that someone reading the launch configuration can still determine which documents become active.

**Purpose:**

Explain profile includes and groups as composition tools while warning against opaque activation graphs.

## Where Profile Activation Properties May Be Declared

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:20–05:30`

**Visual:**

Take the composed profile graph and place profile-selection properties inside a conditional document, then draw a circular warning arrow.

**Script:**

Composition only stays deterministic if profile-selection properties are declared from unconditional configuration, not from inside documents that depend on profile activation.

**Purpose:**

Introduce the declaration restrictions that prevent circular activation.

### Scene 7 — Do Not Select Profiles from Conditional Documents

**Time:** `05:30–06:15`

**Visual:**

Show an invalid document containing `spring.config.activate.on-profile=prod` and `spring.profiles.active=metrics`. Freeze on a startup rejection and highlight the circular dependency.

**Script:**

Boot restricts `spring.profiles.active`, default, include, and group declarations to non-profile-specific configuration. A document that exists only after `prod` is active cannot then redefine the active-profile set from inside itself. Put profile selection in unconditional configuration or another higher-level Environment source.

**Purpose:**

Show why profile-selection properties are restricted to unconditional configuration.

## Multiple Active Profiles and Last-Wins Behavior

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:15–06:25`

**Visual:**

Replace the invalid circular example with two valid active profiles in an explicit order: `prod,live`.

**Script:**

When several profiles are validly active, their order can still change the effective Config Data value.

**Purpose:**

Move from declaration validity to ordering among multiple active profiles.

### Scene 8 — Active Profile Order Can Change the Winner

**Time:** `06:25–07:10`

**Visual:**

Show `--spring.profiles.active=prod,live`, then `application-prod.properties` followed by `application-live.properties`. Put the same key in both and highlight the `live` value. Add a callout that location groups also affect processing order.

**Script:**

Boot uses a last-wins strategy for profile-specific Config Data. With `prod,live`, a competing value from the later `live` profile can override the earlier `prod` value at the relevant location-group level. Treat profile order as part of the configuration contract instead of assuming active profiles are an unordered set.

**Purpose:**

Teach the last-wins effect of multiple active profiles and its relationship to location grouping.

## Profiles vs Ordinary Property Overrides

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:10–07:20`

**Visual:**

Return to the opening comparison between a coherent profile variant and one isolated deployment value.

**Script:**

With activation and ordering clear, we can make the original design choice more precisely.

**Purpose:**

Close the chapter by applying the learned profile mechanics to mechanism selection.

### Scene 9 — Profiles Activate Variants; Overrides Replace Values

**Time:** `07:20–08:05`

**Visual:**

Show a decision card: coherent named configuration variant -> profile; single deployment-specific key -> external file or environment variable. Under both paths show the same downstream `Environment`.

**Script:**

Use a profile when a named variant activates a coherent set of related configuration. Use an ordinary property override when one value simply differs for this deployment. The mechanisms can work together: a profile may choose a baseline variant while an environment variable still overrides one key. Choose the path that makes the deployment easiest to explain.

**Purpose:**

Leave the learner with a practical boundary between profile activation and ordinary property overrides.
