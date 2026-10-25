<a id="back-to-top"></a>

# Functions as Values and Composed Transformations

## Menu
- [First-Class Functions as Data and Behavior](#functions-as-values)
- [Higher-Order Functions and Reusable Processing](#higher-order-functions)
- [Composition of Input-to-Output Functions](#function-composition)
- [Mapping, Filtering, and Aggregating Collections](#map-filter-reduce)
- [A Complete Transformation from Input to Output](#transformation-walkthrough)
- [Readable Composition and Overly Complex Pipelines](#composition-limits)

## <a id="functions-as-values">First-Class Functions as Data and Behavior</a>

<details>
<summary>Click for details</summary>

A first-class function can be assigned to a name, passed as an argument, or returned like another value. An order system can choose a pricing rule once and use it on several orders. The function is behavior carried as a value, not a command that must execute immediately. Such a value need not be pure: passing a logger around still permits logging effects.

~~~text
regular = (price) => price
vip = (price) => price * 0.9
chosen = choosePolicy(customer)
amount = chosen(100)
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="higher-order-functions">Higher-Order Functions and Reusable Processing</a>

<details>
<summary>Click for details</summary>

A higher-order function takes a function as input or returns a function. Imagine transform(items, pricingRule): it owns the traversal, while the caller supplies the pricing behavior. The same traversal can later apply tax or normalization without rewriting the loop. A closure that captures changing external state remains effectful or context-sensitive, so higher-order abstraction alone does not guarantee predictable results.

~~~text
transform([10,20], double) -> [20,40]
transform([10,20], add5)   -> [15,25]
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="function-composition">Composition of Input-to-Output Functions</a>

<details>
<summary>Click for details</summary>

Function composition means the output of one calculation becomes the input of the next. For an order, discounting 100 by 10% gives 90; taxing that amount by 5% yields 94.5. Composition is safe to reason about only when inputs and results match the intended domain and their order is correct: discount-after-tax need not equal tax-after-discount under rounding rules. It is a conceptual relation, not a Java Stream command.

~~~text
subtotal 100 -> discount -> 90 -> tax -> 94.5
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="map-filter-reduce">Mapping, Filtering, and Aggregating Collections</a>

<details>
<summary>Click for details</summary>

Map changes each selected element, filter keeps elements meeting a condition, and reduce combines elements into an accumulated result. For amounts [20,40,50], first keep amounts at least 30: [40,50]; discount each by 10%: [36,45]; sum with identity 0: 81. This is about meaning, not memorizing method names. The reduction operator and initial value matter, and operations with side effects cannot be freely reordered.

~~~text
filter -> [40,50]; map -> [36,45]; reduce(sum,0) -> 81
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="transformation-walkthrough">A Complete Transformation from Input to Output</a>

<details>
<summary>Click for details</summary>

Take orders A(approved,40), B(pending,20), and C(approved,50). Filter to approved orders A and C, project their amounts [40,50], discount by 10% to [36,45], and sum to 81. Every intermediate value has a specific role and can be tested independently. If the source changes during computation, first obtain a stable snapshot; rounding currency amounts also requires a documented rule, not an assumed floating-point behavior.

~~~text
[A,B,C] -> [A,C] -> [40,50] -> [36,45] -> 81
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="composition-limits">Readable Composition and Overly Complex Pipelines</a>

<details>
<summary>Click for details</summary>

A chain of ten unnamed transformations can conceal more than it explains. When a failure occurs, the reader needs recognizable stage names and access to intermediate results. Splitting validateOrder, calculateDiscount, and buildReceipt is often clearer than deeply nesting anonymous callbacks. Do not mix network requests into a transformation advertised as pure; use explicit effect boundaries. An ordinary loop remains a valid alternative when it communicates intent with less cognitive load.

</details>

- [Back to top](#back-to-top)
