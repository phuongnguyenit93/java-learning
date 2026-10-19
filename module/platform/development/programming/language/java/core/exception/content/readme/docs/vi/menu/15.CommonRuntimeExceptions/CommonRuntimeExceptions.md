<a id="back-to-top"></a>

# Ngoại lệ runtime

## Menu
- [NullPointerException: dereference, unboxing và đầu vào null](#common-runtime-exceptions-001)
- [IllegalArgumentException và IllegalStateException](#common-runtime-exceptions-002)
- [IndexOutOfBoundsException và ArrayIndexOutOfBoundsException](#common-runtime-exceptions-003)
- [NumberFormatException và chuyển đổi dữ liệu đầu vào](#common-runtime-exceptions-004)
- [ArithmeticException với phép toán số nguyên và các phép toán exact](#common-runtime-exceptions-005)
- [Phân biệt chia số thực cho 0 với chia số nguyên cho 0](#common-runtime-exceptions-006)
- [ClassCastException và ArrayStoreException](#common-runtime-exceptions-007)
- [NegativeArraySizeException](#common-runtime-exceptions-008)
- [NoSuchElementException và truy cập khi không có phần tử](#common-runtime-exceptions-009)
- [UnsupportedOperationException và thao tác tùy chọn](#common-runtime-exceptions-010)
- [ConcurrentModificationException và giới hạn của cơ chế fail-fast](#common-runtime-exceptions-011)
- [Objects.requireNonNull và kiểm tra điều kiện trước khi xử lý](#common-runtime-exceptions-012)

## <a id="common-runtime-exceptions-001">NullPointerException: dereference, unboxing và đầu vào null</a>

<details>
<summary>Xem chi tiết</summary>

# NullPointerException: dereference, unboxing và đầu vào null trong Java

> **Java Core – Exception** · Nhóm: **Ngoại lệ runtime** · Phiên bản minh họa: **Java 17+**. Chủ đề tập trung vào ba nguồn lỗi thường gặp: truy cập qua tham chiếu `null`, chuyển wrapper sang primitive (*unboxing*) và hợp đồng xử lý tham số đầu vào có thể là `null`.

## 1. NullPointerException là gì?

`java.lang.NullPointerException` (viết tắt **NPE**) là một lớp con của `RuntimeException`. Nó biểu thị việc chương trình đã thực hiện một thao tác yêu cầu có đối tượng hoặc giá trị cụ thể, nhưng tham chiếu được sử dụng lại là `null`.

Tổng quan:

~~~text
java.lang.Throwable
└── java.lang.Exception
    └── java.lang.RuntimeException
        └── java.lang.NullPointerException
~~~

NPE là **unchecked exception**: compiler **không buộc** phương thức khai báo `throws NullPointerException`, và cũng không buộc caller bắt nó. Tuy nhiên, nếu NPE được ném mà không được xử lý, luồng thực thi hiện tại có thể kết thúc bất thường.

Ví dụ điển hình:

~~~java
String name = null;
int length = name.length(); // NPE tại runtime.
~~~

Đây là **đoạn minh họa trong một method**, không phải chương trình độc lập. Biến `name` có **kiểu tham chiếu** `String` và giá trị `null`. Khi thực thi lời gọi method instance `name.length()`, Java không có đối tượng String thực tế làm receiver.

**Phân biệt:** `null` tự nó **không phải một exception** và cũng không phải giá trị mặc định của mọi biến. `null` là một giá trị tham chiếu đặc biệt thể hiện không tham chiếu tới object nào. Những biến primitive như `int`, `boolean` không nhận trực tiếp giá trị `null`.

### Những điều cần nắm sau bài

1. Nhận biết chính xác thao tác **dereference** nào ném NPE: gọi method instance, đọc/ghi field instance, truy cập mảng, lấy độ dài mảng và nhiều phép toán khác cần receiver khác null.
2. Hiểu vì sao **unboxing** một wrapper như `Integer` hoặc `Boolean` có giá trị null dẫn tới NPE dù không thấy method call tường minh.
3. Xác định rõ API **cho phép null, cấm null hay áp dụng giá trị mặc định**, tránh thiết kế phương thức mơ hồ.
4. Sử dụng kiểm tra null, `Objects.requireNonNull` và các API phù hợp mà không làm mất lỗi gốc.
5. Chẩn đoán NPE dựa trên stack trace và *helpful NullPointerException messages* của Java hiện đại, nhưng không phụ thuộc vào nguyên văn thông báo.
6. Biên dịch và chạy một chương trình Java 17 có stdout được kiểm chứng.

## 2. Null có thể xuất hiện ở đâu?

Một số ví dụ:

~~~java
String text = null;                 // Hợp lệ: String là kiểu tham chiếu.
Integer count = null;               // Hợp lệ: wrapper cũng là tham chiếu.
int[] numbers = null;               // Hợp lệ: mảng là đối tượng.
Object value = null;                // Hợp lệ.
int quantity = 0;                   // Primitive dùng một giá trị int.
boolean enabled = false;            // Primitive dùng một giá trị boolean.
~~~

Ngược lại:

~~~java
// CỐ Ý KHÔNG BIÊN DỊCH:
int quantity = null;
boolean enabled = null;
~~~

NPE liên quan tới **giá trị runtime** của tham chiếu, không đơn thuần là cách biến được khai báo. Một method có kiểu trả về `String` vẫn có thể trả `null` nếu hợp đồng/triển khai cho phép. Một field tham chiếu chưa được gán tường minh có thể có giá trị mặc định `null` khi object được tạo; còn **biến cục bộ** phải đáp ứng quy tắc *definite assignment* trước khi dùng, không được tự động xem là có giá trị null.

Ví dụ minh họa:

~~~java
class Record {
    String title; // Mặc định null nếu chưa được gán.
}
~~~

Nếu tạo `new Record()` rồi thực thi `record.title.length()`, NPE xảy ra ở **lời gọi length() trên giá trị field title**, không phải ở việc đọc field `title` từ một object `record` không null.

### Null không tự động ném lỗi

Các thao tác sau hợp lệ:

~~~java
String data = null;
boolean missing = (data == null); // true.
boolean matched = (data instanceof String); // false.
System.out.println(data);         // In chuỗi "null" theo println(Object).
~~~

Không có NPE chỉ vì gán, so sánh bằng `== null`, kiểm tra `instanceof`, hoặc truyền một tham chiếu null cho phương thức **có hỗ trợ null**. NPE xảy ra khi có **một thao tác cụ thể không chấp nhận null**, chẳng hạn dereference hoặc unboxing.

**Lưu ý:** `System.out.println(data)` ở ví dụ này in chữ `null` vì overload nhận String xử lý đầu vào null theo hợp đồng của `PrintStream`. Không được suy ra mọi API cũng xử lý null giống vậy.

## 3. Dereference: truy cập method và field qua null

### 3.1. Gọi instance method

~~~java
String input = null;
int size = input.trim().length(); // NPE tại bước gọi trim().
~~~

Receiver `input` là null, nên lời gọi instance method `trim()` không thể được thực hiện bình thường. Vì `trim()` đã thất bại nên bước `length()` phía sau cũng chưa được chạy.

Với chuỗi method dài:

~~~java
String city = order.getCustomer().getAddress().getCity();
~~~

đây là ví dụ minh họa, giả định các class và method đã định nghĩa. NPE có thể đến từ nhiều vị trí:

- `order` chính nó null khi gọi `getCustomer()`.
- `getCustomer()` trả null, rồi code gọi `getAddress()` trên null.
- `getAddress()` trả null, rồi code gọi `getCity()` trên null.
- Nếu `getCity()` trả null nhưng chỉ **gán** cho biến `city`, dòng đó có thể hoàn tất bình thường; NPE chỉ phát sinh sau khi có thao tác không chấp nhận giá trị null đó.

Vì vậy **NPE không đồng nghĩa toàn bộ biểu thức bên trái đều null**. Cần tìm chính xác đoạn dereference bị lỗi.

### 3.2. Đọc hoặc gán instance field

~~~java
class User {
    String email;
}

User user = null;
String email = user.email; // NPE khi đọc field instance.
~~~

Một thao tác ghi cũng có thể lỗi:

~~~java
User user = null;
user.email = "contact@example.com"; // NPE tại runtime.
~~~

Đây là các **đoạn minh họa trong method/class**, không đặt tất cả cùng một scope mà khai báo biến trùng tên. NPE ở đây là do **biến receiver `user` null**, không phải do bản thân field `email` có kiểu String.

So sánh trường hợp khác:

~~~java
User user = new User();
String email = user.email; // Hợp lệ, email == null.
~~~

Object `user` khác null nên đọc field thành công, dù **giá trị bên trong field** là null. Chỉ khi code làm `email.trim()` hoặc dereference tương tự mới có NPE.

### 3.3. Mảng null và phần tử mảng null là hai lỗi khác nhau

~~~java
String[] names = null;
int length = names.length; // NPE vì chính mảng null.
String first = names[0];   // NPE vì mảng null.
~~~

Một trường hợp khác:

~~~java
String[] names = new String[1]; // Mảng hợp lệ, phần tử names[0] mặc định null.
String first = names[0];        // Hợp lệ, first == null.
int length = first.length();    // NPE vì first null.
~~~

Phân biệt thêm với `ArrayIndexOutOfBoundsException`: nếu mảng **khác null** nhưng truy cập chỉ số không hợp lệ, đó là lỗi về giới hạn chỉ số, không phải lỗi tham chiếu null.

### 3.4. Các thao tác khác có thể yêu cầu giá trị khác null

Ngoài method/field/mảng, một số cấu trúc Java cũng có thể phát sinh NPE:

- `synchronized (lock) { ... }` nếu biểu thức `lock` cho kết quả null.
- Lệnh `throw ex;` nếu biểu thức `ex` cho kết quả null: Java tạo/ném một NullPointerException theo quy tắc `throw`.
- `switch` thông thường trên giá trị tham chiếu null trong Java 17 không có case null hợp lệ theo chế độ ngôn ngữ ổn định: selector null dẫn tới NPE.
- Unboxing `Integer`, `Long`, `Boolean` ... mà giá trị null.

Không phải thao tác nào cũng cần nhìn thấy dấu chấm `.` mới có NPE. Riêng **unboxing** là nguồn lỗi đặc biệt quan trọng vì code trông như đang thực hiện số học hoặc kiểm tra boolean bình thường.

## 4. Unboxing: vì sao wrapper null gây NullPointerException?

**Autoboxing** là chuyển đổi từ primitive sang wrapper, ví dụ `int → Integer`. **Unboxing** là chuyển ngược từ wrapper sang primitive, ví dụ `Integer → int`, khi ngữ cảnh yêu cầu.

Theo [JLS Java 17 mục 5.1.8 – Unboxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.8), chuyển đổi unboxing từ **tham chiếu null** sẽ ném `NullPointerException`.

### 4.1. Gán Integer cho int

~~~java
Integer boxed = null;
int number = boxed; // NPE.
~~~

Khi biên dịch, Java áp dụng unboxing conversion. Có thể hiểu khái niệm tương đương với việc lấy giá trị qua `boxed.intValue()`: nếu `boxed` null, không có số `int` nào được lấy ra. Đây là **mô tả ngữ nghĩa**, không phải bảo đảm compiler sinh đúng một chuỗi bytecode cố định.

So sánh:

~~~java
Integer boxed = 5; // Boxing số 5 thành Integer.
int number = boxed; // Unboxing thành int, number == 5.
~~~

Unboxing **không phải lúc nào cũng lỗi**; chỉ ném NPE nếu tham chiếu được chuyển đổi là null.

### 4.2. Phép toán số học cũng có thể tự unbox

~~~java
Integer count = null;
int next = count + 1; // NPE.
~~~

Toán tử `+` trong biểu thức này cần giá trị số primitive. `count` được unbox, nên dù bạn không viết `count.intValue()`, NPE vẫn có thể xảy ra.

Ví dụ khác:

~~~java
Integer left = null;
Integer right = 2;
int sum = left + right; // NPE do left.
~~~

NPE không liên quan đến phép cộng vượt phạm vi số hoặc dữ liệu nhập sai định dạng. Nó phát sinh tại thao tác **chuyển một wrapper null sang primitive**.

### 4.3. So sánh có thể kích hoạt unboxing

~~~java
Integer score = null;
boolean absent = score == null; // An toàn; so sánh tham chiếu.
boolean isZero = score == 0;    // NPE; so với int literal nên cần unboxing.
~~~

Phân biệt rõ hai biểu thức:

- `score == null` không yêu cầu giá trị primitive nên dùng để **kiểm tra null**.
- `score == 0` có operand `0` là `int`, dẫn đến unboxing `score`; nếu null thì lỗi.

Đừng suy ra mọi dấu `==` đều nguy hiểm hoặc mọi dấu `==` đều an toàn; ý nghĩa phụ thuộc **kiểu tĩnh của hai operand** và các quy tắc chuyển đổi.

### 4.4. Boolean null trong if, while và toán tử logic

~~~java
Boolean approved = null;
if (approved) {
    System.out.println("approved");
}
~~~

Điều kiện `if` cần `boolean` primitive, nên Java phải unbox `approved`. Khi `approved` null, chương trình ném NPE trước khi chọn nhánh.

Một cách xử lý nếu nghiệp vụ coi null là **không được phê duyệt**:

~~~java
if (Boolean.TRUE.equals(approved)) {
    System.out.println("approved");
}
~~~

`Boolean.TRUE` là đối tượng không null, `equals` so sánh an toàn với argument null. Kết quả khi `approved` null là false. Nhưng **không tự động thay mọi giá trị null bằng false**: nếu null nghĩa là “chưa được đánh giá”, ba trạng thái true/false/unknown có thể là một phần hợp đồng, không nên làm mất ý nghĩa.

### 4.5. Unboxing từ đầu ra của collection và generic

Ví dụ khá thường gặp:

~~~java
Map<String, Integer> quantities = new HashMap<>();
Integer boxed = quantities.get("missing"); // null nếu không có mapping.
int quantity = quantities.get("missing");   // NPE do unboxing.
~~~

Đây là đoạn minh họa, cần import `Map` và `HashMap`. Generic `Integer` **không bảo đảm** mọi giá trị lấy từ map đều khác null. Một phép gọi `get()` có thể trả null do khóa không tồn tại, hoặc vì map cho phép mapping tới null.

**Cẩn trọng với `getOrDefault(key, defaultValue)`:** Nếu một map chứa khóa đó nhưng giá trị liên kết là null, một số implementation tuân hợp đồng `Map.getOrDefault` sẽ trả **null** (vì khóa có mapping), không trả default. Do đó không nên khẳng định `getOrDefault` luôn loại bỏ NPE do unboxing trong mọi trạng thái map.

Phải xác định contract: null là “không có dữ liệu” hay một giá trị hợp lệ? Nếu null bị cấm, kiểm tra sớm hoặc chọn cấu trúc lưu trữ ngăn null; nếu cho phép, phải quyết định cách chuyển sang primitive.

## 5. Chương trình Java 17 hoàn chỉnh để kiểm chứng

Tạo tệp **`NullPointerExceptionDemo.java`**. Ví dụ trình bày nhiều nguyên nhân NPE theo từng ca, bắt NPE **chỉ ở phạm vi demo** để tiếp tục chạy các ca khác. Trong ứng dụng thật, tránh bắt và im lặng bỏ qua lỗi lập trình như thế này.

~~~java
import java.util.Objects;

public class NullPointerExceptionDemo {
    private static final class Profile {
        private final String name;

        private Profile(String name) {
            this.name = name;
        }

        private String upperName() {
            return name.toUpperCase();
        }
    }

    private static String findName() {
        return null;
    }

    private static void requireName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        System.out.println("accepted=" + name);
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static void test(String label, Runnable task) {
        System.out.println("== " + label + " ==");
        try {
            task.run();
            System.out.println("completed");
        } catch (NullPointerException ex) {
            System.out.println("caught=" + ex.getClass().getSimpleName());
            if ("explicit validation".equals(label)) {
                System.out.println("message=" + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        Profile profile = null;
        int[] numbers = null;
        Integer count = null;
        Boolean approved = null;

        test("method dereference", () -> System.out.println(profile.upperName()));
        test("field dereference", () -> System.out.println(profile.name));
        test("array length", () -> System.out.println(numbers.length));
        test("array element", () -> System.out.println(numbers[0]));
        test("unbox assignment", () -> {
            int value = count;
            System.out.println(value);
        });
        test("unbox arithmetic", () -> System.out.println(count + 1));
        test("unbox condition", () -> {
            if (approved) {
                System.out.println("approved");
            }
        });
        test("nullable return", () -> System.out.println(findName().length()));
        test("explicit validation", () -> requireName(null));

        System.out.println("== safe operations ==");
        System.out.println("null comparison=" + (profile == null));
        System.out.println("instanceof=" + (profile instanceof Profile));
        System.out.println("literal equals=" + "admin".equals(findName()));
        System.out.println("objects equals=" + Objects.equals(null, null));
        System.out.println("normalize=[" + normalize(null) + "]");

        System.out.println("== valid input ==");
        requireName("Lan");
    }
}
~~~

Biên dịch với JDK 17 hoặc mới hơn và chọn mức ngôn ngữ 17:

~~~bash
javac --release 17 -encoding UTF-8 NullPointerExceptionDemo.java
java NullPointerExceptionDemo
~~~

**Stdout chính xác đã kiểm chứng bằng việc biên dịch và chạy:**

~~~text
== method dereference ==
caught=NullPointerException
== field dereference ==
caught=NullPointerException
== array length ==
caught=NullPointerException
== array element ==
caught=NullPointerException
== unbox assignment ==
caught=NullPointerException
== unbox arithmetic ==
caught=NullPointerException
== unbox condition ==
caught=NullPointerException
== nullable return ==
caught=NullPointerException
== explicit validation ==
caught=NullPointerException
message=name must not be null
== safe operations ==
null comparison=true
instanceof=false
literal equals=false
objects equals=true
normalize=[]
== valid input ==
accepted=Lan
~~~

Chương trình cố ý **không in thông điệp NPE do JVM tự tạo** ở các ca dereference/unboxing, bởi những thông điệp đó có thể khác nhau giữa JVM và cấu hình. Chỉ có message do `Objects.requireNonNull(name, "name must not be null")` cung cấp là được in tường minh.

### 5.1. Những ca dereference

- `method dereference`: biến `profile` null nên lời gọi `profile.upperName()` phát sinh NPE.
- `field dereference`: `profile.name` thất bại vì receiver `profile` null.
- `array length`: `numbers.length` yêu cầu mảng khác null.
- `array element`: `numbers[0]` cần một mảng thực tế; khi mảng null thì NPE.
- `nullable return`: `findName()` trả null; lời gọi `length()` trên kết quả đó ném NPE.

Ở ca `method dereference`, phương thức `upperName()` **không chạy được** vì không có Profile làm receiver. Đừng nhầm với tình huống `profile != null` nhưng field `name == null`: khi ấy phương thức có thể được gọi và NPE xuất hiện **bên trong** `upperName()` ở lệnh `name.toUpperCase()`.

### 5.2. Những ca unboxing

- `unbox assignment`: `Integer count = null` phải chuyển sang `int value`.
- `unbox arithmetic`: toán tử `count + 1` cần số primitive.
- `unbox condition`: `Boolean approved = null` phải chuyển sang `boolean` cho điều kiện `if`.

Ba thao tác không chứa lời gọi method `.intValue()` hoặc `.booleanValue()` tường minh, nhưng compiler vẫn áp dụng quy tắc unboxing. Đây là nhóm lỗi khó thấy nếu người đọc chỉ tìm những dấu chấm dereference trong source code.

### 5.3. Kiểm tra đầu vào và hành vi an toàn

- `explicit validation`: `Objects.requireNonNull` kiểm tra ngay khi method nhận argument null và ném NPE với message do lập trình viên quy định.
- `null comparison`: `profile == null` là true.
- `instanceof`: toán hạng null làm biểu thức `instanceof Profile` trả false.
- `literal equals`: `"admin".equals(null)` trả false vì receiver `"admin"` khác null.
- `objects equals`: `Objects.equals(null, null)` trả true theo hợp đồng API.
- `normalize`: chọn chính sách rõ ràng **null thành chuỗi rỗng**, không dereference null.
- `valid input`: `requireName("Lan")` chấp nhận argument hợp lệ và in `accepted=Lan`.

**Không tổng quát hóa** rằng mọi null nên biến thành chuỗi rỗng hoặc số 0; đó chỉ là **một chính sách nghiệp vụ minh họa**.

## 6. Null là đầu vào: truyền null và dereference là hai sự kiện khác nhau

Một API có thể nhận được `null` mà **không lập tức ném NPE**:

~~~java
static void send(String recipient) {
    // Chỉ nhận tham chiếu; chưa dereference.
}
send(null); // Không tự phát sinh NPE chỉ do truyền argument.
~~~

Đây là đoạn minh họa, đặt ở đúng ngữ cảnh class và method. Nếu `send` sau đó làm `recipient.trim()`, nó có thể ném NPE; còn nếu nó chủ động kiểm tra null, hành vi phụ thuộc nhánh xử lý.

Nên quyết định **hợp đồng tham số** trước khi viết code:

| Chính sách API | Ý nghĩa của null | Cách triển khai thường gặp |
| --- | --- | --- |
| **Cấm null** | Caller vi phạm tiền điều kiện | `Objects.requireNonNull(arg, "arg must not be null")` |
| **Cho phép null** | Null có ý nghĩa như “chưa biết”/“không có” | Nhánh điều kiện riêng hoặc kiểu dữ liệu biểu diễn trạng thái rõ |
| **Null dùng default** | Nếu vắng dữ liệu thì chọn giá trị mặc định đã định nghĩa | Ternary, `Objects.requireNonNullElse`, logic default |
| **Không dùng null ở public API** | Caller phải truyền một object/value hợp lệ | Factory/validation và hợp đồng nhất quán |

Một chương trình có API không rõ nullability rất dễ sinh ra NPE xa nơi nguyên nhân bắt đầu: input null đi qua ba tầng rồi mới bị dereference. Kiểm tra tại ranh giới giúp xác định **đúng trách nhiệm** giữa bên gọi và bên được gọi.

### 6.1. Cấm null: fail fast

~~~java
import java.util.Objects;

class Account {
    private final String id;

    Account(String id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
    }
}
~~~

Class này có thể biên dịch độc lập (trong file phù hợp). Nếu caller truyền null, constructor ném NPE **tại điểm validate**, với thông điệp nhất quán, thay vì lưu null vào field rồi phát sinh NPE tại một phương thức không liên quan về sau.

Lợi ích:

- Phát hiện sai hợp đồng gần **điểm nhận dữ liệu**.
- Giảm khả năng object được tạo ở trạng thái vi phạm invariant.
- Giữ stack trace gắn với nơi xác thực tiền điều kiện, dễ tìm bug của caller.
- Không cần trải các câu kiểm tra null trùng lặp ở mọi method nếu invariant đã được bảo đảm.

Tuy vậy, `Objects.requireNonNull` chỉ kiểm tra **null**, không kiểm tra chuỗi rỗng/blank, giá trị âm hoặc quy tắc nghiệp vụ khác. Với `String`, có thể cần xác thực thêm.

### 6.2. Cho phép null: xử lý đúng ý nghĩa

~~~java
static String displayName(String nickname) {
    return nickname == null ? "(anonymous)" : nickname.trim();
}
~~~

Đây là phương thức minh họa. Nó **chủ động xác định** ý nghĩa của null là dùng tên thay thế. Điều này phù hợp nếu null được phép mang nghĩa “không có nickname”.

Một số ứng dụng lại cần phân biệt:

- Người dùng chưa nhập dữ liệu.
- Người dùng cố tình xóa giá trị.
- Dữ liệu bị thiếu do lỗi hệ thống.

Nếu ba trạng thái này quan trọng, việc tự động đổi mọi null thành `""` có thể làm mất thông tin. Nên dùng enum, result type, validation state hoặc thiết kế API phù hợp, không mặc định null đồng nghĩa chuỗi rỗng.

### 6.3. Cần giá trị default: dùng utility nhưng hiểu điều kiện

Từ Java 9, `Objects.requireNonNullElse(value, defaultValue)` trả đối số thứ nhất nếu không null, ngược lại trả default **phải khác null**:

~~~java
String language = Objects.requireNonNullElse(requestedLanguage, "vi");
~~~

Đoạn minh họa cần import `Objects` và biến `requestedLanguage`. Nếu cả hai đối số đều null, API ném NPE vì không thể trả về một fallback hợp lệ.

Ngoài ra có `Objects.requireNonNullElseGet(value, supplier)` để tính fallback theo nhu cầu; supplier hoặc kết quả fallback null cũng có thể gây NPE. Không coi utility này là bằng chứng tự động rằng mọi value từ ngoài đều đáng tin cậy.

## 7. Chọn cách kiểm tra null phù hợp

Chọn cách xử lý dựa trên **hợp đồng của tham chiếu**, không chỉ dựa vào việc muốn tránh stack trace:

| Hợp đồng | Cách viết thường phù hợp | Ý nghĩa |
| --- | --- | --- |
| `null` là dữ liệu tùy chọn | `if (value == null)` | Phân nhánh rõ ràng, không giả vờ dữ liệu có mặt |
| `null` bị cấm | `Objects.requireNonNull(value, "value")` | Báo NPE sớm, có tên tham số |
| So sánh chuỗi với literal cố định | `"admin".equals(role)` | Không dereference biến `role` có thể null |
| So sánh hai đối tượng có thể null | `Objects.equals(a, b)` | Xử lý cả trường hợp hai bên cùng null |
| Kiểm tra kiểu dữ liệu động | `value instanceof String` | Với null, biểu thức là `false`, không NPE |
| Kết quả tra cứu có thể vắng mặt | `Optional.ofNullable(findName())` | Diễn đạt giá trị tùy chọn |

Các đoạn ví dụ trong bảng giả định biến/method và import `java.util.Objects`, `java.util.Optional` thích hợp.

Hai cách có hợp đồng khác nhau:

~~~java
static String normalizeOptional(String raw) {
    return raw == null ? "" : raw.trim();
}

static String normalizeRequired(String raw) {
    return java.util.Objects.requireNonNull(raw, "raw").trim();
}
~~~

Đây là các phương thức hợp lệ khi đặt trong một lớp Java. `normalizeOptional(null)` trả chuỗi rỗng **vì API cho phép fallback**, còn `normalizeRequired(null)` ném `NullPointerException` có chủ đích. Đừng tùy tiện thay một hợp đồng bằng hợp đồng kia.

`Objects.equals(null, null)` trả `true`, nhưng không thay thế phép tính trên primitive và có thể có ý nghĩa khác so với unboxing wrapper. `Optional.ofNullable(null)` tạo Optional rỗng; trái lại `Optional.of(null)` ném NPE và `Optional.get()` trên giá trị rỗng ném `NoSuchElementException`. Các overload của `Objects.requireNonNull`, `requireNonNullElse` và cách thiết kế tiền điều kiện được trình bày kỹ hơn ở bài về kiểm tra điều kiện đầu vào.

## 8. Unboxing null: ba hướng phòng tránh có chủ đích

### Hướng A: kiểm tra trước khi chuyển sang primitive

~~~java
Integer quantity = lookupQuantity();
if (quantity == null) {
    throw new IllegalArgumentException("quantity is required");
}
int count = quantity;
~~~

Đây là mã minh họa; `lookupQuantity()` chưa có định nghĩa. Với hợp đồng **bắt buộc có quantity**, ném lỗi xác thực nghiệp vụ có thể hợp lý hơn mặc định thay null bằng 0.

Nếu null là lỗi của caller, cũng có thể dùng `Objects.requireNonNull(quantity, "quantity required")`. Việc chọn NPE hay IllegalArgumentException phụ thuộc quy ước API; quan trọng là thông điệp và thời điểm kiểm tra rõ ràng.

### Hướng B: default trước khi unbox

~~~java
Integer quantity = lookupQuantity();
int count = quantity != null ? quantity : 0;
~~~

Đây là mã minh họa. Nếu null **thật sự có nghĩa là không có số lượng, xem như 0**, cách này tránh NPE. Nhưng với giao dịch, tồn kho hay dữ liệu quan trọng, “không rõ số lượng” và “số lượng bằng 0” thường không tương đương. Không tự default để cho chương trình tiếp tục mà mất ý nghĩa dữ liệu.

Có thể viết:

~~~java
int count = Objects.requireNonNullElse(quantity, 0);
~~~

với Java 9+ và import `Objects`. Cả hai nhánh của utility trả một `Integer` khác null (nếu fallback đúng), sau đó unboxing an toàn.

### Hướng C: giữ kiểu wrapper cho trạng thái thiếu dữ liệu

~~~java
Integer quantity = lookupQuantity();
if (quantity == null) {
    showMissingQuantity();
} else {
    showQuantity(quantity.intValue());
}
~~~

Đây là mã minh họa; các method cần định nghĩa. Nếu trạng thái “missing” là một trường hợp hợp lệ, không nên ép chuyển ngay sang primitive rồi mới xử lý lỗi.

Với `Boolean`, một phép kiểm tra như `Boolean.TRUE.equals(flag)` có thể diễn giải null thành false, **nhưng chỉ nếu hợp đồng muốn vậy**. Nếu null biểu thị “unknown”, nên mô hình hóa ba trạng thái một cách rõ ràng.

## 9. NPE do API trả null và tác động của collection

Không phải NPE nào cũng xuất hiện từ **tham số đầu vào**. Giá trị null có thể đi vào qua:

- Một factory hoặc repository trả null để biểu thị “không tìm thấy”.
- Một method getter của object chưa được khởi tạo đầy đủ.
- Một phần tử của array hoặc collection.
- Một key không tồn tại trong map.
- Một API bên ngoài có contract cho phép null.
- Một đoạn code chuyển đổi dữ liệu mà không xác thực giá trị được trả về.

Ví dụ:

~~~java
Integer current = counters.get("login");
int next = current + 1; // NPE nếu current null.
~~~

Đây là đoạn minh họa; `counters` cần là map phù hợp. Kiểu `Map<String,Integer>` không có nghĩa giá trị đọc từ map luôn là một Integer khác null. Mỗi ranh giới dữ liệu cần quyết định rõ khi không tìm thấy khóa hoặc khi giá trị null.

**Mẫu thiết kế tốt hơn:** Nếu không tồn tại khóa, câu lệnh có thể cố tình dùng 0 như default; nếu null biểu thị lỗi dữ liệu, hãy báo lỗi thay vì mặc định. Đừng bắt NPE ở đoạn cộng để suy ra “không tìm thấy số đếm”; điều đó có thể che một NPE khác xuất phát từ bản thân map null hoặc logic ngoài dự kiến.

## 10. Helpful NullPointerException messages từ Java hiện đại

Từ JEP 358 (*Helpful NullPointerExceptions*, được đưa vào JDK 14), các JVM hỗ trợ có thể bổ sung **thông tin mô tả dereference nào là null** khi NPE được JVM tạo ra tại các vị trí có thể phân tích bytecode.

Ví dụ thông báo có thể giống:

~~~text
Cannot invoke "String.length()" because "name" is null
~~~

Đây là **chuỗi minh họa**, không phải stdout bắt buộc của chương trình chính. Một trường hợp method chain có thể được mô tả theo kết quả getter trả null, như:

~~~text
Cannot invoke "Address.getCity()" because the return value of "Customer.getAddress()" is null
~~~

Tên class, thông điệp, đường dẫn, biến cục bộ được khôi phục và ngôn ngữ hiển thị có thể khác nhau tùy bản build, bytecode và JVM. Thậm chí `getMessage()` vẫn có thể là null với một số NPE.

### Vì sao có khi không có helpful message?

- NPE **được tạo hoặc ném tường minh**, như `new NullPointerException("manual")`, có message do code cung cấp; cơ chế thông báo diễn giải tự động không áp dụng giống cách JVM tạo lỗi dereference.
- NPE được tạo tại thời điểm/cơ chế mà JVM không có dữ liệu bytecode phù hợp để phân tích.
- Một số cấu hình JVM có thể tắt hỗ trợ thông điệp chi tiết.
- Tối ưu và công cụ biên dịch có thể ảnh hưởng thông tin được ghi trong stack trace.

Với HotSpot/Java 17, có thể kiểm tra hoặc điều chỉnh lựa chọn liên quan thông qua flag VM `-XX:+ShowCodeDetailsInExceptionMessages` và lựa chọn tắt tương ứng `-XX:-ShowCodeDetailsInExceptionMessages` khi JVM hỗ trợ. Không nên giả định flag tương tự tồn tại trên mọi nhà cung cấp JVM.

**Không viết unit test phụ thuộc nguyên văn helpful NPE message.** Hãy test loại lỗi, hành vi và message do API của chính bạn quy định; dùng helpful message như công cụ chẩn đoán bổ sung.

## 11. Cách debug NullPointerException có hệ thống

Khi thấy stack trace báo NPE:

1. **Tìm frame đầu tiên thuộc code của bạn**, file, số dòng và method tương ứng. NPE có thể phát sinh trong thư viện, nhưng nguyên nhân lại do input không hợp lệ ở caller.
2. **Đọc toàn bộ biểu thức ở dòng lỗi**: có method chain, field, mảng, unboxing hoặc result từ API không?
3. **Xác định từng tham chiếu có thể null**: receiver, getter result, phần tử mảng, wrapper Integer/Boolean, tham số, return value.
4. **Xem helpful NPE message nếu có**, nhưng không coi nó thay thế việc đọc source và xác minh dữ liệu thực.
5. **Kiểm tra đường đi của giá trị** từ input, map, repository, JSON parser, API bên ngoài tới nơi dereference.
6. **Xác định hợp đồng nullability**: null hợp lệ, thiếu dữ liệu hay caller vi phạm tiền điều kiện?
7. **Sửa ở điểm chịu trách nhiệm**: validate sớm, trả model đúng, hoặc xử lý nhánh thiếu dữ liệu có chủ đích.
8. **Thêm test** cho input null, giá trị hợp lệ, return null, empty collection, wrapper null và các nhánh nghiệp vụ quan trọng.

Nếu một dòng chứa:

~~~java
int total = repository.findOrder(id).getItems().size();
~~~

thì NPE có thể phát sinh do `repository` null, `findOrder(id)` trả null hoặc `getItems()` trả null. Nếu `getItems()` trả collection hợp lệ nhưng rỗng, `size()` thường trả 0; đó không phải NPE. Nếu truy cập tiếp đến field/element null lại là câu chuyện khác.

Đây là dòng **minh họa**, yêu cầu class/method tương ứng; không thể suy nguyên nhân chính xác chỉ bằng cách thấy từ `size()`.

### Vì sao không nên sửa bằng catch NPE rồi trả kết quả mặc định?

~~~java
// ANTI-PATTERN: dễ che bug, không nên sao chép làm giải pháp chung.
try {
    return repository.getCustomer(id).getName();
} catch (NullPointerException ex) {
    return "(unknown)";
}
~~~

Đây là đoạn minh họa trong method trả String, với các biến/method cần định nghĩa. Nó có thể che **nhiều nguyên nhân khác nhau**: repository null, customer null, hay bug xảy ra bên trong getter. Mặc định trả giá trị có thể khiến hệ thống báo thành công dù dữ liệu bị hỏng.

Thay vào đó, kiểm tra **đúng điều kiện null đã được cho phép**:

~~~java
Customer customer = repository.getCustomer(id);
if (customer == null) {
    return "(unknown)"; // Chỉ khi contract cho phép thiếu customer.
}
return Objects.requireNonNull(
        customer.getName(), "customer.name must not be null");
~~~

Đây là đoạn minh họa, cần định nghĩa `Customer`, `repository`, `id` và import Objects. Chính sách là: “không có customer” được chấp nhận, nhưng “có customer mà name null” là lỗi contract. Việc phân biệt này có giá trị lớn hơn một catch NPE rộng.

## 12. NPE có phải luôn là lỗi của caller?

Không thể kết luận chỉ dựa vào class NPE.

Một số tình huống:

| Tình huống | Ai có thể cần sửa? | Gợi ý điều tra |
| --- | --- | --- |
| Caller truyền null vào param đã tài liệu hóa non-null | Caller | Validate tại ranh giới; sửa nơi gọi |
| API cho phép null, implementation dereference không kiểm tra | Implementation | Bổ sung nhánh null phù hợp |
| Getter trả null trái invariant đã cam kết | Tầng tạo/duy trì object | Sửa invariant, khởi tạo và test |
| Map trả null vì thiếu key, caller unbox | Hợp đồng tích hợp hai phía | Quy định cách biểu diễn missing và default |
| Boolean wrapper null do trạng thái “unknown” | Thiết kế domain | Không ép thành primitive trước khi quyết định |
| Một bug nội bộ làm field chưa được khởi tạo | Code sở hữu trạng thái | Sửa logic, không chỉ catch ở UI |

**NPE là tín hiệu về một thao tác không hợp lệ với null**, không tự chỉ ra trách nhiệm cuối cùng. Để xác định trách nhiệm, cần biết **hợp đồng ở ranh giới** và luồng dữ liệu.

### NPE hay IllegalArgumentException khi param null?

Trong Java API có những phương thức dùng `NullPointerException` khi tham chiếu null bị cấm, và có những thư viện chọn `IllegalArgumentException` cho đầu vào sai hợp đồng. Không có một câu trả lời duy nhất dùng đúng cho mọi API.

Gợi ý thực hành:

- Nếu null bị cấm và bạn muốn fail fast, `Objects.requireNonNull` cung cấp một cách diễn đạt trực tiếp bằng NPE.
- Nếu giá trị khác null nhưng sai quy tắc nghiệp vụ, có thể chọn `IllegalArgumentException` hoặc exception nghiệp vụ riêng.
- Nếu null là trường hợp hợp lệ, không nên ném NPE; thực hiện nhánh null theo contract.
- Trong API công khai, **nhất quán, tài liệu hóa rõ và kiểm thử** quan trọng hơn tranh luận tuyệt đối về một class exception.

## 13. Các lỗi thiết kế thường dẫn đến NPE

1. **Trả null nhưng không ghi contract**, khiến caller mặc định rằng kết quả luôn có object.
2. **Unbox wrapper không có null-check**, đặc biệt khi đọc từ Map, ORM hoặc JSON.
3. **Dùng `Boolean` như `boolean`**, quên trạng thái thứ ba có thể là null.
4. **Chỉ kiểm tra object cha**, bỏ qua getter trả null ở bước tiếp theo.
5. **Gọi `value.equals(expected)` khi value có thể null**, thay vì áp dụng cách so sánh null-safe có ý nghĩa.
6. **Tự động default null thành 0/false/rỗng**, làm mất trạng thái thiếu dữ liệu quan trọng.
7. **Cứ bắt NPE rồi retry**: NPE thường liên quan invariant/contract, retry mù có thể không giải quyết bug.
8. **Chỉ đọc phần đầu của method chain trong lỗi**; thực tế có thể là return value giữa chuỗi bị null.
9. **Không kiểm tra result từ Map** trước khi dùng trong phép toán primitive.
10. **Nhầm NPE với ArrayIndexOutOfBoundsException** khi truy cập mảng.
11. **Nhầm `Optional.get()` trên empty với NPE**; đó thường là `NoSuchElementException`.
12. **Tưởng `Optional.of(null)` an toàn**, trong khi API ném NPE; dùng `ofNullable` khi null được phép.
13. **So sánh `Integer == 0` mà quên unboxing**, dù trước đó đã viết mã “tránh dấu chấm”.
14. **Viết test so nguyên văn helpful NPE message**, dễ phụ thuộc JVM/build.
15. **Chèn hàng loạt `Objects.requireNonNull` vào mọi chỗ**, nhưng không xác định null hợp lệ ở đâu, dẫn tới fail fast sai yêu cầu nghiệp vụ.
16. **Nuốt lỗi bằng `catch (Exception)` rồi trả success**, làm bug khó phát hiện và trạng thái không nhất quán.
17. **Tin rằng annotation `@NonNull` luôn được javac kiểm tra sẵn**, trong khi nhiều annotation nullness cần static analyzer/IDE/toolchain riêng.
18. **Không làm test cho giá trị null ở các ranh giới**: constructor, service, parser, repository, DTO.

## 14. Nullness annotations và giới hạn của compiler Java

Java 17 có hệ thống kiểu tĩnh phân biệt **primitive** và **reference**, nhưng ngôn ngữ Java chuẩn không tự coi mọi `String` là non-null. Vì vậy `String name` **không bảo đảm** runtime có giá trị khác null.

Các annotation nullness như `@Nullable`, `@NonNull` hoặc các annotation từ thư viện/framework có thể giúp công cụ phân tích và người đọc hiểu contract. Nhưng:

- Chúng đến từ nhiều chuẩn/thư viện khác nhau và có mức hỗ trợ khác nhau.
- Việc thêm annotation không tự bảo đảm runtime không có NPE.
- `javac` chuẩn không mặc định thực hiện kiểm tra nullness tổng quát cho mọi annotation tùy ý.
- IDE, NullAway, Checker Framework hoặc công cụ phân tích tương tự có thể đưa ra cảnh báo khi được cấu hình; cần hiểu chính xác toolchain dự án.
- Runtime validation vẫn cần thiết khi dữ liệu đến từ nguồn không đáng tin hoặc hợp đồng đòi fail fast.

Ví dụ khai báo `@NonNull String value` chỉ có ý nghĩa cụ thể khi annotation và các công cụ theo dự án được xác định. Đừng tự kết luận một tham số không thể null chỉ vì nhìn thấy một tên annotation.

## 15. Những điều dễ nhầm về Java runtime

### NPE khác lỗi biên dịch

~~~java
String name = null;
int size = name.length();
~~~

Đây là **mã biên dịch hợp lệ**: compiler kiểm tra kiểu và method `String.length()` tồn tại, nhưng không từ chối chỉ vì một tham chiếu runtime bằng null. Lỗi phát sinh **khi thực thi** lời gọi.

Ngược lại, `int number = null;` không biên dịch vì giá trị null không thể được gán trực tiếp cho primitive int. Hai lỗi thuộc hai nhóm khác nhau.

### null không phải object

Không có một đối tượng Java thông thường tên là “null object” để gọi method instance trên nó. `null` là một giá trị tham chiếu đặc biệt. `((Object) null) instanceof Object` trả false; việc cast null sang kiểu tham chiếu thích hợp không tự tạo object mới.

### Không phải lời gọi method nào với null cũng ném NPE tại điểm gọi

~~~java
static void acceptNullable(String value) {
    System.out.println(value == null);
}

acceptNullable(null);
~~~

Đoạn minh họa có thể chạy và in `true` nếu được đặt đúng trong một class/method. Lời gọi `acceptNullable(null)` hợp lệ, vì không có dereference null ở caller; method xử lý null một cách có chủ đích.

Đó là sự khác biệt giữa **truyền null cho tham số** và **gọi method trên receiver null**.

### NPE không chỉ xảy ra trong code do bạn viết

Library có thể chủ động validate và ném NPE nếu một API không chấp nhận null. Ví dụ `Objects.requireNonNull`, `Optional.of(null)` và nhiều factory trong thư viện chuẩn có quy tắc riêng. Hãy đọc Javadoc thay vì dự đoán rằng mọi API đều bỏ qua null hoặc luôn ném cùng kiểu lỗi.

## 16. Bài tập dự đoán hành vi

**Câu 1.** Vì sao hai đoạn sau có kết quả khác?

~~~java
Integer n = null;
boolean a = n == null;
boolean b = n == 0;
~~~

*Đáp án:* `n == null` là so sánh tham chiếu, trả true. `n == 0` yêu cầu unboxing sang int nên ném NPE; biến `b` không được gán giá trị.

**Câu 2.** `Boolean flag = null; if (flag) { ... }` gặp lỗi gì?

*Đáp án:* NPE do Boolean null phải unbox sang boolean trong biểu thức điều kiện.

**Câu 3.** `String[] names = new String[1]; String first = names[0];` có ném NPE không?

*Đáp án:* Không; mảng đã được tạo và đọc phần tử null là hợp lệ. Nhưng `first.length()` sẽ ném NPE.

**Câu 4.** Khi `User user = null;`, `user.email` gây lỗi gì?

*Đáp án:* NPE vì dereference receiver null để đọc instance field.

**Câu 5.** Khi `User user = new User(); user.email == null;`, đọc `user.email` có tự ném NPE không?

*Đáp án:* Không. Đọc một field từ object hợp lệ có thể trả null; dereference giá trị null được trả về mới gây NPE.

**Câu 6.** `"ready".equals(null)` có ném NPE không?

*Đáp án:* Không; receiver literal khác null và equals nhận argument null, trả false.

**Câu 7.** `Objects.equals(null, null)` trả gì?

*Đáp án:* True theo hợp đồng của `Objects.equals`.

**Câu 8.** `Objects.requireNonNull(name, "name required")` khi name null có hành vi gì?

*Đáp án:* Ném NPE tại bước kiểm tra với message do code cung cấp; không tiếp tục dùng name.

**Câu 9.** `Optional.of(null)` và `Optional.ofNullable(null)` khác nhau thế nào?

*Đáp án:* `of(null)` ném NPE; `ofNullable(null)` trả Optional rỗng.

**Câu 10.** Khi method `findUser()` trả null, `findUser().getName()` có thể ném gì?

*Đáp án:* NPE khi gọi `getName()` trên return value null.

**Câu 11.** Tại sao `Map<String, Integer>` không bảo đảm `int n = map.get(key);` an toàn?

*Đáp án:* `get(key)` có thể trả null nếu thiếu key hoặc có mapping null; auto-unboxing null sang int gây NPE.

**Câu 12.** Có nên sửa tất cả NPE bằng cách catch rồi trả chuỗi rỗng?

*Đáp án:* Không. Điều đó có thể che bug hoặc mất trạng thái dữ liệu; phải tìm hợp đồng nullability và sửa ở ranh giới chịu trách nhiệm.

**Câu 13.** Helpful NullPointerException message có nên là hợp đồng JSON trả cho client?

*Đáp án:* Không. Nội dung có thể phụ thuộc JVM/build, tiết lộ chi tiết code và không phải thông điệp nghiệp vụ ổn định.

**Câu 14.** Nếu một method chỉ nhận tham số `String text` và caller truyền null, lời gọi method có tự gây NPE trước khi vào thân không?

*Đáp án:* Không chỉ vì argument null. Nếu thân method hoặc validation yêu cầu non-null, khi đó hành vi phụ thuộc code/API được gọi.

## 17. Checklist thiết kế và kiểm thử

Khi review một method có nguy cơ NPE, tự hỏi:

1. **Input nào có thể null?** Method parameter, field, return value, element, map value, wrapper.
2. **Null có được phép theo hợp đồng không?** Nếu không, validate sớm.
3. **Điểm dereference đầu tiên nằm ở đâu?** Gọi method, field, mảng, unboxing, API không nhận null.
4. **Có wrapper primitive nào đang được implicit unbox không?** Đặc biệt `Integer` và `Boolean`.
5. **Giá trị mặc định có đúng nghiệp vụ không?** Đừng chọn 0/false/rỗng tùy tiện.
6. **API có kết quả nullable?** Nếu có, caller đã xử lý nhánh thiếu chưa?
7. **Các collection có thể chứa null không?** Kiểm tra hợp đồng cụ thể.
8. **Có dùng null-safe equals/instanceof đúng chỗ không?** Không lạm dụng khi cần kiểm tra invariant.
9. **Exception được log có đầy đủ stack trace không?** Đừng chỉ in `ex.getMessage()`.
10. **Test có tách giá trị null, non-null, empty, blank và invalid không?** Đây là các trường hợp khác nhau.
11. **Có static analyzer hỗ trợ nullness không?** Sử dụng nếu có, nhưng không coi annotation như runtime guarantee.
12. **Có đang bắt và nuốt NullPointerException không?** Chỉ catch ở ranh giới có chính sách rõ, tránh che lỗi lập trình.

Một bộ test tối thiểu cho phương thức nhập dữ liệu thường cần: đầu vào hợp lệ, tham chiếu null, chuỗi rỗng, chuỗi toàn khoảng trắng, trường nested nullable, wrapper null và dữ liệu trả về từ repository không tìm thấy. Những tình huống cụ thể phụ thuộc contract của hệ thống.

## 18. Tài liệu chính thức Java 17

- [Java SE 17 API – NullPointerException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NullPointerException.html): loại lỗi và ví dụ tình huống chương trình dùng null không hợp lệ.
- [Java SE 17 API – Objects](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html): `requireNonNull`, `equals`, `requireNonNullElse` và các utility liên quan.
- [JLS Java SE 17 mục 5.1.8 – Unboxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.8): unboxing wrapper và quy tắc NPE khi giá trị null.
- [JLS Java SE 17 mục 15.12.4 – Run-Time Evaluation of Method Invocation](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12.4): điều kiện thực thi method invocation và receiver.
- [JLS Java SE 17 mục 15.11 – Field Access Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.11): đánh giá truy cập field, bao gồm trường hợp receiver null.
- [JLS Java SE 17 mục 15.10.4 – Run-Time Evaluation of Array Access Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.10.4): truy cập array và điều kiện lỗi.
- [JLS Java SE 17 mục 14.18 – The throw Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.18): ngoại lệ khi biểu thức được throw có giá trị null.
- [JEP 358 – Helpful NullPointerExceptions](https://openjdk.org/jeps/358): ngữ nghĩa và giới hạn của thông điệp mô tả NPE do JVM tạo.
- [Java SE 17 API – Optional](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html): phân biệt `of`, `ofNullable` và Optional rỗng.

**Kết luận:** `NullPointerException` là unchecked exception phát sinh khi Java cần tham chiếu khác null nhưng gặp null. Ba nhóm đáng kiểm tra nhất là **dereference**, **unboxing wrapper null**, và **đầu vào/giá trị trả về nullable bị sử dụng trái hợp đồng**. Phòng ngừa đúng cách không phải “chặn mọi null”, mà là định nghĩa rõ **null có nghĩa gì**, validate ở ranh giới chịu trách nhiệm và chỉ dùng default khi hợp đồng thực sự cho phép.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-runtime-exceptions-002">IllegalArgumentException và IllegalStateException</a>

<details>
<summary>Xem chi tiết</summary>

# IllegalArgumentException và IllegalStateException trong Java

`IllegalArgumentException` và `IllegalStateException` là hai ngoại lệ **unchecked** quan trọng của Java, đều kế thừa `RuntimeException`. Chúng có thể cùng xuất hiện khi một lời gọi phương thức không thể được chấp nhận, nhưng mô tả **hai nguyên nhân vi phạm hợp đồng khác nhau**:

- **`IllegalArgumentException`**: *đối số do caller cung cấp không hợp lệ* theo tiền điều kiện của phương thức. Ví dụ `setAge(-2)` khi tuổi phải không âm, hoặc `setPort(70000)` khi cổng chỉ cho phép 1–65535.
- **`IllegalStateException`**: *trạng thái của đối tượng/hệ thống tại thời điểm gọi không cho phép thực hiện thao tác*. Ví dụ `send()` trước `connect()`, `write()` sau `close()`, hoặc `confirm()` khi đơn hàng đã bị hủy.

Cả hai không phải là “mức độ nghiêm trọng” khác nhau; **sự khác biệt nằm ở lý do lời gọi vi phạm hợp đồng**. Nếu caller chỉ cần sửa giá trị được truyền để lời gọi hợp lệ, `IllegalArgumentException` thường đúng hơn. Nếu cùng đối số ấy có thể hợp lệ tại một thời điểm khác sau khi trạng thái đổi, `IllegalStateException` thường diễn đạt tốt hơn.

Bài này trình bày phân cấp lớp, đặc điểm checked/unchecked, tiêu chí lựa chọn, các trường hợp giao thoa, ví dụ Java 17 biên dịch/chạy được, constructor/message/cause, JavaDoc, kiểm thử, đồng thời lưu ý rằng không phải mọi lỗi đầu vào hoặc lỗi nghiệp vụ đều nên biểu diễn bằng exception.

## 1. Vị trí trong hệ phân cấp ngoại lệ Java

Sơ đồ rút gọn:

```text
java.lang.Object
└── java.lang.Throwable
    ├── java.lang.Error
    └── java.lang.Exception
        └── java.lang.RuntimeException
            ├── java.lang.IllegalArgumentException
            │   └── java.lang.NumberFormatException
            ├── java.lang.IllegalStateException
            ├── java.lang.NullPointerException
            └── java.lang.IndexOutOfBoundsException
```

Những điều cần nắm:

1. `IllegalArgumentException` và `IllegalStateException` là **hai lớp cùng nằm trực tiếp dưới `RuntimeException`**, không phải lớp cha/con của nhau.
2. `NumberFormatException` là một lớp con của `IllegalArgumentException`. Khi `Integer.parseInt("abc")` thất bại, caller có thể bắt bằng `catch (NumberFormatException e)` hoặc một handler rộng hơn `catch (IllegalArgumentException e)`.
3. `NullPointerException` và `IndexOutOfBoundsException` cũng là unchecked exception, nhưng **không** phải lớp con của `IllegalArgumentException`. Chúng có ý nghĩa riêng.
4. `catch (RuntimeException e)` có thể bắt cả `IllegalArgumentException` lẫn `IllegalStateException`, nhưng vì quá rộng, handler phải thực sự có chính sách thích hợp nếu muốn xử lý cả hai.
5. `catch (Exception e)` cũng bắt cả hai vì `RuntimeException extends Exception`; điều này không có nghĩa chúng trở thành checked exception.

Hai lớp `IllegalArgumentException` và `IllegalStateException` được cung cấp sẵn trong `java.lang`, vì vậy mã Java thông thường không cần `import` để sử dụng chúng.

## 2. Vì sao chúng là unchecked exception?

Một **checked exception** nằm trong phần của nhánh `Exception` không thuộc `RuntimeException`. Khi một phương thức có thể ném checked exception, Java yêu cầu mã gọi bắt hoặc khai báo nó (`catch-or-declare`) theo quy tắc kiểm tra tại thời điểm biên dịch.

Hai exception đang học là **unchecked**:

```java
static void checkQuantity(int quantity) {
    if (quantity <= 0) {
        throw new IllegalArgumentException(
                "quantity phải lớn hơn 0");
    }
}
```

Đây là **phương thức minh họa**, không có `main`. Không cần ghi:

```java
static void checkQuantity(int quantity)
        throws IllegalArgumentException {
    // ...
}
```

Mệnh đề `throws IllegalArgumentException` là hợp lệ nhưng **không bắt buộc** và không khiến caller bị buộc viết `catch`. Điều tương tự đúng với `IllegalStateException`.

**Unchecked không có nghĩa được Java tự xử lý.** Khi một trong hai exception được ném, luồng thực thi bị ngắt bất thường và exception lan lên call stack cho đến khi có handler tương thích. Nếu không có handler thích hợp, luồng xử lý có thể kết thúc thất bại; cách chương trình và môi trường phản ứng còn phụ thuộc nơi exception được ném.

Hãy phân biệt ba việc:

- **Bắt buộc bởi compiler:** không, đối với cả hai.
- **Có thể chủ động bắt:** có, khi tầng đang xét biết cách phục hồi hoặc phản hồi.
- **Cần thiết kế hợp đồng và giám sát lỗi:** có, dù không ghi `throws`.

## 3. Câu hỏi quyết định: sai đối số hay sai trạng thái?

Khi cân nhắc hai exception, hãy hỏi:

**(A) Caller đã truyền một giá trị không nằm trong miền giá trị hợp lệ của phương thức?**

Nếu đúng, `IllegalArgumentException` là ứng viên tự nhiên. Ví dụ một method `setRetryCount(int count)` quy định `count >= 0` và nhận `-1`.

**(B) Lời gọi bị từ chối chủ yếu do trạng thái hiện tại của đối tượng hoặc thứ tự các thao tác?**

Nếu đúng, `IllegalStateException` thường thích hợp. Ví dụ `write()` chỉ được gọi sau `open()` và trước `close()`.

**(C) Cả hai điều kiện cùng có thể sai?**

Hãy xác định **hợp đồng nào được kiểm tra trước**, đâu là nguyên nhân chính cần phản hồi, và giữ thứ tự kiểm tra ổn định, dễ hiểu. Đừng biến việc chọn exception thành suy luận về mức độ lỗi; hãy diễn đạt điều caller cần sửa.

Bảng ngắn:

| Lời gọi | Điều kiện vi phạm | Lựa chọn thường phù hợp |
| --- | --- | --- |
| `setVolume(-1)` | Âm, ngoài miền được cho phép | `IllegalArgumentException` |
| `setPort(70000)` | Cổng vượt giới hạn | `IllegalArgumentException` |
| `connect(null)` | Thiếu đối tượng bắt buộc | Thường `NullPointerException` / `Objects.requireNonNull` |
| `send(data)` trước `connect()` | Chưa có kết nối đang hoạt động | `IllegalStateException` |
| `commit()` hai lần khi chỉ cho phép một lần | Gọi sai pha thực thi | `IllegalStateException` |
| `Iterator.remove()` trước `next()` | Thao tác sai trạng thái iterator | `IllegalStateException` theo hợp đồng Iterator |
| `Integer.parseInt("x")` | Chuỗi không ở định dạng số hợp lệ | `NumberFormatException`, một loại `IllegalArgumentException` |
| `list.get(100)` khi chỉ có vài phần tử | Chỉ số ngoài phạm vi | `IndexOutOfBoundsException`, không phải `IllegalArgumentException` |
| `requireNonNull(null)` | Tham chiếu null vi phạm tiền điều kiện | `NullPointerException` |
| Giao diện báo sản phẩm hết hàng | Nhánh nghiệp vụ bình thường | Thường kiểu kết quả nghiệp vụ; không tự mặc định dùng một trong hai |

Các ví dụ trên minh họa **ý nghĩa hợp đồng**; với API thư viện thực tế, hãy ưu tiên đúng exception mà JavaDoc API ấy đã quy định.

## 4. Ví dụ chạy được: kiểm tra đối số bằng `IllegalArgumentException`

Lưu **`IllegalArgumentDemo.java`**:

```java
public class IllegalArgumentDemo {
    static int percentOf(int value, int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException(
                    "percent phải thuộc [0, 100]: " + percent);
        }
        return value * percent / 100;
    }

    public static void main(String[] args) {
        System.out.println("Hợp lệ: " + percentOf(200, 25));

        try {
            percentOf(200, 120);
            System.out.println("Không tới dòng này");
        } catch (IllegalArgumentException e) {
            System.out.println("Bắt: " + e.getMessage());
        }

        System.out.println("Kết thúc");
    }
}
```

Biên dịch và chạy với Java 17+:

```bash
javac -encoding UTF-8 --release 17 IllegalArgumentDemo.java
java IllegalArgumentDemo
```

**Stdout chính xác** trên console hỗ trợ UTF-8:

```text
Hợp lệ: 50
Bắt: percent phải thuộc [0, 100]: 120
Kết thúc
```

Phân tích:

1. Giá trị `percent = 25` nằm trong miền hợp lệ, method trả `50`.
2. `percent = 120` vi phạm tiền điều kiện độc lập với trạng thái của đối tượng.
3. Method ném `IllegalArgumentException` trước khi tính toán.
4. `catch` bắt lỗi, in thông điệp rồi tiếp tục sau khối xử lý.
5. Dòng `"Không tới dòng này"` không được in vì lệnh `percentOf(200, 120)` kết thúc bất thường.

Điều caller cần sửa là **giá trị tham số**, không phải gọi `open()` hay chuyển pha trạng thái.

Lưu ý ví dụ dùng `int` để minh họa tiền điều kiện; các hệ thống tài chính không nên mặc định dùng công thức nhân/chia số nguyên như mẫu nghiệp vụ chính thức vì có vấn đề làm tròn và tràn số.

## 5. Ví dụ chạy được: trạng thái đối tượng bằng `IllegalStateException`

Lưu **`IllegalStateDemo.java`**:

```java
public class IllegalStateDemo {
    static class Session {
        private boolean connected;

        void connect() {
            if (connected) {
                throw new IllegalStateException(
                        "Phiên đã kết nối");
            }
            connected = true;
            System.out.println("Đã kết nối");
        }

        void send(String message) {
            if (!connected) {
                throw new IllegalStateException(
                        "Phải connect trước khi send");
            }
            System.out.println("Đã gửi: " + message);
        }

        void disconnect() {
            if (!connected) {
                throw new IllegalStateException(
                        "Phiên chưa kết nối");
            }
            connected = false;
            System.out.println("Đã ngắt kết nối");
        }
    }

    public static void main(String[] args) {
        Session session = new Session();

        try {
            session.send("hello");
        } catch (IllegalStateException e) {
            System.out.println("Bắt: " + e.getMessage());
        }

        session.connect();
        session.send("hello");
        session.disconnect();

        try {
            session.send("again");
        } catch (IllegalStateException e) {
            System.out.println("Bắt: " + e.getMessage());
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 IllegalStateDemo.java
java IllegalStateDemo
```

**Stdout chính xác**:

```text
Bắt: Phải connect trước khi send
Đã kết nối
Đã gửi: hello
Đã ngắt kết nối
Bắt: Phải connect trước khi send
```

Thông điệp `"hello"` hoàn toàn hợp lệ. Vấn đề của lời gọi đầu là **session chưa kết nối**. Sau `connect()`, cùng một lời gọi `send("hello")` thành công. Khi đã `disconnect()`, lời gọi `send("again")` lại không được phép.

Đây là dấu hiệu đặc trưng của `IllegalStateException`: **lời gọi hợp lệ phụ thuộc vào vòng đời/trạng thái đối tượng**. Trong ví dụ, trạng thái được tối giản thành biến `boolean`; mã mạng thật còn có thể có lỗi I/O, timeout, ngắt kết nối bất ngờ và vấn đề đồng bộ đa luồng.

## 6. Một method có thể dùng cả hai loại exception

Có nhiều API cần kiểm tra cả **đối số** và **trạng thái**. Ví dụ một công cụ cấu hình chỉ cho phép sửa tham số trước khi được kích hoạt:

```java
void setMaxConnections(int value) {
    if (value <= 0) {
        throw new IllegalArgumentException(
                "maxConnections phải dương");
    }
    if (started) {
        throw new IllegalStateException(
                "Không thể đổi sau khi start");
    }
    maxConnections = value;
}
```

Đây là **phương thức minh họa** trong một class có field `started` và `maxConnections`. Hai nhánh có lý do khác nhau:

- `value <= 0`: tham số sai; caller phải chọn một số dương.
- `started == true`: trạng thái sai; caller phải đổi cấu hình trước khi khởi động hoặc dùng một API cấu hình nóng được hỗ trợ.

Thứ tự kiểm tra trong ví dụ ưu tiên bắt đối số không hợp lệ trước. Nếu đang chạy rồi caller gọi `setMaxConnections(-5)`, exception nhận được là `IllegalArgumentException`. Nếu đảo thứ tự kiểm tra, caller sẽ nhận `IllegalStateException` cho cùng tình huống. **Java không tự quyết định thứ tự ưu tiên**; đó là một phần thiết kế hợp đồng.

Cần chọn thứ tự có chủ đích, đặc biệt đối với API công khai. Khi nhiều điều kiện đều sai, tài liệu và test nên phản ánh điều kiện nào được báo trước nếu điều đó quan trọng đối với caller.

## 7. Chương trình đầy đủ: đối số và trạng thái cùng tồn tại

Lưu **`ArgumentAndStateDemo.java`**:

```java
public class ArgumentAndStateDemo {
    static class Job {
        private int priority = 1;
        private boolean submitted;

        void changePriority(int priority) {
            if (priority < 1 || priority > 5) {
                throw new IllegalArgumentException(
                        "priority phải từ 1 đến 5");
            }
            if (submitted) {
                throw new IllegalStateException(
                        "Job đã submit");
            }
            this.priority = priority;
        }

        void submit() {
            if (submitted) {
                throw new IllegalStateException(
                        "Không submit hai lần");
            }
            submitted = true;
        }

        int priority() {
            return priority;
        }
    }

    public static void main(String[] args) {
        Job job = new Job();

        try {
            job.changePriority(0);
        } catch (IllegalArgumentException e) {
            System.out.println("Đối số: " + e.getMessage());
        }

        job.changePriority(3);
        job.submit();

        try {
            job.changePriority(4);
        } catch (IllegalStateException e) {
            System.out.println("Trạng thái: " + e.getMessage());
        }

        System.out.println("Priority hiện tại: " + job.priority());
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 ArgumentAndStateDemo.java
java ArgumentAndStateDemo
```

**Stdout chính xác**:

```text
Đối số: priority phải từ 1 đến 5
Trạng thái: Job đã submit
Priority hiện tại: 3
```

Ở lần đầu, `priority = 0` không thuộc miền cho phép dù job chưa submit. Lần tiếp theo `priority = 3` hợp lệ, cập nhật thành công. Sau khi `submit()` chuyển trạng thái, `changePriority(4)` dùng một giá trị **hợp lệ về mặt miền**, nhưng không thể áp dụng ở trạng thái đã submit. Giá trị cũ `3` được giữ lại.

Ví dụ cũng cho thấy **không để exception xảy ra sau khi đã sửa một phần trạng thái quan trọng**, nếu có thể kiểm tra trước. Đây là một nguyên tắc thiết kế API đáng lưu ý.

## 8. Một câu hỏi khó: đối số “đúng dạng” nhưng không hợp lệ trong trạng thái hiện tại?

Ví dụ gọi `moveTo("PAID")` khi đơn hàng đang ở `CANCELLED`. `"PAID"` có thể là tên trạng thái hợp lệ trong hệ thống, nhưng **chuyển từ CANCELLED sang PAID** lại bị cấm.

Có hai cách nhìn:

- Nếu hợp đồng mô tả **đối số `targetState` chỉ được nằm trong tập các trạng thái có thể chuyển đến từ trạng thái hiện tại**, thì đối số có thể được coi là không hợp lệ.
- Nếu hợp đồng mô tả **đối tượng đã ở trạng thái không cho phép bất kỳ thao tác chuyển này**, `IllegalStateException` có thể là lựa chọn tự nhiên hơn.

Một quy ước hữu ích là phân biệt:

1. **Giá trị không hợp lệ ở mọi trạng thái** → thiên về `IllegalArgumentException`. Ví dụ tên trạng thái không tồn tại.
2. **Giá trị hợp lệ nhưng thao tác bị cấm do trạng thái hiện tại** → thiên về `IllegalStateException`.
3. **Kết quả nghiệp vụ dự kiến như “đơn không thể thanh toán vì đã hủy”** → cân nhắc trả kiểu kết quả domain thay vì ném unchecked exception.

Đây là vùng **có lựa chọn thiết kế**, không phải chỗ Java áp đặt một đáp án duy nhất. Điều quan trọng hơn việc tranh luận tên loại là **tính nhất quán, khả năng hiểu hợp đồng và chính sách phản hồi thực tế**.

## 9. `NumberFormatException` là trường hợp cụ thể của `IllegalArgumentException`

Một ví dụ JDK phổ biến:

```java
int value = Integer.parseInt("123");
```

Khi chuỗi không phân tích được thành số nguyên, `Integer.parseInt` ném `NumberFormatException`, là **lớp con của `IllegalArgumentException`**.

Ví dụ Java 17 độc lập **`NumberFormatHierarchyDemo.java`**:

```java
public class NumberFormatHierarchyDemo {
    public static void main(String[] args) {
        try {
            Integer.parseInt("abc");
        } catch (IllegalArgumentException e) {
            System.out.println("Loại: "
                    + e.getClass().getSimpleName());
            System.out.println("Là NumberFormatException: "
                    + (e instanceof NumberFormatException));
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 NumberFormatHierarchyDemo.java
java NumberFormatHierarchyDemo
```

**Stdout chính xác**:

```text
Loại: NumberFormatException
Là NumberFormatException: true
```

Dù handler khai báo `IllegalArgumentException`, object runtime thực tế vẫn là `NumberFormatException`. Điều này có ích nếu tầng hiện tại muốn phản hồi chung cho mọi đối số sai.

Nếu cần xử lý khác nhau, handler cụ thể phải đứng trước handler cha:

```java
try {
    parseInput();
} catch (NumberFormatException e) {
    handleNumberFormat(e);
} catch (IllegalArgumentException e) {
    handleOtherInvalidArgument(e);
}
```

Đây là **khung minh họa**, các phương thức phụ cần được định nghĩa. Không được đảo hai handler khi handler cha đứng trước khiến handler con trở nên không thể tới được.

## 10. Đừng dùng `IllegalArgumentException` thay cho mọi lỗi đầu vào

Một số API JDK dùng **lớp unchecked chuyên biệt**:

- Tham chiếu bắt buộc bằng `null` có thể dẫn tới `NullPointerException`, đặc biệt khi dùng `Objects.requireNonNull`.
- Chỉ số ngoài phạm vi trong collection có thể là `IndexOutOfBoundsException`.
- Chuỗi số không hợp lệ thường là `NumberFormatException` (một nhánh của `IllegalArgumentException`).
- Một phương thức có thể chọn **custom exception** để caller phân biệt lỗi nghiệp vụ đặc thù.

Ví dụ `Objects.requireNonNull`:

```java
String normalized(String value) {
    java.util.Objects.requireNonNull(
            value, "value không được null");
    if (value.isBlank()) {
        throw new IllegalArgumentException(
                "value không được rỗng");
    }
    return value.trim();
}
```

Đây là **phương thức minh họa**. `null` được phân biệt khỏi chuỗi rỗng về mặt hợp đồng: đầu tiên ném `NullPointerException` có message rõ ràng, còn chuỗi chỉ chứa khoảng trắng bị từ chối bằng `IllegalArgumentException`.

Không có luật Java ép mọi mã ứng dụng dùng một tổ hợp exception như trên. Nhưng việc sử dụng các loại JDK đúng ngữ nghĩa giúp caller đọc API và dự đoán lỗi tốt hơn.

## 11. Lỗi “sai đối số” có thể đến từ dữ liệu người dùng

Một tham số sai không nhất thiết là bug của lập trình viên. Đầu vào từ HTTP, biểu mẫu, file hoặc CLI có thể sai một cách **dự kiến**. `IllegalArgumentException` vẫn có thể được ném ở một API yêu cầu tiền điều kiện chặt, nhưng nơi tiếp nhận dữ liệu bên ngoài cần cân nhắc cách **chuyển sang phản hồi hợp lệ**.

Ví dụ người dùng nhập `-5` cho số lượng. Nếu đây là một nhánh thường gặp trong UI, giải pháp có thể tốt hơn là:

1. Kiểm tra giá trị ở **ranh giới nhập liệu**.
2. Trả tập lỗi xác thực có cấu trúc, chỉ ra trường nào không hợp lệ.
3. Chỉ đưa đối tượng hợp lệ vào domain service.
4. Dùng `IllegalArgumentException` như một **hàng rào bảo vệ invariant API**, không làm cơ chế điều khiển mọi nhánh UI bình thường.

Một exception unchecked không nên vô tình biến yêu cầu người dùng sai thành lỗi máy chủ chung. Hệ thống HTTP thường cần ánh xạ thành phản hồi 4xx hoặc thông điệp nghiệp vụ thích hợp theo thiết kế, nhưng **không thể suy từ superclass một mã HTTP duy nhất đúng cho mọi tình huống**.

Điều cần tránh là bắt `IllegalArgumentException` tại ranh giới rồi luôn giả định “đó chắc chắn là lỗi của người dùng”. Nó có thể phát sinh từ **bug ở code nội bộ**, thư viện hoặc một lời gọi sai khác bên trong service.

## 12. `IllegalStateException` không chỉ áp dụng cho một đối tượng đơn lẻ

“State” có thể là:

- Vòng đời đối tượng: created → open → closed.
- Pha giao dịch: new → active → committed/rolled back.
- Trạng thái workflow: draft → approved → published.
- Trạng thái tài nguyên: connected/disconnected, initialized/uninitialized.
- Trạng thái điều phối ở một thành phần: hệ thống chưa sẵn sàng nhận tác vụ.
- Một invariant đã bị vi phạm nên không thể thực hiện hành động hiện tại.

Ví dụ bộ đọc dữ liệu:

```java
class DataReader {
    private boolean open;

    void open() {
        if (open) {
            throw new IllegalStateException("Đã mở");
        }
        open = true;
    }

    String read() {
        if (!open) {
            throw new IllegalStateException(
                    "Chưa mở hoặc đã đóng");
        }
        return "data";
    }

    void close() {
        open = false;
    }
}
```

Đây là **class minh họa không có `main`**. Các method `open` và `read` có hợp đồng phụ thuộc trạng thái. `close` trong ví dụ được thiết kế idempotent (gọi nhiều lần không ném lỗi); đây là **lựa chọn thiết kế** chứ không phải mọi resource Java đều phải từ chối hoặc chấp nhận đóng hai lần theo cùng một cách.

Khi một API thực có giao diện `AutoCloseable` hoặc `Closeable`, hãy tuân thủ hợp đồng đóng tài nguyên cụ thể, kể cả checked `IOException` nếu được khai báo, thay vì mặc định thay thế mọi lỗi đóng bằng `IllegalStateException`.

## 13. Mô hình state machine giúp quy tắc rõ ràng hơn

Khi có nhiều trạng thái, thay vì một số boolean dễ mâu thuẫn, có thể dùng `enum` và bảng chuyển trạng thái:

| Trạng thái hiện tại | Hành động | Trạng thái tiếp theo / Kết quả |
| --- | --- | --- |
| `NEW` | `start()` | `RUNNING` |
| `RUNNING` | `finish()` | `DONE` |
| `NEW` | `finish()` | `IllegalStateException` |
| `DONE` | `start()` | `IllegalStateException` |
| `RUNNING` | `start()` | `IllegalStateException` |

Nếu một hành động nhận đối số, có thể thêm quy tắc riêng về miền hợp lệ của đối số và dùng `IllegalArgumentException` khi thích hợp.

Ví dụ runnable **`StateMachineDemo.java`**:

```java
public class StateMachineDemo {
    enum Phase {
        NEW, RUNNING, DONE
    }

    static class Task {
        private Phase phase = Phase.NEW;

        void start() {
            if (phase != Phase.NEW) {
                throw new IllegalStateException(
                        "start chỉ hợp lệ ở NEW");
            }
            phase = Phase.RUNNING;
        }

        void finish() {
            if (phase != Phase.RUNNING) {
                throw new IllegalStateException(
                        "finish chỉ hợp lệ ở RUNNING");
            }
            phase = Phase.DONE;
        }

        Phase phase() {
            return phase;
        }
    }

    public static void main(String[] args) {
        Task task = new Task();

        try {
            task.finish();
        } catch (IllegalStateException e) {
            System.out.println("Bắt: " + e.getMessage());
        }

        task.start();
        task.finish();
        System.out.println("Phase: " + task.phase());

        try {
            task.start();
        } catch (IllegalStateException e) {
            System.out.println("Bắt: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 StateMachineDemo.java
java StateMachineDemo
```

**Stdout chính xác**:

```text
Bắt: finish chỉ hợp lệ ở RUNNING
Phase: DONE
Bắt: start chỉ hợp lệ ở NEW
```

Một state machine minh bạch giúp quyết định lúc nào lỗi là **trạng thái không phù hợp** và ngăn thao tác làm mất invariant. Trong ứng dụng phức tạp, còn phải xét transaction, timeout và concurrency, nhưng cốt lõi vẫn là **kiểm tra trạng thái trước khi chuyển đổi**.

## 14. Constructor và cách đặt message cho hai exception

Cả `IllegalArgumentException` và `IllegalStateException` đều có các constructor quen thuộc:

```java
new IllegalArgumentException();
new IllegalArgumentException("Sai giá trị");
new IllegalArgumentException(new RuntimeException("gốc"));
new IllegalArgumentException("Không thể nhận giá trị", cause);

new IllegalStateException();
new IllegalStateException("Chưa khởi tạo");
new IllegalStateException(new RuntimeException("gốc"));
new IllegalStateException("Không thể đổi trạng thái", cause);
```

Đây là **các biểu thức minh họa**, với biến `cause` kiểu `Throwable` được giả định đã khai báo. Cả hai lớp cho phép truyền detail message và cause theo constructor.

Mục đích:

- **Message** giải thích hợp đồng nào bị vi phạm, kỳ vọng gì, và giá trị/trạng thái hiện tại là gì nếu được phép tiết lộ.
- **Cause** giữ một Throwable phát sinh trước đó khi bạn chủ động chuyển abstraction.
- **Stack trace** giúp xác định nơi object exception mới được tạo.
- **Suppressed exceptions** là dữ liệu riêng trên `Throwable`, không tự được gắn chỉ vì chọn một trong hai lớp này.

Message có thể là `null` nếu constructor được gọi mà không cung cấp chi tiết. Tuy nhiên, trong API công khai có nhiều nhánh lỗi, message rõ ràng giúp giảm thời gian chẩn đoán.

Tránh message chỉ ghi `"invalid"` hoặc `"bad state"` mà không có ngữ cảnh.

## 15. Viết message để caller và người bảo trì hiểu cách sửa

Một thông điệp tốt thường bao gồm:

1. **Tên tham số hoặc tên thao tác**: `timeoutMillis`, `priority`, `send`.
2. **Điều kiện hợp lệ mong đợi**: phải dương, nằm trong khoảng, phải gọi `open` trước.
3. **Dữ liệu thực tế thích hợp**: ví dụ `-1` hoặc phase `CLOSED`.
4. **Không đưa thông tin nhạy cảm**: token, mật khẩu, dữ liệu định danh hoặc nội dung request bí mật.
5. **Không dùng văn bản message làm mã lỗi ổn định**; nội dung có thể thay đổi hoặc được bản địa hóa.

Ví dụ message cho `IllegalArgumentException`:

```java
throw new IllegalArgumentException(
        "pageSize phải thuộc [1, 100], nhận: " + pageSize);
```

Ví dụ message cho `IllegalStateException`:

```java
throw new IllegalStateException(
        "Không thể send khi phase=" + phase
        + "; cần phase=CONNECTED");
```

Đây là **hai câu lệnh minh họa**, giả định biến `pageSize` và `phase` nằm trong phạm vi. Chúng cung cấp điều kiện sửa rõ hơn là chỉ ghi `"error"`.

Trong API trả lỗi ra người dùng, nên chuyển exception thành mã lỗi/trạng thái hiển thị được biên soạn, không phát tán message kỹ thuật không qua rà soát.

## 16. `IllegalArgumentException`, `NullPointerException` và hợp đồng null

Lựa chọn liên quan null cần nhất quán. Nếu API không chấp nhận `null`, Java cung cấp:

```java
import java.util.Objects;

class NameService {
    static String normalize(String name) {
        Objects.requireNonNull(name, "name không được null");
        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "name không được chỉ có khoảng trắng");
        }
        return name.strip();
    }
}
```

Đây là **class minh họa không có `main`**, dùng Java 17. `Objects.requireNonNull` ném `NullPointerException` nếu `name == null`. Chuỗi chỉ gồm khoảng trắng bị ném `IllegalArgumentException` theo hợp đồng riêng.

Nếu một codebase đã chọn `IllegalArgumentException` cho mọi loại input sai kể cả null, đó có thể là quy ước nội bộ; nhưng khi sử dụng một API JDK có contract cụ thể, **không nên thay thế dự đoán về exception đã được tài liệu hóa**.

Quan trọng: `Objects.requireNonNull` chỉ xác thực điều kiện không null; nó không tự bảo đảm chuỗi không rỗng, dữ liệu đúng định dạng, hoặc trạng thái đối tượng hợp lệ.

## 17. Có nên dùng `IllegalStateException` để bọc lỗi hệ thống không?

`IllegalStateException` có constructor với `cause`, nên có thể dùng để mô tả “thành phần không sẵn sàng vì một lỗi thấp hơn”. Nhưng điều đó nên **phù hợp với ngữ nghĩa** của API.

Ví dụ code cấp dịch vụ có thể không muốn công bố checked `IOException` và muốn thể hiện rằng không thể tiếp tục vì trạng thái khởi tạo:

```java
try {
    initializeFromFile();
} catch (java.io.IOException e) {
    throw new IllegalStateException(
            "Không thể khởi tạo cấu hình", e);
}
```

Đây là **đoạn minh họa**, cần `initializeFromFile()` được định nghĩa.

Lưu ý:

- `IllegalStateException` mới có stack trace ở nơi tạo wrapper.
- `getCause()` giữ `IOException` gốc, gồm thông điệp/trace riêng.
- Wrapper là unchecked, nên hợp đồng checked exception của tầng gọi đã đổi.
- Lựa chọn này chỉ phù hợp nếu lỗi thật sự biểu thị trạng thái không thể thực hiện thao tác của tầng hiện tại.
- Nếu lỗi I/O là một phần quan trọng của hợp đồng cần caller xử lý cụ thể, tiếp tục khai báo `IOException` hoặc dùng một checked domain exception có thể thích hợp hơn.

Không viết `throw new IllegalStateException(e.getMessage())` nếu mục tiêu giữ nguyên nhân: cách đó **không gắn `cause`**.

## 18. Ví dụ Java 17: bọc cause và nhận diện loại runtime

Lưu **`CauseDemo.java`**:

```java
import java.io.IOException;

public class CauseDemo {
    static void readConfig() throws IOException {
        throw new IOException("Tệp cấu hình hỏng");
    }

    static void startService() {
        try {
            readConfig();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Service chưa sẵn sàng", e);
        }
    }

    public static void main(String[] args) {
        try {
            startService();
        } catch (IllegalStateException e) {
            System.out.println("Loại: "
                    + e.getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
            System.out.println("Cause: "
                    + e.getCause().getClass().getSimpleName());
            System.out.println("Cause message: "
                    + e.getCause().getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 CauseDemo.java
java CauseDemo
```

**Stdout chính xác**:

```text
Loại: IllegalStateException
Message: Service chưa sẵn sàng
Cause: IOException
Cause message: Tệp cấu hình hỏng
```

Wrapper cho phép tầng service bổ sung ngữ cảnh mà không làm mất nguyên nhân. Tuy nhiên, bọc một lỗi kỹ thuật thành `IllegalStateException` phải được cân nhắc tương thích API; caller vốn bắt `IOException` sẽ không còn nhận nó trực tiếp.

Nếu exception bị bọc rồi lại bọc ở nhiều tầng, hãy tránh tạo nhiều wrapper lặp thông tin mà không tăng ngữ cảnh. Cause chain có ích nhưng quá nhiều lớp bọc làm trace dài và chẩn đoán khó đọc.

## 19. `catch` cụ thể và thứ tự handler

Hai loại là sibling classes: `IllegalArgumentException` không kế thừa `IllegalStateException` và ngược lại. Có thể bắt riêng:

```java
try {
    doWork();
} catch (IllegalArgumentException e) {
    reportInvalidArgument(e);
} catch (IllegalStateException e) {
    reportInvalidState(e);
}
```

Đây là **mã khung minh họa**, các phương thức phải được định nghĩa. Hai handler cho phép chọn phản ứng khác nhau theo **hợp đồng của operation**.

Nếu muốn xử lý giống nhau, multi-catch cũng hợp lệ:

```java
catch (IllegalArgumentException | IllegalStateException e) {
    System.out.println("Không thực hiện được: " + e.getMessage());
}
```

Đây là **đoạn handler minh họa**. Tham số multi-catch ngầm định `final`, không thể gán lại.

Vì cả hai đều là `RuntimeException`, một `catch (RuntimeException e)` có thể bắt chúng cùng những runtime exception khác. Chọn handler rộng chỉ khi ranh giới đó thực sự đủ trách nhiệm xử lý mọi loại đã bắt; không nuốt tất cả exception chỉ để chương trình tiếp tục.

Nếu có `catch (RuntimeException e)` cùng các catch cụ thể, **handler cụ thể phải đứng trước** handler cha để tránh unreachable catch.

## 20. Cách kết nối với API đã tồn tại trong JDK

Nhìn một số contract tiêu biểu:

- `Iterator.remove()` có thể ném `IllegalStateException` khi `next()` chưa được gọi hoặc `remove()` đã được gọi sau `next()` gần nhất.
- `java.util.Scanner` có thể ném `IllegalStateException` nếu scanner đã đóng và gọi các thao tác không được phép.
- `Integer.parseInt` dùng `NumberFormatException` đối với chuỗi không parse được.
- `Objects.requireNonNull` dùng `NullPointerException` khi đối số bị thiếu.
- `List.get(index)` thường ném `IndexOutOfBoundsException` nếu chỉ số sai.

Tuy nhiên, không nên suy diễn rằng **mọi lớp JDK “đã đóng” đều phải ném `IllegalStateException`**. Ví dụ một số API stream hoặc file có thể ném `IOException`, `UncheckedIOException`, hoặc loại chuyên biệt theo hợp đồng riêng.

Cách học đúng là hiểu **ý nghĩa chung** của hai exception, rồi đọc JavaDoc chính xác của từng API để biết những điều kiện và loại exception mà API đó cam kết.

## 21. Ví dụ: `Iterator.remove()` và trạng thái của iterator

`Iterator.remove()` là một ví dụ có ý nghĩa rõ về trạng thái gọi phương thức.

Lưu **`IteratorStateDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class IteratorStateDemo {
    public static void main(String[] args) {
        List<String> values =
                new ArrayList<>(Arrays.asList("A", "B"));
        Iterator<String> it = values.iterator();

        try {
            it.remove();
        } catch (IllegalStateException e) {
            System.out.println("remove trước next: IllegalStateException");
        }

        System.out.println("next: " + it.next());
        it.remove();
        System.out.println("Sau remove: " + values);

        try {
            it.remove();
        } catch (IllegalStateException e) {
            System.out.println("remove hai lần: IllegalStateException");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 IteratorStateDemo.java
java IteratorStateDemo
```

**Stdout chính xác** với `ArrayList` trên Java 17:

```text
remove trước next: IllegalStateException
next: A
Sau remove: [B]
remove hai lần: IllegalStateException
```

Trước `next()`, iterator chưa có phần tử hiện hành phù hợp để xóa. Sau `next()`, một lần `remove()` hợp lệ. Nếu lại gọi `remove()` mà không có `next()` mới, trạng thái iterator không cho phép tiếp tục xóa.

Đây là **lỗi thứ tự sử dụng API**, không phải giá trị của một tham số, vì `remove()` không nhận tham số nào.

## 22. Không nên dùng exception để thay cho mọi kết quả nghiệp vụ

Giả sử người dùng cố mua số lượng lớn hơn tồn kho. Một API có thể lựa chọn:

- Trả một kiểu kết quả như `InsufficientStock`, `Rejected` hoặc `Result<Success, Failure>` theo ngôn ngữ của dự án.
- Ném một custom domain exception khi hệ thống coi đây là thất bại ngoại lệ cần lan tới handler chung.
- Dùng một unchecked exception khi hành vi là **vi phạm tiền điều kiện đã công bố**, không phải nhánh nghiệp vụ thường gặp.

`IllegalArgumentException` hoặc `IllegalStateException` không phải lúc nào cũng diễn đạt tốt việc sản phẩm hết hàng; ví dụ “số lượng đặt hợp lệ, nhưng tồn kho thay đổi” mang ý nghĩa nghiệp vụ khác với “đối số âm” hoặc “session chưa mở”.

Chọn exception theo **hợp đồng mong muốn**:

| Trường hợp | Phản ứng thường phù hợp |
| --- | --- |
| Caller lập trình sai giá trị theo tiền điều kiện | `IllegalArgumentException` |
| Caller gọi sai pha/lifecycle | `IllegalStateException` |
| Người dùng nhập dữ liệu sai thường xuyên | Trả lỗi xác thực có cấu trúc ở ranh giới |
| Từ chối nghiệp vụ dự kiến | Kết quả domain hoặc exception được thiết kế rõ |
| Hệ thống I/O thất bại | `IOException` hoặc wrapper/domain exception theo hợp đồng |
| Ngắt tác vụ | Giữ ý nghĩa `InterruptedException`/cờ interrupt theo hợp đồng |
| Bug nội bộ gây NPE | Sửa nguyên nhân; không mặc định đổi NPE thành một trong hai loại |

Không có quy tắc “mọi exception unchecked đều là bug” hay “mọi exception checked đều khôi phục được”. Ngữ cảnh và hợp đồng quyết định.

## 23. Thứ tự validation và tính nguyên tử của thay đổi trạng thái

Một method tốt nên kiểm tra **trước** khi thực hiện những side effect khó hoàn tác, nếu có thể:

```java
void update(int newLimit) {
    if (newLimit < 1) {
        throw new IllegalArgumentException(
                "newLimit phải dương");
    }
    if (closed) {
        throw new IllegalStateException(
                "Không thể update sau close");
    }
    limit = newLimit;
}
```

Đây là **phương thức minh họa**, với các field `closed` và `limit`.

Nếu đổi thứ tự để ghi `limit` trước rồi mới kiểm tra `closed`, phương thức có thể ném `IllegalStateException` **sau khi đã làm thay đổi trạng thái**, khiến caller hiểu nhầm rằng thao tác hoàn toàn chưa có tác dụng. Trong các trường hợp đơn giản, kiểm tra trước khi gán giúp duy trì bất biến.

Nhưng trong hệ thống có giao dịch hoặc I/O, “kiểm tra trước” không đủ để bảo đảm tính nguyên tử tuyệt đối. Side effect có thể xảy ra ở bên ngoài, transaction có thể thất bại giữa chừng, và nhiều luồng có thể đua nhau thay đổi trạng thái. Cần công cụ đồng bộ/transaction tương ứng.

## 24. Concurrency: kiểm tra trạng thái và thay đổi phải phối hợp

Một sai lầm là kiểm tra trạng thái của một đối tượng chia sẻ rồi thay đổi không đồng bộ:

```java
if (!closed) {
    // Thread khác có thể close() tại đây.
    doWork();
}
```

Đây là **mã khung minh họa**, chưa phải một thiết kế thread-safe. Giữa kiểm tra và `doWork()` có thể có một luồng khác làm thay đổi trạng thái. Điều này gọi là khoảng đua **check-then-act**.

Tùy hợp đồng, có thể:

- Bảo vệ vùng kiểm tra và chuyển trạng thái bằng `synchronized` hoặc lock.
- Dùng một phép chuyển trạng thái nguyên tử với `AtomicReference` và `compareAndSet`.
- Chỉ cho phép một luồng sở hữu đối tượng; tài liệu hóa rằng class không thread-safe.
- Sử dụng transaction hoặc primitive đồng bộ phù hợp khi trạng thái tồn tại ngoài bộ nhớ Java.

Không nên coi việc ném `IllegalStateException` tự nó giải quyết concurrency. Nó chỉ **báo một bất biến bị vi phạm hoặc một thao tác bị từ chối** ở thời điểm code kiểm tra.

Một biến `volatile boolean closed` có thể cải thiện khả năng nhìn thấy cập nhật giữa các luồng nhưng **không tự biến chuỗi check-then-act thành thao tác nguyên tử**.

## 25. Ví dụ nguyên tử hóa một chuyển trạng thái

Chương trình **`AtomicStateDemo.java`** dùng `AtomicReference` để chỉ cho phép chuyển từ `NEW` sang `STARTED` một lần:

```java
import java.util.concurrent.atomic.AtomicReference;

public class AtomicStateDemo {
    enum State {
        NEW, STARTED
    }

    static class SingleStart {
        private final AtomicReference<State> state =
                new AtomicReference<>(State.NEW);

        void start() {
            if (!state.compareAndSet(State.NEW, State.STARTED)) {
                throw new IllegalStateException(
                        "Đã start trước đó");
            }
        }

        State state() {
            return state.get();
        }
    }

    public static void main(String[] args) {
        SingleStart task = new SingleStart();
        task.start();
        System.out.println("State: " + task.state());

        try {
            task.start();
        } catch (IllegalStateException e) {
            System.out.println("Bắt: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 AtomicStateDemo.java
java AtomicStateDemo
```

**Stdout chính xác**:

```text
State: STARTED
Bắt: Đã start trước đó
```

`compareAndSet` kiểm tra và cập nhật tham chiếu trong một thao tác nguyên tử ở phạm vi biến `state`, tránh hai lời gọi cùng chuyển thành công từ `NEW`. Đây **không phải** bằng chứng rằng mọi side effect của một công việc đã được đồng bộ; nếu `start()` còn mở kết nối hay ghi file, cần kiểm soát chúng theo hợp đồng riêng.

Kiểu exception `IllegalStateException` vẫn phản ánh thao tác lặp lại không được phép ở **trạng thái hiện tại**.

## 26. Dùng JavaDoc `@throws` để công bố hợp đồng

Vì hai exception không buộc xuất hiện trong chữ ký method, **tài liệu** đóng vai trò quan trọng:

```java
/**
 * Cấu hình giới hạn kết nối trước khi server khởi động.
 *
 * @param limit số kết nối, từ 1 đến 500
 * @throws IllegalArgumentException nếu limit ngoài [1, 500]
 * @throws IllegalStateException nếu server đã khởi động
 */
public void configure(int limit) {
    if (limit < 1 || limit > 500) {
        throw new IllegalArgumentException(
                "limit ngoài [1, 500]");
    }
    if (started) {
        throw new IllegalStateException(
                "Server đã khởi động");
    }
    maxConnections = limit;
}
```

Đây là **phương thức minh họa** trong class có field `started` và `maxConnections`. JavaDoc `@throws` mô tả **điều kiện xảy ra lỗi**. Không bắt buộc phải viết trong chữ ký:

```java
public void configure(int limit)
        throws IllegalArgumentException, IllegalStateException
```

Đây là **dòng khai báo minh họa**. Ghi unchecked exceptions trong `throws` là hợp lệ, nhưng không áp nghĩa vụ catch-or-declare cho caller. JavaDoc thường là nơi phù hợp hơn để mô tả các tiền điều kiện và trạng thái.

Nếu code dự án có chính sách công bố `throws` cho một số unchecked exceptions, phải phân biệt rõ đó là **quy ước tài liệu/thiết kế**, không phải yêu cầu kiểm tra checked exceptions của compiler.

## 27. Thứ tự catch và hành vi lan truyền

Khi một phương thức ném `IllegalArgumentException` hoặc `IllegalStateException`, Java chuyển điều khiển tới handler phù hợp theo quy tắc của `try/catch`. Nếu handler kết thúc bình thường, chương trình tiếp tục **sau cấu trúc `try/catch`**, không tự chạy lại lời gọi đã thất bại.

```java
try {
    setPort(70000);
    System.out.println("Không tới đây");
} catch (IllegalArgumentException e) {
    System.out.println("Giá trị cổng sai");
}
System.out.println("Tiếp tục sau catch");
```

Đây là mảnh mã minh họa cần `setPort` được khai báo. Nếu không có handler phù hợp, exception tiếp tục lan lên caller. Các `finally` liên quan vẫn có quy tắc hoàn tất riêng; một `finally` ném lỗi mới có thể che lỗi đang truyền, còn `try-with-resources` có thể giữ lỗi `close()` bằng suppressed exceptions. Những quy tắc đó áp dụng **cho mọi loại exception**, không phải hành vi riêng của hai lớp đang so sánh.

Điều quan trọng của bài: bắt được một `IllegalStateException` **không có nghĩa** state đã được phục hồi. Handler phải bảo đảm hậu điều kiện hợp lệ hoặc tiếp tục báo thất bại cho ranh giới sở hữu chính sách.

## 28. Catch rộng có thể che giấu bug

Ví dụ nguy hiểm:

```java
try {
    processOrder();
} catch (RuntimeException ignored) {
    return "SUCCESS";
}
```

Đây là **mã khung phản ví dụ**, chưa định nghĩa `processOrder` và phương thức chứa. Handler bắt cả những lỗi tiền điều kiện, lỗi trạng thái, `NullPointerException`, lỗi chỉ số và những lỗi runtime khác, rồi trả một kết quả **thành công giả**.

Thay vào đó, một ranh giới HTTP/CLI/job cần:

- Xác định liệu failure thuộc **đầu vào của caller** hay **bug/trạng thái nội bộ**.
- Ghi nhận thông tin phù hợp để chẩn đoán mà không lộ dữ liệu nhạy cảm.
- Trả trạng thái thất bại đúng hợp đồng, không coi catch được exception là đã khôi phục thành công.
- Giữ `cause`/stack trace khi chuyển abstraction.
- Chọn xử lý cụ thể khi thực sự có khả năng phục hồi; các lỗi bất ngờ có thể cần được báo cho hệ thống giám sát.

**Không nên tự động ánh xạ tất cả `IllegalArgumentException` thành “người dùng nhập sai”.** Một thư viện nội bộ có thể ném nó vì service đã tính tham số sai, tức là một bug ở phía server.

## 29. Khi nào tạo custom exception thay vì dùng hai lớp này?

`IllegalArgumentException` và `IllegalStateException` diễn đạt tốt các vi phạm hợp đồng phổ biến. Nếu caller thực sự cần **phân biệt một điều kiện chuyên biệt**, custom exception có thể hữu ích:

```java
class InvalidWorkflowStateException
        extends IllegalStateException {
    InvalidWorkflowStateException(String message) {
        super(message);
    }
}
```

Đây là **class minh họa**, chưa có `main`. Loại mới vẫn unchecked và vẫn thuộc nhánh `IllegalStateException`, nên caller có thể catch cụ thể hoặc catch cha.

Tương tự một ứng dụng có thể xây:

```java
class InvalidCommandArgumentException
        extends IllegalArgumentException {
    InvalidCommandArgumentException(String message) {
        super(message);
    }
}
```

Tuy nhiên, không cần tạo class riêng nếu sự khác biệt chỉ nằm trong một chuỗi message mà caller không cần phân loại theo kiểu. Nhiều class exception vô nghĩa làm API khó học hơn.

Nếu điều kiện mới là **thất bại nghiệp vụ dự kiến** (ví dụ số dư không đủ), cân nhắc kiểu kết quả/domain error hoặc custom exception riêng với hợp đồng rõ, thay vì ép mọi chuyện vào hai loại Java core.

## 30. Kiểm thử nên kiểm tra loại exception và bất biến

Chỉ kiểm tra rằng “một exception đã được ném” là chưa đủ. Nên kiểm tra:

- **Đúng loại** `IllegalArgumentException` cho đối số sai.
- **Đúng loại** `IllegalStateException` cho thứ tự/trạng thái sai.
- **Đúng message/mã lỗi** nếu đó là hợp đồng test, tránh phụ thuộc vào wording không ổn định.
- **Không thay đổi trạng thái ngoài ý muốn** sau lời gọi thất bại.
- **Lời gọi hợp lệ vẫn thành công**.
- **Thử lại sau chuyển trạng thái hợp lệ** hoạt động theo đúng quy tắc.
- **Cause** được giữ nếu code wrap một exception khác.
- Các trường hợp gần biên: 0, -1, giá trị tối đa, state đã đóng/đã hoàn tất, gọi trùng lặp.

JUnit có `assertThrows` để kiểm tra kiểu exception và có thể xem `getMessage()`, `getCause()`. Nếu không sử dụng JUnit, có thể viết phép thử thuần Java để chứng minh.

## 31. Chương trình Java 17 tự kiểm tra hai loại exception

Lưu **`ArgumentStateAssertionsDemo.java`**:

```java
public class ArgumentStateAssertionsDemo {
    static class Counter {
        private boolean closed;
        private int value;

        void add(int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException(
                        "amount phải dương");
            }
            if (closed) {
                throw new IllegalStateException(
                        "Counter đã đóng");
            }
            value += amount;
        }

        void close() {
            closed = true;
        }

        int value() {
            return value;
        }
    }

    public static void main(String[] args) {
        Counter counter = new Counter();

        try {
            counter.add(0);
            throw new AssertionError("Thiếu IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            if (!"amount phải dương".equals(e.getMessage())) {
                throw new AssertionError("Sai message đối số");
            }
            System.out.println("PASS argument");
        }

        counter.add(5);
        counter.close();

        try {
            counter.add(3);
            throw new AssertionError("Thiếu IllegalStateException");
        } catch (IllegalStateException e) {
            if (!"Counter đã đóng".equals(e.getMessage())) {
                throw new AssertionError("Sai message trạng thái");
            }
            System.out.println("PASS state");
        }

        if (counter.value() != 5) {
            throw new AssertionError("Đã sửa trạng thái sai");
        }
        System.out.println("PASS invariant");
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 ArgumentStateAssertionsDemo.java
java ArgumentStateAssertionsDemo
```

**Stdout chính xác**:

```text
PASS argument
PASS state
PASS invariant
```

Ví dụ chủ động dùng `AssertionError` để test thất bại, không phụ thuộc tùy chọn `-ea`. Kết quả chứng minh cả hai đường ném exception đúng loại và **giá trị `counter` vẫn bằng 5** sau khi lời gọi không hợp lệ bị từ chối.

Trong dự án thực, test đa luồng, giao dịch hoặc tài nguyên có thể cần thêm công cụ chuyên biệt; mẫu này chỉ chứng minh hợp đồng đối số/trạng thái của đối tượng đơn giản.

## 32. Những hiểu nhầm phổ biến

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “Hai loại exception giống nhau vì đều là RuntimeException.” | Cùng là unchecked nhưng khác ý nghĩa: đối số sai so với trạng thái không cho phép. |
| “`IllegalArgumentException` là checked vì tên có Exception.” | Sai. Nó kế thừa `RuntimeException` nên unchecked. |
| “`IllegalStateException` chỉ xuất hiện khi đối tượng bị hỏng hoàn toàn.” | Không. Nó có thể báo thao tác sai pha như `send` trước `connect`. |
| “Mọi input sai đều phải ném `IllegalArgumentException`.” | Không. Có loại JDK chuyên biệt và lỗi xác thực dự kiến có thể dùng kết quả cấu trúc. |
| “Mọi trạng thái nghiệp vụ không mong muốn đều phải ném `IllegalStateException`.” | Không. Một kết quả domain hợp lệ có thể phù hợp hơn exception. |
| “Cần ghi `throws IllegalStateException` để compiler cho ném.” | Không. Đây là unchecked exception. |
| “Một handler `catch (IllegalArgumentException)` bắt cả IllegalStateException.” | Không. Chúng là sibling classes. |
| “`NumberFormatException` không liên quan IllegalArgumentException.” | Sai. Nó là subclass của `IllegalArgumentException`. |
| “`NullPointerException` là subclass của IllegalArgumentException.” | Sai. Đây là nhánh riêng dưới `RuntimeException`. |
| “Đã catch được exception nghĩa là thao tác thành công.” | Không. Handler còn phải có logic khôi phục hoặc báo thất bại đúng. |
| “Đối số hợp lệ thì method luôn thành công.” | Không nếu trạng thái, I/O hoặc các điều kiện khác chưa phù hợp. |
| “Thay một loại exception bằng loại kia không ảnh hưởng caller.” | Có thể ảnh hưởng handler, log, test, tài liệu và mã phục hồi. |
| “Message có thể dùng làm mã điều khiển nghiệp vụ.” | Không nên. Message có thể thay đổi hoặc được bản địa hóa. |
| “`volatile` biến kiểm tra và update state thành nguyên tử.” | Không. Cần cơ chế đồng bộ/phép chuyển trạng thái phù hợp. |
| “Exception unchecked không bao giờ cần log/tài liệu.” | Sai. Hợp đồng và chẩn đoán vẫn rất quan trọng. |
| “Sai giá trị trong input HTTP luôn là bug phía server.” | Không. Cần phân biệt lỗi xác thực người dùng với vi phạm invariant trong code nội bộ. |

## 33. Câu hỏi tự kiểm tra

**1. `setRate(-1)` khi rate phải không âm nên cân nhắc loại nào?**  
`IllegalArgumentException`, vì giá trị tham số không thuộc miền cho phép.

**2. `start()` gọi lại khi process đã STARTED và chỉ cho start một lần?**  
`IllegalStateException` thường phù hợp, vì lời gọi sai trạng thái/vòng đời.

**3. `send("hi")` thất bại khi chưa kết nối, trong khi chuỗi hợp lệ?**  
`IllegalStateException` vì nguyên nhân chính là trạng thái kết nối.

**4. `NumberFormatException` là checked hay unchecked? Là subclass của lớp nào đang học?**  
Unchecked, thuộc nhánh `IllegalArgumentException`.

**5. Có phải khai báo `throws IllegalArgumentException` trong chữ ký method không?**  
Không. Có thể tài liệu hóa bằng JavaDoc `@throws`.

**6. `catch (IllegalArgumentException e)` có bắt `IllegalStateException` không?**  
Không, vì hai lớp không kế thừa nhau.

**7. Nếu dùng `catch (RuntimeException e)` thì có bắt cả hai không?**  
Có, nhưng cũng có thể bắt nhiều loại runtime exception khác; cần tránh handler quá rộng nếu không có chính sách đúng.

**8. `Objects.requireNonNull(null)` thường ném gì?**  
`NullPointerException`.

**9. Khi giá trị hợp lệ nhưng object đã CLOSED, nên thiên về loại nào?**  
`IllegalStateException`, nếu hợp đồng mô tả thao tác không cho phép ở trạng thái đó.

**10. Nếu giá trị đối số sai và object cũng đã CLOSED, Java tự ưu tiên lỗi nào?**  
Không. Thứ tự kiểm tra và hợp đồng do người thiết kế API quyết định.

**11. Khi bọc `IOException` thành `IllegalStateException`, làm sao giữ nguyên nhân?**  
Sử dụng constructor `new IllegalStateException("...", e)` và giữ `e` ở `getCause()`.

**12. Có thể ném `IllegalStateException` khi một object đã gọi `close()` không?**  
Có nếu hợp đồng class quy định thao tác tiếp theo bị cấm và loại đó phù hợp; nhiều API cụ thể có thể dùng exception khác.

**13. Vì sao không nên coi mọi `IllegalArgumentException` trong server là lỗi do người dùng?**  
Nó có thể do code nội bộ truyền tham số sai; chỉ ranh giới biết nguồn dữ liệu mới có thể phân loại chính xác.

**14. `IllegalStateException` có ngăn race condition không?**  
Không. Exception chỉ báo lỗi; cần đồng bộ/atomic transition nếu trạng thái dùng chung giữa nhiều luồng.

**15. Nếu method ném IAE rồi được catch ở caller, Java có tự chạy lại lời gọi ấy không?**  
Không. Caller quyết định có thử lại với đối số khác hay không.

**16. Nên đặt JavaDoc thế nào cho API có cả hai loại?**  
Ghi `@throws IllegalArgumentException` cho tiền điều kiện giá trị và `@throws IllegalStateException` cho điều kiện state/vòng đời.

**17. Tên loại nào hợp lý hơn khi `Iterator.remove()` được gọi trước `next()`?**  
`IllegalStateException` theo hợp đồng của `Iterator`.

**18. Nếu trong một tính năng hết hàng là nhánh bình thường, có bắt buộc dùng một trong hai exception không?**  
Không. Có thể trả kết quả nghiệp vụ có trạng thái hoặc custom domain error phù hợp.

**19. Tại sao không bắt tất cả `RuntimeException` rồi trả thành công?**  
Vì có thể che lỗi thực sự, làm mất invariant và khiến caller hiểu nhầm thao tác hoàn tất.

**20. Trong một method có cả input và state validation, tại sao nên kiểm tra trước side effect?**  
Để tránh thay đổi một phần đối tượng rồi ném lỗi, khiến trạng thái và tín hiệu trả về không nhất quán.

## 34. Checklist thiết kế và review

Khi quyết định giữa `IllegalArgumentException` và `IllegalStateException`:

1. **Xác định tiền điều kiện của đối số:** miền hợp lệ, null, phạm vi, định dạng, quan hệ giữa các tham số.
2. **Xác định invariant/trạng thái của object:** pha hoạt động, resource đã mở/đóng, kết nối, workflow.
3. **Phân biệt lỗi giá trị với lỗi vòng đời:** cùng giá trị có thành công khi state đổi không?
4. **Xem API chuẩn có một loại ngoại lệ chuyên biệt hơn không:** `NumberFormatException`, `IndexOutOfBoundsException`, `NullPointerException`, v.v.
5. **Xem đây có phải nhánh nghiệp vụ dự kiến không:** có cần kiểu kết quả/domain status thay vì exception?
6. **Định nghĩa thứ tự validation:** khi nhiều điều kiện cùng sai, lỗi nào được báo trước?
7. **Kiểm tra trước các side effect nếu có thể:** không sửa một phần state rồi mới ném lỗi dễ tránh.
8. **Dùng message hữu ích, an toàn:** tên tham số/trạng thái và giá trị không nhạy cảm.
9. **Không dùng message để định tuyến logic:** dùng loại exception hoặc mã lỗi có cấu trúc.
10. **Công bố hợp đồng bằng JavaDoc `@throws`:** dù không bắt buộc viết trong chữ ký.
11. **Không ép caller catch unchecked exception ở mọi tầng:** chỉ xử lý nơi có trách nhiệm thực sự.
12. **Tại ranh giới nhập liệu, phân loại lỗi từ người dùng với bug nội bộ:** không ánh xạ mù quáng.
13. **Nếu wrap lỗi khác, bảo toàn cause:** dùng constructor `(String, Throwable)`.
14. **Nếu dùng nhiều thread, bảo vệ check-then-act:** lock/atomic/ownership theo thiết kế.
15. **Kiểm thử cả đối số sai và state sai:** cùng các đường hợp lệ và trường hợp gần biên.
16. **Kiểm tra invariant sau failure:** exception không được che việc cập nhật trạng thái sai.
17. **Xem ảnh hưởng tương thích API:** đổi loại exception có thể làm hỏng handler của caller.
18. **Không nuốt RuntimeException rồi báo thành công giả.**
19. **Không dùng `Error` để diễn đạt lỗi đối số/trạng thái ứng dụng.**
20. **Cân nhắc custom exception chỉ khi cần phân biệt tình huống thực sự.**

## 35. Tổng kết

`IllegalArgumentException` và `IllegalStateException` đều là **unchecked RuntimeException**, không buộc caller catch-or-declare. Khác biệt quyết định là **nguồn gốc của lời gọi không hợp lệ**:

- **`IllegalArgumentException`** dành cho **tham số caller truyền không thỏa tiền điều kiện**, chẳng hạn giá trị âm, ngoài khoảng hoặc định dạng bị từ chối.
- **`IllegalStateException`** dành cho **thao tác không được phép vì trạng thái hiện tại**, chẳng hạn dùng object trước khởi tạo, sau khi đóng hoặc lặp một transition không hợp lệ.

Một API có thể cần cả hai: kiểm tra đối số, rồi kiểm tra state, rồi mới thay đổi dữ liệu. Java không tự quyết định thứ tự ưu tiên nếu cả hai vi phạm. Khi có loại JDK cụ thể hơn như `NullPointerException`, `IndexOutOfBoundsException` hoặc `NumberFormatException`, hãy theo đúng hợp đồng API đang sử dụng.

Quan trọng hơn cả tên exception là **hợp đồng rõ ràng, xử lý ở đúng ranh giới, message an toàn, bảo toàn cause, kiểm thử invariant và phân biệt thất bại bất thường với kết quả nghiệp vụ dự kiến**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-runtime-exceptions-003">IndexOutOfBoundsException và ArrayIndexOutOfBoundsException</a>

<details>
<summary>Xem chi tiết</summary>

# `IndexOutOfBoundsException` và `ArrayIndexOutOfBoundsException` trong Java

> **Chủ đề:** Java Core → Exception → Ngoại lệ runtime. `IndexOutOfBoundsException` báo rằng một **chỉ số hoặc phạm vi** nằm ngoài giới hạn được một API cho phép. `ArrayIndexOutOfBoundsException` là **lớp con chuyên biệt** thường gặp khi truy cập trực tiếp một mảng Java bằng chỉ số sai. Cả hai thuộc nhánh `RuntimeException` (unchecked), vì vậy compiler không bắt buộc phải khai báo `throws` hoặc `catch` chúng. Để viết code đúng, cần hiểu giới hạn của mảng, collection, chuỗi, đoạn con và cách kiểm tra điều kiện **trước khi truy cập**, thay vì coi `try/catch` là phương pháp sửa lỗi chỉ số thông thường.

## 1. Hai lớp exception khác nhau thế nào?

Cây kế thừa rút gọn:

```text
java.lang.Throwable
└── java.lang.Exception
    └── java.lang.RuntimeException
        └── java.lang.IndexOutOfBoundsException
            ├── java.lang.ArrayIndexOutOfBoundsException
            └── java.lang.StringIndexOutOfBoundsException
```

- `IndexOutOfBoundsException`: lớp tổng quát báo lỗi **index/range**, chẳng hạn `List.get(index)`, `List.subList(from,to)` hoặc các helper kiểm tra phạm vi.
- `ArrayIndexOutOfBoundsException`: subtype của `IndexOutOfBoundsException`; phát sinh khi một phép truy cập phần tử **mảng** có chỉ số âm hoặc không nhỏ hơn `array.length`.
- `StringIndexOutOfBoundsException`: subtype khác, thường gặp khi truy cập ký tự chuỗi ở vị trí không hợp lệ, chẳng hạn `String.charAt(index)`.
- Vì `ArrayIndexOutOfBoundsException extends IndexOutOfBoundsException`, một `catch (IndexOutOfBoundsException e)` có thể nhận cả ngoại lệ từ mảng.
- Chiều ngược lại **không đúng**: `catch (ArrayIndexOutOfBoundsException)` không bắt mọi `IndexOutOfBoundsException`, ví dụ ngoại lệ chỉ số do một `List` hoặc `String` phát ra.

### 1.1. Bảng so sánh nhanh

| Tiêu chí | `IndexOutOfBoundsException` | `ArrayIndexOutOfBoundsException` |
| --- | --- | --- |
| Vai trò | Tổng quát cho chỉ số/phạm vi ngoài giới hạn | Chuyên biệt cho truy cập mảng |
| Package | `java.lang` | `java.lang` |
| Lớp cha trực tiếp | `RuntimeException` | `IndexOutOfBoundsException` |
| Checked hay unchecked? | Unchecked | Unchecked |
| Ví dụ nguồn thường gặp | `List.get`, `List.subList`, `Objects.checkIndex` | `arr[i]`, phép truy cập từng chiều của mảng |
| Bắt bằng `catch(IndexOutOfBoundsException)`? | Có | Có |
| Bắt bằng `catch(ArrayIndexOutOfBoundsException)`? | Không phải mọi trường hợp | Có |
| Có phải thông điệp luôn giống nhau? | Không | Không |
| Cách phòng tránh | Kiểm tra contract index/range cụ thể | Bảo đảm `0 <= i && i < arr.length` |

**Quy tắc chọn catch:** Khi muốn xử lý thống nhất lỗi chỉ số bất kể cấu trúc dữ liệu, bắt lớp cha `IndexOutOfBoundsException`. Khi chỉ muốn xử lý lỗi truy cập mảng cụ thể, có thể bắt lớp con. Nhưng trong phần lớn code nghiệp vụ, **sửa logic kiểm tra chỉ số** tốt hơn bắt những ngoại lệ này và tiếp tục như không có gì xảy ra.

## 2. Cả hai đều là unchecked exception

Các subtype của `RuntimeException` là **unchecked**: Java không áp dụng quy tắc bắt hoặc khai báo checked `throws` như với `IOException`.

Ví dụ:

```java
static int elementAt(int[] array, int index) {
    return array[index];
}
```

Phương thức này hợp lệ mà không có `throws ArrayIndexOutOfBoundsException`. Nếu `index` nằm ngoài miền cho phép, lúc chạy có thể phát sinh `ArrayIndexOutOfBoundsException`.

Một lời gọi cũng có thể chủ động khai báo `throws` với unchecked exception để tài liệu hóa:

```java
static int elementAt(int[] array, int index)
        throws ArrayIndexOutOfBoundsException {
    return array[index];
}
```

Nhưng khai báo trên **không bắt buộc** và không thay đổi cách kiểm tra khi biên dịch.

### 2.1. Tại sao không bắt buộc caller xử lý?

Sai chỉ số thường là dấu hiệu chương trình vi phạm một **tiền điều kiện** của thao tác truy cập, ví dụ:

- vòng lặp dừng sai;
- quên kiểm tra dữ liệu đầu vào;
- nhầm chỉ số và độ dài;
- sử dụng length của cấu trúc này để truy cập cấu trúc khác;
- nhầm đầu mút bao gồm/không bao gồm;
- dữ liệu đã thay đổi kích thước sau khi kiểm tra.

Java không yêu cầu mọi call site phải bọc `try/catch` cho những lỗi runtime này. Thiết kế tốt thường xác định và bảo đảm invariant, rồi kiểm thử các ranh giới. Nếu user nhập chỉ số tùy ý, validation ở biên hệ thống vẫn cần thiết để trả lỗi nhập liệu thân thiện.

### 2.2. Compiler có tự chặn mọi index sai không?

Không. Ví dụ:

```java
int[] values = {10, 20};
System.out.println(values[2]); // Có thể biên dịch, lỗi lúc chạy.
```

Dù chỉ số là hằng `2`, Java nói chung kiểm tra phạm vi truy cập mảng **khi chương trình thực thi**. Công cụ phân tích tĩnh hoặc IDE có thể cảnh báo trước, nhưng không nên xem cảnh báo ấy là bảo đảm của compiler.

## 3. Miền chỉ số hợp lệ của mảng

Với mảng có độ dài `n = array.length`:

```text
Chỉ số hợp lệ:    0, 1, 2, ..., n-1
Biểu thức đúng:   0 <= index && index < n
Chỉ số sai:       index < 0 hoặc index >= n
```

Ví dụ `int[] a = {5, 9, 12};`:

| Biểu thức | Kết quả |
| --- | --- |
| `a[0]` | `5` |
| `a[1]` | `9` |
| `a[2]` | `12` |
| `a[-1]` | `ArrayIndexOutOfBoundsException` |
| `a[3]` | `ArrayIndexOutOfBoundsException` |
| `a[100]` | `ArrayIndexOutOfBoundsException` |
| `a[a.length - 1]` | Phần tử cuối, với điều kiện mảng không rỗng |
| `a[a.length]` | Sai: `length` là số phần tử, không phải vị trí cuối |

**Độ dài không phải chỉ số cuối.** Khi `n > 0`, chỉ số cuối là `n - 1`. Với mảng rỗng `n == 0`, **không có bất kỳ chỉ số truy cập phần tử hợp lệ nào**, kể cả `0`.

### 3.1. Java 17 — ví dụ lỗi mảng với các chỉ số khác nhau

Tạo tệp **`ArrayBoundsDemo.java`**:

```java
public class ArrayBoundsDemo {
    static void inspect(int[] values, int index) {
        try {
            System.out.println(
                    "index " + index + " = " + values[index]);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("index " + index + " ngoài giới hạn");
        }
    }

    public static void main(String[] args) {
        int[] numbers = {10, 20, 30};
        System.out.println("length = " + numbers.length);

        inspect(numbers, 0);
        inspect(numbers, 2);
        inspect(numbers, -1);
        inspect(numbers, 3);

        int[] empty = {};
        inspect(empty, 0);
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 ArrayBoundsDemo.java
java ArrayBoundsDemo
```

Stdout chính xác:

```text
length = 3
index 0 = 10
index 2 = 30
index -1 ngoài giới hạn
index 3 ngoài giới hạn
index 0 ngoài giới hạn
```

Trong chương trình này, `catch` chỉ để minh họa đường exception mà không làm dừng các ca kiểm thử tiếp theo. Với ứng dụng thật, nếu index đã được kiểm tra hợp lệ trước khi truy cập, không cần `catch` bao quanh mọi phép `a[i]`. Đặc biệt tránh bỏ qua lỗi mảng rồi tiếp tục dùng dữ liệu được giả định đã đọc thành công.

### 3.2. Điều gì xảy ra khi gán vào chỉ số sai?

Không chỉ **đọc** mà cả **ghi** phần tử mảng đều cần chỉ số hợp lệ:

```java
int[] values = {3, 4};
values[2] = 99; // Ném ArrayIndexOutOfBoundsException.
```

Không có phần tử `values[2]` được tạo mới. Mảng Java có **độ dài cố định sau khi tạo**, phép gán ở index ngoài giới hạn không tự mở rộng mảng.

Nếu cần danh sách có thể tăng số lượng phần tử, dùng cấu trúc như `ArrayList` với thao tác thêm phù hợp; không cố “append” bằng `arr[arr.length] = value`.

## 4. Index và độ dài: ba điều kiện cần nhớ

Với chỉ số phần tử `i` trong cấu trúc độ dài `n`:

```java
boolean validElementIndex = i >= 0 && i < n;
```

Với **ranh giới vị trí chèn** vào danh sách có độ dài `n`, `i == n` có thể hợp lệ nếu API cho chèn ở cuối, chẳng hạn `List.add(index, element)`:

```java
boolean validInsertionPosition = i >= 0 && i <= n;
```

Với **cặp đầu mút của đoạn [from, to)**:

```java
boolean validRange = 0 <= from && from <= to && to <= n;
```

Ba contract này **không giống nhau**. Một nguyên nhân lỗi thường gặp là lấy điều kiện cho **vị trí chèn** (cho phép `i == n`) rồi dùng để **đọc phần tử** (không cho phép `i == n`).

### 4.1. Vì sao dấu nhỏ hơn và nhỏ hơn hoặc bằng quan trọng?

- `array[index]` hoặc `list.get(index)`: `index < length/size`.
- `list.add(index, value)`: thường cho `index <= size` để append.
- `list.subList(from, to)`: có thể cho `to == size` do `to` là **exclusive end**.
- `String.substring(begin, end)`: `end == length` hợp lệ, còn `charAt(length)` sai.

Đừng học thuộc một điều kiện duy nhất rồi áp dụng cho mọi phương thức có tham số `index`. Hãy đọc hợp đồng từng API.

## 5. Lỗi vòng lặp `off-by-one` với dấu `<=`

Trường hợp phổ biến:

```java
int[] numbers = {4, 7, 9};

for (int i = 0; i <= numbers.length; i++) {
    System.out.println(numbers[i]); // Lỗi khi i == 3.
}
```

Điều kiện `i <= numbers.length` cho phép i bằng độ dài, nhưng chỉ số cuối là `length - 1`. Sửa thành:

```java
for (int i = 0; i < numbers.length; i++) {
    System.out.println(numbers[i]);
}
```

### 5.1. Java 17 — chạy thử phiên bản sai và phiên bản đúng

Tạo **`OffByOneDemo.java`**:

```java
public class OffByOneDemo {
    public static void main(String[] args) {
        int[] scores = {8, 13};

        try {
            for (int i = 0; i <= scores.length; i++) {
                System.out.println("Sai: " + scores[i]);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Bắt lỗi tại i == length");
        }

        for (int i = 0; i < scores.length; i++) {
            System.out.println("Đúng: " + scores[i]);
        }

        for (int score : scores) {
            System.out.println("For-each: " + score);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 OffByOneDemo.java
java OffByOneDemo
```

Stdout:

```text
Sai: 8
Sai: 13
Bắt lỗi tại i == length
Đúng: 8
Đúng: 13
For-each: 8
For-each: 13
```

Giải thích:

1. Lần duyệt thứ nhất sử dụng điều kiện sai, truy cập `scores[2]` khi mảng có độ dài `2`.
2. Handler chỉ giữ chương trình ví dụ tiếp tục để so sánh, **không sửa** lỗi vòng lặp.
3. Lần duyệt thứ hai dùng `i < scores.length` nên chỉ có i = 0, 1.
4. `for-each` không cần tự tính index cho việc truy cập trực tiếp từng phần tử. Nó là lựa chọn tốt nếu mục tiêu chỉ là duyệt giá trị, không cần vị trí.
5. `for-each` không phòng mọi lỗi: nếu bên trong vẫn có phép `otherArray[i]` dùng chỉ số không hợp lệ, exception vẫn có thể xảy ra.

### 5.2. Các biến thể off-by-one

- Vòng lặp bắt đầu `i = 1` rồi đọc `array[i]`, vô tình bỏ phần tử đầu.
- Vòng lặp lùi dùng `i >= 0` nhưng khởi tạo `i = array.length`, truy cập vượt giới hạn ngay lần đầu.
- Sau khi gọi `list.remove(index)`, `size()` giảm, nhưng code vẫn dùng index dựa trên kích thước cũ.
- Dùng `i <= upperBound` khi `upperBound` là **exclusive**.
- Nhầm một dải `[start, end)` với dải `[start, end]`.

Cách sửa tốt nhất là đặt tên có ngữ nghĩa rõ: `exclusiveEnd`/`inclusiveLastIndex`, và viết test ở đầu, cuối, ngay trước/sau ranh giới.

## 6. `List` và `IndexOutOfBoundsException`

Với `List<T>` kích thước `n = list.size()`, `list.get(i)` yêu cầu `0 <= i < n`. Theo hợp đồng List, chỉ số không hợp lệ dẫn đến `IndexOutOfBoundsException`.

Ví dụ:

```java
java.util.List<String> names =
        java.util.List.of("An", "Bình");

System.out.println(names.get(0)); // "An"
System.out.println(names.get(1)); // "Bình"
// names.get(2); // Ngoài giới hạn.
```

Đây là mảnh code trong method. `list.size()` là **số phần tử**, tương tự `array.length` là số phần tử mảng. Nhưng cú pháp của List là **`size()`**, còn mảng dùng **trường `length`**:

```java
int[] array = {1, 2};
int arraySize = array.length;

java.util.List<Integer> list = java.util.List.of(1, 2);
int listSize = list.size();
```

**Lưu ý quan trọng về loại exception runtime:** Hợp đồng `List.get` nói `IndexOutOfBoundsException`; các implementation khác nhau có thể dùng subclass cụ thể tùy cách triển khai. Không dùng tên subtype xuất hiện trong log làm cách xác định chắc chắn cấu trúc dữ liệu đã được truy cập. Khi xử lý code viết theo interface `List`, thường nên dựa trên hợp đồng lớp cha thay vì phụ thuộc thông điệp hoặc subtype nội bộ.

### 6.1. `get` khác `add(index, element)`

Với List có `size() == 2`:

```java
java.util.List<String> names =
        new java.util.ArrayList<>(
                java.util.List.of("A", "B"));

names.add(2, "C");  // Hợp lệ: chèn cuối.
System.out.println(names.get(2)); // Hợp lệ sau khi thêm.
```

Đoạn trong method sẽ in `C`. Trước khi `add(2,"C")`, `names.get(2)` sai; sau khi thêm, size thành 3 nên index 2 hợp lệ.

Với `List.of(...)`, thao tác thay đổi như `add` sẽ ném `UnsupportedOperationException`, **không phải** `IndexOutOfBoundsException` chỉ vì list là immutable. Vì vậy khi đang đọc stack trace cần phân biệt lỗi **phạm vi chỉ số** với lỗi **khả năng thay đổi collection**.

## 7. `StringIndexOutOfBoundsException`: họ hàng cùng lớp cha

`String.charAt(index)` yêu cầu `0 <= index < string.length()`. Một chỉ số không hợp lệ thường làm phát sinh `StringIndexOutOfBoundsException`, cũng là subtype của `IndexOutOfBoundsException`.

```java
String text = "Java";
// text.charAt(4); // Sai vì length == 4.
```

Một handler lớp cha nhận được nhiều tình huống:

```java
try {
    String text = "Java";
    System.out.println(text.charAt(text.length()));
} catch (IndexOutOfBoundsException e) {
    System.out.println("Vị trí ký tự không hợp lệ");
}
```

Đoạn minh họa này in `Vị trí ký tự không hợp lệ`.

### 7.1. Đừng nhầm `String.length()` với số code point Unicode

Chuỗi Java lưu giá trị theo các **đơn vị UTF-16**. Một ký tự Unicode ngoài Basic Multilingual Plane (như nhiều emoji) dùng **hai UTF-16 code units** (surrogate pair). Do đó:

- `String.length()` trả số **UTF-16 code units**, không hứa là số ký tự người dùng nhìn thấy.
- `charAt(i)` trả một `char` — một code unit — không nhất thiết là toàn bộ Unicode code point.
- `codePointCount(begin,end)` đếm Unicode code points trong khoảng đã chọn.
- `offsetByCodePoints(index, codePointOffset)` hỗ trợ chuyển vị trí theo code point.
- Index mà `charAt` nhận luôn là chỉ số UTF-16, không phải thứ tự “ký tự hiển thị”.

### 7.2. Java 17 — Unicode không làm mất quy tắc bounds

Tạo **`StringBoundsUnicodeDemo.java`**:

```java
public class StringBoundsUnicodeDemo {
    public static void main(String[] args) {
        String text = "A😀B";

        System.out.println("UTF-16 length: " + text.length());
        System.out.println("Code points: "
                + text.codePointCount(0, text.length()));
        System.out.println("Low surrogate at 2: "
                + Character.isLowSurrogate(text.charAt(2)));

        int offsetOfB = text.offsetByCodePoints(0, 2);
        System.out.println("Offset of B: " + offsetOfB);

        try {
            text.charAt(text.length());
        } catch (StringIndexOutOfBoundsException e) {
            System.out.println("Exception: "
                    + e.getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 StringBoundsUnicodeDemo.java
java StringBoundsUnicodeDemo
```

Stdout:

```text
UTF-16 length: 4
Code points: 3
Low surrogate at 2: true
Offset of B: 3
Exception: StringIndexOutOfBoundsException
```

Vì `😀` dùng hai code units, text có `length() == 4` dù chứa ba code points (`A`, emoji, `B`). `charAt(2)` hợp lệ **về bounds** nhưng trả nửa sau của surrogate pair; muốn lấy nguyên code point, nên dùng `codePointAt` hoặc các API Unicode phù hợp.

Vẫn còn giới hạn: *code point* không đồng nghĩa một ký tự hiển thị (grapheme cluster), vì nhiều chuỗi có combining marks. Khi thao tác giao diện người dùng, cần hiểu mức trừu tượng bạn muốn đếm.

## 8. Dải con: `[from, to)` và `IndexOutOfBoundsException`

Nhiều API Java dùng **nửa khoảng mở**:

```text
[fromIndex, toIndex)
bao gồm fromIndex, không bao gồm toIndex
```

Đối với một cấu trúc có độ dài `n`, điều kiện hợp lệ thường là:

```text
0 <= fromIndex <= toIndex <= n
```

Hai ví dụ:

- `list.subList(1, 3)` lấy các phần tử ở index 1, 2.
- `text.substring(1, 3)` lấy các UTF-16 code units tương ứng index 1, 2.

Mỗi API có thêm điều kiện và các loại exception cụ thể. Nếu `fromIndex > toIndex`, một số API (như `List.subList`) có thể ném `IllegalArgumentException` chứ không nhất thiết chỉ `IndexOutOfBoundsException`; đọc hợp đồng phương thức chứ đừng suy từ tên “slice”.

### 8.1. Ví dụ Java 17 với phạm vi có đầu mút exclusive

Tạo **`RangeBoundsDemo.java`**:

```java
import java.util.List;

public class RangeBoundsDemo {
    public static void main(String[] args) {
        List<String> names = List.of("A", "B", "C");

        System.out.println("subList(1, 3): "
                + names.subList(1, 3));
        System.out.println("subList(3, 3): "
                + names.subList(3, 3));

        try {
            names.subList(2, 4);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("toIndex 4 vượt size 3");
        }

        String text = "Java";
        System.out.println("substring(1, 4): "
                + text.substring(1, 4));
        System.out.println("substring(4, 4): '"
                + text.substring(4, 4) + "'");
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 RangeBoundsDemo.java
java RangeBoundsDemo
```

Stdout:

```text
subList(1, 3): [B, C]
subList(3, 3): []
toIndex 4 vượt size 3
substring(1, 4): ava
substring(4, 4): ''
```

**Những điểm rút ra:**

1. `toIndex == size` hợp lệ ở `subList` vì đầu mút trên được **loại trừ**.
2. `fromIndex == toIndex == size` biểu diễn một đoạn rỗng hợp lệ.
3. `toIndex > size` bị từ chối; ở ví dụ này handler `IndexOutOfBoundsException` nhận.
4. Với `String.substring`, `endIndex == length` hợp lệ; điều này **khác với `charAt(length)`**.
5. Trong code thật, `subList` thường là **view** của danh sách gốc, nên thay đổi cấu trúc list gốc khi vẫn dùng view có thể dẫn đến vấn đề độc lập với bounds. `List.of` trong ví dụ không cho sửa nên đơn giản hơn.

## 9. Công cụ kiểm tra chỉ số chuẩn: `Objects.checkIndex`

Từ Java 9, `java.util.Objects` cung cấp các phương thức kiểm tra index và range:

```java
Objects.checkIndex(index, length);
Objects.checkFromToIndex(fromIndex, toIndex, length);
Objects.checkFromIndexSize(fromIndex, size, length);
```

Các helper này có thể ném `IndexOutOfBoundsException` nếu tham số không hợp lệ, và trả lại index hoặc fromIndex khi hợp lệ. Chúng thường hữu ích khi **xây dựng API thao tác trên vùng dữ liệu**, cần validation nhất quán trước khi truy cập.

| Helper | Điều kiện hợp lệ (về mặt ý nghĩa) | Trường hợp hay dùng |
| --- | --- | --- |
| `checkIndex(i, n)` | `0 <= i < n` | Truy cập một phần tử |
| `checkFromToIndex(a,b,n)` | `0 <= a <= b <= n` | Đoạn `[a,b)` |
| `checkFromIndexSize(a,size,n)` | `0 <= a`, `0 <= size` và `a + size <= n` theo phép kiểm tra an toàn tràn số | Đoạn bắt đầu ở `a` có `size` phần tử |

Điều kiện toán học `a + size <= n` phải được thực thi theo cách không sai vì tràn số nguyên; dùng helper chuẩn hoặc so sánh an toàn, đừng cộng hai `int` tùy tiện rồi tin kết quả đã kiểm tra chính xác.

**Phân biệt phiên bản:** `Objects.checkIndex` và hai hàm kiểm tra phạm vi nói trên **không có từ Java 8**. Dự án dùng Java 17 có thể sử dụng trực tiếp.

### 9.1. Java 17 — ví dụ `Objects` với đầu ra cố định

Tạo **`ObjectsIndexCheckDemo.java`**:

```java
import java.util.Objects;

public class ObjectsIndexCheckDemo {
    static void test(String label, Runnable action) {
        try {
            action.run();
            System.out.println(label + ": OK");
        } catch (IndexOutOfBoundsException e) {
            System.out.println(label + ": OUT");
        }
    }

    public static void main(String[] args) {
        test("index 2", () -> Objects.checkIndex(2, 3));
        test("index 3", () -> Objects.checkIndex(3, 3));
        test("index -1", () -> Objects.checkIndex(-1, 3));

        test("range 0..3",
                () -> Objects.checkFromToIndex(0, 3, 3));
        test("empty range",
                () -> Objects.checkFromToIndex(3, 3, 3));
        test("range 0..4",
                () -> Objects.checkFromToIndex(0, 4, 3));

        test("size fits",
                () -> Objects.checkFromIndexSize(1, 2, 3));
        test("size exceeds",
                () -> Objects.checkFromIndexSize(2, 2, 3));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 ObjectsIndexCheckDemo.java
java ObjectsIndexCheckDemo
```

Stdout:

```text
index 2: OK
index 3: OUT
index -1: OUT
range 0..3: OK
empty range: OK
range 0..4: OUT
size fits: OK
size exceeds: OUT
```

Đoạn dùng `Runnable` để thử các phép kiểm tra và chỉ in kết quả, không dựa vào text của exception. Vì ba helper `Objects` ném unchecked exception nên callback `Runnable.run()` (không khai báo checked) phù hợp.

### 9.2. Kiểm tra chỉ số ở đâu?

- Tầng nhận input: validate chỉ số người dùng nhập, trả thông báo dễ hiểu.
- Method thư viện: kiểm tra tiền điều kiện nếu index/range là phần hợp đồng công khai; có thể dùng `Objects.checkIndex` hoặc kiểm tra riêng để đưa thông điệp phù hợp.
- Vòng lặp nội bộ: bảo đảm bất biến bằng điều kiện lặp/thiết kế thuật toán thay vì bao quanh từng lần truy cập bằng `try/catch`.
- Đầu vào đã được validation trước: vẫn phải xét trường hợp cấu trúc dữ liệu bị thay đổi kích thước giữa **check** và **use** nếu shared mutable state/concurrency có liên quan.

## 10. Mảng hai chiều: mỗi chiều có độ dài riêng

Trong Java, “mảng 2D” là **mảng chứa các mảng con**. Các hàng có thể dài khác nhau (*jagged array*) hoặc thậm chí có giá trị `null`:

```java
int[][] matrix = {
    {1, 2, 3},
    {4}
};
```

Với cấu trúc trên:

- `matrix.length == 2`: có hai hàng.
- `matrix[0].length == 3`: hàng đầu có ba phần tử.
- `matrix[1].length == 1`: hàng thứ hai có một phần tử.
- `matrix[1][1]`: sai chỉ số **cột của hàng thứ hai**, gây `ArrayIndexOutOfBoundsException`.
- `matrix[2][0]`: sai chỉ số **hàng**, cũng gây `ArrayIndexOutOfBoundsException`.
- Nếu `matrix[1] == null`, `matrix[1][0]` gây `NullPointerException`, **không phải** lỗi vượt giới hạn tại hàng.

### 10.1. Java 17 — truy cập mảng răng cưa

Tạo **`JaggedArrayBoundsDemo.java`**:

```java
public class JaggedArrayBoundsDemo {
    static void inspect(int[][] matrix, int row, int column) {
        try {
            System.out.println("(" + row + "," + column + ") = "
                    + matrix[row][column]);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("(" + row + "," + column
                    + ") ngoài giới hạn");
        }
    }

    public static void main(String[] args) {
        int[][] matrix = {
                {1, 2, 3},
                {4}
        };

        System.out.println("Số hàng: " + matrix.length);
        System.out.println("Độ dài hàng 1: "
                + matrix[1].length);

        inspect(matrix, 0, 2);
        inspect(matrix, 1, 0);
        inspect(matrix, 1, 1);
        inspect(matrix, 2, 0);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 JaggedArrayBoundsDemo.java
java JaggedArrayBoundsDemo
```

Stdout:

```text
Số hàng: 2
Độ dài hàng 1: 1
(0,2) = 3
(1,0) = 4
(1,1) ngoài giới hạn
(2,0) ngoài giới hạn
```

**Bài học:** không thể dùng `matrix[0].length` cho mọi hàng nếu dữ liệu là mảng răng cưa. Duyệt an toàn:

```java
for (int row = 0; row < matrix.length; row++) {
    if (matrix[row] == null) {
        continue; // Chính sách tùy hợp đồng dữ liệu.
    }
    for (int col = 0; col < matrix[row].length; col++) {
        System.out.println(matrix[row][col]);
    }
}
```

Đây là đoạn trong method, không phải file độc lập. Kiểm tra `null` là điều kiện riêng với kiểm tra index, tránh đồng nhất `NullPointerException` với `ArrayIndexOutOfBoundsException`.

## 11. Dữ liệu bên ngoài: validation trước khi truy cập

Một chỉ số lấy từ form, CLI hoặc request HTTP có thể là:

- chuỗi không phải số (`"abc"`);
- số âm (`"-1"`);
- số nguyên vượt kích thước mảng;
- số nguyên quá lớn, không parse được thành `int`;
- chỉ số từ client bắt đầu ở 1 trong khi Java dùng index bắt đầu ở 0.

Cần phân biệt **lỗi phân tích cú pháp** với **lỗi ngoài phạm vi**. `Integer.parseInt("abc")` ném `NumberFormatException`; nó không trực tiếp ném `IndexOutOfBoundsException` của mảng.

### 11.1. Java 17 — kiểm tra đầu vào trước khi đọc

Tạo **`ValidatedIndexInputDemo.java`**:

```java
import java.util.List;

public class ValidatedIndexInputDemo {
    static void lookup(int[] values, String raw) {
        try {
            int index = Integer.parseInt(raw);

            if (index < 0 || index >= values.length) {
                System.out.println(raw + ": ngoài phạm vi");
                return;
            }

            System.out.println(raw + ": " + values[index]);
        } catch (NumberFormatException e) {
            System.out.println(raw + ": không phải số nguyên");
        }
    }

    public static void main(String[] args) {
        int[] values = {100, 200};

        for (String raw : List.of("0", "1", "2", "-1", "abc")) {
            lookup(values, raw);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 ValidatedIndexInputDemo.java
java ValidatedIndexInputDemo
```

Stdout:

```text
0: 100
1: 200
2: ngoài phạm vi
-1: ngoài phạm vi
abc: không phải số nguyên
```

Ở đây exception của mảng **không cần xảy ra** cho dữ liệu ngoài phạm vi, vì code kiểm tra trước. Tầng ứng dụng có thể chuyển các trường hợp input sai thành validation result hoặc HTTP 400/422 tùy hợp đồng, không cần chờ JVM ném `ArrayIndexOutOfBoundsException` rồi xem đó là cách báo lỗi người dùng.

### 11.2. Nếu người dùng đếm vị trí từ 1?

Ví dụ giao diện ghi “chọn mục 1, 2, 3” nhưng Java lưu ở index 0, 1, 2. Cần định nghĩa chuyển đổi có kiểm soát:

```java
int oneBasedPosition = 2;
int zeroBasedIndex = oneBasedPosition - 1;
```

Tránh trừ 1 nếu input đã là zero-based. Ngoài ra không nên dùng `input - 1` trước khi xét nguy cơ tràn số với `Integer.MIN_VALUE`; bảo đảm input thuộc miền 1..n trước, sau đó mới chuyển thành 0..n-1.

## 12. Bắt ngoại lệ: chọn lớp cha hay lớp con?

Vì quan hệ kế thừa, hai cách đều có giá trị trong ngữ cảnh riêng.

### 12.1. Handler tổng quát cho nhiều cấu trúc dữ liệu

```java
try {
    useIndex();
} catch (IndexOutOfBoundsException e) {
    reportInvalidPosition(e);
}
```

Đây là mẫu minh họa; `useIndex()` và `reportInvalidPosition(...)` chưa được định nghĩa. Một handler lớp cha có thể bắt cả `ArrayIndexOutOfBoundsException` và `StringIndexOutOfBoundsException`.

### 12.2. Handler chuyên biệt cho mảng

```java
try {
    int value = numbers[index];
    System.out.println(value);
} catch (ArrayIndexOutOfBoundsException e) {
    System.out.println("Truy cập mảng sai vị trí");
}
```

Đoạn trong method, giả định `numbers` và `index` có kiểu phù hợp. Handler chỉ bắt subtype chuyên biệt.

### 12.3. Thứ tự hai handler khi dùng cả hai

```java
try {
    readSomething();
} catch (ArrayIndexOutOfBoundsException e) {
    System.out.println("Lỗi chỉ số mảng");
} catch (IndexOutOfBoundsException e) {
    System.out.println("Lỗi chỉ số khác");
}
```

Đây là mẫu minh họa, `readSomething()` chưa định nghĩa. Handler **lớp con** phải đứng trước **lớp cha**, vì `catch(IndexOutOfBoundsException)` đứng trước sẽ bắt cả ArrayIOOBE và làm nhánh chuyên biệt phía sau không tới được.

Đoạn sau **cố ý sai**:

```java
try {
    readSomething();
} catch (IndexOutOfBoundsException e) {
    // Bắt quá rộng trước.
} catch (ArrayIndexOutOfBoundsException e) {
    // Compiler báo unreachable catch.
}
```

Một multi-catch `catch (IndexOutOfBoundsException | ArrayIndexOutOfBoundsException e)` cũng **không hợp lệ** vì các lựa chọn có quan hệ cha–con; chỉ giữ lớp cha nếu thật sự cần xử lý chung.

## 13. Chương trình Java 17 — ba kiểu lỗi được bắt bởi một lớp cha

Tạo **`BoundsHierarchyDemo.java`**:

```java
import java.util.List;

public class BoundsHierarchyDemo {
    static void inspect(String label, Runnable operation) {
        try {
            operation.run();
        } catch (IndexOutOfBoundsException e) {
            System.out.println(label + ": "
                    + e.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        inspect("array", () -> {
            int[] numbers = {1, 2};
            System.out.println(numbers[2]);
        });

        inspect("list", () -> {
            List<String> names = List.of("A", "B");
            System.out.println(names.get(2));
        });

        inspect("string", () -> {
            String text = "AB";
            System.out.println(text.charAt(2));
        });
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 BoundsHierarchyDemo.java
java BoundsHierarchyDemo
```

**Đầu ra khi chạy với JDK 17 tiêu chuẩn:**

```text
array: ArrayIndexOutOfBoundsException
list: IndexOutOfBoundsException
string: StringIndexOutOfBoundsException
```

Phân tích:

1. `numbers[2]` sai khi length bằng 2: exception từ truy cập mảng.
2. `names.get(2)` sai khi list size bằng 2: implementation `List.of` của JDK 17 thông thường phát `IndexOutOfBoundsException`.
3. `text.charAt(2)` sai với chuỗi dài 2: `StringIndexOutOfBoundsException`.
4. Cả ba đều bị `catch(IndexOutOfBoundsException)` bắt vì quan hệ kế thừa.
5. **Không ràng buộc production code vào chính xác subtype/message của mọi List implementation**. Hợp đồng công khai của `List.get` là `IndexOutOfBoundsException`, và implementation có thể có đặc điểm nội bộ khác.

Bài học về API: chọn handler theo **hợp đồng và chính sách khôi phục**, không chỉ vì muốn in một tên exception cố định.

## 14. Nên phòng tránh trước hay bắt lỗi sau?

Trong code nội bộ, một lỗi vượt chỉ số thường biểu thị bug. Bắt rồi lặng lẽ tiếp tục có thể che bug và để lại trạng thái ứng dụng không đúng.

Anti-pattern:

```java
for (int i = 0; i <= values.length; i++) {
    try {
        total += values[i];
    } catch (ArrayIndexOutOfBoundsException ignored) {
        // Lặng lẽ bỏ lượt cuối.
    }
}
```

Mảnh code minh họa, giả sử `values` và `total` đã định nghĩa. Kết quả hiện tại có thể “trông đúng” tình cờ vì ngoại lệ chỉ xảy ra ở lần cuối; nhưng vòng lặp sai và không an toàn nếu thuật toán đổi. Sửa điều kiện thành `i < values.length`.

### 14.1. Khi nào catch là hợp lý?

- Trong ví dụ học tập và test cần minh họa ngoại lệ.
- Ở ranh giới một yêu cầu độc lập, muốn chuyển lỗi không dự kiến thành failure rõ ràng, ghi log và không cho nó phá điều phối tác vụ khác.
- Trong API có hợp đồng đặc biệt dựa trên exception để báo index sai, caller thật sự có kế hoạch thay index/fallback.
- Khi xử lý nguồn dữ liệu không đáng tin và đã xác định exception tương ứng là một phần hợp đồng điều khiển.

Ngay cả khi catch hợp lý, phải tự hỏi: **tầng này có sửa được lỗi không?** Nếu không, nên báo failure hoặc ném tiếp. Không coi catch là bước “đã chữa khỏi” nguyên nhân.

### 14.2. Vì sao không parse message?

Chuỗi lỗi có thể thay đổi theo JDK, loại mảng, implementer, ngữ cảnh và bản phát hành. Ví dụ không nên:

```java
try {
    useIndex();
} catch (IndexOutOfBoundsException e) {
    if (e.getMessage().contains("Index 3")) {
        // Logic dựa trên text lỗi: dễ vỡ.
    }
}
```

Đây là minh họa cần có `useIndex()`, và message có thể là `null`. Nếu cần trả thông tin chỉ số và giới hạn cho UI, **hãy giữ chúng trong dữ liệu đầu vào/validation**, thay vì cố phân tích `getMessage()`. Nếu cần logging, log exception object và ngữ cảnh index/size riêng, theo chính sách bảo mật.

## 15. `NullPointerException`, `ArrayStoreException` và lỗi khác không đồng nghĩa với bounds

Các lỗi sau có nguyên nhân khác:

| Ngoại lệ | Tình huống |
| --- | --- |
| `ArrayIndexOutOfBoundsException` | Chỉ số truy cập phần tử mảng ngoài `[0, length)` |
| `IndexOutOfBoundsException` | Chỉ số/phạm vi ngoài contract của List/helper |
| `StringIndexOutOfBoundsException` | Chỉ số chuỗi không hợp lệ |
| `NullPointerException` | Dùng một tham chiếu `null` như đối tượng/mảng cần truy cập |
| `ArrayStoreException` | Gán đối tượng không tương thích vào runtime component type của reference array |
| `UnsupportedOperationException` | Thao tác không được collection hỗ trợ, ví dụ thêm vào `List.of` |
| `NegativeArraySizeException` | Tạo mảng có kích thước âm |

Hai ví dụ dễ nhầm:

```java
int[] a = null;
// int x = a[0]; // NullPointerException, không phải lỗi bounds.

int[] b = new int[-1];
// NegativeArraySizeException khi tạo mảng.
```

Đây là các mảnh minh họa **cố ý gây lỗi lúc chạy**.

Với reference array:

```java
Object[] values = new String[1];
// values[0] = Integer.valueOf(123); // ArrayStoreException.
```

Chỉ số `0` **hợp lệ**, nhưng kiểu đối tượng `Integer` không phù hợp với runtime component type `String`. Đây là lỗi lưu kiểu, không phải lỗi vượt chỉ số.

### 15.1. Mảng nhiều chiều có thể lỗi null ở tầng trong

```java
int[][] rows = new int[2][];
rows[0] = new int[] {1};
// rows[1][0]; // NullPointerException vì rows[1] chưa có mảng con.
```

Ngay cả khi `rows.length == 2` và index hàng `1` hợp lệ, mảng con vẫn có thể là `null`. Phải kiểm tra từng tầng đúng loại rủi ro.

## 16. Đường biên dữ liệu và tính nhất quán khi size thay đổi

Giả sử một `List` mutable được chia sẻ giữa hai thread:

```java
if (index >= 0 && index < list.size()) {
    return list.get(index);
}
```

Đây là method minh họa giả định `list` đã được định nghĩa. Điều kiện **không** tự bảo đảm an toàn nếu thread khác có thể giảm size giữa lúc kiểm tra và lúc gọi `get`. Đây là trường hợp *time-of-check to time-of-use* (TOCTOU).

Các chiến lược tùy bối cảnh:

- Giới hạn quyền sở hữu của collection trong một thread.
- Đồng bộ hóa check và use dưới cùng một lock phù hợp (cả các thao tác sửa list phải tuân lock).
- Dùng snapshot bất biến (`List.copyOf`) khi thích hợp để có size và nội dung ổn định.
- Sử dụng cấu trúc concurrent và hiểu hợp đồng thao tác của nó, không giả định tất cả cấu trúc thread-safe có cùng semantics về index.
- Thiết kế API tránh chia sẻ index vào một container đang biến đổi nếu có thể.

**Đừng nói** “đã kiểm tra `index < list.size()` thì không bao giờ có bounds error”: câu này còn phụ thuộc không có mutation không đồng bộ, overflow hay sai lệch biến chứa collection.

### 16.1. `List.subList` và thay đổi danh sách gốc

`subList` thông thường là một **view** trên list gốc, không hẳn bản sao độc lập. Thay đổi cấu trúc list gốc không thông qua sublist có thể làm semantics của view không còn được xác định/không còn hợp lệ theo hợp đồng. Nếu cần snapshot, chọn `new ArrayList<>(list.subList(...))` hoặc `List.copyOf(...)` khi phù hợp, sau khi xác thực phạm vi và concurrency.

## 17. Cách debug khi gặp bounds exception

Khi production xuất hiện `ArrayIndexOutOfBoundsException` hoặc `IndexOutOfBoundsException`, dùng quy trình:

1. **Đọc stack trace**, xác định method và dòng code thực sự truy cập chỉ số.
2. **Xác định container**: mảng, list, chuỗi, buffer, view hay dữ liệu mảng 2D.
3. **Đọc contract index/range** của API được gọi, không đoán mọi API đều `[0,n)`.
4. **Ghi chỉ số và độ dài/size tại cùng ngữ cảnh** (không đưa dữ liệu nhạy cảm vào log).
5. **Kiểm tra các trường hợp biên**: rỗng, một phần tử, index 0, last index, index == n, index âm.
6. **Xem phép biến đổi index**: cộng/trừ, đổi one-based sang zero-based, offset, tính start/end.
7. **Với nhiều container**, xác nhận size đang dùng thuộc đúng container được truy cập.
8. **Xem concurrency**: có ai thay đổi size sau validation không?
9. **Sửa invariant hoặc input validation**, không chỉ thêm catch để dập log.
10. **Thêm regression test** với input tối thiểu tái hiện lỗi.

### 17.1. Đọc exception type trước khi đọc message

Ví dụ:

```text
java.lang.ArrayIndexOutOfBoundsException: Index 3 out of bounds for length 3
    at example.OrderService.calculate(OrderService.java:42)
```

Đây là **minh họa thông điệp**, không phải kết quả cố định trên mọi JDK. Đọc nó như: thao tác mảng dùng index bằng length; tra dòng `calculate`, kiểm tra vòng lặp hoặc công thức index. Đừng xây logic production parse chuỗi `"Index 3..."`, vì thông điệp có thể khác.

### 17.2. Debug nên ghi gì?

Nếu không chứa dữ liệu nhạy cảm, thông tin hữu ích là `index`, `size`, tên thao tác và request correlation ID. Với chuỗi Unicode, nên ghi rõ đang xét **UTF-16 offset** hay **code point offset**. Với jagged array, ghi row index, row count, col index và row length tương ứng.

Không log toàn bộ input/request chỉ vì một index sai; log tối thiểu vẫn thường đủ tìm bug.

## 18. Hiệu năng: bounds check là một phần an toàn của Java

Mọi truy cập mảng theo chỉ số phải tuân theo semantics kiểm tra giới hạn. JVM/JIT có thể **tối ưu bỏ phép kiểm tra lặp** trong một số vòng lặp nếu chứng minh được index luôn hợp lệ, nhưng không được làm thay đổi hành vi Java được quan sát khi chỉ số thực sự sai.

Điều này dẫn đến những lưu ý:

- Không dùng `try/catch` như một phép kiểm tra “nhanh” thay cho điều kiện loop đúng.
- Tránh cố tính `array[index]` với hy vọng ngoài giới hạn thì được giá trị mặc định; Java không làm vậy.
- Một lỗi bounds xảy ra thường xuyên trong hot path là dấu hiệu cần sửa thuật toán/validation, không chỉ tối ưu logging.
- Compiler/JIT không miễn kiểm tra chỉ số sai chỉ vì một vòng lặp được đánh dấu “fast”.
- `for-each`, `Arrays` và collection API có thể giúp hạn chế lỗi chỉ số thủ công; chọn cấu trúc hợp nhiệm vụ.

Không cần tự đoán JIT đã loại bỏ bounds check tại runtime cụ thể. Nếu hiệu năng quan trọng, benchmark trên workload thật và giữ kiểm thử correctness trước.

## 19. Unit test đường biên

Các ca test có giá trị:

| Kích thước cấu trúc | Input | Kỳ vọng |
| --- | --- | --- |
| Mảng rỗng `n=0` | `0` | `ArrayIndexOutOfBoundsException` hoặc validation failure trước truy cập |
| Mảng một phần tử `n=1` | `0` | Hợp lệ |
| Mảng một phần tử `n=1` | `1` | Ngoài giới hạn |
| Mảng ba phần tử `n=3` | `2` | Phần tử cuối hợp lệ |
| Mảng ba phần tử `n=3` | `3` | Ngoài giới hạn |
| Bất kỳ `n>=0` | `-1` | Ngoài giới hạn |
| Subrange `n=3` | `[3,3)` | Đoạn rỗng hợp lệ nếu API cho phép |
| Subrange `n=3` | `[2,4)` | Ngoài giới hạn |
| Mảng răng cưa | Hàng 1 có length 1, col 1 | Ngoài giới hạn của hàng |

### 19.1. Mẫu JUnit 5 minh họa

```java
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoundsContractTest {
    @Test
    void arrayRejectsInvalidIndex() {
        int[] values = {4, 5};
        assertEquals(5, values[1]);
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> { int ignored = values[2]; });
    }

    @Test
    void listRejectsInvalidIndexByContract() {
        List<Integer> values = List.of(4, 5);
        assertThrows(IndexOutOfBoundsException.class,
                () -> values.get(2));
    }

    @Test
    void halfOpenRangeAllowsEmptyTail() {
        List<String> names = List.of("A", "B");
        assertTrue(names.subList(2, 2).isEmpty());
    }
}
```

**Đây là file test dành cho một dự án có JUnit Jupiter**, không phải chương trình `javac` độc lập chỉ dùng thư viện chuẩn. Test kiểm tra hợp đồng của thao tác thay vì so sánh message exception. `assertThrows(IndexOutOfBoundsException.class, ...)` chấp nhận instance của subclass nếu implementation dùng subtype.

### 19.2. Test validation khác test exception phát sinh tự nhiên

Nếu public API kiểm tra `index` rồi chủ động trả một `Result` lỗi, nên test **kết quả validation**, không buộc phải ném `ArrayIndexOutOfBoundsException`. Exception runtime từ truy cập mảng thường phản ánh một đường bug; ứng dụng có thể chọn báo lỗi input bằng cách khác trước khi chạm vào mảng.

## 20. Các lỗi phổ biến và cách sửa

| Sai lầm | Kết quả | Cách sửa |
| --- | --- | --- |
| Dùng `i <= array.length` khi đọc `array[i]` | Truy cập index bằng length | Dùng `i < array.length` |
| Đọc `array[array.length]` để lấy cuối | Luôn vượt giới hạn | Dùng `array[array.length-1]` nếu không rỗng |
| Đọc `array[0]` khi length 0 | `ArrayIndexOutOfBoundsException` | Kiểm tra rỗng/thiết kế empty case |
| Dùng `list.get(list.size())` để append | Index đọc vượt giới hạn | `list.add(element)` hoặc `list.add(size, element)` nếu mutable |
| Dùng length của hàng 0 cho mọi hàng | Jagged array có row khác length | Dùng `matrix[row].length` |
| Dùng `String.charAt(length)` | String index sai | Xác định index hợp lệ, hoặc dùng substring end-exclusive |
| Nhầm code point offset với UTF-16 index | Truy cập giữa surrogate pair hoặc sai vị trí | Dùng `codePointAt`/`offsetByCodePoints` |
| Lấy khoảng `[from,to]` nhưng API hiểu `[from,to)` | Lệch một phần tử | Đọc contract endpoint exclusive |
| Coi `i==n` luôn sai | Sai với một số thao tác chèn/empty end range | Phân biệt index phần tử và vị trí chèn/range |
| Bắt `ArrayIndexOutOfBoundsException` để xử lý mọi List lỗi index | Có thể bỏ sót IndexOutOfBoundsException thuần | Dựa trên API contract, bắt cha khi thật cần |
| Đặt `catch(IndexOutOfBoundsException)` trước catch subclass | Handler subclass unreachable | Đặt catch cụ thể trước |
| Multi-catch cha + con | Không biên dịch | Giữ cha hoặc hai catch riêng hợp lệ |
| Catch rồi bỏ qua trong vòng lặp | Che bug điều kiện lặp | Sửa invariant |
| Parse `getMessage()` để lấy index | Message khác theo runtime/phiên bản | Giữ index và size riêng |
| Quên `null` array | `NullPointerException`, không phải bounds | Kiểm tra dữ liệu/null độc lập |
| Dùng `new int[-1]` rồi nghĩ lỗi access | `NegativeArraySizeException` khi tạo | Validate kích thước tạo |
| Sửa List đồng thời sau khi kiểm tra size | Check/use có thể lệch | Đồng bộ, snapshot hoặc ownership phù hợp |
| Đo index từ 1 rồi dùng trực tiếp với mảng | Lệch 1 | Validate vị trí one-based rồi trừ 1 |
| Dùng chỉ số đã tính có nguy cơ overflow | Index biến thành âm hoặc giá trị sai | Dùng phép toán/validation tránh tràn |
| Trả raw stack trace cho client | Lộ chi tiết nội bộ | Map sang phản hồi lỗi an toàn và log có kiểm soát |

## 21. Câu hỏi thường gặp

### 21.1. `ArrayIndexOutOfBoundsException` có phải là `IndexOutOfBoundsException` không?

Có, vì nó **extends** `IndexOutOfBoundsException`. Vì vậy `catch(IndexOutOfBoundsException)` có thể bắt `ArrayIndexOutOfBoundsException`, còn `catch(ArrayIndexOutOfBoundsException)` không đảm bảo bắt được lỗi do `List.get` hoặc `String.charAt`.

### 21.2. Hai exception có phải checked exception không?

Không. Chúng thuộc `RuntimeException`, nên code không bắt buộc khai báo `throws` hoặc bắt chúng. Tuy nhiên vẫn có thể bắt khi có chính sách xử lý đúng.

### 21.3. `array.length` có phải chỉ số cuối không?

Không. Với mảng không rỗng, chỉ số cuối là `array.length - 1`. Nếu mảng rỗng, không có chỉ số nào hợp lệ.

### 21.4. Có thể truy cập `array[-1]` để lấy phần tử cuối như Python không?

Không. Java **không** dùng indexing âm để truy cập từ cuối. `array[-1]` phát sinh `ArrayIndexOutOfBoundsException`. Muốn lấy phần tử cuối, kiểm tra mảng không rỗng rồi dùng `array[array.length-1]`.

### 21.5. Vì sao `list.add(list.size(), x)` có thể hợp lệ, nhưng `list.get(list.size())` không?

`add(index, element)` (với List mutable) thường nhận **vị trí chèn** thuộc `[0,size]`, kể cả cuối. `get(index)` nhận **vị trí phần tử** thuộc `[0,size)`. Đây là hai hợp đồng khác nhau.

### 21.6. Vì sao `substring(0, length)` hợp lệ nhưng `charAt(length)` sai?

Vì `substring(begin,end)` dùng **end-exclusive**; chỉ số end là ranh giới sau phần tử cuối. `charAt(index)` cần chính vị trí một code unit tồn tại, nên index phải nhỏ hơn length.

### 21.7. Có nên bọc mọi phép truy cập bằng `try/catch(IndexOutOfBoundsException)`?

Thường không. Đối với code nội bộ, sửa loop/validation và kiểm thử đường biên rõ hơn. Bắt exception chỉ phù hợp nếu tầng đó có chính sách xử lý hoặc chuyển báo lỗi thực sự.

### 21.8. Có thể trông cậy vào message “Index X out of bounds for length N” không?

Không như một **giao thức ổn định**. Một số JDK sinh message như vậy, nhưng text có thể khác giữa implementation và loại API. Dùng exception type và dữ liệu index/size gốc thay vì parse message.

### 21.9. `List.of` truy cập sai index có luôn ném ArrayIndexOutOfBoundsException không?

Không nên khẳng định điều đó. `List` công bố contract `IndexOutOfBoundsException`; implementation JDK có thể dùng cách kiểm tra và subtype cụ thể. Nếu cần xử lý chung các lỗi index của List, bắt lớp cha.

### 21.10. Bounds check có bảo đảm thread safety không?

Không. Bounds check xác nhận chỉ số với trạng thái được quan sát ở một thao tác; list mutable bị thread khác thay đổi vẫn có thể gây lỗi hoặc vấn đề nhất quán. Cần chính sách đồng bộ/snapshot độc lập.

## 22. Bài tập tự luyện

### Bài 1 — Dự đoán các chỉ số

`int[] a = {2, 4, 6};`. Cho biết kết quả của `a[0]`, `a[2]`, `a[3]` và `a[-1]`. **Đáp án:** `2`, `6`, hai lần lỗi `ArrayIndexOutOfBoundsException`.

### Bài 2 — Sửa vòng lặp

Cho `for (int i = 0; i <= a.length; i++)`, hãy viết lại điều kiện duyệt tất cả phần tử và xử lý trường hợp mảng rỗng. **Đáp án:** `i < a.length`; vòng lặp tự bỏ qua nếu mảng rỗng.

### Bài 3 — Catch theo phân cấp

Viết một đoạn có `catch(ArrayIndexOutOfBoundsException)` trước `catch(IndexOutOfBoundsException)`. Giải thích vì sao đảo thứ tự khiến handler thứ hai không thể tới được.

### Bài 4 — Jagged array

Với `int[][] a = {{1,2,3}, {4}}`, giải thích `a[1][0]`, `a[1][1]` và `a[2][0]`. Tại sao dùng `a[0].length` cho tất cả hàng là sai?

### Bài 5 — String và UTF-16

Chạy `StringBoundsUnicodeDemo`. Giải thích vì sao `length()` bằng 4 nhưng `codePointCount` bằng 3. `charAt(2)` sai về index hay chỉ là lấy một phần của surrogate pair? **Đáp án:** index 2 hợp lệ, nhưng trả low surrogate.

### Bài 6 — Bắt đầu ở 1

Một CLI nhận vị trí người dùng 1..5. Viết validation trước khi chuyển thành chỉ số 0..4. Kiểm tra cả `0`, `-1`, `6` và `Integer.MIN_VALUE` mà không làm phép trừ 1 trước validation.

### Bài 7 — End-exclusive

Cho `List.of("A","B","C")`, dự đoán `subList(1,3)`, `subList(3,3)` và `subList(2,4)`. **Đáp án:** `[B,C]`, `[]`, lỗi vượt giới hạn.

### Bài 8 — `Objects.checkIndex`

Chạy `ObjectsIndexCheckDemo`. Sửa các lời gọi để kiểm tra dải `[2,2)`, `[0,4)` và `from=3,size=0` với length 3. Điều kiện nào hợp lệ? Vì sao đoạn rỗng ở cuối có thể hợp lệ nhưng phần tử tại index 3 không tồn tại?

### Bài 9 — Tác động của mutation

Một `ArrayList` có size 4, thread A kiểm tra index 3 hợp lệ, thread B xóa phần tử trước khi A gọi `get(3)`. Giải thích vì sao check trước đó chưa đủ và đưa ra giải pháp ownership/snapshot/lock phù hợp.

### Bài 10 — Regression test

Viết JUnit 5 test cho một method `elementAt(int[], int)`: thử mảng rỗng, chỉ số âm, index cuối, index bằng length. Chỉ assert loại exception và hành vi, không dựa trên text message.

## 23. Checklist trước khi hoàn thành

- [ ] Tôi phân biệt được lớp cha `IndexOutOfBoundsException` và lớp con `ArrayIndexOutOfBoundsException` chưa?
- [ ] Tôi biết hai lớp này là unchecked `RuntimeException`, không phải checked exception chưa?
- [ ] Với truy cập phần tử, tôi luôn xác minh `0 <= i && i < length/size` chứ?
- [ ] Tôi không nhầm `length`/`size()` với chỉ số phần tử cuối chưa?
- [ ] Tôi xử lý mảng rỗng trước khi dùng `arr[0]` hoặc `arr[length-1]` chưa?
- [ ] Tôi dùng `<` thay vì `<=` trong vòng lặp đọc `arr[i]` chưa?
- [ ] Tôi phân biệt index của phần tử với vị trí chèn cho phép bằng `size` chưa?
- [ ] Tôi hiểu đoạn `[from,to)` cho phép `to == length` và có thể rỗng chưa?
- [ ] Tôi biết `List.get` theo hợp đồng ném `IndexOutOfBoundsException`, không phụ thuộc một subtype runtime chưa?
- [ ] Tôi biết `String.length` dùng UTF-16 code units, không luôn bằng số Unicode code points chưa?
- [ ] Với mảng nhiều chiều, tôi đã dùng `row.length` của từng hàng, không giả mọi hàng đều dài bằng nhau chưa?
- [ ] Tôi phân biệt bounds error với `NullPointerException`, `ArrayStoreException`, `NegativeArraySizeException` chưa?
- [ ] Tôi có validate đầu vào người dùng và chuyển one-based/zero-based đúng không?
- [ ] Tôi có cân nhắc `Objects.checkIndex`/`checkFromToIndex`/`checkFromIndexSize` khi viết API phạm vi chưa?
- [ ] Tôi tránh dùng `try/catch` để che bug vòng lặp hoặc trả thành công giả chưa?
- [ ] Tôi đặt catch subtype trước catch lớp cha nếu có cả hai chưa?
- [ ] Tôi đã xét concurrency và thay đổi size giữa check/use chưa?
- [ ] Tôi không phụ thuộc message hoặc số dòng stack trace như giao thức xử lý nghiệp vụ chưa?
- [ ] Tôi có test input `-1`, `0`, `n-1`, `n`, mảng rỗng và các đoạn rỗng chưa?

**Kết luận:** `ArrayIndexOutOfBoundsException` là **một loại `IndexOutOfBoundsException`** chuyên cho truy cập mảng. Cả hai là unchecked exceptions. Truy cập phần tử hợp lệ dùng **`0 <= index < length/size`**; một số API vị trí chèn hoặc dải **`[from,to)`** cho phép đầu mút bằng `length/size`. Giữ điều kiện vòng lặp chính xác, hiểu contract từng API, kiểm tra dữ liệu tại biên, xử lý mảng răng cưa và Unicode đúng loại chỉ số; nếu có lỗi, sửa invariant thay vì chỉ bắt rồi bỏ qua.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-runtime-exceptions-004">NumberFormatException và chuyển đổi dữ liệu đầu vào</a>

<details>
<summary>Xem chi tiết</summary>

# NumberFormatException và chuyển đổi dữ liệu đầu vào trong Java

Khi đọc dữ liệu từ biểu mẫu, API, file CSV, dòng lệnh hoặc cấu hình, chương trình thường nhận **String** trước khi chuyển thành **int**, **long**, **double** hay **BigDecimal**. Nếu chuỗi không đúng cú pháp số hoặc không thể biểu diễn trong kiểu đích, nhiều API Java báo **java.lang.NumberFormatException**.

Đây là **unchecked exception**, nằm trong chuỗi kế thừa **Throwable → Exception → RuntimeException → IllegalArgumentException → NumberFormatException**. Trình biên dịch không bắt buộc khai báo **throws NumberFormatException** hay sử dụng **try-catch**. Tuy nhiên, đầu vào không tin cậy cần được kiểm tra, phản hồi và ghi nhận rõ ràng. Một chuỗi parse thành công cũng chưa chắc thỏa mãn quy tắc nghiệp vụ.

Bài học trình bày cú pháp chuyển số nguyên và số thực, phạm vi kiểu số, cơ số, dấu/khoảng trắng, số thập phân chính xác, Unicode, locale và cách đặt ranh giới xử lý ngoại lệ. Các chương trình hoàn chỉnh chạy bằng Java 17+.

## 1. Hiểu đúng ngoại lệ và hợp đồng API

Ví dụ **Integer.parseInt("abc")** ném NumberFormatException, vì ký tự đầu vào không tạo thành số nguyên hợp lệ. **Integer.parseInt("2147483648")** cũng ném cùng loại lỗi dù chuỗi chỉ gồm chữ số: giá trị vượt giới hạn **Integer.MAX_VALUE = 2147483647**.

Điều cần phân biệt:

- **Lỗi cú pháp:** chuỗi rỗng, sai chữ số, có ký tự không được phép.
- **Lỗi miền biểu diễn:** giá trị số nguyên lớn hơn hoặc nhỏ hơn miền kiểu đích.
- **Lỗi nghiệp vụ:** parse thành công nhưng giá trị vi phạm quy tắc, chẳng hạn tuổi 200 hoặc số lượng âm.
- **Lỗi bước chuyển đổi khác:** long hợp lệ nhưng không thể chuyển chính xác xuống int bằng **Math.toIntExact**, khi ấy Java ném **ArithmeticException**, không phải NumberFormatException.

Vì NumberFormatException kế thừa IllegalArgumentException, một nhánh **catch (IllegalArgumentException ex)** có thể bắt nó. Nếu hai nhánh cùng cấp tồn tại thì phải đặt nhánh **catch (NumberFormatException ex)** trước; đặt nhánh cha trước sẽ che nhánh con, gây lỗi biên dịch.

Thông điệp **ex.getMessage()** không phải mã lỗi nghiệp vụ ổn định. Nó có thể chứa chuỗi đầu vào và thay đổi giữa overload/JDK; không nên phân loại lỗi bằng cách so sánh chính xác toàn bộ thông điệp từ JDK.

## 2. Cú pháp của Integer.parseInt(String)

Với cơ số 10, parser chấp nhận chữ số hợp lệ và có thể có dấu cộng/trừ ở đầu, miễn giá trị nằm trong miền int.

| Đầu vào | Kết quả |
| --- | --- |
| "42" | int 42 |
| "+7" | int 7 |
| "-12" | int -12 |
| "0009" | int 9 |
| "" | NumberFormatException |
| " 42 " | NumberFormatException vì không tự bỏ khoảng trắng |
| "12.5" | NumberFormatException vì đây không phải số nguyên |
| "1,000" | NumberFormatException vì có dấu phân nhóm |
| "1_000" | NumberFormatException; dấu gạch dưới trong source numeric literal không áp dụng cho chuỗi runtime |
| "0xFF" | NumberFormatException với parseInt cơ số 10 |
| "2147483648" | NumberFormatException vì vượt int |
| null | NumberFormatException |

Một chương trình Java có thể viết numeric literal **1_000**, nhưng **Integer.parseInt("1_000")** không tự hiểu cú pháp dấu gạch dưới đó. Tương tự, dấu thập phân dùng trong locale không tự động được parser số nguyên nhận diện.

### Chương trình 1: parse thành công và thất bại

Lưu thành **IntegerInputDemo.java**:

~~~java
public class IntegerInputDemo {
    static void inspect(String raw) {
        try {
            int value = Integer.parseInt(raw);
            System.out.println("ok=[" + raw + "]:" + value);
        } catch (NumberFormatException ex) {
            System.out.println("invalid=[" + raw + "]");
        }
    }

    public static void main(String[] args) {
        inspect("27");
        inspect("-4");
        inspect("+6");
        inspect("");
        inspect("2.5");
        inspect("2147483648");
        inspect(" 12 ");
    }
}
~~~

~~~bash
javac -encoding UTF-8 IntegerInputDemo.java
java IntegerInputDemo
~~~

Kết quả:

~~~text
ok=[27]:27
ok=[-4]:-4
ok=[+6]:6
invalid=[]
invalid=[2.5]
invalid=[2147483648]
invalid=[ 12 ]
~~~

Chương trình cố ý không in thông điệp JDK để stdout không phụ thuộc phiên bản. Khi hiển thị lỗi cho người dùng, hãy tự mô tả trường nhập và định dạng được phép.

## 3. Chọn phương thức theo kiểu số đích

| Nhu cầu | API |
| --- | --- |
| Số nguyên có dấu 32 bit | **Integer.parseInt(text)**, trả primitive int |
| Đối tượng Integer | **Integer.valueOf(text)**, trả wrapper Integer |
| Số nguyên có dấu 64 bit | **Long.parseLong(text)** |
| Số nguyên độ lớn tùy ý | **new BigInteger(text)** |
| Số nguyên với cơ số 2 đến 36 | **Integer.parseInt(text, radix)** |
| Chuỗi có tiền tố chỉ cơ số theo quy tắc Java | **Integer.decode(text)** |
| Số thực nhị phân 64 bit | **Double.parseDouble(text)** |
| Số thực nhị phân 32 bit | **Float.parseFloat(text)** |
| Giá trị decimal cần độ chính xác từ chuỗi | **new BigDecimal(text)** |
| Chuỗi số theo locale | **NumberFormat** hoặc **DecimalFormat** với Locale rõ |

**Integer.valueOf("42")** cho giá trị số bằng Integer.parseInt("42"), nhưng trả đối tượng wrapper. Không dùng phép so sánh tham chiếu **==** giữa hai wrapper để quyết định giá trị bằng nhau; dùng phương thức equals hoặc giá trị primitive khi thích hợp.

Đừng chọn kiểu số chỉ theo chiều dài chuỗi. Nếu dữ liệu là mã bưu chính hoặc ID có số 0 đầu, chuyển thành int có thể làm mất biểu diễn gốc. Trường đó có thể nên giữ kiểu String nếu không có phép toán số học.

## 4. Cơ số: parseInt(radix) và decode không giống nhau

Integer.parseInt(text, radix) đọc chữ số ở cơ số **2..36**. Trong khi đó, Integer.decode nhận cú pháp có tiền tố như **0x**, **#**, và có quy tắc octal khi chuỗi có số 0 đầu.

### Chương trình 2: khác biệt cơ số

Lưu thành **RadixAndDecodeDemo.java**:

~~~java
public class RadixAndDecodeDemo {
    public static void main(String[] args) {
        System.out.println("binary=" + Integer.parseInt("101101", 2));
        System.out.println("hex=" + Integer.parseInt("7F", 16));
        System.out.println("negativeHex=" + Integer.parseInt("-20", 16));
        System.out.println("base36=" + Integer.parseInt("Z", 36));
        System.out.println("decimal010=" + Integer.parseInt("010"));
        System.out.println("decode010=" + Integer.decode("010"));
        System.out.println("decodeHex=" + Integer.decode("0x2A"));
        System.out.println("decodeHash=" + Integer.decode("#2A"));

        try {
            Integer.parseInt("0x2A");
        } catch (NumberFormatException ex) {
            System.out.println("prefixed=invalid");
        }
        try {
            Integer.parseInt("101", 1);
        } catch (NumberFormatException ex) {
            System.out.println("radix1=invalid");
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 RadixAndDecodeDemo.java
java RadixAndDecodeDemo
~~~

Kết quả:

~~~text
binary=45
hex=127
negativeHex=-32
base36=35
decimal010=10
decode010=8
decodeHex=42
decodeHash=42
prefixed=invalid
radix1=invalid
~~~

Khi dữ liệu người dùng là số thập phân, tránh dùng decode một cách tùy tiện: giá trị **"010"** có thể bị hiểu thành octal 8, không phải decimal 10. Dùng parseInt(text, 10) khi hợp đồng thật sự yêu cầu cơ số 10.

## 5. Overflow: lỗi parse khác lỗi ép kiểu

Giới hạn số nguyên Java:

~~~text
int  : -2147483648 .. 2147483647
long : -9223372036854775808 .. 9223372036854775807
~~~

Một chuỗi vượt giới hạn int gây NumberFormatException ở **Integer.parseInt**. Nhưng sau khi parse thành công một long, nếu muốn chuyển chính xác về int bằng **Math.toIntExact**, thất bại gây ArithmeticException.

### Chương trình 3: ba cách xử lý miền số nguyên

Lưu thành **NumericBoundsDemo.java**:

~~~java
import java.math.BigInteger;

public class NumericBoundsDemo {
    public static void main(String[] args) {
        String large = "2147483648";
        System.out.println("long=" + Long.parseLong(large));
        System.out.println("big=" + new BigInteger(large));

        try {
            Integer.parseInt(large);
        } catch (NumberFormatException ex) {
            System.out.println("int=out-of-range");
        }

        try {
            Math.toIntExact(Long.parseLong(large));
        } catch (ArithmeticException ex) {
            System.out.println("narrowing=out-of-range");
        }

        String huge = "9223372036854775808";
        try {
            Long.parseLong(huge);
        } catch (NumberFormatException ex) {
            System.out.println("long=out-of-range");
        }
        System.out.println("bigHuge=" + new BigInteger(huge));
    }
}
~~~

~~~bash
javac -encoding UTF-8 NumericBoundsDemo.java
java NumericBoundsDemo
~~~

Kết quả:

~~~text
long=2147483648
big=2147483648
int=out-of-range
narrowing=out-of-range
long=out-of-range
bigHuge=9223372036854775808
~~~

Việc ép kiểu **(int) someLong** có thể làm mất các bit cao và không tạo ngoại lệ. Để kiểm tra chính xác, dùng Math.toIntExact hoặc kiểm tra miền trước khi chuyển. BigInteger cho phép số nguyên rất lớn nhưng vẫn cần giới hạn độ dài đầu vào để tránh chi phí CPU/bộ nhớ không kiểm soát.

## 6. Double.parseDouble và những giá trị không hữu hạn

NumberFormatException không được ném cho tất cả giá trị số thực mà nghiệp vụ không muốn chấp nhận. Double.parseDouble hỗ trợ ký hiệu mũ, NaN, Infinity và có thể trả Infinity cho số mũ quá lớn.

Ví dụ:

- **Double.parseDouble("1.5e2")** → 150.0.
- **Double.parseDouble("NaN")** → NaN.
- **Double.parseDouble("Infinity")** → vô cực dương.
- **Double.parseDouble("1e309")** → vô cực trên kiểu double.
- **Double.parseDouble("1,5")** → NumberFormatException.
- **Double.parseDouble("1_000")** → NumberFormatException.

Khác Integer.parseInt, Double.parseDouble chấp nhận một số khoảng trắng đầu/cuối theo hợp đồng parser tương ứng String.trim(). Không được suy luận rằng mọi API chuyển số đều có cùng chính sách khoảng trắng.

### Chương trình 4: tách parse sai khỏi giá trị không hữu hạn

Lưu thành **FloatingInputDemo.java**:

~~~java
public class FloatingInputDemo {
    static void inspect(String raw) {
        try {
            double value = Double.parseDouble(raw);
            if (!Double.isFinite(value)) {
                System.out.println("not-finite=[" + raw + "]");
            } else {
                System.out.println("finite=[" + raw + "]:" + value);
            }
        } catch (NumberFormatException ex) {
            System.out.println("invalid=[" + raw + "]");
        }
    }

    public static void main(String[] args) {
        inspect("12.5");
        inspect("1.5e2");
        inspect("NaN");
        inspect("Infinity");
        inspect("1e309");
        inspect("1,5");
        inspect("1_000");
    }
}
~~~

~~~bash
javac -encoding UTF-8 FloatingInputDemo.java
java FloatingInputDemo
~~~

Kết quả:

~~~text
finite=[12.5]:12.5
finite=[1.5e2]:150.0
not-finite=[NaN]
not-finite=[Infinity]
not-finite=[1e309]
invalid=[1,5]
invalid=[1_000]
~~~

Với một trường yêu cầu số hữu hạn, parse thành công mới là bước đầu: tiếp tục kiểm tra **Double.isFinite(value)** rồi mới xét min/max và quy tắc nghiệp vụ.

## 7. BigDecimal(String) cho số thập phân cần chính xác

Double dùng biểu diễn nhị phân, nên **0.1 + 0.2** trong double thường không đúng chính xác giá trị thập phân 0.3. Khi dữ liệu nguồn là chuỗi số tiền, **new BigDecimal(String)** thường phù hợp hơn.

### Chương trình 5: số thập phân và lỗi chuyển đổi

Lưu thành **DecimalMoneyDemo.java**:

~~~java
import java.math.BigDecimal;

public class DecimalMoneyDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("0.1");
        BigDecimal b = new BigDecimal("0.2");
        System.out.println("exact=" + a.add(b));
        System.out.println("doubleSum=" + (0.1 + 0.2));
        System.out.println("scientific="
                + new BigDecimal("1.25e2").toPlainString());

        try {
            new BigDecimal("1,25");
        } catch (NumberFormatException ex) {
            System.out.println("comma=invalid");
        }

        try {
            new BigDecimal("NaN");
        } catch (NumberFormatException ex) {
            System.out.println("nan=invalid");
        }

        try {
            new BigDecimal("12.5").intValueExact();
        } catch (ArithmeticException ex) {
            System.out.println("fraction=not-an-exact-int");
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 DecimalMoneyDemo.java
java DecimalMoneyDemo
~~~

Kết quả:

~~~text
exact=0.3
doubleSum=0.30000000000000004
scientific=125
comma=invalid
nan=invalid
fraction=not-an-exact-int
~~~

Giá trị decimal từ chuỗi được BigDecimal bảo toàn. BigDecimal không có giá trị NaN/Infinity như double; chuỗi không hợp lệ có thể tạo NumberFormatException. Còn phương thức **intValueExact()** có thể ném ArithmeticException khi có phần lẻ hoặc vượt miền int.

Tránh dùng new BigDecimal(someDouble) chỉ để cố phục hồi giá trị văn bản ban đầu: double có thể đã mang sai số nhị phân. Nếu nhận chuỗi decimal, ưu tiên parse trực tiếp từ chuỗi.

## 8. Xác thực đầu vào theo tầng, không chỉ thử parse

Một trường tuổi có thể có hợp đồng:

1. Bắt buộc nhập khác null/rỗng.
2. Cho phép bỏ khoảng trắng đầu và cuối bằng String.strip().
3. Chỉ chấp nhận chữ số ASCII 0..9, không cho dấu âm/dương.
4. Giá trị phải nằm trong miền int.
5. Tuổi phải không vượt 150.

~~~text
Raw input
  |
  +-- null / empty? -> "age required"
  |
  v
String.strip()
  |
  +-- not [0-9]+? -> "age must contain ASCII digits"
  |
  v
Integer.parseInt()
  |
  +-- NFE? -> "age out of integer range"
  |
  v
age <= 150?
  |
  +-- no -> "age must be <= 150"
  |
  v
Valid age
~~~

### Chương trình 6: lỗi đầu vào có ngữ cảnh và giữ cause

Lưu thành **ValidatedAgeDemo.java**:

~~~java
public class ValidatedAgeDemo {
    static int parseAge(String raw) {
        if (raw == null || raw.strip().isEmpty()) {
            throw new IllegalArgumentException("age required");
        }

        String text = raw.strip();
        if (!text.matches("[0-9]+")) {
            throw new IllegalArgumentException(
                    "age must contain ASCII digits");
        }

        final int age;
        try {
            age = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "age out of integer range", ex);
        }

        if (age > 150) {
            throw new IllegalArgumentException(
                    "age must be <= 150");
        }
        return age;
    }

    static void inspect(String raw) {
        try {
            System.out.println("age=" + parseAge(raw));
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid=" + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        inspect(" 21 ");
        inspect("-1");
        inspect("200");
        inspect("2147483648");
        inspect("");
        inspect(null);
    }
}
~~~

~~~bash
javac -encoding UTF-8 ValidatedAgeDemo.java
java ValidatedAgeDemo
~~~

Kết quả:

~~~text
age=21
invalid=age must contain ASCII digits
invalid=age must be <= 150
invalid=age out of integer range
invalid=age required
invalid=age required
~~~

Ở đây, **"200"** parse đúng nhưng bị loại vì chính sách tuổi; **"-1"** bị loại ở bước grammar; **"2147483648"** qua bước grammar nhưng lỗi miền int. Khi chuyển NumberFormatException sang IllegalArgumentException với tham số **cause**, thông tin kỹ thuật ban đầu vẫn có thể truy ở tầng logging.

Một trường khác có thể cho phép dấu âm hoặc chữ số Unicode. Vì vậy không nên áp dụng chính sách regex ở ví dụ tuổi một cách máy móc cho mọi loại dữ liệu.

## 9. Chữ số Unicode và khác biệt với ASCII

Integer.parseInt sử dụng Character.digit để nhận chữ số theo cơ số, không chỉ ký tự ASCII trong một số tình huống. Ngược lại, regex **[0-9]+** trong ví dụ tuổi chỉ cho phép ASCII.

### Chương trình 7: số Arabic-Indic

Lưu thành **UnicodeDigitDemo.java**:

~~~java
public class UnicodeDigitDemo {
    public static void main(String[] args) {
        String arabicIndic = "\u0661\u0662";
        System.out.println("parsed=" + Integer.parseInt(arabicIndic));
        System.out.println("asciiOnly="
                + arabicIndic.matches("[0-9]+"));
        System.out.println("digit=" + Character.digit('\u0661', 10));
    }
}
~~~

~~~bash
javac -encoding UTF-8 UnicodeDigitDemo.java
java UnicodeDigitDemo
~~~

Kết quả:

~~~text
parsed=12
asciiOnly=false
digit=1
~~~

Các ký tự Unicode U+0661 và U+0662 là chữ số Arabic-Indic tương ứng 1 và 2. Nếu hệ thống giao tiếp với một dịch vụ chỉ chấp nhận ASCII, cần xác thực rõ; nếu hỗ trợ nhập liệu đa ngôn ngữ, cần công bố/kiểm thử chính sách Unicode. Một dấu trừ typographic không nhất thiết được parse như dấu trừ ASCII.

## 10. Xử lý batch: trả trạng thái cho từng mục

Nếu các dòng CSV độc lập, một mục nhập sai không nhất thiết phải dừng toàn bộ batch. Có thể chuyển từng mục thành một giá trị biểu diễn kết quả thay vì ném lỗi dừng vòng lặp.

### Chương trình 8: record mang kết quả hoặc lỗi

Lưu thành **BatchNumberParsingDemo.java**:

~~~java
import java.util.List;

public class BatchNumberParsingDemo {
    record ParseResult(String raw, Integer value, String error) {
        boolean success() {
            return error == null;
        }
    }

    static ParseResult parseOne(String raw) {
        try {
            return new ParseResult(raw, Integer.parseInt(raw), null);
        } catch (NumberFormatException ex) {
            return new ParseResult(raw, null, "invalid integer");
        }
    }

    public static void main(String[] args) {
        for (String raw : List.of("10", "bad", "25", "2147483648")) {
            ParseResult result = parseOne(raw);
            if (result.success()) {
                System.out.println("ok=" + result.raw()
                        + ":" + result.value());
            } else {
                System.out.println("error=" + result.raw()
                        + ":" + result.error());
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 BatchNumberParsingDemo.java
java BatchNumberParsingDemo
~~~

Kết quả:

~~~text
ok=10:10
error=bad:invalid integer
ok=25:25
error=2147483648:invalid integer
~~~

Record là tính năng chuẩn của Java 17. Ví dụ minh họa cách gom lỗi theo phần tử; ở môi trường sản xuất nên áp đặt bất biến như **thành công hoặc thất bại, không đồng thời cả hai**. Nếu tác vụ cần tính nguyên tử, việc tiếp tục sau lỗi có thể không phù hợp; lựa chọn này thuộc hợp đồng nghiệp vụ.

## 11. Dữ liệu dòng lệnh và kiểm tra số lượng đối số

Khi truy cập **args[0]**, một lỗi khác có thể xảy ra **trước khi parse**: nếu chương trình chạy không có đối số, việc truy cập mảng gây ArrayIndexOutOfBoundsException. Vì vậy phải kiểm tra **args.length**.

Lưu thành **CommandLineNumberDemo.java**:

~~~java
public class CommandLineNumberDemo {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("usage: java CommandLineNumberDemo <count>");
            return;
        }

        final int count;
        try {
            count = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            System.out.println("count must be an integer");
            return;
        }

        if (count < 0) {
            System.out.println("count must be non-negative");
            return;
        }

        System.out.println("count=" + count);
    }
}
~~~

~~~bash
javac -encoding UTF-8 CommandLineNumberDemo.java
java CommandLineNumberDemo 12
java CommandLineNumberDemo abc
java CommandLineNumberDemo -2
~~~

Kết quả của ba lần chạy:

~~~text
count=12
count must be an integer
count must be non-negative
~~~

Khi không có đối số, chương trình in dòng usage. Một CLI thực tế có thể cần exit status khác 0, nhưng không nên dùng System.exit trong ví dụ chỉ nhằm giải thích cơ chế parse.

## 12. Dấu thập phân theo locale và phân tích toàn chuỗi

Integer.parseInt và Double.parseDouble không tự động phân tích định dạng theo mọi vùng miền. Chẳng hạn chuỗi **"1.234,5"** có nghĩa 1234.5 theo Locale.GERMANY, nhưng không hợp lệ khi đưa trực tiếp cho Double.parseDouble.

Có thể dùng **NumberFormat.getNumberInstance(Locale.GERMANY)**. Lưu ý: **NumberFormat.parse(String, ParsePosition)** có thể parse thành công một tiền tố rồi dừng trước hậu tố sai. Nếu dữ liệu phải khớp toàn chuỗi, hãy kiểm tra vị trí kết thúc.

Lưu thành **LocaleNumberDemo.java**:

~~~java
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

public class LocaleNumberDemo {
    static Number parseAll(String text, Locale locale) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        ParsePosition position = new ParsePosition(0);
        Number value = format.parse(text, position);

        if (value == null || position.getIndex() != text.length()) {
            throw new IllegalArgumentException("invalid localized number");
        }
        return value;
    }

    public static void main(String[] args) {
        Number value = parseAll("1.234,5", Locale.GERMANY);
        System.out.println("value=" + value.doubleValue());

        try {
            parseAll("1.234,5tail", Locale.GERMANY);
        } catch (IllegalArgumentException ex) {
            System.out.println("suffix=invalid");
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 LocaleNumberDemo.java
java LocaleNumberDemo
~~~

Kết quả với dữ liệu locale tiêu chuẩn:

~~~text
value=1234.5
suffix=invalid
~~~

Trong ví dụ, IllegalArgumentException được **hàm parseAll tự tạo** khi chuỗi không được tiêu thụ hết; không nên cho rằng NumberFormat.parse luôn ném NumberFormatException giống Integer.parseInt. Đối với tiền cần chính xác, có thể cấu hình DecimalFormat để nhận BigDecimal và kiểm tra scale/giới hạn.

## 13. Đặt catch đúng ranh giới, giữ nguyên nhân gốc

Đoạn mã sau có vấn đề về phạm vi bắt lỗi:

~~~java
try {
    int id = Integer.parseInt(input);
    process(id);
} catch (NumberFormatException ex) {
    showInvalidInput();
}
~~~

Đây là mã **minh họa không độc lập**, giả sử các hàm/biến đã định nghĩa. Nếu process(id) cũng ném NumberFormatException vì một lỗi nội bộ khác, nhánh catch có thể nhận nhầm thành lỗi dữ liệu đầu vào.

Nên tách vùng try chỉ quanh phép chuyển:

~~~java
final int id;
try {
    id = Integer.parseInt(input);
} catch (NumberFormatException ex) {
    throw new InputValidationException(
            "ID must be a decimal integer", ex);
}
process(id);
~~~

Đoạn sau giả định có **InputValidationException(String, Throwable)**. Nó giữ NumberFormatException gốc làm cause của lỗi ứng dụng và không nuốt các ngoại lệ khác phát sinh sau bước parse.

Nguyên tắc: **bắt ngoại lệ ở nơi có đủ ngữ cảnh**, nhưng đừng bao trùm quá nhiều logic không liên quan. Thông báo đưa ra cho người dùng nên xác định trường nhập và định dạng yêu cầu, không chỉ in nguyên stack trace hoặc dựa trên message có thể chứa dữ liệu nhạy cảm.

## 14. Parse thành công chưa chắc là hợp lệ

Một vài trường hợp cần xử lý riêng:

- **Integer.parseInt("-3")** thành công nhưng số lượng hàng hóa có thể phải không âm.
- **Integer.parseInt("0007")** trả 7 nhưng mã định danh có thể cần giữ đủ bốn ký tự.
- **Double.parseDouble("NaN")** thành công nhưng giá/điểm số thường phải hữu hạn.
- **new BigDecimal("1.234")** thành công nhưng đơn vị tiền có thể giới hạn hai chữ số sau dấu thập phân.
- **Integer.parseInt("+10")** chấp nhận dấu cộng nhưng hợp đồng giao thức có thể cấm.
- **Integer.parseInt** có thể chấp nhận chữ số Unicode trong khi một hệ thống khác chỉ hỗ trợ ASCII.
- Một chuỗi rất dài có thể gây chi phí parse quá lớn. Dữ liệu không tin cậy cần hạn mức độ dài và kích thước phù hợp.

Luồng xác thực có thể là: **kiểm tra hiện diện → chuẩn hóa được phép → grammar → parse → kiểm tra miền và ý nghĩa nghiệp vụ → tạo phản hồi**. Không nhất thiết dùng regex cho mọi trường; regex phức tạp còn có thể làm code khó bảo trì.

## 15. Lỗi thường gặp và cách tránh

| Sai lầm | Sự thật / cách sửa |
| --- | --- |
| Nghĩ NumberFormatException là checked exception | Nó là RuntimeException; compiler không buộc throws |
| Nghĩ parseInt tự trim | Không; cần chuẩn hóa đầu/cuối nếu hợp đồng cho phép |
| Nhầm numeric literal 1_000 và chuỗi "1_000" | Dấu gạch dưới trong chuỗi bị parseInt từ chối |
| Dùng parseInt("0xFF") để đọc hex có prefix | Dùng parseInt("FF",16) hoặc decode theo hợp đồng |
| Dùng decode cho decimal có số 0 đầu | Decode có thể hiểu octal |
| Cho rằng parseInt overflow thì wrap | ParseInt ném NumberFormatException |
| Bắt NumberFormatException cho Math.toIntExact | Math.toIntExact ném ArithmeticException |
| Xem parseDouble("NaN") là lỗi cú pháp | Nó trả NaN; phải tự kiểm tra hữu hạn |
| Dùng double cho tiền cần chính xác | Dùng BigDecimal từ chuỗi |
| Bỏ kiểm tra đầu vào null/rỗng | Hãy báo lỗi trường bắt buộc một cách rõ ràng |
| Xem NumberFormat.parse luôn đọc toàn chuỗi | Kiểm tra ParsePosition |
| Bắt Exception quá rộng rồi trả 0 | Có thể che bug nội bộ và làm dữ liệu giả thành hợp lệ |
| Chỉ test chuỗi đẹp | Test overflow, rỗng, dấu, locale, Unicode, NaN và domain |
| Ghi nguyên ex.getMessage() vào log công khai | Có thể lộ dữ liệu đầu vào không mong muốn |

## 16. Checklist code review và kiểm thử

1. Đây là giá trị số thật hay một ID dạng ký tự?
2. Kiểu đích int, long, BigInteger, double hay BigDecimal có đúng miền và độ chính xác?
3. Đầu vào null, rỗng, chỉ khoảng trắng được xử lý thế nào?
4. Cho phép dấu cộng/trừ, số 0 đầu, khoảng trắng, chữ số Unicode và dấu phân nhóm không?
5. Có chọn đúng cơ số và tránh decode khi không có cú pháp prefix không?
6. Khi số quá lớn, ứng dụng báo lỗi ổn định hay lỡ làm mất thông tin?
7. Số thực có kiểm tra Double.isFinite và miền nghiệp vụ không?
8. Dữ liệu locale được parse toàn chuỗi chưa?
9. Trường có giới hạn số ký tự và độ dài giá trị không?
10. Catch có chỉ bao phép parse, thay vì cả bước xử lý có thể lỗi nội bộ không?
11. Người dùng nhận tên trường và cách sửa, còn logger giữ cause khi cần không?
12. Với batch, chính sách dừng hay tiếp tục có phù hợp yêu cầu nguyên tử của giao dịch không?

## 17. Câu hỏi tự kiểm tra

**1.** NumberFormatException là checked hay unchecked? **Unchecked**, vì là subclass của RuntimeException.

**2.** Integer.parseInt(" 12 ") trả gì? Ném NumberFormatException; khoảng trắng không được tự bỏ.

**3.** Integer.parseInt("2147483648") có wrap thành âm không? Không; ném NumberFormatException vì vượt miền int.

**4.** Integer.parseInt("010") khác Integer.decode("010") thế nào? Kết quả 10 và 8.

**5.** Double.parseDouble("NaN") ném lỗi không? Không; trả NaN. Cần Double.isFinite khi domain yêu cầu số hữu hạn.

**6.** Khi nào chọn BigDecimal(String)? Khi cần giữ decimal chính xác từ chuỗi, đặc biệt trong tính toán tiền/giá trị có yêu cầu độ chính xác.

**7.** Math.toIntExact(Long.parseLong("2147483648")) ném loại gì? ArithmeticException ở bước narrowing.

**8.** Một tuổi 200 parse thành công có nghĩa hợp lệ chưa? Chưa; vi phạm miền tuổi trong ví dụ.

**9.** NumberFormat.parse có thể bỏ sót hậu tố sai không? Có thể; kiểm tra ParsePosition khi cần parse toàn chuỗi.

**10.** Vì sao không nên catch NumberFormatException quanh cả parse và process(id)? Vì có thể gán nhầm NumberFormatException từ process thành lỗi đầu vào.

**11.** Một dòng CSV lỗi có bắt buộc dừng mọi dòng còn lại không? Không nhất thiết; phụ thuộc hợp đồng và tính nguyên tử nghiệp vụ.

**12.** Vì sao không nên chỉ ghi ex.getMessage() ra UI? Vì thông điệp có thể thiếu ngữ cảnh hoặc chứa dữ liệu nhập nhạy cảm; hãy tạo thông báo lỗi theo trường và mã ứng dụng.

## Tài liệu tham khảo chuẩn

- [Java SE 21 API — NumberFormatException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/NumberFormatException.html).
- [Java SE 21 API — Integer.parseInt, Integer.valueOf, Integer.decode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Integer.html).
- [Java SE 21 API — Long.parseLong](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Long.html).
- [Java SE 21 API — Double.parseDouble và Double.isFinite](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html).
- [Java SE 21 API — BigInteger(String)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigInteger.html).
- [Java SE 21 API — BigDecimal(String)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html).
- [Java SE 21 API — NumberFormat](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/NumberFormat.html).
- [JLS Java SE 21 §11.1.1 — Checked và unchecked exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.1.1).

**Ghi nhớ:** NumberFormatException báo không thể chuyển một chuỗi thành số theo cú pháp hoặc miền của API. Chọn parser đúng ý nghĩa, chuẩn hóa có chủ đích, kiểm tra overflow, tính hữu hạn và quy tắc nghiệp vụ, rồi xử lý lỗi sát ranh giới input thay vì để dữ liệu sai lan qua nhiều tầng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-runtime-exceptions-005">ArithmeticException với phép toán số nguyên và các phép toán exact</a>

<details>
<summary>Xem chi tiết</summary>

# ArithmeticException với phép toán số nguyên và các phép toán exact trong Java

`ArithmeticException` là một **unchecked exception** thuộc nhánh `RuntimeException`. Trong Java Core, nó đặc biệt quan trọng ở hai nhóm tình huống: **chia/lấy dư cho số nguyên với số chia bằng 0**, và **những phép toán yêu cầu kết quả chính xác nhưng vượt phạm vi của kiểu số nguyên**, chẳng hạn `Math.addExact`, `Math.multiplyExact` hay `Math.toIntExact`.

Điều dễ nhầm là **không phải mọi lỗi số học hay mọi trường hợp tràn số đều tự tạo `ArithmeticException`**. Các phép `+`, `-`, `*` thông thường trên `int` và `long` có thể tràn và cho kết quả quấn vòng (*wraparound*) mà không ném lỗi. Ngược lại, họ hàm `Math.*Exact` chủ động phát hiện tràn số và ném `ArithmeticException`. Phép chia số thực `double` cho `0.0` lại có hành vi khác phép chia số nguyên cho `0`.

Bài học thuộc Java Core – Exception, ngữ cảnh **Ngoại lệ runtime**, và tập trung vào ngữ nghĩa phép toán, dấu của thương/số dư, tràn số nguyên, `Math.*Exact`, các ngoại lệ số học liên quan và cách xử lý an toàn trong Java 17+.

## 1. Vị trí của `ArithmeticException` trong hệ thống exception

Phân cấp:

~~~text
java.lang.Object
    |
    +-- java.lang.Throwable
          |
          +-- java.lang.Exception
                |
                +-- java.lang.RuntimeException
                      |
                      +-- java.lang.ArithmeticException
~~~

Vì là subtype của `RuntimeException`:

- Java **không bắt buộc** phương thức khai báo `throws ArithmeticException`.
- Caller **không bị buộc** phải viết `catch (ArithmeticException e)`.
- Ngoại lệ vẫn có thể lan truyền và làm tác vụ thất bại nếu không được xử lý.
- Một `catch (ArithmeticException)` nhận nó; một `catch (RuntimeException)` hoặc `catch (Exception)` cũng nhận nếu có vị trí phù hợp.
- Nếu bắt nhiều nhánh, nên đặt handler **hẹp hơn trước** handler superclass để tránh handler không thể đạt tới.

Ví dụ:

~~~java
static int divide(int a, int b) {
    return a / b;
}
~~~

Phương thức trên biên dịch dù không khai báo `throws`. Nếu `b == 0`, thao tác `a / b` với `int` sẽ ném `ArithmeticException` khi thực thi.

Không dùng `Error` để mô tả phép chia cho 0 trong ứng dụng. `ArithmeticException` là một lỗi runtime thông thường trong ngữ nghĩa của phép toán Java, không phải bằng chứng JVM đã bị hỏng.

## 2. Phép chia nguyên `/` và phép lấy dư `%` khi số chia bằng 0

Với các toán hạng có kiểu số nguyên, cả **chia `/`** và **lấy dư `%`** đều không xác định khi mẫu số bằng `0`. Java phản ánh điều đó bằng `ArithmeticException`.

~~~java
int a = 10;
int zero = 0;

System.out.println(a / zero); // Ném ArithmeticException.
~~~

Đây là đoạn **cố ý gây lỗi runtime**, không phải ví dụ cần chạy nguyên văn mà không có handler.

Tương tự:

~~~java
int remainder = 10 % 0; // ArithmeticException khi thực thi.
~~~

Với `long`:

~~~java
long value = 10L;
long divisor = 0L;

long result = value / divisor; // ArithmeticException.
~~~

Với `byte` và `short`, Java thực hiện **binary numeric promotion** nên nhiều biểu thức được tính như phép toán `int`. Quy tắc chia/lấy dư cho 0 của phép toán nguyên vẫn áp dụng.

Ví dụ:

~~~java
byte left = 7;
byte right = 0;
int result = left / right; // ArithmeticException.
~~~

**Đừng nhầm:** `%` là phép lấy dư, không phải phép chia trả về thương. Dù kết quả cần lấy là số dư, mẫu số bằng 0 vẫn gây ngoại lệ.

### Điều gì được kiểm tra tại runtime?

Bản chất phép `/` hoặc `%` dùng toán hạng nguyên: nếu số chia bằng 0, phép toán không tạo một giá trị nguyên hợp lệ. Khi thao tác ấy được thực hiện, Java ném `ArithmeticException`.

Không nên xây kiểm thử phụ thuộc vào nguyên văn `getMessage()` như `"/ by zero"`: các biểu diễn thông điệp và môi trường có thể khác. Hãy kiểm tra **kiểu ngoại lệ**, điều kiện gây lỗi và dữ liệu cần thiết thay vì parse chuỗi.

## 3. Phép chia nguyên làm tròn về 0, không làm tròn xuống

Java chia hai số nguyên bằng cách **bỏ phần phân số và làm tròn về 0** (*truncate toward zero*):

~~~java
System.out.println(7 / 3);     // 2
System.out.println(-7 / 3);    // -2
System.out.println(7 / -3);    // -2
System.out.println(-7 / -3);   // 2
~~~

Ví dụ `-7 / 3` là `-2` chứ không phải `-3`. Điều này khác phép `Math.floorDiv(-7, 3)`, vốn trả `-3`.

Bảng đối chiếu:

| Biểu thức | Kết quả |
| --- | ---: |
| `7 / 3` | `2` |
| `-7 / 3` | `-2` |
| `7 / -3` | `-2` |
| `-7 / -3` | `2` |
| `Math.floorDiv(-7, 3)` | `-3` |
| `Math.floorDiv(7, -3)` | `-3` |

Không được dùng trực giác “chia số âm luôn làm tròn xuống” khi làm việc với toán tử `/` của số nguyên Java.

### Toán tử `%` có dấu của số bị chia

Trong phép dư nguyên Java, kết quả (nếu khác 0) có cùng dấu với **số bị chia**:

~~~java
System.out.println(7 % 3);     // 1
System.out.println(-7 % 3);    // -1
System.out.println(7 % -3);    // 1
System.out.println(-7 % -3);   // -1
~~~

Đây không phải phép modulo luôn cho số không âm. Nếu cần phép chia dạng *floor division* và số dư theo cùng quy ước, dùng `Math.floorDiv` và `Math.floorMod` khi phù hợp.

Với `a` và `b != 0`, quan hệ của phép chia/dư nguyên thông thường về mặt toán học là:

~~~text
a = (a / b) * b + (a % b)
~~~

Tuy nhiên khi biểu diễn bằng kiểu nguyên cố định, cần thận trọng với tràn số ở những biểu thức trung gian và **trường hợp đặc biệt MIN_VALUE / -1** được nêu ở phần sau.

## 4. `Math.floorDiv` và `Math.floorMod` khác `/` và `%` ở chỗ nào?

Java có các phương thức trong `Math`:

~~~java
Math.floorDiv(int x, int y)
Math.floorDiv(long x, long y)
Math.floorMod(int x, int y)
Math.floorMod(long x, long y)
~~~

Ngoài ra còn có overload phù hợp cho toán hạng `long` và `int` ở một số API. Mấu chốt:

- `/` làm tròn thương **về 0**.
- `floorDiv` chọn thương theo phép **floor** — hướng âm vô cực.
- `%` đi cùng phép chia làm tròn về 0.
- `floorMod` là số dư đi cùng `floorDiv`, nên khi khác 0, dấu thường theo **số chia**.

~~~java
System.out.println(-7 / 3);                 // -2
System.out.println(-7 % 3);                 // -1
System.out.println(Math.floorDiv(-7, 3));   // -3
System.out.println(Math.floorMod(-7, 3));   // 2
~~~

Bảng:

| Số bị chia | Số chia | `/` | `%` | `floorDiv` | `floorMod` |
| ---: | ---: | ---: | ---: | ---: | ---: |
| `7` | `3` | `2` | `1` | `2` | `1` |
| `-7` | `3` | `-2` | `-1` | `-3` | `2` |
| `7` | `-3` | `-2` | `1` | `-3` | `-2` |
| `-7` | `-3` | `2` | `-1` | `2` | `-1` |

**Quan trọng:** `Math.floorDiv(x, 0)` và `Math.floorMod(x, 0)` với số nguyên **vẫn ném `ArithmeticException`**. Chúng đổi quy tắc làm tròn/số dư chứ không làm phép chia cho 0 trở nên hợp lệ.

Cũng đừng nhầm `floorDiv` với “chia có kiểm tra tràn số”. Nó không phải hàm `Exact` để bảo đảm phát hiện mọi tràn số.

## 5. Trường hợp đặc biệt: `MIN_VALUE / -1` không ném `ArithmeticException`

Đây là một góc cạnh rất quan trọng của số nguyên có dấu.

`int` dùng 32 bit, có miền:

~~~text
Integer.MIN_VALUE = -2147483648
Integer.MAX_VALUE =  2147483647
~~~

Giá trị toán học của:

~~~text
-2147483648 / -1 = 2147483648
~~~

lớn hơn `Integer.MAX_VALUE`. **Nhưng toán tử chia nguyên Java không ném `ArithmeticException` trong trường hợp đặc biệt này.** Kết quả là `Integer.MIN_VALUE`.

~~~java
int result = Integer.MIN_VALUE / -1;
System.out.println(result);
// -2147483648
~~~

Điều tương tự đúng với `long`:

~~~java
long result = Long.MIN_VALUE / -1L;
System.out.println(result);
// -9223372036854775808
~~~

Đây là **hành vi được ngôn ngữ xác định**, không phải hành vi ngẫu nhiên của phần cứng.

### Tại sao người học hay đoán sai?

Họ suy luận rằng “phép chia cho ra giá trị ngoài miền int thì phải ném ArithmeticException”. Điều này **không đúng với toán tử `/` thông thường**: chỉ số chia bằng 0 gây ngoại lệ trực tiếp cho phép chia nguyên; riêng `MIN_VALUE / -1` có kết quả tràn được định nghĩa.

Với `Integer.MIN_VALUE % -1`, số dư là `0`.

~~~java
System.out.println(Integer.MIN_VALUE % -1); // 0
~~~

Nếu ứng dụng yêu cầu phát hiện chính xác mọi trường hợp overflow, **không thể chỉ dựa vào `ArithmeticException` của toán tử `/`**. Cần kiểm tra trường hợp đặc biệt trước phép chia:

~~~java
static int divideSafely(int a, int b) {
    if (b == 0) {
        throw new ArithmeticException("division by zero");
    }
    if (a == Integer.MIN_VALUE && b == -1) {
        throw new ArithmeticException("integer division overflow");
    }
    return a / b;
}
~~~

Đây là phương thức Java hợp lệ. Nó đưa ra một **hợp đồng ứng dụng có kiểm tra overflow** rõ ràng hơn toán tử `/` mặc định. Với `long`, cần thay bằng `Long.MIN_VALUE` và `-1L`.

Không nhầm phương thức ứng dụng này với một API `Math.divideExact` của Java 17: trong bài này không giả định JDK 17 cung cấp một overload như vậy. Hãy dùng kiểm tra đã nêu nếu cần đảm bảo trên Java 17.

## 6. Phép `+`, `-` và `*` trên `int`, `long` không tự báo overflow

Mặc định, phép toán số nguyên cố định của Java **không ném `ArithmeticException` vì tràn số** khi cộng, trừ hay nhân.

~~~java
int a = Integer.MAX_VALUE;
System.out.println(a + 1);
// -2147483648
~~~

~~~java
int b = Integer.MIN_VALUE;
System.out.println(b - 1);
// 2147483647
~~~

~~~java
int c = 50_000 * 50_000;
System.out.println(c);
// -1794967296
~~~

Giá trị toán học của `50_000 × 50_000` là `2_500_000_000`, lớn hơn `Integer.MAX_VALUE`. Với biểu diễn 32-bit, kết quả quấn vòng thành `-1794967296`.

Đối với `long`:

~~~java
long value = Long.MAX_VALUE;
System.out.println(value + 1L);
// -9223372036854775808
~~~

Những kết quả này có thể gây lỗi nghiệp vụ rất khó phát hiện nếu dùng cho tiền, bộ đếm, thời hạn, dung lượng hoặc tính số phần tử.

**Đừng cho rằng Java tự bảo vệ bằng `ArithmeticException` khi một phép toán nguyên thông thường vượt miền kiểu.**

## 7. `Math.addExact`: cộng có kiểm tra overflow

`Math.addExact` có overload cho `int` và `long`:

~~~java
int x = Math.addExact(20, 22); // 42
~~~

Nếu kết quả toán học không nằm trong miền của kiểu trả về, nó ném `ArithmeticException`:

~~~java
int x = Math.addExact(Integer.MAX_VALUE, 1);
// Ném ArithmeticException.
~~~

Với `long`:

~~~java
long x = Math.addExact(Long.MAX_VALUE, 1L);
// Ném ArithmeticException.
~~~

So sánh:

| Phép toán | Khi không overflow | Khi overflow |
| --- | --- | --- |
| `a + b` | Trả tổng | Kết quả wraparound, không tự ném lỗi |
| `Math.addExact(a, b)` | Trả tổng | Ném `ArithmeticException` |

`addExact` thích hợp khi **số đếm không được phép quấn vòng**, ví dụ tổng số phần tử xử lý, số byte có kiểm soát hoặc các chỉ số có bất biến mạnh.

Với tiền tệ, chỉ dùng `Math.addExact` sau khi bạn đã xác định rõ **đơn vị lưu trữ**, giới hạn miền, tỷ giá, làm tròn và yêu cầu kế toán. `addExact` chỉ kiểm tra overflow của kiểu nguyên, không tự giải quyết precision/scale của tiền.

## 8. `Math.subtractExact`: trừ có kiểm tra overflow

~~~java
int remaining = Math.subtractExact(100, 35); // 65
~~~

Overflow:

~~~java
int value = Math.subtractExact(Integer.MIN_VALUE, 1);
// ArithmeticException.
~~~

So sánh:

~~~java
int wrapped = Integer.MIN_VALUE - 1;
System.out.println(wrapped);
// 2147483647
~~~

`Math.subtractExact` có cả overload `long` tương ứng.

**Cần chú ý dấu:** một phép trừ từ số âm nhỏ nhất trừ tiếp một số dương có thể vượt đáy miền kiểu. Các lỗi này thường không lộ ra qua kiểm tra `result < 0` đơn giản vì kết quả đã wrap sang số dương.

## 9. `Math.multiplyExact`: nhân có kiểm tra overflow

~~~java
int area = Math.multiplyExact(200, 300);
// 60000
~~~

Overflow:

~~~java
int huge = Math.multiplyExact(50_000, 50_000);
// ArithmeticException.
~~~

Với `long`:

~~~java
long tooLarge = Math.multiplyExact(Long.MAX_VALUE, 2L);
// ArithmeticException.
~~~

Đây là một công cụ hữu ích trong phép tính kích thước:

~~~java
static int requiredBytes(int elements, int bytesPerElement) {
    if (elements < 0 || bytesPerElement < 0) {
        throw new IllegalArgumentException("negative size");
    }
    return Math.multiplyExact(elements, bytesPerElement);
}
~~~

Phương thức này kiểm tra đầu vào không âm rồi dùng phép nhân exact để không âm thầm cho ra một kích thước sai. Lưu ý: `requiredBytes` vẫn chỉ mô tả phép nhân hai số nguyên; việc cấp phát bộ nhớ cần thêm kiểm tra thực tế về giới hạn mảng, bộ nhớ khả dụng và những quy tắc khác của API.

### Nhân trước khi ép sang long là lỗi phổ biến

Sai:

~~~java
int a = 50_000;
int b = 50_000;
long product = a * b;  // Overflow int trước, rồi mới widening.
~~~

Kết quả `product` là `-1794967296L` chứ không phải `2_500_000_000L`.

Nếu muốn tính trong miền `long`:

~~~java
long product = (long) a * b;
// 2500000000
~~~

Hoặc:

~~~java
long product = Math.multiplyExact((long) a, (long) b);
// 2500000000
~~~

Nếu phép toán cuối phải nằm trong `int`, dùng `Math.multiplyExact(a, b)` để **báo lỗi overflow**, không âm thầm nâng kiểu rồi cắt bớt.

## 10. `Math.incrementExact` và `Math.decrementExact`

Với phép tăng/giảm đúng một đơn vị:

~~~java
int next = Math.incrementExact(9);      // 10
int previous = Math.decrementExact(9);  // 8
~~~

Ở biên:

~~~java
Math.incrementExact(Integer.MAX_VALUE);
// ArithmeticException.

Math.decrementExact(Integer.MIN_VALUE);
// ArithmeticException.
~~~

Cả hai có overload cho `long`.

Ngược lại:

~~~java
int x = Integer.MAX_VALUE;
x++;  // Giá trị quấn vòng thành Integer.MIN_VALUE.
~~~

Đừng viết `x++` ở một bộ đếm có yêu cầu **không được phép overflow** rồi mong tự gặp `ArithmeticException`. Dùng `Math.incrementExact` và quyết định chính sách khi đạt giới hạn.

### Ví dụ một bộ đếm có hợp đồng

~~~java
static int countOneMore(int current) {
    if (current < 0) {
        throw new IllegalArgumentException("negative count");
    }
    return Math.incrementExact(current);
}
~~~

Phương thức này phân biệt hai kiểu vấn đề:

- Giá trị hiện tại âm vi phạm tiền điều kiện → `IllegalArgumentException`.
- Giá trị đã chạm `Integer.MAX_VALUE` → `ArithmeticException` từ phép tăng exact.

Trong ứng dụng, có thể bọc `ArithmeticException` thành một loại lỗi miền nếu caller cần thông điệp/ngữ cảnh riêng. Không đánh đồng nó với “mọi lỗi đếm đều do dữ liệu không hợp lệ”.

## 11. `Math.negateExact` và `Math.absExact`

Thông thường:

~~~java
int x = -12;
System.out.println(-x);             // 12
System.out.println(Math.abs(x));    // 12
~~~

Nhưng số nguyên có dấu có **một giá trị âm nhiều hơn số giá trị dương**. Không có số `int` dương tương ứng với độ lớn của `Integer.MIN_VALUE`.

Vì vậy:

~~~java
int a = -Integer.MIN_VALUE;
System.out.println(a);
// -2147483648
~~~

Và:

~~~java
System.out.println(Math.abs(Integer.MIN_VALUE));
// -2147483648
~~~

Đây là hành vi mặc định: `-MIN_VALUE` và `Math.abs(MIN_VALUE)` với số nguyên có thể wrap, **không tự ném ngoại lệ**.

Muốn kiểm tra:

~~~java
Math.negateExact(Integer.MIN_VALUE);
// ArithmeticException.
~~~

Từ Java 17, `Math.absExact` có thể dùng tương tự:

~~~java
Math.absExact(Integer.MIN_VALUE);
// ArithmeticException.
~~~

Với `long`, `Long.MIN_VALUE` là trường hợp biên tương ứng.

### Vì sao `Math.abs(value)` có thể nguy hiểm?

Một thuật toán tính chỉ số mảng hoặc hash bucket bằng:

~~~java
int index = Math.abs(hash) % capacity;
~~~

có thể trả kết quả âm nếu `hash == Integer.MIN_VALUE`, dù `capacity > 0`. Với bài toán chọn bucket có kích thước dương, có thể cân nhắc:

~~~java
int index = Math.floorMod(hash, capacity);
~~~

Khi `capacity > 0`, `floorMod` trả số dư trong miền từ `0` đến `capacity - 1`. Nếu `capacity == 0`, nó vẫn ném `ArithmeticException`; vì vậy cần kiểm tra bất biến `capacity > 0` trước.

## 12. `Math.toIntExact`: thu hẹp `long` thành `int` có kiểm tra

Ép kiểu `long` sang `int` thông thường **không báo lỗi khi mất bit cao**:

~~~java
long value = (long) Integer.MAX_VALUE + 1L;
int narrowed = (int) value;
System.out.println(narrowed);
// -2147483648
~~~

`Math.toIntExact(long)` kiểm tra xem giá trị `long` có thể biểu diễn dưới dạng `int` hay không.

~~~java
int ok = Math.toIntExact(42L); // 42
~~~

~~~java
int failed = Math.toIntExact(
        (long) Integer.MAX_VALUE + 1L);
// ArithmeticException.
~~~

Cách này đặc biệt có ích khi một API mới sử dụng `long`, còn hệ thống cũ nhận `int`; nếu không dùng exact conversion, việc cắt mất bit có thể dẫn tới chỉ số âm hoặc kích thước sai.

**Lưu ý:** `Math.toIntExact` không kiểm tra những điều kiện ứng dụng như “kết quả phải không âm” hay “không vượt kích thước mảng”; nó chỉ đảm bảo nằm trong miền `int`.

## 13. Tổng hợp phép số nguyên nào ném `ArithmeticException`?

| Biểu thức / API | Khi chia cho 0 | Khi overflow |
| --- | --- | --- |
| `int/long /` | Ném `ArithmeticException` | Riêng `MIN_VALUE / -1` không ném, trả MIN_VALUE |
| `int/long %` | Ném `ArithmeticException` | `MIN_VALUE % -1` trả 0 |
| `int/long +`, `-`, `*` | Không áp dụng | Wrap, không tự ném |
| `++`, `--` | Không áp dụng | Wrap, không tự ném |
| Unary `-` | Không áp dụng | `-MIN_VALUE` wrap |
| `Math.floorDiv`/`floorMod` | Ném `ArithmeticException` | Không bảo đảm kiểm tra tràn |
| `Math.addExact` | Không áp dụng | Ném `ArithmeticException` |
| `Math.subtractExact` | Không áp dụng | Ném `ArithmeticException` |
| `Math.multiplyExact` | Không áp dụng | Ném `ArithmeticException` |
| `Math.incrementExact`/`decrementExact` | Không áp dụng | Ném `ArithmeticException` |
| `Math.negateExact`/`absExact` | Không áp dụng | Ném `ArithmeticException` với MIN_VALUE |
| `Math.toIntExact` | Không áp dụng | Ném khi `long` ngoài miền `int` |

Không nên hiểu “exact” là mọi thuật toán bất kỳ đều có phiên bản mang tên `Exact` trong JDK 17. Các API trên có chức năng cụ thể; hãy kiểm tra tài liệu của JDK mục tiêu trước khi dùng một tên hàm chỉ vì có hậu tố giống nhau.

## 14. Số thực `float` và `double` chia cho 0 không giống số nguyên

`ArithmeticException` do mẫu số bằng `0` gắn với phép chia và phép `%` **kiểu số nguyên**. Với `float`/`double`, Java sử dụng các giá trị đặc biệt theo ngữ nghĩa IEEE 754, không tự ném lỗi chỉ vì chia cho `0.0`:

~~~java
System.out.println(1.0 / 0.0);                // Infinity
System.out.println(-1.0 / 0.0);               // -Infinity
System.out.println(0.0 / 0.0);                // NaN
System.out.println(Double.isNaN(1.0 % 0.0)); // true
~~~

Kiểu toán hạng quan trọng: `5 / 2` trả `2`, còn `5.0 / 2` trả `2.5`. `Double.isFinite(value)` giúp xác định kết quả hữu hạn trước khi đưa vào nghiệp vụ yêu cầu số thực hợp lệ.

**Không ép sang `double` chỉ để tránh ngoại lệ chia nguyên cho 0**: việc đó có thể âm thầm chuyển lỗi thành `Infinity`/`NaN` và làm sai tính toán phía sau. Bài về *phân biệt chia số thực cho 0 với chia số nguyên cho 0* phân tích sâu numeric promotion, dấu `-0.0`, phép `%` và kiểm thử các giá trị đặc biệt; trọng tâm bài hiện tại vẫn là phép số nguyên và `Math.*Exact`.

## 15. `BigInteger` và `BigDecimal` có liên quan gì?

`ArithmeticException` không chỉ do toán tử nguyên hoặc `Math.*Exact`. Một số API số học khác cũng sử dụng nó khi phép tính không hợp lệ hoặc không thể biểu diễn theo yêu cầu.

### `BigInteger`: không overflow theo miền int/long, vẫn không chia được cho 0

~~~java
java.math.BigInteger a =
        new java.math.BigInteger("999999999999999999999");

java.math.BigInteger b = a.multiply(a);
System.out.println(b.signum() > 0);
// true
~~~

`BigInteger` có độ chính xác nguyên theo nhu cầu thay vì bị giới hạn 32/64 bit, trong phạm vi tài nguyên bộ nhớ và giới hạn thực thi. Nhưng:

~~~java
java.math.BigInteger.TEN.divide(java.math.BigInteger.ZERO);
// ArithmeticException.
~~~

Không phải vì `BigInteger` lớn nên mọi phép toán đều hợp lệ.

### `BigDecimal`: phép chia chính xác có thể không có biểu diễn hữu hạn

~~~java
java.math.BigDecimal result =
        java.math.BigDecimal.ONE.divide(
                new java.math.BigDecimal("2"));

System.out.println(result); // 0.5
~~~

Nhưng:

~~~java
java.math.BigDecimal.ONE.divide(
        new java.math.BigDecimal("3"));
// ArithmeticException nếu yêu cầu kết quả chính xác
// mà thương có biểu diễn thập phân vô hạn.
~~~

Đây là trường hợp khác hẳn tràn số nguyên: `1 / 3` có khai triển thập phân không kết thúc. Dạng `BigDecimal.divide(BigDecimal)` không được cung cấp chiến lược làm tròn, nên nó ném `ArithmeticException` thay vì âm thầm tạo một số xấp xỉ.

Nếu hợp đồng chấp nhận làm tròn:

~~~java
java.math.BigDecimal rounded =
        java.math.BigDecimal.ONE.divide(
                new java.math.BigDecimal("3"),
                2,
                java.math.RoundingMode.HALF_UP);

System.out.println(rounded);
// 0.33
~~~

Cần quy định scale và rounding mode theo nghiệp vụ; không chọn `HALF_UP` vô điều kiện cho mọi bài toán tiền tệ. Chia cho `BigDecimal.ZERO` vẫn không hợp lệ.

## 16. Chương trình Java 17+ chạy độc lập

Tệp **`ArithmeticExactDemo.java`** dưới đây cho phép thấy rõ:

1. Phép chia/dư nguyên và quy tắc dấu.
2. Trường hợp `MIN_VALUE / -1` không ném lỗi.
3. Overflow mặc định của `+` và `*`.
4. Phép chia cho 0 ném `ArithmeticException`.
5. Các `Math.*Exact` ném ngoại lệ khi vượt miền.
6. Ép kiểu thu hẹp khác `Math.toIntExact`.
7. Số thực và BigDecimal có hành vi riêng.

Chương trình bắt **chỉ `ArithmeticException`** trong các ca cố ý gây lỗi, không dùng `catch (Throwable)`.

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class ArithmeticExactDemo {

    public static void main(String[] args) {
        System.out.println("== integer division ==");
        System.out.println("7 / 3=" + (7 / 3));
        System.out.println("-7 / 3=" + (-7 / 3));
        System.out.println("-7 % 3=" + (-7 % 3));
        System.out.println("floorDiv=" + Math.floorDiv(-7, 3));
        System.out.println("floorMod=" + Math.floorMod(-7, 3));
        System.out.println("int MIN / -1="
                + (Integer.MIN_VALUE / -1));
        System.out.println("long MIN / -1="
                + (Long.MIN_VALUE / -1L));

        System.out.println("== ordinary overflow ==");
        System.out.println("add wraps="
                + (Integer.MAX_VALUE + 1));
        System.out.println("multiply wraps="
                + (50_000 * 50_000));
        System.out.println("abs MIN="
                + Math.abs(Integer.MIN_VALUE));

        System.out.println("== division by zero ==");
        attempt("int / zero", () -> {
            int zero = 0;
            System.out.println(10 / zero);
        });
        attempt("int % zero", () -> {
            int zero = 0;
            System.out.println(10 % zero);
        });
        attempt("long / zero", () -> {
            long zero = 0L;
            System.out.println(10L / zero);
        });
        attempt("floorDiv zero", () -> Math.floorDiv(7, 0));

        System.out.println("== exact operations ==");
        System.out.println("addExact valid="
                + Math.addExact(20, 22));
        attempt("addExact", () ->
                Math.addExact(Integer.MAX_VALUE, 1));
        attempt("subtractExact", () ->
                Math.subtractExact(Integer.MIN_VALUE, 1));
        attempt("multiplyExact", () ->
                Math.multiplyExact(50_000, 50_000));
        attempt("long multiplyExact", () ->
                Math.multiplyExact(Long.MAX_VALUE, 2L));
        attempt("incrementExact", () ->
                Math.incrementExact(Integer.MAX_VALUE));
        attempt("decrementExact", () ->
                Math.decrementExact(Integer.MIN_VALUE));
        attempt("negateExact", () ->
                Math.negateExact(Integer.MIN_VALUE));
        attempt("absExact", () ->
                Math.absExact(Integer.MIN_VALUE));

        System.out.println("== long to int ==");
        long outsideInt = (long) Integer.MAX_VALUE + 1L;
        System.out.println("cast wraps=" + (int) outsideInt);
        System.out.println("toIntExact valid="
                + Math.toIntExact(42L));
        attempt("toIntExact", () ->
                Math.toIntExact(outsideInt));

        System.out.println("== floating point ==");
        System.out.println("double / zero=" + (1.0 / 0.0));
        System.out.println("double 0/0 NaN="
                + Double.isNaN(0.0 / 0.0));
        System.out.println("double % zero NaN="
                + Double.isNaN(1.0 % 0.0));

        System.out.println("== big numbers ==");
        attempt("BigInteger divide zero", () ->
                BigInteger.TEN.divide(BigInteger.ZERO));
        attempt("BigDecimal exact 1/3", () ->
                BigDecimal.ONE.divide(new BigDecimal("3")));
        System.out.println("BigDecimal rounded="
                + BigDecimal.ONE.divide(
                        new BigDecimal("3"),
                        2,
                        RoundingMode.HALF_UP));
    }

    static void attempt(String label, Runnable operation) {
        try {
            operation.run();
            System.out.println(label + " => OK");
        } catch (ArithmeticException e) {
            System.out.println(
                    label + " => ArithmeticException");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ArithmeticExactDemo.java
java ArithmeticExactDemo
~~~

**Stdout xác định cho Java 17+:**

~~~text
== integer division ==
7 / 3=2
-7 / 3=-2
-7 % 3=-1
floorDiv=-3
floorMod=2
int MIN / -1=-2147483648
long MIN / -1=-9223372036854775808
== ordinary overflow ==
add wraps=-2147483648
multiply wraps=-1794967296
abs MIN=-2147483648
== division by zero ==
int / zero => ArithmeticException
int % zero => ArithmeticException
long / zero => ArithmeticException
floorDiv zero => ArithmeticException
== exact operations ==
addExact valid=42
addExact => ArithmeticException
subtractExact => ArithmeticException
multiplyExact => ArithmeticException
long multiplyExact => ArithmeticException
incrementExact => ArithmeticException
decrementExact => ArithmeticException
negateExact => ArithmeticException
absExact => ArithmeticException
== long to int ==
cast wraps=-2147483648
toIntExact valid=42
toIntExact => ArithmeticException
== floating point ==
double / zero=Infinity
double 0/0 NaN=true
double % zero NaN=true
== big numbers ==
BigInteger divide zero => ArithmeticException
BigDecimal exact 1/3 => ArithmeticException
BigDecimal rounded=0.33
~~~

### Vì sao helper `attempt` chỉ bắt `ArithmeticException`?

`attempt(String, Runnable)` dùng một callback `Runnable`, và `Runnable.run()` không khai báo checked exception. Những thao tác trong các lambda trên là những thao tác có thể ném unchecked `ArithmeticException` nên chúng vẫn hợp lệ.

Helper in **tên loại ngoại lệ** thay vì `e.getMessage()`, nhằm giữ ví dụ độc lập với khác biệt câu chữ trong thông điệp runtime.

Không nên sửa thành `catch (Exception e)` rồi xem mọi lỗi được bắt là lỗi số học: một `NullPointerException`, `IllegalArgumentException` hoặc một lỗi của code callback khác cũng có thể nằm dưới `Exception` nhưng cần chính sách khác.

### Vì sao `Math.absExact` hoạt động trong Java 17?

`Math.absExact` được cung cấp trong những JDK hiện đại, bao gồm Java 17, và cho phép phát hiện trường hợp trị tuyệt đối của `Integer.MIN_VALUE`/`Long.MIN_VALUE` không thể biểu diễn bằng kiểu tương ứng.

Khác với `Math.abs`, nó ném `ArithmeticException` cho trường hợp biên đó. Do vậy ví dụ có cả `Math.abs(Integer.MIN_VALUE)` và `Math.absExact(Integer.MIN_VALUE)` để làm rõ sự khác biệt.

### Vì sao `BigDecimal.ONE.divide(new BigDecimal("3"))` có lỗi?

API này đòi kết quả chính xác, nhưng `1/3` không có biểu diễn thập phân hữu hạn. Cần chọn phép chia có scale/rounding mode hoặc `MathContext` phù hợp nếu ứng dụng chấp nhận kết quả làm tròn. Lỗi này **không phải overflow của kiểu `int`**.

## 17. Cách kiểm tra trước khi chia số nguyên

Nếu số chia đến từ input bên ngoài, có thể kiểm tra sớm:

~~~java
static int safeQuotient(int dividend, int divisor) {
    if (divisor == 0) {
        throw new IllegalArgumentException(
                "divisor must not be zero");
    }
    return dividend / divisor;
}
~~~

Hàm này chuyển từ một lỗi phép toán `ArithmeticException` thành một lỗi **tiền điều kiện `IllegalArgumentException`** có ngữ nghĩa rõ hơn khi mẫu số là đối số API.

Tuy nhiên `safeQuotient(Integer.MIN_VALUE, -1)` vẫn trả `Integer.MIN_VALUE`. Nếu cần **chính xác cả về tràn chia**, bổ sung kiểm tra trường hợp đó (như `divideSafely` ở phần 5).

### Nên precondition hay catch exception?

Không có một quy tắc duy nhất, nhưng thường:

- Nếu `divisor == 0` là **dữ liệu không hợp lệ có thể biết từ trước**, kiểm tra trước giúp báo lỗi đúng hợp đồng.
- Nếu bạn đang xây API thực hiện phép toán và chủ động muốn giữ ngữ nghĩa Java, có thể để `ArithmeticException` truyền ra.
- Nếu cần fallback nghiệp vụ, chỉ thực hiện khi fallback **được thiết kế và có ý nghĩa**.
- Nếu tính toán liên quan giao dịch hoặc cập nhật trạng thái, tránh vừa thay đổi dữ liệu vừa chỉ bắt lỗi cuối cùng mà không rollback/khôi phục đúng cách.

Không bắt lỗi rồi trả `0` tùy tiện, vì `0` có thể là một kết quả hợp lệ và việc che lỗi có thể dẫn đến mất tính chính xác của dữ liệu.

## 18. Thiết kế API phép toán exact với lỗi rõ ràng

Một helper kiểm tra số lượng trước khi cấp phát:

~~~java
static int checkedArrayLength(
        int records, int elementsPerRecord) {
    if (records < 0 || elementsPerRecord < 0) {
        throw new IllegalArgumentException("negative quantity");
    }
    int total = Math.multiplyExact(records, elementsPerRecord);
    if (total > 1_000_000) {
        throw new IllegalArgumentException(
                "too many elements");
    }
    return total;
}
~~~

Đây là phương thức Java hợp lệ. Nó có ba trường hợp:

1. Giá trị âm → lỗi tiền điều kiện `IllegalArgumentException`.
2. Phép nhân overflow → `ArithmeticException` từ `Math.multiplyExact`.
3. Kết quả đúng về số học nhưng vượt quota ứng dụng → `IllegalArgumentException` với message về giới hạn.

Đây là ví dụ quan trọng về phân biệt **độ đúng phép toán** với **hợp lệ theo quy tắc nghiệp vụ**. `Math.multiplyExact` không thể tự quyết định một request có vượt hạn mức tài nguyên của chương trình hay không.

### Bọc lỗi ở ranh giới nghiệp vụ khi có ích

~~~java
static int calculateRequiredSlots(
        int groups, int slotsPerGroup) {
    try {
        return Math.multiplyExact(groups, slotsPerGroup);
    } catch (ArithmeticException cause) {
        throw new IllegalArgumentException(
                "requested slot count is too large", cause);
    }
}
~~~

Mã này chuyển overflow thành một lỗi đầu vào tại ranh giới cụ thể và **giữ cause** để điều tra. Chỉ chọn cách bọc này khi overflow thực sự có ý nghĩa “request quá lớn” trong hợp đồng, không phải lỗi lập trình mà ứng dụng muốn phát hiện bằng một handler khác.

Nếu caller cần nhận trực tiếp `ArithmeticException`, cho nó lan truyền cũng có thể là lựa chọn phù hợp.

## 19. Những lỗi phổ biến khi dùng exact arithmetic

### 19.1. Nghĩ rằng `int` overflow luôn ném `ArithmeticException`

Sai. `Integer.MAX_VALUE + 1`, `Integer.MIN_VALUE - 1` và `50_000 * 50_000` mặc định wrap.

### 19.2. Nghĩ `MIN_VALUE / -1` ném overflow

Sai trong toán tử chia nguyên Java. Đây là trường hợp đặc biệt được xác định và trả MIN_VALUE. Nếu cần báo lỗi, tự kiểm tra điều kiện này trong Java 17.

### 19.3. Thấy `Math.floorDiv` rồi cho rằng nó phát hiện mọi overflow

Sai. `floorDiv` phục vụ quy tắc làm tròn xuống, không phải một họ API exact overflow checking.

### 19.4. Dùng `Math.abs` để đảm bảo số không âm

Sai khi input là `Integer.MIN_VALUE` hoặc `Long.MIN_VALUE`. Dùng `Math.absExact` khi cần phát hiện trường hợp không thể biểu diễn.

### 19.5. Nghĩ `Math.toIntExact` giống cast `(int)`

Sai. Cast có thể mất bit và trả giá trị khác hẳn; `toIntExact` ném `ArithmeticException` khi ngoài miền.

### 19.6. Ép sang long sau khi đã nhân int

Sai. `long total = a * b;` thực hiện nhân `int` trước nếu cả hai toán hạng là `int`. Ép một toán hạng **trước** phép nhân hoặc dùng `Math.multiplyExact` với kiểu mong muốn.

### 19.7. Dùng số thực để né chia cho 0

`double` chia cho `0.0` có thể cho `Infinity`/`NaN` chứ không ném. Điều đó không bảo đảm kết quả nghiệp vụ hợp lệ.

### 19.8. Dùng `%` như modulo luôn không âm

Sai với số bị chia âm. Dùng `Math.floorMod` với số chia dương nếu cần số dư không âm theo quy ước floor.

### 19.9. Cho rằng mọi `ArithmeticException` đều do chia cho 0

Sai. Exact arithmetic, thu hẹp kiểu, `BigInteger` và `BigDecimal` có thể gây `ArithmeticException` vì những điều kiện khác.

### 19.10. Bắt lỗi rồi trả 0 trong mọi tình huống

Có thể che lỗi dữ liệu, biến một phép tính không xác định thành kết quả dường như hợp lệ. Fallback phải thuộc hợp đồng.

### 19.11. Dùng message để xác định kiểu lỗi

Không nên viết `if (e.getMessage().equals("/ by zero"))`. Message không phải mã lỗi ổn định và có thể null. Nếu cần phân loại chính xác, kiểm tra precondition hoặc dùng loại lỗi miền/mã lỗi có cấu trúc.

### 19.12. Bắt `Exception` thay vì `ArithmeticException` mà không có lý do

Handler rộng có thể nuốt các lỗi không liên quan và làm việc chẩn đoán kém chính xác.

### 19.13. Quên kiểm tra số âm trước khi tính kích thước

`Math.multiplyExact(-2, 10)` trả `-20` vì phép toán **không overflow**. Nó không tự xác định số lượng âm là không hợp lệ. Phải kiểm tra invariant ứng dụng riêng.

### 19.14. Quên giới hạn tài nguyên dù phép nhân exact thành công

Một kết quả có thể nằm trong `int` nhưng vẫn quá lớn để cấp phát mảng hoặc xử lý an toàn. Exact chỉ bảo đảm miền số học, không bảo đảm bộ nhớ.

### 19.15. Nghĩ `BigInteger` hoặc `BigDecimal` không bao giờ ném lỗi số học

Sai. Chia cho 0 và phép chia `BigDecimal` không có kết quả thập phân hữu hạn khi thiếu rounding policy đều có thể ném `ArithmeticException`.

## 20. Các trường hợp biên cần có trong bài kiểm thử

Một bộ kiểm thử tốt nên có:

| Trường hợp | Điều cần xác nhận |
| --- | --- |
| `10 / 2` | Trả `5` |
| `10 / 0` | `ArithmeticException` |
| `10 % 0` | `ArithmeticException` |
| `10L / 0L` | `ArithmeticException` |
| `Integer.MIN_VALUE / -1` | Không throw, trả MIN_VALUE |
| `-7 / 3` | `-2`, truncate toward zero |
| `-7 % 3` | `-1` |
| `Math.floorDiv(-7,3)` | `-3` |
| `Math.floorMod(-7,3)` | `2` |
| `Integer.MAX_VALUE + 1` | Wrap thành MIN_VALUE |
| `Math.addExact(MAX,1)` | `ArithmeticException` |
| `Math.subtractExact(MIN,1)` | `ArithmeticException` |
| `Math.multiplyExact(50000,50000)` | `ArithmeticException` |
| `Math.negateExact(MIN)` | `ArithmeticException` |
| `Math.absExact(MIN)` | `ArithmeticException` |
| `Math.toIntExact(MAX+1L)` | `ArithmeticException` |
| `1.0 / 0.0` | `Infinity`, không ném ArithmeticException |
| `0.0 / 0.0` | `NaN` |
| `BigInteger.TEN.divide(ZERO)` | `ArithmeticException` |
| `BigDecimal.ONE.divide(3)` | `ArithmeticException` nếu cần kết quả chính xác vô hạn |
| `BigDecimal.ONE.divide(3,2,HALF_UP)` | `0.33` |

Khi viết unit test, kiểm tra exception **theo loại và điều kiện**, không bắt buộc cố định message của JDK. Chẳng hạn với JUnit 5:

~~~java
assertThrows(ArithmeticException.class,
        () -> Math.addExact(Integer.MAX_VALUE, 1));
~~~

Đây là **mảnh minh họa phụ thuộc thư viện JUnit 5**, không nằm trong chương trình `ArithmeticExactDemo.java` độc lập; chỉ sử dụng nếu dự án đã thiết lập JUnit.

## 21. Câu hỏi tự kiểm tra

1. `ArithmeticException` là checked hay unchecked? Nó kế thừa lớp nào?
2. Tại sao `int a = 10 / 0` có thể gây `ArithmeticException`?
3. `10 % 0` có hợp lệ không?
4. Phép chia `long` cho `0L` có giống chia `int` cho 0 không?
5. `7 / 3` và `-7 / 3` lần lượt trả gì?
6. Vì sao `-7 % 3` trả `-1`?
7. `Math.floorDiv(-7,3)` khác `-7/3` như thế nào?
8. `Math.floorMod(-7,3)` trả gì?
9. `Integer.MIN_VALUE / -1` ném lỗi hay trả một giá trị?
10. `Long.MIN_VALUE / -1L` có hành vi biên tương tự không?
11. Phép `Integer.MAX_VALUE + 1` mặc định có ném lỗi không?
12. `Math.addExact(Integer.MAX_VALUE,1)` có hành vi gì?
13. Khi nào `Math.subtractExact` ném lỗi?
14. Vì sao `50_000 * 50_000` cho giá trị `int` âm?
15. `Math.multiplyExact(50_000,50_000)` có trả cùng giá trị âm đó không?
16. `Math.incrementExact(Integer.MAX_VALUE)` làm gì?
17. Vì sao `-Integer.MIN_VALUE` không trả số nguyên dương theo toán học?
18. `Math.absExact(Integer.MIN_VALUE)` làm gì?
19. Nếu `long x = Integer.MAX_VALUE + 1L`, ép `(int)x` có báo lỗi không?
20. `Math.toIntExact(x)` khác cast ở điểm nào?
21. `1.0 / 0.0` có ném `ArithmeticException` không? Kết quả là gì?
22. `0.0 / 0.0` trả loại giá trị số thực đặc biệt nào?
23. `BigInteger` có còn ném `ArithmeticException` không? Nêu ví dụ.
24. Vì sao `BigDecimal.ONE.divide(new BigDecimal("3"))` có thể ném lỗi?
25. Khi thiết kế API chia số nguyên, nên kiểm tra `divisor == 0` trước khi tính không?
26. `Math.multiplyExact(-2,10)` có tự từ chối số lượng âm không?
27. Vì sao phép nhân exact thành công chưa bảo đảm cấp phát mảng an toàn?
28. Có nên mặc định `catch (ArithmeticException)` và trả `0` không?
29. `Math.floorDiv(x,0)` có khác toán tử nguyên `x/0` về lỗi không?
30. Khi kiểm thử, vì sao không nên phụ thuộc tuyệt đối vào `getMessage()` của `ArithmeticException`?

### Gợi ý đáp án

1. Unchecked, kế thừa `RuntimeException`.
2. Số chia bằng 0 trong phép chia nguyên là điều kiện không hợp lệ.
3. Không; ném `ArithmeticException`.
4. Có, với toán hạng nguyên.
5. `2` và `-2`.
6. Số dư theo toán tử `%` của Java mang dấu số bị chia.
7. `floorDiv` làm tròn xuống `-3`, còn `/` làm tròn về 0 thành `-2`.
8. `2`.
9. Trả `Integer.MIN_VALUE`, không ném.
10. Có; trả `Long.MIN_VALUE`.
11. Không; nó wrap.
12. Ném `ArithmeticException` vì overflow.
13. Khi hiệu vượt miền kiểu kết quả.
14. Tích toán học vượt `Integer.MAX_VALUE` nên wrap theo 32 bit.
15. Không; nó ném `ArithmeticException`.
16. Ném `ArithmeticException`.
17. Giá trị dương tương ứng vượt `Integer.MAX_VALUE`.
18. Ném `ArithmeticException`.
19. Không; cast có thể cắt bit cao.
20. `toIntExact` kiểm tra miền `int` và ném khi ngoài miền.
21. Không; kết quả `Infinity`.
22. `NaN`.
23. Có, ví dụ `BigInteger.TEN.divide(BigInteger.ZERO)`.
24. `1/3` không có biểu diễn thập phân hữu hạn khi yêu cầu phép chia chính xác không làm tròn.
25. Có thể, nếu muốn thông báo lỗi tiền điều kiện rõ ràng; còn hành vi phép toán Java tự ném nếu mẫu số 0.
26. Không; `-20` không overflow. Phải kiểm tra bất biến dữ liệu riêng.
27. Kết quả có thể vượt quota bộ nhớ hoặc ràng buộc API.
28. Không, chỉ khi fallback 0 được hợp đồng cho phép.
29. Cả hai ném `ArithmeticException` với số chia nguyên bằng 0.
30. Vì message không phải hợp đồng lỗi ổn định để xử lý máy; nên kiểm tra loại lỗi và điều kiện.

## 22. Ghi nhớ

**`ArithmeticException` trong Java Core chủ yếu xuất hiện khi chia/lấy dư số nguyên cho 0 và khi các API exact phát hiện phép toán không thể biểu diễn chính xác trong miền số nguyên.** Các toán tử `+`, `-`, `*`, `++`, `--` thông thường không tự báo overflow; `Integer.MIN_VALUE / -1` cũng không ném mà trả MIN_VALUE. Dùng `Math.addExact`, `subtractExact`, `multiplyExact`, `incrementExact`, `decrementExact`, `negateExact`, `absExact` và `toIntExact` khi cần phát hiện tràn số. Hiểu rõ khác biệt giữa `/`, `%`, `floorDiv` và `floorMod` với số âm, cùng hành vi `Infinity`/`NaN` của `float`/`double`. Khi thiết kế API, hãy kết hợp kiểm tra tiền điều kiện và bất biến nghiệp vụ; không bắt lỗi số học rồi trả một giá trị giả định nếu chưa có chính sách rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-runtime-exceptions-006">Phân biệt chia số thực cho 0 với chia số nguyên cho 0</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt chia số thực cho 0 với chia số nguyên cho 0 trong Java

## 1. Mục tiêu học tập

Trong Java, cùng là ký hiệu chia `/` và cùng nhìn thấy mẫu số bằng `0`, nhưng kết quả có thể **hoàn toàn khác nhau tùy kiểu toán hạng**:

- **Phép chia số nguyên** với số chia bằng 0 (như `int` hoặc `long`) sẽ **ném `ArithmeticException` tại runtime**.
- **Phép chia số thực dấu phẩy động** với `float` hoặc `double` không tự ném `ArithmeticException` chỉ vì số chia bằng 0. Theo ngữ nghĩa IEEE 754 được Java quy định, kết quả có thể là **`Infinity`, `-Infinity` hoặc `NaN`**.
- **`%` (chia lấy dư)** cũng có sự khác biệt: số nguyên lấy dư với 0 ném `ArithmeticException`; phép lấy dư dấu phẩy động với 0 sinh `NaN`.
- Một phép toán có cả số nguyên và số thực có thể được **numeric promotion** sang `float`/`double`, nên không thể đoán chỉ từ kiểu của tử số.
- Một đối tượng kiểu `BigDecimal` không dùng quy tắc chia của `float`/`double` nguyên thủy: chia cho `BigDecimal.ZERO` ném `ArithmeticException`.

Bài học thuộc chủ đề **Java core — exception / Ngoại lệ runtime**, nên ngoài việc dự đoán kết quả, bạn cần biết *khi nào có ngoại lệ để bắt*, *khi nào không có ngoại lệ nhưng vẫn nhận kết quả không hữu hạn*, và *cách kiểm tra đúng trước khi dùng kết quả*.

Sau bài này, bạn sẽ phân biệt được kiểu toán hạng, dấu của zero, quy tắc lan truyền `NaN`, phép lấy dư, kiểm tra bằng `Double.isFinite`/`isNaN`, và các lỗi thường gặp khi viết `try/catch` cho phép chia.

Ví dụ dùng Java 17+; mỗi chương trình hoàn chỉnh có tên tệp, lệnh `javac`/`java` và `stdout` xác định. Những đoạn mã chỉ minh họa được ghi rõ.

## 2. Quy tắc nền tảng trong một bảng

| Biểu thức | Loại phép toán | Kết quả khi chạy |
| --- | --- | --- |
| `7 / 0` | Chia số nguyên `int` | Ném `ArithmeticException` |
| `7L / 0L` | Chia số nguyên `long` | Ném `ArithmeticException` |
| `7 % 0` | Chia lấy dư `int` | Ném `ArithmeticException` |
| `7.0 / 0.0` | Chia `double` | `Infinity` |
| `-7.0 / 0.0` | Chia `double` | `-Infinity` |
| `7.0 / -0.0` | Chia `double` | `-Infinity` |
| `0.0 / 0.0` | Chia `double` | `NaN` |
| `0.0f / 0.0f` | Chia `float` | `NaN` |
| `7.0 % 0.0` | Chia lấy dư `double` | `NaN` |
| `7 / 0.0` | Chia `double` sau promotion | `Infinity` |
| `7.0 / 0` | Chia `double` sau promotion | `Infinity` |
| `7 / 2` | Chia số nguyên | `3`, phần thập phân bị bỏ |
| `7 / 2.0` | Chia `double` | `3.5` |

**Điểm chính:** Java quyết định ngữ nghĩa phép chia theo **kiểu toán hạng sau binary numeric promotion**. Không phải “mẫu số viết 0 nên chắc chắn có `ArithmeticException`”.

Cụm “chia số thực” trong bài chỉ các toán hạng kiểu nguyên thủy **`float` và `double`** (floating-point), không ngụ ý tất cả lớp biểu diễn số thập phân như `BigDecimal` đều có cùng hành vi.

## 3. Chia nguyên cho 0: `ArithmeticException`

`int` và `long` có phép chia số nguyên. Theo JLS, nếu **toán hạng phải sau chuyển kiểu có giá trị nguyên bằng 0**, phép `/` ném `ArithmeticException`. Cùng nguyên tắc áp dụng cho `%` nguyên.

Ví dụ:

```java
int numerator = 10;
int denominator = 0;
int value = numerator / denominator; // Ném ArithmeticException.
```

Đây là mảnh mã minh họa. `value` không nhận giá trị vì biểu thức ở bên phải không hoàn thành bình thường.

### Chương trình chạy được: các trường hợp chia nguyên

Tạo tệp **`IntegerZeroDivisionDemo.java`**:

```java
public class IntegerZeroDivisionDemo {
    static void divideInt(int numerator, int denominator) {
        try {
            System.out.println("int=" + (numerator / denominator));
        } catch (ArithmeticException failure) {
            System.out.println("int error=" + failure.getMessage());
        }
    }

    static void divideLong(long numerator, long denominator) {
        try {
            System.out.println("long=" + (numerator / denominator));
        } catch (ArithmeticException failure) {
            System.out.println("long error=" + failure.getMessage());
        }
    }

    static void remainderInt(int numerator, int denominator) {
        try {
            System.out.println("remainder=" + (numerator % denominator));
        } catch (ArithmeticException failure) {
            System.out.println("remainder error=" + failure.getMessage());
        }
    }

    public static void main(String[] args) {
        divideInt(10, 2);
        divideInt(10, 0);
        divideLong(10L, 0L);
        remainderInt(10, 0);
    }
}
```

Biên dịch và chạy:

```bash
javac IntegerZeroDivisionDemo.java
java IntegerZeroDivisionDemo
```

`stdout`:

```text
int=5
int error=/ by zero
long error=/ by zero
remainder error=/ by zero
```

**Giải thích:**

1. `10 / 2` cho kết quả `5`.
2. Khi gọi `divideInt(10, 0)`, phép chia số nguyên không có kết quả hợp lệ; `catch (ArithmeticException)` chạy.
3. `long` cũng chịu quy tắc chia nguyên cho 0, không có giá trị “infinity” dành cho số nguyên.
4. `10 % 0` cũng ném `ArithmeticException`. Nó **không** cho kết quả `0` hoặc `NaN` như toán tử `%` dấu phẩy động.
5. `failure.getMessage()` với phép chia nguyên cho 0 được JDK dùng trong ví dụ là `/ by zero`. Không xây logic nghiệp vụ dựa vào chuỗi message; hãy dựa vào kiểu exception và dữ liệu đầu vào.

### `ArithmeticException` là checked hay unchecked?

`ArithmeticException` kế thừa `RuntimeException`, nên là **unchecked exception**. Không bắt buộc khai báo `throws ArithmeticException` trong phương thức đang thực hiện phép chia nguyên:

```java
static int divide(int a, int b) {
    return a / b;
}
```

Đây là phương thức Java hợp lệ. Nếu `b == 0`, nó vẫn ném tại runtime. “Unchecked” **không** có nghĩa “không thể xảy ra”; nó chỉ nói compiler không yêu cầu `try/catch` hoặc `throws`.

### Tràn số nguyên khác với chia cho 0

Một trường hợp đặc biệt:

```java
int value = Integer.MIN_VALUE / -1;
System.out.println(value);
```

Đây là mảnh mã hợp lệ; kết quả là `-2147483648` do quy tắc tràn số của phép chia nguyên có trường hợp đặc biệt. Nó **không** ném `ArithmeticException` chỉ vì kết quả toán học vượt phạm vi `int`. Đừng đánh đồng tràn số nguyên với phép chia cho 0.

## 4. Chia `float`/`double` cho 0: kết quả đặc biệt, không ném tự động

Java dùng ngữ nghĩa dấu phẩy động tương thích IEEE 754 cho các kiểu nguyên thủy `float`/`double`. Với phép chia dấu phẩy động:

- Tử số **khác 0 và hữu hạn** chia cho zero tạo ra vô cực có dấu thích hợp.
- Zero chia cho zero tạo ra `NaN` (*Not a Number*).
- Các phép toán liên quan `NaN` hoặc vô cực còn tuân theo các quy tắc đặc biệt khác.
- Java **không ném ngoại lệ tại phép `/` dấu phẩy động** chỉ vì phát sinh vô cực hoặc `NaN`.

### Chương trình chạy được: các kết quả dấu phẩy động

Tạo tệp **`FloatingZeroDivisionDemo.java`**:

```java
public class FloatingZeroDivisionDemo {
    public static void main(String[] args) {
        double positive = 7.0 / 0.0;
        double negative = -7.0 / 0.0;
        double negativeZero = 7.0 / -0.0;
        double undefined = 0.0 / 0.0;
        float singlePrecision = 7.0f / 0.0f;

        System.out.println("positive=" + positive);
        System.out.println("negative=" + negative);
        System.out.println("negative zero=" + negativeZero);
        System.out.println("zero over zero=" + undefined);
        System.out.println("float=" + singlePrecision);

        System.out.println("isInfinite="
                + Double.isInfinite(positive));
        System.out.println("isNaN="
                + Double.isNaN(undefined));
    }
}
```

```bash
javac FloatingZeroDivisionDemo.java
java FloatingZeroDivisionDemo
```

`stdout`:

```text
positive=Infinity
negative=-Infinity
negative zero=-Infinity
zero over zero=NaN
float=Infinity
isInfinite=true
isNaN=true
```

Tất cả phép chia trong chương trình đều hoàn thành mà **không tự ném `ArithmeticException`**. Java tạo ra những giá trị đặc biệt của kiểu `double` hoặc `float`.

### Vì sao lại có `Infinity` và `NaN`?

Theo ngữ nghĩa floating-point:

- `7.0 / +0.0` → `+Infinity`.
- `-7.0 / +0.0` → `-Infinity`.
- `7.0 / -0.0` → `-Infinity`.
- `0.0 / 0.0` → `NaN`.
- `-0.0 / -0.0` cũng là `NaN`.

`Infinity` không phải kiểu dữ liệu mới: nó là **một giá trị đặc biệt của `double` hoặc `float`**. Tương tự, `NaN` không phải exception mà là một giá trị dấu phẩy động đặc biệt.

Có thể dùng `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY`, `Double.NaN` và các hằng tương ứng của `Float` để biểu diễn các giá trị này rõ ràng.

**Đừng tự dựa vào việc kiểm tra `result == Double.NaN`** để phát hiện NaN; phép so sánh bằng với NaN luôn trả `false`. Dùng `Double.isNaN(result)` hoặc `Float.isNaN(result)`.

## 5. Số 0 âm (`-0.0`) làm kết quả đổi dấu

Kiểu nguyên Java không có một giá trị `-0` tách biệt với `0`. Với số nguyên:

```java
int zero = -0;
System.out.println(zero == 0);
```

Đây là mảnh mã hợp lệ và in `true`.

Nhưng `float` và `double` có **+0.0** và **-0.0** phân biệt ở mức biểu diễn. Mặc dù `0.0 == -0.0` trả `true` trong phép so sánh số thông thường, dấu của zero ảnh hưởng đến phép chia.

### Chương trình chạy được: dấu của zero

Tạo tệp **`SignedZeroDemo.java`**:

```java
public class SignedZeroDemo {
    public static void main(String[] args) {
        double plusZero = 0.0;
        double minusZero = -0.0;

        System.out.println("zero equal="
                + (plusZero == minusZero));
        System.out.println("1/+0="
                + (1.0 / plusZero));
        System.out.println("1/-0="
                + (1.0 / minusZero));
        System.out.println("-1/-0="
                + (-1.0 / minusZero));
        System.out.println("Double.compare="
                + Double.compare(minusZero, plusZero));
    }
}
```

```bash
javac SignedZeroDemo.java
java SignedZeroDemo
```

`stdout`:

```text
zero equal=true
1/+0=Infinity
1/-0=-Infinity
-1/-0=Infinity
Double.compare=-1
```

- `==` coi hai zero bằng nhau trong phép so sánh dấu phẩy động.
- `1.0 / +0.0` và `1.0 / -0.0` có kết quả khác dấu.
- `Double.compare(-0.0, +0.0)` có quy tắc so sánh nhất quán cho các giá trị đặc biệt và trả số âm (cụ thể `-1` trong chương trình).
- Dấu zero có thể hình thành qua phép tính, underflow hoặc từ một literal. Không giả định mọi biểu thức có giá trị so sánh bằng 0 đều biểu diễn `+0.0`.

Nếu thuật toán không có nhu cầu phân biệt dấu zero, có thể kiểm tra mẫu số bằng `denominator == 0.0` để bắt cả `+0.0` lẫn `-0.0`. Nếu dấu zero có ý nghĩa vật lý hay số học, cần thiết kế chính sách khác.

## 6. Toán tử `/` quyết định theo **numeric promotion**, không chỉ theo literal

Một lỗi phổ biến là nhìn thấy tử số `int` và kết luận “đây là phép chia nguyên”. Java trước tiên thực hiện **binary numeric promotion** cho các toán hạng số học.

Tóm tắt hữu ích:

1. Nếu có toán hạng kiểu `double`, cả hai được dùng như `double`.
2. Nếu không có `double` nhưng có `float`, cả hai được dùng như `float`.
3. Nếu không có `double`/`float` nhưng có `long`, cả hai được dùng như `long`.
4. Trường hợp còn lại (các số nguyên nhỏ hơn như `byte`, `short`, `char`) dùng `int`.

Vì vậy:

```java
int a = 7;
int b = 0;

double first = a / b;       // VẪN là phép chia int -> ArithmeticException.
double second = a / 0.0;   // Phép chia double -> Infinity.
double third = (double) a / b; // Phép chia double -> Infinity.
```

Đây là **mảnh mã minh họa**. Phép gán kết quả vào biến `double` **không thể thay đổi cách biểu thức `a / b` đã được đánh giá**. Để buộc phép chia thực, ít nhất một toán hạng phải được chuyển sang `float`/`double` *trước phép chia*.

### Chương trình chạy được: kiểm chứng kiểu toán hạng

Tạo tệp **`NumericPromotionDemo.java`**:

```java
public class NumericPromotionDemo {
    public static void main(String[] args) {
        int seven = 7;
        int two = 2;
        int zero = 0;

        double assignedAfterIntegerDivide = seven / two;
        double floatingDivide = seven / 2.0;
        double castBeforeDivide = (double) seven / two;

        System.out.println("int into double="
                + assignedAfterIntegerDivide);
        System.out.println("with double="
                + floatingDivide);
        System.out.println("cast before="
                + castBeforeDivide);

        try {
            double invalid = seven / zero;
            System.out.println(invalid);
        } catch (ArithmeticException failure) {
            System.out.println("int denominator zero: exception");
        }

        System.out.println("double denominator zero="
                + (seven / 0.0));
        System.out.println("float denominator zero="
                + (seven / 0.0f));
    }
}
```

```bash
javac NumericPromotionDemo.java
java NumericPromotionDemo
```

`stdout`:

```text
int into double=3.0
with double=3.5
cast before=3.5
int denominator zero: exception
double denominator zero=Infinity
float denominator zero=Infinity
```

Phân tích hai dòng dễ nhầm:

- `double assignedAfterIntegerDivide = seven / two;`: vì cả hai toán hạng là `int` nên kết quả `3` trước, sau đó mới chuyển thành `3.0` để gán cho `double`.
- `double floatingDivide = seven / 2.0;`: `2.0` là `double`, nên `seven` được promotion; kết quả là `3.5`.

Tương tự, `(double) (seven / two)` cho `3.0` vì ép kiểu **sau phép chia nguyên**; `((double) seven) / two` mới là chia thực. Vị trí ép kiểu rất quan trọng.

### `byte`/`short`/`char` và `long`

- `byte`, `short`, `char` được promotion lên `int` khi dùng `/` hoặc `%` với nhau (trừ khi có kiểu rộng hơn), vì vậy chia cho zero vẫn ném.
- `long` chia cho `long` zero vẫn ném `ArithmeticException`.
- `long` chia cho `0.0` trở thành phép chia `double` và tạo infinity tùy dấu.
- Kiểu của literal quan trọng: `0` là `int`, `0L` là `long`, `0.0` mặc định là `double`, `0.0f` là `float`.

Không lẫn `0.0f` với `0`: giá trị toán học “zero” giống nhau nhưng **loại phép toán và khả năng phát sinh ngoại lệ** khác nhau.

## 7. Toán tử lấy dư `%`: cùng quy tắc phân biệt kiểu

`%` không chỉ áp dụng cho số nguyên. Java hỗ trợ cả phép lấy dư dấu phẩy động; nhưng hành vi với số chia bằng 0 vẫn chia làm hai nhóm.

- `int % 0`, `long % 0L` → ném `ArithmeticException`.
- `double % 0.0`, `float % 0.0f` → `NaN`.
- Với giá trị dấu phẩy động, toán tử `%` Java dùng quy tắc tính remainder riêng; nó **không đồng nhất** với hàm `Math.IEEEremainder`.

### Chương trình chạy được: `%` với zero

Tạo tệp **`RemainderZeroDemo.java`**:

```java
public class RemainderZeroDemo {
    public static void main(String[] args) {
        try {
            int result = 5 % 0;
            System.out.println(result);
        } catch (ArithmeticException failure) {
            System.out.println("integer remainder: exception");
        }

        double floatingRemainder = 5.0 % 0.0;
        float floatRemainder = 5.0f % 0.0f;

        System.out.println("double remainder="
                + floatingRemainder);
        System.out.println("float remainder="
                + floatRemainder);
        System.out.println("isNaN="
                + Double.isNaN(floatingRemainder));

        System.out.println("finite percent infinity="
                + (5.0 % Double.POSITIVE_INFINITY));
    }
}
```

```bash
javac RemainderZeroDemo.java
java RemainderZeroDemo
```

`stdout`:

```text
integer remainder: exception
double remainder=NaN
float remainder=NaN
isNaN=true
finite percent infinity=5.0
```

Một quy tắc khác: toán tử `%` dấu phẩy động với **số chia vô cực** và số bị chia hữu hạn trả về số bị chia đó; nhưng khi số chia bằng **zero**, nó trả `NaN`. Đừng áp máy móc kiến thức `%` nguyên sang kiểu `double`.

### `Math.IEEEremainder` khác `%`

Java có `Math.IEEEremainder(double f1, double f2)`. Nó dùng quy tắc chọn thương nguyên gần nhất theo IEEE (ties to even), trong khi `%` theo quy tắc remainder gắn với chia và truncation. Chúng có thể trả kết quả khác nhau với cùng hai số thông thường.

Ví dụ `5.0 % 3.0` là `2.0`, nhưng `Math.IEEEremainder(5.0, 3.0)` là `-1.0`. Cả hai không biến phép chia nguyên cho zero thành hợp lệ; đây là các phép toán dấu phẩy động khác nhau.

## 8. `NaN` không phải ngoại lệ, và so sánh với `NaN` dễ sai

`NaN` là một giá trị đặc biệt của `float`/`double` dùng cho những phép tính dấu phẩy động không có kết quả số thông thường, chẳng hạn `0.0 / 0.0`.

Các phép so sánh:

- `Double.NaN == Double.NaN` → `false`.
- `Double.NaN != Double.NaN` → `true`.
- `Double.NaN < 1.0`, `Double.NaN > 1.0` và `Double.NaN == 1.0` → đều `false`.
- `Double.isNaN(value)` là API đúng để phát hiện `NaN`.

### Chương trình chạy được: so sánh và API kiểm tra

Tạo tệp **`NaNInfinityChecksDemo.java`**:

```java
public class NaNInfinityChecksDemo {
    public static void main(String[] args) {
        double positiveInfinity = 1.0 / 0.0;
        double undefined = 0.0 / 0.0;
        double ordinary = 5.0 / 2.0;

        System.out.println("NaN == NaN: "
                + (undefined == undefined));
        System.out.println("NaN != NaN: "
                + (undefined != undefined));
        System.out.println("NaN > 1: " + (undefined > 1.0));

        System.out.println("isNaN=" + Double.isNaN(undefined));
        System.out.println("isInfinite="
                + Double.isInfinite(positiveInfinity));

        System.out.println("infinity finite="
                + Double.isFinite(positiveInfinity));
        System.out.println("NaN finite="
                + Double.isFinite(undefined));
        System.out.println("ordinary finite="
                + Double.isFinite(ordinary));
    }
}
```

```bash
javac NaNInfinityChecksDemo.java
java NaNInfinityChecksDemo
```

`stdout`:

```text
NaN == NaN: false
NaN != NaN: true
NaN > 1: false
isNaN=true
isInfinite=true
infinity finite=false
NaN finite=false
ordinary finite=true
```

`Double.isFinite(x)` chỉ trả `true` khi `x` **không** phải vô cực và **không** phải `NaN`. Đây là phép kiểm tra gọn khi ứng dụng chỉ chấp nhận kết quả số thực hữu hạn.

Các API tương ứng `Float.isNaN`, `Float.isInfinite` và `Float.isFinite` phù hợp với kiểu `float`.

### Tại sao không dùng `try/catch (ArithmeticException)` để phát hiện NaN?

Vì **không có `ArithmeticException` phát sinh từ phép `/` dấu phẩy động với zero**, nên handler đó không chạy. Phát hiện giá trị bất thường bằng API kiểm tra `NaN`/infinity và **quy định chính sách riêng** của ứng dụng, không dựa trên một ngoại lệ Java không tự ném.

## 9. Khi nào vô cực xuất hiện dù **không** chia cho 0?

Không nên kết luận “thấy `Infinity` có nghĩa chắc chắn mẫu số bằng 0”. Số thực có **giới hạn biểu diễn**; các phép tính có thể tràn phạm vi hữu hạn.

Ví dụ:

```java
double overflow = Double.MAX_VALUE * 2.0;
double veryLargeQuotient =
        Double.MAX_VALUE / Double.MIN_VALUE;

System.out.println(overflow);
System.out.println(veryLargeQuotient);
```

Đây là mảnh Java hợp lệ, in:

```text
Infinity
Infinity
```

`Double.MIN_VALUE` là **giá trị `double` dương nhỏ nhất khác 0** (subnormal), không phải số âm hữu hạn có độ lớn tuyệt đối lớn nhất. Vì thế mẫu số ở phép chia thứ hai **không bằng 0**, nhưng kết quả toán học quá lớn để biểu diễn bằng `double` hữu hạn.

Tương tự:

- `Math.sqrt(-1.0)` tạo `NaN` dù không hề dùng phép chia cho zero.
- Một số phép toán với vô cực có thể tạo `NaN`, ví dụ `Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY`.
- Tích `0.0 * Double.POSITIVE_INFINITY` là `NaN`.

Khi một kết quả số thực quan trọng cho nghiệp vụ, hãy kiểm tra **cả tính hợp lệ của đầu vào** và **kết quả cuối**. Chỉ kiểm tra mẫu số có bằng 0 hay không là chưa đủ để bảo đảm kết quả hữu hạn.

## 10. Kiểm tra mẫu số bằng `== 0.0` có đủ không?

Nếu mục tiêu là **chặn chính xác +0.0 và -0.0**, kiểm tra:

```java
if (denominator == 0.0) {
    throw new IllegalArgumentException("denominator must not be zero");
}
```

Đây là mảnh minh họa với biến `denominator` kiểu `double`. Vì `+0.0 == -0.0` là `true`, cả hai dấu đều được chặn.

Nhưng có các giới hạn:

- `denominator = Double.NaN`: biểu thức `denominator == 0.0` là `false`, nhưng phép chia thường tạo NaN.
- `denominator = Double.POSITIVE_INFINITY`: không bằng zero; có thể cho kết quả zero hữu hạn trong một số phép chia.
- `numerator` đã là `NaN` hoặc infinity: kết quả có thể không hữu hạn.
- `denominator` cực nhỏ nhưng **khác 0**: phép chia có thể overflow thành infinity.
- Kiểm tra `Math.abs(denominator) < epsilon` là **một chính sách ngưỡng số học**, không tương đương chính xác với “chia cho zero”. Ngưỡng phải dựa trên kích thước dữ liệu và yêu cầu ứng dụng.

Vì vậy, nếu bài toán yêu cầu **không bao giờ trả NaN hoặc infinity**, hãy kiểm tra `Double.isFinite(...)` của cả đầu vào và kết quả.

## 11. Tự chọn ném ngoại lệ cho phép chia dấu phẩy động khi nghiệp vụ cần

Java không tự ném với phép chia thực cho zero, nhưng **ứng dụng có thể cố ý ném** một ngoại lệ để biểu diễn hợp đồng của mình.

Ví dụ phương thức sau từ chối mọi đầu vào không hữu hạn, mọi mẫu số zero và mọi kết quả không hữu hạn.

Tạo tệp **`FiniteDivisionDemo.java`**:

```java
public class FiniteDivisionDemo {
    static double divideFinite(double numerator, double denominator) {
        if (!Double.isFinite(numerator)
                || !Double.isFinite(denominator)) {
            throw new IllegalArgumentException(
                    "inputs must be finite");
        }
        if (denominator == 0.0) {
            throw new ArithmeticException(
                    "floating denominator is zero");
        }

        double result = numerator / denominator;
        if (!Double.isFinite(result)) {
            throw new ArithmeticException(
                    "floating result is not finite");
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println("valid="
                + divideFinite(5.0, 2.0));

        try {
            divideFinite(1.0, 0.0);
        } catch (ArithmeticException failure) {
            System.out.println("zero="
                    + failure.getMessage());
        }

        try {
            divideFinite(Double.MAX_VALUE, Double.MIN_VALUE);
        } catch (ArithmeticException failure) {
            System.out.println("overflow="
                    + failure.getMessage());
        }

        try {
            divideFinite(Double.NaN, 2.0);
        } catch (IllegalArgumentException failure) {
            System.out.println("invalid="
                    + failure.getMessage());
        }
    }
}
```

```bash
javac FiniteDivisionDemo.java
java FiniteDivisionDemo
```

`stdout`:

```text
valid=2.5
zero=floating denominator is zero
overflow=floating result is not finite
invalid=inputs must be finite
```

**Điểm phân biệt bắt buộc:** `ArithmeticException` ở đây được ném **bởi mã kiểm tra do lập trình viên tự viết**. Nó không phải ngoại lệ mà toán tử `double / double` tự động tạo. Một thư viện khác có thể chọn `IllegalArgumentException`, đối tượng kết quả chuyên dụng, hoặc trả giá trị IEEE; đó là hợp đồng thiết kế API.

### Nên chọn kiểm tra kiểu nào?

| Yêu cầu | Kiểm tra phù hợp |
| --- | --- |
| Không cho mẫu số bằng zero, chấp nhận các kết quả IEEE khác | `denominator == 0.0` |
| Không cho đầu vào `NaN` hoặc infinity | `Double.isFinite(numerator)` và `Double.isFinite(denominator)` |
| Yêu cầu kết quả cuối phải hữu hạn | `Double.isFinite(result)` |
| Muốn phân biệt zero âm/dương | Kiểm tra dấu bằng `Math.copySign` hoặc raw bits khi thật sự cần |
| Muốn tính gần zero theo thang giá trị nghiệp vụ | Ngưỡng tương đối/tuyệt đối được thiết kế và kiểm thử |
| Tính tiền với độ chính xác thập phân và chính sách làm tròn | Cân nhắc `BigDecimal` với `MathContext`/`RoundingMode` thích hợp |

Việc chọn exception, thông điệp, kiểu giá trị trả về và ngưỡng không thể chỉ dựa vào toán tử. Nó là phần hợp đồng của API và yêu cầu chính xác số học.

## 12. `BigDecimal` và `BigInteger`: không áp quy tắc infinity của `double`

Người mới học dễ nghĩ “số thập phân” là một nhóm duy nhất. Thực tế **`BigDecimal` là class số học thập phân chính xác theo thang chia**, không phải kiểu dấu phẩy động nhị phân `double`.

Ví dụ:

```java
java.math.BigDecimal.ONE.divide(java.math.BigDecimal.ZERO);
```

Đây là mảnh Java; nó ném `ArithmeticException`. Nó **không** trả `BigDecimal` biểu diễn infinity, vì API `BigDecimal` không có cùng tập giá trị infinity/NaN như `double`.

Tương tự `BigInteger.ONE.divide(BigInteger.ZERO)` ném `ArithmeticException`.

### Chương trình chạy được: so sánh với `BigDecimal`

Tạo tệp **`BigDecimalDivisionDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class BigDecimalDivisionDemo {
    public static void main(String[] args) {
        try {
            System.out.println(
                    BigDecimal.ONE.divide(BigDecimal.ZERO));
        } catch (ArithmeticException failure) {
            System.out.println("BigDecimal divide zero: exception");
        }

        try {
            System.out.println(
                    BigInteger.ONE.divide(BigInteger.ZERO));
        } catch (ArithmeticException failure) {
            System.out.println("BigInteger divide zero: exception");
        }

        BigDecimal one = BigDecimal.ONE;
        BigDecimal three = new BigDecimal("3");

        System.out.println("rounded="
                + one.divide(three, 2, RoundingMode.HALF_UP));
        System.out.println("double="
                + (1.0 / 0.0));
    }
}
```

```bash
javac BigDecimalDivisionDemo.java
java BigDecimalDivisionDemo
```

`stdout`:

```text
BigDecimal divide zero: exception
BigInteger divide zero: exception
rounded=0.33
double=Infinity
```

**Lưu ý khác của `BigDecimal`:** `BigDecimal.ONE.divide(new BigDecimal("3"))` **cũng có thể ném `ArithmeticException`** nếu dùng overload yêu cầu biểu diễn thập phân chính xác mà không có kết quả hữu hạn. Khi cần chia `1/3`, phải chọn chính sách làm tròn như `RoundingMode` hoặc `MathContext`. Trường hợp này là **không thể biểu diễn chính xác bằng số chữ số thập phân hữu hạn**, khác hoàn toàn chia cho 0.

Không dùng `BigDecimal` với hy vọng nó “tự tạo NaN” khi chia cho 0. Hãy quyết định chính sách nghiệp vụ và bắt/kiểm tra lỗi đúng API.

## 13. `try/catch` đúng cách cho hai họ phép chia

Chia nguyên:

```java
static int integerDivide(int numerator, int denominator) {
    if (denominator == 0) {
        throw new IllegalArgumentException("denominator must not be zero");
    }
    return numerator / denominator;
}
```

Đây là phương thức hợp lệ. Bạn có thể chặn bằng guard trước hoặc để `ArithmeticException` từ phép chia phát sinh rồi xử lý ở ranh giới phù hợp. Dùng guard có thể tạo thông điệp miền cụ thể; không nhất thiết phải bắt mọi `ArithmeticException` vì nó cũng có thể xuất hiện từ thao tác khác.

Chia thực:

```java
static double floatingDivide(double numerator, double denominator) {
    double result = numerator / denominator;
    if (!Double.isFinite(result)) {
        throw new ArithmeticException("result is not finite");
    }
    return result;
}
```

Đây là một **chính sách ứng dụng** chọn ném khi kết quả không hữu hạn. Khác với `integerDivide`, bản thân biểu thức `numerator / denominator` không tạo `ArithmeticException` do mẫu số zero.

Nếu chỉ cần chuẩn IEEE để tính toán khoa học, có thể **cố ý cho phép** infinity/NaN và xử lý chúng ở tầng toán học. Trong hệ thống tính tiền, báo cáo số liệu hoặc validation đầu vào, thường cần từ chối giá trị không hữu hạn và có chiến lược làm tròn/chính xác riêng.

### Vì sao bắt `ArithmeticException` xung quanh `1.0 / 0.0` thường vô ích?

```java
try {
    double result = 1.0 / 0.0;
    System.out.println(result);
} catch (ArithmeticException failure) {
    System.out.println("caught");
}
```

Đây là mảnh hợp lệ. Nó in `Infinity`, **không in `caught`**. Catch được phép có mặt về cú pháp vì `ArithmeticException` là unchecked, nhưng điều đó không khiến `/` dấu phẩy động bắt đầu ném ngoại lệ.

Ngược lại, với `int numerator = 1; int denominator = 0;`, câu lệnh `numerator / denominator` sẽ đi vào nhánh catch nếu có.

## 14. `Double.NaN` trong điều kiện và vòng lặp

Một thuật toán dùng kiểm tra như:

```java
if (value > threshold) {
    // ...
}
```

có thể bỏ qua giá trị NaN không phải vì giá trị “an toàn”, mà bởi phép so sánh `>` với NaN trả `false`. Tương tự:

```java
if (value <= threshold) {
    // ...
}
```

cũng có thể trả `false` với NaN. Hai điều kiện không bao phủ tất cả các giá trị floating-point, dù theo suy nghĩ về số thực thông thường bạn có thể kỳ vọng một trong hai đúng.

Nếu đầu vào đến từ dữ liệu bên ngoài, cảm biến hoặc phép chia trước đó, hãy **xử lý NaN một cách tường minh** trước khi áp dụng ngưỡng nếu yêu cầu nghiệp vụ cần phân loại đủ mọi trạng thái.

Ví dụ mảnh mã:

```java
if (Double.isNaN(value)) {
    throw new IllegalArgumentException("measurement is NaN");
}
if (Double.isInfinite(value)) {
    throw new IllegalArgumentException("measurement is infinite");
}
```

Điều này đặc biệt quan trọng ở những hệ thống validation: nếu chỉ kiểm tra `value < min || value > max`, NaN có thể lọt qua bởi cả hai phép so sánh đều false. Dùng `Double.isFinite(value)` thường là một cổng kiểm tra phù hợp trước khi so sánh ngưỡng.

## 15. Sai lầm do ép kiểu và thứ tự tính toán

Một số tình huống cần phân biệt:

```java
int numerator = 1;
int denominator = 0;

// A: integer division trước, sau đó mới cast -> ném ArithmeticException.
double a = (double) (numerator / denominator);

// B: chuyển tử số sang double trước phép chia -> Infinity.
double b = (double) numerator / denominator;

// C: chuyển mẫu số sang double trước phép chia -> Infinity.
double c = numerator / (double) denominator;
```

Đây là **mảnh minh họa**: câu lệnh A ném và ngăn những câu lệnh tiếp theo chạy nếu không có handler. Dạng B/C chỉ có kết quả Infinity khi thực sự được chạy.

Nếu viết:

```java
int result = (int) (1.0 / 0.0);
System.out.println(result);
```

Đây là mảnh hợp lệ và in `2147483647` (`Integer.MAX_VALUE`) theo quy tắc **narrowing primitive conversion** từ `+Infinity` sang `int`. Nó **không** tự tạo `ArithmeticException`. Chuyển `NaN` sang `int` cho 0 theo quy tắc chuyển kiểu.

Điểm học tập: *một phép chia đã được thực hiện dưới kiểu `double` thì việc cast kết quả sang `int` về sau không biến nó thành phép chia nguyên*. Tuy vậy, narrowing có thể làm mất độ chính xác hoặc bão hòa về giới hạn kiểu nguyên, rất nguy hiểm nếu ứng dụng tưởng giá trị còn hợp lệ.

### Chương trình chạy được: cast sau khi chia thực

Tạo tệp **`FloatingCastDemo.java`**:

```java
public class FloatingCastDemo {
    public static void main(String[] args) {
        double positiveInfinity = 1.0 / 0.0;
        double negativeInfinity = -1.0 / 0.0;
        double notANumber = 0.0 / 0.0;

        System.out.println("double positive=" + positiveInfinity);
        System.out.println("to int positive=" + (int) positiveInfinity);
        System.out.println("to int negative=" + (int) negativeInfinity);
        System.out.println("to int NaN=" + (int) notANumber);
    }
}
```

```bash
javac FloatingCastDemo.java
java FloatingCastDemo
```

`stdout`:

```text
double positive=Infinity
to int positive=2147483647
to int negative=-2147483648
to int NaN=0
```

Những kết quả cast này **không chứng minh phép chia ban đầu hợp lệ về nghiệp vụ**. Nếu cần chặn infinity/NaN, nên kiểm tra trước khi convert sang số nguyên để tránh che dấu tình trạng không hữu hạn.

## 16. Lỗi thường gặp và cách sửa

**“Bất cứ phép chia cho 0 nào trong Java cũng ném `ArithmeticException`.”** Sai. Chỉ có quy tắc ném của phép chia/lấy dư **số nguyên** và của một số API riêng (ví dụ BigDecimal); toán tử `/` floating-point dùng infinity/NaN.

**“`1.0 / 0.0` luôn ném vì mẫu số 0.”** Sai. Nó cho `Infinity`.

**“`0.0 / 0.0` là 0.”** Sai. Nó cho `NaN`.

**“`0 / 0` cũng trả NaN.”** Sai. Hai toán hạng là `int` nên ném `ArithmeticException`.

**“`1 / 0.0` có tử số là int nên ném.”** Sai. Có `double` ở toán hạng phải nên promotion sang `double`; kết quả `Infinity`.

**“Gán `double x = intA / intB` sẽ tự chia số thực.”** Sai. Phép chia `int` chạy **trước** khi chuyển kiểu để gán.

**“Số 0 âm và 0 dương là một trong mọi phép toán double.”** Sai. `==` xem chúng bằng nhau, nhưng `1.0 / -0.0` và `1.0 / +0.0` khác dấu.

**“`try/catch (ArithmeticException)` sẽ bắt kết quả NaN.”** Sai. NaN là giá trị, không phải throwable.

**“`Double.NaN == Double.NaN` là true vì hai vế giống nhau.”** Sai. Equality với NaN theo số học floating-point trả false.

**“Có thể kiểm tra NaN bằng `value == Double.NaN`.”** Sai. Dùng `Double.isNaN(value)`.

**“`Infinity` chỉ xuất hiện khi chia cho zero.”** Sai. Floating-point overflow cũng tạo infinity.

**“Kiểm tra `denominator != 0.0` đủ bảo đảm kết quả hữu hạn.”** Sai. NaN, infinity và overflow có thể xảy ra từ những đầu vào khác.

**“`BigDecimal` là kiểu số thực nên chia BigDecimal.ZERO sẽ cho Infinity.”** Sai. `BigDecimal.divide` ném `ArithmeticException` khi chia cho zero.

**“`BigDecimal.divide` chỉ ném khi chia cho zero.”** Sai. Một phép chia không kết thúc thập phân mà không có rounding phù hợp cũng có thể ném.

**“`int` chia cho 0 là checked exception nên phải ghi `throws ArithmeticException`.”** Sai. `ArithmeticException` là unchecked.

**“`5.0 % 0.0` ném `ArithmeticException` giống `5 % 0`.”** Sai. Dạng dấu phẩy động cho NaN.

**“`(double) (1 / 0)` tránh được lỗi chia nguyên.”** Sai. Phép chia nguyên thực hiện trước cast.

**“Ép Infinity sang int sẽ ném ngoại lệ chia cho 0.”** Sai. Chuyển kiểu thực sang nguyên có quy tắc riêng, có thể cho giá trị biên hoặc 0.

**“`Double.MIN_VALUE` là số âm nhỏ nhất.”** Sai. Nó là số `double` dương nhỏ nhất khác 0; phân biệt với `-Double.MAX_VALUE`.

## 17. Bài tập tự kiểm tra

### Bài 1: Xác định loại phép chia

Dự đoán:

```java
int a = 6;
int b = 0;
double x = a / b;
```

**Đáp án:** ném `ArithmeticException`, vì `a / b` là phép chia `int`. Khai báo `x` là `double` không đổi loại phép chia đã xảy ra.

### Bài 2: Thêm literal `.0`

```java
int a = 6;
double x = a / 0.0;
```

**Đáp án:** `Infinity` do promotion sang `double`.

### Bài 3: Zero chia zero

```java
double x = 0.0 / -0.0;
```

**Đáp án:** `NaN`, không ném ngoại lệ.

### Bài 4: Dấu zero

```java
double x = -6.0 / -0.0;
```

**Đáp án:** `Infinity`.

### Bài 5: Integer remainder

```java
int remainder = 6 % 0;
```

**Đáp án:** `ArithmeticException`.

### Bài 6: Floating remainder

```java
double remainder = 6.0 % 0.0;
```

**Đáp án:** `NaN`.

### Bài 7: Cast trước hay sau?

```java
int a = 1;
int b = 2;
System.out.println((double) (a / b));
System.out.println((double) a / b);
```

**Đáp án:** dòng đầu in `0.0`, dòng sau in `0.5`.

### Bài 8: Kiểm tra NaN

```java
double x = 0.0 / 0.0;
System.out.println(x == x);
System.out.println(Double.isNaN(x));
```

**Đáp án:** `false` rồi `true`.

### Bài 9: Infinity không do zero

```java
double x = Double.MAX_VALUE * 2.0;
```

**Đáp án:** `Infinity`, dù không có phép chia.

### Bài 10: BigDecimal

```java
java.math.BigDecimal.ONE.divide(java.math.BigDecimal.ZERO);
```

**Đáp án:** `ArithmeticException`, không phải infinity.

### Bài 11: Chuyển kiểu kết quả đặc biệt

```java
System.out.println((int) (0.0 / 0.0));
```

**Đáp án:** `0`. Đây là kết quả narrowing từ NaN sang int, không phải kết quả phép chia thực.

### Bài 12: API cần hữu hạn

Nếu yêu cầu “không được trả infinity hay NaN”, kiểm tra mẫu số bằng `denominator == 0.0` **đã đủ chưa?** **Chưa.** Kiểm tra tính hữu hạn của đầu vào và kết quả, đồng thời chọn chính sách phù hợp cho tràn/underflow.

### Bài 13: Checked hay unchecked?

`ArithmeticException` thuộc checked hay unchecked? **Unchecked**, vì kế thừa `RuntimeException`.

### Bài 14: Dấu zero

`0.0 == -0.0` là `true`. Vậy `1.0 / 0.0` và `1.0 / -0.0` có giống nhau không? **Không**: một kết quả `Infinity`, kết quả còn lại `-Infinity`.

### Bài 15: Chặn NaN khỏi kiểm tra ngưỡng

Với `double value = Double.NaN`, biểu thức `value < 0 || value > 100` có phát hiện giá trị không hợp lệ không? **Không**, hai so sánh đều false. Nên dùng thêm `Double.isFinite(value)` khi yêu cầu chỉ chấp nhận số hữu hạn.

## 18. Tài liệu chính thức Java 17

- [Java Language Specification 17 — `15.17.2 Division Operator /](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.17.2): chia nguyên, chia floating-point và zero.
- [Java Language Specification 17 — `15.17.3 Remainder Operator %](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.17.3): lấy dư nguyên và floating-point.
- [Java Language Specification 17 — `5.6 Numeric Contexts](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.6): numeric promotion và kiểu toán hạng.
- [Java Language Specification 17 — `4.2.3 Floating-Point Types, Formats, and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3): infinity, NaN, signed zero và giá trị floating-point.
- [Java Language Specification 17 — `5.1.3 Narrowing Primitive Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.3): chuyển NaN/infinity từ float/double sang kiểu nguyên.
- [Java SE 17 — ArithmeticException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ArithmeticException.html): unchecked exception của các điều kiện số học bất hợp lệ.
- [Java SE 17 — Double](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): `isNaN`, `isInfinite`, `isFinite` và các hằng số đặc biệt.
- [Java SE 17 — Float](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html): kiểm tra và biểu diễn giá trị float.
- [Java SE 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): divide, rounding và lỗi `ArithmeticException`.
- [Java SE 17 — Math.IEEEremainder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html#IEEEremainder(double,double)): so sánh với toán tử `%`.

**Ghi nhớ:** Với toán tử `/` và `%` của **kiểu nguyên** Java, mẫu số bằng 0 gây `ArithmeticException` (unchecked). Với `float`/`double`, phép toán dùng ngữ nghĩa IEEE 754 nên có thể trả `Infinity`, `-Infinity` hoặc `NaN` **mà không ném ngoại lệ**. Hãy xét kiểu toán hạng sau numeric promotion, không chỉ xét literal `0`; dùng `Double.isNaN`/`Double.isInfinite`/`Double.isFinite` và chính sách validation rõ ràng nếu ứng dụng không chấp nhận các giá trị đặc biệt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-runtime-exceptions-007">ClassCastException và ArrayStoreException</a>

<details>
<summary>Xem chi tiết</summary>

# `ClassCastException` và `ArrayStoreException` trong Java: ép kiểu đối tượng và an toàn kiểu của mảng

`ClassCastException` và `ArrayStoreException` đều là **unchecked exceptions** trong Java. Cả hai thường xuất hiện khi một chương trình cố sử dụng một **đối tượng tham chiếu** theo một kiểu không phù hợp với **kiểu runtime thực tế** của đối tượng đó. Tuy nhiên, **thao tác làm phát sinh lỗi hoàn toàn khác nhau**:

- **`ClassCastException`**: thất bại khi một phép **ép kiểu tham chiếu (*reference cast*)** hoặc phép chuyển kiểu tương đương tại runtime cố xem đối tượng như một kiểu mà nó không có.
- **`ArrayStoreException`**: thất bại khi chương trình **ghi một phần tử vào mảng tham chiếu** mà đối tượng phần tử không tương thích với **kiểu phần tử runtime** của mảng.

Một chương trình có thể biên dịch thành công nhưng vẫn gặp một trong hai lỗi này, bởi trình biên dịch chỉ có một phần thông tin kiểu ở thời điểm compile, trong khi JVM kiểm tra kiểu thực tế khi phép ép kiểu hoặc phép ghi mảng chạy.

Hai ví dụ ngắn minh họa sự khác biệt:

```java
Object value = "Java";
Integer number = (Integer) value; // ClassCastException
```

```java
Object[] values = new String[1];
values[0] = Integer.valueOf(10); // ArrayStoreException
```

Hai đoạn trên là **các ví dụ cố ý ném lỗi runtime**, không phải chương trình đầy đủ. Ở ví dụ thứ nhất, `value` thật ra là một `String`, không phải `Integer`. Ở ví dụ thứ hai, biến `values` có **kiểu tĩnh** `Object[]`, nhưng chính mảng được tạo ra là **`String[]`**, nên nó chỉ được chứa `String` hoặc `null`.

## 1. Vị trí trong hệ phân cấp ngoại lệ

Cả hai lớp đều thuộc `java.lang`:

```text
java.lang.Object
└── java.lang.Throwable
    └── java.lang.Exception
        └── java.lang.RuntimeException
            ├── java.lang.ClassCastException
            └── java.lang.ArrayStoreException
```

Đây là **trích đoạn cây kế thừa**, không liệt kê mọi subtype của `RuntimeException`.

| Thuộc tính | `ClassCastException` | `ArrayStoreException` |
| --- | --- | --- |
| Nhóm | Unchecked, extends `RuntimeException` | Unchecked, extends `RuntimeException` |
| Thao tác điển hình | Ép kiểu reference không hợp lệ | Ghi phần tử sai kiểu vào reference array |
| Ví dụ | `(Integer) (Object) "abc"` | Ghi `42` vào mảng thực `String[]` qua `Object[]` |
| Kiểu được kiểm tra | Kiểu runtime của **đối tượng bị ép** so với kiểu đích | Kiểu runtime của **phần tử** so với component type thực của mảng |
| Có thể xảy ra dù biên dịch thành công? | Có | Có |
| Có nhất thiết phải có ép kiểu tường minh trong source? | Không; có thể do cast do compiler chèn, ví dụ khi đọc generic | Không; assignment vào array là đủ |
| Có nhất thiết phải có mảng? | Không | Có, hoặc API liên quan thao tác sao chép/lưu trữ vào mảng |
| Cách phòng tránh trọng tâm | Mô hình kiểu đúng, kiểm tra `instanceof` / `Class.isInstance` khi cần | Không dựa vào tính covariance để ghi phần tử không phù hợp; dùng container generic đúng thiết kế |

**Đừng dùng một `catch (RuntimeException)` rộng để “sửa” hai lỗi này một cách mặc định.** Chúng thường là dấu hiệu của lỗi thiết kế hoặc sai giả định về kiểu, không phải điều kiện nghiệp vụ bình thường có thể bỏ qua.

## 2. Kiểu tĩnh và kiểu runtime: nguyên nhân chung

Java kiểm tra nhiều điều kiện bằng **kiểu tĩnh** mà trình biên dịch nhìn thấy ở biểu thức. Nhưng đối tượng được tạo ra trong bộ nhớ có **kiểu runtime** cụ thể.

```java
Object text = "hello";
```

- Kiểu tĩnh của biến `text` là `Object`.
- Kiểu runtime của đối tượng được tham chiếu là `String`.
- `text instanceof String` là `true`.
- `text instanceof Integer` là `false`.
- Phép ép `(String) text` thành công.
- Phép ép `(Integer) text` biên dịch được nhưng ném `ClassCastException` khi chạy.

Tương tự với mảng:

```java
Object[] elements = new String[2];
```

- Kiểu tĩnh của `elements` là `Object[]`.
- Kiểu runtime của đối tượng mảng là `String[]`.
- Thành phần thực được mảng cho phép lưu là `String` (hoặc `null`).
- `elements[0] = "ok";` thành công.
- `elements[1] = new Object();` ném `ArrayStoreException`.

**Cùng một khác biệt tĩnh/runtime, nhưng hai điểm kiểm tra runtime khác nhau:** kiểm tra *cast* so với kiểm tra *store*.

### Không nhầm “kiểu của biến” với “kiểu của object”

Một biến kiểu `Object` có thể tham chiếu tới rất nhiều loại đối tượng. Java không tự biến mọi object thành một kiểu mới khi gán tham chiếu sang biến cha. Việc `Object value = "hello";` không làm đối tượng `String` trở thành một `Integer` hay `Object` “thuần” mất kiểu gốc.

Với mảng, việc gán `String[]` cho biến `Object[]` cũng không đổi **component type thực** của mảng từ `String` thành `Object`. Kiểm tra lúc ghi vẫn dựa trên mảng thực.

## 3. `ClassCastException` xuất hiện khi nào?

Một reference cast có thể thành công hoặc thất bại dựa trên khả năng đối tượng runtime là instance của kiểu đích.

### Ép kiểu hợp lệ

```java
Object object = "Java";
String language = (String) object;
System.out.println(language);
```

Kết quả:

```text
Java
```

Vì object thực sự là `String`, phép ép kiểu thành công.

### Ép kiểu sai: runtime phát hiện

```java
Object object = "Java";
Integer number = (Integer) object;
```

Trình biên dịch chấp nhận vì `object` có kiểu tĩnh `Object`, nên về nguyên tắc có thể trỏ tới một `Integer`. Tại runtime, object thực sự là `String`, và `String` không phải `Integer`, nên JVM ném `ClassCastException`.

Nếu viết trực tiếp:

```java
// Cố ý KHÔNG BIÊN DỊCH.
String text = "Java";
Integer number = (Integer) text;
```

hai kiểu lớp `String` và `Integer` không có quan hệ chuyển đổi reference cast phù hợp ở đây; trình biên dịch có thể loại bỏ phép ép ngay. Vì vậy **không phải mọi ý định ép kiểu sai đều chờ đến runtime**. `ClassCastException` xảy ra trong những tình huống compiler cho phép kiểm tra khi chạy.

### Downcast từ lớp cha

```java
class Animal {}

class Dog extends Animal {
    void bark() {}
}

class Cat extends Animal {}
```

Đây là các khai báo minh họa. Với:

```java
Animal animal = new Cat();
Dog dog = (Dog) animal; // ClassCastException.
```

Biên dịch thành công vì `animal` là `Animal`, còn `Dog` là subtype có thể xảy ra của `Animal`. Nhưng object thực là `Cat`, không phải `Dog`.

**Bài học:** downcast không làm object đổi từ `Cat` sang `Dog`; nó chỉ kiểm tra xem object đã có đúng kiểu đích hay chưa.

## 4. Phép kiểm tra bằng `instanceof` và pattern matching trong Java 17

Khi chương trình thật sự phải làm việc với các đối tượng đa dạng, có thể kiểm tra kiểu trước khi ép:

```java
Object value = "sample";

if (value instanceof String text) {
    System.out.println("length=" + text.length());
} else {
    System.out.println("not a String");
}
```

Đây là đoạn Java hợp lệ trong một phương thức. Java 17 hỗ trợ **pattern matching for `instanceof`**: khi điều kiện đúng, biến `text` có kiểu `String`, giúp tránh một cast lặp lại.

Không cần viết:

```java
if (value instanceof String) {
    String text = (String) value;
}
```

trong những trường hợp pattern matching diễn đạt rõ hơn. Tuy nhiên, **`instanceof` không thay thế thiết kế hệ kiểu**. Nếu lúc nào cũng phải kiểm tra hàng chục kiểu bằng một chuỗi `if`, có thể interface/polymorphism hoặc kiểu dữ liệu có cấu trúc phù hợp hơn.

### `instanceof` với `null`

```java
Object value = null;

System.out.println(value instanceof String);
String text = (String) value;
System.out.println(text == null);
```

Stdout:

```text
false
true
```

- `null instanceof String` là `false`.
- Ép reference `null` sang `String` **không ném `ClassCastException`**; kết quả vẫn là `null`.
- Việc gọi phương thức trên giá trị null sau đó có thể ném **`NullPointerException`**, là loại lỗi khác.

**Đừng nhầm ép kiểu null thành công với object tồn tại và có thể sử dụng được.**

### `Class.isInstance` và `Class.cast`

Khi kiểu đích chỉ biết tại runtime qua một đối tượng `Class<T>`, có thể dùng:

```java
Class<String> type = String.class;
Object value = "hello";

System.out.println(type.isInstance(value));
String result = type.cast(value);
```

`type.isInstance(value)` trả `true`, còn `type.cast(value)` trả cùng object sau khi kiểm tra kiểu. Nếu giá trị không phù hợp, `Class.cast` ném `ClassCastException`. Với `null`, `cast(null)` trả `null`.

Các API này hữu ích cho framework/reflection/deserialization có kiểu đích động, nhưng không nên thêm reflection cho một trường hợp mà generic type hoặc polymorphism đã đủ.

## 5. `ArrayStoreException` xuất hiện khi nào?

`ArrayStoreException` có thể phát sinh khi **một đối tượng không tương thích được gán vào một mảng của kiểu tham chiếu**.

```java
String[] names = new String[2];
Object[] anyObjects = names;

anyObjects[0] = "Alice";       // Thành công.
anyObjects[1] = Integer.valueOf(5); // ArrayStoreException.
```

Điểm then chốt: **mảng vẫn là `String[]`**. Biến `anyObjects` có thể nhìn nó như một `Object[]` nhưng không làm nó trở thành mảng lưu được mọi loại object.

### Tại sao trình biên dịch lại cho phép?

Vì Java cho phép **covariance của reference array**: nếu `String` là subtype của `Object` thì `String[]` cũng có thể được dùng như `Object[]`.

```java
String[] source = new String[2];
Object[] destination = source; // Hợp lệ.
```

Khả năng gán này tiện khi đọc hoặc đi qua API cần `Object[]`, nhưng **không an toàn kiểu hoàn toàn với thao tác ghi**. JVM phải kiểm tra mỗi lần ghi để bảo vệ tính đúng đắn của mảng runtime.

Nếu loại bỏ kiểm tra runtime, mảng `String[]` có thể chứa `Integer`, khiến mã đọc `String` về sau gặp lỗi khó hiểu. Vì vậy JVM chủ động ném `ArrayStoreException` **ngay tại phép ghi không tương thích**.

### Kiểu phần tử runtime không phải kiểu tĩnh của biến mảng

```java
Number[] values = new Integer[2];

values[0] = Integer.valueOf(1); // Đúng.
values[1] = Long.valueOf(2L);   // ArrayStoreException.
```

`Integer[]` là subtype của `Number[]`, nên phép gán mảng ban đầu hợp lệ. Nhưng mảng thực là `Integer[]` nên không nhận object `Long`, dù `Long` cũng extends `Number`.

### `null` là phần tử hợp lệ cho reference array

```java
Object[] values = new String[2];
values[0] = null;      // Hợp lệ.
values[1] = "hello";   // Hợp lệ.
```

`null` không phải instance của `String` nhưng **được phép lưu trong mảng tham chiếu**. Không có `ArrayStoreException` cho phép gán null hợp lệ này.

Điều đó khác với mảng nguyên thủy như `int[]`: phần tử là `int`, không thể gán null cho một vị trí `int`.

## 6. Ví dụ Java 17+ chạy được: phân biệt hai ngoại lệ trong cùng chương trình

Lưu toàn bộ mã thành **`CastAndArrayStoreDemo.java`**:

```java
import java.util.Arrays;

public class CastAndArrayStoreDemo {

    static void wrongCast() {
        Object value = "Java";

        try {
            Integer number = (Integer) value;
            System.out.println(number);
        } catch (ClassCastException ex) {
            System.out.println(
                    "object cast=" + ex.getClass().getSimpleName());
        }

        if (value instanceof String text) {
            System.out.println("safe text=" + text);
        }
    }

    static void wrongArrayStore() {
        Object[] values = new String[2];
        values[0] = "alpha";

        try {
            values[1] = Integer.valueOf(42);
        } catch (ArrayStoreException ex) {
            System.out.println(
                    "array store=" + ex.getClass().getSimpleName());
        }

        System.out.println(
                "array content=" + Arrays.toString(values));
    }

    static void wrongArrayCast() {
        Object value = new String[] {"A", "B"};

        try {
            Integer[] numbers = (Integer[]) value;
            System.out.println(numbers.length);
        } catch (ClassCastException ex) {
            System.out.println(
                    "array cast=" + ex.getClass().getSimpleName());
        }

        if (value instanceof String[] names) {
            System.out.println("string array length=" + names.length);
        }
    }

    static void numberArrayStore() {
        Number[] values = new Integer[2];
        values[0] = Integer.valueOf(7);

        try {
            values[1] = Long.valueOf(9L);
        } catch (ArrayStoreException ex) {
            System.out.println(
                    "number store=" + ex.getClass().getSimpleName());
        }

        System.out.println(
                "numbers=" + Arrays.toString(values));
    }

    static void nullIsAllowed() {
        Object value = null;
        String text = (String) value;
        Object[] values = new String[1];
        values[0] = null;

        System.out.println("null cast=" + (text == null));
        System.out.println("null array store=" + (values[0] == null));
    }

    public static void main(String[] args) {
        wrongCast();
        wrongArrayStore();
        wrongArrayCast();
        numberArrayStore();
        nullIsAllowed();
    }
}
```

Biên dịch và chạy bằng **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 CastAndArrayStoreDemo.java
java CastAndArrayStoreDemo
```

**Stdout chính xác:**

```text
object cast=ClassCastException
safe text=Java
array store=ArrayStoreException
array content=[alpha, null]
array cast=ClassCastException
string array length=2
number store=ArrayStoreException
numbers=[7, null]
null cast=true
null array store=true
```

### Giải thích từng nhánh

**`wrongCast()`:** object thực là `String`, nhưng cast sang `Integer`. JVM ném `ClassCastException`. Handler in tên lớp đơn giản; sau đó `instanceof String text` cho phép truy cập `String` an toàn.

**`wrongArrayStore()`:** mảng thực là `String[2]`. Ghi `"alpha"` đúng; ghi `Integer(42)` vào vị trí thứ hai ném `ArrayStoreException`, nên phần tử đó vẫn `null`. Lệnh `Arrays.toString` cho thấy phần tử không được ghi thành công.

**`wrongArrayCast()`:** `value` tham chiếu một mảng `String[]` nhưng bị ép sang `Integer[]`. Đó là **lỗi cast đối tượng mảng**, vì vậy ngoại lệ là `ClassCastException`, **không phải `ArrayStoreException`**. Sau đó `instanceof String[]` nhận diện chính xác kiểu runtime của mảng.

**`numberArrayStore()`:** biến `Number[]` trỏ tới mảng `Integer[]`. `Integer(7)` phù hợp; `Long(9)` không phù hợp với component type runtime `Integer`, nên ném `ArrayStoreException`.

**`nullIsAllowed()`:** cast null sang reference type và lưu null vào reference array đều hợp lệ. Kết quả của hai phép kiểm tra là `true`.

Chương trình không in message đầy đủ hoặc stack trace của exception, vì cách diễn đạt chi tiết của message có thể thay đổi theo JDK. In `getClass().getSimpleName()` và trạng thái mảng giúp giữ stdout ổn định.

## 7. Ép kiểu chính đối tượng mảng có thể là `ClassCastException`

Hai dòng này không tương đương:

```java
Object value = new String[2];
Integer[] numbers = (Integer[]) value; // ClassCastException.
```

```java
Object[] values = new String[2];
values[0] = Integer.valueOf(1);        // ArrayStoreException.
```

Ở đoạn một, object được kiểm tra là **toàn bộ mảng `String[]`**, rồi cast sang `Integer[]`. Không có phép ghi phần tử.

Ở đoạn hai, phép gán `values[0] = ...` kiểm tra **một giá trị phần tử** đối với mảng runtime `String[]`. Không hề có cast sang `Integer[]`.

Đây là cách phân biệt nhanh trong lúc đọc stack trace:

- Xem dòng gây lỗi có **`(TargetType) value`**, `Class.cast(value)`, hoặc chuyển kiểu do compiler chèn không?
- Hay có phép **`array[index] = element`** qua một biến mảng rộng kiểu, hoặc một API sao chép phần tử vào mảng?
- Xác định **kiểu runtime thật** của object và mảng tại điểm xảy ra lỗi.

Không nên dựa duy nhất vào từ khóa trong message, vì message chẩn đoán không phải định dạng hợp đồng ổn định.

## 8. Arrays covariance: tại sao Java cho phép nhưng vẫn có rủi ro?

Với các **kiểu tham chiếu** `S` và `T`, khi `S` là subtype của `T`, Java cho phép gán `S[]` cho `T[]`:

```java
String[] strings = {"a", "b"};
Object[] objects = strings;
```

Vì `String[]` là subtype của `Object[]`, phép gán hợp lệ. Nhưng phép gán này không tạo ra một mảng `Object[]` mới; **hai biến trỏ cùng object mảng `String[]`**.

Covariance hữu ích cho một số API đọc dữ liệu, nhưng nếu caller ghi một object chỉ tương thích với `T` mà không tương thích `S`, lỗi runtime sẽ xảy ra.

### Ví dụ giả định API nhận mảng rộng kiểu

```java
static void putDefault(Object[] array) {
    array[0] = new Object();
}
```

Đây là phương thức minh họa. Nếu truyền `new Object[1]` thì phép ghi hợp lệ. Nếu truyền `new String[1]` thì phép ghi ném `ArrayStoreException`.

**Cùng một chữ ký phương thức**, nhưng tính hợp lệ của phép ghi phụ thuộc **runtime component type** của mảng mà caller đưa tới.

Một API chỉ cần **đọc** các phần tử thường nên không sửa mảng đầu vào. Nếu thật sự cần thay đổi phần tử, nên thiết kế kiểu tham số đủ cụ thể, định rõ loại phần tử được phép hoặc dùng generic collection phù hợp.

### Mảng reference được kiểm tra kiểu khi ghi

Có thể xem runtime component type bằng:

```java
Object[] values = new String[2];
System.out.println(
        values.getClass().getComponentType().getSimpleName());
```

Kết quả:

```text
String
```

Từ `Object[]` ở khai báo biến không được dùng để suy luận mảng thực có thể giữ bất kỳ object nào.

## 9. Mảng nguyên thủy không có covariance như mảng tham chiếu

`int[]`, `long[]`, `double[]` là các object mảng trong Java, nhưng **không có quan hệ subtype `int[] extends Object[]`**.

Ví dụ:

```java
int[] numbers = {1, 2, 3};
Object container = numbers; // Được: mọi mảng là Object.
```

Nhưng:

```java
// Cố ý KHÔNG BIÊN DỊCH.
Object[] objects = new int[3];
```

`int[]` không thể gán sang `Object[]` vì thành phần `int` là primitive, không phải reference subtype của `Object`.

### Ép kiểu primitive và `ClassCastException` là hai vấn đề khác nhau

Ép `long` sang `int` là **primitive numeric conversion**:

```java
long large = 1_000_000_000_000L;
int narrowed = (int) large;
```

Đoạn này có thể làm mất thông tin số do narrowing conversion, nhưng **không ném `ClassCastException`** chỉ vì giá trị nằm ngoài phạm vi `int`.

`ClassCastException` liên quan reference casts/chuyển kiểu reference tại runtime; `ArrayStoreException` liên quan lưu reference vào mảng runtime không tương thích. Không nên dùng hai lỗi này để mô tả tràn số khi chuyển primitive.

## 10. Generics khác arrays: invariance và type erasure

Một nguồn gây nhầm lẫn là so sánh:

```java
String[] strings = new String[2];
Object[] objects = strings; // Hợp lệ: array covariance.
```

với:

```java
java.util.List<String> strings = new java.util.ArrayList<>();
// java.util.List<Object> objects = strings; // Không biên dịch.
```

`List<String>` **không phải subtype của `List<Object>`**. Generic type parameter của `List<T>` mặc định **invariant**, khác với covariance của reference array.

Sự bất biến này chặn tại compile time việc biến một `List<String>` thành `List<Object>` để thêm `Integer` tùy ý.

### Type erasure tạo một kiểu bẫy khác cho `ClassCastException`

Với generics, Java dùng **type erasure**: tham số `T` thường không còn như một “tag” của từng `List<T>` ở runtime. Vì thế một cast có cảnh báo chưa chắc thất bại ngay tại thời điểm cast; lỗi có thể xảy ra lúc lấy phần tử ra khỏi collection.

```java
java.util.List<Integer> numbers =
        new java.util.ArrayList<>();
numbers.add(10);

Object asObject = numbers;

@SuppressWarnings("unchecked")
java.util.List<String> texts =
        (java.util.List<String>) asObject;

// String text = texts.get(0); // Có thể ClassCastException tại đây.
```

Đây là đoạn minh họa. Cast sang `List<String>` có thể qua được vì runtime kiểm tra object có phải `List` phù hợp không, **không thể kiểm tra toàn bộ tham số generic `String` bị xóa**. Khi gọi `texts.get(0)` và nhận kết quả theo kiểu `String`, compiler có thể chèn checkcast; giá trị thực `Integer` không phù hợp, dẫn đến `ClassCastException`.

**Không hiểu sai:** việc suppress compiler warning không “làm cast an toàn”. `@SuppressWarnings("unchecked")` chỉ tắt cảnh báo ở phạm vi đó; nó không thay đổi dữ liệu trong collection và không thêm kiểm tra kiểu runtime cần thiết.

## 11. Ví dụ Java 17+ chạy được: heap pollution và `ClassCastException` xuất hiện muộn

Lưu tệp **`GenericCastDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class GenericCastDemo {

    static void unsafeGenericCast() {
        List<Integer> numbers = new ArrayList<>();
        numbers.add(17);

        Object stored = numbers;

        @SuppressWarnings("unchecked")
        List<String> texts = (List<String>) stored;

        System.out.println("list cast reached");

        try {
            String text = texts.get(0);
            System.out.println(text);
        } catch (ClassCastException ex) {
            System.out.println(
                    "element read=" + ex.getClass().getSimpleName());
        }
    }

    static void safeReadUsingWildcard() {
        List<Integer> numbers = List.of(17);

        List<?> unknown = numbers;
        Object element = unknown.get(0);

        System.out.println(
                "element type=" + element.getClass().getSimpleName());

        if (element instanceof Integer number) {
            System.out.println("safe integer=" + number);
        }
    }

    static void checkArrayType() {
        Object data = new String[] {"one", "two"};

        if (data instanceof String[] texts) {
            System.out.println("array first=" + texts[0]);
        }

        System.out.println(
                "is Integer[]=" + (data instanceof Integer[]));
    }

    public static void main(String[] args) {
        unsafeGenericCast();
        safeReadUsingWildcard();
        checkArrayType();
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 GenericCastDemo.java
java GenericCastDemo
```

**Stdout chính xác:**

```text
list cast reached
element read=ClassCastException
element type=Integer
safe integer=17
array first=one
is Integer[]=false
```

### Tại sao dòng `list cast reached` được in?

Cast sang `List<String>` vượt qua kiểm tra runtime ở mức `List`. Kiểu tham số `String` không được kiểm tra hoàn chỉnh do type erasure. Khi lệnh `String text = texts.get(0);` yêu cầu giá trị là `String`, object thực từ list là `Integer` và JVM ném `ClassCastException`.

Ví dụ `safeReadUsingWildcard` dùng `List<?>` vì chưa biết kiểu phần tử. Đọc ra `Object` rồi kiểm tra `instanceof Integer` trước khi dùng là hợp lý hơn **ép cả danh sách thành `List<String>` không có cơ sở**.

### Vì sao đây không phải `ArrayStoreException`?

`ArrayList` lưu phần tử bằng một cấu trúc nội bộ, nhưng người dùng đang thao tác thông qua **API `List` và generic cast**, không làm một phép ghi `Integer` trực tiếp vào object mảng thực `String[]`. Lỗi quan sát được là cast của phần tử khi đọc, không phải vi phạm runtime component type của một `String[]`.

Không suy đoán kiểu lỗi chỉ từ việc một collection có thể dùng mảng nội bộ; phải xác định **thao tác Java/API gây lỗi ở mức chương trình**.

## 12. Vì sao không thể tạo trực tiếp `new List<String>[10]`?

Java không cho tạo mảng với loại thành phần là một kiểu generic parameterized không reifiable như `List<String>`:

```java
// Cố ý KHÔNG BIÊN DỊCH: generic array creation.
java.util.List<String>[] lists =
        new java.util.List<String>[10];
```

Một lý do thiết kế là mảng thực hiện runtime component-type checks, trong khi thông tin tham số generic như `String` của `List<String>` thường bị erasure. Nếu tạo mảng parameterized như vậy, hệ thống kiểm tra kiểu có thể tạo ảo giác an toàn khó giữ được.

Thay vì dùng mảng generic trực tiếp, đa số trường hợp nên dùng:

```java
java.util.List<java.util.List<String>> groups =
        new java.util.ArrayList<>();
```

Một `List<List<String>>` dùng generic type checking ở compile time cho các phép thêm đọc theo API `List`, tránh dựa vào array covariance.

**Lưu ý:** Java vẫn cho phép một số dạng mảng có kiểu thành phần *reifiable*, ví dụ `List<?>[]`; điều đó không làm cho `new List<String>[10]` trở thành hợp lệ.

## 13. `System.arraycopy` cũng có thể ném `ArrayStoreException`

Không chỉ phép gán dạng `array[index] = value`. Nếu bạn sao chép phần tử từ một mảng sang mảng đích có component type không tương thích, `System.arraycopy` có thể ném `ArrayStoreException`.

```java
Object[] source = {"ok", Integer.valueOf(7), "later"};
String[] destination = {"old-0", "old-1", "old-2"};

System.arraycopy(source, 0, destination, 0, source.length);
```

Đây là đoạn minh họa cố ý ném lỗi runtime. Mảng nguồn có phần tử đầu là `String` nhưng phần tử thứ hai là `Integer`. Mảng đích là `String[]`, không thể nhận `Integer`.

Một khác biệt quan trọng so với một phép gán đơn: **`arraycopy` có thể đã sao chép một phần tử hợp lệ trước khi gặp phần tử không tương thích**, nên trạng thái mảng đích có thể đã bị thay đổi một phần khi ngoại lệ được ném.

Với dạng `Object[]` → `String[]` và dữ liệu cụ thể ở trên, `System.arraycopy` chuyển phần tử `"ok"` đầu tiên trước khi gặp `Integer(7)`. Nó ném `ArrayStoreException` và không hoàn tất toàn bộ phép sao chép.

### Đừng suy ra phép sao chép là “all-or-nothing”

Nếu cần thao tác nguyên tử về ngữ nghĩa, hãy validate dữ liệu trước và cân nhắc tạo mảng mới hoặc một cấu trúc kết quả chỉ được công bố sau khi kiểm tra thành công. `System.arraycopy` không phải transaction.

## 14. Ví dụ Java 17+ chạy được: sao chép mảng có lỗi kiểu và trạng thái một phần

Lưu **`ArrayCopyFailureDemo.java`**:

```java
import java.util.Arrays;

public class ArrayCopyFailureDemo {

    public static void main(String[] args) {
        Object[] source = {
                "ok", Integer.valueOf(7), "later"
        };

        String[] destination = {
                "old-0", "old-1", "old-2"
        };

        try {
            System.arraycopy(
                    source, 0, destination, 0, source.length);
        } catch (ArrayStoreException ex) {
            System.out.println(
                    "copy error=" + ex.getClass().getSimpleName());
        }

        System.out.println(
                "destination=" + Arrays.toString(destination));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ArrayCopyFailureDemo.java
java ArrayCopyFailureDemo
```

**Stdout chính xác:**

```text
copy error=ArrayStoreException
destination=[ok, old-1, old-2]
```

### Phân tích

- Phần tử nguồn `source[0]` là `String("ok")`, tương thích với `String[]`, nên được sao chép vào `destination[0]`.
- Phần tử `source[1]` là `Integer(7)`, không thể lưu trong mảng `String[]`, nên thao tác báo `ArrayStoreException`.
- `destination[1]` và `destination[2]` vẫn giữ các giá trị cũ trong trường hợp này.
- Handler chỉ bắt lỗi, **không rollback** thay đổi `destination[0]`.

Đây là lý do cần cân nhắc trạng thái dữ liệu sau khi bắt `ArrayStoreException` từ một API sao chép. Không nên bắt lỗi rồi mặc định xem mảng đích chưa bị động tới.

## 15. Phân biệt với các lỗi mảng hoặc reference khác

Các tình huống có vẻ giống nhau nhưng thực tế khác loại:

| Mã/sự kiện | Lỗi tương ứng | Nguyên nhân |
| --- | --- | --- |
| `(Integer) (Object) "s"` | `ClassCastException` | Reference cast sai kiểu runtime |
| `Object[] a = new String[1]; a[0] = 1;` | `ArrayStoreException` | Lưu object không tương thích với component type |
| `Object[] a = null; a[0] = "x";` | `NullPointerException` | Biến mảng tham chiếu null |
| `String[] a = new String[1]; a[5] = "x";` | `ArrayIndexOutOfBoundsException` | Vị trí truy cập ngoài phạm vi |
| `String value = (String) null;` | Không lỗi tại cast | Reference null có thể cast sang reference type |
| `Object[] a = new String[1]; a[0] = null;` | Không lỗi | Null hợp lệ cho reference array |
| `int n = (int) 1_000_000_000_000L;` | Không phải `ClassCastException` | Numeric narrowing conversion primitive |
| `Object a = new int[2];` | Không lỗi | Mảng primitive vẫn là object |
| `Object[] a = new int[2];` | Lỗi biên dịch | `int[]` không phải `Object[]` |

Đừng dùng `catch (ClassCastException | ArrayStoreException ex)` như một cơ chế kiểm soát kiểu bình thường khi có thể thiết kế code để không tạo tình huống sai ngay từ đầu.

### `NumberFormatException` không phải `ClassCastException`

`Integer.parseInt("abc")` thất bại vì nội dung chuỗi không phải biểu diễn số nguyên hợp lệ. Đây là `NumberFormatException`, không phải lỗi ép một object `String` thành object `Integer`. Parse một giá trị và reference cast giữa các object là hai thao tác khác nhau.

### Không cố đoán lỗi từ thứ tự đánh giá nhiều vấn đề cùng lúc

Nếu một câu lệnh vừa dùng mảng null, vừa chỉ số sai, vừa giá trị không tương thích, loại lỗi đầu tiên phụ thuộc quy tắc đánh giá và kiểm tra của biểu thức. Khi học hai loại lỗi, hãy dùng ví dụ chỉ có **một điều kiện sai chính**; khi debug thực tế, xem stack trace và dòng mã, không suy luận từ nhiều giả định không kiểm chứng.

## 16. Cách phòng tránh `ClassCastException` trong thiết kế API

### Ưu tiên polymorphism thay vì downcast

Nếu tất cả đối tượng có một hành vi chung, đưa hành vi ấy lên interface:

```java
interface Renderer {
    String render();
}

final class TextRenderer implements Renderer {
    @Override
    public String render() {
        return "text";
    }
}

final class HtmlRenderer implements Renderer {
    @Override
    public String render() {
        return "html";
    }
}
```

Đây là các lớp minh họa, không cần ép từ `Renderer` xuống `TextRenderer` hoặc `HtmlRenderer` để gọi `render()`. Caller chỉ viết:

```java
String output = renderer.render();
```

nếu `renderer` là biến kiểu `Renderer` hợp lệ.

Điều này giúp mở rộng subtype mới mà không phải cập nhật một chuỗi cast liên tục.

### Dùng generics đúng kiểu

Một API nhận danh sách tên nên ưu tiên `List<String>`, không phải `List<Object>` mà caller phải downcast từng phần tử. Nếu dữ liệu thực sự có nhiều kiểu, xem xét một interface chung, `sealed hierarchy`, record có tag hoặc `List<?>` với kiểm tra kiểu rõ ràng.

### Kiểm tra kiểu ở ranh giới dữ liệu không đáng tin

Với reflection, plugin hoặc deserialization mà object đến từ bên ngoài:

```java
static String describe(Object value) {
    if (value instanceof String text) {
        return "text:" + text;
    }
    if (value instanceof Integer number) {
        return "number:" + number;
    }
    return "unsupported";
}
```

Đây là phương thức Java độc lập có thể đặt trong lớp. Nó sử dụng pattern matching thay vì ép kiểu mù quáng. Tuy nhiên, ở API public nên chọn chính sách rõ khi gặp loại không hỗ trợ: trả lỗi validation, từ chối input, hoặc mô hình hóa kiểu kết quả thích hợp, chứ không chỉ mặc định bỏ qua.

### Dùng `Class<T>` khi kiểu được truyền vào lúc chạy

```java
static <T> T requireType(Object value, Class<T> type) {
    if (!type.isInstance(value)) {
        throw new IllegalArgumentException(
                "unexpected value type");
    }
    return type.cast(value);
}
```

Đây là phương thức minh họa, với hợp đồng **không chấp nhận null**. `isInstance(null)` là `false`, nên hàm chủ động từ chối null trước khi cast. Cách này có ích khi phương thức nhận dữ liệu động cùng một `Class<T>` làm “type token”.

**Không dùng `Class<T>` nếu cần biểu diễn đầy đủ tham số generic như `List<String>`**: `List.class` không giữ nguyên `String` là tham số type ở runtime.

## 17. Cách phòng tránh `ArrayStoreException`

### Không gán bừa vào mảng được truyền bằng kiểu quá rộng

Một hàm:

```java
static void fill(Object[] target, Object value) {
    target[0] = value;
}
```

có thể thất bại nếu mảng runtime hẹp hơn kiểu `Object[]`. Nếu caller có thể truyền `String[]`, `Integer[]` hoặc `Number[]`, hàm không được mặc định rằng mọi `Object` đều hợp lệ cho mảng.

Những chiến lược tốt hơn:

1. **Không sửa mảng của caller** nếu nhiệm vụ thực sự chỉ cần đọc.
2. **Giữ kiểu phần tử ở API** bằng generics hoặc kiểu cụ thể.
3. **Tạo mảng có component type đúng**, nếu hàm chịu trách nhiệm sở hữu dữ liệu.
4. **Xác thực dữ liệu** khi nhận từ nguồn động trước khi ghi hàng loạt.
5. **Không dựa vào `Object[]` làm vùng chứa tùy ý** nếu mảng thực có thể là một array subtype hẹp.
6. Nếu dùng `System.arraycopy`, tính đến khả năng sao chép một phần khi lỗi.
7. Nếu cần sưu tập có thể thêm đa dạng object, dùng một `ArrayList<Object>` thực sự được khai báo để chứa `Object`, hoặc mô hình kiểu chung phù hợp.

### Generic collections thường an toàn hơn cho thao tác thêm

`List<String>` cho phép compiler từ chối:

```java
java.util.List<String> values =
        new java.util.ArrayList<>();

// values.add(Integer.valueOf(7)); // Không biên dịch.
```

Với các thao tác thông thường không dùng raw/unchecked casts, `List<String>` giúp compiler chặn thêm `Integer` ngay từ đầu, trong khi `Object[]` có thể trỏ tới `String[]` và chỉ ném lỗi lúc chạy.

**Không nói rằng collection generics “không bao giờ sai kiểu”.** Raw types, unchecked casts, reflection và code không an toàn vẫn có thể gây *heap pollution*, và lúc đọc phần tử có thể gặp `ClassCastException`. Generics nâng khả năng kiểm tra ở compile time, nhưng không thay thế việc tuân thủ API.

### Về mảng bất biến

Nếu không cần thay đổi phần tử, cân nhắc trả `List.copyOf(...)` hoặc một cấu trúc immutable phù hợp. Điều này tránh một lớp rủi ro ghi sai kiểu, đồng thời làm hợp đồng dễ hiểu hơn. Tuy nhiên, `List.copyOf` từ chối phần tử null; hãy đọc hợp đồng cụ thể thay vì coi mọi collection immutable có hành vi giống mảng.

## 18. Cách đọc stack trace và chẩn đoán nhanh

Cả hai lỗi thường có stack trace mô tả vị trí hoạt động không hợp lệ. Ví dụ định dạng minh họa:

```text
java.lang.ClassCastException: <runtime class> cannot be cast to <target>
    at example.Service.convert(Service.java:<line>)
    at example.Main.main(Main.java:<line>)
```

```text
java.lang.ArrayStoreException: <incompatible type>
    at example.Service.fill(Service.java:<line>)
    at example.Main.main(Main.java:<line>)
```

Đây **không phải thông điệp nguyên văn cố định**: nội dung message, tên module, class loader và số dòng có thể khác theo JDK, bytecode và nguồn gây lỗi.

### Quy trình điều tra `ClassCastException`

1. Xác định **biểu thức cast** tại frame liên quan: tường minh, `Class.cast`, hoặc cast ngầm khi đọc generic.
2. In/quan sát **kiểu runtime thật** của object, nếu dữ liệu không nhạy cảm.
3. Xác định **kiểu đích** mà code kỳ vọng.
4. Truy nguồn đối tượng được tạo/gán ở đâu: class thực, map, deserialization, reflection, raw generic, plugin...
5. Chọn sửa thiết kế kiểu, kiểm tra `instanceof`, hoặc chuyển đổi dữ liệu thực sự thay vì ép kiểu.
6. Kiểm tra lại các đường null và dữ liệu không hợp lệ.

Ví dụ xem kiểu runtime:

```java
Object value = "Java";
System.out.println(value.getClass().getName());
```

Stdout:

```text
java.lang.String
```

Cần bảo vệ null trước khi gọi `getClass()`. Không sử dụng `getClass()` như một phần logic nghiệp vụ nếu polymorphism đã diễn đạt hợp đồng tốt hơn.

### Quy trình điều tra `ArrayStoreException`

1. Tìm điểm ghi `array[index] = value` hoặc `System.arraycopy`.
2. Xác định **kiểu runtime của mảng** bằng `array.getClass()` và `getComponentType()`.
3. Xác định kiểu runtime của **giá trị đang ghi**.
4. Kiểm tra nơi mảng được tạo; một biến `Object[]` có thể trỏ tới `String[]`.
5. Nếu là sao chép nhiều phần tử, xác định **trạng thái mảng đích sau lỗi**: có thể đã bị thay đổi một phần.
6. Sửa chữ ký và ownership để không cần phép ghi vượt component type; không chỉ thêm `catch` rồi tiếp tục trong trạng thái chưa chắc đúng.

### Không dùng message làm giao thức phân loại

Không nên viết:

```java
if (ex.getMessage().contains("String")) {
    // ...
}
```

`getMessage()` có thể null và định dạng thông điệp không ổn định. Khi phải phân loại cho chương trình, dùng **kiểu ngoại lệ**, error code riêng, hoặc dữ liệu đầu vào đã được validate. Message/stack trace phục vụ chẩn đoán.

## 19. Ví dụ một thư viện API bị lỗi do giả định sai về mảng

Giả sử API này được viết để “điền giá trị mặc định”:

```java
static void initializeAll(Object[] values) {
    for (int i = 0; i < values.length; i++) {
        values[i] = new Object();
    }
}
```

Tất cả lời gọi với `new Object[3]` đều hợp lệ. Nhưng nếu caller gọi:

```java
String[] labels = new String[3];
initializeAll(labels);
```

thì Java cho phép truyền `String[]` vào tham số `Object[]` do covariance. Trong vòng lặp, dòng `values[i] = new Object()` ném `ArrayStoreException` ngay khi cố lưu vào `String[]`.

Nếu API chỉ có mục tiêu **xóa phần tử**, nó có thể đặt mọi phần tử reference thành `null` mà không vi phạm component type (nếu hợp đồng cho phép xóa và hậu quả null phù hợp). Nếu cần **giá trị mặc định theo kiểu**, API phải biết kiểu phần tử và cách tạo giá trị đúng; tạo một `Object` chung không đủ.

Ví dụ này minh họa rằng đôi khi **lỗi là ở chữ ký và hợp đồng thiết kế API**, không phải ở caller. Một phương thức nhận `Object[]` nhưng lại tự ý ghi object bất kỳ là một thiết kế cần xem xét lại.

## 20. `ArrayStoreException` và validation cho nhập liệu động

Khi dữ liệu đến từ JSON hoặc cấu hình động, có thể có danh sách giá trị không đồng nhất. Ví dụ một API nội bộ nhận `List<Object>`, trong đó có chuỗi và số.

Nếu chỉ cần tất cả là chuỗi, **không nên** sao chép thẳng vào `String[]` rồi dựa vào `ArrayStoreException` để báo validation. Cách này khiến thông tin lỗi nghèo nàn và có thể tạo kết quả một phần.

Nên kiểm tra từng giá trị, giữ chỉ số và lỗi có cấu trúc:

```java
static String[] requireStrings(
        java.util.List<?> input) {

    String[] result = new String[input.size()];

    for (int i = 0; i < input.size(); i++) {
        Object item = input.get(i);

        if (!(item instanceof String text)) {
            throw new IllegalArgumentException(
                    "item at index " + i + " must be a String");
        }

        result[i] = text;
    }

    return result;
}
```

Đây là phương thức minh họa Java 17+, có chủ đích **không chấp nhận null** trong đầu vào. Nó kiểm tra bằng `instanceof String text` trước khi ghi, rồi trả một `String[]` chỉ khi tất cả dữ liệu đã đi qua vòng lặp thành công.

Lợi ích:

- Câu báo lỗi có vị trí phần tử rõ hơn thông điệp JVM của `ArrayStoreException`.
- Mảng kết quả được tạo mới trong phương thức; nếu validation thất bại, caller **không nhận mảng kết quả chưa hoàn tất**.
- Không cần cast mù từ `List<?>` sang `List<String>`.
- Chính sách với null là chủ động; nếu null được phép, điều kiện kiểm tra có thể được thay đổi có chủ đích.

Trong hệ thống lớn, có thể dùng record `ValidationFailure(index, code, safeMessage)` để trả nhiều lỗi thay vì ném lỗi đầu tiên; điều đó là lựa chọn hợp đồng validation, không phải chức năng tự có của `ArrayStoreException`.

## 21. Các lỗi hiểu sai phổ biến

| Nhầm lẫn | Thực tế | Cách tiếp cận tốt |
| --- | --- | --- |
| “Ép `Object` thành bất cứ lớp nào cũng biến object thành lớp đó” | Cast chỉ kiểm tra kiểu runtime; không biến đổi object | Dùng constructor/parser/chuyển đổi dữ liệu đúng chức năng |
| “Tất cả cast sai đều ném `ClassCastException` lúc chạy” | Một số cast bị compiler từ chối từ đầu | Phân biệt lỗi compile-time với lỗi runtime |
| “Mảng `String[]` gán vào `Object[]` sẽ chứa được mọi Object” | Runtime vẫn là `String[]` | Không ghi giá trị ngoài component type |
| “`ArrayStoreException` xảy ra khi ép `String[]` sang `Integer[]`” | Đó là lỗi cast chính object mảng | Nhận diện thao tác cast vs store |
| “Có `ArrayStoreException` khi lưu null vào `String[]`” | Null hợp lệ với reference array | Kiểm tra hợp đồng null riêng |
| “`int[]` là một `Object[]`” | Primitive array không subtype `Object[]` | Phân biệt `Object` với `Object[]` |
| “Generic `List<String>` có tính covariance như `String[]`” | Generic `List<T>` mặc định invariant | Dùng wildcard phù hợp khi chỉ cần đọc |
| “Cast `List<Integer>` sang `List<String>` luôn ném ngay” | Erasure có thể khiến cast qua; lỗi nổ khi đọc phần tử | Tránh unchecked casts, dùng kiểu đúng |
| “`@SuppressWarnings` sửa dữ liệu sai kiểu” | Chỉ tắt warning | Sửa design và nơi tạo dữ liệu |
| “`System.arraycopy` lỗi thì mảng đích chắc chắn nguyên trạng” | Có thể đã sao chép một phần tử trước khi ném | Kiểm tra trước hoặc dùng phương án tạo bản mới |
| “`NumberFormatException` là lỗi cast” | Parse chuỗi thành số khác cast object | Dùng parsing API và xử lý lỗi riêng |
| “Ép null sang `String` ném `ClassCastException`” | Cast null thành null hợp lệ | Chú ý NPE khi dereference |
| “Bắt `ClassCastException` là đủ để validate toàn bộ input” | Chỉ bắt lỗi sau khi cast sai, thiếu ngữ cảnh | Xác thực dữ liệu ở boundary |
| “`Class.cast` luôn trả giá trị” | Ném `ClassCastException` nếu object không phù hợp | Dùng `isInstance` hoặc xử lý theo hợp đồng |
| “`ArrayStoreException` là checked” | Nó extends `RuntimeException` | Sửa kiểu dữ liệu, không thêm `throws` máy móc |

## 22. Bài tập tự kiểm tra

**Bài A. Kiểu lỗi nào?**

```java
Object value = Integer.valueOf(10);
String text = (String) value;
```

**Đáp án:** `ClassCastException`. Object runtime là `Integer`, không thể cast thành `String`.

**Bài B. Kiểu lỗi nào?**

```java
Object[] values = new String[1];
values[0] = new Object();
```

**Đáp án:** `ArrayStoreException`. Mảng thực `String[]` không nhận object `Object` thông thường.

**Bài C. Tại sao `Object[]` vẫn lỗi?**

Vì biến `Object[]` là **kiểu tĩnh**, còn phép ghi mảng được kiểm tra theo **component type runtime**, có thể là `String`.

**Bài D. Có lỗi không?**

```java
Object value = null;
String text = (String) value;
```

**Không có `ClassCastException`.** `text` nhận `null`. Nếu sau đó gọi `text.length()`, mới có thể gặp `NullPointerException`.

**Bài E. Có lỗi không?**

```java
Object[] values = new String[1];
values[0] = null;
```

**Không có `ArrayStoreException`** vì null được phép lưu trong reference array.

**Bài F. Ép mảng hay ghi mảng?**

```java
Object value = new String[2];
Integer[] numbers = (Integer[]) value;
```

**`ClassCastException`**, vì phép đang thất bại là ép kiểu **toàn bộ object mảng**.

**Bài G. Generic cast lúc nào lỗi?**

Một `List<Integer>` bị cast unchecked sang `List<String>` qua `Object`. Cast có thể qua, nhưng `String s = list.get(0);` có thể ném `ClassCastException`, vì object phần tử thực là `Integer`.

**Bài H. Có thể gán `int[]` vào `Object[]` không?**

**Không.** Primitive array `int[]` là `Object` nhưng không là `Object[]`.

**Bài I. Mảng `Number[]` trỏ `Integer[]` có thể chứa `Long` không?**

**Không.** Ghi `Long` ném `ArrayStoreException` vì mảng runtime là `Integer[]`.

**Bài J. `System.arraycopy` ném lỗi tại phần tử thứ hai. Phần tử đầu đã sao chép có nhất thiết rollback không?**

**Không.** Trường hợp sao chép thành phần từng phần có thể để phần tử đầu đã được ghi. Không coi `arraycopy` là transaction.

**Bài K. `String[]` hay `List<String>` giúp ngăn thêm `Integer` sớm hơn?**

Với API generic sử dụng bình thường, `List<String>.add(Integer)` bị compiler từ chối. `String[]` cũng từ chối gán trực tiếp `Integer` ở compile time, nhưng vì **array covariance**, có đường `Object[]` hợp lệ để phép ghi sai bị phát hiện tại runtime. `List<String>` invariant làm giảm đường rủi ro đó.

**Bài L. Có nên phân loại bằng cách tìm `"cannot be cast"` trong `getMessage()`?**

**Không.** Message không phải hợp đồng ổn định. Dùng loại exception cùng thông tin kiểu/nguồn dữ liệu, và tránh phụ thuộc câu chữ JVM.

## 23. Checklist kiểm tra khi sửa lỗi trong ứng dụng

Khi bạn gặp `ClassCastException`:

1. Tìm biểu thức cast hoặc chỗ generic/retrieval yêu cầu một reference type cụ thể.
2. Xác định runtime class thật và kiểu đích, có kiểm tra null hợp lệ.
3. Xác minh nguồn tạo object và hợp đồng biến chứa nó.
4. Tìm raw types, unchecked cast, reflection hoặc dữ liệu từ plugin/deserialization.
5. Cân nhắc chuyển dữ liệu thực sự thay vì chỉ ép kiểu.
6. Nếu nhiều subtype cùng hành vi, cân nhắc polymorphism.
7. Chỉ dùng `instanceof` để xử lý những loại động mà API thực sự cho phép.
8. Thử lại đường dữ liệu sai kiểu và null; không chỉ thêm `catch` rồi lặng lẽ trả mặc định.

Khi gặp `ArrayStoreException`:

1. Xác định mảng thực được tạo bằng `new ComponentType[...]` ở đâu.
2. Kiểm tra runtime component type, không chỉ kiểu biến `Object[]`/`Number[]`.
3. Kiểm tra runtime type của giá trị được ghi và quy tắc null.
4. Xác minh có covariance qua tham số phương thức hoặc giá trị trả về không.
5. Kiểm tra `System.arraycopy` hay API sao chép có thể đã thay đổi mảng một phần.
6. Sửa chữ ký API, dùng mảng đúng kiểu hoặc generic collection phù hợp.
7. Nếu dữ liệu từ nguồn động, validate trước khi sao chép hoặc ghi.
8. Không mặc định dùng exception để thực hiện nhánh validation thông thường; diễn đạt lỗi ở ranh giới input bằng mã/ngữ cảnh thích hợp.

## Kết luận

**`ClassCastException` là lỗi của một phép cast reference; `ArrayStoreException` là lỗi của một phép ghi vào reference array.** Cả hai đều xuất phát từ việc kiểu runtime thực tế không phù hợp với thao tác, mặc dù kiểu tĩnh đôi khi cho phép code biên dịch.

Java 17 giúp giảm nguy cơ cast bằng **pattern matching `instanceof`**, generics và các API có kiểu rõ ràng. Với mảng, phải đặc biệt chú ý **covariance**: một biến `Object[]` hoàn toàn có thể trỏ tới `String[]`, và JVM vẫn kiểm tra phần tử theo `String` khi ghi. Khi sao chép mảng, hãy tính tới khả năng trạng thái đích thay đổi một phần nếu lỗi xảy ra.

Giải pháp bền vững là **thiết kế hệ kiểu và hợp đồng API đúng**, kiểm tra dữ liệu không đáng tin tại ranh giới, bảo toàn thông tin chẩn đoán và sửa nguyên nhân thay vì chỉ bắt hai ngoại lệ runtime rồi bỏ qua.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-runtime-exceptions-008">NegativeArraySizeException</a>

<details>
<summary>Xem chi tiết</summary>

# `NegativeArraySizeException` trong Java: khi tạo mảng với kích thước âm

`NegativeArraySizeException` là ngoại lệ **unchecked** của Java, phát sinh khi chương trình cố tạo mảng có **ít nhất một kích thước âm**. Ngoại lệ này khác với lỗi truy cập chỉ số mảng, lỗi truy cập `null`, lỗi hết bộ nhớ và lỗi đầu vào của những collection khác. Hiểu đúng nó giúp bạn tìm ra dữ liệu độ dài sai, phép tính bị tràn số hoặc cách phân bổ mảng nhiều chiều không hợp lệ.

Ví dụ tối giản:

~~~java
int requestedSize = -3;
int[] numbers = new int[requestedSize]; // Ném NegativeArraySizeException khi chạy.
~~~

Trong ví dụ trên, phép tạo `new int[-3]` **biên dịch được**; lỗi xuất hiện khi câu lệnh được thực thi. Số `-3` không phải giá trị phần tử, mà là độ dài yêu cầu của mảng.

Nếu độ dài là `0`:

~~~java
int[] empty = new int[0];
System.out.println(empty.length); // 0
~~~

đây là một mảng rỗng **hoàn toàn hợp lệ**. Điều kiện lỗi là **nhỏ hơn 0**, không phải nhỏ hơn hoặc bằng 0.

Bài học dành cho Java 17+, bao gồm: cơ chế tạo mảng, array một chiều và đa chiều, thứ tự đánh giá các biểu thức kích thước, phân biệt với ngoại lệ liên quan, trường hợp reflection, tràn số, validation an toàn, xử lý lỗi và các ví dụ Java chạy được với đầu ra xác thực.

## 1. `NegativeArraySizeException` nằm ở đâu trong phân cấp ngoại lệ?

~~~text
java.lang.Object
    └── java.lang.Throwable
        └── java.lang.Exception
            └── java.lang.RuntimeException
                └── java.lang.NegativeArraySizeException
~~~

Vì kế thừa `RuntimeException`, `NegativeArraySizeException` là **unchecked exception**: compiler không buộc phương thức có thể gây ra nó phải ghi `throws NegativeArraySizeException`, và không buộc caller phải `catch`.

Đặc điểm chính:

| Thuộc tính | Giá trị/ý nghĩa |
| --- | --- |
| Tên đầy đủ | `java.lang.NegativeArraySizeException` |
| Superclass trực tiếp | `java.lang.RuntimeException` |
| Checked hay unchecked? | Unchecked |
| Khi nào xuất hiện? | Yêu cầu tạo mảng với kích thước nguyên âm |
| Ví dụ trực tiếp | `new int[-1]`, `new String[-5]` |
| Tạo mảng rỗng | `new int[0]` hợp lệ |
| Có bắt buộc `throws` không? | Không |
| Cách phòng tránh | Kiểm tra dữ liệu đầu vào, miền hợp lệ, tràn số và giới hạn cấp phát |

`NegativeArraySizeException` có constructor không tham số và constructor nhận `String` như nhiều loại `RuntimeException`. Khi Java tự ném lỗi, nội dung `getMessage()` **không phải hợp đồng ổn định để phân tích chương trình**; hãy dựa vào loại ngoại lệ và dữ liệu độ dài gốc.

Không nên chủ động sử dụng `NegativeArraySizeException` để biểu diễn mọi giá trị số âm trong nghiệp vụ. Nó gắn với **thao tác tạo mảng**. Ví dụ số lượng đặt hàng âm hợp đồng API thường phù hợp với `IllegalArgumentException` hoặc lỗi domain cụ thể hơn.

## 2. Mảng trong Java cần kích thước không âm

Một array creation expression dùng cú pháp:

~~~java
int[] a = new int[3];
String[] names = new String[2];
double[] none = new double[0];
~~~

Các ví dụ này tạo lần lượt ba, hai và không phần tử. Giá trị phần tử ban đầu được gán mặc định theo loại, chẳng hạn `0` với `int` và `null` với tham chiếu `String`.

Độ dài mảng Java là một giá trị `int` không âm, có thể đọc qua field `length`. Sau khi tạo, **độ dài của chính mảng đó không thay đổi**; bạn có thể sửa phần tử, nhưng không tăng trực tiếp `array.length`.

~~~java
int[] scores = new int[2];
System.out.println(scores.length); // 2
scores[0] = 10;
scores[1] = 20;

// Không hợp lệ: array.length là field chỉ đọc.
// scores.length = 5;
~~~

Cú pháp `new int[-1]` được phép đi qua quá trình kiểm tra cú pháp và kiểu; biểu thức độ dài vẫn có kiểu `int`. Khi **đánh giá runtime** thấy một kích thước âm, Java ném `NegativeArraySizeException` và không trả về mảng vừa yêu cầu.

Điểm then chốt: **kích thước được tính bằng biểu thức**, không nhất thiết là một literal. Các giá trị âm thường bắt nguồn từ dữ liệu cấu hình, số lượng phần tử sau phép trừ, hoặc kết quả tính toán số nguyên.

## 3. Giá trị âm là kích thước, không phải phần tử

So sánh ba ví dụ:

~~~java
int[] a = new int[-3];   // Ném NegativeArraySizeException khi chạy.
int[] b = {-3};          // Hợp lệ: một phần tử có giá trị -3.
int[] c = new int[0];    // Hợp lệ: mảng rỗng.
~~~

- `a` yêu cầu **độ dài -3**: không thể tạo mảng.
- `b` có **độ dài 1**, và phần tử đầu tiên mang giá trị âm.
- `c` có **độ dài 0**; mảng tồn tại nhưng không có chỉ số phần tử hợp lệ.

Không được suy luận rằng chỉ cần trong dữ liệu xuất hiện một số âm là Java sẽ ném `NegativeArraySizeException`. Một mảng các số nguyên âm hoàn toàn bình thường:

~~~java
int[] temperatures = {-5, -2, 0, 7};
System.out.println(temperatures.length); // 4
System.out.println(temperatures[0]);     // -5
~~~

Điểm cần rà soát là **biểu thức ở trong dấu `[...]` của thao tác tạo mảng**, ví dụ `new int[size]`, chứ không phải mọi giá trị nằm trong array initializer `{...}`.

## 4. Java 17+: chương trình cơ bản thử kích thước âm, 0 và dương

Lưu tệp `NegativeArraySizeDemo.java`:

~~~java
public class NegativeArraySizeDemo {
    static void allocate(int size) {
        try {
            int[] numbers = new int[size];
            System.out.println("size=" + size + ", length=" + numbers.length);
        } catch (NegativeArraySizeException ex) {
            System.out.println("size=" + size + ", caught=" + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        allocate(-3);
        allocate(0);
        allocate(2);

        int[] values = {-3};
        System.out.println("Phần tử âm: " + values[0]);

        int[] empty = new int[0];
        System.out.println("Mảng rỗng: " + empty.length);
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 NegativeArraySizeDemo.java
java NegativeArraySizeDemo
~~~

Stdout chính xác:

~~~text
size=-3, caught=NegativeArraySizeException
size=0, length=0
size=2, length=2
Phần tử âm: -3
Mảng rỗng: 0
~~~

### Phân tích

1. `allocate(-3)` tính biểu thức độ dài thành `-3`; Java ném `NegativeArraySizeException` ngay tại array creation expression.
2. `catch` bắt đúng ngoại lệ. Chương trình không in `numbers.length` trên đường thất bại, vì không có mảng được tạo thành công.
3. `allocate(0)` tạo mảng rỗng và in `length=0`.
4. `allocate(2)` tạo mảng hai phần tử.
5. `int[] values = {-3}` chỉ chứa một phần tử âm, không có kích thước âm.
6. `new int[0]` vẫn trả về một đối tượng array hợp lệ.

Trong hệ thống thật, nên xem **giá trị `size` đến từ đâu**, chứ không chỉ viết `catch (NegativeArraySizeException)` rồi tiếp tục bằng cách trả mảng rỗng. Việc silently coi yêu cầu độ dài âm là “không có dữ liệu” thường che giấu lỗi dữ liệu hoặc lỗi tính toán.

## 5. Ngoại lệ xuất hiện lúc nào: compile time hay runtime?

Java cho phép viết biểu thức:

~~~java
int[] numbers = new int[-1];
~~~

Mặc dù `-1` là một hằng số rõ ràng âm, cú pháp khai báo mảng này vẫn **biên dịch hợp lệ** theo Java 17. Khi thực thi biểu thức khởi tạo, JVM sẽ ném `NegativeArraySizeException`.

Đây không phải một lỗi kiểu (`type mismatch`) hoặc một lỗi cú pháp. Biểu thức `-1` có kiểu `int` phù hợp với yêu cầu của biểu thức kích thước. Giá trị không hợp lệ được phát hiện ở bước thực thi việc tạo mảng.

Phân biệt với lỗi compile time:

~~~java
// CỐ Ý SAI: yêu cầu kiểu số nguyên phù hợp cho array dimension.
int[] bad = new int[3.5];
~~~

Ở đây, `3.5` có kiểu `double`, không thể được sử dụng trực tiếp làm array dimension hợp lệ. Compiler từ chối vì **kiểu không phù hợp**, không phải vì kích thước âm.

Một biểu thức kiểu `long` cũng không được sử dụng trực tiếp như `new int[someLong]` nếu không có phép chuyển kiểu phù hợp. Việc ép kiểu `long` sang `int` có thể làm mất dữ liệu, nên chỉ cast sau khi kiểm tra miền giá trị.

## 6. Mảng nhiều chiều: bất kỳ kích thước âm đều là lỗi

Java hỗ trợ tạo mảng nhiều chiều theo cách khai báo:

~~~java
int[][] matrix = new int[2][3];
~~~

Trong ví dụ này, Java tạo một mảng ngoài có hai phần tử, mỗi phần tử là một tham chiếu tới mảng `int` dài ba. Java dùng **mảng của các mảng**, không yêu cầu mọi chiều có cùng độ dài khi bạn gán lại từng hàng.

Những trường hợp gây lỗi:

~~~java
int[][] a = new int[-1][3]; // Sai: chiều ngoài âm.
int[][] b = new int[2][-3]; // Sai: chiều trong âm.
int[][] c = new int[0][-1]; // Sai: chiều trong âm dù chiều ngoài bằng 0.
~~~

Điểm thứ ba dễ gây bất ngờ. Không thể suy luận rằng chiều đầu `0` làm Java bỏ qua mọi kiểm tra kích thước còn lại. **Mọi biểu thức kích thước được viết trong array creation expression đều được đánh giá và kiểm tra theo ngữ nghĩa tạo mảng**.

Ngược lại:

~~~java
int[][] rows = new int[2][];
System.out.println(rows.length);  // 2
System.out.println(rows[0]);      // null
~~~

Cách viết này hợp lệ. Chỉ chiều thứ nhất được cấp phát tại thời điểm đó; hai phần tử trong mảng ngoài ban đầu là `null`, và từng mảng con có thể được tạo/gán sau. Đây là cơ sở của **jagged arrays** (mảng răng cưa).

~~~java
rows[0] = new int[2];
rows[1] = new int[5];
System.out.println(rows[0].length); // 2
System.out.println(rows[1].length); // 5
~~~

Nếu bạn dùng `rows[0]` trước khi gán một mảng con, lỗi có thể là `NullPointerException`, không phải `NegativeArraySizeException`.

## 7. Thứ tự đánh giá biểu thức kích thước: một điểm quan trọng

Với biểu thức:

~~~java
int[][] array = new int[first()][second()];
~~~

Java **đánh giá các biểu thức kích thước từ trái sang phải**, trước khi tiến hành kiểm tra kích thước âm và cấp phát theo ngữ nghĩa array creation. Vì vậy **side effect** trong `second()` vẫn có thể xảy ra ngay cả khi `first()` trả số âm.

Nếu `second()` **tự ném một ngoại lệ**, ngoại lệ của `second()` xuất hiện trước bước kiểm tra kích thước âm; `NegativeArraySizeException` của giá trị âm ở `first()` sẽ không được ném trong lần đánh giá đó.

Tình huống này không có nghĩa giá trị âm trở nên hợp lệ. Nó có nghĩa **đánh giá biểu thức đã kết thúc bất thường trước khi đến bước kiểm tra giá trị**.

Sơ đồ:

~~~text
Đánh giá first()
    ↓
Nếu first() ném → dừng, truyền lỗi first()
    ↓ nếu trả bình thường
Đánh giá second()
    ↓
Nếu second() ném → dừng, truyền lỗi second()
    ↓ nếu trả bình thường
Kiểm tra các giá trị kích thước đã đánh giá
    ↓
Nếu có giá trị âm → NegativeArraySizeException
    ↓ nếu đều không âm
Cấp phát các mảng phù hợp
~~~

Không nên phụ thuộc vào side effect ở biểu thức kích thước để điều khiển logic nghiệp vụ. Cách dễ đọc và kiểm thử hơn là tính toán các biến độ dài, xác thực chúng, rồi mới tạo mảng.

## 8. Java 17+: kiểm chứng thứ tự đánh giá và mảng nhiều chiều

Tệp `DimensionEvaluationDemo.java`:

~~~java
public class DimensionEvaluationDemo {
    static int first() {
        System.out.println("Đánh giá chiều 1");
        return -1;
    }

    static int second() {
        System.out.println("Đánh giá chiều 2");
        return 2;
    }

    static int fails() {
        System.out.println("Chiều 2 ném lỗi");
        throw new IllegalStateException("lỗi biểu thức");
    }

    public static void main(String[] args) {
        try {
            int[][] numbers = new int[first()][second()];
            System.out.println(numbers.length);
        } catch (NegativeArraySizeException ex) {
            System.out.println("Bắt kích thước âm");
        }

        try {
            int[][] numbers = new int[first()][fails()];
            System.out.println(numbers.length);
        } catch (IllegalStateException ex) {
            System.out.println("Bắt lỗi biểu thức: " + ex.getMessage());
        }

        try {
            int[][] numbers = new int[0][-2];
            System.out.println(numbers.length);
        } catch (NegativeArraySizeException ex) {
            System.out.println("Chiều sau âm dù chiều đầu bằng 0");
        }

        int[][] rows = new int[2][];
        System.out.println("Mảng ngoài: " + rows.length
                + ", hàng đầu null: " + (rows[0] == null));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 -encoding UTF-8 DimensionEvaluationDemo.java
java DimensionEvaluationDemo
~~~

Stdout chính xác:

~~~text
Đánh giá chiều 1
Đánh giá chiều 2
Bắt kích thước âm
Đánh giá chiều 1
Chiều 2 ném lỗi
Bắt lỗi biểu thức: lỗi biểu thức
Chiều sau âm dù chiều đầu bằng 0
Mảng ngoài: 2, hàng đầu null: true
~~~

**Các kết luận được chứng minh:**

- Đường thứ nhất đánh giá cả `first()` và `second()`; dù `first()` trả `-1`, `second()` vẫn được gọi, sau đó Java ném `NegativeArraySizeException`.
- Đường thứ hai đánh giá `first()`, rồi `fails()` ném `IllegalStateException`. Không đến bước báo kích thước âm.
- `new int[0][-2]` thất bại vì kích thước chiều thứ hai âm.
- `new int[2][]` hợp lệ, nhưng chưa tạo các mảng con.

Nếu viết nhiều chiều hơn, cùng nguyên tắc áp dụng cho các biểu thức kích thước được chỉ định. Đừng nhầm các chiều **chưa được khai báo kích thước** (`[]` trống ở đuôi) với chiều được khai báo có giá trị `0`.

## 9. `NegativeArraySizeException` và `ArrayIndexOutOfBoundsException` khác nhau

Hai lỗi đều có thể liên quan đến mảng nhưng xảy ra ở **hai giai đoạn khác nhau**:

| Lỗi | Khi nào? | Ví dụ |
| --- | --- | --- |
| `NegativeArraySizeException` | **Tạo mảng** với một biểu thức kích thước âm | `new int[-1]` |
| `ArrayIndexOutOfBoundsException` | **Truy cập mảng đã có** với chỉ số nằm ngoài `[0, length)` | `new int[2]` rồi đọc `a[2]` |
| `NullPointerException` | Dùng tham chiếu mảng đang là `null` | `int[] a = null; a[0]` |

Các ví dụ là mảnh code minh họa, không phải chương trình độc lập. `NegativeArraySizeException` kế thừa trực tiếp `RuntimeException`, còn `ArrayIndexOutOfBoundsException` kế thừa `IndexOutOfBoundsException`; do đó `catch (IndexOutOfBoundsException)` **không bắt lỗi kích thước âm**. Lỗi truy cập chỉ số và các quy tắc `[from, to)` là trọng tâm của bài về bounds; tại đây cần truy ngược **giá trị được tính ra cho kích thước lúc tạo mảng**, kể cả khả năng `int` overflow.

## 10. Reflection: `Array.newInstance` cũng có thể ném ngoại lệ

Java hỗ trợ tạo array qua reflection:

~~~java
Object array = java.lang.reflect.Array.newInstance(String.class, 3);
~~~

Khi sử dụng `Array.newInstance(componentType, length)`, nếu `length` âm, API cũng có thể ném `NegativeArraySizeException`. Với dạng nhiều chiều nhận các kích thước, một kích thước âm cũng là lỗi.

Điều này hữu ích trong framework, serializer, thư viện xử lý bảng động hoặc những phần code chỉ biết component type tại runtime. **Không chỉ cú pháp `new T[n]` mới gặp lỗi kích thước âm.**

Nhưng phải đọc hợp đồng của API cụ thể. Ví dụ một `ArrayList` không phải Java array ở mức runtime; constructor `new ArrayList<>(-1)` sử dụng quy tắc tham số capacity và có thể ném **`IllegalArgumentException`**, không phải `NegativeArraySizeException`.

## 11. Java 17+: reflection, ArrayList và lỗi index

Tệp `ReflectionAndIndexDemo.java`:

~~~java
import java.lang.reflect.Array;
import java.util.ArrayList;

public class ReflectionAndIndexDemo {
    public static void main(String[] args) {
        try {
            Array.newInstance(String.class, -5);
        } catch (NegativeArraySizeException ex) {
            System.out.println("Reflection: " + ex.getClass().getSimpleName());
        }

        try {
            Array.newInstance(int.class, 2, -3);
        } catch (NegativeArraySizeException ex) {
            System.out.println("Reflection nhiều chiều: "
                    + ex.getClass().getSimpleName());
        }

        try {
            new ArrayList<String>(-1);
        } catch (IllegalArgumentException ex) {
            System.out.println("ArrayList capacity: " + ex.getClass().getSimpleName());
        }

        int[] numbers = new int[2];
        try {
            System.out.println(numbers[-1]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("Index: " + ex.getClass().getSimpleName());
        }
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 ReflectionAndIndexDemo.java
java ReflectionAndIndexDemo
~~~

Stdout chính xác:

~~~text
Reflection: NegativeArraySizeException
Reflection nhiều chiều: NegativeArraySizeException
ArrayList capacity: IllegalArgumentException
Index: ArrayIndexOutOfBoundsException
~~~

Từ đó thấy **không thể chỉ nhìn một tham số âm để đoán đúng loại exception**. Phải biết thao tác cụ thể:

- Tạo Java array kích thước âm → `NegativeArraySizeException`.
- Tạo `ArrayList` với capacity âm → `IllegalArgumentException` theo hợp đồng constructor `ArrayList(int)`.
- Truy cập phần tử array bằng index âm → `ArrayIndexOutOfBoundsException`.

`NegativeArraySizeException` phát sinh từ thao tác cấp phát mảng, không phải loại lỗi tổng quát của mọi collection.

## 12. Nguồn phát sinh thường gặp: độ dài tính sai

Trong sản phẩm thật, ít người cố ý viết `new int[-1]`. Lỗi thường xảy ra khi **biểu thức kích thước** bị âm ngoài dự kiến.

### 12.1. Trừ nhầm một đơn vị

~~~java
int remaining = endExclusive - startInclusive;
int[] data = new int[remaining];
~~~

Nếu `endExclusive < startInclusive`, `remaining` âm. Cần xác định hợp đồng của cặp chỉ số:

- Có cho phép đoạn rỗng (`startInclusive == endExclusive`) không?
- Có cho phép `startInclusive > endExclusive` không?
- Hai giá trị có nằm trong giới hạn nguồn dữ liệu không?
- Phép trừ có nguy cơ **tràn số `int`** không?

Không phải cứ `Math.max(0, remaining)` là sửa đúng. Nếu cặp chỉ số bị đảo vì bug, ép độ dài về 0 sẽ **giấu lỗi và thay đổi ngữ nghĩa**.

### 12.2. Giá trị sentinel `-1`

Nhiều API dùng `-1` làm giá trị đặc biệt, ví dụ “không tìm thấy”, “không xác định” hoặc “EOF” tùy hợp đồng. Nếu bạn đưa giá trị đó trực tiếp vào `new byte[count]`, bạn đang dùng **mã trạng thái như kích thước mảng**.

~~~java
int count = -1; // Minh họa sentinel "chưa xác định".
byte[] payload = new byte[count]; // Sai nếu không chuyển trạng thái.
~~~

Cần xử lý `-1` theo ý nghĩa của API nguồn **trước khi** dùng làm độ dài. Không tự động coi `-1` là mảng rỗng.

### 12.3. Giá trị từ cấu hình hoặc người dùng

~~~java
int configuredSize = Integer.parseInt(input);
byte[] buffer = new byte[configuredSize];
~~~

Trong đoạn minh họa này, `input` là biến ở phạm vi chứa. Có hai đường lỗi khác nhau:

- Nếu chuỗi không phân tích được thành số nguyên → có thể là `NumberFormatException`.
- Nếu chuỗi phân tích thành `-7` → `new byte[-7]` có thể ném `NegativeArraySizeException`.

Nếu cấu hình quá lớn dù dương, cấp phát vẫn có thể gặp vấn đề bộ nhớ. Vì vậy validation phải bao gồm **cận dưới và cận trên**, không chỉ kiểm tra `>=0`.

### 12.4. Kích thước suy ra từ phép nhân

~~~java
int totalCells = rows * columns;
int[] matrix = new int[totalCells];
~~~

Cả `rows` và `columns` có thể dương nhưng phép nhân `int` bị tràn, tạo ra giá trị âm hoặc số dương sai. Do đó kiểm tra `totalCells >= 0` **sau khi nhân** chưa đủ bảo đảm phép tính đúng.

## 13. Tràn số nguyên: giá trị âm không phải luôn từ input âm

Số nguyên `int` trong Java có miền từ `-2_147_483_648` đến `2_147_483_647`. Phép cộng số nguyên thông thường có thể bị tràn, và Java không tự ném `ArithmeticException` cho mọi phép cộng tràn.

Ví dụ:

~~~java
int max = Integer.MAX_VALUE;
int overflowed = max + 1;
System.out.println(overflowed); // -2147483648
byte[] data = new byte[overflowed]; // NegativeArraySizeException.
~~~

Hai số đầu vào có thể không âm, nhưng kết quả trung gian âm vì overflow.

Sử dụng các phương thức kiểm tra overflow khi thích hợp:

~~~java
int safeSum = Math.addExact(a, b);
int safeProduct = Math.multiplyExact(a, b);
int narrowed = Math.toIntExact(longLength);
~~~

Đây là **đoạn minh họa**, các biến `a`, `b`, `longLength` cần được khai báo. Các phương thức trên ném `ArithmeticException` khi phép tính/ép hẹp không thể biểu diễn chính xác trong kiểu kết quả. Chúng **không cấp phát bộ nhớ** và không tự xác thực giới hạn nghiệp vụ; bạn vẫn cần kiểm tra độ dài tối đa hợp lý.

Đối với số lượng tính từ hai chỉ số, dùng `long` cho bước trung gian có thể giúp tránh overflow `int`:

~~~java
long length = (long) endExclusive - startInclusive;
if (length < 0 || length > MAX_ALLOWED) {
    throw new IllegalArgumentException("Khoảng dữ liệu không hợp lệ");
}
int[] data = new int[Math.toIntExact(length)];
~~~

Đây là **đoạn minh họa trong phương thức** có `endExclusive`, `startInclusive`, `MAX_ALLOWED` được khai báo hợp lệ. Cách làm phân biệt rõ **kiểm tra miền giá trị** và **cấp phát**.

## 14. Java 17+: overflow và xác thực kích thước trước khi tạo mảng

Tệp `LengthOverflowDemo.java`:

~~~java
public class LengthOverflowDemo {
    static int validatedLength(long length) {
        if (length < 0 || length > 1_000_000) {
            throw new IllegalArgumentException("độ dài ngoài miền cho phép");
        }
        return Math.toIntExact(length);
    }

    public static void main(String[] args) {
        int max = Integer.MAX_VALUE;
        int overflowed = max + 1;
        System.out.println("Sau tràn số: " + overflowed);
        try {
            byte[] bytes = new byte[overflowed];
            System.out.println(bytes.length);
        } catch (NegativeArraySizeException ex) {
            System.out.println("Cấp phát: NegativeArraySizeException");
        }

        try {
            Math.addExact(max, 1);
        } catch (ArithmeticException ex) {
            System.out.println("addExact: ArithmeticException");
        }

        for (long candidate : new long[] {-2, 0, 2, 2_147_483_648L}) {
            try {
                byte[] data = new byte[validatedLength(candidate)];
                System.out.println("candidate=" + candidate + ", length=" + data.length);
            } catch (IllegalArgumentException ex) {
                System.out.println("candidate=" + candidate + ", invalid");
            }
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 -encoding UTF-8 LengthOverflowDemo.java
java LengthOverflowDemo
~~~

Stdout chính xác:

~~~text
Sau tràn số: -2147483648
Cấp phát: NegativeArraySizeException
addExact: ArithmeticException
candidate=-2, invalid
candidate=0, length=0
candidate=2, length=2
candidate=2147483648, invalid
~~~

Giải thích:

1. `Integer.MAX_VALUE + 1` tràn thành `Integer.MIN_VALUE`, tức `-2147483648`.
2. Dùng giá trị đó làm kích thước mảng ném `NegativeArraySizeException`.
3. `Math.addExact(max,1)` **phát hiện overflow** và ném `ArithmeticException`, giúp lỗi được nhận diện ở **phép tính** trước khi đến bước cấp phát.
4. `validatedLength` chặn số âm và số vượt giới hạn của **ví dụ** (1.000.000), rồi dùng `Math.toIntExact`.
5. `0` và `2` hợp lệ, tạo mảng có độ dài tương ứng.
6. `2_147_483_648L` vượt miền `int` và giới hạn của ứng dụng; hàm xác thực báo `IllegalArgumentException` thay vì để cast bị mất dữ liệu.

Giới hạn 1.000.000 trong ví dụ **không phải giới hạn cố định của mọi Java array**; đó là chính sách kích thước có chủ đích. Một chương trình cần lựa chọn ngưỡng theo yêu cầu và ngân sách bộ nhớ của nó.

## 15. Kích thước dương lớn có thể gặp lỗi khác

`NegativeArraySizeException` chỉ nói rằng **một kích thước âm** đã được sử dụng. Không thể kết luận kích thước dương là chắc chắn tạo được mảng.

Ví dụ:

~~~java
// KHÔNG chạy tùy tiện: có thể yêu cầu lượng bộ nhớ rất lớn.
// byte[] huge = new byte[Integer.MAX_VALUE];
~~~

Kết quả phụ thuộc khả năng cấp phát của JVM, memory heap, giới hạn kích thước array và môi trường chạy. Với yêu cầu quá lớn, có thể xuất hiện `OutOfMemoryError` hoặc thông báo liên quan tới giới hạn kích thước mảng. **`OutOfMemoryError` là nhánh `Error`, không phải `NegativeArraySizeException`**.

Đừng viết test sản phẩm cố cấp phát mảng cực lớn chỉ để “xem lỗi gì” trên một môi trường dùng chung; có thể làm JVM chậm, thu hồi bộ nhớ liên tục hoặc hết tài nguyên.

Chính sách tốt:

- Xác định **giới hạn kích thước hợp lý** của bài toán.
- Kiểm tra `length >= 0` và `length <= maxAllowed`.
- Kiểm tra overflow trong phép cộng/nhân tạo ra độ dài.
- Với dữ liệu lớn, xem xét **streaming, chia khối/batching** hoặc cấu trúc dữ liệu không cần cấp một mảng liên tục rất lớn.
- Không dựa vào việc catch `OutOfMemoryError` như giải pháp quản lý tài nguyên bình thường.

## 16. Có nên `catch (NegativeArraySizeException)` trong ứng dụng thật?

Về mặt cú pháp, bạn có thể bắt trực tiếp:

~~~java
try {
    int[] data = new int[requestedSize];
    use(data);
} catch (NegativeArraySizeException ex) {
    logInvalidSize(requestedSize, ex);
}
~~~

Đoạn trên là **minh họa** với `requestedSize`, `use`, `logInvalidSize` thuộc ngữ cảnh chứa. Nhưng cần đánh giá mục đích:

- Nếu độ dài âm có thể do **input không tin cậy**, nên validate input **trước khi cấp phát**, chọn loại lỗi đầu vào có ý nghĩa với caller.
- Nếu độ dài âm do **bug tính toán nội bộ**, bắt rồi bỏ qua thường che bug. Nên log đủ ngữ cảnh và sửa phép tính.
- Nếu đang triển khai một **boundary nhận dữ liệu tùy ý** hoặc công cụ kiểm thử, bắt `NegativeArraySizeException` để chuyển thành thông báo an toàn có thể hợp lý.
- Nếu catch ngoại lệ rồi tự trả mảng rỗng, cần chắc chắn đó là **quy tắc nghiệp vụ được quy định**, không phải cách che trạng thái sai.
- Không được đồng nhất loại ngoại lệ unchecked với lỗi có thể tùy tiện bỏ qua.

Ví dụ tốt hơn ở tầng validation:

~~~java
static byte[] allocateBuffer(long requested) {
    final int maxAllowed = 1_000_000;
    if (requested < 0 || requested > maxAllowed) {
        throw new IllegalArgumentException(
                "requested phải trong [0, " + maxAllowed + "]");
    }
    return new byte[Math.toIntExact(requested)];
}
~~~

Đây là **phương thức Java có thể biên dịch** khi đặt trong lớp. Nó xác định rõ điều kiện đầu vào và giới hạn nghiệp vụ trước khi khởi tạo. `IllegalArgumentException` giúp caller thấy **hợp đồng phương thức bị vi phạm**, thay vì phụ thuộc vào lỗi cấp phát phát sinh sâu bên trong.

## 17. `throw` thủ công và ngoại lệ do runtime tạo

Bạn có thể tự ném đối tượng:

~~~java
throw new NegativeArraySizeException("Kích thước không hợp lệ: " + requested);
~~~

Cú pháp đó hợp lệ khi `requested` tồn tại. Nhưng đối với **validation của API**, một `IllegalArgumentException` chuẩn hoặc exception domain cụ thể thường rõ nghĩa hơn. `NegativeArraySizeException` đặc biệt nói rằng điều kiện gây lỗi liên quan tới **việc tạo mảng**, nên đặt loại đó cho một lỗi không liên quan sẽ gây khó hiểu.

`NegativeArraySizeException` được runtime tạo ra khi array creation expression gặp kích thước âm có thể có message khác với ngoại lệ bạn tự tạo. **Không parse `getMessage()`** để suy ra kích thước hoặc khôi phục dữ liệu; message không nên được coi như API cấu trúc ổn định.

Nếu cần giá trị kích thước âm để điều tra, ghi log input và bước tính toán liên quan, nhưng bảo đảm không đưa dữ liệu nhạy cảm ra log ngoài chính sách.

## 18. So sánh các lỗi thường gặp quanh array

| Tình huống | Ngoại lệ/lỗi điển hình |
| --- | --- |
| `new int[-4]` | `NegativeArraySizeException` |
| `new int[0]` | Thành công, mảng rỗng |
| `new String[2][-1]` | `NegativeArraySizeException` |
| `new int[2][]` | Thành công, các mảng con chưa tạo (ban đầu `null`) |
| `int[] a = new int[2]; a[-1]` | `ArrayIndexOutOfBoundsException` |
| `int[] a = new int[2]; a[2]` | `ArrayIndexOutOfBoundsException` |
| `int[] a = null; a[0]` | `NullPointerException` |
| `Integer.parseInt("abc")` | `NumberFormatException` |
| `new ArrayList<>(-1)` | `IllegalArgumentException` |
| `Math.addExact(Integer.MAX_VALUE,1)` | `ArithmeticException` |
| `new byte[giá_trị_dương_rất_lớn]` | Có thể gặp `OutOfMemoryError`; không suy ra chắc chắn kết quả ở mọi JVM |
| `Array.newInstance(int.class, -2)` | `NegativeArraySizeException` |

Bảng giả định các biểu thức được thực thi và không có một ngoại lệ khác xuất hiện trước bước quan tâm; ví dụ một hàm tính độ dài có side effect và tự ném trước khi hoàn tất có thể tạo loại lỗi khác.

## 19. Xác định bug bằng cách lần ngược giá trị kích thước

Khi gặp stack trace có `NegativeArraySizeException`, hãy truy ngược từ nơi tạo array:

1. **Tìm array creation expression**: `new byte[size]`, `new T[n][m]`, `Array.newInstance(...)` hay API gián tiếp?
2. **Ghi lại giá trị kích thước thực tế** (nếu có thể làm an toàn), gồm từng chiều của mảng đa chiều.
3. **Theo dấu nguồn dữ liệu**: tham số, cấu hình, số dòng đọc được, phép trừ chỉ số, sentinel `-1`, kết quả parser hay phép toán?
4. **Kiểm tra overflow**: phép nhân/tổng độ dài có thể vượt `int` không? Có cast `long → int` không?
5. **Kiểm tra logic độ dài bằng 0**: trường hợp rỗng có được phép không, hay là dấu hiệu dữ liệu lỗi?
6. **Kiểm tra thứ tự đánh giá các dimension expressions**, nếu có nhiều chiều hoặc side effect.
7. **Bổ sung validation ở boundary thích hợp** trước khi cấp phát.
8. **Viết test cho các miền biên**: âm, 0, 1, giới hạn tối đa cho phép, và giá trị vượt giới hạn.
9. Nếu mảng được tạo trong constructor/factory, xem xét liệu **trạng thái/tài nguyên đã cấp phát trước điểm lỗi** được xử lý đúng hay không.
10. Không thay mọi giá trị âm thành `0` khi chưa biết tại sao âm xuất hiện.

Với bài toán tính độ dài đoạn `[start,end)`, có thể assert `start <= end` rồi kiểm tra phạm vi; với kích thước buffer phụ thuộc payload, cần kiểm soát payload tối đa. Điều quan trọng là **mối quan hệ nghiệp vụ**, không chỉ cú pháp `new int[n]`.

## 20. Hướng dẫn kiểm thử các trường hợp biên

Một tập test hợp lý:

| Input kích thước | Kỳ vọng của array allocation trực tiếp | Kỳ vọng của API có validation |
| --- | --- | --- |
| `-1` | `NegativeArraySizeException` | Lỗi đầu vào theo hợp đồng, ví dụ `IllegalArgumentException` |
| `0` | Thành công, `length=0` | Thành công nếu ứng dụng cho phép dữ liệu rỗng |
| `1` | Thành công, `length=1` | Thành công |
| `maxAllowed` | Có thể tạo nếu cấp phát đủ bộ nhớ | Thành công nếu tài nguyên cho phép |
| `maxAllowed+1` | Có thể tạo hoặc không, tùy số cụ thể và môi trường | Bị từ chối theo hợp đồng trước khi cấp phát |
| Giá trị sau overflow thành âm | `NegativeArraySizeException` | Nên phát hiện overflow ở phép tính trước |
| Đầu vào không phân tích thành số | Lỗi parser trước array allocation | Lỗi dữ liệu có thông báo/mã phù hợp |

Trong test unit, đừng cố cấp phát mảng sát giới hạn JVM; thay bằng **giới hạn ứng dụng nhỏ** để kiểm thử validation ổn định và không ngốn heap.

Có thể kiểm tra cả **loại exception** và **thông tin lỗi được công bố**, không cần assert message được tạo bởi JVM:

~~~java
// Ví dụ JUnit-style minh họa ý định; cần thư viện JUnit nếu chạy.
assertThrows(NegativeArraySizeException.class, () -> new int[-1]);
~~~

Đoạn này chỉ mô tả ý tưởng kiểm thử, **không phải chương trình Java 17 độc lập**, và cần kiểm tra ngữ cảnh lambda/method reference theo API test cụ thể. Các chương trình Java 17 ở mục 4, 8, 11 và 14 là các ví dụ hoàn chỉnh không cần phụ thuộc bên ngoài.

## 21. Những hiểu nhầm phổ biến

| Hiểu nhầm | Sự thật |
| --- | --- |
| “`new int[-1]` không biên dịch.” | Biên dịch được; lỗi phát sinh lúc chạy khi biểu thức cấp phát được đánh giá. |
| “`new int[0]` cũng bị lỗi vì không có phần tử.” | Không. Kích thước 0 hợp lệ. |
| “Có một phần tử âm thì mảng không hợp lệ.” | Không. `int[] a={-5}` hoàn toàn hợp lệ. |
| “Chiều đầu 0 nên các chiều sau không được kiểm tra.” | Sai. `new int[0][-1]` vẫn ném `NegativeArraySizeException`. |
| “Nếu chiều đầu âm thì Java không gọi biểu thức của chiều sau.” | Sai khi chiều đầu **trả bình thường**: Java tiếp tục đánh giá các dimension expressions trước khi kiểm tra âm. |
| “Dimension expression sau ném lỗi nhưng vẫn bắt `NegativeArraySizeException` của dimension đầu.” | Lỗi từ biểu thức phía sau có thể kết thúc đánh giá trước bước kiểm tra kích thước âm. |
| “`NegativeArraySizeException` là subclass của `IndexOutOfBoundsException`.” | Không; superclass trực tiếp là `RuntimeException`. |
| “Index âm và size âm là cùng một lỗi.” | Truy cập index âm thường là `ArrayIndexOutOfBoundsException`; cấp phát size âm là `NegativeArraySizeException`. |
| “`new ArrayList<>(-1)` cũng ném `NegativeArraySizeException`.” | Constructor `ArrayList(int)` ném `IllegalArgumentException` cho capacity âm. |
| “Không bao giờ có size âm nếu input ban đầu dương.” | Overflow hoặc phép tính sai vẫn có thể tạo số âm. |
| “Chỉ cần ép `Math.max(0,size)` là luôn đúng.” | Có thể che bug, làm sai ngữ nghĩa và mất dữ liệu. |
| “Giá trị dương chắc chắn cấp phát thành công.” | Kích thước lớn vẫn có thể vượt ngân sách bộ nhớ/giới hạn JVM. |
| “Chỉ cần catch exception là đã khắc phục.” | Nếu size được tính sai, cần sửa nguồn và kiểm tra điều kiện. |
| “Thông điệp `getMessage()` của lỗi runtime là API ổn định.” | Không nên phụ thuộc vào cấu trúc message cụ thể do JVM tạo. |

## 22. Câu hỏi tự kiểm tra

**Câu 1.** `new int[-2]` gây lỗi gì và ở thời điểm nào?

**Đáp án:** `NegativeArraySizeException` tại runtime khi biểu thức tạo mảng được thực thi.

**Câu 2.** `new int[0]` có hợp lệ không?

**Đáp án:** Có. Mảng rỗng có `length=0`.

**Câu 3.** `int[] x={-2}` có ném `NegativeArraySizeException` không?

**Đáp án:** Không. `-2` là giá trị phần tử, mảng dài 1.

**Câu 4.** `new int[0][-1]` có hợp lệ không?

**Đáp án:** Không. Kích thước âm của chiều thứ hai vẫn bị kiểm tra.

**Câu 5.** Với `new int[first()][second()]`, `first()` trả `-1` còn `second()` có side effect nhưng trả `2`, `second()` có chạy không?

**Đáp án:** Có. Java đánh giá các biểu thức kích thước theo thứ tự trái sang phải trước khi kiểm tra giá trị âm.

**Câu 6.** Nếu `second()` ném `IllegalStateException`, Java có nhất thiết ném thêm `NegativeArraySizeException` vì `first()=-1` không?

**Đáp án:** Không. Việc đánh giá biểu thức đã kết thúc bất thường ở `second()`.

**Câu 7.** `int[] a=new int[2]; a[-1]` ném gì?

**Đáp án:** `ArrayIndexOutOfBoundsException`, do truy cập phần tử ở index không hợp lệ.

**Câu 8.** `new ArrayList<String>(-1)` có ném `NegativeArraySizeException` không?

**Đáp án:** Không. Constructor capacity của `ArrayList` ném `IllegalArgumentException`.

**Câu 9.** `Math.addExact(Integer.MAX_VALUE,1)` ném gì?

**Đáp án:** `ArithmeticException` do phép cộng tràn `int`.

**Câu 10.** Nếu một buffer cần kích thước lấy từ `long`, làm sao phòng lỗi do ép hẹp?

**Đáp án:** Kiểm tra giá trị nằm trong miền hợp lệ và ngân sách bộ nhớ, rồi dùng `Math.toIntExact` hoặc chuyển kiểu sau validation có kiểm soát.

**Câu 11.** `NegativeArraySizeException` có bắt buộc khai báo `throws` không?

**Đáp án:** Không, vì đây là unchecked exception.

**Câu 12.** Vì sao không nên chỉ viết `catch (NegativeArraySizeException)` rồi trả mảng rỗng?

**Đáp án:** Vì có thể che lỗi input hoặc tính toán; giá trị âm thường cần được nhận diện và xử lý đúng ngữ nghĩa ở nguồn tạo kích thước.

## 23. Tài liệu tham khảo chính thức

- [Java SE 17 API — `NegativeArraySizeException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NegativeArraySizeException.html): phân cấp, điều kiện phát sinh và constructors.
- [Java Language Specification 17 — §15.10.1, Array Creation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.10.1): cú pháp tạo mảng.
- [Java Language Specification 17 — §15.10.2, Run-Time Evaluation of Array Creation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.10.2): thứ tự đánh giá các dimension expressions và kiểm tra kích thước âm.
- [Java Language Specification 17 — §10, Arrays](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html): mảng, `length`, kiểu thành phần và cấu trúc đa chiều.
- [Java SE 17 API — `java.lang.reflect.Array`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Array.html): `newInstance` và các thao tác reflection với array.
- [Java SE 17 API — `ArrayList`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html): phân biệt hợp đồng initial capacity với kích thước Java array.
- [Java SE 17 API — `Math`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `addExact`, `multiplyExact`, `toIntExact` để kiểm tra overflow.
- [Java SE 17 API — `ArrayIndexOutOfBoundsException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ArrayIndexOutOfBoundsException.html): lỗi truy cập chỉ số, khác lỗi cấp phát.

**Kết luận:** `NegativeArraySizeException` xuất hiện khi **kích thước dùng để tạo Java array là số âm**, bao gồm mảng nhiều chiều và các API tạo mảng qua reflection. Độ dài `0` hợp lệ; giá trị phần tử âm cũng hợp lệ. Để tránh lỗi, hãy **xác thực nguồn dữ liệu, tính toán độ dài không tràn số, kiểm tra giới hạn cấp phát và phân biệt rõ lỗi kích thước với lỗi truy cập index**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-runtime-exceptions-009">NoSuchElementException và truy cập khi không có phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# `NoSuchElementException` và truy cập khi không có phần tử trong Java

Trong Java, `java.util.NoSuchElementException` là một **unchecked exception** dùng để báo rằng một thao tác **yêu cầu lấy phần tử nhưng không còn phần tử phù hợp để trả về**. Nó thường xuất hiện khi gọi `Iterator.next()` sau phần tử cuối, `Scanner.next()` lúc không còn token, `Optional.get()` khi `Optional` rỗng, hoặc `Queue.remove()` trên hàng đợi trống.

Điểm cốt lõi không phải mọi collection trống đều tự ném `NoSuchElementException`. **Chỉ lời gọi tới một API có hợp đồng lấy phần tử theo kiểu “không có thì ném” mới phát sinh ngoại lệ này**, và từng API có lựa chọn an toàn tương ứng: `hasNext()`, `hasNextLine()`, `poll()`, `peek()`, `isPresent()`, `orElseThrow(...)` hoặc một kiểu kết quả khác.

Bài học làm rõ **lúc nào `NoSuchElementException` xuất hiện, kiểm tra thế nào, xử lý khi nào, vì sao “đã kiểm tra rồi” vẫn có thể thất bại trong chương trình có thay đổi trạng thái, và cách lựa chọn API có hợp đồng phù hợp**. Các chương trình hoàn chỉnh dùng Java 17+ với đầu ra được ghi rõ; đoạn minh họa không độc lập được gắn nhãn riêng.

## 1. Định nghĩa, phân cấp và ý nghĩa

`NoSuchElementException` nằm trong `java.util` và có hệ phân cấp:

```text
java.lang.Object
  └── java.lang.Throwable
       └── java.lang.Exception
            └── java.lang.RuntimeException
                 └── java.util.NoSuchElementException
```

Điều này có nghĩa:

- Nó **không phải checked exception**. Compiler không bắt buộc caller `catch` hoặc khai báo `throws`.
- Nó vẫn là một exception thực sự, có thể bị `throw` và `catch`, có message và stack trace như các loại `Throwable` thông thường.
- Nếu không có handler, nó sẽ lan lên call stack và có thể làm đường xử lý đang chạy kết thúc bất thường.
- Lớp này mô tả một **lời gọi đòi hỏi phần tử nhưng phần tử được yêu cầu không tồn tại theo hợp đồng API**. Nó không tự có nghĩa “cả ứng dụng bị hỏng” hoặc “dữ liệu bị mất”.
- `NoSuchElementException` không là lớp con của `IllegalArgumentException`, `IllegalStateException` hoặc `IndexOutOfBoundsException`; các loại đó cùng thuộc những nhánh unchecked khác nhau.

Bạn cần import:

```java
import java.util.NoSuchElementException;
```

Nếu sử dụng nhiều lớp trong `java.util`, có thể import từng lớp cụ thể. Phần lớn chương trình không cần tự `new NoSuchElementException`: thư viện JDK sẽ ném nó khi một lời gọi truy xuất vượt khỏi điều kiện được cho phép.

## 2. Phân biệt “không có phần tử” với những thất bại khác

Một số tình huống trông giống nhau nhưng thuộc các hợp đồng khác:

| Tình huống | API ví dụ | Kết quả thường gặp |
| --- | --- | --- |
| Iterator đã hết nhưng vẫn gọi lấy tiếp | `Iterator.next()` | `NoSuchElementException` |
| Scanner không còn token | `Scanner.next()` | `NoSuchElementException` |
| Scanner không còn dòng | `Scanner.nextLine()` | `NoSuchElementException` |
| `Optional` rỗng nhưng gọi `get()` | `Optional.get()` | `NoSuchElementException` |
| Hàng đợi trống nhưng gọi `remove()` hoặc `element()` | `Queue` | `NoSuchElementException` |
| Hàng đợi trống và gọi `poll()` hoặc `peek()` | `Queue` | Trả `null` theo hợp đồng, không ném vì rỗng |
| Chỉ số mảng/danh sách nằm ngoài phạm vi | `List.get(index)` | `IndexOutOfBoundsException` |
| Tham chiếu object là `null` và bị dereference | `obj.method()` | `NullPointerException` |
| Scanner đang đóng và bị dùng tiếp | `Scanner.next()` | `IllegalStateException` |
| Scanner còn token nhưng không khớp kiểu yêu cầu | `Scanner.nextInt()` | `InputMismatchException` |
| Gọi `Iterator.remove()` sai trình tự | Nhiều iterator hỗ trợ remove | `IllegalStateException` |
| Collection bị sửa cấu trúc sai cách trong khi lặp | Nhiều iterator fail-fast | Có thể `ConcurrentModificationException` |

**Không dùng một `catch (RuntimeException)` để coi các tình huống này đều là “hết dữ liệu”.** Chúng có nguyên nhân và cách sửa rất khác nhau. Cách suy luận chính xác luôn bắt đầu từ **contract của phương thức đang gọi**, không chỉ từ tên collection.

## 3. Hai họ API: lấy bắt buộc và lấy có tín hiệu rỗng

Nhiều API cung cấp cả hai phong cách:

**Phong cách bắt buộc có phần tử**:

```java
E value = iterator.next();  // Nếu hết thì ném.
E head  = queue.remove();   // Nếu queue rỗng thì ném.
```

Đây là **mã khung minh họa** trong ngữ cảnh có `iterator` và `queue` được khai báo.

**Phong cách có thể không có phần tử**:

```java
if (iterator.hasNext()) {
    E value = iterator.next();
}

E head = queue.poll();  // Trả null khi queue rỗng.
```

Đây cũng là **mã khung minh họa**, chưa có class `E` hoặc phần khai báo biến đầy đủ. Điều quan trọng là lựa chọn API đã thay đổi hợp đồng giữa caller và phương thức:

- Với `next()` hoặc `remove()`, caller nói: “Tôi kỳ vọng phần tử tồn tại; nếu không, đây là lỗi đối với thao tác này”.
- Với `hasNext()` hoặc `poll()`, caller nói: “Không có phần tử là một kết quả có thể xảy ra và tôi sẽ chủ động xử lý”.

Không có một kiểu nào luôn tốt hơn. **Nếu phần tử là bất biến bắt buộc, ném lỗi có thể đúng; nếu hết dữ liệu là nhánh thường gặp, kiểm tra/trả kết quả rỗng thường rõ hơn.**

## 4. `Iterator.next()`: ví dụ kinh điển nhất

`Iterator<E>` có hai phương thức thường dùng:

- `boolean hasNext()`: có thêm phần tử để lặp hay không?
- `E next()`: trả phần tử kế tiếp; ném `NoSuchElementException` nếu không còn phần tử.

Lưu chương trình **`IteratorExhaustionDemo.java`**:

```java
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class IteratorExhaustionDemo {
    public static void main(String[] args) {
        List<String> names = Arrays.asList("An", "Bình");
        Iterator<String> iterator = names.iterator();

        System.out.println(iterator.next());
        System.out.println(iterator.next());
        System.out.println("Còn phần tử: " + iterator.hasNext());

        try {
            iterator.next();
            System.out.println("Không đến dòng này");
        } catch (NoSuchElementException e) {
            System.out.println("Đã hết phần tử");
        }
        System.out.println("Kết thúc");
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 IteratorExhaustionDemo.java
java IteratorExhaustionDemo
```

**Stdout chính xác**:

```text
An
Bình
Còn phần tử: false
Đã hết phần tử
Kết thúc
```

Sau hai lần gọi `next()` thành công, vị trí lặp đã tới cuối danh sách. `hasNext()` trả `false`; lời gọi `next()` thứ ba vi phạm hợp đồng và ném `NoSuchElementException`.

Lỗi không xảy ra khi **tạo iterator** và cũng không tự xảy ra lúc **hasNext() trả false**; nó xảy ra ở **lời gọi đòi lấy phần tử tiếp theo khi không có phần tử đó**.

## 5. Cách duyệt `Iterator` an toàn

Pattern chuẩn:

```java
while (iterator.hasNext()) {
    E item = iterator.next();
    process(item);
}
```

Đây là **mã minh họa**, các tên `E` và `process` đại diện cho kiểu và xử lý thích hợp. Nó thể hiện hợp đồng: **chỉ gọi `next()` khi điều kiện còn phần tử được xác nhận trong luồng duyệt thông thường**.

Ví dụ Java 17 **`SafeIteratorDemo.java`**:

```java
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class SafeIteratorDemo {
    public static void main(String[] args) {
        List<Integer> numbers = Arrays.asList(2, 4, 6);
        Iterator<Integer> it = numbers.iterator();
        int sum = 0;

        while (it.hasNext()) {
            sum += it.next();
        }

        System.out.println("Tổng: " + sum);
        System.out.println("Đã hết: " + !it.hasNext());
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 SafeIteratorDemo.java
java SafeIteratorDemo
```

**Stdout chính xác**:

```text
Tổng: 12
Đã hết: true
```

Ưu điểm của pattern:

- Hết dữ liệu là **điều kiện kết thúc vòng lặp**, không phải ngoại lệ dùng để điều khiển vòng lặp bình thường.
- Không cần bắt `NoSuchElementException` trong mỗi vòng.
- Người đọc dễ nhìn thấy điều kiện dừng.
- Có thể tích lũy, lọc và xử lý phần tử mà không suy đoán số phần tử ban đầu.

**Giới hạn:** điều kiện `hasNext()` và lời gọi `next()` chỉ tạo thành một pattern tin cậy khi iterator/nguồn được sử dụng theo đúng hợp đồng và không bị thay đổi bất hợp lệ trong khoảng giữa. Nó không phải lời hứa thread-safe cho mọi iterator.

## 6. Vòng lặp enhanced `for` và khi nào nên dùng

Với một đối tượng `Iterable` thông thường, vòng lặp enhanced `for` sử dụng cơ chế iterator ở bên dưới:

```java
for (String name : names) {
    System.out.println(name);
}
```

Đây là **mã minh họa** với `names` là một `Iterable<String>`. Cú pháp giúp giảm khả năng viết sai trình tự `hasNext()/next()` bằng tay và thích hợp khi chỉ cần lần lượt duyệt phần tử.

Tuy nhiên:

1. Enhanced `for` không thay đổi bản chất của iterator; nếu iterator được cài đặt sai, nó vẫn có thể gặp lỗi runtime.
2. Với collection fail-fast, sửa cấu trúc collection trực tiếp trong khi lặp có thể ném `ConcurrentModificationException`.
3. Nếu cần xóa qua iterator, API `Iterator.remove()` (khi hỗ trợ) có quy tắc riêng; không thay thế bằng cách gọi `collection.remove(...)` tùy tiện trong vòng `for`.
4. Với nguồn dữ liệu lazily loaded hoặc iterator liên quan I/O, cần xét exception, hiệu năng và vòng đời tài nguyên theo API cụ thể.

**Enhanced `for` là cách viết an toàn hơn cho việc duyệt thông thường, không phải cơ chế tự động chống mọi thay đổi đồng thời.**

## 7. Đừng nhầm `next()` hết phần tử với `remove()` sai trạng thái

`Iterator.remove()` có hợp đồng khác `next()`. Trong nhiều iterator có hỗ trợ xóa, gọi `remove()` **trước lần `next()` hợp lệ đầu tiên**, hoặc gọi `remove()` **hai lần sau cùng một `next()`**, sẽ ném `IllegalStateException`.

Ví dụ Java 17 độc lập **`IteratorStateVsElementDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class IteratorStateVsElementDemo {
    public static void main(String[] args) {
        List<String> values =
                new ArrayList<>(Arrays.asList("A"));
        Iterator<String> it = values.iterator();

        try {
            it.remove();
        } catch (IllegalStateException e) {
            System.out.println("Sai trạng thái remove");
        }

        System.out.println("next: " + it.next());
        it.remove();
        System.out.println("Danh sách: " + values);

        try {
            it.next();
        } catch (NoSuchElementException e) {
            System.out.println("Không còn phần tử next");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 IteratorStateVsElementDemo.java
java IteratorStateVsElementDemo
```

**Stdout chính xác**:

```text
Sai trạng thái remove
next: A
Danh sách: []
Không còn phần tử next
```

Ở lời gọi đầu, `remove()` sai **thứ tự sử dụng API**, không phải đang cố lấy phần tử tiếp. Sau `next()` và `remove()` thành công, iterator đã hết và lần `next()` tiếp theo mới ném `NoSuchElementException`.

Ngoài ra, một số iterator không hỗ trợ `remove()` và có thể ném `UnsupportedOperationException`. Không suy từ tên method rằng mọi iterator đều cho phép xóa.

## 8. `Scanner.next()`: token hết thì ném

`Scanner` cung cấp các phương thức đọc dữ liệu token từ nguồn:

- `hasNext()`: có token tiếp theo hay không?
- `next()`: lấy token tiếp theo; nếu hết token thì ném `NoSuchElementException`.
- `hasNextInt()` / `nextInt()`: kiểm tra/đọc token dạng `int` theo bộ phân tách và locale/radix phù hợp.
- `hasNextLine()` / `nextLine()`: kiểm tra/đọc dòng thay vì token.

Ví dụ **`ScannerEndDemo.java`**:

```java
import java.util.NoSuchElementException;
import java.util.Scanner;

public class ScannerEndDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner("alpha beta")) {
            System.out.println(scanner.next());
            System.out.println(scanner.next());
            System.out.println("hasNext: " + scanner.hasNext());

            try {
                scanner.next();
            } catch (NoSuchElementException e) {
                System.out.println("Scanner không còn token");
            }
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 ScannerEndDemo.java
java ScannerEndDemo
```

**Stdout chính xác**:

```text
alpha
beta
hasNext: false
Scanner không còn token
```

`Scanner("alpha beta")` đọc từ một chuỗi cố định, không phụ thuộc console. Sau hai token, `hasNext()` là `false`, nên `next()` bị từ chối.

Trong ví dụ, `Scanner` triển khai `AutoCloseable`, vì thế dùng `try-with-resources` để quản lý vòng đời. Nhưng lưu ý khi Scanner bọc một nguồn được chia sẻ: **đóng Scanner có thể đóng luôn nguồn bên dưới**; không tự ý đóng `Scanner(System.in)` nếu phần khác của chương trình còn cần dùng `System.in`.

## 9. `Scanner.nextLine()`: hết dòng không giống hết token

`Scanner.nextLine()` hoạt động theo **dòng**, không phải chỉ lấy token phân tách bằng whitespace. Nếu không tìm được dòng mới, nó có thể ném `NoSuchElementException`.

Ví dụ độc lập **`ScannerLineDemo.java`**:

```java
import java.util.NoSuchElementException;
import java.util.Scanner;

public class ScannerLineDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner("dòng một\ndòng hai")) {
            while (scanner.hasNextLine()) {
                System.out.println("Đọc: " + scanner.nextLine());
            }

            try {
                scanner.nextLine();
            } catch (NoSuchElementException e) {
                System.out.println("Không còn dòng");
            }
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 ScannerLineDemo.java
java ScannerLineDemo
```

**Stdout chính xác**:

```text
Đọc: dòng một
Đọc: dòng hai
Không còn dòng
```

Tách bạch:

- `hasNext()` kiểm tra **token** còn hay không.
- `hasNextLine()` kiểm tra **dòng** có thể đọc được hay không.
- `next()` đọc token.
- `nextLine()` đọc phần còn lại của dòng hiện tại, không nhất thiết tương ứng một token.

Nếu bạn vừa gọi `nextInt()` rồi `nextLine()`, phần còn lại của dòng chứa số có thể là chuỗi rỗng; đó **không nhất thiết là dấu hiệu đã hết dữ liệu**. Thiết kế parser cần thống nhất chế độ đọc token hay đọc dòng.

## 10. Scanner: còn token nhưng sai định dạng là lỗi khác

`Scanner.nextInt()` có thể phát sinh **`InputMismatchException`** nếu token tiếp theo không thể diễn giải theo kiểu `int` phù hợp. Nó chỉ báo **`NoSuchElementException`** khi không có token để lấy.

Lưu **`ScannerMismatchDemo.java`**:

```java
import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class ScannerMismatchDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner("42 abc")) {
            System.out.println("Số: " + scanner.nextInt());

            try {
                scanner.nextInt();
            } catch (InputMismatchException e) {
                System.out.println("Token không phải số nguyên");
            }

            System.out.println("Token còn lại: " + scanner.next());

            try {
                scanner.nextInt();
            } catch (NoSuchElementException e) {
                System.out.println("Đã hết token");
            }
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 ScannerMismatchDemo.java
java ScannerMismatchDemo
```

**Stdout chính xác**:

```text
Số: 42
Token không phải số nguyên
Token còn lại: abc
Đã hết token
```

Sau `nextInt()` gặp `abc`, Scanner ném `InputMismatchException` và **không tiêu thụ token không khớp**; `next()` sau đó đọc chính `abc`. Lần `nextInt()` cuối gặp EOF nên ném `NoSuchElementException`.

Với Scanner đã bị **đóng**, nhiều thao tác như `next()` sẽ ném `IllegalStateException`. **Không bắt `NoSuchElementException` để giả vờ rằng scanner đã đóng hoặc dữ liệu sai định dạng cũng là EOF.**

## 11. Dùng `hasNextInt()` và lựa chọn xử lý token sai

Nếu input phải là số nguyên, một pattern rõ ràng:

```java
if (!scanner.hasNext()) {
    // Kết thúc nguồn: không có token.
} else if (!scanner.hasNextInt()) {
    String badToken = scanner.next();
    // Ghi nhận/đưa ra lỗi xác thực cho badToken.
} else {
    int value = scanner.nextInt();
    // Xử lý value.
}
```

Đây là **mã khung minh họa**, chưa khai báo `scanner`. Cách xử lý phân biệt ba tình huống:

1. **Không còn dữ liệu**: `hasNext() == false`.
2. **Còn token nhưng không phải int**: `hasNext() == true`, `hasNextInt() == false`.
3. **Có token int hợp lệ**: đọc bằng `nextInt()`.

Trong nguồn có thể thay đổi đồng thời, bản thân việc kiểm tra không là bảo đảm tuyệt đối; cần tuân thủ hợp đồng đồng bộ của nguồn. Với `Scanner` trên input tương tác, phương thức `hasNext()` cũng có thể **chờ dữ liệu**; đừng coi nó là phép kiểm tra tức thời và không blocking trong mọi môi trường.

Một hệ thống xử lý input ổn định nên định nghĩa rõ: token sai sẽ bị loại bỏ, yêu cầu nhập lại, hay làm dừng toàn bộ tác vụ?

## 12. `Optional.get()`: rỗng không có nghĩa là `null`

`Optional<T>` đại diện cho một giá trị có thể **có hoặc không có**. Một `Optional.empty()` không chứa giá trị; gọi `get()` trên nó sẽ ném `NoSuchElementException`.

Ví dụ **`OptionalGetDemo.java`**:

```java
import java.util.NoSuchElementException;
import java.util.Optional;

public class OptionalGetDemo {
    public static void main(String[] args) {
        Optional<String> present = Optional.of("OK");
        Optional<String> missing = Optional.empty();

        System.out.println("Có: " + present.get());
        System.out.println("Rỗng: " + missing.isEmpty());

        try {
            missing.get();
        } catch (NoSuchElementException e) {
            System.out.println("Không có giá trị Optional");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 OptionalGetDemo.java
java OptionalGetDemo
```

**Stdout chính xác**:

```text
Có: OK
Rỗng: true
Không có giá trị Optional
```

`Optional.get()` không trả `null` khi rỗng; nó ném exception. **Cũng không nên nhầm `Optional.empty()` với một `Optional` chứa `null`**: `Optional.of(null)` ném `NullPointerException`, trong khi `Optional.ofNullable(null)` tạo `Optional.empty()`.

Trong Java hiện đại, khi không biết chắc có giá trị, nhiều API khác của Optional thể hiện ý định rõ hơn `get()`.

## 13. Những lựa chọn thay `Optional.get()`

Các lựa chọn thông dụng:

| API | Khi có giá trị | Khi rỗng |
| --- | --- | --- |
| `get()` | Trả giá trị | Ném `NoSuchElementException` |
| `orElse(defaultValue)` | Trả giá trị | Trả fallback được truyền |
| `orElseGet(supplier)` | Trả giá trị | Gọi supplier để tạo fallback |
| `orElseThrow()` | Trả giá trị | Ném `NoSuchElementException` |
| `orElseThrow(exceptionSupplier)` | Trả giá trị | Tạo và ném exception do supplier cung cấp |
| `ifPresent(consumer)` | Gọi consumer | Bỏ qua |
| `ifPresentOrElse(present, empty)` | Chạy nhánh present | Chạy nhánh empty |
| `isPresent()` / `isEmpty()` | Trả boolean | Trả boolean đối ứng |

**Không có lựa chọn nào luôn đúng.** Nếu không có dữ liệu là kết quả bình thường, dùng fallback hoặc nhánh rỗng. Nếu thiếu dữ liệu **vi phạm hợp đồng**, `orElseThrow(...)` với loại exception có ngữ nghĩa cụ thể có thể tốt hơn.

Chú ý: `orElse(value)` đánh giá **đối số `value` trước khi gọi method** theo quy tắc Java, ngay cả khi Optional đang có giá trị; `orElseGet(supplier)` chỉ gọi supplier khi Optional rỗng. Điều này quan trọng nếu fallback tốn kém hoặc có side effect.

## 14. Ví dụ Java 17: fallback của `Optional`

Lưu **`OptionalFallbackDemo.java`**:

```java
import java.util.Optional;

public class OptionalFallbackDemo {
    static String createFallback() {
        System.out.println("Tạo fallback");
        return "mặc định";
    }

    public static void main(String[] args) {
        Optional<String> present = Optional.of("thật");
        Optional<String> empty = Optional.empty();

        System.out.println("orElse: "
                + present.orElse(createFallback()));
        System.out.println("orElseGet: "
                + present.orElseGet(OptionalFallbackDemo::createFallback));
        System.out.println("empty: "
                + empty.orElseGet(OptionalFallbackDemo::createFallback));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 OptionalFallbackDemo.java
java OptionalFallbackDemo
```

**Stdout chính xác**:

```text
Tạo fallback
orElse: thật
orElseGet: thật
Tạo fallback
empty: mặc định
```

Lần gọi `present.orElse(createFallback())` in `Tạo fallback` dù Optional chứa `"thật"`. Ngược lại, `present.orElseGet(...)` không gọi supplier. Với Optional rỗng, `orElseGet` mới tạo fallback.

Hành vi này **không thay đổi ngoại lệ của `get()`**, nhưng giúp chọn API hợp với ý nghĩa rỗng mà không dùng `try/catch NoSuchElementException` như một phần luồng xử lý bình thường.

## 15. `orElseThrow` có hai hình thức khác nhau

- `optional.orElseThrow()`: trả giá trị nếu có; nếu không có thì ném `NoSuchElementException`.
- `optional.orElseThrow(() -> new SomeException(...))`: cho phép caller **chọn loại exception** được tạo khi Optional rỗng.

Ví dụ Java 17 độc lập **`OptionalOrElseThrowDemo.java`**:

```java
import java.util.Optional;

public class OptionalOrElseThrowDemo {
    static String requireName(Optional<String> optional) {
        return optional.orElseThrow(
                () -> new IllegalArgumentException(
                        "name là bắt buộc"));
    }

    public static void main(String[] args) {
        System.out.println(requireName(Optional.of("Lan")));

        try {
            requireName(Optional.empty());
        } catch (IllegalArgumentException e) {
            System.out.println("Bắt: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 OptionalOrElseThrowDemo.java
java OptionalOrElseThrowDemo
```

**Stdout chính xác**:

```text
Lan
Bắt: name là bắt buộc
```

Trong ví dụ, **thiếu tên** là vi phạm tiền điều kiện của API `requireName`, nên `IllegalArgumentException` thể hiện hợp đồng cụ thể hơn là một lỗi truy xuất Optional chung.

Nếu thiếu bản ghi là nhánh nghiệp vụ dự kiến, có thể chọn trả `Optional` lên caller hoặc một kiểu kết quả domain, thay vì ép thành exception.

**Một lưu ý về checked exception:** phiên bản `orElseThrow(exceptionSupplier)` có thể được sử dụng với supplier tạo checked exception; lúc đó phương thức bao ngoài phải catch/declare theo luật checked exceptions của Java. Nó không tự biến checked exception thành unchecked.

## 16. `OptionalInt`, `OptionalLong` và `OptionalDouble`

Java có các lớp Optional chuyên biệt cho primitive, tránh boxing trong một số tình huống:

- `OptionalInt.getAsInt()`
- `OptionalLong.getAsLong()`
- `OptionalDouble.getAsDouble()`

Khi đối tượng tương ứng rỗng, các method bắt buộc phải có phần tử này **ném `NoSuchElementException`**, tương tự `Optional<T>.get()`.

Ví dụ **`PrimitiveOptionalDemo.java`**:

```java
import java.util.NoSuchElementException;
import java.util.OptionalInt;

public class PrimitiveOptionalDemo {
    public static void main(String[] args) {
        OptionalInt present = OptionalInt.of(7);
        OptionalInt missing = OptionalInt.empty();

        System.out.println("Có: " + present.getAsInt());
        System.out.println("Rỗng: " + missing.isEmpty());

        try {
            missing.getAsInt();
        } catch (NoSuchElementException e) {
            System.out.println("Không có giá trị int");
        }

        System.out.println("Fallback: " + missing.orElse(-1));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 PrimitiveOptionalDemo.java
java PrimitiveOptionalDemo
```

**Stdout chính xác**:

```text
Có: 7
Rỗng: true
Không có giá trị int
Fallback: -1
```

Không phải vì primitive không có `null` mà `getAsInt()` sẽ luôn có giá trị: **sự vắng mặt được biểu diễn bằng trạng thái của Optional**, và lời gọi bắt buộc có giá trị vẫn có thể thất bại.

## 17. `Queue`: `remove/element` so với `poll/peek`

`java.util.Queue<E>` cung cấp hai bộ thao tác có ngữ nghĩa khác nhau khi hàng đợi trống:

| Mục đích | Ném nếu trống | Trả `null` nếu trống |
| --- | --- | --- |
| Lấy ra và xóa phần tử đầu | `remove()` | `poll()` |
| Chỉ xem phần tử đầu, không xóa | `element()` | `peek()` |

Với một queue trống, `remove()` và `element()` có thể ném `NoSuchElementException`. Trong khi đó, `poll()` và `peek()` trả `null` theo hợp đồng `Queue`.

Lưu **`QueueEmptyDemo.java`**:

```java
import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import java.util.Queue;

public class QueueEmptyDemo {
    public static void main(String[] args) {
        Queue<String> queue = new ArrayDeque<>();

        System.out.println("poll rỗng: " + queue.poll());
        System.out.println("peek rỗng: " + queue.peek());

        try {
            queue.remove();
        } catch (NoSuchElementException e) {
            System.out.println("remove rỗng: NoSuchElementException");
        }

        queue.add("A");
        System.out.println("element: " + queue.element());
        System.out.println("remove: " + queue.remove());
        System.out.println("Sau remove rỗng: " + queue.isEmpty());
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 QueueEmptyDemo.java
java QueueEmptyDemo
```

**Stdout chính xác**:

```text
poll rỗng: null
peek rỗng: null
remove rỗng: NoSuchElementException
element: A
remove: A
Sau remove rỗng: true
```

Trong ví dụ, `ArrayDeque` không cho phép phần tử `null` nên `poll() == null` là dấu hiệu không có phần tử. **Ở mức interface `Queue` tổng quát, một số implementation có thể cho phép phần tử `null`**, nên caller không được mặc định suy luận y như vậy cho mọi loại queue.

Chọn API theo hợp đồng: một queue đang được dùng như nguồn công việc có thể rỗng bình thường thì `poll()` thường đúng hơn `remove()`.

## 18. `Deque` và stack: `getFirst/removeFirst/pop`

`java.util.Deque<E>` mở rộng các thao tác ở hai đầu hàng đợi. Một số method **ném `NoSuchElementException` khi rỗng**:

- `getFirst()` và `getLast()`: đọc nhưng không xóa.
- `removeFirst()` và `removeLast()`: đọc rồi xóa.
- `pop()`: thao tác stack tương đương lấy và xóa phần tử đầu; ném nếu rỗng.

Những lựa chọn trả tín hiệu rỗng là:

- `peekFirst()` / `peekLast()`.
- `pollFirst()` / `pollLast()`.
- `peek()` / `poll()` ở phía đầu queue.

Ví dụ **`DequeEmptyDemo.java`**:

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;

public class DequeEmptyDemo {
    public static void main(String[] args) {
        Deque<String> stack = new ArrayDeque<>();

        System.out.println("peek rỗng: " + stack.peek());

        try {
            stack.pop();
        } catch (NoSuchElementException e) {
            System.out.println("pop rỗng: NoSuchElementException");
        }

        stack.push("A");
        stack.push("B");

        System.out.println("top: " + stack.getFirst());
        System.out.println("pop: " + stack.pop());
        System.out.println("pop: " + stack.pop());
        System.out.println("poll cuối: " + stack.poll());
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 DequeEmptyDemo.java
java DequeEmptyDemo
```

**Stdout chính xác**:

```text
peek rỗng: null
pop rỗng: NoSuchElementException
top: B
pop: B
pop: A
poll cuối: null
```

Nếu dùng `Deque` làm stack, `push`/`pop` biểu diễn ngữ nghĩa LIFO, còn `peek` cho phép kiểm tra phần tử đầu mà không xóa. **Không ném exception để thông báo một stack rỗng theo nhánh bình thường nếu caller có thể chủ động xử lý bằng `peek` hoặc `isEmpty`.**

## 19. Cẩn thận với `null` trong collection

Không nên nói “`NoSuchElementException` chính là giá trị `null`”:

- `NoSuchElementException` là **một đối tượng lỗi**, có loại và stack trace.
- `null` là **giá trị tham chiếu** biểu thị không trỏ tới object.
- `Optional.empty()` là **một object Optional có trạng thái rỗng**, không phải `null`.
- `Queue.poll()` có thể dùng `null` làm tín hiệu rỗng theo hợp đồng của interface.
- Một số collection có thể chứa **phần tử `null` hợp lệ**, nên `null` trả về không phải lúc nào cũng chứng minh không có phần tử.
- `ArrayDeque` không cho phép thêm `null`, vì vậy nó tránh được sự mơ hồ này trong các ví dụ `Queue`/`Deque` ở trên.

Nếu hợp đồng API cho phép phân biệt **“không có phần tử”** và **“có một phần tử mà giá trị là null”**, hãy thiết kế cơ chế trả trạng thái riêng, ví dụ `Optional` (với lưu ý Optional không chứa null), một result object, hoặc kiểm tra cấu trúc theo ngữ cảnh.

**Đừng chuyển bừa `NoSuchElementException` thành `null`** nếu caller của API vốn cần biết đây là vi phạm invariant bất thường.

## 20. `Enumeration.nextElement()` cũng có thể ném

API cũ `java.util.Enumeration<E>` có hai phương thức:

- `hasMoreElements()`: kiểm tra còn phần tử hay không.
- `nextElement()`: lấy phần tử kế tiếp; ném `NoSuchElementException` nếu hết.

Ví dụ **`EnumerationDemo.java`**:

```java
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.NoSuchElementException;

public class EnumerationDemo {
    public static void main(String[] args) {
        Enumeration<String> e =
                Collections.enumeration(Arrays.asList("X"));

        System.out.println("Phần tử: " + e.nextElement());
        System.out.println("Còn: " + e.hasMoreElements());

        try {
            e.nextElement();
        } catch (NoSuchElementException ex) {
            System.out.println("Enumeration đã hết");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 EnumerationDemo.java
java EnumerationDemo
```

**Stdout chính xác**:

```text
Phần tử: X
Còn: false
Enumeration đã hết
```

`Enumeration` xuất hiện trong các API và code cũ; khi tạo thiết kế mới, `Iterator` hoặc API collection/stream phù hợp thường quen thuộc hơn. Nhưng khi sử dụng `Enumeration` từ thư viện, cần giữ đúng **cặp `hasMoreElements()/nextElement()`**, không gọi `nextElement()` đến khi lỗi xảy ra như một giải pháp mặc định.

## 21. `StringTokenizer.nextToken()` khi không còn token

`java.util.StringTokenizer` là API phân tách token lâu đời. `nextToken()` ném `NoSuchElementException` khi không còn token.

Ví dụ Java 17 **`StringTokenizerDemo.java`**:

```java
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

public class StringTokenizerDemo {
    public static void main(String[] args) {
        StringTokenizer tokenizer = new StringTokenizer("a,b", ",");

        while (tokenizer.hasMoreTokens()) {
            System.out.println("Token: " + tokenizer.nextToken());
        }

        try {
            tokenizer.nextToken();
        } catch (NoSuchElementException e) {
            System.out.println("Không còn token");
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 StringTokenizerDemo.java
java StringTokenizerDemo
```

**Stdout chính xác**:

```text
Token: a
Token: b
Không còn token
```

Ví dụ không nhằm khuyến nghị `StringTokenizer` cho mọi parser hiện đại. Nếu yêu cầu parse CSV, JSON hoặc một định dạng có escape/quote, cần thư viện/parser đúng định dạng; việc tách chuỗi bằng token đơn giản có thể không đủ.

Điểm chung là **mọi API có phương thức “lấy phần tử tiếp theo” cần nói rõ hành vi lúc kết thúc**.

## 22. `Stream.findFirst()` trả `Optional`, không tự ném vì stream rỗng

Khi dùng Java Streams:

```java
Optional<String> first = stream.findFirst();
```

Đây là **đoạn minh họa**, với `stream` là `Stream<String>`. Nếu stream không có phần tử, `findFirst()` trả `Optional.empty()`. Bản thân việc gọi `findFirst()` trên stream rỗng **không ném `NoSuchElementException`**.

Exception sẽ xuất hiện nếu caller sau đó **đòi bắt buộc một giá trị từ Optional rỗng** bằng `get()` hoặc `orElseThrow()` không đối số.

Ví dụ độc lập **`StreamOptionalDemo.java`**:

```java
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

public class StreamOptionalDemo {
    public static void main(String[] args) {
        Optional<String> first = Stream.<String>empty().findFirst();

        System.out.println("Có phần tử: " + first.isPresent());
        System.out.println("Fallback: " + first.orElse("không có"));

        try {
            first.orElseThrow();
        } catch (NoSuchElementException e) {
            System.out.println("Bắt: Optional rỗng");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 StreamOptionalDemo.java
java StreamOptionalDemo
```

**Stdout chính xác**:

```text
Có phần tử: false
Fallback: không có
Bắt: Optional rỗng
```

Một lưu ý khác: nhiều thao tác Stream sinh Optional yêu cầu **giá trị phần tử không phải null**; nếu một phần tử `null` được chọn, hành vi có thể liên quan `NullPointerException` chứ không phải `Optional.empty()`. Không nên coi mọi stream có phần tử `null` như một stream trống.

## 23. `List.get(index)` không ném cùng loại với iterator hết

Giả sử `List<String> list = List.of("A", "B")`:

- `list.get(0)` trả `"A"`.
- `list.get(2)` truy cập chỉ số ngoài phạm vi và ném `IndexOutOfBoundsException`.
- `list.iterator().next()` được gọi đến khi iterator hết thì ném `NoSuchElementException`.

Cả hai lỗi đều do một thao tác truy xuất không thể trả phần tử, nhưng **hợp đồng truy xuất khác nhau**:

- `List.get(index)` nhận **vị trí tường minh**. Sai là chỉ số.
- `Iterator.next()` không nhận chỉ số. Sai là **không còn phần tử kế tiếp**.

Ví dụ Java 17 **`IndexVsElementDemo.java`**:

```java
import java.util.List;
import java.util.NoSuchElementException;

public class IndexVsElementDemo {
    public static void main(String[] args) {
        List<String> values = List.of("A");

        try {
            values.get(1);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("List: chỉ số ngoài phạm vi");
        }

        var it = values.iterator();
        System.out.println("Iterator: " + it.next());

        try {
            it.next();
        } catch (NoSuchElementException e) {
            System.out.println("Iterator: không còn phần tử");
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 IndexVsElementDemo.java
java IndexVsElementDemo
```

**Stdout chính xác**:

```text
List: chỉ số ngoài phạm vi
Iterator: A
Iterator: không còn phần tử
```

Đừng viết một handler `catch (NoSuchElementException)` với kỳ vọng nó bắt mọi phép truy cập vượt phạm vi collection. Nó **không** tự bắt `IndexOutOfBoundsException`.

## 24. Khi nào nên tự ném `NoSuchElementException`?

Nếu bạn tự cài đặt một **Iterator**, method `next()` cần tuân thủ hợp đồng: khi không còn phần tử, ném `NoSuchElementException`. Đây là tình huống **tự tạo exception phù hợp** vì bạn đang triển khai API tiêu chuẩn.

Ví dụ:

```java
@Override
public E next() {
    if (!hasNext()) {
        throw new NoSuchElementException(
                "Không còn phần tử trong iterator");
    }
    return data[index++];
}
```

Đây là **mã minh họa** bên trong một class implements `Iterator<E>`, có các field `data` và `index` tương ứng. `NoSuchElementException` nên được ném **trước khi truy cập ngoài phạm vi mảng/collection** để giữ contract của iterator, thay vì để một `ArrayIndexOutOfBoundsException` ngẫu nhiên xuất hiện.

Không cần tự ném `NoSuchElementException` trong mọi method tra cứu thất bại của ứng dụng. Nếu ứng dụng có contract “không tìm thấy mã người dùng” là một trường hợp nghiệp vụ dự kiến, một `Optional` hoặc một kiểu kết quả rõ ràng có thể phù hợp hơn.

## 25. Ví dụ tự cài đặt `Iterator` đúng hợp đồng

Lưu **`CustomIteratorDemo.java`**:

```java
import java.util.Iterator;
import java.util.NoSuchElementException;

public class CustomIteratorDemo {
    static class RangeIterator implements Iterator<Integer> {
        private int current;
        private final int limit;

        RangeIterator(int start, int limit) {
            if (start > limit) {
                throw new IllegalArgumentException(
                        "start không được lớn hơn limit");
            }
            this.current = start;
            this.limit = limit;
        }

        @Override
        public boolean hasNext() {
            return current < limit;
        }

        @Override
        public Integer next() {
            if (!hasNext()) {
                throw new NoSuchElementException(
                        "Range đã hết");
            }
            return current++;
        }
    }

    public static void main(String[] args) {
        RangeIterator it = new RangeIterator(3, 5);

        while (it.hasNext()) {
            System.out.println("Số: " + it.next());
        }

        try {
            it.next();
        } catch (NoSuchElementException e) {
            System.out.println("Bắt: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 CustomIteratorDemo.java
java CustomIteratorDemo
```

**Stdout chính xác**:

```text
Số: 3
Số: 4
Bắt: Range đã hết
```

Dạng khoảng ở đây là **[start, limit)**, nghĩa là bao gồm start và không bao gồm limit. Khi `current == limit`, `hasNext()` trả false và `next()` ném một `NoSuchElementException` với message do chính class thiết kế.

Ví dụ dùng `Integer` để thực hiện `Iterator<Integer>`; boxing là chi tiết nhỏ chấp nhận được cho mục đích học. Với dãy số rất lớn hoặc yêu cầu hiệu năng, có thể dùng primitive iterator hoặc stream chuyên biệt nếu hợp lý.

Lưu ý về giới hạn số: nếu tự thiết kế iterator số nguyên tổng quát, hãy cân nhắc overflow của `current++` gần `Integer.MAX_VALUE` và điều kiện dừng. Ví dụ khoảng nhỏ ở đây không gặp tình huống đó.

## 26. Khi nào kiểm tra trước tốt hơn catch?

Pattern thông thường:

```java
while (iterator.hasNext()) {
    consume(iterator.next());
}
```

thường tốt hơn pattern dựa trên exception để kết thúc:

```java
while (true) {
    try {
        consume(iterator.next());
    } catch (NoSuchElementException e) {
        break;
    }
}
```

Cả hai là **mã minh họa** với `consume` chưa được định nghĩa. Pattern thứ hai có vấn đề tinh tế: **nếu `consume(...)` cũng ném `NoSuchElementException` từ logic riêng**, handler quá rộng có thể bắt nhầm lỗi của `consume` và coi nó là iterator đã hết.

Khi dùng vòng lặp `hasNext()`, phạm vi điều kiện kết thúc rõ ràng hơn. Nếu vì giao thức đặc biệt bắt buộc dùng exception làm tín hiệu EOF, hãy **thu hẹp phạm vi catch** chỉ quanh lời gọi nguồn và tài liệu hóa lựa chọn đó.

Nguyên tắc: **đừng sử dụng ngoại lệ để điều khiển một nhánh bình thường khi API cung cấp phép kiểm tra hoặc giá trị rỗng rõ ràng**.

## 27. Vì sao `hasNext()` không phải phép khóa đồng thời?

Trong chương trình một luồng tuân thủ hợp đồng iterator, cặp `hasNext()/next()` là cách duyệt chuẩn. Nhưng trong chương trình đa luồng hoặc với nguồn thay đổi từ nơi khác, **kiểm tra rồi thao tác** có thể không nguyên tử.

Ví dụ khung:

```java
if (!queue.isEmpty()) {
    // Thread khác có thể lấy phần tử cuối ở đây.
    String value = queue.remove();
}
```

Đây là **phản ví dụ concurrency**, chưa có khai báo queue. Với một queue chia sẻ phù hợp, thread khác có thể làm queue rỗng **sau `isEmpty()` nhưng trước `remove()`**. Khi đó `remove()` có thể ném `NoSuchElementException`.

Nếu dùng một queue hỗ trợ concurrency, giải pháp thường phù hợp hơn là **thực hiện thao tác nguyên tử theo API queue**:

```java
String value = queue.poll();
if (value == null) {
    // Hàng đợi hiện rỗng, nếu queue không cho phép null element.
} else {
    process(value);
}
```

Đây là **mã minh họa** giả định queue không cho phép phần tử `null` và có hàm `process`. Các queue như `ConcurrentLinkedQueue` hoặc `BlockingQueue` có hợp đồng riêng về thao tác concurrent.

**Không chuyển sang `catch (NoSuchElementException)` để “sửa” race condition một cách mù quáng.** Hãy chọn thao tác có tính nguyên tử/đồng bộ đúng theo cấu trúc dữ liệu và chính sách chờ mong muốn.

## 28. `BlockingQueue`: khi nào không có phần tử là tạm thời?

Với một `BlockingQueue`, queue trống có thể chỉ là trạng thái **tạm thời** vì producer chưa thêm phần tử. Tùy API:

- `poll()`: có thể trả `null` ngay nếu không có phần tử.
- `poll(timeout, unit)`: đợi có giới hạn; trả `null` nếu hết thời gian mà vẫn không lấy được phần tử.
- `take()`: **chờ** cho tới khi có phần tử và có thể ném `InterruptedException` nếu bị ngắt.
- `remove()`: thao tác lấy ngay theo hợp đồng queue; khi trống có thể ném `NoSuchElementException`.

Đây là những **chính sách vận hành khác nhau**, không phải chỉ những biến thể cú pháp của cùng hành vi.

Ví dụ Java 17 độc lập, không dùng thread producer nên đầu ra xác định:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class BlockingQueueDemo {
    public static void main(String[] args) {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(2);

        System.out.println("poll: " + queue.poll());
        queue.add("A");
        System.out.println("poll: " + queue.poll());
        System.out.println("empty: " + queue.isEmpty());
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 BlockingQueueDemo.java
java BlockingQueueDemo
```

**Stdout chính xác**:

```text
poll: null
poll: A
empty: true
```

Không nên dùng `take()` trong một ví dụ không có producer vì có thể chờ vô hạn. Khi dùng API blocking thực tế, **xử lý interrupt** đúng hợp đồng; không coi `InterruptedException` như `NoSuchElementException`.

## 29. Ngoại lệ khi không có phần tử khác với `ConcurrentModificationException`

Nhiều iterator trên collection như `ArrayList` có cơ chế **fail-fast best-effort** khi phát hiện sửa cấu trúc collection bên ngoài iterator trong lúc lặp. Khi phát hiện tình huống này, iterator có thể ném `ConcurrentModificationException`.

Điều đó **khác** với `NoSuchElementException`:

- `NoSuchElementException`: caller yêu cầu phần tử tiếp theo nhưng **không có phần tử tiếp theo** theo contract.
- `ConcurrentModificationException`: iterator phát hiện **điều kiện sửa đổi cấu trúc không phù hợp** với hợp đồng lặp của nó.

Một thay đổi cấu trúc bất hợp lệ không được giải thích lại là “đã duyệt đến cuối”. **Không bắt `NoSuchElementException` để che sửa collection sai cách**.

Cơ chế fail-fast không phải công cụ đồng bộ đảm bảo phát hiện mọi race; tài liệu JDK mô tả việc phát hiện ở mức nỗ lực tối đa trong các tình huống concurrent/structural modification. Muốn thao tác đồng thời đúng, cần chọn cấu trúc dữ liệu và quy tắc sở hữu phù hợp.

## 30. `NoSuchElementException` có constructor và message không?

Lớp này có thể được tạo trực tiếp với message:

```java
throw new NoSuchElementException(
        "Không còn bản ghi trong cursor");
```

Đây là **câu lệnh minh họa**, cần import `java.util.NoSuchElementException`. Message giúp phân biệt nguồn phần tử nếu có nhiều iterator/cursor.

Giống những `RuntimeException` thông thường, message:

- Được truy cập bằng `getMessage()`.
- Có thể là `null` tùy constructor.
- Không tự đảm bảo stack trace hoặc cause được in khi bạn chỉ gọi `getMessage()`.
- Không nên được phân tích bằng `contains(...)` để điều khiển logic nghiệp vụ.
- Không nên chứa mật khẩu, token hay dữ liệu nhạy cảm.

Nếu bạn **bọc lỗi từ tầng khác** thành một `NoSuchElementException`, phải cân nhắc có phù hợp ngữ nghĩa không. Không nên chuyển mọi sự cố I/O, database hoặc timeout thành “không có phần tử” vì như vậy caller có thể hiểu sai bản chất lỗi.

## 31. Khi nào bắt `NoSuchElementException` là hợp lý?

Có những trường hợp phù hợp:

1. Một API **bắt buộc phải có phần tử** và caller muốn chuyển exception thành trạng thái thất bại ở ranh giới.
2. Adapter phải tuân thủ một giao thức cũ dùng exception làm tín hiệu hết dữ liệu.
3. Unit test cố ý kiểm tra contract `next()` trên iterator đã hết.
4. Một lớp wrapper chuyển exception sang **loại lỗi domain rõ ràng** khi việc không có phần tử thực sự có ý nghĩa đối với API của nó.

Nhưng **không nên bắt chỉ để bỏ qua lỗi** hoặc làm chương trình như thể đã đọc được dữ liệu.

Ví dụ:

```java
try {
    return iterator.next();
} catch (NoSuchElementException e) {
    return DEFAULT_RECORD;
}
```

Đây là **mã khung minh họa**. Chỉ hợp lệ nếu `DEFAULT_RECORD` là fallback thật sự được hợp đồng cho phép và được định nghĩa rõ. Nếu phần tử bắt buộc vì dữ liệu có invariant, việc trả một object giả có thể che vấn đề.

Một cách tốt hơn khi API có lựa chọn rỗng ngay từ đầu là **trả Optional hoặc kiểu kết quả có trạng thái** thay vì bắt ngoại lệ ở mọi lần truy xuất.

## 32. Đừng bắt lỗi ở phạm vi quá rộng

Một anti-pattern:

```java
try {
    while (true) {
        Item item = iterator.next();
        process(item);
    }
} catch (NoSuchElementException ignored) {
    // Cho rằng đã lặp xong.
}
```

Đây là **mã khung phản ví dụ**. Nếu `process(item)` ném `NoSuchElementException` do một cursor khác bên trong, handler sẽ **che nhầm lỗi thực tế** và kết luận rằng iterator bên ngoài đã hết.

Nếu phải xử lý exception của một API đặc biệt, tách vùng catch đúng phạm vi:

```java
Item item;
try {
    item = iterator.next();
} catch (NoSuchElementException endOfIterator) {
    // Xử lý kết thúc nguồn ở đây.
    return;
}
process(item);
```

Đây là **mã minh họa trong một phương thức**. Nhưng với `Iterator` tiêu chuẩn, vòng lặp `hasNext()` vẫn là giải pháp bình thường hơn.

Cần nhớ **vị trí exception được ném** quan trọng ngang với **loại exception**. Một handler có phạm vi quá lớn làm mất ngữ cảnh và có thể nuốt lỗi từ các thao tác không liên quan.

## 33. Kiểm thử hành vi rỗng bằng Java thuần

Một bài test tốt không chỉ xác nhận có exception, mà còn xác nhận:

- Truy xuất khi còn phần tử hoạt động đúng.
- Khi hết, method theo hợp đồng **ném đúng `NoSuchElementException`**.
- Method có tín hiệu rỗng như `hasNext()`, `poll()` hoặc `peek()` trả giá trị dự kiến.
- Không thay đổi trạng thái hoặc làm mất phần tử ngoài ý muốn sau lời gọi thất bại.
- Nếu đó là API có fallback, fallback chỉ chạy theo những điều kiện được công bố.

Ví dụ **`NoSuchElementAssertionsDemo.java`**:

```java
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;

public class NoSuchElementAssertionsDemo {
    static void expectNoElement(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Thiếu NoSuchElementException");
        } catch (NoSuchElementException expected) {
            System.out.println("PASS: đã ném NoSuchElementException");
        }
    }

    public static void main(String[] args) {
        Iterator<Integer> iterator = List.of(1).iterator();

        if (iterator.next() != 1 || iterator.hasNext()) {
            throw new AssertionError("Sai trạng thái iterator");
        }

        expectNoElement(iterator::next);

        Queue<String> queue = new ArrayDeque<>();
        if (queue.poll() != null) {
            throw new AssertionError("Queue phải rỗng");
        }

        expectNoElement(queue::remove);
        System.out.println("PASS: hợp đồng rỗng được giữ");
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 NoSuchElementAssertionsDemo.java
java NoSuchElementAssertionsDemo
```

**Stdout chính xác**:

```text
PASS: đã ném NoSuchElementException
PASS: đã ném NoSuchElementException
PASS: hợp đồng rỗng được giữ
```

`Runnable` cho phép dùng method reference `iterator::next` và `queue::remove` ở đây vì các method đó trả giá trị nhưng lời gọi trong ngữ cảnh `void` của functional interface có thể bỏ qua kết quả. `NoSuchElementException` là unchecked nên không có trở ngại checked `throws`.

Test dùng `throw new AssertionError(...)` để thất bại chủ động, không phụ thuộc cờ `-ea` của từ khóa `assert`.

## 34. Những sai lầm phổ biến

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “`NoSuchElementException` là checked nên phải khai báo `throws`.” | Sai. Nó thuộc `RuntimeException`, là unchecked. |
| “`hasNext()` trả false sẽ tự ném exception.” | Không. `next()` được gọi khi hết mới ném theo contract iterator. |
| “Mọi collection rỗng đều tự ném `NoSuchElementException`.” | Không. Phụ thuộc method: `peek/poll` có thể trả `null`. |
| “`Queue.remove()` và `Queue.poll()` giống hệt nhau.” | Không. Với queue rỗng, `remove` ném còn `poll` trả `null`. |
| “`Queue.element()` và `Queue.peek()` giống hệt nhau.” | Không. Với queue rỗng, `element` ném còn `peek` trả `null`. |
| “`Deque.pop()` trên stack rỗng trả `null`.” | Sai. `pop` ném `NoSuchElementException` khi rỗng. |
| “`Optional.empty().get()` trả `null`.” | Sai. Nó ném `NoSuchElementException`. |
| “`Optional.orElseThrow()` chỉ tạo unchecked exception khác.” | Không. Bản không tham số ném `NoSuchElementException` khi rỗng. |
| “`orElse(default())` chỉ gọi `default()` khi Optional rỗng.” | Sai. Biểu thức đối số được đánh giá trước lời gọi; dùng `orElseGet` khi cần lazy fallback. |
| “`Scanner.nextInt()` gặp chữ sẽ ném `NoSuchElementException`.” | Khi còn token sai kiểu, thường `InputMismatchException`; khi hết token mới là `NoSuchElementException`. |
| “`Scanner.nextLine()` và `Scanner.next()` luôn tương đương.” | Không. Một cái đọc dòng, một cái đọc token. |
| “`List.get(index)` vượt giới hạn ném `NoSuchElementException`.” | Sai. Thường là `IndexOutOfBoundsException`. |
| “Sau `hasNext()` chắc chắn thread khác không thể thay đổi nguồn.” | Không. Kiểm tra không tự khóa nguồn; cần tuân thủ contract concurrency. |
| “Bắt `NoSuchElementException` quanh toàn bộ vòng lặp là cách tốt nhất.” | Dễ che lỗi phát sinh từ phần xử lý; dùng `hasNext()` khi phù hợp. |
| “`ConcurrentModificationException` có nghĩa iterator hết phần tử.” | Sai. Nó nói về sửa cấu trúc không đúng điều kiện của iterator fail-fast. |
| “Hàng đợi trả `null` luôn nghĩa là rỗng trong mọi implementation.” | Một số `Queue` có thể chứa `null`; xét contract implementation. |
| “`NoSuchElementException` báo dữ liệu hỏng hoặc timeout.” | Không mặc nhiên; các nguyên nhân đó có hợp đồng exception khác. |
| “Catch được exception thì có thể trả thành công.” | Chỉ khi thật sự có fallback hợp lệ; không biến thất bại thành thành công giả. |

## 35. Câu hỏi tự kiểm tra

**1.** `Iterator.hasNext()` trả false. Gọi `next()` sẽ thế nào?  
**Đáp án:** `next()` ném `NoSuchElementException` theo contract của Iterator.

**2.** `NoSuchElementException` là checked hay unchecked?  
**Đáp án:** Unchecked, vì nó kế thừa `RuntimeException`.

**3.** Vì sao vòng lặp `while (it.hasNext()) { it.next(); }` phù hợp hơn dùng exception để dừng?  
**Đáp án:** Điều kiện hết dữ liệu được thể hiện trực tiếp, ít nguy cơ bắt nhầm lỗi từ logic xử lý khác.

**4.** Gọi `Iterator.remove()` trước `next()` trên iterator hỗ trợ xóa có thể ném gì?  
**Đáp án:** `IllegalStateException`, khác với `NoSuchElementException` khi `next()` hết dữ liệu.

**5.** `Queue.remove()` và `Queue.poll()` khác nhau khi queue trống?  
**Đáp án:** `remove()` ném `NoSuchElementException`; `poll()` trả `null`.

**6.** `Queue.element()` và `Queue.peek()` khác nhau khi queue trống?  
**Đáp án:** `element()` ném; `peek()` trả `null`.

**7.** `Deque.pop()` trên deque trống có trả `null` không?  
**Đáp án:** Không. Nó ném `NoSuchElementException`.

**8.** `Optional.empty().get()` trả giá trị gì?  
**Đáp án:** Không trả bình thường; ném `NoSuchElementException`.

**9.** `Optional.empty().orElse("fallback")` trả gì?  
**Đáp án:** `"fallback"`.

**10.** `Optional.empty().orElseThrow(() -> new IllegalStateException(...))` ném gì?  
**Đáp án:** Exception do supplier tạo, trong ví dụ là `IllegalStateException`.

**11.** `OptionalInt.empty().getAsInt()` ném gì?  
**Đáp án:** `NoSuchElementException`.

**12.** `Scanner.nextInt()` gặp token `"abc"` khác gì gặp EOF?  
**Đáp án:** Nếu token tồn tại nhưng không đúng dạng số nguyên thì `InputMismatchException`; nếu không có token thì `NoSuchElementException`.

**13.** Sau `InputMismatchException` do `Scanner.nextInt()`, token sai có tự bị tiêu thụ không?  
**Đáp án:** Không. Có thể đọc hoặc bỏ token ấy bằng cách phù hợp, ví dụ `next()`.

**14.** `Scanner.next()` trên Scanner đã đóng có phải trường hợp “không còn token” thông thường không?  
**Đáp án:** Không. Dùng Scanner đã đóng có thể ném `IllegalStateException`.

**15.** `List.of("A").get(1)` ném `NoSuchElementException` không?  
**Đáp án:** Không. Đây là truy cập chỉ số sai và thường ném `IndexOutOfBoundsException`.

**16.** `Enumeration.nextElement()` khi hết ném gì?  
**Đáp án:** `NoSuchElementException`.

**17.** `StringTokenizer.nextToken()` khi hết token ném gì?  
**Đáp án:** `NoSuchElementException`.

**18.** `Stream.empty().findFirst()` có tự ném `NoSuchElementException` không?  
**Đáp án:** Không. Nó trả `Optional.empty()`; exception phát sinh nếu caller bắt buộc lấy từ Optional rỗng.

**19.** Một thread đã kiểm tra `!queue.isEmpty()`, thread khác rút phần tử cuối trước `queue.remove()`. Điều gì có thể xảy ra?  
**Đáp án:** `remove()` có thể ném `NoSuchElementException`; cần API/đồng bộ thích hợp, ví dụ `poll()` trên queue phù hợp.

**20.** Vì sao không nên dùng `catch (NoSuchElementException)` quanh cả `consume(iterator.next())`?  
**Đáp án:** `consume` có thể tự ném cùng loại exception từ nguồn khác, khiến chương trình hiểu nhầm đó là kết thúc iterator ngoài.

**21.** Khi tự viết `Iterator.next()`, nên làm gì nếu `hasNext() == false`?  
**Đáp án:** Ném `NoSuchElementException` đúng contract, không để một lỗi index ngẫu nhiên thay thế.

**22.** `NoSuchElementException` có đồng nghĩa với `null` không?  
**Đáp án:** Không. Một bên là exception, bên kia là giá trị tham chiếu.

**23.** Một job queue có thể tạm rỗng. Nên chọn `remove()` hay `poll()/take()`?  
**Đáp án:** Tùy chính sách. `poll()` cho phép kết quả rỗng, `take()` trên BlockingQueue chờ phần tử; `remove()` báo exception khi trống.

**24.** Nếu dữ liệu không tìm thấy là một kết quả nghiệp vụ dự kiến, có bắt buộc ném `NoSuchElementException` không?  
**Đáp án:** Không. Có thể trả Optional hoặc kiểu kết quả rõ ràng.

## 36. Checklist thực hành

Khi thấy hoặc muốn ngăn `NoSuchElementException`:

1. **Xác định method thực sự ném:** `next`, `nextLine`, `get`, `remove`, `element`, `pop` hay API khác?
2. **Đọc contract của đúng API:** không suy từ tên collection mà bỏ qua lựa chọn `poll/peek/hasNext`.
3. **Tình huống rỗng có bình thường không?** Nếu bình thường, dùng cách kiểm tra/tín hiệu rỗng.
4. **Có bắt buộc phải có phần tử theo invariant không?** Nếu có, ném đúng loại với thông điệp có ngữ cảnh có thể thích hợp.
5. **Với Iterator, đã dùng `hasNext()` trước `next()` chưa?**
6. **Với Scanner, đã phân biệt token, dòng và định dạng dữ liệu chưa?** Đừng nhầm `InputMismatchException` với EOF.
7. **Với Optional, có thể dùng `orElse`, `orElseGet`, `orElseThrow(supplier)` hoặc `ifPresentOrElse` không?**
8. **Với Queue, có đang dùng `remove/element` khi `poll/peek` phù hợp hơn không?**
9. **Với Deque, có phân biệt `pop` với `poll`, `getFirst` với `peekFirst` không?**
10. **Với List, có nhầm `IndexOutOfBoundsException` với `NoSuchElementException` không?**
11. **Có collection chứa `null` được không?** Đảm bảo tín hiệu `null` không mơ hồ.
12. **Nguồn có thể bị thay đổi đồng thời không?** Kiểm tra race giữa “còn phần tử” và “lấy phần tử”.
13. **Handler có đang bắt phạm vi quá rộng không?** Tránh nuốt một `NoSuchElementException` từ logic khác.
14. **Có vô tình trả thành công giả sau khi bắt lỗi không?**
15. **Có thật sự cần tự tạo class exception riêng không?** Nếu implement Iterator, dùng lớp JDK chuẩn.
16. **Có test đường còn phần tử và đường cạn phần tử chưa?**
17. **Có test trạng thái sau thao tác thất bại không?** Không làm mất phần tử hoặc lệch vị trí iterator.
18. **Trong môi trường đồng thời, đã chọn queue/lock/atomic operation đúng hợp đồng chưa?**
19. **Nếu dùng BlockingQueue, có chính sách timeout và interrupt phù hợp không?**
20. **Log có đủ nguồn và tên thao tác thất bại mà không lộ dữ liệu nhạy cảm không?**

## 37. Kết luận

`NoSuchElementException` là **unchecked exception báo thao tác yêu cầu phần tử nhưng phần tử đó không tồn tại** theo contract của phương thức. Các trường hợp điển hình là `Iterator.next()` khi hết, `Scanner.next()/nextLine()` khi không còn dữ liệu phù hợp, `Optional.get()` khi rỗng, và `Queue.remove()/element()` hoặc `Deque.pop()` khi container trống.

**Đừng biến tình huống rỗng bình thường thành lỗi bằng cách chọn API không phù hợp.** Với Iterator dùng `hasNext()`, với Optional chọn `orElse`/`orElseGet`/`orElseThrow(supplier)` theo hợp đồng, với Queue/Deque cân nhắc `poll/peek`. Nếu phần tử là bắt buộc, exception là cách báo vi phạm có chủ đích và nên đi kèm thông điệp đủ ngữ cảnh khi tự xây API.

Quan trọng nhất: **không đồng nhất “không có phần tử” với “chỉ số sai”, “input sai định dạng”, “scanner đã đóng”, “collection bị sửa đồng thời” hoặc “giá trị null”**. Phân loại đúng nguyên nhân và chọn API đúng hợp đồng giúp chương trình dễ đọc, dễ kiểm thử, không nuốt lỗi và không trả một kết quả thành công giả.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-runtime-exceptions-010">UnsupportedOperationException và thao tác tùy chọn</a>

<details>
<summary>Xem chi tiết</summary>

# UnsupportedOperationException và thao tác tùy chọn trong Java

> **Java Core – Exception** · Nhóm: **Ngoại lệ runtime** · Phạm vi chính: các thao tác tùy chọn trên Collection, List, Iterator, Map và sự khác nhau giữa danh sách thay đổi được, danh sách cố định kích thước, view không sửa được và danh sách không sửa được. Ví dụ đã được biên dịch và chạy với **Java 17**.

## 1. UnsupportedOperationException là gì?

`java.lang.UnsupportedOperationException` (UOE) là một **unchecked exception** kế thừa `RuntimeException`. Nó thể hiện rằng thao tác được yêu cầu **không được đối tượng hoặc API cụ thể hỗ trợ**.

~~~text
java.lang.Throwable
└── java.lang.Exception
    └── java.lang.RuntimeException
        └── java.lang.UnsupportedOperationException
~~~

Một ví dụ rất phổ biến:

~~~java
List<String> names = List.of("Lan", "Mai");
names.add("An"); // Ném UnsupportedOperationException.
~~~

Đoạn này là mã Java minh họa trong một method, cần import `java.util.List`. Đối tượng do `List.of(...)` trả về là **unmodifiable list**, nên việc thêm phần tử bằng `add` không được hỗ trợ.

**Điểm cốt lõi:** UOE **không nhất thiết** có nghĩa là “Java chưa cài đặt xong method”. Trong nhiều API của Java Collections Framework, một số thao tác thay đổi nội dung được quy định là **optional operations**: interface có method, nhưng một implementation hợp lệ **được phép không hỗ trợ** thao tác đó. Khi bị yêu cầu thực hiện thao tác không được hỗ trợ, nó có thể ném UOE theo hợp đồng.

Do đó, phải phân biệt:

- **Phương thức có trong interface:** ví dụ biến có kiểu `List<String>` luôn gọi được `add()` về mặt cú pháp.
- **Đối tượng tại runtime có hỗ trợ thao tác đó không:** `ArrayList` cho phép thêm, còn `List.of(...)` không cho phép.
- **Dữ liệu có hợp lệ cho thao tác hay không:** một danh sách hỗ trợ `add` vẫn có thể từ chối phần tử không hợp lệ, chẳng hạn theo chính sách cấm `null`, dẫn đến exception khác.
- **Kết quả có phù hợp nghiệp vụ không:** một thao tác được phép ở runtime vẫn có thể sai về yêu cầu nghiệp vụ.

### Những điều cần đạt sau bài

1. Hiểu vì sao `UnsupportedOperationException` là unchecked và tại sao cùng kiểu `List` có thể có tập thao tác khác nhau.
2. Phân biệt chính xác `Arrays.asList` (**fixed-size, backed by array**), `Collections.unmodifiableList` (**unmodifiable view**) và `List.of` (**unmodifiable list**).
3. Dự đoán UOE với `add`, `remove`, `set`, `clear`, thao tác qua `Iterator`, `Map` và các factory.
4. Biết rằng thao tác **không làm thay đổi gì** chưa chắc được mọi implementation chấp nhận.
5. Viết API khai báo rõ khả năng thay đổi dữ liệu, biết cách tạo mutable copy và không dùng catch UOE để thay cho thiết kế hợp đồng.
6. Kiểm chứng output từ một chương trình Java 17 độc lập.

## 2. “Optional operation” nghĩa chính xác là gì?

Các interface như `Collection<E>`, `List<E>`, `Iterator<E>` và `Map<K,V>` định nghĩa những method chung. Nhưng không phải mọi đối tượng triển khai đều bắt buộc hỗ trợ **mọi thao tác cập nhật**.

Ví dụ:

~~~java
List<String> first = new ArrayList<>();
List<String> second = List.of();
first.add("A");  // Hợp lệ.
second.add("A"); // Ném UnsupportedOperationException.
~~~

Đây là đoạn minh họa cần import `List` và `ArrayList`. Hai biến có **cùng kiểu tĩnh** là `List<String>`, nhưng implementation và hợp đồng mutation khác nhau.

Điều này vẫn tuân quy tắc Java: interface định nghĩa một thao tác có thể gọi; hợp đồng Collection Framework cho phép implementation **không hỗ trợ** một số thao tác cập nhật và thông báo bằng UOE.

### 2.1. Những nhóm thao tác thường là optional

| API | Các thao tác có khả năng không được hỗ trợ | Ví dụ |
| --- | --- | --- |
| `Collection` | `add`, `addAll`, `remove`, `removeAll`, `retainAll`, `clear` | Collection không cho sửa |
| `List` | `set`, `add(index,...)`, `remove(index)` và các thao tác cấu trúc | List cố định kích thước hoặc không cho sửa |
| `Iterator` | `remove()` | Iterator chỉ đọc |
| `ListIterator` | `remove()`, `set(...)`, `add(...)` | Iterator không cho cập nhật |
| `Map` | `put`, `putAll`, `remove`, `clear` | Map không cho sửa |
| `Map.Entry` | `setValue(...)` có thể không được hỗ trợ | Entry chỉ đọc |

Đây là **nhóm ví dụ**, không có nghĩa mọi method của mọi implementation trong bảng đều bắt buộc ném UOE. Tùy implementation, cách gọi và hợp đồng cụ thể, cùng một tên method có thể hoạt động hoặc từ chối.

### 2.2. Unchecked không có nghĩa là có thể bỏ qua

UOE thuộc `RuntimeException`, nên compiler **không bắt buộc** khai báo `throws UnsupportedOperationException` hoặc `catch`. Nhưng nếu xảy ra và không được xử lý, hoạt động hiện tại vẫn kết thúc bất thường.

Một method:

~~~java
static void appendName(List<String> list, String name) {
    list.add(name);
}
~~~

có thể biên dịch dù không `throws`. Nhưng **hợp đồng của method chưa đầy đủ** nếu không nói rõ rằng nó yêu cầu một danh sách có hỗ trợ thêm phần tử. Khi caller truyền `List.of("A")`, method sẽ ném UOE tại runtime.

Nếu API được thiết kế để nhận danh sách chỉ đọc, nó **không nên âm thầm gọi `add`**. Nếu cần thay đổi, hãy tài liệu hóa yêu cầu hoặc tạo mutable copy.

## 3. Phân biệt ba mô hình thường gặp

Nhiều lỗi UOE đến từ việc nhầm ba kiểu đối tượng sau:

| Cách tạo | Thay thế phần tử bằng `set`? | Thêm/xóa làm đổi kích thước? | Quan hệ với dữ liệu nguồn |
| --- | --- | --- | --- |
| `new ArrayList<>(...)` | Có | Có | Tạo list mới chứa các tham chiếu phần tử |
| `Arrays.asList(array)` | **Có** | **Không** | **Backed by** mảng được truyền vào |
| `Collections.unmodifiableList(source)` | Không qua view | Không qua view | **View** phản ánh thay đổi từ source |
| `List.of(...)` | Không | Không | List không sửa được do factory tạo |
| `List.copyOf(source)` | Không | Không | Cung cấp list không sửa được, có thể tái sử dụng instance phù hợp |
| `Stream.of(...).toList()` trên Java 17 | Không | Không | Kết quả là list không sửa được |

**Chú ý:** “không sửa được qua một reference” khác “dữ liệu nền không thể bị thay đổi từ nơi khác”, và cả hai đều khác “đối tượng phần tử được giữ bên trong là bất biến”.

## 4. Arrays.asList: danh sách có kích thước cố định, không phải ArrayList thông thường

Phương thức `java.util.Arrays.asList(T... a)` trả một `List` có kích thước cố định **được hậu thuẫn bởi mảng**.

~~~java
String[] colors = {"red", "blue"};
List<String> list = Arrays.asList(colors);

list.set(0, "green");    // Được hỗ trợ.
list.add("gold");        // UnsupportedOperationException.
list.remove(0);         // UnsupportedOperationException.
~~~

Đây là đoạn minh họa cần import `Arrays` và `List`. Sau `set(0, "green")`, phần tử đầu của **mảng gốc** cũng trở thành `"green"` vì list và array liên kết qua cùng vùng chứa phần tử.

Điểm dễ sai:

- `Arrays.asList` **không trả về `java.util.ArrayList`**, dù tên gần giống.
- Phương thức `set` được hỗ trợ vì nó **thay thế** phần tử tại vị trí có sẵn, không đổi kích thước.
- `add/remove` theo cách thông thường sẽ đổi số phần tử nên không được hỗ trợ.
- Thay đổi phần tử trong array gốc có thể quan sát qua list, và thay đổi bằng `set` của list có thể quan sát qua array.
- Danh sách **fixed-size không đồng nghĩa immutable/unmodifiable**: việc thay thế phần tử vẫn được phép.
- Array vẫn có thể chứa `null`; danh sách thu được có thể chứa `null` nếu array tương ứng chứa null.

Một phương án nếu muốn thêm/xóa:

~~~java
List<String> editable = new ArrayList<>(Arrays.asList("red", "blue"));
editable.add("gold");  // Hợp lệ.
editable.remove("red"); // Hợp lệ.
~~~

Đây là đoạn minh họa, cần import `ArrayList`, `Arrays` và `List`. Constructor của ArrayList tạo **danh sách mới có thể đổi kích thước**. Nó sao chép **các tham chiếu phần tử**, không tự clone sâu các object bên trong.

### 4.1. Một bẫy khác: mảng primitive

~~~java
int[] numbers = {1, 2, 3};
List<int[]> list = Arrays.asList(numbers);
System.out.println(list.size()); // 1
~~~

Đây là một ví dụ Java hợp lệ với import phù hợp. Với một đối số `int[]`, danh sách thu được có một **phần tử kiểu int[]**, không phải `List<Integer>` gồm ba số. Đây là vấn đề generic/varargs riêng, không phải nguyên nhân trực tiếp gây UOE, nhưng thường gặp trong những đoạn code dùng `Arrays.asList` rồi cố thêm/xóa.

## 5. Collections.unmodifiableList: một view không cho sửa

Một wrapper không sửa được được tạo bằng:

~~~java
List<String> source = new ArrayList<>(List.of("one", "two"));
List<String> view = Collections.unmodifiableList(source);
~~~

Đoạn minh họa cần import `Collections`, `ArrayList`, `List`.

Với `view`:

~~~java
view.add("three");       // UnsupportedOperationException.
view.set(0, "changed");  // UnsupportedOperationException.
~~~

Tuy nhiên, wrapper **giữ liên hệ với list nền**:

~~~java
source.add("three");
System.out.println(view); // [one, two, three]
~~~

Khi nguồn thay đổi qua reference `source`, việc đọc `view` phản ánh thay đổi đó. Đây là **unmodifiable view**, không phải **snapshot bất biến của dữ liệu ở thời điểm tạo**.

**Hệ quả thiết kế:**

- Nếu một method trả về `Collections.unmodifiableList(internal)` nhưng vẫn giữ và sửa `internal`, caller có thể thấy dữ liệu thay đổi theo thời gian.
- Nếu muốn cung cấp kết quả **ổn định về cấu trúc** trước những thay đổi tiếp theo của danh sách nguồn, hãy cân nhắc một bản sao không sửa được như `List.copyOf(source)` (theo điều kiện dữ liệu và hợp đồng null phù hợp).
- Unmodifiable wrapper **không bảo vệ bạn** khỏi việc một thread khác sửa list nền không đồng bộ. Nó cũng không làm backing collection tự nhiên trở thành thread-safe.
- Nếu phần tử trong list là mutable object, caller vẫn có thể sửa **trạng thái bên trong phần tử** khi có reference tới chúng. Không sửa được **cấu trúc list** không có nghĩa deep immutability.

Không nên công khai một view dựa trên cấu trúc nội bộ mutable nếu cần tránh side effect khó đoán; hãy lựa chọn rõ giữa “live read-only view” và “detached snapshot”.

## 6. List.of và List.copyOf: cấu trúc danh sách không sửa được

Từ Java 9, `List.of(...)` tạo các danh sách không sửa được (*unmodifiable lists*).

~~~java
List<String> names = List.of("Lan", "Mai");
names.add("An");      // UnsupportedOperationException.
names.set(0, "Hoa");  // UnsupportedOperationException.
names.remove(0);      // UnsupportedOperationException.
~~~

Ở đây không chỉ thay đổi kích thước bị cấm; **thay thế phần tử** cũng bị cấm.

Đặc điểm quan trọng của `List.of`:

- Không cho thêm/xóa/thay thế phần tử qua các thao tác cập nhật.
- Không chấp nhận phần tử `null`; ví dụ `List.of("A", (String) null)` ném `NullPointerException` **trong lúc tạo**, không phải UOE do gọi add.
- Thứ tự các phần tử phản ánh thứ tự đối số khai báo; bạn có thể truy cập bằng `get(index)` khi index hợp lệ.
- Object chứa bên trong **vẫn có thể mutable**. Việc list không hỗ trợ `set` không ngăn thay đổi trạng thái của object phần tử nếu class phần tử cho phép.
- Không nên dùng đồng nhất đối tượng (`==`) hoặc khóa đồng bộ dựa trên danh sách từ factory mà không hiểu tính chất *value-based* của các instance từ nhóm factory này.

Từ Java 10, `List.copyOf(source)` tạo hoặc trả về một danh sách **không sửa được** chứa các phần tử của source tại thời điểm tạo về mặt cấu trúc:

~~~java
List<String> source = new ArrayList<>(List.of("A", "B"));
List<String> snapshot = List.copyOf(source);
source.add("C");
System.out.println(snapshot); // [A, B]
~~~

Ví dụ này giả định source là một ArrayList mutable. Kết quả snapshot **không phản ánh lần thêm C vào source**. Nhưng:

- `List.copyOf` không phải thao tác **deep copy** phần tử.
- Nó **từ chối phần tử null** trong input; có thể ném NPE nếu collection chứa null.
- API được phép tái sử dụng một unmodifiable list phù hợp; không được giả định hai lần gọi luôn tạo hai instance khác nhau.
- Nếu phần tử là mutable object, đổi trạng thái object đó vẫn có thể thấy qua cả source và snapshot.

**Tóm tắt:** `Arrays.asList` = fixed-size backed by array; `Collections.unmodifiableList` = wrapper view; `List.of/List.copyOf` = danh sách không cho sửa cấu trúc với hợp đồng cụ thể. Chúng **không thể thay thế cho nhau một cách vô điều kiện**.

## 7. Map.of và các thao tác cập nhật tùy chọn

Không chỉ List mới có optional operations. `Map` cũng định nghĩa nhiều thao tác cập nhật:

~~~java
Map<String, Integer> quantities = Map.of("apple", 2);
quantities.put("banana", 3); // UnsupportedOperationException.
quantities.remove("apple");  // UnsupportedOperationException.
~~~

Đây là đoạn minh họa cần import `java.util.Map`.

Với `Map.of`, map không sửa được. Nếu cần cập nhật, hãy tạo mutable copy:

~~~java
Map<String, Integer> editable = new HashMap<>(Map.of("apple", 2));
editable.put("banana", 3); // Hợp lệ.
~~~

Cần import `HashMap` và `Map`. `Map.of` cũng không chấp nhận key hoặc value null; việc đưa null vào factory sẽ có thể ném NPE thay vì UOE.

**Đừng suy rộng:** `Map` có method `put` không chứng minh mọi instance Map hỗ trợ put. Một view không sửa được cũng có thể phát sinh thông qua `Collections.unmodifiableMap(source)`, với điểm khác biệt giữa view và snapshot tương tự List.

### 7.1. Cẩn trọng với view của keySet, values và entrySet

Một `Map` thường cung cấp các view như `keySet()`, `values()` và `entrySet()`. Khả năng xóa/sửa thông qua các view **phụ thuộc vào hợp đồng map nguồn**.

Một ví dụ:

~~~java
Map<String, Integer> mutable = new HashMap<>();
mutable.put("A", 1);
mutable.keySet().remove("A"); // Thao tác thường được hỗ trợ với HashMap.
~~~

Nhưng cùng tên method qua một view của map không sửa được có thể ném UOE. Không nên tự suy rằng “keySet là Set nên chắc chắn được add/remove”: API view có thể chặn hoặc cho phép từng thao tác.

Với entry từ những map khác nhau, `Map.Entry.setValue(...)` cũng có thể được hoặc không được hỗ trợ. Hãy đọc Javadoc của implementation.

## 8. Iterator.remove và ListIterator: thao tác tùy chọn

`Iterator<E>` cung cấp một method `remove()` để loại bỏ phần tử cuối cùng được trả bởi `next()`, **nếu iterator hỗ trợ**.

Ví dụ với ArrayList:

~~~java
List<String> words = new ArrayList<>(List.of("first", "second"));
Iterator<String> iterator = words.iterator();

iterator.next();
iterator.remove(); // Được hỗ trợ đối với iterator ArrayList.
System.out.println(words); // [second]
~~~

Đoạn này yêu cầu import `ArrayList`, `Iterator`, `List`. Nhưng:

~~~java
Iterator<String> iterator = List.of("first").iterator();
iterator.next();
iterator.remove(); // UnsupportedOperationException.
~~~

Một iterator của danh sách không sửa được không thể dùng để xóa phần tử cấu trúc.

**Phân biệt với IllegalStateException:** Ngay cả iterator **có hỗ trợ remove**, việc gọi `remove()` trước `next()` hoặc gọi `remove()` lặp lại mà không có `next()` mới có thể ném `IllegalStateException` theo hợp đồng của Iterator. Còn UOE thể hiện **bản thân thao tác remove không được hỗ trợ**. Hai lỗi có nguyên nhân khác nhau.

`ListIterator` bổ sung `add` và `set` ngoài `remove`, nhưng các thao tác cập nhật ấy cũng có thể tùy chọn. Dùng `ListIterator` của một danh sách không sửa được không biến nó thành editable.

### 8.1. Đừng nhầm fail-fast iterator với unsupported operation

Một iterator của một collection mutable có thể phát hiện rằng collection bị sửa cấu trúc từ bên ngoài trong lúc lặp và ném `ConcurrentModificationException` theo chính sách implementation. Đó **không phải** cùng điều kiện với `UnsupportedOperationException`.

- UOE: thao tác iterator được yêu cầu không được hỗ trợ.
- `IllegalStateException`: gọi thao tác ở trạng thái không hợp lệ theo giao thức Iterator.
- `ConcurrentModificationException`: implementation phát hiện tình huống sửa cấu trúc không đúng quy tắc trong quá trình lặp.

Không nên dựa vào fail-fast detection như một cơ chế đồng bộ thread. Đây là chủ đề khác với UOE nhưng thường bị nhầm khi code sửa collection trong vòng lặp.

## 9. Stream.toList và Collectors.toList không có cùng cam kết mutability

Từ Java 16, `Stream.toList()` trả một List **không sửa được**:

~~~java
List<String> items = Stream.of("A", "B").toList();
items.add("C"); // UnsupportedOperationException.
~~~

Ví dụ cần import `java.util.stream.Stream` và `java.util.List`. Trong Java 17, hành vi này là hợp đồng rõ ràng của `Stream.toList()`.

Ngược lại, với:

~~~java
List<String> items = stream.collect(Collectors.toList());
~~~

đây là đoạn minh họa cần biến `stream` và import `Collectors`. Javadoc của `Collectors.toList()` **không bảo đảm** kiểu implementation, mutability, serializability hay thread safety của list thu được. Vì vậy không nên biến hành vi tình cờ hiện tại thành một contract lâu dài.

Nếu yêu cầu kết quả **chắc chắn là ArrayList mutable**, có thể sử dụng:

~~~java
List<String> editable =
        Stream.of("A", "B").collect(Collectors.toCollection(ArrayList::new));
editable.add("C");
~~~

Cần import `ArrayList`, `List`, `Collectors`, `Stream`. Tại đây bạn chủ động chọn collection supplier, thay vì phỏng đoán từ tên collector.

**Mẹo:** Đọc kỹ Javadoc của factory/collector để phân biệt “unmodifiable theo đặc tả” với “đang modifiable trên một bản JDK cụ thể”.

## 10. Chương trình Java 17 hoàn chỉnh, kiểm chứng stdout

Tạo tệp **`OptionalOperationsDemo.java`**. Chương trình chạy thử nhiều API khác nhau. Các lời gọi dự kiến ném UOE được đặt trong helper `attempt` để tiếp tục minh họa những ca sau; **đây là kỹ thuật kiểm chứng trong bài học, không phải khuyến nghị bắt UOE rồi bỏ qua trong ứng dụng sản xuất**.

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class OptionalOperationsDemo {
    private static void attempt(String label, Runnable action) {
        try {
            action.run();
            System.out.println(label + "=supported");
        } catch (UnsupportedOperationException ex) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    public static void main(String[] args) {
        System.out.println("== fixed-size list backed by array ==");
        String[] words = {"red", "blue"};
        List<String> fixed = Arrays.asList(words);
        fixed.set(0, "green");
        System.out.println("fixed=" + fixed);
        System.out.println("array=" + Arrays.toString(words));
        attempt("fixed.add", () -> fixed.add("gold"));
        attempt("fixed.remove", () -> fixed.remove(0));
        System.out.println("fixed-after=" + fixed);

        System.out.println("== unmodifiable view ==");
        List<String> source = new ArrayList<>(List.of("one", "two"));
        List<String> view = Collections.unmodifiableList(source);
        System.out.println("view-before=" + view);
        attempt("view.add", () -> view.add("four"));
        attempt("view.set", () -> view.set(0, "changed"));
        source.add("three");
        System.out.println("view-after-source-change=" + view);

        System.out.println("== unmodifiable factories ==");
        List<String> immutable = List.of("a", "b");
        attempt("List.of.add", () -> immutable.add("c"));
        attempt("List.of.set", () -> immutable.set(0, "c"));
        List<String> copied = List.copyOf(source);
        attempt("List.copyOf.clear", copied::clear);
        List<String> streamList = Stream.of("x", "y").toList();
        attempt("Stream.toList.add", () -> streamList.add("z"));
        Map<String, Integer> map = Map.of("key", 1);
        attempt("Map.of.put", () -> map.put("other", 2));

        System.out.println("== iterator capabilities ==");
        List<String> editable = new ArrayList<>(List.of("first", "second"));
        Iterator<String> it = editable.iterator();
        it.next();
        it.remove();
        System.out.println("editable-after-remove=" + editable);
        Iterator<String> readOnlyIt = List.of("first").iterator();
        readOnlyIt.next();
        attempt("List.of.iterator.remove", readOnlyIt::remove);

        System.out.println("== explicitly mutable copy ==");
        List<String> mutableCopy = new ArrayList<>(immutable);
        mutableCopy.add("c");
        System.out.println("mutable-copy=" + mutableCopy);
        System.out.println("original=" + immutable);

        System.out.println("== null contract ==");
        System.out.println("Arrays.asList-null=" + Arrays.asList("ok", null));
        try {
            List.of("ok", (String) null);
        } catch (NullPointerException ex) {
            System.out.println("List.of-null=NullPointerException");
        }
    }
}
~~~

### Biên dịch và chạy

~~~bash
javac --release 17 -encoding UTF-8 OptionalOperationsDemo.java
java OptionalOperationsDemo
~~~

**Stdout chính xác được kiểm chứng bằng việc biên dịch và chạy:**

~~~text
== fixed-size list backed by array ==
fixed=[green, blue]
array=[green, blue]
fixed.add=UnsupportedOperationException
fixed.remove=UnsupportedOperationException
fixed-after=[green, blue]
== unmodifiable view ==
view-before=[one, two]
view.add=UnsupportedOperationException
view.set=UnsupportedOperationException
view-after-source-change=[one, two, three]
== unmodifiable factories ==
List.of.add=UnsupportedOperationException
List.of.set=UnsupportedOperationException
List.copyOf.clear=UnsupportedOperationException
Stream.toList.add=UnsupportedOperationException
Map.of.put=UnsupportedOperationException
== iterator capabilities ==
editable-after-remove=[second]
List.of.iterator.remove=UnsupportedOperationException
== explicitly mutable copy ==
mutable-copy=[a, b, c]
original=[a, b]
== null contract ==
Arrays.asList-null=[ok, null]
List.of-null=NullPointerException
~~~

Đầu ra cố ý in **tên class exception**, không dựa trên `getMessage()` của UOE (có thể không có message hoặc khác giữa implementation). Mã không cần file, mạng hoặc dependency bên ngoài; tương thích mức nguồn Java 17. Các thao tác dự kiến lỗi đã được bắt trong helper, không làm tiến trình dừng giữa chừng.

### 10.1. Diễn giải nhóm fixed-size list

- `fixed.set(0,"green")` thực thi thành công, thay phần tử trong list và **array nền**. Vì vậy hai dòng `fixed` và `array` đều là `[green, blue]`.
- `fixed.add("gold")` không được hỗ trợ do sẽ đổi kích thước.
- `fixed.remove(0)` không được hỗ trợ vì xóa phần tử làm thay đổi kích thước.
- Sau hai exception, nội dung list vẫn là `[green, blue]` trong ví dụ. Không có phần tử nào được thêm hoặc xóa.

**Chú ý:** Nếu một implementation khác ném UOE khi bạn cố dùng `set`, không có mâu thuẫn: `set` bản thân là một optional operation của List; việc `Arrays.asList` hỗ trợ set là hợp đồng riêng của danh sách đó.

### 10.2. Diễn giải view không sửa được

`Collections.unmodifiableList(source)` không cho `view.add` và `view.set`. Nhưng một reference khác là `source` vẫn có thể thêm phần tử `"three"`, nên view đọc được `[one, two, three]`.

Điều này chứng minh:

- **Cấm sửa qua view** không có nghĩa cấm mọi reference khác sửa đối tượng backing.
- Một API có thể trả read-only view để ngăn caller chủ động mutate collection qua reference đó, nhưng không tự tạo snapshot.
- Nếu cần tách cấu trúc khỏi nguồn, cân nhắc tạo snapshot theo hợp đồng cụ thể.

### 10.3. Diễn giải factories và stream

`List.of`, `List.copyOf`, `Stream.toList()` và `Map.of` đều tạo giá trị mà **các thao tác cập nhật được thử nghiệm không được hỗ trợ**. Chương trình in UOE cho `add`, `set`, `clear` hoặc `put` tương ứng.

Đừng coi đây là bằng chứng rằng mọi API `List` hoặc `Map` đều immutable. **Chính nguồn tạo và hợp đồng object** mới quyết định thao tác nào được hỗ trợ.

### 10.4. Diễn giải iterator

Iterator của `ArrayList` cho phép `remove` sau `next`. Iterator của `List.of` không cho thay đổi danh sách, nên `remove` ném UOE. Một reference được khai báo kiểu `Iterator<String>` không tự xác định khả năng remove.

### 10.5. Diễn giải mutable copy và null

`new ArrayList<>(immutable)` tạo list mới cho phép thêm phần tử, trong khi list gốc `immutable` giữ nguyên. `Arrays.asList("ok", null)` có thể chứa null, còn `List.of("ok", (String)null)` ném **NPE tại factory** vì không chấp nhận null.

Đây là hai loại lỗi khác nhau: **UOE** khi yêu cầu một thao tác cập nhật không hỗ trợ; **NPE** khi đầu vào null bị cấm. Đừng bắt UOE để xử lý dữ liệu null, hoặc ngược lại.

## 11. Optional không có nghĩa mọi lần gọi đều bắt buộc ném

Một điểm tinh tế trong Collections Framework: với các thao tác tùy chọn, hành vi **khi thao tác không có tác dụng thực sự** có thể phụ thuộc vào hợp đồng implementation.

Ví dụ có thể thử:

~~~java
Collection<String> items = Collections.unmodifiableCollection(source);
items.addAll(Collections.emptyList());
~~~

Đây là đoạn minh họa cần import `Collection`, `Collections` và biến `source` tương thích. Vì tập thêm là rỗng, nếu thao tác có xảy ra thì kích thước không đổi. **Không được suy một cách phổ quát** rằng trường hợp này luôn thành công hoặc luôn ném UOE đối với mọi Collection implementation.

Theo hợp đồng tổng quát của Collection, một số optional operations **có thể** ném exception ngay cả khi lần gọi không gây thay đổi. Implementation khác có thể chọn trả một kết quả như false khi không có phần tử nào được thêm.

Hậu quả thực tế:

- Không viết business logic dựa trên mẹo “thử `addAll(empty)` để biết Collection có editable không”.
- Không đoán mutability từ một lần gọi không làm thay đổi cấu trúc.
- Đừng cố **dò khả năng cập nhật bằng cách mutate thử đối tượng đang được chia sẻ**: phép thử có thể gây side effect nếu được hỗ trợ.
- Hãy đọc **Javadoc và hợp đồng API** của đối tượng được cung cấp, hoặc chủ động tạo một copy mutable theo yêu cầu.

Tương tự, `removeIf(predicate)` có thể trả về false khi không có phần tử thỏa điều kiện ở một implementation, nhưng thao tác cập nhật không được hỗ trợ ở implementation khác. Kết quả phụ thuộc vào chính sách của class và cách cài đặt method; không sử dụng sự kiện “không có phần tử bị xóa” như bằng chứng quyền sửa collection.

## 12. Không nên dùng catch UnsupportedOperationException làm cơ chế lựa chọn chính

Một anti-pattern:

~~~java
try {
    list.add(value);
} catch (UnsupportedOperationException ex) {
    // Bỏ qua để báo hoàn thành.
}
~~~

Đây là đoạn minh họa có biến `list` và `value` đã định nghĩa. Nếu ứng dụng cần **thực sự thêm phần tử** nhưng lỗi bị bỏ qua, chương trình có thể báo thành công sai và mất dữ liệu.

Tốt hơn, chọn chiến lược theo hợp đồng:

**Nếu caller bắt buộc truyền một list mutable:**

~~~java
static void appendRequired(List<String> destination, String value) {
    destination.add(value);
}
~~~

Phải ghi rõ trong tài liệu rằng `destination` cần hỗ trợ `add`, và để lỗi lan nếu caller vi phạm hợp đồng. Không cần bắt UOE rồi trả trạng thái giả.

**Nếu method nhận bất kỳ list nào và luôn cần kết quả mới:**

~~~java
static List<String> appendCopy(List<String> input, String value) {
    List<String> result = new ArrayList<>(input);
    result.add(value);
    return result;
}
~~~

Đây là các phương thức minh họa cần import `List` và `ArrayList`. Method thứ hai tạo mutable copy nên **không cần thay đổi input**. Nếu hợp đồng muốn kết quả không sửa được, có thể trả `List.copyOf(result)` khi phần tử khác null và điều kiện API phù hợp.

**Nếu tình huống “tác vụ không được hỗ trợ” là một đường hợp lệ của framework:** có thể xử lý UOE ở một **boundary được tài liệu hóa rõ**, ví dụ chuyển sang thông báo “tính năng này không được hỗ trợ”. Nhưng không nên dùng UOE như một kiểm tra quyền thao tác chung cho mọi collection, đặc biệt khi nó có thể che bug hoặc nhầm với lỗi truy cập nghiệp vụ.

## 13. Thiết kế API có chủ đích về mutability

### 13.1. Đọc dữ liệu và sửa dữ liệu là hai hợp đồng khác nhau

Một method chỉ **đọc** danh sách:

~~~java
static long countNonBlank(List<String> items) {
    return items.stream()
            .filter(s -> s != null && !s.isBlank())
            .count();
}
~~~

Đây là một method minh họa hợp lệ, cần import `List`. Nó không cần khả năng `add/remove/set`. Caller có thể truyền `ArrayList`, `Arrays.asList` hoặc unmodifiable List có dữ liệu phù hợp.

Ngược lại:

~~~java
static void addDefault(List<String> items) {
    items.add("default");
}
~~~

method này **đòi danh sách hỗ trợ add**. Kiểu `List<String>` một mình không biểu diễn được cam kết đó ở mức compile time. Viết JavaDoc, dùng type riêng khi cần một contract mạnh hơn hoặc trả một copy mới.

### 13.2. Snapshot và defensive copy

Nếu muốn trả kết quả cho caller **không thể sửa cấu trúc** và không chịu thay đổi từ source list mutable:

~~~java
List<String> result = List.copyOf(source);
return result;
~~~

Đây là đoạn minh họa trong method thích hợp, cần import `List`. `List.copyOf` từ chối null elements, vì vậy phải kiểm tra hợp đồng dữ liệu trước.

Nếu dữ liệu nguồn có thể chứa null và vẫn muốn một bản sao không cho sửa, một cách khác là:

~~~java
List<String> snapshot =
        Collections.unmodifiableList(new ArrayList<>(source));
~~~

Đoạn này tạo ArrayList mới, rồi tạo view không sửa được của **bản sao mới đó**; nếu code không giữ một mutable alias và không cho phép thay đổi phần tử bên trong theo cách không mong muốn, cấu trúc list giữ ổn định hơn so với wrapper trực tiếp quanh source. Nhưng bản sao vẫn **shallow**: phần tử mutable có thể đổi trạng thái.

### 13.3. Tránh tuyên bố “immutable” khi chỉ unmodifiable

Từ “immutable” có thể được hiểu là **toàn bộ trạng thái quan sát được không thay đổi**, nhưng unmodifiable list chỉ có hợp đồng rằng các thao tác cập nhật thông qua API list không được hỗ trợ. Nếu phần tử là `StringBuilder` hoặc object có setter, người giữ tham chiếu vẫn có thể thay đổi chúng.

Một **deeply immutable model** cần thiết kế cả object graph, không chỉ bọc một list. UOE giúp ngăn sửa list qua một reference, **không tự triển khai immutable object graph**.

## 14. Khi nào nên tự ném UnsupportedOperationException?

Một class triển khai interface có optional operation có thể ném UOE để công khai **không hỗ trợ thao tác ấy**:

~~~java
class ReadOnlyIntStore {
    private final List<Integer> values = List.of(1, 2, 3);

    int size() {
        return values.size();
    }

    void add(int value) {
        throw new UnsupportedOperationException("store is read-only");
    }
}
~~~

Đây là định nghĩa class minh họa, cần import `List`. Một API thiết kế rõ ràng có thể **không định nghĩa method add** ngay từ đầu nếu thao tác đó không thuộc năng lực của class. Nhưng khi một method xuất hiện do interface/abstract class có optional operation hoặc yêu cầu tương thích API, ném UOE có thể phù hợp.

**Không nên lạm dụng UOE:**

- Đừng dùng UOE thay cho `IllegalArgumentException` khi tham số sai, nếu thao tác vẫn được hỗ trợ với giá trị khác.
- Đừng dùng UOE thay cho `IllegalStateException` khi thao tác **có được hỗ trợ** nhưng object đang ở trạng thái tạm thời không phù hợp.
- Đừng dùng UOE cho lỗi phân quyền chỉ vì người dùng không được phép thao tác: API nghiệp vụ có thể cần một exception/response rõ về quyền truy cập.
- Đừng dùng UOE cho “chưa code xong” trong production một cách mơ hồ nếu hợp đồng đã hứa method phải hoạt động; cần làm đúng tính năng hoặc thay đổi hợp đồng.

Phân biệt khái niệm:

| Điều kiện | Exception thường phù hợp hơn |
| --- | --- |
| Thao tác không nằm trong năng lực được hỗ trợ bởi đối tượng/API | `UnsupportedOperationException` |
| Tham số không hợp lệ cho một thao tác vốn được hỗ trợ | `IllegalArgumentException`, NPE hoặc type phù hợp với contract |
| Đối tượng đang ở trạng thái không thể thực hiện thao tác vốn có | `IllegalStateException` |
| Chỉ số ngoài giới hạn list/array hợp lệ | `IndexOutOfBoundsException` hoặc subtype tương ứng |
| Iterator.remove không được triển khai hỗ trợ | `UnsupportedOperationException` |
| Iterator.remove được hỗ trợ nhưng gọi sai trình tự | `IllegalStateException` |

Điều kiện và exception cụ thể cuối cùng phải theo **Javadoc của API đang sử dụng**, không chỉ theo heuristic chung trong bảng.

## 15. AbstractCollection, AbstractList và các triển khai mặc định

Một số abstract base class trong Collections Framework cung cấp implementation mặc định cho optional operations. Ví dụ `AbstractCollection.add(E)` mặc định ném UOE, trừ khi subclass override với hành vi hỗ trợ thêm.

Một subclass có thể triển khai đủ các method truy vấn nhưng **không cung cấp một cách cập nhật tương ứng**, dẫn tới UOE khi caller gọi một optional operation. `AbstractList` cũng cung cấp những implementation mặc định có thể không hỗ trợ thao tác thêm/xóa/thay thế nếu subclass không triển khai.

Điều đó giải thích vì sao “implements List” **không tự đồng nghĩa** “sửa được như ArrayList”. Các lớp trợ giúp abstract giảm công viết code, nhưng chính subclass và contract quyết định khả năng mutation.

Khi tự viết collection:

1. Đọc kỹ contract của các method truy vấn và cập nhật.
2. Đừng ghi rằng collection editable trong JavaDoc nếu chỉ hỗ trợ `get` và `size`.
3. Nếu hỗ trợ một optional operation, triển khai cả các bất biến và lỗi cần thiết của nó.
4. Kiểm tra behavior của iterator: `remove`, `set`, `add` không nhất thiết xuất hiện đồng loạt.
5. Test các trường hợp cập nhật thành công, không hỗ trợ và sai trạng thái theo đúng contract.

## 16. Debug một UnsupportedOperationException thực tế

Khi thấy stack trace chứa:

~~~text
java.lang.UnsupportedOperationException
    at ...
    at com.example.Service.updateItems(Service.java:42)
~~~

đây là ví dụ **định dạng minh họa**, không phải stack trace cố định. Hãy làm theo các bước:

1. **Tìm method đầu tiên của code bạn** trên stack trace, ví dụ `updateItems` gọi `add` hoặc `remove`.
2. **Xác định đối tượng runtime thực sự**: nó được tạo bằng `new ArrayList`, `Arrays.asList`, `List.of`, `List.copyOf`, `Collections.unmodifiableList` hay lấy từ thư viện nào?
3. **Xem đúng operation**: thay thế (`set`) hay thay đổi kích thước (`add/remove`), hay thao tác qua iterator?
4. **Đọc Javadoc implementation** để xác định optional operation có hỗ trợ hay không.
5. **Tìm nơi tạo hoặc truyền collection**; đừng chỉ sửa đoạn method call nếu input contract là nguyên nhân.
6. **Xác định có yêu cầu mutation thật không**. Nếu không, tránh mutate; nếu có, quyết định tạo copy hay yêu cầu caller cung cấp mutable collection.
7. **Kiểm thử với nhiều implementation** phù hợp hợp đồng API, không chỉ ArrayList.
8. **Kiểm tra aliasing và threading** nếu dùng unmodifiable view có backing collection thay đổi ở nơi khác.

Nếu chỉ gọi `list.get(0)` và nhận UOE thì đó là hành vi bất thường đối với List chuẩn ở thao tác truy vấn này; cần đọc implementation thực sự và stack trace chứ không mặc định mọi UOE đều do `add`.

**Không dùng nội dung message của UOE làm logic:** một số implementation có thể ném UOE không có message, hoặc message khác giữa phiên bản. Dựa trên API contract và loại exception có ý nghĩa hơn việc parse message.

## 17. Những hiểu nhầm và lỗi phổ biến

1. **“Nếu implements List thì add luôn hoạt động.”** Sai: add là optional operation.
2. **“`Arrays.asList` trả ArrayList mutable bình thường.”** Sai: fixed-size list backed by array.
3. **“`Arrays.asList` không đổi kích thước thì mọi sửa đổi đều bị cấm.”** Sai: `set` được hỗ trợ.
4. **“UnmodifiableList là snapshot.”** Không nhất thiết: `Collections.unmodifiableList` trả một view phản ánh thay đổi source.
5. **“`List.of` chỉ cấm add, còn set được.”** Sai: set cũng ném UOE.
6. **“`List.copyOf` luôn tạo object list mới.”** Sai: API có thể tái sử dụng instance unmodifiable thích hợp.
7. **“Unmodifiable list đồng nghĩa phần tử hoàn toàn bất biến.”** Sai: mutable element có thể đổi trạng thái.
8. **“Unmodifiable view đồng nghĩa thread-safe.”** Sai: backing collection vẫn có thể bị sửa không đồng bộ.
9. **“`Stream.toList()` của Java 17 trả ArrayList editable.”** Sai: kết quả không sửa được.
10. **“`Collectors.toList()` bảo đảm editable.”** Javadoc không bảo đảm điều đó.
11. **“`getOrDefault`, `addAll(empty)` hay `removeIf` luôn kiểm tra được mutability.”** Sai: behavior có thể phụ thuộc implementation và trạng thái.
12. **“Iterator nào cũng cho remove.”** Sai: `Iterator.remove` là optional operation.
13. **“Iterator.remove ném IllegalStateException tức thao tác không được hỗ trợ.”** Không nhất thiết: có thể do gọi sai trình tự.
14. **“NPE khi `List.of(null)` cũng là UnsupportedOperationException.”** Sai: loại lỗi khác vì factory từ chối null.
15. **“Bắt UOE rồi bỏ qua luôn an toàn.”** Sai: dễ làm mất dữ liệu và báo thành công giả.
16. **“Có thể xác định mutability của mọi List bằng `instanceof List`.”** Sai: kiểu chung không mô tả toàn bộ khả năng cập nhật.
17. **“Tạo `new ArrayList<>(source)` sẽ deep-copy toàn bộ object.”** Sai: chỉ copy cấu trúc và tham chiếu phần tử.
18. **“Map có put thì mọi map đều cho put.”** Sai: `Map.of` và unmodifiable wrappers chặn mutation.
19. **“UOE luôn là lỗi do lập trình viên chưa code method.”** Sai: nhiều optional operations cố ý không được hỗ trợ theo thiết kế.
20. **“Không có thay đổi thực tế thì method trên unmodifiable collection nhất định thành công.”** Sai: có optional operation vẫn có thể ném UOE.
21. **“UOE là checked exception.”** Sai: `UnsupportedOperationException extends RuntimeException`.
22. **“`Collections.unmodifiableList` bảo vệ nguồn khỏi mọi reference khác.”** Sai: mutable alias vẫn có thể sửa source.

## 18. Bài tập tự kiểm tra

**Câu 1.** Đoạn sau có in `[B, A]` không?

~~~java
String[] a = {"A", "B"};
List<String> l = Arrays.asList(a);
l.set(0, "B");
System.out.println(Arrays.toString(a));
~~~

*Đáp án:* In `[B, B]`. Thao tác set thay vị trí 0 của mảng nền; vị trí 1 giữ `"B"`.

**Câu 2.** `Arrays.asList("A", "B").add("C")` gặp lỗi gì?

*Đáp án:* `UnsupportedOperationException` vì danh sách có kích thước cố định, không hỗ trợ thêm phần tử.

**Câu 3.** Vì sao `Collections.unmodifiableList(source)` có thể thay đổi dữ liệu quan sát được dù caller không được gọi add qua wrapper?

*Đáp án:* Nó là **view** của source. Một reference khác vẫn có thể mutate source, khiến view phản ánh thay đổi.

**Câu 4.** `List.of("A").set(0, "B")` có hợp lệ không?

*Đáp án:* Gọi được về mặt cú pháp nhưng ném UOE ở runtime, vì list không cho thay thế phần tử.

**Câu 5.** Muốn thêm phần tử vào kết quả `Stream.toList()` trên Java 17, cách nào rõ ràng?

*Đáp án:* Tạo mutable copy: `new ArrayList<>(stream.toList())` rồi thêm phần tử, hoặc dùng `Collectors.toCollection(ArrayList::new)` ngay từ đầu.

**Câu 6.** `List.copyOf(source)` có luôn tạo shallow-independent structure khỏi ArrayList nguồn không?

*Đáp án:* Với source là ArrayList mutable, list thu được không bị ảnh hưởng về cấu trúc bởi lần add/remove sau trên source. Nhưng API có thể tái sử dụng một input đã là unmodifiable phù hợp, và phần tử mutable vẫn có thể thay đổi trạng thái.

**Câu 7.** Vì sao `List.of("A", (String)null)` không ném UOE mà ném NPE?

*Đáp án:* Lỗi xảy ra **khi tạo danh sách** vì List.of cấm phần tử null, chưa phải một lời gọi thao tác cập nhật không được hỗ trợ.

**Câu 8.** Với iterator ArrayList, `remove()` ngay khi chưa gọi `next()` báo loại gì?

*Đáp án:* `IllegalStateException` theo quy tắc giao thức Iterator, khác với UOE khi iterator không hỗ trợ remove.

**Câu 9.** Vì sao không nên “thử `addAll(Collections.emptyList())`” để xác định xem một collection có thể sửa không?

*Đáp án:* Với optional operations không có tác dụng thực tế, hợp đồng có thể cho phép các implementation phản ứng khác nhau; phép thử không chắc chắn xác định năng lực mutation.

**Câu 10.** `Map.of("A", 1).put("B", 2)` ném lỗi gì?

*Đáp án:* `UnsupportedOperationException` vì map không sửa được.

**Câu 11.** Nếu method nhận `List<String>` mà phải append, điều gì cần công bố?

*Đáp án:* Cần tài liệu hóa rằng tham số phải hỗ trợ add, hoặc thiết kế method tạo/trả mutable copy để không phụ thuộc khả năng sửa input.

**Câu 12.** Có thể dùng `UnsupportedOperationException` cho “dữ liệu người dùng không hợp lệ” không?

*Đáp án:* Không nên nếu operation bản thân có được hỗ trợ; loại lỗi tham số hoặc validation phù hợp hơn.

## 19. Tài liệu tham khảo chính thức Java 17

- [API UnsupportedOperationException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/UnsupportedOperationException.html): class unchecked biểu thị thao tác được yêu cầu không được hỗ trợ.
- [API Collection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html): optional operations và hành vi của chúng.
- [API List](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html): các method cập nhật, `List.of`, `List.copyOf` và unmodifiable lists.
- [API Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)): fixed-size list backed by array.
- [API Collections.unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)): unmodifiable view trên list nguồn.
- [API Iterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html): `remove()` tùy chọn và trạng thái iterator.
- [API ListIterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ListIterator.html): các thao tác cập nhật của iterator hai chiều.
- [API Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): `put/remove` và các unmodifiable map factories.
- [API Stream.toList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList()): kết quả List không sửa được.
- [API Collectors.toList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html#toList()): không bảo đảm mutability của list.
- [API AbstractCollection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractCollection.html): các implementation mặc định của optional operations.
- [API AbstractList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html): hỗ trợ tạo List với các khả năng cập nhật khác nhau.

**Ghi nhớ:** `UnsupportedOperationException` là unchecked exception thường xuất hiện khi gọi một **optional operation** mà đối tượng không hỗ trợ. Đừng đánh đồng `List` với `ArrayList`, fixed-size với unmodifiable, view với snapshot, hoặc unmodifiable với deep immutable. Để tránh UOE một cách đúng đắn, hãy **xác định contract mutability từ nơi tạo dữ liệu và từ API**, và nếu cần sửa cấu trúc, tạo một mutable copy phù hợp thay vì thử-catch lỗi rồi bỏ qua.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-runtime-exceptions-011">ConcurrentModificationException và giới hạn của cơ chế fail-fast</a>

<details>
<summary>Xem chi tiết</summary>

# `ConcurrentModificationException` và giới hạn của cơ chế fail-fast trong Java

> **Bối cảnh:** Java Core → Exception → Ngoại lệ runtime. `java.util.ConcurrentModificationException` (CME) thường xuất hiện khi chương trình **thay đổi cấu trúc một collection trong lúc đang duyệt nó** bằng iterator theo cơ chế *fail-fast*. Tên gọi “Concurrent” dễ gây hiểu nhầm: **không cần có hai thread** mới phát sinh exception. Ngược lại, trong chương trình đa luồng, **không có CME cũng không chứng minh code an toàn**. Fail-fast là công cụ phát hiện bug theo nỗ lực tốt nhất (*best effort*), **không phải** cơ chế đồng bộ hóa, khóa, transaction hay cam kết phát hiện mọi thay đổi.

## 1. Hiểu đúng bản chất của `ConcurrentModificationException`

CME là một lớp exception unchecked:

```text
java.lang.Throwable
└── java.lang.Exception
    └── java.lang.RuntimeException
        └── java.util.ConcurrentModificationException
```

Một tình huống phổ biến:

```java
// Mảnh code minh họa, không phải tệp standalone.
List<String> names = new ArrayList<>(
        List.of("An", "Bình", "Chi"));

for (String name : names) {
    if ("Bình".equals(name)) {
        names.remove(name); // Thay đổi collection bên ngoài iterator.
    }
}
```

Mã trên **có thể** ném CME tùy trạng thái duyệt; vì fail-fast là best effort nên **đừng dùng chính đoạn mã đó để khẳng định exception luôn xuất hiện**. Đặc biệt, một thao tác xóa có thể khiến iterator hết phần tử trước lần kiểm tra tiếp theo. Các ví dụ kiểm chứng ở phần sau chủ động chọn dữ liệu và thứ tự để lỗi phát sinh ổn định trong JDK 17.

Ý nghĩa chính:

- **Nguồn bất nhất:** một iterator đang dùng giả định về cấu trúc collection, trong khi code khác thay đổi cấu trúc mà không thông qua cơ chế cho phép của iterator.
- **Phản ứng:** iterator *có thể* phát hiện sự thay đổi và ném CME, thay vì tiếp tục dựa trên trạng thái đã lỗi thời.
- **Không phải bằng chứng chắc chắn:** cơ chế fail-fast không bảo đảm luôn phát hiện và cũng không bảo đảm an toàn thread.
- **Không bắt buộc có concurrency:** một method, một thread, vừa duyệt vừa xóa bằng `list.remove(...)` cũng đủ tạo điều kiện phát sinh CME.
- **Không phải checked exception:** không bắt buộc khai báo `throws ConcurrentModificationException` hay thêm `catch` để code biên dịch.

### 1.1. CME không có nghĩa collection bị hỏng vĩnh viễn

Exception báo rằng một hoạt động duyệt không thể tiếp tục an toàn theo hợp đồng/triển khai fail-fast. Nó không tự nói rằng toàn bộ bộ nhớ hay collection đã hỏng vĩnh viễn. Tuy nhiên **trạng thái nghiệp vụ có thể đã thay đổi một phần trước lỗi**: phần tử có thể đã bị xóa, giá trị có thể đã được cập nhật, hoặc các side effect khác đã xảy ra.

Vì vậy khi xử lý CME, phải phân biệt:

1. **Phát hiện đường logic sai** và sửa ở vị trí tạo ra lỗi.
2. **Đánh giá trạng thái đã thay đổi**; CME không rollback các thao tác trước đó.
3. **Không dùng catch để tiếp tục iterator cũ** rồi hy vọng trạng thái tự phục hồi.

## 2. “Concurrent” nhưng một thread vẫn có thể gây lỗi

Ví dụ một chương trình tuần tự:

```text
Một thread
  ├── tạo ArrayList
  ├── tạo Iterator
  ├── iterator.next() trả phần tử đầu
  ├── list.remove(...) thay đổi cấu trúc trực tiếp
  └── iterator.next() kiểm tra trạng thái → có thể CME
```

Đó là sự *xung đột giữa thao tác duyệt và thay đổi cấu trúc*, không nhất thiết là *data race giữa các thread*.

Một ví dụ khác: callback của `forEach` sửa chính danh sách đang được duyệt. Cả quá trình vẫn có thể chạy trong một thread nhưng vi phạm giả định của iterator.

Ngược lại, có nhiều thread mà **không** CME, ví dụ khi:

- Thực hiện thao tác đồng thời trên một collection có cơ chế iterator *weakly consistent* như `ConcurrentHashMap`.
- Duyệt snapshot của `CopyOnWriteArrayList`.
- Các thread đồng bộ hóa đầy đủ, khiến việc duyệt không chồng lên một thay đổi cấu trúc nguy hiểm.
- Chỉ có các thao tác không ảnh hưởng cấu trúc theo hợp đồng cụ thể.

Vì thế đừng “chữa” CME bằng cách đơn giản tắt đa luồng; hãy xác định **iterator, loại collection, cách sửa và phạm vi ownership**.

## 3. Fail-fast: khái niệm, mục đích và phạm vi

*Fail-fast iterator* cố phát hiện khi cấu trúc collection bị thay đổi ngoài các thao tác được iterator cho phép trong lúc duyệt. Đối với một số collection phổ biến như `ArrayList` và `HashMap`, iterator fail-fast giúp bug xuất hiện sớm thay vì để chương trình tiếp tục với một kết quả khó dự đoán.

Mục đích là **phát hiện vi phạm giao ước**, không phải “cấm sửa collection vĩnh viễn”.

Ví dụ với `ArrayList`:

- Sau khi tạo iterator, việc gọi `list.add(...)` hoặc `list.remove(...)` có thể làm iterator trở nên không hợp lệ.
- `iterator.remove()` sau một lần `next()` hợp lệ có thể xóa phần tử hiện tại **và đồng bộ trạng thái iterator**, nên duyệt có thể tiếp tục.
- `ListIterator.add(...)` và `ListIterator.remove()` cũng là các con đường chỉnh sửa có hợp đồng riêng.
- Không phải mọi phương thức có tên “thay đổi” đều là *structural modification*: `list.set(index, newValue)` thay giá trị tại vị trí đã có mà **không đổi số phần tử** trong `ArrayList`; thông thường không khiến iterator fail-fast chỉ vì thay giá trị.
- Ngược lại, việc sắp xếp hoặc một số thao tác đặc thù của implementation có thể tác động tới dấu hiệu thay đổi dù size không đổi. Đừng biến “size thay đổi” thành định nghĩa tuyệt đối cho mọi class và API.

### 3.1. Fail-fast không đồng nghĩa với synchronized

Một iterator fail-fast **không tự khóa collection**. Nếu hai thread cùng đọc/ghi vào `ArrayList` không được bảo vệ, chúng có thể gặp data race, quan sát kết quả sai, mất cập nhật, CME hoặc lỗi khác. Ngay cả khi thấy CME trong một ca test, nó cũng không cung cấp quan hệ *happens-before* hay sự an toàn truy cập ở ca chạy khác.

Một cách dùng sai:

```java
// Anti-pattern: không dùng exception như đồng hồ báo động đồng bộ.
try {
    for (String x : sharedList) {
        consume(x);
    }
} catch (ConcurrentModificationException e) {
    // Giả sử mọi tình huống unsafe đều đã được phát hiện.
}
```

Đây là mã minh họa chưa định nghĩa `sharedList`/`consume`. Việc bắt CME không sửa tính thiếu đồng bộ. Hãy thiết kế ownership, khóa, snapshot hoặc concurrent collection tùy yêu cầu.

## 4. Cơ chế điển hình: `modCount` và `expectedModCount`

Trong nhiều implementation collection của JDK, cơ chế fail-fast dựa vào hai giá trị:

- **`modCount`:** bộ đếm sửa đổi của cấu trúc collection, thường được cập nhật khi có thay đổi cấu trúc.
- **`expectedModCount`:** giá trị mà iterator ghi nhận tại thời điểm tạo hoặc sau thao tác sửa hợp lệ thông qua chính iterator.
- Khi iterator thực hiện thao tác có kiểm tra và thấy hai giá trị **khác nhau**, nó có thể ném `ConcurrentModificationException`.

Sơ đồ:

```text
ArrayList: modCount = 5
Iterator mới: expectedModCount = 5

iterator.next()
  expected = 5, actual = 5 → tiếp tục

list.remove(0) trực tiếp
  modCount = 6

iterator.next()
  expected = 5, actual = 6 → fail-fast (CME)
```

**Lưu ý về tính chính xác:** các tên `modCount` và `expectedModCount` là chi tiết triển khai của các collection/iterator cụ thể, không phải một API `Iterator` công khai để mọi collection đều phải sử dụng. Những container khác có thể phát hiện thay đổi theo cách khác, hoặc không theo fail-fast. Không viết logic ứng dụng phụ thuộc giá trị bộ đếm nội bộ.

### 4.1. Vì sao `Iterator.remove()` có thể hợp lệ?

Khi xóa đúng cách qua iterator:

```java
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String value = it.next();
    if (shouldRemove(value)) {
        it.remove();
    }
}
```

đây là **mã minh họa** cần biến `list` và `shouldRemove` thích hợp. Iterator tự thực hiện thao tác sửa và cập nhật thông tin nội bộ cần thiết để tiếp tục duyệt trong trạng thái nhất quán với chính nó.

Ngược lại, `list.remove(value)` tác động trực tiếp lên container trong khi iterator đang sống, nên trạng thái quan sát của iterator có thể không còn khớp.

**Không thể kết luận mọi iterator hỗ trợ `remove()`:** phương thức `Iterator.remove()` là optional operation; nhiều iterator từ collection bất biến, snapshot hoặc read-only không hỗ trợ và có thể ném `UnsupportedOperationException`. Phải đọc hợp đồng loại iterator đang dùng.

### 4.2. Việc kiểm tra diễn ra lúc nào?

Một số iterator kiểm tra tại `next()`, `remove()` hoặc các thao tác khác. Với `ArrayList`, `hasNext()` thông thường so vị trí với kích thước mà không bắt buộc kiểm tra `modCount` ngay trong chính phương thức đó. Do vậy có thể xảy ra tình huống thay đổi cấu trúc **không được phát hiện** trước khi vòng lặp kết thúc.

Đừng khẳng định “mọi lần `hasNext()` đều kiểm tra concurrent modification” hoặc “một khi list bị sửa, chắc chắn lần tiếp theo sẽ ném CME”. Đây chính là một giới hạn của cơ chế.

## 5. Java 17 — ví dụ 1: cố tình kích hoạt CME trong một thread

Tạo tệp **`SingleThreadCmeDemo.java`**:

```java
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

public class SingleThreadCmeDemo {
    public static void main(String[] args) {
        List<String> names =
                new ArrayList<>(List.of("An", "Bình", "Chi", "Dũng"));

        Iterator<String> iterator = names.iterator();
        System.out.println("First: " + iterator.next());

        names.add("Em"); // Thay đổi cấu trúc qua list, không qua iterator.

        try {
            System.out.println("Second: " + iterator.next());
        } catch (ConcurrentModificationException e) {
            System.out.println("Caught: "
                    + e.getClass().getSimpleName());
        }

        System.out.println("Size after add: " + names.size());
    }
}
```

Biên dịch/chạy bằng JDK 17:

```bash
javac -encoding UTF-8 SingleThreadCmeDemo.java
java SingleThreadCmeDemo
```

Stdout khi chạy trên `ArrayList` của JDK 17:

```text
First: An
Caught: ConcurrentModificationException
Size after add: 5
```

Phân tích:

1. `iterator` được tạo khi `names` chứa bốn phần tử.
2. `iterator.next()` đầu tiên đọc `"An"`.
3. `names.add("Em")` thay đổi cấu trúc container sau khi iterator được tạo.
4. `iterator.next()` thứ hai phát hiện sự không khớp và ném CME theo cơ chế fail-fast của `ArrayList`.
5. Chương trình chỉ có **một thread** và vẫn ném CME.
6. Phần tử `"Em"` **đã được thêm** trước khi lỗi xuất hiện; catch không rollback thay đổi.

Ví dụ sử dụng lời gọi `next()` rõ ràng sau thay đổi để quan sát failure của JDK 17. Nó không biến quy tắc **best effort** thành hợp đồng phải ném CME ở mọi implementation và mọi lịch thực thi.

## 6. Enhanced for và Iterator: cú pháp ngắn vẫn có quy tắc duyệt

Với một `Iterable`, vòng lặp:

```java
for (String value : list) {
    consume(value);
}
```

thường được biên dịch theo cấu trúc sử dụng `Iterator` tương đương về ý tưởng:

```java
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String value = it.next();
    consume(value);
}
```

Hai đoạn đều là ví dụ minh họa, cần biến/method phù hợp. Vì thế, việc gọi `list.remove(...)` hoặc `list.add(...)` ngay trong vòng `for-each` của chính list đó có thể vi phạm hợp đồng iterator.

**Phân biệt:** enhanced for trên **mảng** có cơ chế duyệt riêng bằng chỉ số và không tạo iterator collection fail-fast; không thể suy mọi vòng `for (x : something)` đều sử dụng `modCount`.

### 6.1. Ví dụ anti-pattern

```java
List<Integer> numbers =
        new ArrayList<>(List.of(1, 2, 3, 4));

for (Integer value : numbers) {
    if (value == 2) {
        numbers.remove(value);
    }
}
```

Đây là mảnh code minh họa không được dựa vào để bảo đảm ném CME trong mọi trường hợp. Nếu code này chạy mà **không ném lỗi**, nó vẫn là cách sửa dễ sai khi duyệt và không nên được coi là đã đúng.

Cách diễn đạt chính xác là: thay cấu trúc list bằng phương thức của chính list trong khi một iterator fail-fast đang duyệt **có thể** dẫn tới CME; phải dùng API sửa tương thích với iterator hoặc chuyển sang chiến lược khác.

## 7. Java 17 — ví dụ 2: xóa đúng bằng `Iterator.remove()`

Tạo tệp **`IteratorRemoveDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IteratorRemoveDemo {
    public static void main(String[] args) {
        List<Integer> numbers =
                new ArrayList<>(List.of(1, 2, 3, 4, 5));

        Iterator<Integer> it = numbers.iterator();
        while (it.hasNext()) {
            int value = it.next();
            if (value % 2 == 0) {
                it.remove();
            }
        }

        System.out.println("Remaining: " + numbers);
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 IteratorRemoveDemo.java
java IteratorRemoveDemo
```

Stdout:

```text
Remaining: [1, 3, 5]
```

Giải thích:

- `it.next()` cung cấp phần tử hiện tại; `it.remove()` xóa **phần tử vừa được iterator trả về**.
- Iterator biết rõ thao tác xóa và có thể tiếp tục duyệt.
- Phần tử chẵn `2` và `4` được loại bỏ, còn thứ tự các phần tử lẻ giữ nguyên.
- Không cần gọi `numbers.remove(value)` trực tiếp trong vòng lặp.
- `Iterator.remove()` không hỗ trợ truyền một phần tử bất kỳ làm tham số; nó chỉ xóa đối tượng tương ứng lần `next()`/`previous()` hợp lệ gần nhất (đối với `ListIterator`).

### 7.1. `remove()` có giao ước về trạng thái

Một iterator `remove()` chỉ được phép sau một lời gọi `next()` thành công và thông thường tối đa **một lần** cho mỗi phần tử được trả về trước thao tác tiếp theo. Nếu gọi `remove()` trước `next()`, hoặc gọi hai lần liên tục mà không có `next()` khác, `ArrayList` iterator có thể ném `IllegalStateException`.

`UnsupportedOperationException` lại mang nghĩa khác: iterator **không hỗ trợ** xóa (ví dụ iterator trên list bất biến).

CME, `IllegalStateException` và `UnsupportedOperationException` là **ba điều kiện khác nhau**, không thể chữa bằng cùng một catch bỏ qua.

## 8. Java 17 — ví dụ 3: `ListIterator` thêm/sửa/xóa hợp lệ

`ListIterator<E>` mở rộng `Iterator<E>` để cho phép duyệt hai chiều, chèn phần tử và thay phần tử hiện tại khi danh sách hỗ trợ.

Tạo tệp **`ListIteratorEditDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class ListIteratorEditDemo {
    public static void main(String[] args) {
        List<String> tasks =
                new ArrayList<>(List.of("A", "B", "C"));

        ListIterator<String> it = tasks.listIterator();
        while (it.hasNext()) {
            String task = it.next();
            if ("B".equals(task)) {
                it.set("B-done");
                it.add("B-extra");
            }
        }

        System.out.println("Tasks: " + tasks);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ListIteratorEditDemo.java
java ListIteratorEditDemo
```

Stdout:

```text
Tasks: [A, B-done, B-extra, C]
```

Với `ArrayList`:

1. `it.set("B-done")` thay giá trị của phần tử vừa được `next()` trả về.
2. `it.add("B-extra")` thêm phần tử tại vị trí hiện tại của iterator theo hợp đồng.
3. Iterator quản lý state và vị trí của chính nó, nên vòng duyệt tiếp tục mà không mắc lỗi do chính các thao tác này.
4. Thao tác `add` của `ListIterator` là optional với một số implementation; phải chọn list mutable hỗ trợ.
5. Sau `it.add(...)`, quy tắc cho lần gọi `remove()/set()` tiếp theo thay đổi theo trạng thái của `ListIterator`; không được giả định gọi `set` ngay sau `add` luôn hợp lệ.

**Không nên nhầm:** có một `ListIterator` không có nghĩa code khác được phép sửa chính list bằng `list.add(...)` mà iterator sẽ luôn thích ứng. Việc cập nhật hợp lệ ở đây được thực hiện **qua cùng iterator**.

## 9. Cách xử lý đơn giản: `removeIf`

Với collection hỗ trợ xóa, API `removeIf(Predicate<? super E>)` thường giúp diễn đạt mục tiêu “xóa các phần tử thỏa điều kiện” rõ ràng hơn việc tự điều khiển iterator.

```java
List<Integer> numbers =
        new ArrayList<>(List.of(1, 2, 3, 4, 5));

numbers.removeIf(n -> n % 2 == 0);
System.out.println(numbers);
```

Mảnh code trong method có đầu ra:

```text
[1, 3, 5]
```

### 9.1. Java 17 — ví dụ `removeIf` có đầu ra xác định

Tạo **`RemoveIfDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class RemoveIfDemo {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>(
                List.of("An", "Bình", "Chi", "Dũng"));

        boolean changed =
                names.removeIf(s -> s.length() <= 2);

        System.out.println("Changed: " + changed);
        System.out.println("Names: " + names);

        boolean changedAgain =
                names.removeIf(s -> s.startsWith("X"));
        System.out.println("Changed again: " + changedAgain);
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 RemoveIfDemo.java
java RemoveIfDemo
```

Stdout:

```text
Changed: true
Names: [Bình, Chi, Dũng]
Changed again: false
```

**Giải thích:**

- `removeIf` gọi predicate cho các phần tử và loại những phần tử phù hợp.
- Ở ví dụ, `"An"` và `"Chi"` có số UTF-16 code units lần lượt là 2 và 3; điều kiện `length() <= 2` **chỉ xóa `"An"`**, không xóa `"Chi"`.
- Giá trị `changed` bằng `true` vì có phần tử được xóa.
- Lần hai không có tên bắt đầu bằng `X` nên `changedAgain` là `false`.
- API ngắn hơn vòng lặp thủ công và có cơ chế xóa phù hợp với implementation khi được dùng đúng hợp đồng.

**Chú ý:** kết quả `Names` ở ví dụ này được xác định theo phép kiểm tra ký tự; hãy dùng kết quả thật từ điều kiện, không suy tên tiếng Việt có hai/chỉ ba “chữ” theo trực giác. Nếu mục tiêu xóa các tên ngắn hơn bốn code units, predicate cần là `s.length() < 4`, và kết quả sẽ khác.

> Lưu ý khi học: `removeIf` **không có nghĩa predicate được phép sửa cấu trúc collection bằng cách khác**. Nếu bên trong predicate lại gọi `names.add(...)` hoặc `names.remove(...)`, code vẫn có thể gặp CME hoặc kết quả không xác định theo hợp đồng. Predicate nên thuần theo phần tử khi có thể.

## 10. “Không đổi size” có đảm bảo không CME?

**Không có quy tắc phổ quát cho mọi collection** rằng “hễ kích thước không đổi thì không thể CME”. Cần xem loại sửa đổi được implementation coi là structural hoặc invalidating iteration.

### 10.1. Với `ArrayList`: `set` chỉ thay giá trị

`ArrayList.set(index, element)` thay giá trị ở vị trí đã tồn tại mà không thay số phần tử. Trong `ArrayList` thông thường, việc gọi `set` không tăng dấu hiệu structural modification nên iterator không tự fail-fast **chỉ do `set`**.

Ví dụ:

```java
List<String> values =
        new ArrayList<>(List.of("A", "B", "C"));
Iterator<String> it = values.iterator();

values.set(1, "B2");
while (it.hasNext()) {
    System.out.println(it.next());
}
```

Đây là mảnh code minh họa. Với `ArrayList` của Java 17, có thể in `A`, `B2`, `C` mà không có CME.

Tuy nhiên:

- Việc nhìn thấy giá trị thay đổi giữa lúc duyệt không có nghĩa thao tác đa luồng đã an toàn.
- Những collection khác có hành vi iterator/metadata riêng.
- Thay đổi thứ tự phần tử bằng `sort` hoặc thay đổi cấu trúc có thể bị phát hiện khác với `set`, kể cả size không đổi.

### 10.2. Với `HashMap`: thay value cho key đã có khác với thêm/xóa key

Khi duyệt `HashMap.entrySet()`, thêm một **key mới** hoặc xóa một entry có thể làm iterator fail-fast. Nhưng `map.put(existingKey, newValue)` chỉ thay giá trị của một entry đã có, thông thường **không phải structural modification**.

Đây là phân biệt giữa *thay thế value* và *thêm/xóa mapping*, không phải lời hứa đồng bộ hóa trong môi trường nhiều thread.

## 11. Java 17 — ví dụ 5: `HashMap` cũng có iterator fail-fast

Tạo tệp **`HashMapCmeDemo.java`**:

```java
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HashMapCmeDemo {
    public static void main(String[] args) {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("A", 10);
        scores.put("B", 20);

        Iterator<String> it = scores.keySet().iterator();
        System.out.println("Has first: " + it.hasNext());

        scores.put("C", 30); // Thêm key: sửa cấu trúc.

        try {
            it.next();
        } catch (ConcurrentModificationException e) {
            System.out.println("HashMap: "
                    + e.getClass().getSimpleName());
        }

        System.out.println("Size: " + scores.size());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 HashMapCmeDemo.java
java HashMapCmeDemo
```

Stdout trên `HashMap` JDK 17:

```text
Has first: true
HashMap: ConcurrentModificationException
Size: 3
```

**Tại sao ví dụ không in thứ tự key?** `HashMap` không bảo đảm thứ tự lặp key; nếu in chuỗi key theo iteration order, stdout có thể khác khi implementation hoặc dữ liệu thay đổi. Ở đây mục tiêu là kiểm tra CME sau thao tác `put` thêm key, không phụ thuộc thứ tự bucket.

### 11.1. Muốn xóa entry khi duyệt Map?

Có thể dùng iterator của `entrySet()` và `iterator.remove()` khi implementation hỗ trợ, hoặc dùng `map.entrySet().removeIf(predicate)` theo hợp đồng.

```java
Map<String, Integer> scores = new HashMap<>();
scores.put("A", 10);
scores.put("B", 20);

scores.entrySet().removeIf(e -> e.getValue() < 15);
System.out.println(scores.size()); // 1
```

Đây là mảnh code trong method. Đừng gọi `scores.remove(e.getKey())` trực tiếp trong enhanced-for của `scores.entrySet()` và mong iterator thích nghi.

## 12. Giới hạn lớn nhất: fail-fast là **best effort**

Tài liệu về các iterator fail-fast của collection chuẩn nhấn mạnh một ý quan trọng: **không thể bảo đảm phát hiện đồng thời không được đồng bộ hóa một cách chắc chắn**. Cơ chế fail-fast phải được dùng để **phát hiện bug**, không dùng để xây dựng logic chính xác của chương trình.

### 12.1. Những tình huống CME có thể không xuất hiện

1. Thao tác sửa diễn ra **trước khi iterator được tạo**: iterator mới thấy trạng thái đã sửa.
2. Thao tác không được coi là structural/invalidation với iterator cụ thể.
3. Iterator **không thực hiện thêm phép kiểm tra** sau khi sửa, ví dụ vòng lặp đã kết thúc hoặc `hasNext()` quyết định không còn phần tử.
4. Có thao tác sửa xen kẽ giữa các thread không có đồng bộ hóa đúng; kết quả nhìn thấy và việc kiểm tra không có bảo đảm phát hiện.
5. Collection sử dụng iterator snapshot/weakly consistent, không được thiết kế để ném CME vì các sửa đổi thường lệ.
6. Một API có cơ chế traversal hoặc spliterator khác với iterator mà bạn đang hình dung.

**Kết luận:** “không ném CME” không đồng nghĩa “code hợp lệ” hoặc “không có concurrent modification”. Những lần chạy khác nhau có thể cho kết quả khác nhau nếu chương trình có data race.

### 12.2. Không dùng CME như tín hiệu chính xác để retry

Anti-pattern:

```java
while (true) {
    try {
        traverse(sharedMutableList);
        break;
    } catch (ConcurrentModificationException ignored) {
        // Retry vô hạn với hy vọng cuối cùng không lỗi.
    }
}
```

Đây là mã minh họa, `traverse` và `sharedMutableList` chưa định nghĩa. Cách này có thể:

- Lặp vô hạn nếu collection liên tục bị sửa.
- Không phát hiện một số thay đổi bất đồng bộ vì CME chỉ best effort.
- Chạy lại thao tác có side effect, dẫn đến trùng lặp.
- Tạo CPU spin và làm system khó ổn định.
- Không tạo quan hệ memory visibility hoặc atomicity.

Nếu yêu cầu traversal nhất quán, dùng thiết kế có **khóa đúng**, **snapshot**, **immutable state**, hoặc collection/algorithm cung cấp semantics phù hợp.

## 13. Một tình huống tinh vi: xóa mà không thấy CME

`ArrayList` iterator trên JDK 17 thường kiểm tra thay đổi khi `next()`. `hasNext()` có thể chỉ so sánh cursor và size. Vì vậy đoạn dưới đôi khi **không ném CME**, dù sửa list trực tiếp trong vòng enhanced-for là cách làm sai.

Xem ví dụ có hai phần tử:

```java
List<String> names =
        new ArrayList<>(List.of("A", "B"));

for (String name : names) {
    if ("A".equals(name)) {
        names.remove("A");
    }
}
```

Đây là **mảnh code chỉ để phân tích**, không bảo đảm cùng hành vi trên mọi List. Sau khi xóa `A`, size giảm còn 1; iterator đã đi qua một phần tử nên cursor cũng bằng 1. Lời gọi `hasNext()` tiếp theo có thể trả `false` và vòng lặp kết thúc **mà không gọi thêm `next()` để kiểm tra modCount**.

Điều quan trọng:

- Việc không thấy exception **không làm đoạn code đúng**.
- Cơ chế có thể khác theo implementation và phiên bản.
- Nhiều bug tương tự tồn tại âm thầm cho đến khi dữ liệu hoặc thứ tự thay đổi.
- Đây là bằng chứng minh họa giới hạn *best effort*, không phải mẹo để tránh CME.

### 13.1. Java 17 — chương trình minh họa giới hạn kiểm tra

Tạo **`FailFastNotGuaranteedDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FailFastNotGuaranteedDemo {
    public static void main(String[] args) {
        List<String> values =
                new ArrayList<>(List.of("A", "B"));

        Iterator<String> it = values.iterator();
        System.out.println("First: " + it.next());

        values.remove("A");

        System.out.println("Has next: " + it.hasNext());
        System.out.println("Contents: " + values);
    }
}
```

Chạy JDK 17:

```bash
javac -encoding UTF-8 FailFastNotGuaranteedDemo.java
java FailFastNotGuaranteedDemo
```

Stdout theo hành vi `ArrayList.Itr.hasNext` trên JDK 17:

```text
First: A
Has next: false
Contents: [B]
```

**Diễn giải:** `it` được tạo trước khi list sửa đổi, nhưng sau khi xóa A, `hasNext` thấy cursor bằng size và trả false. Code không gọi tiếp `next()`, nên không xuất hiện phép kiểm tra tiếp theo có thể báo CME. Nó chứng minh fail-fast **không bảo đảm phát hiện mọi sự thay đổi**, ngay khi chỉ có một thread.

Không chuyển hành vi riêng của JDK 17 thành một điều kiện để điều khiển nghiệp vụ. Nếu logic cần biết một list có bị sửa hay không, hãy sở hữu/truyền dữ liệu theo một hợp đồng nhất quán thay vì quan sát có/không có CME.

## 14. Duyệt snapshot: `CopyOnWriteArrayList`

`java.util.concurrent.CopyOnWriteArrayList` dùng cơ chế sao chép mảng nội bộ khi thực hiện các thao tác sửa đổi cấu trúc phù hợp. Iterator của nó làm việc với một **snapshot tại thời điểm tạo iterator**, không bị ảnh hưởng bởi các cập nhật sau đó và **không ném CME do các sửa đổi thường lệ** của list.

Điểm mạnh:

- Phù hợp với trường hợp **đọc rất nhiều, cập nhật ít**, ví dụ một danh sách listener tương đối nhỏ.
- Duyệt có view ổn định theo snapshot của iterator.
- Thao tác đọc/traversal không phải tranh cùng khóa với mọi lượt sửa theo cách synchronized list truyền thống.

Đổi lại:

- Việc cập nhật thường phải sao chép mảng, chi phí lớn với list to/cập nhật liên tục.
- Iterator snapshot **không phản ánh** cập nhật mới sau lúc iterator được tạo.
- `Iterator.remove()`, `set()` và `add()` không được hỗ trợ trên snapshot iterator, nên có thể ném `UnsupportedOperationException`.
- Iterator snapshot không phải một transaction toàn hệ thống; chỉ là view cụ thể của collection.

### 14.1. Java 17 — ví dụ snapshot iterator

Tạo **`CopyOnWriteSnapshotDemo.java`**:

```java
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class CopyOnWriteSnapshotDemo {
    public static void main(String[] args) {
        CopyOnWriteArrayList<String> names =
                new CopyOnWriteArrayList<>();

        names.add("A");
        names.add("B");

        Iterator<String> snapshot = names.iterator();
        names.add("C");

        System.out.print("Snapshot:");
        while (snapshot.hasNext()) {
            System.out.print(" " + snapshot.next());
        }
        System.out.println();

        System.out.println("Current: " + names);

        try {
            snapshot.remove();
        } catch (UnsupportedOperationException e) {
            System.out.println("Iterator remove unsupported");
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 CopyOnWriteSnapshotDemo.java
java CopyOnWriteSnapshotDemo
```

Stdout:

```text
Snapshot: A B
Current: [A, B, C]
Iterator remove unsupported
```

Dù `C` được thêm trước khi duyệt snapshot, iterator đã được **tạo trước đó**, nên chỉ nhìn thấy A và B. Đây là semantics có chủ đích của snapshot, không phải một failure bị lặng lẽ bỏ qua.

**Khi chọn:** tránh dùng CopyOnWriteArrayList cho workload ghi thường xuyên hoặc list lớn nếu chi phí copy đáng kể; đo đạc và kiểm tra use case. Cũng đừng nhầm “iterator không CME” với “mọi phép phối hợp nhiều thao tác giữa các thread đều atomic”.

## 15. Weakly consistent iterator: `ConcurrentHashMap`

`java.util.concurrent.ConcurrentHashMap` là map được thiết kế cho truy cập đồng thời. Iterator trên các view như `keySet()`, `values()`, `entrySet()` có semantics **weakly consistent**:

- Có thể duyệt trong khi map bị cập nhật.
- Không ném CME do các cập nhật đồng thời thông thường.
- **Không phải snapshot cố định**: iterator có thể nhìn thấy một số cập nhật trong quá trình duyệt.
- Không cam kết thấy **mọi** thay đổi xảy ra trong lúc duyệt.
- Không cung cấp một ảnh chụp giao dịch nhất quán của toàn map trong trường hợp có cập nhật song song.

Sự khác biệt với CopyOnWriteArrayList:

| Đặc điểm | `CopyOnWriteArrayList` iterator | `ConcurrentHashMap` iterator |
| --- | --- | --- |
| Kiểu consistency | Snapshot tại thời điểm tạo | Weakly consistent |
| Thấy phần tử mới được thêm sau khi tạo iterator? | Không | Có thể, không bảo đảm |
| CME do sửa collection thường lệ? | Không theo cơ chế snapshot | Không theo cơ chế weakly consistent |
| Chi phí update điển hình | Sao chép backing array | Cấu trúc concurrent map, chi phí khác |
| Có thể coi traversal là transaction toàn bộ dữ liệu? | Không | Không |
| Phù hợp nhất | Rất nhiều lượt đọc, ít sửa | Map nhiều thread cùng truy cập/cập nhật |

### 15.1. Java 17 — ví dụ không phụ thuộc thứ tự Map

Tạo **`ConcurrentHashMapIteratorDemo.java`**:

```java
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentHashMapIteratorDemo {
    public static void main(String[] args) {
        ConcurrentHashMap<String, Integer> counts =
                new ConcurrentHashMap<>();
        counts.put("A", 1);
        counts.put("B", 2);

        Iterator<String> it = counts.keySet().iterator();
        counts.put("C", 3);

        int seen = 0;
        while (it.hasNext()) {
            it.next();
            seen++;
        }

        System.out.println("Map size: " + counts.size());
        System.out.println("Seen between 0 and 3: "
                + (seen >= 0 && seen <= 3));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ConcurrentHashMapIteratorDemo.java
java ConcurrentHashMapIteratorDemo
```

Stdout:

```text
Map size: 3
Seen between 0 and 3: true
```

Chương trình không in một thứ tự key hoặc số entry bắt gặp cụ thể, vì điều quan trọng là API **không đảm bảo tập thay đổi mới xuất hiện đầy đủ trong traversal**. Dù ví dụ chỉ có một thread, nó đủ minh họa việc iterator CHM vẫn có thể duyệt sau khi map được cập nhật mà không được thiết kế để phát CME.

Không nên viết assert rằng `seen` phải bằng 3 sau khi thêm C **sau lúc tạo iterator**. Đó không phải điều contract weakly consistent hứa.

## 16. `Collections.synchronizedList`: iterator vẫn cần đồng bộ ngoài

`Collections.synchronizedList(...)` trả wrapper đồng bộ hóa nhiều thao tác riêng lẻ của list. Nhưng khi **duyệt bằng iterator** trong khi có những thao tác khác, tài liệu yêu cầu người dùng **tự đồng bộ hóa toàn bộ quãng iteration trên chính wrapper** nếu muốn loại trừ thay đổi chồng lấn.

Mẫu thường gặp:

```java
List<String> sync =
        Collections.synchronizedList(new ArrayList<>());

synchronized (sync) {
    Iterator<String> it = sync.iterator();
    while (it.hasNext()) {
        System.out.println(it.next());
    }
}
```

Đây là đoạn minh họa cần import `List`, `Collections`, `ArrayList` và `Iterator`. Quan trọng:

1. Bọc list bằng synchronized wrapper **không biến iterator thành snapshot**.
2. Các thao tác thêm/xóa khác phải **thực sự dùng cùng wrapper/khóa**, không truy cập backing list riêng bỏ qua wrapper.
3. Giữ khóa trong suốt việc tạo iterator và duyệt.
4. Nếu trong lúc giữ khóa chính thread đó tự sửa cấu trúc list bằng `sync.remove(...)` thay vì dùng iterator, vẫn có thể phát CME vì synchronized không “chữa” quy tắc iterator.
5. Lock ngăn thread khác sửa trong vùng được bảo vệ **khi tất cả bên tham gia tôn trọng cùng cơ chế**.

### 16.1. Java 17 — ví dụ an toàn theo cùng khóa

Tạo **`SynchronizedListTraversalDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class SynchronizedListTraversalDemo {
    public static void main(String[] args) {
        List<Integer> sync = Collections.synchronizedList(
                new ArrayList<>(List.of(10, 20, 30)));

        synchronized (sync) {
            Iterator<Integer> it = sync.iterator();
            int sum = 0;
            while (it.hasNext()) {
                sum += it.next();
            }
            System.out.println("Sum: " + sum);
        }

        sync.add(40);
        synchronized (sync) {
            System.out.println("Size: " + sync.size());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 SynchronizedListTraversalDemo.java
java SynchronizedListTraversalDemo
```

Stdout:

```text
Sum: 60
Size: 4
```

Đây là demo một thread để nội dung chạy xác định; trong code đa thread, cả bên thêm/xóa và bên duyệt cần thống nhất khóa/ownership. Không nên mở lock rộng rồi gọi code bên ngoài không kiểm soát, bởi có thể tăng contention hoặc dẫn đến deadlock theo thiết kế tổng thể.

### 16.2. Vì sao không chỉ khóa từng lần `hasNext`/`next`?

Nếu mỗi thao tác có một vùng khóa riêng và giữa chúng thread khác được phép sửa list, iterator vẫn có thể bị invalidated. Để có một traversal không bị thay đổi cấu trúc xen vào, giữ cùng khóa trong **toàn bộ** quãng duyệt hoặc chụp một snapshot rồi lặp trên snapshot.

## 17. Cách dùng snapshot bất biến khi chỉ cần đọc nhất quán

Nếu dữ liệu không quá lớn và yêu cầu nhất quán theo một thời điểm cụ thể, có thể chụp danh sách:

```java
List<String> snapshot = List.copyOf(source);
for (String value : snapshot) {
    process(value);
}
```

Mảnh code minh họa này cần `source` và `process`, giả định không có phần tử `null` vì `List.copyOf` từ chối `null`. Sau khi snapshot tạo xong, thay đổi cấu trúc ở `source` không thay đổi danh sách snapshot. Nếu phần tử bên trong là object mutable thì `List.copyOf` **không deep-copy chúng**.

**Ranh giới quan trọng:** tạo snapshot từ một list đang bị thread khác sửa **không đồng bộ** vẫn là một vấn đề consistency/concurrency. Cần có giao thức đọc/chụp phù hợp: cùng khóa, publication an toàn hoặc nguồn concurrent với semantics chấp nhận được. `List.copyOf` không tự biến một thao tác đọc trên backing data-race thành ảnh chụp atomic hoàn hảo.

### 17.1. Snapshot vs locking vs concurrent collections

- **Snapshot:** phù hợp khi có thể chấp nhận dữ liệu tĩnh trong suốt một lần xử lý, muốn hạn chế lock ở giai đoạn duyệt.
- **Locking:** phù hợp khi cần một view nhất quán với các thao tác cập nhật dùng cùng cơ chế và có thể khóa toàn bộ quãng duyệt.
- **Concurrent collections:** phù hợp khi muốn đọc/ghi đồng thời theo các contract yếu hoặc mạnh khác nhau; cần đọc semantics cho từng operation.
- **Immutable/persistent data:** phù hợp khi thiết kế có thể trao bản dữ liệu không thay đổi giữa các thread, giúp loại bỏ một lớp bug về iterator.

Không có lựa chọn “an toàn nhất cho mọi ứng dụng”; consistency, tần suất cập nhật, kích thước dữ liệu và độ trễ đều ảnh hưởng.

## 18. `subList`: view và thay đổi backing list

`List.subList(from, to)` thường tạo một **view** dựa trên một list nguồn, không phải bản sao độc lập. Khi backing list bị sửa cấu trúc **ngoài phạm vi view hoặc không thông qua view theo đúng hợp đồng**, trạng thái sublist có thể trở nên không hợp lệ. Một số implementation phát CME khi phát hiện thay đổi này.

Ví dụ minh họa:

```java
List<String> original =
        new ArrayList<>(List.of("A", "B", "C", "D"));
List<String> view = original.subList(1, 3);

original.add("E"); // Thay đổi backing list không qua view.

// Các thao tác tiếp theo trên view có thể thất bại hoặc
// có hành vi ngoài hợp đồng subList; không tiếp tục sử dụng như bình thường.
```

Đây là mảnh code trong method. Đừng xem CME khi dùng `subList` là “trùng hợp đa luồng”. Ngay trong một thread, backing list thay đổi sau lúc view được tạo đã đủ tạo điều kiện vi phạm.

### 18.1. Muốn list độc lập hãy tạo bản sao

```java
List<String> snapshot =
        new ArrayList<>(original.subList(1, 3));
```

Đây là bản sao cấu trúc danh sách tại thời điểm tạo, khác với view gắn với backing list. Với object mutable, những phần tử vẫn có thể là tham chiếu chung, nên không có nghĩa deep copy.

**Lưu ý chính xác về hợp đồng:** Tài liệu `List.subList` nói semantics của list trả về có thể **không được xác định** nếu backing list bị sửa cấu trúc theo cách không qua sublist. CME là một kết quả có thể thấy trên một implementation cụ thể, không phải ngoại lệ được bảo đảm trong mọi tình huống.

## 19. Stream, spliterator và callback sửa nguồn

Một Stream có thể được tạo từ collection:

```java
List<Integer> values = new ArrayList<>(List.of(1, 2, 3, 4));

values.stream().forEach(v -> {
    if (v == 2) {
        values.add(5);
    }
});
```

Đây là **anti-pattern**, không phải code được khuyến nghị. Callback sửa chính nguồn dữ liệu khi pipeline đang duyệt có thể vi phạm quy tắc **non-interference** của Stream. Tùy pipeline và implementation, có thể xuất hiện CME, phần tử bị bỏ sót/lặp, hoặc kết quả khó dự đoán; không xem một kiểu lỗi duy nhất là bảo đảm.

### 19.1. Stream có luôn fail-fast không?

Không. Stream dùng cơ chế spliterator và pipeline, các đặc tính thực tế phụ thuộc nguồn và loại operation:

- Nguồn có tính `IMMUTABLE` hoặc `CONCURRENT` có contract khác với `ArrayList`.
- Một số spliterator có đặc tính late-binding hoặc kiểm tra tại những thời điểm khác nhau.
- Với stream song song, thời điểm các worker quan sát thay đổi càng khó đoán.
- Không thể suy “mọi stream sửa nguồn sẽ ném CME” hoặc “nếu không ném CME thì kết quả đúng”.

**Giải pháp thường tốt:** xây một danh sách kết quả mới bằng `filter`/`map` rồi sử dụng kết quả đó, hoặc hoàn tất traversal trước khi sửa collection gốc. Với collection mutable, `removeIf` thường thích hợp hơn để xóa theo predicate.

### 19.2. Mẫu tạo kết quả mới không sửa nguồn

```java
List<Integer> source = List.of(1, 2, 3, 4);
List<Integer> odds = source.stream()
        .filter(v -> v % 2 != 0)
        .toList();

System.out.println(odds); // [1, 3]
```

Đây là đoạn trong method, dùng Java 16+ `Stream.toList()` (có ở Java 17). Danh sách kết quả của `toList()` là unmodifiable theo hợp đồng; không nên mặc định có thể `add` vào nó.

### 19.3. Mutating predicate cũng có rủi ro

`list.removeIf(x -> { list.add(...); return ...; })` cũng là cách vừa duyệt vừa sửa nguồn ngoài cơ chế mà implementation `removeIf` quản lý. Predicate được thiết kế để **quyết định xóa** phần tử, không để sửa cấu trúc list trên đường chạy của predicate. Nếu cần side effect phức tạp, tách các pha rõ ràng để dễ kiểm thử.

## 20. `HashMap`, `HashSet` và `LinkedHashMap`: không chỉ List

Trong `java.util`, nhiều collection truyền thống cung cấp iterator fail-fast, ví dụ `ArrayList`, `HashMap`, `HashSet` và `LinkedHashMap`. Nhưng luôn đọc contract của implementation cụ thể.

- `HashSet` thường được backing bởi cấu trúc hash; thêm/xóa phần tử có thể làm iterator cũ không còn hợp lệ.
- `HashMap` có key/value/entry views; thay đổi mapping cấu trúc của map tác động lên iterator của các view này.
- `LinkedHashMap` có thứ tự duyệt và có mode access-order; trong cấu hình access-order, một số thao tác truy cập có thể thay đổi thứ tự cấu trúc, khiến iterator bị invalidated theo hợp đồng.
- Đừng dùng “không thay đổi số phần tử” làm tiêu chuẩn duy nhất để suy một iterator an toàn với mọi collection.

### 20.1. Access-order `LinkedHashMap` là trường hợp đáng chú ý

Nếu cấu hình `LinkedHashMap` kiểu **access-order** để làm LRU, thao tác `get(key)` có thể làm đổi thứ tự iteration. Trong implementation này, một thao tác “chỉ đọc value” có thể kéo theo thay đổi cấu trúc theo nghĩa ảnh hưởng iteration.

Vì vậy ngay cả khi không gọi `add/remove`, cần đọc contract về iteration order và structural modification. Không được khái quát từ `ArrayList.set` sang `LinkedHashMap.get` trong access-order mode.

### 20.2. Một số phương thức sửa đổi hợp lệ khác nhau giữa Map và Iterator

`Map.replace(key, value)` hoặc `entry.setValue(...)` có thể thay value của entry mà không sửa tập key; điều đó khác với `put` thêm key mới. Nhưng nếu có cấu trúc access-order, mutable view hoặc tùy implementation, tác động lên iteration có thể khác. Muốn code portable, dựa trên API contract của collection thay vì suy từ tên method.

## 21. CME không phải công cụ khóa hay cơ chế kiểm soát tính đúng

Một code review tốt nên đặt câu hỏi:

1. Có bao nhiêu **thread** có thể truy cập collection?
2. Ai **sở hữu** collection? Có chia sẻ tham chiếu mutable không?
3. Iterator được tạo ở đâu và còn sống đến khi nào?
4. Ai có quyền thêm/xóa/sắp xếp trong khi iterator đang duyệt?
5. Có yêu cầu **snapshot nhất quán** hay chỉ cần quan sát gần thời gian thực?
6. Có cần **atomicity** qua nhiều thao tác, ví dụ kiểm tra rồi cập nhật?
7. Sửa collection có side effect ngoài container không?
8. Có thể tách pha **duyệt/tính toán** và **thực hiện thay đổi** được không?
9. Nếu chuyển sang concurrent collection, contract về iteration consistency có chấp nhận được không?
10. Test nào chứng minh hành vi có ý nghĩa, không chỉ chứng minh “không thấy CME”?

Cách tiếp cận hướng contract tốt hơn một “try/catch CME” chung cho mọi collection.

## 22. Những lỗi thường gặp và cách sửa

| Nhầm lẫn | Vấn đề | Cách làm hợp lý |
| --- | --- | --- |
| “CME chỉ có khi hai thread cùng chạy.” | Một thread cũng có thể sửa list ngoài iterator. | Kiểm tra logic lặp và cách sửa. |
| “CME là checked exception.” | Nó kế thừa `RuntimeException`. | Không cần khai báo bắt buộc; sửa bug ở nguồn. |
| “Không có CME nên thread-safe.” | Fail-fast chỉ best effort; một số iterator không fail-fast. | Khóa, ownership, snapshot, concurrent contract. |
| “Mọi collection đều có `modCount`.” | Đó là chi tiết triển khai của một số class. | Đọc hợp đồng iterator cụ thể. |
| “Mọi `hasNext()` đều kiểm tra sửa đổi.” | Không được bảo đảm; ArrayList có thể bỏ qua. | Không dựa vào CME để phát hiện mọi thay đổi. |
| “Sửa size bằng 0 thì iterator chắc an toàn.” | Thứ tự hay trạng thái nội bộ cũng có thể bị invalidated. | Xét structural modification theo class/API. |
| “Dùng `list.remove(...)` trong for-each là ổn nếu chạy thử không lỗi.” | Có thể chỉ tình cờ thoát trước lần kiểm tra tiếp. | Dùng `Iterator.remove()` hoặc `removeIf`. |
| “`Iterator.remove` được gọi bất kỳ lúc nào.” | Có state machine và optional operation. | Gọi sau `next`, một lần mỗi lượt phù hợp. |
| “`removeIf` cho phép predicate tự thêm/xóa list.” | Có thể vi phạm semantics của traversal. | Giữ predicate đơn giản và không sửa nguồn. |
| “`Collections.synchronizedList` làm iterator tự an toàn.” | Iteration cần khóa ngoài xuyên suốt quãng duyệt. | `synchronized(list) { ... iterator ... }`. |
| “Chỉ khóa `next()` từng lượt là đủ.” | Thread khác sửa giữa các lượt. | Khóa cả traversal hoặc dùng snapshot. |
| “CopyOnWrite iterator thấy cập nhật mới.” | Nó duyệt snapshot lúc tạo. | Chọn semantics snapshot có chủ đích. |
| “CopyOnWrite iterator hỗ trợ `remove`.” | Iterator snapshot không hỗ trợ sửa trực tiếp. | Sửa qua list hoặc chiến lược phù hợp. |
| “ConcurrentHashMap iterator có ảnh chụp đúng một thời điểm.” | Nó weakly consistent, không transactional snapshot. | Chọn algorithm tương ứng. |
| “Đổi ArrayList sang concurrent collection luôn chữa được logic.” | Semantics về ordering, consistency, cost khác nhau. | Định nghĩa yêu cầu rồi chọn collection. |
| “Nếu Stream không CME thì side effects đúng.” | Non-interference có thể bị vi phạm. | Tách source và result, dùng filter/removeIf đúng chỗ. |
| “`subList` là bản sao độc lập.” | Nó thường là view của backing list. | Copy nếu cần độc lập. |
| “CME tự rollback thao tác đã xảy ra.” | Exception không hoàn tác. | Thiết kế transaction/compensation nếu cần. |
| “Có thể catch rồi retry vô hạn.” | Có thể spin, lặp side effect, vẫn không bảo đảm consistency. | Thiết kế đồng bộ hoặc snapshot đúng. |
| “`List.set` luôn gây CME như `add`.” | ArrayList.set không đổi cấu trúc và thường không làm invalidation. | Phân biệt mutation nội dung vs structural. |
| “`HashMap.put` luôn structural.” | Thay value cho key đã có khác thêm mapping. | Xét có thêm/xóa key hay không. |

## 23. Kỹ năng debug khi gặp `ConcurrentModificationException`

Khi stack trace báo CME:

1. **Tìm chính xác collection** đang được duyệt, không chỉ xem tên class ở đầu trace.
2. Xác định kiểu traversal: enhanced for, `Iterator`, `ListIterator`, `forEach`, stream/spliterator hay view từ `subList`.
3. Tìm **thao tác sửa cấu trúc** có thể diễn ra giữa lúc tạo iterator và lúc kiểm tra: `add`, `remove`, `clear`, `sort`, method gọi gián tiếp, callback hay thread khác.
4. Xem các thao tác sửa có đi qua **chính iterator** hay không.
5. Nếu có nhiều thread, xác minh **cùng một khóa** hay concurrent collection phù hợp; không chỉ bọc catch.
6. Nếu dùng `subList`, xác định backing list có bị sửa cấu trúc trực tiếp không.
7. Nếu dùng access-order `LinkedHashMap`, xem liệu `get` có thay iteration order không.
8. Kiểm tra quyền sở hữu, lifecycle và alias: cùng list có thể được truyền qua nhiều method/tầng.
9. Tạo test đơn thread có thể tái hiện đường sửa sai nếu bug thuần logic; với concurrency dùng test/stress test phù hợp nhưng **không** dựa vào một lần “không CME” để kết luận an toàn.
10. Sửa invariant, chọn API xóa đúng, snapshot hoặc synchronization; kiểm tra trạng thái/side effect sau lỗi.

### 23.1. Vì sao stack trace thường chỉ vào `next()`?

CME có thể được ném **tại chỗ phát hiện**, không phải đúng dòng đầu tiên làm thay đổi collection. Stack trace có thể chỉ vào `ArrayList$Itr.next`, trong khi thao tác sai `list.add/remove` đã xảy ra ở dòng khác trước đó.

Vì vậy cần truy ngược đường sửa cấu trúc, không kết luận `next()` là “hàm sai” hoặc JDK bị lỗi. Nếu thay đổi đến từ callback sâu hay thread khác, quan sát ownership và log ngữ cảnh có thể quan trọng hơn xem một dòng stack đơn lẻ.

### 23.2. Logging nên ghi gì?

Có thể ghi tên thao tác, loại collection, kích thước tại vị trí an toàn, ID tác vụ/correlation id và exception, nhưng không cần dump toàn bộ collection chứa dữ liệu nhạy cảm. Với lỗi concurrency, log thứ tự sự kiện có ngữ cảnh giúp tái hiện hơn là chỉ message của CME (thường không chứa nhiều chi tiết).

## 24. Kiểm thử và chứng minh hành vi

Một số hướng test:

- **Iterator invalidation đơn thread:** tạo `ArrayList` iterator, gọi `next()`, sửa cấu trúc, rồi gọi `next()`; trên JDK 17 kỳ vọng CME ở demo có kiểm soát.
- **Iterator.remove:** xác minh đúng tập phần tử sau khi xóa và không cần catch.
- **ListIterator:** xác minh thêm/sửa qua iterator với list mutable.
- **removeIf:** xác minh predicate lọc đúng, không có side effect ngoài dự kiến.
- **CopyOnWriteArrayList:** tạo iterator, thêm mới rồi kiểm tra snapshot không thấy phần tử mới.
- **ConcurrentHashMap:** kiểm tra các phép đồng thời hợp lệ theo API, **không assert thứ tự hoặc việc iterator phải thấy toàn bộ cập nhật xen giữa**.
- **synchronizedList:** dùng test thiết kế cùng khóa, không coi việc không có CME trong stress test là bằng chứng duy nhất.
- **Stream:** kiểm thử kết quả lọc/tính toán, tránh các callback sửa source.

### 24.1. Ví dụ JUnit 5 minh họa

```java
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

class FailFastContractTest {
    @Test
    void iteratorDetectsDirectStructuralChangeOnArrayListJdk17() {
        List<String> values =
                new ArrayList<>(List.of("A", "B", "C"));
        Iterator<String> it = values.iterator();
        assertEquals("A", it.next());
        values.add("D");

        assertThrows(ConcurrentModificationException.class, it::next);
    }

    @Test
    void iteratorRemoveIsLegal() {
        List<Integer> values =
                new ArrayList<>(List.of(1, 2, 3, 4));
        Iterator<Integer> it = values.iterator();

        while (it.hasNext()) {
            if (it.next() % 2 == 0) {
                it.remove();
            }
        }

        assertEquals(List.of(1, 3), values);
    }
}
```

Đây là **file test dành cho JUnit Jupiter**, không phải chương trình `javac` chạy độc lập chỉ với JDK. Test thứ nhất cố tình kiểm chứng hành vi `ArrayList` JDK 17 trong hoàn cảnh cụ thể; **không có nghĩa mọi collection fail-fast trên mọi JVM buộc phải ném CME trong mọi lịch thực thi**.

### 24.2. Test concurrency không giống xác minh hợp đồng concurrency

Một stress test chạy thành công nhiều lần vẫn không đủ chứng minh code có data race là đúng. Phải đối chiếu với **Java Memory Model**, bảo đảm đồng bộ và hợp đồng collection. Nếu cần tính chính xác cao, thiết kế bằng invariant, ownership, atomic operations hoặc cấu trúc concurrent phù hợp trước, rồi dùng test để phát hiện sai sót.

## 25. Các câu hỏi thường gặp

**1. Vì sao một thread vẫn gây CME?** Vì code có thể sửa cấu trúc `ArrayList` bên ngoài iterator đang duyệt ngay trong một thread. “Concurrent” trong tên không yêu cầu có hai thread.

**2. Thêm `synchronized` có luôn chữa CME?** Không. Khóa đúng cách giúp các thread phối hợp, nhưng cùng thread gọi `list.remove(...)` trong lúc duyệt bằng iterator vẫn có thể phá hợp đồng; khi được hỗ trợ, dùng `Iterator.remove()`.

**3. Vì sao đôi lúc có CME, đôi lúc không?** Phát hiện fail-fast chỉ mang tính **best effort**; vị trí cursor, lượt kiểm tra và tình huống đồng thời ảnh hưởng việc phát hiện. Không dùng exception làm test tính đúng duy nhất.

**4. Có thể catch CME rồi khởi tạo iterator mới và retry?** Chỉ nếu đã xác định tính nhất quán dữ liệu, side effect và khả năng retry; đó không phải giải pháp mặc định thay cho ownership hoặc đồng bộ hóa.

**5. `ArrayList.set` có luôn invalidated iterator?** Không. Trên `ArrayList`, thay phần tử đã có thường không phải sửa cấu trúc; điều này **không** tạo bảo đảm thread-safe.

**6. `removeIf` có áp dụng cho mọi collection?** Không. Collection không hỗ trợ xóa có thể ném `UnsupportedOperationException`; predicate cũng không nên tự sửa nguồn đang duyệt.

**7. Snapshot và weakly consistent có giống nhau?** Không. `CopyOnWriteArrayList` iterator dùng snapshot tại lúc tạo, còn iterator của `ConcurrentHashMap` có thể quan sát một phần cập nhật xen kẽ. “Fail-safe” không phải tên hợp đồng chính thức của hai kiểu iterator đó.

**8. Fail-fast có phát hiện đầy đủ data race?** Không. Việc có hay không có CME không thay thế các quy tắc Java Memory Model, khóa và publication đúng.

**9. Dùng `Collections.synchronizedList` hay `CopyOnWriteArrayList`?** Chọn theo tần suất đọc/ghi, chi phí copy và tính nhất quán cần có; với synchronized list vẫn cần khóa **suốt thời gian traversal** bằng iterator.

**10. Vì sao `subList` liên quan CME?** `subList` thường là view của backing list; sửa cấu trúc backing list ngoài cơ chế được hỗ trợ có thể làm view/iterator không còn hợp lệ.

**11. `filter` và `removeIf` khác nhau?** `filter` mô tả chọn phần tử để tạo pipeline/kết quả, trong khi `removeIf` sửa nguồn khi collection hỗ trợ thao tác; không can thiệp cấu trúc nguồn từ callback Stream.

**12. CME có tự rollback thao tác trước đó không?** Không. Kiểm tra state đã thay đổi; ví dụ thao tác `add` có thể hoàn tất trước khi lần kiểm tra iterator tiếp theo ném CME.

Các câu trả lời tóm tắt ở đây cần được hiểu cùng mô hình iterator, độ nhất quán và ví dụ đã phân tích ở những mục trước; không dùng chúng thay cho kiểm thử hợp đồng của collection cụ thể.

## 26. Bài tập tự luyện

### Bài 1 — Một thread có CME

Chạy `SingleThreadCmeDemo` và vẽ thứ tự tạo iterator → next → add → next. Vì sao việc có một thread không tránh CME? **Gợi ý:** iterator đã ghi nhận cấu trúc cũ và bị sửa ngoài cơ chế của nó.

### Bài 2 — Sửa đúng bằng iterator

Chuyển đoạn for-each xóa tên khỏi list sang `Iterator.remove()`. Test danh sách có 0, 1, 2 và nhiều phần tử. Kiểm tra trường hợp xóa phần tử đầu, giữa và cuối.

### Bài 3 — Dùng `ListIterator`

Mở rộng `ListIteratorEditDemo`: thay `A` thành `A-done`, chèn thêm `B-extra` sau B, rồi xóa C bằng `it.remove()` sau khi `next()` trả C. Dự đoán danh sách cuối và chạy xác minh.

### Bài 4 — `removeIf` không phụ thuộc text fail-fast

Dùng `List.of("An","Bình","Chi","Dũng")` làm đầu vào, tạo mutable copy rồi xóa những chuỗi có `length() <= 2`. Dự đoán chính xác tập còn lại theo `String.length()`, không đếm ký tự trực quan.

### Bài 5 — Tìm bug “không CME”

Chạy `FailFastNotGuaranteedDemo`. Giải thích vì sao `hasNext()` có thể trả false sau khi xóa phần tử đầu tiên và vì sao đây là bằng chứng **không được phụ thuộc vào fail-fast để validate cấu trúc**.

### Bài 6 — Snapshot iterator

Chạy `CopyOnWriteSnapshotDemo`. Thêm D sau khi tạo iterator và trước khi duyệt. Snapshot có thấy D không? Còn list hiện tại có không? Thử `snapshot.remove()` và dự đoán loại exception.

### Bài 7 — Weak consistency

Dùng `ConcurrentHashMap`, tạo iterator rồi thêm key. Viết test chỉ kiểm tra các bảo đảm API thật sự có, **không assert số key mới phải được quan sát** trong traversal diễn ra cùng lúc cập nhật.

### Bài 8 — Lock đủ phạm vi

Cho một `Collections.synchronizedList`, hai thread: một duyệt, một sửa. Viết ví dụ mà cả hai tuân `synchronized(syncList)`. Vì sao khóa từng lệnh `next()` tách biệt không tương đương khóa toàn bộ traversal?

### Bài 9 — `HashMap` value vs structural

Tạo `HashMap` với hai key, tạo iterator, sau đó `put` lại key có sẵn với value mới và quan sát. So sánh với việc thêm key mới. Giải thích dựa trên structural modification của Map, không dựa vào “put luôn nguy hiểm”.

### Bài 10 — Stream và non-interference

Viết một pipeline `source.stream().filter(...).toList()` để tạo danh sách chỉ có số lẻ. So sánh với callback `forEach` cố sửa chính `source` trong lúc duyệt. Tại sao chỉ một trong hai là lựa chọn phù hợp khi muốn giữ source không thay đổi?

### Bài 11 — Concurrency và consistency

Một dịch vụ có danh sách listener cập nhật rất hiếm nhưng được duyệt hàng nghìn lần mỗi giây. So sánh `CopyOnWriteArrayList` với synchronized list: chi phí mỗi update, consistency của iterator và cách quản lý lock.

### Bài 12 — Bug từ view

Tạo `subList` từ một `ArrayList`, sau đó thay đổi backing list trực tiếp và thử đọc view. Giải thích vì sao code không được xem hành vi hoặc CME cụ thể là hợp đồng portable; đề xuất snapshot khi cần view độc lập.

## 27. Checklist trước khi kết luận một đoạn code an toàn

- [ ] Tôi đã phân biệt CME là unchecked runtime exception, không bắt buộc `throws` chưa?
- [ ] Tôi biết một thread cũng có thể phát CME khi sửa collection ngoài iterator chưa?
- [ ] Tôi hiểu fail-fast dùng phát hiện bug **best effort**, không phải bảo đảm 100% chưa?
- [ ] Tôi có biết iterator/collection hiện tại thuộc loại fail-fast, snapshot hay weakly consistent không?
- [ ] Tôi đã xác định đúng structural modification cho implementation đó chưa?
- [ ] Tôi không gọi `list.add/remove` trực tiếp trong enhanced-for của cùng list chứ?
- [ ] Nếu phải xóa khi duyệt, tôi dùng `Iterator.remove` khi được hỗ trợ và đúng trạng thái chưa?
- [ ] Nếu chèn/sửa trong khi duyệt list, tôi cân nhắc `ListIterator` phù hợp chưa?
- [ ] Nếu mục tiêu là xóa theo điều kiện, `removeIf` có hợp đồng phù hợp với mutable list không?
- [ ] Tôi không dùng CME để điều khiển retry vô hạn, đo data race hay chứng minh thread safety chứ?
- [ ] Tôi nhớ `modCount/expectedModCount` là chi tiết triển khai, không phải public contract chung chưa?
- [ ] Tôi đã xét đường `hasNext()` kết thúc trước kiểm tra thay đổi tiếp theo chưa?
- [ ] Nếu dùng `CopyOnWriteArrayList`, tôi chấp nhận snapshot cũ và chi phí copy khi sửa chứ?
- [ ] Nếu dùng `ConcurrentHashMap`, tôi không giả iterator luôn thấy mọi update xen giữa chưa?
- [ ] Nếu dùng `Collections.synchronizedList`, tôi giữ khóa xuyên suốt quãng duyệt và mọi writer cùng tuân khóa chưa?
- [ ] Nếu chụp snapshot, tôi bảo đảm tạo snapshot từ nguồn có trạng thái nhất quán chưa?
- [ ] Tôi không sửa backing list ngoài hợp đồng khi còn dùng `subList` chưa?
- [ ] Với Stream, callback của tôi không can thiệp sửa cấu trúc của source trong lúc pipeline xử lý chứ?
- [ ] Khi CME phát sinh, tôi kiểm tra side effect và không giả dữ liệu tự rollback chứ?
- [ ] Tôi đã test contract/hành vi cần thiết thay vì chỉ test “không ném CME” chưa?

**Kết luận:** `ConcurrentModificationException` là **một tín hiệu phát hiện bất nhất trong một số cơ chế duyệt**, không phải bằng chứng có hai thread và cũng không phải hệ thống đảm bảo phát hiện mọi sửa đổi. Fail-fast hoạt động theo *best effort*. Hãy chọn đúng công cụ: `Iterator.remove()`, `ListIterator` hoặc `removeIf` để chỉnh sửa hợp lệ; `CopyOnWriteArrayList` cho snapshot khi nhiều đọc ít ghi; `ConcurrentHashMap` cho traversal weakly consistent; đồng bộ toàn bộ iteration khi dùng `Collections.synchronizedList`. Quan trọng nhất là thiết kế **ownership, tính nhất quán và hợp đồng concurrency**, không bao giờ dùng việc có hoặc không có CME làm thước đo duy nhất cho correctness.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-runtime-exceptions-012">Objects.requireNonNull và kiểm tra điều kiện trước khi xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# `Objects.requireNonNull` và kiểm tra điều kiện trước khi xử lý trong Java

Trong Java, **kiểm tra tiền điều kiện** (*precondition validation*) nghĩa là xác minh các yêu cầu bắt buộc của một lời gọi **trước khi sử dụng dữ liệu hoặc thực hiện side effect**. Ví dụ một phương thức tạo đơn hàng có thể yêu cầu mã đơn hàng khác `null`, không rỗng, số lượng dương và trạng thái hệ thống cho phép ghi. Kiểm tra sớm giúp lỗi xuất hiện tại **ranh giới API**, thay vì đợi một câu lệnh bên trong ném ngoại lệ khó hiểu sau khi dữ liệu đã bị thay đổi một phần.

Trong module Java Core – Exception, nhóm **Ngoại lệ runtime**, `java.util.Objects.requireNonNull` là API nền tảng cho việc yêu cầu một **tham chiếu không được `null`**. Nó có thể trả về chính tham chiếu đầu vào, hoặc ném `NullPointerException` ngay tại điểm kiểm tra. Tuy vậy, **`requireNonNull` chỉ kiểm tra null**, không thay thế việc kiểm tra chuỗi rỗng, giá trị âm, trạng thái không hợp lệ, giới hạn kích thước, tính toàn vẹn dữ liệu hay quyền truy cập.

~~~java
static String normalizeName(String name) {
    Objects.requireNonNull(name, "name must not be null");
    if (name.isBlank()) {
        throw new IllegalArgumentException("name must not be blank");
    }
    return name.trim();
}
~~~

Đây là mảnh phương thức Java hợp lệ khi có `import java.util.Objects;`. Nó cố ý phân biệt **null** và **blank**: cả hai đều không hợp lệ nhưng phản ánh hai điều kiện khác nhau. Phần còn lại giải thích API, lựa chọn loại ngoại lệ, thời điểm kiểm tra, khác biệt giữa fallback eager/lazy, giới hạn của kiểm tra null và một chương trình Java 17+ chạy độc lập.

## 1. Vì sao kiểm tra trước khi xử lý lại quan trọng?

Giả sử một dịch vụ nhận yêu cầu:

~~~java
void sendReceipt(String email, int quantity) {
    inventory.decrease(quantity);
    emailClient.send(email, "Receipt");
}
~~~

Đây là **mã minh họa chưa độc lập**, giả định `inventory` và `emailClient` tồn tại. Nếu `email == null` hoặc `quantity <= 0`, ứng dụng có thể đã giảm tồn kho trước khi phát hiện yêu cầu không hợp lệ. Lúc đó việc bắt một `NullPointerException` đơn thuần **không hoàn tác** thay đổi tồn kho.

Một cấu trúc tốt hơn:

~~~java
void sendReceipt(String email, int quantity) {
    Objects.requireNonNull(email, "email");
    if (email.isBlank()) {
        throw new IllegalArgumentException("email is blank");
    }
    if (quantity <= 0) {
        throw new IllegalArgumentException("quantity must be positive");
    }

    inventory.decrease(quantity);
    emailClient.send(email, "Receipt");
}
~~~

Các kiểm tra rẻ, thuần túy được đặt trước side effect. Điều này giúp phát hiện dữ liệu sai trước khi gây thay đổi trạng thái.

**Nhưng validation không thay thế transaction.** Ngay cả khi input hợp lệ, cập nhật tồn kho có thể thành công rồi thao tác gửi email thất bại. Khi cần tính nhất quán, ứng dụng còn phải thiết kế transaction, outbox, idempotency hoặc chính sách xử lý lỗi phù hợp. “Kiểm tra trước” chỉ giải quyết **tiền điều kiện có thể biết trước**, không bảo đảm mọi thao tác tiếp theo thành công.

## 2. `Objects.requireNonNull` hoạt động như thế nào?

`Objects` là lớp tiện ích trong package `java.util`. Ba overload thường dùng:

~~~java
public static <T> T requireNonNull(T obj)

public static <T> T requireNonNull(T obj, String message)

public static <T> T requireNonNull(
        T obj, java.util.function.Supplier<String> messageSupplier)
~~~

Đây là **chữ ký API minh họa**, không phải một class tự định nghĩa. Cả ba overload đều có điểm chung:

1. Nếu `obj != null`, trả lại **chính tham chiếu `obj`**.
2. Nếu `obj == null`, ném `NullPointerException`.
3. Dạng message bổ sung thông tin để nhận diện tham số nào sai.
4. Dạng `Supplier<String>` cho phép tạo message **lười** khi phát hiện null.

Ví dụ đơn giản:

~~~java
String value = "Java";
String checked = Objects.requireNonNull(value);

System.out.println(checked == value);
// true
~~~

`requireNonNull` không sao chép đối tượng, không làm biến thành bất biến, không tự cắt khoảng trắng, không thay đổi kiểu dữ liệu. Giá trị trả về thường được dùng để gán trực tiếp cho trường hoặc chuyển tới API tiếp theo.

Nếu truyền null:

~~~java
Object missing = null;
Objects.requireNonNull(missing);
// Ném NullPointerException.
~~~

Đây là mảnh cố ý ném ngoại lệ khi chạy. Không dựa vào message của dạng không tham số trong kiểm thử; giá trị thông điệp của một `NullPointerException` mới có thể là `null`.

### Tại sao trả về giá trị đầu vào?

Dạng trả về giúp code gọn trong constructor:

~~~java
final class ConnectionConfig {
    private final String host;

    ConnectionConfig(String host) {
        this.host = Objects.requireNonNull(host, "host");
    }
}
~~~

Đây là một class Java hợp lệ khi có import `Objects`. Nếu caller truyền null, constructor thất bại trước khi hoàn tất, thay vì tạo một đối tượng có trường `host` không hợp lệ.

Lưu ý rằng `host` khác null **không chứng minh** chuỗi host đúng định dạng, tồn tại trong DNS hoặc kết nối được. Những điều kiện khác cần validation riêng.

## 3. Chọn `NullPointerException`, `IllegalArgumentException` hay `IllegalStateException`?

Ba lớp đều là unchecked exceptions, nhưng truyền đạt ý nghĩa khác nhau.

| Điều kiện | Loại thường phù hợp | Ví dụ |
| --- | --- | --- |
| Một tham chiếu bắt buộc của API bằng `null` | `NullPointerException` | `Objects.requireNonNull(orderId, "orderId")` |
| Giá trị đối số khác null nhưng ngoài miền hợp lệ | `IllegalArgumentException` | `quantity <= 0` |
| Đối tượng/hệ thống ở trạng thái không cho phép thao tác | `IllegalStateException` | Chưa kết nối nhưng gọi `send()` |
| Chỉ số nằm ngoài miền của tập hợp | `IndexOutOfBoundsException` | `Objects.checkIndex(index, size)` |
| Giá trị không được tìm thấy nhưng là kết quả dự kiến | Có thể dùng `Optional`/result | Tra cứu ID không tồn tại |

Đây là **quy ước thiết kế thường dùng**, không phải một công thức tuyệt đối cho mọi public API. Một số thư viện coi `null` là đối số không hợp lệ và chủ động ném `IllegalArgumentException`. Điều quan trọng là **hợp đồng nhất quán, rõ nghĩa**.

### Điều kiện null

~~~java
Objects.requireNonNull(customerId, "customerId");
~~~

Dạng này thể hiện rõ “tham chiếu bắt buộc khác null”. Nếu thất bại, `getMessage()` của exception tạo từ overload nhận chuỗi là `"customerId"`.

### Điều kiện giá trị

~~~java
if (quantity <= 0) {
    throw new IllegalArgumentException(
            "quantity must be greater than zero");
}
~~~

`quantity` là kiểu nguyên thủy `int`, không thể là null; `requireNonNull` không có vai trò trong kiểm tra này.

### Điều kiện trạng thái

~~~java
if (!connected) {
    throw new IllegalStateException("connection is not ready");
}
~~~

Ở đây yêu cầu gửi có thể hoàn toàn hợp lệ, nhưng **trạng thái receiver** không cho phép tiếp tục.

### Không bắt `NullPointerException` để đoán tham số nào null

Không nên thay kiểm tra chủ động bằng:

~~~java
try {
    return user.getProfile().getName();
} catch (NullPointerException e) {
    return "unknown";
}
~~~

Đây là mảnh phản ví dụ. `NullPointerException` có thể do `user`, `getProfile()` hoặc logic bên trong một phương thức nào đó; bắt rộng rồi dùng fallback có thể che một lỗi lập trình quan trọng. Nếu một chuỗi tham chiếu nullable là trạng thái hợp lệ, hãy xử lý **từng ranh giới nullable theo hợp đồng**, không nuốt mọi NPE.

## 4. Hai cách viết message cho `requireNonNull`

**Message cố định:**

~~~java
Objects.requireNonNull(request, "request must not be null");
~~~

Thông điệp dễ đọc và không có chi phí tạo chuỗi phức tạp. Đối với phần lớn tham số, đây là lựa chọn mặc định tốt.

**Message động:**

~~~java
Objects.requireNonNull(request,
        () -> "request is required in phase " + phase);
~~~

Ở đây `request` và `phase` là biến có sẵn trong phạm vi. Lambda là `Supplier<String>`; nó chỉ tạo chuỗi khi `request == null`.

### Supplier được gọi lúc nào?

~~~java
String value = "present";
int[] calls = {0};

Objects.requireNonNull(value, () -> {
    calls[0]++;
    return "computed diagnostic";
});

System.out.println(calls[0]); // 0
~~~

Nếu object không null, supplier **không cần được gọi**. Với null:

~~~java
String value = null;
int[] calls = {0};

try {
    Objects.requireNonNull(value, () -> {
        calls[0]++;
        return "computed diagnostic";
    });
} catch (NullPointerException e) {
    System.out.println(e.getMessage()); // computed diagnostic
}
System.out.println(calls[0]); // 1
~~~

Đây là hai mảnh minh họa độc lập; chương trình chính cuối bài sẽ kiểm chứng cùng nguyên tắc.

### Supplier lười không có nghĩa an toàn để tạo side effect

Message supplier chỉ nên tính **thông tin chẩn đoán**. Tránh đặt trong đó cập nhật dữ liệu, gọi mạng hoặc ghi log có side effect:

~~~java
Objects.requireNonNull(value, () -> {
    chargeCreditCard(); // Không nên: side effect trong message.
    return "value is missing";
});
~~~

Đây là phản ví dụ có hàm giả định `chargeCreditCard()`. Khi null, Java chạy supplier để lấy message, dẫn tới thao tác nghiệp vụ không mong muốn **trong lúc đang tạo ngoại lệ**.

Một message supplier quá phức tạp còn có thể ném exception khác, làm che mất NPE mà `requireNonNull` lẽ ra báo. Hãy giữ message ngắn, không ném lỗi và không chứa dữ liệu nhạy cảm.

### Chú ý overload khi truyền null làm message

Không nên viết mơ hồ:

~~~java
Objects.requireNonNull(value, null);
~~~

Dạng này **không biên dịch** do có hai overload thứ hai nhận `String` và `Supplier<String>`, còn literal `null` có thể khớp cả hai mà không có kiểu cụ thể hơn.

Nếu thực sự muốn chọn overload message String với giá trị null, có thể ép kiểu:

~~~java
Objects.requireNonNull(value, (String) null);
~~~

Còn code thông thường nên truyền một message hữu ích, không cần ép kiểu null.

## 5. `Objects.requireNonNullElse`: lựa chọn giá trị dự phòng

Từ Java 9, `Objects` có thêm:

~~~java
public static <T> T requireNonNullElse(
        T obj, T defaultObj)
~~~

Nếu `obj != null`, trả về `obj`; nếu `obj == null`, trả về `defaultObj`, **nhưng giá trị được chọn không được null**.

~~~java
String name = Objects.requireNonNullElse(
        null, "anonymous");

System.out.println(name); // anonymous
~~~

Nếu cả giá trị chính và fallback đều null:

~~~java
Objects.requireNonNullElse(null, null);
// NullPointerException.
~~~

Đây là mảnh cố ý gây lỗi runtime.

### Nó khác `requireNonNull` ở mục đích

| Phương thức | Khi đầu vào khác null | Khi đầu vào null |
| --- | --- | --- |
| `requireNonNull(obj)` | Trả lại `obj` | Ném `NullPointerException` |
| `requireNonNull(obj, message)` | Trả lại `obj` | Ném NPE với message |
| `requireNonNullElse(obj, fallback)` | Trả `obj` | Trả fallback không null, hoặc ném NPE |

`requireNonNullElse` chỉ phù hợp nếu **fallback là một giá trị hợp lệ theo hợp đồng**. Nếu thiếu input là lỗi, đừng dùng một chuỗi mặc định để che nó.

### Java đánh giá tham số trước khi gọi phương thức

Một điểm quan trọng:

~~~java
String result = Objects.requireNonNullElse(
        "primary", createExpensiveDefault());
~~~

Dù `"primary"` khác null, Java vẫn **đánh giá biểu thức tham số** `createExpensiveDefault()` trước khi gọi `requireNonNullElse`. Nó có thể tốn chi phí hoặc có side effect.

Đây là **mảnh code minh họa**, giả định `createExpensiveDefault()` trả một String. Nếu việc tạo fallback tốn tài nguyên, hãy cân nhắc `requireNonNullElseGet`.

## 6. `Objects.requireNonNullElseGet`: fallback tạo lười

Java 9+ cung cấp:

~~~java
public static <T> T requireNonNullElseGet(
        T obj,
        java.util.function.Supplier<? extends T> supplier)
~~~

Nếu `obj != null`, trả lại `obj` mà không gọi supplier tạo fallback. Nếu `obj == null`, Java lấy fallback từ supplier và yêu cầu **kết quả được chọn khác null**.

~~~java
String a = Objects.requireNonNullElseGet(
        "ready", () -> expensiveDefault());

String b = Objects.requireNonNullElseGet(
        null, () -> expensiveDefault());
~~~

Đây là mảnh minh họa có `expensiveDefault()` giả định đã được định nghĩa.

- Trường hợp `a`: supplier không được gọi.
- Trường hợp `b`: supplier được gọi để tạo giá trị dự phòng.

### Đầu vào hợp lệ nhưng supplier trả null?

~~~java
Objects.requireNonNullElseGet(
        null, () -> null);
// NullPointerException.
~~~

Dạng này không cho phép fallback bằng null. Nếu API thật sự cho phép trả null, không nên dùng `requireNonNullElseGet` như một cách âm thầm hợp thức hóa điều đó.

### Khi nào nên dùng `Else` và `ElseGet`?

Dùng `requireNonNullElse` nếu fallback đã có sẵn hoặc cực rẻ:

~~~java
String label = Objects.requireNonNullElse(rawLabel, "(none)");
~~~

Dùng `requireNonNullElseGet` nếu fallback cần dựng object, đọc cấu hình hoặc tính toán đáng kể:

~~~java
Settings settings = Objects.requireNonNullElseGet(
        suppliedSettings,
        () -> Settings.defaults());
~~~

Đây là mảnh minh họa với lớp `Settings` giả định. Tạo dữ liệu dự phòng nên là thao tác an toàn và hợp đồng cần nói rõ khi nào nó được tính.

**Không nhầm với `Optional.orElse`/`orElseGet`:** hai cặp đều thể hiện ý tưởng eager/lazy fallback, nhưng `Objects.requireNonNullElse(Get)` thao tác trên **tham chiếu có thể null**, còn `Optional` biểu diễn việc có/không có giá trị bằng một đối tượng API khác.

## 7. Chọn overload sao cho thông điệp hữu ích

Một message tốt nên mô tả **tên tham số và điều kiện**:

~~~java
Objects.requireNonNull(orderId, "orderId must not be null");
~~~

Kém hữu ích hơn:

~~~java
Objects.requireNonNull(orderId, "Error");
~~~

Trong hệ thống lớn, có thể thêm ngữ cảnh không nhạy cảm:

~~~java
Objects.requireNonNull(payload,
        () -> "payload is required for operation " + operation);
~~~

Nhưng cần tránh:

- Mật khẩu, token, secret, raw cookie, thông tin thẻ thanh toán.
- Toàn bộ request/body chứa dữ liệu nhạy cảm.
- Dùng message làm **mã lỗi máy đọc ổn định**.
- Dựa vào tên file/dòng code trong `printStackTrace()` để xác định điều kiện nghiệp vụ.

Nếu cần nhánh xử lý tự động, nên dùng một mã lỗi cấu trúc hoặc loại exception phù hợp. Message dành cho người đọc, có thể được sửa theo thời gian.

## 8. Tổ chức validation theo thứ tự ưu tiên

Với một yêu cầu nhiều trường, nên quyết định **thứ tự kiểm tra có ý nghĩa**.

Ví dụ:

~~~java
static void validate(
        String orderId,
        String email,
        int quantity,
        boolean systemReady) {

    Objects.requireNonNull(orderId, "orderId");
    Objects.requireNonNull(email, "email");

    if (orderId.isBlank()) {
        throw new IllegalArgumentException("orderId is blank");
    }
    if (email.isBlank()) {
        throw new IllegalArgumentException("email is blank");
    }
    if (quantity <= 0) {
        throw new IllegalArgumentException(
                "quantity must be positive");
    }
    if (!systemReady) {
        throw new IllegalStateException("system is not ready");
    }
}
~~~

Phương thức này có thể đặt trong một lớp Java có import `Objects`.

- Các tham chiếu cần thiết được kiểm tra null trước khi gọi `isBlank()`.
- Các miền giá trị được xác minh tiếp.
- Điều kiện trạng thái được kiểm tra trước khi gây side effect.
- Phương thức chỉ hoàn tất bình thường khi các tiền điều kiện đã thỏa.

Nếu có nhiều lỗi đồng thời, việc **fail fast** nghĩa là method ném lỗi tại **điều kiện đầu tiên không hợp lệ** theo thứ tự code. Nó không tạo danh sách mọi lỗi. Với form nhập liệu, bạn có thể muốn một bộ kiểm tra thu thập toàn bộ lỗi và trả kết quả cấu trúc, thay vì ném exception ở lỗi đầu tiên.

### Không che lỗi thứ nhất bằng lỗi thứ hai

Code kém:

~~~java
static void process(String code) {
    if (code.isBlank()) { // NPE khi code null.
        throw new IllegalArgumentException("blank");
    }
}
~~~

Cách tốt hơn:

~~~java
static void process(String code) {
    Objects.requireNonNull(code, "code");
    if (code.isBlank()) {
        throw new IllegalArgumentException("code is blank");
    }
}
~~~

Trình tự này tạo một contract rõ: null là NPE chủ động, blank là IAE.

## 9. Validation đầu vào không đồng nghĩa object bất biến

Sau khi `requireNonNull(value)` thành công, bạn biết **tham chiếu tại thời điểm kiểm tra** không null. Điều đó không chứng minh:

- Object không bị thay đổi bởi luồng khác.
- Nội dung của một list không chứa null.
- Các field của object đều hợp lệ.
- Dữ liệu có quyền truy cập hoặc bảo mật phù hợp.
- Object luôn ở trạng thái có thể sử dụng.
- Các thao tác I/O về sau không thất bại.

Ví dụ:

~~~java
List<String> names = Objects.requireNonNull(
        suppliedNames, "suppliedNames");
~~~

Đây là mảnh minh họa với `List` và biến `suppliedNames`. Nó chỉ xác minh **list reference không null**. Bên trong vẫn có thể có phần tử null:

~~~java
List<String> names =
        new java.util.ArrayList<>();
names.add(null);

Objects.requireNonNull(names);     // Thành công.
Objects.requireNonNull(names.get(0)); // Ném NPE.
~~~

Nếu hợp đồng yêu cầu mọi phần tử khác null, cần kiểm tra phần tử riêng hoặc dùng một API immutable/copy có các quy tắc rõ.

### Kiểm tra null trong constructor và copy phòng thủ

Với mutable collection:

~~~java
final class NameGroup {
    private final java.util.List<String> names;

    NameGroup(java.util.List<String> names) {
        this.names = java.util.List.copyOf(
                Objects.requireNonNull(names, "names"));
    }
}
~~~

`List.copyOf` tạo một danh sách unmodifiable và **từ chối phần tử null** theo hợp đồng của API. Nó giúp tránh giữ tham chiếu đến một list mutable do caller tiếp tục thay đổi, nhưng vẫn không kiểm tra quy tắc như chuỗi rỗng hay tên trùng nhau.

Đây là class hợp lệ nếu import `Objects`. Đừng suy ra rằng mọi phép copy hoặc mọi collection API đều có cùng chính sách null; đọc hợp đồng cụ thể.

## 10. `requireNonNull` trong constructor, record và setter

### Constructor

~~~java
final class Customer {
    private final String id;

    Customer(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }
}
~~~

Nếu `id == null`, không có đối tượng `Customer` được tạo thành công để bàn giao cho caller.

### Record compact constructor trong Java 17

~~~java
record Product(String sku, int count) {
    Product {
        Objects.requireNonNull(sku, "sku");
        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku is blank");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count is negative");
        }
    }
}
~~~

Đây là khai báo record hợp lệ trong một lớp/tệp Java 17 phù hợp. Compact constructor có thể validation các component trước khi quá trình tạo record hoàn tất.

Lợi thế: bất kỳ lệnh `new Product(...)` dùng constructor này đều được kiểm tra tại ranh giới. Nhưng record không tự xác minh mọi quy tắc nghiệp vụ chỉ vì nó tạo accessor và là shallowly immutable.

### Setter

~~~java
void setDisplayName(String value) {
    this.displayName = Objects.requireNonNull(
            value, "displayName");
}
~~~

Đây là mảnh minh họa bên trong một class mutable. Kiểm tra trước khi gán giúp tránh đưa trạng thái null không hợp lệ vào object.

## 11. Tại sao không nên cố “gọi `requireNonNull` cho mọi thứ”?

Không phải mọi tham chiếu null đều là lỗi:

- Một trường `middleName` có thể tùy chọn.
- Một hàm tra cứu có thể trả `null` theo hợp đồng cũ.
- Một dependency có thể được tạo từ default khi không cung cấp.
- Một thuộc tính input có thể biểu diễn “không có giá trị”.

Nếu `null` là hợp lệ, hãy cho nó một ý nghĩa rõ ràng hoặc dùng API phù hợp như `Optional` cho kiểu trả về có/không. **Không ném NPE chỉ để tỏ ra defensive**.

Ví dụ một thuộc tính tùy chọn:

~~~java
static String visibleLabel(String label) {
    return Objects.requireNonNullElse(label, "(unnamed)");
}
~~~

Khác với mã bắt buộc:

~~~java
static String requiredLabel(String label) {
    return Objects.requireNonNull(label, "label");
}
~~~

Cả hai đều hợp lệ; chọn theo contract của phương thức.

Không nên đưa `Optional` vào mọi field, mọi tham số hoặc mọi collection element theo một khuôn cố định. `Optional` hữu ích đặc biệt ở những API trả về có khả năng không có giá trị, nhưng thiết kế nullability cần nhất quán theo ngữ cảnh.

## 12. `Objects.checkIndex` và những kiểm tra tiền điều kiện liên quan

Ngoài điều kiện tham chiếu khác null, phương thức có thể yêu cầu một **chỉ số hoặc dải chỉ số hợp lệ**. Java 17 cung cấp các helper:

~~~java
Objects.checkIndex(index, length);                 // 0 <= index < length
Objects.checkFromToIndex(from, to, length);        // 0 <= from <= to <= length
Objects.checkFromIndexSize(from, size, length);    // Vùng [from, from + size) nằm trong length
~~~

Đây là mã minh họa với các biến và `import java.util.Objects`. Các helper trả index/from khi hợp lệ, và ném `IndexOutOfBoundsException` nếu không. Helper cho range được thiết kế để kiểm tra an toàn cả điều kiện tràn số, thay vì cộng `int` một cách tùy tiện.

Ví dụ hai tiền điều kiện khác nhau trong cùng API:

~~~java
Objects.requireNonNull(array, "array");
Objects.checkIndex(index, array.length);
int value = array[index];
~~~

Tham chiếu null gây `NullPointerException`; index sai gây lỗi bounds. Không nên nuốt cả hai rồi trả `0` như thể đọc thành công. Ngược lại, truy cập `array[index]` vốn đã kiểm tra bounds; không bắt buộc gọi `checkIndex` trước **mọi** lần đọc. Bài về `IndexOutOfBoundsException` phân tích sâu phạm vi `[from, to)`, off-by-one và lựa chọn helper. Ở đây trọng tâm là **xác nhận tiền điều kiện trước side effect**.

## 13. `Objects.requireNonNull` và helpful NullPointerException

Trên JVM hỗ trợ helpful NPE messages, lỗi dereference null có thể đi kèm thông điệp mô tả biểu thức thất bại. Đây là **hỗ trợ debug**, không phải một hợp đồng nghiệp vụ ổn định để phân tích chuỗi `getMessage()`.

~~~java
String name = null;
Objects.requireNonNull(name, "name must not be null");
~~~

Đoạn minh họa yêu cầu import `java.util.Objects`. `requireNonNull` chủ động báo ràng buộc ngay tại ranh giới đầu vào, với message ứng dụng quyết định; còn helpful NPE tự động xuất hiện từ một số thao tác runtime. Cả hai có thể là `NullPointerException`, nhưng chúng trả lời hai câu hỏi khác nhau: **tiền điều kiện API bị vi phạm** hay **mã đã dereference null ở đâu**.

## 14. Tránh side effect trước validation và thứ tự đánh giá Java

Một chi tiết thực thi quan trọng: **đối số của một lời gọi method được đánh giá trước khi method bắt đầu chạy**.

Xem:

~~~java
save(
        Objects.requireNonNull(orderId, "orderId"),
        calculateAndChargeFee());
~~~

Dù tham số đầu tiên được `requireNonNull` kiểm tra trước khi đánh giá tham số thứ hai theo quy tắc **đánh giá đối số từ trái sang phải**, vẫn nên tránh tổ chức lời gọi phức tạp kiểu này khi side effect lớn hoặc cần kiểm tra nhiều dữ liệu. Đặt validation vào câu lệnh riêng giúp dễ đọc và dễ kiểm soát hơn.

~~~java
Objects.requireNonNull(orderId, "orderId");
validateFeePolicy();

int fee = calculateFee();
save(orderId, fee);
~~~

Đây là mảnh minh họa với các hàm giả định. Nếu `calculateFee()` có side effect, chỉ chạy nó sau các bước kiểm tra cần thiết.

Một phản ví dụ rõ hơn:

~~~java
save(
        calculateAndChargeFee(),
        Objects.requireNonNull(orderId, "orderId"));
~~~

Java đánh giá đối số **từ trái sang phải**: `calculateAndChargeFee()` có thể chạy trước khi `requireNonNull(orderId)` phát hiện null. Đừng để validation đứng sau các thao tác không thể hoàn tác.

**Đừng hiểu “fail fast” là “mọi validation tự xảy ra trước mọi biểu thức”.** Vị trí câu lệnh và thứ tự đánh giá thực tế vẫn quyết định khi nào lỗi được phát hiện.

## 15. Kiểm tra dữ liệu tại ranh giới, không kiểm tra đi kiểm tra lại vô hạn

Ở một API công khai, kiểm tra các tham số không-null giúp hợp đồng rõ ràng. Nhưng mỗi tầng nội bộ không nhất thiết phải lặp lại cùng một kiểm tra nếu invariants đã được thiết lập và bảo toàn.

Ví dụ một object immutable đã được constructor xác minh `id` không-null, các method nội bộ đọc `id` không cần gọi `Objects.requireNonNull(id)` mỗi lần chỉ để che việc invariants không được giữ.

Ngược lại, tại ranh giới giữa hệ thống với dữ liệu bên ngoài — HTTP, JSON, file, database, user input hoặc plugin — nên xác minh lại vì nguồn dữ liệu không thuộc quyền kiểm soát của module.

Một chiến lược thực tế:

1. Kiểm tra **kiểu/khả năng parse** của dữ liệu ở ranh giới nhập.
2. Kiểm tra **null và miền giá trị** ngay trước khi xây domain object.
3. Tạo object đảm bảo invariant nhờ constructor hoặc factory.
4. Truyền object hợp lệ qua các tầng thay vì một bộ tham số rời rạc dễ nhầm.
5. Vẫn xử lý các ngoại lệ I/O, cạnh tranh luồng và thay đổi trạng thái có thể xảy ra về sau.

Kiểm tra trước khi xử lý giúp **giảm lỗi do input**, không thay thế kiểm tra trạng thái ở thời điểm thực hiện thao tác thật.

## 16. Chương trình Java 17+ độc lập để kiểm chứng

Tạo tệp **`RequireNonNullDemo.java`**. Chương trình có đầu ra ổn định, minh họa `requireNonNull`, lazy message, fallback, tiền điều kiện, kiểu exception và kiểm tra chỉ số.

~~~java
import java.util.Objects;

public class RequireNonNullDemo {

    record Order(String id, int quantity) {}

    public static void main(String[] args) {
        System.out.println("== basic non-null ==");
        String original = "java";
        String checked = Objects.requireNonNull(original);
        System.out.println("sameReference=" + (original == checked));

        attempt("missing", () ->
                Objects.requireNonNull(null, "id is required"));

        System.out.println("== lazy message ==");
        int[] messageCalls = {0};
        Objects.requireNonNull("present", () -> {
            messageCalls[0]++;
            return "not present";
        });
        System.out.println("callsAfterPresent=" + messageCalls[0]);

        attempt("lazyMissing", () ->
                Objects.requireNonNull(null, () -> {
                    messageCalls[0]++;
                    return "computed message";
                }));
        System.out.println("callsAfterMissing=" + messageCalls[0]);

        System.out.println("== non-null fallback ==");
        System.out.println("elseValue="
                + Objects.requireNonNullElse(null, "guest"));
        System.out.println("elsePrimary="
                + Objects.requireNonNullElse("owner", "guest"));
        attempt("elseBothNull", () ->
                Objects.requireNonNullElse(null, null));

        System.out.println("== eager versus lazy ==");
        System.out.println("eagerResult="
                + Objects.requireNonNullElse(
                        "primary", makeFallback()));
        int[] fallbackCalls = {0};
        String lazyPrimary = Objects.requireNonNullElseGet(
                "primary", () -> {
                    fallbackCalls[0]++;
                    return "created";
                });
        System.out.println("lazyPrimary=" + lazyPrimary);
        System.out.println("fallbackCalls=" + fallbackCalls[0]);

        String lazyMissing = Objects.requireNonNullElseGet(
                null, () -> {
                    fallbackCalls[0]++;
                    return "created";
                });
        System.out.println("lazyMissing=" + lazyMissing);
        System.out.println("fallbackCalls=" + fallbackCalls[0]);
        attempt("lazyNullResult", () ->
                Objects.requireNonNullElseGet(null, () -> null));

        System.out.println("== domain validation ==");
        Order order = createOrder("O-1", 2, true);
        System.out.println("created=" + order.id()
                + ",quantity=" + order.quantity());

        attempt("nullId", () ->
                createOrder(null, 2, true));
        attempt("blankId", () ->
                createOrder("  ", 2, true));
        attempt("invalidQuantity", () ->
                createOrder("O-2", 0, true));
        attempt("systemNotReady", () ->
                createOrder("O-3", 2, false));

        System.out.println("== index validation ==");
        System.out.println("index=" + Objects.checkIndex(2, 3));
        System.out.println("fromTo="
                + Objects.checkFromToIndex(1, 3, 3));
        System.out.println("fromSize="
                + Objects.checkFromIndexSize(1, 2, 3));
        attempt("invalidIndex", () -> Objects.checkIndex(3, 3));
    }

    static String makeFallback() {
        System.out.println("fallbackFactoryCalled");
        return "backup";
    }

    static Order createOrder(
            String id, int quantity, boolean systemReady) {
        Objects.requireNonNull(id, "id");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id is blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive");
        }
        if (!systemReady) {
            throw new IllegalStateException(
                    "system is not ready");
        }
        return new Order(id, quantity);
    }

    static void attempt(String label, Runnable operation) {
        try {
            operation.run();
            System.out.println(label + "=OK");
        } catch (RuntimeException e) {
            System.out.println(label + "="
                    + e.getClass().getSimpleName()
                    + ":" + e.getMessage());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac RequireNonNullDemo.java
java RequireNonNullDemo
~~~

**Stdout chính xác (Java 17+):**

~~~text
== basic non-null ==
sameReference=true
missing=NullPointerException:id is required
== lazy message ==
callsAfterPresent=0
lazyMissing=NullPointerException:computed message
callsAfterMissing=1
== non-null fallback ==
elseValue=guest
elsePrimary=owner
elseBothNull=NullPointerException:defaultObj
== eager versus lazy ==
fallbackFactoryCalled
eagerResult=primary
lazyPrimary=primary
fallbackCalls=0
lazyMissing=created
fallbackCalls=1
lazyNullResult=NullPointerException:supplier.get()
== domain validation ==
created=O-1,quantity=2
nullId=NullPointerException:id
blankId=IllegalArgumentException:id is blank
invalidQuantity=IllegalArgumentException:quantity must be positive
systemNotReady=IllegalStateException:system is not ready
== index validation ==
index=2
fromTo=1
fromSize=1
invalidIndex=IndexOutOfBoundsException:Index 3 out of bounds for length 3
~~~

### Giải thích chương trình

**`basic non-null`:** `requireNonNull(original)` trả đúng tham chiếu, nên `sameReference=true`. Khi `obj == null`, overload nhận message tạo `NullPointerException` có thông điệp được truyền.

**`lazy message`:** lần đầu giá trị `"present"` khác null, supplier không chạy và biến đếm bằng `0`. Lần sau null khiến supplier chạy một lần để tạo message; `attempt` bắt NPE và in nội dung.

**`non-null fallback`:** khi obj null, `requireNonNullElse` trả `"guest"`. Khi obj khác null, trả `"owner"`. Với cả hai null, JDK 17 ném NPE có thông điệp `defaultObj` theo cách triển khai chuẩn. Không dựa vào chi tiết message này làm hợp đồng phân loại lỗi ứng dụng.

**`eager versus lazy`:** phương thức `makeFallback()` chạy **trước** `requireNonNullElse` dù primary khác null, nên in `fallbackFactoryCalled`. Ngược lại, supplier của `requireNonNullElseGet` không được gọi khi primary khác null; khi primary null, supplier được gọi một lần và trả `"created"`.

**`domain validation`:** `createOrder` phân biệt null id (NPE), blank id và quantity không dương (IAE), hệ thống chưa sẵn sàng (ISE). Validation được thực hiện trước khi tạo record `Order`; caller nhận loại lỗi cụ thể.

**`index validation`:** `Objects.checkIndex` và hai hàm kiểm tra khoảng trả điểm bắt đầu/chỉ số hợp lệ. Khi index bằng length, phương thức ném `IndexOutOfBoundsException`.

### Độ ổn định của thông điệp JDK

Chương trình in message của một số exception được JDK 17 tạo trong các helper `Objects`. Những thông điệp như `defaultObj`, `supplier.get()` hoặc `Index 3 out of bounds for length 3` là **các thông điệp của JDK 17 tiêu chuẩn**, nhưng **không nên dùng chúng làm API contract** hoặc điều kiện logic trong hệ thống. Nếu cần test di động qua nhiều JDK, hãy kiểm tra type của exception và thông tin có cấu trúc do ứng dụng tự kiểm soát; đừng buộc test phụ thuộc nguyên văn message JDK.

## 17. Null trong generic API và suy luận kiểu

`Objects.requireNonNull` có dạng generic `<T> T` để giữ nguyên type tĩnh của đối tượng:

~~~java
String text = Objects.requireNonNull(
        suppliedText, "suppliedText");

java.util.List<String> names = Objects.requireNonNull(
        suppliedNames, "suppliedNames");
~~~

Đây là mảnh minh họa có biến đầu vào tương ứng. Generic ở đây **không kiểm tra sâu type argument** hoặc nội dung collection. Nó chỉ cho compiler biết kiểu trả về tương thích với type của `obj`.

Với method trả đối tượng generic:

~~~java
static <T> T required(T value) {
    return Objects.requireNonNull(value, "value");
}
~~~

Method giữ type `T` và báo NPE nếu object null. Nó không biến một giá trị nullable thành một bằng chứng compile-time chắc chắn không null theo toàn bộ hệ thống type Java (Java 17 không tích hợp null-safety ở cấp type như một số ngôn ngữ khác).

Các annotation như `@NonNull` hoặc `@Nullable` đến từ thư viện và công cụ phân tích tĩnh riêng; chúng có thể tăng chất lượng kiểm tra nhưng không thay thế hành vi runtime của `Objects.requireNonNull`.

## 18. Những hiểu lầm và lỗi thường gặp

### 18.1. “`requireNonNull` sửa giá trị null bằng một mặc định”

Sai. `requireNonNull` ném NPE khi null. Nếu hợp đồng có fallback hợp lệ, dùng `requireNonNullElse` hoặc `requireNonNullElseGet`.

### 18.2. “`requireNonNull` chứng minh String không rỗng”

Sai. `""` và `"   "` đều khác null. Kiểm tra `isEmpty()` hoặc `isBlank()` theo yêu cầu.

### 18.3. “Dùng `requireNonNull` cho số nguyên primitive”

Primitive `int`, `long`, `boolean` không thể null. Nếu một wrapper như `Integer` có thể null thì `requireNonNull` mới kiểm tra tham chiếu wrapper đó.

### 18.4. “`Objects.requireNonNullElse` không bao giờ tạo fallback nếu object đã có”

Sai nếu fallback được truyền bằng **biểu thức gọi hàm**. Java đánh giá tham số trước khi gọi method. Chỉ `requireNonNullElseGet` mới trì hoãn việc **thực thi supplier** theo điều kiện chọn fallback.

### 18.5. “`requireNonNullElseGet` cho phép fallback null”

Sai. Khi cần lấy fallback, giá trị do supplier trả phải khác null; nếu không phương thức ném NPE.

### 18.6. “Message supplier luôn được gọi”

Sai. Với `requireNonNull(obj, Supplier<String>)`, supplier chỉ được dùng khi obj null. Dùng message supplier thuần túy để tránh side effect bất ngờ.

### 18.7. “Đặt mọi thứ vào `catch (NullPointerException)` là defensive programming”

Sai. Nó có thể che lỗi lập trình bất ngờ bên trong nhiều lời gọi khác nhau. Chủ động kiểm tra **tham số có hợp đồng không-null**, không nuốt mọi NPE.

### 18.8. “Mọi dữ liệu khác null đều hợp lệ”

Sai. Số âm, email sai định dạng, độ dài ngoài miền, trạng thái không sẵn sàng và điều kiện bảo mật vẫn cần xác minh.

### 18.9. “`IllegalArgumentException` và `IllegalStateException` thay nhau được”

Không nên. IAE thường mô tả giá trị đối số không hợp lệ; ISE thường mô tả trạng thái receiver không cho phép thao tác.

### 18.10. “Kiểm tra trước là đủ chống mọi lỗi”

Sai. Sau validation vẫn có thể có lỗi I/O, cạnh tranh luồng, thay đổi dữ liệu, timeout, tài nguyên hết, hoặc lỗi side effect. Cần thiết kế giao dịch/lifecycle.

### 18.11. “`requireNonNull(list)` kiểm tra mọi phần tử”

Sai. Nó kiểm tra list reference. Nếu muốn toàn bộ phần tử khác null, phải kiểm tra phần tử hoặc dùng API có hợp đồng kiểm tra như `List.copyOf`.

### 18.12. “Có helpful NPE nên không cần validation”

Sai. Helpful NPE hỗ trợ chẩn đoán nhưng không thay thế một hợp đồng precondition rõ ràng. Message tự động cũng không nên được parse.

### 18.13. “`Objects.checkIndex` báo NPE nếu index vượt miền”

Sai. Với index nguyên, lỗi kiểm tra miền là `IndexOutOfBoundsException`, không phải vấn đề tham chiếu null.

### 18.14. “Bắt một NPE là có thể tiếp tục mọi trạng thái”

Sai. Một NPE bất ngờ có thể báo invariant bị phá vỡ. Nếu không có phương án phục hồi chính xác, nên để lỗi lan truyền tới handler phù hợp.

### 18.15. “Các tham số được đánh giá sau khi method bắt đầu”

Sai. Java đánh giá các biểu thức đối số từ trái sang phải **trước lời gọi method**. Side effect ở đối số trước vẫn có thể chạy trước một validation ở đối số sau.

### 18.16. “Validation nên báo lỗi chung chung để giấu tên tham số”

Không hẳn. Với log nội bộ và API dành cho lập trình viên, tên tham số thường hữu ích. Điều cần giấu là dữ liệu nhạy cảm, không phải mọi ngữ cảnh chẩn đoán.

### 18.17. “Một nullable field luôn là bug”

Không. `null` có thể là trạng thái tùy chọn theo hợp đồng. Lỗi là **không thống nhất** giữa khả năng null và cách sử dụng.

### 18.18. “Fail fast là cách duy nhất cho form validation”

Không. Với API nhập liệu tương tác, việc trả nhiều lỗi cùng lúc đôi khi tốt hơn ném exception ngay ở lỗi đầu tiên. Chọn theo cách caller cần trình bày và phục hồi.

## 19. Checklist trước khi xử lý một yêu cầu

1. **Phân biệt bắt buộc với tùy chọn:** Field nào không được null, field nào có thể thiếu?
2. **Kiểm tra null sớm:** Dùng `Objects.requireNonNull` nếu null là vi phạm hợp đồng.
3. **Kiểm tra miền giá trị:** Chuỗi rỗng/blank, số âm, độ dài, khoảng giá trị, định dạng.
4. **Kiểm tra trạng thái:** Receiver/hệ thống có đang ở trạng thái cho phép thao tác?
5. **Chọn loại lỗi rõ:** NPE cho null bắt buộc; IAE cho giá trị đối số không hợp lệ; ISE cho trạng thái sai; IOBE cho chỉ số.
6. **Đặt validation trước side effect:** Tránh ghi dữ liệu/gọi mạng rồi mới phát hiện input sai.
7. **Không lạm dụng fallback:** Giá trị dự phòng phải hợp lệ về nghiệp vụ; chỉ lazy nếu cần tính muộn.
8. **Không parse message lỗi:** Dùng type hoặc mã lỗi cấu trúc nếu cần logic máy.
9. **Giữ thông tin nhạy cảm khỏi message:** Đừng vô tình để lộ token hoặc toàn bộ request.
10. **Kiểm tra chiều sâu hợp đồng:** Non-null reference không chứng minh dữ liệu bên trong hợp lệ.
11. **Xử lý cạnh tranh và I/O riêng:** Validation ban đầu không loại bỏ race hoặc side-effect failure.
12. **Kiểm thử tại biên:** Null, blank, giá trị ngoài miền, trạng thái không hợp lệ và nhánh thành công.
13. **Duy trì invariants sau construction:** Constructor/factory nên tạo object hợp lệ để code nội bộ không lặp kiểm tra vô hạn.
14. **Định nghĩa public API rõ:** Caller biết khi nào ném ngoại lệ và khi nào nhận kết quả dự phòng.

## 20. Câu hỏi tự kiểm tra

1. `Objects.requireNonNull` trả gì khi object khác null?
2. Nó ném loại exception gì khi object null?
3. `requireNonNull("x")` có tạo một String mới không?
4. Hai overload tạo message khác nhau ở điểm nào?
5. Supplier message được gọi nếu object khác null không?
6. Vì sao nên tránh side effect trong message supplier?
7. `requireNonNull(null, "id")` có message là gì?
8. `requireNonNull("", "name")` có ném lỗi không? Vì sao?
9. `String.isBlank()` khác kiểm tra null như thế nào?
10. Khi nào nên dùng `IllegalArgumentException` sau `requireNonNull`?
11. Khi nào nên dùng `IllegalStateException`?
12. `requireNonNullElse(null, "guest")` trả gì?
13. `requireNonNullElse(null, null)` làm gì?
14. Dùng một lời gọi tạo fallback trong `requireNonNullElse` thì nó chạy lúc nào?
15. `requireNonNullElseGet("present", supplier)` có gọi supplier không?
16. `requireNonNullElseGet(null, () -> null)` có hợp lệ không?
17. Vì sao `requireNonNullElseGet` có thể có lợi khi fallback đắt?
18. Nếu một list reference không null, có chắc từng phần tử không null không?
19. Một record compact constructor có thể kiểm tra null và miền giá trị không?
20. `Objects.checkIndex(index, length)` trả gì nếu hợp lệ?
21. `checkIndex(length, length)` ném lỗi gì?
22. `Objects.checkFromToIndex(1, 3, 3)` trả gì?
23. Dùng `catch (NullPointerException)` bao toàn bộ xử lý có rủi ro gì?
24. Helpful NPE có thay thế hợp đồng validation chủ động không?
25. Nếu kiểm tra null đặt sau lời gọi cập nhật database, chuyện gì có thể xảy ra?
26. `int` nguyên thủy có thể bằng null không?
27. Nếu tham số nullable là hợp lệ, có nên luôn gọi `requireNonNull` không?
28. Vì sao cùng lúc cần validation và transaction?
29. Khi nào nên trả danh sách lỗi validation thay vì ném exception đầu tiên?
30. Nên kiểm thử kiểu ngoại lệ hay nội dung message JDK tự tạo?

### Đáp án gợi ý

1. Chính tham chiếu object đã truyền.
2. `NullPointerException`.
3. Không.
4. `String` có message sẵn; `Supplier<String>` tạo message lười khi null.
5. Không.
6. Nó có thể chạy trong lúc tạo lỗi và gây hành vi bất ngờ hoặc lỗi khác.
7. `"id"`.
8. Không; chuỗi rỗng vẫn là object khác null.
9. `isBlank` kiểm tra ký tự/trống; `requireNonNull` kiểm tra tham chiếu.
10. Khi object đã khác null nhưng giá trị đối số vi phạm tiền điều kiện.
11. Khi trạng thái hiện tại không cho phép thao tác.
12. `"guest"`.
13. Ném NPE.
14. Biểu thức đối số được đánh giá trước khi method bắt đầu, dù primary có sẵn.
15. Không.
16. Không; ném NPE khi fallback kết quả null.
17. Supplier chỉ được tính khi primary null.
18. Không.
19. Có.
20. Trả lại `index`.
21. `IndexOutOfBoundsException`.
22. `1`.
23. Có thể nuốt lỗi lập trình hoặc che vị trí thất bại thực.
24. Không.
25. Side effect có thể đã xảy ra trước khi phát hiện input sai.
26. Không.
27. Không; phải làm theo hợp đồng nullable.
28. Validation kiểm tra input trước; transaction xử lý tính nhất quán khi thao tác sau vẫn có thể lỗi.
29. Khi caller cần hiển thị/sửa nhiều field lỗi trong một lượt.
30. Ưu tiên kiểm tra loại lỗi và dữ liệu ổn định; không parse message JDK.

## 21. Tổng kết

**`Objects.requireNonNull` giúp xác nhận tham chiếu bắt buộc không được `null` và báo `NullPointerException` ngay tại ranh giới API.** Dạng trả về cho phép dùng trực tiếp trong constructor/gán field; overload `Supplier<String>` chỉ tạo message khi cần. Nếu có fallback hợp lệ, `requireNonNullElse` cung cấp giá trị thay thế, còn `requireNonNullElseGet` tạo fallback lười. Những công cụ đó **không** thay thế kiểm tra chuỗi blank, miền số, trạng thái, chỉ số hoặc bảo mật. Hãy kiểm tra các điều kiện có thể biết **trước side effect**, phân biệt NPE/IAE/ISE/IOBE theo ngữ nghĩa, và giữ cơ chế xử lý I/O, transaction, cạnh tranh luồng độc lập với validation.

</details>

- [Quay lại đầu trang](#back-to-top)
