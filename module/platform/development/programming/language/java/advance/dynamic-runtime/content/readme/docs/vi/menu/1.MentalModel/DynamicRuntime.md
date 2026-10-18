<a id="back-to-top"></a>

# Mô hình Dynamic Runtime

## Menu
- [Dynamic Runtime là gì và vì sao tồn tại?](#dynamic-runtime-purpose)
- [Những bài toán cần cơ chế runtime động](#dynamic-runtime-problem-space)
- [Lời gọi trực tiếp, Reflection và java.lang.invoke](#direct-reflection-invoke-boundary)
- [Điều kiện tiên quyết và ranh giới của module](#dynamic-runtime-boundaries)

## <a id="dynamic-runtime-purpose">Dynamic Runtime là gì và vì sao tồn tại?</a>

<details>
<summary>Xem chi tiết</summary>

Dynamic Runtime trong module này là tập các cơ chế mức thấp của `java.lang.invoke` cho phép Java biểu diễn một lời gọi hoặc một vị trí dữ liệu như một giá trị có kiểu, sau đó liên kết, biến đổi hoặc chọn hành vi ở runtime. Trọng tâm không phải là “viết code động cho mọi thứ”, mà là hiểu những primitive mà JVM, framework và language runtime dùng khi lời gọi Java trực tiếp không còn đủ linh hoạt.

Hai nhánh lớn sẽ xuất hiện xuyên suốt module:

```text
typed invocation
MethodType + MethodHandle + Lookup
        ↓
adaptation / composition
        ↓
CallSite + invokedynamic

typed variable access
VarHandle
```

`MethodHandle` và `VarHandle` được JVM đối xử đặc biệt: các access method của chúng là signature-polymorphic, nên descriptor tại call site có thể mang kiểu thực tế thay vì bị giới hạn bởi signature Java nhìn thấy trong source.

Lộ trình học của module đi theo thứ tự:

```text
MethodType + MethodHandle
→ Lookup
→ adaptation / composition
→ CallSite
→ invokedynamic
→ VarHandle
→ synthesis / decision model
```

Nhánh invocation/linkage được học trước để tạo mental model hoàn chỉnh; sau đó `VarHandle` mở nhánh variable-access song song. Kết thúc module, người học phải phân biệt được **hợp đồng kiểu**, **quyền truy cập**, **đối tượng linkage** và **ngữ nghĩa truy cập biến** thay vì chỉ nhớ tên API.

Điểm cần giữ ngay từ đầu: **dynamic** không đồng nghĩa với “không biết gì cho tới runtime”. Một member có thể đã biết trước nhưng ta vẫn muốn một handle thực thi có kiểu, một quyền lookup có thể truyền đi, một chuỗi handle có thể ghép lại, hoặc một call site có target thay đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-runtime-problem-space">Những bài toán cần cơ chế runtime động</a>

<details>
<summary>Xem chi tiết</summary>

Giả sử framework cần gọi method `greet(String)` trên nhiều cách triển khai. Nếu code ứng dụng biết chính xác type tại compile time, lời gọi trực tiếp là lựa chọn tốt nhất:

```java
String result = greeter.greet("Phuong");
```

Khó khăn bắt đầu khi framework muốn **lưu hành vi như dữ liệu**, bind một receiver trước, đổi signature cho khớp một protocol nội bộ, hoặc liên kết một call site một lần rồi dispatch qua target hiện tại. Reflection giải quyết tốt việc khám phá metadata và gọi member khi cần tính động, nhưng `java.lang.invoke` bổ sung một mô hình khác: executable references có kiểu + các phép biến đổi có thể ghép lại.

Một ví dụ mental model:

```text
"Tôi biết hợp đồng lời gọi là (String)String"
        ↓
MethodType mô tả hợp đồng
        ↓
Lookup kiểm tra quyền và tạo MethodHandle
        ↓
MethodHandle có thể bind/adapt/compose
        ↓
CallSite có thể giữ target
        ↓
invokedynamic cho JVM liên kết call site qua bootstrap
```

VarHandle giải quyết một bài toán khác: biểu diễn **một variable hoặc họ variable** cùng access semantics như plain, volatile, acquire/release hay compare-and-set.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="direct-reflection-invoke-boundary">Lời gọi trực tiếp, Reflection và java.lang.invoke</a>

<details>
<summary>Xem chi tiết</summary>

Ba cơ chế thường bị đặt cạnh nhau nhưng trách nhiệm khác nhau:

| Cơ chế | Phù hợp khi | Đặc điểm chính |
| --- | --- | --- |
| Lời gọi trực tiếp | Type và target đã nằm trong code bình thường | Đơn giản nhất, dễ đọc, compiler kiểm tra rõ |
| Reflection | Cần khám phá class/member/annotation hoặc invoke theo metadata | Metadata-first, API quen thuộc cho inspection |
| `java.lang.invoke` | Cần executable handle có kiểu, adaptation/composition hoặc runtime linkage | Runtime/JVM-oriented, mạnh nhưng phức tạp hơn |

Ví dụ Reflection:

```java
Method method = Greeter.class.getMethod("greet", String.class);
Object result = method.invoke(greeter, "Phuong");
```

Ví dụ MethodHandle:

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
MethodType type = MethodType.methodType(String.class, String.class);
MethodHandle handle = lookup.findVirtual(Greeter.class, "greet", type);

String result = (String) handle.invokeExact(greeter, "Phuong");
```

MethodHandle không thay thế Reflection. Reflection vẫn sở hữu bài toán runtime inspection; module này chỉ so sánh đủ để người học hiểu vì sao một framework có thể “inspect bằng Reflection nhưng execute bằng MethodHandle”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-runtime-boundaries">Điều kiện tiên quyết và ranh giới của module</a>

<details>
<summary>Xem chi tiết</summary>

Nên học trước:

- Java type system, method/field/constructor và exception;
- Reflection ở mức `Class`, `Method`, `Field`;
- JVM ở mức bytecode, method descriptor và linkage mental model;
- Concurrency fundamentals trước khi suy luận sâu về visibility của `MutableCallSite` hoặc memory ordering của `VarHandle`.

Module này **sở hữu**:

- `MethodType`, `MethodHandle`, signature polymorphism;
- `MethodHandles.Lookup` và quyền truy cập;
- MethodHandle adaptation/composition;
- `CallSite`, bootstrap linkage và `invokedynamic`;
- `VarHandle` ở góc nhìn typed variable access.

Module này **không sở hữu**:

- metadata/member inspection đầy đủ → Reflection;
- constant pool, bytecode execution, JVM resolution rules đầy đủ → JVM;
- happens-before/JMM và thiết kế thuật toán lock-free → Concurrency;
- JNI/FFM/native memory → Native Interoperability;
- lịch sử feature theo từng Java release → Java Version.

Khi đọc tiếp, hãy giữ câu hỏi: **primitive này giải quyết phần nào của hành vi runtime, và phần nào phải chuyển sang module khác?**

</details>

- [Quay lại đầu trang](#back-to-top)
