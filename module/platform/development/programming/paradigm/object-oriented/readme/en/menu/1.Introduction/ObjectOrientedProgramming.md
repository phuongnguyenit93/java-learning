<a id="back-to-top"></a>

# Object-Oriented Programming: Objects and Responsibilities

## Menu
- [Object-Oriented Programming: Meaning and Scope](#oop-what)
- [Purpose of Responsibility Boundaries for State and Behavior](#oop-why)
- [Procedural Organization over Separate Data](#oop-before)
- [Objects, Identity, State, Behavior, and Collaboration](#oop-solution)
- [The OOP Mental Model versus Java Language Mechanics](#oop-boundary)
- [Starting Knowledge and the Path from Objects to Design](#oop-learning-path)

## <a id="oop-what">Object-Oriented Programming: Meaning and Scope</a>

<details>
<summary>Click for details</summary>

Object-Oriented Programming is a paradigm that organizes programs around **objects with identity, state, behavior, and responsibility**, with objects collaborating through clear contracts.

OOP does not mean simply splitting code into many classes. A class is only one mechanism that some languages use to build an object model.

An **object** is more than a data bundle: it has an identity, a responsibility in the system, and behavior others may request. Two accounts with equal balances remain distinct objects representing different accounts and histories. State may change, but immutable objects are equally compatible with OOP.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-why">Purpose of Responsibility Boundaries for State and Behavior</a>

<details>
<summary>Click for details</summary>

When data and the rules operating on that data are scattered across a system, changing one business rule can affect many unrelated places.

OOP tries to place related state and behavior behind the appropriate responsibility boundary so that change is easier to contain.

If withdrawal rules are duplicated in a screen, a service, and a scheduled job, changing an overdraft policy requires edits in several places. Giving the account responsibility for approving withdrawals lets callers reuse one behavioral contract instead of recreating the rules.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-before">Procedural Organization over Separate Data</a>

<details>
<summary>Click for details</summary>

Procedural programming can organize software around procedures and separate data structures. This is highly effective for many problems and is not inherently inferior to OOP.

OOP becomes useful when a domain contains many entities or collaborators with non-trivial lifecycle, responsibility, and behavior.

A procedural solution can store `AccountData` separately and call `withdraw(account, amount)` to update it; this is often a clear design. OOP becomes valuable when accounts, history, payment policies, and notifications grow into collaborating responsibilities with different contracts.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-solution">Objects, Identity, State, Behavior, and Collaboration</a>

<details>
<summary>Click for details</summary>

```text
state + behavior
      ↓
responsible object
      ↓
collaboration through contracts
```

Common concepts include encapsulation, abstraction, composition, inheritance/subtyping, and polymorphism.

In an account model, **identity** tells which account this is, **state** may include its balance, **behavior** such as deposit or withdraw governs valid changes, and **responsibility** identifies who makes those decisions. A payment object requests account behavior rather than rewriting its stored balance directly.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-boundary">The OOP Mental Model versus Java Language Mechanics</a>

<details>
<summary>Click for details</summary>

This module owns only the **language-neutral OOP paradigm**.

Keywords, access modifiers, class/interface mechanics, and Java-specific dispatch belong to `programming/language/java/core/oop` and related Java Core modules.

Java constructs such as `class`, `interface`, and `private` can implement object models but do not define OOP by themselves. Start by reasoning about responsibility, contracts, consistency, and collaboration; Java's inheritance syntax, overriding rules, and dispatch mechanics belong to Java Core.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-learning-path">Starting Knowledge and the Path from Objects to Design</a>

<details>
<summary>Click for details</summary>

The starting point is basic data, variables, and actions, not mastery of Java classes. Follow an account from **identity and state**, through **encapsulation and invariants**, to **responsibilities and contracts**; then study **composition, subtyping, and polymorphism**, before evaluating design trade-offs.

</details>

- [Back to top](#back-to-top)
