# Checked và Unchecked Exception

Sau khi hiểu `Throwable`, câu hỏi tiếp theo là: **failure nào trở thành một phần của hợp đồng method mà compiler buộc bên gọi phải nhìn thấy?**

Checked/unchecked là phân loại về **quy tắc compile-time**, không phải thước đo “lỗi nặng” hay “lỗi nhẹ”.

## <a id="checked-exception">Checked Exception</a>

Checked exception là exception chịu quy tắc **catch or declare**.

Nếu một checked exception có thể thoát khỏi method, code phải:

```text
catch nó
hoặc
declare nó bằng throws
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

Ý tưởng của checked exception là đưa một nhóm failure vào **compile-time contract**:

```text
method có một khả năng failure mà caller được kỳ vọng phải nhận biết
        ↓
compiler bắt caller xử lý hoặc tiếp tục công bố
```

Nó hữu ích ở những boundary mà caller thực sự có thể chọn hành động có ý nghĩa, ví dụ đổi file khác, báo lỗi nhập liệu, chuyển sang nguồn dự phòng hoặc chấm dứt thao tác theo cách có chủ ý.

Nhược điểm xuất hiện khi caller không có khả năng phục hồi thực tế. Nếu mọi tầng đều chỉ viết:

```java
throws SomeCheckedException
```

mà không có thêm quyết định thiết kế nào, checked contract có thể trở thành boilerplate thay vì thông tin hữu ích.

## <a id="unchecked-exception">Unchecked Exception</a>

Unchecked exception không chịu quy tắc catch-or-declare.

Hai nhóm chính:

```text
RuntimeException và các subclass
Error và các subclass
```

Trong application code, khi nói “unchecked exception” ta thường gặp nhánh `RuntimeException`:

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

Method không bắt buộc phải viết:

```java
void withdraw(long amount) throws IllegalArgumentException
```

Dù khai báo unchecked exception trong `throws` là **hợp lệ về cú pháp**, compiler không yêu cầu làm vậy.

### Unchecked không có nghĩa là “không cần xử lý”

Unchecked chỉ nói:

```text
compiler
→ không ép caller catch hoặc declare
```

Nó không nói:

```text
runtime
→ lỗi không quan trọng
```

`NullPointerException` vẫn có thể làm request/job thất bại. Điểm khác biệt là API không bắt mọi caller thể hiện quyết định đó bằng cú pháp checked exception.

Unchecked exception thường phù hợp khi failure liên quan tới:

- vi phạm precondition;
- trạng thái object không hợp lệ;
- lỗi lập trình;
- failure mà caller gần đó không có chiến lược phục hồi hợp lý.

## <a id="checked-vs-unchecked-design">Chọn Checked hay Unchecked?</a>

Không nên dùng các khẩu quyết máy móc như:

```text
business exception = checked
modern Java = unchecked
```

Thay vào đó, bắt đầu từ trách nhiệm của caller.

### CÂU HỎI THIẾT KẾ

```text
Caller có khả năng xử lý failure này theo cách có ý nghĩa không?
        ↓
Có cần bắt mọi caller phải quyết định ở compile time không?
        ↓
Thông tin trong throws làm API rõ hơn hay chỉ tạo boilerplate?
        ↓
Failure này là một khả năng vận hành có thể phục hồi,
hay phản ánh việc vi phạm contract/invariant?
```

Ví dụ, cùng là “không đọc được dữ liệu” nhưng policy có thể khác:

```text
desktop tool đọc file do user chọn
→ IOException có thể giúp caller yêu cầu user chọn lại file

internal service gọi repository
→ có thể translate sang application exception và xử lý ở boundary cao hơn
```

Không có lựa chọn đúng cho mọi hệ thống. Điều quan trọng là **loại exception phải phục vụ handling policy của API**.

### SO SÁNH COMPILE-TIME

```java
void checked() throws IOException {
    throw new IOException("disk failure");
}

void unchecked() {
    throw new IllegalStateException("invalid state");
}
```

Caller của `checked()` buộc phải catch hoặc declare `IOException`. Caller của `unchecked()` không bị compiler áp quy tắc đó.

### GHI NHỚ

```text
checked
→ compiler buộc failure xuất hiện trong quyết định của caller

unchecked
→ compiler không buộc, nhưng failure vẫn tồn tại ở runtime
```

Chương tiếp theo tách hai keyword dễ nhầm: `throw` thực sự **ném một Throwable**, còn `throws` **mô tả khả năng exception thoát khỏi method**.