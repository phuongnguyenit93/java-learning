# Tổng hợp mô hình xử lý Exception

## <a id="exception-synthesis">Theo dõi một lỗi từ đầu đến cuối</a>

Đến đây, các cơ chế riêng lẻ của module được nối lại thành một mô hình có hai lớp:

```text
khi thiết kế API / tại thời điểm biên dịch
→ chọn kiểu exception và checked/unchecked contract phù hợp
→ checked exception có thể buộc bên gọi bắt hoặc khai báo

khi chương trình chạy
→ exception được ném, bắt, lan truyền, dọn dẹp, chuyển đổi hoặc phục hồi
```

Luồng khi chương trình chạy (runtime) có thể theo dõi như sau:

```text
lỗi phát sinh
        ↓
throw
        ↓
nếu chưa được xử lý: lan truyền + stack unwinding
        ↓
khi rời từng phạm vi: `finally` / try-with-resources vẫn thực hiện dọn dẹp
        ↓
đến tầng có trách nhiệm:
catch / phục hồi / thử lại / chuyển đổi / kết thúc
        ↓
nếu bọc Exception: giữ nguyên nhân (cause)
nếu `close()` trong try-with-resources cũng lỗi: giữ suppressed exception
        ↓
ghi log/quan sát tại ranh giới phù hợp
```

### Ví dụ xuyên suốt

Giả sử `OrderRepository` sở hữu một tài nguyên I/O, đọc dữ liệu và phát sinh `IOException`. Theo mô hình của toàn module:

```text
OrderRepository
→ thao tác đọc phát sinh IOException
→ try-with-resources đóng tài nguyên trước khi IOException thoát khỏi repository
→ nếu close() cũng lỗi, lỗi đóng được giữ dưới dạng suppressed exception của IOException
        ↓
IOException lan truyền tới OrderService

OrderService
→ chưa thể phục hồi tại đây
→ chuyển đổi thành OrderLoadException
→ giữ IOException làm cause

Controller / ranh giới ứng dụng
→ nhận OrderLoadException
→ quyết định ánh xạ kết quả cho bên gọi
→ ghi log một lần với đầy đủ chuỗi nguyên nhân
```

Mỗi tầng chỉ làm phần việc thuộc trách nhiệm của nó: repository quản lý tài nguyên và báo lỗi kỹ thuật; try-with-resources hoàn tất dọn dẹp trước khi lỗi rời phạm vi sở hữu; service đổi ngôn ngữ lỗi khi mức trừu tượng thay đổi; ranh giới cuối mới quyết định kết quả và ghi nhận lỗi.

Nếu `OrderService` chỉ bọc `IOException` thành một exception khác nhưng không bổ sung ý nghĩa, ngữ cảnh hoặc hợp đồng xử lý mới, lớp bọc đó không tạo thêm giá trị.

Quyết định bọc có thể tóm tắt:

```text
mức trừu tượng không đổi
và không có ngữ cảnh/chính sách xử lý mới
→ ưu tiên ném lại hoặc tiếp tục lan truyền

mức trừu tượng thay đổi
hoặc cần thêm ngữ cảnh có cấu trúc / nhóm xử lý mới
→ bọc bằng exception phù hợp + giữ cause
```

Mục tiêu của thiết kế Exception không phải là “catch càng nhiều càng an toàn”. Mục tiêu là **đặt chính sách xử lý lỗi đúng tầng, bảo toàn thông tin và không làm sai ý nghĩa của trạng thái thành công/thất bại**.
