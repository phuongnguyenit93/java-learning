<a id="back-to-top"></a>

# Proxy Limitations and Self-Invocation

## Menu
- [1. Why is this.inner() different from an external call?](#self-invocation-mental-model)
- [2. Demo in this module](#self-invocation-demo)
- [3. Related limitations](#proxy-limitations)
- [4. Final methods on class-based proxies](#final-method-demo)
- [5. Conclusion](#self-invocation-conclusion)

This is one of the most important limitations of proxy-based Spring AOP.

## <a id="self-invocation-mental-model">1. Why is this.inner() different from an external call?</a>

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

## <a id="self-invocation-demo">2. Demo in this module</a>

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

## <a id="proxy-limitations">3. Related limitations</a>

<details>
<summary>Click for details</summary>

Proxy-based interception also depends on whether a method can be intercepted by the current proxy strategy.

Cases such as private methods, final methods, or internal calls must be evaluated according to the concrete proxy mechanism. Do not assume that "adding an annotation is enough".

Spring features such as `@Transactional`, `@Async`, and `@Cacheable` often share the same mental model because they can rely on proxies/interceptors.

### Avoiding self-invocation

The preferred solution is often to refactor the boundary into another bean:

```text
Bean A
→ calls Bean B proxy
→ advice has a chance to run
```

Spring also offers techniques such as self-injection or `AopContext.currentProxy()`, but they make application code more explicitly dependent on AOP infrastructure; `AopContext.currentProxy()` also requires exposing the proxy. In this learning module, these are treated as escape hatches rather than the default design.

</details>

- [Back to top](#back-to-top)

---

## <a id="final-method-demo">4. Final methods on class-based proxies</a>

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

## <a id="self-invocation-conclusion">5. Conclusion</a>

<details>
<summary>Click for details</summary>

When AOP does not run, the first question should be:

```text
did this invocation actually pass through the proxy?
```

It is usually better to refactor the boundary between beans than to work around the proxy model with hidden self-references.

</details>

- [Back to top](#back-to-top)
