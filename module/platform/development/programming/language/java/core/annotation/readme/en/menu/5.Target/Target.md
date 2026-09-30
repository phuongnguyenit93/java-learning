# Targets and Annotation Use Sites

Retention decides how long metadata survives. Target answers a different question: **where is this annotation meaningful and therefore legal to write?** A precise target lets the compiler reject annotation uses that do not fit the metadata contract.

## <a id="annotation-use-sites">Declaration and Type-use Annotations</a>

Annotations can describe **declarations** such as classes, methods, fields, parameters, and modules. Java also supports annotations on **uses of a type**.

Declaration-oriented example:

```java
@Audit(action = "PAYMENT")
void pay(@RequestId String requestId) {
}
```

Type-use example:

```java
List<@NonNull String> names;

@NonNull String findName() {
    return "Ada";
}
```

The same visual `@Name` syntax can therefore participate in different semantic locations. The annotation type's `@Target` declaration decides which locations are legal.

This distinction matters for tools. A framework interested in method declarations asks a different question from a static analyzer interested in nullness of a nested generic type argument.

When one source occurrence is legal in both a declaration context and a type context, Java can treat it according to both applicable locations. Runtime APIs reflect the same distinction: declaration annotations are exposed through `AnnotatedElement`-style APIs, while runtime-retained type annotations are inspected through the `AnnotatedType` family.

## <a id="elementtype-targets">`ElementType` Targets</a>

`@Target` accepts one or more `ElementType` constants:

```java
@Target(ElementType.METHOD)
public @interface Audit {
    String action();
    int level() default 1;
}
```

Now this is valid:

```java
class PaymentService {
    @Audit(action = "PAYMENT")
    void pay() {
    }
}
```

but applying `@Audit` to a class is rejected because the annotation contract says it belongs on methods.

Java 21's `ElementType` includes locations such as:

```text
TYPE
→ class, interface (including annotation interface), enum, record declaration

FIELD
→ field declaration

METHOD
→ method declaration

PARAMETER
→ formal parameter declaration

CONSTRUCTOR
→ constructor declaration

LOCAL_VARIABLE
→ local variable declaration

ANNOTATION_TYPE
→ annotation interface declaration

PACKAGE / MODULE
→ package or module declaration

TYPE_PARAMETER
→ type parameter declaration such as <@A T>

TYPE_USE
→ use of a type in supported type contexts

RECORD_COMPONENT
→ record component declaration
```

### Where do `PACKAGE` and `MODULE` annotations appear?

These targets are easy to misread because they are not written inside an ordinary class.

A package annotation is typically placed in `package-info.java`:

```java
@InternalPackage
package com.example.payment;
```

A module annotation appears in `module-info.java`:

```java
@InternalModule
module com.example.payment {
    exports com.example.payment.api;
}
```

The mental model is unchanged: the annotation describes the **package declaration** or **module declaration**, and a consumer must define what that metadata means.

If an annotation type has no `@Target`, it is applicable in **all declaration contexts**, including type-parameter and record-component declarations, but in **no type contexts**. `TYPE_USE` is therefore still an explicit opt-in for annotating uses of types.

## <a id="type-use-annotation">`TYPE_USE` Annotations</a>

`ElementType.TYPE_USE` allows metadata to describe a **use of a type**, not only the surrounding declaration.

```java
List<@NonNull String> names;
```

Here `@NonNull` describes the `String` used as the list's element type.

Other examples include casts and implemented/extended types:

```java
String value = (@NonNull String) input;

class CachedRepository implements @ReadOnly Repository {
}
```

Type-use annotations are especially useful to static-analysis tools and pluggable type systems because they can express metadata at nested type positions that declaration-only annotations cannot distinguish.

### DISTINCTION — declaration vs the type inside a declaration

Consider a field:

```java
@Important String name;
```

Depending on `@Important`'s targets, the annotation may describe the field declaration, the use of type `String`, or be applicable in both ways. Tool authors should inspect the precise target/location model instead of inferring semantics only from visual placement.

`TYPE_PARAMETER` is also distinct from `TYPE_USE`:

```java
class Box<@TypeParameterMarker T> {
}
```

Use the target that matches the semantic question the consumer actually asks.

One Java-language convenience is easy to miss: an annotation interface targeted with `TYPE_USE` is also applicable in type-declaration and type-parameter declaration contexts. That does not erase the conceptual distinction; tooling still needs to understand which declaration/type location it is inspecting.

### EDGE CASE — local declarations and type annotations use different metadata channels

> **Beginner boundary:** do not memorize class-file representation details here. The important point is that a declaration annotation and a type annotation near the same local variable are different metadata channels.

Declaration annotations on local-variable declarations and lambda formal-parameter declarations are not retained in the binary through the ordinary declaration-annotation channel, even when the annotation type declares `CLASS` or `RUNTIME` retention.

A type annotation on the type used in the corresponding context is a separate metadata channel with its own class-file/runtime representation rules.

So `RUNTIME` retention does not mean every `@...` occurrence in source automatically becomes runtime declaration metadata. Read retention together with the annotation's target and actual use site.

## <a id="target-design">Restrict Annotations to Valid Contexts</a>

A broad target increases the number of places where metadata can be written, but that is not automatically useful.

If `@Audit` describes an executable operation handled by a method interceptor, this is a clear contract:

```java
@Target(ElementType.METHOD)
public @interface Audit {
    String action();
    int level() default 1;
}
```

Allowing fields, parameters, modules, and arbitrary type uses would raise questions the consumer may not know how to answer.

```text
consumer supports methods only
→ METHOD target communicates and enforces that boundary

consumer supports classes and methods with defined semantics for both
→ {TYPE, METHOD} may be appropriate
```

Record components deserve the same precision. An annotation on a record component can propagate to the corresponding component field and to an **implicitly declared** accessor/canonical-constructor parameter when the annotation type is also applicable in those contexts. It is not automatically copied to an explicitly declared accessor, and the formal parameters of an explicitly declared canonical constructor may carry different annotations. Do not assume `RECORD_COMPONENT` means “copy this metadata everywhere”.

### PITFALL — “allow everywhere” weakens the schema

Treat `@Target` as part of annotation design, not as an obstacle to remove when compilation fails. If a new use site is genuinely meaningful, define its semantics first and then widen the target intentionally.

The next chapter asks how an annotation such as `@Audit` declares both its retention and target. The answer is metadata applied to the annotation type itself: **meta-annotations**.
