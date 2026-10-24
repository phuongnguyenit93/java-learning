<a id="back-to-top"></a>

# Ghi đè phương thức

## Menu
- [Chữ ký phương thức và điều kiện ghi đè](#method-overriding-001)
- [Phân biệt override, overload và static method hiding](#method-overriding-002)
- [Không thu hẹp phạm vi truy cập khi ghi đè](#method-overriding-003)
- [Kiểu trả về đồng biến và giới hạn với kiểu nguyên thủy](#method-overriding-004)
- [Checked exception khi ghi đè phương thức](#method-overriding-005)
- [Unchecked exception và trách nhiệm của mã gọi](#method-overriding-006)
- [Phương thức final, private và static trong quan hệ kế thừa](#method-overriding-007)
- [Vai trò của @Override trong phát hiện sai chữ ký](#method-overriding-008)
- [Tham số varargs, mảng và các nhầm lẫn khi ghi đè](#method-overriding-009)
- [Gọi triển khai lớp cha bằng super](#method-overriding-010)

## <a id="method-overriding-001">Chữ ký phương thức và điều kiện ghi đè</a>

<details>
<summary>Xem chi tiết</summary>

# Chữ ký phương thức và điều kiện ghi đè trong Java

Ghi đè (*method overriding*) cho phép lớp con cung cấp cách thực hiện riêng cho một phương thức instance được kế thừa hoặc một hợp đồng từ interface. Khi lời gọi được thực hiện qua kiểu cha, Java có thể chọn phần thân của lớp con tại runtime. Để xác định một override hợp lệ, trước hết cần phân biệt **chữ ký phương thức** với toàn bộ khai báo phương thức.

## 1. Chữ ký phương thức gồm những gì?

Theo Java Language Specification, chữ ký chủ yếu được xác định bởi tên phương thức, các tham số kiểu của phương thức generic và kiểu các tham số hình thức theo thứ tự. Với generic, việc chuyên biệt hóa kiểu và quan hệ *subsignature* có thể ảnh hưởng đến việc hai khai báo có tương ứng hay không.

| Thành phần khai báo | Có phân biệt chữ ký? | Ghi chú |
| --- | --- | --- |
| Tên phương thức | Có | read và load khác nhau. |
| Số lượng, thứ tự và kiểu tham số | Có | find(String) khác find(Object). |
| Tên biến tham số | Không | send(String text) và send(String message) cùng chữ ký. |
| Kiểu trả về | Không | Chỉ đổi return type không tạo overload. |
| Modifier như public, final, static | Không | Có thể ảnh hưởng tính hợp lệ của override. |
| Mệnh đề throws | Không | Vẫn phải tuân thủ quy tắc ngoại lệ khi override. |
| String... so với String[] | Không | Varargs dùng kiểu mảng làm kiểu tham số. |

*Method descriptor* của JVM có chứa kiểu trả về, nhưng đó là khái niệm bytecode khác với chữ ký Java source dùng khi xét overload.

## 2. Điều kiện cơ bản của một override

Một khai báo ở lớp con cần thỏa các điều kiện chính sau:

1. Có quan hệ kế thừa hoặc thực hiện interface phù hợp; phương thức phải thực sự được phép kế thừa/ghi đè.
2. Tên và danh sách kiểu tham số phải tạo chữ ký tương ứng theo quy tắc Java.
3. Kiểu trả về phải tương thích. Primitive phải giữ nguyên kiểu; kiểu tham chiếu có thể dùng kiểu con trong trường hợp covariant return.
4. Quyền truy cập không được thu hẹp so với phương thức bị ghi đè.
5. Không được mở rộng checked exception theo cách hợp đồng cha không cho phép.
6. Không thể override phương thức final; static dùng cơ chế hiding, còn private không được kế thừa để override.
7. Lớp con concrete phải triển khai các phương thức abstract còn hiệu lực.

Các bài tiếp theo tập trung riêng vào từng nhóm quy tắc: phân biệt override/overload/hiding, visibility, return type, exception, modifier, annotation, varargs và lời gọi super.

## 3. Ví dụ chạy được: kiểu tham chiếu và đối tượng thực tế

~~~java
public class OverrideBasics {
    public static void main(String[] args) {
        Message parentView = new DetailedMessage();
        System.out.println(parentView.text());
        System.out.println(parentView.describe());
    }
}

class Message {
    public String text() {
        return "base";
    }

    public String describe() {
        return "Message: " + text();
    }
}

class DetailedMessage extends Message {
    @Override
    public String text() {
        return "child";
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac OverrideBasics.java
java OverrideBasics
~~~

Kết quả:

~~~text
child
Message: child
~~~

Biến parentView có kiểu khai báo Message, nên compiler kiểm tra lời gọi theo API nhìn thấy ở kiểu đó. Đối tượng thực là DetailedMessage, vì vậy lời gọi instance text() dispatch đến override. Ngay cả Message.describe() được kế thừa cũng gọi DetailedMessage.text() khi chạy trên đối tượng này.

## 4. Cách tránh một override vô tình thành phương thức mới

Nếu lớp cha có process(String) mà lớp con viết process(Object), đó thường là overload, không phải override. Chương trình vẫn có thể biên dịch nhưng lời gọi qua kiểu cha không chọn phương thức mới chỉ vì đối tượng thực là lớp con.

Hãy dùng @Override trên phương thức có ý định ghi đè. Nếu chữ ký không tương ứng, compiler sẽ báo lỗi thay vì để sai khác đó âm thầm tồn tại.

**Ghi nhớ:** chữ ký xác định phương thức nào đang được so sánh; các quy tắc về kế thừa, kiểu trả về, quyền truy cập, checked exception và modifier quyết định khai báo đó có phải override hợp lệ hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-overriding-002">Phân biệt override, overload và static method hiding</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt override, overload và static method hiding trong Java

Ba cơ chế có thể xuất hiện cùng lúc trong một hệ phân cấp lớp, nhưng khác nhau ở **cách chọn phương thức**:

- **Override:** lớp con cung cấp triển khai cho một instance method có thể ghi đè. Sau khi chữ ký được chọn lúc biên dịch, runtime dispatch chọn thân phương thức theo đối tượng thực tế.
- **Overload:** cùng tên nhưng danh sách kiểu tham số khác nhau. Compiler chọn chữ ký phù hợp từ kiểu tĩnh của đối số và các phương thức khả dụng.
- **Static method hiding:** lớp con khai báo static method tương ứng với static method được kế thừa. Phương thức được phân giải theo kiểu tại compile time, không theo đối tượng runtime.

## 1. Bảng phân biệt

| Tiêu chí | Override | Overload | Static hiding |
| --- | --- | --- | --- |
| Danh sách tham số | Chữ ký tương ứng | Khác kiểu/số lượng/thứ tự tham số | Chữ ký static tương ứng |
| Cần kế thừa | Có, hoặc thực hiện interface | Không bắt buộc | Có quan hệ class kế thừa phù hợp |
| Cách chọn | Thân instance theo đối tượng runtime | Chữ ký theo kiểu tĩnh lúc biên dịch | Static method theo kiểu lúc biên dịch |
| Đa hình runtime | Có | Bản thân overload không; chữ ký đã chọn vẫn có thể bị override | Không |
| @Override | Nên dùng | Không cho overload đơn thuần | Không áp dụng |

Return type, modifier và throws không tạo ra overload mới nếu danh sách tham số không đổi. Static/instance cùng chữ ký trong quan hệ kế thừa gây lỗi thay vì tạo cơ chế dispatch đặc biệt.

## 2. Ví dụ tổng hợp

~~~java
public class DispatchDemo {
    static class Base {
        String pick(Object value) { return "Base(Object)"; }
        String pick(String value) { return "Base(String)"; }
        static String kind() { return "Base"; }
    }

    static class Child extends Base {
        @Override
        String pick(Object value) { return "Child(Object)"; }

        String pick(Integer value) { return "Child(Integer)"; }
        static String kind() { return "Child"; }
    }

    public static void main(String[] args) {
        Base base = new Child();
        Child child = new Child();

        System.out.println(base.pick("hi"));
        System.out.println(base.pick(Integer.valueOf(3)));
        System.out.println(child.pick(Integer.valueOf(3)));
        System.out.println(base.kind());
        System.out.println(child.kind());
    }
}
~~~

~~~bash
javac DispatchDemo.java
java DispatchDemo
~~~

~~~text
Base(String)
Child(Object)
Child(Integer)
Base
Child
~~~

- base.pick("hi"): compiler chọn overload pick(String) nhìn thấy ở Base. Child không override chữ ký này, nên chạy Base.pick(String).
- base.pick(Integer.valueOf(3)): kiểu Base không có overload pick(Integer), nên chọn pick(Object); runtime dispatch tới Child.pick(Object).
- child.pick(Integer.valueOf(3)): kiểu tĩnh Child có thêm overload pick(Integer), nên compiler chọn chữ ký đó.
- base.kind() và child.kind() chọn static method theo kiểu tĩnh của biểu thức. Trong mã thật, nên gọi Base.kind() và Child.kind() cho rõ ràng.

## 3. Các bẫy thường gặp

**Đổi tham số thành overload:** nếu cha có convert(Object) và con chỉ khai báo convert(String), con chưa triển khai convert(Object); một lớp con concrete có thể vẫn không biên dịch nếu phương thức cha là abstract.

**Overload với null:** nếu có call(String) và call(Integer), lời gọi call(null) mơ hồ vì không kiểu nào cụ thể hơn kiểu còn lại. Với call(Object) và call(String), call(null) chọn String.

**Static không đa hình:** gọi static qua biến đối tượng vẫn dùng kiểu compile-time của biểu thức. Static method ở interface cũng không được class triển khai kế thừa như static method của superclass; hãy gọi qua tên interface.

**Kiểm tra thực hành:** trước khi kết luận một phương thức là override, xác định kiểu tĩnh của receiver, chữ ký overload được chọn, rồi mới xét phương thức instance nào được override ở runtime. Bài về @Override trình bày cách để compiler bắt lỗi sai chữ ký sớm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-overriding-003">Không thu hẹp phạm vi truy cập khi ghi đè</a>

<details>
<summary>Xem chi tiết</summary>

# Không thu hẹp phạm vi truy cập khi ghi đè phương thức trong Java

Một override phải giữ nguyên hoặc **mở rộng** quyền truy cập của phương thức bị ghi đè. Nếu lớp cha công khai một phương thức public, lớp con không thể làm phương thức đó thành protected hoặc package-private: mã gọi hợp lệ qua kiểu cha phải tiếp tục làm việc khi đối tượng thực là lớp con.

## 1. Quy tắc visibility

| Phương thức cha | Visibility hợp lệ ở override | Không hợp lệ |
| --- | --- | --- |
| public | public | protected, package-private, private |
| protected | protected, public | package-private, private |
| Package-private | Giữ nguyên hoặc mở rộng khi thực sự override được | private; hoặc trường hợp khác package không kế thừa phương thức đó |
| private | Không có override đối với phương thức private đó | Không áp dụng thang visibility |

Trong class, bỏ modifier nghĩa là package-private. Phương thức private của cha không được kế thừa để override; phương thức cùng tên trong con là khai báo riêng. Với package-private, cần xét package: subclass ở package khác không nhìn thấy phương thức đó để override. Nếu phương thức package-private là abstract, subclass concrete ở package khác không thể đáp ứng hợp đồng bằng cách chỉ khai báo một phương thức trùng tên.

## 2. Ví dụ: mở rộng quyền truy cập

~~~java
package academy.reports;

public abstract class Report {
    public abstract String title();
    protected abstract int pages();
    abstract String marker();
}
~~~

~~~java
package academy.reports;

public final class MonthlyReport extends Report {
    @Override
    public String title() {
        return "Monthly";
    }

    @Override
    public int pages() {          // protected -> public
        return 12;
    }

    @Override
    protected String marker() {   // package-private -> protected
        return "2026-10";
    }
}
~~~

Hai lớp ở cùng package nên MonthlyReport.marker() thực sự override được phương thức package-private của cha. pages() được mở thành public để caller ngoài package có thể gọi qua kiểu MonthlyReport.

Dù MonthlyReport.pages() là public, lời gọi report.pages() qua biến có kiểu tĩnh Report ở một package khác vẫn không hợp lệ, vì khai báo nhìn thấy qua Report là protected. Quyền truy cập được kiểm tra lúc biên dịch theo kiểu tĩnh và quy tắc protected; đối tượng runtime không tự nâng quyền cho lời gọi.

## 3. Interface luôn yêu cầu public cho instance method

~~~java
interface Sender {
    void send(); // public abstract ngầm định
}

final class EmailSender implements Sender {
    @Override
    public void send() {
        System.out.println("sent");
    }
}
~~~

Class triển khai không được bỏ public khỏi send(): trong class, method không modifier là package-private, yếu hơn hợp đồng public của interface. Quy tắc tương tự áp dụng khi override default method public.

## 4. Chẩn đoán lỗi

~~~java
class Document {
    public String name() { return "Document"; }
}

class PdfDocument extends Document {
    @Override
    protected String name() { return "PDF"; } // Không hợp lệ
}
~~~

Compiler thường báo lỗi về *weaker access privileges*. Bỏ @Override không sửa được lỗi; bản khai báo vẫn đang override với quyền truy cập yếu hơn.

**Checklist:** so sánh visibility ở phương thức cha/interface với phương thức con; kiểm tra package nếu cha dùng package-private; và không nhầm quyền truy cập với dispatch runtime. Bài này tập trung vào visibility; tính tương thích của return type và checked exceptions được giải thích ở các bài riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-overriding-004">Kiểu trả về đồng biến và giới hạn với kiểu nguyên thủy</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu trả về đồng biến và giới hạn với kiểu nguyên thủy khi ghi đè trong Java

**Covariant return** cho phép override trả về một kiểu tham chiếu cụ thể hơn kiểu được công bố ở cha. Ví dụ, nếu cha trả về Number, con có thể trả về Integer. Điều này không cho phép đổi một primitive sang primitive khác hoặc sang wrapper.

## 1. Quy tắc chính

| Return type ở cha | Return type ở con | Kết quả |
| --- | --- | --- |
| Object | String | Hợp lệ: String là kiểu con của Object |
| Number | Integer | Hợp lệ |
| CharSequence | String | Hợp lệ |
| Object | int[] | Hợp lệ: mảng là kiểu tham chiếu |
| String | Object | Không hợp lệ: kiểu con trả về rộng hơn |
| int | int | Hợp lệ |
| int | long hoặc Integer | Không hợp lệ |
| void | void | Hợp lệ |
| void | String | Không hợp lệ |

Kiểu primitive phải giữ nguyên chính xác. Boxing, unboxing hoặc primitive widening trong biểu thức không biến hai kiểu trả về thành kiểu đồng biến.

## 2. Ví dụ chạy được

~~~java
public class CovariantDemo {
    public static void main(String[] args) {
        Item general = new Note("Java");
        Note specific = new Note("Java");

        Number size = general.size();
        Integer exactSize = specific.size();

        System.out.println(general.label() + ":" + size);
        System.out.println(specific.label() + ":" + exactSize);
        System.out.println(general.copy().getClass().getSimpleName());
    }
}

abstract class Item {
    public abstract CharSequence label();
    public abstract Number size();
    public abstract Item copy();
}

final class Note extends Item {
    private final String text;

    Note(String text) { this.text = text; }

    @Override public String label() { return text; }
    @Override public Integer size() { return text.length(); }
    @Override public Note copy() { return new Note(text); }
}
~~~

~~~bash
javac CovariantDemo.java
java CovariantDemo
~~~

~~~text
Java:4
Java:4
Note
~~~

Integer size() hợp lệ vì Integer là subtype của Number; text.length() được boxing thành Integer trong biểu thức return. Điều này khác với việc đổi chữ ký cha từ int sang Integer. Qua biến Item, compiler vẫn xem kết quả của size() là Number, dù runtime đang chạy Note.size().

## 3. Generic và mảng cần xét kiểu đầy đủ

Generic thường bất biến: List&lt;Integer&gt; không phải subtype của List&lt;Number&gt;. Vì vậy, nếu cha trả về List&lt;Number&gt;, con không thể override bằng List&lt;Integer&gt;. Hợp đồng List<? extends Number> linh hoạt hơn và có thể được triển khai bằng List&lt;Integer&gt;.

Mảng là kiểu tham chiếu: Object data() có thể được override bằng int[] data(), vì int[] là subtype của Object. Nhưng int data() không thể đổi thành long data().

~~~java
import java.util.List;

abstract class NumberReader {
    abstract List<? extends Number> read();
}

final class IntegerReader extends NumberReader {
    @Override
    List<Integer> read() {
        return List.of(2, 5, 7);
    }
}
~~~

Đây là ví dụ về quan hệ kiểu trả về; nó không phải chương trình độc lập có main.

## 4. Bridge method là chi tiết biên dịch

Java dùng type erasure cho generic. Compiler có thể sinh *bridge method* để lời gọi qua kiểu cha vẫn dispatch tới triển khai cụ thể sau erasure hoặc khi return type covariant. Ví dụ Box&lt;T&gt;.get() có thể được hiện thực bởi TextBox extends Box&lt;String&gt; với String get(). Không cần tự khai báo bridge method; hãy viết override hợp lệ và dùng @Override.

## 5. Phân biệt return type với tham số

Covariant return chỉ nói về **kiểu trả về**. Nếu cha có Number convert(Number value), con khai báo Integer convert(Integer value) thì tham số đã đổi; đó là overload, không phải override của convert(Number). Muốn override, giữ tham số tương ứng và có thể thu hẹp kiểu trả về.

**Ghi nhớ:** kiểm tra quan hệ subtype của toàn bộ kiểu tham chiếu trả về; với primitive và void, giữ nguyên kiểu. Return type không được dùng để tạo overload và không quyết định overload nào được chọn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-overriding-005">Checked exception khi ghi đè phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Checked exception khi ghi đè phương thức trong Java

Checked exception là một phần của hợp đồng được kiểm tra lúc biên dịch. Khi override hoặc implement phương thức, lớp con không được buộc caller xử lý một checked exception mới hoặc rộng hơn điều mà hợp đồng cha cho phép.

## 1. Quy tắc

Nếu phương thức cha khai báo checked exceptions, phương thức con có thể giữ các loại đó, chọn subtype phù hợp, bỏ bớt loại hoặc không khai báo checked exception. Nếu cha không khai báo checked exception, con không thể thêm một checked exception mới. Unchecked exceptions không chịu giới hạn này; bài kế tiếp nói riêng về hành vi và trách nhiệm của caller.

~~~java
import java.io.FileNotFoundException;
import java.io.IOException;

abstract class DataSource {
    abstract String load(String key) throws IOException;
}

final class CacheSource extends DataSource {
    @Override
    String load(String key) throws FileNotFoundException {
        return "cached:" + key;
    }
}
~~~

FileNotFoundException là subtype của IOException, nên đây là override hợp lệ.

| throws ở cha | throws ở con | Hợp lệ? |
| --- | --- | --- |
| IOException | IOException | Có |
| IOException | FileNotFoundException | Có |
| IOException | Không có throws | Có |
| IOException | Exception | Không: rộng hơn |
| IOException | SQLException | Không: checked type không tương thích |
| Không có checked exception | IOException | Không |
| Không có checked exception | IllegalStateException | Có: unchecked |

Quy tắc xét khai báo throws, không dựa vào việc thân phương thức hiện tại có thực sự ném exception hay không.

## 2. Vì sao không được mở rộng checked exception?

~~~java
import java.io.IOException;

class Parent {
    void read() throws IOException { }
}

class Child extends Parent {
    @Override
    void read() throws Exception { } // Không hợp lệ
}
~~~

Caller dùng biến kiểu Parent có thể đã bắt hoặc khai báo IOException. Nếu Child.read() được phép công bố Exception, lời gọi đa hình có thể phát sinh nhiều loại checked exception mà caller chưa được yêu cầu xử lý. Compiler chặn trường hợp đó.

Tương tự, nếu interface khai báo void run() không có checked exception, implementation không được thêm throws IOException. Nó phải xử lý exception trong thân, chuyển đổi sang một lỗi phù hợp hoặc thay đổi hợp đồng ở nơi có quyền thiết kế API.

## 3. Nghĩa vụ của caller vẫn theo kiểu tĩnh

~~~java
import java.io.IOException;

interface Worker {
    void work() throws IOException;
}

final class LocalWorker implements Worker {
    @Override
    public void work() {
        System.out.println("done");
    }
}

class UseWorker {
    void run() throws IOException {
        Worker polymorphic = new LocalWorker();
        polymorphic.work(); // Xử lý/khai báo IOException theo Worker

        LocalWorker concrete = new LocalWorker();
        concrete.work();    // Không cần IOException theo LocalWorker
    }
}
~~~

Việc LocalWorker.work() không khai báo checked exception không xóa nghĩa vụ tại lời gọi qua biến Worker, vì compiler kiểm tra theo khai báo được nhìn thấy qua kiểu tĩnh.

## 4. Nhiều hợp đồng cùng lúc

Nếu một lớp implement hai interface có cùng phương thức nhưng lần lượt khai báo throws IOException và throws SQLException, một implementation chung phải thỏa **cả hai**. Không thể chỉ chọn hợp đồng thuận tiện hơn; giải pháp thường là xử lý checked exceptions bên trong và không khai báo chúng ở implementation.

Trong chuỗi kế thừa, mỗi override phải tương thích với hợp đồng gần nhất mà nó ghi đè. Nếu GrandParent cho phép Exception, Parent thu hẹp còn IOException, thì Child chỉ có thể thu hẹp tiếp theo quy tắc của Parent; không thể dùng SQLException chỉ vì nó cũng là con của Exception.

## 5. throws không phân biệt overload

Không thể khai báo hai phương thức cùng tên và cùng tham số chỉ khác throws. Danh sách throws không phải thành phần phân biệt chữ ký overload, nhưng được compiler kiểm tra khi một phương thức thực sự override. Constructor cũng không override constructor cha; tuy nhiên, constructor con vẫn phải xử lý checked exception phát sinh từ lời gọi super(...) theo quy tắc thông thường.

Khi gặp lỗi compiler về exception, hãy xác định đúng phương thức bị override, phân loại exception, đối chiếu quan hệ subtype với từng hợp đồng cha/interface và kiểm tra kiểu tĩnh tại vị trí gọi. Đừng xóa @Override để né lỗi: annotation không gây ra giới hạn throws, nó chỉ giúp compiler xác nhận quan hệ override.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-overriding-006">Unchecked exception và trách nhiệm của mã gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Unchecked exception và trách nhiệm của mã gọi khi ghi đè phương thức trong Java

Unchecked exception gồm RuntimeException và các lớp con của nó, cùng Error và các lớp con của nó. Compiler không buộc caller phải catch hoặc khai báo chúng. Một override có thể ném unchecked exception mới dù phương thức cha không có mệnh đề throws; điều đó vẫn có thể làm thay đổi hành vi mà người dùng API quan sát được.

## 1. Không khai báo throws không có nghĩa là không thể thất bại

~~~java
interface TextProcessor {
    String process(String input);
}

final class UpperProcessor implements TextProcessor {
    @Override
    public String process(String input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (input.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        return input.strip().toUpperCase(java.util.Locale.ROOT);
    }
}
~~~

IllegalArgumentException là unchecked, nên chữ ký không cần ghi throws. Tuy vậy, lời gọi với null hoặc chuỗi trắng vẫn có thể ném exception tại runtime. throws X là khai báo; throw new X(...) là câu lệnh thực sự ném đối tượng.

## 2. Ví dụ caller xử lý lỗi đầu vào

~~~java
public class UncheckedDemo {
    public static void main(String[] args) {
        TextProcessor processor = new UpperProcessor();
        show(processor, "  Java  ");
        show(processor, "   ");
        show(processor, null);
    }

    private static void show(TextProcessor processor, String input) {
        try {
            System.out.println("Result: " + processor.process(input));
        } catch (IllegalArgumentException ex) {
            System.out.println("Rejected: " + ex.getMessage());
        }
    }
}
~~~

Khi ghép với interface và implementation ở mục 1, chương trình in:

~~~text
Result: JAVA
Rejected: input must not be blank
Rejected: input must not be null
~~~

Caller bắt đúng lỗi đầu vào tại nơi có thể trình bày thông báo và tiếp tục xử lý. Nếu không có catch, unchecked exception có thể lan truyền lên stack và kết thúc luồng khi không có handler phù hợp.

## 3. Compiler không kiểm tra toàn bộ hợp đồng hành vi

Java cho phép override thêm unchecked exception, nhưng khả năng thay thế kiểu vẫn quan trọng. Nếu lớp cha cam kết thao tác thành công với mọi đầu vào hợp lệ, lớp con tự ý ném IllegalStateException với các đầu vào đó có thể phá hợp đồng dù vẫn biên dịch.

Bên viết implementation nên:

- Nêu rõ tiền điều kiện như non-null, giá trị dương hoặc trạng thái cần thiết.
- Dùng loại exception có ý nghĩa: IllegalArgumentException cho đối số sai, IllegalStateException cho trạng thái không phù hợp, hoặc exception tùy chỉnh khi thực sự cần.
- Tránh thay đổi trạng thái một phần trước khi báo lỗi nếu có thể; Java không tự động rollback mọi thay đổi khi có RuntimeException.
- Giữ nguyên cause khi chuyển exception để không mất thông tin chẩn đoán.

## 4. Trách nhiệm của caller

Caller không cần bọc mọi lời gọi trong catch RuntimeException. Thay vào đó:

- Xác thực dữ liệu do người dùng cung cấp khi có thể đưa ra thông báo tốt hơn.
- Bắt loại exception dự kiến tại ranh giới có cách xử lý cụ thể.
- Để lỗi bất ngờ lan truyền tới cơ chế xử lý phù hợp thay vì âm thầm nuốt lỗi.
- Dọn dẹp tài nguyên bằng try-with-resources hoặc finally khi cần; không giả định exception tự đóng mọi tài nguyên hay rollback transaction.

Không nên bắt Throwable hoặc Error như lỗi đầu vào thông thường. Việc một exception đã được bắt cũng không bảo đảm thao tác chưa tạo tác dụng phụ.

## 5. Phân biệt với checked exception

Quy tắc khai báo checked exception khi override được trình bày ở bài trước: lớp con không được mở rộng checked exceptions mà hợp đồng cha không cho phép. Với unchecked exceptions, compiler không áp dụng giới hạn throws tương tự, nên JavaDoc và hợp đồng hành vi càng quan trọng.

**Ghi nhớ:** compiler xác nhận luật kiểu và checked exceptions; nó không chứng minh mọi implementation tuân thủ ý nghĩa nghiệp vụ của kiểu cha. Người viết API phải mô tả lỗi có thể dự đoán, còn caller cần xử lý các tình huống thất bại ở đúng ranh giới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-overriding-007">Phương thức final, private và static trong quan hệ kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức final, private và static trong quan hệ kế thừa Java

Ba modifier này tạo ra ba quan hệ khác nhau với lớp con. **Final** ngăn override, **private** không được kế thừa để override, còn **static** dùng method hiding thay vì dispatch đa hình theo đối tượng.

## 1. So sánh nhanh

| Loại phương thức cha | Lớp con kế thừa? | Khai báo cùng chữ ký ở lớp con |
| --- | --- | --- |
| Instance method thường | Có, nếu được truy cập | Override nếu thỏa các quy tắc |
| final instance method | Có | Lỗi nếu cố override |
| private instance method | Không | Phương thức độc lập, không override |
| Static method có thể kế thừa | Có | Static method tương ứng sẽ hide |
| Static final method | Có thể gọi theo quyền truy cập | Không thể hide bằng khai báo tương ứng |

## 2. Ví dụ: final, private, static và override

~~~java
public class MethodKindsDemo {
    static class Base {
        public final String category() { return "base"; }
        private String secret() { return "base-secret"; }
        public String reveal() { return secret(); }
        public static String kind() { return "Base"; }
        public String describe() { return "Base.describe"; }
    }

    static class Child extends Base {
        public String category(int level) { return "level-" + level; }
        public String secret() { return "child-secret"; }
        public static String kind() { return "Child"; }

        @Override
        public String describe() { return "Child.describe"; }
    }

    public static void main(String[] args) {
        Child child = new Child();
        Base ref = child;
        System.out.println(ref.category());
        System.out.println(child.category(2));
        System.out.println(ref.reveal());
        System.out.println(child.secret());
        System.out.println(ref.kind());
        System.out.println(ref.describe());
    }
}
~~~

~~~text
base
level-2
base-secret
child-secret
Base
Child.describe
~~~

category() là final và được kế thừa; category(int) là overload hợp lệ. Base.secret() là private, nên Child.secret() là method riêng và không thay đổi lời gọi bên trong Base.reveal(). kind() là static, nên lời gọi qua biến ref được phân giải theo kiểu Base. Chỉ describe() là instance override và dispatch tới Child.

## 3. Các quy tắc dễ quên

- final trên method không cấm gọi hay kế thừa method đó; nó cấm override. final trên class thì cấm subclass.
- Không thể khai báo một method vừa abstract vừa final: một bên yêu cầu override để cung cấp thân, bên kia cấm override.
- private helper của superclass vẫn có thể được gọi từ method khác được định nghĩa trong superclass khi object thực là subclass. Điều không xảy ra là dispatch sang method cùng tên do subclass tự khai báo.
- Static method của interface không được class triển khai kế thừa. Gọi nó bằng tên interface, ví dụ Tools.version(). Private interface methods là helper nội bộ cho các method có thân trong interface.
- Nếu superclass và subclass khai báo static/instance cùng chữ ký tương ứng, compiler báo lỗi; đổi modifier không biến static thành override.

## 4. Dùng final để kiểm soát thuật toán chung

Một mẫu thiết kế phổ biến là *Template Method*: superclass giữ trình tự bằng method final và giao một bước cụ thể cho subclass.

~~~java
abstract class Job {
    public final void run() {
        prepare();
        execute();
        finish();
    }

    private void prepare() { System.out.println("prepare"); }
    protected abstract void execute();
    private void finish() { System.out.println("finish"); }
}

final class ImportJob extends Job {
    @Override
    protected void execute() {
        System.out.println("import");
    }
}
~~~

run() cố định thứ tự; execute() là điểm mở rộng; các helper private không phải API dành cho subclass. Cách này hữu ích khi bắt buộc quy trình chung phải chạy đầy đủ mà không phụ thuộc vào việc subclass có nhớ gọi super hay không.

**Ghi nhớ:** cùng tên không đồng nghĩa override. Hãy xác định modifier, khả năng kế thừa và cơ chế dispatch trước khi kết luận quan hệ giữa hai khai báo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-overriding-008">Vai trò của @Override trong phát hiện sai chữ ký</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của @Override trong phát hiện sai chữ ký phương thức

@Override thể hiện ý định rằng một phương thức đang override hoặc implement một phương thức hợp lệ từ kiểu cha. Compiler kiểm tra lời khẳng định này. Annotation không làm thay đổi dispatch runtime; giá trị của nó là bắt lỗi khi khai báo không đúng như lập trình viên dự định.

## 1. Lỗi điển hình: vô tình tạo overload

~~~java
class Parent {
    public void send(String text) {
        System.out.println("Parent: " + text);
    }
}

class Child extends Parent {
    public void send(Object text) {
        System.out.println("Child: " + text);
    }
}
~~~

send(Object) không override send(String) vì kiểu tham số khác nhau. Với Parent ref = new Child(); ref.send("hello"); kết quả vẫn là Parent: hello.

Nếu viết:

~~~java
class Child extends Parent {
    @Override
    public void send(Object text) {
        System.out.println("Child: " + text);
    }
}
~~~

compiler báo rằng phương thức không override/implement phương thức từ siêu kiểu. Khi ý định là override send(String), hãy sửa tham số cho khớp và giữ @Override.

## 2. Chữ ký và những thay đổi dễ nhầm

| Thay đổi ở lớp con | Có tạo override? |
| --- | --- |
| Đổi tên phương thức | Không |
| Đổi số lượng hoặc kiểu tham số | Không, thường tạo overload |
| Chỉ đổi tên biến tham số | Có thể; tên biến không thuộc chữ ký |
| Chỉ đổi return type | Không tạo overload; return type phải tương thích khi override |
| Chỉ đổi throws | Không tạo overload; checked exceptions vẫn bị kiểm tra |
| Khai báo cùng chữ ký với static hoặc private method của cha | Không phải override |
| Implement method của interface | Có thể dùng @Override |

Với generic, chữ ký cần được xét theo chuyên biệt hóa kiểu và quy tắc subsignature, không chỉ so hai dòng khai báo nguyên văn. Base&lt;T&gt;.accept(T) có thể được override bởi accept(String) khi subclass kế thừa Base&lt;String&gt;.

## 3. Ví dụ hợp lệ với class và interface

~~~java
interface Printer {
    void print(String message);
}

class ConsolePrinter implements Printer {
    @Override
    public void print(String message) {
        System.out.println("In: " + message);
    }
}

class Animal {
    public Number amount() { return 1; }
    public void greet(String value) { }
}

class Dog extends Animal {
    @Override
    public Integer amount() { return 7; } // Covariant return
    public void greet(Object value) { }   // Overload mới, không dùng @Override
}
~~~

@Override dùng được cả khi triển khai interface lẫn khi override method từ class, kể cả method public của Object như toString(), equals(Object) và hashCode().

## 4. Điều annotation không làm

- Không cho phép override method final, private hoặc static.
- Không bỏ qua quy tắc visibility, return type hay checked exceptions.
- Không chứng minh nội dung phương thức đúng nghiệp vụ hoặc test đã đầy đủ.
- Không điều khiển runtime dispatch.

@Override có retention ở mức SOURCE, phục vụ kiểm tra biên dịch. Khi compiler báo “does not override or implement”, hãy kiểm tra tên, danh sách kiểu tham số, quan hệ extends/implements và modifier của method cha. Đừng xóa annotation để làm lỗi biến mất; trước hết xác minh rằng bạn thực sự muốn override chứ không muốn tạo overload mới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-overriding-009">Tham số varargs, mảng và các nhầm lẫn khi ghi đè</a>

<details>
<summary>Xem chi tiết</summary>

# Tham số varargs, mảng và các nhầm lẫn khi ghi đè phương thức trong Java

Trong khai báo phương thức, **T...** là cú pháp thuận tiện cho tham số mảng **T[]**. Vì vậy, thay varargs bằng mảng hoặc ngược lại không tự tạo chữ ký mới: hai khai báo có thể override lẫn nhau nếu các điều kiện khác đều hợp lệ.

## 1. Cùng kiểu tham số, khác cú pháp gọi

~~~java
class ParentVarargs {
    void print(String... words) {
        System.out.println(java.util.Arrays.toString(words));
    }
}

class ChildArray extends ParentVarargs {
    @Override
    void print(String[] words) {
        System.out.println("child: " + java.util.Arrays.toString(words));
    }
}
~~~

Với ParentVarargs parent = new ChildArray(), lời gọi parent.print("A", "B") hợp lệ vì khai báo được nhìn thấy lúc biên dịch là varargs. Compiler đóng hai chuỗi thành String[], rồi runtime dispatch tới ChildArray.print(String[]).

Ngược lại, với ChildArray child = new ChildArray(), lời gọi child.print("A", "B") không hợp lệ: khai báo được nhìn thấy qua kiểu ChildArray dùng String[], nên phải gọi child.print(new String[]{"A", "B"}).

Chiều ngược lại cũng đúng: lớp cha khai báo print(String[]), lớp con override bằng print(String...) vẫn có cùng kiểu tham số; tuy nhiên, gọi qua biến kiểu cha vẫn cần truyền mảng nếu khai báo ở cha không phải varargs.

## 2. Chương trình minh họa

~~~java
public class VarargsOverrideDemo {
    public static void main(String[] args) {
        ParentVarargs ref = new ChildArray();
        ref.print("Java", "17");
        ref.print(new String[]{"A", "B"});

        ChildArray direct = new ChildArray();
        direct.print(new String[]{"C"});
        ref.print((String) null);
        direct.print((String[]) null);
    }
}
~~~

Ghép với hai lớp ở mục 1, kết quả là:

~~~text
child: [Java, 17]
child: [A, B]
child: [C]
child: [null]
child: null
~~~

(String) null là một đối số null được đóng gói thành mảng một phần tử. (String[]) null truyền chính tham chiếu mảng null. Không nên dựa vào print(null) để biểu đạt ý định vì compiler có thể hiểu là mảng null và đưa ra cảnh báo; cast rõ kiểu sẽ dễ đọc hơn.

## 3. Ràng buộc chữ ký

- Varargs phải là tham số cuối cùng và mỗi phương thức chỉ có tối đa một tham số varargs.
- int... tương đương int[]; String[]... tương đương String[][].
- Không thể khai báo hai overload cùng lớp chỉ khác f(String...) và f(String[]); đó là hai chữ ký trùng nhau.
- Object[] và String[] là các kiểu tham số khác nhau, dù mảng String[] có thể gán cho Object[].
- Autoboxing ở lời gọi không biến int[] thành Integer[] và không tạo quan hệ override giữa hai chữ ký khác kiểu.

## 4. Reflection và generic varargs

Method.isVarArgs() cho biết **phương thức được khai báo** với cú pháp varargs hay không. Compiler có thể cho Parent.print(String...) và Child.print(String[]) override nhau dù isVarArgs() trả về khác nhau. Khi dùng reflection, tra cứu tham số bằng String[].class.

Varargs generic như List&lt;String&gt;... có thể gây cảnh báo do kiểu mảng không reifiable và nguy cơ heap pollution. @SafeVarargs chỉ được dùng ở các vị trí được Java cho phép; nó không phải cách bỏ qua cảnh báo tùy tiện.

**Ghi nhớ:** compile time quyết định cú pháp varargs có hợp lệ tại call site không; runtime dispatch mới chọn thân override. Khi debug, hãy kiểm tra cả kiểu tĩnh của receiver lẫn dạng khai báo varargs/mảng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-overriding-010">Gọi triển khai lớp cha bằng super</a>

<details>
<summary>Xem chi tiết</summary>

# Gọi triển khai lớp cha bằng super khi ghi đè phương thức trong Java

Trong override, lời gọi instance bình thường như this.run() có thể quay lại chính override. **super.run()** yêu cầu Java gọi implementation được tra cứu qua superclass, nhờ đó subclass có thể tái sử dụng logic nền tảng rồi mở rộng nó. super không tạo một đối tượng cha riêng: lời gọi vẫn thực hiện trên cùng đối tượng subclass.

## 1. Ví dụ: superclass gọi một hook có thể override

~~~java
public class SuperDispatchDemo {
    static class Parent {
        void run() {
            System.out.println("Parent.run");
            hook();
        }

        void hook() {
            System.out.println("Parent.hook");
        }
    }

    static class Child extends Parent {
        @Override
        void run() {
            System.out.println("Child.before");
            super.run();
            System.out.println("Child.after");
        }

        @Override
        void hook() {
            System.out.println("Child.hook");
        }
    }

    public static void main(String[] args) {
        new Child().run();
    }
}
~~~

~~~text
Child.before
Parent.run
Child.hook
Child.after
~~~

super.run() gọi phần thân Parent.run(). Nhưng lời gọi hook() bên trong thân đó vẫn là instance call bình thường trên cùng đối tượng Child, nên nó dispatch tới Child.hook(). Vì vậy, super chỉ ràng buộc lời gọi hiện tại; nó không tắt dynamic dispatch của mọi lời gọi phát sinh bên trong superclass.

## 2. Khi nào dùng super?

Dùng super.method(...) khi hợp đồng yêu cầu giữ lại hành vi cha, chẳng hạn thêm logging trước/sau, mở rộng kết quả hoặc thực hiện cleanup. Không phải override nào cũng cần gọi super: nếu lớp con thay thế thuật toán hoàn toàn, gọi superclass có thể tạo tác dụng phụ không mong muốn.

Một số điểm cần nhớ:

- Nếu superclass trực tiếp không override method, super.method() có thể gọi implementation được kế thừa từ tổ tiên.
- Không có cú pháp super.super.method() để bỏ qua tùy ý lớp trung gian.
- Không thể gọi thân abstract method qua super, vì method đó không có implementation.
- super không vượt luật truy cập và không dùng để gọi private helper của cha.
- super(...) gọi constructor superclass; super.method(...) gọi một phương thức. Đây là hai cú pháp khác nhau.

## 3. Interface default method

Khi muốn tái sử dụng một default method từ superinterface, cú pháp là **InterfaceName.super.method()** trong ngữ cảnh được Java cho phép.

~~~java
interface Greeter {
    default String greet(String name) {
        return "Hello, " + name;
    }
}

final class FriendlyGreeter implements Greeter {
    @Override
    public String greet(String name) {
        return Greeter.super.greet(name) + "!";
    }
}
~~~

Greeter.super.greet(name) gọi thân default method của interface rồi thêm dấu chấm than. Khi class nhận hai default method xung đột từ các interface không liên quan và không có implementation concrete phù hợp từ superclass, class phải tự override để giải quyết xung đột. Nếu một interface kế thừa interface kia và override default method, phiên bản ở interface con có thể được chọn mà không cần override trong class. Trong override, `InterfaceName.super.method()` chỉ hợp lệ với superinterface thỏa các quy tắc Java; không thể tùy ý chọn mọi tổ tiên. Phương thức concrete được kế thừa từ superclass được ưu tiên hơn default method của interface.

## 4. Ngoại lệ và tác dụng phụ

Lời gọi super.method() chịu cùng quy tắc xử lý return value và exception như lời gọi method bình thường. Nếu superclass ném checked exception, override cần catch hoặc khai báo phù hợp; nếu gọi superclass tạo thay đổi trạng thái, ghi log hay I/O, subclass phải cân nhắc thứ tự và tránh thực hiện lặp.

Khi superclass bắt buộc một quy trình chung phải chạy đúng trình tự, có thể dùng *Template Method*: một method final điều phối quy trình và gọi các hook được subclass override. Cách này giảm phụ thuộc vào việc từng subclass có nhớ gọi super hay không.

**Ghi nhớ:** super chọn implementation superclass cho lời gọi hiện tại; những hook instance mà implementation đó tiếp tục gọi vẫn có thể dispatch tới subclass. Hãy lần theo toàn bộ đường gọi, exception và tác dụng phụ khi debug một override.

</details>

- [Quay lại đầu trang](#back-to-top)
