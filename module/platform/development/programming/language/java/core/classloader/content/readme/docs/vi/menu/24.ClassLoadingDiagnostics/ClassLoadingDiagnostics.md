<a id="back-to-top"></a>

# Chẩn đoán

## Menu
- [In tên lớp, loader, module và nguồn mã trong thông tin lỗi](#class-loading-diagnostics-001)
- [Lần theo parent chain và loader thực sự định nghĩa lớp](#class-loading-diagnostics-002)
- [Xác định lớp đến từ thư mục, JAR hay runtime image nào](#class-loading-diagnostics-003)
- [Liệt kê tài nguyên trùng tên và xác định thứ tự được chọn](#class-loading-diagnostics-004)
- [-verbose:class và unified logging -Xlog:class+load](#class-loading-diagnostics-005)
- [Theo dõi khởi tạo và dỡ lớp bằng các log tag phù hợp](#class-loading-diagnostics-006)
- [jcmd VM.classloaders, VM.classloader_stats và VM.class_hierarchy](#class-loading-diagnostics-007)
- [ClassLoadingMXBean: tổng lớp đã nạp, lớp hiện có và lớp đã dỡ](#class-loading-diagnostics-008)
- [JFR, heap dump và đường tham chiếu giữ class loader](#class-loading-diagnostics-009)
- [javap, jar và jdeps trong điều tra bytecode, JAR và phụ thuộc](#class-loading-diagnostics-010)
- [Đối chiếu runtime classpath thực tế với cây dependency của công cụ build](#class-loading-diagnostics-011)

## <a id="class-loading-diagnostics-001">In tên lớp, loader, module và nguồn mã trong thông tin lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# In tên lớp, loader, module và nguồn mã trong thông tin lỗi Java

Khi ứng dụng Java có nhiều `ClassLoader` — plugin, application server, test runner, service provider hoặc module layer — chỉ in `ClassNotFoundException: com.example.Plugin` thường **không đủ để chẩn đoán**. Tên lớp đúng không có nghĩa JVM đã dùng **đúng định nghĩa class**. Hai loaders có thể định nghĩa hai runtime types trùng tên; một JAR cũ của parent có thể thắng JAR mới của plugin; một class thuộc named module có thể chịu ràng buộc exports/opens. Vì thế, thông tin lỗi hữu ích nên ghi được ít nhất:

1. **Tên class được yêu cầu hoặc class thực sự liên quan** (`Class.getName()`).
2. **Defining loader** (`Class.getClassLoader()`): object loader nào đã định nghĩa runtime class, với tên và loại loader để phân biệt.
3. **Module thực tế** (`Class.getModule()`): module có tên hay unnamed module và layer nếu cần.
4. **Nguồn mã (code source)** (`Class.getProtectionDomain().getCodeSource().getLocation()`), khi được cung cấp.
5. **Loại lỗi, cause chain, stacktrace, hành động đang thực hiện và loader được dùng để tra cứu**; phân biệt *lookup loader* với *defining loader*.
6. **Ngữ cảnh phiên bản và triển khai**: plugin ID/generation, expected artifact/version, Java runtime version và request/correlation ID, không làm lộ secrets.

Bài Knowledge này thuộc **Java core – classloader / Chẩn đoán**, tập trung vào chủ đề **“In tên lớp, loader, module và nguồn mã trong thông tin lỗi”** trên Java 17+. Các ví dụ đi từ log metadata tối thiểu đến xung đột hai class loaders, lỗi lookup và lỗi initialization; mỗi chương trình chạy độc lập có lệnh biên dịch/chạy và stdout dự kiến.

## 1. Một ví dụ vấn đề: cùng class name nhưng khác ý nghĩa

Giả sử:

```text
Host application classpath
  com.example.api.Widget   (loader = app)

Plugin A
  com.example.impl.Parser  (loader = plugin-A)
  com.example.lib.Json     (v1, loader = plugin-A)

Plugin B
  com.example.impl.Parser  (loader = plugin-B)
  com.example.lib.Json     (v2, loader = plugin-B)
```

Nếu hai plugin tự nạp `com.example.impl.Parser`, `ParserA.getName()` và `ParserB.getName()` giống nhau, nhưng:

```java
System.out.println(ParserA == ParserB);
```

Đây là **snippet** cần hai biến `Class<?> ParserA` và `Class<?> ParserB`. Kết quả có thể là `false`, bởi JVM xác định runtime class identity bằng **binary name + defining class loader**.

Thông điệp:

```text
java.lang.ClassCastException: class com.example.impl.Parser ...
```

chưa giúp trả lời được “class này **do loader nào định nghĩa** và lấy bytes từ artifact nào?”. Nếu kèm defining loader, module và code source, ta có thể xác định class phiên bản cũ/tầng plugin sai mà không suy đoán từ source tree.

**Nguyên tắc:** chẩn đoán class loader dựa trên **runtime `Class<?>` object** và những metadata có thật, không chỉ trên string tên class, đường dẫn dự đoán hay cấu hình Maven/Gradle.

## 2. Bốn API lấy metadata class quan trọng nhất

| Điều cần in | API Java 17 | Cần diễn giải đúng |
| --- | --- | --- |
| Tên runtime class | `type.getName()` | Binary name cho class thông thường; array/primitive/hidden class có quy tắc tên riêng |
| Defining loader | `type.getClassLoader()` | `null` thường chỉ bootstrap loader, và luôn `null` cho primitive/void types |
| Module | `type.getModule()`; `module.getName()` | `null` từ `getName()` là unnamed module, không phải không có Module object |
| Nguồn mã | `type.getProtectionDomain().getCodeSource().getLocation()` | Có thể không có `CodeSource` hoặc `location`; URL không phải bằng chứng tuyệt đối về nội dung bytes |

Các thông tin bổ sung thường hữu ích:

- `type.getPackageName()`: tên package theo Java runtime type; **cùng package name không nhất thiết cùng runtime package** nếu defining loaders khác nhau.
- `type.getClassLoader() == expectedLoader`: kiểm tra identity bằng object reference khi điều tra.
- `loader.getClass().getName()`: implementation class của loader, chẳng hạn built-in/custom loader.
- `loader.getName()` (Java 9+): tên mô tả loader nếu nó được đặt; có thể `null`.
- `System.identityHashCode(loader)`: định danh tham chiếu hỗ trợ phân biệt hai instances trong **một JVM đang chạy**, không phải ID ổn định giữa những lần khởi động.
- `module.getDescriptor()` hoặc `module.getLayer()`: khi cần truy vết named module/JPMS layers.
- `StackTraceElement.getClassLoaderName()`, `getModuleName()`, `getModuleVersion()`: metadata của frame (có thể `null`), nhưng không tự cung cấp `Class<?>` hay code source.

**Tránh chỉ log `loader.toString()`.** Nhiều loaders có `toString` hữu ích, nhưng đây không phải contract đảm bảo có URL/source/version. Với custom loader, `toString()` thậm chí có thể quá dài hoặc chứa thông tin nội bộ không nên đưa lên log công khai.

## 3. `Class.getName()`: tên lớp nào cần in?

`Class.getName()` khác với tên file và một số tên hiển thị:

| API/định dạng | Ví dụ | Khi nào dùng |
| --- | --- | --- |
| `type.getName()` | `com.example.MyService` | Log chính xác runtime type thông thường |
| `type.getSimpleName()` | `MyService` | Dễ đọc, nhưng thiếu package và có thể mơ hồ |
| `type.getCanonicalName()` | `com.example.Outer.Inner` | Human-facing code name; có thể `null` cho local/anonymous/hidden |
| Binary name nested class | `com.example.Outer$Inner` | `Class.forName` và JVM lookup |
| Relative class-file path | `com/example/MyService.class` | Kiểm tra JAR entries/classpath |
| Descriptor | `Lcom/example/MyService;` | Bytecode/JVMS reference |
| Array class `getName()` | `[Ljava.lang.String;` | Không phải tên source-style `String[]` |

Đối với error context, ưu tiên **`type.getName()`** vì nó không làm mất package/nested-class identifier. Nếu class chưa nạp thành công, chưa có `Class<?>` để gọi method; hãy log **binary name đã yêu cầu** thay vì tạo một giá trị class giả.

**Ví dụ thiếu class**:

```java
String requested = "com.example.Missing";
try {
    Class<?> found = lookupLoader.loadClass(requested);
    // Có Class<?> mới đọc được defining loader/module/code source.
} catch (ClassNotFoundException error) {
    // Log requested và chính lookupLoader;
    // không tự khẳng định một defining loader không tồn tại.
}
```

Đây là **snippet**, cần biến `lookupLoader` là `ClassLoader` và method có xử lý exceptions.

## 4. Defining loader khác lookup loader và context loader

Một cuộc gọi:

```java
Class<?> type = child.loadClass("api.Shared");
```

không bảo đảm `type.getClassLoader() == child`. Với parent-first, `child` có thể ủy quyền parent và nhận runtime class do parent định nghĩa.

Ba vai trò:

1. **Requested/lookup loader:** object mà code gọi `loadClass` hoặc truyền vào `Class.forName(name, ..., loader)`.
2. **Defining loader:** `type.getClassLoader()` của runtime class thực tế, có thể là parent hoặc ancestor.
3. **Thread context class loader (TCCL):** `Thread.currentThread().getContextClassLoader()`; thường ảnh hưởng framework/SPI discovery (`ServiceLoader.load(service)`), không mặc định đồng nhất với defining loader.

```text
thread TCCL = plugin-B
         │
         └── ServiceLoader chọn service provider resources

requestedLoader = child-B
         │
         └── child-B.loadClass("api.Contract")
                 │
                 └── parent app defines api.Contract

type.getClassLoader() == app
```

Khi log `ClassNotFoundException`, ghi lookup loader và TCCL nếu có ý nghĩa; khi log `ClassCastException` hoặc `LinkageError`, ghi **defining loaders của cả hai runtime types** nếu caller đang so sánh chúng.

**Không nhầm `loader.getParent()` với `type.getClassLoader()`**: parent là quan hệ object giữa loaders; defining loader cho biết ai đã định nghĩa class cụ thể.

## 5. `getClassLoader() == null` không tự là lỗi

Trong nhiều JVM implementations, một class do bootstrap loader định nghĩa có:

```java
ClassLoader loader = String.class.getClassLoader();
System.out.println(loader == null);
```

Đây là snippet runnable nếu đặt trong một method; kết quả trên JDK 17 thông dụng là `true`. `null` ở đây là cách API đại diện bootstrap loader **không có object Java public để trả về**, không phải bug hoặc class chưa nạp.

Với `int.class` hoặc `void.class`, `getClassLoader()` cũng trả `null`; phân biệt bằng `type.isPrimitive()`. Array classes không có class-file định nghĩa bằng `defineClass` như class thường; `getClassLoader()` của array class phản ánh loader của element type hoặc `null` cho primitive components.

Gợi ý formatter:

```java
static String loaderLabel(Class<?> type) {
    ClassLoader loader = type.getClassLoader();
    if (type.isPrimitive()) {
        return "<primitive>";
    }
    if (loader == null) {
        return "<bootstrap>";
    }
    String name = loader.getName();
    return name == null
            ? loader.getClass().getName()
            : name + " (" + loader.getClass().getName() + ")";
}
```

Đây là method minh họa; không tự chạy nếu chưa nằm trong một class có caller. Trong logger production, cân nhắc in thêm **identity hash** của loader instance vì hai loaders cùng tên/cùng Java implementation class vẫn có thể định nghĩa hai class khác nhau.

## 6. Module: named hay unnamed, và tại sao `getName()` có thể null?

`Class.getModule()` trả đối tượng `Module` tương ứng với class; với classes trên classpath hoặc classes do `URLClassLoader` tạo như classpath-style, module thường là **unnamed module** của defining loader.

```java
Module module = type.getModule();
String moduleName = module.isNamed()
        ? module.getName()
        : "<unnamed>";
```

Đây là **snippet**, cần biến `type` kiểu `Class<?>`.

Ví dụ trên Java 17:

- `String.class.getModule().getName()`: `"java.base"`.
- `MyClasspathClass.class.getModule().isNamed()`: `false` nếu class chạy trên application classpath.
- Một class thuộc named module `org.example.plugin` trả tên module ấy, có thể có module version trong descriptor.
- Với named module layer tự xây, cùng module name ở hai layers **không tự bảo đảm cùng `Class<?>` identity**; cần xem `Module` object, layer và loader phù hợp.

**Không suy luận `module.getName() == null` là “module lookup thất bại”**. Đó là tên của unnamed module, một khái niệm bình thường trong JPMS.

Với `ClassCastException` hoặc `IllegalAccessError`, module metadata giúp xem nhanh liệu vấn đề là duplicate types hay module access (`requires`/`exports`/`opens`). Nhưng chỉ in module name không thay thế phân tích runtime type identity.

## 7. Nguồn mã: `ProtectionDomain → CodeSource → URL`

API chuẩn:

```java
ProtectionDomain domain = type.getProtectionDomain();
CodeSource codeSource =
        domain == null ? null : domain.getCodeSource();
URL location = codeSource == null
        ? null : codeSource.getLocation();
```

Đây là **snippet**, cần import `java.security.ProtectionDomain`, `java.security.CodeSource`, `java.net.URL` và biến `type`.

Ý nghĩa:

- `ProtectionDomain` gắn metadata về domain của runtime class.
- `CodeSource` có thể mô tả **URL nguồn và signer certificates** liên quan.
- `CodeSource.getLocation()` là URL, thường là **`file:/.../app.jar`** hoặc **`file:/.../classes/`** cho classpath classes.
- `null` là giá trị **hợp lệ** nếu không có CodeSource hoặc location; nhiều platform/JDK classes không có source URL kiểu JAR/đường dẫn filesystem.
- `Class.getProtectionDomain()` có thể ném `SecurityException` trên runtime có security checks phù hợp (đặc biệt với mô hình cũ); logger nên xử lý theo chính sách fail-safe.

**Đừng gọi `type.getProtectionDomain().getCodeSource().getLocation().toString()` không kiểm tra null**. Một `NullPointerException` phát sinh từ logger sẽ che lỗi gốc đang điều tra.

**CodeSource location không phải một bằng chứng mật mã tuyệt đối.** URL có thể là vị trí JAR/directory đã dùng khi định nghĩa class, nhưng không chứng minh bytes hiện tại trên đĩa là bytes đã được load, không chứng minh artifact chưa bị thay đổi, và không nói class đang có đúng build version nếu không kết hợp hashes/manifest/versioning.

## 8. CodeSource khác class resource URL và JAR manifest

Một số lập trình viên thử:

```java
URL location = type.getResource(
        type.getSimpleName() + ".class");
```

Đây là snippet có thể hữu ích để **tìm resource tương ứng tên class** trong một số trường hợp, nhưng **không tương đương `ProtectionDomain.CodeSource`**:

- `Class.getResource` phụ thuộc loader/module resource lookup, accessibility và resource-name rules.
- `getResource(...)` có thể trả URL `jrt:/...` cho class thuộc JDK runtime image; đó là resource URL, không nhất thiết `CodeSource.getLocation()`.
- Resource có thể không tìm được, bị module encapsulation che hoặc không phải bytes dùng cho runtime definition.
- Đối với dynamically generated class/hidden class, có thể **không tồn tại file `.class` truy cập bằng URL**.
- Một custom loader có thể define từ mảng byte trong memory, database hoặc network mà không đặt CodeSource; `location == null` là điều hoàn toàn hợp lệ.
- Resource URL và CodeSource URL có thể có độ chi tiết khác nhau: một trỏ đúng `some/Type.class`, một trỏ **JAR/directory gốc**.

Nếu cần xác định class có thật trong JAR nào, **ưu tiên CodeSource location nếu có** và đối chiếu với class-loading logs/artifact inventory. Nếu cần chứng minh bytecode từ artifact nào, thiết kế loader có thể log **định danh build hoặc SHA-256 digest của artifact đáng tin đã xác minh** — không chỉ tên file.

## 9. Thực hành A — in đủ class name, loader, module và CodeSource

Ví dụ đầu tiên là chương trình **hoàn chỉnh Java 17+** trên application classpath. Chúng ta in metadata của chính class demo và `java.lang.String`.

Cấu trúc:

```text
diagnostics-lab/
└── src/demo/DiagnosticDemo.java
```

Tệp **`src/demo/DiagnosticDemo.java`**:

```java
package demo;

import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public final class DiagnosticDemo {
    private static String loaderOf(Class<?> type) {
        ClassLoader loader = type.getClassLoader();

        if (loader == null) {
            return type.isPrimitive() ? "<primitive>" : "<bootstrap>";
        }
        if (loader == DiagnosticDemo.class.getClassLoader()) {
            return "application";
        }
        return loader.getName() == null
                ? loader.getClass().getName() : loader.getName();
    }

    private static String moduleOf(Class<?> type) {
        Module module = type.getModule();
        return module.isNamed() ? module.getName() : "<unnamed>";
    }

    private static String sourceSchemeOf(Class<?> type) {
        try {
            ProtectionDomain domain = type.getProtectionDomain();
            CodeSource source =
                    domain == null ? null : domain.getCodeSource();
            URL location =
                    source == null ? null : source.getLocation();
            return location == null
                    ? "<unavailable>" : location.getProtocol();
        } catch (SecurityException denied) {
            return "<denied>";
        }
    }

    private static void describe(Class<?> type) {
        System.out.println("class=" + type.getName());
        System.out.println("loader=" + loaderOf(type));
        System.out.println("module=" + moduleOf(type));
        System.out.println("code-source scheme=" +
                sourceSchemeOf(type));
    }

    public static void main(String[] args) {
        describe(DiagnosticDemo.class);
        describe(String.class);
    }
}
```

Biên dịch và chạy trong `diagnostics-lab`:

```sh
javac --release 17 -d out src/demo/DiagnosticDemo.java
java -cp out demo.DiagnosticDemo
```

**Stdout dự kiến trên OpenJDK/HotSpot Java 17 trong cấu hình classpath này:**

```text
class=demo.DiagnosticDemo
loader=application
module=<unnamed>
code-source scheme=file
class=java.lang.String
loader=<bootstrap>
module=java.base
code-source scheme=<unavailable>
```

Chúng ta cố ý chỉ in **URL scheme** thay vì full filesystem URL để stdout không phụ thuộc thư mục đặt project và không rò đường dẫn người dùng.

### Giải thích

- `DiagnosticDemo.class` được application loader định nghĩa từ thư mục `out` trên classpath; nó thuộc unnamed module. CodeSource thường chứa một `file:` URL của thư mục `out`.
- `String.class` được bootstrap định nghĩa trên JDK 17 thông dụng và thuộc named module `java.base`. CodeSource location thường không được cung cấp ở metadata này; **không viết code bắt buộc nó phải tồn tại**.
- `loader=application` là nhãn do **chính ví dụ quy ước** bằng cách so object với loader của `DiagnosticDemo`, không phải cam kết rằng Java luôn đặt tên loader là chữ `application`.
- Nếu chạy dưới custom launcher/security configuration, labels hoặc khả năng truy cập CodeSource có thể khác; demo vẫn xử lý `null` và `SecurityException`.
- Trong log thật, có thể cần ghi **URL đầy đủ** theo policy nội bộ để xác định artifact; ví dụ chỉ in scheme để giữ expected stdout ổn định.

**Điểm phân biệt:** `Class.getProtectionDomain()` đọc metadata, không chạy constructor hay static initializer của target. Việc có `Class<?>` cũng không có nghĩa object của class đã được tạo.

## 10. Ghi full CodeSource URL nhưng phải kiểm soát bảo mật log

Một helper phù hợp cho logging:

```java
static String codeSourceUrl(Class<?> type) {
    try {
        var domain = type.getProtectionDomain();
        if (domain == null || domain.getCodeSource() == null) {
            return "<unknown>";
        }
        var url = domain.getCodeSource().getLocation();
        return url == null ? "<unknown>" : url.toExternalForm();
    } catch (SecurityException denied) {
        return "<access-denied>";
    }
}
```

Đây là **method minh họa**, cần nằm trong class, không phải chương trình độc lập. Nó giữ đầy đủ URL khi được phép, nhưng **không đảm bảo URL thích hợp để xuất ra log công khai**.

Một số URL có thể chứa:

- username hoặc thông tin đường dẫn home directory;
- cấu trúc thư mục triển khai nội bộ;
- tên khách hàng/tenant hoặc môi trường riêng;
- query/fragment có token trong những custom URL schemes;
- network host và endpoint nội bộ.

**Quy tắc tốt hơn:** logger nội bộ có thể giữ canonical artifact ID/version/digest, còn public-facing error chỉ dùng mã lỗi/correlation ID. Nếu buộc log URL, hãy chuẩn hóa/sanitize và redact thông tin nhạy cảm theo policy của hệ thống. Tránh dùng URL đến endpoint không tin cậy như một nơi tự động đọc/download artifact chỉ để chẩn đoán.

### “Nguồn mã” không phải lúc nào cũng là một file

Class có thể đến từ:

- một **JAR** trên classpath/URLClassLoader;
- một **directory** chứa class files;
- **JDK runtime image** (`jrt:`), với CodeSource có thể unavailable;
- một **custom ClassLoader** sinh bytecode trong bộ nhớ;
- một **module layer** không ánh xạ đơn giản về URL filesystem;
- bytecode instrumentation hoặc runtime enhancement.

Vậy nên `CodeSource == null` nên được log là **`<unknown>`** hoặc **`<unavailable>`**, không phải tự kết luận “đó chắc chắn là class độc hại/không có JAR”.

## 11. In loader theo identity, không chỉ tên

Hai `URLClassLoader` instances có thể cùng class implementation, cùng URLs và thậm chí cùng tên, nhưng vẫn là **hai defining loaders khác nhau**.

Một nhãn hữu ích cho session:

```java
static String loaderDetail(ClassLoader loader) {
    if (loader == null) {
        return "<bootstrap>";
    }
    String name = loader.getName();
    return loader.getClass().getName()
            + "[name=" + (name == null ? "<unnamed>" : name)
            + ", id=0x"
            + Integer.toHexString(System.identityHashCode(loader))
            + "]";
}
```

Đây là helper minh họa; nên cẩn thận với việc tên loader tự cấu hình có thể chứa thông tin đầu vào không tin cậy.

**Vì sao identity hash chứ không chỉ `getName()`?**

- `getName()` có thể null; custom loaders có thể có nhãn trùng.
- `loader.getClass().getName()` thường giống nhau cho mọi instances của cùng subclass.
- `System.identityHashCode(loader)` giúp phân biệt objects trong **một lần chạy** dù `equals` bị override.
- Nhưng identity hash chỉ là số hash 32-bit: **có thể collision**, không phải globally unique ID, không dùng làm authentication token, không nhất quán sau restart.

Đối với hệ plugin sản xuất, tốt hơn là chủ động gán **pluginId + generation/version UUID** tại nơi tạo loader và log cùng identity hash như dữ liệu chẩn đoán phụ trợ.

## 12. Thực hành B — hai URLClassLoader định nghĩa cùng tên nhưng khác runtime type

Ví dụ này cho thấy tại sao không đủ chỉ log class name. Ta compile một class `plugin.Widget`, đặt bytes ở thư mục riêng, sau đó tạo hai **named URLClassLoader instances** dùng chung URL, chung parent nhưng độc lập class identity.

Cấu trúc:

```text
duplicate-loader-lab/
├── src/plugin/plugin/Widget.java
├── src/host/demo/DuplicateLoaderDemo.java
├── plugin-out/
└── host-out/
```

**`src/plugin/plugin/Widget.java`:**

```java
package plugin;

public final class Widget {
    public Widget() {
    }

    public String label() {
        return "plugin widget";
    }
}
```

**`src/host/demo/DuplicateLoaderDemo.java`:**

```java
package demo;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class DuplicateLoaderDemo {
    public static void main(String[] args) throws Exception {
        URL pluginRoot = Path.of("plugin-out")
                .toAbsolutePath().toUri().toURL();
        URL[] urls = { pluginRoot };
        ClassLoader platform = ClassLoader.getPlatformClassLoader();

        try (URLClassLoader a =
                     new URLClassLoader("plugin-A", urls, platform);
             URLClassLoader b =
                     new URLClassLoader("plugin-B", urls, platform)) {

            Class<?> left = a.loadClass("plugin.Widget");
            Class<?> right = b.loadClass("plugin.Widget");
            Object rightObject =
                    right.getDeclaredConstructor().newInstance();

            System.out.println("same name=" +
                    left.getName().equals(right.getName()));
            System.out.println("same runtime Class=" + (left == right));
            System.out.println("left loader name=" +
                    left.getClassLoader().getName());
            System.out.println("right loader name=" +
                    right.getClassLoader().getName());
            System.out.println("left module named=" +
                    left.getModule().isNamed());
            System.out.println("left source scheme=" +
                    left.getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .getProtocol());

            try {
                left.cast(rightObject);
                System.out.println("unexpected cast success");
            } catch (ClassCastException mismatch) {
                System.out.println("cast error=" +
                        mismatch.getClass().getSimpleName());
            }
        }
    }
}
```

**Biên dịch** từ `duplicate-loader-lab` bằng JDK 17+:

```sh
javac --release 17 -d plugin-out src/plugin/plugin/Widget.java
javac --release 17 -d host-out src/host/demo/DuplicateLoaderDemo.java
```

**Chạy** (chú ý: chỉ `host-out` trên app classpath):

```sh
java -cp host-out demo.DuplicateLoaderDemo
```

**Stdout trên OpenJDK/HotSpot Java 17 với URL thư mục như trên:**

```text
same name=true
same runtime Class=false
left loader name=plugin-A
right loader name=plugin-B
left module named=false
left source scheme=file
cast error=ClassCastException
```

**Phân tích:**

1. Cả hai loaders được cấp **cùng `plugin-out` URL**, nhưng mỗi instance tự định nghĩa `plugin.Widget`.
2. `left.getName().equals(right.getName())` trả true: binary names giống nhau.
3. `left == right` trả false: defining loaders là hai object khác nhau.
4. `getName()` của named `URLClassLoader` được đặt từ constructor Java 9+, giúp phân biệt `plugin-A`/`plugin-B`.
5. Hai class thuộc unnamed module **riêng theo defining loader**. `isNamed()` trả false cho mỗi bên.
6. `CodeSource` của class do URLClassLoader lấy từ directory `plugin-out` thường có URL scheme `file`.
7. `Class.cast` kiểm tra runtime assignability, nên object được tạo từ `right` không phải instance của runtime type `left`; `ClassCastException` là đúng dù cả hai có binary name giống nhau.

**Lưu ý API:** demo trực tiếp đọc `getCodeSource().getLocation()` để rút gọn vì nguồn là `URLClassLoader` từ `file:` URL có kiểm soát. Trong mã logging tổng quát, **phải dùng helper null-safe** như phần 7–10, vì nhiều classes hoàn toàn hợp lệ không có CodeSource.

Không thêm `plugin-out` vào `-cp` của host nếu muốn quan sát hai defining loaders riêng. Nếu parent application loader đã định nghĩa `plugin.Widget` và parent-first trả về, hai children có thể cùng dùng bản parent khiến kết quả đổi.

## 13. `ClassCastException`: cần log cả hai phía

Một thông điệp kiểu:

```text
ClassCastException: plugin.Widget cannot be cast to plugin.Widget
```

thoạt nhìn nghịch lý nhưng có thể xuất phát từ **hai `Class<?>` khác identity**. Tại runtime, nên ghi:

- `expectedType.getName()`, defining loader, module và CodeSource của type mà host kỳ vọng;
- `actualObject.getClass().getName()`, defining loader, module và CodeSource của object thực;
- class loader dùng để **nạp provider/plugin**, TCCL nếu framework dùng nó;
- artifact ID/version của cả hai;
- toàn bộ stacktrace/cause, nhưng không đưa đường dẫn nhạy cảm vào client response.

Pseudocode:

```java
Class<?> actual = value.getClass();
Class<?> expected = expectedApiClass;

System.err.println("expected=" + expected.getName());
System.err.println("actual=" + actual.getName());
System.err.println("same Class=" + (actual == expected));
System.err.println("assignable=" +
        expected.isAssignableFrom(actual));
// Log thêm loaderDetail và codeSourceUrl cho cả hai.
```

Đây là **snippet** cần biến `value` và `expectedApiClass`. `isAssignableFrom` tốt hơn chỉ so tên, nhất là với interfaces. Đối với một implementation hợp lệ, `actual == expected` thường false (implementation khác interface), nhưng `expected.isAssignableFrom(actual)` phải true.

**Đừng chỉ in `actual.getClassLoader()` rồi kết luận đủ.** Một cast thất bại có ít nhất hai types liên quan: expected và actual. Cần thông tin của **cả hai**.

## 14. `ClassNotFoundException`: class chưa tồn tại nên không thể in defining loader

Khi `ClassLoader.loadClass("plugins.Missing")` ném `ClassNotFoundException`, không có `Class<?>` kết quả để gọi `getModule()` hoặc `getProtectionDomain()`.

Thay vào đó, ghi:

```text
phase=LOAD_CLASS
requested=plugins.Missing
lookupLoader=plugin-loader-7
threadContextLoader=...
parentChain=...
artifactCandidates=...
error=ClassNotFoundException
cause=...
```

Đây là **schema gợi ý**, không phải stdout đã chạy.

Các câu hỏi:

1. `requested` có phải **binary name** đúng không? Với nested class dùng `Outer$Inner`, không dùng `Outer.Inner` trong `loadClass`.
2. Loader được truyền vào `Class.forName` có đúng không?
3. Nếu framework dùng `ServiceLoader`, TCCL có trỏ loader khám phá provider đúng không?
4. URL roots/JAR entries thực sự có `plugins/Missing.class` không?
5. Có package/module encapsulation hoặc loader policy ngăn lookup không?
6. Lỗi có phải thiếu **dependency gián tiếp** dẫn tới `NoClassDefFoundError` thay vì trực tiếp missing requested class?

**Không tự phát lệnh `Class.forName` để “lấy thêm metadata” cho mọi tên không tìm được**, vì có thể gây loading/initialization side effects, làm thay đổi trạng thái chẩn đoán hoặc lặp lại lỗi.

## 15. Thực hành C — báo requested class khi lookup fail, và metadata khi initialization fail

Cấu trúc:

```text
error-context-lab/
└── src/demo/ErrorContextDemo.java
```

**`src/demo/ErrorContextDemo.java`:**

```java
package demo;

import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public final class ErrorContextDemo {
    static final class Broken {
        static int value = initialize();

        private static int initialize() {
            throw new IllegalStateException("bad configuration");
        }
    }

    private static String codeSourceScheme(Class<?> type) {
        ProtectionDomain domain = type.getProtectionDomain();
        CodeSource codeSource =
                domain == null ? null : domain.getCodeSource();
        URL location = codeSource == null
                ? null : codeSource.getLocation();
        return location == null
                ? "<unavailable>" : location.getProtocol();
    }

    public static void main(String[] args) {
        String missing = "demo.NotPresent";
        ClassLoader lookup =
                ErrorContextDemo.class.getClassLoader();

        try {
            lookup.loadClass(missing);
        } catch (ClassNotFoundException failure) {
            System.out.println("requested=" + missing);
            System.out.println("lookup was app=" +
                    (lookup == ErrorContextDemo.class.getClassLoader()));
            System.out.println("lookup error=" +
                    failure.getClass().getSimpleName());
        }

        try {
            System.out.println(Broken.value);
        } catch (ExceptionInInitializerError failure) {
            Class<?> target = Broken.class;

            System.out.println("failing type=" + target.getName());
            System.out.println("defined by app=" +
                    (target.getClassLoader() == lookup));
            System.out.println("module=" +
                    (target.getModule().isNamed()
                            ? target.getModule().getName()
                            : "<unnamed>"));
            System.out.println("source scheme=" +
                    codeSourceScheme(target));
            System.out.println("init error=" +
                    failure.getClass().getSimpleName());
            System.out.println("root cause=" +
                    failure.getCause().getClass().getSimpleName());
        }
    }
}
```

Biên dịch/chạy bằng JDK 17+ từ `error-context-lab`:

```sh
javac --release 17 -d out src/demo/ErrorContextDemo.java
java -cp out demo.ErrorContextDemo
```

**Stdout trong Java 17/HotSpot thông thường:**

```text
requested=demo.NotPresent
lookup was app=true
lookup error=ClassNotFoundException
failing type=demo.ErrorContextDemo$Broken
defined by app=true
module=<unnamed>
source scheme=file
init error=ExceptionInInitializerError
root cause=IllegalStateException
```

Giải thích:

- Ở nhánh `ClassNotFoundException`, chỉ có **binary name yêu cầu** và **lookup loader**; không hề có class result để gán nhầm module/CodeSource.
- `Broken.value` là static field **không phải compile-time constant**; việc truy cập kích hoạt initialization, `initialize()` ném `IllegalStateException`, JVM báo `ExceptionInInitializerError`.
- Trong catch, `Broken.class` cho phép lấy metadata của **class đã được định nghĩa**, dù initialization thất bại. Class có thể ở trạng thái **erroneous**, nhưng metadata `Class<?>` vẫn có ý nghĩa.
- Runtime class `demo.ErrorContextDemo$Broken` được app loader định nghĩa từ thư mục `out`, thuộc unnamed module và có CodeSource scheme `file` theo cấu hình ví dụ.
- Log **nguyên nhân gốc** quan trọng hơn chỉ in top-level `ExceptionInInitializerError`; trong hệ thống thật cần giữ cả stacktrace/cause chain.

**Đừng suy rằng mọi `NoClassDefFoundError` là thiếu file.** Một class từng initialization thất bại có thể gây `NoClassDefFoundError` cho active uses sau. Điều tra cause và **sự kiện lỗi sớm nhất**.

## 16. Logger tốt phải giữ lỗi gốc, không được ném thêm lỗi

Đừng viết:

```java
try {
    service.run();
} catch (Throwable error) {
    System.err.println("source=" +
        service.getClass()
               .getProtectionDomain()
               .getCodeSource()
               .getLocation());
    // Vô tình NuIlPointerException ở logger → che root cause.
}
```

Đây là **mẫu không an toàn**, chưa có `service` và không phải chương trình runnable. `CodeSource` có thể null. Ngoài ra bắt `Throwable` tràn lan có thể che `VirtualMachineError` hoặc các lỗi JVM không thể phục hồi.

Thay vào đó:

1. **Chỉ catch những loại lỗi thuộc phạm vi handler**, giữ nguyên failure object.
2. Nếu có class thực, lấy metadata theo helper **null-safe**; nếu không có, log lookup context.
3. **Không gọi class constructor/initializer để lấy metadata**; thao tác chẩn đoán phải hạn chế side effects.
4. **Gắn throwable vào logger**: `logger.log(Level.SEVERE, "message", failure)` để giữ stacktrace/cause.
5. Metadata failures (ví dụ `SecurityException`) chỉ nên biến thành sentinel `<denied>` hoặc ghi chú phụ, không thay thế error chính.
6. Hạn chế kích thước log và sanitize strings chứa nguồn không tin cậy.

Ví dụ logging chính thống:

```java
private static final java.util.logging.Logger LOG =
        java.util.logging.Logger.getLogger("plugin.diagnostics");

static void reportFailure(
        String phase, String requested, ClassLoader lookup,
        Class<?> resolved, Throwable failure) {

    String type = resolved == null
            ? "<not-resolved>" : resolved.getName();

    LOG.log(java.util.logging.Level.SEVERE,
            "phase=" + phase
            + ", requested=" + requested
            + ", resolved=" + type
            + ", lookupLoader=" + String.valueOf(lookup),
            failure);
}
```

Đây là **helper minh họa**, chưa phải lớp chạy độc lập; triển khai thật thêm defining loader/module/code source từ `resolved` một cách null-safe. Nếu `resolved == null`, không giả metadata.

**Cẩn trọng:** `String.valueOf(lookup)` có thể gọi custom `toString()`. Khi xử lý logger security-sensitive, dùng định danh loader được quản lý riêng thay vì thực thi logic tùy ý trong `toString()`.

## 17. Những thông tin nên ghi vào một bản ghi lỗi class loading

Một bản ghi structured log có thể dùng các trường:

| Field | Ý nghĩa | Khi không có |
| --- | --- | --- |
| `event` | `CLASS_LOAD_FAILURE`, `LINKAGE_FAILURE`, `PROVIDER_FAILURE` | Bắt buộc có |
| `phase` | `LOOKUP`, `DEFINE`, `LINK`, `INITIALIZE`, `INVOKE` | Có thể `UNKNOWN` nếu caller không biết |
| `requested_class` | Binary name mà code đang yêu cầu | `<not-applicable>` |
| `resolved_class` | `Class.getName()` khi có `Class<?>` | `null`/omitted |
| `defining_loader_name` | `loader.getName()` hoặc label do framework cấp | `<unnamed>/<bootstrap>` |
| `defining_loader_type` | Java class name của loader | `<bootstrap>` |
| `defining_loader_instance` | Scoped ID/plugin generation/identity hash | `<unknown>` |
| `module_name` | Named module hoặc `<unnamed>` | `<not-resolved>` |
| `module_layer` | Named module layer khi hữu ích | `<none>`/omitted |
| `code_source` | URL/code artifact ID nếu có và được phép log | `<unavailable>/<denied>` |
| `lookup_loader` | Loader object ban đầu được hỏi | `<bootstrap>/<unknown>` |
| `tccl` | Thread context loader | Có thể `null` |
| `expected_artifact` | Version/SHA của plugin mong muốn | Omit nếu chưa biết |
| `actual_artifact` | Artifact từ CodeSource/loader mapping | `<unknown>` |
| `error_type` | `failure.getClass().getName()` | Bắt buộc có |
| `message` | Thông điệp lỗi đã được sanitize | Có thể trống |
| `cause_chain` | Causes, suppressed errors theo giới hạn | Có thể thiếu |
| `stacktrace` | Stacktrace được giữ trong logging backend | Có thể bị chính sách cắt |
| `correlation_id` | Nối request, job, plugin generation | Không tự bịa nếu chưa có |

Đây là **schema gợi ý**, không cần mọi hệ thống in đủ trường trên mỗi request. Mục tiêu là đủ thông tin để trả lời **“đã yêu cầu lớp nào, lớp thực tế do đâu định nghĩa, thuộc module nào và bytes có thể đến từ đâu?”**.

Với `ClassCastException`, cần **hai bộ metadata** cho expected/actual types. Với `ClassNotFoundException`, `resolved_class` không có. Với `ServiceConfigurationError`, xem cả service API identity và provider implementation được discovery từ loader/TCCL nào.

## 18. Stacktrace Java 9+ có module/loader, nhưng chưa đủ CodeSource

Java 9 bổ sung các trường trong `StackTraceElement`:

```java
String classLoaderName = frame.getClassLoaderName();
String moduleName = frame.getModuleName();
String moduleVersion = frame.getModuleVersion();
String className = frame.getClassName();
```

Đây là **snippet** cần `frame` kiểu `StackTraceElement`. Các trường loader/module/version có thể null nếu JVM không có metadata tương ứng; `getClassName()` là tên lớp theo frame chứ không phải object `Class<?>`.

Một stack trace có thể hiển thị prefix module/loader:

```text
at java.base/java.lang.Thread.run(Thread.java:...)
at plugin-A//plugin.Widget.run(Widget.java:...)
```

Đây là **ví dụ format minh họa**, không phải output cụ thể của chương trình ở phần 12. Stacktrace formatting có thể phụ thuộc runtime/module/loader names; đừng parse text này như giao thức ổn định.

Những giới hạn:

- Tên loader trong frame không phải object identity đảm bảo duy nhất.
- `StackTraceElement` **không có `getCodeSource()`**.
- Một frame cho biết nơi exception stack đi qua, không tự cho biết **expected runtime class** trong `ClassCastException`.
- Một class có thể đã unloaded hoặc không thể lookup lại từ current TCCL; không mặc định `Class.forName(frame.getClassName())` sẽ nạp đúng class gốc.
- Named module name/version trong frame có thể không đủ phân biệt hai layers hoặc artifacts.

Vì vậy hãy **giữ stacktrace**, đồng thời log `Class<?>` metadata tại những boundary có sẵn type objects.

## 19. Đừng làm logger thay đổi quá trình nạp lớp

Các hành động chẩn đoán có thể gây side effects:

- Gọi `Class.forName(name)` với overload một tham số có thể **initialize class**, chạy static blocks.
- Dùng `Class.forName(name, true, loader)` cũng yêu cầu initialization.
- Gọi `getDeclaredMethods()` hoặc thực hiện reflection sâu có thể kích hoạt thêm resolution/access checks, phát sinh lỗi khác nếu dependencies thiếu.
- In `loader.toString()` trên custom loader có thể chạy user-defined code.
- Gọi `resourceUrl.openStream()` để “xem bytes” có thể gây I/O/block, network access, permission checks.
- Scan toàn bộ `java.class.path`/`java.module.path` tại thời điểm lỗi có thể tốn thời gian hoặc lộ thông tin triển khai.
- Log full URL, environment, system properties hay command-line arguments có thể làm rò secrets.

**Chẩn đoán ít xâm lấn:** nếu đã có `Class<?> type`, lấy `getName()/getClassLoader()/getModule()/getProtectionDomain()` theo policy. Nếu class chưa tồn tại, ghi requested name + lookup loader + known artifact paths từ cấu hình mà không thử khởi tạo class mới chỉ để “xem thêm metadata”.

Khi điều tra sâu ngoài luồng request, có thể sử dụng `Class.forName(name, false, loader)` để yêu cầu lookup **không chủ động initialize target**, nhưng thao tác đó vẫn có thể **load/link thêm classes hoặc phát sinh error**. Phải thực hiện có chủ đích, không nhét vào mọi error handler.

## 20. Bảng lỗi phổ biến và metadata nên ưu tiên

| Triệu chứng | Metadata quan trọng | Giả thuyết cần kiểm tra |
| --- | --- | --- |
| `ClassNotFoundException` | Requested binary name, lookup loader, TCCL, URLs/JAR entries | Không tìm thấy class từ nguồn được phép |
| `NoClassDefFoundError` | Missing dependency name, loader, earliest cause/stacktrace | Dependency không load/link được hoặc class erroneous |
| `ClassCastException` | Expected + actual `Class<?>`, defining loaders, modules, CodeSources | Hai runtime type identities khác nhau |
| `NoSuchMethodError` | Caller and callee CodeSources, binary descriptors, loader, module | Version skew: compile against API khác runtime |
| `NoSuchFieldError` | Callee artifact/version + field descriptor | Runtime class thiếu field tương ứng |
| `LinkageError: loader constraint violation` | Types ở method descriptors, cả defining loaders | Các loader dùng definitions không tương thích |
| `IllegalAccessError` | Caller/target module, runtime package, defining loader, exports | Package-private/JPMS access không phù hợp |
| `InaccessibleObjectException` | Caller module, target module/package, opens rules | Deep reflection vào package chưa mở |
| `ServiceConfigurationError` | Service API, provider name, loader/TCCL, descriptor/JAR | Provider missing, not assignable, constructor/factory lỗi |
| `ExceptionInInitializerError` | Failing class, module, CodeSource, root cause | `<clinit>` lỗi; class đã được load nhưng không initialized |
| `ClassFormatError` | Class bytes source, classfile major/minor, generator version | Bytecode/file class sai cấu trúc |
| `UnsupportedClassVersionError` | Artifact path, `javap -verbose`, running JVM version | Classfile version quá mới/preview mismatch |

**Đừng suy từ exception message duy nhất.** `NoClassDefFoundError` không chỉ có một nguyên nhân; `IllegalAccessError` có thể liên quan đến package/module, không phải đơn thuần là `private`. Class metadata và cause chain mới giúp kiểm chứng.

## 21. CodeSource location không chứng minh checksum, version hoặc nguồn thực tế sau transform

Một lỗi thường gặp là lấy:

```java
URL origin = type.getProtectionDomain()
                 .getCodeSource().getLocation();
```

rồi kết luận “class này chắc chắn có hash X vì JAR ở path Y”. Đây **không phải phép xác minh checksum**.

Với custom loaders, bytecode có thể được transform trước khi `defineClass`; path/code source vẫn chỉ định nơi bắt đầu hoặc domain được gán. File có thể bị thay sau khi bytes đã được nạp. Multi-release JAR có thể chọn class bytes theo runtime version. Java agents có thể biến đổi bytecode lúc loading. Do đó một CodeSource URL không tự chứng minh toàn bộ bytecode đã được JVM định nghĩa.

Nếu cần **provenance mạnh**:

1. Tại deployment, ghi artifact build ID, version và digest SHA-256 từ nguồn đáng tin.
2. Nếu cần xác minh content đã load, thiết kế pipeline bytecode loader có kiểm tra digest/signature theo policy trước khi define.
3. Ghi mapping **loader generation → artifact ID/version/digest** bên ngoài class metadata, có ownership rõ.
4. Không tự tải URL tùy ý trong error handler để hash remote content; có thể gây SSRF, latency, lộ secrets hoặc đọc bytes khác bản đã chạy.
5. Với class do Java agent transform, nếu thực sự cần audit nội dung sau transform, xây cơ chế riêng ở instrumentation boundary và quản lý bảo mật của log.

**Nguồn mã trong log là chỉ dấu để khoanh vùng, không phải bằng chứng integrity đầy đủ.**

## 22. Kết hợp log metadata với JVM và artifact inspection

Khi metadata của `Class<?>` cho thấy một loader hoặc CodeSource bất ngờ, có thể chạy HotSpot với `-Xlog:class+load=info` để quan sát tên và nguồn class khi JVM load:

```bash
java -Xlog:class+load=info -cp out demo.DiagnosticDemo
```

Đây là lệnh minh họa; những dòng JVM ghi ra phụ thuộc JDK, classpath và thời điểm load. Log load không chứng minh class đã initialize, cũng không luôn cho object identity của loader. Dùng `java -Xlog:help` để kiểm tra tag. Với lỗi `NoSuchMethodError`, đối chiếu binary descriptors từ artifact runtime bằng `javap -s -p`; dùng `jar --list --file plugin.jar` để kiểm tra entry. Hai lệnh công cụ đều đọc artifact trên đĩa, không chứng minh JVM đã chọn chính artifact đó.

Các bài tiếp theo trình bày riêng cách đọc Unified Logging (`-verbose:class`, `class+load`, `class+init`), cũng như quy trình `javap`/`jar`/`jdeps`. Trọng tâm ở đây là **nối kết quả chẩn đoán ngoại tuyến với đúng runtime class/loader đã log**, giữ nguyên throwable/cause ban đầu.
## 23. Class loader + module access: đọc hai phía lỗi

Với `IllegalAccessError` hoặc lỗi JPMS reflection, log cả:

- **caller class**: `Class<?>` đại diện code gọi, loader/module/package;
- **target class**: loader/module/package/code source;
- **member**: method/field name và descriptor nếu có;
- **module rules**: caller module có đọc target module không, target package có exports/opens phù hợp không;
- **runtime package**: hai classes có cùng package name *và* defining loader hay không.

Với named modules, `module.isNamed()` và `module.getName()` giúp biết tên, nhưng **module name không bằng module object identity**. Khi có nhiều `ModuleLayer`, ghi layer/loader giúp phân biệt các cấu hình trùng module name.

Ví dụ logic:

```java
Module callerModule = callerClass.getModule();
Module targetModule = targetClass.getModule();

boolean reads = callerModule.canRead(targetModule);
boolean exported = targetModule.isExported(
        targetClass.getPackageName(), callerModule);

System.err.println("reads=" + reads);
System.err.println("exported=" + exported);
```

Đây là **snippet**, cần hai biến `callerClass`/`targetClass`. `canRead`/`isExported` hỗ trợ chẩn đoán nhưng không chứng minh một reflective operation riêng chắc chắn thành công; member visibility, `opens`, lookup privileges và caller context vẫn quan trọng.

## 24. Khi lỗi xảy ra trong ServiceLoader

Với `ServiceConfigurationError`, hãy log **hai vai trò khác nhau**: service API type được consumer yêu cầu và provider implementation (nếu provider đã được resolve), cùng loader/module/CodeSource của mỗi bên. Ngoài ra, log loader dùng để khám phá providers: overload `ServiceLoader.load(service)` dùng TCCL, còn `ServiceLoader.load(service, chosenLoader)` dùng loader truyền vào.

Nếu discovery thất bại trước khi có provider `Class<?>`, chỉ ghi provider *binary name* đọc từ descriptor (nếu biết), không giả một defining loader. Phân biệt thiếu provider descriptor, provider không load được, constructor lỗi và provider không assignable vì service interface bị định nghĩa hai lần. Giữ `ServiceConfigurationError` và nguyên nhân của nó. Trong named modules, kiểm tra `uses`/`provides` và layer liên quan. Bài ServiceLoader của chương về loader ngữ cảnh giải thích chi tiết cơ chế SPI.
## 25. Mẫu structured log không làm lộ thông tin cho người dùng cuối

Một bản ghi nội bộ:

```json
{
  "event": "PLUGIN_LINKAGE_FAILURE",
  "correlationId": "req-42",
  "phase": "INVOKE",
  "requestedClass": "plugin.Widget",
  "resolvedClass": "plugin.Widget",
  "definingLoader": "plugin-A",
  "module": "<unnamed>",
  "codeSource": "<internal-artifact-id>",
  "errorType": "java.lang.ClassCastException",
  "expectedType": "api.Widget",
  "actualType": "plugin.Widget"
}
```

Đây là **JSON mẫu minh họa**, không phải output chạy từ ví dụ nào và không phải schema bắt buộc. ID/source chỉ minh họa. Error logger nội bộ cũng nên lưu throwable/stacktrace thật và plugin generation theo policy.

Thông điệp trả cho client có thể chỉ là:

```text
PLUGIN_EXECUTION_FAILED (correlationId=req-42)
```

Không tự phơi đường dẫn `file:/home/.../deployment/secrets`, absolute filesystem paths, internal hostnames, signer metadata hoặc toàn bộ stacktrace qua public API. **Thông tin nhiều cho người vận hành, tối thiểu cho caller không đáng tin** là lựa chọn tốt hơn.

Một structured log vẫn có thể gây log injection nếu các fields nhận dữ liệu không tin cậy. Log backend phải escape newline/control characters và hạn chế kích thước mỗi field.

## 26. Checklist chẩn đoán classloading trong thực tế

1. **Xác định thao tác**: `loadClass`, `Class.forName`, SPI discovery, linkage, init hay reflection/invoke?
2. **Ghi requested binary name** khi chưa có `Class<?>`. Không tự tạo “resolved class” giả.
3. **Nếu có runtime class**, ghi `getName()`, `getClassLoader()`, `getModule()` và null-safe `CodeSource.location`.
4. **Định danh loader instance** bằng tên/loại/ID cấp bởi framework, không chỉ Java class name của loader.
5. **Ghi cả expected và actual classes** cho cast/assignability failures.
6. **Ghi lookup loader và TCCL** nếu có thể ảnh hưởng delegation/discovery.
7. **Không suy CodeSource phải tồn tại**: bootstrap, generated classes, custom loaders có thể trả null.
8. **Ghi module named/unnamed rõ ràng**, và module layer nếu hệ thống dùng nhiều layers.
9. **Giữ stacktrace/cause chain** của lỗi đầu tiên; đặc biệt khi `ExceptionInInitializerError` dẫn tới `NoClassDefFoundError` về sau.
10. **Đối chiếu artifact thật**: full CodeSource trong log nội bộ được phép, build/version/digest và URL roots.
11. **Không kích hoạt initialization vô ý** chỉ để log metadata.
12. **Không gọi network/I/O không giới hạn trong error handler**, không tải class/JAR chỉ để chứng minh một giả thuyết.
13. **Tôn trọng bảo mật log**: redact secrets/path/tenant data, giữ correlation ID.
14. **Tái hiện trong đúng class loader graph**; IDE/test classpath có thể làm parent loader che plugin JAR.
15. **Kiểm tra lỗi sau khi sửa**: có thể hết `ClassNotFoundException` nhưng lộ thêm `NoSuchMethodError` do dependency version.
16. **Ghi rõ giới hạn** của logging: CodeSource không chứng minh integrity, loader name không định danh duy nhất, module name không quyết định toàn bộ access.

## 27. Các lỗi giải thích phổ biến cần tránh

- **“`Class.getName()` giống nhau thì là cùng runtime type.”** Sai nếu defining loaders khác nhau.
- **“`child.loadClass` trả class thì class đó do child định nghĩa.”** Sai; parent-first có thể trả class của parent.
- **“`getClassLoader() == null` nghĩa là class bị lỗi.”** Sai; thường là bootstrap hoặc primitive.
- **“`getModule().getName() == null` nghĩa là không có module.”** Sai; unnamed module hợp lệ.
- **“Mọi `Class<?>` đều có CodeSource URL.”** Sai; `CodeSource` hoặc location có thể null.
- **“CodeSource file path hiện tại chứng minh bytes đã được JVM nạp.”** Sai; file có thể thay đổi hoặc bytes bị transform.
- **“`Class.getResource` giống `CodeSource.getLocation`.”** Sai; class resource và artifact provenance có semantics khác.
- **“`ClassNotFoundException` luôn có `Class<?>` để in loader/module.”** Không; phải log requested name và lookup context.
- **“Một dòng exception message đủ tìm đúng JAR.”** Không trong plugin/module loader graphs.
- **“`ClassLoader.getName()` là globally unique.”** Không; có thể null/trùng, nên thêm generation/instance ID.
- **“`StackTraceElement.getModuleName()` cung cấp code source.”** Không; không có `getCodeSource` trên stack frame.
- **“Log toàn bộ absolute paths/URLs vào client response là an toàn.”** Không; có thể lộ nội bộ/secrets.
- **“Gọi `Class.forName(name)` chỉ để quan sát metadata không có side effects.”** Sai; overload mặc định có thể initialize class.
- **“Catch `Throwable` rồi chạy reflective probes sâu sẽ luôn làm log tốt hơn.”** Sai; có thể che root cause, tạo lỗi mới hoặc block.
- **“`javap` chứng minh runtime đã chọn artifact đó.”** Sai; đó là công cụ đọc bytecode offline.
- **“Thêm `-Xlog:class+load` nghĩa là mọi class đã initialize.”** Sai; loading và initialization khác nhau.
- **“Module name cùng nhau nghĩa là loader identity cùng nhau.”** Không; cần module object, layer và defining loader.

## 28. Câu hỏi tự kiểm tra

**1. Bốn thông tin runtime nên có khi log một `Class<?>` là gì?** Tên lớp, defining loader, module và CodeSource location nếu được cung cấp.

**2. `Class.getClassLoader() == null` có thể là gì?** Bootstrap loader representation hoặc primitive/void type.

**3. `module.getName() == null` nghĩa là gì?** Unnamed module, không phải class không thuộc module.

**4. Vì sao `child.loadClass("api.Contract")` chưa chứng minh child define class?** Parent-first có thể trả class của parent, cần kiểm tra `type.getClassLoader()`.

**5. `CodeSource.getLocation()` có thể null không?** Có; cần null-safe checks.

**6. Tại sao không in `ClassNotFoundException` cùng defining loader?** Lookup thất bại nên chưa có resolved `Class<?>`; chỉ log requested name và lookup loader.

**7. Vì sao `ClassCastException` cần metadata của expected lẫn actual types?** Hai types có thể trùng binary name nhưng khác defining loader, khiến `isAssignableFrom` false.

**8. `ClassLoader.getName()` có đảm bảo định danh riêng cho hai loader instances không?** Không. Có thể null hoặc trùng; dùng thêm runtime-scoped identity/generation.

**9. CodeSource URL khác class resource URL ở đâu?** CodeSource thường mô tả nguồn/domain cấp JAR/directory; resource có thể trỏ từng `.class` và được lookup theo module/loader rules.

**10. Nếu JDK class có `CodeSource == null`, kết luận nó thiếu JAR được không?** Không; đây có thể là tình huống bình thường trong runtime image.

**11. Có nên dùng `Class.forName(name)` trong exception logger để lấy metadata không?** Không mặc định; nó có thể kích hoạt class initialization.

**12. Khi `NoSuchMethodError`, ngoài loader và CodeSource cần gì?** So compiler/runtime method descriptors (`javap -s`), versions, dependencies và callsite.

**13. Với named modules, module name có đủ khẳng định access hợp lệ không?** Không; cần xem readability, exports/opens, caller/target modules và members.

**14. `StackTraceElement` Java 9+ có những metadata loader/module nào?** `getClassLoaderName()`, `getModuleName()`, `getModuleVersion()` — chúng có thể null.

**15. Có nên tin CodeSource URL như SHA-256 của bytes JVM đã execute không?** Không; integrity cần xác minh artifact/bytes riêng.

**16. Log class metadata sao cho không lộ secrets?** Dùng artifact ID/build version trong public records, giữ full URL ở hệ thống nội bộ được kiểm soát và redact đường dẫn/token.

**17. Một class do loader custom sinh hoàn toàn trong memory có thể có CodeSource không?** Có thể có nếu domain được gán, cũng có thể không; không được giả định bắt buộc.

**18. Khi logger đọc metadata nhưng bị `SecurityException`, nên làm gì?** Giữ lỗi chính, ghi sentinel như `<denied>` và không thay thế throwable ban đầu.

## 29. Nguồn tham khảo chính thức

- [Java SE 17 — `Class` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getName`, `getClassLoader`, `getModule`, `getProtectionDomain`, `getResource`.
- [Java SE 17 — `ClassLoader` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `getName`, parent delegation, `loadClass`, `getParent`.
- [Java SE 17 — `Module` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html): `isNamed`, `getName`, `getLayer`, readability/exports.
- [Java SE 17 — `ProtectionDomain` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/ProtectionDomain.html): `getCodeSource()` và metadata domain.
- [Java SE 17 — `CodeSource` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/CodeSource.html): `getLocation()`, signer information và khả năng null.
- [Java SE 17 — `StackTraceElement` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StackTraceElement.html): frame class, loader/module names và versions.
- [Java SE 17 — `URLClassLoader` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): URL roots, named loaders và class/resource loading.
- [Java Virtual Machine Specification 17, `5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): defining loaders, runtime type identity, linking/access.
- [JDK 17 `javap` tool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): inspection binary names, descriptors và classfile major version.

**Kết luận:** Khi một ứng dụng Java gặp lỗi class loading/linking, **chỉ tên class không đủ**. Nếu đã có `Class<?>`, hãy log **`getName()` + `getClassLoader()` + `getModule()` + null-safe `ProtectionDomain/CodeSource`**, kết hợp lookup loader/TCCL, expected/actual types, stacktrace và artifact generation. Nếu class chưa nạp thành công, log **requested name và lookup context**, không bịa defining loader/module. Cuối cùng, luôn cân bằng chất lượng chẩn đoán với **khả năng thiếu metadata và bảo mật thông tin log**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-diagnostics-002">Lần theo parent chain và loader thực sự định nghĩa lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Lần theo parent chain và ClassLoader thực sự định nghĩa lớp trong Java

## 1. Bài toán chẩn đoán: gọi loader A nhưng class lại do loader B định nghĩa

Trong một hệ Java có plugin, nhiều JAR, container hoặc framework, log thường chỉ ghi:

```java
Class<?> type = pluginLoader.loadClass("com.example.Service");
```

Người đọc dễ suy ra: “Vì gọi `pluginLoader.loadClass`, nên chính plugin loader đã định nghĩa `Service`.” **Đây là kết luận không có cơ sở.** Với cơ chế parent-first thông thường, loader nhận yêu cầu có thể ủy quyền cho parent, parent tiếp tục hỏi parent của nó, và class được trả về có thể là định nghĩa từ một loader **hoàn toàn khác**.

Trong chẩn đoán class loading, cần tách ba câu hỏi:

1. **Ai nhận yêu cầu?** Loader được code gọi vào, ví dụ `pluginLoader`.
2. **Đường ủy quyền qua những loader nào?** Parent chain hoặc các cạnh chuyển tiếp do custom framework tự tạo.
3. **Ai thực sự định nghĩa runtime class?** Defining ClassLoader, tra bằng `returnedClass.getClassLoader()`.

Bài này trình bày cách lần theo parent chain, kiểm chứng defining loader bằng class object, phân biệt với initiating loader và TCCL, truy xuất nguồn bytecode, đọc log JVM và xử lý những trường hợp trùng tên lớp. Một chương trình Java 17 hoàn chỉnh sẽ tạo nhiều loader để **chứng minh bằng kết quả chạy được**, thay vì chỉ dựa vào sơ đồ.

## 2. Ba khái niệm không được đánh đồng

### 2.1. Requesting loader — đối tượng nhận lời gọi

Giả sử:

```java
ClassLoader requested = pluginLoader;
Class<?> type = requested.loadClass("com.example.Report");
```

Đây là snippet, cần có `pluginLoader` và class tương ứng.

**Requesting loader** ở đây chỉ là `requested` — đối tượng mà chương trình gọi. Nó có thể tự định nghĩa class, nhưng cũng có thể nhờ loader khác.

### 2.2. Initiating loader — thuật ngữ ở mức JVM

JVMS phân biệt loader **khởi xướng việc nạp** (*initiating loader*) và loader **định nghĩa lớp** (*defining loader*). Khi loader con ủy quyền và nhận class do parent định nghĩa, JVM có thể ghi nhận loader con như một initiating loader của class đó. Điều này quan trọng cho các ràng buộc nạp/liên kết và việc tái sử dụng lớp.

**Đừng coi “initiating” và “defining” là cùng một khái niệm.** Một class có một defining loader theo mô hình định danh runtime; có thể được nhiều loader khác nhau yêu cầu/nạp qua delegation. Phương thức protected `findLoadedClass(name)` kiểm tra class đã được JVM ghi nhận với loader liên quan, không có nghĩa kết quả nhất định do chính loader đó định nghĩa.

### 2.3. Defining loader — ai sở hữu định nghĩa runtime?

Từ một `Class<?>` đã được trả về:

```java
Class<?> type = requested.loadClass("com.example.Report");
ClassLoader defining = type.getClassLoader();
```

Giá trị `defining` là **defining ClassLoader** của runtime class đó. Có thể:

- Bằng `requested` nếu loader này tự định nghĩa.
- Bằng parent/ancestor nếu parent-first delegation đã tìm được class.
- Bằng một loader khác nếu framework áp dụng routing tùy chỉnh.
- Bằng `null` đối với class do bootstrap loader định nghĩa, ví dụ `java.lang.String` ở Java tiêu chuẩn.

**Quy tắc kiểm chứng:** muốn biết một class thực sự thuộc loader nào, hãy kiểm tra **đối tượng Class thật đã được trả về hoặc đang được sử dụng**, thay vì suy từ loader mà code ban đầu gọi.

## 3. Parent chain được biểu diễn như thế nào?

Trong Java 17 thông thường:

```text
       Bootstrap (Class.getClassLoader() biểu diễn bằng null)
           ^
           |
       Platform ClassLoader
           ^
           |
       Application/System ClassLoader
           ^
           |
       Plugin ClassLoader (nếu parent là application)
```

Dấu mũi tên chỉ **hướng từ child lên parent**, không phải hướng “sở hữu class”. Một plugin loader có thể được tạo với parent khác, ví dụ:

```java
ClassLoader parent = ClassLoader.getPlatformClassLoader();

try (java.net.URLClassLoader plugin =
        new java.net.URLClassLoader(pluginUrls, parent)) {
    // Chỉ code/plugin cần dùng platform classes, theo thiết kế.
}
```

Đây là snippet minh họa; cần có `pluginUrls`. Trong trường hợp này parent trực tiếp là **platform**, không phải application loader. Vì vậy **không thể chỉ nhìn tên “plugin loader” để giả định cây parent của nó**.

Các API liên quan:

```java
ClassLoader system = ClassLoader.getSystemClassLoader();
ClassLoader platform = ClassLoader.getPlatformClassLoader();
ClassLoader parent = system.getParent();
```

Đây là snippet. Ở môi trường chuẩn, system/application loader thường có parent là platform; nhưng class loader hierarchy có thể được tùy biến bằng launcher, runtime hoặc framework.

**Bootstrap không luôn là một Java ClassLoader object hiển thị công khai.** Khi gọi `ClassLoader.getParent()` rồi nhận `null`, parent tiếp theo không còn là object Java quan sát được. Khi gọi `String.class.getClassLoader()` trả `null`, đó là biểu diễn API cho bootstrap loader.

### Parent chain là quan hệ cha–con, không phải toàn bộ đồ thị tìm lớp

Một loader tùy chỉnh có thể override `loadClass` để chuyển một số namespace sang **peer loader**, thread context loader hoặc service registry. Ví dụ:

```text
Plugin A  --parent--> Application
    |
    +-- custom route for api.impl.* --> Plugin B
```

Ở đây, `A.getParent()` chỉ mô tả parent trực tiếp. Nó **không tự tiết lộ** custom route A→B. Do đó tracing đầy đủ đôi khi phải kết hợp `getParent()` với log từ các override của `loadClass` và các bộ định tuyến của framework.

Trong hệ JPMS (Java 9+), module resolution, module layers và class loader mapping cũng ảnh hưởng class được tìm ở đâu; không nên suy toàn bộ hành vi từ cây parent đơn giản.

## 4. Parent-first delegation: đọc chính xác các bước

Thuật toán mặc định `ClassLoader.loadClass(String, boolean)` về tổng thể:

```text
child.loadClass("pkg.Type")
    |
    +-- findLoadedClass("pkg.Type")
    |       \-- Đã có? trả Class đã ghi nhận
    |
    +-- parent.loadClass("pkg.Type")
    |       \-- Parent tìm được? trả Class của parent/ancestor
    |
    +-- child.findClass("pkg.Type")
            \-- Child tìm bytecode rồi defineClass -> trả Class
```

Ngoài sơ đồ còn có class loading locks, resolution/linking và các exception theo API. Sơ đồ chỉ diễn tả ưu tiên **mặc định**.

Hệ quả:

- Nếu parent **có** `pkg.Type`, child thường không tìm bản bytecode cùng tên ở JAR riêng.
- Nếu parent **không có**, child có thể gọi `findClass` để tự định nghĩa.
- Một child có thể **đã ghi nhận nạp** một class do parent định nghĩa; yêu cầu lặp về cùng tên trả lại class object đó.
- Đổi thứ tự URL trong child **không giải quyết** xung đột khi parent-first đã trả class từ parent.
- Nếu override `loadClass` thành child-first, quy tắc ưu tiên khác; phải quan sát implementation thực sự, không mặc định toàn bộ loader đều parent-first.

Một code fragment dễ khiến nhầm lẫn:

```java
ClassLoader child = ...;
Class<?> result = child.loadClass("pkg.Type");
System.out.println(result.getClassLoader() == child);
```

Đây là snippet; kết quả có thể `true` **hoặc** `false`, phụ thuộc class thực tế được định nghĩa ở đâu. Nếu `false`, đó chưa chắc là bug — có thể là delegation được thiết kế đúng.

## 5. Danh tính runtime: cùng binary name có thể là hai kiểu khác nhau

Theo JVMS, một runtime class/interface có danh tính dựa vào **(binary name, defining loader)**.

```text
("pkg.Type", ParentLoader) != ("pkg.Type", SiblingLoader)
```

Ngay cả khi hai loader dùng **cùng nội dung bytecode**, việc cả hai tự định nghĩa `pkg.Type` tạo hai runtime types riêng.

| Quan sát | Class P do parent định nghĩa | Class S do sibling định nghĩa |
| --- | --- | --- |
| `getName()` | `pkg.Type` | `pkg.Type` |
| `getClassLoader()` | parent | sibling |
| `P == S` | `false` nếu hai định nghĩa độc lập | `false` |
| `P.isInstance(instanceOfS)` | Không chỉ vì tên giống nhau | Không tự tương thích |
| Static fields | Gắn với class P | Gắn với class S |
| Package-private | Kiểm tra theo runtime package của P | Theo runtime package của S |

**Runtime package** cũng phụ thuộc tên package và **defining loader**. Hai class cùng khai báo `package pkg;` nhưng do hai loader định nghĩa không được mặc nhiên dùng quyền package-private của nhau.

Khi thấy `ClassCastException` kiểu “X cannot be cast to X”, cần in defining loader của **hai đối tượng Class thực tế**, không chỉ so tên chuỗi.

## 6. Những API chẩn đoán và giới hạn của chúng

| API | Biết được gì? | Không nên suy điều gì? |
| --- | --- | --- |
| `type.getClassLoader()` | Defining loader của class thật | Không trả danh sách mọi loader từng yêu cầu |
| `loader.getParent()` | Parent trực tiếp của loader (hoặc null) | Không liệt kê custom peer routing |
| `ClassLoader.getSystemClassLoader()` | System class loader | Không đảm bảo đây là loader định nghĩa mọi class ứng dụng |
| `ClassLoader.getPlatformClassLoader()` | Platform loader của JDK | Không chứng minh parent của custom loader là platform |
| `Thread.currentThread().getContextClassLoader()` | TCCL đang gắn trên thread | Không mặc định là defining loader của class đang gọi |
| `Class.forName(name, false, loader)` | Yêu cầu tìm/nạp qua loader **được chỉ định**, chưa chủ động initialize | Class trả về có thể do parent/ancestor định nghĩa |
| `type.getProtectionDomain().getCodeSource()` | Code source metadata khi có | Không phải luôn có URL, cũng không xác nhận mọi lớp đến từ file/JAR |
| `type.getModule()` | Runtime module của class | Không thay thế defining loader hay cho biết toàn bộ resolution graph |
| `URLClassLoader.getURLs()` | Các URL đăng ký làm nguồn tìm của URLClassLoader | Không chứng minh URL nào thực sự định nghĩa class; parent có thể thắng |
| `loader.getResource(name)` | URL tài nguyên đầu tiên theo policy lookup | Không phải chứng cứ quyết định class thật đã dùng URL ấy |
| `loader.getResources(name)` | Có thể thấy nhiều bản resource cùng tên | Không chứng minh JVM đã định nghĩa nhiều class cùng tên trong một loader |
| `findLoadedClass(name)` (protected) | Class JVM đã ghi nhận nạp với initiating loader tương ứng | Không có API public gọi từ ứng dụng ngoài subclass |

Với class bootstrap, `getClassLoader() == null` là bình thường; với primitive như `int.class`, API cũng trả `null` nhưng không nên coi primitive là một class file do bootstrap đọc như `java.lang.String`.

Một `Class<?>` có thể đại diện array class. Loader liên quan array types tuân theo quy tắc JVM đối với component type; đừng kết luận mọi array class đều đến từ một class file `[].class` trong JAR.

## 7. Thực hành Java 17: tạo parent, child và sibling loader, kiểm chứng định nghĩa thật

Chương trình này dùng `JavaCompiler` của JDK để **tự tạo hai phiên bản của cùng binary name** `diag.Sample` rồi nạp bằng ba `URLClassLoader`:

```text
Platform loader
    |
    +--> Parent URLClassLoader
    |        root: parent-classes/
    |        diag.Sample.origin() -> "PARENT"
    |          |
    |          +--> Child URLClassLoader
    |                root: child-classes/
    |                diag.Sample.origin() -> "CHILD"
    |                diag.ChildOnly.origin() -> "CHILD_ONLY"
    |
    +--> Sibling URLClassLoader
             root: child-classes/
             diag.Sample.origin() -> "CHILD"
```

Dù `Child URLClassLoader` có một `diag.Sample` riêng, lời gọi `child.loadClass("diag.Sample")` được parent-first delegation thỏa mãn ở **Parent**. Trong khi đó `diag.ChildOnly` không tồn tại ở parent, nên **Child tự định nghĩa**. Sibling có parent là platform, nên nó tự định nghĩa bản Sample ở child-classes.

### 7.1. Chương trình đầy đủ

Lưu toàn bộ mã vào file `LoaderTraceDemo.java`:

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.StringJoiner;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public final class LoaderTraceDemo {
    private static void compile(JavaCompiler compiler, Path output,
                                Path... sources) throws Exception {
        String[] args = new String[4 + sources.length];
        args[0] = "--release";
        args[1] = "17";
        args[2] = "-d";
        args[3] = output.toString();
        for (int i = 0; i < sources.length; i++) {
            args[4 + i] = sources[i].toString();
        }
        if (compiler.run(null, null, null, args) != 0) {
            throw new IllegalStateException("Compilation failed");
        }
    }

    private static boolean definedFrom(Class<?> type, Path expected)
            throws Exception {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        return source != null && source.getLocation() != null
                && Path.of(source.getLocation().toURI()).toRealPath()
                       .equals(expected.toRealPath());
    }

    private static String origin(Class<?> type) throws Exception {
        return (String) type.getMethod("origin").invoke(null);
    }

    private static String chain(ClassLoader start, ClassLoader parent,
                                ClassLoader platform) {
        StringJoiner path = new StringJoiner(" -> ");
        for (ClassLoader at = start; at != null; at = at.getParent()) {
            path.add(at == start ? "child"
                    : at == parent ? "parent"
                    : at == platform ? "platform" : "other");
        }
        path.add("bootstrap");
        return path.toString();
    }

    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Use a full JDK 17+");
        }
        Path root = Files.createTempDirectory("loader-trace-");
        try {
            Path parentSrc = root.resolve("psrc/diag/Sample.java");
            Path childSrc = root.resolve("csrc/diag/Sample.java");
            Path onlySrc = root.resolve("csrc/diag/ChildOnly.java");
            Path parentOut = root.resolve("parent-classes");
            Path childOut = root.resolve("child-classes");
            Files.createDirectories(parentSrc.getParent());
            Files.createDirectories(childSrc.getParent());
            Files.createDirectories(parentOut);
            Files.createDirectories(childOut);
            Files.writeString(parentSrc,
                    "package diag; public class Sample {"
                    + " public static String origin() { return \"PARENT\"; } }");
            Files.writeString(childSrc,
                    "package diag; public class Sample {"
                    + " public static String origin() { return \"CHILD\"; } }");
            Files.writeString(onlySrc,
                    "package diag; public class ChildOnly {"
                    + " public static String origin() { return \"CHILD_ONLY\"; } }");
            compile(compiler, parentOut, parentSrc);
            compile(compiler, childOut, childSrc, onlySrc);

            ClassLoader platform = ClassLoader.getPlatformClassLoader();
            try (URLClassLoader parent = new URLClassLoader(
                         new URL[]{parentOut.toUri().toURL()}, platform);
                 URLClassLoader child = new URLClassLoader(
                         new URL[]{childOut.toUri().toURL()}, parent);
                 URLClassLoader sibling = new URLClassLoader(
                         new URL[]{childOut.toUri().toURL()}, platform)) {

                Class<?> fromChildRequest = child.loadClass("diag.Sample");
                Class<?> childOnly = child.loadClass("diag.ChildOnly");
                Class<?> childVersion = sibling.loadClass("diag.Sample");
                Class<?> fromForName = Class.forName("diag.Sample", false, child);

                System.out.println("chain=" + chain(child, parent, platform));
                System.out.println("requestIsDefining="
                        + (fromChildRequest.getClassLoader() == child));
                System.out.println("parentFirstOrigin=" + origin(fromChildRequest));
                System.out.println("parentFirstLoaderIsParent="
                        + (fromChildRequest.getClassLoader() == parent));
                System.out.println("parentCodeSource="
                        + definedFrom(fromChildRequest, parentOut));
                System.out.println("childOnlyOrigin=" + origin(childOnly));
                System.out.println("childOnlyLoaderIsChild="
                        + (childOnly.getClassLoader() == child));
                System.out.println("childCodeSource="
                        + definedFrom(childOnly, childOut));
                System.out.println("repeatedSameClass="
                        + (child.loadClass("diag.Sample") == fromChildRequest));
                System.out.println("forNameSameClass="
                        + (fromForName == fromChildRequest));
                System.out.println("siblingOrigin=" + origin(childVersion));
                System.out.println("sameBinaryName="
                        + childVersion.getName().equals(fromChildRequest.getName()));
                System.out.println("sameRuntimeClass="
                        + (childVersion == fromChildRequest));
                System.out.println("siblingLoaderIsSibling="
                        + (childVersion.getClassLoader() == sibling));
                Enumeration<URL> resources = child.getResources("diag/Sample.class");
                int matches = 0;
                while (resources.hasMoreElements()) {
                    resources.nextElement();
                    matches++;
                }
                System.out.println("classFileResources=" + matches);
                System.out.println("stringBootstrap="
                        + (child.loadClass("java.lang.String")
                                .getClassLoader() == null));
            }
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
```

### 7.2. Biên dịch và chạy

Từ thư mục chứa file nguồn, dùng JDK 17 trở lên:

```bash
javac --release 17 LoaderTraceDemo.java
java LoaderTraceDemo
```

Chương trình tạo hai thư mục source và hai thư mục class tạm, biên dịch các class ở runtime bằng `javax.tools.JavaCompiler` rồi dọn chúng sau khi ba URLClassLoader đã được đóng. **Không thêm** các thư mục tạm vào application classpath; nếu làm thế, loader được cấu hình có thể không còn minh họa đúng giả thiết “platform parent không chứa diag.Sample”.

### 7.3. Stdout đã kiểm chứng

Đã biên dịch chương trình với JDK 21 và `--release 17`, chạy thành công trong môi trường JDK tiêu chuẩn. Kết quả:

```text
chain=child -> parent -> platform -> bootstrap
requestIsDefining=false
parentFirstOrigin=PARENT
parentFirstLoaderIsParent=true
parentCodeSource=true
childOnlyOrigin=CHILD_ONLY
childOnlyLoaderIsChild=true
childCodeSource=true
repeatedSameClass=true
forNameSameClass=true
siblingOrigin=CHILD
sameBinaryName=true
sameRuntimeClass=false
siblingLoaderIsSibling=true
classFileResources=2
stringBootstrap=true
```

### 7.4. Vì sao mỗi kết quả lại quan trọng?

**`chain=child -> parent -> platform -> bootstrap`**: lần theo `getParent()` cho thấy đường quan hệ cha của loader thử nghiệm. Nhãn cuối “bootstrap” đại diện cho parent `null` ở cuối chuỗi; không phải object ClassLoader Java được in tên.

**`requestIsDefining=false`**: yêu cầu do child gửi, nhưng `fromChildRequest.getClassLoader()` **không phải child**. Đây là bằng chứng trực tiếp rằng “loader nhận request” khác “loader định nghĩa”.

**`parentFirstOrigin=PARENT`** và **`parentFirstLoaderIsParent=true`**: parent-first đã dùng class của parent dù child có một `diag.Sample.class` chứa logic khác.

**`parentCodeSource=true`**: `ProtectionDomain.getCodeSource().getLocation()` của class này chỉ đến thư mục `parent-classes` mà chương trình tự tạo. Đây là tín hiệu bổ sung có giá trị, nhưng trên JVM/runtime khác có thể có `CodeSource == null` hoặc URL không phải file thông thường.

**`childOnlyOrigin=CHILD_ONLY`**, **`childOnlyLoaderIsChild=true`**, **`childCodeSource=true`**: parent không có `diag.ChildOnly` nên child tìm bytecode tại URL local và tự định nghĩa class. Trường hợp này khác hoàn toàn `diag.Sample`.

**`repeatedSameClass=true`**: gọi `child.loadClass("diag.Sample")` lần nữa nhận lại class đã ghi nhận thay vì định nghĩa một runtime type mới cho cùng tên.

**`forNameSameClass=true`**: `Class.forName("diag.Sample", false, child)` dùng **loader chỉ định** để tìm class, nhưng class trả về vẫn là cùng định nghĩa của parent. Tham số `false` yêu cầu không chủ động khởi tạo class tại lời gọi này.

**`siblingOrigin=CHILD`**, **`sameBinaryName=true`**, **`sameRuntimeClass=false`**, **`siblingLoaderIsSibling=true`**: sibling có parent là platform, nên không thấy bản Sample ở parent URLClassLoader và tự định nghĩa bản trong `child-classes`. Hai class cùng tên nhưng **khác defining loader**, do đó là hai runtime types.

**`classFileResources=2`**: hỏi child về resource `diag/Sample.class` có thể liệt kê cả bản của parent và bản ở URL local. **Hai URL resource không chứng minh JVM đã định nghĩa hai runtime classes cùng tên trong child**; JVM thật sự chọn một định nghĩa cho `diag.Sample` ở đường child request.

**`stringBootstrap=true`**: class JDK `java.lang.String` do bootstrap định nghĩa; khi gọi qua child cũng nhận về class nền tảng, thể hiện delegation.

## 8. Thêm helper để đọc parent chain trong dự án thật

Tùy framework, ClassLoader có thể có `toString()` rất dài hoặc nhiều loader có cùng tên hiển thị. Nên gán ID ngắn theo **object identity**, ghi rõ loader được yêu cầu, rồi in defining loader sau khi load.

Helper tối giản:

```java
static void printParentChain(ClassLoader starting) {
    int depth = 0;
    for (ClassLoader loader = starting;
         loader != null;
         loader = loader.getParent()) {
        System.out.println("depth=" + depth++
                + ", type=" + loader.getClass().getName()
                + ", identity=" + System.identityHashCode(loader));
    }
    System.out.println("depth=" + depth + ", loader=<bootstrap/null>");
}
```

Đây là **helper minh họa**, không phải chương trình độc lập. Nếu một loader custom vi phạm dự kiến và tạo parent chain kỳ lạ, nên có giới hạn chiều sâu và visited-set khi viết công cụ chẩn đoán của framework. Quan hệ parent được thiết lập theo constructor thông thường vốn không dễ tạo vòng, nhưng custom delegation routes bên ngoài parent chain **có thể** có chu trình riêng.

**Không xem `System.identityHashCode(loader)` là ID toàn cục tuyệt đối**: có thể trùng giữa object khác nhau và không ổn định qua lần chạy JVM. Với logger sản phẩm, gán một ID từ registry bằng object-identity map cho mỗi session/plugin và không giữ mạnh loader sau khi unload.

### In thêm class/module và CodeSource

Một helper chẩn đoán class đã nạp:

```java
static void describe(Class<?> type) {
    ClassLoader loader = type.getClassLoader();
    java.security.ProtectionDomain pd = type.getProtectionDomain();
    java.security.CodeSource codeSource =
            pd == null ? null : pd.getCodeSource();

    System.out.println("name=" + type.getName());
    System.out.println("definingLoader=" + loader);
    System.out.println("module=" + type.getModule());
    System.out.println("package=" + type.getPackageName());
    System.out.println("source=" + (codeSource == null
            || codeSource.getLocation() == null
            ? "<unknown>" : codeSource.getLocation()));
}
```

Đây là helper độc lập ở mức method (đặt trong class và gọi với `Class<?>` thực tế). Nếu môi trường hạn chế truy cập một số metadata, cần xử lý exception phù hợp; đừng tự coi thiếu CodeSource là một class “không có defining loader”.

**Cảnh báo log bảo mật:** đường `CodeSource`, URL repository hoặc string `toString()` của loader có thể chứa đường dẫn nội bộ/thông tin nhạy cảm. Chỉ in ở môi trường chẩn đoán được cấp quyền và tránh lộ secret/token trong production log.

## 9. Thread Context ClassLoader (TCCL) khác defining loader như thế nào?

Một số framework nạp provider từ plugin JAR thông qua:

```java
ClassLoader context =
        Thread.currentThread().getContextClassLoader();
```

TCCL là **ngữ cảnh nạp lớp được gắn vào thread**, được một số API/framework sử dụng để chọn nơi khám phá class/service. Nó **không thay đổi** ClassLoader đã định nghĩa một class đang tồn tại.

Ví dụ ứng dụng chạy code host do application loader định nghĩa nhưng tạm dùng TCCL của plugin:

```java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();
try {
    thread.setContextClassLoader(pluginLoader);
    // Gọi framework/API vốn lựa chọn context class loader.
} finally {
    thread.setContextClassLoader(previous);
}
```

Đây là snippet, cần biến `pluginLoader`. Sau khi đổi TCCL:

- `Host.class.getClassLoader()` **không đổi**.
- `Class.forName("some.Type")` overload một tham số vẫn theo quy tắc loader gắn với caller, không tự nhiên dùng TCCL.
- `Class.forName("some.Type", false, pluginLoader)` dùng loader chỉ định, nhưng class có thể vẫn do parent định nghĩa.
- `ServiceLoader.load(Service.class)` có những quy tắc sử dụng context loader theo overload/API và quyền module caller; muốn chẩn đoán phải ghi rõ overload nào.
- Thread pool có thể giữ TCCL plugin sau khi plugin dừng; nhớ khôi phục trong `finally` để tránh classloader leak.

**Nguyên tắc:** logging nên tách `requestedLoader`, `TCCL` và `actualClass.getClassLoader()` thành **ba trường khác nhau**, không gán tất cả cùng một nhãn “loader”.

## 10. CodeSource: file/JAR nào cung cấp class?

Khi nghi “hai phiên bản JAR cùng có class này”, sau khi lấy được `Class<?>` thật:

```java
java.security.CodeSource source =
        clazz.getProtectionDomain().getCodeSource();
System.out.println(source == null ? "<unknown>"
        : source.getLocation());
```

Đây là snippet, cần biến `clazz`. Với class do `URLClassLoader` nạp từ JAR/thư mục thông thường, `CodeSource.getLocation()` thường cho URL nguồn đã định nghĩa class. Điều này hữu ích hơn việc chỉ in mọi JAR có mặt trong `loader.getURLs()`.

Tuy nhiên:

- Một loader tùy chỉnh dùng `defineClass` từ memory/database có thể không cung cấp CodeSource URL đặc trưng.
- Class từ runtime image `jrt:`/bootstrap có thể có metadata khác với file/JAR thông thường, thậm chí `null`.
- `CodeSource` có thể cho nơi xuất xứ khai báo, không bảo đảm một file vẫn còn tồn tại tại thời điểm debug.
- JAR có manifest `Class-Path` và công cụ build có thể đưa nguồn phụ thuộc gián tiếp; constructor URL list không luôn là toàn bộ thực tế tìm kiếm.
- URL classpath có thể là `jar:` hoặc giao thức khác; không giả định `new File(url.toURI())` luôn hợp lệ.
- Với named modules, `type.getModule()` giúp bổ sung ngữ cảnh; `CodeSource` không thay thế module information.

**Mạnh nhất là kết hợp:** class name → defining loader → module → CodeSource (nếu có) → launcher/module/classpath configuration → source artifact thực tế.

## 11. Tìm thấy nhiều resource .class không có nghĩa đã định nghĩa nhiều class

Khi chạy:

```java
Enumeration<java.net.URL> urls =
        loader.getResources("diag/Sample.class");
while (urls.hasMoreElements()) {
    System.out.println(urls.nextElement());
}
```

đây là một snippet, cần import `Enumeration` và biến `loader`. Nó có thể in URL từ parent lẫn child, kể cả khi `loader.loadClass("diag.Sample")` chỉ nhận định nghĩa của parent.

Sự khác nhau:

- **Resource search**: lấy URL file có tên phù hợp; `getResources` có thể trả nhiều nơi.
- **Class loading**: JVM định nghĩa/tái sử dụng một runtime class cho binary name trong defining loader phù hợp.
- **Parent-first**: nguồn được tìm đầu có thể đến từ parent, bỏ qua bytecode class trùng tên ở child.
- **Custom resource lookup**: có thể dùng thứ tự khác custom class delegation; không lấy `getResource` thay bằng chứng class `getClassLoader()`.

Nếu cần xác định class do loader nào định nghĩa, **`Class.getClassLoader()` là dữ kiện cốt lõi**. Resource enumeration hữu ích để kiểm tra **xung đột candidate files**, không quyết định lớp đang chạy.

## 12. Những trường hợp khiến parent-chain đơn giản chưa đủ

### 12.1. Child-first hoặc hybrid delegation

Một plugin loader có thể ưu tiên local cho `plugin.internal.*` nhưng giữ parent-first cho `java.*` và `host.api.*`. Khi ấy cần ghi policy theo namespace, vì quan hệ `getParent()` vẫn giống nhau nhưng thứ tự tìm có thể khác.

Hỏi:

1. Loader có override `loadClass(String)` hoặc `loadClass(String, boolean)` không?
2. Với binary name này, policy là parent-first hay child-first?
3. Có nhánh chuyển tiếp sang peer loader không?
4. Class đã ghi nhận qua `findLoadedClass` từ lời gọi trước chưa?
5. Một loader khác có định nghĩa trùng binary name không?

### 12.2. JPMS module layers

Trên module path, module resolution quyết định modules nào tham gia graph, còn loader mapping quyết định class được định nghĩa bởi loader nào. Hai class trong cùng module layer không nhất thiết dùng cùng loader, và tên module không thay cho defining loader.

Nên log cả:

```java
Class<?> type = ...;
System.out.println(type.getModule());
System.out.println(type.getClassLoader());
```

Đây là snippet. Khi có module layer tùy chỉnh, cần đối chiếu layer, module graph, service binding và package ownership thực tế.

### 12.3. Framework có loader tree riêng

Application server, OSGi-like framework, test runner, IDE, plugin host và công cụ build có thể:

- Tạo nhiều classloader đồng cấp;
- Delegate theo package/import rules;
- Dùng một loader chung cho API và loader riêng cho implementation;
- Áp dụng URL/classpath transformation hoặc instrumentation;
- Đưa bytes từ network, database, cache hay memory vào `defineClass`.

Vì vậy, parent chain là **điểm bắt đầu**. Khi lớp do loader bất ngờ định nghĩa, phải tìm logic routing và creation của framework, không tự mặc định JDK URLClassLoader thuần.

### 12.4. Class loader lifetime và hot reload

Một plugin có thể được reload với loader mới nhưng host vẫn giữ `Class<?>`/instance từ loader cũ trong cache. Code cùng tên tiếp tục hoạt động theo định nghĩa cũ. `URLClassLoader.close()` **không** tự xóa lớp cũ hoặc ép unload; classloader cũ chỉ có cơ hội được GC khi những đường strong-reference phù hợp biến mất.

Nếu chẩn đoán “đã deploy JAR mới nhưng JVM vẫn chạy class cũ”, cần xác định class object thực tế, defining loader ID, CodeSource và reference giữ phiên bản cũ; đừng chỉ xem nội dung file JAR hiện tại trên đĩa.

## 13. Bổ sung JVM log và công cụ khi parent chain chưa đủ

Nếu cần chứng minh lớp nào đã được JVM load từ nguồn nào, dùng `-Xlog:class+load=info` trên HotSpot và đối chiếu **cùng runtime `Class<?>`**: `getName()`, `getClassLoader()`, `getModule()`, `CodeSource` khi có. Log load ở mức info không ghi lại mọi nhánh `loadClass`/delegation; muốn biết custom routing phải chèn log đúng vào loader do bạn sở hữu.

Dùng `jcmd PID VM.classloaders` để quan sát parent tree của **target JVM** (kiểm tra lệnh hỗ trợ bằng `jcmd PID help`). Khi nghi JAR chứa binary name hoặc ABI trùng, dùng `jar --list` và `javap -s -p` trên từng artifact; chúng là dữ liệu tĩnh, chưa xác nhận bản được chọn ở runtime. Chương có bài riêng giải thích `-Xlog`, ba lệnh `jcmd` và `jar/javap/jdeps`; giữ parent-chain trace trong phần 7 làm bằng chứng thực nghiệm chính của bài này.
## 14. Quy trình chẩn đoán thực tế theo thứ tự ưu tiên

Khi thấy class được nạp “sai JAR/sai loader”:

1. **Lấy đúng `Class<?>`** mà code ứng dụng đang dùng. Nếu có object, ưu tiên `object.getClass()`; nếu có API method, kiểm tra class đang thực sự chứa nó. Tránh gọi `Class.forName` bừa với loader khác rồi tưởng đó là class gây lỗi.
2. **In binary name và defining loader** bằng `type.getName()`, `type.getClassLoader()`. Đối với bootstrap, ghi rõ `<bootstrap/null>`.
3. **Ghi loader nhận yêu cầu** và **TCCL** tách biệt; dùng object ID/registry ID để phân biệt nhiều instance có cùng class name.
4. **Lần theo parent chain** của loader nhận yêu cầu; đừng quên chain có thể kết thúc tại bootstrap khi parent trả null.
5. **Kiểm tra chính sách override** cho namespace bị ảnh hưởng: parent-first, child-first, peer delegation hay module provider resolution.
6. **In module và CodeSource** của Class thực tế khi metadata có sẵn.
7. **Liệt kê URL/resource candidates** bằng `getURLs`, `getResources("pkg/Type.class")` hoặc xem classpath/module path, nhưng không dùng chúng thay defining loader.
8. **Xem log JVM** `-Xlog:class+load` để bổ sung thời điểm và nguồn class; nếu cần, log trong custom `findClass`/`loadClass`.
9. **Đối chiếu trùng binary names và ABI** trong các JAR bằng `jar tf`/`javap`, đặc biệt khi gặp `NoSuchMethodError` hoặc `ClassCastException`.
10. **Kiểm tra loader lifecycle** nếu vừa redeploy plugin: host cache có thể giữ class hoặc instance do loader cũ định nghĩa.
11. **Kiểm tra module graph** nếu dùng JPMS, thay vì chỉ nhìn mảng URL của `URLClassLoader`.
12. **Đánh giá lại policy dependency:** loại bỏ bản JAR trùng khi có thể; đặt shared SPI ở parent và giữ implementation của plugin ở loader riêng.

Một log sự kiện có cấu trúc tốt:

```text
request=42 class=com.example.Service requestLoader=plugin-A
route=parent-first parent=host
result=success definingLoader=host module=<unnamed>
source=file:/deployment/host-api.jar
```

Đây là **log minh họa**, không phải stdout chương trình ở phần 7. Ghi lại **requesting** và **defining** riêng là bước cải thiện quan trọng nhất.

## 15. Những sai lầm thường gặp

| Suy luận sai | Cách hiểu đúng |
| --- | --- |
| “Tôi gọi `child.loadClass` nên class do child định nghĩa.” | Parent-first có thể trả class của parent. Kiểm tra `result.getClassLoader()`. |
| “`child.getParent() == parent` nghĩa mọi class child trả về đều do parent định nghĩa.” | Child có thể tự định nghĩa nếu parent không có class. |
| “`Class.getClassLoader() == null` là lỗi.” | Với `String` trong Java chuẩn, đó là bootstrap. |
| “TCCL của thread chính là defining loader của mọi class trong thread.” | TCCL chỉ là context có thể được một số API chọn dùng. |
| “Hai class có `getName()` bằng nhau thì là cùng kiểu.” | Cần cùng defining loader; có thể là hai runtime types. |
| “`getResources` tìm hai file .class nghĩa là hai runtime classes cùng được child định nghĩa.” | Resource enumeration và class definition khác nhau. |
| “`URLClassLoader.getURLs()` chứa plugin.jar nên mọi class plugin phải từ đó.” | Parent có thể cung cấp class trước, và nguồn gián tiếp cũng có thể tham gia. |
| “CodeSource null nghĩa class không có nguồn hoặc không có loader.” | CodeSource có thể không có metadata; defining loader vẫn xác định được khi có Class object. |
| “Đổi thứ tự URL trong child sẽ thắng bản ở parent.” | Không khi parent-first vẫn chọn parent. |
| “Đóng URLClassLoader sẽ thay class cũ bằng phiên bản mới.” | Close không phải redefine/unload; phiên bản mới thường cần loader mới. |
| “Hai loader cùng trỏ một thư mục nên tạo cùng runtime class.” | Nếu mỗi loader tự định nghĩa, class identity vẫn khác. |
| “Parent chain mô tả toàn bộ mọi cạnh nạp.” | Custom peer delegation/module resolution có thể tạo các đường khác. |
| “Không có class-load log nghĩa loadClass chưa từng được gọi.” | Lời gọi có thể chỉ tái sử dụng class đã load hoặc thất bại trước khi có sự kiện định nghĩa. |
| “Cứ bật child-first cho toàn bộ package để sửa conflict.” | Có thể nhân đôi API/SPI chung và phá type identity. |

## 16. Câu hỏi tự kiểm tra

**Câu 1.** `child.loadClass("pkg.Type")` trả về đối tượng Class mà `getClassLoader() == parent`. Ai định nghĩa class?

**Đáp án:** Parent. Child là nơi nhận yêu cầu, có thể là initiating loader; defining loader là parent.

**Câu 2.** Nếu parent không có `pkg.Helper` nhưng child có bytecode trong JAR riêng, ai có thể định nghĩa?

**Đáp án:** Child, nếu chính sách parent-first đi đến `findClass` của child và class file hợp lệ.

**Câu 3.** Vì sao `Thread.currentThread().getContextClassLoader()` không đủ để biết class `pkg.Type` do ai định nghĩa?

**Đáp án:** TCCL là ngữ cảnh thread cho các API lựa chọn loader; class runtime đã được định nghĩa bởi một loader xác định riêng và không đổi theo TCCL.

**Câu 4.** Hai `URLClassLoader` độc lập có cùng URL tới file `pkg.Type.class`. Có chắc `Class<?>` trả về bằng nhau?

**Đáp án:** Không nếu mỗi loader tự định nghĩa class. Danh tính runtime class phụ thuộc (binary name, defining loader).

**Câu 5.** `getResources("pkg/Type.class")` liệt kê hai URL, nhưng `child.loadClass("pkg.Type")` trả class parent. Đây có mâu thuẫn không?

**Đáp án:** Không. Resource lookup có thể liệt kê nhiều candidate; class loading chọn/tái sử dụng một định nghĩa theo delegation.

**Câu 6.** Công cụ nào quan trọng nhất để kiểm chứng defining loader của một class thật đã được lấy?

**Đáp án:** `type.getClassLoader()` trên chính đối tượng `Class<?>` đó. Bổ sung CodeSource/module/log khi muốn biết nguồn và bối cảnh.

**Câu 7.** Vì sao `Class.forName(name, false, child)` vẫn có thể trả class của parent?

**Đáp án:** Loader argument chỉ định nơi **bắt đầu yêu cầu**. Thuật toán delegation của child có thể hỏi parent, parent định nghĩa và trả class.

**Câu 8.** Nếu classloader cha không phải application loader mà là platform loader, nên làm gì trước khi chẩn đoán?

**Đáp án:** In actual `getParent()` chain, đừng áp dụng một hierarchy mặc định cứng nhắc từ tên plugin.

**Câu 9.** Khi hot reload plugin, `CodeSource` vẫn trỏ JAR cũ và defining loader ID cũ. Một khả năng là gì?

**Đáp án:** Có thể host đang giữ Class<?>/instance của phiên bản plugin cũ. Kiểm tra caches, listeners, threads và lifecycle.

**Câu 10.** Khi `NoSuchMethodError` xuất hiện với hai bản thư viện trùng class, chỉ cần in danh sách JAR đang có là đủ chưa?

**Đáp án:** Chưa. Cần xác định chính class runtime đã được chọn, defining loader, CodeSource và method descriptor so với compile-time API.

## 17. Checklist vận hành

- [ ] Lấy đúng `Class<?>`/instance gây vấn đề.
- [ ] Ghi binary name, defining loader object identity, module và CodeSource khi có.
- [ ] Lưu requesting loader khác trường với defining loader và TCCL.
- [ ] In parent chain bắt đầu từ loader nhận yêu cầu.
- [ ] Phân biệt bootstrap (null) với loader object thật.
- [ ] Kiểm tra custom `loadClass`/`findClass`, child-first và peer routes.
- [ ] Không dùng `getResource`/`getResources` thay bằng chứng class identity.
- [ ] Liệt kê JAR candidates và kiểm tra trùng binary name/method signatures.
- [ ] Với URLClassLoader, xem URL order nhưng nhớ parent-first có thể thắng.
- [ ] Với JPMS, ghi module/layer và resolution graph.
- [ ] Với hot reload, kiểm tra các Class<?>/instances/caches của loader cũ.
- [ ] Dùng unified class-load log/jcmd theo khả năng JDK, bảo vệ thông tin nhạy cảm.
- [ ] Đảm bảo host SPI/API chỉ có một định nghĩa runtime chung khi plugin chia sẻ kiểu.
- [ ] Kiểm thử cả nhánh parent sở hữu class và nhánh child tự định nghĩa class.

## 18. Kết luận và nguồn chính thức

**Parent chain trả lời “loader có thể hỏi ai?”; `Class.getClassLoader()` trả lời “ai thật sự định nghĩa class này?”.** Đây là hai câu hỏi khác nhau và đều quan trọng. Khi một class được yêu cầu qua child loader, parent-first delegation có thể khiến nó được định nghĩa ở parent; khi parent không tìm được, child mới có cơ hội định nghĩa local. Hai runtime classes cùng tên nhưng khác defining loader là hai kiểu riêng biệt, dù có cùng code source hay bytecode.

Muốn chẩn đoán đáng tin cậy, hãy xuất phát từ **Class object thật**, ghi rõ requesting/defining loader, lần parent chain, bổ sung module/CodeSource và xem code routing thực tế. Log hệ thống và resource enumeration là bằng chứng phụ trợ, không thay thế quy tắc runtime class identity.

Tài liệu tham khảo chuẩn:

- Java SE 17 `ClassLoader` API (`loadClass`, `findLoadedClass`, `getParent`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 `Class` API (`getClassLoader`, `forName`, `getModule`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java SE 17 `URLClassLoader` API (URL search paths, `getURLs`, `close`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- Java SE 17 `ProtectionDomain` / `CodeSource`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/ProtectionDomain.html
- JVMS 17 §5.3 — *Creation of Classes and Interfaces* (defining và initiating loaders): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- JLS 17 §12.2 — *Loading of Classes and Interfaces*: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2
- Java SE 17 launcher `java` (unified logging, classpath): https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- Java SE 17 `jcmd` utility: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-diagnostics-003">Xác định lớp đến từ thư mục, JAR hay runtime image nào</a>

<details>
<summary>Xem chi tiết</summary>

# Xác định lớp Java đến từ thư mục, JAR hay runtime image nào

> **Java Core → ClassLoader → Chẩn đoán.** Khi gặp `ClassNotFoundException`, `NoSuchMethodError`, lỗi thư viện trùng phiên bản hoặc muốn biết JVM thật sự nạp một lớp ở đâu, câu hỏi “class này đến từ file nào?” không thể luôn trả lời chỉ bằng `java.class.path`. Nguồn có thể là một thư mục biên dịch, một JAR trong URL search path, một module trong runtime image (`jrt:`), hoặc bytecode do custom loader sinh/đọc từ nguồn không có tệp `.class` tương ứng. Bài này hướng dẫn kết hợp **`Class<?>` thực tế**, **defining loader**, **`Module`**, **`ProtectionDomain.CodeSource`**, **class-resource URL** và **JVM class-loading logs** để tìm nguyên nhân đúng; gồm chương trình Java 17+ tạo và kiểm chứng đủ ba trường hợp thư mục/JAR/runtime image.

## 1. “Class đến từ đâu?” có nhiều ý nghĩa

Hãy phân biệt năm câu hỏi trước khi dùng API:

1. **Class identity:** object `Class<?>` nào thật sự được JVM sử dụng, với binary name và defining loader nào?
2. **Source artifact:** bytecode thường đến từ thư mục classes, một tệp JAR, module runtime image, hay bộ nạp tùy chỉnh?
3. **Resource URL:** có tìm được resource `.../Foo.class` cùng tên và URL protocol của nó là gì?
4. **Module ownership:** class thuộc named module `java.base`, một named module ứng dụng, hay unnamed module?
5. **Nguồn được khai báo:** `CodeSource.location` của `ProtectionDomain` cho biết loader/runtime **gắn nhãn** nguồn nào?

Không phải năm câu hỏi này luôn có cùng một đáp án.

Ví dụ một lớp `com.example.Widget` được JVM định nghĩa từ bytes do custom loader lấy qua HTTP nhưng loader không cung cấp `CodeSource`, đồng thời resource lookup của loader có thể không tìm thấy `com/example/Widget.class`. Trong trường hợp đó, **không có URL file/JAR đáng tin nào để suy ra** chỉ bằng reflection.

Ngược lại, với `URLClassLoader` tiêu chuẩn đọc một directory hoặc JAR file, `CodeSource` và resource URL thường cung cấp bằng chứng chẩn đoán rất hữu ích.

## 2. Tổng quan các tín hiệu chẩn đoán

| Tín hiệu | API/công cụ | Cho biết | Giới hạn |
| --- | --- | --- | --- |
| Defining class loader | `clazz.getClassLoader()` | Loader gắn với runtime `Class<?>`. | `null` với bootstrap-defined classes; không cho biết path. |
| Named/unnamed module | `clazz.getModule()` | Module chứa class, `Module.getName()`. | Unnamed module thường có tên `null`; không tự chỉ JAR. |
| CodeSource | `clazz.getProtectionDomain().getCodeSource().getLocation()` | URL nguồn được gắn vào protection domain nếu có. | Có thể `null` hoặc được custom loader tự gán, không bảo đảm là source thật đã xác minh. |
| Class resource | `clazz.getResource("/p/Foo.class")` | URL resource tra cứu được theo tên trong context của class. | Có thể `null`, trùng tên, khác bytes thực đã define, custom loader không expose resource. |
| Search path loader | `((URLClassLoader) loader).getURLs()` | Các URL cấu hình trực tiếp trên instance URLClassLoader. | Không phải đường nguồn chính xác của class; không bao gồm parent/module toàn JVM. |
| JVM loading event | `-Xlog:class+load=info` (HotSpot) | Nguồn được JVM ghi nhận khi nạp class. | Log phụ thuộc runtime/format; cần quan sát class thật, không thay API contract. |
| Runtime image modules | `jrt:` / `FileSystems.getFileSystem(URI.create("jrt:/"))` | Module resources thuộc runtime image hiện chạy. | Không phải file JAR `rt.jar` và không nên biến URL sang `File` thông thường. |

Chọn **đúng object `Class<?>`** làm điểm xuất phát. Chỉ nhìn string class name hoặc application classpath có thể sai khi có hai loaders khác nhau định nghĩa cùng binary name.

## 3. Xác định defining loader trước khi xác định đường dẫn

Một class runtime được phân biệt bởi **(binary name, defining class loader)** theo JVM Specification.

```java
Class<?> type = Class.forName("com.example.Widget", false, loader);
System.out.println(type.getName());
System.out.println(type.getClassLoader());
System.out.println(type.getModule());
```

Đây là **snippet** cần biến `loader` là một `ClassLoader` đã biết và class có tồn tại; nó không phải chương trình độc lập.

### Vì sao chọn đúng Class<?> quan trọng?

Giả sử:

```text
Host loader H:
  com.example.PluginApi.class

Plugin loader P1:
  com.example.Impl.class from plugin-v1.jar

Plugin loader P2:
  com.example.Impl.class from plugin-v2.jar
```

`P1.loadClass("com.example.Impl")` và `P2.loadClass("com.example.Impl")` có thể trả về **hai `Class<?>` khác nhau**. Nếu bạn dùng `Class.forName("com.example.Impl")` không truyền đúng loader, kết quả có thể được hỏi từ loader của caller, không phải loader quản lý plugin bạn muốn chẩn đoán.

`clazz.getClassLoader() == null` với các lớp JDK nền tảng được **bootstrap loader** định nghĩa, như `java.lang.String`. `null` **không** có nghĩa lớp không được JVM nạp hoặc có file nguồn ở path `null`. Các lớp được định nghĩa ở platform loader hay application loader thường trả object loader tương ứng, không nhất thiết là `URLClassLoader`.

**Không ép kiểu mù:**

```java
// KHÔNG di động trên Java 9+:
java.net.URLClassLoader app =
        (java.net.URLClassLoader) ClassLoader.getSystemClassLoader();
```

Built-in system/application loader của OpenJDK Java 9+ không phải `URLClassLoader` trong cấu hình thông dụng; câu lệnh có thể ném `ClassCastException`. Chỉ dùng `getURLs` nếu **instance thật sự** là `URLClassLoader`.

## 4. Nhìn vào Module: named, unnamed và java.base

`Class.getModule()` cho biết module **thực sự chứa class**. Với:

```java
Module module = String.class.getModule();
System.out.println(module.isNamed());
System.out.println(module.getName());
```

Kết quả trên Java 17+:

```text
true
java.base
```

Class `java.lang.String` thuộc named module `java.base` nằm trong Java runtime image. Đừng tìm nó bằng đường dẫn `lib/rt.jar` như trên Java 8.

Một class ứng dụng đặt ở classpath thông thường thường thuộc **unnamed module** của defining loader:

```java
Module m = someApplicationClass.getModule();
System.out.println(m.isNamed()); // thường false cho classpath class
System.out.println(m.getName());  // null nếu unnamed
```

Đây là snippet cần `someApplicationClass` được cung cấp. **Không suy ra** chỉ vì module unnamed thì class đến từ directory chứ không phải JAR: cả directory lẫn JAR trên classpath/`URLClassLoader` đều có thể thuộc unnamed module.

Named module `m.library` có thể được lấy từ một JAR trên module path, thư mục modules biên dịch, hoặc một module trong runtime image; `getModule().getName()` là **tên module**, không phải full path tới JAR.

## 5. ProtectionDomain.CodeSource.location: hữu ích nhưng phải xử lý null

Với một class đã được nạp:

```java
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

// Snippet: clazz là Class<?> cần kiểm tra.
ProtectionDomain pd = clazz.getProtectionDomain();
CodeSource cs = pd == null ? null : pd.getCodeSource();
URL location = cs == null ? null : cs.getLocation();
System.out.println(location);
```

`CodeSource.location` là một `URL` mô tả nguồn được gắn với class. Với `URLClassLoader` nạp từ directory/JAR, location thường là **URL đường dẫn directory hoặc archive**:

```text
file:/.../classes/                 (directory class source)
file:/.../lib/example.jar          (JAR code source)
```

Lưu ý: **CodeSource của class bên trong JAR thường là `file:...jar`**, không phải `jar:...!/p/Foo.class`. Đây là sự khác biệt quan trọng giữa **nguồn archive** và **URL tới entry/resource cụ thể**.

CodeSource có thể là `null` với các Java runtime classes, một số generated classes, hidden classes, custom loaders hoặc security domains đặc biệt. Vì vậy không gọi:

```java
// Dễ gây NullPointerException:
clazz.getProtectionDomain().getCodeSource().getLocation();
```

mà không kiểm tra các giá trị có thể rỗng.

### CodeSource có phải chứng cứ bảo mật?

Không. Một custom `SecureClassLoader` có thể tự tạo:

```java
new java.security.CodeSource(
        claimedUrl, (java.security.cert.Certificate[]) null);
```

và gắn URL đó vào class bytes đọc từ một nơi khác. Đoạn này là snippet cần `claimedUrl` trong context và **không xác minh bytecode thực sự được lấy từ URL đã nêu**. Với audit an toàn, kiểm chứng artifact/hash/signatures/source qua pipeline đáng tin, không chỉ ghi lại `CodeSource`.

### Đừng tự động biến mọi URL thành Path

`Path.of(location.toURI())` chỉ có nghĩa khi URI scheme và file-system provider tương ứng hỗ trợ. Một `file:` code source có thể chuyển thành `Path` với `Path.of(URI)` trên file system mặc định; `jar:` và `jrt:` có semantics khác, thường không chuyển trực tiếp sang `new File(...)`.

Xem `URL.getProtocol()` trước; giữ `URL` nếu mục đích chỉ là chẩn đoán.

## 6. Class resource URL: dấu hiệu file:, jar:, jrt:

`Class.getResource(String)` tìm resource bằng URL:

```java
Class<?> clazz = /* class đã tìm được */;
String classResourceName = "/" +
        clazz.getName().replace('.', '/') + ".class";
java.net.URL classResource = clazz.getResource(classResourceName);
System.out.println(classResource);
```

Đây là snippet; `clazz` cần định nghĩa trước. Dấu `/` đầu chuỗi nghĩa đường dẫn resource **tuyệt đối từ root**. Cách xây tên này phù hợp với **lớp thường có binary name**; với array, primitive, hidden/generated classes hoặc custom representation, không thể giả định luôn có resource `.class`.

Các URL phổ biến:

| Nguồn | Resource URL minh họa | `getProtocol()` |
| --- | --- | --- |
| Directory trên classpath/custom URLClassLoader | `file:/work/out/demo/Widget.class` | `file` |
| Entry trong JAR | `jar:file:/work/plugins/widget.jar!/demo/Widget.class` | `jar` |
| Runtime image JDK 9+ | `jrt:/java.base/java/lang/String.class` | `jrt` |

Phần sau ký tự `!` trong `jar:...!/entry` là tên **entry bên trong archive**, không phải path file riêng có thể `Files.readAllBytes(Path.of(...))` theo default filesystem.

Các URL là **ví dụ**, path thực tế khác theo máy và JDK. Trên Windows, URL có thể có drive letter và escaping; không ghép chuỗi `"file:" + path` thủ công.

### Resource URL có chắc là bytes dùng để định nghĩa Class<?> không?

**Không phải lúc nào cũng chắc.** `clazz.getResource(...)` thực hiện **resource lookup**, trong khi class loading dùng cơ chế `loadClass`/`findClass`/`defineClass`. Một custom loader có thể:

- Định nghĩa class từ bytes tạo trực tiếp trong memory nhưng không expose resource nào.
- Sửa/transform bytes trước khi `defineClass`, trong khi resource URL vẫn trỏ bản gốc.
- Nạp class qua một nguồn nhưng `findResource` lại dùng mapping khác.
- Trả resource từ parent/delegated source mà không đúng class object muốn khảo sát.

Nếu có **hai JAR chứa resource/class cùng tên**, `getResource` có thể trả một URL theo delegation/order policy của loader. Một URL hiện có không chứng minh bytecode của class đang chạy được nạp từ URL đó khi class đã được define trước hoặc loader có strategy khác.

Cách suy luận tốt: so sánh **defining loader + module + CodeSource + class resource + class-load logs**; khi không đồng nhất, kiểm tra loader implementation và transformation path thay vì coi một URL đơn lẻ là chân lý.

## 7. Thí nghiệm Java 17+: ba nguồn directory, JAR và runtime image

Ta sẽ tự tạo hai class:

- `demo.dir.FromDirectory` biên dịch vào `out/dir`.
- `demo.jar.FromJar` được đóng gói trong `plugin.jar`.
- `java.lang.String` có sẵn trong `java.base` runtime image của JDK 17+.

Ứng dụng `OriginDemo` chạy từ `out/app`, tạo một `URLClassLoader` với đường tìm gồm **directory URL** và **JAR URL**. Parent là platform loader, nhờ vậy hai demo classes được custom loader tự định nghĩa.

### Cấu trúc project

```text
src/
  dir/
    demo/dir/FromDirectory.java
  jar/
    demo/jar/FromJar.java
  app/
    OriginDemo.java
out/
  dir/         (javac tạo)
  jar/         (javac tạo)
  app/         (javac tạo)
plugin.jar     (jar tạo)
```

**File `src/dir/demo/dir/FromDirectory.java`:**

```java
package demo.dir;

public final class FromDirectory {
    public String value() {
        return "directory";
    }
}
```

**File `src/jar/demo/jar/FromJar.java`:**

```java
package demo.jar;

public final class FromJar {
    public String value() {
        return "jar";
    }
}
```

**File `src/app/OriginDemo.java`:**

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public class OriginDemo {
    private static URL resourceFor(Class<?> type) {
        String path = "/" + type.getName().replace('.', '/') + ".class";
        return type.getResource(path);
    }

    private static URL sourceFor(Class<?> type) {
        ProtectionDomain pd = type.getProtectionDomain();
        CodeSource cs = pd == null ? null : pd.getCodeSource();
        return cs == null ? null : cs.getLocation();
    }

    public static void main(String[] args) throws Exception {
        URL dir = Path.of("out/dir")
                .toAbsolutePath().toUri().toURL();
        URL jar = Path.of("plugin.jar")
                .toAbsolutePath().toUri().toURL();

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{dir, jar},
                ClassLoader.getPlatformClassLoader())) {

            Class<?> directory = Class.forName(
                    "demo.dir.FromDirectory", false, loader);
            Class<?> archive = Class.forName(
                    "demo.jar.FromJar", false, loader);

            System.out.println("directory loader owned: "
                    + (directory.getClassLoader() == loader));
            System.out.println("directory resource protocol: "
                    + resourceFor(directory).getProtocol());
            System.out.println("directory CodeSource matches: "
                    + dir.equals(sourceFor(directory)));

            System.out.println("jar loader owned: "
                    + (archive.getClassLoader() == loader));
            System.out.println("jar resource protocol: "
                    + resourceFor(archive).getProtocol());
            System.out.println("jar resource inside archive: "
                    + resourceFor(archive).toExternalForm()
                       .contains("!/demo/jar/FromJar.class"));
            System.out.println("jar CodeSource matches: "
                    + jar.equals(sourceFor(archive)));

            System.out.println("loader search URLs: "
                    + loader.getURLs().length);

            Class<?> jdk = String.class;
            System.out.println("runtime module: "
                    + jdk.getModule().getName());
            System.out.println("runtime loader null: "
                    + (jdk.getClassLoader() == null));

            URL jrt = resourceFor(jdk);
            System.out.println("runtime resource protocol: "
                    + (jrt == null ? "null" : jrt.getProtocol()));
            System.out.println("runtime resource module path: "
                    + (jrt != null
                       && jrt.toExternalForm().contains(
                            "java.base/java/lang/String.class")));
            System.out.println("runtime CodeSource absent: "
                    + (sourceFor(jdk) == null));
        }
    }
}
```

### Biên dịch và đóng gói

Đứng ở thư mục gốc có `src` (chạy trên Java 17+):

```bash
javac --release 17 -d out/dir src/dir/demo/dir/FromDirectory.java
javac --release 17 -d out/jar src/jar/demo/jar/FromJar.java
jar --create --file plugin.jar -C out/jar .
javac --release 17 -d out/app src/app/OriginDemo.java
java -cp out/app OriginDemo
```

Chương trình ứng dụng **không** thêm `out/dir` hoặc `plugin.jar` vào `-cp`. Bởi vậy không vô tình cho application loader nạp hai class này trước custom loader. Đường `out/dir` được tạo bởi `Path.toUri().toURL()` để có directory URL đúng; `plugin.jar` là URL trỏ file JAR.

**Stdout chính xác đã biên dịch và kiểm chứng với `javac --release 17`, chạy trên OpenJDK 24:**

```text
directory loader owned: true
directory resource protocol: file
directory CodeSource matches: true
jar loader owned: true
jar resource protocol: jar
jar resource inside archive: true
jar CodeSource matches: true
loader search URLs: 2
runtime module: java.base
runtime loader null: true
runtime resource protocol: jrt
runtime resource module path: true
runtime CodeSource absent: true
```

### Vì sao ba kiểu nguồn được nhận ra?

1. **Directory class:** `URLClassLoader` đọc `out/dir/demo/dir/FromDirectory.class`; URL của class resource có `file:` protocol, còn `CodeSource.location` bằng root `out/dir/`. Class thực sự do custom loader define.
2. **JAR class:** `plugin.jar` chứa entry `demo/jar/FromJar.class`. Resource URL có `jar:...!/demo/jar/FromJar.class`; `CodeSource.location` là URL tới **toàn bộ plugin.jar**. Class do cùng custom loader define.
3. **Runtime class:** `String.class` thuộc `java.base`, do bootstrap loader quản lý (`getClassLoader() == null`). Resource lookup trả `jrt:`; `CodeSource` của lớp này không được công bố theo kiểu file/JAR trong demo.
4. **`getURLs().length == 2`** chỉ nói loader cấu hình hai search URLs. Không có nghĩa toàn JVM chỉ có hai nguồn classes; `String` vẫn được nạp từ runtime image qua bootstrap.
5. `Class.forName(name, false, loader)` yêu cầu JVM nạp/tìm class nhưng không buộc chạy static initializer. Test tập trung **provenance**, không dựa vào tác dụng phụ của initialization.

Các path thực tế phụ thuộc nơi chạy, nên bài in ra **protocol và các phép kiểm tra Boolean** thay vì hard-code đường dẫn tuyệt đối.

### Những gì ví dụ KHÔNG chứng minh

- Không chứng minh `CodeSource` là chữ ký/xác thực nguồn đáng tin.
- Không chứng minh mọi custom loader đều cung cấp resource `.class`.
- Không chứng minh `getURLs()` liệt kê class đã load; nó là cấu hình search path.
- Không kiểm tra trường hợp hai JAR có cùng tên class/resource hoặc class được transform trước define.
- Không đo được chính xác thời gian/class unloading khi loader đóng.

Muốn biết loader thật đã đọc entry nào trong production, kết hợp logging và kiểm tra framework loader implementation.

## 8. Runtime image jrt: là gì?

Từ Java 9, JDK/JRE image dùng **modular runtime image**. Các lớp Java nền tảng thường không còn được phát hành trong một `rt.jar` chuẩn như Java 8. Với API Java 9+, tài nguyên module được biểu diễn dưới dạng URI/URL scheme **`jrt:`**.

Ví dụ:

```text
jrt:/java.base/java/lang/String.class
jrt:/java.logging/java/util/logging/Logger.class
```

Cấu trúc logic:

```text
jrt:/
  java.base/
    java/lang/String.class
  java.logging/
    java/util/logging/Logger.class
```

Đây là **không gian đường dẫn logic của runtime image**, không phải thông báo rằng trên disk có một thư mục thực `java.base` với các tệp `.class` thông thường. Với HotSpot/OpenJDK runtime images, các module data thường được lưu trong image file `lib/modules`; định dạng này khác JAR/ZIP truyền thống.

**Đừng chuyển `jrt:/java.base/...` sang `new File(...)` rồi thử đọc.** Nếu cần duyệt dữ liệu runtime module có quyền truy cập, sử dụng jrt filesystem provider:

```java
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

// Snippet: Java 9+ trên JDK hỗ trợ jrt file system:
URI uri = URI.create("jrt:/");
FileSystem fs = FileSystems.getFileSystem(uri);
Path stringClass = fs.getPath(
        "/modules/java.base/java/lang/String.class");
System.out.println(java.nio.file.Files.exists(stringClass));
```

Đây là snippet minh họa trong method có `throws IOException` hoặc xử lý ngoại lệ phù hợp; `getFileSystem` phụ thuộc provider/file system đã được tạo/có sẵn trong môi trường. Đừng hard-code layout vật lý hay mong `jrt` dùng được trên Java 8.

### Runtime image khác module JAR

Một **named module** có thể đến từ:

- Runtime image (`jrt:`, ví dụ `java.base`).
- Modular JAR trên module path (`file:`/`jar:` về artifact).
- Exploded module directory trên module path.
- ModuleLayer tùy chỉnh với nguồn classes riêng.

Vì vậy `clazz.getModule().isNamed() == true` **không đủ** để kết luận class thuộc JDK runtime image. Cần xem **module name và resource/source** cùng runtime provenance. Ngược lại, một Java class trong plugin JAR có thể thuộc unnamed module khi được URLClassLoader nạp từ classpath-style search path.

## 9. Class resource, ClassLoader.getResource và duplicate resources

Các phương thức:

```java
// Snippet với Class<?> clazz:
java.net.URL viaClass =
        clazz.getResource("/demo/Widget.class");

ClassLoader loader = clazz.getClassLoader();
java.net.URL viaLoader = loader == null ? null
        : loader.getResource("demo/Widget.class");
```

Hai tên resource khác nhau về cú pháp:

- `Class.getResource("/p/Foo.class")`: dấu `/` đầu báo **absolute** resource name.
- `ClassLoader.getResource("p/Foo.class")`: tên resource dùng path với dấu `/`, **không bắt đầu bằng `/`**.
- `Class.getResource("Foo.class")`: **relative** theo package của chính class nhận lời gọi.
- `ClassLoader.getSystemResource(...)` dùng system class loader và không phải lời giải phổ quát cho custom loader khác.

Với named modules, resource lookup chịu **module encapsulation** và có ngoại lệ đối với resources tên kết thúc bằng `.class` theo API; resource bình thường trong package đóng có thể bị chặn. Đừng dùng một resource `config.yml` bị `null` để kết luận module/class không được load.

Khi có trùng resource names trên nhiều URL/JAR, `ClassLoader.getResources(String)` có thể liệt kê nhiều URL được tìm thấy (theo policy delegation/visibility) để debug:

```java
// Snippet: loader là ClassLoader đang khảo sát:
java.util.Enumeration<java.net.URL> urls =
        loader.getResources("demo/Widget.class");
while (urls.hasMoreElements()) {
    System.out.println(urls.nextElement());
}
```

**Không phải danh sách mọi class JVM đã define.** `getResources` trả những resources có thể tìm theo tên, không phải list class objects. Nếu một loader đã define `Widget` từ JAR A, sau đó thứ tự resource đổi hoặc class file trong JAR A bị thay, kết quả hiện tại không nhất thiết là bytes chính xác JVM đã dùng trước đó.

Nếu muốn nhận biết **nhiều bản class cùng tên**, log từng resource URL, kiểm tra artifact checksums và quan sát `clazz.getClassLoader()`/`CodeSource` của class thực sự được dùng.

## 10. URLClassLoader.getURLs: search path không phải provenance cuối

`URLClassLoader` cho phép đọc cấu hình URL search path:

```java
if (loader instanceof java.net.URLClassLoader urlLoader) {
    for (java.net.URL url : urlLoader.getURLs()) {
        System.out.println(url);
    }
}
```

Đây là snippet dùng pattern-matching `instanceof` (ổn định ở Java 17+), với biến `loader` kiểu `ClassLoader`. Mảng `getURLs()` gồm URLs khởi tạo và URLs được `addURL` bổ sung cho chính loader.

Nhưng:

- Parent delegation có thể trả `Class<?>` từ parent, không nằm trong search URLs của child.
- Một source URL có thể chứa hàng trăm classes; có URL chưa từng được dùng.
- Manifest Class-Path và multi-release JAR lookup có semantics riêng.
- Class đã được define vẫn là class đó; thêm URL mới **không** thay bytecode của class ấy.
- Loader có thể không phải URLClassLoader, đặc biệt built-in app loader ở Java 9+.

**Nếu câu hỏi là “Class X thực sự đến từ đâu?” thì `clazz`, không phải `loader.getURLs()`, là điểm xuất phát.**

## 11. Khi nào class-loading log giúp xác nhận source?

Trong HotSpot JDK 17+, có thể bật `-Xlog:class+load=info` **trước khi tái hiện** để ghi sự kiện load và source của lớp liên quan:

```bash
java -Xlog:class+load=info -cp out/app OriginDemo
```

Một dòng thường chứa binary class name và `source:`; với JDK classes còn có thể thấy `shared objects file` từ CDS thay vì một `jrt:` URL. Đừng ép URL ở log trùng với `Class.getResource` hay `CodeSource.location`: đó là các tín hiệu khác nhau. Nếu thiếu event, có thể class đã load trước mốc ghi log. Khi source từ custom loader, module layer hoặc instrumentation chưa rõ, kết hợp `Class<?>` thực tế, loader identity và source metadata. Chi tiết cách bật/lọc log và phân biệt load/init/unload nằm ở hai bài chuyên biệt về Unified Logging.
## 12. Dữ liệu có thể không nằm trên disk: generated/hidden/custom classes

Một số frameworks tạo bytecode ngay trong memory (proxies, lambda implementations, hidden classes, code generation). Chúng có thể:

- Không có `.class` tương ứng ở thư mục/JAR thông thường.
- Không expose class resource qua `Class.getResource(...)`.
- Có `CodeSource.location == null` hoặc metadata không trỏ đến một file thực.
- Có tên class mang cấu trúc đặc biệt không phù hợp xây resource path một cách mù quáng.
- Được định nghĩa bởi custom loader từ bytes đọc qua database/network/transformation pipeline.

Một lớp tên `com.example.GeneratedProxy` không nhất thiết tương ứng với tệp `com/example/GeneratedProxy.class`. `getResource` trả `null` không chứng minh class là lỗi hoặc không thể load: chính `Class<?>` đang tồn tại là bằng chứng nó đã được JVM biết tới.

Với **arrays/primitives**:

```java
System.out.println(int.class.getClassLoader());
System.out.println(String[].class.getClassLoader());
```

Cả hai có thể `null` trên runtime thông thường, vì primitive classes không do user-defined loader tạo; array class có loader semantics theo component type. Đừng cố tìm `[Ljava/lang/String;.class` như một JAR entry thật.

Nếu chương trình sử dụng generated classes, cách tốt nhất là bật **diagnostic mode của bytecode generator** để dump/ghi provenance (chỉ khi được quyền và không chứa secrets), theo dõi loader.defineClass calls và lưu metadata build/version riêng.

## 13. Khi nào có thể tin source và khi nào cần kiểm chứng thêm?

Một checklist trước khi kết luận “class đến từ JAR A”:

1. **Đã có Class<?> đang thực sự được dùng chưa?** Nếu chỉ có class name, bạn chưa biết loader instance.
2. **Defining loader có đúng expected loader không?** `clazz.getClassLoader()` cùng identity check.
3. **Module name là gì?** Named module có thể từ runtime image, module JAR, exploded directory hoặc custom layer.
4. **Resource URL protocol là gì?** `file:`, `jar:`, `jrt:` hay `null`.
5. **CodeSource location là gì?** Có null không? `file:...jar` hay `file:...classes/`?
6. **Class-loading log có ghi sự kiện tương ứng?** Log có thể bắt đầu trước khi bạn cấu hình nếu class đã nạp từ trước.
7. **Có duplicate class/resource ở nhiều JARs không?** Dùng `getResources` hoặc dependency diagnostics.
8. **Có transformation/instrumentation không?** Bytes trong archive có thể khác bytes JVM định nghĩa.
9. **Artifact có thể bị thay sau khi JVM load không?** Nguồn đang có trên disk không đảm bảo vẫn là bytes đã load.
10. **Bạn đang điều tra lỗi runtime hay bảo mật?** CodeSource là metadata/diagnostics, không phải chứng thực provenance.

Trong kiến trúc plugin, lưu bản ghi khi kích hoạt gồm `pluginId`, checksum artifact, loader identity, module identity, version, cùng tên class entry có thể giúp debug hơn chỉ in `getURLs()` vào lúc xảy ra lỗi.

## 14. Lỗi thường gặp và cách xử lý

| Lỗi / giả định sai | Hậu quả | Cách sửa |
| --- | --- | --- |
| Dùng `System.getProperty("java.class.path")` để xác định mọi class JVM | Không bao gồm toàn bộ modules/custom loaders. | Bắt đầu từ `Class<?>`, loader, module, source. |
| Dùng `new File(resource.toURI())` với `jar:`/`jrt:` | `IllegalArgumentException` hoặc không có filesystem path thông thường. | Giữ URL/stream; dùng JarFile hoặc jrt FileSystem khi cần. |
| Thấy `CodeSource == null` thì kết luận lớp bị lỗi | Runtime/hidden/custom classes có thể không có location. | Phân tích module, resource URL, logs. |
| Thấy `getResource == null` thì nghĩ class không tồn tại | Resource lookup có semantics khác class definition. | Dùng `Class<?>`, loader/module; kiểm tra source custom loader. |
| Thấy `file:...jar` trong CodeSource và mong protocol `jar` | CodeSource thường trỏ archive, resource trỏ entry. | Phân biệt archive location vs entry URL. |
| Trông đợi Java 17 `String.class` nằm trong `rt.jar` | Runtime image Java 9+ không dùng legacy rt.jar như Java 8. | Xem `jrt:/java.base/...`. |
| Đổi loader khi chỉ muốn xem source của một class | Tạo namespace class khác, không đại diện class đang chạy. | Inspect đúng Class object. |
| Ép system loader sang URLClassLoader | Java 9+ không bảo đảm subtype. | Chỉ cast nếu `instanceof`; dùng class/module/provenance APIs. |
| Gọi `getURLs` rồi coi URL đầu tiên chứa class | Parent/ordering/JAR metadata có thể thay kết quả. | Xác định loader và source thực của class. |
| Parse URL bằng chuỗi `split("!")` không kiểm tra | Các URI schemes và encoding khác nhau. | Dùng `JarURLConnection`/`URI` APIs thích hợp, xử lý `null`. |
| In đủ paths vào log production công khai | Có thể lộ cấu trúc máy/chỗ triển khai. | Sanitize và giới hạn quyền xem logs. |
| `CodeSource` có URL nên bytes chắc có chữ ký hợp lệ | Custom loader có thể tự gán URL. | Xác minh nguồn/signature/hash độc lập. |
| Source filename ở disk đã đổi nhưng class đang chạy vẫn “bản mới” | Class đã define từ trước không tự cập nhật. | Reload với loader mới theo plugin architecture, quản lý identity. |
| Không xét multi-release JAR | JVM có thể chọn versioned entry theo runtime. | Kiểm tra manifest/`META-INF/versions` và loader semantics. |

## 15. Quy trình điều tra một lỗi dependency/plugin thực tế

**Tình huống:** App gặp `NoSuchMethodError: com.vendor.lib.Util.newMethod()` dù bạn đã thêm một JAR “đúng phiên bản” vào startup script.

### Bước A — tìm caller/callee class đang thực sự chạy

Từ debugger hoặc đoạn log có quyền:

```java
// Snippet: class VendorUtil được resolve trong app:
Class<?> util = com.vendor.lib.Util.class;
System.out.println("class: " + util.getName());
System.out.println("loader: " + util.getClassLoader());
System.out.println("module: " + util.getModule());
```

Đây là snippet phụ thuộc vendor class tồn tại trong project; không phải code của demo chính.

### Bước B — đọc CodeSource an toàn với null

```java
java.security.ProtectionDomain pd = util.getProtectionDomain();
java.security.CodeSource cs =
        pd == null ? null : pd.getCodeSource();
System.out.println("source: " +
        (cs == null ? "<not reported>" : cs.getLocation()));
```

Nếu hiển thị `file:/opt/app/lib/util-1.2.jar` trong khi bạn mong `util-2.0.jar`, đã có dấu hiệu **runtime chọn JAR cũ**. Nếu `CodeSource` không có, cần chuyển sang event log/module data thay vì tự đoán.

### Bước C — tra resource duplicates

```java
ClassLoader loader = util.getClassLoader();
if (loader != null) {
    var entries = loader.getResources(
            "com/vendor/lib/Util.class");
    while (entries.hasMoreElements()) {
        System.out.println("candidate: " + entries.nextElement());
    }
}
```

Kết quả có thể có nhiều candidate resources; thứ tự tìm resource không nhất thiết là chứng cứ bytecode nào đã được define bởi custom loader.

### Bước D — xác định chính runtime đang chạy

Kiểm tra `java -version` của **process thực** (server, container, IDE run configuration), dependency tree, loader hierarchy và `-Xlog:class+load=info` nếu JVM HotSpot hỗ trợ. `getURLs` hoặc `java.class.path` đơn lẻ không mô tả toàn bộ resolution/module graph.

### Bước E — sửa đúng nguồn/class loading policy

- Loại JAR phiên bản cũ **khỏi đúng loader path**.
- Căn chỉnh dependencies trong build và deployment artifacts.
- Nếu class đã được define trong JVM, cần restart hoặc loader generation mới theo thiết kế; chỉ copy JAR vào folder không cập nhật class đang chạy.
- Với plugin child loaders, kiểm tra parent-first delegation có trả bản API/dependency từ host trước không.
- Nếu class đến từ runtime module, không cố thêm JAR trùng `java.*` để override.
- Chạy test xác nhận phiên bản API/method và load-source logs sau khi sửa.

Đừng nhảy đến giải pháp `ClassLoader#addURL` hoặc ép system loader: nó có thể không tác động tới loader dùng để resolve class đang lỗi.

## 16. Tự kiểm tra

1. **Muốn biết class nào thật sự đang chạy, nên bắt đầu từ tên string hay `Class<?>`?**  
   Từ `Class<?>` được code thực sử dụng, vì nó gắn với defining loader và module.

2. **`clazz.getClassLoader() == null` thường có ý nghĩa gì với `String.class`?**  
   `String` do bootstrap loader định nghĩa, không phải JVM “quên” class.

3. **`CodeSource.location` cho class JAR và resource URL có cùng protocol không?**  
   Không bắt buộc. Với `URLClassLoader` trong demo: code source là `file:...plugin.jar`, class resource là `jar:file:...plugin.jar!/entry`.

4. **URL class resource `file:/tmp/classes/p/X.class` gợi ý nguồn nào?**  
   Một class-file resource trong directory tree trên filesystem; cần đối chiếu defining loader và CodeSource.

5. **`jrt:/java.base/java/lang/String.class` có phải file JAR trên disk không?**  
   Không. Nó là đường dẫn logic vào Java runtime image module `java.base`.

6. **Named module chắc chắn nằm trong runtime image?**  
   Không. Named module có thể là modular JAR/exploded module trên module path hoặc custom layer.

7. **Nếu `CodeSource` null nhưng `Class<?>` tồn tại, đó có phải class loading thất bại?**  
   Không. Nguồn có thể không được công bố qua CodeSource.

8. **`URLClassLoader.getURLs()` trả hai URL có nghĩa loader đã nạp hai class?**  
   Không. Nó liệt kê search path được cấu hình.

9. **`Class.getResource` luôn trả đúng bytes JVM đã define?**  
   Không. Loader có thể transform bytes, resource lookup khác path hoặc class được generate.

10. **Tại sao nên dùng `clazz.getModule()` khi chẩn đoán JDK 17+?**  
    Nó xác định module chứa class, giúp phân biệt runtime module, named module ứng dụng và unnamed modules.

11. **Có thể chuyển mọi URL `jar:` hoặc `jrt:` thành `new File(...)` không?**  
    Không. Cần scheme-specific API hoặc stream/filesystem provider tương ứng.

12. **`-Xlog:class+load=info` có phải chuẩn Java bắt buộc trên mọi JVM vendor?**  
    Không. Đây là diagnostic của JVM có Unified Logging (đặc biệt OpenJDK/HotSpot); tùy implementation.

13. **Chỉ thấy `Class.forName` tìm được class, có chắc resource `Foo.class` tìm được không?**  
    Không. Class lookup và resource lookup là hai API với hợp đồng/semantics riêng.

14. **Nếu loader đã nạp phiên bản cũ của một class, copy JAR mới có tự thay runtime type không?**  
    Không. Một defining loader không tự redefine class khi file trên disk bị thay.

## 17. Tham khảo chính thức

- [Java SE 21 — Class](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): `getClassLoader`, `getModule`, `getResource` và runtime Class object.
- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): delegation, `getResource`, `getResources` và module encapsulation đối với resources.
- [Java SE 21 — URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): directory/JAR URL search path, `getURLs`, `findClass`.
- [Java SE 21 — CodeSource](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/CodeSource.html): URL location và certificates/signers metadata.
- [Java SE 21 — ProtectionDomain](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/ProtectionDomain.html): code source và security domain properties.
- [Java SE 21 — Module](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Module.html): `getName`, `isNamed`.
- [Java SE 21 — FileSystems](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileSystems.html): `getFileSystem(URI)` cho file systems phù hợp, gồm jrt provider trong môi trường hỗ trợ.
- [Java SE 21 — java launcher](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html): `-Xlog:class+load`, Unified Logging và diagnostics.
- [JDK 9 — JEP 220 Modular Run-Time Images](https://openjdk.org/jeps/220): mô hình runtime image thay cho các archive `rt.jar` truyền thống.
- [JVMS Java SE 21 `5.3](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): class identity theo binary name và defining loader.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-diagnostics-004">Liệt kê tài nguyên trùng tên và xác định thứ tự được chọn</a>

<details>
<summary>Xem chi tiết</summary>

# Liệt kê tài nguyên trùng tên và xác định thứ tự được chọn

## 1. Vì sao cấu hình đúng vẫn đọc ra dữ liệu sai?

Trong một ứng dụng Java có nhiều JAR, những file dưới cùng resource path có thể tồn tại đồng thời, ví dụ `config/settings.properties`, `log4j2.xml`, `META-INF/services/com.example.Service` hoặc `sample/diagnostics/order.txt`. Host, plugin và hai phiên bản dependency có thể mang **những bản khác nhau với cùng một tên tài nguyên**.

Nếu code chỉ gọi:

~~~java
URL selected = loader.getResource("config/settings.properties");
~~~

thì nó nhận **một URL** hoặc `null`. Việc này **không có nghĩa chỉ một file tồn tại trên classpath**. Loader có thể đã chọn resource của parent trước file thuộc plugin.

Muốn biết có bao nhiêu ứng viên, dùng:

~~~java
Enumeration<URL> candidates =
        loader.getResources("config/settings.properties");
while (candidates.hasMoreElements()) {
    System.out.println(candidates.nextElement());
}
~~~

**Phân biệt bốn câu hỏi:**

1. `getResource(name)` — resource **đầu tiên được chọn** là URL nào?
2. `getResources(name)` — **mọi resource cùng tên** mà loader nhìn thấy, với thứ tự enumeration, là những URL nào?
3. `findResource(name)` và `findResources(name)` — loader tìm được gì **trong chính nguồn của mình** khi không thêm bước tra cứu parent mặc định?
4. `getResourceAsStream(name)` — bytes thực tế được đọc từ **resource được chọn** là gì?

Bài thuộc nhóm **Chẩn đoán**, trình bày logic resource lookup của ClassLoader, cách phân biệt parent-first với child-local search, cách xác định JAR nào đang thắng, giới hạn module/resource encapsulation, `ServiceLoader` và các chiến lược xử lý duplicate resources. Ví dụ Java 17+ được biên dịch, đóng JAR và chạy thật với 12 dòng stdout kiểm chứng.

## 2. Resource path, URL và classpath root

### 2.1. Resource name không phải filesystem path

Tên resource dùng dấu `/` và được hiểu tương đối với một classpath/resource root:

~~~text
sample/diagnostics/order.txt
META-INF/services/com.example.spi.Processor
com/example/defaults.json
~~~

Cùng tên `sample/diagnostics/order.txt` có thể có những vị trí vật lý khác nhau:

~~~text
file:/app/classes/sample/diagnostics/order.txt
jar:file:/app/lib/a.jar!/sample/diagnostics/order.txt
jar:file:/app/lib/b.jar!/sample/diagnostics/order.txt
~~~

Các đường trên chỉ minh họa URL. `jar:file:...!/` là dạng URL chỉ một entry trong JAR; dấu `!/` không thuộc tên resource logic mà caller truyền cho `getResource`.

Resource cũng có thể xuất hiện thông qua custom loader, nested-JAR-aware launcher hoặc module runtime image, không nhất thiết có `Path` file bình thường.

### 2.2. Trùng tên không đồng nghĩa trùng bytes

Hai URL khác nhau có thể chứa hai phiên bản cấu hình khác nhau, hoặc hai bản có nội dung giống hệt. Muốn biết, hãy **đọc bytes** hay tính checksum. Chỉ so URL strings không đủ kết luận hai streams giống nhau.

Trong một hệ plugin:

~~~text
Application ClassLoader
  sample/diagnostics/order.txt = HOST

Child URLClassLoader
  URL #1 a.jar
    sample/diagnostics/order.txt = JAR_A
  URL #2 b.jar
    sample/diagnostics/order.txt = JAR_B
~~~

Việc có ba bản cùng tên không phải lỗi định dạng JAR. Quy tắc **lựa chọn một bản**, **đọc tất cả** hoặc **merge** thuộc API và framework đang tiêu thụ resource.

### 2.3. Trùng resource không phải duplicate class definition

Nhiều JAR cùng có `config/app.properties` hoàn toàn có thể hợp lệ. Nhưng nếu hai JAR cùng có `com/example/Foo.class`, chương trình còn có câu hỏi về class identity và nguồn code thực sự đã được định nghĩa.

`ClassLoader.getResources("com/example/Foo.class")` có thể là một tín hiệu chẩn đoán để tìm class files trùng, nhưng **không bảo đảm chính URL đó là bytes đã tạo `Class<?>` đang chạy**: loaders có thể transform bytecode, dùng memory buffers, class caches hoặc custom module lookup. Khi debug class, kết hợp `clazz.getClassLoader()`, `clazz.getModule()` và provenance phù hợp.

## 3. Các API liệt kê và chọn resource

### 3.1. `ClassLoader.getResource(String)`

~~~java
URL url = loader.getResource("sample/diagnostics/order.txt");
if (url == null) {
    System.out.println("not found");
} else {
    System.out.println(url);
}
~~~

Đây là đoạn mã minh họa dùng biến `loader` trong method. Theo Javadoc Java SE 21, implementation mặc định tìm **parent loader trước**; nếu parent `null` thì tìm đường của bootstrap/VM built-in loader; sau khi parent không thấy mới gọi `findResource` của current loader.

Do đó, một resource ở parent có thể **che** resource cùng tên ở child khi gọi API chọn một bản.

### 3.2. `ClassLoader.getResources(String)`

~~~java
Enumeration<URL> found = loader.getResources(
        "sample/diagnostics/order.txt");
while (found.hasMoreElements()) {
    URL candidate = found.nextElement();
    System.out.println(candidate);
}
~~~

Method này khai báo `throws IOException`. **Khi không có file nào**, nó trả `Enumeration<URL>` **rỗng**, không phải `null`.

Implementation mặc định duyệt **toàn bộ URL ở parent** trước, rồi ghép **toàn bộ URL của `findResources` tại loader hiện tại**. Đây là khác biệt chính so với `getResource`: bạn nhìn thấy cả các bản thua trong cuộc chọn.

Javadoc khuyến nghị custom loaders bảo đảm `getResources`/`getResource` nhất quán, để phần tử đầu tiên của enumeration tương ứng URL `getResource` trả về. Khi loader override hai phương thức bằng chiến lược riêng, **phải kiểm chứng** điều đó thay vì xem như quy luật áp dụng cho mọi custom loader.

### 3.3. `ClassLoader.resources(String)` — dạng Stream từ Java 9

~~~java
try (java.util.stream.Stream<URL> stream =
        loader.resources("sample/diagnostics/order.txt")) {
    stream.forEach(System.out::println);
}
~~~

Đây là snippet Java 17 trong method; implementation mặc định tạo Stream từ `getResources`. Nó không phải `Files.walk` và không tự liệt kê mọi file trên classpath, mà chỉ tìm **cùng tên đã truyền**.

### 3.4. `ClassLoader.getResourceAsStream(String)`

~~~java
try (InputStream in =
        loader.getResourceAsStream("sample/diagnostics/order.txt")) {
    if (in == null) {
        System.out.println("not found");
    } else {
        System.out.println(in.readAllBytes().length);
    }
}
~~~

Snippet cần `java.io.InputStream` và xử lý IOException. Method này đọc **một bản được chọn** theo search order tương đương `getResource`, không liệt kê duplicates. Nếu muốn kiểm tra nội dung **từng bản**, duyệt `getResources` và mở `URL.openStream()` trong `try-with-resources`.

### 3.5. `findResource` và `findResources`

Đây là protected hooks mà subclasses như `URLClassLoader` triển khai cho việc tìm resource trong nguồn **riêng**:

~~~java
final class InspectableLoader extends URLClassLoader {
    InspectableLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    URL localFirst(String name) {
        return findResource(name);
    }

    Enumeration<URL> localAll(String name) throws IOException {
        return findResources(name);
    }
}
~~~

Đây là một **snippet class**, cần imports đầy đủ. Nó không đổi policy resource lookup: chỉ expose hai hooks để so sánh với `getResource` và `getResources`.

**Quan trọng:** `findResources` không có nghĩa “`getResources` nhưng không trả duplicates”. Nó chỉ không tự thêm bước tìm parent của phương thức mặc định. Nó vẫn có thể trả nhiều URLs nếu child có nhiều URL roots cùng tên.

### 3.6. Bảng tổng hợp

| API | Trả kết quả | Thứ tự/tập tìm theo mặc định | Dùng khi |
|---|---|---|---|
| `getResource(name)` | `URL` hoặc `null` | Parent trước, rồi current loader | Xem bản được chọn |
| `getResources(name)` | `Enumeration<URL>` | Parent candidates rồi local candidates | Liệt kê duplicates |
| `resources(name)` | `Stream<URL>` | Dựa trên `getResources` | Stream processing |
| `getResourceAsStream(name)` | `InputStream` hoặc `null` | Đọc bản được `getResource` chọn | Xem bytes được tiêu thụ |
| `findResource(name)` | Protected `URL`/`null` | Local search | Loader implementation/debug |
| `findResources(name)` | Protected enumeration | Local search | Debug child-only candidates |
| `SomeClass.class.getResource(name)` | `URL`/`null` | Relative/absolute + module context | File đi kèm class |
| `ClassLoader.getSystemResource(name)` | `URL`/`null` | System/built-in path | System resources |

## 4. Thứ tự nào thắng trong URLClassLoader?

### 4.1. Parent-first không thể bị thay chỉ bằng đảo URL của child

Giả sử:

~~~text
Parent loader:
  order.txt -> HOST

Child loader:
  URL #1: a.jar -> JAR_A
  URL #2: b.jar -> JAR_B
~~~

Kết quả theo default policy:

~~~text
child.getResource(name)  -> HOST
child.getResources(name) -> HOST > JAR_A > JAR_B
child.findResource(name) -> JAR_A
child.findResources(name)-> JAR_A > JAR_B
~~~

Nếu đổi thứ tự URL của child thành B rồi A:

~~~text
child.getResource(name)  -> HOST
child.getResources(name) -> HOST > JAR_B > JAR_A
child.findResource(name) -> JAR_B
child.findResources(name)-> JAR_B > JAR_A
~~~

Lý do: **parent đã thắng trước khi child được hỏi** ở `getResource`. Sửa thứ tự URL chỉ có tác động khi tới bước tra local resources của child.

### 4.2. Thứ tự URL trong chính URLClassLoader là có hợp đồng

Theo constructor của Java SE 21 `URLClassLoader`, URLs được search **theo thứ tự đưa vào**, sau bước search ở parent. Trong ví dụ `URL[] {jarA,jarB}`, child-local resource `JAR_A` đứng trước `JAR_B`; đổi array đảo vị trí.

Nhưng không suy luận rằng mọi framework classloader có cùng chính sách. Các hệ OSGi, servlet/container loaders, plugin engines hoặc Spring Boot launchers có thể dùng custom delegation và protocol/packaging.

### 4.3. `parent=null` không phải “tách quyền” hoặc “không có Java API”

Một `URLClassLoader` có parent `null` vẫn có thể nhờ bootstrap/VM built-in search nạp Java platform classes. Nó chỉ **không dùng application parent** theo cấu hình đó. Không coi `null` parent là security sandbox.

Trong thí nghiệm, vì bootstrap không có resource demo, loader này chọn `JAR_A` trực tiếp, sau đó `JAR_B`.

## 5. Chương trình thực hành Java 17+ có kết quả kiểm chứng

Ví dụ có bốn file text nhưng chỉ ba bản có tên trùng. Tất cả nội dung là nhãn ngắn, giúp chứng minh thứ tự mà không in absolute URL phụ thuộc máy.

### 5.1. Cây thư mục

~~~text
demo/
├── src/demo/host/ResourceOrderDemo.java
├── assets/
│   ├── a/sample/diagnostics/order.txt  (JAR_A)
│   └── b/sample/diagnostics/order.txt  (JAR_B)
└── build/
    ├── host/
    │   ├── demo/host/ResourceOrderDemo.class
    │   ├── demo/host/local.txt          (LOCAL)
    │   └── sample/diagnostics/order.txt (HOST)
    ├── a.jar
    └── b.jar
~~~

Tên resource đang kiểm tra: `sample/diagnostics/order.txt`. `local.txt` chỉ để chứng minh quy tắc tương đối của `Class.getResource`.

### 5.2. File `src/demo/host/ResourceOrderDemo.java`

~~~java
package demo.host;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class ResourceOrderDemo {
    static final String NAME = "sample/diagnostics/order.txt";

    static String label(URL url) throws IOException {
        if (url == null) return "NONE";
        try (InputStream input = url.openStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return reader.readLine();
        }
    }

    static String labels(Enumeration<URL> found) throws IOException {
        List<String> result = new ArrayList<>();
        while (found.hasMoreElements()) {
            result.add(label(found.nextElement()));
        }
        return String.join(">", result);
    }

    static final class InspectableLoader extends URLClassLoader {
        InspectableLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        URL localFirst() {
            return findResource(NAME);
        }

        String localAll() throws IOException {
            return labels(findResources(NAME));
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader parent = ResourceOrderDemo.class.getClassLoader();
        URL jarA = Path.of("build/a.jar").toUri().toURL();
        URL jarB = Path.of("build/b.jar").toUri().toURL();

        try (InspectableLoader ab = new InspectableLoader(
                     new URL[]{jarA, jarB}, parent);
             InspectableLoader ba = new InspectableLoader(
                     new URL[]{jarB, jarA}, parent);
             InspectableLoader independent = new InspectableLoader(
                     new URL[]{jarA, jarB}, null)) {

            System.out.println("parentFirst="
                    + label(ab.getResource(NAME)));
            System.out.println("allParentFirst="
                    + labels(ab.getResources(NAME)));
            System.out.println("childOnlyFirst="
                    + label(ab.localFirst()));
            System.out.println("childOnlyAll=" + ab.localAll());

            System.out.println("reversedStillParent="
                    + label(ba.getResource(NAME)));
            System.out.println("reversedAll="
                    + labels(ba.getResources(NAME)));

            System.out.println("noAppParentFirst="
                    + label(independent.getResource(NAME)));
            System.out.println("noAppParentAll="
                    + labels(independent.getResources(NAME)));

            System.out.println("absoluteClassResource="
                    + label(ResourceOrderDemo.class
                            .getResource("/" + NAME)));
            System.out.println("relativeClassResource="
                    + label(ResourceOrderDemo.class
                            .getResource("local.txt")));

            System.out.println("missingEnumerationEmpty="
                    + !ab.getResources(
                            "sample/diagnostics/absent.txt")
                             .hasMoreElements());

            try (InputStream stream = ab.getResourceAsStream(NAME)) {
                System.out.println("streamUsesParent="
                        + (stream != null
                           && "HOST".equals(new String(
                              stream.readAllBytes(),
                              StandardCharsets.UTF_8).trim())));
            }
        }
    }
}
~~~

### 5.3. Giải thích chương trình

- `label(url)` mở đúng URL qua `openStream`, đọc dòng đầu và đóng stream.
- `labels(enumeration)` duyệt **hết** resource candidates, không chỉ resource đầu tiên.
- `InspectableLoader` chỉ thêm hai wrapper để gọi `findResource`/`findResources`; nó không override parent-first policy.
- `ab` nhận URL A trước B, parent là host application loader.
- `ba` nhận URL B trước A, parent vẫn là host application loader.
- `independent` có parent `null`; bootstrap search vẫn tồn tại nhưng không chứa resource demo.
- `Class.getResource("/" + NAME)` dùng root-relative lookup; `getResource("local.txt")` dùng relative package lookup.
- Tên nonexistent trả enumeration rỗng.
- `getResourceAsStream` được so trực tiếp với nội dung `HOST`.

Chúng ta **không dùng `getURLs()` để chứng minh URL nào thực sự thắng**; `getURLs()` chỉ cho biết search path local. Kết quả `getResource` và từng URL từ `getResources` mới cho thấy những bản được chọn/liệt kê.

### 5.4. Linux/macOS: tạo resources, đóng JAR, chạy

Trước tiên lưu file Java ở mục 5.2, rồi chạy trong directory `demo`:

~~~sh
mkdir -p src/demo/host
mkdir -p build/host/sample/diagnostics
mkdir -p build/host/demo/host
mkdir -p assets/a/sample/diagnostics
mkdir -p assets/b/sample/diagnostics

printf 'HOST'  > build/host/sample/diagnostics/order.txt
printf 'LOCAL' > build/host/demo/host/local.txt
printf 'JAR_A' > assets/a/sample/diagnostics/order.txt
printf 'JAR_B' > assets/b/sample/diagnostics/order.txt

javac --release 17 -d build/host src/demo/host/ResourceOrderDemo.java
jar --create --file build/a.jar -C assets/a .
jar --create --file build/b.jar -C assets/b .
java -cp build/host demo.host.ResourceOrderDemo
~~~

**Lưu ý:** application classpath chỉ chứa `build/host`, không chứa `build/a.jar` hay `build/b.jar`. Hai JAR do child loaders quản lý và chỉ được đưa qua `URL[]`.

### 5.5. Windows PowerShell: tạo file UTF-8 không BOM

~~~powershell
New-Item -ItemType Directory -Force src/demo/host | Out-Null
New-Item -ItemType Directory -Force build/host/sample/diagnostics | Out-Null
New-Item -ItemType Directory -Force build/host/demo/host | Out-Null
New-Item -ItemType Directory -Force assets/a/sample/diagnostics | Out-Null
New-Item -ItemType Directory -Force assets/b/sample/diagnostics | Out-Null

$enc = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText("build/host/sample/diagnostics/order.txt", "HOST", $enc)
[System.IO.File]::WriteAllText("build/host/demo/host/local.txt", "LOCAL", $enc)
[System.IO.File]::WriteAllText("assets/a/sample/diagnostics/order.txt", "JAR_A", $enc)
[System.IO.File]::WriteAllText("assets/b/sample/diagnostics/order.txt", "JAR_B", $enc)

javac --release 17 -d build/host src/demo/host/ResourceOrderDemo.java
jar --create --file build/a.jar -C assets/a .
jar --create --file build/b.jar -C assets/b .
java -cp build/host demo.host.ResourceOrderDemo
~~~

Việc dùng UTF-8 không BOM tránh nội dung text có ký tự BOM làm thay đổi nhãn. Chương trình Java không cần file manifest có `Main-Class`, vì entry point `demo.host.ResourceOrderDemo` được chỉ rõ cho `java -cp`.

### 5.6. Stdout chính xác đã được kiểm chứng

~~~text
parentFirst=HOST
allParentFirst=HOST>JAR_A>JAR_B
childOnlyFirst=JAR_A
childOnlyAll=JAR_A>JAR_B
reversedStillParent=HOST
reversedAll=HOST>JAR_B>JAR_A
noAppParentFirst=JAR_A
noAppParentAll=JAR_A>JAR_B
absoluteClassResource=HOST
relativeClassResource=LOCAL
missingEnumerationEmpty=true
streamUsesParent=true
~~~

Đây là stdout của chương trình Java thực tế đã biên dịch thành công với `javac --release 17`; test đọc tài nguyên từ host directory và hai JAR, không dựa vào suy đoán classpath order.

### 5.7. Phân tích từng kết quả

| Output | Ý nghĩa |
|---|---|
| `parentFirst=HOST` | Parent-first `getResource` chọn bản của application loader |
| `allParentFirst=HOST>JAR_A>JAR_B` | `getResources` thêm các bản của child sau parent |
| `childOnlyFirst=JAR_A` | `findResource` chọn A trong local URL list |
| `childOnlyAll=JAR_A>JAR_B` | `findResources` liệt kê local candidates A rồi B |
| `reversedStillParent=HOST` | Đảo child URL không đổi bản parent thắng |
| `reversedAll=HOST>JAR_B>JAR_A` | Đảo child URLs làm đổi phần enumerate ở child |
| `noAppParentFirst=JAR_A` | Loader parent `null` không hỏi application loader |
| `noAppParentAll=JAR_A>JAR_B` | Independent child nhìn hai JAR theo URL order |
| `absoluteClassResource=HOST` | Class-relative API với `/` đầu chọn path từ root |
| `relativeClassResource=LOCAL` | Class-relative lookup thêm package `demo/host/` |
| `missingEnumerationEmpty=true` | Không tìm thấy là enumeration rỗng |
| `streamUsesParent=true` | `getResourceAsStream` đọc bản mà parent-first đã chọn |

Kết luận từ ví dụ: **muốn biết bản thắng thì xem `getResource`; muốn tìm toàn bộ nguyên nhân cạnh tranh thì dùng `getResources`**. Không mặc định đảo URL của child sẽ override resource ở parent.

## 6. `Class.getResource` và `ClassLoader.getResource` khác nhau ở dấu slash

Một nguồn lỗi rất phổ biến là truyền cùng chuỗi cho hai API khác nhau:

~~~java
Class<?> type = demo.host.ResourceOrderDemo.class;

URL absolute = type.getResource("/sample/diagnostics/order.txt");
URL relative = type.getResource("local.txt");

ClassLoader loader = type.getClassLoader();
URL loaderAbsolute = loader.getResource(
        "sample/diagnostics/order.txt");
~~~

Đây là snippet trong method, không phải file độc lập.

- `Class.getResource("/x/y")`: có `/` đầu, class-level API hiểu là resource name tuyệt đối theo root.
- `Class.getResource("local.txt")`: không có `/`, được hiểu tương đối với package của class `demo.host`, thành `demo/host/local.txt`.
- `ClassLoader.getResource("x/y")`: tên resource tính từ root, **không** tự thêm package và thông thường **không truyền leading slash**.
- `ClassLoader.getResource("/x/y")`: không bảo đảm hoạt động như `Class.getResource("/x/y")`; tránh nhầm cú pháp.

Khi ứng dụng dùng **named modules**, `Class.getResource` còn gắn với module context và quy tắc encapsulation tương ứng, vì vậy không phải mọi trường hợp đều tương đương một cuộc tìm toàn loader.

## 7. Tài nguyên trong JPMS named modules: giới hạn nhìn thấy

### 7.1. Tìm toàn ClassLoader không luôn thấy mọi resource có trong module

Javadoc Java SE 21 `ClassLoader.getResource` và `getResources` đặt ra quy tắc đối với **named modules**. Ngoại trừ tên kết thúc bằng `.class`, resource trong package của named module chỉ được công bố qua loader-wide APIs nếu package được **opened unconditionally** theo yêu cầu API.

Điều này tạo các khả năng:

- Entry có trong modular JAR nhưng `getResources` không trả URL vì package bị encapsulated.
- Package chỉ opened cho một module đích không có nghĩa loader-wide API được phép công bố resource như unconditional open.
- `Module.getResourceAsStream` hoặc `SomeClass.class.getResource` có ngữ cảnh access riêng; xem Javadoc tương ứng.
- “Không thấy resource qua loader” có thể là **vấn đề encapsulation**, không chỉ là file thiếu.

### 7.2. Thứ tự giữa nhiều modules cùng loader có thể không xác định

Javadoc lưu ý rõ rằng khi **nhiều named modules được định nghĩa trong cùng ClassLoader** và chúng chứa resource trùng tên, **thứ tự tìm modules không được chỉ định và có thể rất khó đoán**.

Do đó, không suy output `HOST>JAR_A>JAR_B` của `URLClassLoader` demo thành một cam kết cho mọi `ModuleLayer`.

Nếu ứng dụng cần file thuộc **một named module cụ thể**, dùng API truy cập theo module hoặc thiết kế resource path duy nhất; đừng dựa vào thứ tự module enumeration không ổn định.

### 7.3. Khi chẩn đoán modules

Kiểm tra:

1. Module đã được resolve vào runtime layer chưa?
2. Class loader đang được dùng định nghĩa modules nào?
3. Resource nằm trong package có `opens` hay `exports` phù hợp? `exports` một mình không thay thế `opens` cho resource encapsulation.
4. Caller đang dùng `Class.getResource`, `ClassLoader.getResources` hay `Module.getResourceAsStream`?
5. Nhiều named modules có cùng resource path trong cùng loader không?

Nhớ rằng `ModuleLayer` và ClassLoader có quan hệ mapping, **không phải danh sách file classpath tuyến tính đơn giản**.

## 8. Trùng `META-INF/services` và Java ServiceLoader

Ví dụ:

~~~text
a.jar:
  META-INF/services/com.example.spi.Processor
    com.vendor.alpha.AlphaProcessor

b.jar:
  META-INF/services/com.example.spi.Processor
    com.vendor.beta.BetaProcessor
~~~

Đây là mô tả nội dung, không phải code Java. Hai JAR chứa **cùng resource name** là thiết kế bình thường cho Java SPI.

`ServiceLoader` có thể đọc **nhiều provider configuration files** tương ứng với một service type và tìm các provider classes được khai báo. Vì vậy:

- Không kết luận chỉ provider trong resource đầu tiên mới được dùng.
- Không xóa ngẫu nhiên các `META-INF/services` trùng tên khi đóng fat/uber JAR.
- Khi shade nhiều JAR, cần **merge provider descriptors** theo build tool/resource transformer thích hợp.
- Chú ý service class identity: nếu host và plugin có hai bản runtime interface cùng tên do loaders khác nhau định nghĩa, provider có thể báo lỗi subtype/configuration.
- Nếu nhiều providers tồn tại, ứng dụng nên có **policy chọn provider** rõ; không coi thứ tự discovery là độ ưu tiên mà framework không cam kết.

Thực tế một số resource types phải **merge**, còn config chính của ứng dụng nên **được đặt tên riêng** hoặc chọn theo một policy có căn cứ. `getResources` là công cụ phát hiện; cách xử lý phụ thuộc ngữ nghĩa resource.

## 9. Quy trình chẩn đoán trong dự án thật

### 9.1. In đúng candidates của loader được dùng ở runtime

~~~java
static void inspect(ClassLoader loader, String name)
        throws IOException {
    URL winner = loader.getResource(name);
    System.out.println("winner=" + winner);

    Enumeration<URL> all = loader.getResources(name);
    int count = 0;
    while (all.hasMoreElements()) {
        URL candidate = all.nextElement();
        System.out.println("candidate[" + count++ + "]=" + candidate);
    }
    System.out.println("count=" + count);
}
~~~

Đây là **snippet method**, cần `URL`, `Enumeration` và `IOException` imports. Gọi với **đúng loader của code gây lỗi**, không nhất thiết là system ClassLoader.

Ba loader dễ nhầm:

~~~java
ClassLoader system = ClassLoader.getSystemClassLoader();
ClassLoader context = Thread.currentThread().getContextClassLoader();
ClassLoader defining = SomeType.class.getClassLoader();
~~~

Snippet yêu cầu `SomeType` tồn tại. TCCL có thể được framework đổi theo worker/request; resource lookup từ `SomeType.class.getResource` không tự đồng nghĩa dùng TCCL. Với `ServiceLoader`, overload `load(service)` thường dựa trên TCCL, còn `load(service, loader)` dùng loader chỉ định.

### 9.2. Đọc nội dung, không chỉ in URL

~~~java
try (InputStream in = candidate.openStream()) {
    byte[] bytes = in.readAllBytes();
    System.out.println("resourceLength=" + bytes.length);
}
~~~

Đây là snippet có `candidate` URL và xử lý exceptions. Với file cấu hình có credentials, **không log toàn bộ nội dung**. Nếu muốn xác định hai candidates có cùng bytes hay không, có thể tính SHA-256 và so hashes; không coi URL string là checksum.

Với large binary resources, nên hash dạng streaming thay vì `readAllBytes` để tránh allocation không cần thiết.

### 9.3. Kiểm tra deployment, không chỉ build dependency graph

Những nguồn làm kết quả khác IDE:

- Lệnh `java -cp` hoặc `java -jar`, module path và launcher script thật.
- `Class-Path` manifest, external JAR dependencies và resource roots.
- Gradle/Maven runtime classpath khác compile/test classpath.
- Wildcard như `lib/*`: **không dựa vào thứ tự mở rộng không được cam kết**.
- Fat/shaded JAR merge/overwrite files; nested-JAR launcher có search semantics riêng.
- Parent/child loader graph trong application server/plugin container.
- Custom ClassLoader override `getResource` hoặc `getResources`.
- Cached config in memory, classloader lifecycle và TCCL trên thread pool.
- Module encapsulation, package `opens`, module layer mapping.

Nếu resource chỉ sai sau deploy, in **all candidate URLs ngay trong process deploy** và kiểm tra các JAR chứa entries qua:

~~~sh
jar --list --file path/to/app.jar
jar --list --file path/to/dependency.jar
~~~

Đây là các lệnh minh họa; thay paths bằng artifacts thực. Lệnh `jar --list` xem contents trên disk; `getResources` cho biết contents **được loader thấy trong process**. Hai tập này có thể khác vì cách launch.

### 9.4. Thứ tự điều tra đề xuất

1. Ghi **tên resource logic chính xác**, phân biệt slash/package-relative.
2. Xác định **loader được API đang lỗi sử dụng**: defining, context, system hoặc framework loader riêng.
3. Gọi `getResource` để in URL thắng.
4. Gọi `getResources` để liệt kê theo đúng thứ tự.
5. So URL với nội dung từng candidate, không chỉ tên JAR.
6. Kiểm tra parent loader search path và URL order của child.
7. Đối chiếu runtime command, repackaging/shading, module path và resource access.
8. Xem library/config framework có merge mọi candidates hay chỉ lấy một.
9. Sửa bằng **resource namespace riêng**, dependency cleanup, transformer hoặc lựa chọn URL tường minh — thay vì sửa order tùy tiện.
10. Test lại trên artifact phát hành thật và thêm regression test phát hiện duplicate sai chính sách.

### 9.5. Lưu ý về caching

Một ứng dụng có thể đọc resource một lần lúc startup rồi cache object cấu hình. Khi file disk đổi, `getResources` có thể vẫn trỏ cùng URL, trong khi ứng dụng tiếp tục dùng parsed state cũ. Một loader/JAR connection có thể có caching và lifecycle riêng; phải xem API và cấu hình launcher thực tế.

**Liệt kê URLs không đồng nghĩa reload thành công.** Nếu code/framework đã cache, cần cơ chế reload theo contract của framework, hoặc restart/recreate loader/process khi hợp lý.

## 10. Các lỗi và hiểu nhầm thường gặp

| Hiện tượng | Nguyên nhân có thể | Kiểm tra/hướng xử lý |
|---|---|---|
| `getResource` chọn bản HOST thay vì plugin | Parent-first | In parent candidates; đặt tên resource riêng |
| `getResources` có 3 URLs | Trùng tên ở parent và child | Đọc nội dung, đừng tự xóa |
| Đảo thứ tự JAR_A/JAR_B nhưng bản thắng vẫn HOST | Parent đã thắng | Thay policy/path, không chỉ đảo child URLs |
| `getResources` không thấy entry có trong modular JAR | Named module encapsulation hoặc module chưa resolve | Kiểm tra module `opens`, layer, API gọi |
| `Class.getResource("x")` trả null | Relative path bị thêm package | Dùng `"/x"` nếu muốn root-absolute |
| `ClassLoader.getResource("/x")` trả null | Leading slash không giống class-relative API | Dùng `"x"` |
| Hai `META-INF/services` cùng tên | Nhiều SPI providers hợp lệ | Merge descriptors/kiểm tra providers |
| `ServiceLoader` báo `not a subtype` | API runtime identity chia đôi giữa loaders | Shared API từ parent, inspect Class objects |
| `getResources` rỗng nhưng JAR có file | Loader không thấy JAR, path sai, module access hoặc custom policy | In loader URLs, kiểm tra archive/module |
| Chạy IDE đúng, container sai | Classpath/launcher/framework khác | In runtime URLs và actual command |
| Thay file nhưng config không đổi | Cached parsed config/JAR connection/old loader | Kiểm tra lifecycle/reload |
| Nhiều modules cùng loader, thứ tự thay đổi | Module search order không được bảo đảm | Chọn module cụ thể, tránh priority theo index |
| Tìm đúng URL nhưng nội dung không như kỳ vọng | Artifact deploy/version khác hoặc duplicate byte content | Hash từng candidate |
| `findResources` ít entries hơn `getResources` | Không bao gồm parent | Hành vi được thiết kế |

**Các nhận định sai cần nhớ:**

1. `getResource` trả một URL không chứng minh chỉ có một resource.
2. `getResources` không trả `null` khi resource vắng mặt; trả enumeration rỗng.
3. `getResourceAsStream` không tự merge nhiều bản cấu hình.
4. Parent-first mặc định không giống child-first của một số frameworks.
5. URL thứ nhất của child không thể thắng parent nếu parent đã chọn.
6. `findResources` không tự đưa parent vào enumeration.
7. Cùng resource name không buộc cùng content.
8. `Class.getResource` và `ClassLoader.getResource` không có cùng quy tắc relative name.
9. Đường `jar:file:...!/entry` là URL, không phải resource name truyền vào.
10. Resource trong named module không phải lúc nào cũng được công bố qua loader-wide lookup.
11. Thứ tự giữa modules cùng loader không được bảo đảm chung.
12. Nhiều `META-INF/services` có thể là thiết kế đúng.
13. `ClassLoader.getSystemClassLoader()` không nhất thiết là loader đang tìm resource cho plugin.
14. Thứ tự build dependencies không nhất thiết bằng URL order ở production.
15. Không nên ghi URLs chứa secrets/credentials vào logs công khai.
16. Một container thay đổi launcher có thể đổi hoàn toàn thứ tự resources.
17. Có URL resource đúng không đồng nghĩa framework đã parse và reload nó.
18. `URLClassLoader.close()` ảnh hưởng lifecycle/resources nhưng không tự reload mọi config.

## 11. Tự kiểm tra kiến thức

1. `getResource` và `getResources` trả về loại dữ liệu nào?
2. Nếu không có resource, mỗi API phản hồi ra sao?
3. Vì sao `parentFirst=HOST` khi child có cả A và B?
4. Vì sao `allParentFirst=HOST>JAR_A>JAR_B`?
5. Tại sao `findResources` của child không liệt kê HOST?
6. Đảo URL order A/B thay đổi những dòng nào trong demo?
7. Parent `null` nghĩa là không còn Java built-in class loader không?
8. `getResourceAsStream` đọc mọi bản hay một bản?
9. `Class.getResource("local.txt")` xây path gì khi class ở package `demo.host`?
10. Nên dùng tên có slash đầu với Class-level hay ClassLoader-level API?
11. Làm thế nào kiểm tra hai URL có bytes giống nhau?
12. Có nên so URL string để xác thực integrity không?
13. `ClassLoader.resources(name)` có quan hệ gì với `getResources`?
14. Tại sao không nên dựa vào thứ tự classpath wildcard?
15. Khi nào nhiều `META-INF/services` cùng tên là bình thường?
16. ServiceLoader có nhất thiết chỉ đọc descriptor đầu tiên không?
17. Named module `opens` liên quan gì tới resource lookup?
18. Vì sao không thể suy thứ tự URLClassLoader demo sang mọi ModuleLayer?
19. System, TCCL và defining loader có thể khác nhau không?
20. Trong container, cần in log những giá trị nào để biết resource bản nào thắng?
21. Thay JAR trên disk có bảo đảm parsed configuration trong memory đổi ngay không?
22. Nếu framework merge nhiều resources, lựa chọn đầu tiên còn là nguyên nhân duy nhất không?
23. Cách viết một method in tất cả candidates mà không phụ thuộc IDE là gì?
24. Khi nào nên dùng path namespace riêng thay vì đổi URL order?
25. `getResources` rỗng trong khi JAR có entry có thể do những nguyên nhân nào?

**Tiêu chí hoàn thành:** tạo ba bản `sample/diagnostics/order.txt`, chạy và giải thích đủ 12 dòng output Java 17; tự liệt kê mọi URL trùng tên, chỉ ra bản thắng và nguyên nhân theo parent/child ordering, và nhận diện giới hạn JPMS/SPI/custom loaders.

## 12. Nguồn chính thức

- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): resource path names, `getResource`, `getResources`, `findResources`, `resources`.
- [Java SE 21 — URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): parent search, thứ tự URLs truyền constructor, close.
- [Java SE 21 — Class.getResource](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getResource(java.lang.String)): relative/absolute name và module context.
- [Java SE 21 — Module.getResourceAsStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Module.html#getResourceAsStream(java.lang.String)): module resource encapsulation.
- [Java SE 21 — ServiceLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html): service provider files và discovery.
- [Java SE 21 — Java launcher](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html): classpath và module path khi chạy ứng dụng.

**Kết luận:** `getResource` chỉ chỉ ra **bản được chọn**, còn `getResources` giúp bạn thấy **toàn bộ ứng viên và thứ tự thực tế** của loader. Phải quan sát trong JVM đang chạy, với đúng loader, và xem nguồn bytes mỗi URL nếu cần. Đừng suy thứ tự từ tên file, dependency declaration hay tưởng mọi loader/JPMS module đều tìm theo cùng một quy luật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-diagnostics-005">-verbose:class và unified logging -Xlog:class+load</a>

<details>
<summary>Xem chi tiết</summary>

# Chẩn đoán class loading với -verbose:class và -Xlog:class+load trong Java 17

## 1. Vì sao cần quan sát class loading?

Khi gặp lỗi `ClassNotFoundException`, `NoClassDefFoundError`, `ClassCastException`, `NoSuchMethodError`, `LinkageError` hoặc plugin dùng nhầm phiên bản thư viện, câu hỏi quan trọng thường không phải “file `.class` có nằm đâu đó trên máy không?”, mà là:

1. **Runtime đã thực sự nạp/định nghĩa class có binary name nào?**
2. **Class đó được lấy từ nguồn nào** — JDK runtime image, application classpath, JAR, thư mục plugin?
3. **Định nghĩa thuộc class loader nào**, có phải loader mà ứng dụng kỳ vọng?
4. **Lúc nào class được load**, lúc nào class mới được initialize, và lỗi xảy ra ở bước nào?
5. Một lần gọi `loadClass` thứ hai **tái sử dụng `Class<?>` đã có** hay định nghĩa một class mới bởi loader khác?

Java 17 có hai cách chẩn đoán cơ bản:

```bash
java -verbose:class -cp out/host TraceLoadDemo
```

và:

```bash
java -Xlog:class+load=info -cp out/host TraceLoadDemo
```

Ở HotSpot/JDK 17, cách thứ hai dùng **Unified JVM Logging** với tag set `class+load`. Nó cho phép chọn mức log, đầu ra, decorators, file rotation, cùng nhiều tag khác. Cách thứ nhất là tùy chọn truyền thống còn hỗ trợ để tiện kiểm tra nhanh.

**Một chi tiết phải nhớ:** Tài liệu lệnh `java` của Oracle JDK 17 ánh xạ `-verbose:class` tương đương với:

```text
-Xlog:class+load=info,class+unload=info
```

Vì vậy, nếu chỉ sử dụng `-Xlog:class+load=info`, bạn đã chọn **load events** và **không tự yêu cầu unload events**. Dù cả hai cách có nhiều dòng giống nhau, chúng không nên bị gọi là hai chuỗi tùy chọn đồng nghĩa tuyệt đối.

## 2. Nắm ngữ nghĩa: loading, linking, initialization, unloading

Một runtime class có thể trải qua các giai đoạn khác nhau:

```text
Tên class / yêu cầu lookup
          |
          v
     LOADING
  Tìm/định nghĩa class
          |
          v
     LINKING
  Verify -> Prepare -> Resolve
  (resolution có thể được thực hiện vào những thời điểm phù hợp)
          |
          v
   INITIALIZATION
  <clinit> và static initialization
          |
          v
  Thực thi constructor/method theo nhu cầu
          |
          v
  Có thể UNLOAD khi defining loader đủ điều kiện GC
  và JVM thực sự chọn unloading
```

**Log `class+load` là quan sát quá trình class loading**, không phải log riêng của initialization. Một class có thể đã được load từ trước nhưng `static { ... }` chưa chạy.

Ví dụ:

```java
Class<?> token = Class.forName(
        "demo.trace.PluginMarker", false, pluginLoader);
```

Overload ba tham số `Class.forName(name, initialize, loader)` với `false` **không yêu cầu initialization**. Log `class+load` có thể xuất hiện cho `PluginMarker` trong khi static initializer chưa chạy. Khi method static sau đó được gọi qua reflection, class có thể được initialized.

Cũng đừng đánh đồng **unloading** với việc object instance hết scope. Theo JLS `12.7, class chỉ có thể unload khi **defining loader** đủ điều kiện được garbage collector thu gom; JVM không bảo đảm class sẽ unload trong một lần GC cụ thể. Thiếu log `class+unload` trong demo ngắn **không chứng minh memory leak**.

## 3. -verbose:class cho biết điều gì?

Tùy chọn:

```bash
java -verbose:class -cp out/host TraceLoadDemo
```

yêu cầu Java runtime xuất thông tin class loading qua cơ chế verbose. Trên HotSpot/JDK 17, nội dung thực tế có thể dùng định dạng Unified Logging, ví dụ một dòng có hình thức gần giống:

```text
[0.123s][info][class,load] demo.trace.PluginMarker source: file:/path/to/out/plugin/
```

**Đây là ví dụ dạng log**, không phải timestamp/đường dẫn/stdout cố định. Thời gian, path, dòng log hỗ trợ, decorators, module source, chính sách class loading và số class nạp thay đổi theo JDK, OS và cách chạy.

`-verbose:class` hữu ích khi:

- Cần kiểm tra nhanh liệu một binary name có xuất hiện trong log load.
- Cần nhìn nguồn class ở mức cơ bản khi chẩn đoán classpath trùng JAR.
- Muốn một option quen thuộc và chưa cần cấu hình file/rotation.
- Cần chạy lại một bài test nhỏ rồi đọc toàn bộ các sự kiện load/unload.

**Hạn chế:** Nó thường sinh rất nhiều output từ JDK classes và thư viện framework. Với production, nên lọc theo tên class và dùng Unified Logging để giới hạn đầu ra.

### Có thể dùng -verbose:class để biết chính xác loader object không?

**Không nên mặc định.** Dòng `class,load` ở mức `info` thường có tên class và source, nhưng có thể không hiển thị mọi thông tin identity/parent/loader object mà ứng dụng cần. Cùng một URL hoặc cùng binary name chưa chắc là cùng defining loader.

Trong code, xác nhận độc lập:

```java
ClassLoader actualDefiner = token.getClassLoader();
System.out.println(token.getName());
System.out.println(actualDefiner);
```

Snippet trên yêu cầu biến `token` thuộc `Class<?>`. Nếu cần kết luận identity, so sánh `tokenA == tokenB` và `token.getClassLoader() == expectedLoader`; không chỉ so sánh tên class hoặc tên loader.

## 4. Unified Logging: cú pháp tổng quan của -Xlog

JVM Unified Logging (JEP 158) cho phép cấu hình:

```text
-Xlog:<what>:<output>:<decorators>:<output-options>
```

Các phần:

| Thành phần | Vai trò | Ví dụ |
|---|---|---|
| `what` | Tag selector và mức log | `class+load=info` |
| `output` | Gửi log đến stdout/stderr hoặc file | `stdout`, `stderr`, `file=classload.log` |
| `decorators` | Thông tin kèm mỗi dòng | `uptime,level,tags` |
| `output-options` | Cấu hình output/rotation | `filecount=3,filesize=10M` |

Mẫu dùng phổ biến:

```bash
java -Xlog:class+load=info -cp out/host TraceLoadDemo
```

Tương đương một biểu diễn tường minh về đầu ra:

```bash
java -Xlog:class+load=info:stdout:uptime,level,tags -cp out/host TraceLoadDemo
```

Với `-Xlog`, output mặc định là `stdout`, và các decorators mặc định thường gồm uptime, level và tags.

Kiểm tra các tag/level/decorators mà chính JVM của bạn hỗ trợ:

```bash
java -Xlog:help
```

**Không tự đoán tag tồn tại trong mọi vendor/version**. Unified Logging thuộc triển khai HotSpot/JDK và tài liệu lệnh `java`; cách hiển thị thực tế có thể khác giữa Java distributions.

### Các mức log

Những mức chính: `off`, `error`, `warning`, `info`, `debug` và `trace`. Mức càng chi tiết thường càng nhiều dòng và chi phí logging càng cao.

```bash
java -Xlog:class+load=info -cp out/host TraceLoadDemo
java -Xlog:class+load=debug -cp out/host TraceLoadDemo
```

Theo tài liệu Oracle JDK 17, `info` dùng cho class loading thông thường; `debug` cho thông tin bổ sung tùy log site. Không bảo đảm rằng mỗi class đều có đúng một dòng `debug` hoặc rằng format của `debug` giữ nguyên giữa JDK updates.

### Tag set không phải dấu cộng logic tùy ý

Trong Unified Logging, `class+load` biểu diễn một **tập tag** gắn với một log event. Không có wildcard, selector thường khớp chính xác tag set được định nghĩa cho event.

```text
class+load=info       -> chọn tag set class+load tại info
class+unload=info     -> chọn tag set class+unload tại info
class*=debug          -> chọn các tag set có class theo wildcard
```

`class*=debug` là cách rộng hơn; nó có thể tạo rất nhiều logs khác liên quan class. Khi học hoặc chẩn đoán một lỗi cụ thể, bắt đầu bằng `class+load=info`, rồi mở rộng sau khi xác định cần thêm loại sự kiện nào.

## 5. Bảng tùy chọn cần nhớ trên JDK 17

| Tùy chọn | Quan sát chính | Trường hợp dùng |
|---|---|---|
| `-verbose:class` | Legacy verbose class information; trên HotSpot JDK 17 tương đương load + unload info | Kiểm tra nhanh |
| `-Xlog:class+load=info` | Class loading events | Xác định class nào/nguồn nào được nạp |
| `-Xlog:class+load=debug` | Loading chi tiết hơn khi log sites có dữ liệu | Điều tra sâu |
| `-Xlog:class+load=info,class+unload=info` | Load và unload | Quan sát vòng đời class loader |
| `-Xlog:class+unload=info` | Unload events thực sự xảy ra | Hot reload/metaspace, không thay thế GC roots analysis |
| `-Xlog:class+resolve=debug` | Constant-pool class/member resolution | Lỗi liên kết hoặc resolution |
| `-Xlog:class+init=info` | Class initialization log | Phân biệt load với static initialization |
| `-Xlog:class+loader+constraints=info` | Các loading constraints được ghi nhận | Nghi vấn linkage across loaders |
| `-Xlog:class+path=info` | Class path details | Nghi vấn classpath sai |
| `-Xlog:class+preorder=debug` | Load/reference preorder diagnostics | Theo dõi tham chiếu trước class load |
| `-Xlog:help` | Tùy chọn/tags mà JVM hỗ trợ | Kiểm tra runtime trước khi áp dụng |

Các ánh xạ `TraceClassLoading`, `TraceClassResolution`, `TraceClassInitialization`, `TraceLoaderConstraints` được tài liệu Java 17 mô tả qua Unified Logging. Không sao chép máy móc flags `-XX:+TraceClassLoading` từ bài hướng dẫn JDK rất cũ: những flags legacy có thể đã bị loại bỏ và làm JVM không khởi động. Chọn `-Xlog` tương ứng.

## 6. Ví dụ Java 17 đầy đủ: nạp class plugin rồi tái sử dụng cùng Class<?>

Ví dụ này cố tình đặt `demo.trace.PluginMarker` **ngoài application classpath** và đưa riêng vào một `URLClassLoader`. Khi chạy, ta thấy một sự kiện load class plugin, nhưng hai yêu cầu lookup vẫn trả **cùng `Class<?>`**.

### 6.1. Cây project

```text
src/
  plugin/
    demo/
      trace/
        PluginMarker.java
  host/
    TraceLoadDemo.java

out/
  plugin/
  host/
```

`out` chưa cần tồn tại; `javac -d` sẽ tạo output packages, còn lệnh shell bên dưới tạo root directories.

### 6.2. PluginMarker.java

Tạo file `src/plugin/demo/trace/PluginMarker.java`:

```java
package demo.trace;

public final class PluginMarker {
    static {
        System.out.println("PLUGIN_INITIALIZED");
    }

    public static String run() {
        return "PLUGIN_OK";
    }
}
```

Static initializer là marker **initialization**, khác thời điểm class được load. Class có method public để host gọi bằng reflection.

### 6.3. TraceLoadDemo.java

Tạo file `src/host/TraceLoadDemo.java`:

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class TraceLoadDemo {
    public static void main(String[] args) throws Exception {
        System.out.println("BEGIN_USER_LOOKUP");

        URL pluginRoot = Path.of("out/plugin").toUri().toURL();
        ClassLoader hostLoader =
                TraceLoadDemo.class.getClassLoader();

        try (URLClassLoader pluginLoader = new URLClassLoader(
                "demo-plugin",
                new URL[] {pluginRoot},
                hostLoader)) {

            Class<?> first = Class.forName(
                    "demo.trace.PluginMarker", false, pluginLoader);
            Class<?> second =
                    pluginLoader.loadClass("demo.trace.PluginMarker");

            System.out.println("SAME_CLASS=" + (first == second));
            System.out.println("DEFINED_BY_PLUGIN="
                    + (first.getClassLoader() == pluginLoader));

            System.out.println("BEFORE_STATIC_CALL");
            Object value = first.getMethod("run").invoke(null);
            System.out.println("RESULT=" + value);
        }

        System.out.println("END_USER_LOOKUP");
    }
}
```

Ở đây:

1. `hostLoader` chỉ có host class; không có `demo.trace.PluginMarker`.
2. `pluginLoader` dùng parent-first nhưng parent không thấy class plugin, nên `URLClassLoader` tìm file dưới `out/plugin`.
3. `Class.forName(name, false, pluginLoader)` yêu cầu load nhưng **không yêu cầu initialization**.
4. `pluginLoader.loadClass(name)` trả class đã ghi nhận qua loader đó; không định nghĩa lại class cùng binary name.
5. `first.getClassLoader() == pluginLoader` chứng minh defining loader của class plugin.
6. Reflection gọi static `run` kích hoạt initialization nếu class chưa initialized. Vì thế `PLUGIN_INITIALIZED` xuất hiện sau `BEFORE_STATIC_CALL`.
7. `URLClassLoader.close()` nhờ try-with-resources giải phóng resources của loader; nó **không có nghĩa** class unload ngay sau khi ra khỏi khối try.

### 6.4. Biên dịch và chạy không bật JVM log

Bash (Linux/macOS):

```bash
mkdir -p out/plugin out/host
javac -d out/plugin src/plugin/demo/trace/PluginMarker.java
javac -d out/host src/host/TraceLoadDemo.java
java -cp out/host TraceLoadDemo
```

Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force out/plugin,out/host
javac -d out/plugin src/plugin/demo/trace/PluginMarker.java
javac -d out/host src/host/TraceLoadDemo.java
java -cp out/host TraceLoadDemo
```

**Không thêm `out/plugin` vào `-cp`!** Nếu parent classpath nhìn thấy PluginMarker, một `URLClassLoader` parent-first có thể nhận class từ parent, làm `DEFINED_BY_PLUGIN` thành `false` và phá ý nghĩa bài test.

Stdout **xác định** khi chạy sạch theo cấu trúc trên:

```text
BEGIN_USER_LOOKUP
SAME_CLASS=true
DEFINED_BY_PLUGIN=true
BEFORE_STATIC_CALL
PLUGIN_INITIALIZED
RESULT=PLUGIN_OK
END_USER_LOOKUP
```

Các markers này dùng để đọc log. Khi bật JVM logging, các log lines có thể xen giữa markers nhưng **thứ tự logic của các markers do chương trình in vẫn đúng**.

### 6.5. Bật -verbose:class

```bash
java -verbose:class -cp out/host TraceLoadDemo
```

Khi lọc log, tìm tên:

```text
demo.trace.PluginMarker
```

Một dòng `class,load` tương tự (chỉ minh họa hình dạng):

```text
[0.080s][info][class,load] demo.trace.PluginMarker source: file:/.../out/plugin/
```

Sau đó, gần lời gọi reflection, stdout `PLUGIN_INITIALIZED` xuất hiện; nếu bật thêm `class+init`, có thể quan sát log initialization riêng. Không suy “class đã initialized” chỉ từ class-load log.

### 6.6. Bật riêng class+load

```bash
java -Xlog:class+load=info -cp out/host TraceLoadDemo
```

Tập trung vào:

- `TraceLoadDemo` được load từ `out/host` hoặc nguồn classpath tương ứng.
- `demo.trace.PluginMarker` được load từ `out/plugin`.
- Runtime cũng nạp nhiều classes thuộc JDK và thư viện cần để chạy. Đừng nhầm các dòng đó là plugin classes.

**Không có bảo đảm mỗi lời gọi `loadClass` sẽ in một log load mới.** Hai lần lookup trong demo trả lại cùng runtime `Class<?>` nên không phải hai class definitions độc lập.

### 6.7. Ghi log ra file thay vì trộn với stdout ứng dụng

Trên JDK 17 HotSpot:

```bash
java -Xlog:class+load=info:file=classload.log:uptime,level,tags \
     -cp out/host TraceLoadDemo
```

Trong PowerShell:

```powershell
java -Xlog:class+load=info:file=classload.log:uptime,level,tags -cp out/host TraceLoadDemo
```

Khi đó **stdout ứng dụng** vẫn là bảy dòng cố định ở phần 6.4, còn JVM class-load logs được ghi trong `classload.log` tại working directory.

Lọc trên Linux/macOS:

```bash
grep -F 'demo.trace.PluginMarker' classload.log
```

Hoặc PowerShell:

```powershell
Select-String -Path classload.log -Pattern 'demo.trace.PluginMarker'
```

Một class-load record phù hợp chứng minh JVM đã ghi nhận loading event của tên class ấy trong lần chạy được quan sát; không dùng duy nhất dòng này để kết luận class loader identity hay authorization.

## 7. Cách đọc một dòng Unified Logging

Một dòng log có hình dạng:

```text
[0.147s][info][class,load] demo.trace.PluginMarker source: file:/.../out/plugin/
```

Các phần:

| Thành phần | Diễn giải |
|---|---|
| `[0.147s]` | Uptime tại thời điểm event, **không phải elapsed time của thao tác load đó** |
| `[info]` | Log level |
| `[class,load]` | Tag set |
| `demo.trace.PluginMarker` | Binary name hiển thị trong record |
| `source: ...` | Nguồn bytes/module/path theo JVM logging |

**Uptime không phải thời lượng nạp lớp.** Chênh lệch giữa hai timestamps cũng có thể bao gồm các công việc khác, JIT, I/O hoặc scheduling; không nên dùng khoảng thời gian đó như một microbenchmark thời gian `ClassLoader.loadClass`.

**Source không phải chứng chỉ niềm tin.** `file:/.../plugin.jar` cho biết JVM báo nguồn class; không chứng minh JAR đã được hash/signed hay được phép thực thi. Nếu cần kiểm tra provenance, phải có cơ chế xác thực artifact riêng.

**Không chắc source là file path.** Nhiều JDK classes đến từ runtime image `jrt:/` hoặc `shared objects file` tùy CDS/JVM. Dynamic/generated classes có thể không có file nguồn thông thường.

## 8. Tách class loading khỏi initialization bằng hai loại dấu vết

Một nhầm lẫn phổ biến: “Tôi thấy class-load log khi `Class.forName(name, false, loader)` chạy, vậy static initializer đã chạy rồi.”

Không đúng. Trong ví dụ:

```text
BEGIN_USER_LOOKUP
[class,load] demo.trace.PluginMarker ...   <-- event loading
SAME_CLASS=true
DEFINED_BY_PLUGIN=true
BEFORE_STATIC_CALL
PLUGIN_INITIALIZED                      <-- static initialization của class
RESULT=PLUGIN_OK
END_USER_LOOKUP
```

Sơ đồ xen kẽ trên **minh họa**, không phải toàn bộ exact stdout/log của một JVM.

Muốn yêu cầu JVM ghi thêm events initialization:

```bash
java -Xlog:class+load=info,class+init=info \
     -cp out/host TraceLoadDemo
```

JDK 17 có mapping cũ `TraceClassInitialization` sang `-Xlog:class+init=info`. Log cụ thể của class initialization phụ thuộc VM, nên đọc marker do chương trình in cùng log `class+init` là cách kiểm tra dễ hiểu.

Các API khác nhau:

| API | Loading? | Initialization? |
|---|---|---|
| `Class.forName(name)` | Yêu cầu class loading/lookup theo loader của class gọi | **Yêu cầu** initialization |
| `Class.forName(name, false, loader)` | Yêu cầu lookup qua loader chỉ định | **Không yêu cầu** initialization |
| `loader.loadClass(name)` | Yêu cầu lookup qua loader | **Không tự yêu cầu** initialization |
| `SomeType.class` | Class literal | Không yêu cầu khởi tạo chỉ vì literal |
| Gọi active static method `SomeType.run()` | Có thể cần class đã load/link | **Có thể kích hoạt** initialization theo quy tắc JVM |

Đừng mặc định initialization luôn xảy ra “ngay sau loading”. JVM resolution/initialization có những quy tắc riêng và một số resolution có thể bị trì hoãn.

## 9. Dùng class-load log để tìm JAR bị chọn nhầm

Giả sử ứng dụng có:

```text
libs/common-v1.jar
plugins/report/lib/common-v2.jar
```

Cả hai chứa `com.example.common.Codec`. Khi plugin gặp `NoSuchMethodError`, một khả năng là plugin biên dịch với v2 nhưng runtime nạp v1 từ parent classpath.

Cách kiểm tra:

1. Chạy lại với `-Xlog:class+load=info` và giữ log.
2. Lọc theo binary name `com.example.common.Codec`.
3. Xem `source` có khớp artifact runtime kỳ vọng hay không.
4. In `Codec.class.getClassLoader()` hoặc lấy token qua plugin loader để xác nhận defining loader.
5. So sánh ABI bằng `javap -classpath ... -s -p com.example.common.Codec` trên hai artifacts.
6. Kiểm tra `parent-first` và vị trí thư viện riêng: nếu parent có cùng binary name, nó có thể che bản private.
7. Giải quyết ở dependency ownership/delegation, không sửa bằng cách bắt `NoSuchMethodError` để bỏ qua.

Lệnh bytecode inspection minh họa:

```bash
javap -classpath libs/common-v1.jar -s -p com.example.common.Codec
javap -classpath plugins/report/lib/common-v2.jar -s -p com.example.common.Codec
```

Đây là ví dụ đường dẫn và tên class cần thay phù hợp dự án. `javap` chỉ kiểm tra **artifact được chỉ định**, không tự chứng minh runtime đã dùng artifact đó; vì thế phải kết hợp class-load log.

### Cùng class name có thể xuất hiện nhiều lần trong log

Hai custom loaders có thể **định nghĩa hai runtime classes khác nhau có cùng binary name**. Không kết luận log trùng tên là VM đã nạp cùng class hai lần trong một loader.

Để xác minh identity:

```java
Class<?> a = loaderA.loadClass("com.example.common.Codec");
Class<?> b = loaderB.loadClass("com.example.common.Codec");

System.out.println(a.getName().equals(b.getName()));
System.out.println(a == b);
System.out.println(a.getClassLoader() == b.getClassLoader());
```

Snippet này cần hai loaders đã tạo. Nếu cả hai ủy quyền về cùng parent, `a == b` có thể là `true`. Nếu chúng thực sự tự định nghĩa riêng, `a == b` là `false` dù tên giống nhau. Dòng class log mức info không thay thế phép so sánh class token.

## 10. Debug parent delegation: log class loading nói được gì?

Mặc định `ClassLoader.loadClass(String, boolean)`:

1. Kiểm tra `findLoadedClass(name)`.
2. Nếu chưa có, hỏi parent; nếu parent null, hỏi bootstrap theo contract.
3. Nếu chưa tìm thấy, gọi `findClass(name)`.
4. Resolve class khi `resolve` được yêu cầu.

**Class-load event không phải nhật ký từng nhánh của thuật toán.** Không nhìn thấy `findClass` event không có nghĩa method không từng được gọi; cũng không thể đọc một dòng `class,load` rồi suy rằng parent lookup đã thất bại bao nhiêu lần.

Nếu cần hiểu logic delegation của **custom loader chính mình**, thêm instrumentation có chọn lọc vào loader implementation:

```java
@Override
protected Class<?> findClass(String binaryName)
        throws ClassNotFoundException {
    System.out.println("FIND_CLASS_ATTEMPT=" + binaryName);
    return super.findClass(binaryName);
}
```

Đây là **snippet minh họa** nằm trong subclass `ClassLoader`; method mặc định của superclass ném `ClassNotFoundException`, nên không phải loader đọc JAR thực tế. Trong production, tránh ghi logs vô hạn hoặc rò tên class nhạy cảm.

Có thể kết hợp:

- `-Xlog:class+load=info` để thấy các classes được VM ghi nhận.
- Logs ở `findClass`/factory để thấy nguồn lookup dự kiến.
- `Class.getClassLoader()` để xác nhận defining loader.
- `ClassLoader.getParent()` để kiểm tra parent chain.
- `Thread.currentThread().getContextClassLoader()` khi API sử dụng TCCL.

**Không đánh đồng TCCL và defining loader.** TCCL là trạng thái gắn với thread, trong khi `Class.getClassLoader()` phản ánh loader định nghĩa runtime class. `ServiceLoader.load(service)` dùng TCCL, nhưng `Class.forName(String)` overload một tham số dùng loader của class gọi.

## 11. Kết hợp sự kiện unload với bằng chứng về loader

Muốn xem HotSpot thực sự ghi nhận class unloading, bật riêng `-Xlog:class+unload=info` hoặc kết hợp với `class+load=info`. Việc `URLClassLoader.close()` không tạo cam kết class sẽ unload ngay; JVM chỉ có thể unload khi defining loader đủ điều kiện GC và runtime thực hiện unloading.

Nếu kiểm tra redeploy/metaspace, so nhiều chu kỳ `load → stop → GC/unload` và đối chiếu loader còn sống, references từ threads/TCCL, listeners hoặc caches. **Không thấy unload event trong một lần chạy ngắn chưa chứng minh leak.** Bài tiếp theo dành riêng cho `class+init`/`class+unload`, còn bài JFR/heap dump giải thích cách truy GC roots để tìm holder thật sự.
## 12. Ghi Unified Logs ra file, đặt decorators và rotation

Production logs thường cần giới hạn dung lượng:

```bash
java -Xlog:class+load=info:file=classload-%p-%t.log:uptime,level,tags:filecount=3,filesize=10M \
     -cp out/host TraceLoadDemo
```

- `file=classload-%p-%t.log`: tạo file log; `%p` là PID, `%t` là timestamp khởi động theo Java launcher logging.
- `uptime,level,tags`: giữ thông tin thời gian, mức độ, tag.
- `filecount=3,filesize=10M`: cấu hình log rotation theo kích thước với các giới hạn **xấp xỉ**, không phải SLA lưu giữ chính xác từng byte.
- Đường dẫn log phải có quyền ghi phù hợp; đừng cho người không đáng tin sửa log nếu cần điều tra sự cố.

Log rotation có thể **ghi đè/xóa log cũ** theo cấu hình. Nếu điều tra bắt buộc lưu đầy đủ, chọn retention ngoài runtime hoặc tập hợp log theo quy trình an toàn.

### Ghi vào stderr

```bash
java -Xlog:class+load=info:stderr:uptime,level,tags \
     -cp out/host TraceLoadDemo
```

Việc chọn stdout/stderr/file giúp tách JVM diagnostics với application output. Cần nhớ rằng một số môi trường hoặc log aggregation có thể ghép lại các streams, khiến thứ tự hiển thị liên luồng khác thứ tự flush thực tế.

### Quản lý lượng dữ liệu

Nên bật verbose class logs trong môi trường có kiểm soát để tránh:

- Ghi log hàng chục nghìn classes không liên quan.
- Tăng chi phí I/O và dung lượng đĩa.
- Làm khó phân tích do log rotation.
- Tiết lộ cấu trúc package/artifact paths hoặc thông tin môi trường trong logs.

Ở lần đầu, chạy bài test nhỏ, lọc theo target class. Chỉ tăng mức `debug` hoặc phạm vi tags khi cần thiết.

## 13. JDK 8 và JDK 17: đừng dùng nhầm option legacy

Nhiều bài viết cũ gợi ý:

```text
-XX:+TraceClassLoading
-XX:+TraceClassUnloading
-XX:+TraceClassResolution
```

Các JVM options lịch sử này đã có những thay đổi/loại bỏ trong các JDK mới. Trên JDK 17, tài liệu Oracle hướng dẫn dùng Unified Logging:

```text
-XX:+TraceClassLoading    -> -Xlog:class+load=info
-XX:+TraceClassUnloading  -> -Xlog:class+unload=info
-XX:+TraceClassResolution -> -Xlog:class+resolve=debug
```

Bảng là **mapping khái niệm của legacy options**, không phải các flags nên đưa đồng thời vào lệnh chạy. Trước khi chép một command từ tài liệu cũ, xác nhận version:

```bash
java -version
java -Xlog:help
```

Đặc biệt khi dùng shell scripts deploy qua nhiều Java versions, không áp đặt `-Xlog` của HotSpot/JDK 17 cho runtime cũ không hỗ trợ. Quản lý runtime options theo environment.

## 14. Chẩn đoán exception bằng đúng loại dữ liệu

| Triệu chứng | Class-load log giúp gì? | Còn phải xem gì? |
|---|---|---|
| `ClassNotFoundException` | Tên class đã từng được ghi nhận loading chưa; source có hợp lý không | Loader visibility, binary name, classpath/module path |
| `NoClassDefFoundError` | Những classes/dependencies liên quan đã load từ đâu | Root cause, linkage/init failure, nested `Caused by` |
| `NoSuchMethodError` | Library class thực tế đến từ artifact nào | ABI/method descriptor bằng `javap`; phiên bản compile-time |
| `ClassCastException` | Có thể thấy cùng binary name xuất hiện từ nhiều sources | Runtime `Class<?>` identity và defining loaders |
| `LinkageError` / loader constraint | Tham gia những classes nào, loading order | `class+loader+constraints`, method descriptors, shared API |
| `ExceptionInInitializerError` | Class đã load trước khi lỗi init | `class+init`, `<clinit>`, static dependencies |
| `ServiceConfigurationError` | Provider class có được load không | `ServiceLoader`, TCCL, provider descriptor/module, assignability |
| `IllegalAccessError` / reflection access | Class được load từ module nào | Module readability, `exports`/`opens`, Java access modifiers |
| Metaspace tăng sau reload | Load/unload events qua nhiều cycles | GC roots, old loaders, heap dump, jcmd/JFR |

**Cần phân biệt “log không có class name” và “class chắc chắn không tồn tại”.** Một class có thể chưa từng được yêu cầu nạp, được nạp trong process/run khác, hoặc JVM logging selector không ghi sự kiện bạn cần. Đừng kết luận chỉ từ việc grep không có kết quả.

### Ví dụ phân tích NoSuchMethodError

Giả sử `com.acme.Parser` bản 1 có `parse(String)`, còn bản 2 có thêm `parse(byte[])`. Plugin compile với v2 nhưng runtime parent trả bản 1.

Một hướng điều tra:

1. Ghi stack trace với method gây lỗi.
2. Bật `-Xlog:class+load=info`; lọc `com.acme.Parser`.
3. Đọc `source` của event, kiểm tra JAR/version ở đó.
4. So sánh `javap -s -p` trên JAR v1/v2.
5. Xem parent loader có JAR v1 và plugin loader parent-first không.
6. Sửa dependency layout hoặc plugin isolation theo contract, không catch `NoSuchMethodError` rồi tiếp tục.

Ví dụ chỉ minh họa tình huống ABI mismatch; `NoSuchMethodError` còn có nguyên nhân khác liên quan binary compatibility.

## 15. Công thức quan sát theo mục tiêu

**Muốn biết class nào được JVM nạp:**

```bash
java -Xlog:class+load=info -cp out/host TraceLoadDemo
```

**Muốn so sánh nhanh với tùy chọn truyền thống:**

```bash
java -verbose:class -cp out/host TraceLoadDemo
```

**Muốn cùng ghi load và unload:**

```bash
java -Xlog:class+load=info,class+unload=info -cp out/host TraceLoadDemo
```

**Muốn phân biệt load và initialization:**

```bash
java -Xlog:class+load=info,class+init=info -cp out/host TraceLoadDemo
```

**Muốn xem class resolution chi tiết:**

```bash
java -Xlog:class+resolve=debug -cp out/host TraceLoadDemo
```

**Muốn tách JVM log khỏi stdout ứng dụng:**

```bash
java -Xlog:class+load=info:file=classload.log:uptime,level,tags -cp out/host TraceLoadDemo
```

**Muốn kiểm tra tag thực sự được runtime hỗ trợ:**

```bash
java -Xlog:help
```

Một quy trình tiết kiệm thời gian thường là: **bật info → lọc binary name → xác định source → xác minh defining loader trong code → chỉ mở thêm debug/resolve/init/constraints khi bằng chứng yêu cầu**.

## 16. Lỗi thường gặp khi đọc log

| Ngộ nhận | Diễn giải chính xác |
|---|---|
| “`-verbose:class` chỉ log load” | Trong mapping của Oracle JDK 17, nó tương đương load và unload mức info |
| “`-Xlog:class+load=info` và `-verbose:class` hoàn toàn giống nhau” | Cách thứ hai còn bao gồm unload trong mapping JDK 17 |
| “Mỗi `loadClass` call tương ứng một dòng class-load mới” | Loader có thể tái sử dụng `Class<?>` đã nạp |
| “Class xuất hiện trong log tức là static initializer đã chạy” | Load và initialize là hai giai đoạn khác |
| “Source path chắc chắn cho biết defining loader object” | Source thường chỉ là nguồn bytes; cần thêm class token/loader identity |
| “Không thấy class trong log nghĩa class không tồn tại” | Class có thể chưa được yêu cầu hoặc log cấu hình khác |
| “Có hai dòng cùng binary name nghĩa duplicate definition trong một loader” | Hai loaders khác nhau có thể định nghĩa cùng tên |
| “Uptime chênh lệch là thời gian load class” | Uptime là thời điểm log, không phải elapsed load cost |
| “Đóng URLClassLoader thì phải có unload event ngay” | Close không quyết định GC/unloading ngay |
| “`class+load=debug` in luôn mọi quan hệ parent/child” | Mức debug phụ thuộc log sites/JDK; xác nhận bằng API/profiler |
| “Cứ bật `class*=trace` ở production là tốt” | Khối lượng log/performance/privacy có thể không chấp nhận |
| “Flags `TraceClassLoading` cũ vẫn chắc chắn chạy trên 17” | Dùng ánh xạ Unified Logging trong tài liệu JDK 17 |

## 17. Bài tập tự kiểm tra

**Câu 1.** `-verbose:class` trong Oracle JDK 17 tương đương cấu hình Unified Logging nào?

**Đáp án:** `-Xlog:class+load=info,class+unload=info` theo tài liệu lệnh `java`.

**Câu 2.** Dùng `-Xlog:class+load=info` có bắt buộc xuất hiện unload events không?

**Đáp án:** Không. Selector này dành cho tag set class+load. Muốn unload, thêm `class+unload=info`.

**Câu 3.** Trong demo, `Class.forName(name, false, pluginLoader)` rồi `pluginLoader.loadClass(name)` trả cùng `Class<?>`. Có bắt buộc hai load events riêng không?

**Đáp án:** Không. Loader có thể dùng class đã nạp/ghi nhận; hai lookup calls không tương đương hai class definitions.

**Câu 4.** `-Xlog:class+load` xuất hiện trước `PLUGIN_INITIALIZED` cho biết điều gì?

**Đáp án:** Class đã được load trước khi static initialization chạy. Không thể suy init đã hoàn tất chỉ từ class-load event.

**Câu 5.** Vì sao log `source: file:/.../plugin.jar` không chứng minh artifact đáng tin?

**Đáp án:** Nó cho biết source theo JVM, không kiểm tra digest/signature/approval của artifact.

**Câu 6.** Nếu plugin nạp `Parser` từ parent v1 thay vì child v2, log có thể giúp gì?

**Đáp án:** Nó có thể chỉ ra source của runtime `Parser`; kết hợp defining loader và `javap` để xác nhận ABI/version mismatch.

**Câu 7.** Vì sao hai custom loaders có thể khiến một binary name xuất hiện nhiều lần trong log?

**Đáp án:** Vì runtime type identity còn có defining loader; mỗi loader có thể định nghĩa một class riêng cùng tên.

**Câu 8.** Có unload log ngay sau `URLClassLoader.close()` là điều chắc chắn không?

**Đáp án:** Không. Close giải phóng resources; unloading phụ thuộc loader có thể GC và quyết định của VM.

**Câu 9.** Một dòng `[0.205s][info][class,load] ...` nghĩa load class mất 205 ms?

**Đáp án:** Không. `0.205s` là uptime của JVM tại thời điểm event, không phải duration của thao tác.

**Câu 10.** Muốn điều tra lỗi `ExceptionInInitializerError` nên kết hợp tag nào?

**Đáp án:** `class+load` với `class+init`, cùng stack trace/static initializer của class.

**Câu 11.** Nếu `grep` không tìm thấy tên class, kết luận class chắc chắn chưa được load được không?

**Đáp án:** Chưa đủ nếu chưa chắc runtime log đang đầy đủ cho process/run và selector liên quan; class có thể chưa từng được yêu cầu hoặc log lọc/rotation.

**Câu 12.** Muốn ghi class load vào file, tách khỏi stdout của ứng dụng, dùng ví dụ nào?

**Đáp án:** `-Xlog:class+load=info:file=classload.log:uptime,level,tags`.

## 18. Tóm tắt và nguồn chính thức

**Năm điểm chính:**

1. **`-verbose:class`** là cách quan sát nhanh và trên HotSpot/JDK 17 có mapping sang **class load + unload info**.
2. **`-Xlog:class+load=info`** là selector rõ ràng cho sự kiện **class loading**; có thể chọn `debug`, output, decorators và rotation.
3. **Class load khác initialization, resolution và unload.** Đọc logs đúng giai đoạn, kết hợp stack trace và các tag phù hợp.
4. **Dòng log không phải bằng chứng đầy đủ về class identity hoặc trust.** Kiểm tra `Class.getClassLoader()`, `Class<?> ==`, classpath/module path và artifact digest khi cần.
5. **Chẩn đoán theo giả thuyết:** xác định binary name/source rồi mở rộng logging có mục tiêu, không bật mọi tag vô hạn.

Tài liệu chuẩn Java 17:

- [Oracle Java SE 17 — The java Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-verbose:class`, `-Xlog`, tags/levels/outputs và mapping legacy tracing flags.
- [OpenJDK JEP 158 — Unified JVM Logging](https://openjdk.org/jeps/158): cấu trúc log levels, tags, outputs, decorators.
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, delegation và `findLoadedClass`.
- [Java SE 17 — Class.forName](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): overload có tham số `initialize` và loader.
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): nạp class/resources từ URL và `close()`.
- [JLS 17 — `12 Execution](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html): loading, linking, initialization và unloading.
- [JVMS 17 — `5 Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): quy tắc JVM về class lifecycle.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-diagnostics-006">Theo dõi khởi tạo và dỡ lớp bằng các log tag phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Theo dõi khởi tạo và dỡ lớp bằng log tag phù hợp trong Java

## 1. Mục tiêu: phân biệt đúng các sự kiện trong vòng đời lớp

Khi phân tích ứng dụng Java có nhiều thư viện, plugin, framework hoặc cơ chế reload, câu hỏi “lớp này đã khởi tạo chưa?” khác hẳn “JVM đã nạp lớp chưa?” hay “lớp đã được dỡ khỏi JVM chưa?”. **Class loading**, **class initialization** và **class unloading** là các sự kiện có điều kiện, thời điểm và ý nghĩa khác nhau. JVM HotSpot cung cấp **Unified Logging** qua tùy chọn `-Xlog` để quan sát chúng theo các nhóm tag riêng.

Bài này tập trung vào **chọn đúng log tag, tái hiện sự kiện, đọc log có căn cứ và hạn chế của bằng chứng** trên HotSpot JDK 17+. Những câu lệnh dưới đây dựa trên cú pháp `java` của JDK 17; JVM khác như OpenJ9 có thể dùng hệ thống log và tùy chọn khác.

| Sự kiện | Ý nghĩa | Log HotSpot cần xem |
|---|---|---|
| **Loading** | JVM lấy/định nghĩa một lớp hoặc interface; class đã có định danh runtime theo tên và defining loader | `class+load` |
| **Linking/resolution** | JVM verify, prepare hoặc resolve các symbolic reference | `class+resolve` khi cần phân tích sâu; không đồng nhất với initialization |
| **Initialization** | JVM thực hiện quy trình khởi tạo class/interface, gồm thực thi các initializer tĩnh khi có | `class+init` |
| **Unloading** | JVM ghi nhận dỡ một lớp/interface đã nạp khi vòng đời và điều kiện thu hồi cho phép | `class+unload` |
| **Garbage collection** | Chu kỳ GC; dữ liệu thời gian/loại thu gom để đối chiếu với unloading | `gc` hoặc các nhóm `gc*` khi cần |
| **Metaspace** | Bối cảnh sử dụng/cấp phát bộ nhớ metadata của JVM | `gc+metaspace` ở level phù hợp và các công cụ thống kê khác |

Không suy luận “có dòng load ⇒ chắc chắn đã init” hoặc “GC đã chạy ⇒ mọi class không dùng nữa đã unload”. Một lớp có thể được **load nhưng chưa init**; một class loader đã mất tham chiếu mạnh cũng chỉ làm các lớp do nó định nghĩa **đủ điều kiện** để có thể unload, chứ không tạo cam kết về thời điểm.

### Sơ đồ ý nghĩa

```text
Nhận yêu cầu dùng lớp
       │
       ▼
   Loading ──────────────► [class,load]
       │
       ▼
   Linking / resolution
       │
       ▼
Active use kích hoạt? ── Không ──► Có thể vẫn chưa init
       │ Có
       ▼
   Initialization ───────► [class,init]
       │
       ▼
 Lớp/loader tồn tại, được ứng dụng sử dụng
       │
       ▼
 Loader/lớp đủ điều kiện thu hồi?
       │ Có, và JVM thực hiện unloading
       ▼
   Unloading ────────────► [class,unload]
```

Đây là sơ đồ để phân loại **ý nghĩa log**; nó không khẳng định thời gian của mọi bước đều tuyến tính, hay mỗi bước đều xuất hiện chính xác một dòng. JVM có quyền trì hoãn resolution; một số log có thể được ghi bởi các thread khác nhau.

## 2. Chọn đúng `-Xlog` tag và level

Unified Logging có cú pháp tổng quát:

```text
-Xlog[:[tag-selection][:[output][:[decorators][:output-options]]]]
```

Trong `tag-selection`:

- `class+load` nghĩa là **tập tag kết hợp** `class` và `load`.
- Dấu phẩy trong `class+load=info,class+init=info,class+unload=info` nghĩa là **nhiều lựa chọn** độc lập.
- `=info` chọn các thông điệp từ level `info` trở lên trong ngưỡng severity của lựa chọn đó; `debug`/`trace` chi tiết hơn, có thể làm log tăng rất mạnh.
- Nếu không có `*` sau tổ hợp tag, selection khớp **chính xác tập tag**. `class*=debug` khớp các tập tag chứa `class` và sẽ rộng/ồn hơn.
- `-Xlog:help` hiển thị tag, level, decorator và output-option của **chính runtime Java đang chạy**. Nếu một lựa chọn không được HotSpot nhận diện, đọc error và trợ giúp thay vì giả định cờ hoạt động.

### Cấu hình tối thiểu theo câu hỏi

**Chỉ biết lớp nào được nạp:**

```bash
java -Xlog:class+load=info -cp . InitTrace
```

**Chỉ theo dõi khi khởi tạo:**

```bash
java -Xlog:class+init=info -cp . InitTrace
```

**Chỉ theo dõi class unloading:**

```bash
java -Xlog:class+unload=info -cp . UnloadTrace
```

**Theo dõi cả ba loại sự kiện và đối chiếu GC:**

```bash
java -Xlog:class+load=info,class+init=info,class+unload=info,gc=info -cp . InitTrace
```

`UnloadTrace` và `InitTrace` được tạo trong các phần thực hành bên dưới. Trên HotSpot, `-verbose:class` tương đương logging class load và class unload ở mức `info` theo tài liệu JDK 17; **không thay thế `class+init`** khi đề bài là quan sát khởi tạo.

Chú ý `-Xlog:class=info` không đồng nghĩa ba selection trên. Tag `class` đứng một mình là một tag set khác với `class+load` và `class+init`. Khi điều tra sự kiện cụ thể, dùng đúng tổ hợp được tài liệu hỗ trợ.

### Ghi ra file để giảm nhiễu stdout

```bash
java -Xlog:class+load=info,class+init=info,class+unload=info,gc=info:file=class-events-%p.log:time,uptime,pid,tid,level,tags:filecount=5,filesize=10M -cp . InitTrace
```

Trong cấu hình này:

- `file=class-events-%p.log` ghi log ra file có PID, thuận tiện phân biệt nhiều JVM; thư mục chạy cần quyền ghi.
- `time,uptime,pid,tid,level,tags` bổ sung thời gian đồng hồ, thời gian kể từ khi JVM chạy, PID/TID, level và tags để tương quan sự kiện.
- `filecount=5,filesize=10M` giới hạn dung lượng/rotation theo cơ chế Unified Logging; việc rotation được mô tả là xấp xỉ kích thước cấu hình.
- Log JVM đi vào file; lệnh `System.out.println` của chương trình vẫn ra stdout, giúp tách hai luồng quan sát.

Muốn xem log và ứng dụng cùng console mà không cần file, giữ `:stdout:uptime,level,tags`:

```bash
java -Xlog:class+load=info,class+init=info,class+unload=info:stdout:uptime,level,tags -cp . InitTrace
```

Trên Windows PowerShell, có thể đặt toàn bộ tham số `-Xlog:...` trong cặp dấu nháy khi shell/parser yêu cầu. Cú pháp dùng dấu phẩy là **một đối số JVM**, không phải các đối số tùy ý.

## 3. Thí nghiệm Java 17+: thấy rõ load trước init

Để quan sát initialization, cần thiết kế chương trình sao cho lớp mục tiêu được load trước, rồi mới có **active use**. Một `Class.forName` có overload `initialize=false` giúp thể hiện điểm khác biệt.

Tạo file **`InitTrace.java`**:

```java
public final class InitTrace {
    static final class Target {
        // Constant variable: compiler có thể inline giá trị.
        static final int CONSTANT = 7;

        // Không phải compile-time constant.
        static int counter = 42;

        static {
            System.out.println("Target.<clinit> executed");
        }

        static void ping() {
            System.out.println("Target.ping");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("1. Class.forName(false)");
        Class.forName("InitTrace$Target", false,
                InitTrace.class.getClassLoader());

        System.out.println("2. Constant = " + Target.CONSTANT);
        System.out.println("3. Read counter");
        System.out.println("Counter = " + Target.counter);

        System.out.println("4. Call ping");
        Target.ping();

        System.out.println("5. Class.forName(true) after init");
        Class.forName("InitTrace$Target", true,
                InitTrace.class.getClassLoader());
        System.out.println("6. Done");
    }
}
```

Biên dịch và chạy với HotSpot JDK 17+:

```bash
javac InitTrace.java
java -Xlog:class+load=info,class+init=info -cp . InitTrace
```

Chương trình tự in chính xác các dòng sau, **nằm xen giữa những dòng logging khác của JVM**:

```text
1. Class.forName(false)
2. Constant = 7
3. Read counter
Target.<clinit> executed
Counter = 42
4. Call ping
Target.ping
5. Class.forName(true) after init
6. Done
```

**Cách phân tích:**

1. `Class.forName("InitTrace$Target", false, loader)` yêu cầu load lớp nhưng **không yêu cầu initialize**. Có thể thấy dòng log `[class,load] InitTrace$Target ...` trước khi chương trình in “3. Read counter”.
2. `Target.CONSTANT` là một **constant variable** kiểu nguyên thủy với hằng biên dịch. Java thường inline `7` vào caller nên chỉ đọc hằng này **không kích hoạt Target initialization**.
3. `Target.counter` là trường static không phải constant variable: lần đọc đầu là **active use** và kích hoạt initialization, chạy initializer tĩnh. JVM có thể ghi `[class,init] ... InitTrace$Target ...` gần lúc chương trình in `Target.<clinit> executed`.
4. `Target.ping()` gọi sau khi lớp đã được init nên không chạy lại static initializer.
5. `Class.forName(..., true, ...)` tiếp tục yêu cầu init nhưng lớp đã được init rồi; không có lần chạy `<clinit>` thứ hai cho cùng runtime class.

Tên nội bộ của nested class là `InitTrace$Target`, khác cách viết Java source `InitTrace.Target`. Class file `InitTrace$Target.class` được tạo cùng `InitTrace.class` sau khi biên dịch.

**Đọc event log cẩn thận:** Dòng `[class,init]` của HotSpot có thể có chỉ số, tên lớp ở dạng `InitTrace$Target` và địa chỉ VM. Những con số/địa chỉ đó **phụ thuộc phiên chạy**, không phải output cố định. Không cần tìm đúng một format text bất biến; kiểm tra **tag, tên lớp, mốc thời gian và thứ tự tương đối**. Đặc biệt, log init có thể báo sự kiện cho các lớp nền tảng của JDK mà code không trực tiếp nhắc tới.

### Vì sao `Target.class` và `Class.forName(false)` không tự chứng minh init?

Một literal `Target.class` có thể lấy `Class` object mà không cần thực hiện class initialization; còn `Class.forName(name, false, loader)` có ý nghĩa rõ ràng là **không initialize tại lệnh đó**. Đây là lý do số dòng `[class,load]` không thể dùng làm số lớp đã init.

Các thao tác thường kích hoạt init theo JLS `12.4.1` gồm: tạo instance của class, gọi static method do class khai báo, đọc/ghi static field do class khai báo **ngoại trừ đọc constant variable**, và một số thao tác reflection. Khi initialize class, JVM xử lý superclass chưa init và những superinterface phù hợp theo đặc tả; thứ tự log có thể chứa nhiều lớp hơn mục tiêu.

Một lớp chỉ initialize **một lần cho mỗi runtime class identity** trong đời sống hợp lệ của nó. Nếu loader khác định nghĩa lớp cùng tên, đó là một class khác và có thể init riêng. Không xem việc có hai event trùng tên là bằng chứng JVM “init lại cùng một lớp” trước khi biết defining loader.

## 4. Đọc `class+load`, `class+init` và `class+unload` có căn cứ

Ví dụ dạng log minh họa, đã lược bỏ các dòng khác và **không đại diện chính xác cho địa chỉ/ID cố định**:

```text
[0.090s][info][class,load] InitTrace$Target source: file:/.../
[0.094s][info][class,init]  ... Initializing 'InitTrace$Target' ...
```

Giải nghĩa từng phần:

- `[0.090s]` là uptime từ lúc JVM khởi động (nếu bật decorator này).
- `[info]` là level.
- `[class,load]` hoặc `[class,init]` là **tập tag của dòng log**. Dấu phẩy xuất hiện trong **cách trình bày tag** của log, trong khi cú pháp selection dùng dấu `+`.
- `source` trong log load có thể là đường JAR, directory, `shared objects file` hoặc dạng khác; không nhất thiết luôn chỉ vào một file local đơn giản.
- `class,init` phản ánh JVM xử lý initialization; nếu lớp không có static initializer có nội dung, vẫn có thể có thông điệp khởi tạo. Không yêu cầu phải nhìn thấy `System.out.println` từ `<clinit>` mới chấp nhận log.

**Hạn chế:** log “class đã load” không chỉ ra rằng loader vẫn reachable sau thời gian dài; log init không đo lượng bộ nhớ cấp phát; log unload không chứng minh nguyên nhân sâu xa khiến loader cuối cùng được thu hồi. Mỗi tag giải đáp một câu hỏi, và nên đối chiếu các bằng chứng khác khi chẩn đoán leak hoặc reload.

## 5. Thí nghiệm: tạo loader riêng và theo dõi class unloading

Class unloading khó tạo ra một cách tất định hơn class initialization. Lớp được định nghĩa bởi loader tồn tại dài hạn thường không thể unload độc lập tùy ý. Ta dùng `URLClassLoader` riêng để định nghĩa lớp plugin, thực hiện một lần, đóng tài nguyên và bỏ **strong reference**, sau đó quan sát log.

### Cấu trúc thư mục

```text
working-directory/
  UnloadTrace.java
  plugin-src/
    plugin/
      Ephemeral.java
  plugin-bin/           (do javac tạo, KHÔNG đặt vào -cp của host)
```

File **`plugin-src/plugin/Ephemeral.java`**:

```java
package plugin;

public final class Ephemeral {
    static {
        System.out.println("plugin.<clinit>");
    }

    public static void touch() {
        System.out.println("plugin.touch");
    }
}
```

File **`UnloadTrace.java`**:

```java
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class UnloadTrace {
    private static WeakReference<ClassLoader> runPlugin()
            throws Exception {
        URL url = Path.of("plugin-bin")
                .toAbsolutePath().toUri().toURL();

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { url },
                ClassLoader.getPlatformClassLoader())) {
            Class<?> clazz = Class.forName(
                    "plugin.Ephemeral", true, loader);
            clazz.getMethod("touch").invoke(null);

            System.out.println("Child defines class: "
                    + (clazz.getClassLoader() == loader));

            // Không trả Class<?> hoặc loader mạnh về main.
            return new WeakReference<>(loader);
        }
    }

    public static void main(String[] args) throws Exception {
        WeakReference<ClassLoader> weak = runPlugin();

        // Chỉ tạo cơ hội GC; không đảm bảo unload.
        for (int i = 0; i < 30 && weak.get() != null; i++) {
            System.gc();
            Thread.sleep(150);
        }

        System.out.println("Loader reference cleared: "
                + (weak.get() == null));
    }
}
```

Để chạy trên shell Bash từ thư mục `working-directory`:

```bash
mkdir -p plugin-bin
javac -d plugin-bin plugin-src/plugin/Ephemeral.java
javac UnloadTrace.java
java -Xlog:class+load=info,class+init=info,class+unload=info,gc=info -cp . UnloadTrace
```

Với Windows PowerShell, chỉ cần tạo thư mục bằng `New-Item -ItemType Directory -Force plugin-bin` (hoặc `mkdir plugin-bin`) rồi chạy ba lệnh `javac` và `java` giống trên.

Những dòng **do ứng dụng** tạo ra lúc bắt đầu luôn là:

```text
plugin.<clinit>
plugin.touch
Child defines class: true
```

Sau đó ứng dụng in một trong hai khả năng:

```text
Loader reference cleared: true
```

hoặc:

```text
Loader reference cleared: false
```

Hai giá trị đều có thể xảy ra. Nếu HotSpot thực sự unload lớp `plugin.Ephemeral` trong cửa sổ quan sát và logging đã bật đúng, log có thể chứa thông điệp gắn tag `[class,unload]` nêu tên lớp đó. Dòng này có mốc thời gian do JVM quyết định; không tự viết một dòng unload giả vào output của ứng dụng.

**Tại sao thí nghiệm có ý nghĩa?**

- Parent là `ClassLoader.getPlatformClassLoader()`, không phải application class loader nạp `UnloadTrace`. Nếu `plugin-bin` **không** xuất hiện trên classpath của host, `plugin.Ephemeral` sẽ được định nghĩa bởi loader con đúng như câu in `Child defines class: true`.
- `runPlugin()` có scope riêng để tham chiếu local tới `clazz`, `loader` và kết quả reflection hết vòng đời khi phương thức trả về. `WeakReference` không giữ loader sống mạnh.
- `URLClassLoader.close()` chỉ đóng tài nguyên mà loader quản lý, **không phải API bắt buộc unload**.
- `System.gc()` đề nghị GC và có thể bị bỏ qua/không làm phát sinh class unloading trong thời gian quan sát; cấu hình như `-XX:+DisableExplicitGC` khiến việc dựa vào lệnh gọi này càng không đáng tin.
- Nếu `Loader reference cleared: false`, chưa đủ kết luận leak: có thể chưa có chu kỳ GC phù hợp, JVM/JIT còn giữ reference tạm thời hoặc còn đường giữ loader mạnh. Cần kiểm tra thêm trước khi kết luận.
- Nếu weak reference đã clear, đó là dữ kiện hữu ích về reachability/GC của loader; còn câu hỏi **lớp cụ thể đã unload chưa** cần đối chiếu với log `class+unload` và đúng class loader.

Với code thử nghiệm khác dùng thread pool, listener, `ThreadLocal`, static cache hoặc framework registry, các tham chiếu mạnh vẫn có thể giữ instance, `Class<?>` hoặc loader; nếu vậy event unload không xảy ra dù đã gọi `close()`. Trong ứng dụng thực, phải giải phóng lifecycle hoàn chỉnh rồi quan sát **nhiều chu kỳ** trong điều kiện tải thích hợp.

## 6. Phân tích thời gian với GC và metaspace

Khi thấy class unload, nên hỏi thêm **GC nào diễn ra gần thời điểm đó**. Ví dụ:

```bash
java -Xlog:class+unload=info,gc=info:stdout:time,uptime,pid,level,tags -cp . UnloadTrace
```

Dùng cùng log output và decorator nhất quán để so thời điểm. Lưu ý GC log có thể nhắc một “Pause”, “Concurrent Cycle” hoặc giai đoạn khác tùy collector; **không được mặc định mọi GC sẽ unload mọi lớp đủ điều kiện**. Đừng suy từ mỗi dòng “GC” rằng unload chắc chắn đã thực hiện.

Nếu phải điều tra metaspace, có thể sử dụng những tín hiệu bổ trợ như:

```bash
jcmd <pid> VM.classloader_stats
jcmd <pid> VM.metaspace
jcmd <pid> help
```

`<pid>` là placeholder thay bằng PID thực tế. Các lệnh `jcmd` yêu cầu quyền attach và JDK phù hợp. Chúng cung cấp snapshot/thống kê, **không thay thế event log mang tên lớp**. Dung lượng metaspace committed/reserved còn phụ thuộc allocator, chính sách tái sử dụng và những lớp khác đang nạp, nên **không giảm** sau unload cũng chưa chứng minh bị giữ class.

Khi log quá lớn, lọc theo dòng `[class,unload]` và tên class/loader mục tiêu (nếu thông tin loader có trong bản JVM). Tuy vậy, nên **giữ bản log gốc** cùng PID, timestamp, cờ JVM và artifact version, để không mất mốc load/init/GC liên quan.

## 7. Cách đọc bằng chứng theo các tình huống

| Quan sát được | Có thể khẳng định | Chưa thể khẳng định |
|---|---|---|
| `[class,load]` xuất hiện cho `com.example.Plugin` | JVM đã ghi nhận sự kiện nạp lớp này | `<clinit>` đã chạy |
| `[class,init]` xuất hiện cho lớp mục tiêu | JVM bắt đầu/thực hiện quy trình initialization cho lớp | Class đã unload sau đó |
| `[class,unload]` nêu đúng lớp/loader | JVM đã ghi nhận unload tại thời điểm log | Ứng dụng không có leak nào khác |
| Có log GC sau thao tác dispose plugin | JVM có hoạt động GC được ghi nhận | Lớp plugin chắc chắn được unload |
| Không thấy `[class,unload]` sau một lần `System.gc()` | Trong dữ liệu quan sát không ghi nhận unload phù hợp | Đây chắc chắn là class-loader leak |
| Không thấy `[class,init]` khi in compile-time constant | Đây phù hợp quy tắc constant-variable không yêu cầu init | Class chưa từng được nạp |
| Hai event init cùng binary name | Có ít nhất hai event liên quan tên đó ở log | Cùng một runtime class đã init hai lần; cần kiểm tra loader identity |

**Thứ tự chữ in stdout và log có thể bị ảnh hưởng bởi buffering, nhiều thread và việc dùng file output.** Khi cần phân tích timeline chính xác, ưu tiên timestamp/uptime của log, ghi mốc ứng dụng cùng clock, cân nhắc đồng bộ nguồn thời gian và cấu hình asynchronous logging. Không suy ra thứ tự nhân quả tuyệt đối chỉ từ thứ tự xuất hiện khi trộn stdout/stderr/file.

## 8. Quy trình chẩn đoán có thể áp dụng trong dự án

1. **Đặt câu hỏi cụ thể:** “Lớp đã được load nhưng chưa init?”, “Plugin có nhiều lần init dưới loader khác nhau?”, hay “Sau khi unload logic, class loader cũ có được GC và các class đã unload?”.
2. **Bật tag tối thiểu trước sự kiện cần bắt:** `class+load` cho load, `class+init` cho init, `class+unload` cho unload; thêm `gc` khi cần quan hệ thời gian. Nếu tag chỉ bật sau sự kiện, log không tái tạo dữ liệu quá khứ.
3. **Lưu danh tính môi trường:** JDK/vendor, collector, flags, PID, ứng dụng/build version, classpath/module path, loại loader, mốc load/redeploy. Trong hệ thống nhiều process, xác định đúng PID trước khi so.
4. **Tạo thao tác có kiểm soát:** nạp class, gọi static API, load plugin, dispose plugin. Với unload, bỏ strong reference và đủ cửa sổ quan sát; tránh hiểu một lần `System.gc()` là điều kiện bắt buộc.
5. **Đọc các sự kiện theo đúng tag và đúng class identity.** Nếu cùng class name có nhiều loader, đừng gom tất cả vào một thực thể; đối chiếu nguồn class và loader khi JVM phát ra thông tin đó.
6. **Tìm bằng chứng bổ sung nếu cần kết luận nguyên nhân:** class load source, `Class.getClassLoader()`, thống kê loader, GC roots, metaspace, heap dump/JFR theo mức độ phù hợp.
7. **Kết luận theo dữ liệu:** “đã thấy unload class X lúc T” mạnh hơn “metaspace có vẻ giảm”; “chưa có event trong cửa sổ quan sát” khác “chắc chắn leak”.

### Quan sát hoặc thay đổi logging của JVM đang chạy

Trên HotSpot có thể dùng diagnostic command `VM.log` qua `jcmd`, nhưng chỉ có hiệu lực cho **sự kiện từ thời điểm cấu hình được bật trở đi**:

```bash
jcmd <pid> help VM.log
jcmd <pid> VM.log list
```

Tùy phiên bản/runtime, dùng cú pháp hiển thị trong `help VM.log` để bật hoặc đổi output/selection. Nên bật logging ngay từ lúc khởi động cho thí nghiệm tái hiện đáng tin cậy; đừng cố suy luận một lần init đã xảy ra trước khi bật log.

## 9. Lỗi thường gặp khi thiết lập và diễn giải logging

**1. Chỉ bật `class+load` rồi kết luận trạng thái init.** Hai log tag đo hai sự kiện khác nhau. Dùng `class+init` cùng phép thử active use.

**2. Dùng `-Xlog:class=info` thay cho `class+init=info`.** Chọn tag chính xác; dấu `+` kết hợp nhiều tag, dấu `,` tách nhiều selection.

**3. Dùng `-verbose:class` rồi cho rằng đã log initialization.** Theo tài liệu JDK 17, shortcut này bao phủ class loading và unloading, không thay tag init.

**4. Tắt log hoặc bật sau khi lớp đã init.** JVM thông thường không chạy lại `<clinit>` của cùng runtime class để phục vụ logging. Nếu cần lặp lại từ đầu, chạy JVM mới hoặc thử nghiệm với defining loader mới.

**5. Cho rằng class trùng tên tương đương cùng class.** Class identity dựa trên binary name và defining loader; plugin loader mới có thể định nghĩa cùng tên với class loader cũ.

**6. Nhầm việc không thấy unload với leak đã được chứng minh.** Class unloading phụ thuộc reachability, collector và thời điểm thu gom. Bằng chứng thiếu có thể do tag/level/output, thời gian quan sát, hoặc loader vẫn có đường strong reference hợp lệ.

**7. Gộp log class với GC và kết luận từ một dòng.** GC log chỉ nói về GC; cần event class+unload hoặc bằng chứng loader/GC roots phù hợp.

**8. Không lưu đầy đủ log metadata.** Khi phân tích nhiều JVM, các mốc `[0.100s]` đều tính từ uptime riêng của process; ghép hai process chỉ bằng uptime sẽ gây sai thứ tự. Kết hợp PID, wall time, JDK flags và bản dựng.

**9. Dùng `class*=trace` trên production dài ngày không kiểm soát dung lượng.** Logging rộng có overhead, tăng I/O và gây áp lực lưu trữ. Bật đúng tổ hợp/level, dùng rotation và giới hạn thời gian thu thập.

**10. Đọc log load source rồi cho rằng đó là defining loader.** `source` cho biết nguồn bytes ở ngữ cảnh log; muốn khẳng định class identity cần đối chiếu loader. Với plugin/class loader tùy biến, xác minh `Class.getClassLoader()` và cơ chế delegation.

## 10. Bài luyện tập và đáp án

**Câu 1.** Sau `Class.forName("InitTrace$Target", false, loader)` đã thấy `[class,load]` nhưng chưa thấy `[class,init]`. Đây có phải lỗi?  
**Đáp án:** Không. Đây là hành vi phù hợp với yêu cầu load mà chưa initialize. Đọc `Target.counter` hoặc một active use khác mới tạo điều kiện init.

**Câu 2.** Vì sao đọc `static final int CONSTANT = 7` không nhất thiết gây init?  
**Đáp án:** `CONSTANT` là constant variable được biểu thức hằng compile-time; caller có thể dùng giá trị inline mà không phải truy cập field runtime.

**Câu 3.** `-Xlog:class+load=info,class+init=info,class+unload=info` có nghĩa JVM tự động thực hiện loading/init/unloading không?  
**Đáp án:** Không. Đây là **cấu hình quan sát** những sự kiện khi JVM thực hiện chúng, không phải lệnh cưỡng bức lifecycle.

**Câu 4.** Sau `URLClassLoader.close()` và `System.gc()` không thấy `[class,unload]`. Kết luận được gì?  
**Đáp án:** Chưa quan sát được unload trong cửa sổ đó; cần xác minh tham chiếu mạnh, cấu hình GC, log, thời điểm và loader identity trước khi nói leak.

**Câu 5.** Khi chẩn đoán cùng tên class từ hai plugin loader, thông tin nào giúp tránh nhầm?  
**Đáp án:** Xác định defining loader, nguồn class, PID, version và timeline các sự kiện; binary name đơn lẻ không đủ để nhận diện runtime class.

## Nguồn tham chiếu chính thức

- [Oracle JDK 17 `java` command / Unified Logging / legacy flags mapping](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): cú pháp `-Xlog`, tag/level/output, `TraceClassInitialization → class+init` và `-verbose:class`.
- [Java Language Specification 17 — chương 12, Execution](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html): `12.4.1` quy định thời điểm class/interface được initialize, `12.4.2` quy trình chi tiết, `12.7` về unloading.
- [Java SE 17 — `Class.forName`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader)): tham số `initialize` và hành vi của reflection.
- [Java SE 17 — `URLClassLoader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): vòng đời loader và `close()`.
- [JDK 17 — `jcmd` manual](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `VM.log`, `VM.classloader_stats` và `VM.metaspace` cho phân tích runtime.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-diagnostics-007">jcmd VM.classloaders, VM.classloader_stats và VM.class_hierarchy</a>

<details>
<summary>Xem chi tiết</summary>

# Chẩn đoán class loader bằng jcmd: VM.classloaders, VM.classloader_stats và VM.class_hierarchy

Trong một JVM chạy lâu — đặc biệt ứng dụng có plugin, application server, Spring Boot, công cụ build, hot reload hay các `URLClassLoader` tùy chỉnh — câu hỏi “vì sao class không được tìm thấy?”, “class nào do loader nào nạp?” và “vì sao metaspace tăng sau mỗi lần redeploy?” không nên trả lời bằng suy đoán về classpath.

**`jcmd`** là công cụ đi kèm JDK (đặc biệt trong HotSpot/OpenJDK) để gửi *diagnostic commands* tới **một tiến trình JVM đang chạy**. Ba lệnh liên quan class loading thường được dùng cùng nhau, nhưng cung cấp **ba góc nhìn khác nhau**:

1. **`VM.classloaders`:** in **cây quan hệ class loader (parent hierarchy)**; tùy chọn có thể liệt kê class thuộc mỗi loader.
2. **`VM.classloader_stats`:** in **thống kê theo class loader**: số class, thông tin `ClassLoaderData` và dung lượng metadata/metaspace chunks/blocks.
3. **`VM.class_hierarchy`:** in **cây kế thừa class** (superclass/subclass), có định danh `ClassLoaderData*` của loader liên quan; **không phải** cây parent của class loader.

Bài thuộc nhóm **Chẩn đoán** trong Java core – classloader. Nội dung tập trung vào cú pháp Java 17+, ví dụ chạy được để tạo class loader có tên, cách đọc output, cách phối hợp ba lệnh chẩn đoán lỗi nạp class và rò rỉ class loader, giới hạn của snapshot và lưu ý an toàn khi attach JVM production.

## 1. Bảng chọn lệnh đúng câu hỏi

| Bạn muốn biết | Lệnh phù hợp | Nhìn thấy gì? | Không kết luận được trực tiếp |
| --- | --- | --- | --- |
| JVM đang có những **loader** nào, parent là ai? | `VM.classloaders` | Bootstrap/platform/app và các custom loader trong cây | Object nào giữ loader sống, binary có tương thích không |
| Loader A đã nạp **class nào**? | `VM.classloaders show-classes=true` | Class names được hiển thị dưới loader trong cây | Class từ JAR nào trong mọi trường hợp, heap object size |
| Có bao nhiêu loader/class, metadata đang phân bổ ra sao? | `VM.classloader_stats` | `Classes`, `ChunkSz`, `BlockSz`, `CLD*` theo loader | Strong retaining path và thời điểm unload chắc chắn |
| `plugin.Child` kế thừa class nào? | `VM.class_hierarchy plugin.Child` | Superclass chain và loader-data identity | Loader parent chain |
| `plugin.Base` có các subclass đang nạp nào? | `VM.class_hierarchy -s plugin.Base` | Superclasses và subclasses của class được chỉ định | Mọi class trên classpath chưa nạp |
| Vì sao loader cũ không được GC thu hồi? | Thống kê + heap dump/retaining paths + TCCL | Dấu hiệu leak và đường giữ reference | Chỉ dùng một snapshot `jcmd` chưa đủ kết luận |
| Class nào thực sự đến từ JAR/CodeSource nào? | Class-loading log, `ProtectionDomain`/CodeSource, `javap` | Nguồn class theo loader/bytecode | Không dựa hoàn toàn vào ba lệnh thống kê trên |

**Mấu chốt:** `VM.classloaders` và `VM.class_hierarchy` có chữ “hierarchy” trong mô tả, nhưng **hierarchy của chúng là hai loại quan hệ khác nhau**.

## 2. Điều kiện để dùng được jcmd

Một phiên chạy điển hình:

```bash
jcmd -l
jcmd <PID> help
jcmd <PID> help VM.classloaders
jcmd <PID> help VM.classloader_stats
jcmd <PID> help VM.class_hierarchy
```

Trong các lệnh trên, **thay `<PID>` bằng số PID thực tế**, ví dụ `14920`; đừng gõ nguyên dấu ngoặc nhọn/góc vào PowerShell/CMD/Bash vì có thể bị shell hiểu thành redirection.

### Chọn PID và JDK phù hợp

- `jcmd -l` liệt kê những tiến trình Java mà cơ chế discovery có thể nhìn thấy; danh sách phụ thuộc quyền truy cập và namespace/container.
- Nếu bạn biết PID của service từ process manager, task manager, `ps` hoặc `ProcessHandle.current().pid()`, dùng trực tiếp `jcmd PID ...`.
- Ưu tiên `jcmd` của **cùng phiên bản/nhà cung cấp JDK** với target JVM, và môi trường hỗ trợ Attach API/diagnostic commands tương ứng.
- Tiến trình phải là JVM đang chạy, hỗ trợ attach; thường cần cùng user/đúng quyền OS. Trong Docker/Kubernetes còn phải xét PID namespace, container filesystem và security policy.
- JVM có thể khởi động với cấu hình **vô hiệu attach**, ví dụ `-XX:+DisableAttachMechanism` trên HotSpot; trong trường hợp đó `jcmd` không hoạt động theo cách thường.
- Đừng mặc định mọi JVM Java SE đều hỗ trợ chính xác ba lệnh này. Đây là **diagnostic commands của HotSpot/OpenJDK và một số runtime tương thích**; luôn hỏi target bằng `help`.

### Cú pháp chuẩn

```text
jcmd <PID> <diagnostic_command> [options] [arguments]
```

Ví dụ:

```bash
jcmd 14920 VM.classloaders
jcmd 14920 VM.classloader_stats
jcmd 14920 VM.class_hierarchy plugin.Greeter
```

`14920` chỉ là PID minh họa. Các lệnh **chỉ đọc/in thông tin** trong bài; tuy vậy diagnostic attach vẫn có chi phí JVM runtime. `VM.classloader_stats` được tài liệu đánh giá *Low impact*; hai lệnh in cây thường *Medium impact* tùy số loaders/classes. Trên JVM production lớn, tránh gọi tràn lan hoặc liệt kê mọi class không cần thiết.

## 3. Kiểm tra sự tồn tại và tùy chọn của từng lệnh

Chạy:

```bash
jcmd <PID> help VM.classloaders
```

Trên một JVM HotSpot đã thử nghiệm, help cho biết:

```text
VM.classloaders
Prints classloader hierarchy.

Options:
  show-classes : Print loaded classes. (BOOLEAN, false)
  verbose      : Print detailed information. (BOOLEAN, false)
  fold         : Show loaders of the same name and class as one.
                 (BOOLEAN, true)
```

Đoạn trên **lược bỏ phần header/impact và rút gọn mô tả**, không phải copy nguyên toàn bộ terminal output. Các tùy chọn sử dụng dạng `key` hoặc `key=value`:

```bash
jcmd <PID> VM.classloaders
jcmd <PID> VM.classloaders show-classes=true
jcmd <PID> VM.classloaders verbose=true
jcmd <PID> VM.classloaders fold=false
jcmd <PID> VM.classloaders show-classes=true fold=false
```

Đặc biệt **`fold=true` mặc định có thể gom các loader có cùng tên và cùng class** trong output. Khi điều tra **nhiều instance URLClassLoader cùng tên**, dùng `fold=false` để tránh vô tình đọc hình hiển thị gộp thành “chỉ một loader”.

Với statistics:

```bash
jcmd <PID> help VM.classloader_stats
```

Help xác nhận:

```text
VM.classloader_stats
Print statistics about all ClassLoaders.
Impact: Low
Syntax: VM.classloader_stats
```

Lệnh này **không có `show-classes` hay `fold`** trong cú pháp của bản JVM đã kiểm thử; không chuyển tùy chọn của `VM.classloaders` sang statistics.

Với class hierarchy:

```bash
jcmd <PID> help VM.class_hierarchy
```

Help chỉ ra:

```text
VM.class_hierarchy [options] [<classname>]
  -i : Include inherited interfaces (BOOLEAN, false)
  -s : Show subclasses as well as superclasses when class
       name is supplied (BOOLEAN, false)
  classname : Fully qualified class name (optional)
```

Đoạn này cũng là **tóm lược help**, giữ đúng ý nghĩa flags. Cách sử dụng:

```bash
jcmd <PID> VM.class_hierarchy
jcmd <PID> VM.class_hierarchy plugin.Greeter
jcmd <PID> VM.class_hierarchy -s plugin.Greeter
jcmd <PID> VM.class_hierarchy -i plugin.Greeter
```

Nếu không chỉ định tên class, lệnh có thể in **toàn bộ hierarchy các class đã nạp**, rất nhiều dòng. Dùng tên class khi có thể.

**Chú ý vị trí:** `-s` và `-i` là **options của `VM.class_hierarchy`**, không phải flags của executable `jcmd` đặt trước PID.

## 4. VM.classloaders — cây quan hệ loader parent

Lệnh:

```bash
jcmd <PID> VM.classloaders
```

Một JVM có custom loader do chương trình đặt tên `plugin-demo` có thể hiển thị:

```text
+-- <bootstrap>
      |
      +-- "platform", jdk.internal.loader.ClassLoaders$PlatformClassLoader
            |
            +-- "app", jdk.internal.loader.ClassLoaders$AppClassLoader
            |
            +-- "plugin-demo", java.net.URLClassLoader
```

Đây là **phần output thực nghiệm được giản lược khoảng trắng**, không hứa format giống hệt trên mọi JDK/OS.

Đọc sơ đồ:

- **Bootstrap loader** ở gốc; ở API `Class.getClassLoader()`, bootstrap có thể được thể hiện bằng `null`.
- **Platform loader** nạp các classes thuộc platform theo module/runtime configuration.
- **Application loader** thường phụ trách application classpath.
- **Plugin loader** là một instance `URLClassLoader` riêng, có thể chọn platform hoặc application loader làm parent.
- **Cây thể hiện quan hệ parent của loaders**, không phải class Java `extends` nhau. Ví dụ `URLClassLoader` *extends* `SecureClassLoader` trong hệ class inheritance, nhưng cây `VM.classloaders` không vẽ `SecureClassLoader` làm cha delegation chỉ vì quan hệ `extends`.

Trong ví dụ ở bài này, plugin loader được tạo với **parent = platform loader** nên `plugin-demo` nằm **cùng nhánh dưới platform** với app loader, **không phải con của app**. Đừng mặc định “plugin do app tạo ⇒ app là parent”. Quan hệ delegation là do **constructor/configuration** quyết định.

### Ba tùy chọn đặc biệt của VM.classloaders

**`show-classes=true`:** in class names gắn với mỗi loader. Hữu ích khi nghi `PluginLoader` nạp lại bản API được parent sở hữu, hoặc muốn xác định `plugin.Greeter` có thực sự do loader plugin define.

**`verbose=true`:** thêm chi tiết của loaders/output. Cần xem output trên JVM mục tiêu vì trường/cú pháp biểu diễn thay đổi giữa releases.

**`fold=false`:** không gom các loaders có cùng name và class. Rất quan trọng với plugin reload leak: nhiều instance URLClassLoader cùng tên có thể bị folded khi `fold=true`, khiến ảnh chụp hierarchy ít rõ ràng hơn số instances.

Ví dụ:

```bash
jcmd <PID> VM.classloaders show-classes=true fold=false
```

**Chi phí:** in toàn bộ class cho hàng nghìn loaders có thể tạo output rất lớn và mất thời gian. Trong production hãy bắt đầu bằng bản mặc định, chỉ bật `show-classes=true` khi đã có mục tiêu chẩn đoán.

### Điều VM.classloaders chưa cho biết

- Không hiển thị **GC roots/retaining paths** dẫn đến loader.
- Không chứng minh loader đã leak chỉ vì nó tồn tại trong một snapshot.
- Không nói class trên filesystem *có thể load* nhưng **chưa load**.
- Không thay `javap` để xem method/field descriptors.
- Không thay `ModuleLayer`/JPMS graph để kết luận exports/opens/readability.

## 5. VM.classloader_stats — số class và các số liệu metadata

Lệnh:

```bash
jcmd <PID> VM.classloader_stats
```

Một snapshot rút gọn từ JVM thử nghiệm:

```text
ClassLoader   Parent       CLD*         Classes ChunkSz BlockSz Type
0x...         0x...        0x...             1    5120    1336  java.net.URLClassLoader
0x...         0x...        0x...             1    6144    3440  jdk.internal.loader.ClassLoaders$AppClassLoader
0x...         0x...        0x...           899 4456448  367896  <boot class loader>
...
Total = 4                                   907 4486144  383144
```

**Đây là output đã rút gọn**, địa chỉ và số liệu chỉ mang tính ví dụ từ một JVM thử nghiệm. Không được so các số này như định mức cho mọi JVM Java 17; số classes/core modules thay đổi theo JVM/build và code đang chạy.

Các cột:

| Cột | Ý nghĩa thực dụng | Cần thận trọng |
| --- | --- | --- |
| `ClassLoader` | Địa chỉ/định danh biểu diễn loader trong VM snapshot, có thể `0x0` cho bootstrap | Không phải Java identity hash code hay PID |
| `Parent` | Thông tin parent pointer của classloader object | Giá trị dạng pointer, không là binary name của class parent |
| `CLD*` | Internal pointer tới cấu trúc **ClassLoaderData** của HotSpot | Chỉ định danh nội bộ trong runtime; không phải API Java ổn định |
| `Classes` | Số class thuộc loader/loader-data entry tương ứng trong thống kê | Không phải số object instances tạo từ class đó |
| `ChunkSz` | Tổng kích thước các metaspace chunks được cấp phát trong nhóm thống kê | Không phải heap memory, không phải “bộ nhớ sống chỉ cho class” |
| `BlockSz` | Tổng kích thước metaspace blocks được cấp phát trong các chunks | Không đồng nhất với RSS hay mức dùng Java heap |
| `Type` | Class của loader (hoặc bootstrap/hidden classes notation) | Nhiều instance có cùng `Type` nhưng tuổi đời khác |

Có thể có dòng **`+ hidden classes`** gắn vào bộ đếm của một loader/data entry. Hidden classes được JVM dùng cho một số implementation như lambda/invokedynamic; đừng mặc định chúng là “lớp bị giấu do ứng dụng độc hại” hay nhất định là leak.

### Chú ý kích thước và phép cộng

`ChunkSz` và `BlockSz` trong output HotSpot đang quan sát là các con số kích thước **theo bytes** trong bản xây dựng thử nghiệm; `jcmd` help chính thức mô tả dạng thống kê, không bắt buộc mọi JVM/phiên bản hiển thị cột giống hệt. Cần đối chiếu chú thích ngay dưới `Total` trong output bản runtime đang dùng.

Không suy ra:

- “`ChunkSz` = live Java heap của plugin.”
- “`BlockSz` = dung lượng JAR trên đĩa.”
- “`ChunkSz - BlockSz` = classloader leak chắc chắn.”
- “Một loader có 100 class nghĩa 100 object instance.”
- “Sau close loader, `Classes` của loader phải về 0.”

Một URL loader có thể được **đóng** nhưng object/class của nó vẫn được giữ mạnh bởi host, nên `VM.classloader_stats` vẫn hiển thị loader với class count và metadata.

### Khi nào statistics đặc biệt hữu ích?

- So **số instance custom loaders** và **số class mỗi loader** trước/sau nhiều lần reload plugin.
- Phát hiện xu hướng: sau mỗi redeploy có thêm một `java.net.URLClassLoader` với nhiều classes, và các loader cũ không biến mất sau GC phù hợp.
- Xác nhận bản ứng dụng vô tình tạo hàng trăm loaders gần như giống nhau.
- Ước lượng áp lực metadata theo loader (không thay `VM.metaspace`/Native Memory Tracking khi cần số liệu metaspace chi tiết).
- Correlate `CLD*` với class names từ `VM.class_hierarchy` trên cùng target JVM, cùng thời điểm tương đối.

**Một snapshot chưa đủ chứng minh leak.** JVM có thể vừa tạo loader hợp lệ, GC chưa diễn ra, hoặc container chủ ý giữ nhiều phiên bản song song. Cần so thời gian và tìm đường giữ strong reference.

## 6. VM.class_hierarchy — cây kế thừa của Java classes

Lệnh:

```bash
jcmd <PID> VM.class_hierarchy plugin.Greeter
```

Ví dụ trên target đã nạp `plugin.Greeter`:

```text
java.lang.Object/null
|--plugin.Greeter/0x0000...
```

`plugin.Greeter` là subclass của `java.lang.Object`. Phần `0x0000...` là **ClassLoaderData pointer** của defining loader, khác định danh Java object/loader name. `null` sau `java.lang.Object` ở ví dụ có nghĩa class được bootstrap loader định nghĩa.

### Đừng đọc nhầm thành cây classloader

Cây này là:

```text
java.lang.Object
   |
   +-- plugin.Greeter
```

không phải:

```text
Bootstrap loader
   |
   +-- Plugin URLClassLoader
```

Mối quan hệ **extends/implements giữa classes** và **parent delegation giữa loaders** là hoàn toàn khác nhau.

Một class `plugin.Greeter` có thể extends `plugin.Base`; hai class ấy có thể được define bởi các loader khác nhau tùy mô hình. `VM.class_hierarchy` tập trung vào class inheritance và in thêm loader-data identity để hỗ trợ phân biệt runtime definitions.

### Dùng -s và -i

```bash
jcmd <PID> VM.class_hierarchy plugin.Greeter
jcmd <PID> VM.class_hierarchy -s plugin.Greeter
jcmd <PID> VM.class_hierarchy -i plugin.Greeter
```

- Không `-s`: khi lọc theo tên class, chỉ in superclasses theo help của VM.
- Có `-s`: bổ sung các subclasses đã nạp của class được chọn.
- Có `-i`: in thêm inherited interfaces phù hợp theo implementation.
- Không truyền tên: có thể in **toàn bộ loaded class hierarchy**, rất lớn.

**Không thể dùng lệnh này để xem source code, bytecode, method implementations hoặc mọi class có trên classpath chưa được load.** Với lỗi `NoSuchMethodError`, cần `javap` để đối chiếu binary descriptors; `VM.class_hierarchy` chỉ giúp nhận diện hierarchy/loader-data liên quan.

### Khi có cùng binary name trong hai loaders

Hai loaders độc lập có thể tự define hai class đều tên `plugin.Greeter`. Với JVM, đó là **hai runtime types khác nhau** (binary name + defining loader). `VM.class_hierarchy` có thể biểu diễn chúng dưới cùng tên chữ nhưng với **loader-data identity khác nhau**; nên phối hợp `VM.classloader_stats` hoặc `VM.classloaders show-classes=true fold=false`.

Không kết luận chỉ từ tên chữ rằng đó là cùng một `Class<?>`. Cần xác định **defining loader**.

## 7. Ví dụ Java 17+: tạo URLClassLoader có tên rồi dùng cả ba lệnh

Đây là một ví dụ **đã biên dịch với `javac --release 17` và chạy** trên JVM HotSpot. Nó tạo một `URLClassLoader` có name `plugin-demo`, tự nạp class plugin và giữ chương trình sống trong một khoảng để `jcmd` attach. Bạn có thể chạy lại trên JDK 17+ có `jcmd`.

### 7.1 Cấu trúc

```text
jcmd-loader-demo/
├── src/
│   └── plugin/
│       └── Greeter.java
├── App.java
├── plugin/           (output .class của plugin)
└── host/             (output .class của host)
```

Tạo thư mục output `plugin` và `host`. **Không đưa `plugin` vào `-cp` của host**, để class plugin thật sự do URL loader riêng define.

### 7.2 Class của plugin

File `src/plugin/Greeter.java`:

```java
package plugin;

public class Greeter {
    public String say() {
        return "Hello from plugin";
    }
}
```

### 7.3 Host tạo named URLClassLoader

File `App.java`:

```java
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class App {
    public static void main(String[] args) throws Exception {
        URLClassLoader loader = new URLClassLoader(
                "plugin-demo",
                new java.net.URL[] {
                    Path.of("plugin").toUri().toURL()
                },
                ClassLoader.getPlatformClassLoader());

        Class<?> clazz = loader.loadClass("plugin.Greeter");
        Object object = clazz.getConstructor().newInstance();

        System.out.println("PID=" + ProcessHandle.current().pid());
        System.out.println("loader=" + clazz.getClassLoader().getName());
        System.out.println("msg=" + clazz.getMethod("say").invoke(object));

        Thread.sleep(30_000);
        loader.close();
    }
}
```

Giải thích:

- Constructor `URLClassLoader(String name, URL[] urls, ClassLoader parent)` có trên Java 17 và cho phép đặt tên để dễ quan sát.
- Parent được chọn là `ClassLoader.getPlatformClassLoader()`, nên `plugin-demo` nằm dưới platform trong cây delegation; nó không phải child của app loader.
- `clazz.getClassLoader().getName()` trả `plugin-demo` để xác nhận đúng loader.
- `ProcessHandle.current().pid()` in PID thực tế của tiến trình target, tránh đoán.
- `Thread.sleep(30_000)` giữ chương trình sống khoảng **30 giây**, vừa đủ cho thao tác thủ công trong ví dụ. Đây chỉ là khoảng giữ tiến trình minh họa, không phải quy trình chẩn đoán server dài hạn.
- `loader.close()` sau sleep chỉ đóng URL loader; không ép unload classes. Class/object references vẫn còn trong method tại thời điểm chạy ví dụ.

### 7.4 Biên dịch và chạy

Tại thư mục `jcmd-loader-demo`:

```bash
javac --release 17 -d plugin src/plugin/Greeter.java
javac --release 17 -d host App.java
java -cp host App
```

Stdout đã quan sát:

```text
PID=<PID thật của tiến trình>
loader=plugin-demo
msg=Hello from plugin
```

**Không dùng nguyên `<PID thật của tiến trình>` như stdout cố định**: dòng đầu luôn là PID OS/runtime hiện tại, ví dụ trên máy thử nghiệm là một số cụ thể nhưng sẽ khác trên máy người học.

Để gọi `jcmd` khi target còn sống, **mở terminal thứ hai** trong khi terminal đầu đang sleep. Với ứng dụng chạy lâu thật, bạn không cần viết `Thread.sleep`.

### 7.5 Ba lệnh trên target

Ở terminal thứ hai:

```bash
jcmd -l
jcmd <PID> help VM.classloaders
jcmd <PID> VM.classloaders show-classes=false
jcmd <PID> VM.classloader_stats
jcmd <PID> VM.class_hierarchy -s plugin.Greeter
```

**Đã kiểm chứng trên JVM thử nghiệm:**

- `VM.classloaders` chứa `"plugin-demo", java.net.URLClassLoader` dưới platform loader.
- `VM.classloader_stats` có một dòng loader type `java.net.URLClassLoader` với ít nhất class plugin được tính, kèm `CLD*`, `ChunkSz` và `BlockSz`.
- `VM.class_hierarchy -s plugin.Greeter` in `java.lang.Object/null` và `plugin.Greeter/<loader-data-pointer>`.

Vì pointer, class count, heap/metaspace allocations và bootstrap class count đều phụ thuộc JVM, đây là **các đặc điểm được kiểm chứng**, không phải khẳng định các số cụ thể cố định trong mọi lần chạy.

### 7.6 Ba lệnh trả lời ba câu hỏi khác nhau

Cùng một plugin class:

- “`plugin-demo` có parent loader nào?” → `VM.classloaders`.
- “`plugin-demo` đã định nghĩa bao nhiêu class, metadata chunks thế nào?” → `VM.classloader_stats`.
- “`plugin.Greeter` extends class nào, và class loader data nào gắn với định nghĩa này?” → `VM.class_hierarchy`.

Nếu mới chỉ dùng một trong ba, bạn có thể đánh đồng loader **object**, loader **data**, class **hierarchy**, parent **delegation**, hoặc metaspace **allocation**.

## 8. Correlate CLD* giữa statistics và class hierarchy

Trong thử nghiệm, thống kê có dòng tương tự:

```text
... CLD*=0x0000020ffe529c10 ... Type=java.net.URLClassLoader
```

Cây class hierarchy có:

```text
|--plugin.Greeter/0x0000020ffe529c10
```

Đây là **minh họa từ một snapshot của JVM thử nghiệm**, cho thấy cả hai output cùng nhắc đến một `ClassLoaderData*`. Không copy pointer này sang tiến trình khác hoặc JVM restart: đây là địa chỉ/ID VM-internal, không phải persistent identifier.

Một cách đọc an toàn:

1. Dùng `VM.classloaders fold=false` để xác định custom loader theo tên và parent.
2. Dùng `VM.classloader_stats` để tìm những loader type liên quan và `CLD*`; lưu snapshot theo thời điểm.
3. Dùng `VM.class_hierarchy plugin.Greeter` để thấy class thực sự đã nạp và CLD pointer của nó.
4. Nếu pointer khớp trong cùng JVM, bạn có thêm dữ kiện liên hệ loader-data và class.
5. Không suy ra *CodeSource/JAR file path*, version hoặc GC retaining path chỉ từ pointer ấy.

**Một điểm tinh tế:** `ClassLoaderData` là cấu trúc nội bộ của HotSpot, không phải `java.lang.ClassLoader` hay `java.lang.Module` public API. Mối liên hệ của nó hữu ích để debug, nhưng output/địa chỉ/format có thể khác trong release khác.

## 9. Dùng thống kê jcmd để khoanh vùng loader nghi bị giữ

Với plugin reload, thu `VM.classloaders fold=false` và `VM.classloader_stats` **trước/sau mỗi thế hệ triển khai** trong cùng JVM. Dùng `fold=false` để nhìn rõ nhiều loader cùng tên/class; đọc `Classes`, `CLD*`, `ChunkSz` và `BlockSz` như các số liệu loader metadata, không phải object heap bytes.

Nếu loader cũ vẫn xuất hiện sau `close()`, có thể còn công việc hợp lệ hoặc strong references từ TCCL, task, cache, listener hay provider; JVM cũng không bắt buộc unload ngay. So xu hướng qua nhiều chu kỳ và GC thích hợp. Để tìm **ai đang giữ loader**, cần heap dump/MAT Path to GC Roots hoặc bằng chứng tương đương, không suy từ một snapshot jcmd. Bài JFR/heap dump hướng dẫn điều tra root path, phân biệt thời điểm quan sát và tính hợp lệ của lifecycle.
## 10. Vì sao close loader không làm nó biến mất khỏi thống kê?

`URLClassLoader.close()` chỉ đóng nguồn classpath/JAR handles theo hợp đồng. Nó **không**:

- Đóng mọi thread/executor do plugin tạo.
- Xóa mọi callback/provider/cache host đang giữ.
- Hủy instances hoặc xóa `Class<?>` đã nạp.
- Buộc GC thu hồi loader.
- Buộc JVM unload class metadata.

Một loader đã closed **vẫn có thể xuất hiện** trong `VM.classloaders`/`VM.classloader_stats` nếu nó còn reachable/loader data còn tồn tại. Đây không phải bằng chứng close thất bại.

Quy trình plugin stop tốt hơn:

```text
Reject new requests
       |
       v
Await/cancel in-flight plugin tasks
       |
       v
Call plugin.stop() and unregister callbacks/services
       |
       v
Restore TCCL / clear ThreadLocal / close plugin-owned resources
       |
       v
Call URLClassLoader.close()
       |
       v
Release host strong references to old instance/Class/loader
       |
       v
Loader may become eligible for GC
       |
       v
JVM may unload its classes (no guaranteed deadline)
```

**Điểm liên quan chẩn đoán:** chụp statistics ngay sau `close()` mà không kiểm tra tasks/strong references rồi kết luận “classloader leak” là một kết luận vội. Class unloading có điều kiện reachability và phụ thuộc quyết định GC.

## 11. Phân biệt loaded classes với observable classes trên đường tìm

`VM.classloaders show-classes=true` và `VM.class_hierarchy` tập trung vào **các class đã được JVM nạp**. Một JAR có hàng nghìn `.class` nhưng ứng dụng chỉ chạm tới mười class, thống kê không tự liệt kê toàn bộ mọi file class có trong JAR.

Phân biệt:

| Khái niệm | Ví dụ |
| --- | --- |
| **File có trên đĩa** | `plugin.jar!/plugin/Unused.class` tồn tại |
| **Loader có thể tìm** | `URLClassLoader` biết URL trỏ tới plugin JAR |
| **Class đã load/define** | JVM đã có runtime `Class<?>` cho `plugin.Used` |
| **Class đã initialize** | JVM đã chạy `<clinit>` theo quy tắc initialization |
| **Class đủ điều kiện unload** | Defining loader có thể được GC thu hồi |
| **Class đã unload** | JVM thực sự thực hiện unloading, có thể thấy qua logs/events |

Không dùng `VM.class_hierarchy` như một câu lệnh “liệt kê toàn bộ classes có thể load từ classpath”. Và cũng không suy rằng class vắng mặt trong snapshot nghĩa là file bị thiếu: có thể chỉ chưa được nạp.

Muốn điều tra class-path/JAR contents, phối hợp `jar --list`, classloading logs, runtime resource lookup hoặc `javap`.

## 12. Khi nào dùng -Xlog thay vì jcmd?

`jcmd` cho **snapshot lúc yêu cầu**, còn unified JVM logging có thể ghi **chuỗi sự kiện theo thời gian**. Với HotSpot:

```bash
java -Xlog:class+load=info,class+unload=info -cp host App
```

Lệnh này cho phép quan sát class load và unload events xảy ra theo thời gian nếu runtime có hỗ trợ. Lưu ý:

- `class+load`: sự kiện class được load, không phải danh sách tất cả file JAR.
- `class+unload`: sự kiện unloading; chỉ xuất hiện **khi thực sự xảy ra**.
- Không thấy log unload **không chứng minh** `URLClassLoader.close()` thất bại hoặc VM bị leak.
- Runtime có thể giữ classloader mạnh bởi callback/TCCL hoặc chưa có GC/unloading.
- Logging chi tiết có thể sinh output lớn; chọn đúng mức log và lưu file theo chính sách production.

Trong môi trường production, nên chọn hợp lý:

- `jcmd` snapshot ít lần để biết trạng thái hiện tại.
- `-Xlog:class+load/class+unload` hoặc JFR khi cần timeline.
- Heap dump/retaining path khi cần xác định leak.
- `VM.metaspace` hoặc NMT khi cần đi sâu vào native/metaspace allocations.

Các công cụ bổ trợ không làm mất vai trò của ba lệnh chính; chúng trả lời **những câu hỏi mà snapshot tree/stats không thể trả lời**.

## 13. Khi jcmd không chạy hoặc báo lỗi attach

Một vài tình huống:

| Dấu hiệu | Nguyên nhân có thể | Bước kiểm tra |
| --- | --- | --- |
| `jcmd -l` không thấy tiến trình | Khác user/PID namespace/container, process không hỗ trợ discovery | Kiểm tra OS PID và chạy jcmd cùng namespace/quyền |
| `Could not attach` hoặc timeout | Attach mechanism tắt, quyền thiếu, JVM bận/không phản hồi, filesystem temp restrictions | Kiểm tra runtime flags, user, container và logs |
| `Unknown diagnostic command` | JVM/version/vendor không hỗ trợ tên command | `jcmd PID help` |
| Command chạy nhưng output quá nhiều | Chọn `show-classes=true` hoặc hierarchy không filter | Bỏ liệt kê class, lọc theo class cụ thể |
| Không thấy plugin loader | Plugin chưa load, đã GC, bị fold, hoặc parent đã define class thay | `fold=false`, chạy đúng lúc, kiểm tra code/defining loader |
| `VM.classloader_stats` có nhiều rows giống type | Nhiều **loader instances** cùng class/type | So CLD/loader IDs và lifecycle; không đếm theo type name duy nhất |
| `VM.class_hierarchy plugin.Foo` không thấy class | Class chưa load, sai binary name, target JVM khác, đã unload | Xác nhận `getName()`/loader và class-loading logs |
| Output class hierarchy có `null` | Class do bootstrap loader định nghĩa | Không hiểu `null` là class không có superclass |
| Snapshot sau deploy có `ChunkSz` tăng | Nạp thêm classes/metaspace chunks | So nhiều mốc và GC, không kết luận leak ngay |
| Loader tồn tại sau `close()` | Còn references hoặc VM chưa unload | Phân tích TCCL/cache/tasks/retaining roots |

### Lưu ý bảo mật và vận hành

`jcmd` là công cụ quản trị tương tác với tiến trình. Không cấp quyền attach cho user/automation không tin cậy. Một số diagnostic commands có khả năng làm thay đổi trạng thái VM hoặc tạo heap dump; **chỉ chạy những lệnh cần thiết và hiểu tác động**.

Với ba lệnh trong bài, dùng `help` trước rồi đọc output; không chạy “`jcmd PID` không command” hoặc script quét mọi tiến trình trong production nếu chưa được phép. Cũng không lưu log chứa thông tin classnames/plugin internals công khai.

## 14. Lỗi suy luận thường gặp

| Kết luận vội | Cách hiểu đúng |
| --- | --- |
| “`VM.class_hierarchy` là cây parent của loaders.” | Sai; đó là cây **class inheritance**. `VM.classloaders` mới là parent loader hierarchy. |
| “`VM.classloader_stats` hiển thị heap bytes của mỗi instance.” | Sai; thống kê theo loader/class metadata. |
| “`ChunkSz` cao chứng tỏ memory leak.” | Chưa đủ. Đây là allocation snapshot; cần xu hướng và retaining paths. |
| “Loader đã `close()` phải biến mất khỏi stats.” | Sai; close không ép GC/unload. |
| “Không thấy loader trong `VM.classloaders` tức không có class tương ứng.” | Có thể class do parent/bootstrap define, loader chưa tạo, bị fold hoặc đã GC. |
| “Class có trong JAR thì chắc có trong `VM.class_hierarchy`.” | Không; chỉ class đã loaded/defined mới xuất hiện. |
| “`fold=true` cho tôi số chính xác mọi loader instance.” | Có thể gộp loaders cùng tên/class; dùng `fold=false` khi đếm instances. |
| “CLD pointer là tên/id vĩnh viễn của loader.” | Không; địa chỉ VM-internal không ổn định qua JVM restart. |
| “ClassLoader name giống nhau nghĩa cùng loader.” | Không; nhiều objects có thể cùng `getName()`. |
| “`jcmd` có thể tự sửa classpath, recompile class cũ.” | Không; đây là diagnostics, không phải build/deployment repair tool. |
| “jcmd chỉ dùng được cho Java classpath, không dùng được JPMS.” | Không đúng; target JVM dùng named modules vẫn có loaders/classes để chẩn đoán. |
| “Class hierarchy và classloader hierarchy là một đồ thị.” | Sai; inheritance và delegation là hai quan hệ khác nhau. |

## 15. Câu hỏi tự kiểm tra có đáp án

**1.** Muốn xem plugin loader là child của platform hay application loader, dùng lệnh nào?

**Đáp án:** `jcmd PID VM.classloaders`; thêm `fold=false` khi cần phân biệt các instances cùng tên/type.

**2.** Muốn biết loader nào đang giữ nhiều class metadata, dùng lệnh nào?

**Đáp án:** `jcmd PID VM.classloader_stats`. Đọc `Classes`, `ChunkSz`, `BlockSz` và `Type`. Các con số không phải heap object sizes.

**3.** `VM.class_hierarchy plugin.Greeter` in `java.lang.Object` phía trên plugin class. Điều đó nói gì?

**Đáp án:** `plugin.Greeter` kế thừa `java.lang.Object` ở **class hierarchy**; không chứng minh plugin loader có parent là bootstrap loader.

**4.** `-s` trên `VM.class_hierarchy` có tác dụng gì?

**Đáp án:** Khi đã chọn classname, nó yêu cầu in thêm các **subclasses**, ngoài superclasses.

**5.** `-i` có ý nghĩa gì?

**Đáp án:** Yêu cầu hiển thị inherited interfaces theo capability của command.

**6.** Vì sao `VM.classloaders fold=false` hữu ích khi phát hiện leak?

**Đáp án:** Mặc định fold có thể gộp loaders cùng name/class trong hiển thị. Khi đếm nhiều instance loader sau redeploy, cần tách chúng.

**7.** Vì sao kết quả `VM.classloader_stats` sau close vẫn có `plugin-demo`?

**Đáp án:** Close chỉ đóng nguồn URL/JAR handles của URLClassLoader; loader/class metadata có thể còn sống qua references và VM chưa unload.

**8.** `CLD*` là gì?

**Đáp án:** Một pointer/identifier nội bộ của HotSpot `ClassLoaderData`; có thể correlate các lệnh cùng target JVM nhưng không phải public Java object ID bền vững.

**9.** Một class nằm trong plugin JAR nhưng không xuất hiện trong `VM.class_hierarchy`. Có thể là vì sao?

**Đáp án:** Class chưa được load, không phải JVM thiếu file; output command tập trung các loaded classes.

**10.** `VM.classloader_stats` tăng `Classes` qua ba lần deploy có chắc leak?

**Đáp án:** Chưa. Cần biết ứng dụng còn chạy nhiều versions hợp lệ không, GC/unload đã xảy ra chưa và có strong retaining paths nào giữ loaders cũ.

**11.** Để tìm chính xác **ai giữ old loader sống**, ba lệnh trên đủ không?

**Đáp án:** Không; cần heap dump/retaining path analysis và kiểm tra TCCL/ThreadLocal/callbacks/registries.

**12.** Khi JVM báo “unknown command”, bước đầu là gì?

**Đáp án:** `jcmd PID help` và `jcmd PID help VM.classloaders` để kiểm tra target runtime có hỗ trợ command/flags hay không.

**13.** Vì sao không so trực tiếp địa chỉ `0x...` từ hai JVM restart?

**Đáp án:** Đó là pointer/VM internal identity, không phải ID ổn định giữa tiến trình/lần khởi động.

**14.** Muốn xem class nào đến từ JAR nào, lệnh nào bổ trợ?

**Đáp án:** Class-loading logs (ví dụ `-Xlog:class+load=info`), CodeSource của `Class<?>` khi có, cùng `jar --list`/`javap`. `VM.classloader_stats` không phải file-origin resolver.

## 16. Checklist thao tác thực chiến

1. Xác nhận **JDK/HotSpot** của target và khả năng attach; dùng đúng `jcmd`.
2. Dùng `jcmd -l` hoặc lấy PID bằng công cụ OS; kiểm tra target process đúng.
3. Chạy `jcmd PID help` và `help VM.classloaders/VM.classloader_stats/VM.class_hierarchy`.
4. Chụp `VM.classloaders` để xem parent tree, không nhầm với class inheritance.
5. Bật `fold=false` nếu nghi nhiều instance loaders trùng tên/type.
6. Chỉ bật `show-classes=true` khi cần, chú ý chi phí output lớn.
7. Chụp `VM.classloader_stats` để xem `CLD*`, class counts và allocations metadata.
8. Dùng `VM.class_hierarchy ClassName` hoặc `-s`/`-i` theo câu hỏi về inheritance/interface.
9. Correlate `CLD*` giữa stats và hierarchy trong cùng process/snapshot, không lưu làm ID vĩnh viễn.
10. So snapshot **trước/sau** plugin load/reload/stop và sau GC phù hợp.
11. Không coi `URLClassLoader.close()` là lệnh class unloading.
12. Khi nghi leak, tìm root references bằng heap dump và kiểm tra TCCL, ThreadLocal, registries, active tasks.
13. Dùng class-loading/unloading logs hoặc JFR nếu cần timeline, `VM.metaspace` nếu cần phân tích memory sâu.
14. Kiểm tra CodeSource/JAR khi nghi trùng class/version thay vì suy từ tên class hoặc type của loader.
15. Giữ quyền attach, tác động runtime và dữ liệu diagnostics trong phạm vi bảo mật/vận hành được phép.

## Kết luận

**`VM.classloaders` cho biết loader nào làm parent của loader nào; `VM.classloader_stats` cho biết số class và thống kê metadata theo loader; `VM.class_hierarchy` cho biết cây kế thừa của những classes đã nạp, kèm loader-data identity.** Chúng bổ trợ nhau để chẩn đoán classloading và plugin leaks, nhưng không thay nhau và không thay được heap retaining paths.

Khi cần điều tra một JVM có nhiều plugin, hãy bắt đầu bằng **PID → `VM.classloaders fold=false` → `VM.classloader_stats` → `VM.class_hierarchy ClassName`**, sau đó dùng class-loading logs hoặc heap dump nếu câu hỏi là **class/JAR nào thực sự được nạp** hoặc **GC root nào đang giữ loader cũ**. Không kết luận “leak” chỉ từ một snapshot hay từ việc loader vẫn xuất hiện ngay sau `close()`.

**Tài liệu chính thức:**

- [Oracle Java SE 17 — `jcmd` man page](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `VM.classloaders`, `VM.classloader_stats`, `VM.class_hierarchy`, options và impact.
- [Oracle Java SE 17 — Troubleshooting Guide](https://docs.oracle.com/en/java/javase/17/troubleshoot/): quy trình dùng tools/JVM diagnostics.
- [`ClassLoader` API — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, named loader.
- [`URLClassLoader` API — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): constructors, `close()` và giới hạn.
- [JLS 17 `12.7 — Unloading of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.7): khả năng GC thu hồi defining loader và class unloading.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-diagnostics-008">ClassLoadingMXBean: tổng lớp đã nạp, lớp hiện có và lớp đã dỡ</a>

<details>
<summary>Xem chi tiết</summary>

# ClassLoadingMXBean: tổng lớp đã nạp, lớp hiện có và lớp đã dỡ trong JVM

`java.lang.management.ClassLoadingMXBean` là một **MXBean thuộc Java Management API** để quan sát **tình hình class loading của toàn bộ JVM**. Nó không tự nạp, dỡ hoặc giải phóng class: nhiệm vụ của nó là **cung cấp số liệu tổng hợp** và một tùy chọn bật/tắt verbose output về hoạt động nạp/dỡ lớp.

Các chỉ số cơ bản:

1. `getTotalLoadedClassCount()` — **tổng số class đã được JVM nạp từ lúc bắt đầu chạy** (bộ đếm tích lũy).
2. `getLoadedClassCount()` — **số class hiện đang được JVM coi là loaded** tại thời điểm lấy mẫu.
3. `getUnloadedClassCount()` — **tổng số class đã được JVM dỡ từ lúc bắt đầu chạy** (bộ đếm tích lũy).
4. `isVerbose()`/`setVerbose(boolean)` — trạng thái và điều khiển verbose class-loading output (phạm vi JVM, không phải riêng một loader).

Điểm đặc biệt quan trọng với hệ thống plugin: **ba số liệu này có phạm vi toàn JVM**, không phân loại sẵn theo tên plugin, JAR, ClassLoader instance hay module. Muốn tìm class nào/loader nào đang giữ bộ nhớ, cần kết hợp công cụ khác (JFR, JVM logging, thread dumps, JMX, classloader statistics và heap analysis).

Bài này giải thích API chính xác, sự khác biệt giữa ba bộ đếm, cách đọc chúng trong ứng dụng và qua JMX, cách tính biến thiên/đánh giá classloader leaks, những giới hạn không thể suy ra từ một snapshot, và một ví dụ Java 17+ **tải một class bằng hai loaders độc lập** để quan sát số liệu tăng.

## 1. Vì sao cần quan sát class loading?

JVM nạp class khi chương trình cần dùng class ấy, thông qua hệ thống class loaders và các quy tắc JVMS. Ngoài mã ứng dụng, JVM có thể nạp thêm:

- Core Java/platform/library classes.
- Class của ứng dụng và dependency JARs.
- Dynamic proxies, lambda-related implementations, generated adapters hoặc bytecode do framework sinh.
- Class riêng từ plugin loaders, OSGi/container classloader hierarchies hoặc modular environments.
- Các class được nạp do reflection, `ServiceLoader` và dependency resolution khi cần.

Nếu một dịch vụ liên tục deploy/redeploy plugin, tạo classloader mới hoặc sinh class động, **tổng số class đã nạp** có thể tăng nhanh. Nếu các loader cũ không thể được thu gom, **số class hiện loaded** cũng có thể tích lũy, kéo theo sử dụng native metadata/metaspace và các tài nguyên khác.

`ClassLoadingMXBean` giúp trả lời những câu hỏi đầu tiên:

~~~text
Tổng số class từng được nạp tăng bao nhiêu?
              |
Hiện tại còn bao nhiêu class loaded?
              |
Đã có bao nhiêu class thực sự unloaded?
              |
Xu hướng qua nhiều phút/giờ/deploy là gì?
~~~

Nhưng nó **không trả về danh sách các class** hoặc nguyên nhân tại sao một loader chưa được GC. Chẩn đoán leak cần nhiều lớp bằng chứng hơn một biểu đồ class count.

## 2. Vị trí của ClassLoadingMXBean trong Java Management

Từ Java 5, JMX platform management cung cấp bean cho class loading. Trên Java 17, API thuộc module chuẩn `java.management`:

~~~text
Module java.management
   |
   +-- java.lang.management.ManagementFactory
   |
   +-- java.lang.management.ClassLoadingMXBean
   |
   +-- java.lang.management.MemoryMXBean
   +-- java.lang.management.MemoryPoolMXBean
   +-- java.lang.management.ThreadMXBean
   ...
~~~

Lấy một bean toàn JVM:

~~~java
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;

ClassLoadingMXBean bean =
        ManagementFactory.getClassLoadingMXBean();
~~~

Đây là **mảnh API minh họa**, có thể đặt trong `main`/method để dùng. Một JVM có **một instance class-loading management bean theo hợp đồng**, và bean này cũng được đăng ký trong **platform MBeanServer**.

ObjectName chuẩn:

~~~text
java.lang:type=ClassLoading
~~~

Có thể lấy từ:

~~~java
System.out.println(bean.getObjectName());
~~~

`ClassLoadingMXBean` kế thừa `PlatformManagedObject`, nên method `getObjectName()` trả đối tượng JMX `ObjectName` tương ứng.

### Ba counters cốt lõi

| Method | Kiểu trả về | Nghĩa |
|---|---|---|
| `getLoadedClassCount()` | `int` | Hiện tại JVM có bao nhiêu class đang loaded |
| `getTotalLoadedClassCount()` | `long` | Tổng class đã nạp kể từ lúc JVM bắt đầu |
| `getUnloadedClassCount()` | `long` | Tổng class đã unloaded kể từ lúc JVM bắt đầu |

Không nhầm `getLoadedClassCount()` với `getTotalLoadedClassCount()`: **loaded hiện tại** có thể giảm sau khi một số class được unload, còn **total loaded** là tổng tích lũy suốt vòng đời JVM và không giảm.

## 3. Mối quan hệ giữa tổng đã nạp, hiện có và đã dỡ

Về mặt diễn giải, các chỉ số mô tả:

~~~text
              Classes từng được loaded
                         |
               +---------+----------+
               |                    |
          Vẫn loaded          Đã unloaded
               |                    |
  getLoadedClassCount()  getUnloadedClassCount()
               \                    /
                \                  /
         getTotalLoadedClassCount()
             (tích lũy)
~~~

Trong một ảnh chụp lý tưởng cùng thời điểm:

~~~text
Total loaded ≈ Currently loaded + Total unloaded
~~~

Tại sao dùng dấu **`≈` thay vì bắt buộc `==` cho ba lời gọi liên tiếp**?

- Ba method được gọi **lần lượt**, không phải một API atomic snapshot của cả ba counters.
- JVM có thể load hoặc unload class giữa những lần đọc.
- Việc nạp lớp có thể diễn ra ngầm do compiler/JIT, reflection, instrumentation hoặc thread khác.
- Các implementation có thể có chi tiết đo lường khác nhau; ứng dụng không nên phụ thuộc vào invariant **đo được chính xác** trong mọi race.

Do đó, không lấy một mẫu rồi throw exception chỉ vì phép cộng số học **không tuyệt đối bằng nhau**. Khi cần phân tích xu hướng, lấy mẫu theo chu kỳ, lưu timestamp và phân biệt số liệu tích lũy với số liệu tức thời.

### Minh họa bằng số giả định

Giả sử một JVM ở thời điểm T1:

| Chỉ số | T1 |
|---|---:|
| Total loaded | 12.000 |
| Currently loaded | 11.850 |
| Total unloaded | 150 |

Đây là **số liệu ví dụ**, không phải output cố định của bất kỳ chương trình nào.

Sau một đợt tải plugins, tại T2:

| Chỉ số | T2 |
|---|---:|
| Total loaded | 12.500 |
| Currently loaded | 12.300 |
| Total unloaded | 200 |

Ta có:

- `Δtotal = 500`: JVM đã nạp thêm 500 class trong khoảng thời gian quan sát.
- `Δunloaded = 50`: thêm 50 class đã unload.
- `Δcurrent = 450`: số class hiện tại nhiều hơn 450.
- Chưa thể kết luận 450 class ấy thuộc plugin cụ thể nào hoặc là leak, vì hệ thống có thể đang warm up và cache class một cách hợp lệ.

Đừng dùng `getTotalLoadedClassCount()` làm “số class còn chiếm metaspace hiện tại”; đó là **counter tích lũy**.

## 4. Code đọc metrics đơn giản

Mảnh Java:

~~~java
ClassLoadingMXBean bean =
        ManagementFactory.getClassLoadingMXBean();

long total = bean.getTotalLoadedClassCount();
int current = bean.getLoadedClassCount();
long unloaded = bean.getUnloadedClassCount();

System.out.println("total=" + total);
System.out.println("current=" + current);
System.out.println("unloaded=" + unloaded);
~~~

Đây là đoạn minh họa, cần hai imports `ClassLoadingMXBean` và `ManagementFactory`. Output là **số thực tế của JVM**, phụ thuộc JDK, command-line flags, classpath, những lớp đã warm up và việc JVM có unload class hay không.

**Không có bộ số “total=1234/current=...” cố định** cho mọi máy. Khi viết bài học hoặc test, hãy kiểm tra **quan hệ, biến thiên hoặc định dạng**, không hard-code các số counters.

### Đọc đúng kiểu số

- `getTotalLoadedClassCount()` trả `long`.
- `getUnloadedClassCount()` trả `long`.
- `getLoadedClassCount()` trả `int`.

Đừng ép cả ba sang `int` hoặc parse chuỗi log để làm metrics nếu có thể gọi trực tiếp API. Với thời gian dài, các counters tích lũy có thể lớn.

### Quan sát theo mốc thời gian

~~~java
record Sample(
        long nanoTime,
        long totalLoaded,
        int currentlyLoaded,
        long unloaded) {
}

static Sample sample(ClassLoadingMXBean bean) {
    return new Sample(
            System.nanoTime(),
            bean.getTotalLoadedClassCount(),
            bean.getLoadedClassCount(),
            bean.getUnloadedClassCount());
}
~~~

Đây là **mảnh minh họa**, phù hợp khi `ClassLoadingMXBean` đã import. `System.nanoTime()` dành cho **thời gian trôi qua trong cùng process**, không phải wall-clock timestamp hay thời gian Unix. Nếu cần log sự kiện theo lịch, ghi thêm `Instant.now()`.

## 5. Ví dụ Java 17+ hoàn chỉnh: nạp một class từ hai loaders

Thí nghiệm dùng:

- Một class `experiment.Marker` được compile vào thư mục plugin riêng.
- Một host class `ClassLoadingLab` chỉ có trên application classpath.
- Hai `URLClassLoader` độc lập, cùng có root tới `out/plugin`.
- Hai runtime `Class<?>` cùng binary name nhưng khác defining loader.
- `ClassLoadingMXBean` được lấy mẫu trước và sau khi cả hai class được loaded.

Mục tiêu là kiểm tra **sự kiện và quan hệ có thể dự đoán** thay vì in số counters tuyệt đối không thể đoán trước giữa các JDK.

### 5.1. Cấu trúc project

~~~text
project/
  src/
    plugin/
      experiment/
        Marker.java
    host/
      ClassLoadingLab.java
  out/
    plugin/
    host/
~~~

### 5.2. File `src/plugin/experiment/Marker.java`

~~~java
package experiment;

public final class Marker {
    public String id() {
        return "marker";
    }
}
~~~

Class chỉ dùng core `java.base` types, không có dependency của host. Như vậy việc chọn `ClassLoader.getPlatformClassLoader()` làm parent cho hai plugin loaders trong thí nghiệm này không gây thiếu shared host API.

### 5.3. File `src/host/ClassLoadingLab.java`

~~~java
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class ClassLoadingLab {
    private static final String PLUGIN_CLASS = "experiment.Marker";

    record Sample(long total, int current, long unloaded) {
    }

    static Sample read(ClassLoadingMXBean bean) {
        return new Sample(
                bean.getTotalLoadedClassCount(),
                bean.getLoadedClassCount(),
                bean.getUnloadedClassCount());
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: ClassLoadingLab <plugin-dir>");
        }

        ClassLoadingMXBean bean =
                ManagementFactory.getClassLoadingMXBean();

        URL url = Path.of(args[0]).toAbsolutePath()
                .toUri().toURL();

        Sample before = read(bean);

        try (URLClassLoader loaderA = new URLClassLoader(
                    "plugin-A", new URL[] {url},
                    ClassLoader.getPlatformClassLoader());
             URLClassLoader loaderB = new URLClassLoader(
                    "plugin-B", new URL[] {url},
                    ClassLoader.getPlatformClassLoader())) {

            Class<?> first = loaderA.loadClass(PLUGIN_CLASS);
            Class<?> second = loaderB.loadClass(PLUGIN_CLASS);

            Sample after = read(bean);

            System.out.println("mxbean name correct: "
                    + bean.getObjectName().getCanonicalName()
                        .equals("java.lang:type=ClassLoading"));

            System.out.println("same binary name: "
                    + first.getName().equals(second.getName()));
            System.out.println("same runtime class: "
                    + (first == second));

            System.out.println("A defines its class: "
                    + (first.getClassLoader() == loaderA));
            System.out.println("B defines its class: "
                    + (second.getClassLoader() == loaderB));

            System.out.println("total loaded grew by at least two: "
                    + (after.total() >= before.total() + 2));

            System.out.println("unloaded count nondecreasing: "
                    + (after.unloaded() >= before.unloaded()));

            System.out.println("current loaded count positive: "
                    + (after.current() > 0));
        }
    }
}
~~~

**Chi tiết quan trọng:**

1. `getClassLoadingMXBean()` cung cấp một bean quan sát **toàn JVM**, không riêng loader A/B.
2. `Sample before` được lấy **trước** khi tạo và dùng hai URLClassLoader.
3. Parent là platform loader, không có class `experiment.Marker` trên application classpath; cả A và B phải tự tìm `experiment/Marker.class` dưới URL root.
4. `loaderA.loadClass` và `loaderB.loadClass` định nghĩa hai runtime classes với **cùng binary name** nhưng khác defining loaders. Do vậy `first == second` là `false`.
5. Hai class definitions mới đóng góp vào **total loaded**; có thể còn những class khác do JVM tải trong quá trình thí nghiệm, nên kiểm tra `>= 2` chứ **không** kiểm tra đúng bằng 2.
6. `getUnloadedClassCount()` là counter tích lũy, không giảm trong một JVM; khi còn giữ `first`/`second`/loaders trong khối, **không** khẳng định chúng đã unloaded.
7. `getLoadedClassCount()` ở trạng thái JVM hoạt động là giá trị dương; không so `after.current() - before.current() == 2` do concurrency/unloading có thể thay đổi giá trị hiện tại.

### 5.4. Compile và chạy bằng JDK 17+

Từ thư mục `project`, chạy bằng Bash trên Linux/macOS hoặc môi trường Bash tương thích:

~~~bash
mkdir -p out/plugin out/host

javac -d out/plugin src/plugin/experiment/Marker.java

javac -d out/host src/host/ClassLoadingLab.java

java -cp out/host ClassLoadingLab out/plugin
~~~

**Không thêm `out/plugin` vào application classpath**, nếu không parent/caller có thể nhìn thấy class trước và không còn đảm bảo hai child loaders tự định nghĩa.

Trên Windows PowerShell, tạo output directories bằng `New-Item -ItemType Directory -Force` rồi chạy `javac`/`java` tương ứng. Vì classpath host chỉ có **một** phần tử, không cần dấu phân cách `:` hoặc `;`.

### 5.5. Stdout dự kiến

~~~text
mxbean name correct: true
same binary name: true
same runtime class: false
A defines its class: true
B defines its class: true
total loaded grew by at least two: true
unloaded count nondecreasing: true
current loaded count positive: true
~~~

Output trên dựa vào class file đúng, không có bản `experiment.Marker` ở parent, hai URLClassLoader khác nhau và JVM tuân thủ counters `ClassLoadingMXBean`. **Số đếm thực tế không được in**, vì chúng có thể thay đổi theo JDK/OS/classpath/GC.

### 5.6. Vì sao đóng loaders không đồng nghĩa class unloading?

Khi thoát khối `try-with-resources`, các `URLClassLoader` đóng các tài nguyên file/JAR đã mở. Điều này **không buộc** VM dỡ `experiment.Marker` ngay khi kết thúc khối.

Để có cơ hội class unloading, phải không còn strong references tới:

- Loader instances, `Class<?>` hoặc objects do class ấy tạo.
- Threads, scheduled tasks, TCCL hoặc `ThreadLocal` trỏ tới loader.
- Reflection `Method`/`Constructor`, `MethodHandle`, service providers hoặc caches của framework.
- JNI global references, listeners, callbacks hoặc registries giữ plugin generation.

Kể cả khi mọi reference đã giải phóng, JVM có thể chọn thời điểm GC/unloading khác nhau. **`System.gc()` không phải cam kết unload class**, và việc `getUnloadedClassCount()` không tăng ngay **không chứng minh có leak**.

## 6. Thu thập metrics theo chu kỳ trong service thật

Trong production, snapshot một lần không đủ. Nên lấy mẫu tại chu kỳ xác định (ví dụ 10–60 giây tùy nhu cầu) và kèm timestamp:

~~~java
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.time.Instant;

public final class ClassLoadingSnapshot {
    public static void main(String[] args) {
        ClassLoadingMXBean bean =
                ManagementFactory.getClassLoadingMXBean();

        System.out.println("timestamp=" + Instant.now());
        System.out.println("loaded_current="
                + bean.getLoadedClassCount());
        System.out.println("loaded_total="
                + bean.getTotalLoadedClassCount());
        System.out.println("unloaded_total="
                + bean.getUnloadedClassCount());
    }
}
~~~

Đây là **chương trình độc lập** để lấy snapshot hiện tại. Chạy:

~~~bash
javac ClassLoadingSnapshot.java
java ClassLoadingSnapshot
~~~

Output là **timestamp và những số liệu động**, ví dụ minh họa:

~~~text
timestamp=2026-10-24T12:00:00Z
loaded_current=1200
loaded_total=1200
unloaded_total=0
~~~

Các con số/timestamp **chỉ mang tính minh họa**, không phải stdout cố định. Điều quan trọng là API/field names và tính thời điểm.

**Lưu ý:** chạy `java ClassLoadingSnapshot` nhiều lần tạo **nhiều JVM khác nhau**, vì vậy không tạo chuỗi timeseries của **cùng một process**. Để đo xu hướng của ứng dụng đang chạy, thu metrics từ **bên trong JVM đó** hoặc dùng JMX/metrics exporter gắn tới cùng process.

### Lưu delta thay vì chỉ lưu giá trị tuyệt đối

Nếu có hai mẫu T1/T2:

~~~text
total_loads_per_interval  = totalLoaded(T2) - totalLoaded(T1)
unloads_per_interval      = unloaded(T2) - unloaded(T1)
current_loaded_change     = currentlyLoaded(T2) - currentlyLoaded(T1)
rate_total_loads_per_sec  = deltaTotal / elapsedSeconds
~~~

Tính rate bằng **thời gian thực tế giữa hai lần sample**. Đừng dùng “tổng class từng loaded” để suy ra classload rate trực tiếp nếu không có mẫu trước.

**Counter reset:** tất cả chỉ số tích lũy bắt đầu lại khi JVM restart. Khi làm time-series, gắn **process identity / start time** và xử lý reset; không tính delta âm giữa hai JVM thành “JVM unload hàng ngàn class”.

### Sơ đồ dashboard

~~~text
JVM process (PID + startTime)
         |
         +-- loaded_class_count (gauge)
         |
         +-- total_loaded_class_count (cumulative counter)
         |
         +-- total_unloaded_class_count (cumulative counter)
         |
         +-- load_rate = delta(total) / delta(time)
         |
         +-- unload_rate = delta(unloaded) / delta(time)
         |
         +-- metaspace_used / committed (nguồn memory MXBean khác)
         |
         +-- loader count / GC / deployment events (nguồn bổ sung)
~~~

`getLoadedClassCount()` phù hợp như **gauge**; `getTotalLoadedClassCount()` và `getUnloadedClassCount()` phù hợp như **counters**. Đừng publish tất cả ba metric theo kiểu counter hoặc đánh đồng class count với byte-size memory.

## 7. Đọc ClassLoadingMXBean qua JMX

`ClassLoadingMXBean` có ObjectName cố định:

~~~text
java.lang:type=ClassLoading
~~~

Trong `MBeanServer`, những attributes tương ứng thường là:

| Attribute JMX | Java method |
|---|---|
| `LoadedClassCount` | `getLoadedClassCount()` |
| `TotalLoadedClassCount` | `getTotalLoadedClassCount()` |
| `UnloadedClassCount` | `getUnloadedClassCount()` |
| `Verbose` | `isVerbose()`, `setVerbose(boolean)` |

### 7.1. Ví dụ đọc MBeanServer ngay trong JVM

~~~java
import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;

public final class JmxClassLoadingSample {
    public static void main(String[] args) throws Exception {
        MBeanServer server =
                ManagementFactory.getPlatformMBeanServer();
        ObjectName key =
                new ObjectName("java.lang:type=ClassLoading");

        Object loaded =
                server.getAttribute(key, "LoadedClassCount");
        Object total =
                server.getAttribute(key, "TotalLoadedClassCount");
        Object unloaded =
                server.getAttribute(key, "UnloadedClassCount");

        System.out.println("loaded numeric: "
                + (loaded instanceof Number));
        System.out.println("total numeric: "
                + (total instanceof Number));
        System.out.println("unloaded numeric: "
                + (unloaded instanceof Number));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac JmxClassLoadingSample.java
java JmxClassLoadingSample
~~~

Stdout dự kiến:

~~~text
loaded numeric: true
total numeric: true
unloaded numeric: true
~~~

Đây là một cách minh họa **JMX attribute access**, không cần viết riêng MXBean implementation. Đừng kỳ vọng JMX tự trả danh sách những class đang loaded; các attributes này vẫn là counters tổng JVM.

### 7.2. Xem qua JConsole/JMX client

Với process Java đã chạy và cho phép attach/JMX theo môi trường:

1. Khởi động **JConsole** từ JDK hoặc một JMX client đã cấu hình phù hợp.
2. Kết nối đúng process/JVM.
3. Mở phần **MBeans**, tìm `java.lang:type=ClassLoading`.
4. Đọc `LoadedClassCount`, `TotalLoadedClassCount`, `UnloadedClassCount` và `Verbose`.
5. Ghi lại mốc trước/sau khi deploy hoặc unload plugin, rồi so với memory/GC/logs.

Kết nối **remote JMX** phải được bảo vệ đúng authentication, authorization và network policy; không bật JMX port công khai chỉ để đọc counters. Trong một số container/production environments, attach có thể bị vô hiệu hóa hoặc cần cấu hình riêng.

## 8. `isVerbose()` và `setVerbose(boolean)`

Bên cạnh counters, bean cung cấp:

~~~java
boolean enabled = bean.isVerbose();
bean.setVerbose(true);
~~~

Đây là mảnh API, yêu cầu `bean` là `ClassLoadingMXBean`. `setVerbose(true)` yêu cầu VM bật thêm output khi class load/unload; `setVerbose(false)` tắt ở phạm vi **toàn JVM**.

**Những điểm cần nhớ:**

- Đây **không phải** setting chỉ cho một classloader hoặc plugin.
- Thay đổi ảnh hưởng đến các threads/code khác trong cùng process.
- Format và nơi phát output là **implementation-dependent**, không thể cam kết mỗi JDK in một format giống nhau.
- Có thể gây output/log volume đáng kể, ảnh hưởng hiệu năng và khả năng quan sát log.
- Theo hợp đồng API Java 17, có thể phát sinh `SecurityException` nếu môi trường có security manager và không đủ `ManagementPermission("control")`.
- Nhiều thread có thể gọi `setVerbose`, nhưng chúng thay đổi **một global setting**, không phải một setting tách biệt per caller.

Không nên đặt:

~~~java
bean.setVerbose(true);
~~~

trong mọi request handler chỉ để debug một plugin; thao tác đó tạo side effect toàn JVM. Trong môi trường production, ưu tiên JVM logging/JFR có cấu hình, thời hạn và chính sách quan sát rõ ràng.

### Bật class loading logs trên HotSpot Java 17

Ví dụ:

~~~bash
java -Xlog:class+load=info,class+unload=info \
     -cp out/host ClassLoadingLab out/plugin
~~~

Đây là **lệnh chẩn đoán trên HotSpot/JDK phù hợp**. Output sẽ có thêm nhiều log lines, không còn giống stdout gọn ở phần ví dụ. Việc log ở mức `info` thường giúp thấy nhiều class đã load và nguồn của chúng, nhưng có thể không giải thích đầy đủ **tại sao** mỗi class được giữ sống.

JDK khác có logging flags/format khác; luôn kiểm tra hỗ trợ của runtime thực.

## 9. Dùng metrics để nhận diện classloader leak

Một tình huống điển hình:

~~~text
Plugin generation 1 -> loader A -> 100 classes
Plugin generation 2 -> loader B -> 100 classes
Plugin generation 3 -> loader C -> 100 classes
...
Host vẫn giữ reference tới A, B, C
       |
       v
getLoadedClassCount() tăng dài hạn
getTotalLoadedClassCount() tăng dài hạn
getUnloadedClassCount() tăng ít hoặc không tăng
Metaspace có thể tăng
~~~

Tuy nhiên, **đây mới là tín hiệu nghi ngờ, không phải bằng chứng cuối cùng**.

### 9.1. Mô hình phân tích trước/sau deploy

| Thời điểm | Total | Current | Unloaded | Ghi chú |
|---|---:|---:|---:|---|
| Trước deploy | 10.000 | 9.900 | 100 | Baseline |
| Sau deploy plugin | 10.300 | 10.200 | 100 | Nạp class plugin |
| Sau undeploy, chờ và GC quan sát | 10.350 | 10.050 | 300 | Một phần đã unload |
| Sau nhiều redeploy | 12.500 | 11.900 | 600 | Cần điều tra xu hướng |

Tất cả **giá trị ở bảng là dữ liệu giả định để học cách đọc**, không phải kết quả thực nghiệm.

Đặc biệt, chỉ từ ví dụ này không thể khẳng định chính xác class nào bị giữ hoặc GC có tạo class unloading ngay. Cần quan sát **trong cùng JVM** và so sánh với deployment events, memory pools, classloader statistics, heap dump và lifecycle của plugin.

### 9.2. Vì sao total luôn tăng không đồng nghĩa leak?

`TotalLoadedClassCount` là counter tích lũy nên **sẽ không giảm**, kể cả khi JVM unload tất cả class thuộc plugin cũ. Một hệ thống khỏe mạnh vẫn có thể tăng total hàng giờ vì:

- Framework khởi tạo những features mới.
- Dynamic proxies/lambdas/generated classes xuất hiện khi tải workload mới.
- Plugins được cập nhật hợp lệ.
- New application code paths được chạm lần đầu.
- JVM/app warm-up chưa kết thúc.

Tín hiệu đáng quan tâm hơn là **current loaded class count** tiếp tục tăng sau nhiều chu kỳ ổn định và không giảm khi lifecycle/GC đủ điều kiện, đặc biệt khi metaspace và số lượng loader cũng tăng.

### 9.3. Vì sao unloaded chưa tăng không khẳng định leak?

Class unloading thường gắn với **garbage collection và điều kiện reachability của defining loader**. Không có bảo đảm `close()` hoặc `System.gc()` khiến count tăng ngay. JVM flags, collector, memory pressure và references còn giữ class/loader đều ảnh hưởng.

Không đặt test:

~~~java
System.gc();
assert bean.getUnloadedClassCount() > before;
~~~

làm **điều kiện pass bắt buộc** cho mọi JDK; đây là anti-pattern vì GC/unloading không deterministic.

## 10. Khi số class tăng, cần tìm holder ở đâu?

`ClassLoadingMXBean` chỉ có tổng số liệu toàn JVM; nó không chỉ ra cache, thread hay plugin loader nào giữ classes. Nếu `LoadedClassCount` cùng metaspace và số loader tăng qua nhiều chu kỳ redeploy, hãy khảo sát các nguồn giữ phổ biến: static registries của host, listener/callback, queued task, TCCL của thread pool, `ThreadLocal`, provider cache, JNI references và plugin-owned executors.

Để phân biệt chúng, kết hợp `jcmd VM.classloader_stats`/JFR với heap dump và **Path to GC Roots**; xác nhận holder vẫn không cần thiết sau khi plugin stop rồi mới kết luận là leak. Bài về JFR, heap dump và GC roots hướng dẫn chứng minh retention, còn bài này tập trung **đếm class và lập chuỗi thời gian các metrics**.
## 11. Kết hợp với metaspace và memory metrics

Một class được loaded tiêu thụ metadata/memory ở JVM, nhưng **không có phép quy đổi cố định “1 class = X bytes metaspace”**. Size phụ thuộc methods, constant pool, annotations, class structure, runtime implementation và nhiều yếu tố.

`ClassLoadingMXBean` **không có** method kiểu:

~~~java
bean.getMetaspaceBytes();
~~~

Đây là **API không tồn tại**, chỉ để cảnh báo ngộ nhận. Để đo memory, dùng `MemoryPoolMXBean`, `MemoryMXBean`, JVM tools hoặc JFR.

Ví dụ đọc các memory pools có tên chứa Metaspace (phù hợp HotSpot, **không portable theo tên pool**):

~~~java
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

for (MemoryPoolMXBean pool
        : ManagementFactory.getMemoryPoolMXBeans()) {
    if (pool.getName().contains("Metaspace")) {
        MemoryUsage usage = pool.getUsage();
        if (usage != null) {
            System.out.println(usage.getUsed());
        }
    }
}
~~~

Đây là mảnh minh họa; tên pool có thể khác theo JVM, và output bytes phụ thuộc runtime. Không dùng số `Metaspace used` để suy ra số class bị leak một cách một-một.

### Khi nào cần thêm công cụ?

| Câu hỏi | Công cụ / tín hiệu |
|---|---|
| Tổng số class đã được load? | `ClassLoadingMXBean.getTotalLoadedClassCount()` |
| Hiện tại còn bao nhiêu class? | `getLoadedClassCount()` |
| Đã unload bao nhiêu? | `getUnloadedClassCount()` |
| Class nào được load từ JAR nào? | Unified JVM class-loading logs, JFR/JVM-specific tools |
| Bao nhiêu classloaders đang sống? | JFR, `jcmd`/VM-specific classloader stats hoặc heap analysis |
| Metaspace đang tăng? | `MemoryPoolMXBean`/JVM memory metrics |
| Tại sao plugin loader không unload? | Heap dump reference paths, thread/TCCL inspection, native references |
| Classloader có đang giữ JAR handles? | Lifecycle log, URLClassLoader close, OS file handle tools |

## 12. Sự khác biệt giữa class loading, linking và initialization

Một source Java được compile thành `.class` **không đồng nghĩa JVM đã loaded nó**. Loader phải tìm định nghĩa phù hợp, JVM phải link/verify/resolve theo quy tắc và class có thể được initialized khi active use.

~~~text
Source .java
     |
   javac
     |
 .class file / JAR entry
     |
 ClassLoader.loadClass(...)
     |
 JVM class loading
     |
 Linking (verify/prepare/resolve theo nhu cầu)
     |
 Initialization (static init nếu được yêu cầu)
     |
 Instance/method execution
~~~

`ClassLoadingMXBean` quan sát **class loading/unloading counts**, không cung cấp một counter riêng cho mọi bước verification, linking hoặc initialization. Đừng dùng “totalLoaded tăng 1” để kết luận constructor của class đã chạy.

Ví dụ:

~~~java
Class<?> type =
        Class.forName("experiment.Marker", false, pluginLoader);
~~~

Đây là mảnh minh họa với `pluginLoader` phù hợp. Lời gọi có thể load/link class nhưng **không yêu cầu class initialize** do `initialize=false`. TotalLoaded có thể tăng, dù static initializer chưa chạy.

### Class identity với nhiều loaders

Hai loaders có thể nạp cùng tên class nhưng tạo **hai runtime types** riêng:

~~~text
("experiment.Marker", LoaderA) !=
("experiment.Marker", LoaderB)
~~~

`ClassLoadingMXBean` **không gộp hai types chỉ vì tên giống nhau** thành một entry logic theo tên. Nó đo các runtime classes được nạp trong JVM. Đây là lý do số loaded classes tăng khi tạo plugin loaders định nghĩa các bản riêng của cùng dependency.

Không vì thế mà tạo một loader mới cho mỗi request: nhiều bản class definitions trùng có thể gây tăng memory và classloading overhead.

## 13. Những sai lầm thường gặp khi dùng ClassLoadingMXBean

1. **“Total loaded là số class còn trong bộ nhớ.”** Sai: đó là số tích lũy kể từ khi JVM khởi động.
2. **“Unloaded là số class đã được xóa khỏi JAR.”** Sai: đây là count runtime class unloading, không liên quan xóa file JAR.
3. **“Loaded hiện tại không bao giờ giảm.”** Sai: JVM có thể unload classes khi điều kiện GC/reachability thỏa.
4. **“`ClassLoadingMXBean` trả class names.”** Sai: nó cung cấp counts, không phải danh sách class/JAR/loader.
5. **“`ClassLoadingMXBean` thuộc từng ClassLoader.”** Sai: chỉ số có phạm vi **toàn JVM**.
6. **“Một class name chỉ được đếm đúng một lần trong toàn JVM.”** Sai: cùng binary name do loaders khác nhau define là runtime types khác nhau.
7. **“Ba lời gọi đọc counters là một atomic snapshot.”** Sai: class loading/unloading có thể xảy ra giữa chúng.
8. **“`getTotalLoadedClassCount()` phải giảm khi GC.”** Sai: nó là counter tích lũy.
9. **“Unloaded count chưa tăng sau `System.gc()` là leak chắc chắn.”** Sai: GC/unloading không được cam kết đồng bộ ngay.
10. **“Loaded count tăng một lần sau startup chứng tỏ leak.”** Sai: warm-up và feature loading bình thường cũng tăng.
11. **“Metaspace bytes có thể tính bằng loaded classes × một hằng số.”** Sai: metadata footprint biến động.
12. **“`setVerbose(true)` chỉ bật log cho current thread.”** Sai: global JVM setting.
13. **“`setVerbose(true)` luôn in trên stdout theo cùng format.”** Sai: output format/stream do JVM implementation quyết định.
14. **“Đọc bean bằng JMX bắt buộc mở một server remote.”** Sai: có thể lấy trong cùng JVM qua `ManagementFactory` hoặc platform MBeanServer.
15. **“Bean có thể bắt JVM unload đúng một class cụ thể.”** Sai: không có API unload từng class theo tên.
16. **“Plugin loader close tương đương class unloading.”** Sai: close handles và unloading là hai cơ chế khác.
17. **“Đếm class đủ để chẩn đoán mọi memory leak.”** Sai: còn threads, heap objects, native memory, metaspace và classloader reachability.
18. **“Nhiều tiến trình có thể cộng counters mà không cần identity.”** Sai: cần gắn PID/JVM start time để xử lý counter reset và tránh nhầm process.
19. **“Một `Class<?>` từ loader A có thể tái dùng như class của loader B.”** Sai: runtime class identity phụ thuộc defining loader.
20. **“`getLoadedClassCount()` phản ánh số file `.class` trong package/JAR.”** Sai: nó đếm runtime classes đang loaded, không quét filesystem.

## 14. Checklist observability trước khi kết luận classloader leak

- [ ] Lấy bean đúng qua `ManagementFactory.getClassLoadingMXBean()`.
- [ ] Ghi rõ cả ba chỉ số: total loaded, current loaded, total unloaded.
- [ ] Phân biệt **counter tích lũy** với **gauge tức thời**.
- [ ] Gắn sample timestamp, PID/process identity và JVM start time.
- [ ] Lấy nhiều mẫu ở cùng một JVM, không lấy nhiều lần chạy `java` khác nhau.
- [ ] Tính delta và rate đúng khoảng thời gian.
- [ ] So sánh trước/sau deployment, redeploy, shutdown theo cùng workload.
- [ ] Cho JVM đủ thời gian GC/unloading khi đánh giá xu hướng; không ép một lần GC là test pass.
- [ ] Quan sát Metaspace và số loaders từ nguồn phù hợp nếu nghi leak.
- [ ] Lưu log class sources/loader IDs khi cần tìm plugin cụ thể.
- [ ] Kiểm tra caches, TCCL, threads, scheduled tasks, callbacks, registries, JNI refs.
- [ ] Có cơ chế drain rồi đóng URLClassLoader nếu dùng plugin JAR.
- [ ] Không bật `setVerbose(true)` vô thời hạn trên JVM nhiều traffic.
- [ ] Giới hạn quyền truy cập JMX; tránh remote exposure không bảo vệ.
- [ ] Không hard-code expected counters của bất kỳ JVM cụ thể nào.
- [ ] Nếu ứng dụng modular, phân biệt class loading stats với module readability/opens/exports.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** `getTotalLoadedClassCount()` và `getLoadedClassCount()` khác nhau như thế nào?

**Đáp án:** Total là tổng lớp từng loaded kể từ lúc JVM khởi động; current là số lớp hiện loaded tại thời điểm đọc. Total không giảm vì class unloading.

**Câu 2.** `getUnloadedClassCount()` tăng sau một giai đoạn có nghĩa gì?

**Đáp án:** JVM đã unload thêm class trong giai đoạn đó. Chưa thể xác định class hoặc loader cụ thể nào nếu không có thêm nguồn chẩn đoán.

**Câu 3.** Có thể suy ra classloader bị leak chỉ vì TotalLoaded liên tục tăng không?

**Đáp án:** Không. Total là cumulative, tăng theo hoạt động hợp lệ. Cần xem current, unloaded, metaspace, loader count và retention/lifecycle theo thời gian.

**Câu 4.** `LoadedClassCount` có phân theo plugin JAR không?

**Đáp án:** Không. Đây là số liệu toàn JVM. Muốn truy class/loader cụ thể cần logs, JFR, jcmd hoặc heap analysis.

**Câu 5.** Hai URLClassLoader định nghĩa cùng `experiment.Marker`. Có hai runtime class types không?

**Đáp án:** Có. Cùng binary name nhưng khác defining loader tạo hai `Class<?>` khác identity; chúng đóng góp vào classloading counters.

**Câu 6.** `URLClassLoader.close()` có bảo đảm `UnloadedClassCount` tăng ngay không?

**Đáp án:** Không. Close file/JAR handles, còn class unloading phụ thuộc references/GC.

**Câu 7.** `setVerbose(true)` có thay đổi riêng một plugin không?

**Đáp án:** Không. Nó thay đổi verbose class-loading output của toàn JVM; nhiều threads có thể bị ảnh hưởng.

**Câu 8.** Cần ObjectName nào khi đọc qua JMX?

**Đáp án:** `java.lang:type=ClassLoading`, với attributes như `LoadedClassCount`, `TotalLoadedClassCount` và `UnloadedClassCount`.

**Câu 9.** Vì sao ba counters đọc liên tiếp đôi khi không khớp phép tính `total == current + unloaded` một cách chính xác?

**Đáp án:** Vì các lời gọi không tạo một atomic snapshot; JVM có thể load/unload giữa các lần đọc.

**Câu 10.** Sau một tuần, current loaded và metaspace tăng sau mỗi redeploy, unloaded hầu như không tăng. Bước kế tiếp là gì?

**Đáp án:** Điều tra retention: heap dump, loader statistics, threads/TCCL, static caches, service providers, JNI refs và plugin lifecycle. Counters là tín hiệu, chưa đủ làm kết luận cuối.

## 16. Sơ đồ quyết định chẩn đoán

~~~text
                 Quan sát class loading trong JVM
                              |
                  ManagementFactory.get...
                              |
                  ClassLoadingMXBean
                      /       |       \
                     /        |        \
            total loaded    current    unloaded
             (counter)       (gauge)   (counter)
                    \         |        /
                     \        |       /
                       Samples + time
                              |
                       Tính deltas/rates
                              |
                    Có tăng bất thường?
                        /           \
                      Không          Có
                       |             |
                  Lưu baseline     Xem Metaspace,
                                   loader count,
                                   deployment events
                                       |
                               Nghi vấn retention?
                                   /        \
                                 Không       Có
                                   |          |
                              Tiếp tục       Heap dump,
                              quan sát       TCCL/threads/
                                             callbacks
                                                   |
                                           Sửa lifecycle,
                                           đo lại sau deploy
~~~

**Kết luận:** `ClassLoadingMXBean` là công cụ **đo ba mặt của class loading**: **tổng nạp**, **đang loaded**, **đã dỡ**. Bộ đếm tích lũy giúp đo khối lượng hoạt động; gauge hiện tại giúp quan sát retention; nhưng việc xác định classloader leak phải dựa vào **xu hướng nhiều mẫu cùng JVM** và các công cụ chẩn đoán loader/memory chuyên biệt. Không lấy việc total tăng hoặc GC chưa unload ngay làm kết luận một chiều.

### Tài liệu chính thức

- [Java SE 17 — ClassLoadingMXBean](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ClassLoadingMXBean.html)
- [Java SE 17 — ManagementFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ManagementFactory.html)
- [Java SE 17 — PlatformManagedObject / ObjectName](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/PlatformManagedObject.html)
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html)
- [Java SE 17 — MemoryPoolMXBean](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryPoolMXBean.html)
- [Java 17 HotSpot — Unified JVM Logging](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-diagnostics-009">JFR, heap dump và đường tham chiếu giữ class loader</a>

<details>
<summary>Xem chi tiết</summary>

# JFR, heap dump và đường tham chiếu giữ class loader

Trong ứng dụng Java có plugin, application server, hot redeploy, script engine hoặc custom `URLClassLoader`, việc **tạo loader mới không đồng nghĩa loader cũ sẽ được garbage collector (GC) thu hồi ngay**. Class loader có thể còn được giữ bởi một đối tượng trong cache tĩnh, một worker thread giữ thread context class loader (TCCL), `ThreadLocal`, provider của `ServiceLoader`, listener, MBean, callback, JNI reference hoặc một đường tham chiếu bất ngờ.

Nếu các deployment đã dừng vẫn còn loader/class metadata ở **Metaspace**, JVM có thể tốn thêm bộ nhớ, tích lũy nhiều thế hệ class cùng tên và về lâu dài phát sinh áp lực GC hoặc `OutOfMemoryError: Metaspace`. Tuy nhiên, **thấy loader tồn tại chưa đủ để kết luận leak**: loader có thể còn đang phục vụ tác vụ hợp lệ, GC chưa đủ điều kiện chạy, hoặc số liệu đang phản ánh cached metadata theo vòng đời dự kiến.

Cách điều tra có hệ thống là kết hợp:

1. **JDK Flight Recorder (JFR)** để quan sát hiện tượng theo **thời gian**: class loading/unloading, class loader statistics, allocation/old-object samples và GC.
2. **`jcmd`/thống kê class loader** để xem snapshot tình trạng loader, số class và xu hướng.
3. **Heap dump HPROF** để thấy đồ thị đối tượng tại **một thời điểm**.
4. **Path to GC Roots / Dominator Tree** trong Eclipse Memory Analyzer (MAT) để xác định **tham chiếu mạnh nào đang giữ loader hay object thuộc plugin**.
5. **Sửa nguyên nhân và đo lại theo cùng kịch bản**, không chỉ tăng `-XX:MaxMetaspaceSize` hoặc ép `System.gc()`.

Bài tập trung vào Java 17+ và HotSpot khi giới thiệu lệnh diagnostic. Tên command, event JFR, quyền attach hoặc kết quả GC có thể khác giữa nhà cung cấp JVM/OS; cần xác minh bằng `jcmd <PID> help` và cấu hình thực tế. Các log, PID, địa chỉ object và trạng thái GC là dữ liệu động, **không nên công bố stdout cố định nếu runtime không bảo đảm**.

## 1. Những thứ phải phân biệt trước khi tìm leak

| Khái niệm | Ý nghĩa | Kết luận không được suy vội |
| --- | --- | --- |
| **Class loader còn reachable** | Có đường tham chiếu đang giữ object loader. | Không tự chứng minh một leak; có thể là loader còn được sử dụng. |
| **Class còn loaded** | JVM còn định nghĩa Class/type metadata có thể sử dụng. | Không có nghĩa loader đã leak vĩnh viễn. |
| **Class unloading** | JVM thu hồi metadata của class/loader khi điều kiện reachability và GC thích hợp. | Không bắt buộc diễn ra ngay sau `close()` hoặc `System.gc()`. |
| **Heap used** | Bộ nhớ Java heap của instances, maps, arrays, Class objects và object khác. | Không đồng nhất với toàn bộ Metaspace/native memory. |
| **Metaspace** | Vùng native memory JVM quản lý nhiều loại class metadata (HotSpot). | Tăng Metaspace không tự chứng minh một plugin loader bị giữ. |
| **Shallow heap** | Kích thước bản thân một object trong heap. | Không nói hết bộ nhớ nó gián tiếp giữ. |
| **Retained heap** | Lượng heap có thể giải phóng nếu bỏ đi một object/dominator theo đồ thị. | Không đồng nghĩa toàn bộ native memory/Metaspace do loader chiếm. |

**Bất biến cần nhớ:** class được định danh bằng **binary name và defining class loader**. Hai phiên bản `com.acme.plugin.Worker` cùng tên nhưng loader khác nhau là hai type JVM khác nhau. Khi mỗi lần redeploy tạo một loader mới, số class/data của deployment cũ có thể tiếp tục tồn tại nếu loader cũ còn reachable.

### Khi nào một loader có thể được thu hồi?

Để loader và những class được nó định nghĩa trở nên đủ điều kiện thu gom/unloading, không thể còn những **tham chiếu mạnh giữ chúng sống** theo GC/runtime: object instance của class plugin, `Class<?>`, phản chiếu/method handles, loader object, thread đang chạy code của class đó, JNI global references hoặc những thành phần khác có thể tiếp tục giữ class/loader. Sau khi đủ điều kiện, JVM vẫn quyết định **khi nào** và **có** thực hiện GC/class unloading theo collector/cấu hình hiện hành hay không.

**`URLClassLoader.close()` chỉ đóng tài nguyên loader**, chẳng hạn JAR handles được quản lý theo API, **không tự xóa Class objects và không cưỡng bức class unloading**. Tương tự, bỏ một biến cục bộ hoặc gọi `System.gc()` không tạo hợp đồng rằng loader phải biến mất ngay.

## 2. Đường tham chiếu (retention path) là trọng tâm chẩn đoán

Một class loader bị giữ khi có **đường tham chiếu giữ sống** từ một **GC root** (hoặc điều kiện root của JVM) tới loader, hoặc tới một object/class mà sự sống của nó kéo theo loader.

Ví dụ:

~~~text
GC Root: class host tồn tại suốt JVM
    |
    v
HostRegistry.CACHE (static Map)
    |
    v
HashMap entry -> plugin instance
    |
    v
demo.plugin.Payload instance
    |
    v
Class metadata của demo.plugin.Payload
    |
    v
defining URLClassLoader
    |
    v
metadata/các class thuộc deployment cũ
~~~

Trong heap analysis, đường từ **instance → class → class loader** có thể được MAT thể hiện bằng quan hệ class/loader do công cụ mô hình hóa; đó không nhất thiết là một field Java thông thường bạn có thể gọi bằng reflection.

Các root hoặc holder thường gặp:

| Holder/đường giữ | Ví dụ sai về lifecycle | Hướng xử lý |
| --- | --- | --- |
| **Static cache của host** | Map/singleton ở loader sống lâu giữ plugin object, `Class<?>`, method handle. | Xóa entry khi undeploy; tránh global strong cache không có policy. |
| **Thread.contextClassLoader** | Worker pool của host còn TCCL trỏ `OldPluginLoader`. | Ghi nhớ và restore TCCL bằng `finally`; dừng các worker riêng. |
| **ThreadLocalMap** | Long-lived thread giữ `ThreadLocal` value thuộc plugin. | `remove()` trong đúng thread; scope hóa context; cleanup executors. |
| **Scheduler/executor queue** | `Runnable`/`Callable`/`CompletableFuture` callback capture instance plugin. | Ngừng nhận việc, cancel/drain queue, shutdown đúng lifecycle. |
| **Listeners/registries** | Event bus, JDBC driver manager, JMX MBean, logging manager giữ handler của plugin. | Unregister/deregister vào lifecycle hook. |
| **ServiceLoader cache** | Host lưu `ServiceLoader` hoặc provider instances qua nhiều deployment. | Bỏ reference tới loader/provider cũ; tạo loader theo deployment. |
| **Reflection/caches** | Cache `Class<?>`, `Constructor<?>`, `Method`, `MethodHandle` hoặc proxy metadata. | Scope cache theo deployment, sử dụng weak keys đúng thiết kế khi phù hợp. |
| **JNI/global/native hooks** | Native component giữ global reference tới plugin object. | Release JNI refs/native registrations theo API. |

**ThreadLocal key yếu nhưng value mạnh:** `ThreadLocalMap` dùng weak reference cho key, song value có thể được giữ mạnh bởi entry của worker còn sống, kể cả khi key mất tham chiếu. Không kết luận “ThreadLocal yếu nên tự an toàn”.

**Class loader không phải rò rỉ chỉ vì có một path:** GC root path mô tả **vì sao còn sống**. Phải đối chiếu liệu path có còn **cần thiết về nghiệp vụ** sau khi plugin/deployment kết thúc. Một loader đang xử lý request có thể tồn tại hợp lệ, trong khi loader phiên bản cũ qua mười lần redeploy và không còn công việc là tín hiệu nghi ngờ rõ hơn.

## 3. JFR: quan sát thay đổi theo thời gian

**JDK Flight Recorder (JFR)** ghi nhận các event của JVM và ứng dụng theo thời gian, thay vì chỉ chụp một heap snapshot. Với class loader leak, JFR hữu ích để trả lời:

- Bao nhiêu class/loader được tạo khi load hoặc redeploy plugin?
- Sau khi undeploy và GC, có dấu hiệu **class unload** hay không?
- Loader statistics và Metaspace/GC thay đổi thế nào?
- Có object sống lâu được sampling bắt gặp không?
- Những allocation stack nào tương quan với thời điểm bùng nổ instances?

### 3.1. Ghi recording ngắn với jcmd

Giả sử một JVM HotSpot Java 17 đang chạy với PID đã xác định:

~~~bash
jcmd <PID> JFR.start name=loaderdiag settings=profile
~~~

Đây là **lệnh shell có placeholder `<PID>`**: thay bằng số PID thực tế, không gõ dấu `<`/`>` như ký tự literal trong shell. Để xem recording nào đang chạy:

~~~bash
jcmd <PID> JFR.check
~~~

Khi đã thực hiện load/undeploy hoặc tái hiện nghi ngờ, ghi ra file:

~~~bash
jcmd <PID> JFR.dump name=loaderdiag filename=loaderdiag.jfr
~~~

Nếu cần điều tra suspected memory leak và có ngân sách pause cho phép, bản `jcmd` Java 17 cung cấp tùy chọn (lưu ý **tên option theo command**):

~~~bash
jcmd <PID> JFR.dump name=loaderdiag filename=loader-roots.jfr path-to-gc-root=true
~~~

**Chú ý chính tả:** Tùy chọn của `JFR.dump` trên tài liệu JDK 17 là `path-to-gc-root` (số ít). Trong các cách khởi động recording như `-XX:StartFlightRecording`/cấu hình JFR lại có thể thấy `path-to-gc-roots` (số nhiều). Luôn chạy `jcmd <PID> help JFR.dump` trên đúng JVM để biết option hỗ trợ; không đoán theo một bài hướng dẫn khác phiên bản.

Thu thập đường tới GC roots có thể gây **pause/overhead đáng kể**, vì vậy chỉ bật khi đã nghi leak và đã cân nhắc tác động production. Một bản JFR thông thường có thể **không chứa đủ retention paths**; không coi `JFR.dump` mặc định là thay thế toàn diện cho HPROF.

Sau đó:

~~~bash
jfr summary loaderdiag.jfr
jfr print --events jdk.ClassLoaderStatistics,jdk.ClassLoad,jdk.ClassUnload loaderdiag.jfr
~~~

`jfr summary` liệt kê loại event thực sự có trong file. `jfr print --events ...` lọc event đã được ghi, **không khiến những event bị tắt từ trước xuất hiện trở lại**.

### 3.2. Bẫy quan trọng: profile không nhất thiết bật ClassLoad/ClassUnload

Trong cấu hình JFR của nhiều OpenJDK/HotSpot phiên bản 17, `settings=profile` bật thống kê như `jdk.ClassLoaderStatistics` nhưng có thể **không bật riêng** các event nhiều chi tiết `jdk.ClassLoad` và `jdk.ClassUnload`. Vì vậy:

**Không thấy `jdk.ClassUnload` trong file JFR có ít nhất hai khả năng rất khác nhau:** không có event được ghi **hoặc** loại event đó đang tắt. Không thể dựa riêng vào một recording chưa kiểm tra cấu hình để tuyên bố JVM không unload class.

Cần làm đúng:

1. Dùng `jfr summary` kiểm tra loại event/số event và metadata settings.
2. Kiểm tra tệp cấu hình `$JAVA_HOME/lib/jfr/default.jfc` hoặc `profile.jfc` của **đúng JDK** đang chạy.
3. Nếu cần theo dõi từng lần load/unload, tạo **custom `.jfc`** bật các event `jdk.ClassLoad`/`jdk.ClassUnload`, hoặc cấu hình event-setting được JVM hỗ trợ.
4. Thu recording **trước khi tái hiện**. Nếu JFR được start sau khi class đã load, sẽ không có sự kiện loading đã xảy ra trước đó chỉ bằng cách đợi.
5. Có thể bổ sung HotSpot unified logging khi cần sự kiện riêng:

~~~bash
java -Xlog:class+load=info,class+unload=info -cp . LoaderRetentionDemo
~~~

Logging nạp lớp tạo nhiều dòng phụ thuộc JDK, classpath và thời điểm; **không có stdout cố định** cho lệnh này. Hãy xem việc unload của **loader/class cụ thể**, không chỉ tổng số dòng log.

### 3.3. OldObjectSample và path to GC roots

JFR có event như `jdk.OldObjectSample` để cung cấp dấu hiệu về **object sống lâu** (dựa trên sampling và cấu hình). Khi recording được setup thích hợp, JDK Mission Control (JMC) có thể cho bạn xem allocation stack và đôi khi đường giữ tới GC roots.

- Sampling **không liệt kê tất cả object** hoặc đảm bảo bắt được đúng plugin loader đang leak.
- Nếu event không có sample phù hợp, điều đó **không chứng minh không có leak**.
- `path-to-gc-root=true` trong JFR dump cần được bật rõ khi muốn capture roots (theo khả năng JVM); việc thu thập này có chi phí.
- Truy vết đường tham chiếu **đầy đủ cho một object cụ thể** thường thuận tiện hơn với HPROF và MAT, trong khi JFR mạnh ở **dòng thời gian và correlation**.
- Khi phân tích JFR, so snapshot/event giữa các vòng `deploy → use → undeploy → GC`, thay vì chỉ nhìn một thời điểm.

### 3.4. JFR, GC logs và classloader stats bổ trợ nhau

| Công cụ | Quan sát tốt | Giới hạn |
| --- | --- | --- |
| **JFR** | Timeline: allocation/GC/class loader stats và các events được bật. | Có sampling; event có thể tắt, không phải full object graph. |
| **`-Xlog:class+unload=info`** | Thông báo class unload trên HotSpot khi nó xảy ra. | Chỉ nói có unload, không chỉ ra trực tiếp mọi reference path. |
| **`jcmd VM.classloader_stats`** | Loader/class/metaspace snapshot. | Không tự xác định GC root giữ loader. |
| **`GC.heap_dump` + MAT** | Đồ thị object và đường tới GC roots tại một thời điểm. | Có chi phí pause/disk; dump không chứa lịch sử allocation đầy đủ. |
| **JMC** | Giao diện phân tích JFR, event correlation, leak suspects/old samples tùy cấu hình. | Chỉ phân tích evidence đã được ghi. |

## 4. Heap dump: chụp đồ thị object ở một thời điểm

Heap dump HPROF thường cho phép thấy **các object trên Java heap, fields, class/loader references và GC roots** tại lúc dump, tùy JVM và công cụ xuất. Dùng HotSpot Java 17:

~~~bash
jcmd <PID> VM.classloader_stats
jcmd <PID> GC.class_histogram
jcmd <PID> GC.heap_dump loader-before.hprof
~~~

**Ý nghĩa:**

- `VM.classloader_stats`: in thống kê class loader và class metadata. Dùng để xác định số loader cùng loại tăng qua các vòng redeploy; tên loader hoặc con số không phải bằng chứng leak độc lập.
- `GC.class_histogram`: số instance/size theo class trong heap; giúp nhìn nhóm object đáng ngờ. Nó không trực tiếp trả lời “ai giữ loader”.
- `GC.heap_dump`: tạo file HPROF để phân tích bằng MAT. Theo JDK 17 HotSpot, lệnh này có **tác động cao** và thường yêu cầu **full GC** trừ khi bật `-all`.

**Không nên dùng `-all` khi đang cố chứng minh object sống sau GC**: tùy chọn này có thể đưa cả object **unreachable** vào snapshot và có thể không thực hiện full GC trước. Vì vậy, việc object xuất hiện trong HPROF `-all` **không tự chứng minh nó được giữ mạnh**.

Nếu có hai giai đoạn:

~~~text
before: sau khi plugin tạo/còn được host cache giữ
after:  sau khi undeploy, xóa cache, dừng tasks và có cơ hội GC
~~~

có thể thu:

~~~bash
jcmd <PID> GC.heap_dump loader-before.hprof
jcmd <PID> VM.classloader_stats
~~~

Sau cleanup, thu ở thời điểm khác:

~~~bash
jcmd <PID> GC.heap_dump loader-after.hprof
jcmd <PID> VM.classloader_stats
~~~

**Không có số đếm loader/class cố định** giữa hai file. Một loader có thể chưa bị thu gom dù không còn strong references bởi thời điểm GC, chính sách class unloading, active stack hoặc reference ngoài kịch bản. Kết luận phải căn cứ đường giữ và điều kiện lifecycle đã thực sự kết thúc.

### Cảnh báo vận hành và quyền riêng tư

- Heap dump có thể **rất lớn**, tạo pause đáng kể và làm tăng áp lực disk/memory lúc capture.
- Dump có thể chứa **passwords, tokens, customer data, sessions, secret keys, request payloads** còn ở heap. Coi HPROF như dữ liệu nhạy cảm: phân quyền file, encryption, retention hạn chế, xóa an toàn theo policy, không gửi lên hệ thống công cộng.
- `jcmd` thường cần quyền attach phù hợp tới JVM đích và công cụ JDK/OS tương thích; container và security policy có thể chặn attach.
- Không tạo heap dump hàng loạt trên production khi chưa có kế hoạch disk capacity và tác động latency.

## 5. MAT: lần theo Path to GC Roots đúng cách

**Eclipse Memory Analyzer (MAT)** là công cụ phổ biến để mở `.hprof` và tìm những đối tượng giữ lại bộ nhớ. Cách điều tra:

1. Mở `loader-before.hprof` trong MAT.
2. Vào **Histogram** hoặc **Class Loader Explorer** để tìm `java.net.URLClassLoader`, custom loader `PluginClassLoader` hoặc class plugin như `demo.plugin.Payload`.
3. Lọc instance của class plugin hoặc loader suspect, xem trường và retained/shallow sizes.
4. Trên loader hoặc instance, chọn **Path To GC Roots**, ưu tiên tùy chọn **exclude weak/soft/phantom references** khi muốn tìm **strong retention path**.
5. Đọc **chuỗi sở hữu** ngược tới GC root. Ví dụ `system-loaded HostRegistry.static CACHE → HashMap entry → plugin instance → class/loader`.
6. Dùng **Dominator Tree** để xem object nào là accumulation point. Dominator không nhất thiết là object ở đầu một đường GC-root ngắn nhất; cần diễn giải retained heap đúng định nghĩa.
7. So với file `loader-after.hprof` và trạng thái thực tế sau cleanup để xem holder còn tồn tại hay đã được loại bỏ.
8. Gắn phát hiện với code đăng ký/khởi tạo và **hook cleanup** chứ không chỉ báo “URLClassLoader còn sống”.

### Path to GC Roots không phải “đường gọi hàm”

`Path to GC Roots` là **đồ thị tham chiếu tới object**, không phải stack trace cho biết loader được tạo ở file/dòng nào. Heap dump **không tự cung cấp lịch sử allocation hoàn chỉnh**. Muốn biết “ai tạo object” cần JFR allocation stack/profiler hoặc logging/instrumentation từ lúc tạo.

Một đường tham chiếu rút gọn trong MAT có thể trông như:

~~~text
GC Root: class của LoaderRetentionDemo (system loader)
    |
    v
static HOST_CACHE
    |
    v
java.util.HashMap
    |
    v
HashMap$Node.value
    |
    v
demo.plugin.Payload instance
    |
    v
class demo.plugin.Payload
    |
    v
java.net.URLClassLoader
~~~

**Đây là sơ đồ giải thích**; giao diện MAT có thể sắp xếp các node hoặc biểu diễn Class/loader edges khác tùy phiên bản. Điểm quan trọng là **host static cache giữ plugin instance**, và plugin instance khiến defining loader chưa thể được thu gom.

### Những câu hỏi phải trả lời sau mỗi root path

- **Root thuộc về ai?** system class/static field, live thread, native/JNI reference hay object framework?
- **Quan hệ strong hay weak/soft?** Một weak reference không phải bằng chứng nó giữ referent sống mãi.
- **Path có hợp lý theo lifecycle?** Nếu plugin còn active, cache tồn tại có thể là đúng. Nếu plugin đã undeploy, cần giải thích vì sao holder không được dọn.
- **Có nhiều path khác không?** Xóa một cache nhưng còn TCCL/thread pool hoặc listener sẽ không giải phóng loader.
- **Có class/instance/reference từ phiên bản cũ nào không?** Chỉ so tên binary name không đủ; so **defining loader identity** và deployment marker.
- **Có JNI/global reference hoặc active frame không?** Những path ngoài Java field thông thường cũng có thể giữ classloader.

## 6. Ví dụ Java 17 tự chạy: host static cache giữ plugin class loader

Chương trình sau tạo `demo.plugin.Payload` bằng `JavaCompiler` của JDK, nạp class bằng **URLClassLoader mới**, tạo một plugin instance rồi đặt instance vào một **static Map của host class**. Nó cố ý giữ loader thông qua instance để bạn quan sát bằng JFR và MAT.

Để không phải dùng `System.gc()` làm điều kiện kiểm thử, stdout chỉ kiểm tra **những điều Java bảo đảm**: loader đã định nghĩa class, cache có một entry, weak reference vẫn có referent **khi cache còn giữ mạnh**, và cache trống sau `clear()`. Chương trình **không tuyên bố loader đã unload ngay sau clear**, vì quyết định thực tế thuộc GC/JVM.

### Tệp duy nhất: LoaderRetentionDemo.java

~~~java
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class LoaderRetentionDemo {
    private static final Map<String, Object> HOST_CACHE = new HashMap<>();

    private static Path compilePlugin() throws Exception {
        Path root = Files.createTempDirectory("loader-retention-");
        Path src = root.resolve("src/demo/plugin/Payload.java");
        Path classes = root.resolve("classes");

        Files.createDirectories(src.getParent());
        Files.createDirectories(classes);
        Files.writeString(src,
                "package demo.plugin; " +
                "public final class Payload { " +
                "public String id() { return \"plugin\"; } }");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Run with a JDK");
        }

        int status = compiler.run(null, null, null,
                "--release", "17",
                "-d", classes.toString(), src.toString());

        if (status != 0) {
            throw new IllegalStateException("Plugin compilation failed");
        }
        return classes;
    }

    private static WeakReference<ClassLoader> install(Path classes)
            throws Exception {
        try (URLClassLoader pluginLoader = new URLClassLoader(
                new URL[] { classes.toUri().toURL() },
                ClassLoader.getPlatformClassLoader())) {

            Class<?> type =
                    pluginLoader.loadClass("demo.plugin.Payload");
            Object instance =
                    type.getDeclaredConstructor().newInstance();

            HOST_CACHE.put("plugin", instance);

            System.out.println("Plugin class: " + type.getName());
            System.out.println("Defined by child: "
                    + (type.getClassLoader() == pluginLoader));

            return new WeakReference<>(pluginLoader);
        }
    }

    public static void main(String[] args) throws Exception {
        Path classes = compilePlugin();
        BufferedReader console = new BufferedReader(
                new InputStreamReader(System.in));

        System.out.println("PID: " + ProcessHandle.current().pid());
        System.out.println("Press ENTER to load plugin");
        console.readLine();

        WeakReference<ClassLoader> weak = install(classes);

        System.out.println("Cache entries before cleanup: "
                + HOST_CACHE.size());
        System.out.println("Loader reachable while cached: "
                + (weak.get() != null));

        System.out.println("Press ENTER to clear cache");
        console.readLine();

        HOST_CACHE.clear();
        System.out.println("Cache entries after cleanup: "
                + HOST_CACHE.size());

        System.out.println("Press ENTER to exit");
        console.readLine();
    }
}
~~~

**Biên dịch và chạy trên Java 17 trở lên:**

~~~bash
javac --release 17 LoaderRetentionDemo.java
java LoaderRetentionDemo
~~~

Sau mỗi prompt, nhấn **ENTER** một lần. `ProcessHandle.current().pid()` in PID thực của process, nên dòng đầu **thay đổi theo mỗi lần chạy**. Các dòng chức năng ổn định là:

~~~text
PID: <số PID thực tế>
Press ENTER to load plugin
Plugin class: demo.plugin.Payload
Defined by child: true
Cache entries before cleanup: 1
Loader reachable while cached: true
Press ENTER to clear cache
Cache entries after cleanup: 0
Press ENTER to exit
~~~

Đây là **output mẫu có đúng một dòng PID động**; không thể in cùng một con số PID ở mọi máy. Chương trình được kiểm tra bằng `javac --release 17` và chạy trên OpenJDK; `false` không xuất hiện ở dòng `Loader reachable while cached` vì map đang giữ **strong reference** tới instance plugin.

### Vì sao đây là ví dụ retention chứ không đơn thuần giữ biến local?

- `install(...)` tạo loader, nạp class, tạo instance, chèn instance vào **static `HOST_CACHE`** và trả về **`WeakReference`**. Khi method trả về, biến local `pluginLoader` và `type` không còn nằm trong stack frame ấy.
- Method `install` dùng `try-with-resources` nên đã gọi `URLClassLoader.close()`. Nhưng `close()` **không** giải phóng một defining loader còn được tham chiếu bởi những class/object sống.
- `HOST_CACHE` nằm trên **host/application class loader** và sống lâu hơn plugin. Object `Payload` được map giữ mạnh, từ đó giữ class/loader tương ứng.
- `WeakReference<ClassLoader>` được giữ trên stack của `main` chỉ dùng làm **quan sát viên yếu**. Nó **không tự giữ loader sống**. Giá trị `weak.get() != null` khi cache còn giữ object là `true` theo thiết kế.
- Sau `HOST_CACHE.clear()`, đường giữ từ static map bị loại. **Chưa có khẳng định loader sẽ lập tức biến mất**: còn phụ thuộc GC và những references khác.
- Tạm dừng bằng `BufferedReader.readLine()` giúp bạn đủ thời gian chụp JFR/heap dump ở cả hai giai đoạn. Trên PowerShell và Bash, dùng terminal tương tác để nhấn ENTER; không cần background process để nhập nhiều bước.

### Trình tự chạy và thu chứng cứ

**Terminal A — chạy demo.** Chương trình in PID và dừng ở `Press ENTER to load plugin`.

**Terminal B — bắt đầu recording trước khi load plugin:**

~~~bash
jcmd <PID> JFR.start name=loaderdiag settings=profile
~~~

Quay lại Terminal A, nhấn ENTER **lần thứ nhất**. Lúc này plugin class được load và `HOST_CACHE` có một entry. Không nhấn ENTER lần thứ hai ngay; giữ chương trình đứng ở `Press ENTER to clear cache`.

**Terminal B — snapshot trước cleanup:**

~~~bash
jcmd <PID> VM.classloader_stats
jcmd <PID> GC.heap_dump loader-before.hprof
jcmd <PID> JFR.dump name=loaderdiag filename=loader-before.jfr
~~~

Trong MAT, mở `loader-before.hprof`, tìm `demo.plugin.Payload` hoặc loader `java.net.URLClassLoader` của nó. Lần theo Path To GC Roots sẽ có một đường thể hiện static `HOST_CACHE` của `LoaderRetentionDemo` giữ một `Payload` instance. Nếu công cụ mô hình hóa loader/class edges khác đi, vẫn phải tìm được **host cache là holder mạnh**.

**Terminal A — nhấn ENTER lần thứ hai** để xóa map, rồi giữ chương trình ở `Press ENTER to exit`.

**Terminal B — snapshot sau cleanup:**

~~~bash
jcmd <PID> GC.heap_dump loader-after.hprof
jcmd <PID> VM.classloader_stats
jcmd <PID> JFR.dump name=loaderdiag filename=loader-after.jfr
~~~

So sánh HPROF, class loader statistics và JFR theo vòng đời thực tế. Nếu loader đã đủ điều kiện cho class unloading, một lần GC thích hợp có thể thu gom; nếu chưa thấy unload ngay, không dùng kết quả duy nhất đó để kết luận fix thất bại. Kiểm tra lại các roots khác, GC policy và có active stack/plugin tasks không.

Cuối cùng nhấn ENTER lần thứ ba để demo thoát. Thư mục tạm chứa ClassFile sinh động có thể được dọn riêng; chương trình không tự xóa file nguồn/class tạo trong temp folder.

## 7. Hướng dẫn thực hành MAT với chính ví dụ trên

Khi mở `loader-before.hprof`:

1. **Histogram →** tìm `demo.plugin.Payload` và instance của nó.
2. Chọn instance đó, xem **Path To GC Roots** và đặt **exclude weak/soft/phantom references** (theo tùy chọn của MAT).
3. Xác nhận chain đại ý `LoaderRetentionDemo.HOST_CACHE → HashMap entry → Payload instance`. Trong MAT có thể có node bổ sung cho class/static field.
4. Chọn defining loader của `Payload` để xem các class được nó định nghĩa và instance counts. Chỉ có một `Payload` vì demo đơn giản, còn JVM có thể có nhiều loader khác.
5. Xem **Dominator Tree** để hiểu `HOST_CACHE`/holder nào chi phối object graph; lưu ý retained heap không nhất thiết đại diện toàn bộ Metaspace.
6. Mở `loader-after.hprof`. Tìm lại `Payload` và loader; nếu đã bị GC thu gom, chúng có thể không còn trong live snapshot. Nếu vẫn tồn tại, yêu cầu **Path To GC Roots mới** thay vì giả định map cũ vẫn giữ.
7. Đối chiếu kết quả với source: đường tham chiếu trực tiếp của `HOST_CACHE` bị xóa sau `clear()`. Bản dump trước cleanup chứng minh đường giữ ban đầu; bản sau giúp xem còn holder nào khác hoặc retention ngoài dự kiến.

### Nếu thấy loader còn trong heap sau clear?

Các giải thích khác nhau:

- **Không còn strong path** nhưng ảnh chụp gồm unreachable objects do tùy chọn dump hoặc cơ chế GC; kiểm tra cách capture.
- **JVM chưa unload** do GC timing/class unloading policy; không ép một deadline tự tạo.
- **Vẫn còn reference mạnh** từ thread/TCCL, JNI, listener, class/instance, cache khác hoặc instrumentation.
- **Bản dump/loader đối tượng khác**: có nhiều loader cùng loại/tên, nên phải đối chiếu identity, owner/class instance và deployment id.

Bằng chứng quyết định là **root path** và lifecycle. Một thống kê “metaspace chưa giảm” đơn lẻ có thể do allocator vẫn giữ committed memory hoặc do class khác vẫn loaded; không khẳng định leak chỉ dựa trên một biểu đồ.

## 8. JFR + heap dump: cách đọc theo timeline

Giả sử bạn chạy lại plugin 5 lần:

~~~text
T0: baseline sau startup, loader count ổn định
T1: deploy #1 → nạp 80 classes
T2: undeploy #1 → đóng tài nguyên, dừng tasks
T3: deploy #2 → tạo loader mới, nạp 80 classes
T4: undeploy #2 → ...
T5: sau vài lần GC phù hợp → đo live loaders/metaspace
~~~

Hai trường hợp:

**Tình huống kỳ vọng:** các loader cũ không còn strong path sau undeploy; class unloading có thể diễn ra sau GC, số live plugin loaders không tăng mãi cùng số vòng deploy.

**Tình huống nghi leak:** mỗi lần deploy tạo một loader mới; sau undeploy vẫn có loader cũ được giữ qua một cache/thread root, số live loaders/classes theo thế hệ tăng qua nhiều vòng và không có đường cleanup tương ứng.

Nhưng **JFR không tự cung cấp kết luận cuối**. Class loading metrics tăng có thể do lazy startup bình thường; class unload event có thể bị tắt. Vì thế:

- Dùng **JFR** để chỉ ra **khi nào** load/redeploy/GC xảy ra và thay đổi nào đáng ngờ.
- Dùng **`VM.classloader_stats`** để biết **những loader nào** vẫn được JVM nhìn thấy và số liệu về class.
- Dùng **heap dump/MAT** để tìm **ai giữ** loader cụ thể đó.
- Dùng code/lifecycle logs để quyết định holder còn hợp lệ hay là leak.
- Dùng **kịch bản lặp có điều kiện so sánh giống nhau** để đánh giá fix.

## 9. Những nhầm lẫn gây kết luận sai

### 9.1. `System.gc()` rồi WeakReference vẫn khác null → chắc chắn leak

Không đúng. `System.gc()` không đảm bảo GC/class unloading diễn ra theo deadline mong đợi; trong một số môi trường explicit GC có thể bị vô hiệu. Một weak ref còn referent chưa đủ kết luận leak; một weak ref `null` cũng không chứng minh toàn bộ tài nguyên/plugin đã cleanup đúng.

Trong ví dụ, thứ **chắc chắn** trước cleanup là `HOST_CACHE` đang giữ instance mạnh. Sau cleanup, kiểm chứng qua root path/live set trên dump và nhiều chu kỳ phù hợp, không in một dòng `unloaded=true` tùy hứng.

### 9.2. `URLClassLoader.close()` gọi thành công → loader unload

Sai. `close()` giải phóng những resource handles liên quan, **không xóa** Class/instance/method handles còn reachable. Mẫu demo đóng loader ở `install` nhưng static cache vẫn giữ class loader.

### 9.3. Heap dump chỉ có heap, nên không điều tra được class loader

Không đúng. HPROF và MAT có thể biểu diễn các object `ClassLoader`, `Class<?>`, static fields và các reference paths tương ứng; MAT còn mô hình hóa liên hệ class→loader. Tuy nhiên heap dump **không đo toàn bộ Metaspace/native memory như heap bytes**. Cần dùng JFR/stats/metaspace tools khi phân tích native side.

### 9.4. Retained heap của loader = toàn bộ Metaspace của loader

Sai. **Retained heap** là đại lượng trên object graph của Java heap; nhiều class metadata sống ở Metaspace native. Một loader instance có shallow size nhỏ vẫn có thể gắn với nhiều class metadata và references đáng kể.

### 9.5. `JFR.dump` không thấy ClassUnload = không có unload

Sai nếu event `jdk.ClassUnload` chưa bật, recording không bao phủ thời gian diễn ra unload hoặc JVM chưa thực hiện GC ở giai đoạn quan sát. Kiểm tra event configuration và timeframe trước khi suy luận.

### 9.6. Heap snapshot sau cleanup vẫn có Payload → cache chưa clear

Chưa chắc. Có thể object vẫn được giữ ở root khác, dump dùng `-all`, active frame còn tồn tại hoặc GC chưa thu gom. Đừng bỏ qua `Path To GC Roots`.

### 9.7. ThreadLocal có weak key nên tự hết leak

Sai. Worker thread sống lâu có thể giữ value mạnh dù key weak bị dọn sau này. Cleanup cần chạy đúng ngữ cảnh thread và gắn với vòng đời task/deployment.

### 9.8. Chỉ tăng Metaspace là fix

Không. Tăng `-XX:MaxMetaspaceSize` có thể làm lỗi xảy ra muộn hơn, nhưng không xóa root references. Nếu leak tăng theo mỗi redeploy, sau đủ vòng vẫn tích lũy.

## 10. Quy trình chẩn đoán production có kiểm soát

1. **Xác định triệu chứng có thể đo:** loader instances, class counts, Metaspace committed/used, rate redeploy, GC/unloading logs, hoặc OOME stack/cause.
2. **Xác nhận thời điểm và scope:** JVM nào, application/deployment nào, loader thuộc plugin nào, từ trước hay sau undeploy.
3. **Thu JFR recording** đủ sớm, chọn `default`/`profile` hay custom `.jfc` phù hợp; biết event nào đang bật.
4. **Dùng `VM.classloader_stats`** (và có thể `VM.metaspace` tùy HotSpot/JDK) để xem tổng quan theo loader.
5. **Chọn thời điểm heap dump an toàn:** có đủ disk, pause budget, privacy controls; cân nhắc ảnh hưởng full GC; ghi lại JVM build/flags.
6. **Trong MAT**, tìm loader/objects của deployment cũ, Path To GC Roots excluding weak refs và Dominator Tree.
7. **Xác định root holder**: static cache, TCCL, ThreadLocal, service registry, listener, JNI global, active frame, task queue.
8. **Sửa tại lifecycle boundary:** unregister, `ThreadLocal.remove()`, restore TCCL bằng `finally`, shutdown executor, cancel delayed tasks, xóa map/registries, đóng loader resources.
9. **Đo lại qua nhiều vòng deploy/undeploy**, so snapshot và JFR stats với baseline có cùng workload. Không đặt kỳ vọng “GC phải unload ngay sau gọi `clear`”.
10. **Báo cáo bằng bằng chứng:** đường GC-root trước fix, root path sau fix, classloader stats/timeline, điều kiện test, chi phí vận hành và giới hạn kết luận.

### Mẫu xử lý TCCL trong worker

Đây là **fragment** áp dụng khi task thực sự cần loader plugin (các biến `pluginLoader`/`task` đã được khai báo):

~~~java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();

thread.setContextClassLoader(pluginLoader);
try {
    task.run();
} finally {
    thread.setContextClassLoader(previous);
}
~~~

TCCL là **trạng thái của thread đang chạy**, không được executor tự truyền theo từng `submit`. Pattern trên tránh để worker của host giữ TCCL plugin sau task; nhưng nếu task còn ghi ThreadLocal hoặc đăng ký listener thì vẫn phải cleanup riêng.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** `URLClassLoader.close()` thành công có đồng nghĩa loader được GC ngay không?

**Đáp án:** Không. Nó đóng resource handles của loader; loader/class/instances có thể vẫn reachable từ root.

**Câu 2.** Một host static map giữ một plugin instance. Vì sao loader plugin có thể vẫn sống?

**Đáp án:** Instance có defining class và loader liên quan; map giữ instance mạnh qua GC root của host, nên lớp/loader không đủ điều kiện unload.

**Câu 3.** `JFR.dump` không có event `jdk.ClassUnload` chứng minh không có unload không?

**Đáp án:** Không. Event có thể chưa bật hoặc recording không bao phủ đúng thời điểm; kiểm tra `.jfc` và `jfr summary`.

**Câu 4.** Heap dump khác JFR ở đâu?

**Đáp án:** Heap dump là snapshot object graph/GC roots tại một thời điểm; JFR là event timeline/sampling. Kết hợp hai nguồn để biết cả “khi nào” và “ai giữ”.

**Câu 5.** MAT `Path To GC Roots` trả lời câu hỏi gì?

**Đáp án:** Chuỗi object/reference/root **đang giữ object sống**, không phải mặc định là stack trace nơi object được tạo.

**Câu 6.** Vì sao nên exclude weak/soft/phantom references khi tìm leak qua strong path?

**Đáp án:** Những đường tham chiếu yếu/mềm/phantom không có cùng ý nghĩa giữ sống như strong references; bỏ chúng giúp tập trung vào holder mạnh cần cleanup.

**Câu 7.** Vì sao `Metaspace used` tăng chưa đủ kết luận classloader leak?

**Đáp án:** Có thể do normal class loading, JIT/JVM allocator, workload hoặc thời điểm GC; cần evidence từ loader generations và root paths còn tồn tại sau lifecycle kết thúc.

**Câu 8.** Xóa cache rồi vẫn có một worker với TCCL=OldPluginLoader. Fix hoàn chỉnh chưa?

**Đáp án:** Chưa. TCCL là một strong reference từ live thread; cần restore/cleanup tại task boundary hoặc chấm dứt worker phù hợp.

## Ghi nhớ

**Class loader leak là vấn đề reachability và lifecycle, không phải chỉ là số byte heap hay một lệnh `close()`.** JFR cho timeline load/unload/GC, `VM.classloader_stats` cho snapshot loader/class, heap dump cho đồ thị object, còn MAT **Path To GC Roots** chỉ ra holder mạnh khiến loader/instance còn sống. Không coi absence of JFR event, one-time Metaspace growth hoặc một lần `System.gc()` là chứng cứ quyết định. Tìm **đường giữ**, gắn nó với nghiệp vụ đã kết thúc, xóa đúng tham chiếu và đo lại qua nhiều vòng deploy/undeploy.

**Tài liệu chính thức và công cụ tham khảo:**

- Oracle Java SE 17, `jcmd` (JFR.start/JFR.dump/GC.heap_dump/VM.classloader_stats): https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- Oracle Java SE 17, `jfr` command: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jfr.html
- Oracle Java SE 17, `ClassLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Oracle Java SE 17, `URLClassLoader.close()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- OpenJDK/Java learning, JFR configuration: https://dev.java/learn/jvm/monitoring-troubleshooting/jfr/configure/
- Eclipse Memory Analyzer, *Basic Tutorial* — Dominator Tree và Path to GC Roots: https://help.eclipse.org/latest/topic/org.eclipse.mat.ui.help/gettingstarted/basictutorial.html
- Eclipse MAT, *Path to GC Roots* và exclude weak references: https://help.eclipse.org/latest/topic/org.eclipse.mat.ui.help/reference/inspections/path_to_gc_roots.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-diagnostics-010">javap, jar và jdeps trong điều tra bytecode, JAR và phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# javap, jar và jdeps trong điều tra bytecode, JAR và phụ thuộc

## 1. Ba công cụ phục vụ ba câu hỏi khác nhau

Khi một ứng dụng Java gặp **ClassNotFoundException**, **NoClassDefFoundError**, **NoSuchMethodError**, **UnsupportedClassVersionError**, lỗi JPMS/module path hoặc nghi ngờ JAR chứa bản class không đúng, việc đọc source code thôi chưa đủ. JVM thực thi **class files đã biên dịch** và nạp chúng thông qua classpath, module path, ClassLoader và các quy tắc liên kết runtime.

Ba công cụ đi cùng JDK giúp thu thập bằng chứng từ artifact thật:

- **javap** — *Java class file disassembler*: “Class này đã được biên dịch thành **bytecode, descriptors, constant pool, flags và metadata** nào?”
- **jar** — *Java archive tool*: “**JAR có những entries nào**, manifest/module descriptor gì, có đúng class/resource/provider file cần thiết không?”
- **jdeps** — *Java class dependency analyzer*: “Bytecode của artifact **tham chiếu trực tiếp đến class/package/module nào** và những phụ thuộc nào đang thiếu hoặc thuộc JDK internal APIs?”

Ba công cụ này **không tương đương** và không tự cho biết chính xác runtime đã chọn defining loader nào. Muốn điều tra class-loading thật, phải ghép bằng chứng tĩnh với runtime logs, exception/cause, classpath/module path và **Class.getClassLoader()**.

### Bảng định hướng

| Triệu chứng / câu hỏi | Bắt đầu bằng | Vì sao |
| --- | --- | --- |
| “Class X có nằm trong JAR hay không?” | **jar --list** | Xem đúng đường dẫn entry của class/resource. |
| “JAR có module-info hay chỉ automatic module?” | **jar --describe-module** | Xem descriptor hoặc automatic module được suy ra. |
| “Method được compile có descriptor gì?” | **javap -s -p** | Thấy kiểu JVM, overload, return/parameters. |
| “Bytecode gọi invokevirtual hay invokeinterface?” | **javap -c -p** | Disassemble instructions và symbolic references. |
| “Class file dành cho Java 17 hay Java mới hơn?” | **javap -verbose** | Xem major/minor version. |
| “Ứng dụng thiếu một thư viện nào?” | **jdeps --missing-deps** | Tìm class references không giải quyết được khi thiếu dependency path. |
| “app.jar phụ thuộc vào JAR/module nào?” | **jdeps -summary** | Tổng hợp phụ thuộc tĩnh. |
| “API public đang phơi bày type từ thư viện nào?” | **jdeps -apionly** | Giới hạn vào signatures/API surface. |
| “Có gọi JDK internal API?” | **jdeps -jdkinternals** | Tìm tham chiếu tĩnh đến các JDK internals. |
| “Thứ tự ClassLoader nào thực sự được dùng?” | **Runtime evidence** | javap/jar/jdeps không thay thế quan sát JVM. |

## 2. Điều kiện tiên quyết và nguyên tắc chẩn đoán

Bài dùng **Java 17+**, minh họa kiểm chứng trên **OpenJDK 21**. Cả ba công cụ là **JDK tools**, không phải ứng dụng tự động có trong Java runtime tối giản.

Xác minh toolchain:

~~~bash
java -version
javac -version
javap -version
jar --version
jdeps --version
~~~

Output số phiên bản tùy máy. **Đừng giả định** công cụ JDK đang trỏ cùng Java runtime dùng để chạy production: PATH, JAVA_HOME, container images và build runners có thể chọn bản Java khác.

Khi điều tra lỗi deployment, cần ghi lại tối thiểu:

1. JDK version dùng **javac** để build.
2. JDK/JVM version chạy application.
3. Classpath/module path thật của tiến trình.
4. JAR thực tế được deploy, **không phải JAR trong source/build directory khác**.
5. Full exception message/cause, bao gồm tên class, method descriptor, module và loader nếu có.
6. Source/manifest/module-info/resource entries của chính artifact.
7. Mọi custom ClassLoader, plugin layer, TCCL và chiến lược parent/child delegation.

**Nguyên tắc:** kiểm tra một artifact cụ thể trước, sau đó điều tra đường lựa chọn artifact và loader lúc runtime. File có class hợp lệ **không có nghĩa JVM chắc chắn đã load class ấy**.

## 3. Tạo bộ ví dụ tái hiện để thực hành

Chúng ta sẽ xây **hai JAR riêng**:

- **greeting-lib.jar** chứa interface **lab.api.Greeting** và implementation **lab.impl.GreetingImpl**.
- **app.jar** chứa **lab.app.Main**, được compile chống lại thư viện nhưng **không đóng gói classes của library** vào app.jar.

Nhờ đó ta có thể kiểm tra một lỗi thật: app.jar có entry Main, nhưng nếu chạy mà quên greeting-lib.jar sẽ thiếu phụ thuộc runtime.

Cấu trúc source:

~~~text
project/
└── src/
    ├── lab/
    │   ├── api/Greeting.java
    │   ├── impl/GreetingImpl.java
    │   └── app/Main.java
    ├── classes-lib/           (tạo khi biên dịch)
    ├── classes-app/           (tạo khi biên dịch)
    ├── libs/greeting-lib.jar  (tạo khi đóng gói)
    └── app.jar                (tạo khi đóng gói)
~~~

Các tệp output ở cùng cấp với **src/**; phần cây trên nhóm theo ý nghĩa. Đứng ở thư mục project khi chạy lệnh.

### 3.1 Source của shared API

**src/lab/api/Greeting.java**:

~~~java
package lab.api;

public interface Greeting {
    String hello(String name);
}
~~~

### 3.2 Source của implementation

**src/lab/impl/GreetingImpl.java**:

~~~java
package lab.impl;

import lab.api.Greeting;

public final class GreetingImpl implements Greeting {
    public GreetingImpl() {
    }

    @Override
    public String hello(String name) {
        return "Hello, " + name;
    }
}
~~~

### 3.3 Source của application

**src/lab/app/Main.java**:

~~~java
package lab.app;

import lab.api.Greeting;
import lab.impl.GreetingImpl;

public final class Main {
    public static void main(String[] args) {
        Greeting greeting = new GreetingImpl();
        System.out.println(greeting.hello("Java"));
    }
}
~~~

### 3.4 Biên dịch và đóng gói

Từ thư mục project, tạo những thư mục output cần thiết:

~~~bash
mkdir -p classes-lib classes-app libs
javac --release 17 -g -d classes-lib src/lab/api/Greeting.java src/lab/impl/GreetingImpl.java
javac --release 17 -g -cp classes-lib -d classes-app src/lab/app/Main.java
jar --create --file libs/greeting-lib.jar -C classes-lib .
jar --create --file app.jar --main-class lab.app.Main -C classes-app .
~~~

Dấu **-g** yêu cầu tạo debugging information thích hợp, để **javap -l** có dữ liệu line/local variable tables. **--release 17** bảo đảm class-file target và tập API Java 17 tương thích, ngay cả khi đang dùng javac 21.

Trong **Windows PowerShell 5.x** dùng:

~~~powershell
New-Item -ItemType Directory -Force classes-lib, classes-app, libs | Out-Null
javac --release 17 -g -d classes-lib src/lab/api/Greeting.java src/lab/impl/GreetingImpl.java
javac --release 17 -g -cp classes-lib -d classes-app src/lab/app/Main.java
jar --create --file libs/greeting-lib.jar -C classes-lib .
jar --create --file app.jar --main-class lab.app.Main -C classes-app .
~~~

Nếu tạo các source files trên Windows, đảm bảo mã UTF-8 thích hợp với môi trường compiler; ví dụ này dùng literals ASCII nên không phụ thuộc encoding locale.

### 3.5 Chạy với đầy đủ dependency

Linux/macOS:

~~~bash
java -cp "app.jar:libs/greeting-lib.jar" lab.app.Main
~~~

Windows PowerShell:

~~~powershell
java -cp "app.jar;libs/greeting-lib.jar" lab.app.Main
~~~

**Stdout đã chạy thực tế trên OpenJDK 21:**

~~~text
Hello, Java
~~~

Nếu chỉ chạy:

~~~bash
java -jar app.jar
~~~

trong setup này, lệnh **không đưa greeting-lib.jar vào classpath**. Trên OpenJDK 21, kết quả là:

~~~text
Error: Unable to initialize main class lab.app.Main
Caused by: java.lang.NoClassDefFoundError: lab/api/Greeting
~~~

**Vì sao?** JAR **app.jar** có **Main-Class**, nhưng manifest chưa có **Class-Path** trỏ đến thư viện và lệnh java -jar không lấy -cp bên ngoài tùy tiện làm đường nạp bổ sung. Bản app.jar không chứa lab/api/Greeting.class. Đây là fixture để dùng cả **jar** và **jdeps** tìm nguyên nhân.

## 4. Công cụ jar: kiểm tra đúng contents của archive

### 4.1 Liệt kê entries

Hai cách tương đương:

~~~bash
jar --list --file libs/greeting-lib.jar
jar tf libs/greeting-lib.jar
~~~

Với JAR thư viện của ví dụ, danh sách gồm:

~~~text
META-INF/
META-INF/MANIFEST.MF
lab/
lab/api/
lab/api/Greeting.class
lab/impl/
lab/impl/GreetingImpl.class
~~~

Với app.jar:

~~~bash
jar --list --file app.jar
~~~

Các entries gồm:

~~~text
META-INF/
META-INF/MANIFEST.MF
lab/
lab/app/
lab/app/Main.class
~~~

**Kết luận chẩn đoán:** **Greeting.class không nằm trong app.jar**. Nếu application launcher chỉ dùng app.jar mà không có library JAR, dependency đó có thể bị thiếu.

**Đường entry JAR dùng dấu /, không dùng dấu chấm:** Class **lab.api.Greeting** ứng với **lab/api/Greeting.class**. Tên **Greeting.class** ở sai đường package có thể khiến class loader không tìm đúng binary name.

### 4.2 Kiểm tra tên package và duplicate entries

Nếu nghi trùng version của một class:

~~~bash
jar --list --file libs/greeting-lib.jar
jar --list --file app.jar
~~~

So sánh **đầy đủ đường entry**, không chỉ filename cuối. JAR có thể chứa nhiều classes tên **Helper.class** ở package khác nhau mà không có lỗi.

**JAR A và JAR B cùng có lab/api/Greeting.class chưa chứng minh có LinkageError.** Runtime có thể chọn một bản dựa trên classpath order hoặc delegation, hoặc hai loaders có thể định nghĩa các bản riêng hợp lệ. Phải xem Class identity và liên kết type tại runtime.

### 4.3 Xem manifest

Manifest nằm ở **META-INF/MANIFEST.MF**. Để đọc bằng JDK tools, giải nén riêng vào thư mục trống rồi mở file:

~~~bash
mkdir -p inspect-app
cd inspect-app
jar --extract --file ../app.jar META-INF/MANIFEST.MF
cat META-INF/MANIFEST.MF
cd ..
~~~

Trên Windows PowerShell:

~~~powershell
New-Item -ItemType Directory -Force inspect-app | Out-Null
Set-Location inspect-app
jar --extract --file ../app.jar META-INF/MANIFEST.MF
Get-Content META-INF/MANIFEST.MF
Set-Location ..
~~~

Manifest trong fixture có **Main-Class: lab.app.Main** do **--main-class**, nhưng không có **Class-Path: libs/greeting-lib.jar**. Đừng tự suy rằng **--main-class** đóng gói dependency.

**Lưu ý an toàn:** Giải nén một JAR không tin cậy vào thư mục riêng có kiểm soát; tránh extract tất cả entries tùy tiện vào thư mục chứa project/sensitive files. Việc xem qua **jar --list** trước khi extract là bước kiểm tra thận trọng.

### 4.4 --verbose và --validate

~~~bash
jar --list --verbose --file app.jar
jar --validate --file app.jar
~~~

**--list --verbose** bổ sung kích thước, timestamp và metadata về entries; output có thể khác theo cách đóng gói. **--validate** kiểm tra tính hợp lệ archive theo các quy tắc công cụ, ví dụ các trường hợp multi-release hoặc module-related entries.

**Quan trọng:** **jar --validate** thành công **không chứng minh** application đã có toàn bộ dependencies, class-loader policy đúng, hoặc có thể chạy không lỗi. Trong fixture, app.jar hợp lệ về mặt archive nhưng **java -jar app.jar** vẫn lỗi thiếu Greeting.

### 4.5 --describe-module và automatic modules

~~~bash
jar --describe-module --file libs/greeting-lib.jar
~~~

Vì fixture không biên dịch **module-info.java**, công cụ nói đây **không có module descriptor** và suy ra automatic module name từ file JAR:

~~~text
No module descriptor found. Derived automatic module.

greeting.lib automatic
requires java.base mandated
contains lab.api
contains lab.impl
~~~

Đó là output đã quan sát với tên file **greeting-lib.jar** trên JDK 21. Một explicit modular JAR có **module-info.class** sẽ có descriptor thực tế, không chỉ tên automatic được suy ra.

**Đừng nhầm:**

- **Có JAR file** chưa có nghĩa đó là **explicit named module**.
- **Automatic-Module-Name** trong manifest có thể điều khiển tên automatic module, nhưng không tạo **module-info.class**.
- Khi đặt JAR trên classpath, classes thường thuộc **unnamed module**, không tự thành named module chỉ vì jar --describe-module suy ra tên automatic.
- Khi đặt JAR trên module path, module resolution và module graph quyết định module nào thực sự được dùng.

### 4.6 Những entries đặc biệt nên tìm

Khi chẩn đoán plugin/module/SPI:

| Entry | Ý nghĩa |
| --- | --- |
| **META-INF/MANIFEST.MF** | Main-Class, Class-Path, Implementation-Version, Multi-Release... |
| **module-info.class** ở root | Explicit module descriptor. |
| **META-INF/services/com.example.Service** | Provider-configuration file cho SPI classpath/automatic modules theo mô hình liên quan. |
| **META-INF/versions/17/...** | Class/resource version-specific trong multi-release JAR. |
| **package/name/Type.class** | Bytecode class ở package tương ứng. |
| **META-INF/*.SF / *.RSA / *.DSA** | Có thể liên quan JAR signing. |
| **application.properties, config/...** | Resources có thể cần cho runtime, jdeps không nhất thiết theo dõi. |

**Lưu ý Multi-Release:** Có entry ở **META-INF/versions/17** không tự bảo đảm JVM dùng nó; cần manifest **Multi-Release: true**, version runtime và quy tắc lookup phù hợp. Với một JAR chưa chắc chắn, hãy xem cả base entry và versioned entries.

## 5. Công cụ javap: nhìn thẳng class-file thay vì source

**javap** không chạy application và không tạo instances của class được phân tích. Nó đọc class files/JARs/modules và in signatures, bytecode, constant pool, flags hoặc debug metadata tùy option.

### 5.1 Lệnh nền tảng

~~~bash
javap -classpath libs/greeting-lib.jar lab.impl.GreetingImpl
javap -classpath libs/greeting-lib.jar -p lab.impl.GreetingImpl
javap -classpath libs/greeting-lib.jar -s lab.impl.GreetingImpl
javap -classpath libs/greeting-lib.jar -c lab.impl.GreetingImpl
javap -classpath libs/greeting-lib.jar -v lab.impl.GreetingImpl
~~~

Dùng **-classpath** (hoặc **-cp**) cho nơi chứa class files cần inspect. Đây là đường để **javap tìm class**, không chứng minh runtime application cũng chọn đường đó.

Bảng các options hữu ích:

| Option | Thông tin |
| --- | --- |
| **-p / -private** | Hiện cả private members, kể cả synthetic members tùy class. |
| **-c** | Disassemble bytecode instructions từng method. |
| **-s** | In JVM internal type descriptors. |
| **-l** | In line number table và local variable table nếu class file chứa. |
| **-v / -verbose** | Class-file version, constant pool, access flags, attributes, bootstrap methods... |
| **-sysinfo** | Path, size, timestamp, hash theo công cụ/JDK. |
| **--module moduleName** | Inspect class trong named module thích hợp. |
| **--module-path path** | Chỉ định module path cho named modules. |

### 5.2 Quan sát bytecode thực tế

~~~bash
javap -classpath libs/greeting-lib.jar -c -p -s lab.impl.GreetingImpl
~~~

Đoạn output liên quan method **hello** đã kiểm chứng trên OpenJDK 21 khi javac --release 17:

~~~text
public java.lang.String hello(java.lang.String);
  descriptor: (Ljava/lang/String;)Ljava/lang/String;
  Code:
     0: aload_1
     1: invokedynamic #7,  0
     6: areturn
~~~

Constant pool numbering như **#7** có thể đổi nếu source/compiler/options khác. Nhưng đối với **đúng fixture và toolchain** đã dùng, đây là cách method string concatenation được biên dịch qua **invokedynamic**.

**Giải mã:**

- **aload_1**: đưa local variable #1 (tham số name) lên operand stack.
- **invokedynamic**: gọi site động do JVM liên kết với bootstrap method thích hợp, ở đây phục vụ string concatenation.
- **areturn**: trả về reference, trong trường hợp này là String.

Không đọc output -c như Java source; nó là **JVM bytecode disassembly**. Java compiler có thể tối ưu hoặc chọn bytecode khác với cách lập trình viên hình dung từ source.

### 5.3 JVM descriptors là chìa khóa để chẩn đoán NoSuchMethodError

Descriptor:

~~~text
(Ljava/lang/String;)Ljava/lang/String;
~~~

có nghĩa method nhận một **java.lang.String** và trả một **java.lang.String**.

Một số ví dụ:

| Java signature | JVM descriptor |
| --- | --- |
| void run() | **()V** |
| int sum(int a, int b) | **(II)I** |
| String hello(String x) | **(Ljava/lang/String;)Ljava/lang/String;** |
| void main(String[] args) | **([Ljava/lang/String;)V** |
| long[] values() | **()[J** |
| boolean test(Object o) | **(Ljava/lang/Object;)Z** |

Khi gặp:

~~~text
java.lang.NoSuchMethodError:
  'java.lang.String lib.Type.lookup(java.lang.String)'
~~~

hãy inspect **chính bản lib.Type.class được deploy** với **javap -s -p**. Có thể source hiện tại có method, nhưng runtime JAR cũ không có descriptor đó. Return type, parameters và method name là phần quan trọng của liên kết bytecode; overload/erasure/bridge methods có thể khiến hình thức source và descriptor thực khác.

**Không chữa bằng cách đổi tên class tùy tiện.** Kiểm tra compile-time vs runtime artifact và ClassLoader nào đã chọn.

### 5.4 invokevirtual, invokestatic, invokeinterface

Với app.jar:

~~~bash
javap -classpath app.jar -c -p -s lab.app.Main
~~~

Các bytecode quan trọng trong fixture:

~~~text
0: new           #7     // class lab/impl/GreetingImpl
3: dup
4: invokespecial #9     // constructor
7: astore_1
8: getstatic     #10    // Field java/lang/System.out
11: aload_1
12: ldc          #16     // String Java
14: invokeinterface #18,  2
19: invokevirtual #24    // PrintStream.println
22: return
~~~

Chỉ số constant pool cụ thể phụ thuộc build, nên đoạn này là output đại diện từ fixture đã chạy. Ý nghĩa:

- **new** tham chiếu class implementation **lab.impl.GreetingImpl**.
- **invokespecial** gọi constructor.
- **invokeinterface** gọi interface method **lab.api.Greeting.hello**.
- **invokevirtual** gọi method instance của PrintStream.

Nếu dependency bị thiếu hoặc class identity không phù hợp, lỗi có thể lộ ra khi JVM phải **resolve** những symbolic references này. javap cho biết bytecode đang **tham chiếu tên gì**, chứ không quyết định nguồn loader trả Class nào.

### 5.5 Đọc constant pool, class-file major version và attributes

~~~bash
javap -classpath libs/greeting-lib.jar -verbose lab.impl.GreetingImpl
~~~

Các dòng đã quan sát:

~~~text
major version: 61
flags: (0x0031) ACC_PUBLIC, ACC_FINAL, ACC_SUPER
this_class: #11     // lab/impl/GreetingImpl
...
Constant pool:
...
#7 = InvokeDynamic ...
~~~

**Major version 61 là class-file target Java 17.** Một số phiên bản thường gặp:

| Java target | Major version |
| --- | ---: |
| Java 8 | 52 |
| Java 11 | 55 |
| Java 17 | 61 |
| Java 21 | 65 |

Nếu runtime Java 17 nhận một class file có major version 65, có thể ném **UnsupportedClassVersionError**; xác nhận bằng javap -v trên artifact được deploy.

### 5.6 -l chỉ giúp khi có debug information

~~~bash
javap -classpath libs/greeting-lib.jar -l lab.impl.GreetingImpl
~~~

Vì đã compile **-g**, class file có thể chứa **LineNumberTable** và **LocalVariableTable**. Nếu bản production được build với **-g:none** hoặc bị shrink/obfuscate, những bảng này có thể thiếu.

**Sự thiếu debug tables không làm class file mặc nhiên sai.** Nó chỉ giới hạn khả năng map bytecode ↔ source line/local variable name.

### 5.7 Compile-time artifacts không nói mọi điều về runtime

Một classfile có interface **lab.api.Greeting** trong constant pool, nhưng điều đó **không chứng minh** JVM đã dùng bản interface ở libs/greeting-lib.jar. Nếu classpath/module path có bản khác hoặc loader custom, có thể xảy ra type mismatch.

Dùng:

~~~java
System.out.println(lab.api.Greeting.class.getClassLoader());
System.out.println(lab.api.Greeting.class.getProtectionDomain()
        .getCodeSource());
~~~

Đây là snippet cần đặt trong method hợp lệ; **getCodeSource() có thể null**, nên production code phải null-check. Log runtime kết hợp javap mới giúp khẳng định nguồn bytecode đã được chọn.

## 6. jdeps: phân tích dependency graph từ bytecode

**jdeps** phân tích các **static class-file dependencies**, không chạy application. Nó đọc class files/JARs/directories và báo class/package/module dependencies theo options.

### 6.1 Chạy summary với đầy đủ library path

~~~bash
jdeps -summary -cp libs/greeting-lib.jar app.jar
~~~

**Output đã kiểm chứng trên OpenJDK 21:**

~~~text
app.jar -> libs/greeting-lib.jar
app.jar -> java.base
~~~

Điều này cho thấy bytecode trong app.jar tham chiếu lớp ở library JAR và JDK java.base.

**Cú pháp quan trọng:** Ở JDK 21, tùy chọn summary là **-summary** hoặc **-s**, **không phải --summary**. Sai thành --summary sẽ bị jdeps báo unknown option. Kiểm tra **jdeps --help** của JDK đang dùng để lấy chính tả options chính xác.

### 6.2 Bỏ library path để thấy dependency thiếu

~~~bash
jdeps -summary app.jar
~~~

Output trong fixture:

~~~text
app.jar -> java.base
app.jar -> not found
~~~

Nhìn sâu các class thiếu:

~~~bash
jdeps --missing-deps app.jar
~~~

Output đã kiểm chứng:

~~~text
app.jar -> not found
   lab.app.Main -> lab.api.Greeting         not found
   lab.app.Main -> lab.impl.GreetingImpl    not found
~~~

Định dạng khoảng trắng giữa các cột do tool render; tên classes và **not found** là thông tin cần thiết.

**Đọc đúng ý nghĩa:** jdeps không tìm thấy **các classes được bytecode tham chiếu trong đường phân tích hiện tại**. Nó không chứng minh file class không tồn tại ở bất kỳ nơi nào trên ổ đĩa. Có thể bạn chưa truyền **-cp**, dùng sai relative path, thiếu JAR transitive hoặc chưa cấu hình module path.

So sánh với runtime:

~~~text
java -jar app.jar
  -> NoClassDefFoundError: lab/api/Greeting

jdeps --missing-deps app.jar
  -> lab.api.Greeting not found
~~~

Hai quan sát độc lập cùng chỉ vào một vấn đề: application artifact **không đủ dependencies theo đường chạy/đường phân tích**.

### 6.3 Xem dependency ở mức class

~~~bash
jdeps -verbose:class -cp libs/greeting-lib.jar app.jar
~~~

Một phần output đã kiểm chứng:

~~~text
app.jar -> libs/greeting-lib.jar
app.jar -> java.base
   lab.app.Main -> java.io.PrintStream           java.base
   lab.app.Main -> java.lang.Object              java.base
   lab.app.Main -> java.lang.String              java.base
   lab.app.Main -> java.lang.System              java.base
   lab.app.Main -> lab.api.Greeting              greeting-lib.jar
   lab.app.Main -> lab.impl.GreetingImpl         greeting-lib.jar
~~~

Đây là cách xác định **class nào trong app.jar phụ thuộc vào class nào**. Nếu bạn có vài chục library JARs, -verbose:class giúp chỉ ra nguồn dependency trong bộ artifact đưa vào phân tích.

Một số tùy chọn liên quan:

| Option jdeps | Tác dụng |
| --- | --- |
| **-s / -summary** | Summary archive/module dependencies. |
| **-verbose:class** | Hiện dependencies ở mức class. |
| **-verbose:package** | Hiện dependencies ở mức package. |
| **-v / -verbose** | Nhiều class-level dependencies hơn theo hợp đồng, tương đương -verbose:class -filter:none. |
| **-cp / --class-path** | Chỉ định classpath để tìm classes referenced. |
| **--module-path** | Chỉ định path cho named modules. |
| **--missing-deps** | Tập trung các dependencies không tìm được. |
| **-apionly** | Chỉ phân tích dependencies hiện ra trong API public/protected thích hợp. |
| **-jdkinternals** | Tìm references tới JDK internal APIs. |
| **--multi-release 17** | Chọn view class files cho multi-release JAR theo target version. |
| **--list-deps** | Liệt kê module dependencies và các JDK internals có liên quan. |
| **--print-module-deps** | Module list dạng dấu phẩy, hữu ích làm input cho jlink. |
| **--dot-output dir** | Xuất Graphviz DOT files để vẽ graph. |

### 6.4 API-only không có nghĩa là tất cả runtime dependencies

~~~bash
jdeps -apionly -verbose:class -cp libs/greeting-lib.jar app.jar
~~~

Với fixture, Main chỉ có public **main(String[])** và constructor; nhiều phụ thuộc implementation nội bộ sẽ không xuất hiện trong API-only output. Nếu dùng kết quả này để tạo danh sách runtime dependencies đầy đủ, bạn có thể bỏ sót thư viện.

**-apionly** giúp hiểu dependencies lộ qua public/protected contracts, không thay thế phân tích toàn bộ bytecode.

### 6.5 --list-deps và --print-module-deps

~~~bash
jdeps --list-deps -cp libs/greeting-lib.jar app.jar
jdeps --print-module-deps -cp libs/greeting-lib.jar app.jar
~~~

Với fixture, các lệnh đã kiểm chứng trả về JDK module dependencies:

~~~text
java.base
~~~

**Đừng hiểu chỉ cần java.base để chạy ứng dụng.** Kết quả là **module names**, không phải danh sách toàn bộ third-party JAR classpath cần có. Demo vẫn cần greeting-lib.jar để chạy.

**--print-module-deps** có thể được dùng khi xây **jlink custom runtime image** cho một tập modules; ứng dụng classpath vẫn cần được đóng gói/triển khai cùng third-party libraries tương ứng.

## 7. jdeps có những điểm mù gì?

### 7.1 Reflection động

Ví dụ:

~~~java
Class<?> c = Class.forName(System.getProperty("plugin.class"));
~~~

Nếu tên class chỉ đến từ config lúc runtime, bytecode không có class symbolic reference cố định cho tên đó. jdeps **không thể suy ra chính xác mọi giá trị khả dĩ từ config/user input**. Vì vậy jdeps có thể không báo một plugin JAR mà runtime thật cần.

### 7.2 ServiceLoader và META-INF/services

~~~java
ServiceLoader<MyService> loader =
        ServiceLoader.load(MyService.class);
~~~

Đây là snippet giả định MyService và imports phù hợp. ServiceLoader có thể tìm providers qua **META-INF/services** hoặc **provides** trong named modules. jdeps không thay thế việc kiểm tra **provider-configuration file** và module descriptors, đặc biệt với plugins được đóng gói bên ngoài app.jar.

Một JAR provider không nằm trong static references từ consumer chưa chắc xuất hiện trong jdeps dependency list, nhưng vẫn cần khi chạy ServiceLoader.

### 7.3 ClassLoader custom và downloaded/generated classes

~~~java
byte[] bytes = downloadClassBytes();
Class<?> c = loader.defineOrLoad(...);
~~~

Đây là mã ý tưởng, không biên dịch độc lập. Class từ database, network, runtime bytecode generation hoặc encrypted JAR có thể không nằm trong input artifact lúc jdeps chạy.

**jdeps không execute custom ClassLoader**. Kết quả phụ thuộc files bạn đưa vào phân tích, không phải toàn bộ class graph sẽ phát sinh ở runtime.

### 7.4 JNI, native libraries và external config

Một class có **native method** hoặc load thư viện bằng **System.loadLibrary** có thể cần **.so / .dll / .dylib** hoặc environment variables. jdeps tập trung vào class dependencies, không chứng minh các native binaries tương thích hoặc đã có.

Tương tự, resource paths, config files, SQL/schema, network services và reflective scanning conventions không nhất thiết được jdeps liệt kê.

### 7.5 Dynamic proxies và invokedynamic

Static analysis có thể nhìn thấy những symbolic references trong class file, bootstrap methods và API classes, nhưng không thể đảm bảo mọi runtime generated proxy/hidden class/call-site target đã được liệt kê đầy đủ.

**Kết luận:** jdeps là một lớp chứng cứ mạnh cho **static references**, không phải mô phỏng chính xác toàn bộ runtime.

## 8. Điều tra Java modules và JPMS

### 8.1 Phân biệt classpath với module path

Trong Java 9+, cùng một JAR có thể có nhiều ngữ nghĩa khi được đặt ở vị trí khác nhau:

- **Classpath**: classes thuộc unnamed module của loader tương ứng.
- **Module path với explicit module-info.class**: named module với descriptor thật.
- **Module path với JAR không có module-info.class**: có thể thành **automatic module**.

**jar --describe-module** giúp xem artifact là modular/automatic; **jdeps --module-path** giúp phân tích module graph, còn **javap module-info.class** giúp xem bytecode descriptor.

### 8.2 Xem module descriptor

Với explicit modular JAR:

~~~bash
jar --describe-module --file some-module.jar
javap -p -v -classpath some-module.jar module-info
~~~

Lệnh thứ hai có thể phụ thuộc cách javap lookup class/module info; một lựa chọn đơn giản với JAR là **javap -v some-module.jar** không phải cú pháp ổn định cho descriptor. Ưu tiên **jar --describe-module** hoặc **javap --module-path path --module module.name module-info** theo môi trường cụ thể.

Đối với JAR fixture **greeting-lib.jar**, **không có module-info.class**, vì vậy không tự bịa một module descriptor với requires/exports do lập trình viên viết.

### 8.3 jdeps trên modules

Khi có hai explicit modules được biên dịch/đóng gói:

~~~bash
jdeps --module-path mods --check my.app
jdeps --module-path mods --module my.app
jdeps --module-path mods --print-module-deps --module my.app
~~~

Đây là **lệnh mẫu có placeholder tên module my.app và đường mods**, không áp dụng trực tiếp cho fixture classpath phía trên. Thay bằng tên module thật; nếu module path không có my.app, công cụ sẽ báo lỗi.

**--check** phân tích dependences và graph của named module, có thể giúp phát hiện requires chưa cần thiết, transitive relationships và graph sau reduction. **--module** chỉ định root module dùng để phân tích. Không dùng **--check** với một tên JAR được suy ra tùy tiện nếu nó chưa ở module path đúng.

### 8.4 jdeps --generate-module-info: gợi ý, không phải kết quả deploy cuối

Trong một số lộ trình migration từ classpath sang JPMS, có thể dùng:

~~~bash
jdeps --generate-module-info generated libs/greeting-lib.jar
~~~

Lệnh phân tích JAR để tạo **module-info.java** đề xuất trong thư mục output. Một số ràng buộc áp dụng: không ghép tùy tiện **--generate-module-info** với **--class-path** hay **--dot-output**; kiểm tra **jdeps --help** của JDK đang dùng.

**Không commit/gắn nhãn module-info do tool sinh ra mà không review.** Static analysis có thể bỏ sót reflective access, services, optional runtime deps, qualified exports hoặc opens cần thiết. Hãy review module name, requires/exports/opens/uses/provides và chạy integration tests.

### 8.5 Multi-release JAR: nhìn đúng bản class theo Java version

Nếu manifest có:

~~~text
Multi-Release: true
~~~

JAR có thể có:

~~~text
com/example/Feature.class
META-INF/versions/11/com/example/Feature.class
META-INF/versions/17/com/example/Feature.class
~~~

Trong Java 17, class được chọn có thể khác base; trong Java 21 có thể dùng bản version 17 hoặc cao nhất phù hợp nếu tồn tại.

Với jdeps:

~~~bash
jdeps --multi-release 17 -summary multi.jar
jdeps --multi-release base -summary multi.jar
~~~

**multi.jar là tên minh họa**, không phải artifact được tạo trong fixture. So sánh results giữa base và 17 khi có MR entries; dependencies khác nhau là dấu hiệu một runtime version có thể cần class/module khác.

Với javap, khi cần xem cụ thể **versioned class bytes**, hãy xác định đúng entry theo đường META-INF/versions/... hoặc dùng option **--multi-release** nếu được hỗ trợ bởi JDK/tool tương ứng. Luôn kiểm tra **javap --help** để không áp dụng nhầm option của jdeps.

## 9. Điều tra những lỗi class loading phổ biến

### 9.1 ClassNotFoundException: lớp không được tìm thấy theo tên

Tình huống:

~~~text
java.lang.ClassNotFoundException: lab.impl.GreetingImpl
~~~

Quy trình:

1. **jar --list** library JAR: có entry **lab/impl/GreetingImpl.class** không?
2. Kiểm tra classpath/runtime loader thực tế: JAR đó có được cung cấp không?
3. **javap -classpath ... lab.impl.GreetingImpl**: class file đọc được theo binary name không?
4. **jdeps --missing-deps app.jar**: có nhắc tới class cần thiết không?
5. Nếu custom loader, kiểm tra parent delegation, child-first package filter, TCCL và source locations.

Nếu JAR có class nhưng **runtime không dùng JAR đó**, không nên thêm ngẫu nhiên URL rồi kết luận đã sửa chính sách loader.

### 9.2 NoClassDefFoundError: JVM không định nghĩa được class cần thiết

Trong fixture:

~~~text
NoClassDefFoundError: lab/api/Greeting
~~~

**jdeps --missing-deps** và **jar --list** giúp tìm dependency thiếu. Nhưng NoClassDefFoundError còn có thể xuất hiện vì class đã **initialization failed** trước đó hoặc linkage issue khác. Hãy xem full cause chain, không mặc định nó luôn do thiếu file.

### 9.3 NoSuchMethodError

Tình huống build chống lại library version mới nhưng deploy version cũ:

~~~text
java.lang.NoSuchMethodError: ...
~~~

Dùng **javap -p -s** trên class được deploy và **javap -c -v** trên class gọi để kiểm tra name/descriptor kỳ vọng. Sau đó so phiên bản JAR và classpath order. jdeps có thể cho biết caller phụ thuộc library nào nhưng **không tự chứng minh method linkage tương thích**.

Một method generic có thể bị erasure thành descriptor khác với source. Bridge methods, static vs instance, access modifiers và return descriptors cũng cần kiểm tra. Đừng chỉ so tên method.

### 9.4 UnsupportedClassVersionError

Ví dụ compile thành class-file major 65 (Java 21), chạy trên Java 17 chỉ hỗ trợ đến major 61:

~~~bash
javap -verbose -classpath app.jar lab.app.Main
~~~

Đọc **major version** trên artifact thực sự deploy. Sửa bằng build **javac --release 17** nếu project hỗ trợ API Java 17, hoặc chạy JVM mới hơn phù hợp.

### 9.5 ClassCastException “X cannot be cast to X”

Nếu cùng binary name bị hai loaders định nghĩa:

~~~text
Class com.example.Dto (LoaderA)
Class com.example.Dto (LoaderB)
~~~

**javap** có thể cho hai artifacts bytecode, **jar --list** có thể xác nhận các JAR đều chứa com/example/Dto.class, **jdeps** có thể cho thấy shared API type xuất hiện trong signatures. Nhưng để khẳng định root cause, cần in runtime:

~~~java
System.out.println(a.getName().equals(b.getName()));
System.out.println(a == b);
System.out.println(a.getClassLoader());
System.out.println(b.getClassLoader());
~~~

Snippet giả định a, b là các **Class<?>** có sẵn. Nếu name giống nhưng a != b, đó là hai runtime types. Giải pháp thường là chia sẻ API/DTO ở một defining loader chung và cô lập implementation riêng.

### 9.6 LinkageError: loading constraint violation

Ở boundary giữa host/plugin, method descriptor chứa **api.Payload** có thể yêu cầu hai initiating loaders thống nhất Class cho tên đó. Nếu plugin tự define bản api.Payload khác với host, có thể phát sinh **LinkageError**.

**javap -s** cho biết descriptor nào đang chứa type nhạy cảm. **jar --list** cho biết shared DTO class có bị đóng gói trùng. **jdeps -verbose:class** cho biết caller references. Cuối cùng **runtime loaders/stack trace** xác nhận chính Class identities bị liên kết sai.

**Không có công cụ nào trong ba lệnh này tự sửa loader constraint.** Phải sửa kiến trúc shared API, delegation và class identity.

### 9.7 ServiceLoader không tìm thấy provider

Kiểm tra **META-INF/services/fully.qualified.Service** trên classpath JAR hoặc **module-info.class** cùng **provides ... with ...** của named module. **jar --list** cho thấy service config file có tồn tại. **javap** xác nhận provider class có method/constructor tương ứng; **jdeps** giúp phát hiện dependencies trực tiếp của provider.

Nếu provider được chọn bằng reflection/service configuration, đừng ngạc nhiên khi jdeps chỉ phân tích consumer không báo ra implementation JAR. Vòng đời module graph/TCCL cũng quan trọng.

### 9.8 JPMS lỗi module not found

Xác nhận module JAR có **module-info.class** hoặc automatic module name, đưa đúng **--module-path** và root modules. Dùng:

~~~bash
jar --describe-module --file suspicious.jar
jdeps --module-path mods --check my.app
~~~

Các lệnh này minh họa theo module artifacts thật. **Module path** khác classpath; chuyển file sang module path có thể thay tên module, readability/exports và service behavior.

## 10. Quy trình điều tra theo từng lớp bằng chứng

~~~text
[0] Ghi runtime JDK, full error/cause và launcher args
              |
              v
[1] jar --list: artifact có class/resource/config đúng path?
              |
              v
[2] jar --describe-module / manifest:
    module-info, Main-Class, Class-Path, Multi-Release?
              |
              v
[3] javap -p -s -c -v:
    bytecode, method descriptors, class version, references?
              |
              v
[4] jdeps -summary / --missing-deps / -verbose:class:
    dependency graph thiếu class/JAR/module nào?
              |
              v
[5] Runtime evidence:
    classpath/module path, ClassLoader, TCCL, Class.getModule,
    class-loading logs, jar/module resolver, plugin lifecycle
              |
              v
[6] Xác minh fix bằng deployment giống production
~~~

Mỗi lớp bằng chứng trả lời một câu hỏi khác nhau. Một lập trình viên nhìn thấy **lab/impl/GreetingImpl.class** qua jar --list vẫn chưa biết:

- class đó có **được chọn** trong runtime không;
- nó có **compatible method descriptor** không;
- ClassLoader nào đã định nghĩa nó;
- dependency transitive hoặc JNI resource có thiếu không;
- JPMS access rules có cho phép caller sử dụng nó không.

Chỉ khi ghép nhiều bằng chứng mới kết luận root cause vững chắc.

## 11. Thực hành mở rộng: chẩn đoán “JAR đúng nhưng chạy sai”

Một lỗi điển hình là **đã sửa và build library nhưng production vẫn chạy class cũ**. Quy trình:

1. Dùng **jar --list --file artifact.jar** kiểm tra JAR triển khai thực sự chứa expected path.
2. Dùng **javap -verbose -classpath artifact.jar package.Class** so major version, method descriptors, constant pool và timestamp/hash nếu cần.
3. Dùng **jdeps -verbose:class ... app.jar** xem static reference tới library artifact mong đợi.
4. Xác định startup **-cp**, **--module-path**, **-jar** và manifest Class-Path chính xác. Đừng nhầm JVM command của local/test với production.
5. Gắn class-loading logging trên JVM đang gặp lỗi nếu phù hợp:
   
   ~~~bash
   java -Xlog:class+load=info -cp "app.jar:libs/greeting-lib.jar" lab.app.Main
   ~~~

6. Trong code chẩn đoán, in Class.getClassLoader() và ProtectionDomain CodeSource khi được phép.
7. Xem custom ClassLoader có **parent-first**, **child-first** hay **cache class đã loaded**; không thể đổi Class hiện tại chỉ bằng thay file JAR.
8. Nếu đã có hai Class identities khác nhau, xem shared API boundary và các method descriptors chịu loading constraints.
9. Xác nhận sửa lỗi trên **chính môi trường runtime/loader configuration** gặp sự cố, không chỉ trên unit test classpath.

**Ghi chú:** Option **-Xlog:class+load** được HotSpot hỗ trợ theo Unified Logging; format chi tiết khác theo JVM/JDK. Nó là nguồn dữ liệu runtime riêng, không phải chức năng của javap/jar/jdeps.

## 12. Các giới hạn khi dùng ba công cụ

### 12.1 javap không chứng minh class đã được JVM load

javap đọc artifact chỉ định. Class file hợp lệ không đủ chứng minh class loader đã chọn nó. Nó cũng không cho biết class đã initialized, instances tồn tại hay Class object đang giữ reference nào.

### 12.2 jar không “giải dependency” chỉ bằng list

jar --list xem entries; một JAR có thể hoàn toàn hợp lệ nhưng vẫn thiếu dependencies runtime. Main-Class không phải Class-Path; Class-Path trong manifest có rules path resolution riêng.

### 12.3 jdeps không phải dynamic tracer

Không coi static graph đầy đủ cho mọi Class.forName, ServiceLoader, JNI, plugins động hoặc bytecode generation. Đặc biệt, **--ignore-missing-deps** chỉ đổi cách tool xử lý/bỏ qua dependencies thiếu; **không tạo những classes thiếu** hay bảo đảm chương trình chạy.

### 12.4 Phụ thuộc không đồng nghĩa compatibility

jdeps có thể thấy class A phụ thuộc class B nhưng không biết B runtime có **method descriptor** mong đợi trong phiên bản triển khai. javap và linkage tests bổ sung góc nhìn này.

### 12.5 Version của JDK tools cũng tác động kết quả

JDK tool options, class-file versions, JPMS module graph và cách disassembler trình bày có thể thay đổi. Nếu chạy javap rất cũ trên class-file của JDK mới, có thể không hiểu định dạng mới. Ưu tiên toolchain phù hợp với Java runtime/artifact đang chẩn đoán và luôn kiểm tra **--help**.

### 12.6 Không dùng logging/inspection như sandbox

Một JAR đến từ nguồn không tin cậy có thể có zip entries không an toàn, malware hoặc bytecode độc hại. **jar --list/javap/jdeps không thực thi application main**, nhưng giải nén và thử chạy một archive không tin cậy vẫn cần môi trường cô lập, tránh ghi đè file và tuân thủ quy trình bảo mật.

## 13. Câu lệnh cheat sheet

### Bytecode và signatures

~~~bash
javap -classpath libs/greeting-lib.jar lab.impl.GreetingImpl
javap -classpath libs/greeting-lib.jar -p -s lab.impl.GreetingImpl
javap -classpath libs/greeting-lib.jar -c lab.impl.GreetingImpl
javap -classpath libs/greeting-lib.jar -verbose lab.impl.GreetingImpl
javap -classpath libs/greeting-lib.jar -l lab.impl.GreetingImpl
~~~

### JAR và manifest

~~~bash
jar --list --file libs/greeting-lib.jar
jar --list --verbose --file app.jar
jar --describe-module --file app.jar
jar --validate --file app.jar
~~~

### Static dependencies

~~~bash
jdeps -summary app.jar
jdeps --missing-deps app.jar
jdeps -summary -cp libs/greeting-lib.jar app.jar
jdeps -verbose:class -cp libs/greeting-lib.jar app.jar
jdeps -apionly -verbose:class -cp libs/greeting-lib.jar app.jar
jdeps --list-deps -cp libs/greeting-lib.jar app.jar
jdeps --print-module-deps -cp libs/greeting-lib.jar app.jar
~~~

Các lệnh trên đã được chạy hoặc đối chiếu với tool help trên OpenJDK 21; kết quả phụ thuộc files và JDK cụ thể.

**Windows:** Dùng dấu **;** khi một classpath chứa nhiều entries, ví dụ **"app.jar;libs/greeting-lib.jar"**. Các lệnh có đúng một entry **-cp libs/greeting-lib.jar** không cần đổi dấu.

## 14. Kiến thức cần tránh nhầm

**“jar --list có class nên JVM chắc chắn load được.”** Sai: còn classpath/module path, loader policy, access và dependencies.

**“jar --validate PASS thì java -jar không thể lỗi.”** Sai: app.jar trong fixture hợp lệ nhưng thiếu greeting-lib.jar.

**“javap -c cho Java source chính xác.”** Sai: nó disassemble JVM instructions, không khôi phục source-level semantics đầy đủ.

**“javap thấy method source code có thì runtime Class chắc chắn có method.”** Sai: có thể javap đang đọc artifact khác runtime chọn.

**“jdeps --missing-deps không có output nghĩa là không có dependency động nào.”** Sai: reflection/services/native/generated classes có thể vượt phạm vi static analysis.

**“jdeps --print-module-deps in java.base nên có thể bỏ thư viện third-party.”** Sai: nó trả module names, không phải tất cả library JARs.

**“Mọi JAR trên module path đều là explicit named module.”** Sai: JAR không có module-info.class có thể là automatic module.

**“Hai JAR có một class cùng tên chắc chắn gây LinkageError.”** Sai: chỉ có class name duplication không đủ; xét defining loader và loading constraints.

**“Cùng getName() là cùng runtime Class.”** Sai: runtime identity còn defining ClassLoader.

**“javap/jdeps tự kiểm tra code coverage hoặc latency.”** Sai: đó là static inspection/analysis; dùng profiler/JFR/coverage agent nếu cần hành vi runtime.

## 15. Bài tập tự kiểm tra

**Câu 1.** Sau khi chạy jar --list app.jar, bạn thấy Main.class nhưng không thấy Greeting.class. Có đủ kết luận app.jar bị hỏng không?

**Đáp án:** Chưa. Nó có thể được thiết kế để dùng dependency JAR riêng. Phải xem runtime classpath/manifest và jdeps.

**Câu 2.** javap -verbose cho major version 61. Nó tương ứng Java release nào?

**Đáp án:** Java 17 class-file version.

**Câu 3.** Method Java **String hello(String)** có descriptor nào?

**Đáp án:** **(Ljava/lang/String;)Ljava/lang/String;**.

**Câu 4.** Tại sao javap -c thấy invokeinterface khi Main gọi Greeting.hello?

**Đáp án:** Static type của biến trong fixture là interface Greeting, method reference được compile thành invokeinterface.

**Câu 5.** Tại sao jdeps -summary app.jar không có library path có dòng “not found”?

**Đáp án:** Bytecode references tới lab.api.Greeting và lab.impl.GreetingImpl không được tool resolve từ input/classpath hiện có.

**Câu 6.** Thêm **-cp libs/greeting-lib.jar** vào jdeps có đồng nghĩa JVM java -jar app.jar sẽ tự có thư viện không?

**Đáp án:** Không. Đó là cấu hình phân tích của jdeps, không thay manifest hoặc runtime classpath của JVM.

**Câu 7.** Vì sao -apionly không nên được dùng như danh sách runtime dependency đầy đủ?

**Đáp án:** Nó tập trung vào public/protected API signatures, bỏ qua nhiều dependencies bên trong implementation.

**Câu 8.** ServiceLoader tìm provider qua META-INF/services. jdeps chỉ phân tích consumer có thể không hiện provider JAR vì sao?

**Đáp án:** Dependency discovery qua resource/service metadata có thể không được phản ánh như symbolic class reference trong consumer bytecode.

**Câu 9.** JAR không có module-info.class trên module path có thể mang dạng module nào?

**Đáp án:** Automatic module theo module system; tên có thể được suy ra hoặc dùng Automatic-Module-Name.

**Câu 10.** Hai ClassLoader khác nhau đều define com.example.Dto; điều gì cần xác minh để biết có vấn đề?

**Đáp án:** Defining loader, Class identity, boundary API signatures và constraints; hai class trùng tên tự nó có thể hợp lệ.

**Câu 11.** “java.lang.NoSuchMethodError” thường dùng công cụ nào để so method descriptors giữa hai versions?

**Đáp án:** **javap -s -p** trên các artifact chính xác; kết hợp javap -c -v ở caller.

**Câu 12.** Tại sao java -jar app.jar trong fixture không chạy dù app.jar có Main-Class?

**Đáp án:** Manifest/launcher không cung cấp greeting-lib.jar, thiếu lab.api.Greeting ở runtime.

## 16. Tóm tắt

~~~text
LỖI JAVA CLASS LOADING / LINKING
                |
                v
       jar: có đúng class/resource/JAR?
                |
                v
       javap: bytecode, version, descriptor đúng?
                |
                v
       jdeps: phụ thuộc tĩnh nào thiếu hoặc sai?
                |
                v
       runtime: loader, module, classpath, API identity?
                |
                v
       sửa artifact/deployment/loader policy
       rồi tái hiện lại dưới đúng JVM
~~~

**Nhớ ba câu:**

- **jar** xác minh **đúng những gì được đóng gói**.
- **javap** xác minh **đúng những gì đã được biên dịch**.
- **jdeps** xác minh **những gì bytecode tham chiếu tĩnh**.

Cả ba hỗ trợ chẩn đoán, nhưng **không thay thế** bằng chứng tại runtime về actual defining loader, parent delegation, module accessibility, runtime-generated classes và lifecycle.

### Nguồn tham khảo chính thức

- Oracle Java SE 21 **javap command**: https://docs.oracle.com/en/java/javase/21/docs/specs/man/javap.html
- Oracle Java SE 21 **jar command**: https://docs.oracle.com/en/java/javase/21/docs/specs/man/jar.html
- Oracle Java SE 21 **jdeps command**: https://docs.oracle.com/en/java/javase/21/docs/specs/man/jdeps.html
- Oracle Java SE 21 **ClassLoader API**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html
- Oracle Java SE 21 **JAR File Specification**: https://docs.oracle.com/en/java/javase/21/docs/specs/jar/jar.html
- Oracle JVMS **§4 Class File Format**: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html
- Oracle JVMS **§5 Loading, Linking, Initializing**: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-diagnostics-011">Đối chiếu runtime classpath thực tế với cây dependency của công cụ build</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu runtime classpath thực tế với cây dependency của công cụ build

## 1. Vấn đề cần giải quyết

Một dự án Java biên dịch thành công với Maven hoặc Gradle **không bảo đảm JVM lúc chạy sử dụng đúng tập JAR/classes mà ta nhìn thấy trong cây dependency của công cụ build**. Khi ứng dụng được khởi động qua IDE, test runner, application server, executable JAR, Docker image, plugin host hoặc custom ClassLoader, một lớp có thể được định nghĩa bởi **loader khác**, từ **bản thư viện khác**, hoặc thậm chí từ **bytes được tạo/biến đổi động**.

Tình huống quen thuộc:

- `mvn dependency:tree` hoặc `gradle dependencies` hiển thị thư viện v2, nhưng stack trace xuất hiện `NoSuchMethodError` tương ứng với runtime v1.
- Build thông qua, test chạy ổn, nhưng khi deploy lên Tomcat mới báo `ClassCastException` hoặc `NoClassDefFoundError`.
- `java.class.path` trông đúng nhưng một dependency lại được nạp từ thư mục `WEB-INF/lib`, JAR lồng trong executable artifact, hoặc plugin ClassLoader riêng.
- Xóa một dependency trong `pom.xml`/`build.gradle` mà runtime vẫn dùng class đó vì nó được cung cấp từ application server hoặc lớp agent.
- Một số class xuất hiện nhiều lần trên classpath; phiên bản thực sự được dùng khác bản bạn nghĩ.

**Mục tiêu:** xác định **classpath/module path ở build**, **dependency graph đã được resolve**, **đường chạy thực tế của JVM**, **ClassLoader định nghĩa class**, và **artifact bytes thực sự**. Không suy luận một trong các dữ kiện đó chỉ từ một dữ kiện khác.

Bài này dành cho Java 17+, dùng Maven và Gradle làm ví dụ; phần lớn kỹ thuật nhận diện ClassLoader áp dụng được cho Java nói chung.

## 2. Phân biệt năm góc nhìn của cùng một dependency

| Góc nhìn | Câu hỏi | Công cụ |
|---|---|---|
| **Declared dependencies** | Dự án khai báo những gì? | `pom.xml`, `build.gradle(.kts)`, version catalog |
| **Resolved dependency graph** | Build tool đã chọn versions/modules và loại bỏ xung đột như thế nào? | `dependency:tree`, Gradle `dependencies`/`dependencyInsight` |
| **Resolved classpath** | Các files output/JARs nằm trong compile/runtime/test classpath theo configuration nào? | Maven `dependency:build-classpath`; Gradle `runtimeClasspath`/`testRuntimeClasspath` |
| **Launch context** | Process thực sự được khởi động với arguments, working directory, module path, manifest, launcher, container hay agent nào? | command line, build launch task, runtime properties, process diagnostics |
| **Runtime class source/defining loader** | Class cụ thể được JVM định nghĩa bởi loader nào, từ nguồn nào? | `Class.getClassLoader()`, `getProtectionDomain()`/`CodeSource`, `-Xlog:class+load` |

Từng tầng trả lời một phần khác nhau. **Cây dependency không phải “bản kê khai chính xác mọi class đã load vào JVM”**. Nó thường không thể hiện toàn bộ:
- application/test output directories;
- JAR/class được server cung cấp ngoài build project;
- JAR bổ sung lúc launch bằng `-cp`, `--module-path`, manifest hoặc custom launcher;
- các dependency do Java agents append;
- nested JARs do Spring Boot hoặc launcher riêng xử lý;
- class được tạo qua bytecode generation;
- code được nạp bởi plugin loader hoặc container loader;
- khác biệt test runtime, production runtime và JPMS module layers.

## 3. Classpath không phải ClassLoader graph

### 3.1. Classpath là tập nguồn cho một hoặc nhiều loaders

Lệnh cơ bản:

```bash
java -cp "app/classes:libs/a.jar:libs/b.jar" com.example.Main
```

Trên Linux/macOS, separator giữa các entries classpath thường là dấu `:`. Trên Windows, dùng `;`:

```powershell
java -cp "app\classes;libs\a.jar;libs\b.jar" com.example.Main
```

Với **launcher và application class loader mặc định**, thứ tự entries có thể ảnh hưởng class cùng binary name nào được tìm trước. Tuy nhiên:

- Parent delegation có thể trả class đã định nghĩa ở parent.
- Loader đã nạp một tên có thể trả lại class cũ qua `findLoadedClass`.
- Một custom loader có thể dùng child-first, loader-specific URLs, network source hoặc generated bytecode.
- Named modules dùng module resolution và mapping module-to-loader, không chỉ danh sách file `-cp`.
- Một process có thể có nhiều ClassLoaders độc lập với nguồn và quy tắc riêng.

Vì vậy **nói “nó nằm trên classpath” chưa đủ** để giải thích class thực sự được định nghĩa từ đâu.

### 3.2. Định danh class ở runtime

Với các classes thông thường, danh tính của runtime class bao gồm **binary name và defining ClassLoader**. Hai class cùng tên từ hai loaders độc lập có thể là hai runtime types khác nhau:

```text
Loader-A ──define──▶ com.example.Widget [type A]
Loader-B ──define──▶ com.example.Widget [type B]

"type A" != "type B"
```

Ngược lại, hai loader con có thể cùng delegate đến parent, do đó nhận **cùng đối tượng `Class<?>`**, dù cả hai gọi `loadClass("com.example.Widget")`.

Khi debug, hãy quan sát:
- `type.getName()`;
- `type.getClassLoader()` (defining loader);
- `type.getModule()` và layer nếu dùng JPMS;
- `type.getProtectionDomain().getCodeSource()` nếu khả dụng.

**Không chỉ in `System.getProperty("java.class.path")` rồi kết luận đã biết tất cả runtime sources.**

## 4. Compile classpath, runtime classpath và test classpath là ba tập khác nhau

### 4.1. Compile classpath

Chứa classes/libraries đủ để compiler resolve symbols khi biên dịch. Nó có thể bao gồm **API cho compile** nhưng không tự đảm bảo các implementation JAR đó xuất hiện trong runtime artifact.

Ví dụ công cụ/build cấu hình một thư viện ở compile time theo scope/chính sách cụ thể, nhưng app server cung cấp thư viện ấy lúc chạy. Build pass, server version lệch ABI thì runtime báo lỗi.

### 4.2. Runtime classpath

Chứa code/implementation cần cho main application. Runtime thường cần thêm dependency không bắt buộc với compile (driver/database implementation, logging backend, service provider) và có thể không gồm một số artifact **compile-only**.

### 4.3. Test classpath

Thường có:
- main output (classes/resources);
- test output (classes/resources);
- main runtime dependencies;
- test framework/assertion/mocking libraries;
- fixtures/test-specific resources.

Một test có thể chạy được vì `testRuntimeClasspath` vô tình có thư viện mà production runtime không có. Ngược lại, application server có một JAR mà test local không thấy.

**Nguyên tắc:** phải so sánh **đúng configuration, đúng source set, đúng launch task**. Không lấy cây dependency compile để suy thẳng runtime, hoặc lấy testRuntimeClasspath để khẳng định production distribution sẽ giống hệt.

## 5. Maven: đọc dependency:tree đúng ngữ cảnh

### 5.1. Lệnh cơ bản

Ở root Maven project hoặc module được chọn:

```bash
mvn dependency:tree
```

Kết quả dạng cây cho biết những dependencies đã được Maven resolve theo project và plugin execution. Ví dụ minh họa:

```text
com.acme:app:jar:1.0
+- com.acme:core:jar:1.0:compile
|  \- org.example:codec:jar:2.0:compile
\- com.acme:web:jar:1.0:compile
   \- org.example:codec:jar:1.5:compile (omitted for conflict)
```

Đây là **output minh họa**, không phải output chạy từ một project cụ thể. `omitted for conflict` và định dạng chi tiết phụ thuộc version của Maven Dependency Plugin; một số version/option chỉ hiện các nodes đã chọn mà không in đầy đủ các nhánh bị bỏ.

Maven thường áp dụng **nearest definition** cho version conflict (nếu có cùng độ sâu thì thứ tự khai báo ảnh hưởng). Kết quả còn chịu tác động bởi:
- `dependencyManagement` trong parent/BOM;
- declared versions của direct dependencies;
- exclusions;
- scopes và scope transitivity;
- optional dependencies;
- profiles đang active;
- plugin/build configuration.

**Không nhìn mỗi `pom.xml` để đoán bản transitively selected**; kiểm tra cây đã resolve với đúng Maven profiles/properties.

### 5.2. Đúng module và đúng profile

Trong multi-module Maven reactor:

```bash
mvn -pl :service-app dependency:tree
```

`:service-app` là ví dụ artifactId; thay bằng module thực. Để so sánh môi trường profile:

```bash
mvn -Pproduction -pl :service-app dependency:tree
```

Nếu CI dùng `-Pproduction` nhưng máy local không bật, cây dependency có thể khác. Tương tự, property-based profiles, OS/JDK activation và parent POM version cũng có thể làm resolution thay đổi.

**Một artifact xuất hiện trong tree của module A không đảm bảo xuất hiện trong runtime classpath của module B**.

### 5.3. Scope của Maven quan trọng thế nào?

| Maven scope | Ảnh hưởng điển hình |
|---|---|
| `compile` | Compile, runtime và test classpaths |
| `provided` | Compile/test; runtime do JDK/server/host cung cấp theo mô hình deploy |
| `runtime` | Runtime/test; compiler không cần API đó để compile app |
| `test` | Test compile/runtime; không thuộc main runtime |
| `system` | File local khai báo bằng system path; không nên dùng trong thiết kế hiện đại |
| `import` | Chỉ cho dependency `pom` trong `dependencyManagement`; nhập BOM quản lý versions, không phải runtime JAR |

Đây là khái quát scopes của Maven, không thay thế việc xem **effective POM** và kết quả thực tế. Ví dụ servlet API scope `provided` có thể vắng trong WAR `WEB-INF/lib` nhưng **hiện diện ở Tomcat** do container loader định nghĩa.

### 5.4. Xuất dependency runtime classpath

Maven Dependency Plugin có goal:

```bash
mvn dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile=runtime-cp.txt
```

Tùy version plugin, tham số đi kèm có thể được đặt từ Maven property của goal; cần kiểm tra plugin version đang dùng nếu kết quả khác dự kiến.

**Quan trọng:** output này liệt kê những **dependency artifact paths** được plugin xuất cho configuration đã chọn; nó **không nhất thiết chứa main output directory, manifest Class-Path, JAR được server cung cấp hoặc classloader URLs từ launcher**. Vì vậy muốn tái dựng lệnh chạy Java, còn cần cộng đúng `target/classes`, resources, packaging và cấu hình launch.

Để xem effective project model khi nghi parent/BOM/profile gây lệch:

```bash
mvn help:effective-pom
mvn help:active-profiles
```

Tham số chi tiết, goal availability và message có thể khác theo plugin version. Không tự sửa BOM/exclusion chỉ dựa trên một dòng `dependency:tree` khi chưa xác nhận runtime class nguồn nào thực sự gây lỗi.

## 6. Gradle: phân biệt runtimeClasspath với testRuntimeClasspath

### 6.1. Xem cây resolved runtime

Với Gradle Java plugin:

```bash
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencies --configuration testRuntimeClasspath
```

Trên Windows PowerShell:

```powershell
.\gradlew.bat dependencies --configuration runtimeClasspath
.\gradlew.bat dependencies --configuration testRuntimeClasspath
```

Với multi-project:

```bash
./gradlew :app:dependencies --configuration runtimeClasspath
```

`:app` chỉ là **tên project ví dụ**. Hãy thay bằng path project tương ứng trong build thật.

Gradle `dependencies` report hiển thị resolved dependency graph, gồm những version được chọn sau conflict resolution, constraints và variants. Ví dụ minh họa:

```text
runtimeClasspath - Runtime classpath of source set 'main'.
+--- org.example:codec:1.0 -> 2.1
\--- org.example:transport:3.0
     \--- org.example:codec:2.1
```

Ký hiệu `1.0 -> 2.1` nói request version 1.0 nhưng resolution chọn 2.1 trong configuration này; không có nghĩa ở runtime chắc chắn nạp bytes từ một file duy nhất với tên đó nếu launcher/container bổ sung nguồn khác.

### 6.2. Dùng dependencyInsight để tìm nguyên nhân version

```bash
./gradlew :app:dependencyInsight --dependency org.example:codec --configuration runtimeClasspath
```

Lệnh này giúp trả lời:
- Dependency được kéo vào bởi đường nào?
- Vì sao version X được chọn?
- Có constraint, conflict, force/platform/strict version hoặc variant rules không?
- Có nhiều request versions nhưng chỉ một resolved component version không?

Gradle conflict resolution **không hoàn toàn giống nearest-wins của Maven**. Gradle thường chọn một version phù hợp theo conflict resolution/constraints, mặc định có thể chọn version cao hơn trong những điều kiện phổ biến, nhưng `strictly`, `force`, dependency substitution, platforms/capabilities, variants và resolution strategy có thể thay đổi kết quả. **Không suy đoán** một bản dependency chỉ từ thứ tự khai báo trong `build.gradle`.

### 6.3. In ra classpath dùng bởi Java source set

Trong một project Gradle Kotlin DSL đã áp dụng Java plugin, có thể tạo task:

```kotlin
import org.gradle.api.tasks.SourceSetContainer

plugins {
    java
}

tasks.register("printMainRuntimeClasspath") {
    doLast {
        val sourceSets = extensions.getByType<SourceSetContainer>()
        val main = sourceSets.getByName("main")
        println(main.runtimeClasspath.asPath)
    }
}
```

Đây là **đoạn `build.gradle.kts`**, không phải Java source. Nếu project đã có `plugins { ... }`, chỉ tích hợp import/task cần thiết; không tạo thêm block `plugins` trùng lặp.

Chạy:

```bash
./gradlew printMainRuntimeClasspath
```

`main.runtimeClasspath` gồm **main output directories** cộng runtime dependencies theo Java plugin/source-set semantics; chỉ in riêng `configurations.runtimeClasspath.get().asPath` có thể bỏ sót output directories của chính project. Dù vậy, classpath in từ Gradle vẫn chưa chứng minh một **IDE launch, custom JavaExec task, executable JAR hoặc server** sử dụng đúng chuỗi đó.

### 6.4. Gradle run/test/application packaging

Khi debug cần ghi nhận **task thực sự dùng**:
- `test` thường sử dụng test runtime classpath và forked test worker JVMs.
- `JavaExec` task có `classpath` có thể được tùy chỉnh ngoài runtimeClasspath mặc định.
- Application plugin `run`/start scripts có classpath từ distribution do plugin/build quy định.
- Fat/shadow JAR có nội dung được ghép/relocate; không nhất thiết giữ nguyên file JAR theo resolved graph.
- IDE Gradle import có thể chạy bằng IDE classpath thay vì dùng đúng Gradle `run` task, tùy launch settings.

Vì vậy so sánh cả **Gradle resolved graph** và **task’s effective classpath/process command**.

## 7. Khác biệt compile vs runtime gây NoSuchMethodError như thế nào?

Giả sử code compile với thư viện `codec-v1`:

```java
public static String encode() {
    return "encoded";
}
```

Sau khi compile, runtime đặt một JAR khác chứa cùng class name nhưng **không còn method `encode()`** lên classpath. Java compiler đã tạo method reference tương ứng trong constant pool của caller. Khi JVM resolve lời gọi, target method không tồn tại trong class thực sự được load, nên ném:

```text
java.lang.NoSuchMethodError
```

Điểm cần nhớ:
- Hai class có **cùng binary name** chưa bảo đảm cùng **binary API**.
- JVM nạp class theo runtime loader/search path chứ không đọc cây dependency lúc build.
- `NoSuchMethodError` là `LinkageError` (qua `IncompatibleClassChangeError`).
- Nó không phải `ClassNotFoundException`; JVM có thể đã tìm được class nhưng API runtime không giống API lúc compile.
- Việc đổi library từ v1 thành v2 không làm **caller bytecode** tự được compile lại nếu chỉ copy JAR vào runtime.

## 8. Ví dụ Java 17+ đầy đủ: compile với v1, chạy với v2

Thí nghiệm này không cần Maven/Gradle, vì mục đích là **chứng minh bằng JVM thuần** rằng build-time classpath và runtime classpath khác nhau có thể tạo `NoSuchMethodError`.

### 8.1. Cấu trúc thư mục

Tạo:

```text
classpath-mismatch/
├── src/
│   ├── v1/demo/lib/Codec.java
│   ├── v2/demo/lib/Codec.java
│   └── app/demo/app/Main.java
└── out/
    ├── v1/
    ├── v2/
    └── app/
```

Hai files `Codec.java` khai báo **cùng fully qualified binary name** `demo.lib.Codec`, nhưng ta biên dịch chúng vào hai output directories độc lập.

### 8.2. Phiên bản v1: src/v1/demo/lib/Codec.java

```java
package demo.lib;

public final class Codec {
    private Codec() {
    }

    public static String origin() {
        return "lib-v1";
    }

    public static String encode() {
        return "encoded";
    }
}
```

### 8.3. Phiên bản v2: src/v2/demo/lib/Codec.java

```java
package demo.lib;

public final class Codec {
    private Codec() {
    }

    public static String origin() {
        return "lib-v2";
    }

    public static String newer() {
        return "new API";
    }
}
```

V2 còn `origin()` nhưng đã loại bỏ `encode()` — đây là **binary-incompatible change** đối với caller biên dịch với v1.

### 8.4. App: src/app/demo/app/Main.java

```java
package demo.app;

import demo.lib.Codec;

public final class Main {
    public static void main(String[] args) {
        System.out.println("runtime library: " + Codec.origin());

        try {
            System.out.println("encode: " + Codec.encode());
        } catch (NoSuchMethodError error) {
            System.out.println("caught: NoSuchMethodError");
        }
    }
}
```

### 8.5. Biên dịch tách biệt

Ở thư mục `classpath-mismatch`, dùng JDK 17+:

```bash
javac --release 17 -d out/v1 src/v1/demo/lib/Codec.java
javac --release 17 -d out/v2 src/v2/demo/lib/Codec.java
javac --release 17 -cp out/v1 -d out/app src/app/demo/app/Main.java
```

Điểm quyết định: **app được compile với `-cp out/v1`**, nơi `Codec.encode()` tồn tại. Chỉ tới runtime ta mới chọn một trong hai phiên bản.

### 8.6. Chạy đúng phiên bản v1

Trên Linux/macOS:

```bash
java -cp "out/app:out/v1" demo.app.Main
```

Trên Windows PowerShell:

```powershell
java -cp "out/app;out/v1" demo.app.Main
```

Stdout:

```text
runtime library: lib-v1
encode: encoded
```

### 8.7. Thay runtime bằng v2

Trên Linux/macOS:

```bash
java -cp "out/app:out/v2" demo.app.Main
```

Trên Windows PowerShell:

```powershell
java -cp "out/app;out/v2" demo.app.Main
```

Stdout:

```text
runtime library: lib-v2
caught: NoSuchMethodError
```

**Giải thích chính xác:**
1. Caller `demo.app.Main` đã được compile cùng `Codec` v1, nên constant pool có reference đến `demo.lib.Codec.encode:()Ljava/lang/String;`.
2. Runtime `-cp` có `out/app` và **`out/v2`**, nhưng **không có `out/v1`**.
3. `Codec.origin()` có trong v2, nên chương trình in `lib-v2`: một bằng chứng **runtime class thực sự đến từ API v2**, không phải dependency declaration.
4. Khi resolve `Codec.encode()`, JVM không tìm được method tương ứng trong runtime class v2, nên ném `NoSuchMethodError`.
5. `catch` nằm trong code app giúp stdout ổn định, không phụ thuộc message/stack trace dài mà JVM tạo.

Trong hệ production, **không khuyến khích bắt `NoSuchMethodError` rồi cho ứng dụng tiếp tục bình thường**. Đây là cơ chế cố ý trong demo để quan sát failure. Một lỗi ABI của dependency runtime thường cần chặn khởi động hoặc sửa deployment.

### 8.8. Nếu v1 và v2 cùng nằm trên classpath?

Thử Linux/macOS:

```bash
java -cp "out/app:out/v1:out/v2" demo.app.Main
java -cp "out/app:out/v2:out/v1" demo.app.Main
```

Kết quả dự kiến:
- Đặt `out/v1` trước → `Codec` v1 thường được application loader tìm trước; hai dòng tương ứng `lib-v1` và `encoded`.
- Đặt `out/v2` trước → `Codec` v2 thường được chọn; in `lib-v2` và `caught: NoSuchMethodError`.

Trên Windows thay separator `:` bằng `;`. **Đây là mô hình application classpath thông thường của demo**, không phải quy tắc bao quát cho custom ClassLoader, module layers hoặc container. Nếu parent loader đã định nghĩa cùng binary name, thứ tự entries ở child cũng không quyết định class nguồn thực tế.

### 8.9. Đọc bytecode của caller

```bash
javap -verbose -classpath out/app demo.app.Main
```

Tìm constant-pool references tới `demo/lib/Codec.origin` và `demo/lib/Codec.encode`. Nếu dùng `javap -c`, tìm invocation instructions tại body `main`.

Sau khi thay runtime sang v2, **caller class file không tự sửa** để đổi `encode()` thành `newer()`. Đó là nguyên lý cốt lõi dẫn tới binary linkage errors.

## 9. In ra thông tin class thực sự được JVM định nghĩa

Muốn biết “`demo.lib.Codec` đến từ đâu” ở process đang chạy, sử dụng probe trên **`Class<?>` thật**, không chỉ từ build report.

### 9.1. File RuntimeClassProbe.java

```java
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public final class RuntimeClassProbe {
    public static void report(Class<?> type) {
        System.out.println("class=" + type.getName());

        ClassLoader loader = type.getClassLoader();
        System.out.println("defining loader="
                + (loader == null ? "<bootstrap>" : loader.toString()));

        Module module = type.getModule();
        System.out.println("module="
                + (module.isNamed() ? module.getName() : "<unnamed>"));

        ProtectionDomain pd = type.getProtectionDomain();
        CodeSource source = pd == null ? null : pd.getCodeSource();
        URL url = source == null ? null : source.getLocation();

        System.out.println("code source="
                + (url == null ? "<unavailable>" : url));

        ClassLoader tccl =
                Thread.currentThread().getContextClassLoader();
        System.out.println("TCCL="
                + (tccl == null ? "<bootstrap>" : tccl.toString()));
    }

    public static void main(String[] args) {
        report(RuntimeClassProbe.class);
    }
}
```

Biên dịch và chạy như chương trình Java 17 bình thường:

```bash
javac --release 17 RuntimeClassProbe.java
java RuntimeClassProbe
```

**Không có stdout cố định cho loader name, URL và TCCL.** Những giá trị đó tùy môi trường thực tế, là thứ ta muốn chẩn đoán. Các dòng `class=RuntimeClassProbe` và `module=<unnamed>` là giá trị phổ biến của cách chạy ví dụ `java RuntimeClassProbe` trên classpath, nhưng không thay thế việc kiểm tra process.

Để kiểm tra một dependency class trong ứng dụng của bạn:

```java
// Trích đoạn ở vị trí đã có compile-time type:
RuntimeClassProbe.report(demo.lib.Codec.class);
```

Lệnh trên yêu cầu class `demo.lib.Codec` có sẵn tại compile time. Khi không được phép import class vì module access hoặc muốn tránh liên kết trực tiếp, dùng một đường `Class.forName` với **loader thích hợp** và bắt đúng exceptions.

### 9.2. CodeSource hữu ích nhưng không tuyệt đối

`type.getProtectionDomain().getCodeSource().getLocation()` có thể cho thấy:
- `file:/.../out/v2/`;
- `file:/.../some-lib.jar`;
- URL từ class loader/launcher;
- hoặc `null` nếu không có thông tin nguồn.

**Giới hạn:**
- Không phải mọi loader có CodeSource hữu ích; generated classes và một số container/transformer có thể cho `null` hoặc nguồn tổng hợp.
- Nested JAR launchers có thể trả scheme/URL đặc thù.
- CodeSource location cho biết nguồn được gắn với ProtectionDomain, **không phải bằng chứng cryptographic rằng bytes bằng 100% file ở disk hiện tại**.
- Nếu JAR trên disk bị thay sau khi class đã load, CodeSource path vẫn có thể giống nhưng JVM đang giữ class bytes cũ.
- Nếu có Java agent transform class trước khi định nghĩa, source JAR có thể không khớp bytecode cuối được JVM nhận.

Nên kết hợp CodeSource với class loading logs, artifact checksum, version/build metadata và runtime loader graph.

## 10. java.class.path không nói hết các nguồn nạp

Trong chương trình:

```java
System.out.println(
        System.getProperty("java.class.path"));
```

Đoạn này in system property liên quan application classpath, hữu ích để bắt đầu kiểm tra. Nhưng:

- Các custom `URLClassLoader` instances có URLs riêng không nhất thiết có trong `java.class.path`.
- JPMS named modules đi qua `--module-path` và module layer resolution; module path không hiện đầy đủ như các classpath entries bình thường.
- Executable/fat/nested JAR launchers có thể xây runtime loaders riêng.
- Java agents có thể thêm JAR vào bootstrap/system loader search.
- Servlet/container/plugin host có loader graph riêng.
- Application's `-cp` có thể được ghi đè theo launcher/manifest rules.
- Program có thể được đóng gói thành native image, không tương đương JVM classpath theo mô hình truyền thống.

**Câu hỏi đúng:** “Class cụ thể được defining loader nào định nghĩa từ nguồn nào trong process thực tế?” Không chỉ “java.class.path hiện có chuỗi gì?”.

## 11. Trường hợp java -jar, manifest Class-Path và executable JAR

Câu lệnh:

```bash
java -jar app.jar
```

chọn main class từ manifest của JAR theo quy tắc `-jar`. Với Java launcher chuẩn, `-jar` **bỏ qua `-cp`/`CLASSPATH` trong vai trò user classpath** thông thường cho ứng dụng đó. Một manifest có thể có `Class-Path` liệt kê các JAR tương đối ngoài artifact.

Ví dụ manifest:

```text
Manifest-Version: 1.0
Main-Class: com.example.Main
Class-Path: lib/a.jar lib/b.jar
```

Đây là **nội dung manifest minh họa**, phải được đóng gói đúng trong `META-INF/MANIFEST.MF`, không phải shell command độc lập.

Với Spring Boot executable JAR phổ biến:
- Implementation dependencies có thể nằm trong `BOOT-INF/lib`.
- App classes có thể ở `BOOT-INF/classes`.
- Launcher riêng tạo class loader phục vụ nested JARs.
- `java.class.path` có thể chỉ hiện đường đến artifact executable, **không liệt kê mọi nested library JAR**.

Vì vậy `mvn dependency:tree` đúng, executable artifact đúng dependencies, nhưng runtime class source vẫn cần kiểm tra qua loader/launcher thực tế.

### Kiểm tra artifact cuối

```bash
jar --list --file app.jar
```

Thay `app.jar` bằng file thật. Với WAR, kiểm tra `WEB-INF/lib` và `WEB-INF/classes`; với JAR, xem manifest và vị trí packaged resources/dependencies.

**Đừng nhầm thư mục `target/classes`/`build/classes/java/main` của build workspace với nội dung artifact đã deploy.**

## 12. App server: “provided” có nghĩa container là nguồn runtime

Một ứng dụng web có thể declare dependency `provided` cho Servlet API (tên package và artifact cụ thể tùy Jakarta/Java EE version). Build tool compile được, nhưng WAR thường không đóng JAR provided vào `WEB-INF/lib`. **Container** cung cấp API qua loader chung.

Sơ đồ khái niệm:

```text
Bootstrap / Platform
        │
  Container Common Loader
        │
  Webapp ClassLoader
        ├── WEB-INF/classes
        └── WEB-INF/lib/*.jar
```

Điều này khiến:
- `dependency:tree` vẫn cho thấy dependency `provided` trong build model;
- runtime webapp source có thể do common/container loader cung cấp;
- container version có thể khác version compile;
- hai webapps có bản dependency private khác nhau hoặc cùng chia sẻ container library;
- local unit tests chạy bằng Maven/Gradle không tái hiện đúng loader graph container.

Với Tomcat, webapp loader có quy tắc local-first cho nhiều web application classes và ngoại lệ delegation dành cho các platform/container API. **Không tự áp parent-first mặc định của ClassLoader lên mọi loại loader mà không đọc tài liệu container/version.**

### Cách kiểm tra đúng

- In `type.getClassLoader()` và `CodeSource` bên trong process container, không chỉ trong test local.
- Liệt kê JARs trong WAR/lib và shared/container libs thật.
- Kiểm tra parent delegation/order và excluded/filtered packages.
- Nếu deployment trên nhiều nodes/pods, bảo đảm từng node thật sự chạy cùng artifact/image version.
- So class compile-time API với class ở container runtime.
- Kiểm tra loader được sử dụng bởi framework, TCCL và thư viện SPI.

Một vấn đề chỉ xảy ra sau redeploy còn có thể liên quan **old loader generations** bị host registry/thread giữ lại, không đơn giản là version mismatch.

## 13. JPMS: module path là một nguồn khác

Với Java 9+, chương trình có thể khởi động bằng:

```bash
java --module-path mods --module my.app/my.app.Main
```

Những modules quan sát được trên `mods` không nhất thiết đều resolved. Runtime class được gắn với **Module** và có thể nằm trong **ModuleLayer**.

Khi debug:

```java
// Trích đoạn:
Class<?> type = my.app.SomeType.class;
System.out.println(type.getModule().getName());
System.out.println(type.getModule().getLayer());
System.out.println(type.getClassLoader());
```

Đây là trích đoạn, class `my.app.SomeType` chỉ là ví dụ. Với cùng loader phục vụ nhiều modules, `ClassLoader` không đủ xác định package export/readability; phải xem `Module`.

Các lỗi điển hình:
- `module not found` vì module path/root/resolution;
- `IllegalAccessError` hoặc `InaccessibleObjectException` khi `exports`/`opens` không phù hợp;
- `ClassNotFoundException` vì loader/layer được hỏi không sở hữu class;
- `ClassCastException` nếu cùng binary name được định nghĩa trong loaders/layers khác nhau.

**Không biến lỗi JPMS access thành “Gradle/Maven chọn sai version”** trước khi xác minh dependency graph và module rules.

## 14. Test runner: cây dependency đúng nhưng JVM test khác JVM production

### 14.1. Maven Surefire

Maven Surefire/Failsafe có thể dùng forked JVM workers, provider-specific class loading và test classpaths. Các options như:
- `forkCount`;
- `reuseForks`;
- `useSystemClassLoader`;
- `argLine`;
- test classpath customization;

có thể thay đổi process/ClassLoader được sử dụng cho tests.

Một test nhìn thấy library qua `test` scope không chứng minh production có library đó. Ngược lại, container runtime có thư viện do server cung cấp mà Surefire không có.

### 14.2. Gradle Test

Gradle `Test` task thường chạy tests trong forked JVM workers. Các cấu hình `maxParallelForks` và `forkEvery` ảnh hưởng parallel/fork lifecycle, nhưng **không tự bảo đảm mỗi test method có JVM riêng**.

Nếu test pass bằng `./gradlew test` nhưng fail bằng `java -jar`:
- so `testRuntimeClasspath` và main `runtimeClasspath`;
- xem những test dependencies nào không được đóng gói;
- xem JVM options/agents và system properties;
- xác định fixtures/mock frameworks có override classes không;
- so application launcher và artifact thực tế.

### 14.3. IDE launch

IDE có thể sử dụng classpath từ IDE model, Gradle import hoặc delegated build/run. Một run configuration còn có thể chọn JDK khác, working directory khác, env vars khác và module path khác.

Để tái hiện production, ghi **đúng launch command** hoặc dùng đúng task/distribution, thay vì suy luận chỉ từ project dependencies.

## 15. Java agents và bytecode transformation

Các agents có thể:
- transform class bytes khi class được định nghĩa;
- retransformation/redefinition theo API và điều kiện JVM;
- thêm JARs vào bootstrap/system classloader search;
- tạo proxy/helper classes;
- thay thứ tự/nguồn class mà bạn quan sát.

Nếu `CodeSource` chỉ ra một JAR đúng nhưng method bytecode hoặc lỗi `VerifyError` không khớp artifact trên disk, hỏi:
- Có `-javaagent` hoặc runtime attach không?
- Có instrumentation bởi AOP/coverage/mock framework không?
- Agent nào đã transform class? Có dump transformed bytes được không?
- Class có được redefine/retransform sau khi nạp không?

**Cây dependency không biết chính xác bytes đã transform**. Đây là lý do chẩn đoán `ClassFormatError`/`VerifyError` cần cả class-file bytes trước và sau transformer, không chỉ dependency versions.

## 16. Đọc nguồn class runtime qua JVM logging

Trên HotSpot/JDK 17 có thể bật `-Xlog:class+load=info` từ lúc start process, rồi lọc đúng binary name nghi dùng sai phiên bản:

```bash
java -Xlog:class+load=info -cp "out/app:out/v2" demo.app.Main
```

Trên Windows, thay dấu `:` phân cách classpath bằng `;`. Source trong dòng log là dấu hiệu runtime có ích, nhưng phải đối chiếu defining loader và `CodeSource` của **runtime `Class<?>`**, vì một binary name có thể do nhiều loader định nghĩa. Với process đang chạy, `jcmd PID VM.command_line` và `VM.classloader_stats` bổ sung launch/loader details; kiểm tra `jcmd PID help` và kiểm soát thông tin nhạy cảm trong output. Bài `-verbose:class`/`-Xlog` của chương hướng dẫn đầy đủ về tag, level, output và lọc log.
## 17. Phát hiện candidate class trùng bằng resource enumeration

Khi nghi hai JAR cùng có một `demo/lib/Codec.class`, `ClassLoader.getResources("demo/lib/Codec.class")` của **đúng runtime loader** có thể liệt kê các URL ứng viên:

```java
// Snippet trong method, loader là ClassLoader đang điều tra.
var entries = loader.getResources("demo/lib/Codec.class");
while (entries.hasMoreElements()) {
    System.out.println(entries.nextElement());
}
```

Phương thức yêu cầu xử lý `IOException`. Resource enumeration không chứng minh URL đầu tiên là bytes JVM đã define: parent-first/child-first policy, module encapsulation, generated classes và bytecode transformation có thể làm khác class lookup. Luôn đối chiếu `Class.getClassLoader()` và `CodeSource`/class-load log của class đang chạy. Bài “Liệt kê tài nguyên trùng tên...” có thí nghiệm riêng về `getResource` (winner), `getResources` (candidates) và thứ tự parent/URL lookup.
## 18. Cây dependency và dependency conflict: Maven khác Gradle

Giả sử graph khai báo:

```text
app
├── library-A ── codec:1.5
└── library-B ── codec:2.1
```

**Maven:** với các dependencies cùng group/artifact có khả năng conflict, quy tắc nearest definition và tie-order, dependencyManagement/BOM quyết định version cuối trong scope/configuration cụ thể.

**Gradle:** conflict resolution/constraints/capabilities/variants và strategies của configuration quyết định component cuối; hành vi mặc định khác Maven, nhưng version forcing/strict constraints/platform có thể quyết định kết quả.

Cả hai đều cần hỏi:
1. **Build tool chọn version nào trong đúng configuration?**
2. **Artifact version đó có thực sự được đặt trong JAR/WAR/distribution cuối không?**
3. **Runtime launcher có thêm/bớt nguồn không?**
4. **ClassLoader nào thực sự định nghĩa class?**
5. **Bytes runtime có khớp API mà caller đã được compile cùng không?**

Chỉ thay dependency declaration mà không kiểm tra 4 câu sau có thể tạo false fix: local test xanh nhưng staging server vẫn load một bản JAR cũ ở common lib.

## 19. Scope và exclusions: runtime bị thiếu hay bị cung cấp từ nơi khác?

Hai nhóm tình huống trái ngược:

### 19.1. Dependency bị thiếu

Build tool compile thành công với API của library, nhưng runtime deployment loại library đó. Khi JVM dùng code cần class bên trong, có thể nhận:
- `ClassNotFoundException` từ explicit lookup;
- `NoClassDefFoundError` từ symbolic reference cần được resolution;
- lỗi service discovery nếu thiếu provider implementation.

Điều tra:
- dependency scope, exclusions và optional flags;
- packaging plugin và artifact cuối;
- classpath production thực tế;
- module resolution nếu JPMS;
- loader visibility và TCCL.

### 19.2. Dependency được server hoặc loader khác cung cấp

Build tree không liệt kê một phiên bản cho runtime host, nhưng server có JAR trong shared libs. Một class có thể load thành công nhưng API version sai, gây `NoSuchMethodError`/`NoSuchFieldError`/`IllegalAccessError`.

Điều tra:
- container common libs;
- `WEB-INF/lib`;
- extension/plugin loader sources;
- child-first/parent-first policy;
- duplicate binary names và CodeSource;
- actual process/container image version.

Với Maven `provided`, việc dependency không đóng trong artifact có thể **là thiết kế đúng**, nhưng phải đối chiếu **server API/version thực**.

## 20. Failure taxonomy để tránh chữa sai

| Triệu chứng | Điều có thể xảy ra | Cần kiểm tra |
|---|---|---|
| `ClassNotFoundException` | Loader được yêu cầu không tìm ra class | Binary name, loader, URLs, packaging, module graph |
| `NoClassDefFoundError` | JVM cần class không khả dụng hoặc class init từng thất bại | Dependency chain, cause, initialization |
| `NoSuchMethodError` | Caller build với API có method, runtime class thiếu đúng signature | Chênh compile/runtime versions và CodeSource |
| `NoSuchFieldError` | Field runtime name/descriptor không khớp | ABI và selected JAR |
| `IncompatibleClassChangeError` | Contract class/interface hoặc static/instance sai | Binary compatibility |
| `ClassCastException` giữa hai tên giống | Defining ClassLoaders khác nhau | Loader identities, parent/shared API |
| `UnsupportedClassVersionError` | Runtime JDK không hiểu class-file version | `java -version`, `javap -verbose`, `--release` |
| `ClassFormatError` | Bytes class file không hợp lệ | Actual bytes, artifact corruption, transformer |
| `VerifyError` | Bytecode invalid theo verifier | After-transform bytes, frames, agent |
| `IllegalAccessError`/`InaccessibleObjectException` | Package/member access bị từ chối | JPMS requires/exports/opens, runtime package |
| `ServiceConfigurationError` | Provider config/constructor/classloader/module sai | `META-INF/services`, `uses/provides`, TCCL |

Mọi symptom ở bảng đều cần **lỗi đầu tiên + class source + loader** để phân loại, không được kết luận tự động chỉ vì tên exception.

## 21. Quy trình chẩn đoán có thể lặp lại

### Bước A — Đóng băng các thông số launch

Ghi lại:
- version Java runtime (`java -version`);
- build tool version và active profiles;
- artifact/build commit/checksum;
- exact launch command, working directory, env vars;
- classpath/module-path/manifest/launcher;
- JVM flags và Java agents;
- container image và shared libs, nếu có.

Đừng copy secrets từ `VM.system_properties`, command line hay environment ra log/tracker công khai.

### Bước B — Xác minh resolved graph

Maven:

```bash
mvn dependency:tree
mvn help:effective-pom
```

Gradle:

```bash
./gradlew :app:dependencies --configuration runtimeClasspath
./gradlew :app:dependencyInsight --dependency org.example:codec --configuration runtimeClasspath
```

Chạy với **đúng module/profile/configuration** của ứng dụng bị lỗi. Nếu test lỗi, xem test runtime; nếu production lỗi, xem main runtime và packaging.

### Bước C — Xác minh artifact thực tế

- Kiểm tra JAR/WAR nội dung, `META-INF/MANIFEST.MF`, bundled libs.
- Đối chiếu checksums/build IDs với artifact trong image/server.
- Kiểm tra dependency scope, exclusions/relocation, plugin packaging.
- Xem library do server/host/agent cung cấp ngoài build tool không.

### Bước D — Xác minh defining loader và class source

- Ghi `Class.getClassLoader()` và `getModule()`.
- Ghi `CodeSource` nếu có.
- Dùng `-Xlog:class+load=info` để tìm class source.
- Dùng `getResources` khi nghi duplicate entries.
- Khi có plugin, kiểm tra shared API types do parent định nghĩa hay bị child định nghĩa lại.

### Bước E — So ABI và bytecode

- `javap -verbose`/`-c` trên **runtime artifact** và **compile artifact**;
- so method names + descriptors, field types, class/interface flags;
- nếu có agent/transformer, dump/so bytes sau biến đổi;
- so JDK class-file major/minor;
- tái hiện bằng **JVM mới hoặc ClassLoader mới** nếu class cũ đã cached.

### Bước F — Sửa nguyên nhân tại đúng tầng

- Build graph sai → điều chỉnh dependency declarations, constraints/BOM, exclusions/scope đúng.
- Artifact packaging sai → sửa packaging/distribution, rebuild image/redeploy.
- Launcher/classpath sai → sửa run task, manifest, container libs, IDE configuration.
- Loader isolation sai → dùng shared API ở parent, sửa delegation/module layer.
- ABI không tương thích → đồng bộ compile/runtime versions hoặc rebuild callers.
- Agent sinh bytecode sai → sửa transformer, không chỉ đổi JAR declarations.

Sau sửa phải **xác nhận runtime class source thay đổi theo dự kiến**, không dừng ở kết quả `mvn dependency:tree`/`gradle dependencies` “đẹp hơn”.

## 22. Checklist nhanh Maven và Gradle

| Hỏi | Maven | Gradle |
|---|---|---|
| Dependency graph? | `mvn dependency:tree` | `dependencies --configuration runtimeClasspath` |
| Vì sao version được chọn? | effective POM, dependencyManagement và tree | `dependencyInsight --dependency ...` |
| Main runtime dependencies? | `dependency:build-classpath` với scope phù hợp | `runtimeClasspath` |
| Test dependencies? | Test scope/Surefire classpath | `testRuntimeClasspath` |
| Main output classes ở đâu? | `target/classes` thường gặp | `build/classes/java/main` thường gặp |
| Packaging? | JAR/WAR/assembly/shade/Boot plugin | JAR/distribution/shadow/Boot task |
| Launch process? | `java -jar`, exec plugin, Surefire, container | `run`/`JavaExec`/`test`/IDE/distribution |
| Runtime class source? | JVM probe/`-Xlog:class+load` | JVM probe/`-Xlog:class+load` |

**Lưu ý:** cấu trúc thư mục là quy ước phổ biến, có thể bị build customizations thay đổi. Không suy dự án dùng `target/classes` hay `build/classes/java/main` chỉ từ tên công cụ; kiểm tra configuration thật.

## 23. Bài tập tự kiểm tra

1. **Maven dependency tree cho biết chính xác mọi class đã load?** Không; đó là graph theo cấu hình build, không phải log runtime class definitions.
2. **Nếu có hai JAR chứa cùng binary name trên classpath, điều gì quyết định class nào được dùng?** Loader/search/delegation order và trạng thái đã nạp; không chỉ version mới nhất.
3. **`java.class.path` có liệt kê mọi custom URLClassLoader URL?** Không.
4. **TestRuntimeClasspath có nhất thiết giống runtimeClasspath?** Không; test thêm frameworks/fixtures và output riêng.
5. **Maven `provided` bị loại khỏi WAR có chắc là lỗi không?** Không; container có thể là nguồn runtime API.
6. **Gradle dependencyInsight giải đáp gì?** Vì sao một dependency/version/variant được chọn cho configuration.
7. **Caller compile với `Codec.encode()` nhưng runtime class không có method đó có thể gây gì?** `NoSuchMethodError`.
8. **Dòng `runtime library: lib-v2` trong demo xác nhận điều gì?** `Codec.origin()` được gọi trên runtime class từ version v2 trong classpath của demo.
9. **`CodeSource` luôn là JAR path chính xác của class bytes cuối cùng không?** Không; có thể null/synthetic hoặc bytes bị transform.
10. **Khi chạy `java -jar`, thêm `-cp` là cách mặc định thay user classpath không?** Không; `-jar` dùng cơ chế launcher/manifest riêng.
11. **Executable JAR có nested libs có nhất thiết hiện đầy đủ trong java.class.path không?** Không.
12. **`Class.getClassLoader()` trả loader khởi xướng hay defining loader?** Defining loader.
13. **Hai classes cùng tên do loaders khác nhau định nghĩa có chắc cast được không?** Không.
14. **Một module trên module path có chắc đã resolved không?** Không.
15. **`getResources("p/Foo.class")` có chứng minh Foo được định nghĩa từ URL đầu tiên?** Không; resource lookup không đồng nhất class loading.
16. **CodeSource trỏ tới JAR đúng nhưng `VerifyError` vẫn có thể do đâu?** Agent/transformer thay bytes trước khi JVM define.
17. **Local unit test pass nhưng container fail: cần so những gì?** Test vs prod classpath, packaging, server libs, loader hierarchy, runtime JDK.
18. **Nếu fix dependency tree mà runtime vẫn lỗi?** Phải kiểm tra artifact deployed, launch context, loader and class source.
19. **Maven và Gradle giải quyết version conflict hoàn toàn giống nhau không?** Không.
20. **Dữ kiện tối thiểu để chẩn đoán mismatch tốt?** Error gốc, class name, defining loader, CodeSource/log, runtime JDK, resolved graph và launch command.

## 24. Tài liệu tham khảo

- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): delegation, `loadClass`, `findClass` và class loader identity.
- [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getClassLoader()`, `getModule()`, runtime type.
- [Java SE 17 — CodeSource](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/CodeSource.html): nguồn code gắn với protection domain.
- [Java SE 17 — JVM options/unified logging](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-classpath`, `-jar`, `-Xlog` và startup.
- [Apache Maven Dependency Plugin — dependency:tree](https://maven.apache.org/plugins/maven-dependency-plugin/tree-mojo.html): cây dependency đã resolve.
- [Apache Maven Dependency Plugin — dependency:build-classpath](https://maven.apache.org/plugins/maven-dependency-plugin/build-classpath-mojo.html): tạo classpath artifact paths.
- [Maven — Introduction to the Dependency Mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html): scopes, mediation, dependencyManagement và transitivity.
- [Gradle — Viewing Dependencies](https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html): `dependencies` và `dependencyInsight`.
- [Gradle — Java Plugin](https://docs.gradle.org/current/userguide/java_plugin.html): configurations và source sets.
- [Apache Tomcat — Class Loader How-To](https://tomcat.apache.org/tomcat-11.0-doc/class-loader-howto.html): server common/webapp loader, delegation.
- [Java SE 17 — JVM Specification `5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): loading, linking, runtime class identity.

**Kết luận:** **Dependency tree ≠ runtime classpath ≠ class thực sự được JVM định nghĩa**. Maven/Gradle giúp xác định dependencies/versions theo configuration; launcher, server, module layer, plugin ClassLoader và agents quyết định những nguồn nạp class lúc chạy. Muốn sửa lỗi đáng tin cậy, phải **đối chiếu cả graph của build với process thật**, xem **defining loader, CodeSource/class-load logs và binary API**, rồi xác nhận bytes/runtime source đã đúng sau khi redeploy.


</details>

- [Quay lại đầu trang](#back-to-top)
