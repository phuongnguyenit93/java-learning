<a id="back-to-top"></a>

# Khai báo interface

## Menu
- [Cú pháp, tên và mục đích của interface](#interface-declaration-001)
- [Interface là kiểu tham chiếu và ngầm định abstract](#interface-declaration-002)
- [Interface cấp cao nhất: public và package-private](#interface-declaration-003)
- [Interface mở rộng một hoặc nhiều interface bằng extends](#interface-declaration-004)
- [Lớp thực thi một hoặc nhiều interface bằng implements](#interface-declaration-005)
- [Vì sao interface không có constructor và thuộc tính instance](#interface-declaration-006)
- [Kiểu thành viên được khai báo bên trong interface](#interface-declaration-007)
- [Phân biệt interface thông thường với annotation interface](#interface-declaration-008)

## <a id="interface-declaration-001">Cú pháp, tên và mục đích của interface</a>

<details>
<summary>Xem chi tiết</summary>

# Cú pháp, tên và mục đích của interface trong Java

## 1. Interface mô tả hợp đồng hành vi

Một `interface` là kiểu Java công bố những thao tác mà một đối tượng có thể cung cấp. Nó giúp mã gọi làm việc với **khả năng** của đối tượng mà không phải phụ thuộc vào một lớp cụ thể. Ví dụ, hệ thống thanh toán chỉ cần biết cách gọi `pay`; tiền mặt, thẻ hoặc chuyển khoản có thể triển khai hành vi đó theo cách riêng.

```java
interface PaymentMethod {
    String pay(int amount);
}

final class CashPayment implements PaymentMethod {
    @Override
    public String pay(int amount) {
        return "Cash: " + amount;
    }
}

public class Main {
    public static void main(String[] args) {
        PaymentMethod payment = new CashPayment();
        System.out.println(payment.pay(150));
    }
}
```

Lưu thành `Main.java`, biên dịch và chạy bằng JDK 17+:

```bash
javac Main.java
java Main
```

Kết quả:

```text
Cash: 150
```

Biến `payment` có kiểu khai báo `PaymentMethod`, còn đối tượng thực tế là `CashPayment`. Khi gọi `pay`, Java thực thi phần cài đặt của đối tượng đó. Hợp đồng kiểu được trình biên dịch kiểm tra; ý nghĩa nghiệp vụ như “thanh toán thành công” vẫn cần được cài đặt và kiểm thử.

## 2. Cú pháp và cách đặt tên

Cú pháp cơ bản:

```java
public interface PaymentMethod {
    String pay(int amount);
}
```

- `interface` là từ khóa khai báo kiểu.
- `PaymentMethod` là tên kiểu; quy ước Java dùng **PascalCase** cho tên class và interface.
- `String pay(int amount);` là phương thức được công bố. Dấu chấm phẩy cho biết đây là khai báo không có thân phương thức.
- `public` cho phép mã ở package khác tham chiếu kiểu khi các điều kiện truy cập liên quan được đáp ứng. Nếu bỏ modifier trên interface cấp cao nhất, kiểu trở thành package-private; bài về truy cập sẽ giải thích kỹ hơn.

Tên interface nên thể hiện vai trò hoặc khả năng, chẳng hạn `Runnable`, `Comparable<T>`, `Closeable`, `MessageSender` hoặc `PriceCalculator`. Tránh tên mơ hồ như `CommonInterface` nếu không cho biết hợp đồng gì được cung cấp. Tên phương thức thường là động từ hoặc cụm mô tả hành vi; hằng số thường viết `UPPER_SNAKE_CASE`.

## 3. Interface giúp ích khi nào?

Interface hữu ích khi:

1. Nhiều lớp cung cấp cùng một khả năng nhưng có cách thực hiện khác nhau.
2. Thành phần sử dụng nên phụ thuộc vào hợp đồng ổn định thay vì tự tạo một implementation cụ thể.
3. Cần thay implementation trong kiểm thử, ví dụ truyền một sender giả để xác minh thông điệp được gửi.
4. Một lớp cần đảm nhiệm nhiều vai trò độc lập, như vừa có thể in vừa có thể lưu.

Không cần tạo interface cho mọi class. Nếu chưa có ranh giới thay thế hoặc hợp đồng có ích, interface trung gian chỉ làm tăng số kiểu và tệp phải quản lý.

## 4. Phân biệt nhanh với class và abstract class

| Cơ chế | Trọng tâm | Constructor và trạng thái instance | Quan hệ kế thừa |
| --- | --- | --- | --- |
| Class cụ thể | Cung cấp implementation và tạo đối tượng | Có thể có | Một superclass trực tiếp |
| Abstract class | Chia sẻ implementation hoặc trạng thái cho các lớp con | Có thể có constructor và field instance | Một superclass trực tiếp |
| Interface | Công bố hợp đồng và kiểu đa hình | Không có constructor hoặc field instance | Một class có thể triển khai nhiều interface |

Interface hiện đại còn có thể chứa phương thức `default`, `static` và `private`, nên không phải mọi phương thức đều trừu tượng. Các bài tiếp theo phân tích kiểu tham chiếu, quyền truy cập, `extends`, `implements`, giới hạn trạng thái, kiểu thành viên và annotation interface.

## 5. Lỗi thường gặp

- Dùng `new PaymentMethod()`: interface không thể được khởi tạo trực tiếp; hãy tạo một implementation.
- Đổi chữ ký ở class, ví dụ `pay(String)` thay cho `pay(int)`: đây không phải implementation của phương thức đã khai báo.
- Giảm quyền truy cập khi triển khai phương thức public của interface: implementation phải giữ mức truy cập `public`.
- Tạo interface chỉ để phản chiếu một class mà không có nhu cầu về hợp đồng hay thay thế: cân nhắc xem abstraction đó có giúp thiết kế rõ hơn không.

**Tự kiểm tra:** Nếu `CardPayment` và `CashPayment` cùng triển khai `PaymentMethod`, thành phần nhận tham số kiểu `PaymentMethod` có cần biết tên hai class đó không? Không; nó chỉ cần gọi các thao tác được công bố bởi hợp đồng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-declaration-002">Interface là kiểu tham chiếu và ngầm định abstract</a>

<details>
<summary>Xem chi tiết</summary>

# Interface là kiểu tham chiếu và ngầm định abstract trong Java

## 1. Kiểu tham chiếu và đối tượng thực tế

Tên interface là một **kiểu tham chiếu**. Nó có thể xuất hiện ở khai báo biến, tham số, kiểu trả về, field và đối số generic, giống như các kiểu tham chiếu khác.

```java
interface MessageSender {
    void send(String message);
}

final class ConsoleSender implements MessageSender {
    @Override
    public void send(String message) {
        System.out.println("Sent: " + message);
    }

    public void clearHistory() {
        System.out.println("History cleared");
    }
}

public class Main {
    public static void main(String[] args) {
        MessageSender sender = new ConsoleSender();
        sender.send("hello");
        System.out.println(sender instanceof MessageSender);
    }
}
```

Kết quả:

```text
Sent: hello
true
```

Có ba điều cần tách biệt:

- **Kiểu khai báo của biến** là `MessageSender`, nên compiler chỉ cho gọi các thành viên có thể truy cập qua kiểu đó.
- **Kiểu runtime của đối tượng** là `ConsoleSender`, nên lời gọi phương thức instance được dispatch đến implementation thích hợp của đối tượng.
- Quan hệ `implements` cho phép gán `ConsoleSender` vào biến `MessageSender`; phép gán không tạo ra một đối tượng interface riêng.

Dù đối tượng thực tế có phương thức `clearHistory()`, biểu thức `sender.clearHistory()` vẫn không biên dịch vì phương thức đó không thuộc API được khai báo bởi kiểu `MessageSender`.

## 2. Vì sao interface ngầm định abstract?

Một khai báo interface vốn đã là trừu tượng. Hai cách viết sau có cùng ý nghĩa về tính abstract:

```java
interface Task {
    void execute();
}

abstract interface LegacyTask {
    void execute();
}
```

Cách viết thông dụng là `interface Task`; từ khóa `abstract` ở khai báo interface là dư thừa. Không thể tạo instance trực tiếp bằng `new Task()` vì interface không có constructor và không định nghĩa một implementation cụ thể cho mọi hành vi.

Một lớp concrete triển khai `Task` phải cung cấp implementation hợp lệ cho các phương thức abstract còn thiếu, hoặc thừa hưởng implementation phù hợp. Một lớp vẫn còn thiếu phương thức có thể được khai báo `abstract`.

**Không suy ra rằng mọi phương thức trong interface đều abstract.** Ví dụ, Java hiện đại cho phép phương thức `default`, `static` và `private` có thân. Một phương thức instance thông thường được khai báo không có thân trong interface có ngữ nghĩa `public abstract` dù không viết hai modifier đó.

## 3. Anonymous class và lambda không phải constructor của interface

Java cho phép tạo một đối tượng triển khai thông qua anonymous class:

```java
Runnable task = new Runnable() {
    @Override
    public void run() {
        System.out.println("running");
    }
};
task.run();
```

Đối tượng trên thuộc một lớp ẩn danh do cú pháp tạo ra; không có instance trực tiếp của `Runnable`. Với functional interface, lambda cũng có thể cung cấp implementation:

```java
Runnable task = () -> System.out.println("running");
task.run();
```

Lambda chỉ áp dụng khi interface thỏa điều kiện functional interface: có một abstract method phù hợp sau khi xét các quy tắc Java. Phương thức `default`, `static` và `private` không được tính như abstract method bổ sung.

## 4. Những lỗi cần nhận diện

| Cách viết | Vì sao |
| --- | --- |
| `new Task()` | Không thể khởi tạo interface trực tiếp. |
| Lớp concrete bỏ sót abstract method | Không hoàn tất hợp đồng kiểu. |
| Implementation có cùng tên nhưng khác tham số | Đó có thể là overload, không phải override. |
| Gọi phương thức riêng của class qua biến interface | Compiler kiểm tra theo kiểu khai báo của biểu thức. |
| Cho rằng `default` nghĩa là mọi phương thức interface đều có body | `default` chỉ áp dụng cho loại phương thức được khai báo theo cú pháp đó. |

**Tự kiểm tra:** Với `MessageSender sender = new ConsoleSender()`, kiểu nào quyết định các phương thức có thể gọi ở thời điểm biên dịch? `MessageSender`. Kiểu nào cung cấp implementation chạy cho `send`? Đối tượng thực tế `ConsoleSender`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-declaration-003">Interface cấp cao nhất: public và package-private</a>

<details>
<summary>Xem chi tiết</summary>

# Interface cấp cao nhất: public và package-private

## 1. Vị trí khai báo và hai mức truy cập

**Top-level interface** được khai báo trực tiếp trong tệp nguồn, bên ngoài mọi class, interface, enum hoặc record khác. Một top-level interface chỉ có hai lựa chọn về mức truy cập:

| Cú pháp | Mức truy cập | Ai có thể tham chiếu tên kiểu? |
| --- | --- | --- |
| `public interface PublicService { ... }` | public | Mã ở package khác có thể dùng kiểu nếu package/module chứa nó được truy cập. |
| `interface InternalService { ... }` | package-private | Mã có quyền truy cập trong cùng package. |

Package-private không phải từ khóa. Nó được tạo bằng cách bỏ modifier truy cập. Một top-level interface không thể là `private` hoặc `protected`; `static` cũng không phải modifier hợp lệ cho top-level interface.

## 2. Tên tệp và ranh giới package

Với cách tổ chức nguồn Java thông thường, nếu một tệp có top-level type `public` thì tên tệp phải khớp tên kiểu public đó. Một tệp không nên khai báo nhiều top-level public type.

Ví dụ, tệp `PublicService.java`:

```java
package demo.api;

public interface PublicService {
    String greet(String name);
}
```

Tệp `InternalService.java` trong cùng package:

```java
package demo.api;

interface InternalService {
    String auditTag();
}
```

`InternalService` có thể được dùng từ các tệp khác trong `demo.api`, dù các tệp nằm riêng. Nó không thể được import trực tiếp từ `demo.client`. Package `demo.api.internal` cũng là package khác; tên trông giống package cha không cấp quyền package-private.

Một lớp triển khai có thể được giữ package-private và được cung cấp ra ngoài qua factory public:

```java
package demo.api;

public final class Services {
    private Services() {}

    public static PublicService create() {
        return new DefaultService();
    }
}

final class DefaultService implements PublicService, InternalService {
    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }

    @Override
    public String auditTag() {
        return "internal";
    }
}
```

Mã bên ngoài package chỉ cần `PublicService service = Services.create()`. Nó không phải biết tên lớp triển khai `DefaultService`.

## 3. Quyền truy cập của kiểu khác với thành viên

Không nên suy ra rằng phương thức trong interface package-private cũng trở thành package-private. Quy tắc modifier của kiểu và thành viên là hai vấn đề riêng. Ví dụ, một phương thức abstract thông thường trong interface vẫn ngầm định `public abstract`; field interface ngầm định `public static final`.

Dù thành viên được khai báo public, mã ngoài package vẫn không thể đặt tên một kiểu top-level package-private trong khai báo của mình. Kiểu phải truy cập được trước khi quyền truy cập thành viên có ý nghĩa.

Interface được khai báo bên trong class/interface là member interface, không phải top-level interface. Member interface có quy tắc truy cập của kiểu thành viên và có thể dùng `private` hoặc `protected` khi vị trí khai báo cho phép.

## 4. Java Platform Module System (JPMS)

Trong dự án có named modules, modifier `public` là điều kiện cần nhưng chưa đủ để module khác dùng kiểu. Thường cần cả:

1. Module sử dụng đọc được module cung cấp, thông qua `requires`.
2. Module cung cấp export package chứa kiểu qua `exports`.
3. Bản thân top-level interface được khai báo `public`.

Ví dụ:

```java
module demo.api.module {
    exports demo.api;
}
```

Module khác còn phải khai báo `requires demo.api.module;`. Nếu package không được export, một public interface trong package đó không tự động trở thành API công khai cho module khác.

## 5. Checklist khi chẩn đoán lỗi truy cập

- Kiểm tra dòng `package` thực tế ở cả hai tệp; đừng dựa vào tên thư mục.
- Kiểm tra kiểu top-level có `public` nếu cần được dùng từ package khác.
- Kiểm tra tên tệp trùng với kiểu top-level public.
- Nếu có `module-info.java`, kiểm tra cả `requires` và `exports`.
- Nếu chỉ cần lớp nội bộ phối hợp, cân nhắc package-private để tránh công bố một phần API không cần thiết.

**Tự kiểm tra:** Nếu `InternalService` nằm trong `demo.api`, lớp thuộc `demo.api.client` có thể truy cập nó chỉ vì package đó có tiền tố giống nhau không? Không; đó là package khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-declaration-004">Interface mở rộng một hoặc nhiều interface bằng extends</a>

<details>
<summary>Xem chi tiết</summary>

# Interface mở rộng một hoặc nhiều interface bằng extends

## 1. Cú pháp và quan hệ subtype

Interface dùng `extends` để kế thừa một hoặc nhiều interface. Danh sách superinterface được ngăn cách bằng dấu phẩy:

```java
interface Identified {
    String id();
}

interface Named {
    String name();
}

interface CatalogEntry extends Identified, Named {
    String category();
}
```

`CatalogEntry` kế thừa hợp đồng của `Identified` và `Named`, đồng thời thêm `category()`. Một giá trị kiểu `CatalogEntry` cũng có thể được dùng qua kiểu `Identified` hoặc `Named`. Đây là quan hệ subtype; không tạo bản sao đối tượng.

Interface không thể `extends` class, và Java cấm vòng kế thừa. Cú pháp `interface Child implements Parent` là sai; `implements` dùng ở khai báo class.

## 2. Thành viên nào được kế thừa?

| Thành viên của superinterface | Quy tắc chính |
| --- | --- |
| Abstract instance method | Trở thành phần của hợp đồng con; class concrete phải đáp ứng nếu chưa có implementation hợp lệ. |
| `default` instance method | Có thể được kế thừa cùng implementation, trừ khi bị override hoặc có xung đột. |
| Field hằng `public static final` | Có thể được tham chiếu qua interface con, nhưng tên trùng có thể mơ hồ. |
| `static` method | Không được kế thừa như method của interface con; gọi qua tên interface khai báo nó. |
| `private` method | Là helper nội bộ của interface khai báo, không được kế thừa. |

Quan hệ kế thừa có tính bắc cầu. Nếu `Leaf extends Middle` và `Middle extends Root`, một class triển khai `Leaf` cũng phải thỏa các hợp đồng abstract còn thiếu của `Middle` và `Root`.

## 3. Các khai báo abstract tương thích

Hai superinterface có thể khai báo abstract method cùng chữ ký:

```java
interface Reader {
    String read();
}

interface TextSource {
    String read();
}

interface ReadableText extends Reader, TextSource {}

final class FileText implements ReadableText {
    @Override
    public String read() {
        return "hello";
    }
}
```

Một implementation `public String read()` đáp ứng cả hai hợp đồng. Kiểu trả về cũng phải tương thích theo quy tắc Java. Ví dụ, một method trả `Integer` có thể đáp ứng các khai báo trả `Integer` và `Number` tương thích trong cùng hierarchy. Trái lại, `String get()` và `Integer get()` không thể được phân biệt chỉ bằng return type; tổ hợp không tương thích sẽ gây lỗi biên dịch.

Với generic interface, đối số kiểu được áp dụng xuyên suốt chuỗi kế thừa. Việc kế thừa cùng generic interface qua các đối số kiểu không tương thích có thể bị từ chối do xung đột kiểu hoặc type erasure.

## 4. Xung đột default method

Hai interface không liên quan có thể cùng khai báo một `default` method. Interface con phải giải quyết xung đột thay vì để Java tự chọn tùy ý.

```java
interface Left {
    default String label() {
        return "left";
    }
}

interface Right {
    default String label() {
        return "right";
    }
}

interface Combined extends Left, Right {
    @Override
    default String label() {
        return Left.super.label() + "/" + Right.super.label();
    }
}

public class Main {
    public static void main(String[] args) {
        System.out.println(new Choice().label());
    }
}

final class Choice implements Combined {}
```

Kết quả:

```text
left/right
```

`Left.super.label()` và `Right.super.label()` gọi rõ implementation từ superinterface trực tiếp phù hợp. Cách khác là tái khai báo `String label();` trong `Combined` để buộc class concrete cung cấp implementation. Nếu interface con là nhánh chuyên biệt hơn và override một default method của cha, implementation chuyên biệt có thể được ưu tiên theo quy tắc Java; phương thức instance hợp lệ từ class cũng có thể thắng default method.

## 5. Những nhầm lẫn cần tránh

- `extends A, B` dùng dấu phẩy, không dùng dấu `&` trong danh sách superinterface.
- `static` method của cha không trở thành `Child.staticMethod()`.
- `private` helper không phải một phần hợp đồng được lớp triển khai cung cấp.
- Một class concrete triển khai interface con vẫn phải đáp ứng các abstract method được kế thừa.
- `sealed interface` (Java 17+) có thể giới hạn các subtype trực tiếp được phép; nó không thay đổi nguyên tắc cơ bản của `extends`.

**Tự kiểm tra:** Nếu `Combined extends Left, Right` mà hai cha có default method cùng chữ ký, khai báo interface con rỗng có luôn hợp lệ không? Không; cần override hoặc tái khai báo abstract method khi xung đột không được giải quyết bởi quy tắc kế thừa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-declaration-005">Lớp thực thi một hoặc nhiều interface bằng implements</a>

<details>
<summary>Xem chi tiết</summary>

# Lớp thực thi một hoặc nhiều interface bằng implements

## 1. Cú pháp trong khai báo class

Một class dùng `implements` để cam kết thực hiện một hoặc nhiều interface. Nếu class còn kế thừa một superclass, `extends` phải đứng trước `implements`:

```java
interface Printable {
    void print();
}

interface Storable {
    void save();
}

class DocumentBase {
    protected String prefix() {
        return "DOC";
    }
}

final class Report extends DocumentBase implements Printable, Storable {
    @Override
    public void print() {
        System.out.println(prefix() + " printed");
    }

    @Override
    public void save() {
        System.out.println(prefix() + " saved");
    }
}

public class Main {
    public static void main(String[] args) {
        Printable item = new Report();
        item.print();
    }
}
```

Kết quả:

```text
DOC printed
```

Cú pháp đúng là `class Report extends DocumentBase implements Printable, Storable`. Một class chỉ có một superclass trực tiếp, nhưng có thể triển khai nhiều interface. Giữa các interface với nhau, dùng `extends`.

## 2. Trách nhiệm của class concrete

Một class không khai báo `abstract` phải cung cấp implementation hợp lệ cho các abstract method mà nó nhận từ interface và superclass. Không nhất thiết mỗi method phải được viết lại ngay trong class có `implements`: một implementation phù hợp có thể được thừa hưởng từ superclass hoặc từ default method.

Ví dụ, với `interface Validator { boolean valid(String value); }`, phương thức `boolean valid(Object value)` không triển khai `valid(String)` vì khác kiểu tham số. Phương thức `protected boolean valid(String value)` cũng không hợp lệ để thực hiện một phương thức public của interface. Dùng `@Override` giúp compiler phát hiện các lỗi chữ ký phổ biến.

Các quy tắc khác của Java vẫn áp dụng: kiểu trả về phải tương thích, không được giảm quyền truy cập, và implementation không thể thêm checked exception trái với hợp đồng gốc. Nếu class chưa thể hoàn thành hợp đồng, có thể khai báo nó là `abstract` và để lớp con concrete hoàn tất.

## 3. Interface là kiểu cho đa hình

```java
interface Sender {
    void send(String message);
}

final class ConsoleSender implements Sender {
    @Override
    public void send(String message) {
        System.out.println("console: " + message);
    }
}

final class AlertService {
    private final Sender sender;

    AlertService(Sender sender) {
        this.sender = sender;
    }

    void alert(String message) {
        sender.send(message);
    }
}

public class Main {
    public static void main(String[] args) {
        AlertService service = new AlertService(new ConsoleSender());
        service.alert("Order received");
    }
}
```

Kết quả:

```text
console: Order received
```

`AlertService` phụ thuộc vào hợp đồng `Sender`. Có thể truyền vào một implementation khác mà không phải thay đổi logic gọi `alert`. Đây cũng là nền tảng của dependency injection và việc đưa một implementation giả vào test.

Compiler kiểm tra lời gọi theo kiểu khai báo `Sender`; lúc chạy, JVM dispatch `send` đến implementation của đối tượng thực tế. Nếu thường xuyên ép kiểu sang một class cụ thể để gọi các method riêng, hãy xem hợp đồng hiện tại có thiếu hành vi cần thiết hoặc có thể được chia thành interface nhỏ hơn hay không.

## 4. Kế thừa implementation và default method

Nếu `Vehicle implements Movable`, thì `ElectricVehicle extends Vehicle` cũng là `Movable` dù không lặp lại từ khóa `implements Movable`. Lớp con có thể dùng implementation thừa hưởng hoặc override nó.

Các method `default` có thể được dùng mà không cần override. Nếu nhiều interface mang các default method xung đột cùng chữ ký, class phải giải quyết xung đột theo quy tắc kế thừa Java; bài về `extends` trình bày cách dùng `InterfaceName.super.method()` và cách tái khai báo abstract method.

## 5. Lỗi thường gặp

| Sai lầm | Cách đúng |
| --- | --- |
| `class A implements I1 implements I2` | `class A implements I1, I2` |
| `class A implements I extends Base` | `class A extends Base implements I` |
| Concrete class thiếu abstract method | Cài đặt method còn thiếu, thừa hưởng implementation hợp lệ hoặc khai báo class abstract. |
| Implementation giảm mức truy cập | Giữ `public` khi thực hiện phương thức public của interface. |
| Nghĩ biến kiểu interface gọi được mọi method của implementation | Gọi qua các thành viên của kiểu tham chiếu, hoặc thiết kế hợp đồng phù hợp. |
| Tạo interface cho mọi class theo thói quen | Chỉ tạo khi hợp đồng mang lại lợi ích thực tế. |

**Tự kiểm tra:** Nếu `Report` triển khai cả `Printable` và `Storable`, một biến `Printable p = new Report()` có gọi trực tiếp `p.save()` không? Không; `save()` không thuộc kiểu tham chiếu `Printable` dù đối tượng thực tế cũng là `Storable`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-declaration-006">Vì sao interface không có constructor và thuộc tính instance</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao interface không có constructor và thuộc tính instance trong Java?

## 1. Interface công bố hành vi, class sở hữu trạng thái

Interface mô tả những thao tác mà một đối tượng phải cung cấp. Nó không định nghĩa cách dựng một instance cụ thể và không khai báo field instance. Constructor, trạng thái riêng và quy trình khởi tạo thuộc về class hoặc record triển khai.

```java
interface Account {
    String id();
    int balance();
    void deposit(int amount);

    String CURRENCY = "VND";
}

final class Wallet implements Account {
    private final String id;
    private int balance;

    Wallet(String id, int initialBalance) {
        this.id = id;
        this.balance = initialBalance;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int balance() {
        return balance;
    }

    @Override
    public void deposit(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        balance += amount;
    }
}

public class Main {
    public static void main(String[] args) {
        Account first = new Wallet("A", 100);
        Account second = new Wallet("B", 5);
        first.deposit(25);
        System.out.println(first.id() + ": " + first.balance());
        System.out.println(second.id() + ": " + second.balance());
        System.out.println(Account.CURRENCY);
    }
}
```

Kết quả:

```text
A: 125
B: 5
VND
```

`Wallet` có constructor và field instance `id`, `balance`. Hai đối tượng ví giữ trạng thái riêng; việc nạp tiền vào A không làm đổi số dư B. Biến `first` và `second` dùng kiểu interface `Account` ở phía gọi, nhưng đối tượng thực tế vẫn là `Wallet`.

## 2. Vì sao interface không có constructor?

Constructor khởi tạo instance của class cụ thể và tham gia chuỗi gọi `this(...)` / `super(...)` của class. Interface không phải class có thể khởi tạo trực tiếp, nên khai báo constructor trong interface là không hợp lệ:

```java
interface Service {
    Service() {} // Sai cú pháp Java
    void run();
}
```

`new Service()` cũng không hợp lệ. Cú pháp `new Service() { ... }` lại có thể tạo anonymous class triển khai `Service`; constructor thuộc cơ chế tạo lớp ẩn danh, không thuộc interface. Lambda cũng có thể cung cấp implementation cho functional interface.

Abstract class khác ở chỗ nó vẫn là class trong chuỗi kế thừa: dù không thể tạo trực tiếp bằng `new`, nó có thể khai báo constructor và field instance để chia sẻ trạng thái cho các lớp con.

## 3. Vì sao field interface không phải field instance?

Mọi field khai báo trực tiếp trong interface đều ngầm định là `public static final`. Vì vậy:

```java
interface HttpSettings {
    int DEFAULT_PORT = 8080;
}
```

tương đương với khai báo tường minh `public static final int DEFAULT_PORT = 8080;`. Field thuộc về kiểu interface, không phải một bản sao cho từng đối tượng. Field interface phải được khởi tạo và không thể gán lại.

`final` khóa việc gán lại biến tham chiếu hoặc giá trị, nhưng không nhất thiết làm đối tượng được tham chiếu bất biến. Ví dụ, field `public static final List<String> NAMES` vẫn có thể trỏ đến một danh sách mutable; các nơi gọi có thể thay đổi nội dung danh sách. Vì thế không nên dùng field interface để công bố trạng thái mutable dùng chung.

## 4. Phương thức có thân không có nghĩa interface có state

Từ Java 8, interface có thể chứa `default` và `static` method; từ Java 9, có thể chứa `private` helper method. Các method này có thể gọi method khác của hợp đồng, dùng biến cục bộ và xử lý logic. Chúng không tạo field instance cho mỗi đối tượng.

```java
interface Named {
    String name();

    default String displayName() {
        return name().trim();
    }
}
```

`displayName()` lấy dữ liệu qua `name()`, do implementation quyết định. Nếu cần bộ đếm, cache hoặc trạng thái thay đổi riêng theo từng đối tượng, hãy lưu nó trong class triển khai hoặc một thành phần mà class đó sở hữu.

## 5. Chọn đúng cơ chế

| Nhu cầu | Lựa chọn |
| --- | --- |
| Nhiều kiểu khác nhau cùng công bố một hành vi | Interface |
| Mỗi đối tượng cần field riêng và constructor kiểm tra dữ liệu | Class/record triển khai |
| Nhiều subclass cần chung field, constructor và code cơ sở | Có thể dùng abstract class |
| Muốn một method tiện ích gọi qua tên kiểu | Static method phù hợp |
| Muốn chia sẻ logic instance ngắn mà không cần state chung | Có thể dùng default method |

**Tự kiểm tra:** `int count = 0;` khai báo trực tiếp trong interface có thể được tăng bằng `count++` trong default method không? Không; field đó ngầm định `final`. Hãy khai báo trạng thái trong class triển khai và công bố thao tác qua interface.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-declaration-007">Kiểu thành viên được khai báo bên trong interface</a>

<details>
<summary>Xem chi tiết</summary>

# Các kiểu thành viên được khai báo bên trong interface

## 1. Kiểu thành viên là gì?

Interface có thể chứa khai báo kiểu dữ liệu trực tiếp trong thân của nó. Các kiểu đó được gọi là **member types**. Java cho phép nested class, nested interface, enum, record (Java 16+) và annotation interface:

```java
interface Transport {
    interface Driver {
        void drive();
    }

    class Route {
        private final String name;

        Route(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }
    }

    enum Mode {
        ROAD, RAIL
    }

    record Ticket(String code) {}

    @interface Audited {}
}
```

Tên đầy đủ của các kiểu trong ví dụ lần lượt là `Transport.Driver`, `Transport.Route`, `Transport.Mode`, `Transport.Ticket` và `Transport.Audited`. Chúng là kiểu, không phải field hoặc method của `Transport`.

## 2. Modifier ngầm định: public static

Mọi member type khai báo trực tiếp trong interface đều ngầm định `public static`, kể cả khi không viết hai từ khóa đó.

- `static` nghĩa là kiểu thành viên không cần một instance của interface bao ngoài. Không thể tạo instance của `Transport` vì đó là interface, và cũng không cần một instance như vậy để tạo `Transport.Route`.
- `public` là modifier của member type; nhưng nó không vượt qua quyền truy cập của kiểu bao ngoài. Nếu top-level interface bao ngoài là package-private, mã ngoài package vẫn không thể truy cập nó chỉ nhờ member type public.
- Không đặt `private` hoặc `protected` lên member type được khai báo trực tiếp trong interface.

Cách sử dụng:

```java
Transport.Route route = new Transport.Route("North");
Transport.Mode mode = Transport.Mode.RAIL;
Transport.Ticket ticket = new Transport.Ticket("T-1");
```

`Transport.Route` là static nested class và có constructor riêng. Nó không cần cú pháp `outer.new Route(...)`.

## 3. Ví dụ chạy được trên Java 17+

```java
interface Library {
    interface Identified {
        String id();
    }

    class Holder {
        private final String text;

        Holder(String text) {
            this.text = text;
        }

        String text() {
            return text;
        }
    }

    enum Status {
        DRAFT, PUBLISHED
    }

    record Book(String id, String title) implements Identified {}
}

public class Main {
    public static void main(String[] args) {
        Library.Holder holder = new Library.Holder("Notes");
        Library.Book book = new Library.Book("B01", "Java");
        Library.Identified identified = book;
        System.out.println(holder.text());
        System.out.println(identified.id() + " - " + book.title());
        System.out.println(Library.Status.PUBLISHED);
    }
}
```

Kết quả:

```text
Notes
B01 - Java
PUBLISHED
```

`Library.Book` là record nên tự cung cấp accessor `id()` và `title()`. Nó triển khai `Library.Identified` một cách tường minh; việc khai báo bên trong `Library` không tự tạo quan hệ `implements` với interface bao ngoài.

## 4. Phân biệt kiểu thành viên với field và method

```java
interface Sensor {
    int MAX_RETRIES = 3;  // Field: public static final
    int read();           // Method: public abstract

    default boolean ready() {
        return read() >= 0;
    }

    static String category() {
        return "sensor";
    }

    record Reading(int value) {} // Member type: public static
}
```

`MAX_RETRIES` là field hằng; `read`, `ready` và `category` là method; `Reading` là kiểu. Đừng suy ra class thành viên thông thường là final hoặc immutable chỉ vì nó nằm trong interface. Chẳng hạn record là final theo quy tắc của record, còn nested class bình thường có thể có state và method thay đổi.

## 5. Vị trí khai báo không tạo quan hệ kiểu

Một nested class không tự động triển khai interface bao ngoài:

```java
interface Task {
    void execute();

    class Config {}
    class Worker implements Task {
        @Override
        public void execute() {
            System.out.println("working");
        }
    }
}
```

`Task.Config` không phải là `Task`. `Task.Worker` mới là `Task` vì khai báo `implements Task` rõ ràng và cung cấp `execute()`. Tương tự, nested interface chỉ kế thừa interface bao ngoài khi nó khai báo `extends`.

## 6. Khi nào nên lồng một kiểu?

Member type phù hợp khi kiểu đó gắn chặt về ý nghĩa với API bao ngoài, ví dụ `Parser.Result`, `Connection.State` hoặc `EventBus.Listener`. Cách gọi `Outer.Member` giúp người đọc nhận ra nhóm khái niệm liên quan.

Nếu kiểu được dùng độc lập bởi nhiều API hoặc có trách nhiệm riêng lớn, hãy cân nhắc top-level type để tăng khả năng tái sử dụng. Luôn kiểm tra riêng quy tắc truy cập, `extends` và `implements`; vị trí lồng nhau chỉ tổ chức tên và phạm vi, không tự tạo quan hệ kế thừa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-declaration-008">Phân biệt interface thông thường với annotation interface</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt interface thông thường với annotation interface trong Java

## 1. Hai kiểu interface, hai mục đích

`interface` thông thường công bố **hợp đồng hành vi**. Class có thể `implements` nó, biến có thể giữ tham chiếu đến đối tượng triển khai, và chương trình gọi method qua kiểu interface.

`@interface` là cú pháp khai báo một **annotation type** (annotation interface). Nó định nghĩa metadata có cấu trúc để gắn vào class, method, field hoặc vị trí khác; compiler, annotation processor, framework hay code Reflection có thể đọc metadata đó. `@interface` không phải dấu `@` gắn trước một interface đã tồn tại.

| Tiêu chí | Interface thông thường | Annotation interface |
| --- | --- | --- |
| Khai báo | `interface Exporter { ... }` | `@interface ExportInfo { ... }` |
| Mục đích | Hợp đồng hành vi và đa hình | Khuôn mẫu metadata |
| Thành viên chính | Abstract, default, static, private methods; constants | Elements không tham số, có thể có giá trị mặc định |
| Sử dụng | `class C implements Exporter` | `@ExportInfo(owner = "Team")` |
| Cách đọc | Gọi method trên đối tượng | Đọc metadata bằng Reflection hoặc công cụ biên dịch |
| Quan hệ kiểu | Có thể `extends` và được class `implements` | Không khai báo `extends` tùy ý; quan hệ với `java.lang.annotation.Annotation` được quy định bởi Java |

## 2. Annotation elements không phải method thông thường

Ví dụ khai báo annotation:

```java
@interface JobInfo {
    String owner();
    int priority() default 3;
    String[] tags() default {};
}
```

`owner`, `priority` và `tags` là **annotation elements**. Chúng không nhận tham số, không có thân method và kiểu trả về bị giới hạn. Các kiểu hợp lệ gồm primitive, `String`, `Class`, enum, annotation type và mảng một chiều của những kiểu đó. `void`, `Object`, `List<String>`, mảng nhiều chiều và element có tham số đều không hợp lệ.

Element không có giá trị mặc định là bắt buộc khi sử dụng annotation. Element có `default` có thể được bỏ qua. Từ khóa `default` ở đây cung cấp **giá trị metadata mặc định**, không phải code của default method.

```java
@JobInfo(owner = "Team A", tags = {"batch", "demo"})
final class ImportJob {}
```

`priority` nhận giá trị 3 vì annotation không cung cấp giá trị khác. Nếu annotation chỉ có element tên `value`, có thể dùng cú pháp rút gọn như `@Role("admin")` thay cho `@Role(value = "admin")`.

## 3. Target và retention

Hai meta-annotation thường gặp:

- `@Target` quy định những vị trí mà annotation được phép gắn, chẳng hạn `TYPE` cho khai báo kiểu hoặc `METHOD` cho phương thức.
- `@Retention` quy định annotation được giữ đến giai đoạn nào: `SOURCE` chỉ phục vụ mã nguồn/biên dịch; `CLASS` được lưu trong class file; `RUNTIME` cho phép Reflection đọc lúc chạy.

Nếu không khai báo `@Retention`, mặc định là `CLASS`. Muốn đọc annotation lúc runtime, hãy khai báo `@Retention(RetentionPolicy.RUNTIME)`. Annotation có retention SOURCE vẫn có thể được annotation processor đọc trong bước biên dịch.

Các meta-annotation khác gồm `@Documented`, `@Inherited` và `@Repeatable`. `@Inherited` áp dụng cho truy vấn annotation trên superclass theo quy tắc Java; nó không tự truyền annotation qua interface hay method override.

## 4. Ví dụ Java 17: hành vi và metadata cùng tồn tại

```java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

interface Executable {
    void execute();

    default String category() {
        return "task";
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface JobInfo {
    String owner();
    int priority() default 3;
}

@JobInfo(owner = "Team A")
final class ImportJob implements Executable {
    @Override
    public void execute() {
        System.out.println("importing");
    }
}

public class Main {
    public static void main(String[] args) {
        Executable job = new ImportJob();
        job.execute();
        JobInfo info = ImportJob.class.getAnnotation(JobInfo.class);
        System.out.println(info.owner());
        System.out.println(info.priority());
        System.out.println(Executable.class.isAnnotation());
        System.out.println(JobInfo.class.isAnnotation());
    }
}
```

Kết quả:

```text
importing
Team A
3
false
true
```

`ImportJob implements Executable` tạo quan hệ hợp đồng hành vi. `@JobInfo(owner = "Team A")` gắn metadata lên class; nó không tự thêm method `execute`, không làm class implements annotation và không tự thực hiện hành vi nghiệp vụ. Annotation có tác dụng gì phụ thuộc vào compiler, processor, framework hoặc code đọc nó.

## 5. Lỗi thường gặp

- Dùng `@interface` khi muốn mọi implementation cung cấp một method: dùng interface thông thường.
- Khai báo element kiểu `List<String>` hoặc có tham số: annotation elements bị giới hạn kiểu và phải không có tham số.
- Bỏ qua element bắt buộc: cung cấp giá trị khi gắn annotation hoặc đặt default phù hợp.
- Mong annotation tự bảo vệ, validate hay khởi tạo service: cần code hoặc framework chủ động xử lý metadata.
- Quên `RUNTIME` rồi mong `getAnnotation` đọc được metadata: retention mặc định là CLASS.
- Nhầm `@Deprecated interface OldApi` (interface thường được gắn annotation) với `@interface Marker` (khai báo annotation type).

**Tự kiểm tra:** Nếu một class được gắn `@RequiresPermission("write")`, việc đó tự động chặn người dùng không có quyền gọi method không? Không; annotation chỉ cung cấp metadata. Cần cơ chế kiểm tra thực sự để thực thi chính sách.

</details>

- [Quay lại đầu trang](#back-to-top)
