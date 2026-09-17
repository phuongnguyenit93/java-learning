<a id="back-to-top"></a>

# Pointcuts and Method Matching

## Menu
- [1. What is the pointcut matching against?](#pointcut-mental-model)
- [2. Demo in this module](#pointcut-demo)
- [3. Spring AOP does not support the full AspectJ join point model](#spring-aop-pointcut-boundary)
- [4. Conclusion](#pointcut-conclusion)

A pointcut decides **which join points are selected**.

## <a id="pointcut-mental-model">1. What is the pointcut matching against?</a>

<details>
<summary>Click for details</summary>

Some common designators:

```text
execution(...)
→ matches method execution/signature

within(...)
→ matches a declaring type/package boundary

args(...)
→ matches the runtime argument shape

@annotation(...)
→ matches methods carrying a specific annotation

this(...)
→ matches the proxy type

target(...)
→ matches the target type

bean(...)
→ Spring-specific designator that matches by bean name
```

Pointcuts can also be named with `@Pointcut` and composed with:

```text
&&
||
!
```

Naming small pointcuts makes complex expressions easier to read than putting the entire rule into one long annotation.

The goal is not to make expressions as broad as possible. A pointcut should be narrow enough for behavior to remain predictable.

</details>

- [Back to top](#back-to-top)

---

## <a id="pointcut-demo">2. Demo in this module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
PointcutController#comparePointcuts()
```

Endpoint:

```text
GET /aop/pointcut/compare
```

The controller calls these methods in sequence:

```text
PointcutService#byExecution()
PointcutService#byAnnotation()
PointcutService#byArgs("demo")
PointcutService#byRuntimeArgs("runtime-string")
PointcutService#byWithin()
PointcutService#byThisAndTarget()
PointcutService#byBean()
PointcutService#unmatched()
```

Aspect:

```text
PointcutMatchingAspect
```

The response `events` will show:

```text
pointcut:execution
target:byExecution

pointcut:@annotation
target:byAnnotation

pointcut:args:demo
target:byArgs:demo

pointcut:args-runtime-type=String
target:byRuntimeArgs:runtime-string:declaredType=Object

pointcut:within+named-composition
target:byWithin

(both pointcut:this-proxy-type and pointcut:target-type appear)
target:byThisAndTarget

pointcut:bean-name
target:byBean

target:unmatched
```

`unmatched()` intentionally matches no advice and acts as a comparison baseline.

`byRuntimeArgs(Object)` intentionally declares its parameter as `Object`, while the controller passes a `String`. Advice using:

```text
args(java.lang.String)
```

still matches because `args(...)` evaluates the **runtime argument type**. This is an important distinction from `execution(...)`: `execution(...)` mainly sees the declared method signature, while `args(...)` sees the argument shape/type at runtime.

`byWithin()` also demonstrates named pointcut composition:

```text
pointcutServiceType()
&&
byWithinMethod()
```

`this(...)` and `target(...)` can both match in the current experiment, but their semantics differ:

```text
this(...)
→ inspects the proxy object's type

target(...)
→ inspects the target object's type behind the proxy
```

Do not use the order between `pointcut:this-proxy-type` and `pointcut:target-type` as a learning contract. They come from two advice methods with the same precedence; the experiment only needs to prove that **both matching rules are true** for the current invocation.

The difference becomes clearer when studying JDK proxies and CGLIB in the Advanced section.

`bean(pointcutService)` is a Spring AOP-specific designator: it selects join points based on Spring bean identity/name rather than only Java type or method signature.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-aop-pointcut-boundary">3. Spring AOP does not support the full AspectJ join point model</a>

<details>
<summary>Click for details</summary>

Spring AOP uses the **AspectJ pointcut expression language**, but the runtime model is still proxy-based method interception.

Therefore, do not infer:

```text
Spring understands AspectJ pointcut syntax
→ Spring AOP supports every AspectJ join point
```

Designators associated with join points outside proxy method execution, such as:

```text
call(...)
get(...)
set(...)
initialization(...)
cflow(...)
cflowbelow(...)
```

are not part of the normal capability of proxy-based Spring AOP.

Correct mental model:

```text
AspectJ expression syntax
        ≠
full AspectJ weaving model
```

Chapter `15.RuntimeBoundary` connects this distinction to full AspectJ weaving.

</details>

- [Back to top](#back-to-top)

---

## <a id="pointcut-conclusion">4. Conclusion</a>

<details>
<summary>Click for details</summary>

When debugging AOP, do not only ask "did the Aspect run?". Split the problem into two questions:

```text
1. did the invocation pass through the proxy?
2. did the pointcut match that invocation?
```

These conditions are independent, and both must be true.

</details>

- [Back to top](#back-to-top)
