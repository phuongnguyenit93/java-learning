<a id="back-to-top"></a>

# Core AOP Model: Aspects and Execution Points

## Menu
- [Aspect: A Unit for Cross-Cutting Concerns](#aop-aspect)
- [Join Point: A Point in the Execution Model](#aop-join-point)
- [Pointcut: Selecting Join Points](#aop-pointcut)
- [Advice: Behavior Added at Selected Points](#aop-advice)
- [Target: The Original Component or Behavior](#aop-target)
- [Weaving: Combining Aspects with Targets](#aop-weaving)
- [Before, After, and Around Advice in an Execution Flow](#aop-advice-kinds)
- [Effective Execution, Results, and Failures with Advice](#aop-effective-execution)
- [A Worked Model of Concerns, Aspects, Join Points, Pointcuts, and Advice](#aop-selection-example)

## <a id="aop-aspect">Aspect: A Unit for Cross-Cutting Concerns</a>

<details>
<summary>Click for details</summary>

An **aspect** packages a cross-cutting concern **and the rule selecting its execution points**, not merely a set of helper functions. A `ServiceTiming` aspect, for example, defines measurement behavior and which service executions it covers. Changing the format of its timing records should not require editing the transfer algorithm.

Do not put all business approval decisions into one convenient aspect: different domain decisions still require clear ownership. In our example, the aspect owns measurement and its selection, while the target remains responsible for the transfer outcome.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-join-point">Join Point: A Point in the Execution Model</a>

<details>
<summary>Click for details</summary>

A **Join Point** is a meaningful point in an execution model where additional behavior may participate.

Depending on the implementation, a join point may represent method execution, construction, field access, or another execution event.

Treat an execution of `transferFunds` as **an event within the running program**, not merely a method declaration in source code. An implementation can offer that event as an eligible join point. AspectJ's model includes more kinds of join points, including certain field operations, whereas Spring AOP supports **method execution on managed Spring beans**.

A method existing in source does not imply every route calling it will be intercepted. The event must be observable by the selected weaving or proxy mechanism. This distinction will explain internal-call limitations later.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-pointcut">Pointcut: Selecting Join Points</a>

<details>
<summary>Click for details</summary>

A **Pointcut** is a rule that selects a set of join points.

In short:

```text
Join Point = a place where behavior could be added
Pointcut   = the rule that selects where it should be added
```

If join points are the eligible moments, a pointcut is **the predicate selecting a subset** of those moments. A service-layer selection might include `transferFunds` but exclude `formatMoney`. When an execution does not match, the associated advice is not applied.

Prefer rules representing a **stable responsibility boundary**, not an indiscriminate wildcard over an application package. Renaming or moving operations can change the match set. Tests should cover both intended and unintended matches.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-advice">Advice: Behavior Added at Selected Points</a>

<details>
<summary>Click for details</summary>

**Advice** is the additional behavior executed at a selected join point. Depending on the model, it may run before, after, or around the primary operation. In our example, timing advice records elapsed time while the target transfers the funds; separating those duties lets us test measurement and its selected execution points independently.

Advice is not necessarily harmless. It may throw, change a returned result or, with `around`, skip the target. The handling of errors, sensitive data, and the number of target executions therefore belongs to the design contract, not just logging style.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-target">Target: The Original Component or Behavior</a>

<details>
<summary>Click for details</summary>

The **target** is the original component or behavior receiving additional cross-cutting behavior, such as the object handling a funds transfer. It need not know the name of its timing aspect. With runtime proxies, the caller may hold an intermediary reference that delegates to the real target.

Business invariants must remain the target's responsibility even if all advice is removed. External audit recording does not substitute for correct balance checks or ledger consistency.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-weaving">Weaving: Combining Aspects with Targets</a>

<details>
<summary>Click for details</summary>

**Weaving** combines an aspect with its target to produce effective runtime behavior; it does not mandate a single compiler step. AspectJ can weave during compilation or class loading, while Spring AOP usually provides advice through runtime proxies. The timing and technique depend on the chosen implementation.

Ask which **actual execution paths** acquire the added behavior, rather than only where an aspect is declared. A perfectly valid aspect declaration may have no effect on a path outside its matching or interception boundary.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-advice-kinds">Before, After, and Around Advice in an Execution Flow</a>

<details>
<summary>Click for details</summary>

**Before** advice runs before the selected point; if it returns normally it does not itself elect to skip the target, although throwing can prevent continuation. **After returning** applies only to normal completion; **after throwing** applies to matching failures; **after/finally** handles exits on both paths. These are different contracts, not interchangeable ways of saying “after”.

**Around** surrounds the remainder of execution and commonly uses an operation like `proceed`. It can continue, skip, or alter a result or exception. Prefer a narrower advice type when sufficient: an after-returning action does not risk forgetting to call `proceed`. See [Spring AOP Concepts](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html).

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-effective-execution">Effective Execution, Results, and Failures with Advice</a>

<details>
<summary>Click for details</summary>

For a timing around advice, a successful flow may look like:

```text
caller → around: start
       → proceed → target transferFunds → result
       → around: record duration → caller receives result
```

If the target throws, a measurement **only following `proceed`** may be skipped; use cleanup equivalent to `finally` when failures must be timed too. If around advice **does not proceed**, the target is skipped and the advice may return an alternative or fail: that changes the observable semantics. Multiple pieces of advice can nest according to precedence, so there is no universal assumption that every “after” runs last.

In implementations that allow calling `proceed` more than once, around advice may also repeat the target execution. For `transferFunds`, that could mean transferring money twice. The **number of continuations** is therefore part of the behavioral contract, not an incidental implementation detail.

| Calls to `proceed` | What happens to the transfer | Risk to verify |
| --- | --- | --- |
| 0 | Target is not invoked; advice supplies a result or error | Caller must not mistake a skipped transfer for a completed one |
| 1 | Target executes once | Expected success, failure, and elapsed-time behavior |
| 2 | Target may execute twice | Two transfers or duplicate external effects, not just two log lines |

This is a capability of around advice in supporting frameworks, **not a recommendation to retry payments**. A retry needs an explicit business safety contract; measuring duration should normally continue exactly once and preserve the original result or failure.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-selection-example">A Worked Model of Concerns, Aspects, Join Points, Pointcuts, and Advice</a>

<details>
<summary>Click for details</summary>

Consider `transferFunds(100)`: the concern is timing, the aspect is `ServiceTiming`, the join point is an **observable execution** of transfer handling, the pointcut selects payment-service operations, and advice records duration around the execution. The target still owns the transfer result. A `renderPage` call elsewhere should fall outside the selected set.

```text
transferFunds(100):  matches → timing → target → timing result
renderPage():       no match → target only
```

Test **both the matched and unmatched operations**, plus failure paths. Seeing a single log message does not prove that the selection rule covers precisely the intended calls.

</details>

- [Back to top](#back-to-top)
