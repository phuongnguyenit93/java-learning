<a id="back-to-top"></a>

# Proxy Failure Model and Self-Invocation

## Menu
- [Why is this.inner() different from an external call?](#self-invocation-mental-model)
- [Executable Evidence for Proxy Failure Model and Self-Invocation](#self-invocation-demo)
- [Related limitations](#proxy-limitations)
- [Responding to self-invocation: refactor, self injection, and AopContext](#self-invocation-remediation)
- [Final methods on class-based proxies](#final-method-demo)
- [Debugging checklist: proxy creation → proxy boundary → pointcut → advisor chain](#aop-debugging-checklist)
- [Proxy Failure Model and Self-Invocation Synthesis](#self-invocation-conclusion)

This is one of the most important limitations of proxy-based Spring AOP.

## <a id="self-invocation-mental-model">Why is this.inner() different from an external call?</a>

<details>
<summary>Click for details</summary>

Suppose the target contains:

```java
public void outer() {
    inner();
}

@TrackExecution("inner")
public void inner() {
}
```

External call:

```text
Controller
→ Proxy
→ inner()
```

Advice has a chance to run.

Self-invocation:

```text
Controller
→ Proxy
→ outer()
      ↓
   this.inner()
```

The call to `inner()` happens directly on the current target object and does not go back out through the proxy a second time.

Therefore **having an annotation does not guarantee that advice will run**.

</details>

- [Back to top](#back-to-top)

---

## <a id="self-invocation-demo">Executable Evidence for Proxy Failure Model and Self-Invocation</a>

<details>
<summary>Click for details</summary>

Controller:

```text
SelfInvocationController#compareSelfInvocation()
```

Endpoint:

```text
GET /aop/self-invocation/compare
```

Target methods:

```text
SelfInvocationService#outerCallsInner()
SelfInvocationService#innerTrackedMethod()
```

`innerTrackedMethod()` is annotated with:

```text
@TrackExecution("self-invocation-inner")
```

The response returns two event groups.

### Self invocation

```text
target:outer-before-inner
target:innerTrackedMethod
target:outer-after-inner
```

There is no `track-before` because `outerCallsInner()` calls `innerTrackedMethod()` directly on the same object.

### External proxy invocation

The controller calls the bean directly with `selfInvocationService.innerTrackedMethod()`:

```text
track-before:label=self-invocation-inner
target:innerTrackedMethod
track-success:method=innerTrackedMethod
track-finished:elapsed-nanos=...
```

This time the invocation begins outside the target and passes through the proxy.

</details>

- [Back to top](#back-to-top)

---

## <a id="proxy-limitations">Related limitations</a>

<details>
<summary>Click for details</summary>

Proxy-based interception depends on both call routing and what the selected proxy can expose or override.

For a JDK dynamic proxy, callers invoke operations through the proxy interfaces. For a class-based proxy, Spring can intercept overridable methods visible to the generated subclass, but it cannot override `final` or `private` methods. A method that is effectively invisible to that subclass, such as a package-private method inherited from a parent in another package, cannot be advised through that subclass boundary either.

These constraints are separate from pointcut matching:

```text
pointcut matches
+
invocation never crosses an interceptable proxy method
=
advice still cannot run
```

Spring features such as `@Transactional`, `@Async`, and `@Cacheable` may use the same proxy/interceptor delivery model, although their domain semantics belong to their own modules.

### References

- Spring Framework Reference — [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="self-invocation-remediation">Responding to self-invocation: refactor, self injection, and AopContext</a>

<details>
<summary>Click for details</summary>

There are three common responses, with different coupling costs.

**1. Refactor the boundary.** Move the advised operation to a collaborator and call that collaborator through its injected bean reference:

```text
Bean A
→ Bean B proxy
→ advice
→ Bean B target
```

This makes the interception boundary visible in the object design and is usually the easiest behavior to test and reason about.

**2. Inject a self reference.** A bean can call an injected reference to its own proxy instead of calling `this`. This can preserve proxy interception, but the class now depends on container wiring to call itself. Self-reference and circular-dependency behavior become part of the design, so use this technique deliberately.

**3. Use `AopContext.currentProxy()`.** This is the strongest coupling. The current proxy is available only when proxy exposure is enabled, for example with `@EnableAspectJAutoProxy(exposeProxy = true)` or `ProxyFactory#setExposeProxy(true)`, and only while execution is inside an invocation for which Spring has exposed that proxy.

The Spring reference documentation recommends avoiding self invocation when practical and treats `AopContext.currentProxy()` as a last-resort technique. Fixing the collaboration boundary is normally more robust than teaching business code to locate its proxy.

### References

- Spring Framework Reference — [Understanding AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- Spring Framework 6.1.x API — [`AopContext`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/aop/framework/AopContext.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="final-method-demo">Final methods on class-based proxies</a>

<details>
<summary>Click for details</summary>

Controller:

```text
SelfInvocationController#finalMethodLimitation()
```

Endpoint:

```text
GET /aop/self-invocation/final-method
```

Method:

```java
@TrackExecution("final-method")
public final String finalTrackedMethod() {
    return "final-method-result";
}
```

`SelfInvocationService` needs a class-based proxy because it does not expose a business interface for this proxy strategy.

A class-based proxy works by subclassing. A `final` method cannot be overridden, so the interceptor/advice cannot insert itself into that method.

The response still returns:

```text
final-method-result
```

but `events` does not contain:

```text
track-before:label=final-method
```

even though the annotation is still present on the method.

These facts are not hard-coded. The controller reads the method with reflection and checks:

```text
annotationPresentOnFinalMethod = true
methodIsFinal                  = true
```

The experiment therefore separates two independent facts: the annotation really exists, and the method really is `final`, yet advice still cannot intercept the invocation through a class-based proxy.

This limitation is **different from self-invocation**:

```text
self-invocation
→ the call does not return through the proxy

final method with class-based proxy
→ the proxy subclass cannot override the method
```

### Private methods

Private methods also cannot be overridden by a subclass proxy. In addition, callers outside the target cannot directly invoke a private method as a public business boundary.

For that reason, the module does not create an artificial endpoint using reflection just to force a private method into a demo. The mental model is that a private method is not an appropriate join point for proxy-based Spring AOP.

Two other class-based proxy caveats are worth remembering:

```text
final class
→ cannot be subclassed for proxying

package-private method inherited from a parent in another package
→ cannot be overridden/intercepted like a normally visible method from the proxy subclass
```

The `finalTrackedMethod()` demo returns a constant so the experiment stays focused on interception. Do not infer that invoking every final method on a class-based proxy is equivalent to a normal target-delegation-chain invocation; the core limitation is that the proxy subclass **cannot override a final method**.

---

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-debugging-checklist">Debugging checklist: proxy creation → proxy boundary → pointcut → advisor chain</a>

<details>
<summary>Click for details</summary>

When advice does not run, debug in the same order as the runtime architecture:

```text
1. proxy creation
2. invocation path
3. method interceptability
4. pointcut / Advisor matching
5. advisor ordering and advice behavior
```

**Proxy creation:** confirm that the reference used by the caller is an AOP proxy. `AopUtils.isAopProxy(...)`, the runtime class, or a non-opaque `Advised` view can provide evidence.

**Invocation path:** confirm that the caller actually invokes the proxy. A raw target reference, an object created directly with `new`, or `this.inner()` can bypass the expected proxy boundary.

**Interceptability:** for a JDK proxy, verify that the operation is exposed through the proxy interface used by the caller. For a class-based proxy, check `final`, `private`, and visibility constraints.

**Pointcut / Advisor matching:** inspect the actual method, target class, annotations, runtime arguments when relevant, and the Advisors applied to the proxy. Do not rewrite the pointcut until proxy creation and call routing are known to be correct.

**Ordering and advice behavior:** if several Advisors match, inspect their order and whether an around interceptor calls `proceed()`, calls it more than once, transforms the result, or changes exception behavior.

This sequence prevents a common debugging mistake: changing the selection rule when the invocation never reached the expected proxy in the first place.

</details>

- [Back to top](#back-to-top)

---

## <a id="self-invocation-conclusion">Proxy Failure Model and Self-Invocation Synthesis</a>

<details>
<summary>Click for details</summary>

When AOP does not run, first establish whether a proxy exists and whether the invocation crossed it. Then check method interceptability, pointcut matching, and the Advisor chain.

For self invocation specifically, refactoring the collaboration boundary is usually clearer than adding hidden proxy lookups. The proxy model becomes predictable once call routing is treated as part of the design rather than as an annotation side effect.

</details>

- [Back to top](#back-to-top)
