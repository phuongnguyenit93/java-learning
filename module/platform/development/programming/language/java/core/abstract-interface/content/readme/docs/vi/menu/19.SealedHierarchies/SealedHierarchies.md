<a id="back-to-top"></a>

# Phân cấp sealed

## Menu
- [Mục đích kiểm soát các kiểu con trực tiếp từ Java 17](#sealed-hierarchies-001)
- [Sealed abstract class và sealed interface](#sealed-hierarchies-002)
- [Cú pháp permits và trường hợp suy ra danh sách cho phép](#sealed-hierarchies-003)
- [Yêu cầu cùng module hoặc cùng package trong unnamed module](#sealed-hierarchies-004)
- [Modifier final, sealed và non-sealed của lớp con trực tiếp](#sealed-hierarchies-005)
- [Interface con sealed hoặc non-sealed](#sealed-hierarchies-006)
- [Lớp trừu tượng vừa abstract vừa sealed hoặc non-sealed](#sealed-hierarchies-007)
- [Nhánh mở rộng được phép và giới hạn của anonymous class, lambda](#sealed-hierarchies-008)
- [Pattern matching switch và tính đầy đủ của phân cấp đóng](#sealed-hierarchies-009)

## <a id="sealed-hierarchies-001">Mục đích kiểm soát các kiểu con trực tiếp từ Java 17</a>

<details>
<summary>Xem chi tiết</summary>

# Sealed hierarchy trong Java 17: kiểm soát các kiểu con trực tiếp

## 1. Vấn đề mà sealed giải quyết

Trong thiết kế hướng đối tượng, đa hình cho phép một biến có kiểu cha tham chiếu đến nhiều kiểu con. Tuy nhiên, có những mô hình nghiệp vụ mà tập kiểu con hợp lệ cần được giới hạn rõ ràng. Ví dụ, một biểu thức có thể chỉ là số hoặc phép cộng; một khoản thanh toán có thể chỉ thuộc một số loại đã được hệ thống công nhận.

Nếu dùng một class hoặc interface thông thường, bất kỳ mã nào đáp ứng điều kiện truy cập và kế thừa đều có thể khai báo thêm kiểu con. Điều đó đôi khi làm mô hình khó kiểm soát: mã xử lý có thể gặp một kiểu ngoài dự kiến, các quy tắc nghiệp vụ bị phân tán, hoặc API công khai vô tình cho phép mở rộng mà tác giả không định hỗ trợ.

Java 17 chuẩn hóa **sealed classes và sealed interfaces** để giới hạn tập kiểu được phép kế thừa hoặc triển khai trực tiếp một kiểu. Từ khóa `sealed` thể hiện rằng kiểu cha có một danh sách các kiểu con trực tiếp được cho phép. Danh sách đó được khai báo bằng `permits`, hoặc được trình biên dịch suy ra trong trường hợp hợp lệ.

Sealed không loại bỏ đa hình. Nó cho phép tiếp tục dùng một kiểu cha chung trong khi đặt ra ranh giới rõ ràng cho cây kế thừa. Từng nhánh sau đó có thể đóng hoàn toàn, tiếp tục giới hạn bằng sealed, hoặc mở rộng tự do bằng non-sealed.

## 2. Cú pháp cơ bản

~~~java
public sealed class Shape
        permits Circle, Rectangle {
    // Trạng thái và hành vi chung của Shape
}

public final class Circle extends Shape {
    // Nhánh này không cho phép kế thừa tiếp
}

public non-sealed class Rectangle extends Shape {
    // Nhánh này cho phép các kiểu khác kế thừa tiếp
}
~~~

Đây là ví dụ minh họa cú pháp và cần đặt các kiểu public trong những tệp tương ứng nếu sử dụng đúng nguyên dạng. Một sealed class có thể là abstract hoặc concrete. Khi khai báo abstract, ta vẫn giới hạn được các kiểu có quyền kế thừa nó, nhưng không thể khởi tạo trực tiếp kiểu cha.

Các từ khóa có ý nghĩa khác nhau:

- `sealed`: kiểu này giới hạn những kiểu được phép kế thừa hoặc triển khai trực tiếp.
- `permits`: liệt kê những kiểu con trực tiếp được cho phép.
- `final`: kiểu con này không thể có lớp con tiếp theo.
- `non-sealed`: mở lại nhánh kế thừa tại kiểu con này; các kiểu khác có thể kế thừa hoặc triển khai nó theo quy tắc thông thường.
- `sealed` trên một kiểu con: tiếp tục đặt giới hạn cho thế hệ kiểu con kế tiếp.

Một kiểu con trực tiếp được phép của sealed class phải là `final`, `sealed` hoặc `non-sealed`. Record vốn đã là kiểu final ngầm định, vì vậy một record có thể trực tiếp triển khai sealed interface mà không cần ghi từ khóa `final`.

## 3. Kiểm soát kiểu con trực tiếp, không phải mọi hậu duệ

Tác dụng cốt lõi của sealed là kiểm soát **các kiểu con trực tiếp**. Cây kế thừa nhiều tầng vẫn có thể tồn tại; quy tắc của từng nhánh quyết định cây đó tiếp tục mở rộng như thế nào.

~~~java
sealed class Message permits EmailMessage, SystemMessage {
}

final class EmailMessage extends Message {
}

non-sealed class SystemMessage extends Message {
}

class AlertMessage extends SystemMessage {
}
~~~

Trong ví dụ này:

- `Message` chỉ cho phép `EmailMessage` và `SystemMessage` kế thừa trực tiếp.
- `EmailMessage` là final, nên không thể có lớp con.
- `SystemMessage` là non-sealed, nên nhánh này được mở lại. `AlertMessage` có thể kế thừa `SystemMessage`, nhưng không vì thế mà trở thành một kiểu con trực tiếp được liệt kê của `Message`.

Nếu thay `SystemMessage` bằng một kiểu con sealed, kiểu con đó phải có danh sách các kiểu được phép mở rộng nó. Như vậy, sealed cho phép giới hạn cây kế thừa ở nhiều tầng, trong khi non-sealed mở một nhánh cụ thể. Không thể đồng thời khai báo cùng một class là final, sealed và non-sealed; chúng là những lựa chọn loại trừ nhau cho khả năng kế thừa của kiểu đó.

## 4. Sealed interface cũng giới hạn tập kiểu triển khai

Sealed áp dụng cho cả class và interface. Với sealed interface, danh sách `permits` có thể chứa class triển khai trực tiếp hoặc interface con trực tiếp. Các kiểu được phép phải tuân theo quy tắc tương ứng: class triển khai trực tiếp cần là final, sealed hoặc non-sealed; interface con cần là sealed hoặc non-sealed vì interface không thể là final.

~~~java
sealed interface Result permits Success, Failure {
}

record Success(String value) implements Result {
}

record Failure(String reason) implements Result {
}
~~~

Hai record trên đều final ngầm định. Do đó, chúng là các lá đóng của phân cấp `Result`: mã bên ngoài không thể tạo một record khác kế thừa từ Success hoặc Failure, và không thể thêm một kiểu triển khai trực tiếp tùy ý cho Result.

Sealed interface hữu ích khi một API cần biểu diễn một tập trường hợp đã biết. Ví dụ, kết quả xử lý có thể là thành công hoặc thất bại; một lệnh có thể thuộc một số loại được xác định; một sự kiện miền có thể nằm trong một tập kiểu được quản lý. Mã gọi vẫn có thể nhận giá trị qua interface chung, còn tác giả API kiểm soát được các kiểu được phép xuất hiện trực tiếp trong phân cấp.

## 5. Ví dụ Java 17 chạy được

Chương trình dưới đây dùng sealed interface để biểu diễn hai loại thanh toán. Các record là kiểu con trực tiếp được phép và cung cấp dữ liệu bất biến cho từng trường hợp.

~~~java
import java.math.BigDecimal;

sealed interface Payment permits CardPayment, CashPayment {
}

record CardPayment(String lastFour) implements Payment {
}

record CashPayment(BigDecimal amount) implements Payment {
}

public class PaymentDemo {
    public static void main(String[] args) {
        Payment card = new CardPayment("4242");
        Payment cash = new CashPayment(new BigDecimal("12.50"));

        printPayment(card);
        printPayment(cash);
    }

    private static void printPayment(Payment payment) {
        if (payment instanceof CardPayment card) {
            System.out.println("Card ending in " + card.lastFour());
        } else if (payment instanceof CashPayment cash) {
            System.out.println("Cash amount: " + cash.amount().toPlainString());
        } else {
            throw new IllegalStateException(
                    "Unexpected payment type: " + payment.getClass().getName());
        }
    }
}
~~~

Lưu toàn bộ chương trình vào tệp `PaymentDemo.java`, sau đó biên dịch và chạy bằng JDK 17 trở lên:

~~~text
javac PaymentDemo.java
java PaymentDemo
~~~

Kết quả stdout:

~~~text
Card ending in 4242
Cash amount: 12.50
~~~

Ở đây, biến `payment` có kiểu khai báo Payment nhưng có thể tham chiếu đến CardPayment hoặc CashPayment. Phương thức `printPayment` dùng pattern matching cho `instanceof`, một tính năng đã được hoàn thiện từ Java 16. Nhánh cuối vẫn phòng vệ trước kiểu ngoài dự kiến ở cấp chạy; sealed giúp trình biên dịch biết tập kiểu con trực tiếp hợp lệ, nhưng chuỗi `if/else` không tự được trình biên dịch xác nhận là đầy đủ.

Pattern matching cho `switch` vẫn là tính năng preview trong Java 17. Không nên dùng cú pháp type-pattern switch như thể nó đã là cú pháp chuẩn không cần cờ preview của Java 17. Ví dụ trên chỉ dùng cú pháp Java 17 thông thường và không yêu cầu bật preview.

## 6. Quy tắc của permits và vị trí khai báo

Thông thường, viết rõ danh sách các kiểu con trực tiếp:

~~~java
sealed interface Command permits StartCommand, StopCommand {
}
~~~

Tên trong `permits` phải xác định các kiểu con trực tiếp được cho phép. Khai báo danh sách không tự tạo các kiểu đó; chúng phải thực sự tồn tại và kế thừa hoặc triển khai đúng kiểu cha. Trình biên dịch kiểm tra các quan hệ này.

Trong trường hợp tất cả kiểu con trực tiếp được khai báo trong cùng compilation unit, có thể bỏ `permits` để trình biên dịch suy ra danh sách. Ví dụ khái niệm:

~~~java
sealed interface Command {
}

final class StartCommand implements Command {
}

final class StopCommand implements Command {
}
~~~

Khi sử dụng dạng suy luận, hãy giữ các khai báo kiểu con trực tiếp trong cùng tệp nguồn với kiểu sealed để trình biên dịch có thể suy ra tập cho phép. Nếu các kiểu nằm ở những tệp khác, hãy khai báo `permits` rõ ràng.

Các kiểu con được phép còn phải tuân thủ ranh giới module/package của Java:

- Trong named module, kiểu sealed và các kiểu con được phép phải thuộc cùng module; chúng có thể nằm ở các package khác nhau trong module đó.
- Khi chạy trong unnamed module, kiểu sealed và các kiểu con được phép phải thuộc cùng package.

Những quy tắc này làm cho tập kiểu con được phép có thể được kiểm tra ở thời điểm biên dịch và giúp tránh một kiểu ở module hoặc package không phù hợp tự ý mở rộng phân cấp.

## 7. So sánh final, sealed và non-sealed

| Modifier | Khả năng kế thừa | Khi nên dùng |
|---|---|---|
| `final` | Không cho phép kiểu con kế thừa tiếp. | Một kiểu lá đã hoàn chỉnh, không cần mở rộng. |
| `sealed` | Chỉ các kiểu được phép mới được kế thừa hoặc triển khai trực tiếp. | Cần một tập kiểu con có kiểm soát. |
| `non-sealed` | Mở lại khả năng kế thừa tại nhánh này. | Muốn giới hạn các nhánh trực tiếp của cha nhưng cho phép một nhánh được mở rộng tự do. |

Chọn modifier theo hợp đồng mà API muốn công bố. Dùng final khi một kiểu cần đóng hoàn toàn. Dùng sealed khi các kiểu con hợp lệ đã được xác định và nên được kiểm soát. Dùng non-sealed khi cần một điểm mở rộng có chủ đích bên trong phân cấp sealed.

Sealed không thay thế modifier truy cập như public, protected hoặc private. Quyền truy cập quyết định mã nào có thể nhìn thấy hoặc sử dụng kiểu; sealed quyết định kiểu nào được phép trở thành kiểu con trực tiếp. Hai nhóm quy tắc giải quyết hai vấn đề khác nhau và cùng có thể áp dụng cho một API.

## 8. Lợi ích và giới hạn trong thiết kế

**Lợi ích chính** là làm rõ ranh giới của mô hình miền. Khi tập kiểu con trực tiếp được kiểm soát, người đọc API biết những trường hợp nào được tác giả hỗ trợ. Việc thêm một trường hợp mới vào phân cấp cũng trở thành thay đổi có chủ đích, thay vì một kiểu triển khai bất kỳ có thể xuất hiện từ mã bên ngoài.

Sealed cũng hỗ trợ phân tích đầy đủ trường hợp trong các tính năng ngôn ngữ hiểu phân cấp sealed, chẳng hạn pattern matching cho switch ở các phiên bản Java hỗ trợ tính năng đó. Tuy nhiên, không nên nhầm lợi ích này với việc mọi cấu trúc điều kiện đều được trình biên dịch chứng minh là đầy đủ. Một chuỗi if/else như ví dụ trên vẫn có thể cần nhánh dự phòng.

**Giới hạn cần cân nhắc** là sealed tạo ra ràng buộc mở rộng. Nếu thiết kế thư viện vốn cần cho bên thứ ba tự do thêm triển khai, việc đóng danh sách kiểu con có thể không phù hợp. Trong thư viện đã phát hành, thay đổi danh sách kiểu con được phép cũng có thể ảnh hưởng đến mã nguồn hoặc binary của người dùng tùy cách phân cấp được sử dụng. Vì vậy, hãy quyết định ranh giới mở rộng ngay khi thiết kế API và ghi rõ các nhánh nào là mở, nhánh nào là đóng.

Không dùng sealed chỉ vì có thể dùng. Nó có giá trị khi giới hạn kiểu con phản ánh quy tắc nghiệp vụ hoặc hợp đồng API thực sự. Với một abstraction cần được mở rộng tùy ý, class hoặc interface thông thường có thể là lựa chọn phù hợp hơn.

## 9. Lỗi thường gặp

- **Quên khai báo cách xử lý kế thừa của kiểu con trực tiếp.** Một class được permits cần là final, sealed hoặc non-sealed. Record là final ngầm định; interface con không thể khai báo final.
- **Nhầm permits với danh sách mọi hậu duệ.** permits chỉ liệt kê kiểu con trực tiếp. Các thế hệ tiếp theo được kiểm soát bởi modifier trên từng nhánh.
- **Đặt kiểu con ngoài module/package được phép.** Kiểm tra quy tắc named module hoặc unnamed module khi compiler báo lỗi về vị trí kiểu con.
- **Dùng non-sealed nhưng mong nhánh đó vẫn đóng.** non-sealed chủ ý mở lại kế thừa tại nhánh được đánh dấu.
- **Cho rằng sealed tự động làm mọi xử lý switch đầy đủ trên Java 17.** Pattern matching cho switch ở Java 17 còn là preview; cần dùng đúng phiên bản và tùy chọn biên dịch nếu chủ động thử tính năng preview.
- **Cho rằng sealed là cơ chế phân quyền truy cập.** Nó giới hạn kiểu con hợp lệ; nó không thay thế kiểm soát truy cập, xác thực hoặc phân quyền ở tầng ứng dụng.

## 10. Ghi nhớ

Java 17 sealed class/interface giới hạn những kiểu có thể kế thừa hoặc triển khai trực tiếp một kiểu cha. Danh sách đó được nêu bằng `permits` hoặc được suy ra khi các kiểu con trực tiếp nằm trong cùng compilation unit. Mỗi nhánh trực tiếp tiếp tục quyết định khả năng mở rộng: final đóng nhánh, sealed tiếp tục giới hạn, còn non-sealed mở nhánh trở lại. Cơ chế này giữ được tính đa hình trong khi làm cho ranh giới của phân cấp kiểu rõ ràng và có thể kiểm tra khi biên dịch.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sealed-hierarchies-002">Sealed abstract class và sealed interface</a>

<details>
<summary>Xem chi tiết</summary>

# Sealed abstract class và sealed interface trong Java

## 1. Mục tiêu và ý tưởng

Từ Java 17, `sealed class` và `sealed interface` là tính năng chính thức giúp giới hạn những kiểu được phép kế thừa hoặc triển khai trực tiếp một kiểu. Chúng hữu ích khi mô hình miền có một tập hợp biến thể đã biết, chẳng hạn các loại hình học, trạng thái xử lý hoặc phương tiện vận tải.

`abstract` và `sealed` giải quyết hai vấn đề khác nhau:

- `abstract` ngăn tạo đối tượng trực tiếp từ một lớp và cho phép khai báo phương thức chưa có phần triển khai.
- `sealed` giới hạn tập hợp các lớp hoặc interface được phép kế thừa trực tiếp kiểu đó.
- Kết hợp `abstract sealed class` tạo một lớp cơ sở không thể khởi tạo, đồng thời kiểm soát rõ nhánh kế thừa của nó.

`sealed` không làm đối tượng bất biến, không tự biến mọi phương thức thành `final`, và cũng không thay thế việc kiểm tra dữ liệu đầu vào.

## 2. Sealed abstract class và sealed interface khác nhau thế nào?

| Khía cạnh | `sealed abstract class` | `sealed interface` |
| --- | --- | --- |
| Mục đích thường gặp | Chia sẻ trạng thái, constructor và hành vi chung cho một họ lớp | Định nghĩa hợp đồng cho một tập hợp kiểu được kiểm soát |
| Trạng thái đối tượng | Có thể có field instance | Không có field instance; field khai báo trong interface là hằng `public static final` |
| Constructor | Có thể khai báo constructor để khởi tạo trạng thái chung | Không có constructor |
| Phương thức | Có thể có phương thức abstract, concrete, final hoặc protected | Có thể có phương thức abstract, `default`, `static` và private theo quy tắc của interface |
| Kế thừa | Một lớp chỉ có thể extends một lớp | Một lớp có thể implements nhiều interface |
| Loại được phép kế thừa trực tiếp | Lớp con | Lớp hoặc interface con |

Chọn abstract class khi các biến thể có dữ liệu hoặc quy trình chung cần được dùng lại. Chọn interface khi trọng tâm là một khả năng hay hợp đồng mà các kiểu có thể thực hiện theo nhiều cách. Có thể dùng cả hai trong cùng một thiết kế.

## 3. Quy tắc của sealed hierarchy

### Khai báo và `permits`

```java
sealed abstract class Vehicle permits Car, Truck {
    abstract double maxLoad();
}

sealed interface Shape permits Circle, Rectangle {
    double area();
}
```

`permits` liệt kê các kiểu con trực tiếp được cho phép. Hãy liệt kê đúng các kiểu `extends` hoặc `implements` trực tiếp kiểu sealed; không liệt kê những lớp ở sâu hơn trong cây kế thừa.

Có thể bỏ `permits` khi toàn bộ kiểu con trực tiếp được khai báo trong cùng compilation unit với kiểu sealed; trình biên dịch sẽ suy ra danh sách đó. Nếu các kiểu được khai báo ở những tệp nguồn khác nhau, hãy khai báo `permits` rõ ràng.

### Chính sách của kiểu con trực tiếp

Class được phép kế thừa hoặc triển khai trực tiếp một sealed type phải là `final`, `sealed` hoặc `non-sealed`; record đã là `final` ngầm định. `final` đóng nhánh, `sealed` tiếp tục giới hạn theo tầng, còn `non-sealed` mở nhánh cho các hậu duệ không cần xuất hiện trong `permits` của tổ tiên. Riêng **interface con** không thể là `final`, nên chỉ chọn `sealed` hoặc `non-sealed`.

Một nhánh `Truck` là `non-sealed` có thể có `DeliveryTruck` kế thừa, nhưng `DeliveryTruck` vẫn là hậu duệ **gián tiếp** của `Vehicle`.

### Quy tắc về package và module

- Khi không dùng named module, kiểu sealed và các kiểu con trực tiếp được phép phải nằm trong cùng package.
- Khi dùng named module, chúng phải nằm trong cùng module; các package có thể khác nhau.
- Mỗi kiểu được liệt kê trong `permits` phải thật sự là kiểu con trực tiếp của kiểu sealed tương ứng.
- Một `record` là kiểu `final` ngầm định, nên có thể trực tiếp triển khai sealed interface khi đáp ứng các quy tắc còn lại. Record luôn kế thừa `java.lang.Record`, vì vậy **không thể** trực tiếp `extends` một sealed class.

### Sealed không đồng nghĩa với abstract

Một lớp sealed có thể concrete. `abstract` chỉ cần thiết khi muốn cấm khởi tạo trực tiếp lớp cơ sở hoặc còn phương thức abstract chưa được triển khai. Một sealed interface vốn không thể được khởi tạo trực tiếp, nên không cần từ khóa `abstract`.

## 4. Ví dụ Java 17 hoàn chỉnh

Ví dụ dưới đây minh họa hai hierarchy độc lập: `Shape` là sealed interface; `Vehicle` là sealed abstract class. `Circle` và `Car` đóng nhánh bằng `final`, còn `Rectangle` và `Truck` mở rộng nhánh bằng `non-sealed`.

Lưu nội dung sau thành `Main.java`:

```java
import java.util.Locale;
import java.util.Objects;

public class Main {
    public static void main(String[] args) {
        Shape circle = new Circle(2.0);
        Shape rectangle = new Rectangle(3.0, 4.0);
        System.out.printf(Locale.ROOT, "Circle area: %.2f%n", circle.area());
        System.out.printf(Locale.ROOT, "Rectangle area: %.2f%n", rectangle.area());

        Vehicle car = new Car("51A-123.45");
        Vehicle truck = new DeliveryTruck("51C-678.90", 8.5);
        System.out.println(car.description());
        System.out.printf(Locale.ROOT, "Truck max load: %.1f t%n", truck.maxLoad());

        if (truck instanceof Truck t) {
            System.out.println("Truck registration: " + t.registration());
        }
    }
}

sealed interface Shape permits Circle, Rectangle {
    double area();
}

final class Circle implements Shape {
    private final double radius;

    Circle(double radius) {
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        this.radius = radius;
    }

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }
}

non-sealed class Rectangle implements Shape {
    private final double width;
    private final double height;

    Rectangle(double width, double height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        this.width = width;
        this.height = height;
    }

    @Override
    public double area() {
        return width * height;
    }
}

sealed abstract class Vehicle permits Car, Truck {
    private final String registration;

    protected Vehicle(String registration) {
        this.registration = Objects.requireNonNull(registration, "registration");
    }

    public final String registration() {
        return registration;
    }

    public String description() {
        return getClass().getSimpleName() + " [" + registration + "]";
    }

    public abstract double maxLoad();
}

final class Car extends Vehicle {
    Car(String registration) {
        super(registration);
    }

    @Override
    public double maxLoad() {
        return 0.0;
    }
}

non-sealed class Truck extends Vehicle {
    private final double maxLoad;

    Truck(String registration, double maxLoad) {
        super(registration);
        if (maxLoad < 0) {
            throw new IllegalArgumentException("maxLoad must not be negative");
        }
        this.maxLoad = maxLoad;
    }

    @Override
    public double maxLoad() {
        return maxLoad;
    }
}

final class DeliveryTruck extends Truck {
    DeliveryTruck(String registration, double maxLoad) {
        super(registration, maxLoad);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```text
javac --release 17 Main.java
java Main
```

Kết quả stdout:

```text
Circle area: 12.57
Rectangle area: 12.00
Car [51A-123.45]
Truck max load: 8.5 t
Truck registration: 51C-678.90
```

## 5. Đọc ví dụ theo từng phần

- `Shape` định nghĩa hợp đồng `area()`. `Circle` và `Rectangle` là hai kiểu triển khai trực tiếp được cho phép.
- `Circle` là `final`, nên không thể có lớp con. `Rectangle` là `non-sealed`, nên có thể được mở rộng thêm mà không phải sửa danh sách `permits` của `Shape`.
- `Vehicle` vừa `sealed` vừa `abstract`. Constructor dùng chung kiểm tra `registration`, còn `description()` là hành vi chung và `maxLoad()` là yêu cầu mà từng loại xe phải triển khai.
- `Car` là kiểu con trực tiếp `final`; `Truck` là kiểu con trực tiếp `non-sealed`. `DeliveryTruck` mở rộng `Truck`, minh họa một kiểu con gián tiếp được phép bởi nhánh đã mở.
- Biến `truck` có kiểu khai báo `Vehicle`, nhưng đối tượng thực tế là `DeliveryTruck`. Biểu thức `truck instanceof Truck t` kiểm tra kiểu và tạo biến mẫu `t` khi điều kiện đúng; cú pháp này dùng được từ Java 16, nên phù hợp Java 17.
- `Locale.ROOT` giữ dấu thập phân của định dạng số ổn định, giúp stdout ở ví dụ không phụ thuộc locale của máy.

## 6. Lỗi thường gặp

1. **Quên khai báo trạng thái của kiểu con trực tiếp.** Một kiểu con trực tiếp của sealed class hoặc interface phải khai báo `final`, `sealed` hoặc `non-sealed` (record là `final` ngầm định). Thiếu lựa chọn này sẽ gây lỗi biên dịch.
2. **Đưa kiểu con gián tiếp vào `permits`.** Chỉ các kiểu `extends` hoặc `implements` trực tiếp mới được liệt kê.
3. **Đặt kiểu con ở sai package hoặc module.** Kiểm tra quy tắc package khi dùng unnamed module và quy tắc module khi dùng named module.
4. **Dùng `non-sealed` trên kiểu không có cha sealed trực tiếp.** Từ khóa này chỉ hợp lệ khi kiểu đó trực tiếp mở rộng hoặc triển khai một kiểu sealed.
5. **Nhầm `abstract` với `sealed`.** Một lớp abstract có thể được kế thừa tự do nếu không có ràng buộc khác; sealed mới là từ khóa giới hạn các kiểu con trực tiếp.
6. **Cho rằng `sealed` khóa toàn bộ cây kế thừa.** Các nhánh `non-sealed` vẫn cho phép mở rộng tiếp. Nếu cần đóng hoàn toàn một nhánh, dùng `final`; nếu muốn tiếp tục kiểm soát từng tầng, dùng `sealed` ở tầng tiếp theo.
7. **Dùng ví dụ cú pháp mới hơn phiên bản mục tiêu.** Sealed types được hoàn thiện trong Java 17. Nếu dùng pattern matching cho `switch`, cần kiểm tra phiên bản Java tương ứng; ví dụ trên không phụ thuộc tính năng `switch` mới hơn.

## 7. Khi nào nên dùng?

Dùng sealed hierarchy khi tập hợp biến thể hợp lệ có thể được xác định rõ ở cấp thiết kế và việc thêm một biến thể mới nên buộc người bảo trì xem lại các điểm xử lý liên quan. Đây là lựa chọn phù hợp cho mô hình miền, cây cú pháp, trạng thái giao dịch hoặc các kiểu kết quả có tập trường hợp hữu hạn.

Tránh dùng sealed chỉ để tạo thêm từ khóa cho một hierarchy vốn cần được mở rộng tự do bởi thư viện hoặc ứng dụng bên ngoài. Trong trường hợp đó, interface hoặc abstract class thông thường có thể phù hợp hơn.

Trước khi hoàn tất thiết kế, hãy kiểm tra:

- Danh sách kiểu con trực tiếp có phản ánh đúng các biến thể hợp lệ không?
- Mỗi kiểu con trực tiếp đã chọn `final`, `sealed` hoặc `non-sealed` chưa?
- Nên chia sẻ trạng thái và triển khai bằng abstract class, hay chỉ cần hợp đồng bằng interface?
- Có thực sự muốn mở nhánh kế thừa bằng `non-sealed` không?
- Phiên bản JDK dùng để biên dịch có hỗ trợ các tính năng xuất hiện trong mã nguồn không?


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sealed-hierarchies-003">Cú pháp permits và trường hợp suy ra danh sách cho phép</a>

<details>
<summary>Xem chi tiết</summary>

# Cú pháp `permits` và trường hợp suy ra danh sách cho phép

Trong Java, `sealed` giới hạn những kiểu nào được phép kế thừa trực tiếp một lớp hoặc triển khai trực tiếp một interface. Mệnh đề `permits` liệt kê các kiểu con trực tiếp được phép. Nếu các kiểu con đều được khai báo trong cùng một compilation unit, Java có thể tự suy ra danh sách này khi bỏ `permits`.

Các quy tắc này là kiểm tra ở thời điểm biên dịch. Chúng giúp mô hình hóa một hệ phân cấp có tập nhánh được kiểm soát, chẳng hạn các loại biểu thức, kết quả hoặc sự kiện mà chương trình biết trước.

## 1. Danh sách `permits` tường minh

Cú pháp cơ bản:

```java
sealed interface Shape permits Circle, Rectangle {
    double area();
}

record Circle(double radius) implements Shape {
    @Override
    public double area() {
        return Math.PI * radius * radius;
    }
}

final class Rectangle implements Shape {
    private final double width;
    private final double height;

    Rectangle(double width, double height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public double area() {
        return width * height;
    }
}
```

Ở đây, `Circle` và `Rectangle` là hai kiểu triển khai trực tiếp của `Shape`, nên cả hai xuất hiện trong `permits`. `Circle` là record và do đó mặc định là `final`; `Rectangle` khai báo `final` tường minh.

Danh sách có thể gồm nhiều tên, phân tách bằng dấu phẩy. Các kiểu được liệt kê phải thực sự là kiểu con trực tiếp của kiểu sealed. Có thể đặt chúng trong các tệp nguồn khác nhau nếu tuân thủ quy tắc package/module ở phần dưới.

## 2. Khi nào Java tự suy ra danh sách?

Nếu bỏ mệnh đề `permits`, trình biên dịch tìm các khai báo kiểu con trực tiếp nằm trong **cùng compilation unit** với kiểu sealed. Compilation unit thường tương ứng với một tệp nguồn Java. Ví dụ:

```java
sealed interface Command {
    String describe();
}

record Start(String name) implements Command {
    @Override
    public String describe() {
        return "start " + name;
    }
}

record Stop() implements Command {
    @Override
    public String describe() {
        return "stop";
    }
}
```

Danh sách được suy ra ở đây là `Start` và `Stop`, vì cả hai record đều trực tiếp triển khai `Command` và được khai báo trong cùng compilation unit. Record tự động là `final`, nên không cần ghi thêm từ khóa đó.

Việc suy ra có phạm vi rõ ràng: Java không tìm kiếm tùy ý tất cả tệp trong package. Nếu một kiểu con nằm ở tệp nguồn khác, hãy khai báo danh sách `permits` tường minh. Khi bỏ `permits` mà không có kiểu con trực tiếp nào được khai báo trong cùng compilation unit, khai báo sealed không hợp lệ.

## 3. Ví dụ hoàn chỉnh chạy được với Java 17+

Lưu nội dung sau trong tệp `PermitsDemo.java`. Ví dụ minh họa cả danh sách tường minh (`Shape`) và danh sách được suy ra (`Command`).

```java
public class PermitsDemo {
    public static void main(String[] args) {
        Shape circle = new Circle(2.0);
        Shape rectangle = new Rectangle(3.0, 4.0);
        System.out.printf("circle area = %.2f%n", circle.area());
        System.out.printf("rectangle area = %.2f%n", rectangle.area());

        Command command = new Start("worker");
        System.out.println(command.describe());
    }
}

sealed interface Shape permits Circle, Rectangle {
    double area();
}

record Circle(double radius) implements Shape {
    Circle {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be non-negative");
        }
    }

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }
}

final class Rectangle implements Shape {
    private final double width;
    private final double height;

    Rectangle(double width, double height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("dimensions must be non-negative");
        }
        this.width = width;
        this.height = height;
    }

    @Override
    public double area() {
        return width * height;
    }
}

sealed interface Command {
    String describe();
}

record Start(String name) implements Command {
    @Override
    public String describe() {
        return "start " + name;
    }
}

record Stop() implements Command {
    @Override
    public String describe() {
        return "stop";
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 PermitsDemo.java
java PermitsDemo
```

Kết quả chính xác:

```text
circle area = 12.57
rectangle area = 12.00
start worker
```

Lưu ý: `--release 17` yêu cầu trình biên dịch tạo mã tương thích Java 17. Cần cài JDK có hỗ trợ tùy chọn này.

## 4. Quy tắc bắt buộc

- **Chỉ liệt kê kiểu con trực tiếp.** Nếu `C` triển khai `B`, còn `B` triển khai `A`, thì `A` chỉ cho phép `B`; `C` thuộc nhánh của `B`, không được liệt kê trực tiếp trong `permits` của `A`.
- **Mọi kiểu con trực tiếp đều phải được cho phép.** Với danh sách tường minh, kiểu con trực tiếp bị bỏ sót sẽ gây lỗi biên dịch.
- **Kiểu con trực tiếp phải đóng hoặc khai báo cách mở rộng.** Nó phải là `final`, `sealed` hoặc `non-sealed`. Record mặc định là `final`, nên không cần ghi `final record`.
- **Quy tắc package/module phải được đáp ứng.** Trong unnamed module, kiểu sealed và các kiểu con được phép phải ở cùng package. Trong named module, chúng phải thuộc cùng module; chúng có thể nằm ở các package khác nhau trong module đó.
- **Suy ra danh sách chỉ dựa vào cùng compilation unit.** Các kiểu con ở tệp khác không tự được thêm vào danh sách khi bỏ `permits`.
- **`permits` chỉ dùng trên khai báo `sealed`.** Nó không phải danh sách mọi lớp có thể xuất hiện trong chương trình và cũng không thay thế các quy tắc kế thừa thông thường.

Nếu một kiểu con trực tiếp là `sealed`, kiểu đó có thể tiếp tục định nghĩa danh sách con riêng. Nếu là `non-sealed`, nhánh đó mở cho các kiểu khác kế thừa tiếp. Nếu là `final`, nhánh dừng tại kiểu đó.

## 5. Phân biệt kiểu con trực tiếp và gián tiếp

Trong ví dụ dưới đây, `Expr` cho phép `Literal` và `Binary`. `Add` và `Multiply` là các kiểu con trực tiếp của `Binary`, vì vậy chúng nằm trong danh sách của `Binary`, không phải của `Expr`.

```java
sealed interface Expr permits Literal, Binary {}

record Literal(int value) implements Expr {}

sealed interface Binary extends Expr permits Add, Multiply {}

record Add(Expr left, Expr right) implements Binary {}

record Multiply(Expr left, Expr right) implements Binary {}
```

Mỗi record là một kiểu con cuối cùng. Cấu trúc này thể hiện rõ hai tầng: `Expr` quyết định các nhánh cấp một, còn `Binary` quyết định các phép toán nhị phân được phép.

## 6. Kiểu sealed có tham số kiểu

Với kiểu generic, các tên trong `permits` là tên kiểu, không ghi đối số kiểu tại danh sách:

```java
sealed interface Result<T> permits Success, Failure {}

record Success<T>(T value) implements Result<T> {}

record Failure<T>(String message) implements Result<T> {}
```

`Success<T>` và `Failure<T>` trực tiếp triển khai `Result<T>`; các record là `final` ngầm định. Quy tắc suy ra danh sách cũng áp dụng cho kiểu generic nếu các kiểu con trực tiếp được khai báo trong cùng compilation unit.

## 7. Lỗi thường gặp và cách tìm nguyên nhân

| Tình huống | Nguyên nhân và cách sửa |
|---|---|
| Bỏ `permits`, nhưng kiểu con nằm ở tệp khác | Thêm danh sách `permits` tường minh và liệt kê kiểu con trực tiếp. |
| Có kiểu con trực tiếp không nằm trong danh sách | Bổ sung kiểu đó vào `permits`, hoặc điều chỉnh quan hệ kế thừa cho đúng thiết kế. |
| Liệt kê một kiểu không trực tiếp kế thừa/triển khai kiểu sealed | Sửa danh sách để chỉ chứa kiểu con trực tiếp. |
| Kiểu con trực tiếp không phải `final`, `sealed` hoặc `non-sealed` | Chọn một trong các trạng thái này; record đã là `final` ngầm định. |
| Các kiểu ở sai package hoặc module | Đưa chúng về cùng package trong unnamed module hoặc cùng module trong named module. |
| Bỏ `permits` nhưng không có kiểu con trực tiếp trong cùng tệp | Khai báo ít nhất một kiểu con trực tiếp trong compilation unit hoặc dùng `permits` tường minh. |

Khi đọc thông báo lỗi, hãy kiểm tra theo thứ tự: kiểu nào khai báo `sealed`, quan hệ `extends`/ `implements` nào là trực tiếp, danh sách `permits` đang khai báo gì, rồi mới kiểm tra package/module và từ khóa của kiểu con.

## 8. Chọn danh sách tường minh hay suy ra?

Dùng danh sách tường minh khi các kiểu con được đặt ở nhiều tệp, khi muốn thấy hợp đồng của hệ phân cấp ngay tại khai báo, hoặc khi danh sách cho phép cần được rà soát rõ ràng trong code review.

Có thể bỏ `permits` khi toàn bộ kiểu con trực tiếp được gom trong cùng compilation unit và việc suy ra giúp mã gọn hơn. Khi đó, thêm một kiểu con trực tiếp mới vào cùng tệp cũng làm thay đổi danh sách được suy ra, vì vậy hãy cân nhắc liệu sự thay đổi ngầm này có phù hợp với mục đích của hệ phân cấp hay không.

Trước khi hoàn tất một khai báo, kiểm tra nhanh:

1. Kiểu cha đã được khai báo `sealed` chưa?
2. Danh sách chỉ gồm các kiểu con trực tiếp chưa?
3. Mọi kiểu con trực tiếp đã được cho phép chưa?
4. Nếu bỏ `permits`, tất cả kiểu con trực tiếp có nằm trong cùng compilation unit không?
5. Mỗi kiểu con trực tiếp đã là `final`, `sealed`, `non-sealed` hoặc record chưa?
6. Quy tắc package/module có đúng không?

## Tài liệu tham khảo

- [Java Language Specification 17: Sealed Classes](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.1.6)
- [Java Language Specification 17: Sealed Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.1.4)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sealed-hierarchies-004">Yêu cầu cùng module hoặc cùng package trong unnamed module</a>

<details>
<summary>Xem chi tiết</summary>

# Yêu cầu cùng module hoặc cùng package trong unnamed module

## Mục tiêu

Bài này giải thích ranh giới vị trí mà Java áp dụng cho các kiểu được `permits` trong một sealed hierarchy: khi sealed type thuộc **named module**, các kiểu con được phép phải thuộc cùng module; khi nó thuộc **unnamed module**, các kiểu con được phép phải nằm trong cùng package. Bạn sẽ biết cách khai báo `sealed`, `permits`, chọn `final`/ `sealed`/ `non-sealed` cho lớp con và nhận ra những lỗi biên dịch thường gặp.

## 1. Sealed type kiểm tra gì khi biên dịch?

~~~java
public sealed interface Shape permits Circle, Rectangle {}
~~~

`permits` chỉ liệt kê các kiểu con **trực tiếp**. `Circle` và `Rectangle` phải thực sự `implements Shape` và cùng đáp ứng chính sách nhánh: class khai báo `final`, `sealed` hoặc `non-sealed`; record là `final` ngầm định. Interface con chỉ dùng `sealed` hoặc `non-sealed`.

Enum cũng có thể trực tiếp triển khai sealed interface. Enum **không có** hằng với thân lớp riêng là `final` ngầm định; enum **có** hằng với thân lớp riêng là `sealed` ngầm định. Enum không thể `extends` một sealed class tùy ý. Vị trí của các kiểu con được phép là quy tắc tiếp theo cần kiểm tra.

## 2. Named module: các kiểu được phép phải cùng module

Một **named module** là module được khai báo bằng tệp `module-info.java`. Nếu sealed type nằm trong named module, tất cả các kiểu con trực tiếp được liệt kê trong `permits` phải thuộc cùng named module đó. Chúng **có thể nằm ở các package khác nhau** trong module.

Ví dụ cấu trúc:

~~~text
src/
└── com.example.shapes/
    ├── module-info.java
    ├── com/example/api/Shape.java
    └── com/example/impl/Circle.java
~~~

`module-info.java`:

~~~java
module com.example.shapes {
    exports com.example.api;
}
~~~

`com/example/api/Shape.java`:

~~~java
package com.example.api;

public sealed interface Shape permits com.example.impl.Circle {
}
~~~

`com/example/impl/Circle.java`:

~~~java
package com.example.impl;

public final class Circle implements com.example.api.Shape {
}
~~~

Trong ví dụ, `Shape` và `Circle` ở hai package khác nhau nhưng cùng thuộc module `com.example.shapes`, vì cả hai được biên dịch và đóng gói trong cùng module. Đây là cách hợp lệ theo quy tắc vị trí của sealed hierarchy. Ví dụ minh họa cấu trúc module; để chạy thực tế, hãy biên dịch module theo cấu trúc thư mục module-source-path hoặc công cụ build của dự án.

Nếu chuyển `Circle` sang một named module khác, khai báo `permits com.example.impl.Circle` sẽ không hợp lệ dù tên kiểu được viết đúng và kiểu đó trực tiếp triển khai `Shape`. Ranh giới bắt buộc là module, không phải chỉ là việc package có cùng tên.

## 3. Unnamed module: các kiểu được phép phải cùng package

Nếu sealed type được nạp trong **unnamed module** (trường hợp thường gặp khi chạy ứng dụng từ classpath mà không có khai báo module), tất cả các kiểu con trực tiếp được phép phải ở **cùng package** với sealed type.

Ví dụ này chạy trên classpath và dùng package `demo` cho tất cả các kiểu:

`src/demo/Transport.java`

~~~java
package demo;

public sealed interface Transport permits Car, Bike {
}
~~~

`src/demo/Car.java`

~~~java
package demo;

public final class Car implements Transport {
}
~~~

`src/demo/Bike.java`

~~~java
package demo;

public final class Bike implements Transport {
}
~~~

`src/demo/SealedDemo.java`

~~~java
package demo;

public class SealedDemo {
    public static void main(String[] args) {
        Transport transport = new Car();
        System.out.println(transport instanceof Car ? "car" : "other");
    }
}
~~~

Biên dịch từ thư mục gốc chứa `src`:

~~~text
javac -d out src/demo/Transport.java src/demo/Car.java src/demo/Bike.java src/demo/SealedDemo.java
java -cp out demo.SealedDemo
~~~

Kết quả stdout:

~~~text
car
~~~

Không có `module-info.java` nên các lớp trong ví dụ được chạy từ classpath và thuộc unnamed module. Tất cả khai báo `package demo;` vì vậy `Car` và `Bike` cùng package với `Transport`, thỏa quy tắc của unnamed module.

Nếu đổi `Car` thành `package demo.impl;`, chỉ việc đặt tệp trong thư mục con `demo/impl` không đủ để làm nó cùng package với `Transport`. Package được quyết định bởi khai báo `package` và danh tính package trong môi trường biên dịch/nạp lớp, không chỉ bởi vị trí tệp. Khi dùng unnamed module, kiểu con trực tiếp được phép ở package khác sẽ gây lỗi biên dịch.

## 4. Đừng nhầm unnamed module với unnamed package

Hai khái niệm này khác nhau:

- **Unnamed module:** mã không thuộc một named module. Ứng dụng Java truyền thống chạy từ classpath thường nằm trong unnamed module.
- **Unnamed package:** kiểu không có khai báo `package` ở đầu tệp nguồn.

Một unnamed module vẫn có thể chứa nhiều package có tên, chẳng hạn `demo`, `demo.model` và `demo.service`. Với sealed type trong unnamed module, các kiểu con trực tiếp được phép phải ở cùng package cụ thể với kiểu cha; việc cả hai cùng thuộc unnamed module là chưa đủ.

Ngược lại, khi dùng named module, quy tắc vị trí cho phép các kiểu con trực tiếp ở package khác nhau miễn là tất cả nằm trong cùng module. Vì vậy, hãy xác định module trước, rồi áp dụng đúng ranh giới: **cùng module nếu là named module; cùng package nếu là unnamed module**.

## 5. Khi nào cần ghi permits?

Nếu các kiểu con trực tiếp được phép được khai báo cùng compilation unit với sealed type, Java có thể suy ra danh sách permitted subclasses và mệnh đề `permits` có thể được bỏ qua. Nếu các kiểu con được khai báo ở những compilation unit khác, cần khai báo danh sách `permits` rõ ràng.

Ví dụ khai báo trong cùng một tệp nguồn:

~~~java
sealed interface Message {
}

final class TextMessage implements Message {
}

final class BinaryMessage implements Message {
}
~~~

Trong trường hợp này, hai kiểu con được khai báo cùng compilation unit với `Message`, nên danh sách permitted subclasses có thể được suy ra. Dù viết `permits` tường minh hay để compiler suy ra, các quy tắc về module/package và modifier của kiểu con vẫn áp dụng như nhau.

## 6. Lỗi thường gặp và cách chẩn đoán

1. **Cùng module nhưng khác package trong unnamed module:** không hợp lệ. Trong unnamed module, kiểu con trực tiếp phải cùng package với sealed type.
2. **Cùng package name nhưng thuộc module khác:** không hợp lệ với sealed type trong named module. Các kiểu con trực tiếp phải thuộc cùng named module.
3. **Chỉ dựa vào đường dẫn thư mục:** thư mục trông giống nhau không chứng minh package/module hợp lệ. Kiểm tra khai báo `package`, `module-info.java` và cách biên dịch.
4. **Quên khai báo kiểu con trong permits:** khi kiểu con nằm ở compilation unit khác, thêm nó vào mệnh đề `permits` của sealed type.
5. **Kiểu con không trực tiếp kế thừa/triển khai sealed type:** `permits` chỉ liệt kê các kiểu con trực tiếp, không phải cháu/chắt trong cây kế thừa.
6. **Kiểu con không có modifier phù hợp:** lớp con trực tiếp phải là `final`, `sealed` hoặc `non-sealed` (record là final ngầm định).
7. **Hiểu nhầm phạm vi của quy tắc:** ràng buộc cùng package/module nói về các permitted direct subclasses. Nếu một lớp con được phép là `non-sealed`, các lớp kế thừa tiếp từ nó tuân theo quy tắc kế thừa thông thường và không tự động phải ở cùng package/module với sealed ancestor.

Khi gặp lỗi, hãy kiểm tra lần lượt: sealed type có `permits` đúng không; mỗi tên có trỏ tới kiểu con trực tiếp không; kiểu cha và kiểu con thuộc named module hay unnamed module; vị trí package/module có đúng quy tắc không; và modifier của mỗi kiểu con có hợp lệ không.

## 7. Checklist thiết kế

- Xác định sealed type được biên dịch trong named module hay unnamed module.
- Với named module, giữ mọi kiểu con trực tiếp được phép trong cùng module; package có thể khác nhau.
- Với unnamed module, đặt mọi kiểu con trực tiếp được phép trong cùng package với sealed type.
- Khai báo `permits` khi các kiểu con không cùng compilation unit hoặc khi cần thể hiện danh sách được phép rõ ràng.
- Chọn `final`, `sealed` hoặc `non-sealed` cho từng kiểu con theo chính sách mở rộng.
- Biên dịch bằng cấu hình module/classpath thực tế của dự án để phát hiện sai lệch giữa package và module.

## Kết luận

Quy tắc vị trí của sealed hierarchy phụ thuộc vào loại module chứa sealed type. Trong named module, các permitted direct subclasses phải thuộc cùng module nhưng có thể ở package khác nhau. Trong unnamed module, chúng phải nằm cùng package với sealed type. Phân biệt đúng hai trường hợp giúp tổ chức mã sealed rõ ràng và tránh lỗi biên dịch khi chia kiểu ra nhiều tệp hoặc package.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sealed-hierarchies-005">Modifier final, sealed và non-sealed của lớp con trực tiếp</a>

<details>
<summary>Xem chi tiết</summary>

# Modifier `final`, `sealed` và `non-sealed` của lớp con trực tiếp

Trong Java, một kiểu được khai báo bằng `sealed` kiểm soát những lớp hoặc interface nào được phép kế thừa hay triển khai trực tiếp nó. Mỗi kiểu con trực tiếp phải xác định chính sách kế thừa tiếp theo bằng `final`, `sealed` hoặc `non-sealed` (trừ những kiểu vốn đã ngầm `final`, chẳng hạn record).

Ba modifier này trả lời ba câu hỏi khác nhau: nhánh kế thừa có kết thúc ở đây không, có tiếp tục nhưng vẫn bị giới hạn không, hay được mở cho mã bên ngoài mở rộng tự do?

## 1. Bối cảnh: sealed type và các nhánh kế thừa

Sealed classes và sealed interfaces trở thành tính năng chính thức trong Java 17. Chúng hữu ích khi mô hình miền nghiệp vụ có một tập hợp kiểu con được kiểm soát, ví dụ các loại kết quả, hình học, thông điệp hoặc phương thức thanh toán.

Ví dụ, một interface `Payment` có thể cho phép đúng ba kiểu trực tiếp: `CardPayment`, `CashPayment` và `CustomPayment`. Java yêu cầu từng nhánh này khai báo cách nó tiếp tục được kế thừa.

- `final`: nhánh kết thúc tại kiểu này.
- `sealed`: nhánh tiếp tục, nhưng chỉ các kiểu được cho phép mới có thể kế thừa trực tiếp kiểu này.
- `non-sealed`: nhánh được mở lại; các lớp khác có thể kế thừa kiểu này mà không cần được liệt kê trong danh sách `permits` của tổ tiên sealed.

Các modifier trên áp dụng cho khai báo lớp hoặc interface. Chúng không phải modifier của phương thức và không tự quyết định một đối tượng có bất biến hay không.

## 2. Ba modifier khác nhau như thế nào?

| Modifier trên kiểu con trực tiếp | Ý nghĩa | Kế thừa tiếp theo |
| --- | --- | --- |
| `final` | Kiểu này là lá của nhánh kế thừa. | Không lớp nào được kế thừa lớp này; một interface `final` không hợp lệ. |
| `sealed` | Kiểu này tiếp tục giới hạn các kiểu con trực tiếp của chính nó. | Các kiểu con trực tiếp tiếp theo phải được khai báo trong `permits`, hoặc được trình biên dịch suy ra khi đủ điều kiện. |
| `non-sealed` | Kiểu này chủ động bỏ giới hạn sealed đối với nhánh của nó. | Các lớp có thể kế thừa kiểu này mà không cần nằm trong danh sách `permits` của kiểu sealed ban đầu. |

### `final`: đóng nhánh kế thừa

Khi một lớp trực tiếp kế thừa lớp sealed hoặc trực tiếp triển khai interface sealed và được khai báo `final`, không thể tạo lớp con từ lớp đó.

```java
sealed interface Payment permits CardPayment {}

final class CardPayment implements Payment {}
```

Trong ví dụ này, `CardPayment` là một kiểu được phép của `Payment`, và nhánh `CardPayment` kết thúc tại đây. Khai báo như `class PremiumCardPayment extends CardPayment {}` sẽ không biên dịch vì `CardPayment` là `final`.

Dùng `final` khi kiểu con đại diện cho một trường hợp cụ thể mà thiết kế không cho phép mở rộng thêm.

### `sealed`: tiếp tục giới hạn một nhánh

Một kiểu con có thể tự nó là sealed. Khi đó, nó trở thành một sealed type mới với danh sách kiểu con trực tiếp riêng.

```java
sealed interface Payment permits CashPayment {}

sealed class CashPayment implements Payment
        permits ExactCashPayment {}

final class ExactCashPayment extends CashPayment {}
```

`CashPayment` là kiểu được `Payment` cho phép, đồng thời giới hạn những lớp có thể kế thừa nó. Trong ví dụ, chỉ `ExactCashPayment` được phép kế thừa trực tiếp `CashPayment`. Một lớp khác tự khai báo `extends CashPayment` mà không được liệt kê sẽ bị từ chối khi biên dịch.

Dùng `sealed` khi muốn phân cấp có nhiều tầng nhưng mỗi tầng vẫn phải tuân theo một danh sách kiểu con được kiểm soát.

### `non-sealed`: mở lại một nhánh

`non-sealed` cho phép một kiểu con trực tiếp của sealed type trở thành điểm mở rộng thông thường.

```java
sealed interface Payment permits CustomPayment {}

non-sealed class CustomPayment implements Payment {}

class BrandedPayment extends CustomPayment {}
class PartnerPayment extends CustomPayment {}
```

`CustomPayment` vẫn phải được liệt kê trong `permits` của `Payment`, vì nó là kiểu triển khai trực tiếp. Sau đó, `BrandedPayment` và `PartnerPayment` có thể kế thừa `CustomPayment` mà không cần được thêm vào `permits` của `Payment`.

Dùng `non-sealed` khi muốn kiểm soát các loại trực tiếp của một abstraction, nhưng vẫn muốn cho thư viện, plugin hoặc mã ứng dụng mở rộng một nhánh cụ thể.

Lưu ý rằng `non-sealed` không có nghĩa là kiểu đó có thể được thêm tùy ý vào danh sách trực tiếp của sealed type. Bản thân kiểu `non-sealed` vẫn phải là một kiểu con trực tiếp được cho phép.

## 3. Ví dụ Java 17+ có thể biên dịch và chạy

Lưu nội dung sau vào tệp `SealedHierarchyDemo.java`. Tất cả các kiểu đều nằm trong cùng compilation unit và package mặc định để ví dụ tập trung vào modifier.

```java
public class SealedHierarchyDemo {
    public static void main(String[] args) {
        Payment[] payments = {
            new CardPayment(),
            new ExactCashPayment(),
            new CustomPayment(),
            new BrandedPayment()
        };

        for (Payment payment : payments) {
            System.out.println(payment.label());
        }
    }
}

sealed interface Payment
        permits CardPayment, CashPayment, CustomPayment {
    String label();
}

final class CardPayment implements Payment {
    @Override
    public String label() {
        return "card";
    }
}

sealed class CashPayment implements Payment
        permits ExactCashPayment {
    @Override
    public String label() {
        return "cash";
    }
}

final class ExactCashPayment extends CashPayment {}

non-sealed class CustomPayment implements Payment {
    @Override
    public String label() {
        return "custom";
    }
}

final class BrandedPayment extends CustomPayment {
    @Override
    public String label() {
        return "branded";
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```text
javac --release 17 SealedHierarchyDemo.java
java SealedHierarchyDemo
```

Kết quả chuẩn (stdout):

```text
card
cash
custom
branded
```

Ví dụ cho thấy modifier không thay thế tính đa hình. Mảng có kiểu phần tử là `Payment`, nhưng mỗi đối tượng vẫn thực thi phiên bản `label()` của lớp thực tế. Các modifier kiểm soát cấu trúc kế thừa tại thời điểm biên dịch.

## 4. Đọc cấu trúc phân cấp trong ví dụ

```text
Payment (sealed)
├── CardPayment (final)
├── CashPayment (sealed)
│   └── ExactCashPayment (final)
└── CustomPayment (non-sealed)
    └── BrandedPayment (final)
```

- `CardPayment` đóng nhánh của nó bằng `final`.
- `CashPayment` tạo thêm một tầng được kiểm soát bằng `sealed`; danh sách cho phép của tầng này chỉ gồm `ExactCashPayment`.
- `CustomPayment` là một nhánh mở. Vì thế `BrandedPayment` có thể kế thừa nó dù không xuất hiện trong `permits` của `Payment`.
- `BrandedPayment` lại được khai báo `final`, nên nhánh này kết thúc tại lớp đó.

Các modifier được xét theo quan hệ kế thừa trực tiếp. Một lớp nằm sâu trong nhánh `non-sealed` không cần khai báo `final`, `sealed` hoặc `non-sealed` chỉ vì tổ tiên xa của nó là sealed. Những quy tắc này áp dụng khi lớp đó trực tiếp kế thừa hoặc triển khai một sealed type.

## 5. Đọc `permits` theo từng nhánh

Trong ví dụ chính, `Payment` liệt kê các kiểu triển khai trực tiếp `CardPayment`, `CashPayment`, `CustomPayment`; `CashPayment` tự cho phép `ExactCashPayment`. `BrandedPayment` chỉ mở rộng `CustomPayment`, vì vậy không xuất hiện trong `permits` của `Payment`. Muốn thêm một biến thể trực tiếp, phải chủ động sửa danh sách ở đúng tầng.

Có thể bỏ `permits` nếu toàn bộ kiểu con trực tiếp của một sealed type được khai báo cùng compilation unit để trình biên dịch tự suy ra; nó không tìm kiểu ở tệp khác. Kiểu con được phép phải cùng named module với kiểu sealed, hoặc cùng package nếu thuộc unnamed module. Đồng thời, quan hệ `extends` / `implements` phải đúng; tên xuất hiện trong `permits` không tự tạo quan hệ kế thừa.

## 6. Những lỗi biên dịch thường gặp

Các đoạn dưới đây được cố ý viết sai để minh họa quy tắc; không thêm chúng vào chương trình mẫu đang chạy.

**Kế thừa một lớp `final`:**

```java
class AnotherCardPayment extends CardPayment {}
```

Lỗi: không thể kế thừa `CardPayment` vì lớp này là `final`.

**Kế thừa một sealed class nhưng không được cho phép:**

```java
class OtherCashPayment extends CashPayment {}
```

Lỗi: `CashPayment` chỉ cho phép `ExactCashPayment` làm kiểu con trực tiếp. Muốn có kiểu con trực tiếp mới, phải thay đổi khai báo sealed có thẩm quyền đối với nhánh đó.

**Triển khai sealed interface nhưng không nằm trong danh sách cho phép:**

```java
class UnknownPayment implements Payment {}
```

Lỗi: `UnknownPayment` không được liệt kê trong `permits` của `Payment`. Khai báo `implements Payment` không tự động đăng ký kiểu mới.

**Dùng `non-sealed` mà kiểu cha trực tiếp không phải sealed:**

```java
non-sealed class OrdinaryExtension {}
```

Lỗi: `non-sealed` chỉ hợp lệ với kiểu con trực tiếp của một sealed class hoặc kiểu triển khai trực tiếp của sealed interface. Nó không phải cách khai báo chung cho một lớp mở thông thường.

**Nhầm `sealed` với `final`:**

Một kiểu không thể đồng thời là `sealed` và `final`: `sealed` cho phép một tập kiểu con, còn `final` cấm mọi lớp con. Tương tự, `final` và `abstract` không thể cùng áp dụng cho một lớp vì ý nghĩa của chúng xung đột.

**Quên chính sách của sealed subclass:**

Nếu một kiểu con trực tiếp không tự đóng bằng `final`, nó phải tiếp tục kiểm soát phân cấp bằng `sealed` hoặc chủ động mở bằng `non-sealed`. Một sealed subclass còn cần danh sách kiểu con được phép riêng, trừ khi danh sách có thể được suy ra hợp lệ từ cùng compilation unit.

## 7. Các trường hợp đặc biệt cần nhớ

- **Lớp trừu tượng vẫn có thể sealed.** Ví dụ `abstract sealed class Node permits Leaf, Branch {}` có thể mô hình hóa một nhánh mà bản thân `Node` không được khởi tạo trực tiếp. Các kiểu con vẫn phải tuân theo quy tắc của sealed type.
- **Record vốn đã final.** Một record có thể là kiểu được phép triển khai sealed interface mà không cần viết thêm modifier `final`, vì record không thể được kế thừa như một lớp thông thường.
- **Interface cũng có thể sealed.** Các kiểu trực tiếp được liệt kê có thể là lớp triển khai hoặc subinterface. Một subinterface sealed có thể giới hạn nhánh tiếp theo; subinterface `non-sealed` mở nhánh cho các interface khác mở rộng.
- **`non-sealed` không xóa chính sách của tổ tiên.** Nó chỉ mở rộng từ kiểu được khai báo `non-sealed` trở xuống. Tập kiểu trực tiếp của sealed type gốc vẫn là tập được khai báo trong `permits`.
- **Sealed không làm đối tượng bất biến.** Trạng thái mutable, quyền truy cập thành viên, khả năng ghi đè phương thức và quy tắc khởi tạo vẫn tuân theo các quy tắc Java tương ứng.
- **Sealed hữu ích cho kiểm tra tính đầy đủ.** Các phiên bản Java mới hơn có thể dùng sealed hierarchy cùng pattern matching trong `switch` để kiểm tra các trường hợp đã được xử lý. Cú pháp pattern switch có phiên bản và yêu cầu ngôn ngữ riêng; không cần nó để biên dịch ví dụ Java 17 ở trên.

## 8. Cách chọn modifier

Chọn modifier theo chính sách mà API muốn công bố:

1. Dùng `final` khi kiểu đại diện cho một trường hợp cụ thể và không được mở rộng.
2. Dùng `sealed` khi nhánh có các biến thể đã biết, nhưng vẫn cần một tầng phân loại tiếp theo có kiểm soát.
3. Dùng `non-sealed` khi muốn giữ một điểm mở rộng cho bên sử dụng API hoặc cho mã của module khác, trong khi các kiểu trực tiếp của abstraction gốc vẫn được kiểm soát.

Ví dụ, một thư viện có thể cho phép các kiểu thanh toán lõi như `CardPayment` và `CashPayment`, nhưng đồng thời cung cấp `CustomPayment` như điểm mở rộng. Nhờ vậy, thư viện giữ được một ranh giới rõ ràng giữa các trường hợp lõi và các triển khai do bên sử dụng cung cấp.

Đừng chọn `non-sealed` chỉ để làm biến mất lỗi biên dịch. Hãy xác định xem việc mở rộng tùy ý có phải là một phần của hợp đồng API hay không. Việc mở nhánh ảnh hưởng đến những kiểu mà ứng dụng có thể định nghĩa về sau và đến khả năng phân tích đầy đủ các trường hợp trong mã sử dụng hierarchy.

## 9. Bảng xử lý nhanh

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| Lớp không thể kế thừa một kiểu con | Kiểu cha là `final`. | Kế thừa một nhánh được thiết kế để mở rộng; chỉ đổi modifier nếu chính sách thiết kế cho phép. |
| Lớp mới không thể trực tiếp triển khai sealed interface | Lớp chưa được liệt kê trong `permits`. | Bổ sung vào danh sách được kiểm soát nếu đó là thay đổi API có chủ đích. |
| Sealed subclass báo lỗi về kiểu con | Danh sách `permits` của tầng đó thiếu hoặc không hợp lệ. | Kiểm tra quan hệ trực tiếp và khai báo danh sách cho phép của chính sealed subclass. |
| `non-sealed` bị từ chối | Kiểu cha trực tiếp không phải sealed type. | Chỉ dùng `non-sealed` ở một nhánh trực tiếp của sealed class/interface. |
| Kiểu được cho phép nhưng vẫn không biên dịch | Sai quan hệ `extends`/`implements`, khác module/package theo quy tắc, hoặc khai báo modifier không hợp lệ. | Kiểm tra đầy đủ quan hệ kế thừa, vị trí package/module và modifier của kiểu con. |

## 10. Checklist khi đọc hoặc thiết kế hierarchy

- Xác định sealed class hoặc sealed interface gốc.
- Đọc `permits` để biết các kiểu con trực tiếp được phép; nếu không có, kiểm tra xem có thể suy luận từ cùng compilation unit không.
- Với từng kiểu con trực tiếp, xác định nó là `final`, `sealed` hay `non-sealed` (hoặc một kiểu ngầm `final` như record).
- Nếu kiểu con là sealed, tiếp tục đọc danh sách `permits` của chính nó.
- Nếu kiểu con là `non-sealed`, xem đó là ranh giới mà nhánh bắt đầu mở rộng tự do.
- Kiểm tra quy tắc package/module và chạy trình biên dịch để xác nhận khai báo thực tế.
- Giữ ví dụ tối thiểu, có tên tệp đúng, lệnh biên dịch rõ ràng và kết quả đầu ra có thể kiểm tra được.

Khi đọc một sealed hierarchy, hãy theo từng cạnh trực tiếp của cây kế thừa. `final` kết thúc nhánh, `sealed` tạo một tầng được kiểm soát khác, còn `non-sealed` mở rộng nhánh đó cho các kiểu con mới.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sealed-hierarchies-006">Interface con sealed hoặc non-sealed</a>

<details>
<summary>Xem chi tiết</summary>

# Interface con `sealed` và `non-sealed` trong Java

Từ Java 17, sealed classes và sealed interfaces là tính năng chính thức để giới hạn những kiểu nào được phép kế thừa hoặc triển khai trực tiếp một kiểu cha. Với interface, cơ chế này giúp mô hình hóa một tập hợp nhánh đã biết, đồng thời cho phép mở rộng có chủ đích ở những nhánh cần linh hoạt.

Điểm cần phân biệt: `sealed` giới hạn các kiểu con trực tiếp; `non-sealed` mở rộng một nhánh của hệ phân cấp để những kiểu khác tiếp tục triển khai hoặc kế thừa nhánh đó.

## 1. Vì sao interface con không thể là `final`?

Một sealed interface có thể cho phép cả class trực tiếp triển khai và interface con trực tiếp mở rộng. Một **class** được phép cần có chính sách `final`, `sealed` hoặc `non-sealed` (record vốn final). Một **interface con** chỉ được chọn `sealed` hoặc `non-sealed`: Java không cho phép khai báo `final interface`.

`sealed interface` con đóng danh sách nhánh trực tiếp ở tầng mới; `non-sealed interface` con là hợp đồng mở cho những class hay interface tiếp theo. Vì vậy, dùng `non-sealed` trên interface con là quyết định về quyền mở rộng API, không chỉ là cách tránh lỗi biên dịch.

## 2. Cách khai báo `sealed interface`

Dùng mệnh đề `permits` để liệt kê các kiểu con trực tiếp:

~~~java
sealed interface PaymentMethod
        permits CardPayment, CashPayment, FlexiblePaymentMethod {
    String description();
}
~~~

Trong ví dụ, chỉ `CardPayment`, `CashPayment` và `FlexiblePaymentMethod` được phép trực tiếp triển khai hoặc mở rộng `PaymentMethod`. Danh sách này không liệt kê mọi kiểu nằm sâu hơn trong hệ phân cấp.

Mỗi kiểu được liệt kê phải thật sự là kiểu con trực tiếp của sealed type. Nếu `CardPayment` là một class `sealed`, nó phải có danh sách `permits` của riêng mình. Nếu `FlexiblePaymentMethod` là `non-sealed`, các kiểu như `GiftCardPayment` có thể triển khai interface đó mà không cần xuất hiện trong danh sách của `PaymentMethod`.

Nếu bỏ mệnh đề `permits`, các kiểu con trực tiếp được cho phép phải được khai báo cùng compilation unit với sealed type. Khai báo tường minh thường dễ đọc hơn khi cần thể hiện rõ phạm vi được phép.

## 3. Các quy tắc của Java

- Sealed classes và sealed interfaces được hoàn thiện trong Java 17. Khi biên dịch, hãy dùng source level tương thích, chẳng hạn `javac --release 17`.
- Các kiểu được nêu trong `permits` phải là kiểu con trực tiếp của sealed class/interface đó.
- Trong named module, sealed type và các kiểu con trực tiếp được phép phải thuộc cùng module. Trong unnamed module, chúng phải thuộc cùng package.
- Mỗi kiểu con trực tiếp phải xác định nhánh bằng `final`, `sealed` hoặc `non-sealed`; record và enum vốn final theo quy tắc của ngôn ngữ.
- `non-sealed` chỉ hợp lệ khi kiểu đó trực tiếp kế thừa hoặc triển khai một sealed type. Viết đúng dấu gạch nối: `non-sealed`.
- Quy tắc sealed kiểm soát quan hệ kế thừa trực tiếp. Một kiểu ở sâu hơn có thể không nằm trong `permits` của interface gốc, miễn là nó đi qua một nhánh đã được mở bằng `non-sealed`.

## 4. Ví dụ hoàn chỉnh chạy được trên Java 17

Lưu nội dung sau vào file `SealedInterfaceDemo.java`. Tất cả các kiểu đều ở cùng file và cùng unnamed package để ví dụ đơn giản, có thể biên dịch trực tiếp.

~~~java
public class SealedInterfaceDemo {
    public static void main(String[] args) {
        PaymentMethod[] methods = {
            new CashPayment(),
            new CardPayment("4242"),
            new InternationalCardPayment("7777", "VN"),
            new GiftCardPayment("GC-01")
        };

        for (PaymentMethod method : methods) {
            System.out.println(method.description());
        }
    }
}

sealed interface PaymentMethod
        permits CardPayment, CashPayment, FlexiblePaymentMethod {
    String description();
}

sealed class CardPayment implements PaymentMethod
        permits InternationalCardPayment {
    private final String lastFour;

    CardPayment(String lastFour) {
        this.lastFour = lastFour;
    }

    @Override
    public String description() {
        return "Card ****" + lastFour;
    }
}

final class InternationalCardPayment extends CardPayment {
    private final String country;

    InternationalCardPayment(String lastFour, String country) {
        super(lastFour);
        this.country = country;
    }

    @Override
    public String description() {
        return "International " + super.description() + " (" + country + ")";
    }
}

record CashPayment() implements PaymentMethod {
    @Override
    public String description() {
        return "Cash";
    }
}

non-sealed interface FlexiblePaymentMethod extends PaymentMethod {
}

final class GiftCardPayment implements FlexiblePaymentMethod {
    private final String code;

    GiftCardPayment(String code) {
        this.code = code;
    }

    @Override
    public String description() {
        return "Gift card " + code;
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 SealedInterfaceDemo.java
java SealedInterfaceDemo
~~~

Kết quả stdout chính xác:

~~~text
Cash
Card ****4242
International Card ****7777 (VN)
Gift card GC-01
~~~

## 5. Đọc ví dụ theo từng nhánh

- `PaymentMethod` là sealed interface gốc. Mệnh đề `permits` cho phép đúng ba kiểu con trực tiếp.
- `CardPayment` là sealed class, nên nó tạo một tầng giới hạn tiếp theo. Nó chỉ cho phép `InternationalCardPayment` kế thừa trực tiếp.
- `InternationalCardPayment` khai báo `final`, vì vậy nhánh thẻ quốc tế dừng ở class này.
- `CashPayment` là record. Record tự động final và đáp ứng yêu cầu của một kiểu con trực tiếp.
- `FlexiblePaymentMethod` khai báo `non-sealed`. Nó vẫn phải nằm trong danh sách `permits` của `PaymentMethod`, nhưng nhánh của nó được mở. `GiftCardPayment` chỉ cần triển khai `FlexiblePaymentMethod`; không cần được thêm vào `permits` của `PaymentMethod`.
- Vòng lặp gọi cùng một phương thức `description()` thông qua kiểu tham chiếu `PaymentMethod`. Tính đa hình vẫn hoạt động bình thường; sealed không thay thế hay loại bỏ cơ chế đa hình của Java.

Có thể hình dung hệ phân cấp như sau:

~~~text
PaymentMethod (sealed)
├── CardPayment (sealed)
│   └── InternationalCardPayment (final)
├── CashPayment (record, implicitly final)
└── FlexiblePaymentMethod (non-sealed)
    └── GiftCardPayment (final trong ví dụ)
~~~

Sơ đồ cho thấy `permits` quản lý những cạnh trực tiếp từ một sealed type. `PaymentMethod` không cần liệt kê `InternationalCardPayment` hay `GiftCardPayment`, vì chúng không trực tiếp kế thừa hoặc triển khai `PaymentMethod`.

## 6. Khi nào nên dùng `sealed` và `non-sealed`?

Dùng `sealed` khi miền dữ liệu có các biến thể được xác định rõ, chẳng hạn trạng thái giao dịch, loại kết quả hoặc các loại thông báo trong một thư viện. Danh sách kiểu con trực tiếp giúp người đọc và trình biên dịch hiểu những nhánh hợp lệ.

Dùng `non-sealed` khi muốn giữ một nhánh mở cho phần mở rộng, ví dụ cho phép ứng dụng thêm nhiều loại phương thức thanh toán riêng. Hãy mở đúng nhánh cần mở; nếu mọi nhánh đều `non-sealed`, lợi ích giới hạn của sealed type sẽ giảm đáng kể.

Dùng `final` cho class con khi muốn khóa nhánh tại đó. Nếu cần thêm một tầng kiểm soát, dùng `sealed` và khai báo danh sách con của tầng đó. Các lựa chọn này diễn đạt chính sách mở rộng ngay trong mã nguồn, giúp API rõ ràng hơn.

## 7. Liên hệ với pattern matching và `switch`

Một sealed hierarchy cho trình biên dịch biết các nhánh trực tiếp có thể xuất hiện. Điều này có thể hỗ trợ kiểm tra tính đầy đủ khi dùng pattern matching trong `switch`. Ví dụ với Java 21 trở lên, có thể xử lý các nhánh của `PaymentMethod` bằng các type pattern tương ứng với `CardPayment`, `CashPayment` và `FlexiblePaymentMethod`; pattern `CardPayment` cũng bao phủ các kiểu con của nó.

Pattern matching for `switch` đã được hoàn thiện trong Java 21. Trong Java 17, tính năng này vẫn ở giai đoạn preview và cần tùy chọn preview tương ứng. Ví dụ chạy được ở phần trên không dùng pattern matching for `switch`, vì vậy chỉ cần Java 17 thông thường.

Sealed không tự làm một chuỗi `if/else` trở nên exhaustive và cũng không tự đảm bảo mọi nhánh nghiệp vụ đã được xử lý. Lợi ích kiểm tra tính đầy đủ phụ thuộc vào cấu trúc biểu thức và phiên bản Java đang dùng.

## 8. Lỗi thường gặp

**Quên modifier ở kiểu con trực tiếp.** Một class thông thường triển khai sealed interface mà không khai báo `final`, `sealed` hoặc `non-sealed` sẽ không biên dịch được. Hãy chọn chính sách mở rộng rõ ràng.

**Dùng `final` cho interface.** Interface không dùng modifier `final`; chọn `sealed` nếu muốn tiếp tục giới hạn các interface con hoặc `non-sealed` nếu muốn mở nhánh.

**Liệt kê nhầm kiểu con gián tiếp trong `permits`.** `permits` chỉ chứa kiểu kế thừa/triển khai trực tiếp. Hãy thêm kiểu trung gian vào danh sách và quản lý nhánh sâu hơn tại kiểu trung gian đó.

**Mở nhánh nhưng vẫn kỳ vọng giới hạn toàn bộ cây.** Một kiểu `non-sealed` chủ động cho phép phần mở rộng phía dưới. Nếu cần kiểm soát toàn bộ các kiểu con ở nhánh đó, cân nhắc `sealed` thay vì `non-sealed`.

**Đặt các kiểu con trực tiếp sai package/module.** Kiểm tra ranh giới package khi dùng unnamed module hoặc ranh giới module khi dùng named module.

**Biên dịch bằng phiên bản Java cũ.** Sealed type yêu cầu Java 17 để dùng như tính năng chính thức. Kiểm tra JDK và tùy chọn `--release` của dự án.

## 9. Checklist thiết kế

- Xác định rõ những kiểu nào được phép kế thừa/triển khai trực tiếp interface gốc.
- Với từng kiểu con trực tiếp, chọn `final`, `sealed` hay `non-sealed` theo chính sách mở rộng mong muốn.
- Nếu chọn `sealed` ở tầng giữa, khai báo `permits` cho tầng đó.
- Chỉ mở nhánh bằng `non-sealed` khi thực sự cần cho phép mở rộng bên ngoài.
- Giữ các kiểu con trực tiếp trong cùng module hoặc package theo quy tắc của Java.
- Biên dịch ví dụ với đúng phiên bản Java mục tiêu và kiểm tra cả lỗi biên dịch lẫn kết quả chạy.

Thiết kế tốt thường dùng sealed để mô tả các biến thể hợp lệ ở ranh giới API, rồi chỉ mở những nhánh có nhu cầu mở rộng thực tế. Cách này cân bằng giữa kiểm soát hệ phân cấp và tính linh hoạt của ứng dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sealed-hierarchies-007">Lớp trừu tượng vừa abstract vừa sealed hoặc non-sealed</a>

<details>
<summary>Xem chi tiết</summary>

# Lớp trừu tượng vừa `abstract` vừa `sealed` hoặc `non-sealed`

Trong Java, `abstract` và `sealed` / `non-sealed` giải quyết hai vấn đề khác nhau. `abstract` quy định lớp có thể được khởi tạo trực tiếp hay không và liệu lớp có thể để lại phương thức chưa triển khai; `sealed` kiểm soát những lớp nào được phép kế thừa trực tiếp; `non-sealed` mở lại nhánh kế thừa vốn nằm dưới một kiểu `sealed`. Vì vậy, một lớp có thể vừa `abstract` vừa `sealed`, hoặc vừa `abstract` vừa `non-sealed`.

Các ví dụ trong bài sử dụng Java 17 trở lên, là phiên bản mà sealed classes được chuẩn hóa.

## 1. Ý nghĩa của từng modifier

- **`abstract`:** không thể dùng `new` để tạo trực tiếp đối tượng từ lớp đó. Lớp có thể khai báo phương thức abstract và để lớp con triển khai. Một lớp abstract vẫn có thể có trường, constructor, phương thức có thân và logic dùng chung.
- **`sealed`:** giới hạn các lớp con trực tiếp vào danh sách được cho phép. Danh sách này thường được viết bằng mệnh đề `permits`. Mỗi lớp con được phép phải khai báo một trong các modifier `final`, `sealed` hoặc `non-sealed`.
- **`non-sealed`:** áp dụng cho một lớp con trực tiếp của kiểu `sealed` để cho phép nhánh đó tiếp tục được mở rộng mà không cần liệt kê trước mọi hậu duệ.
- **`final`:** không cho phép lớp có lớp con. Một lớp `final` không thể đồng thời là `abstract` vì hai mục đích mâu thuẫn nhau.

Các modifier này không thay thế lẫn nhau. `abstract` không tự giới hạn cây kế thừa; `sealed` không tự làm lớp thành abstract; `non-sealed` không bắt buộc lớp phải là concrete.

## 2. Kết hợp `abstract sealed`

Khai báo `abstract sealed class Shape permits Circle, Group { ... }` có nghĩa là:

1. Không thể khởi tạo trực tiếp `Shape`.
2. `Shape` có thể chứa phương thức abstract mà các lớp con concrete phải thực hiện.
3. Chỉ những kiểu được liệt kê trong `permits` mới được kế thừa trực tiếp từ `Shape`.
4. Mỗi lớp con trực tiếp phải quyết định nhánh kế thừa của nó bằng `final`, `sealed` hoặc `non-sealed`.

Lớp `abstract sealed` phù hợp khi muốn mô hình hóa một khái niệm nền tảng chưa hoàn chỉnh, đồng thời muốn kiểm soát các kiểu trực tiếp được phép xuất hiện trong hệ thống. Ví dụ, một lớp biểu thức có thể định nghĩa hợp đồng `evaluate()` nhưng chỉ cho phép một số nhánh biểu thức đã biết.

## 3. Kết hợp `abstract non-sealed`

Một khai báo như `abstract non-sealed class PluginNode extends Node { ... }` có nghĩa là lớp này vẫn không thể được khởi tạo trực tiếp và có thể để lại phương thức abstract, nhưng nhánh kế thừa từ nó được mở. Các lớp khác có thể kế thừa `PluginNode` mà không cần được liệt kê trong `permits` của lớp gốc `Node`.

Điều kiện quan trọng: `non-sealed` chỉ hợp lệ khi lớp trực tiếp mở rộng một lớp `sealed` hoặc triển khai một interface `sealed`. Không thể thêm `non-sealed` tùy ý vào một lớp không nằm dưới kiểu sealed.

## 4. Ví dụ hoàn chỉnh: một cây lớp có cả ba loại nhánh

Chương trình sau cho thấy một lớp gốc `abstract sealed`, một nhánh `abstract sealed` tiếp tục giới hạn lớp con, một nhánh `abstract non-sealed` cho phép mở rộng tự do, và một lớp concrete `final`.

Lưu toàn bộ mã trong `Main.java`:

```java
abstract sealed class Node
        permits NumberNode, GroupNode, ExtensionNode {
    abstract int value();
}

final class NumberNode extends Node {
    private final int number;

    NumberNode(int number) {
        this.number = number;
    }

    @Override
    int value() {
        return number;
    }
}

abstract sealed class GroupNode extends Node
        permits PairNode {
    // Có thể giữ lại phương thức abstract value()
    // để lớp con concrete hoàn thành.
}

final class PairNode extends GroupNode {
    private final Node left;
    private final Node right;

    PairNode(Node left, Node right) {
        this.left = left;
        this.right = right;
    }

    @Override
    int value() {
        return left.value() + right.value();
    }
}

abstract non-sealed class ExtensionNode extends Node {
    // Nhánh này được mở cho các lớp con tùy ý.
}

final class CustomNode extends ExtensionNode {
    private final int number;

    CustomNode(int number) {
        this.number = number;
    }

    @Override
    int value() {
        return number;
    }
}

public class Main {
    public static void main(String[] args) {
        Node number = new NumberNode(3);
        Node pair = new PairNode(
                new NumberNode(4),
                new NumberNode(5));
        Node custom = new CustomNode(10);

        System.out.println(number.value());
        System.out.println(pair.value());
        System.out.println(custom.value());
    }
}
```

Biên dịch và chạy:

```text
javac Main.java
java Main
```

Kết quả chuẩn:

```text
3
9
10
```

Trong ví dụ này:

- `Node` là `abstract sealed`: không thể khởi tạo trực tiếp, có hợp đồng `value()`, và chỉ cho phép ba lớp con trực tiếp đã liệt kê.
- `NumberNode` là `final`, nên nhánh này kết thúc.
- `GroupNode` là `abstract sealed`, nên nó được phép trì hoãn việc triển khai `value()` và tiếp tục kiểm soát lớp con thông qua `permits PairNode`.
- `PairNode` là `final` và triển khai `value()`.
- `ExtensionNode` là `abstract non-sealed`: nó vẫn có thể để lại phương thức abstract, nhưng cho phép những lớp như `CustomNode` tiếp tục kế thừa.
- `CustomNode` triển khai `value()` nên có thể được khởi tạo.

## 5. Quy tắc biên dịch cần nhớ

**Quy tắc của `sealed`:**

- Mọi lớp con trực tiếp của một lớp sealed phải thuộc danh sách được phép.
- Lớp con được phép phải trực tiếp kế thừa lớp sealed đó và phải khai báo `final`, `sealed` hoặc `non-sealed`.
- Các kiểu được phép phải đáp ứng quy tắc module/package của Java: trong cùng named module, hoặc cùng package khi dùng unnamed module.
- `permits` nêu các lớp con trực tiếp, không liệt kê toàn bộ hậu duệ. Ví dụ, `Node` liệt kê `ExtensionNode`, còn `CustomNode` là hậu duệ nằm dưới nhánh `non-sealed`.
- Nếu các lớp con được khai báo cùng compilation unit, Java có quy tắc suy ra danh sách permitted trong trường hợp phù hợp; viết `permits` tường minh thường dễ đọc hơn khi học.

**Quy tắc của `abstract`:**

- Không được khởi tạo trực tiếp lớp abstract bằng `new`.
- Lớp abstract có thể giữ các phương thức abstract chưa triển khai. Một lớp con cũng là abstract có thể tiếp tục trì hoãn; lớp concrete phải triển khai đầy đủ các phương thức abstract còn lại.
- Constructor của lớp abstract vẫn chạy khi một lớp con được khởi tạo. Lớp abstract có thể chứa trạng thái và phương thức dùng chung.
- `abstract` không buộc lớp con phải là abstract. Một lớp con `final` hoặc concrete `non-sealed` vẫn hợp lệ nếu nó triển khai đầy đủ hợp đồng.

## 6. Những tổ hợp hợp lệ và không hợp lệ

| Khai báo | Hợp lệ? | Ý nghĩa |
| --- | --- | --- |
| `abstract sealed class A permits B {}` | Có, nếu `B` là lớp con trực tiếp hợp lệ | Không khởi tạo `A`; giới hạn lớp con trực tiếp |
| `abstract non-sealed class B extends A {}` | Có, nếu `A` là sealed | Không khởi tạo `B`; mở rộng nhánh kế thừa |
| `sealed class A permits B {}` | Có, nếu khai báo các lớp con đúng quy tắc | Lớp có thể concrete hoặc abstract |
| `abstract final class A {}` | Không | Abstract cần có khả năng được kế thừa để hoàn thiện, còn final cấm kế thừa |
| `non-sealed class B {}` khi không có sealed parent | Không | `non-sealed` chỉ mở một nhánh xuất phát từ kiểu sealed |
| `sealed non-sealed class A {}` | Không | Hai modifier điều khiển trạng thái sealed đối nghịch nhau |

## 7. Chọn tổ hợp nào trong thiết kế?

Dùng `abstract sealed` khi lớp đại diện cho một khái niệm chưa hoàn chỉnh và bạn cần kiểm soát rõ các loại lớp có thể kế thừa trực tiếp. Cách này hữu ích trong mô hình miền nghiệp vụ, cây cú pháp, biểu thức, thông điệp hoặc các loại sự kiện có tập biến thể được kiểm soát.

Dùng `abstract non-sealed` khi một nhánh cần giữ hợp đồng abstract và hành vi chung, nhưng muốn cho module khác hoặc mã mở rộng tự định nghĩa thêm lớp con. Hãy cân nhắc ranh giới API: mở nhánh `non-sealed` đồng nghĩa với việc mã sử dụng không thể giả định rằng toàn bộ hậu duệ của nhánh đó đã được liệt kê.

Nếu không cần kiểm soát tập lớp con, một lớp abstract thông thường thường đơn giản hơn. Nếu cần một tập phân cấp đóng, hãy dùng `sealed` và quyết định từng nhánh nên kết thúc bằng `final`, tiếp tục kiểm soát bằng `sealed`, hay mở bằng `non-sealed`.

Khi đọc một khai báo như `abstract sealed` hoặc `abstract non-sealed`, hãy trả lời hai câu hỏi riêng: **lớp có thể được khởi tạo trực tiếp và đã triển khai đầy đủ hành vi chưa?** (`abstract`) và **ai được phép kế thừa lớp này hoặc tiếp tục mở rộng nhánh?** (`sealed` / `non-sealed`). Hai câu trả lời kết hợp sẽ xác định đúng vai trò của lớp trong hệ phân cấp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sealed-hierarchies-008">Nhánh mở rộng được phép và giới hạn của anonymous class, lambda</a>

<details>
<summary>Xem chi tiết</summary>

# Nhánh mở rộng được phép và giới hạn của anonymous class, lambda

## Mục tiêu

Trong Java, `sealed` cho phép một class hoặc interface kiểm soát những kiểu nào được kế thừa hoặc triển khai trực tiếp. Anonymous class và lambda đều giúp tạo hành vi tại chỗ, nhưng chúng không thể bỏ qua các quy tắc của một hierarchy `sealed`.

Sau bài này, bạn sẽ phân biệt được:
- kiểu con trực tiếp được khai báo trong `permits` với kiểu con gián tiếp;
- giới hạn của anonymous class khi mở rộng một kiểu `sealed`;
- vì sao lambda không thể triển khai trực tiếp một sealed interface;
- cách mở một nhánh mở rộng có chủ đích bằng `non-sealed`.

## 1. Trực tiếp và gián tiếp quyết định khả năng mở rộng

~~~java
sealed interface Payment permits CardPayment, ExternalPayment {}

final class CardPayment implements Payment {}

non-sealed class ExternalPayment implements Payment {}
~~~

`Payment` chỉ cho phép hai kiểu triển khai trực tiếp được đặt tên. `CardPayment` đóng nhánh, còn `ExternalPayment` mở nhánh. Một class nằm dưới `ExternalPayment` là hậu duệ **gián tiếp** của `Payment`, không cần được liệt kê lại trong `permits`. Anonymous class có thể mở rộng `ExternalPayment`; ngược lại, nó không thể tự tạo một kiểu con trực tiếp mới cho `Payment`. Lambda cũng tuân thủ ranh giới của kiểu mục tiêu, nhưng có quy tắc riêng về functional interface được giải thích phía dưới.

## 2. Giới hạn của anonymous class

Anonymous class là một lớp không có tên được khai báo ngay tại biểu thức tạo đối tượng. Ví dụ thông thường:

~~~java
Runnable task = new Runnable() {
    @Override
    public void run() {
        System.out.println("running");
    }
};
~~~

Cách viết này hợp lệ vì `Runnable` là interface không bị sealed. Tuy nhiên, anonymous class không thể trở thành kiểu con trực tiếp của một class hoặc interface `sealed`. Kiểu con trực tiếp phải là kiểu có tên, được phép trong hierarchy; anonymous class không thể được nêu như một kiểu cụ thể trong mệnh đề `permits`.

Ví dụ sau **không hợp lệ**:

~~~java
sealed interface Command permits StartCommand {
}

final class StartCommand implements Command {
}

class Main {
    public static void main(String[] args) {
        Command command = new Command() {
            // Anonymous class đang cố triển khai Command trực tiếp.
        };
    }
}
~~~

Trình biên dịch từ chối anonymous class vì kiểu mới tạo không phải là một kiểu con trực tiếp được cho phép. Thêm một đối tượng vào danh sách `permits` cũng không giải quyết được vấn đề: `permits` liệt kê kiểu, không liệt kê đối tượng, và anonymous class không có tên nguồn để khai báo ở đó.

Anonymous class vẫn có thể mở rộng hoặc triển khai một kiểu **không bị sealed**, kể cả khi kiểu đó là một nhánh mở rộng của sealed hierarchy. Đây là cách mở rộng có chủ đích thay vì phá vỡ ranh giới đã định nghĩa.

## 3. Vì sao lambda có giới hạn khác?

Lambda expression không phải là cú pháp tạo một lớp con tùy ý. Lambda cung cấp phần triển khai cho một **functional interface** — interface có một phương thức trừu tượng phù hợp làm hợp đồng chức năng.

Ví dụ:

~~~java
@FunctionalInterface
interface Handler {
    void handle();
}

class Main {
    public static void main(String[] args) {
        Handler handler = () -> System.out.println("handled");
        handler.handle();
    }
}
~~~

Lambda ở đây triển khai hợp đồng `handle()` của `Handler`. Lambda không kế thừa một class, cũng không cung cấp một tên kiểu con để ghi vào `permits`.

Một sealed interface không thể làm functional interface mục tiêu cho lambda: định nghĩa functional interface của Java loại trừ interface `sealed`. Do đó, không thể dùng lambda để tạo một kiểu triển khai trực tiếp cho sealed interface. Nếu cố gán lambda cho sealed interface, chương trình sẽ không biên dịch.

Điều này không có nghĩa là lambda bị cấm hoàn toàn trong sealed hierarchy. Lambda có thể nhắm tới một functional interface `non-sealed` nằm trong một nhánh được cho phép. Khi đó, kiểu trực tiếp nằm dưới sealed interface là interface `non-sealed` có tên; lambda chỉ cung cấp hành vi cho interface mở rộng ấy.

## 4. Mở một nhánh hợp lệ cho anonymous class và lambda

Ví dụ hoàn chỉnh sau minh họa cách thiết kế đó. Lưu toàn bộ mã vào `Main.java`:

~~~java
sealed interface Job permits BuiltInJob, ExtensibleJob {
}

record BuiltInJob(String name) implements Job {
}

@FunctionalInterface
non-sealed interface ExtensibleJob extends Job {
    void execute();
}

public class Main {
    public static void main(String[] args) {
        ExtensibleJob lambdaJob =
                () -> System.out.println("lambda job");

        ExtensibleJob anonymousJob = new ExtensibleJob() {
            @Override
            public void execute() {
                System.out.println("anonymous job");
            }
        };

        lambdaJob.execute();
        anonymousJob.execute();

        Job builtIn = new BuiltInJob("report");
        System.out.println(builtIn instanceof BuiltInJob);
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~text
javac Main.java
java Main
~~~

Đầu ra chuẩn chính xác:

~~~text
lambda job
anonymous job
true
~~~

Phân tích ví dụ:

1. `Job` là sealed interface. Hai kiểu con trực tiếp được phép là `BuiltInJob` và `ExtensibleJob`.
2. `BuiltInJob` là record nên tự động là kiểu `final`; nó không mở rộng hierarchy thêm nữa.
3. `ExtensibleJob` được khai báo `non-sealed`, vì vậy các kiểu khác có thể triển khai interface này. Nó có đúng một phương thức trừu tượng là `execute()`, nên có thể dùng làm mục tiêu cho lambda.
4. `lambdaJob` cung cấp thân hàm bằng lambda. `anonymousJob` dùng anonymous class để triển khai cùng interface mở rộng. Cả hai đối tượng đều là `Job` theo quan hệ kiểu gián tiếp thông qua `ExtensibleJob`.
5. Biểu thức `builtIn instanceof BuiltInJob` in ra `true`, vì đối tượng được tạo là một `BuiltInJob`.

Thiết kế này giữ được một ranh giới rõ: sealed interface chỉ cho phép hai nhánh trực tiếp đã định danh, nhưng một nhánh `non-sealed` có thể cho phép các cách triển khai linh hoạt như lambda và anonymous class.

## 5. So sánh nhanh

| Trường hợp | Hợp lệ? | Lý do |
|---|---|---|
| Anonymous class trực tiếp triển khai sealed interface | Không | Không thể khai báo anonymous class làm kiểu con trực tiếp được phép |
| Anonymous class triển khai interface `non-sealed` trong hierarchy | Có | Kiểu trực tiếp của anonymous class là nhánh mở đã được cho phép |
| Lambda nhắm tới sealed interface | Không | Sealed interface không thể là functional interface mục tiêu |
| Lambda nhắm tới functional interface `non-sealed` trong hierarchy | Có | Functional interface mở có thể nhận phần triển khai từ lambda |
| Kiểu `final` được khai báo trong `permits` | Có | Kiểu con trực tiếp có tên và đóng nhánh kế thừa |
| Kiểu `non-sealed` được khai báo trong `permits` | Có | Kiểu con trực tiếp có tên và mở các nhánh con tiếp theo |

## 6. Lỗi thường gặp và cách xử lý

**Cố đưa anonymous class vào `permits`.** Mệnh đề này khai báo các kiểu con trực tiếp bằng tên kiểu; nó không nhận biểu thức tạo đối tượng. Hãy khai báo một class hoặc interface có tên làm nhánh trực tiếp nếu cần thêm một nhánh.

**Cho rằng lambda có thể triển khai bất kỳ interface nào.** Lambda chỉ tương thích với functional interface. Một sealed interface không thể đóng vai trò functional interface mục tiêu; hãy xác định xem nhánh có thực sự cần bị đóng hay cần được mở.

**Đánh dấu nhánh trực tiếp là `final` rồi mong anonymous class mở rộng nhánh đó.** `final` cấm mọi kiểu con. Nếu thiết kế cần cho phép các triển khai tùy biến, hãy cân nhắc một kiểu con trực tiếp `non-sealed` với tên rõ ràng.

**Dùng `non-sealed` mà không cân nhắc hệ quả.** Từ khi một nhánh được mở, mã khác có thể bổ sung kiểu con mà sealed parent không liệt kê trực tiếp. Chỉ mở nhánh khi đây là phần mở rộng mong muốn của API.

**Nhầm sealed với việc cấm mọi kiểu con.** Sealed không loại bỏ kế thừa; nó kiểm soát các kiểu con trực tiếp. Các nhánh `non-sealed` có thể tiếp tục mở rộng, còn `final` thì đóng và `sealed` thì tiếp tục kiểm soát.

## 7. Quy trình quyết định

1. Xác định kiểu nào là sealed parent và đọc danh sách `permits`.
2. Với mỗi kiểu con trực tiếp, kiểm tra rằng kiểu đó có tên và được khai báo `final`, `sealed` hoặc `non-sealed` theo đúng thiết kế.
3. Nếu cần lambda, kiểm tra kiểu mục tiêu có phải functional interface và không bị sealed hay không.
4. Nếu cần anonymous class, kiểm tra kiểu mà anonymous class trực tiếp mở rộng hoặc triển khai có phải nhánh mở hay không.
5. Khi cần mở rộng sealed hierarchy, tạo một nhánh trực tiếp có tên và đánh dấu `non-sealed` có chủ đích.
6. Biên dịch bằng JDK 17+ để phát hiện vi phạm quy tắc sealed; sau đó chạy chương trình và đối chiếu stdout.

## Ghi nhớ

Anonymous class và lambda không thể vượt qua ranh giới của sealed hierarchy. Anonymous class không thể là kiểu con trực tiếp của một sealed type; lambda không thể nhắm tới sealed interface vì sealed interface không phải functional interface. Cách mở rộng hợp lệ là khai báo một kiểu con trực tiếp có tên, cho phép kiểu đó là `non-sealed` khi thực sự muốn hỗ trợ thêm các triển khai, rồi dùng anonymous class hoặc lambda với nhánh mở ấy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sealed-hierarchies-009">Pattern matching switch và tính đầy đủ của phân cấp đóng</a>

<details>
<summary>Xem chi tiết</summary>

# Pattern matching switch và tính đầy đủ của phân cấp đóng

Java 21 trở lên cho phép dùng **pattern matching trong `switch`** để kiểm tra kiểu và lấy dữ liệu từ đối tượng ngay trong từng nhánh. Khi kiểu được xét là một **sealed hierarchy** (phân cấp lớp/interface đóng), trình biên dịch có thể kiểm tra xem các nhánh đã bao phủ mọi kiểu hợp lệ trong phân cấp hay chưa. Điều này giúp viết biểu thức `switch` an toàn hơn và làm lộ ra những trường hợp cần xử lý khi mô hình dữ liệu thay đổi.

Hai ý tưởng phối hợp với nhau:

- **Pattern matching switch** chọn nhánh dựa trên kiểu hoặc cấu trúc của giá trị, có thể gắn tên cho phần dữ liệu đã khớp.
- **Sealed type** giới hạn những lớp hoặc interface nào được phép trực tiếp mở rộng hoặc triển khai kiểu cha. Các kiểu con được khai báo trong `permits`, hoặc được suy ra khi chúng nằm cùng tệp nguồn theo quy tắc của Java.

## 1. Pattern matching trong switch

Với `switch` truyền thống, các nhãn thường là hằng số như `case 1`, giá trị enum hoặc chuỗi. Pattern matching cho phép nhãn mô tả kiểu của đối tượng:

~~~java
static String describe(Object value) {
    return switch (value) {
        case String text -> "Chuỗi dài " + text.length();
        case Integer number -> "Số nguyên " + number;
        case null -> "Giá trị null";
        default -> "Kiểu khác";
    };
}
~~~

Khi giá trị là `String`, nhánh đầu khớp và biến mẫu `text` có kiểu `String` bên trong nhánh đó. Tương tự, nhánh `Integer` cung cấp biến `number`. Không cần tự kiểm tra bằng `instanceof` rồi ép kiểu thủ công.

Ví dụ trên là đoạn minh họa, không phải chương trình độc lập. Pattern matching switch được hoàn thiện trong **Java 21**. Nếu dùng phiên bản Java cũ hơn, cú pháp này có thể chưa được hỗ trợ hoặc cần cờ preview ở các phiên bản preview trước đó.

### Biểu thức switch và câu lệnh switch

Biểu thức `switch` tạo ra một giá trị và thường được dùng trong phép gán hoặc câu lệnh `return`:

~~~java
String label = switch (status) {
    case ACTIVE -> "Đang hoạt động";
    case INACTIVE -> "Đã dừng";
};
~~~

Câu lệnh `switch` thực hiện hành động trong từng nhánh. Java yêu cầu biểu thức `switch` phải đầy đủ (exhaustive), tức mọi giá trị có thể có đều phải khớp với một nhánh. Câu lệnh `switch` dùng pattern labels cũng phải đầy đủ. Với các dạng `switch` truyền thống khác, việc có cần nhánh mặc định hay không phụ thuộc vào loại switch và mục đích xử lý.

## 2. Sealed hierarchy đóng những kiểu nào?

Một kiểu được khai báo `sealed` giới hạn các kiểu con trực tiếp được phép kế thừa hoặc triển khai. Ví dụ:

~~~java
sealed interface Shape permits Circle, Rectangle, Triangle {}

record Circle(double radius) implements Shape {}
record Rectangle(double width, double height) implements Shape {}
record Triangle(double base, double height) implements Shape {}
~~~

Đây là cấu trúc minh họa. Trong cùng một tệp Java, các kiểu con có thể được khai báo cạnh kiểu cha. Trong chương trình thực tế, các kiểu này cũng có thể được tổ chức thành những tệp riêng theo quy tắc của sealed types.

Một kiểu con trực tiếp được phép của sealed class/interface phải khai báo một trong ba trạng thái:

- `final`: không cho phép kế thừa tiếp.
- `sealed`: tiếp tục giới hạn danh sách kiểu con trực tiếp của nó.
- `non-sealed`: mở lại nhánh đó để các kiểu khác có thể kế thừa hoặc triển khai.

Các record mặc định là `final`, vì vậy chúng phù hợp làm những nhánh lá của phân cấp đóng. Nếu một nhánh con là `sealed`, trình biên dịch có thể tiếp tục xét phân cấp con của nhánh đó. Nếu nhánh là `non-sealed`, có thể có những kiểu con mới mà mã nguồn hiện tại chưa liệt kê; một pattern khớp chính kiểu nhánh đó có thể bao phủ toàn bộ các kiểu con của nó.

Sealed hierarchy tạo ra một tập kiểu có thể phân tích được ở thời điểm biên dịch. Đây là nền tảng để trình biên dịch xác nhận tính đầy đủ của pattern switch.

## 3. Tính đầy đủ của switch trên sealed hierarchy

Giả sử `Shape` chỉ cho phép ba kiểu con trực tiếp là `Circle`, `Rectangle` và `Triangle`. Khi xử lý cả ba kiểu trong một biểu thức switch, có thể không cần `default`:

~~~java
static double area(Shape shape) {
    return switch (shape) {
        case Circle c -> Math.PI * c.radius() * c.radius();
        case Rectangle r -> r.width() * r.height();
        case Triangle t -> rBaseArea(t.base(), t.height());
    };
}

static double rBaseArea(double base, double height) {
    return base * height / 2.0;
}
~~~

Mỗi nhánh xử lý một kiểu con được cho phép, nên biểu thức switch đầy đủ. Trong ví dụ này, `rBaseArea` chỉ tách phép tính diện tích tam giác thành một hàm nhỏ để làm rõ cách viết; chương trình hoàn chỉnh bên dưới dùng trực tiếp công thức đó.

Nếu bỏ nhánh `Triangle`, trình biên dịch báo lỗi vì không thể chứng minh rằng biểu thức switch trả về giá trị cho mọi trường hợp của `Shape`. Lỗi được phát hiện khi biên dịch thay vì chờ đến khi dữ liệu có kiểu còn thiếu xuất hiện trong production.

### Trình biên dịch xác định tính đầy đủ ra sao?

Với một sealed type, trình biên dịch xét các kiểu con được phép:

1. Nếu một kiểu con là `final`, nó là một nhánh đóng.
2. Nếu một kiểu con tiếp tục là `sealed`, trình biên dịch có thể xét các kiểu con được phép của nó.
3. Nếu một kiểu con là `non-sealed`, phân cấp mở tại nhánh đó. Pattern khớp kiểu của nhánh có thể bao phủ các lớp con của nó; nếu chỉ liệt kê một số lớp con đã biết thì chưa đủ để chứng minh tính đầy đủ.
4. Mỗi giá trị có thể có của kiểu selector phải được bao phủ bởi ít nhất một nhãn phù hợp.

Một pattern kiểu cha có thể bao phủ cả các kiểu con của nó. Ví dụ, nhánh `case Shape s` khớp mọi đối tượng không null có kiểu tương thích với `Shape`; tuy nhiên, đặt nhánh rộng như vậy trước các nhánh `Circle`, `Rectangle` hoặc `Triangle` sẽ khiến các nhánh sau trở nên không thể đạt tới và gây lỗi dominance.

## 4. Ví dụ hoàn chỉnh với Java 21+

Ví dụ dưới đây sử dụng sealed interface, record, record patterns, guard `when` và switch expression. Ba record đại diện cho ba hình; cả hai phương thức xử lý đều bao phủ đầy đủ phân cấp mà không cần `default`.

Lưu toàn bộ nội dung vào tệp `SealedSwitchDemo.java`:

~~~java
import java.util.List;
import java.util.Locale;

public class SealedSwitchDemo {
    sealed interface Shape permits Circle, Rectangle, Triangle {}

    record Circle(double radius) implements Shape {}
    record Rectangle(double width, double height) implements Shape {}
    record Triangle(double base, double height) implements Shape {}

    static double area(Shape shape) {
        return switch (shape) {
            case Circle(double radius) -> Math.PI * radius * radius;
            case Rectangle(double width, double height) -> width * height;
            case Triangle(double base, double height) -> base * height / 2.0;
        };
    }

    static String kind(Shape shape) {
        return switch (shape) {
            case Circle(double radius) when radius == 0.0 -> "Đường tròn suy biến";
            case Circle(double radius) -> "Đường tròn";
            case Rectangle(double width, double height) when width == height -> "Hình vuông";
            case Rectangle(double width, double height) -> "Hình chữ nhật";
            case Triangle(double base, double height) -> "Tam giác";
        };
    }

    public static void main(String[] args) {
        List<Shape> shapes = List.of(
            new Circle(2.0),
            new Rectangle(3.0, 3.0),
            new Triangle(4.0, 5.0)
        );

        for (Shape shape : shapes) {
            System.out.printf(Locale.ROOT, "%s: %.2f%n", kind(shape), area(shape));
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 21 trở lên:

~~~text
javac SealedSwitchDemo.java
java SealedSwitchDemo
~~~

Kết quả chuẩn:

~~~text
Đường tròn: 12.57
Hình vuông: 9.00
Tam giác: 10.00
~~~

Giải thích:

- `Shape` giới hạn các kiểu con được phép thành ba record.
- Record patterns như `Circle(double radius)` vừa kiểm tra kiểu vừa lấy thành phần `radius` từ record.
- `area` tính diện tích theo kiểu hình và phải xử lý đủ ba kiểu.
- Trong `kind`, nhãn có guard `when` kiểm tra trường hợp đặc biệt trước. Nhãn `Circle(double radius)` không có guard tiếp theo sẽ xử lý các hình tròn còn lại. Tương tự, hình chữ nhật có chiều rộng bằng chiều cao được mô tả là hình vuông trước khi xét hình chữ nhật thông thường.
- Các kiểu lồng trong lớp công khai có thể nằm chung một tệp; tên tệp phải trùng với lớp `public SealedSwitchDemo`.

## 5. Guard, dominance và thứ tự nhãn

Guard `when` thêm điều kiện vào một pattern. Nó hữu ích khi kiểu đã khớp nhưng hành vi cần phân biệt theo dữ liệu:

~~~java
return switch (shape) {
    case Rectangle(double w, double h) when w == h -> "Hình vuông";
    case Rectangle(double w, double h) -> "Hình chữ nhật";
    case Circle(double r) -> "Hình tròn";
    case Triangle(double b, double h) -> "Tam giác";
};
~~~

Đoạn trên giả định `shape` có kiểu `Shape` như trong ví dụ. Nhánh hình chữ nhật có guard phải đứng trước nhánh tổng quát cho `Rectangle`; nếu đảo thứ tự, nhánh tổng quát sẽ bắt mọi hình chữ nhật và nhánh có guard không bao giờ được chạy.

**Dominance** là tình huống một nhãn đứng trước đã bao phủ tất cả giá trị mà nhãn phía sau có thể khớp. Ví dụ, một nhãn kiểu `Shape` không có guard đứng trước các nhãn kiểu con sẽ khiến các nhãn con bị thống trị. Trình biên dịch phát hiện trường hợp này để tránh nhánh chết.

Các nguyên tắc thực hành:

- Đặt nhánh hẹp hoặc có điều kiện cụ thể trước nhánh rộng.
- Sau một nhánh có guard, thường cần một nhánh không có guard để xử lý những giá trị còn lại của cùng kiểu.
- Không thêm `default` chỉ để che lỗi thiếu nhánh nếu mục tiêu là để compiler phát hiện phân cấp sealed đã thay đổi.
- Chỉ dùng guard cho điều kiện có ý nghĩa nghiệp vụ; tránh sao chép logic phức tạp vào nhiều nhãn.

## 6. Giá trị null và nhánh default

Tính đầy đủ của sealed hierarchy mô tả những đối tượng thuộc các kiểu trong phân cấp. Nó không tự động có nghĩa là `null` được xử lý. Với pattern switch, nếu selector là `null` và không có nhãn `case null`, switch sẽ ném `NullPointerException`.

Nếu `null` là một đầu vào hợp lệ, hãy xử lý tường minh:

~~~java
static String describe(Shape shape) {
    return switch (shape) {
        case null -> "Không có hình";
        case Circle(double radius) -> "Hình tròn";
        case Rectangle(double width, double height) -> "Hình chữ nhật";
        case Triangle(double base, double height) -> "Tam giác";
    };
}
~~~

Ở đây, `case null` xử lý riêng giá trị null; các nhãn còn lại vẫn bao phủ đầy đủ những kiểu con của `Shape`.

`default` hữu ích khi cần một nhánh dự phòng có chủ ý, chẳng hạn xử lý kiểu chưa biết trong một API mở. Nhưng với sealed hierarchy, một nhánh `default` có thể làm trình biên dịch không còn báo thiếu kiểu mới khi danh sách `permits` thay đổi. Nếu muốn tận dụng tính an toàn khi phân cấp phát triển, nên liệt kê các nhánh cụ thể và để compiler kiểm tra.

Nếu các thành phần được biên dịch riêng rồi phiên bản sealed hierarchy thay đổi mà mã switch chưa được biên dịch lại, giả định về tính đầy đủ có thể không còn đúng ở runtime. Với switch expression, trường hợp không nhánh nào khớp có thể dẫn đến `MatchException`. Nên biên dịch lại các module liên quan khi thay đổi danh sách kiểu con được phép.

## 7. Khi nào nên dùng và những lỗi thường gặp

Dùng pattern matching switch kết hợp sealed hierarchy khi:

- Một kiểu cha đại diện cho một tập trường hợp nghiệp vụ hữu hạn, chẳng hạn trạng thái đơn hàng, biểu thức trong AST, thông điệp giao thức hoặc hình học.
- Mỗi kiểu con cần một nhánh xử lý riêng.
- Muốn thay đổi mô hình dữ liệu khiến compiler chỉ ra các vị trí cần cập nhật.
- Cần trích xuất thành phần của record mà không viết nhiều câu lệnh `instanceof` và ép kiểu.

Những lỗi thường gặp:

1. **Bỏ sót kiểu con:** biểu thức switch không đầy đủ sẽ không biên dịch. Thêm nhánh còn thiếu hoặc một nhánh dự phòng có chủ ý.
2. **Dùng `default` quá sớm:** code vẫn biên dịch khi kiểu con mới được thêm, nhưng logic nghiệp vụ có thể chưa được cập nhật. Ưu tiên liệt kê các kiểu con đã biết khi phân cấp thực sự đóng.
3. **Nhầm sealed với final:** `sealed` không có nghĩa là không có lớp con; nó có nghĩa là các kiểu con trực tiếp được kiểm soát bởi danh sách cho phép. Kiểu con có thể là `sealed`, `final` hoặc `non-sealed`.
4. **Quên nhánh mở rộng `non-sealed`:** nếu chỉ liệt kê các lớp con đã biết của một nhánh mở, những kiểu mới có thể không được bao phủ. Dùng pattern cho chính kiểu nhánh mở hoặc default khi có lý do.
5. **Nhầm null với một kiểu con:** null không khớp với pattern kiểu. Nếu cần hỗ trợ null, thêm `case null` và quyết định rõ hành vi.
6. **Sắp xếp nhãn sai:** nhánh tổng quát có thể thống trị nhánh cụ thể phía sau. Đặt pattern cụ thể và guard trước pattern rộng.
7. **Biên dịch bằng JDK quá cũ:** ví dụ dùng record patterns và pattern matching switch cần JDK 21 trở lên. Kiểm tra `java -version` và `javac -version` để chắc chắn compiler phù hợp.

Khi thêm một kiểu con mới vào sealed hierarchy, hãy tìm những switch đang xử lý kiểu cha, cập nhật các nhánh cần thiết rồi biên dịch và chạy lại các kiểm thử. Cách này biến thay đổi mô hình dữ liệu thành lỗi biên dịch có thể phát hiện sớm, thay vì để một trường hợp mới âm thầm rơi vào logic không phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)
