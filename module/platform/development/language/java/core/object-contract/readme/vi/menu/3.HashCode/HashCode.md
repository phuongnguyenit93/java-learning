# hashCode

`equals` trả lời chính xác hai object có bằng nhau không, nhưng nếu một `HashMap` phải gọi `equals` với mọi key thì việc tìm kiếm sẽ rất tốn chi phí. `hashCode` cung cấp một tín hiệu nhanh giúp hash-based collection thu hẹp vùng cần kiểm tra.

## <a id="hashcode-contract">Hợp đồng hashCode</a>

Quy tắc quan trọng nhất:

```text
nếu a.equals(b) == true
→ a.hashCode() phải bằng b.hashCode()
```

Chiều ngược lại không đúng:

```text
cùng hash code
↛ bắt buộc equal
```

Hai object không bằng nhau hoàn toàn có thể collision và cho cùng hash mã.

Ngoài ra, khi trạng thái dùng cho equality không đổi, nhiều lần gọi `hashCode()` trong cùng execution nên cho kết quả nhất quán.

## <a id="hash-distribution">Phân bố Hash</a>

Một hash tốt nên kết hợp các field liên quan tới equality để các giá trị phổ biến phân bố tương đối đều giữa các bucket.

Mục tiêu **không phải tạo hash duy nhất cho từng object**; điều đó không thể bảo đảm trong không gian `int` hữu hạn.

```text
correctness
→ đến từ hợp đồng equals/hashCode

performance
→ bị ảnh hưởng bởi chất lượng phân bố hash
```

Collision không phải bug. Hash table được thiết kế để xử lý collision bằng cách tiếp tục kiểm tra equality trong nhóm candidate phù hợp.

## <a id="mutable-key-risk">Rủi ro của Mutable Key</a>

Nếu field tham gia `equals`/`hashCode` thay đổi sau khi object đã được đưa vào `HashMap` hoặc `HashSet`, object có thể nằm ở bucket dựa trên hash cũ nhưng lần lookup mới lại tính hash khác.

Kết quả có thể là:

```text
object vẫn đang nằm trong collection
nhưng contains/get/remove lại không tìm thấy nó
```

Vì vậy key tốt thường immutable, hoặc ít nhất các field liên quan tới equality/hash phải không thay đổi trong thời gian object đang được dùng làm key.

chương tiếp theo ghép `equals` và `hashCode` lại thành **một hợp đồng duy nhất** và xem collection phụ thuộc vào nó như thế nào.
