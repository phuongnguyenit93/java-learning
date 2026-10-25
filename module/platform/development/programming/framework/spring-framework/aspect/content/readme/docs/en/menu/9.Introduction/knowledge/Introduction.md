<a id="back-to-top"></a>

# Introductions and Interface Enrichment

## Menu
- [What is Introduction?](#introduction-mental-model)
- [@DeclareParents](#declare-parents)
- [Executable Evidence for Introductions and Interface Enrichment](#introduction-demo)
- [Introductions and Interface Enrichment Synthesis](#introduction-conclusion)

Most AOP in the module so far changes behavior **around existing methods**. Introduction shows that a proxy can also expose a **new interface** that the original target class does not implement.

## <a id="introduction-mental-model">What is Introduction?</a>

<details>
<summary>Click for details</summary>

Most advice changes behavior around methods that already exist. An introduction changes the **type surface of the proxy** by adding an interface that the original target class does not implement.

```text
original target
IntroductionTargetService
→ not a UsageTracked

advised reference
Spring AOP proxy
├── IntroductionTargetService behavior
└── UsageTracked contract
```

The target class and its bytecode stay unchanged. The additional contract exists on the proxy. Calls to methods from the target continue through the normal proxy/interceptor path, while calls to the introduced interface are handled by the introduction implementation.

That distinction affects object identity and casting. A raw `IntroductionTargetService` instance cannot be treated as `UsageTracked` merely because an Aspect declares an introduction. Code that needs the introduced contract must hold the advised proxy reference.

Introduction therefore enriches a proxied object's public capability without requiring the original class to know that interface.

</details>

- [Back to top](#back-to-top)

---

## <a id="declare-parents">@DeclareParents</a>

<details>
<summary>Click for details</summary>

This module declares:

```java
@DeclareParents(
    value = "...IntroductionTargetService",
    defaultImpl = DefaultUsageTracked.class
)
public static UsageTracked usageTracked;
```

The pieces have distinct roles:

```text
field type: UsageTracked
→ interface introduced to matching proxies

value
→ AspectJ type pattern selecting eligible target types

defaultImpl: DefaultUsageTracked
→ implementation used for the introduced contract
```

The module intentionally uses an exact target type rather than a broad type pattern. A narrow boundary keeps the new contract predictable and prevents unrelated beans from silently acquiring it.

Introduction is useful when a proxy deliberately needs a stable secondary contract, but it is more implicit than ordinary composition or implementing the interface directly. Use it sparingly and document the proxy-level contract clearly.

### References

- Spring Framework Reference — [Introductions](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/introductions.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="introduction-demo">Executable Evidence for Introductions and Interface Enrichment</a>

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

## <a id="introduction-conclusion">Introductions and Interface Enrichment Synthesis</a>

<details>
<summary>Click for details</summary>

Introduction shows that Spring AOP can change more than execution around an existing method: a proxy can also expose a new interface while the target class remains unchanged.

The benefit is separation from the target class. The trade-off is that the capability exists only on the advised reference, so callers can be surprised if they bypass the proxy or assume the concrete target type tells the whole story.

</details>

- [Back to top](#back-to-top)
