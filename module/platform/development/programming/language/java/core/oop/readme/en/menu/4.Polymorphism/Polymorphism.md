# Polymorphism

Inheritance/subtyping makes it possible to view a concrete object through a supertype. Polymorphism turns that possibility into a design tool: callers use one contract while runtime behavior may vary by implementation.

## <a id="subtype-polymorphism">Subtype Polymorphism</a>

### WHAT — one contract, multiple behaviors

Subtype polymorphism lets code depend on a supertype while working with many concrete implementations.

```java
PaymentMethod payment = new CardPayment();
payment.pay(1000);
```

`CheckoutService` only needs the `PaymentMethod` contract; it does not necessarily need to know whether the runtime object is card, wallet, or bank transfer.

### JAVA MODEL — polymorphism does not require class inheritance

`PaymentMethod` may be an `interface`:

```java
interface PaymentMethod {
    void pay(int amount);
}

class CardPayment implements PaymentMethod {
    @Override
    public void pay(int amount) {
        System.out.println("card: " + amount);
    }
}

PaymentMethod payment = new CardPayment();
payment.pay(1000);
```

There is no `CardPayment extends PaymentMethod`, yet the full polymorphic chain still exists:

```text
CardPayment implements PaymentMethod
→ CardPayment is a subtype of PaymentMethod
→ a PaymentMethod variable can reference CardPayment
→ instance-method calls still dispatch from the runtime receiver
```

This distinction matters: **subtype polymorphism is broader than class inheritance**. Class inheritance is one way to create a subtype; implementing an interface is another. Detailed interface rules belong to `abstract-interface`.

### MENTAL MODEL — static type vs runtime type

With:

```java
PaymentMethod payment = new CardPayment();
```

keep two layers separate:

```text
PaymentMethod payment
↑ static / declared type

new CardPayment()
↑ runtime type / actual object
```

The static type tells the compiler **which members are legal through the expression `payment`**. The runtime type matters when a legal instance-method call has multiple overridden implementations.

For example:

```java
Animal animal = new Dog();

animal.sound(); // legal if Animal declares sound()
animal.bark();  // compile error if Animal does not declare bark()
```

Even though the runtime object is a `Dog`, the compiler does not allow `bark()` through an `Animal` reference merely because the runtime class has that method.

### WHY — remove type-specific branching from callers

The design becomes easier to extend when a new implementation can be added without adding a branch to every existing caller:

```text
Without polymorphism
→ if card ... else if wallet ... else if bank ...

With polymorphism
→ payment.pay(amount)
```

This does not mean every `if` is bad. The benefit appears when a caller is branching on object type only to choose behavior that the object itself could provide.

### RELATION — polymorphism needs a contract and substitutability

Runtime dispatch is useful only if the implementations actually honor a common contract. If every subtype has incompatible preconditions or semantics, callers still need special cases and the polymorphism is mostly cosmetic.

## <a id="dynamic-dispatch">Dynamic Dispatch</a>

### HOW — how does Java choose a method?

For an overridden **instance method**, Java separates two decisions:

```text
Compile time
→ reference type + argument types determine a valid method signature

Runtime
→ receiver runtime class determines the concrete override body
```

Example:

```java
PaymentMethod payment = new CardPayment();
payment.pay(1000);
```

The compiler verifies that `PaymentMethod` exposes a matching `pay(...)`. At runtime, if `CardPayment` overrides that method, the `CardPayment` body is dispatched.

### HOW — calls inside an object still dispatch from the runtime receiver

Dynamic dispatch is not limited to calls made directly by external code. A superclass method that calls another overridable instance method through `this` is still operating on the **same runtime object**:

```java
class PaymentMethod {
    void execute(int amount) {
        pay(amount); // virtual call on this
    }

    void pay(int amount) {
        System.out.println("generic");
    }
}

class CardPayment extends PaymentMethod {
    @Override
    void pay(int amount) {
        System.out.println("card: " + amount);
    }
}

PaymentMethod payment = new CardPayment();
payment.execute(1000);
```

The flow is:

```text
payment.execute(...)
→ PaymentMethod.execute(...) runs
→ execute calls this.pay(...)
→ runtime receiver is still CardPayment
→ CardPayment.pay(...) runs
```

So do not assume that “because execution is currently inside `PaymentMethod`, every internal method call is fixed to `PaymentMethod`”. For overridable instance methods, the runtime receiver still determines the final implementation.

### EVIDENCE — `PolymorphismController#dispatch()`

The module experiment uses:

```java
Speaker first = new Dog();
Speaker second = new Cat();
```

Both variables have declared type `Speaker`, yet `first.speak()` uses `Dog` behavior and `second.speak()` uses `Cat` behavior.

The key observation is **the same declared type with different runtime behavior**.

### BOUNDARY — not every member uses dynamic dispatch

Instance overrides are dynamically dispatched. Static methods and fields are not. The Overloading/Overriding chapter makes that distinction explicit.

## <a id="substitutability">Substitutability</a>

### WHAT — compiling is not enough

The type system may allow `Child` to be assigned to `Parent`, but the design is truly polymorphic only if callers still receive reasonable behavior according to the `Parent` contract.

A subtype should preserve important expectations such as:

- valid inputs are not arbitrarily narrowed;
- postconditions callers rely on still hold;
- important invariants remain valid;
- side effects and error semantics do not violate the contract's assumptions.

This is close to Liskov Substitution Principle reasoning, but at Java Core level the practical question is: **if we swap the implementation without telling the caller, does behavior remain valid?**

### FAILURE CASE — type-compatible but behaviorally incompatible

Suppose the `Account` contract reasonably leads callers to expect that every account supports `withdraw(...)`:

```java
class Account {
    void withdraw(int amount) {
        // normal withdrawal contract
    }
}

class FixedAccount extends Account {
    @Override
    void withdraw(int amount) {
        throw new UnsupportedOperationException();
    }
}
```

Java still permits:

```java
Account account = new FixedAccount();
```

But if every `Account` caller must add a special branch for `FixedAccount`, the subtype no longer preserves the shared behavioral expectation.

```text
assignable in the type system
≠
substitutable under the behavioral contract
```

### EVIDENCE — `SubstitutabilityController#substituteImplementations()`

The experiment passes multiple implementations through one method that accepts `Formatter`:

```text
run(new Upper())
run(new Lower())
```

The caller depends only on the `Formatter` contract; implementations can change without special-casing the `run(...)` flow.

### PRACTICE — a sign of weak substitutability

If code frequently needs:

```java
if (payment instanceof SpecialPayment) {
    // exception path only because this subtype does not fit the shared contract
}
```

reconsider the abstraction or subtype relationship.

### RELATION — the next question

We have now seen runtime dispatch. But Java also has **overloading** selected at compile time, static-method hiding, and field hiding. Without separating those mechanisms, polymorphism is easy to misunderstand. The next chapter does exactly that.
