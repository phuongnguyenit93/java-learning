<a id="back-to-top"></a>

# Advice Semantics and Lifecycle

## Menu
- [1. Semantics of each advice type](#advice-semantics)
- [2. Success path](#advice-success-demo)
- [3. Exception path](#advice-failure-demo)
- [4. Advice parameter binding](#advice-parameter-binding)
- [5. Conclusion](#advice-conclusion)

This section observes when `@Before`, `@After`, `@AfterReturning`, and `@AfterThrowing` execute.

## <a id="advice-semantics">1. Semantics of each advice type</a>

<details>
<summary>Click for details</summary>

Advice types describe **when** behavior runs and how much control it receives.

```text
@Before
→ runs before the selected method execution
→ cannot call proceed() to choose whether the target runs
→ if the advice itself throws, the chain stops

@AfterReturning
→ runs only after normal return
→ can bind and inspect the return value
→ does not replace the caller-visible return reference the way @Around can

@AfterThrowing
→ runs when the selected method execution exits by throwing a matching exception
→ can bind that exception
→ is observational advice, not a general recovery/control-flow mechanism

@After
→ after-finally semantics
→ runs for both normal and exceptional completion

@Around
→ surrounds the invocation
→ controls whether and how the chain proceeds
```

The practical rule is to choose the **least powerful advice type that satisfies the policy**. A before-only check is easier to reason about as `@Before` than as `@Around` with a carefully placed `proceed()`.

The distinction between `@After` and `@AfterReturning` matters:

```text
normal return
→ @AfterReturning participates
→ @After also participates

exceptional exit
→ @AfterThrowing participates if its exception binding matches
→ @After also participates
```

Within one `@Aspect`, different advice types have framework precedence semantics. Spring's AspectJ-style ordering assigns advice-type precedence, while the after-finally semantics make `@After` effectively run after matching `@AfterReturning` or `@AfterThrowing` on the way out.

If two advice methods of the **same type** in the same aspect match the same join point, their source-code declaration order is not a supported ordering contract. Split concerns into separately ordered aspects or collapse the logic when deterministic order matters.

### References

- Spring Framework Reference — Declaring Advice
- Spring Framework Reference — Advice Ordering

</details>

- [Back to top](#back-to-top)

---

## <a id="advice-success-demo">2. Success path</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AdviceLifecycleController#success()
```

Endpoint:

```text
GET /aop/advice/success
```

Target:

```text
AdviceLifecycleService#success()
```

When the target returns normally, the experiment records:

```text
@Before
target:success
@AfterReturning
@After
```

The sequence shows two separate facts. `@AfterReturning` participates because the join point completed normally, and `@After` participates because after-finally advice runs regardless of outcome.

The `@AfterReturning` method also binds the return value. Binding is useful when the policy needs to observe the result, for example recording metrics or auditing a successful operation. If the policy must replace what the caller receives, `@Around` is the appropriate control surface instead.

</details>

- [Back to top](#back-to-top)

---

## <a id="advice-failure-demo">3. Exception path</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AdviceLifecycleController#failure()
```

Endpoint:

```text
GET /aop/advice/failure
```

The target intentionally throws:

```text
IllegalStateException("intentional-advice-demo-error")
```

The controller catches that exception outside the advised service call so the experiment can still return observable evidence.

Important events:

```text
@Before
target:failure
@AfterThrowing
@After
```

`@AfterReturning` does not run because the target did not return normally. `@AfterThrowing` receives the exception leaving the selected method execution, and `@After` still runs with finally-like semantics.

The exception type declared by a `throwing`-bound advice parameter also constrains matching. A method that binds `IllegalStateException` does not receive every possible `Throwable`.

This is observation, not automatic recovery. `@AfterThrowing` does not turn the failure into success and does not provide a `proceed()` hook. Recovery or exception transformation requires an explicit control-flow design, typically `@Around` when AOP is truly the right place for it.

</details>

- [Back to top](#back-to-top)

---

## <a id="advice-parameter-binding">4. Advice parameter binding</a>

<details>
<summary>Click for details</summary>

Advice can receive strongly typed context instead of manually decoding a generic argument array.

Common sources are:

```text
JoinPoint
→ signature, arguments, proxy (getThis), target (getTarget)

args(value)
→ binds a runtime method argument

@annotation(annotation)
→ binds the annotation instance

returning = "result"
→ binds a successful return value

throwing = "throwable"
→ binds an exception leaving the selected method execution
```

Examples from this module:

```text
PointcutMatchingAspect#matchArgs(String value)
TrackingAspect#track(..., TrackExecution trackExecution)
AdviceLifecycleAspect#afterReturning(..., Object result)
AdviceLifecycleAspect#afterThrowing(..., Throwable throwable)
```

Binding does more than provide a value. The declared parameter type can narrow applicability. For example, a throwing parameter typed as a specific exception class restricts that advice to compatible failures.

Use `JoinPoint` when the concern needs general invocation metadata; use typed binding when the policy needs a specific argument, annotation, result, or exception. Typed signatures make the contract easier to review and reduce manual casts.

</details>

- [Back to top](#back-to-top)

---

## <a id="advice-conclusion">5. Conclusion</a>

<details>
<summary>Click for details</summary>

Choose advice by lifecycle semantics:

```text
before only
→ @Before

successful completion
→ @AfterReturning

exceptional completion
→ @AfterThrowing

cleanup regardless of outcome
→ @After

control the invocation itself
→ @Around
```

The next chapter focuses on `@Around` because it can alter arguments, return values, exceptions, and whether the invocation proceeds at all.

</details>

- [Back to top](#back-to-top)
