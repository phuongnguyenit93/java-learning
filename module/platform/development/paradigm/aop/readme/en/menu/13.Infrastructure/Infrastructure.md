<a id="back-to-top"></a>

# Advanced - Auto Proxy Creator and Advised

## Menu
- [1. Where does auto-proxy creation happen?](#auto-proxy-mental-model)
- [2. What enables AOP in Spring Boot?](#spring-boot-aop-auto-config)
- [3. Advised reveals what a proxy contains](#advised-interface)
- [4. Demo in this module](#infrastructure-demo)
- [5. Conclusion](#infrastructure-conclusion)

In normal application code, we do not call `new ProxyFactory(...)` for every bean. Spring automatically discovers candidates and wraps beans in proxies.

## <a id="auto-proxy-mental-model">1. Where does auto-proxy creation happen?</a>

<details>
<summary>Click for details</summary>

Simplified mental model:

```text
Bean definition
    ↓
bean instance is created
    ↓
BeanPostProcessor infrastructure
    ↓
Spring finds matching Advisors
    ↓
if needed
→ return an AOP proxy instead of the raw bean reference
```

An important infrastructure family is:

```text
AbstractAutoProxyCreator
```

With `@AspectJ` support, the runtime typically has a concrete auto-proxy creator responsible for turning Aspect metadata into Advisor candidates and deciding which beans require proxies.

Do not hard-code a specific auto-proxy creator class name into business logic because it is framework infrastructure.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-boot-aop-auto-config">2. What enables AOP in Spring Boot?</a>

<details>
<summary>Click for details</summary>

The module only declares:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

and `AspectApplication` does not need to add:

```java
@EnableAspectJAutoProxy
```

In Spring Boot, AOP is auto-configured when the required dependency and conditions are present. Two important settings are:

```text
spring.aop.auto
→ defaults to true

spring.aop.proxy-target-class
→ defaults to true in Spring Boot
→ prefers class-based/CGLIB proxies
```

These are **documented Boot defaults**. They do not mean the application must explicitly contain both properties in `application.yml`.

If this is changed:

```properties
spring.aop.proxy-target-class=false
```

the auto-proxy infrastructure may use JDK dynamic proxies when the target exposes an appropriate interface.

This explains why many Spring-managed beans in the module currently report:

```text
isCglibProxy = true
```

even though chapter `11.ProxyFactory` can still create both JDK and CGLIB proxies programmatically.

</details>

- [Back to top](#back-to-top)

---

## <a id="advised-interface">3. Advised reveals what a proxy contains</a>

<details>
<summary>Click for details</summary>

A Spring AOP proxy commonly exposes the infrastructure interface:

```text
Advised
```

which can be used to inspect:

```text
Advisor[]
```

This is useful for debugging or learning the architecture, but normal application code should not depend deeply on `Advised` for business flow.

</details>

- [Back to top](#back-to-top)

---

## <a id="infrastructure-demo">4. Demo in this module</a>

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

## <a id="infrastructure-conclusion">5. Conclusion</a>

<details>
<summary>Click for details</summary>

Annotation-style Spring AOP looks highly declarative at the application layer because proxy creation, Advisor discovery, and interceptor-chain construction are performed automatically by framework infrastructure.

In Spring Boot, the starter plus auto-configuration forms the bridge from dependency/configuration to that auto-proxy infrastructure.

</details>

- [Back to top](#back-to-top)
