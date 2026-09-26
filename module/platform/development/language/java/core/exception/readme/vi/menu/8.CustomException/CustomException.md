# Custom Exception

Không phải lỗi nào cũng cần một exception class mới. Custom exception chỉ có giá trị khi kiểu mới **thêm ý nghĩa**, tạo ranh giới abstraction rõ hơn hoặc giúp bên gọi xử lý một nhóm lỗi theo hợp đồng có chủ ý.

## <a id="custom-exception-purpose">Khi nào cần Custom Exception?</a>

Custom exception hữu ích khi nó:

- diễn đạt lỗi của domain hoặc ứng dụng rõ hơn;
- gom các exception tầng thấp thành ngôn ngữ của tầng hiện tại;
- cho phép bên gọi catch một nhóm lỗi có cùng ngữ nghĩa;
- mang ngữ cảnh có cấu trúc mà message chung chung không đủ.

Không nên tạo `SomethingException` chỉ để đổi tên một `IllegalArgumentException` mà không thêm hợp đồng hay ngữ cảnh.

## <a id="exception-context">Giữ ngữ cảnh và Cause</a>

Custom exception nên giữ đủ ngữ cảnh để chẩn đoán:

```java
throw new OrderLoadException(orderId, ex);
```

Ví dụ có thể giữ:

- `orderId`;
- thao tác đang thực hiện;
- cause gốc.

Nhưng tránh đưa secret, token hoặc dữ liệu nhạy cảm vào message/ngữ cảnh nếu chúng có thể xuất hiện trong log.

## <a id="exception-hierarchy-design">Thiết kế Exception Hierarchy</a>

Một hệ phân cấp nhỏ, có ý nghĩa thường tốt hơn hàng chục class chỉ khác tên.

```text
OrderException
├── OrderNotFoundException
└── OrderValidationException
```

Hệ phân cấp nên phục vụ **cách bên gọi muốn xử lý lỗi**, không chỉ phản chiếu mọi chi tiết triển khai nội bộ.

Chương cuối đặt toàn bộ cơ chế đã học vào ngữ cảnh thiết kế ứng dụng: catch ở đâu, chuyển đổi exception ở đâu, log ở đâu và khi nào nên để exception tiếp tục truyền lên trên.
