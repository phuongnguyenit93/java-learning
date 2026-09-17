<a id="back-to-top"></a>

# Spring AOP Proxy Mental Model

## Menu
- [1. The caller does not necessarily invoke the target directly](#proxy-mental-model)
- [2. Demo in this module](#proxy-demo)
- [3. Spring-managed bean vs object created with new](#managed-vs-new-demo)
- [4. JDK Dynamic Proxy and CGLIB](#proxy-strategies)
- [5. Conclusion](#proxy-conclusion)

The proxy boundary is the foundation for understanding most Spring AOP behavior.

## <a id="proxy-mental-model">1. The caller does not necessarily invoke the target directly</a>

<details>
<summary>Click for details</summary>

Mental model:

```text
Caller
  ↓
Spring AOP Proxy
  ↓
Advice chain
  ↓
Target Object
```

When a bean needs AOP, Spring usually gives consumers a proxy instead of a direct reference to the target object.

The proxy can be created using different strategies such as JDK dynamic proxy or class-based proxy. Do not hard-code the assumption that every bean always uses the same proxy type.

More important than the proxy type is this question:

```text
does the invocation pass through the proxy?
```

</details>

- [Back to top](#back-to-top)

---

## <a id="proxy-demo">2. Demo in this module</a>

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

The response `events` shows the call chain:

```text
proxy-boundary:before-target
target:invokeTarget
proxy-boundary:after-target
```

Do not try to guess the generated proxy class name. Use `AopUtils` to inspect semantics instead of depending on runtime class naming.

</details>

- [Back to top](#back-to-top)

---

## <a id="managed-vs-new-demo">3. Spring-managed bean vs object created with new</a>

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

The experiment calls the same method through two different paths:

```text
Spring-managed bean
→ AOP proxy
→ advice
→ target

new ProxyMentalModelService(...)
→ plain Java object
→ target directly
```

The managed branch has these events:

```text
proxy-boundary:before-target
target:invokeTarget
proxy-boundary:after-target
```

The `new` branch only has:

```text
target:invokeTarget
```

`facts` also shows:

```text
managedIsAopProxy = true
plainIsAopProxy   = false
```

**Conclusion:** a correct pointcut expression is still not enough. The object must be inside the Spring AOP proxy boundary before advice has a chance to participate in the invocation.

---

</details>

- [Back to top](#back-to-top)

---

## <a id="proxy-strategies">4. JDK Dynamic Proxy and CGLIB</a>

<details>
<summary>Click for details</summary>

Two proxy strategies must be distinguished:

```text
JDK Dynamic Proxy
→ interface-based proxy

Class-based / CGLIB Proxy
→ proxy is a subclass of the target class
```

At the fundamentals level, it is enough to know that the strategy affects the proxy's type surface and interception limitations.

The experiment that creates **both strategies programmatically** is located at:

```text
readme/en/menu/11.ProxyFactory/ProxyFactory.md
ProgrammaticProxyController#compareProxyFactoryStrategies()
```

---

</details>

- [Back to top](#back-to-top)

---

## <a id="proxy-conclusion">5. Conclusion</a>

<details>
<summary>Click for details</summary>

An annotation or pointcut alone is not enough for advice to run.

The correct mental model is:

```text
method call
→ passes through Spring proxy
→ pointcut matches
→ advice has a chance to run
```

The self-invocation chapter later demonstrates a case where a method has an annotation but the call does not go back through the proxy.

</details>

- [Back to top](#back-to-top)
