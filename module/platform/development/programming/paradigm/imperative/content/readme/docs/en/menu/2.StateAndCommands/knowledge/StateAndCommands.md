<a id="back-to-top"></a>

# Program State, Commands, and Execution Order

## Menu
- [Program State and Values at a Point in Time](#imperative-state-model)
- [Commands that Act and Expressions that Calculate](#imperative-commands-values)
- [Assignment and State Transitions](#imperative-assignment)
- [Statement Order and Dependencies Between Actions](#imperative-sequencing)
- [Tracing State Before and After an Action](#imperative-state-tracing)
- [Consequences of Unexpected Mutation](#imperative-unexpected-mutation)

## <a id="imperative-state-model">Program State and Values at a Point in Time</a>

<details>
<summary>Click for details</summary>

**Program state** means the currently stored values at a point in execution, not the entire computation history. A small cash register might hold `balance = 100` and `transactions = 0`. Reading those values does not, by itself, change them.

A useful execution trace marks the state before and after a command. A valid withdrawal of 30 takes the stored balance from 100 to 70; a rejected withdrawal may leave it at 100. Keep emitted messages distinct from stored state.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-commands-values">Commands that Act and Expressions that Calculate</a>

<details>
<summary>Click for details</summary>

An **expression** computes a value; a **command** specifies an action in an execution flow. `balance - fee` may evaluate to 95 while leaving `balance` untouched. An assignment can take that computed value and update stored state; a check or output action is still an ordered step.

Do not infer that every imperative step mutates state. The condition `balance >= amount` produces true or false without changing the balance. State changes only if a subsequently chosen action writes data. This distinction makes side effects easier to identify.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-assignment">Assignment and State Transitions</a>

<details>
<summary>Click for details</summary>

Assignment means **compute a new value and store it in a named location**, not assert mathematical equality. In `balance = balance - 30`, the right-hand side reads the old balance, computes 70, and the left-hand side receives 70.

Each assignment may be a state transition. Two approved withdrawals of 30 produce 100 → 70 → 40. If an update must be forbidden for insufficient funds, checking the condition before assigning is part of the program's correctness.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-sequencing">Statement Order and Dependencies Between Actions</a>

<details>
<summary>Click for details</summary>

Statement order matters when a later step uses data produced by an earlier one. Computing `total = price * quantity` and then subtracting a discount is not generally equivalent to discounting the unit price before multiplication. The difference is in business meaning, not merely performance.

Independent actions may be safely reordered, but actions sharing reads or writes need a dependency check. Marking “step A supplies a value to step B” makes an incorrect execution sequence visible.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-state-tracing">Tracing State Before and After an Action</a>

<details>
<summary>Click for details</summary>

Create a trace table with **step, condition, balance before, balance after, and output**. From 100, a successful withdrawal of 30 followed by a rejected request for 90 yields states 100 → 70 → 70. The rejection message is an output, not a balance update.

Trace skipped actions as well as executed ones: if subtraction is inside the sufficient-funds branch, the failure branch must not subtract anything. The recorded transitions are concrete evidence for a correctness test.

| Request | Condition at this step | Balance before → after | Observable result |
| --- | --- | --- | --- |
| Withdraw 30 | 100 ≥ 30, accepted | 100 → 70 | Success |
| Withdraw 90 | 70 < 90, rejected | 70 → 70 | Insufficient funds |

The second row has an output even though no balance update took place; the condition reads the **current** state, not the original 100.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-unexpected-mutation">Consequences of Unexpected Mutation</a>

<details>
<summary>Click for details</summary>

An unexpected update makes later outcomes hard to explain. Imagine `printBalance()` accidentally deducting a service fee every time it displays a value: an observation unexpectedly changes what is being observed.

Identify which operations are allowed to write state and verify their before-and-after conditions. With several potential writers, investigate the update history; synchronization between threads is a separate concurrency topic.

</details>

- [Back to top](#back-to-top)
