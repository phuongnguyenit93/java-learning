# String Concatenation

## <a id="concat-semantics">Semantics của string concatenation</a>
Operator `+` concatenate khi operand liên quan là String. Primitive/object được convert sang text theo Java rule. Association quan trọng: `"x" + 1 + 2` tạo `x12`, còn `1 + 2 + "x"` tạo `3x`.

## <a id="compile-time-concat">Compile-time constant concatenation</a>
Concatenation chỉ gồm compile-time constant có thể được compiler fold thành một pooled literal. Điều này ảnh hưởng identity observation nhưng không phải lý do dùng `==` để compare String.

## <a id="runtime-concat">Runtime concatenation và implementation boundary</a>
Runtime concatenation có thể được implement bằng `StringBuilder`, `invokedynamic` concat strategy hoặc optimization khác tùy Java version. Source code chỉ nên rely vào resulting String semantics, không rely mechanism generated cụ thể.

## <a id="loop-concat-cost">Cost của repeated concatenation</a>
Tạo immutable String mới lặp lại trong loop có thể copy content tăng dần nhiều lần. Dùng `StringBuilder` khi xây chuỗi incremental với số phần dynamic. Với expression nhỏ cố định, `+` rõ ràng và compiler/JVM optimize tốt.
