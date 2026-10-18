# Checked Exception và Unchecked Exception

Sau khi hiểu `Throwable`, câu hỏi tiếp theo là: **loại lỗi nào trở thành một phần của hợp đồng phương thức mà trình biên dịch buộc bên gọi phải nhìn thấy?**

Checked/unchecked là phân loại về **quy tắc tại thời điểm biên dịch**, không phải thước đo “lỗi nặng” hay “lỗi nhẹ”.

## <a id="checked-exception">Checked Exception</a>

Checked exception là exception chịu quy tắc **bắt hoặc khai báo (catch or declare)**.

Nếu một checked exception có thể thoát khỏi phương thức, mã nguồn phải:

```text
`catch` nó
hoặc
khai báo nó bằng `throws`
```

Ví dụ:

```java
String read(Path path) throws IOException {
    return Files.readString(path);
}
```

`Files.readString(path)` có thể ném `IOException`. Nếu `read()` không catch lỗi đó, `read()` phải công bố nó trong `throws`.

Bên gọi tiếp tục phải đưa ra quyết định:

```java
try {
    String content = read(path);
} catch (IOException ex) {
    recover(ex);
}
```

hoặc:

```java
String load(Path path) throws IOException {
    return read(path);
}
```

### VÌ SAO JAVA CÓ CHECKED EXCEPTION?

Ý tưởng của checked exception là đưa một nhóm lỗi vào **hợp đồng được kiểm tra tại thời điểm biên dịch**:

```text
phương thức có một khả năng lỗi mà bên gọi được kỳ vọng phải nhận biết
        ↓
trình biên dịch buộc bên gọi xử lý hoặc tiếp tục khai báo
```

Nó hữu ích ở những ranh giới mà bên gọi thực sự có thể chọn hành động có ý nghĩa, ví dụ đổi file khác, báo lỗi nhập liệu, chuyển sang nguồn dự phòng hoặc chấm dứt thao tác theo cách có chủ ý.

Nhược điểm xuất hiện khi bên gọi không có khả năng phục hồi thực tế. Nếu mọi tầng đều chỉ viết:

```java
throws SomeCheckedException
```

mà không có thêm quyết định thiết kế nào, hợp đồng checked exception có thể trở thành mã lặp lại thay vì thông tin hữu ích.

## <a id="unchecked-exception">Unchecked Exception</a>

Unchecked exception không chịu quy tắc bắt-hoặc-khai-báo.

Hai nhóm chính:

```text
RuntimeException và các lớp con
Error và các lớp con
```

Trong mã nguồn ứng dụng, khi nói “unchecked exception” ta thường gặp nhánh `RuntimeException`:

- `NullPointerException`;
- `IllegalArgumentException`;
- `IllegalStateException`;
- `IndexOutOfBoundsException`.

Ví dụ:

```java
void withdraw(long amount) {
    if (amount < 0) {
        throw new IllegalArgumentException("amount must be >= 0");
    }
}
```

Phương thức không bắt buộc phải viết:

```java
void withdraw(long amount) throws IllegalArgumentException
```

Dù khai báo unchecked exception trong `throws` là **hợp lệ về cú pháp**, trình biên dịch không yêu cầu làm vậy.

### Unchecked không có nghĩa là “không cần xử lý”

Unchecked chỉ nói:

```text
trình biên dịch
→ không ép bên gọi bắt hoặc khai báo
```

Nó không nói:

```text
khi chương trình chạy
→ lỗi không quan trọng
```

`NullPointerException` vẫn có thể làm một yêu cầu hoặc tác vụ thất bại. Điểm khác biệt là API không bắt mọi bên gọi thể hiện quyết định đó bằng cú pháp checked exception.

Unchecked exception thường phù hợp khi lỗi liên quan tới:

- vi phạm điều kiện đầu vào (precondition);
- trạng thái đối tượng không hợp lệ;
- lỗi lập trình;
- lỗi mà bên gọi gần đó không có chiến lược phục hồi hợp lý.

## <a id="checked-vs-unchecked-design">Chọn Checked hay Unchecked?</a>

Không nên dùng các khẩu quyết máy móc như:

```text
exception nghiệp vụ = checked
Java hiện đại = unchecked
```

Thay vào đó, bắt đầu từ trách nhiệm của bên gọi.

### CÂU HỎI THIẾT KẾ

```text
Bên gọi có khả năng xử lý lỗi này theo cách có ý nghĩa không?
        ↓
Có cần buộc mọi bên gọi phải quyết định ngay tại thời điểm biên dịch không?
        ↓
Thông tin trong `throws` làm API rõ hơn hay chỉ tạo mã lặp lại?
        ↓
Lỗi này là một khả năng vận hành có thể phục hồi,
hay phản ánh việc vi phạm hợp đồng/bất biến (invariant)?
```

Ví dụ, cùng là “không đọc được dữ liệu” nhưng chính sách xử lý có thể khác:

```text
ứng dụng trên máy tính đọc file do người dùng chọn
→ IOException có thể giúp bên gọi yêu cầu người dùng chọn lại file

tầng dịch vụ nội bộ gọi tầng repository
→ có thể chuyển đổi sang exception của ứng dụng và xử lý ở ranh giới cao hơn
```

Không có lựa chọn đúng cho mọi hệ thống. Điều quan trọng là **loại exception phải phục vụ chính sách xử lý của API**.

### SO SÁNH TẠI THỜI ĐIỂM BIÊN DỊCH

```java
void checked() throws IOException {
    throw new IOException("disk failure");
}

void unchecked() {
    throw new IllegalStateException("invalid state");
}
```

Bên gọi của `checked()` buộc phải bắt hoặc khai báo `IOException`. Bên gọi của `unchecked()` không bị trình biên dịch áp quy tắc đó.

### GHI NHỚ

```text
checked
→ trình biên dịch buộc lỗi xuất hiện trong quyết định của bên gọi

unchecked
→ trình biên dịch không buộc, nhưng lỗi vẫn tồn tại khi chương trình chạy
```

Chương tiếp theo tách hai keyword dễ nhầm: `throw` thực sự **ném một Throwable**, còn `throws` **mô tả khả năng exception thoát khỏi phương thức**.
