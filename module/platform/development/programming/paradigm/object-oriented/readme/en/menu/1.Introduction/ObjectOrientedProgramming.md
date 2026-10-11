<a id="back-to-top"></a>

# OOP Foundations: Why Objects and Collaboration Matter

## Menu
- [Object-Oriented Programming (OOP): Meaning and Scope](#oop-what)
- [From Procedural Data and Functions to Clear Responsibilities](#oop-before)
- [Why OOP Groups Related Rules with Responsible Objects](#oop-why)
- [OOP Concept Map: Objects, Principles, and Relationships](#oop-solution)
- [Object vs. Class — A Working Object and Its Blueprint](#oop-class-vs-object)
- [OOP Thinking and Java Class/Interface Mechanisms](#oop-boundary)
- [Starting Knowledge and a Path from Objects to Design](#oop-learning-path)

## <a id="oop-what">Object-Oriented Programming (OOP): Meaning and Scope</a>

<details>
<summary>Click for details</summary>

**Object-oriented programming** (**OOP**) is a way of organizing a program around **objects**. Each object has **its own data**, **actions it can perform**, and **responsibilities** for the part of the system it manages.

For example, in a banking application, we can think of objects such as `BankAccount` (an account), `Customer` (a customer), and `Transaction` (a transaction). Instead of having one place handle all the data, we assign each object the work that belongs to it. These objects collaborate through the actions they provide.

> **Core idea:** When something needs to be done, ask: “Which object is responsible for doing this?” and “How should the objects work together?”

### 1. Start with a familiar situation

Suppose you are programming a feature to **withdraw money from a bank account**. The program needs to know the current balance, the amount the user wants to withdraw, and the rules for deciding whether the transaction is valid.

If we only think in terms of processing steps, the sequence is: check the amount → check the balance → deduct the money. When thinking in OOP terms, we ask another question: **Who should be responsible for ensuring that the account balance always remains valid?**

A reasonable answer is **the account object itself**. The user interface sends a request through `account.withdraw(amount)`; the account checks the request and changes its balance only when the operation is allowed.

```mermaid
flowchart LR
    A[User requests a withdrawal] --> B[Banking application]
    B --> C[account.withdraw(amount)]
    C --> D{Valid request and sufficient funds?}
    D -->|Yes| E[Deduct balance and return success]
    D -->|No| F[Keep balance unchanged and return failure]
```

The banking application does not need to change the balance inside the account directly. It only needs to know how to **send a request** and **receive the result**. The rule that protects the balance is kept in the object that actually owns that responsibility.

### 2. What characteristics does an object have?

Consider Lan's account, which currently holds 100,000 VND:

| Component | Meaning | Example |
| --- | --- | --- |
| **State** | Data describing the object's current condition | Account holder: Lan; balance: 100,000 VND |
| **Behavior** | Actions the object can perform | `deposit(soTien)`, `withdraw(soTien)` |
| **Identity** | What makes each object a distinct entity | Lan's account and Minh's account are two different accounts |
| **Responsibility** | What the object must manage and ensure | Do not allow withdrawals beyond the balance; reject negative withdrawal amounts |

Identity matters: two accounts can have **the same balance** while still being **two different objects**. When Lan withdraws money, Minh's balance does not change as a result.

#### How are classes and objects different?

- **Class:** a description of the common structure and behavior of a type of object, such as `BankAccount`.
- **Object:** a specific instance created from a class, such as `lanAccount` or `minhAccount`.

You can think of a class as **a blueprint**, and an object as **an individual product made from that blueprint**. Each product has its own state. Two objects belonging to the same class do not necessarily share the same balance variable.

### 3. Java example: an object protects its own state

The following program creates Lan's account with 100,000 VND, withdraws 30,000 VND, and then attempts to withdraw another 120,000 VND.

```java
public class Main {
    public static void main(String[] args) {
        BankAccount account = new BankAccount("Lan", 100_000);

        System.out.println(account.withdraw(30_000));
        System.out.println(account.getBalance());

        System.out.println(account.withdraw(120_000));
        System.out.println(account.getBalance());
    }
}

class BankAccount {
    private final String owner;
    private long balance;

    public BankAccount(String owner, long openingBalance) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Tên chủ tài khoản không hợp lệ");
        }
        if (openingBalance < 0) {
            throw new IllegalArgumentException("Số dư ban đầu không thể âm");
        }
        this.owner = owner;
        this.balance = openingBalance;
    }

    public boolean withdraw(long amount) {
        if (amount <= 0 || amount > balance) {
            return false;
        }
        balance -= amount;
        return true;
    }

    public long getBalance() {
        return balance;
    }

    public String getOwner() {
        return owner;
    }
}
```

**Output:**

```text
true
70000
false
70000
```

| Point in time | What happens | Balance after processing |
| --- | --- | ---: |
| Initially | Lan's account is created | 100,000 |
| Withdraw 30,000 | The amount is valid, so the account allows the withdrawal | 70,000 |
| Withdraw 120,000 | The amount exceeds the balance, so the account rejects the request | **70,000 (unchanged)** |

Looking at the code, you can see that:

1. `balance` is the account's **state**.
2. `withdraw()` is a **behavior** that checks the request and then decides whether to change the state.
3. `balance` is declared `private`, so other code cannot arbitrarily assign `account.balance = -5000`.

The rule that the “balance must not be negative” is an example of an **invariant**: a condition that must remain true after every valid operation. This is a simplified example for understanding an object's responsibility; a real banking system must also handle persistence, transactions, and concurrent requests.

### 4. Why is OOP useful as programs grow?

Imagine the same withdrawal feature being used at an **ATM**, in a **mobile app**, and at a **bank counter**. If each place implements its own logic for deducting money, one might overlook a case. When the rules change, we would have to find and update multiple locations.

When the rule is placed in `BankAccount.withdraw()`, all those places call the same behavior. Responsibilities become clearer: the screen handles input and display, while the account checks the request and updates the balance.

With this organization, OOP can help make a program:

- **Easier to understand and maintain:** we know which object is responsible for each rule.
- **Less affected by changes:** changing how an object works internally has less impact on callers that use its stable interface.
- **More reusable:** different parts of the program can use the same objects and shared contracts.
- **Easier to extend and test:** we can replace specific components or test a responsibility on its own.

These benefits depend on **the quality of the design**. Having a large number of classes does not necessarily make a program well designed in OOP terms.

### 5. Four concepts commonly discussed in OOP

You do not need to master all four concepts to understand the basic idea, but you should recognize the problems each concept helps solve.

| Concept | Question it helps answer | Example |
| --- | --- | --- |
| **Encapsulation** | Who may change an object's state, and under what rules? | `balance` is `private`; withdrawals go through `withdraw()` |
| **Abstraction** | What does someone using a component need to know, and which details can be hidden? | You can call `withdraw()` without knowing which statements it uses internally to validate the request |
| **Inheritance** | When an “is-a” relationship exists, how can a subclass receive and extend common characteristics? | `SavingsAccount` could be a kind of `BankAccount` if that matches the business domain |
| **Polymorphism** | How can we call one common behavior while each type of object implements it differently? | Calling `pay()` to pay by card or e-wallet |

#### A short example of polymorphism and collaboration

Suppose a store supports multiple payment methods. Instead of requiring the checkout process to know the details of every payment provider, we define a **shared contract**:

```java
interface PaymentMethod {
    boolean pay(long amount);
}

class CheckoutService {
    boolean checkout(PaymentMethod paymentMethod, long total) {
        return paymentMethod.pay(total);
    }
}
```

`CardPayment` and `EWalletPayment` objects can both implement `PaymentMethod`, while each has its own logic for `pay()`. `CheckoutService` only needs to know that it is collaborating with an object capable of processing payment according to that contract.

```text
                  PaymentMethod
                       |
CheckoutService -> pay(total)
                       |
             +---------+---------+
             |                   |
        CardPayment       EWalletPayment
           (card)             (e-wallet)
```

This example shows that OOP is more than having data and methods inside the same class. A system also consists of **multiple objects sending requests to one another**, with each object managing its own part of the responsibility. Combining collaborating objects in this way is called *composition*; you do not necessarily need to build a complex inheritance hierarchy.

### 6. How does OOP differ from procedural programming?

Both approaches can solve the same problem. The difference often lies in **how data and responsibilities are organized**.

| Perspective | Procedural approach | Object-oriented approach |
| --- | --- | --- |
| Design question | “Which functions are needed, and what steps should they perform?” | “Which objects are responsible, and how do they collaborate?” |
| Organization | Functions operate on data passed to them | Objects manage state and expose behaviors |
| Example call | `withdraw(accountData, amount)` | `account.withdraw(amount)` |
| Common strength | Simple, clear, sequential processing flow | Clear boundaries of responsibility between components |

Simply changing the syntax from `withdraw(accountData, amount)` to `account.withdraw(amount)` **does not automatically produce a good design**. What matters is whether business rules are placed appropriately, state is protected, and responsibilities make sense.

Procedural programming remains suitable for many programs, especially small tasks or linear computational workflows. OOP is one way to organize software, not a mandatory solution to every problem.

### 7. Common misconceptions

- **“OOP means everything must be a class.”** No. Create a class when there is a clear responsibility; you do not need an additional class for every small function.
- **“An object must represent something that exists in the real world.”** No. `Validator`, `PaymentPolicy`, and `Scheduler` can also be useful objects.
- **“Encapsulation just means adding the `private` keyword.”** `private` is a tool. The real goal is to protect the object's state and rules.
- **“Learning OOP means you must use inheritance.”** No. Interfaces and composition are often useful; use inheritance only when the relationship truly fits.
- **“Writing OOP code automatically makes it safe when multiple threads run at once.”** No. If an object has shared state, you still need to design how concurrent access is handled.

### 8. Try answering three questions yourself

Suppose `lanAccount` and `minhAccount` are both created from `BankAccount`, each with a balance of 100,000 VND.

1. **If Lan withdraws 20,000 VND, does Minh's balance decrease?** No, because the two accounts are separate objects with their own states.
2. **Who should reject a withdrawal when the amount exceeds the balance?** The account object, because it is responsible for protecting its own balance.
3. **Does the screen need to know the name of the balance variable and how to update it?** No; the screen uses the `withdraw()` behavior and the result it returns.

When you begin designing a system with OOP, ask yourself: **What objects are there? What is each object responsible for? How should they call one another to complete a task?** These questions provide a starting point for deciding which classes, attributes, methods, and interfaces to create.

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-before">From Procedural Data and Functions to Clear Responsibilities</a>

<details>
<summary>Click for details</summary>

Procedural programming can organize software around procedures and separate data structures. This is highly effective for many problems and is not inherently inferior to OOP.

OOP becomes useful when a domain contains many entities or collaborators with non-trivial lifecycle, responsibility, and behavior.

A procedural solution can store `AccountData` separately and call `withdraw(account, amount)` to update it; this is often a clear design. OOP becomes valuable when accounts, history, payment policies, and notifications grow into collaborating responsibilities with different contracts.

**Compare the same task under equivalent conditions.** A well-structured procedural design may keep `AccountData` and a single `withdraw(account, amount)` function. With account balance 100, withdrawing 30 gives 70, and this procedural solution can be perfectly clear and correct. The problem arises when the screen, a scheduled job, and Checkout each **duplicate** the sufficient-funds decision. Change the rule from allowing the balance to reach zero to requiring a reserve of 20, and there are three implementations to update consistently.

An object-oriented alternative places `withdraw` behind Account's responsibility boundary. Callers request the operation and examine its result; they need not understand the balance representation. **OOP is not inherently less code or more correct.** A single centralized procedural function may already prevent duplication, and wrapping it in a class merely to change syntax adds nothing. **Check:** is the source of the difficulty the procedural style itself, or scattered copies of a business rule?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-why">Why OOP Groups Related Rules with Responsible Objects</a>

<details>
<summary>Click for details</summary>

When data and the rules operating on that data are scattered across a system, changing one business rule can affect many unrelated places.

OOP tries to place related state and behavior behind the appropriate responsibility boundary so that change is easier to contain.

If withdrawal rules are duplicated in a screen, a service, and a scheduled job, changing an overdraft policy requires edits in several places. Giving the account responsibility for approving withdrawals lets callers reuse one behavioral contract instead of recreating the rules.

**Follow a change all the way to its outcome.** Initially an account with 100 may withdraw 90 because a zero balance is allowed. The new rule requires a reserve of at least 20: the same withdrawal must now be refused. If the UI applies the new condition while the scheduled job retains the old check, identical requests can receive contradictory answers. This is a failure of **scattered decision ownership**, not difficult arithmetic.

With Account responsible for approval, UI, jobs, and Checkout ask the same `withdraw` operation. The account evaluates whether `100 - 90 >= 20`; the answer is false, so it returns a documented refusal and leaves the balance at 100. One behavior owns the policy change, while the callers still handle the result. This **localizes an expected change**; it does not claim OOP prevents all defects. **Check:** when a fourth caller arrives, which component should decide whether withdrawing 90 is allowed?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-solution">OOP Concept Map: Objects, Principles, and Relationships</a>

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

**Read the map as different levels of reasoning, not a compulsory list of pillars.** The **object model** asks which account A is, what state it holds, and which requests it can handle. **Design boundaries** ask who is allowed to modify that state, which validity rule must hold, and what information callers should or should not see. **Collaboration and relationships** explain why Checkout asks Account for authorization and then asks Notifier to deliver a result. **Behavioral variation** explains how a wallet and a bank transfer can fulfill one payment promise in different ways. The levels interact, but they are not interchangeable kinds of objects.

For the same checkout, Account A has an identity; `withdraw(30)` is behavior; `balance >= 0` is an invariant; `Checkout → Account` is a request relationship; and WalletPayment/BankPayment are possible behavioral variants. A **class** is a description/creation mechanism in class-based languages, not the same kind of concept as an object's decision. Later lessons examine each level more closely. **Check:** should you classify polymorphism as a kind of account balance or a way behavior varies?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-class-vs-object">Object vs. Class — A Working Object and Its Blueprint</a>

<details>
<summary>Click for details</summary>

An **object** is a particular participant at runtime. Account A has its own identity, balance of 100, and ability to receive a withdrawal request. A **class**, in a *class-based* language, describes the shared shape and possible behavior of objects of that kind. Think of one Account blueprint and **two actual accounts A and B** made according to it: both can begin with 100 without becoming the same account.

Trace one request: send `withdraw(30)` to **A** → A checks availability → **A becomes 70** → **B remains 100**. The Account class description is not a third account whose balance changed. By contrast, if two names refer to **the same object A**, withdrawing through one name makes the other name observe A's new balance. Thus **two objects from one class** differs from **two references to one object**.

Not every OOP language requires classes; prototype-based systems provide other object models. This lesson owns the conceptual distinction **description versus live participant**, not Java constructors, memory references, or instance-creation syntax. **Check:** does constructing two accounts from one class imply that the resulting accounts share an identity?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-boundary">OOP Thinking and Java Class/Interface Mechanisms</a>

<details>
<summary>Click for details</summary>

This module owns only the **language-neutral OOP paradigm**.

Keywords, access modifiers, class/interface mechanics, and Java-specific dispatch belong to `programming/language/java/core/oop` and related Java Core modules.

Java constructs such as `class`, `interface`, and `private` can implement object models but do not define OOP by themselves. Start by reasoning about responsibility, contracts, consistency, and collaboration; Java's inheritance syntax, overriding rules, and dispatch mechanics belong to Java Core.

**One decision, two levels of questions.** At the paradigm level we can say: Account receives `withdraw(30)`, checks its rule, returns a result, and preserves a valid balance; Checkout never reaches in and updates that balance itself. None of this requires Java. At the language level we then ask how to declare a class or interface, select access modifiers, or dispatch a method call. Those are implementation questions for Java Core.

Putting `private` on a balance field does not automatically make the design correct: a publicly callable `setBalance(-20)` could still violate the rule. Likewise, declaring a Java `interface` does not specify which failures a payment method must report; its behavioral promises need to be understood first. The method names used here are **conceptual notation**, not runnable Java snippets. **Check:** are “who should decide whether to withdraw?” and “which Java keyword represents it?” the same design question?

</details>

- [Back to top](#back-to-top)

---

## <a id="oop-learning-path">Starting Knowledge and a Path from Objects to Design</a>

<details>
<summary>Click for details</summary>

The starting point is basic data, variables, and actions, not mastery of Java classes. Follow an account from **identity and state**, through **encapsulation and invariants**, to **responsibilities and contracts**; then study **composition, subtyping, and polymorphism**, before evaluating design trade-offs.

**Starting point:** you only need to follow variables, simple data, an if-condition, and a function/procedure call. You do not need prior Java `class` or `interface` syntax. **Destination:** given a small checkout request, explain which object owns each rule, how success and failure flow through collaborators, and what must change when a new payment method appears.

First learn **identity and object versus class** by drawing accounts A and B at 100 and withdrawing from A only. Next learn **encapsulation and invariants**: from 100, withdrawal 30 succeeds and leaves 70, while withdrawal 120 is rejected and leaves 100. Once you can trace *who guarantees valid state*, study **responsibilities and contracts**, spelling out the promise before arranging collaborators. In **collaboration**, Checkout receives an outcome and Notifier sends a success receipt only after confirmation; a replaceable FeePolicy contributes its own decision. In **polymorphism and substitution**, wallet and bank transfer return results governed by the same contract. Finally, inspect **trade-offs**: when is another layer helpful, when is a simple function enough, and what does OOP not guarantee?

**Readiness checks:** can you distinguish equal values from identical objects, trace a rejected request without changing state, assign Account/Checkout/Notifier their own jobs, and explain how to introduce BankPayment without a caller type-switch? These abilities form the foundation for studying concrete Java mechanics later.

</details>

- [Back to top](#back-to-top)
