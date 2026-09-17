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

```text
around before
    ↓
proceed()
    ↓
target method
    ↓
return / exception
    ↓
around after
```

`ProceedingJoinPoint#proceed()` transfers execution to the next advice or to the target.

Therefore `@Around` can:

- measure execution time;
- inspect arguments;
- change arguments before continuing the invocation;
- observe the return value;
- change the return value;
- decide not to call the target;
- propagate or transform exceptions.

This power also makes behavior easier to make surprising when `@Around` is overused.

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

The important observation is that code after `proceed()` runs only after the target has completed or control has returned to the advice.

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

But the caller receives:

```text
original-result|transformed-by-around
```

This experiment proves that advice can change the contract observed by the caller. In production code, this capability should be used very carefully.

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

The Aspect intentionally **does not call `proceed()`**.

The response contains:

```text
result = returned-without-calling-target
events = [around:skip-proceed]
```

It does not contain:

```text
target:skippedTarget
```

because the target method was never executed.

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

The Aspect reads the argument from `joinPoint.getArgs()`, normalizes it to:

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

The target really receives the new argument, not the original one.

This is a powerful capability, but it can also make contracts difficult to predict. Use it only when the transformation is an explicit part of the cross-cutting contract.

### `proceed(Object[])` in Spring AOP and AspectJ

This experiment runs on **proxy-based Spring AOP**. In this runtime, the array passed to:

```java
joinPoint.proceed(new Object[]{normalized})
```

represents the new argument list for the underlying method invocation, and its size must match the method arguments.

Do not assume that `proceed(Object[])` has identical semantics when advice is compiled by the AspectJ compiler. With AspectJ-compiled around advice, arguments passed to `proceed(...)` correspond to parameters bound into the around advice signature, so the rules for argument count and position differ from Spring AOP.

If an Aspect must be portable between Spring AOP and AspectJ, bind arguments explicitly in the pointcut/advice signature instead of relying on assumptions specific to one runtime.

---

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

The Aspect observes the exception but **rethrows** it instead of swallowing it:

```text
around:exception-before-proceed
target:throwsFailure
around:exception-observed=IllegalStateException
around:exception-finally
```

The lesson is:

```text
catching an exception in @Around
≠
having to turn that exception into success
```

Preserving propagation is usually less surprising when the Aspect is only performing logging, metrics, or auditing.

---

### What about calling proceed() multiple times?

Technically, `@Around` can alter control flow deeply, but this module does not present multiple calls to `proceed()` as a recommended pattern. Doing so can execute the target multiple times and duplicate side effects.

Remember that `proceed()` means **continue the invocation chain**. It is not a harmless callback that can be invoked arbitrarily.

---

</details>

- [Back to top](#back-to-top)

---

## <a id="around-conclusion">7. Conclusion</a>

<details>
<summary>Click for details</summary>

`@Around` is not merely "before + after inside one method". It is advice that can control the invocation itself.

Safe mental model:

```text
proceed()
→ continue the call chain

no proceed()
→ the chain stops at the current advice
```

</details>

- [Back to top](#back-to-top)
