<a id="back-to-top"></a>

# Pure Functions and Referential Transparency

## Menu
- [Pure Functions: Definition and Observable Behavior](#purity-concept)
- [Predictability and the Cost of Hidden Dependencies](#purity-motivation)
- [Explicit Inputs, Results, and External State](#purity-input-output)
- [Referential Transparency and Substitution](#referential-transparency)
- [Purity Violations and Testable Calculations](#purity-boundaries)

## <a id="purity-concept">Pure Functions: Definition and Observable Behavior</a>

<details>
<summary>Click for details</summary>

A pure function yields the same result for the same values and produces no observable side effect. discount(100, 0.10) returns 90 whether it is called once or ten times. Returning a new object is fine; mutating the caller's cart or printing to a log is not pure. The property concerns behavior, not a function name or a particular language syntax.

~~~text
discount(total, rate) = total * (1-rate)
discount(100, 0.10) = 90
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="purity-motivation">Predictability and the Cost of Hidden Dependencies</a>

<details>
<summary>Click for details</summary>

Compare a price function accepting a rate with one that reads currentRate from configuration and currentTime from the clock. The second depends on invisible inputs and can disagree with earlier tests even if the argument list is unchanged. Purity removes such hidden conditions or moves them to explicit parameters, narrowing the evidence needed to explain a result. This improves tests and reasoning, not necessarily raw execution speed.

</details>

- [Back to top](#back-to-top)

---

## <a id="purity-input-output">Explicit Inputs, Results, and External State</a>

<details>
<summary>Click for details</summary>

An order, discount rule, and tax rate belong in the input model when they affect a total. The caller should inspect the returned amount without searching for fields silently changed elsewhere. Receiving an object as an argument does not guarantee purity if the function mutates its nested list. Read files and services in an outer step, then pass the resulting values into a calculation that does not know their source.

~~~text
rules = readPricingRules() // I/O
amount = calculate(order, rules) // pure candidate
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="referential-transparency">Referential Transparency and Substitution</a>

<details>
<summary>Click for details</summary>

Referential transparency allows an expression to be substituted with its value without altering observable program behavior. If twice(4) always means 8, reasoning about twice(4)+twice(4) as 8+8 is safe. A clock-reading function breaks that property even if it never writes a field. This substitution model is useful for mathematical reasoning and composing computations, not a promise that floating-point arithmetic ignores rounding rules.

~~~text
twice(4)+twice(4) = 8+8 = 16
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="purity-boundaries">Purity Violations and Testable Calculations</a>

<details>
<summary>Click for details</summary>

A function updating shared stock, querying live exchange rates, or appending logs is impure regardless of its return value. Capture the rate at the boundary and pass it into a pure conversion function. Tests of conversion can then use fixed rates and inputs, while separate integration tests cover timeouts, retries, and provider errors. Do not hide networking behind a function called calculateRate if its operation actually performs I/O.

</details>

- [Back to top](#back-to-top)
