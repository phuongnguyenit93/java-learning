<a id="back-to-top"></a>

# Data-Oriented Decisions and Boundaries

## Menu
- [Choosing a Data-First or Object-Centric Approach](#dop-choose)
- [Schema Costs, Visibility, and Loss of Encapsulation](#dop-costs)
- [Shared Ideas and Differences from Functional Programming](#dop-functional-relation)
- [Programming Model versus Memory-Layout Data-Oriented Design](#dop-versus-dod)
- [End-to-End Decision and Handoff to Specialized Modules](#dop-handoff)

## <a id="dop-choose">Choosing a Data-First or Object-Centric Approach</a>

<details>
<summary>Click for details</summary>

Use a data-first approach when the same changing shape must be inspected, validated, transformed, and shared by many independent operations. Our order map serves pricing, reporting, and acceptance checks without binding consumers to its owner's methods. Prefer object-centric responsibility when rich invariants and controlled behavior belong together and exposing every field would increase coupling. A system can mix both: typed domain boundaries outside, generic data transformations inside a well-defined pipeline.

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-costs">Schema Costs, Visibility, and Loss of Encapsulation</a>

<details>
<summary>Click for details</summary>

Generic structures can make changes easy locally but expensive across many consumers. Renaming lines to items may break every operation that reads the old key; losing encapsulation can move invariant checks into multiple places. Independent schemas and boundary validation reduce risk but require maintenance and migration effort. Immutable snapshots may also increase allocation pressure unless an implementation shares structure. Evaluate consumer count, contract stability, security, and debugging costs rather than treating data visibility as always free.

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-functional-relation">Shared Ideas and Differences from Functional Programming</a>

<details>
<summary>Click for details</summary>

Functional programming emphasizes predictable transformations and composition; Sharvit-style DOP adds a specific stance on representation: separate code from generic immutable data with independent schemas. A pure discount(order) can be used in both styles. But a function that reads a live database is effectful even when its input is a generic map, and a functional design need not use generic maps at all. Keep these different choices separate so one does not accidentally claim to guarantee the other.

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-versus-dod">Programming Model versus Memory-Layout Data-Oriented Design</a>

<details>
<summary>Click for details</summary>

Data-Oriented Programming as taught here is about how application information and operations are modeled. Performance-oriented Data-Oriented Design (DOD) instead asks how memory layout, cache locality, batching, and hardware execution affect throughput. A list of immutable generic order maps is not automatically cache-efficient; an array-of-structures versus structure-of-arrays choice belongs to another problem. Similar names should not make a team promise speedups merely because code now separates data and functions.

### References

- [Data-Oriented Design - Richard Fabian](https://www.dataorienteddesign.com/dodbook/): hardware/performance-focused design, not this DOP curriculum.

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-handoff">End-to-End Decision and Handoff to Specialized Modules</a>

<details>
<summary>Click for details</summary>

Return to order A: receive id and lines, validate quantities and prices, calculate subtotal 100, derive discounted value 90, then coordinate saving the new version. A reviewer can inspect every input and derived snapshot, explain schema decisions, and decide whether typed variants would better protect illegal states. This completes the programming-model decision, not Java syntax or infrastructure. Learn records/sealed/patterns in Java language modules, persistence in data/integration, and domain invariants in software design where their deeper rules belong.

~~~text
receive A -> validate -> subtotal 100 -> discounted 90 -> version-check/save
~~~

</details>

- [Back to top](#back-to-top)
