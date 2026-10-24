<a id="back-to-top"></a>

# Lỗi thường gặp

## Menu
- [ClassNotFoundException: yêu cầu tìm lớp tường minh không thành công](#common-class-loading-errors-001)
- [NoClassDefFoundError do thiếu lớp phụ thuộc hoặc sai tên](#common-class-loading-errors-002)
- [NoClassDefFoundError sau khi khởi tạo lớp thất bại](#common-class-loading-errors-003)
- [UnsupportedClassVersionError và phiên bản bytecode](#common-class-loading-errors-004)
- [ClassFormatError, VerifyError và ClassCircularityError](#common-class-loading-errors-005)
- [LinkageError do định nghĩa lớp trùng hoặc vi phạm loading constraint](#common-class-loading-errors-006)
- [NoSuchMethodError và NoSuchFieldError do lệch phiên bản](#common-class-loading-errors-007)
- [AbstractMethodError và IncompatibleClassChangeError](#common-class-loading-errors-008)
- [IllegalAccessError, IllegalAccessException và InaccessibleObjectException](#common-class-loading-errors-009)
- [ClassCastException do khác định danh loader](#common-class-loading-errors-010)
- [SecurityException do package sealing, chứng thư hoặc package bị cấm](#common-class-loading-errors-011)
- [UnsatisfiedLinkError và native library trong nhiều loader](#common-class-loading-errors-012)

## <a id="common-class-loading-errors-001">ClassNotFoundException: yêu cầu tìm lớp tường minh không thành công</a>

<details>
<summary>Xem chi tiết</summary>

# ClassNotFoundException: yêu cầu tìm lớp tường minh không thành công

`java.lang.ClassNotFoundException` (thường viết tắt **CNFE**) là **checked exception** báo rằng một thao tác **chủ động yêu cầu tìm/nạp class theo tên** không thể tìm được định nghĩa phù hợp qua class loader được sử dụng. Những điểm vào tiêu biểu là `Class.forName(...)`, `ClassLoader.loadClass(...)` và các thư viện/framework gọi những API ấy để tải driver, plugin, implementation hoặc lớp cấu hình bằng tên.

Ví dụ một ứng dụng nhận `"com.example.plugins.Reporter"` từ file cấu hình rồi gọi:

~~~java
Class<?> type = Class.forName("com.example.plugins.Reporter");
~~~

Nếu loader được chọn không tìm ra class này, phương thức ném `ClassNotFoundException`. Điều đó **không chứng minh file `Reporter.class` không tồn tại trên máy**: class có thể nằm trong một JAR khác, một module chưa được resolve, thư mục chưa đặt đúng classpath hoặc một child loader mà caller không sử dụng.

**Ý chính của bài:**
- CNFE phản ánh **yêu cầu tìm class tường minh bị thất bại với loader và tên được yêu cầu**.
- `Class.forName(String)`, `Class.forName(String, boolean, ClassLoader)` và `loader.loadClass(String)` có quy tắc chọn loader/initialization khác nhau.
- Tên binary, đường dẫn classpath/module path và phạm vi nhìn thấy của loader phải đồng thời đúng.
- `NoClassDefFoundError` là lỗi JVM/linkage khác, thường xuất hiện khi một class mà bytecode cần tới không thể định nghĩa/khởi tạo; không nên coi hai tình huống là cùng một loại lỗi.
- Cách debug đúng là xác định **tên class được yêu cầu, loader nào tìm, artifact nào chứa bytecode và bước nào thực sự thất bại**.

## 1. Định nghĩa trong hệ thống ngoại lệ Java

Sơ đồ kế thừa:

~~~text
java.lang.Object
  └── java.lang.Throwable
       └── java.lang.Exception
            └── java.lang.ReflectiveOperationException
                 └── java.lang.ClassNotFoundException
~~~

Vì CNFE là checked exception, lời gọi có khai báo `throws ClassNotFoundException` phải được **catch** hoặc khai báo tiếp trong chữ ký method.

~~~java
public static Class<?> locate(String binaryName)
        throws ClassNotFoundException {
    return Class.forName(binaryName);
}
~~~

Đây là method minh họa. Caller phải xử lý ngoại lệ hoặc tiếp tục `throws`.

Ví dụ xử lý có ngữ cảnh:

~~~java
try {
    Class<?> type = Class.forName(configuredClassName);
    // Tiếp tục kiểm tra contract và khởi tạo.
} catch (ClassNotFoundException ex) {
    throw new IllegalStateException(
            "Configured provider was not found: " + configuredClassName,
            ex);
}
~~~

Đây là đoạn minh họa với `configuredClassName` đã có, không phải chương trình độc lập. Việc bọc trong `IllegalStateException` có thể thích hợp khi **thiếu plugin là lỗi cấu hình nghiêm trọng**; nếu plugin tùy chọn thì nên chọn chính sách fallback có chủ đích thay vì lúc nào cũng fail toàn bộ host.

### Tên class trong exception

Thông báo hoặc stack trace thường chứa binary name được yêu cầu, ví dụ:

~~~text
java.lang.ClassNotFoundException: com.example.plugins.Reporter
~~~

Thông báo thực tế có thể khác tùy loader/JVM; đừng dựa vào việc chuỗi lỗi nhất thiết đúng một mẫu ký tự. Điều đáng tin hơn là **loại exception**, tên đã truyền vào API, cause chain, và loader đang được dùng.

## 2. Vì sao “class tồn tại” nhưng vẫn nhận CNFE?

Một câu trả lời chính xác luôn cần ba phần:

~~~text
Lời gọi đang thực hiện:
  tên nhị phân nào?
              |
              v
  ClassLoader nào nhận yêu cầu?
              |
              v
  Loader ấy tìm ở nguồn nào?
              |
              v
  Class có sẵn/được tìm thấy theo quy tắc đó không?
       /                             \
     Có                              Không
     |                                 |
 Class<?> hoặc                  ClassNotFoundException
 lỗi ở bước khác                (tùy API)
~~~

Nếu `out/plugins/com/acme/Feature.class` tồn tại nhưng ứng dụng chỉ chạy với classpath `out/app`, application loader **không tự quét thêm** `out/plugins`. Tương tự, một JAR plugin có thể được một `URLClassLoader` con nhìn thấy, nhưng `Class.forName(String)` gọi từ host vẫn chọn loader của **caller** và không tự đoán child loader.

### Bốn điều kiện phổ biến

1. **Đúng tên:** `com.acme.Feature` chứ không phải `com/acme/Feature.class`.
2. **Đúng nơi:** root của classpath hoặc URL phải là thư mục/JAR chứa đường dẫn package đúng.
3. **Đúng loader:** loader thực thi lookup thực sự phải thấy nguồn đó, trực tiếp hoặc qua delegation có hiệu lực.
4. **Đúng cơ chế khởi chạy:** application classpath, executable JAR và JPMS module path không phải các cấu hình đồng nghĩa.

Nhiều lỗi CNFE là lỗi **configuration/loader visibility**, không phải thiếu file ở cấp hệ điều hành.

## 3. Những API nào có thể ném ClassNotFoundException?

### 3.1. `Class.forName(String)`

~~~java
Class<?> c = Class.forName("com.acme.Feature");
~~~

Overload một tham số sử dụng **defining class loader của class gọi API** để tìm class và mặc định yêu cầu initialization. Nó **không đơn giản lấy TCCL** mà thread đang mang.

Một class trong host có thể gọi thành công `Class.forName("com.acme.HostHelper")` nhưng thất bại với `"com.acme.plugin.Feature"` nếu Feature chỉ có trong child plugin loader.

### 3.2. `Class.forName(String, boolean, ClassLoader)`

~~~java
Class<?> c = Class.forName(
        "com.acme.plugin.Feature",
        false,
        pluginLoader);
~~~

Đây là mảnh minh họa khi `pluginLoader` đã được cấu hình.

- `name`: tên nhị phân của class/interface, hoặc dạng tên mảng theo quy tắc API.
- `initialize=false`: không yêu cầu chạy initializer của class.
- `initialize=true`: yêu cầu khởi tạo class nếu chưa initialized; có thể sinh exception/error ở giai đoạn initialization **khác với CNFE**.
- `loader`: class loader **chỉ định tường minh**; đây là cách đáng tin khi plugin class không nằm trong loader của host.
- Theo API, truyền `null` cho loader có ý nghĩa dùng bootstrap loader, không phải “tìm trên mọi classpath”.

Nếu class tìm được nhưng initializer ném exception, kết quả có thể là `ExceptionInInitializerError` hoặc một `Error` liên quan, **không phải CNFE** chỉ vì class không chạy được.

### 3.3. `ClassLoader.loadClass(String)`

~~~java
Class<?> c = pluginLoader.loadClass("com.acme.plugin.Feature");
~~~

`loadClass` mặc định **không yêu cầu initialization**; nó tìm/nạp kiểu theo chính sách delegation của loader. Nếu không tìm thấy, thường ném CNFE. Khi class tìm được nhưng bytes không hợp lệ hoặc linking/access không hợp lệ, các `LinkageError`/`SecurityException` khác có thể xuất hiện.

Với loader mặc định parent-first:

~~~text
child.loadClass(name)
      |
      v
findLoadedClass(name)
      |
      +-- chưa nạp -> parent.loadClass(name)
                           |
                           +-- parent tìm được -> trả Class của parent
                           |
                           +-- không -> child.findClass(name)
                                            |
                                            +-- có -> define Class
                                            |
                                            +-- không -> CNFE
~~~

**Gọi qua child không đồng nghĩa child sẽ là defining loader.** Nếu parent trả được class, `c.getClassLoader()` vẫn là parent. Khi debug, phân biệt initiating loader và defining loader.

### 3.4. Reflection và thư viện/framework

`ClassNotFoundException` có thể xuất hiện khi:

- Hệ thống plugin đọc tên implementation từ cấu hình và gọi `Class.forName`.
- Code tạo `URLClassLoader` rồi gọi `loadClass` với class name đã cấu hình.
- Framework đọc metadata chứa binary names và gọi reflection để tìm class.
- Hệ thống JDBC đời cũ gọi `Class.forName("...Driver")` để khởi tạo class driver; JDBC 4+ còn có cơ chế service-provider discovery nên **không phải driver nào cũng cần gọi `Class.forName` thủ công**.
- Một framework hay thư viện che CNFE bên dưới ngoại lệ cao hơn, ví dụ provider discovery failure hoặc wrapper exception.

**Không đánh đồng:** `new SomeClass()` trong mã đã biên dịch không phải một yêu cầu tên-string giống `Class.forName`. Khi dependency cho bytecode tham chiếu bị mất, JVM thường báo `NoClassDefFoundError`/linkage failure thay vì checked CNFE trực tiếp tại source Java.

## 4. Binary name, package path và classpath root

Giả sử source:

~~~java
package com.acme.plugin;

public final class Reporter {
}
~~~

Sau khi compile, file thường ở:

~~~text
out/plugin/
  com/
    acme/
      plugin/
        Reporter.class
~~~

Tên API `loadClass`/`forName` là:

~~~java
"com.acme.plugin.Reporter"
~~~

Không phải:

~~~text
com/acme/plugin/Reporter.class
com.acme.plugin.Reporter.class
/out/plugin/com/acme/plugin/Reporter.class
~~~

Những chuỗi sau là **đường dẫn** hoặc tên file, không phải binary class name mà các overload thông thường mong đợi.

### Inner/nested classes

Một static nested class:

~~~java
package com.acme;

public final class Outer {
    public static final class Inner {
    }
}
~~~

Tên nhị phân của nested class là:

~~~text
com.acme.Outer$Inner
~~~

Dùng `Class.forName("com.acme.Outer$Inner")`, **không** dùng `"com.acme.Outer.Inner"`. Trong code Java thông thường có thể tham chiếu `Outer.Inner.class`, nhưng string binary name và cú pháp source type name khác nhau.

### Mảng và primitive types

`Class.forName` có quy tắc riêng cho array class names, ví dụ:

~~~java
Class<?> a = Class.forName("[Ljava.lang.String;");
System.out.println(a == String[].class);
~~~

Với JDK thông thường, output là `true`. Còn:

~~~java
Class.forName("int");
~~~

không phải cách lấy `int.class`; lời gọi ấy ném `ClassNotFoundException`. Dùng trực tiếp `int.class` cho primitive types.

**Khi debug CNFE, luôn log chuỗi đầu vào chính xác**. Một dấu `/`, `.class`, tên package sai chữ hoa/thường hoặc nhầm `$` với dấu `.` có thể là toàn bộ nguyên nhân.

## 5. Ví dụ Java 17+ hoàn chỉnh: host không tìm class, loader plugin tìm được

Ta tạo:

- Một class `com.acme.plugin.Greeting` trong **thư mục plugin riêng**.
- Một host class `ClassNotFoundLab` được compile/chạy **không đưa plugin vào application classpath**.
- Một `URLClassLoader` được host tạo, trỏ đúng vào thư mục plugin.
- Cùng một binary name thất bại khi host dùng loader mặc định, nhưng thành công khi chọn plugin loader tường minh.
- Một static initializer để minh họa khác biệt giữa **load** và **initialize**.

### 5.1. Cấu trúc project

~~~text
project/
  src/
    plugin/
      com/
        acme/
          plugin/
            Greeting.java
    app/
      ClassNotFoundLab.java
  out/
    plugin/              (sinh bởi javac)
    app/                 (sinh bởi javac)
~~~

**Không** đặt `out/plugin` vào application classpath khi chạy host; đó là điểm then chốt của thí nghiệm.

### 5.2. File `src/plugin/com/acme/plugin/Greeting.java`

~~~java
package com.acme.plugin;

public final class Greeting {
    static {
        System.out.println("plugin initialized");
    }

    public static String message() {
        return "hello from plugin";
    }
}
~~~

Initializer chỉ in khi class thực sự initialized. `loadClass` không bắt buộc initializer chạy; `Class.forName(name, true, loader)` yêu cầu điều đó.

### 5.3. File `src/app/ClassNotFoundLab.java`

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class ClassNotFoundLab {
    private static final String NAME =
            "com.acme.plugin.Greeting";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: ClassNotFoundLab <plugin-directory-or-jar>");
        }

        // A. Host không thấy out/plugin trên application classpath.
        try {
            Class.forName(NAME);
            System.out.println("default Class.forName: found");
        } catch (ClassNotFoundException ex) {
            System.out.println(
                    "default Class.forName: ClassNotFoundException");
        }

        try {
            ClassLoader.getSystemClassLoader().loadClass(NAME);
            System.out.println("system loadClass: found");
        } catch (ClassNotFoundException ex) {
            System.out.println(
                    "system loadClass: ClassNotFoundException");
        }

        // B. Plugin loader có URL root tới out/plugin.
        URL root = Path.of(args[0]).toAbsolutePath()
                .toUri().toURL();

        ClassLoader parent = ClassNotFoundLab.class.getClassLoader();

        try (URLClassLoader pluginLoader =
                new URLClassLoader("plugin", new URL[] {root}, parent)) {

            Class<?> type = pluginLoader.loadClass(NAME);

            System.out.println(
                    "explicit loadClass: " + type.getName());
            System.out.println("defined by plugin loader: "
                    + (type.getClassLoader() == pluginLoader));

            Class<?> same = Class.forName(
                    NAME, false, pluginLoader);
            System.out.println(
                    "same Class object: " + (same == type));

            System.out.println("before initialization");
            Class.forName(NAME, true, pluginLoader);

            Object answer = type.getMethod("message").invoke(null);
            System.out.println("invoke: " + answer);

            try {
                pluginLoader.loadClass("com.acme.plugin.Missing");
                System.out.println("missing name: found");
            } catch (ClassNotFoundException ex) {
                System.out.println(
                        "missing name: ClassNotFoundException");
            }

            try {
                pluginLoader.loadClass(
                        "com/acme/plugin/Greeting");
                System.out.println("slash name: found");
            } catch (ClassNotFoundException ex) {
                System.out.println(
                        "slash name: ClassNotFoundException");
            }
        }

        try {
            Class.forName("int");
            System.out.println("primitive name: found");
        } catch (ClassNotFoundException ex) {
            System.out.println(
                    "primitive name: ClassNotFoundException");
        }
    }
}
~~~

Tại sao không import `Greeting` vào host? Nếu host compile dựa vào class đó rồi đưa plugin output vào application classpath, thí nghiệm có thể không còn biểu diễn **explicit lookup từ host không thấy plugin**. Plugin được gọi qua reflection chỉ sau khi host tạo đúng loader.

### 5.4. Compile và chạy

Các lệnh **Bash trên Linux/macOS** (hoặc môi trường Bash tương thích) từ thư mục `project`:

~~~bash
mkdir -p out/plugin out/app

javac -d out/plugin \
    src/plugin/com/acme/plugin/Greeting.java

javac -d out/app src/app/ClassNotFoundLab.java

java -cp out/app ClassNotFoundLab out/plugin
~~~

Đối với Windows PowerShell, tạo thư mục bằng `New-Item -ItemType Directory -Force` và chạy các lệnh `javac`/`java` tương đương. Ở đây classpath chỉ có **một** mục `out/app`, nên không cần dấu phân cách platform-specific giữa nhiều entries.

### 5.5. Stdout chính xác của ví dụ

~~~text
default Class.forName: ClassNotFoundException
system loadClass: ClassNotFoundException
explicit loadClass: com.acme.plugin.Greeting
defined by plugin loader: true
same Class object: true
before initialization
plugin initialized
invoke: hello from plugin
missing name: ClassNotFoundException
slash name: ClassNotFoundException
primitive name: ClassNotFoundException
~~~

**Giải thích:**

1. Hai lời gọi đầu sử dụng loader của host/system application, không thấy `out/plugin`, nên CNFE.
2. `URLClassLoader` được cung cấp URL trỏ đến đúng root `out/plugin/`. Khi parent không tìm class, child tìm `com/acme/plugin/Greeting.class` từ directory.
3. `getClassLoader() == pluginLoader` là `true`, chứng tỏ child thực sự là defining loader của class plugin.
4. `loadClass` đã trả `Class<?>` nhưng **không khởi tạo** class, do đó dòng `plugin initialized` chưa xuất hiện trước `before initialization`.
5. `Class.forName(NAME, false, pluginLoader)` trả lại cùng runtime class; `true` không chứng minh class đã initialized.
6. `Class.forName(NAME, true, pluginLoader)` kích hoạt initialization và in đúng một dòng `plugin initialized`.
7. Method static `message` chạy và trả `hello from plugin`.
8. `com.acme.plugin.Missing` không tồn tại, còn chuỗi dùng dấu `/` sai binary name, nên đều CNFE.
9. `Class.forName("int")` không lấy primitive `int.class`, nên cũng CNFE.

**Giới hạn rõ ràng:** stdout này giả định không có bản `com.acme.plugin.Greeting` trong application classpath, không có class khác cùng tên do parent định nghĩa, Java runtime tương thích và hai source files được tạo đúng như ví dụ.

### 5.6. Nếu đóng plugin thành JAR

Có thể đóng gói từ class output:

~~~bash
mkdir -p dist
jar --create --file dist/plugin.jar -C out/plugin .

jar --list --file dist/plugin.jar
~~~

JAR phải chứa `com/acme/plugin/Greeting.class`. Thử:

~~~bash
java -cp out/app ClassNotFoundLab dist/plugin.jar
~~~

Do host chuyển đối số thành `file:` URL qua `Path.toUri().toURL()`, URLClassLoader hỗ trợ **cả directory URL và JAR file URL**. Lệnh Usage cho phép thư mục hoặc JAR; thí nghiệm dùng cùng code với plugin JAR; khi class tồn tại trong JAR và không bị parent che, stdout vẫn giống.

**Lưu ý:** plugin JAR không cần `Main-Class` nếu chỉ nạp class bằng URLClassLoader; `Main-Class` dành cho executable JAR chạy bằng `java -jar`.

## 6. Phân biệt ClassNotFoundException và NoClassDefFoundError

Cả hai có thể xuất hiện khi một class bị thiếu, nhưng báo **hai tầng lỗi khác nhau**:

| | `ClassNotFoundException` | `NoClassDefFoundError` |
| --- | --- | --- |
| Nhánh Throwable | Checked `ReflectiveOperationException` | `LinkageError` → `Error` |
| Kích hoạt thường gặp | `Class.forName(name)`, `loader.loadClass(name)` yêu cầu tra theo tên | JVM resolve class mà bytecode đã tham chiếu hoặc class đã khởi tạo hỏng |
| Thông tin cần lấy | Chuỗi binary name, API overload và loader được chọn | Cause chain, class còn thiếu, lần thất bại đầu, linker và loader |

Ví dụ `Class.forName("plugin.Missing")` từ host có thể ném **CNFE**, trong khi bytecode đã compile để gọi `MissingDependency.run()` với dependency bị xóa ở runtime có thể ném **NCDFE**, đôi khi CNFE xuất hiện trong cause chain. Hai ví dụ này chỉ minh họa bối cảnh; bytecode gọi `MissingDependency` chỉ biên dịch nếu class thực sự tồn tại lúc compile.

**Với initialization failure**, lần active use đầu có thể ném `ExceptionInInitializerError`; những lần sau trong cùng defining loader có thể ném `NoClassDefFoundError: Could not initialize class ...`. Khi gặp dấu hiệu ấy phải tìm `<clinit>` và cause ban đầu, không chỉ thêm JAR.

Bài **NoClassDefFoundError do thiếu lớp phụ thuộc hoặc sai tên** có hai lab độc lập tái hiện lỗi thiếu dependency và class bytecode mang sai binary name. Bài **NoClassDefFoundError sau khi khởi tạo lớp thất bại** trình bày riêng erroneous state và cách phục hồi bằng loader mới. Trong bài CNFE này, câu hỏi trước tiên vẫn là **ai gọi lookup, tên nào, và loader nào thực sự tìm**.

## 7. Vì sao loader đúng về vật lý nhưng sai về phạm vi?

### 7.1. Parent không nhìn thấy child

~~~text
ApplicationLoader
      |
      +-- PluginLoader A -> plugin-a.jar
      |
      +-- PluginLoader B -> plugin-b.jar
~~~

Parent không có nghĩa vụ tìm class **ở các child loaders**. Plugin loader A cũng không tự thấy class riêng của plugin loader B khi chỉ có chung parent. Vì thế:

~~~java
Class.forName("plugin.b.Feature");
~~~

khi được gọi từ host có thể CNFE dù class nằm trong `plugin-b.jar` mà PluginLoader B thấy. Cần gọi `Class.forName(name, initialize, loaderB)` hoặc `loaderB.loadClass(name)` một cách có chủ đích.

**Không ép mọi plugin JAR vào global application classpath chỉ để xử lý CNFE**, vì điều đó có thể phá version isolation và loader lifecycle của hệ plugin.

### 7.2. Thread context class loader (TCCL)

Một số framework và `ServiceLoader.load(Service.class)` chọn **thread context class loader** để tìm provider. Nhưng:

~~~java
Class.forName("plugin.Feature");
~~~

không mặc định chuyển sang TCCL chỉ vì thread đã gán TCCL khác.

Nếu cần, làm tường minh:

~~~java
ClassLoader tccl =
        Thread.currentThread().getContextClassLoader();

Class<?> type = Class.forName("plugin.Feature", false, tccl);
~~~

Đây là mảnh minh họa; nếu `tccl == null`, overload này sử dụng bootstrap loader. Trong plugin runtime, cân nhắc xác định loader từ framework contract thay vì phụ thuộc ngầm vào trạng thái thread.

Khi thư viện yêu cầu TCCL, đặt có thời hạn và **restore trong `finally`**:

~~~java
Thread thread = Thread.currentThread();
ClassLoader old = thread.getContextClassLoader();
try {
    thread.setContextClassLoader(pluginLoader);
    // Gọi thư viện cần TCCL.
} finally {
    thread.setContextClassLoader(old);
}
~~~

Không để TCCL của thread pool trỏ tới plugin loader đã ngừng hoạt động: nó vừa gây sai discovery cho request sau, vừa có thể giữ loader cũ không unload được.

### 7.3. Parent-first có thể nạp bản khác với mong đợi

Giả sử parent có `lib.Parser` phiên bản v1, plugin JAR chứa v2. Lời gọi `pluginLoader.loadClass("lib.Parser")` với URLClassLoader mặc định có thể trả `Class<?>` v1 do parent định nghĩa, **không ném CNFE**. Nhưng khi plugin gọi method chỉ có ở v2, JVM có thể ném `NoSuchMethodError`.

Đây là tình huống **lookup thành công nhưng runtime behavior/linkage sai**. Đừng kết luận chỉ vì không thấy CNFE thì classpath và dependency versions đúng.

## 8. Classpath, executable JAR và module path

### 8.1. Classpath mode

Trên Linux/macOS:

~~~bash
java -cp "out/app:libs/plugin.jar" com.example.Main
~~~

Trên Windows:

~~~powershell
java -cp "out/app;libs/plugin.jar" com.example.Main
~~~

Các ví dụ cú pháp minh họa giả định `com.example.Main` tồn tại. Dấu phân cách classpath là `:` trên Unix-like, `;` trên Windows.

Classpath root có thể là thư mục hoặc JAR. Nếu binary name là `com.acme.Feature`, đường dẫn bên dưới root phải là `com/acme/Feature.class`.

Classpath CLI wildcard:

~~~bash
java -cp "out/app:libs/*" com.example.Main
~~~

Nó có thể thêm các file JAR trong thư mục `libs` theo quy tắc launcher; wildcard **không tìm đệ quy toàn cây thư mục**, và không tự bảo đảm version order. Không dùng wildcard nếu đang điều tra hai JAR có cùng class và cần biết chính xác bản nào được chọn.

### 8.2. Chế độ `java -jar`

~~~bash
java -cp "libs/*" -jar app.jar
~~~

**Không** có nghĩa `libs/*` được thêm vào user classpath của ứng dụng như ở classpath mode: với `-jar`, những thiết lập classpath thông thường như `-cp` và `CLASSPATH` bị bỏ qua trong việc chọn đường tìm class của ứng dụng. Các dependency có thể được khai báo qua `Class-Path` của manifest hoặc được đóng gói bằng cơ chế thích hợp.

Manifest ví dụ:

~~~text
Manifest-Version: 1.0
Main-Class: com.example.Main
Class-Path: libs/plugin.jar

~~~

Các entry `Class-Path` manifest được phân cách bằng **khoảng trắng** và resolve **tương đối vị trí JAR chứa manifest**, không theo dấu phân cách classpath của OS. Không viết `Class-Path: libs/*` rồi trông đợi wildcard như `-cp "libs/*"`.

### 8.3. JPMS named modules

Java 9+ có named modules được resolve qua **module path**, cùng các quan hệ `requires`, `exports`, `opens` và module layers.

**Phân biệt:**
- Thiếu module hoặc module chưa được resolved có thể khiến named-module class không thuộc phạm vi loader/module lookup mà caller yêu cầu.
- Package không `exports` là **vấn đề access từ module khác**, không tự đồng nghĩa class file không tồn tại và không phải mọi trường hợp đều báo CNFE. Có thể gặp `IllegalAccessError`/`IllegalAccessException` ở bước truy cập tùy tình huống.
- `opens` chủ yếu liên quan deep reflection/resource access; **không phải phương thuốc mặc định cho mọi CNFE**.
- `Class.forName(Module module, String name)` là overload đặc biệt: tìm class trong module chỉ định và **trả `null` khi không tìm thấy trong module đó** theo hợp đồng API; không ném CNFE chỉ vì lookup không tìm thấy. Nó không yêu cầu initialize class.
- `ClassLoader.loadClass(Module module, String name)` cũng có hợp đồng khác với `loadClass(String)` và có thể **trả `null` nếu class không tìm được trong module đã chỉ định**.

Do đó, **xác định overload API thực sự đang gọi** trước khi khẳng định một lookup không thành công luôn ném `ClassNotFoundException`.

## 9. Quy trình chẩn đoán CNFE theo từng lớp bằng chứng

### Bước 1 — Ghi đúng yêu cầu

Lấy **giá trị binary name** thực sự truyền vào API (sau khi trim/config parsing khi cần), API overload nào, thread, module của caller và stack trace/cause chain.

Một config có thể vô tình chứa khoảng trắng đầu/cuối:

~~~text
plugin.class = com.acme.plugin.Greeting
~~~

Nếu parser không chuẩn hóa hoặc đọc sai key, string truyền cho `Class.forName` có thể khác chuỗi bạn nhìn thấy bằng mắt.

### Bước 2 — In loader được dùng

~~~java
ClassLoader callerLoader =
        ClassNotFoundLab.class.getClassLoader();

ClassLoader tccl =
        Thread.currentThread().getContextClassLoader();

ClassLoader system =
        ClassLoader.getSystemClassLoader();

System.out.println(callerLoader);
System.out.println(tccl);
System.out.println(system);
~~~

Đây là đoạn minh họa khi `ClassNotFoundLab` có trong scope; không có bảo đảm ba loader bằng nhau. Trên nhiều ứng dụng đơn giản, chúng có thể trùng, nhưng plugin/container frameworks thường cấu hình khác.

**Nếu class chưa tìm được thì bạn chưa có `Class<?>` của chính nó để gọi `getClassLoader()`.** Hãy bắt đầu từ loader đang thực hiện lookup và search roots của loader đó.

### Bước 3 — Kiểm tra artifact có chứa class thật không

Với JAR:

~~~bash
jar --list --file libs/plugin.jar
~~~

Tìm đúng entry:

~~~text
com/acme/plugin/Greeting.class
~~~

Nếu chỉ thấy `com/acme/plugin/Greeting.java` thì JAR chứa source, **không nhất thiết chứa class file** để JVM nạp như kỳ vọng.

Với thư mục, kiểm tra root và package subdirectories. Đường dẫn `out/plugin/com/acme/plugin/Greeting.class` thì URL/classpath root là `out/plugin`, không phải `out/plugin/com/acme/plugin`.

### Bước 4 — Kiểm tra compile-time và runtime cấu hình khác nhau không

Một dự án compile được nhờ dependency nằm trong classpath của IDE/Maven/Gradle, nhưng lúc runtime dùng một JAR/launch script không mang dependency ấy.

Xác định:

- Lệnh `java` thực tế chạy (classpath mode hay `-jar`).
- Environment/module path của tiến trình runtime, không chỉ IDE.
- Artifact version/path và thư mục triển khai.
- Scope của dependency (compile-only/test-only vs runtime).
- Nếu là plugin framework, URL classloader của plugin có được khởi tạo đúng và còn sống không.

**Compile thành công không chứng minh runtime loader sẽ tìm được mọi class.**

### Bước 5 — Kiểm tra binary name thay vì path

~~~text
Tên Java cần truyền: com.acme.plugin.Greeting
Tệp JAR entry:       com/acme/plugin/Greeting.class
~~~

Với nested class, dùng `Outer$Inner`. Với primitive, dùng `int.class` chứ không dùng `Class.forName("int")`.

### Bước 6 — Thử explicit loader có kiểm soát

Nếu đang chẩn đoán một plugin đáng tin và biết vị trí artifact:

~~~java
URL url = Path.of("plugins", "plugin.jar")
        .toAbsolutePath().toUri().toURL();

try (URLClassLoader loader = new URLClassLoader(
        new URL[] {url},
        ClassLoader.getSystemClassLoader())) {
    Class<?> type = loader.loadClass("com.acme.plugin.Greeting");
    System.out.println(type.getName());
}
~~~

Đây là **mảnh minh họa**, cần import `URL`, `URLClassLoader`, `Path` và xử lý exceptions; plugin JAR thực sự phải tồn tại. Nếu tìm được với URL loader tường minh nhưng không tìm được bằng host loader, vấn đề nằm ở **visibility/loader choice** thay vì binary name không tồn tại.

**Không sửa global classpath của cả ứng dụng vội** khi plugin framework đã có loader riêng; làm vậy có thể tạo class version conflict hoặc duplicate runtime interfaces.

### Bước 7 — Xác định vấn đề sau khi tìm thấy

Nếu class đã nạp:

~~~java
Class<?> type = loader.loadClass("com.acme.plugin.Greeting");

System.out.println(type.getName());
System.out.println(type.getClassLoader());
System.out.println(type.getModule());
~~~

Nếu có CodeSource:

~~~java
var pd = type.getProtectionDomain();
var cs = pd == null ? null : pd.getCodeSource();

System.out.println(cs == null ? "source unavailable"
        : cs.getLocation());
~~~

Các đoạn trên chỉ là **mảnh chẩn đoán** với biến loader phù hợp. CodeSource có thể `null`; không phải chứng chỉ tuyệt đối của nguồn.

### Bước 8 — JVM class loading logs

Trên HotSpot/JDK 17+ có thể bật logging:

~~~bash
java -Xlog:class+load=info \
     -cp out/app ClassNotFoundLab out/plugin
~~~

Đây là **lệnh chẩn đoán** tạo thêm các dòng log tùy runtime; không thuộc stdout chuẩn ở phần ví dụ. Nó hữu ích để xem nguồn của những class **đã được load**, nhưng không phải tất cả class không tìm thấy đều có entry chi tiết ở mức `info`. Đừng ngộ nhận “không thấy log class X” là kết luận duy nhất về nguyên nhân thất bại.

## 10. Những lỗi thường gặp và cách khắc phục

| Triệu chứng | Khả năng thường gặp | Khắc phục đúng hướng |
|---|---|---|
| `Class.forName("pkg.X")` CNFE, JAR chứa `pkg/X.class` | JAR không nằm trong loader search roots | Cấu hình classpath/URL/module cho **loader đúng** |
| `Class.forName` từ host không thấy class plugin | Host loader không nhìn xuống child | Dùng explicit plugin loader hoặc API plugin framework |
| Có `.class` nhưng path dưới root sai | Chỉ định root tới sai thư mục | Đưa root lên đúng cấp package |
| `Class.forName("a/b/C.class")` | Dùng file path thay binary name | Đổi thành `a.b.C` |
| `Class.forName("a.Outer.Inner")` | Nhầm nested class binary name | Dùng `a.Outer$Inner` |
| `Class.forName("int")` | Primitive name không hợp lệ cho API | Dùng `int.class` |
| Chạy từ IDE thành công, `java -jar` CNFE | Runtime thiếu dependency/manifest reference | Kiểm tra artifact cuối, manifest và deployment |
| Plugin A thấy class, plugin B không | Loader graph/ownership khác | Chọn loader đúng và thiết kế SPI/API chung |
| CNFE trong `ServiceLoader`/framework wrapper | Provider config hoặc TCCL sai | Kiểm tra descriptor, provider type, TCCL và cause chain |
| `NoClassDefFoundError` ngoài cùng | JVM cần dependency khi link/init | Đọc cause, kiểm tra missing binary/module/init |
| `NoSuchMethodError` sau khi class tải được | Dependency version ở parent khác compile time | So runtime defining loader và bytecode signatures |
| `IllegalAccessError` / reflection error | Class tồn tại nhưng access/module khác | Kiểm tra package ownership, `exports`/`opens` phù hợp |
| `ClassFormatError` / `UnsupportedClassVersionError` | Bytes không hợp lệ hoặc JDK runtime quá cũ | Compile `--release` phù hợp, xác minh bytes/Java version |

### Vì sao “thêm dependency” đôi khi làm lỗi nặng hơn?

Ví dụ host cần API `api.Plugin` do parent định nghĩa, plugin con cần `plugin.Impl`. Nếu để sửa CNFE bạn copy luôn `api.Plugin` vào một child-first JAR, plugin có thể tự định nghĩa **bản API thứ hai**. Khi trả object về host, cast sẽ fail mặc dù tên interface giống nhau.

Đúng hơn là giữ **shared API ở parent**, implementation ở plugin child. Có những trường hợp lỗi lookup là tín hiệu bạn đang dùng nhầm class loader boundary; giải pháp tốt không phải “đưa mọi JAR vào mọi nơi”.

## 11. Thiết kế lỗi và xử lý ngoại lệ đúng mức

### Thiếu một plugin tùy chọn

Nếu phần mở rộng có tính tùy chọn, có thể log/warn và tiếp tục với provider mặc định:

~~~java
try {
    Class<?> provider = pluginLoader.loadClass(configuredProvider);
    // Kiểm tra interface và tạo instance theo contract.
} catch (ClassNotFoundException ex) {
    System.err.println("Optional provider unavailable: "
            + configuredProvider);
    // Chọn fallback theo chính sách đã thiết kế.
}
~~~

Đây là mảnh minh họa; `pluginLoader`/`configuredProvider` phải tồn tại. Khi triển khai production, log exception/cause và plugin ID đủ để tra cứu, nhưng không log dữ liệu cấu hình nhạy cảm.

### Thiếu một dependency bắt buộc

Với một ứng dụng không thể hoạt động nếu thiếu class cấu hình, nên **fail fast** và báo lỗi có bối cảnh (binary name, artifact/version, loader identity, runtime command). Không catch rồi bỏ qua để tạo lỗi liên kết khó hiểu ở bước sau.

### Không catch mọi `Throwable`

Đoạn như:

~~~java
try {
    Class.forName(name);
} catch (Throwable ignored) {
    // nguy hiểm: che lỗi linkage, OutOfMemoryError, initializer...
}
~~~

là **anti-pattern minh họa**, không phải cách sửa. `ClassNotFoundException` là một loại lỗi cụ thể; `NoClassDefFoundError`, `ExceptionInInitializerError`, `VerifyError` và `OutOfMemoryError` không nên bị coi như cùng một “không có plugin”.

Một hệ plugin có policy cô lập lỗi riêng có thể bắt một số `LinkageError` có chủ đích tại boundary, nhưng phải bảo toàn cause, phân loại fatal/nonfatal và quyết định lifecycle đúng; không gộp tất cả vào CNFE.

### `ClassNotFoundException` và cause chain

Một custom `findClass` có thể ném:

~~~java
throw new ClassNotFoundException(binaryName, ioException);
~~~

Đây là mảnh minh họa với biến `binaryName` và `ioException` có sẵn. Vì loader tìm bytes từ đĩa/JAR/nguồn từ xa có thể thất bại do I/O, cause chain mang thông tin hữu ích hơn một tên class đơn lẻ.

Nếu cause là permission denied, timeout hoặc resource corruption, sửa binary name hoặc thêm classpath không đủ. **Đọc `getCause()`**, xác định source URL/path và loader; không ném một CNFE trống rồi mất thông tin ban đầu.

## 12. Bảng quyết định nhanh

| Bằng chứng quan sát | Nghi ngờ đầu tiên | Bước tiếp theo |
|---|---|---|
| `Class.forName(name)` thất bại, `pluginLoader.loadClass(name)` thành công | Sai loader ở caller | Chọn explicit plugin loader/TCCL phù hợp |
| JAR không chứa entry `p/C.class` | Artifact chưa đóng gói class | Sửa build/package |
| JAR chứa class nhưng URL loader không tìm được | URL root/JAR không đúng, name sai hoặc delegation khác | Log `loader.getURLs()`, binary name, đường dẫn |
| `Class.forName(name,true,loader)` báo initializer error | Class đã được tìm nhưng init fail | Điều tra `static` code/cause |
| CNFE nằm trong cause của NCDFE | JVM cần một class khi link/runtime | Khắc phục dependency runtime, không đổi loại lỗi |
| Class nạp được nhưng `ClassCastException` | API class identity khác loader | Kiểm tra defining loader của cả hai kiểu |
| Class nạp được nhưng `NoSuchMethodError` | ABI/version sai | So compiler classpath với runtime defining loader |
| `java -jar` lỗi nhưng `-cp` mode chạy | Runtime JAR manifest/dependencies khác | Kiểm tra `Main-Class`/`Class-Path` và layout |
| Named-module lookup trả `null` | Dùng overload `Class.forName(Module, String)` | Kiểm tra module object, resolution/layer, tên |
| Chỉ lỗi trên một thread | TCCL hoặc framework loader khác | In thread name/TCCL, restore context sau use |

## 13. Checklist trước khi đóng ticket CNFE

- [ ] Có **binary name chính xác** được yêu cầu, không chỉ file path hoặc tên ngắn.
- [ ] Biết **API overload** nào đã được gọi và nó chọn loader theo cách nào.
- [ ] Phân biệt caller loader, system loader, TCCL và plugin loader.
- [ ] Biết class nằm ở JAR/thư mục nào và artifact runtime **thực sự** có file `.class`.
- [ ] Cấu trúc package path khớp binary name, kể cả nested class `$`.
- [ ] Đường dẫn classpath hoặc URL root trỏ đúng cấp thư mục/JAR.
- [ ] Chế độ khởi chạy là `-cp`, `-jar` hay JPMS; không áp dụng nhầm quy tắc.
- [ ] Nếu dùng `java -jar`, manifest `Class-Path`/deployment dependency đã đúng.
- [ ] Nếu dùng JPMS, module thực sự được resolved và package accessibility được xem riêng.
- [ ] Không nhầm checked CNFE với `NoClassDefFoundError`/linkage/initializer error.
- [ ] Kiểm tra runtime **dependency version** và parent delegation nếu class đã nạp nhưng hành vi sai.
- [ ] Không để workaround thêm JAR tạo duplicate shared API ở child loaders.
- [ ] Nếu class được nạp, kiểm tra defining loader và CodeSource khi có.
- [ ] Có test chạy từ **artifact thật** thay vì chỉ IDE.
- [ ] Log lỗi có ngữ cảnh, không làm lộ secrets và không catch mọi Throwable.
- [ ] Lựa chọn fail-fast hoặc fallback cho plugin dựa trên yêu cầu ứng dụng.

## 14. Tự kiểm tra

**Câu 1.** `out/plugin/com/acme/Foo.class` tồn tại. Ứng dụng chỉ chạy `java -cp out/app Main` rồi gọi `Class.forName("com.acme.Foo")`. Vì sao CNFE?

**Đáp án:** Application loader chỉ có `out/app`; đường tìm kiếm không bao gồm `out/plugin`. Dù file có trên đĩa, loader caller không thấy nó. Cần cấu hình classpath đúng hoặc chỉ định loader có URL tới `out/plugin`.

**Câu 2.** `Class.forName("com/acme/Foo.class")` có đúng không?

**Đáp án:** Không. API cần binary name `com.acme.Foo`; chuỗi có `/` và `.class` là path/filename.

**Câu 3.** Tại sao `Class.forName(name)` từ host thất bại còn `Class.forName(name, false, pluginLoader)` thành công?

**Đáp án:** Overload đầu sử dụng loader của caller; overload thứ hai dùng plugin loader được truyền tường minh. Parent thường không tìm trong child loader.

**Câu 4.** Một class có initializer lỗi. `Class.forName(name, true, loader)` có chắc ném CNFE không?

**Đáp án:** Không. Lookup có thể thành công nhưng class initialization thất bại với `ExceptionInInitializerError` hoặc lỗi liên quan, không phải CNFE.

**Câu 5.** `ClassNotFoundException` khác `NoClassDefFoundError` ở đâu?

**Đáp án:** CNFE là checked exception của explicit lookup thất bại; NCDFE là `LinkageError` khi JVM cần một class definition/initialization mà không thành công, có thể chứa CNFE trong cause chain.

**Câu 6.** `Class.forName("int")` có lấy primitive class object không?

**Đáp án:** Không; dùng `int.class`. `Class.forName("int")` ném CNFE.

**Câu 7.** Một nested class khai báo `Outer.Inner` trong source có binary name gì?

**Đáp án:** `Outer$Inner` (có package prefix nếu có), không phải `Outer.Inner`.

**Câu 8.** `Class.forName(module, "p.C")` không tìm thấy class. Nó phải ném CNFE chứ?

**Đáp án:** Không. Overload có `Module` trả `null` nếu không tìm thấy theo hợp đồng API; xác minh overload trước khi phân loại lỗi.

**Câu 9.** Class tìm được bằng URLClassLoader nhưng cast sang SPI của host thất bại. Có còn là CNFE không?

**Đáp án:** Không. Vấn đề có thể là **hai runtime SPI types** do different defining loaders, dẫn đến `ClassCastException` hoặc lỗi khác. Cần chia sẻ SPI ở parent.

**Câu 10.** Tại sao không nên sửa lỗi bằng cách `catch (Throwable ignored)`?

**Đáp án:** Nó che cả linkage errors, initializer errors và những lỗi nghiêm trọng; rất khó chẩn đoán và có thể tiếp tục trong trạng thái không hợp lệ.

## 15. Quy trình ghi nhớ

~~~text
ClassNotFoundException xuất hiện
            |
            v
Explicit API nào? name nào?
            |
            v
Class.forName(String) / loadClass(String)
hay overload có Module/loader?
            |
            v
Chọn loader thực tế (caller/TCCL/explicit)
            |
            v
Class đã nằm trong nguồn loader nhìn thấy?
       /                        \
     Không                       Có
       |                          |
Sửa runtime classpath/URL     Kiểm tra binary name,
module resolution            parent delegation,
hoặc loader selection         JAR contents, root
       |                          |
       +------------+-------------+
                    |
                    v
             Nếu đã tìm được:
   phân biệt init/linkage/access/type identity
                    |
                    v
       Test artifact thực + xác nhận cause
~~~

**Kết luận:** `ClassNotFoundException` là dấu hiệu một **yêu cầu tìm class tường minh không hoàn tất với tên và loader được chọn**. Cách sửa bền vững không phải cứ chép thêm `.class` hoặc bắt mọi exception, mà phải xác định **lookup API**, **binary name**, **loader graph**, **runtime artifact/search roots** và **giai đoạn lỗi**. Một khi đã tìm được class, hãy tiếp tục kiểm tra initialization, binary compatibility và runtime type identity trước khi coi việc khắc phục hoàn tất.

### Tài liệu chính thức

- [Java SE 17 — ClassNotFoundException API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassNotFoundException.html)
- [Java SE 17 — Class.forName và Class.getClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [Java SE 17 — ClassLoader.loadClass, delegation và resource lookup](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17 — NoClassDefFoundError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoClassDefFoundError.html)
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html)
- [Java SE 17 — java launcher/classpath/`-jar`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html)
- [JVMS 17 `5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-class-loading-errors-002">NoClassDefFoundError do thiếu lớp phụ thuộc hoặc sai tên</a>

<details>
<summary>Xem chi tiết</summary>

# NoClassDefFoundError do thiếu lớp phụ thuộc hoặc sai tên

## 1. NoClassDefFoundError là gì?

`java.lang.NoClassDefFoundError` là một `LinkageError` xảy ra khi JVM hoặc ClassLoader cần định nghĩa, liên kết hay sử dụng một class nhưng **không thể tìm được định nghĩa cần thiết trong bối cảnh runtime**, mặc dù chương trình hoặc bytecode đã có sự phụ thuộc vào class đó. Thông điệp có thể chỉ tên class bị thiếu, tên không khớp hoặc cho biết class đã **khởi tạo thất bại trước đó**.

Trong thực hành Java ClassLoader, hai tình huống rất cần nhận diện là:

1. **Thiếu lớp phụ thuộc lúc chạy:** Class A có trên classpath, nhưng khi thực thi A, JVM cần class B và B không có trong đường tìm của loader thực sự dùng để resolve phụ thuộc.
2. **Sai tên nhị phân của class:** Loader được yêu cầu định nghĩa tên `demo.fake.Thing` từ một file `demo/fake/Thing.class`, nhưng nội dung bytecode lại khai báo `demo.real.Thing`; JVM từ chối định nghĩa với tên không khớp.

Thông điệp điển hình trên OpenJDK/HotSpot 17:

~~~text
java.lang.NoClassDefFoundError: demo/dep/Helper
~~~

hoặc:

~~~text
java.lang.NoClassDefFoundError: demo/fake/Thing (wrong name: demo/real/Thing)
~~~

Đây là **ví dụ định dạng thông điệp**, không phải hợp đồng bắt buộc mọi JDK/JVM phải dùng đúng dấu câu và nội dung. Muốn chẩn đoán chính xác, đọc **toàn bộ stack trace**, nguyên nhân lồng nhau (`Caused by`) và cấu hình ClassLoader thực tế.

### Hai thông tin không được đánh đồng

- “Có file `X.class` trong một JAR” **không chứng minh** loader đang resolve phụ thuộc có thể nhìn thấy file đó.
- “Tên file trên đĩa có vẻ đúng” **không chứng minh** binary name được biên dịch **bên trong bytecode** cũng đúng.

## 2. Phân biệt NoClassDefFoundError với ClassNotFoundException

| Khía cạnh | `ClassNotFoundException` | `NoClassDefFoundError` |
|---|---|---|
| Nhóm lỗi | Checked `Exception` | `Error`, subclass của `LinkageError` |
| Trường hợp thường gặp | Code gọi `Class.forName("pkg.Type")` hoặc `loader.loadClass("pkg.Type")` nhưng không tìm thấy | JVM cần class phụ thuộc đã được bytecode tham chiếu, nhưng không thể định nghĩa/liên kết class đó |
| Cách xử lý thông thường | Có thể bắt và xử lý như một lookup thất bại được dự kiến | Thường là lỗi triển khai/cấu hình nghiêm trọng; phải sửa runtime dependency hoặc binary mismatch |
| Có thể liên quan class loader? | Có | Có |
| Có thể xuất hiện cùng nhau trong causal chain? | Có | Có |

Một ví dụ minh họa:

~~~java
// Minh họa, không phải chương trình độc lập.
try {
    Class.forName("demo.dep.Helper");
} catch (ClassNotFoundException notFound) {
    System.out.println("Explicit lookup did not find Helper");
}
~~~

Đây là tra cứu **tường minh** theo tên; nếu không tìm thấy, API `Class.forName` thường báo `ClassNotFoundException`.

Còn đoạn sau được biên dịch khi `Helper` có sẵn:

~~~java
// Minh họa: Feature là class có bytecode gọi Helper.
System.out.println(Feature.message());
~~~

Nếu khi chạy `Helper.class` không còn được loader tìm thấy, JVM có thể ném `NoClassDefFoundError` khi thực thi lệnh cần resolve `Helper`. Không có mâu thuẫn: **đường code và giai đoạn lỗi khác nhau**.

`ClassNotFoundException` cũng có thể là **cause** bên trong một `NoClassDefFoundError`, tùy cơ chế loader/JVM, nhưng đừng mặc định luôn có hoặc luôn không có cause đó.

## 3. Vì sao compile thành công mà chạy lại lỗi?

Thực tế thường có ít nhất ba cấu hình khác nhau:

~~~text
Compile-time classpath
  ├── lib.jar
  └── dep.jar       ← javac nhìn thấy

Runtime classpath / loader graph
  └── lib.jar       ← dep.jar bị thiếu

Bytecode Feature.class
  └── symbolic reference tới demo/dep/Helper
                   |
             JVM resolve lúc cần
                   |
        loader không tìm được Helper
                   |
           NoClassDefFoundError
~~~

`javac` kiểm tra và ghi tham chiếu tới types/methods vào bytecode dựa trên các dependency lúc biên dịch. Nó **không đóng gói tự động** toàn bộ những JAR phụ thuộc vào output class/JAR ứng dụng.

Khi chạy, JVM dùng **runtime classpath/module path và delegation của ClassLoader tại thời điểm đó**. Một class có thể được load thành công trước, nhưng khi method hoặc field phụ thuộc được sử dụng lần đầu, quá trình resolution mới yêu cầu một class khác và thất bại.

Vì vậy:

- `Compilation successful` không có nghĩa `runtime dependencies complete`.
- Lỗi có thể xuất hiện **muộn**, chỉ khi chạm vào một code path.
- Đổi thứ tự JAR hoặc đưa đúng JAR vào nhầm loader không chắc sửa được.
- Nếu class đã được loader định nghĩa, thay file trên đĩa không làm JVM tự nạp lại version mới.

## 4. Ví dụ Java 17+ số 1: thiếu JAR phụ thuộc tại runtime

Ví dụ gồm ba tầng:

1. `demo.dep.Helper` nằm trong `dep.jar`.
2. `demo.lib.Feature` nằm trong `lib.jar` và gọi Helper.
3. `demo.host.App` là entry point trên application classpath.

Ta biên dịch đúng với dependency đầy đủ, sau đó cố ý bỏ `dep.jar` khỏi runtime `-cp` để tái hiện lỗi.

### 4.1. Cấu trúc thư mục

~~~text
missing-dependency-demo/
├── dep-src/
│   └── demo/dep/Helper.java
├── lib-src/
│   └── demo/lib/Feature.java
└── host-src/
    └── demo/host/App.java
~~~

### 4.2. File Helper.java

Tạo **`dep-src/demo/dep/Helper.java`**:

~~~java
package demo.dep;

public final class Helper {
    private Helper() {
    }

    public static String value() {
        return "DEP_OK";
    }
}
~~~

### 4.3. File Feature.java

Tạo **`lib-src/demo/lib/Feature.java`**:

~~~java
package demo.lib;

import demo.dep.Helper;

public final class Feature {
    private Feature() {
    }

    public static String message() {
        return Helper.value();
    }
}
~~~

Class `Feature` có dependency **trực tiếp trong method body** tới `demo.dep.Helper`. File `Feature.class` được biên dịch thành công nhưng khi `message()` chạy, JVM cần resolve class `Helper`.

### 4.4. File App.java

Tạo **`host-src/demo/host/App.java`**:

~~~java
package demo.host;

import demo.lib.Feature;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        System.out.println("Start");

        try {
            System.out.println("Feature: " + Feature.message());
        } catch (NoClassDefFoundError failure) {
            System.out.println("Failure type: "
                    + failure.getClass().getSimpleName());
            System.out.println("Missing name: "
                    + failure.getMessage());
        }
    }
}
~~~

**Vì sao bắt `NoClassDefFoundError` ở đây?** Chỉ để thí nghiệm in output ngắn và xác định. Code production thường **không nên bắt rồi tiếp tục vận hành** sau lỗi linkage nghiêm trọng nếu runtime dependency chưa được sửa. Trong ứng dụng thật, ghi stack trace/cause và dừng hoặc cô lập plugin bị lỗi.

### 4.5. Biên dịch và đóng JAR trên Bash

Mở terminal tại thư mục `missing-dependency-demo/`, dùng JDK 17+:

~~~bash
mkdir -p build/dep build/lib build/host dist

javac -encoding UTF-8 -d build/dep \
  dep-src/demo/dep/Helper.java

jar --create --file dist/dep.jar -C build/dep .

javac -encoding UTF-8 -cp dist/dep.jar \
  -d build/lib lib-src/demo/lib/Feature.java

jar --create --file dist/lib.jar -C build/lib .

javac -encoding UTF-8 \
  -cp "dist/lib.jar:dist/dep.jar" \
  -d build/host host-src/demo/host/App.java
~~~

### 4.6. Biên dịch và đóng JAR trên Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force build/dep,build/lib,build/host,dist | Out-Null

javac -encoding UTF-8 -d build/dep dep-src/demo/dep/Helper.java

jar --create --file dist/dep.jar -C build/dep .

javac -encoding UTF-8 -cp dist/dep.jar -d build/lib lib-src/demo/lib/Feature.java

jar --create --file dist/lib.jar -C build/lib .

javac -encoding UTF-8 -cp "dist/lib.jar;dist/dep.jar" -d build/host host-src/demo/host/App.java
~~~

### 4.7. Chạy với dependency đầy đủ

**Linux/macOS/Git Bash:**

~~~bash
java -cp "build/host:dist/lib.jar:dist/dep.jar" demo.host.App
~~~

**Windows PowerShell:**

~~~powershell
java -cp "build/host;dist/lib.jar;dist/dep.jar" demo.host.App
~~~

Stdout:

~~~text
Start
Feature: DEP_OK
~~~

### 4.8. Bỏ dep.jar khỏi runtime

**Linux/macOS/Git Bash:**

~~~bash
java -cp "build/host:dist/lib.jar" demo.host.App
~~~

**Windows PowerShell:**

~~~powershell
java -cp "build/host;dist/lib.jar" demo.host.App
~~~

Stdout trên JDK 17 tiêu chuẩn:

~~~text
Start
Failure type: NoClassDefFoundError
Missing name: demo/dep/Helper
~~~

**Lý giải theo thứ tự:**

1. JVM tìm và tải `App` từ `build/host`.
2. `App` gọi `Feature.message()`; `Feature` có trong `dist/lib.jar` nên được tìm thấy.
3. Bytecode của `Feature.message()` gọi `Helper.value()`, cần class `demo.dep.Helper`.
4. Loader dùng để resolve dependency đó không tìm thấy `Helper` vì `dep.jar` đã bị bỏ khỏi runtime classpath.
5. `NoClassDefFoundError` xuất hiện **dù `Feature.class` có trên classpath và ứng dụng đã biên dịch thành công**.

### 4.9. Cách sửa

Giữ nguyên code và thêm **đúng** `dep.jar` vào đường tìm runtime của loader:

~~~bash
java -cp "build/host:dist/lib.jar:dist/dep.jar" demo.host.App
~~~

Trên Windows dùng `;` thay `:`. Trong ứng dụng đóng gói bằng Maven/Gradle, cần kiểm tra *runtime dependency graph*, transitive dependencies, scope/configuration và classpath của đúng tiến trình/test runner, không chỉ compile dependencies.

## 5. Ví dụ Java 17+ số 2: class nằm ở sai đường dẫn so với binary name

Không phải mọi `NoClassDefFoundError` đều là “thiếu file”. JVM kiểm tra rằng **tên class được yêu cầu** nhất quán với **binary name được ghi trong class file**.

Ví dụ class được biên dịch là:

~~~java
package demo.real;

public final class Thing {
}
~~~

Bytecode có internal name `demo/real/Thing`. Nếu chỉ **copy hoặc đổi tên file** tới:

~~~text
build/wrong/demo/fake/Thing.class
~~~

rồi ClassLoader thử định nghĩa tên `demo.fake.Thing` từ file đó, JVM nhận thấy:

~~~text
Tên được yêu cầu: demo/fake/Thing
Tên trong bytecode: demo/real/Thing
Kết quả: NoClassDefFoundError (wrong name: ...)
~~~

Việc đổi tên thư mục/file không sửa được `package`/`this_class` trong bytecode. Đây là một lỗi phổ biến khi tự viết `findClass`, tự giải nén JAR, sao chép bytecode hoặc nhầm package.

### 5.1. Cấu trúc

~~~text
wrong-name-demo/
├── thing-src/
│   └── demo/real/Thing.java
└── host-src/
    └── WrongNameProbe.java
~~~

Tạo **`thing-src/demo/real/Thing.java`**:

~~~java
package demo.real;

public final class Thing {
    public Thing() {
    }
}
~~~

Tạo **`host-src/WrongNameProbe.java`**:

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WrongNameProbe {
    private WrongNameProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: WrongNameProbe <wrong-class-root>");
        }

        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Missing class root");
        }

        URL[] urls = {root.toUri().toURL()};
        try (URLClassLoader child = new URLClassLoader(
                urls, WrongNameProbe.class.getClassLoader())) {
            try {
                Class.forName("demo.fake.Thing", false, child);
                System.out.println("Wrong name detected: false");
            } catch (NoClassDefFoundError failure) {
                System.out.println("Error type: "
                        + failure.getClass().getSimpleName());
                System.out.println("Wrong name detected: "
                        + String.valueOf(failure.getMessage())
                                .contains("wrong name"));
            }
        }
    }
}
~~~

Để tập trung vào sai tên, host **không đặt Thing.class lên application classpath** và chỉ để `URLClassLoader` đọc thư mục `build/wrong`.

### 5.2. Bash: biên dịch rồi cố ý copy sai vị trí

~~~bash
mkdir -p build/real build/wrong/demo/fake build/host

javac -encoding UTF-8 -d build/real \
  thing-src/demo/real/Thing.java

javac -encoding UTF-8 -d build/host \
  host-src/WrongNameProbe.java

cp build/real/demo/real/Thing.class \
  build/wrong/demo/fake/Thing.class

java -cp build/host WrongNameProbe build/wrong
~~~

### 5.3. Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force build/real,build/wrong/demo/fake,build/host | Out-Null

javac -encoding UTF-8 -d build/real thing-src/demo/real/Thing.java

javac -encoding UTF-8 -d build/host host-src/WrongNameProbe.java

Copy-Item build/real/demo/real/Thing.class build/wrong/demo/fake/Thing.class

java -cp build/host WrongNameProbe build/wrong
~~~

Stdout trên OpenJDK/HotSpot 17:

~~~text
Error type: NoClassDefFoundError
Wrong name detected: true
~~~

`WrongNameProbe` in boolean thay vì toàn bộ `getMessage()`, vì format thông điệp lỗi có thể khác giữa các JVM. Trên HotSpot, phần thông điệp thường chứa `wrong name: demo/real/Thing`.

**Cách sửa đúng:** Giữ `Thing.class` tại `demo/real/Thing.class` và yêu cầu `demo.real.Thing`; hoặc thay `package demo.real;` thành `package demo.fake;` **trong source**, biên dịch lại và lưu output tại `demo/fake/Thing.class`. Không sửa chỉ bằng cách đổi tên file `.class`.

## 6. ClassNotFoundException khác với “wrong name”

Cùng `WrongNameProbe` ở trên, nếu child loader không tìm được file `demo/fake/Thing.class` ngay từ đầu, `Class.forName("demo.fake.Thing", false, child)` thường ném **`ClassNotFoundException`**.

Nhưng khi loader **tìm thấy file ở đường tìm hợp lệ**, rồi JVM phát hiện tên class trong bytecode không khớp tên yêu cầu, kết quả có thể là **`NoClassDefFoundError` với “wrong name”**.

~~~text
Không tìm thấy URL / bytes cho tên được yêu cầu
    → ClassNotFoundException (lookup failure)

Có bytes nhưng class file khai báo binary name khác
    → NoClassDefFoundError (wrong name)

Có bytes, đúng tên nhưng nội dung class file không hợp lệ
    → Có thể là ClassFormatError hoặc lỗi liên kết tương ứng
~~~

Một custom loader nên đọc bytes theo tên nhị phân chính xác rồi gọi `defineClass(name, bytes, 0, length)`. Nếu truyền bytes của class khác, JVM có thể phát hiện name mismatch.

Đối với module path, named module package ownership cũng ảnh hưởng class lookup; không suy từ việc thấy class file trong một modular JAR rằng bất kỳ loader/module nào cũng được phép dùng nó.

## 7. Các nguyên nhân khác thường gặp của NoClassDefFoundError

Mặc dù bài tập trung vào thiếu dependency và sai tên, cần nhận diện các biến thể để không chẩn đoán nhầm:

| Hiện tượng | Nguyên nhân khả dĩ | Hướng kiểm tra |
|---|---|---|
| `NoClassDefFoundError: pkg/Dependency` | Dependency thiếu trên runtime classpath/module path, hoặc không visible từ loader | Runtime dependency graph và loader topology |
| `NoClassDefFoundError: pkg/X (wrong name: pkg/Y)` | `.class` được đặt sai package path; custom loader định nghĩa sai tên | `package`, internal binary name, JAR entry |
| `NoClassDefFoundError: Could not initialize class ...` | Class đã từng khởi tạo thất bại, và lần dùng sau thấy class ở trạng thái erroneous | Tìm exception đầu tiên của static initialization |
| `NoClassDefFoundError` sau plugin reload | Old loader đã đóng hoặc dependency/version đã bị đổi; request vẫn dùng old generation | In-flight tasks, lifecycle/leases, `close()` |
| `NoClassDefFoundError` chỉ trên server/test | Compile/test classpath khác runtime thật; class có ở loader khác | Container-specific loaders, packaging, launcher |
| `NoClassDefFoundError` với tên class khác tên code đang gọi | Class trực tiếp có, nhưng dependency sâu hơn không có | Cause chain và class thực sự được resolve |

Đừng sửa bằng cách **bắt mọi `NoClassDefFoundError` rồi tiếp tục**. Nếu application thiếu runtime dependency, lỗi có thể tái xuất hiện trên code path khác. Nếu class khởi tạo hỏng, cơ chế JVM không “retry static initializer” chỉ vì code bắt lỗi.

## 8. Đừng nhầm thiếu class với class đã khởi tạo thất bại

Một class có `static` initializer ném exception có thể chuyển sang trạng thái **erroneous**. Lần active use đầu JVM thường ném `ExceptionInInitializerError` (nếu initializer ném non-`Error`); lần sau cần initialize **cùng runtime class** thường ném `NoClassDefFoundError: Could not initialize class ...`. Trong trường hợp này **bytecode và JAR vẫn có thể tồn tại nguyên vẹn**.

Dấu hiệu chẩn đoán quan trọng là thông điệp *Could not initialize class*, bản ghi `<clinit>`/first exception và quan hệ superclass initialization. Chỉ sửa file JAR hoặc `java.class.path` không reset class erroneous trong một loader đang sống.

Bài riêng **NoClassDefFoundError sau khi khởi tạo lớp thất bại** có lab Java 17+ tái hiện first failure → repeat failure, phân biệt `Class.forName(..., false, loader)` với initialization và thử loader mới sau khi sửa cấu hình. Bài hiện tại tập trung vào **dependency bị thiếu và file mang wrong binary name**, vốn có cách sửa khác.

## 9. Quy tắc binary name và đường dẫn class

Trong Java:

~~~java
package demo.real;

public class Thing {
}
~~~

Tên nhị phân là `demo.real.Thing`. Trong thư mục classpath/JAR thông thường, entry tương ứng:

~~~text
demo/real/Thing.class
~~~

Trong bytecode, constant pool và `this_class` dùng internal form với slash, ví dụ `demo/real/Thing`. Tên file đặt tại `demo/fake/Thing.class` không thay đổi metadata này.

Những kiểu sai tên hay gặp:

- Đổi thư mục hoặc file `.class` nhưng không biên dịch lại `package`.
- Loader dùng `name.replace('.', '/')` sai hoặc bỏ mất prefix.
- Class generator bytecode gắn `this_class` sai với tên yêu cầu.
- Đưa một class byte array tới `defineClass(otherName, bytes, ...)`.
- Nhầm tên nested class như `Outer$Inner` thành `Outer.Inner` trong một API yêu cầu binary name.
- Nhầm case-sensitive package/class name trên OS có cách xử lý filename khác nhau.

Một ví dụ nested class: source `demo.Outer.Inner` có thể có binary name `demo.Outer$Inner`, nên tên lookup bằng reflection thường là `demo.Outer$Inner`. Đây **không phải** lý do để sửa tên file bằng tay; dùng `javac`, đúng output root và `Class.getName()` để đối chiếu.

## 10. Chẩn đoán theo thứ tự: lỗi ở bước nào?

Khi gặp `NoClassDefFoundError`, làm theo trình tự:

1. **Lấy stack trace đầy đủ** của lần thất bại đầu tiên, bao gồm `Caused by` và suppressed exceptions nếu có.
2. **Đọc tên sau dấu hai chấm:** Là tên phụ thuộc như `demo/dep/Helper`, thông điệp `wrong name`, hay `Could not initialize class`?
3. **Xác định thời điểm:** Lỗi khi khởi chạy class chính, gọi một method, tạo plugin instance, mở ServiceLoader, hay sau reload?
4. **Kiểm tra runtime launcher:** `java -cp`, `--module-path`, script, container classpath, environment, dependency scopes.
5. **Kiểm tra nguồn thực tế:** JAR nào chứa entry chính xác, và loader nào được dùng để resolve class?
6. **Đối chiếu class identity:** `getClassLoader()` cho các class đã load; parent-first/child-first và module mapping.
7. **Nếu “wrong name”:** So tên yêu cầu, JAR entry/path và `this_class` bên trong bytecode.
8. **Nếu “Could not initialize class”:** Truy tìm static initialization failure đầu tiên.
9. **Khôi phục và kiểm chứng:** Chạy lại với runtime classpath hoặc package mapping đã sửa; không chỉ sửa compile classpath.

Không tự động lấy class hiện tại bằng `SomeClass.class.getClassLoader()` rồi kết luận mọi dependency của ứng dụng đều nằm trong loader đó: framework/plugin có thể tạo nhiều defining loaders, delegation khác nhau.

## 11. Các lệnh kiểm tra JAR và bytecode

Với ví dụ missing dependency:

~~~bash
jar --list --file dist/lib.jar
jar --list --file dist/dep.jar
~~~

`lib.jar` cần chứa `demo/lib/Feature.class`, còn `dep.jar` chứa `demo/dep/Helper.class`.

Dùng `javap` để đọc bytecode đã biên dịch:

~~~bash
javap -c -classpath dist/lib.jar demo.lib.Feature
~~~

Bạn có thể thấy lời gọi tham chiếu tới `demo/dep/Helper.value` trong disassembly. Lệnh `javap` này không chứng minh Helper có sẵn lúc chạy; nó chỉ cho thấy symbolic reference được ghi trong bytecode.

Đối với wrong name:

~~~bash
javap -verbose -classpath build/real demo.real.Thing
~~~

Trong output có metadata liên quan `this_class` và internal name `demo/real/Thing`. So sánh nó với entry bạn định nghĩa ở `build/wrong/demo/fake/Thing.class`.

Quan sát class loading trên Java 17:

~~~bash
java -Xlog:class+load=info \
  -cp "build/host:dist/lib.jar:dist/dep.jar" demo.host.App
~~~

Dòng log do JVM phát ra có thể nhiều, chứa đường dẫn tùy máy và không có output xác định như stdout của demo. Nó hữu ích khi cần biết một class **thực sự** được JVM nạp từ nguồn nào, nhưng cần kết hợp stack trace và classloader configuration.

Trên Windows, dấu phân cách `-cp` là `;`. Lệnh `javap -verbose` có output dài và phụ thuộc JDK nên không coi nó là một phần của stdout kỳ vọng.

## 12. NoClassDefFoundError trong hệ nhiều ClassLoader

Một plugin host có thể tạo child loader:

~~~java
// Mã minh họa, không phải chương trình độc lập.
URLClassLoader child =
        new URLClassLoader(pluginUrls, apiParentLoader);
Class<?> provider = child.loadClass("demo.plugin.PluginImpl");
~~~

`PluginImpl` được child định nghĩa nhưng phụ thuộc `demo.dep.Helper`. Classloader muốn resolve các tham chiếu đó phải có đường tìm phù hợp cho Helper; nếu Helper chỉ nằm trong **một sibling loader không được delegation tới**, nó có thể vẫn thất bại dù JVM đã nạp Helper ở loader khác.

### Những điều cần nhớ

- **Child nhìn parent** theo mô hình parent-first mặc định; **parent không tự duyệt class riêng của child**.
- **Sibling loaders** không tự động nhìn thấy nhau.
- Hai loaders định nghĩa cùng FQCN tạo runtime classes khác nhau; điều đó còn có thể gây `ClassCastException`/loader constraint `LinkageError`.
- Đưa JAR vào **classpath của một loader khác** không bảo đảm sửa lỗi ở loader đang resolve.
- Với `URLClassLoader.close()`, class đã load vẫn tồn tại nhưng nguồn tìm class mới của loader đã đóng không còn khả dụng.
- Với JPMS named modules, module readability, mapping loader và exports/opens là các ràng buộc bổ sung; không suy từ sự tồn tại class trong module JAR rằng mọi loader đều có thể tìm nó.

Một cách thiết kế ổn định: đặt shared API trong parent, đặt implementation và runtime dependencies của plugin vào đường tìm của plugin loader hoặc ở parent theo chính sách tương thích, quản lý generation lifecycle rõ ràng.

## 13. Lỗi thường gặp khi sửa

| Cách sửa sai | Vì sao chưa đủ/không đúng | Thay bằng |
|---|---|---|
| “File `Feature.class` có nên thiếu gì đâu?” | Feature phụ thuộc Helper ở bước runtime resolution | Kiểm tra toàn bộ dependency graph |
| Chỉ sửa compile classpath | `java` vẫn chạy với classpath khác | Sửa đúng runtime classpath/module path |
| Thêm dep.jar vào thư mục bất kỳ | Loader không tự quét mọi thư mục | Đưa dependency vào đúng loader URL/launcher |
| Đổi tên `Thing.class` hoặc directory bằng tay | Bytecode vẫn khai báo binary name cũ | Sửa `package` và biên dịch lại |
| Bắt `NoClassDefFoundError` rồi chạy tiếp | Lỗi linkage có thể tái diễn | Khắc phục packaging/configuration hoặc cô lập plugin |
| Chỉ xem exception cuối cùng | Initialization failure đầu tiên có thể bị che | Xem log từ lần lỗi đầu và `Caused by` |
| Đóng loader ngay sau plugin cutover | Request cũ có thể còn cần class/resource | Drain in-flight tasks rồi close |
| Đặt dependency ở sibling loader và trông chờ tự thấy | Không có delegation mặc định giữa siblings | Thiết kế parent/delegation đúng |
| Dựa vào thứ tự wildcard classpath | Thứ tự mở rộng `lib/*` không bảo đảm ổn định | Loại bỏ bản trùng và khóa dependencies |
| Dựa vào tên class đơn thuần | Runtime identity còn bao gồm defining loader | Kiểm tra `Class.getClassLoader()` |

## 14. Checklist phục hồi lỗi production

**Khi nhận lỗi:**

1. Chụp stack trace đầy đủ và version/runtime launch command.
2. Xác định tên class thất bại và từ khoá `wrong name` hoặc `Could not initialize class`.
3. Đối chiếu JAR packaging bằng `jar --list`, bytecode bằng `javap`.
4. Kiểm tra compile-time vs runtime dependency scopes.
5. Xác định defining loader của caller và loader graph thực tế.
6. Kiểm tra không có dependency bị exclude/shade sai hoặc transitive version xung đột.
7. Sửa đóng gói; build lại reproducibly, chạy thử đúng command production.
8. Nếu có plugin hot reload, kiểm tra old/new generation, request in-flight và thời điểm `close()`.
9. Nếu là initialization failure, sửa nguyên nhân static initializer thay vì chỉ thêm JAR.
10. Thêm integration test khởi chạy với **đúng runtime artifacts** để phát hiện lỗi trước phát hành.

Trong Maven/Gradle, nên sử dụng dependency-insight/dependency-tree và runtime classpath reporting để phát hiện dependency thiếu hoặc trùng version. Không ép mọi lỗi về “thiếu thư viện” nếu thông điệp đã chỉ ra name mismatch hoặc initialization failure.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** `App` và `Feature` có trên runtime classpath, `Helper` chỉ có lúc biên dịch. Lỗi nào dễ xảy ra khi gọi `Feature.message()`?

**Đáp án:** `NoClassDefFoundError` cho `demo/dep/Helper` khi JVM cần resolve dependency và không tìm được lớp.

**Câu 2.** `Class.forName("demo.dep.Helper")` trực tiếp không tìm thấy class thường ném lỗi nào?

**Đáp án:** `ClassNotFoundException` (checked exception), khác đường xảy ra lỗi resolution của bytecode.

**Câu 3.** Đổi file `demo/real/Thing.class` thành `demo/fake/Thing.class` có làm binary name đổi theo không?

**Đáp án:** Không. Tên nằm trong bytecode vẫn là `demo/real/Thing`; nếu loader cố định nghĩa tên `demo.fake.Thing` có thể xuất hiện `NoClassDefFoundError (wrong name)`.

**Câu 4.** Vì sao phải kiểm tra runtime classpath chứ không chỉ `javac -cp`?

**Đáp án:** Compile-time dependencies không tự xuất hiện ở runtime; JVM tải class theo đường tìm của runtime loader.

**Câu 5.** Lần đầu sử dụng class lỗi ở static initializer, lần sau báo `NoClassDefFoundError`. Có chắc thiếu JAR không?

**Đáp án:** Không. Class có thể đã bị đánh dấu initialization failed; phải truy lần exception đầu tiên.

**Câu 6.** `Helper` nằm trong sibling loader của plugin nhưng plugin không ủy quyền tới nó. Tìm được Helper khi in danh sách JAR toàn JVM đã đủ chưa?

**Đáp án:** Chưa. Loader dùng để resolve phụ thuộc phải có đường tìm thực sự tới class phù hợp.

**Câu 7.** Có nên bắt `NoClassDefFoundError` rồi tiếp tục để “tự khôi phục” production không?

**Đáp án:** Thường không. Nên sửa dependency/name mismatch hoặc tách/disable plugin lỗi với policy rõ, thay vì bỏ qua lỗi linkage.

## 16. Nguồn tham khảo chính thức

- [Java SE 17 — NoClassDefFoundError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoClassDefFoundError.html): ý nghĩa của lỗi khi JVM/ClassLoader cần định nghĩa class nhưng không tìm được định nghĩa phù hợp.
- [Java SE 17 — ClassNotFoundException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassNotFoundException.html): lỗi tra cứu class tường minh.
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): delegation, `loadClass`, `findClass` và `defineClass`.
- [Java SE 17 — ExceptionInInitializerError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ExceptionInInitializerError.html): lỗi static initialization.
- [Java Virtual Machine Specification 17, Chapter 5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): loading, linking, initialization và loading constraints.
- [Java SE 17 — Java launcher](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): runtime `--class-path`, module path và class loading logs.

**Ghi nhớ:** `NoClassDefFoundError` không chỉ có một nguyên nhân. Với lỗi **thiếu phụ thuộc**, so sánh runtime loader/classpath với bytecode đang cần class nào. Với **wrong name**, đối chiếu package, đường dẫn file và binary name bên trong `.class`, rồi biên dịch lại đúng. Luôn phân biệt với `ClassNotFoundException` của explicit lookup và `NoClassDefFoundError` phát sinh **sau một lỗi khởi tạo lớp**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-class-loading-errors-003">NoClassDefFoundError sau khi khởi tạo lớp thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# NoClassDefFoundError sau khi khởi tạo lớp thất bại

> **Java Core → ClassLoader → Lỗi thường gặp.** Một thông báo `NoClassDefFoundError: Could not initialize class X` thường không có nghĩa X.class bị xóa hay thiếu JAR. Class có thể **đã được nạp thành công**, nhưng quá trình chạy static initializer (`<clinit>`) thất bại. JVM đánh dấu đúng runtime class ấy ở trạng thái **erroneous**, và các lần cần khởi tạo tiếp theo sẽ báo `NoClassDefFoundError`. Bài này phân tích cơ chế, tái hiện bằng Java 17, xác định nguyên nhân ban đầu và giải pháp đúng cho application, test, server và plugin ClassLoader.

## 1. Cơ chế cốt lõi

Một lớp Java thường đi qua:

~~~text
Loading                Tìm bytecode / tạo runtime Class
   |
   v
Linking                Verify, prepare, resolve khi thích hợp
   |
   v
Initialization         Chạy static field initializer + static block
   |
   +--------------------------+
   |                          |
   v                          v
SUCCESS                   FAILURE
initialized               erroneous
                              |
                 first active use: EIIE hoặc Error gốc
                              |
                 later initialization attempt: NCDFE
~~~

`EIIE` là `ExceptionInInitializerError`, `NCDFE` là `NoClassDefFoundError`. Khi initializer của X ném một exception như `IllegalStateException` hoặc `NullPointerException`, JVM thường bọc nó trong `ExceptionInInitializerError` cho lần thất bại đầu tiên. Nếu initializer ném một `Error`, JVM có thể truyền chính Error đó chứ không bọc thêm EIIE.

Sau thất bại, JVM lưu trạng thái **erroneous** cho **runtime class X cụ thể**, tức class có binary name và defining ClassLoader nhất định. Một lần sử dụng tiếp theo **cần initialization** sẽ ném `NoClassDefFoundError` mà không chạy lại `<clinit>`. Sửa biến môi trường, file cấu hình hay `System.setProperty` sau sự kiện này không tự reset trạng thái của class.

Đây là quy tắc trong [JLS `12.4.2](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4.2), không chỉ là chi tiết của HotSpot.

### 1.1. Phân biệt ba lỗi dễ bị lẫn

| Lỗi | Tầng xảy ra | Dấu hiệu |
| --- | --- | --- |
| `ClassNotFoundException` | Một API yêu cầu tìm class theo tên nhưng không tìm thấy | Checked exception từ `loadClass`, `Class.forName` |
| `NoClassDefFoundError` do thiếu definition/dependency | JVM/linking không lấy được definition cần thiết | Có thể có tên class/dependency bị thiếu |
| `NoClassDefFoundError: Could not initialize class X` | X đã có runtime Class nhưng initialization trước đó fail | Nên tìm first exception của `X.<clinit>` |
| `ExceptionInInitializerError` | First failed init do non-Error exception | `getCause()` chứa exception gốc |
| `ClassFormatError` / `VerifyError` | Classfile/verification | Bytes hoặc bytecode không hợp lệ |
| `UnsupportedClassVersionError` | Runtime không hỗ trợ classfile version | Kiểm tra phiên bản JDK và `javac --release` |

Một `NoClassDefFoundError` không có cụm “Could not initialize class” vẫn có nhiều nguyên nhân, vì thế không chẩn đoán tất cả là static-init failure. Ngược lại, nếu đã có first `ExceptionInInitializerError` thì tiếp tục copy JAR không phải bước sửa đầu tiên.

## 2. Class initialization diễn ra lúc nào?

Theo JLS `12.4.1, initialization có thể được kích hoạt ngay trước:

- Tạo instance bằng `new X()`.
- Gọi static method của X: `X.run()`.
- Gán/đọc static field khai báo bởi X, trừ tình huống **constant variable** có giá trị compile-time constant.
- Các API reflection/`Class.forName(..., true, loader)` và một số method handles theo Java spec.
- Khi cần initialize subclass, JVM khởi tạo superclass và những superinterfaces phù hợp có default methods theo thuật toán đặc tả.

Các thao tác sau **không tự đồng nghĩa** đã init thành công:

~~~java
Class<?> type = X.class;
Class<?> onlyLoaded = loader.loadClass("demo.X");
Class<?> noInit = Class.forName("demo.X", false, loader);
~~~

Đây là snippet cần X và loader hợp lệ. `ClassLoader.loadClass` không được coi là lệnh initialize; `Class.forName(name,false,loader)` nêu rõ không yêu cầu initialize tại lời gọi ấy. Nhưng một số linking/resolution khác vẫn có thể xảy ra, nên “chưa init” không có nghĩa mọi thao tác reflection đều hoàn toàn không có lỗi.

### 2.1. Constant-variable ngoại lệ

~~~java
static final String LABEL = "hello";       // compile-time constant
static final int SIZE = 42;                // compile-time constant
static final Object OBJECT = new Object(); // không phải constant variable
static final int COUNT = compute();        // không phải constant variable
~~~

Đây là field declarations minh họa trong class. Compiler có thể inline `LABEL` hoặc `SIZE` ở caller, nên đọc chúng không nhất thiết khởi tạo class khai báo. Đọc `OBJECT` hoặc `COUNT` thường yêu cầu initialization.

## 3. Bài lab 1: first EIIE, next NoClassDefFoundError

**Tệp duy nhất `InitFailureDemo.java`:**

~~~java
public final class InitFailureDemo {
    static final class Broken {
        static final int NUMBER = initialize();

        private static int initialize() {
            System.out.println("initializer runs");
            throw new IllegalStateException("configuration missing");
        }

        static int read() {
            return NUMBER;
        }
    }

    public static void main(String[] args) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            System.out.println("attempt=" + attempt);
            try {
                System.out.println("value=" + Broken.read());
            } catch (LinkageError error) {
                System.out.println("error="
                        + error.getClass().getSimpleName());
                if (error instanceof ExceptionInInitializerError e) {
                    System.out.println("root="
                            + e.getCause().getClass().getSimpleName());
                }
            }
        }
    }
}
~~~

Chạy JDK 17+:

~~~bash
javac --release 17 InitFailureDemo.java
java -cp . InitFailureDemo
~~~

Stdout dự kiến:

~~~text
attempt=1
initializer runs
error=ExceptionInInitializerError
root=IllegalStateException
attempt=2
error=NoClassDefFoundError
~~~

**Giải thích:**

1. `Broken.read()` là lời gọi static, JVM phải initialize `Broken` trước.
2. Static field initializer `NUMBER=initialize()` in `initializer runs` rồi ném `IllegalStateException`.
3. Vì đây không phải `Error`, lần đầu JVM báo `ExceptionInInitializerError` có `getCause()` là lỗi gốc.
4. Runtime class `Broken` chuyển thành erroneous; method `read` chưa thực sự trả về value.
5. Lần sau `Broken.read()` không thực thi lại static initializer. JVM ném `NoClassDefFoundError`.
6. Dùng `LinkageError` tại lab này vì cả EIIE và NCDFE đều là subclass của nó; trong production đừng bắt mọi `Error` rồi âm thầm bỏ qua.

**Vì sao không viết `static { throw new RuntimeException(); }` trực tiếp?** Java language có quy tắc static initializer **phải có khả năng hoàn tất bình thường**, nên một block luôn ném exception vô điều kiện có thể không biên dịch. Đưa lỗi vào method `initialize()` tạo mã hợp lệ và biểu thị tình huống thực tế như đọc configuration thất bại.

**Giới hạn stdout:** Bài lab chỉ in tên lớp Throwable; nếu in `printStackTrace()`, message hoặc cause stack có thể thay đổi theo JVM/JDK. Không yêu cầu test assert toàn bộ stack trace dòng-đến-dòng.

## 4. JVM lưu trạng thái class thế nào?

JLS `12.4.2 và JVMS `5.5` mô tả một trạng thái cho mỗi runtime class:

| Trạng thái | Hành vi khi cần initialize |
| --- | --- |
| Verified/prepared, chưa initialized | Một thread bắt đầu init |
| Đang initialized bởi thread T | Tái nhập ở chính T có quy tắc riêng; thread khác chờ |
| Initialized thành công | Dùng class, không chạy lại static init |
| Erroneous sau thất bại | Ném `NoClassDefFoundError` |

Class initialization có **initialization lock** do JVM quản lý. Nó không phải `ClassLoader.getClassLoadingLock(name)`: khóa thứ hai liên quan load/define class, tránh duplicate `defineClass`. Một loader implement thread-safe không đảm bảo static initializer không thất bại.

Nếu một thread T1 bắt đầu init và thread T2 đồng thời cần class đó, T2 chờ T1. Nếu T1 thất bại, T2 quay lại thấy erroneous và nhận NCDFE. **Thread nào được first error phụ thuộc scheduler**, không phụ thuộc tên thread.

### 4.1. Nếu static code ném một Error?

~~~java
static int load() {
    throw new AssertionError("invariant broken");
}
~~~

Snippet nằm trong class được init qua static field. Vì `AssertionError extends Error`, thuật toán initialization không bắt buộc wrap lần đầu thành `ExceptionInInitializerError`; caller đầu có thể thấy chính `AssertionError`. Sau đó class vẫn erroneous và lần cần init tiếp theo nhận `NoClassDefFoundError`.

Không viết lời giải thích “mọi lỗi lần đầu là EIIE”. Quy tắc chính xác là **wrap non-Error throwable**, giữ Error theo thuật toán đặc tả.

### 4.2. Nếu superclass khởi tạo thất bại?

Khi `Child extends Parent`, JVM cần initialize `Parent` trước khi thực thi static initializer của `Child`. Nếu Parent fail, Child có thể được đánh dấu erroneous mà static code của Child chưa từng chạy. Lỗi liên quan Child trong log sau đó không nhất thiết chỉ ra nguyên nhân trong `Child.<clinit>`.

Cách tìm root cause: lần theo **stack trace ở lần thất bại sớm nhất**, cả `Parent.<clinit>`/superinterfaces và dependent static factories. Đừng chỉ search tên class trên dòng NCDFE cuối.

## 5. Bài lab 2: load class không đồng nghĩa initialize

**`InitializationTriggerDemo.java`:**

~~~java
public final class InitializationTriggerDemo {
    static final class Sample {
        static {
            System.out.println("Sample initialized");
        }

        static final String CONSTANT = "fixed";
        static int dynamic = 7;
    }

    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName(
            "InitializationTriggerDemo$Sample",
            false,
            InitializationTriggerDemo.class.getClassLoader());

        System.out.println("loaded=" + type.getSimpleName());
        System.out.println("constant=" + Sample.CONSTANT);
        System.out.println("before dynamic read");
        System.out.println("dynamic=" + Sample.dynamic);
    }
}
~~~

~~~bash
javac --release 17 InitializationTriggerDemo.java
java -cp . InitializationTriggerDemo
~~~

Stdout:

~~~text
loaded=Sample
constant=fixed
before dynamic read
Sample initialized
dynamic=7
~~~

`Class.forName(name,false,loader)` lấy object Class nhưng không init; đọc compile-time constant `Sample.CONSTANT` không buộc init; đọc nonconstant `Sample.dynamic` mới kích hoạt static block. Đây là cách phân biệt **“class đã tìm thấy/nạp”** với **“class đã initialized thành công”**.

## 6. Sửa cấu hình rồi retry trong cùng loader có được không?

Giả sử một static singleton:

~~~java
public final class Settings {
    static final String TOKEN = readRequiredToken();

    static String readRequiredToken() {
        String token = System.getProperty("service.token");
        if (token == null) {
            throw new IllegalStateException("missing service.token");
        }
        return token;
    }
}
~~~

Đây là class biên dịch được (không có main). Lần đầu một active use khiến `readRequiredToken()` throw, `Settings` erroneous. Bạn có thể gọi `System.setProperty("service.token","fixed")`, nhưng **điều đó không làm static initializer của same runtime Settings được chạy lại**.

Những thao tác **không reset erroneous state**:

- `Class.forName(name, false, sameLoader)`.
- `ClassLoader.loadClass(name)` với cùng defining loader.
- Gọi `System.gc()`.
- Gọi `URLClassLoader.close()` rồi giữ `Class<?>` cũ.
- Thay/ghi đè JAR file trong lúc JVM vẫn giữ class đã nạp.
- Retry vô hạn một static method trên class đã failed init.

Không có Java SE API phổ quát kiểu `resetClinit`. Cách phục hồi thực tế là **khởi động lại JVM sau khi sửa nguyên nhân**, hoặc trong plugin architecture tạo **defining ClassLoader mới** để có runtime class mới.

## 7. Bài lab 3: phục hồi bằng loader mới cho plugin

Cấu trúc:

~~~text
new-loader-lab/
├── plugin-src/demo/plugin/Settings.java
├── host-src/NewLoaderDemo.java
└── build/
    ├── plugin/
    ├── host/
    └── plugin.jar
~~~

**`plugin-src/demo/plugin/Settings.java`:**

~~~java
package demo.plugin;

public final class Settings {
    private static final String VALUE = initialize();

    private static String initialize() {
        String token = System.getProperty("lesson.token");
        System.out.println("init token=" + token);
        if (token == null) {
            throw new IllegalStateException("token unavailable");
        }
        return token;
    }

    public static String read() {
        return VALUE;
    }
}
~~~

**`host-src/NewLoaderDemo.java`:**

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class NewLoaderDemo {
    private static final String TYPE = "demo.plugin.Settings";

    static URLClassLoader open(URL jar) {
        return new URLClassLoader(
            new URL[] {jar}, ClassLoader.getPlatformClassLoader());
    }

    static void initialize(String label, ClassLoader loader) {
        try {
            Class.forName(TYPE, true, loader);
            System.out.println(label + "=OK");
        } catch (LinkageError problem) {
            System.out.println(label + "="
                    + problem.getClass().getSimpleName());
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException(missing);
        }
    }

    public static void main(String[] args) throws Exception {
        URL jar = Path.of("build", "plugin.jar")
                .toAbsolutePath().toUri().toURL();
        System.clearProperty("lesson.token");

        try (URLClassLoader oldLoader = open(jar)) {
            Class<?> oldType = oldLoader.loadClass(TYPE);

            initialize("old-first", oldLoader);
            System.setProperty("lesson.token", "ready");
            initialize("old-again", oldLoader);

            try (URLClassLoader newLoader = open(jar)) {
                Class<?> fresh =
                        Class.forName(TYPE, true, newLoader);
                System.out.println("same-class="
                        + (oldType == fresh));
                System.out.println("new-value="
                        + fresh.getMethod("read").invoke(null));
            }
        } finally {
            System.clearProperty("lesson.token");
        }
    }
}
~~~

Từ root `new-loader-lab` (POSIX shell, Java 17+):

~~~bash
mkdir -p build/plugin build/host
javac --release 17 -d build/plugin \
    plugin-src/demo/plugin/Settings.java
jar --create --file build/plugin.jar -C build/plugin .

javac --release 17 -d build/host host-src/NewLoaderDemo.java
java -cp build/host NewLoaderDemo
~~~

Stdout kỳ vọng:

~~~text
init token=null
old-first=ExceptionInInitializerError
old-again=NoClassDefFoundError
init token=ready
same-class=false
new-value=ready
~~~

**Chú ý:**

1. Loader đầu `oldLoader` tìm class trên JAR và lần init đầu thất bại. Sau đó `lesson.token` được sửa nhưng lần init với same loader vẫn gặp NCDFE.
2. Loader mới `newLoader` thực sự **tự define** cùng binary name thành runtime Class khác. Vì nó chưa erroneous và config đã sửa, `<clinit>` chạy thành công.
3. `same-class=false` kiểm tra **runtime class identity**, không tuyên bố class cũ đã unload.
4. Đừng đặt `plugin.jar` vào host classpath hoặc thay parent thành loader đã define `demo.plugin.Settings`: parent delegation có thể làm cả hai “loader mới” vẫn nhận cùng Class cũ.
5. `System.setProperty` là cơ chế mô phỏng lỗi cho lab; trong production không dùng shared process properties như một mutable config trên nhiều concurrent requests.
6. `try-with-resources` close URLClassLoader/JAR handles nhưng không tự dừng threads, xóa static maps hay ép JVM unload classes.

**Không sử dụng loader mới như một cách che mọi lỗi:** phải **sửa nguyên nhân gốc** trước. Nếu config vẫn thiếu, class mới cũng sẽ fail.

## 8. Log lỗi đầu tiên mới là nơi cần điều tra

Trong server, request đầu tiên chạm `ConfigHolder` có thể thất bại lúc 09:00, nhưng log ứng dụng bị cắt và chỉ còn request lúc 10:00 in NCDFE. Điều này khiến người vận hành kết luận nhầm rằng JAR biến mất sau deploy.

Chẩn đoán:

1. Thu stack trace của **first failure** từ đúng JVM/process/generation.
2. Tìm `ExceptionInInitializerError`, `<clinit>`, `Caused by` hoặc `Error` trực tiếp từ static initializer.
3. Ghi class name, process id, defining loader và thời điểm first active use.
4. Phân biệt lỗi config, network, JDBC, native link, bytecode/dependency hoặc permission.
5. Kiểm tra những failures trong **thread khác**: initializer đồng bộ, request thứ hai có thể chỉ thấy NCDFE sau khi chờ thread đầu.
6. Khi lỗi bắt đầu từ superclass, tìm root exception ở superclass chứ không chỉ subclass hiện trong NCDFE mới nhất.
7. Không để framework bắt mọi `Throwable` rồi bỏ qua lỗi gốc; với component hỏng, ghi trạng thái FAILED/UNHEALTHY và tránh đưa vào active routing.

### 8.1. In nguyên cause chain

~~~java
static void report(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
        System.err.println(current.getClass().getName()
                + ": " + current.getMessage());
        current = current.getCause();
    }
}
~~~

Snippet helper cần đặt trong một class. `ExceptionInInitializerError.getCause()` là API hiện đại để lấy exception được bọc; `getException()` còn tồn tại vì tương thích cũ. Với `NoClassDefFoundError` sau lỗi init, **không bảo đảm** cùng error/cause ban đầu luôn còn nguyên qua mọi JDK; first logs là nguồn quan trọng nhất.

### 8.2. Phân biệt ClassNotFoundException và NoClassDefFoundError

Ví dụ `Class.forName("missing.Dependency")` trả `ClassNotFoundException` nếu loader không tìm class theo tên. Nhưng nếu A.class tồn tại rồi khi link/init cần một B không có ở runtime, JVM có thể phát `NoClassDefFoundError`, và cause có thể cho biết B. Nếu A có `<clinit>` fail, những lần initialize lại A cũng có thể ném NCDFE **dù bytes A vẫn tồn tại**.

Bởi thế đầu tiên xem **full error message, root cause, thời điểm xuất hiện** trước khi sửa đường classpath/module path.

## 9. Static initialization thất bại phổ biến vì đâu?

| Nguyên nhân | Lỗi gốc thường gặp | Hành động hợp lý |
| --- | --- | --- |
| Static config đọc missing env/file | `IllegalStateException`, `NullPointerException` | Validate trước startup, nêu rõ key thiếu |
| Static singleton khởi tạo DB/HTTP client | Timeout hoặc unchecked exception | Chuyển sang instance lifecycle và bounded retry |
| Static initializer gọi một dependency missing | `NoClassDefFoundError`/`ClassNotFoundException` được bọc | Kiểm tra runtime dependencies và loader |
| Static reflective scanner thấy signature mismatch | `NoSuchMethodError`/`IllegalAccessError`/related | Kiểm tra binary compatibility và JPMS access |
| Native library load trong static block | `UnsatisfiedLinkError` | Kiểm tra OS/architecture, native library path, JNI symbols |
| Static code ném `AssertionError` | Một `Error` trực tiếp | Xử lý violated invariant, first error có thể là AssertionError |
| Tạo objects quá nhiều | `OutOfMemoryError` | Kiểm tra memory demand/limits/leak |
| Thứ tự init vòng/cross-thread | NPE do partial state hoặc deadlock | Refactor static dependency graph |
| Superclass initialization thất bại | Lỗi xuất phát từ `Parent.<clinit>` | Truy vết earliest parent failure |
| Loader close cạnh tranh với plugin callback | Lỗi khi cần load class/resource chưa có | Quiesce/drain trước close |

**Nếu lỗi là class initialization deadlock**, có thể không thấy EIIE hay NCDFE, vì threads **đang chờ nhau** chứ chưa đánh dấu class erroneous. Dùng thread dump/JFR hoặc `jcmd` cho JVM đang sống để phân biệt.

## 10. Thiết kế tốt hơn: đừng để transient I/O phá class

Một pattern rủi ro:

~~~java
public final class GatewayHolder {
    static final RemoteGateway CLIENT = RemoteGateway.connect();
}
~~~

Đây chỉ là snippet; `RemoteGateway` là type ứng dụng tự định nghĩa. Nếu `connect()` lỗi lúc first active use, `GatewayHolder` có thể erroneous **suốt đời runtime class**.

Một API instance đơn giản hơn:

~~~java
public final class GatewayManager {
    private volatile RemoteGateway current;

    public synchronized RemoteGateway getOrCreate() {
        RemoteGateway existing = current;
        if (existing != null && existing.isHealthy()) {
            return existing;
        }

        RemoteGateway fresh = RemoteGateway.connect();
        current = fresh;
        return fresh;
    }
}
~~~

Đây cũng là snippet và chưa phải giải pháp production hoàn chỉnh: cần xử lý resource cũ, concurrency với close, timeout, backoff và circuit breaker. Nó minh họa điểm cốt lõi: **class có thể initialized bình thường**, trong khi kết nối được tạo theo vòng đời service và có thể retry có giới hạn sau transient failure.

Nguyên tắc:

- Static fields nên ưu tiên constants/immutable dependencies có initialization đáng tin.
- Validation cấu hình và I/O có thể thất bại nên đặt ở factory/startup service rõ ràng.
- Không publish component đã fail health check.
- Retry phải bounded, tránh infinite loops, có logs/circuit breaker thích hợp.
- Với class đã erroneous trong application loader, restart JVM sau khi sửa config thường là phương án rõ ràng; đừng dùng reflection/Unsafe để “force chạy lại `<clinit>`”.

## 11. Unit test, plugin và ServiceLoader

**Unit tests:** test thứ nhất có thể làm class erroneous trong cùng test JVM, test thứ hai sửa mock/property nhưng vẫn NCDFE. Cách tốt là inject dependencies vào instance và tránh static initializer stateful, hoặc cô lập tests bằng **JVM forks / fresh defining loaders** khi chính initialization behavior là chủ đề test.

**Plugin:** phiên bản plugin mới nên được nạp qua loader thế hệ mới, giữ shared API identity ở parent, kiểm tra version/health trước publish, drain tasks cũ, unregister callback/DI/reflection caches, close plugin resources, rồi close old loader. Tạo một URLClassLoader object mới nhưng vẫn delegate implementation class về old parent **không tạo runtime class mới**.

**ServiceLoader:** có thể discover provider đúng, nhưng khi provider được khởi tạo, static initializer của provider fail. Caller có thể thấy `ServiceConfigurationError` được bọc tùy provider instantiation path. Luôn nhìn underlying cause/first log trước khi sửa `META-INF/services`/module `provides`.

**Class unloading:** `URLClassLoader.close()` không ép unload class đã loaded. GC xử lý unload khi defining loader đủ điều kiện reclaim và JVM quyết định. `System.gc()` không phải API reset static fields hay init state.

## 12. Checklist xử lý sự cố

- [ ] Xác định message **`Could not initialize class`** hay lỗi thiếu class/dependency khác.
- [ ] Tìm **first `ExceptionInInitializerError` hoặc `Error` gốc** trong log của cùng JVM/classloader.
- [ ] Kiểm tra method `<clinit>`/static field initializer cụ thể đã ném lỗi.
- [ ] Nếu superclass/superinterface fail, phân tích chain trước khi sửa child.
- [ ] Ghi Java version, startup command, classpath/module path, TCCL và defining loader.
- [ ] Sửa config/secret/dependency/native path **đầu tiên**.
- [ ] Không retry lại class erroneous trong same defining loader.
- [ ] Restart JVM hoặc dựng **new loader + new runtime class** khi cần init lại.
- [ ] Với plugin, không publish thế hệ mới cho tới khi initialize/health-check OK.
- [ ] Ghi đầy đủ root cause và kiểm thử cả success và failed-first-use.
- [ ] Không dùng `System.gc()`, `close(loader)` hoặc `Class.forName(...false...)` để kỳ vọng reset trạng thái.
- [ ] Khi unit test nhiều test cases, tránh share một static initialization failure giữa tests.

## 13. Câu hỏi tự kiểm tra

1. Class file vẫn có trong JAR nhưng sau static-init failure có NCDFE không? **Có**.
2. Lần first active use khi `IllegalStateException` thoát khỏi initializer thường nhận gì? **ExceptionInInitializerError**.
3. Lần cần initialize tiếp theo của same runtime class? **NoClassDefFoundError**.
4. Initializer có tự chạy lại sau khi sửa env/property không? **Không**.
5. Nếu initializer ném `AssertionError` lần đầu, JVM chắc chắn wrap EIIE? **Không**, Error có thể được ném trực tiếp.
6. `ClassLoader.loadClass` là lệnh initialize? **Không**.
7. `Class.forName(name,true,loader)` khác overload false thế nào? **true yêu cầu initialization**.
8. Đọc `static final String X="abc"` có bắt buộc init không? **Không**, có thể là compile-time constant.
9. Đọc `static final int N=calculate()` thì sao? **Đây không là constant variable**, active use có thể init.
10. Hai threads cùng initialize một Class có chạy `<clinit>` hai lần không? **Không**, JVM điều phối.
11. Parent class fail có thể làm Child erroneous không? **Có**.
12. Tạo new URLClassLoader luôn tạo Class mới? **Không**, nếu delegate lên parent đã define nó.
13. Vì sao `System.gc()` không chữa class erroneous? **GC không reset initialization state**.
14. Phục hồi trong plugin cần gì? **Sửa root cause và dùng new generation loader thực sự define class mới**.
15. Khác nhau căn bản giữa CNFE và NCDFE? **CNFE là checked lookup failure; NCDFE là Error lúc runtime/linking/init state**.
16. Lỗi nào quan trọng nhất trong log? **Lỗi gốc đầu tiên**, không chỉ NCDFE cuối.
17. Có nên tạo DB connection dễ fail trong `static {}` không? **Không nên**, nên quản lý lifecycle/retry ở service.
18. Nếu thread chết vì class-init error nhưng process sống, request khác có thể nhận NCDFE? **Có**, cùng runtime class đã erroneous.

## 14. Tài liệu chuẩn

1. [JLS `12.4.1 — When Initialization Occurs](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4.1): active use, constants.
2. [JLS `12.4.2 — Detailed Initialization Procedure](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4.2): erroneous state, initialization locks, EIIE và NCDFE.
3. [JVMS `5.5 — Initialization](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.5): runtime initialization procedure.
4. [ExceptionInInitializerError — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ExceptionInInitializerError.html): causal exception and hierarchy.
5. [NoClassDefFoundError — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/NoClassDefFoundError.html): runtime linkage error.
6. [ClassNotFoundException — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassNotFoundException.html): checked lookup failure.
7. [Class — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Class.html): `forName` và initialize flag.
8. [ClassLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, delegation, class identity.
9. [URLClassLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/URLClassLoader.html): loader lifecycle/close.
10. [JLS `12.7 — Class Unloading](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.7): GC/defining loader eligibility.

**Ghi nhớ:** Lỗi `NoClassDefFoundError: Could not initialize class X` thường là **hậu quả của một lần khởi tạo trước thất bại**, không phải thiếu `X.class`. Đọc first exception/cause ở `<clinit>`, sửa nguyên nhân gốc, rồi dùng fresh JVM hoặc new defining ClassLoader khi cần class mới. Đừng mong retry cùng erroneous class, sửa property hay `System.gc()` đặt lại trạng thái.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-class-loading-errors-004">UnsupportedClassVersionError và phiên bản bytecode</a>

<details>
<summary>Xem chi tiết</summary>

# UnsupportedClassVersionError và phiên bản bytecode Java

> **Java Core → ClassLoader → Lỗi thường gặp.** `UnsupportedClassVersionError` xuất hiện khi JVM cố đọc một class file có **major/minor version** mà JVM hiện tại không hỗ trợ. Tình huống phổ biến nhất là **biên dịch bằng JDK mới hơn rồi chạy bằng Java runtime cũ hơn**. Ngoài ra, lỗi cũng liên quan **preview features**, dependency/JAR chứa bytecode khác phiên bản, hoặc class được tạo/biến đổi bởi công cụ. Bài học tập trung vào cách hiểu header `.class`, xác định JDK thực sự đang chạy, sửa đúng ở build/deploy và phòng tránh tái diễn.

## 1. Nhận diện lỗi qua một thông báo thực tế

Một lỗi tiêu biểu khi chạy class được biên dịch nhắm Java 21 trên Java 17:

```text
java.lang.UnsupportedClassVersionError: com/example/App
has been compiled by a more recent version of the Java Runtime
(class file version 65.0), this version of the Java Runtime only
recognizes class file versions up to 61.0
```

Thông báo trên là **ví dụ mô tả dạng lỗi**; JVM có thể in tên class với dấu `.` hoặc `/`, chi tiết và stack trace khác nhau tùy JDK. Dữ liệu cần đọc là:

- `com/example/App`: class JVM đang cố đọc, **không nhất thiết là class có `main`**.
- `class file version 65.0`: class file yêu cầu phiên bản format tương ứng Java 21.
- `up to 61.0`: runtime ở ví dụ hỗ trợ tối đa major 61, tương ứng Java 17.
- JVM từ chối đọc **định dạng class file** trước khi chạy được bytecode của class đó.

Giải pháp trực tiếp có hai hướng **tùy ràng buộc dự án**:

1. Chạy trên **runtime đủ mới** để hỗ trợ class version đã biên dịch (Java 21+ đối với major 65.0, với lưu ý preview).
2. Nếu production phải dùng **Java 17**, biên dịch **toàn bộ code và dependency có thể tương thích** nhắm Java 17 bằng `javac --release 17` hoặc cấu hình build tool tương đương.

**Không** sửa bằng cách đổi tên JAR, thay classpath tùy tiện, đổi TCCL, ép `URLClassLoader` hoặc sửa số version trong header `.class`. Các thao tác đó không làm JVM cũ có thêm bytecode instructions, constant pool tags và JDK APIs của phiên bản mới.

## 2. UnsupportedClassVersionError nằm ở đâu trong họ lỗi JVM?

Quan hệ kế thừa theo API Java:

```text
java.lang.Object
  └── java.lang.Throwable
        └── java.lang.Error
              └── java.lang.LinkageError
                    └── java.lang.ClassFormatError
                          └── java.lang.UnsupportedClassVersionError
```

`UnsupportedClassVersionError` được ném khi JVM **đọc class file** và nhận thấy tổ hợp major/minor version không được hỗ trợ. Đây là một `Error`, **không phải checked exception** và không thuộc `ClassNotFoundException`.

Các lỗi dễ nhầm:

| Lỗi | Tình huống thường gặp | Có phải cùng nguyên nhân? |
| --- | --- | --- |
| `UnsupportedClassVersionError` | JVM không hỗ trợ `major_version`/`minor_version` của class file được load. | **Đúng chủ đề.** |
| `ClassNotFoundException` | Một API tìm class theo tên (`Class.forName`, `ClassLoader.loadClass`) không tìm thấy định nghĩa phù hợp. | Không; class có thể không có trên đường tìm. |
| `NoClassDefFoundError` | JVM cần class khi linking/execution nhưng không có định nghĩa phù hợp, hoặc class trước đó initialization thất bại. | Không mặc định; xem cause và chi tiết. |
| `ClassFormatError` | Bytecode/constant pool/header/classfile không hợp lệ. | Họ lỗi cha; version mismatch là một nhánh con. |
| `VerifyError` | Bytecode không thỏa quy tắc verifier. | Khác format version, dù có thể gặp sau bước đọc class. |
| `NoSuchMethodError` | Runtime method/signature không tương thích với class đã compile. | Có thể do trộn Java APIs/versions; không chữa bằng sửa major. |
| `UnsupportedOperationException` | Một operation hợp lệ về class loading nhưng API ứng dụng không hỗ trợ. | Khác hoàn toàn. |
| `IllegalAccessError` | Runtime access/linkage checks thất bại. | Thường do module, class/member access. |

Một `ClassLoader` có thể tìm **đúng file**, đọc bytes thành công, nhưng `defineClass` vẫn ném `UnsupportedClassVersionError`. Vì vậy đừng đánh đồng “không tìm thấy class” với “JVM không hiểu phiên bản class file”.

## 3. Class-file header: CAFEBABE, minor và major

Tệp `.class` mở đầu bằng header theo JVM Specification `4.1`:

```text
Offset (byte)    Kích thước   Ý nghĩa
0..3             u4           magic = 0xCAFEBABE
4..5             u2           minor_version
6..7             u2           major_version
8..               ...          constant_pool_count và phần còn lại
```

Các số nguyên này dùng **big-endian**. Ví dụ class biên dịch bằng `javac --release 17` thường bắt đầu bằng:

```text
CA FE BA BE 00 00 00 3D ...
| magic   |minor|major|
                 0x003D = 61 (Java 17)
```

Đừng nhầm số version trong `MANIFEST.MF` (`Implementation-Version`, `Bundle-Version`, v.v.) với `major_version` nằm ở **từng entry `.class`**. Một JAR có thể chứa nhiều class files không cùng major version; một dependency lồng trong classpath thậm chí có version khác class `Main`.

### Bảng quy đổi major version

Theo bảng phiên bản trong Java Virtual Machine Specification Java SE 26:

| Java release | Major của class file thông thường |
| --- | ---: |
| Java 8 | 52 |
| Java 9 | 53 |
| Java 10 | 54 |
| Java 11 | 55 |
| Java 12 | 56 |
| Java 13 | 57 |
| Java 14 | 58 |
| Java 15 | 59 |
| Java 16 | 60 |
| Java 17 | 61 |
| Java 18 | 62 |
| Java 19 | 63 |
| Java 20 | 64 |
| Java 21 | 65 |
| Java 22 | 66 |
| Java 23 | 67 |
| Java 24 | 68 |
| Java 25 | 69 |
| Java 26 | 70 |

Với **Java 5 trở lên**, quy tắc tra nhanh cho release `N` là `major = N + 44` (và `N = major - 44` trong phạm vi này). **Không áp dụng máy móc công thức cho các bản cổ 1.0–1.4**: Java 1.0/1.1 dùng major 45 và Java 1.2–1.4 có 46–48.

Theo Java SE 26, một JVM tương thích hỗ trợ major trong phạm vi 45–70; các JVM Java 17 hay 21 có **giới hạn trên thấp hơn**. JVM mới có xu hướng hỗ trợ format cũ, **nhưng không có nghĩa mọi ứng dụng cũ luôn chạy y nguyên trên JDK mới** vì còn runtime APIs, modules, native libraries và các thay đổi khác.

### Version trong thông báo đọc thế nào?

`65.0` nghĩa là **major=65, minor=0**, tương ứng định dạng thường của Java 21. Không phải “Java 65” hoặc JDK 6.5. `61.0` nghĩa Java 17 class-file format. Đọc hai số này trước sẽ tránh mất thời gian sửa sai library.

## 4. Minor version và preview features: ngoại lệ quan trọng

Một lớp dùng preview features có thể mang **minor version 65535**. Quy tắc từ Java 12 trở đi trong JVM Specification:

- Với class-file major từ 56 trở lên, minor hợp lệ thường là **0 hoặc 65535**.
- `M.65535` biểu thị class **phụ thuộc preview features** của đúng release tương ứng major `M`.
- Class preview của release `N` chỉ được load trên JVM **đúng release `N`**, khi preview được bật cho runtime (thường `java --enable-preview ...`).
- JVM phiên bản **mới hơn** không tự chấp nhận preview class file của phiên bản **cũ hơn**, ngay cả khi bật `--enable-preview`.
- Không nên lấy `--enable-preview` làm cách sửa mọi `UnsupportedClassVersionError`; lỗi major quá mới và lỗi preview minor khác nhau.

Ví dụ:

```text
61.0      = class-file thường nhắm Java 17
61.65535  = preview class-file của Java 17
65.0      = class-file thường nhắm Java 21
65.65535  = preview class-file của Java 21
70.0      = class-file thường nhắm Java 26
70.65535  = preview class-file của Java 26
```

Một `61.65535` có thể dùng trên đúng Java 17 với preview enabled, nhưng **không dùng trên Java 21/24/26** chỉ bằng cách bật flag. Cách xử lý bền vững là **biên dịch lại source preview theo Java release đích**, sửa code để dùng stable features nếu có thể.

### Biên dịch và chạy preview trên cùng release

Cú pháp tổng quát (ví dụ cho một JDK đang dùng preview):

```bash
javac --enable-preview --release 26 Demo.java
java --enable-preview Demo
```

Đây là **ví dụ cú pháp cho JDK 26**, giả định source có preview features được JDK đó hỗ trợ; nó **không chạy nguyên trên JDK 17/21/24** và không thay test độc lập của bài ở mục 8. Compiler chỉ hỗ trợ `--enable-preview` kết hợp source/release của **phiên bản compiler hiện tại**; không dùng JDK 26 để phát hành preview class của Java 17 bằng `--release 17`.

Đối với class files cũ trước Java 12, quy tắc minor có lịch sử khác; khi chẩn đoán ứng dụng Java hiện đại, hãy tập trung vào các cặp major/minor được nêu trong thông báo.

## 5. Vì sao biên dịch bằng JDK mới rồi chạy JVM cũ thất bại?

Giả sử:

```text
Developer machine / CI:
    javac 21 -> App.class major=65

Production server:
    java 17 -> supports max major=61

ClassLoader reads App.class
    -> JVM rejects version 65
    -> UnsupportedClassVersionError
```

Compiler mặc định thường tạo class files theo Java release của **chính compiler** nếu không được cấu hình target. Vì vậy, nếu team có người dùng JDK 21 còn runtime production Java 17, chỉ chạy `javac App.java` hoặc cấu hình mặc định có thể tạo artifact không tương thích.

Tình huống lỗi phổ biến:

1. **JDK nâng trên máy build nhưng production chưa nâng.** Docker image base hoặc VM JRE vẫn Java 17.
2. **Maven/Gradle toolchain không thống nhất.** Java compiler dùng phiên bản mới nhưng launcher runtime dùng phiên bản cũ.
3. **Thư viện bên thứ ba nâng major.** Source của ứng dụng vẫn build target cũ nhưng dependency release mới chỉ hỗ trợ JVM mới.
4. **IDE biên dịch bằng Project SDK khác runtime.** IntelliJ/Eclipse có project language level, compiler SDK và run configuration khác nhau.
5. **Plugin loader nạp JAR của plugin mới.** Host runtime cũ không đọc được class của plugin, dù host chính chạy thành công.
6. **Framework sinh hoặc instrument bytecode.** Byte Buddy, ASM, proxy generator hoặc build plugin có thể tạo class-file major ngoài khả năng JVM hiện tại.
7. **Multi-release JAR đóng gói/đọc sai.** Class theo thư mục `META-INF/versions` cần được runtime chọn theo quy tắc Multi-Release JAR; tool custom lấy nhầm entry tương ứng JDK mới.
8. **Lẫn PATH, JAVA_HOME và toolchain.** `javac` và `java` thực sự được gọi có thể đến từ hai JDK khác nhau.

Một class `Main` có major 61 không đảm bảo tất cả `.class` trong classpath đều major 61. JVM chỉ kiểm tra khi từng class được load; vì vậy app có thể khởi động, chạy vài tính năng rồi lỗi khi truy cập một class mới trong dependency/plugin.

## 6. Kiểm tra phiên bản compiler và runtime thật đang dùng

### Phân biệt java, javac và process đang chạy

Các lệnh:

```bash
java -version
javac -version
java --version
javac --version
```

Thường `java -version` in thông tin runtime và `javac -version` in compiler. **Không kết luận chúng trỏ cùng installation** chỉ vì các lệnh không báo lỗi.

Nếu cần kiểm tra đường executable:

```bash
# Linux/macOS:
command -v java
command -v javac
echo "$JAVA_HOME"

# PowerShell:
Get-Command java
Get-Command javac
$env:JAVA_HOME
```

Đây là lệnh shell phụ thuộc môi trường; không có stdout cố định vì path khác nhau. `JAVA_HOME` là biến cấu hình, **không bắt buộc trùng** executable được PATH hoặc IDE/Gradle daemon/container chọn.

Với container hoặc production process, hãy chạy lệnh bên **chính môi trường đang chạy ứng dụng** thay vì chỉ từ terminal máy developer. Kiểm tra JVM của service bằng startup logs/`jcmd` nếu có quyền; `java -version` từ một terminal host không chứng minh JVM chạy trong container là cùng bản.

### Kiểm tra class file thật

Lệnh JDK:

```bash
javap -verbose path/to/App.class
```

Trong phần đầu sẽ thấy:

```text
minor version: 0
major version: 65
```

Đây là **output minh họa** của class major 65, không phải kết quả khi chạy trên artifact trong mục 8 (artifact ví dụ mục 8 là major 61). Nếu `javap` là phiên bản cũ hơn class file, công cụ có thể không phân tích hết cấu trúc mới; đọc trực tiếp 8 bytes header vẫn giúp xác định version.

Với class nằm trong JAR:

```bash
jar tf my-library.jar
javap -verbose -classpath my-library.jar com.example.Widget
```

`my-library.jar` và `com.example.Widget` là placeholders. `javap` phải chọn đúng JAR và class để kết quả có ý nghĩa. `jar tf` cho thấy entries nhưng **không tự liệt kê major** của từng `.class`. Nếu có nhiều JAR cùng class, kiểm tra đúng archive runtime đã chọn; riêng `javap -classpath ...` không chứng minh chính class đó được loader đang chạy lấy từ JAR ấy.

## 7. Cách sửa đúng: nâng runtime hay build xuống?

### Lựa chọn A — nâng JVM runtime

Khi cần APIs/language features/bytecode của Java 21:

```text
Compile target: Java 21
Runtime:        Java 21 hoặc cao hơn với điều kiện tương thích phù hợp
```

Với major 65.0, nâng từ Java 17 lên Java 21 sẽ giải quyết **giới hạn class-file major**. Tuy nhiên vẫn cần kiểm thử dependencies, JPMS encapsulation, native code, JVM flags và behavioral changes. Chỉ nâng JDK nếu được dự án và vận hành chấp nhận, cập nhật cả Docker image/VM/container/service config.

Nếu class là preview `65.65535`, chạy trên Java 21 với preview enabled mới là điều kiện phù hợp; **Java 25 không tự dùng được** class preview từ Java 21.

### Lựa chọn B — biên dịch để hỗ trợ Java cũ

Khi production phải chạy Java 17, nên dùng:

```bash
javac --release 17 -d out src/com/example/App.java
```

Snippet lệnh giả định file Java đúng vị trí. `--release 17` giúp đồng thời:

- Sử dụng cú pháp và tính năng ngôn ngữ theo **release 17**.
- Phát sinh class files tương ứng với **target Java 17** (major 61).
- Kiểm tra các public documented Java SE/JDK APIs khả dụng ở release 17, tránh vô tình gọi JDK API chỉ có ở release mới.

So với cách chỉ sử dụng `--target 17`, `--release 17` an toàn hơn cho cross-compilation bởi nó đồng bộ **source, target và platform APIs**.

Ví dụ một chương trình dùng API được bổ sung sau Java 17 sẽ không thể “chạy Java 17” chỉ nhờ ép major 61. Compiler `--release 17` sẽ từ chối API không có trong release đích khi nhận diện qua symbols tương ứng.

### Vì sao --source và --target riêng chưa đủ?

`--source 17` chọn language syntax. `--target 17` chỉ định class-file output target. Nếu compile trên JDK mới với **các API của JDK mới** đang hiển thị, một source vẫn có thể compile và tạo major 61 nhưng thất bại ở runtime Java 17 vì thiếu class/method (ví dụ `NoSuchMethodError`), tùy chương trình.

```bash
# Thường NÊN dùng:
javac --release 17 -d out src/com/example/App.java

# Không tương đương hoàn toàn về ràng buộc platform APIs:
javac --source 17 --target 17 -d out src/com/example/App.java
```

Hai lệnh là mẫu áp dụng cho cấu trúc dự án phù hợp, không phải copy-run khi file `src/com/example/App.java` chưa tồn tại. `--release` không thể kết hợp cùng `--source` hoặc `--target` trong một invocation.

Quan trọng: `--release 17` ở **module ứng dụng** không tự ép **dependency JAR đã biên dịch sẵn** về major 61. Nếu dependency chứa major 65, bạn phải chọn **dependency version hỗ trợ Java 17**, tự build dependency đúng release từ source hợp lệ, hoặc nâng runtime.

### Chọn phiên bản compile có hỗ trợ

Không phải JDK nào cũng cho `--release` về mọi phiên bản quá cũ. Kiểm tra:

```bash
javac --help
```

Tài liệu `javac` nêu mỗi JDK chỉ hỗ trợ tập release hiện tại và một số release trước. Cần chọn compiler/toolchain phù hợp thay vì tùy tiện nhập `--release 1.4` trên JDK 26.

## 8. Thực hành chạy được: đọc major/minor và tái hiện UnsupportedClassVersionError

Để không cần cài song song hai phiên bản JDK, thí nghiệm dưới đây:

1. Biên dịch class `demo.Greeting` bằng `--release 17`, tạo class header **major 61, minor 0**.
2. Dùng custom loader nạp bytes gốc thành công.
3. Tạo **bản sao trong bộ nhớ** của bytes rồi cố tình sửa **hai byte major thành 32767**, vượt xa Java releases đang được hỗ trợ, để kiểm tra `UnsupportedClassVersionError`.
4. Tạo bản sao khác giữ major 61 nhưng đặt **minor thành 65535**, tương đương header preview class của Java 17, rồi thử nạp trên runtime **khác release** hoặc không bật preview; nó bị từ chối theo quy tắc preview.
5. Không ghi đè class file gốc trên disk. Việc sửa header ở đây **chỉ là thí nghiệm minh họa điều kiện JVM kiểm tra**, **không phải cách chữa lỗi**.

Thí nghiệm tương thích Java 17+; kết quả dưới được kiểm chứng trên OpenJDK 24 với preview không bật. Nếu chạy đúng JDK 17 **và bật** `--enable-preview`, kịch bản minor 61.65535 có thể khác, như mục 4.

### Cấu trúc thư mục

```text
src/
  demo/
    Greeting.java
  app/
    VersionProbe.java
out/
  plugin/     (javac tạo)
  app/        (javac tạo)
```

**File `src/demo/Greeting.java`:**

```java
package demo;

public class Greeting {
    public String say() {
        return "hello";
    }
}
```

**File `src/app/VersionProbe.java`:**

```java
import java.nio.file.Files;
import java.nio.file.Path;

public class VersionProbe {
    static final class DemoLoader extends ClassLoader {
        DemoLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        Class<?> define(byte[] bytes) {
            return defineClass(
                    "demo.Greeting", bytes, 0, bytes.length);
        }
    }

    static int u2(byte[] b, int offset) {
        return ((b[offset] & 0xff) << 8)
                | (b[offset + 1] & 0xff);
    }

    public static void main(String[] args) throws Exception {
        byte[] original = Files.readAllBytes(
                Path.of("out/plugin/demo/Greeting.class"));

        System.out.println("magic: "
                + String.format("%02X%02X%02X%02X",
                        original[0], original[1],
                        original[2], original[3]));
        System.out.println("minor: " + u2(original, 4));
        System.out.println("major: " + u2(original, 6));
        System.out.println("normal load: "
                + new DemoLoader().define(original).getName());

        byte[] future = original.clone();
        future[6] = (byte) 0x7F;
        future[7] = (byte) 0xFF;  // major = 32767

        try {
            new DemoLoader().define(future);
            System.out.println("future-major rejected: false");
        } catch (UnsupportedClassVersionError expected) {
            System.out.println("future-major rejected: true");
        }

        byte[] oldPreview = original.clone();
        oldPreview[4] = (byte) 0xFF;
        oldPreview[5] = (byte) 0xFF; // minor = 65535

        try {
            new DemoLoader().define(oldPreview);
            System.out.println("old-preview rejected: false");
        } catch (UnsupportedClassVersionError expected) {
            System.out.println("old-preview rejected: true");
        }
    }
}
```

**Lệnh biên dịch** (từ thư mục chứa `src`):

```bash
javac --release 17 -d out/plugin src/demo/Greeting.java
javac --release 17 -d out/app src/app/VersionProbe.java
javap -verbose out/plugin/demo/Greeting.class
java -cp out/app VersionProbe
```

Ở Windows PowerShell/CMD, có thể chạy các lệnh một dòng tương tự nếu thư mục hiện hành đúng. `javap -verbose` sẽ in nhiều thông tin; phần liên quan:

```text
minor version: 0
major version: 61
```

**Stdout chính xác của `java -cp out/app VersionProbe` trên OpenJDK 24:**

```text
magic: CAFEBABE
minor: 0
major: 61
normal load: demo.Greeting
future-major rejected: true
old-preview rejected: true
```

### Vì sao hai lần define hỏng?

- `future[6]` và `future[7]` là byte vị trí **major_version**, big-endian. `0x7FFF = 32767` không được JVM đang dùng hỗ trợ.
- `oldPreview[4]` và `oldPreview[5]` là **minor_version**. Với major 61, minor `65535` biểu thị preview class theo Java 17; JVM OpenJDK 24 chạy bài thử không thể nạp preview class của Java 17.
- `DemoLoader` mỗi lần là instance khác nhau, để tránh lỗi định nghĩa trùng cùng binary name và đảm bảo quan sát đúng lỗi **version**. Class `demo.Greeting` không có trong `out/app` và parent platform loader không nhìn thấy `out/plugin`.
- JVM phát hiện và ném `UnsupportedClassVersionError` tại `defineClass`; code chủ động catch để demo tiếp tục. **Ứng dụng sản xuất không nên catch rồi im lặng**, bởi đó là dấu hiệu artifact không tương thích.
- `byte[]` gốc vẫn giữ header `61.0` và có thể nạp. Không có tệp `.class` nào bị vá trên disk.

**Không lấy số 32767 để định danh một bản Java thật**. Nó chỉ đóng vai trò một major không được hỗ trợ trong thí nghiệm, không phải version mà bạn nên đưa vào artifact.

## 9. Tại sao lỗi có thể đến từ dependency, plugin hoặc bytecode sinh ra?

Một stack trace có thể nêu:

```text
com/vendor/library/SomeHelper
```

dù class có `main` của bạn đã chạy xong giai đoạn startup. Lý do JVM thực hiện **loading và resolution theo nhu cầu**: chỉ đến khi chương trình chạm một class mới thì JVM mới cần đọc class-file bytes của nó.

### Dependency biên dịch mới hơn

Ví dụ:

```text
application.jar          (major 61, Java 17)
  -> logging-core.jar    (major 61)
  -> new-feature.jar     (major 65, Java 21)
runtime: Java 17
```

`new-feature.jar` chứa class major 65 nhưng Java 17 chỉ hỗ trợ tối đa major 61; lỗi xuất hiện khi JVM cần class của thư viện đó. Hạ target của **application** không tự biên dịch lại `new-feature.jar`.

Cách xử lý:

- Tìm class đúng tên trong stack trace và artifact chứa nó.
- Kiểm tra dependency tree (Maven/Gradle) và bản phát hành hỗ trợ Java tối thiểu.
- Chọn version dependency còn hỗ trợ Java 17 hoặc nâng runtime.
- Nếu tự build dependency, biên dịch lại toàn bộ với `--release 17` và xác minh không gọi API mới.

### Plugin loader tùy chỉnh

Một `URLClassLoader` có thể tìm class thành công nhưng `defineClass` ném `UnsupportedClassVersionError`. Một custom loader không thể hỗ trợ major 65 trên Java 17 bằng cách tự đặt `getClassLoadingLock`, đổi parent, thêm URL hoặc chạy `definePackage`: định dạng class là giới hạn **của JVM đang nhận bytecode**, không phải của đường tìm kiếm.

Với hệ plugin nhiều phiên bản, quy định **minimum supported Java runtime** trong plugin metadata và xác minh artifact trước khi kích hoạt để báo lỗi sớm với thông điệp dễ hiểu.

### Bytecode generator/transformer

Các công cụ như ASM/Byte Buddy hoặc runtime proxy generator cần tạo class-file version mà JVM chạy hỗ trợ, đồng thời phải hiểu các tính năng mới của JDK. Nếu library tạo bytes major quá mới hoặc không hợp lệ, lỗi có thể nằm trong class sinh động (không có `.class` sẵn trên filesystem). Kiểm tra version của generator, target, JDK runtime và configuration, không giả định sửa `javac --release` sẽ tác động lên bytecode do thư viện sinh tại runtime.

## 10. Multi-release JAR: nhiều bản classfile trong cùng một archive

Một **multi-release JAR** có thể có các lớp gốc và các bản thay thế cho Java release khác nhau:

```text
library.jar
├── META-INF/MANIFEST.MF
├── com/example/Feature.class
├── META-INF/versions/17/com/example/Feature.class
└── META-INF/versions/21/com/example/Feature.class
```

Manifest có thuộc tính:

```text
Multi-Release: true
```

Với cơ chế chọn version hợp lệ, runtime sẽ chọn entry version-specific có release phù hợp, thông thường là **cao nhất không vượt quá runtime feature version**, và fallback về base entry nếu cần. Do đó **chỉ thấy `META-INF/versions/21` không có nghĩa Java 17 sẽ cố load class Java 21**.

Nhưng có thể gặp lỗi khi:

- Công cụ đóng gói/shading làm mất hoặc sửa sai thuộc tính `Multi-Release`.
- Base classes của JAR cũng biên dịch ở major quá cao so với runtime cần hỗ trợ.
- Custom loader tự lấy raw entry `META-INF/versions/21/...` thay vì dùng semantics multi-release JAR.
- Framework đọc class bytes từ archive bằng ZIP API và áp dụng thay thế không đúng.
- Dependency JAR không hỗ trợ runtime dù có folder `META-INF/versions`.

Đừng vá bằng cách xóa bừa các thư mục versioned. Kiểm tra cách artifact được đóng gói và cách **loader thực tế** chọn entry.

Lệnh hỗ trợ:

```bash
jar tf library.jar
jar xf library.jar META-INF/MANIFEST.MF
```

`jar xf` giải nén một entry vào thư mục hiện tại, vì vậy chỉ chạy khi biết mục tiêu/thư mục làm việc phù hợp. Nếu chỉ muốn xem nội dung manifest mà không làm thay đổi workspace, có thể dùng công cụ ZIP/JAR viewer thích hợp. Các lệnh không tự kết luận class version nào được chọn; cần xác minh runtime behavior.

## 11. Cấu hình Maven/Gradle và CI để không tái diễn

### Maven: thiết lập release thống nhất

Trong `pom.xml`:

```xml
<properties>
    <maven.compiler.release>17</maven.compiler.release>
</properties>
```

Property này được Maven Compiler Plugin sử dụng ở phiên bản/configuration hỗ trợ. Với dự án phức tạp, nên chỉ định phiên bản compiler plugin được hỗ trợ và dùng toolchains nếu build machine có nhiều JDK. **Java đang chạy Maven** có thể khác **JDK dùng để compile** theo toolchains; kiểm tra cả hai.

Các lệnh quan sát:

```bash
mvn -version
mvn dependency:tree
```

Cần Maven và plugin/dependencies tương ứng. `mvn -version` chủ yếu mô tả JDK chạy Maven process, **không luôn là bằng chứng compiler toolchain được chọn**.

### Gradle: Java toolchain và release

Một cấu hình Gradle Groovy DSL minh họa:

```groovy
plugins {
    id 'java'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType(JavaCompile).configureEach {
    options.release = 17
}
```

Toolchain giúp định rõ compiler/JDK mà Gradle dùng cho Java work; `options.release` yêu cầu class files và API compatibility theo release đích khi compiler hỗ trợ. Không nên chỉ đặt `sourceCompatibility` rồi nghĩ production JDK tự phù hợp. Bản Gradle/plugin đang dùng phải hỗ trợ target JDK; cập nhật tooling nếu cần.

### CI: build và test trên bản Java nhỏ nhất cần hỗ trợ

Checklist CI:

1. **Định nghĩa baseline runtime** (ví dụ Java 17) trong build configuration.
2. Biên dịch bằng `--release 17` hoặc tùy chọn tương đương.
3. **Chạy integration tests bằng Java 17**, không chỉ dùng JDK 21 và hy vọng backward compatibility.
4. Kiểm tra major version của **mọi artifacts phát hành và dependency quan trọng**, không chỉ main class.
5. Trên matrix nếu hỗ trợ nhiều bản, test Java 17/21/25... theo support policy.
6. Nếu có preview code, gắn Java release chính xác và kế hoạch migration; đừng phát hành preview class mập mờ với runtime khác.
7. Kiểm tra Docker base image và deployment JVM thực tế, vì build JDK không quyết định JVM của production.
8. Ghi rõ minimum Java version trong tài liệu/plugin manifest để host phát hiện mismatch sớm.

Một JAR có base class major 61 nhưng dùng native code, JNI hoặc API hành vi khác vẫn cần runtime test thật; quét major **không phải toàn bộ compatibility test**.

## 12. Cây quyết định sửa lỗi theo thông báo

```text
Thấy java.lang.UnsupportedClassVersionError
         |
         v
1. Lấy tên class và cặp class-file M.m
         |
         v
2. Xác định JVM đang chạy thật: java -version / runtime logs
         |
         +--> M > max major runtime?
         |       |
         |       +-- YES:
         |           Chọn:
         |            - nâng runtime lên release hỗ trợ
         |            - HOẶC build lại toàn bộ code/dependency
         |              cho release cũ với --release
         |
         +--> M <= max major runtime?
                 |
                 v
3. Kiểm tra minor:
      - m = 65535? preview đúng cùng release + enabled?
      - m bất thường/không hợp lệ? kiểm tra generator/transformer
                 |
                 v
4. Xác minh JAR/class cụ thể và classloader thực tế
                 |
                 v
5. Rebuild/redeploy + test trên JVM baseline thực tế
```

**Nếu runtime Java 17 mà thấy class file 65.0:** Không có flag classloader nào giải quyết format mismatch. Chọn nâng Java runtime lên mức phù hợp (Java 21+) hoặc hạ target/rebuild dependency xuống Java 17.

**Nếu runtime Java 24 mà thấy class file 61.65535:** Đây không phải lỗi do major quá cao; nguyên nhân nằm ở **preview class file từ Java 17**, vốn không thể dùng trên Java 24 theo JVM Specification. Cần rebuild từ source theo release muốn chạy, hoặc chạy đúng Java 17 với preview enabled nếu được hỗ trợ và chấp nhận.

**Nếu runtime Java 21 mà thấy class file 65.0 nhưng vẫn lỗi khác:** Kiểm tra đúng JVM thực chạy, class source thực được chọn, rồi xem exception **chính xác** là gì. Nếu `NoSuchMethodError` hoặc `IllegalAccessError`, đừng gọi đó là `UnsupportedClassVersionError`.

## 13. Những sai lầm thường gặp

| Sai lầm | Vì sao không hiệu quả | Hướng đúng |
| --- | --- | --- |
| Sửa `JAVA_HOME` rồi kết luận production đã đổi JVM | PATH, service wrapper, Docker image hoặc Gradle daemon có thể dùng Java khác. | Xác định runtime executable/process thực. |
| Chỉ sửa `javac --source 17` | Không đảm bảo class target/platform APIs đều đúng. | Dùng `--release 17` và test JVM17. |
| Chỉ sửa `javac --target 17` | Có thể vẫn compile chống API mới từ JDK đang chạy. | Dùng `--release` hoặc toolchain/API matching. |
| Chỉ build lại app nhưng giữ dependency major 65 | JVM17 vẫn không đọc được class major 65. | Đổi dependency/build source hoặc nâng runtime. |
| Đổi số byte major 65→61 bằng hex editor | Bytecode mới có thể dùng instructions/features/constant pool không hỗ trợ; class có thể hỏng. | Recompile source cho target; không vá header như workaround. |
| Bắt `ClassNotFoundException` để xử lý lỗi version | `UnsupportedClassVersionError` là `Error` khác. | Sửa artifact/JVM mismatch; log version. |
| Thêm URLClassLoader vào classpath | Class có thể được tìm nhưng JVM vẫn không hỗ trợ version. | Xác định JVM major support. |
| Bật `--enable-preview` cho class major mới hơn runtime | Preview flag không làm runtime hiểu format release mới. | Dùng đúng runtime, rebuild thích hợp. |
| Dùng JDK mới chạy preview class từ JDK cũ | Preview class gắn chặt với đúng release. | Recompile source theo release mới. |
| Tin rằng `jar tf` hiển thị class version | Nó chỉ liệt kê entry names. | Dùng `javap -verbose` hoặc đọc header. |
| Nhìn `Main.class` major 61 rồi kết luận mọi plugin tương thích Java17 | Dependency/plugin/generated bytes có thể cao hơn. | Kiểm tra artifacts thật được JVM nạp. |
| Tin build CI xanh trên JDK25 nghĩa chạy được Java17 | Test chạy trên JVM mới không chứng minh tương thích JVM cũ. | Test matrix có runtime baseline. |
| Gặp bất kỳ `LinkageError` đều sửa bằng nâng JDK | LinkageError còn nhiều nguyên nhân khác. | Phân tích exception subtype và message. |

## 14. Tự kiểm tra kiến thức

1. **Thông báo “class file version 65.0, runtime up to 61.0” nghĩa gì?**  
   Class file định dạng Java 21 nhưng runtime chỉ hỗ trợ tối đa Java 17.

2. **`UnsupportedClassVersionError` có phải một checked exception?**  
   Không. Nó kế thừa `ClassFormatError → LinkageError → Error`.

3. **Hai byte nào trong class file ghi major?**  
   Offset 6 và 7, theo big-endian; offsets 4 và 5 chứa minor.

4. **Java 17 và Java 21 thường có major gì?**  
   Tương ứng 61 và 65.

5. **`--release 17` bảo đảm điều gì khác `--target 17` đơn lẻ?**  
   Nó phối hợp source rules, class format và các platform APIs của Java 17.

6. **Một app main major 61 có thể bị lỗi do dependency major 65?**  
   Có, khi runtime cần load class thuộc dependency mới hơn.

7. **`61.65535` có giống `61.0`?**  
   Không. Minor 65535 đánh dấu preview class của Java 17; có yêu cầu runtime đặc biệt.

8. **Java 24 có chạy preview `61.65535` chỉ bằng `--enable-preview`?**  
   Không. Preview class từ Java 17 không được load trên Java 24.

9. **`jar tf library.jar` có báo major tất cả classes không?**  
   Không. Dùng `javap -verbose` cho class cụ thể hoặc công cụ scan class headers.

10. **Vì sao bắt `ClassNotFoundException` không xử lý được version mismatch?**  
    JVM đã tìm/đọc bytes nhưng không hỗ trợ version; nó ném `UnsupportedClassVersionError`.

11. **Có nên sửa byte header 65 thành 61 để chạy trên Java17?**  
    Không. Nó không chuyển ngữ nghĩa bytecode/API mới thành tương thích Java17.

12. **Một multi-release JAR chứa `META-INF/versions/21` có mặc nhiên lỗi trên Java17?**  
    Không, nếu archive và loader tuân thủ multi-release selection và base class hỗ trợ Java17.

13. **Vì sao test trên Java mới không chứng minh app chạy được Java cũ?**  
    Runtime mới thường hiểu format cũ, còn runtime cũ không hiểu format mới; API và dependency cũng khác.

14. **Khi production chạy trong container, nên xem Java version ở đâu?**  
    Trong container/process thực đang chạy ứng dụng, không chỉ trên máy developer.

## 15. Tài liệu chính thức

- [Java SE 26 — JVMS `1.2, bảng class-file major versions](https://docs.oracle.com/javase/specs/jvms/se26/html/jvms-1.html#jvms-1.2): mapping Java 1.0–26 và tính tương thích format.
- [Java SE 26 — JVMS `4.1, class file structure](https://docs.oracle.com/javase/specs/jvms/se26/html/jvms-4.html#jvms-4.1): magic, minor/major version, preview rules và định dạng.
- [Java SE 26 — UnsupportedClassVersionError](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/UnsupportedClassVersionError.html): mô tả class error chính thức.
- [Java SE 25 — javac](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javac.html): `--release`, `--source`, `--target` và điều kiện cross-compilation.
- [Java SE 26 — ClassFileVersion](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/classfile/ClassFileVersion.html): major/minor và preview interpretation.
- [Java SE 21 — ClassLoader.defineClass](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): lúc loader chuyển bytes thành runtime class.
- [JAR File Specification](https://docs.oracle.com/en/java/javase/21/docs/specs/jar/jar.html): multi-release JAR, manifest và versioned entries.
- [Java SE 21 — javap](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javap.html): dùng `-verbose` để đọc major/minor.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-class-loading-errors-005">ClassFormatError, VerifyError và ClassCircularityError</a>

<details>
<summary>Xem chi tiết</summary>

# ClassFormatError, VerifyError và ClassCircularityError trong Java ClassLoader

## 1. Ba lỗi đều liên quan đến JVM loading/linking, nhưng không đồng nghĩa

Khi JVM nạp một Java class, vấn đề không dừng ở việc tìm được tệp `.class`. JVM còn phải đọc cấu trúc class file, kiểm tra phiên bản, xác định quan hệ superclass/superinterface, xác minh bytecode và liên kết các tham chiếu. Ba lỗi sau thường bị gộp nhầm thành “class không load được”:

- **`ClassFormatError`:** dữ liệu class file **không có định dạng hợp lệ** hoặc không thể được JVM diễn giải thành class file hợp lệ.
- **`VerifyError`:** JVM phát hiện class file **không qua được bước bytecode verification**: các quy tắc an toàn kiểu, operand stack, local variables hoặc control flow không được thỏa mãn.
- **`ClassCircularityError`:** JVM phát hiện **vòng trong quan hệ kế thừa superclass hoặc superinterface** khi đang nạp/định nghĩa class hoặc interface.

Đây là **ba loại `Error` thuộc nhánh `LinkageError`**, không phải `ClassNotFoundException`. Nếu gặp chúng, cách xử lý thường là **sửa class file, phiên bản compiler/transformer hoặc đồ thị kế thừa**, thay vì chỉ thêm JAR vào classpath.

### Mục tiêu học tập

Sau bài này, bạn có thể:
1. Phân biệt lỗi cấu trúc class file, lỗi verifier và lỗi circular inheritance.
2. Hiểu lỗi có thể xuất hiện ở những thời điểm khác nhau trong loading/linking, không chỉ ngay tại lệnh `loadClass`.
3. Tái hiện **cả ba lỗi** bằng Java 17+ mà không cần thư viện ngoài.
4. Phân biệt `ClassCircularityError` với **vòng khởi tạo static**, **chu trình tham chiếu object**, và **deadlock giữa class loaders**.
5. Debug lỗi đúng theo runtime JDK, bytes đã triển khai, ClassLoader, bytecode transformation và dependency graph.

## 2. Cây kế thừa exception: vị trí của từng lỗi

```text
java.lang.Throwable
├── java.lang.Exception
│   └── ClassNotFoundException              [checked]
└── java.lang.Error
    └── LinkageError
        ├── ClassFormatError
        │   └── UnsupportedClassVersionError
        ├── VerifyError
        ├── ClassCircularityError
        ├── NoClassDefFoundError
        ├── IncompatibleClassChangeError
        │   ├── NoSuchMethodError
        │   ├── NoSuchFieldError
        │   └── IllegalAccessError
        └── ExceptionInInitializerError
```

Các nhánh trong sơ đồ chỉ liệt kê những loại liên quan tới bài; đây **không phải toàn bộ** các subclass của `LinkageError`.

Điều quan trọng:
- `ClassFormatError`, `VerifyError` và `ClassCircularityError` là **ba nhánh độc lập trực tiếp dưới `LinkageError`**.
- `UnsupportedClassVersionError` là **subclass của `ClassFormatError`**, dùng khi major/minor class-file version không được runtime hỗ trợ.
- `ClassNotFoundException` là một **checked exception** ở nhánh `Exception`: thể hiện yêu cầu tìm lớp không thành công theo API ClassLoader.
- `NoClassDefFoundError` thường xuất hiện khi JVM cần một định nghĩa không khả dụng hoặc một class đã khởi tạo thất bại; **không phải tên gọi thay thế** của cả ba lỗi đang xét.
- Các `Error` này bình thường **không nên bị bắt rồi nuốt** ở code nghiệp vụ; có thể phân loại tại boundary của plugin/container để báo lỗi artifact và từ chối kích hoạt plugin.

## 3. Lỗi phát sinh ở giai đoạn nào?

Một cách khái quát:

```text
Yêu cầu nạp: loadClass("plugin.Greeter")
                  │
                  ▼
      ClassLoader tìm bytecode
                  │
                  ├─ không tìm thấy ──► ClassNotFoundException
                  │
                  ▼
           defineClass(bytes)
                  │
                  ├─ cấu trúc class file hỏng
                  │      └──► ClassFormatError
                  │
                  ├─ major/minor version không hỗ trợ
                  │      └──► UnsupportedClassVersionError
                  │
                  ├─ vòng superclass/superinterface
                  │      └──► ClassCircularityError
                  │
                  └─ định nghĩa class
                         │
                         ▼
                Linking / verification
                         │
                         ├─ bytecode vi phạm verifier
                         │      └──► VerifyError
                         │
                         └─ class được dùng
                                 │
                                 ▼
                       initialization khi cần
```

Đây là **sơ đồ tư duy**, không phải thứ tự thời gian cố định mà mọi JVM phải thực hiện một cách đầy đủ trước mỗi lệnh. JVMS cho phép nhiều hoạt động liên kết/đặc biệt là resolution diễn ra **lười** trong giới hạn đặc tả.

Một `ClassLoader.loadClass(name)` trả được `Class<?>` không bảo đảm:
- toàn bộ method bytecode đã được sử dụng;
- mọi symbolic reference đã được resolution;
- static initializer đã chạy;
- mọi dependency sẽ tải thành công về sau.

Trong thử nghiệm Java ở mục 8, `VerifyError` được kích hoạt chắc chắn bằng cách **dùng reflection gọi method chứa bytecode hỏng**, chứ không suy rằng chỉ gọi `loadClass` đã bắt buộc verification mọi thứ.

## 4. ClassFormatError: class file không hợp lệ về cấu trúc

### 4.1. Class file là một định dạng nhị phân có quy tắc

Theo JVM Specification, phần đầu của class file có dạng:

```text
u4 magic                 // 0xCAFEBABE
u2 minor_version
u2 major_version
u2 constant_pool_count
cp_info constant_pool[constant_pool_count - 1]
u2 access_flags
u2 this_class
u2 super_class
...
```

Class file còn có tables interfaces, fields, methods và attributes; các constant-pool entries tham chiếu nhau bằng indices có quy tắc. File mang đuôi `.class` nhưng thực chất là HTML response, ZIP/JAR, text, tệp tải dở hoặc bytes từ transformer bị lỗi không trở thành class hợp lệ nhờ tên file.

**Ví dụ nguyên nhân thường gặp:**

1. Magic number không phải `CAFEBABE`.
2. File thiếu bytes, bị truncate, đọc nhầm offset hoặc còn dữ liệu ở định dạng nén khác.
3. Constant pool tag/count/index không hợp lệ.
4. Các bảng/attributes có độ dài hoặc cấu trúc sai.
5. Tên descriptor, field/method attributes hoặc class-file constraints không thỏa mãn.
6. Bytecode generator/shader/obfuscator/agent làm hỏng phần class file.
7. Loader đọc nhầm resource vì map từ binary name sang file path sai.

`ClassFormatError` biểu thị **dữ liệu có vấn đề**, không phải `ClassLoader` chắc chắn không tìm được dữ liệu.

### 4.2. ClassFormatError và UnsupportedClassVersionError

`UnsupportedClassVersionError` là lỗi con của `ClassFormatError`, khi version trong class file không được JVM hỗ trợ. Các mốc thường gặp:

| Java source/runtime release | Major version |
|---|---:|
| Java 8 | 52 |
| Java 11 | 55 |
| Java 17 | 61 |
| Java 21 | 65 |
| Java 25 | 69 |

Một ứng dụng chạy Java 17 mà đọc bytes class được build cho Java 21 (major 65) có thể gặp `UnsupportedClassVersionError`. Không nên chẩn đoán thành lỗi “thiếu JAR”.

**Cách xử lý:** so `java -version` với `javac -version`, kiểm tra đúng class file đang deploy, biên dịch `javac --release 17` khi mã và dependencies cho phép, hoặc nâng runtime.

`minor_version` cũng có ý nghĩa, nhất là class files gắn **preview features**. Đừng đổi vài bytes version trong file để “tương thích” runtime cũ: opcode, constant pool, API và semantics có thể khác.

### 4.3. Ví dụ phản trực giác

Một custom loader:

```java
// Trích đoạn trong findClass(String name):
byte[] bytes = Files.readAllBytes(classFile);
return defineClass(name, bytes, 0, bytes.length);
```

Nếu path tồn tại và `bytes.length > 0`, chỉ chứng minh đã đọc được bytes. **Không chứng minh** dữ liệu hợp lệ, thuộc đúng binary name, có đúng version hoặc qua verifier.

Kiểm tra magic `CAFEBABE` là một precheck tốt nhưng **không thay thế** JVM verification/format validation.

## 5. VerifyError: class file có thể parse được nhưng bytecode không hợp lệ

### 5.1. Verifier kiểm tra điều gì?

Bytecode verifier xác minh các điều kiện an toàn của code bên trong methods, liên quan:

- Loại giá trị trên **operand stack** tương thích với opcode đang sử dụng.
- Độ sâu stack không âm và không vượt các giới hạn hợp lệ.
- Các giá trị trong **local variables** được sử dụng đúng kiểu/trạng thái.
- Các nhánh `if`, `goto` và exception handler có trạng thái stack/local variables phù hợp tại nơi giao nhau.
- Các quy tắc liên quan `StackMapTable` của class-file versions áp dụng được đáp ứng.
- Constructor và giá trị `uninitializedThis`/`uninitialized` không bị sử dụng sai.
- Giá trị trả về có kiểu đúng với method descriptor, ví dụ method `()I` phải trả `int`.
- Các điều kiện verification khác theo JVMS.

### 5.2. Ví dụ bytecode sai kiểu trả về

Java source hợp lệ:

```java
public class VerifyTarget {
    public static int value() {
        return 1;
    }
}
```

Compiler có thể sinh bytecode cho method `value`:

```text
iconst_1  // đưa int 1 lên stack
ireturn   // trả int từ operand stack
```

Nếu một transformer cố ý đổi opcode `iconst_1` thành `aconst_null` nhưng giữ nguyên `ireturn`, stack chứa **reference null** trong khi `ireturn` đòi **int**:

```text
aconst_null  // reference
ireturn      // yêu cầu int
             // → verifier phát hiện xung đột kiểu
```

`VerifyError` là lỗi đúng bản chất ở đây: phần cấu trúc class file có thể vẫn parse được, nhưng **method instructions vi phạm typing constraints**.

Đây **không phải lỗi compiler Java nguồn thông thường**. `javac` tự kiểm tra type safety và sinh bytecode hợp lệ. Các trường hợp VerifyError trong production thường gợi ý:
- class file bị chỉnh sửa sau compilation;
- bytecode instrumentation không cập nhật stack map frames;
- công cụ code generation/obfuscation làm sai control flow;
- mismatch version của agent/framework với class-file version được xử lý.

### 5.3. Không “sửa” bằng cách bỏ verifier

Không khuyến nghị dùng option cũ để vô hiệu hóa verification hoặc sửa sai bytecode bằng việc bỏ các checks. Một số flags đã bị loại bỏ hoặc không còn được hỗ trợ ở JDK mới. Việc tắt verifier có thể gây crash hoặc hành vi không an toàn; cần sửa bytes/transformer.

## 6. ClassCircularityError: quan hệ kế thừa bị vòng

`ClassCircularityError` được JVM ném khi **phát hiện vòng trong superclass/superinterface hierarchy** trong quá trình load/derive một class hoặc interface.

Ví dụ không hợp lệ ở mức đồ thị:

```text
class A extends B
class B extends A

A ──superclass──▶ B
▲                 │
└────superclass───┘

→ ClassCircularityError
```

Hoặc một interface graph vòng:

```text
interface X extends Y
interface Y extends X

X ──superinterface──▶ Y
▲                    │
└────superinterface──┘
```

**Java source compiler bình thường từ chối các chu trình kế thừa**. Vì vậy lỗi này thường liên quan tới:
- bytecode đã bị chỉnh sửa/sinh động, không đi qua kiểm tra Java source bình thường;
- transformer thao tác sai `super_class` hoặc `interfaces` trong class file;
- class-loader setup và các class definitions phối hợp không đúng khi load graph;
- artifact hỏng hoặc generator tạo quan hệ type hierarchy không hợp lệ.

### 6.1. Dòng nào trong class file quyết định superclass?

Sau constant pool và `access_flags`, class file có:

```text
u2 this_class      // index của class đang được định nghĩa
u2 super_class     // index của superclass (0 chỉ trong trường hợp đặc biệt)
```

`this_class` và `super_class` tham chiếu tới các `CONSTANT_Class_info` entries. Nếu sửa `super_class` của `CycleTarget` để tham chiếu **chính `this_class`**, class file mô tả một lớp **extends chính nó**. JVM phát hiện vòng đó khi tạo class và ném `ClassCircularityError`.

### 6.2. Đừng nhầm với circular dependency thông thường

Những tình huống sau **không mặc nhiên gây `ClassCircularityError`**:

- Class A giữ field kiểu B, và B giữ field kiểu A.
- Hai object tham chiếu lẫn nhau.
- Method A gọi method B và B gọi lại A.
- Static initializer A đọc static field của B, B đọc lại A.
- Hai class loaders ủy quyền chéo và các threads bị deadlock.
- Hai classes cùng tên được định nghĩa bởi hai loaders khác nhau.

Lỗi cần **vòng trong quan hệ superclass/superinterface tại quá trình nạp/định nghĩa**. Việc hai class có phụ thuộc lẫn nhau về fields, methods hoặc data **không phải quan hệ kế thừa**.

## 7. Tổng hợp ba lỗi: dấu hiệu và hướng xử lý

| Lỗi | JVM phát hiện | Ví dụ nguồn gốc | Kiểm tra đầu tiên |
|---|---|---|---|
| `ClassFormatError` | Class file sai định dạng/ràng buộc cấu trúc | Magic sai, constant pool lỗi, truncate | Header, bytes thực, transformer |
| `VerifyError` | Method bytecode không thỏa verification | `ireturn` khi stack là reference; frames sai | `javap -v -c`, StackMapTable, instrumentation |
| `ClassCircularityError` | Vòng superclass/superinterface hierarchy | A extends B extends A, self-superclass | `super_class`, interfaces, type graph |
| `UnsupportedClassVersionError` | Major/minor không được JVM hỗ trợ | JDK runtime cũ hơn target | `java -version`, `javap -verbose` |
| `ClassNotFoundException` | Loader không tìm thấy requested class | Tên sai, URL/path thiếu JAR | ClassLoader hierarchy, classpath |
| `NoClassDefFoundError` | JVM cần class definition không khả dụng hoặc class init từng lỗi | Thiếu dependency, wrong name, erroneous class | Cause chain, loader, init log |

Có thể có **nhiều lỗi nối tiếp**: class file bị hỏng khiến một class không thể load; về sau class phụ thuộc vào nó có thể báo một `LinkageError` khác. Luôn tìm **lỗi đầu tiên và artifact thực tế**, không chỉ đọc message ở cuối log.

## 8. Thí nghiệm Java 17+ tái hiện chính xác cả ba lỗi

Thí nghiệm này cố ý thay đổi bytes **trong bộ nhớ**; **không** ghi đè file `.class` thật của dự án và **không cần ASM/Byte Buddy**. Ta biên dịch hai class hoàn toàn hợp lệ:

1. `VerifyTarget` có method trả về `int 1`.
2. `CycleTarget` extends `Object`.

Sau đó một host loader dùng bytes biến đổi:
- bytes ngẫu nhiên → `ClassFormatError`;
- opcode `iconst_1` bị đổi thành `aconst_null` → `VerifyError`;
- `super_class` được đổi thành `this_class` → `ClassCircularityError`.

Demo dùng **ClassLoader khác cho mỗi trường hợp**, tránh trạng thái `findLoadedClass` từ lượt trước. Chương trình chạy trên **Java 17+**; kết quả bên dưới được kiểm chứng khi dùng `javac --release 17` và chạy trên OpenJDK 21.

### 8.1. File VerifyTarget.java

```java
public class VerifyTarget {
    public static int value() {
        return 1;
    }
}
```

### 8.2. File CycleTarget.java

```java
public class CycleTarget {
}
```

Cả hai là classes ở **default package** chỉ để tối giản demo. Trong hệ thống thật, dùng package rõ ràng cho plugin và tránh default package.

### 8.3. File ErrorKindsDemo.java

```java
import java.nio.file.Files;
import java.nio.file.Path;

public class ErrorKindsDemo {

    static final class BytesLoader extends ClassLoader {
        private final String target;
        private final byte[] bytes;

        BytesLoader(String target, byte[] bytes) {
            super(ErrorKindsDemo.class.getClassLoader());
            this.target = target;
            this.bytes = bytes;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!name.equals(target)) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    static int u2(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8)
                | (bytes[offset + 1] & 0xff);
    }

    static byte[] badFormat() {
        // Không phải magic CAFEBABE / class file hợp lệ.
        return new byte[] {0, 1, 2, 3, 4, 5};
    }

    static byte[] badVerify(byte[] input) {
        byte[] bytes = input.clone();
        int match = -1;

        // Với method "return 1;" khi javac --release 17,
        // iconst_1; ireturn được mã hóa là 04 ac.
        for (int i = 0; i + 1 < bytes.length; i++) {
            if ((bytes[i] & 0xff) == 0x04
                    && (bytes[i + 1] & 0xff) == 0xac) {
                if (match != -1) {
                    throw new IllegalStateException(
                            "Not unique sequence");
                }
                match = i;
            }
        }

        if (match == -1) {
            throw new IllegalStateException(
                    "Bytecode sequence missing");
        }

        // iconst_1 (int) -> aconst_null (reference),
        // nhưng ireturn phía sau vẫn yêu cầu int.
        bytes[match] = 0x01;
        return bytes;
    }

    static byte[] badCycle(byte[] input) {
        byte[] bytes = input.clone();
        int pos = 8; // Sau magic, minor_version, major_version.
        int cpCount = u2(bytes, pos);
        pos += 2;

        // Duyệt constant pool để tới access_flags/this/super.
        for (int i = 1; i < cpCount; i++) {
            int tag = bytes[pos++] & 0xff;
            switch (tag) {
                case 1 -> {
                    int length = u2(bytes, pos);
                    pos += 2 + length;
                }
                case 3, 4, 9, 10, 11, 12, 17, 18 ->
                        pos += 4;
                case 5, 6 -> {
                    pos += 8;
                    i++; // long/double chiếm hai CP slots.
                }
                case 7, 8, 16, 19, 20 -> pos += 2;
                case 15 -> pos += 3;
                default -> throw new IllegalArgumentException(
                        "Unsupported constant tag: " + tag);
            }
        }

        pos += 2; // access_flags
        int selfIndex = u2(bytes, pos); // this_class
        pos += 2; // super_class offset

        // Biến superclass thành chính class này.
        bytes[pos] = (byte) (selfIndex >>> 8);
        bytes[pos + 1] = (byte) selfIndex;

        return bytes;
    }

    static void inspect(String title, String className, byte[] bytes)
            throws Exception {
        try {
            Class<?> type = Class.forName(
                    className,
                    true,
                    new BytesLoader(className, bytes)
            );

            // Ép trường hợp VerifyTarget sử dụng method:
            // JVM có thể trì hoãn verification tới khi cần.
            if ("VerifyTarget".equals(className)) {
                type.getMethod("value").invoke(null);
            }
            System.out.println(title + " -> unexpectedly loaded");

        } catch (LinkageError error) {
            System.out.println(
                    title + " -> "
                    + error.getClass().getSimpleName()
            );

        } catch (ClassNotFoundException error) {
            System.out.println(
                    title + " -> ClassNotFoundException"
            );
        }
    }

    public static void main(String[] args) throws Exception {
        inspect(
                "bad format",
                "BrokenFormat",
                badFormat()
        );

        inspect(
                "bad verifier",
                "VerifyTarget",
                badVerify(Files.readAllBytes(
                        Path.of("inputs/VerifyTarget.class")))
        );

        inspect(
                "circular superclass",
                "CycleTarget",
                badCycle(Files.readAllBytes(
                        Path.of("inputs/CycleTarget.class")))
        );
    }
}
```

### 8.4. Tạo thư mục, biên dịch, chạy

Đặt ba source files trong một thư mục làm việc:

```text
classloader-errors/
├── VerifyTarget.java
├── CycleTarget.java
├── ErrorKindsDemo.java
├── inputs/
└── host/
```

Trên Bash/Linux/macOS:

```bash
mkdir -p inputs host
```

Trên Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force inputs,host | Out-Null
```

Biên dịch với JDK 17+ nhưng nhắm class-file target Java 17:

```bash
javac --release 17 -d inputs VerifyTarget.java CycleTarget.java
javac --release 17 -d host ErrorKindsDemo.java
java -cp host ErrorKindsDemo
```

**Không thêm thư mục `inputs` vào `-cp` của host.** Nếu làm vậy, parent application loader có thể định nghĩa `VerifyTarget` hoặc `CycleTarget` trước khi `BytesLoader.findClass` được gọi, khiến demo không sử dụng bytes bị sửa.

### 8.5. Stdout đã kiểm chứng

```text
bad format -> ClassFormatError
bad verifier -> VerifyError
circular superclass -> ClassCircularityError
```

Chương trình chỉ in **simple class name** của error để tránh phụ thuộc vào message chi tiết khác nhau giữa các JDK/runtime.

### 8.6. Tại sao từng nhánh phát sinh lỗi?

**Nhánh bad format:**
- `badFormat()` tạo mảng 6 bytes không có magic `CAFEBABE` và không có cấu trúc class file.
- `BytesLoader.findClass("BrokenFormat")` gọi `defineClass`.
- JVM từ chối bytes, nên `ClassFormatError` được ném.

**Nhánh bad verifier:**
- File `VerifyTarget.class` gốc được `javac --release 17` sinh hợp lệ.
- `badVerify` tìm đúng chuỗi opcode `04 ac` dành cho `iconst_1; ireturn` trong target demo và đổi `04` thành `01` (`aconst_null`).
- File vẫn có header, constant pool và method structure, nhưng instruction sequence trả reference ở vị trí đòi int.
- `Class.forName(..., true, ...)` và reflective invocation khiến JVM phải kiểm tra/sử dụng method; verifier phát hiện bất hợp lệ, ném `VerifyError`.

**Nhánh circular superclass:**
- `badCycle` đọc class-file constant pool đủ để tìm offset của `this_class` và `super_class`.
- Chương trình đổi `super_class` thành cùng index với `this_class`.
- Đây là class-file graph có superclass trỏ vào chính lớp đó, tạo circularity.
- JVM phát hiện lỗi khi tạo class và ném `ClassCircularityError`.

### 8.7. Các giới hạn của demo

- `badVerify` cố ý dựa vào pattern `04 ac` của **class được biên dịch từ source trên với `--release 17`**. Nếu bạn thay method, chạy obfuscator hoặc compiler sinh bytecode khác, chương trình chủ động ném `IllegalStateException` thay vì âm thầm sửa sai bytes. Với production transformer, phải parse Code attributes theo spec bằng công cụ thích hợp, không scan raw bytes kiểu này.
- `badCycle` là parser **rút gọn** chỉ để đi qua constant pool đến `super_class`. Nó xử lý tags cần cho class-file format thông dụng; không phải class-file reader bảo mật/thương mại đầy đủ, không validate mọi độ dài hay dữ liệu đầu vào. Dùng nó trên **input tự biên dịch hợp lệ** như hướng dẫn.
- Reflection có thể gây các ngoại lệ khác như `NoSuchMethodException`/`InvocationTargetException` nếu bạn sửa source để không còn method phù hợp. Đừng dùng ví dụ này làm loader tổng quát cho class bất kỳ.
- `BytesLoader` không định nghĩa package/lớp từ một JAR thật; nó chỉ dùng `defineClass` trên mảng bytes để minh họa ba lỗi.
- Không dùng các tệp bytes đã cố ý phá hỏng trong ứng dụng thật hoặc phát hành chúng như artifacts.

## 9. Thí nghiệm đối chứng: vòng static initialization không phải ClassCircularityError

Java cho phép hai class cùng tham chiếu tới **static fields của nhau**. Điều đó **không** tạo chu trình kế thừa superclass/superinterface.

Tạo `InitializationCycleDemo.java`:

```java
public class InitializationCycleDemo {
    static final class First {
        static int a = Second.b + 1;
    }

    static final class Second {
        static int b = First.a + 1;
    }

    public static void main(String[] args) {
        System.out.println("First.a=" + First.a);
        System.out.println("Second.b=" + Second.b);
    }
}
```

Chạy:

```bash
javac --release 17 InitializationCycleDemo.java
java InitializationCycleDemo
```

Stdout:

```text
First.a=2
Second.b=1
```

Giải thích:

1. Khi `main` đọc `First.a`, JVM bắt đầu initialization của `First`. `First.a` ban đầu có giá trị mặc định `0` trước khi được gán bởi initializer.
2. `First.a = Second.b + 1` kích hoạt initialization của `Second`.
3. `Second.b = First.a + 1` tham chiếu `First.a` trong khi `First` đang được initialized **bởi cùng thread**. JVM không khởi tạo lại First; giá trị `First.a` lúc này vẫn là `0`.
4. `Second.b` được gán `1`, rồi quay lại `First.a` được gán `2`.
5. Hai biến có giá trị `2` và `1`. **Không hề có lớp nào extends chính mình hoặc vòng superclass.**

Đây là trường hợp **trạng thái initialization chưa hoàn tất**, không phải class circularity. Với nhiều threads, circular initialization có thể dẫn đến chờ/deadlock tùy code đồng bộ; nhưng ngay cả deadlock đó cũng không đồng nghĩa `ClassCircularityError`.

## 10. Circular loader delegation khác circular type hierarchy

Một custom loader có thể được cấu hình ủy quyền chéo:

```text
Loader-A.loadClass("x.Service")
    └── Loader-B.loadClass("x.Service")
           └── Loader-A.loadClass("x.Service")
                  └── ...
```

Đây là **chu trình trong thuật toán tìm class**, có thể gây recursion quá sâu, `StackOverflowError` hoặc deadlock do locking, tùy code. Không nhất thiết có circular superclass/superinterface relation.

Ngược lại:

```text
class A extends B
class B extends A
```

là **chu trình trong type hierarchy**. JVM báo `ClassCircularityError` khi phát hiện theo thuật toán class creation/loading.

Phân biệt hai loại graph:
- **Loader delegation graph:** loader nào hỏi loader nào.
- **Superclass/superinterface graph:** class/interface nào extends class/interface nào.

Một chương trình có thể có cả hai vấn đề, nhưng debug phải xác định vòng thực sự nằm ở đâu.

## 11. Trường hợp lỗi xuất hiện sau khi loadClass đã thành công

Trong JVM, **class loading, verification, resolution và initialization** có thể xảy ra ở các thời điểm khác nhau. Do đó:

```java
// Trích đoạn: loader là ClassLoader do ứng dụng quản lý.
Class<?> type = loader.loadClass("demo.Plugin");
System.out.println(type.getName()); // Có thể thành công.

// Lỗi có thể chỉ xuất hiện ở một sử dụng sau đó:
type.getDeclaredMethods();
```

Đây là **đoạn minh họa, không phải file chạy độc lập**. Không cam kết `getDeclaredMethods()` luôn gây `VerifyError` nếu class hỏng; tùy loại vi phạm và JVM timing. Ý chính: **không xác nhận plugin an toàn chỉ từ một lần `loadClass` không ném lỗi**.

Trong hệ plugin:
- Một method được gọi hiếm gặp có thể chỉ được verify/resolve muộn.
- Một dependency mới có thể chỉ được load khi tính năng đó được kích hoạt.
- AOP/agent transformer có thể chỉ áp dụng cho các class lần đầu được yêu cầu sau startup.
- Test chỉ nạp entry-point class chưa bao phủ toàn bộ provider implementations.

Muốn đảm bảo hơn, chạy integration tests theo các code paths quan trọng, không chỉ gọi `Class.forName` lên một class rồi tuyên bố artifact đúng.

## 12. Vai trò của custom ClassLoader đối với ba lỗi

Một custom loader thường đọc bytes rồi định nghĩa class:

```java
// Trích đoạn bên trong ClassLoader subclass:
@Override
protected Class<?> findClass(String name)
        throws ClassNotFoundException {
    String path = name.replace('.', '/') + ".class";
    byte[] bytes;

    try {
        bytes = Files.readAllBytes(root.resolve(path));
    } catch (IOException cause) {
        throw new ClassNotFoundException(name, cause);
    }

    return defineClass(name, bytes, 0, bytes.length);
}
```

Đây là **trích đoạn**; cần `root`, imports và class loader superclass thích hợp. Ranh giới trách nhiệm:

- `Files.readAllBytes`: giải quyết lỗi I/O của nguồn bytes.
- `defineClass`: yêu cầu JVM đọc cấu trúc class file và tạo runtime class.
- Verification/linking: do JVM thực hiện theo quy tắc.
- ClassLoader không được mặc nhiên bỏ qua `LinkageError` hay “sửa” bytes bằng exception mapping.

**Anti-pattern**:

```java
// KHÔNG dùng trong production:
try {
    return defineClass(name, bytes, 0, bytes.length);
} catch (Throwable ignored) {
    throw new ClassNotFoundException(name);
}
```

Đây là trích đoạn lỗi. Nó xóa thông tin về:
- `ClassFormatError`;
- `VerifyError`;
- `ClassCircularityError`;
- `OutOfMemoryError` và những errors nghiêm trọng khác.

Người debug sẽ tưởng rằng class chỉ thiếu file và thử đổi classpath vô ích. **Chỉ chuyển những lỗi I/O thực sự thuộc lookup path thành `ClassNotFoundException` khi hợp đồng loader phù hợp, giữ original cause.**

## 13. ClassFormatError còn có thể xuất hiện khi magic đúng

Chỉ nhìn `0xCAFEBABE` là chưa đủ.

Ví dụ những dữ liệu vẫn bắt đầu bằng magic hợp lệ nhưng có thể bị JVM từ chối:
- `constant_pool_count` mô tả nhiều entries hơn bytes còn lại.
- Một `CONSTANT_Class_info` tham chiếu đến CP slot không tồn tại hoặc tag sai.
- `Code` attribute khai báo length không khớp nội dung.
- Method signature/descriptor sai grammar.
- Trùng/không hợp lệ ở những class-file structures bị đặc tả cấm.
- Các flags, arrays và attributes không đáp ứng class file constraints.

Một phần lỗi có thể có tên `ClassFormatError` hoặc một lỗi cụ thể khác theo điều kiện mà JVM phát hiện; **đừng khẳng định mọi thao tác sửa bytes đều dẫn đúng một loại Error cố định**.

Muốn kiểm tra:
- Đối chiếu file bytes với artifact gốc từ build output và artifact cuối trên disk/JAR.
- Sử dụng `javap -verbose` để xem CP và attributes nếu tool parse được.
- Nếu có transformer, so sánh **before/after bytes**, không chỉ source Java.
- Dùng checksum để tránh đang debug nhầm JAR hoặc phiên bản đã được container cache.

## 14. VerifyError thường đi cùng StackMapTable như thế nào?

Đối với class files hiện đại, verifier sử dụng thông tin từ `StackMapTable` theo quy tắc JVMS. Bytecode transformers cần giữ các invariants khi:
- thêm/xóa opcode;
- thay branch destinations;
- thêm try/catch handlers;
- thay local variable slots hoặc operand-stack shapes;
- đổi method descriptor/return type;
- thay constructor initialization path.

Ví dụ transformer chèn thêm nhánh:

```text
if (condition) goto L1
...
L1:
   dùng local variable mà một đường trước đó
   chưa xác định đúng type
```

Nếu các control-flow paths hợp nhất với trạng thái stack/local variables không tương thích, JVM có thể ném `VerifyError`. Đó là lỗi **bytecode verification**, dù class file header và CP vẫn hợp lệ.

Một số tool như ASM có công cụ recompute frames (`COMPUTE_FRAMES`) và max stack. Tuy nhiên bật tính năng tính frames không tự sửa một thuật toán transform sai: phải bảo đảm method semantics và exception handlers hợp lệ.

### Không nhầm với lỗi binary API

Nếu method bytecode **qua verifier**, nhưng runtime library thiếu một method từng có lúc compile, lời gọi sau đó có thể ném `NoSuchMethodError`. Nếu method static/instance contract thay đổi, có thể là `IncompatibleClassChangeError`. Nếu access thay đổi, có thể `IllegalAccessError`.

Đây là những **linkage issues khác**, không phải `VerifyError` chỉ vì chúng phát sinh khi dùng method.

## 15. ClassCircularityError và tính đúng đắn của hierarchy

Một JVM runtime class có một superclass chain theo quy tắc, và interfaces có các superinterfaces. Chuỗi/đồ thị này không được tạo vòng bất hợp lệ.

Ví dụ:
- `A extends B`;
- `B extends C`;
- `C extends A`.

Một source compiler bình thường sẽ từ chối quan hệ như vậy nếu toàn bộ source cùng được phân tích. Nhưng công cụ sinh/ghép class files độc lập hoặc deploy mismatched artifacts có thể tạo bytes trình bày một đồ thị kế thừa vòng.

Khi JVM yêu cầu định nghĩa `A`, nó cần xác định các kiểu super liên quan. Nếu phát hiện class đang cần định nghĩa lại trong chính chuỗi super, **`ClassCircularityError`** phản ánh vòng superclass/superinterface.

### Circularity có phải deadlock?

Không. `ClassCircularityError` là một loại `LinkageError` mà JVM ném ra để báo một type hierarchy không hợp lệ; deadlock là các threads khóa/chờ nhau mà có thể không ném exception.

### Object graph có cycle có sai không?

Không. Hai objects giữ reference nhau là tình huống thường gặp và GC hỗ trợ thu gom cả chu trình khi không reachable từ roots:

```java
class Node {
    Node next;
}
```

Đoạn trên hợp lệ. `a.next=b; b.next=a;` **không liên quan** đến `ClassCircularityError`.

## 16. Kỹ thuật chẩn đoán theo bước

### Bước 1 — Lấy error gốc, không chỉ đọc lớp wrapper

Trong plugin manager, exception từ reflection có thể được bọc bởi `InvocationTargetException`, `ExecutionException` hoặc framework-specific exceptions. Log:
- Loại Throwable thực tế;
- message, stack trace;
- cause chain;
- suppressed exceptions khi có;
- class binary name đang được yêu cầu;
- class loader identity và source URL/JAR.

Ví dụ chỉ ghi `"Error loading plugin"` là chưa đủ.

### Bước 2 — Xác định bytes thực tế

Nếu class xuất hiện trong nhiều JARs, classpath/module path hoặc parent/child loaders, phải biết **file nào thực sự được định nghĩa**. Lưu:
- JAR path và entry;
- version/build hash;
- actual class file bytes hoặc checksum khi có;
- bản bytes trước/sau instrumentation;
- defining loader của các classes load thành công liên quan.

Đừng chỉ nhìn `src/` của dự án rồi suy artifact cuối cũng giống source.

### Bước 3 — So JDK compile và runtime

```bash
java -version
javac -version
```

Nếu phiên bản khác nhau, inspect target compilation. Thông tin như major version rất hữu ích:

```bash
javap -verbose -classpath inputs VerifyTarget
```

Với `javac --release 17`, `major version` thông thường là `61`.

### Bước 4 — Phân biệt ba nhánh

**Nếu `ClassFormatError`:**
- Kiểm tra magic/header, size, constant pool, class-file attributes.
- Xem có đọc nhầm JAR/HTML/resource khác không.
- So artifacts trước/sau transformer.
- Kiểm tra `UnsupportedClassVersionError` trước vì nó là subtype cụ thể.

**Nếu `VerifyError`:**
- Dùng `javap -c -v` để xem opcode và `StackMapTable`.
- Xác định method hoặc control-flow location mà JVM báo.
- So bytecode trước/sau agent, shading, obfuscation.
- Kiểm tra phiên bản bytecode tool hỗ trợ class-file target.

**Nếu `ClassCircularityError`:**
- Xác định class/interface được nhắc trong message.
- Truy `super_class`/`interfaces` từ class file.
- Dựng đồ thị superclass/superinterface các artifacts thực tế; tìm vòng.
- Phân biệt vòng trong loader delegation graph với vòng ở type hierarchy.

### Bước 5 — Tái hiện trong loader và runtime sạch

Nếu class đã được định nghĩa thành công trên một loader, `findLoadedClass` có thể trả class cũ thay vì đọc lại file đã được chỉnh sửa. Khi debug bytecode variants:
- tạo **ClassLoader mới** hoặc chạy JVM process mới;
- bảo đảm parent không có sẵn class cùng tên;
- tách riêng classpaths của host và plugin;
- giữ đúng JVM arguments/agent/plugin classpath từ môi trường xảy ra lỗi.

Đừng khẳng định “file đã được sửa nhưng lỗi vẫn y nguyên” nếu JVM thực tế đang dùng runtime class đã load từ trước.

## 17. Log/quan sát JVM bằng công cụ phù hợp

Trên HotSpot hỗ trợ unified logging:

```bash
java -Xlog:class+load=info -cp host ErrorKindsDemo
```

Lệnh giả định đã tạo `host`/`inputs` theo mục 8 và chạy trong đúng thư mục. Log class loading có thể rất dài; nó **không phải** log đầy đủ của verification/resolution hoặc các bước mà custom loader thực hiện.

Có thể dùng:

```bash
javap -verbose -classpath inputs VerifyTarget
javap -c -classpath inputs VerifyTarget
```

`javap` cho thấy **class gốc trước khi chương trình biến đổi bytes trong bộ nhớ**. Nếu cần xem **bytes bị sửa**, hãy dump một bản tạm ở môi trường test rồi inspect bản đó; tránh ghi đè artifact hợp lệ đang được ứng dụng chạy.

Một custom loader có thể log:

```java
// Trích đoạn trong findClass, trước defineClass:
System.out.println(
        "requested=" + name
        + ", loader=" + this.getClass().getName()
        + ", bytes=" + bytes.length
);
```

Không log raw bytecode hoặc đường dẫn chứa secrets/credential ở production nếu không cần. File checksum thường hữu ích hơn việc in toàn bộ bytes.

## 18. Cách bắt lỗi đúng ở plugin boundary

Ở lớp điều phối plugin, đôi khi cần chuyển JVM errors thành **diagnostic report** thay vì để toàn bộ host chết ngay. Đây là đoạn minh họa:

```java
// TRÍCH ĐOẠN: loader và binaryName là biến sẵn có,
// report(...) là method của plugin manager.
try {
    Class<?> type = loader.loadClass(binaryName);
    activatePlugin(type);

} catch (UnsupportedClassVersionError error) {
    report("Unsupported class file version", error);

} catch (ClassFormatError error) {
    report("Malformed class file", error);

} catch (VerifyError error) {
    report("Bytecode verification failed", error);

} catch (ClassCircularityError error) {
    report("Circular superclass/superinterface hierarchy", error);

} catch (ClassNotFoundException error) {
    report("Class not found by configured loader", error);

} catch (LinkageError error) {
    report("Other JVM linkage error", error);
}
```

**Đây không phải source biên dịch độc lập:** `activatePlugin`, `report`, `loader` và `binaryName` thuộc code ứng dụng.

Những quy tắc thiết yếu:
- **Thứ tự catch:** `UnsupportedClassVersionError` trước `ClassFormatError` vì nó là subclass. Những lỗi cụ thể trước `LinkageError`.
- `ClassNotFoundException` là checked exception, giữ original cause từ I/O/lookup khi phù hợp.
- Đừng catch `Error` hay `Throwable` rộng rồi “cho plugin chạy tiếp như không có gì xảy ra”.
- Một plugin đã thất bại loading/linking có thể không an toàn để tiếp tục; hãy đánh dấu failure state và không kích hoạt instance một phần.
- Cleanup các tài nguyên đã đăng ký trong lúc plugin start (listeners, tasks, classloader) theo lifecycle manager; xử lý được error diagnostic không có nghĩa mọi tác dụng phụ trước error được rollback.
- Nếu thấy `OutOfMemoryError`, `StackOverflowError`, `ThreadDeath` hoặc VM errors khác, chúng **không phải** các trường hợp để fallback tùy tiện sang một JAR khác.

**Không convert `ClassFormatError`/`VerifyError`/`ClassCircularityError` thành `ClassNotFoundException`.** Làm vậy che nguyên nhân kỹ thuật và thường dẫn đến sửa classpath sai chỗ.

## 19. Cơ chế nạp class cha-con không được thay thế nguyên nhân

Loader mặc định thực hiện:

```text
findLoadedClass(name)
   ↓ chưa có
parent.loadClass(name)
   ↓ nếu parent không tìm thấy
findClass(name)
   ↓ defineClass(bytes)
```

Lưu ý khi parent loader ném `ClassFormatError`:
- Đây **không phải `ClassNotFoundException`**.
- Thuật toán `loadClass` chuẩn **không tự catch mọi LinkageError của parent rồi chuyển sang child**.
- Nếu custom loader làm vậy, nó có thể che một bản artifact hỏng ở parent và tùy tiện load bản khác, làm tăng rủi ro class identity, package mismatch hoặc binary compatibility.

Trường hợp thật sự cần tìm từ child trước parent phải định nghĩa **delegation policy** rõ ràng theo namespace và tuân ràng buộc JVM. Không dùng child-first như một “thuốc chữa” chung cho bytecode hỏng.

## 20. Một bảng “triage” để đọc stack trace

| Stack trace có gì? | Điều ưu tiên xác nhận | Điều không nên làm |
|---|---|---|
| `ClassFormatError: Incompatible magic value ...` | Dữ liệu class file có đúng nguồn/format? | Chỉ thêm JAR mà không xem bytes |
| `UnsupportedClassVersionError: class file version ...` | Target major/minor và runtime JDK | Sửa vài byte header cho version thấp |
| `VerifyError: Bad type on operand stack` | Method bytecode, frames, transformer | Tắt verification |
| `VerifyError: Expecting a stackmap frame ...` | Branch target, StackMapTable, Java target | Đoán là ClassNotFoundException |
| `ClassCircularityError: ...` | Superclass/interface graph thực tế | Xem mọi reference field như “inheritance cycle” |
| `NoClassDefFoundError: ...` | Missing dependency, wrong name hoặc init cũ thất bại | Mặc định coi là VerifyError |
| `ClassNotFoundException: ...` | Loader search path/binary name/parent | Mặc định coi file hỏng |
| `NoSuchMethodError` | Compile/runtime ABI của dependency | Sửa StackMapTable vô căn cứ |

Message và stack-trace frames cụ thể là **VM/version-specific**. Bảng dùng để gợi ý hướng tra cứu, không khẳng định string message sẽ giống hệt trên mọi JVM.

## 21. Các lỗi phổ biến khi tự tạo hoặc biến đổi bytecode

Ba lỗi chính cần xử lý bằng **bytes nguồn** và **quy tắc JVM phù hợp**:

| Tình huống thực tế | Dạng lỗi cần phân loại | Cách sửa |
| --- | --- | --- |
| Custom loader lấy nhầm HTML/ZIP hoặc class file bị truncate | `ClassFormatError` | Kiểm tra resource URL, magic, constant pool/attributes và bytes đầu vào `defineClass` |
| Transformer thay branches, operand stack hoặc exception handlers nhưng không cập nhật frames | `VerifyError` | Kiểm tra `Code`, `StackMapTable`, paths và dùng generator tương thích với target JDK |
| Transformer viết `super_class` bằng chính `this_class` hoặc tạo A↔B inheritance | `ClassCircularityError` | Phân tích superclass/superinterface graph từ class files thực tế |

Các tình huống liên quan nhưng **không cùng cơ chế**: runtime Java 17 nhận bytecode major 65 là `UnsupportedClassVersionError`; vòng `static` initialization không phải circular superclass; parent/child loader delegation recursion không tự tạo `ClassCircularityError`.

Nếu loader đã định nghĩa một `Class<?>`, thay JAR trên đĩa và gọi `loadClass` lần nữa có thể trả class cũ. Để tái hiện sửa lỗi bytecode cần **fresh defining loader/JVM** và bảo đảm parent không che bytecode cần kiểm thử. Khi thử nghiệm, đừng bắt mọi `Throwable` rồi chuyển thành `ClassNotFoundException`, vì nó làm mất nguyên nhân thật.

## 22. Checklist phát hành artifact Java an toàn

**Tại build:**
- Cố định target JDK (`--release`) và runtime JDK hỗ trợ.
- Verify versions của ASM/Byte Buddy/Javassist/obfuscator phù hợp class-file version.
- Không bỏ qua compiler warnings quan trọng về invalid bytecode transformer.
- Giữ artifact metadata/checksum truy vết được class file thực sự phát hành.
- Với generated classes, chạy kiểm thử JVM loading và invocation trên JDK đích.

**Tại plugin loading:**
- Phân biệt không tìm thấy bytes với bytes không hợp lệ.
- Dùng đúng parent/shared API; tránh định nghĩa trùng runtime types.
- Dùng đúng binary name khi `defineClass`.
- Không nuốt `LinkageError` rồi fallback âm thầm.
- Dừng activation khi class hierarchy/verification thất bại.

**Tại debugging:**
- Thu đủ stack trace và cause chain của **lỗi đầu tiên**.
- Kiểm tra class loader instance, module/layer khi có, source artifact.
- Dùng `javap -verbose`/`-c`, runtime logs và bytecode before/after transformation.
- Tái hiện trong fresh JVM/loader, cùng phiên bản runtime/properties/agent.
- Đối với `ClassCircularityError`, kiểm tra **graph extends**, không chỉ method calls.

**Tại vận hành:**
- Nếu plugin start thất bại, cleanup resources đã tạo trước khi báo lỗi.
- Không để listener/thread/caches của failed plugin giữ loader trong bộ nhớ.
- Nếu muốn thử bản class đã sửa, tạo loader thế hệ mới thay vì tiếp tục dùng runtime class cũ.
- Không yêu cầu GC/unloading xảy ra ngay sau khi gặp `LinkageError` hoặc gọi `URLClassLoader.close()`.

## 23. Bài tập tự kiểm tra

1. **`ClassFormatError` thuộc checked exception không?** Không; nó extends `LinkageError` → `Error`.
2. **`VerifyError` có phải subclass của `ClassFormatError` không?** Không; là sibling dưới `LinkageError`.
3. **`ClassCircularityError` báo vòng kiểu gì?** Vòng superclass/superinterface hierarchy.
4. **Source Java có `A extends B` và `B extends A` hợp lệ không?** Không; compiler bình thường từ chối.
5. **Field `A.b` có kiểu B và `B.a` có kiểu A có tự tạo ClassCircularityError không?** Không.
6. **Một file có magic đúng có bảo đảm không ném ClassFormatError không?** Không; các cấu trúc khác có thể sai.
7. **Method `int f()` thực hiện `aconst_null; ireturn` có thể gây gì?** `VerifyError`.
8. **`UnsupportedClassVersionError` là subclass nào?** `ClassFormatError`.
9. **`loadClass` thành công có bảo đảm mọi method đã được verifier kiểm tra đầy đủ không?** Không nên giả định; verification/resolution timing có thể khác.
10. **Vòng TCCL/delegation giữa loaders có luôn ném `ClassCircularityError` không?** Không; đó là loại graph khác, có thể recursion/deadlock.
11. **Nếu parent ném `VerifyError`, loader child mặc định có tự dùng phiên bản khác không?** Không; không coi LinkageError là ClassNotFoundException.
12. **Khi chạy Java 17 nhưng class file major 65, loại lỗi thường gặp?** `UnsupportedClassVersionError`.
13. **Vì sao demo dùng loader riêng cho mỗi scenario?** Tránh cache class đã loaded và tách trạng thái thử.
14. **Vì sao không catch `Throwable` rồi ném CNFE?** Che lỗi verifier/format/circularity và cả VM errors khác.
15. **Làm thế nào tìm lỗi ClassFormatError gốc?** Kiểm tra bytes nguồn, header/CP/attributes, transformer và artifacts.
16. **Làm thế nào tìm lỗi VerifyError gốc?** Xem method instructions, StackMapTable, frames và bytecode after instrumentation.
17. **Làm thế nào tìm ClassCircularityError gốc?** Dựng graph superclass/superinterface từ class files thực sự.
18. **Vòng khởi tạo static trong ví dụ `First`/`Second` có ném ClassCircularityError không?** Không; đây là initialization, có thể quan sát giá trị static chưa hoàn thiện.
19. **Sửa .class trên đĩa rồi gọi lại loader cũ có chắc nhận bản sửa không?** Không; loader có thể đã định nghĩa runtime class cũ.
20. **Plugin manager nên làm gì với LinkageError khi kích hoạt plugin?** Ghi chẩn đoán, đánh dấu failure, cleanup tài nguyên; không chạy tiếp như plugin hợp lệ.

## 24. Tài liệu tham khảo chính thức

- [Java SE 17 — ClassFormatError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassFormatError.html): lỗi class file malformed hoặc không thể diễn giải hợp lệ.
- [Java SE 17 — VerifyError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/VerifyError.html): lỗi xác minh class bytecode.
- [Java SE 17 — ClassCircularityError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassCircularityError.html): vòng trong superclass/superinterface hierarchy.
- [Java SE 17 — UnsupportedClassVersionError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/UnsupportedClassVersionError.html): lỗi major/minor phiên bản class file.
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findClass`, `defineClass`, class identity/delegation.
- [JVMS 17 — `4 The Class File Format](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html): magic, constant pool, `this_class`, `super_class`, `Code` và `StackMapTable`.
- [JVMS 17 — `5 Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): class/interface creation, circularity checks, linking và verification.
- [JLS 17 — `12 Execution](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html): loading, linking, initialization và yêu cầu về thời điểm lỗi.

**Kết luận:** Khi Java không thể sử dụng một class, cần phân loại đúng: **`ClassFormatError` là class-file structure/format**, **`VerifyError` là bytecode verification**, còn **`ClassCircularityError` là vòng superclass/superinterface hierarchy**. Cả ba đều thuộc **`LinkageError`** nhưng yêu cầu hướng điều tra khác nhau. Dữ liệu điều tra đáng tin nhất là **bytes class file thực sự được JVM nhận, ClassLoader đang định nghĩa, runtime JDK, phương thức/quan hệ type cụ thể và lỗi gốc đầu tiên** — không phải chỉ tên JAR hay chuỗi “class loading failed”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-class-loading-errors-006">LinkageError do định nghĩa lớp trùng hoặc vi phạm loading constraint</a>

<details>
<summary>Xem chi tiết</summary>

# LinkageError do định nghĩa lớp trùng hoặc vi phạm loading constraint

## 1. Vấn đề: cùng tên class nhưng JVM không thể liên kết

Trong hệ thống Java dùng **custom ClassLoader**, plugin, application server hoặc hot reload, bạn có thể gặp thông báo:

~~~text
java.lang.LinkageError: loader ... attempted duplicate class definition
~~~

hoặc:

~~~text
java.lang.LinkageError: loader constraint violation ...
~~~

Cả hai có thể liên quan đến class loading, nhưng **khác nguyên nhân và khác cách xử lý**:

- **Định nghĩa lớp trùng (duplicate class definition):** JVM phát hiện một loader cố tạo lại class có cùng binary name mà nó đã được ghi nhận là initiating loader/đã định nghĩa. Một ClassLoader không thể tùy tiện gọi defineClass lần hai để ghi đè cùng class.
- **Vi phạm loading constraint:** Trong quá trình liên kết các kiểu liên quan qua nhiều loaders, JVM bắt buộc một tên type trong method/field descriptor phải chỉ về **cùng một runtime Class** dưới các loaders nhất định. Nếu thực tế hai loaders đã dùng **hai Class khác nhau** cho tên đó, JVM ném LinkageError để duy trì type safety.

**Quan trọng:** JVM hoàn toàn cho phép **hai ClassLoaders khác nhau định nghĩa hai class có cùng binary name** trong những trường hợp cách ly hợp lệ. Việc trùng tên giữa hai loaders **tự nó không phải LinkageError**. Lỗi chỉ xuất hiện khi phạm quy tắc định nghĩa/initiating loader hoặc một **ràng buộc type identity bắt buộc**.

Bài này có hai ví dụ Java 17+ đã kiểm chứng bằng OpenJDK 21, quy trình tái hiện lỗi, cách đọc stack trace và hướng khắc phục thực tế.

## 2. Phân biệt defining loader và initiating loader

### 2.1 Defining loader

**Defining loader** là loader đã thực sự định nghĩa runtime class từ một biểu diễn hợp lệ (ví dụ gọi defineClass). Với class thường, **(binary name, defining loader)** xác định danh tính runtime type:

~~~text
("plugin.Item", LoaderA) != ("plugin.Item", LoaderB)
~~~

Hai class có cùng mã nguồn và cùng tên vẫn khác loại nếu defining loaders khác nhau.

### 2.2 Initiating loader

**Initiating loader** là loader mà JVM ghi nhận đã bắt đầu yêu cầu load một class; nó có thể không phải defining loader.

Ví dụ:

~~~text
ChildLoader.loadClass("api.Service")
     |
     +-- hỏi ParentLoader
              |
              +-- parent định nghĩa api.Service
     |
     +-- child nhận lại Class của parent
~~~

Ở tình huống này:

- **ParentLoader** là defining loader của api.Service.
- **ChildLoader** có thể được JVM ghi nhận là initiating loader của cùng class do parent định nghĩa.
- **findLoadedClass("api.Service")** của child có thể thấy Class đã được initiate ở child, dù defining loader của Class đó là parent.

Đây là lý do một custom loader không nên tự gọi **defineClass** bừa bãi sau khi đã delegate tên đó sang parent.

### 2.3 Không thể định nghĩa lại tùy tiện trong cùng loader

Theo JVMS §5.3.5, nếu JVM đã ghi nhận loader L là initiating loader cho tên N thì việc yêu cầu tạo định nghĩa mới của tên ấy với L là không hợp lệ và gây **LinkageError**.

Trong thực tế dễ gặp hơn: một loader **gọi defineClass hai lần** với cùng binary name. Đây là hành vi sai ngay cả khi hai mảng bytes hoàn toàn giống nhau.

~~~text
Loader L
  defineClass("demo.Token", bytesV1) -> thành công
  defineClass("demo.Token", bytesV1) -> LinkageError
  defineClass("demo.Token", bytesV2) -> LinkageError

Loader L2 hoàn toàn mới
  defineClass("demo.Token", bytesV2) -> có thể thành công
~~~

Không có cơ chế “overwrite class” tự động trong **ClassLoader.defineClass**. Muốn cập nhật method body của cùng class đã nạp, có cơ chế **java.lang.instrument.Instrumentation.redefineClasses/retransformClasses** với giới hạn riêng. Muốn phiên bản runtime type mới, tạo loader mới rồi quản lý chuyển đổi/lifecycle.

## 3. LinkageError là một họ lỗi, không phải tất cả đều duplicate

**java.lang.LinkageError** là Error, không phải checked exception. Một số loại liên quan gồm:

| Loại lỗi | Dấu hiệu | Nhóm nguyên nhân |
| --- | --- | --- |
| **LinkageError** trực tiếp, thông báo attempted duplicate class definition | Cùng loader cố define lại tên đã biết | Duplicate definition |
| **LinkageError** trực tiếp, thông báo loader constraint violation | Hai loaders phải thống nhất runtime Class trong một type descriptor nhưng không thống nhất | Loading constraint |
| **NoClassDefFoundError** | JVM cần class nhưng định nghĩa không thể sử dụng/tìm được hoặc khởi tạo từng thất bại | Loading/linking/initialization |
| **ClassFormatError** | Bytes không phải class-file hợp lệ | Định dạng |
| **UnsupportedClassVersionError** | Class-file version không được JVM hỗ trợ | Version |
| **VerifyError** | Bytecode không thỏa verifier | Verification |
| **NoSuchMethodError / NoSuchFieldError** | Bytecode tham chiếu thành viên không tồn tại theo runtime definition | Binary incompatibility |
| **IncompatibleClassChangeError** và subclasses | Loại/thành viên được dùng không khớp quan hệ binary mong đợi | Binary incompatibility |

**ClassNotFoundException** không phải LinkageError: đó là checked exception mà lookup APIs như loadClass có thể ném khi không tìm thấy class theo tên.

**ClassCastException** cũng không phải LinkageError: nó là RuntimeException khi cast sai. Hai loaders có thể định nghĩa hai type trùng tên **hợp lệ**, rồi ứng dụng ném ClassCastException khi cố cast instance giữa hai Class khác identity, mà không có loading constraint violation.

**Đừng sửa theo tên exception chung chung.** Đọc **lớp exception thực tế, thông báo đầy đủ, cause, thời điểm lỗi và đường loader ownership**.

## 4. Ví dụ Java 17+ số 1: định nghĩa trùng trong cùng loader

### 4.1 Cấu trúc thư mục

~~~text
project/
├── dup-src/
│   └── demo/
│       └── Token.java
└── src/
    └── DuplicateDemo.java
~~~

**dup-src/demo/Token.java**:

~~~java
package demo;

public class Token {
    public String value() {
        return "token";
    }
}
~~~

**src/DuplicateDemo.java**:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;

public class DuplicateDemo {
    static class OnceLoader extends ClassLoader {
        OnceLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(
                Path.of("dup-out/demo/Token.class"));

        OnceLoader loader = new OnceLoader(
                ClassLoader.getPlatformClassLoader());

        Class<?> first = loader.define("demo.Token", bytes);

        System.out.println("first = " + first.getName());
        System.out.println("defined by loader = "
                + (first.getClassLoader() == loader));

        try {
            loader.define("demo.Token", bytes);
            System.out.println("unexpected");
        } catch (LinkageError error) {
            System.out.println("second = "
                    + error.getClass().getSimpleName());
        }

        OnceLoader secondLoader = new OnceLoader(
                ClassLoader.getPlatformClassLoader());

        Class<?> second =
                secondLoader.define("demo.Token", bytes);

        System.out.println("other loader succeeds = "
                + (first != second));
    }
}
~~~

### 4.2 Biên dịch và chạy

Đứng tại thư mục **project**:

~~~bash
javac -d dup-out dup-src/demo/Token.java
javac -d demo-out src/DuplicateDemo.java
java -cp demo-out DuplicateDemo
~~~

Windows PowerShell dùng chính các lệnh trên vì mỗi classpath chỉ có một entry.

**Stdout đã chạy thực tế trên OpenJDK 21:**

~~~text
first = demo.Token
defined by loader = true
second = LinkageError
other loader succeeds = true
~~~

### 4.3 Tại sao lần thứ hai thất bại?

**loader.define("demo.Token", bytes)** lần đầu tạo runtime Class được định nghĩa bởi **loader**. Lần thứ hai cùng object loader yêu cầu defineClass với cùng name; JVM từ chối tạo định nghĩa trùng và ném LinkageError.

Trong ví dụ, catch chỉ in **tên loại lỗi**, không in toàn bộ thông báo vì **message cụ thể chứa tên loader và định dạng khác nhau giữa JVMs**.

**Quan sát then chốt:** Tạo **secondLoader** mới và define cùng bytes lại thành công. **first != second** là true. Việc **hai loaders định nghĩa cùng name là hợp lệ** nếu chưa có constraint bắt buộc chúng đồng nhất trong một quan hệ liên kết.

Đây là ví dụ về khác biệt giữa “trùng trong cùng loader” và “trùng giữa nhiều loaders”.

### 4.4 Ví dụ này có cố ý bỏ qua loadClass mặc định không?

Có. Phương thức **define** là wrapper trực tiếp tới protected defineClass để minh họa lỗi. Đây **không phải thiết kế loader production**.

Một loader bình thường nên để **ClassLoader.loadClass** thực hiện cache/delegation và override **findClass** để đọc bytes riêng. Khi cần custom policy, vẫn phải bảo toàn logic:

~~~text
getClassLoadingLock(name)
    |
    v
findLoadedClass(name)
    |
    v
parent delegation hoặc custom route phù hợp
    |
    v
findClass(name) khi thật sự cần định nghĩa mới
    |
    v
resolveClass nếu resolve=true
~~~

Nếu lặp lại defineClass trong cùng loader vì thấy file trên đĩa đã thay đổi, phương án đúng không phải “nuốt LinkageError và retry”, mà là xác định **Class đã tồn tại**, hoặc tạo loader mới cho deployment khác.

## 5. Loading constraint thực sự là gì?

### 5.1 Vấn đề type safety giữa nhiều loaders

Giả sử class A của loader L1 gọi method ở class B của loader L2, và chữ ký method có kiểu **api.Payload**:

~~~java
api.Payload echo(api.Payload value)
~~~

Một runtime name **api.Payload** có thể trỏ tới các Class khác nhau nếu loader L1 và loader L2 chọn bytecode từ những nguồn riêng.

Khi liên kết method/field tham chiếu qua boundary này, JVM phải bảo đảm **api.Payload trong descriptor chỉ tới cùng runtime type** ở những loader có liên quan. Nếu không, runtime method call có thể tưởng rằng tham số/giá trị trả về tương thích chỉ vì tên giống nhau trong constant pool, trong khi thực tế không phải cùng Class.

### 5.2 Ký hiệu của JVMS §5.3.4

JVMS mô tả loading constraints dưới dạng:

~~~text
N^L1 = N^L2
~~~

Đọc là: **tên type N do loader L1 và loader L2 initiate phải biểu thị cùng Class** khi ràng buộc ấy áp dụng.

Điều quan trọng:

- Đây là **ràng buộc type identity**, không phải lệnh JVM phải dùng một loader duy nhất cho toàn bộ chương trình.
- JVMS áp đặt constraints tại những thời điểm cụ thể của **preparation/resolution/lookup và method overriding**.
- Type names trong **method descriptors** (tham số và return) và **field descriptors** có thể tham gia.
- Với array reference types, JVM xét element type theo quy tắc thích hợp.
- Khi ràng buộc tồn tại mà hai loaders đã được ghi nhận initiate **hai Class khác nhau** cho cùng tên N, JVM ném **LinkageError**.
- Constraint có thể được áp đặt **trước** khi hai tên được resolve thực sự ở cả hai loader; vi phạm có thể lộ ra về sau khi một tên được initiate.
- JVM không bắt buộc ném lỗi ngay ở dòng **defineClass** của class đầu tiên; lỗi có thể xuất hiện khi preparation, linking hoặc resolution diễn ra.

### 5.3 Điều kiện vi phạm theo đặc tả

Theo JVMS §5.3.4, một loading constraint bị vi phạm khi đồng thời thỏa những điều kiện cần thiết:

1. Có initiating loader L đã được JVM ghi nhận cho Class C với tên N.
2. Có initiating loader L' đã được JVM ghi nhận cho Class C' cũng mang tên N.
3. Tập loading constraints (và quan hệ tương đương bắc cầu từ đó) đòi **N^L = N^L'**.
4. Nhưng **C != C'**.

**Không đủ** chỉ có điều kiện 1, 2 và 4. Phải có **điều kiện 3: ràng buộc bắt buộc đồng nhất type**.

### 5.4 Điều gì tạo ra loading constraint?

Các ví dụ trong JVMS:

- Một method override giữa subclass/superclass hoặc implements interface mà descriptor chứa reference types.
- Resolution của field reference đi qua class boundaries với field descriptor chứa reference type.
- Resolution của method/interface method references với tham số hoặc return reference types.
- Những bước liên kết tương ứng đòi các symbolic references biểu thị cùng runtime type.

Không nên hiểu rằng mọi method có signature chứa class riêng của plugin đều gây constraint violation. Nếu tất cả participants dùng **cùng Class shared API** qua delegation, constraints được thỏa mãn.

## 6. Ví dụ Java 17+ số 2: tái hiện loader constraint violation

### 6.1 Mô hình: interface chung ở host, Payload riêng của child

Ví dụ này cố tình tạo một tình huống **không an toàn**:

- Parent/application loader định nghĩa **api.Api** và **api.Payload**.
- Child loader tự định nghĩa **một api.Payload khác** có **cùng binary name**.
- Child còn định nghĩa **impl.Impl implements api.Api**. Method triển khai có descriptor chứa api.Payload.
- Khi JVM chuẩn bị liên kết interface method table cho Impl, nó phát hiện **api.Payload của child khác api.Payload của parent** trong một chữ ký buộc phải thống nhất.

~~~text
AppLoader:
    api.Api
       method api.Payload echo(api.Payload)
    api.Payload   <-- Class P1

BytesLoader child:
    api.Payload   <-- Class P2, P2 != P1
    impl.Impl implements AppLoader's api.Api

Method echo(Payload) qua interface boundary:
    Payload^AppLoader = Payload^BytesLoader
                  nhưng P1 != P2
         => loader constraint violation
~~~

### 6.2 Cấu trúc thư mục

~~~text
project/
├── host-src/
│   └── api/
│       ├── Api.java
│       └── Payload.java
├── child-src/
│   ├── api/
│   │   └── Payload.java
│   └── impl/
│       └── Impl.java
└── src/
    └── ConstraintDemo.java
~~~

**host-src/api/Payload.java**:

~~~java
package api;

public class Payload {
    public String source() {
        return "host";
    }
}
~~~

**host-src/api/Api.java**:

~~~java
package api;

public interface Api {
    Payload echo(Payload p);
}
~~~

**child-src/api/Payload.java**:

~~~java
package api;

public class Payload {
    public String source() {
        return "child";
    }
}
~~~

**child-src/impl/Impl.java**:

~~~java
package impl;

import api.Api;
import api.Payload;

public class Impl implements Api {
    @Override
    public Payload echo(Payload p) {
        return p;
    }
}
~~~

Hai file **api/Payload.java** có cùng binary name nhưng thuộc **hai source roots khác nhau**; không compile chúng cùng lúc vào một output folder.

### 6.3 Launcher ConstraintDemo.java

**src/ConstraintDemo.java**:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import api.Api;
import api.Payload;

public class ConstraintDemo {
    static final class BytesLoader extends ClassLoader {
        BytesLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> defineLocal(String name, Path file)
                throws Exception {
            byte[] bytes = Files.readAllBytes(file);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("parent Payload = "
                + Payload.class.getName());
        System.out.println("shared API from parent = "
                + (Api.class.getClassLoader()
                   == ClassLoader.getSystemClassLoader()));

        BytesLoader child = new BytesLoader(
                ClassLoader.getSystemClassLoader());

        Class<?> local = child.defineLocal(
                "api.Payload",
                Path.of("child-out/api/Payload.class"));

        System.out.println("same payload Class = "
                + (local == Payload.class));

        try {
            Class<?> impl = child.defineLocal(
                    "impl.Impl",
                    Path.of("child-out/impl/Impl.class"));

            System.out.println("impl defined = "
                    + impl.getName());

            Object obj = impl.getConstructor().newInstance();

            System.out.println("created = "
                    + obj.getClass().getName());
            System.out.println("invoked = "
                    + ((Api) obj).echo(new Payload()).source());

        } catch (LinkageError error) {
            System.out.println("error type = "
                    + error.getClass().getSimpleName());
            System.out.println("mentions constraint = "
                    + error.getMessage().contains("loader constraint"));
            System.out.println("error message = "
                    + error.getMessage());
        }

        // Cách sửa: loader mới KHÔNG định nghĩa api.Payload
        BytesLoader safe = new BytesLoader(
                ClassLoader.getSystemClassLoader());

        Class<?> impl2 = safe.defineLocal(
                "impl.Impl",
                Path.of("child-out/impl/Impl.class"));

        Api ok = (Api) impl2.getConstructor().newInstance();

        System.out.println("shared payload works = "
                + ok.echo(new Payload()).source());
    }
}
~~~

**defineLocal** cố tình gọi defineClass trực tiếp để ép child định nghĩa thêm api.Payload và làm phát sinh xung đột. Đây là fixture chẩn đoán, không phải loader nên dùng trong sản phẩm.

### 6.4 Biên dịch theo đúng thứ tự

Đứng tại thư mục **project**, thực hiện từng lệnh:

~~~bash
javac -d host-out host-src/api/Payload.java host-src/api/Api.java

javac -cp host-out -d child-out child-src/impl/Impl.java

javac -d child-out child-src/api/Payload.java

javac -cp host-out -d demo-out src/ConstraintDemo.java

java -cp demo-out:host-out ConstraintDemo
~~~

**Tại sao compile Impl trước file Payload riêng của child?** Vì **impl.Impl** phải được compile chống lại **api.Api** và **api.Payload** của host để method descriptor đúng với interface chung. Chúng ta sau đó mới tạo một **api.Payload** khác trong child-out để cố tình làm hỏng identity tại runtime.

Trên Windows PowerShell, thay lệnh cuối bằng:

~~~powershell
java -cp "demo-out;host-out" ConstraintDemo
~~~

Các lệnh javac dùng **-cp host-out** chỉ có một entry nên giống nhau trên cả hai hệ điều hành.

### 6.5 Stdout đã kiểm chứng trên OpenJDK 21

Đầu ra được kiểm chứng; dòng **error message** phụ thuộc tên/hash loader cụ thể nên được lược khỏi mẫu cố định:

~~~text
parent Payload = api.Payload
shared API from parent = true
same payload Class = false
impl defined = impl.Impl
error type = LinkageError
mentions constraint = true
shared payload works = host
~~~

Trong chương trình thực, **giữa dòng mentions constraint và shared payload works còn có dòng**:

~~~text
error message = loader constraint violation in interface itable initialization ...
~~~

Đó là **dòng minh họa dạng thông báo**, không phải nguyên văn bất biến. JVM có thể bổ sung identity/address của ClassLoaders, module names và method descriptor. **Không test bằng toàn bộ chuỗi error message**; nếu cần kiểm tra loại lỗi, dùng exception class và đối chiếu nghĩa của message/stack trace.

Đặc biệt, dòng **created = ...** không được in trong lần chạy lỗi trên OpenJDK 21 vì việc liên kết cần thiết khi tạo instance ném LinkageError trước đó.

### 6.6 Tại sao lỗi xuất hiện sau dòng “impl defined”?

Khi **child.defineLocal("impl.Impl", ...)** trả về, class đã được tạo nhưng JVM có thể chưa hoàn tất các bước linking/resolution cụ thể dẫn tới kiểm tra constraint. Lệnh tạo instance qua reflection làm JVM tiến tới **interface itable initialization**, tại đó JVM phát hiện signature bất tương thích.

**Đây là đặc điểm quan trọng của LinkageError:** Không nên khẳng định lỗi phải xuất hiện ngay khi gọi defineClass, ngay khi loadClass hoặc ngay khi method được gọi. JVMS xác định các thời điểm kiểm tra constraints; exact stack frame và timing phụ thuộc kiểu resolution/JVM.

### 6.7 Vì sao phiên bản safe hoạt động?

Loader **safe** chỉ tự define **impl.Impl** và **không** tự define api.Payload. Khi cần api.Api và api.Payload, nó dùng parent delegation thông thường. Do đó method **Api.echo(Payload)** của parent và method triển khai từ child cùng nhắc đến **chính Class api.Payload của parent**.

Lệnh:

~~~java
Api ok = (Api) impl2.getConstructor().newInstance();
System.out.println(ok.echo(new Payload()).source());
~~~

chạy và trả **host**. Điều này chứng minh giải pháp **chia sẻ API types bằng một loader thống nhất**, không phải đổi tên method hay bắt rồi bỏ LinkageError.

## 7. Khác biệt tinh tế: JVM cho phép isolation nhưng bảo vệ boundary

Một hệ thống plugin hợp lệ:

~~~text
PluginLoaderA: plugin.internal.Cache
PluginLoaderB: plugin.internal.Cache
~~~

Hai class cùng binary name nhưng định nghĩa khác nhau **có thể cùng tồn tại** nếu không có ràng buộc buộc chúng phải là một type.

Vấn đề xuất hiện khi giao tiếp giữa hai loaders qua một type cần thống nhất:

~~~text
Host interface:
    SharedResult execute(SharedInput arg)

Plugin implements:
    SharedResult execute(SharedInput arg)

Nếu host và plugin dùng cùng SharedInput/SharedResult Class
    -> loading constraints được thỏa mãn

Nếu plugin tự define SharedInput/SharedResult trùng tên
    -> có thể vi phạm loading constraint ở linkage boundary
~~~

**Kinh nghiệm:** Đưa **service interfaces, DTOs, callback types và các dependency API dùng chung** vào parent/shared loader. Chỉ cô lập implementation packages. Plugin runtime vẫn có thể dùng child-first cho implementation riêng, nhưng tránh child-first trên **api.*** nếu đó là kiểu host truyền qua method signatures.

### 7.1 Cùng binary name không bao giờ đủ để ép kiểu

Với:

~~~java
Class<?> a = loaderA.loadClass("plugin.Item");
Class<?> b = loaderB.loadClass("plugin.Item");

System.out.println(a.getName().equals(b.getName())); // true
System.out.println(a == b);                           // false
~~~

Đây là snippet giả định hai loaders thực sự tự define class và không delegate đến cùng parent Class.

Nếu bạn tạo instance của a rồi thử ép sang type b, có thể gặp **ClassCastException**. Đây là tình huống **identity mismatch ở runtime cast**, không nhất thiết là loader constraint violation. Phân biệt chúng bằng error type và code path.

### 7.2 Runtime package cũng khác theo defining loader

Ngay cả khi hai class khai báo cùng:

~~~java
package plugin.internal;
~~~

nếu được định nghĩa bởi hai loaders khác nhau, chúng không tự thuộc cùng **runtime package** cho package-private access. Khi code cần truy cập thành viên package-private, có thể xuất hiện **IllegalAccessError** nếu bytecode liên kết tới class khác runtime package, dù source text ghi cùng package.

Đừng sửa bằng cách đổi package name tràn lan; phải hiểu class identity, package/module access và loader mapping.

## 8. Nguyên nhân thực tế của duplicate definition

### 8.1 findClass bị gọi trực tiếp thay vì loadClass

~~~java
// Sai hướng nếu class cùng tên đã load:
Class<?> c = customLoader.findClass("plugin.Service");
~~~

Trong ứng dụng thông thường, **findClass là protected** và chỉ nên được gọi trong thuật toán loadClass thích hợp. Nếu subclass tự expose wrapper public để gọi findClass/defineClass nhiều lần, JVM có thể thấy cùng name được định nghĩa lại.

### 8.2 Override loadClass bỏ findLoadedClass

Ví dụ không an toàn:

~~~java
@Override
protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
    return findClass(name);
}
~~~

Đây là đoạn **phản ví dụ** bên trong subclass, không dùng trong production. Nó phá parent delegation, caching, resolve flag và locking. Mỗi lần load lại có nguy cơ gọi defineClass thêm lần nữa.

### 8.3 Cạnh tranh giữa nhiều threads

Nếu hai threads cùng yêu cầu class name, code:

~~~text
Thread A: class chưa có -> đọc bytes
Thread B: class chưa có -> đọc bytes
Thread A: defineClass thành công
Thread B: defineClass lại -> LinkageError
~~~

là race kinh điển. Cần khóa trong suốt chu kỳ **check → load → define** chứ không chỉ synchronized riêng lẻ từng bước.

### 8.4 Thay JAR trên đĩa rồi gọi defineClass lại

JVM không tự cập nhật class đã được định nghĩa khi file .class/.jar thay đổi. Một loader đang sống không phải container có thể “overwrite” class definitions.

Muốn nâng version implementation, tạo loader mới và quản lý old loader lifecycle, hoặc dùng instrumentation trong giới hạn cho phép.

### 8.5 Plugin bị đăng ký nhiều lần trong cùng loader

Một plugin engine có thể retry khởi tạo hoặc chạy hai callback đồng thời, cả hai đi vào cùng logic defineClass(name). Nếu ownership chưa rõ, một Class được define từ nhiều đường gọi.

**Giải pháp:** Cho một thành phần chịu trách nhiệm nạp mỗi binary name; dùng findLoadedClass đúng cách và đồng bộ hóa; tránh caching bytes như thể nó tương đương caching Class object.

## 9. Nguyên nhân thực tế của loading constraint violation

### 9.1 Child-first quá rộng

Nếu child-first áp dụng cả cho **host API package**, loader plugin có thể define lại **api.Event**, **api.Result**, **api.Context** và các types xuất hiện trong method signatures của host interfaces.

JVM có thể chấp nhận một số class trước, rồi phát hiện constraint khi linking interface implementations, method resolution hoặc field access.

**Giải pháp:** Parent-first cho shared packages; selective child-first cho implementation, chẳng hạn **plugin.internal.***.

### 9.2 Thư viện phụ thuộc bị đóng gói trùng

Host và plugin cùng có một bản thư viện **lib.model.Dto** trong classpath/JAR riêng. Nếu class này xuất hiện trong public API signature, việc tự define bản plugin có thể gây identity mismatch.

**Giải pháp:** Một bản shared dependency trong loader chung, hoặc thiết kế boundary không trực tiếp chuyển object thuộc class bị cô lập.

### 9.3 Thay class implementation mà quên shared interface

Host cast plugin implementation thành interface **api.Service** do host define, nhưng plugin lại có bản api.Service riêng. Lỗi có thể là **ClassCastException** hoặc linkage error liên quan, tùy lúc và quan hệ mà JVM phải resolve.

**Giải pháp:** Dùng một **Class api.Service chung** và để implementation của plugin ở loader riêng.

### 9.4 Tùy biến loader graph thành vòng/truy xuất không nhất quán

Một số framework có peer delegation, classloader-per-tenant hoặc module layer mappings. Nếu các đường dẫn lựa chọn Class khác nhau cho cùng API name tại những nơi phải thỏa type descriptor, JVM sẽ áp đặt constraints và có thể phát hiện xung đột.

**Giải pháp:** Xác định package ownership nhất quán, tránh nhiều nguồn tự định nghĩa cùng shared type. Không cố vô hiệu hóa JVM checks.

### 9.5 Reflection và service loading không sửa type identity

**Thread Context ClassLoader**, **Class.forName** và **ServiceLoader** có thể tìm provider classes qua loader khác. Nhưng chỉ đổi TCCL không làm những bản API Class từ host/plugin trở thành một.

Khi debugging, kiểm tra **serviceType.getClassLoader()**, **providerClass.getClassLoader()** và API dependency classes. Điều chỉnh rõ loader của shared contracts.

## 10. Viết ClassLoader đúng để tránh cả hai nhóm lỗi

### 10.1 Mẫu parent-first: chỉ override findClass

Với nguồn bytecode riêng, nên bắt đầu bằng:

~~~java
public final class PluginLoader extends ClassLoader {
    private final java.nio.file.Path root;

    public PluginLoader(ClassLoader parent,
                        java.nio.file.Path root) {
        super(parent);
        this.root = root;
    }

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {

        if (!name.startsWith("plugin.internal.")) {
            throw new ClassNotFoundException(name);
        }

        java.nio.file.Path file = root.resolve(
                name.replace('.', '/') + ".class");

        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file);
            return defineClass(name, bytes, 0, bytes.length);
        } catch (java.io.IOException ex) {
            throw new ClassNotFoundException(name, ex);
        }
    }
}
~~~

Đây là **một class code hoàn chỉnh để minh họa hook**, chưa phải chương trình main. Đầu vào path/name trong loader production cần được kiểm soát chống path traversal, kích thước bytes, package restrictions và nguồn không tin cậy.

**Lợi ích:** Thuật toán ClassLoader.loadClass mặc định đã xử lý **getClassLoadingLock, findLoadedClass, parent-first, findClass, resolveClass**. Shared API packages thường được parent cung cấp, tránh child định nghĩa lại.

**Giới hạn:** Nếu parent cũng có implementation class mà plugin phải ưu tiên bản riêng, findClass đơn độc không sửa được thứ tự delegation. Lúc đó cần cân nhắc selective child-first theo package có chủ đích.

### 10.2 Selective child-first cần giữ cache và khóa

Mẫu ý tưởng:

~~~java
@Override
protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {

    if (!name.startsWith("plugin.internal.")) {
        return super.loadClass(name, resolve);
    }

    synchronized (getClassLoadingLock(name)) {
        Class<?> c = findLoadedClass(name);

        if (c == null) {
            try {
                c = findClass(name);
            } catch (ClassNotFoundException absent) {
                c = super.loadClass(name, false);
            }
        }

        if (resolve) {
            resolveClass(c);
        }
        return c;
    }
}
~~~

Đoạn này là **method trong ClassLoader subclass có findClass phù hợp**, không phải chương trình độc lập.

Nó bảo đảm những yếu tố quan trọng:

- Shared APIs không bị child-first do prefix hạn chế.
- **findLoadedClass** tránh định nghĩa trùng đã initiate.
- **getClassLoadingLock(name)** quản lý concurrent requests.
- **findClass** được dùng cho class nội bộ.
- Chỉ fallback khi không tìm thấy bằng **ClassNotFoundException**; không bắt/nuốt **LinkageError**.
- **resolveClass** được gọi khi resolve=true.

Không mở rộng điều kiện child-first sang **java.*** hoặc mọi package của ứng dụng mà không có thiết kế type boundary, sealing và module rules. Đồng thời, đoạn code này không phải sandbox bảo mật.

### 10.3 Parallel-capable loaders

JDK cung cấp **registerAsParallelCapable()** và **getClassLoadingLock(name)** để hỗ trợ tải song song các tên class khác nhau.

Nếu subclass chưa đăng ký parallel capable theo quy tắc API, lock có thể là chính object loader, làm giảm concurrency nhưng vẫn giữ correctness khi thuật toán dùng lock đúng.

Nếu một runtime có loader graph phi cây hoặc nhiều loaders gọi chéo, cần phân tích **loading deadlock**; bỏ synchronized để “tránh nghẽn” có thể gây race/duplicate define nghiêm trọng hơn.

## 11. Chẩn đoán từ stack trace: xem những gì?

### 11.1 Tìm thông tin định danh loader

Một lỗi loader constraint có thể nhắc tới:

~~~text
loader constraint violation ...
class loader X for class/interface A ...
class loader Y for class/interface B ...
different Class objects for type api.Payload ...
~~~

Trong thông báo JVM thường kèm **module**, **unnamed module**, **parent loader**, tên class hoặc phương thức liên quan. Đừng bỏ phần này khi ghi issue/report.

Một loader custom nên có **tên/định danh** hữu ích khi debug. Ở Java 9+, một số constructor ClassLoader hỗ trợ tên; hoặc override toString có kiểm soát để phân biệt plugin versions.

### 11.2 In Class và defining loaders

Trong method có các biến clazzA, clazzB:

~~~java
System.out.println(clazzA.getName());
System.out.println(clazzA.getClassLoader());
System.out.println(clazzA.getModule());

System.out.println(clazzB.getName());
System.out.println(clazzB.getClassLoader());
System.out.println(clazzB.getModule());

System.out.println(clazzA == clazzB);
~~~

Đây là snippet minh họa, cần có hai Class objects. **Class.getClassLoader() có thể trả null** cho class do bootstrap loader định nghĩa; đừng gọi .toString() trực tiếp nếu chưa kiểm tra null.

**Không chỉ so tên string.** Hai Class có cùng getName() nhưng Class objects khác sẽ chỉ ra defining loaders hoặc identity khác.

### 11.3 Kiểm tra class files có thật sự từ nguồn mong đợi?

Với JDK toolchain:

~~~bash
javap -verbose -classpath host-out api.Payload
javap -verbose -classpath child-out api.Payload
~~~

Hai lệnh này hiển thị thông tin classfile ở **hai artifact roots**, không chứng minh JVM đã chọn nguồn nào trong runtime. Kết hợp với **getClassLoader()**, logs và code loader.

Kiểm tra archive:

~~~bash
jar --list --file plugin.jar
~~~

Nếu cùng API class xuất hiện trong host JAR và plugin JAR, đó là tín hiệu cần đánh giá package ownership, không phải bằng chứng tự động gây LinkageError.

### 11.4 JVM Unified Logging

Trên HotSpot/JVM hỗ trợ:

~~~bash
java -Xlog:class+load=info -cp demo-out:host-out ConstraintDemo
java -Xlog:help
~~~

Windows thay classpath separator bằng **;**. Logging class loading có thể giúp biết class nào được nạp, từ nguồn nào và ở giai đoạn nào, nhưng thông tin chi tiết phụ thuộc JVM/version. Không suy ra initiating loader hoàn toàn từ một dòng log chung; cần xét stack trace và Class objects.

### 11.5 Thread dump khi lỗi xảy ra khi chạy song song

Nếu duplicate do race, lỗi có thể chỉ xuất hiện dưới tải. Kiểm tra:

- Có thread nào vào cùng **findClass/defineClass** với cùng name không?
- Custom **loadClass** có khóa trên **getClassLoadingLock(name)** không?
- **findLoadedClass** có được gọi dưới lock không?
- Loader có phải parallel capable không?
- Một class có bị nạp đồng thời từ nhiều call paths khác nhau?
- Có cycle parent/child/peer delegation làm deadlock không?

Không dùng try/catch LinkageError để che race. Đó là triệu chứng thiết kế cần sửa.

## 12. Cách đọc thời điểm ném loader constraint violation

JVMS cho phép JVM trì hoãn một số bước resolution. Vì vậy:

~~~text
child.defineClass("impl.Impl", bytes)
          |
          v
Class object được tạo thành công
          |
          v
Reflection newInstance / method resolution
          |
          v
JVM liên kết method interface/superclass
          |
          v
Phát hiện api.Payload^AppLoader != api.Payload^ChildLoader
          |
          v
LinkageError: loader constraint violation
~~~

Ví dụ 2 quan sát đúng tình huống này trên OpenJDK 21.

Một số chương trình có thể ném sớm hơn, ví dụ trong preparation, hoặc muộn hơn khi symbolic reference liên quan được resolve. Khi lỗi xuất hiện ở dòng **newInstance()**, không có nghĩa constructor Java của Impl là nguyên nhân trực tiếp. JVM có thể đang làm bước liên kết cần thiết **trước khi constructor chạy**.

**Không dựa vào exact line number để kết luận.** Lấy stack trace, classloader ownership, class signature và JVMS loading constraint rules làm cơ sở.

## 13. Những giải pháp sai thường gặp

### 13.1 Catch LinkageError rồi dùng class khác

Không nên làm điều này như luồng bình thường:

~~~java
try {
    return customLoader.loadClass(name);
} catch (LinkageError ignored) {
    return parent.loadClass(name);
}
~~~

Đây là snippet **phản ví dụ** có biến customLoader/name/parent giả định. Nếu JVM đã thiết lập loading constraint hoặc có định nghĩa trùng, bỏ qua lỗi và tiếp tục trả một Class khác có thể làm application state/type consistency khó dự đoán.

**Đúng hơn:** thiết kế delegation trước khi định nghĩa class. Fallback chỉ nên xử lý **ClassNotFoundException** theo policy, không coi LinkageError là “không tìm thấy class”.

### 13.2 Đổi binary name nhưng vẫn giữ shared signature cũ

Đổi **api.Payload** của child thành một tên khác có thể né trùng tên, nhưng nếu plugin vẫn phải implements **Api.echo(api.Payload)** của host, signature phải dùng **chính host api.Payload**. Không thể sửa type mismatch chỉ bằng rename implementation hoặc ép cast không kiểm soát.

### 13.3 Đổi TCCL và mong sửa Class identity

**Thread Context ClassLoader** chỉ thay lookup context cho các API sử dụng nó. Nó không biến two runtime Class objects thành one Class. Cần kiểm tra **defining loader** thực tế và type boundary.

### 13.4 Xóa class file khỏi JAR khi Class đã nạp

Thay đổi artifact trên disk không hủy Class object đã tồn tại, không reset initiated-loader records. Khi cần nâng bản plugin, tạo loader mới và chuyển lifecycle, không trông chờ JVM tự quên định nghĩa cũ.

### 13.5 Bỏ khóa để “hết LinkageError do race”

Làm vậy dễ tăng xác suất duplicate definition. Cần atomicity trên **check/load/define** bằng khóa đúng và không thao tác defineClass cùng tên từ nhiều code paths.

### 13.6 Parent-first tuyệt đối cho mọi package mà không hiểu phiên bản

Parent-first là mặc định tốt cho **shared API**, nhưng nếu parent và child có implementation cùng tên và cần isolation, parent-first có thể trả bản sai. Dùng **selective child-first cho package nội bộ**, parent-first cho shared contracts.

## 14. Checklist khắc phục trong hệ plugin

Khi nhận lỗi LinkageError liên quan ClassLoader, kiểm tra theo trình tự:

1. **Đọc exact exception class và message.** Phân loại duplicate hay loading constraint; không trộn với ClassCastException.
2. **Ghi lại binary name và loaders được nhắc tới.** Bao gồm plugin version, module, parent chain.
3. **Nếu duplicate:** tìm nơi defineClass được gọi lặp; xem custom loadClass có findLoadedClass và loading lock chưa.
4. **Nếu constraint:** tìm method/field descriptor chứa type được JVM nêu (ví dụ api.Payload).
5. Xác nhận host và plugin đang dùng **cùng hay khác Class object** cho shared type đó.
6. Kiểm tra JAR/dependency graph có hai bản shared API/DTO bị đưa vào child không.
7. Chọn một loader làm **owner** của shared contracts; để child delegate chính xác tới owner.
8. Nếu cần isolation, giới hạn child-first vào internal implementation packages.
9. Kiểm tra kiểu package-private, module exports/opens và loader constraints ở boundary.
10. Rebuild artifact với cấu trúc classpath/module path đúng; không chỉ sửa code loader mà bỏ quên duplicate dependencies.
11. Test concurrency và hot redeploy nhiều vòng; xác nhận loader cũ được unregister và không bị thread/caches giữ.
12. Giữ bài test regression kiểm tra **a == b** cho types phải chia sẻ và **a != b** cho isolated implementations được kỳ vọng.

### 14.1 Dấu hiệu sửa đúng

Một hệ host/plugin đúng thường có:

~~~text
Host loader
   |
   +-- api.Service
   +-- api.Payload
   |
   +-- Plugin loader
           |
           +-- plugin.internal.PluginImpl
           |
           +-- khi cần api.Service/api.Payload:
               delegate tới Host loader
~~~

Method signature **Api.echo(Payload)** ở host và implementation plugin cùng nhìn thấy Class api.Payload do host define. Từ đó loading constraints được thỏa mãn.

Những packages implementation khác có thể vẫn được plugin loader định nghĩa riêng. Đây là thiết kế cho phép isolation có chủ đích **mà không phá type safety**.

## 15. Đối chiếu với các lỗi có vẻ giống

### 15.1 LinkageError duplicate vs ClassCircularityError

**ClassCircularityError** xuất hiện khi JVM phát hiện vòng trong quan hệ class hierarchy, chẳng hạn class cố kế thừa chính nó qua bytecode bất hợp lệ. Nó thuộc nhóm LinkageError nhưng **không phải duplicate definition**.

### 15.2 NoSuchMethodError sau khi thay JAR

Đây thường là binary compatibility issue: compile-time API và runtime API không khớp. Nó có thể liên quan classpath/classloader selection nhưng không đồng nghĩa JVM áp đặt loading constraint và thấy hai Class khác nhau.

### 15.3 NoClassDefFoundError và ClassNotFoundException

**ClassNotFoundException** là checked exception từ explicit lookup; **NoClassDefFoundError** có thể xuất hiện khi JVM cần một class trong linking/runtime execution mà định nghĩa không khả dụng hoặc trước đó initialization thất bại. Việc thiếu một class cũng có thể do loader policy, nhưng cần đọc cause thay vì mặc định “duplicate class”.

### 15.4 ClassCastException: cùng tên nhưng khác loader

~~~text
class plugin.Dto cannot be cast to class plugin.Dto
~~~

Có thể thấy hai tên giống hệt, nhưng message thường có loader/module details. Đây là type mismatch tại cast, không nhất thiết loader constraint violation. Dùng **getClassLoader** và Class identity kiểm tra.

### 15.5 IllegalAccessError với package giống nhau

Hai classes có cùng source package nhưng do loaders khác nhau define có thể không cùng runtime package. Package-private access có thể thất bại. Đây là vấn đề runtime package/module access, không phải attempted duplicate definition.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Loader L gọi defineClass("demo.Token", bytes) hai lần. Lần hai có được coi là cập nhật class cũ không?

**Đáp án:** Không. Đó là duplicate definition và có thể ném LinkageError. Muốn bản mới cần loader mới hoặc instrumentation theo giới hạn riêng.

**Câu 2.** Loader L1 và L2 đều define demo.Token. Chỉ riêng chuyện đó có bắt buộc lỗi không?

**Đáp án:** Không. Hai runtime Class khác nhau có thể cùng tồn tại nếu không có constraint buộc phải đồng nhất.

**Câu 3.** Defining loader và initiating loader có luôn là một không?

**Đáp án:** Không. Child có thể initiate class được parent define qua delegation.

**Câu 4.** Loading constraint ký hiệu N^L1=N^L2 có yêu cầu mọi classes của hai loaders giống nhau không?

**Đáp án:** Không. Nó ràng buộc một tên type N cụ thể trong những quan hệ linking được JVM áp đặt.

**Câu 5.** Vì sao interface Api.echo(api.Payload) có thể gây constraint khi Impl ở child loader?

**Đáp án:** JVM cần type api.Payload trong method descriptor có một identity nhất quán giữa interface và implementation. Nếu child dùng một Class api.Payload riêng, ràng buộc có thể bị vi phạm.

**Câu 6.** LinkageError có phải luôn ném ngay ở defineClass không?

**Đáp án:** Không. Có trường hợp lỗi xuất hiện khi preparation/resolution hoặc bước liên kết tương ứng sau đó. Ví dụ 2 thấy lỗi lúc tạo instance.

**Câu 7.** Vì sao bản safe của ví dụ 2 chạy được?

**Đáp án:** Loader safe không định nghĩa api.Payload riêng; nó dùng cùng shared Payload Class từ parent.

**Câu 8.** Có nên catch LinkageError rồi fallback parent như ClassNotFoundException không?

**Đáp án:** Không. LinkageError biểu thị lỗi linking/consistency, cần sửa classloader policy, duplicate definitions hoặc artifacts.

**Câu 9.** Nếu a.getName().equals(b.getName()) true nhưng a==b false, đây có phải trực tiếp là loading constraint violation?

**Đáp án:** Chưa. Đó chỉ cho thấy hai Class identities khác nhau. Loading constraint violation còn đòi một ràng buộc JVM bắt chúng đồng nhất.

**Câu 10.** API interface và DTO dùng giữa host/plugin nên do loader nào define?

**Đáp án:** Thường do một loader parent/shared được cả hai sử dụng; implementation nội bộ được plugin loader define riêng.

**Câu 11.** Race duplicate definition thường đến từ thiếu thao tác nào?

**Đáp án:** Thiếu phối hợp findLoadedClass và getClassLoadingLock(name) trong toàn bộ đường check/load/define.

**Câu 12.** Vì sao đổi classpath JAR mà không tạo loader mới có thể vẫn nhận bản cũ?

**Đáp án:** Class đã được JVM định nghĩa/initiate; loadClass có thể trả Class cũ. JVM không tự định nghĩa lại nó khi file trên đĩa đổi.

## 17. Tóm tắt

~~~text
Một tên class N được yêu cầu
       |
       v
Cùng loader cố define lại N đã biết?
       |
       +-- Có -> LinkageError (duplicate definition)
       |
       +-- Không
             |
             v
Hai loaders có hai Class khác cho N?
             |
             +-- Có nhưng không có constraint
             |        -> có thể hợp lệ (loader isolation)
             |
             +-- Có và JVM yêu cầu N^L1 = N^L2
                      -> LinkageError (constraint violation)
~~~

**Ba nguyên tắc quan trọng nhất:**

1. **Định nghĩa trùng trong cùng loader không phải reload**; bảo toàn cache/lock và tạo loader mới nếu cần runtime version mới.
2. **Hai loaders cùng tên class không tự sai**; sai khi JVM buộc các type trong một method/field boundary phải đồng nhất mà chúng khác Class identity.
3. **Dùng chung API/DTO từ một defining loader ổn định**, còn implementation có thể cô lập theo loader; kiểm tra bằng Class object và defining loader, không chỉ bằng tên chuỗi.

### Nguồn chuẩn để tra cứu sâu

- JVMS Java SE 21 **§5.3.4 Loading Constraints**: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3.4
- JVMS Java SE 21 **§5.3.5 Deriving a Class from a class File Representation**: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3.5
- JVMS Java SE 21 **§5.4.2 Preparation và §5.4.3 Resolution**: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.4
- JLS Java SE 21 **§12.2 Loading of Classes and Interfaces**: https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.2
- ClassLoader Java SE 21 API: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html
- LinkageError Java SE 21 API: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/LinkageError.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-class-loading-errors-007">NoSuchMethodError và NoSuchFieldError do lệch phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

# NoSuchMethodError và NoSuchFieldError do lệch phiên bản thư viện trong Java

Một chương trình Java có thể **biên dịch thành công** nhưng khi chạy lại phát sinh:

```text
java.lang.NoSuchMethodError: ...
java.lang.NoSuchFieldError: ...
```

Hai lỗi này thường xuất hiện khi **mã client đã được biên dịch với API của phiên bản thư viện A**, nhưng JVM khi thực thi lại nạp **định nghĩa class từ phiên bản B không còn thành viên đúng tên và descriptor**. Đây là lỗi **binary linkage / binary compatibility**, không đơn thuần là trình biên dịch “không tìm thấy method” trong source code.

Trong Java, bytecode lưu các **symbolic references** tới method/field trong *constant pool* của class caller. Khi liên kết tham chiếu, JVM cần tìm **đúng class và thành viên với tên cùng descriptor** mà bytecode đã ghi nhận. Nếu class được nạp có tồn tại nhưng method/field được tham chiếu **không còn khớp**, JVM có thể ném `NoSuchMethodError` hoặc `NoSuchFieldError`.

Bài thuộc nhóm **Lỗi thường gặp** trong Java core – classloader, tập trung vào: nguyên nhân do lệch phiên bản, cơ chế symbolic resolution, tên + descriptor (bao gồm kiểu trả về và kiểu field), ví dụ Java 17+ tái hiện **cả hai lỗi**, cách kiểm tra class thực sự được loader nào nạp, xử lý dependency conflict, đóng gói JAR/fat JAR, hệ plugin/classloader và chiến lược phòng ngừa.

## 1. Hiểu đúng loại lỗi: LinkageError, không phải Exception thông thường

Thứ bậc quan trọng:

```text
java.lang.Throwable
├── java.lang.Exception
│   └── java.lang.ReflectiveOperationException
│       └── java.lang.NoSuchMethodException
└── java.lang.Error
    └── java.lang.LinkageError
        ├── java.lang.IncompatibleClassChangeError
        │   ├── java.lang.NoSuchMethodError
        │   ├── java.lang.NoSuchFieldError
        │   ├── java.lang.IllegalAccessError
        │   ├── java.lang.AbstractMethodError
        │   └── ...
        ├── java.lang.NoClassDefFoundError
        ├── java.lang.VerifyError
        └── ...
```

`NoSuchMethodError` và `NoSuchFieldError` là **Error** thuộc họ **IncompatibleClassChangeError → LinkageError**, thường biểu hiện rằng các binary class được phối hợp khi chạy **không còn tương thích với nhau**.

**Không nhầm:**

- `NoSuchMethodException`: một API reflection như `Class.getMethod("m", ...)` không tìm ra member theo kiểu tìm kiếm reflection, và caller có thể xử lý checked exception này.
- `NoSuchMethodError`: bytecode đã compile tham chiếu method, nhưng khi JVM resolve runtime symbolic reference, method phù hợp không có.
- `NoSuchFieldException`: reflection `Class.getDeclaredField("x")` không tìm được field.
- `NoSuchFieldError`: bytecode có symbolic reference tới field, JVM field resolution không tìm được field phù hợp.

Trong production, không nên dùng `catch (NoSuchMethodError e) { /* bỏ qua */ }` như cách sửa lâu dài. Bắt lỗi có thể hữu ích trong ví dụ chẩn đoán; ứng dụng thật cần **sửa dependency/runtime classpath và khôi phục binary compatibility**.

## 2. Vì sao source compile được mà runtime vẫn thất bại?

Hãy tưởng tượng có ba artifact:

```text
vendor-api-v1.jar      client.jar       vendor-api-v2.jar
    |                     |                    |
    |  compile dependency |                    |
    +-------------------->|                    |
                          | bytecode client     |
                          | lưu method/field    |
                          | v1 symbols          |
                          |                    |
                          +------------------->| Runtime classloader
                                               | nạp Vendor từ v2
                                               |
                                               v
                                   Method/field của v1 không tồn tại
                                   với đúng descriptor trong v2
                                               |
                                               v
                                       LinkageError ở runtime
```

Lúc biên dịch, `javac` nhìn thấy `lib.Vendor.format()` và `lib.Vendor.LEVEL` trong v1. Trình biên dịch tạo bytecode và constant pool ghi rõ **symbolic references** mà các lệnh gọi sẽ sử dụng. Sau đó build hoặc deployment **không bắt buộc mang theo đúng binary v1**: có thể thư viện bị nâng cấp, hạ cấp, loại bởi dependency mediation hoặc bị JAR khác che khuất trên classpath.

Nếu chỉ chạy lại **client.class cũ** với **vendor-api-v2.jar**, JVM không “biên dịch lại” caller để tự thay `Vendor.format()` bằng tên method mới, và cũng không tự xem một field khác như replacement.

Kết quả là:

- **Compile-time API:** bộ thành viên mà client đã compile against.
- **Runtime definition:** bộ thành viên của class mà JVM **thực sự nạp**.
- **Linkage:** JVM cố nối symbolic reference trong client với runtime definition.
- **Mismatch:** thành viên không tồn tại đúng tên/descriptor → lỗi phù hợp.

Điểm này đặc biệt quan trọng trong ứng dụng đa module, plugin, application server, Gradle/Maven nhiều dependencies và các thư viện có transitive dependencies.

## 3. Method resolution thực sự tìm cái gì?

Một method symbolic reference trong class file lưu:

1. **Class/interface owner** theo binary name.
2. **Method name**, ví dụ `format`.
3. **Method descriptor** gồm **kiểu các tham số** và **kiểu trả về**.

Ví dụ Java:

```java
public static String format()
```

được biểu diễn trong JVM descriptor (không có tham số, trả `java.lang.String`):

```text
format:()Ljava/lang/String;
```

Một overload:

```java
public static int format(int n)
```

sử dụng descriptor:

```text
format:(I)I
```

Các ký hiệu thường gặp:

| Java type | JVM field descriptor |
| --- | --- |
| `boolean` | `Z` |
| `byte` | `B` |
| `char` | `C` |
| `short` | `S` |
| `int` | `I` |
| `long` | `J` |
| `float` | `F` |
| `double` | `D` |
| `void` (chỉ kết quả method) | `V` |
| `java.lang.String` | `Ljava/lang/String;` |
| `int[]` | `[I` |
| `String[]` | `[Ljava/lang/String;` |

**Return type cũng có trong JVM method descriptor**, mặc dù một số quy tắc source-level về overloading trong Java không cho phân biệt method chỉ dựa kiểu trả về. Vì thế một thay đổi return type có thể phá binary linkage dù method name và danh sách tham số nhìn bằng mắt vẫn giống.

Theo JVMS 17 `5.4.3.3, method resolution dùng tên và descriptor của reference. Nếu lookup thất bại, `NoSuchMethodError` có thể được ném. Tuy nhiên method có thể vẫn được tìm ở class cha hoặc interface theo các quy tắc resolution; **không phải cứ một class xóa declaration cục bộ thì mọi client lập tức lỗi** nếu vẫn có inherited method tương thích.

### Thay đổi nào thường dẫn tới NoSuchMethodError?

| API mà client đã compile | API runtime khác | Nguy cơ |
| --- | --- | --- |
| `String format()` | Xóa `format()` | Cao: reference cũ không resolve được |
| `String format()` | Đổi tên thành `describe()` | Cao: tên khác |
| `String format()` | Đổi thành `String format(String mode)` | Cao: parameter descriptor khác |
| `String format()` | Đổi thành `Object format()` | Có thể NoSuchMethodError do **return descriptor khác** |
| `Vendor()` public no-arg | Chỉ còn constructor `Vendor(int)` | Có thể `NoSuchMethodError: <init>()V` |
| `format()` instance | `format()` static cùng descriptor | Có thể **IncompatibleClassChangeError**, không mặc nhiên là NoSuchMethodError |
| Method còn nhưng giảm visibility | Ví dụ public thành private | Có thể `IllegalAccessError` thay vì NoSuchMethodError |

Đừng gộp mọi sửa API thành một lỗi duy nhất: JVM phân biệt **không tìm được member**, **không có quyền truy cập**, và **sai loại static/instance**.

## 4. Field resolution và vì sao kiểu field có ý nghĩa

Tương tự, symbolic reference tới field lưu:

1. Owner class/interface.
2. Field name.
3. **Field descriptor** = kiểu của field.

Ví dụ:

```java
public static int LEVEL = 7;
```

tạo field reference:

```text
lib/Vendor.LEVEL:I
```

Nếu runtime có `public static long LEVEL = 7L;`, tên `LEVEL` vẫn tồn tại nhưng **descriptor đổi từ `I` sang `J`**. Reference cũ `LEVEL:I` có thể thất bại với `NoSuchFieldError` vì JVM đang tìm một field **đúng tên và descriptor**, không chỉ “tên gần giống”.

Theo JVMS 17 `5.4.3.2, lookup xét class/interface owner, các superinterfaces/superclasses theo quy tắc rồi nếu không tìm ra field với name+descriptor thì ném `NoSuchFieldError`. Có những thay đổi cấu trúc vẫn tương thích nhờ inherited field; cần xét binary thực thay vì đoán từ source diff.

### Thay đổi field và loại lỗi có thể xảy ra

| V1 compile API | V2 runtime API | Nhận xét |
| --- | --- | --- |
| `public static int LEVEL` | Xóa `LEVEL` | Thường dẫn tới `NoSuchFieldError` |
| `public int level` | Đổi tên thành `count` | Reference cũ không thấy tên |
| `public static int LEVEL` | `public static long LEVEL` | Descriptor khác; có thể `NoSuchFieldError` |
| `public int count` | `public static int count` | Opcode field access không phù hợp; có thể `IncompatibleClassChangeError` |
| `public int count` | `private int count` | Có thể `IllegalAccessError` |
| `public static final int VERSION = 1` | Đổi giá trị thành 2 | **Có thể không lỗi** do constant đã inline vào client; vẫn thấy giá trị cũ |

**Trường hợp `static final` compile-time constant** rất đặc biệt: nếu field là *constant variable* (`static final` primitive hoặc `String` với constant expression), `javac` có thể **inline giá trị** vào client thay vì phát lệnh `getstatic`. Khi thay/loại field ở runtime, client cũ có thể **không phát `NoSuchFieldError`** vì bytecode **không còn symbolic field reference** tại chỗ sử dụng ấy, nhưng giá trị lại bị cũ.

Đó là lý do ví dụ tái hiện chính xác ở phần tiếp theo dùng **`public static int LEVEL` không final**, để buộc client bytecode có `getstatic`.

## 5. Ví dụ Java 17+ tái hiện cả hai lỗi bằng v1/v2

Ví dụ có ba nhóm source độc lập: thư viện v1, thư viện v2, và client chỉ compile với v1. **Client KHÔNG được biên dịch lại khi chuyển runtime sang v2** — đây là điều kiện tạo lỗi binary mismatch.

### 5.1 Cấu trúc

```text
linkage-demo/
├── v1-src/
│   └── lib/Vendor.java
├── v2-src/
│   └── lib/Vendor.java
├── client-src/
│   └── app/Client.java
├── v1-classes/          (output thư viện v1)
├── v2-classes/          (output thư viện v2)
└── client-classes/      (output client compile against v1)
```

Tạo các output directories trước khi chạy. Không đặt cùng lúc hai file `lib/Vendor.java` vào **một source root**, vì chúng là **hai phiên bản khác nhau của cùng binary class name**.

### 5.2 Thư viện v1

File `v1-src/lib/Vendor.java`:

```java
package lib;

public final class Vendor {
    public static int LEVEL = 7; // Non-final: client phải dùng getstatic.

    public static String format() {
        return "v1 format";
    }
}
```

Client sẽ compile thành công vì v1 có cả method `format()` và field `LEVEL`.

### 5.3 Thư viện v2 không tương thích

File `v2-src/lib/Vendor.java`:

```java
package lib;

public final class Vendor {
    public static int REVISION = 2;

    public static String describe() {
        return "v2 describe";
    }
}
```

Class **`lib.Vendor` vẫn tồn tại**, nhưng hai thành viên mà client đã sử dụng bị thay tên: `format()` không còn, và `LEVEL` không còn. Nếu compile client trực tiếp với v2, `javac` sẽ báo không tìm thấy symbols; vấn đề ở đây là **client.class đã được compile trước đó với v1** nên lỗi chỉ xuất hiện khi chạy.

### 5.4 Client compile với v1

File `client-src/app/Client.java`:

```java
package app;

import lib.Vendor;

public class Client {
    public static void main(String[] args) {
        try {
            System.out.println("method=" + Vendor.format());
        } catch (NoSuchMethodError error) {
            System.out.println("method link="
                    + error.getClass().getSimpleName());
        }

        try {
            System.out.println("field=" + Vendor.LEVEL);
        } catch (NoSuchFieldError error) {
            System.out.println("field link="
                    + error.getClass().getSimpleName());
        }

        System.out.println("actual loaded class="
                + Vendor.class.getName());
    }
}
```

**Chỉ để học:** hai `catch` được cố ý đặt để chương trình tiếp tục sau mỗi lỗi và chứng minh cả hai lỗi độc lập. Trong ứng dụng thật, lỗi linkage cho thấy dependency graph không phù hợp; cần fail-fast và sửa artifact, không dùng `catch(Error)` để coi trạng thái là bình thường.

### 5.5 Compile cả ba nhóm theo thứ tự đúng

Dùng JDK 17+ từ thư mục `linkage-demo`:

```bash
javac --release 17 -d v1-classes v1-src/lib/Vendor.java
javac --release 17 -cp v1-classes -d client-classes client-src/app/Client.java
javac --release 17 -d v2-classes v2-src/lib/Vendor.java
```

Thứ tự có ý nghĩa:

1. Compile v1 tạo class chứa API mà client cần.
2. Compile client **chỉ với v1**. Constant pool của `app.Client` ghi references tới v1 API.
3. Compile v2 vào output khác, không đụng `client-classes` đã tạo.
4. Chỉ đổi classpath **khi chạy**, không recompile client.

### 5.6 Chạy với v1: binary phù hợp

**Windows:**

```bash
java -cp "client-classes;v1-classes" app.Client
```

**Linux/macOS:**

```bash
java -cp "client-classes:v1-classes" app.Client
```

Stdout **đã kiểm chứng bằng `javac --release 17` và JVM**:

```text
method=v1 format
field=7
actual loaded class=lib.Vendor
```

Client và runtime đang dùng cùng API v1 nên cả hai symbolic references resolve thành công.

### 5.7 Chạy client cũ nhưng chỉ với v2: tái hiện lỗi

**Windows:**

```bash
java -cp "client-classes;v2-classes" app.Client
```

**Linux/macOS:**

```bash
java -cp "client-classes:v2-classes" app.Client
```

Stdout **đã kiểm chứng**:

```text
method link=NoSuchMethodError
field link=NoSuchFieldError
actual loaded class=lib.Vendor
```

Cách diễn giải:

- `NoSuchMethodError`: caller còn reference tới `lib.Vendor.format:()Ljava/lang/String;`, nhưng v2 không định nghĩa member ấy.
- `NoSuchFieldError`: caller còn reference tới `lib.Vendor.LEVEL:I`, nhưng v2 không định nghĩa field đó.
- `actual loaded class=lib.Vendor`: binary name vẫn giống, cho thấy **tìm được class không bảo đảm tìm được method/field cần thiết**. Dòng này **không chứng minh file v1/v2 nào đã được nạp**, vì cả hai có cùng tên — để xem nguồn chính xác phải kiểm tra CodeSource/class-loading diagnostics.

Chính sự khác nhau giữa **thành công khi dùng v1** và **hai lỗi khi chỉ thay runtime bằng v2** làm rõ nguồn gốc **binary incompatibility**.

## 6. Dùng javap xác nhận bytecode đang cần descriptor nào

Tool `javap` cho phép đọc constant pool và bytecode của **client.class cũ**:

```bash
javap -classpath client-classes -c -verbose app.Client
```

Trong kết quả ví dụ đã xác minh, các đoạn quan trọng gồm:

```text
Methodref ... // lib/Vendor.format:()Ljava/lang/String;
Fieldref  ... // lib/Vendor.LEVEL:I
invokestatic ... // Method lib/Vendor.format:()Ljava/lang/String;
getstatic    ... // Field lib/Vendor.LEVEL:I
```

`invokestatic` được phát vì client compile method tĩnh. `getstatic` được phát vì client đọc field tĩnh **không phải compile-time constant**. Các entry trong constant pool ghi rõ **owner/name/descriptor**.

Sau đó xem **bản runtime**:

```bash
javap -classpath v2-classes -public lib.Vendor
```

Bản v2 có `REVISION` và `describe()`, không có `format()`/`LEVEL`. Trên classpath production, thay `v2-classes` bằng đường tới **JAR được JVM thực sự nạp** (không phải chỉ dependency mà IDE hiển thị).

### Vì sao không chỉ xem source?

Source hiện tại có thể là phiên bản khác so với:

- Class files đã được compile từ tuần trước.
- Artifact trong `build/libs` hay `target` của một module khác.
- JAR từ Maven/Gradle local cache hoặc repository.
- JAR được copy vào server `lib`, plugin folder hoặc Docker image.
- Class bị shading/relocation hoặc class loader che khuất.

**Chẩn đoán linkage phải nhìn bytecode caller và binary runtime**, không chỉ code source đang mở.

## 7. ClassLoader chọn bản class runtime nào?

JVM resolve class symbolic reference thông qua **defining loader của class caller và quy tắc class loading liên quan**, không bằng cách “tìm tất cả JAR rồi tự chọn API phù hợp với phương thức”.

Trên application classpath thông thường, cùng binary name xuất hiện trong hai JAR có thể dẫn tới tình huống **JAR đứng trước hoặc loader policy xác định class được định nghĩa trước**, khiến client nạp bản không kỳ vọng. Với custom loader, **parent-first/child-first**, module mapping, code source và class identity đều ảnh hưởng.

Ví dụ:

```text
Client.class (compiled with Vendor v2 API)
         |
         v
Application loader
   |
   +-- old-v1.jar chứa lib/Vendor.class
   +-- new-v2.jar chứa lib/Vendor.class
         |
         v
JVM có thể nạp phiên bản được tìm trước
         |
         v
NoSuchMethodError / NoSuchFieldError
```

**Không có cơ chế tự động “method thiếu thì thử JAR khác”.** Một khi `lib.Vendor` đã được định nghĩa bởi loader tương ứng, nó có identity và members xác định; JVM không chọn file thứ hai chỉ vì symbolic reference tới method không resolve.

Nếu dùng plugin loader khác nhau, cùng tên `lib.Vendor` có thể được hai **defining class loaders** độc lập định nghĩa thành hai runtime types. Khi client và provider nói chuyện qua API chung, mismatch giữa class loaders có thể gây thêm `ClassCastException`, `LinkageError`/loader constraint violation, ngoài các versioning errors đang nghiên cứu.

### In code source và defining loader

Một đoạn debug đặt ở code thực sự nhìn thấy `lib.Vendor`:

```java
Class<?> type = lib.Vendor.class;

System.out.println("name=" + type.getName());
System.out.println("loader=" + type.getClassLoader());
System.out.println("module=" + type.getModule());

java.security.ProtectionDomain pd = type.getProtectionDomain();
java.security.CodeSource cs = (pd == null) ? null : pd.getCodeSource();

System.out.println("source="
        + (cs == null ? "<unknown>" : cs.getLocation()));
```

Đây là **đoạn minh họa trong method Java**, không có stdout cố định vì loader/module/path thay đổi theo runtime. `CodeSource` có thể `null` (đặc biệt với platform classes, custom loaders hoặc policy cụ thể); không được khẳng định mọi class đều có file URL.

Điểm cần xem nhất là: **loader nào define class, module nào chứa class, CodeSource nào, và JAR/runtime artifact có API descriptor nào**.

### JVM class loading logs

Với HotSpot Java 17+:

```bash
java -Xlog:class+load=info -cp "client-classes;v2-classes" app.Client
```

Lệnh này minh họa Windows; Linux/macOS đổi `;` thành `:`. JVM ghi thông tin class loading; tìm `lib.Vendor` trong log để biết **nguồn mà VM đã nạp**, tùy format/version. Trong production nên dùng output log có kiểm soát vì `class+load=info` có thể sinh nhiều dòng.

Một lựa chọn phổ biến khác để kiểm tra class artifacts là lệnh:

```bash
jar --list --file path/to/vendor-api.jar
```

Kiểm tra đúng `lib/Vendor.class` và không có phiên bản duplicate không mong muốn.

## 8. Không chỉ đổi tên: đổi kiểu trả về hoặc kiểu field cũng phá binary API

Một sự cố rất thực tế:

**V1:**

```java
package lib;
public class Settings {
    public String mode() { return "safe"; }
    public int timeout = 10;
}
```

**V2:**

```java
package lib;
public class Settings {
    public CharSequence mode() { return "safe"; }
    public long timeout = 10;
}
```

Hai đoạn là **hai phiên bản của cùng `lib.Settings`**, không compile cùng lúc trong cùng source root. Source-level code kiểu:

```java
System.out.println(settings.mode());
System.out.println(settings.timeout);
```

có thể có vẻ vẫn gần giống, nhưng **JVM references đã đổi**:

```text
V1 method: lib/Settings.mode:()Ljava/lang/String;
V2 method: lib/Settings.mode:()Ljava/lang/CharSequence;

V1 field:  lib/Settings.timeout:I
V2 field:  lib/Settings.timeout:J
```

Đối với client đã compile với v1, việc chỉ thay runtime `Settings.class` bằng v2 **có thể** tạo `NoSuchMethodError` và `NoSuchFieldError` khi reference cũ không còn được tìm trong class hierarchy.

Một ngoại lệ cần hiểu: compiler có thể tạo **bridge methods** trong một số tình huống generics/covariant overrides để bảo toàn method descriptors, và JVM resolution có lookup trong hierarchy. Vì vậy **không kết luận chắc từ Java source diff**; phải kiểm tra bytecode thật bằng `javap -s -p` hoặc `javap -verbose`.

## 9. Tại sao lỗi có thể chỉ xuất hiện khi một code path được chạy?

JVM phân biệt loading, linking (verification/preparation/resolution) và initialization. Việc resolution symbolic references có thể được JVM thực hiện ở những thời điểm khác nhau phù hợp đặc tả, và nhiều lỗi chỉ trở nên quan sát được khi **tham chiếu cụ thể được sử dụng**.

Ví dụ:

```java
if (featureEnabled) {
    lib.Vendor.format();
}
```

Đây là đoạn minh họa, giả định `featureEnabled` được xác định và client đã compile với API chứa `format()`.

- Nếu code path không chạm tới method, ứng dụng có thể chạy được một thời gian.
- Khi thực hiện reference bị thiếu, lỗi liên kết có thể xảy ra.
- JVM có thể resolve một số tham chiếu sớm, nhưng các lỗi resolution phải được quan sát theo các giới hạn đặc tả tương ứng; không nên dựa vào một timestamp duy nhất cho mọi JVM/JIT mode.

Đây là lý do integration tests chỉ gọi happy path rất hẹp có thể **không phát hiện dependency mismatch**. Thư viện vẫn cần được test trên các chức năng/entrypoints thật, kể cả optional features được triển khai sau.

**Đừng nhầm lỗi được phát hiện muộn với lỗi “method tự biến mất khi chương trình đang chạy”.** Trong JVM, bytecode caller vẫn có reference cũ; chỉ khi resolution xảy ra ta mới nhìn thấy bất tương thích có sẵn.

## 10. Phân biệt với lỗi classloading và access khác

| Lỗi | Nguyên nhân điển hình | Khác gì với hai lỗi trọng tâm? |
| --- | --- | --- |
| `ClassNotFoundException` | Loader được yêu cầu tìm binary class name nhưng không thấy | Thường là checked exception từ lookup API như `loadClass` |
| `NoClassDefFoundError` | VM cần class mà definition không sẵn có hoặc initialization trước đó lỗi | Class/dependency không khả dụng; không phải “thành viên thiếu” |
| `NoSuchMethodError` | Method symbolic reference không được resolve | Owner class có thể đã được nạp, nhưng method name+descriptor không khớp |
| `NoSuchFieldError` | Field symbolic reference không được resolve | Class có thể tồn tại nhưng field name+descriptor không khớp |
| `IllegalAccessError` | Member/class tồn tại nhưng runtime access check không cho phép | Không nhất thiết member biến mất |
| `IncompatibleClassChangeError` | Thay đổi phân loại class/member, ví dụ static ↔ instance sai kỳ vọng | Có thể member tên+descriptor vẫn có, nhưng opcode không phù hợp |
| `AbstractMethodError` | Runtime invocation chọn method abstract/thiếu concrete implementation phù hợp trong một số tình huống | Khác `NoSuchMethodError` lúc resolution |
| `ClassCastException` | Runtime types không tương thích, có thể do hai loader define cùng binary name | Type identity/casting khác field/method resolution |
| `VerifyError` | Class bytecode không qua verification | Vấn đề bytecode validity, không nhất thiết version API |

Không nên viết generic handler bắt `Throwable` rồi đổi tất cả thành thông báo “Class not found”: thông tin class owner, method/field name và descriptor trong stack trace là chìa khóa xác định dependency sai.

## 11. Những nguyên nhân lệch phiên bản thường gặp trong thực tế

### 11.1 Transitive dependency conflict

App phụ thuộc thư viện A và B; A compile với `vendor-core:2.x` trong khi B kéo `vendor-core:1.x`. Maven/Gradle dependency resolution có thể chọn một bản cụ thể theo các quy tắc build, và artifact runtime được chọn có thể **không giống bản mà một dependency đã biên dịch against**.

```text
app
├── feature-a -> vendor-core v2
└── feature-b -> vendor-core v1
                     |
                     v
                runtime chọn 1 version
                     |
                     v
        client binary của nhánh còn lại có thể không tương thích
```

Không có công thức luôn đúng “chọn version cao hơn là hết lỗi”: API v2 có thể xóa thành viên v1 hoặc thư viện v1 không hỗ trợ runtime mới. Cần chọn **tập versions tương thích** và kiểm chứng thực tế.

### 11.2 Duplicated JAR trên classpath/server

`app/lib/vendor-v2.jar` và `server/lib/vendor-v1.jar` cùng có `lib.Vendor`. App classloader có thể delegate parent và nhận class v1 từ server trước khi tự tìm v2. Triệu chứng giống “tôi đã thêm v2 vào app mà vẫn lỗi”.

### 11.3 Shaded/fat JAR đóng gói bản cũ

Build tool tạo uber-JAR nhưng merge classes/resources sai, lưu `lib/Vendor.class` cũ mặc dù dependency metadata hiển thị v2. Cần kiểm tra **artifact cuối thực sự chạy**, không chỉ declared dependencies.

### 11.4 IDE/build cache hoặc incremental build không sạch

Một module đã compile lại v2, nhưng client module khác vẫn dùng class file cũ. Runtime lấy binary mới nhưng caller bytecode chưa recompile, gây mismatch. Clean rebuild cần đi cùng kiểm tra dependency graph; không phải lúc nào “xóa cache” cũng sửa xung đột dependency thực.

### 11.5 Plugin/shared framework version skew

Plugin compile với host SDK v2 nhưng container runtime chỉ export shared API v1. Vì host parent loader define shared API, plugin dù chứa JAR v2 vẫn có thể delegate tới v1 và gặp `NoSuchMethodError`.

### 11.6 Deployment artifact còn cũ

Server, Docker image hoặc classpath script đang tham chiếu JAR cũ, trong khi developer đang xem file mới trên máy khác. Nhìn `CodeSource` và build artifact hash/version là cần thiết.

## 12. Quy trình debug có thứ tự, tránh sửa mò

Khi stack trace có `NoSuchMethodError` hoặc `NoSuchFieldError`, làm theo các bước:

**Bước 1 — Ghi nguyên lỗi và frame caller.**

Ví dụ giả định:

```text
java.lang.NoSuchMethodError:
  'java.lang.String lib.Vendor.format()'
  at app.Client.main(Client.java:7)
```

Từ thông điệp và frame, biết **caller** là `app.Client`, owner là `lib.Vendor`, method kỳ vọng là `format()` trả `String`. Field error tương tự có thể ghi tên field và kiểu kỳ vọng. Format thông báo **không cố định** giữa các phiên bản JVM nên đừng parse string cứng làm logic ứng dụng.

**Bước 2 — Tìm class thực sự được runtime nạp.**

In:

```java
System.out.println(lib.Vendor.class.getClassLoader());
System.out.println(lib.Vendor.class.getModule());
System.out.println(lib.Vendor.class.getProtectionDomain()
        .getCodeSource());
```

Đoạn minh họa cần xử lý trường hợp protection domain/code source null khi triển khai thực; nếu class cần điều tra không load được qua source, dùng `-Xlog:class+load=info` hoặc debugger.

**Bước 3 — Xem method/field đang có trong class runtime.**

```bash
javap -classpath path/to/runtime-vendor.jar -p -s lib.Vendor
```

Với JAR có đúng tên class, `javap` cho các descriptor thay vì chỉ Java signatures đẹp mắt.

**Bước 4 — Xem bytecode caller compile against gì.**

```bash
javap -classpath path/to/client.jar -verbose -c app.Client
```

Tìm `Methodref`/`Fieldref` và opcode. So sánh **owner + name + descriptor** với runtime API.

**Bước 5 — Tìm dependency version thực tế trong build.**

- Maven: `mvn dependency:tree`, kiểm tra scoped/transitive/excluded versions.
- Gradle: `./gradlew dependencies --configuration runtimeClasspath`, hoặc `dependencyInsight` cho module nghi ngờ (Windows Gradle wrapper là `gradlew.bat`).
- Nếu plugin/container: xem cả **host/server libs và plugin classpaths**, không chỉ một build file.

**Bước 6 — Sửa dependency constraints và artifact.**

Chọn một API version được **tất cả caller binaries** hỗ trợ. Nếu API breaking change là chủ đích, rebuild các consumers với phiên bản mới và sửa code. Loại duplicate JAR/parent conflicts. Nếu cần hai versions thật sự, cô lập bằng classloaders/process và chỉ chia sẻ API ổn định.

**Bước 7 — Clean build, redeploy và chạy lại cùng đường runtime.**

Không chỉ reload IDE. Xóa artifact triển khai cũ có chủ đích, build lại module client nếu API thay đổi, kiểm tra container image/server library placement và chạy integration tests bao gồm code path từng lỗi.

### Checklist log tối thiểu khi hỗ trợ production

```text
Error type: NoSuchMethodError / NoSuchFieldError
Caller class and stack frame
Symbolic owner/name/descriptor
Defining loader of caller
Defining loader of owner
Module/layer if named modules used
Owner CodeSource / actual JAR
Classpath/module-path and deployment version
Actual method/field descriptors (javap)
Build dependency graph
```

Thông tin này thường đủ để phân biệt “JAR runtime quá cũ”, “JAR runtime quá mới và xóa API”, “parent loader che bản đúng” và “client.class chưa được rebuild”.

## 13. Cách sửa đúng và cách không nên làm

| Cách làm | Đánh giá |
| --- | --- |
| Đồng bộ phiên bản compiler/runtime dependencies | **Nên**, nhưng phải kiểm tra binary compatibility thực |
| Rebuild client khi dependency API thay đổi | **Nên**, nhất là breaking changes |
| Loại duplicate JAR hoặc cấu hình classloader policy hợp lý | **Nên**, dựa trên CodeSource/loader đã xác minh |
| Dùng dependency management/constraints/locking | **Nên** để build tái lập |
| Thêm compatibility overload/deprecated method giữ descriptor cũ | **Có ích** khi tác giả thư viện cần duy trì binary compatibility |
| Duy trì adapter/bridge tại API boundary | **Có thể**, nếu thiết kế rõ version contract |
| Catch rồi bỏ qua `NoSuchMethodError` | **Không nên** trong nghiệp vụ bình thường |
| Cứ thêm JAR v2 ở cuối classpath khi v1 đã được parent nạp | **Không đủ**; loader có thể vẫn chọn v1 |
| Đổi TCCL và nghĩ `Class.forName(String)`/bytecode linkage đều sẽ dùng loader mới | **Không đúng**; API lựa chọn loader khác nhau |
| Gọi `System.gc()`/`URLClassLoader.close()` để “làm method mới xuất hiện” | **Không đúng**; loader/class đã định nghĩa không tự thay API |
| Xóa toàn bộ cache một cách mù quáng | **Không nên**; xác định nguồn conflict rồi sửa |
| Dựa vào thứ tự classpath không được kiểm soát | **Rủi ro**; ưu tiên một version được kiểm soát và kiểm chứng |

Nếu bạn phát hành thư viện:

- Giữ method/field đã public mà consumers đang sử dụng, hoặc cung cấp **binary-compatible bridge/overload** khi có thể.
- Khi thay tham số/return/field type, đánh giá tác động tới *bytecode descriptors*, không chỉ Java source compilation.
- Tuân thủ versioning policy rõ, công bố breaking changes và runtime requirements.
- Test bằng **binaries được compile với version trước** chạy với version mới để phát hiện lỗi liên kết.
- Phân biệt **source compatibility** với **binary compatibility**: có thay đổi khiến source recompilation thành công nhưng binary cũ không chạy, và ngược lại.
- Không làm biến mất public no-arg constructor đã được caller compile against một cách tình cờ.

## 14. Các tình huống nâng cao: method signature và field constants

### 14.1 Thêm overload mới thường không phá binary cũ

Nếu v1:

```java
public String convert(int value)
```

và v2 vẫn giữ method ấy rồi **thêm**:

```java
public String convert(long value)
```

thì symbolic reference cũ `convert:(I)Ljava/lang/String;` vẫn có thể resolve bình thường. “Thêm overload” nói chung khác hẳn “đổi tham số của method cũ”.

Nếu thay hẳn method v1 bằng overload nhận `long` và xóa bản nhận `int`, client cũ không tự chuyển descriptor; có thể `NoSuchMethodError`.

### 14.2 Đổi instance method thành static: không nhất thiết NoSuchMethodError

Client v1 có `invokevirtual` tới instance method, runtime v2 có cùng tên/descriptor nhưng method thành static: lỗi điển hình là **`IncompatibleClassChangeError`** do opcode yêu cầu instance method nhưng runtime member static. Nên đọc opcode `invokevirtual` / `invokestatic` và error message, không ép mọi linkage về method missing.

### 14.3 Thay field static thành instance: không nhất thiết NoSuchFieldError

Client v1 có `getstatic` mà runtime field mới là instance, JVM có thể báo **`IncompatibleClassChangeError`**. Nếu field đổi cả kiểu dữ liệu, `NoSuchFieldError` có thể xảy ra trước ở giai đoạn lookup. Thứ tự phụ thuộc tham chiếu và loại thay đổi thực tế.

### 14.4 Compile-time constants có thể tạo “lỗi giá trị cũ” âm thầm

V1:

```java
public static final int BUILD_ID = 101;
```

v2:

```java
public static final int BUILD_ID = 202;
```

Client đã compile với v1 thường chứa literal `101` do constant inlining. Khi runtime dùng v2, client có thể **vẫn in 101 mà không ném `NoSuchFieldError`**. Cần recompile consumer để cập nhật giá trị hoặc tránh dựa vào public constant variables khi giá trị được kỳ vọng thay đổi runtime.

Đối với `static final` object không phải compile-time constant (ví dụ một `List` hay expression không phải constant expression), client có thể vẫn phát field reference và chịu linkage rules bình thường.

### 14.5 Generics, erasure và bridge methods

Method Java generic:

```java
public java.util.List<String> items()
```

thường có erased JVM return descriptor `()Ljava/util/List;` (thông tin generic được lưu thêm qua Signature attribute). Đổi generic argument từ `String` sang `Object` có thể không đổi **erased descriptor** và vì vậy không nhất thiết phát `NoSuchMethodError`, dù type-safety/behavior có thể gặp vấn đề.

Đổi raw return type, parameter erasure hoặc bridge methods lại có thể ảnh hưởng descriptors. Khi nghi mismatch, **`javap -s -p` là nguồn kiểm tra chính xác**, không suy từ angle brackets trong source.

## 15. Lưu ý khi triển khai plugin và custom classloader

Trong kiến trúc plugin, host có thể đã nạp SDK/API version cũ qua **parent loader**. Dù plugin JAR chứa SDK mới, child loader sử dụng **parent-first delegation** có thể lấy class API từ host. Khi plugin code được compile với API mới gọi method thiếu trên API cũ, nó phát `NoSuchMethodError`.

```text
Host/System loader: api.Shared v1
       ^
       | parent-first
Plugin loader:
   - plugin.Entry compiled against api.Shared v2
   - plugin.jar có thể chứa api.Shared v2
       |
       v
Runtime plugin.Entry gọi api.Shared.newMethod()
       |
       v
api.Shared v1 do parent define -> NoSuchMethodError
```

Hướng xử lý:

1. **Xác định host-plugin API compatibility matrix.** Plugin phải khai báo minimum host API version và được kiểm tra trước khi load.
2. **Giữ shared API ở loader chung** để host và plugin dùng cùng runtime types. Không tùy tiện “child-first cho cả `api.*`” vì có thể tạo bản sao API gây `ClassCastException`/loader constraints.
3. **Cô lập implementation dependencies** khi chúng không thuộc shared contract, tránh parent che phiên bản plugin cần.
4. **Fail fast ở bootstrap:** kiểm tra version/required capabilities, thay vì đợi method đặc biệt được gọi sau nhiều giờ.
5. **Không hot-replace JAR dưới một loader đang sống** rồi kỳ vọng class definition và method table đổi theo. Dùng deployment/loader mới theo lifecycle đúng nếu cần reload.
6. **Nếu dùng JPMS**, xem module graph và exports/opens ngoài class loading. `IllegalAccessError` có thể là module access, không phải version mismatch.
7. **In class loader của class owner**, không chỉ của plugin entrypoint: cùng plugin thread vẫn có thể gọi API do parent định nghĩa.

Một tình huống khác: ServiceLoader provider được nạp qua TCCL plugin đúng nhưng nó gọi host API thiếu method. **Service discovery thành công không đảm bảo provider đã tương thích binary** với host class definitions. Cần chạy integration contract tests.

## 16. Chẩn đoán bằng dependency graph và artifact consistency

Trước khi sửa, dựng lại đường từ client binary tới runtime class:

```text
Client.class
    |
    | constant_pool Methodref/Fieldref
    v
Required Owner + Name + Descriptor
    |
    | JVM class resolution
    v
Actual Class<?> (defining loader, module, CodeSource)
    |
    | field/method resolution
    v
Runtime available members
    |
    +-- exact match -----------> OK (nếu access/opcode hợp lệ)
    |
    +-- missing match ---------> NoSuchMethodError / NoSuchFieldError
    |
    +-- access mismatch -------> IllegalAccessError
    |
    +-- static mismatch -------> IncompatibleClassChangeError
```

Một khi xác định được **exact runtime JAR**, so manifest/version/checksum với build manifest. Đừng chỉ so tên file: hai artifact có cùng `vendor.jar` nhưng bytecode khác nhau, và một artifact “version 2.1” có thể được repackaged/shaded hoặc có class cũ.

**Build reproducibility**:

- Quản lý dependency versions tập trung và khóa versions được release.
- Không dùng wildcard/uncontrolled directories khi server/plugin cần API riêng.
- Trong CI, kiểm tra dependency conflicts và chạy test bằng **đúng artifact/JRE/runtime classpath** giống production.
- Đối với thư viện công bố API, cân nhắc công cụ so binary APIs giữa releases và chạy old-client-binary compatibility tests.
- Đảm bảo container/server restart hoặc redeploy làm runtime dùng artifact mới, tránh classloaders cũ còn tồn tại dưới dạng plugin deployment khác.

## 17. Tự kiểm tra có đáp án

**Câu 1.** Client dùng `Vendor.format()`, đã biên dịch với v1; runtime lại nạp `Vendor` v2 không có method ấy. Vì sao code compile thành công?

**Đáp án:** `javac` chỉ thấy API v1 lúc compile; runtime class definition v2 khác. JVM link symbolic method reference cũ và không thấy name+descriptor nên `NoSuchMethodError`.

**Câu 2.** `NoSuchMethodException` có giống `NoSuchMethodError` không?

**Đáp án:** Không. `NoSuchMethodException` là checked reflection exception; `NoSuchMethodError` là `LinkageError` khi bytecode symbolic method reference không resolve được.

**Câu 3.** Nếu runtime `Vendor` vẫn có `format()` nhưng trả `Object` thay vì `String`, có chắc không lỗi không?

**Đáp án:** Không. JVM method descriptor gồm return type; client đòi `()Ljava/lang/String;` nhưng bản runtime chỉ có `()Ljava/lang/Object;` có thể bị `NoSuchMethodError`, trừ các điều kiện inheritance/bridge tương thích cụ thể.

**Câu 4.** Vì sao đổi `int LEVEL` sang `long LEVEL` có thể phát `NoSuchFieldError`?

**Đáp án:** Field reference gồm name + type descriptor; `LEVEL:I` khác `LEVEL:J`.

**Câu 5.** Nếu đổi instance field sang static field cùng tên/kiểu, lỗi nào có thể xảy ra?

**Đáp án:** `IncompatibleClassChangeError` do opcode `getfield`/`getstatic` không phù hợp, không nhất thiết `NoSuchFieldError`.

**Câu 6.** Đã thêm JAR mới vào classpath nhưng vẫn lỗi vì sao?

**Đáp án:** Parent-first delegation, classpath precedence, server-provided library hoặc duplicate JAR có thể vẫn khiến runtime nạp bản cũ. In defining loader, module và CodeSource của class thực tế.

**Câu 7.** Cách nào xác minh client.class đang tham chiếu method descriptor nào?

**Đáp án:** `javap -classpath client-classes -verbose -c app.Client`, đọc `Methodref`/`Fieldref` và opcode.

**Câu 8.** Tại sao public `static final int` thay giá trị đôi khi không phát lỗi nhưng client vẫn thấy giá trị cũ?

**Đáp án:** `javac` có thể inline constant vào client bytecode; không còn field lookup ở chỗ dùng ấy. Recompile client hoặc thay thiết kế API nếu giá trị cần cập nhật động.

**Câu 9.** `NoSuchFieldError` có chứng minh toàn bộ class bị thiếu không?

**Đáp án:** Không. Owner class có thể đã load thành công nhưng field name+descriptor không có trong runtime hierarchy. Nếu class thiếu, lỗi thường khác như `ClassNotFoundException`/`NoClassDefFoundError` theo ngữ cảnh.

**Câu 10.** Một plugin compile với SDK v2, host chỉ cung cấp SDK v1. Đổi TCCL có chắc sửa lỗi không?

**Đáp án:** Không. TCCL chỉ ảnh hưởng API chủ động dùng nó; nếu API class đã được parent define từ v1, TCCL không tự thay metadata/method table. Cần version compatibility hoặc classloader architecture khác.

**Câu 11.** Vì sao lỗi chỉ xuất hiện khi bật tính năng hiếm dùng?

**Đáp án:** Symbolic references tới code path đó có thể chỉ được resolve khi thực thi; test startup không nhất thiết chạm tới member bị mismatch. Cần integration tests cho các chức năng trọng yếu.

**Câu 12.** Hướng sửa triệt để là gì?

**Đáp án:** Tìm chính xác binary client expectation và runtime API thực tế; thống nhất/bảo đảm binary-compatible library versions, rebuild callers khi breaking API đổi, loại duplicate/classloader conflicts, đóng gói và redeploy đúng artifact, test end-to-end.

## 18. Checklist xử lý nhanh trước khi deploy

1. **Giữ stack trace đầy đủ** và xác định owner/member/descriptor bị thiếu.
2. **Ghi đúng bản client artifact** được biên dịch và được deploy.
3. **Dùng `javap`** xem symbolic method/field references trong caller class.
4. **Xác định actual runtime class** với class-loading logs, CodeSource và defining loader.
5. **Đọc descriptor runtime** bằng `javap -p -s`.
6. **Kiểm tra duplicate classes/JARs** trên classpath, module path và thư mục của server.
7. **Kiểm tra dependency graph** của cả compileClasspath và runtimeClasspath (Maven/Gradle).
8. **Xem plugin/host parent-first policy** nếu thư viện dùng trong container.
9. **Sửa binary compatibility** chứ không chỉ source API dựa theo IDE.
10. **Rebuild/clean deploy** mọi module caller bị tác động nếu method/field descriptors thay đổi.
11. **Test runtime thật**: old client binaries với new library và các code paths hiếm.
12. **Quản lý versioning và API evolution**: tránh xóa/đổi thành viên công khai thiếu migration plan.
13. **Không dựa vào `System.gc()`/`URLClassLoader.close()`** để chữa linkage mismatch trong class đã define.
14. **Phân biệt lỗi**: `NoSuchMethodError`, `NoSuchFieldError`, `IllegalAccessError`, `IncompatibleClassChangeError`, `NoClassDefFoundError` có nguyên nhân khác nhau.

## Kết luận

**`NoSuchMethodError` và `NoSuchFieldError` thường báo rằng caller bytecode và runtime class definitions không có cùng binary API**. JVM không tra method/field bằng tên gần giống hay tự biên dịch lại client; nó resolve **symbolic owner + member name + descriptor** theo classloader và các quy tắc JVMS. Thay tên, tham số, kiểu trả về hoặc kiểu field có thể làm binary cũ không còn link, dù class owner vẫn load được.

Cách sửa đáng tin cậy: **tìm class thực sự được nạp → so `javap` client/runtime descriptors → giải quyết dependency version/loader conflict → rebuild và deploy tương thích → chạy tests bằng runtime artifacts thực**. Đây là cách giải quyết nguyên nhân, thay vì che `Error` bằng catch hoặc thêm JAR ngẫu nhiên.

**Tài liệu chuẩn Java SE 17:**

- [JVMS 17 `5.1 – Runtime Constant Pool](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.1): symbolic references cho class, method và field.
- [JVMS 17 `5.4.3.2 – Field Resolution](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3.2): field name+descriptor, `NoSuchFieldError` và access/link checks.
- [JVMS 17 `5.4.3.3 – Method Resolution](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3.3): method lookup, `NoSuchMethodError`.
- [JLS 17 Chapter 13 – Binary Compatibility](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html): các thay đổi source/binary giữ hoặc phá compatibility.
- [`NoSuchMethodError` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoSuchMethodError.html) và [`NoSuchFieldError` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoSuchFieldError.html).
- [`ClassLoader` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): defining loader và delegation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-class-loading-errors-008">AbstractMethodError và IncompatibleClassChangeError</a>

<details>
<summary>Xem chi tiết</summary>

# `AbstractMethodError` và `IncompatibleClassChangeError` trong Java: lỗi tương thích nhị phân và class loader

## 1. Hiểu đúng hai loại lỗi

`AbstractMethodError` và `IncompatibleClassChangeError` là các lỗi thuộc họ `LinkageError`. Chúng thường xuất hiện khi JVM đang chạy một tập hợp file `.class` **không còn tương thích với nhau về mặt binary**, ví dụ chương trình được biên dịch với API phiên bản A nhưng runtime lại nạp phiên bản B của API đó.

Cả hai có ý nghĩa rất thực tế ở ứng dụng dùng thư viện bên thứ ba, plugin, application server, class loader tự tạo, redeploy hoặc cơ chế hot reload: **biên dịch thành công không bảo đảm tất cả class được liên kết và gọi method thành công sau khi triển khai**.

Quan hệ kế thừa quan trọng:

```text
java.lang.Throwable
  └─ java.lang.Error
       └─ java.lang.LinkageError
            └─ java.lang.IncompatibleClassChangeError
                 ├─ java.lang.AbstractMethodError
                 ├─ java.lang.NoSuchMethodError
                 ├─ java.lang.NoSuchFieldError
                 ├─ java.lang.IllegalAccessError
                 └─ java.lang.InstantiationError
```

Lưu ý: các lớp cùng cấp trong cây không phải một lỗi duy nhất; mỗi loại cho manh mối chẩn đoán khác nhau. Chẳng hạn `NoSuchMethodError` cho biết một method được tham chiếu không thể được phân giải như mong đợi; `AbstractMethodError` liên quan **việc gọi** method mà runtime không tìm được implementation phù hợp; còn một `IncompatibleClassChangeError` trực tiếp có thể xuất hiện khi runtime nhận thấy method thực tế là instance trong khi bytecode đòi static.

| Đặc điểm | `AbstractMethodError` | `IncompatibleClassChangeError` trực tiếp |
|---|---|---|
| Bản chất | Cố gọi một method mà bước dispatch không tìm thấy implementation concrete hợp lệ | Thấy sự thay đổi cấu trúc lớp/method/field không tương thích với bytecode được biên dịch trước đó |
| Ví dụ đặc trưng | Xóa implementation `default` trên interface và thay bằng method abstract, trong khi implementation cũ chưa được biên dịch lại | Đổi method `static` thành instance nhưng caller cũ vẫn dùng `invokestatic` |
| Khi phát hiện | Có thể đến lúc method được gọi/resolved, không nhất thiết lúc ứng dụng khởi động | Có thể đến khi resolve/link hoặc khi instruction tương ứng chạy |
| Cách chữa căn bản | Tái biên dịch và phân phối **đồng bộ** contract, implementation, caller | Khôi phục binary contract hoặc cập nhật đồng bộ caller và thư viện runtime |

**Quan hệ cần nhớ:** `AbstractMethodError extends IncompatibleClassChangeError`. Vì vậy một đoạn `catch (IncompatibleClassChangeError e)` cũng bắt được `AbstractMethodError`, nhưng khi điều tra phải giữ nguyên **kiểu lỗi cụ thể**, full stack trace và thông tin class loader. Các lỗi này là `Error`, không phải checked exception; việc catch để “bỏ qua” thường khiến ứng dụng tiếp tục chạy trong trạng thái bất nhất.

## 2. Binary compatibility: vì sao compile pass mà runtime fail?

Một file `.class` không chứa toàn bộ mã nguồn của các lớp phụ thuộc. Nó chứa các **symbolic reference** (tên class/interface, tên method/field, descriptor kiểu), cùng bytecode gọi như `invokestatic`, `invokevirtual`, `invokeinterface` hoặc `invokespecial`. Tại runtime JVM phải resolve và thực thi những tham chiếu đó theo định nghĩa class **thực sự được nạp**.

Ví dụ:

```text
Thời điểm build:
  Client.class  ──biên dịch dựa vào──> Library API v1

Thời điểm chạy:
  Client.class  ──JVM resolve tới───> Library API v2
                                       ^
                                       thay đổi method không tương thích
```

Nếu `Client.class` giữ opcode gọi static nhưng `Library` của v2 chỉ còn instance method cùng tên/descriptor, bytecode cũ không được tự sửa để chuyển thành cuộc gọi instance. Tương tự, nếu invocation kỳ vọng method đã có implementation mà runtime chỉ còn abstract method, JVM không thể tự phát minh phần thân còn thiếu.

**Source compatibility khác binary compatibility.** Chạy lại `javac` trên toàn bộ source đã cập nhật có thể phát hiện lỗi tại compile time; nhưng nếu chỉ thay JAR mà giữ class cũ, lỗi có thể lộ ra khi chạy production. Ngược lại, có thay đổi không tương thích ở source nhưng vẫn binary-compatible với caller đã biên dịch trước đó. Mức độ và loại lỗi tùy thay đổi cụ thể.

**Class loader là yếu tố quyết định:** một runtime class được xác định bởi **binary name + defining class loader**, không chỉ tên package/class. Parent delegation, thứ tự classpath, module path, fat JAR, ứng dụng plugin và các loader cô lập đều quyết định phiên bản class thực sự được chọn.

## 3. Thí nghiệm A — tái hiện `AbstractMethodError` với Java 17+

Trong v1, `Contract.message()` là `default` method, vì vậy `Provider` không cần tự triển khai. Caller cũ được biên dịch và gọi hợp lệ. Khi runtime thay `Contract` bằng v2 có method abstract, `Provider.class` từ v1 vẫn không có phần triển khai; lời gọi bị lỗi.

### Cấu trúc thí nghiệm

```text
ame/
  v1/
    src/demo/
      Contract.java
      Provider.java
      Runner.java
    classes/                 (do javac tạo)
  v2/
    src/demo/
      Contract.java
    classes/                 (do javac tạo)
```

File **`ame/v1/src/demo/Contract.java`**:

```java
package demo;

public interface Contract {
    default String message() {
        return "default-v1";
    }
}
```

File **`ame/v1/src/demo/Provider.java`**:

```java
package demo;

public class Provider implements Contract {
    // Hợp lệ ở v1 nhờ kế thừa default method.
}
```

File **`ame/v1/src/demo/Runner.java`**:

```java
package demo;

public class Runner {
    public static void main(String[] args) {
        Contract provider = new Provider();
        System.out.println(provider.message());
    }
}
```

File **`ame/v2/src/demo/Contract.java`**:

```java
package demo;

public interface Contract {
    String message(); // v2: không còn default implementation
}
```

Từ thư mục chứa `ame/`, thực hiện trên Unix/Linux/macOS (Bash):

```bash
mkdir -p ame/v1/classes ame/v2/classes

javac -d ame/v1/classes \
  ame/v1/src/demo/Contract.java \
  ame/v1/src/demo/Provider.java \
  ame/v1/src/demo/Runner.java

javac -d ame/v2/classes ame/v2/src/demo/Contract.java

# Baseline: toàn bộ lớp của v1.
java -cp ame/v1/classes demo.Runner

# Trộn contract v2 với Provider và Runner của v1.
java -cp ame/v2/classes:ame/v1/classes demo.Runner
```

Baseline in đúng:

```text
default-v1
```

Lệnh chạy hỗn hợp **kết thúc với lỗi**; phần đầu stack trace trên HotSpot JDK 17+ có dạng:

```text
Exception in thread "main" java.lang.AbstractMethodError: ...
    at demo.Runner.main(Runner.java:...)
```

Phần mô tả chi tiết của JVM, số dòng và cách in stack trace có thể thay đổi tùy bản JDK. Phần không đổi trong tình huống này là **loại lỗi** `java.lang.AbstractMethodError`. Ví dụ đã được kiểm tra trên JDK 21: receiver `demo.Provider` không định nghĩa hay thừa hưởng implementation cho `demo.Contract.message()` bản abstract.

Tại sao bytecode cũ chạy được đến dòng gọi?

1. `Runner.class` cũ có lời gọi tới `demo.Contract.message:()Ljava/lang/String;`.
2. Runtime nạp `Contract` từ `ame/v2/classes` vì đường dẫn này đứng **trước** `ame/v1/classes`.
3. `Provider.class` cũ không tự cài `message()`; lúc compile nó dựa vào `default` của interface v1.
4. Với interface v2, không còn implementation concrete phù hợp để dispatch. JVM ném `AbstractMethodError` khi thực hiện cuộc gọi.

Nếu compile lại `Provider.java` v1 theo `Contract` v2, `javac` sẽ bắt việc thiếu triển khai, trừ khi code được sửa hoặc khai báo class abstract. Điều đó giải thích vì sao lỗi có thể “chỉ xảy ra ở runtime” với binary trộn phiên bản.

**Không kết luận tất cả thay đổi method sang abstract đều trông giống nhau.** Tình huống trên có điểm quan trọng là caller/implementation được biên dịch độc lập với contract mới. Thay đổi từ concrete sang abstract trong superclass cũng có thể tạo `AbstractMethodError` theo JLS `13.4.16`.

## 4. Thí nghiệm B — tái hiện `IncompatibleClassChangeError` khi static đổi thành instance

Ở v1, bytecode của caller dùng `invokestatic`. Ở v2, method cùng tên/descriptor đã là instance method, nên instruction cũ không còn hợp lệ với method mới.

### Cấu trúc

```text
icce/
  v1/
    src/demo/
      Library.java
      Client.java
    classes/
  v2/
    src/demo/
      Library.java
    classes/
```

File **`icce/v1/src/demo/Library.java`**:

```java
package demo;

public class Library {
    public static String mode() {
        return "static-v1";
    }
}
```

File **`icce/v1/src/demo/Client.java`**:

```java
package demo;

public class Client {
    public static void main(String[] args) {
        System.out.println(Library.mode());
    }
}
```

File **`icce/v2/src/demo/Library.java`**:

```java
package demo;

public class Library {
    public String mode() {
        return "instance-v2";
    }
}
```

Biên dịch và chạy (Bash):

```bash
mkdir -p icce/v1/classes icce/v2/classes

javac -d icce/v1/classes \
  icce/v1/src/demo/Library.java \
  icce/v1/src/demo/Client.java

javac -d icce/v2/classes icce/v2/src/demo/Library.java

java -cp icce/v1/classes demo.Client
java -cp icce/v2/classes:icce/v1/classes demo.Client
```

Baseline in đúng:

```text
static-v1
```

Lệnh thứ hai lỗi (HotSpot JDK 17+, phần message có thể thay đổi):

```text
Exception in thread "main" java.lang.IncompatibleClassChangeError: Expected static method ...
    at demo.Client.main(Client.java:...)
```

Thử phân tích bytecode được lưu trong caller v1:

```bash
javap -classpath icce/v1/classes -c demo.Client
```

Trong phương thức `main` sẽ có instruction `invokestatic` gọi `demo/Library.mode:()Ljava/lang/String;`. Đây là bằng chứng caller cũ **đã mã hóa kiểu gọi static**; chỉ sửa mã nguồn của `Library` rồi thay JAR ở runtime sẽ không biến bytecode caller thành lời gọi instance.

Với Windows PowerShell/cmd, khi classpath có hai thư mục, đổi dấu `:` thành dấu `;` và đặt cả classpath trong dấu nháy nếu cần, ví dụ:

```powershell
java -cp "icce/v2/classes;icce/v1/classes" demo.Client
```

Chỉ dùng đường dẫn tách biệt để minh họa; trong dự án thật, cùng tên class có thể nằm ở hai JAR, các thư mục generated class hoặc các class loader khác nhau.

## 5. Điều gì diễn ra bên trong JVM và class loader?

Các bước khái quát:

1. **Loading:** class loader cung cấp bytes định nghĩa lớp theo tên và cơ chế delegation. Với nhiều URL/JAR, thứ tự tìm kiếm ở một loader có thể ảnh hưởng phiên bản được nạp.
2. **Linking:** verification và resolution bảo đảm tham chiếu symbolic của bytecode có thể kết nối với định nghĩa runtime. Resolution có thể được thực hiện muộn, nên lỗi đôi khi chỉ xuất hiện khi chạy tới nhánh code nhất định.
3. **Method invocation/dispatch:** `invokestatic`, `invokevirtual`, `invokeinterface`, `invokespecial` có yêu cầu khác nhau. Nếu loại method hoặc kết quả dispatch không hợp lệ, JVM báo lỗi liên kết thích hợp.
4. **Không tái nạp âm thầm class cùng tên:** một loader thông thường không “thay bytes của class đã define” chỉ vì JAR trên disk đã đổi. Plugin frameworks/restart loaders có thể tạo **loader mới**, mang theo phiên bản class mới, trong khi loader cũ vẫn giữ định nghĩa cũ.

Trong app server hoặc plugin container, riêng câu hỏi “trong disk có đúng JAR chưa?” là chưa đủ; cần biết **class nào do loader nào định nghĩa**, parent loader có chiếm quyền nạp trước hay không và quá trình xây dựng artifact đã gói những dependency nào.

Một ví dụ khó nhận ra:

```text
Bootstrap / platform loader
   └── Application loader → API v1
           └── Plugin loader → implementation v2
```

Nếu plugin loader delegate API lên parent, plugin thực tế có thể dùng **API v1** dù trong thư mục plugin có bản v2. Trong thiết kế ngược lại, caller của parent có thể resolve implementation của lớp với contract khác mong đợi. Lỗi thực tế có thể là `AbstractMethodError`, `IncompatibleClassChangeError`, `ClassCastException`, `NoClassDefFoundError` hoặc một `LinkageError` khác, tùy ràng buộc type và opcode; **không quy mọi lỗi class loader về hai exception trong bài**.

Đặc biệt: hai loader độc lập cùng định nghĩa `demo.Contract` tạo ra **hai runtime type khác nhau**. Việc cast instance qua ranh giới loader có thể ném `ClassCastException`, không nhất thiết là `IncompatibleClassChangeError`. Những trường hợp loader constraint violation có thể được báo `LinkageError`. Cần đọc **đúng exception class và stack trace**.

## 6. Các kiểu thay đổi binary thường gây lỗi liên quan

| Thay đổi giữa build và runtime | Triệu chứng có thể gặp | Gợi ý |
|---|---|---|
| Concrete/default implementation không còn, method trở thành abstract | `AbstractMethodError` khi gọi implementation cũ | So interface/superclass và bản implementation ở runtime |
| Method `static` ↔ instance | `IncompatibleClassChangeError` | So opcode invoke và modifier method thực được nạp |
| Field `static` ↔ instance | `IncompatibleClassChangeError` khi truy cập field bằng opcode cũ | So `getstatic/putstatic` với `getfield/putfield` |
| Method bị xóa hoặc thay tên/descriptor/return type khiến symbolic reference cũ không resolve được | `NoSuchMethodError` (một subclass của `IncompatibleClassChangeError`) | So chữ ký JVM descriptor, không chỉ tên method |
| Field bị xóa/đổi descriptor | `NoSuchFieldError` | Kiểm tra signature field thực ở class runtime |
| Truy cập member bị thay đổi sang không còn hợp lệ | `IllegalAccessError` | Kiểm tra Java access, module/package và runtime class |
| Đổi interface thành class hoặc ngược lại | Có thể `IncompatibleClassChangeError` | Kiểm tra runtime loại reference và bytecode liên quan |
| Hai loader cùng nạp class cùng tên nhưng khác identity | Có thể `ClassCastException` hoặc lỗi ràng buộc loader khác | In defining loader, trace class load, kiểm tra parent delegation |

Lưu ý: bảng mô tả **những triệu chứng điển hình**, không cam kết mọi phiên bản JVM hay mọi tập hợp binary sẽ phát sinh đúng một lỗi duy nhất. JVM có thể phát hiện một vi phạm khác sớm hơn, phụ thuộc instruction đầu tiên thực thi và cách resolve tham chiếu.

## 7. Quy trình điều tra lỗi trong ứng dụng thực tế

### Bước 1 — ghi nguyên nhân quan sát được, không đoán từ một dòng stack trace

Lưu đầy đủ:
- **Exception class đầy đủ**, message, dòng `Caused by` (nếu có) và stack trace của thread liên quan.
- Tên method, class/interface, descriptor nếu có, bản JDK và vị trí caller.
- Phiên bản API mà **caller được build cùng** và phiên bản API mà JVM thực tế đang nạp.
- Thời điểm triển khai: lỗi chỉ xuất hiện sau cập nhật JAR, hot reload, restart, redeploy hoặc thay classpath?

Stack trace thường chỉ tới **lệnh gọi gây lỗi**, không nhất thiết chỉ tới file JAR **chứa định nghĩa runtime không tương thích**.

### Bước 2 — xác nhận đường nạp class bằng runtime evidence

Nếu chủ động chỉnh được chương trình, đặt chỗ kiểm tra tại phần code chắc chắn còn chạy được:

```java
import java.net.URL;

public final class ClassOrigin {
    public static void describe(Class<?> type) {
        System.out.println("Class: " + type.getName());
        System.out.println("Loader: " + type.getClassLoader());
        var domain = type.getProtectionDomain();
        var source = domain == null ? null : domain.getCodeSource();
        URL location = source == null ? null : source.getLocation();
        System.out.println("Code source: " + location);
    }
}
```

Đây là **utility minh họa** để gọi `ClassOrigin.describe(SomeClass.class)` từ một chương trình khác, không có `main` độc lập. `getClassLoader()` có thể trả `null` đối với lớp do bootstrap loader nạp. `CodeSource` cũng có thể `null`; framework/shading/nested JAR có thể khiến location không đơn giản là một file trên disk. Vì vậy, dùng log JVM và build metadata để đối chiếu khi output không đủ rõ.

Có thể bật log khi khởi động ứng dụng HotSpot JDK 17:

```bash
java -Xlog:class+load=info -cp app-classes:lib/* com.example.Main
```

Đường dẫn/classpath là **mẫu minh họa**; thay bằng đường chạy thực tế. Đọc các dòng liên quan đến class gặp lỗi để biết JVM đã nạp định nghĩa từ đâu. Với JDK 17, lệnh `java -verbose:class ...` cũng hữu ích. Nếu dùng module path hoặc class loader tùy biến, ghi thêm loader identity khi có thể; nhiều JAR cùng version string không bảo đảm là cùng class bytes.

### Bước 3 — kiểm tra binary signature và instruction

Chạy `javap` trên **artifact đang triển khai** và trên **artifact mà caller được biên dịch với**:

```bash
javap -classpath path/to/api-v1.jar -p -s com.example.Library
javap -classpath path/to/api-v2.jar -p -s com.example.Library
javap -classpath path/to/app-classes -c -p com.example.Client
```

Những gì cần đối chiếu:

- Class hay interface; superclass và các interface được implements.
- `abstract` / `default` / `static` / `final` và access modifiers.
- Tên method và **descriptor**, ví dụ `()Ljava/lang/String;` khác `()V`.
- Opcode của caller: `invokestatic`, `invokevirtual`, `invokeinterface`; opcode access field tương ứng.
- Method concrete mà implementation kế thừa thực sự có tồn tại trong contract runtime hay không.

Trong plugin/app server, việc dùng `javap` trên local JAR là **chẩn đoán bản dựng**, chưa thay cho việc chứng minh JVM đã chọn JAR đó. Dùng class-loading log và loader identity để nối bằng chứng.

### Bước 4 — xác minh cây phụ thuộc và nội dung gói triển khai

Đối chiếu Maven/Gradle dependency resolution với **runtime artifact**, vì dependency declaration chưa chắc là class bytes được đóng gói:

- Gradle: `./gradlew dependencies --configuration runtimeClasspath`, `./gradlew dependencyInsight --dependency ten-thu-vien --configuration runtimeClasspath` (cần chọn đúng module/configuration).
- Maven: `mvn dependency:tree` và cấu hình scope/shading/assembly tương ứng.
- Với Spring Boot executable JAR, kiểm tra thư viện trong `BOOT-INF/lib`; với app server, kiểm tra các thư mục thư viện chung và module deployment; với plugin, kiểm tra các JAR mà parent/child loader nhìn thấy.
- So version của API, SPI, provider, adapter, generated proxies và caller. Cảnh giác dependency transitively kéo bản cũ, cùng một API bị gói nhiều lần, hoặc deploy thay từng JAR trong khi JVM vẫn chạy bản cũ.

### Bước 5 — sửa nhất quán và kiểm tra hồi quy

Cách xử lý đúng là **khôi phục binary contract nhất quán** giữa các module: chọn phiên bản API và implementation tương thích; build lại caller/provider bị ảnh hưởng; bảo đảm runtime nhận đúng bộ class bytes; rồi tạo lại class loader hoặc restart process khi cần để loại bỏ class cũ đã load.

Không sửa bằng cách catch `AbstractMethodError` rồi gọi đại một method khác, đổi classpath “cho tới khi chạy”, hoặc thêm `System.gc()`. Garbage collection không sửa được bytecode tham chiếu sai, cũng không biến method abstract thành concrete. `Class.forName` chỉ nạp/khởi tạo class theo cách gọi và có thể giúp phát hiện một số vấn đề sớm; nó **không** tự giải quyết binary incompatibility.

Nếu phải duy trì nhiều phiên bản plugin, thiết kế API boundary ổn định, adapter rõ ràng, quy tắc version và parent delegation nhất quán. Xác minh cả test tích hợp **sử dụng đúng bộ JAR ở runtime**, không chỉ compile test với một tập dependency khác.

## 8. Ví dụ phân biệt lỗi và những kết luận sai

**Sai:** “`AbstractMethodError` là do quên override method abstract, chỉ cần sửa code để compiler bắt.”  
**Đúng:** Với source biên dịch sạch, compiler thường phát hiện thiếu implementation. Khi runtime kết hợp các `.class` biên dịch từ **những phiên bản contract khác nhau**, thiếu implementation có thể chỉ lộ ra ở dispatch.

**Sai:** “`IncompatibleClassChangeError` chắc chắn có hai JAR trùng tên trên classpath.”  
**Đúng:** Trùng JAR chỉ là một nguyên nhân khả dĩ. Một JAR duy nhất nhưng sai phiên bản so với caller đã biên dịch cũng gây lỗi; class loader còn có thể nạp từ directory, module hoặc nguồn bytecode khác.

**Sai:** “Cứ xóa cache Gradle/Maven là hết.”  
**Đúng:** Cache bị hỏng là giả thuyết chỉ nên kiểm tra khi có bằng chứng. Gốc rễ thường là binary version mismatch; kiểm chứng dependency graph, artifact nội dung và nguồn class JVM nạp.

**Sai:** “Lỗi xảy ra khi Spring tạo bean thì chắc là lỗi dependency injection.”  
**Đúng:** Framework có thể là nơi kích hoạt lần gọi method đầu tiên. Hãy đọc nguyên nhân sâu trong stack trace, phân biệt lỗi linkage với lỗi cấu hình bean.

**Sai:** “Hai `Class<?>` có cùng `getName()` thì là cùng kiểu.”  
**Đúng:** Kiểm tra `==` và defining loader; cùng tên nhưng khác loader có thể là hai runtime class khác nhau.

**Sai:** “Chỉ cần restart mà không cần biết nguyên nhân.”  
**Đúng:** Restart có thể khiến loader mới đọc đúng artifact và tạm giải quyết tình trạng redeploy dở dang, nhưng nếu deployment vẫn chứa tập bytecode không tương thích thì lỗi sẽ tái diễn.

## 9. Checklist chẩn đoán nhanh

1. Exception thực tế là `AbstractMethodError`, `IncompatibleClassChangeError` trực tiếp hay subclass khác (`NoSuchMethodError`, `IllegalAccessError`...)?
2. Caller được build với phiên bản API nào? Runtime thực tế nạp `Class<?>` nào, từ loader và code source nào?
3. Có thay đổi `default/concrete → abstract`, `static ↔ instance`, method descriptor, class/interface, superclass hoặc implementation không?
4. `javap -c` của caller chứa opcode gì? `javap -p -s` của class runtime cho biết method/field nào?
5. Có nhiều bản API/plugin, shared library, parent delegation, shaded JAR, app-server library hoặc hot-reload loader cũ không?
6. Đã build/deploy lại **toàn bộ các binary liên quan** và chạy integration test với cùng dependency set như production chưa?

## 10. Câu hỏi tự kiểm tra

**Câu 1:** Vì sao `Provider` trong thí nghiệm A biên dịch hợp lệ với v1 nhưng ném `AbstractMethodError` khi chạy với interface v2?  
**Đáp án:** v1 có `default` implementation; v2 chỉ có abstract declaration. `Provider.class` cũ không tự triển khai `message()` và không được biên dịch lại.

**Câu 2:** Trong thí nghiệm B, vì sao runtime không tự gọi `new Library().mode()` thay cho `Library.mode()`?  
**Đáp án:** `Client.class` chứa `invokestatic` theo contract v1; JVM không tự viết lại chương trình, khởi tạo đối tượng, rồi thay instruction cho phù hợp v2.

**Câu 3:** `AbstractMethodError` có thuộc họ `IncompatibleClassChangeError` không?  
**Đáp án:** Có. Nó là subclass trực tiếp của `IncompatibleClassChangeError`, và cả hai thuộc `LinkageError`.

**Câu 4:** Thấy hai class cùng tên `com.example.Api` từ hai loader, có thể khẳng định sẽ ném `AbstractMethodError` không?  
**Đáp án:** Không. Cần biết tham chiếu/loader constraints và instruction cụ thể; có thể là hai runtime type hợp lệ tách biệt, hoặc phát sinh `ClassCastException`/lỗi linkage khác.

**Câu 5:** Ba bằng chứng quan trọng nhất khi debug là gì?  
**Đáp án:** (i) full exception class/stack trace tại vị trí gọi; (ii) phiên bản và loader/code source của class runtime; (iii) signature/modifier + bytecode opcode và bộ artifact mà caller thực sự được build với.

## Tài liệu chuẩn

- [Java SE 17 — `AbstractMethodError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AbstractMethodError.html): định nghĩa lỗi, thuộc họ `IncompatibleClassChangeError`.
- [Java SE 17 — `IncompatibleClassChangeError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IncompatibleClassChangeError.html): lỗi do thay đổi class definition không tương thích.
- [JLS 17, Chương 13 — Binary Compatibility](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html): đặc biệt `13.4.16` về concrete-to-abstract và `13.4.18` về static/instance method.
- [JVMS 17, Chương 6 — JVM Instruction Set](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html): `invokestatic`, `invokeinterface` và các instruction khác để giải thích bytecode caller.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-class-loading-errors-009">IllegalAccessError, IllegalAccessException và InaccessibleObjectException</a>

<details>
<summary>Xem chi tiết</summary>

# IllegalAccessError, IllegalAccessException và InaccessibleObjectException trong Java ClassLoader

## 1. Ba lỗi khác nhau dù tên rất giống nhau

Khi Java làm việc với `ClassLoader`, reflection và JPMS, một chương trình có thể gặp các lỗi cùng chứa cụm “Illegal Access” nhưng phát sinh từ những cơ chế khác nhau:

- **`java.lang.IllegalAccessError`**: lỗi khi **JVM liên kết/giải quyết hoặc sử dụng một symbolic reference** tới class, field hay method mà caller không được phép truy cập. Đây là một `Error` thuộc nhánh `LinkageError`; thường xuất hiện nếu class được biên dịch với một version của API nhưng chạy với version có visibility thay đổi không tương thích.
- **`java.lang.IllegalAccessException`**: một **checked exception**, điển hình khi code sử dụng `Method.invoke`, `Field.get`, `Field.set` hay `Constructor.newInstance` nhưng Java reflection access checks vẫn có hiệu lực và caller không được quyền truy cập member.
- **`java.lang.reflect.InaccessibleObjectException`**: một **unchecked exception** xuất hiện khi code **đòi suppress các access checks** qua `setAccessible(true)` nhưng không được phép, nhất là khi một named module không mở package cho caller trong Java 9+.

Không nên kết luận “chỉ cần gọi `setAccessible(true)`” mỗi khi nhìn thấy một trong ba lỗi. Với `IllegalAccessError`, đó thường là vấn đề **binary compatibility hoặc JVM access linkage**. Với `IllegalAccessException`, cần xem caller/member và các thao tác reflection. Với `InaccessibleObjectException`, cần xem **quyền mở package của module** và có thật sự cần deep reflection không.

~~~text
                 THAO TÁC TRUY CẬP
                        |
      +-----------------+------------------+
      |                 |                  |
      v                 v                  v
 bytecode call     Method.invoke      setAccessible(true)
 getfield / etc.   Field.get ...           |
      |                 |                  |
  access check     access checks      được suppress?
      |                 |                  |
 illegal          illegal              không
      |                 |                  |
      v                 v                  v
 IllegalAccessError  IllegalAccessException  InaccessibleObjectException
~~~

Sơ đồ chỉ mô tả nguyên nhân điển hình, không phải mọi lỗi có thể xảy ra. Ví dụ, một method được gọi thành công qua reflection nhưng body ném exception thì reflection có thể bọc cause trong `InvocationTargetException`, không phải mặc nhiên ném `IllegalAccessException`.

## 2. So sánh theo lớp lỗi, thời điểm và cách sửa

| Đặc điểm | IllegalAccessError | IllegalAccessException | InaccessibleObjectException |
| --- | --- | --- | --- |
| Package | `java.lang` | `java.lang` | `java.lang.reflect` |
| Thừa kế | `Error → LinkageError → IncompatibleClassChangeError` | `Exception → ReflectiveOperationException` | `RuntimeException` |
| Checked exception? | Không | Có | Không |
| API/ngữ cảnh thường thấy | `invokevirtual`, `invokestatic`, truy cập symbolic class/field/method | `Method.invoke`, `Field.get`, `Constructor.newInstance` | `AccessibleObject.setAccessible(true)` |
| Nguyên nhân thường gặp | Runtime binary không còn cho phép direct access đã compile | Reflection caller không có quyền với private/package-private member | JPMS package không `opens` tới caller |
| Có bắt bởi `catch (Exception)`? | Không | Có | Có |
| Biện pháp gốc | Đồng bộ JAR versions, visibility, exports và class loader | Public API hoặc reflection có quyền hợp lệ | Public API, `opens` hợp lý, library/JDK compatibility |
| `--add-opens` luôn sửa được? | Không | Không phải cho mọi trường hợp | Có thể giúp riêng nguyên nhân JPMS chưa mở package |

**Cẩn thận thứ tự cây kế thừa:** `IllegalAccessError` trực tiếp kế thừa `IncompatibleClassChangeError`, rồi tới `LinkageError`, `Error`. `IllegalAccessException` không nằm cùng nhánh dù tên rất giống.

## 3. Access modifiers và quyền truy cập trong JVM

Java có bốn mức visibility thường gặp:

- `public`: class/member có thể được truy cập khi declaring type, module và caller thỏa quy tắc liên quan.
- `protected`: ngoài cùng package, còn có quyền theo quan hệ subclass với các ràng buộc JVM riêng.
- **Package-private** (không ghi modifier): trong cùng **runtime package**.
- `private`: quyền truy cập hẹp theo class/nestmate rules, không phải cứ trùng package là được.

Ví dụ:

~~~java
package demo;

class HiddenClass {
    public static void run() {
        System.out.println("running");
    }
}
~~~

Đây là **mã minh họa**, không có main. Method `run` public nhưng class `HiddenClass` lại package-private. Caller package khác không mặc nhiên truy cập được method đó.

Với named module, còn phải xét:

1. Module caller có **đọc (reads)** module chứa target không?
2. Package target có **exports** tới caller để dùng public API không?
3. Nếu muốn deep reflection, package target có **opens** tới caller không?
4. Declaring class/member có thực sự thỏa Java language access rules không?
5. Khi có custom loaders, caller và target có thuộc đúng **runtime package** không?

Hai class cùng viết `package demo;` trong Java source **chưa chắc** cùng runtime package nếu hai defining class loaders khác nhau. `getPackageName()` chỉ cho tên chuỗi; không chứng minh có quyền package-private xuyên loader.

## 4. IllegalAccessError: direct JVM access thất bại

Theo Java SE 17 API, `IllegalAccessError` được ném khi ứng dụng cố truy cập/sửa field hoặc gọi method mà nó không có quyền truy cập. Java compiler thường phát hiện lỗi này nếu biên dịch toàn bộ source với cùng định nghĩa API. Vì vậy tại runtime, dấu hiệu này thường gợi ý **runtime class definition khác định nghĩa lúc compile**.

Ví dụ thay đổi không tương thích:

~~~text
Ở compile time:
  api.Library.greet() là public static

Ở runtime:
  api.Library.greet() là static, package-private

client.Main vẫn chứa bytecode gọi api.Library.greet()
=> JVM từ chối quyền gọi => IllegalAccessError
~~~

Các tình huống có thể gây ra:

- API public method bị hạ visibility trong version library mới.
- Public class bị chuyển thành package-private ở runtime.
- Module không export package tới caller dù bytecode có tham chiếu trực tiếp.
- Một dependency JAR không đúng version nằm trước version mong muốn trong classpath.
- Plugin loader hoặc hot reload lấy một class khác với class compiler từng dùng.
- Hai classes trùng package source được **định nghĩa bởi các loaders khác nhau**, làm mất package-private access.
- Bytecode weaving/shading/instrumentation tạo reference bị JVM từ chối.

Vì đây là `Error` trong nhánh **binary linkage**, catch rồi tiếp tục chạy như không có gì thường là cách xử lý sai. Cần sửa dependency graph, module ownership, source/API compatibility hay class loader routing.

## 5. Ví dụ Java 17+ hoàn chỉnh: tái hiện IllegalAccessError

Ví dụ này xây một client chạy bình thường với library v1, sau đó **chỉ thay class thư viện** bằng v2 mà không biên dịch lại client.

### 5.1. Cấu trúc thư mục

~~~text
linkage-demo/
├── v1-src/
│   └── api/
│       └── Library.java
├── v2-src/
│   └── api/
│       └── Library.java
└── client-src/
    └── client/
        └── Main.java
~~~

### 5.2. File v1-src/api/Library.java

~~~java
package api;

public class Library {
    public static String greet() {
        return "hello";
    }
}
~~~

### 5.3. File v2-src/api/Library.java

~~~java
package api;

public class Library {
    static String greet() { // Không còn public
        return "hello";
    }
}
~~~

### 5.4. File client-src/client/Main.java

~~~java
package client;

import api.Library;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("call: " + Library.greet());
        } catch (IllegalAccessError expected) {
            System.out.println("call: IllegalAccessError");
        }
    }
}
~~~

### 5.5. Chạy v1

Đứng trong thư mục `linkage-demo`, dùng JDK 17 trở lên:

~~~bash
javac --release 17 -d out v1-src/api/Library.java client-src/client/Main.java
java -cp out client.Main
~~~

Stdout:

~~~text
call: hello
~~~

### 5.6. Thay chỉ thư viện bằng v2, giữ nguyên Main.class

~~~bash
javac --release 17 -d out v2-src/api/Library.java
java -cp out client.Main
~~~

Stdout:

~~~text
call: IllegalAccessError
~~~

**Giải thích:** `client.Main.class` vẫn tham chiếu `api.Library.greet()Ljava/lang/String;` qua instruction `invokestatic`. Khi JVM chạy trong **process mới** với `Library.class` của v2, method vẫn có tên/descriptor ấy nhưng đã thành package-private; `client.Main` ở package khác nên không có quyền gọi. Nếu biên dịch lại `Main.java` trực tiếp với v2, compiler sẽ báo access problem từ trước. Ta cố tình giữ client bytecode cũ để chứng minh **runtime binary incompatibility**.

Đây không phải `IllegalAccessException` vì không có `Method.invoke`, và không phải `InaccessibleObjectException` vì không có `setAccessible(true)`.

## 6. IllegalAccessException: access check của reflection

Khi dùng `java.lang.reflect.Method`, `Field` hoặc `Constructor`, Java reflection phải xác minh caller có được truy cập member không. `IllegalAccessException` là **checked exception** mô tả trường hợp thao tác reflection không có quyền.

Các API thường gặp:

~~~java
Method method = SomeClass.class.getDeclaredMethod("secret");
method.invoke(null);
~~~

Đây là **mã minh họa** giả sử `SomeClass` có static method `secret`. `getDeclaredMethod` có thể tìm **private method metadata**, nhưng `invoke` khi access checks còn bật vẫn có thể ném `IllegalAccessException`.

Phân biệt:

- `Class.getDeclaredMethod`: tìm method được khai báo trong class, kể cả private, không tự cấp quyền gọi.
- `Method.invoke`: thực thi Java access checks trừ khi đã được cho phép suppression, có thể ném `IllegalAccessException`.
- `Field.get/set`: checks với field cũng có thể tạo `IllegalAccessException`.
- `Constructor.newInstance`: constructor không accessible từ caller có thể gây `IllegalAccessException`.
- `MethodHandles.Lookup.findVirtual`, `findStatic`, `privateLookupIn` có access rules của MethodHandles; chúng cũng có thể ném `IllegalAccessException` nhưng **không phải cùng call path** với `Method.invoke`.

Nếu method thực sự được gọi rồi **body của nó ném exception**, `Method.invoke` thông thường bọc nó trong **`InvocationTargetException`**. Nên xem `getCause()` trước khi nói lỗi do reflection access.

## 7. Ví dụ Java 17+ hoàn chỉnh: IllegalAccessException rồi bật access

Tạo file **ReflectionDemo.java**:

~~~java
import java.lang.reflect.Method;

class Vault {
    private static String secret() {
        return "private-data";
    }
}

public class ReflectionDemo {
    public static void main(String[] args) throws Exception {
        Method method = Vault.class.getDeclaredMethod("secret");

        try {
            method.invoke(null);
            System.out.println("invoke without override: allowed");
        } catch (IllegalAccessException expected) {
            System.out.println(
                    "invoke without override: IllegalAccessException");
        }

        System.out.println("trySetAccessible: "
                + method.trySetAccessible());
        System.out.println("private result: "
                + method.invoke(null));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 ReflectionDemo.java
java ReflectionDemo
~~~

Stdout chính xác:

~~~text
invoke without override: IllegalAccessException
trySetAccessible: true
private result: private-data
~~~

**Giải thích:**

1. `Vault.secret()` là private. `ReflectionDemo` là một top-level class khác, không phải nestmate được phép truy cập private member ấy.
2. `getDeclaredMethod` vẫn tìm được method metadata.
3. Lần đầu `invoke(null)` gặp access check và ném `IllegalAccessException`.
4. Vì `Vault` và `ReflectionDemo` cùng unnamed module, `trySetAccessible()` được phép trả true theo quy tắc `AccessibleObject`.
5. Lần `invoke` sau sử dụng accessible flag đã bật và thành công.

**Không suy rằng trySetAccessible luôn true.** Khi target nằm trong named module khác và package chưa open, suppression có thể bị từ chối, như ví dụ tiếp theo.

## 8. InaccessibleObjectException: setAccessible(true) bị từ chối

Trong Java 9+, **`InaccessibleObjectException` kế thừa `RuntimeException`**. Lỗi điển hình khi:

~~~java
Method method = targetClass.getDeclaredMethod("hidden");
method.setAccessible(true);
~~~

Đây là **đoạn minh họa**. Caller muốn tắt Java language access checks, nhưng module chứa declaring class chưa open package cho module của caller. `setAccessible(true)` ném `InaccessibleObjectException` ngay tại yêu cầu bật quyền.

Đặt cạnh nhau:

| Gọi API | Loại lỗi liên quan |
| --- | --- |
| `method.invoke(obj)` khi access check đang bật | `IllegalAccessException` |
| `method.setAccessible(true)` khi không được suppress | `InaccessibleObjectException` |
| `method.trySetAccessible()` khi không được suppress | Trả `false` theo hợp đồng thông thường |
| `method.invoke(obj)` sau khi bật suppression hợp lệ | Có thể thực thi method; body có thể ném exception riêng |

`trySetAccessible` là cách hỏi một cách mềm dẻo, **không phải cửa sau** vượt qua JPMS. Nếu trả false, ứng dụng vẫn cần lựa chọn public API khác hoặc điều chỉnh module ownership hợp lý.

Một lưu ý: với một số public members trong public classes được export, `setAccessible(true)` cũng có thể được cho phép theo các điều kiện JDK — **không phải mọi lời gọi setAccessible trên member của named module đều ném**. Vấn đề hay gặp là **private/protected/package-private members ở module khác mà package không open**.

## 9. Exports, opens, requires: ba thứ đừng đánh đồng

JPMS (Java 9+) có các modules có tên và descriptor `module-info.java`.

~~~java
module library.mod {
    exports sample.lib;
}
~~~

Descriptor này cho code ở module đọc `library.mod` dùng những **public types của package `sample.lib`** theo Java access rules. Tuy nhiên `exports` **không mặc nhiên cấp deep reflection** tới private fields/methods.

So sánh:

| Khai báo | Tác dụng chính |
| --- | --- |
| `requires library.mod;` | Consumer module được đọc module library |
| `exports sample.lib;` | Package cung cấp public API cho mọi module đọc |
| `exports sample.lib to app.mod;` | Public API chỉ export tới app.mod và những targets nêu rõ |
| `opens sample.lib;` | Mở package cho deep reflection từ modules khác |
| `opens sample.lib to app.mod;` | Chỉ mở deep reflection tới app.mod |
| `open module library.mod { ... }` | Mở package của module theo quy tắc open module |

**Một package đã exports chưa chắc opens.** Vì vậy code có thể compile và gọi `SecretBox.publicMessage()` bình thường, nhưng gọi `setAccessible(true)` trên `SecretBox.secretMessage()` vẫn bị từ chối.

Launcher có hai options tương ứng:

~~~text
--add-exports library.mod/sample.lib=app.mod
--add-opens library.mod/sample.lib=app.mod
~~~

`--add-exports` dùng cho access tới **public API** theo module rules; `--add-opens` cấp quyền **deep reflection** cho caller module được chỉ định. Không nên dùng `--add-opens` như “cách sửa chung mọi IllegalAccessError” vì nó không làm private member thành public trong bytecode và không sửa version mismatch.

## 10. Ví dụ Java 17+ hoàn chỉnh: exported package vẫn chặn deep reflection

Thí nghiệm named modules cho thấy **cùng một method private** gây `IllegalAccessException` khi gọi qua `invoke`, và `InaccessibleObjectException` khi cố bật accessible.

### 10.1. Cấu trúc thư mục

~~~text
jpms-access-demo/
└── src/
    ├── library.mod/
    │   ├── module-info.java
    │   └── sample/
    │       └── lib/
    │           └── SecretBox.java
    └── app.mod/
        ├── module-info.java
        └── sample/
            └── app/
                └── Main.java
~~~

### 10.2. src/library.mod/module-info.java

~~~java
module library.mod {
    exports sample.lib;
}
~~~

### 10.3. src/library.mod/sample/lib/SecretBox.java

~~~java
package sample.lib;

public class SecretBox {
    public static String publicMessage() {
        return "public";
    }

    private static String secretMessage() {
        return "private";
    }
}
~~~

### 10.4. src/app.mod/module-info.java

~~~java
module app.mod {
    requires library.mod;
}
~~~

### 10.5. src/app.mod/sample/app/Main.java

~~~java
package sample.app;

import java.lang.reflect.Method;
import java.lang.reflect.InaccessibleObjectException;
import sample.lib.SecretBox;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("public call: "
                + SecretBox.publicMessage());

        Method method = SecretBox.class
                .getDeclaredMethod("secretMessage");

        try {
            method.invoke(null);
            System.out.println("invoke private: allowed");
        } catch (IllegalAccessException expected) {
            System.out.println(
                    "invoke private: IllegalAccessException");
        }

        try {
            method.setAccessible(true);
            System.out.println("setAccessible: enabled");
            System.out.println("private result: "
                    + method.invoke(null));
        } catch (InaccessibleObjectException expected) {
            System.out.println(
                    "setAccessible: InaccessibleObjectException");
        }
    }
}
~~~

### 10.6. Biên dịch và chạy bình thường

Đứng tại `jpms-access-demo` và dùng JDK 17+:

~~~bash
javac --release 17 --module-source-path src -d mods -m library.mod,app.mod
java --module-path mods --module app.mod/sample.app.Main
~~~

Stdout chính xác:

~~~text
public call: public
invoke private: IllegalAccessException
setAccessible: InaccessibleObjectException
~~~

**Vì sao?** `app.mod` đã requires `library.mod` và package `sample.lib` được exports, do đó direct public call được phép. `Method.invoke` tới private method bị access checks chặn. `setAccessible(true)` cố suppress checks nhưng package chưa `opens` cho app.mod, nên báo `InaccessibleObjectException`.

### 10.7. Chạy lại với mở package hẹp cho app.mod

~~~bash
java --add-opens library.mod/sample.lib=app.mod --module-path mods --module app.mod/sample.app.Main
~~~

Stdout chính xác:

~~~text
public call: public
invoke private: IllegalAccessException
setAccessible: enabled
private result: private
~~~

**Điểm cần hiểu:** `--add-opens` cho phép yêu cầu `setAccessible(true)` thành công. Nó **không tự động tắt access checks** của lời `invoke` đầu tiên, vì đoạn code gọi `invoke` **trước khi setAccessible**.

Nếu sở hữu module library, một biến thể khai báo hẹp trong descriptor thay vì launcher flag là:

~~~java
module library.mod {
    exports sample.lib;
    opens sample.lib to app.mod;
}
~~~

Đây là **descriptor thay thế để hiểu ý**, không được dùng đồng thời với descriptor cũ. Chỉ mở package khi ứng dụng có lý do thực sự cần deep reflection; package private là một phần internal design contract.

## 11. ClassLoader và lỗi package-private access

Với custom class loaders, “cùng package trong source” có thể đánh lừa:

~~~text
Source of Host.class:    package tools;
Source of Helper.class:  package tools;

Host.class  -> defined by HostLoader
Helper.class -> defined by PluginLoader

Cùng package name?        Có
Cùng runtime package?     Không mặc nhiên
Có quyền package-private? Không chỉ nhờ tên giống
~~~

Theo JVM, runtime class identity phụ thuộc **binary name và defining loader**, còn runtime package gắn với package name và defining loader. Vì vậy package-private access trong bytecode có thể thất bại khi một application server hoặc plugin framework tách hai classes thuộc một source package sang loaders khác.

Trong Java 9+, module ownership và access rules cũng phải được xét; loader là một phần của câu chuyện, không phải toàn bộ.

Để giảm lỗi:

- Đặt **shared API/DTO/annotation/exception types** ở common parent loader/module.
- Không child-first một cách vô điều kiện cho package chứa các class vốn cần cùng runtime package access.
- Tránh copy JAR chứa API shared vào plugin fat JAR nếu loader có thể define lại types ấy.
- In `clazz.getClassLoader()`, `clazz.getModule()` và `clazz.getPackageName()` để quan sát thực tế.
- Đừng cho rằng sửa `setAccessible(true)` của một Method giúp lời gọi **bytecode trực tiếp** có quyền package-private; đó là hai đường truy cập khác nhau.

Một helper chẩn đoán:

~~~java
static void describe(Class<?> c) {
    System.out.println("name=" + c.getName());
    System.out.println("loader=" + c.getClassLoader());
    System.out.println("module=" + c.getModule());
}
~~~

Đây là **đoạn minh họa trong một class Java**. Nó không tự kiểm tra module graph đầy đủ, nhưng giúp nhận ra các Class cùng tên/khác defining loader.

## 12. Kiểm tra runtime module access thay vì đoán

Module API Java 17 cho phép quan sát:

~~~java
Module caller = Caller.class.getModule();
Module target = Target.class.getModule();

System.out.println("canRead: "
        + caller.canRead(target));
System.out.println("exported: "
        + target.isExported(Target.class.getPackageName(), caller));
System.out.println("opened: "
        + target.isOpen(Target.class.getPackageName(), caller));
~~~

Đây là **mã minh họa** với hai classes được định nghĩa. Nó giúp phân biệt:

- `canRead`: caller module có đọc target module không?
- `isExported`: package có được export tới caller không?
- `isOpen`: package có được open tới caller không?

Ngay cả khi `isOpen` true, Java source bình thường cũng không được tự do gọi private method. **Open giúp reflective suppression theo quy tắc**, không thay Java visibility của member ở code source.

Một số reflection APIs có quy tắc caller module readability đặc biệt (ví dụ `AccessibleObject` giả định readability trong một số access checks); đừng kết luận `canRead(false)` luôn làm `Method.invoke` báo đúng một loại exception mà không xét API cụ thể.

## 13. Những lớp lỗi liên quan nhưng khác nghĩa

| Lỗi | Ý nghĩa phổ biến |
| --- | --- |
| `ClassNotFoundException` | Loader được hỏi không tìm thấy class theo binary name |
| `NoClassDefFoundError` | JVM không có được một định nghĩa cần thiết hoặc class đã thất bại khi initialize |
| `NoSuchMethodError` | Bytecode mong một method name/descriptor nhưng runtime definition không có |
| `NoSuchFieldError` | Tương tự với field |
| `IllegalAccessError` | Reference tồn tại nhưng caller không có quyền truy cập theo JVM |
| `IllegalAccessException` | Reflection/method-handle lookup bị access check từ chối |
| `InaccessibleObjectException` | Yêu cầu bật reflective access suppression bị từ chối |
| `ClassCastException` | Một object không tương thích runtime type; có thể liên quan duplicate loaders |
| `InvocationTargetException` | Wrapper khi method được gọi qua reflection tự ném exception |
| `ExceptionInInitializerError` | Class static initialization thất bại |
| `LinkageError` | Nhóm errors khi linking, có `IllegalAccessError` là một subclass |
| `SecurityException` | Quyền truy cập bị chặn bởi một runtime security mechanism theo cấu hình |

Không nên đổi tên exception bằng cách catch rồi rethrow ngẫu nhiên. Khi gói lỗi trong framework, giữ nguyên cause/stack trace để phân biệt **missing symbol, wrong loader, access violation, deep reflection denied**.

## 14. Những sai lầm phổ biến khi sửa lỗi

### Sai lầm 1: “Thêm setAccessible(true) sẽ sửa IllegalAccessError”

Sai. Direct bytecode access không đi qua Method object mà bạn vừa thay accessible flag. Nếu runtime API đã giảm visibility, cần sửa dependency hoặc biên dịch lại client với hợp đồng mới.

### Sai lầm 2: “exports là mở private fields”

Sai. Exports cho public API theo module access. Deep reflection đối với private member từ module khác thường cần `opens`, không chỉ `exports`.

### Sai lầm 3: “trySetAccessible trả false nghĩa là JVM có lỗi”

Không. False là kết quả hợp lệ khi không được phép suppress checks. Có thể fallback sang public API hoặc báo lỗi cấu hình rõ ràng.

### Sai lầm 4: “Cùng package text thì được package-private”

Sai trong môi trường khác defining loader. Kiểm tra runtime package và loader graph thực tế.

### Sai lầm 5: “Mọi lỗi khi Method.invoke đều là IllegalAccessException”

Sai. Nếu target method được gọi và body ném exception, wrapper là `InvocationTargetException`; nếu argument sai có thể `IllegalArgumentException`, receiver null với instance method có thể `NullPointerException`, v.v.

### Sai lầm 6: “Chỉ cần --add-opens toàn bộ JDK là xong”

Không nên. Việc mở rộng reflection access tăng phụ thuộc vào internal implementation và giảm ranh giới encapsulation. Ưu tiên cập nhật thư viện và dùng public APIs; nếu cần migration, cấp quyền hẹp đúng module/package.

### Sai lầm 7: “IllegalAccessError nên catch (Exception)”

`IllegalAccessError` kế thừa `Error`, không phải `Exception`. Một catch kiểu `Exception` sẽ không bắt nó. Catch đúng loại Error trong test hay ranh giới plugin để ghi log có thể hợp lý; không nên coi catch là phương án sửa deployment.

## 15. Quy trình điều tra lỗi trong ứng dụng thực tế

1. **Đọc tên exception đầy đủ và dòng stack trace đầu**: direct bytecode access, Method.invoke hay AccessibleObject.setAccessible?
2. **Chụp lại lệnh chạy thực tế**: `-cp`, `--module-path`, `--module`, `--add-exports`, `--add-opens` và plugin class loaders.
3. **Xem classfile được JVM nạp thật**: JAR version nào, class loader nào, module nào?
4. **Đối chiếu compile/runtime dependencies**: một API đã đổi access modifiers chưa?
5. **Nếu IllegalAccessError**, kiểm tra symbolic method/field access, class/interface visibility, runtime package identity và exported package.
6. **Nếu IllegalAccessException**, kiểm tra declaring class/member modifier và caller của reflection operation.
7. **Nếu InaccessibleObjectException**, tìm package/module mà framework cố open; xác định owner có `opens` tới caller không.
8. **Phân biệt access error với missing method/field**: `NoSuchMethodError` và `NoSuchFieldError` cần sửa binary version/signature.
9. **Nếu cần reflection**, ưu tiên public accessor, `trySetAccessible` để phát hiện graceful failure, và mở module hẹp nếu thực sự cần.
10. **Kiểm thử lại trong đúng runtime environment**, không chỉ IDE với một classpath khác.

Một số lệnh hỗ trợ:

~~~bash
jar --list --file library.jar
javap -classpath library.jar -p api.Library
javap -classpath out -c -p client.Main
~~~

Các lệnh là **mẫu**, giả định JAR/classes có thật. `javap -p` xem private/package-private members ở classfile runtime, `javap -c` xem bytecode callsite đã biên dịch. Đừng dựa chỉ vào source mới nhất mà không kiểm tra **artifact thực đang chạy**.

Khi chạy OpenJDK/HotSpot, có thể bật class loading logs:

~~~bash
java -Xlog:class+load=info -cp out client.Main
~~~

Output logs phụ thuộc JDK/JVM và có thể rất dài, nên không có một transcript cố định.

## 16. Strong encapsulation khi chuyển từ Java 8 lên Java 17

Một tình huống phổ biến: framework cũ gọi `setAccessible(true)` trên private fields thuộc classes của JDK. Trong Java 8, JPMS chưa tồn tại. Từ Java 9, các packages JDK được tổ chức thành named modules; trong giai đoạn chuyển tiếp từng có những tùy chọn compatibility.

**Java 17 mặc định mạnh tay encapsulate internals của JDK**. Vì vậy reflection vào private members của `java.base` (chẳng hạn nội bộ `java.lang.String`) có thể báo `InaccessibleObjectException` nếu package chưa open thích hợp. Đối với ứng dụng classpath, một flag có thể xuất hiện trong migration logs:

~~~bash
java --add-opens java.base/java.lang=ALL-UNNAMED -cp app.jar main.Main
~~~

Đây chỉ là **minh họa cú pháp** cần artifact/main thực tế; không khuyến nghị bật rộng khi có public API thay thế. `ALL-UNNAMED` chỉ các unnamed modules. Nếu caller là một **named module**, nên chọn chính tên module đích được cho phép.

`--illegal-access` từng phục vụ quá trình migration ở các bản JDK cũ, nhưng trong **Java 17** không còn cách dùng hữu hiệu để bật deep reflection mặc định; không dựa vào nó để sửa hệ thống mới.

## 17. Test matrix nên có trong thư viện và plugin framework

| Tình huống test | Kết quả cần quan sát |
| --- | --- |
| Client biên dịch với public API v1, chạy với v1 | Gọi thành công |
| Giữ Client.class v1, thay Library.class v2 giảm visibility | `IllegalAccessError` |
| Reflection gọi private method khi chưa suppress access | `IllegalAccessException` |
| Reflection trong cùng module, trySetAccessible hợp lệ | Trả true; invoke có thể thành công |
| Named module exports nhưng không opens, gọi private bằng invoke | `IllegalAccessException` |
| Named module exports nhưng không opens, gọi setAccessible(true) | `InaccessibleObjectException` |
| Mở package đúng target module rồi setAccessible | Thành công; invoke sau đó được |
| Hai classes cùng package source nhưng khác defining loader | Không tự cho package-private access |
| Plugin fat JAR có duplicated shared API | Kiểm tra correct defining loader và identity |
| Runtime dùng module path khác IDE | Kiểm tra exports/opens/readability và launcher arguments |
| Framework reflection vào JDK internals trên Java 17 | Đánh giá public API hoặc migration flag hẹp |
| Method body ném exception qua Method.invoke | `InvocationTargetException.getCause()` giữ lỗi thật |

Các tests này giúp phân biệt **lỗi linkage, lỗi reflection access và lỗi JPMS encapsulation** thay vì vá ngẫu nhiên một exception message.

## 18. Tự kiểm tra

**1. `IllegalAccessError` là checked exception?** Không; là Error thuộc nhánh IncompatibleClassChangeError/LinkageError.

**2. Vì sao code chạy với lib v1 nhưng lỗi khi chỉ thay lib v2?** Client bytecode được compile theo access contract v1, nhưng runtime definition v2 có visibility không tương thích.

**3. `IllegalAccessException` thường đến từ API nào?** Method.invoke, Field.get/set, Constructor.newInstance và một số APIs lookup tương ứng khi caller thiếu quyền.

**4. `InaccessibleObjectException` hay phát sinh ở đâu?** AccessibleObject.setAccessible(true) khi suppression bị từ chối, ví dụ package named module không opens tới caller.

**5. `getDeclaredMethod` tìm thấy private method có nghĩa gọi được không?** Không; metadata lookup và invocation access là hai thao tác khác nhau.

**6. `trySetAccessible()` trả false có giúp bypass access checks không?** Không. False báo suppression không được phép.

**7. `exports p` đủ gọi private method của p qua deep reflection?** Không thông thường; cần opens phù hợp hoặc caller có quyền hợp lệ theo API rules.

**8. `--add-opens` tự sửa IllegalAccessError do direct call tới method package-private?** Không; nó không biến private/package-private thành public cho direct bytecode access.

**9. Hai Class cùng source package do hai loaders khác nhau định nghĩa có tự là cùng runtime package không?** Không.

**10. InvocationTargetException khác IllegalAccessException thế nào?** Cái đầu bọc exception được ném **trong method đã được gọi**; cái sau ngăn invoke vì caller không có quyền.

**11. Nếu gặp lỗi truy cập khi chạy Java 17 mà Java 8 không gặp, kiểm tra gì?** JPMS strong encapsulation, opens/exports, runtime JDK và framework reflection vào JDK internals.

**12. `catch (Exception)` bắt được IllegalAccessError không?** Không, vì IllegalAccessError là Error.

**13. Khi một class có public method nhưng top-level class package-private, caller package khác có luôn gọi được không?** Không; quyền truy cập declaring class cũng được kiểm tra.

**14. Có nên mở mọi package cho mọi unnamed module để tiện debug không?** Không; hãy cập nhật code/library, chọn public API và chỉ mở scope cần thiết.

**15. Cần xác định gì ở một stack trace có custom class loader?** Binary caller/target, defining loaders, module membership, dependency version và runtime package identity.

## 19. Tài liệu chuẩn

- Java 17, **IllegalAccessError**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IllegalAccessError.html
- Java 17, **IllegalAccessException**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IllegalAccessException.html
- Java 17, **InaccessibleObjectException**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InaccessibleObjectException.html
- Java 17, **AccessibleObject** — `setAccessible`, `trySetAccessible`, `canAccess`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html
- Java 17, **Method** — `invoke` và checked access errors: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html
- Java 17, **Module** — readability, exported/open packages: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html
- Java 17, **ClassLoader** — defining loaders và delegation: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- JLS 17 `6.6 — Access Control: https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.6
- JVMS 17 `5.4.4 — Access Control: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.4
- JLS 17 `13 — Binary Compatibility: https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html
- Oracle JDK 17 Migration Guide — Strong Encapsulation: https://docs.oracle.com/en/java/javase/17/migrate/migrating-jdk-8-later-jdk-releases.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-class-loading-errors-010">ClassCastException do khác định danh loader</a>

<details>
<summary>Xem chi tiết</summary>

# ClassCastException do khác định danh loader

## 1. Vì sao một class dường như không cast được thành “chính nó”?

Khi ứng dụng Java có nhiều `ClassLoader`, đôi khi bạn gặp lỗi khó hiểu:

~~~text
java.lang.ClassCastException:
class plugin.Worker cannot be cast to class plugin.Worker
~~~

Trên JDK mới, thông điệp thường bổ sung tên **module và loader** ở mỗi phía; nội dung chính xác có thể khác giữa các môi trường nên không nên viết test dựa trên toàn bộ chuỗi exception.

Nguyên nhân: trong JVM, **tên class không phải toàn bộ danh tính của kiểu**. Với một class/interface thông thường, danh tính runtime là cặp:

~~~text
(binary name, defining class loader)
~~~

Ví dụ:

~~~text
("plugin.Worker", LoaderA) != ("plugin.Worker", LoaderB)
~~~

Ngay cả khi hai loader đọc **chính cùng một file `plugin/Worker.class` với bytecode giống nhau**, nếu cả hai thực sự định nghĩa class, JVM tạo **hai runtime class khác nhau**.

Hệ quả:

- `classA.getName().equals(classB.getName())` có thể trả `true`.
- `classA == classB` trả `false` nếu defining loaders khác nhau.
- `classA.isInstance(objectB)` trả `false` nếu objectB thuộc bản class khác không có quan hệ subtype được JVM công nhận.
- `classA.cast(objectB)` ném `ClassCastException`.
- Cùng một interface dùng trong source code cũng có thể bị **định nghĩa hai lần**, khiến implementation của plugin không thể cast sang interface ở host.

Đây là lỗi **runtime type identity**, không phải lỗi do viết sai tên package hay Java compiler không hiểu `import`.

## 2. Defining loader và initiating loader

Để debug chính xác, phải phân biệt:

| Khái niệm | Ý nghĩa |
| --- | --- |
| **Binary name** | Tên nhị phân của class, ví dụ `plugin.Worker`. |
| **Defining class loader** | Loader thực sự định nghĩa class trong JVM; là thành phần của runtime identity. |
| **Initiating class loader** | Loader khởi đầu yêu cầu tải class; có thể delegate cho loader khác định nghĩa. |
| **Parent-first delegation** | Child thử hỏi parent trước, sau đó mới tìm trong nguồn riêng nếu parent không thấy. |
| **Class object** | Đối tượng `Class<?>` đại diện đúng một kiểu runtime cụ thể, không chỉ chuỗi tên. |

Ví dụ minh họa:

~~~java
Class<?> type = child.loadClass("api.Task");
ClassLoader definingLoader = type.getClassLoader();
~~~

Không được suy ra `definingLoader == child`. Với parent-first, nếu parent đã nạp `api.Task` thì `child.loadClass` trả lại **Class của parent**.

Do đó, chỉ có bản sao `api.Task.class` trong JAR plugin **chưa đủ kết luận bị xung đột**. Nếu child luôn ủy quyền parent cho interface và parent có class ấy, bản sao có thể không được sử dụng. Lỗi xuất hiện khi có **hai bản thực sự được định nghĩa bởi hai loader**.

### Hai class triển khai riêng và một API chung

~~~text
             Host / Application Loader
             ├── api.Task
             └── PluginManager
                       |
          +------------+------------+
          |                         |
       Loader A                  Loader B
       plugin.Worker             plugin.Worker
       implements api.Task       implements api.Task
       (Task từ parent)          (Task từ parent)
~~~

Hai `plugin.Worker` của A/B là hai kiểu khác nhau; nhưng **cả hai có thể được cast sang cùng interface `api.Task`** nếu dùng bản Task do parent định nghĩa. Đó là mô hình chuẩn của hệ thống plugin.

## 3. Hai tình huống ClassCastException khác nhau

### Tình huống A — cast giữa hai bản implementation trùng tên

~~~java
Class<?> fromA = loaderA.loadClass("plugin.Worker");
Class<?> fromB = loaderB.loadClass("plugin.Worker");
Object objectB = fromB.getDeclaredConstructor().newInstance();

System.out.println(fromA.getName().equals(fromB.getName())); // true
System.out.println(fromA == fromB);                          // false
System.out.println(fromA.isInstance(objectB));               // false
fromA.cast(objectB); // ClassCastException
~~~

Đây là đoạn minh họa trong context có hai loader thật sự định nghĩa riêng `plugin.Worker`, constructor truy cập được. Dùng đúng code đầy đủ ở phần 6 để tái hiện.

Ngay cả nếu plugin A và B được biên dịch từ source giống nhau, một instance Worker của B không tự trở thành instance Worker của A. Nếu host cần dùng cả hai, hãy thao tác qua **shared interface** chứ không ép từ concrete class của loader này sang concrete class của loader kia.

### Tình huống B — chính interface API cũng bị nhân đôi

~~~text
Host loader:
  api.Task            -- interface host mong đợi

Isolated plugin loader:
  api.Task            -- bản interface do plugin định nghĩa
  plugin.Worker       -- implements Task trong isolated loader
~~~

Cast kiểu:

~~~java
api.Task task = (api.Task) isolatedObject;
~~~

có thể ném `ClassCastException`, dù mã plugin có `implements api.Task`. Lý do: ký hiệu `api.Task` trong bytecode plugin được resolve thành **Task của isolated loader**, còn phép cast của host tham chiếu **Task của host loader**. Tên giống, interface runtime khác.

Đây là tình huống khó hơn vì đọc source/JAR sẽ thấy type name hoàn toàn hợp lý.

## 4. Duplicate JAR, delegation và chọn package policy

| Tình huống | Khả năng xảy ra |
| --- | --- |
| Plugin JAR có `api.Task.class`, child parent-first và parent có Task | Có thể vẫn dùng **Task của parent**; không nhất thiết lỗi cast. |
| Child-first với cả `api.*` | Child có thể define bản Task khác; nguy cơ ClassCastException. |
| Child có parent là platform/bootstrap, không thấy API host | Child tự define Task từ plugin archive; khác Task host. |
| Hai sibling loaders tự define `plugin.Worker` | Hai runtime types Worker khác nhau dù cùng bytes. |
| Host vô tình thêm plugin JAR vào application classpath | Parent có thể tải implementation sớm, đảo kết quả kỳ vọng về defining loader. |
| Plugin reload tạo loader v2 | Worker v1 và Worker v2 không cùng concrete type, kể cả binary name giống. |

Giải pháp cho SPI/API:

1. Đặt **interface/hợp đồng chung** (và các types đi qua public method signatures) trong classpath/module của host hoặc loader cha được chia sẻ.
2. Để child **parent-first với package của shared API**.
3. Chỉ dùng child-first có chọn lọc với package implementation hoặc dependency cần cách ly.
4. Không cast concrete class giữa các plugin versions/loaders.
5. Nếu cần giao tiếp giữa các lớp riêng, dùng API chung, adapter, DTO chung hoặc serialization có hợp đồng rõ ràng.

**Lưu ý:** `ClassLoader.getParent()` chỉ cho parent trực tiếp; thực tế custom loader có thể override lookup, nên không dựa vào cây loader đơn giản để đoán class identity. Hãy kiểm tra `clazz.getClassLoader()` và `clazz == otherClazz` ngay tại runtime.

## 5. Class.cast, instanceof và isAssignableFrom

Các phép này có mục tiêu liên quan nhưng khác hình thức:

| Phép kiểm tra | Dùng cho | Khi type identity không tương thích |
| --- | --- | --- |
| `TargetType.class.isInstance(object)` | Kiểm tra một object thuộc type/subtype cụ thể không | Trả `false` |
| `TargetType.class.cast(object)` | Cast động bằng đối tượng Class | Ném `ClassCastException` với non-null object không tương thích |
| `object instanceof TargetType` | Kiểm tra runtime type ở code Java | `false` |
| `TargetType.class.isAssignableFrom(otherClass)` | Kiểm tra class/interface khác có assignable tới type này không | `false` |
| `clazzA == clazzB` | Kiểm tra có đúng **cùng runtime Class object** không | `false` |
| `clazzA.getName().equals(clazzB.getName())` | So sánh chuỗi binary name | Có thể `true` dù cast thất bại |

Đừng dùng so tên class bằng chuỗi để thay thế cho `isAssignableFrom`. Với plugin API, câu hỏi thật là **implementation class có tương thích với interface Class của host không**, chứ không phải implementation và interface có tên giống nhau không.

Đối với `Class.cast(null)`, giống cast reference thông thường, `null` không gây ClassCastException; những ví dụ trong bài cố ý dùng instance thật để minh họa.

## 6. Chương trình Java 17+ độc lập: lỗi cast và shared API đúng cách

Chương trình gồm **ba file source**. Đặc biệt, ta sẽ compile `api.Task` vào **hai output folders khác nhau** để có khả năng tạo bản sao API khi loader cô lập đọc plugin root.

Cấu trúc:

~~~text
example/
├── src/
│   ├── api/Task.java
│   ├── plugin/Worker.java
│   └── host/IdentityDemo.java
└── out/
    ├── host/           (Task + IdentityDemo)
    └── plugin/         (Task + Worker)
~~~

**Chỉ `out/host` được đưa vào application classpath khi chạy.** `out/plugin` được truyền qua tham số, không đưa vào `-cp`.

### src/api/Task.java

~~~java
package api;

public interface Task {
    String run();
}
~~~

### src/plugin/Worker.java

~~~java
package plugin;

import api.Task;

public final class Worker implements Task {
    @Override
    public String run() {
        return "plugin-ok";
    }
}
~~~

### src/host/IdentityDemo.java

~~~java
import api.Task;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class IdentityDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("plugin class root");
        }

        URL root = Path.of(args[0]).toAbsolutePath()
                .normalize().toUri().toURL();
        ClassLoader host = IdentityDemo.class.getClassLoader();

        try (URLClassLoader a = new URLClassLoader(
                     new URL[]{root}, host);
             URLClassLoader b = new URLClassLoader(
                     new URL[]{root}, host);
             URLClassLoader isolated = new URLClassLoader(
                     new URL[]{root},
                     ClassLoader.getPlatformClassLoader())) {

            Class<?> classA = a.loadClass("plugin.Worker");
            Class<?> classB = b.loadClass("plugin.Worker");

            Object objectA =
                    classA.getDeclaredConstructor().newInstance();
            Object objectB =
                    classB.getDeclaredConstructor().newInstance();

            System.out.println("same binary name: "
                    + classA.getName().equals(classB.getName()));
            System.out.println("same runtime Class: "
                    + (classA == classB));
            System.out.println("defined by A/B: "
                    + (classA.getClassLoader() == a
                    && classB.getClassLoader() == b));
            System.out.println("A isInstance(B): "
                    + classA.isInstance(objectB));

            try {
                classA.cast(objectB);
                System.out.println("unexpected cast success");
            } catch (ClassCastException expected) {
                System.out.println(
                        "cross-loader cast: ClassCastException");
            }

            System.out.println("shared API A: "
                    + (a.loadClass("api.Task") == Task.class));
            System.out.println("shared API B: "
                    + (b.loadClass("api.Task") == Task.class));

            System.out.println("A via API: "
                    + ((Task) objectA).run());
            System.out.println("B via API: "
                    + ((Task) objectB).run());

            Class<?> copyApi = isolated.loadClass("api.Task");
            Object isolatedObject =
                    isolated.loadClass("plugin.Worker")
                            .getDeclaredConstructor().newInstance();

            System.out.println("isolated API same: "
                    + (copyApi == Task.class));
            System.out.println("isolated implements host API: "
                    + (isolatedObject instanceof Task));

            try {
                Task invalid = (Task) isolatedObject;
                System.out.println(
                        "unexpected API cast success: " + invalid.run());
            } catch (ClassCastException expected) {
                System.out.println(
                        "isolated API cast: ClassCastException");
            }
        }
    }
}
~~~

**Cơ chế của từng loader:**

- `a` và `b` lấy **host loader làm parent**. Với `api.Task`, cả hai delegate về host. Nhưng parent không có `plugin.Worker` nên chúng tự define Worker khác nhau.
- `isolated` dùng **platform loader làm parent**, không dùng application loader của host. Khi nó cần `api.Task`, parent không nhìn thấy Task của ứng dụng, nên isolated định nghĩa Task từ `out/plugin`.
- Ba loader đều trỏ tới cùng URL thư mục `out/plugin`; khác biệt đến từ **parent/delegation và defining loader**, không phải khác tên file.
- `try-with-resources` đóng loader sau thí nghiệm; việc đóng không hứa hẹn class đã unload ngay.

### Lệnh Linux/macOS

Đứng trong thư mục `example` có ba source files:

~~~bash
mkdir -p out/host out/plugin

javac --release 17 -d out/host \
  src/api/Task.java src/host/IdentityDemo.java

javac --release 17 -d out/plugin \
  src/api/Task.java src/plugin/Worker.java

java -cp out/host IdentityDemo out/plugin
~~~

### Lệnh Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force out\host, out\plugin | Out-Null
javac --release 17 -d out\host src\api\Task.java src\host\IdentityDemo.java
javac --release 17 -d out\plugin src\api\Task.java src\plugin\Worker.java
java -cp out\host IdentityDemo out\plugin
~~~

Việc compile `Task.java` hai lần là **cố ý để tạo bản copy** cho thử nghiệm. Khi build thật, API contract nên được định nghĩa nhất quán ở parent và không được child define một bản runtime thứ hai.

### Stdout chính xác đã kiểm chứng

~~~text
same binary name: true
same runtime Class: false
defined by A/B: true
A isInstance(B): false
cross-loader cast: ClassCastException
shared API A: true
shared API B: true
A via API: plugin-ok
B via API: plugin-ok
isolated API same: false
isolated implements host API: false
isolated API cast: ClassCastException
~~~

Bài đã được biên dịch bằng `javac --release 17` và chạy trên OpenJDK 21. Đầu ra trên khớp từng dòng.

### Diễn giải kết quả

1. **same binary name: true** — A và B đều tải `plugin.Worker`.
2. **same runtime Class: false** — Worker do hai defining loaders khác nhau tạo.
3. **defined by A/B: true** — hai implementation thực sự được định nghĩa riêng.
4. **A isInstance(B): false** — B object không thuộc A concrete class.
5. **cross-loader cast: ClassCastException** — cast chéo concrete class thất bại đúng dự kiến.
6. **shared API A/B: true** — A/B đều nhận bản `api.Task` ở host do parent-first.
7. **A/B via API: plugin-ok** — host vẫn giao tiếp được với cả hai Worker qua cùng interface Task.
8. **isolated API same: false** — loader cô lập đã định nghĩa Task riêng từ plugin root.
9. **isolated implements host API: false** — một Worker implements isolated Task không phải instance của host Task.
10. **isolated API cast: ClassCastException** — phép ép sang host interface thất bại.

Đây là cách tái hiện **hai lỗi định danh riêng biệt**: cast concrete implementation A ↔ B, và cast một implementation có copy interface vào interface của host.

## 7. Cách sửa trong ứng dụng plugin thực tế

Kiến trúc nên có một **shared API package** và những implementation loaders riêng:

~~~text
Parent / Host ClassLoader
  ├── api.Task       (một runtime interface duy nhất)
  └── PluginManager
      |
      +--- Plugin Loader v1
      |       plugin.Worker
      |
      +--- Plugin Loader v2
              plugin.Worker
~~~

Host chỉ phụ thuộc API:

~~~java
Class<?> impl = pluginLoader.loadClass("plugin.Worker");
api.Task task = (api.Task) impl
        .getDeclaredConstructor().newInstance();

System.out.println(task.run());
~~~

Đây là đoạn code minh họa trong method, với `pluginLoader`, interface Task và constructor/method hợp lệ. Implementation v1 và v2 **không cần cast qua lại**.

Biện pháp thực tế:

- **Chỉ một chủ sở hữu API:** package `api.*` trong loader cha; plugin biên dịch dựa vào API ấy nhưng runtime dùng bản cha.
- **Parent-first cho API/shared types:** nếu chọn child-first để cô lập dependency plugin, loại trừ package contract khỏi chính sách child-first.
- **Không trao concrete objects qua boundary khi không cần:** dùng interface, DTO được chia sẻ, các primitive/string hoặc message/serialization.
- **Đồng nhất types trong signatures:** exceptions, return values, method parameters, DTO và parent interfaces cần có class identity thống nhất khi đi qua ranh giới.
- **Plugin isolation có kiểm thử:** xác nhận `pluginLoader.loadClass("api.Task") == api.Task.class` và `api.Task.class.isAssignableFrom(impl)`.
- **Đừng chỉ sửa dependency scope:** Maven `provided` hoặc Gradle `compileOnly` có thể giúp không đóng gói bản runtime API thứ hai, nhưng host **phải cung cấp thật API** và loader policy cần delegate chính xác.
- **JAR shading/relocation có cân nhắc:** cách ly private dependencies bằng tên package riêng có thể tránh xung đột, nhưng không relocate interface API chung nếu host cần cast qua interface đó.

Một plugin JAR có API trùng tên **không nhất thiết phải gây lỗi** khi child parent-first; mặt khác, một custom loader child-first sai có thể tạo bản sao dù build tree trông “sạch”. Phải kiểm tra bằng runtime facts.

## 8. Debug thực tế: in đúng Class và defining loader

Khi có ClassCastException, đừng chỉ in tên hai class. Hãy in đủ:

~~~java
static void inspect(Object actualObject, Class<?> expected) {
    Class<?> actual = actualObject.getClass();

    System.out.println("actual: " + actual.getName());
    System.out.println("expected: " + expected.getName());
    System.out.println("same Class: " + (actual == expected));
    System.out.println("compatible: "
            + expected.isInstance(actualObject));
    System.out.println("actual loader: " + actual.getClassLoader());
    System.out.println("expected loader: " + expected.getClassLoader());
    System.out.println("actual module: " + actual.getModule());
    System.out.println("expected module: " + expected.getModule());
}
~~~

Đây là **helper minh họa**, không phải chương trình chạy độc lập. Tên loader/identity string và module có thể thay đổi theo JVM.

**Quan trọng:** nếu `expected` là một interface thì `actual == expected` thường false dù cast **hợp lệ** (implementation khác interface). Vì vậy hãy kiểm tra `expected.isInstance(actualObject)` và **identity của interface thực sự được implementation nạp**:

~~~java
Class<?> taskSeenByPlugin =
        pluginLoader.loadClass("api.Task");

System.out.println(taskSeenByPlugin == api.Task.class);
~~~

Kết quả false là chỉ dấu mạnh về hai interface classes khác nhau. Nhưng kết quả true chỉ chứng minh **Task identity** là chung; vẫn cần kiểm tra implementation thật sự implements Task và constructor/linking không có lỗi khác.

### Kiểm tra CodeSource và modules

Khi cần xác định JAR vật lý:

~~~java
Class<?> type = actualObject.getClass();
var domain = type.getProtectionDomain();
var source = domain == null ? null : domain.getCodeSource();

System.out.println(source == null
        ? "(unknown source)"
        : source.getLocation());
~~~

Không bảo đảm mọi class đều có CodeSource URL hữu ích; custom loaders có thể cung cấp metadata khác hoặc null. **CodeSource chỉ giúp điều tra nguồn bytes**, còn defining loader vẫn là yếu tố cần thiết cho type identity.

`Class.getModule()` giúp phân biệt named/unnamed modules trên Java 9+, nhưng **cùng một module name không tự làm hai classes khác defining loader trở thành cùng kiểu**. Các lớp thuộc bootstrap loader có thể trả `getClassLoader() == null`; null ở đây **không** nghĩa class chưa được nạp.

### Log nạp lớp

JDK 17+ có thể dùng:

~~~bash
java -Xlog:class+load=info -cp out/host IdentityDemo out/plugin
~~~

Đây là lệnh chẩn đoán, output sẽ dài và khác nhau giữa JDKs; lọc theo `plugin.Worker` và `api.Task`. Không dùng toàn bộ log như stdout cố định của bài.

## 9. Phân biệt ClassCastException với các lỗi loader khác

| Exception/Error | Hiện tượng | Hướng điều tra |
| --- | --- | --- |
| `ClassNotFoundException` | Lookup bằng tên không tìm thấy class. | Loader đang được hỏi có source/URL/module nào? |
| `NoClassDefFoundError` | Một dependency không thể được định nghĩa khi cần, hoặc class có initialization failure trước đó. | Cause và defining loader của class chứa symbolic reference. |
| `ClassCastException` | Object không tương thích với runtime target type. | Hai class/interface có cùng defining loader identity không? |
| `LinkageError: loader constraint violation` | JVM phát hiện yêu cầu type identity không nhất quán giữa loader tại linking. | Shared method/field signature types và loading constraints. |
| `NoSuchMethodError` | Runtime method được resolve không khớp bản compile. | Version/dependency parent-vs-child. |
| `IllegalAccessError` | JVM access checks thất bại. | Module exports, package access, JVM linking. |

ClassCastException cũng có thể là một lỗi cast **thông thường không liên quan nhiều loaders**, ví dụ ép Integer thành String. Chỉ kết luận xung đột loader khi metadata của hai runtime types chứng minh điều đó.

Ngược lại, trong một số tương tác xuyên loader, JVM áp dụng **loading constraints** cho type names trong method/field signatures để bảo vệ type safety. Lỗi có thể xuất hiện dưới dạng `LinkageError` **trước khi** có phép cast cụ thể.

## 10. Reload plugin và ClassCastException sau deploy

Một quá trình hot reload thường tạo loader mới cho version khác:

~~~text
Loader L1: plugin.Worker (v1)
Loader L2: plugin.Worker (v2)
~~~

Nếu host giữ object của L1 rồi cố cast nó sang **concrete Class của L2**, lỗi hoàn toàn có thể xảy ra, dù v1 và v2 có code tương tự.

Quy trình an toàn:

1. Thiết kế host–plugin boundary bằng interface/DTO ở parent.
2. Đưa v2 vào loader mới, thử tạo instance và cast qua **shared interface**.
3. Chuyển routing của request mới sang v2 sau health check.
4. Đợi request v1 hoàn tất; dừng callbacks, tasks, threads, unregister và đóng resources v1.
5. Đóng loader v1 khi thích hợp; bỏ references cũ từ caches/registries/ThreadLocal/TCCL.
6. Không cast implementation object từ v1 thành concrete class v2.

`URLClassLoader.close()` không tự xóa class/object đã nạp. Class unloading còn phụ thuộc reachability và GC. Đừng dùng `System.gc()` hoặc restart JVM như giải pháp mặc định nếu lỗi thực tế là trộn runtime class identities; trước hết sửa ranh giới chia sẻ và lifecycle.

## 11. Thread Context ClassLoader và ServiceLoader

Thread context class loader (TCCL) là loader mà framework có thể **chủ động chọn** để tìm services/resources:

~~~java
Thread current = Thread.currentThread();
ClassLoader old = current.getContextClassLoader();

try {
    current.setContextClassLoader(pluginLoader);
    // Gọi framework/API có hợp đồng tìm service bằng TCCL.
} finally {
    current.setContextClassLoader(old);
}
~~~

Đoạn minh họa cần biến `pluginLoader` tồn tại. Một ví dụ API chủ động dùng TCCL là `ServiceLoader.load(ServiceType.class)`. Tuy nhiên TCCL **không sửa được type identity** nếu provider implements bản copy interface khác. Một plugin do TCCL tìm thấy vẫn phải tương thích với Class service ở host.

Không nhầm TCCL với defining loader: khi JVM resolve symbolic references trong bytecode, nó dựa trên defining loader của class chứa reference cùng các loading constraints; nó không tự hỏi TCCL cho mọi dependency.

Với ứng dụng JPMS, `exports` và `opens` điều khiển access/reflection qua module boundary, nhưng không hợp nhất hai Class objects do hai defining loaders khác nhau tạo. Sửa xung đột loader bằng `--add-opens` thường không đúng nguyên nhân.

## 12. Checklist chẩn đoán và ôn tập

Hướng chẩn đoán khi cast lỗi:

1. **Object nào** đang cast và **target Class** nào là đích?
2. Binary name của hai lớp là gì? Có phải chỉ giống tên không?
3. Defining loader của actual class và expected type là ai?
4. Nếu target là interface, plugin có dùng đúng **Class interface** của parent không?
5. Có duplicate API JAR trong plugin, shaded JAR hoặc child-first quá rộng không?
6. Parent loader có vô tình nạp plugin implementation trên application classpath không?
7. Sau reload, có trộn object v1 với Class v2 không?
8. Có thể thay cast concrete implementation bằng cast qua shared interface không?
9. Nếu xảy ra LinkageError/NoSuchMethodError, dependency versions hoặc loading constraints có bất nhất không?
10. Với JPMS, access failure do exports/opens có bị nhầm với ClassCastException không?
11. Cache, callback, TCCL, ThreadLocal hoặc registry nào còn giữ refs loader cũ?
12. Có test runtime class identity bằng `==`, `isInstance` và `isAssignableFrom` chưa?

### Câu hỏi tự kiểm tra

**Câu 1.** Hai loader cùng đọc bytecode Worker và trả class có tên `plugin.Worker`. Tại sao `classA == classB` false?

**Đáp:** JVM phân biệt class runtime theo **binary name và defining loader**. Cùng bytecode/tên không làm hai định nghĩa trở thành một.

**Câu 2.** Worker của Loader A và Loader B có thể cùng được ép sang `api.Task` không?

**Đáp:** Có, nếu cả hai implementation đều thực sự implements **cùng runtime interface Task** do một loader chung định nghĩa.

**Câu 3.** Plugin source có `implements api.Task`, nhưng host cast object vào Task lại lỗi. Vì sao?

**Đáp:** Plugin có thể đã dùng **Task được loader riêng định nghĩa**, khác Task của host; import/name giống nhau chưa đủ.

**Câu 4.** JAR chứa bản copy Task có chắc gây cast lỗi không?

**Đáp:** Không. Nếu parent-first delegate về Task do parent định nghĩa, copy ở child có thể không được load.

**Câu 5.** Có thể dùng `--add-opens` để hợp nhất hai classes trùng tên khác loader không?

**Đáp:** Không. Opens chỉ tác động tới access/reflection của JPMS, không đổi danh tính Class.

**Câu 6.** Cast concrete implementation giữa hai phiên plugin sau reload có phải luôn hợp lệ nếu hai phiên bản cùng source?

**Đáp:** Không. Nếu chúng do hai defining loaders khác nhau định nghĩa, concrete runtime classes khác nhau. Dùng shared API boundary thay thế.

## Tài liệu chuẩn

- [JVMS Java SE 21 §5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): danh tính class theo binary name và defining loader.
- [JVMS Java SE 21 §5.3.4 — Loading Constraints](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3.4): bảo vệ type safety khi nhiều loaders.
- [JLS Java SE 21 §12.2.2 — Class Loader Consistency](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.2.2): consistency của class loader.
- [Java SE 21 — java.lang.Class](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): `cast`, `isInstance`, `isAssignableFrom` và `getClassLoader`.
- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): delegation và defining loader.
- [Java SE 21 — ClassCastException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassCastException.html): exception cast runtime không tương thích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-class-loading-errors-011">SecurityException do package sealing, chứng thư hoặc package bị cấm</a>

<details>
<summary>Xem chi tiết</summary>

# SecurityException do package sealing, chứng thư hoặc package bị cấm

Khi ứng dụng Java nạp class từ JAR, `ClassLoader` có thể ném **`java.lang.SecurityException`** dù class file tồn tại, đường dẫn đúng và bytecode đã biên dịch hợp lệ. Trong chủ đề **Java core – classloader**, có ba nguyên nhân dễ bị nhầm:

1. **Package sealing violation**: loader định nghĩa các class thuộc **cùng runtime package** từ những nguồn JAR không phù hợp với quy tắc package `Sealed`.
2. **Package signer/certificate mismatch**: loader đã định nghĩa class đầu tiên trong package với **một tập chứng thư người ký**, nhưng class tiếp theo cùng package có tập chứng thư **khác** (ví dụ signed và unsigned).
3. **Prohibited package name `java.*`**: application/custom loader cố **tự định nghĩa** một class có binary name bắt đầu bằng `java.`, trong khi Java chỉ cho phép nhóm loader nền tảng được quy định làm việc này.

Ba trường hợp đều có thể biểu hiện là `SecurityException`, nhưng **quy tắc kiểm tra, nguyên nhân và cách sửa khác nhau**. Chúng cũng **không phụ thuộc vào việc SecurityManager đang được bật**: đặc biệt từ JDK 24, SecurityManager đã bị vô hiệu hóa vĩnh viễn nhưng các ràng buộc class loading trên vẫn có ý nghĩa riêng.

## 1. Những khái niệm phải tách biệt

Trước khi sửa lỗi, cần phân biệt các thuật ngữ:

| Thuật ngữ | Ý nghĩa |
|---|---|
| **Binary class name** | Tên như `sample.api.First`, `java.demo.Forbidden`; truyền vào `ClassLoader.loadClass` và `defineClass` |
| **JAR entry name** | Đường trong archive như `sample/api/First.class`; khác binary name |
| **Defining loader** | Loader thực sự định nghĩa runtime class; có thể khác loader được gọi ban đầu do parent delegation |
| **Runtime class identity** | Cặp **(binary name, defining class loader)** |
| **Runtime package identity** | Đối với class thông thường, cặp **(package name, defining class loader)** |
| **JAR sealing** | Quy ước manifest/package nhằm buộc các class trong một package được loader nạp từ một nguồn JAR phù hợp |
| **Signer certificate set** | Tập certificates của code source gắn với class khi định nghĩa, được kiểm tra tính nhất quán trong package của loader |
| **`ProtectionDomain` / `CodeSource`** | Metadata liên quan class/code source, có thể chứa URL và certificates |
| **`SecurityManager`** | Cơ chế permission checks mang tính lịch sử, khác với các kiểm tra class definition ở đây |

Một JAR chứa `a.b.C` **không bảo đảm** class được nạp thành công: JVM/loader còn kiểm tra tên, nguồn, quyền package, chữ ký, phiên bản class file và type linking.

**Quan trọng:** Cùng tên package trong hai loader khác nhau **không phải cùng runtime package**. Điều kiện sealing và signer được đánh giá trong ngữ cảnh loader/package phù hợp, không có nghĩa mọi JAR trong toàn JVM buộc dùng chung một tập certificate.

## 2. Ba nguyên nhân SecurityException: so sánh nhanh

| Tình huống | Nguồn kiểm tra | Ví dụ | Hướng khắc phục |
|---|---|---|---|
| **Sealing violation** | JAR manifest/package metadata, loader như `URLClassLoader` | `sample.api.First` từ JAR A sealed, `sample.api.Second` từ JAR B | Đưa class cùng sealed package về đúng JAR, hoặc thiết kế lại sealing/package |
| **Signer mismatch** | `ClassLoader#defineClass` và certificate set của package | Class đầu từ signed JAR, class sau unsigned trong cùng package/loader | Ký các classes nhất quán, không trộn unsigned/signed, hoặc tách namespace/loader đúng kiến trúc |
| **`java.*` prohibited** | `ClassLoader#defineClass` và reserved platform namespace | Custom loader định nghĩa `java.demo.Forbidden` | Đổi tên package thành namespace ứng dụng; ủy quyền các classes chuẩn cho platform |
| **Legacy permission denial** | SecurityManager trong runtime cũ khi được bật | `checkPermission(new FilePermission(...))` | Kiểm tra JDK/policy cũ; **không đánh đồng** với ba lỗi trên |

Trên OpenJDK/HotSpot, stack trace hoặc message thường có cụm:

```text
sealing violation: package ... is sealed
sealing violation: can't seal package ...: already loaded
class "..."'s signer information does not match signer information of other classes in the same package
Prohibited package name: java.demo
```

Đây là **các mẫu thông điệp chẩn đoán**, không phải chuỗi cố định được mọi Java implementation cam kết. Có thể có khác biệt về chữ hoa, dấu câu, stack trace, phiên bản JDK và framework. **Dựa vào exception class, root cause và classloader/code source**, đừng viết chương trình bắt lỗi bằng cách so khớp nguyên văn message.

## 3. Package sealing là gì?

Trong **JAR File Specification**, một package có thể được khai báo **sealed**. Với loader hỗ trợ JAR manifest sealing, mục đích là bảo đảm các class được **loader đó** định nghĩa trong package ấy đến từ **cùng nguồn JAR** theo quy tắc sealing.

Ví dụ JAR A:

```text
a.jar
├── META-INF/
│   └── MANIFEST.MF
└── sample/
    └── api/
        └── First.class
```

Manifest:

```text
Manifest-Version: 1.0
Sealed: true

```

JAR B:

```text
b.jar
└── sample/
    └── api/
        └── Second.class
```

Nếu **cùng một `URLClassLoader`** nạp `sample.api.First` từ A và sau đó cố nạp `sample.api.Second` từ B, package `sample.api` đã được ghi nhận là sealed với nguồn A; lớp của B không đến từ seal base tương ứng. Loader có thể ném `SecurityException`.

**Không phải lỗi tên package trùng tự thân.** Nếu hai JAR đều không sealed và có cùng package, trong mô hình classpath/unnamed module thông thường, chúng có thể cùng cung cấp các class **khác binary name** trong cùng runtime package — tuy nhiên vẫn còn những ràng buộc độc lập như signer certificates hoặc module resolution.

### 3.1. Manifest khai báo sealing toàn JAR

Đặt ở **main section**:

```text
Manifest-Version: 1.0
Sealed: true

```

`Sealed: true` mặc định áp dụng cho các packages trong JAR, trừ những package có override riêng trong individual manifest sections.

`Sealed: false` hoặc không có thuộc tính `Sealed` thường dẫn đến package không sealed, theo quy tắc specification và package-level override.

### 3.2. Chỉ seal một package

Một manifest có thể cấu hình cụ thể:

```text
Manifest-Version: 1.0

Name: sample/api/
Sealed: true

```

Lưu ý:

- `Name: sample/api/` là **package directory entry name**, dùng dấu `/` và dấu `/` cuối.
- Dòng trống phân tách **main section** và individual section.
- Đây là dữ liệu **manifest**, không phải Java source.
- Các package khác không tự trở thành sealed chỉ vì một package được đánh dấu `Sealed: true`.

Một pattern khác:

```text
Manifest-Version: 1.0
Sealed: true

Name: sample/api/
Sealed: false

```

có nghĩa mọi package mặc định sealed, **riêng `sample.api` được ghi đè thành unsealed**. Không tự viết các section sai cú pháp hoặc quên dòng kết thúc; hãy kiểm tra `META-INF/MANIFEST.MF` sau khi build.

### 3.3. Ai thực thi sealing?

**Sealing theo JAR manifest là hành vi của cơ chế loader/packaging**, điển hình `URLClassLoader` khi mở JARs và xử lý package metadata. `ClassLoader#defineClass` ở mức thấp **không tự đọc manifest hay tự xác định JAR seal base** chỉ từ một mảng `byte[]`.

Nếu bạn tự viết custom loader đọc bytes rồi gọi `defineClass`, cần quyết định cách:

1. Đọc manifest/package attributes.
2. Định nghĩa package metadata bằng `definePackage` khi cần.
3. So sánh nguồn class tiếp theo với seal base.
4. Kiểm tra signer certificates/`CodeSource` đúng quy tắc.

Dựa vào `defineClass` trần rồi tuyên bố “đã thực thi JAR sealing” là sai. Ngược lại, **không nên bỏ qua package metadata khi viết custom loader production** có nhiệm vụ tương đương loader JAR chuẩn.

## 4. Demo Java 17+: sealing, package cấm và class loading đúng/sai

Bài tập tạo hai JAR cùng package `sample.api` nhưng mỗi JAR chứa class **khác tên**, một class thử nghiệm thuộc `java.demo` và một chương trình chính nạp qua loader riêng.

Cấu trúc nguồn/output:

```text
package-security-demo/
├── src/
│   ├── one/sample/api/First.java
│   ├── two/sample/api/Second.java
│   ├── java/demo/Forbidden.java
│   └── app/PackageFailures.java
├── sealed.mf
├── out/
│   ├── one/
│   ├── two/
│   ├── forbidden/
│   └── app/
└── dist/
```

### 4.1. Class ở JAR thứ nhất

Tệp **`src/one/sample/api/First.java`**:

```java
package sample.api;

public class First {
    public static String name() {
        return "first";
    }
}
```

### 4.2. Class ở JAR thứ hai

Tệp **`src/two/sample/api/Second.java`**:

```java
package sample.api;

public class Second {
    public static String name() {
        return "second";
    }
}
```

Hai class thuộc **cùng package name `sample.api`** nhưng **khác binary name**. Điều này làm rõ sealing/signing failure thay vì một lỗi duplicate definition của đúng cùng class.

### 4.3. Class thử nghiệm ở namespace cấm

Tệp **`src/java/demo/Forbidden.java`**:

```java
package java.demo;

public class Forbidden {
}
```

**Chỉ dùng làm tình huống học tập.** Code ứng dụng thực không được đặt dưới namespace `java.*`. Trong bài thử này, `javac --release 17` có thể tạo file `java/demo/Forbidden.class` trên JDK được sử dụng; việc biên dịch không cấp quyền để **application-defined loader định nghĩa runtime class** mang tên ấy.

### 4.4. Chương trình chạy thử

Tệp **`src/app/PackageFailures.java`**:

```java
package app;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PackageFailures {
    static final class BytesLoader extends ClassLoader {
        BytesLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader parent =
                PackageFailures.class.getClassLoader();

        URL[] urls = {
            Path.of(args[0]).toUri().toURL(),
            Path.of(args[1]).toUri().toURL()
        };

        try (URLClassLoader loader =
                new URLClassLoader(urls, parent)) {
            Class<?> first = loader.loadClass(
                    "sample.api.First");

            System.out.println("First loader: "
                    + (first.getClassLoader() == loader));

            try {
                loader.loadClass("sample.api.Second");
                System.out.println("Second: loaded");
            } catch (SecurityException ex) {
                System.out.println("Second: SecurityException");
            }
        }

        BytesLoader own = new BytesLoader(parent);
        byte[] bytes = Files.readAllBytes(Path.of(args[2]));

        try {
            own.define("java.demo.Forbidden", bytes);
            System.out.println("Forbidden: loaded");
        } catch (SecurityException ex) {
            System.out.println("Forbidden: SecurityException");
        }
    }
}
```

**Vai trò hai loader:**

- `URLClassLoader` kiểm tra JAR/package metadata khi tìm classes từ các URLs.
- `BytesLoader` cố ý gọi `defineClass` trực tiếp để chứng minh **namespace `java.*` vẫn bị kiểm tra**, dù không có JAR manifest hay SecurityManager.
- `First` được load **trước `Second`** để xác lập trạng thái runtime package, rồi tình huống thứ hai bộc lộ sealing hoặc signer mismatch.

Trong thực tế phải kiểm tra `args.length` và báo usage khi thiếu tham số; ví dụ được chạy với chính xác ba arguments bên dưới. Catch `SecurityException` ở đây phục vụ **đo lường lỗi**. **Không nên nuốt lỗi trong plugin manager production** rồi coi plugin đã tải thành công.

### 4.5. Manifest sealing

Tệp **`sealed.mf`**:

```text
Manifest-Version: 1.0
Sealed: true

```

Đây là manifest nhập cho công cụ `jar`. Dòng trống cuối phân định hoàn tất main section.

### 4.6. Lệnh biên dịch và đóng JAR (Bash)

Từ thư mục `package-security-demo`, dùng JDK 17+:

```bash
mkdir -p out/one out/two out/forbidden out/app dist

javac --release 17 -d out/one src/one/sample/api/First.java
javac --release 17 -d out/two src/two/sample/api/Second.java
javac --release 17 -d out/forbidden src/java/demo/Forbidden.java
javac --release 17 -d out/app src/app/PackageFailures.java

# JAR A có package sealed.
jar --create --file dist/one-sealed.jar \
  --manifest sealed.mf -C out/one .

# JAR A không có sealing (JAR dành cho đối chiếu).
jar --create --file dist/one-open.jar -C out/one .

# JAR B không có sealing.
jar --create --file dist/two-open.jar -C out/two .

# Trường hợp 1: sealed A + B chứa cùng package.
java -cp out/app app.PackageFailures \
  dist/one-sealed.jar dist/two-open.jar \
  out/forbidden/java/demo/Forbidden.class

# Trường hợp 2: cả A và B đều không sealed.
java -cp out/app app.PackageFailures \
  dist/one-open.jar dist/two-open.jar \
  out/forbidden/java/demo/Forbidden.class
```

### 4.7. Lệnh tương đương trong PowerShell

Từ cùng thư mục, với Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path out/one, out/two, out/forbidden, out/app, dist | Out-Null

javac --release 17 -d out/one src/one/sample/api/First.java
javac --release 17 -d out/two src/two/sample/api/Second.java
javac --release 17 -d out/forbidden src/java/demo/Forbidden.java
javac --release 17 -d out/app src/app/PackageFailures.java

jar --create --file dist/one-sealed.jar --manifest sealed.mf -C out/one .
jar --create --file dist/one-open.jar -C out/one .
jar --create --file dist/two-open.jar -C out/two .

java -cp out/app app.PackageFailures dist/one-sealed.jar dist/two-open.jar out/forbidden/java/demo/Forbidden.class
java -cp out/app app.PackageFailures dist/one-open.jar dist/two-open.jar out/forbidden/java/demo/Forbidden.class
```

Các lệnh có thể dùng cùng JDK 17+ khi `java`, `javac` và `jar` nằm trên PATH.

### 4.8. Đầu ra được kiểm chứng

**Trường hợp 1 — A sealed, B unsealed:**

```text
First loader: true
Second: SecurityException
Forbidden: SecurityException
```

**Trường hợp 2 — A và B đều unsealed:**

```text
First loader: true
Second: loaded
Forbidden: SecurityException
```

Ví dụ đã được biên dịch với **`javac --release 17`**, đóng JAR bằng `jar` và chạy thành công trên **OpenJDK 24**. Các chuỗi ở trên là stdout của chính mã thử nghiệm, không phải nội dung stack trace của `SecurityException`.

**Kết luận từ đối chứng:**

1. Hai JAR unsealed có thể cung cấp hai classes khác tên trong cùng package, cùng defining loader, khi các điều kiện còn lại thỏa.
2. Seal package từ JAR A khiến class cùng runtime package từ nguồn B bị từ chối.
3. Dù sealing được bật hay không, custom `BytesLoader` vẫn không được định nghĩa tên `java.demo.Forbidden`.
4. `SecurityException` xuất hiện **không có nghĩa SecurityManager được bật**.
5. Bỏ sealing không sửa lỗi package `java.*`, vì đó là **một kiểm tra độc lập**.

## 5. Vì sao thứ tự nạp class có thể làm thông điệp khác đi?

Với `URLClassLoader`, package metadata được kiểm tra khi loader định nghĩa class trong package.

### Tình huống A — package bắt đầu sealed

```text
load sample.api.First từ sealed a.jar
   |
   +--> package sample.api: sealed với a.jar
   |
load sample.api.Second từ b.jar
   |
   +--> vi phạm seal base -> SecurityException
```

### Tình huống B — package bắt đầu unsealed, JAR sau muốn seal

```text
load sample.api.Second từ unsealed b.jar
   |
   +--> package sample.api: không sealed
   |
load sample.api.First từ sealed a.jar
   |
   +--> không được “seal ngược” package đã được định nghĩa khác policy
   |
   +--> SecurityException
```

Cả hai đều là sealing violations nhưng message khác. Không sửa bằng cách xáo thứ tự JAR “cho chạy” mà chưa hiểu contract: đổi thứ tự chỉ có thể đổi **lớp nào thắng** hoặc điểm kiểm tra bị lỗi.

Một vấn đề khác là **duplicate class**: nếu hai JAR đều chứa `sample/api/First.class` và chỉ dùng cùng một loader, loader có thể chọn bản đầu tiên theo lookup policy. Điều đó **không phải** lỗi sealing chỉ vì duplicate file; cần phân biệt với những classes **khác nhau trong cùng sealed package**.

## 6. Certificate/signer mismatch là lỗi độc lập với sealing

Ngoài package sealing, **`ClassLoader#defineClass`** kiểm tra tính nhất quán của certificate set trong package của **một defining loader**.

Theo Java SE 17 JavaDoc:

- **Class đầu tiên** được loader định nghĩa trong một package quyết định **tập certificates của package đó**.
- Các classes tiếp theo do **cùng loader** định nghĩa trong cùng package cần có **cùng tập certificates**.
- Tập certificates lấy từ **`CodeSource` của `ProtectionDomain`** gắn với class.
- Nếu tập certificates không khớp, `defineClass` có thể ném **`SecurityException`**.
- Khi overload `defineClass` nhận `name = null`, một số kiểm tra theo tên không được thực hiện theo JavaDoc; **không nên dùng `null` làm thủ thuật né kiểm tra**. Hãy cung cấp đúng binary name khi định nghĩa class.

Ví dụ:

```text
loader L:
  sample.api.First  từ one-signed.jar    [certificate set S]
  sample.api.Second từ two-open.jar      [unsigned/empty set]
         |
         v
      SecurityException
      vì signer information không nhất quán
```

Điểm quan trọng: **Một package có thể không sealed mà vẫn vi phạm signer consistency**. Sealing liên quan **nguồn JAR/seal base**; signer mismatch liên quan **tập chứng thư người ký**. Hai kiểm tra không phải một.

### Signer certificate không phải “mức độ tin cậy toàn cục”

Certificate set dùng cho package consistency là thông tin người ký gắn với code source. Nó không có nghĩa:

- JVM tự xác nhận mọi certificate đều được một CA công khai đáng tin cậy cấp.
- Hai JAR cùng certificate signer đã được tổ chức bạn cho phép chạy.
- Chữ ký JAR thay thế việc kiểm tra dependency provenance, vulnerability và update pipeline.
- Signed JAR tự động bảo vệ plugin trước mọi hành vi độc hại.

Một JAR được ký bằng **self-signed certificate để thử nghiệm** có thể đi qua bước xử lý người ký, nhưng không nên coi đó là bằng chứng trust trong production.

### Signed và unsigned có thể trộn trong cùng package không?

Với **cùng defining loader**, nếu một class đầu có non-empty signer certificates còn class tiếp theo trong cùng runtime package là unsigned, các sets khác nhau, nên có thể gặp `SecurityException`.

Nếu hai plugin được định nghĩa bởi **hai class loaders riêng biệt** và **không share chính runtime package**, kiểm tra giữa classes từ hai defining loaders không phải cùng package của loader. Tuy vậy những objects/types do hai loader tạo cũng không tự tương thích. **Tách loader để tránh lỗi** chỉ đúng khi phù hợp kiến trúc class identity và API boundary; không thay cho signing policy đúng.

## 7. Thực hành tùy chọn: tái hiện signer mismatch với JDK tools

Phần 4 đã tạo `dist/one-open.jar` và `dist/two-open.jar` — cả hai đều **không sealed**. Ta ký **chỉ** JAR đầu bằng một certificate thử nghiệm, để tách nguyên nhân signer khỏi sealing.

Từ thư mục bài thực hành:

```bash
# CHỈ DÙNG CHO THỬ NGHIỆM CỤC BỘ.
# "changeit" là mật khẩu demo công khai, tuyệt đối không dùng
# cho khóa ký thực tế hoặc môi trường production.
keytool -genkeypair \
  -alias lesson \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore demo-test.p12 \
  -storepass changeit \
  -keypass changeit \
  -dname "CN=Lesson Test" \
  -validity 2 \
  -noprompt

# Ký JAR thứ nhất, giữ bản JAR thứ hai unsigned.
jarsigner \
  -keystore demo-test.p12 \
  -storepass changeit \
  -keypass changeit \
  -signedjar dist/one-signed.jar \
  dist/one-open.jar lesson

# Xác nhận JAR có signature metadata.
jarsigner -verify dist/one-signed.jar

# Nạp class đầu từ signed JAR, class sau từ unsigned JAR.
java -cp out/app app.PackageFailures \
  dist/one-signed.jar dist/two-open.jar \
  out/forbidden/java/demo/Forbidden.class
```

Trên **PowerShell**, viết các lệnh dài trên một dòng hoặc dùng ký hiệu tiếp dòng hợp lệ của PowerShell, không dùng dấu `\` cuối dòng kiểu Bash:

```powershell
keytool -genkeypair -alias lesson -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore demo-test.p12 -storepass changeit -keypass changeit -dname "CN=Lesson Test" -validity 2 -noprompt

jarsigner -keystore demo-test.p12 -storepass changeit -keypass changeit -signedjar dist/one-signed.jar dist/one-open.jar lesson

jarsigner -verify dist/one-signed.jar

java -cp out/app app.PackageFailures dist/one-signed.jar dist/two-open.jar out/forbidden/java/demo/Forbidden.class
```

**Đầu ra của chương trình** được kiểm chứng trên JDK 24:

```text
First loader: true
Second: SecurityException
Forbidden: SecurityException
```

Ở lần này:

- `sample.api.First` lấy từ **JAR đã ký**, nhưng **không sealed**.
- `sample.api.Second` lấy từ **JAR chưa ký**, cùng runtime package của loader.
- Việc nạp class thứ hai ném `SecurityException` vì **signer information khác nhau**, không phải do sealing.
- Lỗi `Forbidden` vẫn độc lập và được tái hiện lần thứ ba.

**Cảnh báo về `jarsigner -verify`:** Với self-signed test certificate, công cụ có thể in `jar verified` **kèm warnings** về chuỗi trust/PKIX, certificate gần hết hạn và không có trusted timestamp. Đây là hành vi hợp lý trong bài test, **không được dùng ký tự “jar verified” riêng lẻ để chứng nhận độ tin cậy của phần mềm**.

Không commit, xuất bản hoặc tái sử dụng keystore test với password `changeit`. Production signing phải có key management, trust policy, signature verification và secret handling riêng.

### Nếu muốn thấy nguyên nhân chi tiết

Trong demo, thay tạm catch:

```java
} catch (SecurityException ex) {
    System.out.println("Second: SecurityException");
}
```

bằng:

```java
} catch (SecurityException ex) {
    ex.printStackTrace();
}
```

Đây là **đoạn thay thế minh họa** trong method của demo, không phải chương trình độc lập. Khi chạy, stack trace sẽ giúp phân biệt sealing verification ở `URLClassLoader` với signer check ở `ClassLoader#defineClass`. Không hard-code đầy đủ stack trace vào lesson vì phiên bản JDK/vendor khác nhau.

## 8. Package bị cấm java.*: không liên quan sealing hay chứng thư

Giới hạn thứ ba xuất hiện tại `ClassLoader#defineClass`. Với ordinary application/custom loader, tên như:

```text
java.demo.Forbidden
java.lang.MyString
java.util.CustomCollections
```

đều bắt đầu bằng `java.`, nên không được tự định nghĩa.

Java SE 17 JavaDoc phát biểu cụ thể: class có tên bắt đầu `java.` chỉ được **platform class loader hoặc các loader tổ tiên của nó** định nghĩa. Điều này không đồng nghĩa “bất kỳ loader nào có parent là platform loader đều được phép”; một loader do ứng dụng tạo là **child**, không phải platform/ancestor.

### loadClass được phép khác defineClass bị cấm

```java
ClassLoader pluginLoader = new ClassLoader(
        ClassLoader.getSystemClassLoader()) {
};

Class<?> core = pluginLoader.loadClass("java.lang.String");
System.out.println(core == String.class);
```

Đây là một đoạn minh họa hợp lệ bên trong method, **không có `main` riêng**. `loadClass` mặc định ủy quyền parent để nhận `String` do runtime định nghĩa, nên phép so sánh thường là `true` trên JVM tiêu chuẩn.

Ngược lại, `BytesLoader` trong demo gọi:

```java
own.define("java.demo.Forbidden", bytes);
```

và bị `SecurityException` vì muốn **loader riêng tự định nghĩa một class mới trong namespace `java.*`**.

**Lưu ý:** Compile ra file `java/demo/Forbidden.class` không đồng nghĩa JVM cho phép application loader nạp class ấy; compile-time và runtime class definition có những kiểm tra khác nhau.

### Không nên “sửa” bằng cách đổi thành javax.* hoặc jdk.*

Quy tắc `java.*` ở `defineClass` là một ràng buộc được công bố cụ thể. Nhưng tên `javax.*`, `jdk.*`, `sun.*` cũng thường được gắn với platform/JDK/framework và có thể chịu module/package access hoặc các restriction khác. **Không chiếm dụng namespace của hệ sinh thái** chỉ để vượt một check.

Hãy dùng package riêng, ví dụ:

```java
package com.example.plugin;
```

Đây là package declaration minh họa. Đặt tên package theo miền/đơn vị sở hữu code, tránh xung đột với platform.

## 9. Sự liên quan của defining loader, parent delegation và runtime package

Từ góc nhìn JVM, một runtime package thông thường được xác định bởi:

```text
(package name, defining class loader)
```

Ví dụ:

```text
Loader A defines sample.api.First
Loader B defines sample.api.Second
    |
    +--> cùng package name "sample.api"
    +--> KHÁC defining loader
    +--> KHÁC runtime package
```

Hệ quả:

- Hai classes không tự có cùng **package-private access**, dù source code ghi cùng `package sample.api;`.
- Một package được sealed trong URLClassLoader A không tự làm package cùng tên của loader B thành cùng sealed package.
- Package signer consistency được áp dụng trong ngữ cảnh định nghĩa class của loader; không có nghĩa signer của mọi class cùng tên package **trên toàn JVM** phải giống nhau.
- Nếu host truyền một class private API từ loader A sang loader B, có thể nảy sinh `ClassCastException`, `IllegalAccessError` hoặc `LinkageError` tùy tình huống.

**Không dùng per-plugin loader làm mẹo duy nhất để giấu sealing/signature problems.** Nó có thể làm giảm xung đột package giữa plugin versions khi đúng kiến trúc, nhưng cũng tạo namespace/type boundaries phải quản lý cẩn thận.

### Trường hợp parent thắng child

Nếu parent loader đã định nghĩa `sample.api.First`, một child `URLClassLoader` parent-first có thể trả class từ parent thay vì tự định nghĩa bản trong plugin JAR. Khi đó kiểm tra package/sealing/signers của child liên quan đến **những classes child thực sự định nghĩa**, không phải tất cả class names được gọi trên child qua `loadClass`.

Hãy kiểm tra:

```java
System.out.println(type.getClassLoader());
System.out.println(type.getModule());
```

Đây là snippet có biến `Class<?> type` trong method, không phải chương trình riêng. Đừng suy luận defining loader chỉ từ biến đã gọi `loader.loadClass(...)`.

## 10. Điều gì xảy ra với custom ClassLoader tự viết?

Một loader tự đọc bytes:

```java
protected Class<?> findClass(String name)
        throws ClassNotFoundException {
    byte[] data = loadBytesFromTrustedSource(name);
    return defineClass(name, data, 0, data.length);
}
```

Đây là **mẫu pseudocode Java**, vì `loadBytesFromTrustedSource` chưa được định nghĩa.

Từ góc nhìn security/package rules:

- `defineClass` vẫn có **các kiểm tra như tên `java.*` và signer certificate set**.
- Nếu truyền `ProtectionDomain`, `CodeSource` mang certificate data thích hợp theo contract; không tự giả mạo/đổi certificates để “cho chạy”.
- `defineClass` không tự biết **manifest sealing** của mọi nguồn bytes nếu loader không cung cấp package metadata/verification tương ứng.
- Không định nghĩa lại cùng binary name trong cùng loader; tình huống đó là **duplicate definition/`LinkageError`** khác.
- Nếu bytecode không đúng định dạng, có thể có `ClassFormatError` hoặc `VerifyError`; đừng biến mọi lỗi thành `SecurityException` hoặc `ClassNotFoundException`.
- Nếu name truyền vào khác class name trong bytes, có thể có `NoClassDefFoundError`; đó không phải package sealing.

Với ứng dụng cần JAR loading chuẩn, **`URLClassLoader`** là lựa chọn thực tiễn hơn việc tự xây toàn bộ package verification. Với hệ sinh thái plugin lớn, có thể dùng module/plugin manager sẵn có và thiết kế package namespace rõ ràng.

## 11. Chẩn đoán thực tế: từ exception tới nguyên nhân gốc

Khi gặp `SecurityException` trong class loading, một quy trình hiệu quả:

1. **Đọc stack trace đầy đủ.** Lỗi xuất hiện khi `URLClassLoader` kiểm tra sealing, hay ở `ClassLoader#defineClass` kiểm tra certificates/namespace?
2. **Xác định binary name thật sự.** Có bắt đầu bằng `java.` không? Có thuộc package đã có class trước đó không?
3. **Xác định defining loader.** Đừng đoán từ tên class hoặc JAR path. Ghi class loader object identity của những class nạp thành công.
4. **Kiểm tra thứ tự load.** Class nào trong package đã được định nghĩa trước, từ JAR nào?
5. **Kiểm tra manifest của từng JAR.** Có `Sealed: true` ở main hoặc package-specific section không?
6. **Kiểm tra JAR signing.** JAR đầu signed nhưng JAR sau unsigned? Có certificate sets khác nhau không? Có signature verification error không?
7. **Phân biệt tài nguyên/JAR trùng tên.** Hai JAR có cùng package nhưng khác class name khác với duplicate binary class name.
8. **Kiểm tra classpath/module path thực tế.** Loader nào thấy những JAR nào? Parent-first/child-first có tác động không?
9. **Không bật SecurityManager để sửa.** Lỗi sealing/signing/package reservation vẫn có thể xảy ra khi manager vắng mặt.
10. **Sửa nguồn gốc.** Đồng bộ package/JAR boundaries, signing policy hoặc namespace; không catch rồi bỏ qua lỗi.

### Kiểm tra manifest bằng công cụ JDK

```bash
jar --list --file dist/one-sealed.jar
jar --list --file dist/two-open.jar
```

`jar --list` xác nhận class entries và manifest entry, nhưng **không tự in nội dung manifest**. Để xem `META-INF/MANIFEST.MF`, dùng archive viewer hoặc giải nén **vào một thư mục tạm riêng**:

```bash
mkdir -p manifest-inspect
cd manifest-inspect
jar --extract --file ../dist/one-sealed.jar META-INF/MANIFEST.MF
```

Đây là lệnh ví dụ Bash trong thư mục bài thử; vị trí tương đối `../dist/...` giả định `manifest-inspect` nằm cạnh `dist`. Sau khi mở file text, kiểm tra `Sealed` và các package sections.

### Kiểm tra signing

```bash
jarsigner -verify -verbose -certs dist/one-signed.jar
```

Lệnh có thể cho biết ký số và cảnh báo về certificate chain/timestamp. **Một JAR verified về tính toàn vẹn không tự đồng nghĩa mọi signer được tin cậy**; kiểm tra tin cậy phải dựa vào trust store/PKI policy thực tế.

Nếu cần inspect class metadata khi đã load thành công:

```java
Class<?> type = loader.loadClass("sample.api.First");
System.out.println("Class: " + type.getName());
System.out.println("Defining loader: " + type.getClassLoader());

Package pkg = type.getPackage();
System.out.println("Package sealed: "
        + (pkg != null && pkg.isSealed()));

var pd = type.getProtectionDomain();
var source = pd == null ? null : pd.getCodeSource();
System.out.println("Code source: "
        + (source == null ? "(unknown)" : source.getLocation()));
```

Đây là snippet minh họa trong method có biến `loader`. `Package.isSealed()` cho biết package metadata; `CodeSource.getLocation()` là thông tin nguồn code khi có sẵn, **không phải chứng nhận tin cậy**. Cũng có thể có `null` tùy class/runtime.

Với certificates, `CodeSource.getCertificates()` có thể trả mảng hoặc `null`. Không dùng `toString()`, thứ tự URL hay certificate count đơn thuần làm tiêu chí xác thực an ninh.

## 12. Cách sửa đúng theo từng loại lỗi

### A. Sealing violation

**Sửa tốt:** Đặt tất cả classes thuộc sealed package vào JAR/seal base thống nhất theo hợp đồng; hoặc xem lại việc có thật cần sealing không. Một project cố tình chia package `sample.api` qua nhiều JAR nên **không** seal package ấy bằng một JAR rồi nạp phần còn lại từ JAR khác qua cùng loader.

Các lựa chọn tùy kiến trúc:

- Gom các classes trong cùng sealed package về một JAR nhất quán.
- Tách packages theo artifact/module/plugin ownership để tránh split package.
- Bỏ/đổi `Sealed` **chỉ khi đáp ứng yêu cầu đóng gói và bảo mật**, không làm để che một deployment sai.
- Với plugin độc lập, dùng loader riêng nếu type boundaries và shared API đã được thiết kế rõ.
- Đặt build validation kiểm tra manifest và package split trước triển khai.

**Sai lầm:** Xóa một exception catch hoặc đảo thứ tự JAR mà không điều chỉnh cấu trúc package/source.

### B. Signer certificate mismatch

**Sửa tốt:** Dùng chính sách ký JAR thống nhất cho các classes cùng runtime package/defining loader. Ví dụ nếu cần signed packages, bảo đảm những classes cùng package có **cùng signer certificate set phù hợp**, và tách unsigned dependencies sang package khác khi thích hợp.

Có thể:

- Thống nhất signing key/certificate pipeline hợp lệ cho packages chung.
- Kiểm tra JAR sau build đã bị repack/shade làm signature metadata mất hiệu lực chưa.
- Không trộn unsigned and signed entries dưới cùng runtime package của một loader.
- Tránh trùng package giữa third-party libraries có provenance/signing khác nhau.
- Kiểm tra signer trust, expiration, timestamp/chain validation **ngoài** yêu cầu consistency để bảo đảm security thật.

**Sai lầm:** Đặt một self-signed certificate mới cho từng JAR rồi nghĩ “cả hai signed là đủ”. Hai certificate sets khác nhau vẫn có thể vi phạm package consistency.

### C. Package `java.*` bị cấm

**Sửa tốt:** Đặt class ứng dụng vào namespace sở hữu bởi bạn, ví dụ `com.example.plugin`, và **để platform loaders cung cấp classes chuẩn**.

- Không tự định nghĩa `java.lang.String` hoặc `java.foo.Helper` từ ordinary application loader.
- Không hy vọng đổi parent loader thành platform loader là đã có quyền định nghĩa tên `java.*`.
- Không dùng flags `--add-opens`/`--add-exports` để “cấp quyền” cho `defineClass` trong namespace `java.*`; các flags này nói về module access.
- Kiểm tra bytecode generators/shading/relocation đã đổi binary name sai chưa.

**Sai lầm:** Tự override `loadClass` child-first và cho rằng điều đó vô hiệu hóa `defineClass` security checks.

## 13. Phân biệt với những lỗi khác

| Lỗi | Điểm khác với ba nguyên nhân SecurityException |
|---|---|
| `ClassNotFoundException` | Loader được yêu cầu không tìm được class theo lookup contract |
| `NoClassDefFoundError` | JVM cần một class mà không giải quyết được (hoặc class đã khởi tạo thất bại); cũng có thể do tên class-file không khớp |
| `ClassFormatError` | Bytes không đúng cấu trúc class file |
| `VerifyError` | Bytecode không đáp ứng quy tắc verifier |
| `UnsupportedClassVersionError` | Class file có version mới hơn runtime hỗ trợ |
| `LinkageError` | Có thể do duplicate class definition hoặc loading constraints; không nhất thiết sealing |
| `IllegalAccessError` | Quy tắc JVM access/package/module không cho phép truy cập member/type |
| `ClassCastException` | Runtime types không tương thích, thường gặp ở loader boundary |
| `InaccessibleObjectException` | Deep reflection bị giới hạn theo JPMS/open package, không phải JAR sealing |
| `JarException` / `ZipException` | Lỗi archive/ZIP/JAR data, khác signer consistency theo package |

Một cùng stack trace có thể chứa nhiều lớp exception: đọc **root cause** và diễn biến loader. Không “fix” `SecurityException` bằng cách chỉ thêm dependency vào classpath, và không “fix” `ClassNotFoundException` bằng cách bỏ sealing khi nguồn class chưa được tìm thấy.

## 14. SecurityManager có liên quan không?

Theo **JEP 411**, `SecurityManager` được **deprecated for removal** từ JDK 17; theo **JEP 486**, nó **permanently disabled** từ JDK 24. Những quy tắc package namespace, JAR sealing và certificate consistency vẫn là cơ chế riêng của class loading/packaging.

Vì vậy:

- Một `SecurityException` trong demo được tái hiện trên **JDK 24**, nơi `SecurityManager` không thể được bật theo mô hình cũ.
- `ProtectionDomain` và `CodeSource` vẫn có vai trò metadata/signers nhất định, nhưng không nên suy luận chúng tự bật legacy permission sandbox.
- Package sealing và signer checking **không bảo đảm plugin đáng tin hoặc không đọc file/network**.
- `ClassLoader` riêng giúp runtime type/dependency isolation, **không phải process/OS sandbox**.
- Với code plugin không đáng tin, cần process/container/OS permissions, resource limits và IPC contracts riêng.

Điều kiện security trong class loading **khác** bài toán authorization nghiệp vụ và giới hạn quyền OS.

## 15. Checklist kiểm thử dành cho plugin/JAR build

Để tránh phát hiện lỗi lúc production:

- [ ] Mỗi module/plugin có package namespaces thuộc sở hữu rõ ràng.
- [ ] Không sinh hoặc relocate application classes vào `java.*`.
- [ ] Không có split sealed package qua nhiều JAR trong cùng loader.
- [ ] Manifest `Sealed` toàn JAR và theo package đã được kiểm tra sau build.
- [ ] Signer certificate set của classes cùng runtime package thống nhất theo policy.
- [ ] Không vô tình trộn unsigned plugin classes với signed package của vendor.
- [ ] JAR signing verification/trust checks được thực hiện riêng, không chỉ nhìn signer count.
- [ ] Parent delegation và plugin-private class isolation được xác minh.
- [ ] Có test với nhiều JAR cùng package nhưng khác class names.
- [ ] Có test signer mismatch (trong môi trường thử nghiệm riêng) nếu ứng dụng dùng signed JARs.
- [ ] Có log defining loader và code source khi class load thất bại.
- [ ] Không catch `SecurityException` rồi âm thầm fallback sang class có cùng tên từ nguồn thiếu tin cậy.
- [ ] Đã thử trên JDK/version/module path thực tế.
- [ ] Có kế hoạch cho package migration, không chỉ đổi URL classpath order.

## 16. Câu hỏi tự kiểm tra

**1. Hai JAR không sealed cùng chứa các class khác tên trong package `sample.api`. Có tự động ném SecurityException không?** Không; nếu cùng loader, việc dùng chung package không tự gây sealing violation. Tuy nhiên signer certificates hoặc constraints khác có thể vẫn gây lỗi.

**2. `Sealed: true` ở main section có nghĩa gì?** Mặc định đánh dấu các packages trong JAR là sealed, trừ các package-level overrides.

**3. Có thể chỉ seal `sample.api` không?** Có, dùng individual manifest section với `Name: sample/api/` và `Sealed: true`.

**4. `URLClassLoader` nạp class đầu từ sealed JAR A rồi class sau cùng package từ JAR B. Lỗi chính là gì?** Vi phạm seal base/source consistency, thường là `SecurityException`.

**5. Signed JAR A và unsigned JAR B, cùng loader/cùng package, cả hai unsealed. Có thể ném SecurityException không?** Có, do certificate/signer set không nhất quán.

**6. Nếu hai JAR đều signed thì chắc chắn không mismatch?** Không. Các classes cùng runtime package cần certificate set tương thích/giống nhau theo kiểm tra của loader, không chỉ đều có chữ ký.

**7. `java.demo.Forbidden` không có trong JDK tiêu chuẩn, custom loader có tự định nghĩa được không?** Không; tên bắt đầu `java.` bị giới hạn theo `defineClass`.

**8. Vì sao `child.loadClass("java.lang.String")` thường được phép?** Child ủy quyền để nhận class do loader nền tảng phù hợp định nghĩa, không tự tạo class cùng tên.

**9. Lỗi SecurityException từ sealing có chứng minh SecurityManager đang bật không?** Không. Đây là kiểm tra classloader/JAR độc lập.

**10. Tách hai plugin vào loader riêng có làm classes cùng package trở thành cùng runtime package không?** Không. Runtime package còn phụ thuộc defining loader.

**11. `CodeSource.getLocation()` là bằng chứng JAR đáng tin không?** Không. Nó là metadata nguồn; trust/integrity cần được kiểm tra riêng.

**12. Nếu dùng raw `defineClass` với bytes đọc từ JAR, sealing tự có hiệu lực không?** Không thể mặc định; custom loader phải xử lý package manifest/sealing policy phù hợp.

## 17. Tài liệu tham khảo chính thức

- **Java SE 17 — `ClassLoader#defineClass`:** các kiểm tra `SecurityException` khi certificates không đồng nhất hoặc tên bắt đầu `java.`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Java SE 17 — JAR File Specification, Package Sealing:** main/per-entry `Sealed` attributes và quy tắc cùng JAR: https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html
- **Java SE 17 — `URLClassLoader`:** nạp class từ URL/JAR và package metadata: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- **Java SE 17 — `Package.isSealed`:** metadata của package: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Package.html
- **Java SE 17 — `CodeSource` / `ProtectionDomain`:** nguồn code, certificates và domain: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/CodeSource.html
- **Java SE 17 — Công cụ `jarsigner`:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/jarsigner.html
- **JEP 411 và JEP 486:** https://openjdk.org/jeps/411 và https://openjdk.org/jeps/486

**Ghi nhớ:** Gặp `SecurityException` trong class loading, hãy hỏi **lỗi do manifest sealing, do signer certificates, hay do namespace `java.*`?** Sealing và signer consistency thường liên quan classes **cùng package do cùng loader định nghĩa**; reserved package `java.*` là hạn chế riêng tại `defineClass`. Sửa đúng nguồn gốc và kiểm tra classloader, manifest, signing policy — không dùng SecurityManager hoặc bắt bỏ exception để che lỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-class-loading-errors-012">UnsatisfiedLinkError và native library trong nhiều loader</a>

<details>
<summary>Xem chi tiết</summary>

# UnsatisfiedLinkError và native library trong nhiều ClassLoader

## 1. Vấn đề: Java class có thể cô lập, nhưng JNI library không được nạp tùy ý vào nhiều loader

Trong hệ thống plugin, một kiến trúc phổ biến là mỗi plugin sở hữu một `URLClassLoader` để tải class implementation và dependency của riêng mình. Các class có cùng *binary name* vẫn có thể tồn tại đồng thời nếu được **hai defining class loaders khác nhau** định nghĩa. Nhưng nếu mỗi plugin đều thực hiện `System.loadLibrary("nativecodec")` hoặc `System.load("/absolute/path/libnativecodec.so")` với **cùng một JNI native library**, lần nạp sau vào loader khác có thể thất bại với:

~~~text
java.lang.UnsatisfiedLinkError: Native Library ... already loaded in another classloader
~~~

Đây không nhất thiết là lỗi thiếu file. Trong trường hợp điển hình, **library đã tìm được và được nạp thành công**, nhưng đã được gắn với một class loader khác.

Đặc tả JNI Java 17 nêu rõ: **mỗi class loader quản lý tập native libraries riêng; cùng một JNI native library không thể được nạp vào nhiều class loaders**. Khi cố làm vậy, JVM ném `UnsatisfiedLinkError`. Quy tắc này nhằm bảo vệ *class-loader namespace separation*, tránh native code trộn các class có cùng tên nhưng khác runtime identity.

Vì vậy, câu hỏi đúng không chỉ là “đã cấu hình `java.library.path` chưa?” mà còn là:

1. **Thư viện native nào** được tải: tên logic, đường dẫn tuyệt đối, binary thực tế, dependency native?
2. **Java class nào** gọi `System.load`/`System.loadLibrary`? Nó thuộc **defining loader** nào?
3. **Library ấy đã được nạp vào loader khác chưa?** Vòng đời loader trước đã kết thúc thật sự chưa?
4. Lỗi xảy ra khi **tải thư viện native**, hay khi **liên kết lời gọi `native` với symbol**?
5. Plugin có thể dùng **một JNI bridge dùng chung ở parent loader** thay vì tải lại library ở mỗi child không?

Bài học phân biệt các nguyên nhân, thiết kế loader đúng và xây dựng ví dụ **Java 17 + C/JNI** có thể chạy để tái hiện chính xác việc nạp cùng một thư viện từ hai class loaders.

## 2. `UnsatisfiedLinkError` nằm ở đâu trong hệ thống lỗi Java?

Cấu trúc kế thừa:

~~~text
java.lang.Object
  └── java.lang.Throwable
      └── java.lang.Error
          └── java.lang.LinkageError
              └── java.lang.UnsatisfiedLinkError
~~~

`UnsatisfiedLinkError` là **`Error`**, không phải checked `Exception`. Nó có thể phát sinh khi JVM không liên kết được một native method declaration với implementation phù hợp, hoặc khi thao tác `System.load`/`System.loadLibrary` thất bại.

Ví dụ:

~~~java
public final class NativeCodec {
    static {
        System.loadLibrary("nativecodec");
    }

    public static native int encode(int value);
}
~~~

Đây là **mảnh code minh họa**, chưa chạy độc lập vì cần library native thực sự chứa implementation cho `NativeCodec.encode`.

Một trong nhiều kiểu lỗi có thể thấy:

~~~text
java.lang.UnsatisfiedLinkError: no nativecodec in java.library.path: ...
~~~

Nguyên nhân: thư viện không tìm thấy trong đường tìm đã cấu hình.

Một kiểu khác:

~~~text
java.lang.UnsatisfiedLinkError: Native Library ... already loaded in another classloader
~~~

Nguyên nhân: cùng JNI library đã gắn với loader khác.

Một kiểu nữa:

~~~text
java.lang.UnsatisfiedLinkError: 'int NativeCodec.encode(int)'
~~~

Đây là **dạng minh họa** cho trường hợp native method không có entry point/signature phù hợp; văn bản cụ thể của exception phụ thuộc JDK và signature.

Cả ba đều là `UnsatisfiedLinkError` nhưng cần **ba chiến lược điều tra khác nhau**. Không tự động giải quyết bằng cách thêm JAR, tăng quyền reflection hoặc đổi tên Java class.

## 3. Cơ chế JNI kết hợp với ClassLoader

Java cung cấp hai mô hình liên quan mà ta cần phân biệt.

**Java bytecode loading:**

~~~text
App class loader
  |
  +-- PluginLoader A -> (plugin.NativeBridge, Loader A)
  |
  +-- PluginLoader B -> (plugin.NativeBridge, Loader B)
~~~

Hai `Class<?>` mang tên `plugin.NativeBridge` là hai runtime types nếu mỗi child tự định nghĩa.

**JNI native library management:**

~~~text
PluginLoader A
      |
  System.load("/opt/native/libbridge.so")
      |
      v
JVM native-library registry / loader A
      |
      +--> libbridge.so  [loaded]

PluginLoader B
      |
  System.load("/opt/native/libbridge.so")
      |
      v
JVM phát hiện cùng JNI library thuộc loader A
      |
      +--> UnsatisfiedLinkError
~~~

**Quy tắc cơ bản:** việc một class Java được tải vào loader B **không biến native library đã gắn với A thành library của B**. JVM quản lý thông tin về native libraries, JNI entry points và classloader ownership theo đặc tả JNI.

### Vì sao giới hạn này cần thiết?

JNI nhận tham chiếu tới Java objects/classes và có thể cache `jclass`, `jmethodID`, `jfieldID`, global references, hoặc đăng ký native methods bằng `RegisterNatives`. Hai Java classes có **cùng binary name** nhưng khác defining loader **không phải cùng runtime type**.

Nếu một native binary bị gắn một cách tùy tiện với hai classloader namespaces, native code có thể nhầm metadata/type references và phá cô lập kiểu mà JVM muốn duy trì. Vì vậy JNI không coi native code đơn giản là “thư viện toàn tiến trình, class nào cũng dùng được” theo nghĩa classloader-independent.

**Đừng nhầm điều này với hành vi loader cha:** nếu cả hai plugin thực sự delegate tới **cùng Java JNI wrapper class ở parent**, thì method native được gọi qua **một runtime class duy nhất** và một native library được nạp ở parent. Đây là giải pháp kiến trúc quan trọng, giải thích ở phần sau.

## 4. Hai cách tải library: `System.load` và `System.loadLibrary`

| API | Đối số | Ví dụ | Điều cần lưu ý |
|---|---|---|---|
| `System.load(String absoluteFilename)` | **Đường dẫn tuyệt đối** tới native binary | `System.load("/opt/demo/libbridge.so")` | Không truyền đường dẫn tương đối |
| `System.loadLibrary(String libname)` | **Tên logic**, không có path, prefix hay extension | `System.loadLibrary("bridge")` | Dựa vào quy tắc tìm kiếm library của JVM/OS và `findLibrary` khi áp dụng |
| `System.mapLibraryName(String libname)` | Tên logic | `System.mapLibraryName("bridge")` | Trả tên file theo nền tảng, không tự tải thư viện |

Ví dụ tên được map:

~~~text
Linux:   bridge -> libbridge.so
macOS:   bridge -> libbridge.dylib
Windows: bridge -> bridge.dll
~~~

Đây là **tên điển hình**, còn naming conventions và hình thức binary có thể khác tùy nền tảng/implementation.

### `System.load`

~~~java
import java.nio.file.Path;

String absolute = Path.of("native", "libbridge.so")
        .toAbsolutePath().normalize().toString();

System.load(absolute);
~~~

Đây là **mảnh minh họa** cho Linux, yêu cầu file `libbridge.so` tồn tại và là native binary hợp lệ. Với macOS/Windows, file name khác. Thao tác này **không bỏ qua giới hạn một JNI library trên nhiều loaders**: đưa một absolute path hợp lệ cho cả hai child loaders vẫn có thể làm loader thứ hai thất bại.

### `System.loadLibrary`

~~~java
System.loadLibrary("bridge");
~~~

Không viết:

~~~java
System.loadLibrary("libbridge.so");   // Sai cách gọi API chuẩn
System.loadLibrary("/opt/native/libbridge.so");  // Sai: chứa path
~~~

Tùy hệ thống, JVM tra các vị trí thư viện native liên quan tới `java.library.path`, custom `ClassLoader.findLibrary` và hệ điều hành. Nếu library `bridge` không có ở nơi JVM tìm, sẽ phát sinh `UnsatisfiedLinkError` kiểu “no bridge in java.library.path”.

### `findLibrary` của custom loader

`ClassLoader` cung cấp hook:

~~~java
@Override
protected String findLibrary(String libname) {
    if ("bridge".equals(libname)) {
        return "/opt/my-plugin/native/libbridge.so";
    }
    return null;
}
~~~

Đây là **mảnh minh họa override trong subclass của ClassLoader**. Method phải trả một **absolute path** hợp lệ; trả `null` để dùng cơ chế tìm mặc định. Hook giúp loader tìm đúng binary, **không cho phép** nạp cùng JNI binary vào vô số loaders độc lập.

### Đừng coi `java.library.path` là “loader ownership”

`java.library.path` giải quyết **đường tìm native library**, không phải vấn đề cùng binary đã được đăng ký dưới loader khác. Đặt file đúng đường dẫn có thể sửa lỗi “no library in path” nhưng **không** sửa lỗi “already loaded in another classloader”.

Việc thay `System.setProperty("java.library.path", "...")` khi chương trình đã chạy **không có bảo đảm** thay được search path mà JVM đã chuẩn bị/ghi nhớ. Ưu tiên cấu hình ngay khi khởi chạy hoặc truyền absolute path qua `System.load` với cơ chế loader ownership rõ ràng.

## 5. Những nguyên nhân chính của UnsatisfiedLinkError

| Triệu chứng / thông báo điển hình | Nguyên nhân | Hướng xử lý |
|---|---|---|
| `no bridge in java.library.path` | Native binary không thuộc đường tìm | Kiểm tra startup flags, `findLibrary`, absolute path |
| `already loaded in another classloader` | Native binary đã được loader khác sở hữu | Một shared JNI bridge ở parent; thay lifecycle/loader policy |
| `Can't load ...` hoặc “wrong ELF class” | Binary sai kiến trúc/bitness hay OS | Dùng binary đúng OS/CPU/ABI |
| `... cannot open shared object file ...` | Native binary hoặc **dependency native cấp dưới** không tìm thấy | Kiểm tra loader của hệ điều hành và transitive native dependencies |
| `undefined symbol` / `symbol not found` | Export/symbol ABI không khớp | Kiểm tra JNI header, C exports, linker, overload |
| Native method không liên kết | Chưa export tên JNI đúng, chưa load library, sai signature | Dùng `javac -h`, `JNIEXPORT`, `JNICALL`, `RegisterNatives` nếu thiết kế |
| `JNI_OnLoad` trả JNI version không hỗ trợ | Startup hook báo version không được VM hỗ trợ | Trả JNI version đúng theo JNI spec |
| Chỉ lỗi khi redeploy plugin | Loader cũ vẫn sống và giữ library | Dừng threads, bỏ references, xem class unloading/native unload |
| Chỉ lỗi trên máy CI/server | OS, CPU, permissions, runtime image, loader paths khác | Thu environment và native dependency report |
| Chỉ lỗi khi plugin A và B cùng bật | Hai plugin loaders riêng cùng `System.load` library | Kiểm tra defining loader của wrapper và native registry |

**Lưu ý:** dạng wording và nested causes phụ thuộc nền tảng/JDK. Các dòng trên là những mẫu chẩn đoán, **không phải thông báo byte-for-byte được bảo đảm**.

### Phân biệt lỗi thiếu native binary với lỗi không nạp được Java class

`ClassNotFoundException` phát sinh khi code tìm Java class theo tên không thành công.

`NoClassDefFoundError` thường phát sinh khi JVM cần Java class definition nhưng không tìm/link/initialize thành công theo tham chiếu bytecode; có thể có nguyên nhân khác nhau.

`UnsatisfiedLinkError` phát sinh ở **native linking/loading**, ví dụ native binary không tìm được hoặc native method chưa liên kết được. Một class `plugin.NativeBridge` có thể đã được Java classloader tìm thấy hoàn toàn hợp lệ, nhưng initializer `System.loadLibrary` vẫn ném `UnsatisfiedLinkError`.

## 6. Ví dụ Java 17 + JNI/C tái hiện lỗi ở loader thứ hai

Ví dụ này dùng **một thư viện native thật**, không giả lập lỗi bằng cách tự viết `throw new UnsatisfiedLinkError(...)`. Hai `URLClassLoader` khác nhau cùng định nghĩa Java wrapper `plugin.NativeBridge`. Wrapper chứa static initializer gọi `System.load(absolutePath)`.

Luồng thí nghiệm:

~~~text
                     Host: NativeLoaderLab
                               |
       +-----------------------+-----------------------+
       |                                               |
 URLClassLoader A                                URLClassLoader B
       |                                               |
 plugin.NativeBridge (A)                        plugin.NativeBridge (B)
       |                                               |
 System.load(native shared lib)                 System.load(CÙNG lib)
       |                                               |
       v                                               v
 First load succeeds                         UnsatisfiedLinkError
       |
 NativeBridge.answer() -> 42
~~~

**Yêu cầu:** JDK 17+, một C compiler/linker có thể tạo JNI shared library tương thích, và không có bản `plugin.NativeBridge` trên application classpath của host.

### 6.1. Cấu trúc thư mục

~~~text
project/
  src/
    plugin/
      plugin/
        NativeBridge.java
    host/
      NativeLoaderLab.java
    native/
      nativelab.c
  out/                       (sinh khi build)
    plugin/
    host/
  native/                    (header và .so/.dylib/.dll sinh khi build)
    plugin_NativeBridge.h
    libnativelab.so
~~~

Trên macOS, native output có thể là `libnativelab.dylib`; trên Windows là `nativelab.dll`. Không cố dùng Linux `.so` trên Windows.

### 6.2. File `src/plugin/plugin/NativeBridge.java`

~~~java
package plugin;

public final class NativeBridge {
    static {
        String absolutePath =
                System.getProperty("demo.native.path");

        if (absolutePath == null) {
            throw new IllegalStateException(
                    "Missing demo.native.path");
        }

        System.load(absolutePath);
    }

    private NativeBridge() {
    }

    public static native int answer();
}
~~~

- Mỗi loader tự định nghĩa `plugin.NativeBridge` vì class này **không ở application classpath**.
- Static initializer chạy **khi class được initialize**, không phải chỉ vì `Class.forName(name, false, loader)`.
- `System.load` nhận absolute path do host gán từ command-line argument.
- `answer()` là native method, không có Java method body; JVM liên kết nó với JNI function trong library.

### 6.3. File `src/native/nativelab.c`

~~~c
#include "plugin_NativeBridge.h"

JNIEXPORT jint JNICALL
Java_plugin_NativeBridge_answer(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    return 42;
}
~~~

Header `plugin_NativeBridge.h` do công cụ `javac -h` tự sinh từ Java declaration. Việc dùng header được sinh tránh đoán signature bằng tay; phần native implementation nhận `JNIEnv *` và `jclass` vì `answer()` là **static native method**.

Đây là JNI C implementation hoàn chỉnh cho method của ví dụ. Không cần gọi `RegisterNatives` vì symbol được export theo **quy tắc đặt tên JNI**.

### 6.4. File `src/host/NativeLoaderLab.java`

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class NativeLoaderLab {
    private static final String BRIDGE = "plugin.NativeBridge";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: NativeLoaderLab <plugin-dir> <native-lib>");
        }

        URL pluginRoot = Path.of(args[0])
                .toAbsolutePath().toUri().toURL();

        String nativePath = Path.of(args[1])
                .toAbsolutePath().normalize().toString();

        System.setProperty("demo.native.path", nativePath);

        ClassLoader parent = ClassLoader.getPlatformClassLoader();

        try (URLClassLoader loaderA = new URLClassLoader(
                    "plugin-A", new URL[]{pluginRoot}, parent);
             URLClassLoader loaderB = new URLClassLoader(
                    "plugin-B", new URL[]{pluginRoot}, parent)) {

            // Chỉ nạp, chưa chạy static initializer.
            Class<?> classA = Class.forName(BRIDGE, false, loaderA);
            Class<?> classB = Class.forName(BRIDGE, false, loaderB);

            System.out.println("same binary name: "
                    + classA.getName().equals(classB.getName()));
            System.out.println("same runtime type: "
                    + (classA == classB));
            System.out.println("A owns class: "
                    + (classA.getClassLoader() == loaderA));
            System.out.println("B owns class: "
                    + (classB.getClassLoader() == loaderB));

            // Initializing class A calls System.load(nativePath).
            Class.forName(BRIDGE, true, loaderA);
            Object answer = classA.getMethod("answer").invoke(null);
            System.out.println("first native answer: " + answer);

            // Initializing class B tries to load SAME JNI library
            // but the library already belongs to loader A.
            try {
                Class.forName(BRIDGE, true, loaderB);
                System.out.println("second native load: success");
            } catch (UnsatisfiedLinkError error) {
                System.out.println(
                        "second native load: UnsatisfiedLinkError");
            }
        }
    }
}
~~~

**Giải thích từng kỹ thuật:**

1. Parent là **platform class loader** để hai plugin loaders không vô tình nhận `plugin.NativeBridge` từ application classpath. Trong production, parent thường cần chứa shared API; demo không cần API riêng.
2. `Class.forName(BRIDGE, false, loader)` trả runtime `Class<?>` mà **chưa chạy static initializer**; cho phép quan sát cùng tên nhưng khác defining loader.
3. `Class.forName(BRIDGE, true, loaderA)` chạy initializer của class A, gọi `System.load`; native lib được nạp dưới loader A.
4. Invocation `answer()` qua reflection gọi JNI function và trả số nguyên 42.
5. `Class.forName(BRIDGE, true, loaderB)` chạy initializer **khác** của class B, cố tải cùng library; JVM phát hiện library thuộc loader A và ném `UnsatisfiedLinkError`.
6. Chương trình chỉ in **loại lỗi**, không phụ thuộc text của `error.getMessage()` hoặc đường dẫn thật trên máy.
7. `URLClassLoader.close()` đóng các tài nguyên loader Java như JAR/file handle; **không tự bảo đảm native library được unload ngay**.

### 6.5. Build trên Linux (Bash + GCC)

Chạy từ thư mục `project`. Trước hết cần có **JDK** và `gcc`; `JAVA_HOME` phải trỏ đến JDK đang dùng:

~~~bash
mkdir -p out/plugin out/host native

javac -h native -d out/plugin \
    src/plugin/plugin/NativeBridge.java

javac -d out/host src/host/NativeLoaderLab.java

gcc -fPIC -shared \
    -I"$JAVA_HOME/include" \
    -I"$JAVA_HOME/include/linux" \
    -Inative \
    -o native/libnativelab.so \
    src/native/nativelab.c

java -cp out/host NativeLoaderLab \
    out/plugin native/libnativelab.so
~~~

Nếu `JAVA_HOME` chưa được đặt, xác định nó từ thư mục JDK cài đặt, ví dụ trên nhiều hệ Linux:

~~~bash
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
~~~

Lệnh này **phụ thuộc layout JDK của hệ thống**; trên hệ khác, đặt `JAVA_HOME` thủ công. Để kiểm tra: file `"$JAVA_HOME/include/jni.h"` và `"$JAVA_HOME/include/linux/jni_md.h"` phải tồn tại.

**Quan trọng:** `-cp out/host` chỉ chứa host class. **Không thêm `out/plugin` vào application classpath**, nếu không parent/caller có thể thấy Java wrapper trước và thí nghiệm hai defining loaders không còn đúng.

### 6.6. Build trên macOS (Bash + Clang)

Sau hai lệnh `javac` ở trên, thay lệnh GCC Linux bằng:

~~~bash
clang -dynamiclib \
    -I"$JAVA_HOME/include" \
    -I"$JAVA_HOME/include/darwin" \
    -Inative \
    -o native/libnativelab.dylib \
    src/native/nativelab.c

java -cp out/host NativeLoaderLab \
    out/plugin native/libnativelab.dylib
~~~

Có thể dùng `/usr/libexec/java_home` để xác định `JAVA_HOME` khi JDK được cài đặt theo bố cục macOS chuẩn. Với máy Apple Silicon, phải build native binary đúng kiến trúc của process Java đang chạy.

### 6.7. Build trên Windows

Dùng **Visual Studio Developer Command Prompt** có MSVC C compiler `cl` và đã đặt `JAVA_HOME` tới JDK:

~~~bat
mkdir out\plugin
mkdir out\host
mkdir native

javac -h native -d out\plugin src\plugin\plugin\NativeBridge.java
javac -d out\host src\host\NativeLoaderLab.java

cl /LD /I "%JAVA_HOME%\include" /I "%JAVA_HOME%\include\win32" /I "native" src\native\nativelab.c /Fe:native\nativelab.dll

java -cp out\host NativeLoaderLab out\plugin native\nativelab.dll
~~~

Các lệnh là **Command Prompt**, không phải PowerShell; điều kiện là `cl`/linker/SDK đã được cấu hình, file native đầu ra được tạo thành công và kiến trúc DLL khớp JVM. Trên Windows, thư viện phụ thuộc có thể cần nằm trong đường tìm DLL của hệ điều hành.

### 6.8. Stdout mong đợi

Với chương trình mới chạy trong JVM sạch, đúng library và không có class `plugin.NativeBridge` trên parent classpath:

~~~text
same binary name: true
same runtime type: false
A owns class: true
B owns class: true
first native answer: 42
second native load: UnsatisfiedLinkError
~~~

**Đây là output dự kiến theo đặc tả JNI**; compiler C/linker hoặc launcher lỗi sẽ khiến chương trình không đến bước này. Chuỗi chi tiết của `UnsatisfiedLinkError` có thể khác giữa các JDK/OS; demo tránh in nó.

Lỗi này không phải do function `answer()` bị thiếu: lời gọi của class A trả 42, chứng minh thư viện native và symbol JNI hoạt động. Lỗi thứ hai xảy ra khi **đăng ký cùng library dưới defining loader khác**.

### 6.9. Có nên catch `UnsatisfiedLinkError` và tiếp tục như demo?

Trong demo, catch chỉ để **phân loại kết quả có chủ đích**. Trong production, không nên catch rồi giả vờ đã khởi tạo plugin thành công: **initialization của class B đã thất bại**, có thể làm class ấy ở trạng thái erroneous. Lần sử dụng tiếp theo có thể ném `NoClassDefFoundError: Could not initialize class ...`.

Nếu load native thất bại, hãy **dừng kích hoạt plugin generation đó**, ghi đầy đủ lỗi/cause và sửa loader architecture hoặc native packaging trước khi thử lại bằng generation mới. Không mù quáng loop `System.load` hoặc instantiate lại class đã failed initialization.

## 7. Giải pháp ưu tiên: một JNI bridge do parent loader sở hữu

Nếu nhiều plugin trong cùng JVM dùng **một native implementation có trạng thái chia sẻ**, thường nên tải JNI wrapper và native library **một lần trong loader cha dùng chung**:

~~~text
                 Application loader (parent)
                   |
            api.NativeBridge
            static { System.loadLibrary("bridge"); }
            public static native int answer();
                   |
            libbridge.so / bridge.dll
                   |
       +-----------+-----------+
       |                       |
 PluginLoader A            PluginLoader B
  plugin.ImplA              plugin.ImplB
       |                       |
       +---------call----------+
                 |
           api.NativeBridge
        (cùng một Class object)
~~~

Hai plugin delegate việc tìm `api.NativeBridge` về parent; parent định nghĩa đúng **một runtime class** chứa native method declaration. Khi gọi native bridge, chúng không cố gắng `System.loadLibrary` cùng JNI binary từ hai defining loaders riêng.

### 7.1. Shared API và implementation riêng

Để thiết kế rõ hơn, nên chia:

~~~text
shared-api.jar (application classpath)
  api.NativeCodec                    <- public interface

shared-native-bridge.jar (parent)
  bridge.JniNativeCodec              <- class gọi System.loadLibrary

plugin-a.jar (child A)
  plugin.a.ServiceA                   <- dùng api.NativeCodec

plugin-b.jar (child B)
  plugin.b.ServiceB                   <- dùng api.NativeCodec
~~~

Parent sở hữu cả **JNI wrapper có method native** và native binary; shared interface cũng thuộc parent. Khi plugin cần native function, nó gọi qua public API của parent hoặc một service object parent cung cấp.

Điều này có lợi:

- Không nạp lại cùng native library qua mỗi plugin classloader.
- Hạn chế duplicate Java wrapper classes liên quan JNI.
- Đặt điểm khởi tạo/shutdown native state vào một thành phần có lifecycle rõ.
- Dễ chẩn đoán và kiểm soát version chung của JNI ABI.

**Đánh đổi:** plugin không thể đồng thời sử dụng các native library versions có ABI không tương thích bằng cách giả vờ chúng là hai class phiên bản riêng. Nếu cần version isolation thực sự, có thể cần **process isolation** hoặc binary native hoàn toàn độc lập với tên/path và state được kiểm soát. Không có bảo đảm rằng copy cùng `.so`/.dll đổi tên thành hai file là đủ an toàn.

### 7.2. Vì sao “chỉ đặt shared interface ở parent” chưa đủ?

Giả sử parent có `api.NativeCodec` nhưng từng plugin child vẫn tự định nghĩa `plugin.NativeBridge` với static initializer gọi `System.loadLibrary("bridge")`.

Dù cả hai plugin có thể cast về `api.NativeCodec`, native bridge classes vẫn do **hai defining loaders riêng** sở hữu; chúng vẫn có thể tranh chấp cùng JNI library.

Đúng hơn là đặt **class thật sự gọi `System.load/System.loadLibrary` và khai báo native methods** trong parent hoặc một loader chung đủ ổn định, rồi expose method/service tới plugin.

### 7.3. Khi nào không nên tải native library lên parent?

Không phải mọi native library có thể chia sẻ:

- Hai plugins yêu cầu **ABI hoặc version native thực sự khác nhau**.
- Native code giữ **process-global state**, không hỗ trợ multi-tenancy, có singleton/worker threads hoặc callbacks vào classes của plugin.
- API wrapper ở parent không thể cấu hình state riêng per plugin.
- Plugin không tin cậy hoặc cần quyền hệ điều hành khác nhau.
- Native library có lifecycle gắn với một plugin và không được dùng bởi phần còn lại.

Trong các trường hợp này, phải thiết kế boundary cụ thể, có thể dùng process riêng. Một classloader khác **không tương đương một tiến trình native riêng**.

## 8. Tại sao “copy library thành nhiều file” không phải giải pháp mặc định?

Một số ứng dụng trích xuất cùng một native binary vào nhiều thư mục temp với tên khác nhau để mỗi plugin gọi `System.load` lên path khác.

Cách này **có thể thay đổi identity đường dẫn ở một số implementation**, nhưng không phải giải pháp portable/được JNI đảm bảo cho cô lập native state, vì:

- OS dynamic loader có thể có chính sách canonicalization/deduplication image khác.
- Native dependencies nội bộ có thể vẫn là **cùng những shared libraries** với global/static state.
- Native code có thể tự dùng process-global singleton, lock, network ports, temp files hoặc C runtime state.
- Native code có thể lưu `JNIEnv*` hoặc `jclass` sai vòng đời, tạo crash/undefined behavior.
- Hai binary giống nhau vẫn có thể export JNI symbols trùng hoặc trỏ tới cùng downstream native runtime.
- Windows thường khó xóa/ghi đè một DLL đang được process giữ.
- Packaging nhiều copies gây rác temp files, library handle leak và khó security audit.

Nếu một thư viện có tài liệu chính thức xác nhận hỗ trợ **multi-classloader JNI instances**, hãy làm theo cơ chế cụ thể của vendor. Nếu không, **shared parent bridge** hoặc **separate process** là hai lựa chọn dễ chứng minh đúng hơn.

## 9. `JNI_OnLoad`, `JNI_OnUnload` và cơ hội unload library

JNI có những lifecycle callbacks tùy chọn:

~~~c
#include <jni.h>

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)vm;
    (void)reserved;
    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *vm, void *reserved) {
    (void)vm;
    (void)reserved;
    // Chỉ cleanup những tài nguyên có thể cleanup an toàn.
}
~~~

Đây là **mảnh C minh họa về hooks**, **không** cần ghép vào file `nativelab.c` của demo để chạy; nếu thêm hooks, bảo đảm không định nghĩa trùng những symbols này trong cùng library.

- `JNI_OnLoad` được VM gọi khi native library động được load nếu library có export hook. Hook trả JNI version mà library cần.
- `JNI_OnUnload` có thể được gọi khi class loader chứa library được GC thu gom. **Không có lệnh Java phổ thông bảo đảm gọi hook ngay tại thời điểm mong muốn**.
- Đặc tả khuyến cáo `JNI_OnUnload` dùng Java VM services **thận trọng** vì callback có thể chạy trong ngữ cảnh không lường trước. Tránh callback tùy ý sang Java trong hook cleanup.
- Đừng giữ `JNIEnv*` xuyên threads: `JNIEnv` gắn với thread. Với native threads cần cơ chế attach/detach JVM đúng API.
- Không giữ Java object/class references lâu dài mà không có quản lý local/global/weak global references đúng JNI contract.

### `URLClassLoader.close()` có unload JNI library ngay không?

**Không có bảo đảm như vậy.** `URLClassLoader.close()` đóng những file/JAR handles mà URLClassLoader đã mở. Nó **không phải** một API `dlclose`/`FreeLibrary` tùy ý cho JNI library.

Một native library liên kết với loader có thể có cơ hội unload khi loader thật sự không còn reachable và được GC thu gom, nhưng:

1. Thread đang chạy, callback, TCCL, static caches hoặc application registry có thể giữ loader/class/plugin objects sống.
2. JVM không cam kết GC/unloading xảy ra ngay sau `System.gc()` hay `close()`.
3. Native library có thể giữ resources, threads và global references cần cleanup chuyên biệt.
4. Chính sách unload và native dependency behavior cũng phụ thuộc JVM/OS.

**Không dùng `System.gc()` như lời hứa rằng có thể reload library vào loader khác ngay lập tức.** Hãy quản lý vòng đời chủ động, và thiết kế shared native bridge nếu cần tái triển khai Java plugins thường xuyên.

## 10. `RegisterNatives`, JNI symbol và native method linkage

`UnsatisfiedLinkError` không chỉ xảy ra khi load library. Sau khi thư viện được nạp, lúc Java code gọi một `native` method, JVM vẫn phải tìm implementation tương ứng.

### 10.1. JNI naming

Với Java declaration:

~~~java
package plugin;

public final class NativeBridge {
    public static native int answer();
}
~~~

JNI C symbol thông thường (không overload trong ví dụ) là:

~~~c
JNIEXPORT jint JNICALL
Java_plugin_NativeBridge_answer(JNIEnv *env, jclass cls) {
    (void)env;
    (void)cls;
    return 42;
}
~~~

Dùng `javac -h native ...` để sinh header chính xác. Nếu native method có **overloads**, tên symbol có thể bao gồm thông tin signature được mangle theo quy tắc JNI; không nên tự đoán.

Với C++, cần tuân thủ xuất symbols đúng linkage (header JNI được JDK sinh thường xử lý `extern "C"` theo macro/cấu trúc phù hợp). Với C#/.NET native shim hoặc Rust, cần export ABI/symbol đúng contract của JNI và toolchain tương ứng.

### 10.2. `RegisterNatives`

Một native library có thể đăng ký con trỏ function bằng `RegisterNatives`. Cách này có thể hữu ích với generated/proxy JNI integrations nhưng cũng đòi hỏi:

- Sử dụng đúng `jclass` của **runtime class cần đăng ký**.
- Method names/signatures khớp khai báo Java.
- Không nhầm two classes cùng binary name nhưng thuộc different defining loaders.
- Quản lý JNI references và thread-safe native state.

**Không** dùng `RegisterNatives` như một mẹo để bỏ qua quy tắc “same JNI library cannot be loaded into more than one classloader”. Việc library ownership theo JNI vẫn phải được tôn trọng.

### 10.3. Phân biệt hai loại thất bại bằng thời điểm

~~~text
Java class initialization
       |
       +--> System.loadLibrary("bridge")
       |         |
       |         +--> no library / dependency failure
       |         +--> already loaded in another loader
       |         +--> bad architecture / JNI_OnLoad failure
       |
       v
native method invocation
       |
       +--> JNI symbol / RegisterNatives lookup
                 |
                 +--> symbol missing or incompatible signature
~~~

Thông báo `UnsatisfiedLinkError` của các nhánh có thể tương tự nhau, nên log **thao tác nào đã fail** cùng stack trace, defining loader và lib path.

## 11. JNI class loader lookup và callbacks từ native code

Native code đôi khi gọi JNI `FindClass` để tìm Java types. Quy tắc chọn class loader trong JNI có thể khác nhau tùy **call context**, ví dụ đang chạy trong một native method Java hay từ một native-attached thread không có Java frame phù hợp.

Vấn đề phổ biến:

- Cùng `plugin.Dto` có thể được mỗi plugin loader định nghĩa riêng.
- Native code cache sai một `jclass` rồi dùng cho plugin khác.
- Thread native tự attach vào JVM có thể không nhìn thấy class của plugin nếu tìm tên bằng loader không đúng.
- Thread context class loader của Java **không tự sửa mọi trường hợp JNI `FindClass` trên native thread**.

**Thiết kế khuyến nghị:**

1. Để Java side truyền `Class<?>`/`ClassLoader`/object đã được kiểm tra phù hợp vào native bridge khi cần; native code nhận đối tượng JNI hợp lệ.
2. Nếu native code cần giữ class/object lâu hơn một call, tạo global/weak global reference đúng API rồi cleanup.
3. Không lưu `JNIEnv*` trong biến global rồi dùng từ thread khác.
4. Tránh callback xuyên những plugin generations đã retire.
5. Nếu sử dụng `RegisterNatives`, đăng ký với đúng runtime class, không chỉ dựa vào chuỗi tên.
6. Bảo đảm API/DTO mà native bridge trao đổi với nhiều plugins được **định nghĩa ở parent loader** hoặc chuyển thành dữ liệu trung lập.

Những nguyên tắc này giúp ngăn lỗi JNI khó quan sát, nhưng **không làm một JNI library trở thành có thể tải lại vào nhiều defining loaders**.

## 12. Native library có phụ thuộc hệ điều hành và CPU architecture

Classloader có thể tìm được Java wrapper, nhưng native binary vẫn cần tương thích với **tiến trình JVM đang chạy**:

| Yếu tố | Ví dụ |
|---|---|
| OS | Linux `.so`, macOS `.dylib`, Windows `.dll` |
| CPU instruction set | x86_64, arm64/AArch64 |
| Process bitness | JVM 64-bit cần native binary 64-bit tương thích |
| Calling convention/ABI | JNI macros và compiler toolchain phù hợp |
| C/C++ runtime dependencies | glibc/libstdc++/MSVC runtime phù hợp |
| Transitive native libraries | `libcrypto.so`, runtime DLL, framework native khác |
| Library search path của OS | `LD_LIBRARY_PATH`, rpath, loader configuration, Windows DLL search |
| Security restrictions | File permissions, executable mapping, system policy, hardened runtime/codesigning khi có |

Một file thực sự tồn tại nhưng thiếu **transitive dependency** vẫn có thể làm `System.load` thất bại. Vì vậy “file exists = true” không chứng minh loader có thể map binary vào process.

### Công cụ điều tra theo hệ điều hành

**Linux:**

~~~bash
file native/libnativelab.so
ldd native/libnativelab.so
nm -D native/libnativelab.so
~~~

**macOS:**

~~~bash
file native/libnativelab.dylib
otool -L native/libnativelab.dylib
nm -gU native/libnativelab.dylib
~~~

**Windows:**

~~~text
dumpbin /DEPENDENTS native\nativelab.dll
dumpbin /EXPORTS native\nativelab.dll
~~~

Các lệnh trên là **lệnh chẩn đoán**, không phải output được bảo đảm trên mọi máy; tùy toolchain có thể cần bổ sung flags. `ldd` đối với binary không tin cậy có nguy cơ trên một số môi trường; không dùng tùy tiện cho artifact chưa được kiểm tra nguồn gốc.

### Kiểm tra Java process và library path

~~~java
System.out.println("java.version="
        + System.getProperty("java.version"));
System.out.println("os.name="
        + System.getProperty("os.name"));
System.out.println("os.arch="
        + System.getProperty("os.arch"));
System.out.println("java.library.path="
        + System.getProperty("java.library.path"));
System.out.println("mapped="
        + System.mapLibraryName("nativelab"));
~~~

Đây là **mảnh minh họa**. Các property hữu ích để debug, nhưng không thay thế phân tích native dependencies và defining loader.

## 13. Vòng đời plugin và tình huống redeploy

Giả sử application server tạo:

~~~text
Generation 1:
  PluginLoader-A
      |
      +-- NativeBridge loads libnativecodec.so
      |
      +-- Plugin A active

Redeploy:
  PluginLoader-A should retire
  PluginLoader-B created
      |
      +-- NativeBridge attempts same libnativecodec.so
      |
      +-- UnsatisfiedLinkError
~~~

Vì JVM chưa thể unload library thuộc loader A (loader A còn reachable), loader B không thể nhận cùng JNI native library như thể chưa từng được dùng.

**Nhiều reference gián tiếp khiến loader A còn sống:**

- Threads được plugin tạo ra chưa dừng.
- `ThreadLocal` hoặc thread context class loader của thread pool trỏ đến class/loader cũ.
- Host cache `Class<?>`, object plugin, `Method`, `MethodHandle` hoặc provider instance.
- Listener, registry, scheduled task, event subscription, MBean hoặc classpath framework cache chưa gỡ.
- Native code giữ global references hoặc callbacks vào plugin Java objects.

### Lifecycle đề xuất

~~~text
ACTIVE
  |
  | Không nhận công việc mới
  v
DRAINING
  |
  | Hoàn tất in-flight calls
  | Dừng plugin threads/background executors
  | Hủy callbacks/listeners, reset TCCL
  | Đóng streams/URLClassLoader
  | Bỏ strong references ở caches/registries
  v
RETIRED
  |
  | GC/unloading theo điều kiện của JVM
  v
MAY BE UNLOADED  (không cam kết thời điểm)
~~~

Nếu plugin cần redeploy thường xuyên, **đặt JNI bridge ở parent** và quản lý native state riêng per plugin ở tầng Java/native API nếu thư viện hỗ trợ. Không dựa vào việc loader cũ có thể được GC ngay đúng một thời điểm xác định.

### Khi native state không hỗ trợ multi-tenancy

Nếu native code chỉ có một process-global singleton hoặc không hỗ trợ nhiều versions/instances, API chung ở parent **vẫn chưa giải quyết mọi yêu cầu**. Cần:

- Một native process chung quản lý state theo contract rõ ràng, hoặc
- Mỗi plugin chạy trong một process/container riêng, hoặc
- Thiết kế lại JNI library để hỗ trợ nhiều *handles/contexts* độc lập khi vendor cho phép.

## 14. Quy trình chẩn đoán UnsatisfiedLinkError thực tế

### Bước 1 — Xác định lỗi xảy ra ở thao tác nào

Xem stack trace/cause chain:

- `System.loadLibrary`: lookup theo tên logic hoặc conflict ownership.
- `System.load`: absolute path/mapping hoặc conflict ownership.
- Lời gọi một method `native`: JNI symbol/method registration.
- Static initializer: có thể khiến cả class initialization thất bại.
- Plugin restart: cân nhắc classloader leak/native lifecycle.

### Bước 2 — Log classloader của Java wrapper

~~~java
Class<?> wrapper = plugin.NativeBridge.class;
ClassLoader owner = wrapper.getClassLoader();

System.out.println(wrapper.getName());
System.out.println(owner);
System.out.println(wrapper.getModule());
~~~

Đây là **mảnh minh họa** trong môi trường có thể tham chiếu `plugin.NativeBridge`; host không có wrapper ở compile classpath như demo nên phải dùng `Class<?>` lấy từ explicit loader.

Nếu đang debug từ host:

~~~java
Class<?> type = pluginLoader.loadClass("plugin.NativeBridge");

System.out.println(type.getClassLoader() == pluginLoader);
System.out.println(type.getModule());
~~~

**Đừng chỉ in tên loader**: hai instance khác nhau có thể đặt cùng tên. So sánh reference bằng `==` khi có thể và gắn logical generation IDs để log.

### Bước 3 — Kiểm tra native file và dependencies

- Xác minh **đường dẫn tuyệt đối thực** khi gọi `System.load`.
- Nếu gọi `System.loadLibrary`, kiểm tra tên **không chứa prefix/path/extension**.
- Dùng công cụ OS để kiểm tra architecture, dependencies và exports.
- Kiểm tra JDK/JVM process thực sự đang chạy, chứ không chỉ `JAVA_HOME` lúc build.
- Nếu chương trình chạy trong container, xác minh library có trong container image và đúng libc/ABI.
- Nếu nghi duplicate load, log Java wrapper class, owner loader và plugin generation ngay tại static initializer.

### Bước 4 — Xác nhận ownership conflict

Nếu **loader A đã nạp thành công cùng JNI binary** và loader B mới xuất hiện, đây là dấu hiệu rất mạnh của nguyên nhân “already loaded in another classloader”.

Đặc biệt, nếu tái khởi động **toàn bộ JVM** khiến lỗi biến mất nhưng hot redeploy một plugin gây lỗi, hãy ưu tiên điều tra **native library ownership + old classloader references**. Restart JVM là workaround vận hành, không phải giải pháp vòng đời bền vững.

### Bước 5 — Chọn fix theo bảng

| Kết quả chẩn đoán | Hướng sửa phù hợp |
|---|---|
| Không tìm thấy native binary | Sửa path/packaging và startup configuration |
| Native dependency thiếu | Đóng gói/cài các dependency native, sửa OS loader path |
| Sai CPU/OS/bitness | Build lại đúng target platform |
| JNI symbol sai hoặc export thiếu | `javac -h`, kiểm tra `JNIEXPORT`/`JNICALL`/symbol mangling |
| Duplicate load qua 2 plugin loaders | Shared JNI bridge ở parent hoặc kiến trúc native isolation rõ |
| Reload không unload được | Dọn old-loader references; tránh coi GC là deterministic |
| Native library ABI không chia sẻ được | Separate processes hoặc handle-based multi-instance nếu library hỗ trợ |
| Plugin chưa tin cậy | Isolate ở process/OS boundary, không chỉ classloader |

### Bước 6 — Thử trong môi trường sạch

Một test tái hiện nên:

1. Dùng **JVM mới**.
2. Log một classloader owner/generation ID mỗi lần nạp native binary.
3. Tải một wrapper Java từ loader A, gọi native method thành công.
4. Tạo loader B và cố tải cùng binary; phân loại `UnsatisfiedLinkError`.
5. Test phương án parent-owned bridge: cả hai plugin gọi cùng một wrapper runtime type.
6. Test redeploy/shutdown mà không dựa vào `System.gc()` như cam kết unload.

## 15. Liên hệ với ClassNotFoundException và NoClassDefFoundError

Trong plugin lifecycle, các lỗi có thể xuất hiện liên tiếp nhưng không đồng nhất:

~~~text
Host/Plugin ClassLoader
    |
    +-- loadClass("plugin.NativeBridge")
           |
           +-- Không thấy class Java -> ClassNotFoundException
           |
           +-- Thấy class Java -> Class<?> được tạo
                    |
                    +-- initialize class
                           |
                           +-- System.load(...)
                                  |
                                  +-- Native binary missing
                                  |     -> UnsatisfiedLinkError
                                  |
                                  +-- Already loaded by other loader
                                  |     -> UnsatisfiedLinkError
                                  |
                                  +-- JNI library loaded
                                          |
                                          +-- native method called
                                                 |
                                                 +-- JNI symbol missing
                                                       -> UnsatisfiedLinkError
~~~

Nếu `System.load` ném `UnsatisfiedLinkError` trong static initializer, đây là `Error` nên **có thể được ném trực tiếp**; không nhất thiết bị bọc thành `ExceptionInInitializerError` như trường hợp initializer ném một non-Error exception.

Khi initialization của class đã fail, JVM đánh dấu class ở trạng thái lỗi theo quy tắc initialization; lời gọi sau trên class đó có thể nhận `NoClassDefFoundError` kiểu “Could not initialize class ...”. Vì vậy **đừng thử nạp lại vô hạn trên cùng class** sau khi static initialization thất bại.

Các bước debug phải lưu **lỗi đầu tiên** chứ không chỉ lỗi của lần retry sau.

## 16. JNI trong bối cảnh JPMS và Java mới

JNI native loading và JPMS modularity là **hai chiều kiểm soát khác nhau**:

- `requires`, `exports`, `opens`, module layers điều khiển việc đọc/access theo Java modules.
- `System.loadLibrary` và native method resolution liên quan native-library ownership và ABI.
- Thêm `--add-opens` không làm thư viện native có thể được nạp một lúc vào hai class loaders.
- Tạo `ModuleLayer` mới không đảm bảo native library đã load ở layer/loader khác được load lại độc lập.
- Với các JDK mới có cơ chế **restricted native access**, cần tuân thủ chính sách native access flags tương ứng (ví dụ `--enable-native-access` khi môi trường yêu cầu). Demo định hướng **JDK 17**, không giả định mọi phiên bản mới đều giữ chính sách cảnh báo/từ chối y hệt.
- Foreign Function & Memory API (FFM) hoặc các thư viện bridging khác có thể thay đổi cách gọi mã native, nhưng **không mặc định biến native global state thành độc lập giữa các plugins**. Không dùng FFM như “fix tự động” cho lifecycle/ABI của mọi JNI library.

Cần phân biệt **classloader namespace separation**, **JPMS module access** và **process/OS isolation**; ba khái niệm phục vụ những nhu cầu khác nhau.

## 17. An toàn khi dùng native code và lỗi phổ biến

JNI code thực thi trong quyền của tiến trình Java, có thể truy cập bộ nhớ và tạo native threads. **ClassLoader không cách ly native memory hay quyền hệ điều hành**; nếu plugin không đáng tin cậy, cần process boundary cùng giới hạn tài nguyên và IPC phù hợp.

Năm ngộ nhận quan trọng cần tránh:

1. **“Thêm `java.library.path` chữa mọi `UnsatisfiedLinkError`.”** Đường tìm file không giải quyết JNI symbol/ABI, native dependency, CPU architecture hay *already loaded in another classloader*.
2. **“Một native library đã load cho plugin A thì plugin B tự dùng được.”** JNI sở hữu thư viện theo loader; nếu hai child tự gọi `System.load`, library có thể xung đột.
3. **“`URLClassLoader.close()` hoặc `System.gc()` chắc chắn unload ngay.”** Class/native unloading phụ thuộc reachability, lifecycle và lựa chọn JVM, không có thời điểm được bảo đảm.
4. **“Chỉ đưa shared interface vào parent là đủ.”** Nếu **JNI wrapper thật sự gọi `System.load`** vẫn nằm ở từng child, hai lần native load vẫn xảy ra. Parent cần sở hữu native bridge hoặc thiết kế state riêng rõ ràng.
5. **“Native code có thể giữ `JNIEnv*` dùng xuyên mọi thread hoặc `jclass` local reference vĩnh viễn.”** `JNIEnv*` gắn với thread; JNI object/class references có vòng đời cụ thể và cần global/weak-global quản lý đúng.

Các API như `RegisterNatives`, `--add-opens` hay việc sao chép file `.so` vào nhiều thư mục đều **không mặc nhiên** vượt quy tắc JNI library ownership. Khi xử lý lỗi runtime, giữ nguyên stack trace đầu tiên và phân loại xem thất bại ở bước **load native binary**, **symbol resolution** hay **Java class initialization**.

## 18. Checklist thực tế cho hệ plugin dùng JNI

- [ ] Xác định rõ **Java class khai báo native methods** và defining loader của class đó.
- [ ] Xác định **Java class trực tiếp gọi `System.load`/`loadLibrary`**, không suy đoán chỉ theo plugin ID.
- [ ] Chọn shared JNI wrapper ở parent nếu nhiều plugins dùng chung cùng native library.
- [ ] Giữ public API/DTO chung tại loader phù hợp để không duplicate runtime types.
- [ ] Không vô tình đóng gói thêm bản JNI wrapper ở từng plugin child với child-first policy.
- [ ] `System.loadLibrary` dùng **logical name**; `System.load` dùng **absolute path**.
- [ ] Đúng native binaries cho OS/CPU/bitness của JVM target.
- [ ] Kiểm tra đầy đủ **transitive native dependencies** và các JNI symbols.
- [ ] Header được sinh bằng `javac -h` hoặc signature được verify bằng toolchain tương ứng.
- [ ] Không cố load JNI library đã thuộc loader A vào loader B.
- [ ] Thiết kế native state có thể dùng chung hay cần contexts per plugin.
- [ ] Có policy restart/redeploy, không dựa vào GC timing.
- [ ] Dừng plugin threads/callbacks và bỏ TCCL/caches trước khi retire loader cũ.
- [ ] Có log classloader/generation, native absolute path, JNI ABI và full root cause.
- [ ] Kiểm thử startup sạch, hai plugins cùng lúc, hot redeploy và failure của `JNI_OnLoad`.
- [ ] Kiểm thử broken native symbol, missing native dependency và sai platform binary.
- [ ] Không chạy native code chưa tin cậy trong cùng JVM mà tưởng ClassLoader bảo vệ.
- [ ] Nếu dùng JPMS/newer JDK, kiểm tra riêng restricted native access policy.

## 19. Câu hỏi tự kiểm tra

**Câu 1.** Hai `URLClassLoader` khác nhau cùng định nghĩa `plugin.NativeBridge` và cả hai chạy `System.load("/same/path/libx.so")`. Vì sao loader thứ hai có thể ném `UnsatisfiedLinkError`?

**Đáp án:** Vì cùng một JNI native library đã được gắn với loader thứ nhất. Đặc tả JNI không cho nạp cùng thư viện vào hai classloaders.

**Câu 2.** `System.loadLibrary("libx.so")` có đúng cách dùng không?

**Đáp án:** Không theo contract chuẩn: `loadLibrary` nhận tên logic không prefix/extension/path. Nếu logical name là `x`, gọi `System.loadLibrary("x")`. Với path tuyệt đối, dùng `System.load(...)`.

**Câu 3.** Native binary tồn tại đúng path, nhưng vẫn `UnsatisfiedLinkError`. Có những nguyên nhân nào khác?

**Đáp án:** Sai OS/CPU/bitness, thiếu transitive native library, sai symbols/ABI, phiên bản JNI không hỗ trợ hoặc duplicate ownership giữa loaders.

**Câu 4.** Cho shared API ở parent nhưng mỗi plugin tự định nghĩa JNI wrapper riêng. Đã giải quyết duplicate load chưa?

**Đáp án:** Chưa. Cần đặt **JNI wrapper native owner** ở một shared loader phù hợp hoặc thiết kế native state/lifecycle khác.

**Câu 5.** `URLClassLoader.close()` có đảm bảo gọi `JNI_OnUnload` ngay không?

**Đáp án:** Không. JNI_OnUnload có thể được gọi khi loader chứa library được GC thu gom; thời điểm không được Java app điều khiển chính xác bằng close.

**Câu 6.** Có thể đưa cùng file native sang nhiều thư mục rồi xem như có hai phiên bản native độc lập không?

**Đáp án:** Không có bảo đảm portable. OS loader, dependencies, singleton native state và JNI ownership vẫn có thể gây xung đột hoặc lỗi vòng đời.

**Câu 7.** Khi `UnsatisfiedLinkError` phát sinh trong static initializer, vì sao lần gọi sau có thể trở thành `NoClassDefFoundError`?

**Đáp án:** Vì initialization của runtime class đã thất bại, JVM có thể đánh dấu class lỗi; lần active use sau không tự chạy lại initializer như class mới.

**Câu 8.** Trong demo, vì sao `classA == classB` là `false` dù tên nhị phân đều `plugin.NativeBridge`?

**Đáp án:** Vì A và B là hai defining loaders khác nhau, nên hai runtime types khác nhau.

**Câu 9.** Lỗi “native method not found” có bắt buộc là “already loaded in another classloader” không?

**Đáp án:** Không. Native library có thể được load thành công nhưng export sai JNI symbol, signature hoặc registration. Cần xem giai đoạn lỗi.

**Câu 10.** Khi native library không hỗ trợ multi-instance và mỗi plugin cần version khác nhau, đâu là boundary mạnh hơn classloader?

**Đáp án:** Separate process/container với IPC contract rõ ràng. Classloader không tạo native process-global namespace độc lập.

## 20. Quy trình quyết định

~~~text
             UnsatisfiedLinkError
                     |
                     v
       Xảy ra khi System.load* hay
           khi gọi native method?
                /          \
          System.load*     native invocation
                |               |
                v               v
       Library found?       JNI export/signature/
           /    \            RegisterNatives?
         Không   Có
          |       |
     Sửa path,   v
     OS deps    Already loaded elsewhere?
                /                  \
              Có                    Không
               |                      |
        Kiểm tra owner loader      Kiểm tra ABI,
               |                  dependency, arch
               v
     Nhiều plugin cùng dùng lib?
           /            \
         Có              Không
          |                |
   Shared JNI wrapper    Dọn lifecycle/
      ở parent          xem native state
          |
          v
       Test hai loaders / redeploy
          |
          v
     Không dựa vào GC hoặc
     copy file như giải pháp mặc định
~~~

**Kết luận:** `UnsatisfiedLinkError` trong nhiều classloaders không chỉ là “không tìm thấy thư viện”. Một native binary có thể tồn tại, có JNI symbol đúng và đã chạy thành công với plugin A, nhưng vẫn không được nạp lại vào plugin B vì quy tắc **native-library ownership theo class loader**. Cách giải quyết thường bền vững là **một parent-owned JNI bridge**, hợp đồng dùng chung rõ ràng, hoặc **cách ly process** khi native state/ABI không thể chia sẻ. Kết hợp việc xác định defining loader, native path, OS dependencies và lifecycle sẽ giúp phân biệt đúng nguyên nhân thay vì sửa classpath mù quáng.

### Tài liệu tham khảo chính thức

- [Java 17 JNI Specification — Invocation API / Library and Version Management](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/invocation.html)
- [Java 17 JNI Specification — Design Overview / Resolving Native Method Names](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/design.html)
- [Java SE 17 — System.load, System.loadLibrary, System.mapLibraryName](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html)
- [Java SE 17 — ClassLoader.findLibrary](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html#findLibrary(java.lang.String))
- [Java SE 17 — URLClassLoader.close](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html#close())
- [Java SE 17 — UnsatisfiedLinkError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/UnsatisfiedLinkError.html)
- [Java SE 17 — JNI Types and Functions](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/functions.html)


</details>

- [Quay lại đầu trang](#back-to-top)
