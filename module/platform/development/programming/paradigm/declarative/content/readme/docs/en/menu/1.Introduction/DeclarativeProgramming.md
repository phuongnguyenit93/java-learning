<a id="back-to-top"></a>

# Declarative Programming: Describing Results and Constraints

## Menu
- [Declarative Programming: Meaning and Scope](#declarative-what)
- [Motivation for Separating Intent from Execution Steps](#declarative-why)
- [Intent Descriptions versus Ordered Instructions](#declarative-before)
- [Specifications, Evaluators, and Results: A First Model](#declarative-solution)
- [Connections to Logic, Queries, and Functional Styles](#declarative-relations)
- [Starting Knowledge and the Learning Path through Declarative Styles](#declarative-learning-path)

## <a id="declarative-what">Declarative Programming: Meaning and Scope</a>

<details>
<summary>Click for details</summary>

Declarative Programming emphasizes describing the **desired result, constraints, or rules** instead of fully specifying each execution step.

```text
WHAT is desired
→ runtime / engine / implementation decides HOW
```

That choice still respects the specification's meaning and any ordering or effect guarantees it explicitly requires.

A declarative description states **what must be true** rather than enumerating each processing instruction. “Transactions with positive amounts” specifies a desired result, not an algorithm for traversing records. If ordering or a limit of ten records matters, those constraints must also appear in the specification.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-why">Motivation for Separating Intent from Execution Steps</a>

<details>
<summary>Click for details</summary>

In many domains, the author cares more about intent than execution detail. Separating the two can make expressions shorter and allows the implementation to choose or optimize the execution strategy.

A business condition can be implemented using several algorithms. When traversal and filtering logic is duplicated throughout a program, the intended result becomes obscured by mechanics. A specification makes that intent easier to inspect while allowing an evaluator to choose a suitable implementation.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-before">Intent Descriptions versus Ordered Instructions</a>

<details>
<summary>Click for details</summary>

Imperative code can produce the same result by describing each step explicitly.

Declarative style becomes useful when execution details are repetitive, complex, or better handled by an engine or framework.

To find positive transactions, imperative code might create an empty output, visit each record, test the amount, and append qualifying records. A declarative expression states “select records whose amount is greater than zero.” The implementation may still iterate; those steps are simply not prescribed by the author.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-solution">Specifications, Evaluators, and Results: A First Model</a>

<details>
<summary>Click for details</summary>

```text
desired condition / result
        ↓
declaration
        ↓
engine chooses execution strategy
```

The mental model has a **specification**, an **evaluator**, and an **outcome satisfying the stated constraints**. A filter describes qualifying records; an engine executes a plan; matching rows form the result. The wording does not automatically guarantee a particular row order or a single search strategy.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-relations">Connections to Logic, Queries, and Functional Styles</a>

<details>
<summary>Click for details</summary>

Declarative Programming is an umbrella concept. Functional Programming is often highly declarative, but the two are not identical.

Logic programming, query languages, and rule or configuration systems are also commonly declarative.

This module owns only the **mental model and relationships**; concrete DSLs and technologies remain with their canonical owners.

Logic programming states facts and inference rules; data queries state which records or relations are wanted; functional style often describes transformations by composing functions. Their shared emphasis on **intent** does not mean that every query language, function, or configuration file is purely declarative.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-learning-path">Starting Knowledge and the Learning Path through Declarative Styles</a>

<details>
<summary>Click for details</summary>

Start with values, conditions, and basic iteration over data. Follow the path from result specifications and constraints to evaluators, then facts, rules, and queries; examine ordering and duplicates before comparing trade-offs with imperative alternatives. SQL and Prolog syntax belong to their dedicated technology modules.

</details>

- [Back to top](#back-to-top)
