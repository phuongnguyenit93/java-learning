<a id="back-to-top"></a>

# Functional Programming Decisions and Integration

## Menu
- [Useful Cases for Functional Transformations](#functional-use-cases)
- [Readability, Allocations, and Debugging Costs](#functional-costs)
- [Functional, Imperative, and Object-Oriented Choices](#functional-vs-other-styles)
- [End-to-End Reasoning for a Small Processing Task](#functional-end-to-end)
- [Boundaries with Java Functional APIs and Reactive Flows](#functional-boundaries)

## <a id="functional-use-cases">Useful Cases for Functional Transformations</a>

<details>
<summary>Click for details</summary>

Choose transformations for tasks with identifiable input snapshots and rules: invoice totals, pricing, normalization, eligibility, and deterministic validation. A strong candidate lets the caller explain a result without asking which unrelated component last mutated a shared object. A poor candidate is a device session or externally coordinated workflow whose main purpose is preserving a changing relationship with the world. Functional calculations can still serve such workflows.

</details>

- [Back to top](#back-to-top)

---

## <a id="functional-costs">Readability, Allocations, and Debugging Costs</a>

<details>
<summary>Click for details</summary>

Immutable transformations simplify reasoning but may allocate new objects and pressure garbage collection. A long chain of anonymous functions can also make errors harder to locate. Persistent structures may share unchanged subtrees, yet cost depends on implementation and workload. Prefer meaningful names, inspect intermediate values, and benchmark critical paths. A mutable local counter inside a well-contained procedure may be simpler and perfectly reasonable.

</details>

- [Back to top](#back-to-top)

---

## <a id="functional-vs-other-styles">Functional, Imperative, and Object-Oriented Choices</a>

<details>
<summary>Click for details</summary>

Imperative style makes action order explicit; object-oriented design locates responsibility in collaborating objects; functional design emphasizes value transformations and composition. These are different organizing perspectives rather than exclusive language categories. An Order object may own its invariant, a pure function may compute discount eligibility, and an imperative coordinator may call a payment gateway. Choose boundaries that make each responsibility visible, not one paradigm for every line.

</details>

- [Back to top](#back-to-top)

---

## <a id="functional-end-to-end">End-to-End Reasoning for a Small Processing Task</a>

<details>
<summary>Click for details</summary>

For order A with subtotal 100 and 10% discount, read a stable order snapshot and explicit policy. Compute discounted subtotal 90, tax at 5% as 4.5, and receipt amount 94.5; each result can be asserted independently. Only then does an adapter attempt payment and persistence, checking outcomes and potential duplicate requests. Trace input, calculation, decision, and effects; a changed discount policy should change only the expected value calculation.

~~~text
read A(100) -> discount 10% -> 90 -> tax 5% -> 94.5
                                     -> charge -> save
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="functional-boundaries">Boundaries with Java Functional APIs and Reactive Flows</a>

<details>
<summary>Click for details</summary>

This module explains functional concepts, not Java functional interfaces, lambda capture rules, method references, or JDK Stream pipeline mechanics; those belong to Java language modules. Data-oriented programming prioritizes inspectable data separated from operations; reactive programming describes signals propagating over time, subscriptions, and possibly demand. Both may use composition yet neither is identical to pure functional programming.

### References

- [Clojure Functional Programming](https://clojure.org/about/functional_programming): pragmatic purity and functions as values.
- [Values and Change](https://clojure.org/about/state): immutable values and changing state.

</details>

- [Back to top](#back-to-top)
