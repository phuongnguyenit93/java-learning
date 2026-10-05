<a id="back-to-top"></a>

# Aspect Composition, Ordering, and Lifecycle

## Menu
- [1. The advice chain is a nested call stack](#ordering-mental-model)
- [2. Aspect instance lifecycle: singleton, perthis, pertarget, pertypewithin](#aspect-instantiation-models)
- [3. Demo in this module](#ordering-demo)
- [4. Ordering should not become a business protocol](#ordering-pitfall)
- [5. Conclusion](#ordering-conclusion)

One join point can match multiple Aspects at the same time.

## <a id="ordering-mental-model">1. The advice chain is a nested call stack</a>

<details>
<summary>Click for details</summary>

When several advisors match one method execution, Spring composes them into an interceptor chain. With around advice, that chain behaves like nested method calls rather than a flat list of callbacks.

Suppose two aspects declare:

```text
Aspect A @Order(1)
Aspect B @Order(2)
```

A lower order value has higher precedence. On the way into the invocation, the higher-precedence aspect runs first; on the way back out, it completes last:

```text
A before
    B before
        target
    B after
A after
```

The reversed exit order follows naturally from the call stack:

```text
A calls proceed()
→ B calls proceed()
→ target returns
→ B resumes
→ A resumes
```

Both `@Order` and Spring's `Ordered` contract can express precedence. If two different aspects have no explicit precedence, do not infer a stable relative order from one run.

There is a second ordering issue inside one aspect. If multiple advice methods of the **same advice type** match the same join point, their source-code order is not a supported precedence contract. When correctness depends on order, separate the concerns into different aspects and order those aspects explicitly, or combine the logic into one advice method.

### References

- Spring Framework Reference — Advice Ordering

</details>

- [Back to top](#back-to-top)

---

## <a id="aspect-instantiation-models">2. Aspect instance lifecycle: singleton, perthis, pertarget, pertypewithin</a>

<details>
<summary>Click for details</summary>

Ordering answers **which advice wraps which**. Aspect instantiation answers a different question: **which aspect instance holds the advice and any aspect state for a given invocation?**

The default @AspectJ instantiation model in Spring is **singleton**:

```text
one aspect bean instance
→ reused for matching advised invocations in that ApplicationContext
```

This is the normal model for stateless cross-cutting policies. If a singleton aspect stores mutable fields that change per invocation, those fields are shared across calls and may be accessed concurrently. Prefer invocation-local variables or thread-safe shared state instead of storing request-specific data in singleton aspect fields.

Spring also supports these @AspectJ per-clause models:

```text
perthis(pointcut)
→ associate a distinct aspect instance with each unique matching this object

pertarget(pointcut)
→ associate a distinct aspect instance with each unique matching target object

pertypewithin(TypePattern)
→ associate an aspect instance with each matching target/application type
```

In Spring AOP, `this` means the **AOP proxy object**, while `target` means the underlying target object. Therefore `perthis` and `pertarget` can have different identities even when the same method execution is being advised.

The per-clause controls the aspect-instance association; it does not broaden Spring AOP's join-point model. Advice still participates only in method executions that cross a Spring AOP proxy.

The broader AspectJ models `percflow` and `percflowbelow` are **not supported by Spring AOP**. They depend on control-flow join-point semantics that proxy-based Spring AOP does not provide. Requirements that need those models belong to full AspectJ weaving rather than Spring AOP proxies.

Schema-based Spring AOP aspects use the singleton instantiation model. The alternate per-clause models above belong to the @AspectJ style supported by Spring.

Stateful aspects require particular care: lifecycle changes where state is shared, but it does not turn aspect state into a substitute for explicit business state or request/session state.

### References

- Spring Framework Reference — Aspect Instantiation Models
- Spring Framework Reference — Schema-based AOP Support

</details>

- [Back to top](#back-to-top)

---

## <a id="ordering-demo">3. Demo in this module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
OrderingController#observeOrdering()
```

Endpoint:

```text
GET /aop/ordering/observe
```

Target:

```text
OrderingService#execute()
```

Two aspects participate:

```text
OuterOrderingAspect @Order(1)
InnerOrderingAspect @Order(2)
```

The response `events` is:

```text
order-1:before
order-2:before
target:ordering
order-2:after
order-1:after
```

This is direct evidence of the nested call-stack model. `OuterOrderingAspect` starts first because it has higher precedence, then calls `proceed()` into the inner aspect. After the target returns, the inner aspect completes before control returns to the outer aspect.

The demo establishes ordering only for the two explicitly ordered aspects. It should not be generalized into a promise about unrelated advice that has no declared precedence.

</details>

- [Back to top](#back-to-top)

---

## <a id="ordering-pitfall">4. Ordering should not become a business protocol</a>

<details>
<summary>Click for details</summary>

Explicit ordering is useful when cross-cutting infrastructure has a legitimate precedence relationship. For example, one policy may need to establish context before another policy observes the invocation.

But a chain such as:

```text
Aspect A must run before B
B mutates business data for C
C must run before D
D decides whether the use case can continue
```

is a sign that domain workflow has been hidden inside advice ordering.

Business sequencing belongs in explicit application/domain abstractions where callers, inputs, outputs, and failure paths are visible. Use AOP ordering for composition of cross-cutting policies, not as an invisible workflow engine.

Also distinguish **required order** from **observed order**. The Pointcut chapter has separate `this(...)` and `target(...)` advice methods that can both match. Because their relative precedence is not declared, the experiment only teaches that both match; it does not teach which log line must appear first.

</details>

- [Back to top](#back-to-top)

---

## <a id="ordering-conclusion">5. Conclusion</a>

<details>
<summary>Click for details</summary>

Keep two composition models separate:

```text
advisor ordering
→ determines how matching advice nests in the invocation chain

aspect instantiation
→ determines which aspect instance owns the advice/state
```

For ordinary application AOP, stateless singleton aspects plus explicit ordering only where needed are usually the easiest model to reason about. Alternate per-clause lifecycles are specialized tools, and control-flow lifecycles such as `percflow` remain outside Spring AOP.

</details>

- [Back to top](#back-to-top)
