<a id="back-to-top"></a>

# Functional Programming: Purpose and Mental Model

## Menu
- [Functional Programming: Concept and Scope](#functional-what)
- [Why Shared Mutable State Makes Reasoning Difficult](#functional-why)
- [Imperative Code and the Motivation for Functional Alternatives](#functional-without)
- [Starting Point: Basic Functions, Values, and Programming Styles](#functional-prerequisites)
- [Core Model: Explicit Inputs, Transformations, and Composition](#functional-solution)
- [Practical Use Cases and the Limits of Functional Style](#functional-when)
- [Learning Path: Pure Functions, Values, Composition, and Effects](#functional-learning-journey)

## <a id="functional-what">Functional Programming: Concept and Scope</a>

<details>
<summary>Click for details</summary>

Functional Programming is a paradigm that organizes programs around **functions, data transformations, and composition** rather than making mutation and state-changing command sequences the center of the design.

The goal is not to make everything a function, but to make large portions of logic understandable as:

```text
input
→ transformation
→ output
```

Functional programming organizes computations as functions transforming explicit inputs into results rather than depending on scattered changes to shared state. In checkout, calculating a price is different from charging a card: the former can be a pure calculation; the latter interacts with another system. This distinction is about program organization, not a ban on objects or imperative code.

</details>

- [Back to top](#back-to-top)

---

## <a id="functional-why">Why Shared Mutable State Makes Reasoning Difficult</a>

<details>
<summary>Click for details</summary>

When logic depends heavily on shared mutable state, the result of one piece of code can be affected by changes occurring elsewhere and at different times.

Functional Programming reduces that dependency by favoring functions with explicit inputs and outputs, limiting side effects, and encouraging immutable data.

A discount function that secretly reads a global promotion can return different amounts for identical visible inputs. Reproducing an old result then requires reconstructing external state. Passing the promotion rate explicitly reveals the actual dependency, making tests repeatable. Shared state is not forbidden; its access needs a clear boundary so a reader knows what can change a calculation.

</details>

- [Back to top](#back-to-top)

---

## <a id="functional-without">Imperative Code and the Motivation for Functional Alternatives</a>

<details>
<summary>Click for details</summary>

Imperative programming remains completely valid. Programs can mutate variables, update objects, and control flow through statements.

The problem appears when mutation and side effects spread so widely that reasoning, testing, or concurrent execution becomes difficult.

An imperative loop adding local order totals can be entirely understandable and efficient. The problem arises when another component can alter the same order list during processing. A functional approach computes from a known snapshot and returns a new value without changing that source. Prefer the form that makes ownership and effects obvious; a functional wrapper is not automatically better than a straightforward loop.

</details>

- [Back to top](#back-to-top)

---

## <a id="functional-prerequisites">Starting Point: Basic Functions, Values, and Programming Styles</a>

<details>
<summary>Click for details</summary>

A value is data such as price 100; a function maps supplied inputs to a result; a collection groups multiple items. Basic conditions and function calls suffice to begin: no Java lambda or Stream syntax is required. Declarative thinking focuses on the result desired; this module develops reasoning about transformations, purity, and composition. A side effect is an observable action beyond returning a result, such as writing a receipt.

</details>

- [Back to top](#back-to-top)

---

## <a id="functional-solution">Core Model: Explicit Inputs, Transformations, and Composition</a>

<details>
<summary>Click for details</summary>

Mental model:

```text
explicit input
    ↓
pure or controlled transformation
    ↓
explicit output
    ↓
compose transformations
```

Important ideas include pure functions, immutability, first-class and higher-order functions, composition, and referential transparency.

Consider subtotal, then discount, then tax, then receipt. Each stage takes the previous output rather than guessing a global variable. For 100 discounted by 10%, the subtotal is 90; a 5% tax on 90 gives amount due 94.5. This chain makes intermediate values testable. Actual payment remains a separate effectful operation.

~~~text
100 -> discount(10%) -> 90 -> tax(5%) -> 94.5
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="functional-when">Practical Use Cases and the Limits of Functional Style</a>

<details>
<summary>Click for details</summary>

The paradigm is especially useful for data transformations, pipelines, business rules expressible as small functions, and logic that should be easy to test.

It does not eliminate side effects. I/O, databases, networks, and stateful interaction still exist; the goal is to **isolate and control them**.

Clear pricing rules, reports, input normalization, and validation calculations often fit small predictable transformations. A database write or payment must still be coordinated with real-world state, so pretending those steps are pure obscures correctness. A nested chain of anonymous callbacks may also be harder to read than one simple loop. Choose a style after comparing traceability, side effects, and complexity.

</details>

- [Back to top](#back-to-top)

---

## <a id="functional-learning-journey">Learning Path: Pure Functions, Values, Composition, and Effects</a>

<details>
<summary>Click for details</summary>

First establish purity and why hidden inputs break predictability. Next distinguish immutable values from state changing over time. Then pass functions as values, build higher-order transformations, and trace composition through an order example. Finally control I/O and failures at explicit boundaries and compare functional choices with imperative, object-oriented, data-oriented, and reactive perspectives without re-teaching their APIs.

</details>

- [Back to top](#back-to-top)
