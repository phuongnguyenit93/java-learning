# this and super

Inside instance code, Java needs a way to refer to **the current object** and to **superclass construction/member context**. `this` and `super` express those roles.

The practical reason they matter is constructor and member navigation: one object may need to reuse another constructor in the same class, while subclass construction must also initialize the superclass part of that same object.

## <a id="this-reference">this</a>

`this` is the reference to the current object in instance context.

Common uses include:

- distinguishing fields from same-named parameters;
- passing the current object elsewhere;
- invoking another constructor through `this(...)`;
- returning the current instance in fluent APIs when appropriate.

```java
class BankAccount {
    private final String id;
    private int balance;

    BankAccount(String id) {
        this(id, 0);
    }

    BankAccount(String id, int balance) {
        this.id = id;
        this.balance = balance;
    }
}
```

Here `this(id, 0)` reuses another constructor in the **same** class, while `this.id = id` refers to the current object's field.

Static context has no `this` because there is no implicit current instance.

## <a id="super-access">super</a>

`super` accesses superclass constructors/members according to Java's lookup rules.

```java
class SavingsAccount extends BankAccount {
    private final int interestRate;

    SavingsAccount(String id, int balance, int interestRate) {
        super(id, balance);
        this.interestRate = interestRate;
    }
}
```

`super(id, balance)` initializes the `BankAccount` portion before the `SavingsAccount` constructor body establishes subclass state.

`super` is not a second object nested inside the subclass object. It changes how the source refers to superclass context on the same object.

## <a id="constructor-chaining-order">this()/super() Chaining</a>

Every constructor chain eventually reaches a superclass constructor.

```text
this(...)
→ another constructor in the same class
→ eventually super(...)
```

Constructor invocation follows special first-step rules in Java construction.

For the example above, the chain is:

```text
new SavingsAccount(...)
→ SavingsAccount(...)
→ super(id, balance)
→ BankAccount(id, balance)
→ Object()
```

Understanding this chain prepares us for initialization order later. First, the next chapter asks who may access each member.
