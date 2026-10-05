<a id="back-to-top"></a>

# Advisor API and Programmatic Pointcuts

## Menu
- [1. PointcutAdvisor = Pointcut + Advice; Advisor is the broader contract](#advisor-mental-model)
- [2. Pointcut = ClassFilter + MethodMatcher](#pointcut-internals)
- [3. Static and dynamic MethodMatcher](#static-dynamic-pointcut)
- [4. Demo in this module](#advisor-demo)
- [5. Conclusion](#advisor-conclusion)

This chapter answers the question: if `MethodInterceptor` describes **what to do**, which object describes **which class/method/invocation should receive that behavior**?

## <a id="advisor-mental-model">1. PointcutAdvisor = Pointcut + Advice; Advisor is the broader contract</a>

<details>
<summary>Click for details</summary>

`Advisor` is the broader Spring AOP contract. It carries an `Advice` object, but a generic Advisor is **not** necessarily "Pointcut + Advice".

The common pointcut-based form is `PointcutAdvisor`:

```text
Advisor
└── Advice
      └── what behavior belongs in the proxy chain?

PointcutAdvisor
├── Pointcut
│     └── where is that behavior applicable?
└── Advice
      └── what behavior runs after a match?
```

`DefaultPointcutAdvisor`, used by this module, is the standard general-purpose implementation of that pairing.

Other Advisor forms have different applicability models. For example, an `IntroductionAdvisor` describes an introduction and the interfaces it adds. This is why the correct relationship is:

```text
PointcutAdvisor = Pointcut + Advice
Advisor         = broader advice/configuration contract
```

### References

- Spring Framework Reference — [The Advisor API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html)
</details>

- [Back to top](#back-to-top)

---

## <a id="pointcut-internals">2. Pointcut = ClassFilter + MethodMatcher</a>

<details>
<summary>Click for details</summary>

At the lower Spring AOP API, a `Pointcut` is a structural object rather than merely an expression string:

```text
Pointcut
├── ClassFilter
│     └── is this target class a candidate?
└── MethodMatcher
      └── does this method match on that candidate?
```

`ClassFilter` can reject unrelated target types before method matching. `MethodMatcher` then decides whether an operation on a candidate type is eligible.

The static experiment uses `NameMatchMethodPointcut` and maps only `write`. The same proxy exposes both `read()` and `write()`, but only `write()` enters the Advisor's interceptor:

```text
read()
→ pointcut false
→ target only

write()
→ pointcut true
→ advice
→ target
```

Its `MethodMatcher#isRuntime()` returns `false`, so the match does not require invocation arguments.
</details>

- [Back to top](#back-to-top)

---

## <a id="static-dynamic-pointcut">3. Static and dynamic MethodMatcher</a>

<details>
<summary>Click for details</summary>

A static `MethodMatcher` can decide using the method and target class:

```text
matches(Method, targetClass)
```

When `isRuntime() == false`, Spring does not need an argument-sensitive match on every call.

A dynamic matcher has two stages:

```text
static phase
matches(Method, targetClass)
        ↓ true
runtime phase for each invocation
matches(Method, targetClass, args...)
```

The static phase still matters. Spring first establishes that the method can match at all. Only when that succeeds and `isRuntime()` is `true` does the argument-sensitive check participate for an invocation.

This module's `RuntimeArgumentPointcut` first selects `AdvisorTarget#writeWithMode` and then requires the first argument to equal `"audit"`. Consequently:

```text
writeWithMode("audit")
→ static true
→ runtime true
→ advice + target

writeWithMode("plain")
→ static true
→ runtime false
→ target without that advice
```

Dynamic matching is appropriate only when selection genuinely depends on runtime data. It adds per-invocation matching work and makes applicability data-dependent, so prefer static matching when type and method metadata are sufficient.

### References

- Spring Framework Reference — [Pointcut API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html)
</details>

- [Back to top](#back-to-top)

---

## <a id="advisor-demo">4. Demo in this module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AdvisorController#observeAdvisor()
```

Endpoint:

```text
GET /aop/advanced/advisor/observe
```

The response contains two sub-experiments.

### Static `NameMatchMethodPointcut`

Fact:

```text
staticNameMatchPointcut.methodMatcherIsRuntime = false
```

Events:

```text
target:read
advisor-before:write
target:write
advisor-after:write
```

`read()` is the baseline proving that a proxy can exist without the Advisor intercepting every method.

### Dynamic `RuntimeArgumentPointcut`

Facts are taken from the real `ClassFilter` and `MethodMatcher`:

```text
classFilterMatchesAdvisorTarget = true
staticMethodMatch              = true
methodMatcherIsRuntime         = true
runtimeMatchAudit              = true
runtimeMatchPlain              = false
```

Events:

```text
dynamic-advisor-before:writeWithMode:mode=audit
target:writeWithMode:mode=audit
dynamic-advisor-after:writeWithMode:mode=audit
target:writeWithMode:mode=plain
```

The `plain` invocation still reaches the target but has no `dynamic-advisor-*` events, proving that the runtime matcher rejected the invocation after the static method match succeeded.

</details>

- [Back to top](#back-to-top)

---

## <a id="advisor-conclusion">5. Conclusion</a>

<details>
<summary>Click for details</summary>

The low-level model is now precise:

```text
Advice
→ behavior

Pointcut
→ ClassFilter + MethodMatcher

PointcutAdvisor
→ Pointcut + Advice

Advisor
→ broader contract that always exposes Advice,
  with applicability modeled by the specific Advisor subtype
```

`@AspectJ` style is a convenient authoring layer, but Spring ultimately builds lower-level Advisor/interceptor structures so the proxy can determine which behavior belongs in each invocation chain.
</details>

- [Back to top](#back-to-top)
