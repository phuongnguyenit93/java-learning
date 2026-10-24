<a id="back-to-top"></a>

# Phương thức default

## Menu
- [Động cơ bổ sung default method từ Java 8](#default-methods-001)
- [Cú pháp và thân phương thức default](#default-methods-002)
- [Gọi phương thức abstract từ phương thức default](#default-methods-003)
- [Ghi đè default method trong lớp triển khai](#default-methods-004)
- [Kế thừa default method qua interface con](#default-methods-005)
- [Biến default method thành yêu cầu abstract ở interface con](#default-methods-006)
- [Gọi default method bằng InterfaceName.super](#default-methods-007)
- [Giới hạn gọi super qua interface trực tiếp](#default-methods-008)
- [Giới hạn chia sẻ trạng thái và hợp đồng của default method](#default-methods-009)

## <a id="default-methods-001">Động cơ bổ sung default method từ Java 8</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao Java bổ sung default method?

Trước Java 8, một interface chủ yếu mô tả những phương thức mà lớp triển khai phải cung cấp. Khi thư viện muốn bổ sung một thao tác vào interface công khai đã được nhiều dự án triển khai, thêm phương thức abstract mới thường buộc các lớp cụ thể phải được sửa và biên dịch lại. Với một API phổ biến, số lượng implementer có thể lớn hơn nhiều so với khả năng kiểm soát của tác giả thư viện.

## 1. Bài toán tiến hóa API

Giả sử phiên bản đầu của thư viện có hợp đồng:

```java
interface ReportExporter {
    String export(String content);
}
```

Các lớp như `CsvExporter` chỉ biết cài đặt `export`. Nếu phiên bản tiếp theo thêm `String exportNormalized(String content);` dưới dạng abstract, lớp cũ sẽ thiếu implementation khi biên dịch lại.

Java 8 bổ sung **default method** để interface có thể cung cấp một hành vi mặc định dùng được ngay:

```java
interface ReportExporter {
    String export(String content);

    default String exportNormalized(String content) {
        return export(content.trim());
    }
}

final class CsvExporter implements ReportExporter {
    @Override
    public String export(String content) {
        return "CSV:" + content;
    }
}
```

`CsvExporter` không cần viết lại `exportNormalized`. Default method chuẩn hóa nội dung rồi gọi phương thức abstract `export`; lời gọi sau được phân phối tới implementation của đối tượng thực tế. Lớp vẫn có thể override nếu quy tắc chuẩn hóa của nó khác.

## 2. Lợi ích với API chuẩn của Java

Tính năng này đặc biệt hữu ích khi Java 8 bổ sung lambda, method reference và Stream API. Các interface được triển khai rộng rãi có thể nhận thao tác mới mà không buộc mọi nhà cung cấp phải chép cùng một logic.

| Interface | Ví dụ default method | Tác dụng |
| --- | --- | --- |
| `Iterable` | `forEach(Consumer)` | Duyệt bằng lambda hoặc method reference. |
| `Collection` | `stream()`, `parallelStream()`, `removeIf(Predicate)` | Xây dựng pipeline và thao tác theo điều kiện. |
| `List` | `replaceAll(UnaryOperator)`, `sort(Comparator)` | Biến đổi hoặc sắp xếp phần tử. |
| `Map` | `getOrDefault`, `forEach`, `computeIfAbsent` | Thao tác tiện ích trên ánh xạ. |

Một implementation chuyên biệt vẫn có thể override để đáp ứng cấu trúc dữ liệu, hiệu năng hoặc cam kết riêng của nó. Default method không bảo đảm thuật toán tổng quát là tối ưu cho mọi lớp.

## 3. Ba loại tương thích cần phân biệt

- **Tương thích mã nguồn:** khi lớp cũ được biên dịch lại, default method thường giúp lớp tiếp tục đáp ứng interface mà không cần thêm phương thức mới.
- **Tương thích nhị phân:** các chương trình đã biên dịch có thể tiếp tục liên kết trong nhiều trường hợp vì JVM có thể chọn implementation mặc định khi được gọi.
- **Tương thích hành vi:** hành vi mới vẫn phải có ý nghĩa với mọi implementation hợp lệ. Một default method dựa vào giả định sai về dữ liệu hoặc trạng thái có thể gây lỗi nghiệp vụ dù chương trình vẫn biên dịch.

Đây là hỗ trợ tương thích, không phải bảo đảm tuyệt đối. Một default method mới có thể va chạm với phương thức đã tồn tại trong class hoặc interface khác. Khi có nhiều default method cạnh tranh, cần xét quy tắc kế thừa và có thể phải override để giải quyết.

## 4. Khi nào nên thêm default method?

Hãy cân nhắc khi hành vi mới có thể được xây dựng từ các thao tác đã nằm trong hợp đồng và mặc định đó hợp lý với mọi implementation. Nếu mỗi lớp phải làm một việc khác nhau, một phương thức abstract có thể diễn đạt yêu cầu rõ hơn. Nếu thuật toán cần trạng thái instance, constructor hoặc một giao dịch atomic, nên cân nhắc lớp cơ sở hay đối tượng thành phần.

Trước khi phát hành API, kiểm tra các implementer tiêu biểu, chữ ký có thể xung đột, tiền điều kiện, ngoại lệ, side effect và đặc tính đồng thời. Mục tiêu là thêm một hành vi dùng chung an toàn, chứ không chỉ làm cho mã nguồn ngắn hơn.

## Tự kiểm tra

1. Vì sao thêm phương thức abstract vào interface phổ biến có thể làm lớp triển khai cũ không biên dịch lại được?
2. Default method giảm gánh nặng đó bằng cách nào?
3. Vì sao một default method mới vẫn có thể gây lỗi tương thích?
4. Khi nào phương thức abstract hoặc một service phù hợp hơn một default method?

**Ghi nhớ:** default method giúp interface tiến hóa cùng API mới trong khi nhiều lớp hiện hữu vẫn có thể dùng hành vi chung. Tính tương thích vẫn phụ thuộc vào quy tắc kế thừa và tính đúng đắn của hợp đồng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="default-methods-002">Cú pháp và thân phương thức default</a>

<details>
<summary>Xem chi tiết</summary>

# Cú pháp và thân phương thức default trong Java interface

Một phương thức `default` là phương thức instance có thân được khai báo **bên trong interface**. Từ Java 8, nó cho phép interface cung cấp hành vi chung; lớp triển khai có thể kế thừa hành vi đó mà không phải tự viết lại.

## 1. Cú pháp và quyền truy cập

```java
interface CounterView {
    int value();

    default boolean isEmpty() {
        return value() == 0;
    }

    default void printStatus() {
        System.out.println(isEmpty() ? "Rỗng" : "Có dữ liệu");
    }
}
```

`value()` là phương thức abstract, còn `isEmpty()` và `printStatus()` có phần thân. Một default method có thể dùng tham số, biến cục bộ, điều kiện, vòng lặp, `return`, `throw` và gọi những phương thức hợp lệ khác như một instance method thông thường.

Default method luôn là **public**, kể cả khi không ghi từ khóa `public`. Khi override trong class, phương thức thay thế cũng phải là `public`. Từ khóa `default` không có nghĩa package-private và không biến phương thức thành `static`.

## 2. Phân biệt các loại phương thức

| Khai báo | Có thân? | Ý nghĩa |
| --- | --- | --- |
| `void run();` | Không | Hợp đồng abstract; class cụ thể cần implementation phù hợp. |
| `default void run() { ... }` | Có | Hành vi instance mặc định; class có thể kế thừa hoặc override. |
| `static void help() { ... }` | Có | Thuộc interface, gọi bằng `InterfaceName.help()`; không override theo đa hình instance. |
| `private void helper() { ... }` | Có, từ Java 9 | Helper nội bộ để các phương thức của interface tái sử dụng logic. |

Default method bắt buộc có thân. Muốn khai báo abstract, hãy bỏ `default` và kết thúc bằng dấu chấm phẩy. Không thể dùng `default` trong một class thông thường.

## 3. Ví dụ chạy được

Lưu vào `Main.java`:

```java
public class Main {
    interface Greeting {
        default String greet(String name) {
            return "Xin chào, " + name;
        }
    }

    static final class BasicGreeter implements Greeting {
        // Kế thừa greet(String).
    }

    static final class FriendlyGreeter implements Greeting {
        @Override
        public String greet(String name) {
            return "Chào bạn, " + name;
        }
    }

    public static void main(String[] args) {
        System.out.println(new BasicGreeter().greet("An"));
        System.out.println(new FriendlyGreeter().greet("An"));
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac Main.java
java Main
```

Kết quả:

```text
Xin chào, An
Chào bạn, An
```

`BasicGreeter` dùng thân mặc định. `FriendlyGreeter` cung cấp một phương thức instance `public` với cùng chữ ký để thay đổi hành vi. Class không viết thêm từ khóa `default`.

## 4. Những lỗi cú pháp thường gặp

```java
interface Invalid {
    default void run();          // Sai: thiếu thân.
    abstract default void a() {} // Sai: abstract và default xung đột.
    static default void b() {}   // Sai: static và default xung đột.
    protected default void c() {} // Sai: default method là public.
}
```

Một phương thức có thân trong interface cũng không được khai báo `default` để thay thế các phương thức public tương ứng của `Object`, chẳng hạn `toString()`, `equals(Object)` hoặc `hashCode()`. Nếu cần cách triển khai riêng, hãy đặt override trong class.

Đừng nhầm `default method` với nhánh `default:` của `switch`, giá trị mặc định của annotation hoặc package-private access. Đây là modifier riêng cho phương thức instance có thân trong interface.

## 5. Cách quyết định

Dùng default method khi có một hành vi chung hợp lý mà các implementation có thể kế thừa. Nếu mọi implementation phải tự xác định chính sách quan trọng, dùng phương thức abstract. Nếu hành vi cần trạng thái riêng, constructor hoặc quản lý tài nguyên, hãy cân nhắc class hoặc composition. Quy tắc chọn giữa các phương thức kế thừa được trình bày ở các bài về override và kế thừa; cú pháp cơ bản ở đây không tự giải quyết mọi xung đột.

## Tự kiểm tra

1. Vì sao `default void run();` không hợp lệ?
2. Default method có phải là phương thức package-private không?
3. Class override default method có cần ghi `default` không?
4. Từ phiên bản Java nào interface có private helper methods?

**Ghi nhớ:** `default ReturnType method(...) { ... }` chỉ dùng cho phương thức instance có thân trong interface. Nó là public và có thể được class triển khai override bằng phương thức instance bình thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="default-methods-003">Gọi phương thức abstract từ phương thức default</a>

<details>
<summary>Xem chi tiết</summary>

# Gọi phương thức abstract từ phương thức default trong Java

Một default method có thể định nghĩa quy trình chung nhưng giao một bước cụ thể cho phương thức abstract mà mỗi lớp triển khai cung cấp. Mẫu này giúp tái sử dụng thuật toán mà không gắn interface với một lớp cụ thể.

## 1. Hợp đồng và điểm tùy biến

```java
interface Greeter {
    String displayName();

    default String greet() {
        return "Xin chào, " + displayName();
    }
}
```

`displayName()` khai báo hợp đồng; `greet()` cung cấp cách chào dùng chung. Default method được phép gọi phương thức abstract vì phương thức đó thuộc kiểu interface. Khi chạy, lời gọi instance được phân phối đến implementation của đối tượng thực tế.

## 2. Theo dõi luồng thực thi

```java
public class Main {
    interface Greeter {
        String displayName();

        default String greet() {
            return "Xin chào, " + displayName();
        }
    }

    static final class Employee implements Greeter {
        private final String name;

        Employee(String name) {
            this.name = name;
        }

        @Override
        public String displayName() {
            return name;
        }
    }

    static final class Guest implements Greeter {
        @Override
        public String displayName() {
            return "bạn";
        }
    }

    public static void main(String[] args) {
        Greeter employee = new Employee("An");
        Greeter guest = new Guest();

        System.out.println(employee.greet());
        System.out.println(guest.greet());
    }
}
```

Kết quả:

```text
Xin chào, An
Xin chào, bạn
```

Khi gọi `employee.greet()`, Java dùng thân default của `Greeter` vì `Employee` không override `greet`. Bên trong thân đó, `displayName()` chạy trên đối tượng hiện tại nên kết quả đến từ `Employee.displayName()`. Đối tượng `Guest` đi qua cùng quy trình chung nhưng trả tên khác.

Trình biên dịch kiểm tra rằng `Greeter` khai báo `displayName()`. JVM thực thi implementation phù hợp với đối tượng thật khi chạy. `this` bên trong default method tham chiếu đến đối tượng triển khai đang nhận lời gọi, không phải một instance riêng của interface.

## 3. Hai quyết định độc lập

Nếu một lớp override `greet()`, lời gọi ngoài sẽ dùng thân override đó. Khi thân được chọn lại gọi `displayName()`, lời gọi thứ hai vẫn được phân phối theo implementation thực tế của đối tượng. Vì vậy cần phân biệt:

1. Phương thức nào cung cấp quy trình tổng thể: default hay override của class.
2. Implementation nào cung cấp từng bước abstract mà quy trình gọi đến.

Mẫu này hữu ích khi nhiều lớp cần cùng một quy trình nhưng khác dữ liệu hoặc một bước xử lý, chẳng hạn định dạng thông điệp, tạo nhãn hay xác định một giá trị theo loại đối tượng.

## 4. Giữ hợp đồng đủ rõ

Default method nên dựa vào những thao tác mà interface công bố. Nếu phương thức abstract có thể trả về `null`, ném ngoại lệ hoặc thay đổi trạng thái, hợp đồng cần giải thích cách default method xử lý những trường hợp đó. Một chữ ký hợp lệ không tự bảo đảm mọi implementation đều có cùng ngữ nghĩa.

Nếu thuật toán cần nhiều thao tác thay đổi trạng thái mà phải chạy như một giao dịch duy nhất, không nên giả định rằng gom chúng trong một default method sẽ khiến chúng atomic. Hãy để lớp sở hữu trạng thái cung cấp một thao tác trừu tượng phù hợp.

## Tự kiểm tra

1. Vì sao `greet()` có thể gọi `displayName()` dù interface không có phần thân cho phương thức đó?
2. Khi `employee.greet()` chạy, vì sao tên là `An`?
3. Nếu `Employee` override `greet()`, phương thức `displayName()` có tự mất tính đa hình không?
4. Vì sao default method nên dựa vào hợp đồng interface thay vì ép kiểu sang một lớp cụ thể?

**Ghi nhớ:** default method định nghĩa quy trình dùng chung; phương thức abstract là điểm mở để đối tượng thật cung cấp dữ liệu hoặc hành vi riêng. Dynamic dispatch quyết định implementation của phương thức instance được gọi ở từng bước.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="default-methods-004">Ghi đè default method trong lớp triển khai</a>

<details>
<summary>Xem chi tiết</summary>

# Ghi đè default method trong lớp triển khai Java

Lớp triển khai có thể dùng nguyên default method của interface hoặc override nó để cung cấp hành vi riêng. Override là một phương thức instance bình thường trong class; nó phải đáp ứng hợp đồng công khai của interface.

## 1. Dùng mặc định hoặc thay thế

```java
public class DefaultOverrideDemo {
    interface Messenger {
        default String send(String message) {
            return "Default: " + message;
        }

        default String channel() {
            return "unspecified";
        }
    }

    static final class BasicMessenger implements Messenger {
        // Dùng cả hai default method.
    }

    static final class EmailMessenger implements Messenger {
        @Override
        public String send(String message) {
            return "Email: " + message;
        }
    }

    public static void main(String[] args) {
        Messenger basic = new BasicMessenger();
        Messenger email = new EmailMessenger();

        System.out.println(basic.send("hello"));
        System.out.println(email.send("hello"));
        System.out.println(email.channel());
    }
}
```

Kết quả:

```text
Default: hello
Email: hello
unspecified
```

`BasicMessenger` kế thừa cả hai phương thức. `EmailMessenger` chỉ override `send`; `channel` vẫn đến từ interface. Tham chiếu có kiểu `Messenger` vẫn gọi override của đối tượng thực tế theo quy tắc đa hình.

## 2. Quy tắc khi override

| Thành phần | Yêu cầu |
| --- | --- |
| Tên và tham số | Phải có chữ ký override tương thích. `execute(int)` và `execute(long)` là hai chữ ký khác nhau. |
| Truy cập | Phải giữ `public` vì default method của interface là public. |
| Kiểu trả về | Giữ kiểu tương thích; với kiểu tham chiếu có thể dùng kiểu trả về đồng biến hợp lệ. |
| Checked exception | Không thêm checked exception rộng hơn hợp đồng của phương thức bị override cho phép. |
| `static` | Không thể dùng static method để override một default instance method. |
| `final` | Override trong class có thể là `final` để ngăn lớp con override tiếp, nếu cấu trúc class cho phép. |

Nên đặt `@Override` trên phương thức. Annotation không tạo ra đa hình, nhưng giúp trình biên dịch phát hiện ý định override sai chữ ký. Class không ghi từ khóa `default` trước phương thức override.

Ví dụ sau sai vì giảm quyền truy cập:

```java
interface Renderer {
    default String render() {
        return "default";
    }
}

class BrokenRenderer implements Renderer {
    @Override
    String render() { // Sai: package-private hẹp hơn public.
        return "hidden";
    }
}
```

Hãy viết `public String render()`. Tương tự, một phương thức cùng tên nhưng khác tham số chỉ là overload, không thay thế default method gốc.

## 3. Phương thức lớp cha và default method

Khi class hoặc superclass có một phương thức instance concrete phù hợp, phương thức từ class được ưu tiên so với default method cùng chữ ký. Đây thường được gọi là quy tắc *class wins*.

```java
interface Named {
    default String name() {
        return "interface";
    }
}

class BaseNamed {
    public String name() {
        return "class";
    }
}

class UserNamed extends BaseNamed implements Named {
}
```

`new UserNamed().name()` trả về `class`. Kiểu khai báo của biến không làm default method thắng phương thức được kế thừa từ class. Trường hợp class có phương thức abstract hoặc chữ ký/truy cập không tương thích cần được đánh giá theo quy tắc override của Java, không thể kết luận chỉ từ việc hai tên giống nhau.

## 4. Khi nào override?

Override khi mặc định không đáp ứng nghiệp vụ của lớp, khi cần bổ sung xử lý trước/sau, hoặc khi cần giải quyết xung đột kế thừa. Nếu chỉ muốn gọi lại default method rồi trả nguyên kết quả, override thường không đem lại giá trị. Cú pháp gọi trực tiếp một default implementation cụ thể có các điều kiện riêng và được giải thích ở bài về `InterfaceName.super`.

**Ghi nhớ:** dùng `@Override public` với chữ ký tương thích. Một lớp có thể override một số default method và kế thừa các phương thức còn lại; phương thức instance của class có ưu tiên trong việc chọn implementation theo quy tắc kế thừa Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="default-methods-005">Kế thừa default method qua interface con</a>

<details>
<summary>Xem chi tiết</summary>

# Kế thừa default method qua interface con trong Java

Khi interface con `extends` một interface cha, nó kế thừa các phương thức hợp lệ của cha, bao gồm cả default method. Nếu interface con không khai báo lại phương thức, lớp triển khai có thể sử dụng default đó qua nhiều tầng kế thừa.

## 1. Kế thừa nguyên trạng

```java
public class DefaultInheritanceDemo {
    interface Greeter {
        default String greet(String name) {
            return "Hello, " + name;
        }
    }

    interface NamedGreeter extends Greeter {
        String kind();
    }

    static final class ConsoleGreeter implements NamedGreeter {
        @Override
        public String kind() {
            return "console";
        }
        // greet(String) được kế thừa.
    }

    public static void main(String[] args) {
        NamedGreeter greeter = new ConsoleGreeter();
        System.out.println(greeter.greet("Java"));
        System.out.println(greeter.kind());
    }
}
```

Kết quả:

```text
Hello, Java
console
```

`NamedGreeter` không cần khai báo lại `greet`. `ConsoleGreeter` chỉ cài đặt phương thức abstract `kind` nhưng vẫn có thể gọi `greet` trên tham chiếu kiểu `NamedGreeter`.

## 2. Interface con cung cấp default mới

Interface con có thể override phương thức default bằng một default method khác. Bản của interface con được chọn vì nó nằm ở nhánh chuyên biệt hơn.

```java
interface BasicLabel {
    default String label() {
        return "base";
    }
}

interface DetailedLabel extends BasicLabel {
    @Override
    default String label() {
        return BasicLabel.super.label() + "-detailed";
    }
}

final class Service implements DetailedLabel {
}
```

`new Service().label()` trả về `base-detailed`. Trong ví dụ, interface con thay đổi hành vi mặc định cho toàn bộ nhánh của nó. Cú pháp `BasicLabel.super.label()` được dùng ở đúng nơi interface con trực tiếp kế thừa `BasicLabel`; điều kiện chi tiết của cú pháp này được bàn riêng ở bài về qualified super calls.

## 3. Nhiều interface cha

Nếu interface con kế thừa hai default method cùng chữ ký từ hai interface cha không có quan hệ đặc hiệu, Java không tự chọn một bên. Interface con cần override để quyết định hành vi hoặc khai báo lại phương thức thành abstract để yêu cầu các lớp triển khai cung cấp implementation.

```java
interface Left {
    default String side() { return "left"; }
}

interface Right {
    default String side() { return "right"; }
}

interface Combined extends Left, Right {
    @Override
    default String side() {
        return Left.super.side() + " + " + Right.super.side();
    }
}

final class CombinedImpl implements Combined {
}
```

`new CombinedImpl().side()` trả về `left + right`. Nếu không có override tại `Combined`, hai default method cạnh tranh sẽ gây lỗi biên dịch. Không có quy tắc ưu tiên interface được liệt kê trước.

Khi một interface con override default của interface cha, lớp triển khai nhận bản chuyên biệt hơn. Nếu hai interface không liên quan cùng khai báo default cạnh tranh, phải giải quyết rõ ràng. Nếu class/superclass có một phương thức instance concrete phù hợp, phương thức class có thể được ưu tiên.

## 4. Chọn cách thiết kế

- **Không khai báo lại:** giữ default của cha.
- **Override bằng default mới:** cung cấp một hành vi chung khác cho nhánh con.
- **Khai báo lại thành abstract:** bỏ việc thừa hưởng implementation cho chữ ký đó và yêu cầu implementation phù hợp. Trường hợp này có điều kiện và hệ quả riêng, được giải thích ở bài về tái trừu tượng hóa.

Các lựa chọn này áp dụng theo từng chữ ký; khai báo lại một phương thức không làm các default method khác của cha biến mất.

## Tự kiểm tra

1. Một class chỉ implements interface con có cần cài lại mọi default method từ cha không?
2. Vì sao default method của interface con thắng default từ interface cha mà nó override?
3. Java xử lý thế nào khi hai interface cha độc lập cùng cung cấp default method trùng chữ ký?
4. Thứ tự interface trong mệnh đề `implements` có quyết định bên nào thắng không?

**Ghi nhớ:** interface con có thể giữ nguyên default của cha, cung cấp default mới hoặc yêu cầu implementation rõ ràng. Khi có nhiều nguồn phương thức, phải xét quan hệ đặc hiệu, xung đột và phương thức concrete được kế thừa từ class.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="default-methods-006">Biến default method thành yêu cầu abstract ở interface con</a>

<details>
<summary>Xem chi tiết</summary>

# Biến default method thành yêu cầu abstract ở interface con

Một interface con có thể khai báo lại default method của interface cha bằng một phương thức không có thân. Khai báo đó là `public abstract` theo mặc định và yêu cầu các kiểu triển khai interface con có một implementation phù hợp.

## 1. Cú pháp và ý nghĩa

```java
interface Greeting {
    default String greet(String name) {
        return "Hello, " + name;
    }
}

interface FormalGreeting extends Greeting {
    @Override
    String greet(String name); // public abstract
}
```

Đây là **tái trừu tượng hóa** (*re-abstracting a default method*). Nó không xóa thân `Greeting.greet` khỏi interface cha; nó thay đổi hợp đồng của nhánh `FormalGreeting`. Những class chỉ implements `Greeting` vẫn có thể dùng default cũ.

Để tái trừu tượng hóa, interface con phải có quan hệ kế thừa với interface khai báo default, khai báo lại phương thức với chữ ký override hợp lệ và không viết thân hoặc từ khóa `default`. Viết `public abstract` tường minh cũng hợp lệ, nhưng không bắt buộc.

## 2. Ví dụ hoàn chỉnh

```java
public class ReabstractDemo {
    interface Greeting {
        default String greet(String name) {
            return "Hello, " + name;
        }

        default String farewell(String name) {
            return "Goodbye, " + name;
        }
    }

    interface FormalGreeting extends Greeting {
        @Override
        String greet(String name);
    }

    static final class BasicGreeting implements Greeting {
    }

    static final class CorporateGreeting implements FormalGreeting {
        @Override
        public String greet(String name) {
            return "Dear " + name;
        }
    }

    public static void main(String[] args) {
        Greeting basic = new BasicGreeting();
        FormalGreeting formal = new CorporateGreeting();

        System.out.println(basic.greet("An"));
        System.out.println(formal.greet("An"));
        System.out.println(((Greeting) formal).greet("An"));
        System.out.println(formal.farewell("An"));
    }
}
```

Kết quả:

```text
Hello, An
Dear An
Dear An
Goodbye, An
```

Ép kiểu tham chiếu `formal` về `Greeting` không chọn lại thân default của cha. Đối tượng thực tế vẫn là `CorporateGreeting`, nên dynamic dispatch gọi implementation `greet` của class. Phương thức `farewell` không bị khai báo lại, vì thế nó vẫn là default được kế thừa.

## 3. Lớp triển khai cần làm gì?

Một class cụ thể cần có phương thức instance `public` phù hợp. Nó có thể khai báo phương thức trực tiếp hoặc kế thừa một implementation công khai hợp lệ từ superclass. Vì vậy, tái trừu tượng hóa không có nghĩa mọi class luôn phải viết lại phương thức nếu class đã có implementation thỏa hợp đồng.

```java
abstract class GreetingBase {
    public String greet(String name) {
        return "Base: " + name;
    }
}

final class InheritedGreeting extends GreetingBase
        implements FormalGreeting {
    // Phương thức public greet(String) từ superclass đáp ứng hợp đồng.
}
```

Đoạn trên giả định `FormalGreeting` được khai báo như ví dụ trước. `InheritedGreeting` không cần tự override `greet` vì phương thức concrete kế thừa từ superclass có chữ ký phù hợp.

Không thể giảm quyền truy cập xuống package-private, `protected` hoặc `private`. Các quy tắc kiểu trả về và checked exception của override cũng tiếp tục áp dụng.

## 4. Khi nào nên dùng?

Tái trừu tượng hóa hữu ích khi interface cha có mặc định đủ tốt cho đa số trường hợp, nhưng một nhánh chuyên biệt cần yêu cầu hành vi cụ thể. Ví dụ, một `SecureStorage` có thể tái khai báo `validate()` thành abstract nếu default đơn giản của `Storage` không đáp ứng được hợp đồng bảo mật của nhánh đó.

Chỉ nên làm vậy khi không có một default duy nhất đáng tin cậy cho toàn bộ nhánh. Nếu interface con có thể cung cấp một default mới đúng cho mọi implementation, lựa chọn đó có thể đơn giản hơn. Re-abstract kiểm tra sự tồn tại của implementation ở cấp ngôn ngữ; nó không chứng minh logic nghiệp vụ của implementation là đúng.

## 5. Những lỗi dễ gặp

- Viết `default String greet(String name);` mà không có thân: không hợp lệ vì default method bắt buộc có thân.
- Cho rằng phải viết từ khóa `abstract`: không cần; khai báo `String greet(String name);` trong interface đã ngầm định public abstract.
- Cho rằng interface cha mất default method: sai; thay đổi chỉ áp dụng cho nhánh con.
- Cho rằng mọi class phải khai báo trực tiếp phương thức: không tuyệt đối nếu superclass đã cung cấp implementation công khai phù hợp.
- Tái khai báo static method hoặc method không có quan hệ override: không phải tái trừu tượng hóa default method.

## Tự kiểm tra

1. Trong `interface Child extends Parent { void act(); }`, khai báo `act()` là abstract hay default?
2. Nếu class cụ thể không có implementation phù hợp, biên dịch có thành công không?
3. Tại sao ép kiểu đối tượng triển khai về interface cha không gọi lại default cũ?
4. Một default method khác của interface cha có bị ảnh hưởng không?

**Ghi nhớ:** interface con có thể làm một default method thành yêu cầu abstract bằng cách khai báo lại phương thức không có thân. Lớp triển khai cần có implementation công khai phù hợp, trực tiếp hoặc được kế thừa từ superclass.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="default-methods-007">Gọi default method bằng InterfaceName.super</a>

<details>
<summary>Xem chi tiết</summary>

# Gọi default method bằng InterfaceName.super trong Java

Khi override một default method nhưng muốn tái sử dụng đúng phần thân của interface, Java có cú pháp `InterfaceName.super.method(arguments)`. Nó hữu ích để mở rộng một hành vi mặc định hoặc lựa chọn rõ ràng giữa các default method xung đột.

## 1. Phân biệt ba kiểu gọi

| Cú pháp | Ý nghĩa |
| --- | --- |
| `this.method()` hoặc `method()` | Gọi phương thức trên đối tượng hiện tại theo quy tắc dispatch thông thường. |
| `super.method()` | Tìm implementation qua nhánh superclass. |
| `InterfaceName.super.method()` | Chỉ định một default implementation từ superinterface phù hợp. |

`InterfaceName.super` không phải biến hoặc đối tượng có thể lưu vào tham chiếu. Đây là cú pháp đặc biệt của Java, chỉ dùng trong ngữ cảnh hợp lệ và với một default implementation có thể gọi được.

## 2. Mở rộng default method

```java
public class InterfaceSuperDemo {
    interface Greeting {
        default String sayHello(String name) {
            return "Xin chào, " + name;
        }
    }

    static final class FriendlyGreeting implements Greeting {
        @Override
        public String sayHello(String name) {
            String base = Greeting.super.sayHello(name);
            return base + "!";
        }
    }

    public static void main(String[] args) {
        Greeting greeting = new FriendlyGreeting();
        System.out.println(greeting.sayHello("Lan"));
    }
}
```

Kết quả:

```text
Xin chào, Lan!
```

`FriendlyGreeting` gọi phần thân default để tạo câu chào, rồi thêm dấu cảm thán. Nếu viết `return sayHello(name) + "!";` trong chính override này, phương thức sẽ gọi lại nó và đệ quy cho đến `StackOverflowError`. Qualified super call tránh việc đó.

## 3. Chọn giữa hai default method

```java
public class DefaultConflictDemo {
    interface Left {
        default String source() { return "Left"; }
    }

    interface Right {
        default String source() { return "Right"; }
    }

    static final class Combined implements Left, Right {
        @Override
        public String source() {
            return Left.super.source() + " + " + Right.super.source();
        }
    }

    public static void main(String[] args) {
        System.out.println(new Combined().source());
    }
}
```

Kết quả là `Left + Right`. Hai interface độc lập cung cấp cùng chữ ký nên class phải override để giải quyết xung đột. Override có thể chọn một phía, kết hợp cả hai hoặc viết logic mới; thứ tự interface trong `implements` không tạo ưu tiên.

## 4. Interface con cũng có thể gọi default của cha

```java
interface Parent {
    default String label() {
        return "parent";
    }
}

interface Child extends Parent {
    @Override
    default String label() {
        return Parent.super.label() + "/child";
    }
}
```

`Child` có thể gọi `Parent.super.label()` vì `Parent` là superinterface trực tiếp của `Child`. Khi một class triển khai `Child` mà không override, nó nhận default mới của `Child`.

## 5. Điều kiện cần nhớ

- Phương thức được gọi phải có default implementation phù hợp; không thể gọi một method chỉ abstract.
- Lời gọi phải ở ngữ cảnh instance hợp lệ, không dùng trong phương thức static.
- Interface qualifier phải phù hợp với quan hệ superinterface tại nơi chứa lời gọi. Không thể tùy ý gọi một interface bất kỳ mà đối tượng có thể ép kiểu sang.
- Static method của interface được gọi bằng tên interface, không bằng `InterfaceName.super`.
- Qualified super call không cho phép bỏ qua mọi quy tắc kế thừa hoặc tùy tiện gọi một implementation đã bị thay thế ở nhánh cụ thể hơn.

Các điều kiện tinh vi về superinterface trực tiếp, interface trung gian và nhánh cụ thể hơn được phân tích riêng ở bài về giới hạn gọi super qua interface trực tiếp.

**Ghi nhớ:** `InterfaceName.super.method()` chỉ định rõ một default implementation trong phạm vi mà Java cho phép. Dùng nó bên trong override khi cần mở rộng hành vi hoặc giải quyết xung đột; đừng nhầm nó với `super.method()` của class.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="default-methods-008">Giới hạn gọi super qua interface trực tiếp</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn gọi super qua interface trực tiếp trong Java

Cú pháp `I.super.method()` có những giới hạn về quan hệ kế thừa. Tên interface trước `.super` phải là superinterface trực tiếp phù hợp của **chính class hoặc interface đang chứa lời gọi**, và lời gọi không được dùng để bỏ qua một nhánh interface cụ thể hơn.

## 1. Trực tiếp và gián tiếp không giống nhau

```java
interface Base {
    default String label() {
        return "Base";
    }
}

interface Extended extends Base {
}

final class Item implements Extended {
    @Override
    public String label() {
        return Extended.super.label() + " -> Item";
    }
}
```

`Extended.super.label()` hợp lệ vì `Extended` được liệt kê trực tiếp trong `implements` của `Item`. Dù phần thân ban đầu được khai báo trong `Base`, `Extended` kế thừa default đó và là qualifier thích hợp tại vị trí gọi.

Ngược lại, `Base.super.label()` bên trong `Item` không hợp lệ: `Base` chỉ là superinterface gián tiếp. Không thể dùng qualified super call để nhảy tùy ý đến tổ tiên xa hơn.

## 2. Không được bỏ qua interface con cụ thể hơn

Xét `A` có default method và `B extends A` override phương thức đó. Nếu class trực tiếp khai báo `implements A, B`, Java không cho gọi `A.super.method()` để bỏ qua implementation cụ thể hơn từ `B`.

```java
interface A {
    default String name() { return "A"; }
}

interface B extends A {
    @Override
    default String name() { return "B"; }
}

class C implements A, B {
    @Override
    public String name() {
        return B.super.name();
    }
}
```

`B.super.name()` chọn nhánh chuyên biệt hơn. Đổi thành `A.super.name()` là lỗi biên dịch vì `B` cũng là superinterface trực tiếp của `C` và là subtype của `A`. Liệt kê cả cha và con trong `implements` không tạo quyền bỏ qua override của con.

## 3. Tôn trọng quyết định ở tầng trung gian

```java
interface Root {
    default String format() {
        return "Root";
    }
}

interface Middle extends Root {
    @Override
    default String format() {
        return "Middle(" + Root.super.format() + ")";
    }
}

class Bottom implements Middle {
    @Override
    public String format() {
        return Middle.super.format() + " -> Bottom";
    }
}
```

Chuỗi gọi hợp lệ là `Middle` gọi `Root.super.format()` tại nơi `Root` trực tiếp được kế thừa; `Bottom` gọi `Middle.super.format()`. Kết quả `new Bottom().format()` là `Middle(Root) -> Bottom`. Không có cú pháp tổng quát để nhảy qua nhiều tầng và cưỡng ép chạy một thân cũ; mỗi tầng cần tôn trọng quan hệ kế thừa của chính nó.

## 4. Những trường hợp không thể dùng qualified super

- **Không có thân default:** một phương thức abstract không có implementation để gọi.
- **Static hoặc private:** static method được gọi bằng tên interface; private helper chỉ được gọi từ nơi có quyền truy cập theo quy tắc Java.
- **Static context:** default method là instance method nên qualified super call không hợp lệ trong phương thức static.
- **Chỉ được kế thừa qua superclass:** nếu `Parent implements Labelled` và `Child extends Parent`, `Labelled` không tự trở thành superinterface trực tiếp của `Child`. Child có thể dùng phương thức kế thừa theo cách gọi instance bình thường hoặc qua `super` khi thích hợp.
- **Có nhánh trực tiếp cụ thể hơn:** không được chọn qualifier tổ tiên để bỏ qua override của superinterface con.

| Tình huống | Cách xử lý |
| --- | --- |
| `C implements B` và `B extends A` | Xem xét `B.super.m()`, không gọi `A.super.m()` từ C. |
| `C implements A, B` và `B extends A` | Tôn trọng nhánh cụ thể hơn `B`. |
| `C implements Left, Right`, hai default độc lập xung đột | Override trong C và chọn rõ `Left.super.m()`/`Right.super.m()` nếu cần. |
| Phương thức đích là abstract/static/private | Không gọi bằng qualified super; dùng cơ chế phù hợp với loại phương thức. |

**Ghi nhớ:** không chỉ tên interface được ghi trong `implements` quan trọng; cấu trúc kế thừa và các override cũng quyết định lời gọi có hợp lệ hay không. Nếu cần tái sử dụng hành vi qua nhiều tầng, hãy để từng interface trung gian quyết định cách phơi bày hoặc mở rộng hành vi của tổ tiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="default-methods-009">Giới hạn chia sẻ trạng thái và hợp đồng của default method</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn chia sẻ trạng thái và hợp đồng của default method

Default method chia sẻ **cách thực hiện một hành vi**, không tạo trạng thái riêng cho từng đối tượng và không tự bảo đảm mọi lớp triển khai tuân thủ cùng một quy tắc nghiệp vụ. Trạng thái, bất biến và đồng bộ vẫn phải thuộc về nơi sở hữu dữ liệu.

## 1. Hành vi dùng chung không phải trạng thái dùng chung

```java
interface Greeter {
    String name();

    default String greet() {
        return "Xin chào, " + name();
    }
}
```

`greet()` chỉ dựa vào hợp đồng `name()`. Tên có thể đến từ trường của class, cơ sở dữ liệu, một đối tượng thành phần hoặc giá trị được tính toán. Default method không cần biết chi tiết đó; nó cũng không thể truy cập trực tiếp trường riêng chỉ tồn tại trong một class.

Interface không có constructor và không thể khai báo instance field. Mọi field khai báo trong interface đều ngầm định `public static final`. Vì thế:

```java
interface Settings {
    int MAX_RETRIES = 3;
}
```

`MAX_RETRIES` là hằng cấp interface, không phải biến riêng của mỗi instance. Từ khóa `final` ngăn gán lại biến tham chiếu, nhưng không làm đối tượng mutable mà nó trỏ tới trở thành bất biến.

## 2. Cẩn thận với đối tượng mutable trong field interface

```java
import java.util.ArrayList;
import java.util.List;

interface SharedRecords {
    List<String> RECORDS = new ArrayList<>();

    default void record(String value) {
        RECORDS.add(value);
    }
}
```

Đoạn mã này hợp lệ nhưng danh sách `RECORDS` là một tham chiếu `public static final` dùng chung, không phải danh sách riêng của từng đối tượng. Mọi nơi có quyền truy cập có thể thay đổi nội dung của nó; `ArrayList` cũng không tự bảo đảm an toàn luồng. Đặt cache hoặc collection mutable trong interface thường vô tình tạo trạng thái dùng chung khó kiểm soát.

Nếu cần trạng thái theo instance, hãy đặt nó trong lớp triển khai. Nếu cần trạng thái dùng chung, đặt nó trong một thành phần có chủ sở hữu, vòng đời và chính sách đồng bộ rõ ràng.

## 3. Ví dụ: chia sẻ thuật toán nhưng giữ trạng thái riêng

```java
public class DefaultStateDemo {
    interface Counter {
        int value();
        void add(int delta);

        default void increment() {
            add(1);
        }

        default boolean isZero() {
            return value() == 0;
        }
    }

    static final class MemoryCounter implements Counter {
        private int current;

        @Override
        public int value() {
            return current;
        }

        @Override
        public void add(int delta) {
            if (delta < 0) {
                throw new IllegalArgumentException("delta must be non-negative");
            }
            current = Math.addExact(current, delta);
        }
    }

    public static void main(String[] args) {
        Counter first = new MemoryCounter();
        Counter second = new MemoryCounter();
        first.increment();
        first.increment();

        System.out.println(first.value());
        System.out.println(second.value());
        System.out.println(first.isZero());
        System.out.println(second.isZero());
    }
}
```

Kết quả:

```text
2
0
false
true
```

Cả hai đối tượng dùng chung implementation của `increment()` và `isZero()`, nhưng mỗi `MemoryCounter` có trường `current` riêng. Ví dụ chạy tuần tự; nó không làm `MemoryCounter` an toàn khi nhiều luồng cập nhật cùng lúc.

## 4. Hợp đồng phải nêu rõ tiền điều kiện và tác động

Một default method có thể ghép nhiều thao tác, nhưng chữ ký Java không tự định nghĩa đầy đủ ý nghĩa của chúng. Với mỗi phương thức hợp đồng, hãy làm rõ:

| Khía cạnh | Điều cần quy định |
| --- | --- |
| Tiền điều kiện | Đầu vào null, âm, rỗng hoặc ngoài miền xử lý được xử lý thế nào? |
| Hậu điều kiện | Sau khi thành công, kết quả và trạng thái thay đổi ra sao? |
| Ngoại lệ | Lỗi được trả qua giá trị hay exception nào? |
| Side effect | Phương thức có sửa dữ liệu, I/O hoặc gọi dịch vụ khác không? |
| Đồng thời | Thao tác có cần atomic hoặc thread-safe không? |
| Override | Implementation riêng có phải giữ cùng cam kết ngữ nghĩa không? |

Ví dụ thiết kế có vấn đề:

```java
interface Inventory {
    int available();
    void reserve(int units);

    default boolean tryReserve(int units) {
        if (available() >= units) {
            reserve(units);
            return true;
        }
        return false;
    }
}
```

Hai lời gọi `available()` và `reserve()` độc lập. Một luồng khác có thể thay đổi tồn kho giữa lúc kiểm tra và cập nhật; default method không biến chuỗi thao tác thành giao dịch atomic. Ví dụ còn thiếu quy tắc cho `units <= 0` và cách báo thất bại.

Nếu kiểm tra và cập nhật phải là một thao tác nguyên tử, hãy để lớp sở hữu trạng thái triển khai một phương thức hợp đồng duy nhất như `tryReserve(int units)`. Default method có thể tổng hợp các thao tác đọc an toàn, nhưng không nên giả định nó có thể cưỡng chế bất biến của trạng thái mà interface không sở hữu.

## 5. Chọn cơ chế phù hợp

| Nhu cầu | Lựa chọn thường phù hợp |
| --- | --- |
| Hành vi nhỏ được tính từ các phương thức hợp đồng rõ ràng | Default method |
| Mỗi implementation phải tự quyết định một hành vi quan trọng | Phương thức abstract |
| Trạng thái instance, constructor và logic cốt lõi dùng chung | Abstract class nếu quan hệ kế thừa phù hợp |
| Cache, giao dịch, I/O hoặc dependency có vòng đời | Service hoặc đối tượng thành phần |
| Helper chỉ phục vụ các phương thức trong interface | Private method của interface từ Java 9 |

Một default method cũng có thể bị override; nó không phải cơ chế bảo đảm thuật toán sẽ luôn chạy. Khi phát hành API, hãy kiểm tra xung đột phương thức và tương thích hành vi với các implementer hiện có. Mã biên dịch được chưa đủ chứng minh rằng mọi implementation giữ đúng tiền điều kiện, hậu điều kiện hoặc cam kết đồng thời.

## Tự kiểm tra

1. Vì sao field khai báo trong interface không phải trạng thái riêng của từng đối tượng?
2. Tại sao `static final` không làm một `ArrayList` trở thành bất biến?
3. Vì sao `available()` rồi `reserve()` trong default method không tự tạo tính atomic?
4. Khi nào nên đưa một thao tác vào phương thức abstract do class sở hữu thay vì tổng hợp trong default method?

**Ghi nhớ:** default method chia sẻ logic. Lớp triển khai hoặc thành phần chuyên trách sở hữu dữ liệu, bất biến và cơ chế đồng bộ. Hợp đồng phải mô tả rõ cách xử lý đầu vào, lỗi, side effect và tính đồng thời.

</details>

- [Quay lại đầu trang](#back-to-top)
