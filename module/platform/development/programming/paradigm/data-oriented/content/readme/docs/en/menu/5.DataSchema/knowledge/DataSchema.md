<a id="back-to-top"></a>

# Data Shapes, Schemas, and Validation Boundaries

## Menu
- [Data Shapes and Separate Schema Descriptions](#dop-schema-concept)
- [Flexible Data and Selective Schema Validation](#dop-schema-optional)
- [Validation at Input, Output, and Trust Boundaries](#dop-validation-boundaries)
- [Invalid Data, Error Information, and Recovery Choices](#dop-validation-failure)
- [Schema Evolution and the Cost of Loose Representations](#dop-schema-tradeoffs)

## <a id="dop-schema-concept">Data Shapes and Separate Schema Descriptions</a>

<details>
<summary>Click for details</summary>

A data shape is the arrangement of fields and nested values, such as an order with id and lines. A **schema** separately states which fields are required, their types, and constraints: lines must be a list, quantity must be positive, and price nonnegative. The map still contains the values; the schema is a rule describing acceptable maps, not a copy of each order. Separating both lets different operations share representation while validating where needed.

~~~text
order data: {id:A, lines:[{qty:2, price:30}]}
schema: id required; lines list; qty > 0; price >= 0
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-schema-optional">Flexible Data and Selective Schema Validation</a>

<details>
<summary>Click for details</summary>

Sharvit's fourth principle does not demand one globally rigid schema for all transient values. A local projection with only id and total may be useful for reporting without duplicating the complete order schema. At a public input boundary, however, an explicit schema can prevent bad shapes from entering the system. Choose validation based on trust and consequences: a temporary internal map and an untrusted payment request need different levels of scrutiny.

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-validation-boundaries">Validation at Input, Output, and Trust Boundaries</a>

<details>
<summary>Click for details</summary>

Validate where trust changes. On receiving an external order, ensure each line contains a known sku, a positive integer quantity, and an allowed price representation before calculating totals. Check outgoing payloads against the recipient's contract as well. Validation is not confined to a single library: it is a design responsibility that prevents downstream calculations from silently interpreting absent or malformed values as legitimate data.

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-validation-failure">Invalid Data, Error Information, and Recovery Choices</a>

<details>
<summary>Click for details</summary>

Consider `lines[1].qty = -3`, the second line's invalid quantity in the same order representation. A useful validator returns a structured failure containing location, expected rule, and actual value rather than quietly replacing -3 with zero. The caller can reject the request, ask for correction, or apply an explicitly approved normalization policy. Do not turn all validation failures into catch-all exceptions with no field context: readable error evidence is part of safely working with generic data.

</details>

- [Back to top](#back-to-top)

---

## <a id="dop-schema-tradeoffs">Schema Evolution and the Cost of Loose Representations</a>

<details>
<summary>Click for details</summary>

Flexible representation eases experimentation and interoperation, but evolving a schema takes discipline. Renaming qty to quantity can break consumers that still read qty; adding a required field breaks older producers. Plan compatibility, defaults only when semantically safe, and version transitions where necessary. Typed models can catch some mistakes earlier, whereas runtime validation can accept varying external shapes. Neither option removes the need to manage a changing business contract.

### References

- [Separate data schema from data representation - Yehonathan Sharvit](https://blog.klipse.tech/databook/2022/06/22/data-validation.html): why schema and data are independent.

</details>

- [Back to top](#back-to-top)
