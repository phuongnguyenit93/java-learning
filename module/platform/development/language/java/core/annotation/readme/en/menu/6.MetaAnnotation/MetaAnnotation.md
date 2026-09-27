# Meta-Annotations

After defining `@Audit`, Java still needs answers to questions about the annotation type itself:

```text
Where may @Audit appear?
→ method, class, field...?

How long must @Audit survive?
→ source, class file, or runtime?

Should documentation include it?
Does it participate in superclass lookup?
May it repeat?
```

If those rules existed only in comments, the compiler and tools would have no standard contract to enforce. The **annotation type itself therefore needs metadata describing its own behavior**.

A meta-annotation is an annotation applied to an annotation type. It configures how that annotation participates in Java's metadata model.

The running example can now state its complete basic contract:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Audit {
    String action();
    int level() default 1;
}
```

Each meta-annotation answers a different question.

## <a id="retention-meta">`@Retention`</a>

`@Retention` declares the retention policy of an annotation type:

```java
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {
    String action();
    int level() default 1;
}
```

Its value is one of `SOURCE`, `CLASS`, or `RUNTIME`.

If `@Retention` is absent, the annotation type uses `RetentionPolicy.CLASS` by default. That default is easy to overlook, so public metadata contracts usually benefit from declaring the intended retention explicitly.

```text
consumer runs during source compilation
→ SOURCE may be enough

consumer reads class-file metadata
→ CLASS may be enough

consumer uses runtime reflection
→ RUNTIME required
```

`@Retention` configures availability; it does not define where the annotation may be written or what behavior a framework should perform.

## <a id="target-meta">`@Target`</a>

`@Target` declares the program locations at which an annotation type may appear.

```java
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface FeatureFlag {
    String value();
}
```

The compiler enforces this target contract at annotation use sites.

For the `@Audit` example, method-only targeting keeps the metadata aligned with a consumer that intercepts operations:

```java
@Target(ElementType.METHOD)
public @interface Audit {
    String action();
    int level() default 1;
}
```

Retention and target should be designed together but answer separate dimensions:

```text
@Retention
→ when is metadata still available?

@Target
→ where is metadata legal?
```

### Custom meta-annotations and the composition boundary

Meta-annotations are not limited to the JDK's predefined annotation types. An application or framework can define metadata whose target is another annotation type:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface FrameworkStereotype {
}

@FrameworkStereotype
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AuditedComponent {
}
```

Here `@FrameworkStereotype` describes the annotation type `@AuditedComponent` itself.

Java Core does **not** automatically turn that relationship into transitive/composed annotation semantics:

```java
@AuditedComponent
class PaymentService {
}
```

`PaymentService` is not automatically directly annotated with `@FrameworkStereotype`. A framework that supports stereotypes, composed annotations, or recursive meta-annotation lookup must define and implement that traversal itself.

```text
Java Core meta-annotation
→ metadata on an annotation type

framework composition
→ framework actively traverses/interprets meta-annotations
→ not automatic Java behavior
```

## <a id="documented-meta">`@Documented`</a>

`@Documented` tells documentation tools such as Javadoc that uses of the annotation should be included in the generated API documentation of annotated declarations.

```java
@Documented
@Target(ElementType.METHOD)
public @interface Audit {
    String action();
    int level() default 1;
}
```

This is a documentation contract. `@Documented` does not change runtime reflection, annotation processing, method dispatch, or framework behavior.

Use it when the annotation is part of the API information that consumers of a declaration should see. Purely internal build metadata may not need to appear in public API docs.

## <a id="inherited-meta">`@Inherited` Boundary</a>

`@Inherited` changes how certain runtime annotation queries on **classes** search the superclass chain.

```java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AuditedComponent {
}

@AuditedComponent
class BaseService {
}

class PaymentService extends BaseService {
}
```

For appropriate `Class` annotation lookup methods, `PaymentService` can observe `@AuditedComponent` from `BaseService` when it does not declare its own annotation of that type.

### BOUNDARY — this is not general annotation inheritance

`@Inherited` does **not** mean annotations automatically propagate through every Java relationship:

```text
superclass → subclass class-annotation lookup
→ @Inherited can participate

interface → implementing class
→ no @Inherited propagation rule

overridden method → overriding method
→ method annotation is not inherited by @Inherited

field / constructor / parameter
→ no @Inherited class-hierarchy propagation
```

The Repeatable/Inherited chapter develops these lookup rules in detail.

## <a id="repeatable-meta">`@Repeatable`</a>

### WHY — why does repeatability need an explicit contract?

Suppose one method needs two independent audit labels:

```java
@Audit(action = "SECURITY")
@Audit(action = "COMPLIANCE")
void transfer() {
}
```

Without a repeatable declaration on `Audit`, multiple annotations of the same type at that context are rejected by the compiler.

The manual alternative is an explicit container:

```java
@Audits({
    @Audit(action = "SECURITY"),
    @Audit(action = "COMPLIANCE")
})
void transfer() {
}
```

`@Repeatable` gives callers the cleaner repeated syntax while retaining a defined container contract underneath.

`@Repeatable` allows multiple annotations of the same type to appear at one legal location. Java represents those repetitions through a **container annotation type**.

```java
@Repeatable(Audits.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audit {
    String action();
    int level() default 1;
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audits {
    Audit[] value();
}
```

Client code may then write:

```java
@Audit(action = "AUTHORIZE")
@Audit(action = "CAPTURE")
void pay() {
}
```

The container is part of the annotation type's structural contract, not an implementation detail that can be chosen arbitrarily. Its `value()` element must return an array of the repeated annotation type, and Java enforces compatibility rules between the repeated annotation and its container.

The next chapter focuses on what repetition and `@Inherited` mean when annotations are actually retrieved, especially why `getAnnotationsByType(...)` is different from asking for one annotation directly.
