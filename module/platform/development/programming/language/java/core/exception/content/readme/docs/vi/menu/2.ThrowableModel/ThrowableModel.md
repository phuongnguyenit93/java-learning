# Throwable và cách Java phân loại lỗi

Sau khi hiểu vì sao ngoại lệ tồn tại, bước tiếp theo là hiểu Java biểu diễn và phân loại các lỗi có thể được ném thông qua hệ phân cấp `Throwable`.

## <a id="throwable-hierarchy">Hệ phân cấp Throwable</a>

### KHÁI NIỆM

`Throwable` là lớp gốc của cơ chế ngoại lệ trong Java. Mô hình rút gọn:

```text
Throwable
├── Error
└── Exception
    └── RuntimeException
```

Mọi giá trị được `throw` phải có kiểu tương thích với `Throwable`. Một `Throwable` có hai vai trò cùng lúc:

```text
tín hiệu điều khiển luồng
→ báo rằng đường chạy bình thường bị gián đoạn

đối tượng chẩn đoán
→ mang kiểu, thông điệp, stack trace, cause và có thể có suppressed exception
```

Đây là lý do ngoại lệ không chỉ là “một đối tượng chứa lỗi”. Khi nó được ném, Java còn thay đổi luồng điều khiển để tìm nơi xử lý phù hợp.

### Checked và unchecked nằm ở đâu?

Phân loại theo quy tắc tại thời điểm biên dịch có thể nhìn như sau:

```text
Throwable
├── Error                         → unchecked
└── Exception
    ├── RuntimeException          → unchecked
    └── các Exception còn lại     → checked
```

Nói chính xác hơn: các lớp thuộc nhánh `RuntimeException` **và** `Error` không chịu quy tắc bắt-hoặc-khai-báo của checked exception. Mọi lớp `Throwable` còn lại đều là checked theo quy tắc ngôn ngữ, kể cả trường hợp hiếm khi một lớp kế thừa trực tiếp từ `Throwable` thay vì từ `Exception`.

Trong mã nguồn ứng dụng thông thường, nên kế thừa `Exception` hoặc `RuntimeException` thay vì tạo lớp con trực tiếp của `Throwable`; sơ đồ trên cố ý tập trung vào cấu trúc mà người học sẽ gặp trong thực tế.

Điểm này quan trọng vì “unchecked” không đồng nghĩa với “chỉ RuntimeException”; `Error` cũng không bị trình biên dịch bắt buộc bắt hoặc khai báo.

### CƠ CHẾ

Khi mã nguồn được thực thi:

```java
throw new IllegalStateException("order state is invalid");
```

đường chạy bình thường dừng tại `throw`. Java không chuyển sang câu lệnh kế tiếp trong khối lệnh mà bắt đầu tìm khối xử lý phù hợp. Nếu phương thức hiện tại không xử lý, exception tiếp tục lan truyền lên ngăn xếp lời gọi.

Ta sẽ đi sâu vào cơ chế đó ở chương **throw, throws và sự lan truyền Exception**.

## <a id="error-vs-exception">Error và Exception</a>

`Error` thường đại diện cho các điều kiện nghiêm trọng liên quan tới JVM, quá trình liên kết lớp (linkage), môi trường chạy hoặc assertion, ví dụ `OutOfMemoryError` hay `NoClassDefFoundError`.

`Exception` thường đại diện cho lỗi mà ứng dụng hoặc API có thể mô hình hóa, xử lý, chuyển đổi hoặc truyền tiếp, ví dụ `IOException`, `IllegalArgumentException` hoặc exception riêng của miền nghiệp vụ.

### VÌ SAO PHẢI PHÂN BIỆT?

Về cú pháp, Java vẫn cho phép:

```java
try {
    run();
} catch (Throwable t) {
    ...
}
```

Nhưng `catch (Throwable)` quá rộng có thể bắt luôn cả `Error`. Ứng dụng khi đó dễ vô tình biến một tình trạng nghiêm trọng của môi trường chạy thành “một lỗi nghiệp vụ bình thường”.

Mô hình cần nhớ:

```text
Exception
→ "ứng dụng có thể có chính sách xử lý lỗi này"

Error
→ "đừng mặc định rằng code ứng dụng có thể phục hồi an toàn"
```

Đây là hướng dẫn thiết kế, không phải lời khẳng định rằng `Error` tuyệt đối không bao giờ được bắt. Một số ranh giới rất đặc biệt có thể cần quan sát hoặc dọn dẹp, nhưng mã nguồn thông thường không nên dùng `catch (Throwable)` như khối xử lý chung.

### Hệ phân cấp quyết định phạm vi của `catch`

Kiểu khai báo trong `catch` xác định một **nhánh của hệ phân cấp** mà khối đó có thể nhận:

```text
catch (Throwable)
→ rất rộng: nhận cả Error lẫn Exception

catch (Exception)
→ nhận Exception và các lớp con của nó
→ không nhận Error

catch (IOException)
→ hẹp hơn: chỉ nhận IOException và các lớp con
```

Vì vậy chọn kiểu `catch` không phải là ghi nhớ tên lớp; đó là chọn **phạm vi lỗi mà tầng hiện tại thực sự có trách nhiệm xử lý**. Bắt quá rộng dễ kéo vào những lỗi mà tầng này không hiểu, còn bắt cụ thể giúp chính sách xử lý rõ hơn.

Chương về sự lan truyền Exception sẽ đi sâu vào cách Java chọn khối `catch` cụ thể khi nhiều khối xử lý cùng xuất hiện.

## <a id="stack-trace-cause">Stack Trace và nguyên nhân (Cause)</a>

Một `Throwable` thường chứa bốn mảnh thông tin quan trọng:

- **kiểu** — loại lỗi, ví dụ `IOException`;
- **thông điệp** — thông tin mô tả cụ thể;
- **stack trace** — các khung ngăn xếp (stack frame) giúp biết đường gọi dẫn tới lỗi;
- **cause** — exception ở tầng thấp hơn đã dẫn tới exception hiện tại.

Để chỉ quan sát quan hệ `cause` mà chưa cần biết về Custom Exception, dùng một lớp bọc quen thuộc:

```java
try {
    return repository.load(orderId);
} catch (IOException ex) {
    throw new RuntimeException("Cannot load order " + orderId, ex);
}
```

Khi đó:

```text
RuntimeException
message = Cannot load order 42
cause
  ↓
IOException
message = connection reset
```

`RuntimeException` ở đây chỉ dùng để minh họa cấu trúc `cause`, không phải khuyến nghị rằng mọi `IOException` nên được bọc bằng `RuntimeException`. Thiết kế Custom Exception và quyết định chuyển đổi lỗi sẽ được học ở các chương sau.

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
phương thức đang thực thi
        ↓
bên gọi
        ↓
bên gọi của bên gọi
```

Nó là dữ liệu chẩn đoán, không phải hợp đồng ổn định để logic nghiệp vụ phân tích bằng chuỗi. Việc refactor có thể thay đổi tên phương thức, số dòng và khung ngăn xếp.

Ngoài ra, đừng tái sử dụng cùng một đối tượng exception như một “hằng lỗi”. Stack trace thường được ghi khi `Throwable` được tạo hoặc điền stack trace; tái sử dụng cùng đối tượng có thể khiến thông tin vị trí trở nên gây hiểu nhầm.

### GHI NHỚ

```text
Throwable
= một đối tượng mang thông tin
+ một tín hiệu làm thay đổi luồng điều khiển
```

Chương tiếp theo phân biệt **checked** và **unchecked**: khi nào trình biên dịch biến khả năng phát sinh lỗi thành một phần bắt buộc của hợp đồng phương thức?
