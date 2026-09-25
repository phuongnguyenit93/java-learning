# OOP Mental Model

OOP does not begin with `class`, `extends`, or `interface`. It begins with a design question: **where should state, rules, and responsibilities live so that change does not spread across the whole program?**

Imagine a small checkout flow. At first, one function can read the cart, calculate a price, choose a payment path, and update state. As rules grow, that function accumulates branches, several places mutate the same state, and unrelated code starts knowing internal details. OOP is one way to reorganize those responsibilities around objects with explicit boundaries.

## <a id="oop-object-collaboration">What Is OOP?</a>

### WHAT — what is OOP?

Object-Oriented Programming organizes software around **objects**: each object represents a responsibility, owns some state, and exposes behavior so other objects can collaborate with it.

An object is not merely “a bag of fields”. A useful object answers three questions:

- what **state** does it own;
- what **responsibility** does it have;
- what **behavior** may callers request from it.

For a checkout flow:

```text
ShoppingCart
→ owns items and the rules for changing the cart

PricingPolicy
→ owns a pricing decision

PaymentMethod
→ owns payment behavior

CheckoutService
→ coordinates those objects to complete checkout
```

The key is that rules no longer need to be scattered across every caller. Callers send requests through methods; the responsible object protects its own state and rules.

### WHY — why not keep everything in one function?

Small procedural code is often perfectly readable. Problems appear when many operations read and mutate the same state and all know the same internal rules. Then a small change, such as adding a new discount policy, may require edits in many places.

OOP tries to **localize change**: pricing rules live near the pricing responsibility; state-validity rules live near the object that owns that state. That is why “an object owns behavior” matters more than merely splitting code into many classes.

### RELATION — how do the topics in this module fit together?

The following chapters are not seven isolated definitions. They form one chain of questions:

```text
An object owns state
        ↓
How does it keep that state valid?
Encapsulation
        ↓
How can one type be treated as another compatible type?
Inheritance / Subtyping
        ↓
How can one contract produce different behavior?
Polymorphism
        ↓
How does Java choose methods at compile time vs runtime?
Overloading / Overriding / Dispatch / Hiding
        ↓
What if inheritance creates too much coupling?
Composition / Delegation
        ↓
What should callers know, and what should remain hidden?
Abstraction
```

`Encapsulation`, `inheritance`, `polymorphism`, and `abstraction` are commonly taught as the “four pillars of OOP”. Treat that as a useful mnemonic, not four unrelated keywords. They interact closely, and practical designs often use them together with composition, delegation, and clear responsibility boundaries.

### HOW — what does Java provide?

Java provides classes, objects, access modifiers, inheritance, interfaces, overriding, dynamic dispatch, and reference-based composition. But **language mechanisms do not automatically produce good design**. A class with only `private` fields may still encapsulate poorly; a hierarchy using `extends` may still be an invalid subtype design.

So keep two levels of reasoning separate:

```text
Design concept
→ responsibility, encapsulation, substitutability, abstraction...

Java mechanism
→ private, extends, implements, override, overload, runtime dispatch...
```

### PRACTICE — a review question

When reading a class, do not only ask “what fields and methods does it have?”. Ask: **what state does it own, what rules must it protect, and which callers currently know too much about its internals?**

## <a id="oop-boundaries">Responsibility Boundaries</a>

### WHAT — what is a boundary?

A boundary separates “what this object is responsible for” from “what callers are allowed to know or request”. A good boundary exposes meaningful behavior while keeping irrelevant implementation details inside.

For checkout, a caller should prefer:

```java
checkoutService.checkout(cart);
```

over reading every field from `ShoppingCart`, calculating prices itself, branching on payment type, and then mutating order state directly.

### WHY — why do responsibility boundaries matter?

When related state and rules live behind one boundary, cohesion improves: there is a clear place to find the logic. When many objects know each other's internals, coupling increases: changing one side can force changes across the system.

Good OOP does not mean “more classes are always better”. Splitting one responsibility into layers that add no real boundary only creates navigation cost.

### RELATION — boundaries lead to encapsulation

As soon as we say “this object owns state”, the next question is: **who is allowed to change that state?** That is the bridge to encapsulation. Encapsulation is not merely `private`; it uses a boundary to control valid state transitions.

A useful heuristic:

```text
If a caller must know many fields to implement one rule
→ that responsibility may live behind the wrong boundary.
```

### PRACTICE — three boundary questions

For each class, ask:

1. which state truly belongs to this responsibility;
2. which behavior does a caller need, rather than which raw data it wants;
3. if the internal representation changes, how many callers must change too.

## <a id="oop-vs-procedural">OOP vs Procedural Programming</a>

### WHAT — what is being decomposed differently?

Procedural decomposition usually organizes code around **operations and data flow**. OOP decomposition usually organizes code around **objects and responsibilities**.

| Design question | Procedural | OOP |
| --- | --- | --- |
| Primary unit | Function / procedure | Object / responsibility |
| State | Often passed through operations | Often owned by an object |
| Change tends to localize by | Flow / operation | Responsibility / type |
| Natural fit | Pipelines, linear transforms, algorithms | Stateful domains, collaborators, behavior varying by type |

### WHY — OOP is not universally better

A short data transformation or pure algorithm may be clearer as a function. Forcing every concept into an object hierarchy can add ceremony without adding clarity.

Conversely, when a domain has long-lived state, invariants, multiple implementations, and collaboration that evolves over time, object boundaries can make reasoning easier.

Java supports both styles. Modern Java code often mixes them intentionally: objects own responsibilities while functions or lambdas handle local transformations.

### EVIDENCE — what should you observe while learning this module?

Do not judge OOP by the number of classes. Observe whether a change becomes more localized. Adding a pricing strategy without changing `Checkout` is much stronger evidence than merely proving that Java has an `interface` keyword.

### PRACTICE — the roadmap forward

Keep one question throughout the module:

> “Which problem in the current mental model is the next concept solving?”

The next chapter starts with the first problem: **once an object owns state, how do we prevent callers from breaking it?**
