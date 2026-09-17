<a id="back-to-top"></a>

# Multiple Aspects and Ordering

## Menu
- [1. The advice chain is a nested call stack](#ordering-mental-model)
- [2. Demo in this module](#ordering-demo)
- [3. Ordering should not become a business protocol](#ordering-pitfall)
- [4. Conclusion](#ordering-conclusion)

One join point can match multiple Aspects at the same time.

## <a id="ordering-mental-model">1. The advice chain is a nested call stack</a>

<details>
<summary>Click for details</summary>

Suppose two `@Around` Aspects match the same target:

```text
Aspect A @Order(1)
Aspect B @Order(2)
```

With the current precedence, the Aspect with the lower order wraps the outside of the call chain:

```text
A before
    B before
        target
    B after
A after
```

The exit order is reversed because this is a nested invocation, not four independent callbacks executed in a flat sequence.

`@Order`/precedence must be explicit if correctness depends on ordering. If two advice methods match without a clear precedence contract, **do not treat the log order observed in one execution as a guarantee**.

</details>

- [Back to top](#back-to-top)

---

## <a id="ordering-demo">2. Demo in this module</a>

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

Two Aspects:

```text
OuterOrderingAspect @Order(1)
InnerOrderingAspect @Order(2)
```

Response `events`:

```text
order-1:before
order-2:before
target:ordering
order-2:after
order-1:after
```

This is direct evidence of the nested mental model.

</details>

- [Back to top](#back-to-top)

---

## <a id="ordering-pitfall">3. Ordering should not become a business protocol</a>

<details>
<summary>Click for details</summary>

`@Order` is useful when multiple cross-cutting concerns need explicit precedence, but a dependency chain such as:

```text
Aspect A must run before B,
B must change data for C,
C must run before D
```

is a sign that the design is becoming difficult to understand.

Business rules should live in explicit abstractions/services rather than turning advice ordering into a hidden workflow engine.

The Pointcut chapter intentionally contains two advice methods using `this(...)` and `target(...)` that can both match without using their observed ordering as a learning contract. This is an example of the same principle.

</details>

- [Back to top](#back-to-top)

---

## <a id="ordering-conclusion">4. Conclusion</a>

<details>
<summary>Click for details</summary>

When reading logs from multiple `@Around` Aspects, picture a nested stack rather than a flat callback list.

</details>

- [Back to top](#back-to-top)
