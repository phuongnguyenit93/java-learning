<a id="back-to-top"></a>

# Interface và Object

## Menu
- [Quan hệ giữa interface và các phương thức public của Object](#interface-and-object-001)
- [Khai báo equals, hashCode và toString trong hợp đồng](#interface-and-object-002)
- [Vì sao default method không được ghi đè phương thức public của Object](#interface-and-object-003)
- [Triển khai equals và hashCode nhất quán giữa các kiểu con](#interface-and-object-004)
- [clone có phạm vi protected và yêu cầu public của interface](#interface-and-object-005)
- [Giới hạn của marker interface Cloneable](#interface-and-object-006)

## <a id="interface-and-object-001">Quan hệ giữa interface và các phương thức public của Object</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa interface và các phương thức public của Object trong Java

Một biến có kiểu interface vẫn có thể gọi các phương thức public instance quen thuộc của java.lang.Object, dù interface không khai báo chúng tường minh. Quy tắc này thuộc về kiểm tra kiểu của Java; nó không có nghĩa interface kế thừa class Object.

## 1. Kiểu khai báo và kiểu runtime giải quyết hai câu hỏi khác nhau

~~~java
interface Named {
    String name();
}

final class Person implements Named {
    private final String name;

    Person(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "Person(" + name + ")";
    }
}
~~~

Với các kiểu trên, đoạn sau hợp lệ:

~~~java
Named person = new Person("Lan");
System.out.println(person.name());
System.out.println(person.toString());
System.out.println(person.equals(person));
System.out.println(person.hashCode());
System.out.println(person.getClass().getSimpleName());
~~~

Java xét hai điều:

1. **Compile time:** kiểu khai báo Named cho phép gọi name() vì interface khai báo phương thức đó. Lời gọi toString(), equals(), hashCode() và getClass() cũng hợp lệ theo quy tắc về phương thức public instance của Object.
2. **Runtime:** đối tượng thật là Person. Nếu Person override một instance method có thể override, lời gọi sẽ chạy implementation của Person. getClass() là final nên trả về lớp runtime thực tế.

Đây là lý do cùng một object có thể được truy cập qua biến Named hoặc Object mà vẫn chạy Person.toString(). Kiểu khai báo quyết định phương thức nào có thể gọi; dynamic dispatch chọn implementation cho instance method có thể override.

Interface không được viết cú pháp extends Object vì extends của interface chỉ nhận interface khác. Với interface không có direct superinterface, Java quy định các chữ ký public instance method thích hợp của Object như thành viên abstract ngầm định, trừ khi đã có khai báo tương thích. Interface con nhận thành viên thông qua quy tắc kế thừa interface.

## 2. Những phương thức Object nào có thể gọi?

| Phương thức | Gọi qua biến interface? | Điều cần nhớ |
| --- | --- | --- |
| toString(), equals(Object), hashCode() | Có | Hành vi có thể do lớp triển khai override; equals và hashCode phải giữ hợp đồng nhất quán |
| getClass() | Có | Là final, trả về lớp runtime; không thể override |
| wait(), wait(long), wait(long, int) | Có về mặt kiểu | Có thể ném InterruptedException; phải tuân thủ quy tắc monitor |
| notify(), notifyAll() | Có về mặt kiểu | Cần sở hữu monitor của object khi gọi |
| clone() | Không tự động | Object.clone() là protected, không phải public method được interface tự động phơi bày |

Một lời gọi hợp lệ khi biên dịch vẫn có thể thất bại lúc chạy. Ví dụ, wait() hoặc notify() ngoài ngữ cảnh sở hữu monitor phù hợp có thể ném IllegalMonitorStateException. Với wait(), code còn phải xử lý InterruptedException. Quy tắc kiểu không thay thế yêu cầu đồng bộ hóa.

Cloneable cũng không khai báo clone(). Nếu interface muốn yêu cầu API sao chép công khai, nó phải tự khai báo phương thức public thích hợp và lớp triển khai phải đáp ứng hợp đồng đó; các bài về clone đi sâu vào trường hợp này.

## 3. Điều quy tắc này không có nghĩa

Một interface không làm mọi API của runtime class trở nên khả dụng. Nếu Person có method internalDetail() mà Named không khai báo, caller không thể gọi person.internalDetail() chỉ vì object runtime là Person. Các public method tương ứng của Object là trường hợp đặc biệt do ngôn ngữ định nghĩa.

Tương tự, getClass() hữu ích để chẩn đoán hoặc dùng reflection, nhưng không nên thay cho một hợp đồng nghiệp vụ. So sánh task.getClass() với một class cụ thể có thể khiến code phụ thuộc implementation, proxy hoặc class runtime do lambda sinh ra. Nếu cần hành vi chung, khai báo method đó trong interface; nếu cần phân nhánh theo subtype, xác định rõ phạm vi và điều kiện.

## 4. Khai báo tường minh và giới hạn của default method

Interface có thể khai báo tường minh chữ ký abstract tương thích:

~~~java
interface Identified {
    String id();
    boolean equals(Object other);
    int hashCode();
    String toString();
}
~~~

Những khai báo này không tạo thuật toán equals, hashCode hay toString. Lớp triển khai có thể thừa hưởng implementation public phù hợp từ Object hoặc override nó theo ngữ nghĩa riêng.

Interface không được cung cấp default implementation override-equivalent với phương thức non-private của Object. Vì vậy, khai báo default toString(), default equals(Object) hoặc default hashCode() là lỗi biên dịch. Giới hạn này được phân tích ở bài kế tiếp; không nên lặp lại nó bằng cách cho rằng interface có thể “thắng” Object qua cơ chế default method.

## 5. Vì sao các chữ ký Object không làm mất tính functional interface?

Khi xác định phương thức chức năng duy nhất (SAM), Java không tính các public instance method tương ứng với Object thành những nhiệm vụ lambda bổ sung.

~~~java
@FunctionalInterface
interface Task {
    void run();
    boolean equals(Object other);
    String toString();
}

public class Main {
    public static void main(String[] args) {
        Task task = () -> System.out.println("Running");
        task.run();
    }
}
~~~

Chương trình in:

~~~text
Running
~~~

Task vẫn có một phương thức chức năng là run(). Nếu thêm một abstract method độc lập như stop(), interface không còn là functional interface theo quy tắc SAM.

## 6. Cách phân tích một lời gọi

Khi gặp reference.method(...) với reference có kiểu interface:

1. Kiểm tra method có trong interface, superinterface hoặc nhóm chữ ký Object được Java công nhận hay không.
2. Kiểm tra kiểu tham số, kiểu trả về và quyền truy cập; đừng suy ra mọi API của runtime class đều hiện ra.
3. Xác định method có thể override hay final.
4. Với instance method có thể override, theo dõi implementation của đối tượng runtime.
5. Với wait/notify, kiểm tra cả quy tắc monitor và xử lý ngoại lệ; lời gọi hợp lệ khi biên dịch không bảo đảm mọi ngữ cảnh runtime đều hợp lệ.

Tự kiểm tra:
- Vì sao Named không cần extends Object để gọi toString()? Vì Java có quy tắc riêng cho các public instance method thích hợp của Object.
- Có thể gọi protected clone() chỉ nhờ biến có kiểu interface không? Không.
- Nếu lớp runtime override toString(), lời gọi qua biến interface dùng bản nào? Bản override của lớp runtime.
- getClass() có thể override không? Không, vì method này final.

Điểm cốt lõi: interface xác định tập hành vi mà mã gọi được phép dùng; lớp thực tế cung cấp implementation cho các instance method có thể override. Các quy tắc đặc biệt của Object không biến interface thành lớp con của Object.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-and-object-002">Khai báo equals, hashCode và toString trong hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo equals, hashCode và toString trong hợp đồng interface

Một interface có thể khai báo tường minh ba chữ ký public quen thuộc của Object để làm rõ ý định tài liệu hóa API. Việc khai báo lại không cung cấp thuật toán và cũng không buộc mọi implementation có cùng ngữ nghĩa equality hay cùng định dạng chuỗi.

## 1. Khai báo chữ ký

~~~java
interface EntityContract {
    boolean equals(Object other);
    int hashCode();
    String toString();
}
~~~

Các method interface trên mặc định là public abstract. Chúng không cần thân hàm. Lớp triển khai có thể thừa hưởng các method public phù hợp từ Object mà không phải override chỉ vì interface đã nhắc lại chữ ký.

~~~java
final class Ticket implements EntityContract {
    // Không cần override toString(), equals() hoặc hashCode()
    // nếu hành vi mặc định của Object phù hợp với mục đích lớp.
}
~~~

Hành vi mặc định thường không đủ cho value object hoặc entity nghiệp vụ. Object.equals() mặc định so sánh identity tham chiếu; Object.toString() tạo chuỗi chung phục vụ chẩn đoán. Khi cần so sánh theo dữ liệu hoặc biểu diễn dễ đọc, lớp cụ thể phải định nghĩa rõ logic. Interface chỉ tài liệu hóa kỳ vọng, không tự tạo logic chung.

## 2. Hợp đồng equals

Một implementation equals đúng phải bảo đảm:

| Tính chất | Yêu cầu |
| --- | --- |
| Phản xạ | x.equals(x) là true với x khác null |
| Đối xứng | x.equals(y) và y.equals(x) cho cùng kết quả |
| Bắc cầu | nếu x bằng y và y bằng z thì x bằng z |
| Nhất quán | kết quả không đổi khi dữ liệu dùng để so sánh không đổi |
| Null | x.equals(null) là false |

Phương thức nên kiểm tra kiểu trước khi truy cập dữ liệu của đối tượng kia. Chữ ký để override là equals(Object), không phải equals(Product). Nếu chỉ khai báo equals(Product), đó là overload; lời gọi từ HashSet hoặc HashMap vẫn dùng equals(Object).

## 3. Hợp đồng hashCode

Quy tắc bắt buộc: nếu a.equals(b) là true thì a.hashCode() và b.hashCode() phải bằng nhau. Chiều ngược lại không bắt buộc; va chạm băm là hợp lệ. Khi override equals theo giá trị, thường phải override hashCode dựa trên cùng dữ liệu định nghĩa equality.

HashMap và HashSet dùng hash code để chọn vùng tìm kiếm, rồi dựa vào equals để xác định khóa hoặc phần tử phù hợp. Nếu object được dùng làm khóa mà trường tham gia equality/hash thay đổi, tìm kiếm hoặc xóa phần tử có thể thất bại. Ưu tiên trường bất biến cho equality của khóa. Hash code không phải ID duy nhất và không cần giữ nguyên giữa các lần chạy chương trình.

## 4. Ví dụ chạy được

~~~java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

interface EntityContract {
    boolean equals(Object other);
    int hashCode();
    String toString();
}

final class Product implements EntityContract {
    private final String code;
    private final String name;

    Product(String code, String name) {
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof Product product
                    && code.equals(product.code));
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return "Product{code='" + code + "', name='" + name + "'}";
    }
}

public class ObjectContractDemo {
    public static void main(String[] args) {
        Product first = new Product("P1", "Pen");
        Product sameCode = new Product("P1", "Pencil");
        Product other = new Product("P2", "Pen");

        System.out.println(first == sameCode);
        System.out.println(first.equals(sameCode));
        System.out.println(first.hashCode() == sameCode.hashCode());
        System.out.println(first.equals(other));

        Set<Product> products = new HashSet<>();
        products.add(first);
        products.add(sameCode);
        System.out.println(products.size());
        System.out.println(first);
    }
}
~~~

Kết quả:

~~~text
false
true
true
false
1
Product{code='P1', name='Pen'}
~~~

Product định nghĩa hai sản phẩm có cùng code là bằng nhau, bất kể name. Vì hashCode cũng dựa trên code, hai object equal có cùng hash code và HashSet chỉ giữ một phần tử đại diện. Product là final để không có subtype thay đổi riêng chính sách equality.

## 5. toString là biểu diễn, không phải định dạng dữ liệu

Object.toString() mặc định thường tạo chuỗi gồm tên lớp, ký tự @ và dạng hex của hash code; chuỗi đó không phải bản mô tả nghiệp vụ. Override có thể tạo chuỗi dễ đọc, nhưng Java không yêu cầu chuỗi đó duy nhất hay ổn định để serialization. Không dùng toString() làm giao thức truyền dữ liệu hoặc ID lâu dài trừ khi API đặc tả riêng định dạng đó. Tránh đưa bí mật hoặc dữ liệu nhạy cảm vào chuỗi log.

## 6. Lỗi thường gặp

- Chỉ override equals mà quên hashCode: hai object equal có thể có hash code khác nhau, làm collection hoạt động sai.
- Dùng hashCode bằng nhau để kết luận equals: hai object khác nhau có thể va chạm băm.
- Viết equals(Product) và nghĩ đã override equals(Object): đó là overload.
- Dùng các trường khác nhau cho equals và hashCode: có thể phá quy tắc equal implies same hash.
- Cho rằng khai báo trong interface đã cung cấp logic: chữ ký abstract chỉ nêu method, không định nghĩa semantics.

## 7. Khi nào nên khai báo tường minh?

Nên cân nhắc khi ngữ nghĩa equality hoặc biểu diễn là phần quan trọng của tài liệu interface. Nếu interface chỉ mô tả hành vi đặc thù, khai báo lại ba chữ ký thường không thêm khả năng gọi mới. Trong cả hai trường hợp, tài liệu API phải nói rõ dữ liệu nào quyết định equality và các implementation cần tuân thủ điều gì.

Không viết default equals(Object), default hashCode() hoặc default toString() trong interface. Giới hạn biên dịch và lý do thiết kế được giải thích riêng ở bài về default method và Object.

Tự kiểm tra:
- Nếu a.equals(b) là true, hash code có bắt buộc bằng nhau không? Có.
- Hai hash code bằng nhau có chứng minh equals là true không? Không.
- Interface khai báo String toString() có buộc lớp phải override không? Không nhất thiết; implementation public từ Object có thể đáp ứng.
- Nếu một object đã nằm trong HashSet rồi thay đổi trường equality, chuyện gì có thể xảy ra? HashSet có thể không tìm được phần tử theo hash mới.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-and-object-003">Vì sao default method không được ghi đè phương thức public của Object</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao interface không được định nghĩa default method thay thế phương thức Object?

Java cho interface cung cấp default method để bổ sung hành vi dùng chung. Tuy nhiên, một interface không được khai báo default method có chữ ký override-equivalent với **bất kỳ phương thức non-private nào của java.lang.Object**. Các trường hợp quen thuộc là toString(), equals(Object) và hashCode(); phạm vi quy tắc không chỉ giới hạn ở ba method public này.

## 1. Ví dụ không biên dịch

~~~java
interface BadDisplay {
    default String toString() {
        return "custom display";
    }
}
~~~

~~~java
interface BadIdentity {
    default boolean equals(Object other) {
        return true;
    }

    default int hashCode() {
        return 1;
    }
}
~~~

Cả hai khai báo đều không hợp lệ. Đổi thân hàm hoặc thêm @Override không sửa được lỗi. Quy tắc còn áp dụng cho chữ ký override-equivalent với các method non-private khác của Object, kể cả protected clone(). Một số method public như getClass(), wait() và notify() còn final nên class cụ thể cũng không được override chúng.

## 2. Vì sao có giới hạn này?

Mọi class Java đều kế thừa trực tiếp hoặc gián tiếp từ Object. Nếu interface có thể cung cấp default implementation thay thế những method nền tảng này, ý nghĩa của phương thức sẽ không rõ ràng: hành vi của class đến từ Object hoặc superclass vẫn có ưu tiên trong cơ chế phân giải method. Cấm khai báo giúp API không hứa một default implementation mà lớp không thể sử dụng như cách override thông thường.

Đặc biệt, equals và hashCode ảnh hưởng đến equality và collection dựa trên hashing. Chính sách của chúng cần khớp với dữ liệu và identity của lớp cụ thể. toString cũng thường phụ thuộc trạng thái hoặc mục đích chẩn đoán của từng lớp. Một default chung trong interface không thể an toàn áp đặt ngữ nghĩa đó cho mọi implementation.

## 3. Abstract declaration khác default implementation

Interface vẫn có thể khai báo một chữ ký abstract tương thích. Nó thể hiện hợp đồng nhưng không cung cấp thân hàm:

~~~java
interface NamedObject {
    String name();
    String toString();

    default String description() {
        return name() + " | " + toString();
    }
}

final class Product implements NamedObject {
    private final String name;

    Product(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "Product[" + name + "]";
    }
}
~~~

NamedObject.description() hợp lệ vì description là method mới, dù thân hàm gọi toString(). Product cung cấp implementation cụ thể cho toString(). Một lớp cũng có thể thừa hưởng Object.toString() nếu implementation đó đáp ứng chữ ký interface và ngữ nghĩa mặc định là đủ.

## 4. “Class wins” không cho phép default toString()

Quy tắc phương thức lớp được ưu tiên hơn default method áp dụng cho các xung đột hợp lệ. Nó không làm cho default toString() trở nên hợp lệ; khai báo đó bị từ chối trước khi phân giải implementation.

~~~java
interface Greeter {
    default String greet() {
        return "Interface";
    }
}

class BaseGreeter {
    public String greet() {
        return "Base class";
    }
}

final class ActualGreeter extends BaseGreeter implements Greeter {
}

public class Main {
    public static void main(String[] args) {
        Greeter greeter = new ActualGreeter();
        System.out.println(greeter.greet());
    }
}
~~~

Kết quả:

~~~text
Base class
~~~

Ví dụ dùng greet(), một method thông thường không bị giới hạn bởi quy tắc Object. Implementation của BaseGreeter được chọn. Không suy ra từ ví dụ này rằng Java cho phép khai báo default method trùng chữ ký toString(), equals(Object) hoặc hashCode().

## 5. Cách thiết kế đúng

- Nếu muốn chia sẻ hành vi mặc định, đặt tên method riêng như description(), displayLabel() hoặc describe().
- Nếu interface cần nêu rõ một chữ ký Object, có thể khai báo abstract tương thích nhưng không thêm default body.
- Nếu cần chính sách equals/hashCode/toString theo dữ liệu, triển khai hoặc override ở class cụ thể.
- Nếu cần API sao chép công khai, đừng cố dùng default clone(); khai báo một phương thức public có hợp đồng rõ ràng.

Tự kiểm tra:
- Có thể khai báo abstract String toString() trong interface không? Có, nếu chữ ký tương thích.
- Có thể khai báo default int hashCode() không? Không.
- Quy tắc chỉ áp dụng cho ba method public thường gặp không? Không; quy tắc bao quát method non-private của Object.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-and-object-004">Triển khai equals và hashCode nhất quán giữa các kiểu con</a>

<details>
<summary>Xem chi tiết</summary>

# Triển khai equals và hashCode nhất quán giữa các kiểu con trong Java

Khi một class có subtype, equality phải có một định nghĩa thống nhất cho cả hierarchy. Lỗi thường gặp là lớp cha chấp nhận một object lớp con vì các trường chung giống nhau, trong khi lớp con so sánh thêm trường riêng và từ chối object lớp cha. Khi đó equals mất tính đối xứng; nếu hashCode cũng dựa trên các tập trường khác nhau, hợp đồng hash có thể bị phá vỡ.

## 1. Hợp đồng cần giữ

| Tính chất | Câu hỏi kiểm tra |
| --- | --- |
| Phản xạ | x.equals(x) có true không? |
| Đối xứng | x.equals(y) và y.equals(x) có cùng kết quả không? |
| Bắc cầu | nếu x bằng y và y bằng z thì x có bằng z không? |
| Nhất quán | khi dữ liệu liên quan không đổi, kết quả có ổn định không? |
| Null | x.equals(null) có false không? |
| Hash | nếu x.equals(y) là true, hai hash code có bằng nhau không? |

Hai object không bằng nhau vẫn có thể trùng hash code. Điều bắt buộc chỉ là equal implies same hash. Compiler không thể chứng minh các tính chất này chỉ từ việc code biên dịch.

Mỗi hierarchy cần quyết định trước: object khác lớp cụ thể có thể bằng nhau không, subtype có được thêm trường vào equality không, và ai được quyền thay đổi chính sách khi thêm subtype. Đừng bắt đầu bằng cách chọn instanceof hoặc getClass() một cách máy móc; hãy bắt đầu bằng ý nghĩa identity/value trong miền nghiệp vụ.

## 2. Lỗi kinh điển: lớp cha dùng instanceof, lớp con thêm điều kiện

~~~java
class Point {
    final int x;
    final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Point point
                && x == point.x && y == point.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }
}

final class ColorPoint extends Point {
    final String color;

    ColorPoint(int x, int y, String color) {
        super(x, y);
        this.color = color;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ColorPoint point
                && super.equals(point)
                && color.equals(point.color);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + color.hashCode();
    }
}

public class BrokenEqualityDemo {
    public static void main(String[] args) {
        Point plain = new Point(1, 2);
        Point colored = new ColorPoint(1, 2, "blue");
        System.out.println(plain.equals(colored));
        System.out.println(colored.equals(plain));
    }
}
~~~

Kết quả:

~~~text
true
false
~~~

Point chấp nhận ColorPoint vì tọa độ trùng; ColorPoint từ chối Point vì thiếu màu. Hai chiều cho kết quả khác nhau. Point còn có thể xem hai object là equal trong khi ColorPoint tạo hash code dựa trên màu, dẫn đến vi phạm quy tắc equal implies same hash. Việc cả hai method đều có @Override không bảo đảm hợp đồng đúng.

## 3. Chọn ranh giới equality cho cả hierarchy

**Mô hình A: chỉ bằng nhau khi cùng lớp runtime.** Phù hợp khi mỗi subtype mang dữ liệu hoặc ý nghĩa khác nhau. Dùng getClass() để từ chối subtype khác, rồi so sánh trường chung và trường riêng. Nếu muốn khóa chính sách cho cả cây, lớp cha có thể định nghĩa final equals/hashCode và gọi hook protected để subtype so sánh chi tiết.

**Mô hình B: equality theo identity chung xuyên subtype.** Phù hợp khi các subtype là những cách nhìn khác nhau của cùng thực thể và có ID toàn cục, bất biến. Lớp cha có thể dùng instanceof với ID chung, đồng thời khai báo final equals/hashCode để subtype không thêm điều kiện riêng. Chỉ chọn mô hình này nếu hai subtype cùng ID thực sự phải đại diện cùng identity nghiệp vụ.

## 4. Ví dụ cho hai mô hình

~~~java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class EqualitySubtypesDemo {
    static abstract class Notice {
        private final String code;

        Notice(String code) {
            this.code = Objects.requireNonNull(code);
        }

        protected abstract boolean sameDetails(Notice other);
        protected abstract int detailsHash();

        @Override
        public final boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Notice other = (Notice) obj;
            return code.equals(other.code) && sameDetails(other);
        }

        @Override
        public final int hashCode() {
            return Objects.hash(getClass(), code, detailsHash());
        }
    }

    static final class EmailNotice extends Notice {
        private final String email;

        EmailNotice(String code, String email) {
            super(code);
            this.email = Objects.requireNonNull(email);
        }

        @Override
        protected boolean sameDetails(Notice other) {
            return email.equals(((EmailNotice) other).email);
        }

        @Override
        protected int detailsHash() {
            return email.hashCode();
        }
    }

    static final class SmsNotice extends Notice {
        private final String phone;

        SmsNotice(String code, String phone) {
            super(code);
            this.phone = Objects.requireNonNull(phone);
        }

        @Override
        protected boolean sameDetails(Notice other) {
            return phone.equals(((SmsNotice) other).phone);
        }

        @Override
        protected int detailsHash() {
            return phone.hashCode();
        }
    }

    static abstract class GlobalEntity {
        private final String id;

        GlobalEntity(String id) {
            this.id = Objects.requireNonNull(id);
        }

        @Override
        public final boolean equals(Object obj) {
            return this == obj
                    || (obj instanceof GlobalEntity other && id.equals(other.id));
        }

        @Override
        public final int hashCode() {
            return id.hashCode();
        }
    }

    static final class UserEntity extends GlobalEntity {
        UserEntity(String id) { super(id); }
    }

    static final class AdminEntity extends GlobalEntity {
        AdminEntity(String id) { super(id); }
    }

    public static void main(String[] args) {
        Notice a = new EmailNotice("N1", "a@example.org");
        Notice b = new EmailNotice("N1", "a@example.org");
        Notice c = new EmailNotice("N1", "b@example.org");
        Notice d = new SmsNotice("N1", "12345");

        Set<Notice> notices = new HashSet<>();
        notices.add(a);
        notices.add(b);
        notices.add(c);
        notices.add(d);

        HashMap<Notice, String> messages = new HashMap<>();
        messages.put(a, "sent");

        System.out.println(a.equals(b));
        System.out.println(a.hashCode() == b.hashCode());
        System.out.println(a.equals(c));
        System.out.println(a.equals(d));
        System.out.println(notices.size());
        System.out.println(messages.get(b));

        GlobalEntity user = new UserEntity("G1");
        GlobalEntity admin = new AdminEntity("G1");
        System.out.println(user.equals(admin));
        System.out.println(user.hashCode() == admin.hashCode());
    }
}
~~~

Kết quả:

~~~text
true
true
false
false
3
sent
true
true
~~~

Notice dùng mô hình A: chỉ cùng loại và cùng dữ liệu mới bằng nhau. GlobalEntity dùng mô hình B: UserEntity và AdminEntity cùng ID được coi là cùng identity. Hai lựa chọn này đều có thể đúng nếu phù hợp nghiệp vụ; không được trộn chúng trong một hierarchy. Trong mô hình A, phép cast ở sameDetails an toàn vì equals đã xác nhận đúng cùng lớp runtime trước khi gọi hook.

## 5. instanceof, getClass và canEqual trả lời câu hỏi khác nhau

- other instanceof Parent chấp nhận Parent và subtype.
- getClass() == other.getClass() yêu cầu hai object có đúng cùng lớp runtime.
- final equals/hashCode ở base ngăn subtype thay đổi riêng chính sách equality.
- canEqual là một mẫu để hai phía xác nhận có thể so sánh với nhau; chỉ có canEqual không tự chứng minh tính bắc cầu hoặc hashCode đúng.

Kiểm tra lớp cụ thể thường phù hợp khi subtype mang trường equality riêng. Tuy nhiên, nó có thể không phù hợp với ORM proxy hoặc entity có lớp runtime do framework tạo. ID được cấp sau khi persist cũng làm equality khó hơn vì giá trị hash có thể đổi trong vòng đời object. Với entity, hãy tuân theo semantics và lifecycle của framework cụ thể.

Record là final và tự định nghĩa equals/hashCode theo components, phù hợp với nhiều value object. Nếu component là mảng, equality được sinh không tự động trở thành deep equality vì array thường so sánh theo tham chiếu. Sealed hierarchy giới hạn subtype được phép nhưng không tự chọn chính sách equality thay cho bạn.

## 6. Các kiểu dữ liệu và trạng thái dễ tạo lỗi

- **Trường mutable:** nếu một trường dùng cho equals/hashCode thay đổi sau khi object được đưa vào HashSet hoặc dùng làm key của HashMap, thao tác tìm kiếm có thể thất bại. Ưu tiên khóa bất biến.
- **Mảng:** Objects.equals(arrayA, arrayB) không so sánh phần tử của mảng; dùng Arrays.equals hoặc Arrays.deepEquals tùy cấu trúc, và cặp hash tương ứng.
- **BigDecimal:** equals phân biệt scale; 2.0 và 2.00 có thể không bằng nhau dù compareTo trả về 0. Chọn rõ semantics trước khi thiết kế hash.
- **Số thực:** NaN và +0.0/-0.0 cần quy tắc so sánh tương thích với hash; dùng cách xử lý nhất quán như Double.compare và Double.hashCode khi phù hợp.
- **Subclass có field mới:** nếu base equality coi mọi subtype có cùng ID là equal, subtype không được tự thêm trường vào equality mà vẫn giữ quy tắc chung đó.

Một interface có thể được dùng làm kiểu tham chiếu khi gọi equals; implementation vẫn được dispatch theo lớp runtime. Chỉ cùng implement một interface hoặc cùng trả về id() không có nghĩa hai implementation phải bằng nhau. equals(Customer) cũng không thay thế equals(Object); dùng @Override để phát hiện lỗi chữ ký.

## 7. Kiểm thử cả hierarchy

Dùng ma trận gồm: cùng object, object cùng dữ liệu cùng subtype, khác dữ liệu cùng subtype, khác subtype cùng key, khác subtype khác key và null. Với từng cặp và bộ ba thích hợp, kiểm tra phản xạ, đối xứng, bắc cầu, nhất quán và quy tắc equal implies same hash. Sau đó kiểm tra HashSet không thêm bản sao equal và HashMap.get tìm được value khi dùng key khác nhưng equal với key ban đầu.

Đừng chỉ kiểm tra parent.equals(child); luôn thử cả child.equals(parent). Với nhiều tầng kế thừa, kiểm tra mọi cặp subtype liên quan. Nếu dùng assert trong Java, nhớ rằng assertion mặc định tắt nếu không chạy với -ea; test framework có thể giúp thực thi các kiểm tra ổn định hơn.

Trước khi code, hãy trả lời: object có semantics identity hay value; subtype khác nhau có được bằng nhau không; trường nào tham gia equality; ai được quyền thay đổi chính sách; và framework có proxy hoặc ID được cấp trễ không. Chính sách rõ ràng quan trọng hơn việc chọn riêng instanceof hoặc getClass().


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-and-object-005">clone có phạm vi protected và yêu cầu public của interface</a>

<details>
<summary>Xem chi tiết</summary>

# Object.clone() protected và yêu cầu public của interface

Object.clone() có phạm vi protected. Một interface có thể khai báo clone() như một method công khai, nhưng lớp triển khai phải cung cấp một implementation public phù hợp; method protected được kế thừa từ Object không đáp ứng hợp đồng public của interface.

## 1. Phạm vi truy cập của Object.clone()

Chữ ký nền tảng có dạng:

~~~java
protected native Object clone() throws CloneNotSupportedException;
~~~

- **protected** là quy tắc truy cập tại compile time. Code trong cùng package có thể truy cập theo quy tắc package; subclass có thể truy cập theo các điều kiện protected. Code ngoài package không thể tùy ý gọi someObject.clone().
- **native** cho biết phần triển khai đến từ runtime thay vì thân Java thông thường.
- **CloneNotSupportedException** là checked exception mà method có thể ném khi object không hỗ trợ cơ chế clone mặc định.
- **Object** là kiểu trả về của method gốc. Override có thể dùng kiểu trả về cụ thể hơn theo quy tắc covariant return type.

Lỗi truy cập lúc biên dịch khác với CloneNotSupportedException lúc chạy. Một lệnh gọi có thể bị từ chối vì caller không có quyền truy cập, hoặc caller có quyền truy cập nhưng cơ chế clone không được hỗ trợ.

## 2. Interface yêu cầu một method public

Method thông thường trong interface là public theo mặc định. Interface không có method protected:

~~~java
interface PublicClone {
    Object clone() throws CloneNotSupportedException;
}
~~~

Dù không ghi modifier public, clone() trong PublicClone vẫn là public. Nếu class chỉ thừa hưởng protected Object.clone(), nó chưa đáp ứng method public mà PublicClone yêu cầu. Class phải override clone() thành public; Java cho phép mở rộng quyền truy cập khi override, nhưng không cho thu hẹp quyền truy cập của method public.

~~~java
interface PublicClone {
    Object clone() throws CloneNotSupportedException;
}

class InvalidNote implements PublicClone {
    // Không hợp lệ: inherited Object.clone() vẫn protected.
    // Class phải cung cấp một implementation public phù hợp.
}
~~~

Tên clone không bị cấm trong interface. Vấn đề là visibility của implementation phải đáp ứng hợp đồng public.

## 3. Ví dụ hoàn chỉnh

~~~java
public class InterfaceCloneDemo {
    interface PublicClone {
        Object clone() throws CloneNotSupportedException;
    }

    static final class Note implements PublicClone, Cloneable {
        private final String text;

        Note(String text) {
            this.text = text;
        }

        @Override
        public Note clone() throws CloneNotSupportedException {
            return (Note) super.clone();
        }

        String text() {
            return text;
        }
    }

    public static void main(String[] args) throws CloneNotSupportedException {
        Note original = new Note("Java");
        PublicClone publicView = original;
        Note copy = original.clone();
        Object throughInterface = publicView.clone();

        System.out.println(copy.text());
        System.out.println(original == copy);
        System.out.println(throughInterface.getClass().getSimpleName());
    }
}
~~~

Kết quả:

~~~text
Java
false
Note
~~~

Note khai báo public clone(), trả về Note theo kiểu covariant và gọi super.clone(). PublicClone cho phép caller gọi clone() qua biến có kiểu interface. Cloneable là cơ chế riêng: nó cho phép Object.clone() mặc định tạo bản sao; nó không làm clone thành public và không khai báo method clone().

## 4. Visibility và khả năng clone không giống nhau

| Trường hợp | Kết quả |
| --- | --- |
| Class implements Cloneable nhưng không override clone() | Biên dịch được; method kế thừa vẫn protected |
| Interface yêu cầu public clone(), class chỉ thừa hưởng protected clone() | Không đáp ứng hợp đồng interface |
| Class override clone() thành public và supports Cloneable | Có thể công khai clone; super.clone() mặc định tạo bản sao |
| Class public clone() gọi super.clone() nhưng không supports Cloneable | Có thể biên dịch, nhưng cơ chế Object.clone() ném CloneNotSupportedException |
| Class không dùng Object.clone() mà tự viết method clone() | Hành vi phụ thuộc implementation do class định nghĩa |

Cloneable là marker interface rỗng. Vì vậy, biến có kiểu Cloneable không thể gọi clone() qua chính interface đó. Nếu API cần một thao tác sao chép đa hình, hãy định nghĩa interface có method copy() với ngữ nghĩa rõ ràng.

## 5. Shallow copy và lựa chọn API

Object.clone() mặc định sao chép giá trị các trường. Trường primitive được sao chép giá trị; trường tham chiếu sao chép tham chiếu nên object mutable bên trong có thể bị chia sẻ giữa bản gốc và bản clone. Public visibility không làm bản sao trở thành deep copy.

~~~java
class Profile implements Cloneable {
    StringBuilder label = new StringBuilder("Java");

    @Override
    public Profile clone() throws CloneNotSupportedException {
        return (Profile) super.clone();
    }
}
~~~

Hai Profile sau clone là hai object khác nhau nhưng có thể dùng chung StringBuilder. Nếu cần trạng thái độc lập, implementation phải sao chép rõ các trường mutable. Copy constructor hoặc static factory thường thể hiện ý định tốt hơn và có thể chạy lại validation trong constructor.

Nguồn tham khảo:
- [Java SE 17 API: Object.clone()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#clone())
- [Java SE 17 API: Cloneable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Cloneable.html)
- [JLS 17 §9.4: Interface Method Declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.4)
- [JLS 17 §8.4.8: Overriding](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8)

Tự kiểm tra:
- Cloneable có khai báo clone() không? Không.
- Interface có thể yêu cầu protected clone() không? Không; method interface thông thường là public.
- Vì sao public clone() không bảo đảm deep copy? Vì Object.clone() sao chép nông các trường.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interface-and-object-006">Giới hạn của marker interface Cloneable</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của marker interface Cloneable trong Java

Cloneable là một dấu hiệu để cơ chế mặc định của Object.clone() biết rằng object được phép clone. Nó không khai báo phương thức sao chép, không mở quyền truy cập public, và không bảo đảm bản sao độc lập hay giữ được mọi invariant của lớp.

## 1. Marker đánh dấu khả năng, không cung cấp thao tác

~~~java
public interface Cloneable {
}
~~~

Đây là hình dạng của interface chuẩn java.lang.Cloneable: không có method nào. Vì vậy, biến có kiểu Cloneable không thể gọi x.clone(); compiler không tìm thấy method clone() trong interface. Object.clone() được khai báo protected và phải được gọi từ ngữ cảnh có quyền truy cập phù hợp. Phần API public và hợp đồng interface được trình bày ở bài về clone protected.

Với hành vi mặc định của Object.clone(), runtime kiểm tra object có thuộc một class implements Cloneable hay không, kể cả qua kế thừa. Nếu không, nó ném CloneNotSupportedException. Nếu có, nó tạo object mới cùng lớp runtime và sao chép giá trị các trường theo kiểu shallow copy.

~~~text
Object.clone() được gọi
    |
    +-- object không hỗ trợ Cloneable
    |       -> CloneNotSupportedException
    |
    +-- object hỗ trợ Cloneable
            -> tạo object mới cùng lớp runtime
            -> sao chép giá trị trường
            -> không clone đệ quy mọi object được tham chiếu
~~~

Một class có thể tự định nghĩa method tên clone() theo cách riêng. Không nên suy ra mọi method cùng tên đều dùng cơ chế Object.clone().

## 2. Chương trình minh họa shallow copy

~~~java
public class CloneableLimitsDemo {
    static final class Address {
        String city;
        Address(String city) { this.city = city; }
    }

    static final class Person implements Cloneable {
        final String name;
        final Address address;

        Person(String name, Address address) {
            this.name = name;
            this.address = address;
        }

        @Override
        public Person clone() {
            try {
                return (Person) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    static final class WithoutMarker {
        @Override
        public WithoutMarker clone() throws CloneNotSupportedException {
            return (WithoutMarker) super.clone();
        }
    }

    static final class SafePerson {
        final String name;
        final Address address;

        SafePerson(String name, Address address) {
            this.name = name;
            this.address = address;
        }

        SafePerson copy() {
            return new SafePerson(name, new Address(address.city));
        }
    }

    public static void main(String[] args) throws CloneNotSupportedException {
        Person original = new Person("Lan", new Address("Hà Nội"));
        Person cloned = original.clone();
        System.out.println(original != cloned);
        System.out.println(original.address == cloned.address);
        cloned.address.city = "Huế";
        System.out.println(original.address.city);

        SafePerson safe = new SafePerson("Minh", new Address("Đà Nẵng"));
        SafePerson independent = safe.copy();
        independent.address.city = "Cần Thơ";
        System.out.println(safe.address.city);

        try {
            new WithoutMarker().clone();
        } catch (CloneNotSupportedException e) {
            System.out.println(e.getClass().getSimpleName());
        }

        int[][] matrix = {{1, 2}};
        int[][] matrixClone = matrix.clone();
        System.out.println(matrix != matrixClone);
        System.out.println(matrix[0] == matrixClone[0]);
    }
}
~~~

Kết quả:

~~~text
true
true
Huế
Đà Nẵng
CloneNotSupportedException
true
true
~~~

Person là hai object khác nhau sau clone, nhưng cùng tham chiếu Address. Sửa địa chỉ qua bản clone cũng thay đổi dữ liệu mà bản gốc nhìn thấy. SafePerson.copy() chủ động tạo Address mới nên hai object không chia sẻ địa chỉ. WithoutMarker biên dịch được nhưng super.clone() ném CloneNotSupportedException vì object không hỗ trợ marker. Với mảng nhiều chiều, clone mảng ngoài không clone các mảng con.

## 3. Những điều Cloneable không bảo đảm

Cloneable không định nghĩa:
- Deep copy toàn bộ đồ thị object.
- Hai object sau clone phải bằng nhau theo equals().
- Quy tắc giữ, xóa hoặc tạo mới ID nghiệp vụ.
- Cách sao chép socket, file descriptor, lock, listener, cache hay transaction.
- Snapshot nhất quán khi nhiều thread đang sửa trạng thái.
- Việc constructor hoặc field initializer chạy lại để kiểm tra invariant.

Object.clone() tạo object mà không chạy constructor của lớp đích theo cách new thông thường. Vì vậy, nếu constructor đăng ký listener, kiểm tra dữ liệu hoặc khởi tạo tài nguyên, shallow clone có thể bỏ qua những bước đó. Trường final chỉ cấm gán lại tham chiếu sau khởi tạo; nó không khiến object được tham chiếu trở nên immutable. Ví dụ final Address vẫn bị chia sẻ trong chương trình trên.

Không phải mọi clone đều sai. Shallow copy có thể phù hợp với lớp được thiết kế có toàn bộ trạng thái primitive hoặc tham chiếu immutable. Nhưng marker không chứng minh điều đó; lớp phải định nghĩa và kiểm thử ngữ nghĩa bản sao.

## 4. Kế thừa và trạng thái mutable

Nếu superclass implements Cloneable, subclass thường cũng hỗ trợ marker qua kế thừa. Điều đó không tự bảo đảm các trường mới của subclass được sao chép đúng theo nghiệp vụ. Một List mutable mới thêm vào subclass vẫn được chia sẻ sau shallow clone. Superclass cũng có thể override clone() theo hợp đồng riêng, nên không được giả định mọi đường gọi đều đi thẳng đến implementation mặc định của Object.

Mảng Java là trường hợp đặc biệt: mảng hỗ trợ clone(). Clone một mảng primitive tạo mảng mới với các giá trị primitive được sao chép. Với mảng tham chiếu hoặc mảng nhiều chiều, các phần tử là tham chiếu và không được clone đệ quy. Vì thế, matrix.clone() tạo mảng ngoài mới nhưng vẫn dùng chung từng mảng con.

## 5. Khi nào nên dùng copy constructor hoặc factory?

~~~java
public final class Snapshot {
    private final String name;
    private final java.util.List<String> tags;

    public Snapshot(String name, java.util.List<String> tags) {
        this.name = java.util.Objects.requireNonNull(name);
        this.tags = java.util.List.copyOf(tags);
    }

    public Snapshot(Snapshot source) {
        this(source.name, source.tags);
    }

    public Snapshot copy() {
        return new Snapshot(this);
    }

    public java.util.List<String> tags() {
        return tags;
    }
}
~~~

Snapshot dùng List.copyOf để giữ danh sách không sửa được qua API trả ra. Vì phần tử là String immutable, chia sẻ phần tử an toàn cho ví dụ này; List.copyOf không phải deep copy tổng quát cho các phần tử mutable.

Copy constructor hoặc static factory làm rõ trường nào được chia sẻ, trường nào được sao chép, và cho phép kiểm tra invariant trong constructor. Nếu cần một hợp đồng sao chép đa hình, có thể định nghĩa interface riêng:

~~~java
interface Copyable<T> {
    T copy();
}
~~~

Tuy vậy, chữ ký copy() vẫn cần tài liệu hóa mức độ độc lập, lỗi có thể phát sinh và quy tắc giữ trạng thái. Nếu object vốn immutable, có thể không cần clone; tái sử dụng instance thường đơn giản hơn.

## 6. Lỗi thường gặp

| Nhận định | Vấn đề | Cách xử lý |
| --- | --- | --- |
| implements Cloneable nghĩa là caller gọi được clone() từ mọi nơi | Object.clone() vẫn protected | Cung cấp API public nếu thật sự cần |
| Cloneable có method clone() | Marker interface rỗng | Phân biệt marker với method của Object |
| Clone tự sao chép mọi object bên trong | Object.clone() mặc định shallow | Sao chép rõ từng trường mutable cần độc lập |
| final field không bị chia sẻ | final không làm object được tham chiếu immutable | Kiểm tra identity và trạng thái của object con |
| Clone chạy constructor | Cơ chế clone không dùng constructor như new | Dùng constructor/factory nếu cần thiết lập lại invariant |
| Cloneable bảo đảm clone an toàn qua inheritance | subtype có thể thêm trạng thái hoặc tài nguyên | Thiết kế và kiểm thử hợp đồng cho cả hierarchy |

## 7. Checklist thiết kế

1. Xác định API nào cho phép caller yêu cầu sao chép; đừng dựa vào marker để suy ra visibility.
2. Quyết định các object mutable nào được phép chia sẻ giữa bản gốc và bản sao.
3. Kiểm tra field final có tham chiếu tới object mutable hay không.
4. Xem constructor có thiết lập invariant, đăng ký listener hoặc quản lý tài nguyên không.
5. Với inheritance, kiểm tra cả các trường được thêm bởi subtype.
6. Kiểm thử identity bằng == và thay đổi trạng thái sau copy; equals() đúng không có nghĩa hai object không chia sẻ trạng thái.
7. Cân nhắc copy constructor, static factory hoặc copy() có tên rõ ràng khi cần semantics sao chép có kiểm soát.

Tự kiểm tra:
- Class chỉ implements Cloneable nhưng không override clone(): caller bên ngoài có mặc nhiên gọi được không? Không.
- Nếu object không hỗ trợ Cloneable nhưng public clone() gọi super.clone(), điều gì xảy ra? Cơ chế mặc định ném CloneNotSupportedException.
- Vì sao final Address vẫn có thể bị chia sẻ sau clone? Clone sao chép giá trị tham chiếu, còn final chỉ ngăn gán lại trường.
- Mảng hai chiều sau khi clone mảng ngoài có hoàn toàn độc lập không? Không; mảng con vẫn được chia sẻ.

Cloneable chỉ bật một nhánh hành vi đặc biệt của Object.clone(). Nó không thay thế hợp đồng sao chép do API thiết kế, cũng không bảo đảm bản sao an toàn. Hãy chọn Cloneable khi cần tương thích với cơ chế này và đã xác định rõ shallow/deep copy, invariant cùng trạng thái được chia sẻ.


</details>

- [Quay lại đầu trang](#back-to-top)
