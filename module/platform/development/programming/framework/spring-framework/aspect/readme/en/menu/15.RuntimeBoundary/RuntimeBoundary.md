<a id="back-to-top"></a>

# Spring AOP Runtime Boundary and AspectJ

## Menu
- [1. @AspectJ syntax, Spring AOP runtime](#spring-aop-runtime)
- [2. Boundary with full AspectJ](#spring-aop-vs-aspectj)
- [3. Demo in this module](#runtime-boundary-demo)
- [4. Connections to other Spring features](#framework-connections)
- [5. End-to-end synthesis: concern → proxy creation → advisor chain → target](#aop-end-to-end-synthesis)
- [6. Conclusion](#runtime-boundary-conclusion)

The final chapter establishes the correct boundary between **@AspectJ declaration style**, the **Spring AOP runtime**, and **full AspectJ weaving**.

## <a id="spring-aop-runtime">1. @AspectJ syntax, Spring AOP runtime</a>

<details>
<summary>Click for details</summary>

The module uses the `@AspectJ` declaration style:

```text
@Aspect
@Pointcut
@Before
@Around
```

but its runtime model is Spring AOP. The module configures Spring's proxy-based AOP infrastructure and does not configure the AspectJ compiler or a load-time-weaving agent.

The mental model is:

```text
@AspectJ-style declaration
        ↓
Spring turns eligible advice into Advisor metadata
        ↓
Spring AOP auto-proxy infrastructure
        ↓
runtime proxy
        ↓
method-execution interception
```

AspectJ annotation/runtime types appear on the classpath because Spring's `@AspectJ` support needs them. Their presence alone does not mean application classes are being woven.

A `ProxyFactory` proxy created directly in code is still the same Spring AOP runtime model; it simply skips container auto-proxy discovery.
</details>

- [Back to top](#back-to-top)

---

## <a id="spring-aop-vs-aspectj">2. Boundary with full AspectJ</a>

<details>
<summary>Click for details</summary>

Spring AOP is a runtime proxy system. In normal container usage it advises method executions reached through Spring-created proxies. Full AspectJ weaving changes the execution model.

With AspectJ compile-time weaving (CTW) or load-time weaving (LTW), the woven class itself contains the interception machinery. Advice therefore does not depend on routing the call through a Spring AOP proxy. AspectJ also supports a broader join-point model, including method-call, constructor, and field-access join points that Spring AOP's method-execution model does not provide.

This difference explains the self-invocation boundary:

```text
Spring AOP
this.inner()
→ stays on the target
→ no second proxy crossing

AspectJ weaving
this.inner()
→ code is woven
→ no proxy crossing is required
```

Chapter 7 uses:

```text
ProceedingJoinPoint#proceed(Object[])
```

with Spring AOP semantics: the supplied array represents arguments for the underlying method invocation. When the same source Aspect is compiled by the AspectJ compiler, the argument semantics of `proceed(...)` follow AspectJ's bound advice parameters and are not identical. Shared `@AspectJ` syntax does not make the two runtimes equivalent.

This module stays with Spring AOP because that is its learning target. Requirements that depend on self-invocation interception, constructors, field access, or other join points outside a method-execution proxy boundary are signals to evaluate AspectJ weaving or another mechanism.

### References

- Spring Framework Reference — [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- Spring Framework Reference — [Using AspectJ with Spring Applications](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html)
</details>

- [Back to top](#back-to-top)

---

## <a id="runtime-boundary-demo">3. Demo in this module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
RuntimeBoundaryController#inspectRuntimeBoundary()
```

Endpoint:

```text
GET /aop/advanced/runtime-boundary/inspect
```

Target:

```text
RuntimeBoundaryService#execute()
```

Aspect:

```text
RuntimeBoundaryAspect#around(...)
```

The response demonstrates these positive facts:

```text
The Aspect exists as a Spring bean
The injected target is an AOP proxy
The real target class can still be resolved behind the proxy
```

Events:

```text
runtime-aspect:join-point-kind=method-execution
runtime-aspect:before
target:runtime-boundary
runtime-aspect:after
```

`method-execution` comes directly from `JoinPoint#getKind()`, not from a hard-coded descriptive string in the controller.

The endpoint does not try to "prove with reflection" that every possible AspectJ weaving mode in the world is absent. That boundary is established by the module's build/runtime configuration itself.

</details>

- [Back to top](#back-to-top)

---

## <a id="framework-connections">4. Connections to other Spring features</a>

<details>
<summary>Click for details</summary>

After understanding proxies + Advisors + interceptor chains, the same mental model can help explain many declarative features in the Spring ecosystem, for example:

```text
transaction management
caching
method security
async method execution
```

The concrete implementation of each feature can differ, but questions about proxy boundaries, method interception, and self-invocation remain useful. The business semantics of transactions, caching, async execution, and security remain owned by their respective specialized modules.

Another boundary is important for async/reactive APIs:

```text
@Around measures method invocation time
≠
the time when a CompletableFuture / Mono / Flux finishes the asynchronous work
```

If a method returns an async/reactive container very quickly, the advice can complete before the real workload finishes. Measuring end-to-end completion requires instrumentation appropriate to that async/reactive abstraction, not only timing around `proceed()`.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-end-to-end-synthesis">5. End-to-end synthesis: concern → proxy creation → advisor chain → target</a>

<details>
<summary>Click for details</summary>

A Spring AOP feature can be reasoned about as one pipeline:

```text
cross-cutting concern
        ↓
Aspect / Advice declaration
        ↓
Pointcut determines applicability
        ↓
Pointcut + Advice form a PointcutAdvisor
        ↓
auto-proxy creator evaluates beans
        ↓
caller receives proxy
        ↓
matching invocation enters interceptor chain
        ↓
TargetSource supplies target
        ↓
target method executes
        ↓
return value / exception flows back through the chain
```

`Advisor` is the broader contract. A `PointcutAdvisor` is specifically the Advisor form that combines a pointcut with advice; introductions use a specialized Advisor model instead.

Every major failure mode in this module is a break or constraint in that pipeline:

```text
no proxy
→ no automatic proxy interception

call bypasses proxy
→ self invocation or direct target reference

method cannot be intercepted
→ final/private/non-visible class-based boundary
  or operation unavailable through a JDK proxy interface

pointcut does not match
→ proxy exists, but this PointcutAdvisor is not applicable

around advice does not proceed
→ chain intentionally stops before target
```

This also explains why transaction management, caching, async execution, and method security can consume AOP infrastructure without owning AOP semantics. Their domain rules differ; the shared part is the proxy/advisor/interceptor delivery mechanism.

When requirements move outside this pipeline, especially beyond method-execution proxy boundaries, the design has crossed into a different mechanism such as AspectJ weaving.
</details>

- [Back to top](#back-to-top)

---

## <a id="runtime-boundary-conclusion">6. Conclusion</a>

<details>
<summary>Click for details</summary>

The module ends with one runtime model:

```text
declaration / policy
        ↓
Pointcut + Advice
        ↓
PointcutAdvisor
        ↓
auto-proxy creation or ProxyFactory
        ↓
Proxy
        ↓
Interceptor chain
        ↓
TargetSource
        ↓
Target
```

`Advisor` remains the broader contract above specialized forms such as `PointcutAdvisor` and `IntroductionAdvisor`.

`@Aspect`, `@Around`, ordering, self invocation, proxy type, and debugging behavior are consequences of this architecture. AspectJ weaving shares some declaration syntax but changes the runtime boundary, so choose it only when the required join points exceed Spring AOP's proxy-based method-execution model.
</details>

- [Back to top](#back-to-top)
