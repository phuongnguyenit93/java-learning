<a id="back-to-top"></a>

# Logical Facts, Relations, and Inference

## Menu
- [Logic Programming as a Way to Describe Relations](#declarative-logic-model)
- [Logical Facts and Known Relations](#declarative-facts-relations)
- [Inference Rules and Logical Conditions](#declarative-inference-rules)
- [Query Goals and Satisfying Conclusions](#declarative-goal-queries)
- [Relationship Specifications versus Search Algorithms](#declarative-search-boundary)
- [Boundary between Logic Models and Prolog Implementations](#declarative-prolog-boundary)

## <a id="declarative-logic-model">Logic Programming as a Way to Describe Relations</a>

<details>
<summary>Click for details</summary>

Logic programming describes **relations that hold** and rules that can establish additional relations. Rather than writing a traversal that searches for eligible people, we state relationships and ask whether a goal can be established.

It is a family of declarative models, with differing semantics and inference strategies across systems. The relational idea is not identical to one language's syntax, nor does it guarantee every search terminates quickly.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-facts-relations">Logical Facts and Known Relations</a>

<details>
<summary>Click for details</summary>

A logical **fact** is an accepted assertion about a relation: `parent(An, Binh)` states that An is a parent of Binh. It is not a time-based **event** such as a user clicking a button.

Facts can form a network of relationships: adding `parent(Binh, Chi)` extends the known parent relation. A solver may answer questions about that relation without each fact specifying its own search procedure.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-inference-rules">Inference Rules and Logical Conditions</a>

<details>
<summary>Click for details</summary>

An inference rule says a conclusion holds **when its premises hold**. For example, `grandparent(x,z)` may hold when some `y` satisfies both `parent(x,y)` and `parent(y,z)`. The rule states a relationship without prescribing an iteration order.

If the required parent facts cannot be established, the rule does not establish the grandparent relation. “Not proved” should not always be confused with “proved false”; that distinction depends on the logic system's semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-goal-queries">Query Goals and Satisfying Conclusions</a>

<details>
<summary>Click for details</summary>

A **goal** is a relation or proposition to check. Given `parent(An,Binh)` and `parent(Binh,Chi)`, a grandparent query can derive An as a matching person from the inference rule.

Some systems answer yes/no; others also find substitutions for variables in a goal. A goal may have multiple solutions, requiring extra criteria if exactly one is desired. This describes relational reasoning rather than Prolog syntax.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-search-boundary">Relationship Specifications versus Search Algorithms</a>

<details>
<summary>Click for details</summary>

The `grandparent` rule specifies **which relationships justify a conclusion**, not which person the engine must inspect first. The solver still uses an inference or search strategy to produce answers.

Strategies can differ in resource use, answer order, and termination, especially with recursive relations. A declarative statement does not imply that every search strategy has equal cost or guaranteed termination.

</details>

- [Back to top](#back-to-top)

---

## <a id="declarative-prolog-boundary">Boundary between Logic Models and Prolog Implementations</a>

<details>
<summary>Click for details</summary>

Prolog is a prominent example of a system with facts, rules, and goals. Its variable unification, clause selection, and search behavior implement the model but do not define all of declarative programming.

Here `parent(x,y)` is conceptual notation, not a complete executable language tutorial. Practical Prolog study requires its own treatment of rule order, search behavior, and nonterminating queries.

</details>

- [Back to top](#back-to-top)
