# this và super

Trong instance ngữ cảnh, Java cần cách nói rõ **object hiện tại** và **phần hành vi/trạng thái thuộc superclass**. Hai keyword `this` và `super` phục vụ đúng vai trò đó.

Lý do thực tế là quá trình khởi tạo và truy cập member cần đi qua nhiều ngữ cảnh: một constructor có thể tái sử dụng constructor khác trong cùng class, còn subclass phải khởi tạo phần superclass của chính object đó trước khi hoàn thiện trạng thái riêng.

## <a id="this-reference">this</a>

`this` là reference tới object hiện tại trong instance ngữ cảnh.

Nó thường được dùng để:

- phân biệt field với parameter cùng tên;
- truyền object hiện tại sang method khác;
- gọi constructor khác bằng `this(...)`;
- trả về instance hiện tại trong fluent API nếu thiết kế phù hợp.

```java
class BankAccount {
    private final String id;
    private int balance;

    BankAccount(String id) {
        this(id, 0);
    }

    BankAccount(String id, int balance) {
        this.id = id;
        this.balance = balance;
    }
}
```

Ở đây `this(id, 0)` gọi lại constructor **trong cùng class**, còn `this.id = id` truy cập field của object hiện tại.

Không có `this` trong static ngữ cảnh vì static member không gắn với một instance cụ thể.

## <a id="super-access">super</a>

`super` cho phép truy cập constructor/member của superclass theo quy tắc của Java.

```java
class SavingsAccount extends BankAccount {
    private final int interestRate;

    SavingsAccount(String id, int balance, int interestRate) {
        super(id, balance);
        this.interestRate = interestRate;
    }
}
```

`super(id, balance)` khởi tạo phần `BankAccount` trước khi constructor của `SavingsAccount` thiết lập trạng thái riêng của subclass.

`super` không phải một object thứ hai nằm bên trong subclass. Object vẫn là một instance duy nhất; keyword này chỉ thay đổi cách Java chọn member hoặc constructor của superclass trong mã nguồn.

## <a id="constructor-chaining-order">Quy tắc this()/super()</a>

Mỗi constructor cuối cùng phải dẫn tới một superclass constructor.

```text
this(...)
→ constructor khác cùng class
→ cuối cùng phải tới super(...)
```

Lời gọi constructor này phải tuân quy tắc đặc biệt của Java ở đầu chuỗi constructor.

Với ví dụ trên, chuỗi gọi là:

```text
new SavingsAccount(...)
→ SavingsAccount(...)
→ super(id, balance)
→ BankAccount(id, balance)
→ Object()
```

Hiểu chuỗi này là nền tảng để đọc initialization order ở các chương sau.

Trước đó, chương tiếp theo trả lời: **những member nào bên ngoài hoặc subclass được phép truy cập?**
