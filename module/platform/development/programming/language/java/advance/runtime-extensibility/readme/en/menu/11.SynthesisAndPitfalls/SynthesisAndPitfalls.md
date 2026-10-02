<a id="back-to-top"></a>

# Runtime Extensibility Synthesis and Design Pitfalls

## Menu
- [End-to-End Runtime Extensibility Flow](#extensibility-end-to-end-flow)
- [Choosing Static Wiring, ServiceLoader, or ModuleLayer](#mechanism-selection)
- [Common Runtime Extensibility Design Pitfalls](#common-design-pitfalls)
- [Failure-containment and Resource-cleanup Checklist](#failure-containment-checklist)
- [When to Hand Off to ClassLoader, JPMS, Framework Plugins, or Instrumentation](#boundary-handoffs)
- [The End-to-End Mental Model to Retain](#runtime-extensibility-synthesis)

## <a id="extensibility-end-to-end-flow">End-to-End Runtime Extensibility Flow</a>

<details>
<summary>Click for details</summary>

An end-to-end runtime-extensibility flow can be summarized as:

```text
1. Define a stable service contract
        ↓
2. Package providers independently
        ↓
3. Deploy/register providers
        ↓
4. Discover providers
        ↓
5. Validate compatibility/capability
        ↓
6. Select a provider
        ↓
7. Initialize/activate
        ↓
8. Execute behind a failure boundary
        ↓
9. Deactivate/clean up/replace
```

`ServiceLoader` directly addresses only part of step 4 and provider construction. SPI design, selection, lifecycle, isolation, and compatibility remain application architecture.

For dynamic JPMS plugins, deployment/discovery may add:

```text
ModuleFinder → Configuration/resolveAndBind → ModuleLayer → ServiceLoader
```

Keeping the whole flow visible prevents one Java API from being mistaken for a complete plugin system.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mechanism-selection">Choosing Static Wiring, ServiceLoader, or ModuleLayer</a>

<details>
<summary>Click for details</summary>

Choose a mechanism from requirements, not from how “advanced” it sounds.

| Need | Usually sufficient mechanism |
|---|---|
| Implementation known at build time | Static wiring / DI |
| Provider JARs discovered at startup | Class-path `ServiceLoader` |
| Named-module service providers | `uses` / `provides` + `ServiceLoader` |
| Separate runtime module graph | `Configuration` + `ModuleLayer` |
| Stronger dependency isolation in one JVM | Dedicated loaders/layers |
| Strong failure isolation | A process boundary may be more appropriate |

Do not introduce `ModuleLayer` when a fixed startup module path is enough.

Do not build a custom plugin framework when `ServiceLoader` plus a small validated registry satisfies the requirement.

Conversely, do not call `ServiceLoader.reload()` a hot-reload architecture when plugins have real state, resources, and class-loader lifecycle.

Complexity is worth paying for only when it solves a concrete requirement.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-design-pitfalls">Common Runtime Extensibility Design Pitfalls</a>

<details>
<summary>Click for details</summary>

Common design pitfalls include:

1. **Host directly requires/imports providers** → extension decoupling disappears.
2. **Discovery order becomes priority** → behavior depends on deployment topology.
3. **Provider constructors perform heavy work** → discovery creates uncontrolled side effects.
4. **No missing-provider policy** → failures occur late.
5. **Too many shared dependencies** → version conflicts.
6. **Each plugin loads its own contract copy** → type-identity failures.
7. **Calling `reload()` is treated as class unloading** → loader/resource leaks remain.
8. **Threads, ThreadLocals, or listeners are not cleaned up** → old plugin generations stay reachable.
9. **SPI changes lack compatibility tests** → breakage appears only during deployment.
10. **A ModuleLayer design still hard-codes provider modules into the host** → complexity without real extensibility.

A design review should surface these questions before the implementation becomes expensive to change.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="failure-containment-checklist">Failure-containment and Resource-cleanup Checklist</a>

<details>
<summary>Click for details</summary>

Before activating or replacing a plugin, check at least:

```text
[ ] Is the provider discovery context explicit?
[ ] Was service/capability compatibility validated?
[ ] Is ambiguity/priority policy defined?
[ ] What happens when a required provider is missing?
[ ] Which resources are rolled back after initialization failure?
[ ] Which threads/executors/sockets/files does the plugin create?
[ ] Who owns cleanup?
[ ] How are in-flight requests drained or cancelled?
[ ] Does any host/global cache retain plugin classes or instances?
[ ] Are context ClassLoader and ThreadLocal state restored?
[ ] Does observability include plugin identity/version/cause?
```

This checklist does not replace architecture, but it turns “reload the plugin” from a vague phrase into a lifecycle with explicit ownership.

Test containment using providers that intentionally fail during discovery, initialization, request processing, and cleanup—not only the happy path.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boundary-handoffs">When to Hand Off to ClassLoader, JPMS, Framework Plugins, or Instrumentation</a>

<details>
<summary>Click for details</summary>

Knowing when to **hand the problem to another curriculum owner** is part of understanding this module.

Hand off when the primary question becomes:

```text
detailed ClassLoader delegation/type-identity mechanics?
→ Java Core ClassLoader

complete module readability/exports/opens/resolution semantics?
→ JPMS / Module System

framework extension points, DI scopes, bean lifecycle?
→ the framework-specific owner

bytecode redefine/retransform or Java Agents?
→ Instrumentation

process sandboxing, security, or OS isolation?
→ system/infrastructure architecture
```

Runtime Extensibility keeps enough of these boundary concepts to design hosts and plugins correctly without duplicating their full curricula.

Clear boundaries help distinguish mechanisms used to implement a plugin system from concepts that are actually owned by this module.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-extensibility-synthesis">The End-to-End Mental Model to Retain</a>

<details>
<summary>Click for details</summary>

The final mental model is:

```text
Runtime Extensibility
=
stable contract
+ independently deployable provider
+ explicit discovery context
+ application-owned selection policy
+ lifecycle
+ compatibility
+ isolation/failure boundary
```

In Java:

```text
SPI
→ defines the capability

ServiceLoader
→ locates/loads providers

Class Path / Module Path
→ provider deployment models

ClassLoader
→ visibility and type-identity boundary

Configuration / ModuleLayer
→ dynamic named-module graph
```

If you retain one principle, make it this: **discovery is not the whole plugin architecture**.

Start from the contract and the requirement. Add `ServiceLoader`, custom loaders, or `ModuleLayer` only when each mechanism solves a concrete problem that a simpler level cannot solve.

</details>

- [Quay lại đầu trang](#back-to-top)
