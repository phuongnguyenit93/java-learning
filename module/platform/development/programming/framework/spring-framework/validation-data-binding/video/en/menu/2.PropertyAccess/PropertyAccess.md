---
video:
  url: ""
---

# Property Access and Binding State

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

## Property Paths and Nested Access

<!-- VIDEO_SECTION -->

### Scene 1 — A path through writable state

**Time:** `00:00–01:00`

**Visual:**

Draw a `CustomerForm` object tree and animate the path `address.city` segment by segment. Then replace the `address` node with `null` and show a warning that behavior now depends on accessor/binder configuration.

**Script:**

Binding needs a stable way to name where a value belongs. A simple property path such as `name` identifies one property. A nested path such as `address.city` walks through an object graph. The path itself is only target-location metadata; it does not promise that the property is writable, that every intermediate object exists, or that the incoming value can be converted. That is why the same path can later appear in both assignment logic and a `FieldError`.

**Purpose:**

Build the property-path mental model while making clear that a valid-looking path does not guarantee successful access or binding.

## BeanWrapper and the Property Access Abstraction

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:00–01:10`

**Visual:**

Keep `address.city` highlighted and reveal a `BeanWrapper` around the object tree with read, write, descriptor, and conversion icons.

**Script:**

Once we can name a location, something still has to inspect and manipulate that JavaBean state. Spring’s low-level abstraction for that job is `BeanWrapper`.

**Purpose:**

Move from path notation to the property-access mechanism that understands those paths.

### Scene 1 — Property access is lower level than binding

**Time:** `01:10–02:15`

**Visual:**

Show `PropertyAccessorFactory.forBeanPropertyAccess(form)`, then `setPropertyValue("address.city", "Da Nang")`, `getPropertyValue`, and `isWritableProperty`. Beside it, show a larger `DataBinder` box adding field policy, results, and validation around the `BeanWrapper` layer.

**Script:**

`BeanWrapper` can inspect JavaBean properties, navigate nested paths, get and set values, and participate in type conversion and `PropertyEditor` registration. That makes it powerful, but it is still lower level than a binding workflow. `DataBinder` commonly uses this property-access infrastructure and then adds policies such as allowed fields, required fields, structured errors, and validators. Think “manipulate properties” for `BeanWrapper`, and “orchestrate an input operation” for `DataBinder`.

**Purpose:**

Separate low-level JavaBean property manipulation from the higher-level binding policy taught later.

## BindingResult and Errors

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:15–02:25`

**Visual:**

Turn the property operation into an outcome card and split it into a common `Errors` layer and a richer `BindingResult` layer.

**Script:**

Property access can succeed or fail, but callers need more than an exception stream. Spring preserves those outcomes in a structured error model.

**Purpose:**

Bridge property manipulation to the shared result representation used by binding and validation.

### Scene 1 — Structured outcome instead of plain strings

**Time:** `02:25–03:30`

**Visual:**

Show an inheritance diagram: `Errors` above `BindingResult`. Animate `rejectValue("address.city", "city.required")` into a field-error card, then reveal BindingResult-only details such as target, raw field value, suppressed fields, and message-code resolution.

**Script:**

`Errors` is the common contract for registering and querying validation and binding problems. `BindingResult` extends it with details that belong specifically to a binding outcome: the target object, raw values, suppressed fields, property-editor access, and message-code resolution. A validator can stay coupled to `Errors`, while infrastructure that needs the full bind result can use `BindingResult`. The key design choice is that failures remain structured data that later layers can inspect and localize.

**Purpose:**

Show why Spring separates the common error collector from the richer result of a binding operation.

## ObjectError and FieldError

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:40`

**Visual:**

Take one generic error card and split it into “whole object” and “specific field path”.

**Script:**

Structured errors become more useful when we can say whether the problem belongs to one field or to the object as a whole.

**Purpose:**

Lead from the error container to the two primary error shapes learners will see in later binding and validation evidence.

### Scene 1 — Scope the error to the rule

**Time:** `03:40–04:40`

**Visual:**

Show `errors.reject("dateRange.invalid")` attached to an entire form, then `errors.rejectValue("quantity", "quantity.positive")` attached to one field. Reveal a `FieldError` panel containing rejected value and `bindingFailure` metadata.

**Script:**

Use an `ObjectError` when the rule describes combined object state, such as a date range. Use a `FieldError` when a specific property path owns the problem. Both can carry message codes, arguments, and a default message because they are `MessageSourceResolvable`. A field error can also preserve the rejected value and tell consumers whether the failure happened during binding. That metadata lets later layers distinguish a bad conversion from a domain rule violation.

**Purpose:**

Teach learners to choose and interpret object-level versus field-level errors without collapsing them into localized text.

## Direct Errors vs Policy-Dependent Binding Cases

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:40–04:50`

**Visual:**

Place four input cards on a table: bad type, unknown name, inaccessible nested path, and missing configured required field.

**Script:**

Now we can name errors, but not every suspicious input is treated the same way. Some failures are direct; others depend on binder policy.

**Purpose:**

Connect the structured error model to the field-policy distinctions that matter before the DataBinder chapter.

### Scene 1 — Four cases, four policies

**Time:** `04:50–06:05`

**Visual:**

Walk through a comparison table. Highlight: type mismatch as a direct binding failure; unknown field with `ignoreUnknownFields=true` by default; invalid nested field with `ignoreInvalidFields=false` by default; required field only after `setRequiredFields(...)`. Finish with a note that these switches apply to property binding, not constructor binding.

**Script:**

A type mismatch means the property exists but its value cannot be converted, so that is a direct binding failure. An unknown field names no property; `DataBinder` ignores those by default unless configured otherwise. An invalid field names target state that cannot currently be accessed, and those are not ignored by default. A required-field error exists only when the binder has explicitly declared that incoming field required. These are property-binding policies. They should not be confused with constructor binding, and they still do not define the secure write surface by themselves.

**Purpose:**

Give learners a precise failure taxonomy before later chapters introduce DataBinder field-policy and safe-binding controls.
