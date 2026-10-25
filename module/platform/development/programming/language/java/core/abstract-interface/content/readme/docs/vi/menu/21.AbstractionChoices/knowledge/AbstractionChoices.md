<a id="back-to-top"></a>

# Lựa chọn trừu tượng

## Menu
- [So sánh abstract class và interface theo mục tiêu thiết kế](#abstraction-choices-001)
- [Chọn interface cho vai trò chung của các lớp không cùng họ](#abstraction-choices-002)
- [Chọn abstract class khi cần trạng thái và quy trình dùng chung](#abstraction-choices-003)
- [Kết hợp interface công khai với lớp trừu tượng hỗ trợ tùy chọn](#abstraction-choices-004)
- [Đánh đổi giữa kế thừa triển khai và composition, delegation](#abstraction-choices-005)
- [Nguyên tắc thay thế Liskov và các bất biến của hợp đồng](#abstraction-choices-006)
- [Tách interface theo nhu cầu sử dụng và tránh interface quá lớn](#abstraction-choices-007)
- [Đảo ngược phụ thuộc bằng tham số hoặc constructor](#abstraction-choices-008)
- [Tránh tạo interface cho mọi lớp khi chưa có nhu cầu](#abstraction-choices-009)
- [Tránh constant interface và cây kế thừa chỉ để tái sử dụng mã](#abstraction-choices-010)

## <a id="abstraction-choices-001">So sánh abstract class và interface theo mục tiêu thiết kế</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh abstract class và interface theo mục tiêu thiết kế

Trong Java, `abstract class` và `interface` đều giúp mô tả những hành vi mà một lớp cụ thể phải cung cấp. Điểm khác nhau cốt lõi nằm ở mục tiêu thiết kế: **abstract class** phù hợp khi các lớp thuộc cùng một họ cần chia sẻ trạng thái, quy tắc và phần triển khai; **interface** phù hợp khi muốn định nghĩa một vai trò hoặc khả năng mà nhiều lớp khác nhau có thể cùng đảm nhận.

Các ví dụ dưới đây dùng Java 17 trở lên.

## 1. So sánh nhanh

| Tiêu chí | Abstract class | Interface |
|---|---|---|
| Mục tiêu chính | Chia sẻ nền tảng triển khai giữa các lớp có quan hệ cùng họ | Mô tả hợp đồng hoặc khả năng mà lớp có thể đảm nhận |
| Kế thừa/triển khai | Một lớp chỉ có thể `extends` một lớp trực tiếp | Một lớp có thể `implements` nhiều interface |
| Trạng thái đối tượng | Có thể có field instance mutable hoặc immutable | Không có field instance; field được khai báo trong interface là hằng `public static final` |
| Khởi tạo | Có constructor để thiết lập trạng thái chung; không thể `new` trực tiếp abstract class | Không có constructor và không thể khởi tạo interface trực tiếp |
| Phương thức | Có thể có phương thức concrete, abstract, final, protected và các mức truy cập lớp cho phép | Có phương thức abstract, `default`, `static`, và `private` (private method trong interface từ Java 9) |
| Mức truy cập | Phương thức có thể là public, protected, package-private hoặc private tùy mục đích | Hợp đồng được công khai qua phương thức public; phương thức private chỉ là helper bên trong interface |
| Thay đổi hợp đồng | Có thể thêm logic chung, trạng thái hoặc hook; thay đổi vẫn cần đánh giá ảnh hưởng đến lớp con | Có thể thêm default method để mở rộng hành vi, nhưng cần xem xét xung đột và thay đổi ngữ nghĩa |
| Khi phù hợp | Template method, trạng thái và invariant chung, khung xử lý có phần bắt buộc ghi đè | Vai trò độc lập, capability, API mở cho nhiều kiểu triển khai, kết hợp nhiều hợp đồng |

Không nên chọn chỉ dựa vào việc có hay không có phương thức đã triển khai. Interface hiện đại có thể chứa `default` và `static` method; abstract class có thể không chứa trạng thái. Hãy bắt đầu từ quan hệ và trách nhiệm mà mô hình cần thể hiện.

## 2. Chọn abstract class khi cần nền tảng chung

Một abstract class hữu ích khi các lớp con thực sự thuộc cùng một họ và cần chia sẻ dữ liệu hoặc quy tắc không nên lặp lại. Nó có thể:

- Giữ trạng thái đối tượng bằng field instance.
- Khởi tạo và xác thực trạng thái qua constructor.
- Cung cấp phương thức dùng chung để các lớp con không tự triển khai lại.
- Khai báo abstract method làm điểm mở rộng bắt buộc.
- Đóng một phần quy tắc bằng phương thức `final`, đồng thời cho phép lớp con tùy biến các bước được thiết kế để mở rộng.

Ví dụ, các loại tài liệu có cùng mã định danh, quy tắc kiểm tra và vòng đời có thể kế thừa một lớp nền `Document`. Lớp nền giữ mã định danh và xác thực dữ liệu một lần; từng loại tài liệu tự định nghĩa cách kết xuất nội dung.

Mẫu này thường hợp lý khi quan hệ là “là một loại của” và các lớp cần kế thừa một phần triển khai thực chất. Đừng tạo abstract class chỉ để gom những phương thức tiện ích không có quan hệ mô hình rõ ràng.

### Template method

Abstract class cũng có thể giữ thứ tự các bước của một thuật toán, trong khi giao việc cụ thể cho các phương thức abstract hoặc protected hook. Nếu thứ tự đó là invariant, một phương thức điều phối có thể là `final` để lớp con không vô tình phá vỡ quy trình. Chỉ dùng cách này khi các bước chung thực sự thuộc về một quy trình ổn định.

## 3. Chọn interface khi cần mô tả vai trò hoặc capability

Interface phù hợp khi người gọi chỉ cần biết một đối tượng **có thể làm gì**, không cần biết đối tượng được xây dựng ra sao hoặc nó thuộc cây kế thừa nào.

Ví dụ, `Comparable<T>`, `Runnable` và `AutoCloseable` mô tả những hợp đồng khác nhau. Các lớp không cùng một lớp cha vẫn có thể thực hiện cùng một interface. Một lớp có thể đồng thời đảm nhận nhiều vai trò, chẳng hạn vừa có tên, vừa có thể xuất dữ liệu.

Ưu tiên interface khi:

- Muốn API nhận nhiều cách triển khai khác nhau mà không phụ thuộc lớp cụ thể.
- Muốn kết hợp nhiều capability trên cùng một lớp.
- Muốn dễ thay thế implementation trong kiểm thử hoặc cấu hình ứng dụng.
- Muốn xác định hợp đồng ở ranh giới giữa các module hoặc thành phần.
- Không cần lưu trạng thái instance chung hay ép mọi implementation dùng chung một thuật toán nền.

Một interface có thể cung cấp `default` method khi có hành vi mặc định hợp lý cho tất cả hoặc phần lớn implementation. Tuy nhiên, `default` method không thay thế được trạng thái instance, constructor hay mọi nhu cầu của một lớp nền.

## 4. Những giới hạn và quy tắc cần nhớ

**Java chỉ cho một lớp kế thừa trực tiếp một lớp khác.** Vì vậy, nếu lớp đã `extends` một abstract class, nó không thể thêm một lớp cha thứ hai để lấy code dùng chung. Nó vẫn có thể `implements` nhiều interface.

**Một interface không thể `extends` class.** Interface có thể `extends` một hoặc nhiều interface khác; một lớp có thể `extends` một lớp và `implements` nhiều interface.

**Field của interface là hằng.** Field khai báo trong interface ngầm định là `public static final`. Không dùng interface để lưu trạng thái riêng của từng đối tượng. Nếu các implementation cần trạng thái riêng, hãy để trạng thái đó trong lớp hoặc một đối tượng cộng tác.

**Abstract class có thể chứa constructor nhưng không thể được khởi tạo trực tiếp.** Constructor của nó chạy khi lớp con được khởi tạo và giúp bảo đảm trạng thái chung hợp lệ. Lớp con concrete phải triển khai các abstract method còn thiếu.

**Interface method không phải lúc nào cũng abstract.** Interface có thể khai báo abstract method, `default` method, `static` method và private helper method. Tuy vậy, các phương thức hợp đồng public vẫn cần được triển khai phù hợp bởi lớp cụ thể.

**Default method có thể phát sinh xung đột.** Nếu một lớp kế thừa hai interface không liên quan có cùng một default method, lớp thường phải override để quyết định hành vi. Phương thức instance phù hợp trong lớp cha cũng có độ ưu tiên cao hơn default method của interface. Vì vậy, việc thêm default method vào một API công khai cần xem xét các implementation hiện có.

**Abstract class và interface không loại trừ lẫn nhau.** Abstract class có thể `implements` một hoặc nhiều interface. Đây thường là thiết kế tốt: interface thể hiện hợp đồng, còn lớp trừu tượng cung cấp một implementation nền cho những lớp muốn tái sử dụng.

## 5. Ví dụ Java hoàn chỉnh

Trong ví dụ này, `Named` mô tả capability đặt tên, `Exportable` mô tả khả năng xuất dữ liệu, còn abstract class `Task` giữ trạng thái và quy tắc chung của một tác vụ. `BuildTask` kế thừa phần nền từ `Task` và đồng thời triển khai `Exportable`.

Lưu thành `DesignChoiceDemo.java`:

~~~java
interface Named {
    String name();

    default String label() {
        return name().trim();
    }
}

interface Exportable {
    String export();
}

abstract class Task implements Named {
    private final String name;

    protected Task(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name.trim();
    }

    @Override
    public final String name() {
        return name;
    }

    public abstract String execute();
}

final class BuildTask extends Task implements Exportable {
    private final int steps;

    BuildTask(String name, int steps) {
        super(name);
        if (steps < 0) {
            throw new IllegalArgumentException("steps must be non-negative");
        }
        this.steps = steps;
    }

    @Override
    public String execute() {
        return "Built " + name() + " in " + steps + " steps";
    }

    @Override
    public String export() {
        return name() + "," + steps;
    }
}

public class DesignChoiceDemo {
    public static void main(String[] args) {
        Task task = new BuildTask("Java", 3);

        System.out.println(task.label());
        System.out.println(task.execute());

        Exportable exportable = (Exportable) task;
        System.out.println(exportable.export());
    }
}
~~~

Biên dịch và chạy:

~~~text
javac DesignChoiceDemo.java
java DesignChoiceDemo
~~~

Stdout chính xác:

~~~text
Java
Built Java in 3 steps
Java,3
~~~

Ví dụ cho thấy mỗi kiểu chịu trách nhiệm khác nhau:

- `Task` giữ trạng thái `name`, kiểm tra dữ liệu đầu vào, cung cấp `name()` và buộc lớp con triển khai `execute()`.
- `Named` mô tả hợp đồng đặt tên và cung cấp cách tạo nhãn mặc định mà không lưu trạng thái riêng.
- `Exportable` cho phép bên gọi xuất dữ liệu mà không cần biết lớp cụ thể là `BuildTask`.
- `BuildTask` kết hợp một lớp nền với một capability bổ sung. Bên gọi có thể làm việc qua kiểu `Task` hoặc `Exportable` tùy mục tiêu.

Phép ép kiểu trong ví dụ an toàn vì đối tượng thực tế là `BuildTask`, lớp này implements `Exportable`. Trong code ứng dụng, nếu không biết chắc đối tượng có capability đó, hãy nhận một tham chiếu `Exportable` từ nơi tạo đối tượng hoặc kiểm tra kiểu trước khi ép kiểu.

## 6. Các lựa chọn thiết kế thường gặp

| Nhu cầu | Lựa chọn thường phù hợp | Lý do |
|---|---|---|
| Nhiều implementation không cùng cây kế thừa nhưng cần cùng một hợp đồng | Interface | Không buộc chúng có chung lớp cha |
| Các lớp con cần cùng trạng thái và constructor để bảo đảm invariant | Abstract class | Đặt trạng thái và quy tắc chung tại một nơi |
| Một lớp cần đảm nhận nhiều vai trò độc lập | Nhiều interface | Java cho phép một lớp triển khai nhiều interface |
| Muốn chia sẻ một thuật toán khung với một vài bước tùy biến | Abstract class | Có thể giữ thứ tự xử lý và định nghĩa hook |
| Chỉ muốn định nghĩa API mà code gọi không phụ thuộc implementation | Interface | Phân tách hợp đồng khỏi lớp cụ thể |
| Muốn cung cấp hành vi mặc định nhỏ cho capability | Interface với default method | Cho phép implementation kế thừa hành vi nếu không cần override |
| Muốn tái sử dụng code nhưng không có quan hệ “là một loại của” hợp lý | Ưu tiên composition | Đối tượng cộng tác thường tránh được sự phụ thuộc cứng vào cây kế thừa |

## 7. Composition là lựa chọn thứ ba

Không phải mọi phần code dùng chung đều nên đặt trong abstract class. Nếu hai lớp chỉ cần dùng chung một dịch vụ hoặc thuật toán nhưng không phải cùng một họ khái niệm, hãy cân nhắc composition: chuyển phần logic đó thành một đối tượng riêng và truyền hoặc inject vào nơi cần dùng.

Composition giúp thay đổi hành vi trong runtime dễ hơn, tránh giới hạn kế thừa đơn của Java và giảm sự phụ thuộc vào chi tiết lớp cha. Đổi lại, nó có thể cần thêm một đối tượng hoặc một lớp cộng tác; hãy chọn theo độ phức tạp thực tế thay vì tạo abstraction quá sớm.

Ví dụ, nhiều loại thông báo có thể dùng chung một `Formatter` thông qua composition, trong khi các loại tác vụ thực sự chia sẻ trạng thái và invariant có thể hợp lý khi kế thừa `Task`.

## 8. Quy trình ra quyết định

1. Xác định điều code gọi cần biết: một hợp đồng/capability hay một nền tảng triển khai có trạng thái.
2. Nếu nhiều lớp cần cùng dữ liệu, constructor, invariant hoặc template method, cân nhắc abstract class.
3. Nếu các kiểu khác nhau cần cùng vai trò, hoặc một lớp cần nhiều vai trò, ưu tiên interface.
4. Nếu chỉ muốn tái sử dụng logic giữa các lớp không cùng một họ, cân nhắc composition.
5. Kiểm tra khả năng mở rộng: Java chỉ cho một lớp cha; interface cho phép kết hợp nhiều hợp đồng, nhưng default method có thể xung đột.
6. Để code gọi phụ thuộc vào interface khi có lợi cho khả năng thay thế implementation; chỉ phụ thuộc vào abstract class khi hành vi nền của nó thực sự là một phần quan trọng của thiết kế.
7. Viết ví dụ nhỏ và kiểm tra constructor, khả năng thay thế, các trường hợp lỗi, cùng hành vi khi thêm implementation mới.

Không có quy tắc rằng interface luôn tốt hơn hoặc abstract class luôn mạnh hơn. Thiết kế tốt chọn abstraction thể hiện đúng mục tiêu, giữ trách nhiệm rõ ràng và không khóa các implementation vào một quan hệ kế thừa không cần thiết.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstraction-choices-002">Chọn interface cho vai trò chung của các lớp không cùng họ</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn interface cho vai trò chung của các lớp không cùng họ

Một interface giúp các lớp có quan hệ kế thừa khác nhau cùng đảm nhiệm một **vai trò chung**. Ta chọn interface khi muốn một đoạn mã làm việc với một khả năng hoặc hợp đồng ổn định, bất kể đối tượng thực tế thuộc lớp nào. Ví dụ, một hóa đơn và một phiếu hỗ trợ không cần có cùng lớp cha nghiệp vụ để đều có thể cung cấp một dòng báo cáo.

Ý tưởng cốt lõi là: **mã gọi phụ thuộc vào vai trò mà nó cần, còn từng lớp tự chịu trách nhiệm thực hiện vai trò đó**. Interface tạo ra ranh giới rõ giữa điều người dùng của đối tượng có thể yêu cầu và cách mỗi lớp đáp ứng yêu cầu ấy.

## 1. Interface biểu diễn vai trò, không phải họ hàng lớp

Giả sử ứng dụng có các kiểu **Invoice**, **SupportTicket** và **UserAccount**. Chúng mô hình hóa các khái niệm khác nhau; ép chúng kế thừa cùng một lớp chỉ để dùng chung một method có thể tạo ra quan hệ phân loại không đúng với nghiệp vụ.

Nếu nhiều đối tượng có thể cung cấp một dòng tóm tắt để đưa vào báo cáo, hãy mô tả vai trò đó bằng interface:

~~~java
public interface Reportable {
    String reportLine();
}
~~~

Hợp đồng trên nói rằng mọi đối tượng **Reportable** phải cung cấp một chuỗi báo cáo. Interface không quy định chuỗi được tạo bằng cách nào, dữ liệu nào được lưu, hoặc lớp triển khai có những method riêng nào. Mỗi lớp hiện thực hóa hợp đồng theo dữ liệu và quy tắc của chính nó.

Lớp triển khai dùng từ khóa **implements** và phải cung cấp các method trừu tượng được yêu cầu:

~~~java
final class Invoice implements Reportable {
    @Override
    public String reportLine() {
        return "Invoice ...";
    }
}

final class SupportTicket implements Reportable {
    @Override
    public String reportLine() {
        return "Ticket ...";
    }
}
~~~

Đoạn trên minh họa cấu trúc; method trả chuỗi cố định chỉ để thấy chữ ký. Ví dụ chạy đầy đủ ở phần tiếp theo sẽ sử dụng dữ liệu thật của từng đối tượng.

**Invoice** và **SupportTicket** không cần kế thừa lẫn nhau hay chia sẻ một lớp cha nghiệp vụ. Chúng chỉ cam kết cùng thực hiện vai trò **Reportable**. Mỗi lớp vẫn có thể có constructor, field, method và quy tắc riêng.

## 2. Ví dụ Java 17+ có thể chạy

Lưu nội dung sau vào tệp **ReportableDemo.java**. Ví dụ có hai lớp miền độc lập và một dịch vụ chỉ biết interface.

~~~java
import java.util.List;

interface Reportable {
    String reportLine();
}

final class Invoice implements Reportable {
    private final String number;
    private final long totalCents;

    Invoice(String number, long totalCents) {
        this.number = number;
        this.totalCents = totalCents;
    }

    @Override
    public String reportLine() {
        return "Invoice " + number + ": " + totalCents + " cents";
    }
}

final class SupportTicket implements Reportable {
    private final int id;
    private final String status;

    SupportTicket(int id, String status) {
        this.id = id;
        this.status = status;
    }

    @Override
    public String reportLine() {
        return "Ticket #" + id + ": " + status;
    }
}

final class ReportPrinter {
    static void printAll(List<? extends Reportable> items) {
        for (Reportable item : items) {
            System.out.println(item.reportLine());
        }
    }
}

public class ReportableDemo {
    public static void main(String[] args) {
        List<Reportable> items = List.of(
            new Invoice("INV-42", 12_500L),
            new SupportTicket(17, "OPEN")
        );

        ReportPrinter.printAll(items);
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac ReportableDemo.java
java ReportableDemo
~~~

Đầu ra:

~~~text
Invoice INV-42: 12500 cents
Ticket #17: OPEN
~~~

Điểm quan trọng nằm ở **ReportPrinter**: nó không cần kiểm tra **instanceof Invoice**, **instanceof SupportTicket**, hoặc ép kiểu về lớp cụ thể. Vòng lặp chỉ gọi **reportLine()** qua kiểu **Reportable**. Khi chương trình chạy, Java chọn phần triển khai đúng theo lớp thực tế của mỗi đối tượng; đó là đa hình.

Nếu sau này thêm **UserAccount implements Reportable**, dịch vụ in báo cáo vẫn có thể xử lý đối tượng mới mà không cần thêm nhánh điều kiện hay sửa **ReportPrinter**. Điều này chỉ đúng khi lớp mới thực sự đáp ứng ý nghĩa của hợp đồng báo cáo.

## 3. Vì sao cách này hữu ích?

- **Giảm phụ thuộc vào lớp cụ thể.** Dịch vụ chỉ cần biết khả năng được cung cấp, nên thay đổi cách triển khai ít lan sang mã gọi.
- **Kết hợp các kiểu không cùng nhánh kế thừa.** Một danh sách hoặc method có thể tiếp nhận nhiều lớp khác nhau miễn là chúng có cùng vai trò.
- **Dễ mở rộng.** Lớp mới có thể tham gia hợp đồng mà không phải thay đổi tất cả nơi đang sử dụng hợp đồng ấy.
- **Dễ kiểm thử.** Có thể tạo một triển khai giả của interface để kiểm tra dịch vụ mà không phải dựng toàn bộ hệ thống thật.
- **Cho phép một lớp có nhiều vai trò.** Java chỉ cho một lớp kế thừa trực tiếp một lớp cha, nhưng lớp có thể **implements** nhiều interface. Chẳng hạn, một kiểu có thể vừa **Reportable** vừa thực hiện một vai trò **Exportable** nếu cả hai đều có ý nghĩa với nó.

Interface vì vậy thường hữu ích ở ranh giới giữa các thành phần: dịch vụ nhận một vai trò, repository cung cấp hợp đồng truy cập dữ liệu, hoặc một bộ xử lý chấp nhận các đối tượng có chung khả năng.

## 4. Chọn hợp đồng đủ nhỏ và đúng nghĩa

Tên interface nên nói rõ **vai trò hoặc khả năng** mà đối tượng cam kết cung cấp. Ví dụ: **Reportable**, **Closeable**, **Comparable&lt;T&gt;** hoặc một interface nghiệp vụ như **Refundable**. Tránh đặt tên quá chung chung như **Common**, **Helper** hoặc **Data** nếu tên đó không làm rõ hợp đồng.

Trước khi tạo interface, hãy xác định:

1. Đoạn mã gọi cần yêu cầu hành động hoặc thông tin nào?
2. Các lớp dự kiến triển khai có thể đáp ứng cùng một ý nghĩa của method đó không?
3. Chữ ký method có đủ rõ về tham số, kiểu trả về và điều kiện lỗi không?
4. Có khả năng nhiều lớp hoặc nhiều thành phần sẽ cần vai trò này không?
5. Hợp đồng có thể ổn định khi phần triển khai thay đổi không?

Một interface tốt thể hiện được điều bên gọi có quyền trông đợi. Nếu **reportLine()** được quy định là trả về một dòng báo cáo có thể hiển thị cho người dùng, mọi lớp triển khai nên đáp ứng ý nghĩa đó. Nếu một lớp trả về chuỗi rỗng trong khi lớp khác trả về một thông báo lỗi không liên quan, chúng có thể đang bị ép vào một hợp đồng không phù hợp.

Không cần tạo interface cho mọi lớp. Nếu hiện chỉ có một triển khai, không có ranh giới cần thay thế hoặc đa hình, và hợp đồng chưa có giá trị riêng, một method thông thường có thể đơn giản hơn. Hãy tạo interface khi nó thể hiện một vai trò có ý nghĩa, hỗ trợ thay thế triển khai, hoặc giúp giảm phụ thuộc trong thiết kế.

## 5. Interface hay abstract class?

| Nhu cầu | Nên cân nhắc |
| --- | --- |
| Các lớp không cùng họ cần cung cấp cùng một khả năng | Interface |
| Các lớp cần chung một hợp đồng nhưng có cách triển khai khác nhau | Interface |
| Nhiều lớp cùng chia sẻ trạng thái instance, constructor hoặc code triển khai nền tảng | Abstract class |
| Muốn một lớp có thể kết hợp nhiều vai trò độc lập | Interface |
| Muốn biểu diễn một quan hệ phân loại có trạng thái và hành vi chung | Có thể dùng abstract class, nếu quan hệ đó đúng với mô hình miền |

Interface không có trạng thái instance thông thường và không có constructor. Field được khai báo trong interface là hằng số **public static final**; method trừu tượng mặc định là **public**. Một abstract class có thể giữ field instance, constructor, method **protected** và phần triển khai dùng chung. Một lớp có thể kế thừa một abstract class và đồng thời triển khai nhiều interface.

Đừng chọn interface chỉ vì “dùng interface luôn tốt hơn”. Hãy chọn dựa trên quan hệ thực tế và trách nhiệm cần biểu diễn. Nếu hai lớp không có chung trạng thái hay nền tảng triển khai, nhưng có cùng một khả năng, interface thường là ranh giới phù hợp.

## 6. Một vài chi tiết Java cần nhớ

- **Triển khai method phải đủ khả năng truy cập.** Method trừu tượng trong interface là **public**, nên method triển khai phải là **public**; không thể giảm xuống package-private hoặc **protected**.
- **Một interface có thể mở rộng interface khác.** **interface AdvancedReportable extends Reportable** có thể bổ sung hợp đồng; lớp triển khai interface con phải đáp ứng cả các method trừu tượng được kế thừa.
- **Một lớp có thể triển khai nhiều interface.** Điều này cho phép kết hợp các vai trò độc lập, nhưng không nên thêm interface mà lớp không thể thực hiện đúng ý nghĩa.
- **Interface có thể có default và static method.** **default** phù hợp với hành vi mặc định có ý nghĩa chung cho mọi lớp triển khai; **static** thuộc về interface. Từ Java 9, interface cũng có thể có **private** method để chia sẻ logic nội bộ giữa các method của chính interface.
- **Cẩn thận với nhiều default method trùng chữ ký.** Nếu một lớp nhận các triển khai mặc định xung đột từ nhiều interface, lớp đó phải giải quyết xung đột bằng cách tự triển khai method.
- **Interface không tự bảo đảm hành vi đúng.** Trình biên dịch kiểm tra chữ ký, nhưng người thiết kế và kiểm thử vẫn phải bảo đảm mỗi lớp tôn trọng ý nghĩa của hợp đồng.

## 7. Những lỗi thiết kế thường gặp

- **Dùng lớp cha chỉ để gom những lớp không cùng bản chất.** Nếu các lớp chỉ chia sẻ một khả năng, ép chúng vào cùng một cây kế thừa có thể làm sai mô hình miền. Hãy xem xét interface theo vai trò.
- **Đưa quá nhiều method không liên quan vào một interface.** Lớp chỉ cần một phần hợp đồng sẽ bị buộc triển khai các method không có ý nghĩa. Tách thành các interface nhỏ theo vai trò cụ thể.
- **Đặt tên theo chi tiết triển khai.** Tên như **UsesArrayList** gắn hợp đồng với cách cài đặt. Tên như **Reportable** thể hiện điều bên gọi có thể làm.
- **Kiểm tra lớp cụ thể ở khắp nơi.** Nhiều nhánh **instanceof** thường là dấu hiệu đoạn mã có thể làm việc qua một interface chung; tuy vậy, chỉ trừu tượng hóa khi các nhánh thực sự có chung ý nghĩa.
- **Dùng default để che giấu khác biệt nghiệp vụ.** Một triển khai mặc định không phù hợp cho mọi lớp có thể làm hợp đồng khó hiểu. Chỉ chia sẻ hành vi khi nó đúng về mặt ngữ nghĩa.
- **Tạo interface chỉ để đổi tên một method.** Nếu không có vai trò, hợp đồng, ranh giới thay thế hoặc lợi ích đa hình nào, interface mới có thể chỉ làm tăng số kiểu cần hiểu.

Khi xem xét thiết kế, hãy thử hỏi: “Nếu thêm một lớp mới không cùng họ với các lớp hiện tại nhưng có cùng khả năng, đoạn mã gọi có thể dùng nó mà không cần biết lớp cụ thể không?” Nếu có, một interface theo vai trò có thể là hợp đồng đúng. Nếu câu trả lời là không vì các lớp không thật sự cùng ý nghĩa, hãy điều chỉnh hợp đồng thay vì ép chúng vào cùng một interface.

## 8. Checklist áp dụng

- [ ] Interface có tên thể hiện rõ vai trò chung.
- [ ] Các lớp triển khai thực sự đáp ứng cùng ý nghĩa của hợp đồng.
- [ ] Bên gọi phụ thuộc vào interface thay vì ép kiểu về lớp cụ thể.
- [ ] Method có chữ ký rõ, mức truy cập đúng và mô tả lỗi phù hợp.
- [ ] Interface không gom các trách nhiệm không liên quan.
- [ ] Không tạo abstraction chỉ để phòng xa khi chưa có lợi ích thiết kế cụ thể.
- [ ] Ví dụ kiểm thử hoặc chạy thử chứng minh nhiều lớp không cùng họ được xử lý qua cùng một hợp đồng.

Hãy xem interface như một cam kết có ý nghĩa: các lớp có thể khác nhau về dữ liệu và cách hoạt động, nhưng đều cung cấp đúng vai trò mà bên gọi cần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstraction-choices-003">Chọn abstract class khi cần trạng thái và quy trình dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn abstract class khi cần trạng thái và quy trình dùng chung

## 1. Ý tưởng chính

Trong Java, nên cân nhắc abstract class khi một nhóm lớp có quan hệ cùng loại, cần giữ dữ liệu theo từng đối tượng và phải thực hiện một quy trình chung. Lớp trừu tượng có thể cung cấp trường dữ liệu, constructor, các phương thức đã cài đặt và những phương thức trừu tượng để lớp con hoàn thiện phần khác biệt.

Ví dụ, nhiều loại báo cáo có thể cùng có tiêu đề, cùng kiểm tra đầu vào, cùng tạo phần đầu và phần tổng kết, nhưng trình bày từng mục theo cách khác nhau. Một abstract class có thể giữ tiêu đề và định nghĩa trình tự tạo báo cáo; các lớp con chỉ quyết định cách trình bày mỗi mục.

Cụm “trạng thái và quy trình dùng chung” là dấu hiệu để xem xét abstract class, không phải quy tắc tuyệt đối. Chọn nó khi phần chung thực sự thuộc về một họ lớp và giúp mô hình dễ hiểu hơn.

## 2. Abstract class cung cấp những gì?

Một abstract class có thể kết hợp các khả năng sau:

- **Trạng thái theo từng instance:** có trường instance để lưu dữ liệu chung, chẳng hạn tiêu đề, cấu hình hoặc thông tin cần cho các bước xử lý. Mỗi đối tượng vẫn có giá trị riêng.
- **Khởi tạo và bất biến:** constructor kiểm tra dữ liệu đầu vào và thiết lập trạng thái hợp lệ. Trường **private final** phù hợp với dữ liệu được gán một lần.
- **Hành vi dùng chung:** các phương thức cụ thể cài đặt phần logic mà mọi lớp con đều cần.
- **Điểm mở rộng:** phương thức trừu tượng yêu cầu lớp con cung cấp phần hành vi khác nhau.
- **Quy trình được kiểm soát:** phương thức cụ thể có thể gọi các bước chung theo một thứ tự rõ ràng. Nếu trình tự không được phép thay đổi ở lớp con, có thể khai báo phương thức đó là **final**.

Không thể tạo instance trực tiếp từ abstract class. Một lớp con cụ thể phải hoàn thiện các phương thức trừu tượng còn thiếu. Constructor của lớp cha vẫn chạy thông qua lời gọi **super(...)** khi tạo đối tượng lớp con.

Abstract class có thể triển khai interface và có thể chứa cả phương thức cụ thể lẫn phương thức trừu tượng. Java chỉ cho một lớp kế thừa trực tiếp từ một lớp cha, nên cần cân nhắc giới hạn kế thừa đơn khi thiết kế hệ thống.

## 3. Phân biệt abstract class, interface và composition

| Nhu cầu | Lựa chọn nên cân nhắc |
| --- | --- |
| Nhiều lớp cùng chia sẻ trạng thái instance và cách khởi tạo | Abstract class |
| Nhiều lớp cần tuân theo một hợp đồng, nhưng không cần chung trạng thái hoặc quy trình | Interface |
| Các lớp cần dùng chung một khả năng nhưng không thuộc cùng một họ kế thừa | Interface hoặc composition |
| Muốn cố định các bước của thuật toán và để lớp con tùy biến một vài bước | Abstract class với Template Method |
| Muốn thay đổi thuật toán hoặc thành phần cộng tác linh hoạt khi chạy | Composition, chẳng hạn truyền một đối tượng chiến lược vào constructor |

Interface vẫn có thể chứa **default** method và **static** method. Tuy nhiên, interface không có trường instance để lưu trạng thái riêng của từng đối tượng; các trường được khai báo trong interface là hằng **public static final**. Vì vậy, khi cần mỗi đối tượng mang trạng thái chung được khởi tạo qua constructor, abstract class thường phù hợp hơn.

Đừng chọn abstract class chỉ để tránh viết vài dòng trùng lặp. Nếu các lớp không có quan hệ “là một loại của” rõ ràng, hoặc cần kết hợp nhiều khả năng độc lập, interface và composition thường giữ thiết kế linh hoạt hơn.

## 4. Ví dụ chạy được với Java 17+

Ví dụ sau tạo báo cáo có tiêu đề và phần tổng kết dùng chung. Báo cáo dạng gạch đầu dòng và báo cáo đánh số chỉ khác cách trình bày từng mục.

Tạo file **Main.java** với toàn bộ nội dung sau:

~~~java
import java.util.List;
import java.util.Objects;

abstract class ReportGenerator {
    private final String title;

    protected ReportGenerator(String title) {
        this.title = Objects.requireNonNull(title, "title");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
    }

    public final String generate(List<String> items) {
        Objects.requireNonNull(items, "items");

        StringBuilder out = new StringBuilder();
        appendHeader(out);

        int index = 1;
        for (String item : items) {
            Objects.requireNonNull(item, "items must not contain null");
            appendItem(out, index, item);
            index++;
        }

        out.append("Total: ").append(items.size()).append(" items").append('\n');
        return out.toString();
    }

    private void appendHeader(StringBuilder out) {
        out.append(title).append('\n');
        out.append("-----").append('\n');
    }

    protected abstract void appendItem(StringBuilder out, int index, String item);
}

final class BulletReportGenerator extends ReportGenerator {
    BulletReportGenerator(String title) {
        super(title);
    }

    @Override
    protected void appendItem(StringBuilder out, int index, String item) {
        out.append("- ").append(item).append('\n');
    }
}

final class NumberedReportGenerator extends ReportGenerator {
    NumberedReportGenerator(String title) {
        super(title);
    }

    @Override
    protected void appendItem(StringBuilder out, int index, String item) {
        out.append(index).append(". ").append(item).append('\n');
    }
}

public class Main {
    public static void main(String[] args) {
        ReportGenerator bullets = new BulletReportGenerator("Fruits");
        ReportGenerator numbered = new NumberedReportGenerator("Tasks");

        System.out.println(bullets.generate(List.of("Apple", "Banana")));
        System.out.println(numbered.generate(List.of("Read", "Practice")));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file:

~~~text
javac Main.java
java Main
~~~

Stdout chính xác:

~~~text
Fruits
-----
- Apple
- Banana
Total: 2 items

Tasks
-----
1. Read
2. Practice
Total: 2 items
~~~

Ví dụ chỉ tạo chuỗi báo cáo trong bộ nhớ; nó không ghi tệp ra đĩa. Có thể thay đầu ra chuỗi bằng thao tác I/O trong một ứng dụng thực tế, đồng thời xử lý lỗi I/O phù hợp.

## 5. Đọc ví dụ theo luồng thực thi

**Trạng thái và constructor.** **ReportGenerator** giữ **title** dưới dạng trường **private final**. Constructor nhận tiêu đề, từ chối **null** và chuỗi rỗng hoặc chỉ có khoảng trắng. Mọi lớp con gọi constructor bằng **super(title)**, nên quy tắc khởi tạo này không bị lặp lại.

**Quy trình dùng chung.** Phương thức **generate** kiểm tra danh sách, tạo bộ đệm, gọi **appendHeader**, duyệt từng mục, gọi **appendItem** rồi thêm tổng số mục. Cả hai lớp con đều sử dụng cùng quy trình đó.

**Điểm tùy biến.** **appendItem** là phương thức **protected abstract**. Lớp con bắt buộc cài đặt cách ghi một mục: **BulletReportGenerator** thêm dấu gạch đầu dòng, còn **NumberedReportGenerator** thêm số thứ tự.

**Giữ nguyên thứ tự các bước.** **generate** được khai báo **final**, nên lớp con không thể thay thế toàn bộ quy trình bằng một cách triển khai khác. Lớp con chỉ tùy biến bước được thiết kế để mở rộng. Đây là ví dụ của mẫu thiết kế Template Method.

**Đa hình.** Hai biến trong **main** đều có kiểu khai báo **ReportGenerator**, nhưng Java gọi phiên bản **appendItem** của đối tượng thực tế. Mã sử dụng không cần kiểm tra từng loại bằng **if** hoặc **switch**.

**Một tệp Java.** Chỉ **Main** là lớp **public**, nên các lớp còn lại có thể đặt cùng tệp **Main.java**. Trong dự án lớn, có thể tách mỗi lớp ra tệp riêng theo quy ước của dự án.

## 6. Template Method: khi nào nên cố định quy trình?

Template Method là cách thiết kế trong đó một phương thức cấp cao định nghĩa các bước của thuật toán, còn một hoặc nhiều phương thức được lớp con cài đặt để thay đổi chi tiết. Trong ví dụ, **generate** là phương thức khuôn mẫu và **appendItem** là điểm mở rộng.

Mẫu này hữu ích khi thứ tự các bước là một phần của quy tắc nghiệp vụ. Ví dụ, xử lý dữ liệu có thể luôn cần kiểm tra đầu vào, chuẩn hóa dữ liệu, xử lý từng phần và tổng hợp kết quả; các loại bộ xử lý chỉ khác cách xử lý từng phần.

Hãy dùng **final** cho phương thức khuôn mẫu khi lớp con thực sự không được thay đổi thứ tự hoặc bỏ qua bước bắt buộc. Nếu lớp con cần thay đổi toàn bộ thuật toán một cách hợp lệ, một phương thức **final** sẽ quá hạn chế; hãy cân nhắc interface chiến lược hoặc composition.

Tránh gọi phương thức có thể override từ constructor của abstract class. Khi constructor lớp cha chạy, lớp con có thể chưa khởi tạo xong các trường của nó. Gọi phương thức override ở giai đoạn này dễ khiến mã lớp con hoạt động trên trạng thái chưa hoàn chỉnh.

## 7. Quy tắc thiết kế trạng thái

- Giữ trường dữ liệu ở phạm vi **private** và cung cấp phương thức truy cập có chủ đích khi cần. Không dùng **protected** cho mọi trường chỉ để lớp con tiện sửa.
- Ưu tiên **final** cho dữ liệu cấu hình được thiết lập một lần. Điều này không tự làm đối tượng bất biến nếu trường chứa một đối tượng mutable, nhưng giúp ngăn việc gán lại tham chiếu.
- Xác thực dữ liệu trong constructor để mọi instance bắt đầu ở trạng thái hợp lệ.
- Tránh để lớp cha chứa trạng thái mutable không rõ quyền sở hữu, vì các lớp con có thể vô tình tạo ra cách cập nhật mâu thuẫn.
- Không đưa dữ liệu theo từng yêu cầu hoặc người dùng vào một trường **static** dùng chung nếu dữ liệu đó phải độc lập giữa các lần xử lý.
- Chỉ đưa trạng thái lên lớp cha khi nó thật sự có ý nghĩa đối với mọi lớp con. Nếu chỉ một loại báo cáo cần một trường, có thể để trường đó ở lớp con.

Trong ví dụ, tiêu đề là dữ liệu riêng của từng instance và không đổi sau khi khởi tạo. Bộ đệm và chỉ số là biến cục bộ trong **generate**, nên chúng không bị chia sẻ giữa các lần gọi.

## 8. Lỗi thường gặp

**Chọn abstract class khi không có quan hệ chung hợp lý.** Chỉ có một vài phương thức giống nhau chưa đủ để tạo quan hệ kế thừa. Nếu các lớp độc lập nhưng cần cùng khả năng, dùng interface hoặc đối tượng cộng tác có thể phù hợp hơn.

**Lặp lại quy trình ở mọi lớp con.** Nếu mọi lớp con đều kiểm tra đầu vào, tạo header và tính tổng theo cùng cách, hãy gom các bước đó vào lớp cha. Mỗi bước chỉ nên được override khi có lý do thiết kế rõ ràng.

**Cho phép lớp con sửa trực tiếp trạng thái chung.** Trường **protected** mutable khiến quy tắc của lớp cha khó kiểm soát. Ưu tiên trường **private**, phương thức rõ mục đích và các điểm mở rộng nhỏ.

**Để phương thức trừu tượng quá rộng.** Một hook nên biểu đạt đúng phần cần thay đổi. Nếu lớp con phải viết lại gần như toàn bộ thuật toán, abstract class có thể đang gom những lớp không thực sự chung quy trình.

**Dùng final ở mọi nơi.** **final** hữu ích để bảo vệ bất biến hoặc trình tự bắt buộc, nhưng không nên dùng máy móc. Xác định rõ phần nào phải ổn định và phần nào là điểm mở rộng hợp lệ.

**Gọi phương thức override từ constructor.** Tránh để constructor của lớp cha gọi phương thức có thể được lớp con thay thế; trạng thái lớp con có thể chưa sẵn sàng.

**Nhầm abstract class với interface.** Abstract class có thể giữ trạng thái instance và constructor. Interface mô tả hợp đồng mà nhiều kiểu có thể triển khai, kể cả khi chúng đã kế thừa những lớp cha khác nhau.

## 9. Checklist ra quyết định

Trước khi tạo abstract class, hãy tự hỏi:

1. Các lớp có thực sự là những biến thể của cùng một khái niệm không?
2. Chúng có cần cùng trạng thái instance hoặc cùng quy tắc khởi tạo không?
3. Có một quy trình chung mà mọi lớp con nên tuân thủ không?
4. Có thể xác định một vài điểm mở rộng rõ ràng thay vì cho phép lớp con thay đổi mọi thứ không?
5. Có thể dùng interface hoặc composition để chia sẻ khả năng mà không buộc các lớp vào một hệ thống kế thừa không cần thiết không?

Nếu câu trả lời cho các câu hỏi về trạng thái và quy trình chung là có, abstract class là lựa chọn đáng xem xét. Nếu nhu cầu chủ yếu là mô tả một hợp đồng, interface thường đủ. Khi cần thay đổi hành vi linh hoạt và kết hợp nhiều thành phần, composition thường giúp giảm sự phụ thuộc vào cấu trúc kế thừa.

Mục tiêu là đặt phần trạng thái và quy trình thực sự dùng chung vào một nơi, đồng thời giữ cho các lớp con chỉ chịu trách nhiệm về phần hành vi khác biệt của chúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstraction-choices-004">Kết hợp interface công khai với lớp trừu tượng hỗ trợ tùy chọn</a>

<details>
<summary>Xem chi tiết</summary>

# Kết hợp interface công khai với lớp trừu tượng hỗ trợ tùy chọn

Khi thiết kế API Java, thường nên công bố một **interface mô tả hợp đồng** và cung cấp thêm một **abstract class làm lớp hỗ trợ tùy chọn**. Người dùng API có thể triển khai interface trực tiếp nếu muốn tự kiểm soát toàn bộ hành vi, hoặc kế thừa lớp trừu tượng để tái sử dụng phần triển khai chung.

Cách thiết kế này tách biệt hai quyết định: *một đối tượng phải làm được gì* và *một triển khai có thể tái sử dụng hành vi đó như thế nào*. Mã gọi API chỉ cần phụ thuộc vào interface; abstract class đóng vai trò một lựa chọn tiện lợi, không phải điều kiện bắt buộc.

## 1. Interface công khai định nghĩa hợp đồng

Interface nên mô tả những thao tác mà người dùng API được phép gọi và những điều kiện có ý nghĩa đối với các thao tác đó. Hợp đồng cần tập trung vào hành vi quan sát được: đầu vào, kết quả, lỗi có thể xảy ra và các bảo đảm mà mọi triển khai phải đáp ứng.

Ví dụ, một bộ kết xuất văn bản có thể được mô tả bằng interface:

```java
interface TextRenderer {
    String render(String text);
}
```

Đoạn mã sử dụng bộ kết xuất chỉ cần biết kiểu `TextRenderer` và gọi `render`. Nó không cần biết đối tượng là một lớp viết trực tiếp từ interface hay một lớp kế thừa abstract class.

```java
static void show(TextRenderer renderer, String value) {
    System.out.println(renderer.render(value));
}
```

Đây là điểm quan trọng của hợp đồng công khai: người gọi dựa vào khả năng mà interface cam kết, thay vì phụ thuộc vào một lớp triển khai cụ thể.

## 2. Abstract class cung cấp phần triển khai dùng chung

Interface ở trên chỉ định nghĩa thao tác `render`. Một lớp trừu tượng có thể cung cấp thuật toán chung và để các lớp con tùy chỉnh một bước nhỏ thông qua phương thức móc (hook) được khai báo `protected`.

Mẫu này thường được gọi là **skeletal implementation** khi abstract class giúp giảm lượng mã mà người triển khai phải viết. Khi lớp cơ sở kiểm soát thứ tự thực hiện các bước, đây cũng là một dạng của **Template Method**.

```java
import java.util.Objects;

abstract class AbstractTextRenderer implements TextRenderer {
    @Override
    public final String render(String text) {
        Objects.requireNonNull(text, "text");
        return "[" + renderContent(text.trim()) + "]";
    }

    protected abstract String renderContent(String normalizedText);
}
```

Lớp cơ sở thực hiện các bước chung: từ chối đầu vào `null`, loại bỏ khoảng trắng ở hai đầu, rồi bao kết quả bằng dấu ngoặc vuông. Phương thức `renderContent` là điểm tùy biến dành cho lớp con.

Từ khóa `final` trên `render` bảo đảm lớp con không thay đổi thứ tự của thuật toán chung. Đây là một quyết định thiết kế có chủ đích: nó phù hợp khi quy trình cần được giữ ổn định, nhưng không phù hợp nếu mỗi lớp con cần tự định nghĩa toàn bộ quy trình `render`.

## 3. Cả hai cách triển khai đều hợp lệ

Lớp sử dụng interface có thể tự triển khai hợp đồng mà không cần kế thừa abstract class:

```java
import java.util.Objects;

class DirectRenderer implements TextRenderer {
    @Override
    public String render(String text) {
        Objects.requireNonNull(text, "text");
        return text.trim();
    }
}
```

Hoặc có thể kế thừa lớp hỗ trợ và chỉ cung cấp bước còn thiếu:

```java
import java.util.Locale;

class UppercaseBracketRenderer extends AbstractTextRenderer {
    @Override
    protected String renderContent(String normalizedText) {
        return normalizedText.toUpperCase(Locale.ROOT);
    }
}
```

Cả hai đối tượng đều là `TextRenderer`. Lớp gọi không cần kiểm tra kiểu lớp cụ thể hoặc có hai nhánh xử lý khác nhau.

## 4. Ví dụ hoàn chỉnh có thể chạy

Lưu tất cả mã sau trong tệp `InterfaceAndAbstractSupportDemo.java`. Các interface và lớp hỗ trợ không có từ khóa `public` nên có thể nằm cùng tệp với lớp công khai:

```java
import java.util.Locale;
import java.util.Objects;

interface TextRenderer {
    String render(String text);
}

abstract class AbstractTextRenderer implements TextRenderer {
    @Override
    public final String render(String text) {
        Objects.requireNonNull(text, "text");
        return "[" + renderContent(text.trim()) + "]";
    }

    protected abstract String renderContent(String normalizedText);
}

class DirectRenderer implements TextRenderer {
    @Override
    public String render(String text) {
        Objects.requireNonNull(text, "text");
        return text.trim();
    }
}

class UppercaseBracketRenderer extends AbstractTextRenderer {
    @Override
    protected String renderContent(String normalizedText) {
        return normalizedText.toUpperCase(Locale.ROOT);
    }
}

public class InterfaceAndAbstractSupportDemo {
    private static void show(TextRenderer renderer, String value) {
        System.out.println(renderer.render(value));
    }

    public static void main(String[] args) {
        TextRenderer direct = new DirectRenderer();
        TextRenderer supported = new UppercaseBracketRenderer();

        show(direct, " Java ");
        show(supported, " Java ");
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```text
javac -encoding UTF-8 InterfaceAndAbstractSupportDemo.java
java InterfaceAndAbstractSupportDemo
```

Kết quả chuẩn:

```text
Java
[JAVA]
```

Dòng đầu cho thấy triển khai trực tiếp có thể tự chọn cách xử lý. Dòng thứ hai cho thấy lớp kế thừa tái sử dụng quy trình chung của abstract class và chỉ cung cấp phần định dạng nội dung.

Trong thiết kế thực tế, các kiểu và phương thức của API công khai thường được đặt trong những tệp riêng. Ví dụ trên gom chúng vào một tệp để người học có thể biên dịch và chạy ngay mà không cần cấu hình dự án.

## 5. Vì sao abstract class nên là tùy chọn?

Java chỉ cho phép một lớp kế thừa trực tiếp từ một lớp khác. Nếu API bắt buộc mọi triển khai phải kế thừa một abstract class, người dùng có thể không còn khả năng kế thừa lớp khác phù hợp với thiết kế của họ.

Khi interface là hợp đồng chính, người dùng có thể lựa chọn:

- **Triển khai trực tiếp:** phù hợp khi hành vi khác biệt, cần kế thừa một lớp khác, hoặc phần hỗ trợ không phù hợp.
- **Kế thừa abstract class:** phù hợp khi muốn dùng lại thuật toán, trạng thái, constructor hoặc các phương thức hỗ trợ đã được cung cấp.
- **Kết hợp với một lớp cơ sở riêng:** nếu có thể kế thừa một lớp khác, người dùng vẫn có thể triển khai interface công khai và tự tổ chức phần mã dùng chung.

Nhờ giữ abstract class là tùy chọn, API có thể cung cấp tiện ích mà không chiếm mất quyền lựa chọn cấu trúc lớp của người dùng.

## 6. Interface có `default` method thì khác gì?

Interface Java có thể cung cấp phương thức `default` để chia sẻ một hành vi đơn giản ngay trong hợp đồng. Cách này hữu ích khi hành vi không cần trạng thái của đối tượng hoặc không cần một hệ thống hook phức tạp.

Ví dụ:

```java
interface Named {
    String name();

    default boolean hasName() {
        return name() != null && !name().isBlank();
    }
}
```

Mọi triển khai `Named` đều nhận được `hasName` nếu không ghi đè phương thức đó. Tuy nhiên, `default` method không thay thế mọi vai trò của abstract class. Abstract class có thể có constructor, trường instance, các phương thức `protected` và một chuỗi bước triển khai có thể tái sử dụng.

Một quy tắc thực tế:

- Dùng interface để công bố hợp đồng và các phương thức `default` nhỏ khi hành vi chung thực sự phù hợp với mọi triển khai.
- Dùng abstract class khi cần một triển khai nền có trạng thái, constructor, phương thức hỗ trợ hoặc Template Method.
- Giữ lớp trừu tượng là tùy chọn nếu người dùng không cần phải kế thừa nó để đáp ứng hợp đồng.

Không nên thêm một abstract class chỉ để bọc lại interface mà không cung cấp giá trị dùng chung thực tế.

## 7. Giữ hợp đồng công khai ổn định

Interface công khai là điểm phụ thuộc của mã khách hàng. Việc thêm một phương thức abstract mới có thể làm các lớp triển khai hiện tại không còn biên dịch được cho đến khi chúng triển khai phương thức đó.

Một abstract class hỗ trợ có thể giảm công việc cho những lớp kế thừa nó nếu lớp cơ sở cung cấp triển khai phù hợp cho phương thức mới. Tuy nhiên, các lớp triển khai interface trực tiếp vẫn phải tuân theo hợp đồng mới. Vì vậy, không thể xem abstract class là cách tự động bảo đảm tương thích ngược cho mọi khách hàng.

Khi phát triển API đã được sử dụng rộng rãi:

1. Chỉ thêm thao tác công khai khi nó thực sự thuộc hợp đồng của interface.
2. Cân nhắc liệu một phương thức `default` có thể cung cấp hành vi tương thích hay không.
3. Nếu cần hỗ trợ triển khai trực tiếp, hãy xác định rõ cách hành vi mới hoạt động với các triển khai hiện hữu.
4. Nếu có abstract class hỗ trợ, hãy cập nhật nó một cách nhất quán nhưng vẫn xem các triển khai trực tiếp là một phần của API.
5. Kiểm thử cả lớp kế thừa abstract class và lớp triển khai interface trực tiếp.

Phương thức `default` cũng cần được thiết kế cẩn thận: thêm phương thức có chữ ký trùng hoặc xung đột với interface khác có thể gây xung đột khi kết hợp nhiều interface. Không nên thêm mặc định chỉ để tránh suy nghĩ về hợp đồng.

## 8. Những lỗi thiết kế thường gặp

- **Ép mọi người dùng phải kế thừa abstract class:** điều này giới hạn khả năng kế thừa đơn của Java và khiến lớp hỗ trợ trở thành ràng buộc không cần thiết.
- **Cho mã gọi phụ thuộc vào lớp hỗ trợ:** khi có thể, hãy nhận tham số bằng interface công khai để triển khai trực tiếp và triển khai dựa trên abstract class đều sử dụng được.
- **Đưa toàn bộ hành vi vào interface dù cần trạng thái hoặc hook:** hãy xem xét abstract class nếu thuật toán chung cần trường instance, constructor hoặc các bước tùy biến có kiểm soát.
- **Dùng abstract class mà không có phần tái sử dụng đáng kể:** nếu lớp chỉ lặp lại chữ ký interface, nó có thể làm API phức tạp mà không giúp người dùng.
- **Cho rằng abstract class tự bảo đảm tương thích ngược:** thay đổi hợp đồng interface vẫn ảnh hưởng đến các triển khai trực tiếp.
- **Để lớp con ghi đè một bước phải được giữ nguyên:** nếu thuật toán cần bảo toàn thứ tự, cân nhắc phương thức điều phối `final` và các hook `protected` rõ ràng; chỉ dùng cách này khi hạn chế ghi đè là yêu cầu thật sự.

## 9. Quy trình lựa chọn

Trước tiên, xác định những thao tác mà mọi đối tượng cần cung cấp và công bố chúng qua interface. Sau đó, tìm phần thuật toán hoặc trạng thái nào có thể dùng chung. Nếu phần dùng chung đơn giản và không cần trạng thái, phương thức `default` có thể đủ. Nếu cần constructor, trạng thái, hook hoặc một thuật toán khung, hãy cung cấp abstract class triển khai interface đó.

Cuối cùng, bảo đảm mã khách hàng có thể làm việc thông qua interface và không phải biết lớp hỗ trợ có được sử dụng hay không. Kiểm thử cả hai đường triển khai để xác nhận chúng đáp ứng cùng hợp đồng.

Kết hợp interface công khai với abstract class tùy chọn cho phép API vừa giữ được hợp đồng rõ ràng, vừa cung cấp mã dùng chung hữu ích. Interface xác định cách người dùng tương tác; abstract class giảm công sức triển khai khi phù hợp, đồng thời để những người dùng khác tự chọn cách hiện thực hợp đồng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstraction-choices-005">Đánh đổi giữa kế thừa triển khai và composition, delegation</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh đổi giữa kế thừa triển khai và composition, delegation

Khi nhiều kiểu có hành vi chung, Java cho phép tái sử dụng mã bằng **kế thừa triển khai** hoặc bằng **composition kết hợp delegation**. Cả hai đều có thể giảm trùng lặp, nhưng chúng tạo ra mối liên kết khác nhau giữa các thành phần. Chọn đúng cách phụ thuộc vào quan hệ miền nghiệp vụ, mức độ cần thay đổi hành vi và độ ổn định của phần triển khai được tái sử dụng.

- **Kế thừa triển khai (implementation inheritance):** lớp con kế thừa trạng thái và phương thức đã có từ lớp cha, có thể dùng lại hoặc ghi đè hành vi.
- **Composition:** một đối tượng chứa hoặc giữ tham chiếu đến đối tượng khác để hợp thành hành vi của nó.
- **Delegation:** một đối tượng chuyển một phần công việc cho đối tượng được nó giữ tham chiếu. Delegation thường là cách composition sử dụng thành phần bên trong.

Composition và delegation thường xuất hiện cùng nhau: đối tượng ngoài giữ một formatter, logger hoặc policy rồi ủy quyền tác vụ tương ứng cho thành phần đó.

## 1. Kế thừa triển khai: tái sử dụng qua lớp cha

Một lớp cơ sở có thể cung cấp quy trình chung, còn lớp con tùy biến một bước thông qua phương thức abstract hoặc ghi đè phương thức có sẵn. Ví dụ, một lớp thông báo có thể giữ quy trình chuẩn bị thông điệp, trong khi lớp con định nghĩa cách định dạng nội dung.

Ưu điểm:

- Mã dùng chung nằm ở một nơi; lớp con có thể kế thừa trực tiếp các phương thức và trạng thái được phép truy cập.
- Mô hình rõ ràng khi quan hệ thực sự là một dạng chuyên biệt của kiểu cơ sở.
- Template Method có thể cố định trình tự xử lý ở lớp cha và cho phép lớp con tùy biến những bước đã xác định.

Đánh đổi:

- Quan hệ giữa lớp con và lớp cha khá chặt. Thay đổi phương thức được kế thừa có thể ảnh hưởng đến mọi lớp con, kể cả lớp đang phụ thuộc vào chi tiết triển khai ngầm.
- Lớp con thường bị ràng buộc bởi một lớp cha duy nhất. Java không hỗ trợ kế thừa triển khai từ nhiều lớp.
- Ghi đè có thể phá vỡ giả định của lớp cha nếu hợp đồng không được thiết kế rõ ràng. Phương thức protected và các trạng thái có thể thay đổi cũng có thể tạo ra nhiều phụ thuộc khó thấy.
- Hành vi được chọn chủ yếu qua kiểu của lớp con; thay đổi một chính sách cho từng đối tượng thường đòi hỏi thêm lớp con hoặc cấu trúc điều kiện.

Kế thừa triển khai hữu ích khi các kiểu thật sự chia sẻ một khái niệm cơ sở ổn định, và lớp cha có thể định nghĩa một hợp đồng nhất quán cho tất cả lớp con.

## 2. Composition và delegation: ghép hành vi từ thành phần

Với composition, đối tượng ngoài giữ một đối tượng khác làm thành phần. Thay vì kế thừa cách định dạng từ lớp cha, bộ chuẩn bị thông báo có thể giữ một đối tượng triển khai interface MessageFormatter và ủy quyền việc định dạng cho nó.

Ưu điểm:

- Mối liên kết thường lỏng hơn: đối tượng ngoài phụ thuộc vào interface nhỏ thay vì một lớp triển khai cụ thể.
- Có thể chọn thành phần khi khởi tạo đối tượng, thuận tiện cho cấu hình, dependency injection và kiểm thử bằng fake hoặc stub.
- Có thể kết hợp nhiều hành vi độc lập, ví dụ formatter, retry policy, logger và transport, mà không tạo ra một cây kế thừa lớn.
- Có thể tái sử dụng một thành phần ở những lớp không có quan hệ kế thừa với nhau.

Đánh đổi:

- Cần khai báo và truyền các thành phần phụ thuộc; một đối tượng có thể có nhiều trường và constructor hơn.
- Delegation có thể tạo thêm phương thức chuyển tiếp. Nếu lớp ngoài chỉ lặp lại toàn bộ API của thành phần, nó có thể trở thành lớp bọc dư thừa.
- Cần quyết định rõ ai sở hữu vòng đời, cấu hình và xử lý lỗi của thành phần.
- Composition không tự động bảo đảm thiết kế tốt: nếu đối tượng giữ quá nhiều phụ thuộc hoặc điều phối quá nhiều việc, nó vẫn có thể trở nên khó bảo trì.

Composition phù hợp khi một đối tượng cần một khả năng có thể thay thế, khi các khả năng thay đổi độc lập, hoặc khi không có quan hệ “là một” tự nhiên giữa đối tượng ngoài và thành phần.

## 3. Ví dụ Java 17+: cùng mục tiêu, hai cách tổ chức

Chương trình dưới đây tạo thông điệp theo hai cách. Trong nhánh kế thừa, lớp con cung cấp bước định dạng cho quy trình chung ở lớp cha. Trong nhánh composition, lớp Notifier giữ MessageFormatter và ủy quyền bước định dạng cho nó.

Lưu toàn bộ mã vào tệp **NotifierDemo.java**:

~~~java
import java.util.Objects;

interface MessageFormatter {
    String format(String recipient, String message);
}

// Option 1: implementation inheritance.
abstract class InheritanceNotifier {
    protected abstract String format(String recipient, String message);

    public final String prepare(String recipient, String message) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(message, "message");
        return "SEND " + format(recipient, message);
    }
}

final class PlainTextInheritanceNotifier extends InheritanceNotifier {
    @Override
    protected String format(String recipient, String message) {
        return "to=" + recipient + "; body=" + message;
    }
}

// Option 2: composition and delegation through an interface.
final class PlainTextFormatter implements MessageFormatter {
    @Override
    public String format(String recipient, String message) {
        return "to=" + recipient + "; body=" + message;
    }
}

final class BracketFormatter implements MessageFormatter {
    @Override
    public String format(String recipient, String message) {
        return "[" + recipient + "] " + message;
    }
}

final class ComposedNotifier {
    private final MessageFormatter formatter;

    public ComposedNotifier(MessageFormatter formatter) {
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    public String prepare(String recipient, String message) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(message, "message");
        // Delegate formatting to the injected component.
        return "SEND " + formatter.format(recipient, message);
    }
}

public class NotifierDemo {
    public static void main(String[] args) {
        InheritanceNotifier inherited = new PlainTextInheritanceNotifier();
        System.out.println("Inheritance: "
                + inherited.prepare("alice", "hello"));

        ComposedNotifier plain = new ComposedNotifier(new PlainTextFormatter());
        System.out.println("Composition: "
                + plain.prepare("alice", "hello"));

        ComposedNotifier bracket = new ComposedNotifier(new BracketFormatter());
        System.out.println("Swapped formatter: "
                + bracket.prepare("bob", "welcome"));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac NotifierDemo.java
java NotifierDemo
~~~

Stdout chính xác:

~~~text
Inheritance: SEND to=alice; body=hello
Composition: SEND to=alice; body=hello
Swapped formatter: SEND [bob] welcome
~~~

Hai dòng đầu tạo cùng định dạng đầu ra nhưng đi qua hai cấu trúc khác nhau. Với kế thừa, lớp con phải được tạo riêng cho biến thể triển khai. Với composition, có thể tạo một ComposedNotifier khác và cung cấp BracketFormatter mà không cần tạo lớp con mới của Notifier. Thành phần formatter được chọn khi khởi tạo; nếu cần thay đổi sau đó, hãy thiết kế cơ chế cấu hình hoặc thay thế rõ ràng thay vì ngầm làm đối tượng mutable.

Ví dụ cố ý giữ đơn giản để làm rõ quan hệ giữa các kiểu. Trong ứng dụng thực, quy trình chuẩn bị và gửi thông báo có thể cần kiểm tra địa chỉ, xử lý lỗi, mã hóa, ghi log và gọi mạng. Những trách nhiệm đó nên được phân chia theo hợp đồng và ranh giới phù hợp, thay vì dồn hết vào một lớp cơ sở hoặc một lớp điều phối lớn.

## 4. Delegation khác với kế thừa như thế nào?

Trong kế thừa, đối tượng lớp con cũng là một thể hiện của lớp cha; nó thừa hưởng API và có thể dùng những phương thức đã kế thừa. Lớp con có thể ghi đè phương thức để thay đổi hành vi đa hình.

Trong delegation, đối tượng ngoài có một tham chiếu đến đối tượng cộng tác và gọi phương thức của nó. Đối tượng ngoài không tự động có toàn bộ API của thành phần. Nó quyết định phần nào được cung cấp ra bên ngoài và có thể bổ sung điều phối, kiểm tra hoặc phối hợp nhiều thành phần.

Một interface thường là ranh giới hữu ích cho delegation vì bên gọi chỉ phụ thuộc vào hợp đồng. Tuy nhiên, interface không bắt buộc phải dùng cùng composition; một lớp có thể triển khai interface bằng kế thừa, bằng logic riêng hoặc bằng cách ủy quyền.

## 5. Bảng so sánh các đánh đổi

| Tiêu chí | Kế thừa triển khai | Composition và delegation |
|---|---|---|
| Quan hệ | Lớp con là một dạng của lớp cha | Đối tượng có hoặc sử dụng thành phần |
| Tái sử dụng | Kế thừa phương thức và trạng thái | Gọi hành vi của thành phần |
| Ghép nhiều hành vi | Bị giới hạn bởi một lớp cha; dễ phát sinh cây lớp | Có thể giữ nhiều thành phần độc lập |
| Thay đổi hành vi | Thường qua ghi đè hoặc lớp con khác | Thường qua cung cấp implementation khác |
| Mức phụ thuộc | Có thể chặt với chi tiết lớp cha | Có thể lỏng hơn nếu phụ thuộc interface |
| Độ phức tạp | Ít mã kết nối lúc đầu, nhưng cây lớp có thể phình to | Thêm constructor và đối tượng cộng tác, nhưng ranh giới rõ hơn |
| Kiểm thử | Có thể cần tạo lớp con hoặc kiểm thử cả lớp cơ sở | Có thể tiêm fake/stub qua interface |
| Phù hợp khi | Có quan hệ chuyên biệt thực sự và hợp đồng cơ sở ổn định | Hành vi cần thay thế, kết hợp hoặc thay đổi độc lập |

Không có cách nào luôn tốt hơn. Composition không loại bỏ mọi chi phí, và kế thừa không phải lúc nào cũng là thiết kế sai. Hãy đánh giá mức độ ổn định của hợp đồng, số lượng biến thể, khả năng cần phối hợp hành vi và mức độ phụ thuộc mà thay đổi trong tương lai có thể tạo ra.

## 6. Một số lỗi thiết kế thường gặp

- **Dùng kế thừa chỉ để lấy một phương thức tiện ích.** Nếu quan hệ “là một” không có ý nghĩa trong miền nghiệp vụ, hãy xem xét một thành phần hoặc phương thức tiện ích phù hợp.
- **Tạo lớp con cho mọi tổ hợp hành vi.** Khi số lớp tăng theo tích của nhiều lựa chọn độc lập, hãy cân nhắc ghép các thành phần thay vì tạo cây kế thừa cho từng tổ hợp.
- **Để lớp con phụ thuộc vào chi tiết nội bộ của lớp cha.** Quá nhiều trạng thái protected hoặc các phương thức gọi chéo làm việc thay đổi lớp cha trở nên rủi ro.
- **Ủy quyền toàn bộ API một cách máy móc.** Chỉ đưa ra những thao tác mà đối tượng ngoài thực sự cần cung cấp; nếu không có trách nhiệm nào ngoài chuyển tiếp, hãy cân nhắc dùng trực tiếp thành phần.
- **Ẩn phụ thuộc bằng cách tự khởi tạo bên trong.** Khi lớp tự tạo một implementation cụ thể, việc thay thế hoặc kiểm thử thường khó hơn. Truyền phụ thuộc qua constructor khi việc cấu hình và thay thế là yêu cầu thực tế.
- **Nhầm composition với sở hữu vòng đời tuyệt đối.** Một tham chiếu đến thành phần không tự nó quyết định ai sở hữu tài nguyên hoặc khi nào tài nguyên được đóng. Quy tắc vòng đời cần được xác định rõ, nhất là với file, kết nối và thread.

## 7. Quy trình chọn giải pháp

1. Xác định phần hành vi nào thực sự giống nhau và phần nào có khả năng thay đổi độc lập.
2. Nếu các kiểu có quan hệ chuyên biệt tự nhiên và cần một quy trình chung ổn định, xem xét lớp cơ sở với hợp đồng rõ ràng.
3. Nếu hành vi là một chính sách hoặc dịch vụ có thể thay thế, định nghĩa interface nhỏ rồi dùng composition và delegation.
4. Tránh để lớp con dựa vào chi tiết nội bộ không được cam kết của lớp cha; tránh để lớp điều phối trở thành một lớp bọc không có trách nhiệm.
5. Kiểm thử cả hành vi chung và biến thể: cùng đầu vào có kết quả mong đợi, thay implementation không làm thay đổi hợp đồng, và lỗi của thành phần được xử lý theo chính sách đã định.

Kế thừa triển khai tái sử dụng mã qua một quan hệ kiểu tương đối chặt. Composition và delegation tái sử dụng bằng cách ghép các cộng tác viên qua hợp đồng. Chọn dựa trên mối quan hệ và cách hệ thống cần thay đổi, rồi giữ cho hợp đồng đủ nhỏ để việc thay thế một triển khai không làm lan rộng thay đổi sang phần còn lại.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstraction-choices-006">Nguyên tắc thay thế Liskov và các bất biến của hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Nguyên tắc thay thế Liskov và các bất biến của hợp đồng

## Mục tiêu

Nguyên tắc thay thế Liskov (Liskov Substitution Principle, LSP) nói rằng một đối tượng của kiểu con phải có thể được dùng ở mọi nơi chương trình mong đợi kiểu cha mà vẫn giữ đúng hành vi đã cam kết. Đây là một phần của nguyên tắc SOLID, thường được phát biểu bằng ý tưởng: nếu `S` là kiểu con của `T`, có thể thay một đối tượng `T` bằng đối tượng `S` mà không làm hỏng tính đúng đắn của chương trình.

Trong Java, quan hệ `extends` hoặc `implements` chỉ chứng minh quan hệ kiểu ở mức cú pháp. Nó không tự chứng minh LSP. Một lớp có thể biên dịch thành công nhưng vẫn vi phạm hợp đồng hành vi mà lớp cha hoặc interface đã hứa với người gọi.

## 1. Hợp đồng hành vi gồm những gì?

Hợp đồng của một phương thức hoặc kiểu mô tả những điều người gọi được phép giả định và những gì phương thức phải đảm bảo.

- **Tiền điều kiện (precondition):** điều kiện đầu vào mà người gọi phải đáp ứng trước khi gọi phương thức.
- **Hậu điều kiện (postcondition):** điều kiện kết quả hoặc trạng thái mà phương thức cam kết sau khi thực hiện thành công.
- **Bất biến (invariant):** điều kiện phải luôn đúng đối với đối tượng ở những thời điểm được hợp đồng quy định, đặc biệt trước và sau các thao tác công khai.
- **Ràng buộc lịch sử (history constraint):** giới hạn các thay đổi trạng thái được phép theo thời gian; kiểu con không được tạo ra chuỗi chuyển trạng thái mà hợp đồng kiểu cha cấm.
- **Hành vi lỗi:** các ngoại lệ và điều kiện lỗi mà người gọi có thể gặp khi dùng đầu vào hợp lệ.

LSP yêu cầu kiểu con tôn trọng hợp đồng của kiểu cha. Một cách ghi nhớ hữu ích là:

1. Kiểu con không được siết chặt tiền điều kiện: mọi đầu vào hợp lệ với kiểu cha vẫn phải được chấp nhận bởi kiểu con.
2. Kiểu con không được làm yếu hậu điều kiện: mọi bảo đảm của kiểu cha vẫn phải đúng khi đối tượng thực tế là kiểu con.
3. Kiểu con phải giữ các bất biến mà kiểu cha công khai cam kết. Kiểu con có thể có thêm bất biến riêng, nhưng việc duy trì chúng không được phá vỡ hợp đồng của kiểu cha.
4. Kiểu con phải tôn trọng những chuyển đổi trạng thái mà người dùng kiểu cha được phép dựa vào.
5. Với đầu vào hợp lệ, kiểu con không nên phát sinh lỗi mới khiến lời gọi vốn hợp lệ theo hợp đồng kiểu cha trở thành thất bại.

Ví dụ, nếu hợp đồng kiểu cha cho phép gọi một phương thức với mọi số nguyên không âm, kiểu con không thể tự ý yêu cầu thêm rằng giá trị phải nhỏ hơn 100. Đó là siết chặt tiền điều kiện. Nếu kiểu cha cam kết rằng một thao tác thành công sẽ trả về một danh sách không rỗng, kiểu con không thể trả về danh sách rỗng trong trường hợp tương tự. Đó là làm yếu hậu điều kiện.

## 2. Ví dụ kinh điển: hình chữ nhật và hình vuông có thể thay thế nhau không?

Về toán học, hình vuông là một hình chữ nhật đặc biệt. Nhưng quan hệ “là một” trong mô hình miền nghiệp vụ chưa đủ để quyết định quan hệ kế thừa trong chương trình. Hãy xem một lớp hình chữ nhật có thể thay đổi chiều rộng và chiều cao độc lập.

Hợp đồng của lớp này nói rõ rằng `setWidth` chỉ thay đổi chiều rộng, còn chiều cao được giữ nguyên. Một lớp hình vuông kế thừa nó thường cố bảo đảm chiều rộng luôn bằng chiều cao bằng cách ghi đè cả hai setter. Điều đó duy trì bất biến của hình vuông, nhưng lại phá vỡ lời hứa của hình chữ nhật.

Lưu mã sau vào tệp `LiskovContractDemo.java`:

```java
public class LiskovContractDemo {
    public static void main(String[] args) {
        MutableRectangle rectangle = new MutableRectangle(4, 5);
        MutableRectangle squareAsRectangle = new MutableSquare(4);

        checkWidthSetterContract(rectangle);
        checkWidthSetterContract(squareAsRectangle);

        Shape rectangleShape = new RectangleShape(4, 5);
        Shape squareShape = new SquareShape(4);
        System.out.println("Shape model: rectangle area="
                + rectangleShape.area() + ", square area=" + squareShape.area());
    }

    // Hợp đồng Rectangle quy định setWidth không thay đổi chiều cao.
    private static void checkWidthSetterContract(MutableRectangle rectangle) {
        int heightBefore = rectangle.getHeight();
        rectangle.setWidth(10);
        int expectedArea = 10 * heightBefore;

        System.out.printf("%s: expected area=%d, actual area=%d%n",
                rectangle.getClass().getSimpleName(), expectedArea, rectangle.area());
    }

    static class MutableRectangle {
        private int width;
        private int height;

        MutableRectangle(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int area() {
            return width * height;
        }
    }

    static final class MutableSquare extends MutableRectangle {
        MutableSquare(int side) {
            super(side, side);
        }

        @Override
        public void setWidth(int width) {
            super.setWidth(width);
            super.setHeight(width);
        }

        @Override
        public void setHeight(int height) {
            super.setHeight(height);
            super.setWidth(height);
        }
    }

    interface Shape {
        int area();
    }

    record RectangleShape(int width, int height) implements Shape {
        RectangleShape {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Dimensions must be non-negative");
            }
        }

        @Override
        public int area() {
            return width * height;
        }
    }

    record SquareShape(int side) implements Shape {
        SquareShape {
            if (side < 0) {
                throw new IllegalArgumentException("Side must be non-negative");
            }
        }

        @Override
        public int area() {
            return side * side;
        }
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```text
javac -encoding UTF-8 LiskovContractDemo.java
java LiskovContractDemo
```

Kết quả chuẩn:

```text
MutableRectangle: expected area=50, actual area=50
MutableSquare: expected area=40, actual area=100
Shape model: rectangle area=20, square area=16
```

Khi tham số là `MutableRectangle(4, 5)`, đặt chiều rộng thành 10 giữ nguyên chiều cao 5, nên diện tích là 50. Khi truyền `MutableSquare(4)` qua cùng kiểu tham chiếu `MutableRectangle`, lời gọi `setWidth(10)` cũng đổi chiều cao thành 10; diện tích trở thành 100 thay vì 40 như người gọi được phép kỳ vọng theo hợp đồng lớp cha. Chương trình vẫn biên dịch, nhưng hành vi đã vi phạm LSP.

## 3. Thiết kế lại bằng abstraction theo hành vi

Nếu người gọi chỉ cần biết diện tích, không nên yêu cầu mọi hình đều có các setter chiều rộng và chiều cao độc lập. Hãy mô tả hành vi chung bằng interface `Shape`, rồi để hình chữ nhật và hình vuông là hai kiểu riêng biệt. Phần cuối của chương trình trên minh họa cách này: cả hai kiểu đều có thể được dùng thông qua `Shape`, và hợp đồng chung chỉ yêu cầu cung cấp diện tích.

Cách thiết kế này giữ được đặc điểm riêng của từng kiểu:

- `RectangleShape` lưu chiều rộng và chiều cao độc lập.
- `SquareShape` lưu một cạnh, nên không thể rơi vào trạng thái có hai cạnh khác nhau.
- Cả hai đều đáp ứng hợp đồng `area()` và không cần giả vờ rằng các thao tác thay đổi kích thước của chúng có cùng ngữ nghĩa.

Một giải pháp khác là dùng composition thay vì kế thừa nếu hai lớp cần chia sẻ mã nhưng không có cùng hợp đồng hành vi. Kế thừa nên biểu diễn khả năng thay thế được, không chỉ là cách tái sử dụng một số trường hoặc phương thức.

## 4. Bất biến của kiểu cha và kiểu con

Bất biến giúp xác định trạng thái nào luôn phải đúng. Chẳng hạn, một tài khoản ngân hàng có thể yêu cầu số tài khoản không đổi sau khi tạo; một tập hợp có thể yêu cầu không chứa phần tử trùng lặp; một hình vuông yêu cầu hai cạnh bằng nhau.

Khi tạo kiểu con, hãy kiểm tra hai nhóm điều kiện:

- **Bất biến của kiểu cha:** vẫn phải đúng đối với mọi đối tượng kiểu con được dùng thông qua kiểu cha. Không được làm mất bảo đảm mà người gọi đã dựa vào.
- **Bất biến bổ sung của kiểu con:** được phép chặt chẽ hơn, miễn là kiểu con có thể duy trì chúng mà không thay đổi hành vi được hứa bởi kiểu cha.

Một bất biến riêng có thể khiến kế thừa trở nên bất khả thi. Trong ví dụ trên, hình vuông yêu cầu chiều rộng và chiều cao bằng nhau; lớp hình chữ nhật cho phép hai giá trị thay đổi độc lập. Khi cùng một đối tượng phải đáp ứng cả hai hợp đồng, hai yêu cầu xung đột. Đây là dấu hiệu nên thay đổi abstraction hoặc mô hình hóa các kiểu riêng qua một interface nhỏ hơn.

Không nên nhầm bất biến với tiền điều kiện. Bất biến mô tả trạng thái hợp lệ của đối tượng; tiền điều kiện mô tả điều người gọi phải đáp ứng cho một lời gọi cụ thể. Nếu kiểu con từ chối một lời gọi mà kiểu cha chấp nhận chỉ để bảo vệ bất biến riêng, thiết kế kế thừa có thể đã sai.

## 5. Cách đánh giá một quan hệ kế thừa trong Java

Với mỗi lớp con hoặc lớp triển khai interface, hãy tự hỏi:

1. **Đầu vào:** Có đầu vào nào hợp lệ theo hợp đồng kiểu cha nhưng bị kiểu con từ chối không?
2. **Kết quả:** Kiểu con có trả về kết quả yếu hơn, thiếu bảo đảm hoặc trạng thái khác với điều người gọi được hứa không?
3. **Trạng thái:** Sau một lời gọi hợp lệ, các bất biến công khai của kiểu cha còn đúng không?
4. **Chuỗi thao tác:** Có chuỗi thao tác nào hợp lệ với kiểu cha nhưng trở nên bất hợp lệ hoặc gây kết quả bất ngờ khi dùng kiểu con không?
5. **Ngoại lệ:** Kiểu con có ném ngoại lệ mới trong trường hợp mà hợp đồng kiểu cha hứa sẽ thành công không?
6. **Ngữ nghĩa:** Tên phương thức và hành vi thực tế của kiểu con có còn phù hợp với cách người gọi hiểu hợp đồng kiểu cha không?

Java có thể kiểm tra một phần quy tắc ghi đè ở mức ngôn ngữ. Ví dụ, phương thức ghi đè không thể giảm mức truy cập, kiểu trả về có thể tương thích theo quy tắc covariance, và không thể khai báo thêm checked exception rộng hơn những gì phương thức bị ghi đè cho phép. Tuy nhiên, trình biên dịch không thể chứng minh rằng tiền điều kiện, hậu điều kiện và bất biến về mặt nghiệp vụ đã được giữ nguyên. Vì thế, code biên dịch thành công chưa đủ để kết luận thiết kế tuân thủ LSP.

## 6. Thực hành để giữ hợp đồng ổn định

- **Viết rõ hợp đồng:** JavaDoc nên nêu điều kiện đầu vào, kết quả, ảnh hưởng lên trạng thái và ngoại lệ quan trọng của phương thức công khai.
- **Giữ interface nhỏ và có mục đích:** chỉ đưa vào interface những hành vi mà mọi kiểu triển khai đều có thể cung cấp với cùng ý nghĩa.
- **Ưu tiên bất biến khi phù hợp:** kiểu bất biến thường ít có rủi ro vi phạm ràng buộc lịch sử vì trạng thái không thay đổi sau khi tạo.
- **Dùng composition khi hợp đồng không tương thích:** chia sẻ triển khai không bắt buộc phải chia sẻ quan hệ kế thừa.
- **Kiểm thử hợp đồng:** chạy cùng một bộ kiểm thử hành vi trên lớp cơ sở và các lớp triển khai. Kiểm thử không chứng minh mọi trường hợp, nhưng có thể phát hiện các vi phạm thực tế như ví dụ hình chữ nhật/hình vuông.
- **Kiểm thử các chuỗi thao tác:** không chỉ kiểm tra một lời gọi riêng lẻ; hãy thử các trình tự thay đổi trạng thái mà người dùng kiểu cha được phép thực hiện.

## Ghi nhớ

LSP là nguyên tắc về hành vi: kiểu con phải giữ những cam kết mà người gọi được phép dựa vào khi làm việc với kiểu cha. Không siết chặt tiền điều kiện, không làm yếu hậu điều kiện, giữ bất biến và các chuyển đổi trạng thái hợp lệ. Nếu một lớp con phải phá hợp đồng của lớp cha để duy trì quy tắc riêng của nó, hãy cân nhắc dùng interface thể hiện hành vi chung, tách các abstraction hoặc chuyển sang composition thay vì cố ép quan hệ kế thừa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstraction-choices-007">Tách interface theo nhu cầu sử dụng và tránh interface quá lớn</a>

<details>
<summary>Xem chi tiết</summary>

# Tách interface theo nhu cầu sử dụng và tránh interface quá lớn

Interface Segregation Principle (ISP), thường dịch là **nguyên tắc phân tách interface**, là chữ I trong bộ nguyên tắc SOLID. Nguyên tắc này nói rằng một client không nên bị buộc phụ thuộc vào những phương thức mà nó không sử dụng. Khi một interface gom quá nhiều khả năng không liên quan đến mọi bên gọi, các lớp triển khai có thể phải cung cấp những phương thức vô nghĩa, còn client phải biết đến một hợp đồng rộng hơn nhu cầu thực tế.

Trong Java, có thể áp dụng ISP bằng cách thiết kế các interface nhỏ, có mục đích rõ ràng theo vai trò của client hoặc theo một khả năng nghiệp vụ thống nhất. Một lớp vẫn có thể triển khai nhiều interface nếu nó thực sự hỗ trợ nhiều khả năng. Mục tiêu không phải là tạo interface cho từng phương thức, mà là để mỗi client chỉ phụ thuộc vào hợp đồng cần thiết.

## 1. Vấn đề của một interface quá lớn

Hãy hình dung một ứng dụng văn phòng có máy in cơ bản, máy quét và máy đa chức năng. Nếu mọi thiết bị đều phải triển khai một interface duy nhất gồm `print`, `scan` và `fax`, máy in cơ bản phải khai báo cả những thao tác mà nó không hỗ trợ.

~~~java
interface OfficeMachine {
    void print(String document);
    String scan();
    void fax(String number, String document);
}

class BasicPrinter implements OfficeMachine {
    @Override
    public void print(String document) {
        System.out.println("Print: " + document);
    }

    @Override
    public String scan() {
        throw new UnsupportedOperationException("Scanning is unavailable");
    }

    @Override
    public void fax(String number, String document) {
        throw new UnsupportedOperationException("Fax is unavailable");
    }
}
~~~

Đây là ví dụ minh họa cho thiết kế có vấn đề. Mã vẫn có thể biên dịch, nhưng hợp đồng `OfficeMachine` tuyên bố rằng mọi implementation đều có thể quét và fax, trong khi `BasicPrinter` không thể thực hiện hai thao tác đó. Client chỉ cần in vẫn phải phụ thuộc vào một interface mô tả cả scan và fax. Khi interface tiếp tục lớn lên, mỗi implementation có thể phải viết thêm phương thức rỗng, ném ngoại lệ hoặc hành vi giả để thỏa mãn hợp đồng.

Những dấu hiệu thường gặp của interface quá lớn:

- Một số implementation chỉ hỗ trợ một phần phương thức, còn các phương thức khác phải để trống hoặc ném `UnsupportedOperationException`.
- Thay đổi một nhóm phương thức có thể khiến nhiều lớp không liên quan phải sửa hoặc biên dịch lại.
- Client nhận một kiểu tổng quát rồi phải kiểm tra kiểu thực tế bằng `instanceof` hoặc ép kiểu để tìm khả năng mà nó cần.
- Test double hoặc mock phải triển khai nhiều phương thức không liên quan đến hành vi đang kiểm thử.
- Tên interface quá chung, chẳng hạn `Manager`, `Service` hay `Worker`, nhưng lại chứa nhiều nhóm trách nhiệm không cùng một vai trò.

Một interface có nhiều phương thức chưa chắc đã vi phạm ISP. Vấn đề xuất hiện khi các client khác nhau phụ thuộc vào những phần hợp đồng khác nhau và phải gánh những phương thức không cần thiết.

## 2. Tách theo khả năng hoặc vai trò của client

Thay vì buộc mọi thiết bị dùng một hợp đồng chung, hãy định nghĩa những interface nhỏ hơn theo các khả năng thực sự có ý nghĩa:

~~~java
interface Printable {
    void print(String document);
}

interface Scannable {
    String scan();
}

interface Faxable {
    void fax(String number, String document);
}
~~~

Giờ đây, máy in cơ bản chỉ triển khai `Printable`. Máy đa chức năng có thể triển khai cả `Printable`, `Scannable` và `Faxable`. Client muốn in chỉ cần nhận một `Printable`, không cần biết thiết bị đó có quét hay fax hay không.

~~~java
class BasicPrinter implements Printable {
    @Override
    public void print(String document) {
        System.out.println("Print: " + document);
    }
}

class OfficeMfp implements Printable, Scannable, Faxable {
    @Override
    public void print(String document) {
        System.out.println("Print: " + document);
    }

    @Override
    public String scan() {
        return "scanned-page";
    }

    @Override
    public void fax(String number, String document) {
        System.out.println("Fax " + document + " to " + number);
    }
}
~~~

Các interface này mô tả hợp đồng mà client cần. Một lớp có thể kết hợp nhiều khả năng bằng cách triển khai nhiều interface; không cần tạo một interface lớn để đại diện cho mọi tổ hợp thiết bị có thể có.

### ISP thay đổi quan hệ phụ thuộc như thế nào?

Trước khi tách, phương thức chỉ in có thể yêu cầu `OfficeMachine`, khiến client phụ thuộc cả scan và fax:

~~~java
void printReport(OfficeMachine machine) {
    machine.print("report.pdf");
}
~~~

Sau khi tách, phương thức chỉ yêu cầu khả năng in:

~~~java
void printReport(Printable printer) {
    printer.print("report.pdf");
}
~~~

Cả `BasicPrinter` và `OfficeMfp` đều có thể truyền vào `printReport`. Client không cần nhánh điều kiện để hỏi thiết bị có hỗ trợ scan hay fax hay không. Hợp đồng tham số thể hiện đúng nhu cầu của phương thức.

## 3. Ví dụ Java 17+ hoàn chỉnh

Lưu đoạn mã sau vào tệp `InterfaceSegregationDemo.java`. Ví dụ dùng các interface lồng trong lớp demo để chỉ cần một tệp nguồn và chạy độc lập.

~~~java
public class InterfaceSegregationDemo {
    interface Printable {
        void print(String document);
    }

    interface Scannable {
        String scan();
    }

    interface Faxable {
        void fax(String number, String document);
    }

    static final class BasicPrinter implements Printable {
        @Override
        public void print(String document) {
            System.out.println("Print: " + document);
        }
    }

    static final class OfficeMfp implements Printable, Scannable, Faxable {
        @Override
        public void print(String document) {
            System.out.println("Print: " + document);
        }

        @Override
        public String scan() {
            return "scanned-page";
        }

        @Override
        public void fax(String number, String document) {
            System.out.println("Fax " + document + " to " + number);
        }
    }

    static void printReport(Printable printer) {
        printer.print("report.pdf");
    }

    static void scanDocument(Scannable scanner) {
        System.out.println("Scan: " + scanner.scan());
    }

    public static void main(String[] args) {
        Printable basicPrinter = new BasicPrinter();
        printReport(basicPrinter);

        OfficeMfp mfp = new OfficeMfp();
        printReport(mfp);
        scanDocument(mfp);
        mfp.fax("555-0100", "invoice.pdf");
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~text
javac --release 17 InterfaceSegregationDemo.java
java InterfaceSegregationDemo
~~~

Kết quả stdout chính xác:

~~~text
Print: report.pdf
Print: report.pdf
Scan: scanned-page
Fax invoice.pdf to 555-0100
~~~

Ví dụ minh họa ba điểm:

1. `BasicPrinter` chỉ cần triển khai `Printable`; nó không khai báo các thao tác mà thiết bị không hỗ trợ.
2. `OfficeMfp` kết hợp ba khả năng bằng cách triển khai ba interface độc lập.
3. `printReport` và `scanDocument` nhận các kiểu khác nhau theo đúng nhu cầu của từng client. Phương thức in không phụ thuộc vào hợp đồng quét hoặc fax.

Các kiểu lồng bên trong `InterfaceSegregationDemo` là những interface và lớp thành viên được khai báo theo cú pháp Java. Chúng được truy cập trong ví dụ qua tên đơn giản vì nằm trong cùng lớp bao. Đây là chương trình chạy đầy đủ; các đoạn ở phần trước là những mảnh mã dùng để giải thích thiết kế.

## 4. ISP liên hệ với tính kết dính và sự thay đổi

Tách interface giúp giảm phạm vi phụ thuộc. Khi client chỉ nhận `Printable`, thay đổi riêng hợp đồng `Scannable` thường không cần thay đổi chữ ký hay logic của client in. Điều này làm cho các phần ít liên quan có thể tiến hóa độc lập hơn.

ISP cũng giúp hợp đồng thể hiện tính kết dính (cohesion): các phương thức nằm cùng một interface nên phục vụ một vai trò hoặc một nhóm client có nhu cầu gần nhau. Nếu hai nhóm client thay đổi vì những lý do khác nhau và chỉ sử dụng các nhóm phương thức riêng biệt, đó là tín hiệu để xem xét tách interface.

| Khía cạnh | Interface quá lớn | Interface được phân tách |
| --- | --- | --- |
| Hợp đồng client | Phụ thuộc nhiều khả năng cùng lúc | Phụ thuộc khả năng đang cần |
| Implementation | Có thể phải cung cấp phương thức không hỗ trợ | Chỉ triển khai khả năng thật sự có |
| Thay đổi API | Dễ ảnh hưởng đến lớp không liên quan | Thường giới hạn ở nhóm client liên quan |
| Kiểm thử | Mock có thể cần nhiều phương thức dư thừa | Mock nhỏ và sát hành vi cần kiểm tra |
| Kết hợp chức năng | Một interface đại diện mọi tổ hợp | Lớp kết hợp nhiều interface khi cần |

ISP không đảm bảo mọi thay đổi chỉ ảnh hưởng một lớp. Nếu client thực sự cần nhiều khả năng hoặc nhiều interface cùng thay đổi vì một khái niệm nghiệp vụ chung, chúng vẫn có thể phụ thuộc lẫn nhau. Nguyên tắc này giúp chọn đúng ranh giới phụ thuộc thay vì cố gắng loại bỏ mọi quan hệ.

## 5. Phân biệt ISP với các nguyên tắc gần nhau

**Single Responsibility Principle (SRP)** tập trung vào lý do để một module hoặc lớp thay đổi. **Interface Segregation Principle (ISP)** tập trung vào hợp đồng mà client phụ thuộc vào. Hai nguyên tắc liên quan nhưng không đồng nghĩa: một lớp có thể có trách nhiệm rõ ràng nhưng vẫn triển khai một interface quá rộng; ngược lại, một interface nhỏ không tự bảo đảm implementation chỉ có một trách nhiệm.

**Dependency Inversion Principle (DIP)** nói về việc các module cấp cao và cấp thấp phụ thuộc vào abstraction phù hợp. ISP giúp làm các abstraction đó nhỏ và sát nhu cầu client. Một interface nhỏ vẫn có thể được dùng sai nếu client cấp cao phụ thuộc vào abstraction không phù hợp.

**Open/Closed Principle (OCP)** khuyến khích mở rộng hành vi mà không phải liên tục sửa mã đã ổn định. Các interface theo khả năng có thể giúp bổ sung implementation mới dễ hơn, nhưng việc tách interface tự nó không bảo đảm OCP.

Có thể ghi nhớ: ISP hỏi “client có phải phụ thuộc vào những phương thức nó không cần không?”, còn SRP hỏi “module này có những lý do thay đổi không liên quan nào?”.

## 6. Không nên tách interface một cách máy móc

ISP không yêu cầu tạo một interface riêng cho mọi phương thức. Việc tách quá mức làm tăng số kiểu cần hiểu, tên gọi, quan hệ giữa các abstraction và chi phí bảo trì. Các phương thức nên nằm cùng nhau nếu chúng biểu đạt một hợp đồng có ý nghĩa và thường được client sử dụng cùng nhau.

Ví dụ, một hợp đồng `InputStream` không trở nên tốt hơn chỉ vì ta tách `read()` và `close()` thành các interface rời rạc trong mọi trường hợp. Hai thao tác có thể thuộc cùng một abstraction vì vòng đời và cách sử dụng của luồng gắn bó với nhau. Quyết định cần dựa vào nhu cầu client và ngữ nghĩa của hợp đồng, không chỉ dựa vào số lượng phương thức.

Tránh những kiểu tách sau:

- **Một interface cho mỗi getter hoặc setter** khi chúng chỉ tạo ra nhiều kiểu vụn vặt mà không biểu đạt vai trò độc lập.
- **Interface rỗng hoặc tên quá mơ hồ** chỉ để ép lớp vào một cấu trúc phân cấp.
- **Tách nhưng vẫn yêu cầu client phụ thuộc tất cả interface** thông qua một tham số hoặc abstraction tổng hợp không cần thiết.
- **Dùng `instanceof` hoặc ép kiểu khắp nơi** để tìm các khả năng bị che giấu. Nếu client cần một khả năng, nên yêu cầu trực tiếp interface thể hiện khả năng đó.
- **Giữ phương thức giả** trả về `null`, làm việc không có tác dụng hoặc ném ngoại lệ chỉ để lớp thỏa mãn interface.
- **Tách các thao tác có quan hệ nghiệp vụ chặt chẽ** khiến client phải tự điều phối một quy trình mà lẽ ra phải là một hợp đồng nhất quán.

Một interface tổng hợp vẫn hữu ích nếu một client cụ thể thực sự cần toàn bộ các khả năng. Có thể định nghĩa `interface MultifunctionDevice extends Printable, Scannable, Faxable {}` để đặt tên cho tổ hợp đó. Các client chỉ in vẫn nên nhận `Printable`; chúng không cần phụ thuộc `MultifunctionDevice`.

## 7. Thiết kế interface theo nhu cầu client

Một quy trình thực tế để áp dụng ISP:

1. **Liệt kê các client.** Xác định các lớp, dịch vụ hoặc use case gọi đến hợp đồng hiện tại.
2. **Đánh dấu phương thức được dùng.** Lập bảng client–phương thức để thấy các nhóm nhu cầu thực tế; không giả định mọi client cần mọi phương thức.
3. **Tìm nhóm hợp đồng có ý nghĩa.** Gom các phương thức theo vai trò, khả năng hoặc nhóm client liên quan.
4. **Tạo interface chuyên biệt.** Chọn tên diễn đạt hành vi mà client yêu cầu, ví dụ `Printable`, `Scannable`, `Clock` hoặc `MessageSender`.
5. **Điều chỉnh implementation.** Cho mỗi lớp triển khai các interface tương ứng với khả năng thực tế. Nếu một lớp hỗ trợ nhiều vai trò, triển khai nhiều interface.
6. **Thu hẹp kiểu tham số và trường.** Để client phụ thuộc interface nhỏ nhất đáp ứng được công việc của nó.
7. **Kiểm thử các hành vi quan trọng.** Kiểm tra client chỉ gọi đúng khả năng cần dùng và các implementation thực hiện đúng hợp đồng.
8. **Xem lại API công khai.** Nếu interface hiện tại đã được bên khác sử dụng, lên kế hoạch chuyển đổi tương thích thay vì xóa hoặc đổi hợp đồng một cách đột ngột.

Ví dụ bảng nhu cầu ban đầu có thể trông như sau:

| Client | In | Quét | Fax |
| --- | --- | --- | --- |
| `ReportPrinter` | Có | Không | Không |
| `ArchiveService` | Không | Có | Không |
| `LegacyFaxSender` | Không | Không | Có |
| `OfficeConsole` | Có | Có | Có |

Ba client đầu chỉ cần các interface tương ứng với một khả năng. `OfficeConsole` có thể nhận nhiều interface hoặc nhận một abstraction tổng hợp nếu thiết kế thực sự cần cả ba. Bảng này là công cụ phân tích nhu cầu, không phải quy tắc rằng mỗi cột bắt buộc phải trở thành interface mới.

## 8. ISP trong kiểm thử và mở rộng

Interface nhỏ thường làm cho test double dễ viết hơn. Nếu một dịch vụ chỉ cần in báo cáo, test có thể cung cấp một implementation giả của `Printable` mà không cần tạo các hành vi scan và fax. Test vì vậy tập trung vào hợp đồng thực sự liên quan.

Tương tự, khi thêm một loại thiết bị mới, lớp mới có thể triển khai đúng các khả năng mà nó hỗ trợ. Một máy in mạng chỉ cần `Printable`; một bộ chuyển tài liệu có thể cần `Scannable`; máy đa chức năng triển khai nhiều interface. Không cần sửa mọi implementation hiện có chỉ vì hệ thống bổ sung một khả năng độc lập.

Tuy vậy, ISP không thay thế kiểm thử hợp đồng. Một lớp khai báo `implements Printable` vẫn có thể in sai hoặc không tuân theo ý nghĩa của phương thức. Interface xác định hình dạng của hợp đồng ở mức kiểu; kiểm thử và tài liệu xác định hành vi mong đợi.

## 9. Lưu ý khi thay đổi một API đã tồn tại

Nếu một thư viện công khai đã có `OfficeMachine`, xóa interface hoặc thay đổi chữ ký phương thức có thể phá vỡ mã của người dùng thư viện. Trong trường hợp đó, hãy xem xét:

- Bổ sung các interface nhỏ như `Printable` và `Scannable`, rồi để implementation hiện có triển khai chúng.
- Cung cấp adapter giúp client chuyển từ hợp đồng cũ sang hợp đồng mới.
- Giữ interface cũ trong một giai đoạn chuyển tiếp nếu yêu cầu tương thích nhị phân hoặc nguồn.
- Đánh dấu API cũ là deprecated khi có chiến lược loại bỏ rõ ràng, đồng thời hướng dẫn người dùng chuyển sang hợp đồng mới.
- Không âm thầm thay đổi ý nghĩa phương thức cũ hoặc trả về kết quả giả chỉ để phù hợp với thiết kế mới.

Việc giữ interface cũ và cách nối nó với các interface mới phụ thuộc vào API thực tế. Không có một cách chuyển đổi tương thích duy nhất cho mọi thư viện; hãy kiểm tra các client đang tồn tại và mức tương thích mà dự án cam kết.

## 10. Những lỗi thiết kế thường gặp và cách xử lý

| Dấu hiệu | Nguyên nhân có thể | Hướng xử lý |
| --- | --- | --- |
| Nhiều implementation ném `UnsupportedOperationException` | Interface đòi hỏi khả năng mà một số lớp không có | Tách hợp đồng theo khả năng; để lớp chỉ triển khai khả năng được hỗ trợ |
| Client ép kiểu sang lớp cụ thể | Kiểu tham số không diễn đạt khả năng mà client thực sự cần | Đưa khả năng vào interface riêng và nhận interface đó ở tham số |
| Thêm phương thức vào interface khiến nhiều lớp không liên quan phải sửa | Các client hoặc implementation phụ thuộc một hợp đồng quá rộng | Kiểm tra nhóm phương thức và phân tách theo nhu cầu ổn định |
| Có quá nhiều interface một phương thức | Tách theo cú pháp mà không dựa trên vai trò | Gộp những phương thức có ngữ nghĩa và nhu cầu sử dụng gắn bó |
| Interface tổng hợp xuất hiện ở khắp mọi nơi | Mọi client đều phụ thuộc vào tổ hợp lớn dù chỉ dùng một phần | Chỉ dùng kiểu tổng hợp cho client cần tất cả khả năng |
| Sửa API gây lỗi cho người dùng thư viện | Thay đổi hợp đồng công khai không có lộ trình tương thích | Bổ sung interface, adapter hoặc kế hoạch deprecation phù hợp |

Một `default` method trong Java có thể giúp một interface mở rộng về mặt tương thích trong một số trường hợp, nhưng không tự giải quyết vấn đề thiết kế. Nếu client vẫn phụ thuộc vào một hợp đồng lớn với nhiều khả năng không liên quan, cần xem xét lại ranh giới interface. Tương tự, chuyển phương thức thành `default` rồi để implementation không làm gì có thể che dấu vấn đề thay vì sửa nó.

## 11. Checklist trước khi chấp nhận một interface

- Tên interface có mô tả một vai trò hoặc một khả năng cụ thể không?
- Client sử dụng interface có phải biết đến phương thức mà nó không cần không?
- Các implementation có thể thực hiện đầy đủ ý nghĩa của hợp đồng mà chúng khai báo không?
- Có phương thức nào thường xuyên để trống, trả kết quả giả hoặc ném ngoại lệ vì không được hỗ trợ không?
- Các client khác nhau có dùng những nhóm phương thức tách biệt không?
- Có cần một interface tổng hợp cho một số client, trong khi các client khác chỉ cần interface nhỏ không?
- Việc tách có làm tăng số abstraction một cách khó hiểu mà không giảm phụ thuộc thực tế không?
- Nếu interface công khai đang được sử dụng, kế hoạch chuyển đổi có bảo vệ mức tương thích cần thiết không?
- Test có thể cung cấp implementation đơn giản của hợp đồng cần dùng mà không giả lập nhiều khả năng dư thừa không?

ISP là nguyên tắc thiết kế ranh giới phụ thuộc. Hãy để client khai báo nhu cầu bằng kiểu interface nhỏ và có ý nghĩa; để lớp triển khai kết hợp những khả năng mà nó thật sự hỗ trợ. Cách làm này giúp hợp đồng dễ hiểu, implementation trung thực với năng lực của nó và các thay đổi ít lan sang những phần không liên quan.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstraction-choices-008">Đảo ngược phụ thuộc bằng tham số hoặc constructor</a>

<details>
<summary>Xem chi tiết</summary>

# Đảo ngược phụ thuộc bằng tham số hoặc constructor trong Java

Đảo ngược phụ thuộc (Dependency Inversion Principle, DIP) là một trong năm nguyên tắc SOLID. Ý tưởng chính là **mã nghiệp vụ cấp cao nên phụ thuộc vào abstraction (giao diện hoặc lớp trừu tượng), còn chi tiết cấp thấp cũng triển khai hoặc phụ thuộc vào abstraction đó**. Nhờ vậy, logic nghiệp vụ không bị khóa vào một cách gửi thông báo, lưu dữ liệu hay gọi dịch vụ cụ thể.

Trong Java, có thể truyền dependency vào một đối tượng qua constructor hoặc truyền vào một phương thức qua tham số. Đây là các cách tiêm phụ thuộc (dependency injection, DI). Chúng giúp áp dụng DIP, nhưng bản thân việc truyền tham số chưa đủ để bảo đảm kiến trúc tuân thủ DIP: kiểu tham số và hướng phụ thuộc vẫn cần được thiết kế đúng.

## 1. Vấn đề khi phụ thuộc trực tiếp vào lớp cụ thể

Giả sử dịch vụ cảnh báo phải gửi thông báo qua email:

~~~java
final class AlertService {
    private final EmailChannel email = new EmailChannel();

    void alert(String recipient, String message) {
        email.send(recipient, message);
    }
}
~~~

Lớp `AlertService` tự tạo `EmailChannel`. Nó vừa quyết định khi nào gửi cảnh báo, vừa quyết định sử dụng cơ chế gửi nào. Nếu muốn gửi SMS, dùng một nhà cung cấp khác hoặc kiểm thử mà không gửi email thật, ta phải sửa hoặc thay thế cách tạo dependency bên trong dịch vụ.

Vấn đề không nằm ở việc `EmailChannel` là một class. Vấn đề là logic cấp cao bị gắn chặt với một chi tiết triển khai. Một abstraction nhỏ cho phép dịch vụ chỉ biết rằng nó có thể gửi thông báo, không cần biết cách gửi cụ thể.

## 2. Tách hợp đồng bằng interface

Định nghĩa một interface mô tả khả năng mà nghiệp vụ cần:

~~~java
interface NotificationChannel {
    void send(String recipient, String message);
}
~~~

Các cách gửi khác nhau triển khai cùng hợp đồng:

~~~java
final class EmailChannel implements NotificationChannel {
    @Override
    public void send(String recipient, String message) {
        System.out.println("EMAIL to " + recipient + ": " + message);
    }
}

final class SmsChannel implements NotificationChannel {
    @Override
    public void send(String recipient, String message) {
        System.out.println("SMS to " + recipient + ": " + message);
    }
}
~~~

Giờ `AlertService` có thể phụ thuộc vào `NotificationChannel`. `EmailChannel` và `SmsChannel` là các chi tiết cụ thể cùng triển khai abstraction. Khi thêm một kênh khác, chẳng hạn push notification, chỉ cần triển khai interface nếu hợp đồng hiện tại đáp ứng nhu cầu.

## 3. Tiêm dependency qua constructor

Constructor injection truyền dependency vào lúc tạo đối tượng. Đây thường là lựa chọn phù hợp khi dependency là bắt buộc và cần được giữ ổn định trong suốt vòng đời đối tượng.

~~~java
import java.util.Objects;

final class AlertService {
    private final NotificationChannel channel;

    AlertService(NotificationChannel channel) {
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
    }

    void alert(String recipient, String message) {
        channel.send(recipient, message);
    }
}
~~~

Các điểm quan trọng:

- Trường `channel` khai báo theo interface, không theo `EmailChannel` hay `SmsChannel`.
- Constructor nhận dependency từ bên ngoài. `AlertService` không tự gọi `new EmailChannel()`.
- Trường `final` giúp dependency không bị thay thế sau khi đối tượng được khởi tạo.
- `Objects.requireNonNull` làm lỗi cấu hình lộ ra ngay khi tạo đối tượng, thay vì phát sinh muộn khi gửi thông báo.

Nơi khởi tạo ứng dụng quyết định implementation nào được dùng:

~~~java
AlertService emailAlerts = new AlertService(new EmailChannel());
AlertService smsAlerts = new AlertService(new SmsChannel());
~~~

Cùng một lớp nghiệp vụ có thể được cấu hình bằng hai implementation khác nhau. Khi muốn thay đổi kênh mặc định, phần cấu hình khởi tạo đổi cách truyền dependency; logic bên trong `AlertService` vẫn giữ nguyên.

## 4. Tiêm dependency qua tham số phương thức

Đôi khi dependency chỉ cần cho một thao tác, hoặc mỗi lần gọi cần một implementation khác nhau. Khi đó, có thể truyền abstraction qua tham số:

~~~java
final class NotificationWorkflow {
    static void sendOnce(
            NotificationChannel channel,
            String recipient,
            String message) {
        Objects.requireNonNull(channel, "channel must not be null")
               .send(recipient, message);
    }
}
~~~

Ví dụ sử dụng:

~~~java
NotificationWorkflow.sendOnce(
        new EmailChannel(), "ops@example.com", "Daily report ready");
NotificationWorkflow.sendOnce(
        new SmsChannel(), "+84901234567", "Deployment started");
~~~

Phương thức chỉ yêu cầu một `NotificationChannel`; nó không cần biết implementation cụ thể. Người gọi chọn dependency phù hợp cho từng lần thực thi. Vì ví dụ sử dụng `Objects.requireNonNull`, file hoàn chỉnh ở phần tiếp theo có import `java.util.Objects`.

Constructor injection và parameter injection giải quyết hai nhu cầu khác nhau:

| Cách truyền | Phù hợp khi | Đặc điểm |
|---|---|---|
| Constructor | Dependency bắt buộc cho hầu hết hành vi của đối tượng | Có sẵn sau khi khởi tạo; dễ giữ bất biến và dễ nhận ra dependency cần thiết |
| Tham số phương thức | Dependency chỉ cần cho một thao tác hoặc thay đổi theo lần gọi | Phạm vi sử dụng hẹp; người gọi chọn implementation ở từng lần gọi |
| Setter | Dependency thực sự tùy chọn hoặc có thể cấu hình lại sau khi tạo | Đối tượng có thể tồn tại ở trạng thái chưa được cấu hình; cần quản lý tính hợp lệ cẩn thận |

Không nên chọn parameter injection chỉ vì nó có vẻ linh hoạt hơn. Nếu mọi thao tác của dịch vụ đều cần cùng một dependency bắt buộc, constructor thường biểu đạt ý định rõ ràng hơn. Ngược lại, nếu mỗi lần gọi cần một chiến lược riêng, truyền qua tham số có thể tự nhiên hơn.

## 5. Ví dụ hoàn chỉnh chạy được trên Java 17

Lưu mã dưới đây vào file `DependencyInversionDemo.java`. Ví dụ minh họa constructor injection và parameter injection trong một chương trình độc lập.

~~~java
import java.util.Objects;

public class DependencyInversionDemo {
    public static void main(String[] args) {
        AlertService emailAlerts = new AlertService(new EmailChannel());
        emailAlerts.alert("linh@example.com", "Build succeeded");

        AlertService smsAlerts = new AlertService(new SmsChannel());
        smsAlerts.alert("+84901234567", "Deployment started");

        NotificationWorkflow.sendOnce(
                new EmailChannel(), "ops@example.com", "Daily report ready");
    }
}

interface NotificationChannel {
    void send(String recipient, String message);
}

final class EmailChannel implements NotificationChannel {
    @Override
    public void send(String recipient, String message) {
        System.out.println("EMAIL to " + recipient + ": " + message);
    }
}

final class SmsChannel implements NotificationChannel {
    @Override
    public void send(String recipient, String message) {
        System.out.println("SMS to " + recipient + ": " + message);
    }
}

final class AlertService {
    private final NotificationChannel channel;

    AlertService(NotificationChannel channel) {
        this.channel = Objects.requireNonNull(
                channel, "channel must not be null");
    }

    void alert(String recipient, String message) {
        channel.send(recipient, message);
    }
}

final class NotificationWorkflow {
    static void sendOnce(
            NotificationChannel channel,
            String recipient,
            String message) {
        Objects.requireNonNull(channel, "channel must not be null")
               .send(recipient, message);
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 DependencyInversionDemo.java
java DependencyInversionDemo
~~~

Stdout chính xác:

~~~text
EMAIL to linh@example.com: Build succeeded
SMS to +84901234567: Deployment started
EMAIL to ops@example.com: Daily report ready
~~~

Tất cả các kiểu phụ trợ không khai báo `public` nên có thể nằm cùng file với lớp `public` `DependencyInversionDemo`. Lệnh biên dịch trên dùng Java 17 làm phiên bản đích và không cần framework DI.

## 6. Vì sao đây là đảo ngược phụ thuộc?

Có hai hướng phụ thuộc cần quan sát:

- **Trước khi tách abstraction:** `AlertService` biết và tự tạo `EmailChannel`. Thay đổi chi tiết gửi email có thể buộc phải sửa lớp nghiệp vụ.
- **Sau khi tách abstraction:** `AlertService` chỉ tham chiếu `NotificationChannel`. `EmailChannel` và `SmsChannel` triển khai interface; phần khởi tạo bên ngoài kết nối một implementation với dịch vụ.

Sơ đồ quan hệ sau khi thiết kế lại (mũi tên thể hiện phụ thuộc ở mã nguồn hoặc thao tác tại nơi khởi tạo):

~~~text
AlertService --------- sử dụng --------> NotificationChannel
                                            ^              ^
                                            | implements   | implements
                                       EmailChannel     SmsChannel

CompositionRoot --- tạo --------> EmailChannel hoặc SmsChannel
CompositionRoot --- truyền -----> new AlertService(channel)
~~~

`AlertService` chỉ biết kiểu `NotificationChannel`. Hai channel cụ thể triển khai hợp đồng đó, còn `CompositionRoot` là phần mã ở `main` quyết định tạo channel nào và truyền nó vào constructor. Hướng phụ thuộc mã nguồn không bị đảo sai: chính `AlertService` phụ thuộc vào interface, còn implementation cũng phụ thuộc vào cùng interface.

DIP là nguyên tắc kiến trúc về hướng phụ thuộc. DI là kỹ thuật cung cấp một đối tượng mà lớp cần dùng. Constructor hoặc parameter injection có thể hỗ trợ DIP khi dependency được khai báo qua abstraction. Nếu constructor nhận trực tiếp `EmailChannel`, dù dependency được truyền từ ngoài vào, lớp vẫn bị gắn với chi tiết email.

## 7. Kiểm thử dễ hơn khi dependency được tiêm vào

Có thể truyền một implementation giả (fake) hoặc mock để kiểm tra logic mà không gọi mạng, gửi email thật hay phụ thuộc vào nhà cung cấp bên ngoài. Ví dụ fake đơn giản:

~~~java
final class RecordingChannel implements NotificationChannel {
    String lastRecipient;
    String lastMessage;

    @Override
    public void send(String recipient, String message) {
        lastRecipient = recipient;
        lastMessage = message;
    }
}
~~~

Trong bài kiểm thử, tạo `RecordingChannel`, truyền nó vào `AlertService`, gọi `alert`, rồi kiểm tra `lastRecipient` và `lastMessage`. Vì fake triển khai cùng interface, lớp nghiệp vụ không cần nhánh đặc biệt dành riêng cho môi trường kiểm thử.

Đây là một lợi ích lớn của việc phụ thuộc vào abstraction: hành vi bên ngoài có thể được thay thế trong kiểm thử, còn logic nghiệp vụ được kiểm tra một cách có kiểm soát.

## 8. Những lỗi thiết kế thường gặp

**Tự tạo lớp cụ thể bên trong dịch vụ.** Nếu `AlertService` vẫn gọi `new EmailChannel()`, việc thêm constructor nhận interface không giải quyết triệt để coupling nếu dependency được bỏ qua hoặc ghi đè bằng một đối tượng tự tạo bên trong.

**Truyền lớp cụ thể thay vì abstraction.** Tham số `EmailChannel channel` giới hạn người gọi vào một implementation. Nếu nghiệp vụ cần nhiều loại kênh, nên nhận `NotificationChannel`.

**Nhầm DI với DIP.** Nhận dependency từ constructor chưa đủ. Nếu kiểu của dependency là lớp triển khai cụ thể, hoặc module cấp cao vẫn phụ thuộc vào chi tiết cấp thấp, nguyên tắc đảo ngược phụ thuộc chưa được thể hiện đầy đủ.

**Dùng setter cho dependency bắt buộc.** Đối tượng có thể được tạo rồi gọi trước khi setter cấu hình dependency. Với dependency cần thiết để đối tượng hoạt động hợp lệ, constructor thường an toàn hơn.

**Cho phép dependency null.** Nếu null không phải một trạng thái hợp lệ, kiểm tra ngay tại điểm tiếp nhận. Cần thống nhất hợp đồng nullability thay vì để lỗi xảy ra sâu bên trong xử lý.

**Tạo abstraction không có giá trị.** Không cần thêm interface cho mọi class chỉ để tuân thủ một mẫu. Abstraction hữu ích khi biểu đạt một hợp đồng ổn định, tách chính sách nghiệp vụ khỏi chi tiết triển khai hoặc cho phép thay thế/kiểm thử có ý nghĩa.

**Đưa việc lựa chọn implementation vào khắp nơi.** Nếu nhiều lớp nghiệp vụ tự quyết định bằng `new EmailChannel()` hoặc `new SmsChannel()`, coupling vẫn lan rộng. Tập trung việc tạo và kết nối các đối tượng ở nơi cấu hình ứng dụng, thường được gọi là composition root.

## 9. Có cần framework dependency injection không?

Không. Java hỗ trợ truyền đối tượng trực tiếp qua constructor và tham số phương thức; ví dụ ở trên không dùng thư viện ngoài.

Trong ứng dụng lớn, framework DI có thể tự tạo dependency, quản lý vòng đời và kết nối các thành phần theo cấu hình. Framework thay đổi cách đối tượng được tạo và lắp ghép, nhưng không thay thế việc thiết kế đúng abstraction. Một lớp nhận trực tiếp lớp triển khai cụ thể vẫn có coupling đó dù được framework khởi tạo.

## 10. Checklist áp dụng

- Xác định phần nghiệp vụ cấp cao cần khả năng gì, rồi biểu diễn khả năng đó bằng interface hoặc abstraction phù hợp.
- Để các lớp cụ thể triển khai hợp đồng và chứa chi tiết thực thi.
- Dùng constructor cho dependency bắt buộc, ổn định theo vòng đời đối tượng.
- Dùng tham số phương thức khi dependency chỉ cần cho một thao tác hoặc lựa chọn thay đổi theo lần gọi.
- Tạo và kết nối implementation ở nơi khởi tạo ứng dụng, thay vì rải `new ConcreteClass()` trong logic nghiệp vụ.
- Kiểm tra nullability, quyền sở hữu và vòng đời của dependency theo hợp đồng của ứng dụng.
- Viết kiểm thử bằng fake hoặc mock để xác nhận hành vi mà không gọi hệ thống bên ngoài.
- Xem xét liệu việc thay một implementation có buộc phải sửa lớp nghiệp vụ hay không; nếu có, kiểm tra lại hướng phụ thuộc.

Thiết kế hướng abstraction giúp mã nghiệp vụ ổn định trước các thay đổi của chi tiết kỹ thuật. Constructor injection làm dependency bắt buộc trở nên rõ ràng; parameter injection giúp một thao tác sử dụng implementation do bên gọi lựa chọn. Cả hai đều hữu ích khi được dùng đúng phạm vi và kết hợp với hợp đồng abstraction được thiết kế hợp lý.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstraction-choices-009">Tránh tạo interface cho mọi lớp khi chưa có nhu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh tạo interface cho mọi lớp khi chưa có nhu cầu

Trong Java, `interface` là công cụ để mô tả một hợp đồng mà nhiều kiểu có thể thực hiện. Tuy nhiên, **không phải lớp nào cũng cần một interface riêng**. Nếu tạo cặp `SomeService`/`SomeServiceImpl` cho mọi lớp dù không có nhu cầu thay thế, mở rộng hay tách ranh giới phụ thuộc, mã nguồn có thể nhiều tầng hơn nhưng không linh hoạt hơn.

Nguyên tắc thực tế là: **bắt đầu bằng thiết kế đơn giản nhất đáp ứng nhu cầu hiện tại; thêm interface khi nó thể hiện một ranh giới hoặc khả năng thay thế có ý nghĩa**. Đây là cách áp dụng YAGNI (*You Aren’t Gonna Need It*): đừng xây dựng tính linh hoạt giả định chỉ vì có thể sẽ cần trong tương lai. Điều đó không có nghĩa là cấm interface có một implementation; điều quan trọng là lý do thiết kế.

## 1. Interface giải quyết vấn đề gì?

Interface khai báo những thao tác mà một kiểu cam kết cung cấp, nhưng không buộc người gọi phụ thuộc vào một cách triển khai cụ thể.

Ví dụ, `PaymentGateway` có thể định nghĩa thao tác thanh toán. Một implementation dùng cổng thanh toán thật, implementation khác dùng sandbox, và test có thể dùng fake để ghi nhận lời gọi. `OrderService` chỉ cần biết hợp đồng thanh toán, không cần biết nhà cung cấp cụ thể.

Ngược lại, nếu một lớp chỉ định dạng chuỗi theo một quy tắc ổn định, chỉ có một người gọi và không có nhu cầu thay đổi hành vi, việc tạo `Formatter` interface, `DefaultFormatter` implementation và thêm lớp chuyển tiếp chỉ để gọi một method có thể tạo thêm tên kiểu, file và bước điều hướng mà không giải quyết vấn đề thực tế.

Interface hữu ích khi nó tạo ra **khả năng thay thế thực sự**, ranh giới giữa các thành phần, hoặc hợp đồng công khai. Nó không tự động làm thiết kế tốt hơn chỉ vì mã đã “trừu tượng hóa”.

## 2. Vì sao không nên tạo interface một cách máy móc?

- **Tăng indirection:** người đọc phải lần từ interface sang implementation để hiểu hành vi, dù chỉ có một đường thực thi duy nhất.
- **Tăng số kiểu và file:** một lớp đơn giản có thể biến thành interface, implementation mặc định, factory và lớp ủy quyền không cần thiết.
- **Tạo điểm mở rộng giả:** nhiều implementation chưa chắc sẽ tồn tại; API linh hoạt dựa trên giả định chưa được kiểm chứng có thể khiến thiết kế khó thay đổi hơn.
- **Tăng chi phí bảo trì:** khi hợp đồng thay đổi, mọi implementation và nơi cấu hình có thể phải cập nhật theo.
- **Làm test quá phụ thuộc cấu trúc:** nếu mock mọi lớp nội bộ, test dễ kiểm tra cách cài đặt hơn là kết quả/hành vi mà người dùng quan tâm.
- **Làm mờ trách nhiệm:** interface không có ý nghĩa nghiệp vụ hoặc ranh giới rõ ràng khiến tên gọi và vai trò của các thành phần khó hiểu.

Đây không phải lý do để tránh interface nói chung. Đó là lý do để yêu cầu một lợi ích cụ thể trước khi thêm một tầng trừu tượng.

## 3. Khi nào interface đáng được tạo?

### 3.1 Có nhiều cách triển khai thực sự

Khi cùng một hợp đồng có các implementation với hành vi hoặc cơ chế khác nhau, interface giúp caller chọn implementation mà không phải chứa logic `if/else` theo từng lớp cụ thể.

Ví dụ: `FileStorage` và `CloudStorage`, nhiều chính sách tính phí, nhiều chiến lược sắp xếp, hoặc các nhà cung cấp thanh toán khác nhau. Nhiều implementation phải cùng tuân thủ một hợp đồng ổn định; nếu không, chỉ có nhiều class chưa chắc đã cần interface.

### 3.2 Có ranh giới với hệ thống bên ngoài

Database, HTTP client, email gateway, hàng đợi hoặc dịch vụ thanh toán là những phụ thuộc có thể thay đổi theo môi trường và khó chạy trong unit test. Một interface ở ranh giới đó giúp logic nghiệp vụ không phụ thuộc trực tiếp vào thư viện hoặc nhà cung cấp.

Không nhất thiết phải tạo interface cho mọi wrapper quanh thư viện. Hãy đặt hợp đồng ở nơi nó có ý nghĩa với thành phần sử dụng, và chỉ đưa ra các thao tác mà thành phần đó thực sự cần.

### 3.3 Có API công khai, plugin hoặc điểm mở rộng được thiết kế rõ

Nếu mã khác sẽ triển khai hợp đồng, hoặc hệ thống hỗ trợ plugin/extension, interface có thể là phần API cần ổn định. Trong trường hợp này, dù ban đầu chỉ có một implementation, hợp đồng vẫn có giá trị vì nó là ranh giới được chủ động công bố.

### 3.4 Có nhu cầu thay thế phụ thuộc trong test

Interface hữu ích khi fake hoặc stub giúp test hành vi của thành phần mà không cần khởi động hệ thống bên ngoài. Tuy vậy, không nên suy luận rằng “muốn test được thì mọi lớp phải có interface”. Các hàm thuần, lớp nhỏ có hành vi xác định và nhiều thành phần nội bộ có thể test trực tiếp. Chỉ tách hợp đồng ở ranh giới mà việc thay thế mang lại giá trị rõ ràng.

### 3.5 Cần phụ thuộc vào hợp đồng thay vì chi tiết triển khai

Theo nguyên tắc đảo ngược phụ thuộc (*Dependency Inversion Principle*), chính sách cấp cao không nên bị ràng buộc chặt vào chi tiết cấp thấp; cả hai nên phụ thuộc vào abstraction phù hợp. Nhưng điều này **không đồng nghĩa mọi dependency đều phải là interface**. Một lớp cụ thể nhỏ, ổn định và không tạo ra coupling đáng lo có thể là lựa chọn đơn giản, dễ đọc hơn.

## 4. Khi nào nên giữ lớp cụ thể?

Thường nên bắt đầu với một class cụ thể khi phần lớn các điều kiện sau đúng:

- Có một hành vi rõ ràng, ổn định và hiện chỉ có một cách thực hiện hợp lý.
- Lớp không phải API mở rộng cho mã bên ngoài và không nằm ở ranh giới dễ thay đổi.
- Không có caller thực tế nào cần chọn hoặc hoán đổi implementation.
- Thêm interface sẽ chỉ đổi tên kiểu, chuyển tiếp lời gọi hoặc sao chép đúng một hợp đồng mà chưa có mục đích độc lập.
- Test có thể xác minh kết quả qua API công khai của lớp mà không cần giả lập chi tiết nội bộ.

Ví dụ: `ReceiptFormatter` chỉ ghép mã đơn hàng thành dòng biên nhận theo một quy tắc đơn giản. Nếu chưa có nhu cầu chọn định dạng theo vùng, kênh hoặc chính sách, một class cụ thể là đủ. Khi xuất hiện yêu cầu thực tế như nhiều định dạng biên nhận có thể hoán đổi, lúc đó có thể trích xuất `ReceiptFormatter` interface với những tên và phương thức phản ánh nhu cầu mới.

Không nên dùng “chỉ có một implementation” như quy tắc tuyệt đối. Một interface một implementation vẫn hợp lý nếu nó là port ở ranh giới hạ tầng, API công khai hoặc seam thay thế quan trọng. Ngược lại, hai implementation chỉ khác tên nhưng không có nhu cầu thay thế cũng chưa chắc tạo nên một abstraction tốt.

## 5. Ví dụ Java 17+: trừu tượng hóa đúng ranh giới

Trong ví dụ sau, `PaymentGateway` là interface vì cách thực hiện thanh toán có thể thay đổi hoặc được thay thế khi test. `ReceiptFormatter` vẫn là class cụ thể vì quy tắc định dạng hiện tại đơn giản và ổn định. `OrderService` phụ thuộc vào hợp đồng thanh toán, nhưng không tạo interface riêng cho mọi collaborator.

Lưu đoạn mã đầy đủ vào `InterfaceWhenNeededDemo.java`:

```java
public class InterfaceWhenNeededDemo {
    @FunctionalInterface
    interface PaymentGateway {
        void charge(String orderId, int amountCents);
    }

    static final class SandboxPaymentGateway implements PaymentGateway {
        @Override
        public void charge(String orderId, int amountCents) {
            System.out.println("Charged " + orderId + ": "
                    + amountCents + " cents");
        }
    }

    static final class ReceiptFormatter {
        String format(String orderId) {
            return "Receipt for " + orderId;
        }
    }

    static final class OrderService {
        private final PaymentGateway paymentGateway;
        private final ReceiptFormatter receiptFormatter =
                new ReceiptFormatter();

        OrderService(PaymentGateway paymentGateway) {
            this.paymentGateway = paymentGateway;
        }

        void checkout(String orderId, int amountCents) {
            paymentGateway.charge(orderId, amountCents);
            System.out.println(receiptFormatter.format(orderId));
        }
    }

    public static void main(String[] args) {
        OrderService service =
                new OrderService(new SandboxPaymentGateway());
        service.checkout("ORD-42", 1250);
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac InterfaceWhenNeededDemo.java
java InterfaceWhenNeededDemo
```

Stdout chính xác:

```text
Charged ORD-42: 1250 cents
Receipt for ORD-42
```

### Đọc thiết kế trong ví dụ

- `OrderService` gọi `PaymentGateway.charge(...)` thay vì phụ thuộc trực tiếp vào `SandboxPaymentGateway`. Một implementation khác có thể thay thế mà không cần sửa logic checkout.
- `SandboxPaymentGateway` là implementation chạy minh họa. Trong hệ thống thật, adapter có thể gọi nhà cung cấp thanh toán; không nên coi sandbox là giao dịch tiền thật.
- `ReceiptFormatter` được dùng trực tiếp vì hiện tại nó chỉ thực hiện một quy tắc định dạng nhỏ, xác định. Không có lợi ích rõ ràng khi thêm `ReceiptFormatter` interface chỉ để có thêm một kiểu.
- `PaymentGateway` có một phương thức trừu tượng nên được đánh dấu `@FunctionalInterface`. Trong test, có thể cung cấp lambda làm fake, ví dụ:

```java
PaymentGateway fake = (orderId, amountCents) ->
        System.out.println("Fake charge: " + orderId + ", " + amountCents);
OrderService service = new OrderService(fake);
service.checkout("TEST-1", 500);
```

Đoạn test trên được đặt trong ngữ cảnh có thể truy cập các kiểu lồng của `InterfaceWhenNeededDemo` (ví dụ bên trong lớp demo hoặc test cùng package với quyền truy cập phù hợp). Nó minh họa khả năng thay thế phụ thuộc, không phải phần của `main` ở chương trình chạy chính. Khi dự án lớn hơn, có thể chuyển các kiểu thành top-level types và điều chỉnh modifier theo ranh giới package/API.

## 6. Chọn class, interface hay abstract class?

| Lựa chọn | Chọn khi | Ví dụ điển hình |
| --- | --- | --- |
| Class cụ thể | Có một hành vi xác định, ổn định; chưa cần thay thế implementation. | `ReceiptFormatter`, lớp tính toán thuần. |
| Interface | Cần một hợp đồng/capability để nhiều implementation hoặc thành phần bên ngoài có thể tuân thủ. | `PaymentGateway`, `Storage`, `Clock` ở ranh giới cần thay thế. |
| Abstract class | Các lớp cùng họ cần chia sẻ trạng thái, mã triển khai hoặc quy trình chung; Java chỉ cho kế thừa một class. | Một lớp cơ sở có template method và trạng thái dùng chung thực sự. |

Interface chủ yếu mô tả hợp đồng; abstract class có thể giữ state và triển khai một phần hành vi dùng chung. Đừng tạo abstract class chỉ để tránh lặp một vài dòng, cũng đừng tạo interface chỉ vì một class có public methods.

## 7. Cách ra quyết định trước khi thêm interface

Có thể tự hỏi theo thứ tự:

1. **Ai là consumer?** Có thành phần nào cần gọi qua hợp đồng thay vì biết class cụ thể không?
2. **Điều gì cần thay thế?** Có các implementation, môi trường hoặc chính sách thực sự khác nhau không? Nếu chưa có, ranh giới đó có lý do công khai/kiến trúc nào khác không?
3. **Thay đổi có bị chặn bởi coupling không?** Nếu class cụ thể thay đổi, nó có buộc logic nghiệp vụ hoặc test phải phụ thuộc vào thư viện/hạ tầng không?
4. **Interface nói lên điều gì?** Tên và method có mô tả capability hoặc hợp đồng có ý nghĩa không, hay chỉ lặp lại tên class thêm chữ `I`, `Default` hoặc `Impl`?
5. **Chi phí có xứng đáng không?** Lợi ích về thay thế, kiểm thử, API hoặc ranh giới module có lớn hơn số kiểu và điểm điều hướng mới không?
6. **Có thể trì hoãn một cách an toàn không?** Nếu chưa có nhu cầu hiện tại và không có ranh giới quan trọng cần bảo vệ, hãy bắt đầu bằng class cụ thể rồi trích xuất interface khi bằng chứng thiết kế xuất hiện.

Không cần mọi câu trả lời đều là “có nhiều implementation”. Interface cũng có thể bảo vệ một ranh giới trước khi implementation thứ hai tồn tại. Nhưng lý do đó nên cụ thể và có thể giải thích được, không chỉ là dự đoán mơ hồ.

## 8. Những cách làm dễ gây vấn đề

### Tạo interface chỉ để tuân thủ quy ước đặt tên

Ví dụ, mọi class đều bị bắt buộc có cặp `CustomerService` và `CustomerServiceImpl` dù không có hợp đồng hay consumer độc lập. Quy ước này có thể hợp lý trong một framework hoặc kiến trúc đã xác định rõ, nhưng không phải quy tắc chung của Java. Hãy dựa vào yêu cầu của hệ thống, không tạo lớp trung gian chỉ vì thói quen.

### Dùng interface để che giấu một thiết kế chưa rõ

Đặt tên `Manager`, `Processor` hoặc `Handler` chung chung không làm trách nhiệm rõ hơn. Trước khi tạo abstraction, xác định capability, dữ liệu đầu vào/đầu ra và điều kiện mà mọi implementation phải tuân thủ.

### Mock tất cả các class trong unit test

Mock có thể hữu ích ở ranh giới bên ngoài, nhưng mock mọi collaborator nhỏ làm test dễ vỡ khi cấu trúc nội bộ đổi. Hãy ưu tiên kiểm tra hành vi quan sát được; dùng fake/mock ở nơi cần kiểm soát kết quả phụ thuộc hoặc tránh I/O thật.

### Giữ abstraction không còn giá trị

Nếu nhiều implementation đã bị xóa và hợp đồng không còn được consumer nào cần đến, xem xét đơn giản hóa có kiểm soát. Ngược lại, đừng xóa interface chỉ vì hôm nay mới có một implementation nếu nó vẫn là ranh giới API/hạ tầng quan trọng.

## Kết luận

Interface là công cụ để thể hiện hợp đồng và tạo khả năng thay thế có ý nghĩa, không phải bước bắt buộc cho mỗi class. Hãy giữ lớp cụ thể khi hành vi đơn giản và ổn định; tạo interface khi cần nhiều implementation, ranh giới với hạ tầng, API mở rộng hoặc một điểm thay thế thực sự hữu ích. Thiết kế tốt không phải là trừu tượng hóa nhiều nhất, mà là **đặt abstraction đúng nơi có nhu cầu và tránh indirection không mang lại giá trị**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstraction-choices-010">Tránh constant interface và cây kế thừa chỉ để tái sử dụng mã</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh constant interface và cây kế thừa chỉ để tái sử dụng mã

Trong Java, interface và abstract class là công cụ để mô hình hóa hợp đồng, hành vi chung và quan hệ kiểu. Tuy nhiên, dùng interface chỉ để gom hằng số hoặc tạo một cây kế thừa chỉ nhằm lấy lại vài dòng mã thường khiến thiết kế khó hiểu, khó thay đổi và dễ tạo phụ thuộc ngoài ý muốn.

Hai nguyên tắc cần ghi nhớ:

- **Không dùng constant interface:** đừng bắt một lớp `implements` interface chỉ để tham chiếu các hằng số được khai báo trong interface đó.
- **Ưu tiên composition (kết hợp đối tượng) hơn inheritance (kế thừa) khi mục tiêu chỉ là tái sử dụng mã:** hãy dùng quan hệ kế thừa khi kiểu con thực sự có thể thay thế kiểu cha và tuân thủ hợp đồng của kiểu cha.

Điều này không có nghĩa là tránh mọi interface hoặc mọi kế thừa. Interface mô tả hành vi mà nhiều kiểu có thể cung cấp; abstract class có thể chứa trạng thái và triển khai chung khi có quan hệ trừu tượng phù hợp. Vấn đề nằm ở việc chọn công cụ chỉ vì tiện lấy hằng số hoặc dùng lại mã, thay vì phản ánh đúng ý nghĩa của mô hình.

## 1. Constant interface là gì?

Trong Java, các trường được khai báo trong interface mặc nhiên là `public static final`. Vì vậy, interface có thể được viết để chứa các hằng số:

~~~java
public interface HttpConstants {
    int DEFAULT_TIMEOUT_SECONDS = 30;
    int MAX_RETRIES = 3;
}
~~~

Cách này trở thành **constant interface anti-pattern** khi một lớp triển khai interface chỉ để dùng trực tiếp các tên hằng số:

~~~java
// Không nên thiết kế theo cách này.
class HttpClient implements HttpConstants {
    void connect() {
        int timeout = DEFAULT_TIMEOUT_SECONDS;
        // ...
    }
}
~~~

`HttpClient` không phải là một loại `HttpConstants`. Nó chỉ cần dùng một vài giá trị cấu hình, nhưng `implements` lại làm cho các hằng số trở thành một phần của API kiểu và tạo cảm giác rằng lớp có quan hệ kiểu với interface.

Các vấn đề chính:

- **Sai ý nghĩa của quan hệ kiểu:** `implements` nên biểu thị lớp cung cấp hành vi theo hợp đồng của interface.
- **Làm rò rỉ chi tiết triển khai:** hằng số có thể xuất hiện trong API công khai của lớp triển khai, dù người dùng lớp không cần biết nguồn gốc của chúng.
- **Tăng phụ thuộc không cần thiết:** lớp gắn với interface chỉ vì các giá trị dùng chung, khiến việc đổi tên hoặc tổ chức lại hằng số có thể ảnh hưởng nhiều nơi.
- **Khó thay đổi thiết kế:** một interface nên được thiết kế quanh hành vi và hợp đồng; nhóm hằng số thường không cung cấp một hợp đồng hành vi hữu ích.

Đặc biệt, việc lớp triển khai interface để truy cập hằng số không mang lại lợi ích đa hình. Không có hành vi nào được thay thế qua interface; mục tiêu chỉ là rút ngắn cách viết tên hằng số.

## 2. Các cách tổ chức hằng số tốt hơn

Chọn nơi đặt hằng số theo ý nghĩa của dữ liệu, phạm vi sử dụng và quyền sở hữu của nó.

### Cách A: Đặt hằng số trong lớp liên quan

Nếu giá trị chỉ có ý nghĩa đối với một lớp, hãy đặt nó ngay trong lớp đó:

~~~java
public final class HttpClient {
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRIES = 3;

    public void connect() {
        int timeout = DEFAULT_TIMEOUT_SECONDS;
        // Sử dụng timeout khi kết nối.
    }
}
~~~

Các hằng số `private` không trở thành một phần của API công khai. Người bảo trì cũng dễ xác định giá trị thuộc về lớp nào.

### Cách B: Dùng lớp tiện ích không thể khởi tạo

Khi nhiều lớp thực sự cần dùng chung một nhóm hằng số thuộc cùng một miền, có thể dùng lớp `final` với constructor riêng:

~~~java
public final class HttpDefaults {
    private HttpDefaults() {
        // Không cho phép khởi tạo lớp chứa hằng số.
    }

    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    public static final int MAX_RETRIES = 3;
}
~~~

Lớp dùng giá trị qua tên đầy đủ:

~~~java
int timeout = HttpDefaults.DEFAULT_TIMEOUT_SECONDS;
~~~

Tên `HttpDefaults` cho biết giá trị thuộc nhóm cấu hình HTTP; nó không giả vờ rằng lớp sử dụng các hằng số là một kiểu con của một hợp đồng nào đó.

Không nên biến một lớp chứa hằng số thành nơi tập hợp mọi giá trị của toàn bộ ứng dụng. Nếu các hằng số thuộc những miền khác nhau, hãy đặt chúng gần kiểu hoặc cấu hình sở hữu chúng. Nếu giá trị thay đổi theo môi trường hoặc cần được kiểm thử, truyền cấu hình vào đối tượng thường tốt hơn việc đọc một hằng số toàn cục.

### Cách C: Dùng enum cho một tập lựa chọn hữu hạn

Nếu dữ liệu đại diện cho một tập giá trị có ý nghĩa và được xác định trước, hãy cân nhắc `enum` thay vì các số nguyên hoặc chuỗi rời rạc:

~~~java
enum RetryMode {
    NONE,
    FIXED_DELAY,
    EXPONENTIAL_BACKOFF
}
~~~

`enum` cho phép kiểu hóa các lựa chọn và dùng `switch` rõ nghĩa. Tuy nhiên, enum không phải cách thay thế cho mọi hằng số; một giá trị số như thời gian chờ vẫn có thể phù hợp với trường `static final` hoặc cấu hình có kiểu.

### Cách D: Static import khi giúp mã dễ đọc

Java hỗ trợ static import:

~~~java
import static java.lang.Math.PI;
~~~

Sau đó có thể dùng `PI` thay vì `Math.PI`. Static import hữu ích khi tên nguồn đã rõ và việc lặp lại tên lớp gây nhiễu. Với hằng số ứng dụng, hãy dùng có chừng mực: nếu tên đơn lẻ không cho biết giá trị đến từ đâu, cách viết `HttpDefaults.DEFAULT_TIMEOUT_SECONDS` thường dễ hiểu hơn.

Static import chỉ thay đổi cách viết tên trong mã nguồn; nó không giải quyết thiết kế constant interface nếu lớp vẫn triển khai interface chỉ để truy cập hằng số.

## 3. Khi nào interface thực sự phù hợp?

Interface nên mô tả một hợp đồng hành vi hoặc khả năng mà các kiểu khác nhau có thể cung cấp. Ví dụ, một chương trình có thể cần nhiều chính sách giảm giá nhưng đều phải trả lời câu hỏi: “Với số tiền này, mức giảm là bao nhiêu?”

~~~java
import java.math.BigDecimal;

interface DiscountPolicy {
    BigDecimal discountFor(BigDecimal subtotal);
}
~~~

Interface này có một phương thức trừu tượng, nên có thể được triển khai bằng các lớp khác nhau hoặc biểu đạt bằng lambda khi kiểu đích được xác định. Nó có mục đích rõ ràng: bên gọi phụ thuộc vào hợp đồng tính giảm giá mà không phải biết cách tính cụ thể.

Ngược lại, một interface chỉ có `int TIMEOUT = 30;` không xác định hành vi mà lớp triển khai phải cung cấp. Đừng thêm `implements` chỉ để tránh viết tên lớp trước hằng số.

Interface vẫn có thể khai báo trường hằng số khi hằng số đó là một phần có chủ đích của hợp đồng và có ý nghĩa với người sử dụng interface. Nguyên tắc cần tránh là dùng interface làm namespace tiện lợi cho các giá trị không liên quan đến hợp đồng hành vi.

## 4. Vì sao không nên tạo cây kế thừa chỉ để tái sử dụng mã?

Giả sử hai lớp cần cùng một đoạn xử lý. Ta có thể tạo một lớp cha để đặt đoạn mã đó vào rồi cho cả hai lớp kế thừa. Cách làm này có vẻ tiết kiệm mã, nhưng kế thừa còn tạo ra một quan hệ kiểu: lớp con được xem là một dạng của lớp cha và phải giữ được hợp đồng của lớp cha.

Nếu hai lớp chỉ tình cờ có chung vài bước thực hiện nhưng không có quan hệ “là một”, việc đưa chúng vào cùng cây kế thừa có thể tạo mô hình sai.

Những rủi ro thường gặp:

- **Quan hệ kiểu sai:** lớp con có thể không đáp ứng kỳ vọng của người dùng lớp cha.
- **Phụ thuộc vào chi tiết triển khai:** lớp con có thể dựa vào trường `protected`, thứ tự gọi phương thức hoặc trạng thái nội bộ của lớp cha.
- **Lớp cha thay đổi gây hỏng lớp con:** một thay đổi tưởng như vô hại ở lớp cha có thể làm hành vi lớp con khác đi.
- **Khó kết hợp nhiều khả năng:** Java chỉ cho một lớp trực tiếp kế thừa một lớp khác, nên việc dùng cây kế thừa để ghép các tính năng độc lập nhanh chóng bị hạn chế.
- **Khó kiểm thử:** lớp con thường cần khởi tạo và tuân thủ trạng thái của lớp cha, dù phần tái sử dụng chỉ là một thao tác nhỏ.

Kế thừa phù hợp khi có quan hệ “là một” thực sự, lớp con có thể thay thế lớp cha ở nơi lớp cha được yêu cầu và hợp đồng vẫn đúng. Tái sử dụng mã là lợi ích có thể có của kế thừa, nhưng không đủ để tự nó biện minh cho quan hệ kế thừa.

## 5. Ưu tiên composition và delegation

**Composition** nghĩa là một đối tượng chứa hoặc nhận một đối tượng khác để sử dụng chức năng của nó. **Delegation** (ủy quyền) là việc một đối tượng chuyển một phần công việc cho đối tượng thành phần đó.

Ví dụ, thay vì cho `CheckoutService` kế thừa một lớp giảm giá chỉ để lấy phương thức tính toán, hãy để dịch vụ nhận một `DiscountPolicy`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

interface DiscountPolicy {
    BigDecimal discountFor(BigDecimal subtotal);
}

final class NoDiscountPolicy implements DiscountPolicy {
    @Override
    public BigDecimal discountFor(BigDecimal subtotal) {
        return BigDecimal.ZERO.setScale(2);
    }
}

final class TenPercentDiscount implements DiscountPolicy {
    @Override
    public BigDecimal discountFor(BigDecimal subtotal) {
        return subtotal.multiply(new BigDecimal("0.10"))
                .setScale(2, RoundingMode.HALF_UP);
    }
}

final class CheckoutService {
    private final DiscountPolicy discountPolicy;

    CheckoutService(DiscountPolicy discountPolicy) {
        this.discountPolicy = discountPolicy;
    }

    BigDecimal totalFor(BigDecimal subtotal) {
        BigDecimal discount = discountPolicy.discountFor(subtotal);
        return subtotal.subtract(discount);
    }
}

public class CompositionDemo {
    public static void main(String[] args) {
        BigDecimal subtotal = new BigDecimal("100.00");
        CheckoutService checkout =
                new CheckoutService(new TenPercentDiscount());

        BigDecimal total = checkout.totalFor(subtotal);
        System.out.println("Subtotal: " + subtotal);
        System.out.println("Discount: "
                + subtotal.subtract(total).setScale(2));
        System.out.println("Total: " + total);
    }
}
~~~

Lưu đoạn mã trong tệp **CompositionDemo.java**, sau đó biên dịch và chạy với Java 17 trở lên:

~~~bash
javac CompositionDemo.java
java CompositionDemo
~~~

Kết quả stdout:

~~~text
Subtotal: 100.00
Discount: 10.00
Total: 90.00
~~~

Trong ví dụ này, `CheckoutService` không phải là một loại `TenPercentDiscount`; dịch vụ **có một** chính sách giảm giá. Nó chỉ gọi phương thức qua interface `DiscountPolicy`. Có thể thay chính sách giảm giá mà không cần tạo lớp con mới của `CheckoutService`.

Constructor nhận chính sách từ bên ngoài cũng giúp kiểm thử dễ hơn: có thể truyền `NoDiscountPolicy` hoặc một chính sách kiểm thử để kiểm tra kết quả. Trong ứng dụng thực tế, nên xác thực các giá trị đầu vào theo yêu cầu miền nghiệp vụ, chẳng hạn xử lý `null` và không cho phép subtotal âm nếu nghiệp vụ không chấp nhận chúng.

## 6. Dấu hiệu cây kế thừa được tạo chỉ để dùng lại mã

Khi rà soát một lớp `Base*` có nhiều lớp con, đừng chỉ đếm số dòng được chia sẻ. Hãy kiểm tra các câu hỏi gắn trực tiếp với anti-pattern:

1. **Có hợp đồng thay thế thật không?** Nếu client nhận kiểu lớp cha, liệu mọi lớp con có giữ cùng ý nghĩa của các phương thức công khai? Nếu không, quan hệ kế thừa có thể sai dù mã được dùng lại.
2. **Phần tái sử dụng có phải chỉ là tiện ích không?** Nếu lớp cha chỉ có vài hàm định dạng, tính toán hoặc kết nối không gắn với một họ kiểu, hãy cân nhắc helper có phạm vi thích hợp hoặc một đối tượng cộng tác.
3. **Lớp con có phụ thuộc chi tiết nội bộ không?** Việc phải truy cập hàng loạt trường `protected`, gọi `super` theo thứ tự ngầm định hoặc override để vô hiệu hóa logic là dấu hiệu thiết kế mong manh.
4. **Các khả năng có thay đổi độc lập không?** Nếu thêm một phương thức xử lý buộc tạo nhiều lớp con cho mọi tổ hợp, composition với interface theo vai trò có thể làm ranh giới thay đổi rõ ràng hơn.

Composition cũng có chi phí: thêm dependency, constructor và đôi khi lớp chuyển tiếp. Chọn nó khi thành phần có trách nhiệm riêng; nếu chỉ có một hàm thuần nhỏ, helper đơn giản có thể đủ. Với quan hệ “là một” thực sự và một quy trình chung ổn định, kế thừa vẫn là lựa chọn hợp lý.

## 7. Liskov Substitution Principle và hợp đồng của lớp cha

Nguyên tắc thay thế Liskov (LSP) nói rằng đối tượng của kiểu con phải có thể dùng ở nơi yêu cầu kiểu cha mà không phá vỡ những kỳ vọng hợp lệ của bên gọi.

Ví dụ, nếu một lớp cha quy định phương thức `save()` luôn lưu dữ liệu thành công hoặc ném ngoại lệ đã được tài liệu hóa, lớp con không nên âm thầm bỏ qua thao tác lưu chỉ vì muốn tái sử dụng một phương thức tiện ích khác. Việc kế thừa kéo theo trách nhiệm tuân thủ hợp đồng của lớp cha, bao gồm các điều kiện đầu vào, kết quả, bất biến và hành vi có thể quan sát.

Nếu hai lớp chỉ cần chung một hàm định dạng hoặc thuật toán, có thể chuyển phần đó sang một helper có phạm vi hợp lý hoặc một đối tượng dịch vụ riêng. Điều này tránh buộc lớp này phải trở thành lớp con của lớp kia khi chúng không có quan hệ thay thế hợp lệ.

## 8. Abstract class vẫn hữu ích khi có triển khai chung phù hợp

Không nên hiểu “ưu tiên composition” là cấm abstract class. Abstract class có ích khi các lớp con thực sự thuộc cùng một họ khái niệm và chia sẻ trạng thái hoặc một phần thuật toán theo hợp đồng rõ ràng.

Ví dụ, một abstract class `FileProcessor` có thể định nghĩa quy trình chung `process()` và yêu cầu lớp con cung cấp các bước `read()` hoặc `transform()`. Đây là thiết kế kiểu template method: lớp cha kiểm soát quy trình chung, lớp con tùy chỉnh các bước được thiết kế để mở rộng.

Ngược lại, nếu một lớp chỉ kế thừa `BaseUtilities` để lấy một phương thức `formatDate()`, trong khi nó không phải là một loại của `BaseUtilities`, hãy cân nhắc đặt thao tác định dạng trong một lớp hoặc dịch vụ riêng và gọi nó. Đừng tạo các lớp cha tổng quát kiểu `BaseEverything` chỉ để gom nhiều hàm không liên quan.

## 9. Cách chọn giải pháp theo tình huống

- **Một hằng số chỉ dùng trong một lớp:** đặt nó trong lớp đó, thường là `private static final`.
- **Một nhóm hằng số có chung miền:** cân nhắc lớp `final` với constructor riêng, hoặc một kiểu cấu hình phù hợp.
- **Một tập lựa chọn hữu hạn có ý nghĩa:** cân nhắc `enum`.
- **Nhiều lớp cần cùng một hợp đồng hành vi:** định nghĩa interface mô tả hành vi đó.
- **Các lớp cùng một họ, có quan hệ thay thế và triển khai chung rõ ràng:** cân nhắc abstract class hoặc lớp cha cụ thể.
- **Một lớp chỉ cần gọi lại một chức năng của thành phần khác:** dùng composition/delegation.
- **Một thuật toán thuần, nhỏ, không có trạng thái:** cân nhắc phương thức helper có phạm vi thích hợp; không cần dựng cây kế thừa chỉ để chia sẻ nó.

Trước khi thêm interface hoặc lớp cha, hãy tự hỏi: “Quan hệ kiểu này có đúng về mặt nghiệp vụ không? Người dùng có thể dựa vào hợp đồng này không? Nếu không cần tái sử dụng mã, tôi vẫn muốn mối quan hệ này tồn tại chứ?” Nếu câu trả lời cho câu hỏi cuối là không, composition hoặc một helper thường đáng xem xét hơn.

## 10. Lỗi thiết kế thường gặp

- **`implements` chỉ để truy cập hằng số:** chuyển hằng số vào lớp sở hữu hoặc một lớp cấu hình có tên rõ nghĩa.
- **Interface chỉ có trường dữ liệu nhưng tên gợi ý hợp đồng:** thiết kế interface quanh hành vi thực sự cần thiết; giữ hằng số riêng nếu chúng không thuộc hợp đồng.
- **Lớp cha “tiện ích” chứa mọi thứ:** tách các trách nhiệm độc lập thành thành phần nhỏ có tên mô tả đúng nhiệm vụ.
- **Kế thừa để tránh vài dòng trùng lặp:** đánh giá xem trùng lặp có thực sự cần loại bỏ không; nếu có, trích xuất phương thức hoặc dùng một thành phần có trách nhiệm rõ ràng.
- **Lớp con ghi đè để vô hiệu hóa hành vi lớp cha:** đây có thể là dấu hiệu lớp con không tuân thủ hợp đồng hoặc lớp cha có ranh giới trách nhiệm chưa phù hợp.
- **Dùng `protected` để mở trạng thái chỉ nhằm cho lớp con tái sử dụng:** cân nhắc đóng gói trạng thái và cung cấp một phương thức hoặc interface nhỏ thay vì để lớp con phụ thuộc vào chi tiết nội bộ.
- **Composition tạo quá nhiều lớp chuyển tiếp:** chỉ trích xuất thành phần khi nó có trách nhiệm hoặc điểm thay đổi riêng; giữ mã đơn giản khi một helper nhỏ đã đủ.

## 11. Quy trình rà soát thiết kế

1. Xác định lớp đang triển khai interface hoặc kế thừa lớp cha vì lý do gì: hợp đồng kiểu, tái sử dụng hành vi hay chỉ để truy cập hằng số.
2. Với constant interface, chuyển các giá trị về lớp sở hữu, lớp cấu hình hoặc kiểu miền thích hợp; cập nhật cách tham chiếu rõ ràng và bỏ `implements` không có ý nghĩa.
3. Với cây kế thừa dùng để tái sử dụng, kiểm tra quan hệ “là một”, hợp đồng thay thế và sự phụ thuộc của lớp con vào trạng thái hoặc chi tiết của lớp cha.
4. Nếu chỉ cần một khả năng, trích xuất interface hành vi nhỏ hoặc thành phần cụ thể và để lớp gọi nó qua composition/delegation.
5. Viết hoặc chạy kiểm thử cho hành vi trước và sau thay đổi, chú ý kết quả quan sát được và các trường hợp biên.
6. Đặt tên cho interface, abstract class, thành phần và hằng số theo vai trò thực tế của chúng; tránh các tên tổng quát che giấu trách nhiệm.

Thiết kế Java tốt dùng interface để mô tả hành vi, abstract class để chia sẻ triển khai trong một họ kiểu có ý nghĩa, và composition để phối hợp các khả năng độc lập. Tránh constant interface và cây kế thừa chỉ nhằm tái sử dụng mã giúp quan hệ giữa các lớp rõ ràng hơn, giảm phụ thuộc ngoài ý muốn và làm cho việc thay đổi, kiểm thử cũng như bảo trì dễ dàng hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
