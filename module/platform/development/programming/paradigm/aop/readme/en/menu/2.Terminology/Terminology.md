# Core AOP Terminology

## <a id="aop-aspect">1. Aspect</a>

An **Aspect** groups a cross-cutting concern together with the rule that determines where the concern applies.

## <a id="aop-join-point">2. Join Point</a>

A **Join Point** is a meaningful point in an execution model where additional behavior may participate.

Depending on the implementation, a join point may represent method execution, construction, field access, or another execution event.

## <a id="aop-pointcut">3. Pointcut</a>

A **Pointcut** is a rule that selects a set of join points.

In short:

```text
Join Point = a place where behavior could be added
Pointcut   = the rule that selects where it should be added
```

## <a id="aop-advice">4. Advice</a>

**Advice** is the behavior executed at a selected join point.

Conceptually, advice may run before, after, or around the primary behavior depending on the execution model.

## <a id="aop-target">5. Target</a>

The **Target** is the original behavior or component to which the cross-cutting behavior is applied.

## <a id="aop-weaving">6. Weaving</a>

**Weaving** is the process of combining an aspect with a target to produce effective behavior.

Weaving does not require the same mechanism in every implementation; it may happen at build time, load time, or runtime.
