<a id="back-to-top"></a>

# Spring AOP Proxy Mental Model

## Menu
- [The caller does not necessarily invoke the target directly](#proxy-mental-model)
- [Executable Evidence for Spring AOP Proxy Mental Model](#proxy-demo)
- [Container auto-proxying vs direct object creation](#managed-vs-new-demo)
- [JDK Dynamic Proxy and CGLIB](#proxy-strategies)
- [Spring AOP Proxy Mental Model Synthesis](#proxy-conclusion)

The proxy boundary is the foundation for understanding most Spring AOP behavior.

## <a id="proxy-mental-model">The caller does not necessarily invoke the target directly</a>

<details>
<summary>Click for details</summary>

When Spring applies AOP to an object, callers commonly receive a proxy reference:

```text
Caller
  ↓
Spring AOP Proxy
  ↓
Advisor / interceptor chain
  ↓
Target Object
```

The proxy is the interception boundary. It can inspect the current method invocation, run matching advice, and continue toward the target. This leads to the most useful runtime question in Spring AOP:

```text
Did this invocation cross the expected proxy?
```

That question comes before pointcut debugging. If code already holds a direct target reference, no pointcut can retroactively intercept that direct call.

Container auto-proxying is the common application path: Spring's infrastructure examines Spring-managed beans and, when advisors apply, exposes a proxy in place of the plain bean reference. This is a container lifecycle feature, not a rule that every Java object in the JVM is automatically proxied.

There is also a low-level boundary. `ProxyFactory` can explicitly wrap an object and create an AOP proxy programmatically. That matters when reasoning about examples created with `new`:

```text
new SomeService()
→ ordinary object, no AOP by itself

ProxyFactory(new SomeService()).getProxy()
→ explicitly created Spring AOP proxy
```

So the correct distinction is between **calls through an AOP proxy** and **direct calls to an unproxied target**, rather than between "Spring bean" and "object created with new" as an absolute rule.

### References

- Spring Framework Reference — Proxying Mechanisms
- Spring Framework Reference — Creating AOP Proxies Programmatically with the ProxyFactory

</details>

- [Back to top](#back-to-top)

---

## <a id="proxy-demo">Executable Evidence for Spring AOP Proxy Mental Model</a>

<details>
<summary>Click for details</summary>

Controller:

```text
ProxyMentalModelController#inspectProxy()
```

Endpoint:

```text
GET /aop/proxy/inspect
```

Target:

```text
ProxyMentalModelService#invokeTarget()
```

Aspect:

```text
ProxyMentalModelAspect#observeProxyBoundary(...)
```

The response `facts` includes:

```text
runtimeClass
targetClass
isAopProxy
isCglibProxy
isJdkDynamicProxy
```

The response `events` shows the invocation crossing advice before reaching the target:

```text
proxy-boundary:before-target
target:invokeTarget
proxy-boundary:after-target
```

Generated proxy class names are not a stable contract. Use Spring utilities such as `AopUtils.isAopProxy(...)`, `isJdkDynamicProxy(...)`, `isCglibProxy(...)`, and target-class inspection when the experiment needs semantic evidence.

</details>

- [Back to top](#back-to-top)

---

## <a id="managed-vs-new-demo">Container auto-proxying vs direct object creation</a>

<details>
<summary>Click for details</summary>

Controller:

```text
ProxyMentalModelController#compareManagedBeanAndPlainObject()
```

Endpoint:

```text
GET /aop/proxy/managed-vs-new
```

The experiment deliberately compares the container-managed path with a plain direct object:

```text
Spring-managed reference
→ AOP proxy
→ advice
→ target

new ProxyMentalModelService(...)
→ plain object
→ target directly
```

The managed branch records:

```text
proxy-boundary:before-target
target:invokeTarget
proxy-boundary:after-target
```

The plain branch records only:

```text
target:invokeTarget
```

and `facts` confirms the difference:

```text
managedIsAopProxy = true
plainIsAopProxy   = false
```

The lesson is precise: container auto-proxying does not automatically advise an arbitrary object merely because its class would match the same pointcut. The plain object could still be wrapped explicitly with `ProxyFactory`, but this experiment intentionally does not do that.

</details>

- [Back to top](#back-to-top)

---

## <a id="proxy-strategies">JDK Dynamic Proxy and CGLIB</a>

<details>
<summary>Click for details</summary>

Spring AOP has two main proxy strategies.

```text
JDK dynamic proxy
→ interface-based proxy
→ proxy type exposes the proxied interfaces

CGLIB proxy
→ generated subclass of the target class
→ proxy type is assignable to the target class
```

In core Spring Framework defaults, if the target implements at least one interface, Spring can use a JDK dynamic proxy; if no interface is available, Spring uses a CGLIB class proxy. Configuration such as `proxyTargetClass = true` can force class-based proxying. Spring Boot can choose different application defaults, so do not treat a Boot default as the Spring Framework AOP contract.

The strategy affects which calls can be represented through the proxy. JDK proxies intercept calls exposed through the proxy interfaces. CGLIB relies on subclassing/overriding, so Java rules create limits:

- a `final` class cannot be subclass-proxied;
- a `final` method cannot be overridden and therefore cannot be advised through CGLIB;
- a `private` method cannot be overridden and therefore cannot be advised;
- methods that are effectively invisible to the subclass cannot be advised through that subclass proxy.

Common application interactions should use clear public service boundaries. A requirement to intercept arbitrary internal calls, constructors, or field access is usually evidence that proxy-based Spring AOP is no longer the right runtime boundary.

The module's programmatic comparison of both strategies is in:

```text
readme/en/menu/10.ProxyFactory/ProxyFactory.md
ProgrammaticProxyController#compareProxyFactoryStrategies()
```

### References

- Spring Framework Reference — Proxying Mechanisms

</details>

- [Back to top](#back-to-top)

---

## <a id="proxy-conclusion">Spring AOP Proxy Mental Model Synthesis</a>

<details>
<summary>Click for details</summary>

For any "why did advice run or not run?" question, keep this order:

```text
1. Is there an AOP proxy?
2. Does the invocation cross that proxy?
3. Does an advisor / pointcut match the invocation?
4. What advice runs in the resulting chain?
```

Proxy strategy changes the type surface and some interception limits, but the boundary principle stays the same. The later self-invocation chapter shows the classic case where a proxied object exists while one internal call still bypasses the proxy.

</details>

- [Back to top](#back-to-top)
