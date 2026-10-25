<a id="back-to-top"></a>

# Rollback and Failure Semantics

## Menu
- [Default Rollback Rules for Checked and Unchecked Failures](#transaction-rollback-defaults)
- [Rollback and No-Rollback Rules](#transaction-rollback-rules)
- [Rollback-Only State and UnexpectedRollbackException](#transaction-rollback-only)
- [Exception Handling and Swallowed Failure Pitfalls](#transaction-exception-handling)
- [Rollback from Vavr Try and Future Results](#transaction-return-value-rollback)

## <a id="transaction-rollback-defaults">Default Rollback Rules for Checked and Unchecked Failures</a>

<details>
<summary>Click for details</summary>

Spring needs a deterministic rule for turning an exception into a transaction outcome. With declarative transactions, the default rule in Spring Framework 6.1 is intentionally conservative: an unhandled `RuntimeException` or `Error` causes rollback, while a checked `Exception` does not cause rollback by default.

That rule is evaluated when the exception escapes the transactional method and returns to the transaction interceptor. The interceptor is the boundary that decides whether to commit or roll back; merely creating or catching an exception inside the method does not by itself determine the final outcome.

```java
@Transactional
public void placeOrder() {
    inventory.reserve();
    throw new IllegalStateException("payment failed");
}
```

Here `IllegalStateException` is unchecked, so the transaction is rolled back unless a more specific no-rollback rule overrides the default. Do not infer the same behavior for every checked business exception: if a checked exception must roll back, express that policy explicitly.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-rollback-rules">Rollback and No-Rollback Rules</a>

<details>
<summary>Click for details</summary>

Rollback policy is part of the transaction contract, not part of the exception hierarchy alone. `@Transactional` can add explicit `rollbackFor` / `noRollbackFor` rules using exception types, or the class-name variants when type references are not practical.

Prefer type-based rules when possible because they are unambiguous. Name-pattern rules use substring matching and can match more broadly than expected; a pattern such as `"Exception"` is usually far too wide.

```java
@Transactional(
    rollbackFor = BusinessCheckedException.class,
    noRollbackFor = OptimisticWarning.class
)
public void process() throws BusinessCheckedException { ... }
```

If multiple rules match, Spring chooses the strongest matching rule. Treat overrides as business policy: document why a checked exception represents failure severe enough to invalidate the unit of work, or why an unchecked exception is intentionally allowed to commit.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-rollback-only">Rollback-Only State and UnexpectedRollbackException</a>

<details>
<summary>Click for details</summary>

A transaction can be doomed even before the outer method reaches its commit point. Spring represents this through a rollback-only marker. A participating inner scope can mark the shared physical transaction rollback-only; the outer scope can continue executing Java code, but it can no longer successfully commit that transaction.

This is the mental model behind `UnexpectedRollbackException`: the outer caller requested commit, but the physical transaction had already been marked rollback-only. Spring throws the exception so the caller is not falsely told that a commit succeeded.

```text
outer REQUIRED scope
    ↓ joins same physical transaction
inner REQUIRED scope
    ↓ marks rollback-only
outer continues
    ↓ asks to commit
UnexpectedRollbackException
```

Do not catch `UnexpectedRollbackException` as a normal success path. Investigate which participating scope marked the transaction rollback-only and whether the transaction boundary or propagation policy is wrong.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-exception-handling">Exception Handling and Swallowed Failure Pitfalls</a>

<details>
<summary>Click for details</summary>

Exception handling inside a transactional method can accidentally hide failure from Spring. If code catches an exception, converts it to a normal return value, and does not mark the transaction rollback-only, the interceptor sees a successful method return and may commit.

```java
@Transactional
public boolean reserve() {
    try {
        repository.update();
        riskyOperation();
        return true;
    } catch (RuntimeException ex) {
        return false; // interceptor sees a normal return
    }
}
```

Possible remedies depend on intent: rethrow the failure, translate it to another exception covered by rollback rules, or explicitly call `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` when the method must return normally but the unit of work is invalid.

Explicit rollback signaling couples business code to Spring transaction infrastructure, so use it sparingly. Prefer exception-based flow when it communicates the failure model clearly.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-return-value-rollback">Rollback from Vavr Try and Future Results</a>

<details>
<summary>Click for details</summary>

Spring 6.1 recognizes a small set of return-value failure forms in addition to thrown exceptions. A returned Vavr `Try` that is a failure can expose its underlying exception to the transaction interceptor when Vavr is present. Spring 6.1 also inspects a returned `Future` / `CompletableFuture`: if it is **already exceptionally completed when the transactional method returns**, its failure can be evaluated at that boundary.

These return-value integrations do not bypass the transaction attribute's rollback rules. Spring evaluates the underlying failure through the same rollback decision (`rollbackOn`) used for thrown failures: a checked cause still does not roll back by default, and an explicit `noRollbackFor` rule can suppress rollback for a matching unchecked failure.

This does not make an imperative transaction follow asynchronous work onto another thread. The decision is still made at the method-return boundary. A future that completes successfully at return time and fails later does not retroactively roll back a transaction that has already completed.

```text
method returns failed CompletableFuture
→ failure already visible to interceptor
→ rollback rules are evaluated

method returns incomplete CompletableFuture
→ transaction boundary completes according to the visible outcome
→ later async failure is outside that completed transaction
```

Keep this distinction explicit when combining `@Transactional` and `@Async`; thread/context propagation is a separate concern.

</details>

- [Back to top](#back-to-top)
