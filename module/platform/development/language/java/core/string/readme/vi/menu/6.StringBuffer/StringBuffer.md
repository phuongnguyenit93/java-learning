# StringBuffer

## <a id="buffer-synchronization">Synchronization trong StringBuffer</a>
`StringBuffer` là mutable character sequence giống `StringBuilder` nhưng public operation đọc/ghi được synchronized. Điều đó cung cấp per-method mutual exclusion cho một buffer instance và thường khiến nó chậm hơn builder khi không contention.

## <a id="builder-vs-buffer">Trade-off StringBuilder và StringBuffer</a>
Dùng `StringBuilder` cho text construction local/single-thread-confined. Chọn `StringBuffer` khi synchronized per-operation contract thật sự khớp sharing requirement. Thường design tốt hơn là không share mutable text accumulator giữa thread.

## <a id="thread-safety-boundary">Synchronized method không đảm bảo compound action atomic</a>
Nhiều call synchronized riêng lẻ không tự trở thành một atomic compound action. Check-then-append vẫn có thể interleave nếu không có external synchronization bảo vệ invariant. Concurrency đầy đủ thuộc module concurrency; boundary cần nhớ là method-level synchronization khác multi-step atomicity.
