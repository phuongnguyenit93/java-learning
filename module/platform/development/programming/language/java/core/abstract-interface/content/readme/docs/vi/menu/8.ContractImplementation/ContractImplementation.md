<a id="back-to-top"></a>

# Triển khai hợp đồng

## Menu
- [Triển khai đầy đủ các phương thức abstract còn thiếu](#contract-implementation-001)
- [Phương thức thực thi interface phải public](#contract-implementation-002)
- [Lớp trừu tượng thực thi một phần interface](#contract-implementation-003)
- [Dùng phương thức kế thừa từ lớp cha để đáp ứng interface](#contract-implementation-004)
- [Một phương thức đáp ứng nhiều interface có cùng chữ ký](#contract-implementation-005)
- [Nhiều interface có yêu cầu kiểu trả về không tương thích](#contract-implementation-006)
- [Triển khai tường minh bằng phương thức có @Override](#contract-implementation-007)
- [Không tự động trở thành kiểu con chỉ vì có phương thức giống nhau](#contract-implementation-008)
- [Giữ đúng ý nghĩa hợp đồng bên cạnh việc biên dịch thành công](#contract-implementation-009)

## <a id="contract-implementation-001">Triển khai đầy đủ các phương thức abstract còn thiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Triển khai đầy đủ các phương thức abstract còn thiếu trong Java

Một lớp Java **cụ thể** phải có cách triển khai hợp lệ cho mọi phương thức instance abstract còn hiệu lực từ các lớp cha và interface mà nó kế thừa. Phần triển khai có thể được khai báo ngay trong lớp hoặc được kế thừa từ một lớp cha phù hợp. Nếu lớp còn nghĩa vụ abstract chưa giải quyết, nó phải tiếp tục là lớp abstract; nếu không, trình biên dịch từ chối khai báo lớp.

Bài này tập trung vào cách xác định nghĩa vụ còn thiếu và chọn đúng nơi hoàn thành nó. Các quy tắc chuyên biệt về quyền truy cập, phương thức kế thừa, nhiều interface và kiểu trả về được phân tích sâu ở những bài tiếp theo trong cùng chương.

## 1. Phân biệt phương thức abstract và phương thức có thân

Một phương thức abstract chỉ công bố chữ ký, không cung cấp phần thân thực thi. Phương thức cụ thể có phần thân và có thể được gọi khi một đối tượng thực thi nó.

Ví dụ sau là chương trình Java 17 hoàn chỉnh, lưu vào **Main.java**:

~~~java
public class Main {
    abstract static class Shape {
        abstract double area();
    }

    static final class Circle extends Shape {
        private final double radius;

        Circle(double radius) {
            this.radius = radius;
        }

        @Override
        double area() {
            return Math.PI * radius * radius;
        }
    }

    public static void main(String[] args) {
        System.out.printf("%.2f%n", new Circle(2).area());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac Main.java
java Main
~~~

Kết quả:

~~~text
12.57
~~~

**Circle** là lớp cụ thể vì đã triển khai phương thức **area()** mà nó kế thừa. Nếu bỏ phương thức này nhưng vẫn giữ **Circle** là lớp cụ thể, mã sẽ không biên dịch. Nếu **Circle** vẫn chưa biết cách tính diện tích vì nó chỉ là bước trung gian trong hệ phân cấp, có thể khai báo **abstract class Circle** để lớp con hoàn thiện sau.

## 2. Nghĩa vụ triển khai đến từ đâu?

Khi kiểm tra một lớp cụ thể, hãy xét toàn bộ hệ phân cấp chứ không chỉ nhìn các phương thức được viết ngay trong lớp:

- Các phương thức abstract được khai báo trong superclass và vẫn chưa có triển khai cụ thể.
- Các phương thức abstract mà interface trực tiếp hoặc gián tiếp yêu cầu.
- Những nghĩa vụ được để lại qua một hoặc nhiều lớp abstract trung gian.
- Những phương thức có cùng tên nhưng thực ra khác chữ ký; chúng không tự động hoàn thành nghĩa vụ của nhau.

Một abstract class có thể triển khai một phần, toàn bộ hoặc chưa triển khai phương thức abstract nào. Từ khóa **abstract** cho phép lớp trì hoãn nghĩa vụ; nó không làm nghĩa vụ đó biến mất. Trước khi một lớp có thể được tạo bằng **new**, mọi phương thức abstract còn thiếu phải được giải quyết.

Không phải mọi phương thức interface đều tạo nghĩa vụ triển khai mới. Một phương thức **default** đã có thân và thường được kế thừa; phương thức **static** hoặc **private** của interface không thay thế một phương thức instance abstract. Trường dữ liệu, constructor và một phương thức overload khác tham số cũng không thể thay thế chữ ký abstract đang còn thiếu.

## 3. Lỗi biên dịch và cách sửa

Đoạn dưới đây được cố ý viết sai: **HtmlContent** là lớp cụ thể nhưng chưa triển khai **render()**.

~~~java
abstract class Content {
    abstract String render();
}

class HtmlContent extends Content {
    // Chưa có render(), nên lớp cụ thể này không hợp lệ.
}
~~~

Có hai hướng sửa tùy ý định thiết kế.

**Hướng A — lớp đã đủ chức năng để tạo đối tượng:** triển khai phương thức còn thiếu.

~~~java
class HtmlContent extends Content {
    @Override
    String render() {
        return "<p>Hello</p>";
    }
}
~~~

**Hướng B — lớp chỉ là tầng trung gian:** khai báo lớp đó là abstract và để lớp cụ thể phía sau hoàn thiện phương thức.

~~~java
abstract class HtmlContent extends Content {
    // Có thể bổ sung trạng thái và hành vi chung ở đây.
}

final class WelcomePage extends HtmlContent {
    @Override
    String render() {
        return "<p>Welcome</p>";
    }
}
~~~

Đây là các phương án thay thế nhau; không đặt hai khai báo cùng tên vào cùng package để biên dịch.

## 4. Quy trình tìm phương thức còn thiếu

Khi gặp lỗi có ý nghĩa “class is not abstract and does not override an abstract method”, hãy kiểm tra theo thứ tự:

1. Xác định lớp được nêu trong lỗi và xác nhận lớp đó có chủ ý là concrete hay không.
2. Đọc các khai báo **extends** và **implements**, rồi liệt kê các phương thức abstract mà lớp nhận được từ hệ phân cấp.
3. Với từng phương thức, tìm một triển khai cụ thể đã có hoặc cần viết. Không bắt buộc sao chép lại phương thức đã được kế thừa hợp lệ.
4. So sánh chính xác tên, kiểu tham số, kiểu trả về, khả năng truy cập và ngoại lệ checked. Nếu cần triển khai phương thức interface thông thường, phương thức đó phải là public.
5. Dùng **@Override** trên phương thức mới để compiler phát hiện việc viết nhầm chữ ký. Nếu lớp vẫn còn nghĩa vụ chưa giải quyết, hãy triển khai thêm hoặc giữ lớp ở trạng thái abstract.
6. Sau khi mã biên dịch được, kiểm tra phần thân có thực hiện đúng ý nghĩa của hợp đồng hay không. Hết lỗi compiler mới chứng minh tính hợp lệ về kiểu.

## 5. Những nhầm lẫn cần tránh

- **Có phương thức cùng tên chưa chắc đã override.** Thêm hoặc đổi kiểu tham số có thể tạo overload và để lại phương thức abstract ban đầu.
- **Không phải lớp cụ thể nào cũng phải tự viết mọi phương thức.** Triển khai concrete được kế thừa có thể đã đáp ứng hợp đồng.
- **Khai báo abstract không phải lỗi thiết kế tự thân.** Đây là cách hợp lệ để chia trách nhiệm qua nhiều tầng kế thừa.
- **Không dùng thân giả để che nghĩa vụ.** Một phương thức chỉ ném UnsupportedOperationException có thể làm lớp biên dịch được nhưng vẫn không đáp ứng hành vi mà người gọi mong đợi.
- **Không nhầm phương thức default với abstract.** Default method có thân, nhưng xung đột giữa các default method vẫn có thể cần giải quyết tường minh.

## 6. Tự kiểm tra

**Câu 1.** Interface **Printer** khai báo **print()** và **close()**. Abstract class **BasePrinter** đã triển khai **close()**. Lớp concrete **ConsolePrinter** kế thừa **BasePrinter** phải làm gì?

**Đáp án:** triển khai **print()**, trừ khi có một implementation hợp lệ khác được kế thừa. Không cần viết lại **close()**.

**Câu 2.** Một abstract class đã triển khai mọi phương thức abstract có được tiếp tục khai báo abstract không?

**Đáp án:** có. Từ khóa abstract vẫn có thể thể hiện rằng lớp chỉ dùng làm lớp nền và không được khởi tạo trực tiếp.

**Câu 3.** Vì sao thêm một phương thức cùng tên nhưng nhận tham số khác không giải quyết lỗi thiếu phương thức abstract?

**Đáp án:** vì đó là overload; chữ ký cần được triển khai vẫn còn thiếu.

**Ghi nhớ:** một lớp concrete phải hoàn tất mọi nghĩa vụ phương thức abstract còn hiệu lực. Xác định đúng chữ ký và toàn bộ chuỗi kế thừa trước khi thêm phương thức, rồi kiểm tra riêng tính đúng đắn của hành vi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-implementation-002">Phương thức thực thi interface phải public</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức thực thi interface phải public trong Java

Phương thức instance abstract thông thường trong Java interface có mức truy cập **public**, ngay cả khi khai báo không viết từ khóa này. Vì vậy, lớp cung cấp phần thân để đáp ứng phương thức đó phải giữ mức truy cập public. Java không cho phép implementation thu hẹp quyền truy cập của hợp đồng mà người gọi đang sử dụng.

## 1. Ví dụ đúng và sai

Lưu chương trình sau vào **PublicContractDemo.java**:

~~~java
public class PublicContractDemo {
    interface Task {
        void run(); // Ngầm hiểu là public abstract.
    }

    static final class PrintTask implements Task {
        @Override
        public void run() {
            System.out.println("running");
        }
    }

    public static void main(String[] args) {
        Task task = new PrintTask();
        task.run();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac PublicContractDemo.java
java PublicContractDemo
~~~

Kết quả:

~~~text
running
~~~

Nếu bỏ từ khóa **public** khỏi **PrintTask.run()**, phương thức trong class trở thành package-private. Đoạn sau được cố ý viết sai:

~~~java
interface Task {
    void run();
}

class InvalidTask implements Task {
    @Override
    void run() {
        System.out.println("running");
    }
}
~~~

Compiler báo rằng phương thức trong **InvalidTask** đang cố triển khai phương thức public với quyền truy cập yếu hơn. Đổi thành **protected** hoặc **private** cũng không hợp lệ. Annotation **@Override** giúp kiểm tra quan hệ phương thức nhưng không tự thêm từ khóa public.

## 2. Vì sao phải giữ public?

Interface là một kiểu mà code gọi có thể phụ thuộc vào mà không cần biết lớp triển khai cụ thể. Khi biến có kiểu **Task**, người gọi có quyền gọi **run()** theo hợp đồng public. Nếu một implementation có thể hạ phương thức xuống package-private hoặc protected, cùng lời gọi sẽ phụ thuộc vào package hay quan hệ kế thừa của lớp cụ thể, làm hỏng khả năng thay thế mà interface hứa hẹn.

Trong class, phương thức không ghi modifier mặc định là package-private. Java không tự suy ra rằng bạn muốn dùng public chỉ vì tên và tham số trùng với interface. Vì thế hãy ghi rõ **public** khi triển khai hợp đồng interface.

## 3. Phân biệt các loại phương thức trong interface

| Dạng khai báo | Vai trò | Lớp triển khai có cần tự viết phương thức không? |
| --- | --- | --- |
| Phương thức abstract thông thường, ví dụ **void run();** | Public abstract, không có thân | Có, nếu chưa kế thừa implementation hợp lệ; phần thân phải public |
| Phương thức **default** có thân | Hành vi instance mặc định, cũng là public | Không bắt buộc; nếu override thì vẫn phải public |
| Phương thức **static** có thân | Thuộc interface, được gọi qua tên interface | Không; static không override phương thức instance |
| Phương thức **private** có thân | Helper nội bộ bên trong interface | Không; nó không phải hợp đồng cho lớp triển khai |

Từ khóa **private** trong bảng chỉ dùng cho helper có thân trong interface. Nó không cho phép một lớp triển khai phương thức abstract public bằng một phương thức private.

## 4. Phương thức lớp cha có đáp ứng được không?

Một lớp concrete có thể đáp ứng interface bằng phương thức public, concrete và tương thích được kế thừa từ superclass. Nó không bắt buộc phải khai báo lại phương thức chỉ để lặp lại cùng phần thân. Tuy nhiên, phương thức protected hoặc package-private từ lớp cha không tự trở thành public khi lớp con thêm **implements**.

Ví dụ: nếu **BaseTask** có **public void run()**, lớp **TaskImpl extends BaseTask implements Task** có thể dùng phương thức đó. Nếu **BaseTask** chỉ có **protected void run()**, lớp con phải cung cấp một implementation public hợp lệ hoặc thiết kế lại quan hệ lớp. Quy tắc về phương thức kế thừa được phân tích riêng trong bài kế tiếp của chương.

Một abstract class cũng phải tuân thủ quy tắc này nếu nó **tự khai báo lại** phương thức interface. Nó có thể để phương thức chưa được khai báo và trì hoãn nghĩa vụ vì vẫn abstract, nhưng không thể viết **protected abstract void run()** để thay thế một phương thức interface public.

## 5. Lỗi thường gặp

- Quên **public** vì cho rằng Java sẽ tự kế thừa modifier từ interface.
- Dùng **protected** với lý do chỉ các lớp con cần gọi phương thức. Nếu đó là phương thức public của interface, hãy giữ public và chuyển logic hỗ trợ riêng sang một helper protected/private khác.
- Cho rằng mọi phương thức interface đều phải tự viết. Default method có thân, và phương thức public tương thích từ superclass cũng có thể đáp ứng hợp đồng.
- Nhầm helper **private** trong interface với phương thức abstract mà lớp triển khai phải cung cấp.
- Chỉ nhìn tên phương thức. Khác kiểu tham số vẫn là một chữ ký khác; public đúng nhưng chữ ký sai vẫn không đáp ứng hợp đồng.

## 6. Tự kiểm tra

**Câu 1.** Vì sao khai báo **void save();** trong interface vẫn yêu cầu phương thức triển khai public?

**Đáp án:** các phương thức instance abstract thông thường trong interface ngầm định là public abstract.

**Câu 2.** Abstract class có thể để **save()** chưa được triển khai không?

**Đáp án:** có, nếu lớp vẫn abstract. Nhưng nếu lớp đó tự khai báo lại phương thức, nó không được thu hẹp quyền truy cập của hợp đồng public.

**Câu 3.** Nếu interface có một default method public, lớp con có thể override bằng protected không?

**Đáp án:** không. Khi override, phương thức vẫn không được giảm quyền truy cập.

**Ghi nhớ:** phần thân triển khai phương thức instance public của interface phải public. Hãy phân biệt modifier của phương thức với việc phương thức có cần được viết lại hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-implementation-003">Lớp trừu tượng thực thi một phần interface</a>

<details>
<summary>Xem chi tiết</summary>

# Lớp trừu tượng thực thi một phần interface trong Java

Một abstract class có thể nhận hợp đồng từ interface rồi triển khai những phần dùng chung, để các lớp con cụ thể hoàn thiện những hành vi còn phụ thuộc loại đối tượng. Nghĩa vụ chưa thực hiện vẫn tồn tại trong hệ phân cấp; chỉ lớp concrete cuối cùng mới phải có cách triển khai hợp lệ cho tất cả phương thức abstract còn thiếu.

## 1. Ví dụ hoàn chỉnh: lớp nền chia sẻ phần xử lý chung

Chương trình Java 17 sau minh họa interface định nghĩa hợp đồng, abstract class giữ trạng thái và logic dùng chung, còn concrete subclass cung cấp hành vi chuyên biệt. Lưu thành **PartialImplementationDemo.java**:

~~~java
import java.util.Objects;

public class PartialImplementationDemo {
    interface ReportJob {
        void validate();
        String generate();
        void audit(String result);

        default String kind() {
            return "report";
        }
    }

    abstract static class AbstractReportJob implements ReportJob {
        private final String reportName;

        protected AbstractReportJob(String reportName) {
            this.reportName = Objects.requireNonNull(reportName, "reportName");
        }

        @Override
        public final void validate() {
            if (reportName.isBlank()) {
                throw new IllegalArgumentException("reportName is blank");
            }
            System.out.println("validated: " + reportName);
        }

        @Override
        public void audit(String result) {
            System.out.println("audit: " + reportName + " -> " + result);
        }

        protected final String reportName() {
            return reportName;
        }

        // generate() vẫn chưa có thân; abstract class có thể trì hoãn.
    }

    static final class CsvReportJob extends AbstractReportJob {
        CsvReportJob(String reportName) {
            super(reportName);
        }

        @Override
        public String generate() {
            return reportName() + ": csv ready";
        }
    }

    public static void main(String[] args) {
        ReportJob job = new CsvReportJob("Sales");

        System.out.println("kind: " + job.kind());
        job.validate();
        String result = job.generate();
        System.out.println("result: " + result);
        job.audit(result);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 PartialImplementationDemo.java
java PartialImplementationDemo
~~~

Kết quả:

~~~text
kind: report
validated: Sales
result: Sales: csv ready
audit: Sales -> Sales: csv ready
~~~

### Trách nhiệm của từng kiểu

- **ReportJob** mô tả những hành vi người gọi có thể dựa vào. Ba phương thức abstract tạo nghĩa vụ triển khai; **kind()** là default method đã có thân.
- **AbstractReportJob** triển khai **validate()** và **audit()**, giữ trạng thái **reportName**, đồng thời cung cấp helper protected cho lớp con. Nó hợp lệ dù chưa có thân cho **generate()**, vì chính lớp này được khai báo abstract.
- **CsvReportJob** là lớp concrete. Nó kế thừa hai phương thức đã có và chỉ cần cung cấp **generate()**. Sau khi constructor gọi **super(reportName)**, đối tượng cụ thể có thể được tạo bằng **new**.
- **validate()** được đánh dấu final để lớp con không bỏ qua quy tắc kiểm tra chung bằng cách override. **generate()** được để mở cho hành vi riêng của từng loại báo cáo.

## 2. Lớp abstract có thể triển khai bao nhiêu phần?

Một abstract class có thể triển khai **không phương thức nào, một phần hoặc tất cả** các phương thức abstract của interface. Từ khóa abstract không bắt buộc lớp phải chứa một phương thức abstract; ngay cả khi đã có đủ phần thân, lớp vẫn có thể được cố ý giữ lại làm lớp nền không cho tạo đối tượng trực tiếp.

| Tầng kiểu | Trách nhiệm |
| --- | --- |
| Interface | Công bố hợp đồng và những phương thức abstract cần có |
| Abstract class | Đặt trạng thái, constructor, helper và logic chung; có thể trì hoãn một số phương thức |
| Abstract subclass khác | Có thể hoàn thiện thêm một phần rồi tiếp tục để lại phần còn thiếu |
| Concrete subclass | Phải có triển khai hợp lệ cho tất cả nghĩa vụ abstract còn hiệu lực |

Không bắt buộc abstract class phải khai báo lại chữ ký của phương thức nó để lại. Ví dụ **AbstractReportJob** không cần viết lại **public abstract String generate()** để trình biên dịch biết phương thức vẫn còn thiếu. Có thể khai báo lại tường minh nếu muốn nhấn mạnh điểm mở rộng hoặc thêm tài liệu API; việc đó không tạo ra phần thân. Nếu khai báo lại một phương thức interface public, không được giảm mức truy cập xuống protected.

## 3. Khi nào cách thiết kế này hữu ích?

Cách làm này phù hợp khi nhiều lớp cùng một họ cần chia sẻ trạng thái, constructor, kiểm tra dữ liệu, thứ tự xử lý hoặc một phần thuật toán, nhưng vẫn có một bước chuyên biệt cho từng loại.

Ví dụ, các báo cáo CSV và JSON có thể cùng kiểm tra tên báo cáo và ghi audit, trong khi mỗi loại có cách sinh nội dung riêng. Đặt phần chung ở abstract class giúp các lớp con không phải sao chép và giảm nguy cơ chúng áp dụng các quy tắc chung không nhất quán.

Interface và abstract class giải quyết hai nhu cầu khác nhau:

- **Interface** cho phép client phụ thuộc vào khả năng mà một đối tượng cung cấp, đồng thời một lớp có thể triển khai nhiều interface.
- **Abstract class** phù hợp khi các lớp con cần chia sẻ trạng thái instance, constructor, phương thức protected hoặc một thuật toán có các bước cố định.
- **Default method** hữu ích cho hành vi mặc định không cần trạng thái instance của lớp nền. Nếu các interface cung cấp default method xung đột, cần xử lý theo quy tắc kế thừa Java.
- **Composition** thường hợp lý hơn nếu chỉ cần tái sử dụng một dịch vụ độc lập hoặc nếu việc kế thừa tạo liên kết quá chặt. Java chỉ cho một superclass trực tiếp.

## 4. Những lỗi cần tránh

**Lỗi 1 — Lớp chưa hoàn thiện nhưng quên abstract.** Nếu **AbstractReportJob** bị đổi thành class thông thường trong khi vẫn thiếu **generate()**, chương trình không biên dịch. Cần giữ abstract hoặc cung cấp phần thân phù hợp.

**Lỗi 2 — Nghĩ rằng concrete subclass phải viết lại mọi phương thức.** **CsvReportJob** không cần override **validate()** hoặc **audit()** chỉ để hoàn thành interface, vì các phương thức đó đã được kế thừa hợp lệ.

**Lỗi 3 — Dùng abstract class để né xung đột kiểu.** Nếu hai hợp đồng yêu cầu kiểu trả về không tương thích, việc chuyển lớp thành abstract không tự làm những khai báo xung đột trở nên hợp lệ. Cần phân tích và sửa hợp đồng kiểu.

**Lỗi 4 — Gọi phương thức có thể override từ constructor lớp cha.** Constructor của abstract class chạy trước khi phần khởi tạo riêng của subclass hoàn tất. Nếu constructor gọi hook có thể bị override, implementation của lớp con có thể đọc trạng thái chưa được khởi tạo đầy đủ. Constructor nên thiết lập và kiểm tra trạng thái của lớp mình; hãy gọi hành vi đa hình sau khi đối tượng được tạo xong.

**Lỗi 5 — Dồn logic riêng của subclass vào lớp nền.** Nếu abstract class phải kiểm tra loại lớp con để chọn nhánh xử lý, có thể trách nhiệm chưa được phân chia hợp lý. Cân nhắc một phương thức abstract rõ ràng, một interface nhỏ hơn hoặc composition.

## 5. Tự kiểm tra

**Câu 1.** Interface **Vehicle** khai báo **start()** và **stop()**. Abstract class **BaseVehicle** đã triển khai **start()**. Một lớp concrete **Car** kế thừa **BaseVehicle** cần làm gì?

**Đáp án:** cung cấp **stop()**, trừ khi có triển khai hợp lệ khác được kế thừa.

**Câu 2.** Abstract class đã triển khai mọi phương thức interface có bắt buộc bỏ từ khóa abstract không?

**Đáp án:** không. Nó vẫn có thể được giữ abstract để biểu diễn rằng chỉ lớp con cụ thể mới nên được khởi tạo.

**Câu 3.** Nếu một abstract class không khai báo lại phương thức interface còn thiếu, Java có quên nghĩa vụ đó không?

**Đáp án:** không. Nghĩa vụ vẫn tồn tại trong hệ phân cấp và được kiểm tra khi một lớp concrete được khai báo.

**Ghi nhớ:** abstract class là nơi phù hợp để triển khai phần chung và trì hoãn phần chuyên biệt. Chỉ lớp concrete mới phải giải quyết toàn bộ nghĩa vụ abstract còn hiệu lực; hãy thiết kế rõ phần nào được kế thừa, phần nào là điểm mở rộng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-implementation-004">Dùng phương thức kế thừa từ lớp cha để đáp ứng interface</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng phương thức kế thừa từ lớp cha để đáp ứng interface

## 1. Ý tưởng cốt lõi

Khi một lớp khai báo `implements InterfaceName`, Java yêu cầu lớp cụ thể đó có cách triển khai hợp lệ cho mọi phương thức trừu tượng của interface. **Không bắt buộc phải viết lại phương thức ngay trong lớp khai báo `implements`**: một phương thức cụ thể, `public`, có chữ ký phù hợp được **kế thừa từ lớp cha** cũng có thể đáp ứng hợp đồng.

Ví dụ, lớp cha `BaseRecord` đã cung cấp `public String code()`. Lớp con `Record` kế thừa phương thức này và khai báo `implements HasCode`, vì vậy Java chấp nhận `Record` dù thân lớp không có phương thức `code()` riêng.

Cần phân biệt hai việc:

- **Có sẵn phương thức:** `BaseRecord` định nghĩa `code()` và `Record` được kế thừa cách thực hiện.
- **Mang kiểu interface:** chỉ lớp được khai báo là triển khai `HasCode` (hoặc kế thừa từ một lớp đã triển khai interface đó) mới có thể sử dụng như một giá trị `HasCode`. Việc lớp cha tình cờ có phương thức cùng chữ ký không tự động biến lớp cha thành một implementation của interface. Java dùng khai báo `implements`, không dùng cơ chế tự động coi mọi kiểu có đủ phương thức là một interface.

## 2. Ví dụ Java 17+ chạy được

Lưu vào tệp `InheritedInterfaceDemo.java`:

```java
public class InheritedInterfaceDemo {
    interface HasCode {
        String code(); // Mặc định là public abstract
    }

    static class BaseRecord {
        public String code() {
            return "BASE-001";
        }
    }

    static class Record extends BaseRecord implements HasCode {
        // Kế thừa BaseRecord.code(), không cần override.
    }

    static class SpecialRecord extends Record {
        @Override
        public String code() {
            return "SPECIAL-002";
        }
    }

    public static void main(String[] args) {
        HasCode first = new Record();
        HasCode second = new SpecialRecord();

        System.out.println(first.code());
        System.out.println(second.code());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 InheritedInterfaceDemo.java
java InheritedInterfaceDemo
```

Kết quả chính xác:

```text
BASE-001
SPECIAL-002
```

Diễn giải từng bước:

1. `HasCode.code()` là phương thức `public abstract` dù trong interface không ghi hai từ khóa đó.
2. `BaseRecord.code()` là phương thức instance `public`, trả về `String`, khớp với phương thức interface yêu cầu.
3. `Record extends BaseRecord implements HasCode`: compiler tìm được `code()` đã kế thừa, vì vậy `Record` là lớp cụ thể hợp lệ mà không phải định nghĩa thêm `code()`.
4. `SpecialRecord` override `code()`, nên lời gọi thông qua biến kiểu `HasCode` vẫn chọn cách triển khai theo **kiểu đối tượng thực tế**: `Record` in `BASE-001`, `SpecialRecord` in `SPECIAL-002`. Đây là đa hình với dynamic dispatch.

Lưu ý: `BaseRecord` **không** được khai báo là `HasCode`; câu `HasCode x = new BaseRecord();` không biên dịch được, trong khi `HasCode x = new Record();` hợp lệ.

## 3. Điều kiện để phương thức kế thừa đáp ứng hợp đồng

Một phương thức kế thừa chỉ thay thế được việc cài đặt phương thức trừu tượng của interface nếu thỏa các quy tắc override/implement của Java:

- **Đúng tên và danh sách tham số:** chỉ trùng tên nhưng khác kiểu hoặc số lượng tham số là overload, không triển khai yêu cầu. Với generic cần xét cả quy tắc chữ ký và *type erasure*.
- **Đúng loại phương thức:** interface yêu cầu một phương thức instance thì phương thức đáp ứng phải là instance; phương thức `static` cùng tên không thể làm implementation.
- **Đủ mức truy cập:** phương thức triển khai phải là `public`, vì phương thức abstract của interface là `public`. Phương thức `protected` hoặc package-private kế thừa từ lớp cha chưa đạt yêu cầu.
- **Kiểu trả về tương thích:** kiểu trả về phải giống hoặc là kiểu con hợp lệ (*covariant return*) theo quy tắc Java. Một phương thức chỉ trùng tên và tham số nhưng có kiểu trả về không tương thích sẽ không được chấp nhận.
- **Checked exceptions hợp lệ:** phương thức triển khai không được khai báo checked exception rộng hơn mức hợp đồng interface cho phép. Unchecked exception không có ràng buộc cùng loại này.
- **Phương thức đã có thân:** đối với lớp con cụ thể, phương thức `abstract` kế thừa chưa cung cấp implementation; lớp cụ thể vẫn phải cung cấp một implementation hợp lệ.

Một phương thức `public final` có sẵn ở lớp cha **vẫn có thể** đáp ứng interface nếu chữ ký tương thích. `final` chỉ ngăn lớp con override phương thức đó, không ngăn việc dùng implementation kế thừa để thỏa hợp đồng.

## 4. Lỗi phổ biến: phương thức lớp cha không phải public

Đoạn sau **cố ý không biên dịch**:

```java
interface Executable {
    void execute();
}

class Base {
    protected void execute() {
        System.out.println("Run");
    }
}

class Job extends Base implements Executable {
    // Lỗi: execute() kế thừa chỉ là protected,
    // trong khi Executable.execute() yêu cầu public.
}
```

`protected` cho phép lớp con truy cập phương thức, nhưng **không** đủ để triển khai hợp đồng `public` của interface. Có thể sửa bằng cách override với mức truy cập `public`:

```java
class Job extends Base implements Executable {
    @Override
    public void execute() {
        super.execute();
    }
}
```

Trong phiên bản sửa, `Job.execute()` là điểm truy cập `public` đáp ứng `Executable`; `super.execute()` vẫn tái sử dụng logic của lớp cha. Hai khối mã trong mục này minh họa lỗi và cách sửa, không phải các chương trình độc lập có `main`.

Một số bẫy tương tự:

- Lớp cha có `static void execute()` còn interface yêu cầu `void execute()`: phương thức static không thể thực hiện hợp đồng instance.
- Lớp cha có `public void execute(int n)` nhưng interface yêu cầu `void execute()`: khác tham số nên không đáp ứng.
- Lớp cha chỉ khai báo `public abstract void execute()`: lớp con cụ thể vẫn cần triển khai.
- Lớp cha có phương thức `private` cùng tên: phương thức private không được lớp con kế thừa như một phương thức có thể override để đáp ứng interface.

## 5. Nếu interface có phương thức default thì sao?

Quy tắc **class wins**: khi lớp cha cung cấp phương thức instance kế thừa thích hợp, nó được ưu tiên hơn phương thức `default` cùng chữ ký từ interface. Đây là hành vi có chủ đích, giúp thêm phương thức default vào interface mà không bất ngờ thay thế logic sẵn có của lớp cha.

Lưu đoạn sau vào tệp `ClassWinsDemo.java`:

```java
public class ClassWinsDemo {
    interface Greeter {
        default String greet() {
            return "from interface";
        }
    }

    static class Parent {
        public String greet() {
            return "from parent";
        }
    }

    static class Child extends Parent implements Greeter {
    }

    public static void main(String[] args) {
        Child child = new Child();
        Greeter asInterface = child;

        System.out.println(child.greet());
        System.out.println(asInterface.greet());
    }
}
```

Chạy `javac -encoding UTF-8 ClassWinsDemo.java`, rồi `java ClassWinsDemo`. Kết quả:

```text
from parent
from parent
```

Dù tham chiếu thứ hai có kiểu `Greeter`, phương thức được gọi vẫn là implementation kế thừa từ `Parent`. Nếu lớp con override `greet()` hợp lệ, implementation override mới được gọi.

## 6. Khi nào nên tận dụng phương thức kế thừa?

Cách này phù hợp khi lớp cha đã có implementation đúng ngữ nghĩa của interface và bạn muốn lớp con công bố rõ hợp đồng mà không nhân bản code. Chẳng hạn, một lớp cha đã có thao tác `id()` đáng tin cậy, còn lớp con cần được truyền vào API nhận interface `Identifiable`.

Khi đánh giá thiết kế, hãy tự hỏi:

1. Phương thức kế thừa có đúng **ý nghĩa** mà interface hứa hẹn không, hay chỉ tình cờ trùng chữ ký?
2. Phương thức có đáp ứng đủ `public`, chữ ký, kiểu trả về và checked exceptions không?
3. Có phải thay đổi trong lớp cha sẽ làm thay đổi hành vi của những lớp con triển khai interface không?
4. Nếu lớp con cần hành vi riêng, có nên override rõ ràng thay vì kế thừa nguyên bản không?

**Ghi nhớ:** `implements` xác lập cam kết về kiểu và hợp đồng; phương thức `public` phù hợp được kế thừa từ lớp cha có thể thực hiện cam kết đó. Java kiểm tra tính hợp lệ lúc biên dịch và chọn implementation phù hợp với đối tượng thực tế lúc chạy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-implementation-005">Một phương thức đáp ứng nhiều interface có cùng chữ ký</a>

<details>
<summary>Xem chi tiết</summary>

# Một phương thức đáp ứng nhiều interface có cùng chữ ký

Trong Java, một lớp có thể `implements` nhiều interface cùng yêu cầu một phương thức. Khi các khai báo có chữ ký phù hợp, **chỉ một phương thức instance trong lớp (hoặc kế thừa từ lớp cha) có thể đáp ứng tất cả các hợp đồng ấy**. Không cần, và cũng không thể, viết hai thân hàm chỉ khác nhau ở interface “sở hữu” phương thức.

Điều kiện không dừng ở tên và tham số: phương thức chung còn phải tương thích với mọi kiểu trả về, checked exception, mức truy cập và ý nghĩa hành vi do từng interface yêu cầu.

## 1. Ví dụ cơ bản: hai interface, một phương thức

Tạo tệp `SharedMethodDemo.java` (Java 17+):

```java
interface Describable {
    String code();
}

interface Trackable {
    String code();
}

final class Asset implements Describable, Trackable {
    private final String code;

    Asset(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}

public class SharedMethodDemo {
    public static void main(String[] args) {
        Asset asset = new Asset("asset-42");
        Describable described = asset;
        Trackable tracked = asset;

        System.out.println("Describable: " + described.code());
        System.out.println("Trackable: " + tracked.code());
        System.out.println("Direct: " + asset.code());
    }
}
```

Biên dịch và chạy:

```bash
javac SharedMethodDemo.java
java SharedMethodDemo
```

```text
Describable: asset-42
Trackable: asset-42
Direct: asset-42
```

`Describable.code()` và `Trackable.code()` đều khai báo `String code()`. Phương thức `public String code()` duy nhất của `Asset` thỏa mãn cả hai. Hai biến kiểu interface tham chiếu cùng đối tượng `Asset`, vì vậy lời gọi qua mỗi biến cùng đi tới phần thân triển khai ấy. Annotation `@Override` dùng một lần để trình biên dịch kiểm tra hợp đồng.

Kiểu tham chiếu interface xác định những phương thức nào được phép gọi khi biên dịch; lớp của đối tượng tại thời điểm chạy xác định implementation được gọi. Không có hai “bản sao” độc lập của `code()` chỉ vì có hai interface.

## 2. Cùng chữ ký khác với cùng kiểu trả về

Đối với phương thức thông thường, chữ ký (`method signature`) dựa trên **tên phương thức và danh sách kiểu tham số**, cùng những quy tắc bổ sung cho phương thức generic. Tên của biến tham số không ảnh hưởng chữ ký. Kiểu trả về và mệnh đề `throws` không tạo ra overload mới.

| Khai báo ở hai interface | Kết luận |
| --- | --- |
| `void save(String id)` và `void save(String key)` | Cùng chữ ký; một `public void save(String)` có thể đáp ứng cả hai. |
| `String find(int id)` và `String find(int id)` | Một phương thức phù hợp có thể triển khai cả hai. |
| `Number value()` và `Integer value()` | Có thể dùng `Integer value()` nhờ kiểu trả về covariant. |
| `void send(String s)` và `void send(int n)` | Khác chữ ký; cần hai overload nếu lớp triển khai cả hai. |
| `String value()` và `Integer value()` | Không có kiểu trả về tương thích cho cả hai khai báo này. |
| `int count()` và `long count()` | Không thể dùng một override để thỏa cả hai kiểu trả về nguyên thủy. |

**Kiểu trả về covariant** cho phép phương thức override trả về subtype tham chiếu của kiểu được khai báo trong hợp đồng. Ví dụ chạy được trong `CovariantReturnDemo.java`:

```java
interface NumericValue {
    Number value();
}

interface IntegralValue {
    Integer value();
}

final class NumberItem implements NumericValue, IntegralValue {
    @Override
    public Integer value() {
        return 42;
    }
}

public class CovariantReturnDemo {
    public static void main(String[] args) {
        NumberItem item = new NumberItem();
        NumericValue n = item;
        IntegralValue i = item;
        System.out.println(n.value());
        System.out.println(i.value());
    }
}
```

```bash
javac CovariantReturnDemo.java
java CovariantReturnDemo
```

```text
42
42
```

Vì `Integer` là subtype của `Number`, `Integer value()` tương thích với cả hai. Ngược lại, không thể viết hai phương thức `String value()` và `Integer value()` trong cùng lớp chỉ để phân biệt interface: Java không overload theo kiểu trả về.

## 3. Mức truy cập và checked exception cũng phải tương thích

Phương thức abstract thông thường trong interface có mức truy cập `public` mặc định. Phương thức triển khai phải là phương thức instance `public` thích hợp; bỏ `public` để thành package-private, dùng `protected` hay chuyển thành `static` không đáp ứng được hợp đồng instance đó.

Với checked exception, phương thức triển khai phải có khai báo `throws` hợp lệ **đối với từng interface**. Nếu một interface cho phép `IOException` nhưng interface kia chỉ cho phép `SQLException`, phương thức chung có thể không khai báo checked exception nào.

Ví dụ `CompatibleThrowsDemo.java`:

```java
import java.io.IOException;
import java.sql.SQLException;

interface FileAction {
    void run() throws IOException;
}

interface DatabaseAction {
    void run() throws SQLException;
}

final class UnifiedAction implements FileAction, DatabaseAction {
    @Override
    public void run() {
        System.out.println("finished");
    }
}

public class CompatibleThrowsDemo {
    public static void main(String[] args) {
        new UnifiedAction().run();
    }
}
```

```bash
javac CompatibleThrowsDemo.java
java CompatibleThrowsDemo
```

```text
finished
```

`UnifiedAction.run()` không khai báo checked exception nên không vi phạm bên nào. Nếu phương thức ấy khai báo `throws IOException`, nó không còn phù hợp với `DatabaseAction.run()`, vì `IOException` không phải subtype của `SQLException`. Các ngoại lệ unchecked như `RuntimeException` có quy tắc khác.

Một chi tiết quan trọng: nếu gọi `run()` qua biến có kiểu tĩnh `FileAction` hoặc `DatabaseAction`, trình biên dịch vẫn kiểm tra `throws` trong khai báo của **kiểu biến đó**, dù implementation `UnifiedAction` cụ thể không khai báo checked exception.

## 4. Trùng default method: buộc chọn hoặc kết hợp cách triển khai

Hai khai báo `abstract` cùng chữ ký có thể cùng được đáp ứng bằng một phương thức. Tuy nhiên, khi **hai interface độc lập cùng cung cấp `default` method cùng chữ ký**, Java không tự chọn một trong hai thân hàm.

Ví dụ `DefaultConflictDemo.java`:

```java
interface LeftTitle {
    default String title() {
        return "Left";
    }
}

interface RightTitle {
    default String title() {
        return "Right";
    }
}

final class CombinedTitle implements LeftTitle, RightTitle {
    @Override
    public String title() {
        return LeftTitle.super.title() + " & " + RightTitle.super.title();
    }
}

public class DefaultConflictDemo {
    public static void main(String[] args) {
        System.out.println(new CombinedTitle().title());
    }
}
```

```bash
javac DefaultConflictDemo.java
java DefaultConflictDemo
```

```text
Left & Right
```

Bỏ phương thức override ở `CombinedTitle` sẽ gây lỗi biên dịch về default method xung đột. Cú pháp `LeftTitle.super.title()` và `RightTitle.super.title()` gọi tường minh hai default implementation từ các direct superinterface. Đây là một lựa chọn; lớp cũng có thể tự viết hoàn toàn logic `title()` mới.

Một trường hợp khác là **phương thức concrete kế thừa từ lớp cha**. Phương thức `public` tương thích của lớp cha có thể triển khai interface và thường được ưu tiên trước default method (`class wins`):

```java
class ParentTitle {
    public String title() {
        return "class";
    }
}

interface Titled {
    default String title() {
        return "interface";
    }
}

final class ChildTitle extends ParentTitle implements Titled {
}

public class ClassMethodPriorityDemo {
    public static void main(String[] args) {
        System.out.println(new ChildTitle().title());
    }
}
```

Lưu trong `ClassMethodPriorityDemo.java`, rồi chạy:

```bash
javac ClassMethodPriorityDemo.java
java ClassMethodPriorityDemo
```

```text
class
```

`ChildTitle` không cần khai báo `title()` vì phương thức phù hợp của `ParentTitle` được kế thừa và đáp ứng hợp đồng `Titled`.

## 5. Phân biệt với overload và generic

Hai interface sau **không** có cùng chữ ký:

```java
interface TextSink {
    void send(String value);
}

interface NumberSink {
    void send(int value);
}

final class MixedSink implements TextSink, NumberSink {
    @Override
    public void send(String value) {
        System.out.println("text=" + value);
    }

    @Override
    public void send(int value) {
        System.out.println("number=" + value);
    }
}
```

Đây là đoạn mã minh họa lớp và interface, không có phương thức `main` độc lập. `MixedSink` phải có hai overload vì tham số lần lượt là `String` và `int`. Java chọn overload phù hợp với lời gọi lúc biên dịch.

Với generic, cần phân tích phương thức sau khi thay tham số kiểu và xét quy tắc `erasure`/`override-equivalent`. Chẳng hạn `interface Handler<T> { void handle(T v); }` được triển khai dưới dạng `Handler<String>` có yêu cầu `handle(String)`. Ngược lại, `List<String>` và `List<Integer>` cùng bị xóa về `List`; việc hai khai báo có cùng erasure có thể gây name clash hoặc đòi dùng kiểu thô với cảnh báo mất an toàn kiểu. Vì vậy không suy luận khả năng “một phương thức đáp ứng cả hai” chỉ bằng cách nhìn tên sau erasure.

Các phương thức `static` và `private` của interface không phải các phương thức instance abstract mà lớp `implements` phải ghi đè. Đừng nhầm tên trùng với việc đã triển khai hợp đồng.

## 6. Trùng chữ ký chưa bảo đảm đúng ý nghĩa hợp đồng

Giả sử `Auditable` yêu cầu `save(String id)` phải ghi sự kiện audit, còn `Persistable` yêu cầu chính chữ ký ấy phải lưu dữ liệu bền vững. Java có thể chấp nhận một phương thức `save(String)` về mặt kiểu, nhưng phần thân chỉ thực hiện audit sẽ **không** đáp ứng hành vi mà `Persistable` hứa hẹn.

Khi hợp nhất triển khai, cần làm rõ điều kiện đầu vào, tác dụng phụ, ngoại lệ và hậu điều kiện của *mọi* interface. Nếu các yêu cầu hành vi mâu thuẫn, nên xem lại thiết kế interface hoặc phân tách trách nhiệm; trình biên dịch không thể tự giải quyết mâu thuẫn ngữ nghĩa này.

## 7. Quy trình kiểm tra và lỗi phổ biến

1. Đặt khai báo của các interface cạnh nhau, so tên và kiểu tham số, kể cả generic sau khi thay kiểu.
2. Xác minh một kiểu trả về đáp ứng được tất cả; không cố phân biệt phương thức bằng return type.
3. Xác minh `public`, instance method và checked `throws` tương thích với mọi interface.
4. Xác định các khai báo là abstract hay default; nếu có hai default implementation xung đột, viết override rõ ràng.
5. Kiểm tra lớp cha đã có concrete method phù hợp hay chưa.
6. Gọi phương thức qua từng kiểu interface và viết test cho hành vi đã được mỗi hợp đồng hứa hẹn.

Lỗi hay gặp là viết phương thức package-private, khai báo một checked exception chỉ được một interface cho phép, quên override khi hai default method xung đột, hoặc cho rằng hai phương thức khác kiểu trả về là hai overload.

**Bài tập:** Tạo `EventPublisher` và `AuditPublisher` cùng khai báo `void publish(String message)`. Cho `ConsolePublisher` implements cả hai bằng một `public` method. Gọi qua hai biến interface, giải thích vì sao cùng một phần thân được thực thi. Sau đó thêm `default String channel()` vào cả hai interface với hai giá trị khác nhau; tự sửa xung đột bằng một override và chạy `javac` để kiểm chứng.

**Ghi nhớ:** Một implementation có thể thực hiện nhiều hợp đồng interface cùng chữ ký; kiểu trả về, checked exception và hành vi phải cùng thỏa mãn các hợp đồng. Hai default method xung đột phải được giải quyết tường minh, còn phương thức `public` phù hợp kế thừa từ lớp cha có thể đã đủ để đáp ứng interface.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-implementation-006">Nhiều interface có yêu cầu kiểu trả về không tương thích</a>

<details>
<summary>Xem chi tiết</summary>

# Nhiều interface có yêu cầu kiểu trả về không tương thích trong Java

## 1. Vấn đề khi triển khai đồng thời nhiều hợp đồng

Một lớp Java có thể `implements` nhiều interface. Tuy nhiên, nếu các interface cùng yêu cầu một phương thức có **cùng chữ ký** nhưng đòi hỏi **kiểu trả về không tương thích**, một lớp triển khai không thể thỏa mãn đồng thời các hợp đồng đó.

Xét hai interface:

~~~java
interface TextId {
    String id();
}

interface NumericId {
    Integer id();
}
~~~

Cả hai đều khai báo phương thức `id()`, không có tham số. Điểm khác biệt là `TextId.id()` yêu cầu trả về `String`, còn `NumericId.id()` yêu cầu trả về `Integer`.

Nếu viết:

~~~java
// Ví dụ CỐ Ý SAI: không biên dịch.
final class BrokenRecord implements TextId, NumericId {
    @Override
    public String id() {
        return "42";
    }
}
~~~

Trình biên dịch từ chối, vì `String id()` không triển khai được yêu cầu `Integer id()`. Đổi thành `Integer id()` thì lại vi phạm `TextId`. `String` và `Integer` là hai kiểu lớp không có quan hệ lớp con để kiểu trả về của phương thức duy nhất thay thế được cho cả hai. Nội dung thông báo lỗi cụ thể có thể khác theo phiên bản `javac` và dạng mã, nhưng đây là **lỗi biên dịch**, không phải lỗi chỉ xảy ra khi chương trình chạy.

Một interface kế thừa đồng thời hai hợp đồng không tương thích cũng gặp lỗi:

~~~java
// Ví dụ CỐ Ý SAI: không biên dịch.
interface CombinedId extends TextId, NumericId {
}
~~~

Vấn đề xuất hiện ngay trong quan hệ kế thừa hợp đồng; không thể trì hoãn việc giải quyết bằng cách chờ một lớp khác triển khai.

## 2. Vì sao khác kiểu trả về vẫn bị xem là trùng phương thức?

Trong Java, chữ ký phương thức dùng để xét việc overload/override dựa vào **tên phương thức** và **danh sách kiểu tham số** (cùng các quy tắc liên quan đến phương thức generic). **Kiểu trả về không được dùng để phân biệt hai overload.**

Do đó:

~~~java
// Ví dụ CỐ Ý SAI: không biên dịch.
class InvalidOverload {
    String read() {
        return "text";
    }

    int read() {
        return 123;
    }
}
~~~

Hai phương thức `read()` không khác tên hoặc tham số; không thể cùng tồn tại chỉ vì một phương thức trả về `String` còn phương thức kia trả về `int`.

Ngược lại, khi tham số khác nhau, ta có overload hợp lệ:

~~~java
// Hai chữ ký khác nhau: convert(String) và convert(int).
class Converter {
    int convert(String value) {
        return Integer.parseInt(value);
    }

    String convert(int value) {
        return Integer.toString(value);
    }
}
~~~

Đây là **đoạn minh họa** không có `main`; nếu dùng, truyền tham số đúng kiểu. Không nên đổi tham số một cách tùy tiện chỉ để tránh xung đột khi nghiệp vụ cần cùng thao tác không tham số.

## 3. Quy tắc tương thích kiểu trả về khi ghi đè

Java áp dụng quy tắc **return-type-substitutability**: kiểu trả về của phương thức triển khai phải thay thế hợp lệ kiểu trả về mà hợp đồng yêu cầu. Ba nhóm quan trọng cho người mới học là:

| Kiểu trả về trong hợp đồng | Phương thức triển khai phù hợp | Không phù hợp |
| --- | --- | --- |
| `void` | `void` | `String`, `int`, ... |
| Kiểu nguyên thủy `int` | `int` | `long`, `Integer` |
| Kiểu tham chiếu `CharSequence` | `CharSequence` hoặc kiểu con như `String` | Kiểu rộng hơn như `Object` |

Với **kiểu nguyên thủy**, cùng chữ ký phải dùng kiểu trả về nguyên thủy giống hệt; khả năng tự động chuyển `int` sang `long` khi dùng biểu thức không biến một phương thức `int get()` thành triển khai của `long get()`. Autoboxing cũng không biến `int get()` thành triển khai hợp lệ của `Integer get()`.

Với **kiểu tham chiếu**, Java hỗ trợ **kiểu trả về hiệp biến** (*covariant return type*): phương thức triển khai có thể trả về kiểu cụ thể hơn, miễn thỏa quy tắc thay thế kiểu trả về. Ví dụ, `String` thực hiện `CharSequence`, nên `String` thay thế được cho yêu cầu `CharSequence`. Một số trường hợp generic/unchecked được quy định riêng trong Java Language Specification; không nên diễn giải quy tắc bằng việc chỉ nhìn tên hai kiểu.

### Ví dụ hợp lệ: kiểu trả về hiệp biến

Lưu toàn bộ chương trình sau vào `CovariantReturnDemo.java`:

~~~java
interface TextView {
    CharSequence render();
}

interface StringView {
    String render();
}

final class MarkdownView implements TextView, StringView {
    @Override
    public String render() {
        return "Học Java";
    }
}

public class CovariantReturnDemo {
    public static void main(String[] args) {
        MarkdownView view = new MarkdownView();

        TextView general = view;
        StringView specific = view;

        System.out.println(general.render());
        System.out.println(specific.render().length());
    }
}
~~~

Biên dịch và chạy với Java 17+:

~~~bash
javac -encoding UTF-8 CovariantReturnDemo.java
java CovariantReturnDemo
~~~

Kết quả chính xác:

~~~text
Học Java
8
~~~

Giải thích: `MarkdownView.render()` chỉ có **một** phương thức triển khai, trả về `String`. Giá trị này đáp ứng cả yêu cầu `CharSequence render()` lẫn `String render()`. Biến `general` chỉ nhìn thấy hợp đồng `CharSequence`; biến `specific` biết kiểu trả về là `String` và có thể gọi `length()`. Chuỗi `Học Java` có tám ký tự.

Đây là điểm quyết định: **khác kiểu trả về chưa chắc đã xung đột**. Xung đột chỉ trở thành vấn đề khi không có kiểu trả về hợp lệ cho phương thức triển khai theo các quy tắc của Java.

### Mở rộng interface với kiểu trả về cụ thể hơn

Một interface con cũng có thể khai báo lại phương thức bằng kiểu trả về hiệp biến:

~~~java
interface GeneralResult {
    Number value();
}

interface IntegerResult extends GeneralResult {
    @Override
    Integer value();
}

final class Counter implements IntegerResult {
    @Override
    public Integer value() {
        return 7;
    }
}
~~~

Đây là đoạn khai báo hợp lệ, nhưng chưa có chương trình `main`. Vì `Integer` là lớp con của `Number`, `IntegerResult.value()` tuân thủ hợp đồng từ `GeneralResult`. Một instance `Counter` có thể được sử dụng qua cả hai interface.

## 4. Những xung đột thật sự không thể giải quyết bằng một phương thức

### Hai kiểu tham chiếu không thể cùng được thỏa mãn

~~~java
interface TextReader {
    String read();
}

interface NumericReader {
    Integer read();
}

// CỐ Ý SAI.
class DualReader implements TextReader, NumericReader {
    @Override
    public Object read() {
        return "data";
    }
}
~~~

`Object` có khả năng giữ giá trị `String` hoặc `Integer` tại runtime, nhưng **kiểu trả về khai báo** là `Object` quá rộng so với cả `String` lẫn `Integer`. Vì thế phương thức trên không triển khai đúng hai hợp đồng. Thay `Object` bằng `String` hay `Integer` cũng chỉ thỏa được một phía.

Không thể khai báo thêm `Integer read()` bên cạnh `String read()`, bởi Java không overload theo kiểu trả về. Ép kiểu trong thân phương thức, thay đổi giá trị trả về lúc runtime, thêm `@Override` hay sử dụng `default` không làm biến mất sự không tương thích của kiểu khai báo.

### Xung đột kiểu nguyên thủy và void

~~~java
interface IntMetric {
    int measure();
}

interface LongMetric {
    long measure();
}

// CỐ Ý SAI.
interface ConflictingMetric extends IntMetric, LongMetric {
}
~~~

`int` và `long` khác nhau; một phương thức `measure()` không thể thỏa cả hai chỉ nhờ widening conversion. Tương tự, `void execute()` và `boolean execute()` có cùng chữ ký nhưng kiểu trả về không tương thích. Trường hợp `int` và `Integer` cũng không được giải quyết bằng autoboxing.

### Chú ý với generic

Các kiểu tham số hóa như `List<String>` và `List<Integer>` không có quan hệ subtype với nhau chỉ vì cùng bắt đầu bằng `List`:

~~~java
interface Names {
    java.util.List<String> items();
}

interface Counts {
    java.util.List<Integer> items();
}

// CỐ Ý SAI.
interface ImpossibleCatalog extends Names, Counts {
}
~~~

Ví dụ này cũng bị lỗi vì các phương thức cùng chữ ký có yêu cầu kiểu trả về generic không tương thích. Generic trong Java không tự biến `List<String>` thành một kiểu con của `List<Integer>`. Cần cân nhắc thiết kế lại hợp đồng, loại dữ liệu hoặc đặt tên hành vi khác nhau; dùng raw type/ép kiểu không an toàn để né lỗi chỉ làm mất kiểm tra kiểu.

**Lưu ý nâng cao:** Hai kiểu trả về là interface *khác nhau* không mặc nhiên có nghĩa là không thể triển khai đồng thời. Một lớp cụ thể có thể thực hiện cả hai interface kiểu trả về đó; nếu phương thức của lớp trả về kiểu cụ thể phù hợp với cả hai, hai hợp đồng có thể cùng được thỏa mãn. Hãy phân tích khả năng thay thế kiểu thực sự, thay vì suy luận từ việc tên kiểu trả về khác nhau. Tuy nhiên, kế thừa đồng thời nhiều khai báo trong một interface con có quy tắc riêng về các phương thức được thừa kế; đừng giả định một cách giải cho class sẽ làm mọi `interface extends A, B` hợp lệ.

## 5. Cách giải quyết khi hợp đồng không tương thích

### Cách A: Điều chỉnh tên hoặc ý nghĩa phương thức nếu kiểm soát được API

Nếu hai hành vi vốn khác nhau về ý nghĩa, tách rõ tên hợp đồng:

~~~java
interface TextIdentifier {
    String textId();
}

interface NumericIdentifier {
    int numericId();
}

final class Product implements TextIdentifier, NumericIdentifier {
    private final int number;

    Product(int number) {
        this.number = number;
    }

    @Override
    public String textId() {
        return "P-" + number;
    }

    @Override
    public int numericId() {
        return number;
    }
}
~~~

Hai phương thức `textId()` và `numericId()` có chữ ký khác nhau. Cách đặt tên này còn làm rõ một giá trị dùng làm mã hiển thị, một giá trị dùng làm mã số nội bộ. Đây là đoạn triển khai minh họa; không có `main`.

### Cách B: Thống nhất hợp đồng về kiểu trả về có quan hệ subtype

Nếu hai interface thực sự mô tả cùng một thao tác, có thể sửa API của mình để sử dụng kiểu phù hợp, chẳng hạn một hợp đồng trả về `Number` và một hợp đồng khác trả về `Integer`. Khi đó phương thức trả về `Integer` có thể đáp ứng cả hai, như ví dụ `GeneralResult` / `IntegerResult` ở trên.

Không nên ép các hợp đồng dùng một kiểu chung quá rộng như `Object` chỉ để biên dịch: điều này chuyển gánh nặng kiểm tra kiểu sang các nơi sử dụng. Việc thay đổi kiểu trả về của public API cũng có thể ảnh hưởng mã nguồn/nhị phân phụ thuộc, nên cần xem xét phạm vi tương thích khi sửa thư viện đã phát hành.

### Cách C: Dùng hai adapter khi không thể sửa interface gốc

Nếu `TextId` và `NumericId` là hợp đồng của thư viện bên ngoài, một đối tượng dữ liệu dùng chung có thể được bao bởi **hai lớp adapter** khác nhau. Mỗi adapter chỉ triển khai hợp đồng của mình, tránh buộc một class phải có hai `id()` với kiểu trả về không tương thích.

Ví dụ sau là chương trình đầy đủ. Lưu thành `IdAdapterDemo.java`:

~~~java
interface TextId {
    String id();
}

interface NumericId {
    Integer id();
}

final class RecordData {
    private final int value;

    RecordData(int value) {
        this.value = value;
    }

    int number() {
        return value;
    }
}

final class TextIdAdapter implements TextId {
    private final RecordData data;

    TextIdAdapter(RecordData data) {
        this.data = data;
    }

    @Override
    public String id() {
        return "ID-" + data.number();
    }
}

final class NumericIdAdapter implements NumericId {
    private final RecordData data;

    NumericIdAdapter(RecordData data) {
        this.data = data;
    }

    @Override
    public Integer id() {
        return data.number();
    }
}

public class IdAdapterDemo {
    public static void main(String[] args) {
        RecordData data = new RecordData(42);
        TextId textApi = new TextIdAdapter(data);
        NumericId numericApi = new NumericIdAdapter(data);

        System.out.println(textApi.id());
        System.out.println(numericApi.id() + 1);
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 IdAdapterDemo.java
java IdAdapterDemo
~~~

Đầu ra:

~~~text
ID-42
43
~~~

`TextIdAdapter` và `NumericIdAdapter` cùng sử dụng `RecordData`, nhưng mỗi adapter có một trách nhiệm và hợp đồng riêng. Dữ liệu gốc là `42`; phía văn bản nhận `"ID-42"`, phía số nhận `Integer` có giá trị `42`, cộng 1 được `43`. Khi thay đổi lớp cung cấp dữ liệu, chỉ cần cập nhật adapter tương ứng.

Nếu yêu cầu bắt buộc một lớp duy nhất phải trực tiếp `implements TextId, NumericId` với hai kiểu `String`/`Integer` cho cùng `id()`, thì **không có cách viết một phương thức Java hợp lệ** để giữ nguyên đồng thời cả hai hợp đồng đó. Cần đổi mô hình hoặc dùng lớp trung gian riêng.

## 6. Phân biệt xung đột kiểu trả về với xung đột default method

Cần xác định đúng nguyên nhân:

- **Xung đột return type:** cùng chữ ký nhưng kiểu trả về không thể thay thế nhau. Viết lại phần thân, thêm `@Override` hay chọn một `default` không chữa được mâu thuẫn kiểu.
- **Xung đột default method:** hai interface cung cấp implementation mặc định trùng chữ ký. Khi các kiểu trả về tương thích, lớp triển khai có thể cần ghi đè tường minh để chọn hoặc kết hợp hành vi; đây là bài toán ưu tiên implementation, khác với bài toán hợp đồng kiểu.

Ví dụ, nếu `interface A { default String label() { return "A"; } }` và `interface B { default String label() { return "B"; } }`, lớp triển khai cả hai phải tự định nghĩa `public String label()` (có thể gọi `A.super.label()` hoặc `B.super.label()` khi hợp lệ). Việc cả hai trả về `String` không tự quyết định lớp chọn thân phương thức nào.

## 7. Quy trình chẩn đoán lỗi biên dịch

Khi `class X implements First, Second` hoặc `interface Combined extends First, Second` gặp lỗi quanh một phương thức, thực hiện lần lượt:

1. **So sánh tên và tham số:** chúng có cùng chữ ký hoặc *override-equivalent* không? Nếu khác danh sách tham số, có thể là hai overload thông thường.
2. **So sánh kiểu trả về khai báo:** trùng nhau, hiệp biến, hay không thể thay thế? Nhớ phân biệt primitive, `void`, reference type và generic.
3. **Kiểm tra phương thức triển khai:** có đúng mức truy cập `public` và kiểu trả về thay thế hợp lệ cho **từng** interface cần triển khai không?
4. **Xác định quyền kiểm soát API:** tự sở hữu interface thì có thể đổi tên hoặc chuẩn hóa kiểu; interface bên thứ ba thì thường dùng composition/adapter.
5. **Biên dịch lại tại chỗ:** lỗi xảy ra ở compile time nên không thể dùng `instanceof` hoặc bắt ngoại lệ runtime để thay cho việc chỉnh khai báo.

Thông điệp lỗi có thể có dạng “return type is not compatible” hoặc “inherits abstract and default / unrelated defaults” trong các trường hợp khác nhau. Đọc tên phương thức, kiểu đang được yêu cầu và nơi kế thừa để phân biệt nguyên nhân thật.

## 8. Câu hỏi tự kiểm tra

1. Hai interface cùng khai báo `String fetch()` và `Integer fetch()`: một class triển khai cả hai có thể thêm hai phương thức chỉ khác kiểu trả về hay không?
2. Vì sao `String render()` có thể triển khai `CharSequence render()`?
3. Nếu hợp đồng yêu cầu `long total()`, trả về `int total()` có hợp lệ vì int chuyển thành long được hay không?
4. `List<String> items()` và `List<Integer> items()` có tự động tương thích vì cùng là `List` không?
5. Nếu hai thư viện cung cấp interface có `id()` trả về hai kiểu không thể hòa giải và không thể chỉnh thư viện, cách thiết kế nào giữ được cả hai hợp đồng?

**Gợi ý đáp án:** (1) Không; Java không overload theo return type. (2) Vì `String` là subtype của `CharSequence` (covariant return). (3) Không, primitive return phải cùng kiểu. (4) Không, kiểu generic khác nhau không mặc nhiên có quan hệ subtype. (5) Dùng các adapter riêng cùng khai thác dữ liệu/lõi xử lý chung, hoặc sửa kiến trúc nơi tích hợp.

## 9. Đọc thêm từ đặc tả Java

- [JLS `8.4.2` — Method Signature](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.2): quy tắc chữ ký phương thức và overload.
- [JLS `8.4.5` — Method Result](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.5): kiểu trả về và quy tắc return-type-substitutability.
- [JLS `9.4.1.3` — Inheriting Methods with Override-Equivalent Signatures](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.4.1.3): các phương thức cùng chữ ký thừa kế trong interface.

**Ghi nhớ:** nhiều interface chỉ được kết hợp an toàn khi một cách triển khai hợp lệ có thể đáp ứng tất cả phương thức cùng chữ ký của chúng. Khi các yêu cầu return type thực sự mâu thuẫn, hãy sửa hợp đồng hoặc tách lớp thích ứng, thay vì tìm cách overload theo kiểu trả về.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-implementation-007">Triển khai tường minh bằng phương thức có @Override</a>

<details>
<summary>Xem chi tiết</summary>

# Triển khai hợp đồng tường minh bằng phương thức có @Override

Trong Java, **@Override** là annotation đặt trên khai báo phương thức để compiler xác nhận rằng phương thức thực sự override một phương thức kế thừa hoặc triển khai một phương thức được khai báo trong interface. Nó thể hiện ý định và giúp phát hiện sai chữ ký ngay khi biên dịch.

Java không có cú pháp riêng để tạo một bản triển khai interface tách biệt theo tên interface như một số ngôn ngữ khác. Ở đây, “tường minh” nghĩa là viết rõ phương thức triển khai và đánh dấu **@Override**.

## 1. Ví dụ hoàn chỉnh

Chương trình sau minh họa cả triển khai interface và override phương thức đã có thân trong lớp cha. Lưu thành **OverrideDemo.java**:

~~~java
public class OverrideDemo {
    interface Formatter {
        String format(String value);
    }

    static class BasicFormatter implements Formatter {
        @Override
        public String format(String value) {
            return value.trim();
        }
    }

    static final class BracketFormatter extends BasicFormatter {
        @Override
        public String format(String value) {
            return "[" + super.format(value) + "]";
        }
    }

    public static void main(String[] args) {
        Formatter formatter = new BracketFormatter();
        System.out.println(formatter.format(" Java "));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac OverrideDemo.java
java OverrideDemo
~~~

Kết quả:

~~~text
[Java]
~~~

**BasicFormatter.format()** đáp ứng hợp đồng của interface. **BracketFormatter.format()** override phương thức có thân của lớp cha và dùng **super.format(value)** để tái sử dụng xử lý trim. Dù biến **formatter** có kiểu tĩnh là interface, lúc chạy Java gọi implementation của **BracketFormatter** theo đối tượng thực tế.

## 2. Annotation kiểm tra điều gì?

| @Override giúp compiler kiểm tra | @Override không làm |
| --- | --- |
| Phương thức có quan hệ override/implement hợp lệ với kiểu cha hoặc interface | Không tự sinh phần thân phương thức |
| Phát hiện nhầm tên, sai kiểu tham số hoặc một overload bị tưởng nhầm là override | Không thay đổi mức truy cập thành public |
| Phát hiện ý định override một phương thức không thể override | Không tự gọi phương thức lớp cha |
| Thể hiện rõ ý định với người đọc và công cụ phân tích | Không chứng minh logic đáp ứng đầy đủ ý nghĩa nghiệp vụ |

Về mặt cú pháp, annotation không bắt buộc cho mọi phương thức override hợp lệ. Tuy nhiên, nên dùng nó thường xuyên: nếu chữ ký thay đổi ở interface hoặc superclass, compiler có thể chỉ ra các phương thức không còn override đúng như dự định.

## 3. Phân biệt override và overload

Đoạn sau được cố ý viết sai:

~~~java
interface Printer {
    void print(String value);
}

class ConsolePrinter implements Printer {
    @Override
    public void print(Object value) {
        System.out.println(value);
    }
}
~~~

Hợp đồng yêu cầu **print(String)** nhưng lớp lại khai báo **print(Object)**. Hai phương thức khác kiểu tham số nên không có quan hệ override. **@Override** khiến compiler báo lỗi ngay tại khai báo; nếu xóa annotation, lớp concrete vẫn chưa triển khai **print(String)** nên vẫn không hợp lệ.

Tên biến tham số không ảnh hưởng chữ ký: **print(String text)** có thể triển khai **print(String value)**. Thay đổi kiểu tham số, số lượng tham số hoặc thứ tự kiểu tham số có thể tạo overload khác. Kiểu trả về cũng không thể được dùng riêng để phân biệt hai overload; kiểu trả về của phương thức override phải tuân theo quy tắc tương thích của Java.

## 4. Dùng @Override với các loại phương thức khác nhau

- **Triển khai phương thức abstract của interface:** khai báo phương thức có thân và giữ đúng chữ ký, kiểu trả về cùng mức truy cập public mà hợp đồng yêu cầu.
- **Hoàn thiện phương thức abstract từ superclass:** đánh dấu phương thức cụ thể để compiler xác nhận nó hoàn thành đúng khai báo abstract.
- **Thay đổi hành vi của phương thức concrete trong superclass:** có thể override nếu phương thức không final, private hoặc static theo cách khiến quan hệ override không hợp lệ.
- **Override default method:** có thể dùng annotation khi muốn thay hành vi mặc định của interface. Nếu nhiều default method xung đột, lớp cần giải quyết xung đột theo quy tắc Java.
- **Phương thức chỉ được kế thừa mà không khai báo lại:** không có chỗ để gắn annotation. Không cần viết lại phương thức chỉ để thêm @Override.

Annotation không làm một phương thức static trở thành instance method, không cho phép override phương thức final và không khiến phương thức private của lớp cha trở thành phương thức có thể override. Khi muốn gọi logic lớp cha, phải viết lời gọi **super.method(...)** tường minh.

## 5. Quy trình triển khai an toàn

1. Đọc khai báo interface hoặc superclass để biết chính xác phương thức cần triển khai.
2. Viết đúng tên và kiểu tham số, kiểu trả về tương thích, cùng quyền truy cập cần thiết.
3. Đặt **@Override** trước phương thức và biên dịch. Nếu compiler báo không override được, hãy kiểm tra chữ ký thay vì xóa annotation để che lỗi.
4. Viết phần thân thực hiện hành vi được hợp đồng yêu cầu. Annotation chỉ kiểm tra quan hệ kiểu, không xác minh kết quả nghiệp vụ.
5. Kiểm thử cả lời gọi qua biến kiểu interface/superclass lẫn các trường hợp biên của hành vi cụ thể.

## 6. Tự kiểm tra

**Câu 1.** Interface **Converter** khai báo **String convert(String value)**. Phương thức **public String convert(String input)** có thể triển khai nó không?

**Đáp án:** có; tên tham số không tham gia chữ ký.

**Câu 2.** Phương thức **public Object convert(String value)** có thể override khai báo trên không?

**Đáp án:** không; kiểu trả về Object rộng hơn String nên không đáp ứng quy tắc tương thích kiểu trả về.

**Câu 3.** Nếu bỏ @Override khỏi một phương thức có sai kiểu tham số, compiler có tự coi nó là override không?

**Đáp án:** không. Annotation không tạo quan hệ override; việc thiếu annotation cũng không sửa được phương thức còn thiếu.

**Ghi nhớ:** viết phương thức cụ thể để đáp ứng hợp đồng, rồi dùng @Override để compiler kiểm tra ý định. Annotation phát hiện lỗi quan hệ phương thức sớm, còn tính đúng đắn về hành vi vẫn phải được đánh giá riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-implementation-008">Không tự động trở thành kiểu con chỉ vì có phương thức giống nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Java: Có phương thức giống nhau vẫn chưa phải kiểu con

Trong Java, **một lớp không tự động trở thành kiểu con của interface hoặc lớp khác chỉ vì nó có phương thức cùng tên, cùng tham số và cùng kiểu trả về**. Quan hệ kiểu con phải được thiết lập theo hệ thống kiểu danh nghĩa (*nominal typing*) qua `implements`, `extends` hoặc quan hệ kế thừa đã được khai báo. Đây là điểm cốt lõi khi **triển khai hợp đồng** bằng interface.

Sau bài này, bạn có thể giải thích vì sao mã có phương thức phù hợp vẫn không truyền được vào tham số interface, khai báo đúng quan hệ kiểu con và dùng adapter khi không thể sửa lớp có sẵn.

## 1. Chữ ký phương thức và quan hệ kiểu con là hai việc khác nhau

Xét hai khai báo:

```java
interface MessageSender {
    void send(String message);
}

final class ConsoleSender {
    public void send(String message) {
        System.out.println(message);
    }
}
```

`ConsoleSender` có phương thức `send(String)` trùng chữ ký mà `MessageSender` yêu cầu. Nhưng `ConsoleSender` **chưa khai báo `implements MessageSender`**, nên trình biên dịch không coi nó là `MessageSender`.

- **Có phương thức phù hợp** mô tả khả năng gọi một phương thức trên một đối tượng với kiểu đã biết.
- **Là kiểu con** mô tả một quan hệ kiểu được Java công nhận: đối tượng của kiểu con có thể được sử dụng tại vị trí yêu cầu kiểu cha/interface.
- **Triển khai hợp đồng** có hai phần: khai báo tham gia hợp đồng (`implements`) và cung cấp hoặc kế thừa các phương thức trừu tượng cần thiết với cách triển khai hợp lệ.

Java dùng **nominal typing** cho các kiểu lớp và interface: danh tính kiểu cùng các quan hệ kế thừa được khai báo quyết định khả năng gán và truyền tham số. Trong hệ thống *structural typing* của một số ngôn ngữ khác, hình dạng thành viên có thể đủ để thỏa mãn một kiểu; đừng áp dụng suy luận đó cho Java.

## 2. Ví dụ không biên dịch dù có phương thức giống nhau

Lưu đoạn dưới đây thành `Main.java` để quan sát lỗi có chủ ý:

```java
public class Main {
    interface MessageSender {
        void send(String message);
    }

    static final class ConsoleSender {
        public void send(String message) {
            System.out.println("Gửi: " + message);
        }
    }

    static void dispatch(MessageSender sender) {
        sender.send("xin chào");
    }

    public static void main(String[] args) {
        ConsoleSender sender = new ConsoleSender();
        dispatch(sender); // Lỗi biên dịch: không phải MessageSender
    }
}
```

Chạy `javac Main.java` sẽ **không biên dịch thành công**. Tại lời gọi `dispatch(sender)`, Java cần một giá trị có thể chuyển thành `MessageSender`, nhưng kiểu `ConsoleSender` không có quan hệ kiểu con tương ứng. Thông báo thường mang ý nghĩa `ConsoleSender cannot be converted to MessageSender`; cách trình bày cụ thể phụ thuộc JDK.

Việc phương thức `send(String)` là `public`, có đúng tên và chạy được khi gọi `sender.send("...")` **không thay thế** khai báo `implements`. Tương tự, phép gán `MessageSender target = new ConsoleSender();` cũng không hợp lệ.

## 3. Giải pháp: khai báo `implements` và thực hiện hợp đồng

Sửa `ConsoleSender` để khai báo chính thức interface. Đây là phiên bản **độc lập, biên dịch và chạy được trên Java 17+**:

```java
public class Main {
    interface MessageSender {
        void send(String message);
    }

    static final class ConsoleSender implements MessageSender {
        @Override
        public void send(String message) {
            System.out.println("Gửi: " + message);
        }
    }

    static void dispatch(MessageSender sender) {
        sender.send("xin chào");
    }

    public static void main(String[] args) {
        ConsoleSender concrete = new ConsoleSender();
        MessageSender abstraction = concrete; // Phép gán hợp lệ
        dispatch(abstraction);
    }
}
```

Biên dịch và chạy:

```bash
javac Main.java
java Main
```

Kết quả stdout:

```text
Gửi: xin chào
```

Lúc này, `ConsoleSender implements MessageSender` thiết lập quan hệ kiểu con, còn `public void send(String)` đáp ứng phương thức trừu tượng. `@Override` giúp trình biên dịch kiểm tra phương thức thực sự ghi đè hoặc triển khai một phương thức từ kiểu cha/interface; annotation này **không tự tạo quan hệ kiểu con**. Với lớp cụ thể khai báo `implements` nhưng chưa triển khai đủ phương thức trừu tượng được yêu cầu, Java báo lỗi biên dịch (hoặc lớp phải là `abstract`).

Điểm quan trọng của đa hình là `dispatch` chỉ phụ thuộc `MessageSender`. Khi chạy, lời gọi `sender.send(...)` được phân phối đến cách triển khai tương ứng với đối tượng thực tế. Người gọi không cần biết đối tượng được tạo từ lớp nào, miễn kiểu của nó thỏa hợp đồng.

## 4. Quan hệ có thể được kế thừa, không cần lặp lại `implements`

Một lớp cũng có thể trở thành kiểu con của interface **gián tiếp** thông qua lớp cha hoặc interface cha. Ví dụ sau là các khai báo minh họa, có thể đặt cùng một tệp Java trong một package thích hợp:

```java
interface Sender {
    void send(String text);
}

class BaseSender implements Sender {
    @Override
    public void send(String text) {
        System.out.println(text);
    }
}

class LoggedSender extends BaseSender {
    // Kế thừa quan hệ Sender và cách triển khai send(String).
}
```

`LoggedSender` không ghi trực tiếp `implements Sender`, nhưng vì `LoggedSender extends BaseSender` và `BaseSender implements Sender`, nên `LoggedSender` vẫn là `Sender`. Phép gán `Sender s = new LoggedSender();` hợp lệ. Lớp con có thể kế thừa phương thức thực thi phù hợp hoặc ghi đè nó theo quy tắc Java.

Tương tự, khi `interface Child extends Parent`, mọi lớp cụ thể triển khai `Child` cũng có quan hệ kiểu con với `Parent`. Điều quyết định vẫn là **chuỗi quan hệ kiểu đã khai báo**, không phải Java tự so khớp các tên phương thức.

## 5. Khi không thể sửa lớp cũ: dùng adapter

Đôi khi bạn sử dụng thư viện ngoài có phương thức đúng nhu cầu nhưng lớp không `implements` interface của ứng dụng. Không thể truyền trực tiếp đối tượng ấy vào API nhận interface. Có thể viết một adapter để **chuyển lời gọi**, giữ ranh giới hợp đồng rõ ràng.

Ví dụ đầy đủ với một *functional interface* (interface có đúng một phương thức trừu tượng):

```java
public class AdapterExample {
    @FunctionalInterface
    interface Printer {
        void print(String text);
    }

    static final class LegacyPrinter {
        public void print(String text) {
            System.out.println("Legacy: " + text);
        }
    }

    static void printNotice(Printer printer) {
        printer.print("ready");
    }

    public static void main(String[] args) {
        LegacyPrinter legacy = new LegacyPrinter();

        // printNotice(legacy); // Không hợp lệ: thiếu quan hệ kiểu con.
        Printer adapter = legacy::print;
        printNotice(adapter);
    }
}
```

```bash
javac AdapterExample.java
java AdapterExample
```

Stdout:

```text
Legacy: ready
```

`legacy::print` là **method reference** được chuyển theo *target type* `Printer` thành một đối tượng thực thi phương thức trừu tượng của functional interface. Nó **không biến `LegacyPrinter` thành kiểu con của `Printer`**. Với interface không phải functional interface, hãy tạo lớp adapter có `implements` và chuyển tiếp từng phương thức cần thiết.

Cách tiếp cận này cũng giải thích vì sao Java cho phép `Runnable task = () -> System.out.println("run");`: lambda được chuyển sang functional interface đích; Java không tự coi một lớp bất kỳ có `run()` là `Runnable`.

## 6. Khai báo `implements` chưa đủ nếu triển khai không đúng

Khi đã khai báo quan hệ kiểu, Java vẫn kiểm tra quy tắc triển khai phương thức. Các trường hợp dễ nhầm:

| Trường hợp | Có đáp ứng `void send(String)` của interface không? | Lý do |
| --- | --- | --- |
| `public void send(String text)` | Có | Chữ ký và mức truy cập phù hợp. |
| `void send(String text)` (package-private) | Không | Không thể giảm mức truy cập `public` của phương thức interface. |
| `private void send(String text)` | Không | Phương thức `private` không triển khai phương thức `public` của interface. |
| `public void send(Object text)` | Không | Khác kiểu tham số; đây là overload, không phải override. |
| `public static void send(String text)` | Không | Phương thức `static` không thực thi phương thức instance của interface. |

Với hợp đồng có kiểu trả về, Java còn kiểm tra kiểu trả về tương thích (có quy tắc *covariant return* cho kiểu tham chiếu), ngoại lệ checked được khai báo và các quy tắc ghi đè khác. Việc chỉ nhìn thấy hai tên phương thức giống nhau có thể gây hiểu lầm ngay cả sau khi thêm `implements`.

**Lưu ý:** Interface có thể chứa `default` method và thừa kế các phương thức từ interface cha; lớp triển khai không nhất thiết phải tự viết lại tất cả. Ngược lại, nếu interface có phương thức trừu tượng chưa được đáp ứng, lớp cụ thể không thể bỏ qua hợp đồng.

## 7. Quy tắc kiểm tra khi dùng interface trong API

Khi trình biên dịch báo không thể chuyển một kiểu cụ thể sang interface, hãy kiểm tra lần lượt:

1. Lớp có `implements InterfaceName` trực tiếp, hoặc kế thừa quan hệ ấy qua `extends`/interface cha không?
2. Bạn có đang truyền chính đối tượng của lớp chưa khai báo hợp đồng chỉ vì nó có các phương thức cùng tên không?
3. Nếu đã `implements`, chữ ký, mức truy cập, kiểu trả về, `static`/instance và ngoại lệ checked có phù hợp không? Dùng `@Override` để được kiểm tra sớm.
4. Nếu không thể sửa lớp gốc, bạn có thể khai báo adapter thực hiện interface và chuyển tiếp hành vi, hoặc dùng lambda/method reference khi interface là functional interface không?

Đừng dựa vào ép kiểu để “tạo” quan hệ. Một phép cast không bổ sung `implements` vào định nghĩa lớp; phép cast được chấp nhận về mặt cú pháp vẫn có thể gây `ClassCastException` khi đối tượng thực tế không thuộc kiểu đích.

**Ghi nhớ:** Trong Java, *có cùng bộ phương thức* là một nhận xét về hình dạng API; *là kiểu con và thực hiện hợp đồng* là quan hệ kiểu được khai báo và được trình biên dịch xác thực. Chính quan hệ ấy cho phép gán vào biến interface, truyền tham số interface và sử dụng đa hình an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-implementation-009">Giữ đúng ý nghĩa hợp đồng bên cạnh việc biên dịch thành công</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ đúng ý nghĩa hợp đồng bên cạnh việc biên dịch thành công

## Mục tiêu

Trong Java, một lớp có thể `implements` interface thành công, khai báo đủ phương thức, biên dịch không lỗi và vẫn **vi phạm điều mà người sử dụng interface có quyền kỳ vọng**. Bài này giúp bạn phân biệt hợp đồng về **hình dạng API** với hợp đồng về **hành vi**, diễn đạt quy tắc hành vi rõ ràng, thiết kế triển khai có thể thay thế cho nhau và kiểm chứng các quy tắc ấy bằng ví dụ Java 17+.

Đây là kỹ năng trọng tâm khi triển khai interface hoặc kế thừa lớp abstract: trình biên dịch bảo vệ được một phần cấu trúc chương trình; chất lượng triển khai còn phụ thuộc vào ngữ nghĩa mà API cam kết.

## 1. Một hợp đồng có nhiều lớp

Xét interface sau:

```java
interface Counter {
    void increase();
    int value();
}
```

Một lớp `Counter` triển khai `increase()` bằng cách đặt giá trị về `0` vẫn có thể biên dịch, vì Java kiểm tra phương thức có tồn tại và tương thích chữ ký, kiểu trả về, quyền truy cập, một số ràng buộc exception và quan hệ kiểu. Nhưng nếu ý nghĩa được công bố là “`increase()` tăng bộ đếm lên một đơn vị”, cách cài đặt đó sai hợp đồng.

Cần phân biệt:

| Lớp hợp đồng | Ví dụ | Trình biên dịch Java có tự bảo đảm? |
| --- | --- | --- |
| Hợp đồng kiểu/chữ ký | Có `increase()`, `value()` trả `int`, phương thức override hợp lệ | Có, trong phạm vi quy tắc ngôn ngữ |
| Tiền điều kiện (precondition) | Tham số đầu vào phải dương, chuỗi không được `null` | Không tự suy ra từ chữ ký thông thường |
| Hậu điều kiện (postcondition) | Sau `increase()`, giá trị mới bằng giá trị cũ cộng một | Không |
| Bất biến (invariant) | Bộ đếm luôn không âm | Không |
| Thứ tự/quan hệ giữa các lần gọi | Hàng đợi trả phần tử theo FIFO | Không |
| Tác dụng phụ và lỗi | Một lần gọi thất bại không làm đổi trạng thái; ném exception đã mô tả | Không đầy đủ |
| Đặc tính vận hành đã hứa | An toàn luồng, tính nguyên tử, ổn định thứ tự hoặc tính nhất quán | Không tự bảo đảm chỉ nhờ `implements` |

Trong một interface, tên phương thức, kiểu tham số và kiểu trả về là **bề mặt** của hợp đồng. Ý nghĩa của thao tác, giới hạn dữ liệu, tác động đến trạng thái, lỗi có thể xảy ra và những điều *không* được phép làm mới xác định liệu một triển khai có dùng đúng được hay không.

`@Override` giúp phát hiện sai lệch về phương thức override, nhưng nó không chứng minh phương thức đó thực hiện đúng chức năng.

## 2. Hợp đồng hành vi là điều người gọi có thể quan sát

Một hợp đồng hành vi tốt trả lời được các câu hỏi cụ thể:

- **Đầu vào hợp lệ:** Có chấp nhận `null`, chuỗi trống, số âm hoặc giá trị ngoài miền không?
- **Kết quả:** Giá trị trả về có ý nghĩa gì? Có được trả `null` hoặc giá trị đặc biệt không?
- **Trạng thái trước và sau:** Phương thức có thêm, bớt, thay thế hoặc giữ nguyên dữ liệu nào?
- **Thứ tự:** Với các thao tác liên tiếp, kết quả có phụ thuộc vào thứ tự gọi không?
- **Lỗi:** Điều gì xảy ra với đầu vào sai? Ném exception nào? Trạng thái có còn nguyên không?
- **Tính lặp:** Gọi lại có gây thêm tác dụng phụ không? Nếu idempotent thì được bảo đảm trong phạm vi nào?
- **Đồng thời:** Nếu nhiều luồng cùng gọi, API có hứa thread-safe/atomic không? Nếu không hứa, không được mặc định là có.

Ví dụ hợp đồng cho một hàng đợi công việc:

> `offer(task)` thêm tác vụ hợp lệ vào cuối hàng đợi. `poll()` lấy và xóa tác vụ có mặt lâu nhất chưa được lấy (FIFO). Khi rỗng, `poll()` trả `Optional.empty()`. `offer(null)` ném `NullPointerException` và không làm thay đổi nội dung. Hợp đồng này không cam kết thread-safe.

Những câu trên kiểm tra được bằng hành vi quan sát qua API, thay vì phụ thuộc vào chi tiết như dùng `ArrayDeque`, mảng, danh sách liên kết hay cơ sở dữ liệu.

**Độ rõ ràng của lời hứa rất quan trọng:** nếu chưa quy định FIFO, người viết một triển khai LIFO có thể hiểu khác mà vẫn cảm thấy đúng. Vì vậy cần ghi hợp đồng hành vi ngay tại interface hoặc tài liệu API liên quan.

## 3. Ví dụ chạy được: cùng interface, một triển khai sai ý nghĩa

Lưu toàn bộ mã sau trong một tệp `ContractDemo.java`.

```java
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;

public class ContractDemo {
    /** FIFO; null không hợp lệ; poll() rỗng trả Optional.empty(). */
    interface WorkQueue {
        void offer(String task);
        Optional<String> poll();
    }

    static final class FifoQueue implements WorkQueue {
        private final ArrayDeque<String> tasks = new ArrayDeque<>();

        @Override
        public void offer(String task) {
            tasks.addLast(Objects.requireNonNull(task, "task"));
        }

        @Override
        public Optional<String> poll() {
            return Optional.ofNullable(tasks.pollFirst());
        }
    }

    // Hợp lệ về kiểu nhưng sai yêu cầu FIFO.
    static final class LifoQueue implements WorkQueue {
        private final ArrayDeque<String> tasks = new ArrayDeque<>();

        @Override
        public void offer(String task) {
            tasks.addLast(Objects.requireNonNull(task, "task"));
        }

        @Override
        public Optional<String> poll() {
            return Optional.ofNullable(tasks.pollLast());
        }
    }

    static void check(String name, WorkQueue queue) {
        queue.offer("A");
        queue.offer("B");
        String first = queue.poll().orElseThrow();
        String second = queue.poll().orElseThrow();
        boolean fifo = first.equals("A") && second.equals("B");
        boolean empty = queue.poll().isEmpty();
        boolean nullRejected = false;
        try {
            queue.offer(null);
        } catch (NullPointerException expected) {
            nullRejected = true;
        }
        System.out.printf("%s: fifo=%s, empty=%s, nullRejected=%s%n",
                name, fifo, empty, nullRejected);
    }

    public static void main(String[] args) {
        check("FifoQueue", new FifoQueue());
        check("LifoQueue", new LifoQueue());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 --release 17 ContractDemo.java
java ContractDemo
```

Kết quả stdout đã được kiểm tra:

```text
FifoQueue: fifo=true, empty=true, nullRejected=true
LifoQueue: fifo=false, empty=true, nullRejected=true
```

### Đọc kết quả theo hợp đồng

1. **Cả hai lớp đều biên dịch.** Mỗi lớp đều cài đủ `offer` và `poll`, dùng kiểu trả về chính xác, quyền truy cập override hợp lệ.
2. **`FifoQueue` thực hiện FIFO.** Chèn cuối bằng `addLast()` và lấy đầu bằng `pollFirst()` nên hai lời gọi trả `"A"` rồi `"B"`.
3. **`LifoQueue` vi phạm FIFO.** `pollLast()` trả `"B"` trước `"A"`. Đây là lỗi **ngữ nghĩa**, không phải lỗi chữ ký. Vì vậy `fifo=false` dù chương trình chạy được.
4. **Cả hai trả empty khi đã lấy hết phần tử.** Hai phần tử đều được lấy ra trước lần `poll()` kiểm tra rỗng, nên `empty=true`.
5. **Cả hai từ chối `null`.** `Objects.requireNonNull` ném `NullPointerException` trước khi thêm, đúng hành vi đã ghi; `nullRejected=true`.

Ví dụ `check` là phép kiểm tra minh họa, **không phải bộ kiểm thử đầy đủ**. Nó chưa bao quát nhiều phần tử, nhiều chuỗi thao tác, việc khôi phục sau lỗi hay xử lý đồng thời. Mục tiêu là cho thấy sự khác biệt giữa “biên dịch thành công” và “đáp ứng hợp đồng”.

Có thể sửa `LifoQueue.poll()` thành `tasks.pollFirst()` để tuân thủ FIFO. Nhưng trong ứng dụng thật, hãy sửa tại nguyên nhân thiết kế và kiểm tra tất cả các đường đi qua API.

## 4. Kiểu tham chiếu chung không tự bảo đảm tính thay thế được

Mã gọi thường chỉ thấy interface:

```java
WorkQueue queue = obtainQueue();
queue.offer("A");
queue.offer("B");
String next = queue.poll().orElseThrow();
```

Đây là đoạn mã minh họa được đặt bên trong ngữ cảnh có `WorkQueue` và `obtainQueue()` phù hợp; bản thân nó không phải chương trình độc lập. Nếu mọi triển khai `WorkQueue` tôn trọng FIFO, `next` phải là `"A"`. Người gọi có thể thay `FifoQueue` bằng một triển khai khác mà không đổi giả định nghiệp vụ.

Nguyên tắc thay thế Liskov (Liskov Substitution Principle, LSP) diễn đạt mong muốn đó: nơi cần một đối tượng theo hợp đồng của kiểu cha/interface, đối tượng của kiểu con/triển khai phải sử dụng được **mà không làm mất những tính chất hợp đồng đã được cam kết**.

Với mỗi phương thức, cách diễn đạt thực tế là:

- **Không thắt chặt tiền điều kiện** so với hợp đồng đã công bố. Nếu `offer` cam kết chấp nhận mọi chuỗi không null, triển khai không được tự ý chỉ chấp nhận tối đa 10 ký tự mà không được hợp đồng cho phép.
- **Không làm yếu hậu điều kiện.** Nếu `poll` cam kết FIFO, triển khai không được đổi sang LIFO hoặc trả một phần tử tùy ý.
- **Duy trì bất biến trạng thái.** Nếu hợp đồng yêu cầu không mất phần tử chưa lấy, một lần lỗi không được âm thầm xóa hàng đợi.
- **Giữ các cam kết về exception và tác dụng phụ.** Nếu API hứa không thay đổi trạng thái khi thất bại, triển khai không được sửa dữ liệu rồi báo lỗi.

Các tiêu chí trên áp dụng cả cho `extends` trong lớp abstract và override trong class, không riêng interface. Chúng là tiêu chí về thiết kế và hành vi; trình biên dịch Java không có bộ suy luận tổng quát để chứng minh chúng.

## 5. Phân định rõ tiền điều kiện, hậu điều kiện, bất biến

### Tiền điều kiện: người gọi phải cung cấp gì?

Giả sử `reserve(String sku, int quantity)` chỉ chấp nhận `sku` không trống và `quantity > 0`. Tài liệu nên nêu rõ đầu vào sai sẽ bị từ chối như thế nào, chẳng hạn `IllegalArgumentException`, thay vì để từng lớp tự diễn giải.

Không nên đặt một điều kiện mới âm thầm trong một implementation, ví dụ giới hạn `quantity <= 5` trong khi interface công bố không có giới hạn đó. Người dùng thay implementation sẽ gặp lỗi dù đã tuân thủ tiền điều kiện của interface.

### Hậu điều kiện: sau khi gọi, điều gì phải đúng?

Nếu hợp đồng `reserve` nói “trả `true` khi giữ chỗ thành công và giảm tồn kho đúng `quantity`; trả `false` khi không đủ hàng và giữ nguyên tồn kho”, một triển khai luôn trả `true` nhưng không giảm tồn kho sẽ sai. Nó vẫn có thể đúng về kiểu trả về `boolean`.

### Bất biến: thuộc tính nào phải đúng mọi lúc?

Với dữ liệu tồn kho, `stock >= 0` có thể là bất biến. Khi xử lý trạng thái qua nhiều thao tác, bất biến phải tiếp tục được giữ sau mỗi thao tác công khai, kể cả nhánh ngoại lệ theo quy định.

Không biến mọi chi tiết cấu trúc nội bộ thành bất biến API. Ví dụ dùng `ArrayDeque` chỉ là cách cài đặt; thứ tự FIFO mới là đặc tính mà người gọi phải quan sát được.

## 6. Exception, `null` và thất bại cũng là hợp đồng

Hợp đồng nên quy định điều có thể xảy ra ở **đường lỗi**, không chỉ đường thành công.

Ví dụ với `WorkQueue`:

| Tình huống | Hành vi được yêu cầu |
| --- | --- |
| `offer("A")` trên queue còn dùng được | Lưu phần tử `"A"` ở cuối thứ tự FIFO |
| `poll()` khi queue có phần tử | Trả phần tử cũ nhất chưa lấy, đồng thời xóa nó |
| `poll()` khi queue rỗng | Trả `Optional.empty()`; không trả `null` |
| `offer(null)` | Ném `NullPointerException`, không thêm phần tử |
| Nhiều luồng truy cập | Chưa được bảo đảm; cần thỏa thuận thêm nếu ứng dụng có nhu cầu |

Điều này tránh các phiên bản triển khai trả `null` thay `Optional.empty()`, hoặc lặng lẽ bỏ qua `offer(null)`. Các lựa chọn kia có thể vẫn biểu diễn được trong Java, nhưng **khác quy tắc người dùng đang dựa vào**.

Nếu API công bố một exception cụ thể, nên kiểm tra cả loại exception lẫn sự thay đổi trạng thái. `throws` trong Java thể hiện một số ràng buộc checked exception; nó không tự mô tả đầy đủ unchecked exception, thông điệp lỗi hoặc tính nguyên tử của thao tác.

Khi trạng thái có thể thay đổi, cần nói rõ thất bại là “không thay đổi gì”, “thành công một phần” hay “có thể đã commit”. Đây là sự khác biệt lớn với thao tác mạng, ghi tệp và giao dịch cơ sở dữ liệu.

## 7. Ghi hợp đồng ngay ở nơi khai báo interface

Hãy viết tài liệu trước khi có nhiều implementation. Javadoc giúp đưa quy tắc đến người implement lẫn người gọi.

Ví dụ sau là một **khai báo minh họa độc lập**, không kết hợp cùng khai báo `WorkQueue` trong `ContractDemo.java`:

```java
import java.util.Optional;

/**
 * Hàng đợi tác vụ theo FIFO.
 *
 * <p>Không cam kết an toàn luồng. Mọi implementation phải giữ thứ tự
 * chèn của các phần tử chưa được lấy ra.</p>
 */
interface WorkQueue {
    /**
     * Thêm tác vụ vào cuối hàng đợi.
     *
     * @param task tác vụ không null
     * @throws NullPointerException nếu task là null;
     *                              trong trường hợp này queue không đổi
     */
    void offer(String task);

    /**
     * Lấy và xóa phần tử được thêm sớm nhất còn lại.
     *
     * @return phần tử vừa lấy, hoặc Optional.empty() nếu rỗng
     */
    Optional<String> poll();
}
```

Đối với một interface cần công khai cho package khác, sử dụng `public` ở top-level interface (và đặt trong tệp Java tương ứng). Mức truy cập của kiểu là vấn đề riêng với ý nghĩa hành vi.

Viết Javadoc càng cụ thể càng tốt: thứ tự, nullability, hậu điều kiện, biên lỗi, tính đồng thời và giá trị trả về. Tránh câu mơ hồ như “xử lý hợp lý”, “trả kết quả đúng” hoặc “thường trả phần tử tiếp theo”. Nếu behavior còn có điều kiện, hãy nêu điều kiện đó.

**Tài liệu là nguồn cam kết, nhưng chưa phải cơ chế kiểm chứng tự động.** Cần dùng các phép kiểm thử được rút trực tiếp từ chính quy tắc đã công bố.

## 8. Thiết kế kiểm thử hợp đồng dùng chung cho mọi implementation

Một cách hiệu quả là viết **contract tests**: cùng một bộ thử chạy cho mọi implementation của interface, kể cả các phiên bản mới thêm sau này.

Với `WorkQueue`, bộ kiểm thử cần quan sát:

1. Queue mới rỗng: `poll()` trả `Optional.empty()`.
2. Thêm một phần tử: `offer("A")` rồi `poll()` trả `"A"`, lần kế tiếp rỗng.
3. Thêm nhiều phần tử: lần lượt `A, B, C` rồi lấy ra đúng `A, B, C`.
4. Chèn thêm sau khi đã lấy: `offer(A), offer(B), poll(), offer(C)` phải tiếp tục trả `B, C`.
5. Thêm `null`: ném `NullPointerException`; những phần tử có sẵn vẫn còn với thứ tự không đổi.
6. Những kịch bản khác trong tài liệu: tính lặp, giới hạn dung lượng, lỗi tài nguyên hoặc đồng thời (nếu có cam kết).

Bạn có thể dùng JUnit 5 với các test được tham số hóa hoặc một hàm kiểm thử nhận `Supplier<WorkQueue>` để tạo queue sạch cho mỗi ca thử. `Supplier` ở đây cần tạo instance mới; tái sử dụng một queue đã bị test trước làm thay đổi trạng thái và khiến test khó tin cậy.

Pseudo-flow cho contract test:

```text
Danh sách các implementation được chấp nhận
    -> tạo một instance mới cho từng test
    -> thực hiện cùng chuỗi thao tác công khai
    -> so sánh đầu ra, trạng thái quan sát và exception
    -> báo lỗi khi bất kỳ implementation nào phá cam kết
```

Bộ `check` ở ví dụ chạy được sử dụng chính tư tưởng này ở phạm vi hẹp. Với hệ thống thật, test nên **thất bại rõ ràng** khi kỳ vọng không đúng (ví dụ assertion của JUnit), thay vì chỉ in `false`.

Kiểm thử hành vi cũng có thể sử dụng property-based testing: tạo nhiều chuỗi `offer/poll` hợp lệ, so sánh kết quả với mô hình tham chiếu FIFO. Dù vậy, không có bộ kiểm thử hữu hạn nào chứng minh tuyệt đối mọi triển khai luôn đúng với mọi dữ liệu và lịch chạy đồng thời.

## 9. Default method và abstract class không miễn trừ hợp đồng

### `default` trong interface

Một `default method` có thân giúp tái sử dụng logic, nhưng logic mặc định cũng phải đúng ý nghĩa được công bố. Implementation override `default` được phép đổi thuật toán, **không được tự ý đổi lời hứa API**.

Ví dụ nếu một interface công bố `isEmpty()` đúng khi không còn phần tử, việc một implementation override trả `true` vô điều kiện có thể biên dịch nhưng sai hợp đồng, trừ khi những ngữ cảnh đặc biệt được quy định rõ.

### Lớp abstract

Một abstract class có thể cung cấp trạng thái và logic chung. Việc kế thừa phương thức có sẵn giảm số lượng mã phải viết, nhưng không tự bảo đảm lớp con giữ tiền điều kiện, hậu điều kiện và bất biến khi override hoặc thêm đường đi thay đổi trạng thái.

Không đánh đồng “được thừa kế code”, “triển khai interface” và “đúng hành vi”. Một lớp có thể dùng lại toàn bộ code sai hoặc override một phần nhỏ làm sai quy tắc tổng thể.

### Composition

Nếu nhiều triển khai cần giữ các quy tắc chung, có thể gom thuật toán hoặc bước xác thực thành đối tượng cộng tác, hàm tiện ích nội bộ hoặc template method phù hợp. Việc tái sử dụng logic chung giúp giảm lệch hành vi, nhưng vẫn cần test ở biên interface.

## 10. Một số bẫy thiết kế thường gặp

**Bẫy 1 — Trùng chữ ký, khác ý nghĩa.** Định nghĩa `sort()` nhưng implementation chỉ đảo ngược danh sách; chương trình có thể chạy, nhưng người gọi tưởng kết quả đã sắp xếp tăng dần.

**Bẫy 2 — Thêm hạn chế không có trong contract.** Interface chấp nhận số lượng bất kỳ trong miền `int` hợp lệ, implementation âm thầm giới hạn tối đa 100. Đây là việc tăng tiền điều kiện khiến lời gọi hợp lệ theo interface thất bại.

**Bẫy 3 — Hậu điều kiện yếu đi.** Interface hứa `save` thành công nghĩa là dữ liệu đã được ghi bền vững, implementation chỉ đẩy vào bộ đệm dễ mất mà vẫn trả thành công. Nếu hệ thống chỉ hứa “đã xếp hàng ghi”, phải diễn đạt chính xác mức bảo đảm đó.

**Bẫy 4 — Hành vi sai trên trường hợp biên.** `poll()` có phần tử chạy đúng nhưng khi rỗng trả `null` thay `Optional.empty()`; ứng dụng lỗi ở nhánh ít xảy ra.

**Bẫy 5 — Exception làm hỏng trạng thái.** Xóa trước một phần dữ liệu rồi ném lỗi, trong khi hợp đồng đã cam kết thao tác thất bại phải giữ nguyên trạng thái.

**Bẫy 6 — Dùng `instanceof` để bù cho implementation lệch chuẩn.** Nếu client liên tục kiểm tra “nếu là lớp A thì FIFO, nếu là lớp B thì LIFO”, interface không còn tạo ra một hợp đồng có thể thay thế đáng tin. Chỉ nên phân nhánh theo khả năng được mô hình hóa rõ ràng, thay vì che giấu sai lệch.

**Bẫy 7 — Hứa thread-safe theo suy đoán.** Đúng chức năng khi chạy một luồng không có nghĩa đúng trong nhiều luồng. Nếu API phải thread-safe, cần xác định rõ điều kiện đồng thời, khả năng quan sát trạng thái và phép kiểm thử phù hợp.

**Bẫy 8 — Thay đổi tài liệu mà không đổi kiểm thử.** Khi công bố hợp đồng mới hoặc sửa hợp đồng, kiểm tra các implementation và bộ test liên quan. Không nên chỉnh Javadoc đơn thuần để hợp thức hóa một lỗi đã xuất hiện.

## 11. Quy trình thực hành khi triển khai một interface

1. **Đọc tên thao tác và Javadoc** để xác định những gì client có quyền dựa vào, đặc biệt các trạng thái biên.
2. **Viết trước danh sách tiền điều kiện, hậu điều kiện và bất biến.** Với thao tác có lỗi, xác định rõ trạng thái sau thất bại.
3. **Chọn thuật toán/cấu trúc dữ liệu** thỏa các điều kiện ấy. Ví dụ FIFO với `ArrayDeque` nên thêm cuối và lấy đầu.
4. **Dùng `@Override` và kiểm tra biên dịch** để bảo đảm phù hợp về cấu trúc; sau đó kiểm thử ngữ nghĩa riêng.
5. **Chạy contract tests trên tất cả implementation**, kể cả implementation giả cho test và các lớp tối ưu hiệu năng.
6. **Kiểm tra đường lỗi và tương tác giữa nhiều lần gọi**, không chỉ một “happy path”.
7. **Đánh giá thay thế implementation**: client có còn dùng đúng được khi không biết lớp thực thi cụ thể không?

Để luyện tập, hãy thử tạo thêm `PriorityQueueWork` triển khai `WorkQueue` và sắp tác vụ theo độ ưu tiên. Hỏi: nếu `WorkQueue` hiện cam kết FIFO, liệu đây có phải implementation hợp lệ? **Không**, nếu việc đổi thứ tự làm vi phạm FIFO. Muốn hỗ trợ hàng đợi ưu tiên, cần định nghĩa hợp đồng khác hoặc sửa thiết kế một cách có chủ đích và cập nhật toàn bộ client/test phụ thuộc.

## Ghi nhớ

`implements` và việc biên dịch thành công chứng minh lớp tương thích với **kiểu** của interface ở mức ngôn ngữ; chúng không chứng minh lớp tuân thủ **ý nghĩa nghiệp vụ**. Hợp đồng có giá trị khi người gọi có thể tin vào các cam kết quan sát được — đầu vào hợp lệ, kết quả, thứ tự, trạng thái, lỗi và những thuộc tính vận hành đã công bố — bất kể đối tượng đến từ implementation nào. Viết rõ các cam kết đó và kiểm tra chúng bằng cùng bộ contract tests là nền tảng để interface thật sự cho phép thay thế triển khai an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)
