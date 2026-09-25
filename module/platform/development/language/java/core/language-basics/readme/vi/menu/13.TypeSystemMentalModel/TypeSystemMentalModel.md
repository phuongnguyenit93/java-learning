# Mô hình Type System của Java

## <a id="compile-time-vs-runtime-type">Compile-time type và runtime type</a>
Reference expression có compile-time type dùng cho member access, overload resolution và assignment check. Object mà nó refer tới có thể có runtime class specific hơn và class đó quyết định overridden instance-method dispatch.

```java
Animal a = new Dog();
// compile-time type: Animal; runtime class: Dog
```

## <a id="assignment-compatibility">Assignment compatibility</a>
Assignment chỉ hợp lệ khi source convert được sang target theo type rule của Java. Reference widening thường safe; downcast cần explicit cast và runtime check. Primitive conversion có widening/narrowing rule riêng.

## <a id="overload-vs-override-dispatch">Overload selection và override dispatch</a>
Overload được chọn ở compile time từ declared type và conversion applicable. Override được chọn ở runtime từ receiver object thực sau khi method signature đã được chọn. Trộn hai stage này là nguồn phổ biến của dự đoán sai.

## <a id="type-system-boundaries">Boundary giữa compile-time guarantee và runtime check</a>
Compiler ngăn nhiều operation incompatible, nhưng Java vẫn có runtime check khi static typing không đủ chứng minh safety: downcast, array store, null dereference, class-loader identity và reflection. Mental model tốt phải phân biệt cái compiler guarantee với cái runtime validate.
