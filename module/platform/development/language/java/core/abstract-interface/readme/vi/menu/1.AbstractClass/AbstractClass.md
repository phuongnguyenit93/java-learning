# Abstract Class

Module này tập trung vào một câu hỏi rất thực tế trong thiết kế Java: **làm sao định nghĩa một hợp đồng chung nhưng vẫn cho phép nhiều cách triển khai khác nhau?** Java có hai cơ chế quan trọng cho bài toán này là `abstract class` và `interface`.

`abstract class` phù hợp khi các kiểu con không chỉ chia sẻ một hợp đồng mà còn thực sự chia sẻ **trạng thái và một phần cách triển khai**. `interface` linh hoạt hơn khi ta muốn mô tả một vai trò hoặc khả năng mà nhiều class không cùng cây kế thừa vẫn có thể thực hiện.

Lộ trình của module:

```text
Abstract Class
→ chia sẻ trạng thái và một phần triển khai
        ↓
Interface
→ mô tả vai trò/hợp đồng độc lập với một cây class cụ thể
        ↓
Abstract Class vs Interface
→ chọn cơ chế nào cho đúng bài toán
        ↓
Interface Inheritance
→ ghép các hợp đồng nhỏ thành hợp đồng lớn hơn
        ↓
Default / Static / Private Method
→ mở rộng interface mà vẫn có thể dùng lại hành vi
        ↓
Multiple Inheritance qua Interface
→ xử lý khi nhiều hợp đồng/default method gặp nhau
```

## <a id="abstract-class-model">Abstract Class là gì?</a>

### KHÁI NIỆM

`abstract class` là một class **không thể tạo object trực tiếp**, nhưng vẫn có thể chứa:

- instance field;
- constructor;
- concrete method;
- abstract method.

Vì vậy nó thường đóng vai trò **khung triển khai một phần** cho một nhóm class có quan hệ gần nhau.

Ví dụ:

```java
abstract class PaymentMethod {
    private final String provider;

    protected PaymentMethod(String provider) {
        this.provider = provider;
    }

    String provider() {
        return provider;
    }

    abstract void pay(int amount);
}
```

`PaymentMethod` đã giữ phần trạng thái chung là `provider`, đồng thời buộc class con tự hoàn thiện `pay(...)`.

### VÌ SAO

Nếu nhiều class thực sự có chung trạng thái, quy tắc khởi tạo và một số hành vi nền tảng, việc sao chép toàn bộ phần đó sang từng class con sẽ làm tăng trùng lặp. `abstract class` cho phép giữ phần chung ở một nơi nhưng vẫn để lại những điểm mà kiểu con phải hoàn thiện.

## <a id="abstract-method">Abstract Method</a>

### KHÁI NIỆM

`abstract method` chỉ khai báo method signature mà không có phần thân:

```java
abstract void pay(int amount);
```

Class con cụ thể phải cung cấp cách triển khai phù hợp, trừ khi nó đã nhận được một phần triển khai cụ thể hợp lệ từ nơi khác trong hệ phân cấp.

### CƠ CHẾ

Abstract method vẫn tham gia đầy đủ các quy tắc overriding của Java:

- khả năng truy cập phải tương thích;
- return type phải tương thích;
- checked exception phải tuân quy tắc override;
- có thể dùng `@Override` ở class con để compiler kiểm tra.

Điểm quan trọng là `abstract` không tạo ra một loại dispatch đặc biệt. Khi class con override method, lời gọi instance method vẫn dùng dynamic dispatch như bình thường.

## <a id="abstract-constructor">Constructor của Abstract Class</a>

Một abstract class không thể được `new` trực tiếp, nhưng constructor của nó **vẫn chạy** khi tạo object của class con cụ thể.

```java
class CardPayment extends PaymentMethod {
    CardPayment() {
        super("card");
    }

    @Override
    void pay(int amount) { ... }
}
```

Constructor của `PaymentMethod` khởi tạo phần trạng thái thuộc class cha trước khi quá trình khởi tạo `CardPayment` hoàn tất.

### GIỚI HẠN

Không nên gọi method có thể bị override từ constructor nếu không có lý do rất mạnh. Khi đó cách triển khai của class con có thể chạy trước khi trạng thái riêng của class con được khởi tạo đầy đủ.

## <a id="abstract-class-limits">Giới hạn của Abstract Class</a>

`abstract` chỉ ngăn việc tạo instance trực tiếp. Abstract class vẫn dùng được làm kiểu tham chiếu:

```java
PaymentMethod payment = new CardPayment();
```

Giới hạn quan trọng hơn là Java chỉ cho một class `extends` **một class**. Vì vậy khi một capability cần xuất hiện ở nhiều cây class không liên quan, `abstract class` thường quá ràng buộc.

Đây là lý do chương tiếp theo chuyển sang `interface`: **nếu ta chỉ cần một hợp đồng chung mà không muốn buộc mọi cách triển khai vào cùng một cây kế thừa class thì sao?**
