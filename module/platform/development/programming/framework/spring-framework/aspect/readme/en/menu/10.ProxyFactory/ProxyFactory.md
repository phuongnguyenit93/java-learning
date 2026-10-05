<a id="back-to-top"></a>

# ProxyFactory and MethodInterceptor

## Menu
- [1. From annotations down to proxy infrastructure](#proxy-factory-mental-model)
- [2. JDK Dynamic Proxy and CGLIB with the same target](#jdk-vs-cglib)
- [3. Demo in this module](#proxy-factory-demo)
- [4. Conclusion](#proxy-factory-conclusion)

This chapter opens the proxy layer instead of continuing to view Spring AOP only through `@Aspect`.

## <a id="proxy-factory-mental-model">1. From annotations down to proxy infrastructure</a>

<details>
<summary>Click for details</summary>

Earlier chapters used declarative Aspects and let the container create proxies. `ProxyFactory` exposes the same lower-level model programmatically:

```text
Target
  +
Advice / Advisor
  +
proxy configuration
  ↓
ProxyFactory
  ↓
AOP Proxy
```

A `MethodInterceptor` is an around-style AOP Alliance advice:

```java
public Object invoke(MethodInvocation invocation) throws Throwable {
    before();
    try {
        return invocation.proceed();
    } finally {
        after();
    }
}
```

`MethodInvocation.proceed()` continues the remaining interceptor chain and eventually invokes the target. An interceptor can also short-circuit, call `proceed()` more than once, transform a result, or change exception behavior. Those powers should be used only when the cross-cutting contract deliberately requires them.

`ProxyFactory#addAdvice(...)` adds behavior without a pointcut supplied by that call. `addAdvisor(...)` is the route when behavior is paired with applicability metadata.

Programmatic proxy construction is useful for infrastructure code and experiments; ordinary Spring application beans usually rely on container auto-proxying.

### References

- Spring Framework 6.1.14 API — [`ProxyFactory`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/aop/framework/ProxyFactory.html)
</details>

- [Back to top](#back-to-top)

---

## <a id="jdk-vs-cglib">2. JDK Dynamic Proxy and CGLIB with the same target</a>

<details>
<summary>Click for details</summary>

The experiment keeps the target and interceptor constant and changes only the proxy strategy.

For the JDK proxy:

```java
factory.setInterfaces(GreetingOperations.class);
factory.setProxyTargetClass(false);
```

the caller sees the configured interface surface. The proxy implements `GreetingOperations` but is not a subclass of `GreetingTarget`:

```text
proxyIsGreetingOperations = true
proxyIsGreetingTarget     = false
```

A concrete method that exists only on `GreetingTarget` is therefore not part of the JDK proxy interface contract.

For the class-based proxy:

```java
factory.setProxyTargetClass(true);
```

Spring creates a runtime subclass of `GreetingTarget`. The concrete type surface is therefore visible through the proxy, so the demo can call `targetOnlyCapability()`.

This does not make class-based proxies universally better. Subclassing brings its own limits: a final class cannot be subclassed; final and private methods cannot be overridden; and methods not visible to the generated subclass cannot be intercepted.

The distinction also explains `this(...)` versus `target(...)` pointcuts. `this(...)` observes the proxy object, while `target(...)` refers to the target object behind it. With a JDK proxy those types can differ significantly.
</details>

- [Back to top](#back-to-top)

---

## <a id="proxy-factory-demo">3. Demo in this module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
ProgrammaticProxyController#compareProxyFactoryStrategies()
```

Endpoint:

```text
GET /aop/advanced/proxy-factory/compare
```

Service:

```text
ProgrammaticProxyService#compareProxyStrategies()
```

Interceptor:

```text
TracingMethodInterceptor#invoke(...)
```

Both proxies produce events for `greet(...)`:

```text
interceptor-before:greet
target:greet:...
interceptor-after:greet
```

But `facts` shows that one object is a JDK dynamic proxy and the other is a CGLIB proxy.

The CGLIB branch also contains:

```text
interceptor-before:targetOnlyCapability
target:targetOnlyCapability
interceptor-after:targetOnlyCapability
```

while the JDK proxy does not expose that method through `GreetingOperations`.

</details>

- [Back to top](#back-to-top)

---

## <a id="proxy-factory-conclusion">4. Conclusion</a>

<details>
<summary>Click for details</summary>

`ProxyFactory` makes the Spring AOP runtime visible: proxy strategy, target, advice, and Advisors are explicit objects rather than annotations hidden behind container setup.

The next chapter adds the missing selection abstraction. `Advice` describes behavior; a pointcut-based Advisor pairs that behavior with the classes and methods where it applies.
</details>

- [Back to top](#back-to-top)
