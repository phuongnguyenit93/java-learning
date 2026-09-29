# Retention Policy

Retention trả lời một câu hỏi về **vòng đời metadata**:

> Annotation cần tồn tại đến source processing, class file hay runtime?

Java có ba `RetentionPolicy`: `SOURCE`, `CLASS`, `RUNTIME`. Chọn retention không nên dựa trên “RUNTIME mạnh hơn”, mà dựa trên **consumer thực sự cần đọc annotation ở giai đoạn nào**.

Nếu annotation type không khai báo `@Retention`, policy mặc định là **`CLASS`**.

## <a id="retention-source">SOURCE retention</a>

```java
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface CompileNote {
}
```

`SOURCE` annotation chỉ cần tồn tại trong source/compile-time model và bị compiler loại bỏ khỏi class-file representation.

Phù hợp khi metadata phục vụ:

- compiler check;
- static analysis;
- source-oriented tooling;
- annotation processing không cần metadata tồn tại trong output class.

Mental model:

```text
.java
  @CompileNote
      ↓ compiler/processor có thể đọc
.class
  metadata không còn
      ↓
runtime reflection không thể đọc
```

Một annotation processor vẫn có thể xử lý `SOURCE` annotation vì processor chạy **trong compilation**, trước khi metadata bị loại khỏi class output.

Không nên chọn `SOURCE` nếu application cần `getAnnotation(...)` ở runtime.

## <a id="retention-class">CLASS retention</a>

`CLASS` giữ annotation trong class file nhưng JVM **không bắt buộc** phải giữ nó để standard runtime reflection truy cập.

```java
@Retention(RetentionPolicy.CLASS)
public @interface BytecodeMetadata {
}
```

Đây cũng là retention mặc định nếu không viết `@Retention`.

Use case điển hình:

- bytecode tooling;
- post-compile analysis/instrumentation;
- metadata cần đi cùng artifact `.class` nhưng không cần reflection runtime.

Mental model:

```text
.java
  annotation
      ↓
.class
  annotation vẫn được ghi
      ↓
runtime reflection
  không expose như RUNTIME annotation
```

Pitfall phổ biến là quên `@Retention(RUNTIME)` rồi thắc mắc vì sao reflection không thấy custom annotation.

## <a id="retention-runtime">RUNTIME retention</a>

`RUNTIME` giữ annotation trong class file và JVM giữ metadata để standard runtime reflection có thể đọc:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audit {
    String action();
    int level() default 1;
}
```

Ví dụ:

```java
Method method = PaymentService.class.getDeclaredMethod("transfer");
Audit audit = method.getAnnotation(Audit.class);

if (audit != null) {
    System.out.println(audit.action());
}
```

Đây là retention phù hợp khi runtime framework hoặc application code cần inspect metadata.

Nhưng:

> `RUNTIME` chỉ làm metadata **có thể được runtime đọc**; nó không tự trigger interception, validation hay dependency injection.

Một consumer vẫn phải chủ động inspect và xử lý metadata.

Với annotation trên **type use**, runtime inspection thường đi qua các API thuộc họ `AnnotatedType` thay vì chỉ các API declaration-level quen thuộc trên `AnnotatedElement`.

### Edge case — local declaration không giống type annotation

Declaration annotations trên **local-variable declaration** và formal parameter của lambda không được lưu trong binary theo retention mechanism thông thường, kể cả annotation type khai báo `CLASS` hoặc `RUNTIME`. Type annotations đặt lên **type được sử dụng** ở các context tương ứng là một kênh metadata khác và có rule lưu trữ riêng.

Điều này nhắc lại rằng “RUNTIME retention” không có nghĩa mọi source location đều trở thành runtime declaration metadata; retention luôn hoạt động cùng target/context semantics.

## <a id="retention-use-case">Chọn retention theo consumer</a>

Thay vì mặc định dùng `RUNTIME`, hãy bắt đầu từ consumer:

| Consumer cần metadata ở đâu? | Retention phù hợp |
| --- | --- |
| Chỉ source/compiler/static analysis | `SOURCE` |
| Class-file/bytecode tooling | `CLASS` |
| Reflection/runtime framework | `RUNTIME` |

Ví dụ:

```text
@GeneratedHint cho source processor
→ SOURCE có thể đủ

@BytecodeRule cho class-file transformer
→ CLASS

@Audit được interceptor/runtime code đọc
→ RUNTIME
```

### Trade-off thiết kế

Giữ metadata lâu hơn mức cần thiết không tự động tốt hơn. Nó làm contract rộng hơn và có thể khiến người dùng hiểu rằng runtime inspection là một phần của API.

Ngược lại, retention quá ngắn làm metadata biến mất trước khi consumer cần nó.

Quy tắc thực dụng:

```text
xác định consumer
→ xác định giai đoạn consumer chạy
→ chọn retention ngắn nhất vẫn đáp ứng consumer
```

### Retention và annotation processing

Annotation processor chạy trong compilation nên có thể xử lý annotation mà runtime sẽ không bao giờ thấy. Đây là khác biệt nền tảng:

```text
compile-time processing
≠
runtime reflection
```

Ta sẽ đi sâu vào processing ở chapter cuối. Chapter tiếp theo giải quyết dimension độc lập còn lại: **annotation được phép xuất hiện ở đâu?**
