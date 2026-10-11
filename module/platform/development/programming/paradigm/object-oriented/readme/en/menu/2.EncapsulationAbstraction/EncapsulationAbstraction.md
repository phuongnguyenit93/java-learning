<a id="back-to-top"></a>

# Objects, Encapsulation, and Abstraction

## Menu
- [Object Identity — Same Object vs. Equal Value](#oop-object-identity)
- [State and Behavior — Including Objects That Do Not Change](#oop-state-behavior)
- [Encapsulation — Controlling Changes to Internal State](#oop-encapsulation-purpose)
- [Object Invariant — Conditions Preserved by Valid Operations](#oop-object-invariants)
- [Invariant vs. Immutability — Validity Rules and Unchanging Data](#oop-invariant-vs-immutability)
- [Information Hiding — Concealing Internal Design Decisions](#oop-behavior-boundary)
- [Abstraction — Exposing Capabilities without Implementation Details](#oop-abstraction-value)

## <a id="oop-object-identity">Object Identity — Same Object vs. Equal Value</a>

<details>
<summary>Click for details</summary>

**Object identity** asks “which entity is this?”; **value equality** asks “how similar are these contents?” Two accounts with a balance of 100 are not the same account; ownership and histories may differ.

Two separately created values representing `100 VND`, however, may be considered equal by their contents. Whether identity or value matters is a domain-design decision, not something determined solely by constructing two objects.

**Compare two easily confused situations.** First, A and B are separate accounts, each with 100. A successful `A.withdraw(30)` leaves A at 70 while B remains 100. Equal balances never merged their identities. Second, a checkout screen and a report may both refer to **the very same A**. After the screen requests an accepted withdrawal of 30, reading A again through the report reveals 70. That is two ways of accessing **one account**, not two accounts.

Money such as `100 VND`, by contrast, is often compared by **value**: two separately calculated amounts can be equivalent without being one account. The question to ask is *are these the same entity, or merely equal amounts?* The difference makes sense without Java `==` or `equals()`. **Check:** if A and B both end at 70 through separate transactions, may their transaction histories be merged as one?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-state-behavior">State and Behavior — Including Objects That Do Not Change</a>

<details>
<summary>Click for details</summary>

State is the data associated with an object, while behavior is what requests the object can answer. An account has a balance and a withdrawal operation; a `canWithdraw` check may only inspect state without changing it.

Objects need not be mutable. An address or fee-policy object can be constructed once and expose calculations without later mutation. Equating OOP with mutable data overlooks valuable immutable object designs.

**Behavior can read state or change it.** Account A starts at `balance = 100`. Asking `canWithdraw(30)` **reads** the balance and answers yes; A remains at 100. Asking `withdraw(30)` checks the rule and then **updates** the account to 70 before reporting the result. When `withdraw(120)` is rejected, the object has still processed a request, even though no balance change occurs. So behavior is not synonymous with mutation.

An immutable FeePolicy can retain its chosen pricing rule and answer `calculateFee(30)` without changing itself between calls. Thinking of an object as an entity with meaningful responsibilities, rather than a bag of writable fields, explains both mutable and immutable examples. **Check:** what makes `canWithdraw` behavior even when balance never changes?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-encapsulation-purpose">Encapsulation — Controlling Changes to Internal State</a>

<details>
<summary>Click for details</summary>

**Encapsulation** establishes a boundary between internal state and the behavior available to callers. If arbitrary code can assign `balance = -50`, an account cannot uphold a nonnegative-balance rule.

A better boundary lets callers request `withdraw(50)` while the account validates it. Java's `private` modifier can help enforce access, but the design goal is protecting **rules and decision-making authority**, not merely hiding a field.

**Trace the unsafe and safe paths.** Account A has 100 and may not go negative. If Checkout calculates `100 - 120 = -20`, writes -20 directly, and only *then* checks validity, the invalid state has already escaped the boundary; noticing the mistake does not automatically undo it. With an encapsulated behavior, Checkout only calls `withdraw(120)`. A first verifies that the amount is positive, then whether enough balance exists. The second condition fails, so A **returns a refusal and stays at 100**. For a request of 30, both conditions pass and A then changes to 70.

Encapsulation is not a demand to hide every datum. It means outsiders cannot bypass the authority of the behavior that protects a rule. A Java `private` field can help, but a public `setBalance(-20)` would still defeat the intended invariant. **Check:** which decision must happen before changing the balance, and why?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-object-invariants">Object Invariant — Conditions Preserved by Valid Operations</a>

<details>
<summary>Click for details</summary>

An **object invariant** is a condition that must hold whenever an object exposes a valid state. A non-overdraft account might require `balance >= 0`; construction and every permitted update must maintain that condition.

With a balance of 100, withdrawing 30 leaves 70 and preserves the invariant. Withdrawing 120 should fail without exposing -20. An invariant does not mean a variable never changes: the balance can change while the rule stays true.

**Follow the state through both branches.** A starts with 100 and satisfies `balance >= 0`. It receives `withdraw(30)`: check `30 > 0` → true; check `100 >= 30` → true; update the balance to **70**; report success. The invariant holds afterward. Reset to the starting 100 and request `withdraw(120)`: the amount is positive but funds are insufficient, so **skip the update**, return a documented failure, and expose **100** afterward. Both traces support the same validity rule.

Here “always” means whenever the account exposes a **valid operation boundary**; it need not describe each inaccessible intermediate calculation. Another product might allow overdrafts and therefore have a different invariant. Nor does this single-object reasoning make simultaneous requests or distributed transactions atomic. **Check:** after the rejected 120 request, which specific state proves the account remains valid?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-invariant-vs-immutability">Invariant vs. Immutability — Validity Rules and Unchanging Data</a>

<details>
<summary>Click for details</summary>

An **invariant** is a condition that remains true; **immutability** means a value cannot be changed after creation. An account moving from balance 100 to 70 can preserve `balance >= 0` while its state remains mutable.

An immutable transaction record can still be invalid when constructed, such as a negative amount forbidden by business rules. Making data unchangeable does not automatically establish validity; validation and behavior contracts still matter.

**Compare three cases.** (1) Account A is **mutable**: withdrawal 30 changes 100 to 70, but its nonnegative-balance invariant remains true. (2) A transaction record is **immutable after creation** but was created with an invalid `amount = -5`: it never changes, yet violates the business rule. (3) A fee policy is constructed with a valid rate and remains unchanged: it is immutable **and** valid. The two properties answer different questions.

An invariant describes *which states count as valid*; immutability describes *whether the data can change after creation*. Immutable objects still need construction-time validation, whereas mutable objects need to preserve their invariants through their exposed operations. The word “immutable” does not guarantee correctness. **Check:** does preventing modification automatically make a negative transaction amount acceptable?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-behavior-boundary">Information Hiding — Concealing Internal Design Decisions</a>

<details>
<summary>Click for details</summary>

An object should expose **meaningful actions** rather than force callers to know which internal fields to modify. `withdraw(amount)` expresses a domain request; a public `setBalance(value)` can enable callers to bypass required checks.

Storage format, history handling, and fee calculations may change behind a stable contract if observable obligations remain intact. This boundary localizes change, but significant effects should not be concealed from callers.

**Hide a decision that can genuinely change.** An early Account implementation might store `balance = 100` directly. A later design might store deposits and withdrawals in a ledger and **derive the balance from entries**. Checkout should not depend on which representation Account uses. In either design it requests `withdraw(30)`; Account checks available funds, accepts the operation, and later reports **70**. The **internal representation choice** changed, while the caller's contracted observations remain stable.

Keep three ideas separate: **encapsulation** controls *which behaviors are allowed to modify state*; **information hiding** shields callers from *changeable representation and decisions*; **abstraction** exposes *which capabilities and outcomes they may rely on*. Important errors and promised side effects remain observable rather than being hidden. **Check:** must Checkout be rewritten when Account switches from a single balance field to a ledger, provided the contract is honored?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-abstraction-value">Abstraction — Exposing Capabilities without Implementation Details</a>

<details>
<summary>Click for details</summary>

**Abstraction** focuses on what an object **promises to do**, not every internal implementation step. A checkout service requests `pay(amount)` and relies on its success/failure contract without knowing every fee-calculation detail.

A useful abstraction is neither a vague `doEverything()` operation nor a complete exposure of internal data. Express meaningful requests, conditions, and observable outcomes so implementations can evolve independently.

**Consider the client's actual needs.** Checkout wants to request `pay(30)` and understand either `Success(receiptId)` or `Failure(reason)`. It needs to know valid request conditions and how failures are reported. It does **not** need to know the provider's internal steps, how many fee calculations occur, or the storage format. Abstraction presents these necessary capabilities as a **usable promise**, rather than exposing a vague `doEverything()` operation or a list of writable fields.

Two payment methods can uphold the same observable result contract while implementing it very differently. Conversely, hiding the very fact that a payment was refused would make the abstraction misleading. Abstraction is *selective exposure of what matters*, not secrecy about every possible outcome. The Java `interface` mechanism can be studied later; the behavioral promise comes first. **Check:** what must Checkout know to respond properly to a declined payment?

</details>

- [Back to top](#back-to-top)
