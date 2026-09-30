# StringBuffer

`StringBuffer` có API gần giống `StringBuilder` nhưng nhiều phương thức được `synchronized`. Điều đó tạo một số bảo đảm ở mức từng thao tác, nhưng không tự động giải quyết mọi bài toán đồng thời.

## <a id="buffer-synchronization">Cơ chế đồng bộ trong StringBuffer</a>

Các phương thức như `append` được đồng bộ hóa theo đối tượng `StringBuffer`, giúp tránh một số tranh chấp ở mức từng lời gọi phương thức khi nhiều luồng cùng truy cập.

Đánh đổi là chi phí đồng bộ hóa và tranh chấp tài nguyên.

Nếu bộ đệm chỉ được dùng trong phạm vi một luồng hoặc một phương thức, `StringBuilder` thường đơn giản và nhanh hơn.

### Cơ chế đồng bộ đảm bảo điều gì?

Ở mức khái niệm:

```text
luồng A gọi append(...)
        │
        └── synchronized trên cùng đối tượng StringBuffer

luồng B gọi append(...)
        │
        └── phối hợp qua cùng monitor cho lời gọi phương thức đó
```

Điều này khác `StringBuilder`, nơi API không cung cấp cơ chế đồng bộ hóa nội bộ tương tự.

Nhưng “có phương thức synchronized” không có nghĩa mọi chuỗi nhiều bước đều nguyên tử (atomic).

## <a id="builder-vs-buffer">StringBuilder hay StringBuffer?</a>

Quy tắc lựa chọn nhanh:

```text
không cần bộ đệm có thể thay đổi dùng chung giữa nhiều luồng
→ StringBuilder

thật sự cần bộ đệm ký tự có thể thay đổi với đồng bộ hóa nội bộ
→ cân nhắc StringBuffer
```

Trong nhiều thiết kế tốt, thay vì chia sẻ một bộ đệm có thể thay đổi giữa nhiều luồng, mỗi luồng xây kết quả riêng rồi kết hợp ở một ranh giới rõ ràng.

So sánh thực dụng:

```text
xây chuỗi cục bộ trong một luồng
→ StringBuilder

API cũ yêu cầu StringBuffer
→ StringBuffer

bộ đệm dùng chung thật sự cần đồng bộ hóa ở mức từng phương thức
→ cân nhắc StringBuffer, nhưng phải rà soát toàn bộ luồng xử lý
```

Không chọn `StringBuffer` chỉ vì “an toàn luồng nghe an toàn hơn”. Đồng bộ hóa có chi phí và trạng thái có thể thay đổi dùng chung làm thiết kế phức tạp hơn.

## <a id="thread-safety-boundary">Giới hạn của an toàn luồng</a>

Hai phương thức synchronized riêng lẻ không làm một chuỗi nhiều bước trở thành nguyên tử.

Ví dụ logic kiểu:

```text
đọc độ dài
→ quyết định dựa trên độ dài
→ append
```

có thể bị luồng khác xen vào giữa các bước nếu bên gọi không có phạm vi đồng bộ hóa lớn hơn.

Ví dụ:

```java
if (buffer.length() < 100) {
    buffer.append(part);
}
```

`length()` và `append()` có thể được đồng bộ hóa riêng, nhưng chuỗi kiểm tra-rồi-thực-hiện ở bên gọi gồm **hai thao tác**. Luồng khác có thể thay đổi bộ đệm giữa chúng.

Nếu điều kiện bất biến cần bao phủ:

```text
đọc trạng thái
→ quyết định
→ thay đổi
```

thì phạm vi đồng bộ hóa phải bao phủ toàn bộ chuỗi thao tác hoặc thiết kế nên tránh bộ đệm có thể thay đổi dùng chung.

An toàn luồng phải được đánh giá ở **toàn bộ thao tác nghiệp vụ cần bảo vệ**, không chỉ nhìn annotation hoặc `synchronized` của từng phương thức.

Các cơ chế nguyên thủy của lập trình đồng thời, khóa và Java Memory Model không thuộc chương này; phần chuyên sâu đó thuộc mô-đun Concurrency.

Chương tiếp theo quay lại String Pool và giải thích việc chuẩn hóa tham chiếu có chủ đích bằng `String.intern()`.
