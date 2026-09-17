<a id="back-to-top"></a>

# Advanced - Spring AOP Runtime Boundary and AspectJ

## Menu
- [1. @AspectJ syntax, Spring AOP runtime](#spring-aop-runtime)
- [2. Boundary with full AspectJ](#spring-aop-vs-aspectj)
- [3. Demo in this module](#runtime-boundary-demo)
- [4. Connections to other Spring features](#framework-connections)
- [5. Conclusion](#runtime-boundary-conclusion)

The final chapter establishes the correct boundary between **@AspectJ declaration style**, the **Spring AOP runtime**, and **full AspectJ weaving**.

## <a id="spring-aop-runtime">1. @AspectJ syntax, Spring AOP runtime</a>

<details>
<summary>Click for details</summary>

The module uses:

```text
@Aspect
@Pointcut
@Before
@Around
```

but the module build only declares the Spring Boot AOP starter and does not configure the AspectJ compiler or a load-time weaving agent.

The module's mental model is:

```text
@AspectJ-style declaration
        ↓
Spring ApplicationContext discovers the Aspect bean
        ↓
Spring AOP auto-proxy infrastructure
        ↓
proxy-based method interception
```

Do not see the `org.aspectj.lang...` package and conclude that the runtime is weaving bytecode with AspectJ.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-aop-vs-aspectj">2. Boundary with full AspectJ</a>

<details>
<summary>Click for details</summary>

Spring AOP focuses on method execution on Spring-managed objects through a proxy boundary.

Full AspectJ weaving has a broader model and can operate at join points that do not need to pass through a Spring proxy, depending on the weaving mode and configuration.

Another nuance concerns argument rewriting in around advice. Chapter 6 uses:

```text
ProceedingJoinPoint#proceed(Object[])
```

with Spring AOP semantics: the new array represents the arguments of the underlying method invocation. If the same source Aspect is compiled by the AspectJ compiler, the semantics of arguments supplied to `proceed(...)` are not identical; they correspond to parameters bound into the around advice signature. This is one reason why "the same @AspectJ syntax" should not be treated as evidence that two runtimes are completely equivalent.

This module intentionally does not switch to compile-time/load-time AspectJ because its main goal is understanding Spring AOP.

Therefore, debugging in this module should always begin with:

```text
1. is the object Spring-managed/proxied?
2. does the invocation pass through the proxy?
3. does the Advisor/pointcut match?
4. how does the interceptor/advice chain execute?
```

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

The exact implementation of each feature can differ, but questions about proxy boundaries, method interception, and self-invocation are often still useful.

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

## <a id="runtime-boundary-conclusion">5. Conclusion</a>

<details>
<summary>Click for details</summary>

Advanced Spring AOP ends with this mental model:

```text
Declarative annotation
        ↓
Pointcut / Advisor
        ↓
Auto Proxy Creator
        ↓
Proxy
        ↓
Interceptor chain
        ↓
Target
```

Once this chain is understood, `@Aspect`, `@Around`, `@Order`, and self-invocation stop being isolated rules and become consequences of the same architecture.

</details>

- [Back to top](#back-to-top)
