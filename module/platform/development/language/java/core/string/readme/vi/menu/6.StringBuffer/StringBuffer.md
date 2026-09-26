# StringBuffer

`StringBuffer` có API gần giống `StringBuilder` nhưng nhiều method được synchronized. Điều đó tạo một số guarantees ở mức từng thao tác, nhưng không tự động giải quyết mọi bài toán concurrency.

## <a id="buffer-synchronization">Synchronization trong StringBuffer</a>

Các method như `append` được synchronization theo object buffer, giúp tránh một số race ở mức method call riêng lẻ khi nhiều thread cùng truy cập.

Đánh đổi là synchronization overhead và contention.

Nếu builder chỉ được dùng trong một thread/local scope, `StringBuilder` thường đơn giản và nhanh hơn.

## <a id="builder-vs-buffer">StringBuilder hay StringBuffer?</a>

Heuristic:

```text
không cần shared mutable buffer giữa nhiều thread
→ StringBuilder

thật sự cần synchronized mutable character buffer
→ cân nhắc StringBuffer
```

Trong nhiều thiết kế tốt, thay vì share một mutable buffer giữa threads, mỗi thread xây kết quả riêng rồi combine ở ranh giới rõ ràng.

## <a id="thread-safety-boundary">Giới hạn Thread-safety</a>

Hai method synchronized riêng lẻ không làm một chuỗi nhiều bước trở thành atomic.

Ví dụ logic kiểu:

```text
đọc length
→ quyết định dựa trên length
→ append
```

có thể bị thread khác xen vào giữa các bước nếu bên gọi không có synchronization lớn hơn.

Thread-safety phải được đánh giá ở **thao tác ranh giới của use case**, không chỉ nhìn annotation/synchronized của từng method.

chương tiếp theo quay lại String pool và giải thích explicit canonicalization bằng `String.intern()`.
