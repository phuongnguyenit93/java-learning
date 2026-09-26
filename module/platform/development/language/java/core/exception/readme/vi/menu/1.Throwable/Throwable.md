# Throwable

Trong luồng thực thi bình thường, ta giả định một method sẽ hoàn thành công việc rồi trả kết quả. Nhưng chương trình thực tế luôn có khả năng **không thể tiếp tục theo đường thành công**: dữ liệu đầu vào sai, file không tồn tại, kết nối lỗi, tài nguyên đóng thất bại hoặc một invariant bị phá.

Java dùng exception để biểu diễn **sự kết thúc bất thường của luồng thực thi**. Thay vì bắt mọi method trả về một “mã lỗi”, Java cho phép lỗi mang theo ngữ cảnh và truyền ngược qua chuỗi lời gọi cho tới tầng có đủ trách nhiệm để xử lý.

Trong toàn module, ta sẽ dùng một câu chuyện lặp lại:

```text
caller
  ↓
OrderService
  ↓
OrderRepository / file I/O
  ↓
failure
```

Tầng thấp có thể phát sinh lỗi kỹ thuật; tầng cao quyết định khôi phục, chuyển đổi exception, ghi log hay để lỗi tiếp tục truyền đi.

Lộ trình của module:

```text
Java biểu diễn sự kết thúc bất thường như thế nào?
Throwable
        ↓
Lỗi nào compiler buộc bên gọi phải nhận biết?
Checked vs Unchecked
        ↓
Làm sao ném lỗi và khai báo khả năng phát sinh lỗi?
throw / throws
        ↓
Nếu không xử lý tại đây, exception đi đâu?
Propagation
        ↓
Xử lý lỗi và dọn dẹp bằng cách nào?
try / catch / finally
        ↓
Tài nguyên được đóng an toàn ra sao?
try-with-resources
        ↓
Nếu body và close đều fail thì giữ lỗi nào?
Suppressed Exception
        ↓
Khi nào cần kiểu exception riêng?
Custom Exception
        ↓
Nên catch, wrap, log, retry, recover hay propagate ở đâu?
Exception Design
```

Mục tiêu cuối cùng không phải là nhớ thật nhiều tên exception, mà là hiểu **failure di chuyển qua chương trình như thế nào và tầng nào phải chịu trách nhiệm với nó**.

## <a id="throwable-hierarchy">Hệ thống Throwable</a>

### KHÁI NIỆM

`Throwable` là kiểu gốc của cơ chế exception trong Java. Mô hình rút gọn:

```text
Throwable
├── Error
└── Exception
    └── RuntimeException
```

Mọi giá trị được `throw` phải có kiểu tương thích với `Throwable`. Một `Throwable` có hai vai trò cùng lúc:

```text
control-flow signal
→ báo rằng đường chạy bình thường bị gián đoạn

diagnostic object
→ mang type, message, stack trace, cause và có thể có suppressed exceptions
```

Đây là lý do exception không chỉ là “một object chứa lỗi”. Khi nó được ném, Java còn thay đổi luồng điều khiển để tìm nơi xử lý phù hợp.

### Checked và unchecked nằm ở đâu?

Phân loại compile-time có thể nhìn như sau:

```text
Throwable
├── Error                         → unchecked
└── Exception
    ├── RuntimeException          → unchecked
    └── các Exception còn lại     → checked
```

Nói chính xác hơn: các lớp thuộc nhánh `RuntimeException` **và** `Error` không chịu quy tắc catch-or-declare của checked exception. Các exception còn lại thuộc hệ `Throwable` là checked theo quy tắc ngôn ngữ.

Điểm này quan trọng vì “unchecked” không đồng nghĩa với “chỉ RuntimeException”; `Error` cũng không bị compiler bắt buộc catch/declare.

### CƠ CHẾ

Khi code thực thi:

```java
throw new IllegalStateException("order state is invalid");
```

đường chạy bình thường dừng tại `throw`. Java không chuyển sang câu lệnh kế tiếp trong block mà bắt đầu tìm handler phù hợp. Nếu method hiện tại không xử lý, exception tiếp tục truyền lên call stack.

Ta sẽ đi sâu vào cơ chế đó ở chương **Propagation**.

## <a id="error-vs-exception">Error và Exception</a>

`Error` thường đại diện cho các điều kiện nghiêm trọng liên quan tới JVM, linkage, môi trường chạy hoặc assertion, ví dụ `OutOfMemoryError` hay `NoClassDefFoundError`.

`Exception` thường đại diện cho failure mà ứng dụng hoặc API có thể mô hình hóa, xử lý, chuyển đổi hoặc truyền tiếp, ví dụ `IOException`, `IllegalArgumentException` hoặc exception riêng của domain.

### VÌ SAO PHẢI PHÂN BIỆT?

Về cú pháp, Java vẫn cho phép:

```java
try {
    run();
} catch (Throwable t) {
    ...
}
```

Nhưng `catch (Throwable)` quá rộng có thể bắt luôn cả `Error`. Ứng dụng khi đó dễ vô tình biến một tình trạng nghiêm trọng của runtime thành “một lỗi nghiệp vụ bình thường”.

Mental model hữu ích:

```text
Exception
→ "ứng dụng có thể có chính sách xử lý failure này"

Error
→ "đừng mặc định rằng application code có thể phục hồi an toàn"
```

Đây là hướng dẫn thiết kế, không phải lời khẳng định rằng `Error` tuyệt đối không bao giờ được catch. Một số boundary rất đặc biệt có thể cần quan sát hoặc cleanup, nhưng code thông thường không nên dùng `catch (Throwable)` như handler chung.

## <a id="stack-trace-cause">Stack Trace và Cause</a>

Một `Throwable` thường chứa bốn mảnh thông tin quan trọng:

- **type** — loại failure, ví dụ `IOException`;
- **message** — thông tin mô tả cụ thể;
- **stack trace** — các call frame giúp biết đường gọi dẫn tới failure;
- **cause** — exception thấp hơn đã dẫn tới exception hiện tại.

Ví dụ tầng repository gặp `IOException`, nhưng tầng service muốn dùng ngôn ngữ phù hợp với ứng dụng:

```java
try {
    return repository.load(orderId);
} catch (IOException ex) {
    throw new OrderLoadException("Cannot load order " + orderId, ex);
}
```

Khi đó:

```text
OrderLoadException
message = Cannot load order 42
cause
  ↓
IOException
message = connection reset
```

Bên ngoài có thể đọc chuỗi nguyên nhân:

```java
Throwable current = ex;

while (current != null) {
    System.out.println(current.getClass().getSimpleName()
            + ": " + current.getMessage());
    current = current.getCause();
}
```

### Stack trace nói cho ta điều gì?

Một stack trace thường cho thấy:

```text
nơi Throwable được tạo / ghi stack
        ↓
method đang thực thi
        ↓
caller
        ↓
caller của caller
```

Nó là dữ liệu chẩn đoán, không phải hợp đồng ổn định để business logic parse bằng chuỗi. Thay đổi refactor có thể thay đổi tên method, số dòng và call frame.

Ngoài ra, đừng tái sử dụng một exception instance như một “constant lỗi”. Stack trace thường được ghi khi `Throwable` được tạo/fill stack trace; tái sử dụng cùng object có thể khiến thông tin vị trí trở nên gây hiểu nhầm.

### GHI NHỚ

```text
Throwable
= một object mang thông tin
+ một tín hiệu làm thay đổi control flow
```

Chương tiếp theo phân biệt **checked** và **unchecked**: khi nào compiler biến khả năng failure thành một phần bắt buộc của hợp đồng method?