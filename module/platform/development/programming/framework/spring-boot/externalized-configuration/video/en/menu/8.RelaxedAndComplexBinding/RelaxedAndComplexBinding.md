---
video:
  url: ""
---

# Relaxed Binding, Complex Types, and Conversion

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

## How Relaxed Binding Normalizes Property Names

<!-- VIDEO_SECTION -->

### Scene 1 — One Logical Property, Several Source Spellings

**Time:** `00:00–00:45`

**Visual:**

Put `customer.first-name` in the center as the canonical key. Around it show `customer.firstName`, `customer.first_name`, and `CUSTOMER_FIRSTNAME`, with arrows converging on Java member `firstName`.

**Script:**

Relaxed binding lets source-specific spellings map to one logical property. That is useful because files, YAML, and environment variables follow different naming conventions. The flexibility belongs to binding, though; it does not mean every configuration consumer performs identical lookup. Keep canonical lower-case kebab case as the documented name.

**Purpose:**

Establish relaxed binding as source adaptation around one canonical property identity.

## Mapping Canonical Properties to Environment Variables

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:45–00:55`

**Visual:**

Keep the canonical key in the center and isolate the environment-variable representation.

**Script:**

Environment variables have stricter naming rules, so Boot defines a deterministic conversion from the canonical key.

**Purpose:**

Move from general relaxed names to the specific environment-variable mapping rule.

### Scene 2 — Derive the Environment Variable, Do Not Invent It

**Time:** `00:55–01:40`

**Visual:**

Transform `spring.main.log-startup-info` in three steps: dots become underscores, dashes disappear, then the result becomes uppercase: `SPRING_MAIN_LOGSTARTUPINFO`. Show `my.service[0].other -> MY_SERVICE_0_OTHER` below it.

**Script:**

Start from the canonical property name, replace dots with underscores, remove dashes, and uppercase the result. Indexed elements place the number between underscores. This gives operations a repeatable rule instead of a collection of one-off environment names.

**Purpose:**

Teach Boot's deterministic environment-variable mapping from canonical property names.

## Binding Nested Configuration Objects

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:40–01:50`

**Visual:**

Turn the flat canonical key into a small property tree with a nested `security` branch.

**Script:**

Naming is only one part of binding. The Java model can also preserve meaningful hierarchy from the configuration tree.

**Purpose:**

Move from property-name normalization to object-shape binding.

### Scene 3 — Let the Object Shape Match the Configuration Domain

**Time:** `01:50–02:35`

**Visual:**

Show `mail.host`, `mail.security.enabled`, and `mail.security.protocol` on the left. On the right show `MailProperties` containing a nested `Security` object and map each branch into its target member.

**Script:**

Nested binding lets the Java configuration model mirror meaningful subgroups in the property hierarchy. A `security` object keeps related settings together and creates a natural validation boundary. Model the configuration concept, not the package structure of the application.

**Purpose:**

Show nested objects as a domain-oriented representation of hierarchical configuration.

## Binding Lists and Sets

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:35–02:45`

**Visual:**

Replace the nested object branch with a repeated `servers` branch containing two elements.

**Script:**

Some configuration values repeat rather than nest into named fields, so the binder also understands collection shapes.

**Purpose:**

Move from nested objects to collection binding.

### Scene 4 — Bind Repeated Values into Collections

**Time:** `02:45–03:30`

**Visual:**

Show a YAML `app.servers` list containing `api-a.example` and `api-b.example`, then map it to `List<String> servers`. Add environment forms `APP_SERVERS_0` and `APP_SERVERS_1` below.

**Script:**

Boot can bind indexed or YAML list values into collection properties and convert each element to the target type. Environment variables can address indexed elements with the underscore convention. The collection still participates in normal source precedence, but a later section shows why ordinary complex lists are replaced rather than merged across sources.

**Purpose:**

Explain how repeated configuration values map into collection targets before cross-source replacement is introduced.

## Binding Maps and Preserving Special Keys

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:40`

**Visual:**

Replace the ordered list with a set of named entries under `labels`.

**Script:**

When the keys themselves are data, a map can be a better target than a fixed object or collection.

**Purpose:**

Move from indexed collections to dynamic key/value binding.

### Scene 5 — Preserve Map Keys Deliberately

**Time:** `03:40–04:25`

**Visual:**

Show `labels.region=eu` and `labels.tier=gold` binding into a map. Then compare `[a.b]` as one preserved key with `a.b` as nested path structure.

**Script:**

Maps fit configuration where the key set is data-driven. Bracket notation preserves characters that would otherwise be interpreted as path separators, so `[a.b]` can stay one map key. Use that feature deliberately; if the configuration starts to resemble a general document store, properties may no longer be the clearest representation.

**Purpose:**

Teach map binding and the bracket notation used to preserve special characters in map keys.

## Why Higher-Precedence Lists Replace Lower-Precedence Lists

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:25–04:35`

**Visual:**

Bring back two list values from two different precedence levels and place them on top of each other.

**Script:**

Collections introduce one important precedence detail: an ordinary complex list is not patched element by element across sources.

**Purpose:**

Connect collection binding to whole-list replacement across property sources.

### Scene 6 — Higher Precedence Replaces the Whole Ordinary List

**Time:** `04:35–04:58`

**Visual:**

Show a lower-precedence `users` list with Alice and Bob and a higher-precedence list with only Carol. Cross out merge arrows and display the effective list containing only Carol.

**Script:**

For ordinary complex-list binding, Boot does not merge elements from several property sources. The higher-precedence participating source replaces the lower list as a whole. That is why changing one element through a later source can replace the entire effective list instead of patching one index.

**Purpose:**

Make whole-list replacement explicit and prevent incorrect element-by-element merge assumptions.

### Scene 7 — Do Not Generalize the Rule to Every List-Shaped Setting

**Time:** `04:58–05:20`

**Visual:**

Keep the replaced `users` list on the left. On the right, show a map whose keys can combine across sources and a separate `spring.profiles.include` card labeled “special Boot processing — evaluate by its own contract.”

**Script:**

List replacement is a binder rule for ordinary complex configuration, not a universal rule for every setting that happens to contain multiple values. Maps can combine keys differently, and Boot features such as `spring.profiles.include` have dedicated processing rules. Diagnose the actual mechanism before assuming merge or replacement behavior.

**Purpose:**

Prevent the ordinary list-binding rule from being overgeneralized to maps or special profile-processing properties.

## Type Conversion During Binding

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:20–05:30`

**Visual:**

Keep the effective property values and place a conversion step between them and typed Java members.

**Script:**

After name and structure matching, the binder still has to turn text-oriented configuration into Java target types.

**Purpose:**

Move from structural binding into the conversion stage.

### Scene 8 — A Found Key Can Still Fail Conversion

**Time:** `05:30–06:15`

**Visual:**

Show `client.timeout=2s` and `client.max-payload=10MB` becoming `Duration` and `DataSize`. Then change `2s` to `fast` and freeze on the conversion failure.

**Script:**

Boot converts resolved strings into numbers, booleans, enums, URIs, durations, sizes, and other target types. If `client.timeout` exists but contains `fast`, source loading succeeded and the key was found; the failure happens because that text cannot become a `Duration`. That is a different diagnostic stage from loading or validation.

**Purpose:**

Distinguish type-conversion failures from missing configuration and later validation failures.

## Duration, DataSize, and Other Common Target Types

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:15–06:25`

**Visual:**

Zoom into the `Duration` and `DataSize` targets and highlight the unit suffixes in their source values.

**Script:**

Some target types become much safer when the configuration carries the unit explicitly instead of relying on an implicit convention.

**Purpose:**

Close the binding chapter by connecting conversion to domain-appropriate target types and units.

### Scene 9 — Put Units in the Configuration Contract

**Time:** `06:25–07:10`

**Visual:**

Show `cache.ttl=30s` entering a `Duration` and `upload.max-size=25MB` entering a `DataSize`. Fade in a bare `30` with a question mark, then show `@DurationUnit` as a compatibility aid.

**Script:**

Values such as `30s`, `250ms`, and `25MB` make time and size semantics visible to both people and Boot. Unit annotations can define how a legacy bare number should be interpreted, but explicit units are clearer for new configuration. A `Duration` also communicates intent more safely than a plain `long` whose unit exists only in documentation.

**Purpose:**

Encourage domain-specific target types and explicit units so configuration intent stays visible.
