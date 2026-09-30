# Hàm khởi tạo (constructor) và trạng thái hợp lệ

Hàm khởi tạo (`constructor`) là một khai báo đặc biệt của lớp, được dùng trong quá trình tạo đối tượng để thiết lập trạng thái ban đầu. Nó không chỉ là cú pháp đi kèm `new`; đây là nơi đối tượng chuyển từ “đang được tạo” sang **một thể hiện có trạng thái ban đầu hợp lệ**.

## <a id="constructor-purpose">Mục đích của hàm khởi tạo</a>

Hàm khởi tạo nên thiết lập những điều kiện để trạng thái luôn hợp lệ (`invariant`) ngay khi đối tượng trở nên sử dụng được.

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

Sau hàm khởi tạo, bên gọi không nên nhận một đối tượng “nửa hợp lệ” rồi phải gọi thêm nhiều phương thức gán bắt buộc mới dùng được.

## <a id="constructor-overloading">Nạp chồng và nối chuỗi hàm khởi tạo</a>

Một lớp có thể có nhiều hàm khởi tạo với danh sách tham số khác nhau.

```java
BankAccount(String id) {
    this(id, 0);
}
```

`this(...)` cho phép một hàm khởi tạo gọi hàm khởi tạo khác trong cùng lớp để gom logic khởi tạo về một nơi.

Chuỗi gọi hàm khởi tạo giúp tránh lặp lại kiểm tra dữ liệu. Với một lớp có lớp cha, nếu hàm khởi tạo không bắt đầu bằng `this(...)` hoặc `super(...)`, Java sẽ ngầm chèn lời gọi `super()` không tham số. Lời gọi đó phải khớp với một hàm khởi tạo có thể truy cập của lớp cha; nếu không, mã sẽ không biên dịch. `java.lang.Object` là ngoại lệ gốc vì nó không có lớp cha.

## <a id="default-constructor">Hàm khởi tạo mặc định</a>

Trình biên dịch chỉ tự tạo hàm khởi tạo mặc định không tham số khi lớp **không khai báo bất kỳ hàm khởi tạo nào**.

Ngay khi bạn viết một constructor:

```java
BankAccount(String id) { ... }
```

trình biên dịch không tự thêm `BankAccount()` nữa.

Điều này thường gây nhầm khi framework hoặc đoạn mã khác yêu cầu hàm khởi tạo không tham số.

## <a id="constructor-exceptions">Khi hàm khởi tạo thất bại</a>

Hàm khởi tạo có thể ném ngoại lệ nếu không thể tạo đối tượng hợp lệ.

Nếu quá trình khởi tạo thất bại, bên gọi không nhận một tham chiếu tới đối tượng hoàn chỉnh từ biểu thức `new`. Nhưng các tác động phụ đã xảy ra trước lỗi, ví dụ đăng ký đối tượng ra bên ngoài, vẫn có thể tồn tại.

Vì vậy tránh chia sẻ `this` ra ngoài trước khi hàm khởi tạo hoàn tất; chương về quá trình tạo đối tượng sẽ quay lại rủi ro này.

Chương tiếp theo giải thích tham chiếu `this` tới đối tượng hiện tại và từ khóa/cú pháp `super` dùng để chọn ngữ cảnh hàm khởi tạo hoặc thành viên của lớp cha.
