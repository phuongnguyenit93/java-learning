# Thiết kế Exception

Biết cú pháp `try/catch` chưa đủ để thiết kế luồng lỗi tốt. Câu hỏi quan trọng hơn là: **tầng nào có đủ ngữ cảnh và trách nhiệm để làm gì với lỗi?**

## <a id="exception-boundaries">Exception Translation</a>

Exception ở tầng thấp thường dùng ngôn ngữ của cách triển khai:

```text
SQLException
IOException
SocketTimeoutException
```

Tầng cao hơn có thể cần ngôn ngữ phù hợp với domain hoặc ứng dụng hơn:

```text
OrderRepositoryException
DocumentLoadException
PaymentUnavailableException
```

Chuyển đổi exception tại ranh giới giúp bên gọi không phụ thuộc vào chi tiết hạ tầng, nhưng phải giữ `cause` để không làm mất chuỗi chẩn đoán.

## <a id="do-not-swallow">Không nuốt lỗi</a>

Anti-pattern:

```java
try {
    run();
} catch (Exception ex) {
    // bỏ qua
}
```

Nếu lỗi bị bỏ mà trạng thái/thao tác vẫn được xem là thành công, hệ thống trở nên khó đoán và khó debug.

Nếu thật sự muốn bỏ qua một lỗi cụ thể, mã nguồn nên thể hiện rõ lý do và phạm vi thay vì catch rộng rồi để trống.

## <a id="logging-boundary">Ranh giới Logging</a>

Một exception bị log ở mọi tầng rồi rethrow sẽ tạo nhiều bản ghi giống nhau cho cùng một lỗi.

Heuristic hữu ích:

```text
tầng kết thúc hoặc xử lý cuối request/job
→ thường là nơi log đầy đủ một lần

tầng chỉ chuyển đổi hoặc rethrow
→ thường không cần log lại nếu ngữ cảnh đã được giữ
```

Đây không phải luật tuyệt đối, nhưng giúp tránh log dư thừa và ghi nhận cùng một lỗi nhiều lần.

## <a id="exception-as-control-flow">Exception và Control Flow</a>

Exception phù hợp với **trường hợp kết thúc bất thường**, không nên thay thế những nhánh bình thường có thể kiểm tra rõ ràng.

Ví dụ dùng `NumberFormatException` để thử parse đầu vào có thể hợp lý ở một ranh giới nhỏ, nhưng dùng exception liên tục để điều khiển vòng lặp hoặc trạng thái bình thường thường làm mã khó đọc và tốn chi phí không cần thiết.

## <a id="cleanup-and-recovery">Dọn dẹp, khôi phục và truyền lỗi</a>

Ba hành động này khác nhau:

```text
dọn dẹp
→ giải phóng tài nguyên / hoàn tất công việc bắt buộc

khôi phục
→ có chiến lược thực sự để thao tác tiếp tục hoặc thử phương án khác

truyền tiếp
→ tầng hiện tại không đủ trách nhiệm để xử lý, chuyển lỗi lên trên
```

Đừng catch chỉ vì “ở đây có exception”. Chỉ catch khi tầng hiện tại có việc có ý nghĩa để làm: khôi phục, chuyển đổi lỗi, bổ sung ngữ cảnh, dọn dẹp hoặc kết thúc luồng có chủ ý.

Sau module này, mô hình tư duy nên là:

```text
lỗi xảy ra
→ được throw
→ truyền qua call stack
→ có thể được xử lý hoặc chuyển đổi
→ tài nguyên vẫn phải được dọn dẹp
→ nguyên nhân gốc không được làm mất
→ logging/khôi phục diễn ra ở ranh giới có trách nhiệm
```
