# null in Java

## <a id="null-reference">null as a reference value</a>
`null` is a special reference value meaning that no object is referenced. It can be assigned to reference types, not primitive values. `null` has no runtime class and should not be treated as a placeholder object.

## <a id="null-dereference">Dereference failure</a>
Accessing an instance member through `null`, unboxing a null wrapper, or otherwise requiring an object causes `NullPointerException`. Modern JVM messages often identify the failing expression, but prevention still comes from clear contracts and validation.

## <a id="null-comparison">Null comparison and control flow</a>
Identity comparison against `null` with `==`/`!=` is the normal test. Call instance methods only after a non-null guarantee. Helpers such as `Objects.equals` can make equality null-tolerant without changing the null model.

## <a id="null-api-design">Nullability as an API-design concern</a>
Decide whether absence is valid and document it. Returning `null`, throwing for missing data, using an empty collection, or using `Optional` have different semantics; full `Optional` curriculum belongs to the functional module. Avoid unexplained nullable boundaries.
