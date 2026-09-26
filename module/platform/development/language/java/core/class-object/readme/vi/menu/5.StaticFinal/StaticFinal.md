# static và final

`static` và `final` giải quyết hai câu hỏi khác nhau. `static` nói **member thuộc class hay instance**; `final` nói **một biến/reference/member có được thay đổi hoặc override theo cách nào** tùy ngữ cảnh.

## <a id="static-vs-instance">Static và Instance Member</a>

Instance field/method gắn với từng object:

```java
bankAccount.balance
bankAccount.withdraw(...)
```

Static field/method gắn với class thay vì một instance cụ thể:

```java
BankAccount.MAX_LIMIT
BankAccount.createDefault()
```

Static method không có `this` vì không có instance hiện tại mặc định.

## <a id="final-variable-reference">Các ý nghĩa của final</a>

Một biến `final` chỉ được nhận giá trị **một lần** theo quy tắc definite assignment của Java. Giá trị có thể được gán ngay khi khai báo hoặc được gán sau đó đúng một lần trên mọi đường khởi tạo hợp lệ.

```java
final int x = 10;
final List<String> names = new ArrayList<>();
```

Với reference, `final` ngăn `names` trỏ sang list khác, nhưng **không làm list phía sau trở thành bất biến**:

```java
names.add("A"); // vẫn có thể hợp lệ
```

Đây là điểm phân biệt quan trọng khi học object bất biến (immutable object) sau này.

`final` còn có ý nghĩa khác tùy loại declaration:

```text
final variable
→ giá trị/reference không được gán lại sau lần gán hợp lệ duy nhất

final method
→ subclass không được override method đó

final class
→ không thể tạo subclass từ class đó
```

Chương này chỉ cần thiết lập ranh giới khái niệm; overriding và thiết kế kế thừa được học sâu trong module OOP.

## <a id="static-initialization">Static Initialization</a>

Static field initializer và static initializer block chạy trong quá trình class initialization theo thứ tự được xác định bởi vị trí trong mã nguồn và vòng đời của superclass.

Trạng thái `static` được dùng chung giữa mọi instance, nên trạng thái `static` có thể thay đổi sẽ tạo phụ thuộc toàn cục và vấn đề đồng thời (concurrency) lớn hơn trạng thái riêng của từng instance.

## <a id="constants-design">Constant và Compile-time Constant</a>

Không phải mọi `static final` đều là compile-time constant.

```java
static final int MAX_DAILY_WITHDRAWALS = 3;          // compile-time constant
static final int CONFIGURED_LIMIT = Integer.parseInt("3"); // không phải compile-time constant
```

Giá trị đầu là primitive được khởi tạo từ constant expression nên có thể được inline vào bytecode của code sử dụng. Giá trị thứ hai cần gọi method nên được xác định khi runtime.

Compile-time constant phải đáp ứng các quy tắc cụ thể về primitive/String và constant expression. Điều này ảnh hưởng tới inlining và một số hành vi khi thư viện thay đổi constant nhưng mã sử dụng chưa được biên dịch lại.

Tên `UPPER_SNAKE_CASE` là quy ước phổ biến cho constant, nhưng ngữ nghĩa của constant quan trọng hơn kiểu đặt tên.

Chương tiếp theo đi vào các block khởi tạo nằm ngoài constructor body.
