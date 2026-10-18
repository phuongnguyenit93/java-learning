---
video:
  url: ""
---

# Field Formatting

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

## Why Formatting Is Separate from General Conversion

<!-- VIDEO_SECTION -->

### Scene 1 — When text representation is part of the requirement

**Time:** `00:00–01:05`

**Visual:**

Show the number `1234.5` as a typed value. Branch it into US text `1,234.5` and German text `1.234,5`. Beside that, show a context-free `String -> OrderId` conversion with no locale input.

**Script:**

Conversion and formatting can both change representation, but they solve different problems. General conversion asks how one Java value becomes another type. Formatting asks how a value should appear as text, or be parsed from text, for a particular field and locale. When text presentation is part of the requirement, locale and field metadata matter. Keeping that concern in the formatting layer prevents presentation rules from leaking into otherwise reusable converters.

**Purpose:**

Establish formatting as presentation-aware transformation and contrast it with context-independent conversion.

## Printer and Parser Contracts

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:05–01:15`

**Visual:**

Split the two-way text arrow into one outbound arrow labeled `Printer<T>` and one inbound arrow labeled `Parser<T>`.

**Script:**

Once text is the boundary, Spring separates the two directions so each contract says exactly what it does.

**Purpose:**

Move from the formatting problem to its directional primitive contracts.

### Scene 1 — Two directions, the same locale context

**Time:** `01:15–02:15`

**Visual:**

Show `Printer<BigDecimal>.print(value, locale)` producing display text, then `Parser<BigDecimal>.parse(text, locale)` producing a typed value. Run the same percentage through two locale badges and show different text.

**Script:**

`Printer<T>` turns a typed object into display text. `Parser<T>` turns text back into a typed object. Both receive the current `Locale`, which is the crucial context a plain converter does not have. Parsing should reject malformed text instead of quietly inventing a value, so downstream binding can preserve a real failure. Printing should be deterministic for the supplied value and locale and should not depend on mutable request state.

**Purpose:**

Teach the directional formatter contracts and why locale is part of both operations.

## Formatter as Parse and Print

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:15–02:25`

**Visual:**

Merge the Printer and Parser cards into one `Formatter<T>` card.

**Script:**

Most editable fields need both directions. Spring packages that pair into one coherent text contract.

**Purpose:**

Bridge the two directional contracts into the common Formatter abstraction.

### Scene 1 — One coherent text policy

**Time:** `02:25–03:20`

**Visual:**

Show a `LocalDateFormatter` with `parse` and `print` methods using the same `DateTimeFormatter` pattern and locale. Animate a text value through parse, edit the typed date, then print it back.

**Script:**

`Formatter<T>` combines `Printer<T>` and `Parser<T>` for one object type. It is a good fit when the same presentation policy must both display and accept text. The two directions should form a coherent contract. Perfect round trips are not mandatory when display formatting intentionally loses detail, but asymmetry should be deliberate rather than accidental.

**Purpose:**

Show Formatter as a paired parse/print policy and clarify what coherent round-trip behavior means.

## FormatterRegistry and Central Registration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:20–03:30`

**Visual:**

Place several formatter cards into one central registry, then connect multiple consumers to that registry.

**Script:**

A useful formatter still has to be discoverable by the parts of the application that need it. Registration is where local code becomes shared policy.

**Purpose:**

Connect a formatter implementation to centralized application configuration.

### Scene 1 — Central policy with explicit scope

**Time:** `03:30–04:25`

**Visual:**

Show `FormattingConversionService.addFormatter(...)`, `addFormatterForFieldType(...)`, separate printer/parser registration, and annotation formatter registration. Highlight a broad `BigDecimal` formatter affecting every matching field.

**Script:**

`FormatterRegistry` is the configuration side of the formatting system, and it also extends `ConverterRegistry`. It can register a formatter by generic type, a formatter for a specific field type, separate printers and parsers, or annotation-driven factories. Central registration creates consistency, but wide registrations have wide impact. A global formatter should represent a true application-wide default; field-specific exceptions are better expressed explicitly.

**Purpose:**

Show the available registration scopes and the design consequence of making a formatter global.

## Annotation-Driven Field Formatting

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:25–04:35`

**Visual:**

Show two `LocalDate` fields with different format annotations despite sharing the same Java type.

**Script:**

Sometimes the type alone cannot tell Spring which text policy to use. The missing context can live directly on the field.

**Purpose:**

Lead from type-wide registration to metadata-specific formatting.

### Scene 1 — Let field metadata select presentation

**Time:** `04:35–05:35`

**Visual:**

Reveal a custom `@YearMonthText(pattern="MM/uuuu")` and an `AnnotationFormatterFactory` that declares supported field types and returns the matching printer and parser. Highlight the annotation instance feeding the chosen pattern.

**Script:**

`AnnotationFormatterFactory` lets field metadata choose formatting behavior. The factory declares which field types its annotation supports and creates the printer and parser for the actual annotation values. This works when two fields share a Java type but require different textual representation. Keep the annotation focused on presentation; it should not secretly become a business-validation or transport rule.

**Purpose:**

Demonstrate how field annotations add presentation context without redefining domain validation.

## FormattingConversionService

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:35–05:45`

**Visual:**

Place converters and formatters into one service box labeled `FormattingConversionService`, with a single `convert(...)` API on the consumer side.

**Script:**

We now have two families of transformation rules. Spring combines them so callers do not need two unrelated runtime APIs.

**Purpose:**

Connect formatting registration back to the common conversion runtime model.

### Scene 1 — One runtime service, two policy families

**Time:** `05:45–06:45`

**Visual:**

Show `DefaultFormattingConversionService` extending generic conversion and implementing `FormatterRegistry`. Animate a formatter being adapted into the conversion system while the caller still invokes `convert`.

**Script:**

`FormattingConversionService` is where conversion and field formatting meet. It can register both converters and formatters while still serving conversion requests through the familiar `ConversionService` API. `DefaultFormattingConversionService` provides common converters and standard number and Java-time formatting support, but it cannot know application-specific textual contracts. Those still require explicit registration.

**Purpose:**

Explain how Spring presents one coherent runtime transformation service without erasing the semantic difference between conversion and formatting.

## Locale-Sensitive Formatting Boundary

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:45–06:55`

**Visual:**

Zoom into the `Locale` input and draw arrows from a standalone caller and a web framework toward the same formatter.

**Script:**

The formatter receives a locale, but it does not decide where that locale comes from. That ownership boundary is the final piece of this chapter.

**Purpose:**

Separate locale-aware formatting from locale resolution and broader message localization.

### Scene 1 — Locale shapes text, not ownership

**Time:** `06:55–08:00`

**Visual:**

Show `NumberStyleFormatter` printing the same value with `Locale.US` and `Locale.GERMANY`. Then show a separate pipeline where a validation error code goes to `MessageSource`. Add a warning against using locale-formatted text as a stable machine serialization.

**Script:**

Formatting uses `Locale` to interpret and render human-facing text, but the formatter does not own locale resolution. A standalone caller can supply a locale directly; MVC or WebFlux can resolve one from their request lifecycle. Formatting is also different from `MessageSource` localization: a formatter renders values, while `MessageSource` resolves message codes into localized messages. For durable machine-to-machine formats, use explicit locale-independent representations rather than human formatting rules.

**Purpose:**

Define the locale boundary clearly and prevent confusion between field formatting, locale resolution, message localization, and machine serialization.
