<a id="back-to-top"></a>

# AOP Design Trade-offs, Patterns, and Pitfalls

## Menu
- [Patterns that fit AOP](#practical-patterns)
- [When AOP is the wrong abstraction: prefer explicit composition and visible workflow](#aop-design-tradeoffs)
- [Auditing + timing demo](#practical-demo)
- [Pitfalls to avoid](#aop-pitfalls)
- [AOP Design Trade-offs, Patterns, and Pitfalls Synthesis](#practical-conclusion)

This chapter applies the AOP mental model to design choices and patterns that are closer to real application code.

## <a id="practical-patterns">Patterns that fit AOP</a>

<details>
<summary>Click for details</summary>

Commonly suitable behaviors include:

- execution logging;
- timing or metrics;
- auditing;
- tracing hooks;
- declarative policy checks;
- annotation-driven cross-cutting behavior.

They share a common shape:

```text
the same policy applies across multiple targets
        +
there is a stable interception boundary
        +
the policy is orthogonal to the business workflow
```

A good AOP concern can usually be stated as a policy over method boundaries: "record every audited operation", "time calls in this service layer", or "attach tracing context around these entry points".

The target's business operation should still be understandable when read without the Aspect source. If the Aspect contains a required step of the domain workflow, the abstraction is starting to hide too much.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-design-tradeoffs">When AOP is the wrong abstraction: prefer explicit composition and visible workflow</a>

<details>
<summary>Click for details</summary>

AOP becomes a poor fit when the behavior is part of the domain workflow rather than a stable cross-cutting policy.

Prefer explicit composition when a reader needs to see a dependency or sequence to understand correctness:

```text
reserve inventory
→ charge payment
→ publish order event
```

Those are business steps. Hiding one behind a pointcut makes control flow harder to discover, test, and reason about.

A useful decision test is:

```text
Does the behavior apply to many targets for the same policy reason?
        ↓ yes
Is there a clear, stable method boundary to intercept?
        ↓ yes
Can the target keep its business contract if the policy is viewed separately?
        ↓ yes
AOP may fit
```

Choose explicit collaborators, decorators, protocol-level filters/interceptors, or ordinary method calls when the behavior needs visible data flow, explicit business ordering, or domain-specific branching.

AOP also adds operational costs: proxy type matters, self invocation can bypass advice, pointcuts can drift as packages and annotations change, and multiple Advisors can create hidden ordering dependencies. Those costs are worthwhile only when centralizing the cross-cutting policy makes the overall design easier to understand.

</details>

- [Back to top](#back-to-top)

---

## <a id="practical-demo">Auditing + timing demo</a>

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

## <a id="aop-pitfalls">Pitfalls to avoid</a>

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

## <a id="practical-conclusion">AOP Design Trade-offs, Patterns, and Pitfalls Synthesis</a>

<details>
<summary>Click for details</summary>

The design goal is not merely to remove repeated lines of code. AOP should centralize a stable cross-cutting policy while keeping the main workflow readable and the interception boundary predictable.

If understanding a use case requires reconstructing several hidden pointcuts and ordering rules, explicit composition is usually the clearer design.

</details>

- [Back to top](#back-to-top)
