# Constructor

Constructor không chỉ là cú pháp chạy sau `new`. Nó là nơi object chuyển từ “đang được tạo” sang **một instance có trạng thái ban đầu hợp lệ**.

## <a id="constructor-purpose">Mục đích của Constructor</a>

Constructor nên thiết lập những invariant cần có ngay khi object trở nên sử dụng được.

```java
class BankAccount {
    private final String id;
    private int balance;

    BankAccount(String id, int openingBalance) {
        if (openingBalance < 0) throw new IllegalArgumentException();
        this.id = id;
        this.balance = openingBalance;
    }
}
```

Sau constructor, bên gọi không nên nhận một object “nửa hợp lệ” rồi phải gọi thêm nhiều setter bắt buộc mới dùng được.

## <a id="constructor-overloading">Constructor Overloading</a>

Một class có thể có nhiều constructor với danh sách tham số khác nhau.

```java
BankAccount(String id) {
    this(id, 0);
}
```

`this(...)` cho phép một constructor gọi constructor khác trong cùng class để gom logic khởi tạo về một nơi.

Chuỗi gọi constructor (constructor chaining) giúp tránh lặp lại validation. Nếu constructor không bắt đầu bằng `this(...)` hoặc `super(...)`, Java sẽ ngầm chèn lời gọi `super()` không tham số. Lời gọi đó phải khớp với một constructor có thể truy cập của superclass; nếu không, mã sẽ không biên dịch.

## <a id="default-constructor">Default Constructor</a>

Compiler chỉ tự tạo no-đối số default constructor khi class **không khai báo bất kỳ constructor nào**.

Ngay khi bạn viết một constructor:

```java
BankAccount(String id) { ... }
```

compiler không tự thêm `BankAccount()` nữa.

Điều này thường gây nhầm khi framework hoặc đoạn mã khác yêu cầu constructor không tham số.

## <a id="constructor-exceptions">Constructor thất bại</a>

Constructor có thể throw exception nếu không thể tạo object hợp lệ.

Nếu quá trình khởi tạo thất bại, bên gọi không nhận một reference tới object hoàn chỉnh từ biểu thức `new`. Nhưng các tác động phụ đã xảy ra trước lỗi (ví dụ đăng ký object ra bên ngoài) vẫn có thể tồn tại.

Vì vậy tránh để `this` “thoát” khỏi constructor quá sớm; chương Object Creation Lifecycle sẽ quay lại rủi ro này.

Chương tiếp theo giải thích hai reference đặc biệt trong quá trình khởi tạo và truy cập member: `this` và `super`.
