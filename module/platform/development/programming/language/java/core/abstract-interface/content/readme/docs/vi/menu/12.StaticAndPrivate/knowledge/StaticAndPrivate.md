<a id="back-to-top"></a>

# Static và private

## Menu
- [Phương thức static trong interface từ Java 8](#static-and-private-001)
- [Gọi static method qua đúng tên interface](#static-and-private-002)
- [Static method không được kế thừa bởi interface con hoặc lớp triển khai](#static-and-private-003)
- [Phương thức private trong interface từ Java 9](#static-and-private-004)
- [Chia sẻ mã giữa các default method bằng private instance method](#static-and-private-005)
- [Private static method và giới hạn truy cập thành viên instance](#static-and-private-006)
- [Phân biệt private helper với điểm mở rộng cho lớp triển khai](#static-and-private-007)

## <a id="static-and-private-001">Phương thức static trong interface từ Java 8</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức `static` trong interface từ Java 8

Từ **Java 8**, interface có thể khai báo phương thức `static` có thân hàm. Phương thức này thuộc về chính interface, phù hợp với thao tác gắn với kiểu dữ liệu như kiểm tra đầu vào, chuẩn hóa dữ liệu hoặc tạo một implementation. Nó không mô tả hành vi cần được lựa chọn theo từng đối tượng.

## 1. Phân biệt ba loại phương thức

| Loại | Vai trò | Cách gọi thường dùng | Có thể override như hành vi instance? |
|---|---|---|---|
| Abstract instance method | Khai báo việc đối tượng phải làm | `sender.send(message)` | Có; lớp concrete phải cung cấp implementation phù hợp |
| `default` instance method | Cung cấp hành vi mặc định cho đối tượng | `sender.description()` | Có |
| `static` method | Cung cấp thao tác thuộc về interface | `MessageSender.normalize(text)` | Không |

Ví dụ, `send` là hành vi của từng sender, còn `normalize` là quy tắc xử lý chuỗi có thể dùng chung mà không cần một đối tượng `MessageSender`.

## 2. Cú pháp và các quy tắc chính

~~~java
interface MessageSender {
    void send(String message);

    static String normalize(String message) {
        if (message == null) {
            return "";
        }
        return message.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
~~~

Phương thức `static` trong interface phải có thân hàm. Nếu không ghi access modifier, nó là `public`; có thể viết rõ `public static` để nhấn mạnh API công khai.

Một phương thức không thể đồng thời là `static` và `abstract` hoặc `default`. Phương thức `static` không có `this` đại diện cho instance hiện tại, vì vậy không thể gọi trực tiếp một instance method nếu chưa có đối tượng nhận lời gọi.

Phương thức `static` của interface được gọi bằng tên interface đã khai báo nó. Quy tắc này khác với cách gọi instance method qua đối tượng; bài kế tiếp đi sâu vào cách viết lời gọi đúng.

## 3. Ví dụ hoàn chỉnh

Lưu đoạn sau thành `Main.java`. Ví dụ tương thích Java 8.

~~~java
interface MessageSender {
    void send(String message);

    static String normalize(String message) {
        if (message == null) {
            return "";
        }
        return message.trim().toUpperCase(java.util.Locale.ROOT);
    }
}

final class ConsoleSender implements MessageSender {
    @Override
    public void send(String message) {
        System.out.println("Sent: " + MessageSender.normalize(message));
    }
}

public class Main {
    public static void main(String[] args) {
        MessageSender sender = new ConsoleSender();
        sender.send("  hello Java  ");
        System.out.println("Normalized: " + MessageSender.normalize(" java "));
    }
}
~~~

Biên dịch và chạy trên JDK 9 trở lên:

~~~bash
javac -encoding UTF-8 --release 8 Main.java
java Main
~~~

Stdout:

~~~text
Sent: HELLO JAVA
Normalized: JAVA
~~~

Lời gọi `sender.send(...)` được phân giải theo đối tượng thực tế và gọi implementation của `ConsoleSender`. Lời gọi `MessageSender.normalize(...)` được xác định theo interface ghi trước dấu chấm; nó không cần tạo sender và không tham gia đa hình instance.

## 4. Phương thức static trong interface generic

Static method không gắn với một đối tượng `Box<T>` cụ thể, nên không thể dùng trực tiếp type parameter `T` của interface. Nếu chính phương thức cần generic, hãy khai báo type parameter riêng:

~~~java
interface Box<T> {
    T get();

    static <U> U identity(U value) {
        return value;
    }
}
~~~

`Box.identity("Java")` suy luận `U` là `String`, còn `Box.identity(42)` suy luận `U` là `Integer`. Type parameter `U` thuộc về method; nó không phụ thuộc vào `T` của một instance `Box<T>`.

## 5. Khi nào nên dùng?

Cân nhắc `static` khi thao tác có ý nghĩa trực tiếp với interface và không cần trạng thái của đối tượng, ví dụ kiểm tra dữ liệu hoặc factory method. Nếu mỗi implementation cần cung cấp hành vi riêng, hãy dùng abstract instance method; nếu cần hành vi mặc định có thể thay đổi theo đối tượng, cân nhắc `default`.

Không đưa mọi tiện ích vào interface một cách máy móc. Nếu logic không thuộc hợp đồng của interface, một utility class có thể diễn đạt trách nhiệm rõ hơn.

**Tự kiểm tra**

- Phương thức `static` trong interface có bắt buộc có thân hàm không? **Có.**
- Có thể gọi nó qua biến instance để nhận dispatch đa hình không? **Không.**
- Nếu một static method cần generic riêng, dùng type parameter của interface hay khai báo type parameter của method? **Khai báo type parameter của method.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="static-and-private-002">Gọi static method qua đúng tên interface</a>

<details>
<summary>Xem chi tiết</summary>

# Gọi static method qua đúng tên interface

Khi một `static method` được khai báo trong interface, tên interface đó là chủ thể của lời gọi. Ví dụ, nếu `hasText` được khai báo trong `TextRules`, cách gọi rõ ràng là:

~~~java
TextRules.hasText("Java");
~~~

Đừng suy ra nơi khai báo method từ kiểu của biến đang cầm hoặc từ class có `implements` interface đó. Lời gọi static được xác định theo tên kiểu ghi trong biểu thức, không theo runtime class của đối tượng.

## 1. Ví dụ chạy được

Lưu toàn bộ mã sau thành `InterfaceStaticDemo.java`:

~~~java
interface TextRules {
    static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    default boolean accepts(String value) {
        return TextRules.hasText(value);
    }
}

final class BasicRules implements TextRules {
}

public class InterfaceStaticDemo {
    public static void main(String[] args) {
        System.out.println(TextRules.hasText("Java"));
        System.out.println(TextRules.hasText("   "));

        TextRules rules = new BasicRules();
        System.out.println(rules.accepts(" Java "));

        java.util.function.Predicate<String> check = TextRules::hasText;
        System.out.println(check.test("API"));
    }
}
~~~

Biên dịch và chạy trên JDK 8 trở lên:

~~~bash
javac -encoding UTF-8 --release 8 InterfaceStaticDemo.java
java InterfaceStaticDemo
~~~

Stdout:

~~~text
true
false
true
true
~~~

`TextRules.hasText(...)` là lời gọi static. Ngược lại, `rules.accepts(...)` là lời gọi instance đến một default method. Bên trong `accepts`, mã vẫn gọi helper static bằng `TextRules.hasText(value)`.

`TextRules::hasText` là method reference đến static method được interface khai báo. Nó có thể được truyền vào API nhận functional interface như `Predicate<String>`.

## 2. Những lời gọi không hợp lệ

Trong ví dụ trên, hai dòng sau cố ý được để thành comment vì chúng gây lỗi biên dịch nếu bỏ comment:

~~~java
// BasicRules.hasText("Java"); // BasicRules không kế thừa static method của TextRules.
// rules.hasText("Java");      // Static interface method không phải instance method.
~~~

`implements TextRules` chỉ kết nối class với hợp đồng instance của interface; nó không đưa các static method của interface thành thành viên kế thừa của class. Nếu `BasicRules` tự khai báo một static method cùng tên, đó là method của chính `BasicRules`, không phải override của `TextRules.hasText`.

## 3. Static import và lựa chọn API

Trong một tệp khác thuộc package phù hợp, Java cho phép static import một phương thức công khai:

~~~java
import static com.example.rules.TextRules.hasText;

class Caller {
    boolean check() {
        return hasText("Java");
    }
}
~~~

Ví dụ import này minh họa cú pháp; package phải khớp với nơi `TextRules` thực sự được khai báo. Static import chỉ rút gọn cách viết. Nó không thay đổi chủ sở hữu method, quyền truy cập hay quy tắc dispatch.

Nếu muốn một thao tác là hành vi có thể tùy biến theo implementation, hãy khai báo abstract/default instance method và gọi qua object. Đừng dùng static method rồi kỳ vọng class triển khai sẽ override nó.

**Checklist trước khi gọi**

1. Xác định interface thực sự khai báo method.
2. Dùng `InterfaceName.method(...)` hoặc method reference `InterfaceName::method`.
3. Nếu cần gọi qua đối tượng và có đa hình, kiểm tra xem method có phải instance method hay không.
4. Nếu compiler báo không tìm thấy method trên class triển khai hoặc interface con, quay lại kiểu khai báo gốc để xác định owner.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="static-and-private-003">Static method không được kế thừa bởi interface con hoặc lớp triển khai</a>

<details>
<summary>Xem chi tiết</summary>

# Static method không được kế thừa bởi interface con hoặc lớp triển khai

Một static method khai báo trong interface thuộc về interface đó. Quan hệ `extends` giữa các interface và `implements` giữa class với interface **không làm static method trở thành thành viên được kế thừa**. Quy tắc này khác với static method của class, vốn có thể được class con kế thừa và che khuất theo các quy tắc riêng.

## 1. Interface con kế thừa default method nhưng không kế thừa static method

~~~java
interface ParentApi {
    static String version() {
        return "parent";
    }

    default String greet() {
        return "hello";
    }
}

interface ChildApi extends ParentApi {
    static String version() {
        return "child";
    }
}

final class ApiImpl implements ChildApi {
    static String version() {
        return "class";
    }
}

public class Main {
    public static void main(String[] args) {
        System.out.println(ParentApi.version());
        System.out.println(ChildApi.version());
        System.out.println(ApiImpl.version());

        ParentApi api = new ApiImpl();
        System.out.println(api.greet());
    }
}
~~~

Biên dịch trên JDK 8 trở lên bằng `javac -encoding UTF-8 --release 8 Main.java` rồi chạy `java Main`. Stdout:

~~~text
parent
child
class
hello
~~~

`ChildApi.version` là một static method mới do `ChildApi` khai báo; nó không override `ParentApi.version`. Tương tự, `ApiImpl.version` là method của class, độc lập với các static method của interface. Ba lời gọi được chọn theo tên kiểu ở bên trái dấu chấm.

Ngược lại, `greet` là default instance method được kế thừa từ `ParentApi`. Lời gọi `api.greet()` hợp lệ vì nó đi qua một đối tượng triển khai interface.

Nếu `ChildApi` không tự khai báo `version`, biểu thức `ChildApi.version()` sẽ không hợp lệ. Phải gọi `ParentApi.version()` bằng đúng tên interface khai báo method.

## 2. Class implements không phải triển khai static method

Class triển khai chỉ phải cung cấp các abstract instance method còn thiếu. Static method của interface không tạo ra nghĩa vụ implementation, cũng không được class kế thừa như một method của chính class.

~~~java
interface Codec {
    static String encode(String value) {
        return "[" + value + "]";
    }

    String decode(String text);
}

final class BracketCodec implements Codec {
    @Override
    public String decode(String text) {
        return text.substring(1, text.length() - 1);
    }
}
~~~

`Codec.encode("Java")` hợp lệ. Các lời gọi sau không hợp lệ nếu `BracketCodec` không tự khai báo method tương ứng:

~~~java
// BracketCodec.encode("Java");
// new BracketCodec().encode("Java");
// Codec codec = new BracketCodec();
// codec.encode("Java");
~~~

Đừng nhầm lỗi ở `BracketCodec.encode` với lỗi quyền truy cập: method `Codec.encode` là public, nhưng vẫn không được kế thừa vào class. Quyền truy cập và quy tắc kế thừa là hai vấn đề khác nhau.

## 3. Static method cùng tên không phải override

Một subinterface hoặc class triển khai có thể tự khai báo static method trùng tên với static method của interface cha nếu không xung đột với một instance method được kế thừa. Các khai báo static này độc lập. Không dùng `@Override` để đánh dấu chúng.

~~~java
interface Root {
    static int parse(String value) {
        return Integer.parseInt(value);
    }
}

interface StrictRoot extends Root {
    static int parse(String value) {
        return Root.parse(value) + 1;
    }
}
~~~

`Root.parse("10")` trả về `10`, còn `StrictRoot.parse("10")` trả về `11`. Method ở interface con tự định nghĩa hành vi của nó; nó không thay thế method ở interface cha.

Có một ranh giới quan trọng: nếu interface cha đã có default hoặc abstract instance method với một chữ ký, interface con không thể khai báo static method cùng chữ ký để thay thế method instance được kế thừa. Static và instance method không thể được trộn như thể chúng là cùng một extension point.

## 4. So sánh với static method của class

~~~java
class Base {
    static String kind() {
        return "base";
    }
}

class Derived extends Base {
}
~~~

`Derived.kind()` hợp lệ vì class con có thể kế thừa static method phù hợp từ superclass. Nếu class con khai báo một static method tương ứng, đó là *method hiding*, không phải overriding đa hình.

Với interface, static method không được kế thừa xuống subinterface hoặc class implements. Cũng đừng khái quát rằng mọi thành viên static đều có cùng quy tắc: field của interface là `public static final` và có thể được kế thừa theo quy tắc áp dụng cho field, trong khi static method thì không.

| Trường hợp | Quy tắc |
|---|---|
| Class con kế thừa static method của class cha | Có thể; method cùng chữ ký ở class con có thể che khuất method cha |
| Interface con kế thừa static method của interface cha | Không |
| Class implements kế thừa static method của interface | Không |
| Interface con kế thừa default instance method | Có, theo quy tắc kế thừa và xử lý xung đột |
| Class implements phải override static method của interface | Không |

**Tự kiểm tra**

- `ChildApi.version()` có gọi được method chỉ được khai báo trong `ParentApi` không? **Không.**
- Class triển khai có phải implement static method của interface không? **Không.**
- Nếu subtype tự khai báo static method trùng tên, nó có override method của interface cha không? **Không; đó là khai báo riêng.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="static-and-private-004">Phương thức private trong interface từ Java 9</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức private trong interface từ Java 9

Từ **Java 9**, interface có thể khai báo phương thức `private` có thân hàm. Đây là helper nội bộ để các method khác trong cùng interface chia sẻ logic mà không đưa helper đó thành một phần API công khai.

Java 8 đã bổ sung `default` instance method và `public static` method, nhưng chưa cho phép private method trong interface. Vì vậy, đoạn code dùng private interface method không biên dịch khi target là `--release 8`.

## 1. Private instance và private static

- **Private instance method** có ngữ cảnh đối tượng; thường được gọi từ default method hoặc private instance method khác trong cùng interface.
- **Private static method** không cần instance; có thể được gọi từ static method, default method và các helper phù hợp trong cùng interface.

Cả hai dạng đều phải có thân hàm. Chúng không phải hợp đồng mà class triển khai cần implement, không được kế thừa để override, và không thể được gọi trực tiếp từ code bên ngoài interface khai báo chúng.

## 2. Ví dụ: helper dùng chung nhưng không lộ ra API

Lưu toàn bộ chương trình thành `Main.java`. Ví dụ yêu cầu Java 9 trở lên.

~~~java
interface TextView {
    default String title(String value) {
        return format("TITLE", value);
    }

    default String summary(String value) {
        return format("SUMMARY", value);
    }

    private String format(String label, String value) {
        return label + ": " + normalize(value);
    }

    static String versionLabel() {
        return "[" + normalize(" Java 9+ ") + "]";
    }

    private static String normalize(String value) {
        if (value == null) {
            return "(empty)";
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}

final class ConsoleTextView implements TextView {
}

public class Main {
    public static void main(String[] args) {
        TextView view = new ConsoleTextView();
        System.out.println(view.title(" private helper "));
        System.out.println(view.summary(" shared code "));
        System.out.println(TextView.versionLabel());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 --release 9 Main.java
java Main
~~~

Stdout:

~~~text
TITLE: PRIVATE HELPER
SUMMARY: SHARED CODE
[JAVA 9+]
~~~

`title` và `summary` là default instance method công khai; cả hai gọi `format` để tái sử dụng bước ghép nhãn. `format` có thể hoạt động trong ngữ cảnh instance và gọi `normalize`. Phương thức static công khai `versionLabel` cũng gọi được `normalize` vì helper này là private static, không cần `this`.

`ConsoleTextView` không cần khai báo hoặc override các helper. Nếu lớp này tự khai báo một method trùng tên với `format`, đó không phải override của helper private; lời gọi trong interface vẫn dùng phần cài đặt riêng tư đã khai báo ở `TextView`.

## 3. Quy tắc truy cập cần nhớ

| Khai báo | Bên ngoài interface gọi trực tiếp được? | Class triển khai override được? |
|---|---|---|
| `default String title(...) { ... }` | Có, qua object | Có |
| `static String versionLabel() { ... }` | Có, qua `TextView.versionLabel()` | Không |
| `private String format(...) { ... }` | Không | Không |
| `private static String normalize(...) { ... }` | Không | Không |

**Nếu bỏ comment ở một trong các dòng dưới đây, khai báo đó sẽ không hợp lệ.** Mỗi dòng là một ví dụ riêng; không thể ghép tất cả lỗi vào cùng một chương trình chạy được.

~~~java
interface InvalidHelpers {
    // private abstract String helper();
    // private default String other() { return "x"; }
    // private void missingBody();
}
~~~

`private abstract` và `private default` không hợp lệ. Private method không thể chỉ có dấu chấm phẩy; nó cần body vì không được lớp con kế thừa để cung cấp implementation. Interface cũng không dùng `protected` hoặc package-private method để tạo một phạm vi trung gian như class.

Private helper vẫn có thể khai báo `throws` cho checked exception. Nếu một default/static method gọi helper đó, phương thức gọi phải bắt hoặc khai báo checked exception phù hợp; modifier `private` không làm ngoại lệ biến mất.

## 4. Chọn loại helper theo nhu cầu

Dùng private instance method khi logic cần ngữ cảnh của đối tượng hiện tại, chẳng hạn khi gọi một instance method khác của interface. Dùng private static method khi logic chỉ cần tham số hoặc các thành viên static và cần được dùng từ cả static method.

Nếu mọi class triển khai phải cung cấp một hành vi, hãy công bố một abstract instance method. Nếu cần hành vi mặc định có thể được class thay đổi, dùng default method. Private helper chỉ nên chứa chi tiết cài đặt nội bộ.

**Kiểm tra phiên bản:** nếu build dùng Java 8, hãy chuyển helper sang utility class phù hợp hoặc điều chỉnh target; không thể giữ private method trong interface rồi kỳ vọng `--release 8` chấp nhận nó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="static-and-private-005">Chia sẻ mã giữa các default method bằng private instance method</a>

<details>
<summary>Xem chi tiết</summary>

# Chia sẻ mã giữa các default method bằng private instance method trong Java

## 1. Vì sao cần private method trong interface?

Từ Java 8, interface có thể định nghĩa **default method** có thân, cung cấp hành vi mặc định cho các lớp triển khai. Khi nhiều default method cần cùng một bước xử lý, sao chép logic vào từng phương thức làm mã lặp và dễ lệch nhau khi bảo trì.

Từ **Java 9**, interface có thể khai báo phương thức `private` để giữ phần triển khai dùng chung bên trong interface. **Private instance method** có thân, không có từ khóa `static`, và hoạt động trong ngữ cảnh đối tượng hiện tại. Nó cho phép các phương thức trong interface dùng chung logic mà không công khai helper thành một phần API.

Mẫu sử dụng:

1. Giữ các default method làm điểm gọi công khai.
2. Chuyển logic lặp lại vào private instance method.
3. Gọi helper đó từ từng default method với tham số phù hợp.
4. Giữ helper private để caller và lớp triển khai không phụ thuộc vào chi tiết nội bộ.

## 2. Ví dụ Java 17+: hai default method dùng chung helper

Lưu mã sau vào **PrivateInterfaceMethodDemo.java**:

~~~java
interface ReportFormatter {
    String serviceName();

    default String success(String user) {
        return format("SUCCESS", user);
    }

    default String failure(String user) {
        return format("FAILURE", user);
    }

    private String format(String status, String user) {
        return "[" + serviceName() + "] " + status + ": "
                + clean(user);
    }

    private String clean(String user) {
        if (user == null || user.isBlank()) {
            return "guest";
        }
        return user.trim();
    }
}

final class BillingFormatter implements ReportFormatter {
    @Override
    public String serviceName() {
        return "Billing";
    }
}

public class PrivateInterfaceMethodDemo {
    public static void main(String[] args) {
        ReportFormatter formatter = new BillingFormatter();
        System.out.println(formatter.success(" Mia "));
        System.out.println(formatter.failure(" "));
        System.out.println(formatter.success(null));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac PrivateInterfaceMethodDemo.java
java PrivateInterfaceMethodDemo
~~~

Đầu ra chính xác:

~~~text
[Billing] SUCCESS: Mia
[Billing] FAILURE: guest
[Billing] SUCCESS: guest
~~~

`success()` và `failure()` là default method có thể gọi qua biến kiểu `ReportFormatter`. Cả hai dùng `format()`, còn `format()` gọi `clean()` để xử lý tên. Nhờ đó, cách ghép thông báo chỉ được định nghĩa một lần.

Helper `format()` gọi `serviceName()` trên đối tượng hiện tại. Lời gọi được điều phối đến `BillingFormatter.serviceName()`, nên kết quả chứa `[Billing]`. Đây là lý do ví dụ dùng private **instance** method: helper dựa vào hành vi của đối tượng thực tế. `clean()` biến `null`, chuỗi rỗng hoặc chuỗi chỉ có khoảng trắng thành `guest`; các tên khác được loại khoảng trắng đầu/cuối.

## 3. Khác biệt giữa default method và private instance method

| Đặc điểm | Default method | Private instance method |
| --- | --- | --- |
| Có thân phương thức | Có | Có |
| Gọi qua biến kiểu interface từ caller | Có | Không |
| Lớp triển khai kế thừa để gọi trực tiếp | Có, trừ khi bị ghi đè hoặc có xung đột | Không |
| Lớp triển khai override như một phương thức được kế thừa | Có | Không; helper private không được kế thừa |
| Mục đích | Hành vi mặc định trong API | Chia sẻ chi tiết triển khai nội bộ |

Caller có thể gọi `formatter.success("Mia")` nhưng không thể gọi `formatter.format("SUCCESS", "Mia")` vì `format()` là private. Lớp triển khai cũng không thể gọi helper qua `this` hay qua biến kiểu interface.

Nếu lớp triển khai tự khai báo phương thức cùng tên `format`, đó không phải override helper private của interface: hai phương thức không có quan hệ kế thừa. Lời gọi từ default method vẫn dùng helper trong interface. Private method phải có thân; không khai báo nó như phương thức trừu tượng và không kết hợp `private` với `default` trong cùng một khai báo.

## 4. Khi nào nên dùng private instance method?

Dùng nó khi nhiều default method cần cùng logic, phần logic là chi tiết triển khai và nó cần ngữ cảnh của đối tượng hiện tại. Private instance method có thể gọi phương thức instance khác trong interface, kể cả phương thức trừu tượng như `serviceName()`. Lời gọi đến phương thức trừu tượng sẽ được điều phối theo đối tượng thực tế.

Nếu hành vi phải được cung cấp bởi mọi lớp triển khai, hãy khai báo một phương thức hợp đồng công khai thay vì giấu hành vi đó trong helper private. Nếu logic chỉ được dùng một lần và việc tách helper làm luồng xử lý khó đọc hơn, không cần tạo thêm phương thức chỉ để tách một dòng mã.

## 5. Ranh giới sử dụng

**Private instance method** phù hợp khi helper cần ngữ cảnh đối tượng, như lời gọi `serviceName()` trong ví dụ. Nếu xử lý chỉ dựa trên tham số, một `private static` helper có thể đơn giản hơn; helper static không có `this` và chỉ gọi được hành vi instance khi có receiver cụ thể. Cả hai loại helper trong interface đều yêu cầu **Java 9 trở lên**.

Không nên tạo helper nếu việc tách hàm khiến luồng xử lý một lần dùng khó đọc hơn. Nếu nhiều interface **không liên quan** cùng cần một thuật toán, private helper của interface này không được interface khác gọi; hãy cân nhắc utility class hoặc thành phần dùng chung.

**Tự kiểm tra:** Vì sao `format()` cần là private *instance* method? Vì nó gọi `serviceName()` theo đối tượng `BillingFormatter` thực tế; một private static helper không có sẵn đối tượng đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="static-and-private-006">Private static method và giới hạn truy cập thành viên instance</a>

<details>
<summary>Xem chi tiết</summary>

# Private static method và giới hạn truy cập thành viên instance

Hai từ khóa `private` và `static` trả lời hai câu hỏi khác nhau. `private` quyết định nơi nào có quyền truy cập; `static` cho biết method thuộc về kiểu khai báo và không có instance hiện tại ngầm định. Vì thế, `private static` thường phù hợp với helper nội bộ chỉ cần tham số đầu vào.

## 1. Private static helper trong interface từ Java 9

Trong interface, `private static` gom xử lý nội bộ dùng được từ cả `default` method lẫn `static` method của chính interface. Nó không cần đối tượng nhận lời gọi nên thích hợp cho chuẩn hóa tham số hoặc kiểm tra đầu vào. Khác với một phương thức instance, nó không thể gọi trực tiếp `prefix()` hoặc dùng `this` vì không có đối tượng hiện tại.

Lưu chương trình sau thành `InterfacePrivateStaticDemo.java` và chạy với Java 9 trở lên:

~~~java
import java.util.Locale;

interface DisplayRule {
    String prefix();

    default String label(String value) {
        return prefix() + ": " + clean(value);
    }

    static String normalize(String value) {
        return clean(value);
    }

    static String labelFor(DisplayRule rule, String value) {
        return rule.prefix() + ": " + clean(value);
    }

    private static String clean(String value) {
        return value == null ? "(empty)" : value.trim().toUpperCase(Locale.ROOT);
    }
}

final class BasicDisplay implements DisplayRule {
    @Override
    public String prefix() {
        return "VIEW";
    }
}

public class InterfacePrivateStaticDemo {
    public static void main(String[] args) {
        DisplayRule rule = new BasicDisplay();
        System.out.println(rule.label(" java "));
        System.out.println(DisplayRule.normalize(" private "));
        System.out.println(DisplayRule.labelFor(rule, " api "));
    }
}
~~~

~~~bash
javac -encoding UTF-8 --release 9 InterfacePrivateStaticDemo.java
java InterfacePrivateStaticDemo
~~~

Stdout:

~~~text
VIEW: JAVA
PRIVATE
VIEW: API
~~~

`label` là instance default method nên gọi được `prefix()` trên đối tượng thực tế. `normalize` và `labelFor` là các static method công khai do `DisplayRule` khai báo; chúng đều gọi private static `clean`. Trong `labelFor`, biểu thức `rule.prefix()` hợp lệ vì `rule` cung cấp **receiver rõ ràng**, còn `prefix()` đứng một mình trong static method sẽ không biên dịch. Chính hai điều kiện độc lập — có receiver và có quyền truy cập — quyết định việc tham chiếu thành viên instance có hợp lệ hay không. Caller bên ngoài không thể gọi `DisplayRule.clean(...)` vì `clean` là private.

## 2. Ví dụ bổ trợ: helper tĩnh trong class

Lưu chương trình thành `PrivateStaticDemo.java`:

~~~java
import java.util.Locale;

public class PrivateStaticDemo {
    public static void main(String[] args) {
        Ticket first = new Ticket("  t-01  ");
        Ticket second = new Ticket("t-02");

        System.out.println(first.code() + " / " + second.code());
        System.out.println("Created: " + Ticket.createdCount());
    }
}

final class Ticket {
    private static int created;
    private final String code;

    Ticket(String rawCode) {
        this.code = normalize(rawCode);
        created++;
    }

    private static String normalize(String rawCode) {
        if (rawCode == null || rawCode.trim().isEmpty()) {
            throw new IllegalArgumentException("code is required");
        }
        return rawCode.trim().toUpperCase(Locale.ROOT);
    }

    String code() {
        return code;
    }

    static int createdCount() {
        return created;
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 PrivateStaticDemo.java
java PrivateStaticDemo
~~~

Stdout:

~~~text
T-01 / T-02
Created: 2
~~~

Constructor instance gọi được `normalize` vì nó nằm trong chính class `Ticket`. Helper chỉ cần chuỗi được truyền vào, nên không cần `this`. Trường `code` thuộc từng ticket, còn `created` là một biến static được chia sẻ bởi các đối tượng của class đó.

`Ticket.normalize("abc")` không được gọi từ `PrivateStaticDemo` vì helper là private. Quyền truy cập được kiểm tra theo nơi code xuất hiện; dùng cú pháp tên lớp bên trong chính `Ticket` vẫn hợp lệ.

Lưu ý, `created++` chỉ minh họa trạng thái static dùng chung; nó không phải bộ đếm an toàn khi nhiều thread cùng tăng giá trị.

## 3. Static context không có instance hiện tại

Một static method không có tham chiếu `this` ngầm định. Nếu muốn đọc số dư của một `Wallet`, Java cần biết đối tượng nào được đọc.

~~~java
class Wallet {
    private int balance;

    private int getBalance() {
        return balance;
    }

    private static int invalid() {
        // return balance;       // Lỗi: thiếu object nhận.
        // return this.balance;  // Lỗi: không có this trong static context.
        // return getBalance();  // Lỗi: thiếu object nhận.
        return 0;
    }

    private static int readBalance(Wallet wallet) {
        return wallet.balance;
    }
}
~~~

Ba dòng bị comment là các ví dụ cố ý sai. `readBalance` hợp lệ vì `wallet` chỉ rõ đối tượng nhận lời gọi. Code đó nằm trong chính class `Wallet` nên cũng có quyền truy cập trường private `balance`.

Hai điều kiện cần được kiểm tra độc lập:

- **Có receiver cụ thể không?** `wallet.balance` có; `balance` trần trong static method thì không.
- **Có quyền truy cập không?** Một tham chiếu đối tượng không tự cấp quyền đọc private field cho class không liên quan.

Vì vậy, nói “static method không bao giờ truy cập được instance member” là không chính xác. Nó không có instance hiện tại tự động, nhưng có thể xử lý một đối tượng được truyền vào hoặc được lấy từ một nguồn tham chiếu hợp lệ.

Nested class có thể truy cập private member của outer class theo quy tắc Java, nhưng static nested class không có `Outer.this`; nó vẫn cần một tham chiếu object cụ thể để đọc instance state.

## 4. Private static không phải private instance method

| Thành viên | Có `this` ngầm định? | Ví dụ phù hợp |
|---|---|---|
| `private void update() { ... }` | Có | Cập nhật trạng thái của đối tượng hiện tại |
| `private static String normalize(String value) { ... }` | Không | Chuẩn hóa tham số, kiểm tra dữ liệu, parse |
| `private static int total(Counter a, Counter b) { ... }` | Không; nhưng có receiver rõ ràng qua `a` và `b` | Tính toán dựa trên nhiều đối tượng được truyền vào |

Ví dụ, một helper static trong chính class có thể đọc private field của các instance được truyền vào:

~~~java
final class Counter {
    private final int value;

    Counter(int value) {
        this.value = value;
    }

    private static int total(Counter left, Counter right) {
        return left.value + right.value;
    }
}
~~~

`left.value` và `right.value` hợp lệ vì method có tham chiếu đến hai đối tượng cụ thể và đang ở trong class khai báo field. Nếu code nằm ngoài `Counter`, có tham chiếu `Counter` thôi vẫn không đủ quyền đọc `value` trực tiếp.

## 5. Kế thừa, hiding và interface

Private static method của class cha không tạo ra điểm override cho class con. Nếu class con tự khai báo một method cùng tên, đó là method riêng của class con. Với static method của class có thể truy cập và được kế thừa, class con có thể khai báo method tương ứng để *hide* method cha; cả hai vẫn không tham gia dynamic dispatch qua instance.

Trong interface, private static method cũng là helper nội bộ. Java hỗ trợ `private static` method trong interface từ **Java 9**; Java 8 chưa có private interface method. Helper đó phải có body, chỉ được gọi từ code có quyền truy cập bên trong interface khai báo nó, và không phải API cho class triển khai gọi trực tiếp.

Không nhầm private với static: một method có thể là private instance hoặc private static. Cũng không nhầm static với public: access modifier và ngữ cảnh instance là hai trục riêng.

## 6. Chọn đúng dạng method

- Dùng `private static` khi xử lý không cần trạng thái của `this` và các đầu vào cần thiết có thể truyền qua tham số.
- Dùng private instance method khi logic cần trạng thái của đối tượng hiện tại.
- Nếu một class khác cần gọi thao tác, thiết kế API công khai có chủ đích; đừng nới quyền private chỉ để tránh lỗi compiler.
- Nếu hành vi cần được subclass thay đổi, hãy tạo extension point bằng instance method phù hợp. Static/private helper không phải hook đa hình.
- Trạng thái mutable trong static field được chia sẻ; cân nhắc đồng bộ hoặc atomic nếu nhiều thread có thể cập nhật đồng thời.

**Tự kiểm tra**

1. Vì sao constructor gọi được private static `normalize`? Vì code constructor nằm trong class khai báo helper.
2. Vì sao `return balance` sai trong static method nhưng `return wallet.balance` có thể hợp lệ? Cách thứ hai chỉ rõ object nhận và quyền private được kiểm tra riêng.
3. Class con có override private static method của cha không? Không; method private không được kế thừa để override, và static method không phải instance override.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="static-and-private-007">Phân biệt private helper với điểm mở rộng cho lớp triển khai</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt private helper với điểm mở rộng cho lớp triển khai

Một API tốt tách rõ **hành vi mà implementation được phép quyết định** khỏi **chi tiết triển khai dùng chung**. Trong interface, abstract/default instance method có thể là một phần API và tham gia cơ chế override; private helper giữ logic nội bộ. Static method thuộc về kiểu khai báo, nên cũng không phải hook đa hình của instance.

## 1. So sánh vai trò

| Dạng method | Vai trò | Lớp triển khai có override được không? |
|---|---|---|
| `public abstract` instance method | Hợp đồng bắt buộc cho implementation cụ thể | Có; class concrete phải cung cấp implementation phù hợp |
| `public default` instance method | Hành vi mặc định mà implementation có thể thay đổi | Có |
| `public static` method trong interface | Tiện ích thuộc về interface, gọi qua tên interface | Không |
| `private` instance helper | Chi tiết nội bộ có ngữ cảnh instance | Không |
| `private static` helper | Chi tiết nội bộ không cần instance hiện tại | Không |
| `protected abstract` method trong abstract class | Điểm mở rộng dành cho class con | Có |

Private method trong interface được hỗ trợ từ Java 9. Các helper phải có thân hàm; chúng không phải hợp đồng cho class triển khai. Còn một method abstract/default công khai được thiết kế như extension point khi API thật sự muốn cho implementation cung cấp hoặc thay đổi hành vi.

Hai interface không liên quan có thể khai báo private helper trùng tên và chữ ký mà không xung đột, vì helper private không được kế thừa. Ngược lại, nếu cả hai cùng công bố default method cùng chữ ký, class triển khai có thể phải override để giải quyết xung đột.

## 2. Ví dụ hoàn chỉnh: hook và helper cùng tồn tại

Lưu mã sau thành `InterfaceHelperDemo.java` và chạy bằng Java 17 trở lên:

~~~java
import java.util.Locale;

interface MessageView {
    String transform(String raw);

    default String render(String raw) {
        String checked = requireText(raw);
        return "Result: " + trimResult(transform(checked));
    }

    private String trimResult(String text) {
        return text.trim();
    }

    private static String requireText(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        return raw;
    }

    static String kind() {
        return "MessageView";
    }
}

final class UpperMessageView implements MessageView {
    @Override
    public String transform(String raw) {
        return raw.toUpperCase(Locale.ROOT);
    }

    public String trimResult(String text) {
        return "Class method: " + text;
    }
}

public class InterfaceHelperDemo {
    public static void main(String[] args) {
        MessageView view = new UpperMessageView();

        System.out.println(view.render("  java  "));
        System.out.println(new UpperMessageView().trimResult("test"));
        System.out.println(MessageView.kind());

        try {
            view.render(null);
        } catch (IllegalArgumentException ex) {
            System.out.println("Rejected: " + ex.getMessage());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 --release 17 InterfaceHelperDemo.java
java InterfaceHelperDemo
~~~

Stdout:

~~~text
Result: JAVA
Class method: test
MessageView
Rejected: text must not be null
~~~

### Đọc ví dụ theo từng vai trò

- `transform` là extension point: `UpperMessageView` phải cung cấp implementation công khai. Khi `render` gọi `transform(checked)`, Java dispatch đến implementation của đối tượng thực tế.
- `render` là default method chứa quy trình dùng chung. Một class triển khai có thể override nó nếu API cho phép thay đổi toàn bộ quy trình.
- `trimResult` của interface là private helper. `render` gọi helper này trong interface, nên kết quả là `Result: JAVA`. Method công khai `trimResult` trong class là một method độc lập; nó không override private helper của interface.
- `requireText` là private static helper để kiểm tra đầu vào. Caller bên ngoài không thể gọi `MessageView.requireText(...)`.
- `MessageView.kind()` là public static method của interface. Nó được gọi bằng tên interface, không qua `view`.

Nếu thêm `@Override` vào `UpperMessageView.trimResult`, compiler sẽ từ chối vì không có method tương ứng được kế thừa để override. Tên và chữ ký giống nhau chưa đủ để tạo quan hệ override; phải có một method gốc có thể được override theo quy tắc Java.

## 3. Khi cần một extension point thật sự

Nếu mọi implementation phải tự quyết định cách biến đổi dữ liệu, hãy công bố hành vi đó trong hợp đồng:

~~~java
interface Transformer {
    String transform(String input);
}

final class UpperTransformer implements Transformer {
    @Override
    public String transform(String input) {
        return input.toUpperCase(java.util.Locale.ROOT);
    }
}
~~~

`transform` là method abstract công khai; implementation cụ thể có nghĩa vụ cung cấp nó. Nếu chỉ muốn tái sử dụng một bước chuẩn hóa giữa các default method, dùng private helper sẽ tránh thêm method công khai mà người dùng API có thể phụ thuộc vào.

Default method nằm giữa hai nhu cầu: nó cung cấp implementation dùng chung nhưng vẫn có thể override. Nếu muốn bảo vệ toàn bộ quy trình mặc định, hãy cân nhắc xem API có nên cho phép override default method đó hay không; private helper chỉ khóa phần helper, không tự khóa phương thức public đang gọi nó.

## 4. Khi abstract class phù hợp hơn

Abstract class cho phép thiết kế một template method cố định và một hook dành cho class con:

~~~java
abstract class BaseWorkflow {
    public final String run(String input) {
        return normalize(step(input));
    }

    private String normalize(String value) {
        return value.trim();
    }

    protected abstract String step(String input);
}

final class EchoWorkflow extends BaseWorkflow {
    @Override
    protected String step(String input) {
        return " " + input + " ";
    }
}
~~~

Đây là các khai báo lớp hợp lệ trên Java 17, chưa có `main`. `run` là `public final` nên class con không override được khung thuật toán. `step` là `protected abstract` hook mà class con phải cung cấp. `normalize` là private helper của lớp cha, được dùng sau khi hook trả về kết quả.

Dùng interface khi cần mô tả hợp đồng và có thể cung cấp default behavior. Cân nhắc abstract class khi cần trạng thái instance, constructor hoặc một template method với hook được kiểm soát rõ hơn.

## 5. Quy tắc quyết định

- **Implementation phải quyết định hành vi:** khai báo abstract instance method công khai trong interface.
- **Cần hành vi mặc định có thể thay đổi:** dùng default method.
- **Cần chia sẻ chi tiết nội bộ giữa các default method:** dùng private instance helper.
- **Cần chia sẻ logic tĩnh nội bộ:** dùng private static helper.
- **Cần tiện ích thuộc về interface:** dùng public static method và gọi bằng tên interface.
- **Cần giữ một quy trình lớp cha cố định nhưng cho phép class con thay một bước:** cân nhắc template method `final` cùng hook `protected` trong abstract class.

Không đặt helper thành public chỉ để hai method gọi chung nó. Ngược lại, đừng đặt một hành vi bắt buộc cho implementation thành private helper: class triển khai không thể dùng helper đó làm hợp đồng hoặc override nó.

**Tự kiểm tra:** Nếu class triển khai cần thay đổi kết quả của một bước, hãy xác định bước đó có thật sự là extension point không. Nếu có, công bố method phù hợp; nếu không, giữ nó sau ranh giới private để implementation không phụ thuộc vào chi tiết nội bộ.

</details>

- [Quay lại đầu trang](#back-to-top)
