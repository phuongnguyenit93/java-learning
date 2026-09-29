<a id="back-to-top"></a>

# Advanced - ProxyFactory and MethodInterceptor

## Menu
- [1. From annotations down to proxy infrastructure](#proxy-factory-mental-model)
- [2. JDK Dynamic Proxy and CGLIB with the same target](#jdk-vs-cglib)
- [3. Demo in this module](#proxy-factory-demo)
- [4. Conclusion](#proxy-factory-conclusion)

This chapter opens the proxy layer instead of continuing to view Spring AOP only through `@Aspect`.

## <a id="proxy-factory-mental-model">1. From annotations down to proxy infrastructure</a>

<details>
<summary>Click for details</summary>

In previous chapters, the call chain looked like:

```text
Caller
→ Spring AOP Proxy
→ Advice
→ Target
```

`ProxyFactory` lets us construct that chain directly in code:

```text
Target
  +
MethodInterceptor
  ↓
ProxyFactory
  ↓
Proxy
```

`MethodInterceptor` is a lower-level around-style advice:

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

Its mental model is very close to `ProceedingJoinPoint`:

```text
MethodInvocation.proceed()
→ continue the interceptor chain
→ eventually reach the target
```

</details>

- [Back to top](#back-to-top)

---

## <a id="jdk-vs-cglib">2. JDK Dynamic Proxy and CGLIB with the same target</a>

<details>
<summary>Click for details</summary>

The experiment uses the same:

```text
GreetingTarget
TracingMethodInterceptor
```

but creates two proxies.

JDK proxy:

```java
factory.setInterfaces(GreetingOperations.class);
factory.setProxyTargetClass(false);
```

The caller interacts through the `GreetingOperations` interface.

A JDK proxy exposes the interface surface and does not become a subclass of `GreetingTarget`. Therefore the response contains:

```text
proxyIsGreetingOperations = true
proxyIsGreetingTarget     = false
targetOnlyCapabilityVisibleThroughProxyType = false
```

The `targetOnlyCapabilityVisibleThroughProxyType` fact is calculated by inspecting public methods on the **runtime proxy class**, not by hard-coding a boolean.

CGLIB proxy:

```java
factory.setProxyTargetClass(true);
```

The proxy is a subclass of `GreetingTarget`.

Therefore the concrete type surface remains visible:

```text
proxyIsGreetingOperations = true
proxyIsGreetingTarget     = true
targetOnlyCapabilityVisibleThroughProxyType = true
```

The experiment also calls:

```java
cglibProxy.targetOnlyCapability()
```

directly, proving that a method defined only on the concrete target class is still part of the class-based proxy's type surface.

The lesson is not that one proxy is universally "better". The strategy determines the type surface and some interception limitations.

This connects directly back to `this(...)` and `target(...)` in the Pointcut chapter. With the JDK proxy in this experiment:

```text
proxy object
→ implements GreetingOperations
→ is not GreetingTarget

target object behind the proxy
→ GreetingTarget
```

So `this(...)` observes the proxy's type surface, while `target(...)` observes the target type. This is why the two designators can produce different results when the proxy strategy changes.

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

`@Around` is not the only mechanism that can wrap a method invocation. At the Spring AOP API level, a proxy can be constructed directly from a target plus advice/interceptor.

The next chapter adds another important abstraction: **Advisor**.

</details>

- [Back to top](#back-to-top)
