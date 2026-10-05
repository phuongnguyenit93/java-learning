<a id="back-to-top"></a>

# Around Advice and ProceedingJoinPoint

## Menu
- [1. Mental model of @Around](#around-mental-model)
- [2. Timing demo](#around-timing-demo)
- [3. Return-value transformation demo](#around-transform-demo)
- [4. Demo without calling proceed()](#around-skip-demo)
- [5. proceed(Object[]) and argument replacement](#around-arguments-demo)
- [6. Exception propagation demo](#around-exception-demo)
- [7. Conclusion](#around-conclusion)

`@Around` is the Spring AOP advice type with the greatest control over an invocation.

## <a id="around-mental-model">1. Mental model of @Around</a>

<details>
<summary>Click for details</summary>

Around advice receives a `ProceedingJoinPoint` and can decide how the invocation chain continues.

```text
around: before
    ↓
proceed()
    ↓
next interceptor/advice
    ↓
eventually the target
    ↓
return or exception travels back outward
```

`ProceedingJoinPoint#proceed()` does not mean "call the target directly". It means **continue the current invocation chain**. If other advisors have lower precedence, they may run before the target is reached.

That control lets `@Around`:

- inspect the invocation and arguments;
- do work before and after normal completion;
- replace arguments with `proceed(Object[])`;
- observe or replace the return value;
- skip the remaining chain by not calling `proceed()`;
- call `proceed()` conditionally;
- observe, rethrow, wrap, or intentionally translate exceptions.

A normal statement after `proceed()` runs only when control returns normally. Cleanup that must happen for both success and failure belongs in a `finally` block around `proceed()`.

This power is useful for policies such as timing that need state before and after the call, but it also makes hidden control flow easy to create. Use a narrower advice type when no proceed-level control is required.

### References

- Spring Framework Reference — Declaring Advice, Around Advice

</details>

- [Back to top](#back-to-top)

---

## <a id="around-timing-demo">2. Timing demo</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AroundAdviceController#timing()
```

Endpoint:

```text
GET /aop/around/timing
```

Target:

```text
AroundAdviceService#timedOperation()
```

Events:

```text
around:before-proceed
target:timedOperation
around:after-proceed:elapsed-nanos=...
```

The advice captures time before `proceed()` and computes elapsed time after normal return. The sequence proves that the target runs while the around advice is still active on the call stack.

For production timing that must record failures as well, place the measurement finalization in `finally`:

```java
long start = System.nanoTime();
try {
    return joinPoint.proceed();
}
finally {
    record(System.nanoTime() - start);
}
```

The experiment keeps the success path simple so the nested control flow is easy to see; the exception section later shows the failure path explicitly.

</details>

- [Back to top](#back-to-top)

---

## <a id="around-transform-demo">3. Return-value transformation demo</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AroundAdviceController#transform()
```

Endpoint:

```text
GET /aop/around/transform
```

The target returns:

```text
original-result
```

but the caller receives:

```text
original-result|transformed-by-around
```

The around advice first obtains the result from `proceed()` and then returns a different value. This demonstrates an important contract boundary:

```text
target return value
≠ necessarily the value observed by the caller
```

Transformation can be intentional, but it should be part of a clearly documented cross-cutting contract. Unexpectedly changing business results in an aspect makes code harder to reason about and can violate assumptions in callers.

When the policy only needs to **observe** a successful result, `@AfterReturning` provides a smaller and safer control surface.

</details>

- [Back to top](#back-to-top)

---

## <a id="around-skip-demo">4. Demo without calling proceed()</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AroundAdviceController#skip()
```

Endpoint:

```text
GET /aop/around/skip
```

The aspect intentionally does **not** call `proceed()`.

The response contains:

```text
result = returned-without-calling-target
events = [around:skip-proceed]
```

It does not contain:

```text
target:skippedTarget
```

because the remaining invocation chain never runs.

This is why `@Around` is more than "before plus after in one method". It can short-circuit the invocation and supply its own result or exception. That capability is appropriate only when short-circuiting is a deliberate part of the policy; otherwise it can silently suppress business behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="around-arguments-demo">5. proceed(Object[]) and argument replacement</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AroundAdviceController#arguments()
```

Endpoint:

```text
GET /aop/around/arguments
```

The controller passes:

```text
"  Book  "
```

The aspect reads the current arguments, normalizes the value to:

```text
book
```

and continues with:

```java
joinPoint.proceed(new Object[]{normalized})
```

Events:

```text
around:argument-before="  Book  "
around:argument-after=book
target:normalizeArgument:item=book
```

The target receives the replacement argument.

For **Spring AOP**, the `Object[]` passed to `proceed(Object[])` is the replacement argument list for the underlying method invocation. Its size must match the underlying method's argument count, and the values must be compatible with those method parameters.

This differs from around advice compiled by the AspectJ compiler. In traditional AspectJ semantics, the arguments supplied to `proceed(...)` correspond to values bound into the around advice signature, so count/position rules are based on that binding model rather than Spring AOP's simpler underlying-method argument list.

For code intended to remain portable between Spring AOP and AspectJ weaving, bind method arguments explicitly in the pointcut/advice signature and keep the relationship between bound values and replacement values clear.

Argument rewriting changes what the target sees. Use it only when normalization or transformation is an explicit policy, not as an invisible patch for target code.

### References

- Spring Framework Reference — Declaring Advice, Proceeding with Arguments

</details>

- [Back to top](#back-to-top)

---

## <a id="around-exception-demo">6. Exception propagation demo</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AroundAdviceController#exceptionPropagation()
```

Endpoint:

```text
GET /aop/around/exception
```

The target intentionally throws:

```text
IllegalStateException("intentional-around-demo-error")
```

The aspect observes the exception and rethrows it:

```text
around:exception-before-proceed
target:throwsFailure
around:exception-observed=IllegalStateException
around:exception-finally
```

The `finally` event still runs because it belongs to cleanup around `proceed()`.

Catching an exception inside `@Around` does not require converting it to success. For logging, metrics, and auditing, preserving the original propagation is usually the least surprising behavior. Wrapping or translating exceptions is possible, but it changes the caller-visible contract and should be intentional.

### What about calling proceed() multiple times?

An around advice can invoke `proceed()` more than once. Each call continues the chain again and can therefore execute downstream advice and the target again. For state-changing operations, that can duplicate side effects.

Treat `proceed()` as control over the invocation chain, not as a harmless callback. Retry behavior belongs here only when repeated execution is an explicit, carefully designed policy with clear idempotency and failure semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="around-conclusion">7. Conclusion</a>

<details>
<summary>Click for details</summary>

The essential model is:

```text
proceed()
→ continue the current interceptor/advice chain

proceed(newArgs)
→ continue with replacement method arguments

no proceed()
→ short-circuit the remaining chain

catch / return different value
→ change the caller-visible outcome
```

Because `@Around` can control arguments, return values, exceptions, and target execution, it should express a deliberate policy rather than become the default advice type.

</details>

- [Back to top](#back-to-top)
