<a id="back-to-top"></a>

# invokedynamic và bootstrap linkage

## Menu
- [invokedynamic giải quyết vấn đề gì?](#invokedynamic-purpose)
- [Hợp đồng của bootstrap method](#bootstrap-method-contract)
- [Lookup, name, MethodType và static arguments](#bootstrap-linkage-inputs)
- [Vòng đời linkage của invokedynamic](#invokedynamic-linkage-lifecycle)
- [CallSite validation và BootstrapMethodError](#invokedynamic-linkage-failures)
- [Lambda, string concatenation và các nơi sử dụng ở runtime](#invokedynamic-runtime-consumers)

## <a id="invokedynamic-purpose">invokedynamic giải quyết vấn đề gì?</a>

<details>
<summary>Xem chi tiết</summary>

Các bytecode invoke truyền thống như `invokevirtual` hay `invokestatic` mô tả khá trực tiếp kiểu symbolic reference mà JVM cần resolve. `invokedynamic` mở một extension point khác: **logic linkage do bootstrap method quyết định**.

Thay vì bytecode chỉ rõ target method cuối cùng:

```text
invokedynamic
→ JVM gọi bootstrap method khi cần link call site
→ bootstrap trả về CallSite
→ instruction từ đó dispatch qua CallSite đó
```

Điều này cho phép language runtime hoặc JDK runtime library triển khai custom invocation semantics mà không cần thêm bytecode instruction riêng cho từng language feature.

Java source bình thường hiếm khi tạo `invokedynamic` trực tiếp. Compiler/JDK runtime có thể dùng nó cho feature như lambda hoặc string concatenation. Module này học cơ chế để hiểu luồng runtime, không dạy viết classfile generator.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bootstrap-method-contract">Hợp đồng của bootstrap method</a>

<details>
<summary>Xem chi tiết</summary>

Bootstrap method là method được JVM gọi để link một dynamic call site. Một shape phổ biến:

```java
static CallSite bootstrap(
        MethodHandles.Lookup lookup,
        String name,
        MethodType type
) throws Throwable
```

Bootstrap có thể nhận thêm static arguments từ constant pool.

Nhiệm vụ chính:

1. đọc context (`Lookup`, symbolic name, `MethodType`, static args);
2. chọn/tạo target `MethodHandle`;
3. adapt target về đúng call-site type nếu cần;
4. trả một `CallSite`.

```java
MethodHandle target = ...;
target = target.asType(type);
return new ConstantCallSite(target);
```

Bootstrap nên tập trung vào **linkage**, không chứa luồng nghiệp vụ cần chạy lại cho mỗi invocation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bootstrap-linkage-inputs">Lookup, name, MethodType và static arguments</a>

<details>
<summary>Xem chi tiết</summary>

Ba đầu vào cốt lõi:

- `Lookup`: access context của class chứa dynamic call site;
- `name`: symbolic name do classfile/phía gọi cung cấp;
- `MethodType`: descriptor của dynamic call site.

Static bootstrap arguments bổ sung configuration đã encode trong classfile. Chúng là các **loadable constant-pool values** như primitive/String constant, `Class`, `MethodHandle` hoặc `MethodType`; một số giá trị cũng có thể cần được resolve động trước khi bootstrap nhận được chúng.

Mental model:

```text
class chứa call site
   ↓ Lookup

symbolic operation
   ↓ name

hợp đồng lời gọi
   ↓ MethodType

loadable constant-pool values
   ↓ static args

bootstrap policy
   ↓
CallSite
```

`Lookup` ở đây quan trọng vì bootstrap thường cần resolve member theo quyền của class chứa call site, không phải quyền tùy ý của phần triển khai bootstrap.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="invokedynamic-linkage-lifecycle">Vòng đời linkage của invokedynamic</a>

<details>
<summary>Xem chi tiết</summary>

Vòng đời đơn giản:

```text
1. JVM gặp invokedynamic instruction chưa được link
        ↓
2. Resolve bootstrap method + bootstrap arguments
        ↓
3. Invoke bootstrap
        ↓
4. Bootstrap trả CallSite
        ↓
5. JVM kiểm tra CallSite type
        ↓
6. Instruction được link với CallSite
        ↓
7. Invocation dispatch qua target hiện tại của CallSite
```

Bootstrap có thể được JVM thực thi lazily khi instruction lần đầu cần link. Đây là lý do “bootstrap chạy mỗi lần invoke” là sai.

Nếu nhiều thread cùng chạm tới một call site chưa link, bootstrap **có thể được gọi đồng thời nhiều lần**. JVM chỉ cài đặt một kết quả; các lần gọi bootstrap còn lại có thể hoàn tất nhưng kết quả của chúng bị bỏ qua. Vì vậy bootstrap có đọc/ghi trạng thái dùng chung vẫn phải an toàn thread.

Sau linkage, nếu CallSite mutable đổi target, instruction vẫn giữ CallSite đã link; chỉ target được dispatch thay đổi. Mỗi `invokedynamic` instruction chỉ chuyển từ unlinked sang linked tối đa một lần, dù bootstrap có thể chủ động trả cùng một `CallSite` object cho nhiều instruction khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="invokedynamic-linkage-failures">CallSite validation và BootstrapMethodError</a>

<details>
<summary>Xem chi tiết</summary>

CallSite do bootstrap trả về phải có type tương thích chính xác với descriptor của invokedynamic instruction.

Failure semantics cần tách rõ:

- nếu bootstrap ném một `Error` hoặc subclass của `Error`, chính error đó được dùng làm resolution failure;
- nếu bootstrap ném một `Throwable` không phải `Error`, JVM bọc nó trong `BootstrapMethodError` và giữ throwable gốc làm cause;
- nếu bootstrap trả `null`, trả object không phải `CallSite`, hoặc trả `CallSite` có type không khớp descriptor của instruction, resolution thất bại bằng `BootstrapMethodError`.

Với cùng một `invokedynamic` instruction, sau khi resolution đã thất bại thì các lần thực thi sau tiếp tục nhận cùng resolution error; bootstrap không được chạy lại cho instruction đó.

Luồng debug:

```text
lỗi invokedynamic
→ call-site MethodType là gì?
→ bootstrap nhận name/type nào?
→ target.type() trước asType là gì?
→ returned CallSite.type() là gì?
→ nếu là BootstrapMethodError, original cause là gì?
→ nếu là Error khác, bootstrap/resolution đã ném trực tiếp error nào?
```

Không nên bắt `BootstrapMethodError` như một business exception. Đây thường là lỗi linkage, lập trình hoặc cấu hình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="invokedynamic-runtime-consumers">Lambda, string concatenation và các nơi sử dụng ở runtime</a>

<details>
<summary>Xem chi tiết</summary>

Hai ví dụ quen thuộc:

- lambda: compiler có thể emit invokedynamic và bootstrap qua `LambdaMetafactory`;
- string concatenation hiện đại: runtime có thể dùng `StringConcatFactory`.

Ví dụ source:

```java
Function<String, String> upper =
        value -> value.toUpperCase();
```

Người học không nên suy ra rằng “lambda chính là invokedynamic”. Lambda là **language feature**; invokedynamic là một cơ chế runtime/linkage mà compiler/JDK có thể dùng để hiện thực feature đó.

Tương tự:

```java
String result = "Hello " + name;
```

không có nghĩa business code đang chủ động dùng Dynamic Runtime API.

Ranh giới này giúp tránh biến chapter thành lịch sử cách triển khai của từng Java feature.

</details>

- [Quay lại đầu trang](#back-to-top)
