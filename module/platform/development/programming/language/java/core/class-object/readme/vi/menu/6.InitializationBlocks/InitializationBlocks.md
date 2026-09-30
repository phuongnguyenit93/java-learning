# Khối khởi tạo

Java cho phép đặt logic khởi tạo ở phần khởi tạo trường, khối `static`, khối khởi tạo đối tượng và hàm khởi tạo. Chương này trả lời **mỗi cơ chế dùng để làm gì**; chương tiếp theo mới ghép chúng thành thứ tự thực thi đầy đủ.

## <a id="static-initializer">Khối khởi tạo static</a>

Khối khởi tạo `static`:

```java
static {
    ...
}
```

chạy khi lớp được khởi tạo, không phải mỗi lần tạo đối tượng.

Nó có thể phù hợp cho việc thiết lập trạng thái `static` không biểu diễn thuận tiện bằng một biểu thức, nhưng logic phức tạp hoặc I/O trong quá trình khởi tạo lớp làm việc khởi động và xử lý lỗi khó kiểm soát.

## <a id="instance-initializer">Khối khởi tạo đối tượng</a>

Khối khởi tạo đối tượng:

```java
{
    ...
}
```

chạy cho mỗi lần tạo đối tượng, sau phần khởi tạo của lớp cha và trước thân hàm khởi tạo của lớp hiện tại theo quy tắc khởi tạo của Java.

Nó có thể gom logic dùng chung giữa nhiều hàm khởi tạo, nhưng thường khó đọc hơn việc đưa logic vào hàm khởi tạo hoặc phương thức trợ giúp rõ ràng.

## <a id="initializer-use-cases">Khi nào dùng khối khởi tạo?</a>

Khối khởi tạo là cơ chế hợp lệ nhưng không nên dùng chỉ vì “Java hỗ trợ”.

Ưu tiên mã dễ theo dõi:

```text
khởi tạo trường đơn giản
→ tốt cho trạng thái mặc định rõ ràng

hàm khởi tạo/phương thức trợ giúp
→ tốt cho kiểm tra dữ liệu và khởi tạo cần ngữ cảnh

khối khởi tạo
→ dùng khi thật sự làm luồng khởi tạo rõ hơn
```

Chương tiếp theo ghép các mảnh này thành **thứ tự khởi tạo chính xác**.
