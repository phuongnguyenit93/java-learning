<a id="back-to-top"></a>

# Data-Oriented Programming: Purpose and Core Model

## Menu
- [Data-Oriented Programming: Meaning and Module Scope](#dop-concept)
- [The Cost of Coupling Data to Its Operations](#dop-motivation)
- [Starting Point: Values, Collections, and Existing Paradigms](#dop-prerequisites)
- [Objects, Functions, and Data as Different Design Views](#dop-context)
- [Sharvit's Four Principles and Their Relationships](#dop-sharvit-model)
- [Sharvit DOP, Java DOP, and Performance-Oriented DOD](#dop-terminology)
- [Learning Sequence from Data Representation to Decisions](#dop-learning-path)

## <a id="dop-concept">Data-Oriented Programming: Meaning and Module Scope</a>

<details>
<summary>Click for details</summary>

Data-oriented programming (DOP) treats application data as inspectable values that can be represented and transformed independently of the operations working on them. The primary model here is Yehonathan Sharvit's language-neutral approach. Imagine an order described by its id, lines, and status: separate functions can calculate totals, display summaries, or validate the same value. DOP is a design choice, not a requirement that all programs use untyped maps or discard object boundaries.

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-motivation">The Cost of Coupling Data to Its Operations</a>

<details>
<summary>Click for details</summary>

Suppose every order value is locked behind an object with its own display, discount, export, and storage methods. When a reporting team needs the same data differently, it may depend on that object's methods or special adapters. Separating data from operations can make reuse and inspection easier. The cost is that rules and invariants once enforced inside an object may need explicit validation elsewhere. DOP is useful when data crosses many operations, not because encapsulation is always wrong.

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-prerequisites">Starting Point: Values, Collections, and Existing Paradigms</a>

<details>
<summary>Click for details</summary>

Begin with ordinary values, lists, maps, functions, and simple object responsibilities. A map associates keys with values; a list preserves a sequence of values. You need not know schema libraries, records, or SQL. The object-oriented module explains objects owning behavior; the functional module introduces transformations and immutability. Here we reuse those terms briefly but teach the DOP combination from first principles. Later sections show where generic data requires validation and coordination.

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-context">Objects, Functions, and Data as Different Design Views</a>

<details>
<summary>Click for details</summary>

The same order can be viewed in three ways: an object that owns methods and protects invariants; a value passed through pure calculation functions; or visible data consumed by multiple independent operations. These views overlap rather than forming exclusive language categories. DOP's particular emphasis is explicit representation and operations separated from it. A data-first model does not automatically make all operations pure, nor does an OOP class automatically prevent sharing a snapshot.

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-sharvit-model">Sharvit's Four Principles and Their Relationships</a>

<details>
<summary>Click for details</summary>

Sharvit's four principles work together: **separate code from data**, **represent data with generic structures** such as maps and lists, **treat values as immutable**, and **describe schema separately from representation**. For our order, a plain map carries fields, calculateTotal accepts it, an update produces a new map, and a validator checks its shape at an input boundary. The principles reinforce each other: flexible generic structures are easier to reuse but must be guarded by explicit contracts where correctness matters.

~~~text
order value -> schema check -> independent function -> new order value
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-terminology">Sharvit DOP, Java DOP, and Performance-Oriented DOD</a>

<details>
<summary>Click for details</summary>

The name DOP is used for related but distinct approaches. Sharvit's model emphasizes generic immutable structures, operations apart from data, and separately defined schemas. **Inside Java/Project Amber DOP v1.1** favors immutable transparent, precise typed data models, complete data variants, illegal states made unrepresentable, and separate operations. Neither is performance-oriented **data-oriented design (DOD)** about memory layout, caches, SIMD, or game-style ECS. We compare concepts, leaving Java syntax and memory engineering to their owners.

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-learning-path">Learning Sequence from Data Representation to Decisions</a>

<details>
<summary>Click for details</summary>

Start by separating a value from functions that use it, then represent a small order with generic nested maps and lists. Next derive immutable versions and learn when separately maintained schemas should validate inputs and outputs. Follow the order through receive, validate, transform, and coordinate side effects, and contrast that design with typed Java/Amber data modeling. Finish by deciding whether visibility and reuse outweigh lost encapsulation, validation work, and change costs.

</details>

- [Back to top](#back-to-top)
