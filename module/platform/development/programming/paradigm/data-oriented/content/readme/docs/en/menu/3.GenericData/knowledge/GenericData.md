<a id="back-to-top"></a>

# Generic and Inspectable Data Representation

## Menu
- [Generic Data Structures: Maps, Lists, and Values](#generic-concept)
- [Representing a Small Domain as Nested Data](#generic-domain-example)
- [Reusable Operations on Visible Data Shapes](#generic-operations)
- [Missing Keys, Invalid Shapes, and Ambiguous Values](#generic-failure-modes)
- [Flexibility, Static Contracts, and the Need for Schemas](#generic-contracts)

## <a id="generic-concept">Generic Data Structures: Maps, Lists, and Values</a>

<details>
<summary>Click for details</summary>

A generic structure stores ordinary values without requiring a new behavior-bearing class for every shape. A map associates keys with values, and a list holds ordered elements; nesting them can represent an order with several lines. This gives common utilities a predictable way to inspect data. Generic does not mean unlimited flexibility: shape and field meanings still form a contract between producers and consumers, whether or not the programming language checks it statically.

</details>

- [Back to top](#back-to-top)

---

## <a id="generic-domain-example">Representing a Small Domain as Nested Data</a>

<details>
<summary>Click for details</summary>

Represent order A as a map with `id`, `lines`, and `status`. Each line is a map with `sku`, `qty`, and `price`, matching the actual data below. From two lines of 2 times 30 and 1 times 40, the subtotal is 100. A report can read the id and calculate a subtotal, while a validator checks every quantity. Nested data is easy to examine, but field names, monetary units, and missing values must have explicit meanings.

~~~json
{ "id":"A", "lines":[{"sku":"P","qty":2,"price":30},{"sku":"Q","qty":1,"price":40}], "status":"pending" }
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="generic-operations">Reusable Operations on Visible Data Shapes</a>

<details>
<summary>Click for details</summary>

A generic calculation function can traverse `lines` and sum `qty × price`, while a different operation produces a customer-facing summary. Both accept the same visible order representation without modifying it. This allows new read-only operations to appear without adding methods to the order value itself. Still, each function should state the fields it requires; an order with an absent `price` must not silently become a plausible but wrong total.

</details>

- [Back to top](#back-to-top)

---

## <a id="generic-failure-modes">Missing Keys, Invalid Shapes, and Ambiguous Values</a>

<details>
<summary>Click for details</summary>

With flexible maps, misspelling `qty` as `quantitty` can return missing rather than a type error. Treating a string `"2"` as numeric quantity may also produce surprising behavior if coercion is implicit. Check required keys, types, allowed ranges, and nested lists before calculating financial values. A safe calculation rejects invalid input with a meaningful field path, such as `lines[1].qty`, rather than filling every missing number with zero.

</details>

- [Back to top](#back-to-top)

---

## <a id="generic-contracts">Flexibility, Static Contracts, and the Need for Schemas</a>

<details>
<summary>Click for details</summary>

Generic data trades concise representation for weaker static guarantees in many implementations. You can recover confidence through documented shapes, tests, typed boundary objects, or schemas. For an incoming order, specify `lines` as a list, `qty` as a positive integer, and `price` as a nonnegative monetary amount. The representation remains a map, while the schema expresses expectations separately. A schema is not automatically necessary for every tiny transient value; prioritize shared and untrusted boundaries.

</details>

- [Back to top](#back-to-top)
