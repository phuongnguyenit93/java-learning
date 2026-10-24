<a id="back-to-top"></a>

# Khởi tạo đối tượng

## Menu
- [Constructor lớp cha khi khởi tạo lớp con](#object-initialization-001)
- [Chuỗi gọi super và truyền dữ liệu bắt buộc](#object-initialization-002)
- [Thứ tự khởi tạo thuộc tính, khối khởi tạo và constructor](#object-initialization-003)
- [Duy trì trạng thái hợp lệ trong lớp trừu tượng](#object-initialization-004)
- [Rủi ro gọi phương thức có thể bị ghi đè trong constructor](#object-initialization-005)
- [Tránh để this thoát ra khi đối tượng chưa khởi tạo xong](#object-initialization-006)
- [Constructor không được kế thừa và không thể abstract](#object-initialization-007)
- [Factory tạo lớp cụ thể nhưng trả về kiểu trừu tượng](#object-initialization-008)

## <a id="object-initialization-001">Constructor lớp cha khi khởi tạo lớp con</a>

<details>
<summary>Xem chi tiết</summary>

# Constructor lớp cha khi khởi tạo lớp con trong Java

Khi gọi `new Child(...)`, Java tạo **một đối tượng Child duy nhất**. Đối tượng đó có cả các trường instance được khai báo ở Parent và Child; Java không tạo thêm một đối tượng Parent độc lập để nhét vào Child. Constructor của Parent chạy để thiết lập phần trạng thái do lớp cha sở hữu trước khi constructor của Child hoàn tất.

Constructor không được kế thừa hoặc override. Tuy vậy, constructor lớp cha vẫn tham gia chuỗi khởi tạo qua `super(...)`. Đây là hai ý khác nhau: **không kế thừa constructor** không có nghĩa là **bỏ qua constructor cha**.

## 1. Hình dung chuỗi constructor

~~~text
new Child(...)
   |
   v
Child constructor
   |
   +-- this(...) ? --> constructor Child khác
   |
   +-- super(...) / super() ngầm định
                    |
                    v
              Parent constructor
                    |
                    v
              ... -> Object constructor
                    |
                    v
              hoàn tất phần superclass
   |
   v
hoàn tất phần Child
   |
   v
trả về tham chiếu Child
~~~

Mỗi lớp chỉ gọi constructor của **lớp cha trực tiếp**. Nếu Parent còn extends Grandparent, constructor của Grandparent cũng nằm trong chuỗi. Java lần lượt hoàn tất phần khởi tạo của các lớp tổ tiên trước khi phần khởi tạo instance và phần thân constructor của Child hoàn tất.

## 2. Gọi constructor cha bằng super(...)

Ví dụ độc lập sau cho thấy lớp con truyền dữ liệu cho constructor cha. Lưu vào tệp **SuperConstructorDemo.java**:

~~~java
public class SuperConstructorDemo {
    static class Parent {
        private final String id;

        Parent(String id) {
            this.id = id;
            System.out.println("Parent(" + id + ")");
        }

        String id() {
            return id;
        }
    }

    static class Child extends Parent {
        private final int level;

        Child(String id, int level) {
            super(id);
            this.level = level;
            System.out.println("Child(level=" + level + ")");
        }

        int level() {
            return level;
        }
    }

    public static void main(String[] args) {
        Child child = new Child("A-01", 3);
        System.out.println(child.id() + " / " + child.level());
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac SuperConstructorDemo.java
java SuperConstructorDemo
~~~

Stdout:

~~~text
Parent(A-01)
Child(level=3)
A-01 / 3
~~~

Khi tạo Child, lời gọi `super(id)` chọn constructor Parent(String). Parent gán trường private final của mình; sau khi constructor cha trả về, Child mới gán level. Hai trường thuộc cùng một đối tượng Child.

`super(id)` là lời gọi constructor tường minh đến superclass trực tiếp, không phải gọi một phương thức thông thường và không tạo Parent mới. Danh sách đối số chọn overload theo luật của Java.

## 3. Khi nào Java tự gọi super()?

Trong Java 17, nếu constructor không bắt đầu bằng `this(...)` hoặc `super(...)`, trình biên dịch chèn lời gọi **super() không tham số**. Constructor không tham số đó phải tồn tại và truy cập được.

~~~java
class Parent {
    Parent(String id) {}
}

class BrokenChild extends Parent {
    BrokenChild() {
        // LỖI: Java chèn super(), nhưng Parent() không tồn tại.
    }
}
~~~

Sửa bằng cách gọi overload có thật:

~~~java
class FixedChild extends Parent {
    FixedChild(String id) {
        super(id);
    }
}
~~~

Khi Parent đã khai báo ít nhất một constructor, Java không tự sinh constructor mặc định không tham số cho Parent. Nếu cần cả Parent() và Parent(String), phải khai báo cả hai. Một constructor private hoặc không truy cập được từ Child cũng không thể được chọn chỉ nhờ `super()` ngầm định.

## 4. this(...) không làm constructor cha chạy hai lần

`this(args)` ủy quyền sang constructor khác **trong cùng lớp**; `super(args)` gọi constructor superclass trực tiếp. Trong Java 17, lời gọi constructor tường minh phải đứng đầu constructor, nên không thể viết trực tiếp cả hai lời gọi trong một thân constructor.

Giả sử `Child()` gọi `this("guest")` và `Child(String role)` gọi `super(role)`. Thứ tự các **phần thân** chạy là `Parent(String)` → `Child(String)` → `Child()`. Chuỗi ủy quyền chỉ chọn đường khởi tạo; nó không tạo thêm đối tượng Parent hoặc Child và không khiến instance initializer chạy hai lần.

Bài “Chuỗi gọi super và truyền dữ liệu bắt buộc” sẽ theo dõi cách chuyển tham số và xác thực dữ liệu qua nhiều tầng kế thừa.

## 5. Constructor của abstract class vẫn chạy

Abstract class không thể được khởi tạo trực tiếp, nhưng vẫn có thể sở hữu trạng thái chung và constructor có thân:

~~~java
abstract class Account {
    private final String id;

    protected Account(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
    }

    public final String id() {
        return id;
    }
}

final class SavingsAccount extends Account {
    SavingsAccount(String id) {
        super(id);
    }
}
~~~

`new SavingsAccount("A-01")` gọi constructor Account qua `super(id)`. Constructor cha kiểm tra và gán dữ liệu do Account sở hữu. Interface thì không có constructor; một lớp implements interface chỉ chạy constructor của các lớp trong chuỗi extends của nó.

## 6. Ngoại lệ và quyền truy cập

Nếu constructor cha khai báo checked exception, constructor con gọi `super(...)` phải khai báo ngoại lệ phù hợp trong `throws` (hoặc chọn một overload superclass không ném ngoại lệ đó). Trong Java 17, không thể bao lời gọi `super(...)` bằng `try/catch` vì lời gọi constructor tường minh phải đứng đầu constructor; caller có thể xử lý exception quanh biểu thức `new Child(...)`.

Nếu constructor cha ném exception, việc khởi tạo bị dừng; phần còn lại của constructor con không chạy và biểu thức `new Child(...)` không trả về một đối tượng khởi tạo thành công. Vì vậy, constructor nên kiểm tra dữ liệu và thiết lập trạng thái thuộc trách nhiệm của lớp mình; tránh thực hiện tác dụng phụ bên ngoài khó hoàn tác.

Mức truy cập cũng áp dụng cho constructor: public, protected, package-private và private quyết định nơi có thể gọi. Constructor private của Parent không thể được Child thông thường ở ngoài phạm vi truy cập gọi bằng `super(...)`.

## 7. Cảnh báo khi constructor gọi phương thức override

Constructor cha có thể gọi một phương thức instance bị Child override. Java vẫn chọn phương thức theo lớp runtime của đối tượng, trong khi các trường và initializer của Child có thể chưa sẵn sàng. Ví dụ, nếu Parent() gọi `label()` và Child override phương thức đó để đọc `label = "ready"`, lời gọi sớm có thể thấy null.

Tránh lời gọi overridable trực tiếp hoặc gián tiếp trong constructor. Phân tích chi tiết về dynamic dispatch, this escape và các cách sửa thuộc các bài chuyên biệt kế tiếp; ở đây chỉ cần nhớ rằng **constructor cha chạy trước khi phần khởi tạo riêng của Child hoàn tất**.

## 8. Phân biệt cú pháp

| Cú pháp | Tác dụng |
| --- | --- |
| `super(args)` | Gọi constructor superclass trực tiếp để khởi tạo phần cha của cùng đối tượng. |
| `this(args)` | Gọi constructor khác trong cùng lớp. |
| `super.method(args)` | Gọi implementation của phương thức ở superclass tại vị trí hợp lệ. |
| `this.method(args)` | Gọi phương thức trên đối tượng hiện tại; override vẫn có thể được dispatch. |
| `new Parent(args)` | Tạo một đối tượng Parent riêng. |

`super(...)` và `super.method(...)` không cùng cơ chế: một bên là lời gọi constructor, bên kia là lời gọi phương thức.

## 9. Lỗi thường gặp và tự kiểm tra

- **Constructor cha không tham số không tồn tại:** gọi `super(args)` đúng chữ ký hoặc cung cấp constructor cha có thể truy cập.
- **Cho rằng Child kế thừa Parent(int):** khai báo constructor Child(int) của chính lớp con rồi gọi `super(value)`.
- **Viết cả this(...) và super(...) trong cùng constructor:** chọn một lời gọi đầu tiên; constructor được gọi tiếp tục chuỗi.
- **Cho rằng abstract class không có constructor:** abstract class có constructor, nhưng không thể `new` trực tiếp.
- **Cho rằng constructor cha không thể ảnh hưởng đến Child:** lời gọi phương thức override từ constructor cha có thể thấy trạng thái Child chưa hoàn chỉnh.

**Câu hỏi 1.** Parent chỉ có Parent(String). Child() {} có biên dịch không?  
**Đáp án:** Không, vì Java 17 chèn super() nhưng Parent() không tồn tại.

**Câu hỏi 2.** Khi Child() gọi this("guest") và Child(String) gọi super("guest"), constructor Parent chạy mấy lần?  
**Đáp án:** Một lần cho đối tượng đó.

**Câu hỏi 3.** new SavingsAccount("A-01") có chạy constructor Account không?  
**Đáp án:** Có. Account không được khởi tạo riêng, nhưng constructor của nó chạy như một phần của chuỗi khởi tạo SavingsAccount.

Hãy nhớ: constructor cha khởi tạo phần trạng thái của mình trong cùng đối tượng lớp con; `super(...)` chọn constructor cha, còn `this(...)` ủy quyền giữa các constructor cùng lớp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-initialization-002">Chuỗi gọi super và truyền dữ liệu bắt buộc</a>

<details>
<summary>Xem chi tiết</summary>

# Chuỗi gọi super và truyền dữ liệu bắt buộc khi khởi tạo đối tượng Java

Khi tạo một đối tượng lớp con, Java phải chạy chuỗi constructor của các lớp cha. Mỗi tầng chịu trách nhiệm khởi tạo dữ liệu mà tầng đó sở hữu. Điều này đặc biệt hữu ích với abstract class: không thể tạo trực tiếp abstract class, nhưng constructor của nó vẫn chạy khi một lớp con cụ thể được tạo.

Bài này tập trung vào **super(...)**, **this(...)** và việc buộc lớp con truyền đủ dữ liệu cần thiết cho superclass. Chi tiết về thứ tự field initializer thuộc bài “Thứ tự khởi tạo thuộc tính, khối khởi tạo và constructor”; nguy cơ gọi phương thức override trong constructor được phân tích riêng ở bài chuyên biệt.

## 1. Theo dõi chuỗi constructor

~~~text
Object
  ↑
Entity (abstract class)
  ↑
Account
  ↑
PremiumAccount
~~~

Khi tạo `new PremiumAccount(...)`, mỗi constructor chỉ gọi superclass trực tiếp của mình. PremiumAccount gọi Account, Account gọi Entity, và chuỗi tiếp tục tới Object. Đây là nhiều lời gọi trên **một đối tượng**, không phải nhiều đối tượng cha lồng nhau.

Interface không có constructor và không thêm một tầng vào chuỗi này, dù lớp có thể implements nhiều interface.

## 2. Dữ liệu bắt buộc không được tự suy ra

Trong Java 17, nếu superclass chỉ khai báo `Base(String code)` thì không tồn tại constructor mặc định `Base()` được compiler tự sinh. Constructor subclass cuối chuỗi `this(...)` phải gọi một overload superclass hợp lệ và có thể truy cập bằng `super(code)`; compiler không tự điền dữ liệu còn thiếu.

Một chữ ký `Child(String code)` buộc caller cung cấp đối số ở mức cú pháp, còn constructor `Base` mới là nơi kiểm tra giá trị như null hoặc chuỗi rỗng. Khi chuẩn hóa đối số cho `super(...)`, có thể sử dụng tham số, hằng hoặc helper static; không dựa vào trường instance của đối tượng đang khởi tạo. Ví dụ đầy đủ ở phần tiếp theo cho thấy cách superclass sở hữu việc xác thực và subclass chỉ cung cấp dữ liệu.

## 3. Buộc lớp con truyền dữ liệu cho abstract class

Abstract class thường sở hữu các thuộc tính chung như mã định danh hoặc chủ sở hữu. Constructor có tham số buộc subclass chuyển dữ liệu ngay khi tạo đối tượng; superclass tự kiểm tra và thiết lập bất biến của nó.

Lưu ví dụ chạy được vào **Main.java**:

~~~java
import java.util.Objects;

public class Main {
    interface Auditable {
        String auditLabel();
    }

    static abstract class AccountRecord {
        private final String id;
        private final String owner;

        protected AccountRecord(String id, String owner) {
            this.id = requireNonBlank(id, "id");
            this.owner = requireNonBlank(owner, "owner");
            System.out.println("AccountRecord constructed");
        }

        private static String requireNonBlank(String value, String field) {
            Objects.requireNonNull(value, field + " must not be null");
            if (value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value;
        }

        public final String id() {
            return id;
        }

        public final String owner() {
            return owner;
        }

        public abstract String summary();
    }

    static final class PremiumAccount extends AccountRecord
            implements Auditable {
        private final String tier;

        PremiumAccount(String id, String owner, String tier) {
            super(id, owner);
            this.tier = Objects.requireNonNull(tier, "tier");
            System.out.println("PremiumAccount constructed");
        }

        @Override
        public String summary() {
            return id() + " | " + owner() + " | " + tier;
        }

        @Override
        public String auditLabel() {
            return id() + "/" + tier;
        }
    }

    public static void main(String[] args) {
        PremiumAccount account =
                new PremiumAccount("P-100", "Linh", "GOLD");
        System.out.println(account.summary());
        System.out.println("Audit: " + account.auditLabel());

        try {
            new PremiumAccount("", "Linh", "GOLD");
        } catch (IllegalArgumentException ex) {
            System.out.println(ex.getMessage());
        }
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac Main.java
java Main
~~~

Stdout:

~~~text
AccountRecord constructed
PremiumAccount constructed
P-100 | Linh | GOLD
Audit: P-100/GOLD
id must not be blank
~~~

Điều cần quan sát:

1. AccountRecord là abstract nhưng constructor của nó vẫn chạy qua `super(id, owner)`.
2. AccountRecord sở hữu và xác thực id, owner rồi lưu vào các trường private final. PremiumAccount không thể gán trực tiếp các trường ấy.
3. Auditable chỉ là hợp đồng hành vi; interface này không có constructor và không tham gia chuỗi super.
4. Chỉ khi constructor cha trả về, constructor PremiumAccount mới tiếp tục gán tier.
5. Nếu id rỗng, constructor cha ném exception và constructor PremiumAccount không đi đến dòng in thứ hai. Chữ ký constructor buộc cung cấp tham số; logic xác thực mới buộc giá trị thực sự hợp lệ.

Constructor nên hạn chế tác dụng phụ bên ngoài. Nếu subclass còn dữ liệu riêng có thể không hợp lệ, việc khởi tạo tổng thể vẫn có thể thất bại sau khi superclass đã chạy; vì vậy đừng gửi thông điệp, đăng ký callback hoặc công bố this trong lúc xây dựng.

## 4. Dùng this(...) để tránh lặp kiểm tra

`this(args)` gọi constructor khác trong cùng lớp. Một constructor chính có thể gom việc xác thực và gán trường, trong khi overload ngắn cung cấp giá trị mặc định:

~~~java
public class TicketDemo {
    static class Base {
        Base(String owner) {
            System.out.println("Base(" + owner + ")");
        }
    }

    static class Ticket extends Base {
        private final int quantity;

        Ticket(int quantity) {
            this("guest", quantity);
            System.out.println("Ticket shortcut");
        }

        Ticket(String owner, int quantity) {
            super(owner);
            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "quantity must be positive");
            }
            this.quantity = quantity;
            System.out.println("Ticket(" + owner + ", " + this.quantity + ")");
        }
    }

    public static void main(String[] args) {
        new Ticket(2);
    }
}
~~~

Stdout:

~~~text
Base(guest)
Ticket(guest, 2)
Ticket shortcut
~~~

Chuỗi là Ticket(int) → Ticket(String, int) → Base(String), rồi quay ngược về phần còn lại của Ticket(int). Đây vẫn là một đối tượng Ticket; initializer của lớp không chạy hai lần. Trong Java 17, lời gọi this(...) hoặc super(...) phải đứng đầu constructor và một constructor không thể đặt cả hai lời gọi trực tiếp ở đó.

## 5. Phân chia trách nhiệm qua nhiều tầng

~~~java
abstract class BaseEntity {
    private final String id;

    protected BaseEntity(String id) {
        this.id = java.util.Objects.requireNonNull(id, "id");
    }

    public final String id() {
        return id;
    }
}

abstract class OwnedEntity extends BaseEntity {
    private final String owner;

    protected OwnedEntity(String id, String owner) {
        super(id);
        this.owner = java.util.Objects.requireNonNull(owner, "owner");
    }

    public final String owner() {
        return owner;
    }
}

final class Document extends OwnedEntity {
    private final String title;

    Document(String id, String owner, String title) {
        super(id, owner);
        this.title = java.util.Objects.requireNonNull(title, "title");
    }

    public String description() {
        return id() + ": " + title + " (" + owner() + ")";
    }
}
~~~

Đây là các khai báo hợp lệ, không có main. Khi tạo Document, BaseEntity sở hữu id, OwnedEntity sở hữu owner, còn Document sở hữu title. Mỗi constructor kiểm tra dữ liệu do chính tầng đó quản lý. Nếu hệ thống cần quá nhiều lớp chỉ để chuyền vài trường, hãy cân nhắc composition thay vì thêm tầng kế thừa.

## 6. Các ranh giới của lời gọi constructor

Trong Java 17, các đối số của `super(...)` không được dựa vào trạng thái instance của đối tượng chưa khởi tạo. Có thể dùng tham số constructor hoặc helper static để chuẩn hóa/xác thực:

~~~java
class Parent {
    Parent(String id) {}
}

class Child extends Parent {
    Child(String input) {
        super(java.util.Objects.requireNonNull(input, "input").trim());
    }
}
~~~

Đoạn này hợp lệ. Nếu input là null, Objects.requireNonNull ném exception trước khi constructor cha được gọi. Không nên giấu logic phức tạp trong biểu thức đối số; chỉ dùng cách này khi giúp constructor rõ ràng hơn.

Nếu superclass constructor khai báo checked exception, subclass gọi `super(...)` phải khai báo ngoại lệ phù hợp trong `throws`; Java 17 không cho bao lời gọi `super(...)` bằng `try/catch` trong constructor con. Caller có thể bắt exception khi gọi `new Child(...)`. Cũng cần kiểm tra mức truy cập của constructor cha: `private` hoặc package-private có thể không gọi được từ vị trí subclass.

## 7. Lỗi thường gặp và tự kiểm tra

| Tình huống | Cách hiểu hoặc sửa |
| --- | --- |
| Compiler báo implicit super constructor không tồn tại | Viết `super(args)` đúng chữ ký hoặc khai báo constructor cha phù hợp. |
| Lớp con quên truyền id/owner bắt buộc | Đưa dữ liệu vào tham số constructor và gọi super(...) rõ ràng. |
| Các overload lặp logic xác thực | Chọn một constructor chính, cho overload khác gọi this(...). |
| Trường private của cha bị gán từ lớp con | Để superclass tự gán trường qua constructor của nó. |
| Constructor cha gọi hook của lớp con | Tránh logic đa hình khi subclass chưa sẵn sàng; xem bài về overridable method trong constructor. |
| Chỉ kiểm tra constructor nhưng không kiểm tra API cập nhật | Bảo vệ invariant cả sau khi tạo đối tượng. |

**Câu 1.** Abstract class chỉ có Parent(String id). Child() {} có hợp lệ không?  
**Đáp án:** Không, vì Java 17 ngầm chèn super() nhưng Parent() không tồn tại.

**Câu 2.** Một lớp implements ba interface có chạy ba chuỗi constructor không?  
**Đáp án:** Không. Interface không có constructor; chuỗi extends có một superclass trực tiếp ở mỗi tầng.

**Câu 3.** Nên xác thực id ở đâu nếu id thuộc trách nhiệm của abstract superclass?  
**Đáp án:** Trong constructor superclass nhận id, rồi lưu vào trường được đóng gói; subclass bắt buộc gọi super(id).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-initialization-003">Thứ tự khởi tạo thuộc tính, khối khởi tạo và constructor</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự khởi tạo thuộc tính, khối khởi tạo và constructor trong Java

## 1. Quy tắc cần nhớ

Khi tạo một đối tượng Java bằng `new`, các thành phần khởi tạo **không chạy theo thứ tự tùy ý**. Đối với một lớp cụ thể có thể khởi tạo, trình tự cốt lõi là:

1. **Khởi tạo lớp (nếu chưa xảy ra):** các bộ khởi tạo `static` cần thiết được thực hiện, với lớp cha được khởi tạo trước lớp con.
2. **Cấp phát đối tượng và đặt giá trị mặc định:** các trường instance có giá trị ban đầu như `0`, `false`, `null`; đó chưa phải giá trị được viết bằng biểu thức khởi tạo trường.
3. **Chạy chuỗi constructor từ lớp con lên lớp cha để tìm điểm bắt đầu:** constructor con gọi `super(...)` hoặc `this(...)`; việc thực thi constructor của tổ tiên diễn ra trước phần khởi tạo instance của lớp con.
4. **Trong từng lớp, sau khi constructor lớp cha kết thúc:** chạy các biểu thức khởi tạo trường instance và các khối khởi tạo instance của lớp hiện tại **theo thứ tự xuất hiện trong mã nguồn**, sau đó chạy phần thân constructor hiện tại.
5. **Hoàn thành lớp con cuối cùng:** khi constructor của lớp được `new` kết thúc, đối tượng mới hoàn tất quá trình khởi tạo thông thường.

Với quan hệ `Parent` → `Child` và không có ủy quyền constructor phức tạp, thứ tự dễ nhớ là:

```text
Khởi tạo static cần thiết (Parent trước Child, một lần mỗi lớp)
    ↓
Cấp phát + giá trị mặc định cho toàn bộ trường instance
    ↓
Object constructor
    ↓
Parent: field initializer / instance initializer block (thứ tự trong mã)
    ↓
Parent: constructor body
    ↓
Child: field initializer / instance initializer block (thứ tự trong mã)
    ↓
Child: constructor body
    ↓
new Child() trả về tham chiếu đối tượng
```

Sơ đồ này diễn tả trường hợp thông thường. Nếu constructor gọi `this(...)`, nó chuyển sang một constructor khác **cùng lớp** trước; các field initializer và instance initializer của lớp đó chỉ chạy **một lần** trên mỗi đối tượng. Ngoài ra, static initializer có thể đã chạy từ một lần sử dụng lớp trước đó; nó **không nhất thiết chạy ngay trong lần `new` đang quan sát**.

## 2. Ba loại thành phần dễ nhầm lẫn

| Thành phần | Ví dụ | Khi nào thực thi? |
| --- | --- | --- |
| Khởi tạo trường instance | `int count = 10;` | Mỗi lần đối tượng của lớp hoặc lớp con được tạo, ở pha khởi tạo instance của lớp khai báo trường |
| Khối khởi tạo instance | `{ count++; }` | Mỗi lần khởi tạo đối tượng, xen với field initializer theo thứ tự xuất hiện trong mã nguồn |
| Constructor | `Example() { ... }` | Chạy khi constructor tương ứng được gọi trong chuỗi khởi tạo đối tượng, sau pha khởi tạo instance của lớp có constructor đó |
| Khởi tạo trường static | `static int limit = 100;` | Khi lớp được khởi tạo, không lặp lại trên từng đối tượng |
| Khối khởi tạo static | `static { ... }` | Trong pha khởi tạo lớp, xen với các static field initializer theo thứ tự xuất hiện trong mã nguồn |

Hai nguyên tắc đặc biệt quan trọng:

- **Các thành phần instance trong cùng một lớp được xen kẽ theo thứ tự văn bản.** Không có quy tắc “khởi tạo hết tất cả field, rồi mới chạy tất cả instance block”. Nếu trong mã một instance block nằm giữa hai field initializer, nó chạy giữa hai initializer đó.
- **Static và instance là hai chuỗi riêng.** Một `static` block đặt cuối tệp không vì thế chạy sau constructor; khi khởi tạo lớp, các thành phần `static` của lớp chạy theo thứ tự tương ứng trước quá trình tạo đối tượng đầu tiên kích hoạt khởi tạo lớp ấy.

Các trường khai báo nhưng không có initializer, như `int count;`, cũng nhận giá trị mặc định ngay khi bộ nhớ đối tượng được chuẩn bị. Chúng không có một bước gán trong thứ tự field initializer của mã nguồn, trừ khi được gán trong block/constructor.

## 3. Thí nghiệm 1: thứ tự field initializer, instance block và constructor của cùng lớp

Chương trình hoàn chỉnh dưới đây chạy được với Java 17 trở lên.

**Tệp `Main.java`:**

```java
public class Main {
    private int first = mark("1. initialize first", 10);

    {
        System.out.println("2. instance block A; first = " + first);
    }

    private int second = mark("3. initialize second", 20);

    {
        System.out.println("4. instance block B; second = " + second);
    }

    Main() {
        System.out.println("5. constructor; first = "
                + first + ", second = " + second);
    }

    private int mark(String message, int value) {
        System.out.println(message);
        return value;
    }

    public static void main(String[] args) {
        new Main();
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 Main.java
java Main
```

**Stdout chính xác:**

```text
1. initialize first
2. instance block A; first = 10
3. initialize second
4. instance block B; second = 20
5. constructor; first = 10, second = 20
```

Giải thích:

1. Bộ nhớ của `Main` đã được chuẩn bị; `first` và `second` ban đầu đều có giá trị mặc định `0`.
2. Field initializer của `first` gọi `mark(...)`, in dòng 1 rồi trả về `10`.
3. Khối instance A chạy ở đúng vị trí được khai báo, thấy `first == 10` và in dòng 2.
4. Field initializer của `second` chạy tiếp, in dòng 3 và gán `20`.
5. Khối instance B in dòng 4; cuối cùng phần thân `Main()` in dòng 5.

Hàm `mark` chỉ giúp nhìn rõ thứ tự; bản thân phương thức này không được Java gọi tự động. Java gọi **biểu thức khởi tạo** chứa `mark`.

**Thử thay đổi:** di chuyển khối B lên trước khai báo `second` và thay việc đọc `second` trong khối bằng một lời gọi phương thức đọc giá trị `second`. Khi đó nó có thể quan sát giá trị mặc định `0`. Trong Java, việc tham chiếu trực tiếp một trường instance bằng tên đơn giản ở một initializer nằm trước khai báo trường đó còn có thể gặp quy tắc **illegal forward reference**; đó là kiểm tra về mã nguồn, khác với hiện tượng trường chưa được gán giá trị mong muốn.

## 4. Giá trị mặc định tồn tại trước khi initializer chạy

Đây là điểm quyết định để hiểu những đầu ra gây bất ngờ: tất cả trường instance của đối tượng đều có giá trị mặc định trước khi chuỗi constructor xử lý initializer.

| Kiểu trường | Giá trị mặc định |
| --- | --- |
| `byte`, `short`, `int`, `long` | `0` tương ứng với kiểu |
| `float`, `double` | `0.0` tương ứng với kiểu |
| `char` | `'\u0000'` (ký tự NUL) |
| `boolean` | `false` |
| Kiểu tham chiếu như `String`, mảng, đối tượng | `null` |

**Ví dụ độc lập, tệp `Main.java`:**

```java
public class Main {
    private int observed = readLater();
    private int later = 42;

    private int readLater() {
        return later;
    }

    Main() {
        System.out.println("observed = " + observed);
        System.out.println("later = " + later);
    }

    public static void main(String[] args) {
        new Main();
    }
}
```

Chạy `javac -encoding UTF-8 Main.java` rồi `java Main`:

```text
observed = 0
later = 42
```

Khi biểu thức `observed = readLater()` được thực thi, trường `later` đã tồn tại nhưng **chưa đến lượt** biểu thức `later = 42`. Phương thức `readLater()` đọc giá trị mặc định `0`; sau đó `later` mới được gán `42`. Đây là hành vi hợp lệ và nhất quán.

Lưu ý: ví dụ gọi qua một phương thức để minh họa giá trị mặc định. Viết trực tiếp `private int observed = later;` trước khai báo `later` có thể bị lỗi `illegal forward reference`; không nên nhầm lỗi đó với quy tắc giá trị mặc định.

## 5. Thí nghiệm 2: có kế thừa thì lớp cha khởi tạo instance trước lớp con

Xét hai lớp `Parent` và `Child`. Mỗi lớp có field initializer, instance block và constructor.

**Tệp `Main.java`:**

```java
class Parent {
    int parentValue = trace("1. Parent field", 10);

    {
        System.out.println("2. Parent instance block");
    }

    Parent() {
        System.out.println("3. Parent constructor");
    }

    static int trace(String message, int value) {
        System.out.println(message);
        return value;
    }
}

class Child extends Parent {
    int childValue = trace("4. Child field", 20);

    {
        System.out.println("5. Child instance block");
    }

    Child() {
        // super(); được trình biên dịch chèn ngầm tại đây trong Java 17.
        System.out.println("6. Child constructor");
    }
}

public class Main {
    public static void main(String[] args) {
        Child child = new Child();
        System.out.println("7. values = "
                + child.parentValue + ", " + child.childValue);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 Main.java
java Main
```

**Stdout chính xác:**

```text
1. Parent field
2. Parent instance block
3. Parent constructor
4. Child field
5. Child instance block
6. Child constructor
7. values = 10, 20
```

Điểm then chốt là constructor `Child()` gọi constructor `Parent()` trước. Khi `Parent()` thực sự thực thi, Java chạy các instance initializer của `Parent` theo thứ tự xuất hiện rồi mới đến phần thân `Parent()`. Sau khi phần của cha xong, Java mới chạy các initializer của `Child` rồi phần thân `Child()`.

Nếu `Parent` còn kế thừa một lớp khác, trình tự khởi tạo instance tiếp tục đi từ tổ tiên cao hơn xuống lớp con. Thông thường constructor của `Object` là điểm đầu của chuỗi này; ví dụ không in bước của `Object`.

### Khi constructor cha nhận tham số

Nếu `Parent` chỉ khai báo `Parent(String name)`, constructor `Child` phải gọi constructor cha phù hợp, ví dụ `super("Mimi");`. Java **không tự sinh ra** constructor không tham số trong `Parent` khi lớp đó đã có constructor do lập trình viên khai báo. Việc thiếu lời gọi `super(...)` phù hợp gây lỗi biên dịch về constructor, không thay đổi quy tắc thứ tự khởi tạo.

Trong Java 17, lời gọi `super(...)` hoặc `this(...)` tường minh phải là statement đầu tiên của constructor; hai lệnh này không thể xuất hiện đồng thời để gọi trực tiếp trong cùng một constructor.

## 6. Thí nghiệm 3: `this(...)` không làm chạy initializer hai lần

Một lớp có thể có nhiều constructor. Khi một constructor gọi `this(...)`, nó **ủy quyền** cho constructor khác của cùng lớp. Phần field initializer và instance block vẫn chỉ cần chạy **một lần** cho đối tượng đang được tạo.

**Tệp `Main.java`:**

```java
class Sample {
    private int value = report("1. field initializer");

    {
        System.out.println("2. instance block");
    }

    Sample() {
        this(5);
        System.out.println("4. no-arg constructor body");
    }

    Sample(int number) {
        System.out.println("3. int constructor body: " + number);
    }

    private static int report(String message) {
        System.out.println(message);
        return 100;
    }
}

public class Main {
    public static void main(String[] args) {
        new Sample();
    }
}
```

Chạy `javac -encoding UTF-8 Main.java` và `java Main`:

```text
1. field initializer
2. instance block
3. int constructor body: 5
4. no-arg constructor body
```

Trình tự thực tế:

1. `new Sample()` chọn constructor không tham số.
2. Câu lệnh đầu tiên của constructor ấy là `this(5)`, chuyển tới `Sample(int)`.
3. Vì `Sample(int)` không gọi một constructor cùng lớp khác, nó thực hiện lời gọi `super()` ngầm, rồi các instance initializer của `Sample` chạy theo thứ tự khai báo, sau đó phần thân `Sample(int)`.
4. Quyền điều khiển quay về `Sample()` và chạy phần còn lại của constructor này.

Không có lần thứ hai in `field initializer` hoặc `instance block`. Một chuỗi `this(...)` phải kết thúc ở constructor gọi `super(...)` (tường minh hoặc ngầm định), không được tạo vòng lặp gọi constructor vô tận.

## 7. Thí nghiệm 4: static initializer và instance initializer khác nhau ra sao?

`static` thuộc về **lớp**, không thuộc riêng một đối tượng. Các biểu thức khởi tạo `static` field và khối `static` được thực thi theo thứ tự xuất hiện trong mỗi lớp **khi lớp được khởi tạo**. Khi lớp con được khởi tạo, Java đảm bảo các lớp cha thích hợp đã được khởi tạo trước.

Trong khi đó, initializer instance chạy lại cho **mỗi đối tượng** mới.

**Tệp `Main.java`:**

```java
class Parent {
    static int sharedParent = log("Parent static field");

    static {
        log("Parent static block");
    }

    int instanceParent = log("Parent instance field");

    {
        log("Parent instance block");
    }

    Parent() {
        log("Parent constructor");
    }

    static int log(String message) {
        System.out.println(message);
        return 1;
    }
}

class Child extends Parent {
    static int sharedChild = log("Child static field");

    static {
        log("Child static block");
    }

    int instanceChild = log("Child instance field");

    {
        log("Child instance block");
    }

    Child() {
        log("Child constructor");
    }
}

public class Main {
    public static void main(String[] args) {
        System.out.println("-- first --");
        new Child();
        System.out.println("-- second --");
        new Child();
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 Main.java
java Main
```

**Stdout chính xác khi chạy chương trình này trong JVM mới:**

```text
-- first --
Parent static field
Parent static block
Child static field
Child static block
Parent instance field
Parent instance block
Parent constructor
Child instance field
Child instance block
Child constructor
-- second --
Parent instance field
Parent instance block
Parent constructor
Child instance field
Child instance block
Child constructor
```

Ở lần `new Child()` đầu tiên, `Parent` và `Child` chưa được khởi tạo, nên chuỗi static của lớp cha rồi lớp con chạy trước chuỗi instance. Ở lần thứ hai, lớp đã khởi tạo và **không thực hiện lại** các static initializer; mỗi đối tượng mới vẫn chạy lại chuỗi instance và constructor.

Có một ngoại lệ cần hiểu về **thời điểm quan sát**: lớp có thể đã được khởi tạo sớm hơn do một thao tác sử dụng chủ động khác (ví dụ truy cập một static field không phải hằng số tại thời điểm biên dịch, gọi static method hoặc dùng `Class.forName` theo cách khởi tạo lớp). Vì thế, các dòng static sẽ không nhất thiết xuất hiện tại lệnh `new` đầu tiên trong một chương trình lớn. Truy cập một hằng `static final` là biểu thức hằng tại thời điểm biên dịch cũng có quy tắc kích hoạt riêng.

Không nên suy luận thứ tự thực thi `static` và instance từ việc dòng khai báo nào xuất hiện trước trong cả tệp. Hãy xác định **giai đoạn khởi tạo lớp** và **giai đoạn khởi tạo đối tượng** trước, rồi xét thứ tự trong từng giai đoạn.

## 8. Thứ tự gán giá trị nếu cùng một trường được gán nhiều lần

Giá trị cuối của trường là giá trị sau lần gán cuối cùng **trong luồng khởi tạo đã thực sự chạy**.

**Ví dụ độc lập, tệp `Main.java`:**

```java
public class Main {
    private int level = 1;

    {
        level = 2;
    }

    Main() {
        level = 3;
    }

    public static void main(String[] args) {
        System.out.println(new Main().level);
    }
}
```

Kết quả khi chạy `javac -encoding UTF-8 Main.java` và `java Main`:

```text
3
```

Đối tượng nhận `level = 0` mặc định, rồi lần lượt `1` ở field initializer, `2` trong instance block, và `3` trong constructor. Việc đặt giá trị ở constructor thường dùng khi giá trị phụ thuộc tham số lúc tạo đối tượng; field initializer hợp với giá trị mặc định cố định; instance block phù hợp với logic chung giữa các constructor nhưng nên dùng thận trọng vì dễ làm luồng khởi tạo khó đọc.

Với **blank final instance field** (trường `final` không gán ngay tại khai báo), Java yêu cầu trường được gán đúng theo quy tắc definite assignment trước khi constructor hoàn tất. Có thể gán nó trong instance initializer hoặc constructor phù hợp, nhưng không thể tự do ghi đè nhiều lần như ví dụ `level` không có `final`. Giá trị `final` có sẵn initializer tại khai báo thì không được gán lại trong constructor.

## 9. Phương thức override có thể chạy khi trường lớp con còn mặc định

Chú ý một trường hợp dễ sai khi dự đoán thứ tự stdout: nếu constructor `Parent` gọi một phương thức instance có thể override, Java vẫn dispatch đến implementation ở `Child`, dù initializer của `Child` chưa chạy. Giả sử `Child` khai báo `String label = "READY"` và override `describe()` để đọc `label`, còn `Parent()` gọi `describe()`.

| Thời điểm | Giá trị `Child.label` khi `describe()` đọc |
| --- | --- |
| Trong phần thân `Parent()` | `null` (giá trị mặc định, chưa chạy initializer của Child) |
| Sau khi `new Child()` hoàn tất | `"READY"` |

**Thứ tự khởi tạo vẫn giữ nguyên**; chính cơ chế dynamic dispatch khiến logic lớp con chạy sớm. Khi phân tích ví dụ có gọi phương thức từ constructor, hãy thêm bước kiểm tra override vào sơ đồ thứ tự. Bài “Rủi ro gọi phương thức có thể bị ghi đè trong constructor” cung cấp chương trình tái hiện, những kiểu lỗi và hướng thiết kế an toàn.

## 10. Checklist tự dự đoán đầu ra trước khi chạy

Với bài tập yêu cầu tìm thứ tự dòng in khi khởi tạo đối tượng, có thể giải theo quy trình sau:

1. **Xác định lệnh tạo đối tượng và kiểu thực tế sau `new`.** Ví dụ `Parent p = new Child();` vẫn tạo **đối tượng Child**, nên chuỗi khởi tạo của `Child` được dùng.
2. **Xác định những lớp nào chưa được khởi tạo static.** Nếu chưa, thực hiện static initializer theo thứ tự lớp cha trước lớp con; trong mỗi lớp, static field initializer và static block xen kẽ theo mã nguồn.
3. **Tạm ghi giá trị mặc định cho mọi trường instance.** Việc có `int x = 7` không có nghĩa ngay từ đầu `x` đã là `7`.
4. **Vẽ chuỗi `this(...)` / `super(...)`.** Tìm constructor cuối cùng của mỗi chuỗi ủy quyền và phần thân nào sẽ chạy khi các lời gọi quay về.
5. **Ở từng lớp từ cha đến con:** chạy instance field initializer và instance block theo đúng thứ tự xuất hiện, rồi phần thân constructor liên quan.
6. **Nếu một phương thức overridable được gọi**, xét lớp triển khai được chọn bằng dynamic dispatch và trạng thái của các trường **ngay tại thời điểm gọi**.
7. **Tách các đối tượng khác nhau.** Lần `new` mới chạy lại phần instance, nhưng không chạy lại static initializer của lớp đã được khởi tạo.

### Những ngộ nhận thường gặp

- “Field initializer nào cũng chạy trước mọi instance block.” **Sai:** cùng lớp, chúng được xen kẽ theo thứ tự khai báo.
- “Constructor con chạy trước vì `new Child()`.” **Sai:** constructor cha hoàn tất phần khởi tạo của nó trước pha khởi tạo instance và phần thân constructor con.
- “`this(...)` làm gọi hai constructor nên khối instance chạy hai lần.” **Sai:** chỉ có một đối tượng; initializer của lớp chạy một lần, dù nhiều phần thân constructor có thể chạy khi quay về.
- “Static initializer chạy khi mỗi đối tượng được tạo.” **Sai:** chạy một lần trong quá trình khởi tạo lớp, ở thời điểm do quy tắc kích hoạt khởi tạo lớp quyết định.
- “Biểu thức gán trong khai báo field có hiệu lực trước constructor cha.” **Sai:** trường lớp con còn giữ giá trị mặc định khi constructor cha đang chạy.
- “Gọi phương thức override từ constructor cha sẽ thấy lớp con đã sẵn sàng.” **Sai:** ví dụ `label = null` chứng minh trạng thái lớp con có thể chưa hoàn tất.

## 11. Tóm tắt thứ tự thực thi

Hãy luôn phân biệt ba việc: **khởi tạo lớp static**, **chuẩn bị giá trị mặc định cho đối tượng**, và **khởi tạo instance theo chuỗi constructor**. Trong một lớp, field initializer và instance block chạy theo thứ tự xuất hiện trong mã nguồn; phần thân constructor chạy sau chúng. Khi có kế thừa, quá trình instance đi từ cha xuống con. Khi có `this(...)`, phần initializer của lớp chỉ chạy một lần. Nắm vững các quy tắc này giúp dự đoán chính xác stdout, tránh dùng trạng thái chưa khởi tạo và viết constructor dễ bảo trì hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-initialization-004">Duy trì trạng thái hợp lệ trong lớp trừu tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Duy trì trạng thái hợp lệ trong lớp trừu tượng (Java)

Một đối tượng có **trạng thái hợp lệ** khi các giá trị của nó đáp ứng quy tắc thiết kế. **Bất biến (invariant)** là những điều kiện phải đúng sau khi constructor khởi tạo thành công và sau mỗi thao tác công khai hoàn tất bình thường.

Ví dụ, một đặt chỗ có thể yêu cầu mã tham chiếu không rỗng, số chỗ còn lại không âm, và mỗi yêu cầu đặt phải có số lượng dương không vượt quá sức chứa còn lại. Nếu trạng thái trở nên sai, những phương thức sau đó có thể tiếp tục tính toán dựa trên dữ liệu không đáng tin cậy.

Abstract class có constructor, trường và phương thức bình thường; nó chỉ không cho phép tạo thể hiện trực tiếp bằng new. Khi tạo lớp con cụ thể, phần trạng thái của superclass vẫn nằm trong đối tượng. Vì vậy, **abstract class nên tự bảo vệ các invariant chung mà nó sở hữu**.

| Khái niệm | Câu hỏi cần trả lời |
| --- | --- |
| Precondition | Caller được phép truyền giá trị nào? |
| Invariant | Trạng thái nào phải luôn đúng? |
| Postcondition | Sau thao tác thành công, kết quả phải ra sao? |
| Encapsulation | Ai được đọc hoặc sửa trạng thái và qua API nào? |

## 1. Kiểm tra ở constructor và mọi điểm cập nhật

Constructor là nơi phù hợp để xác thực dữ liệu bắt buộc và khởi tạo trường do lớp quản lý. Nếu tham số vi phạm hợp đồng, hãy ném exception rõ ràng thay vì tạo một đối tượng đã sai ngay từ đầu.

Tuy nhiên, **constructor chỉ bảo vệ điểm bắt đầu**. Mọi phương thức thay đổi trạng thái cũng phải duy trì invariant. Nếu chỉ kiểm tra lúc tạo nhưng sau đó cho phép gán số chỗ âm, đối tượng vẫn có thể trở nên sai.

- Dùng IllegalArgumentException cho giá trị không thỏa điều kiện nghiệp vụ.
- Dùng Objects.requireNonNull khi một tham chiếu bắt buộc khác null và thông báo NullPointerException là phù hợp.
- Constructor protected thường phù hợp cho abstract superclass chỉ được khởi tạo thông qua lớp con.
- Tránh công bố this qua callback hoặc registry trước khi đối tượng hoàn tất khởi tạo.

## 2. Ví dụ chạy được: đặt chỗ với invariant

Lưu vào **ReservationDemo.java**:

~~~java
abstract class Reservation {
    private final String reference;
    private int available;

    protected Reservation(String reference, int available) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException(
                    "reference must not be blank");
        }
        if (available < 0) {
            throw new IllegalArgumentException(
                    "available must be non-negative");
        }
        this.reference = reference;
        this.available = available;
    }

    public final String reference() {
        return reference;
    }

    public final int available() {
        return available;
    }

    public final void reserve(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "count must be positive");
        }
        if (count > available) {
            throw new IllegalStateException("not enough seats");
        }
        available -= count;
    }

    protected abstract String type();

    public final String description() {
        return type() + " " + reference + ": "
                + available + " seats";
    }
}

final class ConferenceReservation extends Reservation {
    ConferenceReservation(String reference, int available) {
        super(reference, available);
    }

    @Override
    protected String type() {
        return "Conference";
    }
}

public class ReservationDemo {
    public static void main(String[] args) {
        Reservation reservation =
                new ConferenceReservation("EV-24", 5);

        System.out.println(reservation.description());
        reservation.reserve(2);
        System.out.println(reservation.description());

        try {
            reservation.reserve(4);
        } catch (IllegalStateException error) {
            System.out.println("Rejected: " + error.getMessage());
        }
        System.out.println("Remaining: " + reservation.available());

        try {
            new ConferenceReservation(" ", 3);
        } catch (IllegalArgumentException error) {
            System.out.println("Invalid: " + error.getMessage());
        }
    }
}
~~~

Chạy:

~~~bash
javac ReservationDemo.java
java ReservationDemo
~~~

Stdout:

~~~text
Conference EV-24: 5 seats
Conference EV-24: 3 seats
Rejected: not enough seats
Remaining: 3
Invalid: reference must not be blank
~~~

Constructor Reservation xác thực mã và số chỗ trước khi lưu. reserve kiểm tra count và sức chứa **trước khi trừ**, nên yêu cầu thất bại không để lại số chỗ âm. Biến tham chiếu có kiểu Reservation nhưng đối tượng thực tế là ConferenceReservation; abstract class vẫn có thể làm kiểu tham chiếu.

## 3. Đóng gói để bảo vệ trạng thái

Nếu available là protected, lớp con có thể gán available = -99 và phá invariant mà không đi qua reserve. Hãy giữ trường mutable là private và cung cấp hành vi có ý nghĩa nghiệp vụ thay vì setter tùy ý.

- **Sở hữu dữ liệu rõ ràng:** không trả trực tiếp tham chiếu đến cấu trúc mutable nội bộ nếu caller có thể sửa nó.
- **Kiểm tra trước khi sửa:** xác thực đầu vào và trạng thái hiện tại, rồi mới cập nhật.
- **Hạn chế điểm mở rộng:** dùng final cho phương thức bảo vệ invariant khi lớp con không được phép thay đổi quy tắc đó.
- **Đừng nhầm final với thread-safe:** final ngăn override hoặc gán lại theo quy tắc Java, nhưng không tự làm thao tác nhiều bước trở thành nguyên tử.

Với trường tham chiếu mutable, có thể cần defensive copy. Ví dụ dưới đây minh họa danh sách nhãn:

~~~java
import java.util.List;

abstract class TaggedResource {
    private final List<String> tags;

    protected TaggedResource(List<String> tags) {
        this.tags = List.copyOf(tags);
    }

    public final List<String> tags() {
        return tags;
    }
}
~~~

List.copyOf từ chối danh sách null hoặc phần tử null và cung cấp danh sách không thể sửa qua API List. Vì String bất biến, đây là cách bảo vệ phù hợp cho các nhãn. Với phần tử mutable, đây vẫn là bản sao nông; cần xem có phải sao chép từng phần tử hay dùng kiểu bất biến không.

## 4. Phối hợp invariant giữa superclass và subclass

Invariant của toàn đối tượng là tổng hợp quy tắc cho cả trạng thái cha và trạng thái con. Constructor cha xác thực dữ liệu cha sở hữu; constructor con xác thực dữ liệu riêng sau khi gọi super(...). Lớp con phải giữ hợp đồng quan sát được từ lớp cha và không mở đường cập nhật trái với quy tắc chung.

Ví dụ, nếu lớp con yêu cầu số chỗ luôn lớn hơn 0, kiểm tra điều kiện đó chỉ trong constructor con là chưa đủ: một lần gọi reserve tiếp theo có thể đưa số chỗ về 0. Mọi API thay đổi trạng thái liên quan phải bảo vệ cùng invariant. Đây là khác biệt giữa **hợp lệ khi mới tạo** và **hợp lệ suốt vòng đời**.

Cũng cần cẩn thận khi superclass gọi phương thức overridable trong constructor: phương thức con có thể chạy trước khi trường con được khởi tạo. Hãy giữ constructor tập trung vào tham số và trạng thái mà chính nó kiểm soát; bài về rủi ro gọi phương thức override trong constructor phân tích trường hợp này chi tiết hơn.

## 5. Giới hạn khi có nhiều luồng

Ví dụ Reservation bảo vệ invariant trong luồng gọi tuần tự. Nếu hai luồng cùng gọi reserve, bước kiểm tra available và bước trừ không tự động trở thành một thao tác nguyên tử. Muốn hỗ trợ nhiều luồng, cần đồng bộ hóa phù hợp như khóa hoặc thiết kế concurrency khác, kèm kiểm thử đúng. Một phương thức final không tự đồng nghĩa với thread-safe.

## 6. Kiểm thử và lỗi thường gặp

| Ca kiểm tra | Kết quả mong đợi |
| --- | --- |
| Mã null, rỗng hoặc chỉ khoảng trắng | Từ chối |
| available âm | Từ chối |
| available bằng 0 với mã hợp lệ | Chấp nhận theo hợp đồng ví dụ |
| reserve(0) hoặc reserve(-1) | Từ chối, giữ trạng thái cũ |
| Đặt nhiều hơn số chỗ còn lại | Từ chối, giữ trạng thái cũ |
| Đặt số lượng hợp lệ | Giảm đúng lượng, không âm |
| Lớp con truy cập trực tiếp available | Lỗi biên dịch vì trường private |
| Nhiều luồng đặt chỗ đồng thời | Chỉ tuyên bố an toàn khi cơ chế đồng bộ đã được kiểm chứng |

Lỗi thường gặp là chỉ xác thực setter mà quên constructor; chỉ xác thực constructor mà quên phương thức cập nhật; dùng assert cho dữ liệu nghiệp vụ; để trường mutable protected; hoặc thay đổi nhiều trường trước khi kiểm tra xong khiến đối tượng ở trạng thái nửa chừng khi có exception.

**Ghi nhớ:** abstract class là nơi thích hợp để thiết lập invariant chung của một họ lớp. Constructor xác nhận điểm bắt đầu; đóng gói, API cập nhật có kiểm tra và kiểm soát điểm mở rộng giữ cho trạng thái hợp lệ về sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-initialization-005">Rủi ro gọi phương thức có thể bị ghi đè trong constructor</a>

<details>
<summary>Xem chi tiết</summary>

# Rủi ro gọi phương thức có thể bị ghi đè trong constructor của Java

## 1. Vấn đề cốt lõi

Trong Java, constructor có trách nhiệm đưa đối tượng về trạng thái hợp lệ trước khi mã bên ngoài bắt đầu sử dụng nó. Tuy nhiên, **nếu constructor của lớp cha gọi một phương thức instance có thể bị override**, Java vẫn thực hiện **phân phối động (dynamic dispatch)** đến phương thức được ghi đè ở lớp con, dù constructor của lớp con **chưa hoàn tất**, thậm chí những bộ khởi tạo trường của lớp con **chưa chạy**.

Hệ quả thường gặp: phương thức override đọc các trường của lớp con khi chúng còn giữ **giá trị mặc định** (`null`, `0`, `false`). Kết quả có thể là trạng thái sai, log sai, `NullPointerException`, vi phạm bất biến của đối tượng, hoặc hành vi khó tái hiện.

Quy tắc thực hành quan trọng nhất:

> **Không gọi phương thức có thể bị override trên `this` khi constructor đang chạy, kể cả gọi gián tiếp qua một phương thức khác.** Hãy hoàn tất khởi tạo trước rồi mới thực thi các bước mở rộng đa hình.

Vấn đề đặc biệt dễ gặp với **abstract class**, bởi constructor của lớp trừu tượng vẫn chạy khi tạo đối tượng lớp con; phương thức `abstract` khai báo trong lớp cha có thể bị gọi ngay từ constructor ấy và thực thi ở lớp con lúc lớp con chưa khởi tạo xong.

## 2. Vì sao có thể xảy ra? Kết hợp thứ tự khởi tạo với đa hình

Khi thực thi `new Derived()` với `Derived extends Base`, JVM tạo một đối tượng có kiểu thực tế là `Derived`, chứ không tạo trước một đối tượng `Base` độc lập rồi thay thế nó. Vùng nhớ cho các trường instance của **toàn bộ** đối tượng được cấp phát trước và có giá trị mặc định.

Với trường hợp kế thừa thông thường, không có lời gọi `this(...)` phức tạp:

1. **Cấp phát bộ nhớ** cho đối tượng `Derived` và gán giá trị mặc định cho các trường instance trên toàn chuỗi kế thừa: tham chiếu `null`, số `0`, `boolean` là `false`.
2. **Đi theo chuỗi constructor** từ lớp con lên `super(...)`; constructor của lớp cao nhất chạy trước. Đối với từng lớp, sau khi constructor lớp cha hoàn thành, các bộ khởi tạo trường instance và instance initializer block của lớp đó mới chạy theo thứ tự xuất hiện trong mã, rồi thực thi phần thân constructor của lớp đó.
3. Khi đang ở **constructor `Base`**, các trường của `Derived` vẫn chưa chạy initializer của `Derived` hoặc phần thân constructor `Derived`.
4. Nhưng nếu `Base` gọi một instance method có thể override, Java dùng **kiểu thực tế của đối tượng** để chọn bản override, nên có thể nhảy thẳng vào `Derived.method()`.
5. Chỉ sau khi constructor `Base` trả về, việc khởi tạo trường và phần thân constructor `Derived` mới tiếp tục.

Điều này không trái với nguyên tắc `super(...)` chạy trước: **thứ tự constructor** và **cơ chế chọn phương thức override** là hai quy tắc khác nhau. Lời gọi `super(...)` xác định cách khởi tạo phần lớp cha; nó không vô hiệu hóa virtual dispatch.

Giá trị mặc định có thể bị quan sát trong override ngay cả khi trường lớp con khai báo initializer tại nơi định nghĩa, chẳng hạn `private String name = "Đã sẵn sàng";`. Dòng gán ấy được thực thi **sau** khi constructor của lớp cha kết thúc.

Lưu ý: static initialization là cơ chế riêng của lớp và không thay đổi quy tắc nguy hiểm đối với các trường instance trong ví dụ này. Constructor có gọi `this(...)` sẽ ủy quyền sang constructor khác của cùng lớp; dù có biến thể thứ tự, invariant quan trọng vẫn giữ nguyên: **không giả định phần lớp con đã khởi tạo xong khi mã lớp cha đang chạy**.

## 3. Ví dụ Java 17+ tái hiện lỗi và cách sửa

Ví dụ sau đặt cả trường hợp nguy hiểm và cách tổ chức an toàn vào **một tệp `Main.java`**. Đầu tiên, constructor lớp cha gọi một phương thức `abstract` và bản override nhìn thấy `null`. Sau đó, phương án an toàn chỉ kiểm tra phần trạng thái mà chính lớp cha đã khởi tạo, rồi chạy hook của lớp con sau khi toàn bộ constructor hoàn tất.

```java
import java.util.Objects;

// CÁCH KHÔNG AN TOÀN: constructor gọi một phương thức overridable.
abstract class HazardBase {
    protected HazardBase() {
        System.out.println("1. HazardBase(): đang khởi tạo");
        printStatus(); // Dynamic dispatch xảy ra ngay tại đây.
    }

    protected abstract void printStatus();
}

final class HazardChild extends HazardBase {
    // Initializer này CHƯA chạy khi HazardBase() gọi printStatus().
    private String status = "ready";

    @Override
    protected void printStatus() {
        System.out.println("2. Override: status = " + status);
    }

    String getStatus() {
        return status;
    }
}

// CÁCH AN TOÀN: constructor chỉ xử lý phần trạng thái của lớp cha.
abstract class SafeBase {
    private final String category;

    protected SafeBase(String category) {
        this.category = Objects.requireNonNull(category, "category");
        verifyBaseState(); // private, không bị override.
    }

    private void verifyBaseState() {
        System.out.println("4. SafeBase(): category = " + category);
    }

    // Gọi phương thức đa hình sau khi đối tượng đã tạo xong.
    public final void start() {
        System.out.println("6. start(): category = " + category);
        perform();
    }

    protected abstract void perform();
}

final class SafeChild extends SafeBase {
    private final String status;

    SafeChild() {
        super("TASK");
        status = "ready";
        System.out.println("5. SafeChild(): status = " + status);
    }

    @Override
    protected void perform() {
        System.out.println("7. perform(): status = " + status);
    }
}

public class Main {
    public static void main(String[] args) {
        HazardChild hazard = new HazardChild();
        System.out.println("3. Sau new: status = " + hazard.getStatus());

        SafeChild safe = new SafeChild();
        safe.start();
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 Main.java
java Main
```

Đầu ra chính xác:

```text
1. HazardBase(): đang khởi tạo
2. Override: status = null
3. Sau new: status = ready
4. SafeBase(): category = TASK
5. SafeChild(): status = ready
6. start(): category = TASK
7. perform(): status = ready
```

**Phân tích dòng 1–3:**

- Trong `new HazardChild()`, constructor `HazardBase()` chạy trước; nó in dòng 1 rồi gọi `printStatus()`.
- `printStatus()` là phương thức trừu tượng ở lớp cha nhưng đã được `HazardChild` triển khai. Lời gọi chọn `HazardChild.printStatus()` theo kiểu thực tế của đối tượng.
- Khi override đọc `status` ở dòng 2, phép gán `"ready"` trong initializer của lớp con chưa được thực thi. Trường `String` đang là `null`.
- Sau khi constructor lớp cha trả về, initializer của `HazardChild` chạy và gán `status = "ready"`. Dòng 3 vì vậy in `ready`.
- Từ khóa `final` trên lớp `HazardChild` không giúp ngăn vấn đề, vì việc override đã diễn ra **giữa `HazardChild` và `HazardBase`**.

**Phân tích dòng 4–7:**

- `SafeBase()` chỉ gán trường của chính nó và gọi `verifyBaseState()` là `private`, nên phương thức này không thể bị lớp con override.
- `SafeChild()` tiếp tục khởi tạo `status` và kết thúc. Trong `main`, chỉ **sau khi `new SafeChild()` trả về**, chương trình mới gọi `safe.start()`.
- `start()` là `final`, giữ cố định thứ tự công việc; nó gọi hook `perform()` đa hình khi `status` đã là `"ready"`. Lúc này việc mở rộng qua lớp con là hợp lý.
- `final` chỉ ngăn override trực tiếp phương thức `start()`; nó **không tự khiến mọi lời gọi bên trong `start()` trở nên an toàn trong constructor**. Sự an toàn ở đây đến từ thời điểm gọi `start()` sau khởi tạo.

Ví dụ dùng `System.out.println` để quan sát chính xác thứ tự, nên không phụ thuộc lịch chạy luồng hoặc yếu tố ngẫu nhiên.

## 4. Lỗi thực tế có thể nghiêm trọng hơn giá trị `null`

### 4.1. `NullPointerException`

Một phương thức override có thể giả định trường lớp con đã được gán:

```java
// Đoạn minh họa bên trong một lớp con:
private String configuration = "production";

@Override
protected void printStatus() {
    System.out.println(configuration.length());
}
```

Nếu constructor lớp cha gọi `printStatus()` quá sớm, `configuration` vẫn là `null`. Lời gọi `configuration.length()` sẽ ném `NullPointerException` và việc tạo đối tượng thất bại. Vì đây chỉ là đoạn minh họa trong một lớp con giả định, nó không phải một tệp Java độc lập; ví dụ `Main.java` ở trên mới là chương trình có thể chạy trực tiếp.

### 4.2. Giá trị mặc định trông có vẻ hợp lệ

Với trường số nguyên hoặc boolean, lỗi đôi khi **không ném exception**, mà âm thầm tính toán sai:

```java
// Đoạn minh họa trong lớp con:
private int retryLimit = 3;
private boolean enabled = true;

@Override
protected void printStatus() {
    System.out.println("retryLimit=" + retryLimit
            + ", enabled=" + enabled);
}
```

Nếu được gọi quá sớm, override sẽ nhìn thấy `retryLimit=0, enabled=false`. Vì `0` và `false` là giá trị hợp lệ về mặt kiểu dữ liệu, lỗi có thể len vào quyết định nghiệp vụ hoặc bị ghi lại thành dữ liệu sai mà không báo lỗi ngay.

### 4.3. Khi lời gọi sớm đồng thời làm `this` thoát ra

Nếu override được gọi từ constructor rồi đăng ký `this` với listener hoặc registry, hoặc lên lịch tác vụ giữ tham chiếu đối tượng, mã khác có thể sử dụng đối tượng trước khi khởi tạo xong. Tuy nhiên, **dispatch sớm không tự động có nghĩa là tham chiếu đã bị công bố**. Cần kiểm tra hai cơ chế riêng: phương thức nào chạy quá sớm và đối tượng có thoát khỏi constructor hay không.

Bài “Tránh để this thoát ra khi đối tượng chưa khởi tạo xong” tập trung riêng vào callback, thread, safe publication và quan hệ happens-before.

## 5. Những cách gọi gián tiếp thường bị bỏ sót

Chỉ tìm câu lệnh `overridableMethod();` trong constructor là chưa đủ. Cần xem **toàn bộ chuỗi lời gọi xuất phát từ constructor**.

| Dạng code trong constructor | Vì sao cần kiểm tra |
| --- | --- |
| `initialize();`, trong đó `initialize()` lại gọi `hook()` | Constructor không gọi `hook()` trực tiếp nhưng vẫn dẫn tới override. |
| `setValue(x);` với setter không `final` | Lớp con có thể override setter và đọc trạng thái chưa hoàn chỉnh. |
| `System.out.println(this);` hoặc `"obj=" + this` | Chuyển đối tượng thành chuỗi có thể gọi `toString()` đã bị override. |
| `Objects.toString(this)`, logging object, debugger display | Những thao tác tưởng như chỉ để quan sát có thể gọi `toString()`. |
| `registerListener(this)` | Đối tượng có thể được callback gọi lại khi constructor vẫn đang chạy. |
| `executor.execute(() -> useFields())` hoặc tạo thread dùng `this` | Tác vụ bất đồng bộ có thể chạy sớm và đọc trạng thái chưa khởi tạo. |

Một số chi tiết cần phân biệt:

- Gọi `super.method()` từ lớp con chọn phần triển khai ở lớp cha đối với **lời gọi cụ thể đó**, nhưng method lớp cha vẫn có thể tiếp tục gọi một hook ảo khác. `super` không bảo đảm cả chuỗi lời gọi không có dispatch.
- `private` method không bị override; `final` instance method không bị override; `static` method không dispatch như instance method (trùng tên có thể là *hiding*). Nhưng **một phương thức `private` hoặc `final` vẫn có thể gọi phương thức overridable**, nên phải kiểm tra lời gọi bên trong.
- `getClass()` và kiểu thực tế của `this` đã là kiểu lớp con khi chạy trong constructor lớp cha. Không nên suy luận rằng đối tượng “đang là lớp cha” trong giai đoạn ấy.
- Một phương thức được gọi từ constructor còn có thể truy cập trạng thái của lớp con **qua method reference, lambda, callback hoặc listener**. Nguy cơ đến từ thời điểm thực thi và truy cập đối tượng, không đơn thuần từ cách viết cú pháp.

## 6. Các cách thiết kế an toàn hơn

### Cách A — Constructor tự hoàn tất dữ liệu của lớp mình

Constructor lớp cha nên thực hiện các công việc có thể quyết định hoàn toàn từ tham số và trường đã khởi tạo của chính lớp cha: kiểm tra tham số, gán trường, thiết lập bất biến, hoặc gọi helper `private`/`final` được kiểm soát cẩn thận.

Ví dụ:

```java
// Đoạn minh họa về constructor chỉ xử lý trạng thái nội bộ.
abstract class ConnectionBase {
    private final String endpoint;

    protected ConnectionBase(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint bắt buộc");
        }
        this.endpoint = endpoint;
    }

    protected final String endpoint() {
        return endpoint;
    }
}
```

Không cần hỏi lớp con xem `endpoint` đã hợp lệ chưa thông qua một hook có thể override. Nếu dữ liệu là tham số constructor, kiểm tra trực tiếp ở nơi nhận dữ liệu thường đơn giản và an toàn hơn.

### Cách B — Tách phần mở rộng sang phương thức được gọi sau `new`

Mẫu Template Method có thể duy trì một quy trình mở rộng rõ ràng, nhưng phương thức khuôn phải được gọi sau khi constructor của lớp con đã chạy xong:

```java
// Đoạn minh họa; các lớp cụ thể sẽ cung cấp executeStep().
abstract class Job {
    public final void run() {
        checkInputs();
        executeStep();
    }

    private void checkInputs() {
        System.out.println("Đã kiểm tra");
    }

    protected abstract void executeStep();
}
```

Luồng sử dụng đúng là tạo một `Job` cụ thể **xong rồi** gọi `run()`. Không gọi `run()` trong constructor `Job`, bởi nó vẫn dẫn tới `executeStep()` đa hình. Nếu có nhiều phương thức gọi gián tiếp, điểm gọi công khai sau khởi tạo vẫn là ranh giới thiết kế dễ kiểm soát hơn.

### Cách C — Cần bước khởi động bắt buộc: dùng factory với hậu khởi tạo rõ ràng

Khi ứng dụng yêu cầu khởi động đối tượng ngay lúc tạo, có thể đặt một phương thức factory kiểm soát thứ tự: tạo đối tượng hoàn chỉnh rồi mới gọi bước khởi động.

```java
// Đoạn minh họa độc lập về ý tưởng factory, không phụ thuộc ví dụ Main.
final class Session {
    private final String user;
    private boolean started;

    private Session(String user) {
        this.user = java.util.Objects.requireNonNull(user);
    }

    public static Session open(String user) {
        Session session = new Session(user);
        session.startInternal();
        return session;
    }

    private void startInternal() {
        started = true;
    }

    public String status() {
        return user + ":" + (started ? "started" : "new");
    }
}
```

Với lớp được thiết kế để kế thừa, factory cũng có thể tạo xong một instance cụ thể rồi gọi hook cần thiết sau khi constructor trả về. Phải kiểm soát việc công bố tham chiếu và đường khởi tạo: nếu đối tượng đã bị lộ cho mã khác **trước** bước hậu khởi tạo, factory chưa giải quyết hoàn toàn vấn đề. Nếu việc khởi động có thể thất bại, cần quy định rõ liệu đối tượng bị lỗi có được trả ra hay phải được dọn dẹp.

### Cách D — Dùng composition nếu đa hình trong lúc khởi tạo không cần thiết

Thay vì để constructor lớp cha dựa trên một method override của lớp con, truyền một dependency đã sẵn sàng qua constructor hoặc chuyển logic biến thiên sang một đối tượng cộng tác. Khi có thể kiểm tra và cung cấp dependency từ bên ngoài, mã khởi tạo không cần dispatch trên `this` chưa hoàn thiện.

Chú ý: dependency được truyền vào cũng có thể gọi callback ngược đến `this` nếu constructor cung cấp tham chiếu ấy. Composition giúp thu hẹp rủi ro thiết kế, nhưng không tự xóa mọi lỗi nếu vẫn để `this` thoát ra quá sớm.

## 7. Một số hiểu lầm cần tránh

**“Constructor không được override, nên không thể gọi override bên trong.”** Đúng là constructor không tham gia override, nhưng **phương thức instance được constructor gọi** vẫn có thể là virtual method và được phân phối động.

**“Lớp con đã khai báo initializer nên trường chắc chắn có giá trị.”** Sai. Initializer trường lớp con chỉ chạy ở bước khởi tạo lớp con, sau khi constructor lớp cha trả về.

**“Đổi trường thành `final` là đủ.”** Sai. `final` trên trường ngăn gán lại sau khi được khởi tạo (theo các quy tắc gán của Java); nó không làm bước gán trường lớp con chạy sớm hơn. Override ở constructor lớp cha vẫn có thể thấy giá trị mặc định.

**“Đổi constructor sang `private` hoặc đổi method khung sang `final` là xong.”** Phải xét đúng nơi dispatch: `private` helper không thể bị override, nhưng helper ấy có thể gọi một hook ảo. `final` trên method khung chỉ ngăn override khung; nếu khung gọi method khác có thể override, nguy cơ còn nguyên.

**“Lỗi này chỉ xảy ra với abstract method.”** Không. Bất kỳ **instance method có thể override** nào gọi từ constructor (kể cả method cụ thể ở base class hoặc `toString()`) đều có thể chọn bản ghi đè ở lớp con.

**“Thấy `null` chứng tỏ Java chưa cấp phát đối tượng con.”** Sai. Toàn bộ đối tượng đã được cấp phát với các giá trị mặc định; phần khởi tạo riêng của lớp con mới là thứ chưa thực thi.

**“Tôi phải thêm `super.printStatus()` trong override để sửa.”** Không đáng tin cậy. Vấn đề là **thời điểm gọi**; gọi thêm phương thức lớp cha không bảo đảm trạng thái lớp con đã sẵn sàng. Cần thay đổi điểm thực thi hoặc thiết kế quan hệ khởi tạo.

## 8. Checklist rà soát code và kiểm thử

Khi review một abstract class hoặc bất kỳ class nào được phép kế thừa:

1. **Đọc các constructor và chuỗi `this(...)`/`super(...)`**. Ghi lại mọi phương thức instance được gọi, kể cả qua helper.
2. **Phân loại từng method được gọi:** `private`, `final`, `static` hay overridable? Với helper `private`/`final`, tiếp tục xem bên trong có gọi hook hoặc publish `this` không.
3. **Xem các override ở lớp con:** chúng đọc trường nào và trường ấy được gán lúc nào? Phân biệt giá trị mặc định với giá trị do field initializer hoặc constructor gán.
4. **Tìm đường thoát của `this`:** callback, listener, đăng ký vào registry, executor, lambda hay khởi động thread.
5. **Chuyển việc cần đa hình sang pha sau khởi tạo**, hoặc gom vào dữ liệu đầu vào constructor và helper chỉ thao tác trạng thái đã có.
6. **Thử các trạng thái khiến lỗi lộ rõ:** trường tham chiếu `null`, trường số bằng `0`, boolean `false`, constructor có nhiều cấp kế thừa. Xác minh cả trường hợp không có exception nhưng đầu ra sai.
7. **Xác minh thứ tự bằng chương trình nhỏ** như ví dụ `Main.java`; phân biệt log trong constructor lớp cha, initializer lớp con và logic chạy sau `new`.

Nếu dùng công cụ phân tích mã nguồn, cảnh báo tương đương “constructor invokes overridable method” là tín hiệu để kiểm tra. Không phải mọi lời gọi đều dẫn tới lỗi nhìn thấy ngay, nhưng khi có lớp con và override trong tương lai, rủi ro vẫn tồn tại; API công khai có khả năng mở rộng càng cần tránh thiết kế này.

## 9. Bài tập tự kiểm tra hiểu biết

**Bài 1.** Giả sử `Base()` gọi `getCount()` và lớp con override `getCount()` để trả về `count`, trong khi `count` được khai báo `private int count = 10;`. Khi `Base()` chạy, lời gọi có thể trả về bao nhiêu? **Đáp án: `0`** vì initializer của lớp con chưa chạy; sau khi hoàn thành `new`, giá trị mới là `10`.

**Bài 2.** Thay `getCount()` bằng một helper `private int getCount()` trong chính lớp cha đã đủ loại bỏ dispatch vào override của lớp con chưa? **Có, đối với chính lời gọi helper đó**, vì `private` method không override được. Nhưng vẫn phải kiểm tra helper có gọi phương thức overridable hoặc làm lộ `this` hay không.

**Bài 3.** Nếu lớp cha có phương thức `public final void initialize()`, bên trong gọi `configure()` là `protected abstract`, liệu có an toàn để gọi `initialize()` trong constructor lớp cha không? **Không.** `initialize()` tuy `final` vẫn thực thi `configure()` đa hình. Đưa lời gọi `initialize()` ra sau khi `new` hoàn thành.

**Bài 4.** Có nên cấm tất cả phương thức instance trong constructor không? **Không cần.** Constructor có thể gọi helper không overridable, làm việc với trạng thái đã khởi tạo, không làm lộ `this`. Quy tắc cần tránh là gọi vào logic có thể truy cập phần lớp con chưa sẵn sàng hoặc tạo cơ hội cho mã bên ngoài nhìn thấy đối tượng chưa hoàn chỉnh.

## 10. Ghi nhớ

- Constructor lớp cha được thực thi trước phần khởi tạo riêng của lớp con, nhưng **dynamic dispatch đã hoạt động theo kiểu thực tế của đối tượng** ngay khi constructor lớp cha chạy.
- Trường lớp con chưa khởi tạo có thể được quan sát qua override: **`null`, `0`, `false`** thay vì giá trị mà lập trình viên mong đợi.
- Gọi **trực tiếp hoặc gián tiếp** phương thức overridable từ constructor đều có thể nguy hiểm; `abstract`, `toString()`, setter và hook trong một method `final` đều cần kiểm tra.
- Giữ constructor tập trung vào trạng thái do chính nó kiểm soát; đưa hook đa hình sang phương thức được gọi **sau khi toàn bộ quá trình `new` hoàn thành**. Không publish `this` quá sớm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-initialization-006">Tránh để this thoát ra khi đối tượng chưa khởi tạo xong</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh để this thoát ra khi đối tượng chưa khởi tạo xong trong Java

**This escape** xảy ra khi tham chiếu đến đối tượng đang xây dựng được đưa cho mã khác trước khi quá trình khởi tạo hoàn tất. Mã nhận tham chiếu có thể gọi phương thức hoặc đọc trường khi đối tượng còn ở trạng thái trung gian.

Đây là lỗi khác với việc một constructor gọi phương thức override: hai rủi ro có thể cùng xuất hiện, nhưng **gọi override sớm không tự động chứng minh rằng this đã thoát ra ngoài**. Bài này tập trung vào callback, listener, registry, thread/task và bảo đảm an toàn khi công bố đối tượng giữa các luồng.

## 1. Thời điểm khởi tạo có ý nghĩa gì?

Khi cấp phát một đối tượng, các trường instance ban đầu có giá trị mặc định: tham chiếu là null, số là 0, boolean là false. Sau đó chạy chuỗi constructor và initializer theo quy tắc Java. Một trường final cũng chỉ nhận giá trị được gán khi phép gán tương ứng thực thi.

~~~java
final class Settings {
    private final String endpoint;
    private int retryCount = 3;

    Settings(String endpoint) {
        this.endpoint = endpoint;
    }
}
~~~

Trước khi dòng gán endpoint chạy, trường đó vẫn có giá trị mặc định null. Constructor có nhiệm vụ kiểm tra dữ liệu và đưa đối tượng đến trạng thái hợp lệ **trước khi cho mã bên ngoài sử dụng nó**.

## 2. Những đường thoát phổ biến

| Mẫu trong constructor | Vì sao cần kiểm tra |
| --- | --- |
| `registry.register(this)` | Registry có thể giữ tham chiếu hoặc gọi đối tượng ngay. |
| `eventBus.subscribe(this::onEvent)` | Method reference giữ receiver là this; callback có thể chạy trước khi các trường sẵn sàng. |
| `eventBus.subscribe(e -> this.handle(e))` | Lambda cũng có thể đóng trên this. |
| `new Thread(this::run).start()` | Luồng mới có thể đọc trạng thái khi constructor chưa xong. |
| `executor.execute(this::run)` | Executor có thể thực thi ngay hoặc chuyển việc sang luồng khác. |
| Gán this vào biến static hoặc collection dùng chung | Mã ngoài có thể tìm thấy đối tượng chưa hoàn chỉnh. |
| Truyền this cho dependency khác | Dependency có thể lưu tham chiếu hoặc kích hoạt callback. |

Không phải mọi lần viết this trong constructor đều là escape. `this.endpoint = endpoint` hoặc một helper private chỉ xử lý trường cục bộ, không công bố tham chiếu, là các thao tác bình thường. Ngược lại, lambda không viết chữ this vẫn có thể giữ tham chiếu nếu nó gọi phương thức hoặc đọc trường instance.

## 3. Ví dụ chạy được: callback nhìn thấy trạng thái chưa hoàn tất

Lưu vào **EscapeDemo.java**:

~~~java
import java.util.Objects;
import java.util.function.Consumer;

public class EscapeDemo {
    static final class CallbackBus {
        private Consumer<String> listener;

        void subscribe(Consumer<String> callback) {
            listener = Objects.requireNonNull(callback);
            listener.accept("subscribed");
        }
    }

    static final class UnsafeObserver {
        private String label;

        UnsafeObserver(CallbackBus bus) {
            bus.subscribe(this::onMessage);
            label = "ready";
        }

        private void onMessage(String event) {
            System.out.println(
                    "unsafe: label=" + label + ", event=" + event);
        }
    }

    static final class SafeObserver {
        private final String label;

        private SafeObserver(String label) {
            this.label = Objects.requireNonNull(label);
        }

        static SafeObserver create(CallbackBus bus, String label) {
            Objects.requireNonNull(bus);
            SafeObserver ready = new SafeObserver(label);
            bus.subscribe(ready::onMessage);
            return ready;
        }

        private void onMessage(String event) {
            System.out.println(
                    "safe: label=" + label + ", event=" + event);
        }
    }

    static class Base {
        Base() {
            System.out.println("override: " + describe());
        }

        String describe() {
            return "base";
        }
    }

    static final class Derived extends Base {
        private final String detail;

        Derived() {
            detail = "ready";
        }

        @Override
        String describe() {
            return detail;
        }
    }

    public static void main(String[] args) {
        new UnsafeObserver(new CallbackBus());
        SafeObserver.create(new CallbackBus(), "ready");
        new Derived();
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 EscapeDemo.java
java EscapeDemo
~~~

Kết quả xác định:

~~~text
unsafe: label=null, event=subscribed
safe: label=ready, event=subscribed
override: null
~~~

- **unsafe:** constructor đăng ký callback giữ tham chiếu đến đối tượng. CallbackBus gọi callback đồng bộ ngay trong subscribe, trước khi label = "ready" chạy, nên callback đọc null.
- **safe:** factory hoàn tất constructor SafeObserver rồi mới đăng ký callback. Callback gọi ngay vẫn thấy label đã sẵn sàng.
- **override:** Base() gọi describe(), Java dispatch đến Derived.describe() trước khi trường detail được gán. Đây là nguy cơ gọi override trong constructor; đoạn này không cho thấy tham chiếu đã được công bố ra registry hay luồng khác, nên cần phân biệt với this escape.

CallbackBus chỉ mô phỏng callback đồng bộ, không phải event bus đa luồng. Đầu ra xác định vì listener được gọi ngay trong subscribe.

## 4. Khác biệt giữa this escape và gọi override sớm

Gọi phương thức overridable từ constructor có thể chạy logic của lớp con khi trường lớp con còn mặc định. This escape xảy ra khi tham chiếu được đưa ra khỏi quá trình khởi tạo và mã khác có thể giữ hoặc dùng nó. Một tình huống có thể bao gồm cả hai, nhưng cần xác định đường đi thực tế.

Ví dụ, `System.out.println(this)` hoặc nối chuỗi với this có thể gọi toString() đã bị override; đây là lời gọi phương thức đáng kiểm tra. Để khẳng định escape, hãy xem đối tượng có được đưa cho logger/formatter hay dependency ngoài có thể giữ hoặc sử dụng nó hay không. Cách sửa cốt lõi là tránh hook đa hình sớm và tránh công bố tham chiếu trước khi invariant sẵn sàng.

## 5. Không khởi chạy thread hoặc task từ constructor

Đoạn sau minh họa một thiết kế nguy hiểm:

~~~java
final class UnsafeWorker {
    private String configuration;

    UnsafeWorker(java.util.concurrent.Executor executor) {
        executor.execute(() -> process(configuration));
        configuration = "loaded";
    }

    private void process(String value) {
        System.out.println("configuration=" + value);
    }
}
~~~

Nếu executor chạy tác vụ đồng bộ như direct executor, process nhận null chắc chắn. Với thread pool, kết quả còn phụ thuộc lịch thực thi và cơ chế đồng bộ; không được dựa vào giả định “task chắc chạy sau constructor”. Không dùng Thread.sleep để che vấn đề, vì chờ một khoảng thời gian không tạo ra invariant hoặc quan hệ happens-before đúng.

## 6. Cách an toàn: tạo xong rồi mới start

Lưu chương trình sau vào **SafeThreadDemo.java**:

~~~java
import java.util.Objects;

public class SafeThreadDemo {
    static final class Worker {
        private final String configuration;

        private Worker(String configuration) {
            this.configuration =
                    Objects.requireNonNull(configuration, "configuration");
        }

        static Thread start(String configuration) {
            Worker ready = new Worker(configuration);
            Thread thread = new Thread(ready::runJob, "worker");
            thread.start();
            return thread;
        }

        private void runJob() {
            System.out.println("Worker config=" + configuration);
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Thread worker = Worker.start("loaded");
        worker.join();
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 SafeThreadDemo.java
java SafeThreadDemo
~~~

Stdout:

~~~text
Worker config=loaded
~~~

Factory chỉ gọi Thread.start() sau khi constructor Worker hoàn tất. Theo Java Memory Model, hành động của luồng tạo trước Thread.start() happens-before hành động của luồng mới. Thread.join() cũng giúp main đợi worker kết thúc. Điều này bảo đảm trạng thái được gán trước start có thể được nhìn thấy theo hợp đồng của start; nó **không** khiến mọi thao tác mutable về sau tự thread-safe.

Mẫu tương tự áp dụng cho listener: tạo đối tượng xong, sau đó mới đăng ký callback. Nếu đăng ký thất bại, xác định cách xử lý lỗi và dọn tài nguyên; nếu listener giữ tham chiếu lâu dài, cung cấp cách unregister khi đối tượng dừng hoạt động.

## 7. final field, safe publication và giới hạn của chúng

Java có các bảo đảm đặc biệt cho final field khi đối tượng được xây dựng đúng cách và this không thoát ra trong lúc xây dựng. Tuy nhiên, các bảo đảm này không sửa được callback đọc trường trước khi phép gán xảy ra, và cũng không biến mọi trạng thái mutable thành thread-safe.

| Nhận định | Đánh giá |
| --- | --- |
| final String name luôn có giá trị cuối cùng ở mọi thời điểm constructor | Sai: trước phép gán, lời gọi sớm vẫn có thể thấy giá trị mặc định. |
| final khiến publish this từ constructor luôn an toàn | Sai: tránh escape sớm vẫn là điều kiện quan trọng. |
| final List&lt;String&gt; làm nội dung list bất biến | Sai: final ngăn gán lại tham chiếu, không tự ngăn sửa list. |
| volatile trên một trường làm cả đối tượng thread-safe | Sai: invariant nhiều trường và thao tác cần thiết kế đồng bộ phù hợp. |
| Xây xong rồi publish qua Thread.start(), lock hoặc cơ chế đồng bộ thích hợp | Cung cấp quan hệ visibility theo hợp đồng của cơ chế đó. |

Safe publication là đưa tham chiếu đã khởi tạo hoàn chỉnh sang mã khác qua cơ chế có bảo đảm visibility, chẳng hạn Thread.start(), khóa dùng nhất quán, volatile reference hoặc concurrent collection phù hợp. Sau khi publish, các truy cập mutable về sau vẫn phải được đồng bộ nếu nhiều luồng cùng sử dụng.

## 8. Callback, lambda và vòng đời đối tượng

Các biểu thức `this::onMessage` và `event -> onMessage(event)` có thể giữ receiver là đối tượng hiện tại. Lambda chỉ dùng biến cục bộ độc lập thì không nhất thiết capture this, nhưng dependency mà lambda gọi vẫn có thể có vấn đề vòng đời hoặc đồng bộ.

Với đối tượng cần đăng ký listener, mở socket hoặc khởi động tác vụ, có thể tách hai pha:

~~~text
constructor
   ↓
gán trường và kiểm tra invariant
   ↓
constructor hoàn tất
   ↓
register / start / publish
   ↓
active
   ↓
unregister / stop / close
~~~

Static factory có thể tập trung thứ tự này để caller không quên bước khởi chạy. Chỉ dùng mô hình start/stop khi đối tượng thực sự có vòng đời cần quản lý; không cần ép mọi đối tượng dữ liệu vào mô hình đó.

## 9. Checklist review và bài tập

Khi review constructor, hãy lần lượt hỏi:

1. Trạng thái nào phải đúng khi đối tượng sẵn sàng?
2. Có truyền this, this::method, lambda instance, listener hoặc đối tượng vào registry không?
3. Dependency có thể gọi callback đồng bộ ngay lúc đăng ký không?
4. Có thread/task nào bắt đầu trước khi constructor hoàn tất không?
5. Nếu có nhiều luồng, cơ chế nào cung cấp happens-before và bảo vệ các cập nhật sau đó?
6. Test có thể buộc callback chạy ngay để tái hiện lỗi một cách xác định không?
7. Có cần unregister hoặc dọn tài nguyên khi đối tượng dừng không?

**Tình huống A.** Constructor đăng ký `eventBus.register(this::onEvent)` trước khi gán handler, còn event bus gọi callback đồng bộ. Kết luận: callback có thể đọc handler null; chuyển đăng ký sang sau khi constructor hoàn tất.

**Tình huống B.** Constructor lớp cha gọi toString() bị lớp con override. Kết luận: có nguy cơ gọi override sớm; để kết luận this escape, cần xem tham chiếu có được đưa cho mã ngoài giữ hoặc sử dụng không.

**Tình huống C.** Factory tạo Worker, rồi mới gọi Thread.start(). Kết luận: các hành động trước start happens-before hành động trong luồng mới; điều đó không thay thế đồng bộ cho những thay đổi sau này.

**Ghi nhớ:** constructor chuẩn bị trạng thái; callback, listener, registry và thread/task chỉ nên nhận đối tượng sau khi toàn bộ constructor hoàn tất. Với nhiều luồng, đi cùng safe publication và cơ chế đồng bộ đúng cho các cập nhật về sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-initialization-007">Constructor không được kế thừa và không thể abstract</a>

<details>
<summary>Xem chi tiết</summary>

# Constructor không được kế thừa và không thể abstract trong Java

Trong Java, **constructor không được kế thừa** và **không thể khai báo abstract**, kể cả khi constructor nằm trong một abstract class. Hai quy tắc này không mâu thuẫn với việc constructor lớp cha vẫn chạy khi tạo lớp con.

- **Không được kế thừa:** Parent(int) không tự trở thành constructor Child(int). Mỗi lớp có các constructor của riêng mình.
- **Không thể abstract:** constructor cần có thân để tham gia quá trình khởi tạo. Java không có cơ chế override constructor để lớp con cung cấp phần thân còn thiếu.
- **Vẫn được gọi:** constructor Child có thể gọi constructor Parent trực tiếp qua super(...). Đây là chuỗi gọi constructor, không phải kế thừa constructor.

## 1. Constructor gắn với lớp khai báo

~~~java
class Parent {
    Parent() {}
    Parent(int value) {}
}

class Child extends Parent {
    // Compiler sinh Child() vì lớp này chưa khai báo constructor.
}
~~~

`new Child()` hợp lệ vì Child có constructor mặc định không tham số và Parent() có thể được gọi. Nhưng `new Child(10)` không hợp lệ: Parent(int) không được kế thừa thành Child(int).

Để tạo Child bằng một số nguyên, khai báo constructor của chính Child:

~~~java
class Child extends Parent {
    Child(int value) {
        super(value);
    }
}
~~~

`super(value)` gọi Parent(int) để khởi tạo phần superclass của cùng đối tượng Child. Nó không biến constructor cha thành constructor của Child.

## 2. Kế thừa, override, overload và chaining

| Cơ chế | Có áp dụng cho constructor? | Ý nghĩa |
| --- | --- | --- |
| Kế thừa | Không | Parent(int) không được thêm vào tập constructor của Child. |
| Override | Không | Constructor không tham gia dynamic dispatch và không dùng @Override. |
| Overload | Có | Một lớp có thể khai báo nhiều constructor với danh sách tham số khác nhau. |
| this(...) | Có | Gọi constructor khác trong cùng lớp. |
| super(...) | Có | Gọi constructor của superclass trực tiếp. |

Java chỉ sinh constructor mặc định không tham số khi lớp **không khai báo bất kỳ constructor nào**. Nếu đã khai báo Child(int), compiler không tự thêm Child(). Constructor mặc định được sinh cũng là constructor của chính lớp đó và chỉ hợp lệ nếu lời gọi constructor cha tương ứng hợp lệ.

Cũng đừng nhầm constructor với phương thức có tên trùng lớp nhưng có kiểu trả về. Ví dụ `void Child(int value)` là một phương thức, không phải constructor.

## 3. Abstract class có constructor có thân

Abstract class không thể được khởi tạo trực tiếp, nhưng có thể có nhiều constructor để khởi tạo trạng thái chung. Ví dụ sau chạy được; lưu thành **ConstructorRulesDemo.java**:

~~~java
abstract class Resource {
    private final String name;

    protected Resource(String name) {
        this.name = name;
        System.out.println("Resource(String): " + name);
    }

    protected Resource(String name, boolean verbose) {
        this(name);
        if (verbose) {
            System.out.println("Resource(String, boolean): verbose");
        }
    }

    public final String name() {
        return name;
    }

    public abstract String use();
}

final class FileResource extends Resource {
    private final String extension;

    FileResource(String name, String extension) {
        super(name, true);
        this.extension = extension;
        System.out.println("FileResource: " + extension);
    }

    @Override
    public String use() {
        return name() + "." + extension;
    }
}

public class ConstructorRulesDemo {
    public static void main(String[] args) {
        FileResource file = new FileResource("bao-cao", "txt");
        Resource resource = file;
        System.out.println("use: " + resource.use());
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 ConstructorRulesDemo.java
java ConstructorRulesDemo
~~~

Stdout:

~~~text
Resource(String): bao-cao
Resource(String, boolean): verbose
FileResource: txt
use: bao-cao.txt
~~~

Constructor FileResource gọi Resource(String, boolean) bằng super(...). Constructor đó gọi Resource(String) qua this(...), nên phần gán name chỉ tập trung ở một nơi. Sau khi chuỗi constructor cha hoàn tất, FileResource mới gán extension. Resource là abstract nhưng constructor có thân và vẫn chạy khi tạo FileResource.

## 4. Vì sao constructor không thể abstract?

Phương thức abstract mô tả hành vi chưa được cài đặt và yêu cầu lớp con cung cấp implementation. Constructor có vai trò khác: nó tham gia tạo và khởi tạo đối tượng, và phải đưa quá trình gọi constructor cha vào một chuỗi hợp lệ. Vì constructor không được override, không có cơ chế để chọn một “implementation constructor” ở runtime như với phương thức instance.

Khai báo sau không hợp lệ:

~~~java
abstract class InvalidResource {
    abstract InvalidResource(); // LỖI: constructor không thể abstract.
}
~~~

Cách đúng là để constructor có thân và tách hành vi cần lớp con hiện thực thành phương thức abstract:

~~~java
abstract class ResourceBase {
    protected ResourceBase() {
        // Khởi tạo trạng thái chung.
    }

    public abstract void use();
}

final class ConcreteResource extends ResourceBase {
    @Override
    public void use() {
        System.out.println("using");
    }
}
~~~

`ResourceBase()` khởi tạo phần chung; use() là hợp đồng hành vi mà lớp con cụ thể phải triển khai. Abstract class có constructor và constructor không thể abstract đều đúng.

## 5. Các lỗi biên dịch điển hình

| Tình huống | Nguyên nhân và cách xử lý |
| --- | --- |
| Child không khai báo constructor nhưng Parent chỉ có Parent(int) | Compiler định sinh Child() gọi super(), nhưng Parent() không tồn tại. Khai báo Child(int) gọi super(value). |
| Constructor cha private hoặc không truy cập được | Không có constructor superclass phù hợp mà subclass có quyền gọi. Kiểm tra access modifier và package. |
| Gắn @Override lên constructor | Constructor không override. Bỏ annotation và khai báo constructor riêng của Child. |
| Đặt lệnh thông thường trước super(...) trong Java 17 | Lời gọi constructor tường minh phải đứng đầu. Đưa công việc khác xuống sau super(...). |
| Gọi cả this(...) và super(...) trong cùng constructor | Chỉ chọn một lời gọi đầu tiên; constructor đích tiếp tục chuỗi. |
| Dùng modifier abstract, final hoặc static cho constructor | Các modifier đó không áp dụng cho constructor; chỉ dùng modifier truy cập và throws khi cần. |

Ví dụ thiếu constructor cha không tham số:

~~~java
class Base {
    Base(int id) {}
}

class Derived extends Base {
    // Lỗi: constructor mặc định Derived() sẽ gọi super().
}
~~~

Sửa bằng constructor tường minh:

~~~java
class Derived extends Base {
    Derived(int id) {
        super(id);
    }
}
~~~

Vấn đề là lời gọi super() không hợp lệ, không phải constructor Base(int) được kế thừa hoặc không được kế thừa.

## 6. Một cảnh báo liên quan đến đa hình

Constructor không override được, nhưng constructor cha có thể gọi một phương thức instance mà lớp con override. Khi đó Java vẫn dispatch theo lớp runtime, và trường của lớp con có thể chưa được khởi tạo. Tránh gọi hook overridable từ constructor; bài về rủi ro gọi phương thức override trong constructor trình bày ví dụ, hậu quả và cách thiết kế an toàn.

Đừng cố sửa bằng cách thêm @Override hoặc thay constructor bằng một phương thức tên gần giống. Điều cần thay đổi là điểm gọi của hành vi đa hình, hoặc thiết kế khởi tạo, chứ không phải cơ chế constructor inheritance.

## 7. Bài tập tự kiểm tra

**Câu 1.** Parent có Parent() và Parent(int). Child không khai báo constructor. Lệnh new Child() và new Child(5) có hợp lệ không?  
**Đáp án:** new Child() hợp lệ vì compiler sinh Child() gọi super(). new Child(5) không hợp lệ vì Child(int) không được kế thừa.

**Câu 2.** Abstract class Base chỉ có Base(String). Viết constructor cho Concrete để tạo new Concrete("A").  
**Đáp án:** Concrete(String name) { super(name); }.

**Câu 3.** Vì sao protected Base() hợp lệ nhưng abstract Base() không hợp lệ?  
**Đáp án:** Constructor có thể có thân và mức truy cập; constructor không thể abstract vì không được override. Hành vi chưa cài đặt nên được mô tả bằng phương thức abstract.

**Câu 4.** Có thể gọi cả this(...) và super(...) trực tiếp trong cùng constructor Java 17 không?  
**Đáp án:** Không. Nếu constructor gọi this(...), constructor được ủy quyền sẽ tiếp tục chuỗi đến superclass.

Hãy nhớ bốn điều: constructor không được kế thừa, không override, có thể overload, và có thể gọi constructor cha. Abstract class có constructor có thân; lớp con dùng super(...) để khởi tạo phần superclass và dùng phương thức abstract để cung cấp hành vi còn thiếu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-initialization-008">Factory tạo lớp cụ thể nhưng trả về kiểu trừu tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Factory tạo lớp cụ thể nhưng trả về kiểu trừu tượng trong Java

## Mục tiêu

Hiểu vì sao factory có thể gọi constructor của một lớp cụ thể nhưng khai báo kiểu trả về là `interface` hoặc `abstract class`; phân biệt kiểu tham chiếu và kiểu đối tượng lúc chạy; tự viết, sử dụng, kiểm thử và mở rộng một factory đơn giản trong Java 17+.

## 1. Bản chất: đối tượng cụ thể, hợp đồng trừu tượng

Trong Java, `interface` định nghĩa tập hành vi các lớp triển khai phải cung cấp. `abstract class` có thể định nghĩa hành vi chung, trạng thái và các phương thức abstract cho lớp con. Không thể khởi tạo trực tiếp interface hay abstract class bằng `new`; ta chỉ tạo đối tượng của **lớp cụ thể**.

Một đối tượng của lớp triển khai interface hoặc kế thừa abstract class có thể được tham chiếu bằng **kiểu trừu tượng tương ứng**:

```java
MessageSender sender = MessageSenderFactory.create("email");
sender.send("an@example.com", "Xin chào");
```

Đoạn trên minh họa cách sử dụng; định nghĩa đầy đủ của các lớp nằm ở mục 3. Factory có thể gọi `new EmailSender()` bên trong nhưng công bố chữ ký `static MessageSender create(String channel)`. Sau khi gán, `sender` là **biến kiểu `MessageSender`** chứa tham chiếu đến **đối tượng thực tế kiểu `EmailSender`**. Đối tượng không bị biến đổi thành interface.

Đây là upcasting hợp lệ: Java cho phép dùng đối tượng lớp con ở vị trí yêu cầu lớp cha/interface mà nó đáp ứng. Khi gọi `sender.send(...)`, Java chọn triển khai của lớp thực tế lúc chạy (**dynamic dispatch**). Trong khi đó, compiler dùng kiểu khai báo `MessageSender` để kiểm tra những phương thức mà mã người gọi được phép gọi.

Ví dụ, `EmailSender` có thêm phương thức riêng `configureSmtp()` nhưng `MessageSender` không có thì `sender.configureSmtp()` không biên dịch được. Việc ẩn API riêng giúp người gọi phụ thuộc vào hợp đồng chung.

## 2. Vì sao dùng factory?

Nếu mọi nơi tự `new EmailSender()` hay `new SmsSender()`, việc lựa chọn implementation bị rải ở các nơi sử dụng. Một factory tập trung quyết định tạo đối tượng:

1. Người gọi yêu cầu một khả năng, chẳng hạn `create("sms")`.
2. Factory kiểm tra đầu vào, chọn lớp cụ thể tương ứng.
3. Factory gọi `new SmsSender()` để khởi tạo đối tượng.
4. Factory trả về tham chiếu dưới kiểu `MessageSender`.
5. Người gọi sử dụng `send` mà không cần biết constructor hay lớp vừa được chọn.

Factory không loại bỏ sự tồn tại của lớp cụ thể: **chính factory vẫn biết** những lớp mà nó khởi tạo. Lợi ích là mã nghiệp vụ không cần đưa logic lựa chọn ấy vào mọi nơi. Nếu thay Email bằng một implementation khác vẫn đáp ứng hợp đồng, các đoạn chỉ phụ thuộc `MessageSender` thường giữ nguyên.

## 3. Chương trình Java 17+ chạy được

Tạo tệp `Main.java` với nội dung sau:

```java
import java.util.Locale;

public class Main {
    interface MessageSender {
        void send(String recipient, String message);
    }

    static final class EmailSender implements MessageSender {
        @Override
        public void send(String recipient, String message) {
            System.out.println("Email -> " + recipient + ": " + message);
        }
    }

    static final class SmsSender implements MessageSender {
        @Override
        public void send(String recipient, String message) {
            System.out.println("SMS -> " + recipient + ": " + message);
        }
    }

    static final class MessageSenderFactory {
        private MessageSenderFactory() {
            // Không cần tạo instance của lớp factory tiện ích này.
        }

        static MessageSender create(String channel) {
            if (channel == null) {
                throw new IllegalArgumentException("Kênh không được null");
            }

            return switch (channel.trim().toLowerCase(Locale.ROOT)) {
                case "email" -> new EmailSender();
                case "sms" -> new SmsSender();
                default -> throw new IllegalArgumentException(
                    "Kênh không hỗ trợ: " + channel
                );
            };
        }
    }

    public static void main(String[] args) {
        MessageSender email = MessageSenderFactory.create("email");
        MessageSender sms = MessageSenderFactory.create("sms");

        email.send("an@example.com", "Xin chào");
        sms.send("0900000000", "Xin chào");

        System.out.println("Lớp thực tế: " + email.getClass().getSimpleName());
    }
}
```

Chạy bằng JDK 17 trở lên, tại thư mục chứa file:

```bash
javac Main.java
java Main
```

Stdout:

```text
Email -> an@example.com: Xin chào
SMS -> 0900000000: Xin chào
Lớp thực tế: EmailSender
```

**Đọc mã theo quan hệ kiểu:**

- `MessageSender` là hợp đồng của người gọi: nó chỉ yêu cầu `send(String, String)`.
- `EmailSender` và `SmsSender` là lớp cụ thể có thể `new`. Mỗi lớp `implements MessageSender` và ghi đè `send`.
- `MessageSenderFactory.create` khai báo kiểu trả về là `MessageSender`. Mỗi nhánh `switch` trả một đối tượng của lớp cụ thể. Cả hai nhánh đều hợp lệ nhờ quan hệ `implements`.
- `channel.trim().toLowerCase(Locale.ROOT)` giúp `" EMAIL "` khớp `"email"` mà không phụ thuộc locale máy chạy. `null` và kênh không hỗ trợ nhận lỗi rõ ràng.
- Trong `main`, hai biến đều khai báo `MessageSender` nhưng `email.send` chạy triển khai Email, `sms.send` chạy triển khai SMS. `getClass().getSimpleName()` chỉ được dùng để quan sát lớp runtime.
- Mỗi lời gọi thành công của **factory cụ thể trong ví dụ này** thực hiện `new`, do đó tạo đối tượng mới. Đó không phải là bảo đảm chung của mọi factory.

Thử đổi `create("email")` thành `create("sms")` ở dòng tạo biến `email`. Không cần sửa lệnh `email.send(...)`; chỉ kết quả hành vi khác đi. Nếu đổi thành `create("push")`, factory ném `IllegalArgumentException` với thông báo `Kênh không hỗ trợ: push`.

## 4. Factory cũng có thể trả về abstract class

Không chỉ `interface`: factory có thể trả `abstract class` khi nhiều lớp con chia sẻ một phần thuật toán hoặc trạng thái. Ví dụ độc lập sau đặt trong tệp `MainAbstract.java`:

```java
public class MainAbstract {
    abstract static class Greeting {
        final void greet(String name) {
            System.out.print("Xin chào, ");
            sayName(name);
        }

        protected abstract void sayName(String name);
    }

    static final class FormalGreeting extends Greeting {
        @Override
        protected void sayName(String name) {
            System.out.println("anh/chị " + name + "!");
        }
    }

    static class GreetingFactory {
        static Greeting create() {
            return new FormalGreeting();
        }
    }

    public static void main(String[] args) {
        Greeting greeting = GreetingFactory.create();
        greeting.greet("An");
    }
}
```

Chạy `javac MainAbstract.java` rồi `java MainAbstract`. Stdout:

```text
Xin chào, anh/chị An!
```

Ở đây `Greeting` không thể khởi tạo trực tiếp, nhưng `new FormalGreeting()` hoàn toàn hợp lệ. `GreetingFactory.create()` trả một `Greeting` tham chiếu đến đối tượng `FormalGreeting`. Phương thức `greet` là phần thuật toán dùng chung; khi gọi `sayName`, Java chạy phần cài đặt ở lớp con. Nếu chỉ cần hợp đồng hành vi, thường chọn interface; nếu cần kế thừa phần cài đặt/trạng thái chung thực sự, abstract class có thể phù hợp hơn.

## 5. So sánh kiểu trả về

| Cách viết | Người gọi biết gì? | Hệ quả |
| --- | --- | --- |
| `EmailSender e = new EmailSender();` | Biết lớp và constructor cụ thể. | Gắn trực tiếp với EmailSender. |
| `EmailSender e = Factory.createEmail();` | Biết lớp cụ thể dù logic tạo nằm trong factory. | Vẫn có thể phụ thuộc các phương thức riêng của EmailSender. |
| `MessageSender s = Factory.create("email");` | Biết hợp đồng `MessageSender` và yêu cầu lựa chọn. | Dễ thay lớp triển khai miễn là giữ hợp đồng. |
| `MessageSender s = new EmailSender();` | Dùng hợp đồng nhưng vẫn tự chọn lớp cụ thể. | Phép gán hợp lệ, nhưng nơi sử dụng còn phụ thuộc constructor. |

Một kiểu trả về trừu tượng làm API rõ ràng hơn: “Bạn cần một đối tượng biết gửi thông điệp”, chứ không yêu cầu người gọi sử dụng các chi tiết đặc thù của Email hoặc SMS. Đây là một cách áp dụng lập trình dựa trên interface, phù hợp với polymorphism và mục tiêu giảm coupling.

Tuy vậy, interface không tự bảo đảm immutable, thread-safe, singleton hay lazy initialization. Các đặc tính ấy phụ thuộc vào implementation và quy ước vòng đời mà factory cam kết.

## 6. Thiết kế factory tốt

**Hợp đồng vừa đủ.** Đặt trong `MessageSender` đúng hành vi cần được sử dụng chung. Nếu bắt mọi implementation triển khai nhiều phương thức vô nghĩa, interface quá rộng và khó mở rộng.

**Tham số rõ ràng.** Trong ví dụ, đầu vào là chuỗi `"email"` hoặc `"sms"`. Với tập lựa chọn hữu hạn ở ứng dụng thực, một `enum` có thể giúp tránh chuỗi gõ sai. Nhưng input từ HTTP, config hoặc file vẫn cần được kiểm tra tại ranh giới và báo lỗi có ý nghĩa. Factory không nên âm thầm trả `null` cho mọi lựa chọn không hỗ trợ nếu contract không nói rõ điều đó.

**Vòng đời đối tượng có chủ ý.** Có factory luôn tạo mới, có factory trả đối tượng cache hoặc instance dùng chung. Nếu đối tượng có mutable state hoặc giữ tài nguyên, cân nhắc cạnh tranh truy cập, thread safety và trách nhiệm đóng tài nguyên. Việc khai báo kiểu trừu tượng không thay thế thiết kế vòng đời.

**Chọn static hoặc instance theo nhu cầu.** `static create` đủ phù hợp với logic đơn giản. Nếu việc tạo sender cần cấu hình, HTTP client hoặc dependency khác, có thể dùng factory instance được inject. Công thức cốt lõi vẫn là: tạo lớp cụ thể và công bố kiểu trừu tượng.

**Tách việc chọn implementation khỏi nghiệp vụ.** Mã nghiệp vụ nhận `MessageSender` qua constructor hoặc tham số sẽ dễ được kiểm thử bằng một implementation giả ghi nhận `send`. Test factory riêng để xác minh `"email"` tạo `EmailSender`, `"sms"` tạo `SmsSender`, và đầu vào sai được xử lý đúng. Nếu mọi phương thức nghiệp vụ đều tự gọi một static factory thì chính mã nghiệp vụ vẫn gắn với factory đó, dù kiểu trả về đã là interface.

**Mở rộng khi cần.** Để thêm `PushSender`, hãy triển khai `MessageSender` rồi cập nhật logic chọn trong factory. Ở hệ thống lớn, có thể dùng registry hoặc dependency injection thay `switch`; không nhất thiết cần cơ chế ấy khi chỉ có vài lựa chọn ổn định.

## 7. Phân biệt với các khái niệm Factory khác

- **Constructor** `new EmailSender()` trực tiếp khởi tạo lớp cụ thể.
- **Simple factory / factory function**, giống `MessageSenderFactory.create`, gom logic tạo và lựa chọn đối tượng. Đó là cách tổ chức code thông dụng, không nhất thiết là một design pattern GoF riêng.
- **Static factory method** là một phương thức static trả/cung cấp instance. Nó có thể tạo mới, dùng cache hay trả singleton theo hợp đồng.
- **Factory Method pattern (GoF)** định nghĩa phương thức tạo sản phẩm ở lớp cha, để lớp con quyết định kiểu sản phẩm được tạo. Không thể suy ra một phương thức `static create()` bất kỳ là Factory Method pattern.
- **Abstract Factory pattern (GoF)** biểu diễn một giao diện tạo *họ sản phẩm liên quan* mà không phụ thuộc lớp cụ thể. Nó giải quyết phạm vi rộng hơn ví dụ chọn một sender.

Tên bài “Factory tạo lớp cụ thể nhưng trả về kiểu trừu tượng” diễn đạt nguyên tắc **tách API sử dụng khỏi chi tiết khởi tạo**. Nguyên tắc có thể được áp dụng trong nhiều biến thể factory, không cần áp dụng toàn bộ pattern phức tạp cho một bài toán nhỏ.

## 8. Các lỗi thường gặp

1. **Cố `new` interface hoặc abstract class:** `new MessageSender()` và `new Greeting()` không hợp lệ. Hãy khởi tạo một lớp cụ thể triển khai/kế thừa đúng hợp đồng.
2. **Cho rằng biến trừu tượng mất kiểu thực tế:** object vẫn có class runtime như `EmailSender`; lời gọi phương thức được phân phối động.
3. **Gọi API riêng của lớp con qua interface:** chỉ những phương thức thuộc hợp đồng chung mới có thể gọi trực tiếp. Đừng ép kiểu xuống chỉ để lách API, trừ khi có yêu cầu và kiểm tra kiểu an toàn.
4. **Ép kiểu sai:** `(EmailSender) MessageSenderFactory.create("sms")` có thể biên dịch nhưng lúc chạy ném `ClassCastException`. Nếu cần cả hai khả năng, ưu tiên thiết kế interface chung thích hợp.
5. **Không xác định trường hợp kênh lạ/`null`:** dẫn đến lỗi mơ hồ hoặc `NullPointerException` ở xa nguồn. Hãy kiểm tra và thông báo thất bại tại factory.
6. **Đánh đồng static factory với singleton:** một phương thức static vẫn có thể gọi `new` mỗi lần; hai lần gọi có thể trả hai đối tượng khác nhau.
7. **Lạm dụng `instanceof` ở nơi sử dụng:** nếu nghiệp vụ cứ tách nhánh Email/SMS để chọn hành vi, xem lại hợp đồng và cách dùng đa hình.
8. **Factory phức tạp khi không cần thiết:** chỉ thêm lớp factory khi việc chọn, cấu hình, điều khiển lifecycle hoặc tách dependency thực sự giúp code dễ hiểu.

## 9. Câu hỏi tự luyện

1. Vì sao `MessageSender sender = MessageSenderFactory.create("email")` hợp lệ dù không thể `new MessageSender()`?
2. Khi đổi kênh thành `"sms"`, điều gì xảy ra ở lệnh `sender.send(...)` và vì sao không cần sửa kiểu biến?
3. Hai lời gọi `create("email")` trong **ví dụ hiện tại** có trả cùng một đối tượng không? Nếu muốn cache, phần cài đặt nào phải thay đổi?
4. Nếu thêm lớp `PushSender implements MessageSender`, cần bổ sung gì trong factory, và đoạn mã người gọi chỉ sử dụng `send` có phải sửa không?
5. Nếu muốn kiểm thử một service gọi `sender.send(...)` mà không gửi tin thật, bạn sẽ truyền dependency kiểu gì vào service?

Ghi nhớ: **factory có thể biết và `new` lớp cụ thể, nhưng khai báo trả về interface/abstract class để người gọi phụ thuộc vào hợp đồng chung.** Compiler kiểm tra thao tác theo kiểu khai báo; JVM thực thi phương thức được ghi đè theo lớp thực tế của đối tượng.


</details>

- [Quay lại đầu trang](#back-to-top)
