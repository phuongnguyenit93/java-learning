# Interface như hợp đồng hành vi

Lớp trừu tượng phù hợp khi các lớp con thật sự chia sẻ trạng thái và phần triển khai. Nhưng nhiều lúc ta chỉ muốn nói rằng một kiểu **có một khả năng hoặc tuân theo một hợp đồng**, bất kể nó nằm trong cây kế thừa lớp nào. `interface` giải quyết bài toán đó.

## <a id="interface-contract">Interface là gì?</a>

### KHÁI NIỆM

`interface` mô tả một **vai trò hoặc hợp đồng hành vi** mà nhiều loại lớp có thể thực hiện. Trong Java 21, lớp thông thường, lớp `enum`, lớp `record` và cả lớp đại diện (proxy) được tạo động đều có thể triển khai interface khi phù hợp.

```java
interface PaymentMethod {
    void pay(int amount);
}
```

Một lớp triển khai `PaymentMethod` cam kết rằng bên sử dụng có thể yêu cầu `pay(...)` thông qua kiểu `PaymentMethod` mà không cần biết lớp cụ thể phía sau là gì.

### VÌ SAO

Interface giúp giảm phụ thuộc vào cách triển khai cụ thể:

```java
void checkout(PaymentMethod payment) {
    payment.pay(100);
}
```

`checkout(...)` phụ thuộc vào hợp đồng `PaymentMethod`, không phụ thuộc trực tiếp vào `CardPayment`, `WalletPayment` hay một lớp cụ thể khác.

## <a id="interface-fields">Trường (field) trong interface</a>

Trường khai báo trong interface mặc nhiên là:

```text
public static final
```

Ví dụ:

```java
interface Limits {
    int MAX_RETRY = 3;
}
```

`MAX_RETRY` trong ví dụ này là một biến hằng (constant variable) gắn với chính interface, không phải trạng thái riêng của từng đối tượng triển khai interface đó. Nói tổng quát hơn, mọi trường khai báo trong interface đều là `public static final`; chỉ những trường thỏa thêm quy tắc về biến hằng của Java mới là hằng tại thời điểm biên dịch (compile-time constant).

Trường trong interface phải có giá trị khởi tạo ngay tại khai báo. Đây là yêu cầu của khai báo field trong interface; các field này là `static final` và interface không dùng hàm khởi tạo đối tượng để gán chúng sau đó:

```java
interface InvalidLimits {
    int MAX_RETRY; // lỗi biên dịch: MAX_RETRY chưa được khởi tạo
}
```

`final` ngăn chính biến trường được gán một giá trị khác sau khi đã khởi tạo. Nếu giá trị của trường là một tham chiếu tới đối tượng có thể thay đổi thì đối tượng phía sau vẫn có thể đổi trạng thái. Vì vậy không nên dùng trường của interface để công khai trạng thái toàn cục có thể thay đổi.

## <a id="interface-method-kinds">Các loại phương thức trong interface</a>

Interface hiện đại có thể chứa nhiều loại phương thức:

| Loại | Vai trò |
| --- | --- |
| phương thức abstract của đối tượng | phần hợp đồng mà cách triển khai phải cung cấp |
| phương thức `default` | hành vi mặc định của đối tượng có thể được kế thừa |
| phương thức `static` | hành vi thuộc chính kiểu interface |
| phương thức `private` | hỗ trợ nội bộ; có thể là phương thức của đối tượng hoặc `static` |

Một phương thức abstract của đối tượng không ghi từ khóa kiểm soát truy cập trong interface vẫn mặc nhiên là `public abstract`:

```java
interface PaymentMethod {
    void pay(int amount); // public abstract
}
```

Vì vậy lớp triển khai không được giảm mức truy cập:

```java
class CardPayment extends BasePayment implements PaymentMethod {
    @Override
    public void pay(int amount) {
        // phần triển khai
    }
}
```

Các loại phương thức trên có quy tắc gọi, kế thừa và ghi đè khác nhau. Ở đây chỉ cần nhận diện chúng; phần sau sẽ đi sâu vào `default`, `static` và `private`.

## <a id="interface-implementation">Một lớp triển khai nhiều interface</a>

Một lớp có thể `implements` nhiều interface. Ví dụ xuyên suốt có thể kết hợp hợp đồng công khai, phần triển khai dùng chung và một khả năng độc lập:

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

Điều này cho phép một đối tượng đảm nhận nhiều vai trò mà không cần đa kế thừa lớp.

Các yêu cầu abstract tương thích từ nhiều interface có thể được thỏa bởi cùng một phương thức triển khai. Nếu nhiều phương thức `default` cạnh tranh nhau, Java có các quy tắc giải quyết riêng; phần đó được học sau khi ta hiểu kế thừa interface và các loại phương thức interface.

### MỐI LIÊN HỆ

Ta đã có hai công cụ có thể cùng tham gia mô tả hợp đồng: lớp trừu tượng và interface. Bước tiếp theo đặt chúng cạnh nhau để trả lời câu hỏi quan trọng: **khi nào nên chọn cơ chế nào?**
