<a id="back-to-top"></a>

# Advice Lifecycle

## Menu
- [1. Semantics of each Advice type](#advice-semantics)
- [2. Success path](#advice-success-demo)
- [3. Exception path](#advice-failure-demo)
- [4. Advice parameter binding](#advice-parameter-binding)
- [5. Conclusion](#advice-conclusion)

This section observes when `@Before`, `@After`, `@AfterReturning`, and `@AfterThrowing` execute.

## <a id="advice-semantics">1. Semantics of each Advice type</a>

<details>
<summary>Click for details</summary>

```text
@Before
→ runs before the target invocation

@AfterReturning
→ runs only when the target returns normally

@AfterThrowing
→ runs only when the target exits through a matching exception

@After
→ has finally-like semantics and runs when the invocation finishes, whether by success or exception
```

Do not interpret `@After` as "runs only after success".

Each advice type also has a different level of control:

```text
@Before
→ runs before invocation; has no proceed() API for intentionally skipping the target
→ but if the advice itself throws, the chain stops

@AfterReturning
→ observes the return value on the success path
→ does not replace the return value like @Around can

@AfterThrowing
→ observes an exception leaving the invocation
→ is not a strong recovery/control-flow API like @Around

@Around
→ has proceed() and can control the invocation chain
```

Choose the **weakest advice type that still satisfies the requirement**. Using `@Around` for every concern grants more control than necessary and makes the flow harder to read.

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

When the target returns normally, the response contains events corresponding to:

```text
@Before
target:success
@AfterReturning
@After
```

The important observation is that `@AfterReturning` can read the return value.

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

The controller catches the exception so the experiment can still return an observable response.

Important events:

```text
@Before
target:failure
@AfterThrowing
@After
```

`@AfterReturning` does not run on the exception path.

Within one `@Aspect`, advice methods of **different types** follow framework precedence semantics. In this experiment, `@AfterReturning`/`@AfterThrowing` are observed before `@After` because `@After` has finally-like semantics.

In contrast, if an Aspect declares **multiple advice methods of the same type** that match the same join point, do not use the source-code order of those advice methods as a contract. If order really matters, separate the concerns into different Aspects and declare explicit precedence.

</details>

- [Back to top](#back-to-top)

---

## <a id="advice-parameter-binding">4. Advice parameter binding</a>

<details>
<summary>Click for details</summary>

The chapters in this module use several binding styles. They can be summarized with this mental model:

```text
JoinPoint
→ signature / this / target / arguments

args(value)
→ binds a runtime argument to the value parameter

@annotation(annotation)
→ binds the annotation instance to an advice parameter

returning = "result"
→ binds the return value of a successful invocation

throwing = "throwable"
→ binds the exception leaving the invocation
```

Examples from this module:

```text
PointcutMatchingAspect#matchArgs(String value)
TrackingAspect#track(..., TrackExecution trackExecution)
AdviceLifecycleAspect#afterReturning(..., Object result)
AdviceLifecycleAspect#afterThrowing(..., Throwable throwable)
```

The important point is that advice can receive exactly the data bound by its pointcut/advice declaration instead of every advice manually parsing a generic `Object[]`.

</details>

- [Back to top](#back-to-top)

---

## <a id="advice-conclusion">5. Conclusion</a>

<details>
<summary>Click for details</summary>

Choose an advice type based on lifecycle semantics, not because its name merely sounds appropriate.

If full control around the invocation or control-flow changes are required, move to `@Around` in the next chapter.

</details>

- [Back to top](#back-to-top)
