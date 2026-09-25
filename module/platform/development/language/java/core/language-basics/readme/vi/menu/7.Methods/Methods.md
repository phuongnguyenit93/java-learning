# Phương thức trong Java

## <a id="method-signature">Method signature, parameter và return</a>
Method Java khai báo name và parameter types; return type không tham gia overload identity. Parameter là local variable được khởi tạo từ argument value. Contract của method nên làm rõ input hợp lệ, output, side effect và exceptional behavior.

## <a id="method-invocation-conversion">Method invocation conversion</a>
Khi kiểm tra argument có dùng được cho method hay không, Java có thể dùng identity conversion, primitive/reference widening, boxing/unboxing theo invocation rule và cuối cùng varargs applicability. Không phải conversion có vẻ hợp lý nào cũng được xét.

## <a id="overload-resolution-phases">Các phase của overload resolution</a>
Overload resolution là compile-time selection. Compiler xét fixed-arity candidate ở các phase sớm và chỉ sau đó mới xét varargs. Nếu phase sớm đã có candidate hợp lệ thì phase sau không quyết định lại.

```java
void f(long x) {}
void f(Integer x) {}
void f(int... x) {}
// f(1) chọn f(long), không chọn boxing hay varargs.
```

## <a id="most-specific-overload">Chọn overload most-specific</a>
Nếu nhiều overload cùng applicable trong một phase, Java chọn method most-specific theo type relationship và invocation compatibility. Nó không đơn giản là numeric type nhỏ nhất hay method viết trước.

## <a id="null-overload-ambiguity">null và overload ambiguity</a>
`null` compatible với reference type. Nếu overload nhận các reference type không liên quan, `f(null)` có thể ambiguous vì không candidate nào specific hơn. Cast có thể disambiguate, nhưng API tốt nên tránh overload set gây khó hiểu.

## <a id="method-call-evaluation">Evaluation order của argument</a>
Argument được evaluate từ trái sang phải trước khi method body bắt đầu. Side effect vì vậy có order xác định, nhưng expression quá dày đặc side effect vẫn khó đọc.

## <a id="recursion-stack">Recursion và call-stack cost</a>
Mỗi recursive call tạo thêm invocation frame cho tới base case. Java không guarantee tail-call elimination nên recursion sâu có thể gây `StackOverflowError`. Depth không giới hạn thường nên cân nhắc iterative solution.
