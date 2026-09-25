# Kế thừa (Inheritance)

Đóng gói giúp từng đối tượng tự bảo vệ ranh giới của mình. Bước tiếp theo là mô hình hóa trường hợp một kiểu cụ thể có thể được sử dụng như một kiểu tổng quát hơn.

Ví dụ, một `CardPayment` có thể được sử dụng ở nơi chương trình chỉ yêu cầu một `PaymentMethod`.

## <a id="is-a-subtyping">Kế thừa và kiểu con</a>

### KHÁI NIỆM — “is-a” nên được hiểu như thế nào?

Trong Java, khi một class dùng `extends`, nó tạo ra quan hệ **kiểu con (subtype)** với class cha.

```java
PaymentMethod payment = new CardPayment();
```

Ở đây `CardPayment` có thể được gán cho một biến có kiểu `PaymentMethod`.

Tuy nhiên, “is-a” không nên chỉ được hiểu là “hai class có vài field giống nhau”. Ý nghĩa quan trọng hơn là:

> Một đối tượng của kiểu con có thể được dùng ở nơi kiểu cha được yêu cầu mà vẫn giữ được hành vi hợp lý theo hợp đồng của kiểu cha.

Nếu `CardPayment` chỉ kế thừa để dùng lại vài dòng code nhưng lại phá những kỳ vọng quan trọng của `PaymentMethod`, chương trình có thể vẫn biên dịch nhưng thiết kế đã có vấn đề.

### MỐI LIÊN HỆ — kế thừa và subtyping không hoàn toàn cùng mục tiêu

Kế thừa class trong Java có thể mang hai mục đích khác nhau:

```text
Dùng lại phần triển khai
→ nhận lại một phần trạng thái hoặc method từ class cha

Tạo quan hệ kiểu con
→ object của class con dùng được ở nơi class cha được yêu cầu
```

Hai mục đích này thường bị trộn lẫn.

Nếu mục tiêu duy nhất là dùng lại vài method, `composition` có thể tạo ít phụ thuộc hơn. Nếu bên sử dụng thực sự cần thay thế nhiều cách triển khai thông qua một kiểu chung, quan hệ kiểu con mới có ý nghĩa rõ ràng hơn.

`interface` cũng tạo được quan hệ kiểu con mà không cần kế thừa phần triển khai từ class cha. Module `abstract-interface` sẽ đi sâu hơn vào phần này.

### THỰC HÀNH — kiểm tra “is-a” bằng hành vi

Đừng chỉ hỏi:

> `CardPayment` có nghe giống một loại payment không?

Hãy hỏi mạnh hơn:

> Mọi đoạn mã chỉ biết `PaymentMethod` có thể dùng `CardPayment` mà không cần trường hợp xử lý riêng hay không?

## <a id="inherited-state-behavior">Thành phần được kế thừa</a>

### CƠ CHẾ — một object của class con gồm những gì?

Đối tượng của class con chứa phần trạng thái và hành vi được định nghĩa từ class cha, đồng thời có thể bổ sung phần riêng của class con.

```java
class PaymentMethod {
    String provider() { return "generic"; }
}

class CardPayment extends PaymentMethod {
    String cardNetwork() { return "VISA"; }
}
```

Một `CardPayment` có thể dùng `provider()` nếu method đó có quyền truy cập phù hợp, đồng thời có thêm `cardNetwork()`.

Field `private` của class cha vẫn tồn tại trong object, nhưng class con không truy cập trực tiếp nó bằng member access thông thường.

### Constructor không được kế thừa

Constructor có nhiệm vụ thiết lập trạng thái ban đầu cho từng phần của object.

Constructor của class con phải gọi constructor của class cha thông qua:

```java
super(...)
```

hoặc compiler tự chèn `super()` nếu lời gọi đó hợp lệ.

Điều này quan trọng vì phần trạng thái và các điều kiện bất biến của class cha phải được thiết lập đúng trước khi việc khởi tạo class con hoàn tất.

### MỐI LIÊN HỆ — được kế thừa method chưa phải là đa hình

Việc class con có sẵn method từ class cha chưa phải điểm quan trọng nhất.

Điều thú vị xuất hiện khi class con **ghi đè (override)** một instance method và bên gọi giữ biến tham chiếu có kiểu cha. Khi đó Java phải quyết định phần triển khai nào sẽ chạy tại thời điểm thực thi (runtime).

Đó là cầu nối sang **đa hình (polymorphism)**.

## <a id="inheritance-coupling">Rủi ro của kế thừa</a>

### VÌ SAO — kế thừa mạnh nhưng tạo phụ thuộc mạnh

Class con có thể phụ thuộc không chỉ vào hợp đồng công khai của class cha mà còn vào:

- thứ tự khởi tạo;
- protected method;
- cách class cha gọi các method có thể override;
- những giả định bên trong cách triển khai của class cha.

Ví dụ, nếu constructor của class cha gọi một method có thể bị override, hành vi của class con có thể chạy trước khi trạng thái riêng của class con được khởi tạo đầy đủ.

Đây là một dạng **fragile base class**: thay đổi ở class cha có thể gây ảnh hưởng bất ngờ tới class con.

### ĐÁNH ĐỔI — khi nào inheritance hợp lý?

Kế thừa phù hợp hơn khi:

- quan hệ kiểu con có ý nghĩa thực sự về hành vi;
- bên sử dụng cần khả năng thay thế giữa các kiểu con;
- class cha có hợp đồng tương đối ổn định;
- class con không phải override hàng loạt method chỉ để tránh các giả định của class cha.

Kế thừa đáng nghi khi lý do chính chỉ là:

> “Tôi muốn dùng lại vài method cho đỡ phải copy.”

Trong trường hợp đó, `composition` hoặc `delegation` thường rõ ràng và ít phụ thuộc hơn.

### MỐI LIÊN HỆ — từ kế thừa sang đa hình

Khi một biến kiểu `PaymentMethod` có thể trỏ tới `CardPayment`, `WalletPayment` hoặc một cách triển khai khác, bên gọi không nên phải viết:

```java
if (payment instanceof CardPayment) { ... }
else if (payment instanceof WalletPayment) { ... }
```

cho mọi loại payment.

**Đa hình (polymorphism)** giải quyết chính vấn đề này.
