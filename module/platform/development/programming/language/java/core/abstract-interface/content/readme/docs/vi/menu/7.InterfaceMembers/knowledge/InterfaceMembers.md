<a id="back-to-top"></a>

# Thành viên interface

## Menu
- [Phân loại phương thức abstract, default, static và private](#interface-members-001)
- [Phương thức abstract ngầm định public](#interface-members-002)
- [Thuộc tính ngầm định public static final và bắt buộc khởi tạo](#interface-members-003)
- [Phân biệt thuộc tính final với đối tượng bất biến](#interface-members-004)
- [Hằng thời điểm biên dịch và thuộc tính cần khởi tạo lúc chạy](#interface-members-005)
- [Truy cập hằng qua tên interface và xử lý tên hằng trùng nhau](#interface-members-006)
- [Giới hạn truy cập và modifier không hợp lệ](#interface-members-007)
- [Giới hạn tham chiếu thuộc tính và thứ tự khai báo](#interface-members-008)

## <a id="interface-members-001">Phân loại phương thức abstract, default, static và private</a>

<details>
<summary>Xem chi tiết</summary>

# Phân loại phương thức `abstract`, `default`, `static` và `private` trong Java interface

## 1. Vì sao interface có nhiều loại phương thức?

Trong Java, `interface` xác định hợp đồng mà các lớp thực hiện (`implements`) phải tuân theo. Từ Java 8, interface không chỉ chứa các phương thức trừu tượng mà còn có thể cung cấp mã triển khai bằng phương thức `default` và `static`. Từ Java 9, interface có thể khai báo thêm các phương thức `private` để tái sử dụng logic nội bộ.

Bốn nhóm phương thức cần phân biệt theo **có thân hay không**, **cách gọi**, **phạm vi truy cập** và **khả năng kế thừa/ghi đè**.

| Loại phương thức | Cú pháp điển hình | Thân phương thức | Phạm vi truy cập | Cách sử dụng |
|---|---|---|---|---|
| `abstract` | `void execute();` hoặc `public abstract void execute();` | **Không có**, kết thúc bằng `;` | Luôn là `public` | Lớp thực hiện phải cung cấp triển khai nếu chưa có triển khai phù hợp được kế thừa và là lớp cụ thể. |
| `default` | `default void log() { ... }` | **Bắt buộc có** | Luôn là `public` | Gọi trên đối tượng triển khai interface; lớp triển khai có thể override. |
| `static` | `static boolean valid() { ... }` | **Bắt buộc có** | Mặc định là `public`; có thể khai báo `private static` để dùng nội bộ | Phương thức công khai được gọi qua **tên interface**; không được kế thừa như phương thức instance. |
| `private` (instance) | `private void helper() { ... }` | **Bắt buộc có** | Chỉ trong chính interface | Hỗ trợ mã dùng chung giữa các phương thức instance có thân, không phải API bên ngoài. |
| `private static` | `private static String normalize(...) { ... }` | **Bắt buộc có** | Chỉ trong chính interface | Hàm trợ giúp nội bộ dùng được từ cả ngữ cảnh `static` và ngữ cảnh instance của interface. |

**Mốc phiên bản:** `abstract` thuộc mô hình interface từ đầu; `default` và `static` có thân được hỗ trợ từ **Java 8**; `private` và `private static` trong interface được hỗ trợ từ **Java 9**. Tất cả ví dụ dưới đây dùng cú pháp hợp lệ trong **Java 17+**.

## 2. Phương thức `abstract`: hợp đồng chưa có cách triển khai

Phương thức interface không có thân được hiểu ngầm là **`public abstract`**. Chẳng hạn, `boolean pay(long amount);` dưới đây là hợp đồng công khai dù không viết hai modifier:

~~~java
interface PaymentMethod {
    boolean pay(long amount);
}

final class CardPayment implements PaymentMethod {
    @Override
    public boolean pay(long amount) {
        return amount > 0; // Minh họa một quy tắc kiểm tra tối giản
    }
}
~~~

Đoạn này minh họa khai báo hợp lệ, chưa có `main`. Một class cụ thể phải đáp ứng các phương thức trừu tượng còn thiếu bằng triển khai tương thích có quyền truy cập **`public`**; class `abstract` có thể hoãn việc này. Phương thức `abstract` không có thân và không được kết hợp `private`, `protected`, `final` hay `static`.

Phương thức công khai đã được kế thừa từ `Object`, ví dụ `equals(Object)`, có thể đáp ứng một khai báo abstract tương ứng trong interface. Để kiểm chứng modifier bằng reflection và phân biệt với abstract method của class, xem bài **Phương thức abstract ngầm định public**.
## 3. Phương thức `default`: triển khai mặc định cho đối tượng

`default` cho phép interface khai báo **phương thức instance có thân**. Lớp triển khai có thể sử dụng hành vi có sẵn hoặc override nếu cần:

~~~java
interface Named {
    String name();

    default String displayName() {
        return "Tên: " + name();
    }
}

class User implements Named {
    @Override
    public String name() {
        return "Lan";
    }
}
~~~

Ở đây `name()` là `public abstract` ngầm định. `displayName()` là `public default` ngầm định về phạm vi truy cập; nó gọi `name()` và nhận được triển khai của đối tượng thực tế. Đối tượng `new User()` có thể gọi `displayName()` dù lớp `User` không tự viết phương thức này.

Quy tắc quan trọng:

1. Phương thức `default` **phải có thân**, được viết với từ khóa `default` và có quyền truy cập `public` (ngầm định hoặc tường minh).
2. Đây là **phương thức instance**; lời gọi được thực hiện qua đối tượng như `user.displayName()`, có thể tham gia đa hình và được lớp triển khai override.
3. Khi lớp override một phương thức `default`, phương thức đó vẫn phải là `public`. Nếu lớp triển khai trực tiếp interface và muốn gọi bản mặc định từ phương thức override, có thể dùng `InterfaceName.super.method()` theo các điều kiện cú pháp Java.
4. `default` không được đi cùng `abstract`, `static` hoặc `private` trên cùng phương thức.
5. Interface không được cung cấp phương thức `default` override những phương thức `public` của `Object`, chẳng hạn `default String toString() { ... }`. Lớp thực hiện vẫn có thể tự override `toString()` như bình thường.

Một ứng dụng là **bổ sung hành vi mặc định khi phát triển API interface** mà không bắt tất cả lớp hiện tại phải khai báo ngay phương thức mới. Tuy nhiên, việc thêm `default` vẫn có thể tạo xung đột với những interface khác mà một lớp đang thực hiện; đó không phải bảo đảm tương thích trong mọi trường hợp.

## 4. Phương thức `static`: tiện ích gắn với chính interface

Phương thức `static` có thân và **không phụ thuộc vào một đối tượng triển khai**:

~~~java
interface TextRules {
    static boolean hasText(String input) {
        return input != null && !input.isBlank();
    }
}
~~~

Gọi đúng:

~~~java
boolean result = TextRules.hasText("Java");
~~~

`result` có giá trị `true`. Với phương thức `static` công khai trong interface, tên interface là điểm truy cập rõ ràng. Nếu có biến kiểu `TextRules`, gọi `variable.hasText(...)` sẽ không hợp lệ; một lớp `implements TextRules` cũng **không kế thừa** phương thức `hasText` để gọi như phương thức static của chính lớp đó. Tương tự, một subinterface không được thừa kế phương thức static từ superinterface theo cách kế thừa phương thức instance.

Một phương thức `static` của interface:

- Có thể gọi trực tiếp các phương thức static khác trong interface, kể cả `private static`.
- Không thể gọi trực tiếp phương thức instance `default` hoặc `private` instance khi không có đối tượng thích hợp.
- Không được override theo nghĩa đa hình của phương thức instance; việc trùng tên với phương thức static khác không biến phương thức đó thành `default` hoặc `abstract`.
- Khi không ghi modifier truy cập, nó là `public static`; muốn chỉ dùng nội bộ thì ghi **`private static`** (Java 9+).

Dùng `static` khi hàm liên quan chặt chẽ đến hợp đồng interface, chẳng hạn thao tác tạo đối tượng, kiểm tra đối số hoặc chuyển đổi độc lập với trạng thái instance.

## 5. Phương thức `private` và `private static`: mã hỗ trợ nội bộ

Java 9 cho phép interface ẩn những phương thức có thân, tránh phải lặp logic trong nhiều `default`/`static` methods:

~~~java
interface Formatter {
    default String emphasize(String text) {
        return wrap(text);
    }

    default String label(String text) {
        return "Nhãn " + wrap(text);
    }

    static String normalize(String text) {
        return trim(text).toLowerCase(java.util.Locale.ROOT);
    }

    private String wrap(String text) {
        return "[" + trim(text) + "]";
    }

    private static String trim(String text) {
        return text.trim();
    }
}
~~~

Phân biệt hai dạng:

- `private String wrap(String text)` là **phương thức instance**. Nó chỉ được gọi từ mã trong interface với ngữ cảnh instance thích hợp; ví dụ, cả `emphasize` và `label` gọi được nó.
- `private static String trim(String text)` là **phương thức static**. Cả phương thức instance `wrap` lẫn phương thức static `normalize` đều có thể gọi nó.
- Phương thức `private` và `private static` **phải có thân**, không thể đồng thời là `abstract` hoặc `default`; chúng không phải thành viên API có thể gọi từ lớp triển khai.
- Lớp triển khai **không kế thừa** và không được override phương thức `private` của interface. Viết cùng tên trong lớp triển khai là một phương thức khác, không phải ghi đè.
- Phương thức `private` interface không làm tăng số phương thức trừu tượng phải triển khai, và không tham gia hợp đồng triển khai của `implements`.

Nếu chỉ có một đoạn helper duy nhất và không cần ẩn phần triển khai, đôi khi viết logic ngay trong `default`/`static` sẽ đơn giản hơn. Dùng `private` khi việc đặt tên một bước xử lý hoặc tái sử dụng logic thực sự làm mã dễ hiểu hơn.

## 6. Ví dụ tổng hợp đầy đủ: đủ bốn loại phương thức

Trong ví dụ sau, `Notifier` có phương thức `abstract`, `default`, `static`, `private` instance và `private static`. Một lớp sử dụng bản mặc định, một lớp chủ động override bản mặc định.

Lưu **toàn bộ** mã vào tệp **`Demo.java`**:

~~~java
import java.util.Locale;

interface Notifier {
    // Ngầm định public abstract.
    String recipient();

    // Ngầm định public; có thân và là phương thức instance.
    default String message() {
        return decorate(recipient());
    }

    // Ngầm định public; gọi qua Notifier.supports(...).
    static boolean supports(String languageCode) {
        return normalize(languageCode).equals("vi");
    }

    // Java 9+: chỉ dùng trong chính interface.
    private String decorate(String rawName) {
        return "Xin chào " + rawName.trim() + "!";
    }

    // Java 9+: helper dùng được từ static và instance methods.
    private static String normalize(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }
}

final class StandardNotifier implements Notifier {
    private final String name;

    StandardNotifier(String name) {
        this.name = name;
    }

    @Override
    public String recipient() {
        return name;
    }
}

final class CustomNotifier implements Notifier {
    @Override
    public String recipient() {
        return "Mai";
    }

    @Override
    public String message() {
        return "Thông báo dành cho " + recipient();
    }
}

public class Demo {
    public static void main(String[] args) {
        Notifier standard = new StandardNotifier(" Lan ");
        Notifier custom = new CustomNotifier();

        System.out.println(standard.recipient().trim());
        System.out.println(standard.message());
        System.out.println(custom.message());
        System.out.println(Notifier.supports(" VI "));
    }
}
~~~

Biên dịch/chạy bằng Java 17+:

~~~bash
javac Demo.java
java Demo
~~~

**Kết quả stdout chính xác:**

~~~text
Lan
Xin chào Lan!
Thông báo dành cho Mai
true
~~~

Phân tích dòng gọi:

1. `standard.recipient()` gọi triển khai **abstract contract** của `StandardNotifier`; `trim()` trong lệnh in loại khoảng trắng đầu/cuối nên in `Lan`.
2. `standard.message()` sử dụng phương thức **default** từ `Notifier`. Bên trong `message()` gọi `decorate(...)` **private instance**; phương thức đó lấy `recipient()` của đối tượng thực và loại khoảng trắng.
3. `custom.message()` gọi bản **override** của `CustomNotifier` nên không sử dụng nội dung `message()` mặc định.
4. `Notifier.supports(" VI ")` gọi phương thức **static công khai** qua tên interface. Nó gọi `normalize(...)` **private static**: bỏ khoảng trắng, chuyển chữ thường theo `Locale.ROOT`, sau đó so với `"vi"` nên trả về `true`.

Các lời gọi sau **không hợp lệ** bên ngoài `Notifier` (minh họa lỗi, không đưa vào `Demo.java`):

~~~java
// standard.supports("vi");        // Lỗi: static method không gọi qua instance
// StandardNotifier.supports("vi"); // Lỗi: static method interface không kế thừa vào lớp
// standard.decorate("Lan");       // Lỗi: private interface method không truy cập được
// Notifier.normalize("vi");       // Lỗi: private static method không truy cập được
~~~

## 7. Kế thừa, ghi đè và xung đột `default` methods

Khi nhiều interface có phương thức cùng chữ ký, hiểu quy tắc chọn triển khai là rất quan trọng:

1. **Phương thức instance cụ thể từ lớp cha được ưu tiên** so với triển khai `default` tương ứng của interface, nếu chữ ký và kiểu trả về tương thích.
2. Trong hệ interface có quan hệ kế thừa, triển khai `default` từ interface **cụ thể hơn** thường được ưu tiên so với `default` ở superinterface theo quy tắc Java.
3. Nếu một lớp nhận **hai triển khai `default` không liên quan** cùng chữ ký từ hai interface, nó phải giải quyết xung đột bằng cách override rõ ràng.
4. Phương thức `abstract` vẫn quy định hợp đồng; một `default` phù hợp có thể đáp ứng hợp đồng đó trong những cấu trúc kế thừa hợp lệ.
5. `static` interface và `private` interface không phải đối tượng của quá trình override `default` theo đa hình.

Ví dụ xung đột `default`, lưu riêng trong **`ConflictDemo.java`**:

~~~java
interface Left {
    default String label() {
        return "L";
    }
}

interface Right {
    default String label() {
        return "R";
    }
}

final class Combined implements Left, Right {
    @Override
    public String label() {
        return Left.super.label() + "+" + Right.super.label();
    }
}

public class ConflictDemo {
    public static void main(String[] args) {
        System.out.println(new Combined().label());
    }
}
~~~

Chạy:

~~~bash
javac ConflictDemo.java
java ConflictDemo
~~~

Stdout:

~~~text
L+R
~~~

Nếu bỏ `Combined.label()`, mã sẽ không biên dịch vì lớp nhận hai phương thức `default` cùng chữ ký mà chưa chỉ ra cách giải quyết. Cú pháp `Left.super.label()` và `Right.super.label()` cho phép gọi các triển khai mặc định cụ thể trong ngữ cảnh kế thừa hợp lệ. Cũng có thể viết override mới hoàn toàn, không cần gọi một trong hai bản mặc định.

**Chú ý:** chỉ có phương thức cụ thể từ lớp cha là đủ để giải quyết ưu tiên theo quy tắc “class wins” thông thường. Nếu lớp cha để lại một phương thức trừu tượng cùng chữ ký hoặc các chữ ký/kiểu trả về không tương thích, có thể vẫn cần triển khai rõ ràng và xử lý lỗi theo quy tắc Java; đừng suy luận rằng mọi khai báo trong class tự động giải quyết mọi xung đột.

## 8. Lỗi phổ biến khi phân loại phương thức trong interface

| Viết sai hoặc hiểu sai | Nguyên nhân | Cách sửa |
|---|---|---|
| `interface A { void run() {} }` | Phương thức thiếu modifier loại có thân được hiểu là abstract. | Viết `void run();` hoặc `default void run() {}`. |
| `interface A { default void run(); }` | `default` phải có thân. | Thêm `{ ... }` hoặc khai báo abstract. |
| `interface A { private void helper(); }` | `private` interface phải có thân. | Viết `private void helper() { ... }`. |
| `interface A { static void work(); }` | `static` interface phải có thân. | Viết `static void work() { ... }`. |
| `new Notifier()` | Không thể khởi tạo trực tiếp interface. | Khởi tạo lớp triển khai, ví dụ `new StandardNotifier("Lan")`. |
| `StandardNotifier.supports("vi")` | Phương thức `static` của interface không được kế thừa vào class. | Gọi `Notifier.supports("vi")`. |
| Hai interface không liên quan có cùng `default void f()` mà lớp triển khai không override | Hai triển khai mặc định xung đột. | Override `public void f()` để giải quyết. |
| `default String toString() { ... }` | `default` không được override phương thức public của `Object`. | Để lớp triển khai tự override `toString()`. |

Các mẫu sai chỉ minh họa một lỗi, không phải tệp Java hoàn chỉnh. Bài **Giới hạn truy cập và modifier không hợp lệ** có ma trận riêng cho `private`, `protected`, `final` và các tổ hợp modifier bị cấm; bài **Phương thức abstract ngầm định public** đi sâu vào việc class triển khai giữ quyền `public`.
## 9. Cách chọn đúng loại phương thức

- Chọn **`abstract`** khi định nghĩa **hành vi bắt buộc** nhưng không có triển khai chung: ví dụ `save()`, `calculate()`, `validate()`.
- Chọn **`default`** khi có **hành vi instance dùng chung** cho các đối tượng triển khai và có thể cho phép lớp triển khai override. Cẩn trọng với xung đột khi interface được dùng chung hoặc kế thừa từ nhiều nhánh.
- Chọn **`static`** khi có **hàm công khai liên quan đến interface** nhưng không cần nhận đối tượng triển khai, ví dụ kiểm tra định dạng đầu vào hoặc factory method.
- Chọn **`private` instance** khi cần trích ra logic hỗ trợ cho các **`default` hoặc phương thức instance có thân** của cùng interface.
- Chọn **`private static`** khi helper không phụ thuộc vào đối tượng và cần dùng nội bộ, kể cả từ những phương thức `static` công khai.

Ghi nhớ sự khác nhau giữa **kế thừa** và **khả năng truy cập**: một phương thức `default` được lớp triển khai thừa hưởng dưới dạng hành vi instance; phương thức `static` công khai có thể được gọi từ mã bên ngoài nhưng qua **tên interface**, không được kế thừa vào lớp triển khai; phương thức `private` tồn tại chỉ để hỗ trợ việc triển khai bên trong interface.

Một interface được đánh dấu `@FunctionalInterface` vẫn có thể chứa `default`, `static` và `private` methods. Những phương thức có thân này **không tính là phương thức trừu tượng duy nhất** (single abstract method, SAM) của functional interface. Điều kiện chính của functional interface là chỉ có một hợp đồng phương thức trừu tượng phù hợp sau khi xét các quy tắc của Java.

### Tự kiểm tra

1. `void run();` trong interface có phạm vi gì? — **`public abstract` ngầm định**.
2. Lớp triển khai có bắt buộc viết `default void log()` không? — **Không**, nếu bản mặc định thỏa mãn và không có xung đột; vẫn có thể override.
3. `MyInterface.check()` và `object.check()`: dạng nào đúng khi `check` là `static` công khai của interface? — **`MyInterface.check()`**.
4. Một phương thức `private` của interface có thể bị lớp triển khai override không? — **Không**, vì nó không được kế thừa và chỉ truy cập được bên trong interface.
5. Khi nào dùng `private static` thay cho `private` instance? — Khi helper **không cần ngữ cảnh đối tượng**, nhất là khi phương thức `static` của interface cũng cần gọi helper đó.

**Điểm cốt lõi:** `abstract` = hợp đồng chưa có thân; `default` = triển khai instance có thể override; `static` = hành vi gắn với tên interface; `private`/`private static` = logic trợ giúp chỉ dùng nội bộ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-members-002">Phương thức abstract ngầm định public</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức abstract ngầm định public trong Java interface

## 1. Quy tắc ngôn ngữ

Một phương thức được khai báo trong interface mà không có thân và không dùng modifier dành cho phương thức có thân sẽ mặc nhiên là **public abstract**. Hai từ khóa có thể được viết rõ hoặc lược bỏ mà không đổi ý nghĩa.

~~~java
interface Formatter {
    String format(String text);
}
~~~

Khai báo trên tương đương với:

~~~java
interface Formatter {
    public abstract String format(String text);
}
~~~

**public** khiến phương thức trở thành phần công khai của hợp đồng. **abstract** cho biết interface chỉ nêu chữ ký, còn cách xử lý được cung cấp bởi lớp cụ thể hoặc bởi một triển khai phù hợp được kế thừa. Dấu chấm phẩy kết thúc khai báo vì không có thân phương thức.

## 2. Vì sao lớp triển khai phải dùng public?

Một lớp cụ thể thực hiện hợp đồng này phải cung cấp phương thức có chữ ký tương thích và không được giảm quyền truy cập.

~~~java
interface Printer {
    void print(String message);
}

final class ConsolePrinter implements Printer {
    @Override
    public void print(String message) {
        System.out.println(message);
    }
}
~~~

Ví dụ sau không hợp lệ:

~~~java
class BrokenPrinter implements Printer {
    @Override
    void print(String message) {
        System.out.println(message);
    }
}
~~~

Trong class, phương thức không ghi modifier truy cập là package-private. Quy tắc ngầm định public chỉ áp dụng cho phương thức abstract trong interface; nó không tự nâng quyền truy cập của phương thức trong class. Dùng protected hoặc private cũng không thể đáp ứng hợp đồng public.

Lớp được khai báo abstract có thể hoãn một số triển khai. Khi tạo lớp cụ thể, mọi phương thức abstract còn thiếu phải được đáp ứng bằng triển khai hợp lệ.

## 3. Quan sát modifier bằng reflection

Lưu mã sau trong tệp **InterfaceAbstractDemo.java**:

~~~java
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

interface Formatter {
    String format(String text);
}

final class UppercaseFormatter implements Formatter {
    @Override
    public String format(String text) {
        return text.toUpperCase(Locale.ROOT);
    }
}

public class InterfaceAbstractDemo {
    public static void main(String[] args) throws NoSuchMethodException {
        Formatter formatter = new UppercaseFormatter();
        System.out.println(formatter.format("java"));

        Method method = Formatter.class.getDeclaredMethod("format", String.class);
        System.out.println(Modifier.toString(method.getModifiers()));
        System.out.println(Modifier.isPublic(method.getModifiers()));
        System.out.println(Modifier.isAbstract(method.getModifiers()));
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac InterfaceAbstractDemo.java
java InterfaceAbstractDemo
~~~

Kết quả:

~~~text
JAVA
public abstract
true
true
~~~

Reflection xác nhận modifier thực tế của phương thức được khai báo trong interface. Lời gọi formatter.format("java") vẫn chạy phần triển khai của đối tượng UppercaseFormatter; interface quy định hợp đồng, còn dynamic dispatch chọn phần override theo đối tượng thực.

## 4. So với phương thức abstract trong abstract class

| Khai báo | Quyền truy cập khi bỏ modifier | Nơi có thể cung cấp triển khai |
| --- | --- | --- |
| Phương thức abstract trong interface | public | Class cụ thể hoặc triển khai tương thích được kế thừa |
| Phương thức abstract trong class | package-private | Class con, tuân theo quyền truy cập đã khai báo |

~~~java
interface Task {
    void execute();             // public abstract
}

abstract class BaseTask {
    abstract void execute();    // package-private abstract
}
~~~

Phương thức trong BaseTask có thể được class con cùng package override mà giữ package-private; nếu muốn class con ở package khác có thể triển khai, thiết kế quyền truy cập của class cha cần phù hợp, chẳng hạn protected hoặc public. Interface không cho phép dùng protected cho phương thức của nó.

## 5. Hai trường hợp cần hiểu đúng

**Phương thức tương ứng với Object.** Một class cụ thể có thể đã kế thừa public equals(Object), hashCode() hoặc toString() từ Object. Phương thức kế thừa phù hợp có thể đáp ứng khai báo interface tương ứng; không phải lúc nào class cũng cần viết lại một bản override riêng.

**Functional interface.** Các phương thức default, static và private có thân không làm tăng số hợp đồng abstract cần triển khai. Những phương thức tương ứng với public methods của Object được xử lý theo quy tắc riêng khi xác định functional interface.

~~~java
@FunctionalInterface
interface Validator {
    boolean isValid(String text);

    default boolean isNonEmpty(String text) {
        return text != null && isValid(text);
    }
}
~~~

Lambda có thể cung cấp isValid; phương thức default vẫn có sẵn. Để phân biệt chi tiết các loại phương thức có thân, xem bài phân loại phương thức trong interface.

## 6. Lỗi thường gặp

| Lỗi | Vì sao | Cách sửa |
| --- | --- | --- |
| Khai báo protected hoặc private abstract method trong interface | Phương thức abstract của interface là public | Bỏ modifier không hợp lệ hoặc thiết kế helper private có thân từ Java 9 |
| Bỏ public khi triển khai trong class | Phương thức class trở thành package-private | Ghi public rõ ràng |
| Cho rằng mọi phương thức interface đều abstract | default, static và private có thân là các dạng khác | Xem cả modifier và việc có thân hay không |
| Cho rằng tên tham số khác làm thay đổi hợp đồng | Tên tham số không quyết định việc override | So sánh tên phương thức, tham số, kiểu trả về và quy tắc ngoại lệ |

### Tự kiểm tra

1. Khai báo **void run();** trong interface có modifier nào? **public abstract**.
2. Lớp cụ thể implements interface có thể viết **void run() {}** không? **Không**, vì đó là package-private.
3. Lớp abstract có thể hoãn triển khai không? **Có**, miễn lớp vẫn được khai báo abstract và lớp cụ thể sau cùng hoàn thiện hợp đồng.
4. **default void log() {}** có phải phương thức abstract không? **Không**, nó có thân và là phương thức instance công khai.

Điểm cần nhớ: modifier public vẫn có hiệu lực dù không xuất hiện trong mã nguồn. Khi triển khai interface, hãy đáp ứng đúng hợp đồng công khai và dùng @Override để trình biên dịch phát hiện chữ ký sai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-members-003">Thuộc tính ngầm định public static final và bắt buộc khởi tạo</a>

<details>
<summary>Xem chi tiết</summary>

# Thuộc tính interface ngầm định public static final và bắt buộc khởi tạo

## 1. Ba modifier mặc định

Mọi field được khai báo trực tiếp trong interface đều ngầm định **public static final**.

~~~java
interface ConnectionOptions {
    int DEFAULT_PORT = 8080;
    String PROTOCOL = "https";
}
~~~

Các field trên có cùng modifier như thể viết tường minh:

~~~java
interface ConnectionOptions {
    public static final int DEFAULT_PORT = 8080;
    public static final String PROTOCOL = "https";
}
~~~

| Modifier | Ý nghĩa |
| --- | --- |
| public | Mã có quyền truy cập interface có thể đọc field |
| static | Field thuộc về interface, dùng chung ở cấp kiểu |
| final | Field không thể được gán lại sau khi khởi tạo |

Vì field là static, cách đọc rõ nghĩa là ConnectionOptions.DEFAULT_PORT. Không cần tạo instance và không có field riêng cho từng đối tượng implements interface.

## 2. Initializer là bắt buộc

Mỗi field interface phải có biểu thức khởi tạo ngay tại khai báo:

~~~java
interface ServerConfig {
    int TIMEOUT_SECONDS = 30;
    String HOST = "localhost";
}
~~~

Đoạn sau không hợp lệ:

~~~java
interface BrokenConfig {
    int TIMEOUT_SECONDS;
}
~~~

Interface không có constructor instance để gán field sau này và không cho khai báo khối static initializer kiểu class. Constructor của class implements cũng không thể gán field của interface, vì field đó là static final thuộc interface.

Điều kiện “có initializer” không có nghĩa giá trị phải là hằng thời điểm biên dịch. Ví dụ, **int TIMEOUT = Integer.parseInt("1500");** có initializer hợp lệ nhưng được tính khi interface khởi tạo. Bài về hằng thời điểm biên dịch giải thích kỹ hơn sự khác nhau này.

## 3. Ví dụ chạy được

Lưu vào **InterfaceFieldRulesDemo.java**:

~~~java
interface Limits {
    int MAX_RETRIES = 3;
    String DEFAULT_MODE = "SAFE";
    long MAX_BYTES = 1_024L * 1_024;
}

public class InterfaceFieldRulesDemo {
    public static void main(String[] args) {
        System.out.println(Limits.MAX_RETRIES);
        System.out.println(Limits.DEFAULT_MODE);
        System.out.println(Limits.MAX_BYTES);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac InterfaceFieldRulesDemo.java
java InterfaceFieldRulesDemo
~~~

Kết quả:

~~~text
3
SAFE
1048576
~~~

Tên interface trong biểu thức truy cập cho thấy nguồn khai báo của giá trị. Không cần implements Limits để đọc các field public. Chỉ implements một interface khi class thực sự đáp ứng hợp đồng mà interface đại diện.

## 4. Field interface không phải trạng thái instance

~~~java
interface PortDefaults {
    int PORT = 8080;
}

final class HttpClient {
    int defaultPort() {
        return PortDefaults.PORT;
    }
}
~~~

Mọi nơi truy cập PortDefaults.PORT đều đọc cùng field tĩnh đó. Nếu mỗi client cần cổng riêng, hãy đặt một field instance trong class và gán qua constructor, thay vì cố dùng field interface cho trạng thái từng đối tượng.

Field interface cũng không tự làm đối tượng tham chiếu trở nên bất biến. Ví dụ, field trỏ đến mảng vẫn có thể cho phép sửa phần tử; bài về final và immutability tập trung vào ranh giới này.

## 5. Modifier không hợp lệ

Các ví dụ sau minh họa từng lỗi riêng biệt, không ghép chung thành một chương trình:

~~~java
interface InvalidAccess {
    private int PORT = 8080;      // Sai: field interface không thể private
}
~~~

~~~java
interface InvalidVisibility {
    protected int PORT = 8080;    // Sai: field interface không thể protected
}
~~~

~~~java
interface MissingValue {
    int PORT;                     // Sai: thiếu initializer
}
~~~

~~~java
interface InvalidInitializer {
    int PORT = 8080;
    static {
        System.out.println("init"); // Sai: interface không có static initializer block
    }
}
~~~

Các modifier transient và volatile cũng không phù hợp với field interface. Không thể loại bỏ riêng static hoặc final để biến field thành dữ liệu instance hay dữ liệu có thể gán lại.

## 6. Có nên gom mọi hằng vào interface?

Một interface có thể công bố hằng khi các giá trị thực sự thuộc hợp đồng của nó. Nhưng dùng interface chỉ như kho hằng, rồi cho class implements chỉ để có thể viết tên field ngắn, thường tạo quan hệ kiểu gây hiểu nhầm.

Với hằng tiện ích không thuộc hợp đồng hành vi, có thể dùng utility class:

~~~java
public final class Defaults {
    private Defaults() {
        throw new AssertionError("No instances");
    }

    public static final int DEFAULT_TIMEOUT = 30;
}
~~~

Mã sử dụng viết **Defaults.DEFAULT_TIMEOUT** mà không cần implements. Nếu giá trị thay đổi theo môi trường hoặc cần đọc lại khi chạy, dùng API cấu hình hoặc object cấu hình thay vì giả định field final sẽ tự cập nhật.

Khi có nhiều interface với field trùng tên, hãy chỉ rõ interface sở hữu field; bài về truy cập hằng xử lý các xung đột đó.

### Tự kiểm tra

1. Field **int PORT = 8080;** trong interface có modifier ngầm định nào? **public static final**.
2. Có thể gán field interface trong constructor của class implements không? **Không**.
3. Mọi initializer đều là hằng thời điểm biên dịch không? **Không**; initializer có thể gọi phương thức hoặc tạo đối tượng.
4. Có nên implements một interface chỉ để lấy tên hằng ngắn? **Thông thường không**; truy cập qua tên interface rõ nghĩa hơn.

Ba modifier mặc định và initializer bắt buộc là quy tắc nền tảng của field interface. Chúng quyết định field thuộc về ai, ai có thể đọc và khi nào giá trị được cung cấp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-members-004">Phân biệt thuộc tính final với đối tượng bất biến</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt final với tính bất biến của đối tượng trong Java

## 1. Hai câu hỏi khác nhau

**final** trên biến hoặc field giới hạn việc gán lại biến. **Immutability** mô tả việc trạng thái có thể quan sát của đối tượng có thay đổi sau khi tạo hay không. Một thuộc tính không tự suy ra thuộc tính kia.

~~~java
final int count = 3;
// count = 4;                 // Lỗi: không thể gán lại count

final StringBuilder text = new StringBuilder("Java");
text.append(" 17");           // Hợp lệ: sửa đối tượng được tham chiếu
// text = new StringBuilder(); // Lỗi: không thể đổi tham chiếu
~~~

Với primitive, final cố định giá trị sau khi khởi tạo. Với kiểu tham chiếu, final cố định tham chiếu; API của đối tượng đích vẫn có thể cho phép sửa trạng thái.

String và Integer là các kiểu bất biến. StringBuilder, mảng và ArrayList có trạng thái thay đổi được. Một final class chỉ ngăn kế thừa, và một record chỉ cố định việc gán lại component field theo cấu trúc của record; cả hai không tự làm các đối tượng con bất biến sâu.

## 2. Vì sao điều này quan trọng với field interface?

Field khai báo trong interface là public static final. Nếu field trỏ tới một đối tượng mutable, mã bên ngoài vẫn có thể thay đổi đối tượng đó qua tham chiếu công khai.

~~~java
interface SharedSettings {
    int TIMEOUT_SECONDS = 5;
    int[] PORTS = {8080, 8081};
}
~~~

Không thể gán SharedSettings.PORTS sang mảng mới, nhưng phép gán SharedSettings.PORTS[0] = 9090 hợp lệ. Tất cả nơi đọc field đều nhìn thấy thay đổi trên cùng mảng. Tránh công khai mảng hoặc collection mutable nếu bạn muốn dữ liệu cấu hình ổn định.

## 3. Bất biến cần kiểm soát các tham chiếu thoát ra ngoài

~~~java
import java.util.List;

final class UnsafeProfile {
    private final List<String> roles;

    UnsafeProfile(List<String> roles) {
        this.roles = roles;
    }

    List<String> roles() {
        return roles;
    }
}
~~~

Lớp này không bất biến: bên gọi có thể giữ danh sách đầu vào rồi sửa nó sau khi tạo profile; đồng thời getter trả thẳng danh sách nội bộ cho phép sửa từ bên ngoài. Hai vấn đề thường được gọi là aliasing ở đầu vào và representation exposure ở đầu ra.

Muốn trạng thái ổn định, hãy kiểm soát cả dữ liệu đi vào lẫn dữ liệu được trả ra.

## 4. Sao chép phòng vệ với List.copyOf

Lưu mã sau vào **ProfileDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

final class Profile {
    private final String username;
    private final List<String> roles;

    Profile(String username, List<String> roles) {
        this.username = username;
        this.roles = List.copyOf(roles);
    }

    String username() {
        return username;
    }

    List<String> roles() {
        return roles;
    }
}

public class ProfileDemo {
    public static void main(String[] args) {
        List<String> input = new ArrayList<>(List.of("READER"));
        Profile profile = new Profile("mai", input);
        input.add("ADMIN");

        System.out.println(profile.username());
        System.out.println(profile.roles());
        System.out.println(input);
    }
}
~~~

Chạy với JDK 17+:

~~~bash
javac --release 17 ProfileDemo.java
java ProfileDemo
~~~

Kết quả:

~~~text
mai
[READER]
[READER, ADMIN]
~~~

List.copyOf tạo danh sách không sửa đổi được và tách cấu trúc danh sách khỏi nguồn mutable. Vì phần tử trong ví dụ là String bất biến, không cần sao chép riêng từng phần tử. List.copyOf từ chối null ở danh sách hoặc phần tử, và không tự sao chép sâu các phần tử mutable.

## 5. View không sửa đổi và snapshot khác nhau

**Collections.unmodifiableList(source)** chặn thao tác sửa qua view, nhưng view vẫn phản ánh thay đổi từ source. **List.copyOf(source)** tạo cấu trúc không sửa đổi được, không tiếp tục phản ánh việc thêm hoặc xóa trên nguồn mutable.

| Cách tạo | Có thể sửa qua kết quả? | Thấy thay đổi cấu trúc từ source? |
| --- | --- | --- |
| Collections.unmodifiableList(source) | Không | Có |
| List.copyOf(source) | Không | Không |

Cả hai cách đều không làm các phần tử mutable trở nên bất biến. Nếu một phần tử là StringBuilder và có tham chiếu khác tới nó, nội dung phần tử vẫn có thể thay đổi.

## 6. Record và bất biến sâu

Record không tự sao chép collection được truyền vào. Nếu record nhận một List mutable và giữ nguyên tham chiếu, bên gọi vẫn có thể làm nội dung quan sát được thay đổi. Có thể sao chép phòng vệ trong compact constructor:

~~~java
import java.util.List;

record Report(List<String> tags) {
    Report {
        tags = List.copyOf(tags);
    }
}
~~~

Với các phần tử bất biến như String, cách này bảo vệ cấu trúc và dữ liệu quan sát được trong ví dụ. Với phần tử mutable, phải định nghĩa rõ cách sao chép hoặc chuyển chúng thành kiểu dữ liệu bất biến.

## 7. final không làm dữ liệu chia sẻ thread-safe

Một public static final ArrayList vẫn có thể bị nhiều thread đọc và sửa đồng thời. final không biến các thao tác sửa trên đối tượng mutable thành thao tác an toàn luồng. Cần chọn cấu trúc dữ liệu và cơ chế đồng bộ phù hợp; tốt nhất là tránh chia sẻ mutable state khi không cần thiết.

### Checklist thiết kế

- Dùng final để ngăn gán lại biến hoặc tham chiếu.
- Dùng kiểu bất biến khi giá trị không nên đổi sau khi tạo.
- Sao chép phòng vệ dữ liệu đầu vào và không trả trực tiếp tham chiếu mutable nội bộ.
- Phân biệt view không sửa đổi với snapshot độc lập.
- Đừng suy luận bất biến sâu chỉ từ final class, record hoặc List.copyOf.
- Tránh công khai mutable state từ interface vì mọi nơi có quyền truy cập đều có thể tác động đến cùng dữ liệu.

Tính bất biến là kết quả của thiết kế đối tượng và các tham chiếu có thể tiếp cận nó; từ khóa final chỉ là một phần của cách thiết kế đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-members-005">Hằng thời điểm biên dịch và thuộc tính cần khởi tạo lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Hằng thời điểm biên dịch và field cần khởi tạo lúc chạy trong interface Java

## 1. public static final chưa chắc là hằng biên dịch

Mọi field khai báo trực tiếp trong interface đều ngầm định public static final. Nhưng một **constant variable** còn phải có kiểu primitive hoặc String và được khởi tạo bằng constant expression theo quy tắc Java.

~~~java
interface AppSettings {
    int MAX_RETRIES = 3;                    // Constant variable
    String PREFIX = "api-" + "v1";          // Constant variable
    int TIMEOUT_MS = Integer.parseInt("1500"); // Runtime initialization
    Integer BOXED_RETRIES = 3;               // Runtime initialization
}
~~~

| Khai báo | Constant variable? | Lý do |
| --- | --- | --- |
| int A = 10; | Có | Primitive và literal |
| String B = "Java" + " Core"; | Có | Nối các constant expressions |
| int C = A + 1; | Có, nếu A là constant variable đã khai báo | Biểu thức hằng |
| int D = Integer.parseInt("10"); | Không | Gọi phương thức |
| Integer E = 10; | Không | Integer là kiểu tham chiếu |
| String F = new String("Hi"); | Không | Tạo đối tượng |
| int[] G = {1, 2}; | Không | Mảng là đối tượng |

Kiểu primitive thôi chưa đủ: một field int được gán bằng lời gọi phương thức vẫn không phải constant variable. Tương tự, final trên một tham chiếu không làm đối tượng được trỏ tới bất biến; nội dung đó được giải thích ở bài về final và immutability.

## 2. Quan sát thời điểm khởi tạo

Lưu chương trình sau vào **InterfaceFieldsDemo.java**:

~~~java
interface Configuration {
    int MAX_RETRIES = 2 + 3;
    String PREFIX = "/api/" + "v1";
    int TIMEOUT_MS = readTimeout();

    static int readTimeout() {
        System.out.println("Configuration initializing");
        return 1500;
    }
}

public class InterfaceFieldsDemo {
    public static void main(String[] args) {
        System.out.println("max = " + Configuration.MAX_RETRIES);
        System.out.println("prefix = " + Configuration.PREFIX);
        System.out.println("before runtime field");
        System.out.println("timeout = " + Configuration.TIMEOUT_MS);
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac InterfaceFieldsDemo.java
java InterfaceFieldsDemo
~~~

Stdout:

~~~text
max = 5
prefix = /api/v1
before runtime field
Configuration initializing
timeout = 1500
~~~

MAX_RETRIES và PREFIX là constant variables; compiler có thể đưa giá trị của chúng trực tiếp vào mã sử dụng. Việc đọc chúng không bắt buộc khởi tạo Configuration. Lần đầu đọc TIMEOUT_MS cần khởi tạo interface, nên readTimeout chạy và in thông báo. Việc khởi tạo kiểu diễn ra một lần cho định nghĩa kiểu được nạp bởi class loader tương ứng, không lặp lại mỗi lần đọc field.

Interface không cho khai báo khối static initializer như class. Logic chạy lúc khởi tạo phải được biểu diễn thông qua initializer của field hoặc được chuyển sang API/factory có vòng đời rõ ràng.

## 3. Vì sao client cũ có thể giữ giá trị hằng cũ?

Constant variables có thể được compiler nhúng trực tiếp vào bytecode của mã sử dụng. Ví dụ, thư viện phát hành phiên bản đầu:

~~~java
public interface ApiConstants {
    int VERSION = 1;
    int PORT = Integer.parseInt("8080");
}
~~~

Client:

~~~java
public class Client {
    public static void main(String[] args) {
        System.out.println("version = " + ApiConstants.VERSION);
        System.out.println("port = " + ApiConstants.PORT);
    }
}
~~~

Sau khi biên dịch cả hai tệp, đổi VERSION thành 2 và PORT thành giá trị runtime 9090, rồi chỉ biên dịch lại ApiConstants.java, client.class cũ có thể in:

~~~text
version = 1
port = 9090
~~~

VERSION là constant variable nên giá trị 1 có thể đã được nhúng vào Client.class. PORT không phải constant variable, vì vậy client cũ đọc field lúc chạy và thấy 9090. Biên dịch lại Client.java sẽ khiến mã sử dụng giá trị VERSION mới.

Đây là vấn đề tương thích nhị phân của API công khai: thay đổi giá trị compile-time constant có thể yêu cầu biên dịch lại các client phụ thuộc. Đừng dùng constant variable cho cấu hình mà người dùng kỳ vọng có thể đổi chỉ bằng cách thay JAR.

## 4. Những hành động có thể kích hoạt khởi tạo

Trong các tình huống thông thường, đọc một static field được khai báo bởi interface mà không phải constant variable hoặc gọi static method được khai báo bởi interface có thể kích hoạt khởi tạo kiểu. Đọc constant variable không tự kích hoạt việc đó. Các initializer runtime được xử lý theo thứ tự khai báo; vì vậy hãy tránh phụ thuộc vòng hoặc side effect khó dự đoán.

Một field kiểu List, mảng hay object cũng không phải constant variable chỉ vì nó là static final. Nó được khởi tạo khi interface được khởi tạo, còn trạng thái bên trong có sửa đổi được hay không phụ thuộc vào chính đối tượng.

Để hiểu các lỗi do dùng field phía sau trong initializer, xem bài về giới hạn tham chiếu và thứ tự khai báo; bài đó phân biệt lỗi biên dịch với việc đọc giá trị mặc định lúc chạy.

## 5. Quy tắc quyết định

- Dùng constant variable khi giá trị primitive hoặc String thực sự ổn định như một phần của hợp đồng công khai.
- Dùng field runtime-initialized khi chấp nhận giá trị được tính một lần lúc interface khởi tạo và không cần tự cập nhật mỗi lần đọc.
- Dùng method, service hoặc configuration object khi giá trị phụ thuộc môi trường, người dùng, request hoặc cần được truy vấn lại.
- Tránh đặt I/O, truy cập mạng hay xử lý lỗi phức tạp trong initializer của field.
- Khi đổi hằng công khai, đánh giá client đã biên dịch có cần build lại hay không.

### Tự kiểm tra

1. Integer BOXED = 3 có phải constant variable không? **Không**, vì Integer là kiểu tham chiếu.
2. Đọc constant variable có bắt buộc khởi tạo interface không? **Không**.
3. Lời gọi phương thức trong initializer có thể là constant expression không? **Không**.
4. Đổi một compile-time constant rồi chỉ phát hành JAR mới có đảm bảo client cũ dùng giá trị mới không? **Không**, vì client có thể đã inline giá trị.

Nguồn đặc tả: [JLS §4.12.4 — final Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4), [JLS §15.29 — Constant Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.29), và [JLS §12.4 — Initialization](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-members-006">Truy cập hằng qua tên interface và xử lý tên hằng trùng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Truy cập hằng qua tên interface và xử lý tên hằng trùng nhau

## 1. Ghi rõ kiểu sở hữu hằng

Field của interface là public static final, vì vậy có thể đọc trực tiếp qua tên interface mà không cần tạo đối tượng hoặc implements interface đó.

~~~java
interface Pagination {
    int DEFAULT_SIZE = 20;
    int MAX_SIZE = 100;
}

public class ConstantAccessDemo {
    public static void main(String[] args) {
        System.out.println(Pagination.DEFAULT_SIZE);
        System.out.println(Pagination.MAX_SIZE);
    }
}
~~~

Kết quả:

~~~text
20
100
~~~

Viết Pagination.DEFAULT_SIZE cho người đọc biết rõ nguồn khai báo. Trong class implements interface, tên đơn có thể hoạt động nếu không có xung đột, nhưng nó khiến người đọc phải dò toàn bộ các interface và field cùng tên. Ưu tiên qualifier để mã dễ bảo trì.

## 2. Hai interface có hằng cùng tên

~~~java
interface HttpSettings {
    int TIMEOUT_SECONDS = 5;
}

interface DatabaseSettings {
    int TIMEOUT_SECONDS = 30;
}

final class ConnectionMonitor implements HttpSettings, DatabaseSettings {
    void showTimeouts() {
        System.out.println(HttpSettings.TIMEOUT_SECONDS);
        System.out.println(DatabaseSettings.TIMEOUT_SECONDS);
    }
}

public class NameCollisionDemo {
    public static void main(String[] args) {
        new ConnectionMonitor().showTimeouts();
    }
}
~~~

Kết quả:

~~~text
5
30
~~~

Trong ConnectionMonitor, tên đơn TIMEOUT_SECONDS không chỉ ra muốn lấy trường nào và có thể gây lỗi ambiguous reference. Java không tự chọn interface đứng trước trong danh sách implements, kể cả khi hai field trùng tên có cùng giá trị. Ghi rõ HttpSettings.TIMEOUT_SECONDS hoặc DatabaseSettings.TIMEOUT_SECONDS sẽ giải quyết xung đột.

## 3. Field hiding không phải overriding

Field không tham gia đa hình ghi đè như phương thức instance. Nếu class khai báo field cùng tên, field của class che khuất tên kế thừa trong phạm vi phù hợp:

~~~java
interface DefaultLimits {
    int LIMIT = 10;
}

final class LimitedService implements DefaultLimits {
    static final int LIMIT = 25;

    void printLimits() {
        System.out.println(LIMIT);
        System.out.println(LimitedService.LIMIT);
        System.out.println(DefaultLimits.LIMIT);
    }
}
~~~

Hai giá trị vẫn tồn tại: LimitedService.LIMIT là 25, còn DefaultLimits.LIMIT là 10. Việc chọn field được giải quyết theo tên và kiểu khai báo, không theo đối tượng thực như lời gọi phương thức instance.

Interface con cũng có thể khai báo field trùng tên với field được kế thừa; field con che khuất tên, nhưng field cha vẫn có thể truy cập qua tên interface cha.

## 4. Xung đột với static import

Static import giúp dùng tên thành viên static mà không ghi tên kiểu. Nhưng nó không loại bỏ xung đột tên.

~~~java
import static java.lang.Integer.*;
import static java.lang.Long.*;

public class StaticImportCollisionDemo {
    public static void main(String[] args) {
        // MAX_VALUE một mình sẽ nhập nhằng giữa Integer và Long.
        System.out.println(Integer.MAX_VALUE);
        System.out.println(Long.MAX_VALUE);
    }
}
~~~

Kết quả:

~~~text
2147483647
9223372036854775807
~~~

Với tên phổ biến như MAX_VALUE, LIMIT, STATUS hoặc TIMEOUT, dùng OwnerType.CONSTANT giúp tránh phải suy luận từ danh sách import. Xung đột có thể đến từ interface mà class implements, interface cha, field của chính class hoặc static import.

## 5. Quy tắc thực tế

- Đọc hằng qua tên interface định nghĩa nó.
- Khi nhiều interface có cùng tên field, ghi qualifier rõ ràng.
- Nếu class khai báo field cùng tên, phân biệt field của class với field của interface; đó là hiding.
- Đừng implements interface chỉ để lấy tên hằng ngắn. Quan hệ implements nên thể hiện hợp đồng kiểu/hành vi.
- Trường mutable được công khai từ interface vẫn có thể bị thay đổi qua tham chiếu; xem bài về final và immutability.
- Giá trị compile-time constant có thể được nhúng vào client đã biên dịch; thay đổi giá trị hằng có thể cần biên dịch lại client.

### Tự kiểm tra

1. Có cần tạo đối tượng để đọc Pagination.DEFAULT_SIZE không? **Không**.
2. Hai interface không liên quan cùng khai báo TIMEOUT_SECONDS; tên đơn có tự chọn interface đầu tiên không? **Không**.
3. Field của class trùng tên field interface có override đa hình không? **Không**, đó là hiding.
4. Static import có làm hai hằng MAX_VALUE trở thành một field không? **Không**, qualifier kiểu giúp chọn nguồn.

Điểm chính là xác định rõ field thuộc kiểu nào. Tên đủ nghĩa tránh nhập nhằng và giữ ý định thiết kế ổn định khi class hoặc interface mở rộng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-members-007">Giới hạn truy cập và modifier không hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn truy cập và modifier không hợp lệ của thành viên interface

## 1. Phân biệt kiểu interface với thành viên bên trong

Quy tắc truy cập của chính interface khác với quy tắc của các thành viên. Một interface top-level có thể package-private nếu không ghi public. Tuy nhiên, phương thức abstract thông thường bên trong interface vẫn ngầm định public abstract; bỏ public không làm nó thành package-private.

Bài này là bảng tra cứu các tổ hợp modifier. Phần giải thích và ví dụ đầy đủ về từng loại phương thức nằm trong bài phân loại phương thức và bài phương thức abstract ngầm định public.

## 2. Ma trận modifier hợp lệ

| Thành viên | Quy tắc mặc định hoặc bắt buộc | Hạn chế quan trọng |
| --- | --- | --- |
| Field | public static final, phải có initializer | Không private, protected, volatile hoặc transient |
| Abstract method | public abstract, không có thân | Không private, protected, static hoặc final |
| Default method (Java 8+) | Instance method public có thân | Không kết hợp abstract, static hoặc private |
| Static method (Java 8+) | Public static có thân, trừ private static helper | Không abstract; không được gọi như method instance kế thừa |
| Private method (Java 9+) | Private instance hoặc private static, luôn có thân | Không abstract hoặc default |
| Member type lồng trong interface | public static theo ngầm định | Không private hoặc protected khi khai báo trực tiếp trong interface |

Các quy tắc này giải thích vì sao không thể dùng private field để giấu trạng thái trong interface. Nếu cần trạng thái riêng, đặt field trong class. Nếu cần helper method nội bộ từ Java 9, dùng private method có thân.

## 3. Một interface hợp lệ làm ví dụ đối chiếu

~~~java
interface AccessRules {
    int LIMIT = 3;

    void execute();

    default String describe() {
        return "[" + LIMIT + "]";
    }

    static boolean valid(int value) {
        return value >= 0;
    }

    private String helper(String value) {
        return value.trim();
    }

    private static String normalize(String value) {
        return value.trim();
    }

    class Metadata {
        String category() {
            return "rules";
        }
    }
}
~~~

LIMIT là field public static final. execute là public abstract. describe là phương thức instance default có thân; valid là static method có thân; helper và normalize là private helper methods. Metadata là member type public static theo ngầm định. Mã trên dùng cú pháp Java 17+.

## 4. Những tổ hợp sai thường gặp

| Khai báo sai | Vì sao không hợp lệ | Hướng sửa |
| --- | --- | --- |
| private int code = 1; | Field interface luôn public static final | Đặt trạng thái private trong class |
| protected void work(); | Interface method không có quyền protected | Dùng abstract method public hoặc private helper có thân |
| private abstract void work(); | Private helper không được abstract/override | Dùng public abstract contract hoặc private method có thân |
| static void work(); | Static method phải có thân | Viết static method có thân |
| default void work(); | Default method phải có thân | Viết thân hoặc dùng abstract declaration |
| static default void work() { } | Static và default mô tả hai loại phương thức khác nhau | Chọn một loại phù hợp |
| final default void work() { } | Method interface không được final | Dùng default hoặc static tùy ý định |
| private class Detail { } | Member type của interface public static theo ngầm định | Đặt kiểu cần ẩn bên trong class phù hợp |

Đừng ghép các dòng sai thành một chương trình duy nhất để kiểm tra: mỗi dòng minh họa một lỗi biên dịch riêng. Các phương thức interface cũng không được khai báo synchronized hoặc native. Với abstract method, không có thân; default, static và private method phải có thân.

## 5. Phiên bản Java cần lưu ý

- **Java 8:** hỗ trợ default method và static method có thân trong interface.
- **Java 9:** bổ sung private instance method và private static method để tái sử dụng logic nội bộ.
- **Java 17+:** tất cả các dạng trên có thể được dùng cùng nhau, miễn từng modifier phù hợp với loại thành viên.

Không suy ra rằng Java 9 cho phép mọi thành viên private: private field và private member type khai báo trực tiếp trong interface vẫn không hợp lệ.

## 6. Các lỗi về phạm vi thường bị nhầm

**Top-level interface** không có public có thể chỉ truy cập được trong package. Điều đó không làm các method bên trong nó trở thành package-private.

**Member type** nằm trực tiếp trong interface là public static theo ngầm định. Ngược lại, nested interface nằm trong một class tuân theo quy tắc thành viên của class bao ngoài; class có thể có nested type private.

**Abstract method** cần một triển khai tương thích ở lớp cụ thể, vì vậy class implements phải dùng public. **Private helper** chỉ hỗ trợ logic bên trong interface và không được kế thừa hoặc override. **Static method** thuộc về kiểu interface và được gọi qua tên interface.

### Checklist

1. Field có initializer và không dùng modifier trái phép chưa?
2. Abstract method có được hiểu là public dù bỏ từ khóa không?
3. Default/static/private method có thân và đúng loại không?
4. Private method chỉ được dùng làm helper nội bộ, không bị nhầm với hợp đồng implements?
5. Member type có được công khai theo quy tắc public static của interface?
6. Class cụ thể đã triển khai các abstract method bằng public chưa?

Dùng ma trận này khi đọc lỗi biên dịch. Trước khi sửa modifier, hãy xác định thành viên là field, abstract/default/static/private method hay member type; bỏ một từ khóa không đúng chưa chắc tạo ra một khai báo hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-members-008">Giới hạn tham chiếu thuộc tính và thứ tự khai báo</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn tham chiếu field và thứ tự khai báo trong interface Java

## 1. Hai vấn đề cần phân biệt

Trong initializer của field interface, Java hạn chế tham chiếu bằng **tên đơn** đến chính field đó hoặc field được khai báo phía sau. Đây là quy tắc lúc biên dịch. Một trường hợp khác là tham chiếu qualified name hợp lệ về cú pháp nhưng đọc field runtime trước khi nó được gán; khi đó kết quả có thể là giá trị mặc định.

Field interface luôn là public static final và phải có initializer. Để phân biệt constant variable với field được tính lúc runtime, xem bài về hằng thời điểm biên dịch.

## 2. Simple-name forward reference và tự tham chiếu

Ví dụ sau không biên dịch:

~~~java
interface BadOrder {
    int FIRST = SECOND + 1;
    int SECOND = 5;
}
~~~

SECOND được dùng bằng tên đơn trong initializer của FIRST trước vị trí khai báo SECOND. Việc SECOND là hằng biên dịch không làm hợp lệ tham chiếu này.

Tự tham chiếu trực tiếp cũng sai:

~~~java
interface BadSelfReference {
    int COUNT = COUNT + 1;
}
~~~

Các declarator trên cùng dòng cũng tuân theo thứ tự từ trái sang phải:

~~~java
interface ValidOrder {
    int BASE = 5, NEXT = BASE + 1;
}
~~~

BASE đã được khai báo trước NEXT nên biểu thức NEXT hợp lệ. Quy tắc này nhắm vào initializer của field; thân phương thức có thể dùng field khai báo ở phía sau.

## 3. Qualified name có thể hợp lệ nhưng nguy hiểm lúc chạy

Tên đủ nghĩa như RuntimeOrder.LATER tránh lỗi simple-name forward reference. Tuy nhiên, nó không tự sắp xếp việc khởi tạo runtime.

~~~java
interface RuntimeOrder {
    int EARLY = RuntimeOrder.LATER;
    int LATER = supply();

    static int supply() {
        return 42;
    }
}
~~~

Khi khởi tạo interface, EARLY được gán trước khi initializer của LATER chạy. Lúc đó LATER còn có giá trị mặc định 0, nên EARLY nhận 0; sau đó LATER nhận 42.

Trái lại, nếu field phía sau là constant variable, giá trị hằng có thể được biết ngay lúc biên dịch:

~~~java
interface QualifiedConstant {
    int FIRST = QualifiedConstant.SECOND;
    int SECOND = 7;
}
~~~

FIRST nhận 7. Hai trường hợp có cú pháp gần giống nhau nhưng hành vi khác nhau vì một field là constant variable, field kia cần initializer runtime.

## 4. Ví dụ Java 17+ hoàn chỉnh

Lưu vào **Main.java**:

~~~java
interface RuntimeOrder {
    int EARLY = RuntimeOrder.LATER;
    int LATER = supply();

    static int supply() {
        return 42;
    }
}

interface QualifiedConstant {
    int FIRST = QualifiedConstant.SECOND;
    int SECOND = 7;
}

public class Main {
    public static void main(String[] args) {
        System.out.println(RuntimeOrder.EARLY);
        System.out.println(RuntimeOrder.LATER);
        System.out.println(QualifiedConstant.FIRST);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac Main.java
java Main
~~~

Kết quả:

~~~text
0
42
7
~~~

Lúc khởi tạo RuntimeOrder, static fields được chuẩn bị với giá trị mặc định rồi các initializer được thực hiện theo thứ tự khai báo. EARLY đọc LATER trước khi lời gọi supply gán giá trị 42. QualifiedConstant.SECOND là constant variable nên FIRST nhận 7.

## 5. Method call, field kế thừa và tên trùng nhau

Lời gọi phương thức trong initializer không phải tham chiếu field bằng tên đơn, vì vậy cú pháp này hợp lệ:

~~~java
interface Computed {
    int PORT = defaultPort();

    static int defaultPort() {
        return 8080;
    }
}
~~~

PORT không phải constant variable vì initializer gọi phương thức. Tuy nhiên, nếu phương thức được gọi gián tiếp đọc field runtime chưa được gán, thứ tự khởi tạo vẫn có thể tạo giá trị bất ngờ.

Field được kế thừa từ interface cha cũng có thể được sử dụng trong initializer của interface con:

~~~java
interface ParentLimits {
    int MAX = 20;
}

interface ChildLimits extends ParentLimits {
    int WARNING = MAX - 5;
}
~~~

Nếu hai interface cha độc lập cùng khai báo TIMEOUT, tên đơn TIMEOUT có thể mơ hồ. Đây là lỗi phân giải tên, khác với forward reference do vị trí khai báo. Dùng East.TIMEOUT hoặc West.TIMEOUT theo đúng ý nghĩa cần lấy.

## 6. Cách tổ chức khai báo an toàn

- Khai báo field nền trước field phụ thuộc.
- Với field runtime, tránh phụ thuộc ngược hoặc vòng giữa các initializer.
- Khi compiler báo illegal forward reference, kiểm tra xem initializer dùng simple name của field khai báo phía sau hay tự tham chiếu.
- Đừng thêm tên interface vào trước field chỉ để làm compiler hết báo lỗi mà không xem xét thứ tự runtime.
- Chuyển cấu hình phức tạp hoặc có side effect sang factory/service nếu thời điểm khởi tạo cần được kiểm soát.
- Khi nhiều interface cha có tên field giống nhau, dùng qualifier của interface định nghĩa field cần đọc.

### Tự kiểm tra

1. FIRST dùng SECOND bằng tên đơn khi SECOND khai báo phía sau: **lỗi biên dịch**.
2. FIRST dùng QualifiedConstant.SECOND, với SECOND = 7: **hợp lệ và nhận 7**.
3. EARLY dùng RuntimeOrder.LATER, với LATER = supply(): **EARLY có thể nhận 0** vì initializer runtime của LATER chưa chạy.
4. Một static method có thể được khai báo sau field gọi nó không? **Có**, nhưng cần xem nó phụ thuộc field runtime nào.

Đọc theo ba câu hỏi: tên tham chiếu là simple name hay qualified name; field đích đã được khai báo trước chưa; và giá trị là constant variable hay chỉ được tính khi interface khởi tạo.

</details>

- [Quay lại đầu trang](#back-to-top)
