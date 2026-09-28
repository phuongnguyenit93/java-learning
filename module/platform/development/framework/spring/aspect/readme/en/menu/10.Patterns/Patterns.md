<a id="back-to-top"></a>

# Practical Patterns and Pitfalls

## Menu
- [1. Patterns that fit AOP](#practical-patterns)
- [2. Auditing + timing demo](#practical-demo)
- [3. Pitfalls to avoid](#aop-pitfalls)
- [4. Conclusion](#practical-conclusion)

The final chapter of the **Fundamentals** section applies the AOP mental model to a pattern closer to real application code.

## <a id="practical-patterns">1. Patterns that fit AOP</a>

<details>
<summary>Click for details</summary>

Commonly suitable behaviors include:

- execution logging;
- timing/metrics;
- auditing;
- tracing hooks;
- declarative authorization boundaries;
- annotation-driven cross-cutting behavior.

They share these characteristics:

```text
behavior appears across multiple targets
        +
clear boundary
        +
not core business logic
```

</details>

- [Back to top](#back-to-top)

---

## <a id="practical-demo">2. Auditing + timing demo</a>

<details>
<summary>Click for details</summary>

Controller:

```text
PracticalPatternController#checkout(String)
```

Endpoint:

```text
GET /aop/patterns/checkout?item=book
```

Business method:

```text
PracticalPatternService#checkout(String)
```

It performs only the main behavior and declares:

```text
@AuditedOperation(action = "checkout")
```

Aspect:

```text
PracticalPatternAspect#audit(...)
```

The response `events` shows:

```text
audit-start:action=checkout
target:checkout:item=book
audit-success:method=checkout
metric:elapsed-nanos=...
```

The business service does not have to implement audit start/success or timing itself.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-pitfalls">3. Pitfalls to avoid</a>

<details>
<summary>Click for details</summary>

### Pointcut too broad

```text
execution(* com.example..*(..))
```

can intercept more methods than intended and make behavior hard to predict.

### Business logic inside an Aspect

If pricing decisions, order state, or the main workflow live inside an Aspect, dependencies become hidden and the system becomes harder to test and debug.

### Silently changing arguments or return values

`@Around` can do this, but technical capability does not automatically make it a good design.

### Swallowing exceptions

An Aspect that catches an exception and returns a fake value can break the target contract and hide failures.

### Too many ordering dependencies

If correctness depends on a complex chain of `@Order` relationships, the abstraction has usually gone beyond what AOP should carry.

### Mutable state in an Aspect

A normal Aspect bean is typically a singleton in the ApplicationContext. A mutable field such as:

```java
private int currentRequestCount;
```

can therefore be accessed by multiple requests/threads and create race conditions or leak state across invocations.

Prefer stateless Aspects. If state is truly required, design the scope and concurrency semantics explicitly rather than assuming each invocation gets its own Aspect instance.

`AopTraceLog` in this learning module uses `ThreadLocal` only to separate events for each request/thread while observing experiments. It is instrumentation for learning, not a reason to place business state into an Aspect's `ThreadLocal`.

</details>

- [Back to top](#back-to-top)

---

## <a id="practical-conclusion">4. Conclusion</a>

<details>
<summary>Click for details</summary>

Final mental model:

```text
good AOP
→ cross-cutting
→ declarative
→ clear boundary
→ predictable behavior

bad AOP
→ hidden business flow
→ overly broad pointcut
→ difficult-to-follow control flow
```

AOP should make business code cleaner without turning runtime behavior into a hidden system that is difficult to understand.

</details>

- [Back to top](#back-to-top)
