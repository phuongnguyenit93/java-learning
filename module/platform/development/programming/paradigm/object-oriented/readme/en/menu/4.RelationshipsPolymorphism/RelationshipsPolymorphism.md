<a id="back-to-top"></a>

# Polymorphism, Subtyping, and Inheritance

## Menu
- [Polymorphism — One Contract, Different Implementations](#oop-polymorphism-purpose)
- [A Shared Behavioral Contract for Polymorphic Variants](#oop-shared-behavior-contract)
- [Subtyping — Promises Made by a Subtype](#oop-subtype-relation)
- [Inheritance — Organizing and Reusing Behaviors](#oop-inheritance-concept)
- [Substitutability — Replacing a Subtype without Breaking Expectations](#oop-substitutability)
- [Composition vs. Inheritance — Combining Objects or Extending Types](#oop-composition-vs-inheritance)
- [Extending Behavior Variants through Polymorphism](#oop-behavior-variants)

## <a id="oop-polymorphism-purpose">Polymorphism — One Contract, Different Implementations</a>

<details>
<summary>Click for details</summary>

**Polymorphism** lets a caller make the same contracted request while different objects carry it out appropriately. A `pay(amount)` request may be fulfilled by a wallet or bank transfer through different internal operations.

The caller no longer needs a growing conditional on each payment kind. However, alternatives must honor the agreed behavior; different implementation details do not justify silently violating payment rules.

**One request, different responsible objects.** Checkout must pay 30, but the customer may select WalletPayment or BankPayment. Checkout makes the same conceptual call `pay(30)` on the selected PaymentMethod. The wallet checks its balance, while bank transfer may request provider authorization. Their internal actions differ, yet both return outcomes shaped like **`Success(receiptId)` or `Failure(reason)`** according to the shared contract. Checkout handles the result without growing a `if wallet ... else if bank ...` branch in every caller.

Polymorphism means **behavior varies behind a common request**, not that every variant uses inheritance, stores identical fields, or succeeds. An actual provider decline can legitimately produce `Failure(providerDeclined)`. **Check:** what remains constant in Checkout, and what legitimately differs between wallet and bank transfer?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-shared-behavior-contract">A Shared Behavioral Contract for Polymorphic Variants</a>

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

**Apply the contract table to concrete variants.** For `pay(30)`, WalletPayment checks relevant funds and may return `Success(walletReceipt)`; BankPayment contacts its provider and might return either `Success(bankReceipt)` or `Failure(providerDeclined)`. For amount zero, both should use the **documented InvalidInput outcome**, rather than one silently pretending to process a payment. Checkout sends a success receipt **only after Success**.

A positive amount is **eligible for processing**, not guaranteed acceptance by the payment provider. A properly reported decline is therefore **not** itself a contract violation. Implementations may use different internal steps, but may not falsely report success before completion or throw an undocumented error instead of the promised outcome. This is a **conceptual contract chosen for this learning scenario**, not executable Java API syntax. **Check:** does a bank's `Failure(providerDeclined)` for `pay(30)` break a contract explicitly allowing failures?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-subtype-relation">Subtyping — Promises Made by a Subtype</a>

<details>
<summary>Click for details</summary>

A **subtype** promises to work wherever a broader contract is expected. If `WalletPayment` is a subtype of `PaymentMethod`, wallet objects must satisfy what callers legitimately expect of that payment method.

Sharing method names is not enough for sound subtyping. Suppose the common contract accepts any positive amount **for processing** and promises an explicit success-or-failure result. A subtype that throws an undocumented `unsupported amount` exception for a positive request violates that contract. A properly reported provider decline, however, is an allowed failure, not evidence of broken subtyping.

**Replace an object where only the general contract is known.** Checkout accepts any PaymentMethod that promises to **process** positive requests and then produce explicit success-or-failure results. WalletPayment is a valid subtype only if it honors those legitimate caller expectations. Suppose the wallet secretly adds “positive amounts must also be below 20” and throws an **undocumented** `unsupported amount` error for `pay(30)`. It has **strengthened the caller's preconditions** beyond the published contract.

BankPayment, on the other hand, may process 30, receive a real provider decline, and return the contract's documented `Failure(providerDeclined)`. Subtyping does not mean “all positive requests succeed” or merely “two classes have methods with identical names.” It is a **behavioral replacement promise**. **Check:** why is an undocumented exception different from a decline that the shared contract explicitly permits?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-inheritance-concept">Inheritance — Organizing and Reusing Behaviors</a>

<details>
<summary>Click for details</summary>

**Inheritance** organizes and may reuse features across types, often forming a type relationship in languages that support it. Reusing code and guaranteeing substitutability are separate goals.

A child payment type can reuse fee-calculation code while changing acceptance rules in a way that breaks a contract. Avoid inheritance solely to deduplicate a few lines; evaluate the actual relationship and consequences of future changes.

**Watch a reuse assumption break.** Imagine a parent payment type computes fees with `fee=0`, and a child design quietly assumes that behavior. A later parent change adds `fee=5`; the inherited calculation can make `pay(30)` charge 35 even though callers expected 30. The failure came from **sharing an unstable assumption through inheritance**, not from the name “parent type.”

Inheritance can **organize and reuse features**, but a legitimate behavioral subtype must still satisfy the **observable contract** callers were promised. Reusing a fee calculation does not itself prove a sound “is-a” relationship. A separate FeePolicy collaborator may contain fee changes more safely when that is the real axis of variation. This lesson does not teach Java `extends`, overriding rules, or runtime dispatch. **Check:** what must still be verified after code has been successfully inherited?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-substitutability">Substitutability — Replacing a Subtype without Breaking Expectations</a>

<details>
<summary>Click for details</summary>

**Substitutability** means using a subtype must not violate the legitimate expectations of the common contract. If payment methods promise an explicit failure result when payment cannot be completed, one implementation must not falsely report success.

Test the same contract scenarios against every variant: valid amounts, invalid amounts, service failures, and result handling. A syntactic inheritance relationship alone is not proof of behavioral substitutability.

**Run the same contract cases against each variant** instead of reading only a type hierarchy.

| Request | Correct WalletPayment | Correct BankPayment | Broken variant |
| --- | --- | --- | --- |
| `pay(30)` completes | `Success(walletId)` | `Success(bankId)` | Reports Success before processing |
| `pay(30)` provider declines | `Failure(reason)` | `Failure(providerDeclined)` | Throws undocumented error instead of Failure |
| `pay(0)` | `InvalidInput` | `InvalidInput` | Silently reports successful payment |

When Checkout substitutes BankPayment for WalletPayment, it can continue relying on the agreed **result categories**. A real provider decline reported through Failure is an **allowed result**, not a violation of behavioral substitutability under the shared contract. A variant that invents extra preconditions, hides failures, or falsely announces success breaks what Checkout may legitimately infer. We test **observable behavior and promises**, not whether one Java class extends another. **Check:** which behavior in the table prevents the broken variant from safely replacing PaymentMethod?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-composition-vs-inheritance">Composition vs. Inheritance — Combining Objects or Extending Types</a>

<details>
<summary>Click for details</summary>

Use **composition** when an object **has** a collaborator or policy; use **inheritance** only when a genuine subtype relationship respects the parent contract. A payment service **has** a fee policy; a fee policy is not itself a payment method.

Composition often allows collaborators to change without affecting an entire subclass hierarchy. Too many wrappers have a cost too: judge the decision by contract stability and the change each responsibility is expected to absorb.

**Compare one fee-policy change under both designs.** Checkout needs to pay 30 and can ask `FeePolicy.quote(30)` for the total. With a replaceable policy collaborator, changing the fee from zero to two changes the total from 30 to 32; Checkout still asks for a quote and calls `pay(total)`. With multiple payment subclasses depending on a fee algorithm inherited from a parent, changing that parent can unintentionally affect every child.

Payment **has a policy**; a policy is not **a kind of payment**. Composition here means **assembling object capabilities**, not necessarily UML composition with lifetime ownership of the FeePolicy. Inheritance remains appropriate for a stable subtype relationship with honored behavioral expectations. Conversely, adding forwarding objects around every tiny calculation has a comprehension cost. **Check:** why is changing a fee commonly a collaborator replacement rather than a new subtype of Checkout?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-behavior-variants">Extending Behavior Variants through Polymorphism</a>

<details>
<summary>Click for details</summary>

When a new fast-transfer method is added, the caller may keep using the established payment contract. The new variant implements its behavior and passes existing contract tests instead of forcing changes at every call site.

Polymorphism is valuable where behavior **genuinely varies**. For a single calculation with no meaningful alternatives or independent state, introducing a large object hierarchy can be harder to understand than a straightforward procedure.

**Introduce FastTransferPayment without changing the caller's contract.** Before the change, Checkout receives a PaymentMethod, calls `pay(30)`, and handles `Success(receiptId)` or `Failure(reason)`. Afterward, the **configuration or selection boundary** can supply a new FastTransferPayment; Checkout makes the same call and handles the same result types. The new method may use a different provider, but must still pass common contract cases: positive requests are processed, a real decline gives Failure, and zero gives the documented InvalidInput outcome.

Do not claim that adding a variant requires **zero edits anywhere**: the place that registers or selects a payment method may need to change. The gain is **localized change**, not copying type-based branches into all consumers. If there is only a small calculation with no actual variation, a polymorphic hierarchy might be unnecessary. **Check:** which part must learn that FastTransfer exists, and which part only needs the PaymentMethod promise?

</details>

- [Back to top](#back-to-top)
