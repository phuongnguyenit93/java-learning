<a id="back-to-top"></a>

# Thực hành

## Menu
- [In cây loader và nguồn gốc các lớp trong một ứng dụng nhỏ](#class-loading-exercises-001)
- [Đọc cấu hình từ thư mục và JAR bằng API resource](#class-loading-exercises-002)
- [Xây dựng loader nạp bytecode từ bộ nhớ](#class-loading-exercises-003)
- [Cô lập hai phiên bản thư viện trong hai loader](#class-loading-exercises-004)
- [Tạo plugin dùng API chung và phát hiện bằng ServiceLoader](#class-loading-exercises-005)
- [Tạo module layer cho plugin mô đun hóa](#class-loading-exercises-006)
- [Tái hiện và sửa lỗi cùng tên lớp nhưng không ép kiểu được](#class-loading-exercises-007)
- [Tái hiện NoClassDefFoundError và lỗi khởi tạo lớp](#class-loading-exercises-008)
- [Thực hiện reload plugin bằng loader mới](#class-loading-exercises-009)
- [Điều tra một tình huống rò rỉ class loader](#class-loading-exercises-010)

## <a id="class-loading-exercises-001">In cây loader và nguồn gốc các lớp trong một ứng dụng nhỏ</a>

<details>
<summary>Xem chi tiết</summary>

# In cây loader và nguồn gốc các lớp trong một ứng dụng nhỏ

## 1. Bài thực hành này giải quyết câu hỏi gì?

Khi JVM đã chạy một ứng dụng Java, không đủ chỉ nhìn vào tên package hoặc classpath để biết **ClassLoader nào thực sự định nghĩa mỗi class**. Đặc biệt khi ứng dụng có plugin JAR, nhiều class loaders hoặc một thư viện được đóng gói lặp, các câu hỏi thường gặp là:

- **java.lang.String** được định nghĩa bởi loader nào?
- Class của chương trình **Main** đến từ application/system loader hay plugin loader?
- Một class chỉ có trong **plugin.jar** có defining loader nào?
- Cây quan hệ **parent** của custom loader có hình dạng gì?
- Parent và plugin cùng chứa **sample.host.HostApi** thì JVM chọn bản nào?
- Có thể xác định **CodeSource** và resource URL của class từ JAR hay từ thư mục không?
- Vì sao hai loaders dùng cùng binary name có thể tạo hai **Class objects khác nhau**?
- In cây loader có đồng nghĩa thấy toàn bộ delegation/module graph không?

Bài thực hành xây một ứng dụng **Java 17+** với host ở thư mục **host-out**, plugin trong **plugin.jar**, hai **URLClassLoaders**, rồi in các bằng chứng tại runtime. Mã đã được biên dịch và chạy trên **OpenJDK 21** với các lệnh nêu dưới đây; ứng dụng chỉ dùng Java standard library.

**Mục tiêu quan trọng nhất:** tách ba loại thông tin: **(1) class identity/defining loader; (2) quan hệ parent của loader; (3) nguồn artifact/code source hoặc URL resource**. Chúng liên quan nhưng không thể dùng thay thế cho nhau.

## 2. Những quan sát khác nhau khi in cây loader

Bài thực hành phải phân biệt **danh tính lớp**, **cây parent** và **nguồn bytes**. Một lớp thông thường được nhận diện bởi **(binary name, defining loader)**, vì vậy hai `URLClassLoader` thực sự định nghĩa `sample.plugin.Greeter` sẽ có hai `Class<?>` khác nhau; còn hai loader ủy quyền cho cùng parent có thể trả đúng **một** `Class<?>`.

| Quan sát | API | Không nên suy ra |
| --- | --- | --- |
| Ai định nghĩa class? | `clazz.getClassLoader()` | Loader được gọi `loadClass()` nhất thiết là defining loader |
| Cha của loader là ai? | `loader.getParent()` | Đây là danh sách tất cả loader đang sống hoặc toàn bộ JPMS graph |
| Nguồn gốc được ghi nhận? | `clazz.getProtectionDomain().getCodeSource()` | URL là bằng chứng mật mã hoặc mọi class đều có CodeSource |
| Resource nằm đâu? | `clazz.getResource(...)` / `loader.getResources(...)` | Resource tồn tại nghĩa là class đã được JVM nạp |
| Class thuộc module nào? | `clazz.getModule()` | Module identity và defining loader là cùng khái niệm |

Trong HotSpot/OpenJDK phổ biến, cây parent bắt đầu từ application loader đi tới platform, rồi bootstrap (thường biểu diễn bằng `null`). `String.class.getClassLoader() == null` vì String do bootstrap định nghĩa, **không phải lỗi nạp lớp**. Một số platform classes có defining loader khác bootstrap; kết quả cụ thể còn tùy runtime/JDK.

Cần phân biệt **initiating loader** (loader khởi phát yêu cầu, có thể delegate) với **defining loader** (loader sở hữu class thực sự). `Class.getClassLoader()` chỉ cho biết defining loader, không phải danh sách mọi initiating loader. Chương trình sẽ đối chiếu object identity, nguồn artifact, parent chain và module thay vì dựa vào tên class hay chuỗi `toString()` của loader.

## 3. Kết quả mong đợi của bài

Chúng ta sẽ xây:

~~~text
Host application:
  host-out/
    sample/app/InspectLoaders.class
    sample/app/IsolatedLoaderDemo.class
    sample/host/HostApi.class
          |
          +-- HostApi.origin() -> "host"

Plugin artifact:
  plugin.jar
    sample/plugin/Greeter.class
    sample/host/HostApi.class
          |
          +-- bản HostApi đóng gói bên plugin
          +-- HostApi.origin() -> "plugin-shadow"
~~~

**Cố ý có hai bản HostApi** để quan sát parent delegation; đây là fixture thực hành, không phải kiến trúc plugin nên mặc định áp dụng. Khi plugin loader có **application loader làm parent**, lời gọi HostApi sẽ chọn bản host trước. Khi một loader cô lập có **platform loader làm parent**, nó không thấy host-out qua parent chain và sẽ lấy bản đóng trong plugin.jar.

Hai trường hợp cùng tên class nhưng defining loader khác nhau sẽ cho kết quả khác nhau.

## 4. Tạo project

Cấu trúc source hoàn chỉnh:

~~~text
loader-tree-lab/
├── src/
│   └── sample/
│       ├── app/
│       │   ├── InspectLoaders.java
│       │   └── IsolatedLoaderDemo.java
│       └── host/
│           └── HostApi.java
├── plugin-src/
│   └── sample/
│       ├── host/
│       │   └── HostApi.java
│       └── plugin/
│           └── Greeter.java
├── host-out/       (sinh ra sau javac)
├── plugin-out/     (sinh ra sau javac)
└── plugin.jar      (sinh ra sau jar)
~~~

**Chạy tất cả các lệnh từ thư mục loader-tree-lab**. Không đặt host-out hoặc plugin.jar lẫn vào một source folder rồi kỳ vọng parent delegation tự chọn đúng; các đường output ở đây được chủ động tách biệt để kiểm chứng.

### 4.1 API bản host

**src/sample/host/HostApi.java**:

~~~java
package sample.host;

public final class HostApi {
    private HostApi() {
    }

    public static String origin() {
        return "host";
    }
}
~~~

Ở runtime, class này sẽ đến từ thư mục **host-out** trên classpath của application.

### 4.2 API bản plugin dùng làm phép thử shadow

**plugin-src/sample/host/HostApi.java**:

~~~java
package sample.host;

public final class HostApi {
    private HostApi() {
    }

    public static String origin() {
        return "plugin-shadow";
    }
}
~~~

Đây là **một class có cùng binary name sample.host.HostApi** nhưng body khác. Ta biên dịch ra **plugin-out** và đóng gói vào plugin.jar. Nó cho phép quan sát trực tiếp bản nào đã được loader chọn, qua kết quả **origin()**.

**Cảnh báo:** Đóng gói hai bản của một shared API trong host và plugin ở production dễ tạo type identity mismatch. Bản trùng ở đây chỉ phục vụ mục đích chẩn đoán.

### 4.3 Class của plugin

**plugin-src/sample/plugin/Greeter.java**:

~~~java
package sample.plugin;

import sample.host.HostApi;

public final class Greeter {
    static {
        System.out.println("plugin initialized");
    }

    public String greet() {
        return "hello from " + HostApi.origin();
    }
}
~~~

Greeter phụ thuộc vào HostApi. Nó cũng có static initializer để chứng minh **loadClass chưa tự kích hoạt khởi tạo class**, còn reflective instance creation sẽ kích hoạt.

**Biên dịch quan trọng:** Greeter được compile chống lại HostApi của host, nhưng khi JVM nạp **binary name sample.host.HostApi** ở runtime, loader resolution và parent delegation mới quyết định Class nào được dùng trong trường hợp cụ thể. Vì hai bản HostApi có cùng public method signature, demo có thể gọi method mà không gặp NoSuchMethodError. Nếu API schema khác nhau, đây sẽ là một loại lỗi cần điều tra riêng.

## 5. Chương trình chính: in cây loader và từng nguồn Class

Tạo **src/sample/app/InspectLoaders.java** với nội dung đầy đủ:

~~~java
package sample.app;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.sql.DriverManager;
import java.util.Enumeration;

public final class InspectLoaders {
    private static String role(
            ClassLoader loader, ClassLoader plugin) {
        if (loader == null) {
            return "bootstrap";
        }
        if (loader == plugin) {
            return "plugin-loader";
        }
        if (loader == ClassLoader.getSystemClassLoader()) {
            return "application";
        }
        if (loader == ClassLoader.getPlatformClassLoader()) {
            return "platform";
        }
        return "other";
    }

    private static void tree(
            ClassLoader loader, ClassLoader plugin) {
        System.out.println("Loader ancestry:");

        int depth = 0;
        while (true) {
            System.out.println("  ".repeat(depth)
                    + role(loader, plugin));

            if (loader == null) {
                break;
            }

            loader = loader.getParent();
            depth++;
        }
    }

    private static String source(
            Class<?> c, Path host, Path jar) throws Exception {
        ProtectionDomain pd = c.getProtectionDomain();
        CodeSource cs =
                pd == null ? null : pd.getCodeSource();

        URL url = cs == null ? null : cs.getLocation();

        if (url == null) {
            return "unavailable";
        }

        // Fixture dùng URL file: từ thư mục hoặc JAR.
        Path actual = Path.of(url.toURI()).toRealPath();

        if (actual.equals(host.toRealPath())) {
            return "host-out";
        }
        if (actual.equals(jar.toRealPath())) {
            return "plugin.jar";
        }
        return "other";
    }

    public static void main(String[] args)
            throws Exception {

        Path host = Path.of("host-out");
        Path jar = Path.of("plugin.jar");
        URL jarUrl = jar.toUri().toURL();

        ClassLoader app =
                ClassLoader.getSystemClassLoader();

        try (URLClassLoader plugin =
                    new URLClassLoader(
                            "plugin-loader",
                            new URL[]{jarUrl},
                            app);
             URLClassLoader plugin2 =
                    new URLClassLoader(
                            "plugin-loader-2",
                            new URL[]{jarUrl},
                            app)) {

            tree(plugin, plugin);

            System.out.println("String loader = "
                    + role(String.class.getClassLoader(),
                           plugin));

            System.out.println("DriverManager loader = "
                    + role(DriverManager.class.getClassLoader(),
                           plugin));

            System.out.println("HostApi loader = "
                    + role(sample.host.HostApi.class
                            .getClassLoader(), plugin));

            Class<?> type =
                    plugin.loadClass("sample.plugin.Greeter");

            System.out.println("Greeter loader = "
                    + role(type.getClassLoader(), plugin));

            System.out.println("Greeter source = "
                    + source(type, host, jar));

            System.out.println("HostApi source = "
                    + source(sample.host.HostApi.class,
                             host, jar));

            Class<?> shared =
                    plugin.loadClass("sample.host.HostApi");

            System.out.println("shadow selected = "
                    + (shared == sample.host.HostApi.class));

            System.out.println("Greeter named module = "
                    + type.getModule().isNamed());

            System.out.println("String module = "
                    + String.class.getModule().getName());

            System.out.println("plugin class resource scheme = "
                    + type.getResource("Greeter.class")
                            .getProtocol());

            Enumeration<URL> paths = plugin.getResources(
                    "sample/host/HostApi.class");

            int n = 0;
            while (paths.hasMoreElements()) {
                paths.nextElement();
                n++;
            }

            System.out.println("shadow resource count = " + n);

            Class<?> again =
                    plugin.loadClass("sample.plugin.Greeter");

            System.out.println("same loader cache = "
                    + (type == again));

            Class<?> parallel =
                    plugin2.loadClass("sample.plugin.Greeter");

            System.out.println("second loader same Class = "
                    + (type == parallel));

            System.out.println("before instance");

            Object obj =
                    type.getConstructor().newInstance();

            System.out.println("greeting = "
                    + type.getMethod("greet").invoke(obj));
        }
    }
}
~~~

Đây là **chương trình độc lập hoàn chỉnh** trong package sample.app. Các method được tách để dễ hiểu khi debug.

### 5.1 Vì sao role() so object identity?

Code dùng:

~~~java
if (loader == ClassLoader.getSystemClassLoader()) {
    return "application";
}
~~~

thay vì so **loader.toString()** hoặc **loader.getClass().getName()**. Tên implementation của built-in loaders và identity hash thường thay đổi theo JVM/version; **so sánh identity object** đáng tin hơn để biết loader có phải đúng system/platform object trong chính tiến trình không.

**role() chỉ tạo nhãn cho demo.** Nếu runtime dùng thêm một loader tùy biến khác, nó có thể in **other**, không có nghĩa class lỗi. Với công cụ chẩn đoán production nên in cả **ClassLoader.getName()**, loại implementation và một định danh instance ổn định trong cùng tiến trình.

### 5.2 Vì sao dùng vòng lặp tree()?

~~~java
ClassLoader current = plugin;

while (true) {
    // In current.
    if (current == null) {
        break;
    }
    current = current.getParent();
}
~~~

Đây là snippet minh họa, không phải method hoàn chỉnh. Nó duyệt **parent chain** đến null. Với loader hierarchy hợp lệ, chuỗi parent hữu hạn.

**Không tạo một đối tượng BootstrapClassLoader Java giả.** Null là biểu diễn hữu dụng trong kết quả API của các platform JVMs như HotSpot.

Nếu cần tool tổng quát cho nhiều custom loaders, có thể giới hạn depth và thêm phát hiện cycle phòng trường hợp biểu diễn tùy biến không như giả định. Tuy nhiên, quan hệ parent của ClassLoader được thiết lập khi constructor chạy; thường không phải đồ thị người dùng tự sửa để tạo cycle.

### 5.3 getParent() khác loadClass()

**getParent()** chỉ trả thông tin về quan hệ loader; nó **không gọi loadClass()** hay nạp class. Đọc cây loader không tự khởi tạo Greeter.

**ClassLoader.loadClass(name)** mặc định kiểm tra class đã loaded, hỏi parent trước, rồi gọi findClass nếu parent không tìm thấy. Custom loaders có thể thay đổi chính sách này, vì thế một cây parent giống nhau **chưa đủ bảo đảm hành vi lookup giống nhau**.

### 5.4 CodeSource lấy từ đâu?

Method **source(c, host, jar)** sử dụng:

~~~java
ProtectionDomain pd = c.getProtectionDomain();
CodeSource cs = pd == null ? null : pd.getCodeSource();
URL url = cs == null ? null : cs.getLocation();
~~~

Các lệnh là snippet trong method.

**CodeSource.getLocation()** có thể trỏ đến một thư mục classes hoặc file JAR. Trong demo:

- HostApi từ **host-out/** → CodeSource location tương ứng thư mục host-out.
- Greeter do URLClassLoader đọc từ **plugin.jar** → location là file URL trỏ đến plugin.jar.

**CodeSource hoặc location có thể null**, đặc biệt với bootstrap/JDK classes, bytes được tự định nghĩa/custom code domains hoặc security/runtime contexts. Vì vậy không được tự gọi **.getLocation().toString()** mà bỏ null-check.

Method demo dùng **Path.of(url.toURI()).toRealPath()** vì biết trước hai nguồn hợp lệ là **file:** paths. Đây **không phải bộ chuyển đổi URL tổng quát**: URL có protocol **jrt:, jar:, http:** hoặc custom handler có thể không chuyển thành Path trực tiếp được.

### 5.5 Tại sao resource URL khác CodeSource?

Có hai câu hỏi khác nhau:

~~~java
type.getProtectionDomain()
        .getCodeSource().getLocation();
~~~

và:

~~~java
type.getResource("Greeter.class");
~~~

Dòng đầu hỏi **vị trí code source của class** do protection domain ghi nhận. Với URLClassLoader đọc từ JAR, nó thường là **file:/.../plugin.jar**.

Dòng thứ hai tìm **resource tương ứng class file**; với class nằm trong JAR, URL thường có dạng:

~~~text
jar:file:/.../plugin.jar!/sample/plugin/Greeter.class
~~~

Nó chứa đường entry trong JAR và có **protocol = jar**. Địa chỉ thật phụ thuộc đường filesystem, nên chương trình chỉ in protocol để kết quả dễ tái hiện.

**Lưu ý:** Class.getResource và ClassLoader.getResource không hoàn toàn có cùng quy tắc xử lý path: **Class.getResource("Greeter.class")** giải quyết tương đối theo package của class; **ClassLoader.getResource("sample/plugin/Greeter.class")** dùng absolute resource name với dấu slash giữa các segments, **không bắt đầu bằng /**.

### 5.6 Vì sao gọi getResources chứ không chỉ getResource?

Program gọi:

~~~java
plugin.getResources("sample/host/HostApi.class")
~~~

và đếm được **2** resource URLs: một từ **host-out**, một từ **plugin.jar**. Đây là dấu hiệu *có hai resources cùng path*, không phải JVM đã define hai Class HostApi trong cùng loader.

**getResource** thường dùng chính sách parent-first: parent resource có thể thắng. **getResources** có thể liệt kê nhiều URLs; trong ứng dụng thực sự còn cần xét duplicate entries, module encapsulation, custom resource delegation và launcher environment.

**Đừng kết luận “resource count = 2” đồng nghĩa “Class count = 2”.** Class identity chỉ được xác lập khi JVM định nghĩa Class theo loader, không phải khi JAR có một entry tương ứng.

## 6. Biên dịch và đóng gói

Đứng ở thư mục **loader-tree-lab** và tạo đủ các source files trên, thực hiện:

~~~bash
javac --release 17 -d host-out   src/sample/host/HostApi.java   src/sample/app/InspectLoaders.java

javac --release 17 -cp host-out -d plugin-out   plugin-src/sample/plugin/Greeter.java

javac --release 17 -d plugin-out   plugin-src/sample/host/HostApi.java

jar --create --file plugin.jar -C plugin-out .

java -cp host-out sample.app.InspectLoaders
~~~

Đây là lệnh nhiều dòng dùng **backslash** nối dòng phù hợp với bash trên Linux/macOS. Với Windows PowerShell, dùng một dòng cho mỗi lệnh:

~~~powershell
javac --release 17 -d host-out src/sample/host/HostApi.java src/sample/app/InspectLoaders.java
javac --release 17 -cp host-out -d plugin-out plugin-src/sample/plugin/Greeter.java
javac --release 17 -d plugin-out plugin-src/sample/host/HostApi.java
jar --create --file plugin.jar -C plugin-out .
java -cp host-out sample.app.InspectLoaders
~~~

**Tại sao compile Greeter trước bản HostApi của plugin?** Để nó được compile chống lại API của host qua **-cp host-out**. Sau đó ta bổ sung bản HostApi khác vào plugin-out và đóng gói. Trình biên dịch ghi symbolic reference tới **sample.host.HostApi**, còn defining loader của HostApi được JVM lựa chọn lúc runtime.

**Quan trọng về classpath:** Chỉ đưa **host-out** vào application classpath khi chạy chương trình. Không thêm **plugin.jar** vào application classpath trong lệnh demo chính; nếu thêm, application parent có thể tự load Greeter trước và phép thử ownership thay đổi.

**Vì sao dùng --release 17?** Đây là một chương trình Java 17+; dùng --release 17 giúp bytecode/class-file target tương thích Java 17 ngay cả khi biên dịch bằng JDK 21. Constructor **URLClassLoader(String name, URL[] urls, ClassLoader parent)** có từ Java 9 nên hợp lệ.

## 7. Output thực tế và giải thích từng dòng

Đây là **stdout đã kiểm chứng trên OpenJDK 21** với source tree và lệnh chạy như trên:

~~~text
Loader ancestry:
plugin-loader
  application
    platform
      bootstrap
String loader = bootstrap
DriverManager loader = platform
HostApi loader = application
Greeter loader = plugin-loader
Greeter source = plugin.jar
HostApi source = host-out
shadow selected = true
Greeter named module = false
String module = java.base
plugin class resource scheme = jar
shadow resource count = 2
same loader cache = true
second loader same Class = false
before instance
plugin initialized
greeting = hello from host
~~~

### 7.1 Dòng đầu: cây loader

~~~text
plugin-loader
  application
    platform
      bootstrap
~~~

Ý nghĩa:

- Plugin URLClassLoader có **parent là application/system loader**.
- System loader có **parent là platform loader** trong môi trường thử nghiệm.
- Platform loader có parent được biểu diễn bằng **null/bootstrap**.
- Độ thụt đầu dòng chỉ là phần code **"  ".repeat(depth)**, không phải tính năng vẽ cây của JVM.

**Đây không phải toàn bộ loaders đang tồn tại trong JVM.** Chương trình chỉ in parent chain bắt đầu từ **plugin**, không quét các loaders khác do JVM/framework đã tạo.

### 7.2 String và DriverManager

~~~text
String loader = bootstrap
DriverManager loader = platform
~~~

**java.lang.String** thuộc các core platform classes do bootstrap loader định nghĩa trên OpenJDK. **java.sql.DriverManager** được platform loader định nghĩa trong JVM thử nghiệm, cho một ví dụ platform class không phải bootstrap.

Một JVM khác có thể có mapping chi tiết không hoàn toàn giống; đừng viết assertion portable kiểu “mọi JDK phải có DriverManager loader là platform” trừ khi môi trường chạy đã được kiểm soát và xác nhận.

### 7.3 HostApi thuộc application

~~~text
HostApi loader = application
HostApi source = host-out
~~~

**HostApi.class** được compile và load từ **host-out**, do application loader. Đây là **Class object được host sử dụng để tham chiếu API chung**.

Trong demo, đối tượng application loader chính là parent của plugin, nên khi plugin yêu cầu tên này, parent đã nhìn thấy class.

### 7.4 Greeter thuộc plugin.jar

~~~text
Greeter loader = plugin-loader
Greeter source = plugin.jar
~~~

Application parent không có **sample.plugin.Greeter**, vì plugin.jar không nằm trên application classpath. URLClassLoader plugin tìm class trong URL duy nhất là plugin.jar và tự định nghĩa Greeter.

**getClassLoader() và CodeSource hỗ trợ lẫn nhau:**

- getClassLoader cho biết **ai định nghĩa**.
- CodeSource cho biết **nguồn artifact được ghi nhận**.
- Không thể hoán đổi vai trò của hai API; hai loaders khác nhau có thể đọc cùng một JAR và tạo hai Class khác nhau.

### 7.5 Plugin có bản trùng HostApi nhưng không được chọn

~~~text
shadow selected = true
greeting = hello from host
~~~

Ở đây biến **shadow selected** là tên in để kiểm tra **plugin.loadClass("sample.host.HostApi")** có trả chính Class HostApi của host hay không. Giá trị **true** nghĩa **parent/host thắng**, không phải plugin-shadow thắng.

Dòng **greeting = hello from host** chứng minh code Greeter đã gọi đúng implementation HostApi của host. Bản **plugin-shadow** nằm trong plugin.jar nhưng không phải class được chọn cho lookup này.

Đây là **parent-first delegation** phù hợp với mặc định ClassLoader.loadClass. Đừng sửa thông báo thành “plugin-shadow selected = true” vì nó sẽ làm người học hiểu ngược kết quả.

### 7.6 Module information

~~~text
Greeter named module = false
String module = java.base
~~~

Greeter được định nghĩa từ JAR qua URLClassLoader trong **unnamed module** của loader plugin, vì demo không dùng module-info.java hoặc JPMS ModuleLayer.

String thuộc **named module java.base**. ClassLoader identity và module membership là hai lớp thông tin khác nhau: một loader có thể định nghĩa nhiều named modules theo module layer, nhưng trong fixture này Greeter ở unnamed module.

**Class.getModule() không cho biết JAR filename**. Dùng CodeSource hoặc resource URL để điều tra artifact.

### 7.7 Một loader dùng lại class đã nạp

~~~text
same loader cache = true
~~~

Hai lời gọi **plugin.loadClass("sample.plugin.Greeter")** trả cùng Class object. Thuật toán ClassLoader.loadClass mặc định kiểm tra class đã loaded/initiated và không gọi defineClass lần hai cho cùng name.

Nếu một custom loader cố define class trùng trong cùng loader, có thể ném **LinkageError**, chứ không phải tạo một bản cập nhật method body.

### 7.8 Hai loader cùng JAR tạo hai Class khác nhau

~~~text
second loader same Class = false
~~~

**plugin** và **plugin2** đều có URL trỏ đến cùng **plugin.jar**, nhưng là **hai URLClassLoader objects khác nhau**. Khi mỗi loader tự định nghĩa sample.plugin.Greeter, ta có hai Class objects khác runtime identity.

Khi ứng dụng plugin cần cast object về shared interface, interface/DTO thường nên đến từ **một defining loader chung**. Nếu plugin2 tự define một bản interface API trùng tên với host, cast có thể thất bại dù source giống nhau.

### 7.9 loadClass chưa chạy static initializer

~~~text
before instance
plugin initialized
greeting = hello from host
~~~

Chương trình gọi loadClass, kiểm tra Class/CodeSource/Resource rồi in **before instance**. Chỉ khi:

~~~java
type.getConstructor().newInstance();
~~~

được thực hiện, Greeter mới bị khởi tạo, static block chạy và in **plugin initialized**.

**Loading không đồng nghĩa initialization**. Cờ resolve trong ClassLoader cũng không phải yêu cầu tự chạy class initializer; chỉ active use theo JLS mới kích hoạt initialization phù hợp.

## 8. Bài thực hành thứ hai: cùng JAR nhưng thay parent

Mục tiêu: thay đúng một yếu tố **parent loader** và quan sát kết quả. Lần này custom URLClassLoader có parent là **platform loader**, không phải application loader.

Vì platform loader không thấy **host-out** trong fixture, child có thể tự định nghĩa **sample.host.HostApi** bản đóng trong plugin.jar.

Tạo **src/sample/app/IsolatedLoaderDemo.java**:

~~~java
package sample.app;

import java.net.URLClassLoader;
import java.net.URL;
import java.nio.file.Path;
import sample.host.HostApi;

public class IsolatedLoaderDemo {
    public static void main(String[] args) throws Exception {
        URL jar = Path.of("plugin.jar").toUri().toURL();

        try (URLClassLoader isolated =
                new URLClassLoader(
                        "isolated",
                        new URL[]{jar},
                        ClassLoader.getPlatformClassLoader())) {

            Class<?> shadow =
                    isolated.loadClass("sample.host.HostApi");

            Class<?> greeter =
                    isolated.loadClass("sample.plugin.Greeter");

            System.out.println(
                    "host and isolated HostApi same = "
                    + (shadow == HostApi.class));

            System.out.println(
                    "shadow owner is isolated = "
                    + (shadow.getClassLoader() == isolated));

            System.out.println(
                    "greeter owner is isolated = "
                    + (greeter.getClassLoader() == isolated));

            Object g =
                    greeter.getConstructor().newInstance();

            System.out.println(
                    "greeting = "
                    + greeter.getMethod("greet").invoke(g));
        }
    }
}
~~~

Biên dịch/chạy từ thư mục project:

~~~bash
javac --release 17 -cp host-out -d host-out   src/sample/app/IsolatedLoaderDemo.java

java -cp host-out sample.app.IsolatedLoaderDemo
~~~

Windows PowerShell dùng một dòng javac:

~~~powershell
javac --release 17 -cp host-out -d host-out src/sample/app/IsolatedLoaderDemo.java
java -cp host-out sample.app.IsolatedLoaderDemo
~~~

**Stdout đã chạy trên OpenJDK 21:**

~~~text
host and isolated HostApi same = false
shadow owner is isolated = true
greeter owner is isolated = true
plugin initialized
greeting = hello from plugin-shadow
~~~

### 8.1 Vì sao khác chương trình đầu?

Trong chương trình đầu:

~~~text
PluginLoader -> ApplicationLoader -> Platform -> Bootstrap
                   |
                   +-- biết HostApi từ host-out
~~~

Trong chương trình thứ hai:

~~~text
IsolatedLoader -> Platform -> Bootstrap
      |
      +-- tự tìm HostApi trong plugin.jar
~~~

Vì application loader **không nằm trên parent chain của isolated**, việc code launcher dùng **HostApi.class** từ host-out không làm isolated bắt buộc lấy Class đó.

**Class equality = false** phản ánh hai defining loaders khác nhau: application loader và isolated loader.

**Kết quả "plugin-shadow"** cho thấy Greeter trong isolated dùng HostApi bản đóng trong plugin.jar.

### 8.2 Không nhầm parent platform với “full sandbox”

Việc chọn platform làm parent giúp minh họa cô lập class lookup, **không phải security sandbox**. Plugin có thể truy cập những Java APIs mà runtime/module constraints cho phép. Nếu chạy code không tin cậy, cần một mô hình isolation khác (ví dụ process/container sandbox) với quyền hạn phù hợp.

Nếu plugin cần gọi host API, chuyển parent sang platform có thể làm shared API không còn visible. Ví dụ thứ hai chạy được vì plugin.jar có bản HostApi của riêng nó; trong thực tế điều này có thể dẫn đến **ClassCastException/LinkageError** khi host-plugin boundary yêu cầu cùng Class identity.

## 9. Đọc CodeSource một cách an toàn trong chương trình thật

CodeSource là một tín hiệu hữu ích nhưng **không phải lúc nào cũng có URL**.

### 9.1 Helper cho code production

~~~java
static String codeSourceLocation(Class<?> type) {
    try {
        java.security.ProtectionDomain pd =
                type.getProtectionDomain();

        if (pd == null) {
            return "(no protection domain)";
        }

        java.security.CodeSource cs = pd.getCodeSource();

        if (cs == null || cs.getLocation() == null) {
            return "(no code source URL)";
        }

        return cs.getLocation().toExternalForm();

    } catch (SecurityException denied) {
        return "(access denied)";
    }
}
~~~

Đây là **method hoàn chỉnh** có thể đặt trong helper class, nhưng không có main riêng. Nó không tự động đọc/validate nội dung URL.

### 9.2 Khi nào CodeSource có thể khác mong đợi?

- **Bootstrap/JDK classes:** CodeSource thường không phải URL file JAR thông thường.
- **Custom ClassLoader dùng defineClass với ProtectionDomain tùy chỉnh:** Có thể không có location, hoặc location do loader đặt.
- **In-memory generated classes, proxies, hidden classes:** Nguồn vật lý có thể không tồn tại dưới dạng .class ở thư mục/JAR mà bạn trỏ được.
- **Named modules/runtime image:** Location có thể dùng **jrt:** hoặc không có source URL theo cách bạn mong đợi.
- **Container/nested JAR/custom URL protocols:** URL có thể biểu diễn vị trí logic thay vì Path file đơn giản.
- **Security policy:** Quyền truy cập ProtectionDomain/CodeSource có thể bị giới hạn tùy runtime.

**Không dùng CodeSource thay cơ chế xác thực file.** Nó là metadata liên quan protection domain, không phải bằng chứng cryptographic rằng bytes xuất phát từ artifact đã ký và đáng tin.

### 9.3 Không in đường dẫn bí mật ra production logs

CodeSource location có thể chứa đường dẫn home, build workspace, container mount, mạng hoặc URL nội bộ. Với bài thực hành ta chuẩn hóa thành **host-out/plugin.jar** để stdout ổn định. Với logs production, cân nhắc quyền truy cập và mức thông tin được phép xuất.

## 10. Resource lookup: thấy class file không có nghĩa Class đã được nạp

Hai API:

~~~java
ClassLoader loader = Greeter.class.getClassLoader();
java.net.URL url =
        loader.getResource("sample/plugin/Greeter.class");
~~~

Đây là snippet không độc lập và cần lớp Greeter visible ở compile-time. Nó tìm **resource URL**, không phải trả Class<?>.

**Sự khác nhau:**

| API | Trả về | Ý nghĩa |
| --- | --- | --- |
| **clazz.getClassLoader()** | ClassLoader hoặc null | Defining loader của runtime Class. |
| **clazz.getModule()** | Module | Named/unnamed module của Class. |
| **clazz.getProtectionDomain().getCodeSource()** | CodeSource hoặc null | Metadata nguồn/protection domain. |
| **clazz.getResource("X.class")** | URL hoặc null | Tìm resource tương đối với package của clazz. |
| **loader.getResource("p/X.class")** | URL hoặc null | Tìm resource theo tên /-separated qua loader. |
| **loader.getResources("p/X.class")** | Enumeration URL | Các resource matches có thể gồm parent và child. |
| **loader.loadClass("p.X")** | Class<?> | Tìm runtime class theo binary name. |

**Lưu ý JPMS:** Đối với resources trong named modules, **ClassLoader.getResource** còn có quy tắc package openness/encapsulation. Đừng giả định mọi resource bên trong named module đều được trả về chỉ vì JAR có entry ấy. Class.getResource có ngữ nghĩa module-aware riêng.

**Kiểm tra được đường dẫn không có nghĩa loadClass sẽ tự dùng đúng resource URL ấy:** URLClassLoader parent-first có thể chọn Class của parent mặc dù plugin JAR cũng chứa resource cùng tên.

## 11. In cây nhiều loaders trong ứng dụng phức tạp

Nếu hệ thống có nhiều plugin loaders, có thể ghi nhận từng loader theo một cấu trúc:

~~~text
Name: plugin-A
  implementation: java.net.URLClassLoader
  parent: application
  known artifact URL: file:/.../pluginA.jar
  class sample.plugin.PluginA:
      defining loader = plugin-A
      module = unnamed
      code source = pluginA.jar

Name: plugin-B
  implementation: java.net.URLClassLoader
  parent: application
  known artifact URL: file:/.../pluginB.jar
  class sample.plugin.PluginB:
      defining loader = plugin-B
      module = unnamed
      code source = pluginB.jar
~~~

Đây là **mẫu báo cáo**, không phải stdout của fixture.

Với nhiều loaders, bạn cần:

1. Đặt **tên hoặc deployment ID** khi tạo loader.
2. Lưu **owner** (host/plugin manager) của loader.
3. Ghi parent chain cho loader mới.
4. In **Class.getClassLoader()** cho từng class quan trọng.
5. So sánh identity của shared APIs qua **==**.
6. Liên hệ CodeSource/resource URLs với expected artifacts.
7. Ghi module name, layer nếu dùng JPMS.
8. Kiểm soát lifecycle/close của URLClassLoader khi deployment dừng.

**Không duyệt JVM để liệt kê mọi loader bằng cách giả định mọi loader nằm dưới system loader.** Những loader có parent là bootstrap/platform hoặc peer delegation có thể không được tìm bằng cách đi từ system parent chain.

### 11.1 Hiển thị parent chain theo kiểu không phụ thuộc toString()

Nếu dùng output như:

~~~text
jdk.internal.loader.ClassLoaders$AppClassLoader@30946e09
~~~

đây là class name và identity hash liên quan JVM implementation. Nó hữu ích để phân biệt instance trong một process, nhưng không ổn định giữa các lượt chạy. Không nên viết integration tests so sánh nguyên văn hash hoặc implementation class name.

Có thể thêm một stable label riêng như **plugin-loader, plugin-loader-2, deployment-v1** và đối chiếu identity trong chương trình.

### 11.2 Thread Context ClassLoader là một trục khác

~~~java
ClassLoader tccl =
        Thread.currentThread().getContextClassLoader();
~~~

TCCL không nhất thiết bằng loader định nghĩa class đang chạy. Frameworks như ServiceLoader có thể dùng TCCL để tìm providers và resources.

Nếu đang debug SPI không tìm thấy provider, **Class.getClassLoader của service type và TCCL** đều hữu ích, nhưng không thay thế class identity checks. Việc set TCCL tạm phải **restore previous** trong finally nếu thread thuộc host pool.

## 12. Chẩn đoán khi output khác dự kiến

### 12.1 Greeter loader là application, không phải plugin

Nguyên nhân thường là bạn vô tình đưa **plugin.jar** hoặc plugin-out vào application classpath:

~~~bash
# Không dùng cho expected demo output:
java -cp "host-out:plugin.jar" sample.app.InspectLoaders
~~~

Vì URLClassLoader parent-first, application loader thấy Greeter trước, nên plugin.loadClass trả class của parent. Trong trường hợp này **CodeSource** của Greeter có thể vẫn là plugin.jar nhưng **defining loader** không còn là plugin.

**Bài học:** artifact source giống không bảo đảm loader owner giống.

### 12.2 Greeter source = unavailable

Đừng vội kết luận class “không có file”. Có thể runtime sử dụng custom defineClass, in-memory bytes, không có CodeSource, hoặc security/module context che thông tin.

Kiểm tra defining loader, resource lookup, class loading logs và thiết kế ProtectionDomain của loader đó.

### 12.3 FileNotFoundException hoặc NoSuchFileException khi chạy

Ví dụ dùng đường **plugin.jar** và **host-out** tương đối với **current working directory**. Nếu chạy trong IDE với working directory khác, sẽ không tìm file.

Khắc phục: chạy ở project root hoặc đổi cấu hình working directory/absolute input paths. Đừng dựa vào classpath tự tìm file **plugin.jar** nếu Path.of("plugin.jar") đang tìm trực tiếp theo filesystem.

### 12.4 ClassNotFoundException: sample.plugin.Greeter

Kiểm tra:

~~~bash
jar --list --file plugin.jar
~~~

Phải có entry:

~~~text
sample/plugin/Greeter.class
~~~

Nếu không có, xem javac -d plugin-out và jar -C plugin-out. Nếu có, xác nhận plugin URL dùng đúng **Path.of("plugin.jar").toUri().toURL()**, class name dùng dấu chấm và parent delegation.

### 12.5 ClassCastException dù tên class trùng

Nếu hai URLClassLoaders tự define cùng binary name, **Class objects khác nhau**. Một instance do plugin loader thứ nhất tạo không tự động cast thành type do plugin2 define.

Với shared host-plugin contracts, đặt interface/DTO trong parent loader và tránh đóng bản sao vào child-first packages.

### 12.6 NoClassDefFoundError: sample/host/HostApi

Nếu dùng platform parent và plugin.jar **không còn bản HostApi** nhưng Greeter vẫn phụ thuộc nó, loader isolated không nhìn thấy HostApi của host-out. Có thể loader cần parent khác hoặc API package phải được cho phép delegate tới loader shared.

**Sửa kiến trúc API boundary** thay vì chỉ nuốt exception rồi trả Class không tương thích.

### 12.7 Output DriverManager loader khác platform

Quan sát này phụ thuộc runtime JDK. Kiểm tra vendor/version và built-in ClassLoader mapping. Bài demo vẫn đúng nếu Class được load bởi loader khác; chỉ cần sửa expected output cho đúng môi trường được hỗ trợ và không đồng nhất mọi platform classes với một loader cố định.

### 12.8 Nguồn resource là jar: nhưng CodeSource file:

Đây là điều thường gặp và bình thường:

~~~text
CodeSource: file:/.../plugin.jar
Resource:   jar:file:/.../plugin.jar!/sample/plugin/Greeter.class
~~~

CodeSource chỉ vị trí artifact; resource URL chỉ entry riêng bên trong JAR. Hai URL khác protocol không có nghĩa hai class versions.

### 12.9 Không thấy static initializer chạy khi loadClass

Theo JLS, class initialization là bước khác với loading. Ví dụ chỉ chạy initializer lúc reflective construction. Không dùng **getClassLoader()**, **getModule()** hoặc **getResource()** để khẳng định static initializer đã thực thi.

## 13. Bài kiểm thử từ ví dụ

Các assertions nên tập trung vào **tính chất**, không phải string hash của loaders:

~~~java
// Snippet trong scope có plugin, type và plugin2:
assert type.getClassLoader() == plugin;
assert plugin.loadClass("sample.host.HostApi")
        == sample.host.HostApi.class;
assert type != plugin2.loadClass(
        "sample.plugin.Greeter");
~~~

Để bật assert trong Java:

~~~bash
java -ea -cp host-out sample.app.InspectLoaders
~~~

Trong bài chính, các assertions không được thêm vào launcher, nhưng nếu bạn đưa snippet vào scope phù hợp, ba thuộc tính trên đúng theo setup đã hướng dẫn. Lưu ý **assert disabled mặc định** trừ khi có **-ea**, nên integration tests quan trọng thường dùng assertions của framework kiểm thử (JUnit) hoặc explicit checks không tùy thuộc JVM assertion flags.

### 13.1 Không kiểm thử bằng System.identityHashCode cố định

**System.identityHashCode(loader)** có thể hữu ích để gắn nhãn identity trong một lần chạy, nhưng giá trị cụ thể không ổn định giữa processes. So **==** khi cùng JVM, và log thêm deployment ID có nghĩa với nghiệp vụ.

### 13.2 Kiểm thử Source location

Với fixture này, code dùng **toRealPath** để so CodeSource với host-out và plugin.jar, tránh khác biệt relative/absolute path của cùng file.

Trên Windows, nếu directory case, symlinks, permissions hoặc file layout khác, Path equality có thể cần xử lý phù hợp. Đây là **code fixture** cho hai file URLs, không phải một phép xác thực nguồn lớp có thể dùng cho mọi protocol.

## 14. Mở rộng thực hành

### Bài tập A — thêm một class chỉ có trong plugin JAR

Tạo một class mới **sample.plugin.Helper**, compile vào plugin-out rồi đóng lại plugin.jar. In:

~~~java
Class<?> helper =
        plugin.loadClass("sample.plugin.Helper");

System.out.println(helper.getClassLoader() == plugin);
~~~

Dự kiến **true** nếu parent không thấy class đó và loader plugin còn mở. Đặt code trong scope có URLClassLoader plugin của InspectLoaders.

### Bài tập B — quan sát CodeSource không có URL

Trong main, in:

~~~java
System.out.println(
        String.class.getProtectionDomain()
                .getCodeSource());
~~~

Kết quả có thể null hoặc dạng runtime-specific. Đừng viết exact-output assertion portable. Trong application thật, null-check ProtectionDomain trước nếu cần.

### Bài tập C — thêm plugin.jar vào application classpath

Chạy thử launcher với plugin.jar bổ sung vào classpath. Quan sát **Greeter loader**, **Class identity**, static initializer và **resource counts**; so với lần chạy plugin.jar chỉ nằm ở URLClassLoader.

**Giải thích:** Parent-first khiến application loader có thể định nghĩa Greeter, dù chương trình vẫn gọi plugin.loadClass. Tuy nhiên output **source = plugin.jar** vẫn có thể giống nhau. Đây là minh họa rõ rằng **source artifact và defining loader** là hai trục riêng.

### Bài tập D — tạo hai phiên bản plugin.jar

Đóng gói Greeter method trả hai kết quả khác nhau dưới hai JAR URLs, tạo loader riêng cho từng JAR, rồi so **Class identity** và kết quả method. Không cố redefine Greeter trong cùng loader bằng defineClass lần thứ hai. Khi cần hot reload, tạo loader mới và quản lý lifecycle.

### Bài tập E — in module name/unnamed module

Kiểm tra:

~~~java
System.out.println(type.getModule().isNamed());
System.out.println(type.getModule().getName());
~~~

Với URLClassLoader thường trên classpath-style JAR của fixture, output là **false** rồi **null** vì Greeter ở unnamed module. Với **String.class**, **getModule().getName()** là **java.base**.

### Bài tập F — đối chiếu ClassLoader.getResource và Class.getResource

Dùng resource path **sample/plugin/Greeter.class** cho ClassLoader và **Greeter.class** cho Class.getResource tương đối package. In protocols và kiểm tra có cùng entry theo logic hay không.

Không thêm dấu slash đầu vào **ClassLoader.getResource** theo cách tùy tiện, vì tên resource của ClassLoader theo contract dùng /-separated path không có slash đầu.

## 15. Câu hỏi tự kiểm tra

**1.** Trong HotSpot, tại sao **String.class.getClassLoader()** trả null?

**Đáp án:** Bootstrap loader là loader tích hợp JVM và thường được biểu diễn bằng null, không phải do String chưa được nạp.

**2.** **getParent()** có liệt kê toàn bộ loaders hiện có trong JVM không?

**Đáp án:** Không. Nó đi theo parent chain từ loader đang xét, không phải danh sách toàn bộ loader objects.

**3.** Một ClassLoader được gọi **loadClass** có chắc là defining loader của Class trả về không?

**Đáp án:** Không. Nó có thể delegate sang parent và chỉ là initiating loader trong ngữ cảnh liên quan.

**4.** API nào trả defining loader?

**Đáp án:** **Class.getClassLoader()** (có thể null cho bootstrap classes).

**5.** API nào cho code source location của Class?

**Đáp án:** **Class.getProtectionDomain().getCodeSource().getLocation()**, với null-check từng bước.

**6.** Parent và plugin JAR cùng có HostApi. Tại sao bản host thắng trong demo đầu?

**Đáp án:** URLClassLoader mặc định parent-first và có application loader làm parent, nơi HostApi đã có trên classpath.

**7.** Vì sao getResources tìm được hai **HostApi.class** URLs nhưng plugin.loadClass chỉ trả một Class object?

**Đáp án:** Resource lookup có thể liệt kê cả hai files; class lookup tuân theo delegation và trả một Class đã được định nghĩa/chọn, không define mỗi resource thành một Class.

**8.** **CodeSource** và **Class.getResource** có luôn cùng URL không?

**Đáp án:** Không. Một cái có thể là file:/.../plugin.jar, cái kia là jar:file:/.../plugin.jar!/path/Class.class.

**9.** Hai URLClassLoader mới cùng đọc một plugin.jar. Tại sao Greeter Class equality false?

**Đáp án:** Defining loader khác, nên hai runtime Class identities khác nhau.

**10.** **loadClass** có chạy static initializer không?

**Đáp án:** Không tự làm vậy. Initialization xảy ra theo các quy tắc active use khác, ví dụ tạo instance bằng reflection trong demo.

**11.** Loader isolated dùng parent platform nạp HostApi bản nào?

**Đáp án:** Nó nạp bản trong plugin.jar, vì platform parent không thấy bản của host-out trong fixture.

**12.** Một JAR có class đó nhưng chương trình vẫn báo ClassNotFoundException — cần kiểm tra gì?

**Đáp án:** Đúng binary name, đúng path trong JAR, URL tạo loader, parent delegation, loader đã close chưa, module encapsulation và runtime classpath/module path.

**13.** Có thể xác nhận Class bị unload chỉ bằng việc đóng URLClassLoader và gọi getClassLoader trước đó không?

**Đáp án:** Không. URLClassLoader.close chỉ đóng khả năng tìm class/resources mới của chính loader và tài nguyên do nó sở hữu; existing Class/instances vẫn có thể sống cho đến khi không còn strong references và JVM unload sau đó.

**14.** Khi log toàn bộ CodeSource URL, rủi ro gì?

**Đáp án:** Có thể lộ đường filesystem hoặc hạ tầng nội bộ; đồng thời URL không phải bảo đảm mật mã về nguồn class thực sự đáng tin.

## 16. Tóm tắt thực hành

~~~text
Tạo host-out chứa HostApi
               |
               v
Tạo plugin.jar chứa Greeter + HostApi trùng tên
               |
               v
AppLoader (parent) ------> HostApi bản host
               ^
               |
Plugin URLClassLoader ---> Greeter từ plugin.jar
               |
               +-- hỏi HostApi -> parent thắng
               |
               +-- class resource URLs có thể có 2 bản
               |
               v
Class.getClassLoader() => WHO defined?
Class.getModule()      => IN WHICH module?
CodeSource location    => SOURCE artifact metadata?
Class.getResource()    => RESOURCE entry URL?
ClassLoader.getParent()=> PARENT chain?
               |
               v
Tạo URLClassLoader khác cho plugin.jar
               |
               v
Greeter Class identity khác
~~~

**Ghi nhớ:**

- **Cây loader** giải thích parent relationships, không đầy đủ runtime graph.
- **Class.getClassLoader()** là cách trực tiếp hỏi defining loader của Class object.
- **CodeSource và resource URL** hỗ trợ tìm artifact nhưng không thay thông tin loader identity.
- **Parent-first** có thể khiến class thực sự lấy từ host ngay cả khi plugin JAR có file cùng tên.
- **Hai loader đọc cùng một JAR vẫn có thể tạo hai Class khác nhau**.
- **loadClass không tự initialize class**; active use như newInstance mới khiến static initializer trong fixture chạy.
- Khi chẩn đoán ứng dụng thật, in đồng thời **loader identity, module, CodeSource, resource URL và class-loading logs** thay vì chỉ nhìn tên class.

### Tài liệu tham khảo chính thức

- Java SE 21 **ClassLoader API** (getParent, loadClass, getResource, getSystemClassLoader): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 21 **URLClassLoader API** (constructors, class/resource loading, close): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html
- Java SE 21 **Class API** (getClassLoader/getModule/getProtectionDomain/getResource): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html
- Java SE 21 **ProtectionDomain API**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/ProtectionDomain.html
- Java SE 21 **CodeSource API**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/CodeSource.html
- Java SE 21 **JLS §12.2: Loading Classes and Interfaces**: https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.2
- Java SE 21 **JVMS §5.3: Creation and Loading**: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-exercises-002">Đọc cấu hình từ thư mục và JAR bằng API resource</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc cấu hình từ thư mục và JAR bằng API resource trong Java

## 1. Mục tiêu thực hành

Cùng một ứng dụng Java cần đọc file cấu hình khi chạy trong IDE, bằng lệnh `java -cp` với thư mục chứa class, và sau khi đóng gói thành file `.jar`. Nếu mã viết:

~~~java
Path path = Path.of("resources/config/app.properties");
~~~

thì đây là **đường dẫn hệ thống tệp tương đối với working directory**, không phải đường dẫn classpath. Khi triển khai, `resources` có thể không tồn tại dưới dạng thư mục và file có thể nằm **bên trong JAR**. Vì vậy giải pháp thích hợp cho cấu hình **đóng gói cùng ứng dụng** là sử dụng các **resource APIs**:

- `ClassLoader.getResource(name)` để tìm một `URL` tới resource.
- `ClassLoader.getResourceAsStream(name)` để mở luồng đọc bytes, bất kể resource nằm trong thư mục classpath hay entry của JAR.
- `Class.getResource(name)` và `Class.getResourceAsStream(name)` khi muốn định vị tương đối với package của một class.
- `Properties.load(Reader)` hoặc định dạng parser khác để đọc dữ liệu sau khi có luồng.

**Mục tiêu cuối bài:** Tự tạo một ứng dụng Java 17+, đọc cùng file cấu hình bằng một chương trình duy nhất, chạy được hai cách (directory/JAR), quan sát URL `file:` so với `jar:`, xử lý file không tồn tại, hiểu quy tắc dấu `/`, tránh chuyển URL của JAR thành `File`, và kiểm tra resource khi đóng gói Maven/Gradle.

## 2. Resource khác file trên hệ thống tệp như thế nào?

Một **resource** ở đây là dữ liệu được nhận diện bởi một **tên logic trong phạm vi classpath/module**, ví dụ `config/app.properties`, `demo/local-note.txt`, ảnh/logo hoặc tệp JSON. Một entry resource có thể được lấy từ:

~~~text
Classpath root là thư mục:
out/classes/
├── demo/
│   └── ResourceConfigDemo.class
├── config/
│   └── app.properties
└── demo/
    └── local-note.txt

Hoặc classpath root là JAR:
resource-demo.jar
├── META-INF/MANIFEST.MF
├── demo/ResourceConfigDemo.class
├── config/app.properties
└── demo/local-note.txt
~~~

Ở sơ đồ đầu, thư mục `demo/` chỉ được hiểu là **một thư mục chung**: hai entry `ResourceConfigDemo.class` và `local-note.txt` đều nằm trong đó; không cần tạo hai thư mục `demo`.

**Classpath root** là vị trí mà trình nạp class tìm tên phân cấp:

- Tên class `demo.ResourceConfigDemo` tương ứng entry `demo/ResourceConfigDemo.class`.
- Tên resource `config/app.properties` tương ứng entry `config/app.properties`.
- Không bao gồm tiền tố `src/`, `resources/` hoặc `out/classes/` trong tên resource nếu chúng chỉ là source/build directories.

Nếu chương trình chỉ dùng:

~~~java
Files.newInputStream(Path.of("config/app.properties"));
~~~

nó sẽ đọc file từ working directory và **chỉ chạy khi đúng file vật lý đang tồn tại ở vị trí đó**. Ngược lại:

~~~java
InputStream stream =
        ResourceConfigDemo.class.getClassLoader()
                .getResourceAsStream("config/app.properties");
~~~

sẽ tìm resource trong những nguồn mà class loader nhìn thấy. Đây là **đoạn minh họa** đặt trong class `ResourceConfigDemo`; phần 5 có mã hoàn chỉnh với xử lý null và đóng stream.

## 3. Bốn API quan trọng và quy tắc tên resource

| API | Đối số tên | Giá trị trả về |
| --- | --- | --- |
| `loader.getResource("config/app.properties")` | Tên tương đối **classpath root**, không có `/` đầu | `URL` hoặc `null` |
| `loader.getResourceAsStream("config/app.properties")` | Như trên | `InputStream` hoặc `null` |
| `SomeClass.class.getResource("/config/app.properties")` | Bắt đầu `/` ⇒ từ classpath/module root theo ngữ cảnh API | `URL` hoặc `null` |
| `SomeClass.class.getResource("local-note.txt")` | Không `/` ⇒ tương đối với package của `SomeClass` | `URL` hoặc `null` |

Giả sử `SomeClass` nằm trong package `demo`:

~~~text
SomeClass.class.getResource("local-note.txt")
    -> demo/local-note.txt

SomeClass.class.getResource("/demo/local-note.txt")
    -> demo/local-note.txt

SomeClass.class.getResource("/config/app.properties")
    -> config/app.properties

SomeClass.class.getClassLoader()
    .getResource("config/app.properties")
    -> config/app.properties
~~~

**Một lỗi cực phổ biến:**

~~~java
loader.getResourceAsStream("/config/app.properties");
~~~

Với `ClassLoader`, tên chuẩn phải là `config/app.properties` **không có slash đầu**. `Class#getResource` là API **khác**, nó xử lý slash đầu để biểu thị absolute name. Không có một quy tắc chung áp dụng nguyên xi cho mọi method có tên `getResource`.

### 3.1. Resource names dùng slash

Kể cả chạy Windows, resource name là đường dẫn **phân cấp với dấu `/`**, không phải `\` hoặc đường dẫn ổ `C:\`. Ví dụ đúng:

~~~text
config/app.properties
templates/email/welcome.html
demo/local-note.txt
~~~

Tên `"config\\app.properties"` trong Java source không phải đường resource portable.

### 3.2. Khi nào nên dùng Class thay vì ClassLoader?

- Dùng `MyClass.class.getResource("file.txt")` khi file gắn với package chứa class.
- Dùng `MyClass.class.getResource("/config/app.properties")` cho một resource dưới root.
- Dùng `MyClass.class.getClassLoader().getResourceAsStream("...")` khi mục tiêu là tìm trong lookup scope của defining class loader.
- Với ứng dụng named module, semantics khác classpath truyền thống: `Class#getResource` có thể tìm trong module của class và tuân thủ các quy tắc encapsulation; không đồng nhất mọi overload với một global classpath search.

Nên chọn API có tên đường dẫn rõ ràng, tránh dựa vào **current working directory** nếu dữ liệu được đóng gói cùng bytecode.

## 4. Bài thực hành: bộ file và kết quả mong muốn

Ta tạo một project nhỏ, **không cần Maven, Gradle hay thư viện ngoài**:

~~~text
resource-practice/
├── src/
│   └── demo/
│       └── ResourceConfigDemo.java
└── resources/
    ├── config/
    │   └── app.properties
    └── demo/
        └── local-note.txt
~~~

Ý nghĩa:

- `src/demo/ResourceConfigDemo.java`: chương trình có main.
- `resources/config/app.properties`: file cấu hình đóng gói vào root `config/`.
- `resources/demo/local-note.txt`: file mẫu để phân biệt lookup tương đối với package `demo`.

Sau biên dịch và copy resources, classpath directory phải có:

~~~text
out/classes/
├── demo/
│   ├── ResourceConfigDemo.class
│   └── local-note.txt
└── config/
    └── app.properties
~~~

Sau tạo JAR, cấu trúc **entry names** bên trong JAR vẫn là `demo/...` và `config/...`. Chính sự thống nhất này cho phép **không sửa Java source khi đổi hình thức triển khai**.

## 5. Mã Java 17+ hoàn chỉnh

### 5.1. resources/config/app.properties

Tạo file văn bản UTF-8 với **nội dung chính xác**:

~~~properties
app.name=Resource Demo
app.port=8080
feature.enabled=true
~~~

Các khóa cố ý là ASCII để transcript stdout độc lập locale/terminal encoding. Ở hệ thống thật có thể dùng Unicode; phần 8 giải thích vì sao chọn đúng `Reader` khi load Properties.

### 5.2. resources/demo/local-note.txt

Tạo file text UTF-8 có một dòng:

~~~text
package-local-resource
~~~

### 5.3. src/demo/ResourceConfigDemo.java

~~~java
package demo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Properties;

public final class ResourceConfigDemo {
    private static final String CONFIG = "config/app.properties";

    private ResourceConfigDemo() {
    }

    private static Properties loadConfig(ClassLoader loader)
            throws IOException {
        Properties values = new Properties();

        try (InputStream input = loader.getResourceAsStream(CONFIG)) {
            if (input == null) {
                throw new FileNotFoundException(
                        "Missing classpath resource: " + CONFIG);
            }

            try (InputStreamReader reader = new InputStreamReader(
                    input, StandardCharsets.UTF_8)) {
                values.load(reader);
            }
        }

        return values;
    }

    private static String readLocalNote() throws IOException {
        try (InputStream input = ResourceConfigDemo.class
                .getResourceAsStream("local-note.txt")) {
            if (input == null) {
                throw new FileNotFoundException(
                        "Missing package resource: demo/local-note.txt");
            }
            return new String(
                    input.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = ResourceConfigDemo.class.getClassLoader();

        URL byLoader = loader.getResource(CONFIG);
        if (byLoader == null) {
            throw new FileNotFoundException(
                    "Missing classpath resource: " + CONFIG);
        }

        URL byClass = ResourceConfigDemo.class
                .getResource("/" + CONFIG);

        Properties config = loadConfig(loader);

        String name = config.getProperty("app.name");
        int port = Integer.parseInt(
                config.getProperty("app.port"));
        boolean enabled = Boolean.parseBoolean(
                config.getProperty("feature.enabled"));

        System.out.println("resource found: " + (byLoader != null));
        System.out.println("absolute Class lookup matches: "
                + byLoader.equals(byClass));
        System.out.println("protocol: " + byLoader.getProtocol());
        System.out.println("name: " + name);
        System.out.println("port: " + port);
        System.out.println("feature enabled: " + enabled);
        System.out.println("local note: " + readLocalNote());

        Enumeration<URL> matches = loader.getResources(CONFIG);
        int count = 0;
        while (matches.hasMoreElements()) {
            matches.nextElement();
            count++;
        }
        System.out.println("matching resources: " + count);
    }
}
~~~

### 5.4. Điểm cần hiểu trong mã

**Chọn loader từ class đã biết:** `ResourceConfigDemo.class.getClassLoader()` là defining loader của class ứng dụng. Trong demo này là application loader và khác null. Với các class do bootstrap loader định nghĩa, `getClassLoader()` có thể trả null; không nên mặc định mọi class đều có một loader object khác null.

**Tìm resource qua URL:** `getResource(CONFIG)` trả `URL` nếu tìm được. Khi nạp từ thư mục thông thường, protocol hay là `file`. Khi đọc entry từ JAR, protocol của URL được hỗ trợ thường là `jar`. Chương trình **chỉ in protocol**, không in toàn bộ URL có đường dẫn máy khác nhau.

**Đọc nội dung qua stream:** `getResourceAsStream` trả `null` nếu không tìm thấy resource hoặc không truy cập được theo cơ chế của loader. Phải kiểm tra null **trước** khi tạo `InputStreamReader`, nếu không sẽ gặp `NullPointerException` kém rõ nghĩa.

**Đóng tài nguyên:** `try-with-resources` đóng reader/stream kể cả khi `Properties.load` ném `IOException`. Ở đây cả reader lẫn input đều được khai báo trong nested try; việc đóng input lần nữa an toàn đối với `InputStream` chuẩn, nhưng có thể giản lược thành một try với cả hai resources nếu đã kiểm tra null.

**Đọc UTF-8 rõ ràng:** `Properties.load(Reader)` giải mã theo charset mà reader đã chọn. Đoạn code dùng `StandardCharsets.UTF_8` thay vì phụ thuộc vào charset mặc định của OS hoặc nhầm với overload `load(InputStream)`.

**Class-relative lookup:** `ResourceConfigDemo.class.getResourceAsStream("local-note.txt")` tìm `demo/local-note.txt` vì class đang ở package `demo`. Nếu viết `"/local-note.txt"` thì lookup từ root và sẽ **không** trỏ tới file đã tạo.

**getResources:** `loader.getResources(CONFIG)` trả `Enumeration<URL>` những matches tìm được. Trong hai lệnh chạy cơ bản dưới đây, mỗi process có một nguồn chứa `config/app.properties`, nên count dự kiến là 1. Trong ứng dụng có nhiều classpath roots/JARs cùng tên resource, count có thể lớn hơn.

**Validate cấu hình:** `Integer.parseInt` sẽ ném `NumberFormatException` nếu `app.port` không phải số. `Boolean.parseBoolean` trả false cho mọi chuỗi không bằng "true" (không phân biệt hoa/thường), kể cả typo như `tru`; đối với config production nên kiểm tra giá trị hợp lệ tường minh thay vì âm thầm chấp nhận.

## 6. Chạy trực tiếp từ thư mục classpath

### 6.1. Linux/macOS (bash, zsh)

Đứng tại thư mục `resource-practice`:

~~~bash
mkdir -p out/classes
javac --release 17 -d out/classes src/demo/ResourceConfigDemo.java
cp -R resources/config resources/demo out/classes/
java -cp out/classes demo.ResourceConfigDemo
~~~

Lệnh copy tạo đúng `out/classes/config/app.properties` và `out/classes/demo/local-note.txt`. Nếu copy vào sai `out/classes/resources/config`, tên lookup `config/app.properties` sẽ không khớp.

### 6.2. Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force out/classes | Out-Null
javac --release 17 -d out/classes src/demo/ResourceConfigDemo.java
Copy-Item -Recurse -Force resources/config out/classes
Copy-Item -Recurse -Force resources/demo out/classes
java -cp out/classes demo.ResourceConfigDemo
~~~

**Không cần sửa Java resource names theo dấu backslash của Windows.** Các resource names vẫn dùng `/` như đã viết trong source.

### 6.3. Stdout dự kiến: directory

~~~text
resource found: true
absolute Class lookup matches: true
protocol: file
name: Resource Demo
port: 8080
feature enabled: true
local note: package-local-resource
matching resources: 1
~~~

Điều kiện cho transcript chính xác: classpath chỉ gồm `out/classes` như trong lệnh; không có classpath root khác chứa `config/app.properties`; các file có nội dung đúng; chạy bằng JDK thông thường hỗ trợ URL `file:`. Vị trí working directory cần đúng để lệnh compile/copy tìm được source, **nhưng bản thân phép đọc resource không phụ thuộc working directory** sau khi classes/resources đã nằm đúng root.

## 7. Đóng gói thành JAR thực thi rồi đọc cùng cấu hình

Sau khi thực hiện mục 6 (đã có `out/classes` chứa cả `.class` và resource files), tạo JAR:

~~~bash
jar --create --file out/resource-demo.jar \
    --main-class demo.ResourceConfigDemo \
    -C out/classes .
~~~

Lệnh trên dùng dấu `\` xuống dòng dành cho bash/zsh. Trong Windows PowerShell hoặc CMD, dùng **một dòng**:

~~~powershell
jar --create --file out/resource-demo.jar --main-class demo.ResourceConfigDemo -C out/classes .
~~~

Kiểm tra nội dung:

~~~bash
jar --list --file out/resource-demo.jar
~~~

Các entry cần tồn tại (JAR có thể chứa thêm `META-INF/` hoặc entry trật tự khác):

~~~text
META-INF/MANIFEST.MF
demo/ResourceConfigDemo.class
demo/local-note.txt
config/app.properties
~~~

Chạy JAR:

~~~bash
java -jar out/resource-demo.jar
~~~

**Stdout dự kiến khi chạy từ JAR:**

~~~text
resource found: true
absolute Class lookup matches: true
protocol: jar
name: Resource Demo
port: 8080
feature enabled: true
local note: package-local-resource
matching resources: 1
~~~

Khi đóng gói đúng, tất cả **business configuration values** và package resource giống hệt chạy từ thư mục. Điểm chủ yếu thay đổi là `protocol: jar`, thể hiện resource được cung cấp qua một JAR URL thay vì một file URL.

**Chú ý:** `java -jar` thường dùng `Main-Class` từ manifest và không lấy app classpath từ `-cp` như cách gọi main class thông thường. Nếu cần dependency JAR khác, phải cấu hình manifest class path, module path hoặc dùng phương án launcher/packaging phù hợp. Demo này không có dependency ngoài JDK.

## 8. Đọc Properties đúng encoding

Java có hai overload quan trọng:

~~~java
Properties p = new Properties();
p.load(inputStream);
~~~

và:

~~~java
Properties p = new Properties();
p.load(reader);
~~~

Đây là **snippets minh họa** với biến `inputStream` hoặc `reader` đã mở. Điểm khác:

- `load(InputStream)` định nghĩa diễn giải bytes của `.properties` theo **ISO-8859-1** và xử lý `\uXXXX` escapes theo format, không tự coi là UTF-8.
- `load(Reader)` đọc các characters do `Reader` cung cấp. Khi reader là `new InputStreamReader(stream, UTF_8)`, file có thể chứa tiếng Việt UTF-8 như `app.title=Quản lý kho`.
- Nếu dùng Java `Properties.store(OutputStream,...)`, cần hiểu định dạng escape/encoding tương ứng; không giả định file do công cụ UTF-8 tạo sẽ luôn được đọc đúng bởi overload InputStream.

Trong chương trình mục 5, `new InputStreamReader(input, StandardCharsets.UTF_8)` bảo đảm encoding **được chỉ định rõ ràng**, không lệ thuộc mặc định hệ điều hành. Cần đảm bảo file `resources/config/app.properties` thực sự được lưu UTF-8.

### 8.1. Properties không chỉ là các dòng key=value đơn giản

`Properties` hỗ trợ comments bằng `#`/`!`, escape characters, continuation lines và các dạng separators. Ví dụ:

~~~properties
# A comment
app.name=Resource Demo
app.port: 8080
feature.enabled=true
~~~

Cả dấu `=` và `:` được chấp nhận theo cú pháp `Properties`. Nếu cấu hình là YAML, JSON, XML hoặc format tùy chỉnh, không nên dùng `Properties.load` để đọc rồi kỳ vọng đúng semantics; vẫn có thể dùng **resource stream**, nhưng parser bên trên phải phù hợp định dạng.

### 8.2. Missing và invalid values

Có thể xử lý fail fast:

~~~java
String rawPort = config.getProperty("app.port");
if (rawPort == null || rawPort.isBlank()) {
    throw new IllegalArgumentException("Missing app.port");
}
int port = Integer.parseInt(rawPort);
if (port < 1 || port > 65535) {
    throw new IllegalArgumentException("Invalid app.port");
}
~~~

Đây là **snippet minh họa** với `config` đã được load. Nó nêu chính sách validation thay vì chấp nhận mọi value tồn tại. Với secrets, tránh in thẳng passwords/tokens và không đóng cứng credentials thật vào JAR.

## 9. Vì sao không đổi resource URL sang File hoặc Path?

Một lỗi quen thuộc:

~~~java
URL url = loader.getResource("config/app.properties");
File file = new File(url.getFile());
~~~

Đây là **mã sai/không portable**, chưa kể phải null-check URL. Khi URL là:

~~~text
file:/.../out/classes/config/app.properties
~~~

một file vật lý có thể tồn tại, nhưng chuyển `URL.getFile()` thẳng sang `File` vẫn có thể xử lý sai escaping, dấu cách, Unicode hoặc URI encoding.

Khi URL là JAR entry, dạng điển hình:

~~~text
jar:file:/.../out/resource-demo.jar!/config/app.properties
~~~

`config/app.properties` **không phải một file riêng trên disk**, nên `new File(url.getFile())` hoặc `Paths.get(url.toURI())` không thể mặc định hoạt động như với file URL. Một số trường hợp có thể xử lý JAR filesystem đặc biệt, nhưng không cần làm vậy để đọc cấu hình.

**Giải pháp mặc định để đọc bytes/text đóng gói:** dùng `getResourceAsStream` hoặc `URL.openStream()` trong `try-with-resources`.

Chỉ chuyển URL thành `Path` khi ứng dụng thực sự yêu cầu path và bạn **biết chắc protocol/file system provider phù hợp**, ví dụ một URL `file:` rồi dùng `Path.of(url.toURI())`, có xử lý exceptions. Không ép mọi resource phải là file vật lý.

### 9.1. Nếu cần chỉnh sửa cấu hình sau khi deploy?

Cấu hình đóng gói trong JAR là **application artifact**; không nên cố mở resource entry để ghi ngược. Nếu config cần chỉnh không rebuild JAR:

- Đặt default config trong JAR.
- Đặt override config ở **external filesystem path** (environment variable, CLI argument, system property hoặc deployment config).
- Đọc default qua `getResourceAsStream`.
- Đọc override bằng `Files.newBufferedReader(path, UTF_8)` nếu file đã được chỉ định và cho phép.
- Áp dụng thứ tự ưu tiên, validate, xử lý lỗi và lifecycle rõ.

Phân biệt hoàn toàn **classpath resource** và **external user-managed config**; mỗi loại dùng API và quyền truy cập phù hợp.

## 10. Trùng tên resource trên nhiều JAR hoặc thư mục

Giả sử:

~~~text
lib-a.jar!/config/app.properties
lib-b.jar!/config/app.properties
out/classes/config/app.properties
~~~

và cả ba nằm trên lookup path. `ClassLoader.getResource("config/app.properties")` tìm **một** resource theo thứ tự tra cứu của loader cụ thể, thường có delegation về parent. `ClassLoader.getResources(...)` có thể liệt kê nhiều matches.

Không nên giả định một **thứ tự nhất quán cho mọi JVM, mọi class loader, mọi module layer**:

- Parent-first và child-first class loaders có thể khác.
- Java module path khác unnamed classpath; các rules cho named modules ảnh hưởng visibility.
- Khi nhiều named modules cùng loader chứa resource trùng, API Javadoc nêu thứ tự tìm có thể **không được quy định**.
- Một ứng dụng phụ thuộc vào “JAR tình cờ được đọc trước” dễ thay config khi build/deploy thay đổi.

Muốn biết tất cả occurrences:

~~~java
Enumeration<URL> resources =
        loader.getResources("config/app.properties");
while (resources.hasMoreElements()) {
    URL current = resources.nextElement();
    System.out.println(current);
}
~~~

**Đoạn minh họa** yêu cầu `loader` tồn tại và method cho phép `IOException`. Để debug, có thể in URL nguồn; nhưng trong log production cần tránh lộ đường dẫn nội bộ hoặc tokens nhúng trong URL.

Đối với cấu hình quan trọng:

1. Ưu tiên **một owner rõ ràng** cho config.
2. Kiểm tra duplicate resources trong build pipeline.
3. Nếu thiết kế overlay/merge, tự định nghĩa thứ tự ưu tiên và hợp đồng merge, không phó mặc lookup order.
4. Trong multi-tenant/plugin architecture, mỗi plugin có loader riêng thì việc gọi `getResource` từ **loader nào** là quyết định quan trọng.

## 11. Parent delegation và context class loader

Trong demo, loader được chọn là defining loader của `ResourceConfigDemo`. Trong ứng dụng thực tế còn có các khả năng:

- **Application class loader**: thấy classes/resources trong app classpath.
- **Plugin class loader**: có thể thấy resources của plugin và parent tùy chính sách; phù hợp khi file thuộc plugin.
- **Thread Context ClassLoader (TCCL)**: dùng bởi một số frameworks/framework SPI để phát hiện providers/resources của context hiện tại; không tự thay defining loader của class.
- **Custom loader**: có thể override `findResource`, `findResources` hoặc dùng URLClassLoader; phải kiểm tra delegation và resource lookup behavior.

Ví dụ minh họa:

~~~java
ClassLoader context =
        Thread.currentThread().getContextClassLoader();

if (context != null) {
    URL pluginConfig = context.getResource("config/plugin.properties");
}
~~~

**Đoạn code minh họa**: TCCL có thể phù hợp nếu caller/framework chủ động quản lý nó. Nhưng không nên dùng TCCL ngẫu nhiên khi file phải gắn cố định với một class/module; TCCL của thread pool hoặc container có thể trỏ tới loader khác, gây missing hoặc duplicate config.

Khi gọi `loader.getResourceAsStream`, API nhận **một resource name**, không nhận một `Class<?>` để “hợp nhất” namespaces. Loader được chọn vẫn quyết định phạm vi lookup.

## 12. Named modules (JPMS) có điểm khác classpath

Trong Java 9+ có named modules, các API resource chịu **encapsulation rules** ngoài đường dẫn đơn thuần. Theo Java 17 Javadoc:

- `ClassLoader.getResource`, `getResourceAsStream`, `getResources` với non-`.class` resources thuộc **packages của named modules** thường chỉ tìm được nếu package **opened unconditionally**; chỉ `opens p to q` có thể chưa đủ cho tìm kiếm kiểu ClassLoader generic.
- `Class.getResource` và `Class.getResourceAsStream` trên class thuộc named module tìm theo module sở hữu class và áp dụng module encapsulation rules; resource trong package không open cho caller module có thể không truy cập được.
- `Module.getResourceAsStream` có semantics gắn với module và caller, phù hợp khi cần resource của một module xác định.
- Resource ở path **không thuộc package** có thể chịu điều kiện khác với resource nằm trong một package module; không lấy classpath examples làm lời hứa cho mọi module.
- `exports` cho public Java API không đồng nghĩa `opens` cho deep reflection/resource encapsulation.

Ví dụ descriptor minh họa nếu module cố ý cho phép truy cập resource trong package `demo.config` bằng ClassLoader generic:

~~~java
module demo.app {
    opens demo.config;
}
~~~

**Đây là snippet mô tả cấu hình JPMS, không phải phần phải thêm vào demo classpath ở mục 5.** Phải có package tồn tại và module descriptor được biên dịch hợp lệ theo project cụ thể; không mở vô điều kiện packages chứa dữ liệu nhạy cảm chỉ để làm API lookup thuận tiện.

**Chủ động chọn cách tổ chức resources:** Config đặt ở path root không đại diện cho package Java, hoặc truy cập resource thông qua code API do module sở hữu, có thể đơn giản và rõ quyền hơn việc mở hàng loạt packages.

## 13. Maven và Gradle: chuẩn đóng gói resources

### 13.1. Maven

Cấu trúc quen thuộc:

~~~text
project/
├── pom.xml
└── src/
    └── main/
        ├── java/demo/ResourceConfigDemo.java
        └── resources/
            ├── config/app.properties
            └── demo/local-note.txt
~~~

Maven Resources Plugin mặc định copy `src/main/resources` vào output classes (thường `target/classes`) và công cụ đóng JAR lấy các entries đó. **File `src/main/resources/config/app.properties` sẽ thành `config/app.properties` trong JAR**, không phải `resources/config/app.properties`.

Bài này không yêu cầu chạy Maven; đây là đối chiếu layout nếu bạn chuyển ví dụ đã chạy sang project Maven.

### 13.2. Gradle

Với Java plugin thông thường:

~~~text
src/main/java/demo/ResourceConfigDemo.java
src/main/resources/config/app.properties
src/main/resources/demo/local-note.txt
~~~

Gradle xử lý resources trong build output (ví dụ `build/resources/main`) và các outputs cần thiết được đưa vào classpath/JAR theo configuration. Nếu có cấu hình source sets hoặc custom packaging, phải kiểm tra **artifact thực tế**, không chỉ dựa vào layout trên source tree.

### 13.3. Lệnh xác minh artifact

~~~bash
jar --list --file out/resource-demo.jar
~~~

Cho JAR ở bài tập mục 7. Khi dùng build tool, thay file path bằng artifact do build tạo. Một file cấu hình có trên disk nhưng **không nằm trong JAR** sẽ không tự xuất hiện qua resource lookup khi chỉ chạy JAR đã đóng gói.

Với executable/fat JAR framework-specific (ví dụ class files/resources trong `BOOT-INF/classes`), loader đặc thù có thể cung cấp URL schemes và cấu trúc package khác. **Đừng suy URL luôn là `jar:file:...!/config/...` trong mọi launcher.** API `getResourceAsStream` vẫn là abstraction được ưu tiên nếu framework loader hỗ trợ.

## 14. Lỗi thường gặp và cách chẩn đoán

| Hiện tượng | Nguyên nhân có thể | Kiểm tra/sửa |
| --- | --- | --- |
| `getResourceAsStream` trả null | Sai name hoặc resources chưa ở classpath/JAR | In tên lookup, kiểm tra build output và `jar --list` |
| Chạy IDE được, `java -jar` lỗi | IDE thêm `src/main/resources` vào classpath nhưng JAR thiếu entries | Kiểm tra cấu hình copy/build |
| Windows chạy sai | Dùng `\` trong resource name | Luôn dùng `/` |
| `Class.getResource("config/x")` không tìm | API này tìm tương đối với package class | Dùng `"/config/x"` hoặc ClassLoader root name |
| `ClassLoader.getResource("/config/x")` null | Sai slash đầu của ClassLoader lookup | Bỏ slash đầu |
| `FileNotFoundException` chỉ khi chạy JAR | Code giả định resource là physical File | Dùng InputStream |
| Java đọc tiếng Việt thành ký tự lạ | Dùng `Properties.load(InputStream)` với UTF-8 bytes | `load(Reader)` với UTF-8 |
| Config từ JAR khác được chọn | Trùng tên resources và lookup order | `getResources`, tránh trùng, define precedence |
| Local note trong package không tìm thấy | Copy sai `demo/local-note.txt` vào build root | Đặt đúng package-relative path |
| `Integer.parseInt` lỗi | Thiếu hoặc giá trị không phải số | Validate key/format với message rõ |
| Resource có tên đúng nhưng module không cho thấy | Named module encapsulation, package not opened | Kiểm tra module/Javadoc API, `opens` có kiểm soát |
| Plugin không đọc thấy config | Chọn sai ClassLoader/TCCL | Dùng defining/plugin loader đúng owner |
| `Resource URL` có protocol lạ | Custom/fat JAR loader cung cấp custom URL scheme | Đọc stream thay vì dựa vào File path |
| File tồn tại trên disk nhưng không thấy | Nó nằm ngoài classpath roots | Copy đóng gói hoặc dùng explicit filesystem API |

### 14.1. Chẩn đoán thứ tự phù hợp

1. **Đọc đúng message** và resource name. Phân biệt `null` với `IOException`/parser exception.
2. Xem lookup dùng **Class hay ClassLoader**, class nằm trong package nào.
3. Xem **classpath hoặc module path thực khi chạy**, không suy từ cửa sổ IDE.
4. Kiểm tra output folder `out/classes` hoặc build/classes/resources.
5. Kiểm tra JAR entries, chú ý resource ở **classpath root**.
6. Nếu nhiều JARs, gọi `getResources` để xem duplicate matches.
7. Nếu là named module, kiểm tra module ownership và encapsulation.
8. Nếu URL không thuộc `file`, tránh `File`/`Path` giả định.
9. Kiểm tra charset và dữ liệu có đúng format `Properties`.
10. Chạy từ thư mục working directory khác để xác nhận chương trình thực sự không phụ thuộc CWD sau khi đóng gói.

## 15. Tự thực hành mở rộng: cấu hình bên ngoài có quyền ưu tiên

Một bài tập thực tế là dùng mặc định từ `config/app.properties` **trong JAR**, rồi cho phép người vận hành cung cấp file bên ngoài:

~~~text
Built-in default (JAR)        app.name=Resource Demo
                              app.port=8080

Optional external override    app.port=9090

Effective config              app.name=Resource Demo
                              app.port=9090
~~~

Ví dụ API minh họa:

~~~java
Properties values = new Properties();
try (InputStream in = loader.getResourceAsStream(
        "config/app.properties")) {
    if (in == null) {
        throw new FileNotFoundException("Missing packaged defaults");
    }
    values.load(new InputStreamReader(
            in, StandardCharsets.UTF_8));
}

if (externalPath != null) {
    try (var reader = Files.newBufferedReader(
            externalPath, StandardCharsets.UTF_8)) {
        values.load(reader);
    }
}
~~~

**Đây là snippet, không phải file runnable độc lập**; `loader`/`externalPath` đã được xác định trước, cần imports, exception handling và validation phù hợp. `Properties.load` lần hai có thể ghi đè các keys cùng tên, nếu đó là precedence bạn lựa chọn.

Nên thiết kế:

- File bên ngoài được **chỉ rõ** qua CLI/ENV, không tự động tìm tùy tiện gần working directory.
- Thông báo rõ default file thiếu hay external override file không tồn tại.
- Giới hạn path được phép nếu người dùng không tin cậy có thể điều khiển đường dẫn.
- Không cho phép config key nguy hiểm chỉ vì tệp tồn tại; cần validate sau merge.
- Không in secrets/token values vào log.
- Phân biệt sửa external file trong runtime với **thay JAR artifact**, không cố ghi ngược vào classpath URL.

## 16. Checklist kiểm thử thực tế

- [ ] `src/demo/ResourceConfigDemo.java` compile với `javac --release 17`.
- [ ] `config/app.properties` được copy dưới **classpath root**, không thừa `resources/`.
- [ ] `demo/local-note.txt` nằm cùng package relative path với class.
- [ ] `java -cp out/classes demo.ResourceConfigDemo` in `protocol: file` và đúng keys/values.
- [ ] JAR chứa `config/app.properties` và `demo/local-note.txt`.
- [ ] `java -jar out/resource-demo.jar` in `protocol: jar` và đúng keys/values.
- [ ] Các streams được đóng với `try-with-resources`.
- [ ] Missing resources được báo rõ thay vì `NullPointerException`.
- [ ] Đọc Properties UTF-8 bằng `InputStreamReader` + `Properties.load(Reader)`.
- [ ] Không dùng `new File(url.getFile())` với một entry nằm trong JAR.
- [ ] Không phụ thuộc thứ tự trùng resources ngoài hợp đồng đã định nghĩa.
- [ ] Nếu có plugins, dùng đúng ClassLoader và đúng resource owner.
- [ ] Nếu dùng JPMS, kiểm tra named-module encapsulation riêng.
- [ ] Nếu có external config, validate thứ tự ưu tiên và chống rò rỉ secrets.

## 17. Câu hỏi tự kiểm tra

**1. Một resource là gì?** Dữ liệu được tìm bằng tên logic trong lookup scope của class loader/module, có thể nằm ở thư mục, JAR hoặc nguồn do loader hỗ trợ.

**2. `ClassLoader.getResourceAsStream("config/app.properties")` có cần file vật lý bên ngoài JAR không?** Không; nó có thể đọc entry bên trong JAR.

**3. Khác nhau giữa `MyClass.class.getResource("x.txt")` và `getResource("/x.txt")`?** Tên không slash đầu tương đối với package của class; slash đầu tính từ root.

**4. Có nên viết `loader.getResource("/config/app.properties")`?** Không theo quy tắc ClassLoader chuẩn; dùng `config/app.properties`.

**5. Vì sao resource chạy IDE được nhưng JAR không thấy?** IDE có thể thêm resource source dir vào classpath, còn JAR đóng gói thiếu file hoặc sai root.

**6. URL resource bên trong JAR thường có protocol gì?** Thường `jar` trong môi trường Java JAR chuẩn; không phụ thuộc vào điều này cho mọi custom launcher.

**7. Vì sao không lấy `new File(url.getFile())` để đọc mọi resource?** Entry trong JAR không phải file riêng trên OS filesystem; còn có URI encoding issues.

**8. `getResourceAsStream` trả gì nếu file thiếu?** `null` theo API; nên kiểm tra và báo lỗi rõ.

**9. `Properties.load(InputStream)` đọc UTF-8 giống `load(Reader UTF_8)` không?** Không. InputStream overload có quy tắc ISO-8859-1; Reader overload dùng characters đã giải mã.

**10. Vì sao `getResources` hữu ích?** Nó cho phép phát hiện nhiều resources cùng tên trong classpath/module lookup scope.

**11. Vì sao cần try-with-resources?** Đóng stream/reader kịp thời, tránh rò rỉ file/JAR handles và resources.

**12. Khác biệt classpath config và external config?** Classpath config là dữ liệu đóng gói cùng artifact; external config do hệ thống triển khai quản lý qua Path rõ ràng.

**13. `Class.getResource` có luôn giống `ClassLoader.getResource` trong named module không?** Không; named modules có quy tắc ownership và encapsulation riêng.

**14. File `src/main/resources/config/app.properties` trong Maven thường xuất hiện ở entry nào trong JAR?** `config/app.properties`.

**15. Khi nhận ClassLoader từ `String.class.getClassLoader()`, có thể null không?** Có. Null thường đại diện bootstrap loader.

## 18. Tham khảo chính thức

- Java SE 17 — `ClassLoader.getResource`, `getResources`, `getResourceAsStream`, module encapsulation: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 — `Class.getResource` và `getResourceAsStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java SE 17 — `Module.getResourceAsStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html
- Java SE 17 — `Properties.load(InputStream)` và `load(Reader)`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Properties.html
- Java SE 17 — `URL.openStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URL.html
- Java SE 17 — `JarFile` (khi thực sự cần thao tác archive entries): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarFile.html
- Java SE 17 — `jar` command: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jar.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-exercises-003">Xây dựng loader nạp bytecode từ bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng ClassLoader nạp bytecode trực tiếp từ bộ nhớ

## 1. Mục tiêu bài thực hành

Một `ClassLoader` không bắt buộc phải lấy định nghĩa lớp từ file `.class` trên ổ đĩa, từ JAR hay qua URL. JVM cần một **mảng byte biểu diễn class file hợp lệ**, tên class phù hợp và một defining loader có quyền định nghĩa class. Nguồn của các bytes có thể là:

- Kết quả biên dịch source Java ngay trong chương trình bằng `JavaCompiler`.
- Bytecode do bộ sinh mã (bytecode generator) tạo.
- Một artifact được đọc và kiểm tra rồi giữ trong memory cache.
- Bytes được truyền qua IPC/network hoặc đọc từ một kho dữ liệu (nếu ứng dụng tin cậy nguồn đó và có chính sách xác minh phù hợp).

Trọng tâm là tự xây dựng loader kiểu **map binary name → byte[]** để class được định nghĩa từ bộ nhớ bằng protected `ClassLoader.defineClass`. Sau đó ghép loader với `javax.tools.JavaCompiler` để tạo một ví dụ **biên dịch và nạp hoàn toàn trong RAM**, không cần ghi file `Hello.class` lên đĩa.

Bài thuộc nhóm **Thực hành** của module *Java core – classloader*. Sau bài này, bạn có thể:

1. Viết subclass `MemoryLoader extends ClassLoader` và override `findClass(String)` đúng cách.
2. Biên dịch source Java thành `byte[]` bằng `JavaCompiler` và custom `JavaFileManager`.
3. Đưa bytes vào map và gọi `loadClass` để tạo runtime `Class<?>`.
4. Dùng reflection/contract chung để tạo object và gọi method.
5. Kiểm chứng việc **load cùng tên trên cùng loader** trả cùng class, còn **hai loaders khác nhau** có thể tạo hai runtime types khác nhau.
6. Phân biệt **nạp từ bộ nhớ** với chuyện **công bố resource `.class` qua `getResource`**.
7. Xử lý đúng `ClassNotFoundException`, `LinkageError`, classfile version, parent delegation, initialization và nguy cơ memory leak.
8. Biết giới hạn của bài demo so với production, nơi bytecode phải được xác minh và vòng đời classloader được quản lý.

**Phiên bản:** Java 17 trở lên. Ví dụ không cần Maven/Gradle, Internet, JAR bên ngoài hay API không công khai của JDK. Chương trình đầy đủ đã được biên dịch bằng `javac --release 17` và chạy thực tế.

## 2. Hiểu đúng “nạp bytecode từ bộ nhớ”

### 2.1. Source → byte[] → runtime Class

Một luồng thông thường:

~~~text
Hello.java
  |
  | javac
  v
Hello.class trên disk
  |
  | ClassLoader đọc file/JAR
  v
byte[] classfile
  |
  | defineClass
  v
Class<?> trong JVM
~~~

Trong bài này ta thay bằng:

~~~text
Java source nằm trong String
  |
  | JavaCompiler (JSR 199)
  v
JavaFileObject chứa ByteArrayOutputStream
  |
  | snapshot()
  v
Map<String,byte[]> trong RAM
  |
  | MemoryLoader.findClass(name)
  | -> defineClass(name, bytes, 0, bytes.length)
  v
Class<?> trong JVM
  |
  | Reflection
  v
Instance + method invocation
~~~

Điểm khác biệt là **bytes đầu vào được giữ trong map và chuyển thẳng cho `defineClass`**. Không có bước tạo file `memory/generated/Hello.class` trên disk. Tuy nhiên Java VM vẫn tạo thông tin class ở runtime, gồm metadata/linking structures, và có thể phát sinh mã native/JIT theo hoạt động bình thường.

**“Từ bộ nhớ” không đồng nghĩa “không có metadata, không có memory footprint, không có kiểm tra an toàn JVM”.**

### 2.2. Binary name khác resource path

Nếu source khai báo:

~~~java
package memory.generated;

public final class Hello {
    public String greet(String person) {
        return "MEMORY:" + person;
    }
}
~~~

thì:

- **Binary name:** `memory.generated.Hello`.
- **Resource path thông thường:** `memory/generated/Hello.class`.
- **Source path biểu diễn trong công cụ biên dịch:** `memory/generated/Hello.java`.
- **Runtime class identity:** phụ thuộc **binary name + defining ClassLoader**, không chỉ chuỗi tên.

Map của loader nên dùng binary names làm key:

~~~java
Map<String, byte[]> compiled = Map.of(
    "memory.generated.Hello", classfileBytes);
~~~

Đây là snippet minh họa, cần có biến `classfileBytes` chứa bytecode classfile thật. Nếu vô tình dùng key `memory/generated/Hello.class` rồi gọi `loadClass("memory.generated.Hello")`, lookup map sẽ không khớp.

### 2.3. `ClassLoader.loadClass` và `findClass` không giống nhau

- `loadClass(String)` là entry point công khai thường được caller dùng.
- Implementation mặc định xử lý cache class đã nạp, parent delegation và chỉ gọi `findClass` khi cần.
- `findClass(String)` là hook mà subclass override để **tìm bytes và định nghĩa class do loader mình quản lý**.
- `defineClass(...)` là thao tác thực sự chuyển classfile bytes thành class được JVM định nghĩa cho loader này.

Do đó, bài chỉ cần override **`findClass`**, không cần tự viết lại `loadClass`. Giữ implementation mặc định giúp tận dụng synchronization, `findLoadedClass` và parent delegation để tránh nhiều lỗi khó tái hiện.

## 3. MemoryLoader tối thiểu: phần cốt lõi cần nhớ

Nếu đã có một `Map<String, byte[]>` đáng tin và bất biến sau khi xây dựng, loader chính chỉ cần:

~~~java
import java.util.Map;

final class MemoryLoader extends ClassLoader {
    private final Map<String, byte[]> bytecodes;

    MemoryLoader(Map<String, byte[]> bytecodes, ClassLoader parent) {
        super(parent);
        this.bytecodes = Map.copyOf(bytecodes);
    }

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {
        byte[] bytes = bytecodes.get(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }
}
~~~

Đây là **một snippet class hoàn chỉnh về cơ chế nạp**, nhưng chưa có `main` và chưa tạo bytecode. Các bước quan trọng:

1. `MemoryLoader` kế thừa `ClassLoader` với parent được chỉ định.
2. Map dùng binary name → Java classfile bytes.
3. `findClass(name)` tìm mảng bytes.
4. Nếu không có entry, **ném `ClassNotFoundException(name)`** — đừng trả `null`.
5. Nếu có, gọi `defineClass(name, bytes, 0, bytes.length)`.
6. JVM kiểm tra classfile, tên, verification/linking constraints theo hợp đồng và tạo `Class<?>` khi hợp lệ.

**Lưu ý về bất biến:** `Map.copyOf` chỉ copy map structure, **không clone mảng byte[]**. Nếu caller sửa các mảng sau khi cấp loader, sẽ tạo race và hành vi khó dự đoán. Trong production, tạo **defensive copies** của từng byte array tại boundary, không đưa map có thể thay đổi vào code không tin cậy. Trong demo đầy đủ, compiler tạo mảng riêng và không có thread khác chỉnh sửa.

### 3.1. Vì sao không gọi `defineClass` trực tiếp từ application code?

`defineClass` là protected API. Đặt lời gọi trong `ClassLoader` subclass là cách được hỗ trợ. Nó không phải public static method kiểu:

~~~java
Class<?> clazz = ClassLoader.defineClass(...);
~~~

Dòng trên là **mã sai có chủ đích**. Bài đầy đủ gọi `defineClass` từ `MemoryLoader.findClass`, sau đó application code gọi `loader.loadClass(binaryName)`.

### 3.2. Vì sao không override `loadClass` ngay từ đầu?

Tự override `loadClass` và luôn `defineClass` rất dễ tạo race hoặc duplicate-definition lỗi:

~~~text
Thread A: findClass("X") -> defineClass X -> thành công
Thread B: findClass("X") -> defineClass X -> LinkageError
~~~

Implementation chuẩn `loadClass` đồng bộ đúng critical section và kiểm tra `findLoadedClass` trước khi dùng hook `findClass`. Nếu bạn cần child-first plugin policy, đó là thiết kế nâng cao đòi hỏi khóa `getClassLoadingLock(name)` và kiểm tra cache/delegation kỹ lưỡng. Không nên đổi policy chỉ để giải quyết bài nạp bytecode trong RAM.

### 3.3. Parent-first có thể “thắng” map của MemoryLoader

Giả sử application loader đã có class `memory.generated.Hello` trên `-cp`. Khi child gọi `loadClass("memory.generated.Hello")`, parent có thể trả class của parent **trước khi `findClass` được gọi**. Kết quả là bạn nghĩ memory map bị bỏ qua nhưng thực ra đó là parent-first delegation đúng thiết kế.

Để tái hiện bài thực hành:

- Chỉ compile **`MemoryLoaderDemo.java`** làm launcher.
- Source `Hello` nằm trong **String**, không có `Hello.class` trên app classpath.
- Gọi `MemoryLoader.loadClass(NAME)` với parent là loader định nghĩa host.
- Đừng vô tình thêm JAR khác cũng có `memory.generated.Hello` lên application classpath.

## 4. Tạo bytecode trong RAM bằng JavaCompiler

### 4.1. `javax.tools.JavaCompiler` là gì?

`ToolProvider.getSystemJavaCompiler()` lấy compiler Java được cung cấp bởi JDK đang chạy:

~~~java
JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
if (compiler == null) {
    throw new IllegalStateException("Run with a JDK, not a JRE");
}
~~~

Trên JDK 17+, công cụ compiler nằm trong module `java.compiler` (API) và implementation/compiler support của JDK (thường module `jdk.compiler`). Nếu chạy với custom runtime image không chứa system compiler, `getSystemJavaCompiler()` có thể trả `null`. Khi đó không thể dùng cách “compile source ở runtime” mà không bổ sung compiler; **MemoryLoader vẫn có thể load bytes đã compile sẵn**.

### 4.2. `JavaFileObject` cho source Java từ String

Thay vì trỏ compiler tới file `.java`, ta tạo `SimpleJavaFileObject` mang một URI giả và trả source string bằng `getCharContent`:

~~~java
static final class SourceFile extends SimpleJavaFileObject {
    final String code;

    SourceFile(String name, String code) {
        super(URI.create("string:///" +
              name.replace('.', '/') + Kind.SOURCE.extension),
              Kind.SOURCE);
        this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}
~~~

Đây là **snippet bên trong class**, cần `URI` và `javax.tools.SimpleJavaFileObject` imports. `string:///...` là **URI đặt tên JavaFileObject**, không phải file cần tồn tại hoặc giao thức mạng thật.

### 4.3. `JavaFileObject` ghi `.class` vào `ByteArrayOutputStream`

Đầu ra của compiler bình thường là file `.class`. Ta thay đích bằng object tự ghi vào RAM:

~~~java
static final class ClassFile extends SimpleJavaFileObject {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    ClassFile(String name, Kind kind) {
        super(URI.create("bytes:///" +
              name.replace('.', '/') + kind.extension), kind);
    }

    @Override
    public OutputStream openOutputStream() {
        return out;
    }

    byte[] bytes() {
        return out.toByteArray();
    }
}
~~~

Đây là snippet cần `ByteArrayOutputStream`, `OutputStream`, `URI`, `SimpleJavaFileObject` imports. URI `bytes:///...` chỉ là định danh của compiler output object; mảng bytes thật nằm trong buffer `out`.

### 4.4. Custom JavaFileManager nhận từng class được compile

Một lượt compile có thể phát sinh **nhiều `.class`** từ một source (inner/nested classes, các lớp bổ trợ). Vì vậy không nên chỉ có một biến `byte[] compiledOutput` cho mọi tên. Ta tạo file manager:

~~~java
static final class MemoryFileManager
        extends ForwardingJavaFileManager<StandardJavaFileManager> {
    final Map<String, ClassFile> outputs = new HashMap<>();

    MemoryFileManager(StandardJavaFileManager delegate) {
        super(delegate);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(
            Location location, String className,
            JavaFileObject.Kind kind, FileObject sibling) {
        ClassFile file = new ClassFile(className, kind);
        outputs.put(className, file);
        return file;
    }

    Map<String, byte[]> snapshot() {
        Map<String, byte[]> all = new HashMap<>();
        outputs.forEach((name, file) -> all.put(name, file.bytes()));
        return Map.copyOf(all);
    }
}
~~~

Đây là snippet trong class bao ngoài với imports JavaCompiler/JavaFileManager, HashMap/Map, FileObject, JavaFileObject. `getJavaFileForOutput` được compiler gọi khi muốn ghi class bytes. Ta giữ mỗi output theo **binary class name** do compiler cung cấp.

**Tác dụng thật:** compiler ghi classfile vào RAM, rồi `snapshot()` lấy byte arrays. Trong ví dụ một class `Hello` sẽ có một entry `memory.generated.Hello`; với inner classes còn có thể có `Outer$Inner`.

## 5. Chương trình Java 17+ đầy đủ và chạy được

Ví dụ hoàn chỉnh bên dưới tự compile source `memory.generated.Hello` nằm trong String, dùng `MemoryLoader` để define bytes, gọi method bằng reflection, sau đó nạp cùng bytes trong loader thứ hai.

### 5.1. Cây project

~~~text
memory-loader-demo/
├── MemoryLoaderDemo.java
└── out/
    └── MemoryLoaderDemo*.class  (tạo khi javac launcher)
~~~

Không cần file `Hello.java` và **không có file `Hello.class` trên disk**. Dùng JDK 17+ để chạy chương trình từ một directory bạn có quyền tạo file của launcher; toàn bộ kết quả compile **lớp sinh động `Hello`** ở trong memory.

### 5.2. Tệp `MemoryLoaderDemo.java`

~~~java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public final class MemoryLoaderDemo {
    static final String NAME = "memory.generated.Hello";
    static final String CODE = """
            package memory.generated;
            public final class Hello {
                public String greet(String person) {
                    return "MEMORY:" + person;
                }
            }
            """;

    static final class SourceFile extends SimpleJavaFileObject {
        final String code;
        SourceFile(String name, String code) {
            super(URI.create("string:///" +
                  name.replace('.', '/') + Kind.SOURCE.extension),
                  Kind.SOURCE);
            this.code = code;
        }
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    static final class ClassFile extends SimpleJavaFileObject {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ClassFile(String name, Kind kind) {
            super(URI.create("bytes:///" +
                  name.replace('.', '/') + kind.extension), kind);
        }
        @Override
        public OutputStream openOutputStream() {
            return out;
        }
        byte[] bytes() { return out.toByteArray(); }
    }

    static final class MemoryFileManager
            extends ForwardingJavaFileManager<StandardJavaFileManager> {
        final Map<String, ClassFile> outputs = new HashMap<>();

        MemoryFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                Location location, String className,
                JavaFileObject.Kind kind, FileObject sibling) {
            ClassFile file = new ClassFile(className, kind);
            outputs.put(className, file);
            return file;
        }

        Map<String, byte[]> snapshot() {
            Map<String, byte[]> all = new HashMap<>();
            outputs.forEach((name, file) -> all.put(name, file.bytes()));
            return Map.copyOf(all);
        }
    }

    static final class MemoryLoader extends ClassLoader {
        final Map<String, byte[]> bytecodes;

        MemoryLoader(Map<String, byte[]> bytecodes) {
            super(MemoryLoaderDemo.class.getClassLoader());
            this.bytecodes = bytecodes;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            byte[] bytes = bytecodes.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Run with a JDK, not a JRE");
        }

        DiagnosticCollector<JavaFileObject> diagnostics =
                new DiagnosticCollector<>();
        Map<String, byte[]> compiled;

        try (StandardJavaFileManager standard =
                     compiler.getStandardFileManager(
                             diagnostics, null, null);
             MemoryFileManager files = new MemoryFileManager(standard)) {
            boolean ok = compiler.getTask(
                    null, files, diagnostics,
                    List.of("--release", "17", "-proc:none"),
                    null, List.of(new SourceFile(NAME, CODE))).call();
            if (!ok) {
                throw new IllegalStateException(
                        "Compilation failed: " + diagnostics.getDiagnostics());
            }
            compiled = files.snapshot();
        }

        System.out.println("compiledNames=" + compiled.keySet());
        System.out.println("validClassBytes="
                + (compiled.get(NAME).length > 4));

        MemoryLoader first = new MemoryLoader(compiled);
        Class<?> c1 = first.loadClass(NAME);
        System.out.println("definedByMemoryLoader="
                + (c1.getClassLoader() == first));
        System.out.println("repeatedLoadSameClass="
                + (first.loadClass(NAME) == c1));

        Object obj = c1.getDeclaredConstructor().newInstance();
        Object reply = c1.getMethod("greet", String.class)
                         .invoke(obj, "Java");
        System.out.println("result=" + reply);

        MemoryLoader second = new MemoryLoader(compiled);
        Class<?> c2 = second.loadClass(NAME);
        System.out.println("differentLoaderDifferentClass=" + (c1 != c2));
        System.out.println("parentStringShared="
                + (first.loadClass("java.lang.String") == String.class));
        System.out.println("resourceNotAutoPublished="
                + (first.getResource("memory/generated/Hello.class") == null));

        try {
            MemoryLoaderDemo.class.getClassLoader().loadClass(NAME);
            System.out.println("appLoaderFindsGenerated=true");
        } catch (ClassNotFoundException expected) {
            System.out.println("appLoaderFindsGenerated=false");
        }

        try {
            first.loadClass("memory.generated.Absent");
            System.out.println("missingClassRejected=false");
        } catch (ClassNotFoundException expected) {
            System.out.println("missingClassRejected=true");
        }
    }
}
~~~

**Phần cần nhớ:** `ClassFile` được compiler ghi ra trong RAM; `MemoryFileManager.snapshot()` cung cấp map bytes; `MemoryLoader.findClass` là nơi duy nhất gọi `defineClass`. Host không import `memory.generated.Hello` và không có dependency runtime với file `Hello.class`.

### 5.3. Lệnh biên dịch và chạy

Linux/macOS:

~~~sh
mkdir -p out
javac --release 17 -d out MemoryLoaderDemo.java
java -cp out MemoryLoaderDemo
~~~

Windows PowerShell:

~~~powershell
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -d out MemoryLoaderDemo.java
java -cp out MemoryLoaderDemo
~~~

**Chú ý về bản JDK:** nguồn sử dụng Java text block (`"""`), API từ Java 9+ và `--release 17`. Phải dùng **JDK 17+ có system JavaCompiler**; một custom runtime image thiếu `jdk.compiler` có thể không chạy được phần compile động.

### 5.4. Stdout chính xác đã kiểm chứng

~~~text
compiledNames=[memory.generated.Hello]
validClassBytes=true
definedByMemoryLoader=true
repeatedLoadSameClass=true
result=MEMORY:Java
differentLoaderDifferentClass=true
parentStringShared=true
resourceNotAutoPublished=true
appLoaderFindsGenerated=false
missingClassRejected=true
~~~

**Kết quả được kiểm chứng bằng chương trình thật**: đầu tiên biên dịch `MemoryLoaderDemo.java` với `javac --release 17`, sau đó chạy bằng `java` trên JDK. Chương trình chỉ compile đúng **một class** trong memory nên thứ tự key của map không gây khác biệt cho dòng đầu.

### 5.5. Phân tích từng dòng output

| Output | Điều kiểm chứng |
|---|---|
| `compiledNames=[memory.generated.Hello]` | Compiler đã trả bytes và file manager lưu theo binary name |
| `validClassBytes=true` | `byte[]` tồn tại và dài hơn header tối thiểu; **không phải phép verify hoàn chỉnh** |
| `definedByMemoryLoader=true` | JVM gán defining loader của generated class bằng instance `first` |
| `repeatedLoadSameClass=true` | `loadClass` trên cùng loader/tên trả lại cùng `Class<?>` sau lần định nghĩa đầu |
| `result=MEMORY:Java` | Reflection đã tạo object và gọi method của generated class |
| `differentLoaderDifferentClass=true` | Hai loader khác nhau định nghĩa hai runtime class identities cùng tên |
| `parentStringShared=true` | Java `String` được parent/bootstrap tìm và cả loader cùng dùng |
| `resourceNotAutoPublished=true` | Lưu bytes trong map **không** tự tạo resource URL `Hello.class` |
| `appLoaderFindsGenerated=false` | Application loader không tự thấy generated class |
| `missingClassRejected=true` | Không có entry tương ứng thì `findClass` ném `ClassNotFoundException` |

Ba kiểm chứng `definedByMemoryLoader`, `repeatedLoadSameClass` và `differentLoaderDifferentClass` giúp phân biệt vai trò **defining loader**, cache loader và identity một cách quan sát được.

## 6. Tại sao `getResource("...Hello.class")` không tìm ra bytes?

Một sai lầm khi xây MemoryLoader là tưởng:

~~~java
loader.getResource("memory/generated/Hello.class");
~~~

sẽ tự động trả URL vì loader đã có map bytecode. **Không đúng.** `findClass` là hook tìm/define class, còn `findResource`/`findResources` là các hooks **khác** dành cho resource lookup.

Trong demo:

~~~text
Map entry:
  memory.generated.Hello -> byte[]

loadClass("memory.generated.Hello"):
  -> findClass -> defineClass -> Class<?>

getResource("memory/generated/Hello.class"):
  -> parent resource search
  -> child findResource (chưa override)
  -> null
~~~

Điều này bình thường. Nếu framework cần expose `.class` hoặc JSON/config/resource từ RAM, bạn phải bổ sung **resource store** và một giải pháp `URL`/`URLConnection`/stream phù hợp, hoặc cung cấp một API nhận bytes trực tiếp. Đừng ngầm ghi file lên disk chỉ để `getResource` hoạt động nếu yêu cầu ban đầu là chỉ dùng RAM.

### 6.1. Hai namespace nên được thiết kế rõ

- **Class map:** `Map<String,byte[]>`, key là binary class name.
- **Resource map:** `Map<String,byte[]>`, key là slash-separated resource path.

Hai map này có thể cùng trỏ tới bytes classfile cho đường `memory/generated/Hello.class`, nhưng chúng **không tự liên kết** với nhau. Cần xác định cách tra parent/resources và quyền công bố URL nếu ứng dụng cần.

### 6.2. CodeSource location có thể không có

Khi gọi `ClassLoader.defineClass(name, bytes, ...)` không truyền một `ProtectionDomain` tường minh, JDK có default protection domain. Bạn **không nên giả định CodeSource có location URL trỏ tới file JAR**.

Một class được nạp chỉ từ RAM có thể có `CodeSource` với `getLocation()==null`. Đây không phải dấu hiệu không có defining loader hoặc bytecode bị lỗi. Nếu cần provenance, cần **lưu metadata riêng khi nhận bytecode** (artifact ID/hash, origin đã xác minh, phiên bản) chứ không suy luận từ `getResource`.

## 7. Hai loader dùng cùng byte[] nhưng tạo hai Class<?> khác nhau

Runtime identity của ordinary class gồm:

~~~text
(binary class name, defining ClassLoader)
~~~

Trong demo, cả hai đều có tên `memory.generated.Hello`:

~~~java
Class<?> c1 = first.loadClass(NAME);
Class<?> c2 = second.loadClass(NAME);
System.out.println(c1 == c2); // false
~~~

Đây là snippet dùng biến demo đã tạo. Hai objects `Class<?>` khác nhau vì **defining loader khác nhau** dù bytes giống nhau.

Hệ quả:

- Không cast instance `Hello` từ loader A sang type `Hello` của loader B chỉ vì binary names giống nhau.
- Reflection với `Class<?>` của mỗi loader vẫn có thể sử dụng đúng instances.
- Nếu muốn host/plugin giao tiếp type-safe, hãy đưa **interface/DTO chung** vào parent loader và để generated class implements contract đó.
- Đừng để generated library chứa bản sao của shared API rồi vô tình child-first define API khác; dễ gặp `ClassCastException`, `LinkageError` hoặc `ServiceConfigurationError`.

### 7.1. Không thể “redefine” cùng class trong cùng loader bằng cách sửa map

Sau khi `first.loadClass(NAME)` thành công, đổi bytes trong map không làm JVM tự thay `c1`. Lần gọi `loadClass(NAME)` tiếp theo thường trả class đã có.

Nếu cố gọi `defineClass` lần nữa với cùng binary name trong cùng loader, có thể gặp `LinkageError` do duplicate definition. Muốn tạo version mới thường phải dùng **MemoryLoader instance mới** và quản lý references/lifecycle thích hợp; điều đó cũng tạo **runtime type identity mới**.

### 7.2. Khi nào class được initialized?

Lời gọi `MemoryLoader.loadClass(NAME)` tạo/load class; không tự đồng nghĩa chạy static initialization. Trong demo, `Hello` không có static initializer.

Nếu thêm:

~~~java
static {
    System.out.println("HELLO_INITIALIZED");
}
~~~

vào class source được compile, dòng này sẽ được in khi class được **initialized** (ví dụ new instance), không chỉ vì ta đã giữ một `Class<?>` từ `loadClass`.

Phân biệt:

~~~text
load/define -> verify/link/resolution (khi cần) -> initialize (active use)
~~~

Với `Class.forName(NAME, true, first)`, cờ `true` yêu cầu initialization, còn `Class.forName(NAME, false, first)` không yêu cầu initialize ngay. Không nhầm với `Class.forName(Module, String)` có hợp đồng khác.

## 8. Các lỗi và trường hợp khó cần xử lý

### 8.1. Không tìm thấy class trong map

~~~java
byte[] bytes = bytecodes.get(name);
if (bytes == null) {
    throw new ClassNotFoundException(name);
}
~~~

Đây là hành vi đúng trong `findClass`. `ClassNotFoundException` là checked exception cho đường tìm class chủ động; đừng trả `null` rồi để code bên ngoài lỗi `NullPointerException`.

Nếu generated class tham chiếu helper class mà map không chứa và parent cũng không tìm được, bạn có thể gặp `NoClassDefFoundError` khi VM cố liên kết/sử dụng helper. Cần lưu **mọi output classes**, không chỉ class entry point.

### 8.2. Class bytes sai format

`defineClass` có thể ném:

- `ClassFormatError`: classfile không đúng cấu trúc.
- `UnsupportedClassVersionError`: classfile target vượt khả năng JVM.
- `VerifyError`: bytecode vi phạm verification rules.
- `NoClassDefFoundError` hoặc các lỗi linkage khác khi dependencies/binary names không phù hợp.

Không biến mọi `LinkageError` thành `ClassNotFoundException`; chúng có nguyên nhân và cách khắc phục khác. Hãy giữ stack trace/cause khi debug.

### 8.3. Binary name không khớp nội dung class file

Nếu map có key `memory.generated.Hello` nhưng bytecode khai báo `another.pkg.Hello`, `defineClass(name, bytes, ...)` có thể ném `NoClassDefFoundError` hoặc linkage error về wrong name.

Phần quan trọng cần đảm bảo:

~~~text
requested name == map key == binary name trong classfile
~~~

Trong ví dụ, compiler cung cấp `className` chuẩn cho `MemoryFileManager.getJavaFileForOutput` nên map key được hình thành nhất quán với class output.

### 8.4. Package `java.*` bị bảo vệ

Không cố dùng custom loader để định nghĩa class trong `java.lang` hoặc packages mà JVM không cho phép application loader tạo. Có thể gặp `SecurityException` do quy tắc package bảo vệ. Cũng cần tuân thủ sealing và package metadata nếu nạp từ signed/sealed libraries.

Đây là quy tắc bảo vệ namespace nền tảng, **không phải một sandbox tổng quát** chống code không tin cậy.

### 8.5. Compiler không có ở runtime

`ToolProvider.getSystemJavaCompiler()==null` xảy ra với runtime không có compiler module (ví dụ một số custom jlink images). Hai lựa chọn:

1. **Biên dịch ở build/deploy phase**, chuyển classfile bytes đã kiểm tra vào chương trình.
2. Chạy trên JDK/runtime được cấu hình có compiler.

`MemoryLoader` chỉ cần bytes, không phụ thuộc có compiler. **Phần compile tại runtime mới cần `JavaCompiler`**.

### 8.6. Compiler phát sinh nhiều classes

Một source có inner class, anonymous class hoặc các top-level classes phụ có thể sinh nhiều outputs:

~~~text
memory.generated.Outer
memory.generated.Outer$Inner
memory.generated.Helper
~~~

Nếu file manager chỉ lưu `Outer.class`, đường sử dụng `Outer$Inner` có thể ném `NoClassDefFoundError` sau đó. Bởi vậy, `MemoryFileManager` của demo **lưu mọi tên do compiler phát sinh** thay vì một buffer duy nhất.

### 8.7. Dependencies compile-time và runtime khác nhau

`JavaCompiler` phải resolve được các dependencies khi compile source. `MemoryLoader` lại phải load các classes cần thiết khi runtime. Hai hoạt động có **classpath/loader graph riêng**:

~~~text
JavaCompiler classpath/modulepath -> compile source
MemoryLoader parent + bytecode map -> runtime resolution
~~~

Nếu source tham chiếu một thư viện có sẵn lúc compile nhưng library đó không ở parent/map khi chạy, chương trình vẫn có thể ném `NoClassDefFoundError`. Không tự suy rằng compile success nghĩa runtime visibility đủ.

### 8.8. Concurrency và race

Vì demo override **`findClass`, không phải `loadClass`**, implementation mặc định của `ClassLoader.loadClass` kiểm tra đã load và giữ khóa tương ứng. Đây là con đường dễ bảo trì.

Khi bạn làm compiler/file manager cho nhiều requests đồng thời:

- Không chia sẻ một `ByteArrayOutputStream` cho nhiều class names hoặc compilation tasks.
- Snapshot map chỉ sau khi compiler hoàn thành.
- Tạo immutable catalog per generation và không chỉnh mảng bytes khi loader đang đọc.
- Nếu cần custom child-first `loadClass`, phải bảo vệ `findLoadedClass + findClass/defineClass` trong cùng vùng đồng bộ theo `getClassLoadingLock(name)`.
- Tách caches/versions theo tenant/plugin và quản lý cleanup.

Race trong producer map là vấn đề riêng; bản thân ClassLoader synchronization không tự làm mọi map của ứng dụng thread-safe.

## 9. Bộ nhớ, unload và vòng đời loader

### 9.1. Bytes map khác JVM class metadata

Sau `defineClass` thành công, JVM có thông tin class riêng, không đồng nghĩa mảng bytes trong map là bản lưu duy nhất mà JVM cần giữ. Có ít nhất hai loại memory cần quan tâm:

- **Java heap:** `Map`, `byte[]` buffers, source strings, instances, metadata do ứng dụng giữ.
- **VM/native/metaspace & code caches:** metadata/linking/JIT structures tương ứng loaded classes.

Giữ mọi generations trong static registry sẽ khiến cả bytes map và ClassLoader/classes/instances sống lâu. Nếu build tool bên trong server compile hàng ngàn class mỗi phút, phải có retention/eviction policy.

### 9.2. Loader có thể unload khi nào?

Đối với ordinary class, unload liên quan đến việc **defining ClassLoader không còn reachable** theo điều kiện của JVM. Không có API:

~~~java
loader.unloadClass("memory.generated.Hello");
~~~

Dòng trên là **mã không tồn tại**. Bạn không thể xóa một entry khỏi map rồi yêu cầu JVM “quên” `Class<?>` đã define.

Một quy trình reload plugin hợp lý:

1. Dừng nhận requests cho generation cũ.
2. Chờ hoặc hủy tasks còn chạy bằng chính sách rõ ràng.
3. Dọn instances, callbacks, listeners, thread locals, reflection caches và TCCL references.
4. Bỏ references tới `MemoryLoader` và `Class<?>` của generation cũ.
5. Tạo **MemoryLoader instance mới** cho bytes generation mới.
6. Để garbage collector thu hồi class metadata khi đủ điều kiện; không đòi thời điểm chính xác.

`System.gc()` không bảo đảm unload ngay. Nên quan sát memory/metrics phù hợp nếu ứng dụng liên tục sinh classes.

### 9.3. MemoryLoader không phải security sandbox

Bytecode hợp lệ do custom loader định nghĩa vẫn chạy trong **cùng JVM process với host** và thường có quyền của process đối với file/network/threads/resources. Dùng ClassLoader riêng giúp **namespace isolation**, không tự tạo OS-level permission isolation.

Nếu cho người dùng nhập source Java không đáng tin rồi compile/load trong JVM phục vụ dữ liệu quan trọng, đó là rủi ro lớn. Compiler cũng có thể tiêu tốn CPU/RAM hoặc kích hoạt annotation processors nếu không kiểm soát. Trong demo đã dùng `-proc:none` để tắt annotation processing, nhưng **điều đó không tạo sandbox**.

Đối với code untrusted cần process/container/VM isolation với quyền OS tối thiểu, quotas, network policy, artifact provenance và IPC authorization. Trên JDK 24+, SecurityManager không còn bật được (JEP 486); không dựa vào `SecurityManager` hay `ProtectionDomain` để thiết kế permission sandbox hiện đại.

## 10. Nếu muốn triển khai MemoryLoader trong ứng dụng thực tế

### 10.1. Thiết kế input contract

Một API tạo loader nên nhận **một snapshot đáng tin**:

~~~java
Map<String, byte[]> snapshot = new HashMap<>();
input.forEach((name, bytes) -> snapshot.put(name, bytes.clone()));

MemoryLoader loader =
        new MemoryLoader(Map.copyOf(snapshot), sharedApiLoader);
~~~

Đây là snippet minh họa. `input` là map của caller; `sharedApiLoader` là parent ClassLoader chứa contract được thống nhất. Clone byte arrays giúp không phụ thuộc nguồn mutable. Cần kiểm tra `name` không null/rỗng và giới hạn tổng bytes/class count theo chính sách.

### 10.2. Đặt policy cho namespace

Không nhận mọi binary name từ untrusted source rồi cố define. Nên xác định:

- Prefix package nào thuộc generated implementation, ví dụ `memory.generated.*`.
- API packages nào phải delegate cho parent.
- Không tạo class trong packages dành cho JDK.
- Tránh trùng runtime package có sealing/certificate constraints không đáp ứng.
- Nếu source compile có public classes, package path và binary name phải phù hợp.

Map key/name từ compiler đáng tin hơn map key tùy ý, nhưng code input vẫn phải có nguồn/validation rõ khi deploy.

### 10.3. Chia API với host

Nếu host cần gọi generated implementation theo interface, ví dụ:

~~~java
package shared.api;

public interface Operation {
    String execute(String argument);
}
~~~

thì `Operation` nên do **shared API loader/parent** định nghĩa. Source class sinh động có thể `implements shared.api.Operation`, trong khi bytes của implementation nằm ở child MemoryLoader.

Lúc đó host có thể:

~~~java
Class<?> implementation = loader.loadClass("memory.generated.OpImpl");
Operation operation = (Operation)
        implementation.getDeclaredConstructor().newInstance();
System.out.println(operation.execute("hello"));
~~~

Đây là snippet kiến trúc, **không nằm trong chương trình demo chạy ở mục 5**, vì ví dụ chính chỉ dùng reflection. Khi áp dụng phải bảo đảm compiler tìm thấy `shared.api.Operation` và runtime child delegation nhìn thấy **cùng `Operation.class`** host dùng. Nếu parent/child định nghĩa hai versions của API thì `ClassCastException` vẫn xảy ra.

### 10.4. Logging và diagnostics

Khi compile failure, `DiagnosticCollector` có line/column/error code. Có thể xử lý:

~~~java
for (var diagnostic : diagnostics.getDiagnostics()) {
    System.err.println(
            diagnostic.getKind() + " line " + diagnostic.getLineNumber()
            + ": " + diagnostic.getMessage(null));
}
~~~

Đây là snippet ở method có `diagnostics`. Không log source code/paths/secrets của người dùng nếu chứa dữ liệu nhạy cảm. Với runtime errors, log:

- Binary class name được yêu cầu.
- Loader identity (và version/generation ID do host cấp, không chỉ `toString()`).
- Available class names trong snapshot (cẩn trọng với log privacy).
- Source/compiler diagnostics, target classfile major version.
- Full `ClassNotFoundException`/`LinkageError` stack trace và cause.
- Artifact bytes hash được tính từ dữ liệu được chấp nhận (nếu cần provenance).

### 10.5. Giới hạn kích thước và thời gian

Dynamic compilation là một pipeline có thể tiêu tốn memory. Production nên có:

- Max source size, số source files, số output classes, max classfile bytes.
- Timeouts đối với compilation/execution ở **process boundary cưỡng chế** nếu code không tin cậy.
- Cơ chế không giữ vô hạn compiled byte arrays.
- Compiler options kiểm soát classpath, module path, annotation processors.
- Metrics số generated classes và ClassLoader generations còn reachable.
- Cache key dựa trên source/options/dependency versions, không chỉ binary name.

Không coi `Map.copyOf` hoặc `try-with-resources` là đủ để giải quyết memory leaks của loaded classes.

## 11. So sánh các hướng nạp bytecode động

| Hướng | Khi nên dùng | Cần nhớ |
|---|---|---|
| `ClassLoader.findClass` + `defineClass` từ `byte[]` | Loader tự sở hữu namespace generated classes, nhiều binary names | Parent delegation, runtime identity, lifecycle |
| `Lookup.defineClass(byte[])` | Định nghĩa ordinary class trong package/loader của lookup class, với access constraints | Không tự tạo loader mới; package/nest/module rules |
| `Lookup.defineHiddenClass(...)` | Framework sinh implementation nội bộ không cần tra lại bằng tên | Hidden class không discoverable bằng `Class.forName`/`loadClass` |
| `URLClassLoader` | Nạp class/resources từ URLs/JARs đã đóng gói | Có filesystem/JAR path, không phải pure in-memory byte map |
| `JavaCompiler` + custom file manager | Compile source Java text thành bytecode RAM | Cần compiler module; output files/map riêng |
| Bytecode library/generator | Tạo classfile bytes không qua Java source compiler | Phải bảo đảm classfile hợp lệ và phù hợp JVM target |
| ModuleLayer | Định nghĩa named modules và mapping loader(s) | JPMS module graph/access không tự được tạo bởi map bytes |

Bài hiện tại chủ ý xây **ordinary class nạp từ bytecode memory**, không phải hidden class, không phải một JPMS named module và không phải nạp JAR resource bằng URLClassLoader.

## 12. Lỗi thường gặp và cách xử lý

| Triệu chứng | Nguyên nhân có thể | Cách điều tra |
|---|---|---|
| `getSystemJavaCompiler() == null` | Runtime thiếu compiler | Dùng JDK có compiler hoặc compile trước ở build |
| `compilation failed` | Source syntax/dependency/options | In `DiagnosticCollector`, kiểm tra `--release`/classpath |
| `ClassNotFoundException` cho class entry point | Sai map key/binary name; parent không thấy | In compiled key set, kiểm tra package |
| `NoClassDefFoundError` khi gọi method | Thiếu generated nested/helper class hoặc runtime dependency | Lưu mọi outputs, so loader graph |
| `NoClassDefFoundError: wrong name` | Key/name không khớp nội dung classfile | Tạo key theo compiler output |
| `UnsupportedClassVersionError` | Bytecode target lớn hơn JVM | Dùng `--release` phù hợp runtime |
| `ClassFormatError`/`VerifyError` | Bytes hỏng/sinh không hợp lệ | Kiểm tra generator/JavaFileObject và origin |
| `LinkageError` khi define lặp | Định nghĩa cùng tên hai lần trong một loader | Dùng `loadClass` mặc định, tạo loader mới cho generation |
| `ClassCastException` giữa hai cùng tên class | Defining loaders khác nhau | Dùng parent-shared API types |
| `getResource("Hello.class") == null` | Bytes map không tự là resource store | Bổ sung `findResource` hoặc API bytes riêng |
| Parent trả class khác với memory map | Parent-first loader có cùng binary name | Cô lập app classpath; namespace policy |
| Static initializer không chạy ngay sau `loadClass` | Load/link khác initialization | Kích hoạt active use hoặc `Class.forName(...,true,...)` |
| Memory tăng sau nhiều compiles | Maps/caches/handles/loaders cũ vẫn reachable | Giới hạn generations, dọn references, profile |
| Nhiều threads dùng cùng map tạo lỗi | Mutable bytes hoặc publish trước compile xong | Snapshot bất biến, defensive copy |
| Code compile thành công nhưng runtime fail | Compiler classpath khác memory loader graph | Kiểm tra compiled + runtime dependencies |
| Plugin có thể đọc file/secrets của host | Loader khác không là security sandbox | Process isolation/OS permissions |
| Code mới không được dùng sau cập nhật map | Class đã định nghĩa một lần trong loader | Tạo MemoryLoader generation mới |

### Checklist trước khi dùng production

1. Nguồn source/bytecode có đáng tin và được validate theo policy không?
2. Có quy định rõ packages/binary names cho generated classes không?
3. Bytes map đã được **deep copy** và đóng băng trước khi publish cho loader chưa?
4. Có lưu tất cả compiler outputs, kể cả nested/helper classes không?
5. Compiler runtime và target JVM version có tương thích không?
6. Compiler classpath có khớp dependencies mà MemoryLoader/parent cung cấp khi chạy không?
7. API xuyên boundary có đúng một defining loader không?
8. Có metrics, diagnostics, memory/classloader generation lifecycle không?
9. Có giới hạn lượng source/output bytes và compilation time không?
10. Nếu source untrusted, có **sandbox process-level** và quyền OS phù hợp không?

## 13. Bài tập thực hành nâng cao

1. Trong ví dụ, tại sao map key phải là `memory.generated.Hello` thay vì `memory/generated/Hello.class`?
2. Vì sao `findClass` phải ném `ClassNotFoundException` khi không có bytes?
3. Nếu compiler tạo thêm inner class `Hello$Inner`, file manager phải làm gì?
4. Vì sao method `getJavaFileForOutput` cần giữ output theo class name?
5. Vì sao `ClassLoader.loadClass` nên được giữ implementation mặc định cho demo?
6. Định danh runtime class phụ thuộc binary name và yếu tố nào nữa?
7. Hai loaders cùng bytes tạo hai `Class<?>` objects khác nhau có hợp lệ không?
8. Gọi `first.loadClass(NAME)` hai lần có define lại class không?
9. Khi nào `Class.forName(NAME, true, first)` khác `loadClass(NAME)` về initialization?
10. Vì sao `Map.copyOf` không bảo vệ mảng byte[] khỏi bị sửa?
11. Tại sao `getResource("memory/generated/Hello.class")` trả null trong demo?
12. Nếu parent đã có class cùng tên, loader nào được ưu tiên?
13. Nếu source compile nhưng generated class tham chiếu thư viện không có lúc runtime, điều gì xảy ra?
14. Vì sao không dùng `defineClass` để thay trực tiếp class đã định nghĩa trong một loader?
15. Có cần JDK compiler module để **load** bytes đã compile sẵn không?
16. Vì sao không nên đánh đồng MemoryLoader với sandbox?
17. Khi nào nên dùng `Lookup.defineHiddenClass` thay vì ordinary MemoryLoader?
18. Cách nào lưu generated classes để host gọi theo interface mà không reflection?
19. `ClassFormatError`, `UnsupportedClassVersionError` và `ClassNotFoundException` khác nhau về nguyên nhân?
20. Khi memory leak xuất hiện sau reload nhiều lần, kiểm tra references nào trước?
21. Vì sao mở rộng code sang multi-threaded compiler cần chiến lược snapshot riêng?
22. Muốn hỗ trợ dynamic config/resource JSON trong RAM, cần thêm API nào?
23. Tại sao loader không tự được unload khi xóa entry map?
24. Nếu kết quả compile nằm trong `byte[]` RAM, lúc nào JVM có thể cần thêm metaspace?
25. Khi nhận bytecode từ mạng, cần xác thực origin/integrity và hạn chế execution ra sao?

**Tiêu chí hoàn thành:** tự chạy chương trình với JDK 17+, giải thích được đầy đủ 10 dòng stdout; sửa generated source để tạo một method khác rồi tạo loader generation mới; trình bày được pipeline `JavaCompiler → JavaFileManager → Map<String,byte[]> → findClass → defineClass → Class<?>`; biết xử lý dependencies, type identity, resources và vòng đời.

## 14. Tài liệu chính thức

- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): `findClass`, `defineClass`, `loadClass`, `findLoadedClass`, class identity.
- [Java SE 21 — JavaCompiler](https://docs.oracle.com/en/java/javase/21/docs/api/java.compiler/javax/tools/JavaCompiler.html): compiler task, file manager và diagnostics.
- [Java SE 21 — JavaFileManager](https://docs.oracle.com/en/java/javase/21/docs/api/java.compiler/javax/tools/JavaFileManager.html): output JavaFileObject và location.
- [Java SE 21 — ForwardingJavaFileManager](https://docs.oracle.com/en/java/javase/21/docs/api/java.compiler/javax/tools/ForwardingJavaFileManager.html): wrapper cho standard file manager.
- [Java SE 21 — SimpleJavaFileObject](https://docs.oracle.com/en/java/javase/21/docs/api/java.compiler/javax/tools/SimpleJavaFileObject.html): source và bytecode output objects.
- [Java SE 21 — ToolProvider](https://docs.oracle.com/en/java/javase/21/docs/api/java.compiler/javax/tools/ToolProvider.html): lấy system JavaCompiler.
- [JVMS Java SE 21, Chapter 5](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html): loading, linking, initialization và runtime class identity.
- [JEP 486 — Permanently Disable the Security Manager](https://openjdk.org/jeps/486): lý do không dựa ClassLoader/legacy SecurityManager để chạy bytecode không đáng tin.

**Kết luận:** MemoryClassLoader là một cách dùng ClassLoader rất thực tế: JVM không quan tâm bytes đến từ file JAR hay RAM miễn classfile hợp lệ và loader định nghĩa đúng. Kết hợp `JavaCompiler` với `JavaFileManager`, bạn có thể biên dịch, giữ toàn bộ classfile outputs và nạp chúng trực tiếp trong memory. Điều khó hơn phần code là **duy trì runtime type identity, parent delegation, dependency visibility, resource policy, memory lifecycle và permission boundary** khi mở rộng hệ thống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-exercises-004">Cô lập hai phiên bản thư viện trong hai loader</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: Cô lập hai phiên bản thư viện trong hai ClassLoader (Java 17+)

Một tình huống kinh điển của **Java ClassLoader** là hai plugin cùng cần một thư viện có **cùng fully qualified class name**, nhưng phiên bản hoặc hành vi khác nhau. Ví dụ:

- Plugin A cần `lib.Formula` phiên bản **v1**: `evaluate(x) = x + 1`.
- Plugin B cần `lib.Formula` phiên bản **v2**: `evaluate(x) = x * 10`.
- Cả hai đều triển khai interface `spi.Calculator` do ứng dụng chủ (host) cung cấp.

Nếu mọi JAR nằm trên **cùng application classpath/class loader**, hai class files đều mang tên `lib.Formula`; một defining loader không thể sở hữu **hai runtime classes cùng binary name**. Trong bài thực hành này, ta tạo **hai đối tượng `URLClassLoader` khác nhau** cùng parent. Mỗi loader đọc JAR riêng và định nghĩa bản `lib.Formula` của mình; đồng thời cả hai dùng **cùng runtime interface `spi.Calculator`** qua parent delegation.

**Kết quả mong đợi:** Hai plugin có cùng implementation binary name `plugins.CalculatorImpl` vẫn hoạt động song song với kết quả khác nhau, không gây `ClassCastException` khi host ép về `spi.Calculator`. Ta sẽ kiểm tra trực tiếp class identity, defining loader, static state và trường hợp đối chứng khi dùng một loader chung.

## 1. Mục tiêu học tập và điều kiện cần

Sau bài này, bạn có thể:

1. Giải thích vì sao **(binary name, defining class loader)** xác định runtime class identity.
2. Đóng hai phiên bản của thư viện có **cùng package/class name** thành hai JAR riêng.
3. Tạo **hai `URLClassLoader`**, mỗi loader chỉ có URL JAR của phiên bản nó cần.
4. Đặt **SPI/shared API ở parent** để instances từ hai plugin đều có thể cast thành một interface chung.
5. Chứng minh **`lib.Formula` của A khác `lib.Formula` của B** dù tên giống hệt.
6. Chứng minh static fields/caches của mỗi runtime class độc lập.
7. Chạy trường hợp đối chứng **một loader chứa cả hai JAR** để thấy xung đột namespace/chọn phiên bản theo thứ tự URL.
8. Nhận diện nguyên nhân khiến isolation thất bại: parent classpath bị “nhiễm” thư viện private, sai loader, trùng SPI, lệch resource discovery hoặc lỗi binary compatibility.
9. Biết cách quản lý lifecycle, đóng loader và tránh hiểu nhầm class loader là sandbox bảo mật.

**Yêu cầu:** JDK 17 trở lên với `javac`, `java` và `jar` trên PATH; terminal Bash/Linux/macOS/Git Bash hoặc Windows PowerShell. Code dùng cú pháp/API tương thích Java 17, không cần Maven/Gradle, thư viện bên ngoài hay IDE.

Đây là **thực hành classpath/JAR thông thường**, không dùng `module-info.java`. Cuối bài có phần đối chiếu với JPMS/module layers.

## 2. Kiến trúc: một host và hai namespace plugin

```text
                  HOST / APPLICATION CLASSLOADER
                    ├── host.TwoVersions
                    └── spi.Calculator
                              ^
                              | parent delegation
              +---------------+-----------------+
              |                                 |
       URLClassLoader A                  URLClassLoader B
       URLs: [plugin-v1.jar]             URLs: [plugin-v2.jar]
          |                                 |
          +-- plugins.CalculatorImpl        +-- plugins.CalculatorImpl
          |                                 |
          +-- lib.Formula (v1)              +-- lib.Formula (v2)
              evaluate(x)=x+1                  evaluate(x)=x*10
```

Hai bản `lib.Formula` có **cùng binary name**, nhưng runtime identity khác vì **defining loader A** khác **defining loader B**:

```text
("lib.Formula", Loader A) != ("lib.Formula", Loader B)
```

Tương tự, `plugins.CalculatorImpl` trong JAR A và JAR B là hai runtime classes khác nhau.

Trong khi đó:

```text
a.loadClass("spi.Calculator")
    == b.loadClass("spi.Calculator")
    == spi.Calculator.class
```

vì cả hai loader **delegate** việc nạp `spi.Calculator` cho parent, nơi chỉ có **một** định nghĩa interface. Host dùng interface này để giao tiếp với hai implementation instances.

### Lưu ý: hai loader riêng chưa đồng nghĩa cô lập mọi thứ

`URLClassLoader` mặc định dùng **parent-first delegation**. Nếu parent **đã có `lib.Formula`**, cả A và B có thể lấy ngay class của parent và **không dùng** class private trong JAR tương ứng. Vì vậy runtime classpath của host trong thực hành này **chỉ** chứa `out/host` — tuyệt đối không thêm `out/v1`, `out/v2` hoặc `plugin-v1.jar` / `plugin-v2.jar` vào `-cp` của host.

Trong hệ thống thực tế, cần tách rõ:

| Đặt ở đâu? | Class/package | Lý do |
|---|---|---|
| **Parent/host** | `spi.Calculator`, host APIs, shared DTO | Một runtime type identity cho cả host và hai plugin |
| **Loader A** | `lib.Formula` v1, `plugins.CalculatorImpl` cho A | Dependency và implementation riêng |
| **Loader B** | `lib.Formula` v2, `plugins.CalculatorImpl` cho B | Dependency và implementation riêng |
| **Không đặt ở parent** | Bất kỳ bản private `lib.Formula` nào | Tránh parent-first “đè” phiên bản của child |
| **Không đóng vào từng plugin JAR** | Bản copy `spi.Calculator` | Tránh lệch identity/SPI class duplication |

## 3. Tạo cấu trúc project

Thực hành sử dụng **năm Java source files**:

```text
two-version-lab/
├── src/
│   ├── host/
│   │   ├── spi/
│   │   │   └── Calculator.java
│   │   └── host/
│   │       └── TwoVersions.java
│   ├── v1/
│   │   └── lib/
│   │       └── Formula.java
│   ├── v2/
│   │   └── lib/
│   │       └── Formula.java
│   └── plugin/
│       └── plugins/
│           └── CalculatorImpl.java
├── out/
│   ├── host/
│   ├── v1/
│   └── v2/
└── dist/
    ├── plugin-v1.jar
    └── plugin-v2.jar
```

**Vì sao implementation chỉ có một file source?** Ta muốn chứng minh **cùng một code implementation** có thể liên kết với **hai class definitions của thư viện khác nhau** khi biên dịch/đóng gói riêng. Cùng file `src/plugin/plugins/CalculatorImpl.java` được biên dịch **hai lần**: lần đầu cùng thư viện v1 vào `out/v1`, lần thứ hai cùng thư viện v2 vào `out/v2`.

**Vì sao `lib.Formula` có hai source files trùng fully qualified name?** Để biểu diễn hai phiên bản thư viện thật — chúng phải được biên dịch **vào hai output folders riêng**, không được cùng đẩy vào một thư mục `-d`.

## 4. Viết SPI dùng chung ở host

Tệp **`src/host/spi/Calculator.java`**:

```java
package spi;

public interface Calculator {
    String version();
    int apply(int value);
}
```

Đây là **contract** mà host biết trước. `version()` phục vụ quan sát kết quả, còn `apply(int)` gọi chức năng được plugin cung cấp.

Một thiết kế plugin tốt chỉ cho host giao tiếp thông qua **các types do parent/shared loader định nghĩa**; không để public method signatures của SPI lộ một private type như `lib.Formula` (vì hai loader có hai types khác nhau).

Nếu cần truyền object phức tạp, sử dụng DTO/record hoặc interfaces chung được host định nghĩa, chứ không truyền object `lib.Formula` trực tiếp qua boundary.

## 5. Tạo thư viện phiên bản v1

Tệp **`src/v1/lib/Formula.java`**:

```java
package lib;

public final class Formula {
    private static int calls;

    private Formula() {}

    public static String version() {
        return "v1";
    }

    public static int evaluate(int value) {
        ++calls;
        return value + 1;
    }

    public static int calls() {
        return calls;
    }
}
```

Ở v1, `evaluate(3)` trả **4**. `calls` là **static state** của runtime class `lib.Formula` do loader tương ứng định nghĩa.

Tệp này chỉ dùng Java chuẩn, không phụ thuộc `spi.Calculator`.

## 6. Tạo thư viện phiên bản v2

Tệp **`src/v2/lib/Formula.java`**:

```java
package lib;

public final class Formula {
    private static int calls;

    private Formula() {}

    public static String version() {
        return "v2";
    }

    public static int evaluate(int value) {
        ++calls;
        return value * 10;
    }

    public static int calls() {
        return calls;
    }
}
```

Ở v2, `evaluate(3)` trả **30**.

Hai source files có **cùng package `lib`, cùng class `Formula` và cùng method signatures** nhưng implementation khác. Đây là tình huống tốt để kiểm thử isolation vì **không phải** chỉ dùng các class khác tên, mà thật sự có **binary-name collision**.

### Nếu hai phiên bản có API khác nhau thì sao?

Bài lab cố ý giữ signatures giống nhau để so sánh behavior rõ ràng. Trong sản phẩm thực, một version có thể thay `evaluate` thành method khác. Khi plugin được biên dịch dựa vào v2 nhưng runtime vô tình nạp v1, có thể có `NoSuchMethodError`, `NoSuchFieldError`, `AbstractMethodError` hoặc các lỗi linking khác tùy kiểu thay đổi. Isolation đúng giúp mỗi plugin nhìn đúng dependency version nó được build/test với.

## 7. Tạo plugin implementation (biên dịch hai lần)

Tệp **`src/plugin/plugins/CalculatorImpl.java`**:

```java
package plugins;

import lib.Formula;
import spi.Calculator;

public final class CalculatorImpl implements Calculator {
    @Override
    public String version() {
        return Formula.version();
    }

    @Override
    public int apply(int value) {
        return Formula.evaluate(value);
    }
}
```

Tệp này **không hard-code “v1” hay “v2”**. Nó gọi `lib.Formula` được tìm thấy trong namespace của class loader định nghĩa `plugins.CalculatorImpl`.

Lúc runtime, bytecode của `CalculatorImpl` có **symbolic reference** đến `lib.Formula`. Khi JVM cần resolve reference này, loader gắn với defining class `plugins.CalculatorImpl` tham gia việc tìm class liên quan. Với loader A, `lib.Formula` là v1; với loader B, `lib.Formula` là v2.

Trong lab cả `CalculatorImpl` lẫn `Formula` thuộc cùng plugin JAR và được cùng child loader định nghĩa; điều này giữ dependency graph của mỗi plugin nhất quán.

**Không thử cho plugin A trực tiếp import một implementation class của plugin B** trong thiết kế isolation này: chúng là namespaces riêng, và cross-plugin type references cần contract/delegation rõ.

## 8. Viết chương trình host: hai loader riêng và một loader đối chứng

Tệp **`src/host/host/TwoVersions.java`**:

```java
package host;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import spi.Calculator;

public final class TwoVersions {

    private static Calculator plugin(URLClassLoader loader)
            throws Exception {
        Class<?> type = loader.loadClass("plugins.CalculatorImpl");
        return (Calculator) type.getDeclaredConstructor()
                .newInstance();
    }

    public static void main(String[] args) throws Exception {
        ClassLoader parent = TwoVersions.class.getClassLoader();
        URL v1 = Path.of(args[0]).toUri().toURL();
        URL v2 = Path.of(args[1]).toUri().toURL();

        try {
            parent.loadClass("plugins.CalculatorImpl");
            System.out.println("Parent sees implementation: true");
        } catch (ClassNotFoundException expected) {
            System.out.println("Parent sees implementation: false");
        }

        try (URLClassLoader a =
                     new URLClassLoader(new URL[]{v1}, parent);
             URLClassLoader b =
                     new URLClassLoader(new URL[]{v2}, parent);
             URLClassLoader both =
                     new URLClassLoader(new URL[]{v1, v2}, parent)) {

            Calculator c1 = plugin(a);
            Calculator c2 = plugin(b);

            System.out.println("Version A: " + c1.version());
            System.out.println("Version B: " + c2.version());
            System.out.println("Result A(3): " + c1.apply(3));
            System.out.println("Result B(3): " + c2.apply(3));

            Class<?> aImpl = a.loadClass("plugins.CalculatorImpl");
            Class<?> bImpl = b.loadClass("plugins.CalculatorImpl");

            System.out.println("Different implementation classes: "
                    + (aImpl != bImpl));

            System.out.println("Different dependency classes: "
                    + (a.loadClass("lib.Formula")
                        != b.loadClass("lib.Formula")));

            System.out.println("Same shared SPI class: "
                    + (a.loadClass("spi.Calculator")
                        == b.loadClass("spi.Calculator")));

            System.out.println("Loader A owns plugin: "
                    + (aImpl.getClassLoader() == a));

            System.out.println("Loader B owns plugin: "
                    + (bImpl.getClassLoader() == b));

            System.out.println("Calls A: "
                    + a.loadClass("lib.Formula")
                       .getMethod("calls").invoke(null));

            System.out.println("Calls B: "
                    + b.loadClass("lib.Formula")
                       .getMethod("calls").invoke(null));

            Calculator shared = plugin(both);
            System.out.println("Shared loader version: "
                    + shared.version());
            System.out.println("Shared result (3): "
                    + shared.apply(3));
            System.out.println("Shared version remains: "
                    + plugin(both).version());
        }
    }
}
```

Chương trình dùng **ba** đối tượng loader để so sánh trực tiếp:

- `a`: **chỉ** có URL JAR v1.
- `b`: **chỉ** có URL JAR v2.
- `both`: **cả hai URLs**, v1 trước v2, đóng vai trò **trường hợp đối chứng** — không phải thiết kế đáp ứng yêu cầu dùng đồng thời hai versions.

Mỗi child loader nhận **cùng `parent`** là defining loader của `host.TwoVersions`. Do host cũng chứa `spi.Calculator`, cả A và B delegate SPI lên cùng parent.

### Phân tích đoạn plugin(URLClassLoader)

```java
Class<?> type = loader.loadClass("plugins.CalculatorImpl");
return (Calculator) type.getDeclaredConstructor().newInstance();
```

Đây là **trích đoạn** từ program hoàn chỉnh ở trên. `loadClass` nhận binary name `plugins.CalculatorImpl`. Reflection gọi public no-arg constructor và tạo object. Cuối cùng, ép kiểu qua **`spi.Calculator` của host**.

Cast thành công vì trong cả hai JAR chỉ có implementation `plugins.CalculatorImpl` và thư viện private `lib.Formula`. Không có file `spi/Calculator.class` riêng nằm trong hai plugin JARs; host interface đã được defining parent cung cấp.

### Vì sao dùng try-with-resources?

`URLClassLoader` implements `Closeable`. Sau khi kết thúc thực hành, `try-with-resources` gọi `close()` cho A, B và both để giải phóng JAR/file handles do loaders quản lý.

**`close()` không phải “unloadClass”**: classes đã được định nghĩa vẫn có thể tồn tại khi còn strong references. Trong sản phẩm thật, không đóng loader trước khi plugin dừng nhận công việc và hoàn tất lifecycle, vì loader đã đóng có thể không đọc được class/resource mới từ các URLs của nó.

## 9. Biên dịch và đóng gói trên Linux/macOS/Git Bash

Từ thư mục `two-version-lab`:

```bash
mkdir -p out/host out/v1 out/v2 dist

# Host chỉ chứa SPI và chương trình thử nghiệm.
javac --release 17 -d out/host \
  src/host/spi/Calculator.java \
  src/host/host/TwoVersions.java

# Plugin v1: biên dịch Formula v1 + cùng CalculatorImpl.
# Compile classpath chỉ có shared SPI trong out/host.
javac --release 17 -cp out/host -d out/v1 \
  src/v1/lib/Formula.java \
  src/plugin/plugins/CalculatorImpl.java

# Plugin v2: Formula v2 + CalculatorImpl được biên dịch riêng.
javac --release 17 -cp out/host -d out/v2 \
  src/v2/lib/Formula.java \
  src/plugin/plugins/CalculatorImpl.java

# Đóng hai JAR, không đưa spi.Calculator vào plugin JAR.
jar --create --file dist/plugin-v1.jar -C out/v1 .
jar --create --file dist/plugin-v2.jar -C out/v2 .

# Quan trọng: CHỈ out/host nằm trên runtime -cp.
java -cp out/host host.TwoVersions \
  dist/plugin-v1.jar dist/plugin-v2.jar
```

**Đường dẫn classpath trên Windows khác dấu phân tách** nếu có nhiều entries: Windows dùng `;`, Linux/macOS dùng `:`. Trong demo này runtime classpath chỉ có **một** directory `out/host` nên không cần delimiter; hai đường JAR là **program arguments**, không phải các phần tử của `-cp`.

### Kiểm tra hai JAR thật sự độc lập

Sau khi build:

```bash
jar --list --file dist/plugin-v1.jar
jar --list --file dist/plugin-v2.jar
```

Cả hai phải có các entries sau (có thể còn manifest và directory entries):

```text
lib/Formula.class
plugins/CalculatorImpl.class
```

**Không nên thấy**:

```text
spi/Calculator.class
host/TwoVersions.class
```

trong plugin JAR. Nếu có, build process đã đưa shared API/host code vào plugin artifact; cần xem lại packaging để tránh class identity/trùng contract về sau.

## 10. Lệnh tương đương trên Windows PowerShell

```powershell
New-Item -ItemType Directory -Force -Path out/host, out/v1, out/v2, dist | Out-Null

javac --release 17 -d out/host src/host/spi/Calculator.java src/host/host/TwoVersions.java

javac --release 17 -cp out/host -d out/v1 src/v1/lib/Formula.java src/plugin/plugins/CalculatorImpl.java

javac --release 17 -cp out/host -d out/v2 src/v2/lib/Formula.java src/plugin/plugins/CalculatorImpl.java

jar --create --file dist/plugin-v1.jar -C out/v1 .
jar --create --file dist/plugin-v2.jar -C out/v2 .

java -cp out/host host.TwoVersions dist/plugin-v1.jar dist/plugin-v2.jar
```

Các lệnh này cũng làm việc khi các paths chứa khoảng trắng nếu được quote phù hợp khi tạo lệnh tương ứng. Để chắc chắn JDK đang sử dụng:

```powershell
java -version
javac -version
```

Nếu terminal không tìm được `javac` hay `jar`, hãy kiểm tra JDK (không chỉ JRE) và `JAVA_HOME`/PATH. Không tự động lấy các bản class đã biên dịch sẵn của IDE khi chưa biết IDE đang dùng compile target và output directories nào.

## 11. Kết quả thực nghiệm đã kiểm tra

Bộ **5 Java source files** và hai JAR trên được biên dịch với **`javac --release 17`** và chạy trên JDK 24 tương thích Java 17. Stdout chính xác cho thứ tự **v1 rồi v2**:

```text
Parent sees implementation: false
Version A: v1
Version B: v2
Result A(3): 4
Result B(3): 30
Different implementation classes: true
Different dependency classes: true
Same shared SPI class: true
Loader A owns plugin: true
Loader B owns plugin: true
Calls A: 1
Calls B: 1
Shared loader version: v1
Shared result (3): 4
Shared version remains: v1
```

Đây là **đầu ra của chính chương trình**, không phải JVM verbose class-loading log.

### Đọc hiểu từng kết quả

| Dòng kết quả | Ý nghĩa |
|---|---|
| `Parent sees implementation: false` | Host **không** nhìn thấy `plugins.CalculatorImpl`; implementation không vô tình nằm trên application classpath |
| `Version A: v1` | Loader A resolve `lib.Formula` từ JAR v1 |
| `Version B: v2` | Loader B resolve `lib.Formula` từ JAR v2 |
| `Result A(3): 4` | Bản v1 chạy phép `3 + 1` |
| `Result B(3): 30` | Bản v2 chạy phép `3 * 10` |
| `Different implementation classes: true` | `plugins.CalculatorImpl` của A và B là **hai `Class<?>` khác nhau** |
| `Different dependency classes: true` | `lib.Formula` của A và B là **hai runtime classes khác nhau** |
| `Same shared SPI class: true` | Cả hai dùng cùng runtime interface do parent định nghĩa |
| `Loader A/B owns plugin: true` | Đúng defining loader, không bị parent hoặc shared loader định nghĩa hộ |
| `Calls A/B: 1` | Mỗi bản `lib.Formula` có **static field `calls` riêng**; cùng chạy một lượt nên mỗi bên bằng 1 |
| `Shared loader version: v1` | Loader có URL v1 đứng trước v2 chọn một class implementation/Formula từ v1 |
| `Shared result (3): 4` | Loader chung thực thi behavior v1; không tự chọn “v2 cho request khác” |
| `Shared version remains: v1` | Gọi nạp implementation nhiều lần trên cùng loader vẫn được một runtime type, không chuyển sang v2 |

### Vì sao hai bản giữ static state riêng?

`static int calls` thuộc runtime class, không thuộc tên `"lib.Formula"` trên toàn JVM. Vì vậy:

```text
("lib.Formula", Loader A).calls = 1
("lib.Formula", Loader B).calls = 1

Đó là hai biến static riêng biệt của hai runtime classes.
```

Nếu có một shared library `lib.Formula` được parent định nghĩa, cả hai plugin có thể dùng **chung static state của bản parent**, trái yêu cầu isolation.

## 12. Đối chứng bổ sung: đảo thứ tự hai JAR

Không cần sửa mã nguồn. Đảo thứ tự arguments:

```bash
java -cp out/host host.TwoVersions \
  dist/plugin-v2.jar dist/plugin-v1.jar
```

PowerShell:

```powershell
java -cp out/host host.TwoVersions dist/plugin-v2.jar dist/plugin-v1.jar
```

Đầu ra xác minh:

```text
Parent sees implementation: false
Version A: v2
Version B: v1
Result A(3): 30
Result B(3): 4
Different implementation classes: true
Different dependency classes: true
Same shared SPI class: true
Loader A owns plugin: true
Loader B owns plugin: true
Calls A: 1
Calls B: 1
Shared loader version: v2
Shared result (3): 30
Shared version remains: v2
```

**Giải thích:**

- `a` nhận URL đầu tiên (lúc này v2), `b` nhận URL thứ hai (v1): **mỗi loader vẫn cô lập chính xác** phiên bản được cấp cho nó.
- `both` nhận URL array `[v2, v1]`: với `URLClassLoader` mặc định và parent không có class trùng tên, nó tìm trong URLs theo thứ tự và chọn bản v2.
- `both` **không thể định nghĩa thêm một bản `plugins.CalculatorImpl`/`lib.Formula` với cùng binary name** từ JAR còn lại sau khi đã chọn bản v2.

Đối chứng này cho thấy **thứ tự URL ảnh hưởng đến một loader chung**, chứ không tự cung cấp đa phiên bản. Thứ tự là nguyên tắc tra class/resource của `URLClassLoader` khi dùng một URL search path; với app server/container/custom loaders, lookup policy có thể khác.

## 13. Hai versions không tạo ra một kiểu chung cho implementation

Kết quả ở mục 11 chứng minh `a.loadClass("lib.Formula") != b.loadClass("lib.Formula")`, dù tên nhị phân giống nhau: **defining loader khác tạo runtime type khác**. Tương tự, hai bản `plugins.CalculatorImpl` không tự cast được sang nhau. Nhưng cả hai vẫn cast được sang **một `spi.Calculator` do parent định nghĩa**.

| Type | Defining loader | Có dùng xuyên hai plugin? |
| --- | --- | --- |
| `lib.Formula` v1/v2 | Lần lượt A và B | Không, mỗi bên là private dependency |
| `plugins.CalculatorImpl` v1/v2 | Lần lượt A và B | Không cast chéo implementation |
| `spi.Calculator` | Host/parent | Có, là shared contract |

Trong scope `main`, có thể kiểm tra thêm:

```java
Class<?> aType = a.loadClass("plugins.CalculatorImpl");
Class<?> bType = b.loadClass("plugins.CalculatorImpl");
System.out.println(aType.getName().equals(bType.getName())); // true
System.out.println(aType == bType); // false
System.out.println(bType.isInstance(c1)); // false
```

**Reflection không hợp nhất class identity.** Nó có thể gọi method của object thuộc loader cụ thể, nhưng type checks, access và các constraints của JVM vẫn áp dụng. Không đưa private `lib.Formula` vào chữ ký public của `spi.Calculator`; giao tiếp qua API/DTO do host định nghĩa.

## 14. Trường hợp isolation thất bại: parent classpath bị nhiễm thư viện

Hãy hình dung host bị cấu hình sai:

```bash
# KHÔNG phải cách chạy đúng cho bài này:
java -cp "out/host:dist/plugin-v1.jar" \
  host.TwoVersions dist/plugin-v1.jar dist/plugin-v2.jar
```

Trên Windows, dùng `";"` thay dấu `":"` trong `-cp` nếu muốn tái hiện, nhưng **không dùng lệnh sai làm production configuration**.

Lúc này parent/application class loader có thể tìm:

```text
plugins.CalculatorImpl
lib.Formula
```

trong `plugin-v1.jar`. Vì `URLClassLoader` mặc định **delegate parent first**, cả A và B có thể nhận class của parent (thay vì class từ JAR riêng):

```text
a.loadClass("plugins.CalculatorImpl") -> Parent class
b.loadClass("plugins.CalculatorImpl") -> Parent class
a.loadClass("lib.Formula")            -> Parent v1
b.loadClass("lib.Formula")            -> Parent v1
```

Điều này có thể khiến output phiên bản B không còn là v2, `Different implementation classes` không còn đúng và `getClassLoader() == b` là `false`.

**Cách khắc phục:** Đặt implementation/dependencies private chỉ trong URL list của plugin loader. Ở parent, giữ **shared SPI/contracts** và các thư viện thật sự cần được chia sẻ giữa host và plugins.

### Nếu parent cần chính thư viện đó?

Khi một framework host buộc mang `lib.Formula` ở parent, có thể cần **custom child-first loader có giới hạn namespace** để cho plugin dùng private copy, hoặc relocation/shading tại build time, hoặc cách ly plugin bằng process. Không nên tự override `loadClass` child-first cho **mọi** package: sẽ gây lỗi với `java.*`, API dùng chung, logging/reflective integration và type identity.

Nếu thật sự triển khai custom policy, thường:

```text
java.* / platform.*       -> always parent/platform
spi.* / shared.api.*      -> parent first
plugin private namespaces -> child first, controlled fallback
others                    -> defined policy (usually parent first)
```

Đây là **pseudocode chính sách**, không phải implementation Java hoàn chỉnh. Cần xử lý `getClassLoadingLock`, `findLoadedClass`, `resolveClass`, class loading errors và policy resources tương ứng.

## 15. Các lỗi thường gặp trong quá trình làm bài

### Lỗi 1 — ClassNotFoundException khi host thử nhìn thấy plugin

Trong demo có đoạn cố ý:

```java
parent.loadClass("plugins.CalculatorImpl");
```

và bắt `ClassNotFoundException` để in `Parent sees implementation: false`. Đây là **kết quả mong muốn**, vì host không được chứa class implementation.

Nếu `a.loadClass("plugins.CalculatorImpl")` mới ném `ClassNotFoundException` thì không phải như vậy. Hãy kiểm tra:

- `plugin-v1.jar` đã chứa entry `plugins/CalculatorImpl.class` chưa?
- `Path.of(args[0]).toUri().toURL()` có trỏ đúng file JAR không?
- Tên binary `plugins.CalculatorImpl` có viết đúng package/class không?
- Có vô tình dùng URL thư mục thiếu dấu `/` hoặc URL protocol không được hỗ trợ không?

### Lỗi 2 — NoClassDefFoundError: spi/Calculator

Plugin implementation cần giải quyết shared interface `spi.Calculator`. Nếu host không có interface này hoặc child parent không nhìn thấy host SPI, JVM có thể thất bại khi định nghĩa/liên kết implementation.

**Cách sửa:** Bảo đảm `spi.Calculator` nằm trong `out/host` và child loaders dùng đúng parent chứa SPI. Không giải quyết bằng cách lén đóng một bản interface độc lập trong mỗi plugin mà không kiểm tra type identity.

### Lỗi 3 — ClassCastException với hai bản spi.Calculator

Nếu custom child-first policy tự định nghĩa `spi.Calculator` riêng trong A và B, object A/B có thể không cast được về interface `spi.Calculator` mà host đang dùng, **mặc dù tên giống nhau**.

**Cách sửa:** Bảo đảm một defining loader chung cho SPI. In:

```java
System.out.println(
    a.loadClass("spi.Calculator") == Calculator.class);
```

Đây là snippet trong method có biến `a` và import `spi.Calculator`. Kết quả kỳ vọng là `true`.

### Lỗi 4 — NoSuchMethodError hoặc IncompatibleClassChangeError

Một implementation được build với thư viện v2 nhưng runtime lỡ resolve `lib.Formula` v1 có API khác. JVM có thể ném `NoSuchMethodError` hoặc một `LinkageError` phù hợp khi cần resolve method/class.

**Cách sửa:** Xác định runtime `lib.Formula` do loader nào định nghĩa và JAR nào cung cấp class. Build/test đúng cặp implementation + dependency, không chỉ dựa vào Maven dependency declaration.

### Lỗi 5 — Static state không tách

Nếu `Calls A` và `Calls B` không độc lập sau khi chạy hai plugin, có thể bạn đang:

- dùng một `lib.Formula` class do parent định nghĩa;
- dùng một shared loader chứa cả hai JARs;
- đặt counter/state ở một **shared class** do parent định nghĩa;
- vô tình giữ một singleton toàn host chứa state của cả plugin.

**Cách sửa:** Xem `Class.getClassLoader()` của private dependency và chỗ khai báo static fields. Class identity mới là yếu tố quyết định static storage.

### Lỗi 6 — Package sealing hoặc signer mismatch

Hai JAR có package trùng tên trong **cùng loader** đôi khi còn gặp `SecurityException` do JAR manifest `Sealed: true` hoặc signer certificate sets khác nhau. Hai plugin loaders riêng tạo runtime packages riêng, nhưng không biến signed JAR thành trusted hay loại bỏ mọi classloading checks.

**Cách sửa:** Kiểm tra packaging/manifest/signing policy; không catch lỗi rồi silently fallback sang nguồn class khác.

### Lỗi 7 — Resource của phiên bản khác

`ClassLoader.getResource` và `getResources` có lookup policy riêng. Nếu bạn viết custom child-first class loading nhưng để resource parent-first, code v2 có thể đọc config/schema của v1. Đặt resource namespaces theo plugin, dùng đúng loader hoặc `SomePluginClass.class.getResourceAsStream` khi phù hợp. Với `META-INF/services` cần bảo toàn nhiều provider descriptors thay vì chọn duy nhất một resource.

### Lỗi 8 — Đóng loader quá sớm

`URLClassLoader.close()` ngăn nạp những classes/resources **mới** do loader ấy cung cấp theo contract. Đóng ngay sau khi tạo instance rồi chạy long-lived plugin có thể gây lỗi khi code dùng một dependency hoặc resource lần đầu sau đó.

**Cách sửa:** Gắn loader với plugin lifecycle; dừng request/tasks, unregister services, đóng resources rồi cuối cùng mới đóng loader.

## 16. Quan sát nguồn lớp bằng JVM logs và Java API

### 16.1. In defining loader

Trong method `main` của demo đã có:

```java
System.out.println(aImpl.getClassLoader() == a);
System.out.println(bImpl.getClassLoader() == b);
```

Đây là cách xác nhận **defining loader object identity**. Đừng chỉ in tên class `lib.Formula` rồi kết luận nó thuộc version nào, vì hai tên giống hệt.

Có thể bổ sung thông tin code source:

```java
Class<?> formulaA = a.loadClass("lib.Formula");
var pd = formulaA.getProtectionDomain();
var source = pd == null ? null : pd.getCodeSource();
System.out.println(source == null
        ? "(unknown)"
        : source.getLocation());
```

Đây là **snippet minh họa** bên trong method. Code source là một chỉ báo về nguồn, **không phải bằng chứng toàn bộ code an toàn/được tin cậy**, và trong custom loader có thể là `null`.

### 16.2. Bật class loading log (HotSpot/OpenJDK)

```bash
java '-Xlog:class+load=info' -cp out/host \
  host.TwoVersions dist/plugin-v1.jar dist/plugin-v2.jar
```

PowerShell:

```powershell
java "-Xlog:class+load=info" -cp out/host host.TwoVersions dist/plugin-v1.jar dist/plugin-v2.jar
```

Log có thể cho thấy nhiều sự kiện `plugins.CalculatorImpl` / `lib.Formula` từ hai JARs khác nhau. Tùy JVM, logging verbosity và thời điểm class loading, định dạng source URL, thời gian và thứ tự dòng khác nhau. **Không viết exact stdout assertion** cho VM diagnostic log; hãy dùng nó để quan sát class origin và đối chiếu `getClassLoader()`.

### 16.3. API tra class theo loader tường minh

```java
Class<?> v1Type = Class.forName("lib.Formula", false, a);
Class<?> v2Type = Class.forName("lib.Formula", false, b);
System.out.println(v1Type == v2Type); // false
```

Đây là snippet có biến `a`, `b`. Overload `Class.forName(name, initialize, loader)` cho phép chỉ rõ loader; `false` không yêu cầu initialization. **`Class.forName("lib.Formula")` một tham số** không tự động dùng plugin loader hay thread context class loader: nó dùng defining loader của calling class. Từ host, nếu `lib.Formula` không có ở parent, lời gọi một tham số có thể thất bại.

## 17. Cần thêm plugin thứ ba thì tổ chức thế nào?

Mỗi plugin có thể gắn với một **generation/loader scope**:

```text
Host + shared SPI
    |
    +-- Plugin A / Loader A / lib.Formula v1
    +-- Plugin B / Loader B / lib.Formula v2
    +-- Plugin C / Loader C / lib.Formula v3
```

Plugin manager nên giữ metadata:

- Plugin ID và version.
- Đường JAR/URL nguồn.
- Loader instance và parent.
- Implementation class name.
- Shared SPI type/version tương thích.
- Instance/start/stop state.
- Service/resource registrations.
- Thread pool/TCCL policy và cleanup hooks.

Không để implementation class hoặc private dependency object từ loader A đi vào API của B nếu không có contract phù hợp. Nếu cần tương tác giữa plugins, nên dùng shared interfaces/DTOs do host định nghĩa, message bus hoặc adapters.

### Hot-reload không phải redefine cùng loader

Nếu `plugin-v1.jar` được thay bằng bản mới trên disk, runtime class `("lib.Formula", A)` đã định nghĩa **không tự thay đổi**. Một loader cũng không thể gọi `defineClass` lần thứ hai cùng binary name để tạo một runtime type mới trong cùng loader.

Thường tạo **loader generation mới**, nạp/cấu hình plugin mới, chuyển request sang generation đó rồi dừng generation cũ. Khi không còn references từ host/threads/caches, loader cũ có thể đủ điều kiện cho JVM/GC thu hồi; **không có API yêu cầu unload class tùy ý, tức thì**.

### Unload và close

`URLClassLoader.close()` đóng resources/JAR handles do loader quản lý; class unloading do JVM/GC quyết định và phụ thuộc loader reachability. `close()` không làm `Class<?>` đã load hoặc các instances biến thành vô hiệu ngay.

Các nguồn giữ loader cũ sống lâu: plugin instances trong registries, `ServiceLoader` caches, event listeners, TCCL của thread pools, `ThreadLocal`, scheduled tasks, reflection handles, logging/JDBC/MBeans và other host references.

## 18. Khi nào nên dùng custom ClassLoader hoặc JPMS thay URLClassLoader?

### Chọn URLClassLoader khi

- Mỗi plugin là một hoặc vài JARs/directories thông thường.
- Parent chỉ chứa shared API/JDK dependencies phù hợp.
- Parent-first delegation đủ để bảo đảm private library từ child được dùng.
- Không cần custom resource policy hoặc bytecode transformations.

Bài lab thuộc chính trường hợp này. **Không cần override `loadClass` hoặc viết `defineClass` trực tiếp** để hoàn thành isolation.

### Cân nhắc custom ClassLoader khi

- Có dependency cùng binary name **đã nằm trên parent** nhưng plugin bắt buộc dùng bản private, cần child-first **chỉ cho namespace cụ thể**.
- Classes đến từ memory/remote storage/container đặc thù.
- Cần resource lookup/`getResources` priority policy riêng, có test để tránh mismatch.
- Có codegen/transformation hoặc plugin framework điều phối namespace phức tạp.

Khi tự override `loadClass`, phải kiểm tra locking/`findLoadedClass`/`resolveClass` và không bắt/nuốt `LinkageError` như thể là `ClassNotFoundException`. Với `java.*` và shared API, phải giữ các nền tảng/type identities đúng.

### Cân nhắc JPMS ModuleLayer khi

- Plugin JARs là **named modules** có `module-info.java`, `requires`, `exports`, `opens` và `provides ... with ...`.
- Bạn cần module configuration/readability/service discovery và module boundaries rõ.
- Có yêu cầu mapping modules vào một hoặc nhiều class loaders.

`ModuleLayer.defineModulesWithOneLoader` và `defineModulesWithManyLoaders` là những chiến lược khác nhau. **Module không đồng nghĩa class loader**, và việc tách named modules chưa chắc tạo isolation tương tự mỗi plugin/JAR một loader. Nên thiết kế module graph, loader mappings và package conflicts rõ ràng trước khi đổi kiến trúc.

## 19. Bảo mật: hai loader riêng không phải hai sandbox

**Class loader isolation không giới hạn độc lập quyền truy cập file, network, CPU hoặc hệ điều hành của code plugin**. Nó chủ yếu cách ly **runtime type identity, versioned dependencies, static state** và hỗ trợ vòng đời.

Một plugin từ nguồn không đáng tin chạy trong cùng JVM vẫn có thể gây side effects qua nhiều APIs mà process được quyền dùng. `SecurityManager` là cơ chế lịch sử đã bị deprecated for removal ở JDK 17 và **permanently disabled từ JDK 24**; không lấy nó làm sandbox mặc định cho một kiến trúc Java hiện đại.

Đối với plugin không đáng tin, cân nhắc **worker process/container** riêng, OS permissions, network egress controls, resource quotas, chặn/kiểm soát native access và một RPC/IPC contract nhỏ. Không suy luận “có hai `URLClassLoader` thì plugin A không thể ảnh hưởng B” ngoài phạm vi class identity.

## 20. Kiểm thử tự động cần có nếu triển khai production

Có thể chuyển các quan sát trong lab thành assertions:

1. Parent không tìm thấy `plugins.CalculatorImpl`.
2. Loader A thực sự là defining loader của implementation A, loader B của implementation B.
3. `a.loadClass("lib.Formula") != b.loadClass("lib.Formula")`.
4. `a.loadClass("spi.Calculator") == b.loadClass("spi.Calculator") == Calculator.class`.
5. `c1.version()` trả `v1`, `c2.version()` trả `v2` khi A=v1, B=v2.
6. `c1.apply(3) == 4`, `c2.apply(3) == 30`.
7. Calls static counters độc lập ở hai versions.
8. Loader chung chứa cả JARs chọn một version theo URL policy; đảo thứ tự URL làm đối chứng.
9. Sau khi kích hoạt plugin, shared API calls hoạt động không cần private type từ loader khác.
10. Plugin lifecycle đóng resources/threads và không lưu references sang plugin loader đã retire.

Đừng viết test phụ thuộc tuyệt đối vào **thời điểm class unload** sau `System.gc()`; GC không phải cơ chế unload theo yêu cầu, và workload/collector có thể khác nhau. Kiểm thử hành vi do ứng dụng kiểm soát và dùng profiling/heap retaining paths để khảo sát memory.

### Một thử thách nhỏ: kiểm tra chéo identity

Trong `main`, sau khi có `aImpl`, `bImpl` và `c1`, thêm:

```java
System.out.println("Same binary name: "
        + aImpl.getName().equals(bImpl.getName()));
System.out.println("A object is B instance: "
        + bImpl.isInstance(c1));
```

Đây là **đoạn chèn minh họa**, không thuộc stdout kiểm chứng nguyên bản. Dự kiến:

```text
Same binary name: true
A object is B instance: false
```

Đó là minh chứng ngắn gọn cho khác biệt giữa **tên** và **runtime type**.

### Thử thách 2: một dependency mới chỉ ở v2

Tạo `lib.Extra` chỉ trong plugin v2 JAR và cho implementation v2 gọi class này. Khi giữ hai loaders riêng, v2 nhìn thấy `lib.Extra`, v1 không cần nhìn thấy. Nếu thay đổi app classpath/JAR URLs thiếu nhất quán, thử nghiệm có thể xuất hiện `NoClassDefFoundError`.

Đây là bài mở rộng **tự thực hiện**; cần thêm file/class và biên dịch lại. Không nên trình bày một output cố định khi chưa chạy đúng mã mở rộng.

### Thử thách 3: quan sát tài nguyên trùng tên

Thêm `plugin.properties` vào cả hai JARs với nội dung `version=v1` và `version=v2`. So sánh `a.getResourceAsStream("plugin.properties")` và `b.getResourceAsStream("plugin.properties")`, rồi `both.getResources("plugin.properties")`. Bạn sẽ thấy bài toán **resource lookup** khác class identity: một loader có thể liệt kê **nhiều tài nguyên cùng tên** nhưng chỉ có một runtime class cho mỗi binary name.

Khi thiết kế loader child-first cho classes, phải kiểm soát resource lookup priority tương ứng nếu resources gắn chặt với version.

## 21. Checklist trước khi hoàn tất bài

- [ ] Biên dịch **hai `lib.Formula` riêng**, không chung output directory.
- [ ] Hai plugin JAR chứa `lib/Formula.class` và `plugins/CalculatorImpl.class`.
- [ ] Host output chỉ có `spi/Calculator.class` và `host/TwoVersions.class`.
- [ ] Runtime command dùng `-cp out/host` — **không** đưa plugin JARs lên parent classpath.
- [ ] Hai loader `a`, `b` dùng cùng parent và **khác URL lists**.
- [ ] A trả version v1 / số 4; B trả version v2 / số 30.
- [ ] Hai implementation `Class<?>` và hai dependency `Class<?>` khác nhau.
- [ ] Shared SPI `Class<?>` giống nhau giữa A/B và host.
- [ ] Static counters độc lập.
- [ ] Một loader chung có cả JARs chọn version đầu theo URL order, không chạy song song hai definitions cùng tên.
- [ ] Đã đối chiếu khi đảo URL order và khi parent bị đưa nhầm implementation private.
- [ ] Biết cách đóng loader và không nhầm `close` với unload.
- [ ] Hiểu vì sao runtime type isolation **không phải security sandbox**.

## 22. Câu hỏi tự kiểm tra

**1. Hai file `lib/Formula.class` có tên giống nhau trong hai JARs. Chúng có thể cùng tồn tại trong JVM không?** Có, nếu chúng được **hai defining loaders khác nhau** định nghĩa.

**2. Vì sao không cho cả hai versions vào cùng một defining loader?** Một defining loader chỉ sở hữu một runtime class cho mỗi binary name; một version có thể shadow version còn lại.

**3. Trong lab, tại sao host có thể cast cả hai CalculatorImpl objects sang `spi.Calculator`?** Vì interface chỉ do parent định nghĩa và cả hai child delegate cho parent.

**4. Dùng hai URLClassLoader nhưng thêm `plugin-v1.jar` vào host `-cp` sẽ ra sao?** Parent-first có thể khiến child trả bản class v1 do parent định nghĩa, phá private-version isolation.

**5. Hai `static int calls` của `lib.Formula` có dùng chung không?** Không; đó là hai static fields trong hai runtime classes khác defining loaders.

**6. Tại sao kết quả loader chung đổi từ v1 sang v2 khi đảo URL order?** `URLClassLoader` tìm URLs theo thứ tự khi parent không cung cấp class, rồi chỉ giữ một definition cùng binary name trong loader đó.

**7. `Class.forName("lib.Formula")` trong host có tự chọn plugin loader theo thread context không?** Không; overload một tham số sử dụng defining loader của calling class. Dùng overload có loader argument nếu cần chỉ rõ.

**8. Hai implementation classes cùng tên có ép kiểu sang nhau được không?** Không chỉ vì tên giống; runtime class identity phụ thuộc defining loader.

**9. `URLClassLoader.close()` có xóa runtime classes ngay không?** Không; nó giải phóng nguồn/handles theo API, class unloading do JVM/GC quyết định.

**10. Hai class loaders riêng có bảo đảm chạy mã plugin không đáng tin an toàn không?** Không; cần process/OS isolation và quyền thực thi được kiểm soát.

**11. Khi nào chọn custom child-first loader?** Khi namespace private cần thắng bản trùng tên trên parent và đã có policy package, locking, resource lookup và shared API rõ ràng.

**12. Bài tập này chứng minh isolation ở mức nào?** **Class identity, dependency version và static state** của private classes; không tự chứng minh process-security isolation hoặc hot unload.

## 23. Nguồn tham khảo

- **Java SE 17 `ClassLoader`** — binary names, defining loader, parent delegation, `loadClass`, `findClass`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Java SE 17 `URLClassLoader`** — tra class/resources theo thứ tự URLs sau parent, `close()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- **JVMS Java SE 17 §5.3 — Creation and Loading** — runtime class identity gắn với binary name + defining loader: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- **Java SE 17 `Class.forName`** — overload có tên, `initialize` và loader tường minh: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **Java SE 17 `ServiceLoader`** — dùng shared service type, discovery providers, TCCL/module variants: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- **Java SE 17 `ModuleLayer`** — alternatives cho named-module plugins và loader mappings: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html
- **JLS Java SE 17 §§12.2–12.4** — loading, linking và initialization: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html

**Ghi nhớ:** **Hai versions của cùng `lib.Formula` có thể chạy song song vì được hai class loaders khác nhau định nghĩa.** Hãy để **SPI/shared contracts ở parent**, còn implementation và dependencies private nằm trong các JAR/loader riêng. Luôn xác minh `getClassLoader()`, class identity, static state và thứ tự URL; đừng nhầm “hai JARs” với “hai namespaces” nếu chúng đang được nạp bởi **một loader**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-exercises-005">Tạo plugin dùng API chung và phát hiện bằng ServiceLoader</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: Tạo plugin dùng API chung và phát hiện bằng ServiceLoader

> **Java Core → ClassLoader → Thực hành.** Xây một ứng dụng Java 17 gồm **host**, **shared SPI interface** và **hai plugin JAR riêng**; dùng `URLClassLoader` và `java.util.ServiceLoader` để host phát hiện provider implementations mà không cần import lớp triển khai. Bài lab có đủ source code, cấu trúc thư mục, file `META-INF/services`, lệnh `javac`/`jar`/`java`, stdout, kiểm chứng ClassLoader identity, ví dụ TCCL và xử lý sự cố. Không cần Maven, Gradle hay thư viện ngoài JDK.

## 1. Kết quả cần đạt

Sau bài học, bạn sẽ biết:

1. Viết **API contract chung** để host và plugins có thể trao đổi qua cùng runtime interface.
2. Tạo hai plugin implement contract nhưng ở **hai JAR độc lập**.
3. Khai báo mỗi provider theo giao thức Java SPI với `META-INF/services/<service-binary-name>`.
4. Tạo `URLClassLoader` trỏ tới JAR và dùng `ServiceLoader.load(Service.class, chosenLoader)` để tìm provider.
5. Chứng minh plugin implementation do plugin loader định nghĩa, còn service interface được chia sẻ từ parent loader.
6. Giải thích khác biệt giữa explicit ClassLoader và thread context class loader (TCCL).
7. Hiểu lazy discovery, `ServiceConfigurationError`, `reload()` và vòng đời của plugin/loader.
8. Mở rộng hệ thống mà không sửa host API mỗi lần thêm plugin.

**Bài lab chính đã được biên dịch bằng `javac --release 17` và chạy thành công trên OpenJDK 21.** Chỉ cần JDK 17 trở lên có lệnh `java`, `javac` và `jar`.

## 2. Kiến trúc: host chỉ biết API, không biết implementation

~~~text
                       Application/Host Loader
                       defines demo.spi.Processor
                                  |
                    +-------------+---------------+
                    |                             |
                    v                             v
            URLClassLoader upper            URLClassLoader reverse
            build/upper.jar                 build/reverse.jar
                    |                             |
            demo.upper.UpperProcessor      demo.reverse.ReverseProcessor
            META-INF/services/...          META-INF/services/...
                    |                             |
                    +-------------+---------------+
                                  |
                       ServiceLoader<Processor>
~~~

**Bất biến quan trọng:** JVM nhận diện runtime type bằng **binary name + defining ClassLoader**. Plugin có thể được nạp bởi loader riêng nhưng phải implement **chính runtime `Processor.class` mà host biết**. Nếu mỗi plugin tự định nghĩa bản `demo.spi.Processor` riêng với child-first loader, host có thể nhận `ServiceConfigurationError: not a subtype` hoặc `ClassCastException` dù tên class/interface trùng nhau.

Cách đơn giản nhất:

- Compile API ra thư mục riêng `build/api`.
- Host có API trên application classpath.
- Compile providers với API trên **compile classpath**.
- Đóng provider JAR **không kèm** `demo/spi/Processor.class`.
- Đặt parent của mỗi `URLClassLoader` bằng `Processor.class.getClassLoader()`.
- Khi chạy host **không** đưa implementation JARs lên application classpath; chúng chỉ được thêm qua `URLClassLoader`.

Parent-first delegation đảm bảo child có thể dùng `Processor` đã được parent định nghĩa, trong khi plugin-private implementations vẫn do child định nghĩa. Đừng dùng tên interface giống nhau làm phép thử tương thích; so sánh `Class<?>` identity hoặc `isAssignableFrom`.

## 3. Cây thư mục hoàn chỉnh

Tạo source/config files như sau; thư mục `build` được lệnh build tạo:

~~~text
service-plugin-lab/
├── src/
│   ├── api/demo/spi/Processor.java
│   ├── upper/demo/upper/UpperProcessor.java
│   ├── reverse/demo/reverse/ReverseProcessor.java
│   └── host/demo/host/PluginHost.java
├── config/
│   ├── upper/META-INF/services/demo.spi.Processor
│   └── reverse/META-INF/services/demo.spi.Processor
└── build/
    ├── api/
    ├── upper/
    ├── reverse/
    ├── host/
    ├── upper.jar
    └── reverse.jar
~~~

Tất cả lệnh bên dưới chạy từ **`service-plugin-lab` làm working directory**. Nếu chạy host từ folder khác, `Path.of("build/upper.jar")` có thể trỏ sai file vì đây là đường dẫn tương đối.

### 3.1. Source API chung

**Tệp `src/api/demo/spi/Processor.java`:**

~~~java
package demo.spi;

public interface Processor {
    String id();
    String process(String text);
}
~~~

Interface cố ý chỉ dùng `String` thuộc JDK trong signature. `id()` giúp host phân biệt các providers. `process()` minh họa capability của plugin. Trong ứng dụng thực, API có thể có shared DTO, error types hoặc lifecycle methods; tất cả types đi qua boundary phải có ownership/ABI rõ ràng.

**Không nên** để method signature trả về `demo.upper.InternalData` hay một library class private mà host không truy cập được. Phân tách API chung khỏi class riêng làm giảm xung đột phiên bản và bảo vệ type identity.

## 4. Plugin A — UpperProcessor

**Tệp `src/upper/demo/upper/UpperProcessor.java`:**

~~~java
package demo.upper;

import demo.spi.Processor;
import java.util.Locale;

public final class UpperProcessor implements Processor {
    public UpperProcessor() {}

    @Override
    public String id() {
        return "upper";
    }

    @Override
    public String process(String text) {
        return text.toUpperCase(Locale.ROOT);
    }
}
~~~

Provider là **public concrete class** và có **public no-argument constructor**. Đây là contract thích hợp cho provider triển khai trong **unnamed module/classpath**. `Locale.ROOT` tránh output phụ thuộc default locale; nhập `java 17` cho kết quả `JAVA 17`.

## 5. Plugin B — ReverseProcessor

**Tệp `src/reverse/demo/reverse/ReverseProcessor.java`:**

~~~java
package demo.reverse;

import demo.spi.Processor;

public final class ReverseProcessor implements Processor {
    public ReverseProcessor() {}

    @Override
    public String id() {
        return "reverse";
    }

    @Override
    public String process(String text) {
        return new StringBuilder(text).reverse().toString();
    }
}
~~~

Nhập `java 17` cho kết quả `71 avaj`. `StringBuilder.reverse()` đủ dùng cho ví dụ ASCII này; không nên coi nó là thuật toán đảo các Unicode grapheme clusters phức tạp trong mọi ngôn ngữ.

Hai plugins không import lẫn nhau. Host cũng không import `UpperProcessor` hay `ReverseProcessor`. Đây là điểm cốt lõi để thêm implementation mới mà không sửa host source.

## 6. Khai báo SPI bằng `META-INF/services`

Với classpath provider, file cần mang tên **binary name đầy đủ của service type**:

~~~text
META-INF/services/demo.spi.Processor
~~~

**Tệp `config/upper/META-INF/services/demo.spi.Processor` có một dòng:**

~~~text
demo.upper.UpperProcessor
~~~

**Tệp `config/reverse/META-INF/services/demo.spi.Processor` có một dòng:**

~~~text
demo.reverse.ReverseProcessor
~~~

Quy tắc:

- Dùng plain UTF-8 text, không viết `.class`, `.java`, dấu slash hay biểu thức `new`.
- Mỗi dòng chứa một fully qualified **provider binary name**; có thể có nhiều providers với các dòng riêng.
- Path chính xác là `META-INF/services` (services số nhiều), phân biệt chữ hoa/thường trên nhiều môi trường.
- Descriptor phải được **copy vào chính JAR output**; chỉ đặt file ở source tree không đủ.
- Provider phải visible với loader, implement đúng shared `Processor`, constructor hợp lệ và không fail khi tạo instance.

ServiceLoader **không tự quét mọi `.class`** trong plugin JAR để tìm lớp implement một interface. Nếu không có descriptor (và không có provider declaration của named module), kết quả discovery có thể **trống** dù class implementation vẫn có trong JAR.

**Lưu ý:** Khi dùng named JPMS modules, bạn có thể khai báo `provides ... with ...` trong `module-info.java` thay vì cơ chế classpath descriptor. Bài lab này cố ý dùng **unnamed modules và SPI descriptor** để xem rõ ClassLoader.

## 7. Host Java code — chỉ biết interface `Processor`

**Tệp `src/host/demo/host/PluginHost.java`:**

~~~java
package demo.host;

import demo.spi.Processor;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ServiceLoader;

public final class PluginHost {
    static void run(String label, ClassLoader loader) {
        ServiceLoader<Processor> discovered =
                ServiceLoader.load(Processor.class, loader);

        int count = 0;
        for (Processor provider : discovered) {
            count++;
            System.out.println(label + ":" + provider.id()
                    + "=" + provider.process("java 17"));
            System.out.println(label + ":owned="
                    + (provider.getClass().getClassLoader() == loader));
        }
        System.out.println(label + ":count=" + count);
    }

    public static void main(String[] args) throws Exception {
        ClassLoader shared = Processor.class.getClassLoader();

        URL upper = Path.of("build/upper.jar")
                .toAbsolutePath().toUri().toURL();
        URL reverse = Path.of("build/reverse.jar")
                .toAbsolutePath().toUri().toURL();

        try (URLClassLoader upperLoader =
                     new URLClassLoader(new URL[] {upper}, shared);
             URLClassLoader reverseLoader =
                     new URLClassLoader(new URL[] {reverse}, shared)) {

            run("host", shared);
            run("upper", upperLoader);
            run("reverse", reverseLoader);

            System.out.println("same api="
                    + (upperLoader.loadClass(Processor.class.getName())
                        == Processor.class
                    && reverseLoader.loadClass(Processor.class.getName())
                        == Processor.class));

            Thread t = Thread.currentThread();
            ClassLoader before = t.getContextClassLoader();
            ServiceLoader<Processor> tcclServices;

            try {
                t.setContextClassLoader(upperLoader);
                tcclServices = ServiceLoader.load(Processor.class);
            } finally {
                t.setContextClassLoader(before);
            }

            System.out.println("tccl first="
                    + tcclServices.findFirst().orElseThrow().id());
        }
    }
}
~~~

Phân tích:

- `ClassLoader shared = Processor.class.getClassLoader()` chọn loader đang thực sự định nghĩa shared API. Không đoán dựa vào loader tên “system” nếu chạy trong container/custom runtime.
- `new URLClassLoader(new URL[]{upper}, shared)` tạo loader trỏ tới provider JAR, với parent là shared API loader.
- `ServiceLoader.load(Processor.class, loader)` dùng loader truyền vào để tìm provider-configuration resources và provider classes (trong lab classpath này).
- `for (Processor provider : discovered)` là bước **lazily tìm và tạo instance**. `ServiceLoader.load` tự nó chưa nhất thiết instantiate providers.
- `provider.getClass().getClassLoader() == loader` xác nhận **defining loader** của implementation trong cấu hình lab.
- `loadClass(Processor.class.getName()) == Processor.class` xác nhận **shared runtime contract identity**.
- `try-with-resources` đóng hai `URLClassLoader` khi toàn bộ ví dụ dùng xong; không gọi `close()` cho các provider objects (API demo chưa khai báo lifecycle này).

**Không áp dụng bừa `owned==true` cho mọi ServiceLoader ngoài lab**: với parent-first delegation hoặc named module providers, implementation có thể do loader khác định nghĩa. Đó không có nghĩa ServiceLoader sai; cần hiểu defining loader thực.

### 7.1. Phân biệt explicit loader với TCCL

Overload:

~~~java
ServiceLoader.load(Processor.class, upperLoader)
~~~

lấy **upperLoader** do host đưa vào, không phụ thuộc TCCL tình cờ của thread.

Overload:

~~~java
ServiceLoader.load(Processor.class)
~~~

tương đương với:

~~~java
ServiceLoader.load(
    Processor.class,
    Thread.currentThread().getContextClassLoader())
~~~

Trong `main`, TCCL được tạm đặt thành upper loader đúng lúc tạo `tcclServices`, rồi khôi phục **trước** `findFirst()`. ServiceLoader vẫn dùng loader đã chọn ban đầu, cho kết quả `upper`.

Khi sử dụng pool worker, **đừng cho rằng** TCCL của thread submitter được truyền sang worker đã tồn tại. Nếu framework chỉ dùng convenience overload, set/restore TCCL ở **chính thread thực thi discovery**, trong `try/finally`. Nếu có thể, plugin host nên ưu tiên overload hai tham số vì owner rõ ràng hơn.

## 8. Build hai plugin JAR và chạy host

Sử dụng POSIX shell (Linux/macOS/Git Bash). Từ root `service-plugin-lab`:

~~~bash
# 1. Output folders + đúng đường cho SPI descriptors.
mkdir -p build/api build/upper/META-INF/services \
         build/reverse/META-INF/services build/host

# 2. Shared API được biên dịch riêng.
javac --release 17 -d build/api \
    src/api/demo/spi/Processor.java

# 3. Providers dùng shared API ở compile classpath.
javac --release 17 -cp build/api -d build/upper \
    src/upper/demo/upper/UpperProcessor.java

javac --release 17 -cp build/api -d build/reverse \
    src/reverse/demo/reverse/ReverseProcessor.java

# 4. Copy SPI descriptor vào root output mỗi provider.
cp config/upper/META-INF/services/demo.spi.Processor \
   build/upper/META-INF/services/demo.spi.Processor

cp config/reverse/META-INF/services/demo.spi.Processor \
   build/reverse/META-INF/services/demo.spi.Processor

# 5. Đóng đúng hai JAR, không chứa shared Processor.class.
jar --create --file build/upper.jar -C build/upper .
jar --create --file build/reverse.jar -C build/reverse .

# 6. Host biên dịch chỉ với API.
javac --release 17 -cp build/api -d build/host \
    src/host/demo/host/PluginHost.java

# 7. Runtime host KHÔNG có upper.jar/reverse.jar trong classpath.
java -cp "build/api:build/host" demo.host.PluginHost
~~~

Trên Windows, `javac`/`jar` tương tự, nhưng tạo/copy thư mục bằng PowerShell/CMD và **classpath separator là `;`**:

~~~powershell
java -cp "build/api;build/host" demo.host.PluginHost
~~~

Không chạy `java -jar build/upper.jar`: đó là provider JAR không có `Main-Class`. Class có `main` nằm trong host.

### 8.1. Kiểm tra đúng artifacts trong JAR

~~~bash
jar --list --file build/upper.jar
jar --list --file build/reverse.jar
~~~

`upper.jar` phải chứa ít nhất:

~~~text
demo/upper/UpperProcessor.class
META-INF/services/demo.spi.Processor
~~~

`reverse.jar` phải chứa ít nhất:

~~~text
demo/reverse/ReverseProcessor.class
META-INF/services/demo.spi.Processor
~~~

`jar --list` có thể in thêm `META-INF/`, `MANIFEST.MF` và directory entries; không assert danh sách chỉ có hai dòng hay thứ tự in. **Không** có `demo/spi/Processor.class` trong các JAR plugin.

## 9. Stdout của lab đã kiểm chứng trên OpenJDK 21

~~~text
host:count=0
upper:upper=JAVA 17
upper:owned=true
upper:count=1
reverse:reverse=71 avaj
reverse:owned=true
reverse:count=1
same api=true
tccl first=upper
~~~

Giải thích từng nhóm:

- `host:count=0`: application/host loader biết interface, **không có** plugin descriptor JARs trên host classpath.
- `upper:upper=JAVA 17`: `upperLoader` đọc descriptor của `upper.jar` và instantiate `UpperProcessor`.
- `upper:owned=true`: implementation class do `upperLoader` định nghĩa.
- `upper:count=1`: chỉ một provider visible qua scope này.
- `reverse:reverse=71 avaj`: `reverseLoader` thấy provider riêng của `reverse.jar`.
- `reverse:owned=true` và `reverse:count=1`: một implementation do đúng reverse loader định nghĩa.
- `same api=true`: hai loaders delegate `demo.spi.Processor` về shared parent, tránh duplicate type identity.
- `tccl first=upper`: `ServiceLoader.load(Processor.class)` đã dùng TCCL upper loader lúc factory call.

Kết quả này đáng tin trong **đúng cấu hình của lab**; nếu bạn đặt plugin JARs vào host classpath, host có thêm providers, dùng parent khác hoặc custom child-first loader thì không nên kỳ vọng mọi dòng giống hệt.

## 10. ServiceLoader discovery phía sau hậu trường

Với classpath providers:

1. Caller chỉ ra `Processor.class` (một runtime class/interface cụ thể) và một ClassLoader.
2. Khi bắt đầu iteration, ServiceLoader tìm `META-INF/services/demo.spi.Processor` qua cơ chế `getResources` của loader.
3. Nó đọc các dòng binary names; có thể có comments bắt đầu `#` và dòng trống theo specification.
4. Nó nạp provider class bằng loader/module visibility rules, kiểm tra khả năng cung cấp đúng service.
5. Với classpath deployment, nó gọi **public no-argument constructor** và trả `Processor` instance.
6. Provider đã instantiate được giữ trong **provider cache của ServiceLoader instance**; iteration sau có thể thấy cached objects trước.

**Không** dùng `Class.forName("demo.upper.UpperProcessor")` từ default system loader như một replacement cho ServiceLoader. Host sẽ hard-code class name và có thể nhìn sai loader. SPI cho phép provider thêm/bớt với metadata thay vì sửa host.

### 10.1. Một descriptor nhiều providers

Một `META-INF/services/demo.spi.Processor` có thể có:

~~~text
demo.upper.UpperProcessor
demo.reverse.ReverseProcessor
~~~

Điều kiện là loader tìm được **cả hai class**. Nếu tạo một `URLClassLoader` có URLs của cả `upper.jar` và `reverse.jar`, ServiceLoader có thể tìm cả hai provider descriptors. Đây là cách triển khai nhóm plugins trong **cùng loader namespace**, khác mô hình hai loader riêng của lab.

**Bài tập snippet** bên trong `main` với `upper`, `reverse` và `shared`:

~~~java
try (URLClassLoader combined = new URLClassLoader(
        new URL[] {upper, reverse}, shared)) {

    ServiceLoader<Processor> services =
            ServiceLoader.load(Processor.class, combined);

    java.util.List<String> names = new java.util.ArrayList<>();
    for (Processor provider : services) {
        names.add(provider.id());
    }
    names.sort(String::compareTo);
    System.out.println(names);
}
~~~

Nếu cả hai JARs đúng, dòng cuối dự kiến:

~~~text
[reverse, upper]
~~~

Sắp xếp trước khi in để kết quả không dựa vào provider discovery order. Code này là **snippet mở rộng**, không nằm trong stdout của `PluginHost.java` ở mục 9.

### 10.2. Lazy loading, `findFirst`, `stream` và `reload`

~~~java
ServiceLoader<Processor> services =
        ServiceLoader.load(Processor.class, upperLoader);

// Java 9+: provider instance đầu tiên nếu có.
Processor first = services.findFirst().orElseThrow();

// Stream descriptor trước khi khởi tạo object:
services.stream().forEach(p -> {
    System.out.println(p.type().getName());
    // p.get() tạo/nhận provider instance theo contract.
});

// Xóa cache provider của ServiceLoader instance này:
services.reload();
~~~

Đây là **snippet** trong scope có `upperLoader`. `stream()` trả `ServiceLoader.Provider<S>`; `type()` và `get()` giúp inspect type, instantiate có chủ đích. `reload()` chỉ làm mới discovery/provider cache **trên ServiceLoader instance hiện tại**; nó **không** đổi loader đã chọn, không close objects và không redefine classfile của JAR trong cùng defining loader.

ServiceLoader **không thread-safe**; không dùng một instance chung cho nhiều threads vừa iterate vừa reload mà không kiểm soát đồng bộ/lifecycle.

## 11. Biến thể và lỗi cố ý để tự kiểm chứng

### 11.1. Xóa SPI descriptor khỏi JAR

Nếu `upper.jar` không có `META-INF/services/demo.spi.Processor`, khi gọi `run("upper", upperLoader)` không có provider ở plugin-scope này, có thể in `upper:count=0`. **Nhưng** phần cuối `tcclServices.findFirst().orElseThrow()` trong host main sẽ ném `NoSuchElementException` nếu không có provider nào; không nói cả ứng dụng chạy hết như trước.

### 11.2. Viết sai class name trong descriptor

Nếu viết `demo.upper.MissingProcessor`, Java sẽ không tìm provider class và có thể ném `ServiceConfigurationError` **lúc iterator/findFirst tìm provider**, không nhất thiết khi `ServiceLoader.load` được gọi. Sửa binary name theo JAR entry path; kiểm tra package trong source và `jar --list`.

### 11.3. Provider không phải subtype của shared API

Nếu plugin implement một **bản `Processor` được child-first loader tự define lại**, host đang dùng `Processor.class` từ parent; hai types không tương thích dù `Class.getName()` bằng nhau. ServiceLoader có thể ném `ServiceConfigurationError: ... not a subtype`. Nếu plugin JAR có copy API nhưng loader **vẫn parent-first**, riêng việc copy đó chưa chắc gây lỗi vì class cha có thể thắng. Phải quan sát defining loaders thực tế.

### 11.4. Constructor không public hoặc ném lỗi

Classpath provider cần public concrete class và public no-arg constructor. Sai contract hoặc constructor throw dẫn tới `ServiceConfigurationError`/cause liên quan khi instantiate. Không nên catch mọi `Throwable` rồi giả vờ provider vắng mặt.

### 11.5. Đưa plugin JAR lên host classpath

Nếu command `java -cp` bao gồm `build/upper.jar`, application loader có thể nhìn thấy provider descriptor và UpperProcessor; `host:count=0` không còn đúng. Vì parent-first, `upperLoader` cũng có thể delegate implementation về host, làm `upper:owned=false`. Đây là lỗi cấu hình ClassLoader rất phổ biến.

### 11.6. Tìm được resource nhưng không tạo được instance

Có thể kiểm tra đúng loader:

~~~java
String path = "META-INF/services/" + Processor.class.getName();
var urls = upperLoader.getResources(path);
while (urls.hasMoreElements()) {
    System.out.println(urls.nextElement());
}
~~~

Snippet cần đặt trong `main`; method có thể ném `IOException`. Có resource **không chứng minh** implementation class tồn tại, subtype đúng, constructor public hoặc dependency đã đủ. Nếu một lỗi xảy ra, xem full `ServiceConfigurationError` stack/cause.

## 12. So sánh explicit loader với TCCL

| API | Loader dùng làm điểm bắt đầu | Khi phù hợp |
| --- | --- | --- |
| `ServiceLoader.load(S.class, pluginLoader)` | Loader tường minh của plugin | Host biết owner, tránh phụ thuộc thread |
| `ServiceLoader.load(S.class)` | TCCL tại lúc factory call | Framework dùng ambient application context |
| `ServiceLoader.load(S.class, null)` | System classloader (hoặc bootstrap nếu không có system) | Không có nghĩa “không load” |
| `ServiceLoader.loadInstalled(S.class)` | Platform classloader (Java 9+) | Không phải cách chung tìm app plugin JAR |
| `ServiceLoader.load(layer, S.class)` | Named modules trong layer và các parent layers | JPMS plugin ModuleLayer |

`ServiceLoader.load(S.class)` bắt TCCL **lúc tạo** ServiceLoader, dù provider instantiation có thể diễn ra lazy **sau**. Nếu worker pool tái sử dụng thread mà còn TCCL của plugin cũ, một request mới có thể thấy nhầm providers và giữ loader cũ alive. Dùng explicit loader hoặc **set/restore TCCL trong `try/finally`** khi framework API không cho truyền loader.

Không coi TCCL là defining loader của service type. `Processor.class.getClassLoader()` vẫn là loader đã định nghĩa API theo JVM type identity.

## 13. JPMS (Java 9+) là biến thể khác, không phải bắt buộc của lab

Khi dùng named modules, provider có thể được khai báo ở `module-info.java`:

**API module:**

~~~java
module demo.api {
    exports demo.spi;
}
~~~

**Provider module:**

~~~java
module demo.upper {
    requires demo.api;
    provides demo.spi.Processor
        with demo.upper.UpperProcessor;
}
~~~

**Consumer module:**

~~~java
module demo.host {
    requires demo.api;
    uses demo.spi.Processor;
}
~~~

Đây là **ba descriptors thuộc ba named modules khác nhau**, không gộp vào một tệp. `uses` là khai báo consumer sử dụng service; `provides ... with` là khai báo provider; `requires` cho phép đọc module API; `exports` cho phép package API được truy cập.

Với named modules:

- Không bắt buộc có classpath `META-INF/services` cho provider khai báo bằng `provides`.
- Provider implementation package không cần export công khai chỉ để ServiceLoader tạo provider theo module contract.
- Provider modules phải có mặt trong **resolved module graph/layer**, không chỉ nằm tùy ý trong folder.
- `ServiceLoader.load(layer, S.class)` tìm providers trong named modules ở layer đó và parent layers, **không** tự tìm providers trong unnamed modules qua SPI files.
- Named-module provider có constructor/provider-method rules riêng; đừng lấy static `provider()` method làm giải pháp thiếu public constructor trong **classpath demo**.

Nếu chuyển sang ModuleLayer, phải viết lại build/launch module-path và module-resolution logic phù hợp; **không** chỉ thêm `module-info.java` vào JAR demo và giữ nguyên mọi kỳ vọng classpath.

## 14. Lifecycle, hot reload và độ an toàn

Trong lab, `try-with-resources` đóng `URLClassLoader` và file/JAR handles mà nó sở hữu. Tuy nhiên `URLClassLoader.close()`:

- Không hủy class đã nạp, không làm static fields reset.
- Không tự đóng HTTP/DB connections, streams hoặc threads do provider tự tạo.
- Không tự unregister listeners, `ServiceLoader` provider caches, DI/reflection maps ở host.
- Không bảo đảm class unloading/Metaspace giảm ngay.
- Không có giao thức an toàn để gọi đồng thời với thread khác đang load class; Javadoc nói kết quả load khi `close()` cạnh tranh là undefined.

Muốn cập nhật `upper.jar` từ V1 sang V2:

1. Xác minh artifact/version và tạo **loader thế hệ mới**.
2. Discover/instantiate/health-check provider V2 trước khi publish.
3. Cutover routing có synchronization/leases đúng để request mới dùng V2.
4. Drain công việc V1 đang chạy và ngừng callbacks.
5. Đóng provider-owned resources, clear caches, restore TCCL, remove ThreadLocal values.
6. Đóng old `URLClassLoader` và bỏ strong references; JVM có thể unload classes cũ khi đủ điều kiện.

`ServiceLoader.reload()` không phải hot-reload bytecode. Nếu plugin code là **untrusted**, classloader không tạo security sandbox; cần process/container/OS boundary, xác thực source/signatures và quản lý quyền thực sự.

## 15. Checklist nghiệm thu

- [ ] `Processor.java` tồn tại một bản ở `build/api`.
- [ ] Host không import concrete provider classes.
- [ ] Mỗi provider có public no-arg constructor và implements `demo.spi.Processor`.
- [ ] Hai JARs chứa implementation và `META-INF/services/demo.spi.Processor` đúng path.
- [ ] Không chứa bản duplicate `demo/spi/Processor.class` trong plugin JARs.
- [ ] `javac --release 17` biên dịch đủ bốn files.
- [ ] Host runtime classpath chỉ chứa `build/api` và `build/host`.
- [ ] `host:count=0` và mỗi plugin `count=1`.
- [ ] Output đúng `JAVA 17` và `71 avaj`.
- [ ] `upper:owned=true`, `reverse:owned=true` và `same api=true`.
- [ ] TCCL được restore trong `finally`, dòng `tccl first=upper` đúng.
- [ ] ServiceLoader không được cache VM-wide qua nhiều tenants/generations.
- [ ] Khi plugin retire, đã drain requests, clear caches và đóng resources đúng owner.
- [ ] Khi `ServiceConfigurationError`, đã kiểm tra original cause và SPI descriptor/JAR contents.

## 16. Câu hỏi tự kiểm tra

1. Shared API được nạp bởi loader nào? **Host/parent**, do `Processor.class.getClassLoader()` nhận diện.
2. Host có cần compile against `UpperProcessor` không? **Không**, chỉ cần `Processor`.
3. File nào dùng để khai báo provider trên classpath? **`META-INF/services/demo.spi.Processor`**.
4. Descriptor chứa Java source path hay provider binary name? **Binary name `demo.upper.UpperProcessor`**.
5. Plugin JAR chỉ có implementation class nhưng không có descriptor có được tự phát hiện không? **Không**, trừ cơ chế named-module provider riêng.
6. Vì sao loader của plugin nhận shared loader làm parent? **Để dùng cùng runtime `Processor.class`**.
7. `ServiceLoader.load(S.class, loader)` và một tham số khác nhau thế nào? **Explicit loader so với current thread context loader**.
8. Khi host đổi TCCL sau `ServiceLoader.load(S.class)`, ServiceLoader cũ tự đổi provider scope không? **Không**.
9. Provider class có thể do parent define khi explicit loader là child không? **Có**, tùy delegation/JPMS.
10. `ServiceLoader` tìm thấy providers ngay khi tạo đối tượng à? **Discovery/instantiation phần lớn lazy khi iterate/stream**.
11. Nếu descriptor sai class name, lỗi nào thường gặp? **ServiceConfigurationError**.
12. Nếu API bị duplicate trong child-first loader, điều gì xảy ra? **Runtime type identity mismatch, provider not subtype/cast failure**.
13. `URLClassLoader.close()` có unload classes ngay không? **Không**.
14. `reload()` có nạp class implementation V2 trong same defining loader không? **Không**.
15. `ServiceLoader.load(layer, S.class)` tìm classpath `META-INF/services` ở unnamed modules không? **Không**, dùng named-module declarations.
16. Có thể dùng hai JARs trong một loader không? **Có**, khi cùng binary namespace/dependencies tương thích.
17. Khi nào nên tạo loader riêng cho từng plugin? **Khi cần dependency isolation hoặc lifecycle/version ownership độc lập**.
18. Có nên dùng ClassLoader làm sandbox cho plugin chưa tin cậy không? **Không**, cần OS/process isolation khi có trust boundary.

## 17. Tài liệu chuẩn

1. [ServiceLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html): contract của provider, classpath `META-INF/services`, named modules, explicit loader/TCCL, lazy instantiation, `reload`, errors.
2. [URLClassLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/URLClassLoader.html): tìm classes/resources từ URLs, parent delegation, `close`.
3. [ClassLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html): defining/initiating loaders và resource lookup.
4. [ServiceConfigurationError — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceConfigurationError.html): lỗi khi SPI provider loading/creation không hợp lệ.
5. [Thread — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Thread.html): thread context ClassLoader.
6. [JVMS §5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3): runtime class identity.
7. [ModuleLayer — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ModuleLayer.html): provider modules theo layer.
8. [JEP 261 — Module System](https://openjdk.org/jeps/261): `uses` và `provides ... with ...` trong module descriptors.

**Kết luận:** Một plugin classpath theo Java SPI là **implementation class + đúng `META-INF/services` file**. Host giữ shared `Processor` interface, tạo ClassLoader trỏ tới provider JAR rồi gọi `ServiceLoader.load(Processor.class, pluginLoader)`. Đừng đánh đổi shared API type identity lấy child-first tùy tiện; khi triển khai production, quản lý provider discovery, TCCL, errors, caches và lifecycle của loader theo từng plugin generation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-exercises-006">Tạo module layer cho plugin mô đun hóa</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo ModuleLayer cho plugin mô đun hóa với Java 17+

## 1. Bài toán: nạp plugin dưới dạng named module khi host đang chạy

Trong Java Platform Module System (**JPMS**, Java 9+), một plugin không nhất thiết chỉ là một JAR đặt trên classpath rồi tìm implementation bằng `Class.forName`. Plugin có thể là **named module**, có file `module-info.java`, khai báo phụ thuộc `requires` và nhà cung cấp dịch vụ `provides ... with ...`.

Một ứng dụng chủ (**host**) có thể bắt đầu mà **không resolve plugin module**, sau đó:

1. Khám phá module artifact bằng `ModuleFinder`.
2. Resolve module graph mới dựa trên `Configuration` của parent layer.
3. Định nghĩa runtime modules và ánh xạ loader bằng `ModuleLayer.defineModulesWithManyLoaders` (hoặc `WithOneLoader`).
4. Tìm service providers trong **layer mới** bằng `ServiceLoader.load(layer, ServiceType.class)`.
5. Gọi plugin qua **shared API module** của host, không import implementation class.
6. Quản lý vòng đời layer, instances, class loaders, caches và các resources liên quan.

Bài thực hành này sẽ có ba phần:

~~~text
Boot ModuleLayer (ModuleLayer.boot())
  |
  +-- plugin.api named module
  |     exports plugin.api.Extension
  |
  +-- java.base và các system modules đã resolve
  |
  v
Child ModuleLayer (được tạo động)
  |
  +-- plugin.echo named module
        requires plugin.api
        provides plugin.api.Extension
          with plugin.echo.EchoExtension
~~~

**Điểm cốt lõi:** API `plugin.api.Extension` phải có **cùng runtime class identity** ở host và provider. Plugin implementation được định nghĩa ở **child layer**, còn shared API module đã tồn tại trong **boot parent layer**. Đây là cách giữ hợp đồng giao tiếp trong khi cho phép khám phá plugin mới lúc chạy.

### Kết quả cần đạt

Bạn sẽ xây được chương trình Java 17+ không cần Maven/Gradle, có một module API, một module provider và launcher host; tự biên dịch từng phần; tạo `Configuration` rồi `ModuleLayer`; dùng `ServiceLoader.load(layer, ...)`; xác minh defining loader, runtime module identity, parent visibility và việc tạo hai child layers độc lập.

Bài cũng giải thích khác nhau giữa module path với classpath, `ModuleFinder` với `Configuration`, parent `ModuleLayer` với parent `ClassLoader`, lựa chọn one/many/custom loaders, giới hạn visibility/exports/opens, sự khác nhau giữa `resolve` và `resolveAndBind` và các lỗi thường gặp trong hệ plugin thực tế.

## 2. Bốn đối tượng khác nhau của JPMS

### 2.1. Module artifact và module descriptor

Một modular JAR hoặc một directory module đã biên dịch chứa `module-info.class`. Tệp source descriptor:

~~~java
module plugin.echo {
    requires plugin.api;
    provides plugin.api.Extension
        with plugin.echo.EchoExtension;
}
~~~

Ở runtime, thông tin descriptor cho biết:

- **Module name**: `plugin.echo`.
- **Requires**: cần `plugin.api`.
- **Provides**: đăng ký provider `plugin.echo.EchoExtension` cho service `plugin.api.Extension`.
- Các directives khác có thể là `exports`, `opens`, `uses`.

Một artifact xuất hiện trên disk **chưa đồng nghĩa** module của nó đã được resolve hoặc có runtime `Module` object.

### 2.2. ModuleFinder: tìm artifacts/module references

~~~java
ModuleFinder finder =
        ModuleFinder.of(Path.of("build/plugins"));
~~~

`ModuleFinder` biết cách nhận diện module artifacts ở những roots được chỉ định; nó không tự define runtime classes hay khởi động provider.

Trong demo, `build/plugins` chứa exploded module directory `plugin.echo`. Có thể thay bằng modular JAR nếu đã đóng gói đúng và chỉ định path phù hợp.

### 2.3. Configuration: module graph đã resolve

~~~java
Configuration configuration =
        ModuleLayer.boot().configuration().resolve(
                finder,
                ModuleFinder.of(),
                Set.of("plugin.echo"));
~~~

Đây là graph của **các modules mới được resolve** dựa trên parent configuration. `resolve` xử lý `requires` và các ràng buộc resolution, không tự instantiate provider hay tạo ClassLoader.

Các tham số cần phân biệt:

- `finder` thứ nhất: module artifacts có thể dùng trong cuộc resolution hiện tại.
- `ModuleFinder.of()`: danh mục after finder trong API này, ở demo để trống.
- `Set.of("plugin.echo")`: **root module names** yêu cầu resolve.

API `plugin.api` đã ở parent boot configuration; resolver có thể thỏa `requires plugin.api` từ parent, nên **không cần tạo bản thứ hai của API trong child**.

### 2.4. ModuleLayer: định nghĩa runtime modules và loaders

~~~java
ModuleLayer layer = ModuleLayer.boot()
        .defineModulesWithManyLoaders(
                configuration,
                ClassLoader.getSystemClassLoader());
~~~

Đây là thời điểm tạo runtime `Module` objects và ánh xạ named modules mới vào loader(s). Method instance `defineModulesWithManyLoaders` trả `ModuleLayer`.

Trong thí nghiệm:

- Parent module layer: `ModuleLayer.boot()`.
- Parent `Configuration`: `boot.configuration()`.
- Child configuration chứa `plugin.echo`.
- Loader strategy: **mỗi module mới** có loader mới riêng.
- Tham số `ClassLoader.getSystemClassLoader()` là **parent ClassLoader cho loader mới**, **không** có nghĩa plugin được định nghĩa trực tiếp trong application/system loader.

**Tách bạch ba quan hệ:** JPMS module readability (có thể đọc module nào), parent layer relation (layer nào là cha), và ClassLoader delegation (loader nào nạp/ủy quyền những classes nào). Chúng liên quan nhưng **không đồng nhất**.

## 3. Thiết kế host, shared API và plugin module

### 3.1. Hợp đồng API dùng chung

API module tên `plugin.api`:

~~~java
module plugin.api {
    exports plugin.api;
}
~~~

Và interface:

~~~java
package plugin.api;

public interface Extension {
    String name();
    String execute(String input);
}
~~~

Lựa chọn này giữ public contract nhỏ. Host và implementation chỉ biết `Extension`, không cần chia sẻ implementation type hay thư viện riêng của plugin.

**Trách nhiệm của API module:** quản lý các service interfaces, DTOs và exceptions đi qua boundary. Với hệ nhiều plugin, không để provider định nghĩa thêm bản `plugin.api.Extension` riêng; nếu không runtime type identity có thể bị chia đôi và cast/service discovery thất bại.

### 3.2. Plugin implementation module

Descriptor:

~~~java
module plugin.echo {
    requires plugin.api;
    provides plugin.api.Extension with plugin.echo.EchoExtension;
}
~~~

Provider class:

~~~java
package plugin.echo;

import plugin.api.Extension;

public final class EchoExtension implements Extension {
    @Override
    public String name() {
        return "echo";
    }

    @Override
    public String execute(String input) {
        return "PLUGIN:" + input;
    }
}
~~~

**Không cần `exports plugin.echo;`** chỉ để `ServiceLoader` tạo provider đã khai báo qua `provides ... with ...`. Cơ chế provider factory được JPMS/`ServiceLoader` xử lý theo hợp đồng. Host không cần truy cập trực tiếp type implementation bằng Java source import.

Trong demo, provider có public no-arg constructor mặc định. Trong hệ thực tế, provider requirements, constructor và static `provider()` factory behavior còn tùy named module/service-provider contract, cần kiểm tra Javadoc tương ứng.

### 3.3. Host biết API và child ModuleLayer, không biết EchoExtension

Host sẽ viết:

~~~java
for (Extension extension :
        ServiceLoader.load(layer, Extension.class)) {
    System.out.println(extension.execute("hello"));
}
~~~

Không có:

~~~java
import plugin.echo.EchoExtension;
~~~

Điều đó cho phép plugin module **không xuất hiện lúc compile host** hoặc không có trong boot layer. Vòng đời plugin có thể được xử lý qua interface API và metadata/service registry.

### 3.4. Vì sao API nên nằm ở parent layer?

Nếu host dùng `plugin.api.Extension` thuộc boot layer nhưng plugin child tự resolve thêm một `plugin.api` khác cùng tên dưới một layer khác, có nguy cơ host và plugin không dùng cùng `Class<?>`.

Thiết kế khuyến nghị trong demo:

~~~text
Boot layer:
  plugin.api.Extension => Class<?> chung

Child layer:
  plugin.echo.EchoExtension
       implements Extension từ parent
~~~

Điều kiện quan trọng là **module resolution graph và module-classloader mapping** thực sự dẫn provider đến chính `Extension.class` của host; không đủ khi chỉ kiểm tra hai bytecode JAR “trông giống nhau”.

## 4. Cấu trúc dự án demo

~~~text
demo/
├── src/
│   ├── plugin.api/
│   │   ├── module-info.java
│   │   └── plugin/api/Extension.java
│   ├── plugin.echo/
│   │   ├── module-info.java
│   │   └── plugin/echo/EchoExtension.java
│   └── host/
│       └── HostMain.java
└── build/
    ├── mods/
    │   └── plugin.api/          # module API đã compile
    ├── plugins/
    │   └── plugin.echo/         # module provider đã compile
    └── host/                    # launcher trên classpath
~~~

**Tách hai directory có chủ đích:**

- `build/mods` là **module path khi chạy host** để `plugin.api` được resolve vào boot layer.
- `build/plugins` **không nằm trên `--module-path` của launcher**; host chỉ truyền path này cho `ModuleFinder.of` **khi đang chạy**.
- `build/host` là classpath chứa `HostMain.class` trong **unnamed module**.

Nhờ đó, bài kiểm thử `echoNotInBoot=true` có ý nghĩa thật sự: provider không bị launcher resolve trước khi code host tạo child layer.

Nếu để `plugin.echo` vào boot module path **và thêm nó làm root module** bằng `--add-modules`, demo không còn đo đúng quá trình dynamic layer creation.

## 5. File nguồn của shared API module

### 5.1. `src/plugin.api/module-info.java`

~~~java
module plugin.api {
    exports plugin.api;
}
~~~

`exports plugin.api;` cho phép code từ module khác (kể cả unnamed host module) dùng public types của package API khi có readability phù hợp.

### 5.2. `src/plugin.api/plugin/api/Extension.java`

~~~java
package plugin.api;

public interface Extension {
    String name();
    String execute(String input);
}
~~~

Trong chương trình, `Extension.class` được host truyền vào `ServiceLoader.load(layer, Extension.class)`. Nó là một type cụ thể được JVM định nghĩa, không phải một chuỗi service name.

## 6. File nguồn của plugin provider module

### 6.1. `src/plugin.echo/module-info.java`

~~~java
module plugin.echo {
    requires plugin.api;
    provides plugin.api.Extension with plugin.echo.EchoExtension;
}
~~~

Không cần `uses plugin.api.Extension;` trong chính plugin provider module để khai báo provider; `uses` là directive cho **module tiêu thụ service**.

Host demo là unnamed module. Nếu host được mô đun hóa thành named module `plugin.host`, descriptor nên có:

~~~java
module plugin.host {
    requires plugin.api;
    uses plugin.api.Extension;
}
~~~

Đây là **descriptor minh họa cho kiến trúc named host**, **không dùng trong source demo runnable** ở mục 8. Khi host là named module tiêu thụ service, `uses` là điểm cần khai báo để `ServiceLoader` hợp lệ. Mục đích demo dùng unnamed host là đơn giản hóa biên dịch và nhấn mạnh dynamic layer; nó vẫn có quyền gọi service API đã được resolve trong boot.

### 6.2. `src/plugin.echo/plugin/echo/EchoExtension.java`

~~~java
package plugin.echo;

import plugin.api.Extension;

public final class EchoExtension implements Extension {
    @Override
    public String name() {
        return "echo";
    }

    @Override
    public String execute(String input) {
        return "PLUGIN:" + input;
    }
}
~~~

Plugin này chỉ nhận và trả `String` thuộc Java platform để output không phụ thuộc serialization hay versioning của DTO. Production có thể thay bằng `Request`/`Response` thuộc API module, nhưng phải giữ API version/identity nhất quán.

## 7. Tạo child ModuleLayer đúng thứ tự

### 7.1. Xác nhận API đã được resolve trong boot

~~~java
ModuleLayer boot = ModuleLayer.boot();
boolean apiInBoot = boot.findModule("plugin.api").isPresent();
~~~

Nếu `plugin.api` không ở boot, `requires plugin.api` trong provider có thể không được resolver đáp ứng từ parent như ta dự định. Demo chủ động chạy với `--add-modules plugin.api` để tạo root cho API boot layer.

### 7.2. Xác nhận plugin chưa ở boot

~~~java
boolean pluginNotYetLoaded =
        boot.findModule("plugin.echo").isEmpty();
~~~

Đây là kiểm tra **module đã được định nghĩa trong layer**; nó không khẳng định artifact không tồn tại trên disk. Plugin có sẵn ở `build/plugins` nhưng chưa resolve trong boot configuration.

### 7.3. ModuleFinder đọc plugin artifact

~~~java
ModuleFinder finder =
        ModuleFinder.of(Path.of("build/plugins"));
~~~

Với cấu trúc thư mục đã chuẩn, finder nhận `plugin.echo`. Nếu `module-info.class` thiếu hoặc directory/JAR module không hợp lệ, bước discovery/resolution có thể thất bại. Phải dùng **module root**, không truyền nhầm source tree hoặc output directory chứa nhiều files vô tổ chức.

### 7.4. Resolve roots, giữ API ở parent

~~~java
Configuration configuration = boot.configuration()
        .resolve(finder, ModuleFinder.of(), Set.of("plugin.echo"));
~~~

Ý nghĩa:

~~~text
Parent Configuration:
  plugin.api (đã resolved)
     ^
     | requires
New Configuration:
  plugin.echo
~~~

`resolve` không tạo runtime classes; nó chỉ tạo graph có các module references và readability dependencies hợp lệ. Vì root set chứa `plugin.echo`, provider module được resolve **dù host không có compile-time dependency đến provider**.

`resolveAndBind` là một phương thức khác: ngoài roots/requires, nó còn có service binding resolution giúp kéo thêm provider modules phù hợp theo module graph. Trong demo, ta **đã đặt `plugin.echo` làm root tường minh**, nên không cần service binding để chọn plugin. Nếu muốn khám phá providers tự động theo `uses/provides`, phải thiết kế giải thuật `resolveAndBind` và module finder scope/parent config rõ.

### 7.5. Định nghĩa runtime layer và chọn ClassLoader policy

~~~java
ModuleLayer layer = boot.defineModulesWithManyLoaders(
        configuration,
        ClassLoader.getSystemClassLoader());
~~~

Dạng instance này trả `ModuleLayer`. `WithManyLoaders` tạo một loader mới cho **mỗi named module được định nghĩa trong configuration mới**, không yêu cầu mỗi module của **parent boot layer** cũng được tạo lại.

Sau đó:

~~~java
Module providerModule =
        layer.findModule("plugin.echo").orElseThrow();

ClassLoader providerLoader =
        layer.findLoader("plugin.echo");
~~~

`layer.findModule` có thể tìm ở current layer hoặc parents, còn `layer.modules()` chỉ là modules định nghĩa trực tiếp ở current layer. `layer.findLoader` tra loader của module tương ứng trong current/parent layers; nó có thể trả `null` cho bootstrap loader, không phải chỉ cho module thiếu.

### 7.6. Phân biệt module loader và parent loader

Trong demo:

~~~java
ClassLoader appLoader = ClassLoader.getSystemClassLoader();
ClassLoader pluginLoader = layer.findLoader("plugin.echo");
~~~

- `appLoader` là **parent loader parameter** được truyền khi tạo child layer.
- `pluginLoader` là **defining loader** do layer creation thiết lập cho plugin module.
- `Extension.class.getClassLoader()` thuộc loader định nghĩa shared API module ở boot layer.
- Plugin implementation class sẽ có loader `pluginLoader`, nhưng interface API có class identity từ shared parent.

Không khẳng định chỉ từ việc có một parent loader rằng plugin class đã được **định nghĩa bởi parent**. Đó là sai khác giữa **ủy quyền** và **định nghĩa**.

## 8. Chương trình Java 17+ đầy đủ, có thể chạy ngay

Lưu file `src/host/HostMain.java`:

~~~java
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.Set;
import plugin.api.Extension;

public final class HostMain {
    public static void main(String[] args) {
        ModuleLayer boot = ModuleLayer.boot();

        System.out.println("apiInBoot="
                + boot.findModule("plugin.api").isPresent());
        System.out.println("echoNotInBoot="
                + boot.findModule("plugin.echo").isEmpty());

        ModuleFinder finder = ModuleFinder.of(
                Path.of("build/plugins"));
        Configuration configuration = boot.configuration()
                .resolve(finder, ModuleFinder.of(),
                         Set.of("plugin.echo"));

        ModuleLayer layer = boot.defineModulesWithManyLoaders(
                configuration, ClassLoader.getSystemClassLoader());
        Module pluginModule =
                layer.findModule("plugin.echo").orElseThrow();
        ClassLoader pluginLoader = layer.findLoader("plugin.echo");

        System.out.println("pluginResolved="
                + configuration.findModule("plugin.echo").isPresent());
        System.out.println("newLayerModule="
                + layer.modules().contains(pluginModule));
        System.out.println("parentApiVisible="
                + layer.findModule("plugin.api").isPresent());
        System.out.println("loaderFindsParentApi="
                + (layer.findLoader("plugin.api")
                    == boot.findLoader("plugin.api")));

        int count = 0;
        for (Extension ext :
                ServiceLoader.load(layer, Extension.class)) {
            count++;
            System.out.println("providerName=" + ext.name());
            System.out.println("providerResult=" + ext.execute("hello"));
            System.out.println("providerInChildModule="
                    + (ext.getClass().getModule() == pluginModule));
            System.out.println("providerLoaderMatches="
                    + (ext.getClass().getClassLoader() == pluginLoader));
            System.out.println("sharedApiIdentity="
                    + Extension.class.isAssignableFrom(ext.getClass()));
        }
        System.out.println("providerCount=" + count);

        ModuleLayer second = boot.defineModulesWithManyLoaders(
                configuration, ClassLoader.getSystemClassLoader());
        System.out.println("separateLayerSeparateModule="
                + (second.findModule("plugin.echo").orElseThrow()
                    != pluginModule));
        System.out.println("separateLayerSeparateLoader="
                + (second.findLoader("plugin.echo") != pluginLoader));
    }
}
~~~

Các thao tác:

- `ModuleFinder.of(...)` tạo finder cho plugin directory.
- `Configuration.resolve(...)` tạo graph mới có provider root.
- `defineModulesWithManyLoaders(...)` tạo child ModuleLayer và loader của provider.
- `ServiceLoader.load(layer, Extension.class)` yêu cầu discovery trong layer đó.
- `provider.getClass().getModule()` đối chiếu **Module object identity**, không chỉ tên String.
- `provider.getClass().getClassLoader()` đối chiếu đúng defining loader do layer gán.
- `Extension.class.isAssignableFrom(provider.getClass())` chứng minh provider dùng được interface runtime của host.
- Tạo thêm child layer `second` từ cùng configuration cho phép so **hai runtime Module objects** và loaders riêng, không đổi layer thứ nhất.

**Chú ý:** ví dụ chỉ có một provider, nên vòng lặp in đúng một nhóm dòng. Một hệ plugin thật có thể có nhiều providers; thứ tự discovery không nên được coi là priority trừ khi ứng dụng tự quy định chính sách chọn provider.

## 9. Lệnh biên dịch và thực thi

### 9.1. Linux/macOS

Từ thư mục `demo`, tạo output directories rồi dùng JDK 17+:

~~~sh
mkdir -p build/mods/plugin.api build/plugins/plugin.echo build/host

javac --release 17 \
  -d build/mods/plugin.api \
  src/plugin.api/module-info.java \
  src/plugin.api/plugin/api/Extension.java

javac --release 17 \
  --module-path build/mods \
  -d build/plugins/plugin.echo \
  src/plugin.echo/module-info.java \
  src/plugin.echo/plugin/echo/EchoExtension.java

javac --release 17 \
  --module-path build/mods \
  --add-modules plugin.api \
  -d build/host \
  src/host/HostMain.java

java \
  --module-path build/mods \
  --add-modules plugin.api \
  -cp build/host \
  HostMain
~~~

**Không** đưa `build/plugins` vào `--module-path` lúc launch. Nếu dùng khác biệt directory root, sửa đúng `ModuleFinder.of(...)` trong host code trước khi chạy.

### 9.2. Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force build/mods/plugin.api | Out-Null
New-Item -ItemType Directory -Force build/plugins/plugin.echo | Out-Null
New-Item -ItemType Directory -Force build/host | Out-Null

javac --release 17 -d build/mods/plugin.api src/plugin.api/module-info.java src/plugin.api/plugin/api/Extension.java

javac --release 17 --module-path build/mods -d build/plugins/plugin.echo src/plugin.echo/module-info.java src/plugin.echo/plugin/echo/EchoExtension.java

javac --release 17 --module-path build/mods --add-modules plugin.api -d build/host src/host/HostMain.java

java --module-path build/mods --add-modules plugin.api -cp build/host HostMain
~~~

Đây là các lệnh một dòng tương thích Windows PowerShell. Thư mục chứa sources phải được tạo trước, đúng đường đã nêu ở mục 4. Không cần Maven, Gradle, external libraries hoặc platform-specific Java code.

### 9.3. Vì sao phải có `--add-modules plugin.api`?

Host chạy trên **classpath/unnamed module** nhưng muốn gọi `Extension.class` của named module API. Đặt `plugin.api` trên module path **chưa đủ nếu module chưa được resolve làm root**. `--add-modules plugin.api` buộc JVM resolve nó vào boot layer khi launch.

Khi compile host, cũng cần `--module-path build/mods --add-modules plugin.api` để `javac` nhìn thấy exported type trong named module API. Không đưa implementation JAR/directory vào host compile dependencies.

### 9.4. Stdout chính xác đã kiểm chứng

~~~text
apiInBoot=true
echoNotInBoot=true
pluginResolved=true
newLayerModule=true
parentApiVisible=true
loaderFindsParentApi=true
providerName=echo
providerResult=PLUGIN:hello
providerInChildModule=true
providerLoaderMatches=true
sharedApiIdentity=true
providerCount=1
separateLayerSeparateModule=true
separateLayerSeparateLoader=true
~~~

Ví dụ đã được kiểm chứng bằng cách compile ba source groups với `javac --release 17` và chạy `java` trên JDK có hỗ trợ Java 17. Không phụ thuộc hash của ClassLoader, đường tuyệt đối hoặc thứ tự filesystem.

### 9.5. Giải thích 14 dòng

| Dòng | Ý nghĩa |
|---|---|
| `apiInBoot=true` | Shared API được resolve vào boot layer |
| `echoNotInBoot=true` | Implementation module chưa được define trong boot layer |
| `pluginResolved=true` | Child configuration đã resolve provider module root |
| `newLayerModule=true` | Plugin Module object được định nghĩa trực tiếp trong child layer |
| `parentApiVisible=true` | Child layer thấy shared API từ parent layer |
| `loaderFindsParentApi=true` | `findLoader` của child có thể tra module ở parent layers |
| `providerName=echo` | `ServiceLoader` đã tạo instance plugin |
| `providerResult=PLUGIN:hello` | Plugin xử lý input qua shared API contract |
| `providerInChildModule=true` | Implementation class thuộc đúng runtime Module object của child layer |
| `providerLoaderMatches=true` | Implementation class do loader mà `layer.findLoader` trả về định nghĩa |
| `sharedApiIdentity=true` | Provider implement chính runtime service API type của host |
| `providerCount=1` | Chính xác một provider được tìm trong cấu hình demo |
| `separateLayerSeparateModule=true` | Hai sibling child layers có runtime Module objects khác nhau |
| `separateLayerSeparateLoader=true` | Với `WithManyLoaders`, hai child layers có plugin loaders riêng |

**Đọc chính xác:** `ModuleLayer.findLoader(name)` **có thể tìm ở parent layers**. Không được khẳng định nó chỉ tra các modules của child layer. Với bootstrap-defined module như `java.base`, kết quả loader có thể là `null` mà module vẫn hợp lệ.

## 10. Một loader, nhiều loaders và mapping tùy chỉnh

### 10.1. `defineModulesWithOneLoader`

~~~java
ModuleLayer child = boot.defineModulesWithOneLoader(
        configuration,
        ClassLoader.getSystemClassLoader());
~~~

Tất cả **modules được định nghĩa trong configuration mới** dùng **một ClassLoader mới**. Nếu plugin gồm nhiều named modules có thể chia sẻ một loader, đây là lựa chọn đơn giản. Tuy nhiên có thể gặp package overlap/loader constraints khiến tạo layer thất bại.

**Đừng hiểu** mọi module trong **boot parent layer** cũng bị chuyển vào loader mới; chỉ modules trong configuration mới được định nghĩa tại child layer.

### 10.2. `defineModulesWithManyLoaders`

~~~java
ModuleLayer child = boot.defineModulesWithManyLoaders(
        configuration,
        ClassLoader.getSystemClassLoader());
~~~

Mỗi module mới được định nghĩa bằng loader mới riêng. Khi có nhiều module implementation, điều này có thể giảm một số xung đột package names. Nhưng vẫn cần module readability/exports/opens và sự nhất quán type API.

Một provider module có thể tham chiếu types do parent API module định nghĩa; không cần tạo thêm bản API trong child.

### 10.3. `defineModules` với mapping function

Nếu framework cần chọn custom loader cho từng module:

~~~java
ModuleLayer child = boot.defineModules(
        configuration,
        moduleName -> loaderRegistry.get(moduleName));
~~~

Đây là **snippet kiến trúc**, chưa runnable độc lập; cần chuẩn bị `loaderRegistry` và đáp ứng đầy đủ module/package/delegation constraints của API. Nó không tự biến bất kỳ ClassLoader nào thành JPMS-aware loader hoàn chỉnh. Custom loaders phải triển khai đúng class/resource loading và tránh deadlocks/delegation cycles.

### 10.4. Instance và static overloads

Dùng `boot.defineModulesWithManyLoaders(cf, parentLoader)` trả `ModuleLayer` trực tiếp. Còn nhóm **static** methods:

~~~java
ModuleLayer.Controller controller =
        ModuleLayer.defineModulesWithManyLoaders(
                configuration,
                List.of(boot),
                ClassLoader.getSystemClassLoader());
ModuleLayer child = controller.layer();
~~~

Đây là snippet cần `java.util.List`, `configuration` và `boot`. Static variant cho phép danh sách parent layers và trả `Controller`, có thể dùng một số thao tác điều chỉnh readability/exports/opens qua controller theo hợp đồng. Đừng nhầm return type với instance method.

### 10.5. Chọn chiến lược theo bài toán

| Chiến lược | Mapping modules mới | Dùng khi | Chú ý |
|---|---|---|---|
| `WithOneLoader` | Nhiều modules chung một loader mới | Các modules phối hợp chặt, không xung đột package | Không giả định luôn hợp lệ với split packages |
| `WithManyLoaders` | Một loader mới/module mới | Muốn namespace tách theo module | Shared API vẫn cần thống nhất |
| `defineModules(cf, fn)` | Custom mapping | Container/plugin engine có loader policy riêng | Implementation và debugging phức tạp |
| Static `ModuleLayer.defineModules...` | Kèm `Controller` | Nhiều parents hoặc cần controller APIs | Return type khác instance method |

## 11. `resolve` và `resolveAndBind` trong kiến trúc plugin

### 11.1. `resolve` theo root + requires

Trong demo:

~~~java
Configuration configuration = boot.configuration().resolve(
        finder, ModuleFinder.of(), Set.of("plugin.echo"));
~~~

Ta **biết đích danh tên plugin module** và resolve nó thành root. `requires plugin.api` của plugin được đáp ứng từ boot parent config.

### 11.2. `resolveAndBind` khi dùng service binding

Một mô hình khác là host module khai báo `uses plugin.api.Extension;` và muốn resolver tìm các provider modules từ finder. Khi đó `resolveAndBind` có thể thêm provider modules vào graph dựa trên `provides` theo service binding rules.

Tuy nhiên:

- Không phải mọi JAR chứa provider đều được chọn trong mọi resolution.
- Root graph/module finder scope và module descriptors ảnh hưởng binding.
- Service binding có thể kéo thêm module dependencies hoặc providers so với roots ban đầu.
- `ServiceLoader.load(layer, service)` vẫn cần một **layer đã định nghĩa các provider modules**. Nó không tự giải quyết graph hay tải bất kỳ JAR trên disk ngoài layer chỉ nhờ service name.

Cần phân biệt **discovery của provider module lúc resolution** và **discovery/instantiation provider object lúc ServiceLoader iteration**.

### 11.3. ModuleFinder trước, ServiceLoader sau

~~~text
Plugin artifacts
   |
   | ModuleFinder
   v
Resolved Configuration
   |
   | ModuleLayer.defineModules...
   v
Runtime ModuleLayer + loaders
   |
   | ServiceLoader.load(layer, Service.class)
   v
Provider instances
~~~

Nếu chưa tạo layer, `ServiceLoader.load(layer, ...)` không có nơi để tìm provider runtime modules mới. Nếu plugin chưa được resolve, thay đổi TCCL cũng không làm `ModuleLayer` tự xuất hiện.

## 12. Chẩn đoán: module có trong folder nhưng ServiceLoader không thấy provider

### 12.1. Kiểm tra packaging/module descriptors

Một plugin modular JAR phải có hợp đồng phù hợp:

~~~text
plugin.echo.jar
  module-info.class
  plugin/echo/EchoExtension.class
~~~

Descriptor khai báo `provides plugin.api.Extension with plugin.echo.EchoExtension;`. Nếu quên `provides`, `ServiceLoader.load(layer, Extension.class)` không có provider declaration của named module để tìm, dù class implementation tồn tại.

Với **explicit named module**, không dựa vào mỗi `META-INF/services/plugin.api.Extension` như ở mô hình classpath. Java SPI cho classpath có provider configuration file riêng; với JPMS explicit module, descriptor `provides` mới là cách chuẩn công bố providers.

### 12.2. Kiểm tra runtime paths

Các path khác vai trò:

~~~text
--module-path build/mods
    => plugin.api ở boot configuration

ModuleFinder.of("build/plugins")
    => plugin.echo được phát hiện khi host chạy

-cp build/host
    => HostMain ở unnamed module
~~~

Nếu vô tình thêm `plugin.echo` vào boot roots, `echoNotInBoot` không còn true; nếu để `plugin.api` không được resolve trong boot, provider dependency resolution có thể thất bại.

Đừng đánh đồng compile-time module path, launcher module path, dynamic ModuleFinder roots và classpath.

### 12.3. Các nguyên nhân phổ biến

| Triệu chứng | Nguyên nhân cần kiểm tra | Hướng xử lý |
|---|---|---|
| `FindException` lúc finder/resolve | Module artifact không hợp lệ, missing module hoặc duplicate names | Kiểm tra path/module-info.class, roots |
| `ResolutionException` | Xung đột module graph, packages, readability constraints | Đọc exception; sửa descriptors/dependencies |
| `LayerInstantiationException` | Không thể tạo loader/module mapping theo policy | Kiểm tra package overlap và loader constraints |
| `NoClassDefFoundError` khi provider được dùng | Runtime dependency không nằm trong graph/loader thích hợp | Kiểm tra `requires`/modules/launcher path |
| `ServiceConfigurationError` | Provider declaration/constructor/module/class identity không phù hợp | Kiểm tra `provides ... with` và shared API type |
| Không có provider dù layer tạo thành công | Quên `provides` hoặc plugin module không resolved | Kiểm tra `configuration.modules()`, `layer.modules()` |
| Provider class không tìm được bằng host loader | Implementation thuộc child module/loader | Dùng `ServiceLoader.load(layer,service)`; không `Class.forName` host |
| `ClassCastException` khi ép kiểu provider | Host/plugin dùng khác runtime API type | API từ parent, kiểm tra `Extension.class` identity |
| Module ở file nhưng `findModule` trống | ModuleFinder chưa resolve module làm root/dependency | Sửa root set/module finder |
| Plugin thêm rồi host không thấy | Layer hiện có là immutable về modules | Tạo child layer mới hoặc plugin manager generations |
| TCCL đổi mà `ServiceLoader.load(layer,...)` không thay | Overload theo layer dùng explicit layer | Chọn đúng overload/kiểm tra thực sự dùng layer nào |
| Runtime JAR version khác source | Đóng gói/deploy sai artifact | So module descriptor, bytecode và source version |
| Module đồng tên ở sibling layers | Runtime module identity khác nhau dù name giống | Giữ đúng layer reference và API bridge |

### 12.4. Phân biệt ClassLoader lỗi với JPMS access lỗi

Một module đã resolve và class load thành công vẫn có thể gặp:

- `IllegalAccessError` nếu Java access/module exports không phù hợp.
- `InaccessibleObjectException` khi deep reflection vào package chưa `opens`.
- `NoSuchMethodException` hoặc provider constructor/factory errors.
- `ClassCastException` nếu service API/runtime types chia đôi.
- `ServiceConfigurationError` khi provider không hợp lệ.

Đừng chỉ đổi `ClassLoader.getParent()` hoặc thêm `--add-opens` bừa bãi. Hãy phân loại lỗi theo bước **resolution**, **definition**, **loading**, **access** hay **instantiation**.

## 13. Module layer không phải plugin hot reload tự động

### 13.1. ModuleLayer không cho “append module” vào cùng instance

Khi tạo `ModuleLayer` xong, không có thao tác `layer.addModule(newJar)` để định nghĩa thêm module tùy ý vào chính layer cũ. Muốn triển khai plugin mới hoặc version mới, thường cần **Configuration/layer mới**, chọn parent(s)/module graph theo thiết kế.

Hai sibling layers có thể có named module cùng tên và implementation binary class names giống nhau nhưng là **những runtime Module/Class objects khác nhau**, như hai dòng cuối của demo.

### 13.2. Lifecycle references khiến loader không được GC

Một plugin manager có thể giữ:

- `ServiceLoader` và provider instances.
- `ModuleLayer`, `Module`, `ClassLoader`, `Class<?>`.
- `MethodHandle`/reflection caches.
- Threads, thread context class loaders, executors và thread locals.
- Listeners, callbacks, scheduled tasks hoặc static maps.

Tạo layer mới không đồng nghĩa layer cũ tự unload. Khi thay plugin, cần stop code cũ, dọn references và tài nguyên trước khi garbage collector có thể thu hồi classes/loaders khi đủ điều kiện.

### 13.3. API compatibility trong nhiều generations

Shared API module ở boot layer giúp host và providers dùng cùng `Extension.class`, nhưng không đảm bảo mọi version plugin tương thích:

- Thay method descriptor có thể phá binary compatibility.
- Plugin mới cần API revision khác, host chưa có sẽ gây lỗi resolution/linkage.
- Shared DTO/type xuất hiện ở method signatures cũng phải có identity nhất quán.
- Một child module có thể chứa private dependencies của riêng nó, nhưng nếu type private tràn qua shared API boundary thì ClassCastException/linkage problems có thể xuất hiện.

Production nên có version metadata, semantic compatibility policy và health/startup validation trước khi đưa plugin vào phục vụ requests.

### 13.4. Parent layer khác parent ClassLoader

Nhắc lại theo logic demo:

~~~text
ModuleLayer parent:
    Boot Layer
         |
         v
    Dynamic Plugin Layer

ClassLoader relationships (không vẽ thành 1:1):
    Application/system loader (tham số parentLoader)
         |
         v
    Loader mới cho plugin.echo
~~~

Boot layer có thể chứa modules do bootstrap, platform, application loaders định nghĩa. **Không có quy tắc “một layer = một loader”**; API `WithOneLoader` và `WithManyLoaders` chủ động chọn mapping khác nhau.

### 13.5. Ranh giới bảo mật

`ModuleLayer` giúp kiểm soát module resolution/readability/exports/opens, nhưng **không phải OS sandbox** để chạy plugin không tin cậy. Một provider trong cùng JVM vẫn có thể dùng public Java APIs, tiêu thụ CPU/RAM và truy cập những tài nguyên mà process được OS cho phép.

Với plugin untrusted cần process/container/VM isolation, least-privilege credentials, resource limits, network policy và IPC authorization. Trên JDK 24+, `SecurityManager` đã bị vô hiệu hóa theo JEP 486; không thiết kế sandbox mới dựa vào policy SecurityManager legacy.

## 14. Các sai lầm thường gặp cần tránh

1. **“Có module JAR trong folder là runtime đã có module.”** Sai: finder/artifact khác resolve/define.
2. **“`ModuleFinder` tạo ClassLoader.”** Sai: module finder chỉ cung cấp references.
3. **“`Configuration.resolve` đã instantiate provider.”** Sai: mới resolve graph.
4. **“Host phải import provider implementation.”** Sai: shared API + service loader được thiết kế để không cần.
5. **“Cứ đặt modular JAR trên `--module-path` là chắc chắn được resolve.”** Sai: cần roots/depends/binding.
6. **“Plugin và shared API nên cùng có một bản API module riêng.”** Sai cho demo: làm mất shared type identity.
7. **“Không `exports plugin.echo` thì `ServiceLoader` không dùng được provider.”** Sai trong mô hình `provides` explicit module hợp lệ.
8. **“`ServiceLoader.load(layer, Service.class)` dùng TCCL như overload một tham số.”** Sai: overload này dùng layer được truyền.
9. **“`ServiceLoader` tự tìm JAR chưa được resolve và define layer.”** Sai: phải có runtime modules trong layer.
10. **“Parent ModuleLayer là parent ClassLoader.”** Sai: hai quan hệ riêng.
11. **“`WithManyLoaders` tạo mỗi module ở boot layer một loader mới.”** Sai: chỉ modules configuration mới.
12. **“`WithOneLoader` chắc chắn hợp lệ cho mọi module graph.”** Sai: có thể gặp split-package/loader constraints.
13. **“`layer.modules()` liệt kê cả ancestor modules.”** Sai: chỉ modules trực tiếp trong layer.
14. **“`findLoader` của child không tìm được parent module.”** Sai: có thể tìm parent layers.
15. **“`findLoader` trả null luôn có nghĩa module thiếu.”** Sai: bootstrap loader là null; thiếu module gây IllegalArgumentException.
16. **“Hai layers cùng module name chứa cùng Module object.”** Sai: sibling layers có thể có identities khác.
17. **“Tạo layer mới là unload layer cũ.”** Sai: references/threads có thể giữ layer cũ.
18. **“Một loader riêng bảo đảm plugin không đọc host files.”** Sai: ClassLoader không phải sandbox.
19. **“`ServiceLoader` không thấy provider chỉ vì thiếu `META-INF/services`.”** Với explicit named module, kiểm tra `provides` trong `module-info.java`.
20. **“Có thể đổi module/loader mapping của layer đã tồn tại.”** Sai: cần tạo cấu hình và layer phù hợp mới.

## 15. Bài tập và câu hỏi tự kiểm tra

1. `ModuleFinder`, `Configuration`, `ModuleLayer` và `ServiceLoader` lần lượt chịu trách nhiệm gì?
2. Vì sao không cho `plugin.echo` vào launcher `--module-path` của demo?
3. `--add-modules plugin.api` làm gì với boot layer?
4. Vì sao `Extension.class` của host và plugin cần cùng runtime identity?
5. `module-info.java` của plugin phải khai báo `requires` và `provides` như thế nào?
6. Tại sao `plugin.echo` không bắt buộc `exports plugin.echo;` để làm service provider?
7. Host unnamed module khác gì một named module host về `uses`?
8. Vì sao `Configuration.resolve` không tự chạy static initializer của provider?
9. `resolveAndBind` bổ sung bước gì so với `resolve`?
10. Điều gì xảy ra nếu module folder không chứa `module-info.class` hợp lệ?
11. `defineModulesWithOneLoader` và `defineModulesWithManyLoaders` chọn loaders như thế nào?
12. Tham số `parentLoader` có cùng ý nghĩa với parent `ModuleLayer` không?
13. Vì sao `layer.findLoader("plugin.api")` trong demo vẫn trả được loader từ boot layer?
14. `layer.modules()` và `layer.findModule("plugin.api")` có phạm vi tìm giống nhau không?
15. Vì sao trong demo `plugin.echo` là named module ở child layer dù host chạy trên classpath?
16. Overload nào của `ServiceLoader` dùng layer explicit? Nó khác overload dựa TCCL ra sao?
17. `provider.getClass().getModule() == pluginModule` kiểm chứng điều gì?
18. `Extension.class.isAssignableFrom(provider.getClass())` giúp kiểm tra điều gì?
19. Tại sao cùng một `Configuration` có thể tạo hai child `ModuleLayer` runtime độc lập?
20. Nếu thêm plugin JAR vào filesystem sau khi layer đã tạo, làm sao đưa plugin mới vào runtime?
21. Khi nào nên dùng static `ModuleLayer.defineModules...` và `Controller`?
22. Nếu provider runtime thấy `NoClassDefFoundError`, cần kiểm tra graph/loader phần nào?
23. Nếu provider dùng private dependency versions khác host, làm sao tránh API type leak?
24. Vì sao cần stop plugin threads, dọn TCCL/caches và provider references trước khi unload?
25. Nếu plugin đến từ nguồn không tin cậy, ModuleLayer có đủ làm sandbox không?

**Tiêu chí hoàn thành:** tạo đúng cây source, compile API/provider/host, chạy đủ **14 dòng stdout** như tài liệu; giải thích vì sao plugin được tạo trong child layer còn API ở parent; dùng `ServiceLoader.load(layer, Extension.class)` thành công; phân biệt finder/resolve/define/service discovery và lỗi thường gặp.

## 16. Tài liệu chính thức

- [Java SE 21 — ModuleLayer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ModuleLayer.html): tạo layers, `findModule`, `findLoader`, parents, controller.
- [Java SE 21 — Configuration](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/module/Configuration.html): `resolve`, `resolveAndBind`, resolution graph.
- [Java SE 21 — ModuleFinder](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/module/ModuleFinder.html): cách tìm modular JAR/exploded modules.
- [Java SE 21 — ServiceLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html): service provider modules, `load(ModuleLayer, Class)`, declaration.
- [Java SE 21 — Module](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Module.html): runtime module identity, loader và layer.
- [Java SE 21 — Java launcher](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html): `--module-path`, `--add-modules`, `-cp`.
- [Java Language Specification §7.7 — Module Declarations](https://docs.oracle.com/javase/specs/jls/se21/html/jls-7.html#jls-7.7): module descriptors, `requires`/`exports`/`uses`/`provides`.
- [JEP 261 — Module System](https://openjdk.org/jeps/261): kiến trúc JPMS.
- [JEP 486 — Permanently Disable the Security Manager](https://openjdk.org/jeps/486): giới hạn security architecture mới đối với untrusted code.

**Kết luận:** tạo ModuleLayer động là một quy trình tường minh: **ModuleFinder tìm plugin artifact → Configuration resolve module graph → ModuleLayer định nghĩa runtime named modules và loaders → ServiceLoader dùng layer để tạo provider instances**. Khi giữ shared API trong parent layer, host có thể gọi plugin mô đun hóa mà không compile phụ thuộc implementation, đồng thời giữ đúng module/classloader identity và chủ động quản lý vòng đời plugin.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-exercises-007">Tái hiện và sửa lỗi cùng tên lớp nhưng không ép kiểu được</a>

<details>
<summary>Xem chi tiết</summary>

# Tái hiện và sửa lỗi cùng tên lớp nhưng không ép kiểu được trong Java

> **Java Core → ClassLoader → Thực hành.** Hai đối tượng có class cùng **binary name**, cùng source code, thậm chí cùng bytecode lấy từ một thư mục/JAR, vẫn có thể **không ép kiểu được** nếu các lớp đó được định nghĩa bởi **hai ClassLoader khác nhau**. Lỗi hay gặp trong hệ plugin, application server, OSGi, hot reload, test class loaders hoặc ứng dụng đóng gói trùng bản host API. Bài thực hành dùng Java 17+ để (1) tái hiện `ClassCastException` với hai loaders độc lập, (2) **sửa** bằng giao diện dùng chung do parent/host loader định nghĩa, và (3) cố tình tạo lại lỗi khi plugin tự định nghĩa bản sao API theo child-first. Mọi chương trình đều có file, lệnh build, stdout đã kiểm tra và giải thích gắn với runtime class identity.

## 1. Vấn đề: tên lớp là giống nhau nhưng type identity thì khác

Trong Java, runtime type của một class hoặc interface thông thường **không được quyết định chỉ bởi tên đầy đủ**. Theo JVM Specification `5.3`:

```text
Runtime class identity = (binary name, defining ClassLoader)
```

Ví dụ cùng binary name `dup.Token`:

```text
                 ClassLoader L1
                      |
                      v
           defineClass("dup.Token")
                      |
                      v
                Class<?> A

                 ClassLoader L2
                      |
                      v
           defineClass("dup.Token")
                      |
                      v
                Class<?> B

A.getName() == B.getName()     // nội dung String giống nhau
A == B                         // false
A.getClassLoader() == L1       // true
B.getClassLoader() == L2       // true
```

Điều đó có nghĩa:

- Một instance của `A` **không tự động là** instance của `B`.
- `B.class` không thể cast thành `A.class` chỉ nhờ hai class có cùng tên.
- `Class.cast` cũng dùng **runtime type identity**, không chỉ so sánh `getName()`.
- Nếu cả hai được định nghĩa bởi hai loaders khác nhau, kể cả nội dung bytecode trùng nhau 100%, `ClassCastException` vẫn có thể xảy ra.
- Hai Java packages cũng có thể là **hai runtime packages khác nhau** khi cùng package name nhưng defining loader khác; package-private access và các quy tắc liên kết liên quan chịu ảnh hưởng.
- JVM không “hợp nhất” hai lớp khác loaders sau khi chúng đã được định nghĩa.

Đây là cơ chế đúng của JVM để cho phép nhiều phiên bản một thư viện tồn tại cạnh nhau và cô lập namespaces, **không phải bug của phép cast**.

## 2. Tái hiện tối giản: cùng một file .class, hai URLClassLoader

Ta cố tình dùng **cùng một thư mục chứa đúng một `dup/Token.class`** và tạo **hai instance `URLClassLoader` khác nhau**, đều có parent là platform loader. Vì `dup.Token` không có ở parent, mỗi loader phải tự định nghĩa một runtime class.

Các nguồn:

```text
src/
  dup/dup/Token.java
  host/api/Message.java
  plugin/impl/HelloPlugin.java
  app/
    LoaderIdentityDemo.java
    DuplicateApiDemo.java
build/
  duplicate/                  (javac tạo)
  host/                       (javac tạo)
  plugin/                     (javac tạo)
  app/                        (javac tạo)
plugin.jar                    (jar tạo)
plugin-with-api.jar           (jar tạo)
```

Ta sẽ dùng cả năm file nguồn xuyên suốt các thử nghiệm để người học có thể copy thành một project duy nhất.

### File 1: src/dup/dup/Token.java

```java
package dup;

public final class Token {
    public String value() {
        return "token";
    }
}
```

Class này không implements interface nào từ host. Đây là một kiểu cụ thể dùng để **chứng minh cast hỏng**.

### Lấy cùng một class bằng hai loaders

Đoạn code sau sẽ xuất hiện trong `LoaderIdentityDemo.main` ở mục 5:

```java
URL classes = Path.of("build/duplicate")
        .toAbsolutePath().toUri().toURL();

try (URLClassLoader left = new URLClassLoader(
             new URL[]{classes},
             ClassLoader.getPlatformClassLoader());
     URLClassLoader right = new URLClassLoader(
             new URL[]{classes},
             ClassLoader.getPlatformClassLoader())) {

    Class<?> a = Class.forName("dup.Token", true, left);
    Class<?> b = Class.forName("dup.Token", true, right);

    Object rightObject = b.getDeclaredConstructor().newInstance();
    a.cast(rightObject);  // ném ClassCastException
}
```

Đây là **trích đoạn** của chương trình đầy đủ, không phải file Java độc lập. `Class.forName(name, true, loader)` nạp/tìm lớp theo loader được chỉ định và yêu cầu initialization; trong ví dụ `Token` không có static initializer đặc biệt. Cùng directory URL **không** làm hai loader trở thành cùng một object.

## 3. Vì sao JVM không chấp nhận cast?

Một phép cast runtime không chỉ hỏi tên lớp trùng không, mà hỏi quan hệ kiểu thực:

```text
Object rightObject là instance của:
   (dup.Token, defining loader = right)

Lệnh a.cast(rightObject) yêu cầu nó là instance của:
   (dup.Token, defining loader = left)

Hai Class<?> khác identity -> không có quan hệ assignable
                           -> ClassCastException
```

`java.lang.Class#cast(Object)` kiểm tra đối tượng có thể được cast đến type mà `Class<?>` này đại diện không. `Class#isInstance(Object)` và `Class#isAssignableFrom(Class<?>)` cũng hữu ích để kiểm tra trước:

```java
System.out.println(a.isInstance(rightObject)); // false
System.out.println(a.isAssignableFrom(b));      // false
System.out.println(a == b);                     // false
```

Các dòng là snippet đặt trong scope có `a`, `b`, `rightObject` của ví dụ mục 2. Chúng không thể “sửa” quan hệ type identity; chỉ phản ánh trạng thái thực tế.

Cũng có thể xảy ra tình huống subclass quan hệ được chia sẻ khiến cast tới **interface/superclass chung** thành công, dù ép kiểu giữa **hai concrete implementation classes** vẫn thất bại. Đây là chìa khóa để sửa trong kiến trúc plugin.

### Tại sao phản chiếu gọi method vẫn có thể thành công?

Ngay khi hai concrete types không cast trực tiếp được, reflection có thể gọi public method trên đúng `Class<?>` và object tương ứng:

```java
Object value = b.getDeclaredConstructor().newInstance();
Object result = b.getMethod("value").invoke(value);
```

Cách này **không biến** `value` thành instance của class `a`. Reflection vẫn kiểm tra access và method receiver; chỉ tránh yêu cầu static cast vào concrete type không tương thích.

Dùng reflection cho mọi lời gọi là giải pháp tình thế nhiều rủi ro (mất type safety, cần xử lý `InvocationTargetException`, các vấn đề JPMS/reflection), không phải thiết kế giao tiếp plugin khuyến nghị.

## 4. Cách sửa: một API chung do host/parent định nghĩa

Hệ plugin nên chia rõ:

```text
System/host ClassLoader
    └── api.Message         <-- chỉ định nghĩa MỘT LẦN
          ^
          |
    +-----+------------------------------+
    |                                    |
PluginLoader A                      PluginLoader B
    └── impl.HelloPlugin               └── impl.HelloPlugin
         implements api.Message             implements api.Message
```

Hai concrete implementation classes vẫn khác identity:

```text
(impl.HelloPlugin, Loader A)
(impl.HelloPlugin, Loader B)
```

**Nhưng cả hai đều implements chính xác một runtime interface:**

```text
(api.Message, HostLoader)
```

Host có thể cast từng implementation object về `api.Message`, sau đó gọi `message()` theo interface chung. Không cần cast object của plugin A thành class implementation do plugin B định nghĩa.

### File 2: src/host/api/Message.java

```java
package api;

public interface Message {
    String message();
}
```

File này **thuộc ứng dụng host**, được compile vào `build/host` và đặt trong host application's classpath. Nó **không** được đóng bản thứ hai vào `plugin.jar` của giải pháp đúng.

### File 3: src/plugin/impl/HelloPlugin.java

```java
package impl;

import api.Message;

public final class HelloPlugin implements Message {
    @Override
    public String message() {
        return "hello-from-plugin";
    }
}
```

Khi biên dịch plugin, compiler phải nhìn thấy host API qua `-cp build/host`. Nhưng **compile-time classpath không đồng nghĩa phải đóng bản API vào JAR plugin**. `plugin.jar` sẽ chỉ chứa `impl/HelloPlugin.class`. Khi JVM resolve interface của plugin, parent loader của plugin phải trả về chính `api.Message` của host.

### Khi nào nên dùng shared API?

- Host cần gọi các method của plugin qua contract rõ ràng.
- Có nhiều plugin loaders sống cạnh nhau, mỗi loader chứa implementation/dependency khác nhau.
- Cần hot reload implementation nhưng host API ổn định.
- Tránh dùng reflection cho mọi operation.
- Muốn public contract có thể kiểm thử về Java source và binary compatibility.

**Đừng ép plugin implementation objects với nhau:**

```java
// KHÔNG làm với implementation từ loader khác:
// HelloPluginFromA typed = (HelloPluginFromA) objectFromB;

// NÊN:
// Message typed = (Message) objectFromAOrB;
```

Hai dòng trên là **pseudocode** vì các tên `HelloPluginFromA` và `objectFromB` không phải class/biến Java đã khai báo. Trong chương trình đầy đủ, code sử dụng trực tiếp interface `Message`.

## 5. Chương trình đầy đủ: LoaderIdentityDemo.java

**File `src/app/LoaderIdentityDemo.java`** tạo cả tình huống cast hỏng và tình huống đã sửa.

```java
import api.Message;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class LoaderIdentityDemo {
    public static void main(String[] args) throws Exception {
        URL classes = Path.of("build/duplicate")
                .toAbsolutePath().toUri().toURL();

        try (URLClassLoader left = new URLClassLoader(
                     new URL[]{classes},
                     ClassLoader.getPlatformClassLoader());
             URLClassLoader right = new URLClassLoader(
                     new URL[]{classes},
                     ClassLoader.getPlatformClassLoader())) {

            Class<?> a = Class.forName("dup.Token", true, left);
            Class<?> b = Class.forName("dup.Token", true, right);
            Object rightObject =
                    b.getDeclaredConstructor().newInstance();

            System.out.println("same binary name: "
                    + a.getName().equals(b.getName()));
            System.out.println("same Class object: "
                    + (a == b));
            System.out.println("distinct defining loaders: "
                    + (a.getClassLoader() == left
                       && b.getClassLoader() == right));
            System.out.println("same runtime module: "
                    + (a.getModule() == b.getModule()));

            try {
                a.cast(rightObject);
                System.out.println("cross-loader cast: succeeded");
            } catch (ClassCastException ex) {
                System.out.println(
                        "cross-loader cast: ClassCastException");
            }
        }

        URL pluginJar = Path.of("plugin.jar")
                .toAbsolutePath().toUri().toURL();
        ClassLoader hostLoader = Message.class.getClassLoader();

        try (URLClassLoader first = new URLClassLoader(
                     new URL[]{pluginJar}, hostLoader);
             URLClassLoader second = new URLClassLoader(
                     new URL[]{pluginJar}, hostLoader)) {

            Class<?> p1 = Class.forName(
                    "impl.HelloPlugin", true, first);
            Class<?> p2 = Class.forName(
                    "impl.HelloPlugin", true, second);

            Message m1 = (Message) p1
                    .getDeclaredConstructor().newInstance();
            Message m2 = (Message) p2
                    .getDeclaredConstructor().newInstance();

            System.out.println("plugin impl classes identical: "
                    + (p1 == p2));
            System.out.println("shared host interface: "
                    + (p1.getInterfaces()[0] == Message.class
                       && p2.getInterfaces()[0] == Message.class));
            System.out.println("both cast to host API: "
                    + (m1 instanceof Message
                       && m2 instanceof Message));
            System.out.println("first plugin result: "
                    + m1.message());
            System.out.println("second plugin result: "
                    + m2.message());
        }
    }
}
```

### Cơ chế của phần thứ nhất

- `left` và `right` có **cùng URL** trỏ `build/duplicate` nhưng là **hai object loader khác nhau**.
- Vì `dup.Token` không có trong platform loader, `left` và `right` mỗi bên định nghĩa riêng một class từ cùng bytes.
- `a.getName().equals(b.getName()) == true`, nhưng `a == b == false`.
- Hai classes nằm trong **hai unnamed modules khác nhau** gắn với hai loaders (trong ví dụ), nên `a.getModule() == b.getModule()` là `false`.
- `a.cast(rightObject)` cố cast instance của B sang A: JVM từ chối như mong đợi.

### Cơ chế của phần thứ hai

- `Message.class` được host/system loader định nghĩa nhờ `build/host` có trong `-cp`.
- Hai plugin loaders dùng **chính loader của `Message.class` làm parent**.
- `plugin.jar` chỉ chứa implementation; `impl.HelloPlugin` do mỗi child loader tự định nghĩa.
- Khi load interface `api.Message`, parent trả về một `Class<?>` chung.
- Dù `p1 != p2`, cả hai instances có thể cast về cùng `Message` và gọi `message()` an toàn.
- Demo dùng `p1.getInterfaces()[0]` vì `HelloPlugin` của demo có đúng một direct interface. Trong ứng dụng thật, đừng dựa vào index 0; dùng `Message.class.isAssignableFrom(p1)` và kiểm tra interface hierarchy.

## 6. Lệnh biên dịch, đóng JAR và chạy

Từ thư mục gốc chứa `src`, thực hiện:

```bash
javac --release 17 -d build/duplicate src/dup/dup/Token.java
javac --release 17 -d build/host src/host/api/Message.java
javac --release 17 -cp build/host -d build/plugin src/plugin/impl/HelloPlugin.java
jar --create --file plugin.jar -C build/plugin .
javac --release 17 -cp build/host -d build/app src/app/LoaderIdentityDemo.java
```

Trên **Linux/macOS**, dấu ngăn cách nhiều entries trong Java classpath là `:`:

```bash
java -cp "build/app:build/host" LoaderIdentityDemo
```

Trên **Windows PowerShell/CMD**, classpath separator là `;`:

```powershell
java -cp "build/app;build/host" LoaderIdentityDemo
```

Không đưa `build/duplicate`, `build/plugin` hoặc `plugin.jar` vào **host application classpath**. Nếu đặt chúng vào đó, host/system loader có thể nạp `dup.Token` hoặc `impl.HelloPlugin` trước child theo parent-first, làm thí nghiệm **không còn đo hai defining loaders khác nhau**.

`Plugin.jar` không cần copy `api/Message.class`, vì `Message` đã có ở host. Tách compile classpath khỏi final plugin archive là mấu chốt thực hành.

### Stdout chính xác đã chạy trên OpenJDK 24 với javac --release 17

```text
same binary name: true
same Class object: false
distinct defining loaders: true
same runtime module: false
cross-loader cast: ClassCastException
plugin impl classes identical: false
shared host interface: true
both cast to host API: true
first plugin result: hello-from-plugin
second plugin result: hello-from-plugin
```

Mười dòng này không phụ thuộc tên thư mục tuyệt đối. Kết quả cho thấy vấn đề là **type identity theo loader**, và sửa đúng ở **shared parent API**, không phải sửa phép cast bằng ép cưỡng bức.

**Đây là một test chức năng, không phải chứng minh plugin được sandbox.** Plugin classes chạy trong cùng JVM process; loaders không tự cấp/thu hồi quyền filesystem/network.

## 7. Phản ví dụ: plugin đóng gói lại API và dùng child-first

Một nguồn lỗi rất thường gặp:

```text
Host:
  build/host/api/Message.class
  (defined by system ClassLoader)

Plugin JAR:
  api/Message.class           <-- bản sao API bị đóng nhầm
  impl/HelloPlugin.class

Child-first PluginLoader:
  ưu tiên api.* từ plugin JAR
  ưu tiên impl.* từ plugin JAR
```

Lúc này:

```text
Host API:
    (api.Message, HostLoader)

Plugin API:
    (api.Message, PluginLoader)

Plugin implementation:
    (impl.HelloPlugin, PluginLoader)
    implements (api.Message, PluginLoader)
```

Dù `HelloPlugin` có khai báo source `implements api.Message`, nó **implements bản API mà child loader tự định nghĩa**, không phải API của host. Host nhìn nó qua `Message.class.isAssignableFrom(pluginClass)` sẽ được `false`. Cast sang `api.Message` của host ném `ClassCastException`.

### Tạo JAR lỗi có chứa bản sao API

Sau khi làm xong phần build mục 6, chạy:

```bash
jar --create --file plugin-with-api.jar \
    -C build/plugin . \
    -C build/host api/Message.class
```

Trên Windows PowerShell, chạy cùng lệnh trên **một dòng**:

```powershell
jar --create --file plugin-with-api.jar -C build/plugin . -C build/host api/Message.class
```

`plugin-with-api.jar` chứa cả `impl/HelloPlugin.class` và `api/Message.class`. Đây là artifact **cố ý sai** để tái hiện bug, không được dùng làm plugin artifact đã sửa.

### File 5: src/app/DuplicateApiDemo.java

```java
import api.Message;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class DuplicateApiDemo {
    static final class ChildFirst extends URLClassLoader {
        ChildFirst(URL url, ClassLoader parent) {
            super(new URL[]{url}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);

                if (c == null) {
                    if (name.startsWith("api.")
                            || name.startsWith("impl.")) {
                        try {
                            c = findClass(name);
                        } catch (ClassNotFoundException missing) {
                            c = super.loadClass(name, false);
                        }
                    } else {
                        c = super.loadClass(name, false);
                    }
                }

                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        URL jar = Path.of("plugin-with-api.jar")
                .toAbsolutePath().toUri().toURL();

        try (ChildFirst loader = new ChildFirst(
                jar, Message.class.getClassLoader())) {

            Class<?> childApi = Class.forName(
                    "api.Message", false, loader);
            Class<?> plugin = Class.forName(
                    "impl.HelloPlugin", true, loader);
            Object instance =
                    plugin.getDeclaredConstructor().newInstance();

            System.out.println("same api binary name: "
                    + childApi.getName()
                              .equals(Message.class.getName()));
            System.out.println("same api Class object: "
                    + (childApi == Message.class));
            System.out.println("plugin implements child api: "
                    + (plugin.getInterfaces()[0] == childApi));
            System.out.println("host API assignable: "
                    + Message.class.isAssignableFrom(plugin));

            try {
                Message typed = (Message) instance;
                System.out.println("host API cast: succeeded");
            } catch (ClassCastException expected) {
                System.out.println(
                        "host API cast: ClassCastException");
            }
        }
    }
}
```

### Vì sao override loadClass có khóa?

Ví dụ child-first này chỉ phục vụ chứng minh bug. Tuy nhiên để tránh tự tạo thêm race/double-define, code vẫn:

1. Khóa `synchronized(getClassLoadingLock(name))`.
2. Kiểm tra `findLoadedClass(name)`.
3. Thử child-first cho `api.` và `impl.` rồi fallback parent qua `super.loadClass(name,false)`.
4. Gọi `resolveClass` khi `resolve == true`.

**Đây không phải classloader production đã qua review**. Trong hệ thống thật, policy child-first phải cân nhắc protected packages (`java.`), class-loading locks, package sealing, certificates, module access, dependency API và security boundaries. Đặc biệt, **không nên child-first cho chính gói API chia sẻ với host**.

### Biên dịch và chạy ví dụ lỗi duplicate API

```bash
javac --release 17 -cp build/host -d build/app src/app/DuplicateApiDemo.java
```

Linux/macOS:

```bash
java -cp "build/app:build/host" DuplicateApiDemo
```

Windows:

```powershell
java -cp "build/app;build/host" DuplicateApiDemo
```

**Stdout đã kiểm chứng bằng OpenJDK 24, bytecode build với `--release 17`:**

```text
same api binary name: true
same api Class object: false
plugin implements child api: true
host API assignable: false
host API cast: ClassCastException
```

Cả host và child đều có `api.Message` theo cùng binary name, nhưng `Class<?>` khác nhau; plugin implements bản child API. Đây là **đúng loại lỗi** khi plugin JAR đóng trùng host API và child-first loader làm shadow API của host.

## 8. Sửa triệt để duplicate API

Để sửa trường hợp mục 7 trong ứng dụng thật:

1. **Đưa host API vào shared parent classpath/module** và đảm bảo nó được nạp bởi một loader ổn định.
2. **Không đóng API class thứ hai vào plugin JAR**. Nếu plugin chỉ cần API lúc compile, đánh dấu dependency dạng *provided/compileOnly* phù hợp với Maven/Gradle.
3. **Delegation policy cho API namespace phải parent-first**. Nếu bắt buộc child-first cho third-party libraries để hỗ trợ nhiều versions, tách rule rõ ràng: `java.` và `api.` của host là parent-first, còn `impl.`/thư viện plugin do child xử lý theo thiết kế.
4. **Tạo lại plugin loader** sau thay đổi. Một loader đã định nghĩa class sai không tự thay definition chỉ vì bạn xóa bản `api/Message.class` khỏi JAR trên disk.
5. **Kiểm tra type identity** bằng `api.Message.class.isAssignableFrom(implClass)` trước khi cast trong hệ plugin.
6. **Chạy integration test đúng topology loader thực tế**. Unit test chỉ chạy tất cả lớp trên một classpath không thể tái hiện bug do nhiều defining loaders.
7. **Quản lý version của API hợp đồng**. Shared API phải binary-compatible giữa host và plugin; nếu interfaces/method signatures thay đổi không tương thích, còn có thể gặp `NoSuchMethodError`, `AbstractMethodError` hoặc lỗi linkage khác.

### Maven: plugin API dependency dạng provided

Một fragment `pom.xml` của plugin:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>plugin-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

Đây là **fragment cấu hình minh họa**, tọa độ artifact `com.example:plugin-api:1.0.0` là tên ví dụ. `provided` nói dependency có sẵn lúc compile nhưng thông thường không được đóng thành runtime transitively included dependency của plugin; nếu dùng shaded/assembly plugin, kiểm tra artifact thật vì custom packaging rules có thể vẫn copy nó.

Với Maven Shade, kiểm tra dependencies/exclusions/relocations để bảo đảm `api.Message` không bị đóng một bản khác vào plugin. **Relocate API shared** không tạo ra shared type identity; nó sẽ đổi tên API và phá contract với host.

### Gradle: dùng compileOnly cho shared API

Một fragment Gradle Groovy DSL:

```groovy
dependencies {
    compileOnly 'com.example:plugin-api:1.0.0'
}
```

Đây là cú pháp minh họa. `compileOnly` không tự đảm bảo một uber-JAR do task đóng gói tùy chỉnh tạo ra không sao chép API; cần xem nội dung final plugin JAR.

### Kiểm tra JAR có bản trùng API

```bash
jar tf plugin.jar
jar tf plugin-with-api.jar
```

Ở ví dụ:

```text
plugin.jar:
  impl/HelloPlugin.class

plugin-with-api.jar:
  impl/HelloPlugin.class
  api/Message.class
```

Hai đoạn này **trích lược các entry liên quan**, không phải full output của `jar tf` (vì còn META-INF/MANIFEST.MF). Trong production, scan tất cả plugin artifacts và nested/shaded JARs nếu runtime loader giải nén/tải chúng.

## 9. Vì sao parent-first quan trọng khi sửa lỗi cast?

`ClassLoader.loadClass` mặc định kiểm tra lớp đã nạp, hỏi parent trước rồi mới gọi `findClass` của child. Khi child `loadClass("api.Message")` nhưng parent đã cung cấp API đó, **defining loader là parent**; child chỉ có thể đóng vai trò initiating loader. Vì vậy bản API trùng có trong plugin JAR vẫn có thể *chưa* gây lỗi khi loader parent-first.

Phản ví dụ ở mục 7 cố tình áp dụng **child-first cho `api.*`**, khiến plugin định nghĩa một `api.Message` khác và host cast thất bại. Cách sửa là giữ `api.*`/shared contracts parent-first, tách namespace implementation và dependencies riêng. Nếu cần child-first cho thư viện private, phải giữ `getClassLoadingLock`, `findLoadedClass`, delegation, `resolveClass` và phân loại exception đúng; không áp một policy cho mọi package.

**Kiểm chứng thực tế:** `Message.class.isAssignableFrom(pluginClass)` cùng `pluginClass.getClassLoader()` đáng tin hơn việc đối chiếu class name hay source JAR. TCCL và reflective access không thay đổi runtime identity đã được JVM định nghĩa.

## 10. Chẩn đoán ClassCastException thực tế

Thông báo lỗi có thể giống:

```text
java.lang.ClassCastException: class dup.Token cannot be cast to class dup.Token
(dup.Token is in unnamed module of loader ...;
 dup.Token is in unnamed module of loader ...)
```

Đây là **mẫu thông báo**; OpenJDK version, tên loader và module metadata có thể làm format/wording khác. Phần “cannot be cast to chính nó” không vô lý nếu **hai runtime types** có defining loaders khác nhau.

### Mẫu log type identity đúng cách

```java
static void describe(Object obj) {
    if (obj == null) {
        System.out.println("<null>");
        return;
    }
    Class<?> type = obj.getClass();
    System.out.println("typeName=" + type.getName());
    System.out.println("typeIdentity=" +
            System.identityHashCode(type));
    System.out.println("definingLoader=" + type.getClassLoader());
    System.out.println("loaderIdentity=" +
            System.identityHashCode(type.getClassLoader()));
    System.out.println("module=" + type.getModule());
}
```

Đây là **snippet method** đặt trong class bất kỳ; dùng `System.identityHashCode` hỗ trợ phân biệt objects trong logs, nhưng hash có thể collision — **không dùng hash thay phép so sánh `==`**.

Các điểm cần ghi riêng cho **expected type** và **actual object**:

- `expected.getName()` và `actual.getClass().getName()`.
- `expected == actual.getClass()`.
- `expected.isInstance(actual)`.
- `expected.isAssignableFrom(actual.getClass())`.
- `expected.getClassLoader()` và `actual.getClass().getClassLoader()`, so sánh **identity**.
- `getModule()` và vị trí source (`ProtectionDomain.CodeSource`) khi có.
- Khi có custom plugin loader, ghi `pluginId`, loader generation, parent và source JAR/version.

**Cẩn thận `null`:** Bootstrap loader được biểu diễn bằng `null` trong `Class.getClassLoader()`. Hai classes có loader null chưa tự kết luận mọi điều về type identity nếu bạn chưa xem tên/module; tuy nhiên đối với `String.class`, đây là expected bootstrap behavior.

### Đừng chỉ nhìn tên JAR

Hai URLClassLoader có thể đọc **chính một `plugin.jar`** nhưng tạo hai implementation classes không tương thích với nhau. Ngược lại, hai classes đọc từ hai JAR khác nhau có thể cùng implement **một host API** và cùng cast thành interface đó.

`CodeSource.location` giúp debug artifact/version, nhưng **không thay thế** so sánh defining-loader identity. Với generated classes, custom loaders hoặc JDK runtime classes, `CodeSource` có thể `null`.

### JVM class loading logs

Với OpenJDK/HotSpot Java 17+:

```bash
java -Xlog:class+load=info -cp "build/app:build/host" LoaderIdentityDemo
```

Đây là câu lệnh Linux/macOS; Windows thay dấu `:` bằng `;`. `-Xlog:class+load=info` in nhiều lines thêm vào output, có thể cho biết nguồn JAR/directory/loader; lệnh **không có exact stdout giống mục 6** vì logging chi tiết.

Log source path giúp xác định artifact, nhưng muốn biết class đang chạy thuộc **instance loader nào**, cần kết hợp `getClassLoader()`, object identity và debugger/heap data. `-Xlog` là tùy chọn HotSpot/Unified Logging, không phải API bảo đảm trên mọi JVM vendor.

## 11. Một số nguyên nhân gần giống nhưng cách sửa khác

| Triệu chứng | Nguyên nhân có thể | Cách phân biệt |
| --- | --- | --- |
| `ClassCastException` dù tên giống nhau | Hai defining loaders định nghĩa cùng binary name. | So `Class<?> ==` và `getClassLoader()`. |
| `ClassCastException` giữa hai tên khác nhau | Hai unrelated types bình thường. | Xem actual/expected names và hierarchy trước. |
| `NoClassDefFoundError` khi nạp plugin | Class API/dependency thiếu hoặc định nghĩa không tìm được. | Kiểm tra cause, loader search/delegation và JAR. |
| `ClassNotFoundException` khi `Class.forName` | Caller chọn sai loader hoặc dependency không có. | Truyền đúng loader, kiểm tra loader hierarchy/classpath. |
| `LinkageError` “loader constraint violation” | JVM phát hiện hai loader resolve cùng type trong signature/override context không nhất quán. | Xem offending class names, method descriptors, loader identities. |
| `NoSuchMethodError` | API binary version mismatch, method signature thay đổi. | So API contract/version và `javap` signatures. |
| `AbstractMethodError` | Implementation không khớp interface/abstract method được runtime mong đợi. | Build lại plugin theo contract binary phù hợp. |
| `IllegalAccessError` / `InaccessibleObjectException` | Java/JPMS access policies hoặc reflection boundaries. | Xem module readability/exports/opens, khác lỗi type identity. |
| Duplicate class definition `LinkageError` | **Cùng loader** cố define lại một binary name. | Kiểm tra `findLoadedClass`/`getClassLoadingLock` và override `loadClass`. |
| `UnsupportedClassVersionError` | Runtime JVM không hỗ trợ class-file major/minor. | Đọc version bytecode/JDK, không phải cast issue. |

Không sửa bằng cách catch `ClassCastException` rồi bỏ qua; điều đó chỉ che lỗi kiến trúc và có thể làm plugin hoạt động nửa chừng.

### Loader constraint violation không giống lỗi cast

Java linking có **loading constraints** cho những type xuất hiện trong inherited method signatures hoặc các tình huống liên kết yêu cầu type identity nhất quán giữa loaders. Nếu một loader đã nạp tên `api.Message` bằng class A nhưng một hierarchy liên quan yêu cầu cùng tên được nhìn thấy như class B, JVM có thể ném `LinkageError` ngay khi kiểm tra constraints.

`ClassCastException` là lỗi cast của object không tương thích; `LinkageError` có thể xuất hiện sớm hơn trong loading/linking. Hai hiện tượng có chung gốc “lớp trùng tên nhưng loader khác”, nhưng **không phải subtype exception giống nhau**.

## 12. Chú ý với ServiceLoader, TCCL và framework containers

`ServiceLoader` có thể được dùng để tìm plugin providers, nhưng bạn phải đảm bảo **service interface** do loader được cả host và provider nhìn thấy theo cùng identity.

Ví dụ contract mong đợi:

```java
ServiceLoader<Message> services =
        ServiceLoader.load(Message.class, pluginLoader);
```

Đây là **snippet** cần `import java.util.ServiceLoader;` cùng biến `pluginLoader`. Nó còn yêu cầu service registration metadata (`META-INF/services/api.Message`) hoặc module `provides`/`uses` đúng. Dù `ServiceLoader` tìm được class provider, nếu provider thực hiện **bản API khác loader**, có thể lỗi vì không phải subtype của service.

Một vài container dùng thread context class loader (TCCL) để chọn provider/dependency:

```java
Thread t = Thread.currentThread();
ClassLoader old = t.getContextClassLoader();
try {
    t.setContextClassLoader(pluginLoader);
    // Thực hiện provider lookup / plugin call...
} finally {
    t.setContextClassLoader(old);
}
```

TCCL thay **ngữ cảnh tìm** cho API có hỗ trợ nó; nó **không tái định nghĩa** bản `api.Message` đã nạp bởi host. Thay TCCL cũng không biến `impl.HelloPlugin` đã định nghĩa sai thành subtype của host interface.

Application servers (Tomcat, Jakarta EE), OSGi, Spring Boot DevTools hoặc plugin frameworks có loader hierarchy tùy biến. Nên inspect topology cụ thể thay vì mặc định mọi loader là parent-first hoặc mọi dependency nằm trên system classpath.

## 13. Vòng đời hot reload: tránh ép kiểu qua generation cũ

Trong một hệ plugin hot reload:

```text
Generation 1:
    loader-v1 -> impl.HelloPlugin (runtime type 1)

Generation 2:
    loader-v2 -> impl.HelloPlugin (runtime type 2)

Host:
    api.Message -> định nghĩa một lần, sống xuyên generations
```

Một object `impl.HelloPlugin` của loader-v1 **không** cast được thành concrete type của loader-v2, kể cả hai cùng có source. Nhưng cả hai có thể cast sang `api.Message` nếu API nằm ở host loader và binary-compatible.

Để dỡ generation cũ:

1. Ngăn tasks mới dùng object implementation cũ.
2. Unregister event handlers, service provider references và caches giữ `Class<?>`/plugin instances.
3. Dừng tasks, xử lý TCCL/ThreadLocal có thể giữ loader cũ.
4. Đóng `URLClassLoader` nếu là loại hỗ trợ `close`.
5. Bỏ host references đến objects/`Class<?>`/loader generation cũ.
6. Quan sát reachability/GC nếu cần, nhưng không khẳng định class unloading diễn ra ngay.

`URLClassLoader.close()` **không xóa classes đã load**, không sửa type identity và không “ép” old instance thành new generation. Nó đóng tài nguyên loader theo API; muốn thay implementation, dùng loader mới và đổi các references ở application layer.

### Không truyền implementation object qua boundary như kiểu public API

Nếu host API khai báo:

```java
public interface Message {
    String message();
}
```

đây là contract an toàn trong demo vì chỉ dùng Java `String` thuộc runtime và được host/plugin chia sẻ.

Nhưng nếu API trả về `impl.HelloPlugin` hoặc một kiểu `plugin.private.Data` do child loader định nghĩa, host có thể không resolve/cast được kiểu đó. Thiết kế public plugin contract bằng **các types thuộc host/shared API hoặc Java/JDK types** có identity thống nhất; sử dụng DTO/boundary protocol phù hợp khi cách ly sâu hơn.

Tham số generic cũng cần lưu ý: `List<PluginImpl>` vẫn có thể mang instance của class do plugin loader định nghĩa; **type erasure không biến implementation types thành shared types** và có thể ném cast ở nơi đọc phần tử.

## 14. Anti-pattern và hướng sửa thực tế

| Sai lầm | Tại sao lỗi | Cách sửa |
| --- | --- | --- |
| “Cùng binary name thì cast được.” | Thiếu defining loader identity. | So `Class<?>` và loader, dùng shared abstraction. |
| “Cùng JAR bytes thì cùng Class.” | Hai loader instances vẫn tạo hai namespaces. | Tạo một loader khi thực sự cần cùng concrete type, hoặc dùng shared API. |
| Đóng `plugin-api.jar` vào cả host và child-first plugin | Hai runtime copies `api.Message`. | Provided/compileOnly API; parent-first API namespace. |
| Dùng child-first cho cả `api.*` và `impl.*` | Plugin tự định nghĩa API thay vì dùng host API. | Child-first selective cho implementation deps; host API always parent-first. |
| Chỉ xóa file API khỏi plugin JAR khi JVM đã load | Class đã được define không tự thay thế. | Tạo class loader generation mới/restart và rebuild artifact. |
| Dùng reflection làm “sửa” type identity | Chỉ gọi method qua reflection, không tạo subtyping thật. | Shared interface or explicit data transfer boundary. |
| Ép `ClassLoader.getSystemClassLoader()` sang `URLClassLoader` | Java 9+ system loader thường không phải URLClassLoader. | Dùng API phù hợp hoặc custom loader đúng loại. |
| Thấy `CodeSource.location` giống nhau nên bỏ qua loader | Archive/path không quyết định Class identity. | Xem `getClassLoader()` trực tiếp. |
| Đổi TCCL và kỳ vọng cast cũ thành công | TCCL không sửa class đã định nghĩa. | Sửa owner loader/API hierarchy, nạp lại generation. |
| Lặp `defineClass` với cùng name và loader | JVM từ chối duplicate class definition. | `findLoadedClass` và proper locking/loader policy. |
| Tắt JPMS/`--add-opens` để sửa `ClassCastException` | Mở reflective access không hợp nhất Class identities. | Sửa định nghĩa API/loader graph. |
| Chỉ kiểm tra bằng `instanceof` với class đã link tại host | Có thể thấy false nhưng chưa hiểu loader. | Log expected/actual `Class<?>` + loaders + modules. |

## 15. Checklist từng bước khi một plugin không ép kiểu được

1. **Đọc lỗi chính xác.** Xác nhận thực sự là `ClassCastException`, `LinkageError` loader constraint, hay lỗi khác.
2. **Ghi expected class:** binary name, `Class<?>` object, defining loader, module, version.
3. **Ghi actual class:** `object.getClass()`, name, loader, module, source location nếu có.
4. **So sánh bằng `==`**, không chỉ `getName()` hay `toString()`.
5. **Kiểm tra API dùng chung:** `HostApi.class == pluginClass.getInterfaces()[...]` có phù hợp, hoặc dùng `HostApi.class.isAssignableFrom(pluginClass)`.
6. **Kiểm tra JAR:** bản sao `api.*` có bị shading/assembly/loader copy vào plugin không?
7. **Kiểm tra delegation:** parent-first cho host API chưa, child-first có vô tình shadow shared packages?
8. **Kiểm tra runtime:** dependency/JAR nào đang thực sự được load; xác minh parent loader vs child loader.
9. **Sửa build/deployment:** loại duplicate API khỏi plugin artifact, pin API version, chia shared loader.
10. **Thay loader generation:** không kỳ vọng runtime type đã định nghĩa tự biến đổi.
11. **Chạy integration test** với ít nhất hai child loader instances và hai phiên bản plugin nếu dự án hỗ trợ hot reload.
12. **Quản lý lifecycle** để old instances/caches không bị đưa sang generation mới.

Không coi plugin JAR có checksum giống nhau là đủ; checksum xác minh bytes, còn vấn đề ở **defining loader identity**.

## 16. Bài tập tự kiểm tra và đáp án

1. **Hai loader riêng cùng đọc một file `dup.Token.class`, vì sao `Class<?>` khác nhau?**  
   Runtime identity gồm binary name và defining loader; hai instances loader độc lập định nghĩa hai runtime types.

2. **`Class.getName()` của A và B giống nhau chứng minh cast hợp lệ?**  
   Không. So identity `A == B` hoặc quan hệ `isAssignableFrom`.

3. **Nếu cùng một loader gọi `loadClass("dup.Token")` nhiều lần, có tạo nhiều classes?**  
   Theo đường chuẩn, `findLoadedClass` và synchronization tránh định nghĩa lại; trả một `Class<?>` cho name/loader thích hợp.

4. **Khi hai implementations từ hai plugin loaders khác nhau, cast về cùng host interface có thể thành công không?**  
   Có, nếu cả hai implement đúng **một** runtime interface do parent/host định nghĩa.

5. **Plugin JAR có bản sao API nhưng parent-first API lookup trả host API, cast có thể thành công?**  
   Có thể. Bản sao trong JAR vẫn là rủi ro thiết kế/packaging; nếu child-first hoặc hierarchy đổi thì có thể lỗi.

6. **Tại sao `Message.class.isAssignableFrom(pluginClass)` false trong `DuplicateApiDemo`?**  
   Plugin implements `api.Message` do child loader định nghĩa; host `Message.class` có defining loader khác.

7. **Chỉ sửa TCCL có biến một class child API thành host API không?**  
   Không. TCCL chỉ giúp lookup trong các APIs có dùng nó, không sửa Class identity đã tồn tại.

8. **`CodeSource.location` giống nhau có nghĩa hai classes runtime giống nhau?**  
   Không. Cùng JAR có thể được load bởi nhiều child loader instances.

9. **`URLClassLoader.close()` có làm instance cũ cast được sang class phiên bản mới?**  
   Không. Close không redefine lớp và không thay type identity.

10. **Ứng dụng cần hai plugin dùng dependencies khác version, nên cast implementation với nhau không?**  
    Không. Dùng shared host API, DTO hoặc giao thức rõ ràng.

11. **`ClassCastException` và `LinkageError` duplicate definition giống nhau?**  
    Không. ClassCastException là cast sai; duplicate definition do cùng defining loader cố định nghĩa trùng name; loader constraint errors thuộc linking.

12. **Trong Maven/Gradle, làm sao giảm nguy cơ đóng trùng host API?**  
    `provided` hoặc `compileOnly` tương ứng, kiểm tra final JAR và giữ parent-first API namespace.

13. **Vì sao cần kiểm thử trên loader topology của production?**  
    Unit test dùng một app loader duy nhất có thể che class identity mismatch xảy ra với custom child loaders.

14. **Khi API contract nhận tham số thuộc package private của plugin, có vấn đề gì?**  
    Host có thể không resolve được cùng class identity cho type ấy; public boundary nên dùng shared/host types.

## 17. Tài liệu chuẩn

- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, `loadClass`, `findClass`, `findLoadedClass`, `defineClass` và defining loader.
- [JVMS Java SE 21 §5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): class identity là binary name + defining loader; initiating vs defining loader và loading constraints.
- [Java SE 21 — Class](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): `getName`, `cast`, `isInstance`, `isAssignableFrom`, `getClassLoader`, `getModule`.
- [Java SE 21 — URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): constructors, URL sources, `close`.
- [Java SE 21 — ClassCastException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassCastException.html): ý nghĩa phép ép kiểu thất bại.
- [Java SE 21 — ServiceLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html): SPI service/provider identity và loader.
- [Java SE 21 — Package](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Package.html): runtime packages và package metadata.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-exercises-008">Tái hiện NoClassDefFoundError và lỗi khởi tạo lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Tái hiện NoClassDefFoundError và lỗi khởi tạo lớp trong Java

Một ứng dụng Java có thể biên dịch thành công rồi thất bại khi chạy với `java.lang.NoClassDefFoundError`. Nhiều người nhìn tên lỗi và kết luận đơn giản rằng “file `.class` không tồn tại”. Điều này **đúng trong một số tình huống nhưng không phải tất cả**. Một class **có thể vẫn nằm trên classpath và đã được JVM tìm thấy**, nhưng vì **static initialization thất bại**, các lần sử dụng class sau đó vẫn ném `NoClassDefFoundError: Could not initialize class ...`.

Bài thực hành này dùng Java 17+ để tái hiện độc lập hai nhóm lỗi:

1. **Thiếu dependency tại runtime:** `app.MissingDemo` đã biên dịch khi `lib.Helper` còn tồn tại, nhưng lúc chạy chỉ cung cấp output của ứng dụng. JVM không tìm được class cần liên kết: xuất hiện `NoClassDefFoundError`; nguyên nhân (*cause*) trong thí nghiệm là `ClassNotFoundException`.
2. **Lỗi class initialization (`<clinit>`):** class `demo.Broken` được tìm thấy, nhưng static initializer ném `IllegalStateException`. Lần active use đầu tiên thấy `ExceptionInInitializerError`, lớp chuyển sang trạng thái **erroneous**; lần active use tiếp theo trong **cùng JVM, cùng định nghĩa class** thấy `NoClassDefFoundError`.

Mục tiêu: hiểu thứ tự **loading → linking → initialization**, phân biệt ba lỗi `ClassNotFoundException`, `ExceptionInInitializerError` và `NoClassDefFoundError`; biết viết thí nghiệm biên dịch/chạy đúng, giải thích stdout đã kiểm chứng, xác định nguyên nhân gốc bằng stack trace/cause/loader và xử lý đúng khi ứng dụng hoặc plugin đã nạp class lỗi.

## 1. Đặt vấn đề: tìm không được class hay class đã khởi tạo thất bại?

Hãy xem ba thông báo:

```text
java.lang.ClassNotFoundException: lib.Helper

java.lang.NoClassDefFoundError: lib/Helper

java.lang.NoClassDefFoundError: Could not initialize class demo.Broken
```

Các thông báo có thể trông giống nhau, nhưng nói về những bước khác nhau:

| Tình huống | Loại throwable thường gặp | Điều cần kiểm tra |
| --- | --- | --- |
| Ứng dụng gọi `ClassLoader.loadClass("lib.Helper")` theo tên nhưng loader không thấy | `ClassNotFoundException` (*checked exception*) | Tên class, loader cụ thể, classpath/module path |
| Bytecode đã compile cần `lib.Helper` nhưng runtime không tìm được định nghĩa | `NoClassDefFoundError` (*LinkageError*) | Compile/runtime dependencies, loader, file JAR, class root |
| Class được tìm thấy, static initializer ném `RuntimeException` ở lần đầu | `ExceptionInInitializerError` (*LinkageError*) | Lỗi thực sự trong `static { ... }` hoặc static field initialization |
| Sau khi class đã rơi vào trạng thái khởi tạo lỗi, code lại active-use cùng class | `NoClassDefFoundError` | Tìm **lần khởi tạo thất bại đầu tiên** trong log, không chỉ nhìn lỗi về sau |

Với `NoClassDefFoundError`, **message và cause là dữ liệu chẩn đoán, không phải API ổn định để parse bằng chuỗi cứng**. Nội dung của thông báo có thể khác theo JVM hoặc cách tình huống phát sinh.

## 2. Thứ bậc lỗi trong java.lang

```text
java.lang.Throwable
├── Exception
│   └── ReflectiveOperationException
│       └── ClassNotFoundException
└── Error
    └── LinkageError
        ├── NoClassDefFoundError
        ├── ExceptionInInitializerError
        ├── NoSuchMethodError
        ├── NoSuchFieldError
        ├── IllegalAccessError
        └── ...
```

- `ClassNotFoundException` là **checked exception** thường xuất hiện từ lời gọi tìm class bằng tên như `Class.forName(...)` và `ClassLoader.loadClass(...)`.
- `NoClassDefFoundError` và `ExceptionInInitializerError` là **Error thuộc họ LinkageError**.
- `catch (Exception ex)` **không bắt** được hai Error này. Trong phòng lab có thể cố ý `catch (LinkageError)` để quan sát hai lần sử dụng; **không nên che lỗi liên kết trong nghiệp vụ production**.
- Một `NoClassDefFoundError` có thể có `getCause()==ClassNotFoundException` khi thiếu class; nhưng nếu lớp đã ở trạng thái erroneous, cause **có thể là lỗi khởi tạo trước đó**. Vì thế phải đọc cả cause chain.

## 3. Ba giai đoạn của vòng đời class

Theo JLS/JVMS, khi sử dụng class, JVM có các công việc phân biệt:

```text
LOAD
  |
  | JVM/class loader tìm binary class definition và tạo Class<?> runtime
  v
LINK
  |
  +-- Verify: kiểm tra tính hợp lệ của bytecode
  +-- Prepare: chuẩn bị static fields với giá trị mặc định
  +-- Resolve: nối symbolic references tới class/field/method
  |            (một số resolution có thể được trì hoãn)
  v
INITIALIZE
  |
  +-- Khởi tạo superclass/superinterfaces cần thiết
  +-- Gán static fields theo initializer
  +-- Chạy static blocks theo thứ tự văn bản
  v
READY hoặc ERRONEOUS
```

### Loading

`ClassLoader.loadClass("demo.Broken")` thường tải hoặc tìm class nhưng **không yêu cầu chạy static initializer ngay**. Class `Broken` có thể đã được biểu diễn bởi một `Class<?>` hợp lệ dù `<clinit>` chưa chạy.

### Linking

Trong giai đoạn verification/preparation/resolution, bytecode có thể tham chiếu tới classes chưa được nạp vào thời điểm ban đầu. JVM có quyền trì hoãn một số symbolic reference resolution; vì vậy lỗi dependency có thể chỉ xuất hiện **khi thực thi code path tương ứng**. Không giả định mọi dependency thiếu đều làm ứng dụng chết ngay lúc bắt đầu.

### Initialization

Khi chương trình lần đầu *active use* một class, JVM đảm bảo class được initialize theo quy tắc Java. Static field initializers và `static { ... }` được thực hiện trong thứ tự thích hợp. Nếu initializer hoàn thành bình thường, class sẵn sàng. Nếu initializer thất bại, class có thể bị đánh dấu **erroneous**; các lần khởi tạo tiếp theo của **cùng runtime class** không được tự retry như một constructor thông thường.

**Đây là nền tảng để hiểu vì sao “file class vẫn còn trên đĩa” nhưng sau lần lỗi đầu lại xuất hiện `NoClassDefFoundError`.**

## 4. Lab A — Thiếu thư viện ở runtime, tái hiện NoClassDefFoundError

Mục tiêu: tạo một dependency `lib.Helper` riêng khỏi ứng dụng. Biên dịch ứng dụng với dependency đầy đủ rồi **cố ý bỏ thư mục dependency khỏi runtime classpath**.

### 4.1 Cấu trúc dự án

```text
ncdfe-lab/
├── src/
│   ├── lib/
│   │   └── Helper.java
│   └── app/
│       └── MissingDemo.java
├── lib-classes/               (output của Helper)
└── app-classes/               (output của MissingDemo)
```

Tạo `lib-classes` và `app-classes` trước khi biên dịch. Chạy các lệnh từ **thư mục `ncdfe-lab`**, không phải từ `src`.

### 4.2 Tạo thư viện

File `src/lib/Helper.java`:

```java
package lib;

public final class Helper {
    public static String message() {
        return "Helper available";
    }
}
```

Class `lib.Helper` là dependency của ứng dụng. Compiler cần thấy API `message()` để biên dịch thành công.

### 4.3 Tạo consumer

File `src/app/MissingDemo.java`:

```java
package app;

import lib.Helper;

public final class MissingDemo {
    public static void main(String[] args) {
        System.out.println("app started");

        try {
            System.out.println(Helper.message());
        } catch (NoClassDefFoundError error) {
            System.out.println("error="
                    + error.getClass().getSimpleName());
            System.out.println("missing=" + error.getMessage());

            Throwable cause = error.getCause();
            System.out.println("cause="
                    + (cause == null
                       ? "<none>"
                       : cause.getClass().getSimpleName()));
        }
    }
}
```

**Vì sao cố ý bắt `NoClassDefFoundError`?** Chỉ để thí nghiệm in được loại lỗi, tên class không tìm được và cause thay vì kết thúc JVM với stack trace dài. Một ứng dụng thật phải sửa runtime deployment; nó không nên coi dependency thiếu là một nhánh nghiệp vụ bình thường.

### 4.4 Biên dịch với đúng dependency

```bash
javac --release 17 -d lib-classes src/lib/Helper.java
javac --release 17 -cp lib-classes -d app-classes src/app/MissingDemo.java
```

- Lệnh đầu tạo `lib-classes/lib/Helper.class`.
- Lệnh sau cho `javac` thấy `lib.Helper` qua `-cp lib-classes` và tạo `app-classes/app/MissingDemo.class`.
- `-d` chỉ định output root; package paths được tạo tương ứng.
- **Không thêm cả hai source files vào một lần `javac` rồi cùng output**, vì thí nghiệm cần **tách được output dependency khỏi output ứng dụng**.

### 4.5 Trường hợp đối chứng — runtime classpath đúng

**Windows** dùng `;` làm path separator:

```bash
java -cp "app-classes;lib-classes" app.MissingDemo
```

**Linux/macOS** dùng `:`:

```bash
java -cp "app-classes:lib-classes" app.MissingDemo
```

Stdout đã được biên dịch/chạy kiểm chứng:

```text
app started
Helper available
```

`app.MissingDemo` và `lib.Helper` đều được loader thấy. Không có lỗi linkage.

### 4.6 Trường hợp lỗi — chỉ bỏ dependency ở runtime

**Không recompile**. Chạy:

```bash
java -cp app-classes app.MissingDemo
```

Stdout đã được kiểm chứng:

```text
app started
error=NoClassDefFoundError
missing=lib/Helper
cause=ClassNotFoundException
```

Giải thích chính xác:

1. JVM tìm được `app.MissingDemo` từ `app-classes`; do đó chương trình in `app started`.
2. Khi thực thi lời gọi `Helper.message()`, bytecode cần resolve class `lib.Helper`.
3. Runtime classpath **không có `lib-classes`**, nên loader không tìm được định nghĩa class đó.
4. Bytecode use nhận `NoClassDefFoundError`; cause trong thí nghiệm là `ClassNotFoundException`.
5. Đây là **thiếu dependency trong runtime classpath**, không phải static initializer của `Helper` bị lỗi (vì `Helper` chưa được tìm thấy).

Tên trong message dùng dấu gạch chéo `lib/Helper` theo VM internal representation, trong khi Java binary name viết `lib.Helper`. Không lấy dấu slash trong lỗi làm bằng chứng rằng source code phải import bằng slash.

### 4.7 Đối chiếu bytecode của ứng dụng

```bash
javap -classpath app-classes -c -verbose app.MissingDemo
```

Trong constant pool/bytecode của chương trình đã chạy, phần đáng chú ý:

```text
Methodref ... // lib/Helper.message:()Ljava/lang/String;
Class ...     // lib/Helper
...
invokestatic ... // Method lib/Helper.message:()Ljava/lang/String;
```

`javap` chứng minh **caller đã compile against `lib.Helper`**, còn runtime thiếu định nghĩa mà symbolic reference ấy cần. Các chỉ số constant pool (`#21`, `#22`, ...) có thể khác khi source/compiler thay đổi; phần quan trọng là **owner, method name và descriptor**.

**Kết luận Lab A:** class có tồn tại **lúc biên dịch**, nhưng vắng mặt khỏi nguồn loader thấy **lúc chạy**. Thêm dependency đúng vào runtime classpath sẽ làm cùng binary client chạy được mà không cần sửa code.

## 5. Lab B — Static initializer thất bại rồi NoClassDefFoundError xuất hiện ở lần sau

Lab này cố ý **không xóa class file nào**. Class `demo.Broken` luôn có trên runtime classpath. Ta làm `static { ... }` ném một exception tại lần khởi tạo đầu tiên.

### 5.1 Cấu trúc

```text
ncdfe-lab/
├── init-src/
│   └── demo/
│       ├── Broken.java
│       └── InitDemo.java
└── init-classes/             (output riêng)
```

Tạo thêm `init-src/demo` và `init-classes` bên cạnh các thư mục của Lab A.

### 5.2 Class Broken có static initializer chủ động lỗi

File `init-src/demo/Broken.java`:

```java
package demo;

public final class Broken {
    public static int number;

    static {
        System.out.println("<clinit> entered");

        if (Boolean.parseBoolean(
                System.getProperty("lab.fail", "true"))) {
            throw new IllegalStateException("boom");
        }

        number = 42;
    }
}
```

Giải thích:

- JVM thực hiện static initialization khi class được active-use lần đầu theo JLS.
- `System.getProperty("lab.fail", "true")` mặc định bằng `"true"` nếu không truyền JVM property.
- `Boolean.parseBoolean(...)` được tính lúc chạy, nên compiler chấp nhận static initializer có nhánh có thể ném exception (không phải một khối bị chứng minh luôn thất bại ở compile-time).
- `IllegalStateException` **không phải subclass của `Error`**, nên JLS yêu cầu JVM bọc nó thành `ExceptionInInitializerError` khi quá trình initialization thất bại.
- Nếu property là `false`, static initializer đặt `number = 42` và class khởi tạo thành công.
- `number` là non-final field, nên đọc nó qua `Broken.number` thật sự kích hoạt class initialization nếu chưa initialized.

### 5.3 Consumer cố active-use hai lần trong cùng JVM

File `init-src/demo/InitDemo.java`:

```java
package demo;

public final class InitDemo {
    public static void main(String[] args) {
        System.out.println("loaded without init="
                + Broken.class.getName());

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                System.out.println(
                        "attempt " + attempt + " value=" + Broken.number);
            } catch (LinkageError error) {
                System.out.println(
                        "attempt " + attempt + " error="
                        + error.getClass().getSimpleName());

                Throwable cause = error.getCause();
                System.out.println("cause="
                        + (cause == null
                           ? "<none>"
                           : cause.getClass().getSimpleName()));
            }
        }
    }
}
```

**Điểm mấu chốt:** `Broken.class` dùng để lấy class literal; nó **không tự bắt buộc chạy `<clinit>`**. Nhưng đọc `Broken.number` — một static field không phải constant variable — là **active use** và yêu cầu class được initialize.

Lệnh `catch (LinkageError)` chỉ dành cho lab để in rõ lần đầu/lần sau; không dùng cách “bắt lỗi rồi chạy tiếp” làm giải pháp phục hồi class trong production.

### 5.4 Biên dịch

```bash
javac --release 17 -d init-classes init-src/demo/Broken.java init-src/demo/InitDemo.java
```

Kết quả có:

```text
init-classes/demo/Broken.class
init-classes/demo/InitDemo.class
```

**Cả hai class files hiện diện** trong runtime path. Không xóa/thay `Broken.class` ở bất kỳ bước nào của Lab B.

### 5.5 Chạy mặc định — class khởi tạo thất bại

```bash
java -cp init-classes demo.InitDemo
```

Stdout **đã chạy kiểm chứng**:

```text
loaded without init=demo.Broken
<clinit> entered
attempt 1 error=ExceptionInInitializerError
cause=IllegalStateException
attempt 2 error=NoClassDefFoundError
cause=ExceptionInInitializerError
```

Giải thích lần lượt:

1. `loaded without init=demo.Broken`: JVM có `Class<?>` cho `Broken`, nhưng chưa chạy static initializer; câu lệnh class literal không active-use class theo cách bắt buộc initialization.
2. `<clinit> entered`: khi đọc `Broken.number` lần đầu, JVM thực hiện static initialization.
3. `attempt 1 error=ExceptionInInitializerError`: static block ném `IllegalStateException("boom")`; JVM bọc lỗi khởi tạo vào `ExceptionInInitializerError`.
4. `cause=IllegalStateException`: nguyên nhân gốc mà người phát triển cần sửa nằm trong static initializer, không phải vấn đề classpath.
5. `attempt 2 error=NoClassDefFoundError`: JVM ghi nhận `demo.Broken` đã ở trạng thái **erroneous** sau lần init thất bại, nên không chạy lại `<clinit>` của cùng class.
6. `cause=ExceptionInInitializerError`: JVM của thí nghiệm còn giữ chuỗi nguyên nhân liên quan tới lần init trước. **Không giả định mọi JVM luôn đóng gói cause theo đúng cùng format**; hãy tìm lỗi đầu tiên trong log.

Không có dòng `<clinit> entered` lần thứ hai. Đó là bằng chứng thực tế rằng **static initializer không được retry tự động**.

### 5.6 Chạy một tiến trình mới với property sửa đúng

Chạy **JVM mới**:

```bash
java -Dlab.fail=false -cp init-classes demo.InitDemo
```

Với PowerShell trên Windows, nếu cần quote để shell không diễn giải dấu gạch ngang/syntax, có thể dùng:

```powershell
java '-Dlab.fail=false' -cp init-classes demo.InitDemo
```

Stdout đã xác minh:

```text
loaded without init=demo.Broken
<clinit> entered
attempt 1 value=42
attempt 2 value=42
```

Lúc này class initialization hoàn thành bình thường. Trong cùng tiến trình, lần truy cập thứ hai không chạy `<clinit>` nữa vì class đã initialized.

**Điều quan trọng:** Đây là **tiến trình mới**. Việc sửa property **sau khi class đã erroneous trong cùng runtime class definition** không khiến `<clinit>` tự chạy lại. Để phục hồi class definition lỗi trong một ứng dụng có custom loader, thường cần sửa nguyên nhân và tạo **định nghĩa mới trong class loader mới** theo lifecycle plugin phù hợp, hoặc restart JVM; không thể “reset erroneous flag” bằng Java API công khai.

## 6. So sánh Lab A và Lab B bằng bằng chứng

| Câu hỏi | Lab A: Thiếu class runtime | Lab B: Static initialization thất bại |
| --- | --- | --- |
| Class cần dùng tồn tại trong runtime output? | **Không**: `lib.Helper` bị loại khỏi runtime classpath | **Có**: `demo.Broken.class` ở `init-classes` |
| Caller đã compile được? | Có, compile với `lib-classes` | Có, cả `Broken` và `InitDemo` compile cùng nhau |
| Điểm làm lỗi phát sinh | Link class `lib.Helper` khi gọi `Helper.message()` | Active use `Broken.number` lần đầu |
| Lỗi quan sát đầu tiên | `NoClassDefFoundError` | `ExceptionInInitializerError` |
| Cause trong thí nghiệm | `ClassNotFoundException` | `IllegalStateException` |
| Lỗi khi thử lại trong cùng JVM | Không thể dựa vào một kiểu message chung; còn phụ thuộc loader/linkage state | `NoClassDefFoundError` vì class đã ở trạng thái erroneous |
| Hướng sửa | Đưa đúng class/JAR vào **runtime classpath/loader** | Sửa lỗi static initializer/config và tạo quá trình init mới hợp lệ |
| Cần recompile caller không? | Không nếu dependency binary tương thích và được deploy đúng | Không nếu code nguyên nhân và config đã đúng, nhưng class erroneous cũ không tự hồi phục |
| Classloader quan trọng thế nào? | Loader không thấy `lib.Helper` | Identity/trạng thái erroneous gắn với một class definition được loader định nghĩa |
| Có phải `ClassNotFoundException` trực tiếp từ API reflection? | Không: caller dùng bytecode method reference, nên hiện `NoClassDefFoundError` | Không: class đã được tìm thấy và bắt đầu initialize |

Vì **cùng một lỗi cuối `NoClassDefFoundError` có nhiều nguyên nhân**, chẩn đoán phải bắt đầu từ **đường diễn biến trước đó và cause chain**.

## 7. Theo dõi cause chain thay vì chỉ tên lỗi

Trong production, stack trace đầy đủ có thể cho thấy:

**Thiếu dependency:**

```text
java.lang.NoClassDefFoundError: lib/Helper
    ...
Caused by: java.lang.ClassNotFoundException: lib.Helper
    ...
```

**Static initializer lỗi lần đầu:**

```text
java.lang.ExceptionInInitializerError
    ...
Caused by: java.lang.IllegalStateException: boom
    at demo.Broken.<clinit>(Broken.java:...)
    ...
```

**Sử dụng lại class đã erroneous:**

```text
java.lang.NoClassDefFoundError: Could not initialize class demo.Broken
    ...
```

Đây là **các mẫu rút gọn, không phải stdout cố định**; stack trace line numbers và cách trình bày causes khác nhau theo JVM/build.

Cách đọc:

1. **Loại throwable đầu tiên ở log lịch sử**: trước `NoClassDefFoundError` có `ExceptionInInitializerError` không?
2. **Message**: là binary name bị thiếu `lib/Helper` hay câu “Could not initialize class ...”?
3. **Cause**: `ClassNotFoundException`, `IllegalStateException`, `NullPointerException` hay một lỗi khác?
4. **Frame `<clinit>`**: nếu có, đối chiếu static field initializer/static block tại class tương ứng.
5. **Loader và CodeSource**: class thực tế do loader nào định nghĩa, lấy từ JAR nào?
6. **Thời điểm**: startup, request đầu tiên, task plugin hay redeploy?

**Đừng cắt mất dòng `Caused by` khi chuyển log cho người điều tra.** Một `NoClassDefFoundError` muộn thường chỉ là hậu quả, không phải nguyên nhân đầu.

## 8. Thao tác nào thực sự kích hoạt class initialization?

JLS `12.4.1` mô tả những hoạt động yêu cầu init class/interface, ví dụ:

| Thao tác | Có yêu cầu initialization của class khi cần? | Ghi chú |
| --- | --- | --- |
| `new Broken()` | Có | Class được initialize trước khi instance construction hoàn tất |
| Đọc static field **không phải constant variable** (`Broken.number`) | Có | Demo sử dụng cách này |
| Ghi static field (`Broken.number = 5`) | Có | Nếu thao tác thực sự reference field của class |
| Gọi static method (`Broken.run()`) | Có | Active use |
| `Class.forName("demo.Broken")` | Có | Overload một tham số yêu cầu init |
| `Class.forName("demo.Broken", true, loader)` | Có | Loader chỉ định tường minh |
| `Class.forName("demo.Broken", false, loader)` | Không yêu cầu init ngay | Có thể load/link, chưa chạy static initializer |
| `loader.loadClass("demo.Broken")` | Không yêu cầu init ngay | Loader sẽ tìm `Class<?>`, không mặc nhiên chạy `<clinit>` |
| `Broken.class` | Không tự yêu cầu init | Vì vậy dòng đầu Lab B in trước static block |
| Đọc constant variable `static final int LIMIT = 5` | Thường **không** gây init class khai báo constant | Compiler có thể inline compile-time constant |
| Reflective operations cụ thể kích hoạt initialization | Tùy API | Đọc JavaDoc của method, đừng suy toàn bộ reflection đều giống nhau |

Một ví dụ kiểm tra laziness:

```java
ClassLoader loader = Thread.currentThread().getContextClassLoader();

Class<?> type = Class.forName("demo.Broken", false, loader);
System.out.println("loaded class=" + type.getName());

// Tới đây chưa yêu cầu Broken.<clinit> phải chạy.
// Nếu gọi với initialize=true từ đây, có thể ném
// ExceptionInInitializerError khi property lab.fail=true.
```

Đây là **đoạn minh họa trong method xử lý `ClassNotFoundException`**. Không dùng nó như một file độc lập; các labs ở trên đã có file đầy đủ và stdout kiểm chứng.

### Không nhầm load, resolve và initialize

- `loadClass`: tìm class, có thể delegate parent; kết quả `Class<?>` không có nghĩa initializer đã chạy.
- `resolveClass`: dùng cho linking theo ClassLoader API; không đồng nghĩa static initialization.
- `initialize=true` trong `Class.forName`: yêu cầu class initialization khi cần.
- `new`: yêu cầu init class trước khi khởi tạo instance phù hợp.

Một class có thể **load được** nhưng không **initialize được**. Đây chính là Lab B.

## 9. Chính xác JVM làm gì khi static initializer ném lỗi?

Theo JLS 17 `12.4.2, với một class `C`:

1. JVM đồng bộ quanh **initialization lock** liên quan đến `C`.
2. Nếu `C` đã initialized, return thành công.
3. Nếu `C` đang initialized bởi thread khác, thread hiện tại có thể phải chờ.
4. Nếu `C` đã ở trạng thái **erroneous**, JVM **ném `NoClassDefFoundError`** thay vì cố init lại.
5. Nếu chưa, JVM chuẩn bị thực hiện superclass/superinterfaces initialization cần thiết.
6. JVM chạy static fields/static blocks của `C` theo thứ tự văn bản tương ứng.
7. Nếu hoàn thành bình thường, đánh dấu class **fully initialized**.
8. Nếu initializer kết thúc bất thường, đánh dấu class **erroneous**, đánh thức threads chờ và phát lỗi phù hợp.

### Tại sao lần đầu có ExceptionInInitializerError?

Khi initializer ném một `Throwable E`:

- Nếu `E` **không** phải `Error` hoặc subclass của `Error`, JVM bọc E trong một `ExceptionInInitializerError` khi đủ điều kiện theo quy trình `12.4.2`.
- Nếu `E` **đã là Error**, JVM có thể phát chính Error đó ở lần thất bại đầu tiên, **không bắt buộc bọc thành `ExceptionInInitializerError`**.
- Khi class đã erroneous, những lần yêu cầu init tiếp theo dẫn tới `NoClassDefFoundError`.

Vì vậy câu “lần đầu lỗi static initializer **luôn** là `ExceptionInInitializerError`” là **sai**. Demo dùng `IllegalStateException` để có trường hợp rõ, dễ học.

### Nếu nhiều threads cùng active-use class thì sao?

Initialization được JVM điều phối và đồng bộ theo class; không phải hai threads có quyền đồng thời chạy cùng một `<clinit>` độc lập. Nếu một thread đang initialize class, thread khác có thể chờ. Nếu init thất bại, các threads tới sau có thể thấy class erroneous và `NoClassDefFoundError`.

**Không viết ứng dụng retry `Broken.number` trong loop nhiều lần với hy vọng class tự khởi tạo lại.** Nếu trạng thái đã erroneous, retry cùng runtime class không khắc phục nguyên nhân.

## 10. Constructor exception khác class initializer exception

Ví dụ một constructor ném lỗi:

```java
public final class FailingObject {
    public FailingObject() {
        throw new IllegalStateException("constructor failed");
    }
}
```

Đây là class Java có thể biên dịch nhưng **không chứa method `main`**. Nếu class initialization của `FailingObject` bình thường, việc constructor của **một object** ném `IllegalStateException` **không tự đánh dấu cả class erroneous**.

Cần phân biệt:

- `static { throw ... }` / static field initializer hỏng → lỗi **class initialization** (có thể đánh dấu erroneous).
- Instance field initializer hay constructor hỏng → lỗi **instance construction**; không mặc nhiên làm class erroneous.
- Superclass static initializer hỏng → class đang khởi tạo có thể bị ảnh hưởng theo quy trình init hierarchy.
- Method bình thường ném exception → không tự làm class erroneous.

Không chỉ nhìn “stack trace xảy ra khi `new Broken()`” mà kết luận lỗi constructor. `new` có thể kích hoạt class initialization **trước** khi constructor chạy.

## 11. Tại sao đổi property giữa chừng không sửa được class erroneous?

Trong Lab B, `lab.fail` được đọc trong `<clinit>`. Giả sử trong cùng JVM ta chạy:

```java
try {
    int ignored = Broken.number;
} catch (LinkageError error) {
    System.setProperty("lab.fail", "false");
}

int value = Broken.number; // Vẫn có thể ném NoClassDefFoundError.
```

Đây là **đoạn minh họa trong class `demo` đã import/nhìn thấy `Broken`**, không phải file chạy độc lập. Property đã sửa nhưng **quá trình static initialization của cùng `Class<?>` không tự chạy lại**.

Các cách xử lý phù hợp:

- **Ứng dụng thường:** sửa cấu hình/dependency/initializer rồi khởi động lại JVM.
- **Plugin có loader riêng:** dừng plugin, cleanup tất cả listeners/threads/TCCL/instances, sửa artifact hoặc cấu hình, tạo **class loader mới** để có runtime class definition mới, rồi khởi tạo lại theo lifecycle đúng.
- **Không thể:** gọi `Class.forName` nhiều lần và mong một `Class<?>` erroneous tự reset.
- **Không nên:** cố gọi `System.gc()` hay `URLClassLoader.close()` rồi dùng lại class trong **cùng loader đã define**. Close không sửa class initialization state.

Tạo loader mới **không bảo đảm** thành công nếu nguyên nhân vẫn còn (ví dụ DB config, secret thiếu, native library lỗi); nó chỉ tạo cơ hội cho một định nghĩa class mới được initialize với trạng thái phù hợp.

## 12. Thực hành phân biệt ClassNotFoundException với NoClassDefFoundError

Một câu hỏi phổ biến: “Tại sao dùng `Class.forName` ra `ClassNotFoundException`, nhưng gọi method lại ra `NoClassDefFoundError`?”

Xét:

```java
Class.forName("lib.Helper");
```

Đây là **đoạn minh họa** trong method khai báo/handle `ClassNotFoundException`. Vì API được yêu cầu tìm class bằng **chuỗi tên**, nếu loader không thấy class, nó thường ném **checked `ClassNotFoundException`** trực tiếp.

Ngược lại:

```java
System.out.println(lib.Helper.message());
```

Đây là **đoạn minh họa**, giả định caller đã compile against `lib.Helper`. JVM resolve symbolic reference trong bytecode để thực hiện call; nếu class definition thiếu, VM có thể ném **`NoClassDefFoundError`**.

Hình dung:

```text
API lookup theo tên:
    Class.forName("lib.Helper")
          |
          +-- loader không tìm thấy
          |
          +-- ClassNotFoundException (checked)

Bytecode reference:
    invokestatic lib/Helper.message:()Ljava/lang/String;
          |
          +-- JVM cần class khi linking
          +-- không tìm được definition
          |
          +-- NoClassDefFoundError (LinkageError)
              [cause có thể là ClassNotFoundException]
```

**Cả hai vẫn có thể bắt nguồn từ classpath sai**, nhưng loại throwable phụ thuộc **đường gọi/API** và **điểm JVM đang tìm/resolve**. Đừng coi hai exception/error là synonym.

## 13. Classpath root: sửa đúng lỗi của Lab A

Trong Lab A, file đúng ở:

```text
lib-classes/
└── lib/
    └── Helper.class
```

Với binary name `lib.Helper`, **classpath root phải là thư mục `lib-classes`**, không phải `lib-classes/lib`. JVM dùng package path `lib/Helper.class` tương đối từ từng root.

**Sai:**

```bash
java -cp lib-classes/lib app.MissingDemo
```

Lệnh trên còn thiếu output của `app.MissingDemo`; ngay cả nếu thêm app root, `lib-classes/lib` vẫn **không phải root đúng** cho `lib.Helper`.

**Đúng với hai directories:**

```bash
# Linux/macOS
java -cp "app-classes:lib-classes" app.MissingDemo
```

```powershell
# Windows PowerShell
java -cp 'app-classes;lib-classes' app.MissingDemo
```

Nếu dependency nằm trong JAR, phải để root archive có entry `lib/Helper.class` và đưa JAR vào runtime classpath thích hợp:

```bash
jar --create --file helper.jar -C lib-classes .
```

Sau đó ví dụ trên Linux/macOS:

```bash
java -cp "app-classes:helper.jar" app.MissingDemo
```

Windows thay `:` bằng `;`.

**Mẹo:** thêm JAR/đường dẫn vào **compile classpath** không tự thêm vào **runtime classpath**. Tương tự, IDE thấy dependency không chứng minh script/Docker/server/plugin loader khi chạy sẽ nhìn thấy dependency ấy.

## 14. Khi class thực sự có mặt nhưng JVM vẫn báo NoClassDefFoundError

Ngoài static initialization failure, một số nguyên nhân khác cần kiểm tra:

| Tình huống | Cơ chế | Hướng chẩn đoán |
| --- | --- | --- |
| File `Helper.class` có trên đĩa nhưng đặt sai **classpath root** | Loader không tìm theo đúng binary name path | So package với output root |
| JAR có class nhưng không nằm trong runtime `-cp` | Compilation và deployment không đồng bộ | In runtime classpath và JAR list |
| Plugin class chỉ có trong loader con | Parent loader không tự tìm xuống child | Kiểm tra defining/initiating loader, TCCL nếu API dùng TCCL |
| JAR có trên module path nhưng module chưa resolved | Observable không đồng nghĩa resolved | Kiểm tra `--module-path`, `--add-modules` và JPMS dependency graph |
| Class phụ thuộc vào **một dependency khác** bị thiếu | Class đầu có thể load, đến linkage mới thiếu class phụ | Đọc **tên class trong message/cause** và stack trace |
| Class đã erroneous do lỗi static block | Subsequent active use ném NCDFE dù `.class` tồn tại | Tìm `ExceptionInInitializerError` / original initializer error trước đó |
| Đổi binary name/package nhưng còn file cũ | Bytecode caller yêu cầu tên khác | `javap`, kiểm tra binary name và build artifacts |
| JAR/class files cũ bị shadow bởi parent-loader version | JVM nạp API khác với artifact đang xem | `Class.getClassLoader()`, CodeSource, `-Xlog:class+load=info` |
| Code được biên dịch với API version không còn tương thích | Member reference hỏng | Có thể là `NoSuchMethodError` / `NoSuchFieldError`, không nhất thiết NCDFE |
| Class file hỏng hoặc version không hỗ trợ | Verification/format/version lỗi | `ClassFormatError` / `UnsupportedClassVersionError` có thể xuất hiện |

Không sửa bằng cách thêm hàng loạt JAR ngẫu nhiên. Cần xác định **tên class cần resolve** và **loader đang thực hiện lookup**.

## 15. Chẩn đoán bằng javap, CodeSource, class loading logs

### 15.1 Xem tham chiếu bytecode

Lab A:

```bash
javap -classpath app-classes -c -verbose app.MissingDemo
```

Tìm `lib/Helper` và `invokestatic`. Thao tác xác nhận caller binary **thật sự phụ thuộc** `lib.Helper`, dù source hiện tại có thể đã thay đổi.

### 15.2 Xác nhận runtime class có thể được loader nào tìm

Một helper minh họa:

```java
ClassLoader loader =
        Thread.currentThread().getContextClassLoader();

System.out.println("TCCL=" + loader);
System.out.println("resource=" +
        loader.getResource("lib/Helper.class"));
```

Đây là **đoạn minh họa trong method**, giả định TCCL khác null. Resource URL khác null **chỉ chứng minh loader có thể tìm resource đó**, không đủ để kết luận class sẽ verify/link/init thành công; module access và class loading policy có thể khác đường resource lookup.

Nếu class được load thành công, kiểm tra:

```java
Class<?> type = lib.Helper.class;
System.out.println("loader=" + type.getClassLoader());
System.out.println("module=" + type.getModule());

java.security.ProtectionDomain pd = type.getProtectionDomain();
java.security.CodeSource cs =
        (pd == null ? null : pd.getCodeSource());

System.out.println("source="
        + (cs == null ? "<unknown>" : cs.getLocation()));
```

Đây là **đoạn minh họa**, giả định code đã compile có `lib.Helper`. CodeSource có thể null; với loader riêng, source có thể khác runtime classpath mà host in ra. Không dùng đoạn này khi class chưa load được rồi bỏ qua lỗi gốc; khi đó log class loading/deployment paths hữu ích hơn.

### 15.3 HotSpot class load logs

```bash
java -Xlog:class+load=info -cp "app-classes:lib-classes" app.MissingDemo
```

Dòng trên dành Linux/macOS; trên Windows thay `:` bằng `;`. HotSpot có thể in nhiều sự kiện load, giúp xác định class nào được nạp và nguồn nếu thông tin log có. Nhưng log `class+load` không tự chỉ ra **giá trị bên trong static initializer** gây lỗi. Với Lab B, stack trace tới `demo.Broken.<clinit>` và cause `IllegalStateException` quan trọng hơn.

### 15.4 Đừng dùng mọi log sau lỗi làm nguyên nhân

Trong server, một lỗi init duy nhất lúc startup có thể khiến **hàng trăm requests về sau** log `NoClassDefFoundError: Could not initialize class ...`. Nếu chỉ đọc log ở request thứ 100, bạn thấy hậu quả. Hãy tìm **lần đầu** static initializer chạy và exception thật từ config, resource, network, null value hoặc service discovery.

Đôi khi lớp static initializer gọi tới lớp khác mà lớp ấy khởi tạo lỗi. Khi đó cause chain có nhiều class; cần lần theo tới đoạn `<clinit>` và exception ban đầu.

## 16. Lỗi static initialization thực tế đến từ đâu?

Trong thí nghiệm ta chủ động ném `IllegalStateException("boom")`. Trong ứng dụng thật, static initializer có thể hỏng bởi:

- `NullPointerException` khi đọc config chưa được thiết lập.
- `IllegalArgumentException` từ việc parse path/URL/charset/timezone không hợp lệ.
- `ExceptionInInitializerError` từ một dependency có static initialization bị lỗi trước.
- `SecurityException` hoặc `IllegalAccessError` khi thao tác bị runtime/module access hạn chế.
- `UnsatisfiedLinkError` lúc load native library trong static block.
- `ServiceConfigurationError` khi initializer dùng `ServiceLoader` với provider metadata hỏng.
- `NoClassDefFoundError` do dependency khác thiếu đúng lúc initializer gọi đến.
- `OutOfMemoryError` hoặc tài nguyên hệ thống không đủ.
- Thứ tự static initialization giữa nhiều classes dẫn tới trạng thái chưa sẵn sàng hoặc chu trình phụ thuộc phức tạp.

Vì vậy một class initialization failure **không nhất thiết bắt đầu bằng `IllegalStateException`**. Lab B chỉ chọn lỗi này để minh họa rõ quy tắc wrapping.

**Thiết kế tốt:** hạn chế I/O, network, database, native load hoặc service discovery có thể fail trong static initializer nếu chúng phụ thuộc trạng thái vận hành thay đổi. Ưu tiên một **explicit initialization method**, factory hoặc lifecycle `start()` trả lỗi có kiểm soát và hỗ trợ retry/rollback ở mức ứng dụng.

### Ví dụ anti-pattern

```java
public final class GlobalClient {
    static final Connection CONNECTION = openDatabaseOrThrow();

    private GlobalClient() { }

    static Connection connection() {
        return CONNECTION;
    }

    // openDatabaseOrThrow và Connection là thành phần minh họa.
}
```

Đây là **pseudocode**, không phải file độc lập: `Connection`/`openDatabaseOrThrow` chưa khai báo. Nếu cơ sở dữ liệu chưa sẵn sàng lúc class initialization, class có thể rơi vào trạng thái erroneous vĩnh viễn trong runtime class đó. Thay bằng explicit lifecycle/DI/resource management sẽ giúp thông báo lỗi và retry theo chính sách ứng dụng.

## 17. Kết quả khác nhau giữa class literal, static field và static constant

Một thí nghiệm phụ cần hiểu:

```java
package demo;

public class ConstantExample {
    public static final int LIMIT = 10;
    public static int dynamicValue = init();

    private static int init() {
        System.out.println("initializing ConstantExample");
        return 20;
    }
}
```

Đây là **class độc lập có thể biên dịch**, chưa có `main`.

Khi mã khách compile cùng API truy cập `ConstantExample.LIMIT`, compiler có thể **inline** số `10`. Việc chỉ đọc constant variable ấy **không buộc class init**. Nhưng đọc `dynamicValue` sẽ kích hoạt initialization và có thể in `initializing ConstantExample`.

Đừng sử dụng `static final int` compile-time constant làm trigger khi muốn kiểm tra `<clinit>`. Trong Lab B, ta dùng **`public static int number`**, đảm bảo thao tác đọc là active use.

Cũng không suy rằng tất cả `static final` đều là compile-time constant: `static final Object X = new Object()` không phải constant variable theo nghĩa JLS.

## 18. Quan hệ lỗi khởi tạo với classloader và hot reload

Theo JVMS, runtime class identity bao gồm **binary name và defining class loader**. Hai custom classloader khác nhau có thể tự define hai bản `demo.Broken` cùng tên.

```text
OldPluginLoader
    |
    +-- demo.Broken (class definition A)
           +-- initialization failed
           +-- state: ERRONEOUS

NewPluginLoader
    |
    +-- demo.Broken (class definition B)
           +-- independent class initialization state
           +-- may succeed after config fix
```

Đây là sơ đồ khái niệm, không phải API VM để trực tiếp đọc `ERRONEOUS`. Một new loader **chỉ tạo một cơ hội mới** nếu nó thật sự define class mới, thay vì delegate về bản class lỗi ở parent loader.

Trong plugin host:

1. **Shared API** ở parent loader để host/plugin thống nhất interface identity.
2. **Plugin implementation** được custom loader riêng định nghĩa.
3. Nếu implementation class erroneous, stop/cleanup plugin theo lifecycle.
4. Tránh host static caches, TCCL, threads, listeners giữ class/loader cũ khi undeploy.
5. Tạo loader mới trỏ tới artifact/config đã sửa và reinitialize plugin.
6. Kiểm tra new loader thực sự define `demo.Broken`; nếu parent đã define class cùng tên, delegation có thể vẫn trả definition cũ.
7. Không coi `URLClassLoader.close()` là “reset class initialization”; close chỉ quản lý URL search path/JAR handles.

**Không cố chữa lỗi initialization bằng reflection vào private fields hoặc `Unsafe` để ép class chạy `<clinit>` lại.** Không có hợp đồng Java SE công khai đảm bảo thao tác như vậy là an toàn/đúng semantics.

## 19. Module path và vấn đề “class có trên đĩa nhưng không load được”

Từ Java 9, code có thể nằm trên classpath (unnamed module) hoặc module path (named/automatic module). Một JAR có mặt trên `--module-path` chỉ khiến nó **observable**; module còn phải được **resolved** theo graph và class access còn chịu exports/opens/readability.

Ví dụ một ứng dụng classpath cố dùng class trong named module chưa resolved có thể cần `--add-modules module.name` hoặc quan hệ dependency thích hợp. Tuy nhiên **không lấy `--add-modules` làm giải pháp chung cho mọi NoClassDefFoundError**. Hãy xác định class được nạp bởi loader nào, module nào chứa class, module graph có resolved không, và root cause có thực sự do discovery hay là erroneous initialization.

Với Lab A/B trong bài, **chỉ dùng classpath** và không có `module-info.java`, nên dễ tách hai vấn đề gốc mà không trộn thêm JPMS resolution.

## 20. Các bài kiểm thử bổ sung nên thử

### Bài tập 1 — Xóa app class thay vì lib class

Giữ `lib-classes` nhưng chạy:

```bash
java -cp lib-classes app.MissingDemo
```

Launcher sẽ **không tìm được main class `app.MissingDemo`**. Thông báo launcher như “Could not find or load main class ...” khác với trường hợp main đã chạy rồi JVM thiếu class `lib.Helper`. Đừng khẳng định mọi kiểu main startup failure đều phải mang cùng throwable như Lab A.

### Bài tập 2 — Sửa runtime classpath của Lab A

Thêm lại `lib-classes` mà **không biên dịch lại client**. Kết quả đã kiểm chứng là `app started` rồi `Helper available`. Vì lỗi gốc là deployment path, không phải source API.

### Bài tập 3 — Đổi giá trị property của Lab B trước startup

Chạy:

```bash
java -Dlab.fail=false -cp init-classes demo.InitDemo
```

Dự đoán kết quả trước khi xem: static block chạy **một lần**, cả hai lần đọc static field đều in `42`. Đây là trường hợp đối chứng đã được chạy kiểm chứng.

### Bài tập 4 — Đổi property sau lỗi đầu trong cùng JVM

Sửa `InitDemo` để trong `catch` đầu tiên gọi `System.setProperty("lab.fail", "false")` rồi thử đọc `Broken.number` lần nữa. Theo JLS, vẫn không tự reinitialize class definition erroneous. Hãy so với khởi động JVM mới và giải thích sự khác nhau.

### Bài tập 5 — Thay IllegalStateException bằng Error

Trong `Broken`, thay lời ném trong static initializer bằng `throw new AssertionError("boom");` trong một nhánh runtime. Dự đoán:

- Lần đầu initializer kết thúc với một subclass của `Error`, có thể chính `AssertionError` được phát, không nhất thiết có `ExceptionInInitializerError`.
- Lần active use tiếp theo của class erroneous dẫn tới `NoClassDefFoundError`.

**Đây là bài tập dự đoán theo JLS**, không phải stdout của hai labs đã chạy. Khi viết code để quan sát, `catch (LinkageError)` của `InitDemo` không bắt `AssertionError` vì `AssertionError` là nhánh `Error` khác; nếu muốn trình diễn, dùng một harness có `catch (Throwable)` chỉ trong lab và tuyệt đối không che lỗi tương tự trong production.

### Bài tập 6 — Constructor lỗi không làm class erroneous

Tạo một class có constructor ném `IllegalArgumentException`. Hai lần gọi constructor đều có thể ném exception từ constructor mới; static initialization của class vẫn có thể đã thành công. So với Lab B để hiểu trạng thái per-class khác per-instance.

### Bài tập 7 — Xem lần lỗi đầu tiên trong log nhiều threads

Chạy nhiều threads cùng active-use một class có static block ngủ ngắn rồi ném lỗi. Tránh viết tests phụ thuộc thứ tự scheduling; điều cần quan sát là **một initialization attempt** được đồng bộ ở cấp class, và các callers về sau thấy erroneous state. Đọc JLS `12.4.2` để hiểu lock/wait rules.

## 21. Bảng chẩn đoán nhanh khi triển khai thực tế

| Triệu chứng | Câu hỏi trước tiên | Hướng sửa |
| --- | --- | --- |
| `NoClassDefFoundError: pkg/Type`, cause `ClassNotFoundException` | JAR/runtime root có đúng không? Loader thấy class đó không? | Sửa runtime classpath/module path/delegation |
| `NoClassDefFoundError: Could not initialize class pkg.Type` | Lần init đầu đã lỗi gì? Có `<clinit>` frame không? | Sửa static initializer/config, tạo init mới hợp lệ |
| `ExceptionInInitializerError` | Static field/block nào ném exception gốc? | Đọc `getCause()` và stack trace, sửa nguyên nhân |
| `ClassNotFoundException` từ `Class.forName` | Name/loader được truyền có đúng không? | Chọn loader phù hợp, xác minh binary name |
| `NoSuchMethodError` | Caller compile với API nào, runtime nạp API nào? | Binary compatibility/version alignment |
| `NoSuchFieldError` | Field name + descriptor còn không? | So `javap` caller/runtime |
| `UnsupportedClassVersionError` | Bytecode được compile cho JDK cao hơn runtime? | Chọn JDK/build target phù hợp |
| `IllegalAccessError` | Member tồn tại nhưng access/module exports có phù hợp? | Sửa access/module graph, không chỉ thêm classpath |
| `UnsatisfiedLinkError` trong `<clinit>` | Native library/ABI/path và loader ownership? | Sửa native dependency, xem lỗi đầu tiên |
| Lỗi chỉ xảy ra trong plugin | Plugin dùng parent/child loader nào? API shared version nào? | Kiểm tra loader identity, artifact, lifecycle |
| Lỗi mất sau restart nhưng tái diễn khi startup config sai | Static initializer chạy sớm trước config | Sắp xếp init/config hoặc chuyển sang explicit lifecycle |
| Rất nhiều NCDFE cùng class sau một lỗi EIIE | Class đã erroneous; requests sau cùng chịu hậu quả | Tìm exception đầu tiên, sửa root cause rồi restart/redeploy |

## 22. Checklist chẩn đoán theo thứ tự

1. **Ghi nguyên throwable và cause chain.** Không chỉ chép dòng `NoClassDefFoundError`.
2. **Xác định tên class được yêu cầu.** `lib/Helper`, `demo.Broken`, hay dependency khác ở cause?
3. **Hỏi main class đã chạy chưa.** Launcher không tìm main class khác với JVM thiếu dependency ở một code path.
4. **Kiểm tra class có trên runtime output/JAR không.** `jar --list` và filesystem path.
5. **So compile và runtime classpath.** `javac -cp` không tự truyền cho `java -cp`.
6. **Xác định classpath root từ package.** `lib.Helper` cần entry `lib/Helper.class` tương đối root.
7. **Xác định loader cụ thể.** App loader, plugin loader, TCCL của API nào, JPMS layer nào?
8. **Xác minh code source và duplicate JARs** khi có nhiều versions cùng class name.
9. **Nếu thấy `<clinit>`, đọc lần lỗi đầu tiên.** `NullPointerException`, config, native, SPI, missing dependency?
10. **Nếu class erroneous, không retry cùng class vô hạn.** Sửa root cause và dùng JVM/class definition mới phù hợp.
11. **Nếu là plugin, stop và cleanup trước reload.** Không giữ TCCL/tasks/cache trỏ tới loader cũ.
12. **Dùng `javap` để xem bytecode references**, nhất là khi source đang khác artifact.
13. **Chạy lại hai đối chứng.** Đầy đủ vs thiếu dependency; initializer fail vs initializer success.
14. **Thêm integration test dùng runtime artifact thật**, không chỉ dựa vào IDE/test compile.
15. **Quan sát logs từ startup**, không chỉ lỗi thứ N sau khi service đã chạy lâu.

## 23. Câu hỏi tự kiểm tra có đáp án

**1.** Tại sao Lab A compile được nhưng runtime vẫn lỗi?

**Đáp án:** `javac` thấy `lib.Helper` từ `lib-classes` lúc compile, nhưng `java -cp app-classes` không thấy dependency khi symbolic reference cần được resolve.

**2.** Trong Lab A, vì sao in `app started` trước khi báo lỗi?

**Đáp án:** JVM đã nạp/chạy được main class; lỗi chỉ xuất hiện khi code path thực sự gọi `Helper.message()` và cần `lib.Helper`.

**3.** Tại sao Lab B cho kết quả `ExceptionInInitializerError` rồi `NoClassDefFoundError`?

**Đáp án:** Lần đầu `Broken.number` kích hoạt static initializer ném `IllegalStateException` (được JVM bọc EIIE). Class chuyển sang erroneous; lần sau JLS quy định không retry, ném NCDFE.

**4.** Vì sao `Broken.class.getName()` không in `<clinit> entered`?

**Đáp án:** Class literal không tự yêu cầu class initialization. `Broken.number` là active use thực sự.

**5.** Có thể sửa `System.setProperty("lab.fail","false")` sau EIIE rồi đọc `Broken.number` lại thành công trong cùng definition không?

**Đáp án:** Không theo JLS cho class đã erroneous; phải sửa nguyên nhân và tạo runtime class definition mới/khởi động lại theo cách phù hợp.

**6.** `ClassNotFoundException` và `NoClassDefFoundError` khác cấp bậc như thế nào?

**Đáp án:** CNFE là checked `Exception`; NCDFE là `LinkageError`. Chúng xuất hiện ở API/đường resolution khác nhau dù có thể cùng do class missing.

**7.** Nếu static initializer ném trực tiếp `AssertionError`, lần đầu có luôn EIIE không?

**Đáp án:** Không. EIIE wrapping áp dụng khi thrown E không phải Error/subclass; `AssertionError` có thể được phát trực tiếp.

**8.** Nếu chỉ constructor ném exception, class có tự erroneous không?

**Đáp án:** Không mặc nhiên. Constructor error là lỗi instance construction; class initialization có thể đã hoàn thành.

**9.** `NoClassDefFoundError: Could not initialize class ...` có nghĩa JVM không đọc được file class không?

**Đáp án:** Không nhất thiết; thường class đã được tìm/nạp nhưng init thất bại trước đó. Cần tìm lỗi `<clinit>` đầu.

**10.** Vì sao đổi plugin JAR trong cùng class loader có thể không sửa lỗi initialization?

**Đáp án:** Runtime class definition đã được định nghĩa và đánh dấu erroneous; loader không tự định nghĩa lại class cùng tên. Cần deployment/loader mới cùng cleanup và root-cause fix.

**11.** Vì sao không nên đặt network connection setup trong static initializer?

**Đáp án:** Lỗi tạm thời lúc startup có thể biến class thành erroneous, không có retry tự động như một service lifecycle; quản lý explicit init/close dễ kiểm soát hơn.

**12.** Nếu source hiện tại không còn import `lib.Helper` nhưng bytecode deployed vẫn tham chiếu, cần xem gì?

**Đáp án:** `javap -c -verbose` trên **caller.class deployed** và build artifacts; source IDE không phải bằng chứng bytecode đang chạy.

## Kết luận

**`NoClassDefFoundError` không đồng nghĩa duy nhất “thiếu `.class` trên đĩa”.** Nó có thể xuất hiện khi JVM không tìm được class cần link (**Lab A**), hoặc khi JVM yêu cầu initialize lại một class đã thất bại và đang ở trạng thái **erroneous** (**Lab B**).

Cách xử lý đúng phải dựa trên **lần lỗi đầu + cause + bước JVM đang thực hiện**:

- **Thiếu class lúc chạy:** sửa runtime dependency/classpath/module/loader visibility.
- **Static initializer thất bại:** sửa root cause trong `<clinit>`/config, rồi dùng lần khởi tạo hợp lệ với JVM/class definition mới; không retry cùng class erroneous vô hạn.
- **Lỗi API version/access khác:** đối chiếu đúng throwable, bytecode và defining loader trước khi sửa.

Khi thực hành, hãy giữ **hai đối chứng chạy được**: một runtime có/không có dependency, và một JVM có/không có property gây initializer failure. Kết hợp chúng với JLS `12.4.2` sẽ giúp đọc chính xác những lỗi `NoClassDefFoundError` khó nhất trong server và plugin systems.

**Tài liệu chính thức Java SE 17:**

- [`NoClassDefFoundError` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoClassDefFoundError.html) — class definition không còn được tìm thấy khi JVM/loader cần.
- [`ClassNotFoundException` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassNotFoundException.html) — checked exception khi tìm class bằng tên.
- [`ExceptionInInitializerError` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ExceptionInInitializerError.html) — lỗi xảy ra trong static initializer.
- [JLS 17 §12.4.1 — When Initialization Occurs](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1) — các trường hợp active use.
- [JLS 17 §12.4.2 — Detailed Initialization Procedure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2) — trạng thái initialized/erroneous, EIIE wrapping và subsequent NCDFE.
- [JVMS 17 §5 — Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html) — symbolic resolution và class loading.
- [`ClassLoader` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html) — `loadClass` và delegation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-exercises-009">Thực hiện reload plugin bằng loader mới</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hiện reload plugin bằng ClassLoader mới trong Java 17

## 1. Mục tiêu thực hành

Trong một hệ thống Java hỗ trợ plugin, yêu cầu “reload plugin” thường có nghĩa:

1. Ứng dụng host đang gọi implementation phiên bản **v1**.
2. Bản **v2** được phát hành với cùng binary name (ví dụ `plugin.EchoPlugin`) nhưng bytecode/hành vi mới.
3. Host **chuẩn bị một class loader mới** và tạo instance của v2.
4. Host chuyển những lời gọi mới sang instance v2 một cách có kiểm soát.
5. Những lời gọi còn đang chạy trên v1 được hoàn tất hoặc hủy theo chính sách, sau đó giải phóng resources và references của **loader cũ**.
6. Nếu chuẩn bị v2 thất bại, v1 vẫn phục vụ thay vì bị dỡ sớm.

**Điều không nên làm:** ghi đè file `.class`/JAR bên dưới loader cũ rồi kỳ vọng `loadClass` sẽ thay định nghĩa runtime đã tồn tại. Trong JVM, một runtime class được nhận diện bởi **binary name + defining class loader**. Loader đã định nghĩa `plugin.EchoPlugin` không biến class đó thành v2 chỉ vì file trên đĩa đổi nội dung.

Bài này xây một ví dụ **Java 17 có thể biên dịch và chạy**: một shared `api.Plugin`, hai implementation v1/v2 cùng binary name, mỗi version nằm ở một output directory riêng, `URLClassLoader` mới cho mỗi generation và một `Manager` dùng `ReentrantReadWriteLock` để tránh race giữa lời gọi và lúc thay plugin.

Kết quả thực hành sẽ chứng minh:

- Hai version cùng thực hiện chung interface mà host biết.
- Sau reload, v2 chạy behavior mới và static field bắt đầu từ trạng thái mới.
- Class v1 và v2 có cùng binary name nhưng khác `Class<?>` và khác defining loader.
- Nếu chuẩn bị candidate từ đường dẫn thiếu thất bại, v1 vẫn là plugin active.
- Loader của v1 được đóng sau khi manager chuyển sang v2; **đóng loader không chứng minh lớp đã được JVM unload**.

## 2. Reload phải tạo một định nghĩa runtime mới

`loader.loadClass("plugin.EchoPlugin")` nhiều lần trên **cùng loader** thường nhận cùng `Class<?>` đã ghi nhận. Ghi đè JAR hoặc thay bytes ở nguồn không viết đè class đã định nghĩa; cố gọi `defineClass` lại cùng binary name trong loader cũ có thể gây `LinkageError`.

Bài lab vì thế tạo hai **loader instances**:

```text
Host/App Loader
  └── api.Plugin (shared API)
       ├── L1 -> plugin.EchoPlugin v1
       └── L2 -> plugin.EchoPlugin v2

(plugin.EchoPlugin, L1) != (plugin.EchoPlugin, L2)
api.Plugin luôn do parent định nghĩa
```

| Cách thay đổi | Có tạo class identity mới? |
| --- | --- |
| Gọi method/loadClass lại qua L1 | Không |
| Sửa file mà L1 đã đọc | Không thay runtime class cũ; dễ trộn resource/dependency |
| Tạo L2 và để L2 thực sự định nghĩa v2 | Có, thuộc generation khác |
| Instrumentation/JVMTI hot swap | Cơ chế riêng với giới hạn, không phải `defineClass` lần hai |
| Restart JVM | Runtime mới, đổi lại chi phí triển khai khác |

**Hai generations cùng tồn tại trong một khoảng thời gian là hợp lệ.** Trọng tâm không phải nạp được v2, mà là bảo vệ lời gọi đang chạy, chỉ publish v2 sau prepare và retire v1 có kiểm soát.

## 3. Kiến trúc reload có kiểm soát

```text
         Requests từ host
                |
                v
          Plugin Manager
          active = v1
                |
                v
          Plugin v1 / L1
                |
                +------------------+
                                   |
                         Chuẩn bị v2 / L2
                         (chưa publish)
                                   |
                 +-----------------+----------------+
                 |                                  |
          Prepare thất bại                    Prepare thành công
                 |                                  |
          Active vẫn v1                 Khóa write / chờ calls v1
                 |                                  |
          Không đóng v1                Active <- v2 / L2
                                                    |
                                           Close plugin v1 / L1
                                                    |
                                           New calls -> v2
```

Hai bước phải phân biệt:

- **Prepare:** dựng loader mới, load/validate implementation, tạo instance candidate; nếu thất bại thì chỉ dispose candidate, không động vào plugin active.
- **Activate/replace:** khi candidate đã sẵn sàng, chặn các lời gọi mới đủ ngắn để chuyển active reference, chờ lời gọi cũ kết thúc và close old resources theo policy.

Trong ví dụ, `ReentrantReadWriteLock` được dùng như một **cổng đồng bộ**:

- Mỗi `invoke` nắm **read lock** trong suốt lời gọi method của plugin.
- `replace` và `close` nắm **write lock**. Write lock chỉ được lấy khi các read lock hiện tại đã được thả.
- Vì vậy, khi thay v1 bằng v2, không có lời gọi v1 đang thực thi **thông qua manager này**.
- Sau swap, những lời gọi lấy read lock mới sẽ thấy v2.

**Giới hạn:** Lock chỉ quản lý các lời gọi qua manager. Nếu plugin tự tạo background thread, timer hoặc callback độc lập, lock này **không tự dừng** chúng. Trong production cần lifecycle hooks, quiesce/drain tasks và cleanup tương ứng.

## 4. Cấu trúc project

Tạo các file:

```text
src/
  api/
    api/
      Plugin.java

  v1/
    plugin/
      EchoPlugin.java

  v2/
    plugin/
      EchoPlugin.java

  host/
    host/
      ReloadPluginDemo.java

out/
  api/
    api/
      Plugin.class
  v1/
    plugin/
      EchoPlugin.class
  v2/
    plugin/
      EchoPlugin.class
  host/
    host/
      ReloadPluginDemo.class
      ...
```

`out` là **kết quả build**, không cần tạo thủ công từng file `.class`. Hai source files `src/v1/plugin/EchoPlugin.java` và `src/v2/plugin/EchoPlugin.java` có cùng declaration `package plugin; public final class EchoPlugin`; điều này hợp lệ vì chúng được biên dịch ra **hai directories riêng**.

**Bất biến quan trọng:** `api.Plugin` chỉ có một bản runtime ở `out/api` do loader chung của host định nghĩa. Không đưa `out/v1` và `out/v2` vào application classpath trong lúc chạy host.

## 5. Shared API — hợp đồng ổn định giữa host và mọi phiên bản

File `src/api/api/Plugin.java`:

```java
package api;

public interface Plugin extends AutoCloseable {
    String execute(String input);

    @Override
    void close();
}
```

Giải thích:

- `execute` là entry point mà host gọi, trả `String` từ Java core, không làm rò kiểu private của plugin qua chữ ký.
- `AutoCloseable` biểu diễn lifecycle; `Plugin.close()` override và **không khai báo checked exception** trong interface shared này.
- `URLClassLoader.close()` vẫn có thể ném `IOException`; đó là việc host phải xử lý riêng.
- Đây là **contract**; plugin v1/v2 đều compile against `out/api` và ủy quyền `api.Plugin` về parent ở runtime.

Một shared API thực tế có thể khai `start(PluginContext)`, `quiesce()`, `stop()`, health checks, DTO/request/response và domain exception. Những kiểu đó cũng phải được parent/shared loader định nghĩa thống nhất nếu dùng qua boundary.

**Không đổi interface tùy tiện giữa hai lần reload.** Một plugin được compile với API v2 nhưng host chỉ có API v1 có thể gặp `NoSuchMethodError`/`AbstractMethodError`/lỗi binary compatibility ngay cả khi class loader khác nhau đã hoạt động đúng.

## 6. Plugin v1: behavior và static state của thế hệ đầu

File `src/v1/plugin/EchoPlugin.java`:

```java
package plugin;

import api.Plugin;

public final class EchoPlugin implements Plugin {
    private static int calls;

    @Override
    public String execute(String input) {
        return "v1:" + input + ":" + (++calls);
    }

    @Override
    public void close() {
        System.out.println("CLOSE=v1");
    }
}
```

`calls` là **static field** của `plugin.EchoPlugin` do defining loader L1 định nghĩa. Trong demo tuần tự, ba lời gọi v1 sẽ cho `:1`, `:2` và `:3`.

**Cảnh báo production:** `++calls` không thread-safe. Chúng ta chỉ dùng demo để quan sát static class state sau reload; với plugin phục vụ đồng thời, cân nhắc `AtomicInteger`/synchronization và xác định trạng thái nào cần persist/migrate.

`close()` in một marker; trong plugin thực, nó thường giải phóng stream, connection, thread/executor, listener, scheduler và resources. Chỉ in marker **không làm class unload**.

## 7. Plugin v2: cùng tên lớp, triển khai khác

File `src/v2/plugin/EchoPlugin.java`:

```java
package plugin;

import api.Plugin;

public final class EchoPlugin implements Plugin {
    private static int calls;

    @Override
    public String execute(String input) {
        return "v2:" + input + ":" + (++calls);
    }

    @Override
    public void close() {
        System.out.println("CLOSE=v2");
    }
}
```

So với v1:

- **Binary name giống:** `plugin.EchoPlugin`.
- **Shared interface giống:** `api.Plugin` của host.
- **Hành vi khác:** prefix `v2:`.
- **Static state khác:** `calls` của class v2 độc lập với static field của class v1 do defining loaders khác nhau.

Nếu host thấy `v2:hello:1` sau reload, đó là bằng chứng thực tế rằng một generation runtime mới đang xử lý lời gọi (kết hợp với kiểm tra `Class<?>` identity ở phần dưới).

## 8. Host Java 17: tạo loader mới và chuyển generation

File `src/host/host/ReloadPluginDemo.java`:

```java
package host;

import api.Plugin;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class ReloadPluginDemo {
    static final class Generation implements AutoCloseable {
        final URLClassLoader loader;
        final Plugin plugin;
        final Class<?> type;

        Generation(URLClassLoader loader, Plugin plugin, Class<?> type) {
            this.loader = loader;
            this.plugin = plugin;
            this.type = type;
        }

        @Override
        public void close() throws IOException {
            try (loader) {
                plugin.close();
            }
        }
    }

    static Generation prepare(Path pluginDirectory) throws Exception {
        URL source = pluginDirectory.toUri().toURL();
        URLClassLoader loader = new URLClassLoader(
                "generation-" + pluginDirectory.getFileName(),
                new URL[]{source},
                Plugin.class.getClassLoader());

        try {
            Class<?> type = Class.forName(
                    "plugin.EchoPlugin", true, loader);

            Plugin instance =
                    (Plugin) type.getConstructor().newInstance();

            return new Generation(loader, instance, type);
        } catch (Exception | LinkageError failure) {
            try {
                loader.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    static final class Manager implements AutoCloseable {
        private final ReentrantReadWriteLock gate =
                new ReentrantReadWriteLock(true);

        private Generation current;

        String invoke(String message) {
            gate.readLock().lock();
            try {
                if (current == null) {
                    throw new IllegalStateException("No active plugin");
                }
                return current.plugin.execute(message);
            } finally {
                gate.readLock().unlock();
            }
        }

        Class<?> activeType() {
            gate.readLock().lock();
            try {
                if (current == null) {
                    throw new IllegalStateException("No active plugin");
                }
                return current.type;
            } finally {
                gate.readLock().unlock();
            }
        }

        void replace(Generation next) throws IOException {
            gate.writeLock().lock();
            try {
                Generation previous = current;
                current = next;
                if (previous != null) {
                    previous.close();
                }
            } finally {
                gate.writeLock().unlock();
            }
        }

        @Override
        public void close() throws IOException {
            gate.writeLock().lock();
            try {
                Generation previous = current;
                current = null;
                if (previous != null) {
                    previous.close();
                }
            } finally {
                gate.writeLock().unlock();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try (Manager manager = new Manager()) {
            manager.replace(prepare(Path.of("out/v1")));
            Class<?> oldType = manager.activeType();

            System.out.println("FIRST=" + manager.invoke("hello"));
            System.out.println("SECOND=" + manager.invoke("world"));

            try {
                Generation missing =
                        prepare(Path.of("out/missing"));
                manager.replace(missing);
            } catch (ClassNotFoundException expected) {
                System.out.println(
                        "PREPARE_FAILED=ClassNotFoundException");
            }

            System.out.println("STILL=" + manager.invoke("again"));

            Generation next = prepare(Path.of("out/v2"));
            manager.replace(next);

            Class<?> newType = manager.activeType();
            System.out.println("AFTER=" + manager.invoke("hello"));

            System.out.println("SAME_BINARY_NAME="
                    + oldType.getName().equals(newType.getName()));
            System.out.println("SAME_RUNTIME_CLASS="
                    + (oldType == newType));
            System.out.println("SHARED_API="
                    + (Plugin.class.isAssignableFrom(oldType)
                       && Plugin.class.isAssignableFrom(newType)));
            System.out.println("DIFFERENT_DEFINERS="
                    + (oldType.getClassLoader()
                       != newType.getClassLoader()));
        }
    }
}
```

### 8.1. Vai trò của từng thành phần

**`Generation`** giữ ba đối tượng thuộc cùng một plugin deployment:

- `URLClassLoader loader`: nguồn class riêng của thế hệ này.
- `Plugin plugin`: instance implementation được host dùng qua shared API.
- `Class<?> type`: runtime class token, dùng để chẩn đoán identity.

`Generation.close()` gọi `plugin.close()` rồi đóng loader nhờ try-with-resources. Nếu `plugin.close()` ném runtime exception, cấu trúc try-with-resources vẫn cố đóng URLClassLoader. Nếu close JAR/URL resources ném `IOException`, caller có thể nhận exception đó; việc xử lý thật cần logging và chính sách cleanup phù hợp.

**`prepare`** tạo **một URLClassLoader mới cho mỗi lần gọi**, với parent là `Plugin.class.getClassLoader()`. Trong ví dụ application classpath sạch, parent thấy `api.Plugin` nhưng không thấy `plugin.EchoPlugin`, nên child loader định nghĩa implementation riêng.

**`prepare` không “chỉ đọc metadata”**: `Class.forName(..., true, loader)` có thể kích hoạt static initialization, và constructor gọi trong `newInstance` thực thi plugin code. Vì vậy, chỉ đưa artifact **đã được tin cậy/kiểm tra** vào pha prepare; trong production không đánh đồng “chưa activate” với “chưa thực thi mã plugin”.

Nếu lookup/constructor/linking thất bại theo các loại đã catch, candidate loader được đóng và exception được ném lại. `Manager.current` chưa bị thay, nên bản active cũ vẫn phục vụ. Đoạn catch chỉ bao phủ `Exception | LinkageError`; nó **không** biến mọi `Error` nghiêm trọng như `OutOfMemoryError` thành lỗi có thể rollback an toàn.

**`Manager.invoke`** giữ read lock xuyên suốt lời gọi `plugin.execute`. `replace` cần write lock, vì thế chờ các lời gọi qua manager kết thúc trước khi thay active reference và đóng old generation.

**`Manager.activeType`** trả runtime `Class<?>` để chứng minh class identity. Trả token ra ngoài session cho phép caller **giữ loader cũ sống**; đây là thao tác phục vụ bài test, không phải API production nên khuyến khích giữ lâu dài.

**`Manager.close`** đặt active thành null và đóng generation còn hoạt động, ngăn request mới sử dụng plugin qua manager.

### 8.2. Vì sao giữ read lock trong toàn bộ plugin call?

Giả sử worker R1 đang chạy `plugin.execute` của v1, trong khi R2 yêu cầu reload:

```text
R1: acquire readLock
R1: gọi v1.execute()
R2: yêu cầu writeLock -> phải chờ
R1: trả kết quả, release readLock
R2: acquire writeLock
R2: current <- v2, close v1
R2: release writeLock
R3: acquire readLock -> gọi v2
```

Điều này tránh một loại race rất phổ biến: host đóng old loader ngay khi worker còn đang load resources/classes hoặc chạy code v1. Java SE 17 cảnh báo concurrent `URLClassLoader.close()` với class load có kết quả không xác định.

Nhược điểm: plugin call chậm hoặc bị treo có thể giữ read lock quá lâu và khiến reload bị chặn. Production cần timeout/cancellation/operation tracking; có thể dùng reference-counted generations thay vì một read–write lock giữ xuyên invocation, nhưng phải đảm bảo **không close generation khi còn in-flight callers**.

### 8.3. Vì sao không gán current = null trước khi chuẩn bị v2?

Nếu xóa active trước pha prepare, một lỗi như `ClassNotFoundException` hoặc constructor exception của v2 khiến host **không còn plugin đang phục vụ**, dù v1 vẫn có thể hoạt động.

Ở demo, v2 được chuẩn bị trước:

```java
Generation next = prepare(Path.of("out/v2"));
manager.replace(next);
```

Do đó, khi `prepare` thất bại, `replace` không chạy và v1 không bị thay.

**Ghi chú giao dịch:** `replace` trong demo chuyển `current` sang v2 **trước** khi gọi `previous.close()`. Nếu shutdown v1 ném lỗi, v2 **đã được publish** dù `replace` ném exception. Vì vậy đây là **atomic switch cho các lời gọi đi qua lock**, không phải một transaction toàn cục bảo đảm rollback mọi side effects. Hệ thống production nên tách bước activation khỏi cleanup, báo trạng thái riêng, idempotent stop, và quyết định rollback/failover rõ ràng.

## 9. Build và chạy: Linux/macOS Bash

Đứng ở thư mục gốc chứa `src`:

```bash
mkdir -p out/api out/v1 out/v2 out/host

javac -d out/api src/api/api/Plugin.java

javac -cp out/api -d out/v1 src/v1/plugin/EchoPlugin.java

javac -cp out/api -d out/v2 src/v2/plugin/EchoPlugin.java

javac -cp out/api -d out/host \
  src/host/host/ReloadPluginDemo.java

java -cp "out/api:out/host" host.ReloadPluginDemo
```

**Cực kỳ quan trọng:** classpath của lệnh `java` chỉ có `out/api` và `out/host`. Không đưa `out/v1` hoặc `out/v2` vào application classpath khi chạy; nếu không parent-first delegation có thể khiến loader chung tìm thấy implementation trước và làm hỏng tính độc lập của generation.

Các lệnh `javac` biên dịch v1 và v2 **riêng biệt**, mỗi lượt tham chiếu `out/api` để implement đúng shared interface.

## 10. Build và chạy: Windows PowerShell

PowerShell dùng dấu `;` làm classpath separator:

```powershell
New-Item -ItemType Directory -Force out/api,out/v1,out/v2,out/host

javac -d out/api src/api/api/Plugin.java

javac -cp out/api -d out/v1 src/v1/plugin/EchoPlugin.java

javac -cp out/api -d out/v2 src/v2/plugin/EchoPlugin.java

javac -cp out/api -d out/host src/host/host/ReloadPluginDemo.java

java -cp "out/api;out/host" host.ReloadPluginDemo
```

Chạy trong một working directory mới/sạch, tránh có class `plugin.EchoPlugin` từ lần build khác trên parent classpath. Nếu dùng IDE, cũng cần kiểm tra **run configuration** có vô tình thêm v1/v2 vào classpath của host không.

## 11. Stdout dự kiến và kiểm chứng thực tế

Với Java 17+ và project sạch như trên:

```text
FIRST=v1:hello:1
SECOND=v1:world:2
PREPARE_FAILED=ClassNotFoundException
STILL=v1:again:3
CLOSE=v1
AFTER=v2:hello:1
SAME_BINARY_NAME=true
SAME_RUNTIME_CLASS=false
SHARED_API=true
DIFFERENT_DEFINERS=true
CLOSE=v2
```

Đây là kết quả xác định của **demo tuần tự**. Mã đã được biên dịch và chạy thử bằng OpenJDK 21 với các API/cú pháp tương thích Java 17; kết quả khớp đúng các dòng trên. Việc chạy thử trên JDK 21 **không đồng nghĩa** đã chạy bằng JDK 17 cụ thể.

### Diễn giải từng nhóm kết quả

**Hai lời gọi đầu v1:**

```text
FIRST=v1:hello:1
SECOND=v1:world:2
```

Cùng class `plugin.EchoPlugin` của L1 sử dụng cùng static field `calls`.

**Chuẩn bị bản không tồn tại:**

```text
PREPARE_FAILED=ClassNotFoundException
STILL=v1:again:3
```

`out/missing` không chứa class `plugin.EchoPlugin`, parent classpath cũng không có class đó. Candidate lookup báo `ClassNotFoundException` và `prepare` dọn candidate loader. `replace` chưa được gọi, nên manager vẫn trỏ v1.

**Swap:**

```text
CLOSE=v1
AFTER=v2:hello:1
```

Marker `CLOSE=v1` từ lifecycle method của plugin cũ xuất hiện khi manager thay generation. Sau đó bản v2 xử lý lời gọi mới với prefix khác; static `calls` của runtime class v2 bắt đầu tại 0 nên lời gọi đầu cho `:1`.

**Type identity:**

```text
SAME_BINARY_NAME=true
SAME_RUNTIME_CLASS=false
SHARED_API=true
DIFFERENT_DEFINERS=true
```

`oldType` và `newType` cùng `getName()` nhưng khác `Class<?>` identity. Cả hai assignable sang `api.Plugin` của host. Hai `getClassLoader()` khác nhau nên có hai defining loaders.

**Shutdown cuối:**

```text
CLOSE=v2
```

Try-with-resources của `Manager` đóng generation v2. Không có dòng nào chứng minh v1/v2 đã được GC hoặc class unloaded; marker chỉ chứng minh callback `close()` đã được gọi.

## 12. Phân biệt chuẩn bị, kích hoạt, dừng và class unloading

Một reload thật gồm các sự kiện khác nhau:

| Sự kiện | Xảy ra trong demo | Có bảo đảm unload class không? |
|---|---|---|
| Tạo L2 | `prepare(out/v2)` | Không |
| Load v2 class | `Class.forName` với L2 | Không |
| Khởi tạo v2 instance | Reflection constructor | Không |
| Đổi active từ v1 sang v2 | `Manager.replace` | Không |
| Chờ lời gọi cũ hoàn tất | Write lock chờ read locks | Không, chỉ với calls qua manager |
| Gọi `v1.close()` | Lifecycle callback | Không |
| Gọi `L1.close()` | `Generation.close()` | Không |
| Bỏ references tới L1 và class v1 | Cần theo dõi mọi references/roots | Tạo điều kiện có thể reclaim |
| GC/class unloading | Quyết định JVM | Không có deadline |

### Class unloading theo JLS 17 §12.7

JVM chỉ có thể unload một class/interface nếu **defining loader** của nó có thể được garbage collector reclaim; bản thân việc đủ điều kiện không bắt buộc JVM unload ngay.

Trong demo, biến:

```java
Class<?> oldType = manager.activeType();
```

vẫn giữ `Class<?>` cũ để làm bài kiểm tra identity sau reload. Điều đó **có thể tiếp tục giữ L1 reachable**. Đây là lựa chọn có chủ ý để minh họa, không phải cách giữ trong production.

Muốn cho L1 có cơ hội được reclaim, sau khi kiểm chứng phải bỏ cả reference `oldType`, mọi instance/lambda/reflection metadata của v1, listener registries, cache entries, TCCL và background tasks tương ứng; **không được kết luận “CLOSE=v1” nghĩa L1 đã GC**.

### Close không hủy được code đang chạy

`URLClassLoader.close()` đóng nguồn URL/JAR resources và ảnh hưởng việc tìm class/resource mới. Các runtime classes đã load và objects đã tạo vẫn có thể được dùng khi còn reachable. Nếu plugin tự khởi động thread khác và thread ấy còn giữ code/objects v1, close loader không tự stop thread.

**Lời khuyên:** triển khai `Plugin.close()` thực sự làm lifecycle cleanup, đồng thời host theo dõi worker/scheduler của plugin. Nếu plugin không đáng tin, class-loader isolation không phải security sandbox; cần process-level isolation khi threat model đòi hỏi.

## 13. Ví dụ vì sao sửa file JAR tại chỗ là sai

Giả sử triển khai:

```text
plugins/current/plugin.jar
```

và host dùng một `URLClassLoader` trỏ vào file đó. Bạn ghi đè `plugin.jar` bằng v2 khi host vẫn dùng loader L1.

Những điều có thể xảy ra:

- Class `plugin.EchoPlugin` đã loaded vẫn là runtime class v1 do L1 định nghĩa.
- Một class `plugin.Helper` chưa được loaded có thể được đọc về sau từ bytes JAR vừa thay, khiến một generation trộn code v1/v2.
- Resources/service descriptors có thể bị đọc từ bản khác với class đã load.
- JAR handles/caching/file replacement semantics khác nhau giữa filesystem/JDK; trên Windows còn có thể gặp file locking.
- `NoSuchMethodError`/`NoSuchFieldError` hoặc hành vi sai do ABI không nhất quán.

Đừng dùng “đã copy xong JAR mới” như một synchronization protocol giữa deploy process và JVM runtime.

**Cách thích hợp hơn:** mỗi version là một **immutable release directory/JAR riêng**:

```text
plugins/releases/plugin-1.0/plugin.jar
plugins/releases/plugin-2.0/plugin.jar
```

Host xác thực artifact v2, tạo loader mới từ đường dẫn v2, chuyển active generation rồi stop/close v1. Không sửa artifact cũ khi L1 vẫn có thể đọc thêm class/resource.

## 14. Deploy release an toàn và tránh TOCTOU

Với plugin lấy từ nguồn mạng hoặc shared directory, cần xác thực **đúng bytes sẽ dùng để thực thi**. Một quy trình production:

1. **Build/package:** tạo JAR/module artifact nhất quán, có version và dependency manifest.
2. **Approve:** ký manifest hoặc pin digest artifact từ nguồn phê duyệt độc lập.
3. **Download/stage:** tải về thư mục do actor tin cậy quản lý, giới hạn quyền ghi.
4. **Verify:** kiểm tra signature/hash của toàn artifact/dependencies, không chỉ entry class.
5. **Snapshot:** giữ artifact đã verified ở nơi immutable, không cho actor khác sửa giữa check và load.
6. **Prepare candidate loader** từ snapshot v2.
7. **Health-check candidate** trước activation trong phạm vi có thể; tránh tác dụng phụ không kiểm soát.
8. **Activate:** điều phối requests/trạng thái và chuyển generation.
9. **Observe/rollback:** giám sát behavior, sẵn đường fallback nếu v2 lỗi.
10. **Retire old:** dừng tasks v1, unregister listeners, clear caches, close loader và bỏ references.

**Lưu ý quan trọng:** `prepare` demo dùng `Class.forName(..., true, loader)` và tạo instance, vì vậy đã thực thi code candidate trước lúc publish. Artifact phải được trust/verified **trước khi** gọi `prepare`; không dùng chuẩn bị plugin như một vùng sandbox.

Nếu cần rollback tức thì sau khi v2 nhận traffic, có thể giữ v1 tạm thời theo một **grace period** thay vì close ngay, rồi retire sau khi v2 đạt health criteria. Nhưng phải giới hạn số generations giữ sống, tránh Metaspace leak do giữ vô hạn old loaders.

## 15. Vì sao shared API phải ở parent loader?

Ở runtime:

```java
Plugin plugin = (Plugin) type.getConstructor().newInstance();
```

Phép cast hợp lệ vì `plugin.EchoPlugin` của cả hai loaders triển khai **chính `api.Plugin` do parent loader định nghĩa**.

Nếu plugin v2 chứa bản riêng `api.Plugin` và một custom child-first loader tự định nghĩa bản đó, thì:

```text
("api.Plugin", hostLoader) != ("api.Plugin", pluginLoader)
```

Object implementation có thể không assignable sang `Plugin.class` của host, dẫn đến `ClassCastException` hoặc `LinkageError` do loader constraints khi liên kết những reference types trong method descriptors.

**Giữ trong shared API loader:** interface, DTO, exception contracts và các types đi qua host/plugin boundary. **Giữ trong child plugin loader:** implementation/private dependencies có version khác nhau. Tránh đưa `lib.private.*` vào public method parameters/returns của `api.Plugin`.

Với Java module system (JPMS) và `ModuleLayer`, ngoài loader identity còn phải xét module readability, `exports` và `opens`. Ví dụ ở đây chủ động dùng **classpath** để tập trung vào classloader reload, không giả định mọi rules của named modules được bypass.

## 16. State migration: static state không tự chuyển sang loader mới

V1 và v2 có class riêng, vì vậy:

```java
private static int calls;
```

trong hai versions là **hai static fields độc lập**. `AFTER=v2:hello:1` là hệ quả của code demo: v2 bắt đầu thế hệ mới.

Nếu plugin production cần giữ thông tin như user sessions, counters, checkpoints, subscriptions hoặc workflow state, cần một protocol explicit:

```text
v1 quiesce()
  -> exportState() sang shared DTO / serialized schema
  -> validate schema và version compatibility
  -> create v2
  -> importState(snapshot) trước activation
  -> health check
  -> switch traffic
  -> stop v1, release loader
```

Đây là kiến trúc minh họa, **không phải các method có sẵn** trong `api.Plugin` của demo. Nếu muốn triển khai, mở rộng shared API bằng contract rõ ràng và version hóa.

Đừng chuyển trực tiếp `Class<?>`, private objects hoặc lambda của v1 qua boundary để v2 giữ. Những object ấy có runtime identity gắn với L1; nếu v2 lưu chúng thì L1 còn reachable và cách diễn giải type/ABI có thể sai. DTO do parent định nghĩa hoặc định dạng serialization độc lập classloader thường dễ quản lý hơn.

### Khi migration thất bại

Nếu import state v1→v2 không thành công, **không publish candidate** và để v1 tiếp tục phục vụ. Nếu đã quiesce v1, cần quy trình resume/thaw cẩn thận. Không dừng v1 trước khi biết có thể khôi phục dịch vụ.

**Idempotency và external side effects:** nếu v2 đã ghi database/message queue trong pha health check hoặc prepare, rollback con trỏ active không tự đảo ngược side effect. Vì vậy “atomic reload” không nên bị hiểu là transaction của mọi I/O.

## 17. Đồng thời: từ demo lock sang production drain

Read–write lock của demo dễ hiểu nhưng có hạn chế:

- Write lock chờ mọi read locks; một method lâu hoặc treo có thể trì hoãn reload vô hạn.
- Nếu plugin giữ lại work asynchronous sau khi method `execute` trả về, read lock đã được thả nhưng task cũ vẫn có thể chạy.
- Plugin có thể tự gọi lại manager từ một thread hoặc reenter code và tạo lock/lifecycle coupling ngoài dự kiến.
- Đóng loader ngay trong write lock có thể làm tăng thời gian chặn requests.
- Nếu `plugin.close()` gọi lại manager, có thể tạo reentrancy/deadlock logic, tùy call path.

Một thiết kế khác dùng **lease/reference count**:

```text
Acquire active generation -> increment inFlight
Run plugin call
Release generation -> decrement inFlight

Reload:
  prepare v2
  atomically publish v2
  mark v1 retired
  refuse NEW leases for v1
  wait until v1.inFlight == 0
  close v1
```

Đây là pseudocode/sơ đồ thuật toán. Nếu triển khai cần bảo đảm thao tác acquire lease và publish retirement không race: không thể đơn giản đọc `AtomicReference` rồi tăng counter quá muộn, vì retire có thể đóng loader trước khi tăng. Dùng một state machine/lock/CAS protocol đúng.

**Trade-off:**

| Cơ chế | Ưu điểm | Giới hạn |
|---|---|---|
| Read–write lock xuyên invocation | Dễ chứng minh không close khi còn lời gọi qua manager | Reload chờ synchronous calls hoàn tất |
| Ref-counted generation leases | Swap nhanh, cho old calls drain | Khó đúng hơn, cần guard atomicity giữa acquire/retire |
| Process-level rolling restart | Cách ly loader/native/global state tốt hơn | IPC/process orchestration phức tạp |
| Stop-the-world plugin quiescence | Đơn giản hơn khi workload nhỏ | Downtime dài khi stop/load |

Đối với ứng dụng học tập hoặc plugin chạy trong cùng JVM và calls tương đối ngắn, read–write lock là mô hình thực hành tốt. Với hệ thống production concurrency cao, cần SLA và kiến trúc riêng.

## 18. Hủy tác vụ, TCCL và ngăn classloader leak

Dù `Manager` đã close L1, loader v1 vẫn có thể reachable nếu các thành phần khác giữ references:

| Root sống lâu | Có thể giữ L1 qua | Cleanup cần thiết |
|---|---|---|
| Host static registry | `Class<?>`, `Method`, old Plugin instance | Gỡ entry của generation cũ |
| Listener/event bus | Callback implementation do L1 định nghĩa | Unregister listeners |
| Worker thread pool | `Thread.contextClassLoader` = L1 | Restore TCCL trong chính worker |
| `ThreadLocal` | Value chứa plugin context/instance | `remove()` ở worker thread |
| Scheduled future/executor | Runnable/lambda của plugin v1 | Cancel tasks, shutdown executor, drain |
| Reflection cache | `Method`/`MethodHandle` của class v1 | Clear per-generation cache |
| ServiceLoader/provider registry | Provider objects do L1 định nghĩa | Clear provider references |
| Old `Class<?>` token | Host giữ class metadata của v1 | Bỏ token sau diagnostics |

Ví dụ khi framework yêu cầu TCCL:

```java
Thread worker = Thread.currentThread();
ClassLoader saved = worker.getContextClassLoader();
try {
    worker.setContextClassLoader(pluginLoader);
    // Chạy thao tác cần TCCL của plugin.
} finally {
    worker.setContextClassLoader(saved);
}
```

Snippet này cần biến `pluginLoader` đã có. TCCL phải được khôi phục trên **thread thực sự chạy thao tác**, không chỉ trên thread submit task.

**Không mặc định `URLClassLoader.close()` xóa mọi references hoặc ngắt threads.** Nó đóng resources do URLClassLoader quản lý; class unloading vẫn phụ thuộc reachability và quyết định của JVM.

### Bẫy WeakHashMap

`WeakHashMap<ClassLoader, Class<?>>` có **weak keys nhưng strong values**. Nếu value là class do loader key định nghĩa, value có thể giữ lại loader nên key không tự GC như bạn mong đợi. Đừng đổi host static `HashMap` thành `WeakHashMap` rồi coi đó là fix hoàn chỉnh.

Dễ kiểm soát hơn: cache `Class<?>`/`Method` trong **Generation** và clear khi retire; không giữ strong references ở host registry sau stop.

## 19. Quan sát class loading/unloading khi reload

Trên HotSpot Java 17, chạy demo với Unified Logging:

```bash
java -Xlog:class+load=info,class+unload=info \
     -cp "out/api:out/host" host.ReloadPluginDemo
```

Đối với Windows PowerShell:

```powershell
java -Xlog:class+load=info,class+unload=info -cp "out/api;out/host" host.ReloadPluginDemo
```

Class-load log có thể chứa **hai events** cho `plugin.EchoPlugin`: một khi L1 định nghĩa v1, một khi L2 định nghĩa v2. Chi tiết nguồn, timestamp, tags và định dạng dòng log phụ thuộc JVM. Không suy từ việc tên class xuất hiện hai lần rằng duplicate define trong một loader; ở đây là hai defining loaders độc lập.

**Không có bảo đảm class-unload event** xuất hiện trước khi demo kết thúc. Đặc biệt `oldType` là strong local token được giữ để in kết quả identity; nó có thể giữ L1 còn reachable trong khoảng chạy đó.

Muốn điều tra nhiều vòng hot reload trên service thật:

1. Quan sát số active plugin generations.
2. Quan sát loader instances và số class được định nghĩa bởi từng loader.
3. So sánh Metaspace used/committed và heap references qua nhiều chu kỳ.
4. Kiểm tra TCCL, `ThreadLocal`, callbacks, static maps và generated proxy/Method caches.
5. Dùng heap dump/path-to-GC-root analysis khi nghi classloader leak.
6. Nhớ rằng **Metaspace tăng hoặc thiếu unload log một lần** chưa đủ kết luận leak; VM có thể trì hoãn unload và allocator có thể giữ memory để tái sử dụng.

## 20. Các tình huống lỗi thường gặp

### 20.1. Reload xong vẫn gọi behavior v1

Nguyên nhân có thể:

- Parent application classpath đã chứa `plugin.EchoPlugin` v1; cả L1/L2 parent-first đều nhận class của parent.
- Candidate L2 vô tình trỏ tới directory/JAR v1.
- Host không thực sự thay active reference, chỉ tạo L2 rồi bỏ không.
- Cache `Class<?>`/`Method` toàn cục trả class cũ theo binary name.
- Request handler khác vẫn giữ trực tiếp old plugin instance, không đi qua manager.

Cách kiểm tra:

```java
Class<?> type = activePlugin.getClass();
System.out.println(type.getName());
System.out.println(type.getClassLoader());
```

Snippet cần biến `activePlugin`. So sánh trực tiếp `Class<?>` v1/v2, đường URLs của loader và bản implementation thực sự đang được gọi.

### 20.2. ClassCastException khi ép về shared Plugin

Nếu L2 child-first tự định nghĩa bản riêng `api.Plugin`, implementation của L2 không assignable sang `api.Plugin` của host. Dùng một API artifact ở parent và cho các plugin loaders delegate `api.*` về đúng loader chung. Chữ ký dùng shared DTO/exception types, không plugin-private types.

### 20.3. NoSuchMethodError / AbstractMethodError

Plugin v2 có thể được compile với shared API version khác host. Dù L2 tạo thành công và classpath đúng, ABI mismatch khi linking/invoking vẫn có thể gây lỗi. So sánh `javap -s -p` và dependency versions.

Không sửa bằng cách catch `LinkageError` rồi thử tải bytes không rõ nguồn. Chuẩn bị candidate cần kiểm tra binary compatibility và integration smoke tests.

### 20.4. ClassNotFoundException ở candidate

Có thể do:

- Đường dẫn v2 sai hoặc chưa có files.
- JAR/directory layout không chứa đúng binary name `plugin.EchoPlugin`.
- Dependency của plugin thiếu ở loader graph.
- Parent/child policy sai, JPMS module unresolved nếu dùng named modules.

Trong ví dụ, `out/missing` chủ động gây `ClassNotFoundException` để kiểm tra rollback ở **pha prepare**.

### 20.5. File JAR bị khóa sau stop

`URLClassLoader.close()` giúp đóng các JAR files loader đã mở; nhưng nếu streams/classpath users khác còn giữ file handles, OS hoặc framework có thể vẫn chặn việc xóa/đổi tên file. Không vội giả định cứ close loader là toàn bộ liên quan tới file được giải phóng.

### 20.6. Metaspace tăng không ngừng qua nhiều reload

Rất có thể host đang giữ old generations trong static registry, ThreadLocal/TCCL, event bus, executor hoặc reflection cache. Dùng profiler và GC-root paths để xác nhận. Không chỉ nâng `MaxMetaspaceSize` mà bỏ qua nguồn retention.

### 20.7. Reload thành công nhưng hoạt động lỗi sau một lúc

Candidate có thể pass constructor nhưng fail khi xử lý input thực, load lazy dependency/resource hoặc dùng method chưa kiểm tra. Production nên có health checks/integration tests cho các critical paths và rollback window. Một constructor chạy thành công **không chứng minh toàn bộ plugin an toàn và tương thích**.

### 20.8. Stop làm treo reload

Plugin code có thể blocking vô hạn trong `execute`, giữ read lock khiến write lock chờ. Cần timeout/cancellation/cooperative shutdown và thiết kế tách process nếu code không đáng tin hoặc không thể kiểm soát.

## 21. Kiểm thử bắt buộc trước khi dùng thật

| Test | Kỳ vọng |
|---|---|
| Khởi động v1 | `FIRST=v1:hello:1` |
| Gọi v1 hai lần | State tăng 1→2 |
| Candidate không tồn tại | `ClassNotFoundException` được báo; v1 tiếp tục chạy |
| Prepare v2 | Loader mới tạo, interface cast thành công |
| Thay v1→v2 | Marker `CLOSE=v1`, request tiếp theo vào v2 |
| Static state thế hệ v2 | Bắt đầu `:1` |
| So sánh binary name | Hai classes cùng tên |
| So sánh runtime class identity | Hai classes khác nhau |
| Kiểm tra shared API | Cả hai assignable sang `api.Plugin` của host |
| Concurrency | Không đóng v1 khi lời gọi qua manager chưa hoàn tất |
| Cleanup | URLClassLoader đóng, callbacks/threads/cache được dọn |
| Reload nhiều lần | Không tích lũy old generations vô hạn |
| API evolution | Contract v1/v2 được kiểm tra binary compatibility |
| Artifact verification | Không chạy bytes chưa được approve |
| Rollback | Prepare failure không xóa active v1; post-activation failure có chính sách riêng |

### Test race đáng chú ý

Một thread T1 vào `Manager.invoke` và cố tình block trong v1 `execute`. Thread T2 gọi `replace(v2)`. Trong demo, T2 phải chờ T1 nhả read lock rồi mới close v1. Nếu một test concurrent nhận `CLOSE=v1` **trước khi T1 hoàn thành**, có bug trong cách quản lý ownership/lock hoặc plugin đang chạy theo đường ngoài manager.

Các test concurrency nên dùng `CountDownLatch`/`Phaser` để điều khiển tiến trình thay vì chỉ `Thread.sleep` và đo thời gian; sleep-based tests có thể flaky.

## 22. Thực hành mở rộng sau bài demo

### Bài tập A — thêm phiên bản v3

Tạo `src/v3/plugin/EchoPlugin.java` với behavior `v3:`, biên dịch vào `out/v3`, rồi thay v2→v3 bằng `prepare(Path.of("out/v3"))`. Quan sát class identity khác giữa từng generation và shared API không đổi.

**Kiểm tra:** mỗi phiên bản có static `calls` riêng. Không nên giữ `List<Class<?>> allOldTypes` static chỉ để so sánh tất cả generations trong production.

### Bài tập B — giữ trạng thái nghiệp vụ

Mở rộng `api.Plugin` với `exportState()`/`importState(...)` qua shared DTO chứa số lời gọi. Trước activation, migrate state có schema/version guard. Không truyền trực tiếp instance của `plugin.EchoPlugin` v1 sang plugin v2.

**Kiểm tra:** reload mới tiếp tục counter mong muốn và không giữ old loader reachable qua state object.

### Bài tập C — mô phỏng long-running call

Trong v1, dùng `CountDownLatch` để giữ một lời gọi đang chạy. Trong thread khác, prepare và replace v2. Xác minh v1 không close trước khi lời gọi qua manager hoàn tất. Không dựa vào sleep/console output timing làm điều kiện test duy nhất.

### Bài tập D — lỗi close

Cho `Plugin.close()` ném `RuntimeException` trong v1 và quan sát hành vi `Manager.replace`. Trong thiết kế demo, v2 đã được publish trước khi close v1; exception không rollback active reference. Bổ sung error reporting và cleanup policy tách biệt để manager thông báo chính xác “activation succeeded; retirement failed”.

### Bài tập E — jar đóng gói

Đóng gói hai versions thành hai JAR độc lập bằng `jar --create`. Chỉ cho URLClassLoader v1/v2 biết JAR riêng tương ứng; application classpath chỉ chứa shared API + host.

Ví dụ với output đã biên dịch:

```bash
jar --create --file plugin-v1.jar -C out/v1 .
jar --create --file plugin-v2.jar -C out/v2 .
```

Khi đổi sang JAR, `prepare` phải nhận **đúng URL của JAR file**, không còn URL của thư mục chứa file. Không sửa lệnh mẫu khi vẫn dùng thư mục `out/v1`/`out/v2`; hai chế độ source URLs cần tương ứng.

### Bài tập F — unload diagnostics

Dùng `WeakReference<ClassLoader>` để theo dõi loader cũ sau khi retire và bỏ hết strong references. Bật `-Xlog:class+unload=info` và so sánh. **Không assert bắt buộc weak ref cleared sau một `System.gc()`** vì GC/class unloading không deterministic. Học cách phân tích roots nếu loader vẫn còn.

## 23. Những hiểu lầm cần tránh

| Hiểu lầm | Thực tế |
|---|---|
| “Reload là gọi `loadClass` lần nữa trên loader cũ.” | Runtime class cũ thường được tái sử dụng; muốn generation mới cần loader mới |
| “Sửa JAR tại chỗ là đủ.” | Có thể trộn bytes/resources giữa các generations |
| “Hai class cùng tên nên cùng type.” | Defining loader cũng xác định runtime identity |
| “Tạo L2 trước khi L1 unload là sai.” | Hai runtime classes cùng tên có thể cùng tồn tại bởi hai loaders khác nhau |
| “Mỗi plugin nên tự đóng gói API.” | Shared API type phải được định nghĩa thống nhất ở parent |
| “`URLClassLoader.close()` unload class ngay.” | Close chỉ quản lý loader resources, không ra lệnh GC/unload đồng bộ |
| “Constructor v2 thành công là plugin đã được health-check.” | Constructor không kiểm tra mọi lazy path/dependency |
| “`prepare` không thực thi plugin code.” | `Class.forName(..., true, ...)` và constructor có thể chạy code |
| “Read/write lock xử lý luôn background tasks của plugin.” | Chỉ bảo vệ calls qua manager; plugin-owned tasks cần cleanup riêng |
| “Swap active reference là transaction rollback mọi side effects.” | External side effects và lỗi cleanup cần policy riêng |
| “Static state của v1 sẽ tự chuyển sang v2.” | Mỗi runtime class có static state riêng; migrate qua shared DTO/schema nếu cần |
| “Tôi đã đóng session nên loader chắc chắn GC.” | Cần bỏ tất cả host cache/Class/Method/TCCL/ThreadLocal references; unloading nondeterministic |
| “Chỉ cần TTL là giải quyết old loader leak.” | Phải invalidate theo lifecycle và dừng references/tasks đúng owner |
| “Java ClassLoader là sandbox cho plugin lạ.” | Không; cần process/OS isolation cho threat model cao |

## 24. Bài tập tự kiểm tra

**Câu 1.** Vì sao `new URLClassLoader(...)` cho v2 quan trọng hơn việc sửa `EchoPlugin.class` trên đĩa?

**Đáp án:** Class đã định nghĩa trong L1 gắn với runtime identity của L1; thay file không thay định nghĩa đó. L2 có thể định nghĩa một runtime class mới cùng binary name.

**Câu 2.** Tại sao parent của hai plugin loaders được chọn là `Plugin.class.getClassLoader()`?

**Đáp án:** Để v1/v2 cùng tham chiếu đúng `api.Plugin` mà host dùng; nếu mỗi child định nghĩa API copy, cast/loader constraints có thể sai.

**Câu 3.** Vì sao sau reload in `SAME_BINARY_NAME=true` nhưng `SAME_RUNTIME_CLASS=false`?

**Đáp án:** Tên `plugin.EchoPlugin` giống nhau, nhưng defining loaders L1/L2 là hai objects khác nhau, tạo hai runtime types.

**Câu 4.** Tại sao khi `prepare(out/missing)` thất bại, manager vẫn in `STILL=v1:again:3`?

**Đáp án:** Candidate được dựng ngoài manager; thất bại trước khi `replace` chạy nên active vẫn trỏ v1.

**Câu 5.** Trong demo, read/write lock giải quyết điều gì?

**Đáp án:** Đảm bảo `replace` chờ các lời gọi `invoke` đang giữ read lock hoàn tất trước khi publish v2 và close v1. Nó không quản lý background threads của plugin ngoài manager.

**Câu 6.** Vì sao `CLOSE=v1` không chứng minh class v1 đã unload?

**Đáp án:** Đây chỉ là marker từ plugin lifecycle method. Defining loader còn có thể reachable qua Class token, instance, cache, threads; VM quyết định unloading.

**Câu 7.** Nếu `Plugin.close()` của v1 ném lỗi sau khi current đã được chuyển sang v2, có tự rollback không?

**Đáp án:** Không trong demo. Cần phân biệt activation state và retirement error, thiết kế production error reporting/rollback riêng.

**Câu 8.** Vì sao `AFTER=v2:hello:1` cho thấy static state khác?

**Đáp án:** `calls` thuộc class v2 do loader L2 định nghĩa, độc lập `calls` của v1/L1; lượt gọi đầu của v2 tăng từ 0 lên 1.

**Câu 9.** Nếu host tiếp tục giữ `Class<?> oldType` sau reload, có tác động gì?

**Đáp án:** Token đó có thể giữ runtime class và defining loader L1 reachable, cản việc đủ điều kiện class unloading. Chỉ giữ tạm để debug/test.

**Câu 10.** Vì sao plugin JAR nên immutable sau khi đã verified và đưa vào loader?

**Đáp án:** Tránh TOCTOU, class/resource bytes trộn nhiều versions và khó xác minh nguồn; mỗi generation đọc từ snapshot của đúng release.

**Câu 11.** Nếu v2 cần giữ state v1, có nên cast trực tiếp instance v1 sang implementation class v2 không?

**Đáp án:** Không. Hai implementation types khác defining loader/identity. Dùng shared DTO hoặc versioned serialization schema để migrate state.

**Câu 12.** Nếu plugin tự tạo thread chạy liên tục, chỉ dùng manager write lock có đảm bảo old plugin dừng không?

**Đáp án:** Không. Phải có explicit shutdown/cancellation/await cho plugin-owned thread/executor cùng cleanup TCCL/ThreadLocal.

## 25. Tổng kết và nguồn tham khảo

**Ghi nhớ quy trình đúng:**

1. **Giữ shared API ở parent.**
2. **Build v1/v2 thành những artifacts độc lập** với cùng implementation binary name nhưng loader generations khác nhau.
3. **Verify và prepare v2** trước khi thay active; nếu prepare fail, giữ v1 phục vụ.
4. **Quiesce/drain old calls** bằng lock hoặc generation leases được thiết kế đúng.
5. **Publish v2 có kiểm soát**, rồi retire v1 và đóng resources.
6. **Xóa roots tới old generation** để JVM có cơ hội GC loader và unload classes; đừng đòi unload tức thì.
7. **Quan sát và kiểm thử** behavior, class identity, API compatibility, errors và resource lifecycle.

Tài liệu chuẩn:

- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, delegation, `findLoadedClass`, runtime class identity.
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): tạo loader theo URL, named constructor, `close()`, cảnh báo concurrency.
- [Java SE 17 — ReentrantReadWriteLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantReadWriteLock.html): read/write locking cho lifecycle handoff.
- [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `forName`, `getClassLoader` và runtime type tokens.
- [JLS 17 — §§12.2, 12.4, 12.7](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html): loading, initialization và unloading.
- [JVMS 17 — §5.3 Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3): defining/initiating loaders và loading constraints.
- [Java SE 17 — java command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-Xlog:class+load`, `-Xlog:class+unload` và JVM diagnostics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-exercises-010">Điều tra một tình huống rò rỉ class loader</a>

<details>
<summary>Xem chi tiết</summary>

# Điều tra một tình huống rò rỉ class loader trong Java

## 1. Tình huống thực tế: reload plugin nhiều lần, metaspace vẫn tăng

Giả sử một ứng dụng Java chạy lâu dài có cơ chế tải plugin từ JAR riêng bằng `URLClassLoader`. Mỗi khi triển khai phiên bản mới, host tạo loader mới, tải implementation mới, chuyển request sang phiên bản mới rồi đóng loader cũ.

Sau hàng trăm lượt reload, giám sát ghi nhận:

- Số class loader hoặc classes đã nạp tăng, nhưng số classes được unload ít.
- Metaspace có xu hướng tăng theo số lần reload và không trở về mức ổn định.
- Heap dump cho thấy nhiều `URLClassLoader` cũ vẫn reachable.
- Máy chạy lâu có thể gặp `OutOfMemoryError: Metaspace` hoặc độ trễ GC tăng; tuy nhiên không phải mọi lần tăng metaspace đều dẫn đến lỗi.
- File JAR cũ có thể vẫn bị giữ trên Windows nếu resource handles chưa được đóng — **đây là lỗi tài nguyên khác** với việc ClassLoader còn sống trong heap.

Câu hỏi của bài thực hành không phải “làm sao gọi `System.gc()` để ép unload?”, mà là:

> **Tham chiếu mạnh nào từ một GC root đang giữ loader của phiên plugin đã nghỉ, và ai phải xóa tham chiếu ấy trong vòng đời plugin?**

Trong ví dụ sẽ tự tạo hai đường giữ loader có thể quan sát chắc chắn:

1. Một registry `static` của host giữ `Class<?>` thuộc plugin loader.
2. Một worker thread sống lâu của host giữ loader qua **thread context class loader (TCCL)**.

Ta sẽ đóng URLClassLoader, chứng minh **close không tự cắt hai reference**, rồi dọn registry/TCCL, nhưng **không khẳng định rằng GC/class unloading đã diễn ra ngay lập tức**.

## 2. Một loader còn sống chỉ là leak khi đã hết vòng đời dự kiến

Mỗi plugin generation thường có defining loader riêng, và `Class<?>` cũng mang runtime identity của loader đó. Nếu một generation đã dừng nhưng `Class<?>`, plugin object hay TCCL còn được host giữ, JVM có thể không thu hồi loader và metadata tương ứng.

Ba sự kiện **không đồng nghĩa**:

| Sự kiện | Bảo đảm | Không bảo đảm |
| --- | --- | --- |
| `URLClassLoader.close()` | Bắt đầu giải phóng các URL/JAR resources do loader mở; không tìm nguồn riêng mới theo contract | Không xóa classes, stop threads hoặc ép GC |
| Host bỏ strong references | Cắt các đường giữ mà host kiểm soát | Có thể còn GC roots khác; không đảm bảo unload ngay |
| GC/class unloading | JVM thu hồi metadata khi điều kiện phù hợp | Không có deadline do `System.gc()` đặt |

**Metaspace tăng/committed chưa giảm sau một lần GC không đủ kết luận leak.** Bằng chứng mạnh hơn là một loader *đã retire*, không còn request hợp lệ, nhưng vẫn reachable từ GC roots qua host cache, worker TCCL hoặc listener. Bài lab tạo và dọn chính hai đường đầu, còn việc GC diễn ra lúc nào là vấn đề quan sát riêng.

## 3. GC roots và đường dẫn giữ loader

Để kết luận leak, cần tìm **đường tham chiếu mạnh từ GC root** tới loader hoặc tới class/object do loader định nghĩa.

Ví dụ 1 — Registry của host:

~~~text
GC Root
   |
   v
java.lang.Class<host.LeakLab>
   |
   v
static CACHE: Map<String, Class<?>>
   |
   v
Class<plugin.Greeter> (do loader L1 định nghĩa)
   |
   v
Defining ClassLoader L1
~~~

Đường trên minh họa retention qua class metadata. Trên heap dump, liên kết thực tế từ Class tới loader có thể được công cụ hiển thị khác nhau tùy JVM; hãy dựa vào **path to GC roots** và class-loader grouping, không giả định tên field nội bộ cố định.

Ví dụ 2 — Thread context loader:

~~~text
GC Root
   |
   v
Worker Thread trong pool sống lâu
   |
   v
Thread.contextClassLoader
   |
   v
URLClassLoader L1
   |
   v
Các class/resources của plugin đã định nghĩa
~~~

Nếu host đổi TCCL của thread thành plugin loader rồi **quên khôi phục**, thread pool có thể giữ loader đó suốt tuổi thọ ứng dụng.

Ví dụ 3 — Callback/listener:

~~~text
Host singleton EventBus
   |
   v
List<Listener>
   |
   v
plugin.ListenerImpl
   |
   v
Class<plugin.ListenerImpl>
   |
   v
Plugin ClassLoader
~~~

Ngay cả nếu không giữ trực tiếp `ClassLoader`, một plugin instance hoặc `Class<?>` cũng có thể giữ cho loader reachable. Đây là lý do câu hỏi điều tra phải tìm **toàn bộ reference chain**, không chỉ grep mã có biến `ClassLoader`.

## 4. Chuẩn bị bài thực hành Java 17+

Bài dùng đúng **hai file source**, không cần thư viện bên ngoài JDK:

- `plugin.Greeter`: class plugin có phương thức static để chứng minh code được nạp.
- `LeakLab`: host tạo child `URLClassLoader`, lưu `Class<?>` vào static registry, gán loader làm TCCL trên một worker thread trong pool, đóng loader, rồi cleanup.

Mã chạy trên Java 17+.

### Cấu trúc thư mục

~~~text
example/
├── src/
│   ├── plugin/
│   │   └── plugin/
│   │       └── Greeter.java
│   └── host/
│       └── LeakLab.java
└── out/
    ├── plugin/                 (tạo sau compile)
    │   └── plugin/Greeter.class
    └── host/                   (tạo sau compile)
        └── LeakLab.class
~~~

**Điều kiện rất quan trọng:** application classpath khi chạy **chỉ chứa `out/host`**. Không đặt `out/plugin` lên classpath của host, nếu không parent loader có thể nạp `plugin.Greeter` trước và phá điều kiện thí nghiệm.

### File src/plugin/plugin/Greeter.java

~~~java
package plugin;

public final class Greeter {
    public static String greet() {
        return "hello-plugin";
    }
}
~~~

Class được khai báo public, có một public static method để host gọi bằng reflection; plugin không cần dùng trực tiếp bất kỳ API riêng của host.

### File src/host/LeakLab.java

~~~java
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LeakLab {
    private static final Map<String, Class<?>> CACHE = new HashMap<>();

    private static WeakReference<ClassLoader> loadAndRetain(
            Path root, ExecutorService pool) throws Exception {
        URL source = root.toAbsolutePath().toUri().toURL();

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{source}, LeakLab.class.getClassLoader())) {

            Class<?> type = loader.loadClass("plugin.Greeter");

            // Đường giữ loader #1: registry sống lâu của host.
            CACHE.put("old-plugin", type);

            // Đường giữ loader #2: thread trong pool giữ TCCL.
            pool.submit(() ->
                    Thread.currentThread()
                            .setContextClassLoader(loader)).get();

            System.out.println("plugin defined by child: "
                    + (type.getClassLoader() == loader));
            System.out.println("plugin says: "
                    + type.getMethod("greet").invoke(null));

            return new WeakReference<ClassLoader>(loader);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "plugin classes root needed");
        }

        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Thread worker =
                    pool.submit(Thread::currentThread).get();
            ClassLoader original =
                    worker.getContextClassLoader();

            WeakReference<ClassLoader> weak =
                    loadAndRetain(Path.of(args[0]), pool);

            System.out.println("registry holds Class: "
                    + CACHE.containsKey("old-plugin"));

            System.out.println("worker TCCL points to loader: "
                    + (worker.getContextClassLoader() == weak.get()));

            System.out.println("closed loader still reachable: "
                    + (weak.get() != null));

            // Retire plugin: xóa những reference đã xác định.
            CACHE.clear();

            pool.submit(() -> Thread.currentThread()
                    .setContextClassLoader(original)).get();

            System.out.println("registry cleaned: "
                    + CACHE.isEmpty());
            System.out.println("worker TCCL restored: "
                    + (worker.getContextClassLoader() == original));

            // Không kiểm thử tính kịp thời hoặc bảo đảm của GC.
            System.out.println("class unloading guaranteed: false");
        } finally {
            pool.shutdown();
        }
    }
}
~~~

### Tại sao ví dụ được thiết kế như vậy?

1. `URLClassLoader` nhận **loader của host làm parent** và URL của thư mục chứa plugin class. Host không có `plugin.Greeter`, nên child thực sự là **defining loader**.
2. `loadAndRetain` lưu `Class<?>` của plugin trong `CACHE`, một static Map của class thuộc application loader sống lâu.
3. Một task trong single-thread executor cố ý gán TCCL của worker thread thành child loader; task kết thúc nhưng **thread trong pool vẫn tồn tại**.
4. `try-with-resources` đóng URLClassLoader khi rời phương thức; nhưng cache và TCCL **vẫn** giữ loader.
5. `WeakReference<ClassLoader>` cho phép thí nghiệm hỏi tham chiếu lúc đang được giữ mà không tự thêm một **strong reference từ WeakReference**. Tuy nhiên, việc gọi `weak.get()` ở một thời điểm không tự chứng minh GC đã chạy; ta chỉ dựa vào các strong references nhìn thấy bằng code.
6. `CACHE.clear()` xóa một đường giữ; gán lại TCCL cũ trên **đúng worker thread** xóa đường thứ hai.
7. `pool.shutdown()` bắt đầu quá trình đóng thread pool trong khối finally. Trong ứng dụng thực, cần thêm timeout/awaitTermination theo lifecycle nếu tác vụ bất đồng bộ vẫn chạy.
8. Chương trình **không khẳng định** class unloading đã xảy ra ngay sau cleanup. Không có vòng lặp `System.gc()` chờ đến khi weak reference trở thành null.

### Compile và chạy trên Linux/macOS

Đứng trong thư mục `example`:

~~~bash
mkdir -p out/plugin out/host

javac --release 17 -d out/plugin \
    src/plugin/plugin/Greeter.java

javac --release 17 -d out/host \
    src/host/LeakLab.java

java -cp out/host LeakLab out/plugin
~~~

### Compile và chạy trên Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force out\plugin, out\host |
    Out-Null

javac --release 17 -d out\plugin src\plugin\plugin\Greeter.java
javac --release 17 -d out\host src\host\LeakLab.java

java -cp out\host LeakLab out\plugin
~~~

Lưu ý `src/plugin/plugin/Greeter.java` có hai thư mục mang tên plugin do cách tổ chức source root và package; output binary name vẫn chỉ là `plugin.Greeter`.

### Stdout chính xác đã được kiểm chứng

~~~text
plugin defined by child: true
plugin says: hello-plugin
registry holds Class: true
worker TCCL points to loader: true
closed loader still reachable: true
registry cleaned: true
worker TCCL restored: true
class unloading guaranteed: false
~~~

Mã nguồn đầy đủ đã được biên dịch bằng `javac --release 17`, thực thi trên JDK và so khớp chính xác tám dòng stdout.

**Diễn giải:**

- **plugin defined by child: true**: điều kiện experiment đúng, class do URLClassLoader riêng định nghĩa.
- **plugin says: hello-plugin**: class plugin hoạt động và được gọi qua reflection.
- **registry holds Class: true**: static registry của host đang giữ Class của plugin.
- **worker TCCL points to loader: true**: một thread sống lâu vẫn giữ trực tiếp plugin loader.
- **closed loader still reachable: true**: dù `try-with-resources` đã gọi `close()`, tham chiếu tới loader vẫn còn.
- **registry cleaned: true**: host đã loại bỏ Class khỏi registry.
- **worker TCCL restored: true**: TCCL trên đúng worker thread đã được khôi phục về giá trị cũ.
- **class unloading guaranteed: false**: sau cleanup, chương trình không khẳng định JVM đã GC/unload class.

Thí nghiệm này chỉ chứng minh **nguồn retention và hành động cắt hai đường retention chủ động**. Không nên dùng nó như benchmark metaspace, kiểm thử memory leak tự động hoặc cam kết class unloading thời gian thực.

## 5. Vì sao close() rồi vẫn còn rò rỉ?

`URLClassLoader.close()` có phạm vi rõ: nó chấm dứt khả năng nạp class/resource mới **do loader tự định nghĩa**, cố gắng đóng file handles mà nó mở, và không hủy classes/objects đã nạp. Parent loader vẫn có thể cung cấp classes của parent theo cơ chế delegation.

Các lời gọi sau **không đồng nghĩa**:

~~~java
loader.close();       // Đóng tài nguyên của URLClassLoader.
loader = null;        // Bỏ một biến strong reference cụ thể.
System.gc();          // Gợi ý GC, không có timing bảo đảm.
~~~

Đây là đoạn **minh họa**: biến `loader` phải không phải `final` và có thể ném `IOException`. Kể cả sau khi biến cục bộ bằng null, các đường như `CACHE → Class<?> → loader` hoặc `Thread → contextClassLoader → loader` vẫn tồn tại.

Nếu bạn thấy lỗi không xóa được JAR trên Windows, có thể là leak handle chứ không phải heap/class metadata. `JarURLConnection` cache, một `JarFile`/`ZipFile` mở độc lập, custom loader/framework hoặc process khác cũng có thể giữ file. Phải điều tra **ai sở hữu handle**, không suy từ metaspace.

### Rò rỉ loader và rò rỉ heap objects có quan hệ gì?

Loader còn reachable có thể làm những class metadata tương ứng còn sống. Nhưng không phải mọi instance có type thuộc loader đều sẽ được giữ chỉ vì loader tồn tại; object thường vẫn được GC theo reachability riêng. Ngược lại, một **instance** còn được host giữ là đủ tạo một đường giữ runtime class/loader.

Không nên gán một chiều giản đơn “loader giữ tất cả object từng được tạo” hoặc “GC thu object thì loader chắc chắn được unload”. Đây là hai tập điều kiện reachability cần phân tích bằng GC roots.

## 6. Quy trình điều tra thực tế từng bước

Một cuộc điều tra production cần dữ kiện trước/sau và kết luận có thể kiểm chứng, không chỉ cảm giác “metaspace tăng”.

### Bước 1 — Xác định triệu chứng và baseline

Ghi lại ít nhất:

- JDK version/build, GC, JVM flags, max metaspace nếu có.
- Số lần plugin reload và số plugin versions **đáng lẽ** còn hoạt động.
- Số loader classes đang sống theo type/identity, không chỉ số instances `URLClassLoader` đã tạo từ đầu.
- Loaded/unloaded class counts (nếu có), metaspace used/committed, heap memory và GC activity.
- Các phiên plugin đã ngừng nhưng vẫn có references nào được biết trong host registry/task scheduler.

Ví dụ bảng quan sát:

| Thời điểm | Loader version đang phục vụ | Loader retired còn thấy | Dấu hiệu cần điều tra |
| --- | --- | --- | --- |
| Trước reload | v1 | 0 | Baseline |
| Sau chuyển sang v2 | v2 | v1 tạm thời | Có thể bình thường khi request v1 chưa xong |
| Sau khi v1 đã stop/retire | v2 | v1 vẫn reachable | Cần tìm GC root paths |
| Sau nhiều lượt reload | v10 | v1…v9 vẫn giữ | Mạnh hơn về bằng chứng retention bất thường |

Bảng chỉ là **kịch bản giả định**, không phải số đo hệ thống hiện có. Đừng coi việc loader v1 xuất hiện ngay sau cutover là leak nếu vẫn còn request hợp lệ đang chạy.

### Bước 2 — Khẳng định định danh class loader

Từ class plugin:

~~~java
Class<?> type = pluginInstance.getClass();
System.out.println(type.getName());
System.out.println(type.getClassLoader());
System.out.println(type.getModule());
~~~

Đây là code minh họa trong phương thức có `pluginInstance`. `getClassLoader()` thường quan trọng hơn tên class string. Hai class có binary name giống nhau nhưng defining loaders khác có thể là hai phiên khác nhau. Nếu loader thực tế là application loader (do plugin JAR nằm trên host classpath), khả năng retire từng phiên riêng sẽ không đúng như kiến trúc mong đợi.

### Bước 3 — Thu thập công cụ JVM thích hợp

Trên HotSpot JDK 21 có thể dùng `jcmd`. Trước tiên tìm PID hoặc gọi help để xác minh lệnh cụ thể của JVM:

~~~bash
jcmd -l
jcmd <pid> help
jcmd <pid> VM.classloader_stats
jcmd <pid> VM.classloaders
jcmd <pid> VM.metaspace basic
~~~

Những dòng trên là **mẫu shell với `<pid>` phải thay bằng PID thật**, không phải chương trình chạy nguyên xi. `jcmd` thường phải chạy cùng máy và quyền user/group thích hợp với process đích.

Chức năng chính:

- `VM.classloader_stats`: thống kê loader/class metadata để xem quy mô các class loaders đang tồn tại.
- `VM.classloaders`: xem class loader hierarchy; một số options hỗ trợ hiển thị classes cụ thể.
- `VM.metaspace`: kiểm tra metaspace usage, có tùy chọn bổ sung tùy JVM.
- `GC.heap_dump`: tạo heap dump để phân tích refs; có thể ảnh hưởng đáng kể đến vận hành, dung lượng đĩa và pause.
- `GC.class_histogram`: thống kê đối tượng trên heap, nhưng không thay thế path-to-root analysis.

**Cảnh báo vận hành:** heap dump thường chứa dữ liệu ứng dụng nhạy cảm như token, thông tin người dùng hoặc payload. Lưu trong vị trí bảo vệ truy cập, không gửi dump lên nơi công khai và áp dụng retention/deletion policy thích hợp. Không chạy lệnh nặng trên production ở giờ tải cao mà không đánh giá impact.

### Bước 4 — Thu heap dump đúng cách

Sau khi xác định PID và vị trí ghi phù hợp:

~~~bash
jcmd <pid> GC.heap_dump /secure/path/after-reload.hprof
~~~

Đây là mẫu lệnh; hãy kiểm tra help theo JVM và đảm bảo đủ không gian đĩa/quyền ghi. Một heap dump có thể rất lớn, gây pause/impact, và cách JVM xử lý GC trước dump phụ thuộc options.

Nếu cần so sánh retention, có thể chụp trước và sau nhiều lượt reload với quy trình lặp được kiểm soát. Không so hai dump khác workload rồi kết luận leak chắc chắn chỉ từ số bytes tăng.

### Bước 5 — Phân tích “Path to GC Roots”

Mở dump trong Eclipse Memory Analyzer Tool (MAT) hoặc công cụ heap analysis tương thích. Tìm:

1. Instances `java.net.URLClassLoader` hoặc subclass custom của plugin loader.
2. Những loader có tên/identifier của **version đã retired**.
3. Classes được định nghĩa bởi loader đó, ví dụ `plugin.Greeter`.
4. **Paths to GC Roots**, ưu tiên strong references (có thể chọn *exclude weak/soft/phantom references* khi phân tích nguyên nhân mạnh).
5. Retained set/dominator tree để đánh giá object graph bị giữ.
6. Các nút là host static field, Thread.contextClassLoader, ThreadLocal, listener, logger, scheduler hoặc cache.

Với bài mẫu, hai nghi phạm là:

~~~text
GC Root -> Host class -> static CACHE
                      -> Class<plugin.Greeter>
                      -> URLClassLoader old

GC Root -> executor worker Thread
                      -> contextClassLoader
                      -> URLClassLoader old
~~~

Một snapshot có thể không cho thấy đường nối chính xác như sơ đồ giản lược trên do cách JVM/MAT biểu diễn Class mirror và native metadata; yếu tố quyết định là **đường giữ loader từ roots**, không phải tên field nội bộ cố định.

### Bước 6 — Tìm owner của reference

Không chỉ thấy root “Thread” rồi xóa tùy tiện:

- Thread này do **host** hay plugin tạo?
- Nếu host thread pool, task nào đã đổi TCCL? Có thể khôi phục bằng một hành động chạy trên chính worker không?
- Registry được host cache để lookup class/provider theo key gì? Có xóa entry theo version lúc retire không?
- Listener nào đã được đăng ký nhưng không unregister?
- ThreadLocal được gán trên thread nào và đã remove trong đúng thread chưa?
- Có app/framework static field giữ `Method`, `Constructor`, proxy, `Class<?>`, service instance hoặc lambda của plugin không?

Cần chỉ ra **đối tượng sở hữu**, hành vi tạo tham chiếu, thời điểm phải dọn và phép kiểm tra sau khi dọn.

### Bước 7 — Sửa và xác minh qua nhiều chu kỳ

Khi đã xác định root:

1. Thêm cleanup vào plugin lifecycle: `start → running → quiesce → stop → close → retire`.
2. Dừng nhận requests mới từ plugin cũ, đợi requests đang chạy xong.
3. Unregister callbacks, cancel tasks, shutdown executors, đóng streams/JarFile.
4. Xóa host registry/cache entries theo version, không phá bản mới.
5. Khôi phục TCCL trong `finally` cho các threads mà app đã thay đổi.
6. Đóng URLClassLoader (cần tránh concurrent class loading).
7. Bỏ strong references tới old `Class<?>`/plugin object/loader.
8. Chạy chuỗi reload lặp với tải đại diện, đối chiếu loader counts/metaspace và paths-to-roots.
9. Không đặt điều kiện pass là “GC phải unload ngay trong 100 ms”; nên kiểm tra **tính đúng của cleanup và xu hướng dữ liệu**.

Khi cần dùng phiên bản cũ song song để hoàn thành request, giữ loader cũ có chủ đích trong giai đoạn drain là đúng; chỉ label retired sau khi hợp đồng lifecycle kết thúc.

## 7. Dùng WeakReference đúng mục đích

`WeakReference<ClassLoader>` thường được dùng trong test để quan sát loader có thể được JVM thu gom sau khi các đường strong reference đã biến mất.

~~~java
WeakReference<ClassLoader> weak =
        new WeakReference<>(pluginLoader);
~~~

Đây là đoạn minh họa. **WeakReference không giữ strong reference tới referent**, nhưng nếu biến `pluginLoader`, `Class<?>` hay một thread vẫn giữ loader thì `weak.get() != null` là điều bình thường.

Cẩn trọng:

- `weak.get() != null` trước cleanup **không cần gọi GC** mới chứng minh rằng loader vẫn đang reachable, nếu còn mạnh references rõ ràng.
- `weak.get() == null` sau một lần GC có thể hỗ trợ quan sát rằng referent đã được thu gom, nhưng không phải phép đo tổng memory đã trở về baseline.
- `weak.get() != null` ngay sau cleanup **không chứng minh leak**. GC chưa chắc chạy, hoặc một strong reference tạm từ stack/debugger/test vẫn tồn tại.
- Không viết vòng lặp vô hạn `while (weak.get() != null) System.gc();`.
- Nếu cần integration test, đặt timeout rộng/có kiểm soát, loại bỏ strong refs trong một helper scope, và kết hợp heap analysis khi fail thay vì assertion tuyệt đối dựa trên timing GC.
- Dùng `ReferenceQueue` nếu muốn quan sát thông báo khi JVM xử lý tham chiếu yếu, nhưng cũng không nhận được lời hứa thời điểm xử lý cụ thể.

Ở chương trình mẫu, ta cố ý **không** gọi GC, để người học tập trung vào hai root paths do host tạo ra và việc cleanup xác định.

## 8. Những nguồn leak thường gặp hơn chỉ một static Map

| Điểm giữ tham chiếu | Một đường root hay gặp | Biện pháp cleanup |
| --- | --- | --- |
| Host static cache `Class<?>`/`Method`/`Constructor` | Static field → reflective metadata → class/loader | Clear cache theo plugin version; tránh global unbounded map. |
| Thread context class loader | Thread pool worker → TCCL → loader | Lưu và restore TCCL trong finally trên đúng thread. |
| ThreadLocal | Worker thread → ThreadLocalMap → plugin object | Remove/clear trong đúng thread, đừng chỉ null biến ở request khác. |
| Listener/event bus | Host singleton → listener implementation của plugin | Unregister bằng registration token trong stop hook. |
| Executor/scheduled task | Queue/task/runnable → plugin class/object | Cancel futures, shutdown executor, drain tasks. |
| Thread do plugin tự tạo | Live Thread → Runnable/context loader | Stop bằng protocol có kiểm soát và join/await termination. |
| JDBC driver registration | Global DriverManager registry → driver từ plugin | Deregister driver đúng owner khi retire, nếu phù hợp. |
| MBean/JMX | MBeanServer → MBean instance plugin | Unregister name lúc plugin stop. |
| Logger/appender/callback | Logger context/listener → plugin object | Gỡ plugin appender/handler/callback bằng API chính thức. |
| SPI provider caching | Long-lived ServiceLoader/list → provider Class hoặc instance | Bỏ registry/loader hoặc tạo ServiceLoader mới theo phiên. |
| Java shutdown hooks | Runtime hooks → Thread/lambda plugin | Remove hook khi còn được phép và lifecycle hợp lệ. |
| Heap/native resources | Resource stream hoặc JarFile/ZipFile | Đóng đúng owner; memory leak và file-handle leak cần phân tích riêng. |

Đặc biệt, **static field của plugin** thường bị hiểu lầm: một object graph chỉ nằm hoàn toàn bên trong plugin loader, không có root từ host hay external thread, có thể cùng trở thành unreachable và được thu gom. Ngược lại, chỉ một reference từ host static field vào plugin object/class cũng có thể giữ cả thế hệ loader.

## 9. Phân biệt tăng metaspace bình thường với leak thật

Đánh giá thận trọng:

| Quan sát | Kết luận hợp lý |
| --- | --- |
| Metaspace used tăng sau request đầu | Có thể là class loading bình thường. |
| Metaspace committed chưa giảm sau GC | Không tự chứng minh loader vẫn reachable; JVM quản lý vùng đã commit riêng. |
| URLClassLoader.close() đã gọi | Chứng minh cleanup resource đã được yêu cầu, **không** chứng minh class unloading. |
| Heap dump thấy nhiều retired loaders cùng strong GC root paths | Bằng chứng mạnh về retention bất thường nếu các phiên đã ngừng đúng nghĩa. |
| Số loader sống tăng gần tuyến tính qua nhiều lần reload đã drain | Dấu hiệu điều tra leak đáng tin cậy hơn một snapshot. |
| GC/class unloading logs cho thấy loader cũ được thu gom | Hỗ trợ kết luận đã có unloading trong lần đó; không bảo đảm reload lần sau cũng giống hệt. |
| File JAR không xóa được trên Windows | Phải điều tra file handles, không kết luận ngay là metaspace leak. |

Trong đánh giá production, đo **used vs committed metaspace**, number of classes/loader instances, heap-retained objects, GC behavior và plugin lifecycle. Không dùng một con số metaspace ngẫu nhiên làm ngưỡng chung cho mọi workload.

## 10. Quy trình sửa code trong host/plugin

### Không rò rỉ TCCL: luôn dùng finally

~~~java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);
    // Gọi code/framework có hợp đồng cần TCCL của plugin.
} finally {
    thread.setContextClassLoader(previous);
}
~~~

Đây là snippet minh họa với `pluginLoader` đã tạo. Nó đúng khi tác vụ được xử lý trong một worker thread: **khôi phục trong chính thread đã đổi** trước khi trả thread về pool.

Đừng từ thread quản trị cố xóa ThreadLocal values ở thread khác bằng cách sửa những field private JVM; thay vào đó xây lifecycle/task cleanup hợp lệ trên thread sở hữu.

### Không rò rỉ registry: remove theo key version

~~~java
Map<PluginId, PluginHandle> handles = ...;

// Khi retire đúng phiên plugin:
PluginHandle old = handles.remove(retiredPluginId);
if (old != null) {
    old.stopAndClose();
}
~~~

Đây là snippet minh họa trong một host có các kiểu `PluginId` và `PluginHandle` do ứng dụng định nghĩa. Nếu vừa chạy v1 và v2, phải remove **đúng version**, không gọi clear toàn bộ rồi vô tình xóa plugin đang phục vụ.

### Lifecycle được quản lý

Một `PluginHandle` điển hình nên sở hữu loader, instance/provider, subscriptions, tasks và tài nguyên riêng. Tạo API:

~~~text
start()
  -> acceptRequests()

quiesce()
  -> stopAcceptingNewRequests()
  -> awaitInFlightRequests()

stop()
  -> unregisterListeners()
  -> cancelJobs()
  -> clearCaches()
  -> restoreThreadContexts()
  -> closeStreamsAndJars()
  -> closeClassLoader()

retire()
  -> removeHostStrongReferences()
~~~

Đây là sơ đồ **logic**, không phải code Java thực thi. Mục tiêu là ownership rõ và hành động idempotent khi stop/cleanup có exceptions.

Nếu plugin có lazy dependencies, **không** đóng loader trước khi các requests đang chạy hoàn tất. Với URLClassLoader, việc một thread gọi `close()` khi thread khác đang load class có kết quả không xác định theo tài liệu API.

### Classloader riêng không phải sandbox bảo mật

Đừng nhầm cô lập class namespaces với cô lập quyền. Một `URLClassLoader` riêng **không** làm code plugin mất quyền filesystem/network/process của JVM theo mặc định. Nếu phải chạy code không tin cậy, cân nhắc process/container isolation và những kiểm soát ngoài cơ chế nạp lớp.

## 11. Cách tạo một test regression đáng tin hơn

Một test chống rò rỉ nên có ít nhất hai lớp kiểm tra:

**Lớp chức năng, tất định:**

- Plugin được child loader define như mong đợi.
- Plugin operation chạy đúng.
- Khi phiên được đánh dấu retired, registry/callback entries được remove.
- TCCL của mọi host worker bị tác động được restore.
- Thread/executor do plugin tạo được stop/shutdown.
- Các resource owners gọi close theo hợp đồng; exceptions được log/propagate.

**Lớp khả năng unload, quan sát JVM:**

- Chạy nhiều lượt reload trong isolated test process.
- Không giữ `Class<?>` hoặc loader ở biến test/static cache quá tuổi thọ phiên.
- Quan sát WeakReference/ClassLoadingMXBean/logs/heap snapshot **theo giới hạn và điều kiện thực tế**.
- Nếu loader chưa được thu gom, lấy heap dump và path-to-roots thay vì kết luận mù.
- Không viết assertion “loader phải GC trong X ms” như quy tắc JVM phổ quát.

Một cấu trúc test data:

~~~text
Iteration 1: load v1 -> use -> quiesce -> close -> cleanup
Iteration 2: load v2 -> use -> quiesce -> close -> cleanup
Iteration 3: load v3 -> use -> quiesce -> close -> cleanup
...
Compare number of intentionally active plugin versions and reachable retired loaders
~~~

Nếu số retired loaders reachable tiếp tục tăng sau cleanup, mới đào sâu root paths. Nếu count giảm nhưng metaspace committed chưa giảm, phân biệt chính sách cấp phát và fragmentation với retention.

### Vì sao không dùng một test chỉ gọi System.gc()?

`System.gc()` là yêu cầu gợi ý; một runtime có thể bỏ qua hoặc xử lý theo chính sách khác. Các references trên stack, JIT scopes, debugger và những biến tạm trong test còn có thể giữ loader sống. Không dùng một kết quả “weak.get vẫn khác null” đơn lẻ để kết luận application có leak.

## 12. Case study: kết luận nguyên nhân và hành động

### Triệu chứng

Sau mỗi lần reload plugin, class loader thế hệ trước vẫn có trong heap dump; số lượng retired loaders tăng dù plugin manager đã gọi `close()`.

### Bằng chứng trong bài lab

- Một cache trong host giữ `Class<plugin.Greeter>`.
- Một thread của executor giữ child loader làm TCCL.
- Loader đã `close()` nhưng hai đường retention không bị tự động xóa.
- Cleanup code chủ động `CACHE.clear()` và restore TCCL ở worker thread.

### Root cause

**Vòng đời reference dài hơn vòng đời plugin**. Host quên thực hiện cleanup các registration/context sau khi version cũ retired. Không phải do JVM nạp lại cùng tên class là bug, cũng không phải vì GC “bị hỏng”.

### Sửa lỗi

- Registry/cache theo phiên, clear khi phiên nghỉ.
- TCCL được thay tạm trong scope có `try/finally`, không để lại trong thread pool.
- Plugin lifecycle đảm bảo quiesce, stop tasks, unregister, đóng resources rồi mới retire loader.
- Có regression test cho đường retention, không chỉ test rằng `close()` được gọi.

### Xác nhận sau sửa

Các đường strong-reference do host kiểm soát được cắt; ứng dụng có thể tiếp tục đo sau nhiều lượt reload xem class loaders cũ còn reachable từ GC roots nào khác. **Không tự khẳng định GC đã unload**, vì JVM không bảo đảm thời điểm.

## 13. Các lỗi chẩn đoán phổ biến

| Sai lầm | Vì sao sai | Cách đúng |
| --- | --- | --- |
| Thấy metaspace tăng một lần rồi gọi là leak | Class loading/JIT và allocation policy có thể tăng bình thường | So trend và retired loaders reachable qua nhiều chu kỳ |
| Cho rằng `URLClassLoader.close()` tự unload class | Close quản lý lookup/resource handles, không buộc GC | Cắt references rồi quan sát JVM |
| Dùng `System.gc()` như fix production | Không giải quyết GC root giữ loader | Tìm owner và clear references |
| Không phân biệt Class và class name | Hai loaders có thể define cùng binary name | In `Class.getClassLoader()`, compare runtime Class |
| Chỉ đọc exception hoặc log tên loader | Log có thể mơ hồ | Dùng heap dump + paths-to-GC-roots |
| Dọn static cache nhưng quên TCCL | Thread pool tiếp tục giữ loader | Khôi phục context trên đúng worker |
| Dùng `ThreadLocal.remove()` trong sai thread | ThreadLocalMap nằm trong từng thread | Dọn từ thread sở hữu hoặc kết thúc thread đúng cách |
| Thấy WeakReference không null ngay sau cleanup | Chưa chắc JVM đã chạy GC | Không kết luận từ timing thiếu bảo đảm |
| Heap dump đưa công khai để hỏi lỗi | Dump có thể chứa secrets/user data | Bảo vệ file, giới hạn truy cập và retention |
| Đóng loader khi vẫn có request in flight | Concurrent load/close không được đảm bảo | Quiesce và đợi trước close |
| Xóa JAR thất bại rồi quy lỗi metaspace | File handle leak là tài nguyên khác | Theo dõi open handles và chủ sở hữu thật |
| Chuyển mọi class vào system loader để tránh unload issue | Làm mất khả năng retire classes riêng, dễ xung đột version | Dùng loader per version và shared API parent |

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Đã gọi `URLClassLoader.close()`, liệu loader chắc chắn không còn trong heap?

**Đáp:** Không. Class/objects đã loaded có thể vẫn được host/thread/registry giữ; `close()` không xóa strong references.

**Câu 2.** Vì sao static `Map<String, Class<?>>` của host có thể giữ cả plugin loader?

**Đáp:** Map giữ Class của plugin, Class gắn với runtime type được defining loader định nghĩa; đường root từ host static cache có thể làm loader reachable.

**Câu 3.** Vì sao TCCL trên worker thread là một điểm nghi vấn?

**Đáp:** Worker thread sống lâu hơn một request/plugin version; contextClassLoader là strong reference tới plugin loader nếu code gán nó và không restore.

**Câu 4.** Xóa cache và restore TCCL có chứng minh class đã unload ngay không?

**Đáp:** Không. Đó là dọn hai root paths đã biết; còn có thể có đường khác, và JVM/GC không có cam kết unload tức thì.

**Câu 5.** WeakReference vẫn trả loader ngay sau cleanup có luôn nghĩa leak?

**Đáp:** Không. GC có thể chưa chạy, test/debugger còn giữ refs hoặc có roots khác. Cần xem heap/paths-to-root và lifecycle, không chỉ timing.

**Câu 6.** Công cụ nào giúp xem class loaders và GC roots?

**Đáp:** `jcmd VM.classloader_stats`/`VM.classloaders` hỗ trợ thống kê hierarchy (HotSpot); heap dump với MAT giúp lần theo GC root paths và dominators.

**Câu 7.** Tại sao không đóng loader ngay khi nhận lệnh reload?

**Đáp:** Các request đang chạy có thể còn cần lazy class/resource loading; concurrent close và load có behavior không được API đảm bảo. Phải quiesce/stop trước.

**Câu 8.** Thấy file JAR không thay được trên Windows có chắc là classloader leak trong metaspace?

**Đáp:** Không. Một file handle có thể do URLClassLoader, JarURLConnection, JarFile hoặc process khác giữ. File handle và reachability của class loader cần đo riêng.

## Tài liệu tham khảo

- [Java SE 21 — URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): close(), truy cập các class/resource đã nạp và concurrency.
- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): defining loader, delegation và runtime class identity.
- [Java SE 21 — Thread](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html): getContextClassLoader()/setContextClassLoader().
- [Java SE 21 — WeakReference](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ref/WeakReference.html): semantics của weak reachability.
- [Java SE 21 — jcmd command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html): VM.classloader_stats, VM.classloaders, VM.metaspace, GC.heap_dump.
- [JVMS Java SE 21 §5.3](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): class loading và defining loader.


</details>

- [Quay lại đầu trang](#back-to-top)
