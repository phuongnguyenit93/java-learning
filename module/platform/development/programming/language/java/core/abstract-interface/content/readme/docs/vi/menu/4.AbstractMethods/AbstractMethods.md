<a id="back-to-top"></a>

# Phương thức abstract

## Menu
- [Cú pháp khai báo và dấu chấm phẩy thay cho thân phương thức](#abstract-methods-001)
- [Quan hệ giữa phương thức abstract và lớp chứa nó](#abstract-methods-002)
- [Tham số, kiểu trả về và mệnh đề throws](#abstract-methods-003)
- [Phạm vi public, protected và package-private](#abstract-methods-004)
- [Vì sao abstract không kết hợp với private, static hoặc final](#abstract-methods-005)
- [Các modifier không hợp lệ khác của phương thức abstract](#abstract-methods-006)
- [Overload phương thức abstract](#abstract-methods-007)
- [Khai báo lại phương thức đã có thân thành abstract](#abstract-methods-008)
- [Phương thức package-private khi lớp con nằm khác package](#abstract-methods-009)

## <a id="abstract-methods-001">Cú pháp khai báo và dấu chấm phẩy thay cho thân phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Cú pháp khai báo phương thức `abstract` và dấu chấm phẩy

Phương thức `abstract` công bố **một hành vi cần được cung cấp phần triển khai** nhưng không chứa thân phương thức tại nơi khai báo. Trong một lớp, điểm nhận diện cú pháp là từ khóa `abstract` và dấu chấm phẩy `;` ở cuối khai báo.

## 1. Mẫu khai báo trong class

```java
[access-modifier] abstract [TypeParameters] ReturnType methodName(Parameters)
        [throws ExceptionTypes];
```

Dấu ngoặc vuông trong mẫu chỉ đánh dấu phần tùy chọn; không gõ chúng vào mã Java. `TypeParameters` là phần khai báo generic như `<T>`, `Parameters` có thể rỗng, còn `throws` chỉ xuất hiện khi hợp đồng cần công bố ngoại lệ. Ví dụ:

```java
abstract class Shape {
    public abstract double area();
    protected abstract void describe(String label);
}
```

`area()` trả về `double`, còn `describe(String)` nhận một tham số và trả về `void`. Cả hai kết thúc bằng `;` và không có `{ ... }`. Vì `Shape` trực tiếp khai báo phương thức abstract, `Shape` cũng phải là `abstract class`.

## 2. Vì sao khai báo kết thúc bằng `;`?

Dấu `;` kết thúc chữ ký mà không khai báo phần thân:

```java
abstract class Worker {
    abstract void work(); // Hợp lệ: chưa có phần triển khai
}

class ConcreteWorker {
    void work() { }       // Hợp lệ: thân rỗng vẫn là một thân cụ thể
}
```

Hai cách viết mang ý nghĩa khác nhau. `abstract void work();` yêu cầu lớp concrete cung cấp một implementation hợp lệ; `void work() { }` đã có implementation, dù thân chưa làm gì.

Không được viết:

```java
abstract class InvalidWorker {
    abstract void work() { } // Lỗi: abstract method không được có thân
}
```

Muốn khai báo hợp đồng, bỏ `{ ... }` và dùng `;`. Muốn triển khai hành vi tại lớp hiện tại, bỏ `abstract` và viết thân phương thức.

## 3. Vị trí của generic, tham số và `throws`

Generic method đặt danh sách kiểu trước kiểu trả về. Nếu có `throws`, dấu `;` vẫn nằm sau toàn bộ mệnh đề đó:

```java
abstract class Parser {
    protected abstract <T> T parse(
            String source, Class<T> targetType)
            throws java.text.ParseException;
}
```

Ở đây `<T>` là tham số kiểu của phương thức, `String` và `Class<T>` là các kiểu tham số, `T` là kiểu trả về, còn `ParseException` là ngoại lệ được công bố. Khai báo không chứa mã phân tích chuỗi; lớp triển khai sẽ cung cấp phần thân. Các quy tắc chi tiết về tương thích tham số, kiểu trả về và checked exception khi override thuộc phạm vi của bài học về hợp đồng chữ ký.

## 4. Cú pháp tương ứng trong interface

Phương thức instance thông thường không có thân trong interface mặc định là `public abstract`; có thể lược bỏ cả hai từ khóa:

```java
interface Sender {
    void send(String message); // Tương đương public abstract
}
```

Khi một lớp triển khai `Sender`, phương thức `send` phải là `public`. Đừng suy luận rằng mọi phương thức interface đều abstract: `default`, `static` và `private` là các dạng phương thức khác và phải có thân theo quy tắc của chúng.

```java
interface MessageSender {
    void send(String message);

    default void sendWelcome() {
        send("Welcome");
    }

    static String protocol() {
        return "v1";
    }

    private String normalize(String message) {
        return message.strip();
    }
}
```

`send` là hợp đồng abstract; `sendWelcome` có triển khai mặc định; `protocol` thuộc về interface; `normalize` là helper nội bộ có thân. Ví dụ này minh họa cú pháp interface, không phải một ứng dụng có `main`.

## 5. Ví dụ Java 17+ chạy được

Lưu đoạn mã dưới đây vào `AbstractMethodSyntaxDemo.java`:

```java
abstract class MessageFormatter {
    protected abstract String format(String message);

    public String show(String message) {
        return format(message);
    }
}

final class UppercaseFormatter extends MessageFormatter {
    @Override
    protected String format(String message) {
        return message.toUpperCase(java.util.Locale.ROOT);
    }
}

public class AbstractMethodSyntaxDemo {
    public static void main(String[] args) {
        MessageFormatter formatter = new UppercaseFormatter();
        System.out.println(formatter.show("hello java"));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac AbstractMethodSyntaxDemo.java
java AbstractMethodSyntaxDemo
```

Kết quả:

```text
HELLO JAVA
```

`MessageFormatter` khai báo hợp đồng `format`; `show` đã có thân và gọi phương thức đó. `UppercaseFormatter` cung cấp phần triển khai. Lời gọi qua biến kiểu `MessageFormatter` vẫn chạy implementation của đối tượng thực tế `UppercaseFormatter`.

## 6. Lỗi cú pháp thường gặp

| Khai báo | Kết quả | Cách sửa |
|---|---|---|
| `abstract void run()` trong class | Lỗi nếu thiếu dấu kết thúc khai báo | Viết `abstract void run();` |
| `abstract void run() { }` | Lỗi vì abstract method có thân | Bỏ thân hoặc bỏ `abstract` |
| `class A { abstract void run(); }` | Lỗi vì class trực tiếp khai báo abstract method nhưng không abstract | Khai báo `abstract class A` hoặc cung cấp thân cụ thể |
| `class A { void run(); }` | Lỗi vì phương thức concrete trong class cần thân | Viết `void run() { ... }` hoặc đổi thiết kế thành abstract class |
| Lớp implement interface bằng `void send(...)` không có `public` | Lỗi nếu method đang implement public interface method | Khai báo `public void send(...)` |

Một checklist nhanh khi đọc khai báo: xác định kiểu chứa là `class` hay `interface`; kiểm tra từ khóa `abstract`; tìm dấu `;` cuối khai báo; xác nhận không có thân `{ ... }`; sau đó kiểm tra lớp concrete có implementation hợp lệ. Những tổ hợp modifier mâu thuẫn như `private abstract`, `static abstract` và `final abstract` được giải thích riêng trong các bài về modifier.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-methods-002">Quan hệ giữa phương thức abstract và lớp chứa nó</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa phương thức `abstract` và lớp chứa nó trong Java

Một phương thức `abstract` mô tả **một hành vi mà kiểu cha yêu cầu nhưng chưa cung cấp phần thân thực thi**. Quan hệ giữa phương thức này và kiểu chứa nó quyết định kiểu nào phải khai báo `abstract`, kiểu nào được phép khởi tạo đối tượng và lớp con nào có trách nhiệm hiện thực hành vi.

## 1. Quy tắc cốt lõi

Với **lớp (`class`) thông thường trong Java**:

- Nếu lớp **trực tiếp khai báo ít nhất một phương thức `abstract`**, bản thân lớp **bắt buộc phải được khai báo `abstract`**.
- Chiều ngược lại **không đúng**: một `abstract class` hoàn toàn có thể **không có phương thức `abstract` nào**. Người thiết kế vẫn có thể dùng nó để ngăn khởi tạo trực tiếp hoặc làm lớp nền cho hệ phân cấp.
- Một lớp không có từ khóa `abstract` (**concrete class**) phải cung cấp hoặc kế thừa triển khai hợp lệ cho **tất cả phương thức abstract còn tồn đọng** trong hệ phân cấp kiểu của nó. Nếu chưa đáp ứng được, lớp đó phải là `abstract class`.
- Không thể tạo đối tượng trực tiếp bằng `new` từ `abstract class`, dù lớp ấy có constructor và dù chưa khai báo phương thức abstract nào. Tuy nhiên, constructor của lớp abstract vẫn được gọi trong quá trình khởi tạo đối tượng lớp con.
- `abstract` thuộc về **phương thức cần triển khai** và **lớp chưa hoàn chỉnh để khởi tạo**, nhưng hai khái niệm không đồng nghĩa: `abstract class` có thể chứa phương thức đã có thân, trường dữ liệu, constructor, phương thức `static` và `final`.

Trong một `interface`, phương thức thể hiện hợp đồng và không có phần thân thường **ngầm định `public abstract`**. Interface không được khai báo bằng `abstract class`; nó là loại kiểu riêng. Trường hợp `enum` có phương thức abstract là ngoại lệ chuyên biệt, được minh họa ở phần 8.

## 2. Ví dụ một lớp vừa có hợp đồng abstract vừa có logic dùng chung

```java
abstract class DocumentJob {
    protected abstract String execute();

    public String report() {
        return "Result: " + execute();
    }
}
```

`DocumentJob` cần từ khóa `abstract` vì trực tiếp khai báo `execute()` không có thân. Phương thức `report()` đã có triển khai và được phép gọi `execute()`; khi một đối tượng lớp con cụ thể chạy `report()`, lời gọi `execute()` sử dụng implementation tương ứng với lớp thực tế. Điều này cho phép lớp nền giữ một phần quy trình chung và yêu cầu lớp con hoàn thành một bước còn thiếu.

Việc `execute()` không có thân là quy tắc cú pháp của abstract method; còn việc lớp chứa bắt buộc abstract là quy tắc **ở cấp class**. Một `abstract class` không khai báo bất kỳ phương thức abstract nào vẫn hợp lệ: nó có thể chỉ giữ constructor, dữ liệu chung hoặc cố ý ngăn tạo instance trực tiếp. Phần cú pháp với dấu `;` và `interface` được trình bày riêng ở bài mở đầu chương.

## 3. Hai chiều quan hệ không tương đương

| Tình huống | Có hợp lệ? | Lý do |
| --- | --- | --- |
| `abstract class A { abstract void run(); }` | Có | Lớp được đánh dấu abstract để chứa phương thức abstract. |
| `abstract class A { void run() {} }` | Có | Lớp abstract không bắt buộc khai báo phương thức abstract. |
| `class A { abstract void run(); }` | Không | Lớp cụ thể trực tiếp khai báo phương thức abstract. |
| `class A { void run() {} }` | Có | Lớp cụ thể có triển khai đầy đủ. |
| `abstract final class A {}` | Không | `abstract` cần cho phép tạo lớp con triển khai; `final` cấm kế thừa. |
| `abstract class A { private abstract void run(); }` | Không | `private` mâu thuẫn với hợp đồng ghi đè của phương thức abstract. |

Bảng xem xét các khai báo `class` thông thường; `interface` và `enum` có quy tắc cú pháp riêng.

Phân biệt thêm:

- **Phương thức abstract**: thiếu phần thân tại nơi khai báo, tạo nghĩa vụ triển khai trong lớp con cụ thể.
- **Lớp abstract**: chưa được phép khởi tạo trực tiếp; có thể chứa hoặc thừa hưởng những nghĩa vụ triển khai, hoặc chỉ đóng vai trò lớp nền.
- **Lớp concrete**: có thể khởi tạo nếu constructor khả dụng và mọi hợp đồng phương thức abstract đã được thực hiện hợp lệ.

Vì vậy câu “lớp abstract thì tất cả phương thức đều abstract” là sai; câu “có phương thức abstract thì lớp chứa là abstract” đúng với khai báo `class` thông thường nhưng phải xét riêng `interface` và `enum`.

## 4. Quan hệ với lớp con: kế thừa, triển khai và khởi tạo

Một phương thức abstract trong lớp cha được **kế thừa như một nghĩa vụ**. Lớp con có hai hướng xử lý:

1. **Lớp con vẫn là abstract**: được phép chưa triển khai phương thức. Nó có thể tiếp tục chuyển nghĩa vụ xuống các lớp con sau.
2. **Lớp con là concrete**: phải tự triển khai phương thức abstract còn thiếu, hoặc kế thừa một triển khai concrete hợp lệ đã có từ hệ phân cấp.

Khi ghi đè, nên dùng `@Override` để trình biên dịch kiểm tra chữ ký. Triển khai phải tuân thủ quy tắc override: không giảm mức truy cập, kiểu trả về tương thích (bao gồm kiểu trả về đồng biến nếu áp dụng), không mở rộng checked exception vượt khai báo của phương thức được ghi đè.

Sơ đồ tư duy:

```text
abstract class Ticket
  ├─ constructor, field, phương thức có thân header()
  └─ abstract int fee()
          ↓ kế thừa nghĩa vụ
final class AdultTicket extends Ticket
  └─ @Override int fee() { ... }
          ↓
Có thể khởi tạo new AdultTicket(...)
Có thể gán tham chiếu Ticket để gọi fee() đa hình
```

### Ví dụ 1: lớp cha abstract, lớp con concrete

Lưu toàn bộ ví dụ sau trong tệp `AbstractMethodDemo.java`:

```java
abstract class Ticket {
    private final String holder;

    protected Ticket(String holder) {
        this.holder = holder;
    }

    public String header() {
        return "Ticket for " + holder;
    }

    public abstract int fee();
}

final class AdultTicket extends Ticket {
    AdultTicket(String holder) {
        super(holder);
    }

    @Override
    public int fee() {
        return 100;
    }
}

public class AbstractMethodDemo {
    public static void main(String[] args) {
        Ticket ticket = new AdultTicket("Lan");

        System.out.println(ticket.header());
        System.out.println("Fee: " + ticket.fee());
    }
}
```

Biên dịch và chạy bằng Java 17+:

```bash
javac -encoding UTF-8 AbstractMethodDemo.java
java AbstractMethodDemo
```

Kết quả:

```text
Ticket for Lan
Fee: 100
```

Điều cần quan sát:

- `Ticket` **không thể khởi tạo trực tiếp**, do được khai báo `abstract`.
- `Ticket` vẫn được phép có trường `holder`, constructor và phương thức cụ thể `header()`.
- `AdultTicket` trở thành lớp cụ thể vì nó cung cấp `fee()` đúng hợp đồng.
- Biến `ticket` có kiểu tham chiếu `Ticket` nhưng đối tượng thực tế là `AdultTicket`; lời gọi `ticket.fee()` chạy triển khai trong `AdultTicket` theo cơ chế **dynamic dispatch**.
- Từ khóa `final` trên `AdultTicket` chỉ ngăn tạo lớp con tiếp theo; nó hợp lệ ở đây vì `AdultTicket` không phải lớp abstract.

### Ví dụ 2: lớp con trung gian vẫn abstract

Lưu toàn bộ ví dụ trong `InheritanceDemo.java`:

```java
abstract class Device {
    public abstract String connect();
}

abstract class NetworkDevice extends Device {
    public String category() {
        return "Network device";
    }
    // Chưa triển khai connect(): hợp lệ vì lớp vẫn abstract.
}

final class WifiDevice extends NetworkDevice {
    @Override
    public String connect() {
        return "Connected by WiFi";
    }
}

public class InheritanceDemo {
    public static void main(String[] args) {
        Device device = new WifiDevice();
        System.out.println(device.connect());
    }
}
```

```bash
javac -encoding UTF-8 InheritanceDemo.java
java InheritanceDemo
```

```text
Connected by WiFi
```

`NetworkDevice` không cần lặp lại dòng `abstract String connect();`. Nghĩa vụ triển khai vẫn được kế thừa từ `Device` và chuyển tiếp cho `WifiDevice`. Nếu bỏ phương thức `connect()` khỏi `WifiDevice` mà vẫn giữ lớp concrete, chương trình sẽ không biên dịch.

## 5. Nếu lớp chứa không khai báo phương thức abstract trực tiếp thì sao?

Quy tắc phải xét **toàn bộ phương thức abstract còn chưa được triển khai**, gồm cả phương thức kế thừa từ lớp cha và từ interface.

```java
interface Identifiable {
    String id();
}

abstract class BaseEntity implements Identifiable {
    // Không khai báo lại id(); vẫn có nghĩa vụ từ interface.
}

final class UserEntity extends BaseEntity {
    @Override
    public String id() {
        return "U-001";
    }
}
```

Đoạn trên là minh họa các khai báo kiểu (không có `main`). `BaseEntity` phải tiếp tục là abstract vì chưa triển khai `id()`. `UserEntity` đáp ứng nghĩa vụ bằng một phương thức `public`.

Ngược lại, nếu `BaseEntity` đã cung cấp phương thức `public String id()` có thân phù hợp, `UserEntity` có thể kế thừa triển khai đó mà không phải ghi đè thêm.

Một lớp con **không được phép chỉ thêm từ khóa `abstract` cho phương thức nhưng tự giữ lớp concrete**. Nghĩa vụ luôn đi cùng trạng thái abstract của lớp chưa hoàn chỉnh.

## 6. Nghĩa vụ đến từ interface và abstract class

Không chỉ phương thức abstract **tự khai báo** trong lớp mới tạo nghĩa vụ. Khi một abstract class `implements` interface nhưng chưa cung cấp phương thức cụ thể, nó được phép chuyển nghĩa vụ cho lớp con. Trường hợp `BaseEntity implements Identifiable` ở mục 5 minh họa chính xác điều này.

| Khía cạnh | `abstract class` | `interface` |
|---|---|---|
| Khai báo phương thức abstract | Ghi `abstract`; có thể `public`, `protected` hoặc package-private | Phương thức instance không có thân mặc định `public abstract` |
| Vai trò đối với lớp concrete | Phải hoàn tất mọi hợp đồng abstract kế thừa còn thiếu | Lớp `implements` phải hoàn tất mọi hợp đồng chưa có implementation phù hợp |
| Thành viên dùng chung | Có constructor, state instance và phương thức concrete | Có `default`/`static`/`private` method theo quy tắc riêng; không có constructor instance |
| Giới hạn kế thừa trực tiếp | Chỉ được `extends` một class | Có thể `implements` nhiều interface |

**Điều kiện quan trọng:** một method có cùng tên chưa chắc đã thực hiện nghĩa vụ. Cần đối chiếu chữ ký, kiểu trả về, ngoại lệ và phạm vi truy cập, đặc biệt khi lớp con nằm khác package. Một concrete method được kế thừa hợp lệ có thể đáp ứng hợp đồng mà không cần viết lại override. Ngược lại, một default method của interface không phải lúc nào cũng thắng phương thức kế thừa từ class; Java áp dụng quy tắc giải quyết xung đột theo hệ phân cấp. Các bài sau đi sâu vào từng ngoại lệ.

## 7. Chẩn đoán lớp concrete chưa hoàn tất hợp đồng

Khi compiler báo lớp con chưa thực hiện abstract method, hãy tìm **phương thức còn thiếu**, thay vì mặc định rằng thiếu từ khóa `abstract` ở lớp chứa.

| Hiện tượng | Câu hỏi cần kiểm tra | Hướng sửa |
|---|---|---|
| Lớp concrete chưa implement đủ | Có abstract method nào được kế thừa từ class hoặc interface? | Thêm implementation hợp lệ hoặc giữ lớp con là `abstract` |
| Có method trùng tên nhưng vẫn báo thiếu | Danh sách kiểu tham số có khác không? | Dùng `@Override` để phát hiện overload ngoài ý muốn |
| Có implementation nhưng compiler báo quyền truy cập yếu hơn | Lớp cha hoặc interface yêu cầu `public`/`protected`? | Giữ hoặc mở rộng visibility theo quy tắc override |
| Lớp con khác package không implement được hook | Hook ở lớp cha có phải package-private? | Dùng `protected`/`public` nếu cần mở rộng xuyên package hoặc hoàn tất hook trong package gốc |
| Chưa thể tạo instance của lớp nền | Bản thân lớp vẫn được khai báo `abstract`? | Tạo đối tượng từ một lớp concrete phù hợp |

Khai báo `private abstract`, `static abstract`, `final abstract` và đặt thân `{ ... }` cho abstract method là các lỗi cú pháp/modifier riêng. Những ví dụ sai cùng cách sửa chi tiết nằm ở các bài chuyên về cú pháp và modifier; chúng không thay đổi quy tắc kế thừa nghĩa vụ ở đây.

## 8. Các điểm tinh tế cần nhớ

### 8.1. Lớp abstract không phải “lớp không có constructor”

Lớp abstract có thể khai báo constructor để khởi tạo trạng thái chung. Constructor được chạy khi tạo đối tượng của lớp con; chỉ thao tác `new AbstractBase()` trực tiếp là không hợp lệ. Không thể khai báo constructor là `abstract`, vì constructor không được ghi đè như phương thức instance.

Tránh gọi trực tiếp phương thức có thể bị override (đặc biệt phương thức abstract) từ constructor của lớp cha: lời gọi có thể dispatch tới lớp con **trước khi các trường khởi tạo riêng của lớp con sẵn sàng**, tạo ra lỗi trạng thái khó phát hiện.

### 8.2. Lớp abstract có thể khai báo lại phương thức đã có thân thành abstract

Trong một **lớp con abstract**, đôi khi người thiết kế muốn buộc lớp con tiếp theo tự quyết định lại hành vi dù lớp cha đã có triển khai:

```java
class BaseNotifier {
    public void notifyUser() {
        System.out.println("Generic");
    }
}

abstract class CustomNotifier extends BaseNotifier {
    @Override
    public abstract void notifyUser();
}
```

Đây là khai báo hợp lệ: `CustomNotifier` biến yêu cầu `notifyUser()` thành abstract đối với lớp con cụ thể của nó, miễn tuân thủ quy tắc override. Đoạn code là minh họa khai báo, không chứa `main`.

### 8.3. Trường hợp đặc biệt: enum có phương thức abstract

Một `enum` có thể khai báo phương thức instance abstract nếu **mọi hằng enum cung cấp triển khai** thông qua thân lớp riêng của hằng. Không viết `abstract enum`. Ví dụ hoàn chỉnh trong `EnumAbstractDemo.java`:

```java
enum Level {
    LOW {
        @Override
        int score() {
            return 1;
        }
    },
    HIGH {
        @Override
        int score() {
            return 10;
        }
    };

    abstract int score();
}

public class EnumAbstractDemo {
    public static void main(String[] args) {
        System.out.println(Level.HIGH.score());
    }
}
```

```bash
javac -encoding UTF-8 EnumAbstractDemo.java
java EnumAbstractDemo
```

```text
10
```

Đây là trường hợp đặc biệt của Java cho thấy quy tắc “cứ có phương thức abstract thì phải viết `abstract class`” chỉ áp dụng trực tiếp cho khai báo `class` thông thường, không được máy móc áp vào interface hoặc enum.

## 9. Chọn abstract class hay interface khi thiết kế?

Khi chỉ cần quy định **những hành vi mà nhiều lớp không có chung tổ tiên vẫn thực hiện được**, interface thường là lựa chọn tự nhiên. Khi cần **chia sẻ trạng thái, constructor, logic chung** và để một số bước cho lớp con quyết định, abstract class phù hợp hơn. Một lớp Java chỉ được `extends` trực tiếp một lớp cha nhưng có thể `implements` nhiều interface.

Dù chọn cách nào, đừng thêm phương thức abstract nếu không có biến thể nào thực sự cần cung cấp triển khai riêng. Hợp đồng abstract tạo nghĩa vụ cho mọi lớp concrete trong hệ phân cấp liên quan, vì vậy nên đặt nó ở cấp phù hợp và dùng tên, kiểu trả về, tham số rõ ràng.

## 10. Tự kiểm tra

1. Một `abstract class` có bắt buộc khai báo ít nhất một phương thức `abstract` không? **Không.**
2. Một `class` bình thường có thể trực tiếp chứa `abstract void go();` không? **Không; lớp phải khai báo `abstract`, hoặc đổi phương thức thành có thân.**
3. Lớp con `abstract` có được trì hoãn triển khai phương thức abstract kế thừa không? **Có.**
4. Lớp con concrete có thể chỉ kế thừa triển khai hợp lệ từ lớp tổ tiên khác mà không tự viết override không? **Có, nếu nghĩa vụ đã được đáp ứng đầy đủ theo quy tắc kế thừa.**
5. Phương thức không có thân trong interface thường có mức truy cập và tính chất gì? **`public abstract` ngầm định.**
6. Có thể kết hợp `final` với khai báo lớp `abstract` không? **Không.**
7. Khi biến cha kiểu abstract gọi một phương thức abstract đã được lớp con cài đặt, mã nào chạy? **Triển khai tương ứng với đối tượng thực tế tại runtime.**

**Ghi nhớ:** Với lớp Java thông thường, *phương thức abstract buộc lớp chứa được đánh dấu abstract, nhưng lớp abstract không buộc phải có phương thức abstract*. Khi kế thừa, nghĩa vụ triển khai được chuyển tiếp cho đến khi một lớp concrete cung cấp hoặc kế thừa được triển khai hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-methods-003">Tham số, kiểu trả về và mệnh đề throws</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức abstract trong Java: tham số, kiểu trả về và mệnh đề throws

## 1. Ba thành phần tạo nên hợp đồng của phương thức

Một phương thức `abstract` khai báo **điều mà lớp con hoặc lớp triển khai phải cung cấp** nhưng không có thân phương thức. Bên cạnh tên phương thức, phần khai báo xác định ba khía cạnh quan trọng:

- **Danh sách tham số**: dữ liệu mà người gọi phải truyền vào, theo thứ tự và kiểu xác định.
- **Kiểu trả về**: kiểu kết quả mà người gọi được phép nhận (hoặc `void` nếu không trả về giá trị).
- **Mệnh đề `throws`**: các ngoại lệ checked mà lời gọi có thể cần xử lý hoặc khai báo tiếp.

Ví dụ khai báo trong abstract class:

~~~java
import java.io.IOException;

abstract class DocumentStore {
    public abstract CharSequence load(String id, int version) throws IOException;
}
~~~

Trong khai báo này, `id` là `String`, `version` là `int`, kết quả là `CharSequence`, còn `IOException` là ngoại lệ checked được công bố. Dấu `;` kết thúc khai báo; phương thức abstract **không được có thân `{ ... }`**.

Cùng ý tưởng trong interface:

~~~java
import java.io.IOException;

interface DocumentStore {
    CharSequence load(String id, int version) throws IOException;
}
~~~

Phương thức thông thường được khai báo trong interface như trên mặc nhiên là `public abstract` (có thể viết tường minh). Khi triển khai, phương thức tương ứng phải là `public`. Ngược lại, abstract class có thể khai báo phương thức abstract là `public`, `protected` hoặc package-private, tùy thiết kế API. Một phương thức abstract không thể đồng thời là `private`, `static` hoặc `final`: các đặc tính đó cản trở việc override.

## 2. Tham số: kiểu, thứ tự và khả năng override

### 2.1. Tham số hình thức và đối số thực tế

Ở khai báo `load(String id, int version)`, `id` và `version` là **tham số hình thức**. Trong lời gọi `store.load("manual", 2)`, `"manual"` và `2` là **đối số**. Người gọi phải cung cấp các đối số phù hợp với kiểu, thứ tự và số lượng tham số; Java sẽ kiểm tra tại thời điểm biên dịch.

Tên tham số giúp mã dễ đọc nhưng **không quyết định việc override**. Ví dụ, khai báo `process(String input, int count)` có thể được triển khai bằng `process(String text, int times)` vì hai danh sách kiểu vẫn tương ứng.

Java truyền tham số theo **giá trị**: với kiểu nguyên thủy, phương thức nhận bản sao giá trị; với kiểu tham chiếu, phương thức nhận bản sao giá trị tham chiếu. Do đó, có thể thay đổi trạng thái của đối tượng mutable mà tham chiếu cùng trỏ tới, nhưng gán lại tham số không làm biến tham chiếu ở nơi gọi trỏ sang đối tượng mới.

### 2.2. Quy tắc quan trọng khi override

Khi hiện thực phương thức abstract, phương thức override cần có tên và danh sách kiểu tham số tương ứng theo quy tắc method signature/subsignature của Java. Với ví dụ không dùng generic, hãy coi **số lượng, thứ tự và kiểu tham số phải giữ nguyên**:

~~~java
abstract class MessageFormatter {
    abstract String format(String content, int width);
}

class PlainFormatter extends MessageFormatter {
    @Override
    String format(String text, int width) {
        return text.trim();
    }
}
~~~

`@Override` khiến trình biên dịch xác nhận đây thực sự là override. Nếu vô tình đổi `String` thành `Object`, hoặc đảo thứ tự tham số, khai báo có thể trở thành **overload** khác, còn phương thức abstract cũ vẫn chưa được triển khai. Một lớp con concrete trong tình huống đó sẽ không biên dịch.

Các quy tắc bổ sung:

- **Đổi tên tham số**: được; tên không thuộc method signature.
- **Thêm/bớt/đổi kiểu tham số**: không thay thế được phương thức abstract ban đầu; có thể tạo overload.
- **`String...` (varargs)**: chỉ được đặt ở tham số cuối; ở mức kiểu mảng, `String...` tương ứng `String[]`.
- **`final` trên tham số**: chỉ ngăn gán lại biến tham số trong thân phương thức, không làm thay đổi hợp đồng override.
- **Generic**: quy tắc override có thêm khái niệm subsignature và type erasure; không suy ra rằng mọi khác biệt cú pháp của tham số generic đều là overload hợp lệ.

Đặc biệt, **kiểu trả về và `throws` không tham gia phân biệt hai phương thức overload**. Không thể có hai phương thức cùng tên, cùng danh sách tham số và chỉ khác kiểu trả về hoặc `throws`.

## 3. Kiểu trả về: hợp đồng và covariance

Kiểu trả về xác định điều người gọi có thể sử dụng sau lời gọi. Phương thức có kiểu trả về khác `void` phải trả về biểu thức có kiểu tương thích trên mọi đường thực thi kết thúc bình thường; phương thức `void` không trả về một giá trị.

Khi override phương thức abstract:

| Kiểu trả về của phương thức gốc | Kiểu ở phương thức override | Kết quả |
| --- | --- | --- |
| `int` | `int` | Hợp lệ |
| `int` | `long` | Không hợp lệ (không tự nới kiểu nguyên thủy khi override) |
| `void` | `void` | Hợp lệ |
| `void` | `String` | Không hợp lệ |
| `Number` | `Integer` | Hợp lệ: kiểu tham chiếu con, **covariant return** |
| `CharSequence` | `String` | Hợp lệ: `String` triển khai `CharSequence` |
| `String` | `Object` | Không hợp lệ: trả về rộng hơn |

Ví dụ minh họa (độc lập với chương trình `Main` ở phần sau):

~~~java
abstract class Metric {
    public abstract Number measure(String input);
}

class LengthMetric extends Metric {
    @Override
    public Integer measure(String input) {
        return input.length();
    }
}
~~~

Lớp con có thể trả về `Integer` vì đây là subtype của `Number`. Người gọi nhìn biến qua kiểu `Metric` vẫn có hợp đồng tĩnh là `Number`; còn mã trong `LengthMetric` biết cụ thể kiểu `Integer`.

Với generic, compiler còn áp dụng các quy tắc return-type-substitutability; đôi khi tương thích cần xét kiểu generic hoặc tạo cảnh báo unchecked. Không dựa vào việc ép kiểu bừa để vượt qua lỗi khai báo: hãy thiết kế kiểu trả về từ hợp đồng gốc cho đúng.

## 4. Mệnh đề throws: điều gì được phép phát sinh?

### 4.1. `throws` là khai báo, còn `throw` là hành động

`throws IOException` nằm ở **khai báo phương thức**, thông báo cho compiler và người gọi rằng thao tác có thể hoàn thành bằng ngoại lệ checked này. Trong **thân phương thức**, câu lệnh `throw new IOException("...")` thực sự ném một ngoại lệ.

~~~java
import java.io.IOException;

abstract class FileSource {
    abstract String read(String path) throws IOException;
}
~~~

Không có thân phương thức để ném ngoại lệ ở đây, nhưng bản khai báo vẫn ràng buộc cách người gọi xử lý. Một phương thức có thể liệt kê nhiều kiểu ngoại lệ checked bằng dấu phẩy, ví dụ `throws IOException, java.text.ParseException`, nếu hợp đồng thực sự cần cả hai.

**Checked exceptions** như `IOException`, `ParseException` phải được bắt bằng `try/catch` hoặc được khai báo `throws` tiếp ở phương thức gọi (với các ngoại lệ checked có thể phát sinh). **Unchecked exceptions** là `RuntimeException` cùng các lớp con và `Error` cùng các lớp con; Java không bắt buộc khai báo hay bắt các loại này. Có thể ghi một unchecked exception trong `throws` để mô tả API, nhưng điều đó không tạo thêm nghĩa vụ bắt ngoại lệ cho người gọi.

Không nên khai báo `throws Exception` tràn lan: kiểu quá rộng che mờ trường hợp lỗi và buộc người gọi xử lý quá chung chung. Đừng khai báo một ngoại lệ checked chỉ để làm “danh sách dự phòng” nếu API không cần hợp đồng đó.

### 4.2. Ràng buộc throws khi override

Phương thức override được:

1. Giữ nguyên các ngoại lệ checked đã khai báo.
2. Khai báo **loại checked hẹp hơn** (subtype của loại checked mà phương thức được override cho phép).
3. Bỏ hẳn mệnh đề `throws` nếu bản triển khai không ném ngoại lệ checked.
4. Ném hoặc khai báo unchecked exceptions phù hợp bất kể khai báo gốc, dù API nên tài liệu hóa trường hợp có ý nghĩa.

Phương thức override **không được mở rộng** danh sách checked exceptions bằng kiểu checked rộng hơn hoặc hoàn toàn mới so với hợp đồng của phương thức được override.

Ví dụ hợp lệ:

~~~java
import java.io.FileNotFoundException;
import java.io.IOException;

abstract class Resource {
    abstract String open(String path) throws IOException;
}

class MissingResource extends Resource {
    @Override
    String open(String path) throws FileNotFoundException {
        throw new FileNotFoundException(path);
    }
}

class FixedResource extends Resource {
    @Override
    String open(String path) {
        return "constant";
    }
}
~~~

`FileNotFoundException` là subtype của `IOException`; còn `FixedResource` bỏ `throws` vì triển khai của nó không cần ném checked exception.

Ví dụ **sai, cố ý không biên dịch**:

~~~java
import java.io.IOException;

abstract class Job {
    abstract void execute() throws IOException;
}

class InvalidJob extends Job {
    @Override
    void execute() throws Exception { // ERROR: Exception rộng hơn IOException
    }
}
~~~

Dù cả hai đều là checked exception, `Exception` rộng hơn `IOException`, nên Java từ chối override đó. Nếu lớp gốc không khai báo checked exception, lớp con cũng không thể tự thêm `throws IOException` cho cùng phương thức override.

### 4.3. Người gọi nhìn kiểu tĩnh, không chỉ nhìn triển khai

Nếu biến được khai báo theo interface hoặc abstract class với `throws IOException`, lời gọi qua biến đó vẫn chịu hợp đồng checked exception, **ngay cả khi đối tượng cụ thể override bằng phương thức không khai báo `throws`**. Trình biên dịch kiểm tra dựa trên khai báo phương thức được chọn theo kiểu tĩnh tại chỗ gọi.

Chẳng hạn, nếu `Resource resource = new FixedResource();` thì `resource.open("x")` vẫn phải đặt trong `try/catch` hoặc phương thức gọi khai báo `throws IOException`; còn `new FixedResource().open("x")` không đòi hỏi việc đó.

## 5. Ví dụ Java 17+: kết nối cả ba phần của hợp đồng

Ví dụ sau kết hợp interface, abstract class, danh sách tham số, kiểu trả về, `throws`, `throw` và xử lý ngoại lệ. Lưu toàn bộ vào tệp **Main.java**.

~~~java
import java.io.IOException;

interface Reader {
    String read(String id) throws IOException;
}

abstract class BaseReader implements Reader {
    protected abstract int sizeOf(String text);

    protected String prefix() {
        return "data:";
    }
}

final class MemoryReader extends BaseReader {
    @Override
    public String read(String id) throws IOException {
        if (id == null || id.isBlank()) {
            throw new IOException("invalid id");
        }
        return prefix() + id;
    }

    @Override
    protected int sizeOf(String text) {
        return text.length();
    }
}

public class Main {
    public static void main(String[] args) {
        BaseReader reader = new MemoryReader();

        try {
            String text = reader.read("notes");
            System.out.println(text);
            System.out.println(reader.sizeOf(text));
            reader.read("");
        } catch (IOException ex) {
            System.out.println("caught: " + ex.getMessage());
        }
    }
}
~~~

Biên dịch và chạy (JDK 17 trở lên):

~~~text
javac -encoding UTF-8 Main.java
java Main
~~~

Kết quả chuẩn:

~~~text
data:notes
10
caught: invalid id
~~~

Giải thích:

- `Reader.read(String id)` khai báo **một tham số kiểu `String`**, kết quả `String` và `throws IOException`; đây là hợp đồng của interface.
- `BaseReader` là abstract class nên được phép chưa triển khai `read` và tự thêm một phương thức abstract `sizeOf(String)` với kiểu trả về `int`.
- `MemoryReader` là lớp concrete: nó override đủ hai phương thức. `read` dùng quyền truy cập `public` đúng với phương thức từ interface; `sizeOf` giữ `protected`.
- Khi `id` rỗng, `read` thực sự ném `IOException` bằng `throw`. Khối `catch` xử lý ngoại lệ và in thông báo.
- `"data:notes"` có 10 ký tự. Lệnh `reader.read("")` chuyển điều khiển sang `catch`, nên không có dòng kết quả thứ tư.

## 6. Bảng kiểm tương thích khi triển khai

| Thành phần | Yêu cầu khi override phương thức abstract | Lỗi thường gặp |
| --- | --- | --- |
| Tên + tham số | Khớp method signature/subsignature; giữ số lượng, thứ tự và kiểu phù hợp | Đổi `String` thành `Object` rồi tưởng là override |
| Tên tham số | Có thể đổi | Nhầm tên là một phần signature |
| Kiểu trả về | Cùng `void`/kiểu nguyên thủy; kiểu tham chiếu có thể covariant theo luật Java | Đổi `int` thành `long`, hoặc `String` thành `Object` |
| `throws` checked | Giữ nguyên, thu hẹp hoặc bỏ; không thêm loại checked vượt hợp đồng gốc | Đổi `IOException` thành `Exception` |
| Ngoại lệ unchecked | Không bị quy tắc thu hẹp `throws` checked chi phối | Tưởng `RuntimeException` bắt buộc phải khai báo |
| Quyền truy cập | Không giảm khả năng truy cập khi override | Triển khai phương thức interface bằng `protected` |
| Thân phương thức | Khai báo abstract kết thúc bằng `;`; triển khai concrete có thân | Đặt `{ ... }` sau `abstract` hoặc thiếu override |
| `@Override` | Nên dùng để compiler kiểm tra chủ đích | Viết nhầm tham số làm xuất hiện overload không mong muốn |

## 7. Những nhầm lẫn cần tránh

**Nhầm giữa overload và override.** Hai phương thức có tên giống nhau nhưng kiểu/thứ tự tham số khác nhau là các overload. Việc viết thêm overload không thực hiện nghĩa vụ triển khai phương thức abstract đã có. Hãy gắn `@Override` và sửa lỗi compiler thay vì bỏ annotation.

**Nhầm `throws` là lời hứa luôn ném lỗi.** `throws IOException` cho phép tình huống lỗi checked tương ứng được truyền ra ngoài; nó không bắt buộc mỗi lần chạy phải ném ngoại lệ, và không yêu cầu mọi lớp triển khai đều ghi lại cùng mệnh đề `throws`.

**Nhầm kiểu trả về là một phần chữ ký overload.** Không thể giải quyết hai hành vi bằng hai phương thức chỉ khác `String`/ `Object` ở giá trị trả về. Nếu cần hai phép toán khác nhau, đổi tên hoặc danh sách tham số theo một thiết kế rõ ràng.

**Làm hẹp quyền truy cập của interface method.** Một khai báo `String read(String id);` trong interface mặc nhiên là `public abstract`. Lớp hiện thực phải khai báo `public String read(String id)`, không được dùng package-private hoặc `protected`.

**Bỏ quên kiểu tĩnh của người gọi.** Dù một lớp concrete bỏ `throws`, lời gọi qua biến kiểu cha vẫn được kiểm tra theo khai báo ở cha. Khi thiết kế API, nên đưa những checked exceptions có ý nghĩa vào hợp đồng chung ngay từ đầu.

## 8. Tự kiểm tra

1. Một phương thức `abstract Number evaluate(String text) throws IOException;` có thể được override bằng `Integer evaluate(String input)` không? **Có** (giả sử quyền truy cập hợp lệ): `Integer` là kiểu trả về covariant, tên tham số không quan trọng và có thể bỏ `throws`.
2. Có thể đổi tham số thành `Object input` rồi dùng `@Override` không? **Không**, vì đây không còn là cùng phương thức theo signature thông thường; nếu lớp con concrete chưa triển khai `evaluate(String)`, nó sẽ lỗi biên dịch.
3. Có thể đổi `throws IOException` thành `throws Exception` không? **Không**, vì checked exception ở override rộng hơn hợp đồng gốc.
4. Có cần bắt `IOException` nếu gọi qua tham chiếu kiểu cha có khai báo `throws IOException` không? **Có**, hoặc tiếp tục khai báo `throws` ở phương thức gọi, bất kể triển khai cụ thể có thực sự ném lỗi hay không.

Khi đọc một khai báo abstract, hãy xác định riêng **đầu vào**, **kết quả** và **ngoại lệ checked**; khi triển khai, kiểm tra chúng cùng quyền truy cập bằng `@Override`. Đó là cách giữ hợp đồng polymorphism nhất quán giữa người gọi và mọi lớp con.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-methods-004">Phạm vi public, protected và package-private</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi `public`, `protected` và package-private của phương thức abstract trong Java

Access modifier quyết định mã nào có thể gọi một phương thức và lớp con nào có thể override nó. Với phương thức `abstract` trong một class, Java cho phép `public`, `protected` hoặc package-private (không ghi modifier). Ba lựa chọn này tạo ra các hợp đồng có phạm vi khác nhau.

## 1. So sánh ba mức truy cập

```java
public abstract class Task {
    public abstract String title();
    protected abstract int priority();
    abstract String internalCode();
}
```

| Khai báo trong class | Mã cùng package gọi được? | Lớp con ở package khác có thể override? | Mã thông thường ở package khác gọi trực tiếp? |
|---|---|---|---|
| `public abstract` | Có | Có; override phải `public` | Có, nếu kiểu chứa truy cập được |
| `protected abstract` | Có | Có; override phải `protected` hoặc `public` | Không tùy ý; phải thỏa quy tắc `protected` |
| package-private `abstract` | Có | Không bằng cách khai báo cùng chữ ký ở package khác | Không |

Package-private đôi khi được gọi là *default access*. Đây không phải từ khóa `default` dùng để khai báo phương thức có thân trong interface. Quyền package-private dựa trên package Java được khai báo trong source, không dựa vào việc hai package có tên gần nhau hoặc nằm trong cùng thư mục cha.

## 2. Quy tắc visibility khi override

Lớp con được giữ nguyên hoặc mở rộng mức truy cập của phương thức mà nó thật sự override; không được thu hẹp:

| Modifier ở lớp cha | Modifier override hợp lệ |
|---|---|
| `public` | `public` |
| `protected` | `protected`, `public` |
| package-private | package-private, `protected`, `public` nếu lớp con thật sự cùng package và nhìn thấy phương thức |

Ví dụ sai:

```java
abstract class Job {
    public abstract void run();
}

class BadJob extends Job {
    @Override
    protected void run() { // Lỗi: giảm public thành protected
        System.out.println("run");
    }
}
```

Sửa thành `public void run()`. Dùng `@Override` để compiler kiểm tra rằng chữ ký thực sự override một phương thức kế thừa. Đổi visibility không thể biến một phương thức không nhìn thấy thành override hợp lệ.

## 3. Ví dụ chạy được: gọi API công khai, giữ hook nội bộ

Các tệp dưới đây nằm trong cùng package `academy.tasks`, ngoại trừ `Main` ở package `app`.

**`src/academy/tasks/Task.java`**

```java
package academy.tasks;

public abstract class Task {
    public abstract String title();
    protected abstract int points();
    abstract String code();

    public final String summary() {
        return title() + " | " + points() + " | " + code();
    }
}
```

**`src/academy/tasks/ReviewTask.java`**

```java
package academy.tasks;

public final class ReviewTask extends Task {
    @Override
    public String title() {
        return "Review";
    }

    @Override
    protected int points() {
        return 3;
    }

    @Override
    String code() {
        return "REV-01";
    }
}
```

**`src/app/Main.java`**

```java
package app;

import academy.tasks.ReviewTask;
import academy.tasks.Task;

public class Main {
    public static void main(String[] args) {
        Task task = new ReviewTask();
        System.out.println(task.title());
        System.out.println(task.summary());
    }
}
```

Biên dịch từ thư mục chứa `src`:

```bash
javac -encoding UTF-8 -d out src/academy/tasks/Task.java src/academy/tasks/ReviewTask.java src/app/Main.java
java -cp out app.Main
```

Kết quả:

```text
Review
Review | 3 | REV-01
```

`Main` gọi được `title()` và `summary()` vì chúng là public. `points()` là protected, còn `code()` là package-private; mã trong `app` không được gọi trực tiếp hai phương thức này. Tuy vậy, `summary()` có thể gọi chúng bên trong `Task`, nơi access hợp lệ, và dynamic dispatch chọn implementation ở `ReviewTask`. Giới hạn truy cập ở mã nguồn không ngăn lời gọi nội bộ hợp lệ đến implementation.

## 4. Khi lớp con nằm ở package khác

Subclass ở package khác có thể override `protected abstract` hoặc `public abstract` nếu tuân thủ chữ ký và visibility. Với phương thức package-private abstract, khai báo một phương thức cùng tên và tham số ở package khác **không** đáp ứng hợp đồng của lớp cha; một lớp con concrete trực tiếp vẫn còn phương thức abstract chưa được triển khai.

Nếu lớp ngoài package phải tự cung cấp implementation, hãy thiết kế hook là `protected abstract` hoặc `public abstract`. Nếu hook phải được giữ nội bộ trong package của lớp cha, có thể để một lớp cùng package triển khai nó trước khi mở rộng hệ phân cấp ra ngoài. Bài chuyên về package-private xuyên package trình bày trường hợp này và cách triển khai lớp trung gian chi tiết hơn.

## 5. Khác biệt của interface

Phương thức abstract thông thường trong interface mặc định là `public abstract`, không phải package-private:

```java
public interface Action {
    void execute(); // public abstract ngầm định
}

final class PrintAction implements Action {
    @Override
    public void execute() {
        System.out.println("done");
    }
}
```

Lớp triển khai bắt buộc giữ `public`; viết `protected` hoặc package-private sẽ giảm visibility và bị compiler từ chối. Interface có thể có phương thức `default`, `static` hoặc `private` **có thân**, nhưng những dạng này không tạo ra một abstract method package-private.

## 6. Chọn modifier theo mục đích

- Dùng `public abstract` khi hành vi là một phần của API mà mã khách hàng cần gọi.
- Dùng `protected abstract` khi muốn cho lớp con mở rộng hành vi nhưng không công khai lời gọi cho mọi caller.
- Dùng package-private abstract khi implementation được kiểm soát trong cùng package và không cần subclass bên ngoài tự hoàn thành hook.
- Trước khi đổi visibility để chữa lỗi compiler, hãy xác định ai cần gọi phương thức và package của lớp triển khai. `protected` cho phép override xuyên package nhưng không biến phương thức thành API công khai cho mọi đối tượng.

## 7. Lỗi thường gặp và tự kiểm tra

| Triệu chứng | Nguyên nhân cần kiểm tra |
|---|---|
| `attempting to assign weaker access privileges` | Override dùng visibility hẹp hơn hợp đồng gốc |
| `does not override abstract method` | Chữ ký sai, chưa triển khai đủ, hoặc phương thức package-private nằm ở package khác |
| Không gọi được `protected` từ mã ngoài | Caller không thỏa quy tắc truy cập `protected` |
| Implement interface báo visibility yếu hơn | Quên rằng abstract method trong interface là public |

1. `abstract void save();` trong class thuộc mức nào? **Package-private.**
2. Lớp con khác package có thể override `protected abstract void save();` bằng `public void save()` không? **Có.**
3. `void save();` trong interface có package-private không? **Không, nó ngầm định public abstract.**
4. Lớp con khác package khai báo cùng chữ ký với một package-private abstract method đã hoàn thành hợp đồng chưa? **Chưa; đó không phải override hợp lệ.**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-methods-005">Vì sao abstract không kết hợp với private, static hoặc final</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao phương thức `abstract` không thể kết hợp với `private`, `static` hoặc `final` trong Java?

## 1. Bản chất của một phương thức `abstract`

Trong Java, một phương thức trừu tượng (`abstract method`) **khai báo một hợp đồng nhưng không có phần thân**, và yêu cầu lớp con cụ thể cung cấp phần triển khai (trực tiếp hoặc thông qua một triển khai đã được kế thừa hợp lệ). Cơ chế quan trọng ở đây là **ghi đè phương thức instance (`override`)** và **đa hình lúc chạy (`dynamic dispatch`)**.

Ví dụ:

```java
abstract class Animal {
    public abstract String sound();
}

final class Dog extends Animal {
    @Override
    public String sound() {
        return "Woof";
    }
}
```

Đây là khai báo các lớp hợp lệ, nhưng không phải một chương trình độc lập có `main`. `Animal` xác định rằng mọi lớp con cụ thể phải có cách trả về âm thanh. `Dog` hiện thực hợp đồng đó bằng cách override `sound()`.

Từ bản chất này, ta rút ra ba yêu cầu đối với một phương thức `abstract` của class:

1. Phương thức phải **có khả năng được lớp con ghi đè** theo quy tắc truy cập của Java.
2. Nó phải là **phương thức instance**, vì override và dynamic dispatch làm việc với phương thức instance.
3. Nó không được **cấm lớp con ghi đè**.

Ba từ khóa `private`, `static`, `final` lần lượt xung đột với những yêu cầu trên. Java phát hiện sự kết hợp không hợp lệ ngay **lúc biên dịch**, trước khi chạy chương trình.

> **Phạm vi cần phân biệt:** Quy tắc “không kết hợp” ở đây áp dụng cho **khai báo một phương thức `abstract`**. Một **`abstract class`** vẫn có thể chứa trường `private`, phương thức `private`, phương thức `static` và phương thức `final` bình thường, miễn những phương thức đó có cách khai báo hợp lệ. Không nên nhầm quy tắc modifier của method với modifier của class.

## 2. Vì sao `abstract private` là mâu thuẫn?

Một phương thức `private` chỉ được truy cập trong phạm vi lớp khai báo nó; phương thức `private` **không được kế thừa và không thể bị lớp con override**. Ngược lại, `abstract` cần một triển khai do lớp con cung cấp theo quan hệ kế thừa/override. Nếu đặt cả hai trên cùng một phương thức, lớp con không có phương thức trừu tượng khả kiến để thực hiện hợp đồng đó.

**Ví dụ cố ý sai, không biên dịch được:**

```java
abstract class Parent {
    private abstract void process(); // LỖI: private và abstract
}
```

Cách đúng là chọn phạm vi truy cập cho phép lớp con triển khai, thường là `protected` hoặc `public`:

```java
abstract class Parent {
    protected abstract void process();
}

final class Child extends Parent {
    @Override
    protected void process() {
        System.out.println("Processing");
    }
}
```

Nếu hợp đồng cần được mã bên ngoài gọi trực tiếp, dùng `public abstract`. Nếu chỉ dành cho lớp cha và lớp con cộng tác, `protected abstract` thường phù hợp.

### Phương thức trùng tên trong lớp con chưa chắc là override

Xét ví dụ hợp lệ sau:

```java
class Base {
    private void ping() {
        System.out.println("Base");
    }

    void runBasePing() {
        ping();
    }
}

class Derived extends Base {
    public void ping() {
        System.out.println("Derived");
    }
}
```

`Derived.ping()` **không override** `Base.ping()`; đó là phương thức riêng của `Derived` có cùng tên và danh sách tham số. Lời gọi `ping()` bên trong `Base.runBasePing()` vẫn trỏ tới phương thức `private` của `Base`. Đặt `@Override` trên `Derived.ping()` trong trường hợp này sẽ báo lỗi biên dịch vì thực tế không có phương thức được override. Chính vì vậy một `private abstract` không thể tạo ra hợp đồng kế thừa như mong muốn.

**Khi nào vẫn dùng `private` trong abstract class?** Dùng cho các bước xử lý nội bộ đã có thân phương thức hoặc cho dữ liệu chỉ lớp nền được quyền thao tác. Bước bắt buộc lớp con tùy biến cần được khai báo `protected abstract` hoặc `public abstract` thay vì `private abstract`.

## 3. Vì sao `abstract static` là mâu thuẫn?

Một phương thức `static` **thuộc về lớp**, được phân giải theo kiểu hoặc tên lớp tại lúc biên dịch; nó không tham gia dynamic dispatch dựa trên đối tượng như phương thức instance.

Một phương thức `abstract` lại cần lớp con cung cấp implementation thông qua **override phương thức instance**. Java cho phép lớp con **che khuất (`hide`)** một phương thức static bằng một phương thức static cùng chữ ký theo các điều kiện hợp lệ, nhưng **hiding không phải overriding**. Vì vậy không tồn tại “static abstract method” của class theo kiểu lớp con phải override.

**Ví dụ cố ý sai, không biên dịch được:**

```java
abstract class Parent {
    public static abstract void load(); // LỖI: static và abstract
}
```

Để định nghĩa một hành vi mà mọi đối tượng con phải cung cấp, bỏ `static`:

```java
abstract class Parent {
    public abstract void load();
}

final class Child extends Parent {
    @Override
    public void load() {
        System.out.println("Loaded");
    }
}
```

Để định nghĩa một **tiện ích dùng chung không cần đối tượng**, bỏ `abstract` và giữ một phương thức `static` có thân:

```java
abstract class Parent {
    public static String formatCode(int code) {
        return "CODE-" + code;
    }
}
```

Gọi `Parent.formatCode(7)` sẽ cho chuỗi `"CODE-7"`. Lớp nền dù là `abstract` vẫn được phép chứa và cung cấp phương thức static.

### Phân biệt overriding và static hiding bằng chương trình chạy được

Lưu ví dụ dưới vào `StaticHidingDemo.java`:

```java
class Base {
    static String label() {
        return "Base";
    }

    String describe() {
        return "Base instance";
    }
}

class Derived extends Base {
    static String label() {
        return "Derived";
    }

    @Override
    String describe() {
        return "Derived instance";
    }
}

public class StaticHidingDemo {
    public static void main(String[] args) {
        Base value = new Derived();
        System.out.println(Base.label());
        System.out.println(Derived.label());
        System.out.println(value.describe());
        System.out.println(value.label()); // Hợp lệ nhưng không nên gọi static qua biến
    }
}
```

Chạy:

```bash
javac StaticHidingDemo.java
java StaticHidingDemo
```

Kết quả chuẩn:

```text
Base
Derived
Derived instance
Base
```

Giải thích: `value.describe()` gọi implementation của **đối tượng thực tế `Derived`** nhờ overriding. Trong khi đó `value.label()` được chọn theo **kiểu khai báo `Base` của biến**, nên trả `"Base"`; cách viết này dễ gây nhầm nên hãy gọi rõ `Base.label()` hoặc `Derived.label()`. Đó là lý do dùng `static` không thể thay hợp đồng `abstract` vốn cần đa hình.

## 4. Vì sao `abstract final` là mâu thuẫn?

`final` đặt trên một phương thức instance nghĩa là **lớp con không được override phương thức ấy**. Trong khi đó, một phương thức `abstract` chưa có thân và đòi hỏi lớp con cung cấp implementation. Hai yêu cầu đối nghịch trực tiếp: một bên **bắt buộc được triển khai bằng override**, bên kia **cấm override**.

**Ví dụ cố ý sai, không biên dịch được:**

```java
abstract class Parent {
    public abstract final void save(); // LỖI: abstract và final
}
```

Nếu muốn lớp con **bắt buộc triển khai khác nhau**, dùng `abstract`, không dùng `final`:

```java
abstract class Parent {
    public abstract void save();
}
```

Nếu muốn có **một triển khai cố định, cấm lớp con thay đổi**, dùng `final` với **thân phương thức**:

```java
abstract class Parent {
    public final void save() {
        System.out.println("Saved by parent");
    }
}
```

Việc lớp `Parent` là `abstract` không làm cho `save()` trở thành abstract. Trong abstract class, phương thức `final` là một phương thức **đã triển khai**. Lớp con có thể kế thừa và gọi nó, nhưng không thể override nó.

### Mẫu thiết kế phối hợp đúng: final điều phối, abstract tùy biến

Một kỹ thuật hữu ích là **Template Method**: lớp cha dùng phương thức `final` để cố định thứ tự xử lý, rồi gọi một hoặc nhiều bước `abstract` cho lớp con thực hiện. Nhờ đó, **quy trình chung ổn định** mà **hành vi cụ thể vẫn mở rộng được**.

Ví dụ:

```java
abstract class Task {
    public final void run() {
        System.out.println("Start");
        perform();
        System.out.println("Finish");
    }

    protected abstract void perform();
}

final class EmailTask extends Task {
    @Override
    protected void perform() {
        System.out.println("Send email");
    }
}
```

`Task.run()` không thể bị lớp con thay thế, nhưng bước `perform()` được triển khai khác nhau. Đây là cách phối hợp `abstract` và `final` **trên hai phương thức khác nhau**, hoàn toàn hợp lệ. Đoạn này chỉ khai báo lớp; ví dụ chương trình độc lập xuất hiện ở phần tiếp theo.

## 5. Ví dụ Java 17+ hoàn chỉnh: dùng `private`, `static`, `final` đúng chỗ trong một abstract class

Lưu **toàn bộ** mã sau vào tệp `Demo.java` (không đặt khai báo `package`):

```java
abstract class Payment {
    private final int amount;

    protected Payment(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.amount = amount;
    }

    public final void execute() {
        logStart();
        process(amount);
        System.out.println("Done");
    }

    private void logStart() {
        System.out.println("Start payment");
    }

    protected abstract void process(int amount);

    public static String unit() {
        return "VND";
    }
}

final class CardPayment extends Payment {
    CardPayment(int amount) {
        super(amount);
    }

    @Override
    protected void process(int amount) {
        System.out.println("Charge card: " + amount + " " + Payment.unit());
    }
}

public class Demo {
    public static void main(String[] args) {
        Payment payment = new CardPayment(500);
        payment.execute();
    }
}
```

Chạy bằng **JDK 17+** tại thư mục chứa tệp:

```bash
javac Demo.java
java Demo
```

**Stdout chính xác:**

```text
Start payment
Charge card: 500 VND
Done
```

Quan sát từng thành phần:

- `Payment` là **abstract class**: không tạo `new Payment(...)` trực tiếp, nhưng constructor vẫn được gọi khi tạo `CardPayment`.
- `private final int amount` là **trường dữ liệu instance** được giữ kín và không thể gán lại sau khi khởi tạo thành công; `final` trên trường khác với `final` trên phương thức.
- `private void logStart()` có **phần thân**, phục vụ nội bộ lớp nền; không có nhu cầu cho lớp con override.
- `static String unit()` (ở đây là `public static`) là **phương thức dùng chung**, gọi qua tên lớp `Payment.unit()`, không cần đối tượng.
- `final void execute()` (ở đây là `public final`) cố định trình tự **ghi log → xử lý → thông báo hoàn tất**.
- `protected abstract void process(int amount)` không có thân, nên `CardPayment` phải cung cấp cách xử lý cụ thể.
- Biến `Payment payment` giữ đối tượng `CardPayment`. Khi `execute()` gọi `process(amount)`, Java chọn implementation ở `CardPayment` theo **đối tượng thực tế**.

**Ý nghĩa thiết kế:** ba từ khóa `private`, `static`, `final` đều có ích trong một abstract class. Vấn đề chỉ xuất hiện khi đem một trong ba kết hợp **ngay trên phương thức `abstract` cần override**.

## 6. Quy tắc compiler và những trường hợp dễ nhầm

| Khai báo | Hợp lệ? | Lý do |
| --- | --- | --- |
| `protected abstract void work();` trong abstract class | Có | Lớp con có thể override |
| `public abstract void work();` trong abstract class | Có | Hợp đồng public, có thể override |
| `private abstract void work();` | Không | Private không được override |
| `static abstract void work();` | Không | Static tham gia hiding, không tham gia overriding |
| `final abstract void work();` | Không | Final cấm override |
| `private void helper() { ... }` trong abstract class | Có | Hàm nội bộ đã có thân |
| `static void helper() { ... }` trong abstract class | Có | Hàm cấp lớp đã có thân |
| `final void run() { ... }` trong abstract class | Có | Hàm cụ thể không cho override |
| `private final int id;` trong abstract class | Có | Field riêng; phải được gán đúng quy tắc final |
| `abstract final class C { }` | Không | Abstract class cần có khả năng được kế thừa để tạo lớp con cụ thể; final class cấm kế thừa |

Lưu ý thêm về **cấp class**: `abstract static class Nested { ... }` là **hợp lệ khi `Nested` là một lớp thành viên `static` bên trong lớp khác**. Từ `static` ở đây mô tả **nested class** không gắn với instance của lớp bao ngoài, không phải một `static abstract method`. Không được từ quy tắc cấm ở cấp phương thức suy ra mọi cách viết có `abstract` và `static` đều sai.

### Interface có làm ba quy tắc này thay đổi không?

**Không.** Những điểm sau cần được tách bạch:

1. Trong interface, một phương thức không có thân theo kiểu thông thường mặc nhiên là **`public abstract`**; có thể viết tường minh `public abstract void send();`.
2. Interface hiện đại cho phép **phương thức `default` có thân**, **phương thức `static` có thân**, và từ Java 9 có **phương thức `private` có thân** để dùng nội bộ interface. Không có nghĩa là `private abstract` hoặc `static abstract` được cho phép.
3. Ví dụ hợp lệ về các loại phương thức khác nhau trong cùng một interface:

```java
interface MessageService {
    void send(String message); // public abstract theo ngầm định

    default void sendWelcome() {
        send(prefix() + "Welcome");
    }

    static String version() {
        return "1.0";
    }

    private String prefix() {
        return "[Message] ";
    }
}
```

Đây là khai báo interface hợp lệ trên Java 17+, không phải ứng dụng có `main`. `prefix()` là helper nội bộ đã có thân; `version()` được gọi bằng `MessageService.version()`; `send(String)` là hợp đồng phải được implementation đáp ứng. Interface không cho phép đặt `final` lên một phương thức interface; việc bổ sung `default` cũng không khiến `abstract final` trở nên hợp lệ.

### Nếu lớp con vẫn là abstract thì sao?

Một abstract subclass **có thể chưa triển khai** phương thức abstract mà nó kế thừa:

```java
abstract class Job {
    protected abstract void perform();
}

abstract class TimedJob extends Job {
    // Vẫn abstract, nên chưa bắt buộc triển khai perform().
}

final class ReportJob extends TimedJob {
    @Override
    protected void perform() {
        System.out.println("Create report");
    }
}
```

Nghĩa vụ triển khai được hoàn tất trước khi tạo **lớp cụ thể** `ReportJob`. Điều này không làm thay đổi quy tắc: phương thức `perform()` vẫn phải có khả năng override, vì cuối cùng một lớp cụ thể cần cung cấp implementation. Một lớp con cụ thể không thực hiện đủ hợp đồng sẽ bị compiler từ chối.

## 7. Nhận diện và sửa lỗi trong thực tế

Khi thấy compiler báo tổ hợp modifier không hợp lệ, hãy xác định **ý định thiết kế của phương thức** trước:

**Trường hợp A — Lớp con bắt buộc cung cấp hành vi.** Khai báo phương thức **instance `protected abstract` hoặc `public abstract`**; bỏ `private`, `static`, `final`. Lớp chứa phương thức phải là abstract class (hoặc dùng khai báo phương thức trừu tượng hợp lệ trong interface).

**Trường hợp B — Chỉ là helper nội bộ.** Giữ `private`, cung cấp **thân phương thức** và bỏ `abstract`. Nếu lớp con phải tùy biến một bước, tách riêng một hook `protected abstract` có chủ ý; tránh lộ toàn bộ helper.

**Trường hợp C — Hàm tiện ích cấp lớp.** Giữ `static`, cung cấp **thân phương thức** và bỏ `abstract`. Nếu yêu cầu hành vi thay đổi tùy implementation lúc chạy, thiết kế lại thành **instance method** hoặc một đối tượng chiến lược/interface; static hiding không đáp ứng đa hình.

**Trường hợp D — Khung xử lý bắt buộc giữ nguyên.** Giữ phương thức điều phối `final` **có thân**; để phần cần mở rộng trong các phương thức `abstract` khác. Có thể áp dụng Template Method như ví dụ `Payment.execute()`/ `Payment.process()`.

**Trường hợp E — Vừa muốn `abstract` vừa muốn `final` trên class.** Thiết kế lại: một class không thể vừa yêu cầu có lớp con để hoàn chỉnh hợp đồng trừu tượng vừa cấm mọi lớp kế thừa nó. Nếu muốn đóng hệ phân cấp nhưng cho phép một số lớp con, cân nhắc **sealed class** (Java 17+) theo ràng buộc cho phép kế thừa có kiểm soát; đây là cơ chế riêng, không cho phép viết `abstract final class`.

## 8. Câu hỏi kiểm tra

1. Vì sao một phương thức `private` trong lớp cha không thể bị lớp con override, dù lớp con khai báo phương thức cùng tên?
2. Phương thức `static` khác phương thức instance ở thời điểm Java lựa chọn implementation như thế nào?
3. Vì sao `final` và `abstract` không thể cùng xuất hiện trên một phương thức?
4. Trong ví dụ `Payment`, tại sao `execute()` được `final` mà `process()` vẫn `abstract`?
5. `abstract class` có được chứa `private`, `static`, `final` không? Hãy đưa ra một ví dụ hợp lệ cho mỗi loại.
6. Một phương thức `private` có thân trong interface Java 17 có trái với quy tắc cấm `private abstract` không?

**Đáp án ngắn:** (1) `private` không được kế thừa/override; khai báo trùng tên ở lớp con là phương thức riêng. (2) Static được phân giải theo lớp/kiểu tại biên dịch; instance override dùng kiểu thực tế lúc chạy. (3) `abstract` đòi override, `final` cấm override. (4) Lớp cha bảo vệ khung quy trình và cho lớp con triển khai đúng bước tùy biến. (5) Có: `private void helper() { }`, `static void util() { }`, `final void run() { }` bên trong abstract class. (6) Không, vì phương thức `private` của interface **có thân** và phục vụ logic nội bộ, không phải hợp đồng abstract.

Ghi nhớ mối quan hệ cốt lõi: **`abstract` cần một phương thức instance có thể được lớp con override; `private` ngăn override, `static` không được override, còn `final` cấm override.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-methods-006">Các modifier không hợp lệ khác của phương thức abstract</a>

<details>
<summary>Xem chi tiết</summary>

# Các modifier ít gặp không hợp lệ với phương thức `abstract` trong Java

Phương thức `abstract` không có thân Java và cần được hiện thực theo hợp đồng kế thừa. Ngoài ba xung đột thường gặp `private`, `static` và `final`, còn có các modifier ít gặp là `synchronized`, `native`, `strictfp`, cùng từ khóa `default` trong interface. Bài này tập trung vào những trường hợp ít gặp và phân biệt chúng với từ khóa vốn không phải modifier của phương thức.

## 1. `synchronized abstract`: không có thân để khóa khi thực thi

```java
abstract class Job {
    public synchronized abstract void runJob(); // Lỗi biên dịch
}
```

`synchronized` kiểm soát việc lấy monitor khi một phương thức có thân được thực thi. Một khai báo abstract chưa có thân nên không thể đặt chính sách khóa thực thi lên khai báo đó. Lớp triển khai có thể tự chọn đồng bộ hóa:

```java
abstract class Job {
    public abstract void runJob();
}

final class SafeJob extends Job {
    @Override
    public synchronized void runJob() {
        System.out.println("Running safely");
    }
}
```

Hai lớp trên là khai báo hợp lệ. Tuy nhiên, `abstract` không tự bảo đảm mọi lớp con đều dùng `synchronized`; nếu tính đúng đắn cần một chính sách luồng chung, hãy đặt cơ chế đó trong một phương thức cụ thể được chia sẻ hoặc thiết kế rõ hợp đồng và trạng thái dùng chung.

## 2. `native abstract`: hai cơ chế hiện thực khác nhau

```java
abstract class NativeBridge {
    public native abstract int callNative(); // Lỗi biên dịch
}
```

`native int callNative();` có thể hợp lệ mà không có thân Java vì phần hiện thực được cung cấp qua cơ chế native như JNI. Khi gọi thật, chương trình vẫn cần thư viện và hàm native được liên kết đúng; nếu thiếu, có thể gặp `UnsatisfiedLinkError`.

Ngược lại, `abstract int callNative();` yêu cầu một lớp con Java cung cấp phần thân. Không thể gộp hai cách cung cấp implementation trên cùng một phương thức. Hãy chọn `native` khi hiện thực nằm ở mã native, hoặc `abstract` khi lớp con Java phải hiện thực hợp đồng.

## 3. `strictfp abstract`: modifier về phép tính không thay thế thân phương thức

```java
abstract class Geometry {
    public strictfp abstract double area(); // Lỗi biên dịch
}
```

`strictfp` từng được dùng để ràng buộc ngữ nghĩa phép tính dấu phẩy động. Từ Java 17, phép tính dấu phẩy động đã có ngữ nghĩa nghiêm ngặt mặc định, nên `strictfp` trên khai báo được phép dùng không còn cần thiết và có thể phát cảnh báo. Dù vậy, `strictfp abstract` vẫn là tổ hợp không hợp lệ. Khai báo `public abstract double area();` là hợp lệ.

## 4. `default abstract` trong interface

Một phương thức instance thông thường không có thân trong interface mặc định là `public abstract`:

```java
interface MessageFormatter {
    String format(String text);
}
```

Một phương thức `default` lại cung cấp phần thân mặc định:

```java
interface Formatter {
    String format(String text);

    default String wrap(String text) {
        return "[" + format(text) + "]";
    }

    static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private String decorate(String text) {
        return "[" + text + "]";
    }
}
```

`default`, `static` và `private` trong ví dụ đều có thân. Khai báo sau không hợp lệ:

```java
interface InvalidFormatter {
    default abstract String format(String text); // Lỗi biên dịch
}
```

`default` yêu cầu một implementation mặc định, còn `abstract` biểu thị không có thân. Trong interface, phương thức `private` cũng phải có thân; nó là helper nội bộ, không phải hợp đồng cho lớp triển khai. Đừng nhầm từ khóa `default` này với mức truy cập package-private của thành viên class không ghi access modifier.

## 5. Từ khóa không phải modifier của phương thức

Một số từ khóa vốn áp dụng cho thành phần khác, nên khai báo phương thức với chúng sai dù có hay không có `abstract`:

| Từ khóa | Thành phần áp dụng | Lý do |
|---|---|---|
| `volatile` | Field | Không phải modifier của method |
| `transient` | Field | Điều khiển serialization của field, không phải method |
| `sealed` / `non-sealed` | Class hoặc interface trong hierarchy sealed | Kiểm soát kiểu nào được kế thừa/thực hiện, không áp dụng lên method |

Ví dụ `volatile abstract void compute();` sai vì `volatile` không phải modifier của method; `volatile void compute() { ... }` cũng sai. Constructor cũng không thể khai báo `abstract`, vì constructor không được override như instance method. Không có `abstract field` hoặc `abstract constructor` trong Java.

Để đối chiếu, `private`, `static` và `final` cũng không được kết hợp với phương thức abstract: private ngăn override, static dùng method hiding thay vì override instance, còn final cấm override. Lớp abstract vẫn có thể chứa các phương thức private/static/final **có thân**; tính hợp lệ được xét trên từng khai báo.

## 6. Ví dụ Java 17+ chạy được: modifier hợp lệ ở những phương thức khác nhau

Lưu toàn bộ vào `AbstractModifierDemo.java`:

```java
abstract class Processor {
    protected abstract String transform(String input);

    private String normalize(String input) {
        return input.strip();
    }

    public final String process(String input) {
        return "[" + transform(normalize(input)) + "]";
    }

    public static String version() {
        return "v1";
    }
}

final class UppercaseProcessor extends Processor {
    @Override
    protected String transform(String input) {
        return input.toUpperCase(java.util.Locale.ROOT);
    }
}

public class AbstractModifierDemo {
    public static void main(String[] args) {
        Processor processor = new UppercaseProcessor();
        System.out.println(processor.process("  Java  "));
        System.out.println(Processor.version());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 AbstractModifierDemo.java
java AbstractModifierDemo
```

Kết quả:

```text
[JAVA]
v1
```

`transform` là hook abstract mà lớp con triển khai; `normalize` là helper private có thân; `process` là phương thức final cụ thể cố định trình tự; `version` là static có thân. Những modifier đó có thể cùng xuất hiện trong một abstract class vì chúng nằm trên các khai báo riêng biệt.

## 7. Bảng chẩn đoán nhanh

| Khai báo | Hợp lệ? | Cách hiểu |
|---|---|---|
| `synchronized abstract void run();` | Không | Không có thân để thực thi cơ chế đồng bộ |
| `native abstract int call();` | Không | Native implementation và abstract subclass implementation là hai cơ chế khác nhau |
| `strictfp abstract double area();` | Không | Modifier không thể đi cùng abstract |
| `default abstract String format();` trong interface | Không | Default method cần có thân |
| `private void helper() { ... }` trong abstract class | Có | Helper cụ thể, không yêu cầu override |
| `public final void process() { ... }` trong abstract class | Có | Phương thức cụ thể, cấm override |
| `static String version() { ... }` trong abstract class | Có | Phương thức lớp có thân |

Khi đọc lỗi modifier, hãy phân biệt ba nguyên nhân: modifier ngăn override; modifier yêu cầu một cơ chế thực thi khác; hoặc từ khóa vốn không áp dụng cho phương thức. Chẩn đoán theo nhóm này giúp chọn đúng cách sửa thay vì chỉ xóa từ khóa ngẫu nhiên.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-methods-007">Overload phương thức abstract</a>

<details>
<summary>Xem chi tiết</summary>

# Overload phương thức abstract trong Java

## 1. Khái niệm: phương thức abstract có được overload không?

**Có.** Trong Java, một lớp trừu tượng (`abstract class`) hoặc một giao diện (`interface`) có thể khai báo nhiều phương thức **cùng tên nhưng khác danh sách tham số**. Đó là **nạp chồng phương thức (method overloading)**, kể cả khi các phương thức này chưa có phần thân.

Ví dụ:

```java
abstract class Parser {
    public abstract String parse(int number);
    public abstract String parse(String text);
    public abstract String parse(String text, boolean strict);
}
```

Đây là **ba phương thức abstract được overload hợp lệ**:

- `parse(int)` xử lý đầu vào kiểu số nguyên.
- `parse(String)` xử lý chuỗi.
- `parse(String, boolean)` xử lý chuỗi với thêm cờ cấu hình.

Mỗi chữ ký (signature) khác nhau bởi **số lượng, kiểu hoặc thứ tự kiểu tham số**. Các phương thức cùng tên này là những hợp đồng **riêng biệt**. Một lớp con cụ thể phải hiện thực tất cả các phương thức abstract chưa được hiện thực mà nó kế thừa, nếu không chính lớp đó cũng phải khai báo `abstract` (trừ các tình huống phương thức đã được đáp ứng bằng triển khai kế thừa phù hợp).

Điểm cần nhớ: `abstract` nói về việc **chưa cung cấp triển khai**, trong khi `overload` nói về **nhiều chữ ký cùng tên**. Hai đặc tính hoàn toàn có thể xuất hiện đồng thời.

## 2. Phân biệt overload, override và abstract

| Thuộc tính | Overload (nạp chồng) | Override (ghi đè) |
| --- | --- | --- |
| Quan hệ giữa phương thức | Cùng tên, **khác danh sách tham số** | Phương thức ở kiểu con cung cấp triển khai cho phương thức kế thừa có **chữ ký tương ứng** |
| Mục đích | Hỗ trợ nhiều dạng đầu vào/dạng gọi | Thay thế hoặc bổ sung cách thực hiện hợp đồng được kế thừa |
| Có thể xảy ra ở đâu? | Ngay trong một lớp/interface hoặc giữa các thành viên kế thừa | Giữa kiểu con và kiểu cha/giao diện |
| Cơ chế chọn | Thường được xác định **lúc biên dịch**, dựa trên kiểu tĩnh và đối số của lời gọi | Với phương thức thể hiện có thể ghi đè, triển khai được chọn **lúc chạy** theo lớp thực tế của đối tượng |
| `@Override` | Không dùng chỉ vì có thêm một overload | Nên dùng trên phương thức thật sự ghi đè/hiện thực |
| Trường hợp phương thức abstract | Nhiều khai báo `abstract` cùng tên, khác tham số | Lớp con cụ thể thường dùng `@Override` để cung cấp thân phương thức |

Ví dụ khác nhau:

```java
abstract class Analyzer {
    abstract String analyze(String input);
    abstract String analyze(byte[] input); // overload analyze(String)
}

final class DefaultAnalyzer extends Analyzer {
    @Override
    String analyze(String input) {         // override analyze(String)
        return input;
    }

    @Override
    String analyze(byte[] input) {         // override analyze(byte[])
        return Integer.toString(input.length);
    }
}
```

Đoạn trên là ví dụ định nghĩa kiểu, không có `main()`; hãy xem chương trình hoàn chỉnh ở phần tiếp theo để chạy trực tiếp.

Hai phương thức của `Analyzer` **nạp chồng** nhau. Mỗi phương thức cùng chữ ký ở `DefaultAnalyzer` lại **ghi đè** khai báo abstract tương ứng. Một phương thức có thể cùng lúc *là một overload trong tập cùng tên* và *là một override đối với chữ ký được kế thừa*.

## 3. Chương trình Java 17+ hoàn chỉnh

Ví dụ này thể hiện cả ba trường hợp: hai overload abstract, một overload có sẵn thân hàm, và lớp con hiện thực hai hợp đồng còn thiếu.

Tạo tệp **`AbstractOverloadDemo.java`**:

```java
abstract class Printer {
    protected final String prefix;

    protected Printer(String prefix) {
        this.prefix = prefix;
    }

    // Overload 1: chưa triển khai.
    public abstract String print(int value);

    // Overload 2: chưa triển khai.
    public abstract String print(String value);

    // Overload 3: đã có triển khai, gọi lại overload String.
    public String print(boolean value) {
        return print(Boolean.toString(value));
    }
}

final class ConsolePrinter extends Printer {
    public ConsolePrinter(String prefix) {
        super(prefix);
    }

    @Override
    public String print(int value) {
        return prefix + "#" + value;
    }

    @Override
    public String print(String value) {
        return prefix + "[" + value.trim() + "]";
    }
}

public class AbstractOverloadDemo {
    public static void main(String[] args) {
        Printer printer = new ConsolePrinter("item:");

        System.out.println(printer.print(12));
        System.out.println(printer.print(" Java "));
        System.out.println(printer.print(true));
    }
}
```

Chạy:

```bash
javac AbstractOverloadDemo.java
java AbstractOverloadDemo
```

Đầu ra:

```text
item:#12
item:[Java]
item:[true]
```

**Giải thích từng lời gọi:**

1. `printer.print(12)`: đối số `12` có kiểu `int`; trình biên dịch chọn chữ ký `print(int)`. Khi chạy, đối tượng thực sự là `ConsolePrinter`, nên dùng phần triển khai `ConsolePrinter.print(int)`.
2. `printer.print(" Java ")`: đối số là `String`; chọn `print(String)` và thực thi triển khai ở `ConsolePrinter`. `trim()` loại khoảng trắng đầu/cuối.
3. `printer.print(true)`: chọn `print(boolean)`, **không phải phương thức abstract**. Phương thức này chuyển `true` thành `"true"`, rồi gọi `print(String)`. Lời gọi bên trong vẫn được phân phối đến `ConsolePrinter.print(String)`.

Từ đó thấy rằng **overload của một tên có thể gồm cả phương thức abstract và phương thức concrete**. Tất cả là những cách gọi khác nhau của một ý niệm thao tác chung.

Ngoài ra, `protected final String prefix` là trạng thái được khởi tạo qua constructor của lớp cha. Không thể tạo `new Printer(...)` trực tiếp vì `Printer` là lớp trừu tượng, nhưng đối tượng `ConsolePrinter` vẫn chứa phần trạng thái do `Printer` khai báo.

## 4. Quy tắc tạo các overload hợp lệ

### 4.1. Khác số lượng, kiểu hoặc thứ tự tham số

Ví dụ trong **cùng một** lớp trừu tượng:

```java
abstract class TaskRouter {
    abstract void route(String id);
    abstract void route(String id, int priority);
    abstract void route(int priority, String id);
    abstract void route(long numericId);
}
```

Bốn chữ ký khác nhau là hợp lệ. Tên biến tham số **không** phải yếu tố phân biệt chữ ký: đổi `route(String id)` thành `route(String name)` không tạo overload mới.

### 4.2. Chỉ thay đổi kiểu trả về là không hợp lệ

```java
abstract class InvalidReturn {
    abstract String find(int id);
    // abstract int find(int id); // LỖI BIÊN DỊCH: cùng tên và tham số.
}
```

Kiểu trả về **không đủ** để phân biệt hai overload. Ngược lại, khi **override**, Java cho phép kiểu trả về tham chiếu hiệp biến (covariant return type) trong những điều kiện hợp lệ. Đừng dùng quy tắc trả về của override để giải thích overload.

### 4.3. Chỉ thay đổi visibility, tên tham số, modifier hoặc throws cũng không đủ

```java
abstract class InvalidThrows {
    abstract void save(String text);
    // abstract void save(String text) throws java.io.IOException;
    // LỖI BIÊN DỊCH nếu bỏ comment: hai phương thức có cùng chữ ký.
}
```

Thêm `throws`, đổi tên `text`, hoặc thay `public/protected` không tạo chữ ký mới. Với phương thức abstract trong lớp, cách xác định visibility ảnh hưởng tới việc có thể hiện thực/gọi phương thức ra sao, nhưng không tạo overload.

### 4.4. Varargs và mảng không tạo hai chữ ký riêng

```java
abstract class InvalidVarargs {
    abstract void log(String... lines);
    // abstract void log(String[] lines); // LỖI: cùng chữ ký sau quy đổi.
}
```

`String...` cho phép truyền nhiều đối số tại vị trí gọi nhưng về hình thức tham số là mảng `String[]`; không thể khai báo thêm overload `log(String[])`.

### 4.5. Generic type erasure có thể gây xung đột

```java
import java.util.List;

abstract class InvalidErasure {
    abstract void accept(List<String> values);
    // abstract void accept(List<Integer> values);
    // LỖI: cả hai cùng bị xóa kiểu về List (name clash).
}
```

Dù `List<String>` khác `List<Integer>` ở mức kiểu generic, cả hai có cùng erasure `List`, nên **không thể** dùng chúng làm hai overload chỉ khác đối số kiểu generic. Hãy đặt tên phương thức khác, dùng kiểu tham số thực sự khác, hoặc thiết kế một hợp đồng generic phù hợp.

### 4.6. Các dạng tham số khác vẫn hợp lệ

`process(int)` và `process(Integer)` là hai overload hợp lệ do `int` khác `Integer`; `process(Number)` và `process(Object)` cũng khác tham số. Tuy nhiên, hợp lệ khi khai báo **không** có nghĩa mọi cách gọi đều không mơ hồ. Cơ chế chọn được trình bày ngay dưới.

## 5. Java chọn overload bằng cách nào?

Tại thời điểm biên dịch, trình biên dịch sử dụng **kiểu tĩnh của biểu thức nhận lời gọi**, các phương thức có thể truy cập, số lượng/kiểu đối số và quy tắc tương thích/chọn phương thức phù hợp nhất.

Có thể ghi nhớ thứ tự đơn giản hóa của các giai đoạn tìm kiếm phương thức áp dụng được:

1. Ưu tiên lời gọi theo **khả năng tương thích chặt** mà không cần boxing/unboxing hay xử lý varargs mở rộng; bao gồm ví dụ nới rộng kiểu nguyên thủy (`int` → `long`) và nới rộng kiểu tham chiếu.
2. Nếu chưa tìm được, mới xét tương thích **mở rộng** cho boxing/unboxing.
3. Nếu vẫn chưa có phương thức phù hợp, mới xét **varargs** như danh sách đối số biến thiên.

Trong mỗi giai đoạn, Java phải chọn được phương thức phù hợp nhất theo quy tắc *most specific*; nếu không có ứng viên duy nhất, lời gọi có thể **lỗi biên dịch vì mơ hồ**. Đây là một mô tả học tập: Java Language Specification còn xét nhiều chi tiết về generic, suy luận kiểu, lambda và khả năng truy cập.

### Ví dụ chạy được: widening, boxing, varargs

Tạo tệp **`OverloadResolutionDemo.java`**:

```java
abstract class Selection {
    abstract String choose(long value);
    abstract String choose(Integer value);
    abstract String choose(int... values);
}

final class SelectionImpl extends Selection {
    @Override
    String choose(long value) {
        return "long";
    }

    @Override
    String choose(Integer value) {
        return "Integer";
    }

    @Override
    String choose(int... values) {
        return "varargs(" + values.length + ")";
    }
}

public class OverloadResolutionDemo {
    public static void main(String[] args) {
        Selection selection = new SelectionImpl();

        System.out.println(selection.choose(5));
        System.out.println(selection.choose(Integer.valueOf(5)));
        System.out.println(selection.choose(5, 6));
    }
}
```

```bash
javac OverloadResolutionDemo.java
java OverloadResolutionDemo
```

```text
long
Integer
varargs(2)
```

- `choose(5)`: đối số `int`, phương thức `choose(long)` nhận chuyển đổi primitive widening và được chọn trước boxing sang `Integer` hoặc mở rộng varargs.
- `choose(Integer.valueOf(5))`: đối số đã là `Integer`, nên phương thức `choose(Integer)` phù hợp trực tiếp.
- `choose(5, 6)`: hai đối số không thể khớp hai overload chỉ có một tham số; lời gọi đi vào `choose(int...)`, nhận mảng có hai phần tử.

**Không nên** hiểu rằng “overload abstract sẽ được chọn khi chạy theo lớp thực tế”. Việc chọn chữ ký và việc chọn triển khai được thực hiện ở **hai bước khác nhau**.

## 6. Overload được chọn tĩnh, override được phân phối động

Tạo tệp **`StaticTypeDispatchDemo.java`**:

```java
abstract class Receiver {
    public abstract String accept(Object value);
}

final class ReceiverImpl extends Receiver {
    @Override
    public String accept(Object value) {
        return "Object:" + value;
    }

    // Overload mới chỉ xuất hiện trong kiểu con.
    public String accept(String value) {
        return "String:" + value;
    }
}

public class StaticTypeDispatchDemo {
    public static void main(String[] args) {
        Receiver asBase = new ReceiverImpl();
        ReceiverImpl asChild = new ReceiverImpl();
        Object text = "hello";

        System.out.println(asBase.accept("hello"));
        System.out.println(asChild.accept("hello"));
        System.out.println(asChild.accept(text));
    }
}
```

```bash
javac StaticTypeDispatchDemo.java
java StaticTypeDispatchDemo
```

```text
Object:hello
String:hello
Object:hello
```

Phân tích:

- `asBase.accept("hello")`: biến được khai báo kiểu `Receiver`; ở kiểu đó **chỉ có** `accept(Object)`. Chữ ký được chọn lúc biên dịch là `accept(Object)`; khi chạy, `ReceiverImpl.accept(Object)` thực thi.
- `asChild.accept("hello")`: biến có kiểu `ReceiverImpl`, nhìn thấy cả hai overload; `accept(String)` phù hợp hơn `accept(Object)`.
- `asChild.accept(text)`: dù `text` đang tham chiếu đến một đối tượng `String`, **kiểu tĩnh** của biến là `Object`; chọn `accept(Object)`.

Nếu yêu cầu mọi đối tượng được nhìn qua `Receiver` vẫn gọi được `accept(String)` theo nghĩa một chữ ký riêng biệt, hãy đưa overload ấy vào hợp đồng `Receiver` và hiện thực nó tại các lớp con. Đừng chỉ thêm overload vào một lớp con rồi mặc định rằng mọi biến kiểu cha sẽ nhìn thấy nó.

## 7. Lớp con phải triển khai những overload nào?

Giả sử:

```java
abstract class MessageProcessor {
    abstract String process(String text);
    abstract String process(int number);

    String process(boolean flag) {
        return Boolean.toString(flag);
    }
}
```

Một lớp con **concrete** phải bảo đảm hai hợp đồng `process(String)` và `process(int)` được hiện thực. Nó **không bắt buộc** override `process(boolean)` vì phương thức này đã có thân hàm.

Một lớp con vẫn được khai báo `abstract` để trì hoãn việc hiện thực một hay nhiều phương thức:

```java
abstract class TextMessageProcessor extends MessageProcessor {
    @Override
    String process(String text) {
        return text.strip();
    }
    // process(int) vẫn abstract, lớp này vẫn là abstract.
}

final class CompleteMessageProcessor extends TextMessageProcessor {
    @Override
    String process(int number) {
        return Integer.toString(number);
    }
}
```

Đây là đoạn định nghĩa các lớp, có thể dùng trong một chương trình khác; không tự có `main()`. `CompleteMessageProcessor` là concrete vì hợp đồng `process(String)` đã có từ lớp cha trực tiếp và `process(int)` đã được cung cấp.

### Một lỗi rất dễ mắc: vô tình overload thay vì override

```java
abstract class ParentHandler {
    abstract void handle(Number value);
}

// Ví dụ CỐ Ý SAI, không biên dịch nếu bỏ comment:
// final class WrongHandler extends ParentHandler {
//     void handle(Integer value) { } // OVERLOAD, không hiện thực handle(Number).
// }
```

Dù `Integer extends Number`, `handle(Integer)` **không** override `handle(Number)` vì danh sách tham số khác. Lớp `WrongHandler` phải khai báo `abstract` hoặc hiện thực đúng `handle(Number)`.

Dùng `@Override` ngay trên phương thức định ghi đè giúp trình biên dịch phát hiện nhầm chữ ký:

```java
// @Override
// void handle(Integer value) { } // Lỗi: không override phương thức của cha.
```

Chú ý: `@Override` chỉ kiểm tra ghi đè/hiện thực hợp đồng. Nó không có tác dụng “khai báo overload” cho hai phương thức cùng tên.

## 8. Overload phương thức abstract trong interface

Trong interface, phương thức thể hiện khai báo **không có thân hàm** (và không phải `default`/`private`/`static`) mặc định là `public abstract`. Vì vậy có thể overload giống abstract class:

```java
interface Encoder {
    String encode(String text);
    String encode(byte[] data);
}
```

Một lớp hiện thực cả hai hợp đồng:

```java
final class PlainEncoder implements Encoder {
    @Override
    public String encode(String text) {
        return text;
    }

    @Override
    public String encode(byte[] data) {
        return java.util.Base64.getEncoder().encodeToString(data);
    }
}
```

Hai đoạn trên ghép lại là các khai báo kiểu hợp lệ; nếu muốn chạy độc lập, thêm lớp có `main()` sử dụng `new PlainEncoder()`. Triển khai dùng **`public`** vì phương thức interface là public; giảm xuống package-private/protected/private sẽ lỗi biên dịch.

Với `interface`, cũng có thể **trộn** overload abstract và overload `default` có triển khai. Việc hỗ trợ overload **không** giúp interface có constructor hay instance field. Chọn interface khi muốn công bố nhiều cách gọi của cùng một khả năng, còn các lớp hiện thực có thể xử lý theo cách riêng.

Nếu một lớp hiện thực **nhiều interface** cùng khai báo hợp đồng trùng chữ ký, phương thức hiện thực phù hợp có thể đáp ứng cùng lúc nhiều hợp đồng. Nếu hai interface mang các phương thức `default` xung đột, việc giải quyết xung đột default method là một vấn đề kế thừa riêng, không phải bản chất của overloading.

## 9. Các tình huống cần thận trọng

### 9.1. `null` có thể dẫn đến overload mơ hồ

```java
abstract class NullableRouter {
    abstract void submit(String text);
    abstract void submit(Integer number);
}

// NullableRouter router = ...;
// router.submit(null); // LỖI BIÊN DỊCH: không xác định được overload nào.
```

Cả `String` và `Integer` đều nhận `null`, nhưng hai kiểu không có quan hệ kiểu con với nhau nên không có ứng viên cụ thể hơn. Có thể chỉ định rõ khi thực sự cần:

```java
// router.submit((String) null);
// router.submit((Integer) null);
```

Đây là các lời gọi minh họa, yêu cầu biến `router` hợp lệ và lớp triển khai xử lý `null` đúng cách. **Ép kiểu để chọn overload không tự làm `null` trở nên an toàn với logic phương thức.**

Nếu có `submit(Object)` và `submit(String)`, đối số `null` thường chọn `submit(String)` vì `String` cụ thể hơn `Object`.

### 9.2. Overload tương tự có thể làm API khó đoán

Khai báo đồng thời `load(int)`, `load(long)`, `load(Integer)`, `load(Number)`, `load(int...)` hoàn toàn có thể hợp lệ nhưng gây bất ngờ khi truyền literal, `null`, biến đã boxing hoặc mảng. Nhiều overload dễ nhầm còn khiến người đọc không biết sự khác biệt nghiệp vụ.

Chỉ overload khi các cách gọi **cùng mục đích** và khác nhau tự nhiên ở kiểu/số tham số. Nếu ngữ nghĩa khác hẳn, đặt tên như `loadById(...)`, `loadFromFile(...)` giúp rõ ý hơn. Một overload quá nhiều tham số tùy chọn có thể thay bằng đối tượng cấu hình có kiểu rõ ràng.

### 9.3. Override một overload không thay thế các overload khác

Nếu kiểu cha có `execute(String)` và `execute(int)`, thì cung cấp thân cho `execute(String)` không đồng nghĩa đã đáp ứng `execute(int)`. Mỗi chữ ký phải được xét riêng, kể cả khi chúng cùng tên.

### 9.4. Kiểu trả về và checked exceptions được kiểm tra theo chữ ký đã chọn

Sau khi trình biên dịch chọn overload, kiểu kết quả và ngoại lệ kiểm tra (`checked exceptions`) được xác định từ khai báo phương thức được chọn trong ngữ cảnh kiểu tĩnh. Bạn không thể dựa vào kiểu trả về hay `throws` để phân biệt hai khai báo cùng danh sách tham số.

### 9.5. Quy tắc giới hạn của phương thức abstract vẫn áp dụng

Trong lớp, phương thức `abstract` không có thân và không thể đồng thời là `private`, `static` hay `final`, vì điều đó mâu thuẫn với yêu cầu phương thức có thể được lớp con hiện thực. Có thể overload một tên bằng **những phương thức concrete hợp lệ**, nhưng không biến cùng một chữ ký thành cả abstract lẫn concrete trong một lớp.

Constructor có thể được overload ngay cả trong abstract class, nhưng **constructor không phải phương thức abstract** và không thể được khai báo `abstract`.

## 10. Thiết kế API có overload abstract tốt

Một thiết kế hợp lý thường theo các bước:

1. Xác định hành vi chung, ví dụ `serialize(...)`, `parse(...)`, `render(...)`; chỉ tạo overload khi những tham số khác nhau có **ý nghĩa rõ ràng**.
2. Xác định đầu vào chính (canonical form) để tránh các lớp con phải lặp logic; chẳng hạn khai báo một phương thức abstract nhận đối tượng yêu cầu, còn overload concrete chuyển đổi từ `String` hoặc tham số đơn giản thành đối tượng yêu cầu.
3. Nếu nhiều overload đều trừu tượng, giải thích hợp đồng và **yêu cầu riêng** của từng chữ ký; lớp con phải duy trì các bất biến và hành vi mong đợi.
4. Dùng `@Override` khi hiện thực, kiểm tra visibility (`public` với interface), loại tham số chính xác và generic erasure.
5. Viết ví dụ với biến khai báo bằng **kiểu hợp đồng** (abstract class/interface), vì đây là cách mã gọi phổ biến và bộc lộ những overload mà người dùng API thực sự nhìn thấy.
6. Kiểm tra lời gọi biên giới: `null`, số nguyên literal, wrapper, varargs, phân cấp `Object`/`String` và các đối số có kiểu cha.

Cân nhắc đánh đổi: nhiều overload abstract có thể buộc mọi implementer hỗ trợ quá nhiều kiểu dữ liệu. Thay vào đó, một phương thức abstract cốt lõi và vài overload concrete/default đóng vai trò adapter thường giảm sự lặp lại.

## 11. Bài tập tự kiểm tra

**Câu 1.** Hai khai báo dưới đây có phải overload không?

```java
abstract int count(String text);
abstract int count(String text, boolean ignoreCase);
```

**Đáp án:** Có. Tên giống nhau, số tham số khác nhau. Cả hai có thể cùng là abstract, và lớp con concrete cần hiện thực các hợp đồng chưa có triển khai.

**Câu 2.** `abstract String read(int id)` và `abstract Object read(int id)` có hợp lệ trong cùng một lớp không?

**Đáp án:** Không. Danh sách tham số giống nhau, chỉ khác kiểu trả về. Đây không phải overload.

**Câu 3.** Một lớp cha abstract khai báo `handle(Number)`; lớp con chỉ định nghĩa `handle(Integer)`. Đã hiện thực hợp đồng chưa?

**Đáp án:** Chưa. Đó là overload, không phải override. Lớp con concrete vẫn phải cung cấp `handle(Number)` (hoặc kế thừa triển khai hợp lệ).

**Câu 4.** Trong ví dụ `StaticTypeDispatchDemo`, vì sao `asBase.accept("hello")` trả về `Object:hello`?

**Đáp án:** Kiểu tĩnh `Receiver` chỉ khai báo `accept(Object)`; overload `accept(String)` chỉ được giới thiệu ở `ReceiverImpl`. Trình biên dịch chọn chữ ký `accept(Object)`, sau đó JVM gọi phần override tương ứng của đối tượng.

**Câu 5.** `choose(5)` có thể chọn `choose(long)` dù tồn tại `choose(Integer)` không?

**Đáp án:** Có. Với `int` literal, nới rộng `int` → `long` được xem xét trước boxing `int` → `Integer` trong các giai đoạn áp dụng overload.

## Kết luận

- **Phương thức abstract có thể overload** nếu các khai báo cùng tên có danh sách tham số phân biệt hợp lệ.
- **Overload** chọn chữ ký để gọi; **override** cung cấp triển khai cho chữ ký đã được kế thừa. Lựa chọn overload phụ thuộc kiểu tĩnh lúc biên dịch, sau đó cơ chế phân phối động có thể chọn phần triển khai override lúc chạy.
- Lớp con concrete cần đáp ứng **từng** overload abstract chưa được hiện thực.
- Chú ý trường hợp chỉ khác kiểu trả về, `throws`, trùng erasure generic, varargs/mảng, `null` mơ hồ và boxing/widening.
- Một API tốt dùng overload có chủ đích, với hợp đồng nhất quán và các ví dụ gọi được qua abstract class/interface.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-methods-008">Khai báo lại phương thức đã có thân thành abstract</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo lại phương thức đã có thân thành `abstract` trong Java

Một phương thức đã có thân (concrete method) ở lớp cha **có thể được khai báo lại thành phương thức `abstract` ở một lớp con trừu tượng**, nếu phương thức của lớp cha có thể được override. Kỹ thuật này thường gọi là **re-abstracting a method** (tái trừu tượng hóa phương thức). Nó cho phép một nhánh kế thừa **không chấp nhận triển khai mặc định của lớp cha** và bắt buộc lớp con cụ thể ở nhánh đó tự chọn cách thực hiện.

Đây là một quy tắc dễ gây ngạc nhiên: một lớp cha cung cấp đầy đủ thân phương thức **không có nghĩa** mọi lớp con trong mọi nhánh đều phải kế thừa phần cài đặt ấy. Lớp con `abstract` có thể loại bỏ việc kế thừa triển khai cho một phương thức bằng cách khai báo override trừu tượng.

## 1. Cấu trúc và điều kiện cần

Mẫu cơ bản:

```java
class Parent {
    public void execute() {
        System.out.println("Cách làm mặc định");
    }
}

abstract class MustImplement extends Parent {
    @Override
    public abstract void execute();
}

class ConcreteChild extends MustImplement {
    @Override
    public void execute() {
        System.out.println("Cách làm được chọn rõ ràng");
    }
}
```

Ở đây:

- `Parent.execute()` là **concrete method**: có thân, đối tượng `Parent` gọi được ngay.
- `MustImplement` là **abstract class**. Dòng `public abstract void execute();` override phương thức của `Parent` nhưng cố ý **không có thân**. `@Override` hợp lệ và giúp trình biên dịch kiểm tra đây đúng là override.
- `ConcreteChild` là lớp cụ thể: phải cung cấp triển khai `execute()` hợp lệ mới tạo được đối tượng.
- Một lớp con khác của `Parent` mà **không** đi qua `MustImplement` vẫn được dùng `Parent.execute()` mặc định.

Dấu chấm phẩy `;` sau khai báo abstract là bắt buộc; không đặt cặp ngoặc `{}` vào phương thức `abstract` ngay cả khi thân rỗng. `abstract` ở đây đánh dấu yêu cầu triển khai, không phải phương thức có thân rỗng.

Quan trọng: **không thể khai báo hai phương thức cùng chữ ký trong cùng một lớp**, một concrete rồi một abstract. Re-abstract là việc **override phương thức được thừa kế từ lớp cha**, không phải tạo hai phương thức cùng tên/cùng tham số ngay trong một lớp.

## 2. Vì sao cần làm vậy?

Giả sử thư viện có lớp `Formatter` với cách định dạng chung. Một nhóm ứng dụng mới yêu cầu mỗi bộ định dạng phải chọn cách trình bày riêng; dùng ngầm cách mặc định có nguy cơ sai quy tắc nghiệp vụ.

Ta có ba lựa chọn:

1. Giữ `Formatter` để những trường hợp cũ vẫn dùng triển khai mặc định.
2. Tạo `StrictFormatter extends Formatter`, khai báo lại `render(...)` thành `abstract` để **mọi lớp cụ thể kế thừa qua nhánh này phải cài đặt**.
3. Cho từng lớp cụ thể như `UpperFormatter` hoặc `SlugFormatter` triển khai `render(...)`.

Nhờ vậy, thay đổi yêu cầu chỉ ảnh hưởng **nhánh kế thừa nghiêm ngặt**. Ta không cần xóa thân phương thức ở `Formatter` và không phải buộc mọi lớp con hiện hữu thực hiện lại hành vi cũ. Kỹ thuật này hữu ích khi thiết kế khung mở rộng, chuyển tiếp API cũ hoặc dùng một lớp cơ sở có tiện ích chung nhưng muốn một nhánh bắt buộc cung cấp hành vi cụ thể.

Tuy nhiên, re-abstract không phải mặc định tốt cho mọi thiết kế. Nếu không có quan hệ kế thừa thực sự giữa các loại đối tượng, interface và composition thường dễ tổ chức hơn.

## 3. Ví dụ Java 17 chạy hoàn chỉnh

Lưu toàn bộ chương trình sau thành **`Main.java`**:

```java
import java.util.Locale;

public class Main {
    static class Formatter {
        public String render(String input) {
            return "DEFAULT(" + input + ")";
        }

        public final String describe(String input) {
            return "Result=" + render(input);
        }
    }

    static abstract class StrictFormatter extends Formatter {
        @Override
        public abstract String render(String input);
    }

    static final class UpperFormatter extends StrictFormatter {
        @Override
        public String render(String input) {
            return "UPPER(" + input.toUpperCase(Locale.ROOT) + ")";
        }
    }

    static final class SlugFormatter extends StrictFormatter {
        @Override
        public String render(String input) {
            return "SLUG(" + input.toLowerCase(Locale.ROOT)
                    .replace(' ', '-') + ")";
        }
    }

    public static void main(String[] args) {
        Formatter[] formatters = {
                new Formatter(),
                new UpperFormatter(),
                new SlugFormatter()
        };

        for (Formatter formatter : formatters) {
            System.out.println(formatter.describe("Java 17"));
        }
    }
}
```

Với JDK 17 trở lên, thực hiện trong thư mục chứa `Main.java`:

```bash
javac Main.java
java Main
```

**Kết quả chuẩn:**

```text
Result=DEFAULT(Java 17)
Result=UPPER(JAVA 17)
Result=SLUG(java-17)
```

**Giải thích kết quả theo thứ tự chạy:**

1. Phần tử `new Formatter()` sử dụng `Formatter.render()` đã có thân; nhận `DEFAULT(Java 17)`.
2. Phần tử `new UpperFormatter()` có nhánh kế thừa `Formatter → StrictFormatter → UpperFormatter`. `StrictFormatter` đã khai báo lại `render()` là abstract, nên `UpperFormatter` phải định nghĩa phương thức và trả `UPPER(JAVA 17)`.
3. Phần tử `new SlugFormatter()` thuộc cùng nhánh, cũng phải định nghĩa `render()`, trả `SLUG(java-17)`.
4. Dù biến trong vòng lặp có kiểu `Formatter`, lời gọi `render()` bên trong `Formatter.describe()` được **dispatch động** theo loại đối tượng thực tế. Do đó `describe()` có thể dùng một lần cài đặt chung mà vẫn nhận cách định dạng khác nhau.
5. `describe()` được khai báo `final`, nên các lớp con không thể override bản thân nó; ví dụ vẫn re-abstract được `render()` vì **`render()` không `final`**.

Lưu ý: `StrictFormatter` **không tự tạo đối tượng được** do nó là `abstract class`. Một lớp cụ thể đi qua `StrictFormatter` cũng không thể “dùng lại mặc định” chỉ bằng cách bỏ qua `render()` vì yêu cầu abstract của lớp trung gian vẫn còn hiệu lực.

## 4. Chính xác phần nào của kế thừa thay đổi?

Phân biệt **hợp đồng phương thức** với **thân phương thức**:

| Loại lớp | Tình trạng của `render(String)` | Có thể `new`? | Khi chạy |
| --- | --- | --- | --- |
| `Formatter` | Concrete, có thân mặc định | Có | Dùng `DEFAULT(...)` |
| `StrictFormatter` | Override thành abstract, không có thân | Không | Chưa có cài đặt ở nhánh này |
| `UpperFormatter` | Override thành concrete | Có | Dùng `UPPER(...)` |
| `SlugFormatter` | Override thành concrete | Có | Dùng `SLUG(...)` |

Có thể mô tả nhánh kế thừa như sau:

```text
Formatter
  ├─ render(String): concrete
  └─ StrictFormatter (abstract)
       ├─ render(String): abstract  ← tái trừu tượng hóa
       ├─ UpperFormatter
       │    └─ render(String): concrete
       └─ SlugFormatter
            └─ render(String): concrete
```

Việc re-abstract **không xóa** mã của `Formatter.render()` khỏi lớp gốc: một đối tượng `Formatter` hoặc nhánh kế thừa khác không override vẫn gọi được nó. Tuy nhiên, đối với một lớp con kế thừa thông qua `StrictFormatter`, phương thức được thừa kế trong nhánh này là khai báo `abstract` gần nhất và lớp cụ thể phải đáp ứng yêu cầu đó.

`@Override` không phải yếu tố làm phương thức trở thành abstract; từ khóa `abstract` mới quyết định điều đó. `@Override` được khuyên dùng để bắt lỗi chính tả/tên/tham số khi chủ đích là override.

## 5. Quy tắc về chữ ký và phạm vi truy cập

Phương thức khai báo lại phải tuân theo quy tắc override bình thường của Java:

- **Tên và danh sách tham số:** chữ ký override tương ứng phương thức được kế thừa; thay đổi tham số sẽ tạo overload, không ép lớp con triển khai phương thức cũ.
- **Kiểu trả về:** giữ kiểu tương thích (có thể dùng kiểu trả về hiệp biến cho kiểu tham chiếu). Không thay bằng kiểu không tương thích.
- **Mức truy cập:** không giảm khả năng truy cập của phương thức gốc. Phương thức cha `public` phải được override bằng `public`.
- **Checked exceptions:** phương thức override không được khai báo checked exception rộng hơn những gì phương thức cha cho phép; exception không kiểm tra (`RuntimeException` và lớp con) chịu quy tắc khác.
- **`final` / `static` / `private`:** `final` cấm override; `static` dùng cơ chế che khuất (hiding), không phải override instance method; `private` không được thừa kế làm một phương thức có thể override theo cách này.
- **Phương thức `abstract` không thể đồng thời `final`, `static`, `private`**. Bản khai báo abstract không có thân, còn `final` lại cấm ghi đè để cung cấp thân.

Ví dụ hợp lệ với phạm vi truy cập và kiểu trả về hiệp biến (đây là đoạn minh họa độc lập về quan hệ kiểu, không phải phần của chương trình `Main.java` ở trên):

```java
class Metric {
    protected Number value() {
        return 1;
    }
}

abstract class ExactMetric extends Metric {
    @Override
    protected abstract Integer value();
}

class CountMetric extends ExactMetric {
    @Override
    protected Integer value() {
        return 5;
    }
}
```

`Integer` là subtype của `Number`, nên `ExactMetric.value()` có kiểu trả về tương thích và việc override hợp lệ.

## 6. Những ví dụ không biên dịch được và lý do

Các đoạn dưới đây minh họa **lỗi cố ý**, không ghép vào `Main.java` đang chạy.

**Lỗi 1 — lớp cụ thể không hiện thực phương thức vừa được re-abstract:**

```java
class IncompleteFormatter extends StrictFormatter {
    // Không override render(String): lỗi biên dịch.
}
```

Nếu muốn giữ lớp này không có cài đặt, phải khai báo nó `abstract`, khi đó vẫn không thể `new IncompleteFormatter()`. Nếu muốn tạo đối tượng, nó phải hiện thực `render(String)` trực tiếp hoặc kế thừa một cài đặt concrete hợp lệ từ một lớp phù hợp trong chuỗi kế thừa.

**Lỗi 2 — cố re-abstract một phương thức `final`:**

```java
class LockedParent {
    public final void save() {
        System.out.println("đã lưu");
    }
}

abstract class InvalidChild extends LockedParent {
    @Override
    public abstract void save(); // Lỗi: không thể override final.
}
```

Từ khóa `final` ở lớp cha cấm bất kỳ override nào, kể cả khai báo abstract.

**Lỗi 3 — dùng `static` như một phương thức có thể override:**

```java
class Utility {
    public static void run() {}
}

abstract class InvalidUtility extends Utility {
    @Override
    public abstract void run(); // Lỗi: instance method không override static.
}
```

Phương thức static gắn với lớp, không tham gia dynamic dispatch như instance method. Khai báo lại bằng phương thức abstract instance là xung đột.

**Lỗi 4 — khai báo abstract và concrete trùng chữ ký trong cùng lớp:**

```java
abstract class Wrong {
    public void work() {}
    public abstract void work(); // Lỗi: cùng chữ ký trong một lớp.
}
```

Re-abstract phải diễn ra **giữa các lớp trong quan hệ kế thừa**, không phải thông qua hai khai báo trùng nhau trong một lớp.

**Lỗi 5 — gọi `super.render()` từ lớp cháu để lấy lại thân gốc:**

```java
class BrokenFormatter extends StrictFormatter {
    @Override
    public String render(String input) {
        return super.render(input); // Lỗi: render trong StrictFormatter là abstract.
    }
}
```

`super` ở đây tham chiếu triển khai ở lớp cha trực tiếp theo quy tắc gọi `super`; lời gọi tới phương thức abstract đó không cung cấp được thân để thực thi. Java không hỗ trợ cú pháp `super.super.render(...)` nhằm “nhảy qua” `StrictFormatter`. Nếu muốn cho phép dùng lại logic mặc định, hãy **thiết kế rõ một helper concrete riêng** ở lớp gốc (chẳng hạn `protected final String renderDefault(String input)`) để các lớp con gọi helper đó.

## 7. Phân biệt re-abstract, override thông thường và overload

| Hành động | Ví dụ | Kết quả |
| --- | --- | --- |
| Override concrete bằng concrete | Lớp con `public String render(...){...}` | Đưa ra triển khai mới |
| **Re-abstract concrete bằng abstract** | Lớp con abstract `public abstract String render(...);` | Buộc lớp cụ thể ở nhánh sau hiện thực |
| Kế thừa không override | Lớp con giữ nguyên `render()` của cha | Dùng triển khai hiện có |
| Overload | Thêm `render(String, boolean)` | Tạo chữ ký khác, không đáp ứng hay thay thế `render(String)` |

Đa hình runtime vẫn phát huy tác dụng sau khi lớp cụ thể cung cấp thân mới. Re-abstract chủ yếu là **ràng buộc tại lúc biên dịch**: nó buộc người xây dựng lớp cụ thể phải quyết định hành vi thay vì vô tình kế thừa mặc định.

### Trường hợp interface có phương thức `default`

Có một ý tưởng tương tự trong kế thừa **interface**. Interface con có thể ghi đè một `default` method được kế thừa bằng một khai báo abstract để yêu cầu implementer cung cấp lại:

```java
interface Greeter {
    default String greet() {
        return "Xin chào mặc định";
    }
}

interface CustomGreeter extends Greeter {
    @Override
    String greet();
}

class FriendlyGreeter implements CustomGreeter {
    @Override
    public String greet() {
        return "Xin chào từ lớp triển khai";
    }
}
```

Đoạn này minh họa quan hệ interface, **không phải** abstract class. `Greeter` gốc vẫn giữ default method; nhưng một lớp cụ thể `implements CustomGreeter` phải đáp ứng `greet()` theo hợp đồng ở interface con. Không nên nhầm `default` method trong interface với phương thức `static` hoặc một trường hợp Java cho phép đa kế thừa lớp.

## 8. Khi nào nên dùng, khi nào không?

**Nên cân nhắc re-abstract khi:**

- Lớp cơ sở có một hành vi mặc định hợp lý cho đa số trường hợp.
- Một nhánh kế thừa đặc biệt có quy tắc chặt chẽ: mỗi lớp cụ thể bắt buộc chọn hành vi riêng.
- Bạn cần giữ API và hành vi cũ ở lớp cơ sở, đồng thời bắt buộc nhánh mới cung cấp implementation.
- Lớp trung gian trừu tượng có vai trò thiết kế rõ ràng và thực sự biểu diễn quan hệ “là một” (`is-a`).

**Nên cân nhắc interface/composition thay thế khi:**

- Các đối tượng chỉ có chung khả năng, không có quan hệ kế thừa lớp có ý nghĩa.
- Cần thay chiến lược ở thời điểm chạy bằng cách truyền dependency khác, thay vì cố định theo cây kế thừa.
- Cây lớp ngày càng sâu, khó giải thích vì sao một nhánh cấm triển khai mặc định.
- Chỉ cần sửa một lời gọi nhỏ trong hệ thống hiện tại và không muốn thêm một abstract layer không mang ràng buộc hữu ích.

Chọn re-abstract là một **quyết định về hợp đồng kế thừa**. Nếu mục tiêu đơn thuần là đưa nhiều thuật toán dưới cùng một kiểu sử dụng, một interface chung và các implementation độc lập có thể sáng rõ hơn.

## 9. Câu hỏi tự kiểm tra

1. Trong ví dụ, vì sao `new Formatter()` hợp lệ nhưng `new StrictFormatter()` không hợp lệ?
2. Điều gì khiến `UpperFormatter` bắt buộc cài đặt `render()`, dù `Formatter` đã có thân?
3. Có thể re-abstract `Formatter.describe()` không? Từ khóa nào quyết định câu trả lời?
4. Nếu tạo `class LegacyChild extends Formatter {}`, kết quả `new LegacyChild().render("Java")` sẽ là gì?
5. Vì sao phương thức `render(String, boolean)` không thay thế việc override `render(String)`?
6. Muốn triển khai mới tái sử dụng phần logic của `Formatter.render()`, thiết kế helper như thế nào để không dùng cú pháp `super.super`?
7. Trong một thiết kế không có quan hệ cha–con tự nhiên, vì sao interface hoặc composition có thể tốt hơn lớp trung gian re-abstract?

**Điểm cần nhớ:** Java cho phép một **abstract subclass override một instance method concrete có thể override của superclass bằng abstract method**. Điều này giữ nguyên hành vi mặc định ở lớp gốc, nhưng tạo nghĩa vụ triển khai mới cho các lớp cụ thể trong một nhánh kế thừa. Quy tắc override thông thường vẫn áp dụng, đặc biệt đối với `final`, `static`, `private`, kiểu trả về và mức truy cập.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-methods-009">Phương thức package-private khi lớp con nằm khác package</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức package-private khi lớp con nằm khác package trong Java

Khi một lớp cha khai báo phương thức `abstract` với quyền truy cập **package-private** (không viết `public`, `protected` hay `private`), lớp con nằm **khác package** không thể triển khai/override phương thức abstract đó theo cách thông thường. Đây là điểm dễ nhầm: **phương thức cùng tên, cùng tham số, cùng kiểu trả về trong lớp con chưa chắc là một phương thức override**.

Với kiểu kế thừa xuyên package, vấn đề không chỉ là lớp con có gọi được phương thức hay không. Nó còn quyết định lớp con có thể trở thành lớp cụ thể (`concrete class`) hay phải tiếp tục là lớp `abstract`. Bài này giải thích cơ chế, trình bày các trường hợp biên dịch được và không biên dịch được, rồi chỉ ra cách thiết kế đúng.

## 1. Nhắc lại về package-private và abstract

Một phương thức trong lớp được khai báo **package-private** khi thiếu cả ba access modifier `public`, `protected`, `private`:

```java
package academy.base;

public abstract class Task {
    abstract String auditCode(); // package-private + abstract
    public abstract String execute();
}
```

- `abstract String auditCode();` không có thân hàm, yêu cầu được cung cấp triển khai phù hợp trước khi có thể tạo một đối tượng của lớp cụ thể.
- Không ghi access modifier nghĩa là **package-private**, đôi khi gọi là *default access*. Đừng nhầm với từ khóa `default` của phương thức interface.
- Quyền truy cập package-private gắn với **package khai báo lớp/phương thức**, không dựa vào việc hai lớp có tên gần giống nhau hay thuộc cùng một thư mục gốc.
- Các lớp trong **cùng package** có thể truy cập phương thức package-private (nếu thỏa các quy tắc thành viên khác). Lớp nằm **khác package** không có quyền truy cập thành viên đó theo cơ chế thông thường.
- `public abstract String execute();` được khai báo public, nên lớp con phù hợp ở package khác có thể override bằng một phương thức public.

Một phương thức abstract trong lớp có thể là `public`, `protected` hoặc package-private. Không thể kết hợp `abstract` với `private`, `final` hay `static`: chúng mâu thuẫn với yêu cầu về override của phương thức abstract.

### Override không chỉ dựa trên chữ ký

Khi xác định hai phương thức có quan hệ **overriding**, Java xét vị trí trong cây kế thừa, chữ ký, các quy tắc kiểu trả về, quyền truy cập và package. Đặc biệt, phương thức package-private trong lớp cha **không được lớp con khác package override chỉ bằng cách khai báo lại một phương thức cùng chữ ký**. Phương thức mới có thể tồn tại trong lớp con, nhưng nó không phải cách triển khai hợp lệ cho phương thức abstract package-private ban đầu.

Điều này khác với `protected`: phương thức `protected abstract` có thể được lớp con ở package khác override đúng cách, miễn đáp ứng các điều kiện còn lại.

## 2. Ví dụ lỗi: lớp con ở package khác

Xét hai tệp Java độc lập:

**`src/academy/base/Task.java`**

```java
package academy.base;

public abstract class Task {
    protected Task() {}

    abstract String auditCode(); // Chỉ package academy.base
    public abstract String execute();
}
```

**`src/academy/app/BadRemoteTask.java`**

```java
package academy.app;

import academy.base.Task;

public final class BadRemoteTask extends Task {
    public String auditCode() {
        return "attempt";
    }

    @Override
    public String execute() {
        return "remote";
    }
}
```

`Task` ở package `academy.base` và `BadRemoteTask` ở package `academy.app`. Mặc dù `BadRemoteTask.auditCode()` có cùng tên, không có tham số và cùng kiểu trả về `String`, phương thức này **không override** `Task.auditCode()`. Việc thêm `public` không thay đổi quan hệ đó. Vì thế, một `BadRemoteTask` **không phải abstract** chưa hoàn tất triển khai abstract method mà nó phải đáp ứng.

Biên dịch riêng ví dụ lỗi từ thư mục chứa `src`:

```bash
javac -encoding UTF-8 -d out src/academy/base/Task.java src/academy/app/BadRemoteTask.java
```

Kết quả: **biên dịch thất bại**. Chẩn đoán điển hình của `javac` là lớp `BadRemoteTask` không abstract và chưa override phương thức abstract `auditCode()` trong `Task`. Cách diễn đạt hoặc vị trí dòng lỗi có thể thay đổi tùy phiên bản compiler.

Nếu thêm `@Override` ngay trên `public String auditCode()` ở `BadRemoteTask`, compiler còn phát hiện trực tiếp rằng phương thức đó **không override/implement một phương thức từ supertype**. `@Override` không thể “ép” Java thay đổi quy tắc về package và visibility.

### Vì sao lớp con abstract lại khác?

Nếu khai báo `BadRemoteTask` là `abstract class`, lớp đó có thể tồn tại như một lớp trung gian chưa hoàn tất yêu cầu abstract của `Task`; vấn đề triển khai sẽ còn đó khi muốn tạo một lớp cụ thể về sau. Không thể sửa lỗi bằng cách chỉ thay `final class` thành một lớp cụ thể khác ở package ngoài trong khi giữ nguyên phương thức abstract package-private chưa được triển khai đúng.

Ví dụ rõ hơn về một khai báo **không override** (đặt trong lớp abstract ở package khác):

```java
// Trong package academy.app; chỉ là mảnh mã minh họa.
public abstract class ExternalTask extends academy.base.Task {
    public String auditCode() { // Không override Task.auditCode()
        return "another method";
    }

    @Override
    public String execute() {
        return "external";
    }
}
```

Đặt `@Override` lên `auditCode()` trong mảnh mã này sẽ tạo lỗi biên dịch. Kể cả không đặt annotation, tên phương thức trùng không có nghĩa là abstract method bên package `academy.base` đã được thực thi.

## 3. Ví dụ đúng: triển khai phương thức tại chính package của lớp cha

Có thể giữ nguyên `auditCode()` là package-private nếu thiết kế muốn phần triển khai nội bộ nằm trong `academy.base`. Tạo một lớp trung gian ở **cùng package với `Task`** để override phương thức đó, rồi cho lớp ở package khác kế thừa lớp trung gian.

Cấu trúc tệp đầy đủ:

```text
src/
└── academy/
    ├── base/
    │   ├── Task.java
    │   └── VerifiedTask.java
    └── app/
        ├── RemoteTask.java
        └── Main.java
```

**Tệp `src/academy/base/Task.java`**

```java
package academy.base;

public abstract class Task {
    protected Task() {}

    abstract String auditCode();

    public abstract String execute();

    public final String report() {
        return auditCode() + " | " + execute();
    }
}
```

**Tệp `src/academy/base/VerifiedTask.java`**

```java
package academy.base;

public abstract class VerifiedTask extends Task {
    protected VerifiedTask() {
        super();
    }

    @Override
    String auditCode() {
        return "verified";
    }
}
```

**Tệp `src/academy/app/RemoteTask.java`**

```java
package academy.app;

import academy.base.VerifiedTask;

public final class RemoteTask extends VerifiedTask {
    @Override
    public String execute() {
        return "remote";
    }
}
```

**Tệp `src/academy/app/Main.java`**

```java
package academy.app;

import academy.base.Task;

public class Main {
    public static void main(String[] args) {
        Task task = new RemoteTask();

        System.out.println(task.report());
        System.out.println(task.execute());

        // task.auditCode(); // Không biên dịch: package-private của academy.base
    }
}
```

Từ thư mục chứa `src`, biên dịch Java 17+:

```bash
javac -encoding UTF-8 -d out src/academy/base/Task.java src/academy/base/VerifiedTask.java src/academy/app/RemoteTask.java src/academy/app/Main.java
java -cp out academy.app.Main
```

**Standard output chính xác:**

```text
verified | remote
remote
```

**Phân tích:**

1. `Task.auditCode()` là package-private abstract. `VerifiedTask` được khai báo **cùng package `academy.base`** và override bằng `String auditCode()` có cùng mức truy cập. Vì vậy yêu cầu abstract `auditCode()` đã có triển khai hợp lệ.
2. `VerifiedTask` vẫn abstract vì chưa triển khai `execute()`. Lớp trung gian abstract là một lựa chọn thiết kế, không phải lỗi.
3. `RemoteTask` ở package `academy.app` kế thừa `VerifiedTask`, cung cấp `public String execute()` cho phương thức public abstract còn thiếu và trở thành lớp cụ thể.
4. Khi `Task.report()` gọi `auditCode()`, JVM chạy phần triển khai `VerifiedTask.auditCode()`. Tính đa hình qua phương thức instance đã được override vẫn hoạt động; **giới hạn truy cập ở mã nguồn không đồng nghĩa với việc JVM không thể dispatch nội bộ**.
5. Lệnh `task.auditCode()` ở `Main` vẫn không hợp lệ, do `Main` thuộc package `academy.app` và phương thức vẫn là package-private.
6. `Task task = new RemoteTask();` dùng tham chiếu kiểu cha và đối tượng thật thuộc lớp con; lời gọi `execute()` chạy phần triển khai trong `RemoteTask`.

Lưu ý rằng hai thư mục `base` và `app` được tách thành **hai package Java khác nhau** nhờ câu lệnh `package academy.base;` và `package academy.app;`. Package `academy.base.internal` cũng là package **khác** với `academy.base`; Java không cấp quyền package-private theo cấu trúc tên phân cấp.

## 4. Cách đúng nếu lớp ngoài package cần tự triển khai phương thức

Nếu yêu cầu nghiệp vụ là “mỗi lớp con bên ngoài package phải tự cài đặt `auditCode()`”, khai báo package-private abstract là thiết kế không phù hợp. Hãy dùng **`protected abstract` hoặc `public abstract`**.

### Lựa chọn A — `protected abstract`

Tệp `src/academy/base/Task.java` trong phương án thay thế:

```java
package academy.base;

public abstract class Task {
    protected Task() {}

    protected abstract String auditCode();

    public final String report() {
        return auditCode();
    }
}
```

Tệp `src/academy/app/RemoteTask.java` trong phương án thay thế:

```java
package academy.app;

import academy.base.Task;

public final class RemoteTask extends Task {
    @Override
    protected String auditCode() {
        return "remote-audit";
    }
}
```

Đây là **một phương án độc lập**, thay thế định nghĩa `Task` ở ví dụ chính; không biên dịch hai định nghĩa `Task` cùng lúc. `RemoteTask` có thể override hợp lệ vì phương thức cha là `protected`. Bên ngoài package, một lớp bất kỳ **không tự động** được phép gọi `task.auditCode()`: `protected` có quy tắc truy cập riêng cho package và subclass. Quyền override trong lớp con cũng **khác** quyền gọi qua một biến tham chiếu bất kỳ từ mã bên ngoài.

Nên dùng `protected abstract` cho **điểm mở rộng dành cho lớp con** mà không nhất thiết công khai thành API cho mọi mã khách hàng.

### Lựa chọn B — `public abstract`

Khi cả lớp con lẫn mã khách hàng cần truy cập công khai:

```java
package academy.base;

public abstract class Task {
    public abstract String auditCode();
}
```

```java
// Trong tệp khác thuộc package academy.app
package academy.app;

public final class RemoteTask extends academy.base.Task {
    @Override
    public String auditCode() {
        return "remote-audit";
    }
}
```

Hai đoạn trên đại diện **hai tệp riêng**, không ghép nhiều `package` vào một tệp Java. Khi override phương thức public, phương thức triển khai cũng phải public; không thể giảm visibility thành `protected` hoặc package-private.

`public` thích hợp khi đây là hành vi muốn đưa ra **giao diện sử dụng công khai**. Cân nhắc độ ổn định API, vì mã ngoài package có thể phụ thuộc vào phương thức.

### Lựa chọn C — Hoàn tất phần nội bộ trong cùng package

Giữ package-private abstract khi một hook là chi tiết nội bộ chỉ các lớp trong package chủ quản được phép override. Tạo lớp trung gian cùng package như `VerifiedTask` đã minh họa, sau đó cung cấp các điểm mở rộng public/protected khác cho lớp ở package ngoài.

## 5. So sánh các mức truy cập khi override xuyên package

Giả sử `Child` ở `academy.app` kế thừa `Parent` ở `academy.base`, và không có lớp trung gian đã implement phương thức abstract trước đó:

| Phương thức trong `Parent` | `Child` có thể override hợp lệ? | `Child` có thể tự hoàn tất yêu cầu abstract để trở thành concrete? |
| --- | --- | --- |
| `abstract void work();` (package-private) | **Không** qua khai báo cùng chữ ký ở package khác | **Không**; cần triển khai hợp lệ từ lớp ở cùng package cha hoặc thay đổi thiết kế |
| `protected abstract void work();` | **Có**, với visibility hợp lệ | **Có** |
| `public abstract void work();` | **Có**, bằng phương thức `public` | **Có** |
| `private void work() { ... }` | **Không**, private không được override | Không đặt ra yêu cầu abstract; `private abstract` là khai báo bất hợp lệ |
| `final void work() { ... }` | **Không**, final chặn override | Không có yêu cầu abstract do `final abstract` bất hợp lệ |

**Quy tắc chung:** lớp con override không được giảm quyền truy cập so với phương thức mà nó thực sự override. Nhưng **tăng visibility của một phương thức trùng tên ở package khác không tự tạo được quan hệ override** cho phương thức package-private inaccessible.

Trong cùng package, phương thức package-private có thể được override bằng package-private, `protected` hoặc `public`, miễn đáp ứng các quy tắc khác. Khi mở rộng visibility, phương thức mới thực sự override vì lớp con cùng package có quyền làm vậy.

## 6. Phân biệt abstract class và interface

Interface thường dùng làm hợp đồng cho nhiều lớp triển khai ở các package khác nhau. Trong Java, một phương thức **abstract của interface thông thường luôn là public** dù bạn không viết từ khóa public:

```java
package academy.base;

public interface Auditable {
    String auditCode(); // ngầm định public abstract
}
```

```java
package academy.app;

public final class RemoteAudit implements academy.base.Auditable {
    @Override
    public String auditCode() {
        return "remote-audit";
    }
}
```

Hai đoạn là hai tệp riêng, mỗi public type có tệp cùng tên. `RemoteAudit` ở package khác vẫn implement được `auditCode()` vì phương thức interface là public. Không thể implement phương thức public của interface bằng một phương thức package-private; compiler sẽ báo giảm visibility.

Không được suy luận từ cú pháp `String auditCode();` rằng nó luôn package-private: **trong class** nếu không có access modifier thì package-private, còn **trong interface** một phương thức abstract không khai báo modifier truy cập là public. Java 9+ còn cho phép private methods **có thân** trong interface để tái sử dụng logic nội bộ; chúng không phải phương thức abstract để implement từ bên ngoài.

## 7. Phân biệt “không truy cập được” với “không được thực thi”

Có ba câu hỏi độc lập:

- **Mã lớp con ở package khác có nhìn thấy/gọi được `Task.auditCode()` không?** Không, do package-private.
- **Lớp con đó có override phương thức package-private abstract trực tiếp được không?** Không; một phương thức trùng chữ ký không hoàn thành yêu cầu override.
- **Sau khi lớp cùng package implement phương thức, một phương thức public của `Task` có thể gọi implementation ấy cho đối tượng thuộc subclass package khác không?** Có, như `report()` trong ví dụ chạy được. Lời gọi xảy ra bên trong phương thức đã có quyền truy cập và dynamic dispatch tới implementation hợp lệ.

Đây là lý do không thể chỉ nói rằng “package-private không truy cập được nên JVM không chạy được phương thức”. Quy tắc về **truy cập ở mã nguồn** và **phân giải phương thức instance tại runtime** có vai trò khác nhau.

Ngoài ra, chỉ đổi kiểu của biến tham chiếu không thể mở quyền truy cập package-private:

```java
// Trong package academy.app; sử dụng các lớp ở ví dụ chạy được
academy.base.Task task = new RemoteTask();
academy.base.VerifiedTask bridge = new RemoteTask();

// task.auditCode();   // Không được phép gọi
// bridge.auditCode(); // Vẫn không được phép gọi
System.out.println(task.report()); // Hợp lệ
```

Biến tham chiếu kiểu cha hay kiểu lớp trung gian không thay đổi package của mã đang gọi. Cast xuống lớp con cũng không mở quyền truy cập phương thức package-private của package khác.

## 8. Lỗi thường gặp và cách kiểm tra

**Lỗi 1 — Cùng chữ ký nên chắc chắn override.** Với package-private và khác package, suy luận này sai. Đặt `@Override` khi dự định override để compiler xác thực. Nếu annotation báo lỗi, kiểm tra package và access modifier trước khi đoán lỗi do compiler.

**Lỗi 2 — Thêm `public` trong lớp con là đủ.** Không đủ với phương thức package-private abstract ở lớp cha khác package. Cần thay visibility **ở lớp cha** (khi bạn kiểm soát API), hoặc có một lớp trong đúng package cha triển khai trước.

**Lỗi 3 — Biến lớp con thành `abstract` rồi cho rằng đã sửa xong.** Điều này chỉ trì hoãn nghĩa vụ cung cấp implementation. Nếu muốn một lớp concrete từ nhánh đó, vẫn cần một implementation hợp lệ của abstract method còn tồn tại.

**Lỗi 4 — Nhầm package con với cùng package.** `academy.base` và `academy.base.impl` là hai package khác nhau. Không có đặc quyền package-private qua dấu chấm trong tên.

**Lỗi 5 — Quên điều kiện về constructor.** Muốn kế thừa từ package khác, lớp cha còn phải có constructor mà lớp con có thể gọi (thường là `protected` hoặc `public`). Nếu constructor không truy cập được, sẽ xuất hiện lỗi độc lập với abstract method. Các ví dụ ở trên dùng `protected Task() {}` để tách bạch hai vấn đề.

**Lỗi 6 — Nhầm class với interface.** `void run();` trong interface là `public abstract`; `abstract void run();` không có access modifier trong class là package-private. Implement interface public method bằng package-private method sẽ bị compiler từ chối.

**Lỗi 7 — Gọi package-private từ ngoài qua phương thức public trả về cùng đối tượng.** Việc có tham chiếu đến đối tượng không cấp quyền gọi thành viên inaccessible. Hãy cung cấp public method làm API hợp lệ, như `Task.report()`, nếu muốn cho phép một thao tác sử dụng phần implementation nội bộ.

### Quy trình chẩn đoán khi code không biên dịch

1. Mở khai báo abstract method **trong class hay interface**; xác định đúng visibility.
2. Xem chính xác câu lệnh `package ...;` của cả lớp cha, lớp trung gian và lớp con. Chỉ cùng chuỗi tên package mới là cùng package theo nghĩa đang xét (trong bố cục classpath/module thông thường).
3. Kiểm tra đã có lớp **cùng package với nơi khai báo phương thức** cung cấp override hợp lệ trên nhánh kế thừa hay chưa.
4. Kiểm tra `@Override` trên implementation được mong đợi, chữ ký, kiểu trả về và quyền truy cập. Nếu compiler nói không override, đừng xóa annotation như một cách che giấu vấn đề.
5. Kiểm tra lớp định tạo bằng `new` đã implement hết các abstract method còn lại chưa; lớp cụ thể không thể mang nghĩa vụ abstract chưa được đáp ứng.
6. Xem constructor của lớp cha có truy cập được từ subclass khác package không. Khi sửa visibility, chạy lại `javac` trên toàn bộ bộ tệp liên quan.

## 9. Tự kiểm tra

**Câu 1.** `package a; public abstract class Base { abstract void f(); }`. Trong `package b`, một lớp concrete `Derived extends Base` khai báo `public void f() {}`. Có compile được không?

**Đáp án:** Không. Phương thức `f()` mới bên `b` không override `Base.f()` package-private; lớp concrete vẫn chưa đáp ứng abstract method ở lớp cha.

**Câu 2.** Nếu chỉ đổi `Base.f()` thành `protected abstract void f();` và giữ `Derived.public void f() {}` thì sao?

**Đáp án:** Có thể override hợp lệ, vì `protected` cho phép subclass ở package khác override và `public` không giảm quyền truy cập. Các điều kiện khác như constructor, tên tệp, chữ ký vẫn phải đúng.

**Câu 3.** Nếu giữ `Base.f()` package-private nhưng thêm `Bridge extends Base` trong package `a` để override `f()`, rồi cho `Derived extends Bridge` ở package `b` và triển khai các abstract method còn lại?

**Đáp án:** Có thể tạo `Derived` concrete sau khi mọi abstract method đã được triển khai hợp lệ. Đây là mô hình đã chạy trong mục 3.

**Câu 4.** Trong interface `I { void f(); }`, phương thức `f()` có package-private không?

**Đáp án:** Không. Một abstract method của interface như vậy ngầm định `public abstract`; một lớp implement ở package khác được phép và phải cung cấp phương thức `public`.

**Điểm cốt lõi:** Với **abstract method package-private trong class**, *package của lớp triển khai* quyết định khả năng override. Subclass ở package khác không thể “hoàn thành” abstract method đó bằng khai báo cùng chữ ký. Muốn tạo điểm mở rộng xuyên package, dùng `protected/public abstract`; muốn giữ hook nội bộ, triển khai nó trong package của lớp cha trước khi mở rộng ra ngoài.


</details>

- [Quay lại đầu trang](#back-to-top)
