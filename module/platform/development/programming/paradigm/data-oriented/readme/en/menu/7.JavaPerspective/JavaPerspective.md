<a id="back-to-top"></a>

# Java and Project Amber Data-Oriented Perspective

## Menu
- [Java DOP as a Related but Distinct Model](#amber-definition)
- [Transparent and Immutable Data Models](#amber-transparent-data)
- [Complete Data Models That Make Illegal States Unrepresentable](#amber-valid-variants)
- [Separating Operations from Data and Handling Variants with Patterns](#amber-separate-operations)
- [Comparing Java's Typed Models with Sharvit's Generic Data](#amber-sharvit-comparison)

## <a id="amber-definition">Java DOP as a Related but Distinct Model</a>

<details>
<summary>Click for details</summary>

Inside Java/Project Amber uses Data-Oriented Programming for a related but distinct typed design style. The 2024 DOP v1.1 formulation emphasizes transparent immutable data models, complete data representation, unrepresentable illegal states, and operations separate from data. Unlike Sharvit's generic-map emphasis, Java can express shapes and variants through a static type model. Both favor reasoning about data, but their preferred representations and validation boundaries differ; this chapter is a conceptual comparison rather than a Java syntax tutorial.

</details>

- [Back to top](#back-to-top)

---

## <a id="amber-transparent-data">Transparent and Immutable Data Models</a>

<details>
<summary>Click for details</summary>

An immutable transparent data model exposes the components needed to understand a value, rather than hiding crucial facts behind a mutable object's changing behavior. Java records are often a useful representation, but a record is only shallowly immutable if one of its components refers to a mutable collection. For an order summary containing id and total, a precise data carrier is easier to compare and pass around than a component with unrelated business methods. Transparency is not permission to expose secrets publicly.

</details>

- [Back to top](#back-to-top)

---

## <a id="amber-valid-variants">Complete Data Models That Make Illegal States Unrepresentable</a>

<details>
<summary>Click for details</summary>

The principle **model the data, the whole data, and nothing but the data** asks whether every meaningful case is represented without unrelated behavior or redundant flags. **Make illegal states unrepresentable** then pushes impossible combinations out of the model. Rather than {paid:true, rejected:true}, represent one of Pending, Paid(receipt), or Rejected(reason). A sealed family can conceptually enumerate legal variants, while validation is still necessary for data arriving from outside the typed boundary.

~~~text
Payment = Pending | Paid(receipt) | Rejected(reason)
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="amber-separate-operations">Separating Operations from Data and Handling Variants with Patterns</a>

<details>
<summary>Click for details</summary>

Java DOP v1.1 also favors operations that work over data rather than packing every operation into the carrier. Given Pending, Paid, and Rejected variants, an operation can select a suitable result by handling the appropriate case; pattern matching is one Java technique for expressing this decision. The conceptual requirement is a complete and explicit choice, not memorizing switch syntax. Functions processing these values may still have effects, so separation is not identical to universal purity.

</details>

- [Back to top](#back-to-top)

---

## <a id="amber-sharvit-comparison">Comparing Java's Typed Models with Sharvit's Generic Data</a>

<details>
<summary>Click for details</summary>

Both Sharvit-style and Java/Amber DOP favor understandable data and separate operations, but solve different representation problems. Sharvit commonly uses generic maps and independent schemas, accepting runtime validation cost for flexible shapes. Java/Amber favors precisely modeled typed variants and can reject some impossible states before execution, though external input still needs checking. The choice depends on evolution needs and contract strength, not on which name DOP is more correct.

### References

- [Data-Oriented Programming in Java - Version 1.1](https://inside.java/2024/05/23/dop-v1-1-introduction/): the four revised Java principles.
- [Principles of Data-Oriented Programming - Yehonathan Sharvit](https://blog.klipse.tech/dop/2022/06/22/principles-of-dop.html): the independent generic-data model.

</details>

- [Back to top](#back-to-top)
