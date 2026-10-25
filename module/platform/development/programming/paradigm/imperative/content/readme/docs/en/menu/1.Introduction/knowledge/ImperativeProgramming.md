<a id="back-to-top"></a>

# Imperative Programming: Expressing Computation as Actions

## Menu
- [Imperative Programming: Meaning and Scope](#imperative-what)
- [Purpose and Role of Explicit Execution Control](#imperative-why)
- [Problems Requiring Ordered Actions](#imperative-before)
- [Commands, State, and Result: A First Model](#imperative-solution)
- [Connections to Declarative, Object-Oriented, and Functional Styles](#imperative-boundary)
- [Starting Point and Learning Path: From Actions to Procedures](#imperative-learning-path)

## <a id="imperative-what">Imperative Programming: Meaning and Scope</a>

<details>
<summary>Click for details</summary>

Imperative Programming describes a program as a sequence of **commands executed in a chosen order**. Commands may change state or perform calculations and other actions without mutating data.

```text
current state
→ command
→ new state
→ next command
```

This diagram illustrates a state-changing path; a command can also leave the current state unchanged.

Imperative programming describes a solution through **ordered actions**. An action may calculate a value, test a condition, print a result, or update state; not every command mutates data. The author specifies meaningful steps along the route to an outcome.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-why">Purpose and Role of Explicit Execution Control</a>

<details>
<summary>Click for details</summary>

Computers execute instructions in sequence and often update memory or state while running. Imperative Programming maps naturally to that execution model, making step-by-step algorithms straightforward to express.

For a withdrawal, checking the balance should precede subtracting money. Reversing the steps might produce an incorrect message or expose a temporary negative balance. Explicit execution control makes such dependencies visible but leaves their correctness in the programmer's hands.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-before">Problems Requiring Ordered Actions</a>

<details>
<summary>Click for details</summary>

When a problem requires explicit control over operation order, branching, loops, and mutation, imperative style makes each execution step visible.

Starting with a balance of 100, a withdrawal of 30 should yield 70. That result alone omits the necessary sequence: validate the amount, check sufficient funds, update the balance, then record the outcome. A simple mathematical calculation, by contrast, may not benefit from spelling out every action.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-solution">Commands, State, and Result: A First Model</a>

<details>
<summary>Click for details</summary>

```text
do A
then B
if condition → do C
repeat D
update state
```

Common concepts include assignment, mutable state, control flow, procedures, and explicit sequencing.

A useful trace separates the **current command**, **state before it**, and **result or state afterward**. With `balance = 100` followed by `balance = balance - 30`, the stored balance transitions from 100 to 70. Evaluating `100 - 30` alone produces a value without updating any variable.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-boundary">Connections to Declarative, Object-Oriented, and Functional Styles</a>

<details>
<summary>Click for details</summary>

Imperative is not absolutely opposed to OOP or Functional Programming. An object-oriented program may still be highly imperative, and functional code may contain imperative sections.

It is an axis describing **how computation is expressed**, not an exclusive category.

A program may combine approaches: checking then updating a balance is imperative, the rule “withdraw only with sufficient funds” can be described declaratively, and an account object may own the behavior. Paradigms are not mutually exclusive; Java statement syntax belongs to the language modules.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-learning-path">Starting Point and Learning Path: From Actions to Procedures</a>

<details>
<summary>Click for details</summary>

Begin with basic variables, values, arithmetic, and conditions. The next chapter traces state before and after a command, followed by branches and loops, procedures, and correctness checks. Understanding the examples does not require Java memory-model details or concurrent programming.

</details>

- [Back to top](#back-to-top)
