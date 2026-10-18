<a id="back-to-top"></a>

# Lựa chọn và tổng hợp Dynamic Runtime

## Menu
- [Chọn lời gọi trực tiếp, Reflection hay MethodHandle](#choose-invocation-mechanism)
- [Khi nào dùng VarHandle?](#choose-variable-access-mechanism)
- [Mô hình lỗi của Dynamic Runtime](#dynamic-runtime-failure-model)
- [Luồng dynamic linkage từ đầu đến cuối](#dynamic-linkage-end-to-end)
- [Trường hợp sử dụng trong runtime, framework và infrastructure code](#runtime-framework-use-cases)
- [Chuyển tiếp sang JVM, Concurrency và Native Interoperability](#dynamic-runtime-handoffs)

## <a id="choose-invocation-mechanism">Chọn lời gọi trực tiếp, Reflection hay MethodHandle</a>

<details>
<summary>Xem chi tiết</summary>

Quy tắc lựa chọn đơn giản:

```text
Code biết rõ target và gọi bình thường được?
→ direct call

Cần inspect metadata/member/annotation runtime?
→ Reflection

Cần executable reference có kiểu,
chuỗi handle có thể adapt/compose,
hoặc runtime linkage primitive?
→ MethodHandle / java.lang.invoke
```

Không dùng MethodHandle chỉ để tránh viết một lời gọi method bình thường:

```java
service.process(request);
```

vẫn tốt hơn một chuỗi lookup + invoke nếu không có yêu cầu runtime thật.

MethodHandle thường có giá trị trong framework, runtime, serializer, dynamic-language support, FFM plumbing hoặc generated dispatch layer — nơi việc biểu diễn hành vi thành executable handle là một phần của thiết kế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="choose-variable-access-mechanism">Khi nào dùng VarHandle?</a>

<details>
<summary>Xem chi tiết</summary>

Dùng VarHandle khi cần variable abstraction + access semantics:

```text
ordinary field access đủ?
→ field read/write trực tiếp

runtime-selected field/array coordinate
hoặc explicit access mode/CAS?
→ VarHandle
```

Ví dụ phù hợp:

- infrastructure library cần field access handle đã resolve một lần;
- array/byte-buffer view cần typed access;
- cách triển khai concurrency primitive cần acquire/release/CAS semantics.

Ví dụ không phù hợp:

- business entity có getter/setter bình thường;
- dùng VarHandle để “tăng hiệu năng” mà chưa có benchmark;
- dùng atomic access để che một thiết kế shared state không rõ quyền sở hữu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-runtime-failure-model">Mô hình lỗi của Dynamic Runtime</a>

<details>
<summary>Xem chi tiết</summary>

Có thể nhóm lỗi theo vòng đời:

```text
LOOKUP
NoSuchMethodException / NoSuchFieldException
IllegalAccessException

TYPE / ADAPTATION
WrongMethodTypeException
ClassCastException trong conversion path phù hợp

CALL SITE TARGET UPDATE
WrongMethodTypeException
→ setTarget nhận MethodHandle có type khác site.type()

BOOTSTRAP / LINKAGE
BootstrapMethodError
→ bootstrap non-Error failure hoặc CallSite null/sai type
Error subclass
→ propagate trực tiếp từ bootstrap/resolution

CONCURRENCY / VISIBILITY
stale MutableCallSite target
VarHandle ordering misuse
```

Debug nên đi theo luồng thay vì thử cast ngẫu nhiên:

1. member identity đúng chưa;
2. quyền của lookup đã đúng chưa;
3. current `MethodType` là gì;
4. adapter thay type thế nào;
5. CallSite type có giữ invariant không;
6. nếu có nhiều thread, hợp đồng visibility đang dùng là gì.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-linkage-end-to-end">Luồng dynamic linkage từ đầu đến cuối</a>

<details>
<summary>Xem chi tiết</summary>

End-to-end dynamic linkage:

```text
bên gọi
  ↓
invokedynamic descriptor
  ↓
bootstrap(
    Lookup,
    name,
    MethodType,
    static args
)
  ↓
resolve/create MethodHandle target
  ↓
adapt target to exact call-site type
  ↓
create CallSite
  ↓
JVM links instruction to CallSite
  ↓
invoke current target
```

Nếu CallSite là mutable:

```text
same linked CallSite
        ↓
target changes
        ↓
future invocations may observe new target
```

Đây là phần tổng hợp quan trọng nhất của nhánh invocation/linkage.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-framework-use-cases">Trường hợp sử dụng trong runtime, framework và infrastructure code</a>

<details>
<summary>Xem chi tiết</summary>

Các nơi thường sử dụng những cơ chế này:

- lambda metafactory / language runtime;
- string concatenation runtime;
- ORM/serializer/framework dispatch optimization;
- dependency injection/reflection replacement ở hot path;
- proxy/adaptor infrastructure;
- Foreign Function & Memory plumbing sử dụng MethodHandle;
- cách triển khai dynamic language.

Nhưng “framework dùng MethodHandle” không có nghĩa application code cũng nên dùng trực tiếp.

Một kiến trúc tốt thường giấu `java.lang.invoke` sau một abstraction:

```text
application code
→ hợp đồng của framework
→ precomputed MethodHandle/VarHandle pipeline
→ runtime execution
```

Như vậy độ phức tạp nằm ở infrastructure layer, nơi các cơ chế này thực sự tạo giá trị.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-runtime-handoffs">Chuyển tiếp sang JVM, Concurrency và Native Interoperability</a>

<details>
<summary>Xem chi tiết</summary>

Các handoff chính:

| Khi câu hỏi trở thành... | Module tiếp theo |
| --- | --- |
| Constant pool, resolution, bytecode execution/JIT hoạt động thế nào? | JVM |
| `Class`, `Method`, annotation inspection hoạt động thế nào? | Reflection |
| acquire/release/happens-before/CAS algorithm có đúng không? | Concurrency |
| MethodHandle gọi native function/off-heap memory thế nào? | Native Interoperability |
| Feature này xuất hiện/thay đổi ở Java release nào? | Java Version |
| Agent rewrite bytecode/runtime class thế nào? | Instrumentation |

Kết thúc module, người học nên có thể đọc một pipeline `Lookup → MethodHandle → adaptation → CallSite → invokedynamic`, đồng thời nhận ra `VarHandle` là nhánh variable-access riêng.

Điều quan trọng hơn việc nhớ tên API là biết **đâu là hợp đồng kiểu, đâu là quyền truy cập, đâu là object linkage và đâu là ranh giới memory semantics**.

</details>

- [Quay lại đầu trang](#back-to-top)
