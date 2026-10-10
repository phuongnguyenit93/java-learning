<a id="back-to-top"></a>

# Type Relationships, Inheritance, and Polymorphism

## Menu
- [Polymorphism: Concept and Motivation for Varying Behavior](#oop-polymorphism-purpose)
- [A Shared Contract for Alternative Behaviors](#oop-shared-behavior-contract)
- [Subtyping and Promises Made by a Subtype](#oop-subtype-relation)
- [Inheritance as Organization and Reuse](#oop-inheritance-concept)
- [Substitutability and Behavioral Expectations](#oop-substitutability)
- [Choosing Composition versus Inheritance](#oop-composition-vs-inheritance)
- [Extending Behavioral Variants through Polymorphism](#oop-behavior-variants)

## <a id="oop-polymorphism-purpose">Polymorphism: Concept and Motivation for Varying Behavior</a>

<details>
<summary>Click for details</summary>

**Polymorphism** lets a caller make the same contracted request while different objects carry it out appropriately. A `pay(amount)` request may be fulfilled by a wallet or bank transfer through different internal operations.

The caller no longer needs a growing conditional on each payment kind. However, alternatives must honor the agreed behavior; different implementation details do not justify silently violating payment rules.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-shared-behavior-contract">A Shared Contract for Alternative Behaviors</a>

<details>
<summary>Click for details</summary>

A shared payment contract might accept a positive amount and return either confirmation with an identifier or a failure with a reason; it must not signal success before processing is complete. Different objects can implement that contract differently.

Write the contract from the caller's viewpoint, including observable outcomes and failures, rather than requiring identical internal fields. This is what allows new behavioral variants without rewriting each caller.

| Request and situation | Contracted observable result |
| --- | --- |
| Positive amount, payment completes | Confirmation with a valid identifier |
| Positive amount, provider declines or fails | Explicit failure with an explanation |
| Zero or negative amount | Documented invalid-input result |

A positive input is **eligible for processing**, not a promise of successful payment. Declining for an actual provider or account reason is allowed by the contract; silently returning success, or throwing an undocumented exception instead of a failure result, is not.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-subtype-relation">Subtyping and Promises Made by a Subtype</a>

<details>
<summary>Click for details</summary>

A **subtype** promises to work wherever a broader contract is expected. If `WalletPayment` is a subtype of `PaymentMethod`, wallet objects must satisfy what callers legitimately expect of that payment method.

Sharing method names is not enough for sound subtyping. Suppose the common contract accepts any positive amount **for processing** and promises an explicit success-or-failure result. A subtype that throws an undocumented `unsupported amount` exception for a positive request violates that contract. A properly reported provider decline, however, is an allowed failure, not evidence of broken subtyping.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-inheritance-concept">Inheritance as Organization and Reuse</a>

<details>
<summary>Click for details</summary>

**Inheritance** organizes and may reuse features across types, often forming a type relationship in languages that support it. Reusing code and guaranteeing substitutability are separate goals.

A child payment type can reuse fee-calculation code while changing acceptance rules in a way that breaks a contract. Avoid inheritance solely to deduplicate a few lines; evaluate the actual relationship and consequences of future changes.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-substitutability">Substitutability and Behavioral Expectations</a>

<details>
<summary>Click for details</summary>

**Substitutability** means using a subtype must not violate the legitimate expectations of the common contract. If payment methods promise an explicit failure result when payment cannot be completed, one implementation must not falsely report success.

Test the same contract scenarios against every variant: valid amounts, invalid amounts, service failures, and result handling. A syntactic inheritance relationship alone is not proof of behavioral substitutability.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-composition-vs-inheritance">Choosing Composition versus Inheritance</a>

<details>
<summary>Click for details</summary>

Use **composition** when an object **has** a collaborator or policy; use **inheritance** only when a genuine subtype relationship respects the parent contract. A payment service **has** a fee policy; a fee policy is not itself a payment method.

Composition often allows collaborators to change without affecting an entire subclass hierarchy. Too many wrappers have a cost too: judge the decision by contract stability and the change each responsibility is expected to absorb.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-behavior-variants">Extending Behavioral Variants through Polymorphism</a>

<details>
<summary>Click for details</summary>

When a new fast-transfer method is added, the caller may keep using the established payment contract. The new variant implements its behavior and passes existing contract tests instead of forcing changes at every call site.

Polymorphism is valuable where behavior **genuinely varies**. For a single calculation with no meaningful alternatives or independent state, introducing a large object hierarchy can be harder to understand than a straightforward procedure.

</details>

- [Back to top](#back-to-top)
