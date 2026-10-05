<a id="back-to-top"></a>

# Annotation-Driven Pointcuts and Declarative Contracts

## Menu
- [1. The annotation as a contract](#annotation-contract)
- [2. Demo in this module](#annotation-demo)
- [3. When is an annotation-based pointcut appropriate?](#annotation-vs-expression)
- [4. Conclusion](#annotation-conclusion)

A custom annotation lets us describe cross-cutting behavior declaratively.

## <a id="annotation-contract">1. The annotation as a contract</a>

<details>
<summary>Click for details</summary>

An annotation-based pointcut is useful when application code should state its intent explicitly rather than be selected only by package or naming conventions.

The module defines:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackExecution {
    String value();
}
```

The two meta-annotations are part of the contract:

```text
@Retention(RUNTIME)
→ the annotation remains available at runtime
→ advice can bind/read its metadata

@Target(METHOD)
→ this contract is intended for methods
```

A business method can opt in:

```java
@TrackExecution("annotation-demo")
public String executeTrackedOperation() {
    ...
}
```

The annotation itself does not execute timing code and does not create a proxy. It is metadata. An aspect supplies the implementation:

```java
@Around("@annotation(trackExecution)")
public Object track(
        ProceedingJoinPoint joinPoint,
        TrackExecution trackExecution) throws Throwable {
    ...
}
```

Here `@annotation(trackExecution)` does two jobs: it selects method executions carrying `@TrackExecution` and binds the actual annotation instance into the advice parameter. The advice can then read `trackExecution.value()` without manually looking up reflection metadata.

This creates a readable contract:

```text
target method
→ declares intent with @TrackExecution

aspect
→ implements the shared policy

proxy + pointcut
→ connect the declaration to runtime advice
```

The proxy requirement still applies. Metadata on a method does not make a direct, unproxied invocation interceptable.

### References

- Spring Framework Reference — Declaring a Pointcut
- Spring Framework Reference — Declaring Advice

</details>

- [Back to top](#back-to-top)

---

## <a id="annotation-demo">2. Demo in this module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AnnotationDrivenController#trackByAnnotation()
```

Endpoint:

```text
GET /aop/annotation/track
```

Target:

```text
AnnotationDrivenService#executeTrackedOperation()
```

Aspect:

```text
TrackingAspect#track(...)
```

Pointcut:

```text
@annotation(trackExecution)
```

The target declares `@TrackExecution("annotation-demo")`. When the invocation crosses the proxy, the pointcut matches and the advice receives that annotation instance.

The events make both pieces observable:

```text
track-before:label=annotation-demo
target:executeTrackedOperation
track-success:method=executeTrackedOperation
track-finished:elapsed-nanos=...
```

The label proves metadata binding; the target event proves that the advice continued the invocation; the final timing event proves the aspect can surround the method while keeping the business method free of timing code.

</details>

- [Back to top](#back-to-top)

---

## <a id="annotation-vs-expression">3. When is an annotation-based pointcut appropriate?</a>

<details>
<summary>Click for details</summary>

Use an annotation contract when the cross-cutting policy is meaningful enough that a reader should see the opt-in at the method declaration:

```text
@TrackExecution
@Audited
@Measured
```

This has several advantages:

- intent is visible next to the operation;
- metadata can carry policy parameters;
- refactoring package names is less likely to change matching accidentally;
- the aspect can bind the annotation directly.

An expression-based pointcut can be better when the policy belongs to an architectural boundary that should apply consistently without annotating every method, for example all public operations in a service package.

The trade-off is visibility versus central selection:

```text
annotation contract
→ explicit opt-in at the method

structural expression
→ centralized rule based on type/package/signature
```

Avoid marker annotations whose consequences are difficult to infer or whose semantics depend on hidden ordering between many aspects. The annotation should name a stable intent, and the corresponding aspect should implement one clear cross-cutting policy.

Finally, annotation presence does not override proxy semantics. Self-invocation or a direct target reference can still bypass the advice even when the method carries the annotation.

</details>

- [Back to top](#back-to-top)

---

## <a id="annotation-conclusion">4. Conclusion</a>

<details>
<summary>Click for details</summary>

An annotation-driven pointcut works well when application code should declare a cross-cutting intent explicitly.

Keep the layers separate:

```text
annotation
→ metadata / intent

pointcut
→ selection and optional binding

advice
→ policy implementation

proxy
→ runtime interception boundary
```

That separation makes the contract easier to reason about and prepares the next chapter, where advice lifecycle determines exactly when each policy action runs.

</details>

- [Back to top](#back-to-top)
