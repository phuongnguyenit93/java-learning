# Encapsulation

The previous chapter moved responsibility and state into objects. But if callers can still mutate that state arbitrarily, the object does not truly own its responsibility. Encapsulation turns the boundary into a controlled contract.

## <a id="encapsulation-model">What Is Encapsulation?</a>

### WHAT — what is encapsulation?

Encapsulation means **controlling access to state and implementation details** so callers interact through stable behavior rather than controlling every internal detail directly.

`private` is a Java mechanism that supports encapsulation, but it is not the definition of encapsulation.

The following class uses a private field but still has a weak boundary:

```java
class Account {
    private int balance;

    int getBalance() { return balance; }
    void setBalance(int balance) { this.balance = balance; }
}
```

A caller can still set `balance = -100`. The field is syntactically hidden, but the rule is not protected.

A behavior-oriented API is stronger:

```java
class Account {
    private int balance = 100;

    void withdraw(int amount) {
        if (amount <= 0 || amount > balance) {
            throw new IllegalArgumentException("invalid withdrawal");
        }
        balance -= amount;
    }
}
```

### WHY — what are we actually hiding?

Not every field must be “secret”. The important point is that callers should not depend on **decisions and representations outside their responsibility**.

If `balance` later changes from `int` to `Money`, callers using `withdraw(...)` may remain unchanged. Callers using `setBalance(...)` or reimplementing the rule outside the object are tightly coupled to the old representation.

### RELATION — encapsulation and abstraction are different

The concepts often work together but answer different questions:

```text
Encapsulation
→ who may see or mutate state and implementation?

Abstraction
→ what contract or behavior does the caller need to see?
```

We will return to abstraction at the end of the module. For now, remember: access modifiers are tools; a **meaningful boundary is the goal**.

## <a id="invariant-protection">Protecting Invariants</a>

### WHAT — what is an invariant?

An invariant is a condition that must remain true for a valid object. Examples:

```text
Account.balance >= 0
Order.total >= 0
ShoppingCart contains no quantity <= 0
```

Constructors or factories should establish valid state; every public operation that mutates state should preserve those invariants.

### HOW — model state transitions instead of raw mutation

Instead of exposing a setter for each field, expose an operation with enough context to validate a complete transition:

```java
account.withdraw(30);
```

`withdraw` can validate the amount, balance, and business rules behind one boundary. The caller does not need to know how balance is represented.

### EVIDENCE — `EncapsulationController#invariantProtection()`

The module experiment creates an `Account` with balance `100`, withdraws `30`, then tries to withdraw another `100`.

The important observation is not just the exception. The evidence is:

```text
invalid request is rejected
        +
final balance is still valid
        =
the invariant is preserved
```

That is the concrete value of encapsulation: the object owns responsibility for its own state transitions.

### PRACTICE — find invariants before adding setters

Before adding a setter, ask: **does this field participate in a rule with other state?** If so, an intent-oriented operation is often safer than independent mutation.

## <a id="tell-dont-ask-boundary">Tell, Don't Ask</a>

### WHAT — what does “Tell, don't ask” mean?

It is a heuristic: instead of pulling all data out of an object and making every decision in the caller, ask the object to perform behavior that belongs to its responsibility.

Rule leakage:

```java
if (account.getBalance() >= amount) {
    account.setBalance(account.getBalance() - amount);
}
```

More behavior-oriented:

```java
account.withdraw(amount);
```

In the second version, the caller expresses intent; the object keeps the rule.

### WHY — avoid duplicated domain rules

If several callers check balance independently, the rule is duplicated. If the rule later gains reserved funds or transaction fees, every caller may need to change.

### TRADE-OFF — do not turn the heuristic into dogma

Reporting, serialization, and read models legitimately expose data. DTOs do not need to pretend to be rich domain objects. “Tell, don't ask” is most useful when **domain behavior has leaked out of the object that owns the state**.

### RELATION — from encapsulation to inheritance

Once an object has a clear boundary and contract, the next question appears: **if several types share a contract, or one type is a specialized form of another, how does Java represent that relationship?** That is where inheritance and subtyping begin.
