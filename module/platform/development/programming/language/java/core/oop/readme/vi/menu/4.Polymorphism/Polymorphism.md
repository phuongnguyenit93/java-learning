# Đa hình (Polymorphism)

Kế thừa và quan hệ kiểu con cho phép một object cụ thể được nhìn thông qua một kiểu tổng quát hơn. Đa hình biến khả năng đó thành một công cụ thiết kế: **bên gọi làm việc với một hợp đồng chung, còn hành vi thực tế có thể thay đổi theo cách triển khai tại runtime**.

## <a id="subtype-polymorphism">Đa hình kiểu con</a>

### KHÁI NIỆM — một hợp đồng, nhiều cách thực hiện

Đa hình thông qua kiểu con (**subtype polymorphism**) cho phép đoạn mã phụ thuộc vào một kiểu chung nhưng làm việc với nhiều cách triển khai khác nhau.

```java
PaymentMethod payment = new CardPayment();
payment.pay(1000);
```

`CheckoutService` chỉ cần biết hợp đồng của `PaymentMethod`. Nó không nhất thiết phải biết object thật ở runtime là thanh toán bằng thẻ, ví điện tử hay chuyển khoản.

### JAVA MODEL — polymorphism không yêu cầu class inheritance

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
→ CardPayment là subtype của PaymentMethod
→ biến PaymentMethod có thể giữ CardPayment
→ lời gọi instance method vẫn dynamic dispatch theo runtime receiver
```

Điều này rất quan trọng: **subtype polymorphism là ý tưởng rộng hơn class inheritance**. Class inheritance là một cách tạo subtype; interface implementation là một cách khác. Các rule chi tiết của interface thuộc module `abstract-interface`.

### MENTAL MODEL — static type và runtime type

Với:

```java
PaymentMethod payment = new CardPayment();
```

cần giữ hai lớp thông tin tách biệt:

```text
PaymentMethod payment
↑ static / declared type

new CardPayment()
↑ runtime type / actual object
```

Static type cho compiler biết **những member nào hợp lệ để gọi qua biểu thức `payment`**. Runtime type trở nên quan trọng khi một instance method đã hợp lệ lại có nhiều implementation override khác nhau.

Ví dụ:

```java
Animal animal = new Dog();

animal.sound(); // hợp lệ nếu Animal khai báo sound()
animal.bark();  // compile error nếu Animal không khai báo bark()
```

Dù object thật là `Dog`, compiler vẫn không cho gọi `bark()` qua biến `Animal` chỉ vì runtime object có method đó.

### VÌ SAO — giảm nhánh xử lý theo từng kiểu cụ thể

Không có đa hình, bên gọi thường phải tự phân loại object:

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

Điều này không có nghĩa mọi `if` đều xấu. Lợi ích xuất hiện khi nhánh điều kiện chỉ tồn tại vì bên gọi đang tự chọn hành vi dựa trên kiểu object.

### MỐI LIÊN HỆ — đa hình cần một hợp đồng có ý nghĩa

Dynamic dispatch chỉ thực sự hữu ích khi các cách triển khai tuân theo cùng một hợp đồng hành vi.

Nếu mỗi kiểu con có quy tắc hoàn toàn khác nhau đến mức bên gọi vẫn phải xử lý riêng từng loại, thiết kế đa hình chỉ còn mang tính hình thức.

## <a id="dynamic-dispatch">Dynamic Dispatch</a>

### CƠ CHẾ — Java chọn method như thế nào?

Với một **instance method bị override**, Java tách quá trình thành hai bước:

```text
Lúc biên dịch (compile time)
→ kiểm tra kiểu tham chiếu và kiểu tham số
→ xác định method signature hợp lệ

Lúc chạy (runtime)
→ nhìn vào class thật của object nhận lời gọi
→ chọn phần triển khai override cụ thể
```

Ví dụ:

```java
PaymentMethod payment = new CardPayment();
payment.pay(1000);
```

Compiler kiểm tra rằng `PaymentMethod` có method `pay(...)` phù hợp. Khi chương trình chạy, nếu `CardPayment` override method đó thì body của `CardPayment` được thực thi.

Cơ chế này gọi là **dynamic dispatch**.

### CƠ CHẾ — lời gọi bên trong object vẫn dispatch theo runtime receiver

Dynamic dispatch không chỉ xảy ra khi caller bên ngoài trực tiếp gọi một method override. Một method của class cha gọi một instance method khác qua `this` cũng vẫn làm việc với **cùng runtime object**:

```java
class PaymentMethod {
    void execute(int amount) {
        pay(amount); // tương đương lời gọi virtual trên this
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
→ runtime receiver vẫn là CardPayment
→ CardPayment.pay(...) chạy
```

Vì vậy không nên suy luận rằng “đang ở body của `PaymentMethod` thì mọi lời gọi method bên trong cũng cố định vào implementation của `PaymentMethod`”. Với overridable instance method, runtime receiver vẫn quyết định body cuối cùng.

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

> **cùng kiểu khai báo nhưng hành vi ở runtime có thể khác nhau**.

### GIỚI HẠN — không phải member nào cũng dynamic dispatch

Instance method bị override có dynamic dispatch.

Nhưng:

- static method không hoạt động theo cách này;
- field cũng không hoạt động theo cách này.

Sự khác biệt này sẽ được làm rõ ở chương `Overloading` và `Overriding`.

## <a id="substitutability">Khả năng thay thế</a>

### KHÁI NIỆM — gán được về mặt kiểu chưa đủ

Java có thể cho phép:

```java
Parent value = new Child();
```

nhưng điều đó chỉ chứng minh **type compatibility**.

Về mặt thiết kế, `Child` chỉ thực sự là một kiểu con tốt nếu nó vẫn đáp ứng những kỳ vọng quan trọng mà bên gọi có đối với `Parent`.

Ví dụ một kiểu con nên giữ các kỳ vọng như:

- không bất ngờ thu hẹp đầu vào hợp lệ một cách vô lý;
- kết quả sau khi gọi method vẫn giữ các cam kết quan trọng;
- invariant vẫn đúng;
- side effect và cách báo lỗi không phá giả định của hợp đồng chung.

Đây là tư duy gần với **Liskov Substitution Principle**, nhưng ở mức Java Core ta có thể dùng một câu hỏi đơn giản hơn:

> Nếu thay cách triển khai mà bên gọi không biết, hành vi có vẫn hợp lý theo hợp đồng chung không?

### FAILURE CASE — type-compatible nhưng không behavior-compatible

Giả sử hợp đồng `Account` khiến caller hợp lý khi tin rằng mọi account đều hỗ trợ `withdraw(...)`:

```java
class Account {
    void withdraw(int amount) {
        // normal withdrawal contract
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

Nhưng nếu mọi caller của `Account` đều phải thêm ngoại lệ riêng cho `FixedAccount`, quan hệ subtype đã không còn bảo toàn kỳ vọng hành vi chung.

```text
gán được theo type system
≠
thay thế tốt theo behavioral contract
```

### MINH CHỨNG — `SubstitutabilityController#substituteImplementations()`

Ví dụ thực thi truyền nhiều cách triển khai vào cùng một method nhận `Formatter`:

```text
run(new Upper())
run(new Lower())
```

Method `run(...)` chỉ biết hợp đồng của `Formatter`. Nó không cần phân nhánh để xử lý riêng từng cách triển khai.

### THỰC HÀNH — dấu hiệu khả năng thay thế đang yếu

Nếu code thường xuyên phải viết:

```java
if (payment instanceof SpecialPayment) {
    // xử lý riêng vì kiểu con này không làm theo hợp đồng chung
}
```

hãy kiểm tra lại abstraction hoặc quan hệ kiểu con.

### MỐI LIÊN HỆ — câu hỏi tiếp theo

Ta vừa thấy method bị override được chọn ở runtime. Nhưng Java còn có:

- `overloading` được chọn chủ yếu ở compile time;
- static method hiding;
- field hiding.

Nếu không tách rõ các cơ chế này, rất dễ hiểu sai đa hình. Chương tiếp theo tập trung vào chính sự khác biệt đó.
