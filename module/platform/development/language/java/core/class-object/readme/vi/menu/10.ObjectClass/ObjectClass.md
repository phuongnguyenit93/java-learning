# Object Class

## <a id="object-root-type">Object như root reference type</a>
Mọi class cuối cùng đều kế thừa `java.lang.Object` (array cũng là reference object có Object-compatible behavior). Điều này cung cấp common minimal API và cho phép `Object` reference trỏ tới mọi object, đổi lại compile-time không còn biết member specific.

## <a id="object-core-methods">Tổng quan getClass/toString/equals/hashCode</a>
`getClass` cho runtime class identity; `equals` và `hashCode` định nghĩa logical equality/hash contract khi override; `toString` cung cấp diagnostic representation. Contract đầy đủ thuộc object-contract module nhưng mọi class đều inherit các method này.

## <a id="clone-finalize-boundary">Boundary legacy clone/finalization và alternative an toàn hơn</a>
`Object.clone` hỗ trợ shallow field copy qua `Cloneable` protocol nhưng semantics về constructor/invariant khá awkward; copy constructor/factory thường rõ hơn. Finalization đã deprecated for removal và không dùng cho resource management bình thường. Ưu tiên `AutoCloseable` và copy policy explicit.
