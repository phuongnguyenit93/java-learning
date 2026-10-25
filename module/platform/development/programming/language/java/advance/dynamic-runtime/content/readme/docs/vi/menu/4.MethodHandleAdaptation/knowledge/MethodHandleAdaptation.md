<a id="back-to-top"></a>

# Biến đổi và kết hợp MethodHandle

## Menu
- [Mô hình adaptation và composition](#method-handle-adaptation-model)
- [Thích nghi kiểu của MethodHandle](#method-handle-type-adaptation)
- [Binding, chèn và sắp xếp argument](#method-handle-binding)
- [Biến đổi argument và return value](#method-handle-transformations)
- [Ghép handle thành invocation pipeline](#method-handle-composition)
- [Guard, SwitchPoint và lựa chọn target tại runtime](#method-handle-guards)

## <a id="method-handle-adaptation-model">Mô hình adaptation và composition</a>

<details>
<summary>Xem chi tiết</summary>

Sau khi có một MethodHandle đúng member, phần mạnh của `java.lang.invoke` là tạo **handle mới** bằng adaptation/composition thay vì viết wrapper method thủ công.

Running example:

```text
(Greeter,String)String
        ↓ bind receiver
(String)String
        ↓ filter argument
(Object)String
        ↓ filter return
(Object)Object
```

Mỗi bước tạo một handle mới với `MethodType` mới nhưng vẫn đại diện cho một chuỗi thực thi có thể gọi trực tiếp.

Hai khái niệm:

- **adaptation**: thay đổi cách phía gọi nhìn/đưa argument vào target;
- **composition**: ghép nhiều handle thành một thao tác lớn hơn.

Không phải mọi adapter đều “không có chi phí”. Một số conversion, boxing/cast hay chuỗi handle phức tạp có chi phí; trước hết hãy dùng chúng vì tính đúng đắn và thiết kế, rồi benchmark nếu hiệu năng thực sự quan trọng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handle-type-adaptation">Thích nghi kiểu của MethodHandle</a>

<details>
<summary>Xem chi tiết</summary>

`asType` tạo adapter sang một `MethodType` khác khi conversions được hỗ trợ.

```java
MethodHandle typed = ...; // (String)String

MethodHandle generic =
        typed.asType(
                MethodType.methodType(
                        Object.class,
                        Object.class
                )
        );
```

Khi invoke `generic`, argument `Object` sẽ được convert/cast về `String`; return `String` được nhìn như `Object`.

`explicitCastArguments` có quy tắc conversion khác và rõ ràng hơn cho một số cast/primitive conversion. Không nên chọn ngẫu nhiên giữa chúng; hãy bắt đầu từ hợp đồng lời gọi mong muốn rồi xem adapter nào thể hiện conversion policy đúng.

Rule:

```text
target type hiện tại
        ↓
hợp đồng lời gọi mong muốn
        ↓
adapter hợp lệ?
        ↓
handle mới với exact type mới
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handle-binding">Binding, chèn và sắp xếp argument</a>

<details>
<summary>Xem chi tiết</summary>

Binding giúp cố định một argument trước:

```java
Greeter greeter = new Greeter();

MethodHandle greet = ...; // (Greeter,String)String

MethodHandle bound =
        greet.bindTo(greeter); // (String)String
```

`insertArguments` tổng quát hơn vì có thể bind argument tại vị trí chỉ định:

```java
MethodHandle fixedPrefix =
        MethodHandles.insertArguments(
                target,
                0,
                "INFO"
        );
```

`permuteArguments` đổi thứ tự hoặc reuse argument dựa trên mapping:

```text
before: (A,B)R
after : (B,A)R
```

Những adapter này hữu ích khi framework muốn chuẩn hóa nhiều target khác signature về cùng một hợp đồng nội bộ mà không tạo class wrapper cho từng target.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handle-transformations">Biến đổi argument và return value</a>

<details>
<summary>Xem chi tiết</summary>

`filterArguments` biến đổi input trước khi target chạy:

```java
MethodHandle trim =
        lookup.findVirtual(
                String.class,
                "trim",
                MethodType.methodType(String.class)
        );

MethodHandle normalized =
        MethodHandles.filterArguments(
                boundGreet, // (String)String
                0,
                trim        // (String)String
        );
```

Flow:

```text
"  Phuong "
→ trim
→ "Phuong"
→ greet
→ "Hello Phuong"
```

`filterReturnValue` làm điều tương tự với output.

Ngoài filter, `foldArguments`/combiner APIs cho phép tính thêm giá trị từ arguments rồi đưa vào target. Khi chuỗi handle bắt đầu khó đọc hơn một method bình thường, đó là tín hiệu nên cân nhắc wrapper Java rõ ràng hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handle-composition">Ghép handle thành invocation pipeline</a>

<details>
<summary>Xem chi tiết</summary>

Composition biến nhiều thao tác nhỏ thành một chuỗi lời gọi có thể thực thi:

```text
input
 ↓
normalize
 ↓
validate / select
 ↓
business target
 ↓
format result
```

Ưu điểm trong mã runtime/framework:

- chuỗi handle có thể được xây một lần;
- final handle có một `MethodType` rõ;
- phía gọi chỉ invoke một handle;
- chuỗi handle có cấu trúc kiểu rõ ràng để JVM có cơ hội tối ưu; hiệu quả thực tế vẫn phải được đo trên workload cụ thể.

Nhưng composition quá sâu cũng làm stack trace và việc suy luận khó hơn. Hãy đặt tên biến theo từng giai đoạn có ý nghĩa:

```java
MethodHandle normalizeName = ...;
MethodHandle invokeGreeting = ...;
MethodHandle formatResult = ...;
```

Đừng đặt `mh1`, `mh2`, `mh3` nếu mục tiêu là curriculum hoặc production maintainability.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handle-guards">Guard, SwitchPoint và lựa chọn target tại runtime</a>

<details>
<summary>Xem chi tiết</summary>

`guardWithTest` chọn target theo một predicate handle:

```text
test(args)
 ├─ true  → target(args)
 └─ false → fallback(args)
```

```java
MethodHandle guarded =
        MethodHandles.guardWithTest(
                test,
                fastPath,
                fallback
        );
```

Các ràng buộc kiểu quan trọng:

- `test` phải trả về `boolean`;
- `target` và `fallback` phải có cùng type;
- tham số mà `test` nhận phải khớp với **prefix** tham số tương ứng của `target`/`fallback`; `test` có thể nhận ít tham số hơn hai handle kia.

Nếu các type không thỏa điều kiện này, `guardWithTest` ném `IllegalArgumentException` ngay khi xây adapter, trước khi handle kết quả được invoke.

`SwitchPoint` bổ sung một dạng one-way invalidation. Trước invalidation, guard đi theo target chính; sau invalidation, nó chuyển vĩnh viễn sang fallback.

```text
giả định cache còn hiệu lực
→ fast target

giả định bị vô hiệu hóa
→ SwitchPoint.invalidateAll(...)
→ fallback target từ đó về sau
```

Đây là pattern hữu ích cho runtime optimization/cache invalidation. Nó khác `MutableCallSite`: SwitchPoint biểu diễn **một giả định bị vô hiệu hóa một chiều**, còn mutable call site biểu diễn target có thể được cập nhật nhiều lần.

</details>

- [Quay lại đầu trang](#back-to-top)
