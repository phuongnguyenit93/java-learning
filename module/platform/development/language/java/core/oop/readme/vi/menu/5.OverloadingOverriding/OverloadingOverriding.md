# Overloading và Overriding

## <a id="overloading-compile-time">Overloading là compile-time selection</a>
Overloaded method cùng name nhưng khác parameter type. Compiler chọn signature từ compile-time argument type và invocation conversion. Runtime object type không làm Java chọn lại một overload khác sau khi compile.

## <a id="overriding-runtime">Overriding là runtime dispatch</a>
Subclass override cung cấp implementation mới cho inherited instance-method contract với compatible signature. Sau khi signature đã được chọn, runtime receiver class quyết định override body specific nhất.

## <a id="covariant-return">Covariant return type</a>
Overriding method có thể narrow reference return type thành subtype của parent return type. Điều này tăng precision cho caller của subtype nhưng vẫn giữ supertype contract.

## <a id="override-rules">Boundary visibility/final/static/private khi overriding</a>
Override không được giảm accessibility và phải tuân checked-exception compatibility. `final` instance method không override được. `private` method không phải override target được inherit, còn static method là hiding chứ không runtime overriding.

## <a id="static-method-hiding">Static method là hiding, không overriding</a>
Khi subclass khai báo static method cùng signature, selection dựa trên compile-time qualifying type chứ không runtime class của receiver.

```java
Parent p = new Child();
p.staticCall(); // Parent static method
```

Gọi static method qua instance có thể hợp lệ nhưng dễ gây hiểu sai; nên dùng class name.

## <a id="field-hiding">Field selection theo compile-time reference type</a>
Field không virtual. Nếu parent/child có cùng field name, selected field được quyết định từ compile-time type của expression. Đây là hiding, không phải polymorphic state dispatch.

## <a id="dispatch-vs-hiding">Instance dispatch khác static/field hiding</a>
Với `Parent x = new Child()`, overridden instance method có thể chạy `Child` behavior, trong khi `x.someField` và hidden static method resolve theo `Parent`. Tách ba mechanism này giúp tránh hiểu lầm rằng mọi member đều dynamic dispatch.
