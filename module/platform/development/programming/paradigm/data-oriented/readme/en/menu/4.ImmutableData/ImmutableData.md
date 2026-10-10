<a id="back-to-top"></a>

# Immutable Data and Application State

## Menu
- [Immutable Values in the Data-Oriented Model](#dop-immutable-principle)
- [Updating Data by Producing a New Version](#dop-state-update)
- [Snapshots, Comparison, and Change History](#dop-state-history)
- [State Coordination and Concurrency Boundaries](#dop-state-coordination)

## <a id="dop-immutable-principle">Immutable Values in the Data-Oriented Model</a>

<details>
<summary>Click for details</summary>

Sharvit's third principle treats represented data values as immutable. A discount function accepts order A and returns an updated order B, leaving A available for comparisons and audits. A variable may be rebound to B, but that does not mean the content of A was mutated. This also applies to nested lines: changing a quantity must create a new safe nested value rather than modifying a shared list behind both versions.

~~~text
A {status:pending, total:100} -> B {status:pending, total:90}
A remains {status:pending, total:100}
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-state-update">Updating Data by Producing a New Version</a>

<details>
<summary>Click for details</summary>

Model updating a line quantity as updateQuantity(order, sku, nextQty) -> newOrder. The function checks the line identity, builds a replacement line, and builds a new lines collection, leaving every prior value unchanged. A user interface or coordinator later decides whether to make newOrder the current application state. Non-mutating transformation simplifies local reasoning, but publishing the new version is a separate action that may fail.

~~~text
old.lines=[{P, qty:2}] -> update(P,3) -> new.lines=[{P,qty:3}]
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-state-history">Snapshots, Comparison, and Change History</a>

<details>
<summary>Click for details</summary>

Immutable versions make before/after evidence straightforward. If version 1 of order A has two products and version 2 adds a third, tests and audit logs can compare snapshots without worrying that the old map has changed underneath. This does not mean storing every snapshot forever: retention, memory sharing, and persistence are independent decisions. Keep stable order identity distinct from the data value that describes its state at a particular time.

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-state-coordination">State Coordination and Concurrency Boundaries</a>

<details>
<summary>Click for details</summary>

Immutability is not a concurrency protocol. Two requests may both read order version 4, derive different version 5 values, and race to make theirs current. The storage layer needs version checks, locking, transactions, or another appropriate consistency rule. Likewise, a map holding a paid status is not evidence that a payment gateway actually charged money. Separate pure construction of candidate data from coordination of shared state and external effects.

</details>

- [Back to top](#back-to-top)
