# Exception là gì và vì sao Java cần nó?

## <a id="exception-purpose">Exception là gì?</a>

Trong luồng thực thi bình thường, ta giả định một phương thức sẽ hoàn thành công việc rồi trả kết quả. Nhưng chương trình thực tế luôn có khả năng **không thể tiếp tục theo đường thành công**: dữ liệu đầu vào sai, file không tồn tại, kết nối lỗi, tài nguyên đóng thất bại hoặc một bất biến (invariant) bị phá vỡ.

Java dùng ngoại lệ (exception) để biểu diễn **sự kết thúc bất thường của luồng thực thi**. Thay vì bắt mọi phương thức trả về một “mã lỗi”, Java cho phép thông tin về sự cố mang theo ngữ cảnh và lan truyền ngược qua chuỗi lời gọi cho tới tầng có đủ trách nhiệm để xử lý.

Một ngoại lệ cần được hiểu đồng thời theo hai mặt:

```text
đối tượng Throwable
→ mang kiểu, thông điệp, dấu vết ngăn xếp (stack trace), nguyên nhân (cause) và thông tin chẩn đoán khác

cơ chế làm thay đổi luồng điều khiển
→ khi được ném bằng `throw`, đường chạy bình thường dừng và Java tìm nơi xử lý phù hợp
```

Nếu chỉ dùng `boolean`, `null`, mã trạng thái hoặc một giá trị trả về đặc biệt, mỗi bên gọi phải tự nhớ kiểm tra kết quả và tự truyền thông tin lỗi lên tầng trên. Cách đó vẫn phù hợp với một số API, nhưng khi sự cố cần đi qua nhiều tầng thì kết quả bình thường và lỗi dễ bị trộn vào cùng một kênh, thông tin nguyên nhân dễ mất và bên gọi có thể bỏ quên việc kiểm tra. Ngoại lệ cho Java một **kênh hoàn thành bất thường riêng**, có thể truyền qua ngăn xếp lời gọi mà vẫn giữ kiểu lỗi và nguyên nhân.

Vì vậy ngoại lệ có nhiều vai trò trong thiết kế chương trình:

```text
hợp đồng API
→ mô tả loại lỗi mà bên gọi cần biết; checked exception có thể xuất hiện trong khai báo `throws`

lan truyền qua các tầng
→ đưa lỗi từ nơi phát sinh tới tầng có đủ trách nhiệm quyết định

dọn dẹp
→ cho phép `finally` / try-with-resources dọn tài nguyên khi luồng thành công bị gián đoạn

chẩn đoán
→ giữ kiểu, thông điệp, stack trace, cause và Suppressed Exception

chuyển đổi / phục hồi
→ tầng biên có thể bọc lỗi kỹ thuật bằng ngôn ngữ miền nghiệp vụ, thử lại, dùng phương án dự phòng hoặc kết thúc yêu cầu phù hợp
```

Ngoại lệ không nên trở thành cách viết **luồng điều khiển bình thường** cho các nhánh dự kiến xảy ra thường xuyên và có thể biểu diễn rõ hơn bằng điều kiện, giá trị trả về hoặc một kiểu kết quả phù hợp. Việc ném rồi bắt ngoại lệ chỉ để điều khiển vòng lặp hay chọn nhánh làm luồng thành công khó đọc và che mất ý nghĩa thật của cơ chế xử lý lỗi.

Trong toàn module, ta sẽ dùng một câu chuyện lặp lại:

```text
bên gọi
  ↓
OrderService
  ↓
OrderRepository / file I/O
  ↓
lỗi
```

Tầng thấp có thể phát sinh lỗi kỹ thuật; tầng cao quyết định phục hồi, chuyển đổi ngoại lệ, ghi log hay để lỗi tiếp tục lan truyền.

Kiến thức trong module đi theo ROADMAP đã chốt:

```text
Exception là gì và vì sao Java cần nó?
        ↓
Throwable và cách Java phân loại lỗi
        ↓
Checked Exception và Unchecked Exception
        ↓
throw, throws và sự lan truyền Exception
        ↓
Xử lý Exception với try/catch/finally
        ↓
Quản lý tài nguyên an toàn với try-with-resources
        ↓
Ngoại lệ tùy chỉnh (Custom Exception)
        ↓
Ranh giới xử lý Exception trong ứng dụng
        ↓
Tổng hợp mô hình xử lý Exception
```

Mục tiêu cuối cùng không phải là nhớ thật nhiều tên ngoại lệ, mà là hiểu **lỗi di chuyển qua chương trình như thế nào và tầng nào phải chịu trách nhiệm với nó**.
