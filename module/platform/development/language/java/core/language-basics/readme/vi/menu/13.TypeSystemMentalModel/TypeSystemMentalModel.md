# Mô hình Type System

Sau toàn bộ module, cần phân biệt hai thế giới thường bị trộn lẫn: **compiler biết gì từ static type** và **runtime biết gì từ object thật**.

## <a id="compile-time-vs-runtime-type">Compile-time Type và Runtime Type</a>

```java
Animal animal = new Dog();
```

Ở đây:

```text
compile-time / declared type
→ Animal

runtime object type
→ Dog
```

Compiler dùng `Animal` để kiểm tra member nào được gọi hợp lệ và nhiều chuyển đổi/overload quy tắc. Runtime object `Dog` tham gia dynamic dispatch cho overridden instance method.

Hai type này trả lời hai câu hỏi khác nhau và cả hai đều cần thiết.

## <a id="assignment-compatibility">Assignment Compatibility</a>

Java chỉ cho assignment khi value/type relationship phù hợp theo language các quy tắc:

```java
Dog dog = new Dog();
Animal animal = dog; // upcast hợp lệ
```

Chiều ngược lại cần explicit cast và runtime check:

```java
Dog again = (Dog) animal;
```

Primitive assignment có bộ chuyển đổi quy tắc khác reference assignment.

Type system giúp loại bỏ nhiều lỗi trước runtime, nhưng không chứng minh mọi cast/reference thao tác đều an toàn ở runtime.

## <a id="overload-vs-override-dispatch">Overload và Override</a>

Hai cơ chế rất dễ nhầm:

```text
overload selection
→ compile time
→ dựa trên tập method + kiểu đối số/chuyển đổi ở compile time

override dispatch
→ runtime
→ dựa trên runtime receiver type sau khi signature đã được xác định
```

Ví dụ `Parent x = new Child()` có thể chọn overload theo `Parent` đối số type nhưng gọi overridden instance body của `Child`.

OOP module sẽ đi sâu hơn vào dynamic dispatch; ở đây chỉ cần khóa mô hình tư duy compile-time vs runtime.

## <a id="type-system-boundaries">Ranh giới Compile Time và Runtime</a>

Compiler có thể kiểm tra:

- name/type resolution;
- assignment compatibility;
- overload applicability;
- definite assignment;
- nhiều access/cast constraints.

Runtime vẫn phải xử lý những điều compiler không thể biết chắc:

- downcast object thật có đúng type không;
- array store có đúng runtime component type không;
- reference có null không;
- index có nằm trong array phạm vi không.

Đó là lý do Java vừa có static type checking vừa có runtime exception như `ClassCastException`, `ArrayStoreException`, `NullPointerException`.

Kết thúc module, hãy giữ chuỗi tư duy:

```text
value model
→ scope/lifetime
→ chuyển đổi/biểu thức
→ control flow
→ method call
→ pass-by-value/reference sharing
→ arrays/packages/null
→ kiểu compile-time và hành vi runtime
```
