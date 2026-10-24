<a id="back-to-top"></a>

# URLClassLoader

## Menu
- [Tạo URLClassLoader từ thư mục và JAR](#url-class-loader-001)
- [Chọn parent và phạm vi phụ thuộc được kế thừa](#url-class-loader-002)
- [Thứ tự URL và xử lý JAR hoặc lớp trùng nhau](#url-class-loader-003)
- [Nạp lớp và tài nguyên qua URLClassLoader](#url-class-loader-004)
- [getURLs và giới hạn của addURL](#url-class-loader-005)
- [Quản lý vòng đời bằng close và try-with-resources](#url-class-loader-006)
- [Close không đồng nghĩa với gỡ bỏ lớp đã nạp](#url-class-loader-007)
- [Ảnh hưởng của close tới JAR handle và thao tác tải đang diễn ra](#url-class-loader-008)
- [Giới hạn tìm nested JAR và khác biệt với launcher chuyên dụng](#url-class-loader-009)

## <a id="url-class-loader-001">Tạo URLClassLoader từ thư mục và JAR</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo URLClassLoader từ thư mục và JAR

## 1. Bài toán: bổ sung nguồn lớp lúc chạy

Khi chương trình Java đã khởi động, đôi khi nó phải nạp plugin, extension, driver hoặc một thư viện bên ngoài mà không muốn đặt toàn bộ class/JAR ấy trên application classpath ban đầu. **`java.net.URLClassLoader`** là một class loader có sẵn, biết tìm lớp và tài nguyên qua **danh sách URL** trỏ tới thư mục chứa các tệp `.class` hoặc tới JAR.

Một ví dụ phổ biến: ứng dụng host có `PluginHost.class`, còn plugin `sample.Greeting` được biên dịch ra `out/plugin/sample/Greeting.class` hoặc được đóng gói thành `dist/plugin.jar`. Host tạo `URLClassLoader` với URL chỉ đúng **gốc package** hoặc **file JAR**, sau đó gọi `loadClass("sample.Greeting")`.

Các khái niệm:

- **URL search path**: các URL mà một `URLClassLoader` tìm kiếm **sau khi đã thử parent** theo chính sách parent-first mặc định.
- **Directory URL**: URL dùng lược đồ `file:`, kết thúc bằng dấu `/`, trỏ tới **thư mục gốc package**. Ví dụ `file:/home/user/app/out/plugin/`.
- **JAR URL**: thông thường là `file:` trỏ đến **chính file JAR**, chẳng hạn `file:/home/user/app/dist/plugin.jar`; `URLClassLoader` mở archive và tìm các entry theo đường dẫn package. API cũng hỗ trợ các URL thích hợp dùng giao thức `jar:`; trong tình huống file JAR thông thường, không cần tự ghép cú pháp `jar:file:...!/`.
- **Parent loader**: loader được hỏi trước. Thường nên dùng `PluginHost.class.getClassLoader()` để plugin có thể thấy các interface/API chung của ứng dụng.
- **Defining loader**: loader thực sự định nghĩa một lớp. Lấy bằng `SomeClass.class.getClassLoader()`; nó có thể khác đối tượng loader mà bạn gọi `loadClass` trên đó.

`URLClassLoader` hỗ trợ các lớp/tài nguyên trên classpath kiểu truyền thống. Nó **không** tự thiết lập một Java Platform Module System layer, không tự tìm mọi JAR nằm lồng bên trong một JAR khác, và không tự cấp môi trường sandbox bảo mật cho bytecode không tin cậy.

## 2. Cấu trúc URL và đường dẫn package phải đúng

Giả sử tên nhị phân của plugin là `sample.Greeting`. Tệp class tương ứng có đường dẫn `sample/Greeting.class` bên dưới **gốc** tìm kiếm.

### Trường hợp thư mục

~~~text
out/
└── plugin/                  <-- URL phải trỏ tới đây
    └── sample/
        ├── Greeting.class
        └── message.txt
~~~

URL hợp lệ theo ý nghĩa:

~~~text
file:/project/out/plugin/
~~~

`URLClassLoader` đổi tên nhị phân `sample.Greeting` thành đường dẫn tương ứng `sample/Greeting.class` và tìm bên dưới thư mục root. **Không** dùng `out/plugin/sample/` làm URL root nếu `sample` chính là package: khi đó loader sẽ tìm sai thành `.../sample/sample/Greeting.class`.

Theo Javadoc `URLClassLoader`, **URL `file:` kết thúc bằng `/` được coi là thư mục**; các URL `file:` khác được coi là tệp JAR để mở khi cần. Vì vậy dấu slash cuối cùng của URL thư mục rất quan trọng.

Để tạo URL một cách portable, ưu tiên:

~~~java
Path directory = Path.of("out", "plugin")
        .toAbsolutePath().normalize();
URL directoryUrl = directory.toUri().toURL();
~~~

Đoạn trên giả định thư mục **đã tồn tại**, vì cách `Path.toUri()` hình thành URL thư mục dựa vào thông tin filesystem; trong ví dụ đầy đủ ở phần 5, thư mục được tạo trước. Tránh tự nối chuỗi kiểu `"file://" + path` hoặc thay `\` bằng `/` thủ công; trên Windows, khoảng trắng, dấu ổ đĩa và ký tự đặc biệt cần được URI/URL encode đúng.

### Trường hợp JAR

~~~text
dist/
└── plugin.jar
    ├── META-INF/MANIFEST.MF
    └── sample/
        ├── Greeting.class
        └── message.txt
~~~

URL trỏ tới **file archive**:

~~~java
Path jarFile = Path.of("dist", "plugin.jar")
        .toAbsolutePath().normalize();
URL jarUrl = jarFile.toUri().toURL();
~~~

Ví dụ URL có thể hiển thị là `file:/project/dist/plugin.jar`; nó **không** kết thúc bằng `/`. `URLClassLoader` tự đọc các entry thích hợp từ JAR.

Không truyền `file:/project/dist/` rồi mặc định loader sẽ **tự quét mọi JAR** trong thư mục `dist`. Nếu đưa vào một directory URL thì nó dùng thư mục ấy làm nguồn các class file rời; các JAR muốn tham gia cần được liệt kê dưới dạng URL riêng hoặc thông qua metadata/thiết kế có hỗ trợ. Cú pháp wildcard như `lib/*` của Java launcher **không phải phép wildcard tự động của `new URLClassLoader(new URL[]{...})`**.

## 3. Khởi tạo URLClassLoader: constructor và parent

Các constructor thường dùng:

~~~java
URLClassLoader loader = new URLClassLoader(
        new URL[]{directoryUrl}, parentLoader);
~~~

~~~java
URLClassLoader loader = new URLClassLoader(
        new URL[]{jarUrl}, parentLoader);
~~~

Với nhiều nguồn, truyền một mảng URL:

~~~java
URLClassLoader loader = new URLClassLoader(
        new URL[]{directoryUrl, firstJarUrl, secondJarUrl},
        PluginHost.class.getClassLoader());
~~~

Đây là các **đoạn minh họa**, giả định mọi biến URL và `PluginHost` đã tồn tại; code chạy độc lập nằm ở phần 5. Danh sách URL được xét **theo thứ tự đã đưa**, nhưng chỉ sau khi parent được thử theo cách tìm mặc định.

| Constructor | Ý nghĩa |
| --- | --- |
| `new URLClassLoader(urls)` | Dùng parent mặc định theo cơ chế `ClassLoader`. |
| `new URLClassLoader(urls, parent)` | Chỉ rõ loader được ủy quyền trước. |
| `new URLClassLoader("plugin-loader", urls, parent)` | Đặt tên loader để hỗ trợ chẩn đoán (Java 9+). |
| `new URLClassLoader(urls, null)` | Parent delegation tương ứng bootstrap; **không** có nghĩa loader này là bootstrap và có thể làm thiếu các API không do bootstrap định nghĩa. |

**Khuyến nghị:** nếu host chia sẻ một interface, ví dụ `api.Plugin`, cho interface ấy nằm ở parent và cho child nạp lớp triển khai. Plugin dùng cùng `api.Plugin` mà host nhìn thấy, tránh hai bản định nghĩa interface có cùng tên nhưng khác defining loader.

### Parent-first không có nghĩa mọi lớp đều do parent định nghĩa

Mặc định `ClassLoader.loadClass`:

~~~text
Host yêu cầu loader.loadClass("sample.Greeting")
                      |
                      v
            Child đã có lớp?
                      |
                   Chưa có
                      |
                      v
        Parent.loadClass("sample.Greeting")
                 /           \
            Tìm được        Không thấy
                |               |
                v               v
        Trả Class parent   Child.findClass(...)
                                |
                         Tìm trong các URL
                                |
                         Định nghĩa class
~~~

Nếu parent đã có `sample.Greeting` trên application classpath, URLClassLoader con có thể trả **bản của parent**, chứ không định nghĩa bản bên trong JAR plugin. Muốn kiểm chứng lớp do đâu định nghĩa, dùng:

~~~java
Class<?> type = loader.loadClass("sample.Greeting");
boolean loadedByChild = type.getClassLoader() == loader;
~~~

Trong ví dụ độc lập bên dưới, plugin được cố ý **không** đặt trên classpath của host để phép kiểm chứng này luôn là `true`.

## 4. Hai API quan trọng: loadClass và tìm resource

### Nạp class theo binary name

~~~java
Class<?> type = loader.loadClass("sample.Greeting");
~~~

Tên phải ở dạng **binary name** (dấu `.` phân cách package). Không truyền `"sample/Greeting.class"` hay đường dẫn JAR vào `loadClass`. `loadClass` trả đối tượng `Class<?>`; bản thân lời gọi này không chủ động initialize static initializer của `Greeting`.

Để khởi tạo hoặc sử dụng kiểu động, có thể dùng reflection:

~~~java
String result = (String) type.getMethod("hello").invoke(null);
~~~

Đây là ví dụ gọi phương thức **static public `hello()`**, nên `Method.invoke(null)` hợp lệ. Lời gọi static method là active use; nếu class chưa khởi tạo thì JVM chạy khối static initializer trước khi thân method chạy. Với phương thức instance, phải tạo đối tượng hợp lệ và truyền instance thay vì `null`.

Nếu chỉ cần lấy đối tượng `Class<?>` mà chưa kích hoạt initialization, `loader.loadClass(name)` thường đúng mục đích. Còn `Class.forName(name, true, loader)` **chủ động yêu cầu initialization**, khác với `Class.forName(name, false, loader)`.

### Nạp resource qua URLClassLoader

URLClassLoader cũng có thể tìm resource trên cùng search path:

~~~java
URL configUrl = loader.getResource("sample/message.txt");
InputStream in = loader.getResourceAsStream("sample/message.txt");
~~~

Các tên resource của `ClassLoader` bắt đầu **từ gốc resource** và dùng dấu `/`; không thêm dấu `/` đầu giống kiểu absolute path của `Class.getResource`. Nếu không thấy, API có thể trả `null`.

Đọc dữ liệu bằng stream sẽ hoạt động tương tự khi resource nằm ở file trong thư mục hoặc entry bên trong JAR. Đặc biệt, không ép URL `jar:file:...!/sample/message.txt` thành một `java.io.File` trên ổ đĩa.

## 5. Thực hành Java 17+: cùng plugin trong thư mục và JAR

Bài thực hành có **hai tệp nguồn Java** ở hai phần độc lập: plugin được compile vào `out/plugin`, host được compile vào `out/host`. Sau đó đóng gói plugin thành `dist/plugin.jar`. Host chạy chỉ với `out/host` trên application classpath, nên plugin chỉ được tìm thấy bởi URLClassLoader do host tạo.

Cấu trúc làm việc:

~~~text
example/
├── src/
│   ├── plugin/
│   │   └── sample/Greeting.java
│   └── host/
│       └── PluginHost.java
├── out/
│   ├── plugin/          <-- tạo sau khi compile
│   └── host/            <-- tạo sau khi compile
└── dist/
    └── plugin.jar       <-- tạo sau khi đóng gói
~~~

### File `src/plugin/sample/Greeting.java`

~~~java
package sample;

public class Greeting {
    static {
        System.out.println("Greeting initialized");
    }

    public static String hello() {
        return "hello from plugin";
    }
}
~~~

Thông điệp trong khối static giúp quan sát mỗi lớp được **khởi tạo một lần đối với định nghĩa class cụ thể của nó**. Khi có hai loader định nghĩa cùng tên, mỗi bản `sample.Greeting` có quá trình khởi tạo riêng.

### File `src/host/PluginHost.java`

~~~java
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class PluginHost {
    private static void inspect(String label, URL url) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{url}, PluginHost.class.getClassLoader())) {
            Class<?> type = loader.loadClass("sample.Greeting");

            System.out.println(label + " owns class: "
                    + (type.getClassLoader() == loader));
            System.out.println(label + " message: "
                    + type.getMethod("hello").invoke(null));

            try (InputStream in =
                         loader.getResourceAsStream("sample/message.txt")) {
                if (in == null) {
                    throw new IllegalStateException("missing resource");
                }
                System.out.println(label + " resource: "
                        + new String(in.readAllBytes(),
                                StandardCharsets.UTF_8).trim());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("dir jar");
        }

        URL dir = Path.of(args[0]).toAbsolutePath()
                .normalize().toUri().toURL();
        URL jar = Path.of(args[1]).toAbsolutePath()
                .normalize().toUri().toURL();

        System.out.println("directory URL ends in /: "
                + dir.toExternalForm().endsWith("/"));
        System.out.println("JAR URL ends in .jar: "
                + jar.toExternalForm().endsWith(".jar"));

        inspect("directory", dir);
        inspect("JAR", jar);
    }
}
~~~

Điểm học tập trong mã:

1. `Path.of(args[0]).toAbsolutePath().normalize().toUri().toURL()` tạo URL từ đường dẫn thư mục được truyền.
2. `Path.of(args[1])...toURL()` tạo URL từ đường dẫn JAR được truyền.
3. Mỗi lần `inspect` tạo **một URLClassLoader mới**, có parent là loader của `PluginHost` và chỉ một nguồn URL.
4. `loader.loadClass("sample.Greeting")` tìm class trong parent trước; ở ví dụ này parent không có plugin nên child định nghĩa.
5. `type.getClassLoader() == loader` kiểm chứng defining loader thực tế.
6. Reflection `type.getMethod("hello").invoke(null)` kích hoạt static initialization khi cần, sau đó gọi method và trả chuỗi.
7. `getResourceAsStream("sample/message.txt")` tìm file văn bản từ cả hai nguồn. Code xử lý `null` và đóng stream.
8. `try (URLClassLoader loader = ...)` bảo đảm gọi `close()` khi rời block, kể cả khi exception xảy ra.

### Biên dịch và chạy trên Linux/macOS

Đứng trong thư mục `example` (đã tạo hai source Java ở trên):

~~~bash
mkdir -p out/plugin/sample out/host dist

javac -d out/plugin src/plugin/sample/Greeting.java
printf 'resource from plugin\n' > out/plugin/sample/message.txt

javac -d out/host src/host/PluginHost.java
jar --create --file dist/plugin.jar -C out/plugin .

java -cp out/host PluginHost out/plugin dist/plugin.jar
~~~

**Quan trọng:** chỉ truyền `out/host` qua `-cp` lúc chạy. Không thêm `out/plugin` hoặc `dist/plugin.jar` vào application classpath; nếu thêm, parent có thể nhìn thấy plugin trước và ví dụ sẽ không còn chứng minh child định nghĩa class.

### Biên dịch và chạy trên Windows PowerShell

Đứng trong thư mục `example`:

~~~powershell
New-Item -ItemType Directory -Force out\plugin\sample, out\host, dist | Out-Null

javac -d out\plugin src\plugin\sample\Greeting.java
Set-Content -Path out\plugin\sample\message.txt -Value "resource from plugin" -Encoding Ascii

javac -d out\host src\host\PluginHost.java
jar --create --file dist\plugin.jar -C out\plugin .

java -cp out\host PluginHost out\plugin dist\plugin.jar
~~~

Ở Windows, `Path.toUri().toURL()` xử lý đường dẫn ổ đĩa, dấu cách và các ký tự URI phù hợp; bạn không cần tự tạo URL chuỗi bằng cách ghép `file:///C:/...`. Trong ví dụ, resource chỉ có ký tự ASCII để kết quả giống nhau trên các shell.

### Stdout đã kiểm chứng

~~~text
directory URL ends in /: true
JAR URL ends in .jar: true
directory owns class: true
Greeting initialized
directory message: hello from plugin
directory resource: resource from plugin
JAR owns class: true
Greeting initialized
JAR message: hello from plugin
JAR resource: resource from plugin
~~~

Chương trình trên đã được biên dịch và chạy thành công bằng JDK trên Windows với chính cấu trúc thư mục/JAR đã mô tả.

### Giải thích kết quả

- `directory URL ends in /: true`: `Path.toUri()` từ thư mục hiện hữu tạo URL thư mục kết thúc bằng `/`.
- `JAR URL ends in .jar: true`: đối số thứ hai trỏ chính tệp `plugin.jar`.
- `directory owns class: true` và `JAR owns class: true`: mỗi URLClassLoader **tự định nghĩa** bản `sample.Greeting` từ nguồn tương ứng.
- `Greeting initialized` xuất hiện **hai lần** vì có **hai định nghĩa lớp trong hai loader khác nhau**, không phải vì JVM “khởi tạo lại cùng một class”.
- `directory/JAR message` có cùng nội dung vì hai nguồn chứa cùng bytecode của plugin.
- `directory/JAR resource` giống nhau vì resource `sample/message.txt` đã được copy vào gốc thư mục plugin rồi đóng gói trong JAR.

Điều kiện để đầu ra đúng là **file và URL được chuẩn bị đầy đủ**. Nếu JAR chỉ chứa `Greeting.class` mà thiếu `message.txt`, chương trình sẽ ném `IllegalStateException("missing resource")` tại bước đọc JAR.

## 6. Nếu cần nạp nhiều JAR hoặc kết hợp thư mục và JAR

Truyền nhiều URL cùng lúc là cách trực tiếp:

~~~java
URL[] sources = {
    Path.of("out/plugin").toAbsolutePath().toUri().toURL(),
    Path.of("lib/dependency-a.jar").toAbsolutePath().toUri().toURL(),
    Path.of("lib/dependency-b.jar").toAbsolutePath().toUri().toURL()
};

try (URLClassLoader loader =
             new URLClassLoader(sources, PluginHost.class.getClassLoader())) {
    Class<?> plugin = loader.loadClass("sample.Greeting");
    // Sử dụng plugin ở đây.
}
~~~

Đoạn này là **mã minh họa dùng trong ngữ cảnh có đủ file**, không chạy độc lập nếu chưa tạo hai JAR phụ thuộc. Theo API, loader xét các URL **theo thứ tự đã cung cấp sau khi parent được xét**. Nếu hai URL đều chứa cùng một binary name, lớp đầu tiên tìm được theo chính sách tra cứu sẽ quyết định định nghĩa được dùng. Tránh hai version cùng class name để không có lỗi runtime khó dự đoán.

Một lỗi thường gặp là chỉ đưa `plugin.jar` vào URL mà quên các JAR có chứa dependency của plugin. `URLClassLoader` không tự quét thư mục chứa JAR để lục tìm các dependency; cần liệt kê URL phụ thuộc, dùng một cơ chế manifest phù hợp hoặc thiết lập loader tương ứng.

### JAR manifest Class-Path

Một JAR có thể khai báo thuộc tính `Class-Path` trong manifest để tham chiếu tới các JAR khác bằng URL tương đối, theo đặc tả JAR. Điều này có thể tác động tới quá trình tìm dependency, nhưng đừng coi nó là thay thế tuyệt đối cho một danh sách dependency được quản lý rõ ràng: phải kiểm tra file JAR thực tế, quan hệ đường dẫn, packaging và cách class loader sử dụng manifest.

### JAR lồng bên trong JAR

Một đường dẫn như `plugin.jar!/BOOT-INF/lib/dependency.jar` **không tự động** được xử lý như một tệp JAR bình thường khi chỉ truyền `file:/.../plugin.jar` cho URLClassLoader chuẩn. Một số framework có launcher/class loader riêng hỗ trợ nested JAR; đó là cơ chế bổ sung, không phải mặc định được bảo đảm của `URLClassLoader`.

## 7. Đóng URLClassLoader đúng cách và giới hạn của close()

`URLClassLoader` triển khai `Closeable`, vì vậy nên dùng *try-with-resources* để giải phóng các tài nguyên I/O gắn với JAR và URL:

~~~java
try (URLClassLoader loader = new URLClassLoader(
        new URL[]{jarUrl}, parentLoader)) {
    Class<?> clazz = loader.loadClass("sample.Greeting");
    // Thao tác với clazz và resource khi loader còn mở.
}
~~~

Những điểm quan trọng:

- `close()` đóng các JAR file được loader mở và ngừng khả năng dùng loader để nạp **class hoặc resource mới do chính loader định nghĩa**.
- `close()` **không hủy** các `Class<?>` hoặc instance đã được tạo, không tự ném mọi đối tượng plugin đang sống ra khỏi bộ nhớ.
- **Unload class** chỉ có thể xảy ra khi JVM quyết định và khi không còn những tham chiếu ngăn loader cùng các class liên quan được thu gom. Các tham chiếu từ static field, thread đang chạy, thread context loader, thread-local, callback/registry hoặc object plugin đều có thể giữ loader sống.
- Nếu bạn đã lấy `InputStream` từ resource, hãy **đóng stream riêng**. Nếu đóng loader khi còn đang đọc tài nguyên, hành vi đọc tiếp có thể không còn đáng tin; tài liệu API cũng cảnh báo phải đóng stream thích hợp.
- Đóng loader sau khi phục vụ xong plugin không tương đương “nạp lại version mới ngay lập tức”. Để có một namespace mới, thường cần tạo **loader mới**, tránh giữ tham chiếu class/object cũ và điều phối đúng vòng đời.
- `close()` có thể ném `IOException`; try-with-resources xử lý gọi close kể cả khi block lỗi, và exception vẫn phải được truyền/xử lý phù hợp.

**Không** giữ `URLClassLoader` như biến static toàn cục vô thời hạn nếu thiết kế chủ đích là load/unload plugin theo phiên. Ngược lại, cũng không đóng loader quá sớm nếu còn yêu cầu lazy loading hoặc tìm resource về sau.

## 8. Danh tính lớp, API chia sẻ và parent-first

Danh tính runtime của một class/interface Java thông thường gắn với **binary name + defining class loader**. Do đó:

~~~text
Parent loader: app.PluginApi
     |
     +---- child A: sample.Greeting (version A)
     |
     +---- child B: sample.Greeting (version B)
~~~

Nếu cả child A và child B đều tự định nghĩa `sample.Greeting`, chúng tạo hai kiểu khác nhau dù tên nhị phân giống hệt. Việc ép object từ bản này sang bản class của loader khác có thể gây `ClassCastException` hoặc lỗi liên kết. Đừng dùng chuỗi tên class để kết luận hai đối tượng `Class<?>` bằng nhau.

Để host làm việc với plugin an toàn hơn, đặt interface hợp đồng chung ở **parent**:

~~~java
package api;

public interface Plugin {
    String execute();
}
~~~

Trong JAR plugin:

~~~java
package plugin;

import api.Plugin;

public class Impl implements Plugin {
    @Override
    public String execute() {
        return "plugin result";
    }
}
~~~

Đây là **hai file Java minh họa tách biệt**, không nằm trong chương trình chạy ở phần 5. Khi `api.Plugin` đã được parent định nghĩa, child có thể hỏi parent để lấy cùng interface; host tải `plugin.Impl` bằng URLClassLoader rồi ép về `api.Plugin`. Đừng đóng gói một bản `api.Plugin` khác dưới chính sách child-first để tránh xung đột danh tính kiểu.

Một mẫu reflection khi loader còn mở:

~~~java
Class<?> implType = loader.loadClass("plugin.Impl");
api.Plugin plugin = (api.Plugin)
        implType.getDeclaredConstructor().newInstance();
String result = plugin.execute();
~~~

Điều kiện: `api.Plugin` nằm ở parent, plugin được biên dịch dựa trên cùng API, constructor tương ứng truy cập được, và không có xung đột module/security/accessibility. Đây là ví dụ riêng về API contract, không phải phần bắt buộc của chương trình `PluginHost` trước đó.

## 9. Những lỗi phổ biến và cách chẩn đoán

| Lỗi/triệu chứng | Nguyên nhân thường gặp | Cách kiểm tra |
| --- | --- | --- |
| `ClassNotFoundException: sample.Greeting` khi truyền thư mục | URL trỏ nhầm `out/plugin/sample/` thay vì root `out/plugin/`; hoặc tên package/class không khớp. | Kiểm tra `sample/Greeting.class` có ngay dưới directory root. |
| `ClassNotFoundException` khi dùng URL file thư mục | Thiếu dấu `/` cuối, khiến URL `file:` bị hiểu không phải directory. | Tạo URL bằng `Path.of(existingDirectory).toUri().toURL()`. |
| `ClassNotFoundException` khi nạp JAR | URL trỏ sai file; JAR không chứa đúng entry; class có binary name khác. | Dùng `jar tf dist/plugin.jar`, kiểm tra `sample/Greeting.class`. |
| `NoClassDefFoundError` khi plugin dùng dependency | Dependency không có trong parent hoặc danh sách URL của child; hoặc class liên quan thất bại từ bước khác. | Xem cause và JAR chứa class cần thiết; thêm URL dependency đúng. |
| `ClassCastException` dù hai lớp cùng tên | Có hai defining loader khác nhau hoặc duplicate shared API. | Log `clazz.getClassLoader()` và kiểm tra interface ở parent. |
| `Plugin defined by child == false` | Parent đã nhìn thấy tên lớp trước vì host classpath chứa plugin. | Không đưa plugin JAR vào application `-cp` nếu muốn child định nghĩa. |
| Resource qua `getResourceAsStream` bằng `null` | Tệp chưa được copy vào root URL/JAR hoặc tên resource có `/` đầu không phù hợp. | Kiểm tra entry và dùng tên từ root như `sample/message.txt`. |
| `ClassFormatError` / `UnsupportedClassVersionError` | Bytecode không đúng định dạng hoặc build bằng target Java quá mới. | Kiểm tra `javac --release` và phiên bản JDK runtime. |
| `NoSuchMethodError` sau khi nạp được class | Phiên bản dependency runtime khác lúc compile; trùng JAR/loader. | So sánh dependency và nguồn loader, không chỉ tên class. |
| Trên Windows không thể thay thế/xóa JAR sau khi dùng | Loader còn giữ file handle tới JAR hoặc stream chưa được đóng. | Đóng stream và `URLClassLoader.close()`; kiểm tra tham chiếu sống. |
| Đóng loader xong vẫn thấy class/object hoạt động | `close` không lập tức unload lớp đã định nghĩa. | Hiểu vòng đời GC/loader, không dùng `close` như lệnh xóa class. |

### Cách debug thực tế

1. In các URL loader đang dùng qua `loader.getURLs()` để xác định directory có slash cuối và JAR trỏ đúng file.
2. In `type.getName()` và `type.getClassLoader()` để biết binary name và defining loader, không suy từ loader đã được gọi.
3. Dùng `jar tf plugin.jar` để kiểm tra entry; không truyền `sample/Greeting.class` cho `loadClass`.
4. Kiểm tra runtime application `-cp` có vô tình bao gồm plugin/dependency không. Nếu có, parent-first có thể lấy lớp parent.
5. Phân biệt `ClassNotFoundException` khi lookup theo tên với `NoClassDefFoundError` khi JVM giải phụ thuộc bị thiếu và `ExceptionInInitializerError` khi initializer lỗi.
6. Nếu host plugin thành công nhưng class dùng trong plugin lỗi, tìm dependency ở nguồn **defining loader của plugin** có thể nhìn thấy, không chỉ trong thư mục chứa JAR gốc.
7. Kiểm tra quyền truy cập reflection, tên method, constructor, module/package openness; nạp class thành công chưa bảo đảm gọi reflection thành công.

## 10. Những điều URLClassLoader không tự làm

- **Không tạo JPMS ModuleLayer:** truyền JAR vào URLClassLoader không đồng nghĩa resolve named module qua `--module-path` hoặc tạo module graph.
- **Không giải dependency kiểu Maven/Gradle:** nó tìm các bytecode/resource trong URL đã có (và những cơ chế mở rộng phù hợp) chứ không tự tải transitive dependencies từ repository.
- **Không hỗ trợ wildcard theo cú pháp launcher ở mảng URL:** phải tạo URL từng JAR hoặc tự liệt kê file bằng ứng dụng trước khi khởi tạo loader.
- **Không bảo đảm cô lập bảo mật:** custom class loader hoặc parent-child class loading **không là sandbox** cho plugin không tin cậy. Chỉ dùng plugin đáng tin cậy; thẩm định nguồn, checksum/chữ ký và quyền của tiến trình khi thích hợp.
- **Không đảm bảo reload bằng cách sửa file JAR tại chỗ:** với loader đang sống, các class đã định nghĩa không được thay bytecode trong cùng định nghĩa. Thường cần xây loader mới và kết thúc vòng đời loader cũ.
- **Không bảo đảm mọi URL có thể được tải an toàn:** URL từ network có thể chậm/không khả dụng và mở ra nguy cơ lấy code không tin cậy. Ví dụ học tập cố ý dùng file cục bộ, đường dẫn xác định trước.

## 11. Checklist ghi nhớ

Khi tạo URLClassLoader từ directory/JAR, hãy kiểm tra tuần tự:

1. Lớp cần nạp có **binary name** gì? Nó có đúng ở đường dẫn `package/Class.class` hay JAR entry tương ứng không?
2. Directory URL có trỏ tới **package root**, là URL `file:` và kết thúc bằng `/` không?
3. JAR URL có trỏ tới **chính file JAR** không? File có entry cần nạp không?
4. Có cần thêm URL của các JAR phụ thuộc? Có đang nhầm `lib/*` của launcher với mảng `URL[]`?
5. Parent loader là ai? Có chứa shared API hoặc một bản plugin trùng tên làm thay đổi defining loader không?
6. Đã dùng `loadClass("sample.Greeting")` với tên chấm package, thay vì file path không?
7. Có cần **khởi tạo** class hay chỉ load metadata? `loadClass` không thay thế `Class.forName(..., true, loader)`.
8. Có cần đọc resource? Dùng `loader.getResourceAsStream("sample/message.txt")` và kiểm tra `null`.
9. Đã đóng streams và loader trong đúng vòng đời chưa? Có đang giữ reference ngăn class unloading không?
10. Có đang giả định URLClassLoader là module loader, dependency manager hoặc security sandbox không? Nếu có, cần thiết kế khác phù hợp.

### Câu hỏi tự kiểm tra

**Câu 1.** Muốn nạp `com.example.Plugin` từ `plugins/classes/com/example/Plugin.class`, URL thư mục phải trỏ đâu?

**Đáp:** `plugins/classes/`, không phải `plugins/classes/com/example/`; dùng `Path.of("plugins/classes").toUri().toURL()` khi thư mục đã tồn tại.

**Câu 2.** Với `plugins/plugin.jar` chứa `com/example/Plugin.class`, nên truyền URL gì?

**Đáp:** URL của **file JAR** như `Path.of("plugins/plugin.jar").toUri().toURL()`; không cần tự viết `jar:file:...!/com/example/Plugin.class` làm URL search root.

**Câu 3.** `loader.loadClass(name)` trả lớp có `getClassLoader() != loader`. Có chắc URL bị sai?

**Đáp:** Không. Parent-first có thể trả lớp do parent định nghĩa. Kiểm tra parent classpath và defining loader.

**Câu 4.** Hai loader riêng đều nạp `sample.Greeting` từ cùng bytecode và gọi phương thức static. Vì sao khối static chạy hai lần?

**Đáp:** Chúng định nghĩa hai class runtime riêng theo cặp binary name + defining loader; mỗi class có trạng thái initialization riêng.

**Câu 5.** Đóng `URLClassLoader` xong có thể coi lớp và object plugin chắc chắn đã bị unload?

**Đáp:** Không. `close()` giải phóng tài nguyên I/O và ngừng nạp mới từ loader; unloading phụ thuộc vòng đời tham chiếu và quyết định GC/JVM.

## Tài liệu chuẩn

- [Java SE 21 – URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): constructor, URL thư mục/JAR, delegation, `getURLs`, `close()`.
- [Java SE 21 – ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, parent-first, resource lookup và defining loader.
- [Java SE 21 – Path.toUri()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Path.html#toUri()): URI từ filesystem path.
- [JVMS Java SE 21 §5.3 – Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): danh tính class và class loader trong JVM.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="url-class-loader-002">Chọn parent và phạm vi phụ thuộc được kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# URLClassLoader: Chọn parent và phạm vi phụ thuộc được kế thừa

Khi tạo một `java.net.URLClassLoader` để nạp plugin hoặc JAR động, hai thông số quan trọng nhất là **danh sách URL của chính loader** và **parent class loader**. Danh sách URL cho biết loader con có thể tự tìm class/resource ở những nguồn nào; parent cho biết những class/resource nào nó có thể tiếp cận thông qua **delegation**. Với chiến lược **parent-first mặc định**, parent còn quyết định **phiên bản dependency nào được chọn trước** khi plugin JAR chứa một class trùng tên.

Bài học tập trung vào cách chọn parent của `URLClassLoader` trên **Java 17+**, phạm vi phụ thuộc nhìn thấy qua parent, ranh giới host–plugin, cách tránh xung đột phiên bản và các bài thực hành chạy được. “Kế thừa phụ thuộc” ở đây **không phải** Java `extends`, không tự copy JAR từ parent sang child và không có nghĩa child sở hữu mọi class nó tìm thấy.

## 1. Hai nguồn class của một URLClassLoader

Constructor thường dùng:

```java
URLClassLoader pluginLoader =
        new URLClassLoader(pluginUrls, parentLoader);
```

Đây là **snippet**, cần biến `pluginUrls` và `parentLoader` đã khai báo. Nó xác lập:

1. **Child URL roots** (`pluginUrls`): các URL của thư mục class hoặc JAR để child tìm khi parent không tìm được. `URLClassLoader.findClass` sử dụng các nguồn này.
2. **Parent object** (`parentLoader`): một instance `ClassLoader` mà `URLClassLoader` thường hỏi trước thông qua `ClassLoader.loadClass`.
3. **Class identity:** nếu parent trả về `Class<?>`, child dùng chính class đó. Child **không define bản sao** chỉ vì URL của child cũng có cùng binary name.
4. **Phạm vi lookup:** `pluginLoader.getURLs()` liệt kê URL riêng của child, không hợp nhất URL hay dependency của parent.
5. **Định danh parent:** `pluginLoader.getParent()` cho biết object parent, không phải Java superclass của `URLClassLoader`.

Đây là mô hình nạp lớp mặc định:

```text
pluginLoader.loadClass("feature.Service")
                │
                ▼
findLoadedClass(name)
     ├── đã biết ──► trả Class<?> có sẵn
     └── chưa
          │
          ▼
parent.loadClass(name)
     ├── tìm thấy ──► trả Class<?> do parent/ancestor định nghĩa
     └── không thấy
          │
          ▼
pluginLoader.findClass(name)
     ├── tìm trong URL của child ──► define và trả Class<?>
     └── thất bại ──► ClassNotFoundException
```

JVM còn có verification, linking, initialization và runtime module semantics riêng; sơ đồ không khẳng định mọi class loader tùy biến đều parent-first. Nhưng đây là cách suy nghĩ đúng cho `URLClassLoader` sử dụng triển khai `loadClass` chuẩn.

## 2. Parent không phải superclass Java

`URLClassLoader` kế thừa Java class `SecureClassLoader`, rồi `ClassLoader`. Đó là **type hierarchy**:

```text
Object
  └── ClassLoader
        └── SecureClassLoader
              └── URLClassLoader
```

Tuy nhiên, `new URLClassLoader(urls, hostLoader)` tạo một object `URLClassLoader` có parent **trỏ đến object `hostLoader`**. `hostLoader` có thể là instance của built-in application loader hoặc một subclass không liên quan đến `URLClassLoader` trong type hierarchy.

```text
hostLoader : ClassLoader instance
          ▲
          │ pluginLoader.getParent() == hostLoader
          │
pluginLoader : URLClassLoader instance
```

Do đó:

- `pluginLoader.getClass().getSuperclass()` trả Java superclass **`SecureClassLoader.class`**.
- `pluginLoader.getParent()` trả **loader object được chọn** ở constructor.
- `pluginLoader.loadClass("api.Plugin")` trả `Class<?>` được tìm thấy, nhưng `returnedClass.getClassLoader()` mới giúp xác định **defining loader** của class được trả về.
- Parent được lựa chọn theo **hợp đồng API/dependency visibility**, không theo Java `extends`.

Đặc biệt trong kiến trúc plugin, parent và Java superclass giải quyết hai vấn đề hoàn toàn khác nhau: **ủy quyền lookup** và **kế thừa implementation methods**.

## 3. Các lựa chọn parent trên JDK hiện đại

| Parent | Những gì child có thể nhìn thấy qua delegation | Dùng khi nào |
| --- | --- | --- |
| `ClassLoader.getSystemClassLoader()` | Java platform classes cùng các class/dependency mà **system loader thực sự thấy** | Plugin cần host SDK/SPI hoặc code trên application classpath |
| `HostApi.class.getClassLoader()` | Namespace của loader **định nghĩa shared API** (và những gì nó thấy) | Plugin phải tương thích với interface, DTO, exception do host dùng |
| `ClassLoader.getPlatformClassLoader()` | Platform classes từ JDK và ancestor | Plugin có dependency riêng và không mặc định dùng application classpath |
| `null` | Cơ chế tra cứu của **bootstrap** trong `ClassLoader` mặc định | Chỉ khi thực sự muốn parent là bootstrap và chấp nhận phạm vi hẹp |
| Custom shared loader | Những classes/resources mà loader chung quản lý | Application server, plugin SDK chuyên biệt, nhiều tầng class loader |

Cần phân biệt:

- **System parent** là thuận tiện, nhưng có thể khiến dependency của **host** thắng dependency cùng tên của **plugin**.
- **Platform parent** phù hợp khi plugin chỉ cần Java platform API và JAR riêng. Nhưng nó không tự biết một interface như `com.myapp.spi.Plugin` do application loader định nghĩa.
- **`null` parent** không có nghĩa child không tìm được `java.lang.String`: lớp này thường được bootstrap định nghĩa. Tuy nhiên từ Java 9, **không phải mọi platform class đều nhất thiết do bootstrap định nghĩa**, nên parent platform thường phù hợp hơn khi cần đầy đủ platform APIs.
- **Host API loader** có thể khác system loader trong container/framework. Dùng `HostApi.class.getClassLoader()` làm điểm neo khi mục tiêu là chia sẻ **đúng runtime interface**.

Parent là **đối tượng tham chiếu**. Khi đổi parent giữa hai URLClassLoader instances, bạn không đổi Java superclass của chúng; bạn đổi **đường lookup** và khả năng tương thích.

## 4. Constructor nào chọn parent mặc định?

Javadoc Java 17 có các dạng:

```java
new URLClassLoader(urls);
new URLClassLoader(urls, parent);
new URLClassLoader("plugin-A", urls, parent);
```

Đây là syntax minh họa. Những điều đáng nhớ:

- `URLClassLoader(URL[] urls)` sử dụng **system class loader làm parent**.
- `URLClassLoader(URL[] urls, ClassLoader parent)` dùng **đúng loader object** truyền vào, kể cả `null`.
- `URLClassLoader(String name, URL[] urls, ClassLoader parent)` hỗ trợ đặt tên loader để quan sát/diagnostics. Tên `"plugin-A"` không phải binary name, không tự tạo package riêng và không ảnh hưởng delegation order.
- Một số overloads hỗ trợ `URLStreamHandlerFactory`. Chúng không cần thiết để nạp class từ file/JAR URLs bình thường.
- Dùng `try-with-resources` khi ứng dụng sở hữu loader và muốn đóng những JAR handles đúng lúc; `close()` không làm class đã nạp tự động unload ngay lập tức.

**Không bỏ trống parent argument theo thói quen** nếu yêu cầu chính của module là **cô lập phụ thuộc**. Constructor một tham số đã chọn system parent và có thể làm phạm vi lookup lớn hơn dự định.

## 5. Thực hành A — ba parent với cùng URL array rỗng

Ví dụ này chứng minh rằng dù child **không có URL nào**, nó vẫn có thể nhìn thấy class thông qua parent.

Cây tệp:

```text
parent-scope-lab/
└── src/demo/ParentScopeDemo.java
```

Nội dung `src/demo/ParentScopeDemo.java`:

```java
package demo;

import java.net.URL;
import java.net.URLClassLoader;

public final class ParentScopeDemo {
    private static void inspect(String label, URLClassLoader child)
            throws ClassNotFoundException {
        System.out.println(label + " urls=" + child.getURLs().length);
        System.out.println(label + " JDK=" +
                (child.loadClass("java.lang.String") == String.class));

        try {
            boolean visible =
                    child.loadClass("demo.ParentScopeDemo")
                            == ParentScopeDemo.class;
            System.out.println(label + " app=" + visible);
        } catch (ClassNotFoundException e) {
            System.out.println(label + " app=false");
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader app = ParentScopeDemo.class.getClassLoader();
        ClassLoader platform = ClassLoader.getPlatformClassLoader();

        try (URLClassLoader viaApp =
                    new URLClassLoader(new URL[0], app);
             URLClassLoader viaPlatform =
                    new URLClassLoader(new URL[0], platform);
             URLClassLoader viaBootstrap =
                    new URLClassLoader(new URL[0], null)) {
            inspect("app-parent", viaApp);
            inspect("platform-parent", viaPlatform);
            inspect("bootstrap-parent", viaBootstrap);
        }
    }
}
```

Biên dịch/chạy từ thư mục `parent-scope-lab`:

```sh
javac --release 17 -d out src/demo/ParentScopeDemo.java
java -cp out demo.ParentScopeDemo
```

**Stdout trên OpenJDK/HotSpot 17+ thông thường với `-cp out`:**

```text
app-parent urls=0
app-parent JDK=true
app-parent app=true
platform-parent urls=0
platform-parent JDK=true
platform-parent app=false
bootstrap-parent urls=0
bootstrap-parent JDK=true
bootstrap-parent app=false
```

**Đọc kết quả:**

1. `viaApp` có parent là loader nạp class demo từ `-cp out`. Child tìm được `demo.ParentScopeDemo` **qua parent**, dù `getURLs().length == 0`.
2. `viaPlatform` thấy `java.lang.String` nhưng không thấy class demo do application loader quản lý. Child cũng không có URL riêng nên thất bại khi tìm class demo.
3. `viaBootstrap` vẫn thấy `java.lang.String` vì bootstrap nạp lớp cốt lõi này. Nó không tìm class demo.
4. Không một child nào tự define class demo trong trường hợp tìm được qua parent. Kiểm tra `Class<?>.getClassLoader()` sẽ chỉ ra loader thực sự đã định nghĩa class.

**Nguyên tắc:** `getURLs()` không phải “effective dependency list”. Nó chỉ mô tả nguồn riêng của child.

## 6. Parent-first có thể khiến bản JAR cũ của host thắng bản mới của plugin

Giả sử:

```text
Application loader:
    deps/Version.class   → trả về "host"

Child URLs:
    plugin-out/extension/Feature.class
    pluginlib-out/deps/Version.class → trả về "plugin"
```

Nếu plugin nạp `extension.Feature` qua child và `Feature` gọi `deps.Version.value()`, loader child thường hỏi parent trước. Khi parent có class `deps.Version`, JVM liên kết method reference tới **bản parent**. Điều này không thay đổi ngay cả khi pluginlib URL chứa class có cùng tên và source khác.

Hệ quả:

- Plugin có thể chạy **đúng bytecode** nhưng kết quả khác bản mà tác giả plugin mong đợi.
- Nếu method/field signature thay đổi giữa host và plugin compile-time versions, runtime có thể báo `NoSuchMethodError` hoặc `NoSuchFieldError`.
- Parent-first có ích với **shared API identity** nhưng có thể không phù hợp với thư viện **plugin-private** có version trùng tên.
- Không nên sửa mọi lỗi bằng child-first vô điều kiện: nếu loader plugin tự định nghĩa bản sao của interface host, host có thể không cast được plugin instance.

## 7. Thực hành B — cùng plugin bytes, kết quả đổi khi chỉ thay parent

Cấu trúc file:

```text
dependency-choice-lab/
└── src/
    ├── hostlib/deps/Version.java
    ├── pluginlib/deps/Version.java
    ├── plugin/extension/Feature.java
    └── host/demo/DependencyChoiceDemo.java
```

**`src/hostlib/deps/Version.java`:**

```java
package deps;

public final class Version {
    public static String value() {
        return "host";
    }
}
```

**`src/pluginlib/deps/Version.java`:**

```java
package deps;

public final class Version {
    public static String value() {
        return "plugin";
    }
}
```

Đây là **hai phiên bản thay thế** cùng binary name `deps.Version`; phải compile thành hai thư mục riêng.

**`src/plugin/extension/Feature.java`:**

```java
package extension;

import deps.Version;

public final class Feature {
    public String readVersion() {
        return Version.value();
    }
}
```

**`src/host/demo/DependencyChoiceDemo.java`:**

```java
package demo;

import deps.Version;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class DependencyChoiceDemo {
    private static URL directory(String name) throws Exception {
        return Path.of(name).toAbsolutePath().toUri().toURL();
    }

    private static String call(URLClassLoader loader) throws Exception {
        Class<?> feature = loader.loadClass("extension.Feature");
        Object instance = feature.getDeclaredConstructor().newInstance();
        return (String) feature.getMethod("readVersion").invoke(instance);
    }

    public static void main(String[] args) throws Exception {
        URL[] pluginUrls = {
                directory("plugin-out"),
                directory("pluginlib-out")
        };

        System.out.println("host direct=" + Version.value());
        ClassLoader app = DependencyChoiceDemo.class.getClassLoader();

        try (URLClassLoader appParent =
                    new URLClassLoader(pluginUrls, app);
             URLClassLoader platformParent =
                    new URLClassLoader(
                            pluginUrls,
                            ClassLoader.getPlatformClassLoader())) {

            System.out.println("app-parent feature=" + call(appParent));
            System.out.println("platform-parent feature=" +
                    call(platformParent));

            System.out.println("app uses host Class=" +
                    (appParent.loadClass("deps.Version") == Version.class));
            System.out.println("plugin copy differs=" +
                    (platformParent.loadClass("deps.Version")
                            != Version.class));
        }
    }
}
```

**Biên dịch** từ `dependency-choice-lab`:

```sh
javac --release 17 -d hostlib-out src/hostlib/deps/Version.java
javac --release 17 -d pluginlib-out src/pluginlib/deps/Version.java
javac --release 17 -cp pluginlib-out -d plugin-out src/plugin/extension/Feature.java
javac --release 17 -cp hostlib-out -d host-out src/host/demo/DependencyChoiceDemo.java
```

**Chạy trên Windows PowerShell/CMD**:

```powershell
java -cp "host-out;hostlib-out" demo.DependencyChoiceDemo
```

**Chạy trên Linux/macOS**:

```sh
java -cp "host-out:hostlib-out" demo.DependencyChoiceDemo
```

**Stdout dự kiến trên JDK 17+ với đúng thư mục chạy:**

```text
host direct=host
app-parent feature=host
platform-parent feature=plugin
app uses host Class=true
plugin copy differs=true
```

**Giải thích cẩn thận:**

1. Host application classpath gồm `host-out` và `hostlib-out`, **không có `plugin-out`**.
2. Cả hai child `URLClassLoader` nhận cùng URL array với `plugin-out` và `pluginlib-out`. Mỗi child tự định nghĩa `extension.Feature` vì parent của chúng không có class này.
3. Với **app parent**, yêu cầu `deps.Version` được parent giải quyết trước: kết quả là bản `"host"` trên host classpath.
4. Với **platform parent**, platform không thấy class tùy biến `deps.Version` ở application classpath. Child đọc `pluginlib-out` và dùng bản `"plugin"`.
5. `fromParent == Version.class` là cùng runtime type ở nhánh app; nhánh platform có class `deps.Version` khác identity.
6. Trong ví dụ, hai versions cùng có `public static String value()` để output có thể quan sát mà không làm phát sinh lỗi binary compatibility. Nếu signatures khác, lỗi liên kết có thể xuất hiện khi gọi method.

**Vấn đề cốt lõi:** Chỉ thay parent mà không thay plugin bytes đã thay đổi dependency binding. Đây là lý do cấu hình parent phải là **quyết định thiết kế có chủ đích**, không phải thông số ngẫu nhiên.

## 8. Phạm vi được kế thừa không phải là bản sao classpath hay Maven dependencies

“Parent chứa dependency” có thể mang nhiều nghĩa:

| Khái niệm | Quyết định điều gì? | Có tự động được copy vào child? |
| --- | --- | --- |
| `javac -cp ...` | Compiler nhìn thấy type nào khi biên dịch | Không |
| `java -cp ...` | Application/system loader có thể tra cứu nguồn nào lúc chạy | Không |
| Parent chain | Child có thể ủy quyền tra cứu những class nào | Không |
| `URLClassLoader.getURLs()` | URL roots riêng của child | Không chứa các URL của parent |
| Maven/Gradle transitive graph | Artifact nào được build/resolve theo cấu hình build tool | Không tự nạp vào `URLClassLoader` |
| Module `requires`/JPMS readability | Module nào được phép đọc module nào | Không tương đương parent chain |

Nếu một plugin compile với `api.Shared` và `lib.Helper` nhưng runtime parent chỉ cung cấp `api.Shared`, child URL chỉ chứa plugin JAR, thì **`lib.Helper` có thể vẫn thiếu**. Compiler thành công không bảo đảm URL loader nhìn thấy đủ dependency ở runtime.

URL tới một **thư mục chứa JAR files** cũng không tự động quét hết JAR trong đó: muốn cho loader thấy từng JAR, thường phải đưa **URL của mỗi JAR** vào array, hoặc có cơ chế khác quản lý classpath.

## 9. Lựa chọn parent dựa trên shared API identity

Host thường định nghĩa một contract như:

```java
package api;

public interface Plugin {
    String execute();
}
```

Nếu host tải `api.Plugin` bằng application loader, còn plugin chọn platform parent rồi **tự nạp một bản sao** `api.Plugin` trong URL của mình, JVM có thể có hai interface khác nhau:

```text
HostLoader    defines api.Plugin   → Class A
PluginLoader  defines api.Plugin   → Class B

A.getName().equals(B.getName())    → true
A == B                              → false
A.isAssignableFrom(PluginImpl.class) → false
    (nếu PluginImpl implements B thay vì A)
```

Điều này có thể gây `ClassCastException` dù hai bản API có cùng source. Type identity ở JVM là **(binary name, defining loader)**.

**Thiết kế đúng:** shared API của host và plugin phải được nạp thành **cùng `Class<?>`**, thường bằng parent/delegation tới loader chung. Implementation và thư viện private có thể nằm dưới loader riêng nếu không làm hỏng types đi qua ranh giới.

Một interface không phải boundary type duy nhất: DTO, callback, exception, annotation, method parameter/return type, enum và class trong reflective signatures cũng có thể cần identity chung.

## 10. Thực hành C — implementation riêng nhưng contract từ parent

Tạo cấu trúc:

```text
shared-api-lab/
└── src/
    ├── api/api/Plugin.java
    ├── plugin/impl/EchoPlugin.java
    └── host/demo/SharedApiDemo.java
```

**`src/api/api/Plugin.java`:**

```java
package api;

public interface Plugin {
    String execute();
}
```

**`src/plugin/impl/EchoPlugin.java`:**

```java
package impl;

import api.Plugin;

public final class EchoPlugin implements Plugin {
    @Override
    public String execute() {
        return "plugin ready";
    }
}
```

**`src/host/demo/SharedApiDemo.java`:**

```java
package demo;

import api.Plugin;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class SharedApiDemo {
    public static void main(String[] args) throws Exception {
        URL source =
                Path.of("plugin-out").toAbsolutePath().toUri().toURL();
        ClassLoader apiOwner = Plugin.class.getClassLoader();

        try (URLClassLoader pluginLoader = new URLClassLoader(
                new URL[] { source }, apiOwner)) {

            Class<?> implementation =
                    pluginLoader.loadClass("impl.EchoPlugin");
            Class<?> contract = implementation.getInterfaces()[0];

            System.out.println("implementation from child=" +
                    (implementation.getClassLoader() == pluginLoader));
            System.out.println("API shared=" + (contract == Plugin.class));
            System.out.println("assignable=" +
                    Plugin.class.isAssignableFrom(implementation));

            Plugin service = (Plugin) implementation
                    .getDeclaredConstructor()
                    .newInstance();
            System.out.println("result=" + service.execute());
        }
    }
}
```

Biên dịch trong `shared-api-lab`:

```sh
javac --release 17 -d api-out src/api/api/Plugin.java
javac --release 17 -cp api-out -d plugin-out src/plugin/impl/EchoPlugin.java
javac --release 17 -cp api-out -d host-out src/host/demo/SharedApiDemo.java
```

Chạy trên **Windows**:

```powershell
java -cp "host-out;api-out" demo.SharedApiDemo
```

Hoặc trên **Linux/macOS**:

```sh
java -cp "host-out:api-out" demo.SharedApiDemo
```

**Stdout dự kiến với cấu hình đã nêu:**

```text
implementation from child=true
API shared=true
assignable=true
result=plugin ready
```

`impl.EchoPlugin` chỉ có trong `plugin-out` nên được child định nghĩa. `api.Plugin` được host API loader tìm qua parent, do đó `EchoPlugin` thực sự implements cùng interface mà host sử dụng; cast hợp lệ.

Nếu đưa `plugin-out` lên host application classpath, parent-first có thể khiến implementation cũng được host nạp. **Không làm vậy trong thí nghiệm** vì mục đích là kiểm tra identity của child và parent riêng biệt.

## 11. Muốn cô lập lib private nhưng vẫn chia sẻ API thì sao?

Một mô hình plugin tốt thường muốn:

- **Parent-first cho `api.*` và Java platform types:** bảo vệ shared runtime identity.
- **Plugin-private cho `impl.*` và một số `vendor.*`:** cho phép phiên bản dependency khác nhau.
- **Có cơ chế xử lý collision rõ ràng:** tránh cùng một binary name được dùng vô tình ở hai nơi.
- **Chú ý signature biên giới:** nếu `api.Plugin.execute(vendor.JsonNode)` dùng library type, `JsonNode` đã thành một phần của boundary; không thể tùy tiện mỗi bên có một bản khác identity.

`URLClassLoader` với `loadClass` mặc định là **parent-first**, không có switch constructor “child-first for vendor package”. Muốn chính sách phức tạp, có thể viết custom loader override `loadClass`, nhưng đó là công việc khó: phải giữ `findLoadedClass`, khóa nạp lớp, tránh race, loading constraints, các tên `java.*` và chính sách module/resource. Cách đơn giản hơn khi có thể là **thiết kế lại shared/API dependency placement** để parent chỉ giữ contract thật sự cần chia sẻ.

Đừng cố đặt mọi dependency trong plugin JAR và đổi parent thành `null` ngay; việc đó có thể khiến `api.Plugin` không còn cùng runtime type với host.

## 12. Parent và resources: gần nhau nhưng không hoàn toàn đồng nhất

`URLClassLoader` có các phương thức:

```java
URL found = loader.getResource("config/settings.properties");
URL local = loader.findResource("config/settings.properties");
URL[] ownSources = loader.getURLs();
```

Đây là **snippet**: cần `loader` kiểu `URLClassLoader` và import `java.net.URL`.

- `getResource` theo cơ chế lookup của loader có thể trả resource từ parent trước.
- `findResource` của `URLClassLoader` tìm resource trong nguồn riêng của loader, không sử dụng parent theo cách `getResource` mặc định.
- `getURLs` chỉ mô tả URL roots đã được cấu hình cho child, không liệt kê URL parent.
- `getResourceAsStream` đọc resource thành stream (hoặc `null` nếu không tìm được), nên cần kiểm tra `null` và đóng stream.
- Resource trong named module có thể chịu encapsulation riêng; cùng loader nhìn thấy class không bảo đảm đọc được mọi resource không mở package.

Nếu cả host và plugin đều có `config/settings.properties`, parent-first resource lookup có thể khiến bản host được chọn. Hãy dùng `getResource`/`getResources` để kiểm tra thực tế và đặt namespace resource rõ ràng, thay vì xem đó là lỗi `ClassLoader.loadClass`.

**TCCL** (`Thread.currentThread().getContextClassLoader()`) cũng không tự bằng `loader.getParent()`. Một framework có thể dùng TCCL cho `ServiceLoader` discovery, nhưng chọn parent của `URLClassLoader` không tự thay đổi TCCL trên thread.

## 13. Đường dẫn URL chính xác và đóng gói dependency

Đối với thư mục output:

```java
URL classes =
        Path.of("plugin-out").toAbsolutePath().toUri().toURL();
```

Đây là snippet cần imports `java.net.URL` và `java.nio.file.Path`. `Path.toUri()` giúp tạo file URL hợp lệ, tránh ghép chuỗi `file:///...` sai khi tên thư mục có dấu cách.

Đối với JAR:

```java
URL jar = Path.of("plugins", "feature.jar")
        .toAbsolutePath().toUri().toURL();

URLClassLoader loader = new URLClassLoader(
        new URL[] { jar }, ClassLoader.getPlatformClassLoader());
```

Snippet chưa có vòng đời `close`, nên trong ứng dụng thực tế cần quản lý loader được tạo.

Kiểm tra:

1. URL tới directory phải trỏ đến **root của package layout**. Ví dụ `out/extension/Feature.class` tương ứng binary name `extension.Feature`, vậy URL phải trỏ `out/`.
2. URL tới JAR phải trỏ đúng **file JAR**, không chỉ thư mục đang chứa nhiều JAR mà ta nghĩ loader sẽ quét đệ quy.
3. Một JAR chỉ chứa implementation không tự cung cấp mọi transitive dependency. Runtime parent/URL khác phải nhìn thấy dependency cần thiết.
4. Nếu nhiều URL riêng của child chứa cùng class name, thứ tự tìm nguồn có thể tạo xung đột. Đóng gói tường minh và tránh duplicate lành mạnh hơn dựa vào thứ tự.
5. `getURLs()` cho biết URL riêng nhưng **không đủ** để xác định class nào parent đã chọn.
6. `URLClassLoader` phù hợp các nguồn classpath-style; chỉ đặt modular JAR vào URL không tự xây dựng một **JPMS module layer**.

## 14. Các lỗi thường gặp khi chọn parent sai

| Lỗi/triệu chứng | Điều cần xem | Cách xử lý |
| --- | --- | --- |
| `ClassNotFoundException` | Parent và URL riêng đều không tìm tên class | Kiểm tra binary name, roots/JAR, parent, đường dẫn runtime |
| `NoClassDefFoundError` | Dependency cần lúc linking/initialization không được định nghĩa, hoặc init trước thất bại | Đọc `Caused by`, runtime JAR versions và initializer |
| `NoSuchMethodError` | Parent trả library cũ không có method descriptor mà plugin compile against | So `javap -s`, classpath parent, tránh version skew |
| `NoSuchFieldError` | Field name/descriptor ở runtime khác compile-time | So class files thực tế |
| `ClassCastException` dù tên interface giống | Host/plugin dùng hai definitions của shared API | In defining loaders; chia sẻ đúng `Class<?>` |
| `LinkageError` loader constraint violation | Signature xuyên loaders chứa types identity không tương thích | Kiểm tra toàn bộ boundary DTO/return/parameter types |
| `IllegalAccessError` | Runtime access/package/module restrictions | Kiểm tra visibility, JPMS readability/exports |
| Resource trả sai nội dung | Parent resource shadow child resource | `getResource`, `getResources`, resource namespace |
| `ServiceConfigurationError` | Service API/provider không cùng runtime identity hoặc lỗi provider | ServiceLoader/TCCL, runtime API, provider metadata |
| Plugin không unload | Host/threads/caches/TCCL giữ reference hoặc loader chưa được close | Quản lý lifecycle và giải phóng references |

**Không phải mọi lỗi trên xuất phát từ thiếu JAR.** Có lỗi do lookup, có lỗi do liên kết bytecode, có lỗi do type identity hoặc module access. Dùng loại exception/error và stacktrace để khoanh vùng.

## 15. Công cụ debug phạm vi phụ thuộc

Một đoạn helper cho class đã nạp:

```java
static void describe(Class<?> type) {
    System.out.println("name=" + type.getName());
    System.out.println("defining loader=" + type.getClassLoader());
    System.out.println("module=" + type.getModule().getName());
}
```

Đây là **method minh họa**, không phải chương trình độc lập. `getModule().getName()` có thể trả `null` cho unnamed module; đây là thông tin bình thường.

Có thể kiểm tra parent và URLs riêng:

```java
System.out.println("parent=" + pluginLoader.getParent());
System.out.println("urls=" +
        java.util.Arrays.toString(pluginLoader.getURLs()));
```

Khi nghi phiên bản class bị chọn sai, dùng `javap` đọc **binary signatures** ở từng artifact:

```sh
javap -classpath hostlib-out -s deps.Version
javap -classpath pluginlib-out -s deps.Version
javap -classpath plugin-out -c -v extension.Feature
```

Lệnh trên áp dụng từ thư mục ví dụ B sau khi compile. `javap` xác minh bytecode đã tham chiếu symbol nào; nó **không** cho biết runtime đã chọn class nào từ parent. Kết hợp với `Class.getClassLoader()`.

Trên HotSpot hỗ trợ Unified Logging:

```sh
java -Xlog:class+load=info -cp "host-out:hostlib-out" demo.DependencyChoiceDemo
```

Ví dụ `-cp` dùng cú pháp Linux/macOS; Windows thay `:` bằng `;`. Log nạp lớp có thể dài và phụ thuộc runtime; nó giúp xem nguồn class bytes, nhưng không chứng minh class initialization hoặc method resolution đã hoàn tất.

## 16. Vòng đời URLClassLoader và đổi plugin

`URLClassLoader` triển khai `Closeable` và có thể dùng `try-with-resources` như các bài thực hành:

```java
try (URLClassLoader plugin = new URLClassLoader(urls, parent)) {
    Class<?> feature = plugin.loadClass("extension.Feature");
    // Dùng feature trong lúc loader còn phục vụ lookup.
}
```

Đây là snippet với `urls` và `parent` có sẵn. `close()` đóng các resource handles mà loader quản lý. **Không có nghĩa mọi class/object của plugin đã unload lập tức**.

Lưu ý:

- Một plugin đang chạy có thể cần lazy-load dependencies; **đừng close loader trước khi kết thúc vòng đời sử dụng plugin**.
- Host registry có thể đang giữ plugin objects, `Class<?>`, reflective `Method`, callbacks hoặc thread context class loader; những references này có thể giữ loader sống.
- Thay file JAR trên đĩa không tự thay thế `Class<?>` đã được định nghĩa trước đó.
- Reload nên tạo loader instance mới và plugin object mới, dừng instance cũ, giải phóng tham chiếu và kiểm tra contract API version.
- Shared API dùng chung qua parent nên ổn định qua vòng đời plugin; không tự định nghĩa lại bản sao contract mỗi lần reload.

## 17. Checklist chọn parent trước khi triển khai

1. Liệt kê tất cả **boundary types**: SPI, DTO, callback, exception, annotations và types trong method descriptors.
2. Xác định **defining loader thực** của shared API; đừng đoán system loader chỉ vì host đang chạy.
3. Liệt kê các **dependency private** và phiên bản host có thể trùng binary name.
4. Chọn parent (system, platform, bootstrap/null hay custom API loader) từ hai tập trên.
5. Kiểm tra child URL roots: directory/JAR URLs chính xác, có đủ các private dependency cần thiết.
6. Xác định parent-first có gây shadowing không; chỉ dùng custom child-first khi hiểu rõ rủi ro.
7. Integration test **plugin JAR qua loader thật**, không nhét plugin JAR lên application classpath khi mục tiêu là kiểm tra isolation.
8. Assert shared API identity bằng `isAssignableFrom` hoặc `Class<?> == Class<?>`, không chỉ `getName()`.
9. Quan sát resource URLs và TCCL nếu plugin có config/service discovery.
10. Kiểm tra JPMS module rules nếu ứng dụng dùng named modules.
11. Quản lý `close`, plugin lifecycle, callbacks, threads và references.
12. Không xem class loader là sandbox bảo mật cho code không đáng tin cậy.

### Test minh họa

```java
if (pluginLoader.getParent() != expectedParent) {
    throw new AssertionError("Wrong parent");
}
if (!SharedPluginApi.class.isAssignableFrom(implementationClass)) {
    throw new AssertionError("Wrong API identity");
}
```

Đây là snippet cần biến `pluginLoader`, `expectedParent`, `implementationClass` cùng kiểu `SharedPluginApi` có thật. Nếu implementation **bắt buộc** do child định nghĩa, có thể thêm `implementationClass.getClassLoader() == pluginLoader`; không áp dụng assertion ấy cho class được parent cung cấp theo thiết kế.

## 18. Những nhầm lẫn cần tránh

- **“`new URLClassLoader(urls)` mặc định parent là bootstrap.”** Sai: mặc định là system class loader.
- **“`getURLs()` rỗng nghĩa là loader không thể tìm class.”** Sai: delegation vẫn hoạt động.
- **“Parent có dependency v1, plugin URL chứa v2 thì v2 luôn thắng.”** Sai với parent-first mặc định.
- **“Parent được copy tất cả các JAR sang child.”** Sai: parent trả class từ namespace/logic của chính nó.
- **“Parent class loader là Java superclass của URLClassLoader.”** Sai: parent là object tham chiếu.
- **“Đổi parent sang platform sẽ giữ nguyên mọi host-defined SPI.”** Không mặc định; platform không phải application classpath loader.
- **“Parent null là giải pháp sandbox an toàn.”** Sai: class loader visibility không phải biên giới bảo mật đầy đủ.
- **“Cùng tên interface là đủ để host cast object plugin.”** Sai: runtime identity còn phụ thuộc defining loader.
- **“Maven compile được thì URLClassLoader chắc chắn thấy tất cả transitive dependencies.”** Sai: runtime classpath phải được cấu hình thực sự.
- **“URL trỏ vào thư mục plugin sẽ tự quét mọi JAR con.”** Không mặc định.
- **“`close()` xóa ngay classes đang sử dụng.”** Sai: garbage collection/unloading phụ thuộc references.
- **“Resource lookup và class lookup lúc nào cũng có cùng thứ tự/namespace.”** Phải kiểm tra API và loader thực tế.

## 19. Câu hỏi tự kiểm tra

**1. Constructor một tham số `URLClassLoader(urls)` chọn parent gì?** System class loader.

**2. Vì sao child URLs rỗng vẫn tìm được `java.lang.String`?** Child có thể ủy quyền cho parent/ancestor hoặc bootstrap.

**3. Vì sao cùng URL array nhưng app parent và platform parent cho kết quả khác ở ví dụ A?** Vì chúng có phạm vi class visibility khác nhau qua delegation.

**4. Parent chứa `deps.Version` v1, child URL chứa v2: bản nào thường được chọn?** Parent v1 với `loadClass` parent-first mặc định.

**5. Bản v1 thiếu method mà plugin yêu cầu có thể gây lỗi gì?** `NoSuchMethodError`, tùy binary mismatch và thời điểm symbol được sử dụng.

**6. `getURLs()` có phản ánh mọi JAR parent nhìn thấy không?** Không; nó chỉ trả URL riêng của child.

**7. Khi nào chọn platform loader?** Khi plugin cần Java platform APIs mà không mặc định thừa hưởng toàn application classpath, và shared API được thiết kế tách biệt.

**8. Nếu plugin bắt buộc implements `api.Plugin` do host định nghĩa, chọn parent thế nào?** Đảm bảo loader plugin giải quyết interface qua **đúng defining loader của shared API**.

**9. Hai class cùng binary name do hai loaders định nghĩa có cùng runtime type không?** Không, type identity gồm cả defining loader.

**10. Parent null có chắc nhìn thấy tất cả Java SE classes không?** Không trên Java 9+; một số platform classes không thuộc bootstrap.

**11. Parent object có phải superclass Java của URLClassLoader không?** Không; `getParent()` và `getSuperclass()` mô tả hai quan hệ khác nhau.

**12. `close()` có làm plugin unload tức thì không?** Không; cần giải phóng references và JVM quyết định class unloading.

## 20. Nguồn chuẩn và kết luận

- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): constructor parent, `getURLs`, `findClass`, `findResource`, `close`.
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, parent delegation, `getParent`, `getSystemClassLoader`, `getPlatformClassLoader`.
- [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getClassLoader` và `isAssignableFrom` khi kiểm tra type identity.
- [Java Virtual Machine Specification Java SE 17, Chapter 5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): defining/initiating loaders, runtime type identity, linking và loading constraints.
- [JDK 17 javap](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): kiểm tra method descriptors và bytecode để debug binary mismatch.

**Quy tắc nhớ:** `URLClassLoader` có **nguồn class riêng trong URL array**, nhưng **được nhìn thấy các dependency parent có thể cung cấp qua delegation**. Parent-first giúp chia sẻ hợp đồng API, đồng thời có thể khiến phiên bản host che phiên bản plugin. Chọn parent theo **shared API identity và private dependency boundaries**, sau đó kiểm thử chính xác loader nào định nghĩa từng class — không chỉ nhìn tên JAR hoặc tên class.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="url-class-loader-003">Thứ tự URL và xử lý JAR hoặc lớp trùng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự URL và xử lý JAR hoặc lớp trùng nhau trong URLClassLoader

## 1. Vấn đề: nhiều nguồn có cùng một binary name

Trong hệ Java sử dụng nhiều thư viện, hai file JAR có thể cùng chứa `com.example.lib.Parser.class` nhưng ở **hai phiên bản** khác nhau. Một `URLClassLoader` có thể được tạo với danh sách URL trỏ tới nhiều JAR hoặc thư mục:

```java
try (URLClassLoader loader = new URLClassLoader(
        new URL[] {firstJarUrl, secondJarUrl},
        parentLoader)) {
    Class<?> parser = loader.loadClass("com.example.lib.Parser");
}
```

Đây là **đoạn minh họa**; biến URL và parent phải được ứng dụng cung cấp. Câu hỏi quan trọng là: nếu cả hai URL đều chứa `Parser.class`, JVM có nạp hai bản rồi chọn bản mới nhất, hay có một quy tắc ưu tiên khác?

Trong mô hình thông thường:

1. `URLClassLoader` kế thừa thuật toán `ClassLoader.loadClass` có **parent-first delegation**. Nếu parent cung cấp được class, loader con nhận ngay class của parent và không cần tìm các URL cục bộ.
2. Nếu parent không tìm được class, `URLClassLoader` tìm bytecode trong **các URL thuộc đường tìm của chính loader đó**, theo thứ tự tìm kiếm đã cấu hình và cơ chế mở rộng classpath/JAR tương ứng.
3. Với một binary name cụ thể, khi loader đã định nghĩa thành công class, các lời gọi `loadClass` tiếp theo trên cùng loader thường **tái sử dụng định nghĩa đã nạp**, không tự đổi sang bản ở URL đứng sau.
4. Nếu cần hai bản class cùng tên tồn tại dưới hai danh tính runtime độc lập, chúng phải được định nghĩa bởi **hai defining loaders khác nhau**. Trong cùng một defining loader không thể tùy ý định nghĩa lặp lại binary name như hai phiên bản.

Nói ngắn gọn: **thứ tự URL chỉ có ý nghĩa đối với những lớp/resource mà parent chưa thỏa mãn và loader thực sự phải tìm tại nguồn cục bộ**. Không được chỉ nhìn thứ tự JAR mà bỏ qua parent delegation.

## 2. URLClassLoader nhận những URL nào?

`URLClassLoader` là một `ClassLoader` có thể tìm class và resource từ các URL tới thư mục hoặc JAR hỗ trợ, thường dùng để xây dựng môi trường plugin, công cụ, hoặc bộ nạp thư viện động. Cách khởi tạo phổ biến:

```java
URL[] paths = {
    Path.of("plugins/api.jar").toUri().toURL(),
    Path.of("plugins/implementation.jar").toUri().toURL()
};

try (URLClassLoader loader = new URLClassLoader(paths, parentLoader)) {
    Class<?> implementation = loader.loadClass(
            "com.example.plugin.Implementation");
}
```

Đây là **snippet**, giả định hai JAR hợp lệ, class và `parentLoader` tồn tại, đồng thời import các API cần thiết. Một URL tới **thư mục** phải được thiết lập như URL thư mục hợp lệ (thường có dấu `/` cuối), ví dụ từ `Path.toUri().toURL()` của thư mục tồn tại.

Các điểm cần phân biệt:

- URL chỉ tới **JAR**: JAR chứa các entry như `com/example/plugin/Implementation.class`.
- URL chỉ tới **thư mục gốc của package tree**: thư mục chứa đường `com/example/plugin/Implementation.class`, không phải thư mục `.../com/example/plugin/`.
- **Thứ tự URL**: khi loader phải tìm cục bộ, những entry được tìm trước có cơ hội cung cấp class/resource trước.
- **Parent loader**: parent có thể cung cấp class trước toàn bộ đường tìm cục bộ; điều này độc lập với thứ tự URL trong child.
- `loader.getURLs()` trả các URL đã đăng ký trong danh sách tìm của loader theo thứ tự; đó **không nhất thiết** là bản liệt kê đầy đủ tất cả nguồn gián tiếp do manifest/JAR hoặc cấu hình launcher đưa vào quá trình tìm kiếm.

Một URL chỉ được liệt kê **không chứng minh** file tồn tại, có quyền đọc, đúng manifest, đúng class file version hay thực sự được chọn để định nghĩa class.

## 3. Parent-first trước, URL order sau

Giả sử có:

```text
Application Loader (parent)
  └── lib.Json  [version 1.0]

Plugin URLClassLoader (child)
  ├── URL[0] = plugin/lib-json-2.0.jar
  └── URL[1] = plugin/plugin.jar
```

Nếu code gọi:

```java
Class<?> json = pluginLoader.loadClass("lib.Json");
```

và parent đã có định nghĩa `lib.Json`, kết quả thường là **class do parent định nghĩa**, mặc dù URL đầu tiên của child chứa phiên bản 2.0.

Điều này xảy ra vì thuật toán mặc định:

```text
child.loadClass("lib.Json")
    |
    +-- findLoadedClass("lib.Json") -> đã ghi nhận? trả Class
    |
    +-- parent.loadClass("lib.Json") -> tìm thấy? trả Class parent
    |
    +-- child.findClass("lib.Json")  -> chỉ khi parent không tìm ra
              |
              +-- tìm theo các nguồn URL của child
```

Sơ đồ là logic tổng quan; phương thức thực tế còn liên quan khóa nạp lớp, kiểm tra hợp lệ, linking/resolution và trạng thái loader.

**Vì thế:** chuyển JAR phiên bản 2.0 từ URL[1] lên URL[0] **không giúp gì** nếu parent-first đã chọn bản 1.0 của parent. Trước khi thay đổi thứ tự URL, cần xác định **defining loader** của class đang được nạp.

Một số plugin framework thiết kế **child-first chọn lọc** cho package riêng, nhưng đó là chính sách tùy chỉnh. Không nên mặc nhiên giả định `URLClassLoader` dùng child-first.

## 4. Nếu hai URL của cùng loader chứa cùng một class

Xét loader con mà parent không có class `sample.Duplicate`:

```text
URL[0] -> A.jar
           └── sample/Duplicate.class  (origin() trả "A")
URL[1] -> B.jar
           └── sample/Duplicate.class  (origin() trả "B")
```

Khi `URLClassLoader` tìm cục bộ, nó gặp bản trong A trước và định nghĩa `sample.Duplicate` từ A (với giả định không có redirect đặc biệt hoặc nguồn gián tiếp làm thay đổi đường tìm). Class đã định nghĩa không tự được thay bằng bản trong B chỉ vì lần gọi sau “muốn phiên bản B”.

Nếu tạo **một URLClassLoader khác** với URL đảo lại:

```text
URL[0] -> B.jar
URL[1] -> A.jar
```

loader mới có thể định nghĩa `sample.Duplicate` từ B. Hai lớp trả về:

- Cùng `Class.getName()`: `"sample.Duplicate"`.
- Có thể có bytecode/logic khác nhau.
- **Không bằng nhau** dưới phép so sánh `Class<?> == Class<?>` vì defining loader khác nhau.
- Không thể coi các instance là cùng kiểu chỉ nhờ tên nhị phân giống nhau.

Dù dùng cùng một file `.class` ở cả hai nguồn, danh tính kiểu vẫn phụ thuộc **(binary name, defining loader)**.

### Không có quy tắc “JAR mới hơn thắng”

`URLClassLoader` không tự so sánh Maven version, timestamp, tên file JAR hay số phiên bản trong `pom.properties` để chọn bản “tốt nhất”. Nếu nhiều class có cùng tên nằm trên đường tìm của loader, **thứ tự và delegation** là những điều cần kiểm tra, không phải “bản cuối sẽ ghi đè”.

Nếu hai JAR cùng tồn tại nhưng chứa những package/class khác nhau, loader có thể nạp lớp từ cả hai. Điều gây vấn đề là **trùng binary name**, **phụ thuộc không tương thích**, hoặc ràng buộc package/module, chứ không phải mọi trường hợp có nhiều JAR.

## 5. Thực nghiệm Java 17+: đảo URL đổi lớp được chọn

Ví dụ dưới đây tạo hai thư mục riêng thay cho hai JAR. Với `URLClassLoader`, cả JAR và thư mục class đều là URL search roots phù hợp; dùng thư mục giúp bài thực hành hoàn toàn tự chứa, không phải chuẩn bị JAR thủ công.

Chương trình:

1. Tạo hai file nguồn `sample.Duplicate` có **cùng binary name** nhưng trả về giá trị khác nhau.
2. Biên dịch từng nguồn vào thư mục A hoặc B bằng `JavaCompiler`.
3. Tạo hai `URLClassLoader` độc lập, một có URL[A,B], một có URL[B,A].
4. Kiểm tra class trả về, defining loader và tài nguyên cùng tên.
5. Đóng loader, dọn file tạm sau khi hoàn tất.

Lưu **toàn bộ** mã sau thành `UrlOrderDemo.java`:

```java
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class UrlOrderDemo {
    private static final String CLASS_NAME = "sample.Duplicate";
    private static final String MARKER =
            "lesson-url-order-2026.marker";

    private static String read(URL url) throws IOException {
        try (InputStream input = url.openStream()) {
            return new String(input.readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }

    private static void compile(JavaCompiler compiler,
                                Path source, Path output) {
        int exit = compiler.run(null, null, null,
                "--release", "17",
                "-d", output.toString(), source.toString());
        if (exit != 0) {
            throw new IllegalStateException("Compilation failed");
        }
    }

    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Use a full JDK");
        }

        Path root = Files.createTempDirectory("url-order-demo-");
        Path a = root.resolve("a");
        Path b = root.resolve("b");
        Path sourceA = a.resolve("sample/Duplicate.java");
        Path sourceB = b.resolve("sample/Duplicate.java");
        Path classA = a.resolve("sample/Duplicate.class");
        Path classB = b.resolve("sample/Duplicate.class");
        Path markerA = a.resolve(MARKER);
        Path markerB = b.resolve(MARKER);

        try {
            Files.createDirectories(sourceA.getParent());
            Files.createDirectories(sourceB.getParent());

            Files.writeString(sourceA,
                    "package sample; public class Duplicate { "
                    + "public static String origin() { return \"A\"; } }");
            Files.writeString(sourceB,
                    "package sample; public class Duplicate { "
                    + "public static String origin() { return \"B\"; } }");
            Files.writeString(markerA, "A");
            Files.writeString(markerB, "B");

            compile(compiler, sourceA, a);
            compile(compiler, sourceB, b);

            URL urlA = a.toUri().toURL();
            URL urlB = b.toUri().toURL();
            ClassLoader parent = ClassLoader.getPlatformClassLoader();

            try (URLClassLoader first = new URLClassLoader(
                        new URL[] {urlA, urlB}, parent);
                 URLClassLoader reverse = new URLClassLoader(
                        new URL[] {urlB, urlA}, parent)) {

                Class<?> ca = first.loadClass(CLASS_NAME);
                Class<?> cb = reverse.loadClass(CLASS_NAME);

                System.out.println("firstClass="
                        + ca.getMethod("origin").invoke(null));
                System.out.println("reverseClass="
                        + cb.getMethod("origin").invoke(null));
                System.out.println("sameBinaryName="
                        + ca.getName().equals(cb.getName()));
                System.out.println("sameClassObject=" + (ca == cb));
                System.out.println("firstDefiningIsFirst="
                        + (ca.getClassLoader() == first));
                System.out.println("reverseDefiningIsReverse="
                        + (cb.getClassLoader() == reverse));

                System.out.println("firstResource="
                        + read(first.getResource(MARKER)));
                Enumeration<URL> urls = first.getResources(MARKER);
                List<String> values = new ArrayList<>();
                while (urls.hasMoreElements()) {
                    values.add(read(urls.nextElement()));
                }
                System.out.println("firstResources=" + values);
                System.out.println("reverseResource="
                        + read(reverse.getResource(MARKER)));
                System.out.println("stringDefinedByBootstrap="
                        + (first.loadClass("java.lang.String")
                                .getClassLoader() == null));
            }
        } finally {
            Files.deleteIfExists(sourceA);
            Files.deleteIfExists(sourceB);
            Files.deleteIfExists(classA);
            Files.deleteIfExists(classB);
            Files.deleteIfExists(markerA);
            Files.deleteIfExists(markerB);
            Files.deleteIfExists(a.resolve("sample"));
            Files.deleteIfExists(b.resolve("sample"));
            Files.deleteIfExists(a);
            Files.deleteIfExists(b);
            Files.deleteIfExists(root);
        }
    }
}
```

Biên dịch và chạy (JDK 17 trở lên):

```bash
javac UrlOrderDemo.java
java UrlOrderDemo
```

**Stdout trong môi trường JDK chuẩn** khi không có nguồn bên ngoài trùng tên `sample.Duplicate` hoặc marker của bài thực nghiệm:

```text
firstClass=A
reverseClass=B
sameBinaryName=true
sameClassObject=false
firstDefiningIsFirst=true
reverseDefiningIsReverse=true
firstResource=A
firstResources=[A, B]
reverseResource=B
stringDefinedByBootstrap=true
```

### Phân tích kết quả

- **`firstClass=A`**: loader thứ nhất có thứ tự URL[A,B], nên class `sample.Duplicate` từ A thắng trong đường tìm local.
- **`reverseClass=B`**: loader thứ hai có thứ tự URL[B,A], nên class từ B thắng.
- **`sameBinaryName=true`**, **`sameClassObject=false`**: hai class có cùng tên nhưng được hai defining loader khác nhau định nghĩa, nên là **hai runtime types**.
- **Hai dòng defining=true**: trong ví dụ, cả hai loader đều tự định nghĩa class; parent platform không có `sample.Duplicate`.
- **`firstResource=A`**: `getResource` tìm resource đầu tiên theo delegation + thứ tự URL local.
- **`firstResources=[A, B]`**: `getResources` có thể trả **cả hai URL** của resource trùng tên theo thứ tự tìm kiếm. Chọn một class định nghĩa duy nhất **không đồng nghĩa** bỏ hết những resource trùng tên.
- **`reverseResource=B`**: đảo thứ tự URL cũng đổi resource đầu tiên khi parent không có bản resource ấy.
- **`stringDefinedByBootstrap=true`**: dù gọi `first.loadClass("java.lang.String")`, class nền tảng do bootstrap loader định nghĩa, minh họa parent/bootstrap delegation ưu tiên lớp JDK.

**Điều kiện thực nghiệm:** chương trình yêu cầu JDK có module compiler `jdk.compiler` (không chỉ một runtime image tối giản). Hai root tạm được tạo và đóng URLClassLoader trước khi dọn dẹp. Nếu môi trường có hạn chế tạo file hoặc đã chèn một class/resource trùng tên qua loader cha, kết quả cần được phân tích lại theo đường tìm thực tế.

### Vì sao ví dụ dùng hai ClassLoader?

Nếu tạo **một** `URLClassLoader` rồi gọi `loadClass("sample.Duplicate")` hai lần, cùng loader thường trả lại **cùng đối tượng Class** nhờ cơ chế lớp đã được nạp. Thay đổi thứ tự mảng URL bên ngoài sau khi tạo loader, hoặc thêm một JAR khác chứa cùng tên bằng `addURL`, không thay thế class đã được định nghĩa.

Muốn quan sát cả hai định nghĩa độc lập, cần hai loader riêng với URL order tương ứng. Tuy nhiên, hai lớp đó không mặc nhiên có thể ép kiểu qua lại trong ứng dụng: chúng khác type identity.

## 6. Các API và thứ tự URL: class khác resource

Một lớp có thể được định nghĩa **chỉ một lần** cho mỗi binary name trong cùng loader, nhưng **resource** trùng tên có thể được liệt kê nhiều lần.

| API | Hành vi quan trọng |
| --- | --- |
| `loadClass(name)` | Theo mặc định kiểm tra class đã nạp, hỏi parent, rồi mới tìm trong các URL local; trả một `Class<?>` |
| `findClass(name)` của `URLClassLoader` | Tìm/định nghĩa class từ các URL do loader hỗ trợ nếu delegation đến bước local |
| `findLoadedClass(name)` | Kiểm tra class JVM ghi nhận đã nạp đối với loader đó; tránh cần định nghĩa lại |
| `getResource(name)` | Trả **một URL**; parent-first theo mặc định, rồi nguồn local |
| `getResources(name)` | Trả các URL từ parent và local, có thể chứa nhiều resource cùng tên |
| `findResource(name)` / `findResources(name)` | Điểm tìm kiếm local; `URLClassLoader` có triển khai dựa trên URL |
| `getURLs()` | Xem các URL đã được đăng ký cho loader, hỗ trợ điều tra thứ tự search roots |
| `close()` | Đóng URLClassLoader và giải phóng tài nguyên JAR/URL do loader quản lý; không “gỡ” class đã được định nghĩa khỏi JVM ngay |

**Không dùng `getResources` để kết luận rằng JVM đã nạp nhiều bản class cùng tên.** Tìm một resource tên `sample/Duplicate.class` ở hai JAR có thể trả hai URL, trong khi JVM đã chọn một định nghĩa class cụ thể cho loader.

Ngược lại, thành công khi gọi `loadClass` không bảo đảm mọi resource trong cùng JAR đều có thể tìm bằng API bạn đang dùng: resource name, module encapsulation, nguồn được chọn và policy của loader vẫn quan trọng.

## 7. Duplicate JAR, “JAR hell” và binary compatibility

Sự cố không chỉ nằm ở việc lớp nào thắng. Giả sử ứng dụng được biên dịch với thư viện phiên bản A có method:

```java
package lib;
public class Converter {
    public static String convert(String s) {
        return "A:" + s;
    }
}
```

Nhưng trong runtime, do URL order hoặc parent-first, JVM lại định nghĩa `lib.Converter` từ phiên bản B vốn **không còn** method `convert(String)`:

```java
package lib;
public class Converter {
    public static String render(String s) {
        return "B:" + s;
    }
}
```

Hai đoạn là **hai phiên bản thay thế nhau**, không biên dịch đồng thời với cùng tên vào một output.

Bytecode ứng dụng đã compile có thể vẫn chứa lời gọi `invokestatic lib/Converter.convert:(Ljava/lang/String;)Ljava/lang/String;`. Khi runtime phân giải lời gọi ấy với class phiên bản B, ứng dụng có thể gặp `NoSuchMethodError`.

Các dạng mismatch điển hình:

| Triệu chứng | Nguyên nhân khả dĩ |
| --- | --- |
| `NoSuchMethodError` | Runtime chọn class không có method signature mà code được compile với |
| `NoSuchFieldError` | Runtime chọn class không có field mà bytecode tham chiếu |
| `IncompatibleClassChangeError` | Runtime/compile-time không tương thích về kiểu class/interface hoặc static/instance |
| `ClassCastException` giữa hai class cùng tên | Hai defining loaders tạo hai runtime types độc lập |
| `LinkageError` kiểu loader constraint violation | Phạm vi loader không nhất quán đối với các type qua chữ ký phương thức |
| `NoClassDefFoundError` | Có thể thiếu phụ thuộc gián tiếp, lỗi loading hoặc trạng thái initialization lỗi trước đó |
| Lỗi package sealing hoặc signer | Các class trong cùng package được định nghĩa từ nguồn không tương thích với yêu cầu sealing/chứng chỉ |

Phải đọc toàn bộ stack trace và `Caused by`. Không thể xem mọi lỗi `LinkageError` là “chỉ cần đảo hai JAR”.

### Package sealing và certificates

JAR có thể khai báo **sealing** trong manifest. Khi một package được sealed về một code source, việc cố định nghĩa class cùng package từ một URL/JAR khác có thể vi phạm điều kiện sealing; tương tự, các class cùng package trong một loader có thể phải đáp ứng tính nhất quán về certificate/signing.

Đây là lý do “lớp A từ JAR1 và lớp B cùng package từ JAR2” không phải lúc nào cũng hợp lệ. Nếu có `SecurityException: sealing violation` hoặc thông báo certificate mismatch, điều tra manifest, nguồn JAR và cách bố trí package, **không** tắt kiểm tra hoặc đổi JAR bừa bãi.

### JPMS (Java 9+) khác với classpath truyền thống

Các ví dụ parent-first + URL order chủ yếu xét class trên **classpath/unnamed module** qua `URLClassLoader`. Với named modules, module resolution và phạm vi package/readability bổ sung quy tắc; đặt nhiều JAR trên module path không có nghĩa URLClassLoader sẽ quyết định mọi xung đột bằng thứ tự mảng URL.

Đặc biệt, split package, `requires`, `exports` và việc phát hiện duplicate modules có các quy tắc khác. Phải phân biệt **module path resolution** với **classpath search order** trước khi chẩn đoán.

## 8. Manifest Class-Path, thư viện phụ thuộc và đường tìm gián tiếp

Một JAR có thể khai báo thuộc tính **`Class-Path`** trong `META-INF/MANIFEST.MF` để tham chiếu các JAR/thư mục phụ thuộc:

```text
Manifest-Version: 1.0
Class-Path: lib/helper.jar lib/support.jar
```

Các mục ở đây được giải nghĩa theo quy tắc manifest Class-Path, thường tương đối với vị trí JAR chứa manifest. Khi cơ chế loader hỗ trợ xử lý thuộc tính này, những nguồn phụ thuộc **gián tiếp** có thể tham gia đường tìm.

Vì thế:

- Danh sách URL truyền trực tiếp vào constructor không nhất thiết mô tả **đủ mọi vị trí** loader có thể tìm class.
- Sự tham gia của manifest Class-Path ảnh hưởng cách tính thứ tự tìm kiếm khi loader thực sự đi đến JAR khai báo thuộc tính.
- Không nên tự tuyên bố “URL[1] chắc chắn luôn được kiểm tra trước bất kỳ phụ thuộc nào nêu trong manifest của URL[0]”. Quy tắc mở rộng đường tìm và điều kiện hợp lệ của manifest cần được xét theo loader và đặc tả JAR.
- Đừng giả định `getURLs()` luôn liệt kê toàn bộ URL gián tiếp được tìm qua manifest theo cùng cách với constructor URLs.

Muốn kết quả triển khai dự đoán được, nên đóng gói/khai báo dependency nhất quán bằng build tool thay vì dựa vào tình cờ thứ tự nhiều bản JAR.

## 9. Tại sao thứ tự JAR trên -cp và thứ tự URL trong URLClassLoader không luôn tương đương?

Ứng dụng có thể được chạy bằng:

```bash
java -cp "lib/*:app/classes" com.example.Main
```

Đây là ví dụ cú pháp **Linux/macOS**; Windows dùng dấu `;`. Wildcard `lib/*` do **Java launcher** mở rộng thành tập JAR trong thư mục, và thứ tự liệt kê JAR của wildcard **không được bảo đảm theo tên file/phiên bản một cách ổn định** để ứng dụng phụ thuộc vào.

Một danh sách URL tạo rõ ràng bằng `new URL[] {urlA, urlB}` trong `URLClassLoader` cung cấp thứ tự do code quy định; tuy nhiên parent delegation và các nguồn bổ sung như manifest vẫn có thể ảnh hưởng.

Các tình huống cần phân biệt:

| Môi trường | Điểm cần kiểm tra |
| --- | --- |
| `java -cp "a.jar:b.jar"` | Launcher classpath và thứ tự các entry đã được đưa vào application loader |
| `java -cp "lib/*"` | Các JAR thực sự được mở rộng từ wildcard; đừng dựa vào alphabetic order |
| `new URLClassLoader({a,b}, parent)` | Thứ tự URL trực tiếp và chính sách parent-first |
| `java -jar app.jar` | Quy tắc entry point/manifest, không coi `-cp` cạnh `-jar` là user classpath thông thường |
| Maven/Gradle | Dependency graph, conflict resolution, runtimeClasspath, artifact được đóng gói |
| Plugin/container | Loader tree, delegation rule của framework và class riêng/shared packages |
| JPMS module path | Module resolution/readability/exports, không chỉ URL array |

Một build chạy ổn trên máy này nhưng hỏng máy kia vì có nhiều bản JAR trùng class và phụ thuộc vào thứ tự wildcard là dấu hiệu **đóng gói không xác định**, không phải JVM ngẫu nhiên chọn class.

## 10. Nếu phải ưu tiên bản thư viện của plugin

Một chiến lược plugin thường muốn:

- **API/SPI do host cung cấp** (`com.company.api.*`) phải được **chia sẻ**: để parent định nghĩa.
- **Implementation và thư viện riêng** (`plugin.internal.*` hoặc package được phép cô lập) có thể để plugin loader tự định nghĩa.
- Các package nền tảng của JDK không bị custom loader tìm cách giả mạo.
- Các type xuyên qua method parameters/return types phải nhất quán về defining loader để tránh `ClassCastException`/loader constraint violations.

Với `URLClassLoader` mặc định, nếu parent đã có một thư viện, parent-first thường thắng. Nếu bắt buộc cần hai phiên bản thư viện cùng tồn tại, có thể xét:

1. **Tách class loader phù hợp:** mỗi plugin dùng một defining loader cho implementation/dependencies riêng, chung một loader host cho API.
2. **Delegation theo namespace:** plugin framework định nghĩa rõ package nào parent-first và package nào local-first, kèm chính sách tài nguyên.
3. **Shading/relocation trong build:** thay package name của một phiên bản thư viện (nếu bản quyền và cơ chế thư viện cho phép) để tránh cùng binary name trong cùng ranh giới loader.
4. **Dọn dependency tree:** ưu tiên một phiên bản thống nhất khi không thật sự cần cô lập.

Không nên tự override toàn bộ `loadClass` thành child-first cho **mọi package** để sửa một lỗi. Việc này có thể tạo bản riêng của interface host, phá type identity, hoặc chạm ràng buộc an toàn/định nghĩa lớp.

## 11. Chẩn đoán: class nào thực sự được JVM nạp?

Nếu có thể lấy đối tượng `Class<?>`, ba thông tin quan trọng là **binary name, defining loader và nguồn code**.

Đặt phương thức hỗ trợ sau trong class ứng dụng:

```java
static void describe(Class<?> type) {
    System.out.println("name=" + type.getName());
    System.out.println("definingLoader=" + type.getClassLoader());

    java.security.ProtectionDomain pd = type.getProtectionDomain();
    java.security.CodeSource cs =
            pd == null ? null : pd.getCodeSource();
    System.out.println("codeSource=" +
            (cs == null ? "<unknown>" : cs.getLocation()));
}
```

Đây là **snippet hỗ trợ**, không có `main` riêng. Cần import API hoặc dùng tên đầy đủ như trên. `getClassLoader() == null` biểu thị bootstrap loader; `CodeSource` có thể `null` và không được coi đó là bằng chứng loader sai.

### Checklist điều tra khi có trùng class/JAR

1. **Xác định tên lớp bị ảnh hưởng** từ exception/stack trace, ví dụ `lib.Json`.
2. **Ghi loader nhận yêu cầu và defining loader** của class thật sự trả về. Parent-first có thể khiến hai đối tượng loader khác nhau.
3. **In `getURLs()` của URLClassLoader liên quan**, trong trường hợp có quyền truy cập đối tượng loader. So sánh thứ tự constructor URLs, URL được thêm sau và đường nguồn thực tế.
4. **Kiểm tra các bản trùng**: `jar tf plugin-a.jar` và `jar tf plugin-b.jar` để xem cùng chứa `lib/Json.class` không; đối chiếu method signatures bằng `javap`.
5. **Xác minh runtime artifact** chứ không chỉ compile dependency. Binary đã build có thể dùng API phiên bản khác class được nạp lúc chạy.
6. **Đọc manifest và JAR metadata**: `Class-Path`, sealing, multi-release JAR, signer, package version; không chỉ nhìn tên JAR.
7. **Đối chiếu resource lookup**: dùng `getResources` nếu cần tìm mọi bản `lib/config.properties`; phân biệt nhiều URL resource với một runtime Class.
8. **Kiểm tra class loader cũ** sau hot reload/deploy: class/instance đã định nghĩa không tự bị thay bằng việc sửa URL hoặc JAR trên đĩa.
9. **Đọc đúng loại lỗi**: `NoSuchMethodError` thường là mismatch ABI; `ClassCastException` có thể do hai defining loaders; sealing violation là vấn đề package/JAR policy.
10. **Nếu chạy trên module path**, xác định module graph và readability trước khi dựa vào giả định về classpath URL order.

### Dùng công cụ JDK hỗ trợ

```bash
jar tf plugins/a.jar
jar tf plugins/b.jar
javap -classpath plugins/a.jar -public lib.Json
javap -classpath plugins/b.jar -public lib.Json
```

Đây là các ví dụ lệnh; tên file `plugins/a.jar`, `plugins/b.jar` và `lib.Json` cần thay bằng artifact/lớp thực tế. Với JVM có unified logging:

```bash
java -Xlog:class+load=info -cp "app/classes:lib/*" com.example.Main
```

Lệnh dùng separator Linux/macOS; trên Windows thay `:` thành `;`. Log có thể cho biết nguồn bytecode, nhưng vẫn cần phân tích **defining loader** và cách classpath được mở rộng. File/JAR “được log” không tự nói vì sao một bản bị ưu tiên hơn bản kia.

## 12. Resource trùng tên và ServiceLoader

Không phải chỉ file `.class` mới trùng: nhiều JAR thường cùng chứa `META-INF/services/com.example.spi.Plugin`. Đặc điểm ở đây khác class duplication:

- `getResource(name)` thường lấy URL đầu tiên theo delegation và thứ tự nguồn cục bộ.
- `getResources(name)` có thể liệt kê **nhiều URL** cùng tên từ parent lẫn local, giúp các thư viện khám phá nhiều descriptor.
- `ServiceLoader` tiêu thụ thông tin provider theo giao thức riêng, có thể dựa trên loader/context loader được chỉ định; không nên tự coi nó chỉ đọc JAR URL[0].
- Provider phải tương thích với **chính interface SPI runtime** mà host/consumer dùng. Nếu host và plugin tự định nghĩa hai bản `spi.Plugin` khác defining loader, provider có thể không hợp lệ dù file descriptor đúng tên.

Nếu hai JAR chứa resource cấu hình `app.properties`, việc đổi thứ tự URL có thể đổi bản mà code dùng `getResource` nhận đầu tiên. Nhưng nếu code dùng `getResources` rồi merge tất cả nội dung, “file thắng” lại do **logic merge của ứng dụng**, không chỉ URLClassLoader.

Từ Java 9, named modules bổ sung các quy tắc resource visibility. Không dùng kết quả `getResources` trên classpath để suy luận trực tiếp cách mọi resource trong named modules được mở.

## 13. Những hiểu lầm cần tránh

| Hiểu lầm | Sự thật |
| --- | --- |
| “URL[0] luôn thắng mọi nguồn khác.” | Không nếu parent-first đã tìm được class/resource trước hoặc có quy tắc nguồn gián tiếp. |
| “JAR có version lớn nhất sẽ được chọn.” | URLClassLoader không tự chọn Maven semantic version mới nhất. |
| “Đổi thứ tự URL sau khi đã load class sẽ thay definition.” | Một runtime class đã định nghĩa không được tự thay bằng nguồn khác trong cùng loader. |
| “Có hai file Duplicate.class thì cùng loader chứa hai runtime classes.” | Cùng binary name chỉ được định nghĩa một lần trong một defining loader; phiên bản nào thắng phụ thuộc đường tìm. |
| “Hai loader khác nhau định nghĩa cùng binary name thì vẫn cast được.” | Không nhất thiết; chúng là hai runtime types độc lập. |
| “getResources trả hai URL .class nghĩa là JVM đã định nghĩa hai class.” | Resource enumeration và định nghĩa runtime Class là hai cơ chế khác nhau. |
| “Mọi JAR chứa class cùng package đều nạp chung được.” | Sealing/certificates hoặc module boundaries có thể cấm trộn nguồn. |
| “lib/* luôn sắp xếp JAR theo tên.” | Wildcard expansion order không nên được coi là thứ tự version ổn định. |
| “Chỉ xem getURLs là biết mọi phụ thuộc được mở.” | Manifest Class-Path và parent/framework có thể bổ sung nguồn không thể suy ra chỉ từ mảng URL. |
| “Đóng URLClassLoader sẽ unload class ngay.” | Close giải phóng tài nguyên do loader quản lý; unloading phụ thuộc reachability. |
| “Chỉ cần child-first cho mọi package là giải quyết được.” | Có thể gây nhân đôi API/SPI và lỗi type identity. |

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Child URLClassLoader có URL[A.jar,B.jar], cả hai có `lib.X`; parent cũng có `lib.X`. Class nào được chọn theo parent-first mặc định?

**Đáp án:** Class do parent (hoặc ancestor phù hợp) cung cấp nếu parent tìm được. Thứ tự A/B của child chưa có cơ hội được xét.

**Câu 2.** Parent không có `lib.X`. A.jar và B.jar đều có class này. Nếu thứ tự URL là A rồi B, điều gì thường xảy ra?

**Đáp án:** Loader tìm cục bộ từ URL A trước và định nghĩa class từ A, theo điều kiện đường tìm thông thường. Không tự so sánh phiên bản JAR.

**Câu 3.** Đảo URL thành B rồi A trong một **loader mới**; tên lớp giữ nguyên. Hai `Class<?>` từ hai loader có bằng nhau không?

**Đáp án:** Không nếu mỗi loader tự định nghĩa bản tương ứng. Defining loader khác nhau nên runtime type identity khác.

**Câu 4.** Đã nạp `lib.X` từ A trong loader L; sau đó thêm B qua `addURL` (nếu có subclass cho phép). Lần gọi kế tiếp `L.loadClass("lib.X")` có đổi sang B không?

**Đáp án:** Không. Loader tái sử dụng class đã được ghi nhận/định nghĩa; không thay definition cùng binary name.

**Câu 5.** `getResources("config.properties")` trả hai URL. Điều đó có nghĩa có hai `Config` classes runtime không?

**Đáp án:** Không. Nó chỉ cho thấy hai nguồn resource cùng tên; class identity phải kiểm tra qua `Class<?>` và defining loader.

**Câu 6.** Thư viện đang chạy báo `NoSuchMethodError` dù class có mặt trên classpath. Vì sao URL order có thể liên quan?

**Đáp án:** Runtime có thể chọn bản class khác phiên bản compile-time, thiếu method descriptor mà bytecode đã gọi.

**Câu 7.** Dùng `java -cp "lib/*"` và kỳ vọng `a.jar` luôn thắng `z.jar`. Có ổn không?

**Đáp án:** Không nên dựa vào thứ tự expansion wildcard. Loại bỏ xung đột hoặc cấu hình classpath/dependency rõ ràng.

**Câu 8.** Hai JAR khác nhau chứa class khác nhau nhưng cùng một package sealed. Nguy cơ gì?

**Đáp án:** Sealing violation khi loader cố định nghĩa class của một package đã sealed từ nguồn không hợp lệ. Cần thiết kế package/JAR theo ràng buộc manifest.

## 15. Kết luận và tài liệu chính thức

Khi sử dụng `URLClassLoader`, cần phân biệt hai tầng ưu tiên: **parent delegation** trước, rồi mới đến **thứ tự các URL cục bộ** khi loader phải tự tìm class/resource. Trùng binary name trong các JAR không làm một loader định nghĩa hai class cùng tên; bản đã chọn sẽ được tái sử dụng. Resource có thể được liệt kê nhiều URL, còn muốn tồn tại hai runtime classes cùng tên phải dùng các **defining loaders khác nhau** và kiểm soát ranh giới kiểu.

Để tránh “JAR hell”, hãy chuẩn hóa dependency graph, kiểm tra defining loader/code source thực tế, tránh phụ thuộc vào wildcard ordering, và hiểu ràng buộc manifest, sealing cùng module path trước khi kết luận cần đổi URL order.

Nguồn tham khảo:

- Java SE 17 `URLClassLoader` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- Java SE 17 `ClassLoader` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 `java` launcher (classpath, wildcard): https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- Java SE 17 `JarFile` API và multi-release JAR: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarFile.html
- JAR File Specification (manifest Class-Path, sealing): https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html
- JVMS 17 §5.3, Creation of Classes and Interfaces: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="url-class-loader-004">Nạp lớp và tài nguyên qua URLClassLoader</a>

<details>
<summary>Xem chi tiết</summary>

# Nạp lớp và tài nguyên qua URLClassLoader trong Java

`java.net.URLClassLoader` là class loader có sẵn trong Java dùng để tìm **lớp** và **tài nguyên** từ các URL trỏ tới thư mục hoặc file JAR. Nó thường xuất hiện trong hệ thống plugin, extension, các công cụ tải thư viện động hoặc chương trình cần đọc file resource từ một JAR không nằm sẵn trên application classpath.

Để sử dụng đúng URLClassLoader, phải hiểu năm điều:

1. URL phải trỏ đúng **gốc thư mục chứa package** hoặc **file JAR**, không phải tên class.
2. Tên truyền cho `loadClass` là **binary class name** dùng dấu chấm, còn resource name là path dùng dấu `/`.
3. Parent delegation mặc định có thể khiến **parent** định nghĩa class thay vì URLClassLoader đang được gọi.
4. `loadClass` không đồng nghĩa chạy static initializer hay tạo Java instance; `getResource` chỉ tìm URL, `getResourceAsStream` mới mở stream.
5. `URLClassLoader.close()` giúp đóng những file/JAR đã mở; **không bắt buộc** JVM unload các class đã được định nghĩa.

Bài này tập trung vào API, cơ chế, vòng đời và lỗi thường gặp, sau đó tạo ví dụ Java 17+ đọc cùng một plugin từ thư mục và từ JAR.

## 1. URLClassLoader nằm ở đâu trong hệ thống loader?

~~~text
java.lang.Object
  -> java.lang.ClassLoader
      -> java.security.SecureClassLoader
          -> java.net.URLClassLoader
~~~

URLClassLoader kế thừa mô hình class loading của `ClassLoader`, đồng thời cung cấp cách tìm bytecode/resources bằng URL search path và triển khai `Closeable`.

~~~text
Host application
      |
      v
new URLClassLoader(urls, parent)
      |
      +--> Parent loader (ưu tiên trong loadClass mặc định)
      |       |
      |       +--> Java classes, shared APIs
      |
      +--> URL search path (nếu parent không tìm được)
              |
              +--> file:/.../classes/
              |       example/plugin/HelloPlugin.class
              |       example/plugin/greeting.txt
              |
              +--> file:/.../plugin.jar
                      example/plugin/HelloPlugin.class
                      example/plugin/greeting.txt
~~~

Một số method quan trọng:

| API | Trả về | Mục đích |
|---|---|---|
| `loadClass(String)` | `Class<?>` hoặc ném `ClassNotFoundException` | Nạp kiểu theo binary name |
| `getResource(String)` | `URL` hoặc `null` | Tìm một resource |
| `getResources(String)` | `Enumeration<URL>` | Liệt kê nhiều resource cùng tên |
| `getResourceAsStream(String)` | `InputStream` hoặc `null` | Mở resource để đọc |
| `getURLs()` | `URL[]` | Quan sát danh sách URL của loader |
| `getParent()` | `ClassLoader` hoặc `null` | Xem loader cha |
| `getName()` | `String` hoặc `null` | Tên chẩn đoán của loader |
| `close()` | `void`, có thể ném `IOException` | Đóng file/JAR handles của loader |

**Lưu ý:** URLClassLoader không phải dependency resolver Maven/Gradle, không tự tải mọi dependency từ Internet và không phải sandbox bảo mật cho plugin không tin cậy.

## 2. Constructor và parent delegation

Những constructor thường dùng trên Java 17:

~~~java
URLClassLoader a = new URLClassLoader(urls);
URLClassLoader b = new URLClassLoader(urls, parent);
URLClassLoader c = new URLClassLoader("plugin-a", urls, parent);
~~~

Đây là **mảnh minh họa**, `urls` phải là `URL[]` và `parent` là `ClassLoader` thích hợp.

- `URLClassLoader(URL[])` lấy system class loader làm parent.
- `URLClassLoader(URL[], ClassLoader)` cho phép chọn parent tường minh.
- `URLClassLoader(String, URL[], ClassLoader)` tạo named loader (Java 9+); tên giúp log dễ đọc nhưng **không phải định danh duy nhất**.
- Nếu parent là `null`, việc delegation đến bootstrap không bảo đảm toàn bộ platform classes có thể dùng được.
- Với plugin cần implement interface chung của host, parent nên nhìn thấy **một bản duy nhất** của API đó. Không nên định nghĩa lại cùng interface trong từng child loader.

Đối với implementation mặc định của `ClassLoader.loadClass`:

~~~text
loadClass("example.plugin.HelloPlugin")
                 |
       findLoadedClass(name)?
          /              \
         có              không
         |                 |
      trả Class       hỏi parent trước
                           |
                  parent tìm được?
                    /       \
                  có        không
                  |           |
             trả Class   findClass trong URL[]
                                |
                             defineClass
~~~

Nếu parent đã thấy cùng binary name, child có thể nhận Class do parent định nghĩa. Do đó, đoạn:

~~~java
Class<?> type = loader.loadClass("example.plugin.HelloPlugin");
System.out.println(type.getClassLoader() == loader);
~~~

có thể in `false` dù lời gọi `loadClass` thành công. `Class.getClassLoader()` chỉ **defining loader**; không phải lúc nào cũng là initiating loader.

**Type identity của JVM = (binary class name, defining class loader).** Hai loader khác nhau tự định nghĩa cùng `example.plugin.HelloPlugin` là hai runtime types riêng, có thể không ép kiểu qua lại được.

## 3. URL thư mục và URL file JAR

### Thư mục

Nếu có `plugins/classes/example/plugin/HelloPlugin.class`, URL root cần trỏ tới **`plugins/classes/`**:

~~~java
import java.net.URL;
import java.nio.file.Path;

Path directory = Path.of("plugins", "classes").toAbsolutePath();
URL directoryUrl = directory.toUri().toURL();
~~~

`URLClassLoader` coi `file:` URL kết thúc bằng `/` là thư mục. Tạo URL bằng `Path.toUri().toURL()` thay vì tự nối `"file:" + path` để xử lý đúng Windows, dấu cách và ký tự đặc biệt. Khi directory thực sự tồn tại, URL của thư mục được tạo ở dạng thích hợp.

Không trỏ URL tới chính `HelloPlugin.class`; loader tự ghép `example/plugin/HelloPlugin.class` từ tên binary khi tìm trong root.

### File JAR

~~~java
Path jarPath = Path.of("plugins", "hello-plugin.jar").toAbsolutePath();
URL jarUrl = jarPath.toUri().toURL();
~~~

URL `file:/.../hello-plugin.jar` là đầu vào phù hợp. Với một resource ở trong JAR, `getResource` có thể trả URL dạng:

~~~text
jar:file:/.../hello-plugin.jar!/example/plugin/greeting.txt
~~~

Không cần tự viết `jar:file:...!/` làm URL input để nạp **toàn bộ JAR**. Các file bên trong JAR phải đúng package path:

~~~text
hello-plugin.jar
  META-INF/MANIFEST.MF
  example/plugin/HelloPlugin.class
  example/plugin/greeting.txt
~~~

**Plugin JAR không cần `Main-Class`.** `Main-Class` dùng cho executable JAR chạy bằng `java -jar`; URLClassLoader nạp theo tên `loadClass`, không thực thi entry point của JAR.

### Nhiều URL

~~~java
URL[] sources = {
    Path.of("plugins/classes").toUri().toURL(),
    Path.of("plugins/one.jar").toUri().toURL(),
    Path.of("plugins/two.jar").toUri().toURL()
};

try (URLClassLoader loader = new URLClassLoader(
        "extensions", sources, ClassLoader.getSystemClassLoader())) {
    Class<?> type = loader.loadClass("example.plugin.HelloPlugin");
}
~~~

Đây là **mảnh minh họa** cần các URL tương ứng tồn tại. Parent được ưu tiên theo delegation mặc định, sau đó child tìm các URL theo thứ tự cấu hình. Nếu hai JAR trùng class/resource name, vị trí search path có thể quyết định bản được tìm. Manifest `Class-Path` của JAR cũng có thể ảnh hưởng đến đường tìm kiếm hợp lệ; không tự cho rằng chỉ các URL gốc mới có mặt.

## 4. Tải class, khởi tạo class và tạo instance

Ba lời gọi:

~~~java
Class<?> a = loader.loadClass("example.plugin.HelloPlugin");

Class<?> b = Class.forName(
        "example.plugin.HelloPlugin", false, loader);

Class<?> c = Class.forName(
        "example.plugin.HelloPlugin", true, loader);
~~~

Đây là mảnh API; cần loader đã cấu hình và xử lý `ClassNotFoundException`.

| Thao tác | Ý nghĩa |
|---|---|
| `loader.loadClass(name)` | Yêu cầu nạp class nhưng **không yêu cầu initialize static** |
| `Class.forName(name, false, loader)` | Tìm/nạp/liên kết kiểu, không yêu cầu initialization |
| `Class.forName(name, true, loader)` | Tìm và yêu cầu initialize kiểu |
| `type.getConstructor().newInstance()` | Tạo object bằng reflection nếu constructor cho phép |
| `type.getMethod("sayHello").invoke(instance)` | Gọi method reflection; có thể ném exception hoặc lỗi target |

Một object `Class<?>` không đồng nghĩa đã có instance plugin. `loadClass` nhiều lần trên cùng loader thường trả định nghĩa runtime đã có, không định nghĩa lại cùng tên sau mỗi lần gọi.

Khi dùng plugin API chung:

~~~java
PluginApi plugin = (PluginApi) type.getConstructor().newInstance();
~~~

Đây là mảnh minh họa, **chỉ hợp lệ khi `PluginApi` đã được định nghĩa bởi loader chung và class plugin implement đúng runtime interface đó**. Nếu host và plugin loaders tự định nghĩa hai bản `PluginApi` có cùng tên, cast có thể gây `ClassCastException`.

## 5. Tìm resource: path và API

### `getResource`, `getResources` và `getResourceAsStream`

~~~java
URL first = loader.getResource("example/plugin/greeting.txt");

Enumeration<URL> matches =
        loader.getResources("example/plugin/greeting.txt");

try (InputStream input =
        loader.getResourceAsStream("example/plugin/greeting.txt")) {
    if (input == null) {
        throw new IllegalStateException("Resource not found");
    }
    String text = new String(
            input.readAllBytes(), StandardCharsets.UTF_8);
    System.out.println(text);
}
~~~

Đây là các **mảnh minh họa API** yêu cầu imports `URL`, `Enumeration`, `InputStream` và `StandardCharsets` cũng như xử lý exception thích hợp.

- `getResource` trả URL đầu tiên hoặc `null`.
- `getResources` trả enumeration nhiều URL; thích hợp nếu nhiều JAR có resource cùng tên.
- `getResourceAsStream` mở stream hoặc trả `null`. Đóng stream bằng try-with-resources, kể cả khi loader được đóng về sau.
- Resource có thể được parent tìm trước theo quy tắc delegation; URLClassLoader không chắc là nơi vật lý của resource trả về.

### Class và ClassLoader tính path khác nhau

Giả sử `example.plugin.HelloPlugin` nằm cạnh `example/plugin/greeting.txt`:

~~~java
Class<?> type = loader.loadClass("example.plugin.HelloPlugin");

// Class API: relative từ package example.plugin
InputStream a = type.getResourceAsStream("greeting.txt");

// Class API: absolute từ root
InputStream b = type.getResourceAsStream(
        "/example/plugin/greeting.txt");

// ClassLoader API: luôn truyền path từ root
InputStream c = loader.getResourceAsStream(
        "example/plugin/greeting.txt");
~~~

Cả ba stream có thể trỏ cùng resource. Chúng chỉ là minh họa và cần đóng sau khi sử dụng.

| Cách gọi | Tên được tìm |
|---|---|
| `type.getResource("greeting.txt")` | `example/plugin/greeting.txt` |
| `type.getResource("/example/plugin/greeting.txt")` | `example/plugin/greeting.txt` |
| `loader.getResource("example/plugin/greeting.txt")` | `example/plugin/greeting.txt` |
| `loader.getResource("greeting.txt")` | `greeting.txt` ở root |

Đừng đặt dấu `/` đầu trong resource name của `ClassLoader` theo thói quen từ `Class.getResource`. Resource path dùng dấu `/`, trong khi class binary name dùng dấu `.`.

Nếu resource nằm trong **named module** được nạp bằng cơ chế JPMS, còn có các quy tắc `opens`/module encapsulation; ví dụ sau dùng class plugin thuộc unnamed module để tập trung vào quy tắc URL/path.

## 6. Ví dụ runnable Java 17+: nạp cùng class và resource từ directory/JAR

Ví dụ có hai Java files và một text file; host được biên dịch **không cần plugin trên classpath**. Hai URLClassLoader độc lập sẽ định nghĩa hai bản riêng của `example.plugin.HelloPlugin` và đọc resource cùng tên.

### Cấu trúc thư mục

~~~text
project/
  src/
    plugin/example/plugin/HelloPlugin.java
    host/LoaderLab.java
  resources/
    example/plugin/greeting.txt
  out/
    plugin/
    host/
  dist/
    hello-plugin.jar
~~~

### File `src/plugin/example/plugin/HelloPlugin.java`

~~~java
package example.plugin;

public class HelloPlugin {
    public HelloPlugin() {
    }

    public String sayHello() {
        return "Hello from URLClassLoader";
    }
}
~~~

### File `resources/example/plugin/greeting.txt`

~~~text
resource-ok
~~~

### File `src/host/LoaderLab.java`

~~~java
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class LoaderLab {
    private static final String CLASS_NAME =
            "example.plugin.HelloPlugin";
    private static final String RESOURCE_NAME =
            "example/plugin/greeting.txt";

    private static Class<?> loadSource(String label, URL source)
            throws Exception {
        ClassLoader parent = ClassLoader.getPlatformClassLoader();

        try (URLClassLoader loader = new URLClassLoader(
                label, new URL[] {source}, parent)) {

            Class<?> type = loader.loadClass(CLASS_NAME);
            Object instance = type.getConstructor().newInstance();
            String hello = (String) type.getMethod("sayHello")
                    .invoke(instance);

            System.out.println(label + " class: " + hello);
            System.out.println(label + " defined here: "
                    + (type.getClassLoader() == loader));

            try (InputStream in =
                    loader.getResourceAsStream(RESOURCE_NAME)) {
                if (in == null) {
                    throw new IllegalStateException(
                            "Missing: " + RESOURCE_NAME);
                }
                String data = new String(
                        in.readAllBytes(), StandardCharsets.UTF_8).strip();
                System.out.println(label + " resource: " + data);
            }

            try (InputStream in =
                    type.getResourceAsStream("greeting.txt")) {
                System.out.println(label + " class-relative: "
                        + (in != null));
            }

            return type;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: LoaderLab <directory> <jar>");
        }

        URL dir = Path.of(args[0])
                .toAbsolutePath().toUri().toURL();
        URL jar = Path.of(args[1])
                .toAbsolutePath().toUri().toURL();

        Class<?> fromDir = loadSource("directory", dir);
        Class<?> fromJar = loadSource("jar", jar);

        System.out.println("same class name: "
                + fromDir.getName().equals(fromJar.getName()));
        System.out.println("same runtime class: "
                + (fromDir == fromJar));
    }
}
~~~

**Điểm chính của code:**

- `URLClassLoader(String, URL[], ClassLoader)` tạo loader có tên; mỗi nguồn một loader.
- Parent `getPlatformClassLoader()` được dùng **chỉ cho demo**, để nó không nhìn thấy plugin class trên application classpath và child sẽ là defining loader.
- `loadClass` trả `Class<?>`; reflection mới tạo object.
- `getResourceAsStream` của loader dùng tên từ root; `getResourceAsStream` của Class dùng tên tương đối package.
- Trả `Class<?>` từ method rồi đóng loader cho thấy đóng loader **không tự xóa runtime type** đã có.

### Biên dịch và đóng gói (Bash/Linux/macOS)

~~~bash
mkdir -p out/plugin/example/plugin out/host dist

javac -d out/plugin src/plugin/example/plugin/HelloPlugin.java

cp resources/example/plugin/greeting.txt out/plugin/example/plugin/greeting.txt

jar --create --file dist/hello-plugin.jar -C out/plugin .

javac -d out/host src/host/LoaderLab.java
~~~

`javac` không tự copy `.txt`, vì vậy lệnh `cp` quan trọng. Sau build:

~~~text
out/plugin/example/plugin/HelloPlugin.class
out/plugin/example/plugin/greeting.txt
out/host/LoaderLab.class
dist/hello-plugin.jar
~~~

Kiểm tra nội dung JAR bằng `jar --list --file dist/hello-plugin.jar`; phải có cả `example/plugin/HelloPlugin.class` và `example/plugin/greeting.txt`.

### Chạy host

~~~bash
java -cp out/host LoaderLab out/plugin dist/hello-plugin.jar
~~~

**Stdout mong đợi trên JDK 17+:**

~~~text
directory class: Hello from URLClassLoader
directory defined here: true
directory resource: resource-ok
directory class-relative: true
jar class: Hello from URLClassLoader
jar defined here: true
jar resource: resource-ok
jar class-relative: true
same class name: true
same runtime class: false
~~~

Bản từ directory và bản từ JAR có cùng binary name nhưng **không cùng runtime Class object**. Mỗi URLClassLoader định nghĩa một bản riêng. Đây là lý do loader identity ảnh hưởng tới tính tương thích kiểu, dù bytecode hoàn toàn giống nhau.

**Trên Windows PowerShell**, sử dụng `New-Item -ItemType Directory -Force` để tạo các thư mục, `Copy-Item` thay cho `cp`; các lệnh `javac`, `jar`, `java` vẫn dùng tương tự. Ví dụ chạy không có nhiều classpath entries nên không cần dấu phân cách `;`.

## 7. Vòng đời loader và đóng tài nguyên

URLClassLoader triển khai `Closeable` nên cần try-with-resources:

~~~java
try (URLClassLoader loader = new URLClassLoader(urls, parent)) {
    // load class và đọc resources
}
~~~

Hành vi của `close()`:

- Cố gắng đóng JAR/file handles do loader mở.
- Không biến một class đã load thành class chưa load; class/instance đã được tạo có thể vẫn sử dụng được.
- Không bảo đảm **class unloading ngay lập tức**. Unload còn phụ thuộc việc giữ reference đến loader, object, `Class<?>`, thread, context loader, cache và quyết định của GC.
- Không nên gọi `close()` đồng thời lúc thread khác vẫn đang load class; hợp đồng API không bảo đảm kết quả xác định trong trường hợp đua.
- Các class/resource mới từ URL riêng có thể không còn tìm được sau khi loader đã đóng; parent vẫn có thể cung cấp lớp hoặc resource của nó.
- Dù loader sẽ được đóng, `InputStream` vẫn nên được đóng ngay sau khi đọc.

Nếu hệ thống plugin chạy lâu, quản lý mỗi plugin và URLClassLoader theo một lifecycle rõ ràng; không mở/đóng loader mới cho mọi lần gọi method.

## 8. URLClassLoader so với classpath và JPMS

**Classpath của host:** tạo một `URLClassLoader` riêng không sửa `java.class.path`, `CLASSPATH` hoặc đường tìm của loader khác. `Class.forName(String)` ở host dùng loader của caller; dùng `loader.loadClass(name)` hoặc overload `Class.forName(name, initialize, loader)` để tìm trong plugin loader.

**JPMS:** thêm một modular JAR vào mảng URL không tự resolve named modules, lập `ModuleLayer` hoặc thực thi mọi quy tắc `requires` như module path. Ví dụ trong bài dùng unnamed module.

**Đóng gói và bảo mật:** loader thuần không tự giải Maven/Gradle dependency graph, unpack nested JAR hoặc sandbox code không tin cậy. Cần giải quyết những vấn đề ấy qua build/launcher và ranh giới tiến trình phù hợp.

## 9. Lỗi thường gặp và cách xử lý

| Dấu hiệu | Nguyên nhân khả dĩ | Kiểm tra |
|---|---|---|
| `ClassNotFoundException` | Sai URL root, tên nhị phân hoặc thiếu `.class` | Kiểm tra `example/plugin/HelloPlugin.class` |
| `NoClassDefFoundError` | Dependency thiếu, linkage hoặc initialization từng lỗi | Đọc chuỗi cause/stack trace |
| `UnsupportedClassVersionError` | Java runtime thấp hơn version class | Kiểm tra `javac --release` và runtime |
| `IllegalAccessException` | Constructor/method không truy cập được | Xem public API và JPMS |
| `ClassCastException` | Cùng API name nhưng khác defining loader | So `getClassLoader()` của hai Class |
| Resource trả `null` | File thiếu, path sai, resource chưa copy, khác loader, encapsulation | Kiểm tra JAR entries và path |
| Resource không mong đợi | Parent hoặc URL trước có resource cùng tên | Dùng `getResources` và xem delegation |
| JAR bị khóa | Stream/loader chưa đóng | Dùng try-with-resources |
| Class vẫn tồn tại sau `close()` | Loader/class/object còn tham chiếu | Xem cache, thread, callbacks |

### Chẩn đoán nhanh

**Xem URL đang dùng:**

~~~java
for (URL u : loader.getURLs()) {
    System.out.println(u);
}
~~~

**Xem defining loader của class:**

~~~java
Class<?> type = loader.loadClass("example.plugin.HelloPlugin");
System.out.println(type.getClassLoader() == loader);
~~~

**Kiểm tra resource từ root:**

~~~java
URL resource = loader.getResource("example/plugin/greeting.txt");
System.out.println(resource == null ? "missing" : resource);
~~~

Đây là các mảnh minh họa, cần biến `loader` là URLClassLoader cùng imports/exception handling. Đối với file JAR, `jar --list --file your-plugin.jar` giúp phân biệt file thiếu thật với resource lookup sai.

## 10. Sai lầm phổ biến

1. **“URL JAR tải ngay mọi class.”** Không; class được tìm/nạp theo nhu cầu.
2. **“`loadClass` đồng nghĩa initialize.”** Không; nếu cần khởi tạo chủ động, xem `Class.forName(name, true, loader)`.
3. **“ClassLoader tự ghép package hoặc chấp nhận `/` đầu đường resource.”** Dùng path từ classpath root; `Class.getResource` có quy tắc riêng.
4. **“`getResources` chỉ trả một URL.”** Nó có thể liệt kê nhiều resource cùng tên.
5. **“`getResourceAsStream` luôn khác `null` và tự đóng.”** Kiểm tra `null` và đóng stream.
6. **“Host luôn cast được interface cùng tên của plugin.”** Defining loader của API phải là một runtime type chia sẻ.
7. **“Đóng loader khiến mọi method của plugin dừng ngay.”** Class đã nạp còn tồn tại nhưng lazy dependency/resource sau close có thể thất bại.
8. **“URLClassLoader là dependency resolver, module launcher hoặc sandbox.”** Không; đó là những cơ chế khác.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Class `com.acme.Tool` nằm dưới `plugins/classes/com/acme/Tool.class`. URL root nên trỏ tới đâu?

**Đáp án:** `plugins/classes/`, có dạng URL thư mục kết thúc bằng `/`; nạp bằng `loadClass("com.acme.Tool")`.

**Câu 2.** Nếu parent định nghĩa `com.acme.Tool`, `toolClass.getClassLoader() == child` có đúng không?

**Đáp án:** Không, vì defining loader là parent; child chỉ khởi đầu yêu cầu.

**Câu 3.** Resource `com/acme/config.txt` nằm cùng package với Tool. `Tool.class.getResourceAsStream("config.txt")` tương ứng lời gọi nào trên loader?

**Đáp án:** `loader.getResourceAsStream("com/acme/config.txt")`, không phải `loader.getResourceAsStream("config.txt")`.

**Câu 4.** Hai URLClassLoader tự định nghĩa `com.acme.Tool` từ cùng bytecode. Chúng có một runtime type duy nhất không?

**Đáp án:** Không. Binary name giống nhưng defining loader khác tạo hai runtime types.

**Câu 5.** Tại sao phải đóng URLClassLoader?

**Đáp án:** Để giải phóng file/JAR handles và tài nguyên liên quan, không phải để bắt JVM unload class tức thì.

**Câu 6.** Host cần ép kiểu plugin thành interface `PluginApi`. Đặt API ở đâu?

**Đáp án:** Trong thư viện chung do parent định nghĩa để host và plugin dùng đúng một runtime interface.

**Câu 7.** Resource có trong JAR nhưng `getResourceAsStream` trả `null`, cần làm gì trước khi sửa code loader?

**Đáp án:** Kiểm tra tên entry, đường dẫn resource từ root, JAR được loader mở, việc copy resource khi build và nguồn parent.

## 12. Bản đồ quyết định

~~~text
Plugin classes/resources ở thư mục hay JAR?
               |
               v
Tạo URL root đúng bằng Path.toUri().toURL()
               |
               v
Chọn parent hợp lý cho shared APIs
               |
               v
new URLClassLoader(urls, parent)
               |
       +-------+--------+
       |                |
       v                v
  loadClass(name)   getResource(name)
       |                |
       v                v
  Class<?>          URL/InputStream
       |                |
  check defining    đọc resource,
  loader identity   đóng stream
       |
  tạo object nếu cần
       |
       +--------+-------+
                v
   close loader khi hết vòng đời
                |
       close != unload class ngay
~~~

**Tóm tắt:** `URLClassLoader` sử dụng một chuỗi **URL nguồn** kết hợp **parent delegation**. Muốn nạp class đúng phải có binary name và đúng URL root; muốn tìm resource đúng phải có path theo API; muốn plugin chạy ổn định cần quản lý type identity, dependency và vòng đời stream/loader.

### Tài liệu chính thức

- [Java SE 17 — URLClassLoader API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html)
- [Java SE 17 — ClassLoader API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17 — Class.getResourceAsStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getResourceAsStream(java.lang.String))
- [JVMS `5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="url-class-loader-005">getURLs và giới hạn của addURL</a>

<details>
<summary>Xem chi tiết</summary>

# getURLs và giới hạn của addURL trong URLClassLoader

> **Ngữ cảnh:** Java Core → ClassLoader → URLClassLoader. Bài này giải thích `URLClassLoader.getURLs()` phản ánh điều gì, tại sao `addURL(URL)` là `protected`, khi thêm URL có tác dụng và những giới hạn quan trọng về parent delegation, class identity, thời điểm nạp lớp, đóng loader và sự khác biệt với application classpath. Các ví dụ dùng API chuẩn Java 17+.

## 1. Hai API trông đơn giản nhưng dễ bị hiểu sai

`java.net.URLClassLoader` là một `ClassLoader` dùng các URL làm **đường tìm class và resource**. URL có thể trỏ đến thư mục chứa cây package, tệp JAR hoặc nguồn thích hợp mà implementation hỗ trợ.

Hai method chính:

```java
public URL[] getURLs();
protected void addURL(URL url);
```

- **`getURLs()`** trả về **mảng đường tìm URL của chính instance URLClassLoader đó**, gồm các URL ban đầu của constructor và những URL được `addURL` bổ sung sau này.
- **`addURL(URL)`** nối một URL vào cuối danh sách đường tìm của **instance ấy**. Nó **không** tạo object `Class<?>` ngay, không nạp trước toàn bộ JAR và không mở rộng application classpath của cả JVM.
- `addURL` là **`protected`**, nên mã Java bên ngoài lớp/subclass theo quy tắc truy cập của Java không được gọi trực tiếp như một public API. Nếu ứng dụng thực sự sở hữu custom loader, subclass có thể cung cấp một wrapper được kiểm soát.

Hai hiểu lầm cần tránh ngay:

1. `getURLs()` **không phải API liệt kê toàn bộ classpath/module path hoặc mọi vị trí class có thể được tìm thấy trong JVM**.
2. `addURL` **không phải cơ chế sửa class đã được định nghĩa**, không thể bắt JVM nạp lại một tên đã có trên cùng defining loader.

Ví dụ `loader.getURLs()` trả 2 URL không có nghĩa loader đã tải 2 class, có đúng 2 JAR, hay hai nguồn này là tất cả những nguồn do parent và modules cung cấp.

## 2. Cách cấu tạo URLClassLoader và thứ tự tìm kiếm

Một cách tạo loader:

```java
import java.net.URL;
import java.net.URLClassLoader;

URL[] paths = { firstUrl, secondUrl };
ClassLoader parent = ClassLoader.getPlatformClassLoader();

try (URLClassLoader loader = new URLClassLoader(paths, parent)) {
    // loader tìm lớp/resource ở các URL được cung cấp,
    // sau khi delegation đến parent theo hành vi mặc định.
}
```

Đoạn trên **là snippet**: `firstUrl`, `secondUrl` cần được khai báo trước. Nó minh họa kiểu constructor và parent.

Trong cách sử dụng mặc định của `URLClassLoader`:

```text
loadClass("com.example.Widget")
           |
           v
      findLoadedClass(...)
           |
       đã có? ----- yes ----> trả Class<?> đã được ghi nhận
           |
           no
           v
     delegate tới parent
           |
      tìm được? ---- yes ----> trả Class<?> do parent nạp/định nghĩa
           |
           no
           v
      URLClassLoader.findClass(...)
           |
           +--> duyệt các URL đường tìm theo thứ tự phù hợp
           '--> tìm entry com/example/Widget.class
                      |
                      '--> định nghĩa class trong loader này
```

Đây là luồng tổng quát của `ClassLoader.loadClass` mặc định cùng `URLClassLoader.findClass`; custom loader có thể điều chỉnh cách delegation. `URLClassLoader` tra cứu URL theo thứ tự được khai báo, **sau khi parent đã được thử trước**, nếu không có lớp đã được ghi nhận.

Kết quả quan trọng: nếu parent đã tìm được `com.example.Widget`, việc thêm một JAR khác cũng chứa `com.example.Widget` vào child **không khiến child override class của parent** trong chính sách parent-first bình thường.

## 3. getURLs() thực sự cho biết những gì?

Theo Java SE API, `URLClassLoader.getURLs()` trả các URL dùng làm search path cho class/resource của loader đó. Mảng được trả về chứa:

- Những URL được đưa vào **constructor** khi tạo instance.
- Những URL được **thực sự append** bằng `addURL`, theo thứ tự trong đường tìm.
- Không phải các lớp đã nạp, không phải mọi tài nguyên từng sử dụng.

Với:

```java
URLClassLoader loader = new URLClassLoader(
        new URL[] {urlA, urlB}, parent);
```

`loader.getURLs()` sẽ thể hiện danh sách bắt đầu bằng `urlA`, `urlB`. Khi subclass gọi `addURL(urlC)` thành công, `getURLs()` sẽ phản ánh URL mới ở phía sau.

**Không suy diễn được các dữ liệu sau chỉ từ `getURLs()`:**

| Muốn biết | `getURLs()` có đủ không? | Vì sao |
| --- | --- | --- |
| Đường tìm được cấu hình trực tiếp cho **loader này** | Có, trong phạm vi API. | Đây là hợp đồng của method. |
| JAR nào chắc chắn chứa một class cụ thể | Không. | URL có thể trỏ nguồn không có class đó hoặc chưa mở, JAR có nhiều entries. |
| Class đã từng được định nghĩa bởi loader | Không. | `getURLs` là cấu hình đường tìm, không phải history log. |
| Class nào đang được parent cung cấp | Không. | Parent có nguồn và chiến lược lookup riêng. |
| Tất cả classpath và module path của toàn JVM | Không. | Có bootstrap/platform/system loaders, modules và custom loaders khác. |
| Thứ tự thực sự chọn một class trùng tên trong mọi tình huống | Không. | Cần xét delegation, thứ tự URL, nguồn phụ và tình trạng đã load. |
| Các resource hiện đang mở/đã đóng | Không. | URL cấu hình khác file descriptors/stream state. |
| Classpath mà `java` khởi chạy nhận từ `-cp` | Không nhất thiết. | Một custom URLClassLoader có thể hoàn toàn tách biệt application loader. |

Đừng sửa phần tử của mảng kết quả rồi kỳ vọng đổi cấu hình loader. `getURLs()` là **phương thức quan sát**; API thay đổi đường tìm (nếu có quyền) là `addURL`. Muốn ứng dụng dễ kiểm soát, hãy lưu nguồn cấu hình riêng thay vì lấy mảng URL rồi sử dụng như nơi lưu trạng thái có thể sửa.

### URL trong getURLs có thể chưa được mở

Có URL trong đường tìm không phải bằng chứng tệp tồn tại, có thể truy cập được hoặc đã được mở thành JAR. Việc thực sự tải class/resource mới là hành động làm implementation cần truy cập nguồn. Nếu URL sai, thiếu quyền, archive hỏng, hoặc file không có entry tương ứng, lookup vẫn có thể thất bại.

## 4. addURL là protected: đúng phạm vi truy cập

Javadoc định nghĩa:

```java
protected void addURL(URL url)
```

Vì vậy đoạn code sau **không compile được** từ một class không có quyền `protected` thích hợp:

```java
URLClassLoader loader = new URLClassLoader(new URL[0]);
loader.addURL(newUrl); // addURL(URL) has protected access
```

Đây là **snippet cố ý sai để minh họa compile error**; `newUrl` phải được khai báo nếu thử riêng. Sai lầm là nghĩ “method xuất hiện trong Javadoc nên caller nào cũng gọi được”.

Cách hợp lệ khi thật sự muốn bổ sung nguồn vào loader riêng:

```java
import java.net.URL;
import java.net.URLClassLoader;

public final class ExtensibleLoader extends URLClassLoader {
    public ExtensibleLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public void append(URL url) {
        super.addURL(url);
    }
}
```

Đây là **một class Java biên dịch độc lập** nếu lưu tên `ExtensibleLoader.java`; nó không có `main`, dùng làm component trong ứng dụng.

Đừng dùng reflection lên `URLClassLoader.addURL` hoặc JDK internal loaders để né `protected` trong sản phẩm. Java 9+ áp dụng module encapsulation mạnh hơn, và cách đó còn phụ thuộc kiểu implementation của loader thực.

### Vì sao wrapper cần được thiết kế cẩn thận?

Wrapper public `append` có thể khiến bất kỳ caller có reference tới loader đó thêm dependency không mong muốn. Trong hệ plugin thật, chỉ cấp API này cho mã quản lý plugin, kiểm tra URL/path được cho phép, ownership của loader, chính sách resource/certificates, trạng thái close và concurrency.

Một wrapper tối giản dùng trong demo cho thấy **cơ chế API**; không tự tạo chính sách an toàn hay quản lý vòng đời hoàn chỉnh.

## 5. addURL nối URL như thế nào? null, trùng và loader đã đóng

Theo API Java SE 21:

| Trường hợp | Kết quả với `addURL` |
| --- | --- |
| URL hợp lệ, chưa có, loader chưa đóng | URL được nối vào cuối search path. |
| `url == null` | Không có tác dụng. |
| URL đã tồn tại trong danh sách | Không có tác dụng; không append bản trùng. |
| Loader đã bị `close()` | Không có tác dụng. |

Ví dụ một loader có thứ tự ban đầu `[A, B]`:

```text
getURLs()         -> [A, B]
addURL(C)         -> [A, B, C]
addURL(C)         -> [A, B, C]   (không trùng)
addURL(null)      -> [A, B, C]   (không thay đổi)
close()
addURL(D)         -> [A, B, C]   (đã đóng, không thêm)
```

Đây là sơ đồ khái niệm theo contract; `A`, `B`, `C`, `D` là URL, không phải code Java. Kiểm tra URL “đã có trong danh sách” dựa vào semantics so sánh URL mà implementation sử dụng; đừng dùng giả định mọi spelling tương đương của một path đều được chuẩn hóa giống nhau. Hãy đưa vào path/URL đã chuẩn hóa rõ ràng để tránh các nguồn trùng ngữ nghĩa khó phát hiện.

**Nối URL không xác nhận JAR đã hợp lệ.** Phần tử vừa append có thể trỏ tới file bị xóa, archive không chứa entry cần tìm hoặc dữ liệu không phải class file; lỗi sẽ xuất hiện khi cần tra cứu.

## 6. Thực hành chạy được: trước và sau khi addURL

Ví dụ dùng **hai thư mục chứa class** tách khỏi application classpath. Ta chạy ứng dụng từ `build/app`, chủ động cho custom loader nhìn thấy `build/first`, sau đó mới append `build/second`.

### Cấu trúc project

```text
src/
  first/
    example/
      First.java
  second/
    extra/
      Second.java
  app/
    UrlPathsDemo.java
build/
  first/           (javac tạo)
  second/          (javac tạo)
  app/             (javac tạo)
```

**File `src/first/example/First.java`:**

```java
package example;

public class First {
    public String value() {
        return "first";
    }
}
```

**File `src/second/extra/Second.java`:**

```java
package extra;

public class Second {
    public String value() {
        return "second";
    }
}
```

**File `src/app/UrlPathsDemo.java`:**

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class UrlPathsDemo {
    static final class MutableLoader extends URLClassLoader {
        MutableLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        public void append(URL url) {
            super.addURL(url);
        }
    }

    public static void main(String[] args) throws Exception {
        URL first = Path.of("build/first")
                .toAbsolutePath().toUri().toURL();
        URL second = Path.of("build/second")
                .toAbsolutePath().toUri().toURL();

        Class<?> saved;

        try (MutableLoader loader = new MutableLoader(
                new URL[]{first}, ClassLoader.getPlatformClassLoader())) {

            System.out.println("initial URLs: " + loader.getURLs().length);

            System.out.println("first defined here: "
                    + (loader.loadClass("example.First")
                            .getClassLoader() == loader));

            System.out.println("parent class visible: "
                    + (loader.loadClass("java.lang.String") == String.class));

            try {
                loader.loadClass("extra.Second");
                System.out.println("unexpected second load");
            } catch (ClassNotFoundException expected) {
                System.out.println("second before append: missing");
            }

            loader.append(second);

            System.out.println("after append URLs: "
                    + loader.getURLs().length);

            System.out.println("second appended last: "
                    + loader.getURLs()[1].equals(second));

            loader.append(second);
            loader.append(null);

            System.out.println("duplicate and null ignored: "
                    + (loader.getURLs().length == 2));

            saved = loader.loadClass("extra.Second");

            System.out.println("second defined here: "
                    + (saved.getClassLoader() == loader));
        }

        System.out.println("loaded class usable after close: "
                + saved.getMethod("value")
                        .invoke(saved.getConstructor().newInstance()));
    }
}
```

### Biên dịch và chạy

Chạy các lệnh từ thư mục gốc có `src` (lệnh shell không phụ thuộc `CLASSPATH` global):

```bash
javac -d build/first src/first/example/First.java
javac -d build/second src/second/extra/Second.java
javac -d build/app src/app/UrlPathsDemo.java
java -cp build/app UrlPathsDemo
```

**Stdout đã biên dịch và đối chiếu trên JDK 24; code chỉ dùng API có trong Java 17+:**

```text
initial URLs: 1
first defined here: true
parent class visible: true
second before append: missing
after append URLs: 2
second appended last: true
duplicate and null ignored: true
second defined here: true
loaded class usable after close: second
```

### Điều gì được chứng minh?

1. URL đầu tiên trỏ tới `build/first` nên custom loader tự định nghĩa `example.First`.
2. Mặc dù `getURLs()` chỉ có **một URL**, loader vẫn tìm `java.lang.String` qua parent delegation. Do đó **không được coi `getURLs()` là toàn bộ không gian tìm class**.
3. `extra.Second` chưa có trong đường tìm, lần `loadClass` đầu ném `ClassNotFoundException` và được catch có chủ đích.
4. Sau `append(second)`, mảng `getURLs()` dài 2 và URL mới đứng cuối. Lần `loadClass("extra.Second")` trực tiếp sau đó thành công. Đây là bằng chứng rằng **nguồn mới có thể dùng cho lần tìm class tiếp theo** trong cấu hình demo.
5. Thêm lại URL trùng và thêm `null` không làm tăng số phần tử.
6. Sau khi đóng loader, **class đã được định nghĩa** có thể tiếp tục được sử dụng thông qua `Class<?>`, object và metadata còn sống. `close()` không phải lệnh “xóa class khỏi JVM”.

Đừng suy diễn từ dòng 4 rằng **mọi** `NoClassDefFoundError` trong bytecode hoặc mọi kết quả resolution thất bại trước đó sẽ tự sửa sau khi append URL. `ClassNotFoundException` trong ví dụ phát sinh từ **lời gọi trực tiếp `loader.loadClass`**; JVM còn có semantics caching/linking của symbolic references. Với môi trường runtime plugin, tránh thiết kế phụ thuộc việc vá classpath sau khi một phần dependency đã linking thất bại.

### Biến thể nạp từ JAR

Trong ví dụ, đường `build/second` là directory URL. Nếu muốn nạp từ JAR, sau khi compile class thứ hai:

```bash
jar --create --file second.jar -C build/second .
```

Thay phần khai báo `second` trong `UrlPathsDemo.java` bằng:

```java
URL second = Path.of("second.jar")
        .toAbsolutePath().toUri().toURL();
```

Sau khi biên dịch lại `UrlPathsDemo` và chạy với `java -cp build/app UrlPathsDemo`, custom loader sẽ tìm `extra/Second.class` trong `second.jar`. Đây là **biến thể của chương trình**, không copy thêm một khai báo biến `second` vào cùng scope.

`Path.toUri().toURL()` cho directory cần mang dấu `/` cuối, phù hợp với contract `URLClassLoader`. Một `file:` URL chỉ tới JAR thì không kết thúc bằng `/`; nếu tự ghép URL bằng string dễ dẫn đến hiểu lầm directory và archive. Dùng `Path.toUri().toURL()` thay vì nối `"file:" + path` để xử lý khoảng trắng và ký tự đặc biệt.

## 7. Những giới hạn quan trọng của addURL

### 7.1. Chỉ thay đường tìm của loader mà nó được gọi

`append` gọi `addURL` của **một instance URLClassLoader**. Nó không:

- Thay `System.getProperty("java.class.path")` theo contract.
- Thay đường tìm class của bootstrap/platform/system loaders.
- Thay configuration của các URLClassLoader khác.
- Bắt các class loader khác sử dụng URL vừa thêm.
- Thêm module mới vào named module graph hay module layer tự động.

Nếu host framework dùng một loader khác để tìm plugin, append URL vào loader tự tạo nhưng **không đưa loader ấy vào đường đi nạp class** sẽ không có ích. Phải gọi `loader.loadClass(...)` hoặc API plugin phù hợp.

### 7.2. Không thể thay thế một class đã được định nghĩa

JVM định danh một class/interface thông thường bằng **(binary name, defining class loader)**. Nếu loader đã định nghĩa `com.example.Plugin` từ `v1.jar` thì thêm `v2.jar` chứa cùng binary name không làm loader nạp lại `v2` với tên đó.

Ví dụ:

```text
URL loader path = [v1.jar]
loadClass("com.example.Plugin") -> Class object P1

addURL(v2.jar)
loadClass("com.example.Plugin") -> vẫn dùng Class P1
```

Phải phân biệt **tái yêu cầu cùng tên trên cùng loader** với **tạo một loader mới** (có thể định nghĩa class khác cùng tên). Loader mới cho ra **một runtime type khác**, gây vấn đề `ClassCastException` nếu host và plugin có hai bản API/interface. Thiết kế plugin nên đặt API chung trên parent loader thích hợp, chỉ định nghĩa implementation trong loader riêng.

### 7.3. Parent delegation có thể thắng URL mới

Nếu parent đã định nghĩa `com.acme.Config`, việc thêm `override.jar` chứa `com/acme/Config.class` vào child không thay được class do parent trả về trong parent-first search mặc định.

Cũng cần phân biệt **ClassLoader.loadClass** và **findClass**. `URLClassLoader.findClass` chỉ tìm class trong URL path của loader; `loadClass` còn xét lớp đã load và parent delegation. Không tự gọi `findClass` để qua mặt parent nếu không có lý do kiến trúc rõ ràng.

### 7.4. URL mới không đồng nghĩa dependency graph đầy đủ

Một JAR có thể tham chiếu classes trong các JAR khác. Thêm URL của `plugin.jar` giúp loader thấy bytecode của plugin, **không tự đảm bảo** đủ transitive dependencies. Nếu method/field/class liên quan thiếu khi link hoặc run, vẫn có thể gặp `NoClassDefFoundError`, `NoSuchMethodError`, `VerifyError` hoặc `ClassNotFoundException` tùy đường đi.

Không có đảm bảo một JAR có thể được dùng như plugin “an toàn” chỉ vì `getURLs()` trả về tên của nó. Package sealing, version conflict, code source, resource collisions và module access vẫn phải được xét.

### 7.5. getURLs() không phản ánh parent, manifest và module graph như inventory

Một URLClassLoader có thể truy cập class `java.lang.String` qua parent nhưng `getURLs()` không bao gồm các lớp nền tảng ấy. Một JAR có `Class-Path` trong manifest còn có thể khiến việc tìm class/resource liên quan tới các vị trí mà **không phải mọi nơi đều được thể hiện như một URL được append trực tiếp**. Vì vậy hãy hiểu `getURLs()` là **đường tìm URL được cấu hình của instance**, không phải một biểu diễn chuẩn của toàn bộ dependency graph hay các nguồn transitive.

### 7.6. Không dùng getURLs() để ép system loader thành URLClassLoader

Đoạn code cũ:

```java
URLClassLoader app =
        (URLClassLoader) ClassLoader.getSystemClassLoader();
URL[] urls = app.getURLs();
```

Có thể từng chạy trên một số JDK 8 mặc định, nhưng **không di động**: từ JDK 9, built-in application class loader của Oracle/OpenJDK không còn là `URLClassLoader`. Phép ép kiểu có thể ném `ClassCastException`. `ClassLoader.getSystemClassLoader()` khai báo trả về `ClassLoader`, không hứa kiểu concrete nào.

Nếu mục đích là chẩn đoán classpath khởi động, dùng cấu hình launcher/build, `System.getProperty("java.class.path")` cùng thông tin môi trường (và lưu ý property ấy không chứa mọi custom loader/modules). Nếu mục đích là mở rộng plugin source, **tạo và quản lý URLClassLoader riêng** thay vì mutate system loader.

## 8. Close() có ý nghĩa gì đối với getURLs và addURL?

`URLClassLoader` triển khai `Closeable`/`AutoCloseable`; method `close()` được thêm từ Java 7, nên phù hợp với `try-with-resources`.

Khi đóng:

- Loader không còn được dùng để **nạp class/resource mới do chính nó định nghĩa/tìm**.
- Class/resource do parent cung cấp vẫn có thể truy cập theo delegation.
- Class/object đã load trước khi đóng vẫn có thể tồn tại và tiếp tục sử dụng.
- Với URL `jar:` và `file:`, loader cố đóng các file do chính nó mở; `close()` có thể ném `IOException`, lỗi bổ sung có thể ở suppressed exceptions.
- Gọi `close()` thêm lần nữa không có tác dụng; `addURL` sau close cũng **không có tác dụng**.
- Nếu một thread đang load class trong lúc thread khác gọi `close()`, Javadoc nêu kết quả của lần load ấy là **không xác định**.

Ví dụ điều nên tránh:

```java
URLClassLoader loader = new URLClassLoader(urls, parent);
// Thread A: loader.loadClass(...)
// Thread B: loader.addURL(...), loader.close(), loader.addURL(...)
// Không thiết kế dựa trên tương tác concurrent thiếu đồng bộ và lifecycle rule.
```

Đoạn trên chỉ là pseudo-code kiến trúc, không phải chương trình độc lập. Hãy quản lý đồng bộ, quyền sở hữu và trạng thái loader ở tầng ứng dụng. Với plugin manager, thường dùng một loader theo thế hệ plugin, ngăn thao tác tải mới trước khi đóng và giải phóng các tham chiếu khi có thể.

**Đóng loader không buộc JVM unload class ngay.** JVM chỉ có thể thu hồi khi loader và các metadata/object liên quan đủ điều kiện GC; các thread, static caches, TCCL và callbacks có thể giữ reference.

**Đọc `getURLs()` sau close không chứng minh loader còn dùng được.** Nó mô tả cấu hình đã có; để xác định lifecycle phải theo dõi trạng thái bạn tự quản lý và kết quả các API, không dựa vào việc mảng URL vẫn xuất hiện.

## 9. Thực hành lựa chọn giải pháp đúng theo mục đích

| Nhu cầu | Lựa chọn nên dùng | Tránh |
| --- | --- | --- |
| Liệt kê URL được cấu hình trên **một URLClassLoader có thật** | `thatUrlLoader.getURLs()` | Giả định đó là toàn bộ Java classpath. |
| Tải class từ JAR/directory mới với loader riêng | Tạo `URLClassLoader(urls, parent)` hoặc subclass expose `append` có kiểm soát | Ép application loader sang `URLClassLoader`. |
| Tìm class đã có trong môi trường ứng dụng | `Class.forName` hoặc `ClassLoader.loadClass` với đúng loader | Thêm URL mới nếu chẳng liên quan nguồn thiếu. |
| Đọc resource trong JAR | `Class.getResourceAsStream`/`ClassLoader.getResourceAsStream` | Suy từ `getURLs` rồi tự biến mọi URL thành `File`. |
| Dò nhiều resource trùng tên | `getResources` theo loader thật | Tin rằng `getURLs` xác định hết thứ tự resource. |
| Tải các provider theo SPI | `ServiceLoader` cùng parent/loader/module phù hợp | Đoán class tên nào có trong mọi JAR qua mảng URL. |
| Thêm dependency cố định lúc khởi động | Cấu hình `-cp` hoặc module path trong launcher/build tool | Gọi `addURL` giữa lúc process đã run mà không kiểm soát trạng thái. |
| Hot reload plugin / đổi version | Thiết kế loader mới theo thế hệ, quản lý API chung và lifecycle | Append JAR mới rồi trông đợi class đã nạp tự được thay. |
| Tìm nguồn của một class cụ thể | Quan sát `clazz.getClassLoader()`, code source khi có, debug class loading | Coi `getURLs()[0]` chắc chắn chứa class ấy. |

Một loader riêng có parent = `ClassLoader.getPlatformClassLoader()` như demo thường dùng tốt cho plugin không phụ thuộc các interface riêng của host. Nếu plugin cần implement interface của host, parent nên là loader **nhìn thấy cùng định nghĩa host API**, nếu không có thể xảy ra bất tương thích kiểu dù binary name giống nhau.

## 10. Cách debug khi addURL không cho kết quả mong muốn

Trường hợp `append(pluginUrl)` xong vẫn gặp `ClassNotFoundException`:

1. **Xác nhận instance:** mã đang gọi `loadClass` trên **đúng loader đã append** chưa? Có phải framework đang dùng TCCL/system/child khác?
2. **Xem `getURLs()`:** URL đã nằm ở cuối path chưa? Nếu không, kiểm tra `null`, duplicate, trạng thái đã close hoặc wrapper có thực sự gọi `super.addURL`.
3. **Kiểm tra URL scheme:** directory URL nên kết thúc bằng `/`, JAR path nên trỏ file JAR. Dùng `Path.toAbsolutePath().toUri().toURL()` để tránh lỗi escape đường dẫn.
4. **Kiểm tra file/entry:** `jar tf plugin.jar` có đúng `com/acme/Plugin.class` không? Binary name đưa `loadClass` phải là `com.acme.Plugin`, không phải `com/acme/Plugin.class`.
5. **Kiểm tra parent delegation:** parent có định nghĩa trước một class cùng tên hoặc chưa? Nếu có thì child có thể không đọc entry mới.
6. **Kiểm tra dependency transitively:** JAR plugin có class đích nhưng thiếu các API/base class/interface cần cho verifier/linking không?
7. **Đọc exception đầy đủ:** `ClassNotFoundException` khác `NoClassDefFoundError`, `ClassFormatError`, `LinkageError` hoặc `ClassCastException`. Không gộp mọi lỗi nạp/lần gọi thành “addURL không hoạt động”.
8. **Xác minh lifecycle:** loader có bị `close()` hay thay bằng instance khác? Có thread còn giữ loader cũ không?
9. **Không dựa trên thứ tự ngẫu nhiên:** nếu hai URL chứa trùng class/resource, chọn thứ tự tường minh và tránh duplicate dependencies.
10. **Không lấy `getURLs` làm chứng minh cuối:** cần quan sát `Class<?> clazz` của kết quả thực, `clazz.getClassLoader()`, resource URLs và nguồn deployment khi thích hợp.

Cách quan sát tài nguyên của loader được điều khiển:

```java
// Snippet trong method có một URLClassLoader named loader:
URL[] configured = loader.getURLs();
for (URL url : configured) {
    System.out.println("configured: " + url);
}

var found = loader.getResource("com/acme/config.properties");
System.out.println("found: " + found);
```

`found` có thể đến từ parent hoặc URL của loader theo quy tắc tìm tài nguyên. Đừng dùng đường dẫn in ra để suy diễn không có security/encapsulation issues; trong named modules một số resources còn chịu module access rules.

HotSpot có logging `-Xlog:class+load=info` để xem nguồn tải class:

```bash
java -Xlog:class+load=info -cp build/app UrlPathsDemo
```

Lệnh này in thêm nhiều dòng JVM nên **không dùng làm chuẩn stdout của thí nghiệm mục 6**. Class loading log không phải một bản ghi đầy đủ tất cả lookup URL/resource.

## 11. Lỗi phổ biến và cách sửa

| Lỗi hoặc giả định | Vì sao sai | Cách sửa |
| --- | --- | --- |
| `URLClassLoader addURL` được gọi trực tiếp từ class bất kỳ | `addURL` là `protected`. | Subclass tạo wrapper có quyền và kiểm soát nguồn. |
| Sau append, `getURLs()` tăng nên mọi class trong JAR đã load | API chỉ thay search path. | Chỉ load class khi cần; kiểm tra `Class<?>` thực trả về. |
| `getURLs()` trả 1 URL nên loader chỉ thấy 1 nguồn | Parent delegation vẫn có thể cung cấp classes. | Phân tích cả parent và loader graph. |
| Thêm URL trùng hai lần để đổi priority | `addURL` bỏ qua URL trùng. | Tạo loader mới với thứ tự path mong muốn. |
| Append `null` sẽ tạo entry placeholder | API bỏ qua `null`. | Kiểm tra/validate cấu hình đầu vào. |
| Thêm v2.jar sẽ cập nhật class v1 đã nạp | Class identity gắn với defining loader và binary name. | Tạo loader mới cho thế hệ plugin, quản lý API chung. |
| Cast system loader sang URLClassLoader để addURL | JDK 9+ built-in app loader không phải URLClassLoader. | Dùng own loader, classpath launch-time hoặc đúng framework API. |
| Sau close append vẫn dùng được | `addURL` sau close không có tác dụng. | Tạo loader mới hoặc giữ loader mở đúng vòng đời. |
| URL directory thiếu dấu `/` cuối | URLClassLoader có thể hiểu sai loại nguồn. | Dùng `Path.toUri().toURL()`, kiểm tra URL. |
| `getURLs` hiển thị URL nhưng `findClass` không thấy class | URL có thể hỏng, thiếu entry hoặc quyền truy cập. | Kiểm tra tệp, archive và class-file path. |
| Parent có class cùng tên nhưng muốn plugin override | Parent-first chọn parent trước. | Thiết kế package/loader isolation có chủ đích; tránh override tùy tiện. |
| Gọi close đồng thời với loadClass | Javadoc quy định kết quả của lần load đang diễn ra là không xác định. | Đồng bộ lifecycle của plugin loader. |

## 12. Câu hỏi ôn tập có đáp án

1. **`getURLs()` có liệt kê JDK bootstrap/platform module classes không?**  
   Không. Nó trả search URLs được cấu hình cho chính URLClassLoader, còn parent/modules hoạt động riêng.
2. **`addURL` là public method?**  
   Không, nó là `protected`. Từ một subclass được phép, dùng `super.addURL(url)`, hoặc tạo loader mới với constructor URLs.
3. **`addURL(null)` làm gì?**  
   Không có tác dụng theo Javadoc.
4. **Append lại URL đã tồn tại có làm tăng `getURLs().length`?**  
   Không; URL trùng bị bỏ qua.
5. **Trong loader mặc định parent-first, nếu parent đã load class `p.X` thì append JAR chứa `p.X` vào child có thay được không?**  
   Không; lookup sẽ tiếp tục có thể trả class do parent định nghĩa.
6. **Thêm JAR vào loader có lập tức initialize mọi class trong đó không?**  
   Không; nó chỉ mở rộng nguồn tìm, không gọi constructors/static initializers.
7. **Vì sao `ClassNotFoundException` trước append trong demo được giải quyết sau append?**  
   Vì lời gọi `loader.loadClass` trực tiếp sau đó dùng đường tìm mở rộng và tìm thấy class chưa được định nghĩa trước đó.
8. **Có thể dựa vào việc append để chữa mọi `NoClassDefFoundError` đã xảy ra lúc linking?**  
   Không. Resolution/loading có semantics riêng; cần thiết kế initialization và dependency loading nhất quán.
9. **`close()` có làm mất Class<?> đã được định nghĩa?**  
   Không. Các class đã load vẫn có thể tiếp tục được dùng; close chủ yếu hạn chế load mới và đóng tài nguyên mở.
10. **Khi plugin cần interface của host, parent nên chọn gì?**  
    Loader có thể cung cấp cùng định nghĩa host API/interface; không tự động chọn platform loader nếu plugin cần host types.
11. **Sửa phần tử trong mảng `getURLs()` có phải cách append chính thức?**  
    Không. `getURLs` dùng để xem đường tìm; append là method riêng `addURL` theo quyền truy cập và lifecycle.
12. **Nếu `getURLs()` có ba URL nhưng ClassLoader.loadClass tìm được class từ parent, class defining loader là ai?**  
    Parent (nếu parent trực tiếp define class), không nhất thiết URLClassLoader chứa ba URL.

## 13. Nguồn tham khảo chuẩn

- [Java SE 21 — URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): `getURLs`, `addURL`, `findClass`, constructors, `close` và giới hạn khi loader đóng.
- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findLoadedClass`, parent delegation và type identity.
- [Java SE 21 — URL](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URL.html): URL protocol, equality và cú pháp đường dẫn.
- [Java SE 21 — Path.toUri](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Path.html#toUri()): chuyển directory/JAR path thành URI đúng định dạng.
- [Java SE 21 — JarFile](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/jar/JarFile.html): cơ chế archive khi cần đọc/kiểm tra entries.
- [JVM Specification `5.3](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): binary name và defining class loader.
- [Oracle JDK 9 Release Notes](https://www.oracle.com/java/technologies/javase/9-relnotes.html): thay đổi built-in application loader; không còn subtype URLClassLoader.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="url-class-loader-006">Quản lý vòng đời bằng close và try-with-resources</a>

<details>
<summary>Xem chi tiết</summary>

# Quản lý vòng đời URLClassLoader bằng close và try-with-resources

## 1. Vì sao URLClassLoader có vòng đời tài nguyên?

`java.net.URLClassLoader` là một triển khai `ClassLoader` có khả năng tìm class và tài nguyên từ một tập URL: thư mục, tệp JAR và các nguồn URL được hỗ trợ. Nó thường xuất hiện trong **plugin system**, công cụ chạy mã động, trình biên dịch/test runner và các môi trường cần thêm thư viện lúc runtime.

Khi tìm class trong một JAR, loader có thể mở tệp JAR và duy trì cấu trúc truy cập để phục vụ những lần nạp sau. Vì vậy, ngoài nhiệm vụ định nghĩa class, `URLClassLoader` còn có **tài nguyên I/O cần được đóng**.

Từ Java 7, `URLClassLoader` triển khai `java.io.Closeable` (và qua đó là `AutoCloseable`):

```java
public class URLClassLoader
        extends SecureClassLoader
        implements Closeable {

    public void close() throws IOException;
}
```

Đây là **trích đoạn khai báo API**, không phải code để tự định nghĩa lại class JDK.

**Nguyên tắc quan trọng nhất:** gọi `close()` là **đóng khả năng tìm class/resource mới từ các URL mà loader quản lý, đồng thời cố gắng giải phóng các tệp đã mở**. Nó **không đồng nghĩa** hủy mọi class đã được định nghĩa, dừng thread, chạy bộ hủy đối tượng, hoặc ép JVM unload class.

## 2. Chính xác close() làm gì?

Theo hợp đồng chính thức của `URLClassLoader.close()`:

1. **Đóng loader đối với việc tìm class/resource mới do chính loader này định nghĩa**: sau khi đóng, những class hay resource chưa được loader này tìm/nạp trước đó không còn được tải mới từ URL search path của nó theo cách bình thường.
2. **Các class/resource thuộc parent loader vẫn truy cập được** qua chuỗi ủy quyền khi phù hợp.
3. **Các class/resource đã được nạp vẫn có thể sử dụng**. Đối tượng `Class<?>`, class đã định nghĩa và các instance hiện có không bị biến mất chỉ vì loader đóng.
4. Với nguồn `jar:` và `file:`, loader cố gắng **đóng các tệp đã mở** khi phục vụ việc tìm kiếm. Điều này đặc biệt quan trọng để tránh giữ file handle/JAR bị khóa trên hệ điều hành.
5. **Một lần đóng nữa trên loader đã đóng không có tác dụng**; không cần viết thêm trạng thái tự tạo chỉ để gọi `close()` đúng một lần.
6. `close()` **có thể ném `IOException`**. Khi có nhiều lỗi I/O trong quá trình đóng, exception đầu tiên được ném ra, các lỗi tiếp theo được gắn vào `getSuppressed()`.

Có một giới hạn concurrency rất quan trọng: nếu thread khác **đang nạp class đúng lúc `close()` được gọi**, tài liệu Java nêu rằng **kết quả của lần nạp đó không xác định**. Vì thế cách quản lý vòng đời đúng phải **ngăn những công việc nạp mới và đợi các công việc đang chạy kết thúc trước khi đóng**; không dùng `close()` như cơ chế ngắt cưỡng bức một hoạt động nạp class.

### Bảng phân biệt hiệu ứng

| Hoạt động | `close()` có bảo đảm thực hiện? |
|---|---|
| Đóng những tệp JAR/file mà URLClassLoader đã mở | **Cố gắng thực hiện**, có thể ném `IOException` |
| Ngăn loader tự định nghĩa class mới từ các URL của nó | **Có**, theo hợp đồng `close()` |
| Tự xóa `Class<?>` đã nạp | **Không** |
| Vô hiệu hóa các object đã được tạo từ class plugin | **Không** |
| Dừng các thread, executor, timer do plugin khởi động | **Không** |
| Gỡ TCCL khỏi thread đang giữ plugin loader | **Không** |
| Xóa `static` caches, listener registrations hay ThreadLocal | **Không** |
| Ép JVM garbage collect hoặc unload class ngay | **Không** |
| Bảo đảm JAR có thể được xóa trong mọi hoàn cảnh | **Không**; nơi khác vẫn có thể giữ file |
| Tự làm class loader khác đổi phiên bản của class đã nạp | **Không** |

Tóm lại: `close()` giải quyết **một phần vòng đời tài nguyên I/O**, còn vòng đời plugin/ứng dụng là trách nhiệm lớn hơn.

## 3. URLClassLoader tìm class như thế nào trước và sau khi đóng?

Với một `URLClassLoader` dùng cơ chế `ClassLoader.loadClass` mặc định, thứ tự thường là:

```text
loadClass("plugin.Greeter")
       │
       ▼
findLoadedClass(name)
       │ đã nạp → dùng class cũ
       │ chưa nạp
       ▼
parent.loadClass(name) / bootstrap
       │ parent tìm thấy → dùng class của parent
       │ không tìm thấy
       ▼
URLClassLoader.findClass(name)
       │ trước close: tìm từ URL search path
       │ sau close: không tìm được class mới từ URL này
       ▼
Class<?> hoặc ClassNotFoundException
```

Vì vậy:

- Nếu **class đã được nạp trước khi đóng**, `loader.loadClass(name)` có thể trả lại **cùng đối tượng `Class<?>`** sau khi đóng.
- Nếu **class chỉ tồn tại ở plugin JAR và chưa từng được nạp**, sau khi đóng thì loader không còn con đường bình thường để định nghĩa class mới đó; `loadClass` có thể kết thúc bằng `ClassNotFoundException`.
- Nếu **class do parent hoặc bootstrap cung cấp**, ví dụ `java.lang.String`, parent delegation có thể tiếp tục hoạt động sau khi URLClassLoader đóng.

Đừng hiểu `close()` là đặt cờ làm **mọi lời gọi `loadClass`** thất bại. Nó chủ yếu chặn phần **tìm và nạp mới do chính URLClassLoader đảm nhiệm**.

Ngược lại, đừng hiểu “class đã nạp vẫn dùng được” là mọi thao tác của nó **luôn** chạy thành công: một phương thức đã nạp có thể về sau tham chiếu **class phụ thuộc chưa được nạp**, cần resource mới, hoặc cố mở lại tài nguyên trong JAR. Những hoạt động đó có thể thất bại vì loader đã đóng. Vì thế vẫn phải ngừng công việc plugin **trước** khi đóng loader.

## 4. Ví dụ đầy đủ: đóng loader nhưng class đã nạp vẫn tồn tại

Ta tạo một plugin JAR chứa hai class: `demo.Greeter` và `demo.Later`. Chương trình host chỉ nạp `Greeter` trước khi đóng. Sau đó ta thử dùng lại `Greeter`, nạp `Later`, truy cập `String` từ parent và gọi `close()` thêm lần nữa.

### 4.1. File src/demo/Greeter.java

```java
package demo;

public final class Greeter {
    static {
        System.out.println("Greeter initialized");
    }

    public static String say() {
        return "hello from plugin";
    }
}
```

### 4.2. File src/demo/Later.java

```java
package demo;

public final class Later {
    public static String name() {
        return "late class";
    }
}
```

Class `Later` cố ý **không được nạp trước khi đóng**. Nó tồn tại trong archive để kiểm chứng trường hợp loader không còn được phép tìm class mới từ JAR.

### 4.3. File LoaderLifecycleDemo.java

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class LoaderLifecycleDemo {
    public static void main(String[] args) throws Exception {
        Path pluginJar = Path.of("plugin.jar").toAbsolutePath();
        URL[] urls = {pluginJar.toUri().toURL()};

        URLClassLoader loader = new URLClassLoader(
                urls,
                LoaderLifecycleDemo.class.getClassLoader()
        );

        Class<?> greeter;
        try (loader) {
            System.out.println("inside scope");

            greeter = Class.forName("demo.Greeter", false, loader);
            System.out.println("defined by child: "
                    + (greeter.getClassLoader() == loader));

            System.out.println("before invocation");
            System.out.println(
                    greeter.getMethod("say").invoke(null)
            );
        }

        System.out.println("after close");

        Class<?> same = loader.loadClass("demo.Greeter");
        System.out.println("loaded class reused: " + (same == greeter));

        System.out.println("existing method still callable: "
                + greeter.getMethod("say").invoke(null));

        System.out.println("parent String available: "
                + (loader.loadClass("java.lang.String") == String.class));

        try {
            loader.loadClass("demo.Later");
            System.out.println("unexpected");
        } catch (ClassNotFoundException expected) {
            System.out.println("new plugin class unavailable");
        }

        loader.close();
        System.out.println("second close completed");
    }
}
```

`try (loader)` là cú pháp Java 9+ cho một biến resource **đã được khai báo bên ngoài**, miễn là biến đó final hoặc effectively final. Trong ví dụ, không gán lại biến `loader`, nên code biên dịch được trên Java 17+. Nếu cần hỗ trợ Java 7/8 ở mức source, dùng mẫu `try (URLClassLoader loader = new URLClassLoader(...))`, nhưng khi đó không thể truy cập biến loader ngoài scope của `try` chỉ bằng tên đó.

### 4.4. Tạo JAR và chạy

Từ một thư mục làm việc chứa `LoaderLifecycleDemo.java` và hai file `src/demo/*.java`, chạy bằng JDK 17+.

Trên Bash:

```bash
mkdir -p plugin-classes
```

Trên Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force plugin-classes | Out-Null
```

Sau đó:

```bash
javac -d plugin-classes src/demo/Greeter.java src/demo/Later.java
jar --create --file plugin.jar -C plugin-classes .
javac LoaderLifecycleDemo.java
java -cp . LoaderLifecycleDemo
```

Cấu trúc sau build:

```text
project/
├── LoaderLifecycleDemo.java
├── LoaderLifecycleDemo.class
├── plugin.jar
├── src/
│   └── demo/
│       ├── Greeter.java
│       └── Later.java
└── plugin-classes/
    └── demo/
        ├── Greeter.class
        └── Later.class
```

**Điều kiện quan trọng để kết quả đúng:** chạy host với `-cp .`, **không** thêm `plugin.jar` hoặc `plugin-classes` vào application classpath. Nếu parent loader thấy class plugin trước, parent-first delegation có thể trả class từ parent và làm sai ý nghĩa thí nghiệm.

Stdout trên JDK 17+ với điều kiện trên:

```text
inside scope
defined by child: true
before invocation
Greeter initialized
hello from plugin
after close
loaded class reused: true
existing method still callable: hello from plugin
parent String available: true
new plugin class unavailable
second close completed
```

### 4.5. Phân tích kết quả

1. `Class.forName("demo.Greeter", false, loader)` yêu cầu tìm/nạp Greeter nhưng **không yêu cầu chạy static initializer**. `defined by child: true` cho biết URLClassLoader trong ví dụ là defining loader của Greeter.
2. `getMethod("say").invoke(null)` gọi static method, nên JVM khởi tạo Greeter và in `Greeter initialized` một lần.
3. Cuối block `try (loader)`, Java tự gọi `loader.close()`. Ta không cần gọi close thủ công trong `finally`.
4. Sau đó `loader.loadClass("demo.Greeter")` trả class đã ghi nhận. `same == greeter` là `true`: loader đã đóng **không xóa runtime class**.
5. Method `say()` đã được nạp và không đòi dependency/plugin resource mới, nên ví dụ vẫn gọi được sau close. Đây **không phải khuyến nghị tiếp tục chạy plugin sau close**.
6. `java.lang.String` do bootstrap cung cấp, nên tìm qua parent/boot vẫn hoạt động.
7. `demo.Later` đã có trong JAR nhưng **chưa được nạp** trước close. URLClassLoader không được tìm/định nghĩa nó từ JAR nữa nên trả `ClassNotFoundException`.
8. Gọi `close()` thêm lần nữa là **no-op** theo hợp đồng. Không có dòng `Greeter initialized` lần thứ hai.

Ví dụ cho thấy ranh giới giữa **trạng thái “loader đóng”** và **trạng thái “class đã định nghĩa”**. Đó là hai khái niệm riêng.

## 5. Mẫu chuẩn: try-with-resources

Khi chương trình tự tạo `URLClassLoader` để chạy một công việc có phạm vi hữu hạn, cách thông thường là:

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class ScopedLoaderDemo {
    public static void main(String[] args) throws Exception {
        URL jarUrl = Path.of("plugin.jar").toUri().toURL();
        ClassLoader parent = ScopedLoaderDemo.class.getClassLoader();

        try (URLClassLoader pluginLoader =
                     new URLClassLoader(new URL[] {jarUrl}, parent)) {
            Class<?> type = pluginLoader.loadClass("demo.Greeter");
            System.out.println("loaded: " + type.getName());
        }
    }
}
```

Với `plugin.jar` từ ví dụ trước, biên dịch/chạy:

```bash
javac ScopedLoaderDemo.java
java -cp . ScopedLoaderDemo
```

Stdout:

```text
loaded: demo.Greeter
```

Chỉ nạp class không yêu cầu chạy `Greeter.<clinit>`, nên không in `Greeter initialized`. Khi block `try` kết thúc bình thường hoặc kết thúc bởi exception, `close()` sẽ được gọi tự động.

Lợi ích:

- Dễ nhìn thấy **ai sở hữu loader** và **đến lúc nào nó phải đóng**.
- Đảm bảo cố gắng đóng tài nguyên ở cuối scope, kể cả khi logic trong block ném exception.
- Không cần lặp lại `close()` ở mọi nhánh `return`, `throw` hoặc `catch`.
- Hạn chế quên đóng loader khi build tool, plugin host hoặc test runner gặp lỗi.

**Không bọc URLClassLoader trong try-with-resources quá sớm nếu plugin phải sống lâu hơn scope đó.** Ví dụ một phương thức `createPlugin()` nạp class bằng `try (URLClassLoader loader = ...)` rồi trả đối tượng plugin cho caller có thể đóng loader trước khi caller sử dụng class khác/resource của plugin. Lỗi có thể chỉ xuất hiện khi chạy tính năng ít dùng.

## 6. try-with-resources đóng vào thời điểm nào?

Cấu trúc:

```java
try (URLClassLoader loader = new URLClassLoader(urls, parent)) {
    usePlugin(loader);
}
// Tại đây close() đã được gọi.
```

là **mã minh họa**, các biến `urls`, `parent` và method `usePlugin` cần được định nghĩa trong ứng dụng.

Java đóng resource **sau khi thân try kết thúc và trước khi chuyển sang code phía sau**. Dù thân try có `return` hay ném exception, Java vẫn cố đóng resource.

Với nhiều resources:

```java
try (
    URLClassLoader loader = new URLClassLoader(urls, parent);
    java.io.InputStream stream = loader.getResourceAsStream("settings.txt")
) {
    // Sử dụng loader và stream.
}
```

Đây là **trích đoạn**: `urls`, `parent` là giá trị có sẵn, và `stream` có thể `null` nếu resource không tồn tại. Khi có nhiều resources, chúng được đóng theo **thứ tự ngược với thứ tự khai báo**: `stream.close()` trước, `loader.close()` sau. Đây là thứ tự hợp lý để hoàn thành việc đọc resource trước khi đóng chính loader/JAR.

Với `URLClassLoader.getResourceAsStream`, tài liệu còn cho biết các input streams được mở qua phương thức này có thể bị đóng khi loader đóng. Tuy vậy, **không nên dựa vào loader để dọn thay mọi stream**: đóng stream ngay tại nơi sử dụng (try-with-resources riêng) giúp quản lý tài nguyên rõ ràng và tránh giữ resource lâu hơn cần thiết.

Đừng đọc tiếp một `InputStream` đã bị đóng và kỳ vọng nội dung còn nguyên. Việc mở một URL resource lấy được trước close bằng `url.openStream()` cũng không có bảo đảm tất cả underlying handles vẫn khả dụng sau khi loader đã giải phóng tài nguyên.

## 7. Xử lý IOException khi đóng loader

`URLClassLoader.close()` khai báo:

```java
public void close() throws IOException
```

Nó cố đóng các tệp đã mở. Nếu một hoặc nhiều thao tác đóng thất bại, `IOException` có thể được ném ra. Nếu nhiều lỗi I/O được phát hiện, lỗi đầu tiên được ném, những lỗi tiếp theo có thể nằm trong `getSuppressed()`.

Có hai kiểu exception phải xét:

1. **Exception từ logic đang chạy trong thân try**: ví dụ provider khởi tạo thất bại hoặc code xử lý ném exception.
2. **Exception từ `close()`**: I/O khi giải phóng JAR/file resources.

Với try-with-resources, nếu cả hai cùng xảy ra, Java giữ exception từ **thân try làm exception chính**, và gắn lỗi đóng vào danh sách **suppressed**. Điều này giúp không che mất nguyên nhân ban đầu.

### Ví dụ chạy được minh họa suppressed exceptions

Đây là một resource mô phỏng, **không phải bản triển khai lại URLClassLoader**. Nó chỉ thể hiện ngữ nghĩa chung của try-with-resources khi `close()` lỗi.

Tạo `SuppressedDemo.java`:

```java
import java.io.IOException;

public final class SuppressedDemo {
    static final class FailingResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("closing");
            throw new IOException("close failed");
        }
    }

    public static void main(String[] args) {
        try (FailingResource resource = new FailingResource()) {
            throw new IllegalStateException("work failed");
        } catch (Exception ex) {
            System.out.println("primary: " + ex.getMessage());
            System.out.println("suppressed count: "
                    + ex.getSuppressed().length);
            System.out.println("first suppressed: "
                    + ex.getSuppressed()[0].getMessage());
        }
    }
}
```

Chạy:

```bash
javac SuppressedDemo.java
java SuppressedDemo
```

Stdout:

```text
closing
primary: work failed
suppressed count: 1
first suppressed: close failed
```

Điểm mấu chốt: lỗi gốc `work failed` không bị lỗi đóng `close failed` ghi đè. Khi cần chẩn đoán, xem cả `getCause()` lẫn `getSuppressed()`.

**Nếu thân try không có lỗi nhưng close() ném IOException**, lỗi đóng trở thành exception chính và phải được xử lý/khai báo. Với chương trình đơn giản, có thể khai báo `main(String[] args) throws Exception` như ví dụ mục 4. Với ứng dụng thật, quyết định báo lỗi/ghi log/rollback theo chính sách rõ ràng.

### Bắt lỗi khi đóng thủ công

Trong một lifecycle manager có phương thức `stop()`, đôi khi cần đóng loader sau nhiều thao tác cleanup:

```java
// Trích đoạn: loader là URLClassLoader do manager sở hữu.
try {
    loader.close();
} catch (java.io.IOException ex) {
    System.err.println("Failed to close plugin loader: " + ex);
    for (Throwable suppressed : ex.getSuppressed()) {
        System.err.println("Suppressed close error: " + suppressed);
    }
}
```

Không nên âm thầm nuốt IOException: nếu tệp JAR vẫn đang bị giữ mở, thao tác thay thế/xóa JAR sau đó có thể thất bại, nhất là ở hệ điều hành có file locking nghiêm ngặt.

## 8. Đóng loader không có nghĩa unloading classes

Cần phân biệt bốn khái niệm:

| Khái niệm | Ý nghĩa |
|---|---|
| **Đóng loader** | `URLClassLoader.close()` đóng nguồn URL/JAR và ngăn tìm class/resource mới của nó |
| **Ngừng plugin** | Ứng dụng dừng tác vụ, unregister listeners, đóng tài nguyên plugin và không tiếp tục gọi plugin |
| **Loader không còn reachable** | Chương trình đã bỏ các strong references đến loader/class/instances khiến loader không thể được truy cập từ GC roots |
| **Class unloading** | JVM có thể thu gom metadata/classes tương ứng trong quá trình GC khi đủ điều kiện |

**`close()` không yêu cầu JVM unload ngay**. Class unloading là hoạt động do JVM quản lý, tùy reachability và chính sách GC; `System.gc()` cũng chỉ là một lời yêu cầu, không phải bảo đảm.

Ví dụ nguồn giữ loader sống:

```text
Thread pool worker
     │
     └── thread.contextClassLoader ───────┐
                                         ▼
Global static registry ──> Plugin object ──> PluginClassLoader
                                         ▲
Cached Class<?> / Method ────────────────┘
```

Mô hình này là sơ đồ **các đường tham chiếu có thể xảy ra**, không phải mọi đối tượng đều bắt buộc có đúng chuỗi reference như trên.

Một thread pool giữ TCCL trỏ đến plugin loader đã `close()` vẫn có thể ngăn GC thu gom loader. Một cache lưu `Class<?>`, `java.lang.reflect.Method`, constructor, proxy, callback hoặc object plugin cũng có thể kéo dài tuổi thọ runtime class tương ứng. Plugin tự tạo thread và không dừng thread có thể giữ classloader qua target/context hoặc static fields.

**Vì vậy:** muốn redeploy/reload thực sự, vừa phải **đóng nguồn I/O**, vừa phải **dọn các tham chiếu và công việc** liên quan đến plugin. Không được dùng kết quả `URLClassLoader.close()` thành công như bằng chứng JVM đã giải phóng toàn bộ bộ nhớ class metadata.

## 9. Vòng đời plugin: create → start → use → stop → close

Với loader phục vụ một plugin **lâu dài**, try-with-resources quanh toàn bộ vòng đời hợp lý hơn try quanh **chỉ bước nạp class đầu tiên**.

```text
[1] Create URLClassLoader
           │
           ▼
[2] Load/initialize plugin entry point
           │
           ▼
[3] Start plugin; đăng ký task/listener
           │
           ▼
[4] Use plugin qua các request
           │
           ▼
[5] Ngừng nhận request mới
           │
           ▼
[6] Chờ task đang chạy kết thúc (graceful shutdown)
           │
           ▼
[7] Unregister listener, stop executor,
    đóng stream/socket/db connection của plugin,
    dọn caches/TCCL/reference
           │
           ▼
[8] URLClassLoader.close()
           │
           ▼
[9] Bỏ references đến class/loader;
    JVM có thể unload khi đủ điều kiện
```

Thứ tự **ngừng công việc trước khi đóng loader** rất quan trọng vì việc nạp class cùng lúc với `close()` không có kết quả được bảo đảm. Không dùng `close()` để ép tất cả task kết thúc tức thời.

### Mẫu lifecycle scope mức cao

```java
// PSEUDOCODE / trích đoạn: PluginRuntime và các method
// stopAcceptingWork, awaitQuiescence, ... do ứng dụng cung cấp.
try (URLClassLoader loader = createPluginLoader(pluginJar)) {
    PluginRuntime runtime = startPlugin(loader);
    try {
        runtime.serve();
    } finally {
        runtime.stopAcceptingWork();
        runtime.awaitQuiescence();
        runtime.unregisterCallbacks();
        runtime.closeOwnedResources();
    }
}
// Loader chỉ close sau khi công việc/plugin đã được thu dọn.
```

Không sao chép đoạn trên làm file độc lập: các class và method `PluginRuntime` là mô hình thiết kế, **không phải API chuẩn Java**.

Nếu một bước cleanup thất bại, hệ thống vẫn cần cố gắng thu dọn **những tài nguyên còn lại**, rồi báo các lỗi phù hợp. Có thể dùng một đối tượng quản lý plugin implements `AutoCloseable` với `close()` tự dọn từng resource theo thứ tự; hoặc trong hệ thống phức tạp, dùng lifecycle manager với trạng thái và logging rõ ràng.

### Vì sao không nên trả plugin object ra ngoài scope loader quá ngắn?

Mẫu có vấn đề:

```java
// PSEUDOCODE — minh họa anti-pattern
Object createPlugin(Path jar) throws Exception {
    try (URLClassLoader loader = new URLClassLoader(
            new URL[] {jar.toUri().toURL()},
            getClass().getClassLoader())) {
        return loader.loadClass("demo.Plugin")
                .getDeclaredConstructor()
                .newInstance();
    }
}
// Loader đã bị đóng khi object được trả về!
// Object còn sống nhưng có thể cần dependency/resource mới sau đó.
```

Đừng nghĩ việc method trả về một object plugin đồng nghĩa loader không còn cần thiết. Object đã tạo có thể còn gọi một class chưa được nạp, đọc configuration hay tìm provider từ JAR ở lần sử dụng tiếp theo.

Thiết kế đúng là **đối tượng quản lý vòng đời** sở hữu cả plugin object và URLClassLoader, và chúng được dừng/đóng cùng nhau khi hết phiên sử dụng.

## 10. TCCL và thread pool: dọn một phần vòng đời thường bị quên

Nếu ứng dụng đặt **Thread Context ClassLoader (TCCL)** thành plugin loader để dùng `ServiceLoader` hoặc framework SPI, phải restore TCCL sau khi xử lý.

```java
// Trích đoạn dùng trong một method chạy trên worker thread.
// pluginLoader là đối tượng URLClassLoader phù hợp.
Thread current = Thread.currentThread();
ClassLoader previous = current.getContextClassLoader();

try {
    current.setContextClassLoader(pluginLoader);
    // Gọi framework/service-discovery dùng TCCL nếu cần.
} finally {
    current.setContextClassLoader(previous);
}
```

Nếu một worker thread lâu sống giữ TCCL của plugin cũ sau `close()`, nó có thể giữ **strong reference** đến loader và ngăn class unloading. Nó còn khiến request khác vô tình dùng ngữ cảnh plugin đã dừng.

Với executor:

- Dừng nhận task mới trước khi đóng loader.
- Chờ hoặc hủy tác vụ cũ theo hợp đồng của hệ thống.
- Dọn các ThreadLocal và TCCL do plugin/caller đã đặt.
- Đảm bảo tác vụ tiếp theo không tiếp tục sử dụng class/resource của plugin đã đóng.
- Không giả định việc đổi TCCL trong thread submit task tự đổi TCCL của tất cả worker tồn tại từ trước.

**Không xóa TCCL bằng `setContextClassLoader(null)` một cách tùy tiện.** Trạng thái trước có thể là loader của host hoặc container; phải restore **đúng giá trị cũ**.

## 11. Close và quyền truy cập tệp JAR trên Windows/Linux

Trên Windows, một JAR còn bị giữ handle có thể không đổi tên/ghi đè/xóa được. Trên Unix, file có thể unlink khi descriptor còn mở, nhưng descriptor vẫn tiêu thụ tài nguyên cho tới khi được đóng. `URLClassLoader.close()` cố đóng những handle của **chính loader đó**; một `JarFile`, stream, IDE hoặc tiến trình khác có thể tiếp tục giữ file.

Khi thay plugin, trước hết ngừng việc đang chạy, dọn callback/tài nguyên, gọi `close()` và xử lý `IOException`, rồi triển khai artifact mới. Khả năng xóa file sau close không chứng minh JVM đã unload class.

## 12. Mở loader mới không tự làm cùng tên class trở thành cùng kiểu

Hai `URLClassLoader` **tự định nghĩa** `demo.Greeter` tạo hai runtime types, dù binary name và bytecode giống nhau. Đóng loader cũ không biến instance hoặc `Class<?>` cũ thành định nghĩa của loader mới; host không được tự cast implementation giữa hai thế hệ.

Đặt shared API/DTO ở parent chung, implementation ở loader riêng theo thế hệ; kiểm tra `type.getClassLoader()` trong test reload. Nếu parent định nghĩa luôn implementation thì parent-first có thể che bản mới.

## 13. Theo dõi, debug và xác nhận cleanup

### 13.1. Dấu hiệu tài nguyên chưa được giải phóng

- JAR không thể được thay thế/xóa sau khi plugin stop.
- Số lượng open file descriptors tăng theo mỗi lần reload plugin.
- Sau nhiều lần redeploy, memory dành cho class metadata/metaspace hoặc số lượng class loader instance tăng.
- Threads của plugin tiếp tục chạy sau stop.
- Task mới không tìm được class/resource vì loader đã bị close quá sớm.
- `ClassNotFoundException` chỉ xảy ra khi lần đầu đi vào một tính năng ít dùng sau giai đoạn start plugin.

Đừng kết luận chỉ từ một triệu chứng: JAR lock có thể do nơi khác giữ; metaspace tăng có thể do nhiều nguyên nhân; class unloading không cần xảy ra ngay khi một loader đã không còn được tham chiếu.

### 13.2. Dùng công cụ JVM

Với HotSpot JDK có hỗ trợ unified logging, có thể quan sát class load/unload bằng:

```bash
java -Xlog:class+load=info,class+unload=info -cp . LoaderLifecycleDemo
```

Log có thể dài, phiên bản JDK khác nhau có định dạng khác nhau, và một run ngắn **không bắt buộc** ghi nhận class unloading. Không dùng việc thiếu dòng unload làm bằng chứng chắc chắn rằng `close()` thất bại.

Công cụ như `jcmd <pid> VM.classloader_stats` (nếu JDK/VM cung cấp) và thread dump giúp khảo sát các loader/threads còn sống, nhưng cần diễn giải trong bối cảnh GC và ứng dụng thực tế. Không tự động gọi `System.gc()` liên tục làm giải pháp vận hành để “đóng loader”.

### 13.3. Kiểm chứng close trong test

Một unit/integration test hữu ích nên phân tách các điều cần kiểm:

1. **Loaded before close:** load class plugin, xác minh defining loader.
2. **Closed resource path:** khi test không có race hoặc cache ngoài, các class mới của plugin không được loader cũ định nghĩa nữa.
3. **Parent delegation:** class chuẩn do parent/bootstrap định nghĩa vẫn dùng được.
4. **Cleanup:** executor, callbacks, streams được dừng/đóng và TCCL restore.
5. **Reload:** loader mới dùng JAR mới; API chung vẫn duy trì đúng runtime identity.
6. **Failure path:** throw exception khi đang dùng plugin; kiểm tra close vẫn được gọi; nếu close lỗi, suppressed exceptions được giữ.

Test **không nên** assert JVM phải unload class ngay sau `close()` hoặc ngay sau `System.gc()`: đó không phải bảo đảm portable.

## 14. Những lỗi thường gặp

| Lỗi | Hậu quả | Cách làm đúng |
|---|---|---|
| Không đóng loader sau job hữu hạn | JAR/files có thể bị giữ mở | Dùng try-with-resources hoặc lifecycle close rõ ràng |
| Đóng ngay sau khi load entry-point nhưng plugin còn chạy | Lazy dependency/resource load có thể thất bại | Giữ loader sống suốt thời gian plugin hoạt động |
| Coi close như “xóa class” | Sử dụng sai định danh/vòng đời, kỳ vọng unload vô căn cứ | Phân biệt close, stop, dereference, GC |
| Vừa close vừa có thread nạp class | Kết quả nạp không được xác định | Stop/await quiescence trước close |
| Gọi `loadClass` lại trên loader cũ để lấy phiên bản mới | Nhận lại `Class<?>` đã nạp hoặc CNFE | Tạo defining loader mới, thiết kế migration |
| Quên restore TCCL ở thread pool | Nhầm provider, rò rỉ ClassLoader | `try/finally` restore giá trị trước |
| Chỉ đóng loader, không đóng stream/socket/executor plugin | Rò rỉ tài nguyên và thread | Cleanup toàn bộ plugin-owned resources |
| Nuốt `IOException` khi close | Che lỗi file handle, khó chẩn đoán | Log/propagate và xem suppressed exceptions |
| Chỉ chạy test trên Linux rồi suy ra Windows thay JAR được | File handle khác nhau theo OS | Test file lifecycle trên môi trường triển khai |
| Mở JAR qua `JarFile` hoặc `JarURLConnection` ngoài loader rồi kỳ vọng loader đóng giúp | Vẫn còn handle khác | Đóng tài nguyên ở đúng owner |
| Đưa loader vào static cache lâu sống | Loader/class metadata không giải phóng | Quản lý cache theo phiên plugin, loại bỏ khi stop |

## 15. Checklist thiết kế vòng đời URLClassLoader

Trước khi tạo loader:

- Xác định **ai sở hữu URLClassLoader** và **khi nào kết thúc vòng đời**.
- Xác định các URL có thể trỏ tới JAR/directory nào và có cần isolation giữa các plugin không.
- Đặt API chung ở parent loader thích hợp để tránh class identity conflict.

Trong lúc plugin đang hoạt động:

- Không đóng loader trong khi vẫn còn công việc cần nạp class/resource từ nó.
- Quản lý stream, JDBC connection, sockets, executor, listener, timer và cache do plugin mở/tạo.
- Nếu cần TCCL, set/restore đúng phạm vi sử dụng.

Khi dừng:

- Dừng tiếp nhận task mới; chờ các task đang hoạt động đạt trạng thái an toàn.
- Hủy đăng ký callback/listener, tắt executor, đóng tài nguyên của plugin.
- Restore TCCL và xóa reference từ host caches nếu có.
- Gọi `URLClassLoader.close()`, xử lý `IOException` và suppressed failures.
- Bỏ các reference không còn cần, nhưng **không giả định GC/unload xảy ra lập tức**.

Khi reload:

- Tạo **URLClassLoader mới** cho thế hệ mới.
- Giữ boundary API chung rõ ràng, không trộn class implementation giữa hai loaders.
- Kiểm tra cả hành vi hoạt động và giải phóng tệp/classloader trong integration tests.

## 16. Câu hỏi tự kiểm tra

1. **URLClassLoader triển khai interface nào để dùng trong try-with-resources?** `Closeable`, đồng thời là `AutoCloseable`.
2. **`close()` có ngăn việc loader tự nạp class mới từ URL của nó không?** Có, theo hợp đồng URLClassLoader.
3. **Một class đã nạp có biến mất sau close không?** Không; class đã loaded và object hiện có vẫn có thể truy cập.
4. **Sau close, parent loader có thể vẫn cung cấp `java.lang.String` không?** Có.
5. **`loader.loadClass("demo.Greeter")` sau close có luôn thất bại không?** Không; nếu loader đã initiated class đó, nó có thể trả class cũ.
6. **Có nên tiếp tục chạy plugin sau close chỉ vì một method cũ vẫn gọi được không?** Không; dependency/resource được nạp muộn có thể không còn sẵn.
7. **`close()` có chờ tất cả threads đang nạp class hoàn tất không?** Không có bảo đảm; việc close đồng thời với class loading có kết quả không xác định.
8. **Nếu thân try và close cùng ném exception, exception nào là chính?** Exception trong thân try; close exception được giữ như suppressed.
9. **Gọi close hai lần với URLClassLoader đã đóng có phá hỏng loader không?** Không; lần sau không có tác dụng theo hợp đồng.
10. **Vì sao plugin không unload dù đã close?** Có thể còn strong references từ TCCL, ThreadLocal, cache, callback, Class object hoặc instance.
11. **Thay plugin.jar rồi gọi loadClass từ loader cũ có tạo class mới không?** Không tự tạo; cần loader/runtime class thế hệ mới theo kiến trúc reload.
12. **Với plugin sống lâu, nên đặt try-with-resources ở phạm vi nào?** Bao quanh toàn bộ vòng đời plugin, sau khi công việc đã được dừng và tài nguyên plugin được dọn.
13. **`URLClassLoader.close()` có tự đóng mọi `JarFile` do toàn ứng dụng mở không?** Không; chỉ những file/tài nguyên mà loader chịu trách nhiệm theo API.
14. **Vì sao một JVM test không thấy log class unload dù close thành công?** Unloading do GC, không bắt buộc diễn ra ngay sau close.

## Tài liệu tham khảo chính thức

- [Java SE 21 — URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): `Closeable`, `close()`, class/resource access sau close, hành vi lỗi/concurrency và `getResourceAsStream`.
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): API tương thích ví dụ Java 17 trong bài.
- [Java SE 21 — AutoCloseable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/AutoCloseable.html): hợp đồng đóng tài nguyên.
- [Java Language Specification — `14.20.3 try-with-resources](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3): thứ tự đóng và suppressed exceptions.
- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, `findLoadedClass` và cách nạp class.
- [Java Virtual Machine Specification — `5.3–5.5](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html): class loading, defining loader, linking và initialization.

**Kết luận:** **`close()` đóng nguồn I/O của URLClassLoader, chứ không “đóng” các class Java đã tồn tại.** Dùng **try-with-resources** khi biết rõ phạm vi sở hữu loader; với plugin sống lâu, dùng một lifecycle manager để **stop → cleanup → close → bỏ references**. Phân biệt bốn bước này giúp tránh JAR bị giữ mở, lỗi nạp dependency sau khi đóng và rò rỉ classloader khi redeploy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="url-class-loader-007">Close không đồng nghĩa với gỡ bỏ lớp đã nạp</a>

<details>
<summary>Xem chi tiết</summary>

# URLClassLoader.close() không đồng nghĩa với gỡ bỏ lớp đã nạp

`URLClassLoader` có thể tìm và định nghĩa các lớp từ thư mục hoặc JAR theo một tập URL. Vì class loader này triển khai `Closeable`, ta có thể gọi `close()` hoặc dùng `try-with-resources`. Nhưng **đóng loader không phải lệnh “gỡ tất cả class khỏi JVM”**, không hủy các object đã tạo, không reset static field và không ép garbage collector thu hồi class metadata.

Tài liệu Java SE 17 quy định rõ: sau `URLClassLoader.close()`, loader không còn dùng được để **định nghĩa lớp mới từ nguồn URL của chính nó**, nhưng **các lớp và tài nguyên đã được nạp vẫn có thể truy cập**, và các lớp/tài nguyên do parent định nghĩa vẫn truy cập theo delegation. Mặt khác, **class unloading** là cơ chế quản lý bộ nhớ của JVM, chỉ có thể xảy ra khi defining class loader đủ điều kiện được GC thu hồi; ngay cả khi đủ điều kiện, JVM **không cam kết phải unload ngay**.

Bài thuộc chủ đề **URLClassLoader** trong Java core – classloader. Trọng tâm là phân biệt **close / ngừng nạp mới / class unloading / giải phóng toàn bộ plugin**, quan sát hành vi Java 17+ bằng ví dụ chạy được, hiểu các tham chiếu giữ class loader sống, và thiết kế vòng đời plugin tránh lỗi khóa JAR, memory leak hoặc truy cập phụ thuộc sau khi đóng.

## 1. Ba hành động thường bị đánh đồng

| Hành động | Điều gì thực sự xảy ra? | Điều gì KHÔNG được bảo đảm? |
| --- | --- | --- |
| **`URLClassLoader.close()`** | Đóng tài nguyên do URL loader mở, nhất là các file/JAR phù hợp; ngừng tìm/định nghĩa **class và resource mới từ các URL của chính loader** | Không unload class đã định nghĩa; không hủy object, thread, static state hay mọi handle do plugin tự mở |
| **Bỏ tham chiếu Java đến loader và class/plugin** | Có thể khiến loader và các lớp được nó định nghĩa trở nên đủ điều kiện cho GC | Không đảm bảo GC chạy, loader được thu hồi hay class được unload ngay |
| **Class unloading** | JVM có thể thu hồi metadata/class-related state của class khi defining loader có thể bị GC thu hồi | Không phải API Java gọi trực tiếp theo tên class; không thể yêu cầu unload tùy ý trong một loader còn sống |

**Một câu để nhớ:** `close()` là **kết thúc hoạt động tìm tài nguyên từ các URL của loader và giải phóng những handle thuộc loader**; còn **unloading** phụ thuộc trạng thái sống của toàn bộ đồ thị object/Class/ClassLoader và quyết định của JVM.

`URLClassLoader` là một `ClassLoader` cụ thể. Không phải mọi subclass của `ClassLoader` đều có phương thức `close()` hay cơ chế đóng tài nguyên giống URLClassLoader.

## 2. JavaDoc Java 17 nói chính xác gì về `close()`?

Chữ ký API:

```java
public void close() throws IOException
```

Các bảo đảm/cảnh báo quan trọng trong Java SE 17:

1. **Không còn nạp mới từ nguồn do loader định nghĩa.** Các lớp/tài nguyên mới mà loader phải tự tìm trong URL search path sẽ không được cung cấp sau close.
2. **Class/resource đã được nạp vẫn truy cập được.** Đặc biệt, `Class<?>`, method đã resolve, object đã tạo và static state không tự “biến mất” vì close.
3. **Parent delegation vẫn hoạt động.** Một class được bootstrap/platform/system parent định nghĩa không bị vô hiệu vì child URL loader đóng.
4. **Đóng file đã mở qua URL của loader.** Với các URL dạng `jar:` hoặc `file:` phù hợp, API cố gắng đóng những file/JAR mà loader đã mở trong quá trình tìm class/resource.
5. **Nếu thread khác đang nạp class đồng thời với close, kết quả của lần nạp đang diễn ra không được xác định.** Không dùng `close()` như cơ chế đồng bộ an toàn để “hủy” class loading đang chạy.
6. **Đóng lặp lại không có tác dụng bổ sung.** Gọi close trên loader đã closed không phải lỗi chỉ vì gọi lại.
7. **Có thể ném `IOException`.** Loader cố đóng những file đã mở; nếu có nhiều lỗi I/O, ngoại lệ bổ sung được đính kèm dạng suppressed exceptions. Những unchecked exceptions/errors không được cam kết bị bắt.

Từ các quy tắc trên, câu sau là **sai**:

> “Đã gọi `loader.close()` rồi, chắc chắn không còn method nào thuộc class plugin chạy được nữa.”

Đóng loader **không** chặn CPU thực thi bytecode vốn đã được nạp và đã liên kết. Nó cũng không phát một sự kiện cưỡng bức “destroy all instances”. Bên ngoài còn giữ `Class<?>` hoặc object plugin thì code cũ vẫn có thể chạy.

Cũng tránh khẳng định điều ngược lại quá mức: **mọi phương thức của class cũ đều tiếp tục chạy tốt sau close**. Một phương thức đã nạp có thể **lần đầu cần resolve/nạp thêm một class phụ thuộc** khi được gọi về sau. Nếu dependency ấy chỉ có trong URL của loader đã đóng, lời gọi có thể thất bại dù bản thân method/class ban đầu đã nạp trước close.

## 3. Ví dụ Java 17+ đã chạy kiểm chứng: class cũ vẫn sử dụng được

Ví dụ sau tự chạy bằng JDK 17+ và cố ý tách output của host khỏi output của plugin. Không thêm thư mục plugin vào classpath của host.

### 3.1 Cấu trúc

```text
url-close-demo/
├── source/
│   └── plugin/
│       ├── Hello.java
│       └── NotYet.java
├── CloseDemo.java
├── plugins/       (output .class riêng của plugin)
└── host/          (output của host)
```

Tạo các thư mục nếu chưa tồn tại. `plugins` không được đưa vào `-cp` khi chạy `CloseDemo`, vì ta muốn class của plugin chỉ có thể được tìm qua `URLClassLoader` riêng.

### 3.2 Hai class của plugin

File `source/plugin/Hello.java`:

```java
package plugin;

public class Hello {
    public String message() {
        return "still alive";
    }
}
```

File `source/plugin/NotYet.java`:

```java
package plugin;

public class NotYet {
    public static String value() {
        return "new";
    }
}
```

`Hello` sẽ được load **trước** khi đóng. `NotYet` vẫn có file `.class` tồn tại trong cùng thư mục URL, nhưng **không được yêu cầu load trước close**.

### 3.3 Host tạo rồi đóng URLClassLoader

File `CloseDemo.java`:

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.lang.reflect.Method;

public class CloseDemo {
    public static void main(String[] args) throws Exception {
        URL[] urls = {
            Path.of("plugins").toUri().toURL()
        };

        URLClassLoader loader = new URLClassLoader(
                urls, ClassLoader.getPlatformClassLoader());

        Class<?> cls = loader.loadClass("plugin.Hello");
        Object obj = cls.getDeclaredConstructor().newInstance();
        Method message = cls.getMethod("message");

        System.out.println("before close: " + message.invoke(obj));

        loader.close();

        System.out.println("after close: " + message.invoke(obj));
        System.out.println("same cached class: "
                + (loader.loadClass("plugin.Hello") == cls));
        System.out.println("parent class: "
                + (loader.loadClass("java.lang.String") == String.class));

        try {
            loader.loadClass("plugin.NotYet");
            System.out.println("unexpected");
        } catch (ClassNotFoundException expected) {
            System.out.println("new plugin class: not found");
        }
    }
}
```

Vì `CloseDemo` chỉ dùng reflection để truy cập `plugin.Hello`, nó **không cần import/biên dịch phụ thuộc trực tiếp** vào class plugin. `ClassLoader.getPlatformClassLoader()` làm parent để JDK classes vẫn có thể được delegate tới nền tảng, nhưng không cung cấp `plugin.Hello`.

### 3.4 Biên dịch và chạy

Từ thư mục `url-close-demo`, tạo output directories `plugins` và `host` rồi chạy:

```bash
javac --release 17 -d plugins source/plugin/Hello.java source/plugin/NotYet.java
javac --release 17 -d host CloseDemo.java
java -cp host CloseDemo
```

Ví dụ trên đã được kiểm chứng bằng `javac --release 17` và chạy trên JVM tương thích. Stdout:

```text
before close: still alive
after close: still alive
same cached class: true
parent class: true
new plugin class: not found
```

### 3.5 Giải thích từng dòng

- **`before close: still alive`:** `loader.loadClass("plugin.Hello")` tìm class file trong `plugins`, rồi host tạo một instance và gọi method qua reflection.
- **`after close: still alive`:** object, `Class<?>` và `Method` vẫn còn tham chiếu hợp lệ. `close()` không phá hủy chúng; method đã sẵn sàng vẫn chạy.
- **`same cached class: true`:** với cơ chế `loadClass` mặc định, class mà loader đã khởi đầu nạp có thể được tìm lại qua `findLoadedClass`, ngay cả khi URL search path đã đóng. Đừng hiểu close là xóa class cache.
- **`parent class: true`:** loader đã đóng vẫn có thể nhờ parent tìm `java.lang.String`, là class nền tảng không do plugin loader định nghĩa.
- **`new plugin class: not found`:** `plugin.NotYet.class` tồn tại trong thư mục `plugins` nhưng URL loader đã closed, không còn tìm/định nghĩa mới từ URL ấy. Vì parent cũng không có class này, ta nhận `ClassNotFoundException`.

Ví dụ cố ý gọi `loader.loadClass("plugin.Hello")` sau close để chứng minh class đã load còn truy cập được. **Không xây kiến trúc dựa trên việc tiếp tục dùng một loader đã đóng như loader bình thường**; hãy coi nó đã hết nhiệm vụ nạp mới.

### 3.6 Vì sao chưa đủ kết luận class đã bị unload?

Trong ví dụ, `loader`, `cls`, `obj` và `message` vẫn còn tham chiếu sống trong method. Đặc biệt, chính chương trình đang tiếp tục sử dụng chúng. Class `plugin.Hello` không bị unload chỉ vì close, và **không có bất kỳ kiểm tra GC nào** trong ví dụ. Nó minh họa **khả năng tiếp tục dùng class cũ**, không đo thời gian sống metadata sau khi chương trình kết thúc.

## 4. Điểm tinh tế: một class đã nạp vẫn có thể cần class chưa nạp

JVM không nhất thiết resolve tất cả symbolic references của một class cùng một lúc. Resolution có thể được thực hiện khi cần. Xét ví dụ **minh họa cơ chế**, không phải một file chạy độc lập:

```java
package plugin;

public class Entry {
    public void execute() {
        Dependency.run();
    }
}
```

Nếu `Entry` đã được load trước close nhưng `Dependency` chưa được loader tìm/resolve, lần đầu gọi `execute()` sau close có thể cần nạp `plugin.Dependency`. Lúc đó, việc loader không còn đọc được URL path có thể dẫn đến lỗi linking như `NoClassDefFoundError` hoặc `ClassNotFoundException` được báo trong ngữ cảnh cụ thể. JVM có quyền resolve sớm hơn, nên **không nên dựa vào một thời điểm duy nhất được bảo đảm cho mọi dependency**.

Vì thế hai khẳng định khác nhau:

- **Class đã nạp vẫn có thể sử dụng sau close.** Được JavaDoc bảo đảm ở mức class/resource đã có.
- **Mọi đường thực thi của mọi class đã nạp đều luôn thành công sau close.** Không được bảo đảm vì có thể còn lazy loading/resolution, resource lookup hoặc nghiệp vụ sử dụng file/JAR sau đó.

Một ví dụ thực tế: class plugin được nạp sớm nhưng chỉ đến lần xử lý request thứ 100 mới tạo thêm formatter/codec tùy chọn. Nếu URL loader bị đóng ngay sau bootstrap, chức năng tùy chọn có thể hỏng trong tương lai dù test startup không phát hiện.

## 5. Loading, initialization, close và unloading là bốn thời điểm khác nhau

Để không nhầm trong sơ đồ vòng đời, tách thành các sự kiện:

```text
Tạo URLClassLoader
       |
       v
Tìm Class<?> từ URL / parent
       |
       v
Loading + Linking (theo nhu cầu)
       |
       v
Initialization <clinit> (khi JVM có thao tác yêu cầu)
       |
       v
Object / method / static state được sử dụng
       |
       +------------> URLClassLoader.close()
       |                    |
       |                    +--> đóng handle của loader
       |                    +--> dừng tìm mới từ URL của loader
       |                    +--> KHÔNG tự unload class
       |
       v
Ngừng tác vụ, đóng tài nguyên riêng, loại bỏ các tham chiếu giữ plugin
       |
       v
Defining loader có thể không còn reachable
       |
       v
GC có thể thu hồi loader và unload các class do nó định nghĩa
       (thời điểm không được JVM cam kết)
```

**Loading** tạo/đưa class definition vào JVM. **Initialization** chạy static initializers khi JVM yêu cầu. **`close()`** đóng những nguồn/handle do loader sở hữu. **Unloading** là tối ưu quản lý bộ nhớ khi điều kiện GC phù hợp. Không có quan hệ “gọi close ⇒ ngay sau đó static field trở về `null` hoặc `0`”.

`java.lang.Class` và defining loader có quan hệ bản sắc runtime: cùng một binary name nhưng được hai loader khác nhau **tự định nghĩa** là hai kiểu riêng, với static state riêng. Vì thế khi cần reload version mới, cách thông thường là **tạo loader mới**, không mong instance `URLClassLoader` cũ “quên lớp” đã nạp.

## 6. Điều kiện class unloading trong JLS 17

Java Language Specification `12.7 nói rằng JVM **có thể** unload class/interface nếu và chỉ nếu **defining class loader của nó có thể được garbage collector thu hồi**. Các class được bootstrap loader nạp không thuộc diện unloading theo quy tắc này.

Diễn giải thực tế:

- Nếu một class do plugin loader định nghĩa vẫn có object instance sống hoặc `Class<?>` đang được giữ, metadata/class-loader graph thường chưa đủ điều kiện thu hồi.
- Nếu app/container vẫn giữ **tham chiếu mạnh tới plugin loader**, trực tiếp hoặc gián tiếp, loader không được GC thu hồi nên các class do nó định nghĩa cũng không thể unload.
- Nếu **mọi đường tham chiếu mạnh liên quan** đã được giải phóng, class và loader *có thể* đủ điều kiện, nhưng JVM không bắt buộc chạy GC hoặc unload theo yêu cầu ứng dụng.
- Việc một variable local được gán `null` không đủ để chứng minh graph không còn tham chiếu; class objects, instances, method handles, threads, stacks và native roots có thể còn giữ.
- Class do **bootstrap** định nghĩa như `java.lang.String` không được unload theo JLS 12.7, bất kể một URL loader con đã closed bao nhiêu lần.

**Đúng:** “Sau khi dừng plugin và bỏ toàn bộ strong references liên quan, JVM có thể unload class do loader plugin định nghĩa.”

**Sai:** “`loader = null; System.gc();` sẽ chắc chắn giải phóng ngay mọi class plugin.”

`System.gc()` là yêu cầu/hint tới runtime theo chính sách GC; nó không có hợp đồng bảo đảm unload class ngay hoặc trong một khoảng thời gian nhất định. Cũng không nên xây business logic dựa trên kết quả kiểm tra WeakReference thành `null` ở một mốc thời gian tùy ý.

## 7. Điều gì giữ class loader sống sau close?

Một loader có thể được giữ alive qua nhiều đường. Những nguồn giữ tham chiếu thường gặp ở ứng dụng plugin/app server:

| Nguồn giữ | Ví dụ | Cách xử lý khi dừng plugin |
| --- | --- | --- |
| **Instance plugin** | Host cache giữ `Object plugin` hoặc interface implementation | Loại bỏ khỏi registry/cache; không giữ object sống quá vòng đời |
| **`Class<?>` / reflection** | `Map<String, Class<?>>`, `Method`, `Constructor`, `MethodHandle` | Gỡ metadata/cache gắn với deployment cũ |
| **Thread context class loader** | Thread pool worker còn `setContextClassLoader(pluginLoader)` | Khôi phục TCCL cũ bằng `finally` trong task; kiểm tra worker sống lâu |
| **Thread do plugin tạo** | Thread đang chạy mã plugin và giữ runnable/task class | Yêu cầu dừng, ngắt/chờ có kiểm soát trước close; tránh thread vô hạn |
| **`ThreadLocal` / inheritable context** | Thread pool giữ value do plugin định nghĩa | Xóa đúng nơi tạo; cân nhắc scope task và thread reuse |
| **Static registry ở host** | Event listener, callback, scheduler, SPI cache | Hủy registration, `remove` listener và cache entries |
| **ServiceLoader/cache provider** | Cache host giữ provider instances/classes | Dọn cache/scope theo deployment; tạo lại context phù hợp |
| **Shutdown hooks** | Hook thread/runnable của plugin được đăng ký với runtime | Gỡ hook khi được phép và khi chưa bắt đầu shutdown |
| **JMX/MBean, logging, JDBC, driver registry** | Provider hoặc listener còn đăng ký trong subsystem sống lâu | Unregister/close theo API subsystem |
| **Native/JNI refs, task stacks** | Code vẫn chạy hoặc bridge giữ object/class | Dừng tác vụ an toàn và giải phóng các refs do native code quản lý |

Một điều dễ quên: **close và unload không giải quyết nhau theo thứ tự ngược**. Muốn dừng plugin an toàn thường phải **ngăn yêu cầu mới**, hoàn tất/hủy các công việc đang chạy, đóng tài nguyên do plugin sở hữu, **sau đó mới close loader**, rồi loại bỏ những tham chiếu cuối cùng. Nếu còn thread đang load class trong khi `close()`, JavaDoc nói kết quả load đồng thời ấy không xác định.

### Vì sao static field trong plugin cũng cần xem xét?

Static field thuộc class của plugin có thể giữ các object khác, bao gồm thread/task/registry. Nhưng chỉ riêng một vòng tham chiếu **bên trong cụm object không còn reachable** không tự ngăn garbage collector: GC thông thường có thể thu hồi các vòng không được root tham chiếu. Nguy hiểm thật sự nằm ở **đường tham chiếu từ một GC root hoặc đối tượng sống lâu bên ngoài plugin** tới object/class/loader của plugin.

Vì vậy, không kết luận “có static cache trong plugin ⇒ chắc chắn leak vĩnh viễn”. Hãy phân tích **có đường strong reference từ root tới loader hay không**.

## 8. WeakReference có chứng minh loader đã unload chưa?

Có thể dùng `WeakReference<ClassLoader>` để quan sát tương đối việc một loader còn được strong-reference giữ hay đã có cơ hội được thu hồi. Nhưng phải hiểu giới hạn:

```java
java.lang.ref.WeakReference<ClassLoader> reference =
        new java.lang.ref.WeakReference<>(loader);

// Chỉ sau khi đã dừng plugin, loại bỏ tất cả strong references
// và thoát khỏi scope còn giữ loader:
loader = null;

System.gc(); // Không bảo đảm GC chạy hoặc thu hồi loader ngay.
System.out.println(reference.get());
```

Đây là **đoạn minh họa**, không phải chương trình kiểm chứng unload độc lập. `reference.get()` có thể vẫn không null vì loader đang reachable hoặc đơn giản vì GC chưa diễn ra. Nếu `get()==null`, ta có bằng chứng weak referent loader đã bị clear; điều đó **không phải API cam kết thời điểm mọi class metadata được thu hồi**, cũng không đủ thay thế công cụ đo metaspace và class-unload logs.

Một lỗi test phổ biến là giữ `Class<?> klass` hoặc instance plugin trong biến local/static để “kiểm tra đã unload chưa”. Chính các tham chiếu đó có thể giữ class/loader sống. Test đáng tin hơn thường tạo và dùng loader trong **method scope riêng**, chỉ trả weak reference ra ngoài, đồng thời dọn hết registry/TCCL/resource theo vòng đời.

Khi điều tra thực tế, có thể quan sát:

```bash
java -Xlog:class+unload=info -cp host CloseDemo
```

Cú pháp logging trên dành cho các HotSpot JVM hỗ trợ unified logging, và output phụ thuộc sự kiện GC thực tế. **Không thấy log unload không chứng minh `close()` bị lỗi**; chương trình có thể chưa đủ điều kiện, chưa có GC/unloading, hoặc đã kết thúc quá sớm.

Có thể phối hợp `jcmd` và heap dump/classloader statistics tùy JDK/runtime để tìm **retaining paths**. Không sử dụng `-Xlog:class+load=info` như bằng chứng class đã unload: `load` và `unload` là hai loại sự kiện khác nhau.

## 9. `close()` có giải phóng khóa JAR không?

Một lý do thực tiễn để dùng `URLClassLoader.close()` là **giải phóng những JAR/file handles loader đã mở**. Trên Windows, file đang bị process giữ handle có thể khiến thay, xóa hoặc cập nhật JAR thất bại; đóng URLClassLoader theo contract là bước quan trọng khi undeploy/redeploy plugin.

Nhưng các phát biểu sau **không** tương đương:

- **Đã close URL loader:** loader cố đóng file/JAR thuộc tài nguyên mà chính nó mở theo contract.
- **Chắc chắn mọi stream, socket, DB connection và thread của plugin đều được đóng:** không. Plugin có thể tự mở chúng bên ngoài URLClassLoader.
- **Chắc chắn thao tác ghi đè/xóa JAR trên mọi OS đều thành công:** không. Handle có thể do thành phần khác giữ, hoặc chính `close()` ném `IOException`.
- **Có thể tiếp tục đọc mọi resource sau close:** không bảo đảm. Resource đã nạp/truy cập có thể còn sẵn, nhưng stream do loader theo dõi có thể bị đóng trong quá trình close; nguồn URL đã closed không còn phục vụ resource mới theo cách thường.

Nếu mã plugin gọi `URLClassLoader.getResourceAsStream()`, URL loader có thể theo dõi các stream tương ứng để đóng khi loader closed. Nếu plugin trực tiếp mở `FileInputStream`, `Socket`, `JarFile` hoặc dùng thư viện JNI, những tài nguyên đó **không tự thuộc vòng đời close của URLClassLoader**.

**Quy tắc quản lý tài nguyên:** tài nguyên nào do code nào mở thì nên được code đó **đóng theo hợp đồng riêng**, trừ khi tài liệu API nói rõ quyền sở hữu và trách nhiệm được chuyển giao.

### Xử lý `IOException` khi close

Ví dụ an toàn khi một tác vụ thực sự chỉ cần nạp/đọc class từ loader trong một phạm vi:

```java
URL[] urls = {Path.of("plugins").toUri().toURL()};

try (URLClassLoader loader = new URLClassLoader(
        urls, ClassLoader.getPlatformClassLoader())) {
    // Chỉ làm những thao tác cần class và resource từ URLs.
    Class<?> type = loader.loadClass("plugin.Hello");
    System.out.println(type.getName());
} // loader.close() được gọi; IOException có thể được truyền ra.
```

Đây là **đoạn minh họa**, cần đặt trong method có `throws Exception` hoặc xử lý các checked exceptions. `try-with-resources` cũng có thể lưu lỗi đóng tài nguyên thành suppressed exception nếu phần body đã ném lỗi khác.

Cẩn thận: dùng `try-with-resources` cho loader plugin **ngay trong factory** rồi trả về instance plugin để host dùng nhiều giờ là thiết kế rủi ro. Sau khi factory trả về, loader đã closed; object cũ có thể dùng được ở một số đường code, nhưng lazy dependency loading hoặc resource lookup về sau có thể hỏng.

## 10. Vòng đời plugin đúng: stop trước, close sau, release references cuối cùng

Một plugin host có thể dùng vòng đời khái niệm:

```text
CREATED
   |
   v
LOADING/STARTING
   |
   v
RUNNING
   |
   +-- ngừng tiếp nhận công việc mới
   |
   v
STOPPING
   |
   +-- hoàn tất/hủy task đang chạy
   +-- xóa thread context loader tạm thời
   +-- unregister listeners / scheduled tasks
   +-- plugin.stop(): đóng tài nguyên riêng
   +-- gỡ provider/cache/registry của host
   |
   v
CLOSING_LOADER
   |
   +-- URLClassLoader.close(): đóng JAR/file handles của loader
   |
   v
RELEASED
   |
   +-- bỏ strong references còn lại
   |
   v
ELIGIBLE_FOR_GC (nếu không còn đường tham chiếu mạnh)
   |
   v
MAY_UNLOAD (JVM quyết định, không có deadline)
```

Pseudocode minh họa thứ tự, **không phải class chạy độc lập**:

```java
void unloadPlugin(PluginHandle handle) throws Exception {
    handle.rejectNewRequests();
    handle.awaitOrCancelRunningTasks();

    handle.unregisterCallbacks();
    handle.stopPluginResources();

    // Không chạy task plugin mới trong lúc close.
    handle.classLoader().close();

    handle.clearStrongReferences();
}
```

`PluginHandle` trong đoạn trên là **interface/kiểu minh họa do ứng dụng tự thiết kế**, không phải Java SE API. Nội dung cho thấy ý định lifecycle: phối hợp thread, provider registry và resource ownership, chứ không có ý nghĩa `URLClassLoader.close()` tự gọi các bước đó.

**Không đóng loader khi còn thread khác đang loading.** JavaDoc 17 cảnh báo kết quả của lần load đồng thời với close là undefined. Ngừng nhận yêu cầu và đợi các thao tác loading hoàn tất trước khi đóng là yêu cầu vận hành quan trọng.

Nếu một plugin hỗ trợ chuyển trạng thái “running → stopped” nhưng còn object đã đăng ký được host gọi lại sau shutdown, cần sửa hợp đồng callback và xóa registry. Close không phải cơ chế “ngắt ngay mọi hàm do plugin định nghĩa”.

## 11. Reload plugin cần loader mới, không phải mở lại loader cũ

`close()` không làm loader quên các class đã định nghĩa, và Java SE không có API đảm bảo unload riêng một class đang được sử dụng. Thay bytecode của `plugin.jar` rồi gọi `oldLoader.loadClass(name)` có thể vẫn trả về class cũ.

Để chuyển từ v1 sang v2: ngừng request/tác vụ v1, unregister callback, đóng tài nguyên và loader v1, giải phóng tham chiếu host tới instance/`Class<?>` cũ, rồi tạo **loader mới** cho v2. Giữ API/SPI chia sẻ ở parent để host nhận diện provider v2; implementation cần được loader của từng thế hệ định nghĩa, nếu không parent-first có thể tiếp tục chọn bản cũ.

Hai thế hệ tự định nghĩa cùng binary name vẫn là hai runtime types khác nhau; đừng truyền trực tiếp implementation object cũ sang chỗ đòi class cụ thể của v2.

## 12. Các trường hợp khiến developer tưởng `close()` đã “thành công” nhưng plugin vẫn chiếm bộ nhớ

### 12.1 TCCL trong thread pool

`Thread.currentThread().setContextClassLoader(pluginLoader)` giúp những API sử dụng TCCL khám phá plugin. Nếu không khôi phục sau tác vụ, worker thread còn sống có thể giữ loader cũ **sau khi plugin đã close**:

```java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);
    // Chạy công việc/plugin SPI discovery trong đúng context.
} finally {
    thread.setContextClassLoader(previous);
}
```

Đây là đoạn minh họa, giả định `pluginLoader` tồn tại. Với thread pool, phải quản lý TCCL ở **worker thread thực thi nhiệm vụ**, không chỉ thread submit.

### 12.2 Static registry của host

`HostEventBus.listeners` là collection sống lâu do host class ở parent loader định nghĩa. Nếu add một listener object do plugin loader định nghĩa và quên remove, host vẫn giữ listener dù plugin loader đã close.

```text
GC root → host static registry → listener instance → plugin Class → PluginLoader
```

Cách sửa: đăng ký có registration token/handle, gỡ registration trong `stop()`. Không giải quyết được bằng việc chỉ `loader.close()`.

### 12.3 Thread/Executor do plugin tạo

Nếu plugin tự tạo scheduler/thread và không shutdown, JVM có thể còn task đang chạy hoặc thread chờ nhiệm vụ, cùng stack/context giữ loader/class/plugin instance. Dừng scheduler, chờ termination và xóa task references là phần lifecycle riêng.

### 12.4 Logging/JDBC/JMX và các framework caches

Một framework được nạp ở parent có thể lưu class metadata, provider instances, callback, logger/filter, MBean hoặc driver registration do plugin cung cấp. Khi undeploy, phải gọi API cleanup phù hợp của framework. `URLClassLoader.close()` không biết những registries đó.

### 12.5 File resource ngoài quyền sở hữu loader

Plugin có thể tự gọi `new FileInputStream(...)` hoặc `new JarFile(...)`, giữ stream của bên thứ ba, memory mapped files hoặc native handles. Những resource này cần cleanup riêng. Nếu OS vẫn báo khóa JAR sau close, kiểm tra **ai thực sự đang giữ file handle** thay vì quy toàn bộ cho URLClassLoader.

## 13. Những lỗi thường gặp và cách phân biệt

| Dấu hiệu | Diễn giải có khả năng đúng | Hướng kiểm tra |
| --- | --- | --- |
| Gọi method của plugin sau close vẫn chạy | Class/method đã nạp; close không unload bytecode | Có còn instance/`Class<?>`/method references? |
| `loader.loadClass("plugin.NewType")` sau close báo `ClassNotFoundException` | Loader ngừng tìm class mới từ URL do nó phụ trách | Class có thực sự chưa nạp? Có ở parent không? |
| Method class cũ thất bại do dependency sau close | Dependency có thể chỉ được lazy-load/resolve khi method chạy | Tìm `NoClassDefFoundError`/cause, thời điểm resolve |
| JAR không thể thay trên Windows dù đã close | Handle khác vẫn giữ file hoặc close lỗi | Kiểm tra suppressed `IOException`, streams, native handles |
| Metaspace vẫn cao sau nhiều lần reload | Loader/plugin cũ vẫn reachable hoặc GC chưa unload | Heap dump, TCCL, thread, static cache/registry, unload logs |
| `System.gc()` rồi weak reference vẫn còn | Loader chưa đủ điều kiện hoặc GC chưa clear referent | Kiểm tra retaining paths, không xem GC hint là phép thử chắc chắn |
| V1 và V2 có cùng tên class nhưng không ép kiểu cho nhau | Khác defining loader nên là runtime types khác | Tách API chung ở parent, kiểm tra class identity |
| Reload nhưng vẫn nhận behavior cũ | Parent đã định nghĩa implementation hoặc cache/instance v1 còn sống | Kiểm tra `getClassLoader()`, namespace và registry |
| `IOException` khi `close()` | Có lỗi đóng file do loader mở | Xử lý/ghi log, xem suppressed exceptions |
| Thỉnh thoảng lỗi khi loader đang bị đóng | Racing giữa class load và close | Đồng bộ lifecycle, stop/await trước close |

Cần phân biệt rõ:

- `ClassNotFoundException` thường xuất hiện trong API lookup như `ClassLoader.loadClass` khi tìm class không thành công.
- `NoClassDefFoundError` có thể xuất hiện khi JVM cần class phụ thuộc ở giai đoạn linking/execution mà class ấy không sẵn có, hoặc khi initialization trước đó đã thất bại.
- `ClassCastException` giữa hai class trùng binary name thường liên quan **hai defining loaders khác nhau**, không trực tiếp do close.
- `OutOfMemoryError: Metaspace` trong ứng dụng reload nhiều lần có thể liên quan nhiều loader/class metadata không được thu hồi, nhưng cần chứng cứ retaining references và đo thực tế trước khi kết luận.

## 14. Cách quan sát chính xác hơn khi điều tra leak

Các công cụ hữu ích trên một JDK/HotSpot tương thích:

```bash
java -Xlog:class+load=info,class+unload=info -cp host CloseDemo
```

Tùy môi trường, output sẽ rất nhiều hoặc không có sự kiện unload trước khi chương trình kết thúc. `class+load` giúp quan sát class được load; `class+unload` chỉ in khi runtime thật sự unload.

Có thể dùng lệnh `jcmd` (khi công cụ có sẵn và tiến trình cho phép attach) để xem thống kê loader:

```bash
jcmd <PID> VM.classloader_stats
```

Đây là **lệnh minh họa**: thay `<PID>` bằng process ID thật, không gõ nguyên ký hiệu góc; lệnh/format có thể khác giữa các bản JDK hoặc cần quyền phù hợp. Dữ liệu hữu ích là **số loader, class count, metaspace usage** qua các lần deploy/undeploy.

Để tìm nguyên nhân giữ loader, cần **heap dump và retaining-path analysis**. Mục tiêu là tìm chuỗi tham chiếu đi từ GC root đến plugin loader hoặc class/provider objects. Lúc phân tích, tránh giữ tham chiếu thử nghiệm trong chính ứng dụng hoặc thêm static cache debug mới khiến loader bị giữ sống.

**Không dùng `Runtime.freeMemory()` như phép đo trực tiếp class unloading.** Heap objects, native JAR handles và metaspace class metadata là những loại tài nguyên khác nhau. JVM có thể giữ vùng memory đã cấp phát để tái sử dụng dù loader/class nào đó đã được thu hồi.

## 15. Ví dụ sử dụng loader trong phạm vi hẹp và dài hạn

**Tác vụ hữu hạn:** dùng `try (URLClassLoader loader = new URLClassLoader(urls, parent)) { ... }` khi toàn bộ việc tải và xử lý class/resource kết thúc trong khối; đọc và đóng stream trước khi thoát.

**Plugin dài hạn:** manager giữ cả loader và instance trong suốt quá trình sử dụng. Đóng loader trong factory trước khi trả instance có thể khiến method cũ chạy được nhưng lazy class/resource lookup về sau hỏng. Dừng plugin, chờ task hoàn thành, dọn callbacks, đóng loader rồi gỡ các strong references.

Khác biệt nằm ở **phạm vi sở hữu thực tế**, không ở cú pháp gọi `close()`.

## 16. Câu hỏi tự kiểm tra

**1.** Sau `URLClassLoader.close()`, class đã nạp có biến mất ngay khỏi JVM không?

**Đáp án:** Không. `close()` không gỡ định nghĩa class, object hoặc static state. Class unloading liên quan khả năng GC thu hồi defining loader và là quyết định của JVM.

**2.** Vì sao trong ví dụ, `message.invoke(obj)` vẫn trả `still alive` sau close?

**Đáp án:** Instance, `Class<?>` và `Method` đã được tạo/resolve trước khi loader đóng. Close không phá hủy metadata/bytecode đã nạp.

**3.** `URLClassLoader.loadClass("plugin.Hello")` sau close có thể trả cùng `Class<?>` cũ không?

**Đáp án:** Có trong cơ chế mặc định khi class đã được loader khởi đầu nạp và còn lưu trong lookup state (`findLoadedClass`). Điều này khác với việc tìm/định nghĩa một class hoàn toàn mới từ URL sau close.

**4.** Vì sao có `plugin.NotYet.class` trên đĩa mà lệnh load sau close vẫn báo không thấy?

**Đáp án:** URL loader đã đóng nên không tiếp tục dùng source URL của chính nó để định nghĩa class mới. Parent trong ví dụ cũng không cung cấp class ấy.

**5.** Tại sao không nên đóng loader rồi để plugin tiếp tục nhận request?

**Đáp án:** Các method đã nạp có thể còn hoạt động nhưng những dependency/resource cần nạp lần đầu về sau có thể không truy cập được. Đồng thời close cạnh tranh với thread đang load tạo kết quả không xác định.

**6.** `loader = null; System.gc();` có bảo đảm class đã unload không?

**Đáp án:** Không. Loader có thể còn reachable qua instance, class object, cache, thread/TCCL hoặc registry; GC/unload cũng không bị buộc chạy ngay.

**7.** Để reload JAR v2 có đúng type identity mới, thường cần làm gì?

**Đáp án:** Tạo **URLClassLoader mới** sau khi quản lý dừng/đóng loader v1. Nếu v1 và v2 thực sự do hai defining loaders khác nhau định nghĩa, cùng binary name vẫn là hai runtime types khác nhau.

**8.** Nếu dùng `try-with-resources` trong hàm tạo plugin rồi trả instance, điều gì nguy hiểm?

**Đáp án:** Loader đóng trước khi plugin kết thúc hoạt động. Code plugin có thể còn dùng được một thời gian, nhưng sẽ lỗi khi cần lazy-load các class/resource còn lại.

**9.** Sau close JAR vẫn bị khóa. Có thể kết luận JavaDoc sai không?

**Đáp án:** Không. Có thể có handle do plugin hoặc thư viện khác mở, hoặc lỗi I/O trong quá trình close. Cần điều tra file handle thực tế và exception/suppressed exceptions.

**10.** `Class<?>` của plugin bị giữ trong một static map ở host. `close()` có giúp GC unload class đó không?

**Đáp án:** Close không xóa entry map. Strong reference từ host có thể giữ `Class<?>`/defining loader sống. Phải xóa cache/registry đúng vòng đời và xác minh không còn root references.

## 17. Checklist thiết kế plugin có thể hot-reload

1. **Loader scope:** Mỗi deployment/version có loader riêng, shared API ở parent phù hợp.
2. **Load phase:** Không để lẫn classes của plugin vào application classpath khiến parent-first chọn nhầm class.
3. **Ownership:** Host/plugin xác định ai quản lý threads, scheduler, listener, driver, MBean, stream, JAR handles.
4. **Stop:** Ngừng yêu cầu mới; kết thúc các tác vụ và class loading đang hoạt động.
5. **Unregister:** Gỡ provider/listener/cache/registrations từ các object sống lâu ở host.
6. **Context:** Khôi phục TCCL, xóa `ThreadLocal` của task và kiểm tra thread pools.
7. **Close:** Gọi `URLClassLoader.close()` sau khi không còn công việc cần source URL, ghi nhận `IOException` và suppressed.
8. **Release:** Bỏ tham chiếu mạnh tới loader, instances, `Class<?>` và reflection metadata của deployment cũ.
9. **Reload:** Tạo loader mới cho artifact/version mới, kiểm tra class identity của shared API.
10. **Observe:** Theo dõi classloader stats, class unloading logs, metaspace và heap retaining paths; không dùng `System.gc()` làm tiêu chí thành công duy nhất.

## Kết luận

**`URLClassLoader.close()` ≠ unload classes ≠ shutdown plugin.** Close chủ yếu đóng những nguồn/tài nguyên do loader sở hữu và ngăn nó định nghĩa class mới từ các URL; **class đã nạp còn có thể được sử dụng**. Để JVM **có thể** unload class plugin, phải quản lý toàn bộ vòng đời plugin, bỏ các strong references giữ defining loader sống và để GC quyết định thời điểm thu hồi. Không có API Java SE nào bảo đảm “đóng loader ⇒ class biến mất ngay”.

**Tài liệu chuẩn (Java 17):**

- [`URLClassLoader` Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html) — contract của `close()`, exceptions, URLs/JAR handles, behavior đối với class đã nạp.
- [`ClassLoader` Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html) — `loadClass`, `findLoadedClass` và parent delegation.
- [JLS 17 `12.7 – Unloading of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.7) — điều kiện để class/interface có thể được unload.
- [JVMS 17 – Loading, Linking and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html) — class identity, loader và thời điểm resolution.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="url-class-loader-008">Ảnh hưởng của close tới JAR handle và thao tác tải đang diễn ra</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của close tới JAR handle và thao tác tải đang diễn ra

## 1. Vì sao `URLClassLoader.close()` quan trọng?

Một `URLClassLoader` có thể tải class và tài nguyên từ một hoặc nhiều thư mục/JAR. Khi plugin, module mở rộng hoặc bộ công cụ nạp động không còn được sử dụng, ứng dụng thường muốn giải phóng tài nguyên liên quan tới các JAR đó. Java cung cấp:

~~~java
URLClassLoader loader = new URLClassLoader(urls, parent);
loader.close();
~~~

`URLClassLoader` triển khai `Closeable`/`AutoCloseable` và có phương thức `public void close() throws IOException`. Theo tài liệu Java 17, `close()` làm loader **không còn sử dụng được để tải class hay resource mới do chính loader ấy định nghĩa/tìm từ đường URL của nó**, đồng thời đóng các tệp đã được nó mở cho các URL kiểu `jar:` và `file:`. Các class đã được nạp vẫn có thể truy cập; class/resource từ **parent** vẫn có thể được yêu cầu thông qua delegation.

Hai ý phải học cùng nhau:

- **Đóng nguồn tải ≠ xóa class đã nạp.** JVM không “quay ngược” việc định nghĩa class khi `close()` được gọi.
- **Đóng loader ≠ bảo đảm mọi tác vụ đang chạy đã kết thúc.** Nếu thread khác đang nạp class lúc `close()` xảy ra, đặc tả API nói **kết quả của lần nạp ấy không được xác định**. Không được dựa vào race đó để triển khai hot reload.

Trong bài này, “JAR handle” là các tệp/cấu trúc I/O được loader mở để đọc JAR; nó **không** phải mọi tham chiếu object/class và cũng không đồng nghĩa JVM giải phóng toàn bộ bộ nhớ gắn với plugin.

## 2. Trước và sau `close()`: bảng hành vi

Giả sử `child` là `URLClassLoader` có URL tới `plugin.jar`; parent không chứa các class/resource riêng của plugin.

| Thao tác | Khi loader còn mở | Sau `child.close()` |
|---|---|---|
| Nạp class chưa từng được child nạp từ `plugin.jar` | Có thể thành công nếu bytecode hợp lệ | Không thể trông chờ child tìm/định nghĩa class mới từ URL của nó; `loadClass` thường ném `ClassNotFoundException` |
| Dùng instance của class đã được định nghĩa | Hoạt động theo chương trình | Vẫn có thể hoạt động nếu không cần thêm class/resource bị thiếu |
| Gọi `loadClass` cho class đã được loader ghi nhận là đã nạp | Trả class đã biết | Có thể trả lại chính class đã nạp, theo đường kiểm tra `findLoadedClass` |
| Nạp class do parent định nghĩa, ví dụ `java.lang.String` | Delegation tới parent/bootstrap | Parent vẫn có thể tìm/đưa lại class đó |
| `findResource` / `findResources` cho resource chỉ trong plugin JAR | Tìm theo đường URL của child | Tương ứng không tìm thấy hoặc không có phần tử mới từ đường URL đó |
| `getResource` qua child | Hỏi parent trước, rồi nguồn child | Parent vẫn có thể trả resource của nó; nguồn child đã đóng không còn là nguồn tra cứu mới |
| Đọc `InputStream` do `URLClassLoader.getResourceAsStream` mở | Đọc được khi stream/loader còn hợp lệ | Loader đóng các stream mà phương thức này đã mở; không tiếp tục dùng chúng |
| Đổi tên/xóa JAR trên đĩa | Có thể bị file handle của loader hoặc thành phần khác cản trở | Loader cố gắng nhả handle của **nó**; kết quả thao tác file còn phụ thuộc OS và handle khác |
| Class unloading | Phụ thuộc vòng đời object/classloader | **Không tự động xảy ra chỉ vì close** |

Không nên biến bảng thành cam kết mọi hoạt động sau `close()` đều luôn thất bại: class đã nạp và class của parent vẫn có thể truy cập; việc một instance chạy tiếp còn phụ thuộc vào các dependency mà bytecode có thể cần nạp **lần đầu** ở thời điểm chạy sau đó.

## 3. Vòng đời cơ bản của JAR và nguồn class

`URLClassLoader` nhận mảng `URL[]`, ví dụ:

~~~java
URL[] urls = {Path.of("plugins/hello.jar").toUri().toURL()};
ClassLoader parent = Host.class.getClassLoader();
// Mã minh họa: cần class Host và các import phù hợp.
URLClassLoader loader = new URLClassLoader(urls, parent);
~~~

Nó thường mở JAR **khi cần** đọc class hoặc resource, không phải bắt buộc mở mọi JAR ngay lúc constructor chạy. Sau khi có JAR handle, một số runtime/cấu hình có thể giữ nguồn đó mở để phục vụ các lookup tiếp theo.

~~~text
Tạo URLClassLoader
       |
       v
Chưa cần mở tất cả JAR
       |
       v
loadClass / getResource
       |
       v
Mở JAR khi cần, đọc bytecode hoặc resource
       |
       v
Class đã định nghĩa; JAR handle có thể đang được giữ
       |
       v
close()
  ├─ Ngừng khả năng tìm class/resource mới từ URL riêng
  ├─ Cố đóng các file/JAR handle đã mở bởi loader
  └─ Giữ nguyên class đã định nghĩa và parent loader

Class unloading: quá trình khác, không phải thao tác close trực tiếp.
~~~

Hệ quả thực tế: nếu một ứng dụng cập nhật JAR plugin, nên **ngừng tác vụ đang dùng plugin**, đóng loader cũ và các resource còn sử dụng, rồi tạo **loader mới** cho phiên bản mới. Chỉ thay bytecode của JAR trên đĩa không đổi nội dung của `Class<?>` đã được JVM định nghĩa bởi loader cũ.

## 4. Ví dụ Java 17+ hoàn chỉnh: class đã nạp, class mới và resource sau close

Thí nghiệm sẽ tạo một JAR với:

- `demo.plugin.Early`: class được nạp **trước** khi đóng.
- `demo.plugin.Late`: class **chưa nạp** lúc đóng.
- `plugin/data/label.txt`: resource chỉ thuộc JAR của child.

Host nạp bằng `URLClassLoader` qua reflection để **không** vô tình đặt class plugin lên application classpath.

### 4.1. Cấu trúc dự án

~~~text
close-demo/
├── host-src/
│   └── CloseDemo.java
├── plugin-src/
│   └── demo/plugin/
│       ├── Early.java
│       └── Late.java
└── plugin-resources/
    └── plugin/data/
        └── label.txt
~~~

File `plugin-resources/plugin/data/label.txt` chứa đúng dòng:

~~~text
FROM_JAR
~~~

Khi biên dịch, output được chia thành `build/host` và `build/plugin`, sau đó đóng một JAR tại `dist/plugin.jar`. Chỉ `build/host` nằm trong `-cp` lúc chạy chương trình.

### 4.2. Class plugin đã nạp sớm

File **`plugin-src/demo/plugin/Early.java`**:

~~~java
package demo.plugin;

public final class Early {
    public Early() {
    }

    public String message() {
        return "EARLY_OK";
    }
}
~~~

Method `message()` chỉ trả một string literal, không cần resolve thêm class implementation khác thuộc plugin. Điều này quan trọng: khi gọi method sau `close()`, ta chủ động tránh trường hợp method tự yêu cầu class mới từ JAR đã đóng.

### 4.3. Class chỉ nạp nếu được yêu cầu muộn

File **`plugin-src/demo/plugin/Late.java`**:

~~~java
package demo.plugin;

public final class Late {
    public Late() {
    }

    public String message() {
        return "LATE_OK";
    }
}
~~~

Host **không gọi `loadClass("demo.plugin.Late")` trước khi đóng loader**. Khi loader đã đóng, lookup mới từ JAR của child sẽ không thành công.

### 4.4. Host điều khiển thời điểm close

File **`host-src/CloseDemo.java`**:

~~~java
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CloseDemo {
    private CloseDemo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: CloseDemo <path-to-plugin.jar>");
        }

        Path jar = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalArgumentException("Plugin JAR not found");
        }

        URL[] urls = {jar.toUri().toURL()};
        ClassLoader parent = CloseDemo.class.getClassLoader();

        try (URLClassLoader loader = new URLClassLoader(urls, parent)) {
            Class<?> earlyClass = loader.loadClass("demo.plugin.Early");
            Object earlyObject =
                    earlyClass.getConstructor().newInstance();

            System.out.println("Before close: "
                    + earlyClass.getMethod("message").invoke(earlyObject));

            try (InputStream in =
                         loader.getResourceAsStream("plugin/data/label.txt")) {
                if (in == null) {
                    throw new IllegalStateException("Missing resource");
                }
                System.out.println("Resource before close: "
                        + new String(
                                in.readAllBytes(),
                                StandardCharsets.UTF_8).strip());
            }

            // Đóng nguồn tải trước khi thử các lookup tiếp theo.
            loader.close();

            System.out.println("Existing object after close: "
                    + earlyClass.getMethod("message").invoke(earlyObject));

            System.out.println("Already loaded Class identity: "
                    + (loader.loadClass("demo.plugin.Early") == earlyClass));

            boolean lateUnavailable;
            try {
                loader.loadClass("demo.plugin.Late");
                lateUnavailable = false;
            } catch (ClassNotFoundException expected) {
                lateUnavailable = true;
            }
            System.out.println("Late class unavailable: "
                    + lateUnavailable);

            System.out.println("Own resource unavailable: "
                    + (loader.getResource("plugin/data/label.txt") == null));

            System.out.println("Parent String available: "
                    + (loader.loadClass("java.lang.String") == String.class));

            // close() gọi lần thứ hai không gây tác dụng mới.
            loader.close();
            System.out.println("Second close completed");
        }
    }
}
~~~

**Tại sao vừa `try-with-resources` vừa gọi `close()` tường minh?** Vì thí nghiệm phải quan sát các lookup *ngay sau thời điểm đóng*. `try-with-resources` vẫn đảm bảo cleanup nếu có exception trước khi đến lời gọi `close()`. Sau khi rời khối, Java gọi `close()` lần nữa; API quy định đóng loader đã đóng là thao tác không có hiệu lực.

**Tại sao không giữ `InputStream` rồi thử đọc sau close?** `URLClassLoader.getResourceAsStream` có đặc điểm tài liệu hóa: những stream mà loader đã mở qua phương thức này sẽ được đóng khi loader đóng. Đọc sau close là không an toàn và thông báo lỗi cụ thể phụ thuộc stream/URL implementation. Vì vậy ví dụ đọc và đóng stream **trước** loader.close().

### 4.5. Biên dịch, đóng gói và chạy

Dùng **JDK 17 trở lên**, mở terminal tại thư mục `close-demo/` có đầy đủ file.

**Bash (Linux, macOS, Git Bash):**

~~~bash
mkdir -p build/host build/plugin dist

javac -encoding UTF-8 \
  -d build/plugin \
  plugin-src/demo/plugin/Early.java \
  plugin-src/demo/plugin/Late.java

javac -encoding UTF-8 \
  -d build/host host-src/CloseDemo.java

jar --create --file dist/plugin.jar \
  -C build/plugin . \
  -C plugin-resources .

java -cp build/host CloseDemo dist/plugin.jar
~~~

**Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force build/host,build/plugin,dist | Out-Null

javac -encoding UTF-8 -d build/plugin plugin-src/demo/plugin/Early.java plugin-src/demo/plugin/Late.java

javac -encoding UTF-8 -d build/host host-src/CloseDemo.java

jar --create --file dist/plugin.jar -C build/plugin . -C plugin-resources .

java -cp build/host CloseDemo dist/plugin.jar
~~~

Đảm bảo JAR có ba entry:

~~~text
demo/plugin/Early.class
demo/plugin/Late.class
plugin/data/label.txt
~~~

Có thể kiểm tra bằng `jar --list --file dist/plugin.jar`; JAR còn có thể chứa manifest và entry thư mục.

**Stdout kỳ vọng trên Java 17+ với URLClassLoader chuẩn:**

~~~text
Before close: EARLY_OK
Resource before close: FROM_JAR
Existing object after close: EARLY_OK
Already loaded Class identity: true
Late class unavailable: true
Own resource unavailable: true
Parent String available: true
Second close completed
~~~

Phần sau `close()` minh họa ba ranh giới: **class đã nạp vẫn dùng được**, **URL của child không còn là nguồn tải class/resource mới**, **parent vẫn có thể cung cấp class**.

Đừng sửa cách chạy thành `java -cp "build/host:dist/plugin.jar" ...`. Khi plugin JAR đã nằm trong classpath của parent, `parent-first delegation` có thể nạp `Late` qua parent, làm kết quả demo không còn đại diện cho loader con đã đóng.

## 5. Điều gì vẫn hoạt động sau khi close?

### 5.1. Class đã nạp không bị “hủy”

`Class<?>`, reflection metadata và instance đã tạo trước `close()` vẫn có thể được dùng nếu không cần tìm thêm class/resource từ URL đã đóng. Thực nghiệm `Early.message()` chỉ trả literal để minh họa điều đó. Một method khác có thể thất bại khi lần đầu cần lazy dependency sau close; vì thế không tiếp tục phục vụ plugin qua loader đã đóng.

### 5.2. Parent vẫn là nguồn hợp lệ

`loader.loadClass("java.lang.String")` vẫn có thể thành công nhờ bootstrap/parent. Resource do parent sở hữu cũng có thể tìm thấy; không suy rằng mọi `loadClass` hay `getResource` sau close đều thất bại.

### 5.3. Không nhầm với JVM class unloading

Đóng handle I/O không loại bỏ `Class<?>` hoặc ép GC. Host cache, listener, TCCL hay task còn sống có thể giữ defining loader reachable; unloading do JVM quyết định khi đủ điều kiện.

## 6. JAR handle: tại sao Windows và Linux có thể khác nhau?

Khi `URLClassLoader` mở một JAR, có thể tồn tại handle/descriptor tới tệp đó. Trên một số hệ điều hành, đặc biệt Windows, handle đang mở có thể khiến thao tác **đổi tên, ghi đè hoặc xóa** tệp JAR bị từ chối. Trên nhiều filesystem kiểu Unix, có thể unlink/rename trong khi process vẫn giữ handle, nhưng nội dung bytecode/nguồn đọc còn dùng bởi tiến trình không tự đồng nhất với file mới trên đĩa.

Khi gọi `close()`:

1. Loader cố đóng những file mà **nó đã mở** qua đường `jar:`/`file:`.
2. Tài nguyên OS gắn với những file ấy có thể được nhả.
3. Ứng dụng có thể thử thay JAR an toàn hơn, **sau khi đã ngừng tác vụ sử dụng loader**.
4. Không có bảo đảm file sẽ lập tức đổi tên/xóa được: loader khác, `JarFile` khác, `InputStream` khác, tiến trình khác hoặc antivirus/file scanner vẫn có thể giữ handle.

**Không kiểm thử close bằng cách mặc định chạy `Files.delete(pluginJar)` và đòi kết quả giống hệt trên mọi OS.** Hãy quan sát file handles bằng công cụ hệ điều hành khi cần, nhưng lấy Javadoc làm contract về việc loader đóng các file do nó mở.

Một URL cũ từ `loader.getResource(name)` cũng không phải “con trỏ an toàn có thể đọc mãi” sau close. Nếu caller dùng `url.openStream()`, nó có thể mở **một kết nối mới** theo URL handler; điều này là hành vi riêng của URL/resource, không chứng minh loader chưa đóng. Vì vậy không dùng khả năng đọc lại URL cũ làm kiểm tra tính đóng của `URLClassLoader`.

## 7. Nếu thread khác đang tải class khi close thì sao?

Đây là giới hạn quan trọng nhất của bài học. Javadoc Java 17 quy định nếu **một thread đang thực hiện việc tải class** khi thread khác gọi `close()` trên cùng `URLClassLoader`, kết quả của lần tải đó là **không xác định**.

~~~text
Thread A                                Thread B
────────                                ────────
loader.loadClass("plugin.NewType")
   đang đọc/liên kết class
        │                                    │
        └──────────── giao nhau ─────────> loader.close()
                                             │
                                      đóng nguồn JAR
        │                                    │
   kết quả load không được API bảo đảm
~~~

Không nên dự đoán cứng một loại kết quả:

- Không thể khẳng định luôn ném `ClassNotFoundException`.
- Không thể khẳng định luôn hoàn tất thành công.
- Không thể khẳng định cứ đồng bộ hóa `close()` và `loadClass()` bằng một `synchronized` tùy ý ở caller là đủ cho mọi code/library trong hệ thống: còn có thread và API khác dùng loader.
- Không nên tạo race thử nghiệm rồi coi một lần chạy “thành công” là bằng chứng lifecycle đúng.

**Mẫu thiết kế an toàn:** Không gọi `close()` khi hệ thống vẫn có thể phát sinh thao tác nạp class/resource mới từ loader ấy. Muốn đóng loader, trước hết phải **chặn yêu cầu mới và đợi mọi tác vụ phụ thuộc loader hoàn thành**.

## 8. Trình tự ngừng plugin đúng cách

Một host có thể tổ chức vòng đời như sau:

~~~text
RUNNING
   |
   | 1. Chặn request mới gửi tới plugin
   v
DRAINING
   |
   | 2. Đợi các request/task đang dùng plugin kết thúc
   | 3. Gỡ listener/callback/scheduler, ngừng thread riêng
   | 4. Đóng stream, socket, JarFile và resource do plugin mở
   v
QUIESCED
   |
   | 5. Khôi phục TCCL và gỡ mọi tham chiếu host giữ tới loader cũ
   | 6. Gọi oldLoader.close() để nhả các file/JAR handle của loader
   v
CLOSED
   |
   | 7. Thay JAR và tạo loader MỚI để nạp plugin phiên bản mới
   v
RUNNING với plugin mới
~~~

Thứ tự `5` và `6` còn tùy thiết kế: lúc đóng loader, host phải còn một tham chiếu tới `oldLoader` để gọi `close()`; sau khi đóng mới nên loại bỏ tham chiếu cuối cùng. Điều bất biến là **không để loader đóng khi vẫn có task có thể cần nạp thêm class/resource qua nó**.

### Một ví dụ đồng bộ hóa ở cấp ứng dụng

Một mô hình đơn giản trong đó host điều khiển toàn bộ task:

~~~java
// Mã minh họa, không phải file chạy độc lập.
// Các hàm/phạm vi này là contract do ứng dụng tự cung cấp.
stopAcceptingNewTasks();
waitForInFlightTasksToFinish();
unregisterPluginCallbacks();
closePluginOwnedResources();
restoreOrClearThreadContextLoaders();
oldLoader.close();
oldLoader = null;
~~~

Nếu dùng `ExecutorService`, `shutdown()` chỉ có nghĩa **không nhận task mới**; bản thân lệnh đó không đợi task hoàn thành. Cần kết hợp `awaitTermination(...)`, `Future.get()` hoặc cơ chế đếm in-flight phù hợp. Nếu chờ quá hạn, **không được mặc nhiên đóng loader** khi task vẫn có khả năng sử dụng nó; chuyển sang quy trình xử lý timeout/cancel và xác nhận quiescence.

Ngoài method đang thực thi, một task có thể sử dụng:

- `Class.forName(name, true, pluginLoader)`, reflection hoặc dynamic proxies cần class mới.
- `ServiceLoader` có cơ chế discovery/lazy provider initialization.
- `pluginLoader.getResources(...)` hoặc `getResourceAsStream(...)`.
- Dynamic bytecode generation, class annotation scanning, framework dependency injection.
- Thread context class loader (`TCCL`) trỏ tới plugin loader.

Vì vậy việc “không thấy method plugin nào đang chạy” chưa đủ nếu vẫn còn task hoặc lazy-loading từ code khác.

## 9. `try-with-resources`, idempotence và lỗi close

`URLClassLoader` triển khai `AutoCloseable`, do đó có thể viết:

~~~java
try (URLClassLoader loader =
             new URLClassLoader(urls, parent)) {
    // Nạp và sử dụng plugin trong phạm vi này.
}
// loader.close() được gọi tự động.
~~~

Đây là **đoạn mã minh họa** dùng biến `urls` và `parent` được chuẩn bị trước. Trong môi trường thực tế, chỉ dùng `try-with-resources` nếu **mọi tác vụ cần loader kết thúc trước khi rời block**. Nếu plugin phải tồn tại qua nhiều request, giữ loader theo vòng đời plugin và đóng trong phase shutdown đã điều phối.

Một vài tính chất theo tài liệu:

- Gọi `close()` nhiều lần: nếu loader đã đóng, lời gọi sau không có tác dụng.
- `close()` có thể ném `IOException`. JVM cố đóng tất cả tệp đã mở, bắt các `IOException` trong quá trình đó; nếu có nhiều `IOException`, các lỗi sau được thêm vào danh sách **suppressed exceptions** của lỗi đầu.
- `close()` không bắt mọi `RuntimeException` hoặc `Error`.
- Với runtime còn sử dụng `SecurityManager` và policy tương ứng, có thể phát sinh `SecurityException` nếu thiếu quyền đóng classloader.
- Trong `try-with-resources`, nếu thân `try` và thao tác close cùng ném lỗi, lỗi close có thể được **suppressed** vào lỗi chính theo ngữ nghĩa `try-with-resources`.

**Đừng bỏ qua IOException của close.** Nó báo có ít nhất một việc đóng file không thành công; cần log, theo dõi handle còn lại và quyết định có thể cập nhật JAR an toàn hay chưa.

Mẫu xử lý lỗi minh họa:

~~~java
// Mã minh họa; không phải chương trình độc lập.
try {
    quiescePlugin();
} finally {
    try {
        loader.close();
    } catch (IOException ex) {
        // Log đầy đủ ex và ex.getSuppressed().
        reportCloseFailure(ex);
    }
}
~~~

Mẫu này chỉ có ý nghĩa nếu `quiescePlugin()` thực sự đạt trạng thái không còn công việc sử dụng loader. Không dùng `finally` như lý do để đóng loader khi task vẫn chạy.

## 10. Đóng stream và URL resource đúng thời điểm

Có ít nhất ba tầng tài nguyên cần phân biệt:

| Tầng | Ví dụ | Ai chịu trách nhiệm đóng? |
|---|---|---|
| ClassLoader | `URLClassLoader` | Host/plugin lifecycle manager gọi `close()` |
| Resource stream | `InputStream` từ `getResourceAsStream` | Caller đóng càng sớm càng tốt; URLClassLoader cũng đóng các stream do chính method này mở khi loader.close() |
| Tài nguyên riêng của plugin | `JarFile` tự mở, socket, thread, executor, JDBC pool | Code sở hữu tạo/chịu trách nhiệm đóng hoặc shutdown |

**Không coi `loader.close()` là thay thế cho mọi `try-with-resources` trong ứng dụng.** Ví dụ, nếu code plugin tự tạo `new JarFile(...)` độc lập, loader không có nghĩa vụ biết và đóng nó.

Cũng không nên giữ stream mở rồi đóng loader ngay lập tức:

~~~java
// Mã minh họa chống mẫu nguy hiểm.
InputStream stream = loader.getResourceAsStream("plugin/config.txt");
loader.close();
// Không đọc stream tại đây: loader có thể đã đóng stream đó.
~~~

Cách đúng là hoàn thành việc đọc trước khi đóng loader:

~~~java
// Mã minh họa; loader còn mở trong khối.
try (InputStream in = loader.getResourceAsStream("plugin/config.txt")) {
    if (in != null) {
        byte[] bytes = in.readAllBytes();
        // Xử lý bytes.
    }
}
// Sau khi mọi thao tác dùng loader kết thúc, mới close loader.
~~~

Vì resource đọc từ JAR có thể sử dụng cache/file handle riêng của URL handler, không nên suy đoán chính xác thời điểm hệ điều hành nhả mọi file descriptor chỉ từ việc đối tượng `URL` còn tồn tại.

## 11. Khác biệt giữa reload class và reload plugin

Thay `dist/plugin.jar` khi loader cũ đã định nghĩa `demo.plugin.Early` không thay bytecode hoặc runtime `Class<?>` của định nghĩa ấy. Loader **mới** có thể định nghĩa cùng tên thành runtime type mới. Quy trình chuyển plugin cần dừng và drain v1, đóng tài nguyên, triển khai JAR đã kiểm chứng, khởi tạo loader v2 và chỉ chuyển request sang v2 khi đã sẵn sàng.

Trong giai đoạn hai thế hệ cùng hoạt động, quản lý riêng task/callback/loader. Đừng đóng loader v1 trong lúc task còn có thể cần lazy loading.

## 12. Liên hệ với parent delegation và class identity

`URLClassLoader` dùng parent-first mặc định. `loadClass("java.lang.String")` qua loader đã đóng vẫn có thể trả class bootstrap; ngược lại `loadClass("demo.plugin.Late")`, nếu chỉ có trong JAR child và chưa load, không thể định nghĩa mới từ URL đã đóng. Trong test, tránh đặt JAR plugin lên parent application classpath vì parent có thể che mất tình huống cần đo.

Hai loader tự định nghĩa cùng binary name tạo hai runtime types khác nhau. Chia sẻ interface/DTO từ parent và quan sát defining loader qua `Class.getClassLoader()` khi giao tiếp giữa các thế hệ.

## 13. Chẩn đoán sự cố close/JAR handle

| Dấu hiệu | Giải thích có thể | Cách kiểm tra và xử lý |
|---|---|---|
| Windows báo JAR đang được sử dụng | Một loader, `JarFile`, stream hoặc tiến trình vẫn giữ file handle | Quiesce plugin, đóng mọi nguồn; kiểm tra handle qua công cụ OS |
| `loadClass` cho plugin class mới thất bại sau close | Loader không còn tìm từ URL riêng | Tạo loader mới khi cần nạp phiên bản mới |
| Object plugin cũ vẫn chạy sau close | Class đã nạp còn reachable | Hành vi hợp lệ nếu không cần thêm dependency/resource |
| Method cũ lỗi khi lần đầu dùng dependency mới | Dependency chưa được child nạp trước close | Không đóng trước khi plugin thật sự kết thúc; đừng giả định toàn bộ dependency eager-loaded |
| `ClassNotFoundException` khi parent không có class | Sau close không thể tìm class mới từ child URL | Kiểm tra nguồn class và trạng thái loader |
| Một lần race close/load vẫn thành công | Kết quả khi đồng thời tải class và close là không xác định | Không lấy làm contract; điều phối dừng và đợi task |
| `getResourceAsStream` đọc lỗi sau close | Stream do loader mở đã bị đóng | Đọc và đóng stream trước `loader.close()` |
| `close()` ném `IOException` | Có file không đóng được | Log nguyên exception và suppressed exceptions; xem tình trạng handle |
| Plugin cũ không unload dù đã close | Host/thread/cache còn tham chiếu | Dọn callback, TCCL, ThreadLocal, executor, object/plugin reference |
| Có thể mở lại URL cũ | URL handler có thể tạo kết nối mới độc lập | Không coi đọc URL cũ là bằng chứng URLClassLoader còn mở |
| Dùng `URLClassLoader` trong try-with-resources rồi trả task bất đồng bộ | Scope kết thúc trước khi async task dùng loader xong | Chuyển ownership loader vào manager và đóng sau khi tasks kết thúc |

### Gợi ý quan sát môi trường

Trên Java 17, các lệnh có thể hỗ trợ phân tích class loading (các giá trị cụ thể phụ thuộc JVM và cấu hình):

~~~bash
java -Xlog:class+load=info -cp build/host CloseDemo dist/plugin.jar
~~~

Output logging JVM chứa nhiều dòng và không ổn định về thứ tự/đường dẫn, vì vậy **không** xem nó như stdout chuẩn của ví dụ. Với sự cố file locks, các công cụ hệ điều hành như Process Explorer (Windows) hoặc `lsof` (Unix-like) có thể giúp tìm tiến trình/file descriptor đang giữ JAR. Không tùy tiện xóa JAR đang được tiến trình sử dụng để “ép unload”.

## 14. Các tình huống cần tránh trong thiết kế

**Tránh 1: đóng ngay khi nhấn “disable plugin” nhưng task vẫn chạy.** Disable nên chặn công việc mới rồi đợi drain; không đồng nghĩa mọi thread đã xong.

**Tránh 2: đóng loader trong `finally` của mỗi request.** Nếu loader được chia sẻ qua nhiều request, request A có thể đóng tài nguyên khi request B đang tải class. Owner của loader phải là **plugin lifecycle manager**, không phải một request bất kỳ.

**Tránh 3: cho rằng `URLClassLoader.close()` là cơ chế reload.** Nó chỉ đóng nguồn/giải phóng handle, không tải bytecode mới vào runtime class cũ.

**Tránh 4: coi `close()` đảm bảo mọi JAR có thể xóa trên mọi hệ điều hành.** Có thể còn descriptor/handle từ thành phần khác.

**Tránh 5: tìm cách bắt `ClassNotFoundException` để “tự động thử lại trên loader đã đóng”.** Loader cũ không được thiết kế để tái mở bằng `loadClass` sau close; tạo loader mới với đường URL rõ ràng.

**Tránh 6: trộn API dùng chung vào JAR reload và mong object cũ cast được sang class mới.** Các class cùng tên nhưng do loader khác nhau định nghĩa có identity khác; API chung nên ở parent để host và plugin cùng nhận diện.

## 15. Tự kiểm tra

**Câu 1.** Đã load `Early`, tạo instance rồi gọi `loader.close()`. Instance cũ có lập tức vô hiệu không?

**Đáp án:** Không. Class/object đã tạo vẫn có thể được dùng. Nhưng nếu một thao tác sau close mới yêu cầu dependency thuộc child JAR chưa được nạp, nó có thể lỗi.

**Câu 2.** `URLClassLoader.close()` có buộc JVM dỡ class và GC ngay không?

**Đáp án:** Không. Nó đóng nguồn tải/JAR handle của loader; class unloading và GC là cơ chế khác.

**Câu 3.** Vì sao `loader.loadClass("java.lang.String")` có thể hoạt động sau close?

**Đáp án:** Parent/bootstrap loader vẫn có thể cung cấp class. Close của child không đóng parent.

**Câu 4.** Điều gì được API đảm bảo nếu một thread đang nạp class trong khi thread khác gọi `close()`?

**Đáp án:** Kết quả lần nạp class đồng thời đó **không xác định**. Không có cam kết luôn thành công hoặc luôn thất bại theo một cách cố định.

**Câu 5.** Nếu `InputStream` mở qua `loader.getResourceAsStream(...)` vẫn còn, có nên tiếp tục đọc nó sau `close()` không?

**Đáp án:** Không. API `URLClassLoader` đóng những stream được mở qua phương thức đó khi loader đóng.

**Câu 6.** Tại sao đã close vẫn không xóa được JAR trên Windows?

**Đáp án:** Có thể còn handle do loader khác, resource stream, `JarFile`, process khác hoặc công cụ ngoài JVM giữ. Close chỉ xử lý các file do loader này mở.

**Câu 7.** Khi ứng dụng hot reload plugin, khi nào được gọi `oldLoader.close()`?

**Đáp án:** Sau khi ngừng nhận việc mới, bảo đảm các tác vụ đang dùng old loader đã kết thúc, và các resource/callback/thread liên quan đã được dọn phù hợp.

## 16. Tài liệu chính thức

- [Java SE 17 — URLClassLoader.close](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html#close()): đóng loader, JAR/file handle, class đã nạp, parent delegation và undefined result khi close đồng thời với class loading.
- [Java SE 17 — URLClassLoader.getResourceAsStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html#getResourceAsStream(java.lang.String)): resource stream do loader mở được đóng khi loader đóng.
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `getResource`, `getParent`.
- [Java SE 17 — AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html): cleanup với try-with-resources.
- [Java Virtual Machine Specification 17, Chương 5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): vòng đời nạp, liên kết và định danh kiểu runtime.

**Ghi nhớ:** `URLClassLoader.close()` là thao tác **chấm dứt nguồn tải mới và cố nhả file/JAR handle của loader**, không phải lệnh xóa class đã nạp. Muốn đóng an toàn, **quiesce mọi tác vụ và hoạt động nạp**, đọc/đóng stream đúng lúc, rồi close; tuyệt đối không đặt cược vào kết quả của việc `close()` diễn ra đồng thời với class loading.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="url-class-loader-009">Giới hạn tìm nested JAR và khác biệt với launcher chuyên dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn tìm nested JAR của URLClassLoader và khác biệt với launcher chuyên dụng

## 1. Vấn đề: JAR “có chứa thư viện” nhưng không nạp được class

Một file Java Archive (JAR) thực chất là một ZIP archive theo định dạng JAR. JAR có thể chứa file, thư mục, class và **một file JAR khác như một entry dữ liệu**. Ví dụ:

~~~text
outer.jar
├── META-INF/
│   └── MANIFEST.MF
├── app/Main.class
└── lib/
    └── helper.jar
        └── (bên trong helper.jar)
            demo/lib/Helper.class
~~~

Nhìn cấu trúc, có thể nghĩ rằng:

~~~java
new URLClassLoader(new URL[] { outerJarUrl })
        .loadClass("demo.lib.Helper");
~~~

sẽ tự mở `outer.jar`, phát hiện `lib/helper.jar` rồi đi tiếp vào archive con. **Đây là suy luận sai đối với `URLClassLoader` tiêu chuẩn của JDK.** Nó tìm lớp từ **các URL trong search path và các quy tắc tra cứu JAR tiêu chuẩn**, không tự duyệt mọi entry `*.jar` bên trong JAR đang mở như những classpath roots mới.

**Chốt trọng tâm:**

- File `outer.jar` có thể tồn tại và chứa `helper.jar`.
- `URLClassLoader` thông thường **có thể nhìn thấy entry `lib/helper.jar` như một resource** trong outer JAR.
- Nhưng `demo.lib.Helper` nằm *bên trong byte stream của entry `helper.jar`*, không nằm tại đường `demo/lib/Helper.class` của **outer JAR**.
- Muốn load trực tiếp class từ nested archive **mà không trích ra**, cần một cơ chế có hiểu biết về nested archive, chẳng hạn **launcher/ClassLoader chuyên dụng** của một framework. Một giải pháp khác là **trích JAR con thành file JAR thật rồi đặt file đó vào runtime classpath/URLClassLoader**.
- Không phải mọi file được gọi là “executable/fat JAR” đều cùng cơ chế. **Shaded/uber JAR** có thể đưa class vào thẳng cùng một archive, còn **nested JAR** giữ library dưới dạng JAR riêng nằm bên trong.

Bài này giải thích giới hạn của **`java.net.URLClassLoader`**, phân biệt với Spring Boot executable JAR và thực nghiệm Java 17+ có stdout kiểm chứng.

## 2. Trước hết phân biệt ba dạng đóng gói

### 2.1. Plain JAR + dependency nằm ngoài

~~~text
dist/
├── app.jar
└── lib/
    ├── a.jar
    └── b.jar
~~~

`app.jar` là JAR của ứng dụng, `a.jar` và `b.jar` là những **file thực tế** trong hệ thống tệp. Launcher có thể:

- Dùng `java -cp "app.jar:lib/a.jar:lib/b.jar" app.Main` trên Linux/macOS.
- Dùng `java -cp "app.jar;lib/a.jar;lib/b.jar" app.Main` trên Windows.
- Dùng manifest `Class-Path` thích hợp hoặc script launcher do công cụ build tạo, tùy deployment.

Mỗi JAR được liệt kê là một **classpath entry** độc lập; `URLClassLoader` tiêu chuẩn có thể dùng URL trỏ đến các file JAR như vậy.

### 2.2. Shaded/uber JAR: gộp class vào một archive

~~~text
app-all.jar
├── META-INF/MANIFEST.MF
├── app/Main.class
├── demo/lib/Helper.class
└── another/lib/Codec.class
~~~

Không có một `helper.jar` cần mở lồng để tìm `Helper.class`: class của dependency đã được đưa **trực tiếp** vào JAR ngoài theo đường package của nó. Khi không có xung đột/relocation đặc biệt, class loader chuẩn có thể tìm `demo/lib/Helper.class` như entry của một JAR bình thường.

Các công cụ như Maven Shade Plugin, Gradle Shadow và những build task tương tự có thể tạo archive dạng này theo cấu hình. Đừng nhầm chữ “fat JAR” với “nested JAR”: cách đóng gói thực sự mới quyết định cơ chế nạp.

**Rủi ro riêng của shaded JAR:** trùng đường class/resource, chữ ký JAR, `META-INF/services`, relocation package, license/metadata và multi-release entries có thể cần xử lý chính xác khi merge.

### 2.3. Nested JAR: giữ JAR con nguyên vẹn trong JAR ngoài

~~~text
app-nested.jar
├── META-INF/MANIFEST.MF
├── app/Main.class
└── lib/helper.jar       <-- file JAR con là một entry trong archive ngoài
    └── demo/lib/Helper.class  (nằm trong archive con)
~~~

`jar --list --file app-nested.jar` liệt kê `lib/helper.jar`, nhưng **không tự liệt kê** `demo/lib/Helper.class` như một entry trực tiếp của archive ngoài.

Nếu thêm `app-nested.jar` vào `URLClassLoader`, loader có thể tìm `app/Main.class` (nếu class nằm trực tiếp ở root), nhưng **không tự biến `lib/helper.jar` thành một classpath entry mới**. Đây chính là giới hạn bài học phân tích.

## 3. `URLClassLoader` thực sự tìm từ đâu?

Theo [Java SE 17 `URLClassLoader` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html), loader này dùng các URL tham chiếu đến **JAR files và directories**:

- URL `file:` kết thúc bằng `/` đại diện một directory classpath root.
- URL `file:` trỏ tới JAR file đại diện một JAR có thể được mở để tìm class/resource.
- URL dạng `jar:...!/` được xử lý theo quy tắc của JAR URL/`JarURLConnection`.
- Các URL được tra theo cơ chế delegation và thứ tự search path tương ứng; `parent` có thể trả class trước `findClass` của loader con.
- `getURLs()` trả các URL trong search path của chính `URLClassLoader`; danh sách này **không tự hiển thị mọi JAR bị nhúng như entry trong archive ngoài**.

### 3.1. Cách class name ánh xạ thành resource name

Binary name:

~~~text
demo.lib.Helper
~~~

thường tương ứng với class resource:

~~~text
demo/lib/Helper.class
~~~

Để load từ một JAR URL thông thường, entry này phải được tìm theo logic JAR classpath root. Nhưng khi archive ngoài có:

~~~text
BOOT-INF/lib/helper.jar
~~~

thì loader chỉ có **entry chứa byte của một JAR con**; đường `demo/lib/Helper.class` không phải entry trực tiếp trong archive ngoài.

Vì vậy cần tách hai câu hỏi:

| Câu hỏi | Trả lời khi chỉ thêm `outer.jar` vào URLClassLoader |
|---|---|
| Có thể tìm resource `BOOT-INF/lib/helper.jar` không? | **Có thể**, vì đây là entry thật của outer JAR |
| Có thể tự load `demo.lib.Helper` từ bên trong resource đó không? | **Không theo hành vi tìm nested JAR thông thường của JDK URLClassLoader** |
| Có thể load `app.Main` nếu `app/Main.class` nằm trực tiếp trong outer JAR? | Có, nếu đúng loader/đường classpath và không gặp ràng buộc khác |
| Có thể load `demo.lib.Helper` từ một **file `helper.jar` độc lập** được thêm vào URLClassLoader không? | Có, với JAR hợp lệ và không có vấn đề class/dependency khác |

Tìm được resource `helper.jar` không đồng nghĩa JVM đã biết cách tìm từng `.class` bên trong file resource đó.

### 3.2. Đừng nhầm URL của một entry với URL classpath root

Java `jar:` URL tiêu chuẩn có cú pháp đại ý:

~~~text
jar:<url-của-JAR>!/<entry>
~~~

Ví dụ một class resource trong JAR **không lồng nhau**:

~~~text
jar:file:/some/dir/helper.jar!/demo/lib/Helper.class
~~~

Nhưng chuỗi ghép kiểu:

~~~text
jar:file:/some/dir/outer.jar!/BOOT-INF/lib/helper.jar!/demo/lib/Helper.class
~~~

**không tự trở thành cách chuẩn để `JarURLConnection` mở “JAR trong JAR”**. Giao thức JAR URL/JDK thông thường không bảo đảm xử lý nhiều tầng `!/` như một classpath root độc lập. Không nên xây URL thủ công rồi giả định `URLClassLoader` tự hiểu nested archive.

Một framework có thể cài **protocol handler/JAR abstraction chuyên dụng**, tạo ra URL nhìn có vẻ tương tự nhưng ngữ nghĩa do framework cung cấp. Khi đó kết quả không còn là hành vi mặc định thuần JDK.

### 3.3. Resource là dữ liệu, không phải một JAR được mount tự động

Chương trình vẫn có thể lấy byte của entry nested:

~~~java
try (java.util.jar.JarFile outer =
        new java.util.jar.JarFile("build/outer.jar")) {
    java.util.jar.JarEntry nested =
            outer.getJarEntry("BOOT-INF/lib/helper.jar");
    try (java.io.InputStream in = outer.getInputStream(nested)) {
        // Có thể đọc byte helper.jar như dữ liệu thông thường.
    }
}
~~~

Đoạn trên là **snippet minh họa**, không thể biên dịch độc lập nếu không đặt trong phương thức thích hợp và xử lý import/exception. Nó chứng minh việc **đọc entry của outer JAR** hoàn toàn có thể, nhưng không có bước JVM tự đọc tiếp định dạng class/JAR bên trong. Muốn dùng class từ byte đó cần chiến lược thích hợp: trích archive và đưa đường dẫn vào loader, tạo custom loader, hoặc dùng launcher chuyên dụng.

## 4. Vì sao `java -jar outer.jar` không tự giải quyết nested JAR?

Lệnh:

~~~sh
java -jar outer.jar
~~~

yêu cầu Java launcher chạy main class được khai báo bởi **manifest `Main-Class`** trong JAR ngoài. Theo tài liệu [Java launcher](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html), khi dùng `-jar`, classpath tùy chọn của người dùng không được kết hợp như lệnh `java -cp ... Main`.

Điều đặc biệt quan trọng: `java -jar` **không có nghĩa** “Java tự tìm mọi JAR con nằm bên trong JAR ngoài”.

Ví dụ JAR có:

~~~text
META-INF/MANIFEST.MF:
  Main-Class: app.Main

outer.jar:
  app/Main.class
  BOOT-INF/lib/helper.jar
~~~

Ứng dụng `app.Main` có thể khởi chạy nếu class của nó nằm ở vị trí mà launcher tiêu chuẩn tìm thấy. Nhưng class trong `BOOT-INF/lib/helper.jar` vẫn **không tự hiện diện** trên classpath chỉ vì có entry `helper.jar`.

Đặc biệt, `Class-Path` của manifest tiêu chuẩn **không phải cú pháp để nạp các JAR con như một entry bên trong JAR ngoài**. Nó mô tả các đường dẫn classpath liên quan theo hợp đồng manifest, thường dùng cho external dependencies. Để chạy nested libraries bên trong archive duy nhất cần cơ chế đọc phù hợp.

### Phân biệt “chạy được” và “nạp mọi dependency”

Có thể `java -jar app.jar` khởi động `main()` thành công, rồi gặp:

~~~text
java.lang.NoClassDefFoundError: demo/lib/Helper
~~~

khi thực sự cần đến class trong JAR con. Đây thường là dấu hiệu layout chứa nested library nhưng quá trình launch **không cài loader hiểu layout đó**. Tuy nhiên, không phải mọi `NoClassDefFoundError` đều do nested-JAR: lỗi initialization, version và linkage khác cũng có thể dẫn đến nó. Cần xem cause và lệnh launch.

## 5. Launcher chuyên dụng giải quyết vấn đề như thế nào?

Một **launcher chuyên dụng** có thể thực hiện các công việc mà launcher JDK tiêu chuẩn không tự làm:

1. Đọc định dạng archive của framework.
2. Phân biệt phần **application classes** với phần **libraries**.
3. Truy cập archive con qua abstraction/JAR API riêng hoặc, với giải pháp khác, trích chúng ra file tạm.
4. Tạo/cấu hình ClassLoader nhận biết các vị trí đó.
5. Thiết lập môi trường cần thiết (ví dụ TCCL theo thiết kế framework).
6. Gọi `main()` thật của ứng dụng sau khi đã chuẩn bị loader.

**Không có một kỹ thuật duy nhất cho mọi framework.** Cách truy cập nested JAR trực tiếp và cách giải nén tạm là hai lựa chọn triển khai khác nhau. Không nên khẳng định launcher nào cũng luôn extract JAR con.

### 5.1. Ví dụ Spring Boot executable JAR

Tài liệu chính thức của Spring Boot mô tả cấu trúc điển hình:

~~~text
myapp.jar
├── META-INF/
│   └── MANIFEST.MF
├── org/springframework/boot/loader/...  (các class bootstrap của Boot)
└── BOOT-INF/
    ├── classes/
    │   └── com/example/MyApplication.class
    └── lib/
        ├── dependency-1.jar
        └── dependency-2.jar
~~~

Chính layout này cho thấy **`BOOT-INF/classes/` không phải classpath root tiêu chuẩn** của `java -cp myapp.jar com.example.MyApplication`, và `BOOT-INF/lib/*.jar` là những library JAR **lồng**.

Khi artifact được đóng gói bởi Spring Boot plugin đúng chuẩn và chạy theo cách được hỗ trợ:

~~~sh
java -jar myapp.jar
~~~

manifest thường dùng launcher của Spring Boot làm main entry point:

~~~text
Main-Class: org.springframework.boot.loader.launch.JarLauncher
Start-Class: com.example.MyApplication
~~~

- `Main-Class` là **launcher** nằm ở vị trí có thể nạp bằng Java launcher tiêu chuẩn.
- `Start-Class` chỉ định **main class thực sự của ứng dụng**.
- Spring Boot launcher tạo môi trường nạp cần thiết cho `BOOT-INF/classes` và `BOOT-INF/lib` rồi mới gọi app main.

Tên class `org.springframework.boot.loader.launch.JarLauncher` áp dụng cho **layout Spring Boot hiện đại** (ví dụ Boot 3.2+). Tên package của launcher khác nhau giữa một số thế hệ Spring Boot; không nên copy manifest từ phiên bản khác mà không đối chiếu bản Boot đang dùng.

**Nguồn:** [Spring Boot — Launching Executable Jars](https://docs.spring.io/spring-boot/specification/executable-jar/launching.html).

### 5.2. Tại sao Spring Boot có thể đọc JAR lồng?

Tài liệu Spring Boot mô tả abstraction `NestedJarFile`: nó ánh xạ những entry của nested JAR tới vị trí trong JAR ngoài để có thể truy cập byte của class/resource bên trong mà **không nhất thiết phải giải nén mọi thư viện ra disk**.

Spring Boot cung cấp cơ chế tương thích với URL/JAR connection cho các dạng URL nội bộ như:

~~~text
jar:nested:/path/myapp.jar/!BOOT-INF/lib/dependency-1.jar!/pkg/Type.class
~~~

Đây là **ví dụ cú pháp do Spring Boot hỗ trợ bằng cơ chế của nó**, **không phải** cú pháp được `URLClassLoader` JDK độc lập bảo đảm hiểu chỉ nhờ gọi `new URL(...)`. Đừng suy từ hình thức gần giống `jar:...!/` rằng URL protocol đã trở thành chuẩn JDK đa tầng.

**Nguồn:** [Spring Boot — NestedJarFile](https://docs.spring.io/spring-boot/specification/executable-jar/jarfile-class.html).

### 5.3. Điều gì xảy ra nếu bỏ qua launcher Spring Boot?

Giả sử bạn cố chạy:

~~~sh
java -cp myapp.jar com.example.MyApplication
~~~

với một archive Spring Boot đúng layout. JVM tiêu chuẩn có thể không tìm thấy `com.example.MyApplication`, vì class nằm dưới `BOOT-INF/classes/com/example/...` thay vì root package path. Ngay cả khi tìm được entry bằng API đọc JAR, các libraries dưới `BOOT-INF/lib/` cũng không được tự nạp vào standard classpath.

Vì vậy, **Spring Boot `bootJar` không tương đương plain JAR** của Gradle/Maven. Dùng đúng launcher hoặc cơ chế unpack/exploded được tài liệu hóa cho version đó. Không coi `java -jar` là nguyên nhân duy nhất giúp nested JAR hoạt động; **chính bootstrap classes và loader của Spring Boot trong artifact** cung cấp khả năng này.

## 6. Thực hành độc lập Java 17+: standard URLClassLoader thấy nested entry nhưng không thấy class

Thực hành này **không cần Spring Boot, Maven hay Gradle**. Ta tự tạo một JAR chứa một JAR con, thử tìm class bên trong qua standard URLClassLoader, sau đó **trích JAR con ra file thật** và nạp lại bằng standard URLClassLoader.

### 6.1. Cấu trúc source và output

~~~text
demo/
├── src/
│   ├── app/
│   │   └── NestedJarDemo.java
│   └── lib/
│       └── demo/lib/Helper.java
└── build/
    ├── app/
    ├── lib/
    ├── pack/
    │   └── BOOT-INF/lib/helper.jar
    ├── helper.jar
    ├── outer.jar
    └── helper-extracted.jar   (tạo khi chạy)
~~~

Đường `BOOT-INF/lib/` ở thực hành chỉ được chọn để mô phỏng **vị trí một JAR lồng**; `outer.jar` của bài **không phải** Spring Boot executable JAR, vì nó không chứa Boot loader. Điều này giúp cô lập giới hạn của Java/JDK thuần.

### 6.2. Tệp `src/lib/demo/lib/Helper.java`

~~~java
package demo.lib;

public class Helper {
    public static String message() {
        return "from-library";
    }
}
~~~

`Helper.class` sẽ chỉ xuất hiện **trong JAR con**; không được vô tình đưa `build/lib` hoặc `build/helper.jar` lên classpath runtime của launcher trong bước thử thất bại.

### 6.3. Tệp `src/app/NestedJarDemo.java`

~~~java
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class NestedJarDemo {
    public static void main(String[] args) throws Exception {
        Path outer = Path.of("build/outer.jar");

        try (URLClassLoader flat = new URLClassLoader(
                new URL[] { outer.toUri().toURL() },
                NestedJarDemo.class.getClassLoader())) {

            System.out.println("nestedEntryVisible="
                    + (flat.getResource("BOOT-INF/lib/helper.jar") != null));

            try {
                flat.loadClass("demo.lib.Helper");
                System.out.println("ordinaryLoaderFound=true");
            } catch (ClassNotFoundException expected) {
                System.out.println("ordinaryLoaderFound=false");
            }
        }

        Path extracted = Path.of("build/helper-extracted.jar");
        try (JarFile archive = new JarFile(outer.toFile())) {
            JarEntry nested =
                    archive.getJarEntry("BOOT-INF/lib/helper.jar");
            try (InputStream stream = archive.getInputStream(nested)) {
                Files.copy(stream, extracted,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }

        try (URLClassLoader unpacked = new URLClassLoader(
                new URL[] { extracted.toUri().toURL() },
                NestedJarDemo.class.getClassLoader())) {

            Class<?> helper = unpacked.loadClass("demo.lib.Helper");
            System.out.println("extractedLoaderFound="
                    + (helper.getClassLoader() == unpacked));
            System.out.println("message="
                    + helper.getMethod("message").invoke(null));
        }
    }
}
~~~

**Phân tích trước khi chạy:**

- `outer.toUri().toURL()` trỏ đến một **file JAR thông thường trên disk**. `URLClassLoader flat` tìm trong archive ngoài.
- `flat.getResource("BOOT-INF/lib/helper.jar")` tìm **entry là byte dữ liệu của JAR con**, nên có thể trả URL resource.
- `flat.loadClass("demo.lib.Helper")` tìm class theo đường `demo/lib/Helper.class` tương ứng, **không có ở root archive ngoài**, dẫn đến `ClassNotFoundException` được bắt.
- `JarFile` đọc entry `BOOT-INF/lib/helper.jar` và `Files.copy` lưu byte đó thành **file `build/helper-extracted.jar`**.
- `URLClassLoader unpacked` dùng URL của **JAR đã được trích**; bây giờ `demo/lib/Helper.class` nằm trực tiếp trong JAR đang tìm, nên load thành công.
- Chương trình dùng `try-with-resources` để đóng các URLClassLoader/JarFile có thể giữ file handles. Việc đóng loader không đảm bảo unload mọi class ngay lập tức.

### 6.4. Biên dịch và đóng gói trên Linux/macOS

Từ thư mục `demo`, dùng JDK 17 trở lên:

~~~sh
mkdir -p build/app build/lib build/pack/BOOT-INF/lib
javac --release 17 -d build/lib src/lib/demo/lib/Helper.java
jar --create --file build/helper.jar -C build/lib .
cp build/helper.jar build/pack/BOOT-INF/lib/helper.jar
javac --release 17 -d build/app src/app/NestedJarDemo.java
jar --create --file build/outer.jar --main-class NestedJarDemo \
    -C build/app . \
    -C build/pack BOOT-INF/lib/helper.jar
java -jar build/outer.jar
~~~

Dấu `\` cuối dòng chỉ là cú pháp nối lệnh shell POSIX. Có thể viết lệnh `jar --create ...` trên **một dòng** nếu không dùng Bash/POSIX shell.

### 6.5. Lệnh tương đương trên Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force build/app, build/lib, build/pack/BOOT-INF/lib | Out-Null
javac --release 17 -d build/lib src/lib/demo/lib/Helper.java
jar --create --file build/helper.jar -C build/lib .
Copy-Item build/helper.jar build/pack/BOOT-INF/lib/helper.jar
javac --release 17 -d build/app src/app/NestedJarDemo.java
jar --create --file build/outer.jar --main-class NestedJarDemo -C build/app . -C build/pack BOOT-INF/lib/helper.jar
java -jar build/outer.jar
~~~

Với cả hai hệ điều hành, hãy chạy từ đúng thư mục `demo` vì `Path.of("build/outer.jar")` là đường dẫn tương đối. Không thêm `build/helper.jar` hoặc `build/lib` vào runtime classpath khi chạy ví dụ.

### 6.6. Stdout chính xác đã được kiểm chứng

~~~text
nestedEntryVisible=true
ordinaryLoaderFound=false
extractedLoaderFound=true
message=from-library
~~~

Bốn dòng thể hiện **ba trạng thái khác nhau**:

1. `nestedEntryVisible=true`: outer JAR thực sự chứa entry có tên `BOOT-INF/lib/helper.jar`; standard loader có thể tìm entry như một resource.
2. `ordinaryLoaderFound=false`: standard loader **không tự đi tiếp vào JAR con** để nạp class.
3. `extractedLoaderFound=true`: khi library được lưu thành file JAR riêng, loader mới định nghĩa được class.
4. `message=from-library`: class đã được nạp và có thể gọi public method.

**Ranh giới của thí nghiệm:** việc trích archive ở đây chỉ để làm rõ cơ chế. Trong ứng dụng production, việc extract thư viện cần chú ý bảo mật input, thư mục tạm, quyền ghi, cleanup, đồng bộ/concurrency và checksum/khả năng tái lập build. Không trích tùy tiện nội dung archive không đáng tin.

### 6.7. Cách xác minh “file con” và “class trực tiếp” trong JAR

~~~sh
jar --list --file build/outer.jar
jar --list --file build/helper.jar
~~~

Với `outer.jar`, bạn thấy `NestedJarDemo.class` và `BOOT-INF/lib/helper.jar`. Với `helper.jar`, bạn thấy `demo/lib/Helper.class`. Phép kiểm tra này giúp tách **resource JAR lồng** và **class root thực sự mà `URLClassLoader` đi tìm**.

## 7. Bảng so sánh các cơ chế chạy và nạp dependency

| Cơ chế | Layout thư viện | Ai cung cấp khả năng đọc? | Có cần nested-JAR-aware loader? |
|---|---|---|---|
| `java -cp "app.jar:lib/helper.jar" app.Main` | External JAR files | Java launcher + built-in loader | **Không** |
| `new URLClassLoader(new URL[]{helperFileUrl})` | External JAR file URL | `URLClassLoader` tiêu chuẩn | **Không** |
| `new URLClassLoader(new URL[]{outerFileUrl})`, helper nằm trong outer | Nested JAR entry | URLClassLoader tiêu chuẩn **không tự đọc entry thành classpath JAR** | **Có**, nếu muốn nạp trực tiếp từ nested |
| Shaded/uber JAR | Dependency classes gộp vào root JAR | Built-in loader/URLClassLoader thường là đủ | **Không** nếu package hợp lệ |
| Spring Boot executable JAR `java -jar` | `BOOT-INF/classes` + `BOOT-INF/lib/*.jar` | Boot launcher + nested-archive loader/handler | **Có, Boot cung cấp** |
| Extract helper.jar rồi nạp bằng URLClassLoader | JAR con thành file độc lập | Code extract + URLClassLoader tiêu chuẩn | **Không** sau khi extract |
| Container/script chạy JAR distribution | Thư mục app + lib/*.jar | Script tạo `-cp` hoặc launcher framework | Tùy packaging |

Bảng mô tả **cơ chế điển hình**, không phải cam kết tất cả framework đều đóng gói đúng một cách. “Executable JAR” chỉ nói artifact có đường khởi chạy phù hợp; phải xác định kiểu launcher và layout class thực tế.

## 8. Các nhầm lẫn quan trọng

### “Dùng `java -jar` thì JVM tự nạp mọi nested JAR”

Sai. `java -jar` chỉ chọn JAR/main entry point theo hợp đồng launcher. Spring Boot executable JAR dùng `java -jar` **vì manifest trỏ đến Boot launcher** biết cách đọc nested libraries.

### “`URLClassLoader` nạp resource `helper.jar` được thì nạp class ở trong đó được”

Sai. Resource của outer archive khác archive nằm bên trong resource đó. `getResource("BOOT-INF/lib/helper.jar") != null` không chứng minh `loadClass("demo.lib.Helper")` thành công.

### “Ghi URL `jar:file:/outer.jar!/lib/helper.jar!/` là đủ”

Sai trong chuẩn JDK thông thường. Một lớp xử lý archive/URL chuyên dụng phải hiểu ngữ nghĩa nhiều tầng; cú pháp có dấu `!/` không tự cấp capability nạp nested JAR.

### “Thêm `BOOT-INF/lib` vào `-cp` là sẽ dùng được thư viện”

Không đúng nếu đó chỉ là **đường entry bên trong một outer JAR** và không phải directory thật trên disk. Khi trích archive thành thư mục, classpath có thể trỏ đến các file JAR đã được trích; nhưng **cơ chế này khác với tìm trực tiếp nested JAR chưa trích**.

### “Plain JAR, shaded JAR, Boot JAR chỉ khác tên gọi”

Sai: chúng khác về cách ghi dependency và classpath roots. Một plain JAR có thể dùng external libs, shaded JAR merge class files, Boot JAR thường dùng nested libs và bootstrap loader.

### “Spring Boot luôn giải nén tất cả JAR con ra thư mục tạm”

Không chính xác. Spring Boot hỗ trợ truy cập nested JAR thông qua abstraction như `NestedJarFile` và không cần unpack toàn bộ dependency ra files trong cách hoạt động thông thường.

### “Bất kỳ tên package/class bị thiếu nào đều do nested-JAR limitation”

Không chính xác. `ClassNotFoundException`, `NoClassDefFoundError`, `NoSuchMethodError`, `UnsupportedClassVersionError` và `IllegalAccessError` có nguyên nhân khác nhau; cần xem stack trace, version, loader và archive layout trước khi kết luận.

## 9. Quy trình chẩn đoán dự án thật

Nếu dự án đã được build thành một JAR, có nested dependencies, nhưng không tìm được class:

1. **Xem cấu trúc archive thực tế:** dùng `jar --list --file app.jar` hoặc công cụ ZIP. Thư viện là file ngoài, JAR lồng dưới `BOOT-INF/lib`, hay classes merge trực tiếp?
2. **Đọc manifest:** `Main-Class` là class ứng dụng, Boot launcher hay launcher riêng? Có `Start-Class` khi dùng format của Spring Boot? Manifest có `Class-Path` external phù hợp không?
3. **Kiểm tra lệnh launch thật:** `java -jar`, `java -cp`, IDE, `gradlew run`, `bootRun`, script container có thể tạo loader/classpath khác nhau.
4. **Xác định loader đang tìm class:** `ClassLoader.getSystemClassLoader()` có phải loader chứa nested dependencies không? Framework có child/special loader không? In `getClassLoader()` của class đã được nạp nếu có thể.
5. **Đối chiếu class name và entry:** tìm đường `demo/lib/Helper.class` ở root của JAR được loader hỗ trợ hay chỉ nằm trong `helper.jar` là một entry?
6. **Nếu dùng Spring Boot:** chạy đúng Boot launcher/package format phù hợp với version, tránh bỏ qua bootstrapping bằng cách chạy thẳng `Start-Class` từ root outer JAR.
7. **Nếu dùng URLClassLoader thuần JDK:** truyền URL của **file JAR thật/directory thật**; đừng giả định loader tự expand nested JARs.
8. **Nếu cần sửa packaging:** chọn external libs + launch script, shaded JAR hoặc framework-compatible nested JAR có launcher; kiểm thử từ artifact phát hành, không chỉ trong IDE.
9. **Nếu vẫn lỗi sau khi tìm được class:** kiểm tra dependencies bắc cầu, phiên bản, JPMS exports, loader identity và lỗi liên kết/initialization, không chỉ vị trí JAR.

### Công cụ thường dùng

~~~sh
jar --list --file app.jar
jar --describe-module --file app.jar
java -Xlog:class+load=info -jar app.jar
~~~

`jar --describe-module` hữu ích khi liên quan JPMS; không bắt buộc mọi executable archive là named module. `-Xlog:class+load` trên JDK HotSpot hiện đại giúp quan sát nguồn class đang load, nhưng output dài và phụ thuộc JDK; không dùng như stdout cố định của demo.

### Câu hỏi khi gặp stack trace

| Triệu chứng | Kiểm tra trước |
|---|---|
| `ClassNotFoundException: demo.lib.Helper` từ `URLClassLoader` | URL search path; class có ở JAR root hay chỉ bên trong nested JAR? |
| `NoClassDefFoundError: demo/lib/Helper` khi chạy plain outer JAR | Runtime loader thiếu dependency, archive layout và nguyên nhân gốc |
| `ClassNotFoundException` khi chạy Boot `Start-Class` bằng `-cp` | Có bỏ qua Boot launcher hoặc sai layout `BOOT-INF/classes` không? |
| Chạy `bootRun` tốt, `java -jar` thất bại | Artifact đã đóng gói đúng `bootJar` chưa? Version/manifest/loader có đúng? |
| `NoSuchMethodError` mặc dù class được tìm thấy | Phiên bản dependency compile/runtime, ABI mismatch |
| `ClassCastException` với cùng binary class name | Class có bị hai loaders định nghĩa thành hai runtime types không? |

## 10. Tự kiểm tra kiến thức

1. Vì sao `jar --list --file outer.jar` liệt kê `lib/helper.jar` nhưng không liệt kê `demo/lib/Helper.class` bên trong JAR con như một entry trực tiếp?
2. `getResource("BOOT-INF/lib/helper.jar") != null` chứng minh điều gì? Điều gì nó **không** chứng minh?
3. `URLClassLoader` tiêu chuẩn nhận URL trỏ đến một directory hoặc JAR file bên ngoài. Vì sao nó không tự duyệt mọi JAR entry trong archive ngoài?
4. Sự khác nhau giữa `app-all.jar` shaded và `app-nested.jar` nested là gì?
5. Khi chạy `java -jar app.jar`, JVM dựa trên manifest attribute nào để chọn main class?
6. Vì sao không nên đưa `BOOT-INF/classes/com/example/App.class` vào outer JAR rồi mong `java -cp outer.jar com.example.App` tự tìm thấy?
7. Spring Boot `JarLauncher` chịu trách nhiệm gì trước khi gọi `Start-Class`?
8. Ký hiệu `jar:nested:...` có phải giao thức URL chung được mọi JDK `URLClassLoader` mặc định hỗ trợ không?
9. Vì sao trích JAR con thành file thật rồi đưa URL file đó vào `URLClassLoader` làm phép thử thành công?
10. Chỉ cần đặt `BOOT-INF/lib/helper.jar` trong archive và đổi `Main-Class` thành app main class có đủ để tạo Spring Boot-compatible executable JAR không?
11. Một framework có bắt buộc phải giải nén mọi nested JAR ra disk không? Nêu cơ chế thay thế.
12. Khi muốn chọn giữa external libs, shaded JAR và framework launcher, bạn cân nhắc tính gọn của artifact, khả năng cập nhật library, rủi ro merge resource và khả năng chạy độc lập ra sao?
13. Hai bước nào kiểm chứng được **archive layout** và **loader thực sự dùng** khi lỗi chỉ xuất hiện sau deploy?
14. Vì sao `java.class.path` không phản ánh đầy đủ những JAR mà framework custom loader có thể tìm thấy?
15. Nếu loader tìm được class nhưng method bị thiếu, đó còn là giới hạn nested JAR hay cần điều tra version/ABI?

**Tiêu chí hoàn thành:** tự tạo và chạy `outer.jar` của mục 6; dự đoán chính xác bốn dòng stdout; phân biệt được “JAR con là resource” với “JAR con là classpath entry”; giải thích đúng vai trò Boot launcher hoặc launcher chuyên dụng và lựa chọn được một packaging/runtime path nhất quán.

## 11. Tài liệu gốc để tra cứu

- [Java SE 17 — `URLClassLoader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): cơ chế tìm từ URL của JAR/directory và delegation.
- [Java SE 21 — `JarURLConnection`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/JarURLConnection.html): ngữ nghĩa JAR URL của JDK.
- [Java SE 21 — Java launcher `java`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html): `-cp`, `-jar`, manifest/main class và module path.
- [Spring Boot — Nested JARs](https://docs.spring.io/spring-boot/specification/executable-jar/nested-jars.html): vì sao nested JAR không phải tính năng nạp class tiêu chuẩn và layout `BOOT-INF`.
- [Spring Boot — NestedJarFile](https://docs.spring.io/spring-boot/specification/executable-jar/jarfile-class.html): cách Boot truy cập nested archive và URL/connection.
- [Spring Boot — Launching Executable Jars](https://docs.spring.io/spring-boot/specification/executable-jar/launching.html): `JarLauncher`, `Main-Class` và `Start-Class`.

Quy tắc trình bày dùng hợp đồng của JDK và Spring Boot được tài liệu hóa; ví dụ thực nghiệm được biên dịch bằng `javac --release 17` và chạy bằng Java, không cần framework. Dạng URL và tên launcher cụ thể phải được đối chiếu với phiên bản framework đang sử dụng.

</details>

- [Quay lại đầu trang](#back-to-top)
