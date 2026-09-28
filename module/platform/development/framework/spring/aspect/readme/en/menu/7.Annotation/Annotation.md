<a id="back-to-top"></a>

# Custom Annotation-Based Aspect

## Menu
- [1. The annotation as a contract](#annotation-contract)
- [2. Demo in this module](#annotation-demo)
- [3. When is an annotation-based pointcut appropriate?](#annotation-vs-expression)
- [4. Conclusion](#annotation-conclusion)

A custom annotation lets us describe cross-cutting behavior declaratively.

## <a id="annotation-contract">1. The annotation as a contract</a>

<details>
<summary>Click for details</summary>

The module defines:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackExecution {
    String value();
}
```

`RUNTIME` is required because the Aspect reads metadata while the application is running.

`METHOD` indicates that the annotation is intended only for methods.

A method can declare:

```java
@TrackExecution("annotation-demo")
public String executeTrackedOperation() {
    ...
}
```

The business method only declares **which behavior it wants**; the timing/tracking implementation stays in the Aspect.

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

The events show that the Aspect can read both annotation metadata and the method:

```text
track-before:label=annotation-demo
target:executeTrackedOperation
track-success:method=executeTrackedOperation
track-finished:elapsed-nanos=...
```

</details>

- [Back to top](#back-to-top)

---

## <a id="annotation-vs-expression">3. When is an annotation-based pointcut appropriate?</a>

<details>
<summary>Click for details</summary>

Annotations are a good fit when the behavior can be treated as an explicit contract:

```text
@TrackExecution
@Audited
@Measured
```

This is often easier to read than a pointcut that depends deeply on package structure or method naming conventions.

However, the annotation does not guarantee that advice will run. The invocation still has to pass through the proxy.

</details>

- [Back to top](#back-to-top)

---

## <a id="annotation-conclusion">4. Conclusion</a>

<details>
<summary>Click for details</summary>

A custom annotation works best when it represents a clear intent and the Aspect implements the corresponding cross-cutting behavior.

Avoid using annotations as mysterious markers that cause side effects which are difficult for readers to predict.

</details>

- [Back to top](#back-to-top)
