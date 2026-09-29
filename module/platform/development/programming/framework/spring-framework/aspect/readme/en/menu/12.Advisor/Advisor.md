<a id="back-to-top"></a>

# Advanced - Advisor and Programmatic Pointcut

## Menu
- [1. Advisor = Pointcut + Advice](#advisor-mental-model)
- [2. Pointcut = ClassFilter + MethodMatcher](#pointcut-internals)
- [3. Static and dynamic MethodMatcher](#static-dynamic-pointcut)
- [4. Demo in this module](#advisor-demo)
- [5. Conclusion](#advisor-conclusion)

This chapter answers the question: if `MethodInterceptor` describes **what to do**, which object describes **which class/method/invocation should receive that behavior**?

## <a id="advisor-mental-model">1. Advisor = Pointcut + Advice</a>

<details>
<summary>Click for details</summary>

Mental model:

```text
Advisor
├── Pointcut
│     └── which invocation matches?
│
└── Advice / MethodInterceptor
      └── what should happen after a match?
```

In annotation style, these two parts often appear next to each other:

```java
@Around("execution(...)")
public Object around(...) { ... }
```

At the lower Spring AOP API level, they can be independent objects combined by an `Advisor`.

</details>

- [Back to top](#back-to-top)

---

## <a id="pointcut-internals">2. Pointcut = ClassFilter + MethodMatcher</a>

<details>
<summary>Click for details</summary>

At the lower Spring AOP API level, a `Pointcut` is not just an expression string. The core mental model is:

```text
Pointcut
├── ClassFilter
│     └── is this target class a candidate?
│
└── MethodMatcher
      └── does this method match?
```

`ClassFilter` eliminates unrelated types early. `MethodMatcher` decides which methods on candidate types are selected.

The first static experiment uses:

```text
NameMatchMethodPointcut
```

and maps only the method:

```text
write
```

The target has:

```text
read()
write()
```

Therefore only `write()` passes through advice even though both calls use the same proxy:

```text
read()
→ pointcut false
→ target

write()
→ pointcut true
→ advice
→ target
```

The response directly reads:

```text
NameMatchMethodPointcut#getMethodMatcher().isRuntime()
```

and gets `false`.

</details>

- [Back to top](#back-to-top)

---

## <a id="static-dynamic-pointcut">3. Static and dynamic MethodMatcher</a>

<details>
<summary>Click for details</summary>

Static matching only needs method metadata and the target class:

```text
matches(Method, targetClass)
```

If:

```text
MethodMatcher#isRuntime() == false
```

the framework does not need runtime arguments to decide whether each invocation matches.

Dynamic matching adds another step:

```text
static phase
matches(Method, targetClass)
        ↓ true
runtime phase
matches(Method, targetClass, args...)
```

The module defines:

```text
RuntimeArgumentPointcut
```

with this rule:

```text
ClassFilter
→ AdvisorTarget

static MethodMatcher
→ method name = writeWithMode

runtime MethodMatcher
→ first argument = "audit"
```

Two invocations call the same method:

```text
writeWithMode("audit")
→ static match true
→ runtime match true
→ advice runs

writeWithMode("plain")
→ static match true
→ runtime match false
→ target runs but advice does not
```

A dynamic pointcut is useful when selection truly depends on runtime data, but it requires runtime evaluation. If selection depends only on type/method metadata, static matching is simpler.

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

`Advisor` makes the underlying architecture explicit:

```text
Pointcut
  ├── ClassFilter
  └── MethodMatcher
+
Advice / MethodInterceptor
```

`MethodMatcher` can rely only on static metadata or require runtime arguments as well. `@AspectJ` style makes authoring easier, but Spring AOP infrastructure still has lower-level abstractions that represent the same selection rules.

</details>

- [Back to top](#back-to-top)
