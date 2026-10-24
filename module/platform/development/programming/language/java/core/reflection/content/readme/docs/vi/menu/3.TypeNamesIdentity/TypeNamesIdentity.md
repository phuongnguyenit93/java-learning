<a id="back-to-top"></a>

# Tên và định danh kiểu

## Menu
- [getName, getSimpleName, getCanonicalName và getTypeName](#type-names-identity-001)
- [Tên nhị phân của lớp thành viên, lớp cục bộ và lớp vô danh](#type-names-identity-002)
- [Tên mảng, component type và descriptorString](#type-names-identity-003)
- [Trường hợp tên chuẩn không tồn tại](#type-names-identity-004)
- [getClassLoader và ý nghĩa kết quả null](#type-names-identity-005)
- [Tên lớp giống nhau nhưng khác defining loader](#type-names-identity-006)
- [getPackage, getPackageName và getModule](#type-names-identity-007)
- [Nhận diện isPrimitive, isArray, isInterface và isAnnotation](#type-names-identity-008)
- [Class.isInstance khác instanceof với kiểu biết trước](#type-names-identity-009)
- [isAssignableFrom, cast và asSubclass](#type-names-identity-010)

## <a id="type-names-identity-001">getName, getSimpleName, getCanonicalName và getTypeName</a>

<details>
<summary>Xem chi tiết</summary>

# Tên và định danh kiểu trong Java Reflection: `getName()`, `getSimpleName()`, `getCanonicalName()` và `getTypeName()`

Khi dùng Reflection, một đối tượng `Class<?>` cho biết kiểu ở thời điểm chạy. Java cung cấp nhiều cách lấy tên của kiểu vì **tên JVM dùng để định danh**, **tên ngắn dùng để hiển thị**, **tên chuẩn trong mã nguồn** và **biểu diễn tên của `Type`** phục vụ các mục đích khác nhau. Chúng thường trùng nhau với lớp thông thường nhưng khác rõ ở lớp lồng nhau, mảng, lớp cục bộ, lớp vô danh và kiểu generic.

## 1. Phạm vi và mô hình tên

Bài học tập trung vào bốn phương thức:

- `Class#getName()`: lấy *binary name* của lớp/interface thông thường; với mảng trả về dạng tên JVM được mã hóa, với kiểu nguyên thủy trả về từ khóa kiểu.
- `Class#getSimpleName()`: lấy tên ngắn, không có tên gói hay tên lớp bao ngoài; có thể là chuỗi rỗng với lớp vô danh.
- `Class#getCanonicalName()`: lấy tên *canonical* theo cách biểu diễn trong mã nguồn Java; có thể trả về `null`.
- `Class#getTypeName()`: lấy tên biểu diễn kiểu dễ đọc hơn cho mảng; với lớp thông thường nhìn chung giống `getName()`. Cùng tên phương thức này cũng có trên giao diện `java.lang.reflect.Type` để biểu diễn các kiểu generic.

**Phân biệt ngay từ đầu:** `Class` biểu diễn kiểu runtime đã chịu ảnh hưởng của *type erasure*. Trong khi đó, `Type` có thể là `Class`, `ParameterizedType`, `TypeVariable`, `WildcardType` hoặc `GenericArrayType`; kết quả `Type#getTypeName()` phụ thuộc vào dạng `Type` thực tế. Bởi vậy gọi `getTypeName()` trên `Class<?>` không tự làm xuất hiện tham số kiểu như `<String>`.

## 2. Bảng so sánh bốn phương thức

| Phương thức | Ý nghĩa chính | Kiểu mảng | Lớp thành viên (member class) | Khả năng `null`/rỗng |
| --- | --- | --- | --- | --- |
| `getName()` | Tên dùng ở mức runtime/JVM | Mã hóa như `[I`, `[Ljava.lang.String;` | Dùng dấu `$` giữa lớp bao ngoài và lớp thành viên | Không trả về `null` |
| `getSimpleName()` | Tên ngắn để trình bày | `int[]`, `String[][]` | Chỉ còn `Entry`, `Nested`... | `""` đối với lớp vô danh |
| `getCanonicalName()` | Tên chuẩn kiểu viết trong mã nguồn | `int[]`, `java.lang.String[][]` | Dùng dấu chấm: `java.util.Map.Entry` | `null` nếu không có canonical name, điển hình ở lớp local/anonymous |
| `getTypeName()` trên `Class` | Tên biểu diễn kiểu | `int[]`, `java.lang.String[][]` | Dùng tên binary có `$` | Không tự cung cấp tham số generic |

Ví dụ cùng một kiểu `java.util.Map.Entry`:

- `Map.Entry.class.getName()` → `"java.util.Map$Entry"`.
- `Map.Entry.class.getSimpleName()` → `"Entry"`.
- `Map.Entry.class.getCanonicalName()` → `"java.util.Map.Entry"`.
- `Map.Entry.class.getTypeName()` → `"java.util.Map$Entry"`.

Ký tự `$` nằm trong binary name của kiểu thành viên. Không nên tự động đổi tất cả `$` thành dấu chấm, vì `$` cũng có thể xuất hiện trong tên lớp được khai báo hợp lệ và trong tên các lớp do trình biên dịch sinh.

## 3. Quy tắc theo từng loại kiểu

### 3.1. Lớp thông thường, interface, primitive và `void`

Với `String.class`, ba cách lấy tên có chứa package đều cho `java.lang.String`, còn `getSimpleName()` là `String`. Với primitive `int.class`, cả bốn phương thức đều cho `int`; tương tự `boolean.class` cho `boolean`. `void.class` là đối tượng `Class` hợp lệ để biểu diễn kiểu `void`; không có kiểu mảng `void[]` trong Java.

`getSimpleName()` không đủ làm định danh duy nhất: `java.util.Date` và `java.sql.Date` đều có tên ngắn `Date`.

### 3.2. Lớp thành viên và lớp lồng nhau

Với `java.util.Map.Entry` (interface thành viên của `Map`), tên binary là `java.util.Map$Entry`, canonical name là `java.util.Map.Entry`, còn simple name là `Entry`. Quy tắc này cũng áp dụng cho lớp `static nested` và lớp thành viên không `static`. Việc đổi dấu chấm thành `$` chỉ có ý nghĩa ở phần tên kiểu lồng nhau; dấu chấm ngăn các package vẫn giữ nguyên.

### 3.3. Mảng và mã hóa tên mảng trong JVM

`Class#getName()` của lớp mảng theo quy tắc:

1. Mỗi chiều mảng có một ký tự `[`.
2. Thành phần primitive sử dụng mã kiểu JVM: `Z` = `boolean`, `B` = `byte`, `C` = `char`, `S` = `short`, `I` = `int`, `J` = `long`, `F` = `float`, `D` = `double`.
3. Thành phần kiểu tham chiếu dùng `L` + binary name + `;`. Trong tên do `Class#getName()` trả về, các dấu chấm package nằm trong binary name, ví dụ `[Ljava.lang.String;`.

| Biểu thức | `getName()` | `getSimpleName()` | `getCanonicalName()` | `getTypeName()` |
| --- | --- | --- | --- | --- |
| `int[].class` | `[I` | `int[]` | `int[]` | `int[]` |
| `String[][].class` | `[[Ljava.lang.String;` | `String[][]` | `java.lang.String[][]` | `java.lang.String[][]` |
| `Map.Entry[].class` | `[Ljava.util.Map$Entry;` | `Entry[]` | `java.util.Map.Entry[]` | `java.util.Map$Entry[]` |

Đừng nhầm chuỗi do `Class#getName()` trả về với cú pháp descriptor trong tệp class, nơi tên nội bộ kiểu tham chiếu thường dùng dấu gạch chéo `/` thay cho dấu chấm `.`. Nếu đang viết tài liệu hoặc giao diện cho người đọc, `getTypeName()` hoặc `getCanonicalName()` thường dễ hiểu hơn `getName()` ở trường hợp mảng.

### 3.4. Lớp cục bộ (local class), lớp vô danh (anonymous class)

Một local class khai báo trong thân phương thức có simple name, nhưng thường **không có canonical name**: `getCanonicalName() == null`. Lớp vô danh có `getSimpleName().isEmpty() == true` và cũng không có canonical name. Binary name của chúng do trình biên dịch sinh, thường chứa các thành phần như `$1Local` hoặc `$1`; số và cấu trúc chính xác không nên được dùng làm hợp đồng nghiệp vụ.

Mảng có thành phần là kiểu không có canonical name cũng không có canonical name. Ví dụ một mảng của local class không đột nhiên trở thành kiểu có canonical name.

## 4. Ví dụ Java 17+: so sánh trực tiếp

Tạo tệp **`ClassNameDemo.java`**:

~~~java
import java.util.Map;

public class ClassNameDemo {
    private static void show(String label, Class<?> type) {
        System.out.println(label);
        System.out.println("  getName          = " + type.getName());
        System.out.println("  getSimpleName    = " + type.getSimpleName());
        System.out.println("  getCanonicalName = " + type.getCanonicalName());
        System.out.println("  getTypeName      = " + type.getTypeName());
    }

    public static void main(String[] args) {
        show("String", String.class);
        show("int", int.class);
        show("int[]", int[].class);
        show("String[][]", String[][].class);
        show("Map.Entry", Map.Entry.class);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac ClassNameDemo.java
java ClassNameDemo
~~~

**Kết quả stdout:**

~~~text
String
  getName          = java.lang.String
  getSimpleName    = String
  getCanonicalName = java.lang.String
  getTypeName      = java.lang.String
int
  getName          = int
  getSimpleName    = int
  getCanonicalName = int
  getTypeName      = int
int[]
  getName          = [I
  getSimpleName    = int[]
  getCanonicalName = int[]
  getTypeName      = int[]
String[][]
  getName          = [[Ljava.lang.String;
  getSimpleName    = String[][]
  getCanonicalName = java.lang.String[][]
  getTypeName      = java.lang.String[][]
Map.Entry
  getName          = java.util.Map$Entry
  getSimpleName    = Entry
  getCanonicalName = java.util.Map.Entry
  getTypeName      = java.util.Map$Entry
~~~

**Giải thích:** `String` là lớp cấp cao nhất nên `getName()`, `getCanonicalName()` và `getTypeName()` trùng nhau. `int` cũng không tạo khác biệt. Với `int[]`, chỉ `getName()` trả về mã `[I`. Với `String[][]`, tên JVM bắt đầu bằng hai dấu `[`. Với `Map.Entry`, sự khác nhau nằm ở ký tự `$` và dấu chấm giữa hai tên kiểu.

## 5. Ví dụ local class và anonymous class không phụ thuộc số do compiler sinh

Tạo tệp **`LocalAnonymousDemo.java`**:

~~~java
public class LocalAnonymousDemo {
    private static void show(String label, Class<?> type) {
        System.out.println(label
                + ": local=" + type.isLocalClass()
                + ", anonymous=" + type.isAnonymousClass()
                + ", canonicalPresent=" + (type.getCanonicalName() != null)
                + ", simpleNameEmpty=" + type.getSimpleName().isEmpty());
    }

    public static void main(String[] args) {
        class Local {}

        Runnable anonymous = new Runnable() {
            @Override
            public void run() {
                // Ví dụ chỉ kiểm tra metadata của lớp vô danh.
            }
        };

        show("Local", Local.class);
        show("Anonymous", anonymous.getClass());
    }
}
~~~

~~~bash
javac LocalAnonymousDemo.java
java LocalAnonymousDemo
~~~

**Kết quả stdout:**

~~~text
Local: local=true, anonymous=false, canonicalPresent=false, simpleNameEmpty=false
Anonymous: local=false, anonymous=true, canonicalPresent=false, simpleNameEmpty=true
~~~

Nếu in thêm `getName()` hoặc `getTypeName()` của hai lớp này, kết quả chứa tên do compiler đặt; tên ấy có thể thay đổi khi chỉnh cấu trúc mã hoặc đổi compiler. `getSimpleName()` của local class là `Local`, còn của anonymous class là chuỗi rỗng. Đây là lý do phải kiểm tra `null` trước khi dùng `getCanonicalName()` và không lấy tên compiler sinh làm khóa dữ liệu ổn định.

## 6. `Type#getTypeName()` và generic: điểm dễ nhầm nhất

`Class<?>` cho biết kiểu runtime. Do Java xóa phần lớn thông tin tham số kiểu của đối tượng ở runtime (*type erasure*), `List.class.getTypeName()` chỉ cho `java.util.List`. Tuy nhiên chữ ký generic của field, method hoặc parameter có thể được lưu trong metadata của lớp biên dịch. Reflection có API riêng để đọc chúng:

- `Field#getType()` trả về một `Class<?>`: kiểu thô ở runtime.
- `Field#getGenericType()` trả về một `Type`, có thể là `ParameterizedType`: giữ cấu trúc như `List<String>` nếu metadata khai báo chứa nó.
- `Method#getReturnType()` so với `Method#getGenericReturnType()` và `Parameter#getType()` so với `Parameter#getParameterizedType()` tuân theo cùng cách phân biệt kiểu thô với kiểu generic.

Tạo tệp **`GenericTypeNamesDemo.java`**:

~~~java
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

public class GenericTypeNamesDemo {
    private List<String> names;

    public static void main(String[] args) throws NoSuchFieldException {
        Field field = GenericTypeNamesDemo.class.getDeclaredField("names");

        Class<?> rawType = field.getType();
        Type genericType = field.getGenericType();

        System.out.println("Class#getTypeName = " + rawType.getTypeName());
        System.out.println("Type#getTypeName  = " + genericType.getTypeName());
        System.out.println("ParameterizedType = "
                + (genericType instanceof ParameterizedType));
    }
}
~~~

~~~bash
javac GenericTypeNamesDemo.java
java GenericTypeNamesDemo
~~~

**Kết quả stdout:**

~~~text
Class#getTypeName = java.util.List
Type#getTypeName  = java.util.List<java.lang.String>
ParameterizedType = true
~~~

`Field#getGenericType()` trả về `ParameterizedType` cho khai báo `List<String>`, còn `getType()` chỉ biết `List.class`. `getTypeName()` là phương thức chung của `Type`, nên kết quả khác nhau do **đối tượng nhận lời gọi khác nhau**, chứ không phải `Class#getTypeName()` tự phục hồi tham số kiểu đã bị xóa. Với `TypeVariable`, wildcard và generic array, biểu diễn tên tiếp tục phản ánh cấu trúc của `Type` tương ứng; không nên ép mọi `Type` về `Class`.

## 7. Dùng phương thức nào trong thực tế?

| Nhu cầu | Lựa chọn | Vì sao |
| --- | --- | --- |
| Ghi log hoặc truy vết tên lớp JVM, nhất là lớp lồng | `getName()` | Thể hiện binary name; mảng có mã JVM nên có thể khó đọc |
| Tiêu đề ngắn cho UI, bảng thống kê loại đối tượng | `getSimpleName()` | Gọn, nhưng trùng tên giữa các package và có thể rỗng |
| Hiển thị tên đầy đủ gần cú pháp Java source | `getCanonicalName()` sau khi kiểm tra `null` | Phân biệt package/lớp lồng bằng dấu chấm; có thể vắng ở local/anonymous |
| Hiển thị tên runtime của mảng dễ đọc | `Class#getTypeName()` | Cho `String[]` / `int[][]` thay vì `[Ljava.lang.String;` / `[[I` |
| Trình bày generic từ field/method/parameter | `Type#getTypeName()` từ API `getGeneric...` | Có thể giữ `List<String>`, wildcard, type variable theo chữ ký đã khai báo |
| So sánh xem hai `Class<?>` có cùng định danh runtime | So sánh chính các `Class` bằng `==` | Tên chuỗi không biểu diễn đầy đủ danh tính khi có nhiều class loader |

Đoạn xử lý tên đầy đủ an toàn với `null`:

~~~java
static String displayType(Class<?> type) {
    String canonical = type.getCanonicalName();
    return canonical != null ? canonical : type.getTypeName();
}
~~~

Đây là **hàm minh họa** để đặt trong một lớp Java, không phải chương trình chạy độc lập. Nó ưu tiên canonical name khi có, rồi dùng type name để không bị lỗi `NullPointerException`; với local/anonymous class, tên fallback có thể vẫn là tên compiler sinh.

### Liên hệ `Class.forName()`: tên binary không phải lúc nào cũng là khóa tải lớp

`Class.forName(String)` thường cần **binary name** đối với lớp thông thường và lớp lồng; ví dụ `Class.forName("java.util.Map$Entry")` hoạt động, còn `Class.forName("java.util.Map.Entry")` không định vị chính kiểu lồng ấy theo cách thông thường. Với lớp mảng, tên như `[I` hoặc `[Ljava.lang.String;` được chấp nhận. Trái lại `Class.forName("int")` không tải được primitive `int.class`. Ngoài ra, tải lớp còn phụ thuộc class loader; biểu diễn tên không thay thế được danh tính `Class<?>`.

Tạo tệp **`ForNameDemo.java`**:

~~~java
import java.util.Map;

public class ForNameDemo {
    public static void main(String[] args) throws ClassNotFoundException {
        System.out.println(Class.forName(Map.Entry.class.getName())
                == Map.Entry.class);
        System.out.println(Class.forName(int[].class.getName())
                == int[].class);
    }
}
~~~

~~~bash
javac ForNameDemo.java
java ForNameDemo
~~~

**Kết quả stdout:**

~~~text
true
true
~~~

Hai kết quả chứng minh binary name của lớp lồng và tên mã hóa của mảng đều dùng được trong hai lời gọi tương ứng. `Class.forName` có thể làm khởi tạo lớp và nên được cân nhắc kỹ khi Reflection xử lý tên do bên ngoài cung cấp.

## 8. Lỗi phổ biến và cách tự kiểm tra

1. **Dùng `getSimpleName()` làm định danh duy nhất.** Hai lớp ở hai package khác nhau vẫn có thể cùng simple name; giữ đối tượng `Class` hoặc dùng tên phù hợp với mục đích.
2. **Cho rằng `getCanonicalName()` luôn là `String` hợp lệ.** Local/anonymous class có thể trả `null`; hãy kiểm tra trước khi gọi các thao tác chuỗi.
3. **Cho rằng `getName()` của mảng là `String[]`.** Thực tế `String[].class.getName()` là `[Ljava.lang.String;`. Dùng `getTypeName()` nếu mục tiêu là trình bày dễ đọc.
4. **Cho rằng `getName()` và `getCanonicalName()` luôn giống nhau.** Với lớp thành viên: `$` khác dấu chấm; với local/anonymous có thể hoàn toàn không có canonical name.
5. **Cho rằng `Class#getTypeName()` giữ tham số generic.** Cần đọc `Field#getGenericType()`, `Method#getGenericReturnType()` hoặc API generic phù hợp trước khi gọi `Type#getTypeName()`.
6. **Tự phân tích tên bằng `split(".")` hay thay tất cả `$`.** Dấu chấm ở `String.split` là ký tự đặc biệt của regex; hơn nữa tên JVM của lớp lồng, mảng và lớp do compiler sinh có quy tắc riêng. Dùng API Reflection để lấy chính dạng tên cần thiết.
7. **Đồng nhất tên với bản sắc kiểu.** Hai lớp có cùng binary name nhưng được nạp bởi hai class loader khác nhau vẫn có thể là hai kiểu runtime khác nhau; kiểm tra `Class<?>` và class loader khi xây framework.

### Tự kiểm tra nhanh

- `byte[].class.getName()` trả `[B`, còn `byte[].class.getTypeName()` trả `byte[]`.
- `String.class.getSimpleName()` trả `String`, không kèm `java.lang`.
- `Map.Entry.class.getCanonicalName()` trả `java.util.Map.Entry`, còn `getName()` trả `java.util.Map$Entry`.
- Với local class, `getCanonicalName() == null`; với anonymous class, thêm điều kiện `getSimpleName().isEmpty()` là `true`.
- Nếu Reflection đọc field khai báo `List<String>`, `field.getType().getTypeName()` chỉ là `java.util.List`, nhưng `field.getGenericType().getTypeName()` có thể là `java.util.List<java.lang.String>`.

**Ghi nhớ:** hãy chọn phương thức theo đối tượng nhận (`Class` hay `Type`) và mục tiêu (định danh runtime, tên ngắn, tên canonical, hay trình bày generic). Chỉ cần thử với một lớp thường, một lớp lồng và một mảng là có thể thấy ba nhóm khác biệt quan trọng nhất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-names-identity-002">Tên nhị phân của lớp thành viên, lớp cục bộ và lớp vô danh</a>

<details>
<summary>Xem chi tiết</summary>

# Tên nhị phân của lớp thành viên, lớp cục bộ và lớp vô danh trong Java

Khi làm việc với Java Reflection, một kiểu có thể được gọi bằng nhiều tên khác nhau: tên viết trong mã nguồn, **tên nhị phân (binary name)** dùng để định danh lớp sau khi biên dịch, tên đơn giản và tên chuẩn tắc. Với lớp lồng nhau, đặc biệt lớp cục bộ và lớp vô danh, các tên này khác nhau rõ rệt. Hiểu đúng chúng giúp đọc kết quả của `Class.getName()`, nạp lớp bằng `Class.forName(...)` và phân tích thông tin về lớp chứa.

## 1. Phân biệt ba loại lớp

- **Lớp thành viên (member class):** khai báo trực tiếp trong thân một lớp hoặc interface và có tên riêng. Có lớp thành viên `static` và lớp thành viên không `static` (inner class). Ví dụ `Outer.Member`. Tính `static` ảnh hưởng cách tạo đối tượng và khả năng truy cập đối tượng bao ngoài, nhưng cả hai trường hợp đều theo quy tắc tên nhị phân của member class.
- **Lớp cục bộ (local class):** lớp có tên, khai báo trong một khối, chẳng hạn bên trong phương thức, constructor hoặc khối khởi tạo. Ví dụ `class Local {}` bên trong phương thức `run()`. Tên `Local` chỉ có phạm vi sử dụng theo quy tắc Java tại nơi khai báo.
- **Lớp vô danh (anonymous class):** lớp khai báo tại biểu thức tạo đối tượng, chẳng hạn `new Runnable() { public void run() {} }`. Không có tên đơn giản do lập trình viên đặt và không có tên chuẩn tắc.

Một lớp mức trên cùng (top-level class) như `example.Outer` không thuộc ba loại trên. Biểu thức lambda cũng **không đồng nghĩa** với anonymous class trong mã nguồn: không nên áp quy tắc tên nhị phân của anonymous class cho những lớp triển khai lambda do JVM tạo.

## 2. Quy tắc tên nhị phân

Giả sử lớp bao ngoài có tên nhị phân `example.Outer`:

| Kiểu khai báo | Cách gọi ở mức mã nguồn | Dạng tên nhị phân minh họa |
| --- | --- | --- |
| Lớp mức trên cùng | `example.Outer` | `example.Outer` |
| Lớp thành viên `Member` | `Outer.Member` | `example.Outer$Member` |
| Lớp cục bộ `Local` trong `Outer` | `Local` tại phạm vi cục bộ | `example.Outer$1Local` |
| Lớp vô danh bên trong `Outer` | Không có tên kiểu trực tiếp | `example.Outer$1` |

Với **member class**, tên nhị phân là tên nhị phân của lớp/interface khai báo trực tiếp, dấu `$`, rồi tên đơn giản của lớp thành viên. Ví dụ, nếu `Inner` khai báo trong `Outer.Member` thì có dạng `example.Outer$Member$Inner`.

Với **local class**, Java Language Specification quy định dạng tên nhị phân: tên nhị phân của lớp/interface bao quanh, dấu `$`, **một chuỗi chữ số không rỗng do trình biên dịch sinh ra**, rồi tên đơn giản của lớp cục bộ: `Outer$<digits>Local`.

Với **anonymous class**, cấu trúc tương tự nhưng **không có tên đơn giản sau các chữ số**: `Outer$<digits>`.

Các số `1`, `2`… trong bảng chỉ minh họa. **Không suy luận thứ tự khai báo, phương thức, số dòng hay danh tính lâu dài từ số hậu tố.** Cách đánh số cụ thể phụ thuộc trình biên dịch và cấu trúc mã nguồn; thêm hoặc chuyển vị trí một khai báo có thể đổi tên được sinh. Bản thân ký tự `$` hợp lệ trong Java identifier, nên không được tách chuỗi tại mọi dấu `$` để khẳng định quan hệ chứa.

### Tên nhị phân và tên chuẩn tắc không giống nhau

- `Class.getName()` trả về tên nhị phân đối với lớp/interface thông thường, chẳng hạn `example.Outer$Member`. Với mảng, phương thức dùng dạng mã hóa riêng, trình bày ở phần 5.
- `Class.getSimpleName()` trả về `Member` với member class, `Local` với local class, và chuỗi rỗng `""` với anonymous class.
- `Class.getCanonicalName()` trả về `example.Outer.Member` cho member class có tên chuẩn tắc; trả về `null` cho local class và anonymous class. Một member class được khai báo bên trong kiểu vốn không có tên chuẩn tắc cũng có thể không có tên chuẩn tắc.
- `Class.getTypeName()` cung cấp biểu diễn tên kiểu dễ đọc, đặc biệt với mảng; không nên mặc định dùng nó như binary name để nạp lớp.

Dấu chấm trong `Outer.Member` thể hiện cú pháp tham chiếu kiểu/canonical name; dấu `$` trong `Outer$Member` thuộc binary name. Không được thay mọi dấu chấm bằng `$` vì dấu chấm phân cách tên package phải được giữ nguyên.

## 3. Ví dụ Java 17+ chạy được

Lưu mã thành **`BinaryNamesDemo.java`**. Ví dụ cố ý không in trực tiếp tên nhị phân của local class và anonymous class để kết quả không phụ thuộc các số do trình biên dịch sinh ra.

```java
public class BinaryNamesDemo {
    static class StaticMember {}
    class InnerMember {}

    static void inMethod() {
        class Local {}

        Runnable anonymous = new Runnable() {
            @Override
            public void run() {}
        };

        describe("local", Local.class);
        describe("anonymous", anonymous.getClass());

        System.out.println("local enclosed by inMethod = "
                + Local.class.getEnclosingMethod().getName().equals("inMethod"));
        System.out.println("anonymous enclosed by inMethod = "
                + anonymous.getClass().getEnclosingMethod().getName().equals("inMethod"));
    }

    static void describe(String label, Class<?> type) {
        System.out.printf(
                "%s: simple='%s', canonical=%s, member=%s, local=%s, anonymous=%s%n",
                label, type.getSimpleName(), type.getCanonicalName(), type.isMemberClass(),
                type.isLocalClass(), type.isAnonymousClass());
    }

    public static void main(String[] args) throws ClassNotFoundException {
        describe("static-member", StaticMember.class);
        describe("inner-member", InnerMember.class);
        inMethod();

        System.out.println("binary member = " + StaticMember.class.getName());
        System.out.println("member loaded = "
                + (Class.forName("BinaryNamesDemo$StaticMember") == StaticMember.class));
    }
}
```

Biên dịch/chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 BinaryNamesDemo.java
java BinaryNamesDemo
```

Kết quả:

```text
static-member: simple='StaticMember', canonical=BinaryNamesDemo.StaticMember, member=true, local=false, anonymous=false
inner-member: simple='InnerMember', canonical=BinaryNamesDemo.InnerMember, member=true, local=false, anonymous=false
local: simple='Local', canonical=null, member=false, local=true, anonymous=false
anonymous: simple='', canonical=null, member=false, local=false, anonymous=true
local enclosed by inMethod = true
anonymous enclosed by inMethod = true
binary member = BinaryNamesDemo$StaticMember
member loaded = true
```

Giải thích:

1. `StaticMember` và `InnerMember` đều là **member class**, vì vậy `isMemberClass()` bằng `true`, bất kể khai báo `static` hay không.
2. `Local` có tên đơn giản là `Local` nhưng không có canonical name; `isLocalClass()` bằng `true`.
3. Đối tượng `anonymous` thuộc anonymous class; `getSimpleName()` trả chuỗi rỗng và `isAnonymousClass()` bằng `true`.
4. `getEnclosingMethod()` xác định vị trí khai báo bên trong phương thức `inMethod` mà không cần phân tích chuỗi tên.
5. `Class.forName("BinaryNamesDemo$StaticMember")` nhận **binary name** của member class; chuỗi `"BinaryNamesDemo.StaticMember"` không tìm được lớp tương ứng trong ví dụ.

Nếu in thêm `Local.class.getName()` hoặc `anonymous.getClass().getName()`, có thể thấy dạng `BinaryNamesDemo$1Local` và `BinaryNamesDemo$1`. Những số này là ví dụ, không phải stdout cố định cần ghi nhớ.

## 4. Nhận diện loại lớp bằng Reflection

Khi đã có `Class<?> type`, dùng các API thể hiện cấu trúc kiểu thay vì đoán từ tên:

| API | Member class | Local class | Anonymous class |
| --- | --- | --- | --- |
| `isMemberClass()` | `true` | `false` | `false` |
| `isLocalClass()` | `false` | `true` | `false` |
| `isAnonymousClass()` | `false` | `false` | `true` |
| `getDeclaringClass()` | Lớp/interface khai báo trực tiếp | `null` | `null` |
| `getEnclosingClass()` | Lớp chứa | Lớp bao quanh về mặt từ vựng | Lớp bao quanh về mặt từ vựng |
| `getEnclosingMethod()` | Thường `null` | Phương thức bao quanh, nếu khai báo trong phương thức | Phương thức bao quanh, nếu khai báo trong phương thức |

`getEnclosingConstructor()` trả về constructor bao quanh khi local/anonymous class được khai báo trong constructor. Trong initializer hoặc ngữ cảnh không thuộc thân method/constructor, cả `getEnclosingMethod()` và `getEnclosingConstructor()` có thể bằng `null`. Vì vậy hai giá trị `null` này không đủ để kết luận đó là member class.

Ví dụ kiểm tra khai báo chứa:

```java
Class<?> member = BinaryNamesDemo.StaticMember.class;
System.out.println(member.getDeclaringClass() == BinaryNamesDemo.class); // true
System.out.println(member.getEnclosingClass() == BinaryNamesDemo.class); // true
```

Đây là **đoạn mã minh họa bổ sung** chạy trong ngữ cảnh đã biên dịch `BinaryNamesDemo`, không phải một chương trình hoàn chỉnh độc lập.

Đối với debugger, tracer, hay công cụ phân tích metadata, nên dựa vào `isMemberClass()`, `isLocalClass()`, `isAnonymousClass()` cùng các API `getEnclosing...()`. Việc suy đoán dựa trên mẫu ký tự `$1` vừa dễ sai vừa nhạy cảm với thay đổi của compiler.

## 5. Nạp lớp và những trường hợp tên dễ nhầm

### Nạp lớp theo tên

`Class.forName(String)` cần binary name khi nạp một lớp thông thường:

```java
Class<?> type = Class.forName("BinaryNamesDemo$StaticMember");
System.out.println(type == BinaryNamesDemo.StaticMember.class); // true
```

Trong ví dụ, `BinaryNamesDemo` thuộc package mặc định nên tên không có tiền tố package. Nếu đặt trong `package example;`, tên nhị phân thành `example.BinaryNamesDemo$StaticMember`.

Với local và anonymous class, có thể lấy tên thực tế bằng `getName()` từ đối tượng `Class<?>`. Dù vậy, hard-code tên do compiler tự sinh rất dễ hỏng khi sửa mã. Khi nạp lớp trong ứng dụng thực tế còn cần xét **ClassLoader**: tên giống nhau nhưng hai class loader khác nhau có thể cho hai đối tượng `Class` khác nhau. `Class.forName(name)` có thể kích hoạt khởi tạo lớp; dạng `Class.forName(name, false, loader)` cho phép yêu cầu nạp mà không khởi tạo tại thời điểm đó.

### Tên của mảng

`Class.getName()` đối với mảng dùng biểu diễn riêng, không đơn thuần là tên nhị phân của lớp phần tử:

- `int[].class.getName()` trả `[I`.
- `String[].class.getName()` trả `[Ljava.lang.String;`.
- `BinaryNamesDemo.StaticMember[].class.getName()` trả `[LBinaryNamesDemo$StaticMember;`.
- Mảng hai chiều của member class bắt đầu bằng `[[L`.

Ở đây, mỗi `[` biểu thị một chiều; `L...;` biểu thị phần tử là kiểu tham chiếu; `I` biểu thị `int`. Vì thế chuỗi trả về từ `getName()` của mảng không phải trực tiếp binary name của một lớp thường.

### Tên tệp khi biên dịch

`javac` thường sinh `BinaryNamesDemo.class`, `BinaryNamesDemo$StaticMember.class` và các tệp riêng cho local/anonymous class. Các tên tệp đó có liên hệ với tên nhị phân, nhưng không được dựa vào hậu tố số trong tệp `.class` để suy diễn vị trí hoặc thứ tự khai báo của local/anonymous class. Cũng không nên đổi tên tệp `.class` bằng tay để “đổi tên kiểu” trong bytecode.

## 6. Lỗi phổ biến và cách xử lý

| Lỗi | Vì sao sai | Cách đúng |
| --- | --- | --- |
| Truyền `Outer.Inner` cho `Class.forName(...)` khi `Inner` là member class | Đó là cách viết nguồn/canonical name, không phải binary name | Dùng `Outer$Inner`, kèm package nếu có |
| Đọc `Outer$2Local` thành “lớp Local thứ hai trong phương thức” | Số sinh ra không là hợp đồng ổn định về thứ tự/phương thức | Dùng `getEnclosingMethod()`, `getEnclosingClass()` và metadata |
| Xem mọi lớp có `$` là anonymous class | Member, local và cả Java identifier chứa `$` có thể xuất hiện | Dùng `isAnonymousClass()` |
| Tưởng `getCanonicalName() == null` kéo theo `getName() == null` | Local/anonymous không có canonical name nhưng vẫn có tên nhị phân sau biên dịch | Phân biệt ý nghĩa từng API |
| Cho rằng lớp lồng `static` không phải member class | `static` không xóa quan hệ khai báo trực tiếp | Kiểm tra `isMemberClass()` và `getDeclaringClass()` |
| Lưu cứng tên `Outer$1` vào cấu hình dùng lâu dài | Tên compiler sinh có thể đổi sau sửa mã hoặc đổi compiler | Sử dụng API/kiểu ổn định, hoặc nhận tên từ `Class` tại runtime |

## 7. Tự kiểm tra

1. Nếu `class Outer { static class Node {} }` nằm trong package `demo`, binary name của `Node` là gì? **`demo.Outer$Node`**; canonical name là `demo.Outer.Node`.
2. Nếu `class Local {}` nằm trong phương thức và `Local.class.getCanonicalName()` trả `null`, có phải lỗi không? **Không.** Đây là hành vi đúng; `getSimpleName()` vẫn trả `Local`.
3. Nếu biên dịch hai lần cho ra chỉ số khác nhau trong anonymous class, có nên dùng chỉ số đó làm ID bền vững? **Không.** Chỉ số do compiler sinh không dành cho việc định danh trong logic ứng dụng.
4. Muốn biết một local class xuất hiện trong phương thức nào, nên bóc số sau `$` hay gọi API? **Gọi `getEnclosingMethod()`**; xét thêm `getEnclosingConstructor()` nếu có thể khai báo trong constructor.

**Ghi nhớ:** Member class có dạng binary name `Outer$Member`; local class có dạng `Outer$<digits>Local`; anonymous class có dạng `Outer$<digits>`. Dùng `getName()` khi cần tên thực tế và dùng API Reflection chuyên dụng để xác định loại lớp cùng quan hệ chứa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-names-identity-003">Tên mảng, component type và descriptorString</a>

<details>
<summary>Xem chi tiết</summary>

# Tên mảng, component type và `Class.descriptorString()` trong Java Reflection

## 1. Vì sao tên kiểu mảng khác tên lớp thông thường?

Trong Reflection, mỗi kiểu Java có đối tượng `Class<?>` đại diện tại runtime, kể cả kiểu nguyên thủy (`int.class`), lớp (`String.class`) và **kiểu mảng** (`int[].class`, `String[][].class`). Một kiểu mảng là một kiểu riêng: `int[].class != int.class`; hai mảng `new int[2]` và `new int[10]` có **cùng** `Class` vì độ dài thuộc từng đối tượng mảng, không thuộc định danh kiểu.

Java dùng một số cách viết tên kiểu cho những mục đích khác nhau:

- **Cú pháp nguồn Java**: `java.lang.String[][]` hoặc `String[][]`.
- **Tên do `Class.getName()` trả về**: mảng dùng dạng bắt đầu bằng `[` và tên lớp tham chiếu dùng dấu chấm, ví dụ `[[Ljava.lang.String;`.
- **JVM type descriptor** từ `Class.descriptorString()`: mảng vẫn bắt đầu bằng `[` nhưng tên lớp tham chiếu dùng dấu gạch chéo, ví dụ `[[Ljava/lang/String;`.

Không hoán đổi ba dạng tên này một cách máy móc. Chúng phục vụ hiển thị, tra cứu `Class` hoặc mô tả kiểu theo quy tắc JVM khác nhau.

## 2. Component type khác element type

Một mảng đa chiều trong Java thực chất là **mảng chứa các mảng khác**. Với `String[][][]`:

```text
String[][][]                   // kiểu mảng ba chiều
  └── component: String[][]   // kiểu của một phần tử ở chiều ngoài cùng
        └── component: String[]
              └── component: String
                    └── không còn là mảng
```

- **Component type** là kiểu thành phần trực tiếp của **một cấp mảng**. `String[][][].class.getComponentType() == String[][].class`.
- **Element type** (kiểu phần tử cơ sở) là kiểu thu được khi bỏ hết các chiều mảng. Với `String[][][]`, đó là `String.class`.
- `getComponentType()` trả về `null` nếu đối tượng `Class` không biểu diễn mảng, bao gồm lớp thông thường, `int.class` và `void.class`. Luôn kiểm tra `isArray()` trước khi duyệt nhiều cấp.
- `Class.componentType()` (Java 12+) có cùng hành vi với `getComponentType()`. `Class.arrayType()` (Java 12+) làm chiều ngược lại: `String.class.arrayType() == String[].class`.

Đặc điểm quan trọng: `new int[2][]` có kiểu `int[][]` dù chưa tạo các mảng con; thông tin `Class` chỉ cho biết **kiểu**, không cho biết các nhánh đã được cấp phát hay mỗi nhánh dài bao nhiêu.

## 3. Các API tên và định danh kiểu cần phân biệt

| API trên `Class<?>` | Dùng để làm gì? | Với `String[][].class` |
| --- | --- | --- |
| `isArray()` | Kiểm tra có phải kiểu mảng | `true` |
| `getComponentType()` | Kiểu ở **một chiều** phía trong | `String[].class` |
| `getName()` | Tên lớp/kiểu theo quy tắc của `Class`; mảng dùng tên mã hóa | `[[Ljava.lang.String;` |
| `getTypeName()` | Tên dễ đọc, mô tả kiểu | `java.lang.String[][]` |
| `getSimpleName()` | Tên đơn giản, bỏ tên package ở ví dụ thông thường | `String[][]` |
| `getCanonicalName()` | Tên chuẩn dạng nguồn Java, nếu tồn tại | `java.lang.String[][]` |
| `descriptorString()` | Descriptor theo cú pháp JVM | `[[Ljava/lang/String;` |

Với lớp thường, `getName()` trả về **binary name** (`java.lang.String`, `java.util.Map$Entry`), trong khi `getCanonicalName()` của lớp thành viên có thể là `java.util.Map.Entry`. `getCanonicalName()` có thể trả về `null` cho lớp local/anonymous và mảng có kiểu phần tử cơ sở như vậy; `getSimpleName()` cũng không phải định danh toàn cục đáng tin cậy. Những tên thuận tiện cho giao diện (`getSimpleName()`, `getTypeName()`) không phải lúc nào cũng phù hợp để tải lớp.

## 4. Quy tắc của descriptor JVM

Descriptor mô tả **kiểu** một cách ngắn gọn. Các mã primitive:

| Java type | Descriptor | Java type | Descriptor |
| --- | --- | --- | --- |
| `boolean` | `Z` | `byte` | `B` |
| `char` | `C` | `short` | `S` |
| `int` | `I` | `long` | `J` |
| `float` | `F` | `double` | `D` |
| `void` | `V` | | |

`V` biểu diễn `void` nhưng `void` không thể là kiểu phần tử mảng. Với lớp hoặc interface tham chiếu, dạng là **`L<tên-nội-bộ>;`**: tên nội bộ dùng `/` giữa các package, ví dụ `Ljava/lang/String;`, `Ljava/util/List;`. Với mảng, thêm **một dấu `[` cho mỗi chiều** vào trước descriptor của kiểu phần tử cơ sở:

| Java type | `getName()` | `descriptorString()` |
| --- | --- | --- |
| `int.class` | `int` | `I` |
| `int[].class` | `[I` | `[I` |
| `int[][].class` | `[[I` | `[[I` |
| `String.class` | `java.lang.String` | `Ljava/lang/String;` |
| `String[].class` | `[Ljava.lang.String;` | `[Ljava/lang/String;` |
| `String[][].class` | `[[Ljava.lang.String;` | `[[Ljava/lang/String;` |
| `java.util.Map.Entry[].class` | `[Ljava.util.Map$Entry;` | `[Ljava/util/Map$Entry;` |

**Cách đọc `[[Ljava/lang/String;`**: hai dấu `[` chỉ hai chiều; `L...;` chỉ kiểu tham chiếu; `java/lang/String` là tên lớp nội bộ. Với primitive, `getName()` và `descriptorString()` tình cờ giống nhau cho mảng, nhưng **khác nhau với mảng kiểu tham chiếu**. Descriptor của phương thức JVM có dạng `(tham-số)kiểu-trả-về`, ví dụ `(I)Ljava/lang/String;`; `Class.descriptorString()` chỉ trả **descriptor của một kiểu**, không phải cả method descriptor.

## 5. Ví dụ Java 17+ chạy độc lập

Tạo tệp `ArrayTypeInspection.java`:

```java
import java.lang.reflect.Array;

public class ArrayTypeInspection {
    static void show(Class<?> type) {
        Class<?> component = type.getComponentType();
        System.out.printf("%s%n", type.getTypeName());
        System.out.printf(
                "  isArray=%s, component=%s%n",
                type.isArray(),
                component == null ? "<none>" : component.getTypeName());
        System.out.printf("  getName=%s%n", type.getName());
        System.out.printf("  descriptor=%s%n", type.descriptorString());
    }

    public static void main(String[] args) throws Exception {
        show(int.class);
        show(int[].class);
        show(String[][].class);

        Class<?> t = String[][][].class;
        int depth = 0;
        while (t.isArray()) {
            depth++;
            t = t.getComponentType();
        }
        System.out.println("element=" + t.getTypeName()
                + ", dimensions=" + depth);

        Object created = Array.newInstance(String.class, 2, 3);
        System.out.println("created=" + created.getClass().getTypeName());

        System.out.println("roundTrip="
                + (Class.forName(String[][].class.getName())
                        == String[][].class));
    }
}
```

Biên dịch và chạy:

```bash
javac ArrayTypeInspection.java
java ArrayTypeInspection
```

Kết quả chuẩn:

```text
int
  isArray=false, component=<none>
  getName=int
  descriptor=I
int[]
  isArray=true, component=int
  getName=[I
  descriptor=[I
java.lang.String[][]
  isArray=true, component=java.lang.String[]
  getName=[[Ljava.lang.String;
  descriptor=[[Ljava/lang/String;
element=java.lang.String, dimensions=3
created=java.lang.String[][]
roundTrip=true
```

Giải thích: vòng `while` tách dần các `component type` nên đếm đúng ba chiều và tìm được phần tử cơ sở `String`. `Array.newInstance(String.class, 2, 3)` tạo một đối tượng mảng hai chiều (giá trị trả về khai báo là `Object`, nhưng kiểu runtime là `String[][]`). Dòng cuối chứng minh tên từ `getName()` của mảng tham chiếu có thể được `Class.forName(...)` dùng để tìm lại chính kiểu đó.

## 6. Tra cứu lớp mảng: `Class.forName` cần tên nào?

`Class.forName(String)` nhận **binary name** của lớp thường hoặc dạng tên mảng được `Class.getName()` trả về:

```java
Class<?> a = Class.forName("[I");                 // int[]
Class<?> b = Class.forName("[[Ljava.lang.String;"); // String[][]
boolean ok = a == int[].class && b == String[][].class;
```

Đoạn trên là **fragment** dùng trong ngữ cảnh cho phép `ClassNotFoundException` (ví dụ phương thức `throws Exception`). Với mảng tham chiếu, **không** truyền trực tiếp `"[Ljava/lang/String;"` từ `descriptorString()` vào `Class.forName`: cú pháp `/` thuộc descriptor JVM, còn `Class.forName` kỳ vọng tên kiểu mảng với `.` ở binary name thành phần. `Class.forName("java.lang.String[]")` không phải cách tra cứu mảng; `Class.forName("int")` cũng không trả về `int.class`.

Khi đã có `Class<?> component` và chỉ muốn lớp mảng tương ứng, dùng `component.arrayType()` hoặc lấy kiểu của mảng vừa tạo bằng `Array.newInstance(component, 0).getClass()`. Không cần tự nối chuỗi tên và không cần tải lớp bằng tên nếu có sẵn đối tượng `Class`.

## 7. Tạo và truy cập mảng động bằng Reflection

`java.lang.reflect.Array` làm việc với mảng dù lúc biên dịch không biết kiểu phần tử:

```java
Class<?> component = int.class;
Object values = Array.newInstance(component, 3); // runtime: int[]
Array.setInt(values, 0, 42);
int first = Array.getInt(values, 0);              // 42
int size = Array.getLength(values);               // 3
Class<?> arrayClass = values.getClass();          // int[].class
```

Đây là **fragment**, dùng khi đã `import java.lang.reflect.Array;`. Muốn thêm chiều, `Array.newInstance(String.class, 2, 3)` tạo `String[][]`. Nếu `component` bản thân đã là mảng, cấp phát thêm một chiều bằng `Array.newInstance(String[].class, 2)` tạo `String[][]`; các phần tử bên trong ban đầu là `null`, khác với `new String[2][3]`. Dạng `Array.newInstance(component, int... dimensions)` cộng số chiều có sẵn của `component` với số kích thước mới được truyền vào.

`Array.getLength` trả độ dài **một mảng cụ thể**, không trả số chiều của kiểu. Đọc/ghi mảng nhiều chiều phải lấy từng mảng con rồi mới truy cập phần tử; các hàng có thể dài khác nhau hoặc `null`. Với mảng primitive, `Array.get` trả giá trị được boxing; các hàm chuyên biệt như `getInt`/`setInt` tránh nhầm kiểu khi cần thao tác phần tử số nguyên.

Các lỗi dễ gặp:

- `getComponentType()` trả `null` trên `String.class` và `int.class`: không gọi tiếp trên `null`.
- `new int[4][5].getClass()` là `int[][].class` nhưng `getComponentType()` chỉ trả `int[].class`, chưa phải `int.class`.
- `Array.newInstance(void.class, 2)` không hợp lệ; số chiều lớn hơn 255 hoặc độ dài âm cũng bị từ chối.
- Kiểu mảng và độ dài mảng là hai khái niệm khác nhau. `getName()` và descriptor không chứa độ dài.
- Đừng dùng `getTypeName()` hay `getSimpleName()` làm khóa định danh duy nhất: class loader khác nhau có thể định nghĩa các lớp trùng tên. Nếu đang có `Class<?>`, ưu tiên so sánh `Class` hoặc làm việc trực tiếp với nó.
- Reflection bằng `Class<?>` diễn tả **kiểu đã bị erasure**: ví dụ `List<String>[]` ở runtime là kiểu mảng của `List`, descriptor `[Ljava/util/List;`; thông tin tham số generic ở chỗ khai báo, nếu cần, được xem qua API `Type`/`GenericArrayType` thích hợp.

## 8. Chọn API theo tình huống

| Câu hỏi trong chương trình | API nên dùng |
| --- | --- |
| Đầu vào có phải một kiểu mảng? | `type.isArray()` |
| Kiểu của mỗi phần tử tại cấp ngoài cùng? | `type.getComponentType()` |
| Mảng có bao nhiêu chiều, element type là gì? | Lặp `getComponentType()` khi `isArray()` |
| Muốn hiển thị kiểu dễ đọc trong log? | `type.getTypeName()` |
| Muốn cú pháp descriptor cho JVM/tooling? | `type.descriptorString()` |
| Muốn tra cứu lại một kiểu mảng bằng tên được Java hỗ trợ? | `Class.forName(type.getName())`, xử lý ngoại lệ |
| Muốn thêm một chiều từ một `Class` sẵn có? | `type.arrayType()` |
| Muốn cấp phát mảng lúc runtime? | `Array.newInstance(component, dimensions...)` |

**Tự kiểm tra.** (1) `int[][][].class.getComponentType()` trả kiểu nào? **`int[][].class`.** (2) Descriptor của `boolean[][]` là gì? **`[[Z`.** (3) `String[].class.getName()` và `descriptorString()` có giống nhau? **Không:** `[Ljava.lang.String;` so với `[Ljava/lang/String;`. (4) `String.class.getComponentType()` là gì? **`null`.**

Tài liệu tham chiếu: [Java SE 21 — `Class`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html) và [Java SE 21 — `java.lang.reflect.Array`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Array.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-names-identity-004">Trường hợp tên chuẩn không tồn tại</a>

<details>
<summary>Xem chi tiết</summary>

# Trường hợp tên chuẩn không tồn tại trong Java Reflection

## 1. Vấn đề cần hiểu

Khi phân tích một kiểu bằng Java Reflection, lời gọi `Class<?>.getCanonicalName()` **có thể trả về `null`**. Đây là kết quả hợp lệ của API: kiểu đó không có *canonical name* (tên chuẩn) theo Java Language Specification (JLS), không có nghĩa là `Class` bị lỗi, kiểu không tồn tại, hay Reflection không tìm thấy kiểu.

Tên chuẩn là tên ổn định theo quy tắc khai báo kiểu trong ngôn ngữ Java. Ví dụ, `java.lang.String`, `java.util.Map.Entry` và `java.lang.String[][]` là các tên chuẩn. Với kiểu thành viên, tên chuẩn dùng dấu chấm giữa tên kiểu bao ngoài và kiểu thành viên; nó khác với *binary name* thường dùng dấu `$`.

Các trường hợp tiêu biểu **không có tên chuẩn**:

- **Local class**: lớp khai báo bên trong khối lệnh như phương thức hoặc constructor.
- **Anonymous class**: lớp vô danh được tạo bằng biểu thức `new Interface() { ... }` hoặc `new Parent() { ... }`.
- **Hidden class**: lớp ẩn do API JVM tạo ra (có từ Java 15), thường phục vụ cơ chế runtime/framework.
- **Mảng có kiểu thành phần không có tên chuẩn**: ví dụ `Local[]` hoặc mảng của một anonymous class. Tính chất này lan truyền qua nhiều chiều mảng.
- Theo quy tắc tên chuẩn của JLS, **kiểu thành viên có kiểu bao ngoài không có tên chuẩn** cũng không có tên chuẩn.

Đây là danh sách các trường hợp quan trọng, không nên suy diễn rằng mọi kiểu do trình biên dịch sinh ra đều có cùng hành vi. Khi dùng một phiên bản Java mới, đối chiếu Javadoc của phiên bản đó.

## 2. Phân biệt bốn phương thức lấy tên

| API | Ý nghĩa chính | Khi kiểu không có tên chuẩn |
| --- | --- | --- |
| `getCanonicalName()` | Tên chuẩn theo JLS, hoặc `null` | Trả `null` |
| `getName()` | Tên do VM/JVM biểu diễn cho `Class`; với kiểu tham chiếu thông thường thường là binary name; với mảng dùng dạng mô tả riêng | Thường vẫn trả một chuỗi; không được đồng nhất với canonical name |
| `getSimpleName()` | Tên ngắn trong khai báo nguồn; anonymous class có tên ngắn rỗng | Local class thường có tên ngắn; anonymous class trả `""` |
| `getTypeName()` | Chuỗi cung cấp thông tin để hiển thị kiểu | Có thể dùng để ghi log nhưng không đảm bảo là tên chuẩn hay tên có thể nạp bằng `Class.forName` |

Ví dụ với lớp thành viên `CanonicalNameCases.Member`:

- `Member.class.getCanonicalName()` → `"CanonicalNameCases.Member"`.
- `Member.class.getName()` → `"CanonicalNameCases$Member"`.

Với `String[].class`, `getCanonicalName()` là `"java.lang.String[]"` còn `getName()` là `"[Ljava.lang.String;"`. Các chuỗi này phục vụ những mục đích khác nhau.

**Không thể** suy ra rằng `getCanonicalName() == null` kéo theo `getSimpleName() == null`: anonymous class có simple name là chuỗi rỗng, trong khi local class vẫn có simple name.

## 3. Ví dụ Java 17+: local, anonymous và mảng

Tạo tệp **`CanonicalNameCases.java`** với toàn bộ mã sau (không khai báo `package` để kết quả tên lớp dễ đối chiếu):

```java
import java.lang.reflect.Array;

public class CanonicalNameCases {
    static class Member {}

    private static void print(String label, Class<?> type) {
        System.out.println(label
                + " => canonical=" + type.getCanonicalName()
                + ", simple='" + type.getSimpleName() + "'"
                + ", local=" + type.isLocalClass()
                + ", anonymous=" + type.isAnonymousClass());
    }

    public static void main(String[] args) {
        class Local {}

        Runnable anonymous = new Runnable() {
            @Override
            public void run() {}
        };

        Class<?> localArray =
                Array.newInstance(Local.class, 0).getClass();
        Class<?> anonymousArray =
                Array.newInstance(anonymous.getClass(), 0).getClass();

        print("Member", Member.class);
        print("Local", Local.class);
        print("Anonymous", anonymous.getClass());
        print("Local[]", localArray);
        print("Anonymous[]", anonymousArray);
        print("String[]", String[].class);
        print("int", int.class);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac CanonicalNameCases.java
java CanonicalNameCases
```

Kết quả chuẩn:

```text
Member => canonical=CanonicalNameCases.Member, simple='Member', local=false, anonymous=false
Local => canonical=null, simple='Local', local=true, anonymous=false
Anonymous => canonical=null, simple='', local=false, anonymous=true
Local[] => canonical=null, simple='Local[]', local=false, anonymous=false
Anonymous[] => canonical=null, simple='[]', local=false, anonymous=false
String[] => canonical=java.lang.String[], simple='String[]', local=false, anonymous=false
int => canonical=int, simple='int', local=false, anonymous=false
```

**Giải thích từng nhóm:**

1. `Member` là lớp thành viên có tên, được khai báo trong lớp `CanonicalNameCases`. Kiểu bao ngoài có tên chuẩn nên `Member` cũng có: `CanonicalNameCases.Member`. Không phải mọi nested class đều thiếu tên chuẩn.
2. `Local` được khai báo *bên trong phương thức `main`*, vì vậy `Local.class.getCanonicalName()` trả `null` mặc dù `getSimpleName()` trả `"Local"`. Tên VM (từ `getName()`) có thể chứa dấu `$` và số do compiler tạo; không nên mã hóa các giá trị này.
3. Đối tượng `anonymous` thuộc anonymous class. Nó không có tên định danh khai báo trong nguồn, nên cả tên chuẩn không tồn tại và tên ngắn là chuỗi rỗng.
4. `localArray` và `anonymousArray` đều là **Class biểu diễn kiểu mảng**, nên `isLocalClass()` và `isAnonymousClass()` của *bản thân mảng* đều `false`. Tuy vậy, `getCanonicalName()` của mảng vẫn `null` do **kiểu thành phần** không có tên chuẩn. Nếu tạo mảng hai chiều từ các kiểu này, kết quả vẫn `null`.
5. `String[]` có tên chuẩn hợp lệ vì `String` có tên chuẩn. Kiểu nguyên thủy `int` cũng có tên chuẩn là `"int"`. `void.class.getCanonicalName()` trả `"void"`.

Việc sử dụng `Array.newInstance(componentType, 0)` giúp lấy `Class` của mảng khi chỉ có `Class<?>` của kiểu thành phần lúc runtime.

## 4. Lý do tên chuẩn biến mất

### Local class

```java
void process() {
    class Worker {}
    Class<?> type = Worker.class;
    System.out.println(type.getCanonicalName()); // null
}
```

Đây là **mảnh mã minh họa bên trong một class**, không phải chương trình độc lập. `Worker` chỉ có phạm vi định danh trong ngữ cảnh cục bộ; không thể hình thành tên chuẩn theo quy tắc dành cho top-level/member types. Điều này không ngăn việc lấy `Class`, đọc phương thức/trường, hoặc khởi tạo theo các quyền truy cập phù hợp.

### Anonymous class

```java
Runnable task = new Runnable() {
    @Override public void run() {}
};
Class<?> type = task.getClass();
System.out.println(type.getCanonicalName()); // null
System.out.println(type.getSimpleName().isEmpty()); // true
```

Mã này cũng là **mảnh minh họa đặt trong phương thức**. Tên như `Outer$1` mà đôi khi thấy ở `getName()` là chi tiết định danh do runtime/compiler tạo, không biến anonymous class thành kiểu có canonical name.

### Hidden class

Java 15 bổ sung hidden classes, thường được định nghĩa bằng `MethodHandles.Lookup#defineHiddenClass`. Một `Class<?>` biểu diễn hidden class đáp ứng `isHidden() == true` và `getCanonicalName() == null`. Hidden class **không đồng nghĩa** với anonymous class: `isHidden()` và `isAnonymousClass()` kiểm tra hai đặc tính khác nhau. Mặc dù `getName()` có thể trả tên ở dạng nội bộ (chẳng hạn chứa `/`), không nên dùng chuỗi đó như tên nguồn Java hoặc dựa vào `Class.forName` để nạp hidden class.

### Hiệu ứng lan truyền qua kiểu chứa/mảng

Nếu kiểu gốc không có tên chuẩn, việc bọc kiểu đó thành `T[]` hay `T[][]` không tự tạo ra canonical name. Tương tự, một kiểu thành viên khai báo trong kiểu bao ngoài không có tên chuẩn không thể có tên chuẩn đầy đủ từ chuỗi các kiểu bao ngoài.

Ngược lại, các kiểu khai báo có tên như top-level class, member class, interface, enum, record (Java 16+) và array của các kiểu có tên chuẩn thường có `getCanonicalName()` khác `null`. **Nested/member không có nghĩa là local/anonymous.**

## 5. Xử lý an toàn trong code Reflection

### Khi chỉ cần tên để hiển thị hoặc ghi log

```java
static String displayName(Class<?> type) {
    String canonical = type.getCanonicalName();
    return canonical != null ? canonical : type.getTypeName();
}
```

Đây là **mảnh phương thức** cần đặt trong một class. `getTypeName()` là lựa chọn dự phòng để hiển thị, không phải lời hứa trả về tên chuẩn hoặc đầu vào hợp lệ cho `Class.forName`.

Tránh mẫu dễ gây `NullPointerException`:

```java
// Sai: getCanonicalName() có thể trả null.
String shortLabel = someClass.getCanonicalName().toLowerCase();
```

Thay vào đó, kiểm tra `null` và quyết định rõ chính sách: hiển thị tên thay thế, dùng định danh runtime, hoặc từ chối các kiểu không có tên chuẩn.

### Khi cần biết chính xác vì sao `null`

```java
import java.lang.reflect.Array;

static String explain(Class<?> type) {
    Class<?> elementType = type;
    while (elementType.isArray()) {
        elementType = elementType.getComponentType();
    }

    if (elementType.isHidden()) return "hidden class";
    if (elementType.isAnonymousClass()) return "anonymous class";
    if (elementType.isLocalClass()) return "local class";
    if (type.getCanonicalName() == null) {
        return "không có canonical name (trường hợp khác)";
    }
    return "có canonical name";
}
```

Đây là **mảnh phương thức cần đặt trong class**; dòng `import` nằm ở đầu tệp nguồn. `getComponentType()` gỡ từng lớp mảng để kiểm tra **kiểu phần tử cuối cùng**. Ví dụ, `Local[][]` có `isLocalClass() == false` ở cấp mảng, nhưng lớp `Local` bên trong là local class. Nhánh cuối dành cho các trường hợp khác mà API/JDK hiện tại hoặc tương lai có thể hỗ trợ; không nên tự kết luận tất cả giá trị `null` đều chỉ có một nguyên nhân.

Nếu chương trình cần nạp lại class theo tên, trước tiên xác định loại tên API yêu cầu. `Class.forName` sử dụng binary name cho lớp thông thường (ví dụ `"CanonicalNameCases$Member"`), không sử dụng canonical name `"CanonicalNameCases.Member"` của lớp thành viên. Local/anonymous/hidden classes không có một canonical name để lưu trữ rồi nạp lại theo cách này. Với reflection ở runtime, nên giữ chính `Class<?>`, đối tượng hoặc metadata được cấp trực tiếp thay vì đoán tên do compiler sinh ra.

## 6. Sai lầm thường gặp

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “Mọi `Class<?>` đều có `getCanonicalName()`.” | API có quyền trả `null` cho kiểu không có tên chuẩn. |
| “Tên chuẩn `null` nghĩa là lớp không tồn tại.” | Kiểu vẫn có thể được tải, kiểm tra và sử dụng bằng Reflection. |
| “`getName()` và `getCanonicalName()` tương đương.” | Member types và arrays cho thấy hai cách đặt tên khác nhau; `getName()` không thay được quy tắc canonical name. |
| “Anonymous class cũng có tên ngắn `null`.” | `getSimpleName()` trả chuỗi rỗng `""`. |
| “`isLocalClass()` đủ để nhận diện mảng local class.” | `isLocalClass()` của chính kiểu mảng là `false`; phải đi qua `getComponentType()`. |
| “Lambda luôn là anonymous class.” | Lambda không được đồng nhất với anonymous class; lớp triển khai do runtime tạo là chi tiết hiện thực, không nên phụ thuộc vào tên hoặc cách phân loại của nó. |
| “Có thể lưu canonical name rồi dùng `Class.forName` cho mọi kiểu.” | Canonical name có thể `null`; ngay cả nested member types có tên chuẩn cũng thường cần binary name để dùng với `Class.forName`. |

## 7. Bài tập tự kiểm tra

1. Đặt `class Temp {}` trong phương thức, rồi so sánh `Temp.class.getCanonicalName()` và `Temp.class.getSimpleName()`. Giải thích vì sao một bên là `null` còn bên kia là `"Temp"`.
2. Với `Runnable` tạo bằng anonymous class, dự đoán kết quả của `getCanonicalName()`, `getSimpleName()` và `isAnonymousClass()`.
3. Tạo kiểu mảng hai chiều từ `Temp.class` bằng `Array.newInstance(Temp.class, 2, 3).getClass()`. Kiểm tra tên chuẩn của mảng, rồi gỡ từng `getComponentType()` để thấy kiểu gốc.
4. Thử `Member.class.getName()`, `Member.class.getCanonicalName()` và `Class.forName(Member.class.getName())`. Phân tích vì sao định danh dùng để nạp lớp và tên chuẩn khác nhau.

**Tự đánh giá:** Nếu biết `getCanonicalName() == null`, bạn đã biết tên chuẩn *không tồn tại*, nhưng chưa đủ dữ liệu để khẳng định nguyên nhân. Hãy phân loại kiểu qua `isArray()`/`getComponentType()`, `isLocalClass()`, `isAnonymousClass()`, `isHidden()` và ngữ cảnh tạo kiểu trước khi quyết định cách xử lý.

## 8. Tham khảo chính thức

- [Javadoc `Class.getCanonicalName()`, Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getCanonicalName()) — điều kiện trả `null` và cách phân biệt tên.
- [Java Language Specification, mục 6.7](https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.7) — quy tắc fully qualified names và canonical names.
- [Dev.java — Reading Class Names](https://dev.java/learn/reflection/names/) — ví dụ `getName()`, `getSimpleName()`, `getCanonicalName()`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-names-identity-005">getClassLoader và ý nghĩa kết quả null</a>

<details>
<summary>Xem chi tiết</summary>

# Class.getClassLoader() và ý nghĩa kết quả null trong Java Reflection

## 1. Bài toán và mục tiêu

Trong Java Reflection, đối tượng `Class<?>` mô tả một kiểu ở runtime. Phương thức `Class.getClassLoader()` giúp tìm **class loader định nghĩa kiểu đó**. Kết quả có thể là một đối tượng `ClassLoader` hoặc `null`. Hiểu đúng `null` rất quan trọng khi chẩn đoán lỗi nạp lớp, thiết kế plugin, tra cứu tài nguyên hoặc sử dụng `Class.forName(...)`.

Sau bài này, bạn có thể:
- Phân biệt class loader của một kiểu với system class loader và context class loader của luồng.
- Giải thích `null` đối với lớp do bootstrap loader định nghĩa, primitive, `void` và kiểu mảng.
- Viết mã không phát sinh `NullPointerException` khi gặp `null`, đồng thời chọn đúng loader cho nhu cầu thực tế.
- Nhận diện các kết luận sai khi chỉ dựa vào giá trị trả về của `getClassLoader()`.

## 2. Phương thức hoạt động thế nào?

Khai báo API:

~~~java
public ClassLoader getClassLoader()
~~~

Đây là phương thức của `java.lang.Class`, thường gọi như sau:

~~~java
Class<?> type = String.class;
ClassLoader loader = type.getClassLoader();
~~~

Mỗi kiểu tham chiếu do JVM định nghĩa được gắn với *defining class loader* (loader đã định nghĩa lớp bằng bytecode hoặc cơ chế tương ứng). Khi có đối tượng `Class<?>`, `getClassLoader()` cho biết loader gắn với kiểu. Nó **không trực tiếp nạp lại lớp**, không tạo class loader mới và không trả về loader đang được luồng sử dụng theo ngữ cảnh.

Java thường tổ chức các loader theo cơ chế ủy quyền cho loader cha:
- **Bootstrap class loader**: tầng nạp lớp nền tảng của JVM; trong các triển khai thông dụng, Java API biểu diễn nó bằng `null`.
- **Platform class loader**: nạp một số lớp thuộc các module/platform API; có thể lấy qua `ClassLoader.getPlatformClassLoader()` (Java 9+).
- **Application/System class loader**: thường nạp ứng dụng chạy trên classpath và được lấy qua `ClassLoader.getSystemClassLoader()`.

Đây là mô hình thường gặp; ứng dụng có thể dùng loader tùy biến và cây ủy quyền khác. Không suy ra rằng **mọi lớp trong package `java.*` đều do bootstrap loader nạp**, hoặc class loader của mọi lớp ứng dụng luôn đúng bằng system loader.

### 2.1. Bảng diễn giải kết quả

| Kiểu được hỏi | `getClassLoader()` thường trả về | Cách hiểu đúng |
| --- | --- | --- |
| `String.class` trên OpenJDK/HotSpot | `null` | `String` là lớp nền tảng do bootstrap loader định nghĩa. |
| `int.class`, `boolean.class`, `void.class` | `null` | Primitive và `void` không có defining class loader theo API; **không** diễn giải là một class file được bootstrap nạp. |
| `int[].class` | `null` | Mảng có phần tử cơ sở là primitive; mảng là kiểu tham chiếu nhưng không có defining loader được trả về. |
| `String[].class` | `null` trên các JDK nói trên | Mảng dùng defining loader của kiểu phần tử cơ sở `String`, vốn là bootstrap. |
| `MyType.class` chạy bằng `java` thông thường | một `ClassLoader` khác `null` | Thường là application loader; có thể khác trong container, IDE hoặc ứng dụng có loader tùy biến. |
| `MyType[][].class` | cùng loader với `MyType.class` | Mảng nhiều chiều gắn với loader của kiểu phần tử cơ sở. |

**Phân biệt quan trọng:** `null` không có một cách diễn giải duy nhất. Với kiểu tham chiếu *không phải mảng*, `null` thường biểu thị bootstrap loader. Với primitive/`void`, `null` là quy định riêng của API; với mảng, phải kiểm tra kiểu phần tử cơ sở trước khi kết luận.

## 3. Thí nghiệm Java 17+ chạy độc lập

Tạo file `GetClassLoaderDemo.java`:

~~~java
public class GetClassLoaderDemo {
    static final class MyType {
    }

    private static void print(String label, Class<?> type) {
        ClassLoader loader = type.getClassLoader();
        System.out.println(label + " -> " +
                (loader == null ? "null" : "non-null"));
    }

    public static void main(String[] args)
            throws ClassNotFoundException {
        print("String", String.class);
        print("int", int.class);
        print("void", void.class);
        print("int[]", int[].class);
        print("String[]", String[].class);
        print("MyType", MyType.class);
        print("MyType[][]", MyType[][].class);

        System.out.println("same loader -> " +
                (MyType.class.getClassLoader()
                        == MyType[][].class.getClassLoader()));

        Class<?> bootstrapType =
                Class.forName("java.lang.String", false, null);
        System.out.println("bootstrap lookup -> " +
                (bootstrapType == String.class));
    }
}
~~~

Biên dịch và chạy với JDK 17+ trên classpath thông thường:

~~~bash
javac GetClassLoaderDemo.java
java GetClassLoaderDemo
~~~

Kết quả trên môi trường OpenJDK/HotSpot thông thường:

~~~text
String -> null
int -> null
void -> null
int[] -> null
String[] -> null
MyType -> non-null
MyType[][] -> non-null
same loader -> true
bootstrap lookup -> true
~~~

Giải thích:
1. `String` được bootstrap loader định nghĩa. `int` và `void` trả về `null` theo quy tắc của `Class.getClassLoader()`; không nên gom ba trường hợp thành một nguyên nhân.
2. `int[]` là một lớp mảng, không phải primitive (`int[].class.isPrimitive()` là `false`), nhưng loader trả về vẫn là `null` vì phần tử cơ sở `int` là primitive.
3. `String[]` có cùng loader với `String`. `MyType[][]` có cùng loader với `MyType`; mặc định `MyType` được nạp qua application loader nên khác `null`.
4. Đối số loader bằng `null` trong `Class.forName(String, boolean, ClassLoader)` có ý nghĩa **dùng bootstrap class loader**, không có nghĩa dùng loader mặc định của ứng dụng. Vì vậy tên `java.lang.String` tìm được.

Tên lớp cụ thể của application loader và chuỗi từ `loader.toString()` phụ thuộc JVM/cấu hình. Ví dụ cố ý chỉ in `null`/`non-null` để không nhầm chi tiết triển khai với hợp đồng API.

## 4. Nhận diện chính xác trường hợp null

Nếu cần một thông báo giải thích khi khảo sát lớp bất kỳ, hãy đi từ kiểu đầu vào:

~~~java
static String classify(Class<?> type) {
    Class<?> base = type;
    while (base.isArray()) {
        base = base.getComponentType();
    }

    if (base.isPrimitive()) {
        return "primitive/void hoặc mảng của primitive";
    }

    return type.getClassLoader() == null
            ? "kiểu tham chiếu do bootstrap loader định nghĩa"
            : "kiểu tham chiếu do loader khác định nghĩa";
}
~~~

Đoạn trên là **phương thức minh họa**, đặt bên trong một lớp Java; đây không phải chương trình có `main` độc lập. `getComponentType()` trả về kiểu thành phần của mảng một chiều và `null` nếu không phải mảng. Vòng lặp lần tới kiểu phần tử cơ sở, nhờ đó xử lý được `int[][]`, `String[][]` và các mảng nhiều chiều khác.

Với primitive/`void`, `Class.isPrimitive()` trả về `true` (bao gồm `void.class`). Với lớp tham chiếu thông thường, một kết quả loader `null` có thể được diễn giải là bootstrap trong các triển khai dùng `null` để biểu diễn bootstrap loader. Không dùng riêng biểu thức `loader == null` như một kiểm tra lỗi hay bằng chứng rằng kiểu chưa được nạp.

## 5. Dùng kết quả vào công việc thực tế

### 5.1. Tránh NullPointerException khi gọi phương thức của loader

Mã dễ lỗi:

~~~java
ClassLoader loader = String.class.getClassLoader();
loader.loadClass("java.lang.Integer"); // NullPointerException nếu loader == null
~~~

Nếu mục tiêu là nạp một tên lớp qua **chính loader của lớp mốc**, dùng API chấp nhận `null` rõ ràng:

~~~java
static Class<?> findFromSameLoader(
        String binaryName, Class<?> anchor)
        throws ClassNotFoundException {
    return Class.forName(binaryName, false,
            anchor.getClassLoader());
}
~~~

Ví dụ `findFromSameLoader("java.lang.Integer", String.class)` hoạt động với bootstrap loader trên OpenJDK thông thường. Tham số `false` yêu cầu không khởi tạo lớp chỉ vì lệnh `forName` này; nó không cấm việc liên kết/nạp lớp cần thiết. Lưu ý tên truyền vào phải theo quy tắc tên mà `Class.forName` yêu cầu; tên primitive như `"int"` không được xử lý như `int.class`.

Không tự động đổi `null` thành `ClassLoader.getSystemClassLoader()`: hai loader có thể nhìn thấy **những tập lớp khác nhau**. Đặc biệt trong plugin, application server hay môi trường nhiều module, fallback tùy tiện có thể nạp sai bản lớp hoặc ném `ClassNotFoundException`.

### 5.2. Đọc tài nguyên mà không cần gọi loader trực tiếp

Khi tài nguyên gắn với một lớp mốc, có thể dùng `Class.getResourceAsStream(...)`:

~~~java
try (java.io.InputStream input =
        String.class.getResourceAsStream("/example/data.txt")) {
    if (input == null) {
        System.out.println("Không tìm thấy resource");
    } else {
        // Đọc nội dung từ input ở đây.
    }
}
~~~

Đây là **đoạn minh họa đặt trong một phương thức có thể ném IOException**; `/example/data.txt` chỉ là đường dẫn mẫu và thường không tồn tại. Khi tên bắt đầu bằng `/`, nó được hiểu từ gốc tài nguyên mà API `Class` sử dụng. Phương thức trả về `null` khi không tìm thấy resource; kiểm tra `input == null` là kiểm tra **resource**, khác hoàn toàn với `type.getClassLoader() == null`.

### 5.3. Chọn đúng loader cho class discovery và plugin

Ba biểu thức dễ bị dùng thay thế cho nhau nhưng khác mục đích:

| Biểu thức | Ý nghĩa |
| --- | --- |
| `SomeType.class.getClassLoader()` | Defining loader của `SomeType`; phù hợp khi cần thao tác theo đúng không gian lớp chứa kiểu mốc. |
| `ClassLoader.getSystemClassLoader()` | System loader của JVM; thường dùng với classpath ứng dụng thông thường, không bảo đảm định nghĩa `SomeType`. |
| `Thread.currentThread().getContextClassLoader()` | Context class loader do môi trường/luồng thiết lập; thường có ích cho SPI, discovery hoặc framework cần tìm lớp do ứng dụng cung cấp. |

Với plugin có loader riêng, có thể có hai kiểu **cùng binary name** nhưng do hai defining loader khác nhau định nghĩa: chúng là hai lớp khác nhau đối với JVM. Vì vậy chỉ so tên lớp qua `Class.getName()` chưa đủ để xác định cùng kiểu; `Class<?>` và loader cũng là những manh mối quan trọng. Tùy hợp đồng của framework, bạn cần chính loader đã nạp plugin hoặc context loader phù hợp, thay vì cố đoán từ tên lớp.

### 5.4. Chẩn đoán khi kết quả bất ngờ

Thứ tự kiểm tra hữu ích:
1. In `type.getName()`, `type.isPrimitive()` và `type.isArray()`; với mảng, lần theo `getComponentType()` tới kiểu phần tử cơ sở.
2. Đọc `type.getClassLoader()` bằng cách kiểm tra `null` trước khi gọi bất kỳ phương thức nào trên nó.
3. Nếu loader khác `null`, có thể xem `loader.getClass().getName()` và chuỗi loader cha để chẩn đoán; đừng dùng tên lớp loader làm điều kiện nghiệp vụ cố định.
4. So sánh với loader của lớp mốc mong muốn và context loader nếu bài toán liên quan discovery/framework.
5. Khi gặp `ClassNotFoundException` hoặc `NoClassDefFoundError`, kiểm tra **loader đã được dùng tại vị trí nạp** và tính sẵn có của dependency; chỉ nhìn kết quả loader của một lớp khác thường chưa đủ.

## 6. Câu hỏi thường gặp và lỗi dễ mắc

**`String.class.getClassLoader() == null` có nghĩa là lỗi?** Không. Trên JVM thông dụng, đây là kết quả bình thường vì lớp `String` thuộc nhóm lớp được bootstrap loader định nghĩa.

**`int.class.getClassLoader() == null` có chứng minh JVM tìm thấy file `int.class` qua bootstrap?** Không. Primitive và `void` không có class file nạp như lớp Java thông thường; `null` là quy định của API cho các đối tượng `Class` mô tả những kiểu ấy.

**Mảng luôn có loader riêng?** Không. `getClassLoader()` của lớp mảng phản ánh loader của kiểu phần tử cơ sở; mảng primitive trả về `null`. Điều này không có nghĩa bản thân mảng là primitive.

**Có thể gọi `getClassLoader()` để biết lớp có tồn tại không?** Không. Bạn đã có đối tượng `Class<?>` thì kiểu đã được biểu diễn ở runtime. Giá trị `null` chỉ cho biết đặc điểm loader hoặc loại kiểu, không phải trạng thái tìm thấy lớp.

**Nếu `getClassLoader()` khác `null` thì có phải application loader?** Không. Nó có thể là platform loader, custom loader hoặc loader framework. Hãy kiểm tra môi trường và hợp đồng nạp lớp.

**Nên thay loader `null` bằng system loader để tránh NPE?** Chỉ khi bài toán **thực sự yêu cầu** system loader. Với bootstrap lookup, `Class.forName(name, false, null)` có ý nghĩa rõ ràng; với tài nguyên gắn với lớp, ưu tiên API `Class.getResource...` nếu phù hợp.

## 7. Ghi nhớ

- `Class.getClassLoader()` mô tả loader gắn với **kiểu** được phản ánh, không nhất thiết là loader của luồng hay system loader.
- `null` có thể đại diện bootstrap loader, hoặc là kết quả bắt buộc đối với primitive/`void` và các mảng có phần tử cơ sở primitive.
- Để suy luận đúng với mảng, xác định **kiểu phần tử cơ sở** trước.
- Khi truyền `null` cho overload `Class.forName(name, initialize, loader)`, yêu cầu là nạp bằng **bootstrap loader**.
- Chọn loader theo nhu cầu nạp lớp/resource; kiểm tra `null` trước khi gọi phương thức instance của loader.

### Tự kiểm tra

1. Vì sao `int[].class.isPrimitive()` là `false` nhưng `int[].class.getClassLoader()` là `null`?
2. Dưới classpath thông thường, `MyType[].class.getClassLoader() == MyType.class.getClassLoader()` có đúng không, và vì sao?
3. Nếu một plugin class được nạp bởi custom loader, tại sao thay `pluginClass.getClassLoader()` bằng `ClassLoader.getSystemClassLoader()` có thể làm lỗi việc tìm lớp phụ thuộc?

**Đáp án gợi ý:** (1) Mảng là kiểu tham chiếu nhưng loader của nó theo kiểu phần tử cơ sở `int`, thuộc primitive; (2) đúng, vì mảng dùng defining loader của kiểu phần tử cơ sở; (3) system loader có thể không nhìn thấy các lớp chỉ tồn tại trong không gian của custom loader.

**Tài liệu API:** Java SE `java.lang.Class#getClassLoader()`, `Class#forName(String, boolean, ClassLoader)`, `Class#getComponentType()` và `java.lang.ClassLoader`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-names-identity-006">Tên lớp giống nhau nhưng khác defining loader</a>

<details>
<summary>Xem chi tiết</summary>

# Tên lớp giống nhau nhưng khác defining loader trong Java

Hai lớp Java có thể cùng tên, thậm chí được nạp từ **cùng một file `.class` có bytecode giống hệt**, nhưng JVM vẫn coi chúng là **hai kiểu khác nhau** nếu chúng được **định nghĩa bởi hai class loader khác nhau**. Đây là điểm quan trọng khi dùng Reflection để nhận diện kiểu: chỉ so sánh `Class.getName()` hoặc tên hiển thị là chưa đủ.

Bài học thuộc chủ đề **Tên và định danh kiểu**. Ta sẽ phân biệt **tên lớp (binary name)**, **defining class loader**, **initiating class loader** và **định danh runtime của `Class<?>`**; sau đó quan sát hệ quả đối với ép kiểu, gọi phương thức và thiết kế plugin.

## 1. Một tên lớp không bảo đảm chỉ có một kiểu runtime

Trong mã Java bình thường, bạn thường nghĩ `com.example.Plugin` là một kiểu duy nhất. Suy nghĩ này có vẻ đúng vì phần lớn ứng dụng dùng mô hình **ủy quyền về parent class loader**, khiến một bản định nghĩa được dùng chung.

Nhưng JVM cho phép nhiều class loader tạo ra các định nghĩa lớp riêng. Với lớp tham chiếu thông thường, danh tính runtime có thể hiểu qua cặp:

**(binary name, defining class loader)**

Ví dụ hai loader khác nhau cùng định nghĩa lớp có binary name `com.example.Plugin`:

```text
PluginLoader A                            PluginLoader B
      |                                         |
      | defineClass("com.example.Plugin", ...)  | defineClass("com.example.Plugin", ...)
      v                                         v
Class<?> A                                 Class<?> B

A.getName() == "com.example.Plugin"        B.getName() == "com.example.Plugin"

A.getName().equals(B.getName())  == true
A == B                            == false
A.isAssignableFrom(B)             == false
```

Các nhận định quan trọng:

- **Tên bằng nhau**: `Class.getName()` của hai lớp có thể trả về đúng cùng một chuỗi.
- **`Class` khác nhau**: `A == B` là `false`; chúng đại diện hai định nghĩa kiểu.
- **Không tương thích kiểu chỉ vì tên trùng**: đối tượng của `A` không tự trở thành đối tượng của `B`.
- **Bytecode không quyết định đồng nhất kiểu**: ngay cả cùng nội dung `.class`, một loader mới định nghĩa bản riêng thì danh tính kiểu vẫn khác.
- **Cùng loader định nghĩa + cùng binary name**: trong phạm vi một loader, định nghĩa lớp thành công chỉ có một lần cho mỗi binary name; cố định nghĩa trùng thường gây `LinkageError`.

Quy tắc này liên quan đến **lớp tham chiếu do class loader định nghĩa**. Kiểu nguyên thủy do JVM xác lập và không được nạp bằng `defineClass` như lớp thông thường.

## 2. Phân biệt defining loader và initiating loader

**Defining loader** là class loader trực tiếp **định nghĩa** lớp, điển hình qua `defineClass(...)` hoặc cơ chế định nghĩa tương ứng. `Class.getClassLoader()` trả về loader đã định nghĩa lớp (có thể là `null` với lớp do bootstrap loader định nghĩa).

**Initiating loader** là loader mà JVM đã dùng để **bắt đầu một yêu cầu tải lớp** và qua đó ghi nhận quan hệ initiating. Loader này có thể ủy quyền sang parent, nên **không nhất thiết** là loader thực sự định nghĩa class.

### Trường hợp A: hai child loader ủy quyền cho cùng parent

```text
ChildLoader 1 ----\
                   \ delegate
                    -> ParentLoader -> define "com.example.Plugin"
                   /                       |
ChildLoader 2 ----/                        v
                                        Cùng Class<?>
```

Dù có hai child loader object, cả hai đều nhận lại **cùng một `Class<?>`** do parent định nghĩa. Không thể kết luận có hai kiểu khác nhau chỉ vì có hai loader ở điểm bắt đầu lời gọi.

### Trường hợp B: mỗi child loader tự định nghĩa lớp

```text
ChildLoader 1 -> define "com.example.Plugin" -> Class A
ChildLoader 2 -> define "com.example.Plugin" -> Class B
                                               
Class A != Class B
```

Kết quả là hai kiểu runtime khác nhau. Hai lớp có thể cùng tên binary, cùng API, cùng bytes; việc xác định danh tính vẫn phụ thuộc vào loader **định nghĩa thực sự**.

**Lưu ý:** `ClassLoader.loadClass(...)` theo triển khai mặc định thường kiểm tra lớp đã tải, sau đó ủy quyền lên parent và cuối cùng mới dùng `findClass(...)` nếu chưa tìm thấy. Chỉ viết subclass của `ClassLoader` hoặc tạo hai instance class loader **chưa bảo đảm** sẽ có hai `Class` khác nhau. Muốn chủ động định nghĩa một bản riêng phải triển khai chiến lược nạp phù hợp.

## 3. Ví dụ Java 17+: cùng tên, khác defining loader, ép kiểu thất bại

Ví dụ chạy được dưới đây cố ý tạo **hai class loader định nghĩa riêng `DemoPlugin`** nhưng đều ủy quyền lên parent để dùng chung `PluginApi`. Nhờ đó ta quan sát cả vấn đề danh tính và một cách giao tiếp giữa các loader.

Tạo ba file trong **cùng thư mục**.

**File 1 — `PluginApi.java`**

```java
public interface PluginApi {
    String greet();
}
```

**File 2 — `DemoPlugin.java`**

```java
public class DemoPlugin implements PluginApi {
    @Override
    public String greet() {
        return "Xin chào từ plugin";
    }
}
```

**File 3 — `LoaderIdentityDemo.java`**

```java
import java.io.IOException;
import java.io.InputStream;

public class LoaderIdentityDemo {

    // Chỉ DemoPlugin được định nghĩa riêng trong mỗi loader.
    // Các kiểu khác, gồm PluginApi, vẫn được ủy quyền về parent.
    static final class IsolatedPluginLoader extends ClassLoader {
        private static final String TARGET = "DemoPlugin";

        IsolatedPluginLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!TARGET.equals(name)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    String resource = name.replace('.', '/') + ".class";

                    try (InputStream stream =
                                 getParent().getResourceAsStream(resource)) {
                        if (stream == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] bytes = stream.readAllBytes();
                        result = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }

                if (resolve) {
                    resolveClass(result);
                }
                return result;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader parent = LoaderIdentityDemo.class.getClassLoader();

        IsolatedPluginLoader loaderA = new IsolatedPluginLoader(parent);
        IsolatedPluginLoader loaderB = new IsolatedPluginLoader(parent);

        Class<?> classA = Class.forName("DemoPlugin", true, loaderA);
        Class<?> classB = Class.forName("DemoPlugin", true, loaderB);
        Object first = classA.getDeclaredConstructor().newInstance();

        System.out.println("Cùng tên: "
                + classA.getName().equals(classB.getName()));
        System.out.println("Cùng Class: " + (classA == classB));
        System.out.println("Đúng defining loader: "
                + (classA.getClassLoader() == loaderA
                && classB.getClassLoader() == loaderB));
        System.out.println("A gán được từ B: "
                + classA.isAssignableFrom(classB));
        System.out.println("Là DemoPlugin từ parent: "
                + DemoPlugin.class.isInstance(first));

        System.out.println("Là PluginApi chung: "
                + (first instanceof PluginApi));
        PluginApi api = (PluginApi) first;
        System.out.println("Gọi qua interface: " + api.greet());

        try {
            DemoPlugin wrong = (DemoPlugin) first;
            System.out.println(wrong.greet());
        } catch (ClassCastException e) {
            System.out.println("Ép về DemoPlugin: ClassCastException");
        }

        // Hai loader bình thường cùng ủy quyền cho parent.
        ClassLoader childA = new ClassLoader(parent) {};
        ClassLoader childB = new ClassLoader(parent) {};
        System.out.println("Ủy quyền parent, cùng Class: "
                + (childA.loadClass("DemoPlugin")
                == childB.loadClass("DemoPlugin")));
    }
}
```

Biên dịch và chạy từ thư mục chứa cả ba file, dùng JDK 17 trở lên:

```bash
javac PluginApi.java DemoPlugin.java LoaderIdentityDemo.java
java LoaderIdentityDemo
```

**Stdout mong đợi** (không in tên/địa chỉ loader phụ thuộc môi trường):

```text
Cùng tên: true
Cùng Class: false
Đúng defining loader: true
A gán được từ B: false
Là DemoPlugin từ parent: false
Là PluginApi chung: true
Gọi qua interface: Xin chào từ plugin
Ép về DemoPlugin: ClassCastException
Ủy quyền parent, cùng Class: true
```

### Vì sao ví dụ đưa ra kết quả này?

1. `Class.forName("DemoPlugin", true, loaderA)` yêu cầu tải `DemoPlugin` qua `loaderA`. Với đúng tên đó, phương thức `loadClass` được override để dùng `defineClass(...)`; tương tự với `loaderB`. Vì vậy `classA` và `classB` có cùng binary name nhưng hai defining loader khác nhau.
2. `findLoadedClass(name)` tránh định nghĩa lại lớp cùng tên trong **mỗi** loader khi có nhiều lời gọi. Khóa `getClassLoadingLock(name)` giữ việc tra cứu/định nghĩa nhất quán khi có concurrency.
3. Đối với `PluginApi`, cả hai loader gọi `super.loadClass(...)` nên parent định nghĩa và cấp **cùng một kiểu interface**. Cả hai bản `DemoPlugin` đều triển khai đúng một `PluginApi` đó. Vì thế có thể ép instance của classA sang `PluginApi` và gọi `greet()` an toàn.
4. `DemoPlugin.class` trong mã chính tham chiếu bản do **parent** tải, khác với `classA`. Ép object của classA thành `DemoPlugin` từ parent gây `ClassCastException`, dù tên class giống hệt.
5. `childA` và `childB` ở cuối không định nghĩa riêng `DemoPlugin` mà dùng chiến lược ủy quyền mặc định. Cả hai tải thành công về **cùng `Class<?>`** của parent.

Đây là ví dụ đào tạo đơn giản: mã đọc bytes `DemoPlugin.class` từ resource của parent và cố ý thay đổi quy tắc ủy quyền cho **một** lớp. Một hệ thống plugin thực tế cần xử lý thêm package, dependency, nguồn JAR, tài nguyên, unload/lifecycle và chính sách bảo vệ.

## 4. Hệ quả trong Reflection và kiểm tra kiểu

### So sánh `Class<?>` đúng ý nghĩa

| Kiểm tra | Điều gì được so sánh? | Có chứng minh cùng kiểu runtime không? |
| --- | --- | --- |
| `a.getName().equals(b.getName())` | Chuỗi tên lớp | **Không** |
| `a == b` hoặc `a.equals(b)` | Danh tính `Class` | **Có** |
| `a.getClassLoader() == b.getClassLoader()` | Cùng defining loader | **Chưa đủ**; còn phải cùng binary name |
| `a.isAssignableFrom(b)` | Khả năng gán giá trị kiểu `b` cho biến kiểu `a` | Kiểm tra tương thích kiểu, không chỉ tên |
| `a.isInstance(object)` | Instance có tương thích với kiểu `a` không | Có, kiểm tra tương thích đối tượng |
| `a.cast(object)` | Thực hiện cast runtime theo `a` | Ném `ClassCastException` nếu không tương thích |

**`Class<?>` là đối tượng phản ánh định nghĩa kiểu cụ thể**, không phải một record chỉ chứa tên. Khi cần chứng minh cùng runtime class, ưu tiên so sánh trực tiếp đối tượng `Class`.

### Reflection có thể thất bại dù tên hiển thị giống nhau

Giả sử `Method` lấy từ `classA`, nhưng bạn cố gọi nó trên một instance của `classB`:

```java
// Minh họa thêm bên trong main của LoaderIdentityDemo;
// classA, classB đã được tạo như ví dụ trên.
Object second = classB.getDeclaredConstructor().newInstance();
var methodFromA = classA.getMethod("greet");

try {
    methodFromA.invoke(second);
} catch (IllegalArgumentException e) {
    System.out.println("Sai receiver runtime type");
}
```

Đây là **mảnh code minh họa bổ sung**, không phải file Java độc lập. `methodFromA` mô tả phương thức khai báo trên classA, nhưng `second` là đối tượng classB, không phải instance hợp lệ của classA; lời gọi phản chiếu bị từ chối với `IllegalArgumentException` về receiver.

Trong tình huống tương tự, một API lấy `Class<?>` từ loader này rồi dùng constructor/field/method hoặc cast với đối tượng của loader khác có thể lỗi bất ngờ. Không nên chỉ nhìn `getName()` mà cho rằng hai bên tương thích.

### `ClassCastException` “X cannot be cast to X” có nghĩa gì?

Thông báo lỗi runtime trên một số phiên bản Java có thể hiển thị một class không thể cast sang class **cùng tên**, và kèm thông tin loader/module để phân biệt. Lỗi không hề mâu thuẫn: hai tên giống nhau nhưng hai `Class` khác nhau. Không nên dựa vào **toàn bộ văn bản** thông báo vì format và cách đặt tên loader có thể thay đổi theo JDK/môi trường.

## 5. Diagnosing: xác định đâu là class thực sự

Khi gặp lỗi cast hoặc Reflection ở plugin, servlet container, application server, hot reload hay test environment, hãy kiểm tra cả **tên**, **đối tượng `Class`** và **defining loader** ở hai phía.

Đoạn helper sau có thể đưa vào code chẩn đoán; nó **không phải một chương trình độc lập**:

```java
static void describe(String label, Object value) {
    Class<?> type = value.getClass();
    ClassLoader loader = type.getClassLoader();

    System.out.println(label + " name=" + type.getName());
    System.out.println(label + " loader=" + loader);
    System.out.println(label + " module=" + type.getModule().getName());
}
```

Khi so sánh hai đối tượng:

```java
Class<?> left = leftObject.getClass();
Class<?> right = rightObject.getClass();

System.out.println("Same name: " + left.getName().equals(right.getName()));
System.out.println("Same Class: " + (left == right));
System.out.println("Same defining loader: "
        + (left.getClassLoader() == right.getClassLoader()));
```

**Ghi chú đọc log:**

- `getClassLoader() == null` thường gặp với lớp được **bootstrap loader** định nghĩa; đừng diễn giải `null` là lỗi.
- `getModule().getName()` có thể là `null` đối với unnamed module; module name **không thay thế** defining loader để xác định kiểu.
- `ClassLoader.toString()`, tên loader và mã nhận diện dạng hash chỉ giúp điều tra. Muốn khẳng định cùng loader trong chương trình, so sánh đối tượng loader bằng `==`.
- Đối chiếu classpath, thứ tự nạp và JAR thực tế khi nghi lớp trùng; **nguồn bytecode giống nhau không làm hai `Class` đồng nhất**.
- `Thread.currentThread().getContextClassLoader()` là **context loader của thread**, được nhiều framework dùng để tìm tài nguyên/lớp; nó không nhất thiết là defining loader của đối tượng đang kiểm tra. Hãy ưu tiên `object.getClass().getClassLoader()` khi hỏi “ai định nghĩa kiểu này?”.

Khi xem stack trace, chẩn đoán hai phía của phép ép kiểu hoặc phương thức nhận tham số; nếu một API trả đối tượng từ loader riêng, bên nhận phải kiểm tra contract chung thay vì tự ép về bản class của mình.

## 6. Vì sao cơ chế này có ích và vì sao dễ gây lỗi?

### Ích lợi: namespace và lifecycle riêng cho plugin

Một host có thể dùng loader riêng cho từng plugin để tránh va chạm phiên bản thư viện hoặc thay thế plugin theo lifecycle. Hai plugin có thể phụ thuộc vào class cùng binary name nhưng mỗi plugin nhìn thấy định nghĩa riêng. Điều này giúp **cô lập kiểu/dependency**, dù **không phải cơ chế sandbox bảo mật**.

Một tính năng hot reload có thể tạo loader mới, nạp phiên bản mới của một lớp, trong khi đối tượng của loader cũ vẫn tồn tại. Các đối tượng cũ và mới **không nhất thiết** tương thích chỉ vì tên class không đổi.

### Rủi ro: “split-brain type”

Nếu host định nghĩa `com.example.api.PluginApi` bằng một loader, nhưng plugin cũng tự định nghĩa **bản riêng của chính interface đó**, thì câu lệnh:

```java
PluginApi api = (PluginApi) pluginObject;
```

có thể thất bại. Mỗi `PluginApi` mang một defining loader khác nhau, do đó hai loại interface không phải một contract runtime duy nhất, dù source code/interface method giống nhau.

**Cách tổ chức phổ biến:** host và plugin phải thống nhất những kiểu **dùng làm contract qua ranh giới** (interface, DTO cần chia sẻ...). Thường để parent/common loader định nghĩa API chung; child loader dùng phần riêng của plugin và ủy quyền những API chung lên parent.

Điều này không có nghĩa “mọi plugin đều phải parent-first toàn bộ”. Chiến lược child-first có thể cần thiết khi plugin mang dependency riêng. Quy tắc cốt lõi là **định nghĩa rõ những kiểu nào phải được chia sẻ cùng một danh tính runtime**.

### Xung đột chữ ký phương thức và loader constraints

JVM còn áp đặt **loader constraints** để bảo đảm những kiểu xuất hiện trong chữ ký phương thức/field tương tác qua các loader được hiểu nhất quán trong các trường hợp nhất định. Khi các constraint bắt buộc đồng nhất kiểu nhưng hai loader đã định nghĩa lớp không tương thích, có thể xuất hiện `LinkageError`. Đây là vấn đề khác với `ClassCastException` tại câu lệnh cast, nhưng cùng xuất phát từ ranh giới class loader và định danh kiểu.

## 7. Những hiểu lầm phổ biến

| Hiểu lầm | Thực tế |
| --- | --- |
| “Tên class giống nhau tức là cùng kiểu.” | Danh tính lớp tham chiếu còn phụ thuộc defining loader. |
| “Hai loader instance luôn tạo hai kiểu khác nhau.” | Nếu cả hai ủy quyền về một defining loader, chúng nhận cùng một `Class`. |
| “Cùng JAR/bytecode thì cast được.” | Cùng bytecode vẫn có thể được định nghĩa hai lần bởi hai loader khác nhau. |
| “`getName()` đủ để kiểm tra kiểu.” | Chỉ dùng tên để hiển thị/tra cứu; dùng `Class`, `isInstance`, `isAssignableFrom` khi cần kiểm tra kiểu. |
| “`Class.forName(...)` lúc nào cũng dùng một loader.” | Có overload dùng loader rõ ràng; lựa chọn loader ảnh hưởng kết quả. |
| “Đổi TCCL sẽ đổi lớp của instance đang có.” | `Thread Context ClassLoader` không thay đổi defining loader của class đã được định nghĩa. |
| “ClassLoader là sandbox bảo mật.” | Cô lập namespace/dependency không tự tạo ranh giới bảo mật. |

## 8. Áp dụng khi thiết kế hệ thống

Khi chuẩn bị viết plugin framework hoặc API dùng Reflection, có thể đi theo quy trình:

1. **Xác định contract chung**: interface và những kiểu dữ liệu cần truyền từ plugin sang host và ngược lại.
2. **Đặt contract tại loader chia sẻ**: bảo đảm bên host và plugin cùng nhận một `Class<?>` cho mỗi kiểu của contract.
3. **Đặt implementation và dependency riêng tại loader phù hợp**: có thể là loader của từng plugin.
4. **Nạp plugin qua loader đã chọn** và kiểm tra `sharedApiClass.isAssignableFrom(implementationClass)` hoặc `sharedApiClass.isInstance(instance)` trước khi cast.
5. **Không lưu tên class làm bằng chứng tương thích**: dùng identity của `Class` và phép kiểm tra gán kiểu thực tế.
6. **Ghi thông tin hai loader khi lỗi**: chẩn đoán đầu nguồn và đầu nhận, nhất là khi `ClassCastException`/`LinkageError` xuất hiện.
7. **Quản lý vòng đời loader/instance**: tránh giữ reference, thread, static cache hoặc registration khiến loader/plugin cũ tiếp tục tồn tại ngoài dự tính khi hot reload.

Phần lớn code ứng dụng thông thường không cần tự tạo class loader. Hiểu quy tắc định danh lớp giúp đọc đúng metadata Reflection, lý giải lỗi ép kiểu “cùng tên”, và đưa ra quyết định có cơ sở khi gặp plugin/framework/container.

## 9. Ghi nhớ và tự kiểm tra

**Ghi nhớ:** Với lớp tham chiếu, **binary name + defining loader** mới quyết định danh tính kiểu runtime. Initiating loader có thể chỉ ủy quyền, nên số lượng loader bắt đầu tải lớp không nói lên số lượng định nghĩa. Hãy so sánh `Class<?>` trực tiếp, kiểm tra `isInstance`/`isAssignableFrom` và chia sẻ interface đúng loader khi trao đổi object qua ranh giới plugin.

Tự kiểm tra:

1. Hai object có `getClass().getName()` giống nhau nhưng cast thất bại. Bạn sẽ kiểm tra thêm những gì?
2. Hai child loader cùng gọi `loadClass("x.Plugin")` và trả về đúng một `Class<?>`. Ai có thể là defining loader?
3. Trong ví dụ chạy được, vì sao cast về `DemoPlugin` thất bại nhưng cast về `PluginApi` thành công?
4. Chỉ đổi thread context class loader có làm thay đổi `getClass().getClassLoader()` của object đã tạo không?
5. Nếu host/plugin cùng đưa một interface API vào loader riêng của mình, vấn đề gì có thể xảy ra?


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-names-identity-007">getPackage, getPackageName và getModule</a>

<details>
<summary>Xem chi tiết</summary>

# Class.getPackage(), Class.getPackageName() và Class.getModule() trong Java Reflection

## 1. Mục tiêu và bối cảnh

Khi đã có một đối tượng **Class<?>**, Reflection cho phép bạn hỏi kiểu đó thuộc package nào và module nào. Ba API thường bị nhầm lẫn là:

- **Class.getPackage()**: trả đối tượng **Package** để truy vấn metadata của package tại runtime.
- **Class.getPackageName()**: trả **String** là tên package, tiện để hiển thị, phân loại và ghi log.
- **Class.getModule()**: trả đối tượng **Module** của Java Platform Module System (JPMS), dùng khi cần biết kiểu thuộc named module hay unnamed module, hoặc cần xem quan hệ đọc/xuất/mở package.

Đây là **ba góc nhìn khác nhau về cùng một kiểu**, không phải ba cách gọi thay thế nhau. Package tổ chức namespace của class; module nhóm các package và diễn đạt phụ thuộc/ranh giới truy cập; đối tượng Class là điểm bắt đầu để truy vấn metadata.

Sau bài học, bạn có thể giải thích giá trị trả về, cách xử lý null, kết quả đặc biệt với array/primitive/void, phân biệt unnamed package và unnamed module, viết code Reflection tránh giả định sai và dự đoán output của ví dụ Java 17+.

## 2. Ba API và mối quan hệ Class → Package → Module

| API trên Class<?> | Kiểu trả về | Câu hỏi trả lời | Kết quả đáng chú ý |
| --- | --- | --- | --- |
| **getPackage()** | **java.lang.Package** (có thể null) | Package runtime của class này có metadata gì? | **null** với array, primitive, void |
| **getPackageName()** | **String** | Tên package đầy đủ là gì? | **""** với unnamed package; **"java.lang"** với primitive/void; array dùng package của element type |
| **getModule()** | **java.lang.Module** | Class này thuộc module runtime nào? | Array dùng module của element type; primitive/void thuộc **java.base**; class trên classpath thường thuộc unnamed module |

Ba phương thức đều là phương thức instance của **java.lang.Class**, không phải phương thức của một object bất kỳ:

~~~java
Class<?> type = String.class;
Package pkg = type.getPackage();
String packageName = type.getPackageName();
Module module = type.getModule();
~~~

Nếu có đối tượng hiện hữu, lấy Class token bằng **instance.getClass()**, với điều kiện **instance != null**. Class token biểu diễn **kiểu thực tế của object**, còn kiểu khai báo của biến không làm đổi package hay module của object đó.

Sơ đồ khái niệm:

~~~text
Class<?>  ----getPackage()----> Package metadata (hoặc null)
    |
    +-------getPackageName()--> Chuỗi tên package
    |
    +-------getModule()------> Module metadata

Ví dụ String.class:
  class              = java.lang.String
  package            = java.lang
  named module       = java.base
~~~

Một module có thể chứa nhiều package. Một package gồm các class/interface được tổ chức dưới cùng tên package; package không đồng nghĩa với module. Tên package có thể trùng một đoạn trong tên module nhưng Java không yêu cầu hai tên khớp nhau.

## 3. Class.getPackage(): khi nào cần đối tượng Package?

Chữ ký:

~~~java
public Package getPackage()
~~~

Với class thông thường, API trả về đối tượng **Package** đại diện package runtime của class. Khác với một chuỗi tên, Package có metadata và API riêng:

- **getName()**: tên package.
- **getSpecificationTitle()**, **getSpecificationVersion()**, **getSpecificationVendor()**: thông tin đặc tả nếu có.
- **getImplementationTitle()**, **getImplementationVersion()**, **getImplementationVendor()**: thông tin triển khai nếu có.
- **isSealed()** và **isSealed(URL)**: thông tin sealing của package.
- **getAnnotation(...)**, **isAnnotationPresent(...)**: truy vấn annotation trên package khi có metadata tương ứng, ví dụ từ **package-info.class** với annotation lưu ở runtime.

Nhiều trường thông tin phiên bản/vendor **có thể null**. Java không tự lấy phiên bản ứng dụng, phiên bản Maven/Gradle hay phiên bản module từ tên package. Không nên coi **Package.getImplementationVersion()** là nguồn luôn tồn tại cho phiên bản hệ thống.

Ví dụ an toàn khi đọc metadata:

~~~java
Package info = String.class.getPackage();

if (info != null) {
    System.out.println("Package: " + info.getName());
    String version = info.getImplementationVersion();
    System.out.println("Has implementation version: " + (version != null));
}
~~~

Dòng đầu ở trên in **Package: java.lang**. Dòng thứ hai phụ thuộc metadata và môi trường Java, vì vậy không nên ghi cứng rằng nhất định true hay false.

### 3.1. getPackage() có thể null

Theo API, **getPackage() trả null** khi Class token biểu diễn:

- Array: **String[].class**, **int[][].class**, ...
- Primitive: **int.class**, **boolean.class**, ...
- Void: **void.class**.

Vì vậy code sau có lỗi runtime:

~~~java
// String[].class.getPackage().getName(); // NullPointerException
~~~

Hãy kiểm tra null trước khi dùng Package, hoặc dùng **getPackageName()** nếu chỉ cần tên:

~~~java
Class<?> type = String[].class;
Package info = type.getPackage();

String name = info == null ? type.getPackageName() : info.getName();
System.out.println(name); // java.lang
~~~

**Lưu ý:** Giá trị null của Package đối với array/primitive/void là quy tắc của API, không có nghĩa array không có runtime module hoặc không xác định được tên package theo quy ước **getPackageName()**.

## 4. Class.getPackageName(): dùng khi chỉ cần tên package

Chữ ký:

~~~java
public String getPackageName()
~~~

Phương thức có từ **Java 9**, trả tên package đầy đủ, không kèm tên class. Đây thường là lựa chọn trực tiếp nhất cho báo cáo, phân loại class hoặc logging.

Ví dụ:

~~~java
System.out.println(String.class.getPackageName());           // java.lang
System.out.println(java.util.Map.Entry.class.getPackageName()); // java.util
System.out.println(String[].class.getPackageName());         // java.lang
System.out.println(int.class.getPackageName());              // java.lang
~~~

Khác biệt cần nhớ:

1. Class ở **named package** trả tên đầy đủ, ví dụ **java.util**.
2. Class ở **unnamed package** (source không khai báo package) trả chuỗi rỗng **""**, không phải null.
3. Nested/member class trả tên package của enclosing class. **Map.Entry** là nested interface nhưng vẫn thuộc **java.util**, không thuộc một package có tên **java.util.Map**.
4. Local/anonymous class lấy package từ class bao quanh theo định nghĩa API.
5. Array trả tên package của **element type**: **String[]**, **String[][]** đều cho **java.lang**.
6. Primitive và **void** trả **java.lang** theo quy định đặc biệt. Điều này không có nghĩa có source class tên **java.lang.int** hoặc **java.lang.void**.

### 4.1. Unnamed package khác với unnamed module

Ví dụ tệp **Demo.java** đặt trong thư mục gốc, không có khai báo **package**:

~~~java
public class Demo {
    public static void main(String[] args) {
        System.out.println("[" + Demo.class.getPackageName() + "]");
    }
}
~~~

~~~bash
javac Demo.java
java Demo
~~~

Stdout:

~~~text
[]
~~~

Dấu ngoặc vuông giúp thấy rõ chuỗi package rỗng.

**Unnamed package** là trường hợp class không có khai báo package. **Unnamed module** là module runtime dành cho class không thuộc named module (điển hình các class chạy trên **classpath**). Hai khái niệm độc lập: class có **package com.example** vẫn có thể thuộc **unnamed module** nếu được chạy trên classpath.

### 4.2. Không tự cắt chuỗi getName() để suy package

**Class.getName()** không đảm bảo định dạng "package.class" cho mọi loại Class token:

- Nested class thường có dấu **$** trong binary name.
- Array có định dạng kiểu **[Ljava.lang.String;** hoặc **[I**.
- Primitive trả tên keyword như **int**.
- Các loại class đặc biệt có quy tắc tên riêng.

Vì vậy, nếu mục tiêu chỉ là tên package, dùng **getPackageName()** thay cho việc tìm dấu chấm cuối cùng trong **getName()**. Điều này giảm lỗi với mảng và những trường hợp đặc biệt.

## 5. Class.getModule(): metadata của Java module

Chữ ký:

~~~java
public Module getModule()
~~~

Phương thức có từ **Java 9**, là điểm vào của Java Platform Module System (JPMS). **java.lang.Module** mô tả một module runtime, gồm hai nhóm:

- **Named module**: có tên module (ví dụ **java.base**, **java.sql**, hoặc tên tự định nghĩa trong **module-info.java**).
- **Unnamed module**: không có tên module, thường chứa class chạy trên classpath. Runtime gắn unnamed module với class loader tương ứng.

Với bất kỳ Class token hợp lệ nào, **getModule()** trả một Module, kể cả class ở unnamed module, array, primitive và void; kết quả **Module.getName()** có thể null nếu module không tên.

Ví dụ:

~~~java
Module javaBase = String.class.getModule();

System.out.println(javaBase.getName()); // java.base
System.out.println(javaBase.isNamed()); // true
~~~

Đối với class ứng dụng chạy trên classpath, **MyClass.class.getModule().isNamed()** thường là false và **getName()** trả null. Đừng gọi **getModule().getName().length()** mà không kiểm tra null.

### 5.1. Các API hữu ích trên Module

| API | Dùng để hiểu |
| --- | --- |
| **getName()** | Tên named module; null nếu unnamed module |
| **isNamed()** | Named hay unnamed module |
| **getDescriptor()** | Module descriptor; null với unnamed module |
| **getPackages()** | Tập tên package trong module |
| **canRead(Module other)** | Module này có quan hệ đọc module khác không |
| **isExported(String packageName)** | Package có được export vô điều kiện không |
| **isExported(String packageName, Module other)** | Package có được export cho module đích không |
| **isOpen(String packageName)** | Package có được open vô điều kiện không |
| **isOpen(String packageName, Module other)** | Package có được open cho module đích không |

**Không đồng nhất exports với opens**:

- **exports** chủ yếu cho phép các module đủ quan hệ đọc truy cập public type và public member từ package được xuất, theo kiểm tra của Java.
- **opens** cho phép deep reflection vào package theo cơ chế truy cập Reflection (vẫn có những điều kiện truy cập khác).
- Một package có thể được **qualified export/open** chỉ cho một số module; khi đó phép kiểm tra không có tham số module đích không phản ánh đầy đủ quyền của từng caller.
- Có được đối tượng **Module** và tên module không tự trao quyền đọc/ghi member private. Các thao tác như **setAccessible(true)** vẫn có thể không được phép.

**Module** ở đây là JPMS module tại runtime. Nó không phải khái niệm "module Gradle" hay "module Maven" mặc dù các hệ công cụ build có thể tạo ra artifact chứa named module.

### 5.2. Quy tắc đặc biệt với array, primitive, void

Theo đặc tả của **Class.getModule()**:

- Với array, trả Module của **element type**. **String[]** thuộc module **java.base**; một array của class ứng dụng thuộc module của chính element class đó.
- Với primitive hoặc **void**, trả **java.base**.
- Với class trong unnamed module, trả đối tượng Module không tên của class loader định nghĩa class ấy.

Đây là lý do **String[].class.getPackage() == null** và **String[].class.getModule()** vẫn là Module **java.base**: package metadata của array và module theo element type được API xử lý theo hai quy tắc khác nhau.

## 6. Ví dụ Java 17+ chạy được: so sánh cả ba API

Tạo tệp **ClassNamespaceDemo.java** trong thư mục không khai báo package.

~~~java
public class ClassNamespaceDemo {
    private static void inspect(String label, Class<?> type) {
        Package pkg = type.getPackage();
        Module module = type.getModule();

        String pkgObject = pkg == null ? "null" : pkg.getName();
        String moduleName = module.isNamed()
                ? module.getName()
                : "<unnamed>";

        System.out.println(label
                + " | getPackageName=" + type.getPackageName()
                + " | getPackage=" + pkgObject
                + " | getModule=" + moduleName);
    }

    public static void main(String[] args) {
        inspect("String", String.class);
        inspect("String[]", String[].class);
        inspect("int", int.class);
        inspect("void", void.class);

        System.out.println("Local package: ["
                + ClassNamespaceDemo.class.getPackageName() + "]");
        System.out.println("Local module named: "
                + ClassNamespaceDemo.class.getModule().isNamed());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên trên **classpath**, không khai báo **module-info.java**:

~~~bash
javac ClassNamespaceDemo.java
java ClassNamespaceDemo
~~~

Stdout:

~~~text
String | getPackageName=java.lang | getPackage=java.lang | getModule=java.base
String[] | getPackageName=java.lang | getPackage=null | getModule=java.base
int | getPackageName=java.lang | getPackage=null | getModule=java.base
void | getPackageName=java.lang | getPackage=null | getModule=java.base
Local package: []
Local module named: false
~~~

Giải thích:

1. **String.class** là class bình thường thuộc package **java.lang**, named module **java.base**.
2. **String[].class** là array: không có đối tượng Package từ **getPackage()**, nhưng tên package và module lấy từ element type String.
3. **int.class** và **void.class** có **getPackage() == null**, **getPackageName() == "java.lang"** và module **java.base** theo đặc tả.
4. **ClassNamespaceDemo** được đặt ở unnamed package nên tên package là chuỗi rỗng. Khi chạy lệnh **java ClassNamespaceDemo** qua classpath, nó thuộc unnamed module.
5. Ví dụ dùng **module.isNamed()** trước khi đọc tên để tránh suy luận sai rằng null là lỗi tìm module.

**Phạm vi kết quả:** Lệnh ở trên giả định tệp nằm trong unnamed package và được chạy bằng classpath như minh họa. Nếu bạn đưa cùng class vào một named module hoặc thay source khai báo package, phần thông tin của **ClassNamespaceDemo** sẽ đổi tương ứng.

## 7. Ví dụ named module: package name độc lập với module name

Để thấy rõ khác biệt, tạo một Java module nhỏ với cấu trúc:

~~~text
src/
└── com.example.namespace/
    ├── module-info.java
    └── demo/
        └── api/
            └── NamespaceApp.java
~~~

Nội dung tệp **src/com.example.namespace/module-info.java**:

~~~java
module com.example.namespace {
    exports demo.api;
}
~~~

Nội dung tệp **src/com.example.namespace/demo/api/NamespaceApp.java**:

~~~java
package demo.api;

public class NamespaceApp {
    public static void main(String[] args) {
        Class<?> type = NamespaceApp.class;
        Module module = type.getModule();

        System.out.println(type.getPackageName());
        System.out.println(type.getPackage().getName());
        System.out.println(module.getName());
        System.out.println(module.isNamed());
    }
}
~~~

Đứng ở thư mục có **src/**, dùng JDK 17+:

~~~bash
javac --module-source-path src -d out -m com.example.namespace
java --module-path out -m com.example.namespace/demo.api.NamespaceApp
~~~

Kết quả:

~~~text
demo.api
demo.api
com.example.namespace
true
~~~

Đối chiếu:

- **demo.api** là tên **package** của class NamespaceApp.
- **com.example.namespace** là tên **named module** chứa package đó.
- **Package.getName()** và **Class.getPackageName()** khớp trong trường hợp class bình thường này.
- **Module.getName()** khác với tên package và có nhiệm vụ khác.

Hãy thử thay **exports demo.api;** bằng một chỉ thị module hợp lệ khác phù hợp với mục tiêu học; nhớ rằng quyền truy cập giữa module còn phụ thuộc **requires**, **exports**, **opens** và caller. Ví dụ hiện tại chỉ dùng truy cập public và tự quan sát metadata trong cùng module.

## 8. Bảng các trường hợp dễ gây nhầm

| Loại Class token | getPackage() | getPackageName() | getModule() |
| --- | --- | --- | --- |
| **String.class** | Package có tên **java.lang** | **java.lang** | named module **java.base** |
| **java.util.List.class** | Package có tên **java.util** | **java.util** | named module **java.base** |
| **String[].class** | **null** | **java.lang** | module của String: **java.base** |
| **int.class** | **null** | **java.lang** | **java.base** |
| **void.class** | **null** | **java.lang** | **java.base** |
| **Class ở unnamed package** | Đọc Package metadata một cách null-safe | **""** | Phụ thuộc module chứa class; trên classpath thường unnamed |
| **Class package demo.api trên classpath** | Package có tên **demo.api** | **demo.api** | unnamed module của class loader |
| **Class package demo.api trong named module com.example.namespace** | Package có tên **demo.api** | **demo.api** | named module **com.example.namespace** |

Các trường hợp đầu là kiến thức API độc lập với tên project; hai trường hợp cuối cần xét **cách class được triển khai và nạp**, không suy ra JPMS module chỉ từ chuỗi package.

### getPackageName() có phải là duy nhất để nhận diện lớp?

Không. Hai class có cùng **package name** (thậm chí cùng binary class name) nhưng do class loader khác nhau định nghĩa không nhất thiết là cùng kiểu runtime. Một **String** đơn lẻ không mô tả đầy đủ identity của Class token. Khi thiết kế registry Reflection hoặc plugin, cần cân nhắc cả **Class object**, class loader và module tương ứng; đừng dùng package name như khóa duy nhất toàn hệ thống.

## 9. Dùng trong ứng dụng và framework như thế nào?

### 9.1. Ghi log để xác định nguồn class

~~~java
static String identify(Class<?> type) {
    Module module = type.getModule();
    String moduleName = module.isNamed() ? module.getName() : "<unnamed>";
    return type.getName()
            + " | package=" + type.getPackageName()
            + " | module=" + moduleName;
}
~~~

Đây là đoạn **method minh họa** (không phải chương trình độc lập). Nó giúp chẩn đoán vì sao một class đến từ named module hay classpath, nhất là khi ứng dụng pha trộn jar truyền thống và JPMS.

### 9.2. Truy vấn package annotation

~~~java
Package pkg = targetClass.getPackage();
if (pkg != null && pkg.isAnnotationPresent(MyMarker.class)) {
    // Package này có MyMarker với retention phù hợp ở runtime.
}
~~~

Đoạn trên là **mã minh họa**, giả định **targetClass** và annotation **MyMarker** đã được khai báo. Annotation cần **@Retention(RetentionPolicy.RUNTIME)** nếu muốn thấy khi phản chiếu; package annotation thường được khai báo trong **package-info.java**. Cần dùng **Package** thay vì chỉ chuỗi tên vì **String** không mang annotation.

### 9.3. Kiểm tra truy cập package qua module

~~~java
Module owner = targetClass.getModule();
Module caller = SomeCaller.class.getModule();
String packageName = targetClass.getPackageName();

boolean exportedToCaller = owner.isExported(packageName, caller);
boolean openToCaller = owner.isOpen(packageName, caller);
~~~

Đây là **mã minh họa** (giả định **targetClass** và **SomeCaller** đã được cung cấp). Hai boolean phản ánh trạng thái **exports**/**opens** theo module đích, giúp phân tích một phần điều kiện truy cập. Chúng không tự thực hiện lời gọi Reflection và cũng không thay thế toàn bộ luật truy cập Java. Nếu đang xử lý array hoặc primitive, hãy xác định rõ bạn muốn truy vấn package/module của **element type** hay metadata theo quy tắc riêng của Class API, tránh dùng nhầm giá trị suy ra.

### 9.4. Chọn API theo mục đích

- **Chỉ cần chuỗi namespace trong log/cấu hình** → **getPackageName()**.
- **Cần phiên bản, sealing hoặc annotation của runtime package** → **getPackage()** và kiểm tra null.
- **Cần named/unnamed module, exports, opens, reads** → **getModule()**, tiếp tục qua **Module**.
- **Cần tên đầy đủ class** → **getName()** hoặc API tên phù hợp, không thay thế bằng package name.
- **Cần biết đối tượng hiện tại có class gì** → **obj.getClass()**, rồi mới truy vấn ba API trên.

## 10. Lỗi phổ biến và cách tránh

**Lỗi 1: coi ba API tương đương.** Package object, package name và Module object có kiểu trả về cũng như ý nghĩa khác nhau. Ví dụ **String[].class.getPackage()** null nhưng hai API kia vẫn trả thông tin có ích.

**Lỗi 2: gọi thẳng getPackage().getName() cho mọi Class token.** Có thể ném **NullPointerException** trên array/primitive/void. Dùng **getPackageName()** nếu chỉ cần tên hoặc xử lý nhánh null.

**Lỗi 3: suy ra tên module từ tên package.** **java.lang** thuộc **java.base**; class **demo.api** có thể ở unnamed module trên classpath hoặc named module khác. Chỉ **getModule()** cho biết module runtime thực tế.

**Lỗi 4: suy ra unnamed package và unnamed module là một.** Một class khai báo **package demo.api** vẫn có thể ở unnamed module nếu triển khai qua classpath.

**Lỗi 5: dùng Module.getName() mà không xét unnamed module.** **getModule()** vẫn trả Module, nhưng **getName()** trả null khi unnamed. Kiểm tra **isNamed()** trước khi xử lý tên.

**Lỗi 6: mặc định Package luôn chứa version hoặc vendor.** Metadata nhiều khi không có và các getter trả null. Nếu ứng dụng yêu cầu version, cần có nguồn dữ liệu được khai báo rõ ràng trong quá trình đóng gói.

**Lỗi 7: cho rằng có module nghĩa là Reflection được mở khóa.** Module có thể export package cho public access nhưng không open package cho deep reflection. Cần hiểu JPMS access rules và kiểm tra điều kiện của caller.

**Lỗi 8: tách package bằng substring từ getName().** Dễ sai với array, primitive, nested class. Dùng API chuyên dụng **getPackageName()**.

**Lỗi 9: bỏ qua runtime class loader.** Cùng tên class/package không đảm bảo Class token giống nhau; điều này đặc biệt quan trọng khi viết plugin container hoặc cơ chế nạp lớp động.

## 11. Bài tập tự kiểm tra

**Câu 1.** Đúng hay sai: **String[].class.getPackage().getName()** trả **java.lang**?

**Câu 2.** Với **String[].class**, vì sao **getPackage()** trả null nhưng **getModule().getName()** lại là **java.base**?

**Câu 3.** Class **demo.api.Service** được nạp từ classpath, không có **module-info.java**. Có thể chắc chắn module runtime của nó tên **demo.api** không?

**Câu 4.** Khi chỉ cần xuất tên package của một **Class<?>** bất kỳ (kể cả array/primitive), nên chọn API nào?

**Câu 5.** Nếu gọi **someClass.getModule().getName()** và nhận null thì class có "không thuộc module nào" hay không?

**Câu 6.** Khi cần phát hiện package annotation ở runtime, nên dùng tên package String hay Package metadata? Điều kiện về annotation là gì?

**Câu 7.** Named module **com.example.namespace** có class trong package **demo.api**. Dự đoán **getPackageName()** và **getModule().getName()** của class đó.

### Đáp án giải thích

1. **Sai.** **getPackage()** của array trả null; chuỗi lời gọi sẽ ném NullPointerException. **getPackageName()** mới trả **java.lang**.
2. Vì **getPackage()** áp dụng quy tắc array => null, còn **getModule()** dùng module của **element type String** là **java.base**.
3. **Không.** Nếu class không nằm trong named JPMS module thì nó thuộc **unnamed module**, thường **isNamed() == false** và **getName() == null**.
4. **getPackageName()**. Đây là API trả String và có xử lý đặc biệt cho các loại Class token đó.
5. **Vẫn có Module object**; chỉ là unnamed module có tên null.
6. Dùng **Package**; annotation cần có metadata tương ứng (thường trong **package-info.class**) và **RUNTIME retention** để truy vấn runtime.
7. **demo.api** và **com.example.namespace**: package name khác module name.

## 12. Ghi nhớ

**getPackage()** trả runtime Package metadata, có thể null; **getPackageName()** trả chuỗi tên package; **getModule()** trả JPMS Module của Class token. Khi phân tích lỗi Reflection, xác định rõ mục tiêu là package **metadata**, package **name** hay module **membership/access**, đồng thời xét các trường hợp array/primitive/void, unnamed module và class loader.

### Tài liệu chuẩn

- Java SE 17 API — **Class**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java SE 17 API — **Package**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Package.html
- Java SE 17 API — **Module**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html
- Java Language Specification — packages and modules: https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-names-identity-008">Nhận diện isPrimitive, isArray, isInterface và isAnnotation</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện `isPrimitive()`, `isArray()`, `isInterface()` và `isAnnotation()` trong Java Reflection

Khi xử lý một kiểu dữ liệu chưa biết trước lúc biên dịch, Java cung cấp đối tượng `Class<?>` để mô tả kiểu đó ở runtime. Bốn phương thức `Class.isPrimitive()`, `Class.isArray()`, `Class.isInterface()` và `Class.isAnnotation()` giúp nhận diện **loại cấu trúc của chính kiểu** đang xét. Chúng đều trả về `boolean`, không nhận tham số và không cần tạo instance của kiểu.

Đây là các câu hỏi về **metadata kiểu**, khác với câu hỏi “đối tượng có thuộc kiểu này không?” (dùng `isInstance(...)` hoặc `instanceof`), cũng khác với câu hỏi “lớp này có được gắn annotation X không?” (dùng `isAnnotationPresent(...)`).

## 1. Lấy đối tượng `Class<?>` để kiểm tra

Có thể lấy đối tượng mô tả kiểu bằng một trong các cách sau:

```java
Class<?> primitiveType = int.class;
Class<?> arrayType = String[][].class;
Class<?> interfaceType = Runnable.class;
Class<?> runtimeType = "hello".getClass();
Class<?> loadedType = Class.forName("java.lang.String");
```

Đây là **đoạn minh họa** để đặt trong phương thức Java; nó không phải chương trình độc lập. `SomeType.class` hoạt động khi mã nguồn đã biết kiểu, `obj.getClass()` lấy kiểu runtime của một đối tượng khác `null`, còn `Class.forName(...)` tra cứu lớp theo tên nhị phân phù hợp với class loader và có thể phát sinh `ClassNotFoundException`.

Lưu ý: `Class.forName("int")` không tra ra primitive `int` như `int.class`; các dạng tên class và descriptor của mảng có quy tắc riêng. Để xử lý primitive khi cần lựa chọn từ tên, hãy tạo bảng ánh xạ tường minh, chẳng hạn `"int" → int.class`.

## 2. Ý nghĩa chính xác của từng phương thức

### `isPrimitive()`: kiểu nguyên thủy hoặc `void`

Trả về `true` với các đối tượng `Class` đại diện cho tám kiểu nguyên thủy `boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, `double` **và `void.class`**. `void` không phải kiểu dữ liệu có giá trị thông thường, nhưng `Class.isPrimitive()` vẫn trả về `true` cho `void.class` theo đặc tả API.

```java
int.class.isPrimitive();      // true
void.class.isPrimitive();     // true
Integer.class.isPrimitive();  // false
String.class.isPrimitive();   // false
int[].class.isPrimitive();    // false
```

`Integer` là **wrapper class**, không phải primitive `int`. Một mảng chứa primitive cũng là **kiểu mảng tham chiếu**, vì vậy `int[].class.isPrimitive()` là `false`; muốn kiểm tra **phần tử cơ sở** có nguyên thủy không, cần đi qua `getComponentType()`.

Ứng dụng: bộ chuyển đổi dữ liệu, mapper và thư viện reflection dùng thông tin primitive để chọn quy tắc chuyển kiểu, xử lý giá trị mặc định hoặc yêu cầu một đối tượng tham chiếu. Với `void.class`, đừng xử lý như kiểu primitive có thể chứa giá trị.

### `isArray()`: chính kiểu đang xét là mảng

Trả về `true` nếu `Class` mô tả một kiểu mảng, kể cả mảng một chiều/nhiều chiều, mảng primitive, mảng object và mảng annotation:

```java
int[].class.isArray();          // true
String[][].class.isArray();     // true
Runnable[].class.isArray();     // true
String.class.isArray();         // false
```

`getComponentType()` trả về kiểu thành phần trực tiếp của mảng; với kiểu không phải mảng, kết quả là `null`:

```java
int[].class.getComponentType() == int.class;               // true
String[][].class.getComponentType() == String[].class;      // true
String[].class.getComponentType() == String.class;          // true
```

Với mảng nhiều chiều, `getComponentType()` giảm **một** cấp chiều mỗi lần; lặp cho tới khi `isArray()` là `false` để lấy kiểu phần tử tận cùng.

Không dùng `isArray()` để hỏi một class có **field kiểu mảng** hay method trả về mảng. Chỉ truyền `field.getType()` hoặc `method.getReturnType()` rồi kiểm tra nếu đó mới là thông tin cần biết.

### `isInterface()`: interface, bao gồm annotation interface

Trả về `true` nếu đối tượng `Class` đại diện cho một interface. Điều quan trọng: kiểu khai báo bằng `@interface` **cũng là một interface đặc biệt**, vì vậy `MyAnnotation.class.isInterface()` và `MyAnnotation.class.isAnnotation()` có thể **đồng thời** là `true`.

```java
Runnable.class.isInterface();             // true
java.lang.annotation.Annotation.class
        .isInterface();                   // true
String.class.isInterface();               // false
Runnable[].class.isInterface();           // false
```

Một lớp `implements Runnable` vẫn **là class**, nên `MyWorker.class.isInterface()` là `false`. Muốn kiểm tra lớp **có triển khai một interface** hay không, dùng `Runnable.class.isAssignableFrom(MyWorker.class)` hoặc duyệt metadata phù hợp, thay vì gọi `isInterface()` trên class implementation.

Ứng dụng: framework phân biệt hợp đồng giao tiếp với implementation, xác thực kiểu cấu hình động, hoặc quyết định cách khám phá method. Vì annotation interface là tập con của interface, thứ tự phân nhánh cần được thiết kế rõ ràng.

### `isAnnotation()`: chính kiểu đó là annotation type

Trả về `true` nếu `Class` mô tả một kiểu annotation khai báo bằng `@interface`. Phương thức này **không** kiểm tra lớp đang xét có **được gắn annotation** hay không.

Giả sử có:

```java
@interface Tag {}
@Tag class Book {}
```

Khi đó:

```java
Tag.class.isAnnotation();   // true: Tag là annotation type
Book.class.isAnnotation();  // false: Book vẫn là class
Tag.class.isInterface();    // true: annotation type cũng là interface
Tag[].class.isAnnotation(); // false: đây là kiểu mảng
```

Muốn kiểm tra `Book` được gắn `@Tag` ở runtime, dùng `Book.class.isAnnotationPresent(Tag.class)` và bảo đảm annotation được khai báo retention phù hợp (thường `@Retention(RetentionPolicy.RUNTIME)`). `isAnnotation()` chỉ xét **bản chất kiểu `Tag`**, nên không phụ thuộc vào việc một class cụ thể có sử dụng `@Tag` hay không.

## 3. Bảng so sánh và những giao nhau quan trọng

| Kiểu đem kiểm tra | `isPrimitive()` | `isArray()` | `isInterface()` | `isAnnotation()` |
| --- | --- | --- | --- | --- |
| `int.class` | `true` | `false` | `false` | `false` |
| `void.class` | `true` | `false` | `false` | `false` |
| `Integer.class` | `false` | `false` | `false` | `false` |
| `int[].class` | `false` | `true` | `false` | `false` |
| `String[][].class` | `false` | `true` | `false` | `false` |
| `Runnable.class` | `false` | `false` | `true` | `false` |
| `Tag.class` (khai báo `@interface`) | `false` | `false` | `true` | `true` |
| `Tag[].class` | `false` | `true` | `false` | `false` |
| `Book.class` (class được gắn `@Tag`) | `false` | `false` | `false` | `false` |

Hai nhận xét cần nhớ:

1. **Bốn điều kiện không tạo thành một phép chia thành bốn nhóm loại trừ tuyệt đối:** annotation type đồng thời thỏa `isInterface()` và `isAnnotation()`. Không nên giả định chỉ một cờ được `true`.
2. **Cả bốn đều có thể là `false`:** đó là trạng thái bình thường của nhiều class thông thường, enum, record… Nếu cần nhận diện thêm enum/record, xem `isEnum()`, `isRecord()` (có trên các JDK phù hợp) và những API metadata khác. Không kết luận `false` nghĩa là “kiểu không hợp lệ”.

Một mảng của interface/annotation vẫn là **kiểu mảng**; `Runnable[].class.isInterface()` và `Tag[].class.isAnnotation()` đều `false` vì câu hỏi đang hỏi bản thân kiểu mảng, không hỏi phần tử.

## 4. Chương trình Java 17+ có thể biên dịch và chạy

Tạo đúng tệp `TypeKindDemo.java`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Marker {}

interface Worker {}

@Marker
class Job implements Worker {}

public class TypeKindDemo {
    private static void inspect(String label, Class<?> type) {
        System.out.println(
                label
                + " => primitive=" + type.isPrimitive()
                + ", array=" + type.isArray()
                + ", interface=" + type.isInterface()
                + ", annotation=" + type.isAnnotation()
        );
    }

    private static int arrayDimensions(Class<?> type) {
        int dimensions = 0;
        while (type.isArray()) {
            dimensions++;
            type = type.getComponentType();
        }
        return dimensions;
    }

    public static void main(String[] args) {
        inspect("int", int.class);
        inspect("void", void.class);
        inspect("Integer", Integer.class);
        inspect("int[]", int[].class);
        inspect("String[][]", String[][].class);
        inspect("Worker", Worker.class);
        inspect("Marker", Marker.class);
        inspect("Marker[]", Marker[].class);
        inspect("Job", Job.class);

        System.out.println(
                "Job annotated with @Marker => "
                + Job.class.isAnnotationPresent(Marker.class)
        );
        System.out.println(
                "String[][] dimensions => "
                + arrayDimensions(String[][].class)
        );

        Class<?> leafType = String[][].class;
        while (leafType.isArray()) {
            leafType = leafType.getComponentType();
        }
        System.out.println(
                "String[][] leaf type => " + leafType.getName()
        );
    }
}
```

Biên dịch và chạy từ thư mục chứa file:

```bash
javac TypeKindDemo.java
java TypeKindDemo
```

Stdout chính xác:

```text
int => primitive=true, array=false, interface=false, annotation=false
void => primitive=true, array=false, interface=false, annotation=false
Integer => primitive=false, array=false, interface=false, annotation=false
int[] => primitive=false, array=true, interface=false, annotation=false
String[][] => primitive=false, array=true, interface=false, annotation=false
Worker => primitive=false, array=false, interface=true, annotation=false
Marker => primitive=false, array=false, interface=true, annotation=true
Marker[] => primitive=false, array=true, interface=false, annotation=false
Job => primitive=false, array=false, interface=false, annotation=false
Job annotated with @Marker => true
String[][] dimensions => 2
String[][] leaf type => java.lang.String
```

**Giải thích kết quả:**

- `Marker` là annotation interface. Vì thế cả `isInterface()` và `isAnnotation()` đều `true`.
- `Job` có `@Marker`, nhưng bản thân `Job` vẫn là class thường. `Job.class.isAnnotation()` là `false`, còn `isAnnotationPresent(Marker.class)` là `true` nhờ retention runtime.
- `Marker[]` là một **kiểu mảng**, nên ba cờ primitive/interface/annotation đều `false` dù các phần tử có kiểu annotation.
- `int.class`, `void.class` là primitive theo kết quả `Class.isPrimitive()`; `Integer.class` không phải.
- `String[][].class` có hai chiều; việc gọi lặp `getComponentType()` lần lượt thu được `String[].class` rồi `String.class`.

## 5. Ứng dụng trong code reflection và cách tổ chức điều kiện

Một công cụ phân tích cấu trúc kiểu có thể phân loại để quyết định cách xử lý metadata:

```java
static String kindOf(Class<?> type) {
    if (type.isAnnotation()) {
        return "annotation";
    }
    if (type.isInterface()) {
        return "interface";
    }
    if (type.isArray()) {
        return "array";
    }
    if (type.isPrimitive()) {
        return "primitive";
    }
    return "other";
}
```

Đây là **method minh họa**, được đặt bên trong một class đang dùng; chưa phải chương trình độc lập. Đặt `isAnnotation()` **trước** `isInterface()` nếu mục tiêu là xác định **một nhãn riêng** cho annotation, vì annotation interface đáp ứng cả hai điều kiện. Tùy mục đích, có thể xét mảng hoặc primitive trước; chỉ cặp annotation/interface đòi hỏi lưu ý về tính chồng lấn.

Tuy nhiên, không phải hệ thống nào cũng cần ép về một nhãn duy nhất. Nếu API cần phân tích đầy đủ khả năng của kiểu, hãy giữ riêng cả bốn cờ vì `annotation` đồng thời là `interface`.

Ví dụ luồng kiểm tra kiểu từ `Field` của một class:

```java
// "field" là Field đã lấy được từ một class khác.
Class<?> fieldType = field.getType();
if (fieldType.isArray()) {
    Class<?> component = fieldType.getComponentType();
    // Xử lý kiểu thành phần trực tiếp của field mảng.
} else if (fieldType.isPrimitive()) {
    // Xử lý field nguyên thủy.
}
```

Đây là **đoạn minh họa trong ngữ cảnh reflection**, không phải ứng dụng Java hoàn chỉnh. Để xử lý mảng nhiều chiều, lặp `getComponentType()`; nếu phần tử là primitive, `component.isPrimitive()` chỉ `true` khi đã xuống tới phần tử primitive.

## 6. Phân biệt với những API thường bị nhầm

| Nhu cầu | API phù hợp | Vì sao khác |
| --- | --- | --- |
| Kiểu `Class<?>` có đại diện primitive không? | `type.isPrimitive()` | Xét bản chất của kiểu, không xét giá trị |
| Kiểu có phải mảng không? | `type.isArray()` | Không cho biết kiểu phần tử nếu chưa gọi `getComponentType()` |
| Kiểu có phải một interface không? | `type.isInterface()` | Không xác định một **class** có implement interface nào |
| Kiểu có phải annotation type không? | `type.isAnnotation()` | Không kiểm tra một đối tượng/class được gắn annotation gì |
| Class/element có annotation `X` ở runtime không? | `element.isAnnotationPresent(X.class)` | Kiểm tra sự hiện diện của annotation có thể truy vấn tại runtime |
| Một class có triển khai/thuộc kiểu khác không? | `SuperType.class.isAssignableFrom(SubType.class)` | Kiểm tra quan hệ gán kiểu; gồm cả interface và class |
| Đối tượng có là instance của kiểu không? | `type.isInstance(object)` | Kiểm tra một object; khác bản chất của `type` |
| Tìm kiểu thành phần mảng | `arrayType.getComponentType()` | Trả về một `Class<?>` hoặc `null` nếu không phải mảng |

Ví dụ quan hệ kiểu:

```java
Worker.class.isInterface();             // true
Job.class.isInterface();                // false
Worker.class.isAssignableFrom(Job.class); // true
Worker.class.isInstance(new Job());     // true
```

Với `isInstance(...)`, đối tượng `null` luôn cho `false`. Với kiểu primitive, `int.class.isInstance(1)` cũng là `false`: biểu thức `1` phải boxing thành `Integer` khi truyền làm `Object`, còn primitive `Class` không đại diện lớp instance tham chiếu.

## 7. Lỗi phổ biến và cách tránh

1. **Nhầm wrapper với primitive.** `Integer.class.isPrimitive()` trả về `false`. Kiểm tra cả `int.class` và `Integer.class` bằng test nếu library của bạn có quy tắc riêng cho boxing.
2. **Nhầm mảng primitive với primitive.** `int[].class.isArray()` là `true` còn `isPrimitive()` là `false`. Muốn biết loại phần tử, gọi `getComponentType()`.
3. **Nhầm annotation type với class được gắn annotation.** `Marker.class.isAnnotation()` là `true`; `Job.class.isAnnotation()` là `false` dù `Job` có `@Marker`. Dùng `isAnnotationPresent(...)` cho câu hỏi gắn annotation.
4. **Kiểm tra interface trước annotation rồi dừng.** `Marker.class.isInterface()` là `true` và làm nhánh annotation phía sau không chạy. Sắp điều kiện phù hợp với mục đích hoặc không ép các cờ vào một loại duy nhất.
5. **Nhầm triển khai interface với chính interface.** Một class triển khai `Worker` không khiến `Job.class.isInterface()` thành `true`. Kiểm tra khả năng gán bằng `isAssignableFrom(...)` khi cần.
6. **Chỉ xét mảng một chiều.** `String[][].class.getComponentType()` là `String[].class`. Lặp qua nhiều mức nếu cần tới kiểu phần tử cuối.
7. **Cho rằng mọi kiểu phải thuộc một trong bốn nhóm.** `String.class`, `Integer.class` hay nhiều kiểu domain đều có cả bốn cờ `false`. Có thể xem thêm `isEnum()`, `isRecord()` hoặc thông tin khác tùy mục đích.
8. **Dựa vào tên chuỗi thay cho metadata.** Tên kiểu mảng ở `getName()` như `[I` hoặc `[[Ljava.lang.String;` dễ gây nhầm. Ưu tiên `isArray()` và `getComponentType()`; dùng `Class<?>` đã lấy đúng từ API thay vì tự phân tích chuỗi tên.

## 8. Ghi nhớ và tự kiểm tra

- `void.class.isPrimitive()` → **`true`**; `Integer.class.isPrimitive()` → **`false`**.
- `int[].class.isArray()` → **`true`**; `int[].class.getComponentType()` → **`int.class`**.
- `String[][].class.getComponentType()` → **`String[].class`**, không đi thẳng tới `String.class`.
- Với `@interface Marker`, `Marker.class.isInterface()` **và** `Marker.class.isAnnotation()` cùng là **`true`**.
- Với `@Marker class Job`, `Job.class.isAnnotation()` → **`false`**; `Job.class.isAnnotationPresent(Marker.class)` chỉ quan sát được annotation nếu retention/runtime metadata phù hợp.
- Mảng annotation (`Marker[].class`) là **mảng**, không phải annotation interface.
- Bốn predicate mô tả **bản thân kiểu**, không kiểm tra mặc định quan hệ kế thừa, instance hoặc annotation hiện diện.

Khi viết công cụ reflection, hãy xác định chính xác **đối tượng `Class<?>` đang mô tả kiểu nào**: kiểu field, kiểu trả về, component của mảng hay kiểu class chính. Sau đó chọn predicate tương ứng, tránh suy luận từ tên hoặc từ kiểu của đối tượng khác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-names-identity-009">Class.isInstance khác instanceof với kiểu biết trước</a>

<details>
<summary>Xem chi tiết</summary>

# `Class.isInstance()` khác `instanceof` với kiểu biết trước

Trong Java, cả `instanceof` và `Class.isInstance(Object)` đều có thể trả lời câu hỏi: **đối tượng tại thời điểm chạy có tương thích với một kiểu tham chiếu hay không?** Điểm khác biệt quyết định là **kiểu cần kiểm tra được biểu diễn ở đâu**:

- `value instanceof SomeType`: `SomeType` được viết trực tiếp trong mã nguồn và được trình biên dịch biết khi biên dịch.
- `someClass.isInstance(value)`: `someClass` là một đối tượng `Class<?>` (hoặc `Class<T>`), vì vậy kiểu đích có thể được chọn, truyền vào hoặc nạp **trong lúc chương trình chạy**.

Đây là một khác biệt về cách biểu diễn kiểu và cách viết chương trình; khi cùng kiểm tra một kiểu tham chiếu cụ thể, hai cách tuân theo cùng quan hệ tương thích kiểu lúc chạy.

## 1. Kiểm tra kiểu biết trước bằng `instanceof`

Khi kiểu đích đã rõ trong mã, `instanceof` thường dễ đọc nhất:

```java
Object value = "Java";

boolean textLike = value instanceof CharSequence; // true
boolean numeric = value instanceof Number;        // false
```

`instanceof` kiểm tra kiểu thực tế của đối tượng, bao gồm quan hệ kế thừa và triển khai interface. Vì `String` triển khai `CharSequence`, phép kiểm tra đầu trả về `true`.

Trong Java 17, có thể dùng **pattern matching for `instanceof`** để vừa kiểm tra vừa có một biến đã được thu hẹp kiểu:

```java
Object input = "reflection";

if (input instanceof String text) {
    System.out.println(text.toUpperCase()); // REFLECTION
}
```

Biến `text` chỉ được sử dụng tại những vị trí mà trình biên dịch biết phép kiểm tra đã thành công (phạm vi phụ thuộc luồng điều khiển). Cách này tránh ép kiểu thủ công khi kiểu đích đã có sẵn trong mã nguồn.

Không thể thay tên kiểu ở vế phải `instanceof` bằng một biến chứa `Class<?>`:

```java
Class<?> expectedType = String.class;

// KHÔNG biên dịch: expectedType là một biến, không phải tên kiểu.
// boolean matched = input instanceof expectedType;
```

## 2. Kiểm tra kiểu được chọn lúc chạy bằng `Class.isInstance()`

Chữ ký API là:

```java
public native boolean isInstance(Object obj);
```

Phương thức được gọi trên **đối tượng `Class` đại diện cho kiểu đích**, còn đối số là đối tượng cần kiểm tra:

```java
Object value = "Java";
Class<?> expectedType = CharSequence.class;

boolean matched = expectedType.isInstance(value); // true
```

`expectedType` có thể đến từ tham số phương thức, một registry hoặc `Class.forName(...)`. Nhờ đó, không phải viết một chuỗi `if`/`else` chỉ để lựa chọn giữa các kiểu đã có tên trong cấu hình.

Ví dụ kiểu đích chỉ được xác định lúc chạy:

```java
String configuredTypeName = "java.lang.CharSequence";
Class<?> expectedType = Class.forName(configuredTypeName);
Object candidate = "Java";

System.out.println(expectedType.isInstance(candidate)); // true
```

`Class.forName(String)` có thể ném `ClassNotFoundException` và mặc định có thể kích hoạt khởi tạo lớp. Đoạn trên là **mã minh họa đặt trong ngữ cảnh có xử lý hoặc khai báo ngoại lệ**; chương trình chạy đầy đủ ở phần tiếp theo khai báo `throws ClassNotFoundException`.

Không nên xem tên kiểu lấy từ đầu vào không tin cậy là một cơ chế cấp quyền. Khi cần cho phép người dùng chọn kiểu, hãy giới hạn danh sách kiểu hợp lệ và kiểm tra quyền/trường hợp sử dụng ở tầng ứng dụng.

## 3. Đối chiếu trực tiếp

| Tiêu chí | `instanceof` | `Class.isInstance()` |
| --- | --- | --- |
| Ví dụ | `obj instanceof Number` | `Number.class.isInstance(obj)` |
| Cách chỉ ra kiểu đích | Tên kiểu hoặc mẫu kiểu được viết trong mã | Giá trị `Class<?>`/`Class<T>` |
| Chọn kiểu đích khi chạy | Không thể dùng biến `Class<?>` thay tên kiểu | Có thể, qua tham số, registry, `Class.forName`… |
| Kiểm tra quan hệ kế thừa/interface | Có | Có |
| Đối tượng `null` | `false` | `false` |
| Thu hẹp kiểu trong Java 17 | Có thể khai báo biến pattern (`instanceof T t`) | Không tự sinh biến pattern hoặc tự thu hẹp kiểu |
| Tình huống nên dùng | Kiểu đã biết, nhánh xử lý nghiệp vụ thông thường | Framework, reflection, bộ kiểm tra nhận `Class` từ cấu hình hoặc API |

Với một kiểu tham chiếu cụ thể `T` có thể dùng trực tiếp, ta có tính tương đương thực tế:

```java
boolean a = value instanceof Number;
boolean b = Number.class.isInstance(value);
// a và b cho cùng kết quả đối với cùng value.
```

Điều này **không** có nghĩa mọi biểu thức liên quan đến generic, kiểu nguyên thủy hoặc kiểu được chọn động đều có thể đổi cú pháp một cách máy móc.

## 4. Chương trình Java 17+ chạy được

Lưu toàn bộ mã sau trong tệp `TypeChecksDemo.java`:

```java
import java.util.List;

public class TypeChecksDemo {
    public static void main(String[] args) throws ClassNotFoundException {
        Object text = "Java";

        System.out.println(text instanceof CharSequence);
        System.out.println(CharSequence.class.isInstance(text));

        Class<?> selectedType = Class.forName("java.lang.CharSequence");
        System.out.println(selectedType.isInstance(text));
        System.out.println(Integer.class.isInstance(text));

        Object number = Integer.valueOf(42);

        if (number instanceof Number n) {
            System.out.println(n.intValue());
        }

        Class<? extends Number> numberType = Number.class;
        if (numberType.isInstance(number)) {
            Number n = numberType.cast(number);
            System.out.println(n.intValue());
        }

        System.out.println(String.class.isInstance(null));
        System.out.println(int.class.isInstance(number));
        System.out.println(Integer.class.isInstance(number));

        Object words = List.of("a", "b");
        System.out.println(List.class.isInstance(words));
        System.out.println(Object[].class.isInstance(new String[] {"x"}));
        System.out.println(Object[].class.isInstance(new int[] {1}));

        String checked = requireType(String.class, text);
        System.out.println(checked.length());
    }

    private static <T> T requireType(Class<T> expectedType, Object value) {
        if (!expectedType.isInstance(value)) {
            throw new IllegalArgumentException(
                    "Expected " + expectedType.getTypeName());
        }
        return expectedType.cast(value);
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac --release 17 TypeChecksDemo.java
java TypeChecksDemo
```

Kết quả chuẩn (`stdout`):

```text
true
true
true
false
42
42
false
false
true
true
true
false
4
```

Giải thích các nhóm kết quả:

1. Chuỗi `"Java"` vừa là `CharSequence` qua `instanceof`, vừa thỏa `CharSequence.class.isInstance(...)` và kiểu được nạp bằng `Class.forName`. Nó không phải `Integer`.
2. Hai nhánh xử lý đối tượng `Integer(42)` đều lấy được giá trị `42`: nhánh thứ nhất dùng biến pattern `Number n`, nhánh thứ hai dùng token `Class<? extends Number>` và `cast`.
3. `null` không là instance của `String`. `int.class` đại diện cho kiểu nguyên thủy `int`, còn `number` là đối tượng `Integer`, nên kiểm tra với `int.class` là `false` và với `Integer.class` là `true`.
4. `List.class` nhận diện đối tượng danh sách; mảng `String[]` là `Object[]`, còn mảng nguyên thủy `int[]` không phải `Object[]`.
5. `requireType(String.class, text)` xác nhận kiểu, ép kiểu có kiểm tra và trả về `String`. `"Java".length()` bằng `4`.

## 5. `Class.cast()` và giới hạn của thu hẹp kiểu

`isInstance()` trả về `boolean` và **không làm trình biên dịch suy ra kiểu cụ thể của biến**:

```java
Object candidate = "hello";
Class<String> expected = String.class;

if (expected.isInstance(candidate)) {
    // candidate vẫn có kiểu khai báo là Object.
    String result = expected.cast(candidate);
    System.out.println(result.toUpperCase()); // HELLO
}
```

`Class<T>.cast(Object)` dùng kiểu được đại diện bởi `Class<T>` để ép kiểu lúc chạy; nếu đối tượng không tương thích, nó ném `ClassCastException` (riêng `cast(null)` trả về `null`). Kết hợp `isInstance()` trước `cast()` giúp quyết định rõ cách xử lý trường hợp không khớp, chẳng hạn trả về kết quả lỗi riêng thay vì để ngoại lệ xuất hiện.

Với `Class<?>`, kiểu `T` cụ thể chưa được biết:

```java
Class<?> expected = String.class;
Object candidate = "hello";

if (expected.isInstance(candidate)) {
    Object accepted = expected.cast(candidate);
    System.out.println(accepted); // hello
}
```

Biến `accepted` vẫn được dùng như `Object` trong ví dụ; không thể coi `Class<?>` tự động biến thành `Class<String>` chỉ vì đối tượng thực tế là chuỗi. Nếu API biết kiểu cần trả về, hãy nhận tham số `Class<T>` và dùng `T` như hàm `requireType` trong chương trình đầy đủ.

## 6. Các trường hợp biên dễ nhầm

### `null`

Cả `obj instanceof T` lẫn `T.class.isInstance(obj)` đều trả về `false` khi `obj == null`. Vì vậy `isInstance()` không phải phép kiểm tra một giá trị có thể gán vào biến kiểu tham chiếu hay không theo mọi nghĩa: Java cho phép gán `null` cho nhiều biến tham chiếu, nhưng phép thử instance vẫn trả về `false`.

### Primitive, boxing và mảng

`int.class` là một đối tượng `Class` hợp lệ, nhưng **một giá trị primitive không phải đối tượng instance** để `isInstance()` chấp nhận:

```java
Integer boxed = 7;

System.out.println(int.class.isInstance(boxed));     // false
System.out.println(Integer.class.isInstance(boxed)); // true
```

Lệnh `Integer.class.isInstance(7)` cũng trả về `true` vì đối số primitive `7` được **autobox** thành `Integer` khi truyền vào tham số `Object`. Điều này không có nghĩa `int.class` và `Integer.class` là cùng một lớp.

Với mảng, Java kiểm tra loại thành phần và quy tắc tương thích mảng. `String[]` là một loại `Object[]`; `int[]` là một đối tượng và là `Object`, nhưng **không** là `Object[]`:

```java
System.out.println(Object.class.isInstance(new int[] {1}));    // true
System.out.println(Object[].class.isInstance(new int[] {1}));  // false
System.out.println(Object[].class.isInstance(new String[] {"a"})); // true
```

### Generic và type erasure

Thông tin tham số kiểu như `String` trong `List<String>` nhìn chung **không được lưu để kiểm tra phần tử bằng `instanceof` hoặc `Class.isInstance()`** ở thời điểm chạy:

```java
Object data = List.of("a", "b");

System.out.println(data instanceof List<?>);    // true
System.out.println(List.class.isInstance(data)); // true

// KHÔNG biên dịch với data là Object: kiểu tham số hóa không reifiable.
// boolean x = data instanceof List<String>;

// KHÔNG có class literal cho List<String>.
// Class<?> c = List<String>.class;
```

Hai phép kiểm tra hợp lệ chỉ xác nhận đối tượng là một `List`, không xác nhận mỗi phần tử có kiểu `String`. Khi cần xác minh phần tử, cần duyệt và kiểm tra dữ liệu theo yêu cầu thực tế, hoặc dùng cơ chế mô tả kiểu tổng quát riêng phù hợp với framework.

### Class loader và định danh kiểu

Hai lớp có **cùng tên đầy đủ** nhưng được định nghĩa bởi hai class loader khác nhau có thể là hai kiểu runtime khác nhau. `Class.isInstance()` kiểm tra tính tương thích với đúng đối tượng `Class` đang giữ, không chỉ so sánh chuỗi tên lớp. Điều này đặc biệt quan trọng trong hệ thống plugin hoặc môi trường nạp lớp động.

## 7. Phân biệt với `Class.isAssignableFrom()`

Reflection còn có phép kiểm tra rất gần nhưng nhận **kiểu**, không nhận đối tượng:

```java
System.out.println(Number.class.isInstance(Integer.valueOf(5))); // true
System.out.println(Number.class.isAssignableFrom(Integer.class)); // true
```

- `expectedType.isInstance(value)` hỏi **đối tượng `value`** có là instance của `expectedType` không.
- `expectedType.isAssignableFrom(actualType)` hỏi **kiểu `actualType`** có thể gán cho biến kiểu `expectedType` theo quan hệ kiểu tham chiếu hay không.

Nếu đã có đối tượng không `null`, `expectedType.isInstance(value)` thường là cách diễn đạt trực tiếp hơn việc tự lấy `value.getClass()`. Gọi `value.getClass()` trên `null` sẽ ném `NullPointerException`; còn `isInstance(null)` trả về `false`. `isAssignableFrom` cũng có thể kiểm tra quan hệ giữa các `Class` của kiểu primitive, nhưng không thay thế tự động các quy tắc boxing/unboxing của biểu thức Java.

## 8. Chọn API trong thực tế

Dùng **`instanceof`** khi logic xử lý biết trước kiểu cần kiểm tra, nhất là khi pattern variable giúp đọc và xử lý giá trị ngay. Ví dụ: một nhánh nghiệp vụ nhận `Object` và xử lý riêng `String` hoặc `Number`.

Dùng **`Class.isInstance()`** khi kiểu đích là một giá trị đi qua API, cấu hình hoặc registry; chẳng hạn bộ lọc nhận `Class<T> expectedType`, bộ điều phối plugin được đăng ký bằng đối tượng `Class`, hoặc tiện ích reflection muốn kiểm tra đối tượng trước khi gọi `expectedType.cast(...)`.

Một mẫu API hữu ích là:

```java
static <T> boolean accepts(Class<T> expectedType, Object candidate) {
    return expectedType.isInstance(candidate);
}
```

Ví dụ `accepts(CharSequence.class, "Java")` trả về `true`. Tham số `Class<T>` làm ý nghĩa của kiểu đích rõ ràng, còn `isInstance` thực hiện kiểm tra khi chạy.

Không cần thay mọi `instanceof` bằng reflection: nếu kiểu cố định trong mã, `instanceof` ngắn gọn và hỗ trợ pattern matching. Nếu kiểu có thể thay đổi trong lúc chạy, `Class.isInstance()` cho phép truyền kiểu như dữ liệu mà vẫn dùng quan hệ kế thừa/interface của Java.

## 9. Lỗi thường gặp và tự kiểm tra

- Viết `value instanceof expectedClass` khi `expectedClass` là biến `Class<?>`: sai cú pháp/kiểu; hãy gọi `expectedClass.isInstance(value)`.
- Tưởng `isInstance` tự ép kiểu biến: nó chỉ trả về `boolean`; cân nhắc `Class<T>.cast()` sau khi kiểm tra.
- Dùng `int.class` để kiểm tra một `Integer`: hai kiểu khác nhau, kết quả `false`.
- Dùng `List.class.isInstance(value)` để kết luận đó là `List<String>`: tham số kiểu đã bị erasure, kết luận này không được bảo đảm.
- Dùng `obj.getClass()` mà chưa xử lý `null`, hoặc so sánh tên lớp thay cho quan hệ tương thích kiểu và class loader.

**Câu hỏi tự kiểm tra:**

1. Nếu kiểu kiểm tra được lưu trong `Class<?> selected`, vì sao `instanceof selected` không hợp lệ, và lời gọi nào thay thế được?
2. Vì sao `Number.class.isInstance(Integer.valueOf(10))` là `true` nhưng `int.class.isInstance(Integer.valueOf(10))` là `false`?
3. Khi cần trả về `T` từ một hàm nhận `Class<T>`, vì sao kết hợp `isInstance` và `cast` rõ ràng hơn ép kiểu `T` không kiểm tra?
4. Một thư viện nhận tên lớp cấu hình ở thời điểm chạy nên dùng cơ chế nào để kiểm tra đối tượng? Nó cần kiểm soát thêm điều gì đối với tên lớp được phép tải?

**Ghi nhớ:** `instanceof` biểu diễn **kiểu đích ngay trong mã Java**; `Class.isInstance()` biểu diễn **kiểu đích bằng một đối tượng `Class` có thể chọn khi chạy**. Cả hai kiểm tra tương thích kiểu runtime, nhưng khác nhau ở khả năng truyền kiểu như dữ liệu và hỗ trợ thu hẹp kiểu của trình biên dịch.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-names-identity-010">isAssignableFrom, cast và asSubclass</a>

<details>
<summary>Xem chi tiết</summary>

# Reflection: `isAssignableFrom`, `cast` và `asSubclass`

Ba phương thức của `java.lang.Class` giúp xử lý **tương thích kiểu tại runtime** khi lớp hoặc đối tượng chỉ được biết sau khi chương trình chạy, chẳng hạn khi nạp plugin, chọn implementation theo cấu hình hay xử lý kết quả từ reflection. Chúng liên quan nhau nhưng trả lời **ba câu hỏi khác nhau**:

| Phương thức | Câu hỏi | Đầu vào | Kết quả |
| --- | --- | --- | --- |
| `target.isAssignableFrom(source)` | Giá trị mang kiểu `source` có thể gán cho biến kiểu `target` không? | `Class<?>` | `boolean` |
| `target.cast(object)` | Đối tượng `object` có phù hợp với kiểu `target` không? | `Object` | Đối tượng được kiểm tra và trả về với kiểu `T`, hoặc ném `ClassCastException` |
| `candidate.asSubclass(base)` | Kiểu `candidate` có thể được xem là lớp con/kiểu triển khai của `base` không? | `Class<U>` | Chính token `Class` hiện tại với kiểu tĩnh `Class<? extends U>`, hoặc ném `ClassCastException` |

Điểm chung là chúng dựa trên **danh tính kiểu ở runtime** và quan hệ kế thừa/triển khai thực tế. Chúng không tạo đối tượng mới, không ép kiểu dữ liệu theo nghĩa chuyển giá trị, và không thay thế việc kiểm tra generic type arguments.

## 1. Nền tảng: `Class<T>` là token kiểu

Ví dụ `String.class` có kiểu tĩnh `Class<String>`; `Runnable.class` là `Class<Runnable>`. Khi tên lớp chỉ có ở runtime, chẳng hạn sau `Class.forName("...")`, ta thường chỉ có `Class<?>`: biết có một kiểu cụ thể nhưng chưa biết đó là kiểu nào lúc biên dịch.

Giả sử:

~~~java
interface Task {}
class EmailTask implements Task {}
class Other {}
~~~

Các quan hệ sau là hợp lệ:

~~~java
Task task = new EmailTask();   // widening reference conversion
Object value = task;           // upcast hợp lệ
~~~

Ngược lại, `EmailTask task = (EmailTask) someTask` cần kiểm tra giá trị thực tại runtime. Với reflection, ta cần kiểm tra quan hệ này thông qua `Class`, vì không phải lúc nào tên kiểu cũng được biết ở compile time.

## 2. `Class.isAssignableFrom(Class<?>)`: kiểm tra quan hệ hai kiểu

Cú pháp:

~~~java
boolean accepted = Target.class.isAssignableFrom(Source.class);
~~~

**Đọc từ phải sang trái:** “Một giá trị kiểu `Source` có gán được vào biến kiểu `Target` hay không?”. Gọi trên **kiểu đích** và truyền **kiểu nguồn**. Phương thức trả về `true` khi hai kiểu giống nhau hoặc nguồn có thể chuyển sang đích bằng **identity conversion** hay **widening reference conversion**; không tính những chuyển đổi cần cast thu hẹp kiểu.

~~~java
Task.class.isAssignableFrom(EmailTask.class);   // true: implements Task
EmailTask.class.isAssignableFrom(Task.class);   // false: chiều ngược
Task.class.isAssignableFrom(Task.class);        // true: cùng kiểu
Object.class.isAssignableFrom(Task.class);      // true: interface cũng quy chiếu được đến Object
Runnable.class.isAssignableFrom(Thread.class); // true: Thread implements Runnable
~~~

Một lớp có thể triển khai nhiều interface, nên quan hệ tương thích kiểu rộng hơn chuỗi `extends`. Không cần đối tượng `new EmailTask()` để thực hiện phép kiểm tra này.

### Khi nào nên dùng?

Khi duyệt `Class<?>` nhận được từ scan classpath, plugin registry hoặc `Class.forName`, kiểm tra nó có đáp ứng contract mong muốn trước khi đăng ký:

~~~java
Class<?> candidate = EmailTask.class;
if (Task.class.isAssignableFrom(candidate)) {
    // candidate mang kiểu Task hoặc một subtype/implementation tương thích.
}
~~~

Đây chỉ là kiểm tra **kiểu**. Một lớp phù hợp vẫn có thể là abstract, interface, thiếu constructor thích hợp hoặc không thể khởi tạo vì quyền truy cập.

### Primitive và array: các trường hợp dễ nhầm

`isAssignableFrom` xét tương thích **tham chiếu**, không mô phỏng mọi quy tắc gán ở ngôn ngữ Java:

| Biểu thức | Kết quả | Lý do |
| --- | --- | --- |
| `long.class.isAssignableFrom(int.class)` | `false` | Không áp dụng primitive widening `int → long` |
| `int.class.isAssignableFrom(int.class)` | `true` | Cùng token primitive |
| `Integer.class.isAssignableFrom(int.class)` | `false` | Không áp dụng boxing |
| `Number[].class.isAssignableFrom(Integer[].class)` | `true` | Mảng tham chiếu có covariance |
| `Object[].class.isAssignableFrom(String[].class)` | `true` | `String[]` là subtype của `Object[]` |
| `Object[].class.isAssignableFrom(int[].class)` | `false` | `int[]` không phải mảng các tham chiếu `Object` |
| `Object.class.isAssignableFrom(int[].class)` | `true` | Mọi array là một `Object` |
| `Cloneable.class.isAssignableFrom(int[].class)` | `true` | Mọi array implement `Cloneable` |
| `java.io.Serializable.class.isAssignableFrom(int[].class)` | `true` | Mọi array implement `Serializable` |

Array covariance **không bảo đảm** mọi phép ghi phần tử an toàn: một biến `Object[]` tham chiếu tới `String[]` vẫn ném `ArrayStoreException` nếu cố ghi `Integer` vào đó.

Truyền `null` vào `isAssignableFrom(null)` ném `NullPointerException` vì phương thức cần một token lớp để so sánh.

## 3. `Class.cast(Object)`: kiểm tra và chuyển kiểu một đối tượng

`Class<T>.cast(Object)` thực hiện runtime cast theo token kiểu đại diện. Kết quả có kiểu tĩnh `T` nhờ generic của token `Class<T>`:

~~~java
Object raw = "hello";
String text = String.class.cast(raw); // "hello"
Runnable.class.cast(raw);             // ClassCastException: String không implements Runnable
~~~

Đối với kiểu tham chiếu, có thể hiểu gần như cast có kiểm tra ở runtime. Sự khác biệt quan trọng là **kiểu đích có thể được truyền vào dưới dạng tham số**:

~~~java
static <T> T requireType(Class<T> expected, Object value) {
    return expected.cast(value);
}

// Gọi: String name = requireType(String.class, someObject);
~~~

Các trường hợp cần nhớ:

- `target.cast(null)` trả `null`, **không** ném exception.
- Đối tượng không thuộc kiểu đích hoặc subtype tương thích → `ClassCastException`.
- Cast thành công trả **chính cùng tham chiếu đối tượng**, không clone, chuyển đổi giá trị hay gọi constructor.
- `int.class.cast(Integer.valueOf(1))` ném `ClassCastException`: `int.class` là token primitive, còn đối tượng là `Integer`; boxing/unboxing không xảy ra trong `Class.cast`.
- `Class<?> unknown = ...` vẫn có thể gọi `unknown.cast(value)`, nhưng trình biên dịch không thể tự suy ra đó là `Task`. Cần token `Class<Task>` hoặc một bước thu hẹp token hợp lệ.

Nếu chỉ cần câu trả lời `true/false` cho **một đối tượng**, dùng `expected.isInstance(value)` (dynamic equivalent of `instanceof`), thay vì bắt `ClassCastException` để kiểm tra thường xuyên.

## 4. `Class.asSubclass(Class<U>)`: thu hẹp *token lớp* an toàn

Giả sử bộ quét trả về `Class<?>`, trong khi API phía sau cần `Class<? extends Task>`. Việc cast generic trực tiếp:

~~~java
// Không nên: unchecked cast, quan hệ kiểu không được xác minh tại runtime.
// Class<? extends Task> typed = (Class<? extends Task>) candidate;
~~~

không có bảo đảm an toàn từ trình biên dịch. Thay vào đó:

~~~java
Class<?> candidate = EmailTask.class;
Class<? extends Task> typed = candidate.asSubclass(Task.class);
~~~

`asSubclass` kiểm tra `Task.class.isAssignableFrom(candidate)` theo quan hệ tương thích và trả **chính token `candidate`** với kiểu tĩnh mới. Nếu không phù hợp, nó ném `ClassCastException`:

~~~java
String.class.asSubclass(Task.class); // ClassCastException
Task.class.asSubclass(Task.class);   // Hợp lệ: cùng kiểu
~~~

Tên “subclass” ở API này bao gồm trường hợp **chính kiểu đó** và kiểu **implement interface**. Nó không đòi hỏi phải có một nhánh `extends` trực tiếp.

Điều `asSubclass` thay đổi là **cách trình biên dịch hiểu token `Class`**; nó không cast một object instance. Muốn kiểm tra một object, dùng `cast`; muốn biết quan hệ giữa hai token mà không ném exception, dùng `isAssignableFrom`.

## 5. Ví dụ chạy hoàn chỉnh (Java 17+)

Lưu toàn bộ vào **`ClassCompatibilityDemo.java`**. Ví dụ bao gồm interface, quan hệ hướng gán, array, primitive, cast đối tượng và thu hẹp token để khởi tạo implementation bằng reflection.

~~~java
public class ClassCompatibilityDemo {
    interface Task { String run(); }

    static final class EmailTask implements Task {
        public EmailTask() {}

        @Override
        public String run() { return "sent"; }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<?> candidate = EmailTask.class;

        System.out.println("Task <- EmailTask: " + Task.class.isAssignableFrom(candidate));
        System.out.println("EmailTask <- Task: " + EmailTask.class.isAssignableFrom(Task.class));
        System.out.println("Task <- Task: " + Task.class.isAssignableFrom(Task.class));
        System.out.println("Object <- Task: " + Object.class.isAssignableFrom(Task.class));
        System.out.println("Number[] <- Integer[]: " + Number[].class.isAssignableFrom(Integer[].class));
        System.out.println("Object[] <- int[]: " + Object[].class.isAssignableFrom(int[].class));
        System.out.println("Object <- int[]: " + Object.class.isAssignableFrom(int[].class));
        System.out.println("long <- int: " + long.class.isAssignableFrom(int.class));
        System.out.println("int <- int: " + int.class.isAssignableFrom(int.class));

        Object obj = new EmailTask();
        Task task = Task.class.cast(obj);
        System.out.println("cast: " + task.run());
        System.out.println("null cast: " + (Task.class.cast(null) == null));

        Class<? extends Task> typedClass = candidate.asSubclass(Task.class);
        Task created = typedClass.getDeclaredConstructor().newInstance();
        System.out.println("asSubclass: " + created.run());

        try { Task.class.cast("text"); }
        catch (ClassCastException e) {
            System.out.println("cast error: " + e.getClass().getSimpleName());
        }

        try { String.class.asSubclass(Task.class); }
        catch (ClassCastException e) {
            System.out.println("asSubclass error: " + e.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch bằng JDK 17 trở lên:

~~~bash
javac --release 17 ClassCompatibilityDemo.java
java ClassCompatibilityDemo
~~~

Stdout chính xác (đã biên dịch và chạy với `--release 17`):

~~~text
Task <- EmailTask: true
EmailTask <- Task: false
Task <- Task: true
Object <- Task: true
Number[] <- Integer[]: true
Object[] <- int[]: false
Object <- int[]: true
long <- int: false
int <- int: true
cast: sent
null cast: true
asSubclass: sent
cast error: ClassCastException
asSubclass error: ClassCastException
~~~

Các dòng bắt lỗi in **tên loại exception** qua `getSimpleName()` nên không phụ thuộc vào chi tiết thông báo lỗi giữa các bản JDK. Hai thao tác `cast` và `asSubclass` chỉ xác minh tương thích: lời gọi constructor qua `getDeclaredConstructor().newInstance()` mới thực sự tạo đối tượng.

## 6. Áp dụng trong thiết kế plugin / factory

Một factory có thể nhận tên lớp từ cấu hình và contract kiểu từ caller. **Đoạn sau là phương thức minh họa**, cần đặt bên trong một class Java và cung cấp một implementation hợp lệ; nó không phải chương trình độc lập:

~~~java
static <S> S create(String className, Class<S> contract)
        throws ReflectiveOperationException {
    Class<?> raw = Class.forName(className);

    if (!contract.isAssignableFrom(raw)) {
        throw new IllegalArgumentException(
                raw.getName() + " does not implement " + contract.getName());
    }

    Class<? extends S> implementation = raw.asSubclass(contract);
    return implementation.getDeclaredConstructor().newInstance();
}
~~~

Giải thích từng bước:

1. `Class.forName(className)` nạp/lấy token lớp theo tên đầy đủ; ở overload một tham số, việc khởi tạo class có thể diễn ra. Không nên nạp tùy ý tên lớp từ đầu vào không tin cậy.
2. `contract.isAssignableFrom(raw)` kiểm tra khả năng đáp ứng contract; sai thì trả lỗi nghiệp vụ rõ ràng.
3. `raw.asSubclass(contract)` đưa quan hệ đã kiểm tra vào **kiểu tĩnh** `Class<? extends S>`, thuận tiện cho reflection API.
4. `getDeclaredConstructor().newInstance()` gọi constructor không tham số rồi trả object thuộc `S`. Phải bảo đảm class cụ thể, constructor tồn tại và có thể truy cập; nếu không, reflection có thể ném `NoSuchMethodException`, `InstantiationException`, `IllegalAccessException` hoặc `InvocationTargetException`.

Một nơi khác có thể nhận object từ framework hoặc reflection rồi áp contract:

~~~java
Object value = /* kết quả từ framework */ null;
Task task = Task.class.cast(value); // Có thể trả null; kiểm tra null nếu contract cấm.
~~~

`cast` **không bảo đảm non-null**. Nếu kết quả có thể trống, phải kiểm tra `null` theo yêu cầu nghiệp vụ. Trong factory ở trên, constructor thành công sẽ trả một object; việc lớp đó có hành vi an toàn/hợp lệ hay không vẫn là trách nhiệm riêng của ứng dụng.

## 7. Giới hạn và các lỗi phổ biến

**Sai chiều của `isAssignableFrom`.** Khi lọc class triển khai `Task`, viết `Task.class.isAssignableFrom(candidate)`. Viết ngược `candidate.isAssignableFrom(Task.class)` thường lọc nhầm hoặc bỏ mất subtype cần chọn.

**Đồng nhất `Class` với instance.** `Task.class.isAssignableFrom(EmailTask.class)` so hai kiểu; `Task.class.isInstance(object)` kiểm tra instance và trả boolean; `Task.class.cast(object)` kiểm tra instance và trả tham chiếu kiểu `Task` hoặc ném lỗi; `EmailTask.class.asSubclass(Task.class)` kiểm tra token và trả token có generic hẹp hơn. Không thay thế lẫn nhau tùy tiện.

**Tưởng `cast` sẽ chuyển giá trị.** `String.class.cast(123)` không biến `123` thành `"123"`. Dùng API chuyển đổi giá trị như `String.valueOf` hoặc parse phù hợp khi yêu cầu là chuyển đổi dữ liệu.

**Tưởng phương thức xác nhận generic parameter.** Vì type erasure, `List.class.isAssignableFrom(ArrayList.class)` chỉ xác nhận quan hệ kiểu raw; không chứng minh phần tử là `String` hay `Integer`. Không có literal `List<String>.class` trong Java. Muốn kiểm tra kiểu phần tử, cần kiểm tra nội dung hoặc đọc metadata generic phù hợp (`ParameterizedType`) khi metadata đó thật sự hiện diện.

**Nhầm primitive với wrapper.** `Integer.class` khác `int.class`; `Integer.class.isAssignableFrom(int.class)` là `false`. Cả widening primitive lẫn boxing/unboxing đều không được `isAssignableFrom` áp dụng. `Class.cast` cũng không chuyển object thành primitive.

**Bỏ qua class loader.** Hai class có cùng tên đầy đủ nhưng do hai defining class loader khác nhau định nghĩa vẫn có thể là hai runtime type khác nhau. Vì vậy tên khớp không đủ để `isAssignableFrom`, `cast` hoặc `asSubclass` thành công; hệ thống plugin phải chú ý cách chia sẻ contract interface giữa các loader.

**Cho rằng tương thích kiểu đồng nghĩa có thể khởi tạo.** `asSubclass` không kiểm tra class có `abstract` hay interface, constructor phù hợp, accessibility, exception từ constructor hoặc side effect khi class loading. `Class.newInstance()` đã deprecated; ưu tiên `getDeclaredConstructor().newInstance()` khi cần reflection construction.

**Truyền null thiếu suy xét.** `isAssignableFrom(null)` và `asSubclass(null)` ném `NullPointerException`, còn `cast(null)` trả `null`. Khi thiết kế API, nên xác định rõ đầu vào nào được chấp nhận.

## 8. Chọn phương thức theo tình huống

- Có **hai `Class`**, chỉ cần quyết định chấp nhận subtype hay không → `target.isAssignableFrom(candidate)`.
- Có **một object**, cần giá trị typed và chấp nhận exception nếu sai → `target.cast(object)`.
- Có **một object**, chỉ muốn thử trước rồi rẽ nhánh → `target.isInstance(object)`.
- Có **`Class<?>`** từ reflection, cần chuyển thành **`Class<? extends Contract>`** để đưa cho API hoặc lấy typed constructor → `candidate.asSubclass(Contract.class)`.

Một chuỗi thường dùng trong runtime extensibility là: **nạp token `Class<?>` → kiểm tra `Contract.class.isAssignableFrom(raw)` → thu hẹp bằng `raw.asSubclass(Contract.class)` → khởi tạo qua constructor → dùng giá trị như `Contract`**. Nếu đối tượng xuất phát từ nguồn khác, có thể dùng `Contract.class.cast(value)` để kiểm tra ngay tại ranh giới nhận object.

Tài liệu chuẩn: [Java SE 21 — `java.lang.Class`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html), mục `isAssignableFrom`, `cast`, `asSubclass` và `isInstance`.

</details>

- [Quay lại đầu trang](#back-to-top)
