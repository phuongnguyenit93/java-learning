# Ranh giới giữa kiểm tra lúc biên dịch và lúc chạy

Sau các chặng nền tảng, cần phân biệt hai thế giới thường bị trộn lẫn: **trình biên dịch biết gì từ kiểu tĩnh** và **thời điểm chạy biết gì từ đối tượng thực tế**.

## <a id="compile-time-vs-runtime-type">Kiểu lúc biên dịch và kiểu lúc chạy</a>

```java
Animal animal = new Dog();
```

Ở đây:

```text
kiểu lúc biên dịch / kiểu khai báo
→ Animal

kiểu thực tế của đối tượng lúc chạy
→ Dog
```

Trình biên dịch dùng `Animal` để kiểm tra thành viên nào được gọi hợp lệ và áp dụng các quy tắc chuyển đổi/nạp chồng. Đối tượng thực tế `Dog` ở thời điểm chạy tham gia cơ chế phân phối lời gọi động cho phương thức instance đã được ghi đè.

Hai kiểu này trả lời hai câu hỏi khác nhau và cả hai đều cần thiết.

### Kiểu tĩnh thuộc về góc nhìn của biểu thức/tham chiếu

Trình biên dịch không “nhìn xuyên” đối tượng thực tế tùy ý để cho phép gọi mọi thành viên của kiểu con:

```java
Animal animal = new Dog();
animal.eat();
// animal.bark(); // không biên dịch được nếu Animal không khai báo bark
```

Dù đối tượng thực tế là `Dog`, biểu thức `animal` có kiểu tĩnh `Animal`. Muốn gọi API chỉ có ở `Dog`, mã cần quan hệ kiểu, kiểm tra và ép kiểu phù hợp — hoặc một thiết kế trừu tượng tốt hơn.

### Kiểu lúc chạy ảnh hưởng những kiểm tra/hành vi nào?

Kiểu thực tế của đối tượng tham gia phân phối lời gọi động của phương thức instance đã ghi đè, kiểm tra downcast và kiểm tra kiểu phần tử mảng. Nó không làm quá trình chọn overload “chạy lại” ở thời điểm chạy.

## <a id="assignment-compatibility">Tính tương thích khi gán</a>

Java chỉ cho phép phép gán khi quan hệ giữa giá trị và kiểu phù hợp với các quy tắc của ngôn ngữ:

```java
Dog dog = new Dog();
Animal animal = dog; // upcast hợp lệ
```

Chiều ngược lại cần ép kiểu tường minh và kiểm tra ở thời điểm chạy:

```java
Dog again = (Dog) animal;
```

Phép gán kiểu nguyên thủy có bộ quy tắc chuyển đổi khác phép gán kiểu tham chiếu.

Hệ thống kiểu giúp loại bỏ nhiều lỗi trước khi chạy, nhưng không chứng minh mọi thao tác ép kiểu/tham chiếu đều an toàn ở thời điểm chạy.

### Phép gán là hợp đồng ở thời điểm biên dịch

```java
Object value = "java"; // mở rộng kiểu tham chiếu
String text = (String) value; // downcast tường minh + kiểm tra lúc chạy
```

Chuyển đổi kiểu nguyên thủy và kiểu tham chiếu có quy tắc khác nhau:

```java
long n = 10;        // mở rộng kiểu nguyên thủy
Integer boxed = 10; // đóng hộp
```

Không nên dùng từ “cast” để gọi mọi phép chuyển đổi. Phân biệt mở rộng kiểu, thu hẹp kiểu, boxing, unboxing và ép kiểu tham chiếu giúp dự đoán hành vi lúc biên dịch/lúc chạy chính xác hơn.

### Một giá trị có thể được chuyển đổi khác nhau tùy ngữ cảnh

Một trong những nguyên nhân Java dễ gây nhầm là **cùng một giá trị trong mã nguồn nhưng mỗi ngữ cảnh cho phép một bộ chuyển đổi khác nhau**.

| Ngữ cảnh | Mô hình tư duy | Ví dụ |
|---|---|---|
| phép gán | chuyển đổi khi gán | `long x = 10;` |
| số học/toán tử | thăng hạng kiểu số | `byte + byte -> int` |
| gọi phương thức | chuyển đổi khi gọi | `use(long)` nhận đối số `int` qua mở rộng kiểu |
| ép kiểu tường minh | chuyển đổi ép kiểu | `byte b = (byte) x;` |
| ranh giới đối tượng/kiểu nguyên thủy | boxing / unboxing | `Integer n = 10; int x = n;` |

Điểm quan trọng: một phép chuyển đổi hợp lệ ở ngữ cảnh này **không nhất thiết** được trình biên dịch tự áp dụng ở ngữ cảnh khác.

Ví dụ phép gán hằng có quy tắc đặc biệt:

```java
byte a = 1; // hợp lệ: giá trị hằng nằm trong phạm vi byte

void use(byte value) { }
// use(1);   // không biên dịch: lời gọi phương thức không tự dùng phép thu hẹp hằng này
use((byte) 1);
```

Toán tử cũng có quy tắc thăng hạng riêng:

```java
byte x = 1;
byte y = 2;
// byte z = x + y; // x + y có kiểu int
int z = x + y;
```

Khi gặp câu hỏi “tại sao dòng này biên dịch được nhưng dòng gần giống lại không?”, hãy hỏi trước: **đây là ngữ cảnh phép gán, biểu thức toán tử, lời gọi phương thức hay ép kiểu tường minh?**

## <a id="instanceof-safe-cast">`instanceof` và ép kiểu an toàn</a>

`instanceof` cho phép kiểm tra kiểu thực tế trước khi downcast. Pattern matching còn có thể gộp kiểm tra và tạo biến ràng buộc:

```java
if (animal instanceof Dog dog) {
    dog.bark();
}
```

Tuy nhiên nếu mã phải dùng `instanceof` liên tục để chọn hành vi theo kiểu con, hãy kiểm tra lại thiết kế trừu tượng/đa hình.

`instanceof` với `null` luôn cho `false`, vì `null` không phải instance của lớp nào:

```java
Object value = null;
System.out.println(value instanceof String); // false
```

Biến pattern chỉ tồn tại trong vùng mà trình biên dịch biết kiểm tra đã thành công, giúp tránh ép kiểu lặp lại và giảm lệch giữa bước kiểm tra với bước ép kiểu.

### Pattern matching không chỉ thay thế ép kiểu tường minh

Điểm quan trọng hơn cú pháp ngắn là trình biên dịch liên kết **kiểm tra kiểu + ràng buộc biến + luồng điều khiển**:

```java
if (animal instanceof Dog dog && dog.isReady()) {
    dog.bark();
}
```

`dog` dùng được ở vế phải của `&&` vì vế đó chỉ chạy sau khi `animal instanceof Dog dog` thành công.

Guard clause cũng có thể làm biến pattern tồn tại ở phần mã phía sau khi nhánh thất bại đã thoát:

```java
if (!(value instanceof String text)) {
    return;
}

System.out.println(text.length());
```

Ở dòng cuối, trình biên dịch biết chương trình chỉ có thể đi tiếp khi pattern đã khớp.

Ngược lại, biến pattern không thể dùng ở nơi trình biên dịch không bảo đảm pattern đã khớp. Chi tiết phạm vi theo luồng được nối với chương Biến và phạm vi; ở đây mô hình cần giữ là:

```text
kiểm tra kiểu lúc chạy thành công
        ↓
trình biên dịch cho phép biến ràng buộc có kiểu
        ↓
biến chỉ tồn tại nơi kết quả thành công được bảo đảm
```

### Java 21: `switch` có thể khớp theo type pattern

Sau khi đã có mô hình về kiểu tĩnh, kiểu lúc chạy và biến pattern, có thể đọc đầy đủ pattern matching trong `switch`. Với Java 21, `switch` không còn chỉ so giá trị chọn (selector) với hằng; giá trị chọn kiểu tham chiếu có thể được khớp bằng type pattern:

```java
static String describe(Object value) {
    return switch (value) {
        case Integer i -> "integer: " + i;
        case String s -> "string: " + s;
        case null -> "null";
        default -> "other";
    };
}
```

Mô hình tư duy:

```text
giá trị chọn của switch
→ khớp hằng/enum/String khi phù hợp
→ khớp type pattern với giá trị tham chiếu
→ tạo biến pattern khi case khớp
→ có thể xử lý null bằng case null rõ ràng
```

`case Integer i` vừa kiểm tra kiểu thực tế vừa tạo biến `i` có kiểu tĩnh `Integer` trong `case` đó. Đây là cùng họ cơ chế với `instanceof Integer i`, nhưng được dùng trong bảng quyết định của `switch`.

Nếu giá trị chọn kiểu tham chiếu có thể là `null`, hãy xem `null` là một phần của hợp đồng. `case null` cho phép xử lý rõ ràng; nếu không có nhánh `case` phù hợp cho `null` thì việc `switch` trên `null` có thể thất bại thay vì tự chạy `default`.

Không cần biến mọi chuỗi `if/else instanceof` thành pattern `switch`. Dùng nó khi nhiều nhánh cùng phân loại một giá trị chọn và `switch` làm mô hình quyết định rõ hơn.

## <a id="class-cast-failure">ClassCastException</a>

Nếu tham chiếu trỏ tới đối tượng không tương thích với kiểu đích, downcast thất bại ở thời điểm chạy:

```java
Animal animal = new Cat();
Dog dog = (Dog) animal; // ClassCastException
```

Trình biên dịch chỉ biết quan hệ giữa các kiểu; kiểm tra ở thời điểm chạy mới biết đối tượng thực tế.

Mô hình tư duy:

```text
thời điểm biên dịch
→ phép ép kiểu có hợp lệ về quan hệ kiểu không?

thời điểm chạy
→ đối tượng thật có tương thích kiểu đích không?
```

Nếu bạn đang dùng cast chỉ để “ép cho biên dịch được”, đó thường là dấu hiệu cần dừng lại kiểm tra mô hình. Phép ép kiểu nên diễn đạt hiểu biết thật sự về quan hệ kiểu, không phải che lỗi thiết kế.

Hai phần này minh họa vì sao một phép ép kiểu có thể hợp lệ về quan hệ kiểu nhưng vẫn cần kiểm tra đối tượng thực tế ở thời điểm chạy.

## <a id="overload-vs-override-dispatch">Nạp chồng, ghi đè và phân phối lời gọi</a>

Hai cơ chế rất dễ nhầm:

```text
nạp chồng: chọn phương thức
→ thời điểm biên dịch
→ dựa trên tập phương thức + kiểu đối số/chuyển đổi lúc biên dịch

ghi đè: phân phối lời gọi
→ thời điểm chạy
→ dựa trên kiểu thực tế của receiver sau khi chữ ký đã được xác định
```

Ví dụ `Parent x = new Child()` có thể chọn overload theo kiểu khai báo `Parent` nhưng gọi thân phương thức instance đã ghi đè của `Child`.

Mô-đun OOP sẽ đi sâu hơn vào phân phối lời gọi động; ở đây chỉ cần khóa mô hình tư duy lúc biên dịch so với lúc chạy.

Ví dụ nối hai giai đoạn:

```java
class Parent {
    void speak() { System.out.println("Parent"); }
}

class Child extends Parent {
    @Override
    void speak() { System.out.println("Child"); }
}

void use(Parent x) { x.speak(); }
void use(Object x) { System.out.println("Object"); }

Parent p = new Child();
use(p);
```

Trình biên dịch chọn `use(Parent)` dựa trên kiểu tĩnh của `p`. Bên trong phương thức, lời gọi `x.speak()` được phân phối ở thời điểm chạy tới `Child.speak()` vì đối tượng nhận lời gọi thực tế là `Child`.

```text
thời điểm biên dịch: chọn chữ ký
        ↓
thời điểm chạy: phân phối tới thân phương thức đã ghi đè
```

## <a id="type-system-boundaries">Ranh giới giữa lúc biên dịch và lúc chạy</a>

Trình biên dịch có thể kiểm tra:

- phân giải tên/kiểu;
- tính tương thích khi gán;
- khả năng áp dụng của các overload;
- definite assignment;
- nhiều ràng buộc về quyền truy cập và ép kiểu.

Thời điểm chạy vẫn phải xử lý những điều trình biên dịch không thể biết chắc:

- đối tượng thật có đúng kiểu để downcast không;
- giá trị ghi vào mảng có đúng kiểu phần tử thực tế không;
- tham chiếu có `null` không;
- chỉ số có nằm trong phạm vi hợp lệ của mảng không.

Đó là lý do Java vừa có kiểm tra kiểu tĩnh vừa có các exception ở thời điểm chạy như `ClassCastException`, `ArrayStoreException`, `NullPointerException`.

### Bảng tổng kết các ranh giới đã gặp

| Tình huống | Lúc biên dịch biết/kiểm tra | Lúc chạy còn phải kiểm tra |
|---|---|---|
| biến cục bộ | definite assignment | giá trị cụ thể |
| nạp chồng | ứng viên áp dụng được + cụ thể nhất | không chọn lại overload |
| ghi đè | chữ ký đã được chọn | kiểu thực tế của receiver để phân phối lời gọi |
| downcast | quan hệ ép kiểu hợp lệ | đối tượng thật có đúng kiểu đích không |
| ghi vào mảng | phép gán theo kiểu tĩnh có vẻ hợp lệ | kiểu phần tử thực tế của mảng |
| chỉ số mảng | biểu thức tương thích số nguyên | chỉ số có nằm trong giới hạn không |
| truy cập qua tham chiếu | thành viên tồn tại trên kiểu tĩnh | tham chiếu có `null` không |

Chương cuối dùng các ranh giới này để ghép toàn bộ nền tảng ngôn ngữ Java thành một chuỗi suy luận xuyên suốt.
