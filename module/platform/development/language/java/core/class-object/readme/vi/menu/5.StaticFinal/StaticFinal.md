# static và final

`static` và `final` giải quyết hai câu hỏi khác nhau. `static` nói **member thuộc class hay instance**; `final` nói **một biến/reference/member có được thay đổi hoặc override theo cách nào** tùy ngữ cảnh.

## <a id="static-vs-instance">Static và Instance Member</a>

Instance field/method gắn với từng object:

```java
account.balance
account.withdraw(...)
```

Static field/method gắn với class-level ngữ cảnh:

```java
Account.MAX_LIMIT
Account.createDefault()
```

Static method không có `this` vì không có current instance mặc định.

## <a id="final-variable-reference">final với Primitive và Reference</a>

`final` variable chỉ cho phép gán một lần sau initialization.

```java
final int x = 10;
final List<String> names = new ArrayList<>();
```

Với reference, `final` ngăn `names` trỏ sang list khác, nhưng **không làm list phía sau immutable**:

```java
names.add("A"); // vẫn có thể hợp lệ
```

Đây là distinction quan trọng khi học immutable object sau này.

## <a id="static-initialization">Static Initialization</a>

Static field initializer và static initializer block chạy trong quá trình class initialization theo order được định nghĩa bởi source/superclass lifecycle.

Static trạng thái được chia sẻ cho mọi instance, nên mutable static trạng thái tạo coupling toàn cục và concurrency concern lớn hơn instance trạng thái thông thường.

## <a id="constants-design">Constant và Compile-time Constant</a>

Không phải mọi `static final` đều là compile-time constant.

Compile-time constant phải đáp ứng các quy tắc cụ thể về primitive/String và constant biểu thức. Điều này ảnh hưởng tới inlining và một số hành vi khi library thay đổi constant mà client chưa recompile.

Tên `UPPER_SNAKE_CASE` là convention phổ biến cho constant, nhưng ngữ nghĩa quan trọng vẫn là immutability/value hợp đồng chứ không chỉ style.

chương tiếp theo đi vào các block khởi tạo nằm ngoài constructor body.
