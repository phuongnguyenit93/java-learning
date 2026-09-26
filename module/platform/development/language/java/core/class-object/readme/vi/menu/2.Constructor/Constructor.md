# Constructor

Constructor không chỉ là cú pháp chạy sau `new`. Nó là nơi object chuyển từ “đang được tạo” sang **một instance có trạng thái ban đầu hợp lệ**.

## <a id="constructor-purpose">Mục đích của Constructor</a>

Constructor nên thiết lập những invariant cần có ngay khi object trở nên usable.

```java
class Account {
    private final String id;
    private int balance;

    Account(String id, int openingBalance) {
        if (openingBalance < 0) throw new IllegalArgumentException();
        this.id = id;
        this.balance = openingBalance;
    }
}
```

Sau constructor, bên gọi không nên nhận một object “nửa hợp lệ” rồi phải gọi thêm nhiều setter bắt buộc mới dùng được.

## <a id="constructor-overloading">Constructor Overloading</a>

Một class có thể có nhiều constructor với parameter list khác nhau.

```java
Account(String id) {
    this(id, 0);
}
```

`this(...)` cho phép một constructor gọi constructor khác trong cùng class để gom logic khởi tạo về một nơi.

Constructor chaining giúp tránh duplicate validation, nhưng lời gọi `this(...)` hoặc `super(...)` phải tuân vị trí/quy tắc construction của Java.

## <a id="default-constructor">Default Constructor</a>

Compiler chỉ tự tạo no-đối số default constructor khi class **không khai báo bất kỳ constructor nào**.

Ngay khi bạn viết một constructor:

```java
Account(String id) { ... }
```

compiler không tự thêm `Account()` nữa.

Điều này thường gây nhầm khi framework hoặc mã khác yêu cầu no-arg constructor.

## <a id="constructor-exceptions">Constructor thất bại</a>

Constructor có thể throw exception nếu không thể tạo object hợp lệ.

Nếu construction thất bại, bên gọi không nhận một reference tới object hoàn chỉnh từ biểu thức `new`. Nhưng các side effect đã xảy ra trước lỗi (ví dụ đăng ký object ra bên ngoài) vẫn có thể tồn tại.

Vì vậy tránh để `this` “thoát” khỏi constructor quá sớm; chương Object Creation Lifecycle sẽ quay lại rủi ro này.

chương tiếp theo giải thích hai reference đặc biệt trong construction/member ngữ cảnh: `this` và `super`.
