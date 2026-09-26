# Interface

`abstract class` phù hợp khi các kiểu con thật sự chia sẻ trạng thái và cách triển khai. Nhưng nhiều lúc ta chỉ muốn nói rằng một class **có một khả năng nào đó**, bất kể nó đang thuộc cây kế thừa nào. `interface` phù hợp với vai trò đó vì hợp đồng không buộc cách triển khai phải nằm trong cùng một cây class.

## <a id="interface-contract">Interface là gì?</a>

### KHÁI NIỆM

`interface` mô tả một **vai trò hoặc hợp đồng hành vi** mà class, enum, record hoặc proxy có thể thực hiện.

```java
interface PaymentMethod {
    void pay(int amount);
}
```

Một class triển khai `PaymentMethod` cam kết rằng bên sử dụng có thể yêu cầu `pay(...)` mà không cần biết class cụ thể là gì.

### VÌ SAO

Điểm mạnh của interface là giảm phụ thuộc vào cách triển khai cụ thể. Bên sử dụng có thể phụ thuộc vào hợp đồng:

```java
void checkout(PaymentMethod payment) {
    payment.pay(100);
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

Field trong interface phải có giá trị khởi tạo ngay tại khai báo. Interface không có constructor instance để gán giá trị cho field sau:

```java
interface InvalidLimits {
    int MAX_RETRY; // compile error: variable MAX_RETRY not initialized
}
```

`final` chỉ làm tham chiếu không thể được gán lại. Nếu field trỏ tới một object có thể thay đổi thì object phía sau vẫn có thể đổi trạng thái, vì vậy không nên dùng interface field để công khai trạng thái toàn cục có thể thay đổi.

## <a id="interface-method-kinds">Các loại Method trong Interface</a>

Interface hiện đại có thể chứa nhiều loại method:

| Loại | Vai trò |
| --- | --- |
| abstract instance method | phần hợp đồng cách triển khai phải cung cấp |
| `default` method | cách triển khai mặc định có thể được kế thừa |
| `static` method | hành vi gắn với chính interface type |
| `private` method | helper nội bộ; có thể là instance hoặc `static` |

Một abstract instance method không ghi access modifier trong interface vẫn mặc nhiên là `public abstract`:

```java
interface PaymentMethod {
    void pay(int amount); // public abstract
}
```

Vì vậy class triển khai không được giảm mức truy cập:

```java
class CardPayment extends BasePayment implements PaymentMethod {
    @Override
    public void pay(int amount) {
        // phần triển khai
    }
}
```

Không nên gom tất cả vào một khái niệm “method trong interface”. Mỗi loại có cách gọi, kế thừa và override khác nhau. Các chương sau sẽ đi sâu vào `default`, `static` và `private` method.

## <a id="interface-implementation">Nhiều Interface trên một Class</a>

Một class có thể `implements` nhiều interface. Đây là lúc ví dụ xuyên suốt bắt đầu kết hợp hợp đồng, phần triển khai chung và khả năng bổ sung:

```java
interface Refundable {
    void refund(int amount);
}

class CardPayment extends BasePayment
        implements PaymentMethod, Refundable {

    @Override
    public void pay(int amount) { ... }

    @Override
    public void refund(int amount) { ... }
}
```

Điều này cho phép một object đảm nhận nhiều vai trò mà không cần đa kế thừa class.

Các abstract method tương thích từ nhiều interface có thể được thỏa bằng cùng một cách triển khai. Nếu nhiều `default` method xung đột, Java yêu cầu class xử lý rõ ràng; ta sẽ quay lại vấn đề này ở cuối module.

### MỐI LIÊN HỆ

Bây giờ ta đã có hai công cụ cùng mô tả hợp đồng: `abstract class` và `interface`. Chương tiếp theo đặt chúng cạnh nhau để trả lời câu hỏi quan trọng nhất: **khi nào nên chọn cái nào?**
