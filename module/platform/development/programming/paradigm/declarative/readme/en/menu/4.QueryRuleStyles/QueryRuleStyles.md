<a id="back-to-top"></a>

# Data Queries and Other Declarative Description Styles

## Menu
- [Queries as Specifications of Desired Result Rows](#declarative-query-result)
- [Filtering Conditions, Selection, and Relations](#declarative-query-filter)
- [Result Ordering, Duplicates, and Additional Constraints](#declarative-query-order)
- [Declarative Policies and Configuration Styles](#declarative-policy-config)
- [Evaluator Strategies for Executing a Query](#declarative-query-strategy)
- [Limits of Treating Every DSL or Conditional as Declarative](#declarative-dsl-limit)

## <a id="declarative-query-result">Queries as Specifications of Desired Result Rows</a>

<details>
<summary>Click for details</summary>

A query states **which rows should be returned**. “Positive transactions belonging to account A” specifies the desired result; scanning data or using an index is the evaluator's implementation decision.

SQL query results can preserve **duplicate rows** rather than behaving like a mathematical set of unique values. Two matching transactions may both appear; specifying a selection is not the same as asking to update stored data.

For example, suppose two qualifying transactions both have `amount = 50`. Projecting only the amount with default SQL `SELECT ALL` yields rows `[50, 50]`; `SELECT DISTINCT` yields `[50]`. Duplicates and ordering are separate concerns: neither result should be assumed sorted unless an `ORDER BY` criterion is stated.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-query-filter">Filtering Conditions, Selection, and Relations</a>

<details>
<summary>Click for details</summary>

Filtering evaluates properties such as `amount > 0` and may combine them with relationships such as transactions belonging to a particular account. The desired result contains rows satisfying the applicable conditions.

Real SQL has dedicated handling for missing and `NULL` values, so a predicate need not behave like an everyday two-valued check. This chapter teaches constraint meaning; SQL operators and full three-valued semantics have a separate owner.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-query-order">Result Ordering, Duplicates, and Additional Constraints</a>

<details>
<summary>Click for details</summary>

If a query only requests positive receipts, matching rows may arrive in different orders across executions. A stable display sorted by date needs an explicit ordering constraint, plus a tie-breaker if dates can match.

Duplicates are separate from ordering: sorting does not remove repeated rows, and requesting distinct values requires an additional condition. Correct membership but surprising order or multiplicity often indicates an incomplete specification.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-policy-config">Declarative Policies and Configuration Styles</a>

<details>
<summary>Click for details</summary>

A policy might say “only the account owner may see the balance” rather than repeat an authorization branch at every call site. Configuration may describe a desired state for an engine to reconcile.

Declarative behavior depends on **the description's semantics and its evaluator**, not on whether it is stored as YAML or JSON. A configuration containing ordered commands can still be imperative. Examine what the document actually specifies.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-query-strategy">Evaluator Strategies for Executing a Query</a>

<details>
<summary>Click for details</summary>

An engine can interpret a query by scanning transactions or using an account index before filtering. If both plans satisfy the same semantics, they must produce equivalent required results even though their costs differ.

An optimizer is not guaranteed to choose the fastest plan in every environment; statistics, data distribution, and resource constraints matter. This distinction explains unexpected performance without turning the paradigm lesson into SQL tuning.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-dsl-limit">Limits of Treating Every DSL or Conditional as Declarative</a>

<details>
<summary>Click for details</summary>

A domain-specific language can be declarative, but being a DSL does not make it so. “Open file, read line, write file” remains procedural instruction even when stored in a configuration format.

Likewise, an isolated conditional expression does not turn an entire imperative program into a declarative system. Judge **whether the description states desired properties or execution steps** and what responsibilities it delegates to an evaluator.

</details>

- [Back to top](#back-to-top)
