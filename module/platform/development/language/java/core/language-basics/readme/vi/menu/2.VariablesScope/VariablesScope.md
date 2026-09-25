# Biến và phạm vi

## <a id="variable-kinds-and-lifetime">Lifetime của local, parameter, field và static variable</a>
Local variable và parameter thuộc một invocation/block và không có automatic default value. Instance field thuộc về từng object. Static field thuộc về class và được chia sẻ giữa các instance trong cùng defining class loader.

Lifetime và visibility là hai khái niệm khác nhau. Field có thể sống lâu hơn method invocation, còn local variable chỉ nằm trong lexical scope của block. Lifetime của object phụ thuộc reachability chứ không phụ thuộc scope của một reference cụ thể.

## <a id="scope-and-shadowing">Scope, shadowing và name resolution</a>
Lexical scope quyết định nơi một name có thể được dùng. Local variable hoặc parameter có thể shadow field. Khi shadowing có chủ ý, `this.field` giúp phân biệt field với parameter/local. Shadowing quá mức làm code khó đọc.

## <a id="definite-assignment">Quy tắc definite assignment</a>
Compiler phân tích definite assignment cho local variable và blank `final`. Local variable chỉ được đọc nếu compiler chứng minh nó đã được gán trên mọi control-flow path đi tới điểm đọc.

```java
int value;
if (ready) value = 10;
// System.out.println(value); // compile error
```

Đây là compile-time guarantee. Field khác local variable vì object/class initialization cấp default value trước constructor hoặc explicit initializer.
