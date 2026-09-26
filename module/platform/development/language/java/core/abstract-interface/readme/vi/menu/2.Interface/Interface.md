# Interface

`abstract class` phù hợp khi các subtype thật sự chia sẻ trạng thái và cách triển khai. Nhưng nhiều lúc ta chỉ muốn nói rằng một class **có một khả năng nào đó**, bất kể nó đang thuộc cây kế thừa nào. `interface` được thiết kế rất tốt cho vai trò đó.

## <a id="interface-contract">Interface là gì?</a>

### KHÁI NIỆM

`interface` mô tả một **vai trò hoặc hợp đồng hành vi** mà class, enum, record hoặc proxy có thể thực hiện.

```java
interface Refundable {
    void refund(int amount);
}
```

Một class triển khai `Refundable` cam kết rằng bên sử dụng có thể yêu cầu `refund(...)` mà không cần biết concrete class là gì.

### VÌ SAO

Điểm mạnh của interface là giảm phụ thuộc vào cách triển khai cụ thể. Consumer có thể phụ thuộc vào capability:

```java
void processRefund(Refundable payment) {
    payment.refund(100);
}
```

thay vì phụ thuộc trực tiếp vào `CardPayment`, `WalletPayment` hay một class cụ thể khác.

## <a id="interface-fields">Field trong Interface</a>

Field khai báo trong interface mặc nhiên là:

```text
public static final
```

Ví dụ:

```java
interface Limits {
    int MAX_RETRY = 3;
}
```

`MAX_RETRY` là hằng gắn với interface, không phải trạng thái riêng của từng object triển khai interface đó.

`final` chỉ làm reference không thể gán lại. Nếu field trỏ tới một mutable object thì object phía sau vẫn có thể thay đổi, vì vậy không nên dùng interface field để expose mutable global trạng thái.

## <a id="interface-method-kinds">Các loại Method trong Interface</a>

Interface hiện đại có thể chứa nhiều loại method:

| Loại | Vai trò |
| --- | --- |
| abstract instance method | phần hợp đồng cách triển khai phải cung cấp |
| `default` method | cách triển khai mặc định có thể được kế thừa |
| `static` method | hành vi gắn với chính interface type |
| `private` method | helper nội bộ cho default/static method |

Không nên gom tất cả vào một khái niệm “method trong interface”. Mỗi loại có cách gọi, kế thừa và override khác nhau. Các chương sau sẽ đi sâu vào `default`, `static` và `private` method.

## <a id="interface-implementation">Nhiều Interface trên một Class</a>

Một class có thể `implements` nhiều interface:

```java
class CardPayment implements PaymentMethod, Refundable {
    ...
}
```

Điều này cho phép một object đảm nhận nhiều vai trò mà không cần đa kế thừa class.

Các abstract method tương thích từ nhiều interface có thể được thỏa bằng cùng một cách triển khai. Nếu nhiều `default` method xung đột, Java yêu cầu class xử lý rõ ràng; ta sẽ quay lại vấn đề này ở cuối module.

### MỐI LIÊN HỆ

Bây giờ ta đã có hai công cụ cùng mô tả hợp đồng: `abstract class` và `interface`. chương tiếp theo đặt chúng cạnh nhau để trả lời câu hỏi quan trọng nhất: **khi nào nên chọn cái nào?**
