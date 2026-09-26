# Initialization Block

Java cho phép đặt logic khởi tạo ở field initializer, static block, instance initializer và constructor. Hiểu vai trò của từng nơi giúp tránh quá trình khởi tạo trở nên khó theo dõi.

## <a id="static-initializer">Static Initializer</a>

Static initializer:

```java
static {
    ...
}
```

chạy khi class được initialize, không phải mỗi lần tạo object.

Nó có thể phù hợp cho việc thiết lập trạng thái `static` không biểu diễn thuận tiện bằng một biểu thức, nhưng logic phức tạp hoặc I/O trong static initialization làm quá trình khởi động và xử lý lỗi khó kiểm soát.

## <a id="instance-initializer">Instance Initializer</a>

Instance initializer:

```java
{
    ...
}
```

chạy cho mỗi lần tạo object, sau phần khởi tạo của superclass và trước constructor body của class hiện tại theo quy tắc initialization của Java.

Nó có thể gom logic dùng chung giữa nhiều constructor, nhưng thường khó đọc hơn việc đưa logic vào constructor/helper rõ ràng.

## <a id="initializer-use-cases">Khi nào dùng Initializer?</a>

Initializer block là cơ chế hợp lệ nhưng không nên dùng chỉ vì “Java hỗ trợ”.

Ưu tiên mã dễ theo dõi:

```text
field initializer đơn giản
→ tốt cho trạng thái mặc định rõ ràng

constructor/helper
→ tốt cho validation và initialization cần ngữ cảnh

initializer block
→ dùng khi thật sự làm luồng khởi tạo rõ hơn
```

Chương tiếp theo ghép các mảnh này thành **thứ tự initialization chính xác**.
