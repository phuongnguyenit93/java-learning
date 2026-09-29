# Đóng gói (Encapsulation)

Ở chương trước, ta đã đưa trạng thái và trách nhiệm về cho đối tượng. Nhưng nếu đoạn mã bên ngoài vẫn có thể thay đổi trạng thái tùy ý thì đối tượng chưa thực sự kiểm soát được trách nhiệm của mình.

**Đóng gói (encapsulation)** giải quyết vấn đề đó bằng cách kiểm soát những gì bên ngoài được phép nhìn thấy và thay đổi.

## <a id="encapsulation-model">Đóng gói là gì?</a>

### KHÁI NIỆM — đóng gói là gì?

Đóng gói là việc **kiểm soát quyền truy cập vào trạng thái và chi tiết triển khai**, để bên ngoài tương tác thông qua những hành vi có ý nghĩa thay vì tự điều khiển mọi chi tiết bên trong.

`private` là một cơ chế của Java hỗ trợ đóng gói, nhưng `private` không phải bản thân khái niệm đóng gói.

Ví dụ sau dùng field `private` nhưng ranh giới vẫn yếu:

```java
class Account {
    private int balance;

    int getBalance() { return balance; }
    void setBalance(int balance) { this.balance = balance; }
}
```

Đoạn mã bên ngoài vẫn có thể gọi:

```java
account.setBalance(-100);
```

Field đã được ẩn về mặt cú pháp, nhưng quy tắc “số dư không được âm” vẫn chưa được bảo vệ.

Một cách thiết kế tốt hơn là công khai hành vi theo đúng ý định:

```java
class Account {
    private int balance = 100;

    void withdraw(int amount) {
        if (amount <= 0 || amount > balance) {
            throw new IllegalArgumentException("invalid withdrawal");
        }
        balance -= amount;
    }
}
```

### VÌ SAO — điều cần che giấu thực sự là gì?

Mục tiêu không phải là biến mọi field thành “bí mật”. Điều quan trọng là bên ngoài không nên phụ thuộc vào **cách lưu dữ liệu và cách ra quyết định nội bộ** nếu đó không phải trách nhiệm của nó.

Nếu sau này `balance` đổi từ `int` sang một kiểu `Money`, đoạn mã chỉ gọi `withdraw(...)` có thể không cần sửa. Ngược lại, đoạn mã tự đọc `balance`, tự tính toán rồi gọi setter sẽ phụ thuộc chặt vào cách biểu diễn dữ liệu cũ.

### MỐI LIÊN HỆ — đóng gói và trừu tượng hóa khác nhau thế nào?

Hai khái niệm thường đi cùng nhau nhưng trả lời hai câu hỏi khác nhau:

```text
Đóng gói (Encapsulation)
→ ai được phép thấy hoặc thay đổi trạng thái và chi tiết triển khai?

Trừu tượng hóa (Abstraction)
→ bên sử dụng thực sự cần nhìn thấy hợp đồng và hành vi nào?
```

Ta sẽ quay lại trừu tượng hóa ở cuối module. Ở đây chỉ cần nhớ: access modifier là công cụ; **ranh giới có ý nghĩa mới là mục tiêu**.

### PITFALL — field `private` nhưng trạng thái mutable vẫn có thể bị lộ

Đóng gói không chỉ bị phá bởi setter. Một object cũng có thể làm lộ **representation bên trong** thông qua reference mutable:

```java
class Order {
    private final List<String> items = new ArrayList<>();

    public List<String> getItems() {
        return items;
    }
}
```

Bên gọi có thể làm:

```java
order.getItems().clear();
```

Field `items` vẫn là `private`, nhưng caller đã nhận đúng reference mà `Order` đang dùng nội bộ và có thể sửa trạng thái mà không đi qua bất kỳ rule nào của `Order`.

Vì vậy cần phân biệt:

```text
private field
≠
internal state automatically protected

mutable reference escapes
→ caller có thể sửa representation bên trong
→ invariant vẫn có thể bị phá
```

Tùy hợp đồng, object có thể trả về bản sao, immutable view hoặc chỉ cung cấp những operation có ý nghĩa thay vì expose collection mutable trực tiếp. Chi tiết API của collection thuộc module `collection`; ở đây điều cần giữ là mental model về **representation exposure**.

## <a id="encapsulation-access-modifiers">Access Modifier và Encapsulation</a>

### MỐI LIÊN HỆ — access modifier giúp hiện thực hóa đóng gói như thế nào?

Đóng gói là **khái niệm thiết kế**: đối tượng phải kiểm soát được trạng thái và chi tiết triển khai nào được phép lộ ra ngoài.

Access modifier là một trong những **cơ chế của Java** giúp biến ranh giới thiết kế đó thành ràng buộc trong mã nguồn.

Java có bốn mức truy cập chính cho member:

```text
private
→ chỉ class sở hữu truy cập trực tiếp

package-private
→ các class trong cùng package có thể truy cập

protected
→ mở thêm quyền truy cập cho quan hệ kế thừa theo các quy tắc của Java

public
→ trở thành phần mà mã bên ngoài có thể truy cập và phụ thuộc
```

Có thể hình dung mối quan hệ như sau:

```text
Encapsulation
→ quyết định ranh giới nào nên tồn tại

Access Modifier
→ giúp Java thực thi một phần ranh giới đó
```

### VÌ SAO — `private` chưa đủ để tạo ra đóng gói tốt

Ví dụ:

```java
class Account {
    private int balance;

    public void setBalance(int balance) {
        this.balance = balance;
    }
}
```

Field là `private`, nhưng `public setBalance(...)` vẫn cho phép bên ngoài đưa object vào trạng thái không hợp lệ.

Ngược lại:

```java
class Account {
    private int balance;

    public void withdraw(int amount) {
        if (amount <= 0 || amount > balance) {
            throw new IllegalArgumentException("invalid withdrawal");
        }
        balance -= amount;
    }
}
```

Ở đây `private` giới hạn quyền truy cập trực tiếp, còn `withdraw(...)` tạo ra một **đường thay đổi trạng thái có kiểm soát**.

Vì vậy:

```text
private
≠
encapsulation

private + API có ranh giới hợp lý + quy tắc được bảo vệ
→ hỗ trợ encapsulation tốt hơn
```

### GHI CHÚ — tìm hiểu chi tiết Access Modifier ở đâu?

Phần này chỉ giải thích **mối quan hệ giữa Encapsulation và Access Modifier trong thiết kế OOP**.

Để học chi tiết các quy tắc của Java như `private`, package-private, `protected`, `public`, đặc biệt là `protected` khác package và phạm vi truy cập chính xác, hãy xem module **Class Object → Access Modifier**.

## <a id="invariant-protection">Bảo vệ điều kiện bất biến</a>

### KHÁI NIỆM — invariant là gì?

**Invariant** có thể hiểu là một **điều kiện luôn phải đúng để đối tượng được xem là hợp lệ**.

Ví dụ:

```text
Account.balance >= 0
Order.total >= 0
ShoppingCart không chứa sản phẩm có quantity <= 0
```

Constructor hoặc factory nên tạo ra đối tượng ở trạng thái hợp lệ. Sau đó, mọi phương thức công khai làm thay đổi trạng thái phải tiếp tục giữ các điều kiện đó đúng.

Có thể nhìn vòng đời invariant như một chuỗi liên tục:

```text
construction
→ object bắt đầu hợp lệ
→ mỗi public state transition kiểm tra rule cần thiết
→ object vẫn hợp lệ sau transition
```

Nếu constructor tạo ra trạng thái sai, hoặc một method public cho phép đi vòng qua rule, encapsulation đã thất bại dù field vẫn là `private`.

### CƠ CHẾ — thay đổi trạng thái thông qua hành vi có ý nghĩa

Thay vì cung cấp setter cho từng field, hãy cung cấp phương thức biểu diễn đúng hành động cần thực hiện:

```java
account.withdraw(30);
```

Phương thức `withdraw` có đủ thông tin để kiểm tra:

- số tiền phải lớn hơn `0`;
- số tiền không được vượt quá số dư;
- sau khi rút, số dư vẫn hợp lệ.

Bên gọi không cần biết số dư đang được lưu như thế nào.

### MINH CHỨNG — `EncapsulationController#invariantProtection()`

Ví dụ thực thi của module tạo một `Account` có số dư ban đầu là `100`, rút `30`, sau đó thử rút thêm `100`.

Điều cần quan sát không chỉ là exception. Điểm quan trọng là:

```text
yêu cầu không hợp lệ bị từ chối
        +
trạng thái cuối vẫn hợp lệ
        =
điều kiện bất biến được bảo vệ
```

Đó là giá trị thực tế của đóng gói: đối tượng tự chịu trách nhiệm về các thay đổi trạng thái của chính nó.

### THỰC HÀNH — trước khi viết setter, hãy tìm invariant

Trước khi thêm một setter, hãy hỏi:

> Field này có tham gia vào quy tắc nào cùng với các field khác không?

Nếu có, một phương thức thể hiện đúng ý định thường an toàn hơn nhiều so với việc cho phép thay đổi từng field độc lập.

## <a id="tell-dont-ask-boundary">Tell, Don't Ask</a>

### KHÁI NIỆM — “Tell, don't ask” muốn nói gì?

`Tell, don't ask` là một nguyên tắc gợi ý trong thiết kế OOP: thay vì lấy toàn bộ dữ liệu ra ngoài rồi tự quyết định, hãy yêu cầu đối tượng thực hiện hành vi thuộc trách nhiệm của nó.

Ví dụ quy tắc đang bị kéo ra ngoài đối tượng:

```java
if (account.getBalance() >= amount) {
    account.setBalance(account.getBalance() - amount);
}
```

Phiên bản thể hiện đúng ý định hơn:

```java
account.withdraw(amount);
```

Ở phiên bản thứ hai, bên gọi chỉ nói **muốn rút tiền**. Chính `Account` giữ quy tắc kiểm tra số dư.

### VÌ SAO — tránh lặp lại quy tắc ở nhiều nơi

Nếu nhiều đoạn mã cùng tự kiểm tra số dư, cùng tự tính toán và cùng tự cập nhật field, quy tắc sẽ bị lặp lại.

Khi sau này thêm phí giao dịch hoặc số tiền dự phòng, tất cả những nơi đó đều phải sửa. Nếu quy tắc nằm trong `Account`, thay đổi có thể được khoanh vùng trong chính đối tượng này.

### ĐÁNH ĐỔI — không biến nguyên tắc thành luật tuyệt đối

Không phải lúc nào việc đọc dữ liệu cũng xấu. Báo cáo, serialization hoặc read model thường cần lấy dữ liệu ra ngoài. DTO cũng không cần giả vờ thành một domain object giàu hành vi.

`Tell, don't ask` hữu ích nhất khi ta phát hiện **quy tắc nghiệp vụ đang nằm bên ngoài đối tượng sở hữu trạng thái**.

### MỐI LIÊN HỆ — từ đóng gói sang kế thừa

Khi một đối tượng đã có ranh giới và hợp đồng hành vi rõ ràng, câu hỏi tiếp theo xuất hiện:

> Nếu nhiều kiểu có chung một hợp đồng, hoặc một kiểu là dạng chuyên biệt của kiểu khác, Java biểu diễn mối quan hệ đó như thế nào?

Đó là lúc ta đi tới **kế thừa (inheritance)** và **kiểu con (subtyping)**.
