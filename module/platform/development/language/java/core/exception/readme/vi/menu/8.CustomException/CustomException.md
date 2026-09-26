# Custom Exception

Không phải failure nào cũng cần một class exception mới. Custom exception chỉ có giá trị khi type mới **thêm meaning**, tạo abstraction boundary rõ hơn hoặc cho caller một handling category hữu ích.

Câu hỏi không phải:

```text
"Có thể tạo exception riêng không?"
```

mà là:

```text
"Caller được lợi gì khi failure này có một type riêng?"
```

## <a id="custom-exception-purpose">Khi nào cần Custom Exception?</a>

Custom exception hữu ích khi nó:

- diễn đạt failure bằng vocabulary của domain/application;
- che implementation detail của tầng thấp;
- cho phép caller catch một nhóm failure có cùng policy;
- mang structured context mà message thuần chuỗi không đủ;
- tạo một contract ổn định hơn so với exception của thư viện/hạ tầng phía dưới.

Ví dụ repository dùng file hoặc database có thể thay đổi theo thời gian. Service không muốn caller phụ thuộc vào `IOException` hay `SQLException`.

```java
try {
    return repository.load(orderId);
} catch (IOException ex) {
    throw new OrderLoadException(orderId, ex);
}
```

Caller nhìn thấy:

```text
OrderLoadException
```

thay vì phải biết repository đang dùng file.

### Khi nào KHÔNG cần?

Không nên tạo:

```java
class InvalidArgumentForOrderException extends RuntimeException {
}
```

chỉ để thay cho `IllegalArgumentException` nếu type mới không bổ sung:

- domain meaning;
- structured context;
- handling policy;
- abstraction boundary.

Nhiều exception class chỉ khác tên làm API khó đọc hơn mà không tăng expressiveness.

### Checked hay unchecked cho custom exception?

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
Có muốn compiler buộc mọi caller catch/declare failure này không?
```

Đừng chọn chỉ vì “custom exception thì phải extends RuntimeException” hoặc “business exception thì phải extends Exception”.

## <a id="exception-context">Giữ ngữ cảnh và Cause</a>

Một custom exception tốt thường có constructor giữ cause:

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

Usage:

```java
catch (IOException ex) {
    throw new OrderLoadException(orderId, ex);
}
```

Exception giờ có:

```text
type
→ OrderLoadException

structured context
→ orderId

message
→ mô tả cho diagnostics

cause
→ IOException gốc
```

### Message không thay structured context

Nếu code cần `orderId`, đừng bắt caller parse:

```text
"Cannot load order 42"
```

từ message.

Field/accessor rõ ràng hơn:

```java
ex.getOrderId()
```

Message nên phục vụ con người/diagnostics; field phục vụ logic có contract.

### Không đưa secret vào exception

Exception thường đi vào log, tracing hoặc monitoring.

Tránh nhét:

- password;
- access token;
- secret key;
- full credential;
- dữ liệu nhạy cảm không cần thiết.

“Thêm context” không có nghĩa “copy toàn bộ request vào message”.

## <a id="exception-hierarchy-design">Thiết kế Exception Hierarchy</a>

Một hierarchy nhỏ có thể cho caller nhiều mức xử lý:

```text
OrderException
├── OrderNotFoundException
└── OrderValidationException
```

Caller muốn xử lý tất cả lỗi order:

```java
catch (OrderException ex) {
    handleOrderFailure(ex);
}
```

Caller muốn policy riêng:

```java
catch (OrderNotFoundException ex) {
    showNotFound(ex);
}
```

### Hierarchy nên phản ánh handling policy

Đừng phản chiếu mọi class nội bộ:

```text
JdbcOrderTableReadException
JdbcOrderColumnMappingException
JdbcOrderConnectionAcquireException
...
```

nếu caller chỉ có một policy:

```text
"không load được order"
```

Hierarchy tốt thường trả lời:

```text
Caller có cần phân biệt hai failure này để hành động khác nhau không?
```

Nếu không, hai type riêng có thể là over-design.

### Constructor nên phục vụ causal chain

Một base exception thường cần ít nhất các constructor phù hợp với cách module sử dụng:

```java
OrderException(String message) {
    super(message);
}

OrderException(String message, Throwable cause) {
    super(message, cause);
}
```

Không bắt buộc phải copy mọi constructor của `RuntimeException`; chỉ expose contract thực sự cần.

### GHI NHỚ

```text
custom type
→ thêm semantic contract

context field
→ dữ liệu có cấu trúc

cause
→ giữ root diagnostics

hierarchy
→ phục vụ cách caller muốn xử lý
```

Chương cuối ghép toàn bộ module lại thành decision model: catch ở đâu, khi nào wrap, log, retry, recover hoặc để exception propagate?