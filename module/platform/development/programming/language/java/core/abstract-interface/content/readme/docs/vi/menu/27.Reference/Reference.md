<a id="back-to-top"></a>

# Tra cứu

## Menu
- [Bảng so sánh abstract class, interface và concrete class](#reference-001)
- [Bảng modifier hợp lệ theo loại thành viên](#reference-002)
- [Bảng quy tắc ưu tiên và xử lý xung đột phương thức](#reference-003)
- [Các hiểu lầm thường gặp và cách kiểm chứng](#reference-004)
- [Tra cứu Java Language Specification và Javadoc theo chủ đề](#reference-005)

## <a id="reference-001">Bảng so sánh abstract class, interface và concrete class</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng so sánh abstract class, interface và concrete class trong Java

Trong Java, **interface**, **abstract class** và **concrete class** giải quyết những nhu cầu khác nhau khi thiết kế kiểu dữ liệu. Interface mô tả một hợp đồng mà các kiểu có thể cung cấp; abstract class là lớp cơ sở có thể chia sẻ trạng thái và hành vi; concrete class là lớp đủ hoàn chỉnh để tạo đối tượng trực tiếp.

Ba khái niệm này không phải ba lựa chọn loại trừ nhau. Một concrete class có thể kế thừa abstract class và đồng thời triển khai một hoặc nhiều interface. Muốn chọn đúng, hãy xem phần nào cần được cam kết trong hợp đồng, phần nào cần dùng chung mã và trạng thái, và kiểu nào cần được khởi tạo thành đối tượng.

## 1. Bảng so sánh nhanh

| Tiêu chí | Interface | Abstract class | Concrete class |
|---|---|---|---|
| Mục đích chính | Định nghĩa hợp đồng hoặc khả năng mà kiểu cung cấp | Chia sẻ nền tảng và hành vi giữa các lớp có liên quan | Cung cấp một kiểu có thể tạo đối tượng và sử dụng |
| Có thể dùng `new` trực tiếp? | Không | Không | Có, nếu constructor và quyền truy cập cho phép |
| Khai báo phương thức trừu tượng | Có thể khai báo phương thức không có thân | Có thể khai báo phương thức không có thân | Không được còn phương thức trừu tượng chưa triển khai |
| Phương thức có thân | Có `default`, `static` và `private` methods theo quy tắc Java | Có thể có phương thức thường và phương thức `static` | Có thể có phương thức thường và `static` |
| Trạng thái đối tượng | Không có trường instance; trường interface là `public static final` ngầm định | Có thể có trường instance và constructor | Có thể có trường instance và constructor |
| Kế thừa / triển khai | Interface có thể `extends` nhiều interface; class dùng `implements` | Một class có thể `extends` một class cha và abstract class có thể `implements` interface | Có thể `extends` một class và `implements` nhiều interface |
| Khi nào hữu ích? | Các lớp khác nhau cần cam kết cùng một hành vi | Các lớp có chung dữ liệu, quy tắc khởi tạo hoặc logic | Cần một kiểu hoàn chỉnh để tạo và sử dụng đối tượng |

Điểm cần nhớ: **interface nói một kiểu có thể làm gì; abstract class có thể cung cấp nền tảng chung; concrete class cung cấp một triển khai có thể tạo đối tượng**. Đây là cách nhớ nhanh, còn quyết định thực tế phụ thuộc vào thiết kế cụ thể.

## 2. Interface: hợp đồng về khả năng

Interface khai báo các phương thức mà kiểu triển khai cần cung cấp. Các class không cần có cùng lớp cha để cùng triển khai một interface. Điều này hữu ích khi nhiều loại đối tượng khác nhau có cùng một khả năng, chẳng hạn thanh toán, ghi log, sắp xếp hoặc cung cấp mã định danh.

~~~java
interface Payable {
    double monthlyPay();
}
~~~

Một class triển khai `Payable` phải cung cấp phương thức `monthlyPay()`, trừ khi chính class đó là abstract. Bên gọi có thể làm việc với kiểu `Payable` mà không cần biết đối tượng thuộc class nào.

~~~java
final class Contractor implements Payable {
    private final double hourlyRate;
    private final int hours;

    Contractor(double hourlyRate, int hours) {
        this.hourlyRate = hourlyRate;
        this.hours = hours;
    }

    @Override
    public double monthlyPay() {
        return hourlyRate * hours;
    }
}
~~~

Interface cũng có thể có `default` method với phần triển khai mặc định, `static` method thuộc interface và `private` method hỗ trợ tái sử dụng logic nội bộ của các method trong interface. Tuy nhiên, interface không dùng để chứa trạng thái riêng cho từng instance. Các trường khai báo trong interface luôn là hằng số `public static final` theo ngữ nghĩa của Java.

Một class có thể triển khai nhiều interface:

~~~java
final class Invoice implements Payable, Comparable<Invoice> {
    // Cung cấp các phương thức cần thiết của cả hai interface.
}
~~~

Đoạn trên chỉ minh họa cú pháp và không phải class hoàn chỉnh, vì một class thật cần khai báo dữ liệu và triển khai mọi phương thức trừu tượng còn thiếu. Điểm quan trọng là Java cho phép một class thực hiện nhiều hợp đồng interface, trong khi class chỉ có một lớp cha trực tiếp.

**Nên chọn interface khi** cần công bố một hợp đồng ổn định, muốn nhiều loại class độc lập có thể cung cấp cùng khả năng, hoặc muốn dễ thay thế implementation trong kiểm thử và cấu hình ứng dụng.

## 3. Abstract class: lớp cơ sở có thể chia sẻ trạng thái và hành vi

Abstract class được khai báo bằng từ khóa `abstract`. Nó không thể được khởi tạo trực tiếp, nhưng có thể chứa constructor, trường instance, phương thức đã triển khai và phương thức trừu tượng. Class con cụ thể kế thừa nó phải triển khai các phương thức trừu tượng còn thiếu.

~~~java
abstract class Employee {
    private final String name;

    protected Employee(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public abstract String role();

    public String summary() {
        return name + " (" + role() + ")";
    }
}
~~~

Trong ví dụ này, abstract class giữ tên nhân viên và triển khai hành vi dùng chung là `summary()`. Phương thức `role()` được để trừu tượng vì mỗi loại nhân viên có thể có vai trò khác nhau. Constructor giúp bảo đảm các lớp con khởi tạo phần trạng thái chung theo cùng quy tắc.

Class con có thể kế thừa phần dùng chung và hoàn thiện phần còn thiếu:

~~~java
final class SalariedEmployee extends Employee {
    private final double salary;

    SalariedEmployee(String name, double salary) {
        super(name);
        this.salary = salary;
    }

    @Override
    public String role() {
        return "Engineer";
    }

    public double salary() {
        return salary;
    }
}
~~~

`SalariedEmployee` là concrete class vì đã triển khai `role()` và không còn phương thức trừu tượng chưa xử lý. Có thể tạo đối tượng `SalariedEmployee`, nhưng không thể viết `new Employee(...)` khi `Employee` vẫn là abstract class.

Abstract class cũng có thể triển khai interface. Ví dụ, nếu `Employee` implements `Payable`, nó có thể để `monthlyPay()` trừu tượng để từng lớp con tự tính lương. Khi đó abstract class vừa cung cấp trạng thái chung, vừa làm lớp cầu nối giữa hợp đồng interface và các triển khai cụ thể.

**Nên chọn abstract class khi** các lớp có mối quan hệ kế thừa hợp lý và cần chia sẻ trạng thái, constructor, quy tắc bất biến hoặc hành vi chung. Không nên tạo abstract class chỉ để gom các phương thức không liên quan; interface thường thích hợp hơn cho một khả năng độc lập.

## 4. Concrete class: kiểu có thể tạo đối tượng

Concrete class là class không được khai báo `abstract` và đã triển khai mọi phương thức trừu tượng bắt buộc. Nếu class có constructor truy cập được, chương trình có thể tạo instance của nó bằng `new`.

Concrete class không có nghĩa là class đó bắt buộc phải khai báo `final`. Một concrete class vẫn có thể được kế thừa nếu không có modifier hoặc ràng buộc khác cấm kế thừa. `final class` là một concrete class không cho phép class khác kế thừa.

~~~java
final class FixedContractor implements Payable {
    private final String name;
    private final double monthlyAmount;

    FixedContractor(String name, double monthlyAmount) {
        this.name = name;
        this.monthlyAmount = monthlyAmount;
    }

    public String name() {
        return name;
    }

    @Override
    public double monthlyPay() {
        return monthlyAmount;
    }
}
~~~

Class này không kế thừa abstract class nào nhưng vẫn triển khai interface `Payable` và có thể được khởi tạo. Nó cũng minh họa rằng các class không liên quan trong cây kế thừa vẫn có thể được xử lý thống nhất thông qua interface.

Một concrete class có thể đồng thời kế thừa abstract class và triển khai interface:

~~~java
final class SalariedEmployee extends Employee implements Payable {
    // Hoàn thiện các phương thức trừu tượng của Employee và Payable.
}
~~~

Đây là minh họa cấu trúc; trong chương trình hoàn chỉnh, nếu cả hai kiểu yêu cầu cùng một chữ ký phương thức thì một implementation phù hợp có thể đáp ứng cả hai hợp đồng. Nếu các hợp đồng có chữ ký hoặc kiểu trả về xung đột, cần điều chỉnh thiết kế để tuân thủ quy tắc tương thích của Java.

## 5. Ví dụ hoàn chỉnh chạy được trên Java 17

Chương trình dưới đây dùng cả ba khái niệm. `Payable` định nghĩa hợp đồng tính lương; `Employee` là abstract class chứa tên và hành vi dùng chung; `SalariedEmployee` là concrete class kế thừa nền tảng đó. `Contractor` là concrete class độc lập chỉ triển khai interface, cho thấy các kiểu không cùng cây kế thừa vẫn có thể dùng chung một hợp đồng.

Lưu toàn bộ mã vào file `TypeComparisonDemo.java`:

~~~java
public class TypeComparisonDemo {
    public static void main(String[] args) {
        Employee employee = new SalariedEmployee("Lan", 3000.0);
        Payable[] payables = {
            employee,
            new Contractor("Minh", 30.0, 40)
        };

        System.out.println("EMPLOYEE: " + employee.summary());

        for (Payable payable : payables) {
            System.out.printf(java.util.Locale.ROOT,
                    "PAY: %.2f%n", payable.monthlyPay());
        }
    }
}

interface Payable {
    double monthlyPay();
}

abstract class Employee implements Payable {
    private final String name;

    protected Employee(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public abstract String role();

    public String summary() {
        return name + " (" + role() + ")";
    }
}

final class SalariedEmployee extends Employee {
    private final double salary;

    SalariedEmployee(String name, double salary) {
        super(name);
        this.salary = salary;
    }

    @Override
    public String role() {
        return "Engineer";
    }

    @Override
    public double monthlyPay() {
        return salary;
    }
}

final class Contractor implements Payable {
    private final String name;
    private final double hourlyRate;
    private final int hours;

    Contractor(String name, double hourlyRate, int hours) {
        this.name = name;
        this.hourlyRate = hourlyRate;
        this.hours = hours;
    }

    @Override
    public double monthlyPay() {
        return hourlyRate * hours;
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 TypeComparisonDemo.java
java TypeComparisonDemo
~~~

Stdout chính xác:

~~~text
EMPLOYEE: Lan (Engineer)
PAY: 3000.00
PAY: 1200.00
~~~

Trong ví dụ, biến `employee` có kiểu tham chiếu `Employee` nhưng giữ một đối tượng `SalariedEmployee`. Mảng `payables` có kiểu phần tử `Payable`, nên có thể chứa cả nhân viên hưởng lương lẫn nhà thầu. Khi gọi `monthlyPay()`, Java thực thi implementation tương ứng với đối tượng thực tế. Đây là đa hình: mã gọi dựa trên hợp đồng chung trong khi hành vi cụ thể được chọn theo kiểu đối tượng lúc chạy.

## 6. Quan hệ giữa ba khái niệm

Có thể hình dung cấu trúc của ví dụ như sau:

~~~text
Payable (interface)
    ^                  ^
    | implements       | implements
Employee (abstract)   Contractor (concrete)
    ^
    | extends
SalariedEmployee (concrete)
~~~

Mũi tên biểu diễn quan hệ kiểu: `Employee` triển khai `Payable`; `Contractor` cũng triển khai `Payable`; `SalariedEmployee` kế thừa `Employee`. Nhờ vậy, lớp gọi có thể chọn mức trừu tượng phù hợp với nhu cầu:

- Dùng `Payable` nếu chỉ cần gọi `monthlyPay()`.
- Dùng `Employee` nếu cần các hành vi và trạng thái chung của nhân viên như `name()` hoặc `summary()`.
- Dùng `SalariedEmployee` nếu cần hành vi đặc thù của nhân viên hưởng lương như `salary()`, hoặc khi cần tạo đối tượng cụ thể đó.

Nên khai báo biến và tham số bằng kiểu trừu tượng phù hợp nhất với hành vi mà đoạn mã cần. Việc này giảm phụ thuộc vào implementation cụ thể và giúp thay thế đối tượng dễ dàng hơn.

## 7. Những khác biệt trong thiết kế cần hiểu rõ

**Kế thừa class là đơn kế thừa.** Một class Java chỉ có thể có một lớp cha trực tiếp, dù lớp cha đó là abstract hay concrete. Class có thể triển khai nhiều interface. Vì vậy, abstract class phù hợp cho nền tảng chung trong một cây kế thừa, còn interface phù hợp cho các khả năng có thể xuất hiện ở nhiều cây class khác nhau.

**Constructor thuộc class.** Interface không có constructor và không được khởi tạo. Abstract class có constructor, nhưng constructor chỉ được gọi thông qua quá trình khởi tạo lớp con. Concrete class có thể cung cấp constructor để tạo đối tượng.

**Phương thức trừu tượng phải được hoàn thiện.** Một class con vẫn có thể là abstract nếu chưa triển khai tất cả phương thức trừu tượng. Khi muốn tạo instance, cần một concrete class đã đáp ứng đầy đủ các phương thức trừu tượng kế thừa.

**Interface không thay thế mọi nhu cầu của abstract class.** Interface có thể có default method, nhưng không thể chứa trạng thái instance hoặc constructor. Nếu nhiều lớp cần chung một trường dữ liệu cùng quy tắc khởi tạo, abstract class có thể biểu đạt mô hình đó trực tiếp hơn.

**Abstract class không thay thế mọi nhu cầu của interface.** Nếu nhiều class không có quan hệ kế thừa tự nhiên nhưng cần cung cấp cùng khả năng, buộc chúng vào một lớp cha chỉ để chia sẻ tên phương thức sẽ làm cây kế thừa cứng nhắc. Interface thể hiện hợp đồng đó mà không áp đặt một lớp cha chung.

**Concrete class vẫn có thể phụ thuộc vào abstraction.** Dù class được tạo instance trực tiếp, nó có thể triển khai interface và được sử dụng qua kiểu interface. “Concrete” mô tả khả năng khởi tạo của class, không nói rằng class phải được tham chiếu trực tiếp ở mọi nơi.

## 8. Lỗi thường gặp

- **Cố tạo đối tượng từ interface hoặc abstract class:** `new Payable()` và `new Employee(...)` không hợp lệ. Hãy tạo một concrete class triển khai hoặc kế thừa kiểu đó.
- **Quên triển khai phương thức bắt buộc:** concrete class phải triển khai các phương thức trừu tượng còn thiếu. Nếu chưa sẵn sàng, class cần được khai báo `abstract`.
- **Dùng abstract class chỉ để định nghĩa hợp đồng:** nếu không cần trạng thái hoặc hành vi dùng chung, interface thường linh hoạt hơn.
- **Dùng interface để lưu trạng thái instance:** trường interface là hằng số tĩnh, không phải dữ liệu riêng cho từng đối tượng. Hãy đặt trạng thái instance trong class.
- **Nhầm concrete với final:** concrete class có thể được kế thừa; chỉ `final` mới cấm kế thừa class đó.
- **Dùng kiểu cụ thể ở mọi tham số:** điều này có thể gắn mã gọi với một implementation. Nếu chỉ cần hành vi chung, hãy nhận interface hoặc abstract class thích hợp.
- **Tạo cây kế thừa quá sâu:** abstract class nên phản ánh quan hệ “là một” và có hành vi chung thực sự. Với khả năng độc lập, composition và interface thường tạo thiết kế linh hoạt hơn.
- **Quên `@Override`:** chú thích này giúp compiler phát hiện lỗi khi tên hoặc chữ ký phương thức không khớp với phương thức được triển khai.

## 9. Cách chọn trong tình huống thực tế

Chọn **interface** khi bạn muốn định nghĩa một hợp đồng mà nhiều class có thể triển khai, chẳng hạn `Payable`, `Runnable` hoặc `Comparable`. Hợp đồng giúp mã gọi không phụ thuộc vào một loại đối tượng cụ thể.

Chọn **abstract class** khi các kiểu con thuộc cùng một họ khái niệm và cần chia sẻ trạng thái, constructor, hành vi hoặc các quy tắc chung. Để các phương thức trừu tượng dành cho những phần mà từng kiểu con phải tự định nghĩa.

Chọn **concrete class** khi bạn cần một triển khai có thể tạo đối tượng để thực thi nghiệp vụ. Concrete class có thể đứng độc lập, kế thừa abstract class hoặc triển khai một hay nhiều interface.

Một quy trình thiết kế hữu ích là: xác định hành vi mà bên gọi cần, định nghĩa interface nếu cần một hợp đồng có thể thay thế; thêm abstract class nếu có phần trạng thái và logic chung đáng chia sẻ; rồi cung cấp một hoặc nhiều concrete class để thực thi. Không cần tạo đủ cả ba tầng nếu bài toán không có nhu cầu thực tế.

## 10. Checklist tự kiểm tra

- Bạn có thể giải thích interface công bố hành vi nào mà không nói đến implementation cụ thể không?
- Abstract class có thật sự chứa trạng thái hoặc hành vi chung đáng chia sẻ không?
- Concrete class đã triển khai mọi phương thức trừu tượng và có constructor phù hợp chưa?
- Mã gọi có thể dùng interface hoặc abstract class thay vì phụ thuộc vào class cụ thể không?
- Bạn đã phân biệt quan hệ `extends` với `implements` và nhớ rằng class chỉ có một lớp cha trực tiếp chưa?
- Ví dụ đã biên dịch bằng phiên bản Java mục tiêu và kết quả stdout có khớp tài liệu không?

Khi hiểu rõ các vai trò này, bạn có thể thiết kế API Java dễ mở rộng hơn: interface xác định hợp đồng, abstract class cung cấp phần nền tảng dùng chung khi cần, và concrete class biến hợp đồng cùng nền tảng đó thành đối tượng thực thi được.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-002">Bảng modifier hợp lệ theo loại thành viên</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng modifier hợp lệ theo loại thành viên

## Mục tiêu

Java chỉ cho phép một modifier xuất hiện ở những loại khai báo mà ngôn ngữ quy định. Chẳng hạn, trường trong class có thể là private, static, final, transient hoặc volatile; phương thức có thể là abstract, synchronized hoặc native; còn default là modifier dành cho phương thức instance có thân trong interface. Chọn đúng modifier giúp thể hiện quyền truy cập, khả năng kế thừa và cách một thành viên hoạt động.

Bài này dùng quy tắc Java 17 trở lên để tra cứu modifier theo loại khai báo, hiểu modifier ngầm định trong interface, nhận diện các tổ hợp không hợp lệ và đọc lỗi compiler theo đúng nguyên nhân.

## 1. Access modifier và modifier về hành vi

Bốn access level cần phân biệt:

| Modifier | Phạm vi truy cập |
|---|---|
| public | Truy cập từ nơi nhìn thấy được kiểu chứa thành viên và thỏa quy tắc module. |
| protected | Trong cùng package và theo quy tắc kế thừa ở package khác. |
| Không ghi modifier truy cập | Package-private: chỉ trong cùng package. |
| private | Giới hạn trong phạm vi lớp hoặc kiểu bao quanh được Java cho phép. |

Không phải loại khai báo nào cũng dùng được cả bốn access level. Kiểu top-level chỉ có thể là public hoặc package-private; top-level class/interface không thể private hay protected. Constructor và thành viên class có thể dùng public, protected, private hoặc package-private. Thành viên interface có quy tắc riêng: trường và phương thức có modifier ngầm định, còn kiểu thành viên được khai báo trong interface là public static.

Những modifier khác quy định hành vi:

- abstract: để lại phần triển khai cho lớp con hoặc lớp triển khai.
- final: ngăn kế thừa class, ngăn ghi đè phương thức, hoặc ngăn gán lại biến sau khi đã được gán.
- static: thành viên thuộc về kiểu thay vì từng đối tượng; static method được che khuất (hide), không được override theo cơ chế đa hình của instance method.
- sealed và non-sealed: kiểm soát nhánh kế thừa của class hoặc interface.
- transient và volatile: chỉ áp dụng cho trường, nhưng có ý nghĩa khác nhau về tuần tự hóa và truy cập biến.
- synchronized và native: modifier của phương thức.
- default: khai báo hành vi mặc định cho phương thức instance trong interface.
- strictfp: có thể dùng với class, interface và phương thức; từ Java 17, biểu thức dấu phẩy động đã strict theo mặc định nên modifier này thường không cần thiết.

## 2. Bảng modifier theo loại khai báo

| Loại khai báo | Modifier thường được phép | Ghi nhớ |
|---|---|---|
| Top-level class | public, abstract, final, sealed, non-sealed, strictfp; hoặc package-private | Không dùng private, protected hay static. abstract và final loại trừ nhau. |
| Top-level interface | public, abstract, sealed, non-sealed, strictfp; hoặc package-private | Interface ngầm định abstract; không dùng private, protected, final hay static ở cấp top-level. |
| Trường trong class | public, protected, private, static, final, transient, volatile | Chỉ một access modifier; final không thể kết hợp volatile. |
| Phương thức trong class | public, protected, private, abstract, static, final, synchronized, native, strictfp | abstract không thể kết hợp với private, static, final, native, synchronized hoặc strictfp. |
| Constructor | public, protected, private; hoặc package-private | Không dùng static, abstract, final, default, synchronized hay native. |
| Trường trong interface | public, static, final ngầm định | Không dùng private, protected, volatile hoặc transient. |
| Phương thức abstract trong interface | public, abstract ngầm định | Không có thân; không dùng protected, final, synchronized hoặc native. |
| Phương thức default trong interface | default; public ngầm định | Là phương thức instance có thân; không thể đồng thời abstract, static hoặc private. |
| Phương thức static trong interface | static; public ngầm định hoặc private static | Gọi phương thức public static bằng tên interface; helper private chỉ dùng bên trong interface. |
| Phương thức private trong interface | private hoặc private static | Có từ Java 9; phải có thân. |
| Kiểu thành viên trong class | public, protected, private hoặc package-private; static với member class; thêm modifier loại class khi phù hợp | Interface, enum và record lồng nhau là static ngầm định. |
| Kiểu thành viên trong interface | public static ngầm định | Không khai báo kiểu thành viên là private hoặc protected. |
| Biến cục bộ hoặc tham số | final hoặc không có modifier | Không dùng public, private, protected, static, volatile hay transient. Biến effectively final cũng có thể được lambda bắt giữ. |

Bảng này là bản tra cứu nhanh. Một khai báo cụ thể vẫn phải thỏa cả loại thành viên, kiểu bao quanh, vị trí top-level/nested và các tổ hợp loại trừ lẫn nhau.

## 3. Modifier của class và interface

Top-level class có thể là public hoặc package-private, và có thể dùng abstract, final, sealed, non-sealed hoặc strictfp khi thỏa các quy tắc liên quan. Không thể kết hợp abstract với final: abstract dành cho lớp có thể được mở rộng để hoàn thiện phần trừu tượng, còn final cấm kế thừa.

~~~java
public abstract class Task {
    public abstract void run();
}

final class OneShotTask {
    void runOnce() {
        System.out.println("done");
    }
}
~~~

Interface top-level cũng chỉ có thể là public hoặc package-private. Interface ngầm định abstract; ghi rõ abstract là dư thừa nhưng hợp lệ. Interface không thể final vì nó được triển khai bởi class khác, và không thể khai báo static ở cấp top-level.

~~~java
public sealed interface Command permits StartCommand, StopCommand {
    void execute();
}

final class StartCommand implements Command {
    @Override
    public void execute() {
        System.out.println("start");
    }
}

final class StopCommand implements Command {
    @Override
    public void execute() {
        System.out.println("stop");
    }
}
~~~

Ví dụ minh họa sealed hierarchy. Nếu đặt chung trong một tệp nguồn, chỉ một kiểu top-level có thể là public và tên tệp phải khớp với kiểu public. Command giới hạn các kiểu con trực tiếp bằng permits; mỗi kiểu con trực tiếp phải là final, sealed hoặc non-sealed. Modifier non-sealed chỉ hợp lệ khi kiểu đó trực tiếp mở rộng một sealed class hoặc triển khai một sealed interface.

## 4. Modifier của trường

Trường trong class có thể kết hợp access modifier với static, final, transient hoặc volatile nếu tổ hợp cụ thể hợp lệ.

~~~java
class CacheEntry {
    public static final int MAX_SIZE = 100;
    private transient String serializedAway;
    protected volatile boolean refreshNeeded;
    private final String key;

    CacheEntry(String key) {
        this.key = key;
    }
}
~~~

- static final thường dùng cho hằng số thuộc về class. Để là hằng compile-time, giá trị phải là hằng biểu thức phù hợp và kiểu phải là kiểu nguyên thủy hoặc String.
- transient đánh dấu trường không được lưu như một phần trạng thái mặc định khi dùng Java Serialization.
- volatile cung cấp visibility và ordering theo mô hình bộ nhớ Java. Nó không làm cho một chuỗi thao tác nhiều bước trở thành atomic.
- final trên trường yêu cầu gán một lần theo quy tắc definite assignment. Với trường tham chiếu, final khóa việc gán lại tham chiếu chứ không làm đối tượng được tham chiếu tự động bất biến.

Tổ hợp final volatile không hợp lệ. Trường interface luôn là public static final, kể cả khi không viết ba modifier này ra. Do đó, không thể dùng private, protected, volatile hoặc transient để thay đổi cách khai báo trường interface.

## 5. Modifier của phương thức trong class

Các modifier thường gặp là access modifier, abstract, static, final, synchronized, native và strictfp.

- abstract khai báo phương thức không có thân; class chứa phương thức abstract phải là abstract.
- final ngăn lớp con ghi đè phương thức instance.
- static thuộc về class; nó không dùng dispatch đa hình dựa trên kiểu runtime như instance method.
- synchronized đồng bộ thực thi theo monitor của đối tượng đối với instance method, hoặc monitor của class đối với static method.
- native cho biết phần triển khai được cung cấp bên ngoài Java và khai báo không có thân Java.
- strictfp thường không còn cần trong Java 17 vì biểu thức dấu phẩy động đã strict theo mặc định.

Một phương thức abstract không thể đồng thời private, static, final, native, synchronized hoặc strictfp. Những modifier này mâu thuẫn với việc phương thức không có thân cần được triển khai ở lớp con, hoặc bị ngôn ngữ cấm trực tiếp.

~~~java
abstract class Processor {
    protected abstract String transform(String input);

    public final String process(String input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        return transform(input);
    }

    public static String version() {
        return "1.0";
    }

    protected synchronized void record() {
        // Cập nhật trạng thái dùng chung một cách đồng bộ.
    }
}
~~~

process là final để giữ nguyên bước kiểm tra đầu vào và cách gọi transform. transform là abstract để lớp con cung cấp cách biến đổi. version là static nên gọi bằng Processor.version(); record là synchronized để đồng bộ thân phương thức trên monitor tương ứng.

## 6. Modifier của phương thức interface

Nếu không phải private, default hoặc static, phương thức interface thông thường là public abstract ngầm định. Interface không cho phép phương thức protected, final, synchronized hoặc native.

~~~java
interface Formatter {
    String format(String input); // public abstract ngầm định

    default String label(String input) {
        return normalize(input);
    }

    static String kind() {
        return "text";
    }

    private String normalize(String input) {
        return input == null ? "" : input.trim();
    }

    private static boolean missing(String input) {
        return input == null || input.isBlank();
    }

    static boolean accepts(String input) {
        return !missing(input);
    }
}
~~~

- format không có thân nên ngầm định public abstract.
- label là phương thức instance có thân; default cho phép lớp triển khai kế thừa hành vi mặc định.
- kind là static và được gọi bằng Formatter.kind(). Phương thức static trong interface không phải phương thức instance được lớp triển khai override.
- normalize là private instance helper; missing là private static helper. Cả hai dạng private method trong interface được hỗ trợ từ Java 9.
- accepts là public static ngầm định vì có static mà không có access modifier private.

Từ khóa default dành cho phương thức instance có thân trong interface. Nó không phải modifier hợp lệ cho phương thức thông thường trong class. Cũng đừng nhầm default method với cú pháp default value của phần tử annotation, nơi từ khóa default dùng theo ngữ pháp riêng.

## 7. Constructor và kiểu thành viên lồng nhau

Constructor chỉ có thể dùng public, protected, private hoặc package-private. Nó không có kiểu trả về và không phải phương thức instance bình thường, vì vậy không dùng static, abstract hay final. Constructor private thường dùng để giới hạn việc tạo đối tượng từ bên ngoài; constructor protected có thể hữu ích cho lớp cơ sở dùng trong kế thừa.

~~~java
public class Factory {
    private Factory() {
    }

    public static Factory create() {
        return new Factory();
    }

    public static abstract class Worker {
        protected abstract void execute();
    }
}
~~~

Factory có constructor private, còn Worker là member class public static abstract. Kiểu thành viên trong class có thể dùng access modifier; member class có thể là static hoặc inner class. Với member interface, enum và record, Java áp dụng các quy tắc static ngầm định và hạn chế riêng của từng loại.

Kiểu thành viên trong interface luôn là public static ngầm định; không thể dùng private hoặc protected để thu hẹp quyền truy cập của kiểu thành viên đó. Record ngầm định final, nên không thêm abstract, sealed hoặc non-sealed vào record.

## 8. Các tổ hợp sai thường gặp

### Access modifier không được phép ở top-level

~~~java
private class Hidden {
}
~~~

Không biên dịch vì top-level class không được private. Nếu cần giới hạn truy cập theo package, bỏ access modifier; nếu cần kiểu chỉ dùng bên trong kiểu khác, khai báo nested class và chọn access modifier phù hợp.

### Kết hợp abstract và final

~~~java
abstract final class Impossible {
}
~~~

Không hợp lệ vì abstract yêu cầu có thể tạo lớp con để hoàn thiện phần trừu tượng, còn final cấm mọi lớp con. Chọn một ý định: abstract cho lớp cơ sở mở rộng hoặc final cho lớp không cho kế thừa.

### Dùng protected trong interface

~~~java
interface BadApi {
    protected void execute();
}
~~~

Không hợp lệ vì phương thức interface không thể protected. Nếu đây là phương thức hợp đồng mà lớp triển khai phải cung cấp, khai báo void execute(); và để nó ngầm định public abstract. Nếu cần helper chỉ dùng bên trong interface, dùng private method có thân.

### Dùng default trong class

~~~java
class OrdinaryClass {
    default void run() {
    }
}
~~~

Không hợp lệ vì default modifier dành cho phương thức instance trong interface. Trong class, dùng access modifier thích hợp và cung cấp thân phương thức bình thường.

### Kết hợp final và volatile cho trường

~~~java
class BadState {
    final volatile int count = 0;
}
~~~

Không hợp lệ vì final ngăn gán lại sau khi khởi tạo, còn volatile áp dụng quy tắc đọc/ghi biến volatile; Java cấm kết hợp hai modifier này trên cùng một trường.

### Khai báo phương thức abstract private

~~~java
abstract class BadProcessor {
    private abstract void process();
}
~~~

Không hợp lệ vì private method không thể được ghi đè để lớp con cung cấp phần triển khai, trái với mục đích của abstract method. Nếu lớp con cần triển khai, dùng protected hoặc public abstract tùy hợp đồng truy cập.

## 9. Ví dụ Java 17 có thể biên dịch và chạy

Lưu toàn bộ mã sau vào tệp ModifierMatrixDemo.java. Ví dụ dùng trường, phương thức class, một lớp trừu tượng lồng nhau và interface có default/static/private methods để minh họa nhiều loại modifier hợp lệ.

~~~java
public class ModifierMatrixDemo {
    protected static final int LIMIT = 10;
    private volatile boolean dirty;
    private transient String cache;

    public synchronized void markDirty() {
        dirty = true;
    }

    protected final int limit() {
        return LIMIT;
    }

    public abstract static class Worker {
        protected abstract String executeTask(String input);

        public final String execute(String input) {
            if (input == null || input.isBlank()) {
                throw new IllegalArgumentException("input must not be blank");
            }
            return executeTask(input);
        }
    }

    public interface Plugin {
        int API_VERSION = 1;

        default String name() {
            return normalize(" plugin ");
        }

        static int apiVersion() {
            return API_VERSION;
        }

        static boolean supported(String candidate) {
            return !isBlank(candidate);
        }

        private String normalize(String value) {
            return value.trim();
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    private static final class UppercaseWorker extends Worker {
        @Override
        protected String executeTask(String input) {
            return input.toUpperCase(java.util.Locale.ROOT);
        }
    }

    private static final class DemoPlugin implements Plugin {
    }

    public static void main(String[] args) {
        Worker worker = new UppercaseWorker();
        System.out.println(worker.execute("java"));

        Plugin plugin = new DemoPlugin();
        System.out.println(plugin.name());
        System.out.println(Plugin.apiVersion());
        System.out.println(Plugin.supported("demo"));
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~text
javac ModifierMatrixDemo.java
java ModifierMatrixDemo
~~~

Kết quả stdout chính xác:

~~~text
JAVA
plugin
1
true
~~~

Ví dụ cho thấy protected static final là tổ hợp trường hợp lệ; volatile dùng cho trạng thái mutable; synchronized là modifier phương thức; final giữ nguyên phương thức limit và execute khỏi bị ghi đè; abstract bắt lớp con triển khai executeTask. Trong Plugin, API_VERSION ngầm định public static final, name là default method, apiVersion và supported là static method, còn normalize và isBlank là helper private. Các lớp lồng nhau dùng private static final hoặc public static abstract theo mục đích của chúng.

## 10. Cách đọc lỗi compiler và checklist

Khi compiler báo modifier không hợp lệ, hãy kiểm tra theo trình tự:

1. Loại khai báo: top-level type, nested type, field, method, constructor hay local variable?
2. Access level: loại khai báo này có cho phép public, protected hoặc private không?
3. Modifier theo loại: transient chỉ dành cho field, synchronized dành cho method, sealed/non-sealed dành cho class hoặc interface.
4. Tổ hợp xung đột: abstract với final/private/static/native/synchronized; final với volatile; hoặc default trong class thông thường.
5. Ngữ cảnh kế thừa: sealed/non-sealed chỉ hợp lệ khi quan hệ với sealed parent thỏa quy tắc; abstract method phải được lớp concrete triển khai.
6. Modifier ngầm định: field interface đã public static final; phương thức interface thông thường đã public abstract; member type trong interface đã public static.
7. Phiên bản Java: private interface methods cần Java 9 trở lên; ví dụ bài này nhắm Java 17+.

Không nên thêm modifier chỉ vì nó xuất hiện ở một khai báo khác gần giống. Java phân biệt loại khai báo và ngữ cảnh: modifier hợp lệ cho field có thể sai với method; modifier hợp lệ cho member class có thể sai với top-level class; và modifier của interface không luôn giống modifier của class.

## Kết luận

Bảng modifier chỉ hữu ích khi đọc cùng loại khai báo và ngữ cảnh chứa nó. Hãy xác định loại thành viên, kiểm tra access level, sau đó kiểm tra modifier hành vi và các tổ hợp xung đột. Hiểu modifier ngầm định của interface, khác biệt giữa phương thức class và interface, cùng quy tắc sealed hierarchy giúp viết API Java đúng cú pháp, dễ đọc và có chủ đích hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-003">Bảng quy tắc ưu tiên và xử lý xung đột phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng quy tắc ưu tiên và xử lý xung đột phương thức trong Java

## Mục tiêu

Khi Java tìm một phương thức, có hai câu hỏi cần tách riêng: trình biên dịch chọn **chữ ký nào** trong các overload có thể gọi, và khi chương trình chạy thì **phần triển khai nào** được thực thi do kế thừa hoặc ghi đè. Với interface, còn cần xử lý các phương thức `default` được kế thừa từ nhiều interface.

Bài này tổng hợp quy tắc ưu tiên thường gặp, cách đọc lỗi biên dịch do xung đột và cách giải quyết rõ ràng trong Java 17+.

## 1. Phân biệt overload, override và default method

- **Overload (nạp chồng):** nhiều phương thức cùng tên nhưng khác danh sách tham số. Trình biên dịch chọn overload phù hợp dựa trên kiểu khai báo của biểu thức và các quy tắc chuyển đổi kiểu.
- **Override (ghi đè):** lớp con cung cấp cách triển khai cho phương thức instance có thể ghi đè được của lớp cha hoặc interface. Khi gọi qua một tham chiếu, triển khai được chọn theo kiểu thực tế của đối tượng tại thời gian chạy.
- **Default method:** phương thức instance có phần thân được khai báo trong interface bằng từ khóa `default`. Lớp triển khai có thể kế thừa phương thức này nếu không có quy tắc ưu tiên hoặc xung đột buộc lớp phải cung cấp triển khai khác.
- **Phương thức abstract:** khai báo hợp đồng mà không có phần thân. Một lớp cụ thể phải đáp ứng tất cả phương thức abstract còn thiếu; một lớp abstract có thể tiếp tục để lại chúng cho lớp con.

Các khái niệm này phối hợp với nhau nhưng không thay thế nhau. Java trước hết phải xác định lời gọi phù hợp theo kiểu và chữ ký ở thời điểm biên dịch; sau đó, với phương thức instance có tính đa hình, JVM gọi triển khai override thích hợp trên đối tượng thực tế.

## 2. Bảng quy tắc ưu tiên khi kế thừa phương thức

| Tình huống | Quy tắc | Cách xử lý |
|---|---|---|
| Lớp có phương thức instance phù hợp | Phương thức của lớp được ưu tiên so với default method của interface | Dùng phương thức lớp; override trong lớp nếu cần hành vi khác |
| Lớp cha có phương thức instance phù hợp | Phương thức kế thừa từ lớp cha thắng default method của interface | Không cần triển khai lại nếu phương thức lớp cha đáp ứng hợp đồng |
| Lớp cha khai báo phương thức abstract cùng chữ ký | Khai báo trong hệ thống lớp vẫn có hiệu lực; default của interface không tự thay thế hợp đồng abstract đó | Lớp cụ thể phải override và cung cấp thân phương thức |
| Interface con ghi đè default của interface cha | Default từ interface con cụ thể hơn được chọn | Kế thừa default của interface con hoặc override trong lớp |
| Hai interface không có quan hệ cha con cùng cung cấp default trùng chữ ký | Không có default nào tự động thắng interface còn lại | Lớp triển khai phải override và giải quyết xung đột |
| Nhiều interface chỉ khai báo cùng một phương thức abstract với kiểu trả về tương thích | Một triển khai phù hợp có thể đáp ứng tất cả hợp đồng đó | Cung cấp một phương thức công khai |
| Một default và một abstract cùng chữ ký từ hai interface độc lập | Lớp triển khai không tự động chọn default; compiler yêu cầu lớp giải quyết xung đột | Override bằng phương thức public tương thích; nếu phù hợp, gọi default qua `InterfaceName.super.method()` |
| Phương thức static của interface | Không được kế thừa và override như phương thức instance | Gọi bằng tên interface, ví dụ `SomeInterface.utility()` |

Bảng này nói về phương thức instance và hợp đồng Java. Chữ ký phải tương thích, mức truy cập không được giảm khi override, và kiểu trả về phải đáp ứng quy tắc override của Java. Nếu hai khai báo cùng tên và tham số có kiểu trả về không tương thích, không thể khắc phục chỉ bằng cách chọn một default; thiết kế phải được thay đổi để các hợp đồng tương thích.

## 3. Quy tắc chọn overload khi biên dịch

Với các phương thức overload cùng tên, Java chọn phương thức áp dụng được theo các giai đoạn của lời gọi phương thức:

1. **Chuyển đổi invocation nghiêm ngặt:** xét các chuyển đổi như khớp kiểu và widening primitive/reference, chưa dùng boxing hoặc unboxing.
2. **Chuyển đổi invocation linh hoạt:** nếu giai đoạn trước không có phương thức phù hợp, xét thêm các chuyển đổi cho phép boxing/unboxing.
3. **Varargs:** nếu vẫn chưa tìm được phương thức phù hợp, xét các phương thức có tham số biến độ dài.

Java chọn phương thức phù hợp cụ thể nhất trong giai đoạn đầu tiên có ứng viên hợp lệ. Đây là lý do một overload nhận kiểu tham chiếu rộng hơn có thể được chọn trước một overload cần boxing, tùy kiểu đối số và chữ ký. Nếu không có phương thức phù hợp hoặc có nhiều lựa chọn không thể phân định là cụ thể nhất, lời gọi bị lỗi biên dịch.

Ví dụ, nếu có `print(Object)` và `print(String)`, lời gọi `print("hi")` chọn `print(String)` vì `String` cụ thể hơn `Object`. Nếu có `print(String)` và `print(Integer)`, lời gọi `print(null)` mơ hồ vì cả hai kiểu tham chiếu đều nhận được `null` và không kiểu nào cụ thể hơn kiểu còn lại. Có thể giải quyết bằng cách ép kiểu rõ ràng, chẳng hạn `print((String) null)`, nếu đúng với ý định.

Overload được chọn ở thời điểm biên dịch theo kiểu biểu thức. Override của phương thức instance sau đó được phân giải theo đối tượng thực tế. Vì vậy, biến tham chiếu có kiểu lớp cha vẫn có thể gọi triển khai override trong lớp con, nhưng không thể gọi một overload chỉ tồn tại ở lớp con nếu overload đó không khả dụng qua kiểu khai báo của biến.

## 4. Xử lý xung đột default giữa các interface

Giả sử hai interface độc lập đều định nghĩa `default String route()`. Một lớp triển khai cả hai sẽ không thể để trình biên dịch tự chọn một cách tùy ý. Lớp phải override `route()`. Bên trong phương thức override, có thể gọi trực tiếp default của một interface cha trực tiếp bằng cú pháp `InterfaceName.super.method()`.

Ví dụ:

~~~java
interface FastRoute {
    default String route() {
        return "fast";
    }
}

interface SafeRoute {
    default String route() {
        return "safe";
    }
}

class ChosenRoute implements FastRoute, SafeRoute {
    @Override
    public String route() {
        return FastRoute.super.route() + "+" + SafeRoute.super.route();
    }
}
~~~

`ChosenRoute` định nghĩa hành vi rõ ràng bằng cách kết hợp hai default. Nếu chỉ muốn dùng một trong hai, phần thân có thể trả về `FastRoute.super.route()` hoặc `SafeRoute.super.route()`. Việc gọi qua `InterfaceName.super` chỉ hợp lệ trong ngữ cảnh kế thừa interface thích hợp; tên interface được dùng phải là interface trực tiếp mà lớp triển khai.

Nếu interface con mở rộng interface cha và cung cấp default cùng chữ ký, default ở interface con là phương thức cụ thể hơn. Nếu interface con chủ động khai báo lại phương thức đó dưới dạng abstract, nó có thể loại bỏ default kế thừa khỏi hợp đồng của interface con; lớp cụ thể triển khai interface con phải cung cấp phần thân phương thức.

### Default và abstract từ hai interface khác nhau

Không được áp dụng quy tắc “default luôn đáp ứng abstract” cho hai interface **độc lập**. Trong ví dụ sau, `NameResolver` phải tự override `label()` dù `DefaultName` đã có thân phương thức:

~~~java
interface AbstractName {
    String label();
}

interface DefaultName {
    default String label() {
        return "default";
    }
}

class NameResolver implements AbstractName, DefaultName {
    @Override
    public String label() {
        return DefaultName.super.label();
    }
}
~~~

Nếu bỏ override khỏi `NameResolver`, mã sẽ không biên dịch vì không giải quyết được phương thức trùng chữ ký. Trường hợp khác là **interface con** cung cấp default để override một khai báo abstract kế thừa; khi đó lớp cụ thể có thể dùng default đó:

~~~java
interface BaseName {
    String label();
}

interface ChildName extends BaseName {
    @Override
    default String label() {
        return "child";
    }
}

class UsesChildName implements ChildName {
}
~~~

Phân biệt quan hệ kế thừa của các interface trước khi kết luận rằng một default method đã đáp ứng hợp đồng abstract.

## 5. Phương thức abstract trùng nhau và kiểu trả về

Hai interface có thể khai báo cùng một phương thức abstract. Nếu chữ ký và kiểu trả về tương thích, một phương thức public trong lớp có thể triển khai cả hai hợp đồng:

~~~java
interface HasCode {
    String code();
}

interface HasIdentifier {
    String code();
}

class RecordCode implements HasCode, HasIdentifier {
    @Override
    public String code() {
        return "C-17";
    }
}
~~~

Không cần viết hai phương thức chỉ vì có hai interface. Khi gọi qua biến kiểu `HasCode` hoặc `HasIdentifier`, cả hai lời gọi đều chạy cùng triển khai `RecordCode.code()`.

Kiểu trả về cần được xem xét cẩn thận. Với phương thức instance override, kiểu trả về nguyên thủy phải khớp; kiểu tham chiếu có thể dùng kiểu con tương thích theo quy tắc covariant return. Nếu hai interface đòi hỏi kiểu trả về không thể cùng đáp ứng, lớp không thể tạo ra một triển khai hợp lệ bằng cách khai báo hai phương thức chỉ khác kiểu trả về, vì kiểu trả về không tạo thành overload riêng.

## 6. Ví dụ hoàn chỉnh chạy bằng Java 17+

Ví dụ sau minh họa bốn quy tắc: phương thức lớp cha ưu tiên default của interface; default của interface con ưu tiên default từ cha; xung đột giữa hai default độc lập phải được giải quyết; và một triển khai có thể đáp ứng hai hợp đồng abstract. Cuối cùng, ví dụ overload/override cho thấy sự khác nhau giữa lựa chọn ở thời điểm biên dịch và gọi triển khai lúc chạy.

Lưu toàn bộ mã vào `Main.java`:

~~~java
interface Named {
    default String label() {
        return "interface";
    }
}

class BaseLabel {
    public String label() {
        return "class";
    }
}

class ClassWins extends BaseLabel implements Named {
}

interface GeneralMode {
    default String mode() {
        return "general";
    }
}

interface SpecificMode extends GeneralMode {
    @Override
    default String mode() {
        return "specific";
    }
}

class MostSpecificWins implements SpecificMode {
}

interface FastRoute {
    default String route() {
        return "fast";
    }
}

interface SafeRoute {
    default String route() {
        return "safe";
    }
}

class ChosenRoute implements FastRoute, SafeRoute {
    @Override
    public String route() {
        return FastRoute.super.route() + "+" + SafeRoute.super.route();
    }
}

interface HasCode {
    String code();
}

interface HasIdentifier {
    String code();
}

class RecordCode implements HasCode, HasIdentifier {
    @Override
    public String code() {
        return "C-17";
    }
}

class Formatter {
    String show(Object value) {
        return "Object";
    }

    String show(String value) {
        return "String";
    }
}

class CustomFormatter extends Formatter {
    @Override
    String show(String value) {
        return "CustomString";
    }
}

public class Main {
    public static void main(String[] args) {
        System.out.println(new ClassWins().label());
        System.out.println(new MostSpecificWins().mode());
        System.out.println(new ChosenRoute().route());

        RecordCode record = new RecordCode();
        HasCode codeView = record;
        HasIdentifier idView = record;
        System.out.println(codeView.code());
        System.out.println(idView.code());

        Formatter formatter = new CustomFormatter();
        System.out.println(formatter.show("hello"));
        System.out.println(formatter.show((Object) "hello"));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~text
javac Main.java
java Main
~~~

Đầu ra chuẩn chính xác:

~~~text
class
specific
fast+safe
C-17
C-17
CustomString
Object
~~~

Giải thích kết quả:

1. `ClassWins.label()` kế thừa phương thức public từ `BaseLabel`; phương thức đó được ưu tiên so với default `Named.label()`.
2. `MostSpecificWins.mode()` dùng default `SpecificMode.mode()`, vì interface này mở rộng và ghi đè default của `GeneralMode`.
3. `ChosenRoute.route()` giải quyết xung đột bằng cách gọi tường minh cả hai default.
4. `RecordCode.code()` đáp ứng hai interface có cùng hợp đồng abstract, nên hai biến interface đều in `C-17`.
5. Với `formatter.show("hello")`, kiểu đối số là `String`, nên trình biên dịch chọn overload `show(String)`. Đối tượng thực tế là `CustomFormatter`, vì vậy triển khai override in `CustomString`.
6. Với `formatter.show((Object) "hello")`, kiểu biểu thức đối số là `Object`, nên overload `show(Object)` được chọn. `CustomFormatter` không override overload này, do đó phương thức kế thừa in `Object`.

## 7. Các trường hợp dễ nhầm

- **Nghĩ rằng interface nào được liệt kê trước trong `implements` sẽ thắng:** thứ tự liệt kê không quyết định cách chọn default. Xung đột giữa các default độc lập phải được giải quyết bằng override.
- **Nghĩ rằng mọi phương thức cùng tên đều là overload:** overload yêu cầu danh sách tham số khác nhau. Override thay thế triển khai của một phương thức kế thừa có chữ ký tương thích.
- **Nghĩ rằng kiểu thực tế của đối tượng quyết định overload:** kiểu thực tế quyết định triển khai override; overload được chọn dựa trên kiểu biểu thức và các phương thức có thể truy cập tại thời điểm biên dịch.
- **Quên default ở interface con:** default cụ thể hơn trong interface con thắng default kế thừa từ interface cha.
- **Cho rằng default luôn thay thế phương thức abstract của lớp cha:** một khai báo abstract được kế thừa từ lớp cha vẫn có thể buộc lớp cụ thể phải triển khai phương thức đó.
- **Cố dùng kiểu trả về để tạo overload:** các phương thức chỉ khác kiểu trả về không tạo thành overload hợp lệ.
- **Gọi static method qua đối tượng như thể đó là dispatch đa hình:** static method của interface cần được gọi qua tên interface và không tham gia cơ chế override instance.

## 8. Quy trình chẩn đoán khi gặp lỗi biên dịch

1. Xác định lời gọi đang vướng overload, override, default inheritance hay hợp đồng abstract.
2. Ghi lại kiểu khai báo của biến nhận lời gọi và kiểu của từng đối số; dùng chúng để kiểm tra overload có thể áp dụng.
3. Kiểm tra lớp cha trước, rồi xem các interface có cung cấp default cùng chữ ký hay không.
4. Với nhiều default, kiểm tra quan hệ cha con giữa các interface. Nếu các default cạnh tranh đến từ những interface độc lập, override và chọn hành vi mong muốn.
5. Kiểm tra mức truy cập, kiểu trả về, tham số và checked exception của phương thức override.
6. Biên dịch lại và chạy ví dụ tối thiểu để xác nhận kết quả. Khi cần, ép kiểu đối số tường minh để phân biệt overload nào đang được chọn.

## Ghi nhớ

Overload được phân giải ở thời điểm biên dịch; override được chọn theo đối tượng thực tế khi chạy. Trong kế thừa, phương thức instance từ hệ thống lớp được ưu tiên so với default của interface; default ở interface con thắng default từ cha; các default độc lập có cùng chữ ký cần được lớp triển khai giải quyết rõ ràng. Những hợp đồng abstract trùng nhau có thể dùng chung một triển khai nếu chữ ký và kiểu trả về tương thích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-004">Các hiểu lầm thường gặp và cách kiểm chứng</a>

<details>
<summary>Xem chi tiết</summary>

# Các hiểu lầm thường gặp về abstract class và interface trong Java, và cách kiểm chứng

Khi học Java, nhiều lỗi về thiết kế hướng đối tượng xuất phát từ những quy tắc được nhớ chưa đầy đủ: cho rằng lớp trừu tượng không có thân phương thức, interface chỉ chứa phương thức abstract, hoặc một lớp có thể tạo đối tượng trực tiếp từ bất kỳ kiểu tham chiếu nào. Cách học chắc chắn là phân biệt **quy tắc của ngôn ngữ**, **hành vi lúc chạy** và **quy ước thiết kế**, rồi kiểm chứng từng điều bằng compiler hoặc một chương trình nhỏ.

Bài này tập trung vào **abstract class**, **interface**, kế thừa, triển khai, phương thức **default**, phương thức **static** và đa hình.

## 1. Hiểu lầm: lớp abstract không thể có constructor hoặc phương thức đã triển khai

**Thực tế:** lớp abstract có thể có trường, constructor, phương thức cụ thể và phương thức abstract. Điều khiến lớp đó không thể được khởi tạo trực tiếp là khai báo abstract, không phải việc mọi phương thức đều thiếu thân.

Constructor của lớp abstract chạy khi một lớp con được khởi tạo. Nó thường thiết lập trạng thái chung và bảo đảm các bất biến của phần lớp cha. Phương thức cụ thể có thể chứa logic dùng chung, trong khi phương thức abstract để lớp con cung cấp phần khác biệt.

~~~java
abstract class BaseDocument {
    private final String title;

    protected BaseDocument(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    public abstract int pageCount();
}
~~~

Đây là đoạn minh họa, chưa phải chương trình độc lập. BaseDocument có constructor và phương thức cụ thể title(), đồng thời yêu cầu lớp con cung cấp pageCount().

Một lớp con cụ thể phải triển khai phương thức abstract còn thiếu:

~~~java
final class ShortDocument extends BaseDocument {
    ShortDocument(String title) {
        super(title);
    }

    @Override
    public int pageCount() {
        return 1;
    }
}
~~~

Nếu ShortDocument không triển khai pageCount(), lớp phải được khai báo abstract hoặc compiler sẽ báo lỗi. Annotation @Override giúp compiler xác nhận phương thức thực sự ghi đè hoặc triển khai một phương thức kế thừa.

## 2. Hiểu lầm: không thể khai báo biến có kiểu abstract class hoặc interface

**Thực tế:** không thể tạo đối tượng trực tiếp bằng new đối với interface hoặc lớp abstract, nhưng có thể khai báo biến, tham số, kiểu trả về và trường bằng các kiểu đó. Biến tham chiếu có thể trỏ tới một đối tượng của lớp cụ thể tương thích.

~~~java
// Không hợp lệ: không thể khởi tạo trực tiếp
// BaseDocument document = new BaseDocument("Draft");

// Hợp lệ nếu ShortDocument là lớp cụ thể:
BaseDocument document = new ShortDocument("Draft");
~~~

Dòng hợp lệ minh họa đa hình: kiểu khai báo của biến là BaseDocument, còn kiểu thực của đối tượng là ShortDocument. Khi gọi một phương thức instance được override, Java chọn phần triển khai dựa trên kiểu thực của đối tượng lúc chạy.

Không nên kết luận rằng một biến có kiểu interface chỉ có thể trỏ tới một interface. Interface là kiểu tham chiếu; đối tượng thực phải là instance của một lớp cụ thể triển khai interface đó.

## 3. Hiểu lầm: interface chỉ có các phương thức abstract

**Thực tế:** interface có thể khai báo các phương thức abstract, default, static và private (phương thức private trong interface có từ Java 9). Các dạng này phục vụ những mục đích khác nhau:

- Phương thức abstract mô tả hành vi mà lớp triển khai phải cung cấp.
- Phương thức default có thân, cung cấp hành vi mặc định có thể được lớp triển khai giữ nguyên hoặc override.
- Phương thức static thuộc về chính interface và được gọi bằng tên interface.
- Phương thức private hỗ trợ chia sẻ logic giữa các phương thức có thân bên trong interface; nó không phải hợp đồng mà lớp triển khai override.

Ví dụ:

~~~java
interface Auditable {
    String actor();

    default String auditLine() {
        return "actor=" + actor();
    }

    static String formatName() {
        return "audit";
    }

    private String normalize(String value) {
        return value.trim();
    }
}
~~~

Đoạn trên minh họa các dạng phương thức, nhưng normalize hiện chưa được gọi. Trong mã thật, phương thức private có thể được dùng bên trong một phương thức có thân, chẳng hạn default, để chia sẻ logic. Một phương thức private trong interface không được lớp triển khai gọi trực tiếp như một phần API và không thể override.

Các trường được khai báo trong interface cũng có quy tắc riêng: chúng ngầm định là public static final. Vì vậy interface thường không dùng để lưu trạng thái instance riêng cho từng đối tượng. Nếu mỗi đối tượng cần dữ liệu thay đổi, hãy đặt dữ liệu đó trong lớp triển khai hoặc lớp nền phù hợp.

## 4. Hiểu lầm: phương thức default luôn được dùng, không cần quan tâm xung đột

**Thực tế:** phương thức default cho phép interface bổ sung hành vi mặc định, nhưng Java có quy tắc giải quyết khi nhiều nguồn cung cấp phương thức cùng chữ ký.

1. Phương thức instance phù hợp được định nghĩa trong lớp thường được ưu tiên hơn phương thức default từ interface.
2. Nếu một interface con mở rộng interface cha và override phương thức default, phần định nghĩa ở interface con cụ thể hơn.
3. Nếu một lớp nhận hai phương thức default xung đột từ hai interface độc lập, lớp thường phải override phương thức để giải quyết xung đột.

Ví dụ sau **cố ý không biên dịch** vì CombinedLogger nhận hai phương thức default cùng chữ ký mà không chọn cách xử lý:

~~~java
interface ConsoleLog {
    default String channel() {
        return "console";
    }
}

interface FileLog {
    default String channel() {
        return "file";
    }
}

// Cố ý sai: không giải quyết xung đột channel()
class CombinedLogger implements ConsoleLog, FileLog {
}
~~~

Cách sửa là override rõ ràng:

~~~java
class CombinedLogger implements ConsoleLog, FileLog {
    @Override
    public String channel() {
        return ConsoleLog.super.channel() + "+" + FileLog.super.channel();
    }
}
~~~

Cú pháp ConsoleLog.super.channel() cho phép gọi phần triển khai mặc định cụ thể từ interface đó. Chỉ dùng nó khi thật sự muốn tái sử dụng logic mặc định tương ứng.

## 5. Hiểu lầm: phương thức static của interface được kế thừa như phương thức instance

**Thực tế:** phương thức static của interface được gọi bằng tên interface. Nó không được kế thừa như phương thức instance thông thường của lớp triển khai.

~~~java
interface Identifier {
    static String kind() {
        return "identifier";
    }
}

class UserId implements Identifier {
}
~~~

Cách gọi hợp lệ là Identifier.kind(). Cách gọi UserId.kind() hoặc new UserId().kind() không phải cách gọi hợp lệ cho phương thức static chỉ được khai báo trong interface Identifier. Hãy phân biệt rõ phương thức static với phương thức default: default được gọi trên đối tượng và có thể override; static được gọi trên kiểu khai báo và không tham gia dispatch đa hình instance.

## 6. Hiểu lầm: interface có thể triển khai interface bằng implements

**Thực tế:** lớp dùng implements để triển khai interface. Một interface mở rộng một hoặc nhiều interface khác bằng extends. Một lớp chỉ có thể extends một lớp trực tiếp, nhưng có thể implements nhiều interface.

~~~java
interface HasId {
    String id();
}

interface HasLabel extends HasId {
    String label();
}

abstract class EntityBase {
    public abstract String type();
}

final class UserEntity extends EntityBase implements HasLabel {
    @Override
    public String id() {
        return "u-17";
    }

    @Override
    public String label() {
        return "User";
    }

    @Override
    public String type() {
        return "account";
    }
}
~~~

HasLabel kế thừa hợp đồng id() từ HasId, nên UserEntity phải triển khai cả id() và label(). Ngoài ra, UserEntity còn triển khai type() được kế thừa từ lớp abstract EntityBase. Vì thế, khi kiểm tra một lớp concrete, hãy xét toàn bộ phương thức abstract còn lại từ cả lớp cha lẫn các interface.

## 7. Ví dụ hoàn chỉnh: kiểm chứng bằng compiler và lúc chạy

Ví dụ dưới đây cho thấy lớp abstract có constructor và phương thức cụ thể; lớp con hoàn thiện phương thức abstract; kiểu tham chiếu là interface; phương thức default có thể bị override; phương thức static của interface được gọi bằng tên interface.

Lưu toàn bộ mã vào tệp **AbstractInterfaceChecks.java**:

~~~java
interface Describable {
    String describe();

    default String source() {
        return "interface default";
    }

    static String contract() {
        return "Describable";
    }
}

abstract class BaseReport implements Describable {
    private final String title;

    protected BaseReport(String title) {
        this.title = title;
        System.out.println("BaseReport constructor: " + title);
    }

    @Override
    public String describe() {
        return "Report: " + title;
    }

    protected abstract int itemCount();

    public final int itemCountForDisplay() {
        return itemCount();
    }
}

final class SalesReport extends BaseReport {
    SalesReport(String title) {
        super(title);
    }

    @Override
    public String source() {
        return "SalesReport override";
    }

    @Override
    protected int itemCount() {
        return 3;
    }
}

public class AbstractInterfaceChecks {
    public static void main(String[] args) {
        Describable value = new SalesReport("Q3");

        System.out.println(value.describe());
        System.out.println(value.source());
        System.out.println(Describable.contract());
        System.out.println(value instanceof Describable);
        System.out.println(((BaseReport) value).itemCountForDisplay());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~text
javac AbstractInterfaceChecks.java
java AbstractInterfaceChecks
~~~

Kết quả stdout chính xác:

~~~text
BaseReport constructor: Q3
Report: Q3
SalesReport override
Describable
true
3
~~~

Cách đọc kết quả:

- Dòng đầu xác nhận constructor của lớp abstract chạy khi tạo SalesReport.
- describe() dùng phần triển khai chung trong BaseReport, dù biến value có kiểu Describable.
- source() gọi phần override của SalesReport, thay cho thân default trong interface.
- Describable.contract() được gọi qua tên interface vì đó là phương thức static.
- instanceof trả về true vì đối tượng thực là SalesReport, triển khai Describable thông qua lớp cha.
- itemCountForDisplay() là phương thức cụ thể công khai gọi phương thức abstract itemCount() đã được SalesReport hoàn thiện.

## 8. Cách kiểm chứng từng hiểu lầm

Không cần chỉ tin vào lời giải thích. Hãy thiết kế một phép thử nhỏ cho mỗi quy tắc:

| Điều cần kiểm tra | Phép thử | Kết quả mong đợi |
|---|---|---|
| Có thể khởi tạo trực tiếp lớp abstract? | Thử khởi tạo BaseReport trong mã minh họa | Compiler từ chối vì lớp abstract |
| Concrete class có thể thiếu phương thức abstract? | Bỏ itemCount() khỏi SalesReport mà vẫn để lớp concrete | Compiler báo chưa triển khai phương thức abstract |
| Phương thức default có thể bị override? | Gọi source() trước và sau khi thêm override | Kết quả chuyển từ interface default sang SalesReport override |
| Phương thức static interface gọi thế nào? | Gọi Describable.contract() | Trả về Describable |
| Kiểu tham chiếu có quyết định implementation của phương thức instance? | Giữ Describable value, đổi lớp thực sang lớp khác override describe() | Lời gọi dùng implementation của đối tượng thực |
| Hai default method cùng chữ ký có tự chọn một cái? | Triển khai hai interface có cùng default mà không override | Compiler báo xung đột |

Các thử nghiệm cố ý gây lỗi nên được thực hiện riêng từng trường hợp, vì một lỗi biên dịch có thể ngăn những phần còn lại được kiểm tra. Sau mỗi thay đổi, biên dịch lại và đọc thông báo mới.

## 9. Quy trình đọc lỗi và xác minh trong dự án

1. Đọc thông báo compiler và xác định file, dòng, lớp cùng phương thức liên quan.
2. Tìm khai báo gốc trong interface hoặc lớp cha, rồi so sánh chữ ký và mức truy cập.
3. Kiểm tra lớp có phải abstract hay concrete. Lớp concrete phải hoàn thiện mọi phương thức abstract còn thiếu.
4. Thêm @Override cho các phương thức dự định ghi đè để compiler bắt lỗi tên hoặc tham số sai.
5. Kiểm tra xung đột giữa các phương thức default, khác biệt giữa static và instance, và các quy tắc truy cập.
6. Chạy lại lệnh build chuẩn của dự án. Nếu dự án nhiều module, hãy dùng build tool để bảo đảm classpath và source path đúng; một lỗi thiếu dependency không đồng nghĩa với lỗi thiết kế interface.

Không xóa @Override chỉ để làm mất một thông báo lỗi. Annotation giúp phát hiện lỗi hợp đồng; xóa nó có thể che khuất ý định sai mà không sửa nguyên nhân.

## 10. Các hiểu lầm cần tránh

- **“Abstract class không có logic.”** Sai: lớp abstract có thể cung cấp trạng thái, constructor và logic dùng chung.
- **“Không thể dùng abstract class làm kiểu biến.”** Sai: có thể dùng nó làm kiểu tham chiếu, chỉ không thể khởi tạo trực tiếp.
- **“Interface chỉ có phương thức abstract.”** Sai: interface hiện đại có thể có default, static và private method.
- **“Default method không thể bị override.”** Sai: lớp triển khai có thể cung cấp cách thực hiện riêng.
- **“Static method interface là phương thức instance được kế thừa.”** Sai: gọi bằng tên interface, không qua dispatch đa hình instance.
- **“Một lớp triển khai một interface con chỉ cần triển khai các phương thức khai báo ngay trong interface con.”** Sai: hợp đồng có thể được kế thừa từ interface cha và lớp abstract.
- **“Compiler chỉ kiểm tra cú pháp.”** Sai: compiler còn kiểm tra kiểu, hợp đồng phương thức, phạm vi truy cập và nhiều quy tắc ngôn ngữ khác.

## 11. Cách ghi nhớ

Abstract class phù hợp để chia sẻ trạng thái và hành vi giữa các lớp có quan hệ kế thừa; interface mô tả hợp đồng mà nhiều kiểu có thể đáp ứng. Cả hai đều hỗ trợ đa hình, nhưng có quy tắc khác nhau về constructor, trạng thái, phương thức mặc định và cách kết hợp kiểu.

Khi gặp một khẳng định về Java, hãy chuyển nó thành một câu hỏi có thể kiểm chứng: mã này có biên dịch không, phương thức nào được gọi lúc chạy, hoặc giá trị nào được in ra? Một ví dụ nhỏ, một dự đoán trước khi chạy và kết quả thực tế là cách tốt để phân biệt quy tắc ngôn ngữ với suy đoán.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-005">Tra cứu Java Language Specification và Javadoc theo chủ đề</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu Java Language Specification và Javadoc theo chủ đề

Khi cần xác minh một quy tắc Java, hãy bắt đầu bằng cách xác định câu hỏi thuộc về **ngôn ngữ Java** hay **một API cụ thể**. Java Language Specification (JLS) mô tả cú pháp và ngữ nghĩa của ngôn ngữ; Javadoc của Java SE API mô tả hợp đồng sử dụng của lớp, interface, constructor, field và phương thức thư viện. Hai nguồn bổ sung cho nhau nhưng trả lời các loại câu hỏi khác nhau.

## 1. Chọn đúng nguồn

| Câu hỏi cần trả lời | Nguồn nên tra trước | Ví dụ chủ đề |
| --- | --- | --- |
| Mã nguồn có hợp lệ về cú pháp và kiểu không? | JLS | kiểu generic, chuyển đổi kiểu, phạm vi biến, overload phương thức |
| Phương thức nào được chọn tại thời điểm biên dịch? | JLS | overload resolution, method invocation, type inference |
| Phương thức instance nào được gọi lúc chạy? | JLS | overriding, dynamic dispatch, class initialization |
| Phương thức thư viện nhận tham số nào, trả gì, ném exception nào? | Javadoc của API | `List.get`, `Map.get`, `Files.readString` |
| Giá trị trả về có thể thay đổi được không? Điều gì xảy ra với `null`? | Javadoc của API cụ thể | `List.of`, `Optional.of`, `Map.copyOf` |
| Hành vi về thread, khóa và thứ tự bộ nhớ được quy định ra sao? | JLS và Javadoc liên quan | memory model, `synchronized`, `volatile`, concurrency utilities |
| Một lớp có constructor hoặc phương thức nào? | Javadoc của lớp đó | `StringBuilder`, `Path`, `CompletableFuture` |

Quy tắc ngôn ngữ và hợp đồng thư viện có thể cùng xuất hiện trong một vấn đề. Ví dụ, JLS xác định cách Java phân giải lời gọi phương thức; Javadoc xác định phương thức thư viện được chọn làm gì, các điều kiện đầu vào và ngoại lệ được ghi trong hợp đồng.

## 2. Các nguồn chính thức nên lưu

- **JLS Java SE 17:** https://docs.oracle.com/javase/specs/jls/se17/html/index.html
- **Trang chỉ mục đặc tả Java SE:** https://docs.oracle.com/javase/specs/
- **Java SE 17 API Specification:** https://docs.oracle.com/en/java/javase/17/docs/api/index.html
- **Javadoc của `java.util.List`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html

Các liên kết ở trên dùng phiên bản Java SE 17 làm ví dụ tham chiếu. Khi dự án chạy Java 21, 25 hoặc phiên bản khác, hãy mở JLS/API docs tương ứng với phiên bản cần xác minh. Không suy luận rằng một API có trong phiên bản mới cũng tồn tại trong phiên bản cũ.

## 3. Tra JLS theo chủ đề

JLS được chia thành các chương có chủ đề cụ thể. Nếu đã biết loại quy tắc cần tìm, mở chương liên quan trước rồi tìm thuật ngữ trong trang đó.

| Chủ đề | Chương JLS thường liên quan |
| --- | --- |
| Kiểu, giá trị, biến và generic types | Chương 4 — Types, Values, and Variables |
| Chuyển đổi kiểu, boxing/unboxing và chuyển đổi trong ngữ cảnh | Chương 5 — Conversions and Contexts |
| Tên, phạm vi, truy cập và phân giải tên | Chương 6 — Names |
| Package và module | Chương 7 — Packages and Modules |
| Class, constructor, method, overriding và hiding | Chương 8 — Classes |
| Khai báo và thành viên interface | Chương 9 — Interfaces |
| Mảng | Chương 10 — Arrays |
| Exception và checked exceptions | Chương 11 — Exceptions |
| Thực thi và khởi tạo class/interface | Chương 12 — Execution |
| Tương thích nhị phân | Chương 13 — Binary Compatibility |
| Câu lệnh, khối và điều khiển luồng | Chương 14 — Blocks, Statements, and Patterns (tùy phiên bản) |
| Biểu thức, gọi phương thức, lambda và toán tử | Chương 15 — Expressions |
| Definite assignment | Chương 16 — Definite Assignment |
| Thread, monitor, synchronization và memory model | Chương 17 — Threads and Locks |
| Type inference | Chương 18 — Type Inference |
| Ngữ pháp cú pháp | Chương 19 — Syntax |

Tên chương và nội dung chi tiết có thể thay đổi giữa các phiên bản JLS. Hãy dùng mục lục của đúng phiên bản; nếu tìm theo số mục như `15.12`, xác nhận số mục đó trong phiên bản đang đọc.

### Cách tìm một quy tắc trong JLS

1. Viết câu hỏi thành một hành vi cụ thể: “Vì sao lời gọi này chọn overload A thay vì overload B?” hoặc “Khi nào static initializer chạy?”.
2. Chuyển câu hỏi thành thuật ngữ của ngôn ngữ: `method invocation`, `overload resolution`, `class initialization`, `type inference` hoặc `definite assignment`.
3. Mở chương phù hợp trong JLS và tìm thuật ngữ bằng tìm kiếm trên trang (`Ctrl+F`).
4. Đọc toàn bộ mục chứa định nghĩa, các điều kiện áp dụng và các mục được tham chiếu; đừng dựa riêng vào một câu trích rời khỏi ngữ cảnh.
5. So sánh với đoạn mã nhỏ có thể biên dịch. Nếu đang giải thích một lỗi biên dịch, phân biệt rõ kết luận từ quy tắc ngôn ngữ với thông báo cụ thể do compiler đưa ra.

Ví dụ truy vấn web có thể dùng:

```text
site:docs.oracle.com/javase/specs/jls/se17/html/ "method invocation"
site:docs.oracle.com/javase/specs/jls/se17/html/ "class initialization"
site:docs.oracle.com/javase/specs/jls/se17/html/ "type inference"
```

## 4. Tra Javadoc theo lớp hoặc phương thức

Javadoc của một API thường cho biết:

- **Declaration/signature:** tên lớp, kiểu tham số, kiểu trả về, modifier và generic type.
- **Description:** phương thức thực hiện việc gì và ý nghĩa của kết quả.
- **Parameters:** điều kiện hoặc ý nghĩa của từng đối số.
- **Returns:** kết quả trả về, bao gồm các trường hợp đặc biệt được mô tả.
- **Throws:** ngoại lệ có thể hoặc được quy định sẽ ném theo điều kiện nào.
- **API notes, implementation requirements hoặc implementation notes:** hướng dẫn và yêu cầu bổ sung nếu có.
- **Since:** phiên bản Java SE mà API được thêm vào, nếu trang tài liệu cung cấp thông tin này.
- **See Also / inherited methods:** liên kết đến khái niệm hoặc API liên quan.

Khi kiểm tra phương thức, đừng chỉ đọc phần mô tả đầu trang. Tìm đúng overload theo kiểu tham số và đọc phần `Returns`, `Throws` cùng ghi chú liên quan. Hai overload có tên giống nhau có thể có điều kiện hoặc kết quả khác nhau.

Ví dụ truy vấn:

```text
site:docs.oracle.com/en/java/javase/17/docs/api/ "java.util.List" "unmodifiable"
site:docs.oracle.com/en/java/javase/17/docs/api/ "Files.readString"
site:docs.oracle.com/en/java/javase/17/docs/api/ "Optional.of" "NullPointerException"
```

Khi dùng công cụ tìm kiếm, ưu tiên kết quả từ tài liệu Oracle chính thức hoặc nguồn tài liệu chính thức của JDK đang dùng. Blog và câu trả lời diễn đàn hữu ích để tìm từ khóa hoặc ví dụ, nhưng nên quay lại đặc tả/Javadoc để xác nhận hợp đồng.

## 5. Quy trình tra cứu lặp lại được

### Bước 1 — Thu hẹp câu hỏi

Ghi lại đoạn mã tối thiểu, phiên bản Java, kết quả quan sát được và điều cần xác minh. Tránh câu hỏi chung như “Java hoạt động thế nào?”; thay bằng một câu hỏi kiểm chứng được, ví dụ “`List.of` có cho phép thêm phần tử sau khi tạo không?”.

### Bước 2 — Xác định lớp nguồn

- Nếu câu hỏi nói về tính hợp lệ của mã, chọn phương thức nào, phạm vi, kiểu hoặc thứ tự khởi tạo, bắt đầu với JLS.
- Nếu câu hỏi nói về hành vi của thư viện, bắt đầu với Javadoc của lớp/phương thức.
- Nếu có cả hai, đọc cả hai nguồn và ghi rõ nguồn nào hỗ trợ kết luận nào.

### Bước 3 — Khóa phiên bản

Kiểm tra phiên bản source/target, JDK dùng để biên dịch và JDK chạy chương trình. Mở tài liệu cùng phiên bản để tránh dùng API hoặc quy tắc được bổ sung sau đó. Nếu đang nghiên cứu khác biệt phiên bản, so sánh hai bản tài liệu có ghi rõ phiên bản thay vì trộn chúng.

### Bước 4 — Đọc hợp đồng, không chỉ đọc ví dụ

Tìm các từ chỉ điều kiện như “must”, “may”, “throws”, “unless”, “if”, “not supported” và các trường hợp biên. Ví dụ có thể chỉ minh họa một trường hợp; phần đặc tả hoặc Javadoc mới xác định phạm vi hành vi được cam kết.

### Bước 5 — Kiểm chứng bằng chương trình nhỏ

Biên dịch và chạy ví dụ tối thiểu khi câu hỏi có thể kiểm chứng thực nghiệm. Kết quả chạy giúp xác nhận rằng ví dụ tái hiện được hiện tượng, nhưng một lần chạy không thay thế đặc tả: hành vi có thể phụ thuộc vào phiên bản, môi trường hoặc phần chưa được API bảo đảm.

### Bước 6 — Ghi lại kết luận và căn cứ

Ghi tên nguồn, phiên bản, chương/mục JLS hoặc lớp/phương thức Javadoc, quy tắc liên quan và ví dụ tối thiểu. Khi chia sẻ kết quả trong review hoặc tài liệu nội bộ, liên kết trực tiếp đến mục chính xác thay vì chỉ ghi “theo tài liệu Java”.

## 6. Ví dụ: xác minh `List.of` có thể sửa đổi hay không

Câu hỏi: danh sách được tạo bằng `List.of("Ada", "Lin")` có cho phép gọi `add` không?

**Nguồn cần đọc:** Javadoc của `java.util.List`, phần phương thức `of`. Đây là hành vi của API thư viện, nên Javadoc là nguồn trực tiếp để kiểm tra hợp đồng. JLS có thể giải thích cách lời gọi generic được phân giải, nhưng không định nghĩa hợp đồng cụ thể của `List.of`.

Một ví dụ Java 17+ hoàn chỉnh:

```java
import java.util.List;

public class ListOfLookupDemo {
    public static void main(String[] args) {
        List<String> names = List.of("Ada", "Lin");

        try {
            names.add("Grace");
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println("List.of result is unmodifiable");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac ListOfLookupDemo.java
java ListOfLookupDemo
```

Stdout chính xác:

```text
List.of result is unmodifiable
```

Ví dụ này minh họa hành vi mong đợi theo hợp đồng API: các list do `List.of` tạo ra là unmodifiable, nên thao tác thay đổi như `add` ném `UnsupportedOperationException`. Không nên khái quát rằng mọi đối tượng triển khai `List` đều không thể sửa đổi; hãy kiểm tra cách tạo và hợp đồng của từng API. Trong chương trình thực tế, không cần cố gọi `add` để “thử” nếu đã đọc tài liệu và biết rõ hợp đồng.

## 7. Phân biệt điều được bảo đảm và điều tình cờ quan sát được

Khi đọc nguồn, hãy phân loại kết luận:

- **Được quy định/bảo đảm:** hành vi được mô tả trong quy tắc JLS hoặc hợp đồng API áp dụng.
- **Không được quy định cụ thể:** tài liệu không cam kết một kết quả duy nhất; tránh phụ thuộc vào kết quả đó.
- **Đặc thù triển khai:** hành vi đến từ compiler/JVM cụ thể nhưng không nên coi là quy tắc Java chung.
- **Quan sát từ ví dụ:** kết quả thực tế của một lần chạy, hữu ích để tái hiện nhưng cần đối chiếu nguồn quy phạm.

Đặc biệt chú ý các cụm “unspecified”, “implementation-dependent” và “implementation-specific” nếu chúng xuất hiện trong tài liệu. Chúng có ý nghĩa khác nhau; không nên tự diễn giải tất cả thành “Java luôn làm như vậy”.

## 8. Những lỗi tra cứu thường gặp

| Lỗi | Cách tránh |
| --- | --- |
| Dùng bài blog làm căn cứ cuối cùng | Dùng bài viết để tìm thuật ngữ, rồi xác minh trong JLS hoặc Javadoc chính thức. |
| Đọc tài liệu của phiên bản khác | Kiểm tra phiên bản JDK và mở đúng bản tài liệu; so sánh phiên bản có chủ đích nếu cần. |
| Tra JLS để tìm hợp đồng của một phương thức thư viện | Mở Javadoc của đúng lớp và overload trước. |
| Tra Javadoc để giải thích quy tắc biên dịch tổng quát | Tìm quy tắc tương ứng trong JLS, chẳng hạn overload resolution hoặc conversion. |
| Chỉ đọc tên phương thức hoặc phần mô tả ngắn | Đọc declaration, parameters, returns, throws và ghi chú liên quan. |
| Nhầm kết quả của một compiler thành quy tắc ngôn ngữ | Phân biệt thông báo compiler với quy tắc normative trong JLS. |
| Khái quát một ví dụ cho toàn bộ API | Xác định chính xác overload, điều kiện đầu vào và các trường hợp được bảo đảm. |
| Trích dẫn trang tài liệu chung chung | Ghi phiên bản và liên kết tới đúng chương/mục hoặc phương thức. |

## 9. Checklist trước khi kết luận

- [ ] Câu hỏi đã được viết thành hành vi cụ thể cần xác minh.
- [ ] Đã phân biệt quy tắc ngôn ngữ với hợp đồng API.
- [ ] Đã mở tài liệu chính thức và kiểm tra phiên bản Java liên quan.
- [ ] Đã đọc mục chính cùng các điều kiện, ngoại lệ và tham chiếu liên quan.
- [ ] Nếu có ví dụ, mã tối thiểu đã biên dịch/chạy hoặc đã ghi rõ vì sao không thể chạy độc lập.
- [ ] Kết quả thực nghiệm được phân biệt với điều mà tài liệu bảo đảm.
- [ ] Kết luận có liên kết đến đúng mục JLS hoặc Javadoc để người khác kiểm tra lại.

Thói quen quan trọng nhất là tìm đúng nguồn cho đúng câu hỏi: **JLS để xác minh quy tắc của ngôn ngữ; Javadoc để xác minh hợp đồng của API**. Chốt phiên bản, đọc điều kiện và ngoại lệ, rồi kiểm chứng bằng ví dụ nhỏ khi phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)
