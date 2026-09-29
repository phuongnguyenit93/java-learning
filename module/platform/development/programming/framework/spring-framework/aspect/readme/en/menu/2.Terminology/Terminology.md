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

In this module's experiment:

```text
TerminologyAspect
→ Aspect

explainTerms(...)
→ Advice

execution(...TerminologyService.execute(..))
→ Pointcut expression

TerminologyService.execute()
→ selected Join Point (method execution)

TerminologyService instance
→ Target Object

object injected into the Controller
→ AOP Proxy
```

In **Spring AOP**, the join point model is **method execution**. A pointcut selects a subset of those method executions, while the proxy boundary determines whether the invocation actually enters the AOP chain.

`weaving` is the general AOP term for linking an Aspect to a target/advised object. Spring AOP does this at runtime with proxies; full AspectJ also supports compile-time or load-time weaving and a broader join point model.

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

The response `facts` shows:

- whether the injected object is an AOP proxy;
- the runtime class of the injected object;
- the real target class.

The response `events` also records:

```text
aspect=TerminologyAspect
advice=@Before
join-point=...
proxy-class=...
target-class=TerminologyService
target:TerminologyService.execute
```

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-aop-vs-aspectj-style">3. @AspectJ style does not mean AspectJ weaving</a>

<details>
<summary>Click for details</summary>

The module uses annotations such as:

```text
@Aspect
@Before
@Around
```

These annotations belong to the **@AspectJ declaration style**, but the runtime used by this module is still **proxy-based Spring AOP**.

Mental model:

```text
AspectJ annotation syntax
        ↓
Spring reads metadata + pointcut expression
        ↓
Spring creates an AOP proxy
        ↓
method invocation passes through the proxy
```

The module does not configure the AspectJ compiler or a load-time weaving agent.

So keep these three concepts separate:

```text
weaving
→ general AOP concept

Spring AOP runtime weaving
→ proxy-based

AspectJ compile-time / load-time weaving
→ bytecode weaving + broader join point model
```

Another important distinction:

```text
@Aspect
→ declares that a class has Aspect semantics

@Component or @Bean
→ puts the Aspect instance into the Spring ApplicationContext
```

`@Aspect` itself is not a component-scanning annotation. The Aspects in this module use both `@Aspect` and `@Component` because Spring needs the Aspect to exist as a bean before the auto-proxy infrastructure can use it.

Section `15.RuntimeBoundary` revisits this distinction at the infrastructure level.

---

</details>

- [Back to top](#back-to-top)

---

## <a id="terminology-conclusion">4. Conclusion</a>

<details>
<summary>Click for details</summary>

One way to read the call chain is:

```text
Controller
→ AOP Proxy
→ Pointcut selects a Join Point
→ Advice executes
→ Target Object
```

An Aspect groups cross-cutting behavior; Advice is the concrete behavior that runs at a join point selected by a pointcut.

</details>

- [Back to top](#back-to-top)
