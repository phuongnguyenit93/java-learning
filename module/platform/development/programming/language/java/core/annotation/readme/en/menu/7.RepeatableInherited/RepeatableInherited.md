# Repeatable and Inherited Annotations

Repeatability and inheritance affect annotation lookup in ways that are easy to misread from source syntax. A method may visibly contain two `@Audit` annotations while the class file represents them through a container; a subclass may appear to “have” an annotation even though it was declared only on a superclass.

The important skill is to distinguish **what was declared** from **what a particular lookup API returns**.

## <a id="repeatable-container">Repeatable Annotations and Their Container</a>

Making an annotation repeatable requires two annotation types: the repeated annotation and its container.

Without `@Repeatable`, writing the same annotation type more than once at the same context is a compile-time error. Repeatability is therefore an explicit part of the annotation contract, not just a formatting convenience.

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

Source code can now repeat `@Audit`:

```java
@Audit(action = "AUTHORIZE")
@Audit(action = "CAPTURE")
void pay() {
}
```

Conceptually, the repeated form corresponds to a container holding multiple values:

```java
@Audits({
        @Audit(action = "AUTHORIZE"),
        @Audit(action = "CAPTURE")
})
void pay() {
}
```

The compiler validates the container contract. Its `value()` must return `Audit[]`, and the two annotation types must remain compatible. In particular:

- the container's retention must be at least as long-lived as the repeated annotation's retention;
- the repeated annotation must be applicable to at least the same declaration/type-use kinds as its container; the container may intentionally be narrower, which also narrows where repetition is legal;
- every container element other than `value()` must declare a default;
- if the repeated annotation is `@Documented`, the container must also be `@Documented`;
- if the repeated annotation is `@Inherited`, the container must also be `@Inherited`.

These rules prevent repeated source syntax from silently changing the metadata contract when the compiler represents multiple annotations through the container.

### PITFALL — the container is observable metadata

The container is not purely hidden syntax sugar. Low-level reflection or tools that ask for the container type can observe it. Libraries that support repeatable annotations should normally use the `...ByType(...)` APIs designed to flatten repeated values instead of assuming one physical annotation representation.

## <a id="get-annotations-by-type">Retrieving Repeated Annotations</a>

Before choosing an API, distinguish the four lookup terms Java uses:

```text
directly present
→ annotation T is attached directly to the element

indirectly present
→ annotation T is inside the directly present container of a repeatable annotation

present
→ directly present
→ or, for a class and an @Inherited annotation type, found through the superclass rule

associated
→ directly present or indirectly present
→ or, for a class and an @Inherited annotation type, fallback to a superclass when no local associated T exists
```

That vocabulary explains the core lookup APIs:

| API | Unwraps repeatable container | Uses superclass `@Inherited` lookup |
| --- | --- | --- |
| `getDeclaredAnnotation(T)` | no | no |
| `getAnnotation(T)` | no | yes for classes when `T` is `@Inherited` |
| `getDeclaredAnnotationsByType(T)` | yes | no |
| `getAnnotationsByType(T)` | yes | yes for classes when `T` is `@Inherited` |
| `isAnnotationPresent(T)` | same single-annotation presence semantics as `getAnnotation(T) != null` | correspondingly |

This is why `getAnnotation(T)` should not be treated as “return the first repeated annotation”.

For repeatable annotations, prefer APIs that retrieve annotations **by type**:

```java
Method method = PaymentService.class.getDeclaredMethod("pay");

Audit[] audits = method.getAnnotationsByType(Audit.class);

for (Audit audit : audits) {
    System.out.println(audit.action());
}
```

`getAnnotationsByType(Audit.class)` understands the repeatable/container relationship and returns the individual `Audit` instances in the associated order.

By contrast, asking for one annotation does not mean “give me the first repeated one”:

```java
method.getAnnotation(Audit.class);
```

When multiple `@Audit` uses are represented through the container, `getAnnotation(Audit.class)` does not unpack that container into an arbitrary single result.

### DECLARED vs inherited lookup

Two families are useful to separate:

```text
getDeclaredAnnotationsByType(...)
→ annotations associated directly/indirectly with this element
→ no superclass inheritance search

getAnnotationsByType(...)
→ includes the applicable inherited class lookup semantics
→ relevant when the annotation type is @Inherited
```

For methods, fields, and other non-class elements, `@Inherited` does not introduce superclass lookup behavior.

For an inherited repeatable annotation on a class, this is a **fallback lookup**, not accumulation across the whole hierarchy. If the current class already has an associated annotation of that type, `getAnnotationsByType(...)` uses that local associated set rather than merging it with repeated values from every superclass.

## <a id="inherited-class-only">`@Inherited` Applies to Class Inheritance Only</a>

Consider a runtime annotation intended for service classes:

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

`PaymentService.class.getAnnotation(AuditedComponent.class)` can return the annotation inherited from `BaseService`.

The lookup follows the superclass chain when the annotation type is marked `@Inherited` and the subclass does not already provide the relevant annotation itself.

```text
BaseService declares @AuditedComponent
        ↓ superclass relation
PaymentService
        ↓ Class annotation lookup
inherited annotation can be observed
```

`@Inherited` only has this special meaning for annotations on class declarations. It is not a general rule that copies metadata to every member of every subtype.

### REPEATABLE + INHERITED

A repeatable annotation can also participate in inherited class lookup, but the repeated annotation and its container must be designed consistently. If a repeated annotation type is `@Inherited`, its containing annotation type must satisfy the corresponding inheritance requirement as well.

Use `getAnnotationsByType(...)` when the consumer wants the logical repeated values together with the API's inherited-class behavior.

## <a id="annotation-inheritance-boundaries">Method and Interface Inheritance Boundaries</a>

Two common assumptions are incorrect:

```text
interface is annotated
→ implementing class automatically inherits that annotation

superclass method is annotated
→ overriding method automatically inherits that method annotation
```

Neither follows from `@Inherited`.

### Interface boundary

```java
@AuditedComponent
interface PaymentPort {
}

class CardPayment implements PaymentPort {
}
```

`@Inherited` does not make `CardPayment` inherit an annotation from `PaymentPort`. Frameworks that want interface metadata must explicitly inspect interfaces according to their own lookup policy.

### Method boundary

```java
class BaseService {
    @Audit(action = "PAYMENT")
    void pay() {
    }
}

class PaymentService extends BaseService {
    @Override
    void pay() {
    }
}
```

The overriding `PaymentService.pay()` method does not automatically acquire `@Audit` from `BaseService.pay()` merely because the method overrides it. A framework may deliberately search overridden methods, but that is framework behavior, not Java's `@Inherited` contract.

### PRACTICE — define lookup semantics as part of framework design

When writing an annotation consumer, document whether it searches:

- only the exact declaration;
- superclasses;
- implemented interfaces;
- overridden methods;
- repeatable containers.

Java reflection provides building blocks, but a framework's broader “find merged metadata” behavior can be richer than the core language rules.

Runtime lookup is only one way to consume annotations. The final chapter moves to a different lifecycle entirely: compile-time annotation processing, where tools inspect source/program models and can generate new artifacts before the application runs.
