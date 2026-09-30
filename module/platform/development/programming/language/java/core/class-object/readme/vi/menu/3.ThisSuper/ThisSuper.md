# this, super và chuỗi khởi tạo

Trong mã chạy trên một đối tượng cụ thể, Java cần cách nói rõ **đối tượng hiện tại** và **ngữ cảnh của lớp cha**. Hai từ khóa `this` và `super` phục vụ đúng vai trò đó.

Lý do thực tế là quá trình khởi tạo và truy cập thành viên cần đi qua nhiều ngữ cảnh: một hàm khởi tạo có thể tái sử dụng hàm khởi tạo khác trong cùng lớp, còn lớp con phải khởi tạo phần thuộc lớp cha của chính đối tượng đó trước khi hoàn thiện trạng thái riêng.

## <a id="this-reference">this</a>

`this` là tham chiếu tới đối tượng hiện tại trong ngữ cảnh của một thể hiện.

Nó thường được dùng để:

- phân biệt trường dữ liệu với tham số cùng tên;
- truyền đối tượng hiện tại sang phương thức khác;
- gọi hàm khởi tạo khác bằng `this(...)`;
- khi cần, trả về chính đối tượng hiện tại trong API kiểu nối chuỗi lời gọi.

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

Ở đây `this(id, 0)` gọi lại hàm khởi tạo **trong cùng lớp**, còn `this.id = id` truy cập trường dữ liệu của đối tượng hiện tại.

Không có `this` trong ngữ cảnh `static` vì thành viên `static` không gắn với một đối tượng cụ thể.

## <a id="super-access">super</a>

`super` cho phép truy cập hàm khởi tạo hoặc thành viên của lớp cha theo quy tắc của Java.

```java
class SavingsAccount extends BankAccount {
    private final int interestRate;

    SavingsAccount(String id, int balance, int interestRate) {
        super(id, balance);
        this.interestRate = interestRate;
    }
}
```

`super(id, balance)` khởi tạo phần `BankAccount` trước khi hàm khởi tạo của `SavingsAccount` thiết lập trạng thái riêng của lớp con.

`super` không phải một đối tượng thứ hai nằm bên trong đối tượng lớp con. Vẫn chỉ có một đối tượng; từ khóa này thay đổi cách mã nguồn chọn thành viên hoặc hàm khởi tạo của lớp cha.

## <a id="constructor-chaining-order">Chuỗi gọi this()/super()</a>

Với mọi lớp khác `Object`, chuỗi hàm khởi tạo cuối cùng phải dẫn tới hàm khởi tạo của lớp cha; đi theo chuỗi kế thừa sẽ kết thúc ở `Object()`.

```text
this(...)
→ hàm khởi tạo khác cùng lớp
→ cuối cùng phải tới super(...)
```

Các lời gọi này phải tuân quy tắc đặc biệt của Java về vị trí trong chuỗi khởi tạo.

Với ví dụ trên, chuỗi gọi là:

```text
new SavingsAccount(...)
→ SavingsAccount(...)
→ super(id, balance)
→ BankAccount(id, balance)
→ Object()
```

Hiểu chuỗi này là nền tảng để đọc thứ tự khởi tạo ở các chương sau.

Trước đó, chương tiếp theo trả lời: **những thành viên nào mã bên ngoài hoặc lớp con được phép truy cập?**
