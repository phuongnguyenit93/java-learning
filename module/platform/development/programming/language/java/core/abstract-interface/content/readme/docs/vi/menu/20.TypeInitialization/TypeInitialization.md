<a id="back-to-top"></a>

# Khởi tạo kiểu

## Menu
- [Phân biệt nạp, liên kết và khởi tạo lớp hoặc interface](#type-initialization-001)
- [Truy cập static field và kiểu thực sự khai báo field](#type-initialization-002)
- [Truy cập hằng thời điểm biên dịch không kích hoạt khởi tạo interface](#type-initialization-003)
- [Khởi tạo interface không mặc nhiên khởi tạo mọi interface cha](#type-initialization-004)
- [Khởi tạo lớp và các interface cha có khai báo default method](#type-initialization-005)
- [Ví dụ theo dõi thứ tự khởi tạo bằng dấu vết thực thi](#type-initialization-006)

## <a id="type-initialization-001">Phân biệt nạp, liên kết và khởi tạo lớp hoặc interface</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt nạp, liên kết và khởi tạo lớp hoặc interface trong Java

Khi JVM sử dụng một kiểu Java, ba khái niệm **nạp (loading)**, **liên kết (linking)** và **khởi tạo (initialization)** mô tả những công việc khác nhau. Chúng có quan hệ theo trình tự, nhưng không đồng nghĩa và cũng không nhất thiết đều diễn ra ngay khi chương trình bắt đầu. Hiểu đúng sự khác nhau giúp giải thích vì sao lỗi có thể xuất hiện trước hoặc trong khối `static`, vì sao một lớp chưa chạy initializer dù đã được nạp, và vì sao một interface có phương thức mặc định đôi khi được khởi tạo khi một lớp triển khai nó.

## 1. Bức tranh tổng quát

Thông thường, vòng đời một kiểu có thể mô tả như sau:

```text
Nạp (Loading)
    ↓
Liên kết (Linking)
    ├─ Xác minh (Verification)
    ├─ Chuẩn bị (Preparation)
    └─ Phân giải tham chiếu (Resolution)
    ↓
Khởi tạo (Initialization)
```

Đây là mô hình khái quát. JVM có thể trì hoãn việc nạp một kiểu cho đến khi cần và có thể phân giải một số tham chiếu muộn hơn thời điểm liên kết ban đầu. Vì vậy, không nên suy ra rằng mọi lớp trong ứng dụng đều được nạp, liên kết và khởi tạo hết ngay lúc khởi động.

| Giai đoạn | JVM thực hiện điều gì? | Điều đó chưa có nghĩa là gì? |
|---|---|---|
| Nạp | Tìm biểu diễn nhị phân của kiểu, tạo biểu diễn kiểu trong JVM và xác định lớp nạp chịu trách nhiệm. | Chưa chạy các khối `static` hay initializer của trường thông thường. |
| Liên kết | Kiểm tra tính hợp lệ, chuẩn bị các trường `static` và phân giải các tham chiếu tượng trưng khi cần. | Chưa thực thi toàn bộ mã khởi tạo tĩnh của kiểu. |
| Khởi tạo | Thực thi các phép gán trường `static` và khối `static` theo quy tắc của Java. | Không phải là một lần nạp hay liên kết mới; mỗi lớp hoặc interface được khởi tạo tối đa một lần cho mỗi định danh lớp trong JVM. |

## 2. Nạp kiểu (Loading)

Nạp là bước JVM tìm dữ liệu nhị phân của lớp hoặc interface và tạo biểu diễn runtime tương ứng. Nguồn dữ liệu thường là tệp `.class`, nhưng cũng có thể đến từ một class loader tùy chỉnh hoặc nguồn khác được loader hỗ trợ.

Các điểm cần nhớ:

- Một **class loader** chịu trách nhiệm tìm và nạp kiểu. Cơ chế delegation thường cho phép loader hỏi loader cha trước, nhưng loader tùy chỉnh có thể có chính sách riêng.
- Sau khi nạp, JVM có thông tin về kiểu và có thể tạo đối tượng `java.lang.Class` đại diện cho kiểu đó.
- Nạp không đồng nghĩa với việc chạy các câu lệnh trong `static { ... }`. Những câu lệnh này thuộc giai đoạn khởi tạo.
- JVM có thể nạp kiểu theo nhu cầu. Việc khai báo một biến có kiểu nào đó không nhất thiết khiến toàn bộ mã của kiểu ấy được khởi tạo ngay.
- Danh tính kiểu phụ thuộc vào cả tên nhị phân lẫn class loader định nghĩa nó. Hai class loader khác nhau có thể định nghĩa hai kiểu khác nhau dù tên nhị phân giống nhau; điều này quan trọng với plugin, container và hệ thống module tự xây dựng.

Có thể yêu cầu nạp một lớp mà chưa khởi tạo bằng `Class.forName(name, false, loader)`. Ngược lại, overload `Class.forName(name)` thông thường yêu cầu khởi tạo lớp được tìm thấy. Các lời gọi reflection và class loader cần được chọn đúng theo mục đích.

## 3. Liên kết (Linking)

Liên kết làm cho kiểu đã nạp sẵn sàng được sử dụng trong runtime. Theo mô hình JVM, liên kết gồm ba phần.

### 3.1. Xác minh (Verification)

JVM kiểm tra cấu trúc và bytecode để bảo đảm kiểu tuân thủ các ràng buộc của định dạng class file và các quy tắc an toàn của máy ảo. Ví dụ, bytecode phải có cấu trúc hợp lệ và các thao tác trên ngăn xếp toán hạng phải nhất quán.

Nếu kiểu không hợp lệ, JVM có thể ném lỗi dạng `VerifyError`. Lỗi này thuộc quá trình xác minh, khác với ngoại lệ do mã bên trong khối `static` tự ném ra khi khởi tạo.

### 3.2. Chuẩn bị (Preparation)

JVM tạo các trường lớp cần thiết và thiết lập giá trị mặc định cho các biến `static`, chẳng hạn:

- `int` có giá trị mặc định `0`;
- `boolean` có giá trị mặc định `false`;
- kiểu tham chiếu có giá trị mặc định `null`.

Đây chưa phải là thời điểm thực thi các biểu thức Java như `static int count = calculate();` hoặc các khối `static`. Các biểu thức và khối đó được thực thi khi kiểu được khởi tạo. Trường hằng biên dịch được khai báo bằng `static final` với biểu thức hằng có quy tắc riêng của JVM; không nên dùng chúng để quan sát thời điểm khởi tạo, vì giá trị hằng có thể được nhúng trực tiếp vào mã bên gọi.

### 3.3. Phân giải tham chiếu (Resolution)

Bytecode thường chứa tham chiếu tượng trưng đến lớp, trường hoặc phương thức. Phân giải chuyển những tham chiếu đó thành các liên kết mà JVM có thể sử dụng trong runtime.

JVM có thể phân giải tham chiếu theo cách sớm hoặc trì hoãn đến khi tham chiếu được sử dụng, tùy theo quy tắc và cách triển khai. Vì thế, không nên coi việc một lớp đã nạp là bằng chứng rằng mọi tham chiếu của nó đã được phân giải hoàn toàn.

## 4. Khởi tạo (Initialization)

Khởi tạo là lúc các initializer Java thực sự chạy. Với lớp, JVM thực thi mã khởi tạo lớp, thường được trình biên dịch biểu diễn trong phương thức đặc biệt `<clinit>`. Mã này bao gồm các phép gán trường `static` và khối `static`, theo thứ tự văn bản xuất hiện trong phần khai báo của lớp.

Ví dụ:

```java
class Example {
    static int first = printAndReturn("first", 1);

    static {
        System.out.println("static block");
    }

    static int second = printAndReturn("second", 2);

    private static int printAndReturn(String name, int value) {
        System.out.println(name);
        return value;
    }
}
```

Khi `Example` được khởi tạo lần đầu, các initializer tĩnh được chạy theo thứ tự trong mã nguồn: in `first`, in `static block`, rồi in `second`. Việc JVM đã nạp hoặc chuẩn bị lớp từ trước không làm thay đổi thứ tự đó.

### 4.1. Những thao tác thường kích hoạt khởi tạo lớp

Theo quy tắc ngôn ngữ Java, khởi tạo lớp thường được kích hoạt trước các thao tác sử dụng chủ động như:

- Tạo một thể hiện bằng `new`.
- Gọi một phương thức `static` được khai báo bởi lớp đó.
- Đọc hoặc ghi một trường `static` không phải biến hằng biên dịch được khai báo bởi lớp đó.
- Một số thao tác reflection yêu cầu khởi tạo, chẳng hạn `Class.forName(name)` với overload mặc định.

Việc chỉ tham chiếu đến kiểu ở những ngữ cảnh không yêu cầu khởi tạo không nhất thiết kích hoạt các initializer tĩnh. Đặc biệt, đọc một biến hằng biên dịch được như `static final int LIMIT = 10;` thường không kích hoạt khởi tạo lớp khai báo hằng, vì trình biên dịch có thể nhúng giá trị `10` vào mã bên gọi.

Một lưu ý khác: nếu gọi phương thức `static` thông qua tên một lớp con nhưng phương thức thực sự được khai báo ở lớp cha, lớp được khởi tạo theo quy tắc của lớp khai báo phương thức. Không nên chỉ nhìn tên đứng trước dấu chấm để kết luận lớp nào sẽ được khởi tạo.

### 4.2. Thứ tự khởi tạo giữa lớp cha, interface và lớp con

Khi một lớp được khởi tạo:

1. Lớp cha của nó được khởi tạo trước.
2. Các superinterface của lớp có khai báo phương thức `default` được khởi tạo theo thứ tự quy định, bao gồm quá trình duyệt các superinterface liên quan.
3. Sau đó các initializer tĩnh của chính lớp được thực thi theo thứ tự văn bản.

Quy tắc với interface có một điểm khác biệt: **khởi tạo một interface không tự động có nghĩa là khởi tạo tất cả superinterface của nó**. Nhưng khi khởi tạo một lớp, JVM sẽ khởi tạo những superinterface liên quan có khai báo phương thức mặc định theo quy tắc trên. Vì vậy, một interface có phương thức `default` có thể được khởi tạo ngay trước lớp triển khai nó, ngay cả khi chương trình chưa gọi phương thức mặc định đó.

## 5. Thử phân biệt nạp kiểu và khởi tạo (Java 17+)

Ví dụ này tập trung vào ranh giới giữa **biết kiểu đã được nạp** và **thực thi static initializer**. Ta chủ động yêu cầu JVM tìm kiểu với cờ không khởi tạo, rồi mới kích hoạt initialization trong một thao tác riêng. Lưu vào tệp `InitializationPhasesDemo.java`.

~~~java
public class InitializationPhasesDemo {
    static class Target {
        static int value = initialize();

        private static int initialize() {
            System.out.println("Target: initialized");
            return 42;
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        System.out.println("main: before load");

        Class<?> target = Class.forName(
                "InitializationPhasesDemo$Target",
                false,
                InitializationPhasesDemo.class.getClassLoader());
        System.out.println("loaded: " + target.getSimpleName());

        System.out.println("main: before active initialization");
        Class.forName(target.getName(), true, target.getClassLoader());
        System.out.println("Target.value = " + Target.value);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac InitializationPhasesDemo.java
java InitializationPhasesDemo
~~~

Stdout của ví dụ:

~~~text
main: before load
loaded: Target
main: before active initialization
Target: initialized
Target.value = 42
~~~

`Class.forName(name, false, loader)` yêu cầu tìm và nạp kiểu nhưng không yêu cầu thực thi initializer. Vì vậy, thông báo `loaded: Target` xuất hiện mà chưa có `Target: initialized`. Đối số `true` ở lời gọi thứ hai yêu cầu khởi tạo kiểu: lúc đó hàm `initialize()` chạy và gán `value = 42`. Lần đọc `Target.value` sau cùng sử dụng giá trị có sẵn, không chạy lại initializer.

Ví dụ **không đo trực tiếp linking**. JVM có thể thực hiện verification, preparation và một phần resolution ở những thời điểm khác nhau mà không in dấu vết từ mã Java. Để phân biệt loading và linking trong chẩn đoán thực tế, cần kết hợp đặc tả JVM với công cụ chẩn đoán phù hợp; riêng thứ tự `println` chỉ chứng minh lúc initializer thực thi.

Quy tắc lớp cha và superinterface có khai báo `default method` khi khởi tạo lớp được khảo sát chuyên sâu ở bài về khởi tạo lớp và interface cha. Đây là hành vi xảy ra **trước initializer của lớp**, không thay đổi ba giai đoạn loading, linking và initialization đang được phân biệt.

## 6. Điều gì xảy ra nếu khởi tạo thất bại?

Nếu mã khởi tạo tĩnh ném một ngoại lệ không phải `Error`, JVM thường biểu diễn lỗi khởi tạo lần đầu bằng `ExceptionInInitializerError`. Nếu chính lỗi là một `Error`, lỗi đó có thể được truyền trực tiếp. Lớp được đánh dấu là khởi tạo thất bại; các lần sử dụng tiếp theo thường gặp `NoClassDefFoundError` thay vì chạy lại initializer như thể chưa có gì xảy ra.

Điều này giải thích vì sao lỗi có thể xuất hiện ở lần đầu một lớp được sử dụng, dù chương trình đã biên dịch thành công và lớp đã được nạp. Biên dịch, xác minh bytecode và chạy initializer là những bước khác nhau, nên thành công ở một bước không bảo đảm thành công ở các bước sau.

## 7. Lỗi hiểu thường gặp và cách kiểm tra

- **Nhầm nạp với khởi tạo:** một lớp có thể đã được nạp nhưng khối `static` chưa chạy. Dùng `Class.forName(name, false, loader)` khi cần yêu cầu nạp mà không yêu cầu khởi tạo.
- **Cho rằng mọi lớp được khởi tạo lúc ứng dụng bắt đầu:** JVM có thể trì hoãn nạp và khởi tạo cho đến khi kiểu được sử dụng theo cách cần thiết.
- **Coi linking là một thao tác duy nhất:** verification, preparation và resolution có mục đích khác nhau; resolution còn có thể được trì hoãn.
- **Cho rằng interface và lớp có thứ tự giống hệt nhau:** khởi tạo interface không tự động khởi tạo tất cả superinterface; còn khi khởi tạo lớp, các superinterface khai báo phương thức `default` được xử lý theo quy tắc riêng.
- **Dùng hằng `static final` để thử thời điểm khởi tạo:** biến hằng biên dịch được có thể được nhúng vào mã gọi, nên đọc nó không phải phép thử đáng tin cậy.
- **Cho rằng lỗi initializer sẽ được thử lại mỗi lần:** sau khi khởi tạo thất bại, JVM ghi nhận trạng thái lỗi của kiểu; các lần dùng sau có thể ném lỗi khác như `NoClassDefFoundError`.

Khi chẩn đoán, hãy xác định thao tác nào đầu tiên yêu cầu khởi tạo kiểu, kiểm tra lớp cha và các interface có phương thức mặc định, rồi đọc các phép gán `static` và khối `static` theo thứ tự mã nguồn. Tách riêng lỗi định dạng/bytecode, lỗi liên kết và lỗi trong initializer giúp khoanh vùng nguyên nhân nhanh hơn.

## 8. Ghi nhớ

- **Loading:** tìm và tạo biểu diễn runtime của kiểu.
- **Linking:** xác minh kiểu, chuẩn bị các trường và phân giải tham chiếu khi cần.
- **Initialization:** chạy các initializer tĩnh theo thứ tự do Java quy định.
- Lớp cha được khởi tạo trước lớp con; superinterface khai báo phương thức `default` có quy tắc khởi tạo đặc biệt khi một lớp được khởi tạo.
- Mỗi lớp hoặc interface được khởi tạo tối đa một lần cho mỗi định danh kiểu trong JVM. Việc nạp và liên kết có thể diễn ra trước đó và không đồng nghĩa với khởi tạo.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-initialization-002">Truy cập static field và kiểu thực sự khai báo field</a>

<details>
<summary>Xem chi tiết</summary>

# Truy cập static field và kiểu được khai báo của field bằng Reflection

Java Reflection cho phép chương trình tra cứu thông tin của một lớp khi chạy, tìm một field theo tên, đọc giá trị field và kiểm tra kiểu được khai báo trong mã nguồn. Với `static field`, ta đọc giá trị thông qua đối tượng `Field` và truyền `null` vào `Field.get`, vì field không thuộc về một instance cụ thể.

## 1. Ba khái niệm cần phân biệt

- **Field được khai báo ở đâu:** `Field.getDeclaringClass()` trả về lớp hoặc interface trực tiếp khai báo field đó.
- **Kiểu được khai báo của field:** `Field.getType()` trả về một `Class<?>` mô tả kiểu field được khai báo, ví dụ `Number`, `String` hoặc `int`. Với field generic, `Field.getGenericType()` cung cấp thêm thông tin kiểu tổng quát khi thông tin đó có trong chữ ký.
- **Kiểu thực tế của giá trị hiện tại:** nếu giá trị khác `null`, gọi `value.getClass()` để xem lớp runtime của đối tượng đang được field tham chiếu tới. Kiểu này có thể là lớp con của kiểu được khai báo.

Ví dụ, nếu khai báo `static Number LIMIT = Integer.valueOf(42);`, thì kiểu được khai báo của `LIMIT` là `Number`, còn lớp runtime của giá trị hiện tại là `Integer`. Hai câu hỏi này khác nhau và Reflection cung cấp API riêng để trả lời.

## 2. Tìm và đọc static field

Các API thường dùng:

| API | Ý nghĩa |
| --- | --- |
| `SomeClass.class` | Lấy đối tượng `Class` đại diện cho kiểu mà không cần tạo instance. |
| `getDeclaredField("NAME")` | Lấy field tên `NAME` được khai báo trực tiếp trong lớp đó; có thể là `public`, `protected`, package-private hoặc `private`. |
| `getField("NAME")` | Tìm field `public`, bao gồm cả field `public` được kế thừa. |
| `field.getType()` | Lấy kiểu được khai báo của field dưới dạng `Class<?>`. |
| `field.getGenericType()` | Lấy kiểu khai báo có thể bao gồm tham số generic dưới dạng `Type`. |
| `Modifier.isStatic(field.getModifiers())` | Kiểm tra field có modifier `static` hay không. |
| `field.get(null)` | Đọc giá trị của static field. Đối số đối tượng bị bỏ qua với field `static`, vì vậy truyền `null` là cách viết rõ ràng. |
| `field.getDeclaringClass()` | Lấy kiểu trực tiếp khai báo field. |

`getDeclaredField` chỉ tìm trong kiểu được gọi trên đó; nó không tự tìm field khai báo ở lớp cha. Nếu không có field với tên đó, phương thức ném `NoSuchFieldException`. Tên field phân biệt chữ hoa và chữ thường.

## 3. Ví dụ hoàn chỉnh chạy với Java 17+

Lưu mã dưới đây vào tệp `StaticFieldReflectionDemo.java`:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class StaticFieldReflectionDemo {
    public static class Settings {
        public static Number LIMIT = Integer.valueOf(42);
        public static String LABEL = "production";
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        inspect(Settings.class, "LIMIT");
        inspect(Settings.class, "LABEL");
    }

    private static void inspect(Class<?> owner, String fieldName)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        Object value = field.get(null);

        System.out.println("field: " + field.getName());
        System.out.println("declaring class: "
                + field.getDeclaringClass().getTypeName());
        System.out.println("declared type: " + field.getType().getTypeName());
        System.out.println("static: " + Modifier.isStatic(field.getModifiers()));
        System.out.println("value: " + value);
        System.out.println("runtime type: "
                + (value == null ? "null" : value.getClass().getTypeName()));
        System.out.println();
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```shell
javac StaticFieldReflectionDemo.java
java StaticFieldReflectionDemo
```

Stdout chính xác:

```text
field: LIMIT
declaring class: StaticFieldReflectionDemo$Settings
declared type: java.lang.Number
static: true
value: 42
runtime type: java.lang.Integer

field: LABEL
declaring class: StaticFieldReflectionDemo$Settings
declared type: java.lang.String
static: true
value: production
runtime type: java.lang.String
```

Trong kết quả của `LIMIT`, `getType()` trả về `Number.class`, vì đó là kiểu field đã khai báo. Giá trị hiện tại là một `Integer`, nên `getClass()` trên giá trị trả về `Integer.class`. Không gọi `getClass()` khi giá trị là `null`; ví dụ kiểm tra null trước để tránh `NullPointerException`.

## 4. Đọc static field và instance field khác nhau thế nào?

```java
Field staticField = Settings.class.getDeclaredField("LIMIT");
Object limit = staticField.get(null);

Field instanceField = SomeClass.class.getDeclaredField("name");
Object name = instanceField.get(someObject);
```

Đối với static field, không cần instance của lớp khai báo field; đối số `null` biểu đạt điều đó. Đối với instance field, phải truyền một đối tượng tương thích làm nơi lấy giá trị. Nếu truyền `null` cho một instance field, việc đọc sẽ thất bại với `NullPointerException`. Trong cả hai trường hợp, `getType()` vẫn cho biết kiểu được khai báo, không phụ thuộc vào giá trị hiện tại.

Đọc một static field bằng Reflection có thể kích hoạt khởi tạo lớp nếu lớp chưa được khởi tạo. Vì vậy, nếu việc khởi tạo có side effect, cần tính đến điều đó khi dùng Reflection.

## 5. Field kế thừa, kiểu khai báo thực sự và tác động lên khởi tạo

Khi mã dùng `Child.FIELD`, tên `Child` trước dấu chấm không chứng minh field được **khai báo** trong `Child`. Java phân giải tên field tại lúc biên dịch theo quan hệ kế thừa và che khuất thành viên. Nếu field thực sự do `Parent` khai báo, thao tác đọc field `static` không phải hằng thời điểm biên dịch kích hoạt khởi tạo **`Parent`**, không tự kích hoạt `Child` chỉ do cách viết biểu thức.

Đối tượng `Field` giúp xác minh chủ sở hữu tại runtime: `getDeclaringClass()` xác định nơi khai báo; `getType()` xác định kiểu dữ liệu được khai báo. Hai kết quả này trả lời hai câu hỏi khác nhau. Ví dụ lưu vào `StaticFieldOwnerDemo.java`:

~~~java
import java.lang.reflect.Field;

public class StaticFieldOwnerDemo {
    public static class Parent {
        public static int SHARED = initialize();

        private static int initialize() {
            System.out.println("init Parent");
            return 10;
        }
    }

    public static class Child extends Parent {
        static {
            System.out.println("init Child");
        }

        public static int OWN = 20;
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Field inherited = Child.class.getField("SHARED");
        System.out.println("declaring type: "
                + inherited.getDeclaringClass().getSimpleName());
        System.out.println("reflective read: " + inherited.get(null));
        System.out.println("Child.SHARED: " + Child.SHARED);
        System.out.println("Child.OWN: " + Child.OWN);
    }
}
~~~

~~~bash
javac StaticFieldOwnerDemo.java
java StaticFieldOwnerDemo
~~~

~~~text
declaring type: Parent
init Parent
reflective read: 10
Child.SHARED: 10
init Child
Child.OWN: 20
~~~

Tra cứu bằng `Child.class.getField("SHARED")` tìm thấy field public được kế thừa mà chưa đọc giá trị field. `getDeclaringClass()` trả về `Parent`; sau đó `get(null)` kích hoạt khởi tạo `Parent` để đọc `SHARED`. Biểu thức `Child.SHARED` cũng trỏ đến field do `Parent` khai báo, nên không khởi tạo `Child`. Chỉ khi đọc `Child.OWN`, field do chính `Child` khai báo, chương trình mới in `init Child`.

**Field hiding** là trường hợp khác: nếu `Child` tự khai báo field cùng tên với `Parent.SHARED`, mỗi khai báo có một field riêng. `Child.class.getDeclaredField("SHARED")` trả về field của `Child`, còn `Parent.class.getDeclaredField("SHARED")` trả về field của `Parent`. Kiểu tham chiếu dùng để truy cập quyết định khai báo được phân giải; với Reflection, hãy kiểm tra `getDeclaringClass()` để tránh suy luận nhầm. Quy tắc này cũng áp dụng khi field được khai báo bởi một interface và được tham chiếu qua tên interface con.

## 6. Field private và giới hạn truy cập

`getDeclaredField` có thể tìm một field `private`, nhưng tìm thấy không có nghĩa là được phép đọc nó. Với field private trong mã do ứng dụng kiểm soát, có thể yêu cầu mở quyền truy cập bằng `field.setAccessible(true)` trước khi đọc:

```java
Field field = Settings.class.getDeclaredField("SECRET");
field.setAccessible(true);
Object secret = field.get(null); // chỉ khi quyền truy cập được cho phép
```

Trong Java hiện đại, `setAccessible(true)` có thể thất bại, chẳng hạn với `InaccessibleObjectException`, nếu ranh giới module không cho phép mở quyền truy cập. Không nên dựa vào việc phá vỡ encapsulation khi API công khai đã đáp ứng nhu cầu. Chỉ đọc private field khi có lý do rõ ràng và kiểm soát được môi trường chạy.

Với `static final` field, việc đọc và việc sửa là hai chuyện khác nhau. Không nên dùng Reflection để cố sửa hằng số; quyền sửa final field bị hạn chế và không phải một cách cập nhật cấu hình đáng tin cậy.

## 7. Xử lý lỗi và các lưu ý thực tế

- `NoSuchFieldException`: tên field không tồn tại trong lớp được truy vấn bằng `getDeclaredField`, hoặc field nằm trong một lớp khác như lớp cha.
- `IllegalAccessException`: chương trình không có quyền truy cập field theo quy tắc truy cập hiện hành.
- `InaccessibleObjectException`: yêu cầu mở quyền truy cập bị hệ thống module từ chối khi dùng `setAccessible(true)`.
- `IllegalArgumentException`: đối tượng truyền cho field instance không tương thích với kiểu khai báo của field. Với static field, đối số instance không được sử dụng.
- `getType()` trả về kiểu khai báo, còn `getGenericType()` hữu ích khi cần thông tin generic như `List<String>`. Do type erasure, `getType()` của field `List<String>` chỉ là `List.class`.
- Nếu giá trị là kiểu nguyên thủy như `int`, Reflection trả giá trị qua cơ chế boxing khi dùng `Field.get`, ví dụ giá trị `int` được biểu diễn dưới dạng `Integer` trong `Object`.
- Khi chỉ cần đọc một giá trị trong mã ứng dụng bình thường, truy cập trực tiếp `Settings.LIMIT` thường rõ ràng và an toàn hơn. Reflection phù hợp khi tên lớp/field cần được khám phá lúc chạy, chẳng hạn trong framework, công cụ kiểm tra hoặc bộ ánh xạ dữ liệu.

## 8. Checklist

- [ ] Dùng đúng `Class<?>` của lớp chứa field.
- [ ] Chọn `getDeclaredField` cho field khai báo trực tiếp hoặc `getField` cho field public kể cả khi được kế thừa.
- [ ] Dùng `Modifier.isStatic(...)` nếu cần xác minh field là static.
- [ ] Gọi `field.get(null)` để đọc static field.
- [ ] Dùng `field.getType()` cho kiểu khai báo và `value.getClass()` cho lớp runtime khi giá trị khác `null`.
- [ ] Xử lý ngoại lệ Reflection và quyền truy cập theo môi trường module.

Quy tắc quan trọng nhất: kiểu khai báo thuộc về định nghĩa field; kiểu runtime thuộc về đối tượng đang được tham chiếu. Khi đọc static field bằng Reflection, lấy field từ đúng lớp, đọc với `get(null)`, rồi chọn API kiểu phù hợp với câu hỏi cần trả lời.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-initialization-003">Truy cập hằng thời điểm biên dịch không kích hoạt khởi tạo interface</a>

<details>
<summary>Xem chi tiết</summary>

# Truy cập hằng thời điểm biên dịch không kích hoạt khởi tạo interface

Trong Java, việc đọc một **hằng thời điểm biên dịch** (*constant variable*) được khai báo trong interface không tự kích hoạt quá trình khởi tạo interface đó. Đây là một ngoại lệ quan trọng khi tìm hiểu thời điểm khởi tạo kiểu, bởi một số lần truy cập trường `static` có thể kích hoạt khởi tạo, trong khi truy cập hằng thời điểm biên dịch thì không.

Quy tắc này áp dụng cho biến `static final` có kiểu nguyên thủy hoặc `String`, được khởi tạo bằng một **biểu thức hằng** (*constant expression*). Chỉ có `static final` thôi là chưa đủ. Muốn xác định một lần đọc có kích hoạt khởi tạo interface hay không, hãy kiểm tra kiểu dữ liệu và biểu thức khởi tạo của trường.

## 1. Vì sao quy tắc này quan trọng?

Interface có thể khai báo trường, và các trường được ngầm định là `public static final`. Tuy vậy, không phải mọi trường trong interface đều là hằng thời điểm biên dịch.

Ví dụ:

~~~java
interface Limits {
    int MAX_ITEMS = 100;
    int COMPUTED_ITEMS = Integer.parseInt("100");
}
~~~

`MAX_ITEMS` là hằng thời điểm biên dịch: kiểu là `int` và biểu thức `100` là biểu thức hằng. Ngược lại, `COMPUTED_ITEMS` không phải hằng thời điểm biên dịch vì lời gọi phương thức `Integer.parseInt(...)` không phải biểu thức hằng, dù kết quả khi chạy là `100`.

Nếu mã khác chỉ đọc `Limits.MAX_ITEMS`, thao tác đọc này không yêu cầu khởi tạo `Limits`. Nếu mã đọc `Limits.COMPUTED_ITEMS` khi interface chưa được khởi tạo, việc đọc trường `static` không phải hằng sẽ kích hoạt khởi tạo interface.

## 2. Khi nào một trường là hằng thời điểm biên dịch?

Theo quy tắc của Java, một *constant variable* là biến `final` có kiểu nguyên thủy hoặc kiểu `String`, được khởi tạo bằng biểu thức hằng. Trong interface, `final` được ngầm định cho các trường, nhưng vẫn cần đáp ứng cả điều kiện kiểu dữ liệu và biểu thức khởi tạo.

| Khai báo trong interface | Là hằng thời điểm biên dịch? | Lý do |
| --- | --- | --- |
| `int COUNT = 12;` | Có | `int` và literal hằng |
| `static final long MASK = 1L << 4;` | Có | Kiểu nguyên thủy và phép toán trên các hằng |
| `String NAME = "Java" + " SE";` | Có | `String` được tạo từ biểu thức hằng |
| `int COUNT = Integer.parseInt("12");` | Không | Có lời gọi phương thức |
| `static final Integer COUNT = 12;` | Không | `Integer` là kiểu tham chiếu, không phải kiểu nguyên thủy hay `String` |
| `static final int COUNT = loadCount();` | Không | Lời gọi phương thức không phải biểu thức hằng |
| `static final String NAME = new String("Java");` | Không | Biểu thức tạo đối tượng không phải biểu thức hằng |

Từ khóa `static` và `final` không tự biến một trường thành hằng thời điểm biên dịch. Ví dụ `static final Integer VALUE = 5` vẫn là trường tham chiếu kiểu `Integer`; truy cập nó có thể kích hoạt khởi tạo interface nếu interface chưa được khởi tạo.

## 3. Ví dụ Java chạy hoàn chỉnh

Ví dụ dưới đây đặt một hằng thời điểm biên dịch và một trường `static` không phải hằng trong cùng interface. Phương thức khởi tạo in thông báo để có thể quan sát thời điểm khởi tạo.

~~~java
interface BuildInfo {
    int COMPILE_TIME_LIMIT = 100;
    String LABEL = "v" + "1";
    int RUNTIME_LIMIT = initialize();

    static int initialize() {
        System.out.println("BuildInfo initialized");
        return 200;
    }
}

public class InterfaceConstantDemo {
    public static void main(String[] args) {
        System.out.println(BuildInfo.COMPILE_TIME_LIMIT);
        System.out.println(BuildInfo.LABEL);
        System.out.println("After constant access");
        System.out.println(BuildInfo.RUNTIME_LIMIT);
    }
}
~~~

Lưu mã vào tệp `InterfaceConstantDemo.java`, sau đó biên dịch và chạy bằng Java 17 trở lên:

~~~text
javac InterfaceConstantDemo.java
java InterfaceConstantDemo
~~~

Kết quả stdout chính xác:

~~~text
100
v1
After constant access
BuildInfo initialized
200
~~~

Hai lần đọc đầu tiên sử dụng `COMPILE_TIME_LIMIT` và `LABEL`. Cả hai là hằng thời điểm biên dịch nên việc đọc chúng không kích hoạt khởi tạo `BuildInfo`. Dòng `After constant access` xuất hiện trước thông báo khởi tạo, chứng minh điều đó trong lần chạy này.

Khi chương trình đọc `BuildInfo.RUNTIME_LIMIT`, JVM cần giá trị của một trường `static` không phải hằng thời điểm biên dịch. Việc đọc này kích hoạt khởi tạo interface; biểu thức khởi tạo gọi `initialize()`, in `BuildInfo initialized`, rồi trả về `200`.

Kết quả trên minh họa hành vi của chương trình khi interface chưa được khởi tạo bởi nguyên nhân nào khác. Nếu một thao tác trước đó đã khởi tạo interface, thông báo khởi tạo có thể đã xuất hiện trước lần đọc hằng.

## 4. Không phải mọi trường `static final` đều được nội tuyến

Trình biên dịch Java có thể thay một tham chiếu đến hằng thời điểm biên dịch bằng chính giá trị hằng trong mã gọi. Cơ chế này thường được gọi là *constant inlining* (nội tuyến hằng). Bởi vậy, mã dùng `BuildInfo.COMPILE_TIME_LIMIT` có thể không cần đọc trường đó tại thời gian chạy.

Đối với một trường `static final` không phải hằng thời điểm biên dịch, chẳng hạn `RUNTIME_LIMIT`, mã gọi vẫn cần lấy giá trị trường khi chạy. Nếu interface chưa được khởi tạo, lần đọc này là một hành động có thể kích hoạt khởi tạo.

Cần phân biệt:

- **Hằng thời điểm biên dịch:** kiểu nguyên thủy hoặc `String`, khai báo `final` và khởi tạo bằng biểu thức hằng. Đọc trường không tự kích hoạt khởi tạo interface.
- **Trường `static final` thông thường:** có thể mang giá trị cố định sau khi khởi tạo, nhưng biểu thức khởi tạo không phải biểu thức hằng hoặc kiểu dữ liệu không đáp ứng yêu cầu. Đọc trường khi interface chưa khởi tạo có thể kích hoạt khởi tạo.
- **Trường không `final`:** không phải hằng thời điểm biên dịch; đọc trường `static` khi interface chưa khởi tạo cũng có thể kích hoạt khởi tạo.

Nói cách khác, giá trị có vẻ không đổi khi chạy không có nghĩa là nó là hằng thời điểm biên dịch theo định nghĩa của Java.

## 5. Hệ quả khi thay đổi giá trị hằng trong thư viện

Do hằng thời điểm biên dịch có thể được nội tuyến vào mã gọi, thay đổi giá trị hằng trong một thư viện rồi chỉ thay tệp thư viện mà không biên dịch lại mã khách hàng có thể khiến chương trình tiếp tục dùng giá trị cũ.

Ví dụ, thư viện ban đầu khai báo `int RETRY_LIMIT = 3`, rồi phiên bản mới đổi thành `int RETRY_LIMIT = 5`. Nếu một lớp khách hàng đã được biên dịch khi giá trị là `3`, mã bytecode của lớp khách hàng có thể vẫn chứa số `3`. Chỉ cập nhật tệp thư viện không đảm bảo lớp khách hàng tự động nhận giá trị mới.

Khi thay đổi một hằng công khai có thể được sử dụng bởi mã đã biên dịch độc lập, hãy cân nhắc tương thích nhị phân và biên dịch lại các thành phần phụ thuộc khi cần. Nếu giá trị cần thay đổi mà không muốn nội tuyến vào mã khách hàng, có thể cung cấp một phương thức truy cập thay vì một trường hằng, sau khi cân nhắc API và yêu cầu tương thích.

## 6. Interface khởi tạo khác với class khởi tạo như thế nào?

Quy tắc về hằng cần được đặt trong bối cảnh quy tắc khởi tạo kiểu rộng hơn:

- Khởi tạo một class thường bao gồm việc khởi tạo superclass của nó và một số superinterface liên quan có khai báo phương thức `default`.
- Khởi tạo một interface không tự động khởi tạo tất cả superinterface của nó.
- Việc đọc một trường `static` không phải hằng thời điểm biên dịch được khai báo trong interface có thể kích hoạt khởi tạo interface đó.
- Việc đọc hằng thời điểm biên dịch được khai báo trong interface không tự kích hoạt khởi tạo interface đó.

Không nên suy luận rằng bất kỳ thao tác nào có nhắc đến tên interface đều khởi tạo interface. Tên kiểu có thể xuất hiện trong kiểu khai báo, chữ ký, hoặc một tham chiếu hằng đã được trình biên dịch thay bằng giá trị. Hành động thực tế và loại thành viên được truy cập mới quyết định có kích hoạt khởi tạo hay không.

## 7. Các tình huống dễ nhầm

| Tình huống | Có tự kích hoạt khởi tạo interface? | Giải thích |
| --- | --- | --- |
| Đọc `Settings.TIMEOUT`, trong đó `TIMEOUT` là `static final int` được khởi tạo bằng literal | Không | Đây là hằng thời điểm biên dịch |
| Đọc `Settings.NAME`, trong đó `NAME` là `String` được ghép từ các literal | Không | Biểu thức ghép là biểu thức hằng |
| Đọc `Settings.TIMEOUT`, trong đó trường được gán bằng lời gọi phương thức | Có, nếu interface chưa khởi tạo | Đây là trường `static` không phải hằng thời điểm biên dịch |
| Đọc `Settings.VALUE`, trong đó trường có kiểu `Integer` và là `static final` | Có, nếu interface chưa khởi tạo | `Integer` không đáp ứng yêu cầu kiểu của constant variable |
| Đọc một hằng sau khi interface đã được khởi tạo vì một nguyên nhân khác | Không tạo ra lần khởi tạo mới | Việc khởi tạo chỉ xảy ra một lần cho mỗi class loader |
| Dùng một hằng trong biểu thức mã nguồn | Thông thường không | Nếu đó là hằng thời điểm biên dịch hợp lệ, giá trị có thể được nội tuyến khi biên dịch |

Bảng trên nói về việc thao tác đó có tự kích hoạt khởi tạo hay không. Nó không khẳng định rằng interface chắc chắn chưa từng được khởi tạo trước thao tác đang xét.

## 8. Cách kiểm tra trong dự án thực tế

Khi điều tra vì sao một thông báo khởi tạo xuất hiện sớm hoặc muộn, hãy kiểm tra các điểm sau:

1. Xác nhận trường có kiểu nguyên thủy hoặc `String` và được khởi tạo bằng biểu thức hằng hay không. Đừng chỉ dựa vào `static final`.
2. Tìm các nơi truy cập trường `static` không phải hằng, gọi phương thức `static` của interface, hoặc các hành động khác có thể khởi tạo kiểu.
3. Tạo một ví dụ tối giản có thông báo trong biểu thức khởi tạo để quan sát thứ tự thực tế.
4. Nếu thay đổi hằng trong thư viện, kiểm tra xem các lớp khách hàng đã được biên dịch lại hay vẫn chứa giá trị đã nội tuyến.
5. Khi kiểm tra thứ tự khởi tạo, đảm bảo không có thao tác trước đó đã khởi tạo interface và làm sai lệch quan sát.

## 9. Ghi nhớ

Điều quyết định là **hằng thời điểm biên dịch** theo định nghĩa của Java, chứ không đơn thuần là trường `static final`. Đọc hằng thời điểm biên dịch được khai báo trong interface không tự kích hoạt khởi tạo interface. Đọc trường `static` không phải hằng thời điểm biên dịch có thể kích hoạt khởi tạo nếu interface chưa được khởi tạo. Sự khác biệt này cũng giải thích vì sao hằng công khai có thể được nội tuyến vào mã khách hàng và vì sao việc cập nhật giá trị hằng đôi khi cần biên dịch lại các thành phần phụ thuộc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-initialization-004">Khởi tạo interface không mặc nhiên khởi tạo mọi interface cha</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo interface không mặc nhiên khởi tạo mọi interface cha

Trong Java, **khởi tạo (initialization) một interface con không tự động khởi tạo các interface cha của nó**. Đây là điểm khác biệt quan trọng so với khởi tạo một class: trước khi một class được khởi tạo, JVM sẽ khởi tạo superclass của nó và các superinterface có khai báo phương thức `default`. Quy tắc này giúp tránh chạy khởi tạo của mọi interface chỉ vì một interface khác kế thừa nó.

Để hiểu đúng hành vi, hãy phân biệt việc JVM nạp/kiểm tra kiểu với việc khởi tạo kiểu. Khởi tạo là giai đoạn thực thi các biểu thức khởi tạo biến `static` của kiểu đó. Một interface có thể được dùng làm kiểu cha mà chưa chạy phần khởi tạo của nó.

## 1. Khởi tạo trực tiếp một interface

Một interface thường được khởi tạo khi mã lần đầu chủ động sử dụng một thành viên tĩnh được khai báo trong chính interface đó, chẳng hạn gọi phương thức `static` của interface hoặc đọc một trường `static` không phải hằng số biên dịch. Việc truy cập trường là một điểm cần chú ý: nếu trường được khai báo ở interface cha, việc viết tên interface con ở phía trước không biến trường đó thành trường do interface con khai báo.

Khi một interface con được khởi tạo, JVM **không cần khởi tạo trước tất cả interface cha**. Vì vậy, chỉ khai báo `interface Child extends Parent` không có nghĩa các biểu thức khởi tạo của `Parent` sẽ chạy khi `Child` được khởi tạo.

## 2. Ví dụ Java 17+ có thể biên dịch và chạy

Lưu đoạn mã sau thành `InterfaceInitializationDemo.java`:

```java
public class InterfaceInitializationDemo {
    static class InitLog {
        static String mark(String name) {
            System.out.println("init " + name);
            return name;
        }
    }

    interface Parent {
        String PARENT_VALUE = InitLog.mark("Parent");

        default void inheritedDefault() {
        }
    }

    interface Child extends Parent {
        String CHILD_VALUE = InitLog.mark("Child");
    }

    static class Impl implements Child {
        static {
            System.out.println("init Impl");
        }
    }

    public static void main(String[] args) {
        System.out.println("Access Child.CHILD_VALUE");
        System.out.println(Child.CHILD_VALUE);

        System.out.println("Create Impl");
        new Impl();

        System.out.println("Access Parent.PARENT_VALUE");
        System.out.println(Parent.PARENT_VALUE);
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```text
javac InterfaceInitializationDemo.java
java InterfaceInitializationDemo
```

Đầu ra:

```text
Access Child.CHILD_VALUE
init Child
Child
Create Impl
init Parent
init Impl
Access Parent.PARENT_VALUE
Parent
```

## 3. Giải thích thứ tự khởi tạo

**Bước 1 — đọc `Child.CHILD_VALUE`.** Trường này được khởi tạo bằng lời gọi `InitLog.mark`, nên không phải hằng số biên dịch. Việc đọc nó kích hoạt khởi tạo `Child` và in `init Child`. Dù `Child extends Parent`, biểu thức khởi tạo của `Parent` chưa chạy; việc khởi tạo interface con không tự khởi tạo interface cha.

**Bước 2 — tạo `Impl`.** Trước khi JVM khởi tạo class `Impl`, JVM khởi tạo superclass của nó và các superinterface trong cây kế thừa có khai báo phương thức `default`. `Parent` khai báo `inheritedDefault`, nên `Parent` được khởi tạo trước và in `init Parent`. Sau đó khối `static` của `Impl` chạy, in `init Impl`.

**Bước 3 — đọc `Parent.PARENT_VALUE`.** `Parent` đã được khởi tạo ở bước 2, nên biểu thức `InitLog.mark("Parent")` không chạy lần nữa. Chương trình chỉ in giá trị trường `Parent`.

Ví dụ này thể hiện hai quy tắc khác nhau: khởi tạo một interface không kéo theo việc khởi tạo mọi superinterface; còn khởi tạo một class có thể kéo theo việc khởi tạo các superinterface khai báo phương thức `default` trước khi chạy phần khởi tạo của class đó.

## 4. Quy tắc cần nhớ

| Tình huống | Điều xảy ra |
| --- | --- |
| Đọc trường không phải hằng số biên dịch được khai báo trực tiếp trong `Child` | Có thể kích hoạt khởi tạo `Child`; không tự động khởi tạo mọi interface cha. |
| Gọi phương thức `static` được khai báo trong một interface | Kích hoạt khởi tạo interface khai báo phương thức đó. Phương thức `static` của interface không được kế thừa như phương thức instance. |
| Khởi tạo class `Impl` | JVM khởi tạo superclass và các superinterface khai báo phương thức `default` theo quy tắc khởi tạo class trước khi thực thi khối khởi tạo tĩnh của class. |
| Đọc trường được khai báo ở `Parent` qua tên `Parent` | Nếu trường không phải hằng số biên dịch, việc đọc kích hoạt khởi tạo `Parent` nếu nó chưa được khởi tạo. |
| Đọc hằng số biên dịch như `int LIMIT = 100` được khai báo trong interface | Bản thân việc đọc hằng số không kích hoạt khởi tạo interface. |

Các trường khai báo trong interface mặc nhiên là `public static final`. Tuy vậy, không phải trường `final` nào cũng là hằng số biên dịch. Trường kiểu nguyên thủy hoặc `String` được gán bằng biểu thức hằng mới là constant variable theo quy tắc của Java. Ví dụ `String VALUE = InitLog.mark("Child")` không phải biểu thức hằng, nên việc đọc trường sẽ kích hoạt khởi tạo interface khai báo nó.

Nếu một trường do `Parent` khai báo được tham chiếu qua tên `Child`, hãy nhớ rằng trường đó vẫn thuộc về `Parent`. Khi trường không phải hằng số biên dịch, thao tác truy cập có thể kích hoạt khởi tạo `Parent`; không nên suy luận chỉ từ tên đứng trước dấu chấm.

## 5. Lỗi hiểu thường gặp

- **Cho rằng `extends` đồng nghĩa với khởi tạo cha:** quan hệ kế thừa xác định kiểu và thành viên, nhưng khởi tạo interface có quy tắc riêng. Khởi tạo interface con không mặc nhiên khởi tạo các interface cha.
- **Nhầm quy tắc của class với interface:** khi khởi tạo class, JVM có xử lý đặc biệt đối với superinterface khai báo phương thức `default`. Không áp dụng quy tắc đó như thể mỗi lần khởi tạo một interface con cũng phải khởi tạo mọi cha.
- **Cho rằng mọi trường `static final` đều không kích hoạt khởi tạo:** điều đó chỉ đúng với constant variable được Java xác định là hằng số biên dịch. Giá trị lấy từ lời gọi phương thức hoặc phép tính không phải biểu thức hằng có thể kích hoạt khởi tạo.
- **Bỏ qua interface khai báo thực sự trường:** tên interface dùng trong biểu thức truy cập không nhất thiết là nơi khai báo trường. Hãy xác định thành viên được khai báo ở interface nào để hiểu interface nào sẽ được khởi tạo.

## 6. Cách áp dụng khi debug

Khi thấy log khởi tạo tĩnh xuất hiện theo thứ tự bất ngờ, hãy xác định thao tác đầu tiên gây khởi tạo: đọc trường nào, gọi phương thức `static` nào, hay khởi tạo class nào. Sau đó kiểm tra trường đó có phải constant variable không và các interface nào trong cây kế thừa khai báo phương thức `default`. Không kết luận rằng một interface cha đã được khởi tạo chỉ vì interface con kế thừa nó.

Trong thiết kế thực tế, nên tránh dựa vào side effect phức tạp trong biểu thức khởi tạo trường interface. Nếu cần theo dõi thứ tự, dùng ví dụ nhỏ có log rõ ràng như trên và kiểm tra bằng một chương trình chạy độc lập.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-initialization-005">Khởi tạo lớp và các interface cha có khai báo default method</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo lớp và các interface cha có khai báo default method

Khi Java khởi tạo một lớp, JVM không chỉ xử lý lớp cha theo quan hệ kế thừa lớp. Java còn khởi tạo các superinterface có khai báo phương thức `default` của lớp đó trước khi chạy các trình khởi tạo `static` của lớp. Quy tắc này bảo đảm các interface liên quan đã được khởi tạo trước khi mã của lớp bắt đầu thực thi.

Điểm quan trọng là **không phải mọi interface mà lớp triển khai đều tự động được khởi tạo**. Quy tắc đặc biệt áp dụng cho các superinterface khai báo phương thức `default`; interface khác chỉ được khởi tạo khi có một thao tác chủ động sử dụng nó.

## 1. Khi nào một lớp được khởi tạo?

Khởi tạo lớp (*class initialization*) là quá trình thực thi các bộ khởi tạo biến `static` và khối `static` của lớp theo thứ tự xuất hiện trong mã nguồn. Quá trình này khác với khởi tạo đối tượng (*instance initialization*), vốn bao gồm các giá trị khởi tạo trường instance, khối khởi tạo instance và constructor.

Một lớp thường được khởi tạo ngay trước lần đầu xảy ra một thao tác chủ động sử dụng lớp, chẳng hạn:

- Tạo đối tượng bằng `new`.
- Gọi một phương thức `static` được khai báo trong lớp.
- Gán cho trường `static` được khai báo trong lớp.
- Đọc trường `static` được khai báo trong lớp, nếu trường đó không phải biến hằng compile-time.

Khi lớp được khởi tạo lần đầu, Java thực hiện các bước theo thứ tự khái quát:

1. Khởi tạo superclass trước.
2. Khởi tạo các superinterface của lớp có khai báo phương thức `default`, theo thứ tự liệt kê đệ quy mà Java quy định.
3. Chạy các bộ khởi tạo trường `static` và khối `static` của chính lớp, theo thứ tự văn bản trong lớp.

Nếu lớp cha hoặc một superinterface cần thiết đã được khởi tạo trước đó, Java không khởi tạo nó lần thứ hai. Một lớp hoặc interface chỉ trải qua quá trình khởi tạo một lần trong vòng đời của class loader tương ứng.

## 2. Vì sao interface có default method được xử lý đặc biệt?

`default method` là phương thức instance có phần thân được khai báo trong interface. Nó cho phép các lớp triển khai kế thừa một hành vi mặc định mà không cần tự viết lại phương thức đó.

Ví dụ:

~~~java
interface Auditable {
    int INITIALIZED = InitLog.mark("Auditable");

    default void audit() {
        System.out.println("audit");
    }
}
~~~

Trường `INITIALIZED` là trường `public static final` theo quy tắc của interface. Giá trị của nó được tính bằng lời gọi phương thức, nên không phải biến hằng compile-time. Khi interface `Auditable` được khởi tạo, lời gọi `InitLog.mark(...)` sẽ được thực thi.

Nếu một lớp triển khai `Auditable`, quá trình khởi tạo lớp sẽ khởi tạo interface `Auditable` trước khi chạy các trình khởi tạo `static` của lớp. Điều này vẫn đúng ngay cả khi chương trình không gọi `audit()` trong quá trình tạo đối tượng.

Java xét các superinterface trong quan hệ kế thừa đệ quy, không chỉ các interface được liệt kê trực tiếp trên lớp. Vì vậy, nếu `Child` kế thừa một `default method` từ `Parent`, thì `Parent` vẫn thuộc nhóm interface cần xét khi khởi tạo một lớp triển khai `Child`.

Cần phân biệt hai khái niệm:

- **Khai báo `default method`:** interface tự khai báo một phương thức `default`.
- **Kế thừa `default method`:** interface nhận phương thức mặc định từ interface cha nhưng không tự khai báo phương thức đó.

Một interface chỉ kế thừa phương thức mặc định mà không tự khai báo `default method` không được chọn chỉ vì nó kế thừa phương thức ấy. Tuy nhiên, interface cha thực sự khai báo phương thức mặc định vẫn được xét riêng trong quá trình duyệt các superinterface.

## 3. Ví dụ Java 17+ có thể chạy

Lưu chương trình sau vào tệp `DefaultInterfaceInitialization.java`.

~~~java
class InitLog {
    static int mark(String name) {
        System.out.println(name);
        return 0;
    }
}

class Base {
    static int initialized = InitLog.mark("Base");
}

interface PlainParent {
    int initialized = InitLog.mark("PlainParent");
}

interface RootDefault {
    int initialized = InitLog.mark("RootDefault");

    default void root() {
    }
}

interface ChildWithDefault extends PlainParent, RootDefault {
    int initialized = InitLog.mark("ChildWithDefault");

    default void child() {
    }
}

interface Marker {
    int initialized = InitLog.mark("Marker");
}

class Sample extends Base implements ChildWithDefault, Marker {
    static int initialized = InitLog.mark("Sample");
}

public class DefaultInterfaceInitialization {
    public static void main(String[] args) {
        System.out.println("start");

        new Sample();
        System.out.println("instance ready");

        // Lần khởi tạo Sample thứ hai không chạy lại các initializer.
        new Sample();

        // Marker không khai báo default method, nên chỉ được khởi tạo
        // khi trường non-constant của chính interface được đọc.
        System.out.println("Marker value=" + Marker.initialized);
        System.out.println("PlainParent value=" + PlainParent.initialized);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~text
javac DefaultInterfaceInitialization.java
java DefaultInterfaceInitialization
~~~

Kết quả chuẩn:

~~~text
start
Base
RootDefault
ChildWithDefault
Sample
instance ready
Marker
Marker value=0
PlainParent
PlainParent value=0
~~~

### Đọc kết quả theo thứ tự

**`start` xuất hiện đầu tiên.** Chưa có thao tác nào yêu cầu khởi tạo `Sample`, nên các initializer chưa chạy.

**`Base` xuất hiện trước.** Lệnh `new Sample()` cần khởi tạo lớp `Sample`, vì vậy superclass `Base` được khởi tạo trước. Trường `Base.initialized` gọi `InitLog.mark("Base")`, tạo dòng `Base`.

**`RootDefault` xuất hiện trước `ChildWithDefault`.** `Sample` triển khai `ChildWithDefault`; interface này có khai báo `default method`. Khi duyệt các superinterface, Java gặp `RootDefault`, vốn cũng tự khai báo `default method`, trước khi khởi tạo `ChildWithDefault`. Cả hai interface được khởi tạo trước phần `static` của `Sample`.

**`Sample` xuất hiện sau các interface đủ điều kiện.** Lúc này superclass và các superinterface cần khởi tạo đã hoàn tất, nên trường `static` của `Sample` mới được xử lý.

**`instance ready` xuất hiện sau khi `new Sample()` hoàn tất.** Việc tạo đối tượng chỉ hoàn tất sau khi lớp đã được khởi tạo và quá trình khởi tạo instance chạy xong.

**Lần `new Sample()` thứ hai không in thêm dòng khởi tạo.** Java không chạy lại các initializer khi lớp đã khởi tạo thành công.

**`Marker` và `PlainParent` chỉ xuất hiện ở cuối.** Hai interface này không tự khai báo `default method`, nên không được tự động khởi tạo chỉ vì `Sample` triển khai hoặc gián tiếp kế thừa chúng. Đọc trường `initialized` của từng interface là thao tác chủ động sử dụng trường `static` không phải hằng compile-time, vì vậy nó kích hoạt việc khởi tạo interface tương ứng.

Các trường `initialized` trong interface là `public static final`, nhưng không phải biến hằng compile-time vì giá trị của chúng đến từ lời gọi phương thức. Do đó, việc đọc chúng thực sự kích hoạt khởi tạo interface.

## 4. Thứ tự initializer bên trong một kiểu

Khi một lớp hoặc interface bắt đầu khởi tạo, các initializer của chính kiểu đó chạy theo thứ tự xuất hiện trong mã nguồn. Ví dụ:

~~~java
class Example {
    static int first = log("first");

    static {
        log("static block");
    }

    static int last = log("last");

    static int log(String message) {
        System.out.println(message);
        return 0;
    }
}
~~~

Khi `Example` được khởi tạo, thứ tự là:

~~~text
first
static block
last
~~~

Đối với một lớp có superclass và các superinterface khai báo `default method`, thứ tự bên ngoài là: superclass, các superinterface đủ điều kiện theo thứ tự quy định, rồi mới đến các initializer `static` của lớp. Bên trong từng kiểu, các initializer của chính kiểu đó vẫn theo thứ tự văn bản.

## 5. Khởi tạo interface trực tiếp khác với khởi tạo lớp

Một interface cũng có thể được khởi tạo bởi thao tác chủ động sử dụng chính interface đó, chẳng hạn gọi phương thức `static` của nó hoặc đọc một trường `static` không phải biến hằng compile-time.

Tuy nhiên, **khởi tạo một interface trực tiếp không tự động khởi tạo tất cả superinterface của nó**. Quy tắc khởi tạo các superinterface có `default method` được áp dụng khi khởi tạo một lớp. Không nên suy luận rằng mọi interface cha đều được khởi tạo mỗi khi một interface con được sử dụng.

Một trường `static final` có giá trị hằng compile-time, chẳng hạn `int LIMIT = 10`, có thể được trình biên dịch thay trực tiếp bằng giá trị hằng. Chỉ đọc trường như vậy không kích hoạt khởi tạo interface khai báo nó. Ngược lại, một trường được gán bằng lời gọi phương thức như `int value = compute()` không phải biến hằng compile-time, nên đọc trường sẽ kích hoạt khởi tạo interface.

## 6. Lỗi dễ gặp

- **Cho rằng tất cả interface đều được khởi tạo cùng lớp:** Java tự động xử lý superclass và các superinterface khai báo `default method`. Interface còn lại có thể vẫn chưa được khởi tạo.
- **Cho rằng phải gọi `default method` thì interface mới khởi tạo:** không đúng. Việc khởi tạo lớp triển khai đã khiến các superinterface đủ điều kiện được khởi tạo, dù phương thức mặc định chưa được gọi.
- **Nhầm khởi tạo lớp với khởi tạo đối tượng:** initializer `static` chạy khi lớp được khởi tạo; initializer instance và constructor chạy trong quá trình tạo từng đối tượng.
- **Quên thứ tự giữa superclass, interface và lớp:** initializer `static` của lớp con không chạy trước superclass và các superinterface cần khởi tạo.
- **Cho rằng interface cha luôn khởi tạo trước khi interface con được sử dụng:** khởi tạo trực tiếp một interface không tự động kéo theo toàn bộ superinterface. Hãy xác định thao tác chủ động sử dụng và phân biệt nó với quá trình khởi tạo một lớp.
- **Nhầm `static final` với hằng compile-time:** `final` không đủ để kết luận một trường là biến hằng. Giá trị phải là biểu thức hằng theo quy tắc Java; lời gọi phương thức không đáp ứng điều kiện đó.

## 7. Quy trình phân tích một tình huống

Khi cần xác định thứ tự in ra từ các initializer, hãy làm theo các bước sau:

1. Xác định thao tác nào kích hoạt khởi tạo lớp hoặc interface.
2. Nếu đó là lớp, xác định superclass và các superinterface trong quan hệ kế thừa đệ quy có tự khai báo `default method`.
3. Đánh dấu các kiểu đã được khởi tạo trước đó; chúng không chạy lại initializer.
4. Ghi thứ tự khởi tạo superclass, các superinterface đủ điều kiện, rồi các initializer `static` của lớp.
5. Trong mỗi kiểu, đọc initializer trường `static` và khối `static` theo thứ tự văn bản.
6. Sau khi hoàn thành khởi tạo lớp, mới xét quá trình khởi tạo instance và constructor nếu thao tác ban đầu là `new`.

Cách phân tích này giúp dự đoán chính xác thứ tự khởi tạo, tránh các lỗi phụ thuộc vào thứ tự initializer và hiểu vì sao một số interface được khởi tạo sớm hơn những interface khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-initialization-006">Ví dụ theo dõi thứ tự khởi tạo bằng dấu vết thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Ví dụ theo dõi thứ tự khởi tạo bằng dấu vết thực thi

Khi đọc một chương trình Java có kế thừa, nhiều khối khởi tạo và constructor, đừng đoán thứ tự chỉ từ nơi gọi `new`. Hãy theo dõi riêng **khởi tạo lớp (class initialization)** và **khởi tạo đối tượng (instance initialization)**, rồi ghi lại từng câu lệnh có tác dụng quan sát được, chẳng hạn `System.out.println`.

Bài này dùng một chương trình nhỏ có lớp cha `Parent` và lớp con `Child`. Mỗi khối khởi tạo hoặc field initializer in ra một dấu vết. Chạy chương trình sẽ cho thấy vì sao lần tạo đối tượng đầu tiên có cả dấu vết static, còn lần tạo thứ hai chỉ có dấu vết instance.

## 1. Các quy tắc cần dùng khi lần theo dấu vết

1. **Khởi tạo lớp cha trước lớp con.** Trước khi một lớp được khởi tạo, lớp cha trực tiếp của nó phải được khởi tạo trước.
2. **Static field initializer và static block chạy theo thứ tự văn bản trong từng lớp.** Chúng chạy trong quá trình khởi tạo lớp, thường được kích hoạt bởi lần sử dụng chủ động đầu tiên như tạo đối tượng bằng `new` hoặc gọi phương thức static. Việc nạp lớp (loading) không đồng nghĩa với việc các initializer đã chạy.
3. **Static initialization chạy một lần cho mỗi lớp được khởi tạo trong một class loader.** Tạo thêm đối tượng của cùng lớp không chạy lại static block hoặc static field initializer.
4. **Với mỗi đối tượng, phần khởi tạo instance của lớp cha diễn ra trước phần instance của lớp con.** Trong từng lớp, field initializer và instance initializer block chạy theo thứ tự xuất hiện trong mã nguồn.
5. **Constructor body chạy sau các initializer của lớp tương ứng.** Constructor lớp con gọi constructor lớp cha trước khi phần thân còn lại của constructor lớp con chạy. Nếu không viết lời gọi tường minh, Java chèn lời gọi `super()` khi hợp lệ.

Các quy tắc này đủ để giải bài bằng cách chia dấu vết thành hai nhóm: sự kiện static xảy ra khi lớp được khởi tạo, và sự kiện instance xảy ra mỗi lần một đối tượng được tạo.

## 2. Chương trình Java hoàn chỉnh

Lưu toàn bộ mã dưới đây vào tệp `InitTraceDemo.java`. Thứ tự các field và block được cố ý sắp xếp để ta có thể quan sát thứ tự văn bản.

```java
class Parent {
    static {
        System.out.println("1. Parent: static block");
    }

    private static final String STATIC_VALUE =
            log("2. Parent: static field initializer");

    private final String instanceValue =
            log("5. Parent: instance field initializer");

    {
        System.out.println("6. Parent: instance initializer block");
    }

    Parent() {
        System.out.println("7. Parent: constructor body");
    }

    private static String log(String message) {
        System.out.println(message);
        return message;
    }
}

class Child extends Parent {
    static {
        System.out.println("3. Child: static block");
    }

    private static final String STATIC_VALUE =
            log("4. Child: static field initializer");

    private final String instanceValue =
            log("8. Child: instance field initializer");

    {
        System.out.println("9. Child: instance initializer block");
    }

    Child() {
        System.out.println("10. Child: constructor body");
    }

    private static String log(String message) {
        System.out.println(message);
        return message;
    }
}

public class InitTraceDemo {
    public static void main(String[] args) {
        System.out.println("=== first object ===");
        new Child();

        System.out.println("=== second object ===");
        new Child();
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```text
javac InitTraceDemo.java
java InitTraceDemo
```

## 3. Kết quả chính xác

```text
=== first object ===
1. Parent: static block
2. Parent: static field initializer
3. Child: static block
4. Child: static field initializer
5. Parent: instance field initializer
6. Parent: instance initializer block
7. Parent: constructor body
8. Child: instance field initializer
9. Child: instance initializer block
10. Child: constructor body
=== second object ===
5. Parent: instance field initializer
6. Parent: instance initializer block
7. Parent: constructor body
8. Child: instance field initializer
9. Child: instance initializer block
10. Child: constructor body
```

## 4. Đọc dấu vết theo từng giai đoạn

| Dấu vết | Điều vừa xảy ra | Lý do |
|---|---|---|
| 1 | Static block của `Parent` | Lớp cha được khởi tạo trước lớp con. |
| 2 | Static field initializer của `Parent` | Nó nằm sau static block trong mã nguồn của `Parent`. |
| 3 | Static block của `Child` | Sau khi `Parent` được khởi tạo xong, `Child` mới tiếp tục khởi tạo. |
| 4 | Static field initializer của `Child` | Nó nằm sau static block trong mã nguồn của `Child`. |
| 5–7 | Field instance, block instance, rồi constructor của `Parent` | Phần khởi tạo instance của lớp cha hoàn tất trước phần constructor của lớp cha. |
| 8–10 | Field instance, block instance, rồi constructor của `Child` | Chỉ sau khi constructor lớp cha hoàn tất mới đến phần khởi tạo instance và constructor của lớp con. |

Các số trong thông báo là nhãn để dễ đối chiếu, không phải số thứ tự do JVM tự sinh. Dấu vết được in bởi các câu lệnh nằm trong chương trình.

### Vì sao lần tạo đối tượng thứ hai không in các dòng 1–4?

Các static initializer đã chạy trong lần khởi tạo lớp đầu tiên. Lệnh `new Child()` thứ hai vẫn khởi tạo một đối tượng mới, vì vậy các field initializer và instance initializer block của cả `Parent` lẫn `Child` tiếp tục chạy. Nhưng lớp đã được khởi tạo rồi, nên các static initializer không chạy lại.

### Vì sao constructor của lớp cha xuất hiện trước initializer của lớp con?

Constructor lớp con phải gọi constructor lớp cha. Trong quá trình đó, Java khởi tạo phần instance của lớp cha và thực thi constructor lớp cha trước. Khi constructor lớp cha trả về, Java tiếp tục các field initializer và instance initializer block của lớp con, rồi mới chạy phần thân constructor lớp con.

## 5. Quy trình tự giải bài trace

Khi gặp đề yêu cầu dự đoán output về thứ tự khởi tạo, hãy làm theo quy trình sau:

1. **Khoanh vùng lớp được sử dụng đầu tiên.** Xác định hành động nào kích hoạt khởi tạo lớp, ví dụ `new Child()` hoặc gọi một phương thức static.
2. **Liệt kê các lớp theo quan hệ kế thừa.** Ghi lớp cha trước, lớp con sau.
3. **Với từng lớp, quét static từ trên xuống.** Ghi mỗi static field initializer và static block đúng thứ tự xuất hiện.
4. **Theo dõi một đối tượng.** Bắt đầu từ phần khởi tạo instance của lớp cha; trong mỗi lớp, quét field initializer và instance block theo thứ tự văn bản, rồi ghi constructor body.
5. **Đánh dấu các dấu vết chỉ xảy ra một lần.** Static initializer thuộc về lần khởi tạo lớp; instance initializer và constructor thuộc về mỗi lần tạo đối tượng.
6. **Đối chiếu output theo từng dòng.** Không tự sắp xếp lại các dòng theo ý nghĩa của tên biến hay theo vị trí của `new`; thứ tự phải được suy ra từ quy tắc Java và mã nguồn.

Một bảng nháp có thể gồm ba cột: số dòng, câu lệnh phát sinh dấu vết và lý do nó chạy ở thời điểm đó. Cách này đặc biệt hữu ích khi có nhiều lớp cha, nhiều initializer hoặc nhiều lần tạo đối tượng.

## 6. Những nhầm lẫn thường gặp

- **Nghĩ static initializer chạy mỗi lần tạo object:** sai; chúng chạy khi lớp được khởi tạo, không chạy lại cho mỗi instance.
- **Cho rằng tất cả static của lớp con chạy trước static của lớp cha:** sai; lớp cha được khởi tạo trước lớp con.
- **Gộp field initializer và initializer block thành hai nhóm riêng:** sai; trong cùng một lớp, hai loại này đan xen theo đúng thứ tự khai báo trong mã nguồn.
- **Đưa constructor lớp con lên trước constructor lớp cha:** sai; lời gọi constructor lớp cha hoàn tất trước khi phần thân còn lại của constructor lớp con thực thi.
- **Đồng nhất nạp lớp với khởi tạo lớp:** hai bước khác nhau. Khi dự đoán output, cần xác định thời điểm khởi tạo lớp thực sự được kích hoạt.
- **Quên rằng mỗi lần `new` tạo trạng thái instance mới:** các field instance được khởi tạo lại cho từng đối tượng, kể cả khi static state vẫn giữ nguyên.
- **Đọc số nhãn như thể đó là output tự động:** các số trong ví dụ được viết trong chuỗi thông báo. Nếu đổi mã nguồn, phải dự đoán theo câu lệnh thực tế.

## 7. Mở rộng khi dấu vết phức tạp hơn

Nếu constructor dùng `this(...)` để gọi một constructor khác trong cùng lớp, hãy theo chuỗi lời gọi cho đến constructor thực hiện lời gọi `super(...)`. Phần khởi tạo instance của đối tượng chỉ chạy một lần trong chuỗi constructor đó; không cộng thêm một lượt field initializer cho mỗi constructor được gọi qua `this(...)`.

Nếu static initializer gọi phương thức khác hoặc tạo một đối tượng khác, hãy lần theo lời gọi lồng nhau đúng tại vị trí đó. Không chuyển dấu vết của lời gọi lồng nhau xuống cuối bảng: nó xuất hiện ngay khi câu lệnh gọi được thực thi. Cũng cần lưu ý rằng một lỗi trong quá trình khởi tạo lớp có thể làm lần khởi tạo thất bại; các lần sử dụng sau có thể nhận lỗi khởi tạo lớp thay vì chạy lại initializer bình thường.

Để tự kiểm tra, hãy thay đổi vị trí một static block hoặc instance block, thêm một dòng `new Child()`, rồi dự đoán output trước khi chạy lại. Nếu output khác dự đoán, đối chiếu từng dòng với thứ tự khai báo và chuỗi gọi constructor thay vì chỉ xem kết quả cuối cùng.

</details>

- [Quay lại đầu trang](#back-to-top)
