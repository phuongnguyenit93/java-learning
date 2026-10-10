<a id="back-to-top"></a>

# Separating Code and Data

## Menu
- [Data Values and Functions as Separate Responsibilities](#code-data-concept)
- [Behavior-Bearing Objects and Reusable Data](#behavior-coupling)
- [Inspecting and Sharing Data across Operations](#data-visibility)
- [Encapsulation and Coupling Trade-offs of Separation](#separation-tradeoffs)

## <a id="code-data-concept">Data Values and Functions as Separate Responsibilities</a>

<details>
<summary>Click for details</summary>

In a data-oriented view, an order is a value holding fields such as id, lines and status; a function such as total(order) is a separate operation. The same order can be passed to validation, reporting, and pricing without knowing which object owns a method. Separation does not require functions to be globally visible or forbid modules: code may still be grouped by domain purpose. The key is that the represented value itself does not hide processing logic.

</details>

- [Back to top](#back-to-top)

---

## <a id="behavior-coupling">Behavior-Bearing Objects and Reusable Data</a>

<details>
<summary>Click for details</summary>

An object-centric order may own order.total(), order.export(), and order.applyPolicy(). That can protect invariants, but a new team using data for an unrelated report now depends on this object's behavior or writes an adapter. With a plain order value, calculateTotal(order) and exportOrder(order) evolve separately. This can also be overdone: hiding an important business invariant outside an object without reliable validation makes the design more fragile. Choose the boundary intentionally.

</details>

- [Back to top](#back-to-top)

---

## <a id="data-visibility">Inspecting and Sharing Data across Operations</a>

<details>
<summary>Click for details</summary>

Generic data can be inspected without calling a special method on every domain object. A test can compare the order before and after discount by reading ordinary keys and values; a report can project only id and total from the same input. The benefit is interoperability among operations, not permission to expose secrets. At external boundaries, filter sensitive fields and check allowed consumers explicitly rather than passing every nested value indiscriminately.

</details>

- [Back to top](#back-to-top)

---

## <a id="separation-tradeoffs">Encapsulation and Coupling Trade-offs of Separation</a>

<details>
<summary>Click for details</summary>

Moving operations out of data objects often improves reuse, but trades off encapsulation: code may depend directly on key names, nested shapes, and informal field conventions. A rename from total to subtotal can now affect several functions. Consistent operation boundaries, explicit validation, and carefully owned schema evolution reduce the risk. For a small object with rich invariants and few consumers, preserving encapsulated behavior may still be clearer than introducing generic maps everywhere.

</details>

- [Back to top](#back-to-top)
