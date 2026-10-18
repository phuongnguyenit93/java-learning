<a id="back-to-top"></a>

# Immutable Values and Changing State

## Menu
- [Immutable Values and Mutable References](#immutable-values)
- [Values, Identity, and State over Time](#value-vs-state)
- [State Transitions through New Values](#immutable-updates)
- [Structural Sharing and Allocation Trade-offs](#sharing-and-cost)
- [Immutability, Concurrency, and Practical Limits](#immutability-limits)

## <a id="immutable-values">Immutable Values and Mutable References</a>

<details>
<summary>Click for details</summary>

An immutable value does not change after creation, but a variable may be reassigned to a different value. Updating an order should return a new order rather than modifying the original snapshot. If a wrapper contains a mutable nested list, the wrapper is not deeply immutable merely because its top-level reference is fixed. When sharing order history, clarify which objects and nested values are safe from mutation.

~~~text
old={items:[A,B]}; next=addItem(old,C)
old.items=[A,B]; next.items=[A,B,C]
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="value-vs-state">Values, Identity, and State over Time</a>

<details>
<summary>Click for details</summary>

A value describes facts at one moment; an identity such as orderId connects successive versions of the same logical order. State is the current value associated with that identity. Order A may move from pending to paid without rewriting the earlier pending snapshot. This is useful for audits and debugging, but the application remains stateful: a coordinator still selects the current version and manages transitions.

</details>

- [Back to top](#back-to-top)

---

## <a id="immutable-updates">State Transitions through New Values</a>

<details>
<summary>Click for details</summary>

A state transition can be modeled as nextState = transition(previousState, event). For an approved payment event, create a paid order; for a declined payment, return an explicit rejection rather than silently changing the original object. The pure transition decides what state would mean, while the gateway call confirming money transfer is an external effect. These two tasks must not be confused in production flow.

~~~text
pending + PaymentApproved -> paid
pending + PaymentDeclined -> rejected result
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="sharing-and-cost">Structural Sharing and Allocation Trade-offs</a>

<details>
<summary>Click for details</summary>

Copying a large data tree after every small change is expensive. Persistent immutable structures can use structural sharing: a new root points to the changed branch while safe unchanged branches are reused. The old root remains valid and can still be inspected. This is one implementation technique, not a guarantee for every immutable collection; allocation, lookup speed, and garbage collection should be measured where performance matters.

</details>

- [Back to top](#back-to-top)

---

## <a id="immutability-limits">Immutability, Concurrency, and Practical Limits</a>

<details>
<summary>Click for details</summary>

Immutability prevents accidental in-place changes to values; it does not by itself serialize competing updates. Two requests can read order version 3 and both create different version 4 candidates. A coordinator must detect conflicts using locks, compare-and-swap, version checks, or another consistency policy. The same distinction holds for external side effects: immutable data does not make charging a card idempotent or transactions automatically atomic.

</details>

- [Back to top](#back-to-top)
