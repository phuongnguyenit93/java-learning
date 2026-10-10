<a id="back-to-top"></a>

# Side Effects and Program Boundaries

## Menu
- [Side Effects and Observable External Changes](#effect-concept)
- [Pure Calculations and Effectful Operations](#pure-core-effects)
- [Sequencing Effects and Coordinating State](#effect-coordination)
- [Error Handling and Testing at Effect Boundaries](#effect-testing)
- [Functional Logic alongside I/O and External Services](#effects-real-world)

## <a id="effect-concept">Side Effects and Observable External Changes</a>

<details>
<summary>Click for details</summary>

A side effect is observable behavior beyond producing a return value: logging, writing storage, sending a message, or reading a changing clock. A function can compute the right number while still being impure because it mutates something else. An explicit returned error value is not inherently an external effect, whereas throwing or performing I/O requires considering the program's observable behavior. Functional programming makes effects intentional; it does not forbid them.

</details>

- [Back to top](#back-to-top)

---

## <a id="pure-core-effects">Pure Calculations and Effectful Operations</a>

<details>
<summary>Click for details</summary>

A useful separation puts predictable business calculations in a pure core and file, network, clock, and database operations in an effectful shell. The shell fetches an order and policy, passes those values to calculateTotal, then persists or charges according to the result. Pure calculations can be tested offline; adapters need integration tests for real failures. This is an architectural aid, not a claim that functional calculations implement atomic transactions.

~~~text
read(order,policy) [I/O] -> calculateTotal [pure] -> charge/save [I/O]
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="effect-coordination">Sequencing Effects and Coordinating State</a>

<details>
<summary>Click for details</summary>

Effect order can determine correctness. Marking an order paid before a payment provider confirms success risks recording a false state; charging twice during a retry risks duplicate payment. Pure rules may decide eligibility, but an effect coordinator must call the provider, interpret its result, and handle partial failure. Separating concerns makes this order easier to examine, but does not guarantee exactly-once delivery or solve distributed consistency automatically.

</details>

- [Back to top](#back-to-top)

---

## <a id="effect-testing">Error Handling and Testing at Effect Boundaries</a>

<details>
<summary>Click for details</summary>

Test price calculation with fixed inputs: empty order, negative amount, a valid discount, and a rounding boundary. A pure function needs no real clock or database. Test the effect adapter separately with provider timeout, rejected payment, duplicate response, and persistence failure. An in-memory fake verifies expected calls but cannot prove real network reliability. Represent errors explicitly enough for the caller to choose rejection, retry, compensation, or escalation.

</details>

- [Back to top](#back-to-top)

---

## <a id="effects-real-world">Functional Logic alongside I/O and External Services</a>

<details>
<summary>Click for details</summary>

A functional design can sit inside an ordinary web application. Receive request, validate data, calculate a new order value, persist the decision, and respond. Parsing and saving interact with system boundaries, while the intermediate price computation can remain pure. If another request updates the same order between read and save, a version check or lock may still be necessary; immutability of a local value does not coordinate concurrent writes.

~~~text
HTTP -> read/validate -> pure transform -> version check/save -> HTTP
~~~

</details>

- [Back to top](#back-to-top)
