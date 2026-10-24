<a id="back-to-top"></a>

# Thực hành

## Menu
- [Xây dựng trình in cấu trúc một Class theo thứ tự ổn định](#reflection-practice-001)
- [Viết công cụ gọi phương thức từ một hợp đồng đã xác thực](#reflection-practice-002)
- [Tạo factory từ constructor có kiểu và thông báo lỗi rõ ràng](#reflection-practice-003)
- [Đọc object graph có kiểm soát vòng tham chiếu](#reflection-practice-004)
- [Xây dựng bộ ánh xạ dữ liệu nhỏ qua accessor hoặc record component](#reflection-practice-005)
- [Xây dựng bộ kiểm tra annotation runtime đơn giản](#reflection-practice-006)
- [Phân giải generic của một lớp con qua nhiều tầng kế thừa](#reflection-practice-007)
- [Tạo proxy ghi thời gian xử lý và giữ nguyên ngoại lệ mã đích](#reflection-practice-008)
- [So sánh gọi trực tiếp, Method.invoke và MethodHandle](#reflection-practice-009)
- [Hoàn thiện ví dụ module hóa cùng bộ kiểm thử quyền truy cập](#reflection-practice-010)

## <a id="reflection-practice-001">Xây dựng trình in cấu trúc một Class theo thứ tự ổn định</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: Xây dựng trình in cấu trúc một Class theo thứ tự ổn định bằng Java Reflection

## 1. Bài toán và tiêu chí hoàn thành

Viết một chương trình Java 17+ nhận một đối tượng `Class<?>` và xuất cấu trúc lớp theo thứ tự **xác định và lặp lại được**, đủ để đọc, so sánh hoặc lưu thành tài liệu cấu trúc. Chương trình sẽ in:

1. Tên đầy đủ và modifiers của class.
2. Superclass trực tiếp (nếu có).
3. Danh sách interface được khai báo trực tiếp, sắp xếp theo tên.
4. Fields được khai báo trực tiếp: modifier, kiểu và tên; sắp xếp theo khóa rõ ràng.
5. Constructors được khai báo trong class: modifiers và danh sách kiểu tham số; sắp xếp theo signature.
6. Methods được khai báo trực tiếp: modifiers, kiểu trả về, tên, danh sách kiểu tham số; sắp xếp theo signature và các khóa phụ khi cần.

**Điểm cốt lõi:** Theo Java Reflection API, **thứ tự phần tử trong các mảng trả về từ `getDeclaredFields()`, `getDeclaredConstructors()`, `getDeclaredMethods()` không được bảo đảm**. Vì vậy không được chỉ duyệt rồi in nguyên thứ tự mảng mà gọi đó là “ổn định”. Cần **sắp xếp dữ liệu trước khi in** và quy định khóa sắp xếp một cách công khai.

Kết quả ổn định ở đây được hiểu là: với cùng một cấu trúc lớp, cùng quy tắc hiển thị và cùng thông tin Reflection nhìn thấy, chương trình tạo chuỗi đầu ra giống nhau, bất kể thứ tự các phần tử trong mảng Reflection. Đây không phải cam kết rằng bytecode do mọi compiler, phiên bản JDK hoặc cơ chế instrument khác nhau sẽ luôn cho **cùng cấu trúc metadata**; các phần tử synthetic/bridge có thể thay đổi theo quá trình biên dịch.

## 2. Chọn phạm vi: “declared” khác “inherited”

Có hai nhóm API Reflection thường bị trộn lẫn:

| Thông tin | API lựa chọn | Phạm vi |
|---|---|---|
| Fields trong đúng class hiện tại | `getDeclaredFields()` | Gồm public, protected, package-private, private; không tự lấy field kế thừa |
| Fields public có thể thấy | `getFields()` | Có thể bao gồm field public kế thừa |
| Methods trong đúng class hiện tại | `getDeclaredMethods()` | Không tự lấy method kế thừa; có thể có bridge/synthetic methods |
| Methods public có thể thấy | `getMethods()` | Gồm method public kế thừa, ví dụ từ superclass/interfaces |
| Constructors của class | `getDeclaredConstructors()` | Gồm public và non-public constructors được khai báo |
| Constructors public | `getConstructors()` | Chỉ public constructors; constructor không được kế thừa |
| Superclass trực tiếp | `getSuperclass()` | Có thể là `null` với `Object`, interface, primitive, `void` |
| Interfaces trực tiếp | `getInterfaces()` | Các interface class khai báo trực tiếp; không tự trộn vào các interface kế thừa gián tiếp |

**Quyết định thiết kế của bài:** Dùng nhóm `getDeclared...` để in một lát cắt cấu trúc **do class sở hữu**, rồi in riêng superclass và interfaces. Cách này tránh một method được thừa kế xuất hiện như thể nó được khai báo trực tiếp bởi class hiện tại. Các công cụ thực tế có thể thêm chế độ “public API including inherited”, nhưng phải xác định phạm vi và quy tắc xử lý override riêng; **không trộn hai chế độ trong một bản in**.

Trong chương trình mẫu, `Sample` kế thừa `Base` và thực thi `Runnable, AutoCloseable`. Các method `run()` và `close()` được khai báo trực tiếp trong `Sample`, nên xuất hiện ở phần `METHODS`. Những method kế thừa từ `Object` như `wait`, `hashCode` không được thêm vào danh sách `getDeclaredMethods()`.

## 3. Thiết kế khóa sắp xếp theo từng nhóm

Một khóa phải phản ánh đủ sự khác biệt cần thiết và được dựng từ dữ liệu có thứ tự ổn định:

| Nhóm | Khóa chính và phụ trong bài |
|---|---|
| Interfaces | Tên kiểu hiển thị sau chuẩn hóa qua `typeName`, thứ tự từ điển |
| Fields | Tên field → kiểu field → modifiers |
| Constructors | Danh sách kiểu tham số → modifiers |
| Methods | Tên method → danh sách kiểu tham số → kiểu trả về → modifiers → `isBridge` → `isSynthetic` |

Ví dụ methods có overload:

~~~text
describe(int)
describe(java.lang.String)
~~~

Nếu chỉ sắp xếp bằng tên method, hai phần tử có tên `describe` vẫn có thể giữ thứ tự tương đối từ mảng Reflection ban đầu; chúng có thể bị đổi chỗ. Vì vậy **overload cần thêm khóa theo danh sách kiểu tham số**. Kiểu trả về cũng nên có khóa riêng: JVM có thể chứa bridge methods hoặc các method có bytecode signature khác dù có cùng tên và danh sách tham số.

**Không dùng `Method.toString()` hoặc `Class.toString()` làm khóa sắp xếp duy nhất** khi thiết kế công cụ: chuỗi hiển thị không phải một giao ước “sort key” do API cam kết; tốt hơn là chọn chính các thuộc tính bạn muốn so sánh. Bài này tạo chuỗi tham số từ `getParameterTypes()` theo thứ tự tham số đã khai báo, rồi dùng comparator explicit.

**Thứ tự trong một lớp metadata không phải “source order”.** Reflection không cam kết fields/methods giữ thứ tự viết trong file Java, nên printer không hứa tái dựng nguyên văn mã nguồn. Comparator trong bài cố ý tạo thứ tự *mới* và giải thích được.

### Chuẩn hóa tên kiểu

`Class.getSimpleName()` quá ngắn khi có nhiều kiểu `User` khác package. `Class.getName()` dùng ký pháp nhị phân với `$` cho nested types, ví dụ `StableClassPrinter$Sample`. `Class.getCanonicalName()` dễ đọc hơn với nested class thông thường, ví dụ `StableClassPrinter.Sample`, nhưng có thể trả `null` cho local/anonymous classes và một số kiểu khác.

Hàm dùng trong bài:

~~~java
private static String typeName(Class<?> type) {
    String canonical = type.getCanonicalName();
    return canonical != null ? canonical : type.getTypeName();
}
~~~

Đây là **mảnh trích nằm trong class `StableClassPrinter`** ở mục 4. `getTypeName()` cung cấp fallback khi không có canonical name, ví dụ anonymous classes. Các trường hợp kiểu mảng và primitive cũng được Java API hiển thị có nghĩa, như `int[]` và `java.lang.String[]`. Chọn một quy tắc duy nhất cho **cả sort keys và text output** để tránh một cách nhìn khi sắp xếp, một cách nhìn khác khi in.

## 4. Chương trình Java 17+ hoàn chỉnh, có thể chạy ngay

Lưu đúng nội dung sau vào tệp **`StableClassPrinter.java`**. Chương trình tạo class mẫu `Sample`, sắp xếp mọi danh sách và in kết quả. Mã không cần dependency bên ngoài JDK.

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

public class StableClassPrinter {
    public static class Base {}

    public static final class Sample extends Base
            implements Runnable, AutoCloseable {
        public String name;
        private final int id;

        public Sample(int id) {
            this.id = id;
        }

        public Sample(String name, int id) {
            this.name = name;
            this.id = id;
        }

        public String describe(String text) {
            return text;
        }

        public String describe(int number) {
            return Integer.toString(number);
        }

        @Override
        public void run() {}

        @Override
        public void close() {}
    }

    private static String typeName(Class<?> type) {
        String canonical = type.getCanonicalName();
        return canonical != null ? canonical : type.getTypeName();
    }

    private static String parameterTypes(Class<?>[] types) {
        return Arrays.stream(types)
                .map(StableClassPrinter::typeName)
                .collect(Collectors.joining(", "));
    }

    private static String modifiers(int value) {
        String text = Modifier.toString(value);
        return text.isEmpty() ? "" : text + " ";
    }

    public static String describe(Class<?> type) {
        StringBuilder result = new StringBuilder();
        result.append("CLASS ")
                .append(modifiers(type.getModifiers()))
                .append(typeName(type)).append('\n');

        Class<?> parent = type.getSuperclass();
        result.append("SUPER ")
                .append(parent == null ? "<none>" : typeName(parent))
                .append('\n');

        String interfaces = Arrays.stream(type.getInterfaces())
                .map(StableClassPrinter::typeName)
                .sorted()
                .collect(Collectors.joining(", "));
        result.append("INTERFACES ")
                .append(interfaces.isEmpty() ? "<none>" : interfaces)
                .append('\n');

        Field[] fields = type.getDeclaredFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName)
                .thenComparing(f -> typeName(f.getType()))
                .thenComparingInt(Field::getModifiers));
        result.append("FIELDS\n");
        for (Field field : fields) {
            result.append("  ")
                    .append(modifiers(field.getModifiers()))
                    .append(typeName(field.getType())).append(' ')
                    .append(field.getName()).append('\n');
        }

        Constructor<?>[] constructors = type.getDeclaredConstructors();
        Arrays.sort(constructors,
                Comparator.comparing((Constructor<?> c) ->
                        parameterTypes(c.getParameterTypes()))
                        .thenComparingInt(Constructor::getModifiers));
        result.append("CONSTRUCTORS\n");
        for (Constructor<?> constructor : constructors) {
            result.append("  ")
                    .append(modifiers(constructor.getModifiers()))
                    .append(typeName(type)).append('(')
                    .append(parameterTypes(constructor.getParameterTypes()))
                    .append(")\n");
        }

        Method[] methods = type.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName)
                .thenComparing(m -> parameterTypes(m.getParameterTypes()))
                .thenComparing(m -> typeName(m.getReturnType()))
                .thenComparingInt(Method::getModifiers)
                .thenComparing(Method::isBridge)
                .thenComparing(Method::isSynthetic));
        result.append("METHODS\n");
        for (Method method : methods) {
            result.append("  ")
                    .append(modifiers(method.getModifiers()))
                    .append(typeName(method.getReturnType())).append(' ')
                    .append(method.getName()).append('(')
                    .append(parameterTypes(method.getParameterTypes()))
                    .append(')');
            if (method.isBridge()) {
                result.append(" [bridge]");
            }
            if (method.isSynthetic()) {
                result.append(" [synthetic]");
            }
            result.append('\n');
        }
        return result.toString();
    }

    public static void main(String[] args) {
        System.out.print(describe(Sample.class));
    }
}
~~~

Trong thư mục chứa tệp, chạy:

~~~bash
javac --release 17 -encoding UTF-8 StableClassPrinter.java
java StableClassPrinter
~~~

**Stdout chính xác được kiểm chứng bằng biên dịch và chạy chương trình:**

~~~text
CLASS public static final StableClassPrinter.Sample
SUPER StableClassPrinter.Base
INTERFACES java.lang.AutoCloseable, java.lang.Runnable
FIELDS
  private final int id
  public java.lang.String name
CONSTRUCTORS
  public StableClassPrinter.Sample(int)
  public StableClassPrinter.Sample(java.lang.String, int)
METHODS
  public void close()
  public java.lang.String describe(int)
  public java.lang.String describe(java.lang.String)
  public void run()
~~~

Quan sát:

- `AutoCloseable` đứng trước `Runnable` vì tên interface đã được sort; thứ tự viết sau `implements` không được dùng làm thứ tự hiển thị.
- `id` đứng trước `name` vì field sort theo tên; vị trí field trong source không quyết định output.
- Constructor nhận `int` đứng trước constructor nhận `String, int` vì sort theo chuỗi kiểu tham số.
- Method `describe(int)` đứng trước `describe(String)` vì key tham số `int` đứng trước `java.lang.String`.
- `close()`, `describe(...)`, `run()` xuất hiện theo thứ tự tên method. Chương trình không in methods kế thừa từ `Object`.
- `Modifier.toString(...)` chuyển bitmask của modifier thành chuỗi theo quy tắc Java, ví dụ `private final`, `public static final`. Khi không có modifier, hàm `modifiers` trả chuỗi rỗng để tránh thêm một dấu cách thừa.

Ở phần ví dụ chính, mọi phần tử có khóa đủ để tạo thứ tự xác định. Với những kiểu phức tạp hơn có thể có metadata trùng khóa, xem mục 7 về việc hoàn thiện một canonical fingerprint và xử lý metadata đặc biệt.

## 5. Phân tích từng phần của printer

### 5.1. Class header, superclass và interfaces

~~~java
result.append("CLASS ")
        .append(modifiers(type.getModifiers()))
        .append(typeName(type)).append('\n');
Class<?> parent = type.getSuperclass();
~~~

`getModifiers()` trả bitmask; `Modifier.toString` định dạng modifiers thông dụng. `getSuperclass()` trả superclass trực tiếp hoặc `null` trong những trường hợp không có superclass tương ứng. Thay vì giả định mọi loại `Class<?>` đều mô tả một class bình thường, mã hiển thị `<none>` khi không có superclass.

`getInterfaces()` trả một mảng; Java không cam kết đó là thứ tự tối ưu để tạo output canonical. Vì vậy dùng:

~~~java
Arrays.stream(type.getInterfaces())
        .map(StableClassPrinter::typeName)
        .sorted()
        .collect(Collectors.joining(", "));
~~~

Đây là **mảnh trích** của chương trình hoàn chỉnh. `sorted()` sắp xếp chuỗi tên theo natural ordering (thứ tự so sánh `String`), độc lập locale mặc định của hệ điều hành. Công cụ có thể chọn comparator riêng cho yêu cầu xuất dữ liệu, nhưng cần thống nhất và tài liệu hóa.

### 5.2. Fields

~~~java
Field[] fields = type.getDeclaredFields();
Arrays.sort(fields, Comparator.comparing(Field::getName)
        .thenComparing(f -> typeName(f.getType()))
        .thenComparingInt(Field::getModifiers));
~~~

Mảnh mã được trích từ method `describe`. Comparator đảm bảo field khác tên luôn theo thứ tự tên. Các khóa phụ làm quy tắc rõ hơn nếu gặp metadata có tên giống nhau nhưng phân biệt bởi kiểu hoặc modifiers. Khi in, gọi `field.getType()` trả `Class<?>` cho kiểu đã khai báo, **không truy cập giá trị field**. Không cần `setAccessible(true)` vì chỉ đọc metadata.

`getDeclaredFields()` có thể chứa synthetic fields do compiler tạo. Nếu mục tiêu là bản in **đầy đủ ở mức Reflection**, nên giữ và đánh dấu synthetic để biết chúng tồn tại. Nếu mục tiêu là mô tả API do người dùng viết, có thể bỏ qua `field.isSynthetic()`, nhưng phải nói rõ đó là **projection đã lọc**, không còn là toàn bộ declared fields. Mã mẫu giữ mọi field và không tạo synthetic field đặc biệt trong ví dụ.

### 5.3. Constructors

Constructor không có kiểu trả về và không được kế thừa. `Constructor.getParameterTypes()` trả danh sách kiểu tham số theo đúng thứ tự khai báo, còn mảng constructors trả về phải được sort:

~~~java
Arrays.sort(constructors,
        Comparator.comparing((Constructor<?> c) ->
                parameterTypes(c.getParameterTypes()))
                .thenComparingInt(Constructor::getModifiers));
~~~

Mảnh này trích từ chương trình. `getDeclaredConstructors()` có thể bao gồm constructor private/protected/package-private. **Đọc metadata** không có nghĩa là **được phép gọi constructor**; nếu gọi bằng `newInstance(...)`, phải xử lý accessibility và exception do code đích ném.

Với non-static inner class, compiler có thể thêm tham số receiver/enclosing instance vào bytecode constructor; với enum hoặc một số loại do compiler sinh, signature Reflection cũng có khác biệt so với chữ ký hiển thị trong source. Printer này chủ ý hiển thị những kiểu tham số Reflection thực sự báo, không cố tái tạo Java source constructor verbatim.

### 5.4. Methods và overloads

`Method.getName()` một mình chưa đủ làm khóa sắp xếp. Comparator dùng:

~~~java
Comparator.comparing(Method::getName)
        .thenComparing(m -> parameterTypes(m.getParameterTypes()))
        .thenComparing(m -> typeName(m.getReturnType()))
        .thenComparingInt(Method::getModifiers)
        .thenComparing(Method::isBridge)
        .thenComparing(Method::isSynthetic)
~~~

Đây là **mảnh expression** nằm trong `Arrays.sort` ở ví dụ. Danh sách tham số tạo signature cho các overloads. Kiểu trả về tách biệt các biến thể mà compiler có thể sinh ra (ví dụ bridge methods). `isBridge()` và `isSynthetic()` còn phục vụ gắn nhãn trong output để công cụ không âm thầm bỏ qua method compiler-generated.

Các flag này **không có nghĩa cứ là bridge/synthetic thì phải xóa khỏi bản in**. Chọn giữ hay lọc phụ thuộc mục đích. Nếu giữ chúng, in thêm nhãn rõ; nếu lọc, làm trước khi sort và mô tả lý do lọc. Với tài liệu API do người dùng viết, nhiều công cụ lọc synthetic/bridge; với bài phân tích bytecode hoặc chẩn đoán runtime dispatch, giữ chúng lại có thể quan trọng.

## 6. Kiểm tra tính ổn định bằng test, không chỉ nhìn kết quả một lần

Một bản in “trông như đã sắp” không phải bằng chứng đủ. Hãy kiểm tra **nhiều lần, cùng input** và kiểm tra khóa sắp xếp.

### Test 1: Output của hàm là chuỗi không có side effects

Method `describe(Class<?>)` trả về `String`. Vì vậy test có thể so sánh output trực tiếp mà không cần bắt `System.out`:

~~~java
String first = StableClassPrinter.describe(
        StableClassPrinter.Sample.class);
String second = StableClassPrinter.describe(
        StableClassPrinter.Sample.class);

if (!first.equals(second)) {
    throw new AssertionError("Unstable class description");
}
~~~

Đây là **đoạn test minh họa** cho cùng project, không phải tệp Java độc lập. Gọi hai lần mà so sánh chuỗi giúp phát hiện sai biệt nội bộ trong một tiến trình; tuy nhiên nếu JVM thường trả mảng cùng thứ tự, test vẫn có thể pass dù bạn quên sort. Vì vậy phải đọc comparator và kiểm thử bằng dữ liệu có nhiều method overload / nhiều fields như `Sample`.

### Test 2: So khớp snapshot/golden output

Chạy chương trình, lưu output vào `expected.txt`, sau đó so sánh byte-for-byte hoặc chuỗi Unicode với kết quả hiện tại. Tệp snapshot phải được cập nhật **có chủ đích** khi contract hiển thị đổi. Để có file chuẩn UTF-8 trong shell hỗ trợ chuyển hướng:

~~~bash
java StableClassPrinter > actual.txt
~~~

Sau đó dùng công cụ diff/test của project để so `actual.txt` với `expected.txt`. Lưu ý encoding và kiểu xuống dòng (LF/CRLF) khác nhau giữa hệ điều hành; nếu snapshot cần dùng đa nền tảng, có thể chuẩn hóa newline trước khi so, **nhưng không thay đổi quy tắc sort**.

### Test 3: Thêm dữ liệu để thử comparator

- Đổi vị trí khai báo `name` và `id` trong mã nguồn, rồi biên dịch lại. Thứ tự fields trong stdout phải vẫn dựa trên tên.
- Đổi vị trí hai phương thức `describe` trong source. Thứ tự methods vẫn theo tên + kiểu tham số.
- Đổi thứ tự `implements Runnable, AutoCloseable` thành `implements AutoCloseable, Runnable`. Thứ tự output vẫn giữ `java.lang.AutoCloseable` trước `java.lang.Runnable`.
- Thêm overload `describe(long)`. Comparator phải đưa nó vào đúng vị trí từ điển theo tên tham số kiểu, không phụ thuộc thứ tự viết code.

Không khẳng định mọi thay đổi sẽ có bytecode hoàn toàn giống nhau: kiểm thử ở đây xác nhận **quy tắc thứ tự hiển thị**, không xác nhận compiler phát sinh cùng một thứ tự metadata thô.

## 7. Những giới hạn cần nêu trước khi dùng trong hệ thống thật

### 7.1. Reflection metadata không phải cú pháp source

Printer không tái tạo generics/annotations/`throws` chính xác như source Java. `Field.getType()` và `Method.getReturnType()` cho kiểu đã erasure ở những tình huống generic; muốn giữ thông tin generic được lưu trong classfile, xem `Field.getGenericType()`, `Method.getGenericReturnType()` và `Executable.getGenericParameterTypes()`. Những API này trả `Type`, không phải luôn `Class<?>`, và cần formatter riêng cho `ParameterizedType`, `TypeVariable`, `GenericArrayType`, `WildcardType`. Cần quy định sắp xếp và serialization của những biểu thức kiểu đó nếu mục tiêu là API signature tương đối đầy đủ.

Chương trình không hiển thị annotation của class/member, exception types (`throws`), generic type parameters, record components, nested classes, enum constants như một mục riêng hay inherited public API. Để mở rộng, thêm **mỗi mục riêng** với chiến lược sort phù hợp; không nên hứa output ở trên là toàn bộ mô hình Java class file.

### 7.2. Không phải mọi `Class<?>` đều là normal class

Với `int.class`, `void.class`, array type, interface, annotation type, enum, record, local/anonymous class, hidden class hoặc dynamic proxy class, metadata có thể khác giả định “class thường”. Ví dụ `getCanonicalName()` có thể trả `null` và `getDeclaredConstructors()` có thể trả mảng rỗng.

Mã `describe(Class<?>)` trong bài có fallback cho tên kiểu và superclass, nên tránh một số NPE; nhưng định dạng đầu ra hiện viết nhãn `CLASS` chung, **không phân biệt đầy đủ category** (interface/enum/record/annotation). Nếu làm công cụ sản phẩm, hãy thêm `isInterface()`, `isAnnotation()`, `isEnum()`, `isRecord()`, `isArray()`, `isPrimitive()`, `isHidden()` và định dạng rõ từng loại.

### 7.3. Một số metadata có thể do compiler/instrumentation sinh

Bridge/synthetic methods, các field trợ giúp, hidden classes và bytecode instrumentation có thể thay đổi metadata theo compiler hoặc cơ chế build. Vì vậy output ổn định là hàm của **class metadata thực tế + quy tắc sort**, không phải một bản snapshot bảo đảm giống hệt giữa mọi JDK/build khác nhau.

Một vấn đề nâng cao: comparator tốt cần xử lý những trường hợp có **khóa hoàn toàn bằng nhau nhưng phần tử vẫn khác nhau theo khía cạnh bạn muốn in**, ví dụ khi bổ sung annotations, generic signatures, `throws` hoặc thông tin synthetic. Khi đó thêm tie-breaker theo chính các trường sẽ in (được chuẩn hóa). Nếu hai entries giống hệt nhau trên toàn bộ nội dung được xuất, đổi chỗ chúng cũng không làm thay đổi chuỗi đầu ra. Không nên dùng `hashCode()` của reflection object làm tie-breaker vì không phải khóa canonical có ý nghĩa ổn định.

### 7.4. Quyền truy cập và bảo mật

Các method `getDeclared...` trong ví dụ chỉ **liệt kê metadata**, không đọc giá trị private fields và không gọi private methods. Printer vì thế **không dùng `setAccessible(true)`**. Tuy nhiên với security policy hoặc môi trường đặc biệt, việc truy vấn metadata vẫn có thể bị hạn chế. Nếu mở rộng thành công cụ “in giá trị field của đối tượng”, đó là một nhiệm vụ khác, cần chính sách quyền riêng tư, redaction, access control và kiểm soát vòng lặp tham chiếu. **Không log giá trị mật khẩu, token hoặc dữ liệu nhạy cảm chỉ vì Reflection nhìn thấy field.**

### 7.5. Nhiều cách “ổn định” khác nhau

Có thể sort theo:

- Tên field/method + signature như bài này: dễ đọc, ổn định và phù hợp tài liệu.
- Chữ ký JVM descriptor: phù hợp phân tích bytecode nhưng ít thân thiện.
- Phân nhóm theo access modifier trước, rồi tên/signature: phù hợp tài liệu API cho người dùng.
- Tên full canonical có normalization versioned: cần khi dùng làm hash hay dữ liệu trao đổi giữa toolchains.

Điều quan trọng không phải chỉ có một comparator “đúng”, mà là **công bố contract, sắp xếp mọi collection không đảm bảo thứ tự, kiểm thử với nhiều overload và giữ contract nhất quán**.

## 8. Những lỗi phổ biến

| Sai lầm | Vì sao không ổn | Cách khắc phục |
|---|---|---|
| In thẳng mảng `getDeclaredMethods()` | Javadoc không cam kết thứ tự | `Arrays.sort` hoặc `stream.sorted` với comparator xác định |
| Chỉ sort `Method::getName` | Không tách overload | Thêm parameter type signature và return type |
| Dùng `getMethods()` nhưng ghi nhãn “declared methods” | Bao gồm inherited public methods | Chọn `getDeclaredMethods()` hoặc đổi scope label |
| In danh sách `getInterfaces()` theo thứ tự có sẵn | Mục tiêu canonical cần định nghĩa sort | Map sang tên kiểu rồi sort |
| Gọi `getSimpleName()` cho tất cả | Có thể trùng tên giữa package, rỗng với anonymous types | Ưu tiên canonical name với fallback |
| Coi Reflection là source parser | Không có đủ thông tin về thứ tự source, comment, một số generic details | Dùng AST/compiler model nếu cần thông tin nguồn |
| Vứt hết `synthetic`/`bridge` không giải thích | Bỏ một phần cấu trúc runtime | Có chế độ lọc được công bố; mặc định giữ/đánh dấu nếu làm diagnostics |
| Dùng `setAccessible(true)` để in chữ ký | Không cần, tăng rủi ro và có thể bị JPMS chặn | Chỉ đọc metadata qua Reflection |
| Đưa object identity/hash vào đầu ra | Không phải đặc điểm cấu trúc ổn định giữa các lần chạy | In tên kiểu, signature, flags |
| So stdout bằng mắt một lần | Không phát hiện thiếu tie-breakers | Test snapshot/so sánh với overloads và source reorder |

## 9. Bài tập mở rộng

1. Thêm một field `private boolean enabled` và một method `protected int score(double multiplier)`. Xác định chính xác vị trí của chúng trong output trước khi chạy.
2. Viết method `describe(Class<?> type, boolean includeInherited)`. Khi `includeInherited=true`, dùng nhóm API phù hợp và định nghĩa cách xử lý override. Đừng gọi đó là cùng phạm vi với `getDeclaredMethods()`.
3. Thêm interface `java.io.Serializable` vào `Sample`. Xác minh danh sách interfaces được sort dù thứ tự trong `implements` đảo chiều.
4. Tạo generic base class để compiler phát sinh bridge method. Quan sát `isBridge()`/`isSynthetic()` và sửa cách in nếu muốn chế độ “source-like view”.
5. Thêm cột `throws` bằng `getExceptionTypes()`. Chọn thứ tự danh sách exception sao cho phù hợp mục tiêu “signature view”, không tự ý đổi ý nghĩa thứ tự tham số của method.
6. Thêm section `RECORD_COMPONENTS` dùng `Class.getRecordComponents()` cho `record`; quyết định rõ output là **thứ tự component khai báo** hay **thứ tự canonical do công cụ tự sắp**.
7. Tạo unit test so `describe(Sample.class)` với golden text và lặp 100 lần; rồi hoán đổi thứ tự khai báo methods trong source để chứng minh output không dựa vào thứ tự Reflection trả về.
8. Tạo một wrapper CLI nhận tên class qua `args[0]`, gọi `Class.forName(...)` và in cấu trúc; bổ sung xử lý `ClassNotFoundException` cùng chính sách chỉ nhận những tên class tin cậy. Cẩn trọng vì nạp và khởi tạo lớp có thể gây side effects.

**Ghi nhớ:** Reflection cung cấp metadata, **không cung cấp thứ tự canonical mặc định** cho fields, constructors và methods. Để in cấu trúc lớp ổn định, hãy quyết định rõ phạm vi (`declared` hay `inherited`), định nghĩa một comparator có đủ khóa cho từng collection, chuẩn hóa tên kiểu, không dựa vào object identity và kiểm thử với overloads/bridge/synthetic. Khi cần source-level representation thay vì runtime structure, hãy cân nhắc compiler model hoặc AST thay vì cố suy ngược source từ Reflection.

### Tài liệu tham khảo (Java 17)

- [`Class.getDeclaredFields`, `getDeclaredMethods` và `getDeclaredConstructors`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [`Method` và metadata bridge/synthetic](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html)
- [`Constructor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html)
- [`Field`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html)
- [`Modifier.toString`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Modifier.html#toString(int))
- [`Comparator`, `thenComparing`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-practice-002">Viết công cụ gọi phương thức từ một hợp đồng đã xác thực</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: Viết công cụ gọi phương thức từ một hợp đồng đã xác thực

## 1. Bài toán và kết quả cần đạt

Một công cụ dùng Java Reflection có thể nhận yêu cầu dạng `(tên thao tác, danh sách giá trị đầu vào)` rồi chuyển chúng thành lời gọi phương thức. Ví dụ:

```text
sum   ["7", "5"] -> 12
label ["go", "3"] -> "gogogo"
```

Tuy nhiên, công cụ **không được** suy ra class, tên method hay chữ ký tùy ý từ input rồi gọi ngay. Nó cần một **hợp đồng đã được khai báo và xác minh trước**: thao tác nào được phép, phương thức Java nào xử lý, chính xác kiểu tham số/kiểu trả về, quy tắc parse/validation, đối tượng đích và chính sách lỗi.

Sau bài này, bạn có thể:

1. Thiết kế **registry/allowlist** ánh xạ tên lệnh công khai thành một method được tin cậy.
2. Xác minh hợp đồng lúc khởi tạo bằng `Class.getMethod(name, parameterTypes)`, kiểu trả về và class khai báo.
3. Ánh xạ input dạng chuỗi thành `Object[]` theo **parser tường minh cho từng tham số**.
4. Kiểm tra số lượng, phạm vi dữ liệu và điều kiện nghiệp vụ; phân loại lỗi trước/sau lời gọi.
5. Gọi target bằng `Method.invoke(target, args)` và mở `InvocationTargetException` đúng cách.
6. Kiểm thử đường đi thành công, input không hợp lệ, thao tác bị cấm, thiếu đối số và lỗi từ target.

Bài sử dụng **Java 17+**, chỉ cần JDK. Hợp đồng “đã xác thực” ở đây nghĩa là **metadata và quy tắc gọi đã được chương trình kiểm chứng ở quá trình khởi tạo**. Việc này không tự thay thế xác thực danh tính hoặc phân quyền người dùng nếu công cụ được đưa ra HTTP/RPC.

## 2. Thiết kế một hợp đồng có thể kiểm tra

Một hợp đồng gọi phương thức nên mô tả tối thiểu:

| Thành phần | Chức năng | Ví dụ |
| --- | --- | --- |
| **Alias** | Tên thao tác công khai được chấp nhận | `sum` |
| **Target class** | Lớp dịch vụ chứa hành vi cho phép | `Service.class` |
| **Java method name** | Phương thức triển khai cố định | `sum` |
| **Parameter types** | Danh sách kiểu tham số **đúng thứ tự** | `int.class, int.class` |
| **Return type** | Kiểu trả về cần có | `int.class` |
| **Argument parsers** | Luật chuyển từng input thành đối tượng Java | `Integer.parseInt` + kiểm tra miền |
| **Allowed target** | Instance được tạo/quản lý bởi ứng dụng | `new Service()` |
| **Error policy** | Phân biệt lỗi request, contract và nghiệp vụ | `IllegalArgumentException` / exception từ target |

Luồng thực hiện:

```text
Đăng ký hợp đồng TIN CẬY tại startup
  -> resolve Method theo tên + kiểu tham số xác định trước
  -> xác minh class, quyền public, kiểu trả về và số parser
  -> lưu Method vào registry bất biến

Nhận request: alias + raw arguments
  -> tìm trong allowlist
  -> kiểm tra số lượng đối số
  -> dùng parser từng vị trí; kiểm tra cú pháp, miền giá trị
  -> tạo Object[] đã chuyển đổi
  -> Method.invoke(target, converted)
  -> trả kết quả hoặc phân loại exception
```

Có hai biên giới tin cậy phải tách rời:

- **Định nghĩa hợp đồng** do mã ứng dụng hoặc cấu hình quản trị đã kiểm tra quyết định. Nó quy định những gì hệ thống *có khả năng* thực thi.
- **Yêu cầu của caller** chỉ được chọn alias trong danh sách và cung cấp dữ liệu. Caller không được tự thêm method, chọn class tùy ý, đặt `setAccessible(true)` hay định nghĩa parser bằng code truyền vào từ request.

Nếu caller đến từ mạng hoặc có nhiều vai trò, cần **kiểm tra quyền của caller đối với alias** trước khi invoke; allowlist không tự xác định ai được dùng thao tác.

## 3. Vì sao phải xác minh Method trước khi gọi?

Java hỗ trợ tra cứu chính xác bằng:

```java
Method method = Service.class.getMethod("sum", int.class, int.class);
```

`Class.getMethod` lấy method `public` có tên và dãy kiểu tham số tương ứng; khác với chỉ tìm theo tên. Method overloaded có thể cùng tên nhưng khác parameter types, vì vậy một hợp đồng chỉ theo tên không đủ.

Nên kiểm tra thêm:

```java
if (method.getDeclaringClass() != Service.class
        || method.getReturnType() != int.class
        || !Modifier.isPublic(method.getModifiers())) {
    throw new IllegalStateException("Contract mismatch");
}
```

Hai đoạn trên là **mảnh code minh họa** đặt trong ngữ cảnh có `Service`, `Method`, `Modifier` và imports phù hợp; chương trình chạy hoàn chỉnh nằm ở mục 5.

Trong thiết kế cố định ở đây, yêu cầu `getDeclaringClass() == Service.class` nhằm từ chối method được kế thừa không chủ đích. Ứng dụng khác có thể **chủ ý** cho phép inherited method nếu ghi điều đó trong hợp đồng. Các ràng buộc này cũng phải phù hợp với cách đăng ký interface, generic bridge method hoặc proxy của framework nếu dùng chúng.

Không nên gọi `clazz.getMethods()` rồi chọn method đầu tiên trùng tên: thứ tự từ API không phải cơ chế giải quyết overload đáng tin cậy, và có thể lộ ra hành vi chưa được cho phép.

Một điểm dễ bỏ qua: `getMethod` có thể trả về method `public` của lớp không được phép gọi trong ngữ cảnh module/accessibility. Trong thực tế, **khả năng tìm Method** không bảo đảm **có thể invoke** với mọi module/target. Ví dụ này đặt class/method `public`, trong cùng ứng dụng, nên không cần phá đóng gói bằng `setAccessible`.

## 4. Chuyển đổi đối số theo vị trí bằng quy tắc rõ ràng

Input dạng `List<String>` hoặc `String[]` cần được parse sang kiểu Java đã khai báo, ví dụ:

| Tham số | Input thô | Kiểu đích | Quy tắc |
| --- | --- | --- | --- |
| `left` | `"7"` | `int` | `Integer.parseInt` và miền `0..1000` |
| `right` | `"5"` | `int` | Như `left` |
| `text` | `"go"` | `String` | Không null/rỗng/trắng; độ dài tối đa 20 |
| `times` | `"3"` | `int` | `Integer.parseInt` và miền `1..3` |

Parser xử lý đầu vào, còn reflection chịu trách nhiệm đưa các giá trị đã parse vào method. Khi gọi `Method.invoke`, kiểu `int` ở `Object[]` được biểu diễn bằng `Integer`; reflection có thể unbox hợp lệ khi tham số phương thức là `int`. Nhưng nó **không tự** parse chuỗi `"7"` thành số nguyên.

Chính sách cần nói rõ:

- `null` khác chuỗi `"null"` hoặc `""`; không tự thay bằng giá trị mặc định khi không được yêu cầu.
- `Integer.parseInt` có thể ném `NumberFormatException`, một subclass của `IllegalArgumentException`, cho cú pháp sai hoặc số ngoài phạm vi `int`.
- Một giá trị đã parse thành `int` **vẫn có thể vi phạm nghiệp vụ**, ví dụ tổng lớn hơn ngưỡng cho phép.
- Không tự trim, đổi chữ hoa/thường, áp locale hay parse định dạng khác nếu contract không cho phép.
- Converter nên có giới hạn dữ liệu và báo **vị trí tham số + kiểu mong đợi**, tránh trả raw input nhạy cảm trực tiếp trong thông báo lỗi.

## 5. Chương trình Java 17+ hoàn chỉnh

Tạo tệp `ContractInvocationDemo.java`. Ví dụ không phụ thuộc thư viện ngoài. Nó dùng một `Map<String, Plan>` bất biến chứa hai lệnh `sum` và `label`; một `Plan` lưu Method đã kiểm tra và parser tương ứng từng vị trí.

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

public final class ContractInvocationDemo {
    @FunctionalInterface
    interface Parser {
        Object parse(String raw);
    }

    static final class BusinessRuleException extends RuntimeException {
        BusinessRuleException(String message) {
            super(message);
        }
    }

    public static final class Service {
        public int sum(int left, int right) {
            int total = Math.addExact(left, right);
            if (total > 1_500) {
                throw new BusinessRuleException("sum exceeds 1500");
            }
            return total;
        }

        public String label(String text, int times) {
            return text.repeat(times);
        }
    }

    static final class Plan {
        private final String alias;
        private final Method method;
        private final Parser[] parsers;

        Plan(String alias, String methodName, Class<?> returnType,
             Class<?>[] argumentTypes, Parser[] parsers)
                throws ReflectiveOperationException {
            Method resolved = Service.class.getMethod(methodName, argumentTypes);
            if (resolved.getDeclaringClass() != Service.class
                    || !Modifier.isPublic(resolved.getModifiers())
                    || resolved.getReturnType() != returnType
                    || argumentTypes.length != parsers.length) {
                throw new IllegalStateException("invalid trusted contract: " + alias);
            }
            this.alias = alias;
            this.method = resolved;
            this.parsers = parsers.clone();
        }

        Object execute(Service target, List<String> raw) throws Throwable {
            if (raw.size() != parsers.length) {
                throw new IllegalArgumentException(alias + ": expected "
                        + parsers.length + " args, got " + raw.size());
            }
            Object[] converted = new Object[parsers.length];
            for (int i = 0; i < parsers.length; i++) {
                try {
                    converted[i] = parsers[i].parse(raw.get(i));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(alias + ": argument " + i
                            + " expected " + method.getParameterTypes()[i].getTypeName(), ex);
                }
            }
            try {
                return method.invoke(target, converted);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        }
    }

    static int parseAmount(String raw) {
        int value = Integer.parseInt(raw);
        if (value < 0 || value > 1_000) {
            throw new IllegalArgumentException("amount out of range");
        }
        return value;
    }

    static int parseTimes(String raw) {
        int value = Integer.parseInt(raw);
        if (value < 1 || value > 3) {
            throw new IllegalArgumentException("times out of range");
        }
        return value;
    }

    static String parseText(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 20) {
            throw new IllegalArgumentException("invalid text");
        }
        return raw;
    }

    static Map<String, Plan> register() throws ReflectiveOperationException {
        return Map.of(
                "sum", new Plan("sum", "sum", int.class,
                        new Class<?>[]{int.class, int.class},
                        new Parser[]{ContractInvocationDemo::parseAmount,
                                ContractInvocationDemo::parseAmount}),
                "label", new Plan("label", "label", String.class,
                        new Class<?>[]{String.class, int.class},
                        new Parser[]{ContractInvocationDemo::parseText,
                                ContractInvocationDemo::parseTimes}));
    }

    public static void main(String[] args) throws Throwable {
        Map<String, Plan> allowlist = register();
        Service target = new Service();

        System.out.println(call(allowlist, target, "sum", List.of("7", "5")));
        System.out.println(call(allowlist, target, "label", List.of("go", "3")));

        try {
            call(allowlist, target, "getClass", List.of());
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected: " + ex.getMessage());
        }
        try {
            call(allowlist, target, "sum", List.of("7"));
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected: " + ex.getMessage());
        }
        try {
            call(allowlist, target, "sum", List.of("abc", "5"));
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected: " + ex.getMessage());
        }
        try {
            call(allowlist, target, "label", List.of("go", "9"));
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected: " + ex.getMessage());
        }
        try {
            call(allowlist, target, "sum", List.of("900", "900"));
        } catch (BusinessRuleException ex) {
            System.out.println("business: " + ex.getMessage());
        }
    }

    static Object call(Map<String, Plan> allowlist, Service target,
                       String alias, List<String> raw) throws Throwable {
        Plan plan = allowlist.get(alias);
        if (plan == null) {
            throw new IllegalArgumentException("unsupported command");
        }
        return plan.execute(target, raw);
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac ContractInvocationDemo.java
java ContractInvocationDemo
```

**Stdout chính xác** (đã kiểm chứng khi chạy chương trình trên JDK):

```text
12
gogogo
rejected: unsupported command
rejected: sum: expected 2 args, got 1
rejected: sum: argument 0 expected int
rejected: label: argument 1 expected int
business: sum exceeds 1500
```

### Đọc code theo đường đi của một request

1. `register()` được gọi lúc khởi tạo, dùng các tên method, kiểu tham số và kiểu trả về **được hard-code bởi ứng dụng**. `Service.class.getMethod` tra cứu đúng chữ ký. Nếu method không tồn tại, `NoSuchMethodException` khiến khởi tạo thất bại. Nếu return/declaring class sai hoặc số parser không khớp, `IllegalStateException` từ chối hợp đồng.
2. `Map.of(...)` tạo map không sửa được, nên caller không thể thêm alias `getClass` sau đó. `Plan` clone mảng parser, giúp tránh mã bên ngoài thay đổi mảng parser sau đăng ký.
3. `call(...)` tìm alias từ allowlist. `"getClass"` không có trong registry dù `Service` kế thừa `Object.getClass`, nên trả `unsupported command`.
4. `execute(...)` kiểm tra arity trước khi parse. Lệnh `sum` với chỉ một giá trị bị từ chối ngay, không cố gán giá trị mặc định.
5. Mỗi `Parser` áp dụng luật cho một tham số. Dữ liệu `"abc"` không parse thành `int`; dữ liệu `"9"` vượt giới hạn số lần lặp cho phép. Hai lỗi được thêm ngữ cảnh alias, vị trí, kiểu đích mà không lộ nguyên chuỗi đầu vào.
6. `method.invoke(target, converted)` gọi **đối tượng dịch vụ thật**, không gọi chính proxy hay phương thức được chọn từ tên người dùng.
7. Nếu `Service.sum` ném `BusinessRuleException` do tổng vượt 1500, reflection bọc nó trong `InvocationTargetException`; handler lấy `getCause()` để caller nhận đúng lỗi nghiệp vụ. Lỗi mapping được xử lý **trước khi target chạy**.

Trong ví dụ, miền `parseAmount` là `0..1000`, do đó `Math.addExact` không tràn `int`; nó vẫn thể hiện cách tránh overflow khi bài toán số học có phạm vi lớn hơn. Mức giới hạn 1500 chỉ dùng minh họa quy tắc nghiệp vụ.

### Hạn chế được tuyên bố của ví dụ

- `List.of(...)` trong `main` không cho phần tử null. Đầu vào thực tế có thể null/missing, nên tầng nhận request phải quyết định chính sách cho hai trường hợp đó trước khi gọi plan.
- Ví dụ nhận request trong code, không mở HTTP endpoint và không xây hệ thống authentication/authorization. Nếu là API nhiều người dùng, phải kiểm tra caller/role và quyền trên từng alias.
- `Plan.execute` không chặn `target == null` hoặc lỗi target không phù hợp, vì code chỉ tạo `Service` đúng kiểu. Công cụ tổng quát cần kiểm tra target trước khi invoke.
- Ví dụ chỉ đăng ký hai method, không xử lý varargs, generic DTO, optional values hay phương thức asynchronous. Những khả năng đó cần hợp đồng và converter riêng.

## 6. Lỗi ở từng giai đoạn và cách phân loại

Đừng gộp tất cả thành một `catch (Exception)` rồi báo “sai dữ liệu”. Phân chia:

| Giai đoạn | Lỗi điển hình | Ý nghĩa |
| --- | --- | --- |
| Startup / xác minh contract | `NoSuchMethodException` | Method không tồn tại với chữ ký đã khai báo |
| Startup / xác minh contract | `IllegalStateException` | Return type, declaring class hoặc số parser không đúng |
| Dispatch | Alias không có trong allowlist | Lệnh không được hỗ trợ hoặc không được cho phép |
| Mapping | Sai arity, số không hợp lệ, null không đúng | Request không phù hợp schema |
| Reflection invocation | `IllegalAccessException`, `IllegalArgumentException` | Quyền truy cập hoặc receiver/argument không phù hợp |
| Business execution | `InvocationTargetException` bọc cause của target | Phương thức đã chạy và tự ném exception |

Chỉ bắt `InvocationTargetException` ở tầng invoke để lấy nguyên nhân nghiệp vụ:

```java
try {
    return method.invoke(target, converted);
} catch (InvocationTargetException ex) {
    throw ex.getCause();
}
```

Đây là một mảnh code nằm trong phương thức khai báo `throws Throwable`. Không giả định mọi `IllegalArgumentException` đều đến từ parser: `Method.invoke` cũng có thể phát sinh `IllegalArgumentException` khi receiver/argument không tương thích. Nếu target ném `IllegalArgumentException`, nó là **cause** trong `InvocationTargetException`, giúp phân biệt nguồn phát sinh.

Với HTTP API, giai đoạn mapping có thể tương ứng response `400 Bad Request`; từ chối vì thiếu quyền thường là `403 Forbidden`; lỗi contract/khởi tạo là lỗi hệ thống. Đây là hướng ánh xạ phụ thuộc thiết kế giao thức, không phải hành vi tự động của Reflection. Không nên gửi nguyên stack trace hoặc giá trị nhạy cảm cho caller.

## 7. Những điểm cần mở rộng khi làm công cụ thực tế

### 7.1. Phân quyền và giới hạn thao tác

Allowlist ràng buộc **những lệnh tồn tại**, còn authorization xác định **ai được chạy lệnh nào**. Trước khi `call`:

```text
Caller identity -> kiểm quyền thao tác alias -> kiểm quota/rate limit
               -> kiểm contract + validate input -> execute
```

Không nhận tên class, đường dẫn package, method name hoặc flag `setAccessible` từ client để tăng quyền. Nếu dùng registry lấy từ database/cấu hình, phải xác minh tính toàn vẹn và nguồn tin cậy của cấu hình trước khi tạo kế hoạch gọi.

Các lời gọi target có side effect (ghi DB, gửi thông báo, xóa dữ liệu) cần idempotency, transaction và authorization tùy hệ thống. Reflection không làm lời gọi trở nên an toàn hoặc có thể tự động retry.

### 7.2. Overload và chọn Method chính xác

Nếu `Service` có `save(int)` và `save(String)`, chỉ tên `"save"` không xác định overload. Hãy đăng ký hợp đồng kèm parameter types **cụ thể**, ví dụ:

```java
Method numericSave = Service.class.getMethod("save", int.class);
```

Đây là mảnh code minh họa giả định `Service` có phương thức `public save(int)`; phương thức đó **không được khai báo trong chương trình ở mục 5**. Nếu muốn chọn overload theo dữ liệu runtime, cần quy tắc ưu tiên và phát hiện trường hợp mơ hồ. Không chọn method đầu tiên từ `getMethods()`.

### 7.3. Generic, varargs, null và validation

- `getParameterTypes()` cung cấp kiểu sau **type erasure**; để parse `List<String>` hoặc DTO generic cần schema hoặc `getGenericParameterTypes()` kèm converter tương ứng.
- Varargs như `send(String... values)` có kiểu tham số cuối là `String[]` ở reflection. Cần dựng mảng đúng kiểu và đưa vào mảng arguments theo vị trí; ví dụ ở trên không tự mở rộng varargs.
- Tham số primitive không thể nhận null. Wrapper/reference có thể nhận null ở mức Reflection nhưng schema hoặc nghiệp vụ có thể cấm. Phải phân biệt **missing**, **null** và chuỗi `"null"`.
- Parse thành công không bảo đảm dữ liệu hợp lệ về nghiệp vụ: ví dụ parse được số lượng âm nhưng schema có thể cấm; `sum` còn có ràng buộc tổng vượt ngưỡng.
- Giới hạn độ dài, số lượng phần tử, độ sâu dữ liệu lồng nhau và timeout tác vụ nếu request không đáng tin.

### 7.4. Không lạm dụng setAccessible hoặc quét tất cả phương thức

Chỉ vì có thể dùng `getDeclaredMethods()` hoặc `setAccessible(true)` không có nghĩa đó là cách hợp lệ để xây công cụ gọi phương thức. Chúng có thể vượt qua thiết kế đóng gói, đụng ranh giới JPMS module, và làm bề mặt thao tác không còn được kiểm soát.

Nếu mục tiêu là một API/plugin có hợp đồng công khai, ưu tiên **interface rõ ràng, registry command** hoặc annotation được xác minh ở startup. Reflection hữu ích khi cần động hóa dispatch, nhưng không thay thế thiết kế API có kiểu và kiểm soát quyền.

## 8. Kế hoạch kiểm thử cho công cụ

Kiểm thử cần bảo đảm **hợp đồng được xác minh**, **mapper không gọi target khi input sai**, và **sai quyền/command không chạy method khác**.

| Ca kiểm thử | Đầu vào | Kết quả mong đợi |
| --- | --- | --- |
| Hợp lệ `sum` | `sum("7", "5")` | `12` |
| Hợp lệ `label` | `label("go", "3")` | `gogogo` |
| Alias không đăng ký | `getClass()` | Từ chối, không thực hiện reflection invoke |
| Sai số lượng | `sum("7")` | Lỗi số tham số; không gọi target |
| Sai format | `sum("abc", "5")` | Lỗi argument 0, expected int |
| Vượt miền parser | `label("go", "9")` | Lỗi argument 1 |
| Lỗi nghiệp vụ | `sum("900", "900")` | Target ném `BusinessRuleException`, giữ cause |
| Hợp đồng sai | `sum` khai báo return `String` | Startup fail trước khi nhận request |
| Method không tồn tại | Tên/signature không khớp | Startup fail bằng `NoSuchMethodException` |
| Kiểu receiver sai | Gọi instance method trên object không phù hợp | Bị ngăn khi kiểm target hoặc reflection báo lỗi |
| Đầu vào null/missing | `null` hoặc thiếu trường | Áp chính sách schema, không thay mặc định ngầm |
| Lỗi bảo mật | Caller không có quyền dùng alias | Bị từ chối trước mapping/invoke |

Có thể ghi log các sự kiện `alias, result status, duration, error code`; hạn chế log raw input và stack trace ở kênh dành cho người dùng. Benchmark và cache Method chỉ nên thực hiện sau khi hành vi đúng và quyền được bảo đảm.

## 9. Bài tập mở rộng

**Bài 1 — Thêm lệnh an toàn.** Thêm `multiply(int,int)`, đăng ký alias `multiply` với hai parser kiểm phạm vi và kiểu trả về `int`. Xác minh cả câu lệnh hợp lệ và lỗi overflow bằng `Math.multiplyExact` nếu muốn minh họa lỗi số học.

**Bài 2 — Kiểm tra quyền.** Tạo map `role -> allowed aliases` rồi chặn trước `call`. Viết test chứng minh user chỉ có quyền `label` không thể chạy `sum` kể cả khi biết alias.

**Bài 3 — Enum và ngày tháng.** Thêm một method nhận `LocalDate` hoặc enum chỉ định; đăng ký converter tường minh. Kiểm thử format ISO đúng/sai và enum constant không tồn tại.

**Bài 4 — Lỗi target.** Tạo method cố tình ném checked exception được khai báo; xác minh caller nhận nguyên nhân nghiệp vụ qua `InvocationTargetException.getCause()`, không nhầm với lỗi parser.

**Bài 5 — Null/missing.** Thay `List.of` bằng lớp request có thể biểu diễn missing/null/string. Định nghĩa rõ required, optional và default, kiểm tra trước reflection invocation.

**Bài 6 — Contract thay đổi.** Đổi kiểu trả về của method nhưng giữ metadata đăng ký cũ, chạy lại ứng dụng. Kết quả đúng là **startup fail**, thay vì đợi request người dùng đầu tiên mới phát hiện hợp đồng lệch.

## 10. Câu hỏi tự kiểm tra

1. **Vì sao không cho caller gửi tên class/method bất kỳ?** Vì như vậy input đang điều khiển tập hành vi được phép gọi; registry tin cậy phải giới hạn thao tác.
2. **Tại sao kiểm tra return type dù `getMethod` chỉ tìm theo parameter types?** Vì hợp đồng có thể yêu cầu một kiểu trả về cụ thể, và method thực tế có thể đã thay đổi.
3. **`Method.invoke` có parse `"7"` thành `int` không?** Không. Parser phải làm việc đó trước, rồi reflection mới có thể unbox `Integer` thành `int`.
4. **Nếu caller chọn alias `getClass` không đăng ký thì sao?** Bị từ chối, dù `getClass()` thực sự tồn tại trên `Object`.
5. **Lỗi từ target và lỗi chuyển đổi khác gì?** Lỗi chuyển đổi xảy ra trước khi invoke; lỗi target chạy qua `InvocationTargetException` và cần lấy cause.
6. **Allowlist có thay thế authentication/authorization không?** Không. Allowlist quyết định các thao tác *được hệ thống hỗ trợ*; quyền caller vẫn phải được kiểm tra riêng.
7. **Tại sao không chọn overload chỉ bằng tên method?** Vì có thể có nhiều chữ ký khác nhau; cần parameter types hoặc quy tắc giải quyết mơ hồ tường minh.

## 11. Điểm cần nhớ

Một công cụ gọi method có kiểm soát nên đi theo trình tự **khai báo hợp đồng tin cậy → xác minh đúng Method → đăng ký alias được phép → kiểm tra quyền caller → parse/validate từng đối số → gọi target → phân loại kết quả/ngoại lệ**.

Reflection cung cấp cơ chế thực thi động; hợp đồng và quy tắc ánh xạ quyết định hành vi nào được phép. Hãy ưu tiên fail-fast ở startup cho hợp đồng sai, trả lỗi rõ ràng cho input sai, bảo toàn nguyên nhân ngoại lệ nghiệp vụ và kiểm thử mọi đường từ chối trước khi đưa công cụ ra hệ thống thật.

Tài liệu Java 17:

- [Class.getMethod](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getMethod(java.lang.String,java.lang.Class...))
- [Method.invoke](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...))
- [InvocationTargetException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html)
- [Modifier](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Modifier.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-practice-003">Tạo factory từ constructor có kiểu và thông báo lỗi rõ ràng</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo factory từ constructor có kiểu và thông báo lỗi rõ ràng — Java Reflection

## 1. Bài toán thực hành

Giả sử hệ thống có nhiều implementation của interface `Report` và muốn tạo instance tương ứng tại runtime. Mỗi implementation có một **constructor nhận `String title`**. Caller cần:

- Yêu cầu factory tạo instance với **đúng kiểu `T extends Report`**, không phải nhận `Object` rồi ép kiểu tràn lan.
- Kiểm tra constructor mong đợi **một lần khi tạo factory**, thay vì tìm lại metadata trong mọi request.
- Nêu rõ lỗi thiếu class, sai contract, thiếu chữ ký constructor, constructor không public, không thể instantiate, hay **lỗi xuất phát từ chính thân constructor**.
- Giữ nguyên `cause` và stack trace khi bọc exception, giúp người vận hành biết sai do cấu hình/Reflection hay do mã nghiệp vụ.

Thiết kế mong muốn:

```java
TypedFactory<PdfReport> factory = TypedFactory.forClass(PdfReport.class);
PdfReport report = factory.create("Monthly");
System.out.println(report.render()); // PDF: Monthly
```

Đây là mảnh minh họa dùng các class được định nghĩa đầy đủ ở phần 5. `TypedFactory<PdfReport>` giúp trình biên dịch biết `create(...)` trả về `PdfReport`, còn Reflection chỉ được dùng trong lớp factory.

**Ý tưởng:** gom rủi ro Reflection vào một ranh giới nhỏ, biểu diễn phần còn lại của ứng dụng qua interface và kiểu generic. Factory không cần biến mọi lệnh gọi Java thành `Method.invoke`; sau khi tạo đối tượng, dùng đa hình thông thường.

## 2. “Constructor có kiểu” nghĩa là gì?

API `java.lang.reflect.Constructor<T>` giữ thông tin kiểu instance sẽ được tạo:

```java
Class<PdfReport> type = PdfReport.class;
Constructor<PdfReport> ctor = type.getConstructor(String.class);
PdfReport report = ctor.newInstance("Monthly");
```

Đoạn minh họa trên cần `import java.lang.reflect.Constructor;` và các class từ ví dụ đầy đủ bên dưới; nó phát sinh checked reflection exceptions nên phương thức bao quanh phải `throws` hoặc `catch` thích hợp.

Các thành phần:

| Thành phần | Ý nghĩa |
|---|---|
| `Class<T>` | Runtime token đại diện cho kiểu cụ thể `T` |
| `Constructor<T>` | Metadata constructor tạo instance tương ứng với `T` |
| `type.getConstructor(String.class)` | Tra constructor **public**, có **chính xác một** tham số khai báo kiểu `String` |
| `constructor.newInstance(title)` | Kiểm tra điều kiện gọi và chạy thân constructor, trả `T` nếu thành công |
| `TypedFactory<T extends Report>` | Gói constructor và cung cấp API tạo mới có kiểu, ràng buộc kiểu trả về theo `Report` |

Trong factory generic:

```java
public final class TypedFactory<T extends Report> {
    private final Constructor<T> constructor;
    // Các thành viên khác...
}
```

Đây là mảnh khai báo thiết kế; class chạy đầy đủ nằm ở phần 5.

### Điều type safety đảm bảo — và không đảm bảo

`TypedFactory<PdfReport>.create(...)` trả về `PdfReport` tại compile time. Dùng `Class<T>` lấy từ literal `PdfReport.class` giúp các bước tra cứu an toàn hơn kiểu `Class<?>` và cast `(T) rawObject` không được kiểm soát.

Tuy nhiên, **generic không làm runtime lookup biến thành compile-time lookup**:

- Lớp có thể thiếu constructor `public PdfReport(String)`.
- Constructor có thể nằm ở phạm vi `private` hoặc `protected`.
- Thân constructor có thể ném `IllegalArgumentException`, `IOException`, `Error`, v.v.
- Trong các môi trường module/class loader, vẫn có thể có lỗi access, linkage hoặc class initialization.

Factory phải chẩn đoán rõ ràng các điểm này. Không hứa rằng vì sử dụng `Constructor<T>` nên Reflection không thể lỗi tại runtime.

## 3. Chọn đúng constructor: public, declared, signature và class hợp lệ

Hai API thường gặp:

```java
type.getConstructor(String.class);
type.getDeclaredConstructor(String.class);
```

- `getConstructor(...)` tìm **constructor public** với parameter types được chỉ định.
- `getDeclaredConstructor(...)` tìm constructor khai báo ngay trong class, bao gồm non-public.
- Constructor **không được kế thừa** từ superclass: không có cơ chế tìm public inherited constructor như method.
- Hai API tra cứu trên dựa vào **danh sách kiểu tham số chính xác**. Chúng không tự boxing `int` thành `Integer`, không tự chọn overload hoặc convert `CharSequence` thành `String`.
- `Class.getConstructor(...)` ném `NoSuchMethodException` khi không có constructor public đúng signature, kể cả khi tồn tại private constructor cùng signature.
- Constructor không phải method thông thường; không tra bằng `getMethod("ClassName", ...)`.

Với parameter `String`:

| Constructor thực tế | `getConstructor(String.class)` |
|---|---|
| `public PdfReport(String title)` | Tìm được |
| `private PdfReport(String title)` | Ném `NoSuchMethodException` |
| `public PdfReport(CharSequence title)` | Ném `NoSuchMethodException` |
| `public PdfReport()` | Ném `NoSuchMethodException` |
| `public PdfReport(String title, int version)` | Ném `NoSuchMethodException` |
| `public PdfReport(String... values)` | Ném `NoSuchMethodException` nếu tìm bằng `String.class`, vì kiểu tham số ở bytecode là `String[].class` |

**Không tự động “thử tất cả constructor cho tới khi một cái chạy được”**: điều đó khiến API factory khó đoán và có thể thực thi code không mong muốn. Quy định rõ chữ ký contract; khi không khớp, báo lỗi cấu hình có hành động sửa cụ thể.

Trước khi tạo instance cần lưu ý `abstract class` và `interface` không thể được instantiate theo cách này. Ví dụ chọn kiểm tra sớm trong factory để sinh thông báo rõ ràng. Với enum, record, member class không static, anonymous/local class hoặc lớp từ JPMS module, cấu trúc/khả năng khởi tạo có các ràng buộc riêng; không ép mọi class vào hợp đồng `(String)`.

## 4. Thiết kế factory và các nhóm lỗi

Ở bài thực hành, ta chia lỗi thành hai ngoại lệ runtime riêng:

- `FactoryConfigurationException`: thiết lập factory không hợp lệ — class không tồn tại, không implement `Report`, trừu tượng, không có constructor public `(String)`, hoặc kiểm tra metadata bị chặn. Đây thường là lỗi của deploy/config/plugin contract.
- `FactoryCreationException`: factory đã được tạo nhưng **tạo instance thất bại** — constructor đích ném lỗi, hoặc bước invocation gặp vấn đề về access/arguments/instantiation.

Cả hai giữ một `Throwable cause`, không chỉ thông báo chuỗi. Mẫu thông điệp nên nêu **loại lỗi, class, signature và nguyên nhân gốc**:

```text
Missing public constructor (String) on WrongConstructor
Constructor of FailingReport failed: IllegalArgumentException: invalid title
```

Không nên viết duy nhất `"Cannot create object"` hoặc `"Reflection failed"`: người sửa không biết phải đổi cấu hình, sửa constructor, hay sửa input.

### Ba giai đoạn có trách nhiệm khác nhau

```text
input Class<T> hoặc configured class name
             |
             v
[1] xác minh class + contract T extends Report
             |
             v
[2] resolve và cache public Constructor<T>(String)
             |
             v
[3] create(title) -> Constructor.newInstance(title)
             |
     +-------+-----------------------+
     |                               |
     v                               v
Reflection invocation lỗi      Constructor body ném Throwable
IllegalAccessException,        InvocationTargetException
InstantiationException, ...                |
     |                                  getCause()
     v                                      v
FactoryCreationException       FactoryCreationException
(với cause gốc)                  (với cause của target)
```

Việc **tra cứu constructor được thực hiện trước** giúp lỗi thiếu constructor xuất hiện ngay tại `forClass`, thay vì chờ tới request đầu tiên. Đây là kiểu **fail fast** hữu ích khi factory được tạo trong bước bootstrap.

## 5. Ví dụ Java 17+ hoàn chỉnh, có thông báo lỗi rõ ràng

Tạo file duy nhất **`TypedFactoryDemo.java`**:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Objects;

public class TypedFactoryDemo {
    public interface Report {
        String render();
    }

    public static final class PdfReport implements Report {
        private final String title;

        public PdfReport(String title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        @Override
        public String render() {
            return "PDF: " + title;
        }
    }

    public static final class WrongConstructor implements Report {
        public WrongConstructor(int pages) {}

        @Override
        public String render() {
            return "unavailable";
        }
    }

    public static final class PrivateReport implements Report {
        private PrivateReport(String title) {}

        @Override
        public String render() {
            return "private";
        }
    }

    public abstract static class AbstractReport implements Report {
        public AbstractReport(String title) {}
    }

    public static final class FailingReport implements Report {
        public FailingReport(String title) {
            throw new IllegalArgumentException("invalid title");
        }

        @Override
        public String render() {
            return "unavailable";
        }
    }

    public static final class FactoryConfigurationException extends RuntimeException {
        public FactoryConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class FactoryCreationException extends RuntimeException {
        public FactoryCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class TypedFactory<T extends Report> {
        private final Class<T> type;
        private final Constructor<T> constructor;

        private TypedFactory(Class<T> type, Constructor<T> constructor) {
            this.type = type;
            this.constructor = constructor;
        }

        public static <T extends Report> TypedFactory<T> forClass(Class<T> type) {
            Objects.requireNonNull(type, "type");
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                throw new FactoryConfigurationException(
                        "Cannot instantiate abstract type: " + type.getSimpleName(), null);
            }
            try {
                return new TypedFactory<>(type, type.getConstructor(String.class));
            } catch (NoSuchMethodException e) {
                throw new FactoryConfigurationException(
                        "Missing public constructor (String) on " + type.getSimpleName(), e);
            } catch (SecurityException e) {
                throw new FactoryConfigurationException(
                        "Constructor lookup denied for " + type.getSimpleName(), e);
            }
        }

        public T create(String title) {
            try {
                return constructor.newInstance(title);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                throw new FactoryCreationException(
                        "Constructor of " + type.getSimpleName() + " failed: "
                                + cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                        cause);
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
                throw new FactoryCreationException(
                        "Cannot create " + type.getSimpleName() + " with (String)", e);
            }
        }

        public static TypedFactory<? extends Report> fromName(String className) {
            Objects.requireNonNull(className, "className");
            try {
                Class<? extends Report> type =
                        Class.forName(className).asSubclass(Report.class);
                return forClass(type);
            } catch (ClassNotFoundException e) {
                throw new FactoryConfigurationException(
                        "Cannot load class: " + className, e);
            } catch (ClassCastException e) {
                throw new FactoryConfigurationException(
                        "Configured class " + className + " does not implement Report", e);
            }
        }
    }

    public static void main(String[] args) {
        TypedFactory<PdfReport> pdf = TypedFactory.forClass(PdfReport.class);
        PdfReport created = pdf.create("Monthly");
        System.out.println("success: " + created.render());

        try {
            TypedFactory.forClass(WrongConstructor.class);
        } catch (FactoryConfigurationException e) {
            System.out.println("wrong signature: " + e.getMessage());
        }

        try {
            TypedFactory.forClass(PrivateReport.class);
        } catch (FactoryConfigurationException e) {
            System.out.println("private constructor: " + e.getMessage());
        }

        try {
            TypedFactory.forClass(AbstractReport.class);
        } catch (FactoryConfigurationException e) {
            System.out.println("abstract class: " + e.getMessage());
        }

        try {
            TypedFactory.forClass(FailingReport.class).create("bad");
        } catch (FactoryCreationException e) {
            System.out.println("target failure: " + e.getMessage());
            System.out.println("target cause: " + e.getCause().getClass().getSimpleName());
        }

        try {
            TypedFactory.fromName("java.lang.String");
        } catch (FactoryConfigurationException e) {
            System.out.println("wrong type: " + e.getMessage());
        }

        try {
            TypedFactory.fromName("demo.NotFound");
        } catch (FactoryConfigurationException e) {
            System.out.println("missing type: " + e.getMessage());
        }
    }
}
```

Chạy tại thư mục chứa file:

```bash
javac --release 17 TypedFactoryDemo.java
java TypedFactoryDemo
```

**Stdout thực tế** khi biên dịch/chạy chương trình:

```text
success: PDF: Monthly
wrong signature: Missing public constructor (String) on WrongConstructor
private constructor: Missing public constructor (String) on PrivateReport
abstract class: Cannot instantiate abstract type: AbstractReport
target failure: Constructor of FailingReport failed: IllegalArgumentException: invalid title
target cause: IllegalArgumentException
wrong type: Configured class java.lang.String does not implement Report
missing type: Cannot load class: demo.NotFound
```

Mã đã được biên dịch bằng `javac --release 17` và chạy thành công. Nó sử dụng public nested classes trong một file để tiện sao chép; trong ứng dụng thực tế nên tách `TypedFactory` và implementation thành các class/package được quản lý rõ ràng.

## 6. Đọc kết quả: phân loại đúng từng trường hợp

**Đường thành công:** `TypedFactory.forClass(PdfReport.class)` nhận một `Class<PdfReport>`; `getConstructor(String.class)` tìm constructor public đúng chữ ký. `create("Monthly")` trả một đối tượng kiểu `PdfReport`; caller gọi `render()` bình thường, không cần `Method.invoke`.

**Sai chữ ký:** `WrongConstructor` chỉ có constructor `(int)`. Đây không phải lỗi “tham số runtime không chuyển được”; factory phát hiện **thiếu constructor `(String)` ngay tại lookup**, bắt `NoSuchMethodException` và chuyển thành `FactoryConfigurationException` có thông tin rõ ràng.

**Constructor private:** `PrivateReport` có `(String)` nhưng private. `getConstructor(String.class)` không trả constructor này; thông báo “missing public constructor” chính xác theo contract của factory. Không tự tìm private constructor rồi `setAccessible(true)` vì điều đó làm đổi hợp đồng truy cập và có thể vi phạm giới hạn JPMS.

**Abstract class:** `AbstractReport` là một type hợp lệ theo `T extends Report` nhưng không instantiate trực tiếp được. Chặn trước bằng `Modifier.isAbstract` để báo lỗi cấu hình thay vì để `Constructor.newInstance` thất bại muộn.

**Lỗi trong thân constructor:** `FailingReport(String)` hợp lệ, nhưng khi chạy ném `IllegalArgumentException("invalid title")`. `Constructor.newInstance` **bọc lỗi của mã đích trong `InvocationTargetException`**. Factory dùng `e.getCause()` để lấy lại `IllegalArgumentException`, tạo `FactoryCreationException` với cause gốc và thông báo có loại lỗi + nội dung.

**Class sai contract:** `Class.forName("java.lang.String")` tìm được class, nhưng `String` không implement `Report`. `asSubclass(Report.class)` ném `ClassCastException`; factory chuyển sang thông báo cấu hình “does not implement Report”. Không ép kiểu `(Class<Report>)` bằng unchecked cast để che mất lỗi.

**Class không tồn tại:** `Class.forName("demo.NotFound")` ném `ClassNotFoundException`. Đây là lỗi **tìm class**, khác với `NoSuchMethodException` khi class tồn tại nhưng constructor không khớp.

## 7. Vì sao factory cần giữ nguyên cause?

Nếu chỉ ghi:

```java
catch (Exception e) {
    throw new RuntimeException("Create failed");
}
```

thì stack trace của lỗi gốc bị mất khỏi **chuỗi nguyên nhân**; rất khó biết nguyên nhân là constructor thiếu, security restriction, null input hay bug bên trong implementation.

Giữ cause:

```java
catch (InvocationTargetException e) {
    Throwable targetFailure = e.getCause();
    throw new FactoryCreationException(
            "Constructor of " + type.getName() + " failed",
            targetFailure);
}
```

Đoạn này là **mảnh code minh họa** nằm trong `TypedFactory<T>`, với biến `type` và `FactoryCreationException` đã tồn tại. Khi log exception được ném ra, stack trace có đường đi qua lớp factory rồi đến lỗi của constructor. Không chỉ log `getMessage()` nếu cần phân tích stack trace.

### Phân biệt wrapper ngoài và nguyên nhân bên trong

| Giai đoạn | Exception gốc/quan sát | Nên báo |
|---|---|---|
| `getConstructor` không tìm thấy chữ ký public | `NoSuchMethodException` | Cấu hình sai: thiếu public constructor `(String)` |
| `Class.forName` không tìm thấy class | `ClassNotFoundException` | Cấu hình sai: class name/class loader/classpath |
| `asSubclass` không thỏa contract | `ClassCastException` | Cấu hình sai: implementation không phải subtype của interface |
| `Constructor.newInstance` thiếu quyền | `IllegalAccessException` | Không thể gọi constructor do access control/module |
| Constructor body ném exception | `InvocationTargetException` bọc Throwable | Thất bại khi tạo object: **nguyên nhân thực trong `getCause()`** |
| Lỗi không tương thích lớp/runtime | `LinkageError`, `ExceptionInInitializerError` ... | Lỗi linkage/khởi tạo JVM, không nhất thiết là lỗi từ body constructor |

**Điểm quan trọng:** `InvocationTargetException` là wrapper của Reflection invocation; `FactoryCreationException` trong ví dụ là **wrapper do ứng dụng tự định nghĩa**. Hai lớp này không phải một. Người sử dụng factory chỉ phải hiểu contract do ứng dụng công bố; người bảo trì có thể xem cause để điều tra gốc.

Trong production, quyết định có bọc hay **truyền tiếp `Error` nghiêm trọng** (ví dụ `OutOfMemoryError`) phải có chính sách riêng. Ví dụ chọn một loại exception đầu ra dễ hiểu cho mục đích học, nhưng không nên nuốt `Error` hoặc biến mọi lỗi JVM nghiêm trọng thành lỗi nghiệp vụ có thể retry.

## 8. Factory có kiểu khi class được cấu hình bằng String

Khi runtime nhận tên implementation từ file cấu hình, `Class.forName(className)` trả `Class<?>`. Để bảo toàn contract `Report`, dùng:

```java
Class<? extends Report> reportType =
        Class.forName(configuredName).asSubclass(Report.class);
TypedFactory<? extends Report> factory =
        TypedFactory.forClass(reportType);
Report report = factory.create("Monthly");
```

Đây là **mảnh code minh họa** với `configuredName` do ứng dụng cung cấp và các class ở ví dụ đầy đủ. Khi class được quyết định từ chuỗi runtime, compiler không thể biết trước nó là `PdfReport` hay `OtherReport`; do đó kiểu trả về an toàn là `? extends Report` hoặc `Report`, **không phải `PdfReport`**.

Nếu cần caller nhận chính xác `PdfReport`, truyền `PdfReport.class` trực tiếp hoặc kiểm tra/ép subtype một cách tường minh trước khi dùng.

**Không nạp class từ chuỗi do người dùng không tin cậy gửi lên mà không kiểm soát.** `Class.forName(String)` mặc định có thể **khởi tạo class**; initializer có thể chạy code dù bạn chưa gọi constructor. Với hệ plugin, nên dùng allowlist/registry, tên implementation được xác minh, class loader được phép, và tránh tiết lộ toàn bộ classpath qua message trả cho client.

Nếu muốn tách load class khỏi initialization, overload:

```java
Class<?> rawType = Class.forName(
        configuredName,
        false,
        Thread.currentThread().getContextClassLoader());
```

Đây là mảnh code minh họa. `initialize=false` chỉ trì hoãn initialization; khi instantiate về sau, class có thể vẫn bị initialize và phát sinh lỗi. Thread context class loader có thể không phù hợp trong mọi ứng dụng: quyết định loader theo kiến trúc plugin/module, không mặc định luôn đúng. Trong hệ JPMS và khi dùng nhiều class loader, kiểm tra visibility, module boundaries và hành vi load thực tế.

## 9. Vì sao chọn resolve constructor một lần, rồi cache?

Thiết kế giữ:

```java
private final Class<T> type;
private final Constructor<T> constructor;
```

Trong mỗi `TypedFactory<T>` instance, `getConstructor(String.class)` chỉ chạy trong `forClass`; `create` dùng lại `Constructor<T>`. Cách này:

1. **Fail fast** khi cấu hình implementation không thỏa constructor contract.
2. Tránh lặp lại lookup metadata mỗi lần tạo object.
3. Gom logging/exception translation một chỗ.
4. Tăng khả năng test vì mỗi trường hợp lỗi đi qua một API factory nhất quán.

Tuy nhiên, **constructor body vẫn chạy cho mỗi `create(...)`** và có thể thất bại theo dữ liệu đầu vào. Cache metadata không phải cache instance, không đồng nghĩa factory singleton hay thread-safe của chính object được tạo.

Một factory bất biến chỉ giữ `Class<T>` và `Constructor<T>` có thể được tái sử dụng từ nhiều thread; nhưng nếu implement constructor tạo shared mutable state hoặc chạm tài nguyên dùng chung, thread safety của quá trình tạo vẫn phụ thuộc implementation và resource.

### Tránh memory leak với plugin/class loader

Một biến `static Map<Class<?>, TypedFactory<?>>` sống suốt vòng đời ứng dụng có thể giữ strong reference tới `Class`/`Constructor` và class loader plugin. Trong hệ có unload/redeploy, cân nhắc:

- Đặt factory trong vòng đời plugin rồi giải phóng registry khi plugin dừng.
- Gắn metadata theo class bằng `ClassValue` nếu phù hợp.
- Cẩn trọng khi dùng `WeakHashMap<Class<?>, ...>` vì value có thể tham chiếu ngược tới class, làm yếu key không còn hữu ích.
- Không cache bằng duy nhất chuỗi `getName()` khi có nhiều class loader, vì cùng binary name có thể là hai runtime classes khác nhau.

Việc cache cần cân nhắc theo profiling; với số lần khởi tạo rất ít, class factory đơn giản và kiểm tra chuẩn xác thường quan trọng hơn tối ưu vi mô.

## 10. Thông báo lỗi tốt: đủ dữ kiện nhưng không lộ bí mật

Thông báo cấu hình tốt có thể có mẫu:

```text
Cannot construct report implementation com.example.PdfReport:
expected public constructor (java.lang.String)
```

Trong hệ thống thực, nên có:

- **Thao tác:** load/verify constructor/create.
- **Class:** tên đầy đủ (`type.getName()`) để phân biệt package.
- **Expected signature:** ví dụ `(java.lang.String)`, không chỉ tên method chung chung.
- **Loại lỗi:** `NoSuchMethodException`, `IllegalAccessException`, hoặc failure từ target.
- **Root cause:** giữ bằng constructor exception nhận `cause`.
- **Ngữ cảnh có kiểm soát:** operation ID, plugin/module, class loader nếu cần điều tra.

Ví dụ sử dụng `getSimpleName()` cho stdout ngắn và ổn định; khi logging production có nhiều class cùng tên, nên dùng `getName()`, kèm module/loader khi cần.

Không đưa vào thông báo/log các giá trị nhạy cảm như password, token, dữ liệu khách hàng hoặc chuỗi đầu vào bí mật chỉ để debug. Có thể nói “constructor argument validation failed” và giữ root cause trong log nội bộ, thay vì phản chiếu nguyên văn input từ người dùng qua API công khai.

### Không nuốt lỗi thành null

Tránh:

```java
try {
    return constructor.newInstance(title);
} catch (Exception ignored) {
    return null;
}
```

Đây là **phản ví dụ** trong method trả `T`. Nếu object `null` được dùng về sau, caller sẽ nhận `NullPointerException` ở nơi không liên quan, làm mất stack trace của nguyên nhân ban đầu. Hãy ném lỗi có cấu trúc hoặc dùng kết quả kiểu `Result<T, Error>` được định nghĩa rõ theo hợp đồng ứng dụng.

## 11. Những lỗi thường gặp khi thiết kế factory Reflection

**1. Dùng raw type `Constructor` hoặc ép `(T) ctor.newInstance(...)` vô điều kiện.** Raw type loại bỏ thông tin compile-time; dùng `Class<T>` và `Constructor<T>`, hoặc `asSubclass(BaseType.class)` khi type đến từ runtime name.

**2. Gọi `getDeclaredConstructor` rồi mặc định nó gọi được.** Declared lookup có thể tìm private constructor, nhưng `newInstance` vẫn kiểm tra quyền. `setAccessible(true)` không bảo đảm vượt module access và không nên dùng chỉ để “lách” contract public constructor.

**3. Nhầm lookup error với target error.** Thiếu constructor là `NoSuchMethodException` lúc resolve; lỗi do code constructor ném thường nằm trong `InvocationTargetException.getCause()`. Xử lý chúng ở hai nhánh khác nhau.

**4. Bắt `Exception` chung rồi in `"Factory failed"`.** Loại bỏ chi tiết actionable; giữ nguyên cause và tách configuration vs creation.

**5. Dùng `type.newInstance()` đã deprecated.** Dùng `getDeclaredConstructor().newInstance()` hoặc `getConstructor(...).newInstance(...)`; hợp đồng exception của `Class.newInstance()` khác, đặc biệt khi constructor ném checked exceptions.

**6. Cho caller truyền danh sách `Object... args` mà không định nghĩa signature.** Dễ xuất hiện mismatched arity, boxing, varargs hoặc ép kiểu sai. Với factory `create(String)`, caller biết chính xác input contract.

**7. Không kiểm tra `interface`/`abstract`, constructor signature và service contract trước khi phục vụ request.** Lỗi bị phát hiện muộn, khó xác định cấu hình sai. Validate tại bootstrap khi có thể.

**8. Tưởng `Class.forName` chỉ “đọc metadata”.** Overload một tham số có thể kích hoạt class initialization. Class name từ input không đáng tin cần kiểm soát.

**9. Cache factory toàn cục qua strong Class key trong hệ plugin reload.** Có thể giữ class loader cũ. Chọn ownership và lifecycle cache rõ ràng.

**10. Tự retry constructor đích sau khi nó ném exception.** Constructor có thể đã tạo side effects trước khi thất bại (mở file, ghi DB, gửi request). Chỉ retry khi action idempotent và có chính sách retry cụ thể.

**11. Nhận thông báo từ Reflection là hợp đồng ổn định.** JDK/compiler có thể thay text message; test nên kiểm tra **loại exception, cause và phần message ứng dụng tự kiểm soát**.

## 12. Bài tập mở rộng

### Bài 1 — Factory không tham số

Thử viết `TypedFactory<T>` với `getConstructor()` và `create()`, rồi so sánh với interface `Supplier<T>`. Nếu toàn bộ implementation biết lúc compile time, factory bằng method reference (`PdfReport::new` khi constructor signature phù hợp) thường đơn giản hơn Reflection.

### Bài 2 — Factory nhận nhiều tham số

Đổi contract thành `create(String title, int pages)`; tra `type.getConstructor(String.class, int.class)`. Viết test cho trường hợp class chỉ có constructor `(String, Integer)`: Reflection **không tự tìm overload đó**.

### Bài 3 — Viết assertion trên exception

Dùng JUnit hoặc test harness của dự án để kiểm tra:
- `forClass(WrongConstructor.class)` ném `FactoryConfigurationException` với cause `NoSuchMethodException`.
- `forClass(PrivateReport.class)` báo **thiếu public constructor**.
- `forClass(AbstractReport.class)` báo abstract type.
- `forClass(FailingReport.class).create("bad")` ném `FactoryCreationException` với `getCause() instanceof IllegalArgumentException`.
- `fromName("java.lang.String")` báo class không thuộc `Report`.
- `forClass(PdfReport.class).create("ok")` trả đúng `PdfReport` và nội dung mong đợi.

### Bài 4 — Đăng ký plugin theo interface

Thay nhận tên class tùy ý bằng registry `Map<String, Class<? extends Report>>` đã validate. Client chọn một ID như `"pdf"` thay vì tên Java binary class trực tiếp; phần registry có thể được xây từ `ServiceLoader` khi cần plugin registration.

## 13. Checklist trước khi dùng trong hệ thống

1. **Contract rõ:** factory tạo kiểu `T extends Report` và cần đúng public constructor `(String)`.
2. **Typed metadata:** dùng `Class<T>`, `Constructor<T>`, không dùng raw casts tùy tiện.
3. **Fail fast:** kiểm tra class cụ thể, tính abstract, constructor signature khi tạo factory.
4. **Tách lỗi:** configuration/lookup khác create/invocation và exception do constructor đích ném.
5. **Preserve cause:** luôn giữ cause khi bọc, dùng thông báo có class + signature.
6. **Không bypass access vô điều kiện:** ưu tiên public constructor và API rõ, tôn trọng JPMS.
7. **An toàn runtime class name:** validate, allowlist và quản lý class loader; không nhận tùy ý input không tin cậy.
8. **Cache có vòng đời:** tránh lookup lặp, nhưng không giữ class loader plugin vô thời hạn.
9. **Test cả đường thành công và thất bại:** chú ý primitive/wrapper, abstract, private, target runtime exception.
10. **Giữ log có ích nhưng tối thiểu:** không rò rỉ input nhạy cảm và không nuốt root cause.

## 14. Tóm tắt

- `Class<T>` + `Constructor<T>` là nền tảng để tạo **factory Reflection có kiểu trả về**.
- `getConstructor(String.class)` ràng buộc rõ **public constructor với chính xác một tham số String**, và kiểm tra sớm ngay lúc cấu hình factory.
- `Constructor.newInstance` có thể bọc exception từ **thân constructor đích** trong `InvocationTargetException`; giữ `getCause()` khi chuyển sang exception ứng dụng.
- Thông báo lỗi tốt phân biệt **lỗi class/contract/constructor lookup** với **lỗi tạo instance/target logic**, không chỉ nói “Reflection failed”.
- Sau khi tạo thành công, gọi qua `Report`/`PdfReport` bình thường. Reflection nên nằm trong lớp factory nhỏ, có trách nhiệm và test rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-practice-004">Đọc object graph có kiểm soát vòng tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc object graph bằng Reflection và kiểm soát vòng tham chiếu

Một **object graph** (đồ thị đối tượng) gồm các đối tượng Java là **đỉnh**, còn tham chiếu từ field, phần tử mảng hoặc collection là **cạnh**. Một đối tượng có thể được nhiều đối tượng khác trỏ tới, tự trỏ tới chính mình, hoặc nằm trong vòng tham chiếu như `A.next = B` và `B.next = A`.

Khi viết trình duyệt object graph, debugger, công cụ tạo snapshot, serializer hay bộ chẩn đoán sử dụng Reflection, không thể đơn giản gọi đệ quy trên mọi field đến vô hạn. Phải đồng thời bảo vệ **tính đúng đắn của định danh đối tượng**, **độ sâu**, **số node/phần tử**, **quyền truy cập**, **tính ổn định** và **dữ liệu nhạy cảm**.

Nguyên tắc quan trọng nhất: dùng **định danh tham chiếu (`==`)** thay vì `equals()` để nhận ra một đối tượng đã gặp; ghi nhận nó **trước khi** đi sâu vào các field. Khi thấy lại đối tượng đó, chỉ in tham chiếu đến node đã ghi nhận, không đi tiếp.

## 1. Đồ thị khác cây như thế nào?

Ví dụ:

~~~text
A ──next──> B
^           |
|           |
+──next─────+
A ──items[0]──> B
~~~

Ở đây:

- `A` và `B` là **hai instance** độc lập.
- `A.next` và `A.items[0]` cùng tham chiếu đến **một instance B**. Đây là **shared reference/alias**.
- `B.next` tham chiếu ngược đến `A`. Đây là một **chu trình**.
- Nếu chỉ xem từng cạnh riêng lẻ rồi gọi đệ quy, traversal sẽ lặp mãi `A → B → A → B ...`.

Cần phân biệt **đã từng gặp** với **nằm trong chu trình hiện tại**:

| Trạng thái | Ý nghĩa |
| --- | --- |
| Chưa gặp instance | Có thể cấp ID và đi sâu nếu còn ngân sách |
| Đã gặp trong đường đi đệ quy hiện tại | Có đường vòng (*cycle*) quay lại node đang xử lý |
| Đã gặp nhưng không ở đường đi hiện tại | Tham chiếu dùng chung (*alias*) đến node đã xử lý |
| Chạm ngưỡng độ sâu/số node | Không kết luận phần còn lại có hay không có chu trình; chỉ đánh dấu **bị cắt** |

Với mục tiêu **in đồ thị không lặp và giữ cấu trúc chia sẻ**, một bảng `seen` toàn cục cho *một lượt duyệt* là đủ: mọi lần gặp lại đều in `ref #id`. Nếu muốn **phân loại riêng cycle và alias**, cần thêm tập các node *đang ở trên stack* (thường gọi `active`, `gray` hay DFS recursion stack). Bài thực hành bên dưới chọn biểu diễn `ref #id` để bảo toàn identity đơn giản; không gọi tất cả tham chiếu lặp là “cycle”.

## 2. Vì sao phải dùng `IdentityHashMap`?

Có hai khái niệm dễ bị nhầm:

- **Bằng nhau về nội dung:** `x.equals(y)` có thể là `true` ngay cả khi `x` và `y` là hai instance khác nhau. Ngược lại, một số lớp định nghĩa `equals()`/`hashCode()` dựa trên dữ liệu thay đổi được.
- **Cùng một đối tượng:** `x == y`. Đây là identity cần cho đồ thị object, vì hai instance có nội dung giống nhau vẫn là hai node riêng.

Nếu dùng `HashSet<Object> visited` hoặc `HashMap<Object, Integer>`, Java gọi `equals()` và `hashCode()` của chính đối tượng cần duyệt. Điều này có thể gây kết luận sai, side effect hoặc đệ quy vô hạn nếu hai phương thức ấy lại duyệt graph.

`IdentityHashMap<Object, Integer> seen` sử dụng **reference equality (`==`)** cho khóa. Nó tránh phụ thuộc vào cài đặt `equals()`/`hashCode()` của object.

~~~java
IdentityHashMap<Object, Integer> seen = new IdentityHashMap<>();
seen.put(node, 1);
Integer previousId = seen.get(node); // 1 khi cùng instance node
~~~

Đoạn trên chỉ minh họa cách dùng trong một method; cần `import java.util.IdentityHashMap;`. Không dùng `System.identityHashCode(obj)` làm **ID duy nhất** vì hash có thể va chạm. Trong ví dụ hoàn chỉnh, ta cấp ID số nguyên tăng dần theo thứ tự thăm và lưu trong `IdentityHashMap`.

**Thứ tự đúng:** khi thấy node mới, cấp ID và đưa vào `seen` **trước khi** duyệt children. Nếu chờ đến khi xử lý xong children mới đánh dấu, chu trình `A → B → A` vẫn có thể lặp vô hạn.

## 3. Quy định phạm vi và ngân sách của công cụ

Trước khi quét, phải quyết định những gì được phép đọc. Ví dụ của bài áp dụng các chính sách rõ ràng:

1. **Đối tượng lá:** `null`, `String`, `Number`, `Boolean`, `Character`, `enum`, `Class` được in giá trị, không mở sâu các field. Có thể mở rộng tập leaf theo domain; trong môi trường không tin cậy, ngay cả `toString()` của một subclass `Number` có thể gây side effect, nên chỉ in các kiểu được phép rõ ràng.
2. **Mảng:** dùng `Array.getLength`/`Array.get` để đọc cả mảng primitive và reference. Cắt sau `maxArrayItems` phần tử.
3. **Đối tượng application:** duyệt instance fields từ class tới superclass, bỏ `static` và `synthetic`; gắn **declaring class** vào tên field để phân biệt field bị hide bởi lớp con.
4. **Đối tượng JDK không phải leaf/mảng:** trong demo, dừng như node **opaque** và không phản xạ vào internal fields thuộc package `java.*`. Đây là chính sách mẫu, không thay thế allowlist package/type cần có cho production.
5. **Quyền đọc:** thử `Field.canAccess(receiver)`; khi chưa có quyền, `trySetAccessible()` chỉ được thực hiện nếu đó là chính sách cho phép. Nếu vẫn không được, in `<inaccessible>`. Không giả định module nào cũng cho deep reflection.
6. **Giới hạn:** `maxDepth` giới hạn số lớp lồng nhau, `maxNodes` giới hạn số object/array mới được mở, `maxArrayItems` giới hạn số phần tử mảng được xem.
7. **Thứ tự:** sắp xếp field theo tên rồi theo declaring class. `getDeclaredFields()` không cam kết thứ tự; không dùng kết quả tự nhiên của mảng để xây snapshot có tính lặp lại.
8. **Không gọi getter tự động:** ví dụ chỉ đọc field. Getter và `toString()` tùy ý có thể chạy logic nghiệp vụ, thực hiện I/O, thay đổi trạng thái hoặc ném exception.

Lưu ý: **giới hạn độ sâu/số node/phần tử chưa phải sandbox chống dữ liệu thù địch**. Một class có quá nhiều field, một lời gọi `toString()` không an toàn, getter hoặc traversal collection tùy ý vẫn có thể tốn tài nguyên. Production cần thêm ngân sách thời gian, số field, độ dài chuỗi, bytes đầu ra, cancellation và allowlist adapters.

## 4. Ví dụ Java 17+ đầy đủ: `IdentityHashMap`, mảng và cycle

Tạo một tệp **`ObjectGraphDemo.java`** và sao chép toàn bộ chương trình:

~~~java
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;

public class ObjectGraphDemo {
    private static final class Node {
        private final String name;
        private Node next;
        private Object[] items;

        private Node(String name) { this.name = name; }
    }

    private static final class Walker {
        private final IdentityHashMap<Object, Integer> seen = new IdentityHashMap<>();
        private final int maxDepth;
        private final int maxNodes;
        private final int maxArrayItems;

        private Walker(int maxDepth, int maxNodes, int maxArrayItems) {
            this.maxDepth = maxDepth;
            this.maxNodes = maxNodes;
            this.maxArrayItems = maxArrayItems;
        }

        private static boolean leaf(Class<?> type) {
            return type.isPrimitive()
                    || type == String.class
                    || Number.class.isAssignableFrom(type)
                    || type == Boolean.class
                    || type == Character.class
                    || type.isEnum()
                    || type == Class.class;
        }

        private static List<Field> fields(Class<?> type) {
            List<Field> result = new ArrayList<>();
            for (Class<?> c = type; c != null && c != Object.class;
                    c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    int modifiers = field.getModifiers();
                    if (!Modifier.isStatic(modifiers) && !field.isSynthetic()) {
                        result.add(field);
                    }
                }
            }
            result.sort(Comparator.comparing(Field::getName)
                    .thenComparing(f -> f.getDeclaringClass().getName()));
            return result;
        }

        private void visit(String label, Object value, int depth) {
            String pad = "  ".repeat(depth);
            if (value == null) {
                System.out.println(pad + label + ": null");
                return;
            }

            Class<?> type = value.getClass();
            if (leaf(type)) {
                System.out.println(pad + label + ": " + value);
                return;
            }

            Integer id = seen.get(value);
            if (id != null) {
                System.out.println(pad + label + ": ref #" + id);
                return;
            }
            if (depth > maxDepth) {
                System.out.println(pad + label + ": <max depth>");
                return;
            }
            if (seen.size() >= maxNodes) {
                System.out.println(pad + label + ": <max nodes>");
                return;
            }

            id = seen.size() + 1;
            seen.put(value, id); // Must precede exploring children.
            System.out.println(pad + label + ": #" + id + " " + type.getSimpleName());

            if (type.isArray()) {
                int length = Array.getLength(value);
                for (int i = 0; i < Math.min(length, maxArrayItems); i++) {
                    visit("[" + i + "]", Array.get(value, i), depth + 1);
                }
                if (length > maxArrayItems) {
                    System.out.println(pad + "  ... <array truncated>");
                }
                return;
            }

            if (type.getPackageName().startsWith("java.")) {
                System.out.println(pad + "  <opaque JDK object>");
                return;
            }

            for (Field field : fields(type)) {
                String fieldLabel = field.getDeclaringClass().getSimpleName()
                        + "." + field.getName();
                try {
                    if (!field.canAccess(value) && !field.trySetAccessible()) {
                        System.out.println(pad + "  " + fieldLabel + ": <inaccessible>");
                        continue;
                    }
                    visit(fieldLabel, field.get(value), depth + 1);
                } catch (IllegalAccessException | RuntimeException ex) {
                    System.out.println(pad + "  " + fieldLabel + ": <inaccessible>");
                }
            }
        }
    }

    public static void main(String[] args) {
        Node a = new Node("A");
        Node b = new Node("B");
        a.next = b;
        b.next = a;
        a.items = new Object[] {b, "tail"};

        System.out.println("== graph ==");
        new Walker(4, 20, 10).visit("root", a, 0);

        System.out.println("== limits ==");
        new Walker(0, 1, 10).visit("root", a, 0);
    }
}
~~~

Trong terminal tại thư mục chứa tệp, dùng JDK 17+:

~~~bash
javac --release 17 ObjectGraphDemo.java
java ObjectGraphDemo
~~~

Kết quả được kiểm chứng bằng biên dịch và chạy Java:

~~~text
== graph ==
root: #1 Node
  Node.items: #2 Object[]
    [0]: #3 Node
      Node.items: null
      Node.name: B
      Node.next: ref #1
    [1]: tail
  Node.name: A
  Node.next: ref #3
== limits ==
root: #1 Node
  Node.items: <max depth>
  Node.name: A
  Node.next: <max depth>
~~~

Chương trình này sử dụng `Node` là nested class trong cùng top-level class với `Walker`; theo quy tắc **nestmate** của Java 17+, code có thể đọc các field private của `Node` khi đủ điều kiện. Với lớp của thư viện khác hoặc named module không mở package, việc đọc private có thể bị chặn và output `<inaccessible>` là kết quả hợp lệ.

### Giải thích output theo thứ tự

- `root: #1 Node`: lần đầu gặp instance `A`, cấp ID 1 và đưa ngay vào `seen`.
- `Node.items: #2 Object[]`: mảng là **một đối tượng** có identity riêng, được đánh số #2 và duyệt các phần tử.
- `[0]: #3 Node`: đối tượng `B` chưa gặp nên nhận ID 3.
- `Node.next: ref #1` bên dưới B: `B.next` quay về A đã ghi nhận, nên dừng tại tham chiếu này và không đi vào vô hạn.
- `Node.next: ref #3` bên dưới A: `A.next` dùng lại đúng B; không tạo thêm một bản B. Đây là shared reference; không nhất thiết là một vòng mới.
- Lượt `new Walker(0, 1, 10)` dùng **bảng visited mới**, chỉ mở root ở depth 0; các object ở depth 1 bị cắt bằng `<max depth>`. Field leaf `Node.name` vẫn đọc được giá trị `A`.

Điểm thiết kế quan trọng: `seen` là state của **một lần walk**, không dùng `static` hoặc chia sẻ giữa các request. Một walker mới bắt đầu với namespace ID mới; đồng thời `IdentityHashMap` không an toàn khi nhiều thread cùng thay đổi nó.

## 5. Phân biệt kiểm soát vòng tham chiếu và kiểm soát giới hạn

Một đoạn đệ quy kiểu `walk(field.get(value))` chỉ an toàn khi biết graph là cây không chu trình. Trong graph tổng quát, phải theo dõi identity và áp giới hạn:

| Biện pháp | Ngăn được | Chưa giải quyết |
| --- | --- | --- |
| `IdentityHashMap` visited | Duyệt lặp cùng instance, bao gồm cycle/alias | Graph có vô số node khác nhau hoặc traversal trên nguồn dữ liệu không ổn định |
| Đánh dấu *trước khi* đi vào children | Cycle ngay lập tức `A → A` hoặc `A → B → A` | Hạn mức bộ nhớ/thời gian |
| `maxDepth` | Stack quá sâu và traversal theo chiều sâu quá dài | Graph rất rộng ở độ sâu nhỏ |
| `maxNodes` | Mở quá nhiều object/array độc lập | Mỗi object có quá nhiều field, string quá lớn |
| `maxArrayItems` | Mảng lớn gây quá nhiều lần đọc phần tử | Collection/map lớn và dữ liệu ngoài array |
| Lọc field và kiểu | Truy cập nhầm static/synthetic, JDK internals | Private access hợp lệ, dữ liệu nhạy cảm, hiệu ứng phụ |
| Sắp xếp field | Snapshot lệ thuộc thứ tự mảng Reflection | Graph thay đổi trong lúc duyệt do concurrent mutation |

**Thứ tự các kiểm tra cũng có ý nghĩa:** trong mã, null/leaf được xử lý trước; đối tượng đã nằm trong `seen` được in `ref`; node mới sau đó mới bị xét giới hạn depth/node. Vì thế một tham chiếu lặp tới node đã mở có thể vẫn hiện `ref #id` ngay cả khi sâu hơn `maxDepth`, trong khi node chưa mở tại độ sâu đó hiện `<max depth>`.

Các chuỗi `<max depth>`/`<max nodes>` có nghĩa **“chưa duyệt”**, không có nghĩa con trỏ null hoặc object không tồn tại. Đừng dùng snapshot bị cắt làm căn cứ khẳng định graph không có chu trình.

### Muốn phân biệt cycle với alias thật sự

Mở rộng DFS bằng hai cấu trúc:

- `seen`: instance đã được phát hiện trong cả lượt duyệt (để cấp ID và ngăn duyệt trùng).
- `active`: instance đang nằm trên đường đệ quy hiện tại.

Khi duyệt child, nếu nó ở `active` thì đó là **back-edge/cycle**; nếu chỉ ở `seen` nhưng không ở `active` thì đó là **shared reference**. Xóa instance khỏi `active` trong `finally` khi hoàn tất duyệt nhánh, nhưng **không xóa khỏi `seen`**. Khi traversal bị cắt vì giới hạn, phải giữ trạng thái “truncated” để không suy luận thiếu dữ liệu là bằng chứng về việc không có vòng.

Với snapshot/debug output, chỉ cần `ref #id` thường đã đủ; việc phân biệt alias/cycle tùy mục tiêu ứng dụng.

## 6. Mảng, collection, map và kiểu đặc biệt

Ví dụ hỗ trợ **mảng** trực tiếp bằng `java.lang.reflect.Array`:

- `int[]` chứa primitive nhưng toàn bộ mảng vẫn là **object** có identity; `Array.get(array, i)` trả giá trị boxed như `Integer`.
- `Object[]` có thể chứa null, phần tử chia sẻ instance, hoặc tham chiếu ngược về chính mảng đó. `seen` xử lý được nếu đánh dấu mảng trước khi duyệt.
- Với mảng nhiều chiều, mỗi mảng con có identity riêng. Không giả định mọi hàng có cùng độ dài.

**Collection và map cần adapter chuyên biệt**, chẳng hạn duyệt `List` theo index, `Map` theo key/value hoặc `Optional` theo giá trị. Không nên tự do reflect các field nội bộ của `ArrayList`/`HashMap`: cấu trúc bên trong là chi tiết implementation và có thể bị JPMS chặn.

Khi thêm adapter:

1. Áp dụng **cùng một bảng identity** cho container và các phần tử; container cũng có thể tham chiếu chính nó (ví dụ `list.add(list)`).
2. Đặt hạn mức phần tử để tránh dữ liệu quá lớn.
3. Định nghĩa thứ tự biểu diễn; `HashMap`/`HashSet` không cam kết insertion order. Nếu sắp xếp key, chỉ dùng comparator an toàn cho key tương ứng và tránh gọi tùy tiện `toString`/`compareTo` trên dữ liệu không tin cậy.
4. Không gọi các getter, lazy collections hoặc iterators có side effect trong snapshot chẩn đoán nếu chưa có chính sách/ủy quyền rõ.
5. Đặt chính sách với `WeakReference`, `AtomicReference`, proxy, records và ORM lazy proxies; tham chiếu logic có thể khác field vật lý.

Các đối tượng như `Thread`, `ClassLoader`, `Socket` hoặc dữ liệu nội bộ JDK không phải mặc nhiên là những nút nên mở sâu; tốt hơn là in kiểu, trạng thái được cho phép hoặc marker `opaque`.

## 7. Quyền truy cập Reflection và bảo vệ thông tin

`getDeclaredFields()` trả metadata của field private/protected/package-private nhưng **không bảo đảm caller có quyền đọc giá trị**. Mã có thể cần `Field.canAccess(receiver)`, `trySetAccessible()` và xử lý `IllegalAccessException`; trong named modules, package chưa `opens` cho caller có thể ngăn `trySetAccessible()`. Không ép tất cả package dùng `--add-opens` chỉ để debugger chạy được.

Trong ứng dụng thực, cân nhắc chính sách **public only** hoặc explicit allowlist thay cho việc luôn thử nâng quyền. Ví dụ này có `trySetAccessible()` để minh họa kỹ thuật trong phạm vi được phép; không có nghĩa mọi field private đều nên bị đọc trong hệ thống sản xuất.

Cần **redact hoặc bỏ qua** mật khẩu, token, khóa bí mật, thông tin cá nhân, connection strings, nội dung body HTTP nhạy cảm, bất kể field có modifier gì. Việc reflection đọc được dữ liệu không tạo ra quyền công bố dữ liệu đó.

Một cây output an toàn có thể dùng các marker:

~~~text
user.password: <redacted>
payment.token: <redacted>
jdkObject: #12 SomeJdkType
  <opaque JDK object>
internalField: <inaccessible>
largeGraph: <max nodes>
~~~

Khi logging, cũng cần chặn escape/control characters, giới hạn độ dài string và tổng dung lượng output; ví dụ minh họa chưa thực hiện tất cả các biện pháp này.

## 8. Tính nhất quán và giới hạn thực tế

- **Độ phức tạp:** nếu xét mỗi node/cạnh không bị cắt đúng một lần, duyệt graph có độ phức tạp gần `O(V + E)` cho phần được duyệt, ngoài chi phí phản xạ/sắp xếp field. `seen` cần `O(V)` bộ nhớ theo số instance đã gặp. Bộ giới hạn chỉ đặt trần cho một số tài nguyên cụ thể, không tự chứng minh toàn bộ công cụ đạt giới hạn thời gian.
- **Mutable graph:** graph thay đổi trong lúc đang đọc có thể cho snapshot bất nhất, thậm chí gặp null/giá trị khác giữa hai lần truy cập. `IdentityHashMap` không tạo ra atomic snapshot hoặc thread safety cho object graph. Muốn snapshot nhất quán, cần cơ chế snapshot/locking thích hợp hoặc đọc immutable DTO.
- **Hiệu ứng phụ:** `Field.get` không gọi getter, nhưng xử lý một object bằng `toString()`, iterator hoặc adapter do người dùng định nghĩa có thể chạy logic; cần kiểm soát. Trong demo, leaf `Number` được in bằng phép nối chuỗi; ở hệ thống không tin cậy nên thu hẹp whitelist và chuẩn hóa giá trị.
- **Class loader/proxy:** hai object từ các class loader khác nhau có thể có cùng class name; không nên nhận diện object chỉ bằng tên class hoặc `System.identityHashCode`. Identity dựa trên reference là yếu tố quyết định.
- **Field hiding:** hai field tên giống nhau trong cha/con là hai vị trí lưu trữ độc lập; demo gắn `getDeclaringClass()` vào nhãn để không nhập nhằng. Ở production nên dùng cả tên package/declaring class nếu tên class đơn giản có thể trùng nhau.
- **Ngoại lệ:** `trySetAccessible()` có thể bị từ chối, `Field.get` có thể ném `IllegalAccessException`, phản xạ trên kiểu lạ hoặc class bị nạp động có thể thất bại. Không được nuốt lỗi rồi coi mọi field bị lỗi là null.
- **Truy cập kiểu primitive:** Reflection có thể trả wrapper khi dùng `Field.get`; nếu cần phân biệt primitive declared type hãy xét `field.getType()` thay vì chỉ `value.getClass()`.
- **Bảo toàn output:** khi serializing JSON, không thể tự biểu diễn chu trình bằng đối tượng lồng nhau thông thường. Cần schema xác định (`$id`/`$ref` hoặc equivalent), hoặc chính sách từ chối cycle rõ ràng.

## 9. Các câu hỏi tự kiểm tra

1. Nếu hai instance có `equals() == true` nhưng `a != b`, có nên coi chúng là cùng một node khi duyệt object graph? **Không**; dùng identity.
2. Vì sao phải `seen.put(value, id)` trước khi duyệt field con? Để chu trình quay lại node hiện tại nhận ra ngay.
3. Nếu `A.child` và `B.child` trỏ tới cùng `C` nhưng graph không có cycle, lần xuất hiện thứ hai của `C` nên là gì? **`ref` đến cùng ID**, vì đây là alias.
4. Nếu một object không thể đọc private field do module chưa open, có phải field ấy không tồn tại? **Không**; có metadata nhưng không có quyền truy cập giá trị.
5. Nếu dừng ở `<max depth>`, có thể khẳng định subgraph đã bị cắt không có chu trình không? **Không**.
6. Nếu có `List` chứa chính nó, nên xử lý ra sao khi thêm List adapter? Cấp ID cho List, đánh dấu vào `seen` trước, rồi duyệt phần tử và gặp lại dưới dạng `ref`.

**Ghi nhớ:** đọc object graph bằng Reflection là **duyệt một đồ thị theo identity với giới hạn và quyền truy cập cụ thể**. `IdentityHashMap` + đánh dấu sớm ngăn vòng tham chiếu; depth/node/array budgets giới hạn phạm vi; chính sách kiểu, field, access, redaction và snapshot nhất quán quyết định công cụ có thực sự an toàn và đáng tin hay không.

**Tài liệu Java SE 21:** [`IdentityHashMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/IdentityHashMap.html), [`Class.getDeclaredFields`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getDeclaredFields()), [`Field`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html), [`Array`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Array.html), [`AccessibleObject`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AccessibleObject.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-practice-005">Xây dựng bộ ánh xạ dữ liệu nhỏ qua accessor hoặc record component</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: Xây dựng bộ ánh xạ dữ liệu nhỏ qua accessor hoặc record component

## 1. Bài toán và kết quả mong muốn

Giả sử ứng dụng cần chuyển một object Java thành một bảng dữ liệu có dạng `Map<String, Object>`. Bảng đó có thể phục vụ logging có kiểm soát, tạo dữ liệu hiển thị, chuyển sang một DTO khác hoặc làm bước đầu cho serialization. Với các lớp đã biết từ trước, một method chuyển đổi tường minh thường là lựa chọn đơn giản nhất; bài thực hành này tập trung vào trường hợp **cần khám phá cấu trúc ở runtime bằng Reflection**, nhưng vẫn phải giới hạn phạm vi và xử lý lỗi rõ ràng.

Ta sẽ viết một bộ ánh xạ **một chiều**:

```text
Java object
    |
    |-- record?  --> RecordComponent[] --> getAccessor() --> Method.invoke(...)
    |
    +-- JavaBean? --> PropertyDescriptor[] --> getReadMethod() --> Method.invoke(...)
    |
    v
Map<String, Object>  (khóa = tên thành phần/property, giá trị = kết quả accessor)
```

Đầu vào ví dụ gồm:

- `Invoice` là **record** với hai thành phần `code` và `total`.
- `CustomerView` là **class kiểu JavaBean đọc được** với getter `getName()` và `isActive()`.
- Bộ ánh xạ nhận **duy nhất hai kiểu đã được cho phép**; không tự đọc bất kỳ class nào do người dùng gửi tên.
- Bản đồ kết quả sắp xếp theo tên khóa cho dễ kiểm thử và không cho sửa qua API `Map` trả về.

Kết quả dự kiến:

```text
record = {code=INV-7, total=80}
bean = {active=true, name=Lan}
```

**Phạm vi cố ý nhỏ:** chỉ đọc thuộc tính top-level, giữ nguyên giá trị kiểu Java; không đọc trực tiếp private field, không dựng object từ map, không đi vào object lồng nhau, không xử lý cycle, không tự động chuyển kiểu và **không tạo JSON**. `Map.toString()` là biểu diễn in thử trong Java, không phải JSON hợp lệ.

## 2. Hiểu hai kiểu accessor trước khi viết mapper

### Record component

Từ Java 16, record là cú pháp mô tả một dạng class được thiết kế để mang dữ liệu với tập component xác định. Ví dụ:

```java
public record Invoice(String code, int total) {}
```

Compiler cung cấp các accessor tương ứng: `code()` và `total()`. Chúng **không phải JavaBeans getter tên `getCode()` hay `getTotal()`**. Vì vậy không nên mặc định JavaBeans Introspector sẽ tìm đúng property của record.

Reflection cung cấp:

- `type.isRecord()`: kiểm tra class có phải record.
- `type.getRecordComponents()`: lấy các `RecordComponent` được khai báo, theo thứ tự component trong header.
- `component.getName()`: tên component, ví dụ `"code"`.
- `component.getType()`: kiểu khai báo, ví dụ `String.class` hoặc `int.class`.
- `component.getAccessor()`: đối tượng `Method` của accessor tương ứng; có thể gọi bằng `Method.invoke(receiver)`.

Ví dụ khái niệm:

```java
RecordComponent[] parts = Invoice.class.getRecordComponents();
for (RecordComponent part : parts) {
    Method accessor = part.getAccessor();
    // Object value = accessor.invoke(invoice);
}
```

Đây là **đoạn mã minh họa cần import và khai báo ngữ cảnh**, không phải file chạy riêng. Kiểu primitive như `int` được boxing thành `Integer` khi lưu dưới kiểu `Object` trong map.

Record thường tạo các field final/private và accessor public; mapper sử dụng accessor **thay vì** bật quyền truy cập private field. Tuy nhiên tính “bất biến nông” của record không bảo đảm dữ liệu tham chiếu bên trong bất biến: nếu component chứa `List` có thể thay đổi, map sẽ giữ reference đến cùng đối tượng list.

### JavaBean property

Với class thông thường:

```java
public static final class CustomerView {
    private final String name;
    private final boolean active;

    public CustomerView(String name, boolean active) {
        this.name = name;
        this.active = active;
    }

    public String getName() { return name; }
    public boolean isActive() { return active; }
}
```

Theo quy ước JavaBeans:

- `getName()` đại diện cho property `name`.
- `isActive()` đại diện cho property boolean `active`.
- Property chỉ đọc **vẫn hợp lệ** dù class không có setter.
- Method tùy ý `calculate()` không tự nhiên là JavaBean property.
- `getClass()` được kế thừa từ `Object`; không nên đưa metadata class nội bộ vào bản đồ dữ liệu.

`java.beans.Introspector.getBeanInfo(type, Object.class)` khám phá thuộc tính theo quy ước và loại bỏ phần thông tin thuộc superclass `Object`. Mỗi `PropertyDescriptor` có `getName()`, `getPropertyType()` và `getReadMethod()`. Method đọc có thể null nếu property chỉ ghi; mapper bỏ qua trường hợp đó.

Khác với record component, JavaBean là một **quy ước đặt tên và metadata**, không phải một cú pháp class bắt buộc. Tên property không nhất thiết giống tên field: một getter có thể tính giá trị thay vì đọc trực tiếp field, và có thể có tác dụng phụ hoặc ném ngoại lệ.

**Thông tin module:** `java.beans.Introspector` thuộc module JDK `java.desktop`, không thuộc `java.base`. Ở ứng dụng có file `module-info.java`, cần `requires java.desktop;` khi dùng API JavaBeans này; ứng dụng trên classpath thông thường với một JDK đầy đủ không cần khai báo đó.

## 3. Hợp đồng cụ thể của mini mapper

Ta chọn interface sử dụng đơn giản:

```java
Map<String, Object> toMap(Object source);
```

Hợp đồng:

1. `source == null` bị từ chối bằng `NullPointerException` với thông báo `"source"`.
2. Kiểu source được kiểm tra theo **allowlist** gồm `Invoice.class` và `CustomerView.class`; kiểu khác bị từ chối bằng `IllegalArgumentException`. Điều này giữ Reflection trong phạm vi kiểm soát.
3. Với record, chỉ lấy component do `getRecordComponents()` trả về và gọi accessor tương ứng.
4. Với JavaBean, chỉ lấy readable property từ `PropertyDescriptor` do Introspector trả về, không tìm field private.
5. Getter có thể thất bại. Mapper **không nuốt lỗi** mà báo `IllegalStateException` và giữ `cause` gốc.
6. Giá trị `null` được phép giữ trong map vì dùng `TreeMap` (không dùng `Map.copyOf`, vốn không cho phép null).
7. Kết quả được bao bởi `Collections.unmodifiableMap(...)`. Caller không thêm, xóa hoặc thay entry qua map đó; điều này không làm bất biến sâu các object được lưu làm value.

Bài này dùng khóa tên property (`String`) nên không có key null. Thứ tự key của `TreeMap` là thứ tự tự nhiên của chuỗi, không phải thứ tự khai báo record hay thứ tự Reflection/Introspector trả về.

## 4. Chương trình hoàn chỉnh chạy được trên Java 17+

Tạo file **`TinyMapperDemo.java`**, nội dung:

```java
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class TinyMapperDemo {
    public record Invoice(String code, int total) {}

    public static final class CustomerView {
        private final String name;
        private final boolean active;

        public CustomerView(String name, boolean active) {
            this.name = name;
            this.active = active;
        }

        public String getName() {
            return name;
        }

        public boolean isActive() {
            return active;
        }
    }

    public static Map<String, Object> toMap(Object source) {
        Objects.requireNonNull(source, "source");
        Class<?> type = source.getClass();

        // Không thực thi getter tùy ý trên class từ nguồn không tin cậy.
        if (type != Invoice.class && type != CustomerView.class) {
            throw new IllegalArgumentException(
                    "Unsupported: " + type.getName());
        }

        Map<String, Object> result = new TreeMap<>();

        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                result.put(
                        component.getName(),
                        read(component.getAccessor(), source));
            }
        } else {
            try {
                for (PropertyDescriptor property :
                        Introspector.getBeanInfo(type, Object.class)
                                .getPropertyDescriptors()) {
                    Method accessor = property.getReadMethod();
                    if (accessor != null) {
                        result.put(property.getName(),
                                read(accessor, source));
                    }
                }
            } catch (IntrospectionException ex) {
                throw new IllegalStateException(
                        "Invalid bean metadata", ex);
            }
        }

        return Collections.unmodifiableMap(result);
    }

    private static Object read(Method accessor, Object receiver) {
        try {
            return accessor.invoke(receiver);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(
                    "Inaccessible: " + accessor.getName(), ex);
        } catch (InvocationTargetException ex) {
            throw new IllegalStateException(
                    "Accessor failed: " + accessor.getName(),
                    ex.getCause());
        }
    }

    public static void main(String[] args) {
        System.out.println("record = "
                + toMap(new Invoice("INV-7", 80)));
        System.out.println("bean = "
                + toMap(new CustomerView("Lan", true)));
    }
}
```

Biên dịch và chạy từ đúng thư mục chứa file:

```bash
javac -encoding UTF-8 TinyMapperDemo.java
java TinyMapperDemo
```

Kết quả:

```text
record = {code=INV-7, total=80}
bean = {active=true, name=Lan}
```

Vì sao kết quả như trên?

**Với record** `new Invoice("INV-7", 80)`: `isRecord()` trả `true`, vòng lặp lấy hai component `code` và `total`. `getAccessor()` lần lượt dẫn tới `code()` trả `"INV-7"` và `total()` trả `80`. `TreeMap` giữ key theo alphabet nên hiển thị `code` trước `total`.

**Với JavaBean** `new CustomerView("Lan", true)`: kiểu này không phải record nên dùng `Introspector`. `getName()` được đọc thành `name=Lan` và `isActive()` thành `active=true`. Ta chỉ thu nhận readable properties, không thu `class` từ `Object.getClass()` nhờ dùng stop class `Object.class`. `TreeMap` sắp xếp `active` trước `name`.

**Tại sao `read(...)` dùng hai catch khác nhau?** Khi caller không đủ quyền, `Method.invoke` có thể ném `IllegalAccessException`. Khi accessor **được gọi** và tự ném exception, Reflection bọc exception ấy trong `InvocationTargetException`; `getCause()` truy ra lỗi bên trong. Mapper bọc lại bằng `IllegalStateException`, thay vì trả một map thiếu key khiến bug bị che giấu.

Các class trong ví dụ là nested type public và các accessor public; không cần `setAccessible(true)`. Với class/module khác, package visibility/module export có thể làm cho một method trông public nhưng không truy cập được từ caller. Đừng biến `setAccessible(true)` thành giải pháp mặc định.

## 5. Đọc kỹ luồng ánh xạ

### Bước 1: quyết định loại metadata

`type.isRecord()` là kiểm tra chuyên biệt dành cho record. Khi `true`, dùng `getRecordComponents()` thay vì đoán tên `getXxx()`. Khi `false`, bài này dùng quy ước JavaBeans cho một kiểu cho phép.

Đây là **hai đường ánh xạ riêng**. Không nên trộn một cách tự phát, ví dụ với record vừa lấy `getRecordComponents()` vừa quét `getMethods()` để tự đoán thêm thuộc tính: dễ gây key trùng hoặc gọi những method không thuộc hợp đồng dữ liệu.

### Bước 2: chỉ giữ accessor có thể gọi

Với record, `RecordComponent.getAccessor()` trả method accessor tương ứng. Với JavaBean, `PropertyDescriptor.getReadMethod()` có thể null nên cần kiểm tra.

Không trực tiếp lấy mọi `Method` theo `getMethods()` rồi tự bỏ tiền tố `get` vì dễ xử lý sai `isXxx`, thuộc tính viết hoa, property chỉ ghi, overload và các chi tiết quy ước JavaBeans. Introspector đã thực hiện một phần quan trọng của việc phân giải quy ước này.

### Bước 3: invoke với receiver đúng

Cả accessor record và JavaBean getter ở đây là **instance method không có đối số**:

```java
Object value = accessor.invoke(source);
```

Đoạn này minh họa khi đã có biến `accessor: Method` và `source`. Đối số `source` là receiver, không phải tham số getter. Nếu receiver sai kiểu, invocation sẽ lỗi. Giá trị kiểu primitive như `int`/`boolean` được boxing khi trả về dưới dạng `Object`.

### Bước 4: tạo map theo tên thuộc tính

Tên của record component đến từ `getName()`. Tên JavaBean property đến từ `PropertyDescriptor.getName()`, **không cần** tự viết thuật toán xóa `get`/`is` rồi lower-case.

Map sử dụng `TreeMap<String,Object>` để có thứ tự hiển thị ổn định. Khi serializer thực tế cần giữ thứ tự khai báo record, `LinkedHashMap` là lựa chọn hợp lý hơn; đầu ra của chương trình mẫu thì chủ ý **sắp xếp alphabet** để làm test rõ ràng.

### Bước 5: trả map không cho sửa trực tiếp

`Collections.unmodifiableMap(result)` làm người dùng không thể thao tác `put/remove` trên map trả về. Lưu ý nếu các value là list/map/object mutable, việc mutate bên trong value vẫn có thể xảy ra. Nếu cần snapshot bất biến sâu để chia sẻ đa luồng hoặc lưu trữ lâu dài, phải định nghĩa cách copy/serialize phù hợp, không mặc định wrapper map làm được việc đó.

## 6. Thử lỗi và kiểm tra hợp đồng

Với chương trình hoàn chỉnh ở trên, có thể thêm các lệnh sau **vào cuối `main`** để kiểm tra nhánh lỗi:

```java
try {
    toMap(new Object());
} catch (IllegalArgumentException ex) {
    System.out.println("unsupported = true");
}

try {
    toMap(null);
} catch (NullPointerException ex) {
    System.out.println("null = true");
}

Map<String, Object> immutable = toMap(new Invoice("INV-8", 90));
try {
    immutable.put("extra", 1);
} catch (UnsupportedOperationException ex) {
    System.out.println("readOnlyMap = true");
}
```

Đoạn này **không phải chương trình chạy độc lập**; đặt nguyên trong `main` của `TinyMapperDemo` và giữ import đã có. Khi ghép đúng, chương trình in thêm:

```text
unsupported = true
null = true
readOnlyMap = true
```

Các ví dụ thể hiện ba giới hạn rõ:

- Kiểu bất kỳ không được tự động introspect; mapper từ chối trước khi Reflection gọi accessor.
- Không truyền null như đối tượng nguồn.
- Người dùng không thể dùng `put` để sửa map kết quả.

Trong hệ thống thực tế, dùng unit test/assertion thay vì chỉ quan sát dòng in. Cần test cả object có property trả null, getter ném exception, class khác module, class chứa tham chiếu mutable và thay đổi phiên bản schema.

## 7. Mở rộng ánh xạ mà không phá hợp đồng

### a) Ánh xạ nhiều kiểu bằng registry được quản lý

Allowlist trong ví dụ phù hợp với hai lớp cho sẵn. Khi số kiểu tăng, có thể quản lý adapter đã đăng ký:

```java
interface ObjectMapper<T> {
    Map<String, Object> toMap(T source);
}
```

Đây là **khai báo interface minh họa**. Một registry theo `Class<?>` có thể trỏ mỗi kiểu sang một mapper tường minh; các kiểu không đăng ký bị từ chối. Khi thật sự cần Reflection để hỗ trợ record/JavaBean bất kỳ, hãy đặt quy tắc cho phép class/package, property, annotation và quyền riêng tư ngay tại ranh giới registry.

### b) Chỉ chọn thuộc tính cho phép công bố

Không phải getter public nào cũng nên xuất dữ liệu. Ví dụ `getPasswordHash()`, `getAccessToken()` hoặc `getInternalNotes()` có thể có trong class nhưng không phải nội dung trả cho client. Vì vậy serializer thật nên có allowlist property hoặc annotation opt-in, DTO chuyên trách, các phép biến đổi/redaction cụ thể và test bảo vệ dữ liệu.

**Reflection giúp tìm được member, không tự quyết định member có được công bố hay không.** Một log/map có dữ liệu nhạy cảm vẫn là rò rỉ dù toàn bộ lời gọi Reflection hợp lệ.

### c) Field trực tiếp và accessor có ý nghĩa khác nhau

Dùng `Field.get(...)` đọc field khai báo; dùng `Method.invoke(...)` trên getter thực thi logic của accessor. Getter có thể tính toán, kiểm tra quyền, lazy-load dữ liệu, hoặc ném exception. Trong nhiều domain, dùng accessor là cách hợp đồng API muốn công bố dữ liệu; nhưng với getter tốn chi phí, map toàn bộ có thể gây nhiều lời gọi DB (*N+1 query*) hay side effect.

Cần phân biệt “đọc value” với “thực thi getter”: mapper tự động gọi getter không an toàn cho mọi đối tượng không biết trước.

### d) Record mang dữ liệu, nhưng không bắt buộc là schema JSON

`RecordComponent.getAccessor()` cung cấp tên kiểu Java. Nếu tên JSON khác tên Java, hoặc cần phiên bản hóa, nullable, nested conversion, ngày/giờ, enum encoding, polymorphism, default values và schema evolution, bạn cần quy tắc mapping bổ sung.

Một record như `record User(String firstName, List<String> roles)` không tự động biến thành JSON đúng schema chỉ bằng cách bỏ các value vào map. Cần bộ chuyển đổi có hợp đồng về nested types và null, hoặc thư viện serialization được cấu hình rõ.

### e) Ánh xạ chiều ngược

Tạo record từ map cần tìm **canonical constructor** với kiểu và thứ tự component chính xác, lấy giá trị theo tên, kiểm tra null với primitive, ép/chuyển đổi kiểu, xử lý unknown/missing keys và gọi constructor. Với JavaBean, cần biết constructor/setter/builder nào được phép. Đây là một nghiệp vụ riêng, dễ gây lỗi dữ liệu hơn chiều đọc.

Không tự viết chiều ngược bằng cách lấy tất cả private field rồi `setAccessible(true)` để set tùy ý: có thể phá invariants, gặp field final/module restrictions và bỏ qua validation. Nếu cần deserialize trong hệ thống thật, sử dụng DTO/constructor rõ ràng hoặc thư viện có quy tắc schema.

## 8. Lỗi Reflection thường gặp

| Lỗi | Nơi có thể gặp | Nguyên nhân/cách xử lý |
| --- | --- | --- |
| `IllegalAccessException` | `Method.invoke` | Accessor hoặc lớp chứa không truy cập được; xem visibility/module và vị trí code gọi |
| `InvocationTargetException` | `Method.invoke` | Getter đã được gọi nhưng tự ném lỗi; xem `getCause()` |
| `IntrospectionException` | `Introspector.getBeanInfo` | Không introspect được JavaBeans metadata theo yêu cầu |
| `IllegalArgumentException` | Kiểm tra allowlist hoặc gọi method sai receiver | Bản ví dụ cố ý từ chối class chưa đăng ký; phân biệt với lỗi kiểu đối số Reflection |
| `NullPointerException` | `Objects.requireNonNull(source)` | Thiếu đối tượng nguồn |
| `UnsupportedOperationException` | Caller `put` vào map trả về | Map kết quả bị khóa sửa cấu trúc |
| Trùng/thiếu property | Mapper thiết kế sai hoặc accessor không có | Dùng đúng `RecordComponent`/`PropertyDescriptor` và xác định chính sách lọc |
| Value thay đổi ngoài ý muốn | Getter có side effect hoặc trả về object mutable | Không xem giá trị trả về là snapshot bất biến sâu |

Đừng chỉ catch `Exception` rồi bỏ qua property lỗi. Khi mapper tự động lược bỏ một trường, người dùng có thể nhận một bản đồ **có vẻ thành công** nhưng thiếu dữ liệu quan trọng. Nếu sản phẩm yêu cầu best-effort mapping, phải định nghĩa rõ kiểu kết quả chứa cả lỗi theo từng trường; bài này chọn **fail-fast** để không che giấu lỗi.

## 9. Một số giới hạn hiệu năng và vòng đời

Lấy `getRecordComponents()` hoặc gọi `Introspector.getBeanInfo(...)` thường không nên lặp lại một cách vô ích trong vòng lặp xử lý lượng lớn object cùng loại. Có thể cache danh sách metadata/accessor theo `Class<?>` hoặc giữ một mapping plan theo schema.

Tuy nhiên cache phải quan tâm đến classloader: static `Map<Class<?>, ...>` với strong reference có thể giữ lớp từ plugin/redeploy trong bộ nhớ. Với metadata theo class, `ClassValue<T>` là một lựa chọn thích hợp; cache vòng đời component cũng có thể đơn giản hơn. `Introspector` còn có cơ chế cache nội bộ; khi quản lý lớp động có thể cần xem API `Introspector.flushFromCaches(type)` tại thời điểm phù hợp.

Trong phạm vi bài thực hành, không thêm cache để giữ code dễ đọc. Chỉ bổ sung sau khi đo được metadata discovery là điểm nóng. Với ứng dụng production, so sánh mapper Reflection với mapper tường minh, kiểm thử hiệu năng sau JIT warmup và đo cả chi phí getter.

## 10. Bài tập mở rộng

1. Tạo `record Point(int x, int y)`. Cho phép thêm record đó vào mapper, in kết quả và giải thích thứ tự các key khi dùng `TreeMap`.
2. Thêm JavaBean có `getTitle()`, `isPublished()` và method `calculateChecksum()`. Vì sao method thứ ba không trở thành property JavaBeans hợp lệ theo quy ước?
3. Thêm getter trả null. Chứng minh kết quả vẫn có key với value null và lý giải tại sao đổi sang `Map.copyOf(...)` sẽ không phù hợp.
4. Thêm getter cố ý ném `IllegalStateException("offline")`. Xác nhận mapper không bỏ qua lỗi, giữ được cause gốc từ `InvocationTargetException`.
5. Thử gọi `toMap(new Object())`. Giải thích lý do allowlist quan trọng khi dữ liệu nguồn không được tin cậy.
6. Thay `TreeMap` bằng `LinkedHashMap`. Với record, kiểm tra thứ tự khai báo của component ảnh hưởng thứ tự duyệt map thế nào.
7. Bổ sung danh sách tên property được cho phép vào mapper; không xuất các property như `passwordHash` hoặc `secretKey` dù getter public.
8. Thảo luận vì sao ánh xạ ngược `Map → record` cần thêm kiểm tra constructor, type conversion, missing key và validation. Viết đề xuất API rõ ràng trước khi coding.

### Checklist nghiệm thu

- Chương trình biên dịch/chạy trên Java 17+.
- Record dùng `getRecordComponents()` và `getAccessor()`, JavaBean dùng `Introspector` và `getReadMethod()`.
- Không dùng `setAccessible(true)` để bỏ qua quyền Java.
- Không lộ property `class` do `Object.getClass()`.
- Kết quả của hai kiểu khớp output đã công bố.
- Input null và loại chưa đăng ký được xử lý tường minh.
- Lỗi từ accessor được giữ nguyên cause để chẩn đoán.
- Các giới hạn về nested objects, null, bảo mật dữ liệu và quyền truy cập được mô tả rõ.

**Kết quả cần đạt:** tự viết được bộ ánh xạ một chiều quy mô nhỏ bằng accessor/record component; đọc đúng metadata của hai mô hình Java, gọi accessor có kiểm soát, xử lý lỗi Reflection và biết khi nào nên thay mini mapper bằng API tường minh hoặc công cụ serialization chuyên dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-practice-006">Xây dựng bộ kiểm tra annotation runtime đơn giản</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng bộ kiểm tra annotation runtime đơn giản bằng Java Reflection

## 1. Mục tiêu thực hành và phạm vi

Bài này xây dựng **một bộ kiểm tra dữ liệu object (runtime validator) tối giản**, dùng annotation để khai báo quy tắc ngay trên field và Reflection để thực thi những quy tắc đó lúc chương trình chạy.

Yêu cầu đầu ra:

1. Định nghĩa annotation `@NotBlank` cho trường kiểu `String`: giá trị không được `null`, rỗng hoặc chỉ gồm ký tự trắng theo `String.isBlank()`.
2. Định nghĩa annotation `@MinValue(18)` cho trường kiểu `int` hoặc `Integer`: giá trị không được nhỏ hơn ngưỡng; đối với `Integer`, `null` được xem là không hợp lệ trong ví dụ.
3. Dùng Reflection đọc annotation và giá trị field, tạo danh sách lỗi có thể hiển thị cho người dùng.
4. Kiểm tra cả field được khai báo trong **lớp cha**, không chỉ lớp runtime trực tiếp.
5. Không phụ thuộc thứ tự trả về của `getDeclaredFields()`: sắp xếp lỗi trước khi in để kết quả test ổn định.
6. Phân biệt metadata được giữ ở **`RetentionPolicy.RUNTIME`** với annotation chỉ có `RetentionPolicy.CLASS`.
7. Tạo một chương trình **Java 17+ đầy đủ, biên dịch và chạy được**, không cần thư viện bên ngoài.

Đây là **bài thực hành thiết kế validator**: chỉ nhận một object đã có, kiểm tra field có annotation và trả danh sách lỗi. Đây **không** phải bộ quét classpath, engine validation tổng quát, hay bản thay thế cho chuẩn Jakarta Bean Validation.

## 2. Annotation là metadata, không tự thực thi quy tắc

Annotation khai báo thông tin gắn vào thành phần chương trình. Ví dụ:

```java
// Các ví dụ minh họa; định nghĩa hoàn chỉnh nằm ở phần chương trình.
@NotBlank(message = "name is required")
private final String name;

@MinValue(18)
private final Integer age;
```

Hai khai báo trên **không khiến JVM tự động kiểm tra dữ liệu**. Cần code đọc annotation và áp quy tắc lên giá trị của field. Vì vậy bài gồm hai phần khác nhau:

- **Phần metadata:** annotation mô tả yêu cầu, ví dụ `message` hoặc `value`.
- **Phần engine:** validator duyệt field, dùng `Field.getAnnotation(...)`, đọc giá trị field bằng `Field.get(...)`, rồi thêm thông báo lỗi khi quy tắc không đạt.

Annotation không bị giới hạn chỉ ở field: Java hỗ trợ nhiều `ElementType` như `TYPE`, `METHOD`, `PARAMETER`, `CONSTRUCTOR` và `TYPE_USE`. Ở bài này ta cố ý chọn `FIELD`, vì dữ liệu cần kiểm tra nằm trong các field của object.

## 3. Ba meta-annotation quan trọng

### `@Retention(RetentionPolicy.RUNTIME)`

Đây là điều kiện quan trọng nhất khi muốn dùng **runtime reflection** để đọc một annotation trên thành phần đã gắn nó:

- `SOURCE`: tồn tại trong mã nguồn, không bắt buộc được ghi vào class file.
- `CLASS`: được ghi vào class file theo chính sách lưu giữ tương ứng, nhưng **không khả kiến qua API annotation reflection thông thường** tại runtime.
- `RUNTIME`: class file giữ annotation ở dạng runtime-visible phù hợp và Reflection có thể đọc nó lúc chạy.

Nếu quên ghi `@Retention`, chính sách mặc định của annotation là **`RetentionPolicy.CLASS`**, nên `field.getAnnotation(MyRule.class)` thường trả `null` dù khi đọc mã nguồn bạn thấy `@MyRule`.

### `@Target(ElementType.FIELD)`

Ràng buộc annotation chỉ được gắn vào field. Compiler sẽ báo lỗi nếu cố gắn `@NotBlank` lên class hay method trong khi target chỉ cho `FIELD`. Tuy nhiên, `@Target(FIELD)` **không tự giới hạn kiểu field**: annotation vẫn có thể được gắn nhầm vào field `Integer`. Engine phải tự kiểm tra kiểu trường để tránh áp quy tắc không có ý nghĩa.

### Thành phần (element) của annotation

`@NotBlank(message = "...")` cho phép cấu hình thông báo; `@MinValue(18)` lưu ngưỡng kiểm tra qua phần tử `int value()`. Phần tử tên `value` có cú pháp rút gọn: `@MinValue(18)` tương đương `@MinValue(value = 18)`.

Một annotation có thể có giá trị mặc định (ví dụ `String message() default "must not be blank";`). Những giá trị này là **metadata của annotation**, không phải state riêng mà JVM tự thêm vào object được gắn annotation.

## 4. Các API Reflection sẽ dùng

| API | Vai trò trong validator | Lưu ý |
| --- | --- | --- |
| `obj.getClass()` | Lấy lớp runtime của object cần kiểm tra | `obj == null` phải được xử lý trước |
| `Class.getDeclaredFields()` | Lấy field khai báo trực tiếp trong từng lớp | Không tự lấy field từ superclass; thứ tự không bảo đảm |
| `Class.getSuperclass()` | Lần lượt duyệt các lớp cha | Dừng trước `Object.class` |
| `Field.getAnnotation(NotBlank.class)` | Lấy instance annotation, hoặc `null` nếu không có | Cần retention `RUNTIME` để đọc |
| `Field.isAnnotationPresent(...)` | Kiểm tra một annotation có hiện diện không | Không thay thế việc lấy các element của annotation |
| `Field.getType()` | Xác định kiểu khai báo của field | `String.class`, `int.class`, `Integer.class` là các kiểu khác nhau |
| `Field.get(Object)` | Đọc giá trị một field trên instance | Có thể gặp `IllegalAccessException` nếu không đủ quyền |
| `Field.canAccess(Object)` | Kiểm tra quyền truy cập hiện tại vào field đối với receiver | Phân biệt public/private; receiver phải phù hợp |
| `Field.trySetAccessible()` | Thử mở truy cập vào field khi chưa có quyền | Có thể không được phép bởi JPMS/security; không bảo đảm thành công |

Cùng một annotation type có thể được đọc bằng `getAnnotation` hoặc `getDeclaredAnnotation`. Trên **field**, việc duyệt từng field rõ ràng giúp tránh nhầm với inheritance: `@Inherited` chỉ áp dụng theo quy tắc đối với annotation trên **class**, không tự truyền annotation của field lớp cha xuống field cùng tên ở lớp con. Do đó, engine duyệt cả hierarchy theo `getSuperclass()` thay vì tin rằng `getDeclaredFields()` sẽ bao gồm trường kế thừa.

**Cách thiết kế ví dụ:** field `static` và field synthetic bị bỏ qua vì ta đang kiểm tra **trạng thái riêng của một instance**, không phải dữ liệu cấp lớp hoặc thành viên do compiler sinh. Với field cùng tên bị che khuất giữa lớp cha/con, tiền tố tên lớp khai báo trong thông báo giúp phân biệt hai trường.

## 5. Chương trình Java 17+ hoàn chỉnh

Tạo tệp `RuntimeAnnotationValidatorDemo.java`. Tất cả type nằm trong một file, không dùng package ngoài JDK:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface NotBlank {
    String message() default "must not be blank";
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface MinValue {
    int value();
}

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
@interface CompileOnlyTag {}

public class RuntimeAnnotationValidatorDemo {
    static class Person {
        @NotBlank(message = "name is required")
        @CompileOnlyTag
        private final String name;

        @MinValue(18)
        private final Integer age;

        Person(String name, Integer age) {
            this.name = name;
            this.age = age;
        }
    }

    static class Employee extends Person {
        @NotBlank
        private final String department;

        Employee(String name, Integer age, String department) {
            super(name, age);
            this.department = department;
        }
    }

    static class Validator {
        static List<String> validate(Object object) {
            Objects.requireNonNull(object, "object");

            List<String> errors = new ArrayList<>();
            for (Class<?> type = object.getClass();
                    type != null && type != Object.class;
                    type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (field.isSynthetic()
                            || Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }

                    NotBlank notBlank =
                            field.getAnnotation(NotBlank.class);
                    MinValue minValue =
                            field.getAnnotation(MinValue.class);
                    if (notBlank == null && minValue == null) {
                        continue;
                    }

                    if (notBlank != null && field.getType() != String.class) {
                        throw new IllegalArgumentException(
                                "@NotBlank requires String: " + field);
                    }
                    if (minValue != null
                            && field.getType() != int.class
                            && field.getType() != Integer.class) {
                        throw new IllegalArgumentException(
                                "@MinValue requires int/Integer: " + field);
                    }

                    if (!field.canAccess(object) && !field.trySetAccessible()) {
                        throw new IllegalStateException(
                                "Cannot read annotated field: " + field);
                    }

                    Object value;
                    try {
                        value = field.get(object);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(
                                "Cannot read annotated field: " + field, e);
                    }

                    String location =
                            type.getSimpleName() + "." + field.getName();
                    if (notBlank != null
                            && (value == null || ((String) value).isBlank())) {
                        errors.add(location + ": " + notBlank.message());
                    }
                    if (minValue != null
                            && (value == null
                                || ((Integer) value).intValue()
                                   < minValue.value())) {
                        errors.add(location + ": must be >= "
                                + minValue.value());
                    }
                }
            }
            Collections.sort(errors);
            return List.copyOf(errors);
        }
    }

    public static void main(String[] args) throws Exception {
        Field name = Person.class.getDeclaredField("name");
        System.out.println("RUNTIME visible: "
                + name.isAnnotationPresent(NotBlank.class));
        System.out.println("CLASS visible: "
                + name.isAnnotationPresent(CompileOnlyTag.class));

        Employee valid = new Employee("An", 20, "Engineering");
        Employee invalid = new Employee("  ", 16, null);

        System.out.println("Valid: "
                + Validator.validate(valid).isEmpty());

        List<String> errors = Validator.validate(invalid);
        System.out.println("Errors: " + errors.size());
        errors.forEach(System.out::println);
    }
}
```

Lệnh biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 RuntimeAnnotationValidatorDemo.java
java RuntimeAnnotationValidatorDemo
```

Kết quả dự kiến:

```text
RUNTIME visible: true
CLASS visible: false
Valid: true
Errors: 3
Employee.department: must not be blank
Person.age: must be >= 18
Person.name: name is required
```

### Giải thích từng phần

1. `@NotBlank` và `@MinValue` đều có `RetentionPolicy.RUNTIME`, nên `Field.getAnnotation(...)` đọc được cấu hình từ `Person` và `Employee` tại runtime. `@CompileOnlyTag` chỉ có `CLASS`, nên `isAnnotationPresent(CompileOnlyTag.class)` trả `false`.
2. Validator nhận một object đã tồn tại, không quét classpath. Nó bắt đầu từ `object.getClass()`, rồi dùng `getSuperclass()` để đọc cả `Employee` và `Person`. Nếu chỉ dùng `Employee.class.getDeclaredFields()`, sẽ bỏ sót `name` và `age`.
3. Khi tìm thấy annotation, validator kiểm tra field type trước. Điều này ngăn dùng `@NotBlank` lên số hoặc `@MinValue` lên chuỗi mà không được xử lý; lỗi cấu hình annotation được báo tách khỏi lỗi dữ liệu.
4. `canAccess`/`trySetAccessible` cho phép đọc các field `private` trong ví dụ chạy trên classpath thông thường. Việc truy cập này **không được bảo đảm** với mọi lớp/module; trình kiểm tra phải báo lỗi rõ nếu không có quyền.
5. `String.isBlank()` trả `true` cho chuỗi rỗng hoặc chỉ có whitespace theo `Character.isWhitespace`; ví dụ dùng `"  "` để tạo lỗi tên. `Integer` bằng `null` cũng bị coi là không đạt `@MinValue` theo hợp đồng của bài này.
6. `Collections.sort(errors)` làm cho output không phụ thuộc thứ tự các field mà Reflection trả về; tiền tố `Person.`/`Employee.` giúp chỉ rõ nơi khai báo trường.
7. `List.copyOf(errors)` trả một danh sách bất biến để bên gọi không vô ý sửa danh sách lỗi sau khi validator hoàn tất. Validator không sửa giá trị field, không tạo instance và không gọi method nghiệp vụ.

**Kết quả “Valid: true” chỉ có nghĩa object thỏa hai loại quy tắc được triển khai ở đây.** Nó không chứng nhận object hợp lệ cho mọi ràng buộc nghiệp vụ, dữ liệu nhạy cảm, hay toàn bộ đồ thị object lồng nhau.

## 6. Vì sao phải tách lỗi cấu hình khỏi lỗi dữ liệu?

Giả sử ai đó gắn `@NotBlank` lên field `Integer`. Annotation có target `FIELD` nên compiler **không cấm** điều này, nhưng code kiểm tra chuỗi bằng `isBlank()` sẽ không có ý nghĩa. Validator cần một chính sách:

- **Lỗi cấu hình:** annotation đặt sai kiểu, metadata bị hỏng, thiếu quyền đọc field hoặc lớp/module không tương thích. Ví dụ chọn fail-fast bằng `IllegalArgumentException` hoặc `IllegalStateException`, không xem đây là lỗi do người nhập.
- **Lỗi dữ liệu:** field có kiểu phù hợp, đọc thành công nhưng không đạt quy tắc (`name` toàn khoảng trắng, `age` dưới 18). Các lỗi này được thu thập vào `List<String>` để bên gọi hiển thị hoặc trả về API.
- **Đối tượng `null`:** ví dụ dùng `Objects.requireNonNull(object, "object")` và xem đây là lỗi gọi API, không phải đối tượng cần kiểm tra.

Chính sách này quan trọng vì nếu gộp tất cả thành “giá trị không hợp lệ”, framework có thể che giấu bug cấu hình hoặc lỗi truy cập reflection. Trong code production, nên có kiểu kết quả giàu cấu trúc hơn chuỗi, chẳng hạn record `ValidationError(String fieldPath, String code, String message)`, để hỗ trợ i18n, sắp xếp và hiển thị giao diện.

## 7. Kiểm thử cần bao phủ những trường hợp nào?

| Ca thử | Dữ liệu/cấu hình | Kỳ vọng |
| --- | --- | --- |
| Annotation runtime hiển thị | Field có `@Retention(RUNTIME)` | `isAnnotationPresent` trả `true` |
| Annotation chỉ lưu trong class file | Field có `@Retention(CLASS)` | API annotation runtime trả `false` |
| Object hợp lệ | `name="An"`, `age=20`, `department="Engineering"` | Danh sách lỗi rỗng |
| Chuỗi toàn khoảng trắng | `name="  "` | Một lỗi `@NotBlank` cho `name` |
| Giá trị thấp hơn ngưỡng | `age=16` với `@MinValue(18)` | Một lỗi ngưỡng tuổi |
| Giá trị wrapper `null` | `age=null` | Một lỗi `@MinValue` |
| String `null` | `department=null` | Một lỗi `@NotBlank` |
| Field từ superclass | `name` và `age` khai báo trong `Person` | Vẫn được kiểm tra qua vòng lặp class hierarchy |
| Annotation gắn nhầm kiểu | `@NotBlank int count` | Báo lỗi cấu hình, không xem là input sai |
| Không đủ quyền đọc | Field private thuộc module không mở | Báo lỗi truy cập, không giả vờ kiểm tra thành công |
| Field static hoặc synthetic | Không phải dữ liệu instance bình thường | Bỏ qua theo hợp đồng hiện tại |
| Không có annotation được hỗ trợ | Field dữ liệu khác | Bỏ qua; không tự suy diễn thêm quy tắc |

Khi mở rộng bài kiểm thử, có thể thêm `check(...)` hoặc unit test JUnit, nhưng cần phân biệt *việc annotation có hiện diện* và *engine thực thi quy tắc đúng*. Một annotation mới được thêm vào mã nguồn mà validator chưa có nhánh xử lý sẽ không tự tạo hiệu lực.

## 8. Giới hạn cần biết trước khi đưa vào ứng dụng thật

### Truy cập private và Java modules

Trên classpath thông thường, `trySetAccessible()` có thể thành công với các lớp do ứng dụng sở hữu, như ví dụ trên. Với **named modules** trong JPMS, quyền deep reflection có thể bị chặn nếu package chưa được `opens` cho module gọi. `getDeclaredFields()` có thể liệt kê metadata nhưng `Field.get` vẫn không được phép. Không mặc định dùng `setAccessible(true)` sẽ vượt được mọi ranh giới module.

Thư viện thường ưu tiên public accessor/getter hoặc những hợp đồng module rõ ràng nếu cần kiểm tra object thuộc package của bên khác. Nếu không đọc được field, kết quả phải báo lỗi truy cập thay vì im lặng bỏ qua rồi kết luận object hợp lệ.

### Retention, kế thừa và trường bị ẩn

- `@Inherited` **chỉ ảnh hưởng tìm annotation trên class theo cơ chế hỗ trợ**, không làm một field lớp cha “chuyển” annotation xuống field mới của lớp con.
- `getDeclaredFields()` không tự bao gồm field kế thừa, nên vòng lặp `getSuperclass()` là có chủ đích.
- Nếu subclass khai báo field trùng tên với superclass, hai field vẫn là hai thành viên khác nhau. Tiền tố tên class trong thông báo lỗi giúp tránh nhầm.
- Interface không có field instance theo nghĩa thông thường (field trong interface mặc định `public static final`); engine bài này chỉ kiểm tra instance fields của chuỗi superclass, không dò interface.

### Null, kiểu số và dữ liệu tổng quát

`@MinValue` trong bài này hỗ trợ **chỉ `int`/`Integer`**: `Field.get(...)` boxing primitive `int` thành `Integer`, cho phép một đoạn so sánh chung. `long`, `BigDecimal`, `double`, field generic hay tập hợp cần chính sách so sánh riêng; không nên ép mọi `Number` sang `int` vì có thể mất độ chính xác hoặc làm sai nghĩa.

`@NotBlank` dùng `String.isBlank()`, không tương đương kiểm tra string chứa email hợp lệ, không đảm bảo chuẩn hóa Unicode cho mọi nghiệp vụ và không xử lý trimming/mutation. Engine **chỉ đọc**, không thay đổi object.

### Hiệu năng, vòng đời, thread safety

Mỗi lần gọi hiện tại duyệt class hierarchy, phản chiếu field, đọc annotation và truy cập dữ liệu. Khi kiểm tra hàng triệu object cùng lớp, một thư viện có thể cache **metadata bất biến** theo class (ví dụ danh sách field, annotation và cách đọc) để giảm overhead; tuyệt đối không cache **giá trị** field của một object làm kết quả cho object khác.

Trong ví dụ, `List<String> errors` được tạo cục bộ cho mỗi lời gọi nên không dùng state lỗi tĩnh chia sẻ. Điều đó tránh race trên danh sách lỗi nhưng **không làm object được kiểm tra tự động thread-safe**: nếu thread khác sửa dữ liệu cùng lúc, kết quả có thể phản ánh trạng thái không nhất quán.

### Bảo mật và thiết kế API

Việc mở quyền đọc field riêng tư là hoạt động nhạy cảm. Không dùng bài này để đọc object không thuộc quyền kiểm soát, không log giá trị bí mật và không tin mọi annotation đến từ bytecode bên thứ ba. Nếu ứng dụng nhận dữ liệu không tin cậy, validator chỉ là **một lớp kiểm tra**, không thay cho authentication, authorization, ràng buộc nghiệp vụ và an toàn deserialization.

### Khi nào dùng thư viện chuẩn hóa?

Với ứng dụng thật có kiểm tra bean phức tạp, nested objects, group validation, message localization, lifecycle và framework integration, cân nhắc **Jakarta Bean Validation** với implementation phù hợp thay vì tiếp tục mở rộng validator tự viết. Annotation tùy biến `@NotBlank` và `@MinValue` ở đây là **quy tắc minh họa riêng**, không phải chính xác các annotation và toàn bộ hợp đồng của Jakarta Validation.

## 9. Từng bước mở rộng có kiểm soát

1. **Thêm một annotation mới:** ví dụ `@MaxLength(int value)` cho `String`. Khai báo `@Target(FIELD)` và `@Retention(RUNTIME)`; sau đó thêm nhánh đọc và áp quy tắc trong validator.
2. **Tạo cấu trúc lỗi:** thay `String` bằng một record chứa đường dẫn trường, mã lỗi và thông báo. Kiểm thử thứ tự và tình huống cùng field vi phạm nhiều quy tắc.
3. **Hỗ trợ annotation lặp:** chỉ khi thực sự cần, dùng `@Repeatable` và API `getAnnotationsByType(...)` phù hợp; đừng mặc định `getAnnotation` trả mọi giá trị lặp.
4. **Thêm nested validation:** định nghĩa `@ValidNested` riêng, duyệt object con và mảng/collection với chính sách giới hạn độ sâu, phát hiện chu trình bằng identity set và tránh stack overflow.
5. **Cache metadata:** lưu danh sách field/rule đã kiểm tra theo `Class<?>` bằng cơ chế cache có tính đến vòng đời class loader; tránh memory leak khi plugin có thể unload.
6. **Viết unit test âm:** sai loại field, private field không mở bởi module, `null`, Unicode whitespace, inheritance, field static/synthetic và lỗi handler.

Mỗi bước đều phải trả lời: metadata đến từ đâu, khi nào đọc được, lỗi cấu hình xử lý ra sao, và engine có thực hiện đúng quy tắc không. Annotation **không phải magic**; giá trị nằm ở cơ chế đọc và kiểm thử.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao gắn `@NotBlank` nhưng không chạy validator lại không tự chặn một `String` rỗng?

**Đáp án:** Annotation chỉ cung cấp metadata. Cần code lấy annotation và thực thi ràng buộc mới có tác dụng.

**Câu 2.** Nếu `@Retention` không khai báo, `Field.getAnnotation(...)` sẽ đọc được annotation đó mặc định không?

**Đáp án:** Không nên kỳ vọng. Retention mặc định là `CLASS`, không khả kiến qua annotation reflection bình thường tại runtime; dùng `RUNTIME` nếu phải đọc bằng `Field.getAnnotation`.

**Câu 3.** `@Target(FIELD)` có ngăn lập trình viên đặt `@NotBlank` lên field `int` không?

**Đáp án:** Không. `@Target` giới hạn **loại phần tử khai báo** (field), không kiểm tra kiểu dữ liệu Java của field. Validator phải kiểm tra `Field.getType()` và báo lỗi cấu hình khi sai.

**Câu 4.** Tại sao `Employee.class.getDeclaredFields()` không đủ để kiểm tra trường `name` khai báo trong `Person`?

**Đáp án:** `getDeclaredFields()` chỉ lấy field được khai báo trong `Employee`. Phải duyệt `getSuperclass()` để đọc field lớp cha nếu hợp đồng muốn bao phủ chúng.

**Câu 5.** Tại sao cần `Collections.sort(errors)` trước khi so sánh stdout trong test?

**Đáp án:** API Reflection không cam kết thứ tự trả về field; sắp xếp thông báo làm output xác định và tránh test phụ thuộc thứ tự không được bảo đảm.

**Câu 6.** Có phải `trySetAccessible()` chắc chắn mở được mọi private field khi chạy ứng dụng Java 17+?

**Đáp án:** Không. JPMS, security và quyền truy cập có thể chặn. Khi không được phép, validator phải báo lỗi truy cập và không tự kết luận object hợp lệ.

**Câu 7.** Khi `age` là `Integer` bằng `null`, `@MinValue(18)` xử lý ra sao theo ví dụ?

**Đáp án:** Không hợp lệ vì bài này chọn null bị coi là vi phạm `@MinValue`; library khác có thể chọn null là hợp lệ cho ràng buộc min và yêu cầu một annotation `@NotNull` riêng. Cần tài liệu hóa chính sách.

**Kết luận:** Bộ kiểm tra annotation runtime gồm **khai báo ràng buộc (`RUNTIME`/`FIELD`) → đọc metadata bằng Reflection → truy cập dữ liệu → đánh giá quy tắc → báo lỗi có cấu trúc**. Việc tách lỗi cấu hình khỏi lỗi dữ liệu, xử lý kế thừa và access control rõ ràng giúp bài thực hành trở thành nền tảng tốt trước khi dùng các validation framework đầy đủ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-practice-007">Phân giải generic của một lớp con qua nhiều tầng kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# Phân giải generic của một lớp con qua nhiều tầng kế thừa trong Java

## 1. Bài toán thực tế

Trong Java, generic phần lớn bị **type erasure** khi chạy. Tuy nhiên, thông tin về **kiểu cha được khai báo** thường còn trong class file và có thể đọc qua reflection. Nhờ đó, một framework có thể xác định tham số kiểu cụ thể mà lớp con đã cung cấp qua nhiều tầng kế thừa, chẳng hạn để xây serializer, repository, mapper hoặc bộ đăng ký handler.

Xét chuỗi:

```java
class Base<T> {}
class Middle<U> extends Base<java.util.List<U>> {}
class End extends Middle<String> {}
```

Điều muốn tìm là: khi nhìn từ `End`, `T` của `Base<T>` thực sự là `List<String>`. Không thể chỉ xem một lớp cha rồi kết luận, vì `Middle` truyền `List<U>`, còn `U` mới được `End` cố định thành `String`.

Kết quả mong muốn là **phân giải có ngữ cảnh**:

```text
Base.T = java.util.List<java.lang.String>
```

Phải giữ đúng danh tính của từng `TypeVariable` và lần theo liên kết qua các cấp. Chỉ tìm tên biến `T`/`U` hoặc dùng `getSuperclass()` là chưa đủ.

## 2. Cần đọc API nào?

| API | Giá trị nhận được | Vai trò |
| --- | --- | --- |
| `Class.getSuperclass()` | `Class<?>` của lớp cha trực tiếp | Cho biết quan hệ raw, **không chứa** đối số generic như `String` |
| `Class.getGenericSuperclass()` | `Type` mô tả kiểu cha đã khai báo | Có thể là `ParameterizedType` nếu lớp con khai báo đối số generic |
| `Class.getTypeParameters()` | `TypeVariable<?>[]` | Liệt kê biến kiểu mà chính lớp đó khai báo, đúng thứ tự |
| `ParameterizedType.getRawType()` | `Type` (thường là `Class<?>`) | Xác định lớp generic gốc như `Middle.class` |
| `ParameterizedType.getActualTypeArguments()` | `Type[]` | Các đối số như `String.class`, `List<U>`, `U[]` |
| `TypeVariable.getGenericDeclaration()` | `GenericDeclaration` | Phân biệt hai biến cùng tên nhưng từ hai khai báo khác nhau |
| `TypeVariable.getBounds()` | `Type[]` | Ràng buộc trên của biến chưa được cố định, **không phải** đối số đã truyền |

Những đối tượng có thể gặp khi duyệt `Type`:

- `Class<?>`: kiểu cụ thể, primitive hoặc array không có biến generic cần giải tiếp.
- `ParameterizedType`: kiểu có đối số, như `List<U>` hoặc `Map<String, ? extends U>`.
- `TypeVariable<?>`: biến kiểu như `T`, `U`, `Z`.
- `GenericArrayType`: kiểu mảng mà thành phần là kiểu generic, ví dụ `X[]`.
- `WildcardType`: `?`, `? extends U`, `? super U`.

**Sai lầm thường gặp:** `Base.class.getTypeParameters()[0]` sẽ vẫn là biến `T`. Nó không tự biết một lớp con nào đang được xét. Muốn nhận `List<String>`, phải xây **bảng thay thế từ lớp con cụ thể lên lớp cha**.

## 3. Cách lần theo nhiều tầng bằng bảng ánh xạ biến kiểu

Giả sử:

```java
class Three<A, B, C> {}
class Two<X, Y> extends Three<
        java.util.List<Y>,
        X[],
        java.util.Map<String, ? extends Y>> {}
class One<Z> extends Two<String, Z> {}
class Concrete extends One<Integer> {}
```

Các đối số khi đi ngược từ `Concrete` lên `Three`:

| Cạnh kế thừa | Ánh xạ theo khai báo kiểu cha |
| --- | --- |
| `Concrete extends One<Integer>` | `One.Z → Integer` |
| `One<Z> extends Two<String,Z>` | `Two.X → String`, `Two.Y → One.Z` |
| `Two<X,Y> extends Three<List<Y>,X[],Map<String,? extends Y>>` | `Three.A → List<Two.Y>`, `Three.B → Two.X[]`, `Three.C → Map<String, ? extends Two.Y>` |

Từ đó áp dụng **đệ quy**:

- `Three.A → List<Two.Y> → List<One.Z> → List<Integer>`.
- `Three.B → Two.X[] → String[]`.
- `Three.C → Map<String,? extends Two.Y> → Map<String,? extends One.Z> → Map<String,? extends Integer>`.

Từng tên biến chỉ là ký hiệu mô tả. Trong code, khóa bảng ánh xạ phải là **đối tượng `TypeVariable<?>`**, không phải chuỗi `"X"` hay `"Z"`. Vì hai lớp bất kỳ có thể đều khai báo `T`, một `Map<String,Type>` có thể vô tình ghi đè ánh xạ của lớp khác.

### Thuật toán cho chuỗi kế thừa lớp đơn

1. Bắt đầu với `Class<?> leaf` là lớp con đang khảo sát.
2. Lấy `leaf.getGenericSuperclass()`.
3. Nếu là `ParameterizedType`, lấy raw class của cha cùng mảng type arguments; ghép từng `rawClass.getTypeParameters()[i]` với `getActualTypeArguments()[i]` trong bảng ánh xạ.
4. Nếu là `Class<?>`, đó là cạnh không tham số hóa cần đi tiếp; **không tự suy ra một đối số kiểu cụ thể**.
5. Tiếp tục lên các lớp cha cho tới `Object` hoặc hết cha.
6. Khi cần biết một biến kiểu của lớp tổ tiên, **thay thế đệ quy** qua bảng vừa thu thập. Với các kiểu phức hợp, phải đi vào `ParameterizedType`, `GenericArrayType` và `WildcardType` thay vì dừng ở tầng ngoài.

Bảng ở ví dụ có thể tạm giữ `Three.A → List<Two.Y>`. Không cần “đóng băng” kết quả ngay lúc gặp `Three`; đến cuối quá trình mới giải mọi liên kết sẽ cho kết quả đúng sau khi các biến ở cấp thấp hơn đã được ghi nhận.

## 4. Ví dụ Java 17+ hoàn chỉnh: giải chữ ký qua ba cấp

Ví dụ sau cố tình **trả về chuỗi biểu diễn kiểu đã phân giải**, thay vì tạo các triển khai tự viết của `ParameterizedType`/`WildcardType`. Cách này phù hợp khi mục tiêu là chẩn đoán và học thuật toán. Nếu chương trình cần một đối tượng `Type` đã thay thế thực sự cho framework, cần thêm phần hiện thực `Type` hoặc dùng thư viện xử lý type token có hợp đồng rõ ràng.

Lưu tệp `GenericHierarchyResolverDemo.java`:

```java
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

public class GenericHierarchyResolverDemo {
    static class Three<A, B, C> {}

    static class Two<X, Y> extends Three<
            List<Y>, X[], Map<String, ? extends Y>> {}

    static class One<Z> extends Two<String, Z> {}

    static final class Concrete extends One<Integer> {}

    // Từ lớp chưa cố định R: không được tự coi bound Number là kiểu thực tế.
    static class Open<R extends Number> extends One<R> {}

    static Map<TypeVariable<?>, Type> collectBindings(Class<?> leaf) {
        Map<TypeVariable<?>, Type> bindings = new LinkedHashMap<>();
        Class<?> current = leaf;

        while (current != null && current != Object.class) {
            Type genericParent = current.getGenericSuperclass();

            if (genericParent instanceof ParameterizedType parent) {
                Type raw = parent.getRawType();
                if (!(raw instanceof Class<?> parentClass)) {
                    throw new IllegalArgumentException(
                            "Unsupported raw type: " + raw);
                }

                TypeVariable<?>[] variables =
                        parentClass.getTypeParameters();
                Type[] arguments = parent.getActualTypeArguments();

                if (variables.length != arguments.length) {
                    throw new IllegalStateException(
                            "Mismatched generic signature");
                }

                for (int i = 0; i < variables.length; i++) {
                    bindings.put(variables[i], arguments[i]);
                }
                current = parentClass;
            } else if (genericParent instanceof Class<?> rawParent) {
                current = rawParent;
            } else {
                break;
            }
        }
        return bindings;
    }

    static String describe(Type type, Map<TypeVariable<?>, Type> bindings) {
        return describe(type, bindings, new HashSet<>());
    }

    static String describe(Type type, Map<TypeVariable<?>, Type> bindings,
            Set<TypeVariable<?>> visiting) {

        if (type instanceof TypeVariable<?> variable) {
            if (!visiting.add(variable)) {
                return variable.getName();
            }
            try {
                Type replacement = bindings.get(variable);
                if (replacement == null || replacement.equals(variable)) {
                    return variable.getName();
                }
                return describe(replacement, bindings, visiting);
            } finally {
                visiting.remove(variable);
            }
        }

        if (type instanceof ParameterizedType parameterized) {
            String arguments = Arrays.stream(
                    parameterized.getActualTypeArguments())
                    .map(argument -> describe(
                            argument, bindings, visiting))
                    .collect(Collectors.joining(", "));
            return parameterized.getRawType().getTypeName()
                    + "<" + arguments + ">";
        }

        if (type instanceof GenericArrayType genericArray) {
            return describe(
                    genericArray.getGenericComponentType(),
                    bindings, visiting) + "[]";
        }

        if (type instanceof WildcardType wildcard) {
            Type[] lower = wildcard.getLowerBounds();
            if (lower.length > 0) {
                return "? super " + Arrays.stream(lower)
                        .map(bound -> describe(
                                bound, bindings, visiting))
                        .collect(Collectors.joining(" & "));
            }

            Type[] upper = wildcard.getUpperBounds();
            if (upper.length == 1 && upper[0] == Object.class) {
                return "?";
            }
            return "? extends " + Arrays.stream(upper)
                    .map(bound -> describe(bound, bindings, visiting))
                    .collect(Collectors.joining(" & "));
        }

        return type.getTypeName();
    }

    static String resolveParameter(
            Class<?> ancestor, int index,
            Map<TypeVariable<?>, Type> bindings) {
        TypeVariable<?>[] variables = ancestor.getTypeParameters();
        if (index < 0 || index >= variables.length) {
            throw new IndexOutOfBoundsException(index);
        }
        return describe(variables[index], bindings);
    }

    static void requireEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    "Expected: " + expected + "; actual: " + actual);
        }
    }

    public static void main(String[] args) {
        Map<TypeVariable<?>, Type> concrete =
                collectBindings(Concrete.class);

        String a = resolveParameter(Three.class, 0, concrete);
        String b = resolveParameter(Three.class, 1, concrete);
        String c = resolveParameter(Three.class, 2, concrete);
        String z = resolveParameter(One.class, 0, concrete);

        requireEquals("java.util.List<java.lang.Integer>", a);
        requireEquals("java.lang.String[]", b);
        requireEquals(
                "java.util.Map<java.lang.String, ? extends java.lang.Integer>",
                c);
        requireEquals("java.lang.Integer", z);

        System.out.println("Concrete.Three.A=" + a);
        System.out.println("Concrete.Three.B=" + b);
        System.out.println("Concrete.Three.C=" + c);
        System.out.println("Concrete.One.Z=" + z);

        Map<TypeVariable<?>, Type> open = collectBindings(Open.class);
        String unresolved = resolveParameter(Three.class, 0, open);
        requireEquals("java.util.List<R>", unresolved);
        System.out.println("Open.Three.A=" + unresolved);

        System.out.println("rawParent=" +
                Concrete.class.getSuperclass().getSimpleName());
        System.out.println("checks=PASS");
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 GenericHierarchyResolverDemo.java
java GenericHierarchyResolverDemo
```

Stdout:

```text
Concrete.Three.A=java.util.List<java.lang.Integer>
Concrete.Three.B=java.lang.String[]
Concrete.Three.C=java.util.Map<java.lang.String, ? extends java.lang.Integer>
Concrete.One.Z=java.lang.Integer
Open.Three.A=java.util.List<R>
rawParent=One
checks=PASS
```

### Đọc ví dụ theo từng bước

- `Concrete` kế thừa `One<Integer>`: `getGenericSuperclass()` là `ParameterizedType`, từ đó thu được `One.Z → Integer`.
- `One<Z>` kế thừa `Two<String,Z>`: thu được `Two.X → String` và `Two.Y → One.Z`.
- `Two<X,Y>` kế thừa `Three<List<Y>,X[],Map<String,? extends Y>>`: ghi ánh xạ cho `Three.A`, `Three.B`, `Three.C`.
- Khi in `Three.A`, hàm `describe` đi từ `TypeVariable` vào `ParameterizedType` rồi theo hai biến `Y` và `Z`; kết quả là `List<Integer>`.
- `Three.B` dùng `GenericArrayType` (mảng có thành phần là biến kiểu `X`), sau thay thế trở thành `String[]`.
- `Three.C` buộc resolver đi vào một `ParameterizedType` chứa `WildcardType`; nếu chỉ gọi `Type.getTypeName()` của cấu trúc gốc, kết quả vẫn là `? extends Y` và **chưa phân giải hoàn toàn**.
- `Open<R extends Number>` cho ra `List<R>` vì `R` **chưa được cố định** bởi lớp con cụ thể. Không được tự thay `R` bằng `Number`: bound chỉ là giới hạn kiểu, không phải đối số thực tế.
- `requireEquals` làm chương trình **tự kiểm chứng** các kết quả, không chỉ hiển thị chuỗi mô tả.

Hàm `collectBindings` được thiết kế cho **chuỗi superclass**. Nó không tự đi vào `getGenericInterfaces()`, không đưa owner type của lớp lồng non-static vào chuỗi in và không tái tạo một cây đối tượng `Type` có thể dùng thay thế trong mọi framework. Những giới hạn này phải được xử lý trước khi dùng như một thư viện generic resolution toàn diện.

## 5. Vì sao `TypeVariable` phải được phân biệt theo nơi khai báo?

Ví dụ:

```java
class Parent<T> {}
class Child<T> extends Parent<T> {}
class Fixed extends Child<String> {}
```

`Parent.T` và `Child.T` đều có tên `"T"`, nhưng là **hai biến kiểu khác nhau** vì khai báo tại hai class khác nhau. Ánh xạ đúng là:

```text
Parent.T -> Child.T
Child.T  -> java.lang.String
Kết quả Parent.T = java.lang.String
```

Nếu dùng `Map<String, Type>` với key `"T"`, bước thứ hai có thể ghi đè bước thứ nhất và làm mất cấu trúc liên kết; với nhánh phức tạp, kết quả dễ sai hoặc gặp vòng lặp giả. Dùng `Map<TypeVariable<?>,Type>` bảo toàn danh tính và bối cảnh khai báo. Có thể kiểm tra `variable.getGenericDeclaration()` để biết biến thuộc class, interface, method hay constructor nào.

Hàm `describe` còn có `Set<TypeVariable<?>> visiting` nhằm tránh lặp không kết thúc khi gặp ánh xạ tạo vòng. Đây là biện pháp phòng vệ của trình phân giải, **không** có nghĩa generic bất kỳ đều được phép tạo một lớp cha vòng lặp trong Java.

## 6. Generic array, wildcard, raw type và kiểu chưa biết

### Generic array và wildcard

Kiểu `X[]` trong khai báo `Two<X,Y>` thường xuất hiện dưới dạng `GenericArrayType` vì thành phần `X` là `TypeVariable`. Một generic resolver không đọc nhánh này sẽ trả nguyên `X[]` thay vì `String[]`.

Tương tự, `Map<String, ? extends Y>` là `ParameterizedType` chứa `WildcardType`. Đối số `? extends Y` phải được giải tiếp để ra `? extends Integer`. Với `? super Y`, cần xử lý `getLowerBounds()`; với wildcard không giới hạn, `getUpperBounds()` thường là `Object.class` và biểu diễn là `?`.

**Không lẫn bound và type argument**: `class Open<R extends Number>` không có nghĩa `R` là `Number`. Một instance của `Open<Long>` là hợp lệ, vì thế `R` chỉ được thay bằng `Long` khi có một lớp con/đường kế thừa thực sự cung cấp đối số tương ứng.

### Raw type làm mất thông tin

```java
class Box<T> {}
class Raw extends Box {} // Raw type, không cung cấp T cụ thể.
```

Đây là đoạn minh họa. `Raw.class.getGenericSuperclass()` có thể là `Class<?>` dạng raw thay vì `ParameterizedType`; không có dữ liệu để kết luận `T` là `String` hay `Integer`. Với framework nghiêm ngặt, nên **báo unresolved hoặc từ chối**, không tự đoán `Object` là đối số cụ thể chỉ vì type erasure hoặc bound mặc định là `Object`.

### Runtime object không khôi phục `List<String>` tùy ý

`new ArrayList<String>().getClass()` chỉ cho lớp runtime `ArrayList`; cú pháp `new ArrayList<String>()` không tạo một subclass mang `String` trong `generic superclass` của `ArrayList`. Reflection có thể giải đối số từ **khai báo kiểu cha** trên một lớp con có chữ ký ghi trong class file; nó không thể đọc mọi type argument từ một object thông thường.

Một cách khác để mang thông tin generic là **anonymous subclass/type token**, ví dụ `new TypeReference<List<String>>() {}`, nơi superclass được tham số hóa trong class file. Type token là một kỹ thuật khác, không đồng nghĩa với việc mọi object đều giữ generic khi chạy.

## 7. Interface, owner type và những giới hạn cần mở rộng

Java cho phép một class **kế thừa một superclass** nhưng **implements nhiều interface**. Nếu mục tiêu là generic được khai báo bởi interface, cần mở rộng sang `Class.getGenericInterfaces()` và duyệt đồ thị interface/superclass.

Ví dụ:

```java
interface Handler<E> {}
interface NamedHandler<X> extends Handler<java.util.List<X>> {}
class NamedStringHandler implements NamedHandler<String> {}
```

Muốn giải `Handler.E` trong ngữ cảnh `NamedStringHandler`, phải đi qua cạnh interface:

```text
NamedStringHandler
  -> NamedHandler<String>
  -> Handler<List<X>>
  -> Handler.E = List<String>
```

**Không** thể dùng duy nhất `getGenericSuperclass()` để tìm các cạnh này. Với resolver tổng quát, mỗi nhánh traversal nên mang theo **môi trường ánh xạ riêng**; tránh dùng một map toàn cục để trộn các đường khác nhau. Cần tránh thăm lặp, kiểm tra quan hệ `target.isAssignableFrom(candidate)` thích hợp và giải quyết đường kế thừa tới đúng khai báo.

Ngoài ra, một non-static member class có thể sử dụng biến kiểu từ lớp ngoài qua **owner type** (`ParameterizedType.getOwnerType()`). Nếu mô tả kết quả phải đầy đủ, hãy giải cả owner và biểu diễn tên nested class chính xác. Ví dụ code chạy ở mục 4 **không tuyên bố hỗ trợ trường hợp này**.

Một `ParameterizedType`/`TypeVariable` được trả về từ metadata cũng có thể dẫn tới `TypeNotPresentException` hay `MalformedParameterizedTypeException` nếu chữ ký tham chiếu tới kiểu thiếu hoặc metadata lỗi. Thư viện production nên xử lý rõ ràng, kèm class/module/loader đang phân tích để chẩn đoán.

## 8. Những lỗi phổ biến

| Lỗi | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Chỉ dùng `getSuperclass()` | Mất type arguments của cạnh cha | Dùng `getGenericSuperclass()` |
| Chỉ xét một cấp | Lấy `List<U>` thay vì `List<String>` | Theo bảng thế biến tới lớp cuối |
| Thay biến bằng tên chuỗi | Hai class đều có thể khai báo `T` | Dùng khóa `TypeVariable<?>` |
| Cast mọi `Type` sang `ParameterizedType` | Cha raw, tham số concrete, wildcard, array không phải dạng đó | Kiểm tra `instanceof` theo từng nhánh |
| Chỉ thay `TypeVariable` ở tầng ngoài | `List<U>`/`Map<?,U>` còn giữ biến bên trong | Duyệt đệ quy `ParameterizedType`, wildcard, array |
| Dùng `getBounds()` làm kiểu thực tế của biến chưa gắn | Bound không phải actual argument | Giữ trạng thái unresolved |
| Mặc định raw type là `Object` chính xác | Không có thông tin type argument thực | Báo thiếu thông tin; quyết định fallback theo contract |
| Trộn superclass với interface mà không duyệt interface | Mất đường tới generic interface | Duyệt `getGenericInterfaces()` theo môi trường từng nhánh |
| Coi `new List<String>()`-style object giữ type arguments | Type erasure không cho suy từ object thông thường | Dựa vào signature khai báo hoặc type token |
| Không kiểm soát class loader | Tên class giống nhau từ hai loader không nhất thiết cùng kiểu | So `Class<?>`/`Type` thực, ghi bối cảnh loader khi debug |

## 9. Bài tập tự luyện

**Bài 1.** `class A<T> {}`; `class B<U> extends A<U[]> {}`; `class C extends B<Long> {}`. Giải `A.T` trong ngữ cảnh `C`.

**Đáp án:** `java.lang.Long[]`. Biến `U` của `B` được cố định thành `Long`, nên `U[]` thành `Long[]`.

**Bài 2.** `class A<T> {}`; `class B<T> extends A<List<T>> {}`; `class C extends B<String> {}`. Có được dùng `Map<String,Type>` chứa `"T"` không?

**Đáp án:** Không an toàn. `A.T` và `B.T` khác nhau dù tên giống. Dùng `TypeVariable<?>` làm khóa.

**Bài 3.** `class A<T extends Number> {}`; `class B<U extends Number> extends A<U> {}`. `A.T` có tự giải thành `Number` không?

**Đáp án:** Không. Trong `B` chưa cố định `U`; kết quả vẫn là biến `U` với bound `Number`.

**Bài 4.** Lớp triển khai `interface Handler<T>` bằng một chuỗi interface trung gian có thể giải bằng chỉ `getGenericSuperclass()` không?

**Đáp án:** Không. Cần duyệt `getGenericInterfaces()` và ánh xạ biến theo các cạnh interface.

**Bài 5.** Sau khi giải `List<? extends U>` với `U=Integer`, có thể bỏ wildcard và xem là `List<Integer>` không?

**Đáp án:** Không. `List<? extends Integer>` và `List<Integer>` không phải cùng một kiểu generic; resolver phải giữ wildcard.

**Ghi nhớ:** Muốn phân giải generic qua nhiều tầng, hãy đi theo **đường kế thừa đúng**, tạo bản đồ **TypeVariable theo khai báo → Type argument**, rồi thay thế **đệ quy cả kiểu lồng, mảng generic và wildcard**. Chỉ kết luận một kiểu cụ thể khi chuỗi khai báo thực sự cung cấp đủ thông tin.

Tham khảo API Java 17: [`Class.getGenericSuperclass()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getGenericSuperclass()), [`ParameterizedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/ParameterizedType.html), [`TypeVariable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/TypeVariable.html), [`GenericArrayType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/GenericArrayType.html), [`WildcardType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/WildcardType.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-practice-008">Tạo proxy ghi thời gian xử lý và giữ nguyên ngoại lệ mã đích</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: Dynamic proxy đo thời gian xử lý và giữ nguyên ngoại lệ mã đích

## 1. Bài toán và tiêu chí hoàn thành

Một dịch vụ Java có interface `Job`. Ta muốn bọc mỗi lời gọi của dịch vụ bằng **JDK dynamic proxy** để đo thời gian thực thi, ghi kết quả thành công/thất bại và **không làm biến dạng ngoại lệ do mã đích ném ra**.

Đây là hai mối quan tâm khác nhau:

- **Đo thời gian:** lấy mốc `System.nanoTime()` trước lúc chuyển tiếp lời gọi, tính chênh lệch trong `finally` để luôn ghi nhận cả đường thành công lẫn thất bại.
- **Giữ nguyên ngoại lệ:** khi gọi mã đích bằng `Method.invoke`, Java Reflection bọc bất kỳ `Throwable` nào do method đích ném trong `InvocationTargetException`. Handler phải **lấy đúng `getCause()` và ném lại chính đối tượng đó**, nếu phù hợp với contract của interface proxy.

Sau bài học, bạn sẽ tạo được proxy, kiểm tra kết quả/identity của `IOException`, `RuntimeException` và `Error`, hiểu lúc nào `UndeclaredThrowableException` xuất hiện và nhận biết các rủi ro khi code ghi log làm thay đổi lỗi của nghiệp vụ.

**Phạm vi:** JDK 17+, `java.lang.reflect.Proxy` chỉ bọc các **interface**. Ví dụ không dùng thư viện bên ngoài; với concrete class không có interface, bạn cần một cách tiếp cận khác như decorator thủ công hoặc công cụ proxy bytecode phù hợp.

## 2. Luồng xử lý của `InvocationHandler`

Khi ứng dụng gọi `proxy.execute("A")`:

~~~text
Caller
  |
  v
Proxy implements Job
  |
  v
InvocationHandler.invoke(proxy, method, args)
  |
  +--> start = System.nanoTime()
  |
  +--> method.invoke(realTarget, args)
  |       |
  |       +--> trả về bình thường ------> return result
  |       |
  |       +--> method đích throw ------> InvocationTargetException
  |                                         |
  |                                         +--> throw ex.getCause()
  |
  +--> finally: elapsed = System.nanoTime() - start
  |              ghi nhận tên method, elapsed, trạng thái
  |
  v
Caller nhận kết quả hoặc ngoại lệ gốc
~~~

`InvocationHandler.invoke(Object proxy, Method method, Object[] args)` khai báo `throws Throwable`. Điều này cho phép handler ném lại cả checked exception, unchecked exception và `Error` mà **không cần ép chúng thành `RuntimeException`**.

Các tham số:

| Tham số | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `proxy` | Instance proxy đang được gọi | **Không** gọi lại cùng method trên `proxy` để delegate; dễ đệ quy vô hạn |
| `method` | Đối tượng `Method` tương ứng lời gọi được proxy chuyển đến | Có thể khai báo trên interface cha hoặc là method của `Object` |
| `args` | Giá trị đối số đã được đóng hộp nếu là primitive | Có thể là `null` khi method không có tham số |
| Kết quả `invoke` | Giá trị gửi về caller qua proxy | Phải tương thích kiểu trả về; method `void` nhận kết quả `null` |

Mẫu cốt lõi:

~~~java
long started = System.nanoTime();
try {
    return method.invoke(target, args);
} catch (InvocationTargetException ex) {
    throw ex.getCause();
} finally {
    long elapsedNanos = System.nanoTime() - started;
    recordTime(method, elapsedNanos);
}
~~~

Đây là **fragment**, giả định `target`, `method`, `args` và hàm `recordTime` đã tồn tại. Phần thực hành dưới đây cung cấp chương trình hoàn chỉnh.

## 3. Vì sao phải bóc `InvocationTargetException`?

`java.lang.reflect.Method.invoke` dùng Reflection gọi phương thức đích. Nếu mã đích có:

~~~java
throw new IOException("disk offline");
~~~

thì lời gọi Java trực tiếp nhận `IOException`; còn `method.invoke(target, args)` sẽ ném **`InvocationTargetException`**, với `getCause()` trỏ tới chính `IOException` gốc.

Nếu handler viết sai:

~~~java
// Sai khi muốn giữ nguyên hợp đồng lỗi của Job
(proxy, method, args) -> method.invoke(target, args)
~~~

`InvocationTargetException` là **checked exception**. Nếu phương thức interface chỉ khai báo `throws IOException`, proxy không được ném một `InvocationTargetException` từ handler như checked exception hợp lệ. Khi đó lời gọi proxy thường trả ra `UndeclaredThrowableException`, và ngoại lệ nghiệp vụ bị che dưới nhiều lớp wrapper.

Đúng là:

~~~java
try {
    return method.invoke(target, args);
} catch (InvocationTargetException ex) {
    throw ex.getCause();
}
~~~

Điểm quan trọng của `throw ex.getCause()`:

1. Ném lại **đúng đối tượng `Throwable`** do method đích phát sinh; không đổi thông điệp và không tạo instance ngoại lệ mới.
2. Giữ lại cause và suppressed exceptions đã nằm trên đối tượng gốc.
3. Caller có thể tiếp tục bắt `IOException`, `IllegalStateException` hay `AssertionError` theo đúng hợp đồng.
4. Không bóc mọi `getCause()` một cách vô điều kiện. Nếu **mã đích thực sự ném một wrapper** như `CompletionException`, đó là hành vi của mã đích; interceptor không nên tự ý làm mất wrapper ấy.

`Method.invoke` có thể tự phát sinh lỗi do truyền sai đối số, quyền truy cập không đủ hoặc receiver sai. Các lỗi xảy ra **trước khi method đích chạy** không phải `InvocationTargetException` đại diện cho lỗi nghiệp vụ, và cần chẩn đoán riêng.

## 4. Chương trình đầy đủ Java 17+: proxy đo thời gian và giữ nguyên cả ba loại ngoại lệ

Tạo file **`TimedProxyDemo.java`**:

~~~java
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class TimedProxyDemo {
    public interface Job {
        String execute(String input);
        void failChecked() throws IOException;
        void failRuntime();
        void failError();
    }

    public static final class RealJob implements Job {
        final IOException checked = new IOException("disk offline");
        final IllegalStateException unchecked =
                new IllegalStateException("bad state");
        final AssertionError error = new AssertionError("fatal");

        public String execute(String input) {
            return "done:" + input;
        }

        public void failChecked() throws IOException {
            throw checked;
        }

        public void failRuntime() {
            throw unchecked;
        }

        public void failError() {
            throw error;
        }
    }

    record Sample(String method, long nanos, boolean succeeded) {}

    static <T> T timedProxy(Class<T> contract, T target, List<Sample> samples) {
        if (!contract.isInterface() || !contract.isInstance(target)) {
            throw new IllegalArgumentException("Wrong contract or target");
        }

        Object proxy = Proxy.newProxyInstance(
                contract.getClassLoader(),
                new Class<?>[] { contract },
                (self, method, args) -> {
                    // Object methods được xử lý tường minh, không đo ở ví dụ này.
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" ->
                                    "TimedProxy(" + contract.getSimpleName() + ")";
                            case "hashCode" -> System.identityHashCode(self);
                            case "equals" -> self == args[0];
                            default ->
                                    throw new UnsupportedOperationException(
                                            method.toString());
                        };
                    }

                    long start = System.nanoTime();
                    boolean succeeded = false;
                    try {
                        Object result = method.invoke(target, args);
                        succeeded = true;
                        return result;
                    } catch (InvocationTargetException ex) {
                        // Giữ nguyên đúng IOException, RuntimeException
                        // hoặc Error do method đích ném.
                        throw ex.getCause();
                    } finally {
                        long nanos = System.nanoTime() - start;
                        samples.add(new Sample(method.getName(), nanos, succeeded));
                    }
                });

        return contract.cast(proxy);
    }

    public static void main(String[] args) {
        RealJob target = new RealJob();
        List<Sample> samples = new ArrayList<>();
        Job proxy = timedProxy(Job.class, target, samples);

        System.out.println(proxy.execute("A"));

        try {
            proxy.failChecked();
        } catch (IOException ex) {
            System.out.println("checked preserved: "
                    + (ex == target.checked));
        }

        try {
            proxy.failRuntime();
        } catch (IllegalStateException ex) {
            System.out.println("runtime preserved: "
                    + (ex == target.unchecked));
        }

        try {
            proxy.failError();
        } catch (AssertionError ex) {
            System.out.println("error preserved: "
                    + (ex == target.error));
        }

        System.out.println(proxy);
        System.out.println("samples: " + samples.stream()
                .map(s -> s.method() + ":"
                        + (s.succeeded() ? "ok" : "fail"))
                .toList());
        System.out.println("durations recorded: " + samples.stream()
                .allMatch(s -> s.nanos() >= 0));

        for (Sample s : samples) {
            System.out.printf("timing %s: %d ns%n",
                    s.method(), s.nanos());
        }
    }
}
~~~

Lệnh biên dịch/chạy (từ thư mục chứa file):

~~~bash
javac --release 17 TimedProxyDemo.java
java TimedProxyDemo
~~~

**Phần output cố định, đã kiểm chứng với OpenJDK 21 và `javac --release 17`:**

~~~text
done:A
checked preserved: true
runtime preserved: true
error preserved: true
TimedProxy(Job)
samples: [execute:ok, failChecked:fail, failRuntime:fail, failError:fail]
durations recorded: true
~~~

Sau đó chương trình in bốn dòng đo thời gian, ví dụ:

~~~text
timing execute: 2029596 ns
timing failChecked: 1850957 ns
timing failRuntime: 164227 ns
timing failError: 69275 ns
~~~

**Các con số trên chỉ là một lần chạy**; không phải giá trị stdout cố định. Trên máy khác hoặc lần chạy khác, thời gian thay đổi theo JIT, scheduler, GC và tải hệ thống. Tính đúng của bài không phụ thuộc số nanosecond cụ thể.

## 5. Vì sao chương trình thực sự giữ nguyên ngoại lệ?

`RealJob` tạo trước ba đối tượng lỗi và giữ trong các field `checked`, `unchecked` và `error`. Các method tương ứng ném **đúng object đã tạo**, nên câu lệnh:

~~~java
ex == target.checked
~~~

chứng minh **identity** của ngoại lệ được giữ, chứ không chỉ tình cờ có cùng class hoặc thông điệp.

Bốn trường hợp:

| Lời gọi | Hành vi mã đích | Caller quan sát | Ghi thời gian |
| --- | --- | --- | --- |
| `execute("A")` | Trả về `done:A` | Nhận chuỗi kết quả | `succeeded=true` |
| `failChecked()` | Ném `IOException` | Bắt được chính `IOException` gốc | `succeeded=false` |
| `failRuntime()` | Ném `IllegalStateException` | Bắt được chính unchecked exception gốc | `succeeded=false` |
| `failError()` | Ném `AssertionError` | Bắt được chính `Error` gốc | `succeeded=false` |

Từng nhánh đều đi qua `finally`. Điều này bảo đảm ghi bản ghi ngay cả khi nghiệp vụ thất bại, **miễn là bản thân phép ghi metric không phát sinh lỗi phá vỡ luồng**. Khi dùng logger/metric backend thật, cần bảo vệ thao tác ghi khỏi lỗi mới để không che mất lỗi nghiệp vụ.

`succeeded` được đặt thành `true` **chỉ sau khi `Method.invoke` trả về bình thường**. Nó diễn tả trạng thái của **lời gọi method đích**, không chắc chắn mô tả kết quả của mọi adapter phía ngoài: proxy có thể còn cần cast/unbox giá trị trả về theo signature. Vì vậy, đừng dùng biến cờ này để tuyên bố toàn bộ request HTTP/transaction đã commit thành công.

## 6. Đo thời gian đúng cách

### 6.1. Dùng `System.nanoTime()`, không dùng đồng hồ hệ thống

`System.nanoTime()` phù hợp tính **elapsed duration**:

~~~java
long start = System.nanoTime();
try {
    return method.invoke(target, args);
} finally {
    long elapsedNanos = System.nanoTime() - start;
}
~~~

- Giá trị đơn lẻ của `nanoTime()` **không phải** mốc thời gian lịch; không chuyển trực tiếp sang ngày giờ.
- Dùng phép **lấy hiệu** hai lần đọc trong cùng JVM; tuyệt đối không so sánh timestamp `nanoTime` giữa hai JVM.
- Đây là số nanosecond theo đơn vị biểu diễn, **không bảo đảm độ phân giải phần cứng là một nanosecond**.
- Đặt mốc trước `method.invoke` nghĩa là thời gian bao gồm **chi phí gọi thông qua Reflection và dispatch bên trong method**, không thuần CPU time của nghiệp vụ.
- Với lời gọi async trả về `CompletableFuture` hay `Mono`, thời gian này có thể chỉ là thời gian **khởi tạo công việc**; để đo tới completion cần hook vào thời điểm tác vụ thực sự hoàn thành.

Đổi đơn vị để hiển thị (không tự làm tròn sai bằng phép chia kiểu double):

~~~java
long micros = java.util.concurrent.TimeUnit.NANOSECONDS.toMicros(elapsedNanos);
long millis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
~~~

Câu lệnh này là fragment cần biến `elapsedNanos`. Dùng clock đo elapsed để quan sát hoạt động; dùng `Instant.now()` hoặc logging timestamp nếu muốn ghi **thời điểm sự kiện**.

### 6.2. `finally` cần thiết nhưng không miễn nhiễm lỗi

Đặt phép ghi log trong `finally` sẽ chạy dù target trả về hoặc ném lỗi. Tuy nhiên, nếu `samples.add(...)`, backend metric hoặc logger **tự ném ngoại lệ**, ngoại lệ mới từ `finally` có thể **ghi đè** kết quả/ngoại lệ của nghiệp vụ.

Để bảo đảm không che lỗi vì lỗi ghi log trong tình huống thông thường, có thể tách một sink *best effort*:

~~~java
// Fragment bên trong finally, giả sử metricSink.record(...) có sẵn.
long elapsed = System.nanoTime() - start;
try {
    metricSink.record(method.getName(), elapsed, succeeded);
} catch (RuntimeException loggingFailure) {
    // Ghi lỗi của chính telemetry sang kênh an toàn khác nếu có.
    // Không ném lỗi logger đè lên exception nghiệp vụ.
}
~~~

Đây chỉ là **mẫu chính sách**: trong sản phẩm phải chủ động quyết định cách phát hiện và giám sát lỗi telemetry; không nên im lặng mãi khi hệ thống ghi log hỏng. Bắt `RuntimeException` ở đây không bảo đảm chống mọi `Error` nghiêm trọng (ví dụ thiếu bộ nhớ), và cũng không phải lời hứa tuyệt đối rằng không có cách nào che lỗi gốc.

Trong demo một thread, `ArrayList` là đủ. Nếu proxy được gọi đồng thời, **không dùng chung `ArrayList` không đồng bộ**; hãy chọn `ConcurrentLinkedQueue<Sample>`, `CopyOnWriteArrayList` (chỉ nếu tần suất ghi phù hợp), hoặc hệ thống metrics thread-safe.

## 7. Quy tắc của checked exception ở ranh giới proxy

`InvocationHandler.invoke` có thể `throw Throwable`, nhưng class proxy được sinh ra phải tuân thủ hợp đồng của **interface method**.

| Handler ném | Method interface khai báo | Caller nhận |
| --- | --- | --- |
| `IOException` | `throws IOException` (hoặc superclass phù hợp) | `IOException` gốc |
| `IOException` | Không khai báo checked exception phù hợp | `UndeclaredThrowableException` bọc `IOException` |
| `IllegalStateException` | Có hoặc không có `throws` | Unchecked exception gốc |
| `AssertionError` | Có hoặc không có `throws` | `Error` gốc |
| `InvocationTargetException` chưa bóc | Chỉ `throws IOException` | Thường `UndeclaredThrowableException` bọc `InvocationTargetException` |

Vì thế **bóc wrapper của Reflection là điều kiện cần, chưa phải điều kiện đủ** để mọi checked exception đi ra như ban đầu. Interface phải cho phép loại exception ấy.

Ví dụ một handler cố tình ném checked exception không được khai báo:

~~~java
interface NoChecked {
    void work();
}

NoChecked proxy = (NoChecked) Proxy.newProxyInstance(
        NoChecked.class.getClassLoader(),
        new Class<?>[] { NoChecked.class },
        (p, m, a) -> { throw new java.io.IOException("I/O"); });

try {
    proxy.work();
} catch (java.lang.reflect.UndeclaredThrowableException ex) {
    System.out.println(ex.getUndeclaredThrowable().getClass().getSimpleName());
}
~~~

Đây là **fragment Java 17+** để đặt trong method thích hợp với `Proxy` được import hoặc fully-qualified. Nó in `IOException`; `getUndeclaredThrowable()` tham chiếu tới checked exception mà handler đã ném.

Nếu một proxy cài đặt **nhiều interface có cùng tên/tham số**, tập checked exception được phép là những loại thỏa mãn `throws` của **tất cả** lời khai báo trùng liên quan, không chỉ interface đứng đầu danh sách. Điều này có thể khiến exception bị `UndeclaredThrowableException` bọc ngay cả khi một interface riêng lẻ khai báo nó.

**Khuyến nghị:** viết proxy theo contract interface rõ ràng; đừng “sửa” trường hợp ngoại lệ không được khai báo bằng ép tất cả lỗi thành `RuntimeException` nếu mục tiêu là giữ nguyên ngữ nghĩa mã đích.

## 8. Xử lý `equals`, `hashCode` và `toString`

JDK dynamic proxy cũng điều hướng ba method public, non-final của `Object` vào `InvocationHandler`. Với chúng, `method.getDeclaringClass() == Object.class`.

Nếu handler dùng:

~~~java
return method.invoke(proxy, args); // Sai: lại gọi chính proxy!
~~~

thì lần gọi mới lại chạy vào cùng handler và có thể dẫn đến đệ quy/`StackOverflowError`.

Demo chọn một chính sách **proxy identity**:

- `equals(other)` trả `self == other`.
- `hashCode()` trả `System.identityHashCode(self)`.
- `toString()` trả `TimedProxy(Job)`.
- Không đo các lời gọi `Object` này; danh sách `Sample` chỉ chứa method nghiệp vụ.

Một decorator khác có thể chọn delegate các lời gọi `Object` sang target, nhưng phải cân nhắc **đẳng thức đối xứng, transitivity** và hash contract. Không mặc định `target.equals(proxy)` sẽ đúng nếu proxy.equals(target)` do handler tùy biến quyết định; nhất quán `equals`/`hashCode` mới là điều quan trọng.

## 9. Không làm mất thông tin lỗi khi log

Một interceptor quan sát lỗi cần tách **nguyên nhân nghiệp vụ** khỏi **lỗi của chính hệ thống đo**.

Những điều nên làm:

1. **Không tạo lại exception** chỉ để log: `throw new IOException(ex.getMessage())` làm mất identity, stack trace gốc và có thể mất cause/suppressed.
2. **Không dùng `throw new RuntimeException(ex)`** nếu interface có hợp đồng checked exception mà caller cần bắt trực tiếp.
3. **Không viết `catch (Exception ex) { return null; }`**: nó vừa nuốt lỗi, vừa gây `NullPointerException` về sau nếu method trả primitive.
4. **Không quên `finally`**: nếu chỉ ghi metric sau `return method.invoke(...)`, nhánh throw sẽ không được đo.
5. **Tránh log dữ liệu nhạy cảm trong `args` hoặc return value**; bài thực hành chỉ ghi tên method và thời lượng.
6. **Không tự gọi lại method nghiệp vụ** để “thử lại timing” trừ khi retry là contract độc lập, được quyết định tường minh. Gọi hai lần có thể nhân đôi side effect.
7. **Không đóng nhãn “thành công” cho tác vụ bất đồng bộ** chỉ vì trả về một future; kết quả có thể thất bại sau đó.

Khi cần correlation trong ứng dụng có nhiều request, thêm một trace/request id vào metric, nhưng đừng dùng `proxy.toString()` trong chính handler để sinh log message.

## 10. Sai lầm đặc trưng khi dùng `Method.invoke`

### 10.1. Gọi trên sai receiver

`method` do proxy chuyển vào thường là method trên một **interface**. `target` phải implement interface tương ứng; nếu truyền instance sai, Reflection có thể ném `IllegalArgumentException`. Hàm `timedProxy` kiểm tra trước bằng:

~~~java
contract.isInterface() && contract.isInstance(target)
~~~

để phát hiện cấu hình sai trước khi bắt đầu tạo proxy. Điều kiện này không thay thế toàn bộ kiểm tra class loader, interface visibility/module hoặc method accessibility.

### 10.2. Method overload và nhận dạng metric

`method.getName()` đủ để trình bày ví dụ nhưng **không phân biệt** các overload như `run(String)` và `run(int)`. Với telemetry chuẩn, key có thể cần:

~~~java
String signature =
        method.getDeclaringClass().getName()
        + "#" + method.getName()
        + java.util.Arrays.toString(method.getParameterTypes());
~~~

Đây là fragment; có thể thêm return type hoặc module/classloader identity trong hệ thống nhiều loại proxy nếu định danh metric thực sự cần các thông tin đó. Tránh tạo label độc nhất theo input mỗi request vì cardinality cao làm metrics backend tốn bộ nhớ.

### 10.3. `null` và kiểu primitive

Khi method interface không nhận đối số, `args` có thể `null`; `Method.invoke(target, args)` dùng mảng đối số null hợp lệ cho phương thức không tham số. Khi method trả `void`, Reflection trả `null`. Ngược lại, nếu handler trả `null` cho method proxy khai báo trả `int`, proxy có thể ném `NullPointerException` trong unboxing; đây là lỗi adapter, không phải exception gốc của method đích.

### 10.4. Quyền truy cập

`Method.invoke` vẫn tuân thủ Java access checks và JPMS encapsulation. Tạo proxy không tự mở private package hay bỏ qua `IllegalAccessException`. Trường hợp dùng public interface nhưng implementation/lookup ở module khác cần bảo đảm receiver, declaring type và visibility thích hợp. Bài demo cố ý dùng `public interface Job` và `public static final class RealJob` để tránh nhiễu do quyền truy cập.

## 11. Kiểm thử có giá trị thực tế

Thay vì chỉ so sánh tên class của exception, hãy kiểm tra **object identity** và dữ liệu đo:

| Ca | Điều cần xác minh |
| --- | --- |
| Lời gọi trả về | Giá trị đúng và có đúng một bản ghi timing thành công |
| Checked exception khai báo | Caller bắt trực tiếp đúng loại, `caught == original` |
| Runtime exception | Caller bắt đúng object gốc, không bị bọc |
| `Error` | Caller nhận đúng `Error`, timing vẫn được ghi |
| `Object.toString` | Không đệ quy vô hạn, theo chính sách log đã định |
| Lỗi ở metric sink | Không được thay exception nghiệp vụ trong các trường hợp backend logging lỗi có thể xử lý |
| Hai method overload | Metric key phân biệt được nếu yêu cầu quan sát theo signature |
| Gọi đồng thời | Data sink và counter thread-safe, không mất record |
| Có nhiều interface với method trùng | Checked exception phải hợp lệ với mọi contract |

Trong demo, câu `checked preserved: true` là bằng chứng mạnh hơn việc in `IOException`. Đối với benchmark hiệu năng, đo nhiều lần và sử dụng **JMH** khi cần kết luận định lượng; ví dụ ngắn chỉ chứng minh luồng logic đo, không phải số nanosecond ổn định hay mức chi phí của proxy.

## 12. Tình huống nâng cao và giới hạn lựa chọn

**Nested proxies:** Nếu target lại là một proxy khác, tổng thời gian ở lớp ngoài bao gồm lớp trong. Nếu lớp trong thay đổi/wrap exception, outer handler chỉ nhìn thấy exception thực tế target proxy đã ném; không thể bảo đảm khôi phục một “ngoại lệ sâu hơn” nếu không biết hợp đồng nhiều lớp.

**Default methods:** Lời gọi default method trên interface cũng được đưa đến handler. `Method.invoke(target,args)` chỉ đúng khi bạn **cố ý delegate tới target** implement interface phù hợp. Nếu muốn chạy default body **trên chính proxy** theo Java 17, xem `InvocationHandler.invokeDefault` và các điều kiện truy cập/override riêng; không gọi lại default method trên proxy như lệnh delegate.

**`MethodHandle`:** Có thể dùng `MethodHandle` thay `Method.invoke` cho một số đường gọi sau khi xây lookup/access thích hợp. MethodHandle thường truyền exception từ target mà không bọc `InvocationTargetException` theo cơ chế của `Method.invoke`, nhưng bạn vẫn phải giữ nguyên contract exception, đo timing, và xử lý adapter errors.

**Spring AOP:** Có thể đo thời gian bằng around advice với `ProceedingJoinPoint.proceed()`, nơi exception từ join point có thể được truyền ra qua `throws Throwable`. Cách unwrap khác nhau giữa proxy/advice; **không áp dụng vô điều kiện** quy tắc bóc `InvocationTargetException` ở mọi framework, chỉ dùng khi chính bạn gọi `Method.invoke`.

**Async/reactive:** Muốn đo thời gian nghiệp vụ hoàn tất chứ không chỉ thời gian trả về future/reactive publisher, cần thiết kế metric lúc completion/error/subscribe phù hợp với mô hình concurrency. Không được “block” async chỉ để tái sử dụng interceptor đồng bộ của ví dụ.

## 13. Câu hỏi tự kiểm tra

1. Vì sao `method.invoke` khiến caller thấy exception lạ nếu handler không unwrap? Vì ngoại lệ từ method đích bị bọc trong `InvocationTargetException`, và proxy có thể bọc tiếp thành `UndeclaredThrowableException`.
2. Đâu là cách giữ nguyên throwable gốc? **`catch (InvocationTargetException ex) { throw ex.getCause(); }`**.
3. Vì sao không thể bảo đảm giữ checked exception khi interface không có `throws` phù hợp? Vì bytecode proxy phải tôn trọng hợp đồng checked exception của interface; trường hợp không hợp lệ bị bọc thành `UndeclaredThrowableException`.
4. Dùng `System.nanoTime()` để lấy “ngày giờ gọi method” có đúng không? **Không**; nó dùng đo khoảng thời gian bằng phép trừ, không phải wall-clock timestamp.
5. Vì sao ghi metric trong `finally`? Để ghi cả nhánh return và nhánh exception. Nhưng phải hạn chế lỗi của chính metric sink che lỗi chính.
6. Kiểm tra `ex == target.checked` xác minh điều gì? **Cùng identity Throwable**, không chỉ cùng class hoặc nội dung thông báo.
7. Có nên gọi `method.invoke(proxy, args)` từ handler không? **Không**, vì có thể lặp lại handler vô hạn. Delegate vào `target`.
8. Dùng `ArrayList` chung để ghi timing từ nhiều thread an toàn không? **Không**; dùng cấu trúc đồng thời hoặc recorder có cơ chế thread safety.
9. Vì sao thời gian đo không luôn là thời gian “method thật” thuần túy? Vì khoảng đo bao gồm cả Reflective Invocation và overhead liên quan; phương thức async có thể hoàn thành sau.
10. Tại sao `toString`/`equals`/`hashCode` cần chính sách riêng? Vì ba method này cũng được JDK proxy điều hướng qua handler.

## 14. Tài liệu tham khảo chính thức Java 17

- [Oracle Java SE 17 — Proxy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html): cách dispatch method, xử lý `Object` methods và giới hạn checked exception.
- [Oracle Java SE 17 — InvocationHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html): hợp đồng `invoke` và `invokeDefault`.
- [Oracle Java SE 17 — Method](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): `invoke` và hành vi bọc exception.
- [Oracle Java SE 17 — InvocationTargetException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html): `getCause()` trả ngoại lệ của method đích.
- [Oracle Java SE 17 — UndeclaredThrowableException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/UndeclaredThrowableException.html): wrapper cho checked exception không được interface khai báo.

**Ghi nhớ:** Dynamic proxy có thể đo thời gian và chuyển tiếp minh bạch khi handler dùng `System.nanoTime()` trong `try/finally`, gọi **target thật**, và **ném lại đúng `InvocationTargetException.getCause()`**. Ngoại lệ checked vẫn phải được interface cho phép; metric/logging không được làm mất kết quả hoặc throwable của nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-practice-009">So sánh gọi trực tiếp, Method.invoke và MethodHandle</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh gọi trực tiếp, Method.invoke và MethodHandle trong Java

Trong Java, cùng một method có thể được gọi bằng nhiều cách: **gọi trực tiếp** bằng cú pháp Java thông thường, **gọi phản chiếu** qua `java.lang.reflect.Method.invoke(...)` hoặc **gọi động có chữ ký kiểu** qua `java.lang.invoke.MethodHandle`. Cả ba có thể dẫn đến cùng một hành vi nghiệp vụ, nhưng khác nhau ở thời điểm kiểm tra kiểu, cách tìm method, xử lý giá trị primitive, quyền truy cập, exception và cơ hội tối ưu hóa.

Đây là một bài thực hành trong Java Core – Reflection. Mục tiêu không phải kết luận rằng một API luôn nhanh nhất, mà là **hiểu khi nào mỗi cách phù hợp và quan sát chính xác hành vi trên Java 17+**.

## 1. Ba cơ chế ở một cái nhìn

Giả sử có class:

```java
class Calculator {
    int add(int a, int b) {
        return a + b;
    }
}
```

Đây là đoạn minh họa, không phải chương trình hoàn chỉnh.

### A. Gọi trực tiếp

```java
Calculator calculator = new Calculator();
int result = calculator.add(3, 4);
```

Compiler biết `Calculator`, tên method, số lượng và kiểu đối số. Nếu đổi tên method hoặc truyền đối số không phù hợp, thông thường lỗi được phát hiện khi biên dịch lại. Với instance method có dispatch động, JVM vẫn chọn implementation được override phù hợp ở runtime; “gọi trực tiếp” **không đồng nghĩa** mọi lời gọi được quyết định cứng tại compile time.

### B. Reflection qua Method.invoke

```java
Method method = Calculator.class.getDeclaredMethod(
        "add", int.class, int.class);
Object result = method.invoke(new Calculator(), 3, 4);
```

Đoạn minh họa giả định có import `java.lang.reflect.Method`, method được truy cập hợp lệ và xử lý checked exception. `getDeclaredMethod(...)` dùng **tên dưới dạng chuỗi** và đúng kiểu tham số để lấy metadata. Sau đó `invoke(...)` mới thực sự gọi method. Kết quả kiểu `int` được **boxing** thành `Integer` vì `Method.invoke` trả `Object`.

### C. MethodHandle

```java
MethodHandle handle = MethodHandles.lookup().findVirtual(
        Calculator.class,
        "add",
        MethodType.methodType(int.class, int.class, int.class));
int result = (int) handle.invokeExact(new Calculator(), 3, 4);
```

Đoạn minh họa cần import `java.lang.invoke.*` và xử lý `Throwable`. `findVirtual` tìm instance method có **return type là `int`** và **hai tham số `int`**. `MethodHandle` chưa gắn receiver nên khi gọi, `Calculator` là đối số đầu tiên. `invokeExact` đòi hỏi kiểu tĩnh tại call site khớp chính xác kiểu handle; kết quả là `int`, không cần đi qua một kết quả `Object` do API trả về như `Method.invoke`.

Lưu ý: MethodHandle cũng có thể dùng để tìm constructor, getter/setter, static method, default method và xây tổ hợp lời gọi bằng các phép thích nghi handle. Ví dụ trong bài tập trung vào lời gọi method thường gặp.

## 2. So sánh chi tiết

| Tiêu chí | Gọi trực tiếp | `Method.invoke` | `MethodHandle` |
|---|---|---|---|
| Xác định method | Symbol Java trong mã nguồn | Tra cứu đối tượng `Method`, thường bằng tên chuỗi | Tra cứu hoặc chuyển đổi thành handle có `MethodType` |
| Kiểm tra kiểu | Compiler kiểm tra lời gọi | `invoke` kiểm tra receiver và đối số ở runtime | `invokeExact` yêu cầu chữ ký call site trùng chính xác; `invoke` có thể thích nghi |
| Kiểu trả về | Khai báo của method, ví dụ `int` | `Object`, primitive bị boxing, `void` là `null` | Kiểu trả về trong `MethodType`, ví dụ `int` |
| Receiver instance | Viết `receiver.method(...)` | Đối số đầu tiên của `Method.invoke` | Có thể là đối số đầu của `findVirtual` handle hoặc được bind sẵn |
| Chữ ký | Theo mã nguồn, có overload resolution | Thường `getMethod(name, paramTypes...)` | `MethodType` có kiểu trả về và các kiểu tham số |
| Exception từ method đích | Ném theo luồng Java thông thường | Bọc trong `InvocationTargetException` | MethodHandle invocation thông thường ném trực tiếp exception từ target |
| Access control | Theo luật Java trong source và runtime | Reflection kiểm tra access/module; có API thử mở quyền | `Lookup` có quyền theo lookup class/module; access kiểm tra khi tạo handle |
| Đổi tên method | Compiler thường bắt usage source cũ | Chuỗi cũ có thể thất bại khi chạy | Chuỗi dùng trong lookup cũng có thể thất bại khi chạy |
| Chuyển đổi đối số | Quy tắc Java compiler áp dụng cho lời gọi nguồn | Boxing/unboxing và primitive widening hợp lệ theo Reflection, không narrowing tùy ý | `invokeExact` không tự chuyển đổi; `invoke`/adapters hỗ trợ chuyển đổi hợp lệ |
| Phù hợp | Logic ứng dụng thông thường | Framework cần metadata, introspection, gọi linh hoạt | Framework/runtime cần lời gọi động theo kiểu, combinators và tối ưu hóa |

**Điểm then chốt:** MethodHandle không “lấy mất tính động” của Reflection. Khi dùng `findVirtual(Class, String, MethodType)`, bạn vẫn có tên method bằng chuỗi và vẫn có thể gặp lỗi lookup sau refactor. Ưu điểm chủ yếu là **mô hình invocation có kiểu rõ ràng và khả năng tổ hợp/adaptation**, không phải loại bỏ mọi rủi ro liên kết runtime.

## 3. Quá trình từ lookup đến invocation

Phân biệt hai bước độc lập:

```text
                  CHỌN METHOD                       THỰC THI
Gọi trực tiếp:  symbol/compiler               -> invokevirtual/invokestatic...
Reflection:     Class.getMethod(...)           -> Method.invoke(...)
MethodHandle:   Lookup.findVirtual/findStatic  -> invokeExact/invoke/...
                    |                                  |
               thông tin thành viên              chạy nghiệp vụ thật
```

Các API `Class.getMethod(...)` hay `Lookup.findVirtual(...)` dùng để tìm thành viên, **không tự gọi method nghiệp vụ**. Chỉ bước invocation mới thực hiện logic target. Việc tải/resolve lớp hoặc kiểm tra quyền khi lookup vẫn có thể gây lỗi và hoạt động JVM khác; không nên suy rằng lookup hoàn toàn vô tác động trong mọi môi trường.

### `Method.invoke` là phương thức trên một đối tượng metadata

Một `Method` là metadata của method khai báo. Để gọi instance method:

```java
Object value = method.invoke(receiver, arg1, arg2);
```

- `receiver` phải là instance tương thích với declaring class của method.
- Với static method, tham số receiver bị bỏ qua theo API; thường truyền `null` để minh bạch.
- Với method không tham số, có thể truyền không đối số, hoặc đối số mảng thích hợp.
- `Method.invoke` khai báo `Object` là kiểu trả về; cần cast/unbox khi muốn dùng kiểu cụ thể.
- Nếu invoked method ném exception, `invoke` ném `InvocationTargetException` chứa lỗi gốc trong `getCause()`.

**Cảnh báo varargs:** bản thân `Method.invoke(Object, Object...)` là một Java varargs API. Nếu truyền array làm **một tham số của method đích**, cần chuẩn bị đúng mảng `Object[]` chứa một phần tử, tránh compiler trải nhầm array thành danh sách tham số của `invoke`. Đây là vấn đề ở lớp gọi Reflection, không phải thay đổi signature của target.

### `MethodHandle` là điểm gọi có kiểu

`MethodHandle.type()` trả về một `MethodType` gồm return type và danh sách tham số **của chính handle**. Với instance method `int add(int,int)`, kết quả `findVirtual` chưa bind receiver có dạng:

```text
(Calculator,int,int)int
```

Đối số đầu tiên là receiver; hai đối số còn lại là tham số Java của method đích. Dùng `bindTo(calculator)` sẽ biến handle đó thành:

```text
(int,int)int
```

Khi đó caller không truyền receiver nữa. Binding làm thay đổi **chữ ký handle**, không sửa class hoặc tạo một method mới trong class đích.

Cũng cần phân biệt:

- `findVirtual(...)`: tìm virtual instance method; handle cần receiver và có virtual dispatch phù hợp.
- `findStatic(...)`: tìm static method; handle **không có receiver**.
- `findConstructor(...)`: handle biểu diễn thao tác tạo đối tượng; kiểu trả về handle là class mới tạo.
- `Lookup.unreflect(Method)`: có thể tạo MethodHandle từ `Method`, nhưng vẫn chịu điều kiện quyền truy cập phù hợp.
- `invokeExact(...)`: call site phải có tham số **và return type** trùng chính xác với `handle.type()`.
- `invoke(...)`: có thể áp dụng thích nghi kiểu theo `asType` trong các chuyển đổi được hỗ trợ; không thể coi là chuyển mọi kiểu tùy ý.
- `invokeWithArguments(...)`: giao diện linh hoạt lấy danh sách đối số như `Object`; thích hợp tiện ích/framework, nhưng không nên dùng để suy ra hiệu năng của `invokeExact`.
- Các combinator như `asType`, `bindTo`, `filterArguments`, `insertArguments` cho phép xây đường gọi phù hợp mà không cần viết wrapper method thông thường cho mọi trường hợp.

## 4. Ví dụ Java 17+ hoàn chỉnh, có output kiểm chứng

Chương trình bên dưới dùng một `Calculator` có instance method, static method và một method cố tình ném lỗi. Nó đối chiếu kết quả khi gọi trực tiếp, Reflection, MethodHandle chưa bind/bound, `invokeWithArguments`, đồng thời kiểm tra **hai kiểu lỗi khác nhau**.

Lưu thành **`InvocationComparisonDemo.java`**:

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InvocationComparisonDemo {
    public static class Calculator {
        private final int offset;
        public Calculator(int offset) { this.offset = offset; }
        public int add(int left, int right) { return offset + left + right; }
        public int risky(int denominator) {
            if (denominator == 0) throw new IllegalArgumentException("zero");
            return offset / denominator;
        }
        public static String describe(String label, int value) {
            return label + "=" + value;
        }
    }

    public static void main(String[] args) throws Throwable {
        Calculator calculator = new Calculator(10);
        System.out.println("direct: " + calculator.add(3, 4));

        Method method = Calculator.class.getMethod("add", int.class, int.class);
        Object reflected = method.invoke(calculator, 3, 4);
        System.out.println("reflection: " + reflected + " ("
                + reflected.getClass().getSimpleName() + ")");

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle virtual = lookup.findVirtual(
                Calculator.class, "add",
                MethodType.methodType(int.class, int.class, int.class));
        int exact = (int) virtual.invokeExact(calculator, 3, 4);
        System.out.println("handle exact: " + exact);
        System.out.println("unbound type: " + virtual.type());

        MethodHandle bound = virtual.bindTo(calculator);
        System.out.println("bound type: " + bound.type());
        int boundValue = (int) bound.invokeExact(3, 4);
        System.out.println("handle bound: " + boundValue);
        Object generic = bound.invokeWithArguments(3, 4);
        System.out.println("handle generic: " + generic);

        MethodHandle staticHandle = lookup.findStatic(
                Calculator.class, "describe",
                MethodType.methodType(String.class, String.class, int.class));
        String label = (String) staticHandle.invokeExact("sum", 17);
        System.out.println("static handle: " + label);

        try {
            method.invoke(calculator, "3", 4);
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "reflection wrong argument: IllegalArgumentException");
        }
        try {
            long wrong = (long) bound.invokeExact(3, 4);
            System.out.println("unreachable: " + wrong);
        } catch (WrongMethodTypeException e) {
            System.out.println(
                    "handle wrong signature: WrongMethodTypeException");
        }

        Method risky = Calculator.class.getMethod("risky", int.class);
        try {
            risky.invoke(calculator, 0);
        } catch (InvocationTargetException e) {
            System.out.println("reflection target error: "
                    + e.getCause().getClass().getSimpleName());
        }
        MethodHandle riskyHandle = lookup.findVirtual(
                Calculator.class, "risky",
                MethodType.methodType(int.class, int.class));
        try {
            int unused = (int) riskyHandle.invokeExact(calculator, 0);
            System.out.println("unreachable: " + unused);
        } catch (IllegalArgumentException e) {
            System.out.println("handle target error: "
                    + e.getClass().getSimpleName());
        }
    }
}
```

Lệnh biên dịch/chạy:

```bash
javac --release 17 -encoding UTF-8 InvocationComparisonDemo.java
java InvocationComparisonDemo
```

**Stdout thực tế của chương trình trên:**

```text
direct: 17
reflection: 17 (Integer)
handle exact: 17
unbound type: (Calculator,int,int)int
bound type: (int,int)int
handle bound: 17
handle generic: 17
static handle: sum=17
reflection wrong argument: IllegalArgumentException
handle wrong signature: WrongMethodTypeException
reflection target error: IllegalArgumentException
handle target error: IllegalArgumentException
```

Ví dụ đã được biên dịch với `javac --release 17` và chạy để đối chiếu stdout; không yêu cầu phụ thuộc ngoài JDK.

### Đọc từng kết quả

1. `Calculator(10).add(3, 4)` trả `10 + 3 + 4 = 17` ở cả ba đường gọi hợp lệ.
2. Reflection trả `Object` mang giá trị runtime `Integer(17)`. Trong Java source, `invoke` nhận đối số primitive qua varargs `Object...`, nên cần boxing khi đóng gói đối số.
3. Handle chưa bind có chữ ký `(Calculator,int,int)int`. `invokeExact(calculator, 3, 4)` cần receiver và hai số nguyên `int`.
4. `bindTo(calculator)` cố định receiver; chữ ký trở thành `(int,int)int`.
5. `invokeWithArguments(3, 4)` nhận cách truyền linh hoạt, trả giá trị trong dạng `Object`, nên ở ví dụ nhận `17` qua `Object generic`.
6. `findStatic` có chữ ký `(String,int)String` và không nhận `Calculator` làm receiver.
7. Truyền chuỗi `"3"` cho Reflection method yêu cầu `int` bị từ chối với `IllegalArgumentException`. Reflection không tự parse văn bản thành số nguyên.
8. Dòng `long wrong = (long) bound.invokeExact(3, 4)` cố tình dùng **return type của call site là `long`**, nhưng handle trả `int`. Điều đó dẫn đến `WrongMethodTypeException`, kể cả khi đối số `int` đều đúng.
9. `risky(0)` thực sự ném `IllegalArgumentException`. Qua `Method.invoke`, lỗi target được bọc trong `InvocationTargetException`. Qua MethodHandle, lỗi target đi thẳng ra ngoài trong trường hợp này. Hai dòng cùng in tên `IllegalArgumentException` vì code chỉ in **cause hoặc exception thật**, không in lớp wrapper của Reflection.

## 5. Phân biệt invokeExact, invoke và invokeWithArguments

Ba cách dùng MethodHandle không thể hoán đổi một cách tùy ý.

### invokeExact: ràng buộc chặt kiểu tĩnh tại call site

```java
MethodHandle h = MethodHandles.lookup().findStatic(
        Integer.class, "sum",
        MethodType.methodType(int.class, int.class, int.class));
int value = (int) h.invokeExact(2, 3);
```

Đây là đoạn minh họa riêng, yêu cầu xử lý `Throwable`, và `Integer.sum(int,int)` là static method. Trình biên dịch Java gán cho call site chữ ký gồm hai `int` và kết quả `int`. Nếu viết `Object value = h.invokeExact(2, 3)`, return type ở call site sẽ là `Object` và **không khớp** handle trả `int`, dẫn đến `WrongMethodTypeException` khi chạy. Ép kiểu `(int)` ở mã nguồn có ý nghĩa thật đối với kiểu tĩnh được ghi tại call site; nó không phải cast trang trí.

### invoke: có thể thích nghi nhưng vẫn có giới hạn

```java
int value = (int) h.invoke(2, 3);
```

`invoke` không bắt buộc khớp tuyệt đối như `invokeExact` và có thể vận dụng quy tắc conversion thích hợp của MethodHandle. Tuy nhiên không phải mọi khác biệt đều được hỗ trợ, và chi phí/thành công tùy loại chuyển đổi. Có thể dùng `h.asType(desiredMethodType)` khi cần tạo adapter có chữ ký mục tiêu rõ ràng.

### invokeWithArguments: thuận tiện cho danh sách runtime

```java
Object value = h.invokeWithArguments(2, 3);
```

Đoạn này hữu dụng cho framework có danh sách đối số dạng `Object[]` hoặc `List<?>`. Nó thường có nhiều công việc thích nghi/đóng gói hơn một lời gọi `invokeExact` với kiểu ổn định, nên không nên dùng kết quả đo của nó để suy ra hiệu năng tối đa của MethodHandle.

Cả ba snippet này giả định `h` nằm trong phạm vi phù hợp, có imports liên quan và exception được xử lý. Một chương trình đầy đủ đã có ở mục 4.

## 6. Boxing, primitive và overload là nguồn lỗi phổ biến

### Method.invoke

Giả sử method đích có chữ ký `int sum(int,int)`:

- `Method.invoke(target, 2, 3)` cho phép primitive arguments được boxing trước khi đi qua `Object...`; Reflection thực hiện chuyển đổi tương thích để gọi method đích.
- Trả về `int` được boxing thành `Integer` ở lớp kết quả `Object`.
- Nếu method đích trả `void`, kết quả `Method.invoke` là `null`.
- Passing `null` vào tham số primitive không hợp lệ.
- Truyền `Integer` khi tham số yêu cầu `long` có thể được unbox và primitive widening theo quy tắc Reflection; truyền `Long` khi yêu cầu `int` không tự narrowing. Truyền `String` không tự parse thành số.

### getMethod lookup phải khớp kiểu tham số

```java
Method m = Calculator.class.getMethod("add", int.class, int.class);
```

`getMethod("add", Integer.class, Integer.class)` **không tương đương**, dù Java source có autoboxing. Phần lookup bằng `getMethod` không thực hiện overload resolution linh hoạt như compiler. MethodHandle lookup còn thêm **return type** vào `MethodType`.

### Lưu ý generic và array

Java generic phần lớn bị xóa kiểu ở bytecode (type erasure), nên Reflection và MethodHandle lookup dựa trên kiểu runtime đã erased, không phải tham số kiểu `List<String>` như symbol độc lập. MethodHandle có combinator cho array spreading/collecting; khi một method nhận `int[]` hoặc `Object[]`, hãy kiểm tra chữ ký handle và cách truyền varargs để không nhầm một mảng đối số với một đối số là mảng.

## 7. Access control, lỗi tra cứu và exception

### Quyền truy cập không tự biến mất khi dùng Reflection

- Gọi trực tiếp chịu access rule từ mã nguồn, package, module và tính hợp lệ ở runtime.
- `Class.getDeclaredMethod` **có thể tìm thấy** private method, nhưng điều đó không đồng nghĩa `Method.invoke` được phép gọi.
- `Method.setAccessible(true)` / `trySetAccessible()` có thể cho phép deep reflection **chỉ khi các quy tắc module/package cho phép**. Việc dùng bừa bãi có thể bị từ chối hoặc làm thiết kế khó kiểm soát.
- `MethodHandles.lookup()` tạo `Lookup` có quyền gắn với lớp gọi. `findVirtual`, `findStatic` và `unreflect` kiểm tra các điều kiện tiếp cận tương ứng.
- Với private members, `MethodHandles.privateLookupIn(...)` là lựa chọn có kiểm tra quyền và điều kiện mở package phù hợp, không phải cách vượt mọi module encapsulation.

**Không được coi Reflection hay MethodHandle là phương tiện vượt phân quyền.** Chúng là cơ chế gọi code trong những giới hạn runtime và module. Lúc thiết kế plugin/tool runner, việc gọi method nào còn cần allowlist và authorization ở mức ứng dụng.

### Tra cứu lỗi và gọi lỗi là hai nhóm riêng

| Tình huống | Reflection | MethodHandle |
|---|---|---|
| Tên method sai | `NoSuchMethodException` lúc `getMethod`/`getDeclaredMethod` | `NoSuchMethodException` lúc `Lookup.find...` |
| Không truy cập được | `IllegalAccessException` / `InaccessibleObjectException` tùy bước | `IllegalAccessException` lúc lookup hoặc chuyển handle |
| Receiver sai | Thường `IllegalArgumentException` tại `Method.invoke` | Lỗi liên quan mismatch kiểu/receiver tùy cơ chế invoke |
| Đối số không khớp | `IllegalArgumentException` tại `Method.invoke` | `WrongMethodTypeException` ở call site không thích hợp hoặc conversion-related exception |
| Method đích ném lỗi | `InvocationTargetException` bao lỗi gốc | Thường ném trực tiếp lỗi gốc từ target |
| Lớp đích khởi tạo thất bại | Có thể thấy `ExceptionInInitializerError`/lỗi liên quan | Cũng có thể gặp lỗi initialization, tùy lúc method đích được gọi |

Không nên bắt mọi `Exception` rồi báo “method không tồn tại”. Phân biệt lookup/permission/type mismatch với lỗi nghiệp vụ của target. Khi log exception từ `Method.invoke`, xem `InvocationTargetException.getCause()` nhưng vẫn nên giữ thông tin wrapper/context khi chẩn đoán.

## 8. Hiệu năng: so sánh đúng cách, tránh kết luận tuyệt đối

Thứ tự “gọi trực tiếp nhanh hơn MethodHandle nhanh hơn Reflection” đôi khi là một kinh nghiệm phổ biến, **không phải định luật cho mọi JDK và tình huống**.

Những yếu tố phải xem:

1. **JIT và inlining:** compiler có thể tối ưu lời gọi trực tiếp rất tốt khi biết target; MethodHandle với handle ổn định cũng có cơ hội tối ưu hóa mạnh. Reflection trong các JDK hiện đại cũng đã được cải tiến, không thể dựa mãi trên một benchmark cũ.
2. **Chi phí lookup:** nếu lấy `Method`/`MethodHandle` mới ở **mỗi vòng lặp**, bạn đang đo cả discovery và invocation, không chỉ chi phí gọi.
3. **Kiểu đối số và boxing:** Reflection phải đi qua giao diện `Object...`; `invokeWithArguments` cũng có chuyển đổi/đóng gói. Một benchmark trả `Object` và benchmark trả `int` chưa chắc so sánh cùng lượng công việc.
4. **Monomorphic vs megamorphic call sites:** số loại target và khả năng JVM biết trước handle ảnh hưởng rất lớn đến tối ưu hóa.
5. **Workload thật:** khi method đích thực hiện I/O, database, tính toán lớn hoặc network, overhead dispatch có thể nhỏ hơn nhiều so với công việc thực.
6. **JDK/runtime version và warmup:** JVM cần warmup; kết quả có thể khác trên Java 17, 21 hoặc JDK tương lai và giữa các triển khai.
7. **Benchmark dead-code elimination:** nếu không sử dụng kết quả, JIT có thể loại bỏ phần việc, khiến số đo vô nghĩa.

Một phép đo nghiêm túc nên dùng **JMH (Java Microbenchmark Harness)**, có warmup, nhiều lần đo, fork JVM và consumer/return value để giữ công việc quan sát được. Dùng cùng implementation và dữ liệu, **cache** method và handle nếu đó là cách ứng dụng thật hoạt động, tách riêng phép đo lookup. Sau đó mới so sánh latency/throughput kèm JDK và cấu hình JVM.

Nếu cần đo quyết định kiến trúc, đừng benchmark `Method.invoke` với target làm I/O trong khi nhánh khác chỉ cộng hai số. Cũng không nên khuyên chuyển mọi code sang MethodHandle chỉ vì một microbenchmark. Khả năng bảo trì và tính rõ ràng của code thường quan trọng hơn vài nanosecond trong nghiệp vụ bình thường.

## 9. Khi nào nên chọn cách nào?

| Nhu cầu | Gợi ý |
|---|---|
| Gọi method đã biết rõ ở compile time | **Gọi trực tiếp**, ưu tiên interface/method reference rõ ràng |
| Framework cần liệt kê annotation, method, generic metadata | **Reflection metadata**; gọi qua `Method.invoke` nếu cần tính linh hoạt |
| Runtime xây call chain động, cần adapters/combinators có kiểu | **MethodHandle** |
| Chạy cùng một method động nhiều lần với cấu trúc ổn định | Cân nhắc **cache MethodHandle** và đánh giá bằng benchmark thật |
| Input là tên class/method từ config | Reflection hoặc Lookup đều cần **validate allowlist, schema, quyền** |
| Code dùng proxy, serializer, DI hoặc RPC | Chọn công cụ theo framework và API contract, không chỉ theo lý thuyết tốc độ |
| Logic thông thường có API ổn định | Không thêm Reflection/MethodHandle nếu lời gọi trực tiếp đã đơn giản và phù hợp |

Một thực hành hay là tách giai đoạn **discovery** (đọc `Method`/annotation hoặc tìm `MethodHandle`) và **execution** (gọi thật). Khám phá metadata không nên tự chạy nghiệp vụ. Chỉ sau khi kiểm tra schema và quyền, executor mới thực hiện `invoke`.

## 10. Những lỗi người học thường gặp

- **Nhầm Reflection với MethodHandle:** `Method` là đối tượng metadata với API `invoke(Object,Object...)`; `MethodHandle` là callable với `MethodType`, có thể bind/adapt/combine. Chúng không phải cùng một abstraction.
- **Quên receiver của `findVirtual`:** handle ban đầu thường có receiver là đối số đầu tiên. `bindTo` mới cố định đối tượng.
- **Cho rằng `invokeExact` tự widening `int` thành `long`:** Không; cả return type của call site cũng phải khớp.
- **Cho rằng `Method.invoke` trả `int`:** API luôn trả `Object`; primitive được boxing.
- **Bỏ quên `InvocationTargetException`:** Reflection wrapper che cause thực sự nếu không mở ra.
- **Tra cứu bằng kiểu wrapper thay primitive:** `int.class` khác `Integer.class` khi lookup.
- **Bỏ qua access/module rules:** Tìm thấy private method không đồng nghĩa có quyền thực thi nó.
- **Tạo method/handle mới mỗi lần gọi rồi đo tốc độ:** Số liệu trộn lookup cost và invocation cost.
- **Tin rằng handle luôn nhanh hơn:** Cần test thực nghiệm trên JDK và workload thực tế.
- **Đổi tên method mà quên chuỗi runtime:** `getMethod("oldName",...)` và `findVirtual(...,"oldName",...)` đều có thể hỏng sau refactor nếu string không được cập nhật.

## 11. Tự kiểm tra

**Câu 1.** `Method.invoke(calculator, 3, 4)` gọi method trả `int`. Biến nhận là `Object` sẽ chứa kiểu nào?

Một `Integer` đã boxing giá trị `int`, giả sử method thực thi thành công.

**Câu 2.** Vì sao `findVirtual(Calculator.class, "add", methodType(int.class,int.class,int.class))` có kiểu handle `(Calculator,int,int)int`?

Vì virtual method cần **receiver** đầu tiên ở call site của handle, trước hai tham số của method gốc. `MethodType` truyền vào `findVirtual` mô tả signature Java của method **không tính receiver**, còn handle trả về có thêm receiver.

**Câu 3.** Có `Method` từ `getMethod` nghĩa là method đã chạy?

Không. Lookup trả metadata; `Method.invoke` mới yêu cầu thực thi.

**Câu 4.** Vì sao `long x = (long) bound.invokeExact(3,4)` lỗi dù có thể đổi một giá trị `int` sang `long` trong Java bình thường?

Vì `invokeExact` đòi hỏi **return type của call site là `int`** theo kiểu handle. Khai báo/cast sang `long` ngay tại call site làm chữ ký yêu cầu khác và ném `WrongMethodTypeException`. Nếu muốn chuyển đổi, nhận `int` trước rồi widening trong Java: `int tmp = (int) bound.invokeExact(3,4); long x = tmp;`, hoặc dùng adapter hợp lệ.

**Câu 5.** Khi method đích ném `IllegalArgumentException`, khác biệt nổi bật giữa Reflection và MethodHandle?

`Method.invoke` bọc target exception trong `InvocationTargetException`, còn invocation qua MethodHandle thông thường ném trực tiếp exception của target. Cần phân biệt lỗi thực thi với lỗi lookup/chữ ký.

**Câu 6.** Cách nào luôn nhanh nhất?

Không có đáp án cố định cho mọi JDK và ứng dụng. Với method đã biết tĩnh, thường ưu tiên direct call vì rõ ràng và compiler kiểm tra kiểu; nếu yêu cầu động, thử Reflection/MethodHandle theo chức năng, dùng JMH đo trên môi trường tương ứng.

## Tài liệu tham khảo

- Java SE 17 API: [`java.lang.reflect.Method`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html) — metadata, `invoke`, access, `InvocationTargetException`.
- Java SE 17 API: [`java.lang.invoke.MethodHandle`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandle.html) — `invokeExact`, `invoke`, adapters.
- Java SE 17 API: [`java.lang.invoke.MethodHandles.Lookup`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html) — `findVirtual`, `findStatic`, `unreflect`, kiểm tra quyền.
- Java SE 17 API: [`java.lang.invoke.MethodType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodType.html) — chữ ký kiểu của handle.
- [OpenJDK JMH](https://github.com/openjdk/jmh) — microbenchmark cho JVM; đo đúng workload và cấu hình thực tế.
- Java Language Specification §15.12 (method invocation), JVM Specification §6.5 (lệnh invoke), §5 (loading/linking/initialization).

**Ghi nhớ:** **Gọi trực tiếp** có cú pháp rõ và compiler kiểm tra kiểu; **`Method.invoke`** gọi động qua metadata, đối số/kết quả có giao diện `Object` và bọc exception đích; **`MethodHandle`** có chữ ký `MethodType`, hỗ trợ lời gọi kiểu chặt, binding và adaptation. Chọn theo nhu cầu và hành vi thực tế trước khi tối ưu hiệu năng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-practice-010">Hoàn thiện ví dụ module hóa cùng bộ kiểm thử quyền truy cập</a>

<details>
<summary>Xem chi tiết</summary>

# Hoàn thiện ví dụ module hóa cùng bộ kiểm thử quyền truy cập trong Java Reflection

## 1. Mục tiêu thực hành

Trong Java 9+, Java Platform Module System (**JPMS**) không chỉ tổ chức dependency giữa các thư viện mà còn đặt **ranh giới truy cập package tại runtime**. Vì thế, một công cụ Reflection không thể giả định rằng nhìn thấy `Class<?>`, `Field` hoặc `Constructor` đồng nghĩa có thể gọi hoặc đọc mọi thành viên.

Bài thực hành xây dựng hai **named modules** chạy trên Java 17+:

- `lab.model`: module chứa dữ liệu/API, với ba package có chính sách khác nhau.
- `lab.probe`: module đóng vai trò người sử dụng và bộ kiểm thử Reflection; khai báo `requires lab.model`.

Bộ kiểm thử chạy thật, có assertion, phân biệt rõ bốn tình huống: public API trong package được `exports`; private field trong package chỉ `exports`; private field trong package `opens ... to ...`; và public constructor trong package không `exports`/`opens`.

**Kết quả cần đạt:** công cụ chỉ chấp nhận quyền mà module cấp rõ ràng, ghi nhận trường hợp bị từ chối thay vì âm thầm gọi `setAccessible(true)` hoặc thêm `--add-opens` vô điều kiện.

## 2. Ba quy tắc cơ bản của JPMS cần kiểm chứng

### 2.1. `requires`: quan hệ đọc module

Trong `module-info.java` của `lab.probe`, khai báo `requires lab.model;` giúp module `lab.probe` **đọc được** module `lab.model`. Đây là bước cần thiết cho đoạn mã sử dụng kiểu API public của module kia theo cách thông thường, nhưng **không tự mở toàn bộ package** cho truy cập sâu bằng Reflection.

### 2.2. `exports`: công khai kiểu và thành viên public

Trong `lab.model`:

~~~java
exports lab.model.api;
~~~

Mã của module đọc `lab.model` có thể biên dịch với public type `lab.model.api.PublicOrder` và gọi phương thức `public number()`, nếu các quy tắc truy cập Java khác cũng đáp ứng. `getMethod("number").invoke(order)` hợp lệ trong demo.

Tuy nhiên **`exports` không có nghĩa `private` được phép đọc qua deep reflection**. Với field `private secret`, `getDeclaredField("secret")` tìm được metadata nhưng `trySetAccessible()` từ module khác trả `false`, và `Field.get` không có quyền sẽ ném `IllegalAccessException`.

### 2.3. `opens`: cho phép deep reflection, có thể giới hạn theo module đích

Trong `lab.model`:

~~~java
opens lab.model.view to lab.probe;
~~~

Câu lệnh này **mở package `lab.model.view` cho Reflection từ `lab.probe`**, kể cả thành viên `private`; nó **không phải khai báo `exports`** cho mã Java thông thường có thể `import lab.model.view.AuditView`.

Trong demo, `lab.probe` dùng `Class.forName("lab.model.view.AuditView")` để lấy `Class<?>` mà không viết `import`. Sau đó `Constructor.trySetAccessible()` và `Field.trySetAccessible()` thành công vì package được mở đúng module đích.

Package `lab.model.closed` không được `exports` cũng không `opens`. Việc tìm metadata constructor vẫn có thể thực hiện, nhưng truy cập constructor từ module khác bị chặn.

| Cấu hình package của `lab.model` | Dùng public type qua `import` trong `lab.probe` | Deep reflection tới private member từ `lab.probe` |
| --- | --- | --- |
| `exports lab.model.api;` | Có | Không chỉ vì `exports` |
| `opens lab.model.view to lab.probe;` | Không, vì thiếu `exports` | Có, vì mở cho module `lab.probe` |
| Không khai báo `exports` / `opens` cho `lab.model.closed` | Không | Không |

**Sự tinh tế khi kiểm tra:** `Module.isExported(packageName, targetModule)` phản ánh truy cập ở runtime và có thể trả `true` khi package được mở cho target module. Do đó **không dùng duy nhất** `isExported(packageName, target)` để chứng minh `module-info.java` có khai báo `exports`. Nếu cần kiểm tra **khai báo exports chính thức trong descriptor**, duyệt `module.getDescriptor().exports()`, như bộ kiểm thử bên dưới. Dùng `Module.isOpen(packageName, target)` để kiểm tra việc mở package cho Reflection.

## 3. Cấu trúc project có thể biên dịch trực tiếp

Tạo thư mục chứa đúng các file sau. Tên module **phải khớp** tên thư mục ngay dưới `src` để lệnh `--module-source-path src` hoạt động:

~~~text
reflection-access-lab/
├── src/
│   ├── lab.model/
│   │   ├── module-info.java
│   │   └── lab/model/
│   │       ├── api/PublicOrder.java
│   │       ├── view/AuditView.java
│   │       └── closed/ClosedService.java
│   └── lab.probe/
│       ├── module-info.java
│       └── lab/probe/AccessTest.java
└── out/                           # javac sinh ra, không tạo thủ công
~~~

Ví dụ chỉ dùng JDK, không cần Gradle, Maven, JUnit, Spring hay framework. Bộ kiểm thử sử dụng các phương thức `check(...)` tự ném `AssertionError` để có thể chạy mà không phụ thuộc thư viện kiểm thử.

## 4. Module cung cấp dữ liệu: `lab.model`

### File 1 — `src/lab.model/module-info.java`

~~~java
module lab.model {
    exports lab.model.api;
    opens lab.model.view to lab.probe;
}
~~~

Chính sách module:

1. `lab.model.api`: exported, không opened.
2. `lab.model.view`: **chỉ opened tới `lab.probe`**, không khai báo exports.
3. `lab.model.closed`: không export, không open.

Khai báo `opens ... to lab.probe` cho phép **đúng một module đích** được truy cập sâu. Nếu một module `lab.other` cùng thử đọc private field, kết quả vẫn có thể bị từ chối. Đừng thay bằng `open module lab.model` nếu bài toán chỉ cần mở một package cho một công cụ.

### File 2 — `src/lab.model/lab/model/api/PublicOrder.java`

~~~java
package lab.model.api;

public final class PublicOrder {
    private final String secret = "api-private";

    public String number() {
        return "ORD-7";
    }
}
~~~

`PublicOrder` là `public`, và package chứa nó được `exports`. Vì vậy `lab.probe` có thể `import lab.model.api.PublicOrder` và gọi `number()`. Nhưng field `secret` vẫn `private`; chỉ `exports` **chưa đủ** để module khác đọc giá trị field qua Reflection.

### File 3 — `src/lab.model/lab/model/view/AuditView.java`

~~~java
package lab.model.view;

public final class AuditView {
    private final String auditCode = "AUD-42";

    public AuditView() {}
}
~~~

`AuditView` và constructor đều `public` nhưng package không khai báo `exports`; do đó mã nguồn `lab.probe` không thể import kiểu này theo cách Java thông thường. Bài kiểm thử lấy `Class` theo tên, bật truy cập Reflection có kiểm soát và đọc field `auditCode`. Quyền đó phát sinh từ `opens lab.model.view to lab.probe`.

### File 4 — `src/lab.model/lab/model/closed/ClosedService.java`

~~~java
package lab.model.closed;

public final class ClosedService {
    public ClosedService() {}
}
~~~

`ClosedService` cũng là kiểu `public`, constructor `public`; tuy nhiên module không export hoặc open package này. Điều kiện `public` của class/constructor **không vượt qua được ranh giới module**. Demo vẫn có thể lấy `Class<?>` và metadata constructor theo tên, nhưng không thể thực thi constructor từ module kiểm thử bằng quyền truy cập mặc định.

## 5. Module kiểm thử: `lab.probe`

### File 5 — `src/lab.probe/module-info.java`

~~~java
module lab.probe {
    requires lab.model;
}
~~~

Không khai báo `opens` ở phía `lab.probe` để “xin” quyền mở package từ `lab.model`: **module sở hữu package mới quyết định exports/opens của package đó**. `requires` và `opens` phục vụ hai vấn đề khác nhau.

### File 6 — `src/lab.probe/lab/probe/AccessTest.java`

~~~java
package lab.probe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import lab.model.api.PublicOrder;

public final class AccessTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Module model = PublicOrder.class.getModule();
        Module probe = AccessTest.class.getModule();

        check(model.isNamed() && probe.isNamed(), "named modules required");
        check(model.isExported("lab.model.api", probe), "API must be exported");
        check(!model.isOpen("lab.model.api", probe), "API must not be open");
        check(model.isOpen("lab.model.view", probe), "view must open to probe");
        check(!model.getDescriptor().exports().stream()
                .anyMatch(e -> e.source().equals("lab.model.view")),
                "view has exports declaration");
        check(!model.isExported("lab.model.closed", probe), "closed exported");
        check(!model.isOpen("lab.model.closed", probe), "closed open");
        System.out.println("module rules: PASS");

        PublicOrder order = new PublicOrder();
        check(order.number().equals("ORD-7"), "public API mismatch");
        Object number = PublicOrder.class.getMethod("number").invoke(order);
        check(number.equals("ORD-7"), "public method invoke failed");
        System.out.println("exported public API: PASS");

        Field secret = PublicOrder.class.getDeclaredField("secret");
        check(!secret.trySetAccessible(), "private API must remain closed");
        boolean denied = false;
        try { secret.get(order); }
        catch (IllegalAccessException expected) { denied = true; }
        check(denied, "private API read should fail");
        System.out.println("exported private field: DENIED");

        Class<?> view = Class.forName("lab.model.view.AuditView");
        Constructor<?> ctor = view.getDeclaredConstructor();
        check(ctor.trySetAccessible(), "open constructor expected");
        Object audit = ctor.newInstance();
        Field auditCode = view.getDeclaredField("auditCode");
        check(auditCode.trySetAccessible(), "open private field expected");
        check(auditCode.get(audit).equals("AUD-42"), "audit mismatch");
        System.out.println("qualified opens + private field: PASS");

        Class<?> closed = Class.forName("lab.model.closed.ClosedService");
        Constructor<?> cctor = closed.getDeclaredConstructor();
        check(!cctor.trySetAccessible(), "closed constructor exposed");
        boolean blocked = false;
        try { cctor.newInstance(); }
        catch (IllegalAccessException expected) { blocked = true; }
        check(blocked, "closed constructor must be blocked");
        System.out.println("non-exported non-open constructor: DENIED");
        System.out.println("ALL ACCESS TESTS PASSED");
    }
}
~~~

**Vì sao đoạn mã này là bộ kiểm thử chứ không chỉ trình diễn?**

- Mỗi hành vi quan trọng có `check(...)`; nếu sai, chương trình ném `AssertionError` và không in dòng thành công cuối.
- Kỳ vọng bị từ chối kiểm **cả kết quả của `trySetAccessible()` lẫn exception khi chưa có quyền**.
- Dữ liệu kiểm thử cố định `ORD-7`, `AUD-42` để xác minh method/field đọc đúng.
- Kiểm tra tên module và descriptor để giảm khả năng chạy nhầm trên classpath hoặc bị thay đổi `module-info.java` mà vẫn báo pass.
- Tên package public/opened/closed tách biệt; không phụ thuộc lỗi từ một class không tìm thấy.

## 6. Biên dịch và chạy đúng ở module path

Đứng trong thư mục gốc `reflection-access-lab` (cùng cấp với thư mục `src`). Chạy:

~~~bash
javac --release 17 -d out --module-source-path src -m lab.model,lab.probe
java --module-path out --module lab.probe/lab.probe.AccessTest
~~~

`javac --release 17` giới hạn ngôn ngữ và API công khai theo Java 17, `-d out` lưu output theo module, `--module-source-path src` cho javac biết hai module nguồn và `-m lab.model,lab.probe` chọn các module biên dịch.

Lệnh `java --module-path out --module lab.probe/lab.probe.AccessTest` chạy **module `lab.probe`** và main class `lab.probe.AccessTest`. Không thay `--module-path` bằng `--class-path`: nếu mọi class chạy trên classpath/unnamed module, ranh giới module cần kiểm thử không còn như mong đợi.

**Output kiểm chứng:**

~~~text
module rules: PASS
exported public API: PASS
exported private field: DENIED
qualified opens + private field: PASS
non-exported non-open constructor: DENIED
ALL ACCESS TESTS PASSED
~~~

Chương trình trên đã được biên dịch thành công với `javac --release 17` và chạy trên JDK 24, chỉ sử dụng API được Java 17 hỗ trợ. Output là chính xác khi cấu trúc file và `module-info.java` giữ như ví dụ.

### Ý nghĩa từng kết quả

| Dòng output | Quan sát | Kết luận |
| --- | --- | --- |
| `module rules: PASS` | Hai module đều named; `api` exported; `view` opened cho probe nhưng không có exports declaration; `closed` không mở. | JPMS được cấu hình đúng. |
| `exported public API: PASS` | Cả gọi Java trực tiếp lẫn `Method.invoke` lên public method đều thành công. | `requires` + `exports` phù hợp public API. |
| `exported private field: DENIED` | `getDeclaredField` thấy metadata, `trySetAccessible` trả `false`, `Field.get` ném `IllegalAccessException`. | `exports` không cho phép deep reflection vào private member. |
| `qualified opens + private field: PASS` | `Class.forName` tải kiểu, constructor/field `trySetAccessible` thành công. | `opens ... to lab.probe` cấp quyền Reflection cho đúng target module. |
| `non-exported non-open constructor: DENIED` | Có metadata constructor, nhưng không thể bật accessible và `newInstance` bị chặn. | `public` không bỏ qua package-level module encapsulation. |

## 7. Phân biệt tìm metadata, bật quyền và thực thi

Với một class không được export/open, các giai đoạn là:

1. **Tìm class:** `Class.forName("lab.model.closed.ClosedService")` có thể trả `Class<?>` khi module đã được giải quyết và class loader biết class. **Class loading ≠ Java compile-time access**.
2. **Tìm thành viên:** `getDeclaredConstructor()` hoặc `getDeclaredField(...)` thu metadata về khai báo; API tìm không tự chạy constructor hoặc lấy giá trị field.
3. **Kiểm tra quyền:** `trySetAccessible()` hỏi liệu có thể bỏ kiểm tra truy cập với Reflection hay không. Với package không open/export phù hợp, có thể trả `false`.
4. **Thực thi:** `Constructor.newInstance`, `Method.invoke` hay `Field.get` là những bước thật sự chạy constructor/method hoặc đọc state. Lúc này có thể gặp `IllegalAccessException` hoặc `InvocationTargetException` nếu target code ném lỗi sau khi đã gọi được.

`setAccessible(true)` có thể ném `InaccessibleObjectException` khi không thể bật quyền; `trySetAccessible()` thường thuận tiện hơn cho probe vì trả boolean. **Bật accessible không tự tạo quyền truy cập từ module khác nếu JPMS không cho phép.**

Khi debugging, ghi rõ **giai đoạn thất bại**: không tìm được class, không tìm được member, không bật quyền, hoặc đã gọi vào target code nhưng target ném lỗi. Các lỗi này cần cách sửa khác nhau.

## 8. Một lỗi kiểm thử quan trọng: `isExported(package, target)` không chứng minh có câu lệnh `exports`

Với `opens lab.model.view to lab.probe;`, JDK cho phép deep reflection từ `lab.probe`. Ở runtime, kiểm tra như `Module.isExported("lab.model.view", probe)` **có thể cho kết quả `true` vì package đã được mở cho module đó**, mặc dù descriptor **không** có khai báo `exports lab.model.view`.

Do đó test phủ định sau là **sai mục tiêu** nếu ta muốn chứng minh không có câu lệnh exports:

~~~java
// Sai khi dùng để khẳng định không có khai báo "exports":
check(!model.isExported("lab.model.view", probe), "not exported");
~~~

Muốn kiểm tra đặc tả module, hãy đọc `ModuleDescriptor`:

~~~java
// Mảnh nằm trong main của AccessTest; biến model đã được khai báo.
boolean explicitlyExported = model.getDescriptor().exports().stream()
        .anyMatch(e -> e.source().equals("lab.model.view"));
check(!explicitlyExported, "view should not declare exports");
check(model.isOpen("lab.model.view", probe), "view must open to probe");
~~~

Đoạn trên kiểm tra **hai sự thật khác nhau**: `lab.model.view` không được khai báo `exports` và được `opens` cho module kiểm thử. Đừng dùng một giá trị boolean runtime để suy ngược nguyên văn `module-info.java`.

Mặt khác, nếu cần phân biệt **exports có điều kiện** với exports cho tất cả module, hãy xem `ModuleDescriptor.Exports.isQualified()` và `targets()` hoặc dùng các overload phù hợp của `Module.isExported` theo yêu cầu bài test. Chỉ nhìn một method không đủ để lập lại toàn bộ module descriptor.

## 9. Bốn biến thể bài kiểm thử nên thực hiện

Các thay đổi dưới đây là **thí nghiệm riêng biệt**: chỉnh `module-info.java` hoặc file được chỉ định, biên dịch lại từ đầu rồi dự đoán kết quả. Không ghép tất cả thay đổi vào một lần kiểm thử.

### Biến thể A: Gỡ `opens lab.model.view to lab.probe`

Giữ `AuditView` như cũ nhưng xóa dòng `opens`. Khi chạy, `model.isOpen("lab.model.view", probe)` sẽ sai và test báo fail. Nếu bỏ check về module rule để thử tiếp, `trySetAccessible()` trên constructor/field bên `lab.model.view` không còn được phép. **Đây là minh chứng rằng quyền deep reflection đến từ module owner**.

### Biến thể B: Đổi `exports lab.model.api` thành `opens lab.model.api to lab.probe`

`lab.probe.AccessTest` đang có `import lab.model.api.PublicOrder`. Nếu package chỉ `opens` mà không `exports`, quá trình **biên dịch** module probe không còn hợp lệ vì public API không được export tới module đọc. Deep reflection có thể được cho phép ở runtime nhưng không thay thế compile-time public API. Đừng coi lỗi biên dịch và lỗi Reflection là một.

### Biến thể C: Đổi thành `exports lab.model.api; opens lab.model.api to lab.probe;`

Khi package vừa được export vừa open cho probe, private field `secret` có thể được bật accessible từ `lab.probe`, do đó phép kiểm `!secret.trySetAccessible()` dự kiến thất bại. Điều này minh họa cách `opens` thay đổi chính sách runtime mà không làm method `number()` khác đi.

### Biến thể D: Thêm `exports lab.model.closed;`

Khi đó probe có thể truy cập **public class và public constructor** trong package này bằng Java/Reflection nếu module đọc và các điều kiện khác đáp ứng. Tuy nhiên private member trong package chỉ export vẫn không tự động được mở cho deep reflection. Cần cập nhật assertions từ DENIED sang PASS đúng với chính sách mới.

## 10. Khả năng áp dụng vào thư viện Reflection thực tế

Công cụ của production có thể vận hành với nhiều module owner, nhiều lớp và chính sách khác nhau. Một pipeline an toàn:

1. **Định nghĩa contract:** module nào được đọc, package nào public API, package nào cho Reflection framework cụ thể.
2. **Ưu tiên public API:** có thể dùng getter, constructor/factory public hoặc DTO thay vì deep reflection khi không cần.
3. **Quét metadata có giới hạn:** `getDeclaredFields`/`getDeclaredMethods` có thể tìm private member; không tự suy ra quyền gọi.
4. **Kiểm tra access có kiểm soát:** `trySetAccessible` hoặc các điều kiện module theo `Module.isOpen`; không tự động bật quyền cho mọi member của mọi module.
5. **Xử lý lỗi theo chính sách:** trường bắt buộc không truy cập được thì trả lỗi rõ; trường tùy chọn có thể bị bỏ có ghi chú; không tự lén trả `null` khiến dữ liệu sai.
6. **Tạo test module độc lập:** test code chạy ở **module khác** với module được kiểm để thấy đúng boundary. Test đặt cùng module hoặc chạy classpath có thể che giấu vấn đề.
7. **Dùng quyền tối thiểu:** `opens some.package to specific.framework.module;` thay cho `open module` hoặc `--add-opens` rộng khi có thể.

Không sử dụng `--add-opens` như cách sửa mặc định chỉ để làm test xanh. Cờ runtime này có thể là công cụ chuyển tiếp/môi trường kiểm thử chủ động, nhưng nếu dùng nó mà quên ghi rõ thì test không còn xác nhận module descriptor thật sự đang bảo vệ package.

### Các exception nên phân biệt

| Exception | Giai đoạn thường gặp | Hướng kiểm tra |
| --- | --- | --- |
| `ClassNotFoundException` | Tìm tên class | Module có được resolve? Tên package/class có đúng? Loader có nhìn thấy? |
| `NoSuchMethodException` / `NoSuchFieldException` | Truy vấn metadata | Chữ ký tên/kiểu tham số đúng? `getMethod` hay `getDeclaredMethod`? |
| `IllegalAccessException` | `invoke`, `get`, `newInstance` | Java access rules, `exports`/`opens`, access flag trên member. |
| `InaccessibleObjectException` | `setAccessible(true)` | Package không open tới module của caller hoặc member bị hạn chế bởi quy tắc truy cập. |
| `InvocationTargetException` | Target method/constructor được gọi và ném exception | Đọc `getCause()` để phân biệt lỗi từ target code với lỗi Reflection. |
| `IllegalArgumentException` | Sai receiver, tham số hoặc kiểu argument | Kiểm chữ ký và giá trị truyền vào, không gộp thành lỗi module. |

## 11. Sai lầm thường gặp

- **“Có `requires` là có thể truy cập private member.”** `requires` tạo quan hệ đọc module, không mở mọi package.
- **“`public class` luôn gọi được từ module khác.”** Kiểu public trong package không `exports` có thể bị chặn ở biên dịch và Reflection.
- **“`exports` cũng cho phép `setAccessible(true)` trên private.”** Deep reflection từ module khác thường cần package được `opens` phù hợp.
- **“`opens` nghĩa là mã khác có thể `import` public class.”** `opens` chủ yếu cho truy cập Reflection ở runtime; source-level API vẫn cần `exports`.
- **“Tìm thấy `getDeclaredField` nghĩa là đọc được field.”** Metadata lookup khác với `Field.get`.
- **“Nếu `isExported(package, target)` là true thì descriptor nhất định có `exports`.”** Runtime API và khai báo module không luôn suy ngược được như vậy; đọc `ModuleDescriptor`.
- **“Chạy test trên classpath cũng kiểm được JPMS.”** Classpath/unnamed module có ngữ nghĩa khác; phải đặt các module trên module path.
- **“Thêm `--add-opens` là sửa được code.”** Flag có thể che khuất lỗi quyền trong module descriptor và làm kết quả test không đại diện cho runtime thật.

## 12. Bài tập tự kiểm tra

1. Trong cùng module `lab.model`, gọi `Field.trySetAccessible` tới `PublicOrder.secret` từ code cùng module. So với kết quả trong `lab.probe`, sự khác biệt phát sinh từ điều kiện nào?
2. Gỡ `opens lab.model.view to lab.probe`. Hãy xác định assertion đầu tiên thất bại, sau đó bỏ assertion đó để kiểm tra trực tiếp constructor/field.
3. Thử thêm `exports lab.model.view to lab.probe;` thay cho `opens`. Public constructor gọi được không? Private `auditCode` được mở không? Giải thích từng kết quả.
4. Thêm module thứ ba `lab.other` cùng `requires lab.model`. Nó có đọc private `auditCode` qua Reflection không khi directive vẫn là `opens ... to lab.probe`?
5. Thay `trySetAccessible()` ở package đóng bằng `setAccessible(true)`. Bắt riêng `InaccessibleObjectException`, so sánh exception này với `IllegalAccessException` của `newInstance`.
6. Đo `model.isExported("lab.model.view", probe)` và kiểm tra `model.getDescriptor().exports()`; giải thích vì sao hai quan sát có thể khác kỳ vọng nếu chỉ đọc `module-info.java`.
7. Tạo thêm một package nội bộ chỉ cho serializer `opens ... to specific.serializer.module`. Thiết kế test chứng minh module serializer có quyền mà module ngoài danh sách không có.

**Tiêu chí đạt bài:** hai module thực sự chạy trên module path; module descriptor thể hiện quyền rõ ràng; các trường hợp được phép và bị từ chối đều có assertion; test không phụ thuộc exact exception message; không dựa vào `setAccessible` như quyền vô điều kiện; output cuối chỉ xuất hiện nếu toàn bộ kiểm thử pass.

## 13. Tài liệu chuẩn để đối chiếu

- [Java SE 17 — Module](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html): `isExported`, `isOpen`, `getDescriptor`.
- [Java SE 17 — ModuleDescriptor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/ModuleDescriptor.html): `exports()`, `opens()` và qualified directives.
- [Java SE 17 — AccessibleObject](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html): `trySetAccessible`, `setAccessible` và các điều kiện module.
- [Java SE 17 — Field](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html): `getDeclaredField`, `Field.get`, quyền truy cập.
- [Java SE 17 — Constructor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html): `newInstance` và exception.
- [Java Language Specification, Chương 7 — Packages and Modules](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html): cú pháp và ý nghĩa `requires`, `exports`, `opens`.
- [JEP 261 — Module System](https://openjdk.org/jeps/261): cơ chế JPMS và lựa chọn command-line ở runtime.

</details>

- [Quay lại đầu trang](#back-to-top)
