<a id="back-to-top"></a>

# Advanced - Introduction and Interface Enrichment

## Menu
- [1. What is Introduction?](#introduction-mental-model)
- [2. @DeclareParents](#declare-parents)
- [3. Demo in this module](#introduction-demo)
- [4. Conclusion](#introduction-conclusion)

Most AOP in the module so far changes behavior **around existing methods**. Introduction shows that a proxy can also expose a **new interface** that the original target class does not implement.

## <a id="introduction-mental-model">1. What is Introduction?</a>

<details>
<summary>Click for details</summary>

Original target:

```text
IntroductionTargetService
→ does not implement UsageTracked
```

After the introduction is applied:

```text
Spring AOP Proxy
├── behavior of IntroductionTargetService
└── UsageTracked
```

The caller sees a proxy with a broader capability than the original target class.

This does not modify the target class bytecode. The new capability exists on the **proxy type surface**.

</details>

- [Back to top](#back-to-top)

---

## <a id="declare-parents">2. @DeclareParents</a>

<details>
<summary>Click for details</summary>

The module uses:

```java
@DeclareParents(
    value = "...IntroductionTargetService",
    defaultImpl = DefaultUsageTracked.class
)
public static UsageTracked usageTracked;
```

`DefaultUsageTracked` provides the implementation for the introduced interface.

The experiment intentionally matches the **exact target type** instead of using a broad type pattern. In a learning module, a narrow boundary keeps behavior predictable and avoids pulling unrelated beans into type matching.

Introduction is an advanced capability and is less common than logging or timing. It is more useful for understanding the power of the proxy/advisor model than as a pattern that every application should use frequently.

</details>

- [Back to top](#back-to-top)

---

## <a id="introduction-demo">3. Demo in this module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
IntroductionController#observeIntroduction()
```

Endpoint:

```text
GET /aop/advanced/introduction/observe
```

Target:

```text
IntroductionTargetService#businessOperation()
```

The response proves:

```text
targetClassImplementsUsageTracked = false
proxyImplementsUsageTracked       = true
introducedInterface               = true
```

The controller casts the proxy to:

```text
UsageTracked
```

and increments the counter from `0` to `2` before invoking the business method.

</details>

- [Back to top](#back-to-top)

---

## <a id="introduction-conclusion">4. Conclusion</a>

<details>
<summary>Click for details</summary>

Proxy-based AOP can do more than intercept methods. Through introduction, it can also change the **interface contract visible to callers**.

However, the more implicit behavior/interfaces are added through proxies, the more clearly the architecture must be documented to avoid surprises.

</details>

- [Back to top](#back-to-top)
