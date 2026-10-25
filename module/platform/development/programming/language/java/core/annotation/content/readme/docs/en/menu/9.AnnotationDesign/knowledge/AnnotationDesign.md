# When Should You Use Annotations?

After learning annotation syntax, custom annotation types, `@Retention`, `@Target`, meta-annotations, repeatability, inheritance, and annotation processing, the final question is no longer **“How do I write an annotation?”** It is:

> **Should this problem actually be represented with an annotation?**

Annotations are descriptive tools. They work well when information belongs close to source code, is relatively stable, and has a clear consumer. They become harmful when they hide too much configuration or control flow that an explicit API or configuration model would communicate more clearly.

This chapter synthesizes the module into an end-to-end design model.

## <a id="annotation-fit">When Are Annotations a Good Fit?</a>

Annotations fit information that:

- naturally belongs to a class, method, field, parameter, or type;
- is relatively stable;
- is declarative rather than algorithmic;
- needs to be consumed by a compiler, tool, or framework;
- benefits from staying close to the code it describes during refactoring.

Examples include:

```text
this method is a test
this class maps to a data model
this field has a validation rule
this method should be audited
this API is deprecated
```

The common question is:

```text
"What is special about this program element?"
```

rather than a step-by-step description of which algorithm must run.

Annotations are especially useful when the consumer is explicit:

```text
metadata
   ↓
consumer understands the contract
   ↓
consumer validates / generates / registers / creates behavior
```

If you cannot identify **who reads the annotation** and **what that consumer does with it**, the annotation is likely to become a label without a clear contract.

## <a id="annotation-alternatives">When Is Another Mechanism Clearer?</a>

Do not turn annotations into containers for:

- complex business logic;
- rapidly changing runtime data;
- secrets;
- large environment-dependent configuration;
- values that must change frequently without recompiling the application;
- dynamic object graphs.

If the information belongs more naturally in an object, database, or configuration file, use that mechanism instead.

A practical comparison:

| Need | Often clearer mechanism |
| --- | --- |
| Small, stable declarative facts attached to code | Annotation |
| Require a type to provide behavior | `interface` / abstract type |
| Require a caller to invoke an operation explicitly | Method / explicit API |
| Deployment-specific values | Configuration file / environment / configuration object |
| Frequently changing runtime data | Object / database / service |
| Secrets | Secret store / secure configuration mechanism |

For example, if every service must implement `audit()`, an interface may communicate that behavioral contract more directly than a marker annotation.

If the requirement is instead **“this method should be observed by an auditing component”**, an annotation may be a better fit because the information describes the method rather than a method the object must implement itself.

## <a id="annotation-end-to-end">The End-to-End Annotation Model</a>

The whole module can be viewed as a metadata pipeline:

```text
define a metadata vocabulary
→ elements + defaults
→ retention: how long metadata survives
→ target: where metadata is legal
→ meta-annotations: configure the annotation contract
→ repeatable/inherited: lookup semantics
→ compile-time processor or runtime consumer reads metadata
```

For the running `@Audit` example, the complete design flow is:

```text
Problem
→ describe which methods require auditing
        ↓
Annotation schema
→ action, level
        ↓
@Target
→ METHOD if the contract only makes sense on methods
        ↓
@Retention
→ RUNTIME when a runtime component must read it
→ SOURCE when a compile-time processor is the only consumer
        ↓
Consumer
→ processor / Reflection / framework
        ↓
Behavior
→ validate / generate / register / audit
```

The invariant across the entire module is:

> **An annotation describes metadata. Behavior comes from the consumer that reads and interprets that metadata.**

That is why the same `@Something` syntax can lead to completely different effects depending on its consumer.

## <a id="annotation-overuse-pitfalls">Annotation Overuse and Design Pitfalls</a>

### 1. Hidden control flow

Code such as:

```java
@Transactional
@Audited
@Secured
void transfer() {
}
```

may trigger substantial behavior outside the method body. That is not inherently wrong, but developers must be able to identify which consumer owns each annotation and what ordering or conditions apply.

If important behavior cannot be traced back to a clear contract or documentation, annotations are making the code harder to reason about.

### 2. Framework coupling

Framework-owned annotations can reduce visible boilerplate while still creating strong coupling to framework semantics.

Keep this distinction clear:

```text
Java annotation mechanism
≠
framework behavior triggered by an annotation
```

### 3. Incorrect `@Retention`

```text
runtime consumer
+ SOURCE retention
→ metadata disappears before the consumer can read it
```

The opposite mistake is making everything `RUNTIME` because it appears “safer”. That broadens the contract unnecessarily and can imply that runtime inspection is part of the design when it is not.

### 4. Overly broad `@Target`

If an annotation only makes sense on methods but also allows `FIELD`, `TYPE`, and `PARAMETER`, the compiler can no longer prevent meaningless placements.

### 5. Turning annotations into a large configuration language

An annotation with dozens of elements, sentinel values, interacting flags, and override rules can be harder to understand than a typed configuration object.

```java
@Feature(
        mode = Mode.AUTO,
        fallback = true,
        cache = true,
        retries = 3,
        async = true,
        strict = false
)
```

When metadata starts encoding a complex algorithm or many interacting flags, a real type, builder, or configuration object can make behavior clearer and easier to test.

### 6. Default values evolve the contract

Default values belong to the annotation type. When a consumer reads an annotation use that omitted an element, changing the annotation type's default can change the observed value. Defaults are therefore part of the compatibility contract, not merely syntax sugar.

## <a id="annotation-design-checklist">Annotation Design Checklist</a>

Before defining or adopting an annotation, ask:

```text
1. What does the annotation describe?
2. Why should this information live next to source code?
3. Who consumes it?
4. Does the consumer run at compile time or runtime?
5. Where is the annotation legal?
6. How long must the metadata survive?
7. Does it need repetition or class-inheritance lookup?
8. Will the consumer validate, generate, register, or create behavior?
9. Would an explicit API, interface, object, or configuration be clearer?
10. Can a developer reading the code discover the relevant contract and behavior?
```

With those questions answered, `@Something` becomes a complete design model rather than unexplained syntax:

```text
Annotation
→ structured metadata
→ has a schema
→ has a use-site contract
→ has a lifetime
→ has a consumer
→ the consumer creates the effect
```

For deeper runtime inspection mechanics, the `reflection` module owns the detailed Reflection model. The Annotation module ends at annotation contracts, annotation design, and how consumers interact with that metadata.
