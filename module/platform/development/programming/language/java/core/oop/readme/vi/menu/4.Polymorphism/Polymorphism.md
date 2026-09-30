# Đa hình và phân phối động

Kế thừa và quan hệ kiểu con cho phép một đối tượng cụ thể được nhìn thông qua một kiểu tổng quát hơn. Đa hình biến khả năng đó thành một công cụ thiết kế: **bên gọi làm việc với một hợp đồng chung, còn hành vi thực tế có thể thay đổi theo cách triển khai lúc chạy**.

## <a id="subtype-polymorphism">Đa hình kiểu con</a>

### KHÁI NIỆM — một hợp đồng, nhiều cách thực hiện

Đa hình thông qua kiểu con (**subtype polymorphism**) cho phép đoạn mã phụ thuộc vào một kiểu chung nhưng làm việc với nhiều cách triển khai khác nhau.

```java
PaymentMethod payment = new CardPayment();
payment.pay(1000);
```

`CheckoutService` chỉ cần biết hợp đồng của `PaymentMethod`. Nó không nhất thiết phải biết đối tượng thực tế lúc chạy là thanh toán bằng thẻ, ví điện tử hay chuyển khoản.

### MÔ HÌNH TRONG JAVA — đa hình không yêu cầu kế thừa lớp

`PaymentMethod` có thể là một `interface`:

```java
interface PaymentMethod {
    void pay(int amount);
}

class CardPayment implements PaymentMethod {
    @Override
    public void pay(int amount) {
        System.out.println("card: " + amount);
    }
}

PaymentMethod payment = new CardPayment();
payment.pay(1000);
```

Ở đây không có `CardPayment extends PaymentMethod`, nhưng vẫn có đầy đủ chuỗi:

```text
CardPayment implements PaymentMethod
→ CardPayment là kiểu con của PaymentMethod
→ biến PaymentMethod có thể giữ CardPayment
→ lời gọi phương thức đối tượng vẫn được phân phối động theo đối tượng nhận lời gọi thực tế
```

Điều này rất quan trọng: **đa hình kiểu con rộng hơn kế thừa class**. Kế thừa class là một cách tạo kiểu con; triển khai `interface` là một cách khác. Các quy tắc chi tiết của `interface` thuộc mô-đun `abstract-interface`.

### MÔ HÌNH TƯ DUY — kiểu khai báo và kiểu thực tế lúc chạy

Với:

```java
PaymentMethod payment = new CardPayment();
```

cần giữ hai lớp thông tin tách biệt:

```text
PaymentMethod payment
↑ kiểu tĩnh / kiểu khai báo

new CardPayment()
↑ kiểu thực tế lúc chạy / đối tượng thực tế
```

Kiểu khai báo cho trình biên dịch biết **những thành viên nào hợp lệ để gọi qua biểu thức `payment`**. Kiểu thực tế lúc chạy trở nên quan trọng khi một phương thức đối tượng đã hợp lệ lại có nhiều cách triển khai ghi đè khác nhau.

Ví dụ:

```java
Animal animal = new Dog();

animal.sound(); // hợp lệ nếu Animal khai báo sound()
animal.bark();  // lỗi biên dịch nếu Animal không khai báo bark()
```

Dù đối tượng thực tế là `Dog`, trình biên dịch vẫn không cho gọi `bark()` qua biến `Animal` chỉ vì đối tượng thực tế có phương thức đó.

### VÌ SAO — giảm nhánh xử lý theo từng kiểu cụ thể

Không có đa hình, bên gọi thường phải tự phân loại đối tượng:

```text
nếu là CardPayment
→ xử lý kiểu thẻ

nếu là WalletPayment
→ xử lý kiểu ví

nếu là BankTransfer
→ xử lý kiểu chuyển khoản
```

Với đa hình:

```java
payment.pay(amount);
```

Bên gọi chỉ yêu cầu hành vi `pay`. Object cụ thể tự quyết định cách thực hiện.

Điều này không có nghĩa mọi `if` đều xấu. Lợi ích xuất hiện khi nhánh điều kiện chỉ tồn tại vì bên gọi đang tự chọn hành vi dựa trên kiểu đối tượng.

### MỐI LIÊN HỆ — đa hình cần một hợp đồng có ý nghĩa

Phân phối động chỉ thực sự hữu ích khi các cách triển khai tuân theo cùng một hợp đồng hành vi.

Nếu mỗi kiểu con có quy tắc hoàn toàn khác nhau đến mức bên gọi vẫn phải xử lý riêng từng loại, thiết kế đa hình chỉ còn mang tính hình thức.

## <a id="dynamic-dispatch">Phân phối động (Dynamic Dispatch)</a>

### CƠ CHẾ — Java chọn phương thức như thế nào?

Với một **phương thức đối tượng bị ghi đè**, Java tách quá trình thành hai bước:

```text
Lúc biên dịch (compile time)
→ kiểm tra kiểu tham chiếu và kiểu tham số
→ xác định chữ ký phương thức hợp lệ

Lúc chạy (runtime)
→ nhìn vào class thực tế của đối tượng nhận lời gọi
→ chọn phần triển khai ghi đè cụ thể
```

Ví dụ:

```java
PaymentMethod payment = new CardPayment();
payment.pay(1000);
```

Trình biên dịch kiểm tra rằng `PaymentMethod` có phương thức `pay(...)` phù hợp. Khi chương trình chạy, nếu `CardPayment` ghi đè phương thức đó thì phần thân của `CardPayment` được thực thi.

Cơ chế này gọi là **phân phối động (dynamic dispatch)**.

### CƠ CHẾ — lời gọi bên trong đối tượng vẫn phân phối theo đối tượng nhận lời gọi thực tế

Phân phối động không chỉ xảy ra khi bên gọi bên ngoài trực tiếp gọi một phương thức ghi đè. Một phương thức của class cha gọi một phương thức đối tượng khác qua `this` cũng vẫn làm việc với **cùng đối tượng thực tế lúc chạy**:

```java
class PaymentMethod {
    void execute(int amount) {
        pay(amount); // vẫn là lời gọi được phân phối động qua this
    }

    void pay(int amount) {
        System.out.println("generic");
    }
}

class CardPayment extends PaymentMethod {
    @Override
    void pay(int amount) {
        System.out.println("card: " + amount);
    }
}

PaymentMethod payment = new CardPayment();
payment.execute(1000);
```

Flow là:

```text
payment.execute(...)
→ chạy PaymentMethod.execute(...)
→ execute gọi this.pay(...)
→ đối tượng nhận lời gọi thực tế vẫn là CardPayment
→ CardPayment.pay(...) chạy
```

Vì vậy không nên suy luận rằng “đang ở phần thân của `PaymentMethod` thì mọi lời gọi phương thức bên trong cũng cố định vào cách triển khai của `PaymentMethod`”. Với phương thức đối tượng có thể bị ghi đè, đối tượng nhận lời gọi thực tế lúc chạy vẫn quyết định phần thân cuối cùng.

### MINH CHỨNG — `PolymorphismController#dispatch()`

Ví dụ thực thi hiện có dùng:

```java
Speaker first = new Dog();
Speaker second = new Cat();
```

Cả hai biến đều có kiểu khai báo là `Speaker`, nhưng:

```text
first.speak()
→ hành vi của Dog

second.speak()
→ hành vi của Cat
```

Điểm cần quan sát là:

> **cùng kiểu khai báo nhưng hành vi lúc chạy có thể khác nhau**.

### GIỚI HẠN — không phải thành viên nào cũng dùng phân phối động

Phương thức đối tượng bị ghi đè có phân phối động.

Nhưng:

- phương thức `static` không hoạt động theo cách này;
- trường dữ liệu cũng không hoạt động theo cách này.

Sự khác biệt này sẽ được làm rõ ở chương `Overloading` và `Overriding`.

## <a id="substitutability">Khả năng thay thế</a>

### KHÁI NIỆM — gán được về mặt kiểu chưa đủ

Java có thể cho phép:

```java
Parent value = new Child();
```

nhưng điều đó chỉ chứng minh **khả năng tương thích kiểu (type compatibility)**.

Về mặt thiết kế, `Child` chỉ thực sự là một kiểu con tốt nếu nó vẫn đáp ứng những kỳ vọng quan trọng mà bên gọi có đối với `Parent`.

Ví dụ một kiểu con nên giữ các kỳ vọng như:

- không bất ngờ thu hẹp đầu vào hợp lệ một cách vô lý;
- kết quả sau khi gọi phương thức vẫn giữ các cam kết quan trọng;
- invariant vẫn đúng;
- side effect và cách báo lỗi không phá giả định của hợp đồng chung.

Đây là tư duy gần với **Liskov Substitution Principle**, nhưng ở mức Java Core ta có thể dùng một câu hỏi đơn giản hơn:

> Nếu thay cách triển khai mà bên gọi không biết, hành vi có vẫn hợp lý theo hợp đồng chung không?

### TRƯỜNG HỢP VI PHẠM — tương thích kiểu nhưng không tương thích hành vi

Giả sử hợp đồng `Account` khiến bên gọi có lý do hợp lý để tin rằng mọi tài khoản đều hỗ trợ `withdraw(...)`:

```java
class Account {
    void withdraw(int amount) {
        // hợp đồng rút tiền thông thường
    }
}

class FixedAccount extends Account {
    @Override
    void withdraw(int amount) {
        throw new UnsupportedOperationException();
    }
}
```

Java vẫn cho phép:

```java
Account account = new FixedAccount();
```

Nhưng nếu mọi bên gọi của `Account` đều phải thêm ngoại lệ riêng cho `FixedAccount`, quan hệ kiểu con đã không còn bảo toàn kỳ vọng hành vi chung.

```text
gán được theo hệ thống kiểu
≠
thay thế tốt theo hợp đồng hành vi
```

### MINH CHỨNG — `SubstitutabilityController#substituteImplementations()`

Ví dụ thực thi truyền nhiều cách triển khai vào cùng một phương thức nhận `Formatter`:

```text
run(new Upper())
run(new Lower())
```

Method `run(...)` chỉ biết hợp đồng của `Formatter`. Nó không cần phân nhánh để xử lý riêng từng cách triển khai.

### THỰC HÀNH — dấu hiệu khả năng thay thế đang yếu

Nếu mã thường xuyên phải viết:

```java
if (payment instanceof SpecialPayment) {
    // xử lý riêng vì kiểu con này không làm theo hợp đồng chung
}
```

hãy kiểm tra lại trừu tượng hóa hoặc quan hệ kiểu con.

### MỐI LIÊN HỆ — câu hỏi tiếp theo

Ta vừa thấy phương thức bị ghi đè được chọn lúc chạy. Nhưng Java còn có:

- `overloading` được chọn chủ yếu lúc biên dịch;
- che khuất phương thức `static`;
- che khuất trường dữ liệu.

Nếu không tách rõ các cơ chế này, rất dễ hiểu sai đa hình. Chương tiếp theo tập trung vào chính sự khác biệt đó.
