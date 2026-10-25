<a id="back-to-top"></a>

# Transaction Attributes and Boundary Design

## Menu
- [Transaction Policy as a Boundary Contract](#transaction-policy-model)
- [Spring 6.1 @Transactional Defaults](#transaction-defaults)
- [Isolation and Existing-Transaction Validation](#transaction-isolation)
- [Timeout, Read-Only, and Transaction Labels](#transaction-policy-hints)
- [Choosing an Appropriately Sized Transaction Boundary](#transaction-boundary-placement)

## <a id="transaction-policy-model">Transaction Policy as a Boundary Contract</a>

<details>
<summary>Click for details</summary>

Transaction attributes form a **boundary contract**: they describe how a method should behave when entering transaction infrastructure and how that behavior relates to an existing transaction.

The contract answers questions such as:

- join an existing transaction or create/suspend one?
- request which isolation level and timeout?
- is the work read-only?
- which manager should execute it?
- which failures cause rollback?

These are application policy decisions. The transaction manager then translates them to the underlying resource, which may support some options differently. Treat attributes as a coherent policy instead of independent annotation switches.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-defaults">Spring 6.1 @Transactional Defaults</a>

<details>
<summary>Click for details</summary>

Spring Framework 6.1 gives `@Transactional` a practical default policy:

```text
propagation = REQUIRED
isolation   = DEFAULT
readOnly    = false
timeout     = underlying-system default
rollback    = RuntimeException and Error by default
```

`DEFAULT` isolation delegates to the underlying transaction system. The default timeout likewise comes from the transaction system (or no explicit timeout where unsupported).

These defaults are reasonable for many service methods, but they are not a guarantee that every backend behaves identically. Override a default only when the business/resource requirement is clear. Excessive per-method configuration makes transaction policy harder to reason about and may be ignored when the method merely participates in an existing physical transaction.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-isolation">Isolation and Existing-Transaction Validation</a>

<details>
<summary>Click for details</summary>

Isolation is a property of a **physical transaction**. Declaring an isolation level matters when the current scope actually starts a new transaction; a participating `REQUIRED` scope cannot change the isolation of the already-running transaction it joins.

Spring's annotation therefore documents isolation as intended for newly started transactions, commonly with `REQUIRED` when no transaction exists or with `REQUIRES_NEW`.

By default, participating scopes are lenient: local isolation/read-only declarations can be ignored in favor of the outer transaction. Supporting transaction managers can enable strict validation (`validateExistingTransaction`) so incompatible isolation declarations—and read-only mismatches covered by the manager's validation—are rejected.

Choose isolation from data-consistency requirements and database behavior; this module does not replace the database isolation/anomaly curriculum.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-policy-hints">Timeout, Read-Only, and Transaction Labels</a>

<details>
<summary>Click for details</summary>

Timeout, read-only, and labels refine transaction policy but have different strength.

**Timeout** applies to a newly started transaction and requests that the manager/backend stop or fail work that exceeds the configured duration. Enforcement details are resource specific.

**Read-only** is a hint to the transaction subsystem. It may enable optimizations, but Spring's contract does not guarantee that every attempted write fails. A manager that cannot interpret the hint may ignore it.

**Labels** are descriptive strings attached to a Spring transaction attribute. A transaction manager may interpret them as manager-specific options or use them only descriptively.

Do not use these attributes as substitutes for business authorization or data validation. They describe transaction execution policy, not application correctness rules.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-boundary-placement">Choosing an Appropriately Sized Transaction Boundary</a>

<details>
<summary>Click for details</summary>

An appropriately sized transaction includes every state change required by one invariant and excludes work that does not need to hold the same resources.

Too narrow:

```text
save order   → commit
reserve stock → fail
```

The system can expose partial business state.

Too broad:

```text
open DB transaction
→ remote HTTP call
→ large file processing
→ user-dependent wait
→ commit
```

Connections and locks are held longer than necessary, and remote failure still is not made atomic with the local database.

Place boundaries around business state transitions, commonly in service/application methods. Keep slow or unrelated work outside where possible, then coordinate post-commit side effects explicitly.

</details>

- [Back to top](#back-to-top)
