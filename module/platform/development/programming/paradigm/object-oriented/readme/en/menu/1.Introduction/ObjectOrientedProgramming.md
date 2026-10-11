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

Object-Oriented Programming is a paradigm that organizes programs around **objects with identity, state, behavior, and responsibility**, with objects collaborating through clear contracts.

OOP does not mean simply splitting code into many classes. A class is only one mechanism that some languages use to build an object model.

An **object** is more than a data bundle: it has an identity, a responsibility in the system, and behavior others may request. Two accounts with equal balances remain distinct objects representing different accounts and histories. State may change, but immutable objects are equally compatible with OOP.

**Start with a request, not a class diagram.** Account A and Account B each have 100 units. Checkout requests `A.withdraw(30)`; A decides whether the request is valid, changes **A: 100 → 70**, and reports success. **B stays 100**. Identity answers *which account received the request*; state answers *what information that account currently holds*; behavior answers *what the account can do in response*; and responsibility answers *who is entitled to make the decision*. Collaboration means that one object requests work from another under an agreed behavioral promise.

If A instead receives `withdraw(120)` while holding 100, the non-overdraft rule rejects the request, leaving A at 100. These are **conceptual observations**, not a claim that every OOP application resembles a bank or that all objects must mutate. A fee policy can be an immutable object and still have meaningful behavior. **Check your understanding:** why do A and B remain separate objects when their balances happen to match?

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
