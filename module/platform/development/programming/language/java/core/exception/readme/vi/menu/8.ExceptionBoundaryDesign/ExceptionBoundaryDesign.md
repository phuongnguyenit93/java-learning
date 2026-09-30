# Ranh giới xử lý Exception trong ứng dụng

Biết cú pháp `try/catch` chưa đủ để thiết kế luồng xử lý lỗi tốt. Câu hỏi quan trọng hơn là:

```text
Tầng nào có đủ ngữ cảnh
và đủ trách nhiệm
để làm điều có ý nghĩa với lỗi?
```

Một thiết kế Exception tốt giữ được ba thứ cùng lúc:

```text
luồng điều khiển đúng
+ mức trừu tượng đúng
+ đủ thông tin chẩn đoán
```

## <a id="exception-boundaries">Chuyển đổi Exception tại ranh giới trừu tượng</a>

Tầng thấp thường dùng ngôn ngữ của cách triển khai:

```text
SQLException
IOException
SocketTimeoutException
```

Tầng cao hơn có thể cần ngôn ngữ của ứng dụng/miền nghiệp vụ:

```text
OrderRepositoryException
DocumentLoadException
PaymentUnavailableException
```

Ví dụ:

```java
Order loadOrder(long orderId) {
    try {
        return repository.load(orderId);
    } catch (SQLException ex) {
        throw new OrderRepositoryException(
                "Cannot load order " + orderId,
                ex
        );
    }
}
```

### VÌ SAO CẦN CHUYỂN ĐỔI EXCEPTION?

Nếu hợp đồng công khai của tầng service để lộ thẳng `SQLException`, bên gọi sẽ bị phụ thuộc vào công nghệ lưu trữ.

Sau này tầng repository đổi từ JDBC sang file hoặc API từ xa, ngôn ngữ lỗi mà bên gọi phải hiểu cũng bị kéo theo.

Việc chuyển đổi tạo ra một ranh giới:

```text
chi tiết triển khai
→ kết thúc tại ranh giới

ý nghĩa ở mức ứng dụng
→ tiếp tục đi ra bên gọi
```

Nhưng khi chuyển đổi phải giữ `cause`; nếu không, mức trừu tượng đẹp hơn nhưng thông tin chẩn đoán lại kém đi.

### Đừng bọc exception ở mọi tầng

Không cần:

```text
SQLException
→ RepositoryException
→ ServiceException
→ ControllerException
```

nếu mỗi lớp bọc chỉ đổi tên.

Chỉ bọc khi tầng mới thực sự thêm một trong các giá trị:

- ý nghĩa ở mức trừu tượng mới;
- nhóm xử lý mới;
- ngữ cảnh hữu ích;
- hợp đồng công khai ổn định.

Một chuỗi kiểu:

```text
IOException
→ MyIOException
→ ServiceIOException
→ ControllerIOException
```

không tạo thêm giá trị nếu mỗi lớp chỉ đổi tên cùng một lỗi.

### Giữ nguyên hay bọc lại?

Nếu mức trừu tượng không đổi, ném lại exception cũ có thể hợp lý:

```java
catch (IOException ex) {
    audit(ex);
    throw ex;
}
```

Nếu mức trừu tượng thay đổi, bọc bằng một kiểu phù hợp và giữ nguyên nhân có thể rõ hơn:

```java
catch (IOException ex) {
    throw new OrderLoadException("Cannot load order file", ex);
}
```

Không nên tạo lớp bọc chỉ vì “đã bắt exception thì phải ném exception mới”.

## <a id="do-not-swallow">Không nuốt lỗi</a>

Cách viết cần tránh:

```java
try {
    run();
} catch (Exception ex) {
    // ignored
}
```

Mã nguồn bên ngoài có thể tiếp tục như thể thao tác đã thành công trong khi trạng thái thực tế chưa đạt kết quả mong muốn.

Hậu quả:

```text
lỗi xảy ra
→ tín hiệu lỗi bị xóa
→ bên gọi thấy thành công giả
→ bug xuất hiện xa nguyên nhân gốc
```

### “Bỏ qua có chủ ý” khác “nuốt lỗi vô thức”

Có trường hợp một lỗi cụ thể được phép bỏ qua:

```java
try {
    deleteTemporaryFile();
} catch (NoSuchFileException ex) {
    // file đã không tồn tại; kết quả mong muốn vẫn đạt
}
```

Điểm khác là:

- kiểu cụ thể;
- lý do rõ;
- kết quả vẫn hợp lệ;
- phạm vi bỏ qua hẹp.

Không nên dùng `catch (Exception)` trống để đạt hiệu ứng tương tự.

## <a id="logging-boundary">Ranh giới ghi log</a>

Một cách viết cần tránh thường gặp:

```text
repository bắt → log → ném lại
service bắt    → log → ném lại
controller bắt → log → trả kết quả
```

Một lỗi có thể tạo ra ba bản ghi stack trace gần giống nhau.

### Quy tắc kinh nghiệm hữu ích

```text
tầng chỉ ném lại/chuyển đổi
→ giữ ngữ cảnh/cause
→ thường không log lại

tầng kết thúc yêu cầu/tác vụ/thông điệp
→ có đầy đủ ngữ cảnh của yêu cầu, tác vụ hoặc nghiệp vụ
→ thường log một lần
```

Đây không phải luật tuyệt đối. Một tầng có thể cần số liệu đo lường (metric) hoặc bản ghi kiểm toán (audit) riêng mà không ghi lại toàn bộ stack trace.

Câu hỏi tốt hơn “có log không?” là:

```text
Log này thêm thông tin mới gì
và ai chịu trách nhiệm cuối cho lỗi?
```

### Đừng ghi log rồi làm mất cause

Sai:

```java
catch (SQLException ex) {
    log.error("database failed");
    throw new OrderRepositoryException("load failed");
}
```

Tốt hơn nếu cần bọc exception:

```java
catch (SQLException ex) {
    throw new OrderRepositoryException("load failed", ex);
}
```

Ranh giới cuối ghi log lớp bọc cùng chuỗi nguyên nhân một lần.

## <a id="exception-as-control-flow">Exception và luồng điều khiển</a>

Exception phù hợp với **sự kết thúc bất thường**, không nên thay thế nhánh bình thường có thể diễn đạt trực tiếp.

Khó đọc:

```java
try {
    return values.get(index);
} catch (IndexOutOfBoundsException ex) {
    return null;
}
```

nếu “index không tồn tại” thực ra là một trạng thái bình thường mà API có thể kiểm tra rõ ràng.

Rõ hơn trong nhiều ngữ cảnh:

```java
if (index < 0 || index >= values.size()) {
    return null;
}
return values.get(index);
```

### Nhưng exception ở ranh giới phân tích dữ liệu có thể hợp lý

```java
try {
    return Integer.parseInt(text);
} catch (NumberFormatException ex) {
    return defaultValue;
}
```

Ở đây API phân tích dữ liệu vốn dùng exception để báo đầu vào không thể chuyển đổi; bắt exception tại ranh giới nhỏ có chính sách rõ ràng.

Vấn đề không phải “exception chậm nên không bao giờ dùng”, mà là ý nghĩa của luồng xử lý:

```text
nhánh bình thường
→ diễn đạt bằng cấu trúc điều khiển bình thường

kết thúc bất thường
→ exception
```

Hiệu năng có thể là lý do bổ sung trên đường chạy được thực thi rất thường xuyên (hot path), nhưng tính dễ đọc và hợp đồng API mới là lý do thiết kế đầu tiên.

## <a id="cleanup-and-recovery">Dọn dẹp, phục hồi, thử lại và lan truyền</a>

Các hành động này khác nhau.

### Dọn dẹp

```text
mục tiêu
→ giải phóng tài nguyên / hoàn tất dọn dẹp bắt buộc

công cụ
→ try-with-resources
→ finally khi phù hợp
```

Dọn dẹp không có nghĩa thao tác đã phục hồi.

### Phục hồi

Phục hồi nghĩa là có một chiến lược thực sự tạo ra kết quả hợp lệ để thao tác có thể tiếp tục hoặc kết thúc đúng hợp đồng.

Ví dụ:

```text
file cấu hình chính không tồn tại
→ dùng cấu hình mặc định mà hợp đồng cho phép
```

Bắt exception rồi trả một giá trị tùy ý không phải phục hồi nếu bên gọi không thể phân biệt dữ liệu thật với dữ liệu giả.

### Thử lại

Thử lại chỉ có ý nghĩa khi lỗi có khả năng **tạm thời** và thao tác an toàn để thực hiện lại.

Trước khi thử lại, hỏi:

```text
Lỗi có tính tạm thời (transient) không?
Thao tác có tính idempotent hoặc có cơ chế chống tác dụng phụ bị lặp không?
Số lần thử lại có giới hạn không?
Có chính sách tăng thời gian chờ giữa các lần thử (backoff), hủy hoặc giới hạn thời gian (timeout) không?
```

Không nên:

```java
while (true) {
    try {
        sendPayment();
        break;
    } catch (Exception ex) {
        // thử lại vô hạn
    }
}
```

Thử lại vô hạn có thể biến một lỗi thành tình trạng quá tải và nhân lên các tác dụng phụ.

Trong module Java Core này, điểm cần nhớ là **thử lại là một chính sách xử lý tại ranh giới có đủ ngữ cảnh**, không phải phản xạ mặc định cho mọi exception.

### Lan truyền

Nếu tầng hiện tại không thể:

- phục hồi;
- chuyển đổi hữu ích;
- bổ sung ngữ cảnh cần thiết;
- kết thúc thao tác;
- dọn dẹp tài nguyên nó sở hữu;

thì để exception tiếp tục lan truyền có thể là lựa chọn đúng.

### Mô hình ra quyết định

```text
Exception tới tầng hiện tại
        ↓
Tôi có sở hữu tài nguyên cần dọn dẹp không?
        → có: dọn dẹp an toàn

Tôi có thể tạo ra kết quả phục hồi hợp lệ không?
        → có: phục hồi

Lỗi có tính tạm thời và việc thử lại có an toàn không?
        → có: thử lại theo chính sách có giới hạn

Mức trừu tượng có đổi ở đây không?
        → có: chuyển đổi + giữ cause

Đây có phải ranh giới cuối của yêu cầu/tác vụ không?
        → có: ánh xạ kết quả + ghi log/quan sát phù hợp

Không có trách nhiệm nào ở trên?
        → tiếp tục lan truyền
```

Chương cuối sẽ nối các quyết định này thành một mô hình xử lý lỗi xuyên suốt từ nơi phát sinh đến nơi chịu trách nhiệm xử lý cuối cùng.
