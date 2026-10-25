<a id="back-to-top"></a>

# Ngoại lệ và chẩn đoán

## Menu
- [ClassNotFoundException và NoClassDefFoundError](#exceptions-diagnostics-001)
- [NoSuchFieldException và NoSuchMethodException](#exceptions-diagnostics-002)
- [IllegalAccessException khác InaccessibleObjectException](#exceptions-diagnostics-003)
- [IllegalArgumentException do receiver hoặc đối số không hợp lệ](#exceptions-diagnostics-004)
- [InstantiationException và kiểu không thể khởi tạo](#exceptions-diagnostics-005)
- [InvocationTargetException và cách giữ nguyên cause](#exceptions-diagnostics-006)
- [ExceptionInInitializerError khi thao tác kích hoạt khởi tạo lớp](#exceptions-diagnostics-007)
- [GenericSignatureFormatError, TypeNotPresentException và MalformedParameterizedTypeException](#exceptions-diagnostics-008)
- [MalformedParametersException và metadata tham số không hợp lệ](#exceptions-diagnostics-009)
- [AnnotationTypeMismatchException và IncompleteAnnotationException](#exceptions-diagnostics-010)
- [Phân biệt lỗi ở công cụ reflection với lỗi trong mã đích](#exceptions-diagnostics-011)
- [Ghi ngữ cảnh lớp, loader, module và chữ ký khi điều tra](#exceptions-diagnostics-012)

## <a id="exceptions-diagnostics-001">ClassNotFoundException và NoClassDefFoundError</a>

<details>
<summary>Xem chi tiết</summary>

# ClassNotFoundException và NoClassDefFoundError: phân biệt và chẩn đoán trong Java

## 1. Hiểu đúng bản chất hai lỗi

Khi Java dùng reflection, tải plugin, khởi tạo class hoặc giải quyết phụ thuộc runtime, hai tên lỗi thường gặp là `ClassNotFoundException` và `NoClassDefFoundError`. Cùng liên quan đến kiểu không thể sử dụng, nhưng **chúng thuộc hai nhánh ngoại lệ khác nhau và được phát sinh trong các tình huống khác nhau**:

- **`ClassNotFoundException` (CNFE)** là checked exception, được khai báo bởi các API chủ động tìm lớp qua tên ở runtime như `Class.forName(String)` và `ClassLoader.loadClass(String)`. Người gọi phải bắt hoặc khai báo `throws`. Nó thường báo rằng loader được yêu cầu không tìm thấy định nghĩa lớp theo tên đã cho.
- **`NoClassDefFoundError` (NCDFE)** là `Error`, con của `LinkageError`. JVM không thể có được một định nghĩa lớp hợp lệ khi cần liên kết/sử dụng một tham chiếu kiểu, hoặc lớp đã bị đánh dấu lỗi sau khi khởi tạo thất bại. Chương trình có thể biên dịch thành công rồi mới gặp lỗi này lúc chạy.

Đừng ghi nhớ mẹo quá mức rằng “CNFE là chưa từng tìm, NCDFE là đã từng load”. **NCDFE có thể xuất hiện ngay trong lần đầu JVM phải giải quyết một dependency không hiện diện**, và cũng có thể xuất hiện sau lần khởi tạo static lỗi. Cùng một stack trace NCDFE đôi khi có `Caused by: ClassNotFoundException` ở phía dưới, phản ánh việc loader không tìm được lớp gây lỗi liên kết.

| Khía cạnh | `ClassNotFoundException` | `NoClassDefFoundError` |
| --- | --- | --- |
| Phân cấp | `ReflectiveOperationException` → `Exception` | `LinkageError` → `Error` |
| Kiểm tra lúc compile | Checked: xử lý hoặc khai báo | Unchecked (`Error`) |
| Điểm phát sinh điển hình | Gọi API tìm class bằng tên, ví dụ `Class.forName("pkg.Plugin")` | JVM resolve class mà bytecode cần, hoặc class bị hỏng trạng thái khởi tạo |
| Mã đã biên dịch thành công? | Có thể | Thường có; runtime có thể thiếu class đã dùng lúc build |
| Luôn do thiếu JAR? | Không: có thể sai tên, loader, module | Không: có thể do lỗi initializer hoặc linkage |
| Cách chẩn đoán | Kiểm tra tên nhị phân, class loader và runtime classpath/module path | Kiểm tra dependency tại runtime, thông điệp lỗi và cause đầu tiên |

Với lỗi triển khai thực tế, **mục tiêu là sửa tên lớp, dependency, loader hoặc initializer gốc**, không chỉ tìm cách bắt exception để ứng dụng tiếp tục.

## 2. Loading, linking và initialization khác nhau

JVM xử lý một kiểu qua những giai đoạn có liên quan:

1. **Loading**: class loader tìm định nghĩa class (bytecode), tạo đối tượng `Class` tương ứng. Định danh runtime của một class còn phụ thuộc loader định nghĩa nó.
2. **Linking**: JVM xác minh class, chuẩn bị các trường static và giải quyết những tham chiếu ký hiệu cần thiết. Resolution có thể diễn ra theo từng thời điểm; không phải mọi dependency đều resolve ngay lúc class ngoài được load.
3. **Initialization**: JVM chạy static initializer và các biểu thức khởi tạo trường static khi class cần được khởi tạo theo quy tắc của Java.

Sự khác biệt ảnh hưởng trực tiếp đến reflection:

- `Class.forName("demo.Plugin")` (dạng một tham số) tìm class theo tên và yêu cầu khởi tạo class khi thành công. Nếu loader không tìm ra tên đã yêu cầu, nó khai báo `ClassNotFoundException`.
- `Class.forName(name, false, loader)` cho phép tìm class mà **không yêu cầu khởi tạo ngay**; nhưng không bảo đảm toàn bộ phụ thuộc, lần resolve hoặc lần initialization sau sẽ thành công.
- `ClassLoader.loadClass(name)` thông thường tải class mà không chủ động khởi tạo nó. Loader nào được dùng có thể quyết định có tìm ra class hay không.
- Code đã biên dịch như `new Dependency()` hay `Dependency.call()` có thể buộc JVM resolve `Dependency` tại runtime; khi định nghĩa vắng mặt có thể xuất hiện NCDFE.
- Khi static initializer lần đầu ném exception thông thường, thường phát sinh `ExceptionInInitializerError`; lần sử dụng tiếp theo của cùng class ở trạng thái khởi tạo lỗi có thể gây NCDFE với thông điệp “Could not initialize class ...”.

Một đối tượng `Class<?>` được lấy thành công không chứng minh tất cả method, field, superclass, generic metadata và các dependency còn lại đã hoàn toàn dùng được. Lỗi liên kết có thể chỉ xuất hiện khi framework thực hiện `getDeclaredMethods()`, tạo đối tượng hoặc gọi method.

## 3. Ví dụ độc lập: CNFE và class khởi tạo hỏng

Tạo tệp `ClassLoadingDemo.java`:

```java
public class ClassLoadingDemo {
    static class Broken {
        static final int VALUE = fail();

        private static int fail() {
            throw new IllegalStateException("init failed");
        }
    }

    public static void main(String[] args) {
        try {
            Class.forName("demo.feature.MissingPlugin");
        } catch (ClassNotFoundException e) {
            System.out.println("Class.forName: "
                    + e.getClass().getSimpleName());
        }

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                System.out.println(Broken.VALUE);
            } catch (LinkageError e) {
                System.out.println("init " + attempt + ": "
                        + e.getClass().getSimpleName());
            }
        }
    }
}
```

Lệnh JDK 17+:

```bash
javac -encoding UTF-8 ClassLoadingDemo.java
java ClassLoadingDemo
```

Kết quả:

```text
Class.forName: ClassNotFoundException
init 1: ExceptionInInitializerError
init 2: NoClassDefFoundError
```

**Vì sao như vậy?**

1. Tên `demo.feature.MissingPlugin` cố ý không có trong classpath của bài thử. Đây là lỗi lookup chủ động bằng `Class.forName`, nên code nhận checked exception CNFE.
2. `Broken.VALUE` tính bằng lời gọi `fail()`, vì vậy không phải hằng compile-time được nhúng trực tiếp vào bytecode nơi dùng. Lần truy cập đầu phải khởi tạo `Broken`, gọi `fail()` và gặp `IllegalStateException`; JVM bọc exception từ initializer trong `ExceptionInInitializerError`.
3. JVM đánh dấu class đã lỗi trong giai đoạn initialization. Lần thử tiếp theo ở cùng trạng thái runtime tạo `NoClassDefFoundError`. Lúc này **class file của `Broken` vẫn tồn tại**, chứng tỏ NCDFE không luôn đồng nghĩa thiếu file.
4. `catch (LinkageError)` trong ví dụ nhằm hiển thị hai tình trạng khởi tạo, không phải lời khuyên bắt và bỏ qua `Error` trong sản phẩm.

Đặc biệt, nếu bạn chỉ thấy NCDFE “Could not initialize class ...”, hãy tìm trong log **lần khởi tạo thất bại đầu tiên** và cause gốc của nó, có thể xảy ra sớm hơn nhiều trong quá trình khởi động.

## 4. Ví dụ độc lập: compile có phụ thuộc, runtime lại thiếu

Trường hợp thứ hai tái hiện lỗi deployment kinh điển: lớp ứng dụng có mặt, nhưng lớp mà bytecode cần lại bị bỏ khỏi runtime.

Tạo `Plugin.java`:

```java
public class Plugin {
    public String name() {
        return "ok";
    }
}
```

Tạo `MissingDependencyDemo.java`:

```java
public class MissingDependencyDemo {
    public static void main(String[] args) {
        try {
            System.out.println(new Plugin().name());
        } catch (NoClassDefFoundError e) {
            System.out.println("Runtime dependency: "
                    + e.getClass().getSimpleName());
        }
    }
}
```

Biên dịch cả hai tệp từ cùng thư mục, không khai báo `package`:

```bash
javac -encoding UTF-8 Plugin.java MissingDependencyDemo.java
java MissingDependencyDemo
```

Output khi cả hai class đều còn:

```text
ok
```

Tiếp theo, đổi tên `Plugin.class` thành `Plugin.class.bak` để class không còn được tìm thấy; **không biên dịch lại**:

- **PowerShell (Windows):** `Rename-Item Plugin.class Plugin.class.bak`
- **Linux/macOS:** `mv Plugin.class Plugin.class.bak`

Chạy lại `java MissingDependencyDemo`. Output:

```text
Runtime dependency: NoClassDefFoundError
```

Dù `MissingDependencyDemo.class` vẫn tồn tại và được biên dịch hợp lệ, lệnh `new Plugin()` cần JVM resolve định nghĩa `Plugin`. Khi thiếu class file ở runtime, JVM có thể ném NCDFE. Một số stack trace có thêm cause CNFE bên trong; ví dụ này chỉ in tên lỗi ngoài cùng để kết quả ổn định trên những JDK tương thích.

Sau thực nghiệm, khôi phục `Plugin.class` hoặc biên dịch lại. Trong hệ thống thật, lỗi tương tự đến từ JAR phụ thuộc thiếu, đóng gói container không đủ, scope dependency sai hoặc môi trường triển khai dùng classpath khác môi trường build.

## 5. Reflection và plugin: bẫy thường gặp

### 5.1. Tên lớp không phải đường dẫn tệp

`Class.forName` yêu cầu **tên nhị phân** của class, thường là `com.example.Plugin`. Với lớp lồng, tên nhị phân có dấu `$`, ví dụ `java.util.Map$Entry`, trong khi mã nguồn viết `Map.Entry`. Sai package, dấu câu hoặc thay đổi tên lớp trong cấu hình có thể dẫn tới CNFE.

### 5.2. Phụ thuộc của class có thể thiếu dù chính class đó hiện diện

Giả sử `Plugin.class` tồn tại, nhưng superclass, interface, trường hoặc chữ ký method của nó tham chiếu đến một class vắng mặt. JVM có thể phát sinh NCDFE khi linking, method inspection hoặc invocation, dù lệnh tìm tên `Plugin` không phải vấn đề duy nhất.

`Class.forName` cũng có thể phát sinh `LinkageError` hoặc `ExceptionInInitializerError`, nên không nên khẳng định mọi lỗi từ `Class.forName` đều là CNFE. Class không tương thích phiên bản bytecode có thể gây `UnsupportedClassVersionError`; không đủ quyền phản chiếu vào thành viên của module có thể gây `InaccessibleObjectException`. Đây là các nguyên nhân khác nhau, đòi hỏi xử lý khác nhau.

### 5.3. Class loader và module path ảnh hưởng kết quả

Một JAR có thể tồn tại nhưng không được nhìn thấy bởi loader mà code đang sử dụng. Ví dụ plugin container dùng loader con; gọi `Class.forName(name)` với loader không thích hợp có thể tìm sai nơi. Kiểm tra class loader của plugin và quan hệ giữa các loader trước khi đổi tên lớp hoặc tải thêm JAR.

Với Java Platform Module System (JPMS), cần phân biệt module chưa được resolve/đọc thấy với quyền truy cập thành viên: `exports` và `opens` xử lý ranh giới truy cập; chúng không tự làm xuất hiện dependency bị thiếu. Đừng dùng `--add-opens` để “chữa” thiếu class.

### 5.4. Lỗi liên kết khác không phải hai lỗi này

Nếu JAR bị xung đột phiên bản, JVM có thể ném `NoSuchMethodError`, `NoSuchFieldError`, `IllegalAccessError` hoặc `IncompatibleClassChangeError`. Chúng cũng thuộc bối cảnh linkage nhưng không đồng nghĩa NCDFE. Hãy bám sát đúng class, method và cause in ra trong stack trace.

## 6. Quy trình chẩn đoán có hệ thống

**Bước 1 — Xác định thứ xuất hiện trên cùng và cause đầu tiên.** Với CNFE, tìm code dùng `Class.forName` hoặc `loadClass` và tên đã được yêu cầu. Với NCDFE “wrong name” hoặc tên class dùng dấu `/`, xem cấu trúc class/package và metadata; với “Could not initialize class”, tìm initializer thất bại trước đó. Không suy ra nguyên nhân chỉ từ chữ “ClassNotFound” xuất hiện ở một dòng sâu trong cause chain.

**Bước 2 — Kiểm tra artifact.** Dùng các lệnh JDK sau (đổi `app.jar`, `lib` và tên class theo ứng dụng):

```bash
jar tf app.jar
javap -classpath "lib/*" com.example.Plugin
java -Xlog:class+load=info -cp "app.jar:lib/*" com.example.Main
```

`jar tf` xác nhận entry đã đóng gói; `javap` thử tìm class qua classpath; `-Xlog:class+load=info` của JDK 17 giúp quan sát class thực sự được tải từ đâu. Trên Windows, `classpath` dùng dấu `;` thay dấu `:` trong mẫu lệnh; trên các lệnh demo không có `package`, nên chạy từ thư mục chứa file .class.

Nếu ứng dụng dùng **Spring Boot executable fat JAR**, class và dependency có thể nằm ở `BOOT-INF/classes` và `BOOT-INF/lib`; cần launcher phù hợp. Không giả định `java -cp app.jar Main` sẽ hoạt động như JAR phẳng cho mọi package.

**Bước 3 — Đối chiếu cấu hình build với runtime.** Kiểm tra danh sách phụ thuộc runtime (Gradle `runtimeClasspath` hoặc cây Maven), scope/exclusion, phiên bản thực tế, dependency transitively bị thiếu, container image, đường dẫn JAR và phiên bản JDK. Class tồn tại ở test classpath không có nghĩa được đóng gói vào artifact production.

**Bước 4 — Tái hiện tách từng giai đoạn.** Kiểm tra lần lượt tên class → loader được chọn → tìm `Class<?>` → đọc metadata → khởi tạo → gọi method. Ghi rõ class, loader và lỗi gốc để biết vị trí thất bại. Một kiểm thử chạy trên artifact đã deploy thường có giá trị hơn kiểm thử chỉ chạy trong IDE.

**Bước 5 — Sửa tận gốc và xác minh.** Nếu class thiếu, bổ sung đúng dependency và rebuild/redeploy; nếu sai loader, sửa cơ chế nạp plugin; nếu initializer lỗi, sửa cấu hình hoặc code chạy static; nếu phiên bản JAR xung đột, giải quyết sự không tương thích. Chạy lại trên cùng loại runtime để xác minh.

## 7. Các lỗi chẩn đoán cần tránh

| Nhận định sai | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| “NCDFE nghĩa là class trước đó đã load thành công” | NCDFE cũng có thể xảy ra khi resolution lần đầu không tìm ra class | Phân biệt missing definition với failed initialization |
| “CNFE và NCDFE đều catch bằng `Exception`” | NCDFE là `Error`, không là `Exception` | Phân loại theo hierarchy; sửa nguyên nhân NCDFE |
| “`Class.forName` chỉ có CNFE” | Initializer/linkage có thể ném các loại `Error` | Kiểm tra chain và giai đoạn lỗi |
| “Có class file thì không thể NCDFE” | Static initialization có thể đã thất bại | Xem thông điệp “Could not initialize class” và log gốc |
| “Đổi `--add-opens` sẽ sửa classpath” | Mở quyền truy cập không thêm định nghĩa class | Sửa artifact/loader/module configuration |
| “Catch `Throwable` và bỏ qua sẽ ổn” | JVM hoặc ứng dụng có thể đã ở trạng thái không nhất quán | Không nuốt lỗi; thiết kế fallback chỉ cho plugin tùy chọn, có ranh giới rõ |

Với **plugin tùy chọn**, bắt `ClassNotFoundException` tại điểm lookup và chọn fallback đã thiết kế là hợp lý. Với **`NoClassDefFoundError` trong đường thực thi thiết yếu**, thường cần sửa build/config và dừng hoặc báo lỗi rõ thay vì tiếp tục âm thầm. Việc catch NCDFE trong các ví dụ học tập chỉ để quan sát, không phải một cách chữa lỗi production.

## 8. Tự kiểm tra

**Câu 1.** `Class.forName("missing.OptionalDriver")` không tìm thấy class theo loader đã chọn. Kết quả thường là gì?

**Đáp án:** `ClassNotFoundException` — một lời gọi tìm class chủ động bằng tên không thành công.

**Câu 2.** Ứng dụng compile thành công nhưng container thiếu JAR định nghĩa class được gọi bằng `new MissingDep()`. Lỗi điển hình?

**Đáp án:** `NoClassDefFoundError` ở thời điểm JVM resolve kiểu đó, dù cause đôi khi có CNFE.

**Câu 3.** Vì sao chạy lại một class có static initializer từng ném exception có thể thấy NCDFE mặc dù class file vẫn có?

**Đáp án:** JVM đánh dấu class ở trạng thái initialization lỗi. Lần đầu có thể nhận `ExceptionInInitializerError`; lần sau nhận NCDFE.

**Câu 4.** `Class.forName(name, false, loader)` đã trả về `Class<?>`. Có chắc constructor và mọi dependency dùng được không?

**Đáp án:** Không. Class chưa bị yêu cầu khởi tạo, và lỗi linkage/initialization có thể xuất hiện về sau.

**Câu 5.** Nếu thấy NCDFE “Could not initialize class”, nên xem đâu trước?

**Đáp án:** Lần dùng đầu tiên khiến initializer thất bại và cause nguyên thủy của nó, cùng các điều kiện cấu hình/runtime gây lỗi.

**Ghi nhớ:** `ClassNotFoundException` phản ánh thất bại của API tìm class theo tên; `NoClassDefFoundError` là lỗi JVM không thể dùng định nghĩa lớp trong quá trình liên kết hoặc khởi tạo. Chẩn đoán bằng **stack trace + class loader + artifact runtime + lịch sử khởi tạo**, thay vì chỉ dựa trên tên ngoại lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exceptions-diagnostics-002">NoSuchFieldException và NoSuchMethodException</a>

<details>
<summary>Xem chi tiết</summary>

# NoSuchFieldException và NoSuchMethodException trong Java Reflection

## 1. Bản chất của lỗi tra cứu thành viên

Khi làm việc với Java Reflection, ứng dụng có thể chỉ biết tên field hoặc method dưới dạng chuỗi ở thời điểm chạy. Những API như `Class.getField`, `Class.getDeclaredField`, `Class.getMethod` và `Class.getDeclaredMethod` sẽ tra cứu metadata của lớp. Nếu **không có thành viên thỏa đúng phạm vi tra cứu và chữ ký yêu cầu**, chúng có thể ném checked exception:

- `java.lang.NoSuchFieldException`: không tìm được field phù hợp với tên và quy tắc tìm kiếm của phương thức được gọi.
- `java.lang.NoSuchMethodException`: không tìm được method/constructor phù hợp với tên, **danh sách kiểu tham số**, phạm vi kế thừa và quy tắc visibility của phương thức tra cứu.

Hai exception đều thuộc họ `ReflectiveOperationException`, nên Java yêu cầu xử lý bằng `try/catch` hoặc khai báo `throws`. Chúng **không đồng nghĩa** với việc class hoàn toàn không có thành viên đó: thành viên có thể tồn tại nhưng là `private`, được khai báo ở superclass, nhận tham số khác, hoặc chỉ có trên một class loader/type khác.

Ví dụ, cùng trường `count` là private trong `Child`:

```java
Child.class.getDeclaredField("count"); // Tìm thấy field private khai báo tại Child
Child.class.getField("count");         // Ném NoSuchFieldException
```

Đây là mảnh minh họa, giả định lớp `Child` như ví dụ đầy đủ bên dưới. Sự khác nhau nằm ở **cách tra cứu**, không phải vì JVM “làm mất” field.

## 2. Bảng chọn đúng API

| Nhu cầu | API | Thành viên được xem xét | Exception nếu không có kết quả |
|---|---|---|---|
| Tìm field `public` có thể kế thừa | `type.getField("name")` | Field public của lớp/interface theo quy tắc tìm kiếm public member, kể cả những field thừa kế hợp lệ | `NoSuchFieldException` |
| Tìm field **khai báo ngay tại lớp** (kể cả private) | `type.getDeclaredField("name")` | Field trong chính `type`, không tự đi lên superclass | `NoSuchFieldException` |
| Tìm method `public` (kể cả inherited) | `type.getMethod("name", ParamType.class)` | Public methods phù hợp theo tên và kiểu tham số | `NoSuchMethodException` |
| Tìm method **khai báo ngay tại lớp** (kể cả private) | `type.getDeclaredMethod("name", ParamType.class)` | Methods của chính `type`, không tự tìm ở superclass | `NoSuchMethodException` |
| Tìm constructor `public` | `type.getConstructor(ParamType.class)` | Public constructor của chính lớp | `NoSuchMethodException` |
| Tìm constructor kể cả `private` | `type.getDeclaredConstructor(ParamType.class)` | Constructor khai báo bởi chính lớp | `NoSuchMethodException` |

**Điểm cần nhớ:** một method có thể được tìm thấy bởi `getDeclaredMethod` nhưng **không truy cập/gọi được** trong hoàn cảnh hiện tại. Bước *tra cứu* khác với bước *cho phép truy cập* và bước *thực thi*.

### “Declared” không có nghĩa là “dùng được mọi nơi”

- `getDeclaredField` và `getDeclaredMethod` trả metadata của thành viên khai báo ở lớp hiện tại, dù `private`, `protected` hay package-private.
- Các quy tắc truy cập vẫn được kiểm tra khi `Field.get/set`, `Method.invoke` hoặc constructor được sử dụng.
- Java Module System có thể chặn hành động cố mở truy cập sâu qua `setAccessible(true)` nếu package không được `opens` phù hợp. Có thể gặp `InaccessibleObjectException` hoặc `IllegalAccessException` tùy hành động.
- Nếu thực sự cần hỗ trợ private member, phải có hợp đồng truy cập rõ ràng; đừng mặc định gọi `setAccessible(true)` là đủ.

## 3. Vì sao NoSuchFieldException xuất hiện?

### 3.1. Tên field không tồn tại hoặc sai chính tả

`getField("userId")` sẽ không tìm ra field tên `userID`. Java phân biệt chữ hoa/chữ thường. Không truyền tên kiểu `"com.example.Person.userId"` hoặc `"Person.userId"`: tham số là **tên thành viên đơn lẻ**, không phải qualified path.

### 3.2. Dùng getField với field không public

`getField` chỉ tìm public field. Một field private có thể tồn tại nhưng lời gọi vẫn ném `NoSuchFieldException`. Nếu ứng dụng sở hữu lớp và thật sự cần metadata private, dùng `getDeclaredField` rồi kiểm tra quyền truy cập riêng.

### 3.3. Dùng getDeclaredField để tìm field kế thừa

`getDeclaredField` chỉ tìm trực tiếp trong `Class` đích. Nếu `Parent` khai báo `public String label`, còn `Child extends Parent` không khai báo lại field đó, `Child.class.getDeclaredField("label")` ném `NoSuchFieldException`. Trong trường hợp public inherited member, `Child.class.getField("label")` phù hợp hơn.

Nếu cần duyệt **mọi mức superclass** (kể cả non-public fields), có thể chủ động lặp `getDeclaredField` từ class hiện tại lên cha. Đừng dùng `getFields()` để mong liệt kê toàn bộ trường private.

### 3.4. Nhầm field với property hoặc accessor

Một JavaBean có thể có `getName()` mà **không có field `name`**, hoặc field có tên khác với property. Với `record`, các record component có accessor method riêng; không đồng nhất mọi property với field public. Hãy xác định đúng metadata cần đọc: field, method, bean property hay record component.

## 4. Vì sao NoSuchMethodException xuất hiện?

### 4.1. Sai tên, phạm vi hoặc visibility

`getMethod` chỉ tìm public method phù hợp, kể cả inherited methods. `getDeclaredMethod` tìm cả method không public nhưng chỉ ở lớp đang tra cứu. Vì vậy method `private void reset()` có thể tìm thấy qua `getDeclaredMethod("reset")` nhưng không qua `getMethod("reset")`.

### 4.2. Danh sách kiểu tham số phải khớp chính xác

`Class.getMethod` và `getDeclaredMethod` nhận tên method và **danh sách `Class<?>` của các tham số khai báo**. Cơ chế tìm kiếm này không thực hiện quy đổi overload giống lời gọi Java thông thường:

- `int.class` **khác** `Integer.class` (không auto-boxing trong tra cứu).
- `String.class` **khác** `Object.class` (không tự chọn overload qua widening/reference conversion).
- `String[].class` là kiểu tham số đã biên dịch của `String...` (varargs), không phải nhiều `String.class` riêng lẻ.
- Với generics, tra cứu theo kiểu **sau type erasure**: ví dụ method nhận `List<String>` thì thường phải tìm với `List.class`. Không tồn tại `List<String>.class`.
- Thứ tự và số lượng tham số phải đúng: `getMethod("move", int.class, long.class)` khác `getMethod("move", long.class, int.class)`.
- Method không tham số: `getMethod("run")` hoặc `getMethod("run", new Class<?>[0])`.

Ví dụ:

```java
// Nếu lớp có greet(String), greet(int), không có greet(Object).
type.getMethod("greet", String.class);  // Tìm được
type.getMethod("greet", int.class);     // Tìm được
type.getMethod("greet", Integer.class); // NoSuchMethodException
type.getMethod("greet", Object.class);  // NoSuchMethodException
```

Ở đây `type` là `Class<Child>` trong ví dụ hoàn chỉnh.

**Kiểu trả về không phải đối số của `getMethod`.** Sau khi tìm được `Method`, dùng `method.getReturnType()` để kiểm tra return type. Với các trường hợp compiler sinh bridge method/covariant return, kết quả tìm kiếm có thể liên quan nhiều method cùng tên và parameter types ở mức bytecode; nếu cần chẩn đoán, xem `isBridge()`, `isSynthetic()` và metadata trả về, thay vì giả định mỗi tên + tham số luôn ánh xạ duy nhất tới một method bytecode.

### 4.3. Tìm nhầm constructor hoặc static initializer

Constructor không có tên như ordinary method để `getMethod("Child")` tra cứu. Dùng `getConstructor(...)` / `getDeclaredConstructor(...)`. Khối `static { ... }` cũng không phải ordinary method public được tìm qua `getMethod`.

### 4.4. Nhìn nhầm interface/class được tải

Hai class loader có thể định nghĩa những kiểu cùng binary name nhưng khác runtime identity. `getMethod("accept", SomeType.class)` sẽ thất bại nếu method thật nhận một `SomeType` khác được nạp bởi loader khác, dù hai `Class.getName()` trông giống nhau. Khi debug hệ plugin, hãy xem cả `getClassLoader()`/`getModule()` và kiểu tham số thực tế.

## 5. Ví dụ Java 17+ chạy độc lập: từ ngoại lệ đến sửa đúng API

Lưu mã sau vào **`MissingMembersDemo.java`**:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MissingMembersDemo {
    static class Parent {
        public String label = "parent";
        public void inherited() {}
    }

    static class Child extends Parent {
        private int count = 7;
        private void reset() {}
        public String greet(String name) { return "Hello, " + name; }
        public String greet(int count) { return "Number: " + count; }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<Child> type = Child.class;
        Field inheritedField = type.getField("label");
        System.out.println("getField(label) owner: " +
                inheritedField.getDeclaringClass().getSimpleName());

        try {
            type.getDeclaredField("label");
        } catch (NoSuchFieldException e) {
            System.out.println("getDeclaredField(label): " + e.getClass().getSimpleName());
        }

        Field count = type.getDeclaredField("count");
        System.out.println("getDeclaredField(count) private: " +
                Modifier.isPrivate(count.getModifiers()));

        try {
            type.getField("count");
        } catch (NoSuchFieldException e) {
            System.out.println("getField(count): " + e.getClass().getSimpleName());
        }

        Method inherited = type.getMethod("inherited");
        System.out.println("getMethod(inherited) owner: " +
                inherited.getDeclaringClass().getSimpleName());

        try {
            type.getDeclaredMethod("inherited");
        } catch (NoSuchMethodException e) {
            System.out.println("getDeclaredMethod(inherited): " + e.getClass().getSimpleName());
        }

        Method reset = type.getDeclaredMethod("reset");
        System.out.println("getDeclaredMethod(reset) private: " +
                Modifier.isPrivate(reset.getModifiers()));

        Method greetString = type.getMethod("greet", String.class);
        System.out.println("getMethod(greet,String) return: " +
                greetString.getReturnType().getSimpleName());

        try {
            type.getMethod("greet", Object.class);
        } catch (NoSuchMethodException e) {
            System.out.println("getMethod(greet,Object): " + e.getClass().getSimpleName());
        }

        try {
            type.getMethod("greet", Integer.class);
        } catch (NoSuchMethodException e) {
            System.out.println("getMethod(greet,Integer): " + e.getClass().getSimpleName());
        }

        System.out.println("getMethod(greet,int) found: " +
                (type.getMethod("greet", int.class) != null));
        System.out.println("invoke greet: " + greetString.invoke(new Child(), "Lan"));
    }
}
```

Chạy trong thư mục có file:

```bash
javac MissingMembersDemo.java
java MissingMembersDemo
```

Kết quả stdout:

```text
getField(label) owner: Parent
getDeclaredField(label): NoSuchFieldException
getDeclaredField(count) private: true
getField(count): NoSuchFieldException
getMethod(inherited) owner: Parent
getDeclaredMethod(inherited): NoSuchMethodException
getDeclaredMethod(reset) private: true
getMethod(greet,String) return: String
getMethod(greet,Object): NoSuchMethodException
getMethod(greet,Integer): NoSuchMethodException
getMethod(greet,int) found: true
invoke greet: Hello, Lan
```

Cách đọc kết quả:

1. `label` thuộc `Parent`: `getField("label")` tìm được public inherited field; `getDeclaredField("label")` thất bại trên `Child`.
2. `count` là private field khai báo ở `Child`: `getDeclaredField` tìm được; `getField` không tìm được.
3. `inherited()` thuộc `Parent`: `getMethod` thấy public inherited method; `getDeclaredMethod` chỉ tra cứu method trực tiếp trong `Child`.
4. `reset()` là private method của `Child`: `getDeclaredMethod` trả metadata; ví dụ chỉ kiểm tra modifiers, không cố gọi method đó qua ranh giới truy cập.
5. `greet` có overload `String` và `int`, không có `Object` hay `Integer`. Hai lần tìm với kiểu không khớp ném `NoSuchMethodException`.
6. `Method.invoke` trên public method được tìm thấy cho ra kết quả. Đây là bước **gọi method**, riêng biệt với bước **tra cứu metadata**.

Ví dụ đã biên dịch và chạy trên JDK 24 với cú pháp tương thích Java 17+. Stdout phía trên là kết quả của chương trình, không chứa tên lớp sinh ngẫu nhiên.

## 6. Quy trình chẩn đoán khi gặp exception

Thay vì sửa tên method/field theo suy đoán, tiến hành có thứ tự.

**Bước 1 — Xác định đúng Class đang tra cứu.** In ra tên, class loader và module của nó khi cần: `type.getName()`, `type.getClassLoader()` và `type.getModule().getName()`. Lưu ý `getClassLoader()` có thể trả `null` với bootstrap loader; tên module cũng có thể `null` với unnamed module.

**Bước 2 — Xác định API và phạm vi.** Nếu truy vấn `getDeclaredMethod` trên class con nhưng method nằm ở cha, đổi sang `getMethod` nếu method public, hoặc chủ động duyệt superclass khi thật sự cần non-public inherited member.

**Bước 3 — Kiểm tra chữ ký chính xác.** Với method, liệt kê `Method.getParameterTypes()` để xem runtime `Class<?>` của từng parameter; không dựa vào chỉ mỗi tên method. Kiểm tra overload, primitive/wrapper, arrays/varargs, generics erasure và method bridge.

**Bước 4 — Kiểm tra visibility và module access.** Nếu tìm thấy member nhưng không thể truy cập, xử lý theo `IllegalAccessException` / `InaccessibleObjectException` hoặc chính sách module, không biến vấn đề đó thành `NoSuchMethodException`.

**Bước 5 — Xác nhận phiên bản và classpath/runtime.** Một dependency được biên dịch ở version mới nhưng thực thi với class version cũ có thể thiếu member. Khi metadata runtime thật sự không có thành viên, điều chỉnh dependency/API hoặc phát hiện capability một cách tường minh.

### Helper liệt kê candidates để debug

Đây là đoạn code minh họa dùng trong một context đã có biến `type` (kiểu `Class<?>`). Nó không phải chương trình chạy độc lập:

```java
for (Method method : type.getDeclaredMethods()) {
    System.out.println(method.toGenericString());
}

for (Field field : type.getDeclaredFields()) {
    System.out.println(field.toGenericString());
}
```

`getDeclaredMethods`/`getDeclaredFields` chỉ liệt kê những thành viên **khai báo trực tiếp** trên `type`. Khi nghi ngờ lỗi do kế thừa, so sánh với `getMethods`/`getFields` hoặc lần theo `getSuperclass()`. Thứ tự phần tử của các mảng Reflection không nên được coi là thứ tự khai báo ổn định.

Để theo dõi tên và parameter types một cách có cấu trúc:

```java
for (Method method : type.getMethods()) {
    if (method.getName().equals("greet")) {
        System.out.println(java.util.Arrays.toString(method.getParameterTypes()));
    }
}
```

Đây cũng là **mảnh minh họa** với `type` đã được định nghĩa. Dùng lọc tên để tránh log toàn bộ API không liên quan và hạn chế làm lộ thông tin nội bộ.

## 7. Phân biệt exception tra cứu và lỗi thực thi

| Hiện tượng | Exception/Error tiêu biểu | Giai đoạn |
|---|---|---|
| Không tìm được field bằng `getField` hoặc `getDeclaredField` | `NoSuchFieldException` (checked) | Tra cứu Reflection |
| Không tìm được method/constructor qua API `Class` | `NoSuchMethodException` (checked) | Tra cứu Reflection |
| Không tìm được chính lớp theo tên chuỗi | `ClassNotFoundException` (checked) | Nạp/tìm class |
| Tìm được member nhưng không có quyền đọc/gọi | `IllegalAccessException`; có thể `InaccessibleObjectException` khi cố mở sâu | Truy cập Reflection |
| Truyền sai đối số cho `Method.invoke` | `IllegalArgumentException` (runtime) | Gọi Reflection |
| Method đích ném exception | `InvocationTargetException` bao lỗi của method đích | Gọi Reflection |
| Bytecode đã biên dịch trỏ tới field/method không còn tương thích khi liên kết/chạy | `NoSuchFieldError` / `NoSuchMethodError` (thuộc `LinkageError`) | Liên kết/phân giải lời gọi bytecode |

**Đặc biệt: `NoSuchMethodException` khác `NoSuchMethodError`.** Exception là kết quả của một **truy vấn Reflection không tìm ra member**; Error thường phản ánh **mất tương thích nhị phân** hoặc dependency runtime không như lúc biên dịch. Không sửa `NoSuchMethodError` bằng cách bắt `NoSuchMethodException` và coi là hết lỗi. Với `NoSuchFieldError` cũng tương tự.

`NoSuchMethodException` còn có thể xuất hiện khi tra cứu constructor qua `getConstructor`/`getDeclaredConstructor` dù tên exception chứa chữ “Method”; không có `NoSuchConstructorException` riêng trong các API này.

## 8. Xử lý lỗi trong ứng dụng thật

### Bắt đúng loại lỗi và ghi lại dữ liệu chẩn đoán hữu ích

```java
try {
    java.lang.reflect.Method method =
            componentType.getMethod("execute", String.class);
    // Tiếp tục dùng method trong luồng đã kiểm soát.
} catch (NoSuchMethodException e) {
    throw new IllegalStateException(
            "Thiếu public method execute(String) trên " +
                    componentType.getName(), e);
}
```

Đây là **mảnh code minh họa**: `componentType` là biến `Class<?>` trong ứng dụng; `componentType.getMethod` chỉ xác nhận metadata. Giữ exception làm `cause` để không làm mất stack trace. Trong hệ plugin hoặc thư viện có nhiều loader, thêm tên loader/module vào log chẩn đoán nếu có ích.

### Chọn fallback khi tính năng thực sự là tùy chọn

Một số thư viện hỗ trợ nhiều phiên bản API có thể dùng Reflection để kiểm tra method mới. Khi đó **chỉ fallback với trường hợp đã dự tính**, không nuốt mọi `ReflectiveOperationException`:

```java
boolean available;
try {
    apiType.getMethod("newFeature");
    available = true;
} catch (NoSuchMethodException e) {
    available = false;
}
```

Mảnh minh họa giả định `apiType` đã có. Điều kiện `available=false` nói rằng method `public newFeature()` **không tra cứu được theo chữ ký không tham số trên `apiType`**, chứ không chứng minh tính năng hoàn toàn không tồn tại. Phải xác định fallback hợp lệ và phiên bản API hỗ trợ trước khi dùng.

Không nên:

- `catch (Exception ignored)` rồi âm thầm bỏ qua lỗi cấu hình, classpath hoặc truy cập.
- Mặc định dùng `getDeclaredMethod` với `setAccessible(true)` để phá giới hạn encapsulation chỉ nhằm xử lý exception.
- Duyệt method theo thứ tự bất kỳ và gọi phần tử đầu tiên trùng tên mà không kiểm tra chữ ký.
- Bỏ qua vấn đề class loader, đặc biệt ở container, plugin hoặc application server.

## 9. Trường hợp nâng cao: field hiding, overload và version drift

**Field hiding:** nếu subclass khai báo field trùng tên với superclass, đó là hai field khác nhau có declaring class khác nhau. `getDeclaredField` trên subclass trả field do subclass khai báo; muốn truy vấn field của cha hãy gọi `Superclass.class.getDeclaredField(...)`. Không giả định chỉ có một field trùng tên trong toàn bộ hierarchy.

**Method overload:** việc trong class tồn tại method `process(int)` không làm `getMethod("process", Integer.class)` thành công. Reflection lookup không chạy Java overload resolution, boxing hoặc varargs packing giúp bạn.

**Varargs:** với `void log(String... messages)`, danh sách parameter types chỉ gồm `String[].class`. Ví dụ tra cứu đúng là `getMethod("log", String[].class)`. Khi gọi qua `Method.invoke` còn có quy tắc truyền mảng Object[] và varargs riêng; tách việc tìm chữ ký khỏi việc chuẩn bị arguments.

**Generics:** compiler xóa phần lớn type arguments nên lookup cần dùng erased parameter class. Khi thư viện/framework sinh bridge method, metadata có thể chứa method `isBridge() == true`; nên phân tích đầy đủ thay vì lấy method đầu tiên cùng tên.

**Version drift:** `getDeclaredMethod` thất bại có thể do library đã đổi API, method được chuyển lên parent class, signature thay đổi hoặc runtime đang dùng JAR khác. Kiểm tra dependency lock/version và class loader thực tế trước khi vá reflection string.

## 10. Checklist thực hành

1. Xác nhận **đúng class** và module/loader cần tra cứu.
2. Chọn `get...` khi cần public inherited member, `getDeclared...` khi cần khai báo trực tiếp.
3. Với method/constructor, đối chiếu **số lượng, thứ tự và Class của từng tham số**, bao gồm primitive, array và erased generic types.
4. Đừng nhầm lỗi “không tìm thấy” với lỗi truy cập hoặc exception do method đích ném.
5. Khi chỉ kiểm tra API tùy chọn, fallback có chủ đích và ghi được nguyên nhân.
6. Khi API thay đổi phiên bản, ưu tiên cập nhật dependency và sử dụng API công khai ổn định.
7. Viết test cho cả đường tra cứu thành công và trường hợp thiếu member được dự tính.

## 11. Tóm tắt

- `NoSuchFieldException` là checked exception khi **tra cứu field** không khớp tên/phạm vi.
- `NoSuchMethodException` là checked exception khi **tra cứu method hoặc constructor** không khớp tên, parameter types hay phạm vi.
- `getField/getMethod` hướng tới **public members** (có xét kế thừa hợp lệ); `getDeclaredField/getDeclaredMethod` hướng tới **members khai báo trực tiếp** bất kể visibility.
- Reflection yêu cầu parameter `Class<?>` **khớp chính xác**; không áp dụng boxing, widening hoặc overload resolution tự động.
- Tìm thấy metadata chưa chứng minh có quyền truy cập hoặc gọi thành công; `NoSuchMethodError` và `NoSuchFieldError` là **linkage errors khác bản chất**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exceptions-diagnostics-003">IllegalAccessException khác InaccessibleObjectException</a>

<details>
<summary>Xem chi tiết</summary>

# IllegalAccessException khác InaccessibleObjectException như thế nào trong Java Reflection?

Khi dùng Reflection để đọc trường, gọi method hoặc tạo đối tượng, lập trình viên thường gặp hai lỗi có tên gần giống nhau:

- `java.lang.IllegalAccessException`: thao tác Reflection **không được phép truy cập một member** theo các quy tắc access đang áp dụng.
- `java.lang.reflect.InaccessibleObjectException`: chương trình **cố yêu cầu bỏ qua access check bằng Reflection nhưng JVM không cho phép bật quyền đó**, thường do tính đóng gói mạnh của Java Platform Module System (JPMS).

Hai exception giải quyết **hai câu hỏi khác nhau**. `IllegalAccessException` thường xảy ra lúc **thực hiện thao tác lên member**; `InaccessibleObjectException` thường xảy ra khi **cố mở quyền Reflection trước khi thao tác**, chẳng hạn với `setAccessible(true)`. Chúng có thể liên quan đến cùng một member, nhưng không đồng nghĩa và không thể sửa bằng cùng một biện pháp trong mọi trường hợp.

## 1. Nắm mô hình access trước khi xem lỗi

Trong Java có hai lớp quy tắc quan trọng:

1. **Quyền truy cập của ngôn ngữ Java**: `public`, `protected`, package-private (không ghi modifier), `private` và quan hệ class/package/subclass/nestmate.
2. **Ranh giới module của JPMS (Java 9+)**: một package thuộc named module có thể được **export** để hỗ trợ truy cập API công khai, hoặc được **open** để cho phép Reflection truy cập sâu hơn vào các member không public.

Reflection có thể tìm ra metadata về một member bằng `getDeclaredMethod(...)`, `getDeclaredField(...)` hoặc `getDeclaredConstructor(...)` dù việc **gọi/đọc/ghi** member đó vẫn bị từ chối. “Nhìn thấy metadata” không đồng nghĩa “được quyền dùng member”.

Ví dụ:

```java
Method method = type.getDeclaredMethod("secret"); // Lấy metadata.
method.invoke(target);                            // Thực hiện lời gọi; có thể thất bại.
```

Đây là **đoạn minh họa**, giả định `type` và `target` đã tồn tại. Lúc `invoke`, JVM kiểm tra quyền của caller nếu access checks chưa được bỏ qua hợp lệ.

### Vai trò của AccessibleObject

`Method`, `Field` và `Constructor` kế thừa `AccessibleObject`. Các API có ý nghĩa khác nhau:

- `canAccess(receiver)`: kiểm tra caller hiện tại có quyền truy cập member với receiver tương ứng hay không. Với static member/constructor, receiver cần dùng theo hợp đồng riêng của API, thường là `null`.
- `trySetAccessible()`: thử bật chế độ bỏ qua access checks cho đối tượng Reflection; trả `true` nếu thành công, `false` nếu không thể bật do ràng buộc truy cập. Một số kiểm tra bảo mật riêng vẫn có thể ném exception.
- `setAccessible(true)`: yêu cầu bỏ qua access checks; nếu không thể bật vì quy tắc module, có thể ném `InaccessibleObjectException`.
- `setAccessible(false)`: tắt việc bỏ qua access checks đã bật trên đối tượng Reflection ấy; **không** sửa modifier của class/member.

Tên `setAccessible(true)` dễ gây hiểu nhầm: đây **không phải** lệnh biến method `private` thành `public` hoặc mở package toàn cục. Nó chỉ tác động đến đối tượng `AccessibleObject` theo quyền JVM cho phép.

## 2. IllegalAccessException: bị chặn khi đọc, ghi, gọi hoặc tạo đối tượng

`IllegalAccessException` là **checked exception** kế thừa `ReflectiveOperationException`. Các thao tác như sau có thể ném exception này khi caller không được phép truy cập:

- `Method.invoke(target, args)` khi method hoặc class khai báo không khả dụng với caller.
- `Field.get(target)` hoặc `Field.set(target, value)` khi quyền đọc/ghi qua Reflection không được cấp. Với trường `final` còn có các giới hạn ghi riêng.
- `Constructor.newInstance(args)` khi constructor không khả dụng với caller.

Ví dụ `getDeclaredMethod("secret")` có thể trả về một `Method` mô tả method private. Nếu method nằm trong một class không cùng nest và caller không có quyền truy cập, `invoke` sẽ ném `IllegalAccessException`, trừ khi caller đã bật chế độ bỏ qua access checks một cách hợp lệ.

Điểm cần phân biệt:

- `IllegalAccessException` đến từ **quyền sử dụng member khi thực hiện Reflection**.
- `IllegalAccessError` là một **LinkageError**, thường phát sinh khi bytecode tham chiếu đến class/member không thể truy cập theo ràng buộc liên kết runtime; đây **không phải** exception checked của `Method.invoke`.
- Lỗi biên dịch Java kiểu “has private access” cũng **không phải** `IllegalAccessException` của Reflection: compiler có thể từ chối mã truy cập trực tiếp trước khi chạy.

### Cách chẩn đoán

Đầu tiên kiểm tra class khai báo member, modifier và ngữ cảnh caller:

```java
Method method = target.getClass().getDeclaredMethod("secret");
System.out.println(method.getDeclaringClass().getName());
System.out.println(Modifier.toString(method.getModifiers()));
```

Đây là **đoạn minh họa**, cần `import java.lang.reflect.Method;` và `import java.lang.reflect.Modifier;`, giả định `target` đã tồn tại và method đúng tên. `getDeclaredMethod` **không** tự tìm method được kế thừa từ superclass; dùng `getMethod` khi muốn tìm public member theo hợp đồng của API.

Nếu có thể, hãy sử dụng API `public` được thiết kế cho caller. Nếu phải gọi một member không public trong code có kiểm soát, có thể cân nhắc `trySetAccessible()` nhưng phải xem xét ranh giới module và quyền truy cập.

## 3. InaccessibleObjectException: thất bại khi yêu cầu truy cập sâu

`InaccessibleObjectException` là **unchecked exception** kế thừa `RuntimeException`, thuộc package `java.lang.reflect`. Trường hợp phổ biến:

```java
Method method = someClass.getDeclaredMethod("hidden");
method.setAccessible(true); // Có thể ném InaccessibleObjectException.
```

Đây là **đoạn minh họa**. Trên Java 17+, nhiều package nội bộ của JDK thuộc named module và không được mở cho mọi caller. Việc `getDeclaredMethod` tìm được metadata **không** có nghĩa JVM cho phép `setAccessible(true)`.

Ví dụ cụ thể: package `java.lang` của module `java.base` được export để sử dụng API public, nhưng **không mặc nhiên được open** cho ứng dụng ở unnamed module để truy cập sâu vào protected/private member của JDK. Vì vậy việc cố mở `ClassLoader#defineClass(...)` bằng Reflection thường bị từ chối khi chạy Java 17 với cấu hình module mặc định.

Khác với `IllegalAccessException` xảy ra khi `invoke/get/set/newInstance` không có quyền, `InaccessibleObjectException` chỉ ra rằng **chính hành động yêu cầu vượt qua access check không được chấp nhận**. Chưa cần gọi member thật cũng có thể gặp lỗi.

**Quan trọng:** `setAccessible(true)` có thể thành công với private member trong **cùng module** hoặc trong package được open phù hợp, tùy quy tắc API. Vì vậy không được kết luận “Java 17 luôn cấm Reflection với private member”. Java 17 vẫn cho phép deep reflection trong nhiều trường hợp; điểm khác biệt là phạm vi access và module.

## 4. Ví dụ Java 17+ chạy được: hai exception, hai thời điểm

Ví dụ sau không cần thư viện ngoài. Nó đặt `SecretBox` thành một **top-level class khác** trong cùng file, thay vì nested class cùng nest, để trình diễn `IllegalAccessException` một cách rõ ràng. Sau đó nó thử truy cập một protected method của JDK để quan sát `InaccessibleObjectException`.

Lưu **toàn bộ** mã vào file `AccessErrorsDemo.java`:

```java
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;

public class AccessErrorsDemo {
    public static void main(String[] args) throws ReflectiveOperationException {
        Method local = SecretBox.class.getDeclaredMethod("reveal");
        System.out.println("lookup: " + local.getName());

        try {
            local.invoke(new SecretBox());
        } catch (IllegalAccessException ex) {
            System.out.println("invoke before suppression: "
                    + ex.getClass().getSimpleName());
        }

        System.out.println("local trySetAccessible: "
                + local.trySetAccessible());
        System.out.println("invoke after suppression: "
                + local.invoke(new SecretBox()));

        Method defineClass = ClassLoader.class.getDeclaredMethod(
                "defineClass", String.class, byte[].class, int.class, int.class);
        System.out.println("JDK trySetAccessible: "
                + defineClass.trySetAccessible());

        try {
            defineClass.setAccessible(true);
            System.out.println("setAccessible JDK: granted");
        } catch (InaccessibleObjectException ex) {
            System.out.println("setAccessible JDK: "
                    + ex.getClass().getSimpleName());
        }
    }
}

class SecretBox {
    private String reveal() {
        return "secret value";
    }
}
```

Biên dịch/chạy bằng **JDK 17+**, với khởi chạy **mặc định** (không cấp thêm `--add-opens`):

```bash
javac --release 17 -encoding UTF-8 AccessErrorsDemo.java
java AccessErrorsDemo
```

Stdout trên OpenJDK với cấu hình module mặc định:

```text
lookup: reveal
invoke before suppression: IllegalAccessException
local trySetAccessible: true
invoke after suppression: secret value
JDK trySetAccessible: false
setAccessible JDK: InaccessibleObjectException
```

**Đọc kết quả theo thứ tự:**

1. `getDeclaredMethod("reveal")` tìm được metadata của private method, chưa gọi method và chưa thay đổi access.
2. `local.invoke(new SecretBox())` thất bại với `IllegalAccessException` vì `AccessErrorsDemo` và `SecretBox` là hai top-level class **khác nest**, và method `reveal()` là `private`.
3. `local.trySetAccessible()` thành công vì cả hai class thuộc cùng unnamed module, nơi JVM cho phép bỏ qua access checks cho tình huống này.
4. `invoke` lần hai trả `secret value`: việc truy cập private member giờ hợp lệ đối với đối tượng `Method` đã được mở.
5. `ClassLoader#defineClass(String, byte[], int, int)` là protected method trong package `java.lang` thuộc named module `java.base`. Chương trình ở unnamed module không được cấp deep reflection vào package này theo cấu hình mặc định, nên `trySetAccessible()` trả `false`.
6. Cùng member JDK ấy, `setAccessible(true)` ném `InaccessibleObjectException`. Không có lời gọi `defineClass(...)` nào được thực hiện.

**Vì sao có thể đổi kết quả?** Nếu JVM được chạy với tham số mở module, ví dụ:

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED AccessErrorsDemo
```

Thì thao tác mở `ClassLoader#defineClass` có thể thành công: `JDK trySetAccessible` thành `true` và thông báo `setAccessible JDK: granted`. Điều đó minh họa vai trò của module openness. Tùy môi trường/runtime, các cờ mở package sẵn có cũng ảnh hưởng kết quả; không dùng stdout của ví dụ như lời khẳng định cho mọi cấu hình JVM.

**Không dùng `--add-opens` bừa bãi trong production** chỉ để làm mất exception. Đó là thay đổi ranh giới encapsulation, có thể phụ thuộc mạnh vào chi tiết JDK.

## 5. JPMS: exports khác opens thế nào?

Xét named module `billing.core` với package `billing.api` chứa class và method public, cùng package `billing.internal` có private field cần Reflection.

Một file `module-info.java` *minh họa*:

```java
module billing.core {
    exports billing.api;
    opens billing.internal to billing.tool;
}
```

Ý nghĩa:

- `exports billing.api;` cho các module khác sử dụng **public API** của `billing.api` theo luật module và Java access. Nó **không** tự cấp quyền deep reflection tới private field.
- `opens billing.internal to billing.tool;` cho phép module `billing.tool` thực hiện deep reflection vào package `billing.internal` theo điều kiện của API. Nó **không** đồng nghĩa cho module đó import mọi class package-private như API công khai.
- `open module billing.core { ... }` là cách mở mọi package của module cho Reflection; phạm vi rộng hơn và cần cân nhắc thiết kế.
- Một package có thể được both exported và opened; tác dụng của hai khai báo khác nhau.

Đây là **đoạn cấu hình minh họa**, không phải dự án module có thể chạy riêng; module `billing.tool` và các package cần hiện hữu để biên dịch ví dụ.

### Các biến thể ở command line

Khi không thể thay file `module-info.java`, JVM có tùy chọn:

```bash
java --add-opens billing.core/billing.internal=billing.tool \
     --module-path mods \
     --module billing.tool/billing.tool.Main
```

Lệnh trên minh họa cho shell hỗ trợ ký tự `\` nối dòng, giả định `mods` chứa các module đã biên dịch và main class chính xác. Cùng ý tưởng nhưng dùng `ALL-UNNAMED` nếu caller thuộc unnamed module.

**Phân biệt:**

- `--add-exports module/package=target`: bổ sung quyền export, phục vụ truy cập API công khai theo quy tắc access.
- `--add-opens module/package=target`: bổ sung quyền open package cho deep reflection, phù hợp hơn khi vấn đề là `setAccessible(true)` bị chặn bởi JPMS.

`--add-exports` đơn độc **không** đủ để mở các private member của package trong named module. `--add-opens` cũng **không** làm `private` thành public theo lời gọi Java thông thường.

### Unnamed module và same-module access

Ứng dụng chạy trên classpath thường thuộc unnamed module. Theo mô hình JPMS, package của unnamed module được coi là export và open rộng; vì vậy bài demo có thể bật access cho `SecretBox.reveal()`. Trong named module, Reflection vào member không public **bên trong cùng module** cũng có các trường hợp được phép bật access. Nhưng khi đi xuyên sang một named module khác có package không open, `setAccessible(true)` thường bị từ chối.

Cần kiểm tra **module của caller**, **module của declaring class** và trạng thái export/open của **package**; chỉ biết modifier `private` là chưa đủ để dự đoán `InaccessibleObjectException`.

## 6. Bảng so sánh nhanh

| Tiêu chí | `IllegalAccessException` | `InaccessibleObjectException` |
|---|---|---|
| Package | `java.lang` | `java.lang.reflect` |
| Loại | **Checked**, kế thừa `ReflectiveOperationException` | **Unchecked**, kế thừa `RuntimeException` |
| Bước hay xảy ra | `Method.invoke`, `Field.get/set`, `Constructor.newInstance` | `AccessibleObject.setAccessible(true)` |
| Câu hỏi JVM đang trả lời | “Caller có được phép thực hiện thao tác với member này không?” | “Có được phép bỏ qua access checks cho member này không?” |
| Nguyên nhân phổ biến | Private/protected/package-private hoặc class không accessible; chưa bật suppression hợp lệ | Package thuộc named module không open cho caller; quy tắc deep reflection từ chối |
| `trySetAccessible()` | Có thể thành công và cho phép thao tác về sau | Thường trả `false` trong tình huống module cản trở |
| Hướng xử lý ưu tiên | Dùng member public/contract phù hợp, sửa cách gọi, hoặc suppression hợp lệ | Mở package một cách chủ đích hoặc ngừng deep reflection vào internals |
| Điều không nên làm | Bắt rồi bỏ qua exception và giả định method đã được gọi | Bắt rồi tiếp tục coi `setAccessible(true)` đã thành công |

**Một member có thể dẫn tới cả hai exception tùy đường chạy:** nếu bỏ qua bước `setAccessible` và cố `invoke`, chương trình có thể gặp `IllegalAccessException`; nếu trước đó cố `setAccessible(true)` trong package bị JPMS khóa, chương trình có thể gặp `InaccessibleObjectException`. Không phải cùng lúc bắt buộc ném cả hai.

## 7. Quy trình chẩn đoán lỗi thực tế

### Bước 1: Xác định API ném exception

Đọc **stack trace đầy đủ** và chỉ ra call site đầu tiên từ code của bạn:

- `invoke/get/set/newInstance` → tập trung quyền truy cập member, modifiers và receiver.
- `setAccessible(true)` → kiểm tra quyền bypass và tình trạng package/module.
- `getDeclaredMethod/getDeclaredField` thất bại với `NoSuchMethodException/NoSuchFieldException` → đó là lỗi lookup khác, đừng gộp thành một trong hai access exception.

Không nên chỉ grep chữ “access” trong message rồi đoán lỗi.

### Bước 2: Kiểm tra metadata của member và module

Một số API có ích:

```java
Class<?> owner = method.getDeclaringClass();
Module module = owner.getModule();
String packageName = owner.getPackageName();

System.out.println("owner = " + owner.getName());
System.out.println("module = " + module.getName());
System.out.println("modifiers = "
        + java.lang.reflect.Modifier.toString(method.getModifiers()));
System.out.println("package = " + packageName);
```

Đây là **đoạn minh họa**, giả định biến `method` kiểu `Method` đã tồn tại. `module.getName()` có thể là `null` với unnamed module. Để kiểm tra chính xác quyền của caller, xem thêm `Class::getModule` trên caller và `Module::isExported`, `Module::isOpen` với tên package/target module thích hợp.

### Bước 3: Đánh giá cách mở access hợp lệ

Nếu thực sự có chủ đích dùng Reflection với member không public:

```java
if (!method.trySetAccessible()) {
    throw new IllegalStateException(
            "Member is not open for deep reflection: " + method);
}
Object result = method.invoke(target);
```

Đây là **đoạn minh họa**, giả định method không static và `target` phù hợp; thêm xử lý các checked exceptions của `invoke` trong code gọi. `trySetAccessible()` giúp xử lý nhánh không được mở mà không phải phụ thuộc vào exception `InaccessibleObjectException` từ `setAccessible(true)`. Nó **không** bảo đảm việc gọi method sau đó thành công: method có thể ném exception, receiver sai, đối số sai hoặc gặp các lỗi runtime khác.

### Bước 4: Sửa theo đúng lớp nguyên nhân

- Nếu API có public method/getter phù hợp, hãy ưu tiên dùng API đó.
- Nếu class nằm trong code mình kiểm soát, cân nhắc điều chỉnh visibility/contract hoặc tổ chức lại code; không mở rộng quyền chỉ vì một công cụ Reflection.
- Nếu package thuộc **named module do mình sở hữu**, mở có chọn lọc bằng `opens package to module;` khi thực sự cần deep reflection (ví dụ serializer/framework).
- Nếu module thuộc **thư viện/JDK**, xem tài liệu API và khả năng dùng public API thay thế; `--add-opens` chỉ nên là giải pháp có chủ đích trong môi trường được kiểm soát.
- Nếu exception xuất hiện sau khi nâng từ Java 8 lên Java 17+, kiểm tra kỹ module encapsulation và thư viện đang cố truy cập JDK internals; cập nhật thư viện thường bền vững hơn can thiệp JVM flags.

## 8. Những lỗi chẩn đoán phổ biến

**Nhầm 1: “Có `getDeclaredMethod` nghĩa là gọi được.”** Sai. Lookup và access check là hai bước khác nhau.

**Nhầm 2: “Gọi `setAccessible(true)` luôn sửa `IllegalAccessException`.”** Sai. Nếu module không mở package tương ứng, chính lời gọi này có thể ném `InaccessibleObjectException`.

**Nhầm 3: “Java 17 cấm truy cập private bằng Reflection.”** Sai. Các private member cùng module hoặc trong package được open vẫn có trường hợp cho phép suppression.

**Nhầm 4: “Thêm `exports` là đủ để đọc private field.”** Sai. `exports` và `opens` có mục đích khác nhau.

**Nhầm 5: “`InaccessibleObjectException` là checked, cần khai báo `throws`.”** Sai. Nó là unchecked exception; trong khi `IllegalAccessException` là checked.

**Nhầm 6: “Bắt cả hai exception và bỏ qua là xử lý xong.”** Sai. Quyền truy cập **chưa được cấp** và operation có thể chưa được thực hiện; bắt rồi bỏ qua có thể che giấu lỗi logic hoặc bảo mật.

**Nhầm 7: “Mọi exception từ `Method.invoke` đều là IllegalAccessException.”** Sai. Nếu method thật ném exception, Reflection thường bọc bằng `InvocationTargetException`; sai đối số có thể là `IllegalArgumentException`; lỗi khởi tạo class hoặc linkage có cơ chế khác.

**Nhầm 8: “Một method private trong nested class luôn gây IllegalAccessException.”** Không hẳn. Java có **nest-based access control**: các nestmate có thể truy cập private member của nhau theo quy tắc ngôn ngữ/JVM. Đó là lý do ví dụ runnable dùng hai **top-level class** khác nhau.

## 9. Câu hỏi tự kiểm tra

**Câu 1.** `getDeclaredField("password")` trả về `Field` thành công; ngay sau đó `field.get(target)` ném `IllegalAccessException`. Tại sao?

**Trả lời:** Lookup metadata thành công, nhưng caller không có quyền thực hiện thao tác đọc member. Cần kiểm tra declaring class/member và access checks.

**Câu 2.** `field.setAccessible(true)` ném `InaccessibleObjectException` trên Java 17. Có chắc chỉ cần đổi field thành `public` không?

**Trả lời:** Không. Phải xét named module và quyền export/open của package; thay modifier riêng có thể không đủ. Ưu tiên public API hoặc cấu hình openness có chủ đích.

**Câu 3.** `trySetAccessible()` trả `false`: có nên gọi tiếp `field.get(...)` như thể đã được mở quyền không?

**Trả lời:** Không. Lời gọi mở access đã thất bại; cần xử lý nhánh này, hoặc xác minh caller thực ra có quyền qua Java access checks thông thường bằng `canAccess(...)`.

**Câu 4.** Vì sao `--add-exports java.base/java.lang=ALL-UNNAMED` không phải cách tương đương với `--add-opens java.base/java.lang=ALL-UNNAMED`?

**Trả lời:** Export phục vụ khả năng sử dụng thành phần public; open cho phép deep reflection theo quy tắc JPMS. Chúng không đồng nghĩa.

**Câu 5.** Nếu code Java 8 dùng Reflection truy cập các internal field của JDK và lên Java 17 bị lỗi, nên kiểm tra gì đầu tiên?

**Trả lời:** Stack trace, thư viện/framework nào đang dùng internal reflection, public API có thể thay thế, ranh giới module và khả năng cập nhật thư viện; chỉ dùng JVM opening flags sau khi hiểu mục đích.

## Tài liệu chuẩn

- Java SE API: [`IllegalAccessException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IllegalAccessException.html).
- Java SE API: [`InaccessibleObjectException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InaccessibleObjectException.html).
- Java SE API: [`AccessibleObject`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html) — `setAccessible`, `trySetAccessible`, `canAccess`.
- Java SE API: `Method.invoke`, `Field.get/set`, `Constructor.newInstance`, `Module.isOpen/isExported`.
- [JEP 403 — Strongly Encapsulate JDK Internals](https://openjdk.org/jeps/403), có hiệu lực từ JDK 17.

**Ghi nhớ:** `IllegalAccessException` cho biết **thao tác Reflection trên member không được phép**; `InaccessibleObjectException` cho biết **nỗ lực mở quyền deep reflection bị từ chối**. Muốn xử lý đúng, xác định **API ném lỗi**, **modifier/member**, **module/package của caller và declaring class**, sau đó chọn public API hoặc mở quyền một cách chủ đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exceptions-diagnostics-004">IllegalArgumentException do receiver hoặc đối số không hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# `IllegalArgumentException` do receiver hoặc đối số không hợp lệ trong Java Reflection

## 1. Bản chất lỗi: thất bại ở bước áp dụng lời gọi phản chiếu

Khi dùng Java Reflection, chương trình thường tìm được một đối tượng `java.lang.reflect.Method`, `Constructor<?>` hoặc `Field` rồi mới thao tác trên object cụ thể. **Tìm thấy metadata không bảo đảm có thể áp dụng thành viên đó cho mọi receiver hoặc mọi danh sách đối số.**

`IllegalArgumentException` là ngoại lệ *unchecked* thường báo rằng lời gọi API reflection được đưa dữ liệu không đúng hợp đồng: receiver không thuộc kiểu được phép, sai số lượng tham số, không thể chuyển kiểu đối số, hoặc giá trị ghi vào field không phù hợp. Lỗi này có thể xuất hiện **trước khi mã của phương thức/constructor đích chạy**.

Ví dụ `Method` đại diện cho `Service.sum(int, long)` chỉ biết phương thức nào cần gọi. Để `Method.invoke` thành công, còn cần receiver là một instance thích hợp của `Service` và **hai đối số** lần lượt tương thích với `int`, `long`.

Quan trọng: **`IllegalArgumentException` do reflection kiểm tra đầu vào** khác với **`IllegalArgumentException` được chính phương thức đích ném ra**. Khi gọi qua `Method.invoke`, ngoại lệ từ phương thức đích được bọc trong `InvocationTargetException`. Phải phân biệt hai nhánh để xử lý và chẩn đoán đúng.

## 2. `Method.invoke` kiểm tra receiver ra sao?

Chữ ký cơ bản:

```java
Object result = method.invoke(receiver, arguments);
```

Đây là fragment minh họa; `method` đã được tìm trước đó. `receiver` còn gọi là *target object*:

- Với **instance method**, receiver phải là đối tượng của declaring class hoặc lớp con/implementation phù hợp. Truyền đối tượng không liên quan sẽ dẫn đến `IllegalArgumentException`.
- Với **instance method**, truyền `null` làm receiver sẽ dẫn đến **`NullPointerException`**, không phải `IllegalArgumentException`. Không nên gom hai tình huống này thành một loại lỗi.
- Với **static method**, `Method.invoke` **bỏ qua đối số receiver**; truyền `null` là cách diễn đạt rõ ràng. Không kết luận một static method sẽ lỗi chỉ vì receiver không phải object của declaring class.
- Nếu receiver có kiểu hợp lệ nhưng quyền truy cập bị từ chối, đó là một nhóm lỗi khác, thường `IllegalAccessException`.

Ví dụ fragment:

```java
class Parent {
    public String name() { return "parent"; }
}
class Child extends Parent {}
class Unrelated {}

// Đặt trong một method có throws ReflectiveOperationException:
java.lang.reflect.Method m = Parent.class.getMethod("name");

System.out.println(m.invoke(new Parent())); // parent
System.out.println(m.invoke(new Child()));  // parent
// m.invoke(new Unrelated());                // IllegalArgumentException
// m.invoke(null);                           // NullPointerException
```

`m.getDeclaringClass()` cho biết lớp khai báo method, còn `m.getModifiers()` cho biết method có `static` hay không. Một bước kiểm tra hữu ích trước khi gọi instance method là:

```java
if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
    if (receiver == null) {
        throw new IllegalArgumentException("Receiver phải khác null");
    }
    if (!m.getDeclaringClass().isInstance(receiver)) {
        throw new IllegalArgumentException(
                "Receiver không tương thích với " + m.getDeclaringClass().getName());
    }
}
```

Đây là **validation do ứng dụng chủ động thiết kế**, nên chính đoạn `throw new IllegalArgumentException(...)` có thể thay đổi cách phân loại lỗi `null` ở tầng ứng dụng. Nó không làm thay đổi hợp đồng chuẩn của `Method.invoke(null, ...)` đối với instance method.

## 3. Đối số: sai số lượng, sai thứ tự, sai kiểu

Để chẩn đoán một `Method.invoke` bị `IllegalArgumentException`, kiểm tra theo thứ tự:

1. **Số lượng tham số:** `method.getParameterCount()` phải khớp số lượng đối số thực tế mà reflection nhận. Không được mặc định rằng phương thức có `varargs` thì `Method.invoke` tự gom mọi đối số rời thành array.
2. **Kiểu khai báo:** dùng `method.getParameterTypes()` để biết các loại kiểu ở runtime; với generic, `getGenericParameterTypes()` giúp xem metadata khai báo, nhưng kiểm tra gọi thông thường diễn ra trên kiểu đã erasure.
3. **Giá trị `null`:** có thể truyền vào tham số reference; không hợp lệ cho tham số primitive như `int` hay `boolean`.
4. **Primitive conversion:** reflection hỗ trợ unboxing wrapper và **widening primitive** thích hợp (chẳng hạn `Short` sang `int`, `Integer` sang `long`); không tự làm narrowing (`Long` sang `int`) hoặc chuyển tùy tiện từ `String` sang số.
5. **Reference conversion:** một đối tượng cần tương thích với kiểu tham số reference. Không được nhầm `Object[]` (container chứa danh sách đối số của reflection) với `String[]` (một đối số được khai báo của method đích).

Ví dụ `sum(int, long)`:

| Lời gọi qua `Method.invoke` | Kết quả |
| --- | --- |
| `sum.invoke(service, 2, 3L)` | Hợp lệ; `Integer` unbox thành `int`, `Long` thành `long` |
| `sum.invoke(service, Short.valueOf((short) 2), Integer.valueOf(3))` | Hợp lệ; unbox rồi widening primitive |
| `sum.invoke(service, 2)` | `IllegalArgumentException` vì thiếu đối số |
| `sum.invoke(service, 2, 3L, 4)` | `IllegalArgumentException` vì thừa đối số |
| `sum.invoke(service, 2L, 3L)` | `IllegalArgumentException` vì `long` không narrowing thành `int` |
| `sum.invoke(service, null, 3L)` | `IllegalArgumentException` vì `int` không nhận `null` |
| `sum.invoke(service, "2", 3L)` | `IllegalArgumentException` vì không tự parse `String` thành `int` |

Reflection không thực hiện overload resolution như khi biên dịch lời gọi Java thông thường. Ví dụ nếu một class có `f(int)` và `f(long)`, `getMethod("f", int.class)` xác định **đúng một overload**; sau đó `invoke` không tự chọn lại overload khác chỉ vì nhận đối số `Long`. Chọn đúng chữ ký ngay lúc tra cứu.

## 4. Bẫy `Object...`: truyền tham số dạng mảng và varargs

`Method.invoke` có chữ ký `invoke(Object obj, Object... args)`. Dấu `...` này thuộc **API reflection**, không có nghĩa tham số varargs của *method đích* được đóng gói tự động.

Giả sử method đích:

```java
public String join(String... items) {
    return String.join("|", items);
}
```

Tại runtime, chữ ký thật của nó là `join(String[])`. Do đó:

```java
Method join = Service.class.getMethod("join", String[].class);

// Đúng: mảng String[] là MỘT đối số của method đích.
Object ok = join.invoke(service, (Object) new String[] {"a", "b"});
System.out.println(ok); // a|b

// Sai: đưa HAI đối số String riêng lẻ vào reflection.
// join.invoke(service, "a", "b"); // IllegalArgumentException
```

Fragment ở trên cần import `java.lang.reflect.Method`, đồng thời `Service` và `service` đã được khai báo ở ngữ cảnh phù hợp. Ép sang `(Object)` ngăn Java compiler trải `String[]` thành nhiều phần tử cho varargs `Object...` của `Method.invoke`. Cách tương đương, tường minh hơn là:

```java
join.invoke(service, new Object[] {new String[] {"a", "b"}});
```

Khi gọi method có đúng **một tham số reference** bằng giá trị `null`, viết rõ `(Object) null` hoặc `new Object[]{null}`. Truyền một `null` không ép kiểu vào vị trí varargs có thể bị diễn giải là **mảng đối số `Object[]` bằng `null`**, tức không mang cùng ý nghĩa với một đối số `null`.

## 5. `Constructor.newInstance` và `Field.get/set` cũng có ràng buộc đầu vào

Không chỉ `Method.invoke`. Các API reflection khác có thể ném `IllegalArgumentException` do giá trị đưa vào không khớp:

| API | Trường hợp có thể gây `IllegalArgumentException` | Phân biệt |
| --- | --- | --- |
| `Constructor.newInstance(args)` | Sai số lượng/kiểu đối số so với constructor đã tìm | Không có receiver; exception bên trong constructor được bọc `InvocationTargetException` |
| `Field.get(receiver)` | Receiver không phù hợp cho instance field | Receiver `null` cho instance field dẫn đến `NullPointerException` |
| `Field.set(receiver, value)` | Receiver sai kiểu hoặc giá trị ghi không thể chuyển sang kiểu field | Field `final`/không đủ quyền còn chịu quy tắc riêng, có thể `IllegalAccessException` |
| `Field.setInt(receiver, value)` | Receiver không phù hợp hoặc field không chấp nhận giá trị kiểu `int` theo quy tắc chuyển đổi của API | Không dùng `setInt` để ghi tùy ý vào mọi field |

Với field `static`, receiver bị bỏ qua theo hợp đồng của `Field.get/set`. Với instance field, phải truyền instance phù hợp. `Field#getType()` hữu ích để đọc kiểu field trước khi ghi.

`Constructor.newInstance` nhận arguments tương ứng với chữ ký constructor được tra cứu, ví dụ `getConstructor(int.class)`. Nếu một constructor có tham số `int`, truyền chuỗi `"3"` không khiến reflection tự gọi `Integer.parseInt`.

Chú ý `IllegalArgumentException` cũng có thể xuất hiện ở bước **tra cứu hoặc tạo lời gọi động** trong những API khác, nhưng bài này tập trung vào đầu vào của core reflection và cách chẩn đoán receiver/arguments.

## 6. Ví dụ Java 17+ hoàn chỉnh: 12 tình huống và kết quả

Tạo file **`ReflectArgumentDiagnostics.java`**:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectArgumentDiagnostics {
    public static final class Service {
        public int total;

        public Service() {}

        public int sum(int x, long y) {
            return x + (int) y;
        }

        public String join(String... items) {
            return String.join("|", items);
        }

        public void check(int code) {
            if (code < 0) {
                throw new IllegalArgumentException("business rule");
            }
        }
    }

    @FunctionalInterface
    interface Action {
        Object execute() throws Throwable;
    }

    private static void probe(String label, Action action) {
        try {
            System.out.println(label + " => " + action.execute());
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            System.out.println(label + " => InvocationTargetException / cause="
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (Throwable ex) {
            System.out.println(label + " => " + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) throws Exception {
        Service service = new Service();
        Method sum = Service.class.getMethod("sum", int.class, long.class);
        Field total = Service.class.getField("total");
        Constructor<Service> ctor = Service.class.getConstructor();
        Method check = Service.class.getMethod("check", int.class);
        Method join = Service.class.getMethod("join", String[].class);

        probe("valid widening",
                () -> sum.invoke(service, Short.valueOf((short) 2), Integer.valueOf(3)));
        probe("wrong receiver", () -> sum.invoke("not Service", 2, 3L));
        probe("wrong arity", () -> sum.invoke(service, 2));
        probe("narrowing Long to int", () -> sum.invoke(service, 2L, 3L));
        probe("null primitive", () -> sum.invoke(service, null, 3L));
        probe("null receiver", () -> sum.invoke(null, 2, 3L));
        probe("field wrong receiver", () -> total.get("not Service"));
        probe("field wrong value",
                () -> { total.set(service, "text"); return null; });
        probe("constructor wrong args", () -> ctor.newInstance(1));
        probe("target threw IllegalArgumentException",
                () -> check.invoke(service, -1));
        probe("varargs packed",
                () -> join.invoke(service, (Object) new String[] {"a", "b"}));
        probe("varargs spread", () -> join.invoke(service, "a", "b"));
    }
}
```

Biên dịch và chạy tại thư mục chứa file:

```bash
javac --release 17 ReflectArgumentDiagnostics.java
java ReflectArgumentDiagnostics
```

Output chính xác (đã kiểm chứng bằng OpenJDK 21):

```text
valid widening => 5
wrong receiver => IllegalArgumentException
wrong arity => IllegalArgumentException
narrowing Long to int => IllegalArgumentException
null primitive => IllegalArgumentException
null receiver => NullPointerException
field wrong receiver => IllegalArgumentException
field wrong value => IllegalArgumentException
constructor wrong args => IllegalArgumentException
target threw IllegalArgumentException => InvocationTargetException / cause=IllegalArgumentException: business rule
varargs packed => a|b
varargs spread => IllegalArgumentException
```

Các dòng `wrong receiver`, `wrong arity`, `narrowing` và `null primitive` cho thấy lỗi **áp dụng lời gọi**. Dòng `null receiver` chứng minh cần phân biệt `NullPointerException`. Dòng `target threw IllegalArgumentException` cho thấy chương trình đích thật sự chạy rồi ném lỗi nghiệp vụ; vì vậy bên ngoài nhận `InvocationTargetException`. Hai dòng varargs kiểm tra sự khác nhau giữa một mảng `String[]` với hai chuỗi riêng lẻ.

Trong chương trình demo, `probe` bắt `Throwable` ở nhánh cuối chỉ để in tên lỗi của các trường hợp thử nghiệm. Trong code sản phẩm, nên bắt các loại lỗi liên quan một cách có chủ đích, không tự động nuốt mọi `Error` hay `Throwable`.

## 7. Quy trình chẩn đoán từng bước

Khi stack trace trỏ vào `Method.invoke` hoặc `Field.get/set`:

1. **Xác định vị trí ném lỗi:** exception là `IllegalArgumentException` trực tiếp từ API reflection hay `InvocationTargetException` với `getCause()` là `IllegalArgumentException` do code đích?
2. **Ghi nhận metadata thành viên:** `method.toGenericString()`, `getDeclaringClass().getName()`, `getParameterTypes()`, `Modifier.isStatic(method.getModifiers())`. Với constructor, ghi chữ ký constructor; với field, ghi `field.getType()`.
3. **Kiểm tra receiver:** instance hay static? Nếu instance, có khác `null` và `getDeclaringClass().isInstance(receiver)` trả `true` không? Không dùng `receiver.getClass()` trước khi bảo đảm receiver không null.
4. **Kiểm tra số lượng và thứ tự đối số:** so `getParameterCount()` với số phần tử thực sự trong `Object[]` mà reflection nhận. Với parameter varargs của method đích, nhớ truyền *một mảng* vào vị trí cuối.
5. **Kiểm tra kiểu từng đối số:** nếu là `null`, parameter có primitive không? Nếu là wrapper, có unboxing/widening hợp lệ không? Nếu là reference, có assignable không?
6. **Tách lỗi truy cập:** `IllegalAccessException`, `InaccessibleObjectException` hoặc `ExceptionInInitializerError` cần cách xử lý khác. Không sửa bằng cách đổi receiver/ép kiểu nếu nguyên nhân là JPMS hoặc khởi tạo class.
7. **Đánh giá xử lý lỗi:** lỗi đầu vào từ framework/plugin thường cần sửa mapping/validation; lỗi từ code đích cần hiển thị hoặc xử lý `getCause()` với ngữ cảnh thích hợp.

Gợi ý log chẩn đoán khi xử lý input tin cậy, tránh log dữ liệu nhạy cảm:

```java
System.out.println("method = " + method.toGenericString());
System.out.println("declaringClass = " + method.getDeclaringClass().getName());
System.out.println("receiverType = "
        + (receiver == null ? "<null>" : receiver.getClass().getName()));
System.out.println("parameterCount = " + method.getParameterCount());
System.out.println("parameterTypes = "
        + java.util.Arrays.toString(method.getParameterTypes()));
System.out.println("argumentTypes = " + java.util.Arrays.toString(
        java.util.Arrays.stream(args)
                .map(arg -> arg == null ? "<null>" : arg.getClass().getName())
                .toArray(String[]::new)));
```

Đây là fragment cần đặt trong method nơi `method`, `receiver`, `args` (mảng `Object[]` **khác null**) đã tồn tại. Nếu `args == null`, phải xử lý riêng trước khi gọi `Arrays.stream(args)`. Với dữ liệu do bên ngoài cung cấp, ghi kiểu và số lượng thường đủ để debug; không log toàn bộ giá trị argument hoặc stack trace ra kênh công khai.

## 8. Không nhầm với các ngoại lệ reflection khác

| Ngoại lệ | Tín hiệu chính | Hướng xử lý |
| --- | --- | --- |
| `IllegalArgumentException` trực tiếp | Receiver không phù hợp, sai argument count/type, giá trị field không hợp lệ | Kiểm tra chữ ký, receiver và conversion |
| `NullPointerException` từ instance invocation | Receiver `null` cho instance method/field | Kiểm tra receiver trước khi gọi |
| `InvocationTargetException` | Method/constructor đích ném ngoại lệ | Xem `getCause()`; phân tích code đích |
| `NoSuchMethodException` / `NoSuchFieldException` | Không tìm được thành viên đúng tên/chữ ký | Kiểm tra overload, visibility và declaring class |
| `IllegalAccessException` | Không đủ quyền gọi/truy cập thành viên | Xem modifier, package/module và quyền truy cập |
| `InaccessibleObjectException` | Không thể bật deep reflection access qua `setAccessible` | Xem cấu hình `opens`/module, tránh lạm dụng workaround |
| `ExceptionInInitializerError` | Khởi tạo class (ví dụ trước khi gọi static member) thất bại | Phân tích static initializer và cause |

`Method.invoke` **không** có cơ chế tự dò tên overload, tự parse chuỗi, tự ép kiểu narrowing hay tự bỏ qua kiểm tra receiver cho instance method. Việc sửa các lỗi này thường nằm tại code xây dựng lời gọi reflection, không nằm trong logic của method đích.

## 9. Lỗi thường gặp và câu hỏi tự kiểm tra

- **Bắt mọi `IllegalArgumentException` và cho rằng method đích từ chối dữ liệu:** sai; nếu qua `Method.invoke` thì lỗi từ method đích thường được bọc `InvocationTargetException`.
- **Dùng sai receiver nhưng method tên giống nhau:** không đủ; receiver phải thuộc đúng declaring class hoặc subtype được phép.
- **Truyền `Long` vì nghĩ Java sẽ “tự ép” xuống `int`:** không; narrowing bị từ chối.
- **Gọi reflection method có `String...` bằng nhiều chuỗi rời:** không tự đóng gói; phải truyền một `String[]` ở vị trí đối số cuối.
- **Xem mọi trường hợp receiver sai đều là `IllegalArgumentException`:** receiver `null` cho instance method/field là `NullPointerException`.
- **Cố sửa `IllegalAccessException` bằng đổi kiểu argument:** không giải quyết vấn đề quyền truy cập.

Tự kiểm tra:

1. `m` biểu diễn instance method của `User`, nhưng `m.invoke(new Order())`: lỗi nào? **`IllegalArgumentException` do receiver không phù hợp**.
2. `m` biểu diễn `void f(int x)`. Gọi `m.invoke(user, null, 1)` có hợp lệ không? **Không**, vì reflection nhận hai đối số cho method có một tham số (dù từng tham số trông như thế nào).
3. `m` biểu diễn `void f(long x)`. Truyền `Integer.valueOf(1)` có hợp lệ không? **Có**, thông qua unboxing `int` rồi widening thành `long`.
4. `f` đích tự ném `IllegalArgumentException("bad")`. Caller gọi `Method.invoke` bắt gì? **`InvocationTargetException`** và có thể lấy nguyên nhân `IllegalArgumentException` bằng `getCause()`.
5. `m` biểu diễn `void f(String[] words)`. Muốn truyền `String[]` như một argument qua `invoke(Object, Object...)`? **Ép `(Object) stringArray` hoặc bọc trong `new Object[]{stringArray}`**.

Tài liệu chuẩn: [`Method.invoke` (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...)), [`Constructor.newInstance`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html#newInstance(java.lang.Object...)), [`Field`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html), [`InvocationTargetException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exceptions-diagnostics-005">InstantiationException và kiểu không thể khởi tạo</a>

<details>
<summary>Xem chi tiết</summary>

# `InstantiationException` và các kiểu Java không thể khởi tạo

`java.lang.InstantiationException` là **checked exception** (kế thừa `ReflectiveOperationException`) báo việc tạo một instance **bằng Reflection** không thành công vì kiểu đích không thể được khởi tạo theo cách đã yêu cầu. Những nguyên nhân điển hình gồm lớp `abstract`, `interface`, kiểu mảng/nguyên thủy/`void` hoặc việc gọi API cũ `Class.newInstance()` đối với lớp không có constructor không tham số.

Điều cần phân biệt: **“không tạo được đối tượng” không đồng nghĩa “luôn ném `InstantiationException`”**. Ngoại lệ thực tế phụ thuộc kiểu được phản chiếu, API dùng để tìm/gọi constructor và giai đoạn lỗi xảy ra. Với Java 17+, cách thông thường là dùng `getDeclaredConstructor(...).newInstance(...)`, không dùng `Class.newInstance()` đã deprecated.

## 1. `InstantiationException` nằm ở đâu trong Java Reflection?

Phân cấp ngoại lệ:

~~~text
java.lang.Throwable
└── java.lang.Exception
    └── java.lang.ReflectiveOperationException
        └── java.lang.InstantiationException
~~~

`InstantiationException` khác `java.lang.InstantiationError`. `InstantiationError` là `Error` liên quan đến tình trạng không tương thích khi liên kết/thực thi bytecode cố tạo một lớp trừu tượng hoặc interface; nó không phải ngoại lệ nghiệp vụ thường dùng để xử lý một lệnh tạo đối tượng qua Reflection.

Trong mã Java thông thường, viết `new AbstractService()` hoặc `new Runnable()` là lỗi **biên dịch**. Nhưng khi nhận một `Class<?>` tại runtime, trình biên dịch không biết đối tượng `Class` ấy đại diện lớp cụ thể, interface hay kiểu abstract. Phép tạo instance bằng Reflection có thể phát hiện điều kiện này **khi chạy** và ném ngoại lệ tương ứng.

## 2. Hai API `newInstance` có hành vi khác nhau

### 2.1. `Class.newInstance()`: API cũ, deprecated từ Java 9

~~~java
// Minh họa API cũ; không nên dùng cho mã mới:
Object obj = someClass.newInstance();
~~~

API này yêu cầu class có constructor không tham số (nullary/no-arg) và constructor/class đó truy cập được từ caller. Theo tài liệu Java SE, `InstantiationException` có thể xuất hiện nếu `someClass` đại diện cho lớp abstract, interface, mảng, kiểu nguyên thủy, `void` **hoặc không có constructor không tham số**. Lỗi về quyền truy cập thông thường là `IllegalAccessException`.

Vấn đề quan trọng: `Class.newInstance()` **có thể truyền thẳng một checked exception do constructor ném ra**, dù khai báo `throws` của phương thức không thể hiện ngoại lệ cụ thể đó. Điều này bỏ qua cơ chế kiểm tra checked exception của trình biên dịch tại call site; vì vậy API đã bị deprecated từ Java 9.

### 2.2. `Constructor.newInstance(...)`: cách phù hợp cho mã mới

~~~java
// Đặt trong phương thức xử lý ReflectiveOperationException,
// hoặc bắt riêng các ngoại lệ cần thiết:
Object obj = someClass.getDeclaredConstructor().newInstance();
~~~

Chuỗi thao tác gồm **hai bước có thể lỗi khác nhau**:

1. `getDeclaredConstructor()` tìm constructor **không tham số** của đúng lớp, bất kể modifier. Nếu không tồn tại, ném `NoSuchMethodException`. Nếu cần constructor có tham số, chỉ rõ parameter types tại đây, chẳng hạn `getDeclaredConstructor(String.class, int.class)`, rồi truyền giá trị cho `newInstance("A", 3)`.
2. `Constructor.newInstance(...)` kiểm tra khả năng tạo instance, quyền truy cập, đối số và thực thi constructor. Nếu **lớp abstract**, phương thức có thể ném `InstantiationException`; nếu constructor đích tự ném exception, nó sẽ được bọc trong `InvocationTargetException`, với nguyên nhân gốc truy cập qua `getCause()`.

`getDeclaredConstructor()` **tìm được cả constructor private**, nhưng tìm được không có nghĩa được phép gọi. Không đủ quyền ở bước `newInstance` thường dẫn đến `IllegalAccessException`. `setAccessible(true)` hay `trySetAccessible()` phải tuân thủ quy tắc module; `opens` và encapsulation của JPMS không bị bỏ qua tùy ý.

`getConstructor(...)` khác `getDeclaredConstructor(...)`: phương thức thứ nhất chỉ tìm **constructor public**, phương thức thứ hai tìm constructor được khai báo trực tiếp kể cả private/protected/package-private. Constructor không được kế thừa như các phương thức instance.

## 3. Bảng chẩn đoán: cùng mục tiêu, lỗi khác API

| Tình huống | `Class.newInstance()` cũ | `getDeclaredConstructor().newInstance()` hiện đại |
| --- | --- | --- |
| Lớp cụ thể, constructor không tham số public, chạy thành công | Tạo instance | Tạo instance |
| **Lớp abstract**, dù có constructor không tham số | `InstantiationException` | `InstantiationException` tại `Constructor.newInstance` |
| **Interface**, array, primitive hoặc `void` | `InstantiationException` | Thường `NoSuchMethodException` khi tìm constructor, vì không có constructor thích hợp |
| Lớp chỉ có constructor có tham số | `InstantiationException` vì không có no-arg constructor | `NoSuchMethodException` ở `getDeclaredConstructor()` |
| Constructor hiện có nhưng caller không đủ quyền gọi | `IllegalAccessException` | `IllegalAccessException` khi `Constructor.newInstance` (trừ khi được cấp quyền hợp lệ) |
| Constructor chạy rồi tự ném ngoại lệ | Ngoại lệ từ constructor có thể truyền thẳng | `InvocationTargetException` chứa ngoại lệ gốc |
| Khởi tạo static của class thất bại | Có thể là `ExceptionInInitializerError` | Có thể là `ExceptionInInitializerError` |

Bảng này tập trung các trường hợp phổ biến. Không suy luận rằng chỉ từ tên `InstantiationException` có thể biết chính xác class nào sai: luôn lưu lại **kiểu đích, constructor/chữ ký, API được gọi, và ngoại lệ gốc**. Với constructor có tham số, mismatch về số lượng hoặc loại đối số có thể tạo `IllegalArgumentException` tại `Constructor.newInstance`.

Một số kiểu có ràng buộc riêng:

- **Lớp abstract** không thể có instance trực tiếp; chỉ có thể tạo một **lớp con cụ thể**. Constructor của lớp abstract có thể tồn tại để lớp con gọi `super(...)`, nhưng bản thân abstract class vẫn không thể `newInstance`.
- **Interface** và **annotation type** không có constructor để tạo đối tượng trực tiếp. Muốn có instance thỏa interface, dùng một lớp triển khai cụ thể hoặc cơ chế proxy phù hợp.
- **`int.class`, `void.class` và các lớp mảng** không có constructor Reflection kiểu thông thường. Tạo mảng bằng `new int[3]` hoặc `Array.newInstance(int.class, 3)`, không tìm `int[].class.getDeclaredConstructor()`.
- **Lớp không có no-arg constructor** vẫn hoàn toàn có thể được khởi tạo qua constructor có tham số: `getDeclaredConstructor(String.class).newInstance("example")` nếu constructor tương ứng và quyền truy cập hợp lệ.
- **Non-static inner class** thường có tham số constructor ẩn chứa instance của enclosing class; gọi `getDeclaredConstructor()` không tham số thường không tìm thấy. Cần đọc `getParameterTypes()` và truyền enclosing instance khi gọi.
- **Enum** được Java quản lý đặc biệt. `Constructor.newInstance(...)` cố tạo enum sẽ bị từ chối bằng `IllegalArgumentException`; không dùng Reflection để tự sản xuất thêm enum constant.
- **Record** là class có thể tạo nếu gọi đúng constructor tương ứng với các component và điều kiện truy cập; record không mặc nhiên có no-arg constructor.

## 4. Ví dụ Java 17+ chạy độc lập, phân biệt các ngoại lệ

Lưu **nguyên vẹn** nội dung sau trong tệp `InstantiationExceptionDemo.java`:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class InstantiationExceptionDemo {
    public static class Concrete {
        public Concrete() {}
    }

    public static abstract class AbstractTask {
        public AbstractTask() {}
    }

    public interface Contract {}

    public static class RequiresArgument {
        public RequiresArgument(String name) {}
    }

    public static class Exploding {
        public Exploding() {
            throw new IllegalStateException("ctor failed");
        }
    }

    private static void attempt(String label, Class<?> type) {
        try {
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.newInstance();
            System.out.println(label + " -> created");
        } catch (InstantiationException e) {
            System.out.println(label + " -> InstantiationException");
        } catch (NoSuchMethodException e) {
            System.out.println(label + " -> NoSuchMethodException");
        } catch (IllegalAccessException e) {
            System.out.println(label + " -> IllegalAccessException");
        } catch (InvocationTargetException e) {
            System.out.println(label + " -> InvocationTargetException (" +
                    e.getCause().getClass().getSimpleName() + ")");
        }
    }

    @SuppressWarnings("deprecation")
    private static void tryLegacy() throws IllegalAccessException {
        try {
            RequiresArgument.class.newInstance();
        } catch (InstantiationException e) {
            System.out.println(
                    "legacy missing constructor -> InstantiationException");
        }
    }

    public static void main(String[] args) throws Exception {
        attempt("concrete", Concrete.class);
        attempt("abstract", AbstractTask.class);
        attempt("interface", Contract.class);
        attempt("array", String[].class);
        attempt("primitive", int.class);
        attempt("no-arg missing", RequiresArgument.class);
        attempt("constructor throws", Exploding.class);
        tryLegacy();
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac InstantiationExceptionDemo.java
java InstantiationExceptionDemo
~~~

Kết quả:

~~~text
concrete -> created
abstract -> InstantiationException
interface -> NoSuchMethodException
array -> NoSuchMethodException
primitive -> NoSuchMethodException
no-arg missing -> NoSuchMethodException
constructor throws -> InvocationTargetException (IllegalStateException)
legacy missing constructor -> InstantiationException
~~~

**Phân tích output:**

1. `Concrete` là lớp cụ thể, có constructor public không tham số, nên tạo được.
2. `AbstractTask` cố tình **có** constructor public không tham số. Bước `getDeclaredConstructor()` thành công, nhưng `newInstance()` vẫn ném `InstantiationException` vì lớp **abstract**. Ví dụ này cô lập đúng nguyên nhân, tránh nhầm với lỗi thiếu constructor.
3. `Contract` là interface; `String[].class` và `int.class` không có constructor không tham số kiểu class thông thường. API mới thất bại ngay ở **bước tìm constructor**, nên nhận `NoSuchMethodException`.
4. `RequiresArgument` là class cụ thể nhưng constructor yêu cầu một `String`; lookup không tham số thất bại bằng `NoSuchMethodException`.
5. `Exploding` tạo được instance về mặt kiểu, nhưng constructor ném `IllegalStateException`. API mới bọc nó thành `InvocationTargetException`, thay vì `InstantiationException`.
6. `Class.newInstance()` cũ trên `RequiresArgument` lại ném `InstantiationException` cho đúng tình huống **không có no-arg constructor**. Hai API dùng tên `newInstance` nhưng không đồng nhất hành vi lỗi.

Để tạo `RequiresArgument` đúng cách, thay vì thử constructor rỗng, chọn chữ ký thực:

~~~java
// Minh họa đặt bên trong phương thức có xử lý ReflectiveOperationException:
Constructor<InstantiationExceptionDemo.RequiresArgument> ctor =
        InstantiationExceptionDemo.RequiresArgument.class
                .getDeclaredConstructor(String.class);
InstantiationExceptionDemo.RequiresArgument obj =
        ctor.newInstance("demo");
~~~

Đoạn trên chỉ minh họa lựa chọn chữ ký, không phải một chương trình độc lập. Tương tự, để tạo đối tượng thỏa `Contract`, hãy viết lớp triển khai cụ thể `implements Contract` rồi tạo **lớp đó**.

## 5. Cách biết một kiểu có thể khởi tạo trước khi gọi

Nếu đầu vào là tên class do cấu hình/plugin cung cấp, nên kiểm tra các đặc tính căn bản của `Class<?>`:

~~~java
// Đoạn minh họa sử dụng Class<?> type bên trong một method:
int modifiers = type.getModifiers();
boolean unsuitable =
        type.isInterface()
        || type.isAnnotation()
        || type.isArray()
        || type.isPrimitive()
        || type == void.class
        || java.lang.reflect.Modifier.isAbstract(modifiers);
~~~

Đây là **lọc sớm các trường hợp hiển nhiên**; không phải một phép chứng minh có thể tạo instance. `isAnnotation()` thực ra là trường hợp con của interface, nên được ghi tường minh cho người học dễ nhận diện. Một kiểu vượt qua bộ lọc vẫn có thể thiếu constructor phù hợp, không đủ quyền, bị ràng buộc JPMS, là enum, hoặc constructor thực thi thất bại.

Khi có đầu vào dạng `Class<?>` chưa biết trước:

1. Kiểm tra nó có phải lớp cụ thể/loại được hỗ trợ theo contract của hệ thống không.
2. Xác định chữ ký constructor mong muốn: `getDeclaredConstructor(parameterTypes...)` hoặc `getConstructor(...)`. Đừng giả sử mọi lớp có no-arg constructor.
3. Chỉ yêu cầu quyền truy cập thật sự cần. Với JDK 17+ và named modules, package private/protected/private từ module khác có thể bị giới hạn dù lấy được metadata.
4. Bắt và xử lý đúng loại ngoại lệ. Nếu gặp `InvocationTargetException`, kiểm tra `getCause()` để chẩn đoán constructor đích; không che khuất nguyên nhân gốc.
5. Nếu hệ thống cần tạo object từ plugin, cân nhắc contract là interface/base class, factory method hoặc dependency injection. Dùng `type.asSubclass(ExpectedBase.class)` để kiểm tra quan hệ kiểu trước khi tạo, thay vì ép kiểu mù.
6. Ghi log lớp đích, signature đã yêu cầu, exception và cause một cách có kiểm soát; tránh ghi dữ liệu nhạy cảm từ đối số constructor.

Ví dụ xử lý rõ ràng các lỗi phản xạ (đoạn đặt trong một phương thức, không phải file độc lập):

~~~java
try {
    Object instance = type.getDeclaredConstructor().newInstance();
    // Dùng instance theo contract ứng dụng.
} catch (NoSuchMethodException e) {
    // Không có constructor không tham số.
} catch (InstantiationException e) {
    // Có constructor nhưng kiểu không thể khởi tạo (ví dụ abstract).
} catch (IllegalAccessException e) {
    // Không đủ quyền sử dụng constructor.
} catch (java.lang.reflect.InvocationTargetException e) {
    Throwable original = e.getCause();
    // Phân tích/log original và quyết định xử lý theo contract.
}
~~~

Trong ứng dụng thực, các khối xử lý trên phải thực hiện hành động có ý nghĩa (thông báo lỗi cấu hình, propagate/translate exception...), không nuốt lỗi rồi tiếp tục bằng instance `null`.

## 6. Nhầm lẫn thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “`InstantiationException` xảy ra mỗi khi constructor ném lỗi.” | Constructor ném exception khi dùng `Constructor.newInstance` thường được bọc trong `InvocationTargetException`. |
| “Thiếu constructor rỗng với API mới sẽ ném `InstantiationException`.” | `getDeclaredConstructor()` không tìm được constructor ⇒ `NoSuchMethodException`. |
| “Abstract class có public constructor là tạo trực tiếp được.” | Constructor có tồn tại vẫn không thay đổi tính abstract của class. |
| “`Class.forName(...)` tạo một object của class.” | Nó trả `Class<?>` và có thể kích hoạt class initialization, nhưng không gọi constructor để tạo instance của kiểu đích. |
| “`getDeclaredConstructor` tìm được private constructor nghĩa là gọi được.” | Lookup metadata và quyền thực thi là hai vấn đề khác nhau. |
| “`InstantiationException` chính là `InstantiationError`.” | Một bên là checked reflection exception, bên kia là `Error` ở mức liên kết/thực thi. |
| “Thêm `setAccessible(true)` chữa mọi lỗi tạo object.” | Không giải quyết abstract, thiếu constructor, lỗi trong constructor; cũng không vô điều kiện vượt JPMS. |
| “Mảng và primitive có thể tạo qua constructor không tham số.” | Mảng dùng cú pháp mảng hoặc `Array.newInstance`; primitive không được khởi tạo như object qua Reflection. |

**Ghi nhớ:** hãy xét đúng **ba câu hỏi theo thứ tự**: *kiểu này có thể có instance không?* → *constructor đúng chữ ký có tồn tại và truy cập được không?* → *constructor có chạy thành công không?* Với `Constructor.newInstance`, ba nhóm lỗi điển hình tương ứng là `InstantiationException`, `NoSuchMethodException`/`IllegalAccessException`, và `InvocationTargetException`. Thực tế vẫn có những exception/error khác ở mức argument, class initialization hoặc linking nên không được dùng bảng này như danh sách đầy đủ mọi trường hợp.

**Tài liệu chính thức:** [`InstantiationException` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/InstantiationException.html), [`Class.newInstance()` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#newInstance()), [`Constructor.newInstance(...)` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Constructor.html#newInstance(java.lang.Object...)).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exceptions-diagnostics-006">InvocationTargetException và cách giữ nguyên cause</a>

<details>
<summary>Xem chi tiết</summary>

# InvocationTargetException và cách giữ nguyên cause khi dùng Java Reflection

Khi gọi một phương thức qua Java Reflection, lỗi do **chính phương thức được gọi** ném ra thường không đi thẳng tới khối `catch` ở bên ngoài. API `Method.invoke(...)` bọc lỗi gốc trong `InvocationTargetException`. Cơ chế tương tự áp dụng khi constructor thực thi qua `Constructor.newInstance(...)` thất bại vì code bên trong constructor ném ngoại lệ.

Điều cần làm khi chẩn đoán là phân biệt **lỗi thực thi bên trong đối tượng đích** với **lỗi do thao tác Reflection**, đọc ngoại lệ gốc qua `getCause()` và **giữ liên kết cause** khi bổ sung thông tin ngữ cảnh hoặc ném lại ngoại lệ. Nếu chỉ in thông điệp lỗi rồi tạo một exception mới không có cause, thông tin về nơi phát sinh lỗi ban đầu sẽ bị mất khỏi chuỗi chẩn đoán.

Bài này thuộc nhóm **Ngoại lệ và chẩn đoán** của Java Reflection. Các ví dụ dùng Java 17+ và được trình bày đầy đủ để tự biên dịch và chạy.

## 1. InvocationTargetException thực sự đại diện cho điều gì?

Giả sử một phương thức nghiệp vụ có thể thất bại:

~~~java
public String read(String key) throws IOException {
    if ("bad".equals(key)) {
        throw new IOException("không đọc được");
    }
    return "value:" + key;
}
~~~

Nếu gọi trực tiếp `target.read("bad")`, mã gọi phải xử lý `IOException` theo hợp đồng `throws IOException`. Nhưng khi gọi bằng Reflection:

~~~java
Method method = Target.class.getMethod("read", String.class);
Object result = method.invoke(target, "bad");
~~~

`Method.invoke` công bố các ngoại lệ liên quan đến thao tác gọi động, trong đó có **checked exception** `InvocationTargetException`. Khi body của `read` ném `IOException`, lớp wrapper được tạo và chứa chính `IOException` đó làm cause:

~~~text
Call site
   |
   v
Method.invoke(target, "bad")
   |
   v
Target.read("bad")
   |
   +-- throws IOException("không đọc được")    [lỗi gốc]
   |
   v
Method.invoke throws InvocationTargetException
                           |
                           +-- getCause() -> IOException
   |
   v
catch (InvocationTargetException ex)
   |
   +-- xem / ghi log ngoại lệ gốc
   +-- ném lại lỗi gốc hoặc tạo wrapper mới có cause
~~~

Điểm quan trọng:

- `InvocationTargetException` **không tự nói rằng Reflection API bị hỏng**; nó cho biết phần mã được gọi đã ném ngoại lệ.
- `ex.getCause()` trả đối tượng `Throwable` được phương thức hoặc constructor đích ném ra. Lỗi gốc có thể là checked exception (ví dụ `IOException`), `RuntimeException` hoặc `Error`.
- `ex.getTargetException()` cũng trả ngoại lệ đích; đây là phương thức có từ trước cơ chế exception chaining chung. **Ưu tiên `getCause()`**.
- Javadoc cho phép `getCause()` trả `null` (có thể tự tạo `InvocationTargetException` không kèm target). Khi `Method.invoke` thực sự vừa bắt lỗi từ target, thông thường sẽ có cause; mã thư viện phòng thủ vẫn nên tránh dereference nếu cause là `null`.
- Wrapper có stack trace riêng của nó; cause cũng mang stack trace ở nơi lỗi nghiệp vụ phát sinh. Hai lớp thông tin phục vụ hai mục đích chẩn đoán khác nhau.

`InvocationTargetException` kế thừa `ReflectiveOperationException` và là **checked exception**. Không thể dùng một lệnh `catch (RuntimeException ex)` để bắt riêng wrapper này.

## 2. Nhận diện hai nhóm lỗi: target exception và Reflection exception

Không phải mọi lỗi xảy ra quanh một lời gọi `invoke` đều là `InvocationTargetException`.

| Tình huống | Ngoại lệ điển hình | Điểm phát sinh và cách hiểu |
|---|---|---|
| Body phương thức ném `IOException` | `InvocationTargetException` với cause là `IOException` | Target đã chạy và thất bại |
| Body phương thức ném `IllegalStateException` | `InvocationTargetException` với cause là `IllegalStateException` | Target đã chạy và thất bại |
| Body constructor ném `IllegalArgumentException` | `InvocationTargetException` với cause là `IllegalArgumentException` | Constructor đã chạy và thất bại |
| Sai số lượng/kiểu đối số truyền vào `Method.invoke` | `IllegalArgumentException` | API từ chối lời gọi trước khi thực thi target |
| Đối tượng nhận (`receiver`) không phù hợp với method instance | `IllegalArgumentException` | Sai đối tượng đích |
| Dùng `null` làm receiver của method instance | `NullPointerException` | Không có đối tượng đích để gọi |
| Thành viên không thể truy cập theo quy tắc Java | `IllegalAccessException` | Lỗi quyền truy cập Reflection |
| `getMethod`/`getDeclaredMethod` không tìm được chữ ký | `NoSuchMethodException` | Lỗi bước tra cứu trước lời gọi |
| Class initialization do lời gọi kích hoạt thất bại | `ExceptionInInitializerError` | Lỗi khởi tạo lớp, có thể xuất hiện ngoài wrapper |
| Constructor không thể tạo instance của abstract class | `InstantiationException` | Không thể khởi tạo kiểu đó |

Vì thế đừng viết `catch (Exception e) { throw new RuntimeException("Target lỗi"); }` cho toàn bộ đoạn Reflection. Cách đó làm mờ ranh giới giữa **chương trình đích lỗi** và **host gọi sai hoặc không có quyền**. Có thể xử lý trong một tầng chung khi cần, nhưng thông điệp và cause nên đủ để phân biệt từng nhóm.

Một lưu ý kỹ thuật: `Class.newInstance()` đã deprecated từ Java 9 và có cách lan truyền ngoại lệ constructor khác. Khi cần tạo đối tượng từ `Class`, ưu tiên `getDeclaredConstructor(...).newInstance(...)` để có hành vi exception wrapping rõ ràng.

## 3. Chương trình Java 17+ chạy được: thấy nguyên nhân và giữ cause

Lưu nguyên khối mã sau thành **`ReflectionFailureDemo.java`**. Nó minh họa năm bước: gọi thành công, thêm ngữ cảnh và giữ nguyên cause, lan truyền lại checked exception đúng kiểu, bắt lỗi constructor, và phân biệt lỗi sai đối số Reflection.

~~~java
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionFailureDemo {
    static class Target {
        public Target(String token) {
            if (token.isBlank()) {
                throw new IllegalStateException("token rỗng");
            }
        }

        public String read(String key) throws IOException {
            if ("bad".equals(key)) {
                throw new IOException("không đọc được");
            }
            return "value:" + key;
        }
    }

    static String callAndWrap(Method method, Target target, String key) {
        try {
            return (String) method.invoke(target, key);
        } catch (InvocationTargetException ex) {
            throw new IllegalStateException(
                    "Không gọi được Target.read", ex.getCause());
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(
                    "Không có quyền truy cập method", ex);
        }
    }

    static String callAndPreserve(Method method, Target target, String key)
            throws IOException {
        try {
            return (String) method.invoke(target, key);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Lỗi ngoài hợp đồng", cause);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(
                    "Không có quyền truy cập method", ex);
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Constructor<Target> constructor =
                Target.class.getDeclaredConstructor(String.class);
        Target target = constructor.newInstance("ok");
        Method method = Target.class.getMethod("read", String.class);

        System.out.println("success = " + callAndWrap(method, target, "a"));

        try {
            callAndWrap(method, target, "bad");
        } catch (IllegalStateException ex) {
            Throwable cause = ex.getCause();
            System.out.println("wrapper = " + ex.getClass().getSimpleName());
            System.out.println("cause = " + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());
            System.out.println("origin = " + cause.getStackTrace()[0].getMethodName());
        }

        try {
            callAndPreserve(method, target, "bad");
        } catch (IOException ex) {
            System.out.println("preserve = " + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
        }

        try {
            constructor.newInstance("");
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            System.out.println("constructor = " + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());
        }

        try {
            method.invoke(target, 42);
        } catch (IllegalArgumentException ex) {
            System.out.println("argument = " + ex.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file:

~~~bash
javac --release 17 -encoding UTF-8 ReflectionFailureDemo.java
java ReflectionFailureDemo
~~~

**Stdout thực tế của ví dụ (đã kiểm chứng):**

~~~text
success = value:a
wrapper = IllegalStateException
cause = IOException: không đọc được
origin = read
preserve = IOException: không đọc được
constructor = IllegalStateException: token rỗng
argument = IllegalArgumentException
~~~

Giải thích kết quả:

1. Khi `key = "a"`, `read` trả `"value:a"`; Reflection hoàn thành bình thường.
2. Khi `key = "bad"`, `IOException` được `Method.invoke` bọc thành `InvocationTargetException`. `callAndWrap` tạo `IllegalStateException` có ngữ cảnh nhưng truyền **đúng đối tượng cause** vào constructor của wrapper mới.
3. `origin = read` chứng minh stack trace của cause vẫn bắt đầu từ phương thức `read` nơi `IOException` được tạo/ném (trong ví dụ cụ thể này).
4. `callAndPreserve` dùng pattern matching `instanceof` của Java 17 để nhận diện `IOException` và ném lại chính ngoại lệ đó. Chữ ký hàm vẫn khai báo `throws IOException`, giữ hợp đồng checked exception cho caller.
5. Constructor `Target(String)` ném `IllegalStateException` khi token rỗng. `Constructor.newInstance("")` trả lỗi qua `InvocationTargetException`, và cause vẫn là `IllegalStateException` của constructor.
6. `method.invoke(target, 42)` vi phạm chữ ký `read(String)` nên ném **`IllegalArgumentException` trực tiếp**, không tạo `InvocationTargetException` cho tình huống này.

Các phương thức trong `Target` được công bố `public` cho mục đích demo, còn lớp `Target` là nested class cùng phạm vi với mã gọi. Với mã chạy khác package/module, phải tuân thủ quy tắc truy cập Java và JPMS; không suy luận rằng cứ tìm thấy `Method` là có thể gọi.

## 4. Hai chiến lược giữ nguyên nguyên nhân: wrap có cause hoặc ném lại đúng kiểu

### 4.1. Bổ sung ngữ cảnh bằng wrapper mới

Một tầng tích hợp có thể không muốn đưa `IOException` của thư viện nội bộ ra API. Thay vào đó, nó cung cấp ngoại lệ ở cấp ứng dụng:

~~~java
catch (InvocationTargetException ex) {
    Throwable cause = ex.getCause();
    throw new IllegalStateException("Không gọi được phương thức đồng bộ", cause);
}
~~~

Khối trên là **đoạn mã đặt trong một lệnh `try` gọi Reflection**, không phải chương trình độc lập. Constructor `IllegalStateException(String, Throwable)` lưu lại mối quan hệ:

~~~text
IllegalStateException: Không gọi được phương thức đồng bộ
    Caused by: IOException: không đọc được
        at Target.read(...)
        ...
~~~

Khi log đầy đủ `IllegalStateException`, Java có thể in cả chuỗi `Caused by` và các stack frame liên quan. Wrapper mới tạo vị trí/nhãn chẩn đoán ở tầng tích hợp; cause giữ nguyên dấu vết gốc.

Nếu muốn **giữ toàn bộ đường đi qua Reflection**, có thể gắn `ex` làm cause thay vì `ex.getCause()`. Khi đó chuỗi là `ApplicationException -> InvocationTargetException -> IOException`. Cách này không làm mất cause nhưng dài hơn một tầng; chọn theo mức độ cần debug cơ chế Reflection. Khi phân loại lỗi nghiệp vụ, thường cần lấy nguyên nhân đích qua `getCause()`.

**Sai lầm phổ biến:**

~~~java
catch (InvocationTargetException ex) {
    throw new IllegalStateException(ex.getCause().getMessage());
}
~~~

Đây cũng chỉ là mảnh mã minh họa trong khối `try/catch`. Nó **chỉ lấy chuỗi thông điệp**, tạo một exception mới **không có cause**. Lúc log wrapper mới, đường stack trace của `IOException` gốc không còn nối qua `getCause()`.

Tương tự, chỉ ghi `logger.error("{}", ex.getMessage())` có thể làm mất stack trace hoặc chi tiết cause nếu logger không được truyền đối tượng `Throwable`. Dùng API logging của thư viện thực tế để truyền exception làm đối số throwable; ví dụ với SLF4J:

~~~java
logger.error("Lỗi gọi phương thức {}", method.getName(), ex.getCause());
~~~

Đây là **mảnh minh họa phụ thuộc SLF4J**, không thuộc chương trình Java độc lập phía trên. Cần bảo đảm dữ liệu trong exception message/log không làm lộ bí mật, thông tin cá nhân hoặc tham số nhạy cảm.

### 4.2. Giữ nguyên checked exception đã công bố

Nếu API của tầng gọi đã khai báo `throws IOException`, bạn có thể giữ nguyên loại và đối tượng ngoại lệ:

~~~java
Throwable cause = ex.getCause();
if (cause instanceof IOException io) {
    throw io;
}
if (cause instanceof RuntimeException re) {
    throw re;
}
if (cause instanceof Error error) {
    throw error;
}
throw new IllegalStateException("Lỗi ngoài hợp đồng", cause);
~~~

Đoạn trên thuộc **nhánh `catch (InvocationTargetException ex)`**. Kiểu `Throwable` là tổng quát; bạn không thể viết `throw cause;` trong mọi method mà không quan tâm chữ ký `throws`. Nếu phương thức hiện tại chỉ cho phép `IOException`, cần phân loại các nhánh như ví dụ. Có thể dùng `throws Throwable` ở những tầng hạ thấp có chủ ý, nhưng nó làm API khó dùng vì caller phải xử lý quá rộng.

Trong nhánh `IOException`, `throw io` ném **chính đối tượng nguyên nhân gốc**; không tạo wrapper mới và không đổi loại ngoại lệ. Các nhánh `RuntimeException` và `Error` được lan truyền nguyên dạng. Một exception checked khác ngoài hợp đồng được gói trong `IllegalStateException` với cause được giữ lại.

**Không nên tự động bao `Error`** hoặc nuốt những lỗi nghiêm trọng trong một wrapper nghiệp vụ. Chính sách xử lý `Error` phụ thuộc ứng dụng, nhưng khi chọn lan truyền, việc phân biệt nó với lỗi kiểm tra dữ liệu bình thường là quan trọng.

## 5. Khai thác cause chain và stack trace khi chẩn đoán

Một số câu hỏi hữu ích khi gặp `InvocationTargetException` trong log:

1. **Lỗi nào thực sự đầu tiên?** Xem `getCause()`, rồi nếu nó còn cause thì tiếp tục đi theo chuỗi.
2. **Lỗi phát sinh ở đâu?** Xem stack trace của ngoại lệ gốc, tìm frame thuộc code đích thay vì dừng ở `Method.invoke`.
3. **Lỗi thuộc domain hay do host gọi sai?** `InvocationTargetException` nghĩa là target đã ném lỗi; `IllegalAccessException`/`NoSuchMethodException`/`IllegalArgumentException` có thể chỉ ra sai hợp đồng Reflection, cấu hình hoặc quyền.
4. **Có wrapper bổ sung ở tầng trung gian?** Một số framework có thể thêm wrapper để cung cấp context. Đừng giả định rằng `getCause()` chỉ cần gọi đúng một lần là luôn tới root cause.
5. **Có suppressed exception không?** Khi có lỗi đóng tài nguyên trong `try-with-resources`, thông tin này có thể được lưu qua `Throwable.getSuppressed()`; log đầy đủ giúp giữ cả cause và suppressed.

Có thể viết hàm tiện ích duyệt cause chain:

~~~java
static Throwable deepestCause(Throwable throwable) {
    Throwable current = throwable;
    while (current != null
            && current.getCause() != null
            && current.getCause() != current) {
        current = current.getCause();
    }
    return current;
}
~~~

Đây là **phương thức hỗ trợ minh họa**, đặt trong một lớp Java. Nếu có ngoại lệ tự tạo ra chu trình cause phức tạp hơn vòng tự tham chiếu, thuật toán thực tế nên theo dõi tập đối tượng đã gặp để tránh lặp vô hạn. Không luôn thay thế exception bên ngoài bằng deepest cause khi hiển thị: wrapper ngoài còn mang **bối cảnh hoạt động**. Thường log full exception sẽ hữu ích hơn chỉ log root message.

Ví dụ một chuỗi đầy đủ có thể là:

~~~text
IllegalStateException: Không gọi được Target.read
  Caused by: IOException: không đọc được
      tại Target.read(...)
~~~

Nếu thay bằng `new RuntimeException("Lỗi")` không truyền cause, chuỗi quan trọng này biến mất. Nếu truyền `new RuntimeException("Lỗi", cause)`, nó vẫn có thể được lần theo.

### Vị trí catch và thông tin context

Bắt lỗi ở ranh giới giữa hai tầng có thể là hợp lý:

- **Adapter Reflection:** Có tên method, class và input context; có thể chuyển exception sang API ổn định của adapter.
- **Service/application boundary:** Có thông tin nghiệp vụ như operation hoặc request ID; có thể thêm context nhưng không log trùng lặp ở mọi tầng.
- **Top-level handler:** Quyết định phản hồi cho caller, log đầy đủ với chính sách ẩn thông tin nhạy cảm.

Không cần `catch` ở mọi tầng chỉ để thêm một dòng `logger.error` giống nhau. Một log được ghi đúng nơi với cause chain đầy đủ thường dễ hiểu hơn nhiều log lặp cùng stack trace.

## 6. Constructor, checked exception, runtime exception và Error

`Constructor.newInstance(...)` cũng dùng `InvocationTargetException` nếu **body constructor** ném exception:

~~~java
Constructor<Target> constructor = Target.class.getDeclaredConstructor(String.class);
try {
    constructor.newInstance("");
} catch (InvocationTargetException ex) {
    Throwable original = ex.getCause();
    // original là IllegalStateException("token rỗng") trong demo.
}
~~~

Đoạn trên dùng lớp `Target` đã khai báo trong chương trình hoàn chỉnh. Không nhầm với `NoSuchMethodException` của `getDeclaredConstructor(...)` hoặc `InstantiationException` của bước khởi tạo lớp trừu tượng. Các lỗi đó thuộc cơ chế Reflection.

Với lời gọi `Method.invoke`, việc target ném `RuntimeException` hay một `Error` cũng có thể đi qua `InvocationTargetException`. Vì wrapper là checked exception, ở điểm bắt bạn nhận cùng kiểu wrapper để phân loại bên trong. Khi ném lại:

- Checked cause được phép lan truyền nếu API gọi khai báo phù hợp.
- Runtime cause có thể được ném lại theo đúng loại.
- `Error` cần chính sách xử lý riêng; nhiều hệ thống lan truyền thay vì biến thành lỗi nghiệp vụ bình thường.

Trường hợp class initializer (`static`) bị lỗi khi khởi tạo lớp **không nhất thiết** đi qua wrapper target; Java có thể trực tiếp ném `ExceptionInInitializerError`. Vì thế không đưa mọi `ExceptionInInitializerError` vào nhánh xử lý `InvocationTargetException`.

Mã nguồn cũ có thể sử dụng:

~~~java
Class<?> type = Class.forName("example.Plugin");
Object instance = type.newInstance(); // deprecated
~~~

Đây là **mảnh minh họa API cũ**, không yêu cầu chạy: nếu class không tồn tại, `Class.forName` thất bại; và `Class.newInstance()` đã deprecated. Nó có đặc điểm exception propagation khác với `Constructor.newInstance()`. Khi viết code mới, ưu tiên tìm constructor phù hợp rồi gọi `newInstance(...)` trên đối tượng `Constructor<?>`.

## 7. Các lỗi thiết kế thường gặp và cách sửa

| Lối làm dễ lỗi | Hệ quả | Hướng xử lý |
|---|---|---|
| `throw new RuntimeException(ex.getMessage())` | Chỉ giữ chuỗi, mất chuỗi cause | `throw new RuntimeException("...", ex.getCause())` hoặc `throw new RuntimeException("...", ex)` nếu muốn giữ wrapper |
| `throw new RuntimeException(ex.getCause().getMessage())` | Mất loại và stack trace ngoại lệ gốc | Truyền đối tượng `Throwable` vào constructor có cause |
| Chỉ `printStackTrace` rồi `return null` | Host tưởng gọi thành công, lỗi gốc bị che | Trả kết quả thật hoặc lan truyền lỗi có nghĩa |
| Bắt mọi `Exception` rồi báo “method nghiệp vụ lỗi” | Che `NoSuchMethodException`, `IllegalAccessException`, lỗi đối số | Tách lỗi Reflection khỏi lỗi target |
| Dùng `catch (RuntimeException)` để bắt wrapper | `InvocationTargetException` là checked | `catch (InvocationTargetException ex)` hoặc bắt theo hợp đồng checked |
| Luôn viết `throw ex.getCause()` trong API khai báo hẹp | Lỗi compile nếu cause kiểu `Throwable` chưa được khai báo | Phân loại checked/runtime/error hoặc định nghĩa hợp đồng `throws` rõ |
| Luôn dùng `ex.getTargetException()` | API cũ, không đồng nhất với idiom chung | Ưu tiên `ex.getCause()` |
| Chỉ log `ex.getCause().getMessage()` | Thiếu stack trace/cause chain | Log exception object, kiểm soát dữ liệu nhạy cảm |
| Cho rằng `getCause()` không thể `null` trong mọi trường hợp | Có constructor `InvocationTargetException` với target null | Xử lý phòng thủ trong thư viện tổng quát |
| Cố bypass access bằng `setAccessible(true)` mọi nơi | Có thể bị chặn bởi JPMS; che lỗi thiết kế quyền | Ưu tiên API public, `exports`/`opens` phù hợp khi cần |

Cũng cần lưu ý lời gọi phản chiếu có thể đi qua **Java Dynamic Proxy**, callback hoặc tầng framework. Trong Dynamic Proxy, nếu `InvocationHandler.invoke` ném một checked exception **không tương thích `throws` trên interface method**, phía caller có thể gặp `UndeclaredThrowableException`. Đây là cơ chế khác với `Method.invoke` nhưng có thể tạo thêm tầng wrapper. Khi debug, tiếp tục duyệt cause chain thay vì kết luận chỉ từ tên ngoại lệ ngoài cùng.

## 8. Checklist xử lý Reflection trong ứng dụng thực tế

Trước khi gọi Reflection, xác minh tên lớp, tên method, chữ ký tham số, receiver và khả năng truy cập. Dùng `getMethod` cho method public phù hợp, `getDeclaredMethod` khi cần xét member được khai báo trực tiếp; tìm được private member không đồng nghĩa được phép gọi nó trong mọi module.

Khi xử lý exception, nên theo thứ tự:

1. **Bắt đúng `InvocationTargetException`** ở ranh giới `invoke`/`newInstance` nếu bạn cần xử lý lỗi target.
2. **Lấy `Throwable cause = ex.getCause()`**; đừng rút nó thành thông điệp rồi bỏ object.
3. **Chọn chính sách API:** rethrow đúng loại đã khai báo, hay tạo wrapper mới truyền `cause`.
4. **Xử lý các lỗi Reflection khác một cách riêng biệt** để không đánh đồng sai hợp đồng với sai dữ liệu nghiệp vụ.
5. **Log với Throwable đầy đủ**, kèm ngữ cảnh hữu ích (class, method, operation), và giới hạn nội dung nhạy cảm.
6. **Kiểm thử cả trường hợp lỗi**, bao gồm checked exception từ method, runtime exception từ constructor, đối số sai và quyền truy cập.

Khi chỉ cần gọi trực tiếp một kiểu đã biết ở compile time, dùng lời gọi Java thường sẽ có kiểm tra kiểu và xử lý `throws` rõ ràng hơn. Reflection phù hợp khi quyết định kiểu/method nằm ở runtime, nhưng sẽ chuyển trách nhiệm phát hiện lỗi sang phía code tích hợp.

## 9. Bài tập tự kiểm tra

1. Đổi `Target.read` từ `IOException` sang `IllegalArgumentException` trong body. Khi gọi qua `Method.invoke`, ngoại lệ nào ở ngoài cùng? `getCause()` là gì?
2. Đổi `method.invoke(target, 42)` thành `method.invoke(target, "bad")`. Vì sao trường hợp sau có `InvocationTargetException` còn trường hợp đầu ném `IllegalArgumentException` trực tiếp?
3. Sửa `callAndWrap` thành `throw new IllegalStateException("Không gọi được")`. Phần nào của cause chain bị mất? Cách sửa ra sao?
4. Viết thêm một constructor nhận `int` và chủ động ném `IOException`. Gọi bằng `Constructor.newInstance` và xác nhận cause của wrapper. Điều gì khác với lỗi không tìm được constructor đúng chữ ký?
5. Viết một phương thức `callAndPreserve` cho target có thể ném `SQLException`; cập nhật `throws` theo hợp đồng và kiểm tra khả năng bắt trực tiếp `SQLException` ở caller.
6. Tìm trong stack trace hoàn chỉnh hai lớp vị trí: điểm host gọi Reflection và điểm body target ném ngoại lệ. Giải thích vì sao thông điệp của lớp wrapper và message của cause phục vụ hai mục đích khác nhau.

**Điểm cần nhớ:** `InvocationTargetException` là wrapper của lỗi phát sinh trong **method/constructor được gọi**. `getCause()` dẫn tới ngoại lệ thật. Muốn chẩn đoán tốt, giữ nguyên đối tượng cause khi rethrow hoặc thêm wrapper bằng constructor nhận `Throwable`; đồng thời xử lý riêng các lỗi Reflection như sai đối số, sai chữ ký hoặc thiếu quyền.

### Tài liệu chính thức (Java 17)

- [`InvocationTargetException` — `getCause()` và `getTargetException()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html)
- [`Method.invoke` — các ngoại lệ được công bố](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...))
- [`Constructor.newInstance` — wrapper khi constructor ném lỗi](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html#newInstance(java.lang.Object...))
- [`Class.newInstance` đã deprecated](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#newInstance())


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exceptions-diagnostics-007">ExceptionInInitializerError khi thao tác kích hoạt khởi tạo lớp</a>

<details>
<summary>Xem chi tiết</summary>

# ExceptionInInitializerError khi Reflection kích hoạt khởi tạo lớp trong Java

## 1. Tình huống thường gặp

Bạn dùng Reflection để đọc metadata của một class: lấy `Class<?>`, liệt kê method, field, constructor. Mọi thứ hoạt động. Nhưng đến khi gọi method tĩnh, đọc field tĩnh, tạo đối tượng hoặc gọi `Class.forName(name)`, chương trình bất ngờ ném **`ExceptionInInitializerError`**, dù chưa chạy được logic của method đích.

Nguyên nhân thường là **quá trình khởi tạo lớp (class initialization) thất bại**. Những thao tác Reflection có thể kích hoạt khởi tạo lớp trước khi hoàn thành công việc thực tế. Nếu code trong quá trình khởi tạo ném ngoại lệ, bạn thấy lỗi ngay tại thao tác Reflection đã kích hoạt quá trình đó.

Bài này giúp bạn:
- Phân biệt **loading, linking và initialization**; biết khi nào thao tác Reflection làm khởi tạo lớp.
- Hiểu `<clinit>`, static field initializer, static initializer block và `ExceptionInInitializerError`.
- Dự đoán kết quả khi gọi lại một class đã khởi tạo thất bại: thường là **`NoClassDefFoundError`**.
- Phân biệt lỗi khởi tạo lớp với **`ClassNotFoundException`** và **`InvocationTargetException`**.
- Chẩn đoán nguyên nhân gốc và thiết kế thao tác Reflection hạn chế side effect ngoài ý muốn.

Ví dụ được viết cho **Java 17+** và có thể biên dịch bằng JDK tiêu chuẩn; không cần Spring, thư viện bên ngoài hay quyền mở package đặc biệt.

## 2. Class được nạp chưa chắc đã được khởi tạo

Java phân biệt ba giai đoạn quan trọng trong vòng đời của kiểu:

| Giai đoạn | Ý nghĩa | Cần phân biệt |
| --- | --- | --- |
| **Loading (nạp)** | JVM tìm/đọc định nghĩa lớp và tạo biểu diễn runtime cho lớp | Có `Class<?>` không có nghĩa static initializer đã chạy |
| **Linking (liên kết)** | Kiểm tra, chuẩn bị và xử lý tham chiếu ký hiệu theo quy tắc JVM | JVM có thể xử lý một số việc liên kết theo nhu cầu |
| **Initialization (khởi tạo)** | Thực thi logic khởi tạo các biến static và các static block do compiler gom vào phương thức khởi tạo lớp `<clinit>` | Đây là giai đoạn có thể làm phát sinh `ExceptionInInitializerError` |

Ví dụ lớp dưới đây khởi tạo trường static bằng một phương thức:

~~~java
final class Settings {
    static final String TOKEN = loadToken();

    private static String loadToken() {
        throw new IllegalStateException("Missing TOKEN");
    }
}
~~~

`Settings.class` có thể tạo tham chiếu `Class<Settings>` mà chưa gọi `loadToken()`. Nhưng thao tác **chủ động sử dụng** class theo quy tắc khởi tạo có thể chạy `loadToken()` và làm khởi tạo thất bại.

**Lưu ý về `<clinit>`:** đây là tên phương thức khởi tạo lớp ở mức bytecode/JVM; nó không phải API mà bạn thường xuyên gọi trực tiếp bằng `getDeclaredMethod("<clinit>")`. Java compiler có thể kết hợp nhiều field initializer và static block theo thứ tự quy định vào logic khởi tạo. Không phải mọi class đều có code khởi tạo static có side effect.

## 3. Thao tác nào có thể kích hoạt khởi tạo lớp?

Các quy tắc sử dụng tích cực (active use) của Java áp dụng cho cả mã Java thường lẫn nhiều lời gọi Reflection.

| Thao tác | Có khởi tạo class đích chưa được khởi tạo không? | Giải thích |
| --- | --- | --- |
| `SomeType.class` | **Không** chỉ vì tạo class literal | Lấy biểu diễn `Class`, không chạy initializer của `SomeType` |
| `obj.getClass()` | **Không** vì lệnh gọi này | Object đã tồn tại; lấy metadata không phải yêu cầu khởi tạo mới |
| `Class.forName(name)` | **Có**, theo overload một tham số | Tìm lớp và yêu cầu khởi tạo lớp |
| `Class.forName(name, false, loader)` | **Không** do tham số `false` | Có thể nạp/liên kết; không yêu cầu khởi tạo lớp đích |
| `type.getDeclaredMethod("run")` | **Không** chỉ vì tra cứu | Lấy metadata `Method` (có thể kéo theo xử lý kiểu tham chiếu) |
| `type.getDeclaredFields()` | **Không** chỉ vì liệt kê metadata | Chưa đọc giá trị field |
| `staticMethod.invoke(null)` | **Có**, với method tĩnh của lớp chưa khởi tạo | JVM khởi tạo lớp khai báo method trước khi thực thi method |
| `staticField.get(null)` hoặc `getInt(null)` | **Có**, với field tĩnh của lớp chưa khởi tạo | Reflection yêu cầu khởi tạo lớp khai báo field trước khi đọc giá trị |
| `constructor.newInstance(args)` | **Có**, khi tạo instance của lớp chưa khởi tạo | Phải khởi tạo class trước khi tạo và dùng instance |
| `new SomeType()` | **Có** | Đây là hành động tạo instance trực tiếp, không cần Reflection |

Các diễn giải trên xét class đích **chưa được khởi tạo** và hoạt động thông thường của JDK. Không đồng nghĩa việc thao tác metadata hoàn toàn không thể nạp các kiểu liên quan hoặc gặp các lỗi khác. Việc khởi tạo superclass hay superinterface có default method cũng có thể tham gia vào quá trình khởi tạo kiểu theo quy tắc JVM.

**Ngoại lệ cần nhớ:** đọc trực tiếp một **constant variable** kiểu primitive hoặc `String` có giá trị hằng compile-time (ví dụ `static final int LIMIT = 10`) thường không kích hoạt khởi tạo class khai báo vì giá trị có thể được compiler nội tuyến. Quy tắc này không có nghĩa `Field.get(null)` được miễn khởi tạo: **đọc field static qua Reflection vẫn yêu cầu khởi tạo lớp khai báo field nếu chưa khởi tạo**.

## 4. Cơ chế phát sinh ExceptionInInitializerError

Khi JVM tiến hành khởi tạo một class, nó chạy phần static initialization. Nếu quá trình này kết thúc bất thường:

1. JVM xác định `Throwable` gây lỗi.
2. Nếu `Throwable` đó **không phải `Error`**, JVM bọc nó thành **`ExceptionInInitializerError`**; lỗi gốc trở thành cause.
3. Nếu nguyên nhân **đã là `Error`**, JVM có thể truyền chính `Error` đó ra ngoài mà không tạo thêm `ExceptionInInitializerError`.
4. JVM đánh dấu `Class` tương ứng là **erroneous** (khởi tạo lỗi) trong phạm vi định danh lớp đó (bao gồm defining class loader).
5. Lần sử dụng tích cực tiếp theo của cùng class trong trạng thái erroneous sẽ thất bại với **`NoClassDefFoundError`** thay vì chạy lại static initialization.

Chẳng hạn:

~~~java
static int number = initialize();

static int initialize() {
    throw new IllegalStateException("Invalid configuration");
}
~~~

Trong lần khởi tạo đầu tiên, `IllegalStateException` là một `RuntimeException` (không phải `Error`), nên JVM bọc bằng **`ExceptionInInitializerError`**. Người xử lý lỗi lấy được nguyên nhân bằng `error.getCause()`.

**Đừng suy luận ngược tuyệt đối rằng mọi lỗi static initialization đều có `ExceptionInInitializerError`:** static initialization ném `AssertionError`, `OutOfMemoryError` hay một `Error` khác có thể khiến lần kích hoạt đầu nhận trực tiếp `Error`. Các lỗi trong bước nạp hoặc liên kết cũng có loại exception/error riêng.

Dù có từ **Exception** trong tên, **`ExceptionInInitializerError extends LinkageError`**, mà `LinkageError extends Error`. Vì vậy **`catch (Exception)` không bắt được `ExceptionInInitializerError`**.

## 5. Ví dụ hoàn chỉnh: tra cứu metadata bình thường, invoke thất bại

Lưu mã sau thành **`InitErrorReflectionDemo.java`** và dùng **JDK 17 trở lên**.

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InitErrorReflectionDemo {
    private static int fail(String label) {
        System.out.println("initializing " + label);
        throw new IllegalStateException("failure " + label);
    }

    public static class BrokenInvoke {
        static int value = fail("invoke");

        public static int value() {
            return value;
        }
    }

    public static class BrokenForName {
        static int value = fail("forName");
    }

    public static void main(String[] args) throws Exception {
        // false: chỉ yêu cầu nạp lớp, không khởi tạo BrokenInvoke.
        Class<?> type = Class.forName(
                BrokenInvoke.class.getName(),
                false,
                InitErrorReflectionDemo.class.getClassLoader());

        System.out.println("metadata: " + type.getSimpleName());

        // Tra cứu method cũng chưa chạy static initialization.
        Method method = type.getDeclaredMethod("value");
        System.out.println("lookup: " + method.getName());

        // Gọi static method: JVM phải khởi tạo BrokenInvoke trước.
        try {
            method.invoke(null);
        } catch (ExceptionInInitializerError e) {
            System.out.println(
                    "invoke first: " + e.getClass().getSimpleName());
            System.out.println(
                    "root cause: " + e.getCause().getClass().getSimpleName());
        } catch (InvocationTargetException e) {
            // Chỉ để minh họa rằng lỗi khởi tạo lớp không đến qua
            // InvocationTargetException trong trường hợp này.
            System.out.println("unexpected wrapper: " + e);
        }

        // Cùng Class đã ở trạng thái erroneous: không init lại.
        try {
            method.invoke(null);
        } catch (NoClassDefFoundError e) {
            System.out.println(
                    "invoke second: " + e.getClass().getSimpleName());
        }

        // Overload Class.forName một tham số yêu cầu khởi tạo lớp.
        try {
            Class.forName(BrokenForName.class.getName());
        } catch (ExceptionInInitializerError e) {
            System.out.println(
                    "forName first: " + e.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy tại thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 InitErrorReflectionDemo.java
java InitErrorReflectionDemo
~~~

**Đầu ra chính xác (đã kiểm chứng trên JDK 21):**

~~~text
metadata: BrokenInvoke
lookup: value
initializing invoke
invoke first: ExceptionInInitializerError
root cause: IllegalStateException
invoke second: NoClassDefFoundError
initializing forName
forName first: ExceptionInInitializerError
~~~

Đọc kết quả theo thứ tự:

1. `Class.forName(..., false, loader)` trả metadata của **BrokenInvoke** và in `metadata: BrokenInvoke`; không in dòng **initializing** vì chưa khởi tạo class.
2. `getDeclaredMethod("value")` tìm thấy method tĩnh và in `lookup: value`; vẫn chưa khởi tạo class.
3. `method.invoke(null)` yêu cầu khởi tạo class khai báo method trước khi gọi method. Static field initializer chạy và in `initializing invoke`, sau đó ném **IllegalStateException**. JVM bọc thành **ExceptionInInitializerError**.
4. Vì khởi tạo thất bại, method body **value()** thực tế chưa được thực thi. Lần `method.invoke(null)` thứ hai trên cùng class nhận **NoClassDefFoundError** và **không in lại** `initializing invoke`.
5. Với lớp **BrokenForName** khác, `Class.forName(name)` yêu cầu khởi tạo ngay lập tức. Nó in `initializing forName` rồi ném **ExceptionInInitializerError**.

Ví dụ dùng hai nested static class độc lập để quan sát hai đường kích hoạt khởi tạo. Nếu chỉ dùng một class đã fail rồi thử nhiều cách kích hoạt, những lần sau thường chỉ còn thấy **NoClassDefFoundError**, khiến người học dễ tưởng lệnh kiểm thử thứ hai là nguyên nhân ban đầu.

## 6. Phân biệt bốn loại lỗi dễ nhầm

| Lỗi | Điều gì thất bại? | Đặc điểm chẩn đoán |
| --- | --- | --- |
| **ClassNotFoundException** | Yêu cầu nạp class theo tên nhưng class không được tìm thấy trong phạm vi class loader thích hợp | Checked exception; thường ở `Class.forName` hoặc `ClassLoader.loadClass` |
| **ExceptionInInitializerError** | Khởi tạo class thất bại; nguyên nhân ban đầu không phải `Error` | Là **Error**; `getCause()` tiết lộ lỗi gốc như cấu hình sai, null, illegal state |
| **NoClassDefFoundError** | JVM không thể sử dụng định nghĩa class như kỳ vọng, ví dụ class đã khởi tạo thất bại ở lần trước hoặc thiếu dependency khi runtime | Là **LinkageError**; kiểm tra message, cause và lần lỗi đầu tiên |
| **InvocationTargetException** | **Method.invoke** đã gọi được method đích, nhưng method đích ném Throwable | Checked exception; `getCause()` là ngoại lệ từ method body |

Lỗi được ném trong **method body** khi gọi qua `Method.invoke` thường được bọc thành **InvocationTargetException**. Nhưng lỗi xảy ra **trước khi method body có thể chạy**, trong quá trình khởi tạo class phục vụ một lời gọi tĩnh, không phải ngoại lệ do method body ném. Ví dụ trên chứng minh nhánh này ném **ExceptionInInitializerError** trực tiếp thay vì **InvocationTargetException**.

Không nhầm thông điệp **"Could not initialize class ..."** của **NoClassDefFoundError** với **ClassNotFoundException**. Tên class có thể đã tìm được đầy đủ: vấn đề là JVM đã đánh dấu class đó ở trạng thái khởi tạo lỗi.

**Điều kiện phân biệt quan trọng:** không phải mọi **NoClassDefFoundError** đều bắt nguồn từ khởi tạo thất bại. Nó còn có thể xảy ra vì phụ thuộc runtime không có mặt dù class liên quan từng biên dịch được. Xem root cause và chuỗi sự kiện trước khi sửa.

## 7. Vai trò của Field.get, Constructor.newInstance và Class.forName

### Đọc static field bằng Reflection

Các thao tác tra cứu:

~~~java
Class<?> type = Settings.class;
var field = type.getDeclaredField("TOKEN");
~~~

thường chỉ lấy metadata, không khởi tạo **Settings**. Nhưng thao tác đọc:

~~~java
Object value = field.get(null);
~~~

sẽ yêu cầu khởi tạo class khai báo field static nếu chưa khởi tạo. Truyền **null** là thích hợp cho field static vì không cần instance đích. Nếu initialization thất bại, `field.get(null)` có thể ném **ExceptionInInitializerError**; không phải `NoSuchFieldException` chỉ vì field đã tìm thấy.

**Phân biệt:** `Field.get` có thể ném **IllegalAccessException** vì kiểm tra quyền truy cập; đó là vấn đề độc lập với khởi tạo lớp. Không dùng `setAccessible(true)` theo phản xạ khi stack trace thực chất chỉ đến một initializer bị lỗi.

### Tạo instance bằng Constructor.newInstance

Một `Constructor<?> constructor = type.getDeclaredConstructor();` chỉ tra cứu constructor. Nhưng `constructor.newInstance()` kích hoạt khởi tạo class nếu cần, trước khi tạo instance. Nếu static initialization fail, lỗi xuất hiện tại `newInstance`; constructor body có thể chưa bắt đầu.

Nếu constructor body đã chạy và ném exception, API Reflection sẽ phản ánh qua **InvocationTargetException**. Cần xác định lỗi ở phase khởi tạo class hay trong constructor body trước khi chọn catch phù hợp.

### Hai overload của Class.forName

~~~java
Class<?> a = Class.forName("com.example.Plugin");
Class<?> b = Class.forName(
        "com.example.Plugin",
        false,
        Thread.currentThread().getContextClassLoader());
~~~

Dòng thứ nhất **yêu cầu khởi tạo** class `Plugin`. Dòng thứ hai dùng `initialize=false`, hữu ích cho công cụ discovery cần đọc metadata trước khi quyết định có sử dụng plugin không.

Hai dòng là **minh họa**, không phải ví dụ độc lập có sẵn class `com.example.Plugin`; cần class thật với tên và class loader tương ứng. Lệnh `Class.forName(..., false, ...)` không bảo đảm toàn bộ hệ thống không có side effect khác (ví dụ class loader tùy biến), nhưng bản thân nó không yêu cầu khởi tạo class đích.

## 8. Điều xảy ra sau lần khởi tạo thất bại

Đối với cùng một `Class` và defining class loader, JVM ghi nhận class ở trạng thái erroneous khi khởi tạo thất bại. Vì vậy:

- **Không khởi tạo lại class** chỉ vì gọi lại `Class.forName(name)`, `Method.invoke`, `Field.get` hay `Constructor.newInstance` trong cùng quá trình nạp lớp.
- Các lần active use tiếp theo có thể ném **NoClassDefFoundError**, thường kèm thông báo không thể initialize class.
- Nếu ứng dụng thay đổi cấu hình bên ngoài rồi lập tức thử lại cùng class trong cùng class loader, việc khởi tạo **không tự được chạy lại**.
- Một class cùng tên được định nghĩa bằng class loader khác có danh tính runtime khác và có thể có vòng đời khởi tạo riêng. Đây là kiến thức để hiểu phạm vi lỗi, **không phải mẹo khuyến khích tạo class loader mới để che lỗi cấu hình**.

Tính chất này đặc biệt quan trọng ở hệ thống plugin, test runner hoặc ứng dụng sử dụng Reflection: lần lỗi đầu tiên cung cấp stack trace gần nguyên nhân nhất; các lỗi **NoClassDefFoundError** sau đó thường chỉ là hậu quả.

**Với lớp đã fail, ưu tiên sửa root cause và tái khởi động hoặc dùng vòng đời class loader được thiết kế sẵn**, thay vì viết vòng lặp retry không có khả năng đưa class về trạng thái bình thường.

## 9. Cách chẩn đoán ExceptionInInitializerError có hệ thống

Khi thấy `ExceptionInInitializerError` hoặc `NoClassDefFoundError: Could not initialize class ...`, thực hiện theo thứ tự:

1. **Xác định thao tác đầu tiên kích hoạt class initialization**: `Class.forName(name)`, `Method.invoke` method tĩnh, `Field.get` field tĩnh, `Constructor.newInstance`, hay lời gọi Java trực tiếp.
2. **Tìm lần lỗi đầu tiên trong cùng class loader**. Tránh chỉ nhìn stack trace ở lần retry sau khi class đã erroneous.
3. **Mở chuỗi cause** của `ExceptionInInitializerError` bằng `getCause()` (hoặc API lịch sử `getException()`). Đừng dừng ở tên wrapper.
4. **Kiểm tra các static field initializer, static block và phụ thuộc được khởi tạo**. Xem truy cập file/cấu hình, biến môi trường, parsing, phụ thuộc network, thứ tự static field, null, hoặc exception bị ném từ helper.
5. **Phân biệt phase**: nếu lỗi do method/constructor body sau khi class đã khởi tạo thành công, xem `InvocationTargetException`; nếu initializer fail trước method body, tập trung vào khởi tạo lớp.
6. **Xác nhận class loader và cấu hình runtime**. Cùng tên class có thể xuất hiện trong nhiều loader; classpath/module path cũng có thể làm nạp hoặc liên kết thất bại.
7. **Viết test cách ly có thể lặp lại**. Dùng class riêng chưa từng được initialize cho mỗi kịch bản, hoặc chạy trong JVM mới để quan sát chính xác lần lỗi đầu.

Ví dụ để lấy nguyên nhân từ lỗi khởi tạo:

~~~java
try {
    Class.forName("com.example.Plugin");
} catch (ExceptionInInitializerError e) {
    Throwable root = e.getCause();
    System.err.println("Static initialization failed: " + root);
}
~~~

Đoạn trên là ví dụ cho ứng dụng **có class Plugin tương ứng**. Bắt **Error** có chủ đích ở một ranh giới giám sát có thể giúp ghi log nguyên nhân; **không nên coi bắt được lỗi là class đã có thể tiếp tục dùng an toàn**.

Nếu có nhiều tầng wrapper (ví dụ framework bọc thêm một exception), duyệt chuỗi `getCause()` và lưu stack trace đầy đủ để tìm sự kiện đầu tiên. Tránh chỉ dùng `getMessage()` vì đôi khi nó không chỉ rõ nguyên nhân.

## 10. Giảm khởi tạo lớp ngoài ý muốn khi viết công cụ Reflection

- **Discovery trước, activation sau:** khi chỉ liệt kê plugin hoặc đọc thông tin cấu trúc, cân nhắc `Class.forName(name, false, loader)` và API metadata. Chỉ khởi tạo khi quyết định sử dụng class.
- **Giữ static initialization đơn giản:** tránh hoạt động IO, network, truy cập biến môi trường bất ổn hoặc phụ thuộc bên ngoài trong static field initializer nếu có thể hoãn sang thao tác khởi tạo tường minh, dễ trả lỗi có ngữ cảnh.
- **Phân biệt method static với instance:** `Method.invoke(null)` gọi method tĩnh, yêu cầu khởi tạo class; instance method cần receiver đã được tạo thích hợp, và quá trình tạo instance thông thường đã khởi tạo class.
- **Giữ nguyên cause khi báo lỗi:** bọc lỗi thành ngoại lệ nghiệp vụ tại ranh giới phù hợp nhưng không xóa stack trace gốc.
- **Đừng thử chữa bằng quyền Reflection:** `setAccessible(true)` xử lý một số quy tắc kiểm tra truy cập và có thể bị giới hạn bởi module; nó không chữa lỗi từ static initializer.
- **Không retry cùng class vô hạn:** JVM không tự chuyển class erroneous về trạng thái chưa khởi tạo.

Một API plugin tốt nên tách **inspect/discover** (không cần thực thi) khỏi **activate/create** (có thể chạy code khởi tạo), và có bước ghi lỗi rõ ràng tại ranh giới kích hoạt.

## 11. Những hiểu nhầm cần sửa

| Hiểu nhầm | Thực tế |
| --- | --- |
| “Lấy được `Class<?>` tức là static block đã chạy” | Metadata có thể tồn tại khi class chưa được initialize |
| “`getDeclaredMethod()` làm chạy method” | Nó chỉ tra cứu metadata; `invoke()` mới gọi method |
| “Mọi lỗi phát sinh từ `Method.invoke` đều là `InvocationTargetException`” | Lỗi khởi tạo lớp khi gọi static method có thể là `ExceptionInInitializerError` trực tiếp |
| “`ExceptionInInitializerError` là một checked exception” | Nó kế thừa `Error`, không được `catch (Exception)` bắt |
| “Lần khởi tạo đầu thất bại thì retry sẽ chạy lại static block” | Cùng class/loader đã erroneous; lần dùng sau thường ném `NoClassDefFoundError` |
| “Cứ thấy `NoClassDefFoundError` là thiếu file .class” | Nó cũng xuất hiện khi lớp đã khởi tạo thất bại ở lần trước |
| “Field `static final` không bao giờ kích hoạt init” | Compile-time constant trực tiếp có ngoại lệ riêng; **`Field.get` đọc static field vẫn yêu cầu initialization** |

## 12. Bài tập thực hành và tự đánh giá

**Bài tập 1 — tách tra cứu và kích hoạt.** Trong chương trình mẫu, thay `method.invoke(null)` bằng `type.getDeclaredFields()` và in tên field. Dự đoán: metadata được in nhưng **không xuất hiện** `initializing invoke` chỉ vì việc liệt kê. Sau đó thêm `Field.get(null)` trên field static và quan sát thời điểm xuất hiện lỗi.

**Bài tập 2 — thay loại nguyên nhân.** Thay `throw new IllegalStateException(...)` bằng `throw new AssertionError(...)` trong helper. Dự đoán: lần kích hoạt đầu có thể nhận **AssertionError** trực tiếp, còn lần sử dụng lại vẫn có thể gặp **NoClassDefFoundError** do class erroneous. Giải thích quy tắc JVM chỉ bọc nguyên nhân không phải `Error`.

**Bài tập 3 — phân biệt lỗi trước và trong method.** Tạo một class khác có static initialization thành công, nhưng method tĩnh `run()` ném `IllegalStateException`. Gọi nó qua `Method.invoke(null)`; giải thích vì sao trường hợp này nhận **InvocationTargetException** thay cho lỗi khởi tạo.

**Bài tập 4 — Class.forName.** So sánh `Class.forName(name, false, loader)` với `Class.forName(name)` trên hai class mẫu chưa được khởi tạo. In log từ static block để xác định khi nào quá trình khởi tạo xảy ra.

**Câu hỏi tự kiểm tra:**
1. Vì sao thao tác `Class.forName(name, false, loader)` thành công nhưng `Method.invoke(null)` sau đó vẫn có thể thất bại?
2. Nếu nguyên nhân gốc là `NullPointerException` trong static field initializer, lần kích hoạt đầu thường thấy lỗi nào? Lần tiếp theo trên cùng class thường thấy lỗi nào?
3. Vì sao `catch (Exception e)` không giữ được `ExceptionInInitializerError`?
4. Tại sao cần tìm **lần lỗi đầu tiên** chứ không chỉ đọc `NoClassDefFoundError` ở lần gọi thứ hai?

**Đáp án ngắn:** metadata không đồng nghĩa với initialization; `NullPointerException` khi initialize thường được bọc thành `ExceptionInInitializerError`; lần sử dụng kế tiếp của class erroneous thường là `NoClassDefFoundError`; `ExceptionInInitializerError` là `Error`; lần lỗi đầu chỉ ra nguyên nhân và vị trí khởi tạo thất bại.

**Tiêu chí hoàn thành:** bạn chạy được ví dụ Java 17+, giải thích chính xác bảy dòng stdout, phân biệt lỗi từ class initialization và method body, xác định thao tác Reflection kích hoạt initialization, đồng thời biết dùng root cause để sửa lỗi thay vì retry class erroneous.

**Tài liệu chuẩn để học sâu:** *Java Language Specification*, mục **12.4 Initialization of Classes and Interfaces**, và Javadoc của `Class.forName`, `Method.invoke`, `Field.get`, `Constructor.newInstance`, `ExceptionInInitializerError`, `NoClassDefFoundError`, `InvocationTargetException`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exceptions-diagnostics-008">GenericSignatureFormatError, TypeNotPresentException và MalformedParameterizedTypeException</a>

<details>
<summary>Xem chi tiết</summary>

# GenericSignatureFormatError, TypeNotPresentException và MalformedParameterizedTypeException khi dùng Java Reflection

## 1. Vì sao reflection đọc generic lại phát sinh lỗi?

Khi chương trình Java biên dịch các kiểu như `List<String>` hoặc `class Child extends Parent<Long>`, JVM chủ yếu làm việc với **kiểu đã xóa (erasure)**, còn thông tin tham số kiểu được compiler lưu trong thuộc tính **`Signature`** của class file khi cần. Các API `java.lang.reflect` đọc metadata này để tạo cây `Type` (`Class`, `ParameterizedType`, `TypeVariable`, `WildcardType`, `GenericArrayType`).

Ví dụ, trường `List<String> names` thường có hai lớp thông tin:

| Lớp thông tin | Diễn đạt | Truy cập |
| --- | --- | --- |
| Descriptor sau erasure | `Ljava/util/List;` | `Field.getType()` trả `List.class` |
| Generic signature | `Ljava/util/List<Ljava/lang/String;>;` | `Field.getGenericType()` trả một `ParameterizedType` chứa `String` |

**Hệ quả:** Một class có thể được tải, một field có thể được tìm thấy và kiểu raw `List` có thể được đọc, nhưng thao tác **phân tích hoặc phân giải generic signature** vẫn thất bại. Không nên coi `Class.forName(...)` thành công là bằng chứng mọi metadata generic đều hợp lệ.

Ba lỗi của bài là:

- `java.lang.reflect.GenericSignatureFormatError`: biểu diễn `Signature` **sai cú pháp** theo đặc tả JVM.
- `java.lang.TypeNotPresentException`: metadata cần **một kiểu không thể phân giải**, thường do thiếu class/dependency khi chạy.
- `java.lang.reflect.MalformedParameterizedTypeException`: cấu trúc tham số hóa **sai về ngữ nghĩa**, dù chuỗi signature có thể đúng cú pháp.

Chúng thường xuất hiện khi quét thư viện, ORM, serialization, dependency injection, annotation processor/runtime scanner, proxy/bridge metadata, plugin hoặc class do công cụ bytecode sinh/biến đổi. Tuy nhiên đây **không phải** ba lỗi mà mọi reflection call đều ném; phụ thuộc API và phần metadata mà nó cần phân giải.

## 2. Bảng phân biệt nhanh ba lỗi

| Lỗi | Quan hệ kế thừa | Tình huống đặc trưng | Hướng kiểm tra |
| --- | --- | --- | --- |
| `GenericSignatureFormatError` | `Error → LinkageError → ClassFormatError → GenericSignatureFormatError` | Thuộc tính `Signature` không tuân thủ grammar, ví dụ thiếu dấu kết thúc | Kiểm tra class file, bytecode generator/transformer, phiên bản artifact |
| `TypeNotPresentException` | `RuntimeException` | `Signature` đề cập `com.example.Missing` nhưng class loader không tìm thấy định nghĩa | Đọc `typeName()`, `getCause()` và dependency/classpath/module path |
| `MalformedParameterizedTypeException` | `RuntimeException` | Signature có tham số hóa nhưng không thể tạo `ParameterizedType` hợp lệ, ví dụ số type arguments sai | Kiểm tra số lượng/owner type của tham số generic, version mismatch và bytecode đã biến đổi |

**Ranh giới quan trọng:** `GenericSignatureFormatError` là một `Error`, không phải `Exception`; `catch (RuntimeException e)` không bắt được nó. Hai lỗi còn lại đều là unchecked `RuntimeException`. Không nên dùng `catch (Throwable)` bừa bãi và tiếp tục khởi tạo hệ thống sau khi phát hiện class metadata hỏng.

### 2.1. `GenericSignatureFormatError` — lỗi cú pháp của Signature

Theo Java API, lỗi xảy ra khi một reflection method cần diễn giải một thuộc tính `Signature` không tuân thủ cú pháp đặc tả class file. Ví dụ minh họa **chỉ là chuỗi trong class file, không phải mã Java hợp lệ**:

~~~text
Hợp lệ:
Ljava/util/List<Ljava/lang/String;>;

Sai cú pháp (thiếu dấu kết thúc cho type arguments và class type):
Ljava/util/List<Ljava/lang/String;
~~~

Một `Signature` sai có thể được viết bởi bytecode generator, instrumentation/weaver, obfuscator hoặc quá trình sửa class file. Compiler `javac` bình thường không xuất ra kiểu lỗi này từ mã nguồn Java hợp lệ. Đừng tạo ví dụ bằng cách viết `List<String, Integer>` trong source rồi mong chạy đến reflection: source sẽ bị compiler từ chối trước.

Vì thuộc tính `Signature` là metadata bổ sung, lỗi có thể chỉ lộ ra khi API parse generic được gọi. Mặt khác, class file hỏng nghiêm trọng có thể bị JVM từ chối sớm bằng lỗi khác; không được bảo đảm mọi trường hợp sai bytecode đều đợi đến `getGenericType()`.

### 2.2. `TypeNotPresentException` — không tìm thấy kiểu khi phân giải metadata

Đây là exception unchecked thuộc **`java.lang`**. API `typeName()` trả tên kiểu không có định nghĩa, và `getCause()` có thể chứa nguyên nhân, ví dụ `ClassNotFoundException`; cause được phép `null`. Có thể thấy lỗi khi reflection đọc generic signature, hoặc khi đọc annotation runtime chứa giá trị `Class` không phân giải được.

Ví dụ: source ban đầu có `List<demo.Absent>`; sau khi đóng gói, `demo.Absent` biến mất nhưng `Holder.class` vẫn giữ generic `Signature`. Field raw type vẫn là `java.util.List`; lúc `getGenericType()` phân giải tham số kiểu, lỗi xuất hiện.

Đừng đồng nhất với:

- `ClassNotFoundException`: checked exception thường gặp khi chủ động tìm class theo tên bằng `Class.forName(...)` / `ClassLoader.loadClass(...)`; cũng có thể nằm trong `cause`.
- `NoClassDefFoundError`: lỗi liên kết/tải khi JVM cần một định nghĩa class tại runtime; không mặc định biến thành `TypeNotPresentException`.
- `NoSuchMethodException` / `NoSuchFieldException`: tra cứu thành viên không tồn tại, không nói rằng `Signature` generic bị lỗi.

### 2.3. `MalformedParameterizedTypeException` — sai cấu trúc type arguments

Đây là unchecked `RuntimeException`: reflection cần tạo một kiểu tham số hóa nhưng biểu diễn đó **không hợp lệ về ngữ nghĩa**. Ví dụ tiêu biểu trong Java API: số lượng đối số kiểu không phù hợp với khai báo của raw type.

Ví dụ minh họa bằng chuỗi signature (không phải chương trình Java chạy được):

~~~text
Đúng cú pháp, và hợp lệ với java.util.List<E>:
Ljava/util/List<Ljava/lang/String;>;

Đúng dạng cú pháp tham số hóa nhưng sai số lượng đối số cho List<E>:
Ljava/util/List<Ljava/lang/String;Ljava/lang/Integer;>;
~~~

`List` chỉ khai báo **một** biến kiểu `E`; hai type arguments không thể tương ứng với khai báo này. Đây là lỗi khác hẳn việc thiếu ký tự trong grammar. Các vấn đề metadata owner/nested parameterization không phù hợp cũng cần được xem xét, nhưng không nên khẳng định mọi bound generic bị vi phạm đều chắc chắn ném exception này: cách kiểm tra cụ thể phụ thuộc API và biểu diễn được tạo.

## 3. API nào có thể phát hiện các lỗi này?

Các method truy vấn **generic metadata** trong Java SE 21 thường công bố ba lỗi trên trong JavaDoc, tùy thuộc vào signature cụ thể:

| API | Metadata đang phân giải |
| --- | --- |
| `Class.getGenericSuperclass()` | Superclass dạng `Parent<String>` |
| `Class.getGenericInterfaces()` | Interface dạng `Comparable<MyType>` |
| `Field.getGenericType()` | Kiểu khai báo của field như `Map<String, User>` |
| `Method.getGenericReturnType()` | Kiểu trả về khai báo như `List<Response>` |
| `Method.getGenericParameterTypes()` | Các tham số như `Optional<Request>` |
| `Constructor/Method.getGenericExceptionTypes()` | Kiểu trong mệnh đề `throws` của `Executable` |
| `Class.getTypeParameters()` / `Method.getTypeParameters()` | Khai báo biến kiểu và các giới hạn (bounds) |

Một số phép truy vấn có thể tạo `Type` lazily; lỗi có thể phát sinh khi truy cập sâu hơn vào `ParameterizedType.getActualTypeArguments()`, `TypeVariable.getBounds()` hoặc kiểu owner. Do đó, khi chẩn đoán, ghi lại **phương thức reflection chính xác** và **thời điểm chạm vào cây Type**, không chỉ tên class đang scan.

Các API trả kiểu sau erasure như `Field.getType()` hoặc `Method.getReturnType()` không có cùng nghĩa với API generic; thành công ở API raw không chứng minh Signature hay dependencies của type arguments đầy đủ.

## 4. Ví dụ Java 17+: tái hiện TypeNotPresentException thật

Ví dụ dưới đây tạo một class có field `List<Absent>`, biên dịch đầy đủ, sau đó **cố ý xóa một class trong thư mục thử nghiệm**. Metadata generic còn đề cập `demo.Absent`, nhưng class không còn trong classpath. Lưu ý: chỉ xóa file thử nghiệm được tạo trong thư mục riêng; không xóa class của dự án đang chạy.

Tạo ba tệp đúng tên:

**`src/demo/Absent.java`**

~~~java
package demo;

public class Absent {}
~~~

**`src/demo/Holder.java`**

~~~java
package demo;

import java.util.List;

public class Holder {
    public List<Absent> values;
}
~~~

**`src/GenericDiagnosticDemo.java`**

~~~java
import java.lang.reflect.Field;

public class GenericDiagnosticDemo {
    public static void main(String[] args) throws Exception {
        Field field = Class.forName("demo.Holder")
                .getDeclaredField("values");

        System.out.println("Erased field type: "
                + field.getType().getTypeName());

        try {
            System.out.println("Generic type: "
                    + field.getGenericType().getTypeName());
        } catch (TypeNotPresentException e) {
            System.out.println("Missing generic type: " + e.typeName());
            Throwable cause = e.getCause();
            System.out.println("Cause class: "
                    + (cause == null ? "none" : cause.getClass().getName()));
        }
    }
}
~~~

Trong một thư mục thử nghiệm mới, tạo `src/demo` và `classes`, đặt các file vào đường dẫn trên rồi thực hiện:

~~~bash
javac --release 17 -d classes src/demo/Absent.java src/demo/Holder.java src/GenericDiagnosticDemo.java
rm classes/demo/Absent.class
java -cp classes GenericDiagnosticDemo
~~~

Trên Windows PowerShell, thay dòng `rm` bằng `Remove-Item classes/demo/Absent.class`; các lệnh `javac` và `java` vẫn như trên.

**Stdout** của tình huống này trên JDK 17+:

~~~text
Erased field type: java.util.List
Missing generic type: demo.Absent
Cause class: java.lang.ClassNotFoundException
~~~

Điều đáng học: `Class.forName("demo.Holder")`, `getDeclaredField("values")` và `field.getType()` vẫn chạy được. `field.getGenericType()` mới buộc reflection phân giải `Absent` trong generic signature và ném `TypeNotPresentException`. Nếu bạn giữ `Absent.class`, lời gọi này trả `java.util.List<demo.Absent>` bình thường.

Khi phân tích file đã biên dịch, dùng:

~~~bash
javap -v -p -classpath classes demo.Holder
~~~

Trong output của `javap`, tìm trường `values` và dòng `Signature` để thấy metadata giữ tham chiếu tới `demo.Absent`. Đây là lệnh chẩn đoán class file, output dài và phụ thuộc bản JDK nên không cần so khớp toàn bộ output.

## 5. Quy trình chẩn đoán trong ứng dụng thực tế

Khi framework scan generic type của class từ thư viện khác, thực hiện theo thứ tự:

1. **Ghi rõ vị trí lỗi.** Class, tên field/method, API gây lỗi (ví dụ `Field.getGenericType()`), exception đầy đủ, `typeName()` nếu là `TypeNotPresentException`, cause và stack trace. Không log dữ liệu nghiệp vụ nhạy cảm.
2. **Kiểm tra nguồn class đang thực sự được tải.** Xem `clazz.getProtectionDomain().getCodeSource()` khi có giá trị và class loader/module liên quan; kiểm tra có hai phiên bản JAR của cùng thư viện hoặc class trùng ở nhiều class loader không.
3. **Đối chiếu runtime dependencies.** Với `TypeNotPresentException`, tìm class thiếu trong artifact và dependency tree; kiểm tra shading/relocation, optional dependency, đóng gói module và phiên bản API–implementation.
4. **Đọc class file.** Dùng `javap -v -p`, xem `Signature` và raw descriptor. Với `GenericSignatureFormatError`, kiểm tra grammar và công cụ sinh/sửa bytecode. Với `MalformedParameterizedTypeException`, so arity và cấu trúc type arguments với khai báo generic thực tế của raw type.
5. **Tái hiện trong classpath tối giản.** Chạy cùng JDK và cùng cặp JAR đang triển khai; có thể thử build sạch, nhưng không thay class file sản xuất trực tiếp khi đang phân tích. Tìm bước build/weaving/obfuscation thay đổi `Signature`.
6. **Khắc phục tại nguồn.** Bổ sung dependency chính xác, đồng bộ binary versions, sửa bytecode transformer/generator, hoặc loại bỏ artifact hỏng rồi build lại; không dùng `--add-opens` như cách chữa mặc định vì đây thường không phải lỗi quyền truy cập reflection.

### Một mẫu xử lý lỗi có chủ đích

Đoạn sau là **phần thân mẫu** của một hàm đọc generic field type, không phải một chương trình độc lập:

~~~java
try {
    return field.getGenericType();
} catch (TypeNotPresentException ex) {
    // typeName() cho biết kiểu thiếu; cause có thể null.
    throw new IllegalStateException(
            "Missing generic type " + ex.typeName()
            + " on " + field, ex);
} catch (java.lang.reflect.MalformedParameterizedTypeException ex) {
    throw new IllegalStateException(
            "Invalid parameterized type on " + field, ex);
} catch (java.lang.reflect.GenericSignatureFormatError err) {
    // Metadata class file bị hỏng: báo lỗi rõ ràng rồi dừng.
    throw new IllegalStateException(
            "Invalid generic signature on " + field, err);
}
~~~

Trong framework có vòng scan lớn, có thể ghi chẩn đoán theo từng class để biết module nào bị ảnh hưởng. Tuy nhiên, chỉ **fallback từ `getGenericType()` sang `getType()`** khi nghiệp vụ cho phép mất toàn bộ type arguments; ví dụ, serializer cần kiểu `List<User>` mà chỉ còn `List.class` thì tiếp tục đoán `User` sẽ gây lỗi dữ liệu.

**Lưu ý về catch `Error`:** việc bắt riêng `GenericSignatureFormatError` có thể hữu ích ở ranh giới chẩn đoán để bổ sung ngữ cảnh, nhưng đừng bắt mọi `Error` rồi cho hệ thống tiếp tục. Lỗi Signature cho thấy artifact hoặc metadata có vấn đề cần sửa tại nguồn.

## 6. Những hiểu nhầm cần tránh

| Nhận định dễ sai | Điều đúng |
| --- | --- |
| “`Class.forName` thành công thì metadata generic chắc chắn đọc được.” | Generic `Signature` có thể được phân tích/phân giải về sau, lúc gọi API tương ứng. |
| “Cả ba đều là exception từ `java.lang.reflect`.” | `TypeNotPresentException` thuộc `java.lang`; `GenericSignatureFormatError` là `Error`. |
| “`GenericSignatureFormatError` giống `MalformedParameterizedTypeException`.” | Cái trước sai grammar; cái sau có thể đúng grammar nhưng sai ngữ nghĩa của kiểu tham số hóa. |
| “`TypeNotPresentException` luôn có cause.” | `getCause()` có thể `null`; nên đọc `typeName()` trước. |
| “Xóa một class trong `List<Absent>` luôn làm `Holder` không load được.” | Khi `Absent` chỉ hiện diện trong generic signature, raw field descriptor có thể vẫn hợp lệ; lỗi chỉ xuất hiện khi đọc metadata generic. |
| “`ClassNotFoundException`, `NoClassDefFoundError` và `TypeNotPresentException` giống nhau.” | Chúng có hierarchy, thời điểm và ngữ cảnh phát sinh khác nhau. |
| “Có thể sửa generic metadata bằng `setAccessible(true)`.” | `setAccessible` tác động kiểm tra truy cập member, không sửa classpath hay bytecode Signature. |
| “Bất kỳ mã Java sai generic nào cũng tạo ra lỗi runtime này.” | `javac` chặn phần lớn source sai generic từ lúc compile; lỗi runtime này thường đến từ metadata nhị phân hoặc môi trường tải. |

## 7. Tự kiểm tra kiến thức

1. Với field `List<Missing>`, vì sao `getType()` có thể trả về `List.class` còn `getGenericType()` lại thất bại?
2. Phân biệt một `Signature` thiếu dấu kết thúc với chữ ký `List<String, Integer>` bị ghi vào class file của một raw type chỉ có một type parameter.
3. Khi bắt `TypeNotPresentException`, hai thông tin nào giúp xác định dependency bị thiếu?
4. Vì sao `catch (RuntimeException)` không chặn `GenericSignatureFormatError`?
5. Trường hợp nào dùng fallback sang kiểu raw an toàn, và khi nào phải dừng để tránh deserialize dữ liệu sai?

### Tài liệu Java API chính thức

- Java SE 21, `GenericSignatureFormatError`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/GenericSignatureFormatError.html
- Java SE 21, `TypeNotPresentException`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/TypeNotPresentException.html
- Java SE 21, `MalformedParameterizedTypeException`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/MalformedParameterizedTypeException.html
- Java SE 21, `Field`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html
- Java SE 21, `Class`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html
- JVM Specification §4.7.9.1 (Signatures): https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.9.1

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exceptions-diagnostics-009">MalformedParametersException và metadata tham số không hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# MalformedParametersException và metadata tham số không hợp lệ trong Java Reflection

## 1. Tình huống: phương thức tồn tại nhưng đọc danh sách tham số lại lỗi

Khi dùng Reflection, ta thường truy cập `Method` hoặc `Constructor` để đọc thông tin về tham số. Chẳng hạn framework có thể hỏi một phương thức nhận bao nhiêu tham số, kiểu gì và tên từng tham số là gì. Có những trường hợp class tải được, phương thức vẫn tồn tại, nhưng lệnh:

```java
Parameter[] parameters = method.getParameters();
```

ném `java.lang.reflect.MalformedParametersException`. Đây là dấu hiệu **metadata mô tả tham số trong file `.class` không hợp lệ** theo quy tắc của JVM/Reflection, không có nghĩa bản thân Java source đang gọi phương thức với đối số sai.

`MalformedParametersException` là một **unchecked exception**, kế thừa từ `RuntimeException`; có từ Java 8. API điển hình gây ra lỗi là `java.lang.reflect.Executable.getParameters()` (được `Method` và `Constructor` kế thừa). Lỗi có thể xuất hiện khi framework tự động dò constructor, binding tên tham số, làm dependency injection hoặc xử lý annotation và gọi API đọc metadata tương ứng.

Điều cần nhớ ngay:

- **Thiếu metadata tên tham số** thường là trường hợp hợp lệ: Java vẫn có thể tạo đối tượng `Parameter` với tên thay thế `arg0`, `arg1` và `isNamePresent() == false`.
- **Metadata hiện diện nhưng sai cấu trúc/nội dung** mới là nhóm lỗi mà `MalformedParametersException` mô tả.
- Cách sửa tốt nhất thường nằm ở **quá trình biên dịch/sinh/sửa bytecode**, không phải thêm `try/catch` quanh code nghiệp vụ rồi bỏ qua mọi lỗi.

## 2. Hiểu các lớp thông tin tham số trong class file

Một method/constructor có **method descriptor** ở cấp bytecode, xác định kiểu tham số và kiểu trả về. Ví dụ khai báo:

```java
public void submit(String orderId, int quantity) { }
```

có descriptor:

```text
(Ljava/lang/String;I)V
```

Descriptor xác định phương thức nhận `String` và `int`, trả về `void`. **Descriptor không chứa tên `orderId` hoặc `quantity`.**

Từ Java 8, class file có thể chứa attribute tùy chọn **`MethodParameters`**. Attribute này chứa thông tin về mỗi tham số, gồm chỉ mục tên trong *constant pool* (hoặc trường hợp không có tên) và access flags thích hợp. Khi biên dịch bằng `javac -parameters`, compiler thông thường sẽ giữ lại tên tham số trong attribute này để Reflection truy cập.

Cần phân biệt với **`LocalVariableTable`**: đây là thông tin debug có thể được sinh bởi các lựa chọn như `-g`. LocalVariableTable không tương đương với MethodParameters, và **`-g` không thay thế `-parameters`** cho `Parameter.isNamePresent()`. Đừng kết luận một file class đã đủ metadata Reflection chỉ vì debugger nhìn thấy tên biến cục bộ.

| Nguồn thông tin | Chứa gì? | API/đối tượng liên quan | Điểm lưu ý |
| --- | --- | --- | --- |
| Method descriptor | Số lượng và kiểu tham số JVM | `getParameterCount()`, `getParameterTypes()` | Không cần tên tham số source |
| `MethodParameters` (tùy chọn) | Tên và flags của tham số | `getParameters()`, `Parameter.getName()`, `isNamePresent()` | Có thể vắng mặt hợp lệ hoặc có dữ liệu hỏng |
| `LocalVariableTable` (debug) | Biến cục bộ, slot, phạm vi bytecode | Debugger/công cụ phân tích bytecode | Không phải metadata tên tham số Reflection chuẩn |
| `Signature` (khi có) | Thông tin kiểu generic | `getGenericParameterTypes()` và API generic khác | Hỏng Signature có thể sinh lỗi khác, không đồng nhất với `MalformedParametersException` |

**Mô hình:** descriptor nói “tham số thuộc kiểu nào”; MethodParameters bổ sung “tham số tên gì và mang flags nào”. Một method có thể hợp lệ dù thiếu thông tin tên.

## 3. Chính xác khi nào MalformedParametersException có thể xảy ra?

Theo mô tả của API Java, `Executable.getParameters()` có thể ném `MalformedParametersException` khi class file chứa `MethodParameters` được định dạng không đúng.

Các dạng sai phổ biến mà JDK xác định gồm:

1. **Sai số lượng phần tử metadata:** `parameters_count` trong `MethodParameters` không khớp số tham số thực tế của method descriptor.
2. **Tham chiếu constant pool không hợp lệ:** chỉ mục đi ra ngoài phạm vi hoặc trỏ đến mục không thuộc loại `CONSTANT_Utf8` khi cần tên.
3. **Tên tham số không hợp lệ đối với định dạng metadata:** ví dụ chuỗi rỗng `""` hoặc chứa ký tự JVM không cho phép như `.`, `;`, `[`, `/`.
4. **Access flags không được phép:** flags của tham số có bit ngoài nhóm hợp lệ `FINAL`, `SYNTHETIC`, `MANDATED`.

Đừng hiểu điều này thành quy tắc “mọi tên tham số phải duy nhất và là Java identifier chuẩn”. Javadoc của `Executable.getParameters()` nói tên tham số **không nhất thiết duy nhất** hoặc là định danh hợp lệ trong Java source. Điều được kiểm tra ở đây là các ràng buộc của attribute/JDK, không phải việc đặt tên code đẹp.

Một attribute bị hỏng có thể đến từ trình sinh bytecode, trình sửa class, công cụ obfuscation, instrumentation, quá trình transform sau biên dịch hoặc file class bị thay đổi. `javac` bình thường phát sinh metadata đúng với source hợp lệ.

### Khác nhau giữa “không có MethodParameters” và “MethodParameters sai”

```text
MethodParameters không tồn tại
  -> getParameters() thường trả Parameter[] hợp lệ
  -> getName() dùng arg0, arg1, ...
  -> isNamePresent() == false
  -> không tự gây MalformedParametersException

MethodParameters tồn tại nhưng vi phạm quy tắc
  -> getParameters() có thể ném MalformedParametersException
  -> không nên dựa vào việc getName()/isNamePresent() sẽ chạy sau đó
```

Một chương trình thiếu `-parameters` nhưng framework **yêu cầu tên thật** vẫn có thể gặp lỗi cấu hình hoặc binding *riêng của framework*. Lỗi đó cần phân biệt với `MalformedParametersException` do attribute không hợp lệ.

## 4. Ví dụ chạy được: quan sát metadata hợp lệ có và không có -parameters

Chương trình sau minh họa một **đối chứng hợp lệ** để kiểm tra sự khác biệt giữa việc có và thiếu tên tham số. Nó không cố tình tạo class file sai; một ví dụ Java source biên dịch bình thường **không thể tái tạo tự nhiên** lỗi malformed metadata. Mục tiếp theo sẽ trình bày cách tìm class đã bị biến đổi.

Lưu toàn bộ thành file **`ParameterMetadataDemo.java`**:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.MalformedParametersException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class ParameterMetadataDemo {
    public static class OrderService {
        public OrderService(String region, int retries) {
        }

        public void submit(String orderId, int quantity) {
        }
    }

    private static void inspect(String label, Executable executable) {
        System.out.println(label + ": " + executable.getParameterCount());

        try {
            for (Parameter parameter : executable.getParameters()) {
                System.out.println("  "
                        + parameter.getType().getSimpleName()
                        + " " + parameter.getName()
                        + " | namePresent=" + parameter.isNamePresent());
            }
        } catch (MalformedParametersException ex) {
            System.out.println("  malformed MethodParameters: " + ex.getMessage());
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Method method = OrderService.class.getDeclaredMethod(
                "submit", String.class, int.class);
        Constructor<OrderService> constructor =
                OrderService.class.getDeclaredConstructor(
                        String.class, int.class);

        inspect("method", method);
        inspect("constructor", constructor);
    }
}
```

### Lần chạy A — biên dịch không có -parameters

Dùng Java 17 trở lên:

```bash
javac -encoding UTF-8 ParameterMetadataDemo.java
java ParameterMetadataDemo
```

Kết quả dự kiến từ class vừa biên dịch trong ví dụ:

```text
method: 2
  String arg0 | namePresent=false
  int arg1 | namePresent=false
constructor: 2
  String arg0 | namePresent=false
  int arg1 | namePresent=false
```

Dù không thấy tên `orderId`/`quantity`/`region`/`retries`, `getParameters()` vẫn trả danh sách bình thường. **Đây không phải lỗi metadata malformed.**

### Lần chạy B — biên dịch có -parameters

Biên dịch lại cùng file với cờ giữ tên tham số (javac sẽ ghi đè các class sinh từ file này):

```bash
javac -encoding UTF-8 -parameters ParameterMetadataDemo.java
java ParameterMetadataDemo
```

Kết quả:

```text
method: 2
  String orderId | namePresent=true
  int quantity | namePresent=true
constructor: 2
  String region | namePresent=true
  int retries | namePresent=true
```

Lần này `Parameter.isNamePresent()` là `true` vì `MethodParameters` có dữ liệu tên hợp lệ. Không có ngoại lệ nào được ném trong cả hai lần chạy.

**Lưu ý khi tái hiện:** hãy biên dịch lại đúng file bằng lệnh tương ứng và chạy cùng phiên bản class. Nếu chương trình sử dụng dependency/JAR cũ trên classpath, kết quả có thể phản ánh bytecode khác với file vừa biên dịch.

## 5. Kiểm tra class file bằng javap

`javap` là công cụ đi kèm JDK để xem chữ ký, bytecode và attribute class. Chạy với `-v` (verbose) để thấy thông tin cần chẩn đoán:

```bash
javap -v -p 'ParameterMetadataDemo$OrderService'
```

Lệnh trên áp dụng với Bash hoặc PowerShell, nơi dấu nháy đơn giữ nguyên ký tự `$` trong tên nested class. Trên Windows CMD, dùng dấu nháy kép hoặc escape phù hợp với shell.

Tìm các vùng gần tên phương thức `submit` và constructor, chú ý:

- **Descriptor**, ví dụ `(Ljava/lang/String;I)V`.
- Attribute **MethodParameters** nếu có. Khi đã biên dịch với `-parameters`, tên như `orderId`, `quantity` xuất hiện cạnh flags.
- Trường hợp không có MethodParameters hoàn toàn là hợp lệ và thường tương ứng với lần chạy A.
- Với class thật đang lỗi, đối chiếu **số entry MethodParameters** với **số tham số ở descriptor**, rồi kiểm tra tham chiếu constant pool, tên và flags.

Không chỉ kiểm tra source. Khi có nhiều JAR chứa cùng tên class hoặc class đã qua agent transformer, phải xác định **file class thực sự được classloader nạp**. Một bản `.class` hợp lệ trong thư mục build không chứng minh bản trong JAR đang chạy cũng hợp lệ.

Ví dụ in nguồn tài nguyên của class đang được nạp:

```java
System.out.println(OrderService.class.getProtectionDomain()
        .getCodeSource().getLocation());
```

Đoạn này chỉ là ví dụ để đặt **trong ngữ cảnh class có quyền tham chiếu `OrderService`**. `getCodeSource()` có thể `null` với một số cách nạp/lớp hệ thống; vì vậy code production cần kiểm tra null trước khi dereference. Với class load đặc biệt hoặc mã được transform trong bộ nhớ, vị trí code source cũng có thể không đủ để chứng minh bytecode cuối cùng.

## 6. Quy trình chẩn đoán MalformedParametersException

Khi bắt gặp stack trace, làm theo thứ tự giúp phân biệt lỗi nguồn và lỗi biểu hiện:

**Bước 1 — tìm thao tác Reflection kích hoạt lỗi.** Xem stack trace tại `Executable.getParameters()` và lớp/method nào của ứng dụng hoặc framework gọi nó. Ghi nhận chính xác tên class, tên method/constructor, classloader và JDK runtime. Đừng dừng lại ở dòng lỗi bề ngoài như “không tạo được bean”, “không bind được tham số”, hoặc “không gọi được constructor”.

**Bước 2 — tái hiện tối thiểu trên đúng method.** Nếu đã xác định class và chữ ký, thử:

```java
Method m = SomeType.class.getDeclaredMethod(
        "process", String.class, int.class);
System.out.println(m.getParameterCount());
try {
    for (Parameter p : m.getParameters()) {
        System.out.println(p.getName());
    }
} catch (MalformedParametersException ex) {
    ex.printStackTrace();
}
```

Đây là **mẫu minh họa**: phải thay `SomeType`, `process` và các kiểu tham số bằng class/method có thật. Đặt trong phương thức có khai báo hoặc bắt `NoSuchMethodException`; có đầy đủ import thích hợp. Khi tái hiện, dùng đúng **binary class đã được framework nạp**, không mặc định phiên bản trong IDE là phiên bản lỗi.

**Bước 3 — kiểm tra từng tầng cấu trúc metadata.**

| Câu hỏi chẩn đoán | Công cụ/dữ liệu | Hướng xử lý |
| --- | --- | --- |
| Method descriptor có bao nhiêu tham số? | `javap -v -p`, `getParameterCount()` | Đối chiếu descriptor với MethodParameters |
| MethodParameters có tồn tại không? | `javap -v -p` | Vắng mặt là hợp lệ; xem framework có cần tên thật không |
| Số entry, tên, constant-pool index, flags có hợp lệ? | `javap`/bytecode inspector có kiểm tra class file | Khoanh vùng dữ liệu malformed |
| Class nào/JAR nào thật sự được nạp? | Classloader, code source, dependency tree | Loại bỏ duplicate/stale class hoặc sai classpath |
| File class có qua transformer/obfuscator/shader/agent không? | Cấu hình build, pipeline transform | So sánh bản trước/sau bước biến đổi |
| Một lệnh biên dịch sạch có tái hiện không? | Clean build với cùng JDK và source | Xác định toolchain/transform gây hỏng |

Lưu ý: không phải mọi bytecode tool đều có mức kiểm tra như JVM; một công cụ có thể đọc và in attribute nhưng không tự gắn nhãn “hỏng”. Đối chiếu với quy tắc class file và hành vi Reflection.

**Bước 4 — so bản gốc và bản sau xử lý.** Nếu source Java biên dịch sạch không có lỗi nhưng JAR cuối bị lỗi, so sánh attribute MethodParameters trước/sau các bước weaving, shading, minimization, obfuscation, instrumentation. Các bộ biến đổi phải cập nhật metadata nhất quán khi đổi descriptor/tên và không ghi flags/tên sai.

**Bước 5 — sửa artifact và kiểm tra lại.** Tái sinh class hợp lệ, đồng bộ phiên bản thư viện/công cụ và kiểm tra cả môi trường phát triển lẫn runtime thật. Thêm test gọi `getParameters()` trên những API mà framework phụ thuộc để phát hiện hồi quy trong pipeline.

## 7. Xử lý ngoại lệ đúng tầng và lựa chọn fallback

Vì lỗi nằm trong **metadata của binary**, hướng chính là sửa build/artifact. Ở một số công cụ phân tích có thể cần fallback để tránh sập toàn bộ quá trình quét, nhưng phải ghi nhận rằng tên/thông tin tham số có thể không đáng tin.

Ví dụ chỉ cần **kiểu tham số**, không cần tên:

```java
import java.lang.reflect.Executable;
import java.lang.reflect.MalformedParametersException;

public class MetadataFallback {
    public static void printSignature(Executable executable) {
        System.out.println("Parameter count: " + executable.getParameterCount());
        for (Class<?> type : executable.getParameterTypes()) {
            System.out.println(type.getTypeName());
        }

        try {
            System.out.println("Parameter metadata entries: "
                    + executable.getParameters().length);
        } catch (MalformedParametersException ex) {
            System.err.println("Invalid MethodParameters: " + ex.getMessage());
        }
    }
}
```

Đây là đoạn Java biên dịch được như một class thư viện; chưa có `main`, nên không phải chương trình tự in ra output độc lập. Trường hợp descriptor hợp lệ, `getParameterTypes()` có thể cung cấp kiểu ngay cả khi `getParameters()` lỗi. Nhưng **fallback không khôi phục tên thật**, flags `mandated`/`synthetic`, hoặc bảo đảm logic binding theo tên sẽ đúng.

Khi mapping request, injection hoặc serialization **phụ thuộc tên tham số**, việc âm thầm gán `arg0`/`arg1` hay gán theo vị trí có thể đổi ý nghĩa. Hãy fail rõ ràng, log class/method/artifact và sửa metadata nguồn. Nếu API người dùng thực sự được thiết kế để ánh xạ theo index, chỉ dùng fallback khi đã chứng minh thứ tự và kiểu tham số đủ cho bài toán.

Không dùng `try/catch (Exception ignored)` để che lỗi rồi tiếp tục như thể metadata hợp lệ; cũng không đặt `-parameters` thành giải pháp duy nhất cho mọi `MalformedParametersException`. Cờ này giúp tạo metadata đúng từ source Java bình thường nhưng không tự sửa file class đã bị công cụ khác ghi sai.

## 8. Phân biệt các ngoại lệ Reflection dễ nhầm

| Ngoại lệ | Ngữ cảnh tiêu biểu | Khác biệt |
| --- | --- | --- |
| `MalformedParametersException` | `Executable.getParameters()` đọc MethodParameters sai | Lỗi **metadata mô tả tham số** ở class file |
| `NoSuchMethodException` | `getDeclaredMethod(...)` với tên/kiểu tham số không khớp | Tra cứu method sai; không chứng minh metadata tham số bị hỏng |
| `IllegalArgumentException` | `Method.invoke(...)` truyền đối số không hợp lệ | Lỗi sử dụng API/đối số runtime; có thể phát sinh từ nguyên nhân khác |
| `IllegalAccessException` | Truy cập/invoke method không đủ quyền | Lỗi access control, độc lập với tên tham số |
| `GenericSignatureFormatError` | Parse generic Signature không đúng | Lỗi cú pháp metadata **generic**, không đồng nhất MethodParameters |
| `MalformedParameterizedTypeException` | Kiểu generic không thể được tạo hợp lệ | Lỗi ở mô hình kiểu generic; tên trông giống nhưng bản chất khác |
| `ClassFormatError` | JVM thấy class file sai cấu trúc ở mức load/verify thích hợp | Có thể khiến class không nạp được; khác trường hợp nạp được nhưng `getParameters()` báo malformed attribute |

Lỗi `MalformedParametersException` không nhất thiết xảy ra lúc class được nạp. Tùy loại bất hợp lệ, có thể chỉ lộ ra **khi Reflection cố đọc metadata tham số**. Đừng suy ra “class load thành công thì toàn bộ metadata luôn đúng”.

## 9. Những hiểu nhầm thường gặp

- **“Không có cờ -parameters sẽ gây MalformedParametersException.”** Sai. Với method thông thường, Reflection có tên mặc định `arg0`, `arg1`, `isNamePresent() == false` và không tự ném lỗi chỉ vì attribute vắng mặt.
- **“Bật -g là đủ.”** Sai. Debug `LocalVariableTable` khác attribute `MethodParameters`.
- **“Thấy tên arg0 là metadata hỏng.”** Sai. `arg0` có thể là tên tổng hợp hợp lệ khi không có tên gốc; `Parameter.isNamePresent()` cho biết tên đó có thực sự từ thông tin tên hay không.
- **“MethodParameters có bao nhiêu entry cũng được.”** Sai. Nếu attribute hiện diện thì số lượng phải phù hợp với method descriptor, kể cả tham số implicit/synthetic thực sự ở cấp bytecode.
- **“Chỉ đổi tên source là sửa được class cũ.”** Sai. Phải biên dịch/sinh lại đúng binary được dùng, và kiểm tra mọi transform tiếp theo.
- **“Lỗi trong framework thì chắc chắn framework sai.”** Chưa đủ căn cứ. Framework có thể chỉ là nơi đầu tiên đọc bytecode bị hỏng từ một dependency.
- **“getParameterCount() không lỗi nên getParameters() chắc chắn không lỗi.”** Sai. Hai thao tác không cần đọc cùng một lớp metadata.
- **“Bắt RuntimeException rồi bỏ qua là an toàn.”** Sai. Có thể khiến dữ liệu tên tham số dùng để mapping bị sai lặng lẽ.

## 10. Bài tập thực hành và checklist kiểm chứng

1. Chạy `ParameterMetadataDemo` lần lượt **không có** và **có** `-parameters`. Giải thích vì sao `getParameterCount()` không thay đổi và `isNamePresent()` thay đổi.
2. Dùng `javap -v -p` trên class của `OrderService` trong cả hai bản biên dịch. Chỉ ra descriptor và sự có mặt/vắng mặt của MethodParameters.
3. Giả sử một bytecode transformer thay phương thức từ hai tham số thành một tham số nhưng giữ MethodParameters có hai entry. Dự đoán khi nào `MalformedParametersException` có thể xuất hiện và cách xác nhận.
4. Nếu server báo lỗi lúc binding tên tham số nhưng `getParameters()` chạy được và `isNamePresent()` là `false`, cần xem lại gì trong compiler/framework?
5. Nếu cùng source chạy đúng ở IDE nhưng lỗi trong fat JAR, xác định lớp bước nào trong build/deployment cần so sánh.

**Checklist khi debug production:** ghi đầy đủ `exception + stack trace → class/method descriptor → binary/classloader thực tế → MethodParameters trong artifact → toolchain và từng bước biến đổi → clean rebuild → test đọc lại getParameters()`.

**Kết quả cần đạt:** phân biệt được dữ liệu tham số **bị thiếu hợp lệ** với dữ liệu **hiện diện nhưng malformed**, hiểu vì sao `Executable.getParameters()` có thể ném unchecked exception, và biết truy ngược artifact gây lỗi để sửa nguyên nhân.

### Tài liệu tham khảo

- [Javadoc Java 21: Executable.getParameters()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Executable.html#getParameters())
- [Javadoc Java 21: MalformedParametersException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/MalformedParametersException.html)
- [Tài liệu javac: tùy chọn -parameters](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exceptions-diagnostics-010">AnnotationTypeMismatchException và IncompleteAnnotationException</a>

<details>
<summary>Xem chi tiết</summary>

# `AnnotationTypeMismatchException` và `IncompleteAnnotationException` trong Java Reflection

## 1. Vấn đề: annotation hợp lệ lúc biên dịch nhưng không còn khớp lúc chạy

Hai ngoại lệ trong `java.lang.annotation` thường xuất hiện khi **annotation được ghi vào một class file theo định nghĩa cũ**, còn tại runtime JVM đang dùng **định nghĩa annotation mới** không tương thích. Class file mang annotation lưu tên và giá trị của các phần tử annotation; nó không tự được cập nhật khi thư viện định nghĩa annotation thay đổi.

Hãy phân biệt:

| Ngoại lệ | Điều kiện điển hình | Điều gì không còn khớp? |
| --- | --- | --- |
| `AnnotationTypeMismatchException` | Phần tử `value()` từng trả về `String`, phiên bản mới trả về `int` | **Kiểu giá trị cũ** khác kiểu phần tử trong định nghĩa hiện tại |
| `IncompleteAnnotationException` | Định nghĩa mới thêm phần tử bắt buộc `owner()` nhưng class file cũ chưa lưu giá trị | **Thiếu giá trị** cho phần tử không có `default` |

Cả hai đều là **`RuntimeException` (unchecked)**. Chúng có thể xảy ra trong chương trình có source code hiện tại trông hoàn toàn đúng, vì class file ứng dụng và thư viện trên runtime classpath/module path không được biên dịch cùng một hợp đồng annotation.

Trong Reflection, việc có `Class<?>` hoặc gọi `getAnnotation(...)` thành công **chưa bảo đảm đọc được mọi phần tử**: lỗi thường bộc lộ khi gọi trực tiếp `annotation.value()` / `annotation.owner()`. Thời điểm chi tiết có thể phụ thuộc cách API hoặc framework duyệt và xử lý annotation; không nên coi bước lấy annotation là bằng chứng dữ liệu đã hợp lệ.

Sau bài học, bạn có thể tái tạo cả hai lỗi, hiểu API chẩn đoán, phân biệt với các lỗi annotation khác, và sửa xung đột phiên bản một cách có kiểm chứng.

## 2. Nền tảng: annotation lưu gì trong class file?

Xét annotation dùng ở runtime:

~~~java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Info {
    String id();
}
~~~

~~~java
@Info(id = "A1")
public class Record {}
~~~

`@Retention(RetentionPolicy.RUNTIME)` giúp các API Reflection như `Class.getAnnotation(Info.class)` nhìn thấy annotation. Khi `Record.java` được biên dịch, giá trị `id = "A1"` trở thành một phần metadata trong `Record.class`.

Ở phiên bản hiện tại của annotation, mỗi phần tử được khai báo bằng **method không tham số** như `String id()`. `Info.class` được nạp theo classloader/module ở runtime, còn `Record.class` có metadata đã ghi từ lần biên dịch trước. Nếu triển khai chỉ cập nhật `Info.class` mà không biên dịch lại `Record.class`, hai nguồn có thể không thống nhất.

Các điểm quan trọng:

- Annotation có thể chứa các kiểu phần tử mà Java cho phép, ví dụ primitive, `String`, `Class`, enum, annotation và mảng một chiều của các kiểu hợp lệ. Không thể tùy tiện đổi `String` sang `int` rồi mong dữ liệu cũ tự chuyển kiểu.
- Một phần tử có thể khai báo `default`; khi class file không ghi giá trị tường minh cho phần tử đó, API annotation có thể dùng giá trị mặc định từ định nghĩa **hiện tại**.
- `RetentionPolicy.CLASS` được giữ trong class file nhưng không được các API Reflection runtime thông thường trả về như `RUNTIME`; `SOURCE` không lưu vào class file.
- Việc chỉ có một tệp `.java` đúng không xác nhận runtime đúng: phải xem phiên bản `.class` thật sự được nạp.

## 3. `AnnotationTypeMismatchException` — kiểu phần tử đã thay đổi

Theo Java SE 17, ngoại lệ này biểu thị rằng chương trình cố đọc một **phần tử annotation mà kiểu đã thay đổi** sau khi annotation được biên dịch hoặc tuần tự hóa.

Ví dụ lịch sử:

~~~java
// Phiên bản v1, khi lớp sử dụng annotation được biên dịch
public @interface State {
    String value();
}
~~~

~~~java
@State("READY")
public class Stored {}
~~~

Phiên bản mới lại định nghĩa:

~~~java
// Phiên bản v2 đang có mặt khi chạy
public @interface State {
    int value();
}
~~~

Class file cũ của `Stored` vẫn mang giá trị dạng chuỗi `"READY"`, trong khi `State.value()` hiện yêu cầu `int`. Khi gọi `State.value()` trên annotation lấy từ `Stored.class`, runtime không thể trả về kiểu đã khai báo và có thể ném `AnnotationTypeMismatchException`.

### Dữ liệu chẩn đoán của ngoại lệ

~~~java
catch (AnnotationTypeMismatchException ex) {
    System.out.println(ex.element().getName());
    System.out.println(ex.element().getReturnType().getName());
    System.out.println(ex.foundType());
}
~~~

- `element()` trả về `java.lang.reflect.Method` đại diện **phần tử annotation trong định nghĩa hiện tại**. Dùng `getDeclaringClass()`, `getName()`, `getReturnType()` để kiểm tra method mong đợi.
- `foundType()` trả về mô tả kiểu của dữ liệu sai được tìm thấy; chuỗi **có định dạng không được đặc tả**, có thể kèm giá trị. Không parse chuỗi này như một API ổn định, và không dùng nó làm contract để kiểm tra tự động.
- `getMessage()` có ích cho log nhưng nội dung thông báo không phải định dạng để hệ thống khác phụ thuộc.
- Đây là lỗi **không tương thích metadata**, không phải `ClassCastException` thông thường do ứng dụng ép một `Object` sai kiểu.

## 4. `IncompleteAnnotationException` — thiếu phần tử bắt buộc mới

Ngoại lệ này xảy ra khi code đọc một **phần tử mới được thêm vào annotation interface sau khi nơi sử dụng đã được biên dịch**, nhưng class file cũ không chứa giá trị và phần tử mới **không có mặc định**.

Ví dụ:

~~~java
// v1
public @interface Metadata {
    String id();
}
~~~

~~~java
@Metadata(id = "X1")
public class Stored {}
~~~

Sau đó thư viện thay bằng:

~~~java
// v2
public @interface Metadata {
    String id();
    String owner(); // mới thêm, không có default
}
~~~

Khi gọi `metadata.id()`, dữ liệu `"X1"` vẫn hợp lệ. Khi gọi `metadata.owner()`, class file cũ không có `owner` và định nghĩa mới cũng không cung cấp `default` nên runtime ném `IncompleteAnnotationException`.

### Đọc thuộc tính chẩn đoán

~~~java
catch (IncompleteAnnotationException ex) {
    System.out.println(ex.annotationType().getName());
    System.out.println(ex.elementName());
}
~~~

- `annotationType()` trả về `Class<? extends Annotation>` của annotation có phần tử bị thiếu.
- `elementName()` trả về tên phần tử bị thiếu, ví dụ `owner`.
- Constructor `IncompleteAnnotationException(Class<? extends Annotation>, String)` có thể dùng trong code đặc biệt tự xây cơ chế annotation, nhưng trong thực hành thường bạn **bắt và chẩn đoán** lỗi do hệ thống đọc annotation phát hiện.
- Thêm phần tử với giá trị mặc định, ví dụ `String owner() default "UNKNOWN";`, thường giúp **class file cũ vẫn đọc được** phần tử đó mà không cần khai báo giá trị mới. Đây là lựa chọn thiết kế API cần cân nhắc, không giải quyết mọi kiểu thay đổi không tương thích khác.

## 5. Thực hành đầy đủ: tạo hai lỗi bằng hai phiên bản của cùng annotation

Thí nghiệm dưới đây không cần sửa bytecode thủ công. Ta chủ động **biên dịch bản cũ vào một thư mục**, biên dịch **định nghĩa mới cùng chương trình đọc** vào thư mục khác, rồi ưu tiên class mới trên classpath. Điều này mô phỏng lỗi nâng phiên bản dependency nhưng quên biên dịch lại class từng dùng annotation.

Yêu cầu JDK 17+; lệnh dùng `javac --release 17` để cố định cú pháp/API Java 17.

### 5.1. Cấu trúc dự án

~~~text
annotation-compat-demo/
├── old/demo/
│   ├── State.java
│   ├── Metadata.java
│   └── Stored.java
├── current/demo/
│   ├── State.java
│   ├── Metadata.java
│   └── Reader.java
└── out/
    ├── old/
    └── new/
~~~

Các file trong `out` là **đầu ra** của `javac`, không phải file source. Tạo những thư mục này trước khi chạy lệnh biên dịch bên dưới.

### 5.2. Nguồn phiên bản cũ

File `old/demo/State.java`:

~~~java
package demo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface State {
    String value();
}
~~~

File `old/demo/Metadata.java`:

~~~java
package demo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Metadata {
    String id();
}
~~~

File `old/demo/Stored.java`:

~~~java
package demo;

@State("READY")
@Metadata(id = "X1")
public class Stored {}
~~~

Ở lần biên dịch này, `Stored.class` lưu `State.value` dạng chuỗi và chỉ lưu `Metadata.id`; nó chưa từng biết phương thức `Metadata.owner()`.

### 5.3. Nguồn phiên bản mới

File `current/demo/State.java`:

~~~java
package demo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface State {
    int value(); // v1 là String
}
~~~

File `current/demo/Metadata.java`:

~~~java
package demo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Metadata {
    String id();
    String owner(); // v1 chưa có; cố ý không khai báo default
}
~~~

File `current/demo/Reader.java`:

~~~java
package demo;

import java.lang.annotation.AnnotationTypeMismatchException;
import java.lang.annotation.IncompleteAnnotationException;

public class Reader {
    public static void main(String[] args) throws ClassNotFoundException {
        Class<?> stored = Class.forName("demo.Stored");

        State state = stored.getAnnotation(State.class);
        Metadata metadata = stored.getAnnotation(Metadata.class);

        System.out.println("State present: " + (state != null));

        try {
            System.out.println("state=" + state.value());
        } catch (AnnotationTypeMismatchException ex) {
            System.out.println("Mismatch element: " + ex.element().getName());
        }

        System.out.println("id=" + metadata.id());

        try {
            System.out.println("owner=" + metadata.owner());
        } catch (IncompleteAnnotationException ex) {
            System.out.println("Missing element: " + ex.elementName());
            System.out.println("Annotation type: "
                    + ex.annotationType().getSimpleName());
        }
    }
}
~~~

Chương trình đọc cố ý dùng `Class.forName("demo.Stored")`: `Reader.java` **không cần compile-time dependency trực tiếp** lên `Stored.class` của bản cũ. Cả `State` và `Metadata` ở chương trình đọc đều là định nghĩa mới.

### 5.4. Biên dịch và chạy

Chạy lệnh trong thư mục `annotation-compat-demo`.

Trên Linux/macOS:

~~~bash
mkdir -p out/old out/new

javac --release 17 -d out/old \
  old/demo/State.java old/demo/Metadata.java old/demo/Stored.java

javac --release 17 -d out/new \
  current/demo/State.java current/demo/Metadata.java current/demo/Reader.java

java -cp "out/new:out/old" demo.Reader
~~~

Trên Windows PowerShell, dùng đường dẫn tương đương và dấu `;` làm dấu phân cách classpath:

~~~powershell
New-Item -ItemType Directory -Force out\old, out\new | Out-Null

javac --release 17 -d out\old old\demo\State.java old\demo\Metadata.java old\demo\Stored.java
javac --release 17 -d out\new current\demo\State.java current\demo\Metadata.java current\demo\Reader.java

java -cp "out\new;out\old" demo.Reader
~~~

**Thứ tự trong classpath rất quan trọng:** `out/new` đứng trước nên runtime dùng `demo.State` và `demo.Metadata` phiên bản mới; `demo.Stored` chỉ có trong `out/old`. Không cho `Stored.java` được biên dịch lại với hai annotation mới trong lúc chuẩn bị thí nghiệm, vì compiler lúc đó sẽ phát hiện khai báo `@State("READY")` sai kiểu và `@Metadata` thiếu `owner`.

Kết quả thực tế được đối chiếu bằng `javac --release 17`, chạy trên JDK 21:

~~~text
State present: true
Mismatch element: value
id=X1
Missing element: owner
Annotation type: Metadata
~~~

Giải thích từng dòng:

1. `getAnnotation(State.class)` vẫn tìm thấy annotation trên `Stored.class`.
2. `state.value()` yêu cầu `int` nhưng class file lưu chuỗi; exception được bắt và `element().getName()` cho `value`.
3. `metadata.id()` vẫn trả `X1`, vì phần tử này không thay đổi.
4. `metadata.owner()` không có giá trị lưu trong class file và cũng không có default; `elementName()` cho `owner`, `annotationType()` cho `Metadata`.

Kết quả trên là **một tình huống tái hiện lỗi xác định**, không phải khẳng định rằng mọi thao tác lấy annotation trên mọi class file không tương thích đều sẽ hoàn tất trước khi ném lỗi. Annotation hỏng cấu trúc hoặc dependency thiếu có thể thất bại theo đường đi khác.

## 6. Thử sửa lỗi `IncompleteAnnotationException` bằng `default`

Ở file `current/demo/Metadata.java`, đổi thành:

~~~java
package demo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Metadata {
    String id();
    String owner() default "UNKNOWN";
}
~~~

Chỉ biên dịch lại bản **current** (`out/new`); vẫn giữ nguyên `Stored.class` từ `out/old`. Lúc này `metadata.owner()` có thể dùng giá trị mặc định `UNKNOWN` từ định nghĩa hiện tại. Output phần metadata chuyển từ:

~~~text
Missing element: owner
Annotation type: Metadata
~~~

sang:

~~~text
owner=UNKNOWN
~~~

**Chú ý:** `AnnotationTypeMismatchException` ở `State.value()` **vẫn tồn tại**, vì kiểu giá trị lưu cũ chưa tương thích với kiểu mới. Default chỉ xử lý phần tử *vắng mặt*, không tự chuyển kiểu cho phần tử đã lưu sai.

Một hướng sửa đầy đủ hơn là chuẩn hóa các phiên bản dependency và **biên dịch lại tất cả mã dùng annotation** với contract mới. Khi đó compiler phát hiện `@State("READY")` không còn hợp lệ với `int value()` và `@Metadata(id = "X1")` thiếu `owner` (nếu không có default), buộc người duy trì cập nhật source tương ứng.

## 7. Vì sao lỗi lại xuất hiện trong Reflection hoặc framework?

Nhiều thư viện dùng annotation để cấu hình mapping, validation, dependency injection, test, serialization hoặc các bộ xử lý plugin. Một framework có thể:

1. Quét class bằng `Class<?>` hoặc `AnnotatedElement`.
2. Lấy annotation bằng `getAnnotation`, `getDeclaredAnnotation` hay `getAnnotationsByType`.
3. Đọc phần tử annotation để quyết định hành vi, chẳng hạn `annotation.value()`.
4. Phát hiện dữ liệu class file cũ không phù hợp với hợp đồng annotation mới.

Để chẩn đoán đúng, trước hết xác định **method nào đã được gọi**, trên **annotation type nào**, bằng **classloader nào** và từ **artifact/version nào**. Có thể ghi thông tin có cơ sở như:

~~~java
Class<?> inspected = Class.forName("demo.Stored");
System.out.println(inspected.getName());
System.out.println(inspected.getClassLoader());

Class<?> annotationType = demo.State.class;
System.out.println(annotationType.getName());
System.out.println(annotationType.getClassLoader());
~~~

Đây là **fragment chẩn đoán**; trong ứng dụng thực tế cần thay tên `demo.Stored` và `demo.State` theo loại annotation đang gặp. Tránh log toàn bộ annotation bằng `toString()` như phương pháp chẩn đoán duy nhất: việc tạo biểu diễn chuỗi có thể đọc nhiều phần tử và chạm vào dữ liệu đang lỗi.

Nếu framework gọi accessor qua `java.lang.reflect.Method.invoke(...)`, ngoại lệ phát sinh trong method có thể được bọc bằng **`InvocationTargetException`**. Khi đó cần xem `getCause()` để tìm `AnnotationTypeMismatchException` hoặc `IncompleteAnnotationException` thực sự. Gọi trực tiếp `annotation.value()` như ví dụ ở trên có thể nhận ngoại lệ unchecked trực tiếp.

## 8. Phân biệt với các ngoại lệ và lỗi gần giống

| Loại | Tình huống thường gặp | Dấu hiệu quan trọng |
| --- | --- | --- |
| `AnnotationTypeMismatchException` | Giá trị annotation lưu trong class file không còn khớp kiểu phần tử hiện tại | `element()` và `foundType()` |
| `IncompleteAnnotationException` | Phần tử mới không có giá trị được lưu và không có default | `annotationType()` và `elementName()` |
| `EnumConstantNotPresentException` | Giá trị enum trong annotation trỏ tới hằng không còn tồn tại | `enumType()` và `constantName()` |
| `TypeNotPresentException` | Không giải quyết được tên một kiểu, có thể khi đọc phần tử annotation kiểu `Class<?>` | `typeName()` và `getCause()` |
| `AnnotationFormatError` | Class file có metadata annotation sai định dạng hoặc không hợp lệ theo parser | Là **`Error`**, cần kiểm tra class file/tool sinh bytecode |
| `ClassCastException` | Ép object sai kiểu trong ứng dụng hoặc interceptor | Không mặc nhiên đồng nghĩa metadata annotation đã đổi |
| `InvocationTargetException` | Reflection `Method.invoke` bọc lỗi mà method được gọi ném | Kiểm tra `getCause()` trước khi kết luận |

Đừng bắt chung `RuntimeException` rồi bỏ qua annotation lỗi: framework có thể tiếp tục chạy với cấu hình thiếu hoặc sai. Trong một pipeline build, nên coi mismatch là lỗi tương thích phiên bản cần sửa. Việc bắt riêng các exception thường thích hợp ở lớp log/chẩn đoán hoặc nơi có hành vi fallback được xác định rõ.

## 9. Các bước khắc phục một cách có hệ thống

### 9.1. Xác nhận chính xác annotation đang được dùng

Từ stack trace và exception:

- `AnnotationTypeMismatchException.element()`: xác định tên phần tử, lớp khai báo và kiểu trả về hiện tại.
- `AnnotationTypeMismatchException.foundType()`: dùng như dữ liệu phụ để gợi ý kiểu cũ, không parse cứng cấu trúc chuỗi.
- `IncompleteAnnotationException.annotationType()` và `elementName()`: xác định type và phần tử mới bắt buộc.

Sau đó so sánh API annotation giữa **bản compile** và **bản runtime**, xem release notes hoặc thay đổi mã nguồn thật sự. Nếu annotation do bên thứ ba cung cấp, cần kiểm tra toàn bộ cây dependency.

### 9.2. Kiểm tra class file và classpath/module path

Dùng công cụ JDK:

~~~bash
javap -v -p -classpath out/old demo.Stored
javap -p -classpath out/new demo.State
javap -p -classpath out/new demo.Metadata
~~~

`javap -v` có thể hiển thị metadata annotation và tên phần tử ghi trong `RuntimeVisibleAnnotations`. Các lệnh trên minh họa thư mục của bài thực hành; trong hệ thống thực tế, thay bằng đường dẫn tới JAR/lớp đang chạy.

Kiểm tra **nhiều phiên bản JAR của cùng thư viện**, thứ tự classpath, classloader tùy biến, module path và các build artifact cũ. Tên package/class giống nhau không có nghĩa bytecode hoặc loader đang dùng giống nhau.

### 9.3. Chọn phương án sửa đúng với contract

- **Lỗi type mismatch:** khôi phục kiểu cũ để tương thích hoặc sửa source sử dụng annotation theo kiểu mới rồi **clean build và triển khai đồng bộ**.
- **Lỗi incomplete:** cập nhật mọi vị trí sử dụng annotation để cung cấp phần tử mới, hoặc thêm `default` khi giá trị mặc định có ý nghĩa hợp lý và bảo đảm tương thích.
- **Thư viện nhiều module:** đồng bộ version của annotation API với module đã biên dịch sử dụng nó; không trộn class cũ và class mới sau một lần nâng cấp.
- **Code generator và bytecode tool:** kiểm tra pipeline sinh mã, vì công cụ có thể ghi annotation payload thiếu/sai ngay cả khi source Java thông thường không cho phép.

**Không** giải quyết bằng việc đổi tên exception, ép kiểu kết quả, hoặc dùng `setAccessible(true)`. Đây là lỗi **hợp đồng dữ liệu annotation tại runtime**, không phải thiếu quyền đọc private member.

## 10. Thiết kế annotation để giảm rủi ro nâng cấp

Khi công bố một annotation dùng chung cho nhiều module/JAR:

1. Xem **tên và kiểu của element** như hợp đồng API và hợp đồng binary metadata. Đổi kiểu trả về, xóa/đổi tên element là thay đổi cần đánh giá ảnh hưởng.
2. Cân nhắc **`default` cho element mới** nếu có giá trị mặc định hợp lý. Đừng gán default tùy tiện nếu thiếu cấu hình có thể gây hành vi nguy hiểm hoặc âm thầm sai.
3. Thử nghiệm với **consumer artifact đã biên dịch bằng bản trước**: chạy bằng annotation API mới để phát hiện lỗi tương thích, ngoài kiểm tra compile source mới.
4. Với thay đổi breaking, sử dụng lộ trình nâng version, migration guide và dependency constraints/BOM để tránh trộn nhiều phiên bản.
5. Khi dùng Reflection, ghi log có ngữ cảnh (annotation type, element, origin artifact), rồi báo rõ lỗi tương thích; không dựa vào output `toString()` của annotation.
6. Nếu giá trị cần cấu trúc phức tạp và thay đổi thường xuyên, cân nhắc mô hình metadata có schema/version rõ ràng thay vì đẩy mọi cấu hình vào annotation.

## 11. Tự kiểm tra

1. Class `Stored` được biên dịch với `@State("READY")` khi `State.value()` trả `String`. Runtime đổi `value()` thành `int` nhưng không biên dịch lại `Stored`. Lỗi nào? **`AnnotationTypeMismatchException` khi đọc phần tử không khớp.**
2. Một element `owner()` được thêm mà không có `default`; class file cũ không chứa nó. Lỗi nào? **`IncompleteAnnotationException` khi truy cập `owner()`.**
3. Thêm `default "UNKNOWN"` cho `owner()` giúp trường hợp nào? **Phần tử bị thiếu**; không sửa dữ liệu sai kiểu đã tồn tại.
4. Vì sao `getAnnotation(...) != null` chưa chứng minh hợp đồng đúng? Vì lỗi có thể phát sinh **khi đọc element** của annotation đã được lấy.
5. Khi bắt `AnnotationTypeMismatchException`, API nào cho biết method có vấn đề? **`element()`**, trả về `Method`.
6. Với `IncompleteAnnotationException`, lấy annotation type và tên phần tử bị thiếu ở đâu? **`annotationType()`** và **`elementName()`**.
7. Tại sao source hiện tại compile được vẫn có lỗi runtime? Vì JVM có thể đang nạp **class file consumer cũ** cùng **annotation API mới**, hoặc dependency/classloader không đồng bộ.
8. `foundType()` có an toàn để viết parser phụ thuộc format không? **Không**; API không đặc tả định dạng chuỗi.

## 12. Tài liệu Java 17

- [Oracle Java 17 — AnnotationTypeMismatchException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/AnnotationTypeMismatchException.html)
- [Oracle Java 17 — IncompleteAnnotationException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/IncompleteAnnotationException.html)
- [Oracle Java 17 — AnnotatedElement](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html)
- [Oracle Java 17 — RetentionPolicy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/RetentionPolicy.html)

**Ghi nhớ:** `AnnotationTypeMismatchException` báo **giá trị cũ sai kiểu theo element hiện tại**; `IncompleteAnnotationException` báo **element bắt buộc mới bị thiếu**. Cả hai thường là triệu chứng class file và định nghĩa annotation khác phiên bản. Muốn sửa tận gốc, tìm class/JAR thực tế JVM đang nạp, đối chiếu API giữa các bản và đồng bộ lại cách biên dịch/triển khai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exceptions-diagnostics-011">Phân biệt lỗi ở công cụ reflection với lỗi trong mã đích</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt lỗi ở công cụ Reflection với lỗi trong mã đích (Java)

## 1. Vì sao phải phân biệt hai nguồn lỗi?

Với Java Reflection, lời gọi nhìn giống `method.invoke(receiver, args)` nhưng thực tế trải qua hai lớp xử lý:

1. **Lớp hạ tầng Reflection**: tìm `Class`, tìm metadata của field/method/constructor, kiểm tra đối tượng nhận, quyền truy cập, số lượng và kiểu đối số, rồi điều phối lời gọi.
2. **Mã đích (target)**: thân method hoặc constructor được chạy và có thể trả kết quả hay ném exception/error vì chính logic nghiệp vụ.

Nếu lỗi xảy ra ở bước chuẩn bị/kiểm tra của Reflection, API thường ném **trực tiếp** exception tương ứng, như `NoSuchMethodException`, `IllegalAccessException` hoặc `IllegalArgumentException`. Nếu lời gọi đã vào thân method/constructor rồi mã đích ném ra `Throwable`, các API `Method.invoke` và `Constructor.newInstance` thường bọc lỗi đó trong **`InvocationTargetException`**.

Ví dụ hai trường hợp nhìn gần giống:

```java
// Sai số kiểu đối số: lỗi do Reflection kiểm tra trước lời gọi.
method.invoke(target, 123); // Nếu method nhận String: IllegalArgumentException.

// Kiểu đối số đúng nhưng bên trong method lại ném IllegalArgumentException.
// Kết quả của Method.invoke là InvocationTargetException;
// getCause() là IllegalArgumentException của chính method đích.
method.invoke(target, "invalid");
```

Đây là **mảnh code minh họa**; `method` và `target` phải được khai báo trong chương trình cụ thể. Không thể kết luận nguồn lỗi chỉ bằng từ khóa `IllegalArgumentException` trong log: cần xem **exception lớp ngoài, cause và giai đoạn xử lý**.

## 2. Sơ đồ luồng lỗi

```text
Class / tên thành viên / tham số
              |
              v
 [Reflection: tra cứu member]
      |                 |
   thất bại          tìm thấy
      |                 |
NoSuchMethodException  v
NoSuchFieldException   [Kiểm tra quyền, receiver, đối số]
                              |                 |
                           thất bại          hợp lệ
                              |                 |
                    IllegalAccessException     v
                    IllegalArgumentException [Khởi tạo lớp nếu cần]
                    NullPointerException       |
                                             [Chạy mã đích]
                                              /         \
                                        trả về         ném Throwable
                                          |                  |
                                      kết quả      InvocationTargetException
                                                        |
                                                    getCause()
                                                        |
                                                Throwable của mã đích
```

Sơ đồ là luồng **điển hình**, không phải danh sách đầy đủ mọi lỗi JVM. Đặc biệt, class initialization và linkage có thể thất bại trước khi thân method chạy; một số `Error` như `ExceptionInInitializerError` hoặc `LinkageError` có thể xuất hiện trực tiếp mà không qua `InvocationTargetException`. Do đó, đừng viết một khối `catch (InvocationTargetException)` rồi coi nó bao phủ mọi kiểu thất bại của Reflection.

## 3. Bảng phân loại theo nguồn phát sinh

| Dấu hiệu thấy được | Nơi phát sinh thường gặp | Cách hiểu/chẩn đoán |
|---|---|---|
| `ClassNotFoundException` | `Class.forName(name)` khi không tải được class theo tên | Tên/loader/classpath không tìm được lớp, chưa phải mã nghiệp vụ đích |
| `NoSuchMethodException` | `Class.getMethod`, `getDeclaredMethod`, `getConstructor`, `getDeclaredConstructor` | Không tìm được method/constructor theo phạm vi và danh sách **kiểu tham số** |
| `NoSuchFieldException` | `Class.getField` / `getDeclaredField` | Không tìm được field theo tên và phạm vi tra cứu |
| `IllegalAccessException` | `Method.invoke`, `Field.get/set`, `Constructor.newInstance` | Reflection tìm được metadata nhưng không có quyền thực hiện thao tác |
| `InaccessibleObjectException` | Ví dụ `AccessibleObject.setAccessible(true)` khi module không mở package thích hợp | Từ chối mở quyền truy cập sâu; đây là runtime exception của cơ chế truy cập |
| `IllegalArgumentException` trực tiếp | `Method.invoke` khi receiver không tương thích, sai số/kiểu đối số; `Field.get/set` dùng receiver/value không hợp lệ | Lỗi trong bước kiểm tra/call contract của Reflection |
| `NullPointerException` trực tiếp | `Method.invoke` trên instance method với receiver `null`; hoặc đối số bắt buộc của một API Reflection là `null` | Lỗi trước khi chạy thân method, tùy API và vị trí null |
| `InvocationTargetException` | `Method.invoke` hoặc `Constructor.newInstance` **sau khi mã đích ném lỗi** | Wrapper hạ tầng: dùng `getCause()` để nhận `Throwable` thực sự |
| `ExceptionInInitializerError` | Khởi tạo lớp thất bại do initializer ném exception không phải `Error` | Lỗi giai đoạn class initialization; có thể xảy ra trước thân method |
| `NoClassDefFoundError`, `NoSuchMethodError`, `NoSuchFieldError` | Class/linkage/runtime binary compatibility | Là `Error` cấp JVM; **khác** checked lookup exception có tên gần giống |
| `UndeclaredThrowableException` | Chẳng hạn dynamic proxy nhận checked exception từ `InvocationHandler` nhưng interface method không khai báo tương thích | Đặc thù cơ chế lời gọi JDK proxy; không phải wrapper mặc định của `Method.invoke` |

**Lưu ý:** `IllegalArgumentException`, `NullPointerException` hay `AssertionError` cũng có thể được **mã đích chủ động ném**. Trong trường hợp đó, khi gọi qua `Method.invoke`, chúng thường nằm **bên trong `InvocationTargetException.getCause()`**; không được phân loại đơn giản theo tên loại lỗi.

## 4. Hai giai đoạn dễ nhầm: tra cứu metadata và thực thi

Tra cứu:

```java
Class<?> type = Class.forName("java.lang.String");
java.lang.reflect.Method method = type.getMethod("substring", int.class);
```

Hai lời gọi trên tìm lớp và lấy metadata. Chúng **không chạy** thân `String.substring(...)`. Tên sai hoặc chữ ký không đúng có thể dẫn đến `ClassNotFoundException` hay `NoSuchMethodException`. Với `getDeclaredMethod`, không nên nhầm “declared” với “public”: nó chỉ tìm trong chính lớp đó và có thể tìm thấy thành viên private, nhưng tìm được chưa có nghĩa được phép gọi.

Thực thi:

```java
Object result = method.invoke("abcd", 2);
System.out.println(result); // cd
```

`Method.invoke` kiểm tra receiver và kiểu tham số, sau đó mới chạy thân method. Với method không có tham số có thể gọi `invoke(receiver)`. Với static method, đối số receiver của `invoke` có thể là `null`. Với instance method, receiver phải là một instance hợp lệ và không được null.

Với constructor, dùng `getDeclaredConstructor`/`getConstructor` để lấy metadata và `Constructor.newInstance(args)` để chạy thân constructor. `Class.newInstance()` đã deprecated và không có cùng hợp đồng bọc exception như `Constructor.newInstance()`; tránh dùng nó trong code mới.

## 5. Ví dụ Java 17+ hoàn chỉnh: từng nguồn lỗi được tách rõ

Tạo file **`ReflectionFaultsDemo.java`** chứa cả hai top-level class sau (chỉ `ReflectionFaultsDemo` là `public`):

```java
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionFaultsDemo {
    static class Broken {
        static int value = crash();
        static int crash() { throw new IllegalStateException("init failed"); }
        public static void ping() {}
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<Target> type = Target.class;
        Target instance = new Target("ok");
        try {
            type.getMethod("missing");
        } catch (NoSuchMethodException e) {
            System.out.println("lookup: " + e.getClass().getSimpleName());
        }

        Method hidden = type.getDeclaredMethod("hidden");
        try {
            hidden.invoke(instance);
        } catch (IllegalAccessException e) {
            System.out.println("access: " + e.getClass().getSimpleName());
        }

        Field secret = type.getDeclaredField("secret");
        try {
            secret.get(instance);
        } catch (IllegalAccessException e) {
            System.out.println("field access: " + e.getClass().getSimpleName());
        }

        Method greet = type.getMethod("greet", String.class);
        try {
            greet.invoke(instance, 42);
        } catch (IllegalArgumentException e) {
            System.out.println("wrong argument: " + e.getClass().getSimpleName());
        }

        try {
            greet.invoke(null, "Lan");
        } catch (NullPointerException e) {
            System.out.println("null receiver: " + e.getClass().getSimpleName());
        }

        System.out.println("success: " + greet.invoke(instance, "Lan"));

        for (String name : new String[]{"unchecked", "checked", "fatal"}) {
            try {
                type.getMethod(name).invoke(instance);
            } catch (InvocationTargetException e) {
                System.out.println(name + " target: " + e.getCause().getClass().getSimpleName());
            }
        }

        Constructor<Target> constructor = type.getConstructor(String.class);
        try {
            constructor.newInstance("bad");
        } catch (InvocationTargetException e) {
            System.out.println("constructor target: " + e.getCause().getClass().getSimpleName());
        }

        Class<?> broken = Class.forName(
            ReflectionFaultsDemo.Broken.class.getName(),
            false,
            ReflectionFaultsDemo.class.getClassLoader()
        );
        try {
            broken.getMethod("ping").invoke(null);
        } catch (ExceptionInInitializerError e) {
            System.out.println("initialization: " + e.getClass().getSimpleName());
        }
    }
}

class Target {
    private String secret = "hidden";

    public Target(String value) {
        if ("bad".equals(value)) {
            throw new IllegalStateException("constructor failed");
        }
    }
    private void hidden() {}
    public String greet(String name) { return "Hello, " + name; }
    public void unchecked() { throw new IllegalStateException("target bug"); }
    public void checked() throws IOException { throw new IOException("target IO"); }
    public void fatal() { throw new AssertionError("target error"); }
}
```

Biên dịch và chạy từ thư mục chứa file:

```bash
javac ReflectionFaultsDemo.java
java ReflectionFaultsDemo
```

Stdout đã được kiểm tra:

```text
lookup: NoSuchMethodException
access: IllegalAccessException
field access: IllegalAccessException
wrong argument: IllegalArgumentException
null receiver: NullPointerException
success: Hello, Lan
unchecked target: IllegalStateException
checked target: IOException
fatal target: AssertionError
constructor target: IllegalStateException
initialization: ExceptionInInitializerError
```

### Phân tích kết quả theo giai đoạn

1. `getMethod("missing")` thất bại ngay khi **lookup** vì `Target` không có public method tương ứng, nên trả trực tiếp `NoSuchMethodException`.
2. `hidden` và `secret` thực sự tồn tại, vì `getDeclaredMethod` / `getDeclaredField` tìm được. Tuy nhiên, chúng là private members của **top-level class `Target` độc lập**; truy cập từ `ReflectionFaultsDemo` không được phép, nên `Method.invoke` và `Field.get` ném trực tiếp `IllegalAccessException`. Nếu chuyển `Target` thành nested class cùng nest với caller, kết quả quyền truy cập có thể khác.
3. `greet(String)` tồn tại nhưng truyền `42` (an `int` boxed thành `Integer`) không phù hợp với `String`. Reflection ném trực tiếp `IllegalArgumentException` trước khi chạy `Target.greet`.
4. Receiver `null` của `greet(String)` là không hợp lệ vì đây là instance method. `Method.invoke` ném trực tiếp `NullPointerException`. Nếu một **static** method được gọi bằng `invoke(null, ...)` thì tình huống khác.
5. Với receiver và argument hợp lệ, `greet` chạy bình thường và kết quả trả về qua `invoke`.
6. Các method `unchecked()`, `checked()`, `fatal()` cố ý ném `IllegalStateException`, `IOException` và `AssertionError`. `Method.invoke` bọc **cả runtime exception, checked exception và Error từ thân method đích** trong `InvocationTargetException`; `getCause()` cho ra đúng kiểu gốc.
7. Thân constructor `Target("bad")` ném `IllegalStateException`; `Constructor.newInstance` cũng bọc lỗi đích trong `InvocationTargetException`.
8. `Broken` được lấy `Class` với `initialize=false`, rồi gọi static method qua Reflection. Trước khi thân `ping` chạy, JVM phải khởi tạo lớp; initializer thất bại nên ném **`ExceptionInInitializerError`**. Đây là lỗi khởi tạo lớp, không phải method `ping` ném `InvocationTargetException`.

Ví dụ sử dụng Java syntax tương thích Java 17+ và đã chạy thành công. Nó không gọi `setAccessible(true)` vì mục tiêu là quan sát rõ ranh giới quyền truy cập của Reflection.

## 6. Cách đọc InvocationTargetException đúng cách

`InvocationTargetException` là một **checked exception của lớp Reflection**, nhưng nội dung quan trọng thường nằm trong `getCause()` (tương đương `getTargetException()` theo API cũ):

```java
try {
    Object result = method.invoke(receiver, arguments);
    // Chỉ tới đây khi method đích hoàn tất bình thường.
} catch (InvocationTargetException e) {
    Throwable original = e.getCause();
    // original xuất phát từ method đích.
    throw new IllegalStateException("Target method failed", original);
} catch (IllegalAccessException e) {
    // Lỗi quyền truy cập từ Reflection.
    throw new IllegalStateException("Cannot invoke the target method", e);
} catch (IllegalArgumentException e) {
    // Có thể là receiver/arguments không hợp lệ ở tầng Reflection.
    throw new IllegalArgumentException("Invalid reflective invocation", e);
}
```

Đây là **mảnh code minh họa** trong một phương thức có quyền throw runtime exceptions; `method`, `receiver`, `arguments` phải được định nghĩa. Nếu `original` là `InterruptedException`, `IOException` hoặc exception nghiệp vụ được phép xử lý tường minh, hãy xử lý đúng hợp đồng thay vì gộp tất cả vào một runtime exception.

**Không nên** log duy nhất `e.getMessage()`: wrapper có thể không nêu được nguyên nhân nghiệp vụ. Khi log, giữ stack trace của `cause`, tên operation, signature, target class và request/correlation ID phù hợp. Không in raw arguments chứa mật khẩu, access token hay dữ liệu nhạy cảm.

### Khi cần chuyển tiếp ngoại lệ gốc

Ở một API đã khai báo checked exception cụ thể, có thể gỡ `InvocationTargetException` rồi ném lại đúng loại: chẳng hạn `if (cause instanceof IOException io) throw io;` trong method có `throws IOException`. Với `RuntimeException` hoặc `Error`, quyết định truyền tiếp theo hợp đồng của tầng gọi; với checked exception bất ngờ, tạo wrapper **giữ cause**. Cách nhận diện từng kiểu và chính sách giữ stack trace được minh họa sâu ở bài về `InvocationTargetException` (hàng 299). Ở bài này, điều quan trọng là xác nhận mã đích **đã chạy** trước khi phân loại lỗi.

## 7. Field.get/set và method/constructor invoke không có cùng hành vi

Một trường hợp dễ sai là nghĩ *mọi* thao tác Reflection đều bọc lỗi mã đích trong `InvocationTargetException`. Thực tế:

- `Field.get(target)` / `Field.set(target, value)` chủ yếu kiểm tra quyền truy cập, receiver và kiểu dữ liệu rồi đọc/ghi field; chúng **không gọi thân method nghiệp vụ**, nên không có quy tắc bọc “exception của thân method” như `Method.invoke`.
- `Class.getMethod/getDeclaredMethod/getField/getDeclaredField` là các thao tác metadata lookup: có thể ném checked lookup exception nhưng **chưa gọi target method/constructor**.
- `Method.invoke` chạy thân method sau kiểm tra. Nếu chính thân method ném `Throwable`, thường nhận `InvocationTargetException`.
- `Constructor.newInstance` thực thi constructor. Nếu thân constructor ném exception/error, cũng thường nhận `InvocationTargetException`.
- `MethodHandles.Lookup`, `MethodHandle.invoke/invokeExact` và invocation trực tiếp có **hợp đồng khác**. Chẳng hạn exception từ target của `MethodHandle` không mặc định bị bọc bởi `InvocationTargetException`. Không áp quy tắc của `Method.invoke` cho toàn bộ Java invocation API.

Trước khi quyết định catch kiểu gì, phải đọc đúng API cụ thể đang được gọi và giai đoạn phát sinh lỗi.

## 8. Phân biệt lỗi trước khi mã đích chạy

Ngoài kiểm tra quyền và đối số, JVM có thể thất bại ở giai đoạn **tải, liên kết hoặc khởi tạo lớp**, trước khi method/constructor body chạy. Các tín hiệu quan trọng:

| Tín hiệu | Nơi kiểm tra trước |
| --- | --- |
| `ClassNotFoundException` | Tên binary, class loader và classpath của thao tác tìm class theo tên |
| `NoClassDefFoundError` | Dependency thiếu khi linkage, hoặc lần khởi tạo class trước đã thất bại |
| `ExceptionInInitializerError` | Cause gốc trong static initializer; đừng gộp vào `InvocationTargetException` của mã đích |
| `NoSuchMethodError`, `NoSuchFieldError`, `UnsupportedClassVersionError` | Class file và phiên bản phụ thuộc không tương thích runtime |

`Class.forName(name, false, loader)` có thể trì hoãn initialization lúc khám phá metadata, nhưng một lời gọi static sau đó vẫn có thể kích hoạt initializer và gặp lỗi. Bài 294 phân tích loading/linking và bài 300 phân tích riêng lỗi class initialization; bảng này dùng để **xác định tầng lỗi** trong luồng điều tra tổng hợp.

## 9. Trường hợp đặc biệt: JDK dynamic proxy và InvocationHandler

Trong `java.lang.reflect.Proxy`, lời gọi interface được chuyển vào `InvocationHandler.invoke`. Một handler có thể dùng Reflection để gọi target:

```java
// Mảnh code minh họa trong một InvocationHandler.
// targetMethod, realTarget và args do handler quản lý.
try {
    return targetMethod.invoke(realTarget, args);
} catch (InvocationTargetException e) {
    throw e.getCause(); // InvocationHandler.invoke cho phép throws Throwable.
}
```

Nếu handler không gỡ wrapper, client có thể nhìn thấy `InvocationTargetException` hoặc một wrapper khác phát sinh khi proxy cần áp quy tắc checked exceptions. Vì vậy một handler forwarding điển hình **unwrap `InvocationTargetException`** để đưa exception thực của target trở lại cơ chế dispatch proxy.

Với JDK dynamic proxy:

- Một checked exception do `InvocationHandler.invoke` ném ra mà **không phù hợp với `throws` của method interface** có thể được bọc trong `UndeclaredThrowableException`.
- Runtime exception và `Error` không cần được khai báo qua `throws`.
- Exception nhìn thấy ở client còn phụ thuộc implementation handler/framework và những lớp interception xếp chồng.

Không nhầm `UndeclaredThrowableException` với `InvocationTargetException`: cái đầu thuộc hợp đồng **proxy dispatch đối với checked exception không khai báo**, cái sau thuộc hợp đồng **Method.invoke/Constructor.newInstance khi mã đích ném lỗi**.

## 10. Quy trình chẩn đoán theo sự kiện

### Bước 1 — Xác định thao tác gây lỗi

Ghi tên API đang gọi: `Class.forName`, `getDeclaredMethod`, `Method.invoke`, `Field.get`, `Constructor.newInstance` hoặc `InvocationHandler.invoke`. Không chỉ dựa vào “stack trace có `java.lang.reflect`”.

### Bước 2 — Quan sát outer exception và chuỗi cause

- Nếu outer là `InvocationTargetException`, xem `getCause()` đầu tiên.
- Nếu outer là `IllegalAccessException` hoặc `IllegalArgumentException` ném trực tiếp từ invocation, kiểm tra quyền, receiver, parameter count/types.
- Nếu là `ExceptionInInitializerError` hoặc `LinkageError`, phân tích class init/dependency trước khi quy lỗi cho body.
- Nếu qua nhiều framework/proxy, có thể có nhiều tầng wrapper. Theo chuỗi `getCause()` một cách có giới hạn và có cơ chế chống vòng lặp.

### Bước 3 — So sánh actual với expected metadata

```java
System.out.println(method.toGenericString());
System.out.println(java.util.Arrays.toString(method.getParameterTypes()));
System.out.println(method.getDeclaringClass().getName());
```

Đây là **mảnh code minh họa**: `method` phải tồn tại trong ngữ cảnh. Có thể bổ sung loại và class loader của receiver, không log object hoặc tham số nhạy cảm. Lưu ý Reflection metadata có thể biểu thị bridge/synthetic method do compiler sinh ra.

### Bước 4 — Chọn hành động đúng với lỗi

| Nguồn lỗi | Hướng xử lý |
|---|---|
| Lookup: tên hoặc signature không khớp | Sửa tên, parameter classes, thứ tự, overload; chọn `get...` hay `getDeclared...` đúng phạm vi |
| Access: visibility hoặc JPMS | Dùng API public, điều chỉnh thiết kế/module exports/opens một cách có chủ đích |
| Invocation arguments: receiver/arity/types sai | Sửa đối tượng và argument list, bao gồm primitive/wrapper/varargs |
| Target exception từ `getCause()` | Xử lý theo hợp đồng nghiệp vụ, giữ cause và stack trace |
| Initialization/linkage | Sửa lỗi static initialization hoặc dependency/runtime version |
| Proxy wrappers | Xem handler, declared exceptions và lớp interception thực tế |

**Bước 5 — Test đúng cả hai đường đi.** Viết riêng test cho tình huống Reflection có metadata/arguments không hợp lệ và test target chủ động ném lỗi. Assert **outer exception và cause**; đừng chỉ so sánh chuỗi message vì message phụ thuộc implementation và môi trường.

## 11. Những lỗi phổ biến khi xử lý exception Reflection

1. **`catch (Exception ignored)` rồi trả `null`:** làm mất lỗi cấu hình và stack trace; ứng dụng sau đó có thể ném `NullPointerException` ở chỗ hoàn toàn khác.
2. **Bắt `IllegalArgumentException` và kết luận target sai:** nếu nó được ném trực tiếp từ `Method.invoke` thì thường là receiver/argument sai; nếu nó nằm trong `InvocationTargetException.getCause()` thì mới là lỗi target.
3. **Bắt mọi `InvocationTargetException` thành “Reflection bị hỏng”:** exception này thường chỉ báo **mã đích đã được gọi và ném lỗi**.
4. **Bỏ cause khi đổi exception:** `throw new RuntimeException("failed")` không giữ nguyên nguồn lỗi; dùng constructor nhận `cause` hoặc rethrow có chủ đích.
5. **Lẫn checked exception và `Error`:** `catch (Exception)` không bao `Error`; không nuốt `OutOfMemoryError`, `LinkageError` hoặc các lỗi JVM nặng bằng xử lý fallback chung.
6. **Cố gọi private method bằng `setAccessible(true)` vô điều kiện:** module mạnh có thể từ chối, và việc mở truy cập sâu có thể vi phạm thiết kế encapsulation.
7. **Đánh đồng proxy dispatch với Reflection invoke:** `UndeclaredThrowableException` không phải kết quả mặc định của mọi `Method.invoke`.
8. **Dựa vào chỉ một dòng log:** ưu tiên outer class, cause chain, operation, target metadata và thời điểm xảy ra lỗi.

## 12. Tự kiểm tra

1. `type.getMethod("run")` không tìm được method public `run()` thì exception chính là gì? — `NoSuchMethodException`, ở giai đoạn tra cứu.
2. `method.invoke(target, wrongArg)` với kiểu argument không hợp lệ? — Thường `IllegalArgumentException` **trực tiếp** từ Reflection.
3. `method.invoke(target)` gọi vào thân method và thân ném `IOException`? — `InvocationTargetException`; `getCause()` là `IOException`.
4. Nếu thân method ném `AssertionError`? — Nó vẫn có thể được `Method.invoke` bọc trong `InvocationTargetException`; kiểm tra `cause`.
5. `Field.get(receiver)` bị từ chối vì field private? — `IllegalAccessException` trực tiếp, không cần `InvocationTargetException`.
6. Static initializer thất bại trước thân method? — Có thể `ExceptionInInitializerError` trực tiếp; xử lý lỗi khởi tạo.
7. `NoSuchMethodError` có giống `NoSuchMethodException` không? — Không; một bên là `LinkageError`, một bên là checked Reflection lookup exception.

## 13. Ghi nhớ

- **Phân loại theo giai đoạn**: lookup, access/argument checks, class initialization/linkage, rồi mới tới thân method/constructor.
- **Phân loại theo lớp ngoài và `cause`**: `InvocationTargetException` là wrapper; `getCause()` thường cho biết exception của chính mã đích.
- **Không mọi lỗi Reflection đều được bọc**: `NoSuchMethodException`, `IllegalAccessException`, `IllegalArgumentException` và nhiều JVM `Error` có thể đi trực tiếp.
- **API khác nhau có hợp đồng khác nhau**: `Field.get`, `Method.invoke`, `Constructor.newInstance`, `MethodHandle` và dynamic proxy không hoàn toàn giống nhau.
- **Giữ ngữ cảnh chẩn đoán**: operation + signature + class/loader + cause chain, cùng cách xử lý riêng cho lỗi hạ tầng và lỗi nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exceptions-diagnostics-012">Ghi ngữ cảnh lớp, loader, module và chữ ký khi điều tra</a>

<details>
<summary>Xem chi tiết</summary>

# Ghi ngữ cảnh lớp, class loader, module và chữ ký khi điều tra lỗi reflection

## 1. Vì sao thông báo ngoại lệ đơn lẻ chưa đủ?

Khi một thao tác reflection thất bại, chỉ ghi `exception.getMessage()` thường chưa giải thích được nguyên nhân. Cùng tên lớp có thể được nạp bởi hai `ClassLoader` khác nhau; cùng tên phương thức nhưng khác tham số là hai chữ ký khác nhau; một thành viên tồn tại nhưng bị giới hạn truy cập bởi Java Platform Module System (JPMS). Vì vậy, điều tra hiệu quả phải đặt ngoại lệ trong **ngữ cảnh chính xác của đối tượng `Class`, loader, module, thành viên và hành động đã thử**.

Các tình huống điển hình:

- `ClassNotFoundException` khi tải lớp bằng tên nhưng loader được chọn không nhìn thấy lớp.
- `NoSuchMethodException` vì tra sai kiểu tham số, nhầm primitive với wrapper, chỉ tìm declared method hay quên class cha.
- `IllegalAccessException` hoặc `InaccessibleObjectException` vì quyền truy cập Java/module không đáp ứng thao tác reflection.
- `IllegalArgumentException` khi gọi `Method.invoke` với receiver/đối số không phù hợp.
- `InvocationTargetException`: chính phương thức đích ném ngoại lệ; cần xem `getCause()` hoặc `getTargetException()`.
- `ClassCastException` có thể xuất hiện dù hai lớp được hiển thị **cùng tên**, nếu chúng khác danh tính do class loader.
- `NoSuchMethodError` hoặc các lỗi linkage khác xảy ra ở giai đoạn liên kết mã đã biên dịch với runtime, **khác** `NoSuchMethodException` khi tra reflection.

Mục tiêu của log điều tra là trả lời: *đang kiểm tra lớp nào, do loader nào định nghĩa, nằm trong module nào, cần chữ ký nào, thực tế tìm thấy gì, thất bại ở bước nào và nguyên nhân gốc là gì?*

## 2. Bộ thông tin nên ghi cho mỗi lần điều tra

| Nhóm | Dữ liệu nên thu thập | Công dụng |
| --- | --- | --- |
| Hoạt động | `operation`: `loadClass`, `getDeclaredMethod`, `invoke`, `trySetAccessible`… | Phân biệt lỗi tra cứu, quyền truy cập và lỗi trong phần thân |
| Định danh lớp | `Class.getName()`, `getTypeName()`, `getPackageName()` | Phát hiện nhầm package, tên binary, kiểu mảng, lớp lồng |
| Loader | `Class.getClassLoader()`; loại loader và ID tạm trong tiến trình | Phát hiện cùng tên nhưng khác defining loader |
| Module | `Class.getModule()`, `getName()`, `isNamed()`; package của thành viên | Phân tích module/class path và JPMS |
| Thành viên | `getDeclaringClass()`, tên, kiểu tham số, kiểu trả về, modifiers | Xác minh chữ ký và quyền truy cập |
| Chữ ký | `Method.toGenericString()`, `getParameterTypes()`, kiểu trả về; khi cần JVM descriptor | Phân biệt overload, erasure, bridge method |
| Chủ thể gọi | Class/module của code đang reflection; receiver class thực tế | Truy cập phụ thuộc cả caller lẫn target |
| Ngoại lệ | Loại lỗi, thông báo, cause chain, stack trace, target exception | Truy đến bước và nguyên nhân gốc |
| Môi trường | Runtime Java, cơ chế triển khai, phiên bản artifact/class path/module path | Tái hiện khác biệt giữa máy/phiên bản |

**Ghi cả *expected* và *actual*.** Ví dụ: lookup cần `format(int)` nhưng hệ thống truyền `Integer.class`; chỉ log tên `format` sẽ bỏ lỡ sự khác biệt. Trong hệ thống nhiều plugin, so sánh thêm đối tượng `Class` và class loader, không chỉ so tên.

## 3. Phân biệt tên lớp và danh tính lớp

`Class.getName()` trả tên nhị phân, nên lớp lồng thường có ký tự `$` (ví dụ `Outer$Inner`). Với mảng, `getName()` sử dụng dạng JVM như `[Ljava.lang.String;`; `getTypeName()` dễ đọc hơn như `java.lang.String[]`. `getCanonicalName()` có thể trả `null` với kiểu local hoặc anonymous: không nên dựa vào nó làm khóa bắt buộc.

Đối với một class Java thông thường, danh tính runtime gắn với **tên nhị phân và class loader định nghĩa**. Hai loader khác nhau có thể định nghĩa hai lớp cùng tên mà JVM vẫn xem là hai kiểu khác nhau.

```java
// Giả sử first và second đã được lấy từ hai hệ thống plugin:
boolean sameName = first.getName().equals(second.getName());
boolean sameClass = first == second;
boolean sameDefiningLoader =
        first.getClassLoader() == second.getClassLoader();
```

Đây là **đoạn minh họa**, không phải chương trình độc lập. Nếu `sameName == true` nhưng `sameClass == false`, cần kiểm tra loader, module, đường nạp class và bản build/JAR.

`Class.getClassLoader() == null` thường đại diện lớp do **bootstrap class loader** định nghĩa; đây không có nghĩa là thông tin lỗi bị thiếu. `ClassLoader.getClass().getName()` chỉ cho biết *loại loader*, chưa đủ phân biệt hai instance cùng loại. Khi cần liên hệ sự kiện **trong một JVM**, có thể ghi thêm `System.identityHashCode(loader)` cùng tên loại loader, nhưng giá trị này không phải mã định danh toàn cục, không bảo đảm duy nhất và không ổn định qua lần khởi động.

Không suy ra rằng loader mà code **yêu cầu** nạp lớp luôn là loader **định nghĩa** lớp cuối cùng: cơ chế parent delegation có thể chuyển việc nạp cho parent. Ghi `targetClass.getClassLoader()` để biết loader định nghĩa; có thể ghi riêng loader được sử dụng ở lệnh tra cứu.

## 4. Module và quyền truy cập reflection (Java 9+)

Mỗi `Class` thuộc một `Module`: `type.getModule()`. Nếu `module.isNamed() == false`, module đó là **unnamed module**, thường gặp khi chạy Java bằng class path. `module.getName()` khi đó trả `null`; nên hiển thị nhãn rõ ràng như `<unnamed>`.

Với **named module**:

- `exports p` công khai các **public** type/member trong package `p` cho module được phép đọc và truy cập.
- `opens p` cho phép **deep reflection** vào package `p` đối với module nhận quyền (bên cạnh các điều kiện truy cập khác).
- `Module.isExported(p, callerModule)` và `Module.isOpen(p, callerModule)` giúp xem một package có được export/open đến module gọi hay không.
- `setAccessible(true)` không thể tùy ý vượt qua giới hạn JPMS; có thể ném `InaccessibleObjectException`.
- `trySetAccessible()` trả `false` nếu không thể mở quyền truy cập (nhưng có thể ném `SecurityException` nếu bị từ chối bởi kiểm tra bảo mật). **Lưu ý: thao tác này có thể thay đổi accessible flag khi thành công**, nên không dùng chỉ để đọc trạng thái trên đối tượng `Method` đang được chia sẻ.

Muốn kiểm tra *khả năng truy cập theo caller* với một `Method`/`Field` mà không đổi flag, dùng `canAccess(receiver)` khi cung cấp receiver hợp lệ: instance method/field dùng instance thích hợp, static member hoặc constructor dùng `null`. Không dùng `canAccess(null)` với instance member.

Khi có `InaccessibleObjectException`, log **module đích + package + module gọi + thao tác đang thử**, sau đó xem cấu hình `module-info.java` và quan hệ `exports`/`opens`. Chỉ dùng cấu hình `--add-opens module/package=targetModule` như giải pháp có kiểm soát khi hiểu rõ ranh giới quyền; không áp dụng mở rộng bừa bãi trên môi trường sản xuất.

Xem đặc biệt sự khác nhau giữa **tìm thấy metadata** và **được phép truy cập**: `getDeclaredMethod` có thể tìm thấy phương thức private, nhưng `invoke` vẫn có thể bị từ chối nếu chưa có quyền hợp lệ.

## 5. Ghi chính xác chữ ký, kể cả overload và generics

Với `java.lang.reflect.Method`:

- `getName()`: tên phương thức.
- `getDeclaringClass()`: class/interface **khai báo** thành viên; có thể khác runtime class của receiver.
- `getParameterTypes()` và `getReturnType()`: chữ ký sau erasure ở runtime.
- `getGenericParameterTypes()`, `getGenericReturnType()` và `toGenericString()`: thông tin generic còn được lưu ở metadata.
- `getModifiers()` với `Modifier.toString(...)`; `isBridge()`, `isSynthetic()`, `isVarArgs()`: hữu ích khi compiler sinh bridge/synthetic method hoặc thao tác biến tham số.
- `getExceptionTypes()`: danh sách các exception được khai báo, khác với exception thực tế đã ném.

`Class.getDeclaredMethod(name, parameterTypes...)` tra phương thức **khai báo trực tiếp** trong class; `Class.getMethod(...)` tra phương thức **public** có thể truy cập qua quy tắc kế thừa/public members. Khi truyền tham số tra cứu, dùng **chính xác** `Class` của từng tham số: `int.class != Integer.class`. Thứ tự cũng phải trùng; return type không nằm trong danh sách tham số của `getDeclaredMethod`, mặc dù một `Method` có thể báo return type.

Một generic method khai báo `<T extends Number> T echo(T item)` có kiểu tham số runtime là `Number.class`, vì erasure. Việc tra `echo(Integer.class)` sẽ không tìm thấy nó. Nếu cần phân tích generic arguments và type variables, đọc `getGenericParameterTypes()` song song với kiểu runtime.

Với `MethodHandle`, chữ ký lời gọi có thể bao gồm receiver đứng đầu, ngay cả khi chữ ký phương thức Java không biểu diễn receiver thành một tham số rõ ràng. Khi điều tra handle, ghi `handle.type()`; với `VarHandle`, ghi `accessModeType(mode)`. Không đánh đồng `MethodHandle.type()` với kết quả chỉ lấy từ `Method.getParameterTypes()`.

Một biểu diễn JVM descriptor có thể được tạo từ kiểu trả về và tham số (sau erasure):

```java
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

// Đoạn minh họa; giả sử đã có Method method:
String descriptor = MethodType.methodType(
        method.getReturnType(), method.getParameterTypes())
        .toMethodDescriptorString();
```

Ví dụ `String format(int)` cho descriptor `(I)Ljava/lang/String;`. Descriptor thuận tiện để đối chiếu bytecode, nhưng **không thay thế** tên phương thức, declaring class và loader/module.

## 6. Ví dụ Java 17+ chạy độc lập: chụp ngữ cảnh và khoanh vùng lỗi

Lưu file `ReflectionInvestigationDemo.java`:

```java
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class ReflectionInvestigationDemo {
    static final class Sample {
        private final String code;

        Sample(String code) {
            this.code = code;
        }

        public String format(int revision) {
            return code + "-" + revision;
        }

        public <T extends Number> T echo(T value) {
            return value;
        }
    }

    static void describe(String tag, Class<?> type) {
        ClassLoader loader = type.getClassLoader();
        Module module = type.getModule();
        String moduleName = module.isNamed()
                ? module.getName() : "<unnamed>";
        String packageName = type.getPackageName().isEmpty()
                ? "<default>" : type.getPackageName();

        System.out.println(tag + ".class=" + type.getName());
        System.out.println(tag + ".loader=" +
                (loader == null ? "bootstrap" : "non-bootstrap"));
        System.out.println(tag + ".module=" + moduleName);
        System.out.println(tag + ".package=" + packageName);
    }

    public static void main(String[] args) throws Exception {
        describe("target", Sample.class);
        describe("jdk", String.class);

        Method m = Sample.class.getDeclaredMethod("format", int.class);
        System.out.println("method=" + m.getName());
        System.out.println("declaring=" +
                m.getDeclaringClass().getName());
        System.out.println("parameters=" +
                Arrays.toString(m.getParameterTypes()));
        System.out.println("return=" + m.getReturnType().getTypeName());
        System.out.println("descriptor=" +
                MethodType.methodType(
                        m.getReturnType(), m.getParameterTypes())
                        .toMethodDescriptorString());
        System.out.println("public=" +
                Modifier.isPublic(m.getModifiers()));
        System.out.println("bridge=" + m.isBridge());

        Method generic = Sample.class.getDeclaredMethod(
                "echo", Number.class);
        System.out.println("erased=" +
                Arrays.toString(generic.getParameterTypes()));
        System.out.println("generic=" +
                Arrays.toString(generic.getGenericParameterTypes()));

        try {
            Sample.class.getDeclaredMethod("format", Integer.class);
        } catch (NoSuchMethodException e) {
            System.out.println("lookup.failure=" +
                    e.getClass().getSimpleName());
            System.out.println("requested.parameter=Integer");
        }

        Sample instance = new Sample("A");
        try {
            m.invoke(instance, "2");
        } catch (IllegalArgumentException e) {
            System.out.println("invoke.failure=" +
                    e.getClass().getSimpleName());
        }

        System.out.println("result=" + m.invoke(instance, 2));
    }
}
```

Biên dịch và chạy trên class path từ thư mục chứa file:

```bash
javac --release 17 -encoding UTF-8 ReflectionInvestigationDemo.java
java ReflectionInvestigationDemo
```

Stdout mong đợi:

```text
target.class=ReflectionInvestigationDemo$Sample
target.loader=non-bootstrap
target.module=<unnamed>
target.package=<default>
jdk.class=java.lang.String
jdk.loader=bootstrap
jdk.module=java.base
jdk.package=java.lang
method=format
declaring=ReflectionInvestigationDemo$Sample
parameters=[int]
return=java.lang.String
descriptor=(I)Ljava/lang/String;
public=true
bridge=false
erased=[class java.lang.Number]
generic=[T]
lookup.failure=NoSuchMethodException
requested.parameter=Integer
invoke.failure=IllegalArgumentException
result=A-2
```

Điểm học được:

1. `Sample.class` là class do application (hoặc loader khác không phải bootstrap) nạp và ở unnamed module khi chạy bằng class path; `String.class` ở `java.base` và do bootstrap loader định nghĩa.
2. Tên nhị phân của nested class chứa `$`; việc chỉ dùng tên hiển thị đơn giản `Sample` sẽ đánh mất ngữ cảnh.
3. Tra `format(int.class)` thành công, tra `format(Integer.class)` thất bại vì primitive `int` khác wrapper `Integer`.
4. Generic `echo(T)` có tham số runtime `Number` trong khi `getGenericParameterTypes()` còn trả type variable `T`.
5. `Method.invoke(instance, "2")` thất bại vì tham số `String` không thể chuyển thành `int` theo quy tắc cho phép của invocation; sử dụng đối số kiểu `int` mới thành công.
6. Chữ ký và descriptor xác nhận chính xác phương thức đang gọi. Ví dụ chỉ ghi loại lỗi tại các nhánh đã biết nên không đưa thông tin nhạy cảm vào stdout.

Trong môi trường module path hoặc loader tùy biến, vài dòng module/loader có thể khác; ví dụ dùng class path để có đầu ra xác định.

## 7. Khi ngoại lệ phát sinh: chụp cả cause chain và điểm thất bại

Với `Method.invoke`, lỗi *bên trong thân phương thức đích* thường bị gói vào `InvocationTargetException`. Lỗi **trước khi thực thi thân** như receiver/arguments sai hoặc thiếu quyền truy cập lại thuộc nhóm khác. Tách hai nhánh khi điều tra:

```java
// Đoạn minh họa đặt trong phương thức có Method method,
// Object receiver và Object[] arguments hợp lệ:
try {
    method.invoke(receiver, arguments);
} catch (java.lang.reflect.InvocationTargetException e) {
    Throwable root = e.getTargetException();
    // Ghi loại lỗi + stack/cause chain theo chính sách ứng dụng.
    // Không vô tình bỏ qua ngoại lệ gốc.
    System.err.println("target threw: " +
            root.getClass().getName());
} catch (IllegalAccessException e) {
    System.err.println("reflection access denied: " +
            e.getClass().getName());
} catch (IllegalArgumentException e) {
    System.err.println("bad receiver/arguments: " +
            e.getClass().getName());
}
```

Đây **không phải chương trình độc lập**. Khi viết helper phục vụ production, nên log structured fields (`operation`, `targetClass`, `loader`, `module`, `methodSignature`, `exceptionType`, `causeType`) và giữ stack trace gốc thông qua logger. Tránh chỉ gọi `getCause().getCause()` không kiểm tra null vì cause chain có thể ngắn hơn dự kiến. Cũng cần tránh vòng lặp khi tự duyệt chain với Throwable bất thường; ưu tiên logging framework đã quản lý stack trace.

### Nhận diện nhanh theo ngoại lệ

| Ngoại lệ/lỗi | Câu hỏi ưu tiên |
| --- | --- |
| `ClassNotFoundException` | Tên binary đúng chưa? Loader yêu cầu nạp là gì? Class path/module path có dependency đó không? |
| `NoSuchMethodException` | Đúng overload, kiểu tham số, thứ tự, primitive/wrapper, erasure và `getDeclaredMethod`/`getMethod` chưa? |
| `IllegalAccessException` | Caller, visibility, module readability/exports hoặc điều kiện access checks có phù hợp không? |
| `InaccessibleObjectException` | Có đang cố bật deep reflection mà package/module không mở quyền không? |
| `IllegalArgumentException` khi `Method.invoke` | Receiver có đúng declaring class? Đối số có đúng số lượng/kiểu và varargs chưa? |
| `InvocationTargetException` | Exception gốc của thân phương thức là gì? Dòng stack phía ứng dụng nào gây ra? |
| `ClassCastException` | Hai class thật sự bằng nhau qua `==` không? Defining loader và module khác nhau không? |
| `NoSuchMethodError` / `LinkageError` | Có sự khác biệt bytecode/binary giữa bản compile và runtime, xung đột dependency hoặc loader constraint không? |

**Phân biệt:** `NoSuchMethodException` là checked exception của API tra cứu reflection; `NoSuchMethodError` thuộc `LinkageError`, thường phản ánh mismatch liên kết binary. Cách điều tra không thể chỉ dựa vào cùng cụm chữ "NoSuchMethod".

## 8. Ghi log đủ dùng nhưng không làm phát sinh lỗi mới

Để có log chẩn đoán đáng tin:

- **Không gọi `toString()` tùy ý trên receiver/argument của người dùng**: phương thức có thể tốn thời gian, truy cập dữ liệu riêng tư, ném ngoại lệ hoặc có tác dụng phụ. Ghi loại của giá trị khi cần (`obj == null ? "<null>" : obj.getClass().getName()`).
- Không log token, mật khẩu, payload request, object graph, stack của dữ liệu nhạy cảm hoặc đường dẫn nội bộ quá chi tiết ở kênh công khai. Phân mức log và giới hạn quyền truy cập.
- Lấy **định danh loader tương đối trong một tiến trình**, cùng module và package để so sánh; không coi identity hash là ID ổn định qua restart.
- Ghi rõ **chữ ký đã yêu cầu** và **chữ ký thực tế** nếu tìm được. Đối với overload, thiếu một tham số hoặc sai thứ tự đều có ý nghĩa.
- Xác định vị trí lỗi: tra lớp → tra thành viên → kiểm tra quyền → chuyển đổi đối số → thực thi phương thức → chuyển kiểu kết quả. Bước khác nhau cho cùng tên ngoại lệ sẽ cần cách xử lý khác nhau.
- Nếu dùng `Thread.currentThread().getContextClassLoader()` để tìm plugin/resource, ghi cả context loader và loader định nghĩa class được trả về. Hai loader không bắt buộc giống nhau.
- Với lỗi xảy ra trong luồng đa tiến trình, đính kèm trace/correlation ID từ ứng dụng để kết nối các sự kiện; log một lần tại ranh giới được quản lý, tránh đổ trùng stack trace nhiều tầng.
- Không mặc định thêm `--add-opens` hoặc bật `setAccessible(true)` chỉ để làm biến mất lỗi; trước tiên xác định module/package và vì sao thao tác cần quyền truy cập đó.

### Mẫu dữ liệu log có cấu trúc (minh họa)

```text
event=reflection_lookup_failed
operation=getDeclaredMethod
targetClass=com.example.plugins.PaymentAdapter
targetModule=<unnamed>
targetLoaderKind=PluginClassLoader
requestedMethod=calculate(java.math.BigDecimal,int):java.math.BigDecimal
exceptionType=java.lang.NoSuchMethodException
correlationId=<id-cua-ung-dung>
```

Đây là **mẫu minh họa cấu trúc**, không phải output của chương trình Java phía trên; `PluginClassLoader`/`PaymentAdapter`/`correlationId` chỉ là giá trị ví dụ. Không tự giả định chi tiết loader, module hay chữ ký như các sự kiện đã đo được.

## 9. Quy trình kiểm tra nhanh trong thực tế

1. Ghi `operation`, loại exception và cause/stack trace gốc.
2. Xác định tên binary và `Class<?>` **thực tế** của target/receiver/argument.
3. Ghi defining loader (xử lý giá trị `null` của bootstrap) và module/package cho cả target lẫn caller khi cần.
4. Đối chiếu chữ ký yêu cầu và metadata trả về: overload, runtime erasure, primitive/wrapper, receiver, bridge/synthetic.
5. Phân biệt *không tìm thấy*, *không được phép*, *đối số sai* và *thân phương thức ném ngoại lệ*.
6. So artifact/JAR, dependency, class path/module path và cấu hình opens/exports khi lỗi chỉ xuất hiện ở một môi trường.
7. Sửa đúng nguyên nhân, tái hiện bằng test hoặc đoạn Java nhỏ cùng phiên bản JDK và loader/module setup tương ứng.

## 10. Tự kiểm tra

**Câu 1.** Hai `Class` có cùng `getName()` luôn là cùng một kiểu không?

**Đáp án:** Không. Nếu chúng do hai defining class loader khác nhau định nghĩa, có thể là hai kiểu khác nhau; so trực tiếp `first == second` và kiểm tra loader.

**Câu 2.** Vì sao `getDeclaredMethod("run", Integer.class)` có thể thất bại khi source khai báo `run(int)`?

**Đáp án:** Reflection so kiểu tham số chính xác ở runtime; `Integer.class` khác `int.class`.

**Câu 3.** `getDeclaredMethod` trả về `Method` nhưng `invoke` vẫn ném `IllegalAccessException`. Mâu thuẫn không?

**Đáp án:** Không. Tra được metadata chưa đồng nghĩa với được phép gọi; cần kiểm tra visibility, caller và quy tắc module.

**Câu 4.** Trong `InvocationTargetException` cần xem ngoại lệ nào để biết code đích hỏng?

**Đáp án:** Xem `getTargetException()` (hoặc `getCause()`) cùng stack trace của ngoại lệ gốc.

**Câu 5.** Khi class ở unnamed module, `getModule().getName()` có giá trị gì?

**Đáp án:** `null`. Ghi nhãn `<unnamed>` hoặc `isNamed() == false` để tránh hiểu sai.

**Ghi nhớ:** Lỗi reflection cần được điều tra theo bộ **class + defining loader + module/package + declaring member + chữ ký runtime + caller/receiver + exception/cause**. Thiếu một trong các yếu tố đó có thể khiến hai trường hợp khác nguyên nhân trông giống nhau trong log.

Tham khảo API Java 17: [`Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html), [`Method`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html), [`AccessibleObject`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html), [`Module`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html).

</details>

- [Quay lại đầu trang](#back-to-top)
