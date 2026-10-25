<a id="back-to-top"></a>

# AOP and Cross-Cutting Concerns

## Menu
- [What Is a Cross-Cutting Concern and Why Does AOP Exist?](#cross-cutting-concern)
- [Executable Evidence for AOP and Cross-Cutting Concerns](#cross-cutting-demo)
- [AOP and Cross-Cutting Concerns Synthesis](#cross-cutting-conclusion)

This section answers the most basic question: **why does AOP exist?**

## <a id="cross-cutting-concern">What Is a Cross-Cutting Concern and Why Does AOP Exist?</a>

<details>
<summary>Click for details</summary>

A business concern is the behavior that makes a use case valuable: creating an order, reserving inventory, or calculating a price. A cross-cutting concern serves a different role. It is a policy or technical behavior that must be applied across many otherwise unrelated operations.

Typical examples include:

- logging and tracing;
- execution timing and metrics;
- auditing;
- authorization or policy checks.

Without an AOP mechanism, each business method can perform those tasks explicitly:

```text
createOrder()
→ log before
→ business logic
→ log after

cancelOrder()
→ log before
→ business logic
→ log after
```

That code can work, but it spreads one policy through many classes. Changes to the policy then require coordinated edits in places whose main responsibility is something else.

AOP adds another modularization axis:

```text
business classes
→ keep the use-case behavior

aspect
→ owns one cross-cutting policy

pointcut
→ describes where that policy applies
```

This complements object-oriented design. It does not replace classes, services, or explicit collaboration between objects. The target method still owns the business behavior; the aspect owns behavior that can be described independently at a stable interception boundary.

A useful design test is:

```text
Can the concern be described as a reusable policy
that applies to a predictable set of method executions?
```

If yes, AOP may be a good fit. If the behavior is really a multi-step business workflow whose order is part of the domain, explicit service composition is usually easier to understand than hiding the workflow in advice.

Spring AOP implements this idea with runtime proxies and method interception. That runtime boundary becomes important in the next chapters: a matching pointcut alone does not make every Java call interceptable.

### References

- Spring Framework Reference — Aspect Oriented Programming with Spring
- Spring Framework Reference — AOP Concepts

</details>

- [Back to top](#back-to-top)

---

## <a id="cross-cutting-demo">Executable Evidence for AOP and Cross-Cutting Concerns</a>

<details>
<summary>Click for details</summary>

The module keeps the business behavior and the cross-cutting behavior visibly separate.

Controller:

```text
CrossCuttingController#observeCrossCutting()
```

Endpoint:

```text
GET /aop/cross-cutting/observe
```

Target methods:

```text
CrossCuttingService#createOrder()
CrossCuttingService#cancelOrder()
```

Cross-cutting behavior:

```text
CrossCuttingLoggingAspect#logAround(...)
```

The response `events` is expected to show the policy surrounding each target invocation:

```text
logging-before:createOrder
target:create-order
logging-after:createOrder
logging-before:cancelOrder
target:cancel-order
logging-after:cancelOrder
```

Open `CrossCuttingService` and verify that the two business methods do not emit `logging-before` or `logging-after` themselves. Then inspect the aspect: it owns that repeated behavior and applies it at selected method-execution boundaries.

The observation matters more than the logging example itself:

```text
caller
→ AOP boundary
→ shared policy
→ business method
```

Later chapters refine every part of this path: the proxy creates the boundary, the pointcut selects method executions, and advice implements the policy.

</details>

- [Back to top](#back-to-top)

---

## <a id="cross-cutting-conclusion">AOP and Cross-Cutting Concerns Synthesis</a>

<details>
<summary>Click for details</summary>

Keep this first mental model:

```text
Business method
→ owns the main use-case behavior

Aspect
→ groups a cross-cutting concern

Pointcut
→ states where the concern applies

Advice
→ performs the concern at those selected invocations
```

Spring AOP is most useful when the cross-cutting policy is stable, reusable, and understandable without reading every target method. The rest of the module explains how Spring turns that design into a runtime chain built around proxies.

</details>

- [Back to top](#back-to-top)
