# StringBuffer

`StringBuffer` có API gần giống `StringBuilder` nhưng nhiều method được synchronized. Điều đó tạo một số guarantees ở mức từng thao tác, nhưng không tự động giải quyết mọi bài toán concurrency.

## <a id="buffer-synchronization">Synchronization trong StringBuffer</a>

Các method như `append` được synchronization theo object buffer, giúp tránh một số race ở mức method call riêng lẻ khi nhiều thread cùng truy cập.

Đánh đổi là synchronization overhead và contention.

Nếu builder chỉ được dùng trong một thread/local scope, `StringBuilder` thường đơn giản và nhanh hơn.

### WHAT synchronization đảm bảo?

Ở mức khái niệm:

```text
thread A gọi append(...)
        │
        └── synchronized trên StringBuffer instance

thread B gọi append(...)
        │
        └── phối hợp qua cùng monitor cho method call đó
```

Điều này khác `StringBuilder`, nơi API không cung cấp synchronization nội bộ tương tự.

Nhưng “có synchronized method” không có nghĩa mọi multi-step workflow là atomic.

## <a id="builder-vs-buffer">StringBuilder hay StringBuffer?</a>

Heuristic:

```text
không cần shared mutable buffer giữa nhiều thread
→ StringBuilder

thật sự cần synchronized mutable character buffer
→ cân nhắc StringBuffer
```

Trong nhiều thiết kế tốt, thay vì share một mutable buffer giữa threads, mỗi thread xây kết quả riêng rồi combine ở ranh giới rõ ràng.

Comparison thực dụng:

```text
single-thread / local construction
→ StringBuilder

legacy API yêu cầu StringBuffer
→ StringBuffer

shared mutable buffer thật sự cần method-level synchronization
→ cân nhắc StringBuffer, nhưng review toàn workflow
```

Không chọn `StringBuffer` chỉ vì “thread-safe nghe an toàn hơn”. Synchronization có cost và shared mutable state làm design phức tạp hơn.

## <a id="thread-safety-boundary">Giới hạn Thread-safety</a>

Hai method synchronized riêng lẻ không làm một chuỗi nhiều bước trở thành atomic.

Ví dụ logic kiểu:

```text
đọc length
→ quyết định dựa trên length
→ append
```

có thể bị thread khác xen vào giữa các bước nếu bên gọi không có synchronization lớn hơn.

Ví dụ:

```java
if (buffer.length() < 100) {
    buffer.append(part);
}
```

`length()` và `append()` có thể synchronized riêng, nhưng check-then-act ở caller gồm **hai operation**. Thread khác có thể thay đổi buffer giữa chúng.

Nếu invariant cần bao phủ:

```text
read state
→ decide
→ mutate
```

thì synchronization boundary phải bao phủ toàn sequence hoặc design nên tránh shared mutable buffer.

Thread-safety phải được đánh giá ở **thao tác ranh giới của use case**, không chỉ nhìn annotation/synchronized của từng method.

Concurrency primitives, locks và Java Memory Model không thuộc chapter này; module concurrency sở hữu phần sâu đó.

chương tiếp theo quay lại String pool và giải thích explicit canonicalization bằng `String.intern()`.
