<a id="back-to-top"></a>

# Đa kế thừa

## Menu
- [Bài toán hình thoi trong hệ phân cấp interface](#multiple-inheritance-001)
- [Kế thừa cùng một khai báo abstract qua nhiều đường](#multiple-inheritance-002)
- [Nhiều khai báo abstract tương thích và kiểu trả về chung](#multiple-inheritance-003)
- [Ưu tiên phương thức có thân kế thừa từ lớp cha](#multiple-inheritance-004)
- [Ưu tiên default method của interface cụ thể hơn](#multiple-inheritance-005)
- [Xung đột default method từ hai interface không liên quan](#multiple-inheritance-006)
- [Xung đột giữa default method và abstract method độc lập](#multiple-inheritance-007)
- [Phương thức abstract của lớp cha chặn triển khai default](#multiple-inheritance-008)
- [Giải quyết xung đột bằng ghi đè và chọn lời gọi super hợp lệ](#multiple-inheritance-009)
- [Phân biệt xung đột phương thức, hằng và kiểu thành viên](#multiple-inheritance-010)

## <a id="multiple-inheritance-001">Bài toán hình thoi trong hệ phân cấp interface</a>

<details>
<summary>Xem chi tiết</summary>

# Bài toán hình thoi trong hệ phân cấp interface của Java

Bài toán hình thoi xuất hiện khi một kiểu nhận hợp đồng hoặc hành vi qua nhiều nhánh interface. Java không cho class kế thừa trực tiếp nhiều class, nhưng interface có thể mở rộng nhiều interface và class có thể triển khai nhiều interface.

~~~text
Root -> Left -> Child
Root -> Right -> Child
~~~

| Hệ phân cấp | Kết quả | Lý do |
| --- | --- | --- |
| Hai nhánh cùng kế thừa một abstract method từ gốc | Thường hợp lệ | Một implementation tương thích đáp ứng hợp đồng chung. |
| Hai nhánh cùng kế thừa một default từ gốc, không override | Hợp lệ | Chỉ có một default implementation. |
| Hai interface độc lập cung cấp default cùng chữ ký | Cần giải quyết | Java không tự chọn giữa hai body ngang hàng. |

~~~java
interface Root {
    void run();
    default String label() { return "root"; }
}
interface Read extends Root {}
interface Write extends Root {}
interface ReadWrite extends Read, Write {}

final class Worker implements ReadWrite {
    @Override
    public void run() {
        System.out.println("run once");
    }
}
public class Main {
    public static void main(String[] args) {
        Worker worker = new Worker();
        Root root = worker;
        ReadWrite combined = worker;
        root.run();
        combined.run();
        System.out.println(combined.label());
    }
}
~~~

Đầu ra:

~~~text
run once
run once
root
~~~

Worker.run() là một implementation được gọi hai lần qua hai kiểu tham chiếu. label() được kế thừa từ Root qua cả hai nhánh mà không tạo xung đột.

Khi phân tích, tìm nơi khai báo thực tế của từng method; đối chiếu tên, tham số, return type, visibility và throws; kiểm tra superclass; sau đó quyết định có cần override ở class hoặc interface con hay không.

Field trong interface là hằng public static final; interface không có instance field để sao chép qua các nhánh.

## Tự kiểm tra

Hai nhánh cùng kế thừa một abstract method cần mấy implementation? Hai nhánh cùng kế thừa một default từ gốc có tự xung đột không? Hai interface độc lập có default cùng chữ ký cần làm gì?

**Đáp án:** Một; không; cần override hoặc khai báo giải quyết phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multiple-inheritance-002">Kế thừa cùng một khai báo abstract qua nhiều đường</a>

<details>
<summary>Xem chi tiết</summary>

# Kế thừa cùng một khai báo abstract qua nhiều đường trong Java

Hai interface trung gian có thể cùng kế thừa một khai báo abstract từ interface gốc. Hai đường truyền hợp đồng không tạo ra hai nghĩa vụ riêng.

~~~java
interface Task { void run(); }
interface Read extends Task {}
interface Write extends Task {}
interface ReadWrite extends Read, Write {}

final class Worker implements ReadWrite {
    @Override
    public void run() {
        System.out.println("running");
    }
}
~~~

Task.run() là một khai báo abstract được kế thừa qua cả Read và Write. Một method public void run() là đủ để đáp ứng hợp đồng.

## Hai khai báo độc lập cũng có thể tương thích

~~~java
interface Printable { void print(); }
interface Exportable { void print(); }

final class Report implements Printable, Exportable {
    @Override
    public void print() {
        System.out.println("report");
    }
}
~~~

Printable.print() và Exportable.print() là hai khai báo khác nhau, nhưng một method đáp ứng cả hai vì chữ ký tương thích.

| Tình huống | Kết quả |
| --- | --- |
| Cùng khai báo abstract qua hai nhánh | Một implementation |
| Hai khai báo abstract độc lập cùng chữ ký tương thích | Một implementation |
| Cùng tên nhưng tham số khác nhau | Hai overload, xét riêng |
| Cùng chữ ký nhưng return type không tương thích | Lỗi hợp đồng kiểu |

Method abstract thông thường của interface là public. Với các throws khác nhau, implementation không được khai báo checked exception rộng hơn điều mọi hợp đồng cho phép. Với generic, kiểm tra type argument qua từng nhánh; không kế thừa tùy tiện cả Source&lt;String&gt; và Source&lt;Integer&gt;.

Không viết hai method giống nhau trong một class để “phục vụ” từng interface. Đổi tham số để tạo overload cũng không triển khai method abstract ban đầu. Dùng @Override để compiler phát hiện sai chữ ký.

## Tự kiểm tra

Hai interface khai báo void execute() cần mấy method? Hai khai báo execute(String) và execute(int) có thể hợp nhất không? Vì sao implementation cần public?

**Đáp án:** Một; không, đây là hai overload; vì method abstract của interface là public.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multiple-inheritance-003">Nhiều khai báo abstract tương thích và kiểu trả về chung</a>

<details>
<summary>Xem chi tiết</summary>

# Nhiều khai báo abstract tương thích và kiểu trả về chung trong Java

Một class có thể triển khai nhiều interface bằng một method khi các chữ ký tương thích và return type đáp ứng mọi hợp đồng. Tên giống nhau chưa đủ; cần xét cả tham số lẫn kiểu trả về.

## Covariant return type

~~~java
interface ObjectValue { Object value(); }
interface StringValue { String value(); }

final class TextValue implements ObjectValue, StringValue {
    @Override
    public String value() { return "Java"; }
}

public class Main {
    public static void main(String[] args) {
        TextValue value = new TextValue();
        Object a = ((ObjectValue) value).value();
        String b = ((StringValue) value).value();
        System.out.println(a);
        System.out.println(b);
        System.out.println(b.length());
    }
}
~~~

Đầu ra:

~~~text
Java
Java
4
~~~

String là subtype của Object, nên một String value() đáp ứng cả hai hợp đồng. Kiểu tham chiếu quyết định kiểu kết quả mà compiler cho phép sử dụng.

## Khi nào không tương thích?

~~~java
interface StringSource { String value(); }
interface IntegerSource { Integer value(); }
// Cố ý không hợp lệ:
// class InvalidSource implements StringSource, IntegerSource {}
~~~

String và Integer không có quan hệ subtype phù hợp để một method cùng chữ ký đáp ứng cả hai. Không thể overload bằng cách chỉ đổi return type.

Covariant return áp dụng cho kiểu tham chiếu, không cho phép thay int bằng long. Chuyển đổi primitive trong biểu thức không làm hai khai báo tương thích để override.

Trước khi gộp method, kiểm tra chữ ký, return type, type argument và ý nghĩa nghiệp vụ. Number amount() và Integer amount() có thể dùng chung Integer amount(); implementation trả Object không đáp ứng hợp đồng yêu cầu String.

## Tự kiểm tra

Number get() và Integer get() có thể dùng return type nào? String get() và Integer get() có thể overload không? int và long có covariant return không?

**Đáp án:** Integer; không; không.

Tham khảo: [JLS §8.4.5 — Method Result](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.5).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multiple-inheritance-004">Ưu tiên phương thức có thân kế thừa từ lớp cha</a>

<details>
<summary>Xem chi tiết</summary>

# Ưu tiên phương thức có thân kế thừa từ lớp cha trong Java

Nếu superclass cung cấp một instance method concrete phù hợp với default method của interface, method từ class được ưu tiên. Quy tắc này thường được gọi là **class wins**.

~~~java
class Speaker {
    public String describe() { return "class"; }
}
interface Audible {
    default String describe() { return "interface"; }
    default String channel() { return "default channel"; }
}
final class SmartSpeaker extends Speaker implements Audible {}

public class Main {
    public static void main(String[] args) {
        SmartSpeaker speaker = new SmartSpeaker();
        Audible view = speaker;
        System.out.println(speaker.describe());
        System.out.println(view.describe());
        System.out.println(speaker.channel());
    }
}
~~~

Đầu ra:

~~~text
class
class
default channel
~~~

Speaker.describe() thắng Audible.describe(). Vì superclass không có channel(), class nhận default của interface. Kiểu tham chiếu Audible không ép lời gọi instance dùng default khi class đã có method phù hợp.

Không phải method bất kỳ cùng tên đều đủ. Kiểm tra chữ ký, return type, visibility và checked exceptions. Method protected không tự đáp ứng hợp đồng interface yêu cầu public; method static hoặc private cũng không phải instance implementation tương ứng.

Một superclass có method abstract tạo tình huống khác: concrete subclass vẫn phải hoàn thiện nghĩa vụ abstract. Nếu cần dùng default, nó có thể override tường minh rồi gọi Status.super.status() khi Status là direct superinterface phù hợp.

## Tự kiểm tra

Superclass có public String name() và interface có default String name(): body nào chạy? Superclass không có method tương ứng nhưng interface có default thì sao? Method protected có đáp ứng public không?

**Đáp án:** superclass; có thể dùng default; không.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multiple-inheritance-005">Ưu tiên default method của interface cụ thể hơn</a>

<details>
<summary>Xem chi tiết</summary>

# Ưu tiên default method của interface cụ thể hơn trong Java

Khi interface con override default method từ interface cha, khai báo ở interface con cụ thể hơn được ưu tiên. “Cụ thể hơn” phụ thuộc vào quan hệ extends, không phụ thuộc thứ tự viết tên trong implements.

~~~java
interface BaseFormatter {
    default String format() { return "base"; }
}
interface DetailedFormatter extends BaseFormatter {
    @Override
    default String format() { return "detailed"; }
}
final class Report implements DetailedFormatter {}

public class Main {
    public static void main(String[] args) {
        Report report = new Report();
        BaseFormatter view = report;
        System.out.println(report.format());
        System.out.println(view.format());
    }
}
~~~

Đầu ra là detailed hai lần. Cả hai lời gọi thông thường dùng default cụ thể hơn, kể cả khi biến tham chiếu có kiểu cha.

## Hình thoi có một nhánh cụ thể hơn

~~~java
interface Root { default String message() { return "root"; } }
interface Left extends Root {}
interface Right extends Root {
    @Override
    default String message() { return "right"; }
}
final class Result implements Left, Right {}
~~~

Result nhận default từ Right: nhánh Right override default gốc, còn Left chỉ kế thừa nó. Nếu Left và Right tự khai báo default riêng mà không có quan hệ subtype, xem bài về xung đột giữa hai default không liên quan.

Interface con cũng có thể override default bằng khai báo abstract để buộc class concrete tự cung cấp body.

## Ghi nhớ

- Quan hệ extends xác định interface nào cụ thể hơn.
- Đảo thứ tự implements A, B thành implements B, A không đổi kết quả.
- Hai default độc lập cần được giải quyết tường minh.
- Method concrete từ superclass có quy tắc ưu tiên riêng.
- InterfaceName.super.method() có giới hạn; không dùng để bỏ qua tùy ý một interface con cụ thể hơn.

## Tự kiểm tra

Child extends Parent và override default run(). Lớp chỉ implements Child dùng default nào? Nếu interface con khai báo lại run() abstract thì class concrete cần làm gì?

**Đáp án:** default của Child; class concrete phải cung cấp implementation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multiple-inheritance-006">Xung đột default method từ hai interface không liên quan</a>

<details>
<summary>Xem chi tiết</summary>

# Xung đột default method từ hai interface không liên quan trong Java

Khi hai interface không có quan hệ kế thừa ưu tiên cùng cung cấp default method có cùng chữ ký, Java không tự chọn một body. Class hoặc interface con kết hợp phải xác định hành vi.

~~~java
interface AuditLog {
    default String format(String event) {
        return "AUDIT:" + event;
    }
}
interface ConsoleLog {
    default String format(String event) {
        return "CONSOLE:" + event;
    }
}
final class UnifiedLog implements AuditLog, ConsoleLog {
    @Override
    public String format(String event) {
        return AuditLog.super.format(event)
                + " | " + ConsoleLog.super.format(event);
    }
}
public class Main {
    public static void main(String[] args) {
        System.out.println(new UnifiedLog().format("LOGIN"));
    }
}
~~~

Đầu ra:

~~~text
AUDIT:LOGIN | CONSOLE:LOGIN
~~~

UnifiedLog.format() là override public duy nhất. Các lời gọi AuditLog.super.format() và ConsoleLog.super.format() chọn rõ default từ direct superinterface phù hợp.

## Ba chiến lược

- **Chọn một default** nếu một nguồn đáp ứng đúng hợp đồng.
- **Kết hợp hai default** nếu chúng bổ trợ nhau và side effect không bị lặp.
- **Viết body mới** nếu cả hai default đều không phù hợp.

Không gọi cả hai chỉ để hết lỗi biên dịch; chúng có thể ghi log, phát sự kiện hoặc cập nhật trạng thái hai lần.

## Xung đột tại interface con

~~~java
interface Left {
    default String name() { return "left"; }
}
interface Right {
    default String name() { return "right"; }
}
interface Combined extends Left, Right {
    @Override
    default String name() {
        return Left.super.name() + "/" + Right.super.name();
    }
}
~~~

Combined cung cấp default mới, nên class implement Combined có thể kế thừa body này. Một lựa chọn khác là khai báo name() abstract để đẩy trách nhiệm xuống class concrete.

Đừng nhầm với hai nhánh cùng kế thừa một default duy nhất từ tổ tiên, hoặc hai abstract method tương thích. Hai method cùng tên nhưng khác tham số là overload. Nếu return type không tương thích, override không sửa được hợp đồng kiểu.

Tham khảo: [JLS §9.4.1.3 — Inheriting Methods with Override-Equivalent Signatures](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.4.1.3).

## Tự kiểm tra

Hai interface độc lập có default name(); class không override thì sao? Vì sao Java không so sánh body để chọn một? Khi nào InterfaceName.super.method() hữu ích?

**Đáp án:** lỗi biên dịch; quy tắc dựa vào kiểu và kế thừa; khi override cần gọi default phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multiple-inheritance-007">Xung đột giữa default method và abstract method độc lập</a>

<details>
<summary>Xem chi tiết</summary>

# Xung đột giữa default method và abstract method độc lập trong Java

Một default method có body không tự động thỏa một khai báo abstract override-equivalent từ interface độc lập. Cả class kết hợp lẫn interface con kết hợp hai nguồn này cần giải quyết rõ ràng.

## Class kết hợp hai interface

~~~java
interface DefaultTask {
    default String run() { return "default"; }
}
interface RequiredTask {
    String run();
}
// Không hợp lệ nếu không override:
// class BrokenTask implements DefaultTask, RequiredTask {}

final class Task implements DefaultTask, RequiredTask {
    @Override
    public String run() {
        return DefaultTask.super.run() + " + verified";
    }
}
public class Main {
    public static void main(String[] args) {
        System.out.println(new Task().run());
    }
}
~~~

Đầu ra:

~~~text
default + verified
~~~

Task.run() cung cấp implementation rõ ràng và delegate có chủ đích tới default. Nếu bỏ override, compiler báo class chưa triển khai method abstract của RequiredTask.

## Interface con kết hợp hai interface

~~~java
interface DefaultTask {
    default String run() { return "default"; }
}
interface RequiredTask {
    String run();
}
interface Combined extends DefaultTask, RequiredTask {
    @Override
    default String run() {
        return DefaultTask.super.run() + " via Combined";
    }
}
final class Worker implements Combined {}
~~~

Combined giải quyết xung đột ở cấp interface. Nếu nó khai báo run() abstract thay vì default, class concrete phải tự triển khai.

| Nguồn method | Kết quả |
| --- | --- |
| Hai abstract method độc lập, tương thích | Một implementation có thể đáp ứng cả hai |
| Một default và một abstract từ interface độc lập | Cần khai báo giải quyết rõ ràng |
| Hai default độc lập | Cần giải quyết xung đột default |
| Interface con override method của interface cha | Áp dụng quy tắc override/specificity |
| Superclass có abstract method | Concrete subclass vẫn phải hoàn thiện nghĩa vụ class |

Đừng suy luận chỉ từ việc một method có body. Java xét chữ ký và quan hệ giữa các nguồn; nó không quyết định dựa trên ngữ nghĩa nghiệp vụ của default.

Tham khảo: [JLS §9.4.1.3](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.4.1.3) và [JLS §8.4.8.4](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.4).

## Tự kiểm tra

Vì sao interface con không thể để trống khi kế thừa default và abstract độc lập? Cách nào tái sử dụng default có chủ đích? Hai abstract method tương thích có luôn gây lỗi?

**Đáp án:** phải redeclare để giải quyết; override rồi gọi DefaultTask.super.run(); không.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multiple-inheritance-008">Phương thức abstract của lớp cha chặn triển khai default</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức abstract của lớp cha chặn triển khai default trong Java

Một abstract method được kế thừa từ superclass vẫn là nghĩa vụ của class con. Default method cùng chữ ký ở interface không tự hoàn thiện nghĩa vụ đó; concrete subclass cần implementation hợp lệ.

~~~java
interface Greeter {
    default String greet() { return "hello from interface"; }
}
abstract class Base {
    public abstract String greet();
}
// Concrete class này không hợp lệ nếu không override:
// class Broken extends Base implements Greeter {}

final class Person extends Base implements Greeter {
    @Override
    public String greet() {
        return "hello from Person";
    }
}
public class Main {
    public static void main(String[] args) {
        Base base = new Person();
        Greeter greeter = (Greeter) base;
        System.out.println(base.greet());
        System.out.println(greeter.greet());
    }
}
~~~

Đầu ra:

~~~text
hello from Person
hello from Person
~~~

Person.greet() là implementation thực sự; nếu bỏ nó mà vẫn để Person concrete, Base.greet() còn abstract.

## Muốn dùng default thì gọi có chủ đích

~~~java
@Override
public String greet() {
    return "custom: " + Greeter.super.greet();
}
~~~

Đây là body thay thế cho method trong class, không phải khai báo bổ sung. Class vẫn cung cấp implementation của mình; Greeter.super.greet() chỉ tái sử dụng default trong ngữ cảnh direct superinterface hợp lệ.

## So sánh với superclass concrete

Nếu superclass có public String greet() với body phù hợp, class con có thể kế thừa method đó; method class được ưu tiên hơn default. Khác biệt là concrete method đã cung cấp body, còn abstract method giữ nghĩa vụ cho subclass.

Abstract class cũng có thể cố ý tái khai báo default thành abstract:

~~~java
abstract class StrictGreeter implements Greeter {
    @Override
    public abstract String greet();
}
~~~

Concrete subclass của StrictGreeter phải cài đặt greet(). Đây là lựa chọn thiết kế để buộc nhánh class cung cấp hành vi riêng.

Tham khảo: [JLS §8.4.8.4](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.4).

## Tự kiểm tra

Superclass có public abstract String status(); interface có default status(). Concrete subclass có thể bỏ qua không? Nếu superclass có method concrete tương thích thì sao? Có thể gọi default từ override không?

**Đáp án:** không; method concrete của superclass được ưu tiên; có, bằng qualified super call hợp lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multiple-inheritance-009">Giải quyết xung đột bằng ghi đè và chọn lời gọi super hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Giải quyết xung đột bằng ghi đè và chọn lời gọi super hợp lệ

Khi cần giải quyết xung đột default, trước tiên hãy khai báo một override phù hợp. Sau đó chọn một body mới, gọi một default cụ thể hoặc kết hợp nhiều default nếu hợp đồng yêu cầu.

## Hai cú pháp khác nhau

- super.method() gọi method tương ứng từ superclass.
- InterfaceName.super.method() gọi default từ direct superinterface thích hợp trong ngữ cảnh instance hợp lệ.

~~~java
interface Root {
    default String id() { return "root"; }
}
interface Specific extends Root {
    @Override
    default String id() { return "specific"; }
}
final class Item implements Root, Specific {
    @Override
    public String id() {
        return Specific.super.id();
    }
}
public class Main {
    public static void main(String[] args) {
        System.out.println(new Item().id());
    }
}
~~~

Đầu ra:

~~~text
specific
~~~

Specific là direct superinterface và cung cấp default cụ thể hơn. Không thể dùng Root.super.id() để bỏ qua Specific; qualified-super call chịu quy tắc về superinterface trực tiếp và specificity.

| Biểu thức | Ý nghĩa |
| --- | --- |
| super.label() | Method từ superclass |
| Left.super.label() | Default từ direct superinterface Left, nếu hợp lệ |
| Left.label() | Gọi static method nếu Left khai báo static; không gọi default |
| ((Left) this).label() | Lời gọi instance đa hình, không ép chạy default của Left |

Không dùng InterfaceName.super cho method abstract, static hoặc private không truy cập được. Đây không phải cơ chế phản chiếu để gọi tùy ý tổ tiên.

## Chọn hành vi theo hợp đồng

1. Chọn một default nếu nó đáp ứng đúng hợp đồng.
2. Kết hợp hai default chỉ khi chúng bổ trợ nhau và side effect không bị lặp.
3. Viết body mới nếu cần chính sách riêng.
4. Tái khai báo abstract ở interface con nếu muốn từng class tự quyết định.

Khi điều tra, kiểm tra chữ ký, return type, visibility, checked exceptions, superclass và quan hệ extends. Dùng @Override, biên dịch và kiểm thử qua tham chiếu class lẫn interface.

## Tự kiểm tra

Vì sao ép kiểu ((Left) this) không giống Left.super? Khi nào nên dùng super.method()? Vì sao không nên luôn gọi cả hai default?

**Đáp án:** ép kiểu vẫn dispatch đa hình; super.method() dành cho superclass; hai default có thể có side effect hoặc ngữ nghĩa không tương thích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multiple-inheritance-010">Phân biệt xung đột phương thức, hằng và kiểu thành viên</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt xung đột phương thức, hằng và kiểu thành viên trong Java interface

Cùng một tên trong nhiều interface có thể thuộc các thành viên khác loại. Quy tắc giải quyết method không áp dụng nguyên xi cho static method, field hoặc member type.

| Thành viên | Đặc điểm | Cách định danh |
| --- | --- | --- |
| Abstract method | Hợp đồng chưa có body | Một implementation có thể đáp ứng nhiều hợp đồng tương thích |
| Default method | Instance method có body | Kiểm tra specificity và xung đột |
| Static method | Thuộc interface khai báo nó | JsonCodec.format() |
| Field | Ngầm định public static final | SmallLimits.LIMIT |
| Member type | Kiểu lồng ngầm định public static | LocalOptions.Policy |

## Static method

~~~java
interface JsonCodec {
    static String format() { return "JSON"; }
}
interface XmlCodec {
    static String format() { return "XML"; }
}
class Codec implements JsonCodec, XmlCodec {}

public class StaticMethodsDemo {
    public static void main(String[] args) {
        System.out.println(JsonCodec.format());
        System.out.println(XmlCodec.format());
    }
}
~~~

Kết quả:

~~~text
JSON
XML
~~~

Codec không kế thừa static method như instance method và không cần override chúng.

## Hằng trùng tên

~~~java
interface SmallLimits { int LIMIT = 10; }
interface LargeLimits { int LIMIT = 100; }
class Config implements SmallLimits, LargeLimits {
    int smallLimit() { return SmallLimits.LIMIT; }
    int largeLimit() { return LargeLimits.LIMIT; }
}
~~~

SmallLimits.LIMIT và LargeLimits.LIMIT là hai field tĩnh riêng. Field không được override theo đa hình; dùng tên đầy đủ giúp chỉ rõ nguồn giá trị.

Field của class cũng được tra cứu tĩnh theo kiểu tham chiếu:

~~~java
class Parent { String label = "parent"; }
class Child extends Parent { String label = "child"; }
public class FieldHidingDemo {
    public static void main(String[] args) {
        Parent value = new Child();
        System.out.println(value.label);
        System.out.println(((Child) value).label);
    }
}
~~~

Kết quả là parent rồi child: đây là field hiding, không phải method overriding.

## Member type cùng tên

~~~java
interface LocalOptions { class Policy {} }
interface RemoteOptions { class Policy {} }
class OptionReader implements LocalOptions, RemoteOptions {
    LocalOptions.Policy local = new LocalOptions.Policy();
    RemoteOptions.Policy remote = new RemoteOptions.Policy();
}
~~~

LocalOptions.Policy và RemoteOptions.Policy là hai kiểu khác nhau. Kiểu lồng không thể override hoặc hợp nhất hai kiểu đó; dùng tên được qualify rõ ràng.

## Cách phân tích

1. Xác định thành viên là method, static method, field hay type.
2. Với method, xét chữ ký, body, override và specificity.
3. Với static method, gọi qua interface sở hữu method.
4. Với field/type trùng tên, dùng tên qualify.
5. Không suy ra ưu tiên từ thứ tự khai báo interface.

## Tự kiểm tra

Hai abstract String id() tương thích cần mấy implementation? Hai default String send() độc lập cần làm gì? Hai field LIMIT trùng tên nên được gọi thế nào? LocalOptions.Policy và RemoteOptions.Policy có phải cùng kiểu không?

**Đáp án:** một; override để giải quyết; qualify bằng tên interface; không.


</details>

- [Quay lại đầu trang](#back-to-top)
