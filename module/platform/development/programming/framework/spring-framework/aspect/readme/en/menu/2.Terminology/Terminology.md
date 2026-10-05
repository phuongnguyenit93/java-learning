<a id="back-to-top"></a>

# AOP Terminology

## Menu
- [1. Core terminology](#terminology-map)
- [2. Demo in this module](#terminology-demo)
- [3. @AspectJ style does not mean AspectJ weaving](#spring-aop-vs-aspectj-style)
- [4. Conclusion](#terminology-conclusion)

This section maps AOP terminology to a real method call instead of treating each definition as an isolated term to memorize.

## <a id="terminology-map">1. Core terminology</a>

<details>
<summary>Click for details</summary>

Use one invocation as the map:

```text
caller
  ↓
AOP proxy
  ↓
advisor / interceptor chain
  ↓
target object
  ↓
method execution
```

The main terms describe different parts of that path.

**Aspect** groups a cross-cutting concern. An aspect can contain pointcuts, advice, and state.

**Advice** is the behavior that runs at a selected join point. Spring AOP supports before, after-returning, after-throwing, after-finally, and around advice.

**Join point** means a point in program execution. Spring AOP deliberately narrows that concept: a join point is always a **method execution** that can be reached through its proxy-based interception model.

**Pointcut** is the predicate that selects join points. It answers where advice should apply; it does not itself perform the behavior.

**Target object** is the application object whose method eventually executes.

**AOP proxy** is the object the caller usually sees when Spring applies AOP. It receives the call, runs the applicable interceptor/advice chain, and then delegates toward the target.

**Advisor** is a lower-level Spring AOP concept that combines advice with the rule describing where it applies. Annotation-style aspects are translated into advisor/interceptor infrastructure behind the scenes; the dedicated Advisor chapter goes deeper later.

**Weaving** is the general AOP term for linking aspects with application types or objects to produce advised behavior. Spring AOP performs that link at runtime through proxies. Full AspectJ can weave at compile time or load time and supports a broader join-point model.

Mapped to this module:

```text
TerminologyAspect
→ Aspect

explainTerms(...)
→ Advice

execution(...TerminologyService.execute(..))
→ Pointcut expression

TerminologyService.execute()
→ selected method-execution Join Point

TerminologyService instance
→ Target Object

object injected into the Controller
→ AOP Proxy
```

The key relationship is:

```text
proxy boundary
+ pointcut match
→ advice can participate
```

A pointcut match without a call crossing the proxy is not enough.

### References

- Spring Framework Reference — AOP Concepts
- Spring Framework Reference — Spring AOP Capabilities and Goals

</details>

- [Back to top](#back-to-top)

---

## <a id="terminology-demo">2. Demo in this module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
TerminologyController#inspectTerminology()
```

Endpoint:

```text
GET /aop/terminology/inspect
```

Target:

```text
TerminologyService#execute()
```

Advice:

```text
TerminologyAspect#explainTerms(...)
```

The response `facts` shows whether the injected object is an AOP proxy, its runtime class, and the actual target class. The `events` sequence records the same model from the running application:

```text
aspect=TerminologyAspect
advice=@Before
join-point=...
proxy-class=...
target-class=TerminologyService
target:TerminologyService.execute
```

Read the output as evidence of roles, not as a promise about a generated proxy class name. Runtime class names are implementation details; the conceptual identities are proxy, target, pointcut, join point, and advice.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-aop-vs-aspectj-style">3. @AspectJ style does not mean AspectJ weaving</a>

<details>
<summary>Click for details</summary>

Spring can interpret AspectJ annotations such as:

```text
@Aspect
@Before
@Around
@Pointcut
```

This is the **@AspectJ declaration style**. The annotation vocabulary comes from AspectJ, but Spring can use it while the runtime remains pure proxy-based Spring AOP.

Mental model:

```text
@AspectJ metadata
        ↓
Spring parses the aspect + pointcut declarations
        ↓
Spring AOP builds advisors/interceptors
        ↓
Spring creates an AOP proxy for eligible objects
        ↓
method invocation crosses the proxy
```

No AspectJ compiler or load-time weaver is implied by `@Aspect`.

Also separate declaration from bean registration:

```text
@Aspect
→ marks the class as an aspect

@Component / @Bean / XML bean definition
→ makes an aspect instance available in the ApplicationContext
```

`@Aspect` is not itself a component-scanning stereotype. With @AspectJ auto-proxying enabled, Spring detects aspect **beans** and uses them to configure AOP proxies.

If the requirement needs constructor calls, field access, or other join points beyond proxy method execution, that is where full AspectJ weaving becomes a different runtime choice rather than a different spelling of the same Spring AOP mechanism.

### References

- Spring Framework Reference — @AspectJ support
- Spring Framework Reference — Declaring an Aspect
- Spring Framework Reference — Using AspectJ with Spring Applications

</details>

- [Back to top](#back-to-top)

---

## <a id="terminology-conclusion">4. Conclusion</a>

<details>
<summary>Click for details</summary>

Read a Spring AOP call from the outside inward:

```text
caller
→ proxy
→ applicable advisor/interceptor chain
→ advice around a selected method-execution join point
→ target object
```

These terms are related but not interchangeable. Keeping their roles separate prevents later confusion about pointcut matching, proxy limitations, and the difference between Spring AOP and AspectJ weaving.

</details>

- [Back to top](#back-to-top)
