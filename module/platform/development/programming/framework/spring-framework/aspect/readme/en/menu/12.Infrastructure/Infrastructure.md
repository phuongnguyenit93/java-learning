<a id="back-to-top"></a>

# Auto-Proxy Infrastructure, Advised, and TargetSource

## Menu
- [Where does auto-proxy creation happen?](#auto-proxy-mental-model)
- [Spring Framework enablement vs Spring Boot auto-configuration](#framework-boot-aop-enablement-boundary)
- [Advised reveals what a proxy contains](#advised-interface)
- [TargetSource and target lifecycle strategies](#target-source-strategy)
- [Executable Evidence for Auto-Proxy Infrastructure, Advised, and TargetSource](#infrastructure-demo)
- [Auto-Proxy Infrastructure, Advised, and TargetSource Synthesis](#infrastructure-conclusion)

In normal application code, we do not call `new ProxyFactory(...)` for every bean. Spring automatically discovers candidates and wraps beans in proxies.

## <a id="auto-proxy-mental-model">Where does auto-proxy creation happen?</a>

<details>
<summary>Click for details</summary>

Simplified mental model:

```text
Bean definition
    ↓
bean instance is created
    ↓
auto-proxy BeanPostProcessor infrastructure
    ↓
eligible Advisors are resolved
    ↓
if advice applies
→ expose an AOP proxy to downstream callers
```

`AbstractAutoProxyCreator` is the central Spring AOP auto-proxy family and participates as `BeanPostProcessor` infrastructure. With `@AspectJ` support, Spring turns eligible aspect methods into Advisor candidates, decides which Advisors apply to a bean, and wraps that bean when interception is required.

The diagram is deliberately simplified. The container also has lifecycle paths for early proxy references, but the application-level rule is stable: collaborators should use the processed bean reference supplied by the container, because that reference may already be the proxy.

Auto-proxying applies to Spring-managed bean lifecycle processing. An arbitrary object created directly with `new` is not automatically wrapped just because a matching Aspect exists. It must enter suitable Spring infrastructure or be explicitly proxied.

Do not hard-code a concrete auto-proxy creator class name into business code. It is framework infrastructure, not an application contract.

</details>

- [Back to top](#back-to-top)

---

## <a id="framework-boot-aop-enablement-boundary">Spring Framework enablement vs Spring Boot auto-configuration</a>

<details>
<summary>Click for details</summary>

Plain Spring Framework and Spring Boot reach similar proxy infrastructure through different configuration layers.

In Spring Framework Java configuration:

```java
@EnableAspectJAutoProxy
```

enables processing of `@AspectJ`-style aspects in the local `ApplicationContext`. In Spring Framework 6.1.x, the annotation defaults to:

```text
proxyTargetClass = false
exposeProxy      = false
```

Those are Framework defaults.

This module runs with Spring Boot and declares:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

Boot AOP auto-configuration enables the infrastructure when its conditions are satisfied and `spring.aop.auto` has not disabled it. Boot also has its own proxy choice default:

```text
spring.aop.auto
→ defaults to true in Spring Boot

spring.aop.proxy-target-class
→ defaults to true in Spring Boot
→ prefers class-based/CGLIB proxies
```

If `spring.aop.proxy-target-class=false`, Boot can use JDK dynamic proxies when the target exposes suitable interfaces.

The distinction matters: a Boot property default is not a Spring Framework AOP rule. Chapter 10 can explicitly create either proxy style with `ProxyFactory` regardless of Boot's auto-proxy default.

### References

- Spring Framework 6.1.x API — [`@EnableAspectJAutoProxy`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/annotation/EnableAspectJAutoProxy.html)
- Spring Boot 3.3 API — [`AopAutoConfiguration`](https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/autoconfigure/aop/AopAutoConfiguration.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="advised-interface">Advised reveals what a proxy contains</a>

<details>
<summary>Click for details</summary>

A standard non-opaque Spring AOP proxy can expose the infrastructure interface:

```text
Advised
```

Through it, infrastructure and diagnostic code can inspect configuration such as:

```text
Advisor[]
TargetSource
proxied interfaces
proxy configuration
```

This is conditional. `ProxyConfig#setOpaque(true)` prevents callers from casting the generated proxy to `Advised`. The normal default is non-opaque, which is why the module can inspect its advisor chain with an `instanceof Advised` check.

`Advised` is useful for diagnostics and extension code. Ordinary business logic should not depend on it for control flow: doing so couples domain behavior to the AOP proxy and to configuration that infrastructure may change.

### References

- Spring Framework 6.1.x API — [`Advised`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/aop/framework/Advised.html)
- Spring Framework 6.1.x API — [`ProxyConfig`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/aop/framework/ProxyConfig.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="target-source-strategy">TargetSource and target lifecycle strategies</a>

<details>
<summary>Click for details</summary>

The proxy and the target have different responsibilities. A `TargetSource` answers the low-level question immediately before an advised invocation reaches application code:

```text
this invocation is ready to reach a target
        ↓
which target instance should receive it?
```

`TargetSource#isStatic()` defines an important lifecycle contract.

When `isStatic() == true`:

```text
repeated getTarget()
→ must return the same target object

proxy infrastructure
→ may cache that target

releaseTarget(...)
→ does not need to be called
```

This is the ordinary stable-target model, represented by implementations such as `SingletonTargetSource`.

When `isStatic() == false`, the target may vary between invocations. The AOP infrastructure obtains the target for the invocation and releases it afterward through `releaseTarget(...)`. Spring provides advanced strategies for scenarios such as pooling, hot swapping, lazy creation, or prototype-backed targets.

This responsibility is separate from pointcut matching:

```text
Pointcut / Advisor
→ should this behavior apply?

TargetSource
→ which target object ultimately receives the invocation?
```

Most application code should never implement a custom `TargetSource`. It is an infrastructure extension point for the uncommon case where target identity or lifecycle genuinely needs to vary behind a stable proxy reference.

### References

- Spring Framework 6.1.14 API — [`TargetSource`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/aop/TargetSource.html)
- Spring Framework Reference — [Using `TargetSource` Implementations](https://docs.spring.io/spring-framework/reference/core/aop-api/targetsource.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="infrastructure-demo">Executable Evidence for Auto-Proxy Infrastructure, Advised, and TargetSource</a>

<details>
<summary>Click for details</summary>

Controller:

```text
InfrastructureController#inspectInfrastructure()
```

Endpoint:

```text
GET /aop/advanced/infrastructure/inspect
```

Target:

```text
InfrastructureTargetService#execute()
```

Aspect:

```text
InfrastructureAspect#observe(...)
```

The response shows:

```text
isAopProxy
isCglibProxy
isJdkDynamicProxy
runtimeClass
targetClass
advisors
autoProxyCreators
autoProxyCreatorPresent
springAopAutoExplicitlyConfigured
springAopAutoConfiguredValue
springAopProxyTargetClassExplicitlyConfigured
springAopProxyTargetClassConfiguredValue
```

Events:

```text
infrastructure-aspect:before
target:infrastructure
infrastructure-aspect:after
```

The important point is that the controller does not create the proxy manually. The proxy already exists when the bean is injected into the controller.

The endpoint intentionally separates two kinds of evidence:

```text
configuration evidence
→ was the property explicitly configured by the application?
→ if so, what was the configured value?

runtime evidence
→ does an AutoProxyCreator actually exist?
→ is the bean actually an AOP proxy?
→ is the real proxy CGLIB or JDK dynamic proxy?
```

This keeps the response from pretending that a Boot default is a property value explicitly declared by the application. Documented defaults and runtime observations are different sources of information.

</details>

- [Back to top](#back-to-top)

---

## <a id="infrastructure-conclusion">Auto-Proxy Infrastructure, Advised, and TargetSource Synthesis</a>

<details>
<summary>Click for details</summary>

Annotation-style Spring AOP looks declarative at the application layer because proxy creation, Advisor discovery, target selection, and interceptor-chain construction are infrastructure responsibilities.

In Spring Framework, `@EnableAspectJAutoProxy` is one explicit way to register this support. In this Boot-based module, the starter and Boot AOP auto-configuration install equivalent infrastructure with Boot-specific defaults. Keeping those layers distinct prevents application auto-configuration choices from being mistaken for Spring AOP semantics.

</details>

- [Back to top](#back-to-top)
