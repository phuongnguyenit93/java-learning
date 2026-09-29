# Suppressed Exception

Cleanup cũng có thể fail. Đây là bài toán khó của resource management:

```text
operation chính đã thất bại
        +
cleanup cũng thất bại
        ↓
không được làm mất failure nào
```

`try-with-resources` giải quyết việc này bằng khái niệm **primary exception** và **suppressed exception**.

## <a id="primary-vs-suppressed">Primary và Suppressed Exception</a>

Giả sử body throw A:

```java
try (DemoResource resource = new DemoResource()) {
    throw new IllegalArgumentException("body failed");
}
```

và `resource.close()` cũng throw B.

TWR giữ:

```text
A
→ primary exception

B
→ suppressed exception gắn vào A
```

### VÌ SAO A ĐƯỢC ƯU TIÊN?

Operation đã thất bại trước vì A. Nếu cleanup B thay thế A, hệ thống sẽ báo:

```text
"close failed"
```

nhưng mất failure giải thích **vì sao operation chính đã thất bại**.

Suppression cho phép giữ causal picture đầy đủ:

```text
primary
→ failure quyết định outcome chính

suppressed
→ failure phụ xảy ra trong cleanup khi đang xử lý outcome đó
```

### Ví dụ quan sát được

```java
final class DemoResource implements AutoCloseable {
    @Override
    public void close() {
        throw new IllegalStateException("close failed");
    }
}

try (DemoResource resource = new DemoResource()) {
    throw new IllegalArgumentException("body failed");
}
```

Nếu catch bên ngoài:

```java
catch (Exception ex) {
    System.out.println(ex.getMessage());

    for (Throwable suppressed : ex.getSuppressed()) {
        System.out.println("suppressed: " + suppressed.getMessage());
    }
}
```

kết quả logic là:

```text
primary   = IllegalArgumentException("body failed")
suppressed[0]
          = IllegalStateException("close failed")
```

Đây là evidence trực tiếp cho lý do TWR an toàn hơn manual cleanup.

## <a id="get-suppressed">Đọc Suppressed Exception</a>

API:

```java
Throwable[] suppressed = ex.getSuppressed();
```

Một exception có thể có **nhiều** suppressed failures.

Ví dụ ba resource:

```text
A mở trước
B mở sau
C mở cuối
```

Khi body đã fail, close diễn ra:

```text
C → B → A
```

Nếu `C.close()` và `A.close()` cùng fail:

```text
body failure
→ primary

C close failure
→ suppressed

A close failure
→ suppressed
```

Thứ tự trong suppressed diagnostics phản ánh các cleanup failures được ghi nhận trong quá trình close.

### Suppressed khác Cause

Hai khái niệm trả lời hai câu hỏi khác nhau:

```text
cause
→ failure nào dẫn tới exception hiện tại theo causal/abstraction chain?

suppressed
→ failure phụ nào xảy ra trong lúc một failure khác đã là outcome chính?
```

Ví dụ:

```text
OrderLoadException
cause
└── IOException

IOException
suppressed
└── close failure
```

Một throwable có thể vừa có `cause` vừa có suppressed exceptions.

### Business logic có nên phụ thuộc vào suppressed?

Thông thường không.

Suppressed exception chủ yếu là **diagnostic context**. Business policy nên dựa vào contract/type/context rõ ràng hơn, thay vì “nếu có đúng 2 suppressed errors thì làm X”.

## <a id="close-failure">Lỗi khi Close</a>

Có ba case cần phân biệt.

### Case 1 — Body thành công, close fail

```text
body success
→ close throw B
→ B trở thành primary exception
```

Operation cuối cùng vẫn fail vì resource không được đóng thành công theo contract.

### Case 2 — Body fail, close fail

```text
body throw A
→ close throw B
→ A primary
→ B suppressed trên A
```

### Case 3 — Nhiều close cùng fail, body thành công

Với:

```text
A mở trước
B mở sau
```

close order:

```text
B → A
```

Nếu cả hai `close()` throw:

```text
B close failure
→ primary close failure

A close failure
→ suppressed trên B failure
```

Failure đầu tiên xuất hiện trong quá trình close trở thành outcome chính; các close failure tiếp theo được giữ dưới dạng suppressed.

### Manual cleanup dễ sai ở đâu?

```java
try {
    use();
} finally {
    close(); // nếu throw, có thể che exception từ use()
}
```

Để tự tái tạo đúng semantics của TWR với nhiều resource, partial initialization và nhiều close failure là không đơn giản.

Đó là lý do suppression không phải một chi tiết “lạ” của API; nó tồn tại để giải quyết một bài toán preservation rất cụ thể.

### GHI NHỚ

```text
cause
→ chuỗi nguyên nhân / translation

suppressed
→ failure phụ bị giữ lại để không che primary failure

try-with-resources
→ tự quản lý suppression cho cleanup failures
```

Chương tiếp theo chuyển từ runtime mechanics sang API/domain design: khi nào một kiểu custom exception thực sự thêm meaning?