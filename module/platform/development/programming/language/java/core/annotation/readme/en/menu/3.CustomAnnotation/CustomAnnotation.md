# Custom Annotations

Built-in annotations work because their meaning is well-defined. Custom annotations apply the same idea to a domain, framework, build pipeline, or internal tool: first define the metadata schema, then let a consumer interpret it.

This chapter defines the running `@Audit` example used throughout the remaining annotation chapters.

## <a id="declare-annotation">Declaring Annotation Types</a>

An annotation type is declared with `@interface`:

```java
public @interface Audit {
    String action();
    int level() default 1;
}
```

The declaration creates an annotation interface. Its members describe the metadata that each annotation use may supply.

```java
@Audit(action = "PAYMENT")
void pay() {
}
```

Annotation elements look like no-argument methods in the declaration, but they are not ordinary behavior methods. They cannot declare parameters, type parameters, or a `throws` clause, and their return types must follow the annotation-element restrictions from the previous chapter.

The annotation interface itself is also a specialized language construct. Its direct superinterface is `java.lang.annotation.Annotation`; writing an ordinary interface that extends `Annotation` does not make that interface an annotation type. An annotation interface cannot be generic and does not declare an arbitrary `extends` clause. Its element declarations are metadata accessors, not normal `static`, `default`, or `private` interface behavior methods.

### MENTAL MODEL — schema before consumer

Think of the annotation type as a small schema:

```text
@interface Audit
→ defines which metadata fields exist

@Audit(action = "PAYMENT")
→ supplies one metadata instance

processor/framework/reflection code
→ reads and interprets that instance
```

Declaring an annotation does not automatically register framework behavior. A consumer must still be written or configured.

## <a id="annotation-elements-defaults">Elements and Default Values</a>

Elements may require values or provide defaults:

```java
public @interface Audit {
    String action();
    int level() default 1;
    String[] tags() default {};
}
```

Now callers must provide `action`, while `level` and `tags` are optional:

```java
@Audit(action = "PAYMENT")
void pay() {
}

@Audit(
        action = "REFUND",
        level = 2,
        tags = {"money", "manual-review"}
)
void refund() {
}
```

Defaults are part of the annotation type declaration. They are not fields stored independently in every annotation use.

### DESIGN — defaults carry semantics

A default should mean something stable. For example:

```text
level = 1
→ the absence of an explicit value has a clear interpretation

owner = ""
→ may be ambiguous unless empty string is an intentional sentinel
```

Changing a default can change how existing annotation uses are interpreted even when those source files do not change. Treat defaults as part of the metadata contract.

## <a id="marker-annotation">Marker Annotations</a>

A marker annotation declares no elements:

```java
public @interface InternalOnly {
}
```

Its presence is the entire signal:

```java
@InternalOnly
class DebugEndpoint {
}
```

Markers work well for binary questions such as “does this declaration belong to this category?”. They are less suitable when the metadata naturally needs attributes that callers will soon encode indirectly in names or companion constants.

Do not confuse a **marker annotation type** with marker-style syntax. An annotation type whose elements all have defaults can be written simply as `@Feature`, but it is not a true marker annotation because its schema still contains elements.

```text
presence alone has complete meaning
→ marker annotation can be clear

consumer needs mode, category, owner, priority...
→ explicit elements usually communicate the contract better
```

## <a id="custom-annotation-design">Designing Meaningful Metadata</a>

A useful custom annotation has a narrow, explainable consumer contract. Before creating one, answer four questions:

1. **Who consumes it?** Compiler plugin, annotation processor, framework, runtime reflection, documentation tool?
2. **What decision does it affect?** Validation, registration, generation, routing, auditing, serialization?
3. **Where should it be legal?** Class, method, field, type use, record component, or another location?
4. **How long must it survive?** Source only, class file, or runtime?

For the running example, suppose a runtime auditing component reads methods:

```java
@Audit(action = "PAYMENT", level = 2)
void pay() {
}
```

That design immediately suggests later choices:

```text
runtime consumer
→ likely RUNTIME retention

method-level auditing contract
→ METHOD target

action + level describe the audit decision
→ explicit elements with stable semantics
```

### PITFALL — annotations can become hidden configuration languages

Large annotations with many flags and interactions can become harder to understand than ordinary Java objects or explicit configuration.

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

When metadata starts encoding a complex algorithm, consider whether a real type, builder, or configuration object would make behavior clearer and easier to test.

The next question for `@Audit` is temporal: **how long must this metadata survive so its intended consumer can still see it?** That is the job of retention policy.
