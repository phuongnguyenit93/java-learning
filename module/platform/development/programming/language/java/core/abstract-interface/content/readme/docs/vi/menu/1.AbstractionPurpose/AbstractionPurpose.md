# Vì sao Java cần lớp trừu tượng và interface?

Trước khi học cú pháp `abstract class` hay `interface`, cần hiểu bài toán chung mà hai cơ chế này giải quyết. Trong mã thực tế, bên sử dụng thường không nên phụ thuộc vào từng lớp triển khai cụ thể nếu điều nó thật sự cần chỉ là **một tập hành vi được cam kết**.

## <a id="abstraction-mechanism-purpose">Bài toán mà lớp trừu tượng và interface giải quyết</a>

### KHÁI NIỆM

Giả sử hàm thanh toán phụ thuộc trực tiếp vào `CardPayment`:

```java
void checkout(CardPayment payment) {
    payment.pay(100);
}
```

Cách viết này hoạt động, nhưng bên sử dụng bị gắn với một cách triển khai cụ thể. Khi hệ thống có thêm `WalletPayment`, `BankTransferPayment` hoặc một cách thanh toán khác, ta muốn bên gọi chỉ cần biết **hợp đồng thanh toán** thay vì biết từng lớp cụ thể.

Java cho phép biểu diễn nhu cầu đó bằng một kiểu chung. Trong module này, hai công cụ quan trọng là:

```text
lớp trừu tượng (abstract class)
→ phù hợp khi một nhóm lớp có quan hệ gần nhau
→ cần dùng chung trạng thái, quy tắc khởi tạo hoặc một phần cách triển khai

interface
→ phù hợp khi cần mô tả vai trò/khả năng dưới dạng hợp đồng
→ nhiều lớp ở các cây kế thừa khác nhau vẫn có thể cùng thực hiện
```

### VÌ SAO

Mục đích của hai cơ chế **không đơn giản là tạo ra kiểu “không thể `new`”**. Điều quan trọng hơn là tách ba nhu cầu thường bị trộn lẫn:

```text
bên sử dụng cần biết điều gì?
→ hợp đồng

các lớp liên quan cần dùng chung điều gì?
→ trạng thái / quy tắc khởi tạo / phần triển khai

một lớp cần đảm nhận thêm vai trò nào?
→ khả năng được biểu diễn bằng interface
```

Khi ba nhu cầu này được tách rõ, mã ít phụ thuộc vào lớp cụ thể hơn và mô hình kiểu dễ mở rộng hơn.

## <a id="abstraction-mechanism-roles">Hai cơ chế có vai trò khác nhau</a>

| Câu hỏi thiết kế | Lớp trừu tượng | Interface |
| --- | --- | --- |
| Có thể giữ trạng thái của đối tượng? | Có | Không có trạng thái riêng cho từng đối tượng |
| Có hàm khởi tạo? | Có | Không |
| Có thể cung cấp hành vi có phần triển khai? | Có | Có, qua `default`, `static`, `private` với vai trò khác nhau |
| Một lớp có thể kế thừa/triển khai bao nhiêu? | Chỉ `extends` một lớp | Có thể `implements` nhiều interface |
| Thường dùng để biểu diễn | họ lớp có quan hệ gần và dùng chung phần triển khai | vai trò/khả năng/hợp đồng |

Đây mới là bản đồ định hướng; các chương sau sẽ giải thích từng quy tắc và giới hạn cụ thể.

## <a id="abstraction-running-example">Ví dụ xuyên suốt module</a>

Các chương dùng cùng một mô hình để tránh học từng khái niệm rời rạc:

```text
PaymentMethod
→ interface mô tả hợp đồng thanh toán

BasePayment
→ lớp trừu tượng giữ trạng thái và phần triển khai chung

CardPayment
→ lớp cụ thể hoàn thiện hành vi

Refundable
→ interface bổ sung một khả năng độc lập
```

Ví dụ này sẽ lần lượt cho thấy khi nào cần hợp đồng, khi nào cần phần triển khai dùng chung, cách interface kế thừa nhau và cách xử lý xung đột giữa các hành vi mặc định.

## <a id="abstraction-learning-roadmap">Lộ trình học</a>

```text
1. Vì sao Java cần lớp trừu tượng và interface?
        ↓
2. Lớp trừu tượng (abstract class)
        ↓
3. Interface như hợp đồng hành vi
        ↓
4. Chọn lớp trừu tượng hay interface?
        ↓
5. Kế thừa và kết hợp interface
        ↓
6. Phương thức default, static và private trong interface
        ↓
7. Đa kế thừa qua interface và giải quyết xung đột
        ↓
8. Tổng hợp thiết kế bằng lớp trừu tượng và interface
```

Kết thúc module, mục tiêu không phải chỉ nhớ cú pháp. Bạn cần có khả năng giải thích **vì sao một thiết kế chọn lớp trừu tượng, interface hoặc kết hợp cả hai**, và dự đoán được các quy tắc quan trọng khi các interface kế thừa hoặc cung cấp hành vi mặc định.
