<a id="back-to-top"></a>

# Correctness, Trade-offs, and Paradigm Connections

## Menu
- [Preconditions, Transitions, and Expected Outcomes](#imperative-correctness)
- [Risks of Mutable State and Action Ordering](#imperative-state-risks)
- [Debugging Through State-Change Histories](#imperative-debug-tracing)
- [Benefits and Costs of Explicit Execution Control](#imperative-control-tradeoffs)
- [Imperative Steps versus Declarative Descriptions](#imperative-declarative-contrast)
- [Procedures, Other Paradigms, and the Java Boundary](#imperative-paradigm-bridges)

## <a id="imperative-correctness">Preconditions, Transitions, and Expected Outcomes</a>

<details>
<summary>Click for details</summary>

Correctness starts with **preconditions**, permitted state transitions, and **postconditions**. A withdrawal of 30 from 100 requires a positive amount and sufficient funds; success should leave a balance of 70 and preserve the no-negative condition.

For a rejected withdrawal, the postcondition should state that the balance is unchanged. Testing both accepted and rejected requests provides better evidence than one happy-path example. This is state-transition reasoning, independent of a testing framework.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-state-risks">Risks of Mutable State and Action Ordering</a>

<details>
<summary>Click for details</summary>

Failures often arise when multiple actions read or write the same value: updating in the wrong branch, applying a discount twice, or reusing stale data. Calculating a fee against the wrong snapshot can violate a business rule even when the arithmetic is valid.

Shared mutable state also makes behavior dependent on execution history. Track who may write, when values are read, and which conditions must be rechecked; locks and multithreaded memory behavior belong to concurrency modules.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-debug-tracing">Debugging Through State-Change Histories</a>

<details>
<summary>Click for details</summary>

When an outcome is wrong, work backward from the violated postcondition to the most recent state update. Recording `balance before | amount | branch | balance after` identifies whether a condition, assignment, or ordering decision caused the fault.

If a withdrawal of 90 after withdrawing 30 takes a balance of 70 to -20, the trace shows that the rejection branch failed to guard the update. Recording inputs, outputs, and stored state separately makes the issue reproducible.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-control-tradeoffs">Benefits and Costs of Explicit Execution Control</a>

<details>
<summary>Click for details</summary>

Step-by-step control fits work where ordering matters, such as updating an account, validating a sequence, or controlling a device. Its advantage is visible decisions about state changes and potential failure points.

The cost is verbose, order-dependent logic, especially with nested branches and loops. If the problem mainly asks which data satisfies a condition, a declarative specification may express the intent more compactly.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-declarative-contrast">Imperative Steps versus Declarative Descriptions</a>

<details>
<summary>Click for details</summary>

An imperative solution loops over transactions, tests `amount > 0`, and adds qualifying amounts to an accumulator. A declarative description states “sum the positive amounts” without prescribing individual accumulator updates.

For `[10, -2, 25]`, both descriptions can yield 35. They differ in how much **execution procedure** the author specifies. A declarative evaluator still performs real operations, and declarative systems do not universally eliminate ordering or side effects.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-paradigm-bridges">Procedures, Other Paradigms, and the Java Boundary</a>

<details>
<summary>Click for details</summary>

**Procedural programming** organizes imperative actions into callable units; it is not an unrelated opposing paradigm. OOP organizes state, behavior, and responsibility around objects; functional style favors explicit transformations; declarative style emphasizes what must hold.

Real applications combine these views. An object method can execute ordered commands, and a functional transformation can be invoked from a procedure. Java language modules own statement, scope, loop, and method mechanics; this module owns the mental model.

</details>

- [Back to top](#back-to-top)
