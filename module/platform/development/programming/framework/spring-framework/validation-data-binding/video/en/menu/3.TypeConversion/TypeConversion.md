---
video:
  url: ""
---

# Spring Type Conversion

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

## ConversionService as the Runtime Entry Point

<!-- VIDEO_SECTION -->

### Scene 1 — Ask for a conversion, not a converter

**Time:** `00:00–01:00`

**Visual:**

Show a caller invoking `canConvert(String.class, Integer.class)` and `convert("42", Integer.class)`. Behind the `ConversionService` box, animate several possible converter implementations while the caller remains unchanged.

**Script:**

`ConversionService` is the consumer-facing doorway into Spring conversion. The caller asks whether a conversion path exists and requests the target value; it does not select the converter implementation itself. That separation lets binding code depend on one service while registrations evolve independently. Also notice that `canConvert` answers whether a path is available. It does not prove that every runtime value, especially every collection element, will convert successfully.

**Purpose:**

Establish ConversionService as the stable runtime abstraction and separate capability discovery from actual value success.

## ConverterRegistry and Converter Registration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:00–01:10`

**Visual:**

Flip the ConversionService box from a “consume” face to a “configure” face labeled `ConverterRegistry`.

**Script:**

If `ConversionService` is how application code consumes conversion, we need a separate place to decide which conversion strategies exist.

**Purpose:**

Bridge runtime use to conversion-system configuration.

### Scene 1 — Configure once, consume broadly

**Time:** `01:10–02:05`

**Visual:**

Show `DefaultConversionService` receiving `addConverter`, `addConverterFactory`, and a conditional generic converter. Add a warning callout around an overly broad source/target registration.

**Script:**

`ConverterRegistry` owns configuration. It accepts plain converters, converter factories, and generic converters. Central registration is useful because every consumer of the same service sees one coherent policy, but broad registrations also have broad effects. Prefer narrow source and target pairs or explicit conditional matching instead of relying on accidental registration order as business logic.

**Purpose:**

Teach the configuration/consumption split and the impact of global converter registration.

## Converter for One Source-to-Target Pair

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:05–02:15`

**Visual:**

Collapse the registry choices to the smallest card: `Converter<S,T>`.

**Script:**

For the common case, the most flexible SPI is unnecessary. One stable transformation between two types has a simpler contract.

**Purpose:**

Lead from registry choices to the narrowest conversion SPI.

### Scene 1 — One pair, one deterministic rule

**Time:** `02:15–03:10`

**Visual:**

Reveal a `StringToOrderIdConverter` that trims text, parses a positive long, and returns `OrderId`. Highlight “non-null source”, “thread-safe/shareable”, and “IllegalArgumentException for invalid non-null input”.

**Script:**

Use `Converter<S,T>` when one source type has one clear transformation to one target type. Spring passes a non-null source to this contract, and registered converters should be shareable and thread-safe. Keep per-call state inside the method. If a non-null value violates the conversion contract, fail clearly rather than inventing a default. This SPI is ideal when the transformation does not need annotations, generic element types, or target-subtype metadata.

**Purpose:**

Show the contract and design constraints of the simplest conversion SPI.

## ConverterFactory for a Target Type Family

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:10–03:20`

**Visual:**

Fan one `String` source into `Priority`, `Status`, and another enum, all under an `Enum` family bracket.

**Script:**

Sometimes the algorithm stays the same while the requested target subtype changes. That is a family problem rather than many unrelated converter classes.

**Purpose:**

Bridge a single type pair to a coherent family of target types.

### Scene 1 — One source format, one target family

**Time:** `03:20–04:15`

**Visual:**

Show `StringToEnumFactory` receiving `Priority.class` and returning a `String -> Priority` converter, then repeat with `Status.class`. Keep the common `Enum` upper bound visible.

**Script:**

`ConverterFactory<S,R>` lets one source representation convert into related target subtypes. The factory receives the requested target class and returns the converter for that subtype. It works well for a real family, such as enums, where the algorithm is uniform. If the decision depends on field annotations, nested generic types, or other descriptor metadata, a generic conditional converter is a better fit.

**Purpose:**

Give a concrete criterion for choosing ConverterFactory instead of many plain converters or an overly broad generic converter.

## GenericConverter and Conditional Conversion

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:15–04:25`

**Visual:**

Replace raw class labels with richer source and target descriptor cards carrying annotations and generic metadata.

**Script:**

Target subtype alone is still not enough for every rule. Some conversions depend on where the value came from and what metadata surrounds it.

**Purpose:**

Move from class-only matching to descriptor-aware conditional conversion.

### Scene 1 — Use descriptor context only when the rule needs it

**Time:** `04:25–05:25`

**Visual:**

Show a `ConditionalGenericConverter` advertising candidate pairs, then `matches(sourceType, targetType)` checking a target `@EntityRef` annotation before `convert(...)` runs. Highlight that a generic converter may receive a null source.

**Script:**

`GenericConverter` can work with several source and target pairs and receives `TypeDescriptor` on both sides. `ConditionalGenericConverter` adds a `matches` decision so metadata can narrow when the converter applies. That power is useful for annotation-aware or generic-aware conversion, but it also makes a broad converter harder to reason about. Choose it because descriptor context is genuinely part of the semantics, not simply because it is the most flexible API.

**Purpose:**

Explain why generic and conditional conversion exist and guard against using them when a narrower SPI expresses the rule better.

## TypeDescriptor and Conversion Context

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:25–05:35`

**Visual:**

Zoom into the `TypeDescriptor` card and expand annotation, collection-element, map-key/value, and property-location metadata.

**Script:**

The previous converter could make a contextual decision because Spring carries more information than a raw `Class<?> `.

**Purpose:**

Expose the metadata object that makes descriptor-aware conversion possible.

### Scene 1 — Raw type is sometimes too little information

**Time:** `05:35–06:35`

**Visual:**

Compare two `BigDecimal` fields annotated with different currency metadata, then compare `List<Integer>` with `List<UUID>`. Show both pairs having the same raw class while their descriptors differ.

**Script:**

A raw class cannot tell us everything about a conversion target. Two `BigDecimal` fields may carry different annotations, and two lists may erase to the same `List.class` while requiring different element conversions. `TypeDescriptor` preserves property location, annotations, and nested element or map types. Use class-based conversion for straightforward scalar cases; use descriptors when that extra context is part of the actual rule.

**Purpose:**

Show exactly what information TypeDescriptor adds and when that information changes conversion behavior.

## Converter Selection and Conversion Failures

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:35–06:45`

**Visual:**

Turn the descriptor comparison into a two-step flow: “find eligible path” then “convert actual value”.

**Script:**

Once Spring has enough metadata to select a strategy, success still has two separate conditions: a converter must exist, and the concrete value must be convertible.

**Purpose:**

Bridge converter selection metadata to the conversion failure model.

### Scene 1 — Capability does not guarantee every value

**Time:** `06:45–07:45`

**Visual:**

Show path A with no converter and a “no strategy” stop sign. Show path B with a UUID converter selected, then malformed text causing `ConversionException`. Add a collection where one bad element fails after `canConvert` was true.

**Script:**

Conversion can fail because Spring has no eligible strategy for the source and target, or because the selected strategy rejects the actual value. That is why `canConvert` is useful as a capability query but should not be treated as proof that every later value will succeed. A converter should also preserve the difference between missing input, malformed input, and a legitimate value instead of swallowing errors into arbitrary defaults.

**Purpose:**

Distinguish converter lookup failure from runtime conversion failure and explain the practical limit of canConvert.

## Legacy PropertyEditor Integration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:45–07:55`

**Visual:**

Fade the modern conversion diagram into an older JavaBeans `PropertyEditor` card while keeping `DataBinder` connected to both.

**Script:**

Spring’s current conversion system is not its only historical extension point. Binding APIs still expose JavaBeans `PropertyEditor` integration for compatibility.

**Purpose:**

Place the legacy editor model in context without confusing it with the preferred modern conversion abstraction.

### Scene 1 — Keep legacy state scoped

**Time:** `07:55–08:55`

**Visual:**

Compare a stateless shared `Converter` with a mutable `PropertyEditor` holding a current value. Show `DataBinder.registerCustomEditor(...)`, then a recommendation arrow toward `ConversionService` for new reusable conversion policy.

**Script:**

`PropertyEditor` predates `ConversionService` and remains available in low-level binding extension points. The important difference is state: property editors traditionally carry mutable editor state, while registered Spring converters are designed as shareable strategies. For new application-level conversion, prefer `ConversionService` with converters and formatters. Use custom editors when integrating code that genuinely depends on the older model, and avoid defining competing semantics for the same concept in both systems.

**Purpose:**

Explain the legacy boundary and prevent unsafe assumptions that mutable PropertyEditor instances behave like stateless global converters.
