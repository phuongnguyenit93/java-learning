# Kế thừa và quan hệ kiểu con

Đóng gói giúp từng đối tượng tự bảo vệ ranh giới của mình. Bước tiếp theo là mô hình hóa trường hợp một kiểu cụ thể có thể được sử dụng như một kiểu tổng quát hơn.

Ví dụ, một `CardPayment` có thể được sử dụng ở nơi chương trình chỉ yêu cầu một `PaymentMethod`.

## <a id="is-a-subtyping">Kế thừa và kiểu con</a>

### KHÁI NIỆM — “is-a” nên được hiểu như thế nào?

Trong Java, khi một class dùng `extends`, nó tạo ra quan hệ **kiểu con (subtype)** với class cha.

```java
PaymentMethod payment = new CardPayment();
```

Ở đây `CardPayment` có thể được gán cho một biến có kiểu `PaymentMethod`.

Tuy nhiên, “is-a” không nên chỉ được hiểu là “hai class có vài trường giống nhau”. Ý nghĩa quan trọng hơn là:

> Một đối tượng của kiểu con có thể được dùng ở nơi kiểu cha được yêu cầu mà vẫn giữ được hành vi hợp lý theo hợp đồng của kiểu cha.

Nếu `CardPayment` chỉ kế thừa để dùng lại vài dòng mã nhưng lại phá những kỳ vọng quan trọng của `PaymentMethod`, chương trình có thể vẫn biên dịch nhưng thiết kế đã có vấn đề.

### MỐI LIÊN HỆ — kế thừa và subtyping không hoàn toàn cùng mục tiêu

Kế thừa class trong Java có thể mang hai mục đích khác nhau:

```text
Dùng lại phần triển khai
→ nhận lại một phần trạng thái hoặc phương thức từ class cha

Tạo quan hệ kiểu con
→ đối tượng của class con dùng được ở nơi class cha được yêu cầu
```

Hai mục đích này thường bị trộn lẫn.

Nếu mục tiêu duy nhất là dùng lại vài phương thức, kết hợp đối tượng (`composition`) có thể tạo ít phụ thuộc hơn. Nếu bên sử dụng thực sự cần thay thế nhiều cách triển khai thông qua một kiểu chung, quan hệ kiểu con mới có ý nghĩa rõ ràng hơn.

`interface` cũng tạo được quan hệ kiểu con mà không cần kế thừa phần triển khai từ class cha. Mô-đun `abstract-interface` sẽ đi sâu hơn vào phần này.

### GIỚI HẠN — Java chỉ cho một class cha trực tiếp

Một class Java chỉ có thể `extends` **một class trực tiếp**. Điều này giúp tránh một số xung đột trạng thái/cách triển khai của đa kế thừa class, nhưng đồng thời làm cho việc chọn class cha trở thành một quyết định tạo phụ thuộc khá mạnh.

`final class` chặn việc tạo class con; `final` vì vậy không chỉ là cú pháp, mà còn có thể biểu đạt rằng kiểu đó không mở rộng hợp đồng bằng kế thừa class.

### THỰC HÀNH — kiểm tra “is-a” bằng hành vi

Đừng chỉ hỏi:

> `CardPayment` có nghe giống một loại payment không?

Hãy hỏi mạnh hơn:

> Mọi đoạn mã chỉ biết `PaymentMethod` có thể dùng `CardPayment` mà không cần trường hợp xử lý riêng hay không?

## <a id="inherited-state-behavior">Thành phần được kế thừa</a>

### CƠ CHẾ — một đối tượng của class con gồm những gì?

Đối tượng của class con chứa phần trạng thái và hành vi được định nghĩa từ class cha, đồng thời có thể bổ sung phần riêng của class con.

```java
class PaymentMethod {
    String provider() { return "generic"; }
}

class CardPayment extends PaymentMethod {
    String cardNetwork() { return "VISA"; }
}
```

Một `CardPayment` có thể dùng `provider()` nếu phương thức đó có quyền truy cập phù hợp, đồng thời có thêm `cardNetwork()`.

Trường `private` của class cha vẫn tồn tại trong đối tượng, nhưng class con không truy cập trực tiếp nó bằng cú pháp truy cập thành viên thông thường.

### PHÂN BIỆT — nơi khai báo, việc được kế thừa và quyền truy cập là ba câu hỏi khác nhau

Ba câu hỏi sau khác nhau:

```text
Thành viên được khai báo ở đâu?
        ↓
Thành viên có được kế thừa vào kiểu con không?
        ↓
Mã hiện tại có quyền truy cập thành viên đó không?
```

Ví dụ, trạng thái `private` của class cha vẫn là một phần trạng thái của đối tượng `Child`, nhưng mã nguồn của `Child` không được truy cập trực tiếp trường đó. Ngược lại, một phương thức của cha có thể được kế thừa và dùng được nếu quy tắc truy cập cho phép.

Chi tiết đầy đủ của `private` / package-private / `protected` / `public` thuộc `class-object → Access Modifier`; chương này chỉ giữ mô hình tư duy cần cho kế thừa.

### RANH GIỚI VỚI CLASS-OBJECT — hàm khởi tạo không được kế thừa

Hàm khởi tạo **không được kế thừa**. Khi tạo đối tượng của class con, phần trạng thái do class cha sở hữu vẫn phải được khởi tạo thông qua chuỗi `this(...)` / `super(...)` phù hợp.

Một hàm khởi tạo của class con có thể ủy quyền cho hàm khởi tạo khác cùng class bằng `this(...)`; chuỗi đó cuối cùng phải dẫn tới hàm khởi tạo của class cha bằng `super(...)` tường minh hoặc `super()` được chèn ngầm khi hợp lệ. Việc khởi tạo phần class cha hoàn tất trước khi các bộ khởi tạo trường/khối khởi tạo của class con chạy, rồi mới tới thân hàm khởi tạo của class con.

Chi tiết đầy đủ về `this(...)`, `super(...)`, chuỗi hàm khởi tạo và thứ tự khởi tạo thuộc mô-đun `class-object → this, super và chuỗi khởi tạo`. OOP chỉ cần giữ hệ quả thiết kế: **class con có thể phụ thuộc vào cách class cha thiết lập và quản lý phần trạng thái của nó**.

### MỐI LIÊN HỆ — được kế thừa phương thức chưa phải là đa hình

Việc class con có sẵn phương thức từ class cha chưa phải điểm quan trọng nhất.

Điều thú vị xuất hiện khi class con **ghi đè (override)** một phương thức đối tượng và bên gọi giữ biến tham chiếu có kiểu cha. Khi đó Java phải quyết định phần triển khai nào sẽ chạy tại thời điểm thực thi.

Đó là cầu nối sang **đa hình (polymorphism)**.

## <a id="inheritance-coupling">Rủi ro của kế thừa</a>

### VÌ SAO — kế thừa mạnh nhưng tạo phụ thuộc mạnh

Class con có thể phụ thuộc không chỉ vào hợp đồng công khai của class cha mà còn vào:

- thứ tự khởi tạo;
- phương thức `protected`;
- cách class cha gọi các phương thức có thể bị ghi đè;
- những giả định bên trong cách triển khai của class cha.

Một ví dụ điển hình là class cha gọi phương thức có thể bị ghi đè trong quá trình khởi tạo. Phân phối động vẫn có thể đi vào phần triển khai của class con **trước khi trạng thái riêng của class con được khởi tạo đầy đủ**; trường tham chiếu khi đó vẫn có thể mang giá trị mặc định `null`, nên code của class con có thể gặp `NullPointerException` nếu giả định trạng thái đã sẵn sàng.

Cơ chế và ví dụ thực thi chi tiết thuộc `class-object → Vòng đời tạo Object → Dynamic Dispatch trong Constructor`. Trong OOP, điều cần giữ là **hệ quả coupling**: mức phụ thuộc do kế thừa không chỉ nằm ở API công khai; class con còn có thể bị ảnh hưởng bởi **thứ tự vòng đời và quyết định thực thi nội bộ của class cha**.

Đây là một dạng **fragile base class** (class cha dễ gây hiệu ứng dây chuyền): thay đổi ở class cha có thể gây ảnh hưởng bất ngờ tới class con.

### ĐÁNH ĐỔI — khi nào kế thừa hợp lý?

Kế thừa phù hợp hơn khi:

- quan hệ kiểu con có ý nghĩa thực sự về hành vi;
- bên sử dụng cần khả năng thay thế giữa các kiểu con;
- class cha có hợp đồng tương đối ổn định;
- class con không phải ghi đè hàng loạt phương thức chỉ để tránh các giả định của class cha.

Kế thừa đáng nghi khi lý do chính chỉ là:

> “Tôi muốn dùng lại vài phương thức để khỏi phải sao chép mã.”

Trong trường hợp đó, kết hợp đối tượng (`composition`) hoặc ủy quyền (`delegation`) thường rõ ràng và ít phụ thuộc hơn.

### MỐI LIÊN HỆ — từ kế thừa sang đa hình

Khi một biến kiểu `PaymentMethod` có thể trỏ tới `CardPayment`, `WalletPayment` hoặc một cách triển khai khác, bên gọi không nên phải viết:

```java
if (payment instanceof CardPayment) { ... }
else if (payment instanceof WalletPayment) { ... }
```

cho mọi loại payment.

**Đa hình (polymorphism)** giải quyết chính vấn đề này.
