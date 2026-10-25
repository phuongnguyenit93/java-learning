<a id="back-to-top"></a>

# Mô hình lời gọi có kiểu

## Menu
- [MethodType và hợp đồng kiểu của lời gọi](#method-type-model)
- [MethodHandle là tham chiếu thực thi có kiểu](#method-handle-model)
- [Signature polymorphism của MethodHandle](#signature-polymorphism)
- [invokeExact và invoke](#invoke-exact-vs-invoke)
- [Lỗi kiểu trong lời gọi MethodHandle](#invocation-type-failures)

## <a id="method-type-model">MethodType và hợp đồng kiểu của lời gọi</a>

<details>
<summary>Xem chi tiết</summary>

`MethodType` là value object bất biến mô tả **kiểu trả về + danh sách kiểu tham số** của một lời gọi. Nó không chứa tên method, receiver hay phần triển khai.

```java
MethodType type =
        MethodType.methodType(
                String.class,
                String.class
        );
```

Mental model:

```text
(String)String
   │       └── return type
   └────────── parameter types
```

Một `MethodHandle` luôn có `type()`. Bên gọi cũng có một symbolic method type tại call site. JVM dùng hai phía này để quyết định lời gọi có hợp lệ hay cần adaptation.

`MethodType` cũng là cầu nối với JVM descriptor. Ví dụ method descriptor:

```text
(Ljava/lang/String;)Ljava/lang/String;
```

Không cần học constant pool chi tiết ở đây; chỉ cần hiểu MethodType là biểu diễn cấp Java của hợp đồng lời gọi mà MethodHandle và invokedynamic cùng sử dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handle-model">MethodHandle là tham chiếu thực thi có kiểu</a>

<details>
<summary>Xem chi tiết</summary>

`MethodHandle` là một **tham chiếu thực thi có kiểu** (typed, directly executable reference) tới một thao tác mức thấp như method, constructor, field accessor hoặc thao tác đã được composition. Nó gần với “callable value” hơn là metadata.

Running example:

```java
final class Greeter {
    String greet(String name) {
        return "Hello " + name;
    }
}

MethodHandles.Lookup lookup = MethodHandles.lookup();
MethodType type = MethodType.methodType(String.class, String.class);

MethodHandle greet =
        lookup.findVirtual(
                Greeter.class,
                "greet",
                type
        );
```

`greet.type()` ở đây là:

```text
(Greeter, String)String
```

Receiver trở thành **tham số đầu tiên trong `MethodType` của handle** cho virtual method. Điều này giải thích vì sao ta có thể bind receiver sau đó để nhận một handle mới kiểu `(String)String`.

MethodHandle là bất biến. Các thao tác adaptation không thay đổi handle cũ; chúng tạo handle mới đại diện cho pipeline mới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="signature-polymorphism">Signature polymorphism của MethodHandle</a>

<details>
<summary>Xem chi tiết</summary>

Trong Java API, `MethodHandle.invokeExact` và `invoke` được biểu diễn tĩnh bằng tham số varargs `Object...` và kiểu trả về `Object`, nhưng JVM không xử lý chúng như varargs method bình thường. Đây là **signature-polymorphic methods**: descriptor thực tế của lời gọi được lấy từ static types xuất hiện tại call site.

Ví dụ:

```java
MethodHandle handle = ...; // (Greeter,String)String

String value =
        (String) handle.invokeExact(
                greeter,
                "Phuong"
        );
```

Cast `(String)` không chỉ để “ép Object”. Nó góp phần xác định expected return type của call-site descriptor.

Điểm này rất quan trọng khi debug: hai dòng trông gần giống nhau trong source có thể tạo call-site descriptors khác nhau chỉ vì static type hoặc cast khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="invoke-exact-vs-invoke">invokeExact và invoke</a>

<details>
<summary>Xem chi tiết</summary>

`invokeExact` yêu cầu symbolic type của call site **khớp chính xác** với type của MethodHandle.

```java
MethodHandle h = ...; // (Greeter,String)String

String ok = (String) h.invokeExact(greeter, "A");
```

`invoke` linh hoạt hơn: khi type không khớp chính xác, hành vi tương đương việc adapt handle bằng `asType` nếu conversion được hỗ trợ, rồi thực hiện lời gọi exact.

```java
Object value = h.invoke(greeter, "A");
```

Quy tắc thực dụng:

- dùng `invokeExact` khi pipeline đã có hợp đồng kiểu chính xác;
- dùng `invoke` khi chủ đích chấp nhận các conversion mà `MethodHandle.asType` cho phép;
- đừng dựa vào `invoke` để che một thiết kế có hợp đồng kiểu mơ hồ.

Trong mã framework, việc chuẩn hóa handle về một `MethodType` thống nhất trước rồi gọi `invokeExact` thường giúp suy luận dễ hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="invocation-type-failures">Lỗi kiểu trong lời gọi MethodHandle</a>

<details>
<summary>Xem chi tiết</summary>

Lỗi đặc trưng là `WrongMethodTypeException`: descriptor phía gọi và type của handle không tương thích theo quy tắc của `invokeExact`/`invoke`.

```java
MethodHandle h = ...; // (Greeter,String)String

// Expected int, nhưng handle trả String.
int value = (int) h.invokeExact(greeter, "A");
```

Đừng nhầm lỗi kiểu với lỗi access:

```text
Lookup/find...
→ NoSuchMethodException / NoSuchFieldException
→ IllegalAccessException

invoke/adapt...
→ WrongMethodTypeException
→ ClassCastException trong conversion phù hợp một số path
```

Ngoài các lỗi của chính cơ chế MethodHandle, exception do target ném ra sẽ **truyền nguyên trạng** qua `invokeExact`/`invoke`; MethodHandle không bọc nó trong `InvocationTargetException` như `Method.invoke`. Vì API được khai báo `throws Throwable`, code gọi trực tiếp phải bắt hoặc khai báo `Throwable`/kiểu phù hợp.

Một edge case khác: `MethodType` chứa các `Class` object thật. Hai class có cùng tên nhưng được nạp bởi hai class loader khác nhau vẫn là hai type khác nhau, nên việc match MethodHandle type cũng xét class-loader identity. Nếu lỗi đi sâu vào class identity/loading, phần cơ chế thuộc module ClassLoader.

Debug checklist:

1. in `handle.type()`;
2. xác định static type của từng argument tại call site;
3. xác định expected return type do assignment/cast tạo ra;
4. nếu dùng adapter, kiểm tra type trước và sau adapter.

MethodType nên được coi như “schema của lời gọi”, không phải chi tiết phụ.

</details>

- [Quay lại đầu trang](#back-to-top)
