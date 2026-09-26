# Composition

Inheritance answers “is this object a subtype of that type?”. Composition answers a different question: **which objects does this object collaborate with to fulfill its responsibility?**

## <a id="composition-has-a">Composition</a>

### WHAT

Composition builds behavior by storing references to collaborators and delegating some work to them.

```java
final class Checkout {
    private final Pricing pricing;

    Checkout(Pricing pricing) {
        this.pricing = pricing;
    }

    int total(int base) {
        return pricing.price(base);
    }
}
```

`Checkout` **has-a** `Pricing`. It should not `extend Pricing` because checkout is not a pricing policy.

### TERMINOLOGY — “composition” is used in two common senses

In OOP discussions, **object composition** is often used broadly: build an object by combining collaborators instead of inheriting implementation.

In UML/object modeling, **composition** also has a narrower meaning: a whole–part relationship with strong ownership and a part lifecycle tied closely to the whole.

This chapter uses both contexts, so pay attention to the question being answered:

```text
composition as a design technique
→ combine objects / delegate behavior instead of extending a class

composition as a UML relationship
→ strong ownership + lifecycle semantics
```

The two meanings are related but **not identical**. Merely storing a collaborator for delegation is not enough to prove UML composition.

### WHY — reuse behavior without inheriting implementation

If the goal is to vary pricing strategy, composition lets us inject `Regular`, `Discount`, or another implementation without changing the type hierarchy of `Checkout`.

Variation stays in the collaborator rather than turning the consumer into a complex base/subclass hierarchy.

### EVIDENCE — `CompositionController#strategySwap()`

The experiment creates:

```text
new Checkout(new Regular())
new Checkout(new Discount())
```

`Checkout` does not change; only the `Pricing` collaborator changes. The response shows different behavior while the consumer type remains the same.

## <a id="object-relationships">Object Relationships</a>

These terms describe relationships in an object model; they are **not four separate Java runtime features**.

| Relationship | Typical design meaning | Common Java representation |
| --- | --- | --- |
| Dependency | temporary use for an operation | parameter/local/short-lived reference |
| Association | a longer-lived collaboration | field/reference |
| Aggregation | weak/shared ownership | field/reference + lifecycle convention |
| Composition | strong ownership, part lifecycle tied to whole | field/reference + construction/API rules |

Java sees references; ownership meaning mostly comes from the design contract.

### PITFALL — a `final` reference does not automatically mean composition

For example:

```java
class Team {
    private final Player captain;

    Team(Player captain) {
        this.captain = captain;
    }
}
```

`final` only means the `captain` field cannot be reassigned to a different reference after initialization. It does **not prove** that:

- `Team` is the only owner of `Player`;
- `Player` cannot be shared elsewhere;
- `Player` must die with `Team`;
- the relationship is composition in the modeling sense.

Ownership is a **design semantic**, not something Java infers from one keyword.

### WHY — do not infer UML semantics from syntax alone

Two classes both having fields is not enough to decide aggregation vs composition. Look at who creates the part, who retains it, whether it can be shared, and how lifecycle is managed.

## <a id="association-dependency">Dependency and Association</a>

A dependency may be needed for just one operation:

```java
Receipt checkout(PaymentGateway gateway) {
    return gateway.charge(...);
}
```

If a collaborator is a stable part of object state, a field association may be more appropriate:

```java
class CheckoutService {
    private final PaymentGateway gateway;
}
```

A collaborator stored in a field is not necessarily strongly owned. Two services may hold the same mutable object. In that case aliasing, shared mutation, and lifecycle contracts matter more than the mere presence of a field/reference.

### TRADE-OFF

Do not force every parameter to be labeled “dependency” and every field “association” when the distinction adds no reasoning value. The goal is to understand coupling and lifetime, not classify every reference for its own sake.

## <a id="ownership-lifecycle">Ownership and Lifecycle</a>

### Aggregation

The part can exist independently or be shared across owners. A `Team` may reference `Player` objects, while a `Player` does not necessarily cease to exist if the `Team` disappears.

### Composition

The whole conceptually owns the part more strongly. An `Order` may create and own its `OrderLine` objects; the line lifecycle is commonly tied to the order.

### HOW — Java does not enforce ownership semantics

The garbage collector only understands reachability. It does not know “aggregation” or “composition”. Ownership must be expressed through constructors/factories, mutability, APIs, and whether references are exposed or shared.

## <a id="composition-vs-inheritance">Composition vs Inheritance</a>

The mechanisms model different kinds of relationships:

| Question | Inheritance | Composition |
| --- | --- | --- |
| Primary relationship | is-a | has-a / collaborates-with |
| Reuse | inherited implementation | delegation |
| Variation | subtype override | replace collaborator |
| Coupling | tight to base class | tight to collaborator contract |
| Runtime swap | usually less natural | natural when collaborator is injected |

### Decision heuristic

If an object **truly must be usable as the supertype**, inheritance may be right. If the goal is only to reuse or swap behavior, consider composition first.

“Favor composition over inheritance” does not mean “never use inheritance”; it warns against using inheritance as a shortcut for code reuse without subtype semantics.

## <a id="delegation">Delegation</a>

Delegation means an object receives a request and forwards part of the work to a collaborator that owns the relevant responsibility.

```java
int total(int base) {
    return pricing.price(base);
}
```

`Checkout` does not need to know the discount formula. It only knows the `Pricing` contract.

### RELATION — delegation leads to abstraction

For composition to stay flexible, the consumer should depend on a **stable contract** rather than implementation details. That is why composition and abstraction often reinforce each other.

### DISTINCTION — composition and delegation are not the same thing

```text
Composition
→ A keeps/has collaborator B as part of its object structure

Delegation
→ A receives a request and hands a specific responsibility to B
```

In the module's running example, `Checkout` may keep a `Pricing` collaborator without making every `Checkout` responsibility belong to `Pricing`. When `Checkout.total(...)` calls `pricing.price(...)`, **the pricing responsibility itself** is delegated to the `Pricing` collaborator.

```text
Checkout
→ owns checkout coordination

Pricing
→ owns pricing behavior

Checkout.total(...)
→ delegates pricing work to Pricing.price(...)
```

### TRADE-OFF

Too many pass-through layers that add no responsibility can become ceremony. Delegation is valuable when the collaborator truly owns distinct behavior or a variation point.
