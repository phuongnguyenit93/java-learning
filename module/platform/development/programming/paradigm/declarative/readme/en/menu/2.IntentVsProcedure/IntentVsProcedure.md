<a id="back-to-top"></a>

# Specifying Intent and Understanding Execution Strategy

## Menu
- [Specifying Desired Results and Required Properties](#declarative-result-spec)
- [Conditions, Predicates, and Constraints](#declarative-predicates)
- [Completeness and Ambiguity in a Specification](#declarative-spec-completeness)
- [Evaluators: Turning Descriptions into Computation](#declarative-evaluator-role)
- [Semantic Results versus Concrete Execution Plans](#declarative-semantics-plan)
- [Strategy, Observable Order, and Performance Trade-offs](#declarative-execution-tradeoffs)

## <a id="declarative-result-spec">Specifying Desired Results and Required Properties</a>

<details>
<summary>Click for details</summary>

A result specification describes acceptable outputs through **properties that must hold**. For transactions, “amount greater than zero” identifies qualifying records without mentioning indexes, loops, or accumulator variables.

“The three largest receipts” requires additional decisions about comparison, ties, and the requested count. Leaving meaningful constraints unstated may produce an outcome that follows the written specification but surprises its author.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-predicates">Conditions, Predicates, and Constraints</a>

<details>
<summary>Click for details</summary>

A **predicate** is a condition that can hold or fail for an input, such as `amount > 0`. A **constraint** narrows which outcomes are acceptable, such as returning transactions only for the requesting account.

A description might require both a positive amount **and** the correct account owner. Omitting access restrictions can produce numerically correct results that violate the intended scope, a specification defect rather than merely an algorithmic bug.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-spec-completeness">Completeness and Ambiguity in a Specification</a>

<details>
<summary>Click for details</summary>

A specification is sufficiently precise when acceptable and excluded outcomes are clear. “Recent transactions” is ambiguous: recent by creation time, posting time, or latest edit? How many records should it return?

Multiple satisfying outcomes are not inherently wrong; scheduling may permit several valid meetings. If an application needs exactly one choice, the specification must express a tie-breaking policy rather than relying on incidental evaluator order.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-evaluator-role">Evaluators: Turning Descriptions into Computation</a>

<details>
<summary>Click for details</summary>

An evaluator turns a description into actual work: access input data, check constraints, and construct an outcome. For a database query it might scan rows or use an index if one is available.

Execution costs do not disappear. The evaluator needs data, resources, and semantic rules; it does not repair a wrong specification by guessing the author's intent. Optimization algorithms are an implementation-specific topic.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-semantics-plan">Semantic Results versus Concrete Execution Plans</a>

<details>
<summary>Click for details</summary>

The **semantics** of a query define acceptable results; its **execution plan** describes the evaluator's chosen steps. Two plans can traverse data differently yet return equivalent qualifying rows when they respect all declared constraints.

Do not treat the incidental row order of one run as part of the result contract. If business behavior depends on order, express an ordering requirement so every valid execution strategy must honor it.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-execution-tradeoffs">Strategy, Observable Order, and Performance Trade-offs</a>

<details>
<summary>Click for details</summary>

Separating intent from a plan lets an engine adapt its strategy as data volume or available resources change. The trade-off is that execution cost can become less obvious from the specification alone.

Observable effects matter too. Reordering independent pure calculations may be harmless, but moving an external update or log action can change observed behavior. Declarative syntax is not a blanket license to disregard such effects.

</details>

- [Back to top](#back-to-top)
