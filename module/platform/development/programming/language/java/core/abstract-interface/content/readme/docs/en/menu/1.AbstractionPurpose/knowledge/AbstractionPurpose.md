# Why Java Needs Abstract Classes and Interfaces

Before learning `abstract class` or `interface` syntax, start with the problem these mechanisms solve. In real code, consumers often should not depend on one concrete implementation when they only need **a stable set of promised behaviors**.

## <a id="abstraction-mechanism-purpose">The Problem Abstract Classes and Interfaces Solve</a>

### CONCEPT

Suppose checkout depends directly on `CardPayment`:

```java
void checkout(CardPayment payment) {
    payment.pay(100);
}
```

That works, but the consumer is tied to one concrete implementation. If the system later adds `WalletPayment`, `BankTransferPayment`, or another payment mechanism, the caller should ideally depend on **the payment contract**, not each implementation class.

Java can model that need with a common type. This module focuses on two important tools:

```text
abstract class
→ useful when a closely related family of classes shares state, initialization rules, or partial implementation

interface
→ useful when a role/capability should be expressed as a contract
→ unrelated class hierarchies can still implement the same contract
```

### WHY

The purpose of these mechanisms is **not merely to create types that cannot be instantiated directly**. The more important design move is to separate three needs:

```text
What does the consumer need to know?
→ contract

What do related implementations need to share?
→ state / initialization rules / implementation

What additional role may a class need to play?
→ capability expressed by an interface
```

Keeping those concerns distinct reduces dependence on concrete classes and makes the type model easier to extend.

## <a id="abstraction-mechanism-roles">Two Mechanisms, Different Roles</a>

| Design question | Abstract class | Interface |
| --- | --- | --- |
| Can it hold per-object state? | Yes | No independent instance state |
| Can it define constructors? | Yes | No |
| Can it provide implemented behavior? | Yes | Yes, through `default`, `static`, and `private` methods with different roles |
| How many can one class extend/implement? | One superclass | Multiple interfaces |
| Typical modeling role | closely related family with shared implementation | role/capability/contract |

This is only the orientation map. Later chapters explain the detailed rules and limits.

## <a id="abstraction-running-example">Running Example</a>

The module keeps one model consistent across chapters:

```text
PaymentMethod
→ interface describing the payment contract

BasePayment
→ abstract class holding shared state and partial implementation

CardPayment
→ concrete class completing the behavior

Refundable
→ interface describing an independent capability
```

The same example will show why contracts and shared implementation are different responsibilities, how interfaces inherit from one another, and what happens when inherited defaults conflict.

## <a id="abstraction-learning-roadmap">Learning Roadmap</a>

```text
1. Why Java Needs Abstract Classes and Interfaces
        ↓
2. Abstract Classes — Shared State and Partial Implementation
        ↓
3. Interfaces as Behavioral Contracts
        ↓
4. Choosing an Abstract Class or an Interface
        ↓
5. Interface Inheritance and Contract Composition
        ↓
6. Default, Static, and Private Interface Methods
        ↓
7. Multiple Interface Inheritance and Conflict Resolution
        ↓
8. Designing with Abstract Classes and Interfaces
```

By the end, the goal is not merely to remember syntax. You should be able to explain **why a design chooses an abstract class, an interface, or both**, and predict the important rules that apply when interfaces inherit from one another or provide default behavior.
