<a id="back-to-top"></a>

# Call Sites and Changeable Targets

## Menu
- [CallSite, Target, and Dynamic Invoker](#call-site-model)
- [The Permanent CallSite Type](#call-site-type-invariant)
- [ConstantCallSite, MutableCallSite, and VolatileCallSite](#call-site-kinds)
- [Target Updates and Visibility](#call-site-target-updates)
- [Call-Site Linkage vs Target Changes](#call-site-linkage-vs-target-change)

## <a id="call-site-model">CallSite, Target, and Dynamic Invoker</a>

<details>
<summary>Click for details</summary>

`CallSite` is an object that holds a `MethodHandle target`. Once an `invokedynamic` instruction is linked to a CallSite, calls delegate to the **current target** of that CallSite.

Three important pieces:

```text
CallSite
├── type()           → permanent MethodType
├── getTarget()      → current target
└── dynamicInvoker() → MethodHandle that invokes through the CallSite
```

Example:

```java
MethodHandle target =
        MethodHandles.constant(
                String.class,
                "v1"
        );

CallSite site =
        new ConstantCallSite(target);

MethodHandle invoker =
        site.dynamicInvoker();
```

CallSite does not discover a method by itself. It is the runtime abstraction that **holds the target** after bootstrap or another runtime policy has selected that target.

</details>

- [Back to top](#back-to-top)

---

## <a id="call-site-type-invariant">The Permanent CallSite Type</a>

<details>
<summary>Click for details</summary>

A CallSite's type is fixed when the site is created:

```java
MethodType type = site.type();
```

If the site type is:

```text
(String)String
```

every later replacement target must have **exactly the same MethodType**.

```java
MutableCallSite site =
        new MutableCallSite(
                MethodType.methodType(
                        String.class,
                        String.class
                )
        );

site.setTarget(targetWithSameType);
```

A mismatched target causes `WrongMethodTypeException`.

```text
CallSite identity
        +
permanent MethodType
        ↓
target may be fixed or mutable
        but
target.type() must remain == site.type()
```

</details>

- [Back to top](#back-to-top)

---

## <a id="call-site-kinds">ConstantCallSite, MutableCallSite, and VolatileCallSite</a>

<details>
<summary>Click for details</summary>

The three concrete forms have different target behavior:

| Type | Target | Visibility model |
| --- | --- | --- |
| `ConstantCallSite` | Never changes | Permanent target |
| `MutableCallSite` | Can change | Update behaves like an ordinary field; other threads may not immediately observe it |
| `VolatileCallSite` | Can change | Reads/writes have volatile-like semantics |

`ConstantCallSite` is appropriate when bootstrap resolves a target once:

```java
return new ConstantCallSite(target);
```

`MutableCallSite` fits optimization runtimes where a target may be updated and visibility is managed explicitly.

`VolatileCallSite` is simpler when target updates need volatile-style visibility, at the cost of stronger synchronization semantics.

Choose based on **whether the target changes and what visibility guarantee is required**, not merely on the class name.

</details>

- [Back to top](#back-to-top)

---

## <a id="call-site-target-updates">Target Updates and Visibility</a>

<details>
<summary>Click for details</summary>

For `MutableCallSite`, `setTarget` does not provide the same cross-thread visibility as a volatile write.

```java
site.setTarget(newTarget);
```

When a runtime needs to force synchronization of a group of mutable call sites:

```java
MutableCallSite.syncAll(
        new MutableCallSite[]{site}
);
```

With multiple writers, `setTarget` and `syncAll` generally need mutual exclusion so publication updates do not race with one another. Readers may observe the new target immediately after `setTarget` and before `syncAll`, or may keep seeing an older target until `syncAll` completes. `syncAll` can be expensive, so it is better suited to batched, relatively infrequent updates.

`VolatileCallSite` provides stronger visibility behavior:

```java
volatileSite.setTarget(newTarget);
```

This module stops at API-level behavior. Formal happens-before reasoning, memory ordering, and lock-free correctness belong to Java Concurrency.

A common mistake is assuming “mutable target” automatically means volatile publication. It does not.

</details>

- [Back to top](#back-to-top)

---

## <a id="call-site-linkage-vs-target-change">Call-Site Linkage vs Target Changes</a>

<details>
<summary>Click for details</summary>

Keep these two operations separate:

```text
invokedynamic linkage
→ instruction is resolved/linked to one CallSite
→ it does not repeatedly switch to different CallSite objects

CallSite target update
→ MutableCallSite / VolatileCallSite can change the MethodHandle target
→ the dynamic invoker dispatches to the current target
```

So “invokedynamic relinks every time the target changes” is the wrong model.

```text
instruction
   │  link once
   ↓
CallSite
   │  target may change
   ↓
MethodHandle target
```

This distinction is essential before learning the bootstrap lifecycle in the next chapter.

</details>

- [Back to top](#back-to-top)
