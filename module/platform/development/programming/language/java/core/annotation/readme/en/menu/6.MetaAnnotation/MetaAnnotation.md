# Meta-Annotations and Annotation-Type Contracts

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

`@Inherited` configures a class annotation type to participate in superclass lookup for applicable runtime annotation queries.

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

This is deliberately narrow metadata configuration rather than general-purpose inheritance:

```text
superclass → subclass class-annotation lookup
→ @Inherited can participate

method / field / constructor / parameter / interface
→ not automatically inherited because of @Inherited
```

Runtime visibility still requires suitable retention, normally `RUNTIME`. The Repeatable/Inherited chapter develops the actual lookup APIs, superclass fallback, and method/interface boundaries in detail.

## <a id="repeatable-meta">`@Repeatable`</a>

`@Repeatable` configures an annotation type so multiple instances can appear at one legal location. The declaration names the **container annotation type** that represents those repeated values.

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

Client code can then write:

```java
@Audit(action = "AUTHORIZE")
@Audit(action = "CAPTURE")
void pay() {
}
```

At this point the key model is the declaration relationship: **repeatable annotation ↔ container annotation**. The next chapter owns the container compatibility rules, representation, and retrieval semantics, including why `getAnnotationsByType(...)` differs from asking for one annotation directly.
