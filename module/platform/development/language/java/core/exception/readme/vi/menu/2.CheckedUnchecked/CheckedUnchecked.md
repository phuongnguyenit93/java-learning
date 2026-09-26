# Checked và Unchecked Exception

Sau khi hiểu `Throwable`, câu hỏi tiếp theo là: **lỗi nào trở thành một phần của hợp đồng method mà compiler buộc bên gọi phải nhìn thấy?**

## <a id="checked-exception">Checked Exception</a>

Checked exception là exception mà compiler yêu cầu method phải:

```text
catch
hoặc
declare bằng throws
```

nếu exception đó có thể thoát ra khỏi method.

Ví dụ:

```java
String read(Path path) throws IOException {
    return Files.readString(path);
}
```

`IOException` trở thành một phần của hợp đồng ở compile time. Bên gọi phải quyết định xử lý, chuyển đổi hoặc tiếp tục truyền lỗi lên trên.

### VÌ SAO

Checked exception hữu ích khi lỗi là một khả năng mà bên gọi có thể được kỳ vọng xử lý có chủ ý, ví dụ lỗi I/O hoặc lỗi tại một số ranh giới API.

Nhược điểm là nếu dùng quá nhiều checked exception cho những lỗi bên gọi không thể xử lý thực tế, hợp đồng trở nên nặng và sinh nhiều đoạn mã chỉ để khai báo hoặc truyền tiếp exception.

## <a id="unchecked-exception">Unchecked Exception</a>

Các exception thuộc `RuntimeException` là unchecked. Compiler không buộc bên gọi phải catch hoặc khai báo chúng.

Ví dụ phổ biến:

- `NullPointerException`;
- `IllegalArgumentException`;
- `IllegalStateException`;
- `IndexOutOfBoundsException`.

Unchecked không có nghĩa là “không cần quan tâm”. Nó chỉ có nghĩa là **compiler không bắt buộc phải catch hoặc khai báo theo quy tắc của checked exception**.

Unchecked exception thường phù hợp với lỗi lập trình, precondition bị vi phạm, trạng thái không hợp lệ hoặc lỗi mà bên gọi gần đó không có chiến lược khôi phục hợp lý.

## <a id="checked-vs-unchecked-design">Chọn Checked hay Unchecked?</a>

Không nên chọn chỉ bằng câu “business exception = checked” hay “modern Java = unchecked”. Hãy hỏi:

```text
Bên gọi có khả năng và trách nhiệm xử lý lỗi này không?
        ↓
Lỗi có cần xuất hiện rõ trong hợp đồng ở compile time không?
        ↓
Việc bắt bên gọi catch/declare có làm API rõ hơn hay chỉ tạo mã lặp theo khuôn mẫu?
```

Checked exception và unchecked exception thể hiện hai lựa chọn khác nhau về **chính sách lỗi của API**. Cả hai đều có chỗ dùng phù hợp.

Chương tiếp theo đi vào hai keyword dễ nhầm: `throw` và `throws`.
