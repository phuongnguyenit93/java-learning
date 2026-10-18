# Ngoại lệ tùy chỉnh (Custom Exception)

Không phải lỗi nào cũng cần một lớp exception mới. Ngoại lệ tùy chỉnh chỉ có giá trị khi kiểu mới **bổ sung ý nghĩa**, tạo ranh giới trừu tượng rõ hơn hoặc cho bên gọi một nhóm lỗi có cách xử lý riêng.

Câu hỏi không phải:

```text
"Có thể tạo exception riêng không?"
```

mà là:

```text
"Bên gọi được lợi gì khi lỗi này có một kiểu riêng?"
```

## <a id="custom-exception-purpose">Khi nào cần ngoại lệ tùy chỉnh?</a>

Ngoại lệ tùy chỉnh hữu ích khi nó:

- diễn đạt lỗi bằng ngôn ngữ của miền nghiệp vụ/ứng dụng;
- che chi tiết triển khai của tầng thấp;
- cho phép bên gọi bắt một nhóm lỗi có cùng chính sách xử lý;
- mang ngữ cảnh có cấu trúc mà thông điệp thuần chuỗi không đủ;
- tạo một hợp đồng ổn định hơn so với exception của thư viện/hạ tầng phía dưới.

Ví dụ tầng repository có thể thay đổi từ file sang cơ sở dữ liệu theo thời gian. Tầng service không muốn bên gọi phụ thuộc vào `IOException` hay `SQLException`.

```java
try {
    return repository.load(orderId);
} catch (IOException ex) {
    throw new OrderLoadException(orderId, ex);
}
```

Bên gọi nhìn thấy:

```text
OrderLoadException
```

thay vì phải biết tầng repository đang dùng file.

### Khi nào KHÔNG cần?

Không nên tạo:

```java
class InvalidArgumentForOrderException extends RuntimeException {
}
```

chỉ để thay cho `IllegalArgumentException` nếu kiểu mới không bổ sung:

- ý nghĩa nghiệp vụ;
- ngữ cảnh có cấu trúc;
- chính sách xử lý;
- ranh giới trừu tượng.

Nhiều lớp exception chỉ khác tên làm API khó đọc hơn mà không tăng khả năng biểu đạt.

### Checked hay unchecked cho ngoại lệ tùy chỉnh?

Cả hai đều hợp lệ.

Unchecked:

```java
class OrderLoadException extends RuntimeException {
}
```

Checked:

```java
class OrderLoadException extends Exception {
}
```

Lựa chọn quay lại câu hỏi của chương Checked/Unchecked:

```text
Có muốn trình biên dịch buộc mọi bên gọi bắt hoặc khai báo lỗi này không?
```

Đừng chọn chỉ vì “custom exception thì phải extends RuntimeException” hoặc “exception nghiệp vụ thì phải extends Exception”.

## <a id="exception-context">Giữ ngữ cảnh và nguyên nhân (Cause)</a>

Một ngoại lệ tùy chỉnh tốt thường có constructor giữ nguyên nhân (cause):

```java
public final class OrderLoadException extends RuntimeException {

    private final long orderId;

    public OrderLoadException(long orderId, Throwable cause) {
        super("Cannot load order " + orderId, cause);
        this.orderId = orderId;
    }

    public long getOrderId() {
        return orderId;
    }
}
```

Cách dùng:

```java
catch (IOException ex) {
    throw new OrderLoadException(orderId, ex);
}
```

Exception giờ có:

```text
kiểu
→ OrderLoadException

ngữ cảnh có cấu trúc
→ orderId

thông điệp
→ mô tả phục vụ chẩn đoán

cause
→ IOException gốc
```

### Thông điệp không thay thế ngữ cảnh có cấu trúc

Nếu mã nguồn cần `orderId`, đừng bắt bên gọi phải phân tích chuỗi:

```text
"Cannot load order 42"
```

từ thông điệp.

Trường dữ liệu và phương thức truy cập (accessor) rõ ràng hơn:

```java
ex.getOrderId()
```

Thông điệp nên phục vụ con người và chẩn đoán; trường dữ liệu phục vụ logic theo hợp đồng.

### Không đưa bí mật vào exception

Exception thường đi vào log, hệ thống truy vết (tracing) hoặc hệ thống giám sát.

Tránh nhét:

- mật khẩu;
- access token;
- khóa bí mật;
- thông tin xác thực đầy đủ;
- dữ liệu nhạy cảm không cần thiết.

“Thêm ngữ cảnh” không có nghĩa “sao chép toàn bộ yêu cầu (request) vào thông điệp”.

## <a id="exception-hierarchy-design">Thiết kế cây phân cấp Exception</a>

Một cây phân cấp nhỏ có thể cho bên gọi nhiều mức xử lý:

```text
OrderException
├── OrderNotFoundException
└── OrderValidationException
```

Bên gọi muốn xử lý tất cả lỗi order:

```java
catch (OrderException ex) {
    handleOrderFailure(ex);
}
```

Bên gọi muốn chính sách riêng:

```java
catch (OrderNotFoundException ex) {
    showNotFound(ex);
}
```

### Cây phân cấp nên phản ánh chính sách xử lý

Đừng phản chiếu mọi lớp nội bộ:

```text
JdbcOrderTableReadException
JdbcOrderColumnMappingException
JdbcOrderConnectionAcquireException
...
```

nếu bên gọi chỉ có một chính sách:

```text
"không load được order"
```

Một cây phân cấp tốt thường trả lời:

```text
Bên gọi có cần phân biệt hai lỗi này để hành động khác nhau không?
```

Nếu không, hai kiểu riêng có thể là thiết kế thừa.

### Constructor nên phục vụ chuỗi nguyên nhân

Một lớp exception gốc thường cần các constructor phù hợp với cách module sử dụng:

```java
OrderException(String message) {
    super(message);
}

OrderException(String message, Throwable cause) {
    super(message, cause);
}
```

Không bắt buộc phải sao chép mọi constructor của `RuntimeException`; chỉ công khai hợp đồng thực sự cần.

### GHI NHỚ

```text
kiểu tùy chỉnh
→ bổ sung hợp đồng ngữ nghĩa

trường dữ liệu ngữ cảnh
→ dữ liệu có cấu trúc

cause
→ giữ thông tin chẩn đoán từ nguyên nhân gốc

cây phân cấp
→ phục vụ cách bên gọi muốn xử lý
```

Chương tiếp theo tập trung vào ranh giới xử lý trong ứng dụng: bắt ở đâu, khi nào chuyển đổi, ghi log, thử lại, phục hồi hoặc để exception tiếp tục lan truyền?
