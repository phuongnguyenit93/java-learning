<a id="back-to-top"></a>

# Object Identity, State, and Encapsulation

## Menu
- [Object Identity versus Value Equality](#oop-object-identity)
- [Associated State and Behavior without Mandatory Mutation](#oop-state-behavior)
- [Encapsulation: Protecting Internal State through Behavior](#oop-encapsulation-purpose)
- [Object Invariants across Behavioral Operations](#oop-object-invariants)
- [Consistency Conditions versus Immutable Data](#oop-invariant-vs-immutability)
- [Exposed Behavioral Boundaries and Hidden Implementation](#oop-behavior-boundary)
- [Abstraction as the Behavior an Object Promises](#oop-abstraction-value)

## <a id="oop-object-identity">Object Identity versus Value Equality</a>

<details>
<summary>Click for details</summary>

**Object identity** asks “which entity is this?”; **value equality** asks “how similar are these contents?” Two accounts with a balance of 100 are not the same account; ownership and histories may differ.

Two separately created values representing `100 VND`, however, may be considered equal by their contents. Whether identity or value matters is a domain-design decision, not something determined solely by constructing two objects.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-state-behavior">Associated State and Behavior without Mandatory Mutation</a>

<details>
<summary>Click for details</summary>

State is the data associated with an object, while behavior is what requests the object can answer. An account has a balance and a withdrawal operation; a `canWithdraw` check may only inspect state without changing it.

Objects need not be mutable. An address or fee-policy object can be constructed once and expose calculations without later mutation. Equating OOP with mutable data overlooks valuable immutable object designs.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-encapsulation-purpose">Encapsulation: Protecting Internal State through Behavior</a>

<details>
<summary>Click for details</summary>

**Encapsulation** establishes a boundary between internal state and the behavior available to callers. If arbitrary code can assign `balance = -50`, an account cannot uphold a nonnegative-balance rule.

A better boundary lets callers request `withdraw(50)` while the account validates it. Java's `private` modifier can help enforce access, but the design goal is protecting **rules and decision-making authority**, not merely hiding a field.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-object-invariants">Object Invariants across Behavioral Operations</a>

<details>
<summary>Click for details</summary>

An **object invariant** is a condition that must hold whenever an object exposes a valid state. A non-overdraft account might require `balance >= 0`; construction and every permitted update must maintain that condition.

With a balance of 100, withdrawing 30 leaves 70 and preserves the invariant. Withdrawing 120 should fail without exposing -20. An invariant does not mean a variable never changes: the balance can change while the rule stays true.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-invariant-vs-immutability">Consistency Conditions versus Immutable Data</a>

<details>
<summary>Click for details</summary>

An **invariant** is a condition that remains true; **immutability** means a value cannot be changed after creation. An account moving from balance 100 to 70 can preserve `balance >= 0` while its state remains mutable.

An immutable transaction record can still be invalid when constructed, such as a negative amount forbidden by business rules. Making data unchangeable does not automatically establish validity; validation and behavior contracts still matter.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-behavior-boundary">Exposed Behavioral Boundaries and Hidden Implementation</a>

<details>
<summary>Click for details</summary>

An object should expose **meaningful actions** rather than force callers to know which internal fields to modify. `withdraw(amount)` expresses a domain request; a public `setBalance(value)` can enable callers to bypass required checks.

Storage format, history handling, and fee calculations may change behind a stable contract if observable obligations remain intact. This boundary localizes change, but significant effects should not be concealed from callers.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-abstraction-value">Abstraction as the Behavior an Object Promises</a>

<details>
<summary>Click for details</summary>

**Abstraction** focuses on what an object **promises to do**, not every internal implementation step. A checkout service requests `pay(amount)` and relies on its success/failure contract without knowing every fee-calculation detail.

A useful abstraction is neither a vague `doEverything()` operation nor a complete exposure of internal data. Express meaningful requests, conditions, and observable outcomes so implementations can evolve independently.

</details>

- [Back to top](#back-to-top)
