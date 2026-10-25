<a id="back-to-top"></a>

# Kiến trúc plugin

## Menu
- [Phân chia ứng dụng chủ, API chung và triển khai plugin](#plugin-architecture-001)
- [Chọn loader cho API chung và phụ thuộc riêng](#plugin-architecture-002)
- [Mỗi plugin một loader hay nhiều plugin dùng chung loader](#plugin-architecture-003)
- [Giao tiếp qua interface, DTO và exception có định danh thống nhất](#plugin-architecture-004)
- [Khởi tạo plugin và quản lý context class loader](#plugin-architecture-005)
- [Quy trình dừng plugin, hủy đăng ký và giải phóng tài nguyên](#plugin-architecture-006)
- [Thay phiên bản plugin bằng loader mới](#plugin-architecture-007)
- [Ranh giới của hot reload và trạng thái đang được sử dụng](#plugin-architecture-008)
- [Vì sao class loader không tạo thành sandbox bảo mật](#plugin-architecture-009)
- [Nhận diện mô hình cô lập trong container và test runner](#plugin-architecture-010)

## <a id="plugin-architecture-001">Phân chia ứng dụng chủ, API chung và triển khai plugin</a>

<details>
<summary>Xem chi tiết</summary>

# Phân chia ứng dụng chủ, API chung và triển khai plugin bằng ClassLoader

## 1. Vấn đề kiến trúc: host gọi plugin mà không phụ thuộc implementation

Giả sử một ứng dụng Java cần tải thêm tính năng sau khi ứng dụng đã được phát hành: bộ xuất dữ liệu, bộ phân tích nội dung, connector, driver hoặc bộ xử lý tùy chọn. Nếu ứng dụng chủ (**host**) import trực tiếp từng class implementation, mọi lần thêm plugin đều kéo theo sửa mã host, thay đổi dependency graph và có thể gây xung đột phiên bản thư viện.

Một kiến trúc plugin bền vững tách ba vai trò:

1. **Ứng dụng chủ (host):** điều phối vòng đời, tìm và chọn plugin, gọi nghiệp vụ qua hợp đồng, quản lý lỗi, tài nguyên và phiên bản.
2. **API chung (shared API/SPI):** kiểu giao tiếp ổn định — interface, DTO, exception hoặc metadata mà **cả host và plugin cùng nhìn thấy dưới một runtime class identity**.
3. **Triển khai plugin (implementation):** code tính năng, dependencies riêng, service provider, class/resources chỉ cần xuất hiện trong ClassLoader của plugin.

Một mô hình phổ biến:

~~~text
             Application/Parent ClassLoader
            +---------------------------------+
            | Host: plugin.host.PluginHost     |
            | API:  plugin.api.Plugin          |
            | API:  các DTO / contract khác    |
            +---------------------------------+
                         |
                         | parent delegation, chia sẻ API
                         v
             Child ClassLoader plugin A
            +---------------------------------+
            | plugin.impl.EchoPlugin          |
            | plugin-specific classes/JARs   |
            | META-INF/services/...          |
            +---------------------------------+

             Child ClassLoader plugin B
            +---------------------------------+
            | plugin.other.ReportPlugin       |
            | dependency version riêng        |
            +---------------------------------+
~~~

**Nguyên tắc quan trọng nhất:** host và plugin phải dùng **cùng `Class<?>` object** cho kiểu `plugin.api.Plugin`. Chỉ trùng tên `plugin.api.Plugin` hoặc giống bytecode không đủ; một interface cùng tên do hai defining ClassLoader khác nhau tạo ra là **hai runtime types khác nhau**.

Mục tiêu bài: thiết kế ba phần và dependency boundary; thực thi ví dụ Java 17+ nạp plugin từ JAR không nằm trong system classpath; hiểu `ServiceLoader`, parent delegation, đóng gói API, lỗi type identity, version compatibility, quản lý vòng đời và cách mở rộng bằng JPMS khi cần.

## 2. Trách nhiệm cụ thể của ba phần

### 2.1. Host: người điều phối, không sở hữu implementation

Host biết **contract** của plugin, chẳng hạn:

~~~java
Plugin plugin = /* instance do discovery tạo */;
String result = plugin.execute("hello");
~~~

Đây là đoạn **minh họa bên trong một method**, chưa phải chương trình độc lập; `Plugin` thuộc API chung.

Host thường chịu trách nhiệm:

- Chọn thư mục/artifact plugin được phép nạp và xác định phiên bản tương thích.
- Tạo hoặc chọn ClassLoader/module layer cho mỗi plugin hoặc nhóm plugin.
- Khám phá implementation dựa trên metadata, `ServiceLoader` hoặc registry.
- Kiểm tra API version, quyền truy cập và điều kiện khởi tạo.
- Khởi tạo/khởi động, quản lý request, dừng và thu hồi tài nguyên plugin.
- Xử lý lỗi và không để một plugin thất bại làm vô hiệu toàn bộ host nếu kiến trúc cho phép.

**Host không nên cần import `plugin.impl.EchoPlugin`** để biên dịch. Nếu import implementation cụ thể, host có thể vẫn chạy được nhưng đã phá vỡ mục tiêu thay plugin độc lập.

### 2.2. Shared API: điểm hẹn kiểu dữ liệu

Contract nhỏ, ổn định:

~~~java
package plugin.api;

public interface Plugin {
    String id();
    String execute(String input);
}
~~~

API cần nằm trong một artifact/version do host quản lý và **được ClassLoader chung, thường là parent, định nghĩa một lần**.

Đặc điểm API tốt:

- Chứa interface, enums, DTO hoặc abstractions đủ dùng; **không chứa chi tiết engine/vendor của plugin**.
- Các kiểu xuất hiện trong method signatures cũng phải chia sẻ identity, ví dụ `PluginRequest`, `PluginResponse`, `PluginException`.
- Không yêu cầu host biết private implementation classes.
- Có policy versioning và compatibility: binary/source/API contract, default methods khi thích hợp, feature negotiation hoặc manifest version.
- Nếu API cần được cả hai phía gọi, nó phải visible theo classpath/module rules thích hợp.

**Cần chú ý signature closure:** không chỉ `Plugin.class`, mà toàn bộ kiểu trong tham số, return type, callbacks và exceptions crossing boundary cũng cần kiến trúc class identity phù hợp. Nếu interface được parent định nghĩa nhưng một DTO lại bị đóng gói riêng vào cả host và plugin, vẫn có thể xảy ra `ClassCastException` hoặc `LinkageError`.

### 2.3. Plugin implementation: code thay thế được

Ví dụ:

~~~java
package plugin.impl;

import java.util.Locale;
import plugin.api.Plugin;

public final class EchoPlugin implements Plugin {
    @Override
    public String id() {
        return "echo";
    }

    @Override
    public String execute(String input) {
        return input.toUpperCase(Locale.ROOT);
    }
}
~~~

Plugin chỉ cần compile-time access đến `plugin.api.Plugin`. Ứng dụng host không cần compile-time dependency đến `EchoPlugin`.

Các thư viện nội bộ mà plugin cần có thể đặt trong JAR implementation hoặc được bố trí thành các JAR độc lập mà **plugin loader thực sự nhìn thấy**. Đừng nhầm khai báo dependency của build tool với đường classpath/loader của JVM ở runtime.

## 3. Quy tắc ClassLoader khiến kiến trúc này hoạt động

### 3.1. Runtime type identity

Một runtime class thường được nhận diện bằng cặp:

~~~text
(binaryName, definingClassLoader)
~~~

Ví dụ, nếu host loader `L_host` định nghĩa `plugin.api.Plugin`:

~~~text
Plugin_host = ("plugin.api.Plugin", L_host)
~~~

Nếu plugin loader `L_plugin` **tự định nghĩa thêm một bản API trùng tên**:

~~~text
Plugin_plugin = ("plugin.api.Plugin", L_plugin)
~~~

thì:

~~~text
Plugin_host != Plugin_plugin
~~~

Dù source interface giống hệt, đối tượng triển khai bản `Plugin_plugin` **không tự trở thành instance của** `Plugin_host`. Host ép kiểu có thể ném `ClassCastException`; `ServiceLoader` có thể báo provider **not a subtype** bằng `ServiceConfigurationError`.

**Thiết kế đúng:** `L_plugin` **ủy quyền load** những package contract (`plugin.api.*`) cho loader mà host sử dụng. Trong parent-first `URLClassLoader` thông thường, nếu API nằm ở parent và plugin JAR **không chứa bản sao API**, implementation tự nhiên dùng đúng type của host.

### 3.2. Parent delegation và cách chia sẻ API

~~~text
pluginLoader.loadClass("plugin.api.Plugin")
    -> parentLoader.loadClass("plugin.api.Plugin")
    -> trả lại Class<Plugin> mà host cũng dùng

pluginLoader.loadClass("plugin.impl.EchoPlugin")
    -> parent không có class implementation
    -> child tìm trong plugin JAR
    -> class EchoPlugin do child định nghĩa
~~~

Cơ chế của `URLClassLoader` thường theo parent-first delegation. Tuy nhiên framework/containers có thể dùng child-first hoặc delegation tùy namespace. Vì vậy, **không đủ khi chỉ nói “plugin loader là child”**: nếu chính sách child-first chọn bản API trong plugin JAR, identity vẫn có thể bị chia đôi.

Một cách phòng ngừa là quản lý rõ các namespace:

- `java.*` và platform APIs: không được tùy ý shadow.
- `plugin.api.*` và các shared SDK packages: luôn delegating tới loader chung.
- `plugin.impl.*` và private dependencies: lấy từ loader thuộc plugin khi phù hợp.
- Các shared library khác: xác định rõ dùng chung hay cho phép phiên bản riêng; đừng vô tình tạo API có signature phụ thuộc vào type chỉ tồn tại ở một plugin.

### 3.3. ClassLoader không phải ranh giới bảo mật

ClassLoader hỗ trợ tra cứu class, type identity và một mức cô lập namespace. Nó **không** là sandbox đủ để chạy plugin không đáng tin: code Java trong cùng process có thể truy cập tài nguyên hệ thống theo quyền của tiến trình, tiêu thụ CPU/RAM, tạo threads và tương tác với thư viện chung.

Đối với plugin của bên thứ ba không đáng tin, xem xét **process isolation**, OS/container permissions, resource quotas, ký/xác minh artifacts và giao thức IPC. Đừng dựa trên `ClassLoader` hoặc `SecurityManager` cũ như hàng rào duy nhất.

## 4. Tổ chức mã và artifacts: một API, hai phía phụ thuộc

Một layout đơn giản:

~~~text
project/
├── src/
│   ├── api/
│   │   └── plugin/api/Plugin.java
│   ├── host/
│   │   └── PluginHost.java
│   └── plugin/
│       └── plugin/impl/EchoPlugin.java
├── build/
│   ├── api/
│   ├── host/
│   └── plugin/
│       ├── plugin/impl/EchoPlugin.class
│       └── META-INF/services/plugin.api.Plugin
└── dist/
    └── plugin-echo.jar
~~~

**Đồ thị dependency khi biên dịch:**

~~~text
Shared API module/artifact
       ^             ^
       |             |
      Host         Plugin implementation

Không có compile dependency:
Host ----------> plugin.impl.EchoPlugin
~~~

**Đồ thị runtime/class loaders:**

~~~text
JVM launcher classpath:
    build/host + build/api
    => application loader định nghĩa host và Plugin API

URLClassLoader child:
    dist/plugin-echo.jar
    => implementation + provider descriptor
    => parent = application loader
~~~

Host compile với API; plugin compile với API; nhưng **plugin JAR deploy không cần đóng gói thêm class `plugin/api/Plugin.class`**. Plugin API đã được host cung cấp ở runtime.

Với build tool:

| Vai trò | Dependency hợp lý | Cảnh báo |
|---|---|---|
| Host → shared API | `implementation` / Maven compile | API phải nằm trong host runtime classpath/module graph |
| Plugin → shared API | `compileOnly` (Gradle) hoặc `provided` (Maven), nếu host cam kết cung cấp | Đảm bảo API thật sự có ở parent loader; không để API bản khác vô tình bị nhúng |
| Plugin → private library | Runtime dependency/packaging phù hợp loader plugin | Build resolution không tự làm JVM nhìn thấy JAR nếu deploy thiếu |
| Tests → host/API/implementation | Test dependencies theo từng phần | Test pass không chứng minh classloader layout production tương tự |

Đây là **hướng cấu hình phổ biến**, không bắt buộc cho mọi framework. Với Gradle composite/multi-project, Maven modules hoặc JPMS, cách công bố artifact và scope khác nhau; bạn cần kiểm tra **plugin JAR/distribution cuối** bằng `jar --list` và thử chạy với đúng lệnh JVM.

## 5. Java SPI và ServiceLoader: host tìm implementation mà không import class

Java hỗ trợ Service Provider Interface (**SPI**) dựa trên `java.util.ServiceLoader`. Hai thành phần:

1. **Service type:** interface `plugin.api.Plugin` mà host và provider đều nhận biết.
2. **Provider configuration:** file text tại `META-INF/services/plugin.api.Plugin`, chứa **binary name** của provider class.

Nội dung file:

~~~text
plugin.impl.EchoPlugin
~~~

Tên file phải là **binary name đầy đủ** của service type, không có `.class` hay phần mở rộng khác. Mỗi provider khai báo theo một dòng. Provider class trong ví dụ là public, có public no-arg constructor mặc định và implements đúng service interface runtime.

Host dùng overload:

~~~java
ServiceLoader<Plugin> providers =
        ServiceLoader.load(Plugin.class, pluginLoader);
~~~

**Quan trọng:** overload hai tham số dùng **ClassLoader được truyền tường minh**, không cần hy vọng ambient Thread Context ClassLoader trỏ đúng plugin. Overload một tham số `ServiceLoader.load(Plugin.class)` lại dùng context ClassLoader của current thread và có thể cho kết quả khác.

`ServiceLoader` có lazy discovery/instantiation; lỗi đọc provider hoặc provider không hợp lệ có thể ném `ServiceConfigurationError`. Do đó host production nên lập chính sách xử lý provider errors và đóng tài nguyên, thay vì coi discovery không thể thất bại.

## 6. Thực hành Java 17+ chạy độc lập: host/API/plugin tách ba phần

Đây là thực hành hoàn chỉnh **không cần Maven/Gradle và không tải thư viện từ internet**. Nó kiểm chứng:

- Host classpath chỉ có **host** và **API**, không có plugin implementation.
- Host không tự tìm được implementation bằng application loader.
- Child `URLClassLoader` tìm được plugin từ JAR triển khai.
- `ServiceLoader.load(service, pluginLoader)` phát hiện provider.
- Provider implement **chính interface API object** của host, nên host gọi trực tiếp qua `Plugin` mà không cast reflective.
- Thư viện plugin được dùng mà host **không import** `EchoPlugin`.

### 6.1. File `src/api/plugin/api/Plugin.java`

~~~java
package plugin.api;

public interface Plugin {
    String id();
    String execute(String input);
}
~~~

Đây là hợp đồng chung của cả hệ. Nó dùng `String` để ví dụ gọn; trong ứng dụng thật có thể dùng các DTO không ràng buộc implementation.

### 6.2. File `src/plugin/plugin/impl/EchoPlugin.java`

~~~java
package plugin.impl;

import java.util.Locale;
import plugin.api.Plugin;

public final class EchoPlugin implements Plugin {
    public String id() {
        return "echo";
    }

    public String execute(String input) {
        return input.toUpperCase(Locale.ROOT);
    }
}
~~~

### 6.3. File provider descriptor

Tạo file:

~~~text
build/plugin/META-INF/services/plugin.api.Plugin
~~~

Nội dung nguyên một dòng (cho phép newline cuối):

~~~text
plugin.impl.EchoPlugin
~~~

Nội dung này phải đi vào **JAR plugin**, không phải chỉ tồn tại ở source tree. Trong ví dụ, tạo file thẳng dưới `build/plugin` sau khi biên dịch là đủ.

### 6.4. File `src/host/PluginHost.java`

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ServiceLoader;
import plugin.api.Plugin;

public final class PluginHost {
    public static void main(String[] args) throws Exception {
        ClassLoader hostLoader = PluginHost.class.getClassLoader();
        System.out.println("hostApiLoaderSame="
                + (hostLoader == Plugin.class.getClassLoader()));

        try {
            Class.forName("plugin.impl.EchoPlugin", false, hostLoader);
            System.out.println("hostCannotLoadPlugin=false");
        } catch (ClassNotFoundException expected) {
            System.out.println("hostCannotLoadPlugin=true");
        }

        URL pluginUrl = Path.of("dist/plugin-echo.jar").toUri().toURL();
        try (URLClassLoader pluginLoader =
                     new URLClassLoader(new URL[] { pluginUrl }, hostLoader)) {
            int count = 0;
            for (Plugin plugin : ServiceLoader.load(Plugin.class, pluginLoader)) {
                count++;
                System.out.println("pluginImplLoaderIsChild="
                        + (plugin.getClass().getClassLoader() == pluginLoader));
                System.out.println("sharedApiIdentity="
                        + (plugin.getClass().getInterfaces()[0] == Plugin.class));
                System.out.println("pluginId=" + plugin.id());
                System.out.println("result=" + plugin.execute("hello"));
            }
            System.out.println("pluginsFound=" + count);
        }
    }
}
~~~

**Một số chi tiết quan trọng:**

- `PluginHost` chỉ import `plugin.api.Plugin`. Nó không import hoặc trực tiếp new `EchoPlugin`.
- Gọi `Class.forName(..., false, hostLoader)` có chủ đích minh họa application loader không tìm được plugin. Nó **không** yêu cầu initialization.
- `URLClassLoader` được truyền URL JAR plugin và parent `hostLoader`. Trong classpath setup này, `plugin.api.Plugin` đến từ parent.
- `ServiceLoader.load(Plugin.class, pluginLoader)` tìm provider metadata bằng loader được chỉ định và instantiate provider khi duyệt.
- `getInterfaces()[0]` là phép kiểm tra đủ dùng **cho đúng class `EchoPlugin` trong ví dụ**, nơi class trực tiếp chỉ implements một interface. Trong code production không được giả định interface đầu tiên trong mảng luôn là API; có thể dùng `Plugin.class.isAssignableFrom(providerClass)` khi phù hợp.
- `try-with-resources` đóng `URLClassLoader` sau khi đã dùng. Nó đóng tài nguyên loader, **không bảo đảm các class/plugin objects được unload ngay lập tức**.

### 6.5. Biên dịch và đóng gói trên Linux/macOS

Đứng trong thư mục `project` (với đúng cây source đã mô tả):

~~~sh
mkdir -p build/api build/host build/plugin/META-INF/services dist

javac --release 17 -d build/api \
    src/api/plugin/api/Plugin.java

javac --release 17 -cp build/api -d build/plugin \
    src/plugin/plugin/impl/EchoPlugin.java

printf 'plugin.impl.EchoPlugin\n' \
    > build/plugin/META-INF/services/plugin.api.Plugin

jar --create --file dist/plugin-echo.jar -C build/plugin .

javac --release 17 -cp build/api -d build/host \
    src/host/PluginHost.java

java -cp "build/host:build/api" PluginHost
~~~

Dấu `:` là separator classpath Linux/macOS; dấu `\` cuối dòng là nối lệnh POSIX shell, không dùng tương tự trong PowerShell.

### 6.6. Biên dịch và chạy trên Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force build/api, build/host, build/plugin/META-INF/services, dist | Out-Null

javac --release 17 -d build/api src/api/plugin/api/Plugin.java

javac --release 17 -cp build/api -d build/plugin src/plugin/plugin/impl/EchoPlugin.java

[System.IO.File]::WriteAllText(
    "build/plugin/META-INF/services/plugin.api.Plugin",
    "plugin.impl.EchoPlugin" + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)

jar --create --file dist/plugin-echo.jar -C build/plugin .

javac --release 17 -cp build/api -d build/host src/host/PluginHost.java

java -cp "build/host;build/api" PluginHost
~~~

PowerShell mẫu phía trên yêu cầu môi trường chạy JDK 17+ và PowerShell có hỗ trợ cú pháp constructor `::new` (Windows PowerShell 5+ hoặc PowerShell 7). Nếu dùng CMD, tạo provider file bằng công cụ text tương ứng và dùng `;` trong `-cp`.

### 6.7. Stdout chính xác đã kiểm chứng

~~~text
hostApiLoaderSame=true
hostCannotLoadPlugin=true
pluginImplLoaderIsChild=true
sharedApiIdentity=true
pluginId=echo
result=HELLO
pluginsFound=1
~~~

Mã trong thực hành đã được biên dịch với `javac --release 17` và chạy thành công bằng JDK. Không cần plugin JAR có `Main-Class`, vì host là executable entry point; `plugin-echo.jar` là **provider artifact**, không phải chương trình độc lập.

### 6.8. Vì sao từng dòng quan trọng?

| Output | Bằng chứng |
|---|---|
| `hostApiLoaderSame=true` | Host và shared API được defining loader chung nạp |
| `hostCannotLoadPlugin=true` | Plugin implementation không có trên application classpath |
| `pluginImplLoaderIsChild=true` | Child `URLClassLoader` định nghĩa implementation |
| `sharedApiIdentity=true` | Interface mà implementation trực tiếp implements là **chính `Plugin.class`** host sử dụng |
| `pluginId=echo` | Plugin được tạo và gọi qua interface thành công |
| `result=HELLO` | Host đã chạy nghiệp vụ qua contract, không cần import implementation |
| `pluginsFound=1` | Provider descriptor/JAR/ServiceLoader lookup hoạt động đúng, phát hiện đúng một provider |

Không in `ClassLoader.toString()` hay đường file tuyệt đối vì chúng có thể khác giữa các máy/JDK; ví dụ xác minh bằng **object identity** và logic nghiệp vụ.

### 6.9. Kiểm tra artifact đã đóng gói

~~~sh
jar --list --file dist/plugin-echo.jar
~~~

Những entry quan trọng:

~~~text
plugin/impl/EchoPlugin.class
META-INF/services/plugin.api.Plugin
~~~

**Không nên có** `plugin/api/Plugin.class` trong plugin JAR của kiến trúc này. Nếu có, trong parent-first URLClassLoader của demo API parent vẫn có thể thắng, nhưng một child-first loader hoặc framework khác có thể nạp bản trùng vào child và tạo hai interface types. Vì thế nên đặt điều kiện packaging rõ từ đầu.

Trong production, kiểm tra thêm implementation dependencies, resource collisions, duplicate providers và các đường `META-INF/services` khi dùng shaded/uber JAR. Công cụ shading cần **merge service descriptors** nếu nhiều thư viện cùng công bố provider.

## 7. Mở rộng sang nhiều plugin và các chiến lược loader

Có ba cách bố trí tùy nhu cầu. **Một loader chung** đơn giản khi toàn bộ plugin được phát hành cùng nhau và dùng dependency tương thích, nhưng cùng binary name chỉ có một định nghĩa trong loader ấy. **Một loader riêng cho mỗi plugin** phù hợp khi cần phiên bản thư viện khác nhau, nâng cấp hoặc dừng riêng; vẫn phải bảo đảm shared API do parent định nghĩa. **JPMS `ModuleLayer`** có chiến lược một hoặc nhiều loader cho modules và khai báo `uses`/`provides`, khác với classpath SPI dùng `META-INF/services`. Việc chọn mô hình, kiểm thử trùng tên và đánh đổi giữa isolation, state và memory được thực hành chuyên sâu trong các bài tiếp theo.

## 8. Vòng đời plugin: load, dùng, dừng và unload

Host quản lý các trạng thái `DISCOVERED → VALIDATED → LOADED → STARTED → STOPPING → STOPPED → RELEASED`, có rollback khi khởi động lỗi. Trước khi đóng loader, host phải chặn công việc mới, drain/cancel các tác vụ đang chạy, gọi stop hooks, hủy đăng ký callbacks/listeners, đóng streams/connections/executors, dọn `ThreadLocal`/TCCL/caches, rồi bỏ references. `URLClassLoader.close()` đóng tài nguyên loader quản lý nhưng không buộc JVM unload class hay dừng thread. Các bài về khởi tạo, shutdown và hot reload đi sâu vào cơ chế này; ở đây trách nhiệm then chốt là **host sở hữu vòng đời**, plugin cung cấp hooks/cleanup theo contract.

## 9. Tính tương thích phiên bản API và phụ thuộc riêng của plugin

API chung cần **binary compatibility** giữa host và mọi plugin đang deploy. Xóa method, đổi JVM descriptor hoặc constructor/record components có thể tạo `NoSuchMethodError`, `AbstractMethodError` hay lỗi linkage, dù cùng shared loader. Plugin riêng có thể dùng `codec v1/v2` ở loader khác nhau nếu không để `codec.*` lọt qua method signature của SPI; hãy map chúng sang shared DTO/schema tại boundary. Một plugin lỗi có thể gây exception, deadlock hoặc tiêu thụ tài nguyên, nên host phải có lỗi/timeout/quota policy; loader riêng chỉ hỗ trợ namespace và dependency isolation. Nếu có nhiều API versions không tương thích, cần migration/adapter hoặc hợp đồng version mới, không chỉ đổi JAR.

## 10. Quy trình debug plugin không hoạt động

### 10.1. Xác nhận đúng artifacts và loader

1. Host đã build **chỉ phụ thuộc shared API**, hay còn import implementation class?
2. API JAR/classes có ở **parent/runtime** của host không?
3. Plugin JAR có `plugin/impl/EchoPlugin.class` và provider descriptor `META-INF/services/plugin.api.Plugin` không?
4. API class có bị **đóng gói trùng** vào plugin implementation artifact không?
5. `URLClassLoader` hoặc custom loader nhận đúng URLs/resources của plugin chưa? Nested JAR không tự được nạp bằng standard `URLClassLoader` nếu không có launcher hiểu cấu trúc.
6. `Plugin.class.getClassLoader()` và `provider.getClass().getInterfaces()[0].getClassLoader()` có đúng quan hệ parent-shared không? (Chỉ dùng trực tiếp `getInterfaces()[0]` khi biết cấu trúc provider.)
7. `Plugin.class.isInstance(provider)` có true không?
8. ServiceLoader đang dùng overload tường minh hay TCCL? Plugin còn trên đúng loader lúc enumerate providers không?
9. API version và private dependency runtime có khớp artifact thực sự deploy không?
10. Plugin có stop hooks/resources/threads còn giữ loader cũ không?

### 10.2. Bảng lỗi và hướng kiểm tra

| Triệu chứng | Nguyên nhân đáng kiểm tra | Hướng xử lý |
|---|---|---|
| `ServiceLoader` trả 0 providers | Thiếu/sai descriptor, provider chưa ở runtime loader path | Kiểm tra `META-INF/services`, tên nhị phân, JAR layout, loader |
| `ServiceConfigurationError: not a subtype` | Provider implements **bản API khác loader** hoặc provider config sai | So identity `Plugin.class` từ host và interface từ provider |
| `ClassCastException: plugin.impl.EchoPlugin cannot be cast to plugin.api.Plugin` | Split API identity hoặc provider không implement interface thật | Bắt plugin API do parent định nghĩa; loại duplicate API |
| `ClassNotFoundException` trên implementation | Child loader không thấy JAR/classpath sai, binary name sai | Xem loader URLs, artifact và package names |
| `NoClassDefFoundError` khi gọi plugin | Thiếu private transitive dependency, lỗi initialization trước đó | Đọc `Caused by` và các class/library còn thiếu |
| `NoSuchMethodError`/`AbstractMethodError` | API/plugin binary version không tương thích | So compiled ABI và artifacts runtime |
| `IllegalAccessError`/`InaccessibleObjectException` | Java access/JPMS exports/opens | Kiểm tra package, defining loader, module rules |
| Một plugin đổi làm plugin khác hỏng | Loader shared không tách được dependency versions | Cân nhắc loader riêng cho mỗi plugin và shared API ổn định |
| Plugin cũ vẫn sống sau reload | Registry, TCCL, threads, ServiceLoader/cache giữ references | Stop, cleanup, close loader, loại bỏ references |
| Local IDE chạy tốt, deploy không thấy plugin | IDE classpath khác launcher/JAR distribution | Chạy artifact phát hành và kiểm tra command thực tế |
| Plugin class trùng tên với parent | Parent-first ưu tiên host class hoặc child-first tạo type duplicate | Xác định namespace/delegation policy rõ |

### 10.3. Một phép chẩn đoán nhanh bằng code

~~~java
static void inspectPlugin(Class<?> api, Object plugin) {
    Class<?> impl = plugin.getClass();
    System.out.println("api=" + api.getName());
    System.out.println("apiLoader=" + api.getClassLoader());
    System.out.println("impl=" + impl.getName());
    System.out.println("implLoader=" + impl.getClassLoader());
    System.out.println("apiAssignable=" + api.isInstance(plugin));
}
~~~

Đây là **method minh họa**, đặt trong một class thích hợp. `toString()` của ClassLoader hữu ích để debug nhưng không phải ID chuẩn; khi cần kiểm tra chính xác, so reference identity trực tiếp. Nếu ứng dụng có multiple module layers, in thêm `api.getModule()` và `impl.getModule()`.

## 11. Sai lầm kiến trúc phổ biến

Những lỗi đáng kiểm tra trước khi phát hành: host import trực tiếp implementation; plugin JAR gói bản shared API bị child-first tự định nghĩa; private library type xuất hiện trong public method signature; thiếu `META-INF/services` hoặc runtime plugin URLs; dùng nhầm TCCL khi discovery; không kiểm tra API binary compatibility; close loader trong khi plugin đang chạy; giữ listener/thread/caches sau stop; xem `ClassLoader` là sandbox bảo mật. Mỗi trường hợp cần xác minh **defining loader, artifact thực tế, service descriptor, runtime exception và lifecycle ownership**. Các bài chuyên sâu tiếp theo cung cấp thí nghiệm từng lỗi.

## 12. Câu hỏi tự kiểm tra và bài tập

1. Ba vai trò host, shared API, implementation cần tách theo nguyên tắc nào?
2. Vì sao host chỉ cần compile dependency tới interface `Plugin` chứ không cần `EchoPlugin`?
3. `Class<?>` identity bao gồm những yếu tố nào? Tại sao hai interfaces trùng binary name vẫn có thể khác nhau?
4. Vì sao shared API phải được nạp từ loader mà cả host/plugin cùng truy cập?
5. Với `URLClassLoader` parent-first, điều gì xảy ra khi child tìm `plugin.api.Plugin`?
6. Nếu plugin JAR cũng chứa `plugin/api/Plugin.class`, rủi ro gì xuất hiện với child-first loader?
7. `META-INF/services/plugin.api.Plugin` cần chứa nội dung gì?
8. `ServiceLoader.load(Plugin.class, pluginLoader)` và `ServiceLoader.load(Plugin.class)` khác nhau về loader selection ra sao?
9. Trong ví dụ, tại sao `hostCannotLoadPlugin=true` vẫn có thể có `pluginsFound=1`?
10. `sharedApiIdentity=true` chứng minh điều gì vượt quá phép so binary names?
11. Vì sao plugin JAR không cần manifest `Main-Class`?
12. Gradle `compileOnly` hoặc Maven `provided` phù hợp khi nào với API mà host cung cấp?
13. Nếu plugin A/B dùng versions khác nhau của codec, khi nào việc tạo hai plugin loaders riêng hữu ích?
14. Vì sao không nên để private codec class xuất hiện ở public method signatures của Plugin API?
15. Khi ServiceLoader báo `not a subtype`, cần kiểm tra classloader của interface nào?
16. Vì sao chỉ đóng `URLClassLoader` không đảm bảo plugin cũ được garbage-collected?
17. TCCL có tự đổi ClassLoader của class đã được define không? Khi nào ServiceLoader dùng TCCL?
18. Nếu muốn chuyển sang JPMS, những descriptor directives nào tương ứng API, host và implementation?
19. Nếu plugin là code không đáng tin, ClassLoader riêng có thay thế process isolation không?
20. Trong một ứng dụng plugin production, bạn cần thêm những quy tắc nào cho versioning, resource limits, shutdown và observability?

**Tiêu chí hoàn thành:** bạn tự biên dịch ba source sets Java 17+, đóng gói đúng provider JAR, chạy host và giải thích đủ bảy dòng stdout; vẽ được dependency graph và classloader hierarchy; chỉ ra cách giữ shared API identity, cấu hình `ServiceLoader`, tách private dependencies và quản lý plugin lifecycle.

## 13. Tài liệu tham khảo chính thức

- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, runtime loader semantics, `loadClass`.
- [Java SE 21 — URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): nạp class/resources từ JAR/directory URLs, đóng tài nguyên.
- [Java SE 21 — ServiceLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html): service/provider contract, configuration file, overload theo loader/layer, ServiceConfigurationError.
- [Java SE 21 — ModuleLayer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ModuleLayer.html): module layers và loader mapping cho JPMS.
- [JVMS Java SE 21 — Loading/Linking](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html): defining/initiating loaders, runtime identity và loading constraints.
- [Gradle Java Library Plugin](https://docs.gradle.org/current/userguide/java_library_plugin.html): API/implementation dependency separation khi thiết kế multi-project builds.
- [Maven Dependency Mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html): scopes như `compile`, `provided`, `runtime`, `test`.

**Kết luận:** plugin architecture không chỉ là “tạo một ClassLoader”. Kiến trúc hoạt động bền vững khi **host sở hữu hợp đồng chung**, **ClassLoader hierarchy thống nhất runtime identity của API**, còn **implementation và private dependencies** được đóng gói/nạp/lifecycle theo policy riêng. Ví dụ được kiểm chứng với Java 17-compatible bytecode; các biến thể framework/JPMS cần chọn API discovery và module/loader mapping phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-architecture-002">Chọn loader cho API chung và phụ thuộc riêng</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn ClassLoader cho API chung và phụ thuộc riêng trong kiến trúc plugin Java

Một hệ thống plugin muốn đạt hai mục tiêu có vẻ mâu thuẫn: **host và plugin cần hiểu cùng một hợp đồng giao tiếp**, nhưng **mỗi plugin có thể cần thư viện triển khai riêng, thậm chí khác phiên bản với host hoặc plugin khác**. Nếu đưa tất cả class vào một application classpath, version conflict có thể xảy ra. Nếu tạo một child-first loader cho mọi thứ, host và plugin có thể không còn sử dụng cùng runtime interface để giao tiếp.

Giải pháp kiến trúc thông dụng là phân chia **ownership theo loại class**:

- **Shared API** — interface SPI, DTO, exception, callback và các hợp đồng thật sự qua biên giới host/plugin: do **loader cha chung** định nghĩa; các plugin delegate khi cần class API.
- **Plugin implementation** — class thực thi nghiệp vụ chỉ plugin sử dụng: do **plugin loader riêng** định nghĩa.
- **Private dependencies** — thư viện mà một plugin sở hữu và muốn cô lập phiên bản: thường do **plugin loader** định nghĩa với chính sách tìm kiếm có chọn lọc; không vô tình dùng bản cùng tên của parent.
- **JDK/platform classes** — tiếp tục ủy quyền theo cơ chế phù hợp, không tự “nạp lại” `java.*`.

**Nguyên tắc ghi nhớ:** *Chia sẻ hợp đồng; cô lập chi tiết triển khai.* Lựa chọn loader đúng phải dựa trên **class identity**, **hợp đồng nhị phân**, **version thực được nạp**, **resource visibility**, **vòng đời plugin** và **nhu cầu tương tác**, không chỉ dựa vào sơ đồ cây các loader.

## 1. Vì sao kiến trúc plugin cần nhiều loader?

Trong Java, runtime identity của một class/interface là **cặp (binary name, defining class loader)**:

~~~text
Runtime type = (binary name, defining loader)

(api.Greeting, AppLoader)        -> shared interface
(api.Greeting, PluginLoaderA)    -> interface KHÁC nếu child tự define
(plugin.impl.Greeter, LoaderA)   -> implementation A
(plugin.impl.Greeter, LoaderB)   -> implementation B khác
~~~

Hai file `api/Greeting.class` giống từng byte, nhưng được hai defining loaders khác nhau định nghĩa thì vẫn là **hai runtime types khác nhau**. Điều này ảnh hưởng trực tiếp tới `instanceof`, cast, method signatures, reflection và khả năng truyền object giữa host/plugin.

### Sơ đồ khuyến nghị

~~~text
                   Bootstrap / Platform
                           |
                   Application loader
                  /        |          \
       api.Greeting  api.PluginContext  shared DTO
                           |
                  +--------+--------+
                  |                 |
            Plugin loader A   Plugin loader B
                  |                 |
       plugin.impl.Greeter   plugin.impl.Greeter
       deps.private.Json v1 deps.private.Json v2
       resources A          resources B
                  |                 |
                  +--------+--------+
                           |
                 host giao tiếp qua
                  cùng api.Greeting
~~~

Ở đây, hai plugin có thể tự định nghĩa class implementation với **cùng tên nhị phân**, miễn host không cố coi chúng là một runtime implementation type. Cả hai object đều có thể được ép về **cùng interface `api.Greeting`** do parent định nghĩa.

### Defining loader khác initiating loader

~~~java
Class<?> apiType = pluginLoader.loadClass("api.Greeting");
ClassLoader defining = apiType.getClassLoader();
~~~

Đây là mảnh minh họa với biến `pluginLoader` đã có. Nếu plugin loader ủy quyền cho parent, `defining` là **parent**, không phải `pluginLoader`. Loader nhận lời gọi `loadClass` không nhất thiết là loader định nghĩa class.

Khi debug, hỏi:

~~~java
System.out.println(apiType == api.Greeting.class);
System.out.println(apiType.getClassLoader()
        == api.Greeting.class.getClassLoader());
~~~

Hai giá trị cùng là `true` trong cấu hình API chung đúng. Nhưng kiểm tra riêng `getName()` **không đủ**, vì hai `Class<?>` khác loader vẫn có thể cùng tên.

## 2. Phân loại class trước khi chọn parent hoặc child

| Nhóm class | Ví dụ | Nơi thường nên định nghĩa | Lý do |
|---|---|---|---|
| API interface qua boundary | `api.Greeting`, `spi.PaymentProvider` | Parent chung | Giữ một runtime type để cast và invoke |
| Shared DTO, callback, exception | `api.Request`, `api.PluginException` | Parent chung | Method signature qua boundary cần cùng identity |
| Java/platform | `java.lang.String`, `java.sql.*` | Bootstrap/platform/parent phù hợp | Không định nghĩa lại Java platform types |
| Implementation của plugin | `plugin.impl.Greeter` | Plugin loader | Tách vòng đời và version |
| Private implementation dependency | `deps.format.Formatter` | Plugin loader nếu cần version riêng | Tránh host version che plugin version |
| Shared dependency được dùng trong API | `api.json.JsonNode` nếu type xuất hiện trong method public | Parent hoặc contract thống nhất | Tránh hai bản không tương thích trong signature |
| Logging/service facade chung | API của logging/metrics hoặc SPI | Thường parent | Tránh split API, tạo policy rõ cho adapter |
| Resources private | `plugin/config.txt`, template | Nguồn riêng plugin, lookup có chủ đích | Cấu hình không bị resource từ parent che khuất |

**Không quyết định chỉ theo tên JAR.** Một thư viện có thể gồm phần API thực sự chia sẻ và phần implementation riêng. Nếu các lớp của một package cần package-private access lẫn nhau, hãy cân nhắc giữ chúng trong **cùng defining loader**, vì JVM xác định runtime package từ **package name + defining loader**.

### Câu hỏi trước khi “để dependency ở child”

1. Type của thư viện đó có xuất hiện trong public method/field của SPI không?
2. Host có ép kiểu, reflection, deserialize hoặc gọi callback với object thuộc thư viện ấy không?
3. Nó có dựa vào `ServiceLoader`, `Class.forName` hoặc thread context class loader không?
4. Nó dùng native libraries, static singletons, global registries, thread pools hoặc log manager không?
5. Có hai version cùng tồn tại và thực sự cần cả hai không?
6. Library có dùng package-private/nestmate giữa các class mà bạn dự định chia loader không?
7. Nếu plugin redeploy, reference nào có thể giữ loader cũ sống?
8. Có cơ chế mô-đun JPMS hoặc framework launcher riêng không?

Nếu type phải đi qua boundary, thường phải đưa type đó vào một **shared contract** hoặc thay bằng định dạng trung lập (ví dụ message/schema) thay vì cho mỗi bên định nghĩa một bản riêng.

## 3. Parent-first: mặc định an toàn cho API chung nhưng có thể chọn sai dependency

`ClassLoader.loadClass(String, boolean)` mặc định thực hiện:

~~~text
Child.loadClass(name)
  -> findLoadedClass(name)
  -> parent.loadClass(name)  [nếu parent tìm được, dùng class parent]
  -> child.findClass(name)   [nếu parent không tìm được]
  -> resolve nếu được yêu cầu
~~~

Ưu điểm của **parent-first**:

- Giữ host/plugin dùng chung API, DTO và những lớp ở parent.
- Không vô tình định nghĩa lại `java.*` và shared API ở mỗi child.
- Dễ khớp với cách làm việc của `URLClassLoader` mặc định.
- Giảm số bản trùng class metadata và đơn giản hóa nhiều đường dependency.

Nhưng có một rủi ro:

~~~text
Host classpath:
  deps.format.Formatter v1
Plugin JAR:
  deps.format.Formatter v2
PluginLoader: parent-first
~~~

Nếu parent nhìn thấy `deps.format.Formatter` v1, plugin loader trả **v1** khi được yêu cầu nạp class cùng tên, dù JAR của plugin chứa **v2**.

Nếu plugin compile dựa vào method chỉ v2 mới có, JVM có thể báo `NoSuchMethodError` hoặc lỗi linkage khác ở runtime. “Class tìm được” không chứng minh **binary compatibility**. Vì vậy parent-first là lựa chọn tốt cho **API dùng chung**, nhưng không mặc nhiên tốt cho mọi **private dependency**.

## 4. Child-first có chọn lọc: chỉ áp dụng cho implementation/private dependency

Khi plugin cần phiên bản riêng của một thư viện cùng binary name, ta có thể thay chính sách tìm class **cho một tập namespace giới hạn**:

~~~text
Tên lớp                        Chính sách
------------------------------------------------
java.*                         mặc định / delegation
api.*                          parent-first
shared.contract.*              parent-first
plugin.impl.*                  child-first (nếu có nhu cầu)
deps.format.*                  child-first
mọi tên còn lại                parent-first
~~~

Cách triển khai **không** nên đơn giản là “mọi tên child-first”:

- Nếu `api.Greeting` nằm trong plugin JAR và child tự định nghĩa nó, host không thể cast provider về `api.Greeting` của host.
- Nếu plugin dùng các API framework phải chia sẻ, tải bản khác có thể phá method signatures, service registries hoặc phản xạ.
- JVM đặt ràng buộc đối với package `java.*`; custom loader không được tự định nghĩa core Java classes.
- Một thư viện private có thể gọi interface của host; khi đó phần API của thư viện phải được chia sẻ đúng ranh giới.

**Mục tiêu không phải “child-first nhiều nhất”, mà là “child-first đúng nơi cần đổi version”.**

### Tránh phân chia các class phụ thuộc package-private

Giả sử hai class `p.Accessor` và `p.Secret` dùng các thành viên package-private. Nếu chúng được định nghĩa bởi hai loaders khác nhau dù source có cùng `package p;`, chúng thuộc **hai runtime packages khác nhau**. Một lời gọi bytecode có thể phát sinh `IllegalAccessError`.

Vì vậy chính sách namespace nên được thiết kế theo **cụm package/nhóm thư viện nhất quán**, không theo vài class đơn lẻ bị tách ngẫu nhiên qua loaders.

## 5. Ví dụ Java 17+ đầy đủ: parent-first dùng v1, private dependency dùng v2

Thí nghiệm sau cho thấy:

- Host có `api.Greeting` và `deps.format.Formatter` phiên bản **v1** trên application classpath.
- Plugin chứa `plugin.impl.PluginGreeting` và một bản `deps.format.Formatter` phiên bản **v2** trong JAR riêng.
- Loader mặc định **parent-first** khiến plugin chạy với dependency **host-v1**.
- Loader **child-first chỉ cho `deps.format.*`** cho phép plugin dùng **plugin-v2**.
- Cả hai provider đều **cast thành công về một `api.Greeting` duy nhất** vì shared API được parent định nghĩa.
- Hai plugin loaders riêng biệt cho ra hai bản runtime class implementation/dependency khác nhau.
- Resource cùng tên có thể vẫn được lấy từ parent do resource lookup độc lập với policy tải class.

Không sử dụng Maven, Gradle hay thư viện bên ngoài. Mã biên dịch và chạy được trên **JDK 17+**.

### 5.1. Cấu trúc project

~~~text
project/
  src/
    api/api/Greeting.java
    v1/deps/format/Formatter.java
    v2/deps/format/Formatter.java
    plugin/plugin/impl/PluginGreeting.java
    host/LoaderBoundaryDemo.java
  out/
    api/
    lib-v1/
    lib-v2/
    plugin/
    host/
  dist/
    private-plugin.jar
~~~

Các thư mục `out` và `dist` được tạo trong bước build.

### 5.2. API chung: `src/api/api/Greeting.java`

~~~java
package api;

public interface Greeting {
    String message();
}
~~~

Đây là contract mà host biết từ lúc biên dịch. **Không copy `api/Greeting.class` vào JAR private plugin.** Plugin compile dựa vào bản API ở `out/api`, còn runtime plugin loader ủy quyền tìm `api.Greeting` về parent.

### 5.3. Formatter phiên bản host: `src/v1/deps/format/Formatter.java`

~~~java
package deps.format;

public final class Formatter {
    private Formatter() {
    }

    public static String identity() {
        return "host-v1";
    }
}
~~~

### 5.4. Formatter phiên bản riêng plugin: `src/v2/deps/format/Formatter.java`

~~~java
package deps.format;

public final class Formatter {
    private Formatter() {
    }

    public static String identity() {
        return "plugin-v2";
    }
}
~~~

Hai nguồn có cùng binary name `deps.format.Formatter` nhưng kết quả khác. Đây là một ví dụ tối giản cho **version conflict**, không phải lời khuyên đặt hai thư viện cùng tên trên một classpath.

### 5.5. Implementation: `src/plugin/plugin/impl/PluginGreeting.java`

~~~java
package plugin.impl;

import api.Greeting;
import deps.format.Formatter;

public final class PluginGreeting implements Greeting {
    public PluginGreeting() {
    }

    @Override
    public String message() {
        return Formatter.identity();
    }
}
~~~

File này sẽ được compile với Formatter **v2**, nhưng khi chạy với parent-first nó vẫn có thể nhận Formatter **v1**. Trong ví dụ hai version có cùng method signature nên chạy được với hai kết quả khác nhau. Nếu version thực tế không giữ binary compatibility, bạn có thể thấy lỗi linkage thay vì chỉ kết quả khác.

### 5.6. Host và custom loader: `src/host/LoaderBoundaryDemo.java`

~~~java
import api.Greeting;
import deps.format.Formatter;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class LoaderBoundaryDemo {

    static final class PolicyLoader extends URLClassLoader {
        private final boolean isolatePrivateDeps;

        PolicyLoader(String name, URL[] sources, ClassLoader parent,
                     boolean isolatePrivateDeps) {
            super(name, sources, parent);
            this.isolatePrivateDeps = isolatePrivateDeps;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {

            // Shared API, Java classes và mọi namespace khác:
            // dùng parent-first mặc định.
            if (!isolatePrivateDeps || !name.startsWith("deps.format.")) {
                return super.loadClass(name, resolve);
            }

            // Chỉ namespace private dependency được child-first.
            synchronized (getClassLoadingLock(name)) {
                Class<?> found = findLoadedClass(name);

                if (found == null) {
                    try {
                        found = findClass(name);
                    } catch (ClassNotFoundException absentInPlugin) {
                        found = super.loadClass(name, false);
                    }
                }

                if (resolve) {
                    resolveClass(found);
                }
                return found;
            }
        }
    }

    private static Greeting instantiate(PolicyLoader loader)
            throws Exception {
        Class<?> type = loader.loadClass(
                "plugin.impl.PluginGreeting");

        return (Greeting) type.getConstructor().newInstance();
    }

    private static String text(URL url) throws Exception {
        if (url == null) {
            return "missing";
        }
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(),
                    StandardCharsets.UTF_8).strip();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: LoaderBoundaryDemo <plugin-jar>");
        }

        URL jar = Path.of(args[0]).toAbsolutePath()
                .toUri().toURL();
        URL[] sources = {jar};
        ClassLoader parent =
                LoaderBoundaryDemo.class.getClassLoader();

        try (PolicyLoader parentFirst =
                    new PolicyLoader("parent-first", sources,
                            parent, false);
             PolicyLoader privateFirst =
                    new PolicyLoader("private-first", sources,
                            parent, true);
             PolicyLoader otherPrivate =
                    new PolicyLoader("private-2", sources,
                            parent, true)) {

            Greeting normal = instantiate(parentFirst);
            Greeting isolated = instantiate(privateFirst);

            Class<?> apiFromPlugin = privateFirst.loadClass(
                    "api.Greeting");
            Class<?> ownLib = privateFirst.loadClass(
                    "deps.format.Formatter");
            Class<?> parentLib = parentFirst.loadClass(
                    "deps.format.Formatter");

            System.out.println("host library: "
                    + Formatter.identity());
            System.out.println("parent-first plugin: "
                    + normal.message());
            System.out.println("private-first plugin: "
                    + isolated.message());

            System.out.println("same shared API: "
                    + (apiFromPlugin == Greeting.class));
            System.out.println("plugin cast works: "
                    + (isolated instanceof Greeting));
            System.out.println("parent-first library from parent: "
                    + (parentLib.getClassLoader() == parent));
            System.out.println("private library from child: "
                    + (ownLib.getClassLoader() == privateFirst));

            Class<?> otherLib = otherPrivate.loadClass(
                    "deps.format.Formatter");
            System.out.println("private library types equal: "
                    + (ownLib == otherLib));

            Class<?> otherImplementation = otherPrivate.loadClass(
                    "plugin.impl.PluginGreeting");
            System.out.println("implementation types equal: "
                    + (isolated.getClass() == otherImplementation));

            String resourceName = "plugin/config.txt";
            System.out.println("resource via loader: "
                    + text(privateFirst.getResource(resourceName)));
            System.out.println("resource from child URL: "
                    + text(privateFirst.findResource(resourceName)));
        }
    }
}
~~~

**Giải thích kỹ thuật:**

1. `PolicyLoader` kế thừa `URLClassLoader`. Loader này có danh sách URL chứa plugin JAR và parent là application loader.
2. Mặc định toàn bộ class names vẫn theo `super.loadClass`. Chỉ class bắt đầu bằng `deps.format.` được ưu tiên `findClass` của child.
3. Bên trong nhánh child-first, `getClassLoadingLock(name)` cùng `findLoadedClass(name)` giúp tránh tự định nghĩa lại cùng class name trong một loader. `resolveClass` xử lý khi `resolve=true`.
4. `api.Greeting` không thuộc nhánh child-first nên được parent định nghĩa và chia sẻ. Hai object plugin khác implementation loader vẫn tương thích API chung.
5. `plugin.impl.PluginGreeting` không nằm trong application classpath của host; cả parent-first lẫn private-first loaders tự định nghĩa bản plugin riêng.
6. `deps.format.Formatter` có ở host và plugin. Loader mặc định trả class của parent; private-first loader định nghĩa class từ plugin JAR.
7. `getResource` mặc định tiếp tục dùng resource delegation của ClassLoader. **Đổi `loadClass` không tự đổi `getResource`**.
8. `findResource` chỉ tra cứu các URL riêng của URLClassLoader; dùng để minh họa nơi chứa resource, không phải khuyến khích truy cập resource bằng API protected của mọi loader.

### 5.7. Lệnh biên dịch và đóng gói

Các lệnh sau dùng **Bash/Linux/macOS**, chạy từ thư mục `project`:

~~~bash
mkdir -p out/api out/lib-v1 out/lib-v2 out/plugin out/host dist
mkdir -p out/lib-v1/plugin out/plugin/plugin

javac -d out/api src/api/api/Greeting.java
javac -d out/lib-v1 src/v1/deps/format/Formatter.java
javac -d out/lib-v2 src/v2/deps/format/Formatter.java

javac -cp "out/api:out/lib-v2" -d out/plugin \
    src/plugin/plugin/impl/PluginGreeting.java

javac -cp "out/api:out/lib-v1" -d out/host \
    src/host/LoaderBoundaryDemo.java

printf 'host-resource\n' > out/lib-v1/plugin/config.txt
printf 'plugin-resource\n' > out/plugin/plugin/config.txt

jar --create --file dist/private-plugin.jar \
    -C out/plugin . -C out/lib-v2 .

java -cp "out/api:out/host:out/lib-v1" \
    LoaderBoundaryDemo dist/private-plugin.jar
~~~

Lưu ý:

- `out/api` là **API dùng chung**, xuất hiện trên classpath host.
- `out/lib-v1` là dependency host, vì thế application loader thấy Formatter v1 và resource `plugin/config.txt` của host.
- `out/lib-v2` là dependency riêng của plugin, chỉ dùng lúc biên dịch plugin và khi đóng gói vào `private-plugin.jar`; **không** đưa nó vào application classpath của lệnh chạy.
- `out/plugin` cũng **không** nằm trong application classpath. Plugin implementation chỉ đến từ URLClassLoader.
- JAR private chứa class implementation, thư viện v2 và resource plugin, nhưng **không chứa `api/Greeting.class`**.
- `javac -cp ...` là **compile-time classpath**. Nó không quyết định policy runtime của custom loader.

Sau khi build, các entry quan trọng bên trong `dist/private-plugin.jar` là:

~~~text
plugin/impl/PluginGreeting.class
deps/format/Formatter.class
plugin/config.txt
~~~

Có thể kiểm tra bằng:

~~~bash
jar --list --file dist/private-plugin.jar
~~~

Nếu `api/Greeting.class` xuất hiện trong plugin JAR do cách đóng gói nhầm, dù parent-first thường vẫn ưu tiên bản parent, việc **trùng API bytecode** là dấu hiệu cần sửa packaging để không tạo điều kiện cho một policy khác tự định nghĩa lại shared API.

### 5.8. Stdout và giải thích

**Stdout dự kiến trên Java 17+ khi các file đúng như trên:**

~~~text
host library: host-v1
parent-first plugin: host-v1
private-first plugin: plugin-v2
same shared API: true
plugin cast works: true
parent-first library from parent: true
private library from child: true
private library types equal: false
implementation types equal: false
resource via loader: host-resource
resource from child URL: plugin-resource
~~~

Đọc theo từng nhóm:

- `host library: host-v1`: app loader nạp `deps.format.Formatter` từ host output v1.
- `parent-first plugin: host-v1`: plugin class được loader riêng định nghĩa, nhưng tham chiếu `Formatter` được parent trả về v1.
- `private-first plugin: plugin-v2`: nhánh child-first cho `deps.format.*` buộc private dependency đến từ plugin JAR.
- `same shared API: true`: plugin loader cũng nhìn thấy đúng `api.Greeting.class` mà host sử dụng.
- `plugin cast works: true`: object plugin được tạo từ class riêng nhưng implements shared API do parent định nghĩa, nên host cast thành công.
- `parent-first library from parent: true` và `private library from child: true` minh chứng không nên dựa vào tên class để đoán defining loader.
- `private library types equal: false`: hai child loaders riêng định nghĩa hai runtime `deps.format.Formatter` dù đọc cùng plugin JAR.
- `implementation types equal: false`: class implementation từ hai plugin loader cũng khác identity.
- `resource via loader: host-resource`: `ClassLoader.getResource` mặc định hỏi parent trước, host đã có resource cùng tên.
- `resource from child URL: plugin-resource`: `URLClassLoader.findResource` tìm resource chỉ ở nguồn child.

**Điểm rất quan trọng:** class policy child-first có chọn lọc **không làm resource policy child-first tương ứng**. Nếu plugin gọi nhầm `getResource("plugin/config.txt")` và parent có một bản trùng, cấu hình của host có thể che cấu hình của plugin dù class `Formatter` đã được cô lập v2.

### 5.9. Windows PowerShell

Trên Windows, thay cách tạo thư mục/file bằng `New-Item` và `Set-Content`; lệnh `javac`, `jar` và `java` vẫn dùng tương tự. Dấu phân cách `-cp` là **`;`**, ví dụ:

~~~powershell
java -cp "out/api;out/host;out/lib-v1" LoaderBoundaryDemo dist/private-plugin.jar
~~~

Nếu dùng PowerShell để tạo resource, lưu UTF-8 đúng dữ liệu, không thêm ký tự không mong muốn; trong ví dụ, `String.strip()` bỏ ký tự xuống dòng của resource khi in.

## 6. Hợp đồng nhị phân và dependency leak qua public API

Chọn loader cho dependency không chỉ là lựa chọn nơi lưu JAR. Ví dụ interface:

~~~java
package api;

public interface ReportPlugin {
    deps.format.Document create();
}
~~~

Đây là **mảnh minh họa**; cần type `deps.format.Document` thực sự có thể nạp từ API/module tương ứng.

Ở đây `deps.format.Document` xuất hiện trong **public method signature**. Nếu mỗi plugin tự định nghĩa type `Document` khác nhau, host nhận các runtime types khác identity và có thể không gọi/cast/link đúng contract.

**Hướng thiết kế tốt hơn:**

~~~java
package api;

public interface ReportPlugin {
    api.DocumentDto create();
}
~~~

Trong mô hình này, `api.DocumentDto` được loader chung định nghĩa. Plugin có thể chuyển đổi từ object `deps.format.Document` private sang DTO chung trước khi trả về.

Khi plugin cần dùng một dependency type làm contract công khai, **dependency không còn thật sự private**. Bạn cần một trong những phương án:

1. Nâng dependency API type thành **shared dependency** được parent định nghĩa, thống nhất version/ABI.
2. Tạo **anti-corruption layer/adapter** chuyển dữ liệu từ private type sang DTO thuộc shared API.
3. Dùng dữ liệu trao đổi độc lập class identity (schema/message, JSON, protobuf phù hợp) nếu cần biên giới linh hoạt hoặc ngoài tiến trình.
4. Version contract một cách rõ ràng khi có thay đổi không tương thích.

Không nên đưa cả dependency v1 và v2 vào parent rồi hy vọng JVM chọn bản theo caller. Với một binary name và một defining loader, JVM không thể đồng thời định nghĩa hai version cùng tên trong chính loader đó.

## 7. Shading, relocation và trường hợp không cần custom loader

Không phải version conflict nào cũng buộc viết một `ClassLoader` child-first. Một lựa chọn là **shading/relocation**: build tool đổi binary package name của dependency private, ví dụ:

~~~text
deps.format.Formatter
        |
        v
pluginA.shadow.deps.format.Formatter
~~~

Khi đổi tên binary class thực sự và sửa bytecode/resource metadata tương ứng, parent-first có thể hoạt động mà không gặp xung đột tên class cũ. Nhưng việc relocation cần kiểm tra:

- Reflection bằng tên class dạng String.
- `META-INF/services` descriptors và provider names.
- Resources tham chiếu bằng đường dẫn package.
- Serialization metadata hoặc hardcoded names.
- Native libraries, JNI, service registries hay config resource mà library phụ thuộc.
- License/packaging và việc tích hợp với framework sử dụng package names cụ thể.

**Relocation không chỉ là đổi tên file JAR.** Nếu library dùng reflection/service metadata nhiều, relocation có thể phát sinh lỗi khác. Hãy cân nhắc giữa đơn giản hóa runtime loader và chi phí build/compatibility khi thực sự cần multi-version.

## 8. ServiceLoader, SPI và TCCL ở plugin boundary

`ServiceLoader` tiêu thụ **service type chung** từ parent và provider ở plugin loader. Với classpath SPI, plugin cần `META-INF/services/api.PluginService` chứa đúng binary provider name; host nên dùng overload tường minh `ServiceLoader.load(api.PluginService.class, pluginLoader)` khi đã biết plugin owner. Overload một tham số sử dụng TCCL của thread, vì vậy framework chạy trên worker khác có thể không tìm provider của plugin. Nếu phải đổi TCCL, lưu context của chính worker và restore trong `finally`. JPMS named modules dùng `uses`/`provides` và có thể dùng `ServiceLoader.load(layer, Service.class)`; đừng gộp cơ chế đó với loader URL classpath.

## 9. Chính sách resource không tự theo chính sách class

Resource lookup và class lookup là hai đường khác nhau:

| API | Cách tìm thường gặp |
|---|---|
| `loader.loadClass("deps.format.Formatter")` | Theo policy override của loader; có thể child-first |
| `loader.getResource("plugin/config.txt")` | Mặc định parent-first |
| `loader.getResources("plugin/config.txt")` | Có thể nhận nhiều URLs từ parent và child |
| `URLClassLoader.findResource("plugin/config.txt")` | Chỉ tìm nguồn URL riêng của loader |
| `SomeClass.class.getResource("config.txt")` | Tên tương đối với package của class |
| `Class.getResource("/plugin/config.txt")` | Tên tuyệt đối từ gốc, nhưng vẫn theo quy tắc module/loader liên quan |

Nếu parent và child đều chứa `plugin/config.txt`, resource từ parent có thể được chọn dù class logic đang dùng dependency từ child. Vì vậy:

- Đặt config private dưới namespace riêng, tránh path collisions.
- Truyền resource URL/stream tường minh nếu cần đúng nguồn.
- Với URLClassLoader, có thể dùng `findResource` khi thật sự muốn tìm trong child URLs và loader chưa bị đóng.
- Khi override resource lookup, cần cân nhắc đồng bộ `getResource` **và `getResources`**, không tạo hai hành vi mâu thuẫn.
- Với named modules JPMS, resource trong package của named module còn chịu quy tắc `opens`; API `Class`/`Module`/`ClassLoader` có điều kiện truy cập khác nhau.

**Không dùng** việc thử `getResource` thành công để kết luận class cùng tên cũng được cùng loader định nghĩa.

## 10. Binary compatibility và lỗi version thực tế

Có những tình huống quan trọng:

### 10.1. Parent-first chọn một version cũ

Plugin biên dịch dựa vào:

~~~java
Formatter.formatAdvanced(value);
~~~

nhưng parent chỉ có Formatter bản cũ không chứa method với descriptor phù hợp. Class loading có thể thành công, nhưng khi JVM resolve method lúc thực thi, `NoSuchMethodError` có thể xuất hiện.

**Cách điều tra:** kiểm tra `Formatter.class.getClassLoader()` của class thực tế, `CodeSource` nếu có, tên JAR/path, và method signatures của version dùng khi compile.

### 10.2. Child-first tải cả shared API

Plugin object implement interface có **cùng tên** với host nhưng defining loader khác. `ClassCastException` hoặc `ServiceConfigurationError` có thể xảy ra. Không sửa bằng một cast “mạnh hơn”; cần sửa **loader ownership**.

### 10.3. Package-private xuyên loader

Hai class cùng source package nhưng defining loaders khác nhau thuộc runtime packages khác. JVM có thể từ chối truy cập package-private bằng `IllegalAccessError`. Không giải quyết bằng `--add-opens` một cách mù quáng: module openness và runtime package identity là hai vấn đề khác nhau.

### 10.4. Host/plugin có hai phiên bản cùng một native library

Một số native libraries có hạn chế riêng về việc nạp native code từ nhiều class loaders trong cùng JVM. Classloader separation không tự tạo **hai native linker/process namespaces độc lập**. Nếu plugin dependency phụ thuộc JNI hoặc global native state, xem kỹ lifecycle và hỗ trợ của thư viện; có thể cần cách ly bằng process.

### 10.5. Duplicate classes trong cùng một plugin JAR

Nếu một plugin JAR đã gộp/shade sai và chứa nhiều bản cùng binary name, thứ tự entry/search có thể làm class được chọn không như dự định. Trước khi thay policy, kiểm tra artifact bằng `jar --list --file ...` và dependency tree/build pipeline.

## 11. Phân biệt “private” về version với “private” về bảo mật

Trong bài này, `private` chỉ có nghĩa **dependency không được chia sẻ phiên bản/namespace với host**. ClassLoader không tạo security sandbox: plugin cùng process vẫn có thể gọi các JDK API và tác động tới file, mạng, bộ nhớ trong quyền của process. Plugin không tin cậy cần OS/process/container isolation và boundary IPC phù hợp.

## 12. Vòng đời: có thể unload plugin đến đâu?

Mỗi plugin generation phải giữ loader mở khi còn cần lazy-load class/resource. Khi dừng, host chặn công việc mới, drain tasks, unregister callbacks, đóng resource, restore TCCL và cuối cùng close `URLClassLoader`. Class unloading phụ thuộc GC/reachability; `close()` không buộc dỡ class ngay. Thử riêng parent/shared libraries có static caches có thể giữ plugin objects sống.

## 13. Chẩn đoán: class nào do loader nào định nghĩa?

### Dùng `Class.getClassLoader()`

~~~java
Class<?> type = object.getClass(); // object khác null
ClassLoader defining = type.getClassLoader();

System.out.println("type=" + type.getName());
System.out.println("loader=" + defining);
System.out.println("module=" + type.getModule());
~~~

Đây là mảnh minh họa, không phải chương trình đầy đủ. Với core Java classes, `getClassLoader()` có thể trả `null` để biểu diễn bootstrap.

### Dùng CodeSource nếu khả dụng

~~~java
Class<?> type = object.getClass();
var domain = type.getProtectionDomain();
var source = domain == null ? null : domain.getCodeSource();

System.out.println(source == null
        ? "CodeSource unavailable"
        : source.getLocation());
~~~

Không phải mọi runtime class đều có CodeSource hữu ích; nó có thể `null`. Không dựa vào string representation của loader hoặc `identityHashCode` làm ID tuyệt đối; chúng không thay thế so object identity `==`.

### Những thông tin nên log

| Thông tin | Vì sao |
|---|---|
| Binary name | Tìm đúng class gây lỗi |
| Defining loader object/type | Phân biệt API parent, dependency child |
| Loader logical name/generation | Liên hệ plugin đang hoạt động |
| Parent chain | Hiểu delegation |
| CodeSource URL/JAR hash | Xác định bytecode thật |
| Policy branch theo package | Biết parent-first hay child-first đã áp dụng |
| Thread context loader | Điều tra ServiceLoader và frameworks |
| Resource URL | Điều tra file config bị parent che |
| Java module/layer | Điều tra JPMS readability/exports/opens |
| Full exception chain | Phân biệt load/link/initialize/cast/runtime |

Không log credentials hoặc toàn bộ content tài nguyên nhạy cảm chỉ để quan sát.

### Lệnh theo dõi JVM

Trên HotSpot/JDK 17+:

~~~bash
java -Xlog:class+load=info \
    -cp "out/api:out/host:out/lib-v1" \
    LoaderBoundaryDemo dist/private-plugin.jar
~~~

Đây là lệnh chẩn đoán, output phụ thuộc JDK và nhiều hơn stdout chuẩn của demo. Không lấy class-loading log làm thứ tự class loading chính thức cho mọi JVM implementation.

## 14. Thiết kế plugin theo nhu cầu: ba phương án

| Phương án | Khi phù hợp | Đánh đổi |
|---|---|---|
| **Một loader chung, parent-first** | Tất cả plugin dùng dependency compatible, ít nhu cầu version song song | Đơn giản nhưng version conflict giữa plugins |
| **Một loader cho mỗi plugin, selective child-first** | Mỗi plugin cần dependency riêng, API chung ổn định | Tăng số loaders/metaspace, cần policy/resource/lifecycle |
| **Tách process** | Plugin không tin cậy hoặc có native/global state xung đột | Cách ly mạnh hơn, nhưng cần IPC/serialization/operational tooling |

Có thêm lựa chọn **shading/relocation** nếu runtime child-first gây quá nhiều phức tạp; việc chọn phương án phải dựa trên dependency graph thật, method signatures và mô hình deployment.

**Không nhất thiết mỗi plugin phải có loader riêng.** Nếu plugins đáng tin, dùng chung API và dependencies tương thích, parent-first đơn giản có thể là lựa chọn tốt hơn. Ngược lại, “mỗi plugin một loader” chỉ đáng giá khi lợi ích version isolation và lifecycle lớn hơn overhead.

## 15. Checklist thiết kế trước khi phát hành

- [ ] Xác định tập shared API interfaces/DTOs/exceptions/callbacks.
- [ ] Các shared types được định nghĩa bởi **một parent loader** mà host và plugin dùng chung.
- [ ] Plugin JAR không vô tình gói bản sao API chung, hoặc tối thiểu policy chắc chắn không định nghĩa lại API ấy.
- [ ] Implementation plugin do child plugin loader định nghĩa.
- [ ] Private dependency chỉ được child-first ở namespace có chủ đích.
- [ ] Tất cả class trong cùng package dùng package-private đã được giữ đúng defining loader.
- [ ] Các method signatures qua boundary không rò rỉ private dependency types.
- [ ] Parent và plugin version được kiểm tra binary compatibility.
- [ ] Resource lookup được kiểm tra riêng với class lookup.
- [ ] SPI/ServiceLoader, TCCL và provider descriptors được cấu hình đúng.
- [ ] Có policy fail-fast nếu thiếu dependency hoặc bản thư viện không tương thích.
- [ ] Có kế hoạch stop/drain/close/unload cho mỗi plugin generation.
- [ ] Có log defining loader, CodeSource, resource URL và lỗi gốc.
- [ ] Có test ít nhất hai plugin có cùng binary implementation name nhưng khác defining loader.
- [ ] Có test duplicate resource name và parent-first/child-first lựa chọn đúng dữ liệu.
- [ ] Có test missing methods, class not found, duplicate shared API, class cast, hot reload.
- [ ] Nếu dùng JPMS, module layer/readability/exports/opens được đánh giá độc lập.

## 16. Các sai lầm thường gặp

**“Cứ đưa mọi thư viện vào parent để chia sẻ.”** Không đúng nếu các plugin cần nhiều phiên bản không tương thích cùng binary name.

**“Cứ child-first mọi thứ để cô lập.”** Không đúng nếu làm đôi shared API và khiến host không cast được implementation.

**“Cùng file `api/Greeting.class` là cùng interface.”** Không: JVM xét **defining loader**, không chỉ nội dung bytecode.

**“Loader được gọi `loadClass` chắc chắn là loader định nghĩa class.”** Không: parent delegation có thể trả parent type.

**“Chỉ cần plugin interface nằm ở parent, mọi thư viện implementation đều cô lập.”** Không: phải xét class loading policy thực tế và các dependency transitive.

**“Child-first cho class đồng nghĩa child-first cho resource.”** Không: resource delegation là đường riêng.

**“Dùng `Thread.currentThread().setContextClassLoader` một lần là xong.”** Không: phải restore khi request kết thúc, nhất là thread pools.

**“`URLClassLoader.close()` thay bytecode cũ thành mới.”** Không: class definitions đã có không bị thay bởi close; muốn thế hệ mới thường cần loader mới.

**“`--add-opens` tự giải quyết mọi ClassCastException.”** Không: module openness không làm hai runtime types khác loader trở thành một type.

**“Để framework vào plugin jar là framework sẽ dùng luôn provider/plugin class.”** Không: framework có thể dùng TCCL, ServiceLoader, system loader hoặc cache riêng.

**“Shading là đổi tên JAR.”** Không: phải thực hiện relocation class/resource names có hệ thống.

**“ClassLoader riêng là sandbox.”** Không: cần security boundary độc lập nếu không tin cậy plugin code.

## 17. Câu hỏi tự kiểm tra

**Câu 1.** Host và plugin trao đổi `api.Greeting`. Ai nên định nghĩa interface này?

**Đáp án:** Một loader cha chung mà host và plugin delegate tới, để cả hai dùng **cùng runtime interface**.

**Câu 2.** Host có `deps.format.Formatter` v1, plugin muốn v2. Parent-first sẽ trả bản nào nếu parent nhìn thấy v1?

**Đáp án:** v1. Muốn v2 riêng, cần child-first chọn lọc cho namespace phù hợp hoặc phương án như shading/relocation.

**Câu 3.** Plugin loader child-first tự định nghĩa `api.Greeting` giống bytecode của host. Host có cast được provider sang `api.Greeting` của host không?

**Đáp án:** Không được bảo đảm; đó là hai runtime interfaces khác nhau và cast có thể thất bại. Phải dùng shared API thật sự.

**Câu 4.** Cả host lẫn plugin có `plugin/config.txt`. Tại sao loader child-first cho `deps.format.*` vẫn có thể trả config host?

**Đáp án:** Vì `getResource` mặc định delegate parent trước; class loading policy override không thay resource lookup.

**Câu 5.** Vì sao không nên expose `deps.format.Document` private qua public method của SPI?

**Đáp án:** Vì host/plugin cần cùng runtime type cho signature ấy. Nếu mỗi plugin định nghĩa version riêng, boundary có thể không tương thích. Dùng DTO/API chung hoặc chuyển dữ liệu sang định dạng trung lập.

**Câu 6.** Hai plugin loaders cùng tự định nghĩa `plugin.impl.PluginGreeting` từ một JAR có tạo ra cùng runtime `Class<?>` không?

**Đáp án:** Không. Tên/bytecode giống nhau nhưng defining loaders khác nhau nên class identity khác.

**Câu 7.** `URLClassLoader.close()` có lập tức unload class của plugin không?

**Đáp án:** Không. Phải giải phóng references/lifecycle; JVM/GC quyết định class unloading.

**Câu 8.** Khi `ServiceLoader.load(SPI.class)` không tìm provider, cần kiểm tra gì?

**Đáp án:** TCCL của thread, `META-INF/services` hoặc `uses`/`provides`, visibility của loader, và việc provider implement **đúng runtime SPI type**.

## 18. Sơ đồ quyết định cuối cùng

~~~text
             Xây kiến trúc class loader cho plugin
                             |
                   Có API qua boundary?
                     /            \
                   Có              Không
                    |                 |
          đặt API tại parent    Cân nhắc đơn giản:
                    |            parent-first nếu đủ
         plugin private version riêng?
                     /          \
                   Không         Có
                     |            |
              parent-first    selective child-first
                     |        CHỈ private namespaces
                     |            |
                     +------+-----+
                            |
                 Kiểm tra type identity
                 + public method signatures
                            |
                 Kiểm tra resources/SPI/TCCL
                            |
                 Kiểm tra lifecycle/generation
                            |
                 Log defining loader/CodeSource
                            |
                   Integration + redeploy tests
~~~

**Kết luận:** Khi lựa chọn loader, hãy **đặt shared API ở nơi tất cả participants nhìn thấy đúng cùng runtime Class**, còn **private implementation/dependencies ở loader riêng nếu thật sự cần cô lập version**. Parent-first cung cấp mặc định ổn định cho contract; child-first có chọn lọc giải quyết xung đột triển khai; quan sát defining loader và source thực tế giúp chứng minh chính sách đã hoạt động đúng.

### Tài liệu chuẩn

- [Java SE 17 — ClassLoader: loadClass, getClassLoader và delegation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17 — URLClassLoader: findClass, getResource, findResource, close](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html)
- [JVMS 17 5.3 — Class/interface identity, defining and initiating loaders](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3)
- [Java SE 17 — ServiceLoader API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html)
- [Java Language Specification 13 — Binary compatibility](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-architecture-003">Mỗi plugin một loader hay nhiều plugin dùng chung loader</a>

<details>
<summary>Xem chi tiết</summary>

# Mỗi plugin một ClassLoader hay nhiều plugin dùng chung ClassLoader?

Khi thiết kế **kiến trúc plugin Java**, một quyết định quan trọng là: **mỗi plugin có một defining class loader riêng**, hay **nhiều plugin cùng được nạp qua một loader**? Cả hai mô hình đều có thể đúng. Khác biệt cốt lõi là **ranh giới namespace, khả năng dùng các phiên bản dependency khác nhau, chia sẻ kiểu và trạng thái, mức độ độc lập khi nâng cấp/unload, cách tìm resource và chi phí vận hành**.

**Quy tắc định hướng:** Nếu các plugin có vòng đời độc lập, cần các phiên bản dependency khác nhau hoặc có nguy cơ trùng binary class name, nên xem xét **mỗi plugin một loader**. Nếu các thành phần được phát hành và kiểm thử như một tập thống nhất, dùng cùng dependency versions và cần trao đổi trực tiếp nhiều class nội bộ, **một loader dùng chung** có thể đơn giản hơn. Trong cả hai trường hợp, hãy đưa **API/SPI giao tiếp chung** lên một loader cha hoặc một cơ chế module contract nhất quán.

Bài học này giả định Java 17+ và bàn về plugin chạy **trong cùng một JVM**. ClassLoader là công cụ tổ chức runtime classes và resource lookup; **không phải biện pháp sandbox để thực thi mã không đáng tin**.

## 1. Đầu tiên: “một loader” thay đổi điều gì?

Theo *Java Virtual Machine Specification (JVMS)*, danh tính của một runtime class/interface được xác định bởi:

```text
(binary name, defining class loader)
```

Ví dụ tên nhị phân `impl.Greeting`:

```text
Plugin A loader ──define──> impl.Greeting  (runtime type A)
Plugin B loader ──define──> impl.Greeting  (runtime type B)

Tên: giống nhau
Defining loader: khác nhau
Kết quả: hai runtime classes KHÁC NHAU
```

Nhưng nếu cả hai plugin được đưa vào **cùng một loader**:

```text
SharedLoader:
  plugin-a.jar chứa impl/Greeting.class
  plugin-b.jar chứa impl/Greeting.class
       |
       +--> SharedLoader.loadClass("impl.Greeting")
                      |
                      +--> một runtime class được loader đó chọn
```

Một defining loader **không thể định nghĩa hai runtime classes khác nhau có cùng binary name**. Nếu thư viện/JAR thứ hai cũng chứa `impl.Greeting`, code không tự động được dùng “bản dành cho plugin B”. Thường một bản sẽ được tìm trước theo classpath/URL search policy; yêu cầu lặp lại trả cùng `Class<?>` đã được loader ghi nhận.

Cần phân biệt **gọi `loadClass`** và **được loader định nghĩa**. Với parent-first delegation, child có thể gọi `loadClass("shared.api.Plugin")` nhưng nhận lớp do parent định nghĩa. Chỉ gọi loader con không chứng minh class thuộc riêng loader con.

### Ba khái niệm dễ nhầm

| Khái niệm | Danh tính/quy tắc | Hệ quả |
|---|---|---|
| **Runtime class** | Tên nhị phân + defining loader | Hai bản cùng tên ở loader khác nhau là hai kiểu khác nhau |
| **Runtime package** | Tên package + defining loader | Hai class cùng package name ở loader khác nhau không tự dùng chung package-private access |
| **Plugin instance** | Một object cụ thể được tạo từ class | Có thể có nhiều instances ngay cả khi chỉ có một class/loader |

**Một loader cho nhiều plugin** không đồng nghĩa “chỉ có một object plugin”. Các plugin vẫn có thể tạo nhiều instances. Nhưng chúng dùng chung runtime type nếu loader chỉ định nghĩa một class với cùng tên, và **static fields** của runtime class đó cũng được chia sẻ.

## 2. Mô hình A: mỗi plugin một loader

Một sơ đồ parent-first đơn giản:

```text
Host / Application ClassLoader
  ├── spi.Plugin                    (public interface)
  ├── spi.Context                   (shared DTO)
  └── host.PluginManager
       ^                  ^
       | parent           | parent
       |                  |
Plugin A URLClassLoader   Plugin B URLClassLoader
  ├── impl.Greeting         ├── impl.Greeting
  ├── acme.lib.Helper v1    ├── acme.lib.Helper v2
  ├── a.properties          └── b.properties
  └── META-INF/services/... 
```

Mỗi plugin loader có danh sách URLs hoặc một nguồn bytecode riêng. Nó giữ **namespace định nghĩa** riêng cho những classes parent không cung cấp.

**Ưu điểm:**

1. Hai plugin có thể có những class **cùng binary name** với định nghĩa độc lập (nếu chúng thật sự do loader riêng định nghĩa).
2. Các phiên bản thư viện private có thể cùng tồn tại: `acme.lib.Helper` v1 và v2 là các runtime types khác nhau.
3. Có thể phân biệt, giám sát và quản lý vòng đời plugin theo từng loader.
4. Dễ thiết kế việc **thay loader mới** khi cập nhật riêng một plugin mà không buộc mọi plugin khác phải cùng thế hệ.
5. Resource lookup có thể được gắn với loader cụ thể, giảm va chạm tên config/template.

**Chi phí/rủi ro:**

- Cùng một thư viện private có thể bị nạp **lặp lại** ở nhiều loaders; metadata, bytecode representation và static state không tự được chia sẻ.
- Object của một private type do loader A định nghĩa không cast trực tiếp thành type cùng tên do loader B định nghĩa.
- Giao tiếp A↔B khó hơn nếu các phương thức nhận/trả class private; cần chia sẻ contract/DTO ổn định.
- TCCL, ServiceLoader, resource lookup và caching có thể khiến code vô tình lấy provider/resource sai plugin.
- Unload không tự động: thread, static cache ở host, event listeners, JDBC drivers, logging, MBeans, executors và reference tới Class/instance có thể giữ loader sống.

**Điểm tinh tế về parent-first:** Hai `URLClassLoader` con với cùng parent **chưa bảo đảm phiên bản thư viện khác nhau** nếu parent đã có phiên bản thư viện đó. Nếu parent chứa `acme.lib.Helper`, cả hai child mặc định có thể ủy quyền parent và nhận **một** phiên bản của parent. Muốn isolation thật, phải định nghĩa rõ **shared classes ở parent** và **private classes chỉ thuộc plugin loaders**, hoặc dùng policy child-first có kiểm soát.

## 3. Mô hình B: nhiều plugin dùng chung một loader

```text
Host / Application ClassLoader
  └── spi.Plugin
        ^
        | parent
        |
One Shared URLClassLoader
  ├── plugin-a.jar
  ├── plugin-b.jar
  ├── common-helper.jar
  └── resource descriptors của cả nhóm
```

Một `URLClassLoader` chung được tạo với URLs của nhiều plugin và dependencies. Mọi class do loader này **thực sự định nghĩa** dùng cùng loader identity.

**Ưu điểm:**

1. Ít boundaries class-loader hơn, nên các classes cùng loader dễ dùng chung **package-private access** khi cùng runtime package (vẫn phải tuân thủ các quy tắc Java khác).
2. Một phiên bản dependency chung có thể được nạp và khởi tạo **một lần cho toàn nhóm**, tránh duplication.
3. Những plugin được phát hành như một bộ đồng phiên bản dễ gọi class của nhau khi tất cả classes đều nằm trong một namespace.
4. Resource scanning từ một loader chung có thể đơn giản khi intentionally tổng hợp descriptors từ toàn nhóm.
5. Giảm độ phức tạp trong module/class identity khi plugin thực chất chỉ là “các tính năng cùng ứng dụng” chứ không có nhu cầu isolation.

**Chi phí/rủi ro:**

- Hai JAR có cùng binary class name tạo **xung đột**: không thể chọn đồng thời hai definitions riêng trong cùng defining loader.
- Một plugin muốn `foo.Bar v1` còn plugin khác muốn `foo.Bar v2` có thể gặp `NoSuchMethodError`, `NoClassDefFoundError`, sai hành vi hoặc lệch binary compatibility.
- Những **static fields/singletons** thuộc class chung được chia sẻ giữa các plugin; dễ rò trạng thái hoặc tạo coupling ngoài ý muốn.
- Khó thay thế chỉ một plugin implementation với **cùng binary name** bằng bytecode mới trong cùng loader: JVM không có thao tác “định nghĩa lại bằng `defineClass`” đơn giản.
- Loader chung chỉ có thể được collectible khi toàn bộ classes/instances/references của nhóm đã hết được sử dụng theo điều kiện JVM. Một plugin còn hoạt động có thể khiến cả nhóm khó unload.
- Thứ tự các URLs/JARs và resources trùng tên ảnh hưởng kết quả; “plugin B có JAR riêng” không đồng nghĩa B luôn dùng class trong JAR ấy.

Dùng chung loader có thể phù hợp với một **bộ plugin tích hợp do cùng team phát hành**, được kiểm thử chung và triển khai/nâng cấp đồng bộ. Đừng chọn chỉ vì “ít ClassLoader objects hơn” mà bỏ qua yêu cầu versioning và lifecycle.

## 4. Bảng đối chiếu lựa chọn

| Tiêu chí | Mỗi plugin một loader | Một loader cho nhiều plugin |
|---|---|---|
| **Class identity** | Mỗi child có thể định nghĩa private classes riêng | Một binary name chỉ có một definition trong loader chung |
| **Dependency trùng tên khác version** | Có thể cách ly nếu parent không chiếm namespace đó | Thường xung đột; phải thống nhất version hoặc relocate |
| **API giữa host và plugins** | Phải chia sẻ contract ở parent/loader chung | Vẫn nên chia sẻ contract ổn định ở parent |
| **Plugin A gọi private class của B** | Phức tạp; cần contract/loader visibility thích hợp | Thường dễ hơn khi class visible trong cùng loader |
| **Package-private giữa plugins** | Khác runtime package nếu defining loader khác | Có thể cùng runtime package khi package name trùng |
| **Static state của private dependencies** | Riêng theo runtime class/loader | Dùng chung nếu cùng runtime class |
| **Resource trùng tên** | Có namespace lookup theo loader, nhưng delegation vẫn ảnh hưởng | Dễ shadowing, cần chính sách precedence/aggregation |
| **Thay một plugin** | Có thể tạo loader mới riêng, phải cleanup đúng | Thường cần loader mới cho cả nhóm nếu đổi class đã định nghĩa |
| **Unloading** | Có thể cleanup theo plugin, không bảo đảm tức thời | Một tham chiếu còn sống có thể giữ namespace cả nhóm |
| **Memory/metadata** | Có nguy cơ lặp class metadata, caches, static state | Có thể tiết kiệm nếu dùng chung classes |
| **Bảo mật mã không tin cậy** | Không phải sandbox | Cũng không phải sandbox |
| **Độ đơn giản code ban đầu** | Nhiều loader/lifecycle hơn | Ít loader hơn, nhưng cần kiểm soát conflict |

Không nên biến bảng thành quy tắc tuyệt đối. Chính sách delegation, module layers, cách triển khai JAR, container/framework và class loader implementation có thể thay đổi chi tiết. Phải đo/kiểm chứng theo sản phẩm thực tế.

## 5. Demo Java 17+: hai plugin trùng tên class, thử cả hai mô hình

Ví dụ cho phép quan sát chính xác **tác động của defining loader**. Hai plugin có cùng class **`impl.Greeting`**, cùng interface `spi.Plugin` nhưng trả kết quả khác. Ta sẽ thử:

- Một `URLClassLoader` chỉ chứa plugin A.
- Một `URLClassLoader` chỉ chứa plugin B.
- Một `URLClassLoader` chung chứa **cả A và B**, theo thứ tự URL A rồi B.

Host chỉ có **SPI và mã ứng dụng** trên classpath. Nó **không** có plugin implementations, nên parent-first delegation không làm mất isolation của hai plugins.

Cấu trúc nguồn/output:

```text
plugin-demo/
├── src/
│   ├── spi/Plugin.java
│   ├── app/PluginLoaderDemo.java
│   ├── alpha/impl/Greeting.java
│   └── beta/impl/Greeting.java
└── out/
    ├── host/
    │   ├── spi/Plugin.class
    │   └── app/PluginLoaderDemo.class
    ├── alpha/
    │   ├── impl/Greeting.class
    │   └── plugin.properties
    └── beta/
        ├── impl/Greeting.class
        └── plugin.properties
```

### 5.1. Shared SPI

**`src/spi/Plugin.java`:**

```java
package spi;

public interface Plugin {
    String id();
}
```

Class/interface này phải được host định nghĩa. Hai plugin cùng `implements spi.Plugin` và do parent delegation dùng đúng runtime interface của host, không tự đóng gói một bản khác của API.

### 5.2. Plugin A

**`src/alpha/impl/Greeting.java`:**

```java
package impl;

import spi.Plugin;

public class Greeting implements Plugin {
    @Override
    public String id() {
        return "alpha";
    }
}
```

### 5.3. Plugin B

**`src/beta/impl/Greeting.java`:**

```java
package impl;

import spi.Plugin;

public class Greeting implements Plugin {
    @Override
    public String id() {
        return "beta";
    }
}
```

Hai source files có **cùng package/name** nhưng khác nội dung. Chúng được biên dịch vào **hai output directories riêng**. Nếu đặt cả hai cùng compiler output và cùng classpath không kiểm soát, ví dụ có thể bị đè class file hoặc không cho thấy đúng bài toán.

### 5.4. Host runner

**`src/app/PluginLoaderDemo.java`:**

```java
package app;

import spi.Plugin;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public final class PluginLoaderDemo {
    private static String id(Class<?> type) throws Exception {
        Plugin plugin = (Plugin) type
                .getDeclaredConstructor().newInstance();
        return plugin.id();
    }

    public static void main(String[] args) throws Exception {
        ClassLoader host = PluginLoaderDemo.class.getClassLoader();

        URL alpha = Path.of(args[0]).toUri().toURL();
        URL beta = Path.of(args[1]).toUri().toURL();

        try (URLClassLoader a =
                     new URLClassLoader(new URL[] {alpha}, host);
             URLClassLoader b =
                     new URLClassLoader(new URL[] {beta}, host);
             URLClassLoader shared =
                     new URLClassLoader(new URL[] {alpha, beta}, host)) {

            Class<?> typeA = a.loadClass("impl.Greeting");
            Class<?> typeB = b.loadClass("impl.Greeting");

            System.out.println("Different implementation types: "
                    + (typeA != typeB));
            System.out.println("Loader A owns A: "
                    + (typeA.getClassLoader() == a));
            System.out.println("Loader B owns B: "
                    + (typeB.getClassLoader() == b));
            System.out.println("Shared API type: "
                    + (a.loadClass("spi.Plugin")
                        == b.loadClass("spi.Plugin")));
            System.out.println("A: " + id(typeA));
            System.out.println("B: " + id(typeB));

            Class<?> sharedType =
                    shared.loadClass("impl.Greeting");
            System.out.println("Shared loader first: "
                    + id(sharedType));
            System.out.println("Shared class reused: "
                    + (sharedType
                        == shared.loadClass("impl.Greeting")));

            Enumeration<URL> urls =
                    shared.getResources("plugin.properties");
            List<String> found = new ArrayList<>();

            while (urls.hasMoreElements()) {
                try (InputStream in =
                        urls.nextElement().openStream()) {
                    found.add(new String(in.readAllBytes(),
                            StandardCharsets.UTF_8).trim());
                }
            }
            System.out.println("Shared resources: "
                    + String.join(",", found));
        }
    }
}
```

Lớp runner sử dụng **Java 17 syntax/APIs**. `URLClassLoader` là một `Closeable`, vì vậy `try-with-resources` giải phóng các tài nguyên loader quản lý khi code kết thúc. Nhưng `close()` **không ép class unloading ngay lập tức** và không nên đóng loader khi plugin còn phải load lớp/tài nguyên mới.

`id(Class<?>)` dùng reflection tạo instance và cast sang interface `spi.Plugin` của host. Vì cả A và B đều delegate interface này cho parent, cast thành công. Nếu chúng tự định nghĩa `spi.Plugin` riêng, có thể gặp lỗi identity/cast hoặc linkage.

### 5.5. Biên dịch và chạy

Dùng **Bash** từ thư mục `plugin-demo`:

```bash
mkdir -p out/host out/alpha out/beta

javac --release 17 -d out/host \
  src/spi/Plugin.java src/app/PluginLoaderDemo.java

javac --release 17 -cp out/host -d out/alpha \
  src/alpha/impl/Greeting.java

javac --release 17 -cp out/host -d out/beta \
  src/beta/impl/Greeting.java

printf 'alpha\n' > out/alpha/plugin.properties
printf 'beta\n' > out/beta/plugin.properties

java -cp out/host app.PluginLoaderDemo out/alpha out/beta
```

Trên **Windows PowerShell**, chạy tương đương:

```powershell
New-Item -ItemType Directory -Force -Path out/host, out/alpha, out/beta | Out-Null
javac --release 17 -d out/host src/spi/Plugin.java src/app/PluginLoaderDemo.java
javac --release 17 -cp out/host -d out/alpha src/alpha/impl/Greeting.java
javac --release 17 -cp out/host -d out/beta src/beta/impl/Greeting.java

[System.IO.File]::WriteAllText(
    "out/alpha/plugin.properties", "alpha",
    [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText(
    "out/beta/plugin.properties", "beta",
    [System.Text.UTF8Encoding]::new($false))

java -cp out/host app.PluginLoaderDemo out/alpha out/beta
```

Trong PowerShell, cách khởi tạo `UTF8Encoding($false)` giúp tạo resource **không có BOM**. Trên Bash, `printf` tạo nội dung UTF-8 bình thường cho ví dụ ASCII này. Đường dẫn `out/alpha` và `out/beta` được truyền dưới dạng tham số chương trình; `Path.of(...).toUri().toURL()` dựng URLs của hai thư mục.

**Đầu ra xác nhận bằng `javac --release 17` và JDK 24:**

```text
Different implementation types: true
Loader A owns A: true
Loader B owns B: true
Shared API type: true
A: alpha
B: beta
Shared loader first: alpha
Shared class reused: true
Shared resources: alpha,beta
```

### 5.6. Phân tích từng kết quả

**`Different implementation types: true`**

Loader A và B cùng gọi `loadClass("impl.Greeting")`, nhưng parent không có class này. A tự tìm/định nghĩa bản trong `out/alpha`, B tìm/định nghĩa bản trong `out/beta`. `typeA != typeB` vì runtime identity khác defining loader.

**`Loader A owns A` và `Loader B owns B`**

`getClassLoader()` xác nhận **defining loader** chính là loader plugin tương ứng. Nếu parent vô tình chứa `impl.Greeting`, hai dòng này có thể không còn đúng: phải kiểm tra tình huống đó khi thực hiện isolation.

**`Shared API type: true`**

`spi.Plugin` chỉ có một bản ở `out/host`. Parent-first delegation khiến A và B nhận cùng đối tượng `Class<?>` của interface. Đây là cơ sở để host gọi `Plugin.id()` với instance từ cả hai loader.

**`A: alpha` và `B: beta`**

Hai plugin trả đúng behavior riêng mặc dù implementation có **cùng binary name**.

**`Shared loader first: alpha`**

Loader chung có URLs theo thứ tự `alpha` rồi `beta`. Cả hai thư mục đều có `impl/Greeting.class`. Với cấu hình `URLClassLoader` trong demo, class được tìm trước là **bản alpha**. Việc có thêm bản beta không tạo ra class thứ hai cùng tên trong loader chung.

**`Shared class reused: true`**

Lời gọi `shared.loadClass("impl.Greeting")` lần sau trả đúng `Class<?>` đã được nạp bởi shared loader, không tự chọn bản beta.

**`Shared resources: alpha,beta`**

`getResources("plugin.properties")` **liệt kê nhiều URL**. Một loader có thể có **hai resources cùng tên** trong các nguồn URLs, dù nó chỉ định nghĩa **một class cùng binary name**. Vì vậy không được nhầm **resource enumeration** với **class definition selection**.

**Giới hạn ví dụ:** Đây là case parent-first với directories trên classpath JDK chuẩn, không có custom child-first policy, named modules hay framework-specific nested JAR URLs. `URLClassLoader` có quy tắc riêng về class/resource URLs; các container khác có thể có policies và lifecycle khác nhau.

## 6. Chia sẻ API: điều kiện quan trọng của cả hai mô hình

Một ứng dụng plugin nên phân rõ:

```text
shared.api.*          -> hợp đồng host/plugins giao tiếp
plugin.impl.*         -> code triển khai riêng
plugin.private.libs.* -> dependencies nội bộ tùy plugin
```

Ví dụ SPI:

```java
package shared.api;

public interface Plugin {
    String name();
    void start(PluginContext context);
    void stop();
}
```

Đây là **interface minh họa**, không đủ để chạy độc lập vì cần `PluginContext` phù hợp. Điểm quan trọng là **class identity** của `Plugin` và `PluginContext` phải đồng nhất ở những nơi trao đổi object.

Khi dùng **mỗi plugin một loader**, public SPI/DTO và interfaces được host định nghĩa là cách dễ hiểu nhất để bảo đảm cùng runtime types. Plugin phải **delegate** namespace API lên parent, hoặc hệ thống phải thiết kế module/layer contract tương đương.

Nếu plugin loader A tự định nghĩa `shared.api.Plugin` nhưng host có một bản `shared.api.Plugin` khác, câu lệnh như:

```java
Plugin plugin = (Plugin) instanceFromPlugin;
```

có thể ném `ClassCastException`, ngay cả khi cả hai `Plugin` cùng package name và bytecode tương tự. **Tên giống nhau không làm classes từ hai defining loaders trở thành một Java type.**

Với **một loader chung**, nhiều implementation types có thể tham chiếu lẫn nhau dễ hơn, nhưng shared API với host vẫn nên được phân định rõ để tránh phiên bản contract bị “shadow” bởi JAR plugin.

### Cẩn thận với parameter/return types

Giả sử API host:

```java
public interface Processor {
    shared.api.Result process(shared.api.Input input);
}
```

Đây là đoạn minh họa (cần có hai classes trong package `shared.api`). Nếu những method signatures thay bằng `pluginA.internal.Result` mà plugin B/host không nạp được hoặc nạp từ loader khác, code có thể gặp `NoClassDefFoundError`, lỗi access hoặc `LinkageError`. Giữ **cross-plugin boundary** chỉ trên public shared contracts ổn định, tránh để internal implementation types lọt vào chữ ký API chung.

## 7. Dependency conflict: khác tên plugin chưa đủ cách ly

Một plugin A dùng `org.example.json.Parser v1`, plugin B dùng `org.example.json.Parser v2`:

**Hai loaders riêng:**

```text
host (không chứa Parser)
   ├── loader A -> org.example.json.Parser v1
   └── loader B -> org.example.json.Parser v2
```

Nếu cả A và B thực sự tự định nghĩa `Parser`, hai phiên bản có thể cùng tồn tại. Nhưng object của bản v1 không tự tương thích với kiểu bản v2 nếu trao đổi trực tiếp qua boundary. Dùng shared DTO/serialized payload hoặc adapter có contract rõ.

**Một loader chung:**

```text
shared loader:
    plugin-a.jar -> Parser v1
    plugin-b.jar -> Parser v2

loader.loadClass("org.example.json.Parser")
    -> một trong hai definitions theo policy lookup
```

Không thể có hai runtime classes `org.example.json.Parser` v1 và v2 **cùng được shared loader định nghĩa dưới cùng binary name**. Việc chuyển thứ tự JAR có thể đảo lỗi, không giải quyết yêu cầu phiên bản đồng thời.

### Có thể dùng shading/relocation thay isolation?

Trong một số dự án, dependency được **relocate** thành binary names khác nhau khi build:

```text
pluginA.shadow.json.Parser
pluginB.shadow.json.Parser
```

Khi đó một loader chung có thể định nghĩa hai classes vì **tên khác nhau**. Tuy nhiên shading cần xử lý reflective names, `META-INF/services`, resources, native code và các references liên quan. Đây là **chiến lược build** khác, không phải plugin class-loader isolation mặc định.

### Điểm thất bại phổ biến của “hai loader riêng”

Nếu host loader có dependency phiên bản v1 và cả A/B loader đều **parent-first**, loader B có thể luôn nhận Parser v1 từ parent, dù URL của B chứa v2. Để thực sự cho B dùng v2, cần thiết kế:

- Không đưa private dependency cần đa phiên bản vào parent/shared loader.
- Chỉ đưa **contracts** thật sự chung lên parent.
- Dùng loader strategy/container hỗ trợ plugin-private namespace khi cần.
- Có tests xác nhận `Parser.class.getClassLoader()` và behavior/version thực tế.

Một loader riêng là **điều kiện cho isolation**, không tự động bảo đảm isolation nếu class loading vẫn ủy quyền phiên bản không mong muốn từ parent.

## 8. Static fields, singleton và trạng thái plugin

Static fields thuộc **runtime class**, không thuộc “chuỗi tên class” toàn JVM.

Nếu hai loaders định nghĩa hai bản `plugin.State`:

```text
(loader A, plugin.State).counter = 10
(loader B, plugin.State).counter = 20
```

mỗi bản có **static state riêng**. Đây có thể là điều mong muốn cho cache/config của plugin, hoặc có thể gây nhầm khi tác giả nghĩ rằng `State.INSTANCE` là singleton toàn JVM.

Nếu A và B dùng **một defining loader chung** cho `plugin.State`, static field sẽ được chia sẻ cho cả hai. Khi đó code cần thiết kế synchronization, tenant isolation và lifecycle cho state chung.

Một lưu ý quan trọng: **statics trong class do parent định nghĩa được chia sẻ bất kể plugin loaders có riêng hay không**. Nếu thư viện logging/global registry hoặc shared API giữ static cache ở parent, cả A và B vẫn có thể tác động tới cùng state đó.

### Không thể gỡ “static singleton cũ” chỉ bằng cách thay JAR

Nếu đã định nghĩa `plugin.State` trong loader và nó đã được sử dụng, thay bytes trên disk **không làm định nghĩa runtime class cũ thay đổi**. Với per-plugin loader có thể tạo loader **mới** cho thế hệ plugin mới, nhưng phải dọn references tới loader cũ. Với shared loader, thay một plugin cùng tên thường đòi tạo **loader mới cho nhóm**, ảnh hưởng các plugin còn lại.

## 9. Resource, cấu hình và ServiceLoader

Plugin JARs không chỉ chứa bytecode:

```text
plugin-a.jar:
  impl/Greeting.class
  plugin.properties
  META-INF/services/spi.Plugin

plugin-b.jar:
  impl/Greeting.class
  plugin.properties
  META-INF/services/spi.Plugin
```

**Khi mỗi plugin có loader riêng:** code có thể dùng loader A để lấy `plugin.properties` của A, loader B cho B. Nhưng **parent delegation** của `getResource` mặc định thường ưu tiên resource ở parent, nếu có cùng tên. Nếu plugin cần child-first resources, phải kiểm soát đúng lookup policy hoặc đặt tên resources theo plugin để tránh collisions.

**Khi dùng loader chung:** `getResource("plugin.properties")` thường trả **một URL** đầu tiên, còn `getResources("plugin.properties")` có thể trả **nhiều URL**. Đừng thiết kế plugin B đọc “file của B” bằng `getResource` chung rồi cho rằng loader biết plugin nào gọi.

### SPI và ServiceLoader

Với classpath SPI, provider descriptor:

```text
META-INF/services/spi.Plugin
```

chứa binary names của provider implementations, ví dụ:

```text
plugins.alpha.AlphaPlugin
plugins.beta.BetaPlugin
```

Đây là ví dụ metadata, không phải code runnable của demo phần 5; trong demo phần 5 có **hai implementations cùng binary name `impl.Greeting`** để chứng minh class collision.

`ServiceLoader.load(Plugin.class, pluginLoader)` dùng **loader được truyền**. `ServiceLoader.load(Plugin.class)` dùng **thread context class loader (TCCL)** theo Java SE 17 API. Điều này ảnh hưởng cách plugin manager chọn tập providers.

- **Mỗi plugin một loader:** load service providers qua đúng loader tương ứng, xác minh SPI type được parent định nghĩa và descriptor có trong plugin.
- **Loader chung:** service discovery có thể tổng hợp nhiều providers; phải phân biệt providers theo metadata/ID, không phụ thuộc tuyệt đối vào thứ tự nếu contract không đảm bảo.
- Nếu hai descriptors đều trỏ tới **cùng provider binary name** nhưng chỉ có **một shared loader**, tên được resolve trong namespace đó, không tạo ra hai provider implementations tương ứng hai JARs.
- Với named modules, `ServiceLoader` còn hỗ trợ `provides ... with ...` trong `module-info.java` và discovery theo `ModuleLayer`; không chỉ dựa trên `META-INF/services`.

## 10. TCCL, thread pools và async tasks

TCCL thuộc **thread**, được `ServiceLoader.load(serviceType)` và một số frameworks sử dụng cho discovery; nó không đổi defining loader của class hay tự truyền vào worker pool đã tồn tại. Trong callback/task của plugin, nếu cần TCCL thì set đúng plugin loader **trên worker thực thi** và khôi phục context cũ trong `finally`. Ưu tiên `ServiceLoader.load(serviceType, pluginLoader)` khi có owner tường minh. Quên restore gây tìm nhầm providers và giữ loader cũ sau reload.

## 11. Reload/unload: một loader riêng không bảo đảm GC

Loader riêng giúp thay **một plugin generation** độc lập, nhưng chỉ sau khi dừng nhận công việc mới, drain requests/tasks, unregister listeners, đóng tài nguyên và bỏ host references. Tạo **loader mới** cho cùng binary names; không dùng `close()` hoặc thay JAR để redefine class trong loader cũ. Loader chung gắn vòng đời cả nhóm: muốn thay riêng một class đã được định nghĩa thường phải tạo namespace/loader mới cho nhóm hoặc đổi cách phân chia. JVM/GC không hứa thời điểm unload.

## 12. Mô hình trung gian: nhóm loader hoặc shared library loader

Hai cực “mỗi plugin một loader” và “mọi plugin dùng chung một loader” không phải là các lựa chọn duy nhất.

Ví dụ:

```text
Application Host Loader
    └── shared.api.*
          ^
          |
Common Plugin API/Library Loader
    ├── stable.shared.lib.*
    ├── contracts.*
    ^
    | parent
    +-----------+-----------+
    |                       |
PluginGroupLoader-X     PluginGroupLoader-Y
    |                       |
    +--> A, B                +--> C, D
```

Một nhóm plugin có thể dùng chung một loader khi **được quản lý vòng đời và dependencies chung**, nhóm khác có loader riêng. Hoặc có loader nền chứa stable shared libraries, mỗi plugin loader là child.

Cần hiểu:

- Nếu private dependency bị đưa vào parent **quá sớm**, các child parent-first sẽ không tự có phiên bản riêng.
- Shared library loader có thể tiết kiệm tài nguyên, nhưng làm **định nghĩa dependency chung thành ràng buộc version** giữa các plugin.
- Cây parent-child một chiều không tự hỗ trợ tất cả quan hệ chéo A→B. Muốn plugin dùng API nhau có thể cần **contract loader chung**, registry/service bus hoặc loader delegation rõ.
- Tránh graph loader có vòng dependency dẫn tới class-loading recursion hoặc deadlock. `ClassCircularityError` và `LinkageError` cũng có thể xuất hiện trong những tình huống liên quan class resolution/constraints.

Mô hình trung gian nên được thiết kế theo **dependency graph và lifecycle domains**, không đơn thuần theo số lượng file JAR.

## 13. JPMS, ModuleLayer và plugin loaders

Java 9+ có **Java Platform Module System (JPMS)**:

- `Module` định danh một module runtime và access rules.
- `ModuleLayer` tổ chức các modules được resolved trong một configuration.
- `ClassLoader` thực hiện nạp/định nghĩa classes, có thể gắn với một hoặc nhiều modules.

Với plugins được đóng thành **named modules**:

- `ModuleLayer.defineModulesWithOneLoader(...)`: tạo layer trong đó các modules được định nghĩa cho một loader được tạo.
- `ModuleLayer.defineModulesWithManyLoaders(...)`: tạo layer với một loader cho mỗi module trong configuration.
- Có thể dùng module mapping theo loader do bạn kiểm soát khi yêu cầu đặc thù và hiểu cơ chế `defineModules`.

Đây là những API khác với việc tạo nhiều `URLClassLoader` từ JARs đơn thuần. **Module không đồng nghĩa plugin**: một plugin có thể gồm nhiều modules, và một module có thể chứa nhiều thành phần. Chọn one/many loader trong `ModuleLayer` dựa trên module resolution, visibility và nhu cầu lifecycle, không coi một method là đáp án tự động.

**Giới hạn quan trọng:**

- `exports` và `opens` xác định khả năng truy cập code/resources/reflective access của named modules, không bị bỏ qua chỉ vì cùng class loader.
- Split packages giữa modules trong cùng loader có thể chịu ràng buộc của JPMS.
- Service providers có thể được đăng ký bằng `provides ... with ...` trong module descriptor.
- `ModuleLayer` không tự bảo đảm plugin hoàn toàn độc lập hoặc được unload khi không còn cần; vẫn có lifecycle/reference vấn đề tương tự.

Nếu plugin architecture đã dùng named modules, hãy thiết kế một **ModuleLayer/plugin manager** chuyên biệt thay vì chỉ xếp JAR URLs và kỳ vọng JPMS hiểu hết các quan hệ.

## 14. Khi cần child-first thay cho URLClassLoader parent-first

`URLClassLoader` thông thường kế thừa policy **parent-first**. Với case A và B của demo, parent không chứa `impl.Greeting` nên isolation hoạt động. Nhưng nếu parent có cùng binary name hoặc các dependency cần version riêng, child-first có thể được yêu cầu cho **private namespace**.

Một bản thiết kế:

```text
loadClass(name):
    lock bằng getClassLoadingLock(name)
    nếu đã loaded -> trả kết quả
    nếu name là JDK hoặc shared.api -> delegate parent trước
    nếu name thuộc plugin-private:
         thử findClass của child
         nếu ClassNotFoundException -> fallback parent theo policy
    các tên khác -> parent-first
    resolve khi cần
```

Đây là **giả mã**, không phải runnable Java method. Khi tự override `loadClass`, phải kiểm tra:

1. `findLoadedClass` và locking để tránh duplicate class definitions.
2. `resolveClass` theo tham số `resolve` nếu cần.
3. Không tự định nghĩa `java.*` từ custom loader; `defineClass` có namespace restrictions.
4. Không duplicate shared SPI/DTO contracts giữa parent/child.
5. Không bắt/nuốt `VerifyError`, `LinkageError`, `SecurityException` như lỗi “không tìm thấy”.
6. Chính sách resource `getResource` và `getResources` phải đồng bộ nơi cần thiết với class policy.
7. Java package sealing, CodeSource, certificates, module access và native resources có thể ảnh hưởng loader thực tế.

**Nếu plugin của bạn chỉ cần đọc JARs/thư mục và parent không chứa private classes**, hãy bắt đầu với `URLClassLoader` chuẩn. Một custom child-first loader tự viết không phải lựa chọn mặc định.

## 15. Vấn đề bảo mật và cô lập lỗi

Cả per-plugin loader lẫn shared loader đều chạy bytecode trong cùng JVM, chia sẻ quyền process và các nguồn CPU/RAM/OS. Nếu threat model bao gồm plugin không tin cậy hoặc yêu cầu hard stop, cần process/OS sandbox và giao thức rõ; ClassLoader chỉ cô lập runtime types/dependencies.

## 16. Quan sát/đo đạc khi quyết định

Để chọn có căn cứ, nên thử các workload thực tế và đo:

| Quan sát | Một loader/plugin có thể tốt hơn khi | Shared loader có thể tốt hơn khi |
|---|---|---|
| Kích thước class metadata, metaspace | Plugins sử dụng ít code trùng hoặc isolation quan trọng hơn | Plugins cùng nhiều dependencies, tiết kiệm duplicate metadata |
| Startup/load latency | Plugins độc lập và nạp lazy theo nhu cầu | Cả nhóm luôn chạy đồng thời, cache classes chung |
| Đổi phiên bản dependency | Nhiều thư viện trùng binary name khác versions | Có BOM/dependency lock thống nhất |
| Độc lập deploy/reload | A/B có release cadence riêng | Nhóm deploy atomically |
| Chia sẻ objects trực tiếp | Đa số giao tiếp qua public SPI/DTO | Plugins cố tình dùng chung nhiều implementation internals |
| Debugging | Có tooling log loader/class origins tốt | Ít namespace hơn nhưng phải xử lý class shadowing |
| Plugin failures | Có quy trình stop/cleanup riêng | Một lỗi/coupling có thể ảnh hưởng nhóm chung |

Không có bảo đảm “mỗi plugin một loader luôn nhanh hơn” hay “một loader luôn ít bộ nhớ hơn” cho mọi hệ thống. Nên đo **số classes thực nạp**, metaspace, heap retained references, runtime behavior, latency và phụ thuộc cụ thể, thay vì dùng số JAR làm proxy.

### Log chẩn đoán tối thiểu

Trong code đã có đối tượng `Class<?> type`:

```java
System.out.println("name=" + type.getName());
System.out.println("loader=" + type.getClassLoader());
System.out.println("module=" + type.getModule());
System.out.println("package=" + type.getPackageName());
```

Đây là snippet minh họa bên trong method, không phải chương trình độc lập. Đừng so sánh loaders bằng tên in ra từ `toString()`; nếu cần identity chắc chắn hãy dùng `==` trên đối tượng `ClassLoader` hoặc `Class<?>` trong cùng JVM.

Có thể thêm module/code source/version metadata nếu phù hợp; đừng suy ra class-file nguồn đáng tin chỉ từ resource URL nếu loader thực hiện bytecode transformation.

## 17. Các tình huống thường gặp và lựa chọn gợi ý

### Trường hợp 1: IDE/editor cho cài plugin bên thứ ba

Plugins có vòng đời/version khác nhau, dependency conflict tiềm ẩn và cần unload khi người dùng tắt plugin. **Ưu tiên mỗi plugin một loader hoặc nhóm độc lập theo lifecycle**, parent chứa SPI công khai. Nếu code không tin cậy, cần cô lập process/quyền thêm.

### Trường hợp 2: 20 “plugins” thực chất là các feature modules cùng một sản phẩm

Tất cả phát hành đồng bộ, dependency lock chung và không bao giờ hot swap riêng. **Một loader chung** hoặc JPMS layer/module grouping có thể đơn giản, miễn đã kiểm tra binary name collisions.

### Trường hợp 3: Hai plugin cùng dùng thư viện JSON nhưng phiên bản khác nhau

Nếu cần cả hai versions đồng thời, **tách defining loaders cho private library** hoặc relocate dependencies có kiểm thử. Không đặt một bản JSON parser private lên parent rồi kỳ vọng child parent-first chọn bản khác.

### Trường hợp 4: Plugin A phải gọi một service của B

Đừng trao đổi trực tiếp `b.internal.Foo` qua boundary nếu có thể tránh. Cung cấp **shared SPI/DTO** và registry/service locator quản lý endpoints/versions; host điều phối A↔B. Nếu cần gọi direct private classes, một loader chung hoặc layer/module graph có chủ đích sẽ phù hợp hơn, nhưng giảm isolation.

### Trường hợp 5: Nâng cấp plugin trong lúc host vẫn chạy

**Per-plugin loader + generation mới** cho phép thay code của riêng plugin ở mức class identity, miễn plugin cũ được quiesce/cleanup và không còn references sống. Với shared loader, thay cùng binary name của một plugin thường buộc build namespace mới cho cả nhóm.

### Trường hợp 6: Nhiều providers cùng SPI nhưng classnames trùng

Service descriptors của A và B đều khai báo `impl.Greeting`. Một shared loader không tự biết “`impl.Greeting` trong A” khác “trong B”; hãy đổi provider binary names, dùng loaders riêng hoặc có plugin-specific mapping. ServiceLoader là discovery mechanism, **không giải quyết class identity collision** thay bạn.

## 18. Checklist quyết định trước khi triển khai

- [ ] Plugins có cần **đa phiên bản của cùng binary class name** không?
- [ ] Plugin có cần được **deploy/reload/unload độc lập** không?
- [ ] Shared API/SPI và DTO đã có **một defining loader chung** chưa?
- [ ] Parent loader có vô tình chứa private libraries khiến child isolation không có hiệu lực không?
- [ ] Có nhiều `META-INF/services`, config/templates trùng resource names không?
- [ ] TCCL của plugin execution thread được set/reset đúng lúc không?
- [ ] Các cross-plugin method signatures có lộ class private không?
- [ ] Có cần JPMS `ModuleLayer`, named modules, qualified exports/opens không?
- [ ] Có đo duplicate classes/metaspace và overhead hay chỉ suy đoán?
- [ ] Có quản lý listeners/ThreadLocal/executors/ServiceLoader cache/native registrations cho unload không?
- [ ] Có test conflict thực sự: **cùng binary name** ở hai plugin với hành vi khác?
- [ ] Nếu mã plugin không đáng tin, có isolation/security **ngoài class loader** chưa?

**Quy trình khởi đầu hợp lý:** Đặt contracts ở host loader; triển khai thí điểm hai plugins có tên dependency trùng nhau; dùng `URLClassLoader` đơn giản cho các child; test loader identities, type casting, resource discovery và lifecycle. Chỉ phát triển custom delegation/ModuleLayer nâng cao khi thí điểm chứng minh cần thiết.

## 19. Câu hỏi tự kiểm tra

**1. Hai plugin cùng khai báo `impl.Greeting` nhưng mỗi plugin có defining loader khác nhau. Đây có phải cùng runtime type không?** Không. Class identity còn phụ thuộc defining loader.

**2. Một shared loader có thể đồng thời `defineClass` hai bản `impl.Greeting` khác nhau không?** Không trong cùng một defining loader. Một tên nhị phân gắn với một runtime class của loader đó.

**3. Có hai URLClassLoader riêng thì phụ thuộc A v1 và B v2 chắc chắn cách ly chưa?** Chưa. Nếu parent chứa cùng class name, parent-first delegation có thể khiến cả hai dùng bản parent.

**4. Hai plugin có loaders riêng có thể cùng implement một SPI interface do parent định nghĩa không?** Có. Đây là thiết kế phổ biến; plugin implementation types khác nhau nhưng shared SPI type là một.

**5. Hai plugin được nạp bởi cùng loader có nghĩa chỉ có một instance không?** Không. Nhiều instances vẫn có thể được tạo từ một hoặc nhiều runtime classes. Static fields của cùng runtime class mới được chia sẻ.

**6. Tại sao `getResources` có thể trả hai file cấu hình còn `loadClass` chỉ chọn một definition?** Resource enumeration có thể trả nhiều URLs, còn một defining loader chỉ có một runtime class cho mỗi binary name.

**7. `URLClassLoader.close()` có làm JVM unload ngay plugin classes không?** Không. Class unloading phụ thuộc references, GC và runtime lifecycle.

**8. Dùng ServiceLoader có tự giải quyết version conflict/trùng provider class name không?** Không. ServiceLoader phát hiện providers nhưng vẫn dùng class loader/module type identity rules.

**9. Nếu hai plugin phải hot-reload độc lập, mô hình nào thuận lợi hơn?** Thường per-plugin loader, với quiesce/cleanup và API boundary rõ. Không phải bảo đảm unload ngay.

**10. Nếu plugin code không đáng tin, một loader riêng có đủ bảo mật không?** Không. Cần thêm process/container/OS isolation và các biện pháp thực thi an toàn phù hợp.

**11. Dùng một loader chung có thể tiết kiệm metaspace không?** Có thể khi share nhiều classes/dependencies, nhưng cần đo workload; không bảo đảm mọi trường hợp tốt hơn.

**12. Chuyển sang JPMS có đồng nghĩa mỗi module tự có loader riêng không?** Không. ModuleLayer có các chiến lược mapping module→loader; module và loader là hai loại runtime entities khác nhau.

## 20. Nguồn chuẩn để đọc thêm

- Java SE 17 API, `ClassLoader` — defining/initiating loaders, parent delegation, `loadClass`, `defineClass`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 API, `URLClassLoader` — class/resource lookup từ URLs và `close()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- Java SE 17 API, `ServiceLoader` — service providers, classpath descriptors và named modules: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- Java SE 17 API, `ModuleLayer` — `defineModulesWithOneLoader` và `defineModulesWithManyLoaders`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html
- Java Virtual Machine Specification `5.3, *Creation of Classes and Interfaces*, runtime identity và defining loader: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- Java SE 17 API, `Thread` — TCCL: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html

**Ghi nhớ:** **Mỗi plugin một loader** ưu tiên tính độc lập về namespace, dependency versions và lifecycle; **nhiều plugin chung một loader** ưu tiên khả năng dùng chung classes/static state và triển khai đồng bộ. Dù chọn mô hình nào, hãy giữ **shared contracts được định nghĩa nhất quán**, kiểm tra delegation thực tế, biết các giới hạn của service/resource lookup và quản lý rõ vòng đời plugin.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-architecture-004">Giao tiếp qua interface, DTO và exception có định danh thống nhất</a>

<details>
<summary>Xem chi tiết</summary>

# Giao tiếp qua interface, DTO và exception có định danh thống nhất trong kiến trúc plugin

## 1. Bài toán cốt lõi: plugin chạy độc lập nhưng phải hiểu cùng hợp đồng

Kiến trúc plugin thường có **host application** và nhiều **plugin class loaders**. Host muốn gọi một plugin mà không biết trước implementation cụ thể; plugin muốn dùng thư viện riêng, có thể khác phiên bản với plugin khác. Điều này đặt ra hai yêu cầu tưởng như đối lập:

- **Cô lập:** class triển khai và dependency private của plugin A không bị trộn với class tương ứng của plugin B.
- **Giao tiếp được:** host và mọi plugin phải hiểu thống nhất các kiểu đi qua API boundary: interface, data-transfer objects (DTO), exceptions và các kiểu lồng trong chữ ký.

Trong JVM, kiểu runtime không chỉ được nhận diện theo tên như `api.Request`. **Định danh runtime class/interface là cặp (binary name, defining class loader)**. Nếu host dùng `api.Request` do loader H định nghĩa, còn plugin dùng một bản `api.Request` do loader P định nghĩa, đó là **hai kiểu khác nhau**, dù source/bytecode và tên package giống hệt.

**Nguyên tắc thiết kế: chia sẻ kiểu hợp đồng, cô lập kiểu triển khai.**

```text
            Host / Shared API loader (H)
            ├── api.Plugin              [H]
            ├── api.Request             [H]
            ├── api.Result              [H]
            └── api.PluginFailure       [H]
                     |
          +----------+-----------+
          |                      |
   Plugin loader A (A)     Plugin loader B (B)
   ├── plugin.Worker [A]   ├── plugin.Worker [B]
   └── impl.Formatter [A]  └── impl.Formatter [B]

   plugin.Worker [A] != plugin.Worker [B]
   api.Request nhìn từ A == api.Request nhìn từ H
   api.Request nhìn từ B == api.Request nhìn từ H
```

Ở mô hình này, host nhận được object đúng interface, trao đổi DTO cùng runtime identity và bắt được checked exception đúng loại. Plugin vẫn được quyền dùng `impl.Formatter` của riêng mình.

Bài này giải thích **lý do**, **thiết kế loader graph**, **cách định nghĩa API**, **ví dụ Java 17 nhiều file chạy được**, **các lỗi thường gặp** và **kiểm thử identity/binary compatibility**.

## 2. Phân biệt binary name, defining loader và initiating loader

Một class Java như:

```java
package api;
public record Request(String text) {}
```

có **binary name** `api.Request`. Nếu class được loader H định nghĩa, runtime identity là `("api.Request", H)`.

Nếu loader A không tự định nghĩa `api.Request` mà ủy quyền tới H, `loaderA.loadClass("api.Request")` có thể trả cùng đối tượng `Class<?>` với `Request.class` ở host.

Nếu loader A dùng child-first và tự định nghĩa một bản copy `api.Request`, runtime có thể tồn tại hai class:

```text
("api.Request", H) != ("api.Request", A)
```

Không thể “hợp nhất” hai loại trên chỉ bằng cách cast, đổi tên loader, cho chúng cùng source code, hoặc copy cùng JAR. Java runtime type identity gắn với **defining loader**, không gắn với đường dẫn file hay hash của bytecode.

Phân biệt:

| Thuật ngữ | Ý nghĩa |
|---|---|
| **Binary name** | `api.Request`, `api.Plugin`, `plugin.Worker` |
| **Defining loader** | Loader thực sự định nghĩa runtime class; `clazz.getClassLoader()` trả thông tin này với ordinary classes |
| **Initiating loader** | Loader được JVM ghi nhận đã khởi xướng nạp/resolve class, có thể nhận class do parent định nghĩa |
| **Parent loader** | Loader được một loader con hỏi trước trong `loadClass` mặc định |
| **Runtime type identity** | Binary name kết hợp defining loader |
| **Loading constraint** | Quy tắc JVM đòi cùng tên kiểu trong một số chữ ký liên kết qua loaders phải resolve tới **cùng runtime class** |

Hai class loader objects có thể có cùng `getName()` nhưng vẫn khác nhau. Tên loader chỉ phục vụ chẩn đoán. Khi kiểm tra định danh thật, so sánh `Class<?>` bằng `==` hoặc kiểm tra đối tượng defining loader.

### Class<?> khác với tên class

`clazzA.getName().equals(clazzB.getName())` có thể là `true` nhưng `clazzA == clazzB` là `false`. Trong plugin architecture, kiểm tra tên thôi không đủ để chứng minh host và plugin dùng chung API.

Khi cần kiểm tra:

```java
System.out.println(hostType == pluginViewType);
System.out.println(hostType.getClassLoader()
        == pluginViewType.getClassLoader());
```

Đây là **snippet minh họa**, yêu cầu hai biến `Class<?>` có thật. So sánh defining loader là một tín hiệu, còn so sánh `Class<?>` bằng `==` trực tiếp xác nhận cùng runtime class.

## 3. Chữ ký giao tiếp phải được thiết kế ở shared loader

Giả sử host công bố:

```java
package api;

public interface Plugin {
    Result handle(Request request) throws PluginFailure;
}
```

Đây là hợp đồng lý tưởng để **một** loader chung định nghĩa. Host và plugin phải thống nhất:

1. `api.Plugin` — interface để host có thể cast implementation và gọi method.
2. `api.Request` — kiểu dữ liệu tham số host gửi vào.
3. `api.Result` — kiểu trả về plugin gửi lại.
4. `api.PluginFailure` — exception host muốn bắt theo class cụ thể.

Nếu mỗi plugin tự đóng gói một bản API và định nghĩa nó trong loader riêng, các vấn đề có thể xuất hiện ở nhiều bước:

- Plugin implementation có thể không còn `instanceof Plugin` theo `Plugin.class` của host.
- Cast từ implementation sang `api.Plugin` của host có thể ném `ClassCastException`.
- JVM có thể ném `LinkageError` do **loading constraint violation** khi liên kết method/field descriptors bắt buộc phải dùng cùng runtime reference type.
- Một `catch (PluginFailure e)` của host không bắt được một exception class cùng binary name nhưng do loader khác định nghĩa nếu nó không phải subtype của đúng runtime `PluginFailure` host biết.
- DTO copy ở hai loaders không thể trao đổi trực tiếp như cùng Java type; serialization/deserialization cũng có thể chọn sai loader khi tái tạo object.

**Không phải hai class trùng tên ở hai loaders luôn là lỗi.** Việc tách hai bản `impl.Formatter` hoặc `plugin.Worker` có thể hoàn toàn đúng. Rủi ro phát sinh khi những runtime types **phải dùng chung** theo hợp đồng lại bị định nghĩa trùng.

## 4. Lựa chọn kiểu nào được phép qua boundary

Bảng hướng dẫn thiết kế:

| Loại dữ liệu | Chính sách khuyến nghị | Lý do |
|---|---|---|
| `api.Plugin` hoặc shared SPI | Một định nghĩa trong shared API loader | Host và plugin có cùng interface identity |
| `api.Request` / `api.Result` | Shared API loader | DTO qua lại không bị khác runtime type |
| `api.PluginFailure` và exception contract | Shared API loader | Host catch đúng exception type; compiler/VM hiểu chữ ký thống nhất |
| `java.lang.String`, primitive, Java core types | JVM built-in loader phù hợp | Các loại nền tảng được dùng chung |
| `impl.Formatter`, plugin-internal services | Plugin loader riêng | Có thể giữ phiên bản/cấu hình khác nhau |
| Loại phụ thuộc version như `thirdparty.Parser` | Plugin loader riêng nếu không xuất hiện ở public API | Cho phép nhiều phiên bản cùng tồn tại |
| `List<Request>` / `Map<String, Result>` | `List`/`Map` từ JDK, còn `Request`/`Result` từ shared API | Generics không xóa yêu cầu identity của phần tử |
| Plugin-specific exception chỉ xử lý nội bộ | Plugin loader, bắt/chuyển thành shared `PluginFailure` tại boundary | Host không cần import private error types |
| DTO serialization schema hoặc JSON | Shared schema/adapter có version contract rõ | Tránh truyền object private; serialization vẫn cần compatibility tests |

Một API tốt thường **nhỏ, ổn định và không có plugin-private dependencies** trong public signatures.

### Record DTO trong Java 17

`record` phù hợp cho dữ liệu bất biến đơn giản:

```java
package api;

public record Request(String text) {}
```

Đây là một **file riêng `Request.java`**. Record tạo accessor `text()`, constructor canonical và equality/hashCode theo thành phần. Nhưng **record không giải quyết classloader identity**: nếu hai loaders định nghĩa hai bản `api.Request` riêng, chúng vẫn là hai runtime classes khác nhau.

Nếu DTO chứa các field là class của plugin, ví dụ `privateparser.Node ast`, thì ngay cả DTO class được chia sẻ cũng mang dependency không ổn định qua boundary. Hãy dùng shared interfaces/records hoặc chuyển sang dữ liệu trung gian có schema xác định.

### Checked exception cũng là một kiểu runtime

`throws PluginFailure` là một phần của Java source method contract và được compiler dùng để buộc xử lý checked exception; JVM method descriptor *không chứa* danh sách `throws`. Class exception vẫn có runtime identity và cần được resolve đúng khi ném/catch.

Vì vậy **không nên** đưa bản copy `api.PluginFailure` vào plugin loader rồi mong `catch (api.PluginFailure)` của host nhận ra qua tên. Một class exception của plugin có thể là subtype của một shared base type, nhưng host phải catch **đúng shared base class** nếu muốn một contract ổn định.

## 5. Parent delegation giúp thống nhất shared API

Một sơ đồ loader đơn giản:

```text
          App/Shared API loader H
          ├── api.Plugin
          ├── api.Request
          ├── api.Result
          └── api.PluginFailure
                    ^
                    |
          +---------+----------+
          |                    |
      Loader A             Loader B
      plugin.*             plugin.*
      impl.*               impl.*
```

Mỗi plugin loader dùng `H` làm parent. `ClassLoader.loadClass` mặc định thường kiểm tra lớp đã được nạp, hỏi parent, sau đó mới gọi `findClass`/đọc bytes riêng khi parent không có class.

Với `api.*`, H trả về class chung. Với `plugin.*` và `impl.*`, H không có, nên A/B tự định nghĩa. Như vậy **parent-first đủ để vừa chia sẻ API vừa cô lập implementation**, nếu classpath của parent không chứa class implementation của plugin.

Cần lưu ý:

- Parent **không phải** superclass Java của class loader object.
- Có cùng parent **không bảo đảm** mọi child-first custom loader đều chia sẻ API; nếu override `loadClass` để tự định nghĩa `api.*`, vẫn có thể nhân bản types.
- `parent == null` ở loader mặc định hướng về bootstrap, **không tự nhìn thấy** API của application loader; chọn parent null cho plugin cần shared API thường đòi cơ chế lookup riêng.
- Trên một application server hay JPMS multi-layer setup, `ClassLoader.getSystemClassLoader()` chưa chắc là loader sở hữu shared API. Nên chọn loader của chính interface chung, ví dụ `Plugin.class.getClassLoader()`, khi phù hợp.
- Nếu shared interface do bootstrap loader định nghĩa, `getClassLoader()` có thể bằng `null`; phải đọc contract API của loader/factory nhận parent để hiểu trường hợp đặc biệt.

### Đừng cô lập API bằng child-first

Nếu cần child-first để nạp `thirdparty.*` riêng, áp dụng **selective child-first** cho package đã xác định. Những package `api.*` được host/plugin cùng dùng phải ưu tiên parent.

Hình mẫu kiến trúc:

```text
loadClass(name):
    if name startsWith "api.":
        use parent-first
    else if name startsWith "thirdparty.":
        try own classes first
        fallback according to explicit policy
    else:
        default parent-first or plugin policy
```

Đây là **pseudocode**, không phải implementation Java đầy đủ. Một override `loadClass` thực tế phải dùng `findLoadedClass`, `getClassLoadingLock(name)` và xử lý đúng `resolveClass`/errors. Không bắt mọi `LinkageError` rồi fallback sang một bản API khác như thể đó là lỗi class not found.

## 6. Demo Java 17 hoàn chỉnh: shared interface, DTO và checked exception

Demo có **hai plugin** nạp qua **hai `URLClassLoader` khác nhau**. Mỗi plugin tự định nghĩa class cùng binary name `plugin.Worker` và `impl.Formatter`, nhưng cả hai ủy quyền `api.*` về application loader.

Kết quả cần quan sát:

1. Host có thể cast cả hai object sang `api.Plugin`.
2. Plugin A/B nhận cùng loại `Request` và trả cùng loại `Result` của host.
3. Host bắt được chính xác `api.PluginFailure` do plugin ném.
4. Hai implementation classes khác identity, private helpers cũng khác identity.
5. Class loader của cả hai plugin trả cùng `Class<?>` cho các loại shared API.

### 6.1. Cấu trúc project

Tạo cây thư mục:

```text
src/
  api/
    Plugin.java
    Request.java
    Result.java
    PluginFailure.java
  plugin-a/
    impl/
      Formatter.java
    plugin/
      Worker.java
  plugin-b/
    impl/
      Formatter.java
    plugin/
      Worker.java
  host/
    PluginHost.java
out/
  api/
  plugin-a/
  plugin-b/
  host/
```

Thư mục `out` sẽ được tạo bởi lệnh biên dịch; không trộn source/output của plugin A và B trong cùng một classpath khi build host.

### 6.2. Shared API — chỉ biên dịch một bản

File `src/api/Plugin.java`:

```java
package api;

public interface Plugin {
    Result handle(Request request) throws PluginFailure;
}
```

File `src/api/Request.java`:

```java
package api;

public record Request(String text) {}
```

File `src/api/Result.java`:

```java
package api;

public record Result(String message) {}
```

File `src/api/PluginFailure.java`:

```java
package api;

public class PluginFailure extends Exception {
    public PluginFailure(String message) {
        super(message);
    }
}
```

Các file này là **bốn source files độc lập**. `Request`/`Result` là records Java 17, `PluginFailure` là checked exception vì kế thừa `Exception`. Tất cả phải được loader chung H định nghĩa một lần.

### 6.3. Plugin A — private Formatter phiên bản A

File `src/plugin-a/impl/Formatter.java`:

```java
package impl;

public final class Formatter {
    public String format(String input) {
        return "A:" + input;
    }
}
```

File `src/plugin-a/plugin/Worker.java`:

```java
package plugin;

import api.Plugin;
import api.PluginFailure;
import api.Request;
import api.Result;
import impl.Formatter;

public final class Worker implements Plugin {
    @Override
    public Result handle(Request request) throws PluginFailure {
        if (request.text().isBlank()) {
            throw new PluginFailure("blank");
        }
        return new Result(new Formatter().format(request.text()));
    }
}
```

`Formatter` là implementation detail. Không được đặt kiểu `impl.Formatter` trong chữ ký `api.Plugin.handle`.

### 6.4. Plugin B — cùng binary names, behavior khác

File `src/plugin-b/impl/Formatter.java`:

```java
package impl;

public final class Formatter {
    public String format(String input) {
        return "B:" + input;
    }
}
```

File `src/plugin-b/plugin/Worker.java`:

```java
package plugin;

import api.Plugin;
import api.PluginFailure;
import api.Request;
import api.Result;
import impl.Formatter;

public final class Worker implements Plugin {
    @Override
    public Result handle(Request request) throws PluginFailure {
        if (request.text().isBlank()) {
            throw new PluginFailure("blank");
        }
        return new Result(new Formatter().format(request.text()));
    }
}
```

Plugin A/B có cùng binary name cho `plugin.Worker` và `impl.Formatter`; nhưng class bytes của `Formatter` được lưu trong output directories riêng. Mỗi plugin loader sẽ định nghĩa một bản runtime độc lập.

### 6.5. Host — tải và gọi plugin bằng shared types

File `src/host/PluginHost.java`:

```java
package host;

import api.Plugin;
import api.PluginFailure;
import api.Request;
import api.Result;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class PluginHost {
    private static URL url(String directory) throws Exception {
        return Path.of(directory).toUri().toURL();
    }

    private static Plugin create(URLClassLoader loader)
            throws Exception {
        Class<?> implementation =
                Class.forName("plugin.Worker", true, loader);
        Object instance =
                implementation.getDeclaredConstructor().newInstance();
        return (Plugin) instance;
    }

    public static void main(String[] args) throws Exception {
        ClassLoader apiLoader = Plugin.class.getClassLoader();

        try (URLClassLoader loaderA = new URLClassLoader(
                     "plugin-A",
                     new URL[] { url("out/plugin-a") },
                     apiLoader);
             URLClassLoader loaderB = new URLClassLoader(
                     "plugin-B",
                     new URL[] { url("out/plugin-b") },
                     apiLoader)) {

            Plugin pluginA = create(loaderA);
            Plugin pluginB = create(loaderB);

            Request request = new Request("hello");
            Result answerA = pluginA.handle(request);
            Result answerB = pluginB.handle(request);

            System.out.println("A result: " + answerA.message());
            System.out.println("B result: " + answerB.message());

            Class<?> workerA = pluginA.getClass();
            Class<?> workerB = pluginB.getClass();
            Class<?> formatterA = loaderA.loadClass("impl.Formatter");
            Class<?> formatterB = loaderB.loadClass("impl.Formatter");

            System.out.println("Worker identity differs: "
                    + (workerA != workerB));
            System.out.println("Formatter identity differs: "
                    + (formatterA != formatterB));

            System.out.println("Both implement shared Plugin: "
                    + (pluginA instanceof Plugin
                    && pluginB instanceof Plugin));

            boolean sharedApi =
                    loaderA.loadClass("api.Plugin") == Plugin.class
                    && loaderB.loadClass("api.Plugin") == Plugin.class
                    && loaderA.loadClass("api.Request") == Request.class
                    && loaderB.loadClass("api.Request") == Request.class
                    && loaderA.loadClass("api.Result") == Result.class
                    && loaderB.loadClass("api.Result") == Result.class
                    && loaderA.loadClass("api.PluginFailure")
                            == PluginFailure.class
                    && loaderB.loadClass("api.PluginFailure")
                            == PluginFailure.class;

            System.out.println("All shared API identities match: "
                    + sharedApi);

            System.out.println("Request defining loader shared: "
                    + (Request.class.getClassLoader() == apiLoader));

            try {
                pluginA.handle(new Request(""));
            } catch (PluginFailure expected) {
                System.out.println("Caught from A: "
                        + expected.getMessage());
                System.out.println("A failure class shared: "
                        + (expected.getClass() == PluginFailure.class));
            }

            try {
                pluginB.handle(new Request(""));
            } catch (PluginFailure expected) {
                System.out.println("B failure class shared: "
                        + (expected.getClass() == PluginFailure.class));
            }
        }
    }
}
```

`URLClassLoader(String name, URL[] urls, ClassLoader parent)` có từ Java 9 nên hợp lệ trên Java 17. Loader A/B dùng **cùng** `apiLoader` làm parent; mỗi loader có URLs riêng.

Lưu ý:

- `Class.forName("plugin.Worker", true, loader)` yêu cầu lookup qua loader plugin được chọn và initialization theo API.
- `getDeclaredConstructor().newInstance()` tạo implementation object qua public implicit no-arg constructor.
- `(Plugin) instance` cast hợp lệ vì `api.Plugin` luôn được parent định nghĩa.
- `Result` do `plugin.Worker` tạo là đúng `api.Result` shared type, nên host gọi `message()` mà không cần reflection/serialization trung gian.
- `PluginFailure` do implementation ném là đúng exception class của host. `catch (PluginFailure)` bắt được từ cả hai plugin.
- `getClass().getClassLoader()` của implementation là loader plugin, trong khi `Request.class.getClassLoader()` là loader chung.
- `try-with-resources` đóng hai `URLClassLoader` sau khi dùng; đóng loader **không tức thì unload** class hay tiêu hủy các object có reference còn sống.

### 6.6. Biên dịch và chạy

Với Bash trên Linux/macOS, chạy tại thư mục chứa `src`:

```bash
mkdir -p out/api out/plugin-a out/plugin-b out/host

javac -d out/api \
  src/api/Plugin.java \
  src/api/Request.java \
  src/api/Result.java \
  src/api/PluginFailure.java

javac -cp out/api -d out/plugin-a \
  src/plugin-a/impl/Formatter.java \
  src/plugin-a/plugin/Worker.java

javac -cp out/api -d out/plugin-b \
  src/plugin-b/impl/Formatter.java \
  src/plugin-b/plugin/Worker.java

javac -cp out/api -d out/host src/host/PluginHost.java

java -cp "out/api:out/host" host.PluginHost
```

Trên Windows PowerShell (class path separator là dấu `;`):

```powershell
New-Item -ItemType Directory -Force out/api,out/plugin-a,out/plugin-b,out/host

javac -d out/api src/api/Plugin.java src/api/Request.java src/api/Result.java src/api/PluginFailure.java

javac -cp out/api -d out/plugin-a src/plugin-a/impl/Formatter.java src/plugin-a/plugin/Worker.java

javac -cp out/api -d out/plugin-b src/plugin-b/impl/Formatter.java src/plugin-b/plugin/Worker.java

javac -cp out/api -d out/host src/host/PluginHost.java

java -cp "out/api;out/host" host.PluginHost
```

**Tuyệt đối không đưa `out/plugin-a` hoặc `out/plugin-b` vào classpath của host ở bước chạy demo.** Nếu parent nhìn thấy `plugin.Worker` hoặc `impl.Formatter`, parent-first delegation có thể trả class của parent trước, làm mất tính cô lập.

### 6.7. Stdout dự kiến trên JDK 17+

Với cấu trúc sạch và không thay classpath đã nêu:

```text
A result: A:hello
B result: B:hello
Worker identity differs: true
Formatter identity differs: true
Both implement shared Plugin: true
All shared API identities match: true
Request defining loader shared: true
Caught from A: blank
A failure class shared: true
B failure class shared: true
```

**Phân tích kết quả:**

1. `A:hello` và `B:hello` chứng minh hai plugin dùng `Formatter` riêng.
2. `workerA != workerB` dù cùng binary name `plugin.Worker` chứng minh class identity khác giữa defining loaders.
3. `formatterA != formatterB` xác nhận private dependency cùng tên cũng được cô lập.
4. Cả hai đều `instanceof Plugin` vì interface `api.Plugin` do **một** parent loader định nghĩa.
5. `sharedApi` so sánh **bốn shared classes** từ góc nhìn của hai loader; kết quả true chứng minh contract identity thống nhất trong demo.
6. `Request.class.getClassLoader() == apiLoader` cho biết defining loader của DTO ở host là loader chung.
7. Hai lệnh `catch (PluginFailure)` từ A và B đều thành công; exception class đúng `PluginFailure.class` của host, không phải một exception copy cùng tên.

**Giới hạn demo:** project chạy trên class path kiểu truyền thống; trong named modules hoặc khi dùng `ModuleLayer`, còn phải xét `requires`, `exports`, `opens` và mapper module-to-loader. Việc cùng loader không tự vượt qua access rules.

## 7. Điều gì xảy ra nếu plugin đóng gói lại API?

Có hai tình huống riêng.

### Tình huống A: Plugin JAR chứa bản copy API nhưng loader vẫn parent-first

Nếu plugin artifact có các class `api.*` nhưng parent loader đã định nghĩa `api.Plugin`, `api.Request`, `api.Result` và `api.PluginFailure`, thuật toán `loadClass` mặc định thường trả class từ parent trước. Bản copy ở child classpath **có thể không được định nghĩa** trong đường lookup đó.

Tuy nhiên đóng gói trùng API vẫn là rủi ro quản lý dependency: một thay đổi delegation, một parent cấu hình sai hoặc một plugin loader child-first có thể làm bản copy trở thành runtime class khác.

**Cách làm tốt hơn:** build plugin với API dependency ở dạng **compile-only/provided**; đóng gói private implementation dependencies nhưng không bundle lại shared API vào plugin runtime artifact khi không cần.

### Tình huống B: Plugin child-first tự định nghĩa API

Giả sử plugin có bản `api.Plugin` riêng do loader P định nghĩa:

```text
api.Plugin [H] != api.Plugin [P]
```

Class `plugin.Worker` ở P implements `api.Plugin [P]`. Host cầm `api.Plugin [H]` và muốn:

```java
api.Plugin plugin = (api.Plugin) implementation;
```

Đây là snippet minh họa các biến/class đã tồn tại. Nếu `implementation` chỉ implements bản interface P, phép cast không thỏa runtime assignability với H và có thể ném `ClassCastException`.

Kể cả khi methods có cùng tên, descriptor chuỗi giống hệt, JVM **không suy ra cùng runtime interface type**.

Nếu method overriding hoặc cross-loader resolution bắt buộc các tên kiểu như `api.Request` hay `api.Result` phải đồng nhất mà hai loaders lại resolve khác nhau, JVM có thể từ chối liên kết bằng `LinkageError` do **loader constraint violation**.

**Không phải mọi trường hợp phải đợi tới cast.** Với một số liên kết method/interface hoặc field, lỗi có thể phát sinh ngay trong loading/linking. Không hardcode thời điểm exception cho mọi JVM.

## 8. Loading constraints bảo vệ tính nhất quán chữ ký

Trong JVM, class/interface identity được xác định theo binary name và defining loader. Khi class từ hai loaders tham gia một số quan hệ linking, JVM áp đặt các **loading constraints**.

Xét:

```java
package api;

public interface Plugin {
    Result handle(Request request) throws PluginFailure;
}
```

Method descriptor JVM (không chứa `throws`) là:

```text
(Lapi/Request;)Lapi/Result;
```

Nếu class `plugin.Worker` do loader P định nghĩa implements `api.Plugin` do loader H định nghĩa, JVM cần duy trì tính nhất quán của các kiểu tham chiếu trong descriptor phù hợp với quy tắc linking/preparation, như:

```text
api.Request^P = api.Request^H
api.Result^P  = api.Result^H
```

Đây là ký hiệu khái niệm cho yêu cầu resolve cùng runtime class, không phải biểu thức Java. Dùng parent delegation cho `api.*` khiến P và H nhìn thấy cùng runtime class và đáp ứng constraint.

**Quan trọng về exceptions:** `throws PluginFailure` được biểu diễn trong `Exceptions` attribute của method/class metadata, **không phải trong method descriptor**. Vì vậy **không khẳng định JVM áp đặt loading constraint cho `api.PluginFailure` chỉ vì nó xuất hiện trong `throws`**. Tuy vậy, nếu host cần catch chính xác exception type, identity của `PluginFailure` vẫn phải thống nhất qua shared loader. Những symbolic references khác tới exception type cũng có thể có quy tắc linking riêng.

### Generic không tự cứu được runtime identity

Ví dụ:

```java
List<Request> handleAll(List<Request> requests);
```

Mã trên là snippet method declaration cần `import java.util.List`. Ở bytecode, kiểu generic arguments bị erasure; descriptor thông thường là:

```text
(Ljava/util/List;)Ljava/util/List;
```

`Request` có thể nằm trong generic `Signature` metadata chứ không nằm trực tiếp trong descriptor ở đây. Điều đó **không làm hai `Request` ở hai defining loaders trở thành cùng type**. Khi truy xuất phần tử, compiler có thể chèn `checkcast` theo expected runtime class; một bản DTO khác loader có thể gây `ClassCastException`.

Đừng coi generic type parameters là kênh chuyển object an toàn giữa hai không gian loader riêng.

## 9. Exception identity và chiến lược xử lý lỗi plugin

### 9.1. Chia sẻ exception contract

Host thường cần catch và xử lý lỗi domain:

```java
try {
    Result result = plugin.handle(request);
} catch (PluginFailure error) {
    // Ghi log, trả kết quả nghiệp vụ, retry theo policy.
}
```

Đoạn minh họa cần `plugin` và `request` đúng shared types. Việc host catch chính xác `PluginFailure` hoạt động khi plugin ném **chính runtime `api.PluginFailure`** hoặc subclass hợp lệ của nó.

Trong demo phần 6, cả plugin A/B gọi:

```java
throw new PluginFailure("blank");
```

Tên `PluginFailure` được import từ package `api` và parent loader cung cấp cùng class host biết.

### 9.2. Private exception nên được chuyển tại boundary

Plugin có thể phụ thuộc một thư viện riêng ném `vendor.ParseException` hoặc `impl.InternalFault`. Host không nên phải import private exception class mà plugin loader sở hữu. Một adapter ở plugin boundary:

```java
@Override
public Result handle(Request request) throws PluginFailure {
    try {
        return executeInternal(request);
    } catch (VendorParseException error) {
        throw new PluginFailure("Input format is invalid");
    }
}
```

Đây là **pseudocode gần Java**: muốn biên dịch cần các method `executeInternal` và class `VendorParseException` được khai báo, cùng shared `Result`/`Request`/`PluginFailure`. Nếu yêu cầu giữ cause thì thiết kế shared `PluginFailure` có constructor `(String, Throwable)`, nhưng chú ý cause có thể mang private exception object/class tới host.

Một chính sách production thường gồm:

- Phân loại lỗi theo shared error codes hoặc shared exception hierarchy nhỏ.
- Không rò class implementation/private-library trong public signature.
- Giữ thông tin nguyên nhân trong logs theo chính sách dữ liệu/riêng tư, tránh expose toàn bộ stack trace hoặc secrets tới plugin khác.
- Phân biệt checked business failure với fatal runtime failures; đừng bắt mọi `Throwable` rồi giấu `OutOfMemoryError`/`ThreadDeath` như một lỗi nghiệp vụ bình thường.

### 9.3. Catch cùng tên nhưng khác loader không tương đương

Giả sử host định nghĩa `api.PluginFailure [H]`, plugin P tự định nghĩa `api.PluginFailure [P]`. Hai class này cùng extends `Exception` nhưng **không có quan hệ kế thừa** với nhau. Câu lệnh `catch (api.PluginFailure)` ở host chỉ bắt những `Throwable` assignable sang **host runtime class H**.

Nếu muốn host xử lý một private exception, cần một **shared supertype** hoặc chuyển nó thành shared exception/DTO tại plugin adapter boundary; không dựa vào `getName()`.

## 10. DTO compatibility: cùng runtime class chưa đủ cho tiến hóa API

Việc thống nhất defining loader xử lý **runtime type identity**, nhưng **không tự bảo đảm binary/source compatibility** giữa host và plugin versions.

Ví dụ host API v2 đổi record:

```java
public record Request(String text, int timeout) {}
```

Nếu plugin được biên dịch theo v1 `Request(String text)` nhưng runtime host chỉ có v2, lời gọi constructor/signature/method phù hợp có thể thay đổi, gây `NoSuchMethodError` hoặc lỗi liên kết khác. Dù runtime chỉ có **một** `api.Request` do H định nghĩa, bản thân public ABI đã thay đổi không tương thích.

Quy tắc thiết kế:

1. Version shared API độc lập với plugin implementation version.
2. Đặt nguyên tắc compatibility rõ: những thay đổi constructor, record components, interface methods, exception contracts phải được xét trước khi phát hành.
3. Cân nhắc factory methods, builders hoặc DTO envelopes có version field nếu cần tiến hóa schema.
4. Kiểm thử plugin biên dịch với API cũ chạy trên host mới trong phạm vi compatibility được cam kết.
5. Tránh để `vendor.*`, `impl.*` xuất hiện trong public fields, method parameters/returns, exception contract và serialized forms.
6. Nếu DTO đi qua process/JVM khác, classloader identity không còn là cơ chế truyền object; cần serialization schema/protocol có versioning.

### Record không luôn là DTO tốt nhất

Records tuyệt vời cho message bất biến trong cùng một JVM khi shared API ổn định; nhưng thay đổi record component list thường làm thay canonical constructor và một số contract. Với API cần backward compatibility kéo dài, có thể phù hợp hơn khi dùng interface, builder-based value object hoặc versioned message schema.

Không nên dùng Java `Serializable` như một giải pháp mặc định để “vượt class loaders”: deserialization vẫn cần chọn loader, kiểm tra type và serial compatibility; đặc biệt phải quản lý an toàn input không tin cậy.

## 11. ServiceLoader và loader của provider

Với classpath SPI, provider JAR ghi `META-INF/services/api.Plugin` chứa tên provider như `plugin.Worker`. Host dùng `ServiceLoader.load(Plugin.class, pluginLoader)` để chọn loader rõ ràng; provider phải assignable sang **chính `Plugin.class` của host**. Bản duplicate SPI do child định nghĩa có thể gây `ServiceConfigurationError: not a subtype`. Overload một tham số dùng TCCL của thread; named modules có cơ chế `uses`/`provides` và `ServiceLoader.load(layer, service)`.

## 12. Trường hợp nhiều class loader: arrays, generics, resources và reflection

### Array của DTO

`Request[]` là reference array type. Nếu `Request` do loader H định nghĩa, array class có defining-loader behavior gắn với component/element type theo JVM. Plugin A/B sử dụng chung `Request` sẽ dùng chung runtime array class tương ứng.

Nếu plugin B định nghĩa bản `Request` riêng, `Request[]` của nó cũng không tương thích với `Request[]` host theo cách Java type system kỳ vọng. Không thể dựa vào array covariance để làm hai component types khác loader trở nên cùng type.

### Generics và bridge methods

`List<Result>` chỉ bảo toàn generic type information theo Java source/reflection metadata ở mức nhất định, còn JVM verifier/casts xử lý runtime types thực tế. Với interface/generic methods được compiler bridge, classloader identity của shared API vẫn phải được thống nhất để đảm bảo method dispatch/linking.

Không tự suy từ generic `Signature` trong class file rằng hai DTO trùng tên giữa loaders là tương thích.

### Tài nguyên và classpath

Class lookup khác với resource lookup:

```java
ClassLoader loader = plugin.getClass().getClassLoader();
java.net.URL resource = loader.getResource("plugin.properties");
```

Snippet minh họa cần biến `plugin`. Nếu plugin dùng private configuration/resource, ưu tiên resource lookup theo **plugin loader** cụ thể. Nếu resource là contract của host, dùng shared API anchor/classloader theo thiết kế.

Service configuration files trong `META-INF/services` cũng là resources; parent delegation/resource lookup và duplicate descriptors cần được kiểm thử.

### Reflection

`Class.forName("plugin.Worker", true, loaderA)` tìm implementation của A, nhưng việc tạo object/cast vẫn phụ thuộc constructor/accessibility và shared interface identity. Nếu dùng `getDeclaredConstructor().newInstance()`, method/constructor access checks và JPMS exports/opens có thể ảnh hưởng khi chuyển sang named modules.

`Class.forName(String)` overload một tham số **không tự dùng TCCL**; nó dùng loader của caller. Do đó plugin discovery bằng reflection nên chỉ rõ loader để tránh tìm bản class của host hoặc plugin khác.

## 13. ClassLoader isolation và giới hạn của nó

Shared API identity không đồng nghĩa resource hay quyền truy cập của plugin được cách ly hoàn toàn. Static state của classes ở parent có thể chia sẻ giữa các plugin; instance/`Class<?>`/thread/TCCL/caches có thể giữ loader cũ reachable sau redeploy. `URLClassLoader.close()` không ép unload. Mã plugin không tin cậy cần process/OS isolation, vì classloader riêng không cấm dùng JDK file/network APIs.

## 14. Chẩn đoán vấn đề ở ranh giới host–plugin

### Lỗi: ClassCastException dù class implements interface cùng tên

Kiểm tra:

1. `hostPluginClass == loaderA.loadClass("api.Plugin")`?
2. `pluginObject.getClass().getInterfaces()` chứa đúng runtime `Plugin.class` chưa?
3. Plugin có tự đóng gói `api.Plugin` và dùng child-first không?
4. Tên class giống nhau nhưng defining loaders có khác nhau không?

**Hướng sửa:** chỉ định nghĩa API trong shared loader, cho plugin delegate package API về parent. Không “cast qua Object” để che lỗi; runtime type vẫn sai.

### Lỗi: LinkageError với thông điệp loader constraint violation

Kiểm tra field/method descriptor ở host/plugin boundary:

```bash
javap -classpath out/api -s -p api.Plugin
javap -classpath out/plugin-a -p plugin.Worker
```

Ví dụ demo, `Plugin.handle` có descriptor:

```text
(Lapi/Request;)Lapi/Result;
```

Nếu plugin resolver thấy hai bản `api.Request`/`api.Result` từ hai defining loaders, JVM có thể yêu cầu identity thống nhất do method overriding/linking và báo lỗi.

Sửa bằng module/classloader architecture, không đơn giản bằng thay tên source file. Với Java 17 HotSpot có thể dùng class loading log để xác định nguồn class:

```bash
java -Xlog:class+load=info -cp "out/api:out/host" host.PluginHost
```

Log có thể rất dài và format phụ thuộc JVM. Cần kết hợp `Class.getClassLoader()` để xác nhận defining loader.

### Lỗi: catch không bắt được exception plugin

Có thể do:

- Plugin ném private exception không phải subtype của shared `PluginFailure`.
- Plugin tự định nghĩa bản `api.PluginFailure` khác loader.
- Code đã catch nhầm exception family.

**Hướng sửa:** bắt/chuyển đổi lỗi tại adapter boundary thành shared exception (hoặc shared error DTO) và kiểm tra runtime `Class<?>` của exception.

### Lỗi: NoSuchMethodError sau nâng cấp shared API

Một plugin được compile với ABI khác với host API runtime; type identity có thể chung nhưng method descriptor/constructor đã thay đổi. Kiểm tra API version/ABI; không kết luận ngay classloader collision.

### Lỗi: ServiceConfigurationError trong SPI

Provider không assignable với shared service interface, thiếu public provider constructor hoặc cấu hình `META-INF/services` sai. Kiểm tra cả loader dùng để tìm provider và loader định nghĩa interface.

### Lỗi: ClassNotFoundException với shared DTO

Parent loader không thấy API class, plugin loader parent-null không có đường tìm shared types, hoặc runtime thiếu JAR. Kiểm tra build artifacts, classpath/module path, parent delegation và tên binary.

### Lỗi: Works locally, fails in application server

Trong container, `getSystemClassLoader()` chưa chắc là owner của shared API; TCCL có thể là loader của deployment/worker khác. Hãy dùng anchor `Plugin.class.getClassLoader()` hoặc factory/registry cung cấp loader đúng, sau đó kiểm tra cả module access nếu JPMS được dùng.

## 15. Checklist thiết kế và kiểm thử

Trước khi phát hành một plugin, xác nhận:

1. **API boundary rõ ràng:** mọi interface/DTO/checked exception chính đều nằm trong shared API artifact.
2. **Một defining loader cho shared types:** host và tất cả plugin resolve `api.*` qua cùng loader.
3. **Không bundle API không cần thiết:** plugin compile against shared API nhưng không tự định nghĩa duplicate API classes.
4. **Private implementations riêng:** plugin-specific code và versioned dependencies được sở hữu bởi plugin loader.
5. **Không lộ private types:** public method parameters/returns, public fields và exception contract không dùng plugin-private classes.
6. **Binary compatibility:** version host API và plugin build được kiểm tra; generic changes, record component changes, default methods và constructors được đánh giá.
7. **Exception contract:** host bắt chính xác shared exception hoặc chung base type; nội bộ plugin có adapter chuyển lỗi.
8. **SPI đúng loader:** plugin providers dùng chung service interface identity, có service descriptor/module provider declaration đúng.
9. **Concurrency:** class loader custom override `loadClass` phải tránh define cùng tên hai lần; dùng cơ chế khóa theo tên thích hợp.
10. **Lifecycle:** plugin shutdown hooks, thread locals, TCCL, listeners, caches, URLs được dọn.
11. **Quan sát:** log loader name và defining loader identity, module, classpath roots, nhưng không ghi secrets hoặc object payload nhạy cảm.
12. **Test chéo phiên bản:** chạy plugin A/B song song và cùng DTO/exception handling để bảo vệ shared boundary.

Một bài test integration mạnh không chỉ assert `a.handle(...)` đúng nội dung; hãy assert thêm:

```java
assert loaderA.loadClass("api.Plugin") == Plugin.class;
assert loaderB.loadClass("api.Request") == Request.class;
assert pluginA.getClass() != pluginB.getClass();
```

Đây là **snippet assertions**, không phải class chạy độc lập. Java `assert` mặc định **bị tắt** trừ khi chạy với `-ea`; trong test suite hãy dùng assertions của framework hoặc bật `-ea`. Code demo ở phần 6 dùng `System.out.println` để quan sát dù không bật assertion.

## 16. Các ngộ nhận thường gặp

| Nhận định sai | Điều đúng |
|---|---|
| “Chung package/tên là chung interface.” | Runtime identity còn phụ thuộc defining loader. |
| “Plugin và host phải dùng chung toàn bộ thư viện.” | Chỉ cần chia sẻ contract types; private dependencies có thể khác phiên bản. |
| “Cùng source code DTO thì cast được giữa loaders.” | Không nếu defining loaders khác nhau. |
| “Tạo hai loader có cùng parent là tự động an toàn.” | Child-first có thể định nghĩa duplicate API; phải xem lookup policy. |
| “Class loader name khác nhau là định danh class khác nhau.” | Tên chỉ để chẩn đoán; defining loader object quyết định identity. |
| “Exception cùng tên sẽ bị catch đúng.” | Catch kiểm tra runtime exception type/subtype, không chỉ class name. |
| “`throws` nằm trong JVM method descriptor.” | `throws` được ghi ở metadata `Exceptions`, không trong descriptor tham số/trả về. |
| “Generic `List<Request>` bảo đảm mọi phần tử đúng identity.” | Erasure không hợp nhất hai `Request` runtime classes. |
| “Record tự giải quyết version compatibility.” | Thay đổi record components/constructors có thể phá ABI/schema. |
| “Cứ dùng child-first mọi package để cô lập tốt nhất.” | Có thể tạo duplicate shared API và lỗi linkage. |
| “`URLClassLoader.close()` ngay lập tức unload plugin.” | Close không tự xóa mọi references hoặc classes. |
| “Cùng interface name đảm bảo ServiceLoader nhận provider.” | Provider phải assignable sang đúng shared service `Class<?>`. |

## 17. Bài tập tự kiểm tra

**Câu 1.** Loader A/B cùng định nghĩa `impl.Formatter` nhưng đều delegate `api.Plugin` về parent H. Điều đó có hợp lệ không?

**Đáp án:** Có. Private implementation khác defining loader có thể cùng tên, còn shared interface được giữ một runtime identity chung.

**Câu 2.** Hai class `api.Request` có cùng binary name và bytecode nhưng do H/P định nghĩa độc lập. `requestH.getClass() == requestP.getClass()` sẽ là gì?

**Đáp án:** `false`, vì hai runtime class identities khác defining loader.

**Câu 3.** Vì sao host nên định nghĩa `api.PluginFailure`?

**Đáp án:** Host muốn bắt và xử lý lỗi theo một runtime exception type ổn định; plugin phải ném chính shared exception hoặc subtype hợp lệ, không tự định nghĩa bản copy.

**Câu 4.** Method descriptor của `Result handle(Request request) throws PluginFailure` chứa tên kiểu nào?

**Đáp án:** `(Lapi/Request;)Lapi/Result;`. `PluginFailure` trong `throws` không nằm trong method descriptor; nó được lưu qua metadata ngoại lệ.

**Câu 5.** Nếu plugin dùng child-first cho `api.*` và implement bản `api.Plugin` riêng, host cast object plugin sang `Plugin` của H chắc chắn thành công không?

**Đáp án:** Không. Nếu class chỉ implements interface riêng của P, nó không assignable sang `api.Plugin` do H định nghĩa; có thể gặp `ClassCastException` hoặc lỗi linking liên quan.

**Câu 6.** `List<Request>` có tự làm các bản `Request` từ hai loaders tương thích không?

**Đáp án:** Không. Generics erasure không thay runtime class identity; cast hoặc use tại runtime vẫn có thể thất bại.

**Câu 7.** Host có API v2 nhưng plugin compile với API v1 và gọi method đã xóa. Nếu defining loader của shared API chỉ có một, chương trình vẫn có thể lỗi không?

**Đáp án:** Có. `NoSuchMethodError` hoặc lỗi binary compatibility vẫn có thể xảy ra vì ABI không khớp; loader identity chung không bảo đảm mọi version tương thích.

**Câu 8.** Vì sao trong demo `a.getClass() != b.getClass()` nhưng cả hai vẫn `instanceof Plugin`?

**Đáp án:** A/B định nghĩa riêng implementation `plugin.Worker`, nhưng cả hai cùng implement đúng runtime interface `api.Plugin` của parent.

**Câu 9.** `catch (PluginFailure e)` có bắt được một class tên `api.PluginFailure` của loader khác chỉ vì trùng binary name?

**Đáp án:** Không. Cần exception object thuộc đúng runtime class host biết hoặc subclass hợp lệ của nó.

**Câu 10.** Khi hot reload plugin A, có cần tạo lại shared API loader mỗi lần không?

**Đáp án:** Thông thường không. Tạo loader mới cho implementation phiên bản mới, giữ shared API loader ổn định khi contract host vẫn tương thích; dọn references tới plugin cũ để hỗ trợ unloading.

## 18. Tổng kết và tài liệu tham khảo

**Ba nguyên tắc cốt lõi:**

1. **Chung định danh cho contract:** interface, DTO và exception dùng qua host–plugin boundary phải được resolve về **cùng `Class<?>`**.
2. **Khác định danh cho implementation:** class triển khai và private versioned dependencies có thể được loaders riêng định nghĩa để cô lập.
3. **Kiểm tra cả linking, ABI và lifecycle:** parent delegation, Java/JVMS loading constraints, checked exceptions, ServiceLoader, version compatibility và resource cleanup đều là phần của plugin architecture bền vững.

**Nguồn chính thức (Java 17):**

- [JVMS 17 — 5.3 Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3): runtime class identity, defining/initiating loaders.
- [JVMS 17 — 5.3.4 Loading Constraints](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.4): yêu cầu nhất quán kiểu giữa loaders.
- [JVMS 17 — 4.3 Descriptors](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.3): method descriptor và các reference type names.
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): delegation, `findClass`, `loadClass`.
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): plugin URL paths, parent và `close`.
- [Java SE 17 — ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): provider lookup, explicit loader, TCCL.
- [Java SE 17 — Record Classes](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html): record type semantics.
- [JLS 17 — 13 Binary Compatibility](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html): khả năng tương thích nhị phân khi thay đổi API.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-architecture-005">Khởi tạo plugin và quản lý context class loader</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo plugin và quản lý context class loader trong Java

Một ứng dụng có hệ thống plugin thường phải **nạp bytecode của plugin bằng loader riêng**, tạo instance plugin, gọi các hook vòng đời như `start()`/`stop()` và cho phép plugin khám phá implementation hoặc resource thuộc phạm vi của chính nó. Vấn đề là nhiều API nền tảng và framework có thể được **định nghĩa ở loader cha**, còn provider, config hay tài nguyên của plugin lại nằm trong **loader con**. Parent-first delegation giúp child tìm API ở parent, nhưng parent không tự nhìn xuống mọi child loader.

**Thread Context ClassLoader (TCCL)** là cơ chế cho phép code đang chạy trên **một thread** nói rõ “các API có hỗ trợ context-based lookup nên dùng loader nào cho tác vụ hiện tại”. Trong quá trình khởi tạo plugin, host có thể **tạm đặt TCCL = plugin loader**, gọi constructor/start hooks, rồi **khôi phục TCCL cũ trong `finally`**. Điều quan trọng hơn là phải xác định đúng **lúc nào cần context**, tránh leak qua thread pool, và đảm bảo loader không bị đóng trong khi plugin vẫn cần nạp class/resource.

Bài này thuộc nhóm **Kiến trúc plugin** trong Java core – classloader. Mục tiêu là thiết kế chu trình khởi tạo và hủy plugin có ranh giới rõ, hiểu sự khác biệt giữa defining loader và TCCL, triển khai mẫu Java 17+ có thể biên dịch/chạy, phân tích plugin SPI discovery, executor/thread, JPMS và hot reload, đồng thời xử lý lỗi lifecycle một cách có thể kiểm chứng.

## 1. Ba bối cảnh loader cần tách riêng

Có ba quan hệ thường bị gộp thành một:

| Khái niệm | Gắn với gì? | Dùng để làm gì? |
| --- | --- | --- |
| **Defining loader** | Mỗi `Class<?>` đã được JVM định nghĩa | Xác định runtime type identity và nguồn class; `type.getClassLoader()` |
| **Parent loader** | Quan hệ delegation của một `ClassLoader` | Child hỏi parent trước theo mô hình parent-first; `loader.getParent()` |
| **Thread context class loader** | Mỗi `Thread` | API chủ động đọc TCCL có thể dùng nó để tìm providers/classes/resources của ngữ cảnh hiện tại |

Cần ghi nhớ:

- **Đổi TCCL không đổi defining loader của class đã nạp.** Nếu `host.PluginManager` do system loader định nghĩa, đổi TCCL không làm class đó được nạp lại bởi plugin loader.
- **Đổi TCCL không sửa `getParent()`.** Plugin loader vẫn có parent được chỉ định khi tạo loader.
- **Không phải mọi API đều đọc TCCL.** `ServiceLoader.load(Service.class)` dùng TCCL theo hợp đồng Java 17; `Class.forName(String)` dùng defining loader của class chứa lời gọi, còn `Class.forName(name, boolean, loader)` dùng loader được truyền.
- **TCCL có phạm vi thread.** Một request trên thread A không tự thay TCCL của thread B đang chạy trong executor.
- **Loader chọn lúc discovery khác loader thực sự định nghĩa class kết quả.** Plugin loader có thể delegate `api.Plugin` cho parent, dù nó tự define `plugin.Entry`.

Vì vậy, khi một class không được tìm thấy, cần xác định **API lookup cụ thể** và **class/thread gọi nó**, chứ không mặc định luôn đổi TCCL.

## 2. Bài toán kiến trúc plugin thực tế

Giả sử host cung cấp hai interface:

- `api.Plugin`: contract gồm `start()` và `stop()`.
- `api.Greeting`: một SPI mà plugin có thể cung cấp provider.

Một plugin chứa:

- `plugin.Entry` implements `api.Plugin`.
- `plugin.GreetingProvider` implements `api.Greeting`.
- Descriptor `META-INF/services/api.Greeting` chứa tên `plugin.GreetingProvider`.

Đồ thị classloader:

```text
             [Application/System Loader]
               |               |
           api.Plugin       api.Greeting
               |               |
               +-------+-------+
                       ^
                       | parent-first
                       |
               [Plugin URLClassLoader]
                       |
                       +-- plugin.Entry
                       +-- plugin.GreetingProvider
                       +-- META-INF/services/api.Greeting
```

Host **phải** dùng chung `api.Plugin`/`api.Greeting` giữa host và plugin, vì JVM nhận diện một type bằng **binary name + defining loader**. Nếu mỗi plugin tự define bản `api.Plugin` khác, ép kiểu instance plugin về `api.Plugin` của host có thể thất bại dù tên interface và source code giống hệt.

Tại sao TCCL hữu ích? Vì `plugin.Entry.start()` có thể gọi `ServiceLoader.load(Greeting.class)` để tìm implementation chỉ có ở plugin loader. Nếu TCCL lúc đó vẫn là host loader, discovery có thể không thấy `META-INF/services/api.Greeting` của plugin. Nếu TCCL được tạm đặt là plugin loader, ServiceLoader có đường đúng để tìm metadata/provider.

**Nhưng** không nên biến TCCL thành một “biến toàn cục” cho toàn JVM; nếu host chạy nhiều plugin, mỗi tác vụ cần dùng loader **đúng của plugin đang phục vụ**, rồi phải trả lại context trước khi thread được tái sử dụng.

## 3. Trình tự khởi tạo plugin nên như thế nào?

Một chu trình đơn giản:

```text
Đọc/kiểm tra plugin artifact
       |
       v
Tạo ClassLoader mới, parent = shared API loader
       |
       v
Load entrypoint class bằng plugin loader
       |
       v
Kiểm tra entrypoint implement đúng api.Plugin
       |
       v
Lưu original TCCL của thread hiện tại
       |
       v
Đặt TCCL = plugin loader
       |
       +-- khởi tạo entrypoint instance
       |   (constructor, class initialization khi JVM yêu cầu)
       |
       +-- gọi plugin.start()
       |   (SPI, resources, framework initialization)
       |
       +-- đăng ký listener/task/provider khi start thành công
       |
       v
Khôi phục original TCCL trong finally
       |
       v
Plugin RUNNING; host giữ loader + instance + lifecycle handle
```

**Không đóng `URLClassLoader` ngay sau constructor/start** nếu plugin còn phải chạy sau đó. `close()` ngăn nạp class/resource mới từ các URL của loader; các class đã load có thể tiếp tục hoạt động nhưng dependency được lazy-load về sau có thể thất bại.

Phân chia rõ các giai đoạn:

1. **Prepare:** xác minh nguồn JAR, version/API compatibility, quyền chạy, dependency và đường resource; lập namespace plugin.
2. **Load/verify:** dùng loader đã tạo để tìm entrypoint, kiểm tra `Plugin.class.isAssignableFrom(entryClass)`.
3. **Initialize/start:** đặt context thích hợp, tạo instance và gọi hook `start()`; có rollback khi lỗi.
4. **Active:** quản lý các request/tasks gọi vào plugin, luôn mang context đúng trong phạm vi cần thiết.
5. **Stop/dispose:** ngừng nhận công việc mới, chờ/hủy tasks, đặt đúng context để gọi `stop()`, unregister listener/provider/cache, close loader rồi bỏ strong references.

Một entrypoint class **được load** chưa chắc đã **được initialized**. `ClassLoader.loadClass` thường chỉ load class; initializer `<clinit>` có thể chạy khi code thực sự tạo instance hoặc thực hiện thao tác Java yêu cầu initialization. Nếu initializer dùng API dựa trên TCCL, **đặt TCCL trước khi tạo instance**, không phải sau constructor.

## 4. Mẫu Java 17+ chạy được: host và plugin tách classpath

Đây là chương trình độc lập dùng `URLClassLoader`, `ServiceLoader`, hai interfaces chung và một provider descriptor. Ví dụ đã được biên dịch bằng `javac --release 17` và chạy thành công.

### 4.1 Cấu trúc

Tạo thư mục `plugin-context-demo`:

```text
plugin-context-demo/
├── host-src/
│   ├── api/
│   │   ├── Plugin.java
│   │   └── Greeting.java
│   └── host/
│       └── Launcher.java
├── plugin-src/
│   └── plugin/
│       ├── Entry.java
│       └── GreetingProvider.java
├── host-classes/                         (output host)
└── plugin-classes/
    ├── plugin/
    │   ├── Entry.class
    │   └── GreetingProvider.class
    └── META-INF/
        └── services/
            └── api.Greeting                (service descriptor UTF-8)
```

Tạo `host-classes` và `plugin-classes/META-INF/services` trước khi chạy lệnh. **Không thêm `plugin-classes` vào runtime classpath của host**, vì ta muốn chứng minh plugin class/resource chỉ có trong loader riêng.

### 4.2 API contract chung

File `host-src/api/Plugin.java`:

```java
package api;

public interface Plugin {
    void start();
    void stop();
}
```

File `host-src/api/Greeting.java`:

```java
package api;

public interface Greeting {
    String text();
}
```

Host biên dịch và định nghĩa hai interfaces. Plugin được biên dịch **against** `host-classes` và lúc chạy delegate về parent để dùng đúng một bản `api.Plugin`/`api.Greeting`.

### 4.3 Provider của plugin

File `plugin-src/plugin/GreetingProvider.java`:

```java
package plugin;

import api.Greeting;

public final class GreetingProvider implements Greeting {
    @Override
    public String text() {
        return "Hello from provider";
    }
}
```

File **`plugin-classes/META-INF/services/api.Greeting`** chứa chính xác một dòng:

```text
plugin.GreetingProvider
```

Đây là **file văn bản, không có extension `.txt`**, không phải Java source. Path phải nằm dưới **classpath/resource root của plugin loader**. `ServiceLoader` dùng binary service name `api.Greeting` để tìm descriptor, sau đó đọc provider class name `plugin.GreetingProvider`.

### 4.4 Entrypoint plugin phụ thuộc vào TCCL

File `plugin-src/plugin/Entry.java`:

```java
package plugin;

import api.Plugin;
import api.Greeting;
import java.util.ServiceLoader;

public final class Entry implements Plugin {
    public Entry() {
        System.out.println("entry sees plugin TCCL="
                + (Thread.currentThread().getContextClassLoader()
                    == Entry.class.getClassLoader()));
    }

    @Override
    public void start() {
        Greeting service = ServiceLoader.load(Greeting.class)
                .findFirst().orElseThrow();

        System.out.println("plugin started: " + service.text());
    }

    @Override
    public void stop() {
        System.out.println("plugin stopped");
    }
}
```

Tại sao constructor in TCCL? Để xác minh **host đã đổi context trước khi khởi tạo plugin**. Method `start()` gọi `ServiceLoader.load(Greeting.class)`, API theo Java 17 **chọn TCCL**, không nhất thiết chọn defining loader của `Entry`.

`findFirst().orElseThrow()` buộc `ServiceLoader` tìm và instantiate provider đầu tiên. Nếu thiếu descriptor hoặc loader không nhìn thấy provider, method này ném `NoSuchElementException`; nếu metadata/provider không hợp lệ, `ServiceConfigurationError` có thể xuất hiện khi duyệt.

### 4.5 Launcher tạo loader, tạm đặt TCCL và restore

File `host-src/host/Launcher.java`:

```java
package host;

import api.Plugin;
import api.Greeting;
import java.net.URLClassLoader;
import java.net.URL;
import java.nio.file.Path;
import java.util.ServiceLoader;

public class Launcher {
    public static void main(String[] args) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();

        System.out.println("host providers="
                + ServiceLoader.load(Greeting.class).stream().count());

        URL url = Path.of("plugin-classes").toUri().toURL();

        try (URLClassLoader pluginLoader =
                new URLClassLoader(
                        new URL[]{url},
                        ClassLoader.getSystemClassLoader())) {

            Class<?> clazz = pluginLoader.loadClass("plugin.Entry");

            System.out.println("same API="
                    + Plugin.class.isAssignableFrom(clazz));

            thread.setContextClassLoader(pluginLoader);
            try {
                Plugin plugin = (Plugin) clazz
                        .getDeclaredConstructor()
                        .newInstance();

                plugin.start();
                plugin.stop();
            } finally {
                thread.setContextClassLoader(original);
            }
        }

        System.out.println("restored="
                + (thread.getContextClassLoader() == original));

        System.out.println("host providers after="
                + ServiceLoader.load(Greeting.class).stream().count());
    }
}
```

Giải thích design choices:

- `Plugin.class.isAssignableFrom(clazz)` xác nhận class entrypoint triển khai **chính interface mà host dùng**, không phải một bản duplicate do child define.
- `pluginLoader.loadClass("plugin.Entry")` tìm class qua URL/plugin namespace; thao tác này không tự bảo đảm đã chạy static initializer. TCCL được đặt **trước khi invoke constructor**.
- `ClassLoader.getSystemClassLoader()` làm parent để plugin resolve được API `api.Plugin` và `api.Greeting`.
- `thread.setContextClassLoader(pluginLoader)` gắn context với thread hiện tại; các API dùng TCCL như `ServiceLoader` sẽ dùng đúng plugin loader khi `start()` chạy.
- `finally` restore original kể cả khi constructor/`start()`/`stop()` ném exception.
- `try-with-resources` đóng loader **sau khi** đã gọi `stop()` trong demo ngắn hạn.
- Mục đích demo là quan sát context, **không phải toàn bộ production plugin manager**. Với plugin thực tồn tại nhiều giờ, host phải giữ loader đang mở xuyên suốt state RUNNING và chỉ close sau toàn bộ cleanup.

### 4.6 Biên dịch và chạy

Từ thư mục `plugin-context-demo`, dùng JDK 17 trở lên:

```bash
javac --release 17 -d host-classes host-src/api/Plugin.java host-src/api/Greeting.java host-src/host/Launcher.java
javac --release 17 -cp host-classes -d plugin-classes plugin-src/plugin/GreetingProvider.java plugin-src/plugin/Entry.java
java -cp host-classes host.Launcher
```

Các lệnh chỉ cần một classpath entry tại mỗi thời điểm, nên cú pháp dùng được trên Windows/Linux/macOS. File SPI descriptor cần được tạo **đúng tên và đường dẫn** trước khi chạy.

Stdout chính xác đã kiểm tra:

```text
host providers=0
same API=true
entry sees plugin TCCL=true
plugin started: Hello from provider
plugin stopped
restored=true
host providers after=0
```

### 4.7 Ý nghĩa của từng dòng output

- `host providers=0`: loader của host không tìm thấy `api.Greeting` provider vì provider chỉ nằm trong `plugin-classes` chưa có trên host classpath.
- `same API=true`: plugin `Entry` implements đúng `api.Plugin` do parent/system loader định nghĩa, cho phép host cast.
- `entry sees plugin TCCL=true`: ngay lúc constructor chạy, thread context chính là plugin loader.
- `plugin started: Hello from provider`: `ServiceLoader.load(Greeting.class)` đọc `META-INF/services/api.Greeting` qua TCCL, định vị provider và gọi implementation.
- `plugin stopped`: hook stop được gọi trước khi host close loader.
- `restored=true`: TCCL thread đã trở lại giá trị ban đầu.
- `host providers after=0`: sau khi restore, discovery bên host lại dùng context ban đầu, không tự “thấy” provider của plugin.

**Không nhầm kết quả cuối với unloading:** `host providers after=0` chỉ chứng minh context discovery đã restore. Nó **không** chứng minh JVM ngay lập tức unload các class/plugin loader hoặc thu hồi metaspace.

### 4.8 Điều gì xảy ra nếu quên đổi TCCL?

Nếu `Launcher` không gọi `thread.setContextClassLoader(pluginLoader)` trong khi `Entry.start()` vẫn dùng `ServiceLoader.load(Greeting.class)`, lời gọi discovery có thể sử dụng **host/system loader** (theo context ban đầu của ví dụ). Nó không thấy descriptor của plugin, vì vậy `findFirst()` không có phần tử và `orElseThrow()` ném `NoSuchElementException`.

Nếu bỏ `finally` restore, dòng `restored` có thể trở thành `false`, và những tác vụ host sau đó có thể bị hướng tới provider namespace của plugin đã dừng. Đây là lỗi isolation/lifecycle, không phải feature hot reload.

## 5. TCCL trong constructor, static initializer và start hook

Khi khởi tạo plugin, ba điểm có thể chạy code tùy biến:

1. **Class initialization:** JVM chạy static field initializers/`static { ... }` khi class lần đầu được khởi tạo.
2. **Constructor:** tạo object plugin, có thể đọc config, đăng ký callback, gọi service discovery.
3. **Start hook:** khởi động thread/scheduler, service provider, connection hoặc tài nguyên khác.

Nếu bất kỳ điểm nào chủ động dùng `Thread.currentThread().getContextClassLoader()` hoặc API đọc TCCL, thì context phải được chọn **trước khi thao tác đó bắt đầu**.

Ví dụ class plugin:

```java
package plugin;

public final class ContextCheck {
    static {
        System.out.println("TCCL in clinit = "
                + Thread.currentThread().getContextClassLoader());
    }

    public ContextCheck() {
        System.out.println("TCCL in constructor = "
                + Thread.currentThread().getContextClassLoader());
    }
}
```

Đây là **class minh họa**, không bao gồm chương trình `main`. Nếu host chỉ gọi `pluginLoader.loadClass("plugin.ContextCheck")` trước khi set TCCL, static block **chưa chắc chạy**. Nếu host gọi `Class.forName(name, true, pluginLoader)` hoặc constructor trước khi set TCCL, static block có thể chạy với context sai và đã gây side effect không thể hoàn tác chỉ bằng đổi loader sau đó.

Cách host chọn loader và thời điểm init có thể viết:

```java
Thread t = Thread.currentThread();
ClassLoader previous = t.getContextClassLoader();
try {
    t.setContextClassLoader(pluginLoader);
    Class<?> entry = Class.forName(
            "plugin.Entry", true, pluginLoader);
    Object instance = entry.getDeclaredConstructor().newInstance();
    // Gọi plugin.start() và framework initialization tại đây.
} finally {
    t.setContextClassLoader(previous);
}
```

Đây là **đoạn minh họa trong method có xử lý reflection exceptions**, giả định `pluginLoader` có sẵn. Đặt `initialize=true` có chủ đích khi muốn class initializer được chạy ngay trong context đang quản lý. Nếu không muốn static initialization quá sớm, có thể dùng `false` và chỉ init khi thực sự tạo instance.

**Chọn TCCL không thay thế chính sách khởi tạo class.** `Class.forName(String)` có loader mặc định của **class chứa call site**, không tự dùng TCCL. Khi host cần class do plugin định nghĩa, truyền loader explicit hoặc dùng `pluginLoader.loadClass`.

## 6. Viết helper để tránh quên restore

Trong code có nhiều entrypoints, nhân bản `try/finally` ở khắp nơi dễ gây quên. Có thể đóng gói thành helper nhận `ClassLoader` và `Callable<T>`:

```java
import java.util.concurrent.Callable;

public final class ContextLoaders {
    private ContextLoaders() { }

    public static <T> T withContext(
            ClassLoader loader, Callable<T> action)
            throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();

        try {
            thread.setContextClassLoader(loader);
            return action.call();
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}
```

Đây là **class đầy đủ có thể biên dịch bằng Java 17**. Cách sử dụng, giả định `pluginLoader` và `Plugin plugin` đã có:

```java
ContextLoaders.withContext(pluginLoader, () -> {
    plugin.start();
    return null;
});
```

Helper này cung cấp vài bảo đảm:

- Original TCCL được lưu lại **của chính thread thực thi action**.
- Không có biến global chứa context.
- Restore diễn ra dù action ném checked exception, runtime exception hoặc error; `finally` vẫn được thực thi theo semantics Java (trừ các sự cố kết thúc JVM/tiến trình bất thường).
- Có thể dùng nested call: outer đặt loader A, inner đặt B, inner restore về A, outer restore về original.

Giới hạn:

- `Callable<T>` là **đồng bộ**: nó đặt TCCL cho khoảng thời gian `action.call()` chạy trên thread hiện tại.
- Nếu action **khởi chạy asynchronous task** rồi trả ngay, context của future task cần được quản lý riêng; helper không tự “truyền context qua executor”.
- Nếu thread có `SecurityManager` đặc biệt hoặc chính sách runtime giới hạn `setContextClassLoader`, có thể phát `SecurityException`; cần xử lý theo môi trường Java 17 cụ thể.
- Không để plugin code giữ tham chiếu tới thread/loader theo cách gây leak.

### Tại sao không sửa `Thread.currentThread()` một lần ở startup?

Vì thread pool sử dụng nhiều worker threads và nhiều plugin/request khác nhau. TCCL là trạng thái gắn với từng thread và có thể bị framework đổi ở các điểm khác nhau. Thiết lập một lần ở main thread **không bảo đảm** mọi background worker đang chạy có TCCL đúng khi xử lý công việc.

## 7. ExecutorService và lỗi lan truyền ngữ cảnh

`Thread` thông thường có thể **kế thừa TCCL** từ thread tạo ra nó khi tạo thread. Nhưng thread pool worker được **tạo và tái sử dụng**; worker có thể đã tồn tại **trước** khi host set TCCL cho một request/plugin, hoặc có thể bị tác vụ trước sửa context rồi không restore.

Đây là vấn đề:

```java
Thread.currentThread().setContextClassLoader(pluginLoader);
executor.submit(() -> {
    // KHÔNG được mặc định TCCL == pluginLoader.
    useServices();
});
```

Đây là **đoạn minh họa**, giả định `executor`/`pluginLoader`/`useServices()` có sẵn. Việc set TCCL trên **thread submit** không sửa context của một **worker thread đã tồn tại**.

Một pattern đúng:

```java
executor.submit(() -> {
    Thread t = Thread.currentThread();
    ClassLoader before = t.getContextClassLoader();
    try {
        t.setContextClassLoader(pluginLoader);
        return ServiceLoader.load(Greeting.class)
                .findFirst().orElseThrow().text();
    } finally {
        t.setContextClassLoader(before);
    }
});
```

Đây là **đoạn minh họa** trong code host đã có `executor`, `pluginLoader` và `Greeting`. Nó dùng overload `Callable<String>` của `submit` và trả một `Future<String>`. Nếu muốn quan sát exception, phải gọi `Future.get()`/thiết lập cơ chế error reporting; chỉ `submit` rồi bỏ Future có thể che đi lỗi start/discovery.

**Quan trọng:** để tránh deadlock/race trong lifecycle, plugin manager cần theo dõi futures/tasks thuộc plugin. Không close loader trong khi tác vụ khác đang dùng loader để nạp class hoặc đọc resource.

### Thread được plugin tự tạo

Nếu plugin tự tạo thread trong `start()`, thread mới có thể **kế thừa TCCL của thread đang tạo nó**. Điều này làm provider lookup trong thread plugin có vẻ hoạt động — nhưng khi undeploy, thread còn sống có thể giữ TCCL trỏ tới plugin loader mãi.

Cần có lifecycle chính thức:

- Host hoặc plugin ghi nhận thread/executor ownership.
- `stop()` yêu cầu cancel/interrupt và chờ termination hợp lý.
- Thread pool/task wrapper restore TCCL.
- Dọn `ThreadLocal`, callback và schedulers chứa object plugin.
- Không chỉ dựa vào `URLClassLoader.close()` hoặc `System.gc()`.

**Không khuyến khích tự tạo luồng vô hạn không quản lý** trong plugin. Những daemon threads vẫn có thể giữ reference tới plugin loader dù không ngăn JVM exit.

## 8. Host cần giữ những gì khi plugin chạy?

Một plugin có trạng thái RUNNING thường cần một handle quản lý:

```text
PluginHandle
  |
  +-- pluginId / version / lifecycleState
  +-- URLClassLoader
  +-- api.Plugin instance
  +-- registered services/listeners/callbacks
  +-- owned tasks/executor references
  +-- configuration/module/layer context (nếu có)
  +-- cleanup actions
```

Đây là sơ đồ thiết kế, không phải class Java SE có sẵn.

**Không đóng loader cuối method factory** khi trả instance để host sử dụng dài hạn:

```java
Plugin createPlugin() throws Exception {
    try (URLClassLoader loader =
            new URLClassLoader(urls, parent)) {
        Class<?> clazz = loader.loadClass("plugin.Entry");
        return (Plugin) clazz.getDeclaredConstructor().newInstance();
    }
}
```

Đây là **mã minh họa**, `urls`/`parent`/`Plugin` là thành phần giả định. Bởi `try-with-resources` kết thúc khi return, loader đóng quá sớm. Class/object đã nạp có thể hoạt động ở những đường code sẵn có, nhưng lần đầu cần dependency/resource mới từ plugin URL sau đó có thể thất bại.

Thay vào đó, host giữ loader và instance trong `PluginHandle`; close loader chỉ sau `stop`/unregister/cancel tasks.

## 9. Khởi tạo thất bại và rollback

Startup có thể thất bại ở bất cứ giai đoạn nào:

- Plugin artifact sai tên/thiếu class → `ClassNotFoundException`.
- Entrypoint không implements API → `ClassCastException` hoặc kiểm tra `isAssignableFrom` thất bại.
- Constructor/`<clinit>` lỗi → `InvocationTargetException`, `ExceptionInInitializerError` hoặc linkage errors.
- `ServiceLoader` đọc descriptor sai → `ServiceConfigurationError` khi duyệt.
- Provider thiếu dependency → `NoClassDefFoundError`/`ServiceConfigurationError` tùy ngữ cảnh.
- `start()` ném runtime exception sau khi đã đăng ký một số listeners.
- Hệ plugin khởi động tasks nhưng không hoàn tất trong thời hạn chờ.

Một quy trình rollback nên có thứ tự:

1. Chỉ chuyển state sang **RUNNING** sau khi các điều kiện startup thành công.
2. Ghi nhận từng resource/registration được tạo trong quá trình start để có cleanup tương ứng.
3. Nếu lỗi, **ngừng task/registration đã tạo**, không đợi tới một stop phase chưa chắc được gọi.
4. Gọi cleanup của plugin nếu đối tượng đã tạo và contract cho phép cleanup cả khi start chưa hoàn thành.
5. Luôn restore TCCL của thread hiện tại.
6. Sau khi không còn công việc loader phải phục vụ, close loader và giải phóng host references.
7. Báo lỗi gốc kèm suppressed cleanup exceptions; **không nuốt lỗi start** chỉ vì cleanup có lỗi.

### Ví dụ pseudocode quản lý state

```java
// Các kiểu PluginHandle, Registry và CleanupRegistry do host tự định nghĩa.
PluginHandle handle = preparePlugin(artifact);
boolean started = false;
try {
    withPluginContext(handle.loader(), () -> {
        handle.instance().start();
        return null;
    });
    started = true;
    publishRunning(handle);
} catch (Throwable startupFailure) {
    try {
        stopTasksAndUnregister(handle);
        if (handle.instance() != null) {
            withPluginContext(handle.loader(), () -> {
                handle.instance().stop();
                return null;
            });
        }
    } catch (Throwable cleanupFailure) {
        startupFailure.addSuppressed(cleanupFailure);
    } finally {
        handle.loader().close();
    }
    throw startupFailure;
}
```

**Pseudocode minh họa, không phải Java class hoàn chỉnh**: các method và kiểu như `preparePlugin`/`publishRunning` chưa được khai báo. Mẫu cho thấy ý tưởng rollback và không để cleanup exception che mất nguyên nhân đầu.

Trong code production, cần tránh mâu thuẫn state/race; dùng `try/finally` và cấu trúc cleanup mạnh hơn để đảm bảo **mọi thao tác cần cleanup được thử**, ngay cả khi một bước dọn dẹp đã thất bại. `close()` cũng có thể ném `IOException`, nên phải quản lý suppressed exception.

**Điểm tinh tế:** Nếu `start()` đã thất bại giữa chừng, một `stop()` không được thiết kế cho trạng thái partially initialized có thể ném lỗi tiếp. Plugin API nên xác định rõ có hỗ trợ rollback hay cần từng registration trả một handle để cleanup an toàn.

## 10. Quy tắc đặt context cho các hook lifecycle

| Hoạt động | TCCL nên chọn thế nào? | Vì sao? |
| --- | --- | --- |
| Host đọc metadata an toàn trước load | Thường không cần plugin TCCL | Có thể validate artifact ngoài vùng execution |
| Host gọi `pluginLoader.loadClass` | Không bắt buộc đổi TCCL cho chính `loadClass` | Loader đã explicit; nhưng class init chưa chắc xảy ra |
| Khởi tạo class/constructor plugin | **Plugin loader**, nếu initializer dùng context-aware API | Static init/constructor có thể khám phá resources/providers |
| `plugin.start()` | **Plugin loader** | SPI discovery và framework initialization |
| Host xử lý nghiệp vụ thuần của mình | **Context host trước đó** | Tránh plugin rò vào request không liên quan |
| Callback/event handler của plugin | **Plugin loader** trong khoảng callback | Provider/resource lookup có thể diễn ra muộn |
| Task/executor của plugin | Đặt TCCL **trong worker thread lúc chạy task** | TCCL không tự truyền qua thread pool |
| `plugin.stop()`/cleanup hook | **Plugin loader** khi còn cần resource/provider để dọn | Cleanup có thể dùng class/resource plugin |
| Close loader | Sau khi tasks và cleanup hoàn tất | Tránh race với class/resource loading |
| Unload thực sự | Không điều khiển qua TCCL | Phụ thuộc strong references và GC/JVM |

Bảng là **nguyên tắc thiết kế**, không phải quy định rằng **mọi method đều bắt buộc đọc TCCL**. Nếu plugin chỉ dùng explicit loader hoặc API không dựa context, giữ TCCL host có thể phù hợp. Nhưng nếu contract plugin yêu cầu context, nên thể hiện rõ và nhất quán.

## 11. Một API Java không đồng nghĩa một kiểu lookup

So sánh các API thường gặp trong quá trình start:

| API | Loader/source chọn mặc định | TCCL ảnh hưởng trực tiếp? |
| --- | --- | --- |
| `ServiceLoader.load(Service.class)` | TCCL của thread đang gọi | **Có**, theo contract |
| `ServiceLoader.load(Service.class, loader)` | Loader được truyền | Không phụ thuộc TCCL cho lựa chọn ban đầu |
| `ServiceLoader.load(layer, Service.class)` | Named modules của layer và ancestors | Dựa vào ModuleLayer, không chỉ TCCL |
| `Class.forName("pkg.Impl")` | Defining loader của **class chứa câu lệnh gọi** | **Không** |
| `Class.forName("pkg.Impl", true, loader)` | Loader được truyền | Không, trừ khi chính bạn truyền giá trị TCCL |
| `pluginLoader.loadClass(name)` | Chính `pluginLoader` và parent delegation | Không mặc định |
| `ClassLoader.getSystemResource(...)` | System loader | Không mặc định |
| `Thread.currentThread().getContextClassLoader().getResource(...)` | Loader trả bởi TCCL | **Có**, do code chọn tường minh |
| `Main.class.getResource("config.json")` | Module/package/defining loader của `Main` | Không tự chọn TCCL |

Điều này giải thích một bug quen thuộc:

```java
// Đoạn code chạy trong host.FrameworkHelper do system loader định nghĩa:
Class<?> type = Class.forName("plugin.Entry");
```

Dù thread đang có TCCL = plugin loader, overload **một tham số** không tự sử dụng nó. Nếu host cần chọn plugin namespace, viết:

```java
ClassLoader context =
        Thread.currentThread().getContextClassLoader();

Class<?> type = Class.forName("plugin.Entry", true, context);
```

Cả hai là **đoạn minh họa**, yêu cầu caller xử lý `ClassNotFoundException`. Trong plugin manager production, thường tốt hơn là **truyền đúng `PluginHandle.loader()`** thay vì dựa gián tiếp vào TCCL có thể đổi.

## 12. ModuleLayer và plugin theo JPMS

Từ Java 9, một plugin có thể được đóng gói thành **named module** và resolve trong `ModuleLayer` riêng, thay vì chỉ JAR classpath/`URLClassLoader`. Khi ấy phải phân biệt **loader mapping** và **module service discovery**.

Các API:

- `ModuleLayer.defineModulesWithOneLoader(...)`: các modules layer mới chia sẻ loader.
- `ModuleLayer.defineModulesWithManyLoaders(...)`: mỗi module layer mới có loader riêng.
- `layer.findLoader("m.plugin")`: lấy loader được gán cho named module cụ thể.
- `ServiceLoader.load(layer, Service.class)`: tìm providers trong layer và ancestor layers theo JPMS; **không phải classpath SPI discovery từ unnamed modules**.
- `module-info.java` với `uses`/`provides ... with ...`: khai báo service consumption/provision trong explicit named modules.

Ví dụ minh họa descriptor:

```java
module m.plugin {
    requires m.api;
    provides api.Greeting with plugin.GreetingProvider;
}
```

Đây là **file `module-info.java` minh họa**; cần module `m.api` chứa/export `api.Greeting` và cấu hình module path đúng.

Khi plugin hoạt động trong custom layer, **không mặc định cứ set TCCL là giải quyết mọi service discovery**. Có thể cần dùng `ServiceLoader.load(pluginLayer, Greeting.class)`; cũng phải xét module `uses` (đối với caller module phù hợp), exports/opens, read edges và package-to-loader mapping.

Một host có thể dùng TCCL để tích hợp thư viện cũ **đồng thời** dùng `ModuleLayer` explicit cho provider JPMS. Đó là hai cơ chế bổ trợ, không thể thay thế nhau trong mọi ngữ cảnh.

### Có nên gán TCCL cho layer loader?

Nếu một framework legacy chỉ hỗ trợ lookup dựa trên TCCL nhưng plugin được tạo trong custom ModuleLayer, host có thể chọn loader thích hợp của module làm TCCL trong phạm vi framework call. Tuy nhiên loader đó có thể chứa một hoặc nhiều module, và không có bảo đảm nó nhìn thấy **tất cả** module/provider trong layer như overload `ServiceLoader.load(layer, ...)`. Khi muốn khám phá chính xác theo module layer, ưu tiên API module-aware.

## 13. Plugin isolation, security và shared APIs

Khi startup, kiểm tra **shared API identity** trước khi cast; nếu child định nghĩa lại `api.*`, TCCL không sửa được lỗi type identity. Chọn đúng loader khi tìm resource/SPI, nhưng kiểm tra riêng URL resource vì parent-first có thể trả config trùng tên của host. `ClassLoader` và TCCL không tạo sandbox cho plugin không tin cậy; JPMS access rules vẫn có hiệu lực. Kiểm chứng artifact/version và dùng ranh giới process khi yêu cầu cách ly bảo mật.

## 14. Plugin hot reload và memory leak do TCCL

Một lý do rất phổ biến khiến custom classloader không được GC thu hồi sau undeploy là **thread còn giữ TCCL trỏ tới loader cũ**.

Đường tham chiếu ví dụ:

```text
GC root
   |
   v
Thread pool worker vẫn sống
   |
   +-- Thread.contextClassLoader
              |
              v
        OldPluginLoader
              |
              v
       plugin classes / metadata
```

Nếu worker từ tác vụ plugin A **quên restore TCCL**, host có thể close URLClassLoader của A và thậm chí bỏ reference trong registry, nhưng thread vẫn có strong reference tới loader A. Bởi vậy loader và các class do nó định nghĩa **không thể được thu hồi theo kỳ vọng** cho đến khi tham chiếu còn giữ bị giải phóng.

Các nguồn giữ khác cần cleanup:

- Static maps của host chứa provider/`Class<?>`/reflection `Method`.
- `ServiceLoader` cache hoặc provider instance được shared quá deployment scope.
- ThreadLocal values, `InheritableThreadLocal` và executor queues chứa object/plugin class.
- Event listeners, scheduled tasks, callbacks, MBeans, JDBC driver registrations, logging bridges.
- Threads do plugin tạo vẫn chạy khi plugin đã stop.
- JNI global references hoặc native resources nếu plugin có native code.

**`URLClassLoader.close()` không ép class unloading.** Đóng loader giải phóng nguồn URL/JAR handles và ngăn loader định nghĩa class mới từ các URL; metadata của class đã nạp có thể vẫn sống. JLS quy định việc unload phụ thuộc khả năng GC thu hồi defining loader và quyết định JVM, không có deadline.

### Quy trình undeploy/redeploy an toàn hơn

```text
RUNNING
    |
    +-- reject new requests
    +-- stop/await in-flight tasks
    |
    v
STOPPING (TCCL đúng plugin khi gọi stop hooks)
    |
    +-- unregister listeners/providers
    +-- cancel schedulers/threads
    +-- clear deployment-owned caches/ThreadLocals
    |
    v
CONTEXT_CLEANUP
    |
    +-- restore TCCL trên mọi worker có liên quan
    |
    v
CLOSE_LOADER
    |
    +-- URLClassLoader.close()
    |
    v
RELEASE_REFS
    |
    +-- bỏ PluginHandle/instances/Classes/Method references
    |
    v
GC_ELIGIBLE (nếu không còn strong references)
    |
    v
MAY_UNLOAD (khi JVM thực hiện)
```

Hot reload phiên bản mới thường cần **ClassLoader mới**. Việc thay `plugin.jar` trên đĩa không làm loader cũ “định nghĩa lại” cùng một binary class name. Nếu parent đã chứa class plugin trùng tên, loader mới có thể lại delegate parent và không thực sự nhận code mới — hãy kiểm tra `getClassLoader()`.

## 15. Rủi ro nhiều plugin chạy đồng thời

Giả sử host có `PluginA` và `PluginB` với hai loaders riêng. Một request A và request B có thể chạy trên hai threads; mỗi thread chỉ được đặt TCCL của đúng plugin trong khoảng công việc plugin.

**Không lưu loader đang chạy trong static field global** rồi đổi cho mọi request:

```java
// Minh họa anti-pattern; KHÔNG dùng:
static ClassLoader CURRENT_PLUGIN_LOADER;
```

Bởi vì request A có thể đổi giá trị sang loader A trong khi request B đang cần loader B. Ngay cả nếu dùng synchronized, việc “loader hiện tại của toàn JVM” không phù hợp với nhiều plugin song song.

Pattern lựa chọn theo task:

```java
// Pseudocode dùng PluginHandle do host tự định nghĩa.
PluginHandle h = registry.resolve(pluginId);
executor.submit(() -> {
    return ContextLoaders.withContext(h.loader(), () -> {
        return h.instance().process(input);
    });
});
```

Ở đây `PluginHandle`, `registry`, `process` và `input` là **thành phần ứng dụng giả định**; đoạn này minh họa mô hình context per task. Ngoài TCCL, cần quản lý locking/lifecycle để tránh tác vụ sử dụng `h` sau khi plugin đã dừng/loader đã close.

### Có cần tạo thread riêng cho từng plugin?

Không bắt buộc. Thread pool dùng chung có thể hoạt động nếu host gói tất cả invocation bằng context scope và quản lý ownership của tác vụ. Thread pool riêng cho plugin có thể đơn giản hóa isolation/lifecycle, nhưng tốn tài nguyên và vẫn phải shutdown/restore TCCL đúng.

**Quan trọng hơn số thread là scope:** mọi callback, task, SPI lookup và cleanup phải xác định đúng loader/module context trong khoảng thời gian chúng chạy.

## 16. Debug: tìm lỗi ở đâu khi plugin không khởi động?

### 16.1 In loader, module và context tại đúng thời điểm

Có thể thêm đoạn dưới vào constructor hoặc `start()` khi có class `Entry`:

```java
ClassLoader defining = Entry.class.getClassLoader();
ClassLoader context =
        Thread.currentThread().getContextClassLoader();

System.out.println("defining=" + defining);
System.out.println("context=" + context);
System.out.println("same=" + (defining == context));
System.out.println("module=" + Entry.class.getModule());
```

Đây là **đoạn minh họa trong class `Entry`**, kết quả loader `toString()` tùy implementation nên không đưa stdout cố định. `same=false` không luôn là lỗi; chỉ là tín hiệu cần kiểm tra nếu contract plugin yêu cầu context = loader của entry.

### 16.2 Kiểm tra resource SPI được nhìn thấy

```java
ClassLoader loader =
        Thread.currentThread().getContextClassLoader();

java.util.Enumeration<java.net.URL> resources =
        loader.getResources("META-INF/services/api.Greeting");

while (resources.hasMoreElements()) {
    System.out.println(resources.nextElement());
}
```

Đây là **đoạn minh họa trong method xử lý `IOException`**, giả định `loader` khác null và application dùng classpath SPI. Có resource chứng minh descriptor **có thể được nhìn thấy**; không chứng minh provider class/constructor/dependencies đều hợp lệ.

### 16.3 Bảng lỗi và hướng chẩn đoán

| Triệu chứng | Nguyên nhân có thể | Cách sửa đúng tầng |
| --- | --- | --- |
| `ClassNotFoundException: plugin.Entry` | URL/classpath root sai, tên binary sai hoặc parent policy không phù hợp | Xác nhận `plugin/Entry.class` dưới plugin root và loader được dùng |
| `ClassCastException` sang `api.Plugin` | Plugin define bản `api.Plugin` riêng | Đưa shared API vào parent, kiểm tra `isAssignableFrom` |
| `ServiceLoader` trả 0 provider | TCCL sai hoặc thiếu `META-INF/services` / wrong resource root | Chọn explicit plugin loader, kiểm tra descriptor |
| `ServiceConfigurationError` | Descriptor sai, provider thiếu constructor/subtype hoặc initialization lỗi | Đọc cause, metadata/provider class |
| Constructor chạy với context host | TCCL được set sau `newInstance` | Set context **trước** khởi tạo |
| Startup ném exception, TCCL không restore | Thiếu `finally` | Dùng context helper và lifecycle rollback |
| Thread pool lâu lâu thấy provider plugin khác | Worker còn TCCL của request trước | Scope TCCL trong worker task, restore |
| Plugin start được nhưng callback sau đó lỗi resource | Callback được host gọi ngoài plugin context | Wrap mỗi callback ở plugin scope |
| `NoClassDefFoundError` sau close | Loader đóng sớm, dependency mới lazy-resolve hoặc plugin init từng lỗi | Giữ loader mở tới cuối lifecycle, xem cause |
| Hot reload tăng metaspace | Loader cũ vẫn reachable bởi threads/caches/registries | Xem retaining paths, TCCL, lifecycle cleanup |
| Module service không thấy provider | Layer/`uses`/`provides` không phù hợp | Dùng module-aware discovery và graph |
| `InaccessibleObjectException` khi reflection | Thiếu `opens` của named module | Sửa module access, không chỉ đổi TCCL |

Quy trình nhanh:

1. In defining loader của `api.Plugin` và `plugin.Entry`; xác nhận API shared.
2. In TCCL **ngay trong constructor/start/callback/worker** bị lỗi, không chỉ in ở main thread.
3. Nếu ServiceLoader: xác nhận `META-INF/services` ở root và dùng loader discovery đúng.
4. Nếu `Class.forName`: xác nhận overload và caller defining loader; chọn explicit plugin loader khi cần.
5. Nếu module path: kiểm tra layer, named-module service declarations và exports/opens.
6. Nếu lỗi chỉ sau undeploy: kiểm tra loader đã close khi còn callbacks/tasks hay chưa.
7. Nếu loader không unload: phân tích strong references từ thread/TCCL/cache/registry, không suy từ việc `System.gc()` không giải phóng ngay.

## 17. Phân biệt lỗi initialization và lỗi class loading

Khi debug, nhớ rằng “khởi tạo plugin” chứa nhiều thao tác:

**Load class**:

```java
Class<?> clazz = pluginLoader.loadClass("plugin.Entry");
```

Thường chưa yêu cầu static initializer chạy.

**Initialize class**:

```java
Class<?> clazz = Class.forName("plugin.Entry", true, pluginLoader);
```

Yêu cầu class initializer (nếu chưa initialized); có thể gây side effects và lỗi `ExceptionInInitializerError` nếu initializer thất bại.

**Create instance**:

```java
Object instance = clazz.getDeclaredConstructor().newInstance();
```

Yêu cầu constructor hợp lệ, class accessible theo Java/module rules và sẽ khởi tạo class nếu cần. Reflection có thể bọc exception do constructor ném trong `InvocationTargetException`.

**Start plugin**:

```java
((Plugin) instance).start();
```

Gọi code nghiệp vụ có thể tạo resource/thread/provider và cần rollback nếu lỗi.

Bốn đoạn trên là **mã minh họa trong method** với `pluginLoader`/`Plugin` đã được khai báo và exception được xử lý. Nếu `clazz` và `instance` được khai báo trong cùng scope, không lặp lại khai báo biến; tách từng bước chỉ để giải thích thao tác.

Với lỗi static initialization đầu tiên, JVM có thể báo `ExceptionInInitializerError`; những lần sử dụng class erroneous tiếp theo có thể báo `NoClassDefFoundError`. Không giải quyết bằng cách chỉ đổi TCCL sau khi class đã ở trạng thái initialization failure — phải sửa nguyên nhân và thường tạo loader/deployment mới để có một định nghĩa class mới trong lần reload hợp lệ.

## 18. Những nhận định dễ sai

| Nhận định | Giải thích |
| --- | --- |
| “`setContextClassLoader` đổi loader của class đang chạy.” | Không; defining loader của class không đổi. |
| “TCCL là parent loader mới.” | Không; `getParent()` vẫn giữ quan hệ cấu trúc. |
| “Host set TCCL một lần khi boot thì plugin nào cũng đúng.” | Không; nhiều plugin/thread có context khác nhau. |
| “Plugin load xong thì đổi TCCL không còn cần.” | Có thể cần trong constructor, start, callbacks, tasks và stop hooks. |
| “`Class.forName(String)` luôn đọc TCCL.” | Không; mặc định là loader của class caller. |
| “`ServiceLoader.load(Service.class)` dùng system loader.” | Theo Java 17 contract, nó dùng **current thread's TCCL**. |
| “TCCL của thread submit tự đi vào mọi executor worker.” | Không bảo đảm; worker có thể đã tồn tại với context khác. |
| “Đổi TCCL rồi quên restore cũng không sao.” | Có thể trộn providers, tạo lỗi multi-tenant và giữ loader sống. |
| “`close()` của URLClassLoader unload mọi class ngay.” | Không; class unloading phụ thuộc khả năng GC thu hồi defining loader. |
| “Thấy same API=true là plugin hoàn toàn tương thích.” | Chỉ chứng minh interface assignability; còn version/linking/behavior/resource và access checks. |
| “`ServiceLoader.reload()` reload plugin bytecode.” | Không; nó reset provider cache của ServiceLoader instance. |
| “TCCL đủ để sandbox plugin độc hại.” | Không; plugin cần kiểm soát trust/isolation riêng. |
| “Named module nào cũng tìm SPI giống classpath.” | Không; `uses`/`provides`/ModuleLayer có quy tắc riêng. |

## 19. Câu hỏi tự kiểm tra kèm đáp án

**1.** Vì sao plugin loader nên có parent là loader chứa `api.Plugin`?

**Đáp án:** Để plugin và host resolve cùng `Class<api.Plugin>`. Nếu mỗi loader tự define bản API cùng tên, class identity khác và host có thể không cast được.

**2.** Nếu `Entry.start()` gọi `ServiceLoader.load(Greeting.class)`, loader được chọn mặc định là gì?

**Đáp án:** TCCL của thread tại lúc gọi. Không mặc định là defining loader của `Entry` hoặc system loader, trừ khi context hiện tại thực sự trỏ tới loader đó.

**3.** Tại sao phải set TCCL trước constructor của plugin?

**Đáp án:** Constructor và class initializer có thể dùng TCCL hoặc API SPI. Nếu chỉ đổi context sau constructor, các side effects/discovery ở giai đoạn khởi tạo đã chạy với context cũ.

**4.** Nếu host đổi TCCL = plugin loader nhưng gọi `Class.forName("plugin.Entry")` ngay trong host helper, chắc chắn tìm được không?

**Đáp án:** Không. Overload một tham số dùng defining loader của class host caller. Muốn chọn plugin loader, dùng `Class.forName(name, initialize, pluginLoader)` hoặc `pluginLoader.loadClass`.

**5.** Tại sao `try/finally` quan trọng khi gọi `plugin.start()`?

**Đáp án:** Start có thể ném lỗi. `finally` vẫn restore original TCCL, tránh thread giữ context plugin trong các tác vụ host/tenant khác.

**6.** Khi `ExecutorService` có worker tồn tại sẵn, set TCCL ở thread submit đã đủ chưa?

**Đáp án:** Chưa. Phải set/restore trong **worker thread chạy task**, hoặc dùng executor/task wrapper có hợp đồng propagation rõ.

**7.** Nếu plugin tạo thread mới trong khi TCCL của thread hiện tại đang là plugin loader, điều gì có thể xảy ra?

**Đáp án:** Thread mới thường kế thừa TCCL của creator, nên có thể giữ plugin loader lâu hơn vòng đời nếu thread không được dừng. Cần quản lý task/thread và cleanup.

**8.** Vì sao ví dụ chạy `host providers after=0` sau khi khôi phục TCCL?

**Đáp án:** Host discovery lại chọn context gốc không thấy SPI descriptor nằm dưới child plugin loader. Kết quả không chứng minh loader đã GC/unload.

**9.** `URLClassLoader.close()` có nên được gọi ngay sau `plugin.start()` nếu plugin tiếp tục phục vụ request?

**Đáp án:** Không theo thiết kế chung. Những code paths tương lai có thể cần class/resource chưa load. Close sau khi stop/cleanup và không còn task sử dụng loader.

**10.** Lỗi `ClassCastException` giữa plugin `Entry` và host `Plugin` có giải quyết bằng TCCL không?

**Đáp án:** Không trực tiếp. Đó thường là vấn đề type identity/loader sharing. Cần sửa classpath/delegation để plugin dùng cùng API class với host.

**11.** Với plugin named module, phải luôn dùng `ServiceLoader.load(service, pluginLoader)` thay vì overload layer không?

**Đáp án:** Không. Cần xét module graph và API. `ServiceLoader.load(pluginLayer, Service.class)` có semantics tìm provider ở layer và ancestors, phù hợp module-aware discovery; TCCL không thay thế hoàn toàn.

**12.** Sau khi host đã close plugin loader mà vẫn không thấy metaspace giảm, nguyên nhân có thể là gì?

**Đáp án:** Thread/TCCL, plugin instances, class metadata, callback registries, caches hoặc native roots vẫn giữ strong references; JVM cũng có thể chưa thực hiện unloading. Cần kiểm tra retaining paths và GC behavior, không suy rằng close phải unload ngay.

## 20. Checklist production cho plugin startup và context

1. **Artifact:** JAR/plugin bytecode, dependencies, version và source đã được kiểm tra chưa?
2. **Shared contract:** `api.Plugin`, DTOs, service interfaces nằm trong parent loader phù hợp chưa?
3. **Entrypoint:** Host load đúng binary name qua đúng plugin loader và kiểm tra assignability chưa?
4. **Init timing:** Nếu static initialization/constructor dùng SPI, TCCL được đặt **trước init** chưa?
5. **Start hook:** Host gọi `start()` trong đúng context và chỉ đánh dấu RUNNING sau khi thành công chưa?
6. **Restore:** Có `try/finally` ở **mọi** context scope, cả exception path không?
7. **Executor:** Context được đặt trong worker thực thi chứ không chỉ thread submit chưa?
8. **Callbacks:** Listeners/timers/scheduled tasks của plugin được wrap bằng context đúng chưa?
9. **Resource lookup:** Provider descriptor/config ở đúng root; API dùng defining loader hay TCCL?
10. **Error/rollback:** Nếu constructor/start một nửa thất bại, cleanup registrations/tasks/resources được thử đầy đủ chưa?
11. **Stop order:** Reject new requests, await tasks rồi gọi `stop()` với đúng context trước close chưa?
12. **Cleanup:** TCCL, ThreadLocal, ServiceLoader cache, static registries, callbacks đã được gỡ đúng deployment?
13. **Close timing:** `URLClassLoader.close()` sau khi không còn class/resource loading đồng thời chưa?
14. **Reload:** Plugin version mới có loader mới, shared API đúng và không trùng class ở parent?
15. **Modules:** Nếu dùng ModuleLayer, đã giải quyết service discovery, `uses`/`provides`/exports/opens chưa?
16. **Isolation:** Có chính sách trust và không xem class loader/TCCL như security sandbox?
17. **Tests:** Có test provider visibility trước/trong/sau context, error path và thread pool reuse?
18. **Observability:** Có log pluginId, lifecycle phase, defining/context loader và cause để chẩn đoán?

## Kết luận

**Khởi tạo plugin** không chỉ là `loadClass` và gọi constructor. Host cần phối hợp **shared API identity, entrypoint class loading, class initialization, SPI discovery, context management, start/stop lifecycle và cleanup**.

**TCCL là context per thread**, hữu ích khi các API như `ServiceLoader.load(Service.class)` cần tìm provider nằm dưới loader plugin thay vì loader framework/host. Hãy **đặt TCCL đúng trước các thao tác cần context**, **khôi phục bằng `finally`**, đặt lại tại worker/callback thực thi thực tế và quản lý vòng đời loader như một resource sống cùng plugin. Đừng nhầm TCCL với parent/defining loader hoặc trông chờ nó giải quyết mọi lỗi classloading và JPMS.

Tài liệu chính thức Java 17:

- [Thread API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): `getContextClassLoader`, `setContextClassLoader`, kế thừa context cho thread mới.
- [ServiceLoader API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): overload chọn TCCL, SPI, lazy discovery và `reload()`.
- [ClassLoader API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): defining loader, parent delegation và `getParent()`.
- [Class API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `Class.forName` và class initialization.
- [URLClassLoader API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): `close()`, loader URL/JAR lifetime.
- [ModuleLayer API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html): named modules và module-to-loader mapping.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-architecture-006">Quy trình dừng plugin, hủy đăng ký và giải phóng tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Quy trình dừng plugin, hủy đăng ký và giải phóng tài nguyên trong kiến trúc ClassLoader

## 1. Vì sao plugin “đã tắt” vẫn còn chiếm bộ nhớ?

Một ứng dụng Java có thể cài plugin bằng `URLClassLoader` riêng, tìm implementation qua một API/SPI chung, gọi `start()` để đăng ký sự kiện, khởi tạo executor và mở file hoặc kết nối. Khi không cần plugin, ứng dụng gọi `URLClassLoader.close()` rồi tưởng tất cả class, thread và resource của plugin đã biến mất.

Thực tế, **đóng ClassLoader không đồng nghĩa dừng mã plugin hoặc unload class ngay lập tức**. Plugin vẫn có thể được giữ sống bởi:

- Callback/listener đã đăng ký trong event bus của host.
- Scheduled task, executor, thread, timer, task queue, `Future` hoặc các đối tượng runnable.
- Thread Context ClassLoader (TCCL) do thread sống lâu giữ.
- `ThreadLocal` trên thread pool, đặc biệt khi value được định nghĩa trong loader plugin.
- Static registry hoặc singleton của host giữ instance, `Class<?>`, `Method`, `MethodHandle`, lambda, proxy hoặc ClassLoader của plugin.
- MBean, shutdown hook, logging handler, JDBC driver, service provider hoặc một registry bên thứ ba không được hủy đăng ký.
- Tài nguyên chưa đóng: socket, file stream, watcher, JAR handle, connection pool, native handle.
- Request đang chạy hoặc callback chưa hoàn thành và vẫn tham chiếu code/plugin instance.

Do đó **plugin shutdown là một giao thức phối hợp vòng đời**, không phải một lệnh duy nhất. Phải dừng nguồn sinh công việc, tháo các tham chiếu từ host, chờ công việc đang chạy theo chính sách, đóng những resource do plugin sở hữu, sau đó mới đóng ClassLoader và bỏ các tham chiếu còn lại.

## 2. Phân biệt stop, close, unload và GC

| Khái niệm | Nội dung thực tế | Không bảo đảm điều gì? |
| --- | --- | --- |
| **Quiesce / stop accepting** | Không cho request/event/job mới đi vào plugin | Không tự hủy mọi task đang chạy |
| **Unregister** | Tháo callback, command handler, route, provider hoặc listener khỏi registry bên ngoài | Không tự đóng executor và socket đã mở |
| **Stop** | Yêu cầu plugin ngừng phục vụ, có thể chờ/cancel công việc theo hợp đồng | Không nhất thiết class loader được đóng |
| **Close / dispose** | Giải phóng tài nguyên thuộc sở hữu: executor, stream, connection, timer, watcher | Không chứng minh class runtime đã bị GC |
| **`URLClassLoader.close()`** | Đóng các resource mà URLClassLoader quản lý (ví dụ JAR handle đã mở), chặn khả năng nạp class/resource mới theo hợp đồng API | Không tự chấm dứt thread/callback, không gỡ class đã định nghĩa |
| **Unload class** | JVM có thể thu hồi class metadata khi các điều kiện reachability và GC cho phép | Không được bảo đảm xảy ra đúng lúc `close()` |
| **Garbage collection** | Có thể thu hồi đối tượng/loader không còn reachable | Không phải API đồng bộ “ép unload” an toàn cho ứng dụng |

**Điểm mấu chốt:** sau khi `close()` trên `URLClassLoader`, một class đã được định nghĩa vẫn có thể tồn tại, các instance và method của nó vẫn có thể được thực thi. Close tác động vào những nguồn/tài nguyên loader quản lý, không phải “kill switch” của code.

Nếu cần bảo đảm dừng **mã không tin cậy hoặc không hợp tác**, ClassLoader riêng không phải ranh giới cách ly đủ mạnh. Thực thi plugin trong process/container riêng với hợp đồng IPC và quyền phù hợp thường dễ cưỡng chế dừng hơn.

## 3. Vòng đời plugin nên được mô hình hóa bằng trạng thái rõ ràng

Một thiết kế thực tế có thể sử dụng:

```text
       install / load
NEW --------------------> LOADED
                           |
                         start()
                           v
                         ACTIVE
                           |
                    stop requested
                           v
                       QUIESCING
                           |
                no new work; drain/cancel
                           v
                       STOPPING
                           |
              unregister; close resources
                           v
                        STOPPED
                           |
                close loader; drop refs
                           v
                        DISPOSED
```

Có thể có trạng thái lỗi như `FAILED_START`, `FAILED_STOP` hoặc `LEAK_SUSPECTED`. Điều quan trọng là **không làm trạng thái trông như đã dừng hoàn toàn khi vẫn còn thread/callback hoạt động**.

Các điều kiện cần xác định:

1. **Idempotency:** gọi `stop()` hoặc `close()` nhiều lần phải không gây double-close hỏng hoặc đăng ký âm; trường hợp có exception vẫn cố cleanup phần có thể cleanup.
2. **Concurrency:** không cho đồng thời start và stop làm state không nhất quán; cần lock, atomic state hoặc state machine có kiểm thử.
3. **Ownership:** thành phần nào tạo resource thì có trách nhiệm đóng, hoặc hợp đồng chuyển ownership phải rõ ràng.
4. **Deadline:** shutdown không nên chờ vô hạn; có thời hạn cho drain/await, chính sách cancel/fallback và cách báo lỗi rõ ràng.
5. **Failure isolation:** lỗi cleanup của một plugin không được chặn cleanup những plugin/resource còn lại.
6. **No resurrection:** callback đến muộn không được tự khởi động lại plugin đang đóng.

Không có thứ tự “chuẩn duy nhất” áp dụng cho mọi framework; thứ tự phải dựa trên luồng phụ thuộc của plugin. Tuy nhiên, một quy trình stop hữu ích sẽ ưu tiên **chặn work mới trước khi đóng những resource mà work đang dùng**.

## 4. Trình tự dừng gợi ý cho một plugin đang chạy

### Bước 1 — Công bố yêu cầu dừng

Đặt trạng thái plugin thành `QUIESCING` hoặc `STOPPING` bằng thao tác đồng bộ phù hợp. API chuyển trạng thái phải được chỉ định là **một chiều** cho lượt dừng này.

Từ thời điểm ấy:

- Dispatcher không gửi request mới cho plugin.
- Scheduler/command router không cấp thêm job mới.
- Health/readiness không còn coi plugin là một instance phục vụ request bình thường.
- Việc load hoặc tạo instance mới của plugin cùng phiên bản được từ chối theo policy.

Nếu host tự tiếp tục phát lệnh sau khi plugin `close()`, việc plugin “dừng sạch” là bất khả thi.

### Bước 2 — Hủy đăng ký tại các hệ thống bên ngoài plugin

Tháo subscription/listener, route HTTP, command handler, job scheduler entry, ServiceRegistry entry và mọi registration mà host đã cấp. Một pattern tốt là API `register(...)` trả về một **handle `AutoCloseable`/`Registration`** để hủy đăng ký bằng đúng handle đó.

```java
Registration subscription = eventBus.subscribe(listener);
try {
    // Sử dụng subscription khi plugin đang ACTIVE.
} finally {
    subscription.close();  // hủy đăng ký ở host
}
```

Đây là đoạn minh họa (giả định `Registration` và `eventBus` đã được định nghĩa). Không nên chỉ gọi `listeners.clear()` **trong plugin** nếu host vẫn giữ reference tới listener gốc.

### Bước 3 — Dừng tác vụ đang chạy

- Executor: `shutdown()` để ngừng nhận task mới; chờ `awaitTermination(timeout, unit)`.
- Nếu quá deadline: cân nhắc `shutdownNow()` (gửi interrupt tới task đang chạy theo hợp đồng), rồi chờ lại.
- Task phải hợp tác với interrupt/cancellation; `shutdownNow()` **không thể cưỡng chế** một thread đang chặn trong code không hợp tác.
- Thread tự tạo phải có cờ dừng, interrupt hoặc cơ chế signal thích hợp và được `join` có deadline.
- Scheduled executor: cancel periodic jobs và kiểm tra policy đối với delayed tasks.

Không dùng `Thread.stop()` để cưỡng chế dừng; API này không an toàn. Không khóa registry dài qua `awaitTermination` nếu task cần registry để hoàn thành, vì có thể gây deadlock.

### Bước 4 — Đóng resource theo hướng ngược với lúc khởi tạo

Ví dụ khởi tạo: mở socket → tạo worker → đăng ký listener; shutdown thường làm ngược: unregister listener → stop worker → close socket. Nếu task đang cần socket, hãy drain task **trước** khi socket bị đóng; nếu chính việc đóng socket là tín hiệu phá blocking I/O, quy trình cần mô tả rõ.

Áp dụng `try-with-resources` hoặc `finally` để cleanup từng phần và giữ `suppressed exceptions` khi có nhiều lỗi. Resource có thể gồm:

- `InputStream`, `OutputStream`, `WatchService`, `JarFile`.
- `Socket` / HTTP client/connection pool.
- JDBC connections, listeners/drivers đã đăng ký, timer/scheduler.
- Logging/appender, metrics instrument callbacks, exporter.
- Native library handle hoặc plugin-specific unmanaged state.

### Bước 5 — Dọn context và registry sống lâu

Hủy `ThreadLocal` thuộc plugin trên đúng thread khi kết thúc xử lý; khôi phục TCCL khi tạm thay:

```java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();
try {
    thread.setContextClassLoader(pluginLoader);
    // Thực thi API cần context loader của plugin.
} finally {
    thread.setContextClassLoader(previous);
}
```

Snippet cần biến `pluginLoader`. TCCL là một tham chiếu mạnh; thread pool sống sau plugin có thể giữ loader qua TCCL nếu không khôi phục. `ThreadLocal.remove()` chỉ tác động đến **thread đang gọi**; host không thể tùy tiện xóa mọi ThreadLocal của thread khác từ xa một cách an toàn.

### Bước 6 — Đóng ClassLoader và bỏ tham chiếu

Khi các hoạt động đã kết thúc:

- Gọi `URLClassLoader.close()` bằng try-with-resources hoặc trong bước dispose của manager.
- Gỡ entry plugin khỏi registry/route/cache của host.
- Bỏ reference tới instance, class, reflection metadata, threads, module layer và các đối tượng plugin liên quan khi không cần nữa.
- Chuyển trạng thái sang `DISPOSED` theo các điều kiện cleanup thực sự được bảo đảm.

Sau bước này, loader/class **có thể** trở nên unreachable và được GC/unload khi JVM quyết định. Không được báo “class đã unload” chỉ vì `close()` trả về bình thường.

## 5. Ví dụ Java 17 tự chạy: ngừng nhận callback, shutdown executor, đóng URLClassLoader

Ta tách code thành **API dùng chung do host định nghĩa**, **implementation do plugin loader định nghĩa** và **host chịu trách nhiệm điều phối**. Ví dụ có một event bus đơn giản dùng một thread để giữ stdout xác định. Plugin đăng ký callback giữ reference tới `this`, tạo một worker executor và có API dừng đóng idempotent.

### 5.1. Cấu trúc thư mục

```text
src/
├── example/spi/
│   ├── HostServices.java
│   └── Plugin.java
├── example/impl/
│   └── EchoPlugin.java
└── PluginShutdownDemo.java

out/
├── host/         (host + shared SPI)
└── plugin/       (plugin implementation)
```

**Quan trọng:** lúc chạy, `out/plugin` **không** nằm trong application classpath. Implementation phải được nạp qua `URLClassLoader` riêng, còn các interface `example.spi.*` được cung cấp bởi parent application loader.

### 5.2. File `src/example/spi/HostServices.java`

```java
package example.spi;

public interface HostServices {
    Registration subscribe(Runnable listener);

    interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}
```

Đây là hợp đồng callback: lời gọi `subscribe` trả một `Registration` mà plugin phải đóng khi dừng. Host sở hữu registry, nên hủy đăng ký qua handle do host cấp giúp giảm rủi ro host còn giữ lambda của plugin.

### 5.3. File `src/example/spi/Plugin.java`

```java
package example.spi;

public interface Plugin extends AutoCloseable {
    void start(HostServices services) throws Exception;
    void stop();

    @Override
    void close() throws Exception;
}
```

Phân biệt trách nhiệm:

- `start`: tạo resource và đăng ký công việc khi bắt đầu.
- `stop`: chuyển sang không phục vụ, hủy callback/entry từ host.
- `close`: gọi `stop` nếu cần và đóng tài nguyên riêng của plugin.

Trong hệ thực tế, manager có thể có thêm `quiesce()`, `awaitIdle()` hoặc `dispose()` riêng. Ví dụ cố ý giữ API nhỏ để dễ quan sát.

### 5.4. File `src/example/impl/EchoPlugin.java`

```java
package example.impl;

import example.spi.HostServices;
import example.spi.Plugin;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class EchoPlugin implements Plugin {
    private HostServices.Registration registration;
    private ExecutorService worker;
    private boolean running;

    private void handleEvent() {
        if (running) {
            System.out.println("event=handled");
        }
    }

    @Override
    public void start(HostServices services) throws Exception {
        if (worker != null) {
            throw new IllegalStateException("Already started");
        }

        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "echo-plugin-worker");
            // Worker không giữ TCCL plugin sau khi khởi tạo.
            thread.setContextClassLoader(
                    ClassLoader.getSystemClassLoader());
            return thread;
        });

        try {
            // Buộc worker đã tạo, rồi chờ task ngắn hoàn thành.
            worker.submit(() -> {}).get();
            registration = services.subscribe(this::handleEvent);
            running = true;
            System.out.println("plugin=started");
        } catch (Exception failure) {
            if (registration != null) {
                registration.close();
                registration = null;
            }
            worker.shutdownNow();
            worker = null;
            throw failure;
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        if (registration != null) {
            registration.close();
            registration = null;
        }
        System.out.println("plugin=stopped");
    }

    @Override
    public void close() throws InterruptedException {
        stop();
        if (worker == null) {
            return;
        }

        worker.shutdown();
        if (!worker.awaitTermination(2, TimeUnit.SECONDS)) {
            worker.shutdownNow();
            if (!worker.awaitTermination(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Worker did not terminate");
            }
        }
        worker = null;
        System.out.println("plugin=resources-closed");
    }
}
```

**Điều đã được làm rõ:**

- Callback `this::handleEvent` có reference tới instance plugin. Nếu host không tháo callback, registry có thể giữ instance và cả loader liên quan.
- `stop()` không chỉ đặt `running=false` mà còn gọi `registration.close()`.
- `close()` dừng intake nếu cần, rồi shutdown executor và chờ bounded timeout.
- Worker thread được gán TCCL của system loader theo chính sách demo, tránh giữ plugin loader lâu dài chỉ qua TCCL.
- `close()` gọi lặp sau khi đã đóng worker sẽ return; `stop()` gọi lặp không in log/deregister thêm lần nữa.

**Giới hạn:** `running` ở đây chỉ được thay và đọc theo một luồng điều phối của demo; trong môi trường callback đa luồng, cần `volatile`/atomic state hoặc synchronization phù hợp để không có data race. Cơ chế `shutdownNow()` vẫn phụ thuộc task hợp tác với interrupt.

### 5.5. File `src/PluginShutdownDemo.java`

```java
import example.spi.HostServices;
import example.spi.Plugin;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PluginShutdownDemo {

    static final class EventBus implements HostServices {
        private final List<Runnable> listeners = new ArrayList<>();

        @Override
        public Registration subscribe(Runnable listener) {
            listeners.add(listener);
            return new Registration() {
                private boolean closed;

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        listeners.remove(listener);
                    }
                }
            };
        }

        void publish() {
            for (Runnable listener : List.copyOf(listeners)) {
                listener.run();
            }
        }

        int subscribers() {
            return listeners.size();
        }
    }

    public static void main(String[] args) throws Exception {
        EventBus bus = new EventBus();
        URL pluginUrl = Path.of("out/plugin").toUri().toURL();

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{pluginUrl},
                PluginShutdownDemo.class.getClassLoader())) {

            Class<?> implementation =
                    loader.loadClass("example.impl.EchoPlugin");

            Plugin plugin = (Plugin) implementation
                    .getDeclaredConstructor().newInstance();

            try {
                plugin.start(bus);
                bus.publish();
                System.out.println("subscribersBeforeStop="
                        + bus.subscribers());
            } finally {
                try {
                    plugin.stop();
                } finally {
                    plugin.close();
                }
            }

            System.out.println("subscribersAfterStop="
                    + bus.subscribers());
            bus.publish(); // Không còn listener plugin.
        }

        System.out.println("loader=closed");
    }
}
```

Host giữ tham chiếu tới `plugin` và `implementation` trong một scope hữu hạn; đóng loader ở cuối khối `try` không bảo đảm GC ngay. Trong bản production, registry/manager cần gỡ mọi reference lâu dài khi dispose và phân biệt plugin stop thành công với quá trình unload tiềm năng.

### 5.6. Biên dịch và chạy

Từ thư mục gốc của ví dụ, dùng shell hỗ trợ `mkdir -p` (Linux/macOS/Git Bash):

```bash
mkdir -p out/host out/plugin
javac --release 17 -d out/host src/example/spi/HostServices.java src/example/spi/Plugin.java src/PluginShutdownDemo.java
javac --release 17 -cp out/host -d out/plugin src/example/impl/EchoPlugin.java
java -cp out/host PluginShutdownDemo
```

Trên Windows PowerShell, tạo thư mục bằng `New-Item -ItemType Directory -Force out/host,out/plugin`, rồi chạy các lệnh `javac` và `java` còn lại. Classpath ở đây chỉ có **một root** nên không cần separator `:` hoặc `;`.

**Stdout mong đợi trên JDK 17+ khi thực thi thành công:**

```text
plugin=started
event=handled
subscribersBeforeStop=1
plugin=stopped
plugin=resources-closed
subscribersAfterStop=0
loader=closed
```

### 5.7. Vì sao kết quả có ý nghĩa?

1. `plugin=started`: implementation được loader riêng nạp và khởi tạo, worker đã chạy task ngắn, callback đã đăng ký.
2. `event=handled`: host phát sự kiện khi plugin đang hoạt động.
3. `subscribersBeforeStop=1`: registry bên ngoài plugin còn giữ đúng một callback.
4. `plugin=stopped`: plugin chuyển state và đóng registration handle.
5. `plugin=resources-closed`: executor đã ngừng và plugin xóa reference của mình tới worker.
6. `subscribersAfterStop=0`: host không giữ callback nữa; đây là **một điều kiện cần** để loại bỏ nguồn rò rỉ tham chiếu.
7. `loader=closed`: loader được đóng nhờ try-with-resources, **không khẳng định** tất cả class đã unload.

Bài thực hành cố ý **không dùng** `System.gc()` để minh họa “unload đã hoàn tất”, vì việc GC/unloading không có thời điểm xác định theo yêu cầu của ứng dụng.

## 6. Cleanup cả khi start thất bại

Startup có thể fail ở nhiều điểm:

```text
load class -> instantiate -> create executor -> open socket
           -> register listener -> schedule job -> ACTIVE
```

Nếu exception xảy ra giữa chuỗi, những resource đã tạo **vẫn phải được thu hồi**, kể cả plugin chưa đạt trạng thái ACTIVE. Vì vậy mọi bước cấp phát/đăng ký nên có rollback tương ứng, hoặc được quản lý trong một stack các cleanup actions.

Pattern minh họa:

```java
Deque<AutoCloseable> cleanup = new ArrayDeque<>();
try {
    AutoCloseable resource = openResource();
    cleanup.push(resource);

    AutoCloseable subscription = registerCallback();
    cleanup.push(subscription);

    markActive();
} catch (Exception failure) {
    while (!cleanup.isEmpty()) {
        try {
            cleanup.pop().close();
        } catch (Exception cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
    throw failure;
}
```

Đây là **pseudocode Java có API giả định** (`openResource`, `registerCallback`, `markActive` và imports chưa định nghĩa), không phải chương trình độc lập. Đối với implementation thật, hãy đảm bảo nếu startup thành công, ownership của stack cleanup được chuyển rõ ràng cho plugin/manager thay vì để object bị quên.

Bài demo ở phần 5 xử lý việc startup lỗi trong `EchoPlugin.start` bằng cách deregister và shutdown worker. Tuy nhiên, code sản phẩm cần quan tâm cả exception từ registration close, worker shutdown, external side effects và khả năng `start()` đã khởi tạo một phần trước khi throw.

## 7. Unregister là hủy reference ở nơi đã đăng ký

Một lỗi thiết kế phổ biến:

```text
Host singleton EventBus
    └── List<Listener>
           └── lambda -> PluginInstance
                            └── PluginClass
                                  └── PluginClassLoader
```

Ngay cả khi ứng dụng xóa entry `PluginManager.plugins.remove(pluginId)`, event bus vẫn giữ listener => plugin vẫn reachable. Chỉ đặt `plugin = null` ở local variable không đủ.

**Nguyên tắc:**

- Mọi `subscribe/register/addListener` cần một thao tác đối xứng `unsubscribe/unregister/removeListener`.
- Nên trả về **registration token/handle** thay vì buộc plugin đoán object nào cần remove.
- Nếu framework dùng annotation để auto-scan/register, phải có API unregister cho scope plugin khi stop.
- Nếu registry phân theo pluginId, manager có thể thực hiện `unregisterAll(pluginId)` để thu hồi khi plugin lỗi.
- Nếu callback đang chạy trong lúc unregister, cần định nghĩa rõ **drain semantics**: unsubscribe chặn callback mới hay đợi callback đang chạy? Hai khả năng khác nhau.
- Nếu plugin có một lambda listener, không tạo lambda mới rồi gọi remove và mong hai instance luôn tương đương; hãy giữ **chính handle hoặc listener instance** đã đăng ký.

### Những registry ngoài event bus

| Registry hoặc tích hợp | Thao tác thường cần khi dừng | Lý do |
| --- | --- | --- |
| HTTP route / servlet filter | Unregister route/filter, đợi in-flight requests | Không gọi plugin đã disposed |
| Scheduler/cron | Cancel job, wait/cancel executing tasks | Job có thể giữ reference tới code plugin |
| Event listener / message consumer | Unsubscribe/close consumer, drain queue | Tránh callback vào instance đã dừng |
| JDBC driver | Dỡ đăng ký driver do plugin tự đăng ký khi ownership đúng | DriverManager có thể giữ class/loader |
| JMX MBean | Unregister MBean đã đăng ký bởi plugin | MBean server sống lâu giữ implementation |
| Logging handler/appender | Remove handler rồi close | Thread/stream/cache giữ plugin |
| Service registry | Remove service implementation và provider metadata được cache | Host ngừng tra provider cũ |
| Metrics observer | Remove callback/instrument listener, close exporter | Tránh giữ lambda/plugin |
| Shutdown hook | Remove hook nếu được API cho phép và chưa shutdown | Hook Thread có thể giữ loader |
| GUI/desktop listener | Remove listener/observer | Host UI có vòng đời lâu hơn plugin |

Không phải mọi plugin đều dùng mọi registry. **Danh sách cleanup phải phản ánh ownership thực tế**, tránh gọi deregister lên resource do host chung quản lý và gây ảnh hưởng plugin khác.

## 8. Executor, thread và tác vụ đang chạy

Executor thường là nguyên nhân plugin không dừng:

```java
ExecutorService executor = Executors.newFixedThreadPool(2);
// Nếu không shutdown, worker có thể tiếp tục sống sau khi plugin "đóng".
```

Đây là snippet. Pattern cleanup cơ bản (có thể trích vào helper có owner rõ ràng):

```java
executor.shutdown();
try {
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            // Báo trạng thái FAILED_STOP, đừng khẳng định cleanup hoàn tất.
        }
    }
} catch (InterruptedException interrupted) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
```

Snippet giả định biến `executor`, import `TimeUnit` và context được phép can thiệp executor. Nếu task đang chờ network I/O hoặc khóa ứng dụng và bỏ qua interrupt, gọi `shutdownNow()` **không** bảo đảm thread đã chết.

### Dừng request và draining

Một shutdown có khả năng drain có thể:

1. Hủy route/consumer intake hoặc ngăn request mới nhận plugin.
2. Theo dõi số request đang chạy bằng counter/phaser.
3. Chờ counter về 0 tới deadline.
4. Nếu vượt deadline, hủy/cancel theo chính sách; phân biệt yêu cầu idempotent với non-idempotent.
5. Chỉ đóng connection/resource sau khi các request đã rời khỏi đoạn code cần nó.

**Không tự ý kill thread** để giữ SLA shutdown. Nếu cần cưỡng chế cứng (hard kill) đối với plugin không hợp tác, hãy cô lập plugin vào process riêng và giới hạn quyền/runtime phù hợp.

### Kiểm soát TCCL của worker

Thread tạo khi TCCL của parent đang trỏ tới plugin loader có thể **kế thừa** context loader đó. Nếu thread còn sống sau plugin, nó tiếp tục giữ strong reference tới loader. Thread factory của plugin nên đặt TCCL theo policy host hoặc khôi phục sau từng task, và phải đảm bảo không phá các API đang cần loader plugin trong suốt thời gian plugin hoạt động.

**Lưu ý:** chỉ set TCCL về system loader không giải quyết các reference khác (lambda, ThreadLocal, runnable queue). Phải dừng thread và dọn toàn bộ các kênh giữ tham chiếu.

## 9. ThreadLocal, static cache và class loader leak

`ThreadLocal<T>` dễ bị xem nhẹ. Nếu một thread pool của **host** sống suốt tiến trình còn value ThreadLocal có class do plugin loader định nghĩa, nó có thể giữ plugin loader reachable ngay cả khi manager đã bỏ plugin.

Thực hành:

- Khi plugin đặt ThreadLocal, nó nên gọi `remove()` trong `finally` của **chính thread** thực thi task.
- Đừng giữ plugin-defined object trong ThreadLocal của host worker nếu không có teardown rõ ràng.
- Với TCCL tạm thời, khôi phục TCCL trong `finally` trên từng thread.
- Nếu plugin dùng global static cache của thư viện chung, phải kiểm tra API clear/deregister theo pluginId hoặc loader scope.
- Nếu framework dùng weak keys, đừng vội kết luận đã an toàn: **value mạnh** hoặc các đường tham chiếu khác vẫn có thể giữ loader.

Snippet minh họa một task plugin (giả định `context` là `ThreadLocal<?>` thích hợp):

```java
try {
    // Gắn context vào thread khi thực hiện một request.
} finally {
    context.remove();
}
```

Đây là snippet không độc lập; không gọi `ThreadLocal.remove` từ thread khác rồi mong value trên worker được xóa.

### Có nên dùng WeakReference kiểm tra unload?

Có thể dùng `WeakReference<ClassLoader>` để quan sát **khả năng thu hồi** trong bài kiểm thử leak, nhưng:

- Phải bỏ hết strong references từ test, event bus, threads, cache và reflection artifacts.
- GC không có cam kết hoàn tất trong một khoảng thời gian cụ thể.
- `System.gc()` chỉ là gợi ý theo môi trường VM; không phải chứng nhận class đã unload.
- Một weak reference vẫn chưa bị clear **không tự chứng minh** leak nếu GC chưa thu hồi; và việc nó đã clear không chứng minh tất cả resource bên ngoài đã đóng.

Muốn chứng minh leak, nên kết hợp heap dump, class histogram, reference chains, thread dump và quan sát sau nhiều chu kỳ load–unload.

## 10. ServiceLoader, module layer và plugin reload

`ServiceLoader` có provider cache của chính instance đó: `reload()` xóa cache, **không** thay class bytes của loader cũ. Khi retire plugin, host phải bỏ provider instances/descriptors đã đăng ký và các cache giữ generation cũ. Nếu đổi binary implementation, hãy tạo loader/layer mới; **shared API/SPI** vẫn phải do loader/layer chung định nghĩa. Với JPMS, kiểm tra service declarations và references tới `ModuleLayer`, không coi việc close URLClassLoader là cơ chế unload module.

## 11. Quản lý lỗi khi shutdown: best effort nhưng không che giấu

Trong thực tế, nhiều bước có thể ném exception: unsubscribe lỗi, thread không dừng, stream close lỗi, `URLClassLoader.close` ném `IOException`.

Một shutdown manager cần tránh “fail-fast bỏ dở toàn bộ cleanup”. Cách làm phổ biến:

1. Đặt trạng thái STOPPING ngay cả khi callback đang chạy.
2. Gọi cleanup của mọi resource độc lập theo thứ tự phù hợp.
3. Gom lỗi; giữ exception chính và add các lỗi tiếp theo thành suppressed hoặc report aggregate.
4. Hết deadline thì báo `FAILED_STOP`/`PARTIAL_CLEANUP` thay vì trả thành công giả.
5. Đảm bảo các handle đã đóng được đánh dấu để không double-close.
6. Chỉ công bố `DISPOSED` khi những điều kiện mà hợp đồng yêu cầu đã đạt; nếu một worker còn sống, ghi rõ.
7. Có cơ chế buộc quản trị viên đánh giá/restart process khi trạng thái không thể hồi phục.

### Pattern hợp nhất lỗi đóng tài nguyên

```java
Exception primary = null;
for (AutoCloseable resource : ownedResources) {
    try {
        resource.close();
    } catch (Exception error) {
        if (primary == null) {
            primary = error;
        } else {
            primary.addSuppressed(error);
        }
    }
}
if (primary != null) {
    throw primary;
}
```

Đây là đoạn minh họa (giả định `ownedResources` là danh sách đã xác định thứ tự cleanup), không phải một chương trình độc lập. Trong production cần xử lý cả `Error` nghiêm trọng theo chiến lược hệ thống, không bắt rộng `Throwable` để tiếp tục như không có lỗi. Điều quan trọng là **đừng để lỗi đóng resource đầu tiên che mất các resource còn lại**.

### Đóng loader trong tình trạng plugin stop thất bại

Nếu `stop()` không đạt deadline, có thể vẫn cần đóng JAR handles và ngừng route mới. Nhưng đừng xem `URLClassLoader.close()` là phương pháp giết những thread đang chạy. Trạng thái hệ thống cần báo còn hoạt động/rò rỉ tiềm năng; trong plugin untrusted hoặc không hợp tác, restart process hoặc isolation boundary là biện pháp quản lý đáng tin hơn.

## 12. Thiết kế plugin API có khả năng shutdown tốt

Một hợp đồng lớn hơn có thể phân vai trò rõ:

```java
public interface ManagedPlugin extends AutoCloseable {
    void prepare(PluginContext context) throws Exception;
    void start() throws Exception;
    void quiesce();
    boolean awaitIdle(long timeout, TimeUnit unit)
            throws InterruptedException;
    void stop();
    @Override
    void close() throws Exception;
}
```

Đây là **interface minh họa**, cần import `TimeUnit` và định nghĩa `PluginContext` mới có thể biên dịch; không phải API chuẩn của JDK.

Có thể bổ sung:

- `PluginHandle` chứa ID/version, loader, registrations và resource ownership.
- `PluginContext.register(...)` trả cleanup token, do manager giám sát.
- Event bus của host có khả năng unregister theo pluginId và drain.
- Router áp dụng atomic switch từ phiên bản cũ sang mới.
- Manager duy trì trạng thái transition và timeout monotonic.
- Metrics: active requests, remaining listeners, alive threads, executor termination, handles open, dispose latency.
- Audit logs: pluginId, version, loader identity, shutdown reason, lỗi cleanup.

**Idempotency nên được kiểm thử**, không chỉ ghi trong tài liệu: gọi stop hai lần, close hai lần, stop sau lỗi start, stop trong lúc callback chạy, và start/stop đồng thời.

## 13. Cách phát hiện “classloader leak” sau nhiều lần reload

Không thấy exception không chứng minh shutdown sạch. Triệu chứng thường thấy:

- Số instance `URLClassLoader` hoặc loader riêng tăng theo mỗi lần reload.
- Metaspace tăng đều và không ổn định trở lại sau những chu kỳ GC đầy đủ.
- Nhiều thread còn tên plugin cũ.
- Registry chứa listener/route/service đã thuộc phiên bản cũ.
- JAR file không thể thay/xóa trên một số nền tảng vì còn handle mở.
- Thread dump xuất hiện stack của class plugin cũ.
- Heap dump chỉ ra các reference chain từ GC root tới loader cũ.

### Quy trình điều tra

1. **Đo baseline** trước khi load: số thread, subscriptions, file handles, Metaspace và loader.
2. Load plugin, chạy một số request và quan sát class/loader được định nghĩa.
3. Gọi shutdown theo trình tự đã mô tả; kiểm tra count của registry về baseline.
4. Kiểm tra executor thread còn sống, jobs còn queued, TCCL của worker sống lâu, ThreadLocal và shutdown hooks.
5. Kiểm tra `ServiceLoader` cache, MBean/JDBC/logging registrations, URLClassLoader đã close.
6. Bỏ tất cả reference có chủ đích tới plugin/loader trong manager.
7. Chạy lại load–stop–dispose nhiều vòng; tìm tăng trưởng tích lũy bất thường.
8. Dùng heap dump/histogram và GC-root path để xem loader cũ bị ai giữ. Không xem một lần `System.gc()` là phép đo quyết định.

Có thể xem thông tin JVM bằng các lệnh tham khảo:

```bash
jcmd <pid> Thread.print
jcmd <pid> GC.class_histogram
jcmd <pid> VM.classloader_stats
```

Các lệnh này phụ thuộc phiên bản JDK, quyền attach và khả năng JVM hỗ trợ. Thay `<pid>` bằng tiến trình thật; không chạy heap dump lớn ở production nếu chưa đánh giá chi phí.

Có thể bật unified logging khi JVM/JDK phù hợp:

```bash
java -Xlog:class+unload=info -cp app/classes com.example.Main
```

Đây là cú pháp tham khảo; sự kiện unload chỉ xuất hiện khi VM thực sự unload class, không gắn chặt với thời điểm `URLClassLoader.close()`. Log không thay thế việc kiểm tra tài nguyên ngoại JVM.

## 14. Những sai lầm thường gặp

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Chỉ gọi `URLClassLoader.close()` rồi kết luận plugin đã dừng | Callback/thread còn hoạt động, loader có thể vẫn reachable | Stop ingress, unregister, drain/close, rồi đóng loader |
| Chỉ xóa plugin khỏi một `Map` quản lý | Event bus/static cache còn giữ reference | Hủy đăng ký **tại nơi đăng ký** bằng handle |
| Dùng `System.gc()` làm thao tác shutdown | Không bảo đảm class unload; resource bên ngoài còn mở | Đóng resource rõ ràng và bỏ references |
| Stop nhận thêm request đến tận lúc đóng socket | In-flight/new requests thấy trạng thái resource hỏng | Quiesce ingress trước, drain rồi close |
| Dùng `shutdownNow()` và luôn coi là đã dừng | Task có thể không chịu interrupt | Chờ deadline, báo partial failure, cô lập plugin khi cần |
| Không dọn TCCL ở host thread pool | Thread sống lâu giữ loader cũ | Khôi phục TCCL trong `finally` |
| Dọn ThreadLocal từ sai thread | Value trên worker vẫn còn | Remove trong đúng worker/task lifecycle |
| Ghi `running=false` nhưng giữ callback trong registry | Host vẫn giữ plugin instance | Unregister; hỗ trợ in-flight callback race |
| Stop ném lỗi rồi bỏ qua cleanup còn lại | Leak resource khác | Aggregate lỗi, cleanup best effort có trách nhiệm |
| Reuse cùng loader để “reload class” | Binary name đã định nghĩa không tự thay | Tạo loader/module layer mới khi cần |
| API/SPI bị plugin định nghĩa lại | Host không cast được provider/plugin | Đặt API chung ở parent/module chia sẻ |
| Plugin đóng tài nguyên do host sở hữu | Làm hỏng plugin khác | Theo dõi ownership, token riêng cho mỗi plugin |

## 15. Bài tập tự kiểm tra

**Câu 1.** Gọi `URLClassLoader.close()` thành công. Có chắc những class do nó định nghĩa đã bị JVM unload chưa?

**Đáp án:** Không. Close giải phóng các tài nguyên loader quản lý và không phải lệnh bắt buộc JVM unload class; reachability và GC quyết định khả năng unload.

**Câu 2.** Vì sao event bus callback có thể gây ClassLoader leak?

**Đáp án:** Host event bus sống lâu giữ callback; callback/lambda có thể giữ plugin instance; plugin instance liên quan class do loader plugin định nghĩa, làm loader reachable.

**Câu 3.** Nên unregister callback trước hay sau khi đóng database connection mà callback đang sử dụng?

**Đáp án:** Thường quiesce/unregister callback mới, chờ hoặc hủy các callback đang chạy theo hợp đồng, rồi đóng connection. Cần định nghĩa rõ in-flight semantics.

**Câu 4.** `ExecutorService.shutdownNow()` có luôn giết worker thread tức thì không?

**Đáp án:** Không. Nó cố dừng các task và gửi interrupt; task có thể không hợp tác. Phải chờ bounded timeout và báo trạng thái thực.

**Câu 5.** `ThreadLocal.remove()` trên main thread có xóa giá trị của worker thread không?

**Đáp án:** Không. ThreadLocal state gắn với từng thread; cần cleanup trên đúng thread/task.

**Câu 6.** Tại sao đặt TCCL về loader host trước khi plugin thread kết thúc có ích?

**Đáp án:** Có thể loại bỏ một strong reference từ thread sống lâu tới loader plugin. Nhưng nó không thay thế việc dừng thread hoặc dọn những reference khác.

**Câu 7.** Plugin mới có class cùng tên và JAR đã đổi. Có thể dùng loader cũ và gọi `loadClass` lần nữa để lấy bytes mới không?

**Đáp án:** Không theo cơ chế nạp lớp thông thường. Cùng defining loader không tự định nghĩa lại class cùng binary name; thường cần loader mới và lifecycle handoff.

**Câu 8.** Một plugin stop lỗi nhưng các resource khác vẫn có thể đóng. Manager nên làm gì?

**Đáp án:** Tiếp tục cleanup phù hợp, gom/ghi nhận các lỗi, không báo dispose hoàn tất giả, và đánh dấu partial failure hoặc yêu cầu vận hành can thiệp.

**Câu 9.** `subscribersAfterStop=0` trong ví dụ chứng minh gì và không chứng minh gì?

**Đáp án:** Chứng minh event bus của demo không còn giữ callback ấy sau stop; không chứng minh mọi reference trong JVM đều đã mất hoặc class đã unload.

**Câu 10.** Vì sao ClassLoader riêng không đủ để kill một plugin tùy ý khi nó chạy vòng lặp vô hạn?

**Đáp án:** Loader chỉ quản lý việc định nghĩa/tìm class và một số resource nạp; nó không cung cấp cơ chế cưỡng chế dừng thread/code không hợp tác. Cần isolation/process boundary cho hard stop.

## 16. Checklist triển khai và vận hành

- [ ] Mỗi plugin có state rõ: ACTIVE → QUIESCING → STOPPING → STOPPED → DISPOSED.
- [ ] Đóng ingress trước khi resource plugin bị giải phóng.
- [ ] Mọi registration có unsubscribe handle; registry host hết tham chiếu sau stop.
- [ ] Có xử lý request/callback đang chạy và timeout/drain policy.
- [ ] Shutdown executor/worker và chờ bounded timeout; phản ánh đúng partial failure.
- [ ] Dọn ThreadLocal/TCCL trên đúng thread; không bỏ worker lâu dài giữ plugin loader.
- [ ] Đóng file/socket/connection/watchers/logging/MBeans/JDBC registrations thuộc plugin.
- [ ] Quản lý exceptions trong cleanup, giữ causes/suppressed errors.
- [ ] Plugin stop/close idempotent trong các đường lỗi và gọi lặp.
- [ ] Close URLClassLoader sau cleanup; không coi close tương đương unload.
- [ ] Xóa tham chiếu plugin/class/loader/service provider cũ khỏi mọi registry/cache host.
- [ ] Test restart/hot reload nhiều vòng, kiểm tra threads/listeners/resources về baseline.
- [ ] Kiểm tra heap/thread dump và GC root path khi nghi loader leak.
- [ ] Với plugin không tin cậy/không hợp tác, dùng cách ly tiến trình và chính sách cưỡng chế phù hợp.

## 17. Kết luận và tham khảo

Dừng plugin đúng cách là **một quy trình đảo ngược các phụ thuộc đã tạo lúc start**: ngừng nhận work mới → hủy đăng ký → hoàn tất/hủy các hoạt động đang chạy → đóng resource → close loader → bỏ mọi tham chiếu còn lại. Hủy đăng ký là bước quan trọng nhất để host không giữ plugin bằng callback hoặc registry; shutdown executor và dọn context giúp tránh thread giữ class loader sống lâu.

**`URLClassLoader.close()` không phải lệnh unload class.** Đây chỉ là một phần của việc thu hồi tài nguyên loader; khả năng JVM unload class phụ thuộc vào reachability và GC. Phân biệt trạng thái shutdown của plugin với việc thu hồi class metadata giúp viết ứng dụng có lifecycle chính xác, dễ debug và an toàn hơn.

Tài liệu chính thức:

- Java SE 17 `URLClassLoader` (`close()` và hành vi nạp lớp): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- Java SE 17 `ClassLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 `ExecutorService` (`shutdown`, `shutdownNow`, `awaitTermination`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- Java SE 17 `ThreadLocal` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html
- Java SE 17 `ServiceLoader` (`reload()` và provider cache): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- JVMS 17 §5, *Loading, Linking, and Initializing*: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-architecture-007">Thay phiên bản plugin bằng loader mới</a>

<details>
<summary>Xem chi tiết</summary>

# Thay phiên bản plugin bằng loader mới trong Java

> **Java Core → ClassLoader → Kiến trúc plugin.** Khi một plugin được cập nhật từ phiên bản V1 sang V2, cách đúng để có **runtime classes mới** thường là tạo **class loader thế hệ mới**, nạp và kiểm tra phiên bản mới, chuyển luồng request sang thế hệ mới, rồi dừng và giải phóng thế hệ cũ. Không dùng `defineClass` lần thứ hai trong loader cũ, không kỳ vọng `URLClassLoader.close()` tự unload mọi class, và không đóng loader trước khi những tác vụ đang chạy với plugin cũ hoàn tất.

## 1. Câu trả lời ngắn

Quy trình khuyến nghị:

~~~text
     Host + Shared API (sống xuyên nhiều lần cập nhật)
                      |
                      v
                Plugin Registry
                      |
       phiên bản đang active: Generation V1
                      |
   (1) chuẩn bị V2 trong URLClassLoader MỚI
   (2) verify artifact / load / instantiate / health check
   (3) atomic routing cutover: active = V2
   (4) không nhận yêu cầu mới cho V1
   (5) drain in-flight V1 / stop timers / unregister callbacks
   (6) close V1 instances và V1 loader-owned resources
   (7) bỏ strong references tới V1
                      |
                      v
            Generation V2 phục vụ
~~~

**Bất biến quan trọng:**

1. Mỗi runtime class có định danh theo **(binary name, defining loader)**. Class `plugin.impl.Handler` do loader V1 định nghĩa và `plugin.impl.Handler` do loader V2 định nghĩa là **hai runtime types khác nhau** dù tên giống hệt.
2. Các **shared API types** dùng để giao tiếp host↔plugin phải được định nghĩa bởi **một loader chung** (thường là host/parent), nếu muốn cast và gọi interface bình thường.
3. Không sửa JAR trên disk rồi đợi JVM tự dùng bytecode mới cho một `Class<?>` đã có. JVM không coi việc đổi file là lệnh redefine class.
4. Thời điểm **publish V2** và thời điểm **retire V1** khác nhau. Nếu có request V1 đang chạy, không được close resources/loader V1 một cách thiếu phối hợp.
5. `URLClassLoader.close()` giải phóng các file/JAR handles thuộc loader; các lớp và instances đã tạo **có thể vẫn hoạt động** nếu được giữ tham chiếu. Không có deadline chắc chắn cho class unloading.
6. Nếu chuẩn bị V2 thất bại, **giữ V1 làm active**; nếu V2 đã publish nhưng cleanup V1 thất bại, đó là một **retirement failure**, không có nghĩa V2 chưa được publish.

## 2. Vì sao phải thay loader thay vì gọi `loadClass` lại?

Với `ClassLoader` thông thường, load một binary name nhiều lần bằng cùng loader phải giữ **cùng runtime `Class<?>`**. Ví dụ:

~~~java
Class<?> first = loader.loadClass("plugin.impl.Handler");
Class<?> again = loader.loadClass("plugin.impl.Handler");

System.out.println(first == again); // true nếu cả hai thành công
~~~

Đây là **snippet** cần biến `loader`. Việc bạn build lại JAR và ghi đè file `Handler.class` không khiến `again` trở thành runtime class mới, vì loader đã nạp tên đó.

Cố gọi `defineClass` lần thứ hai với cùng binary name **trong chính defining loader** sẽ bị từ chối (thường là `LinkageError`). `resolveClass` chỉ yêu cầu linking, không reset trạng thái hay static initializer. `Class.forName` cũng không phải reload API.

Ngược lại:

~~~java
Class<?> oldType = loaderV1.loadClass("plugin.impl.Handler");
Class<?> newType = loaderV2.loadClass("plugin.impl.Handler");

System.out.println(oldType.getName().equals(newType.getName())); // true
System.out.println(oldType == newType);                            // false
~~~

**Dòng cuối chỉ chắc chắn false nếu mỗi loader thực sự tự define bản class của mình.** Nếu hai loader cùng delegate tên `plugin.impl.Handler` về parent, cả hai có thể trả cùng `Class<?>`. Khi thiết kế hot swap phải biết **defining loader** thực tế, không chỉ tạo hai đối tượng loader có tên khác nhau.

### 2.1. Phân biệt versioned runtime class với JVM instrumentation

Java agent/instrumentation có `redefineClasses`/`retransformClasses` trong những giới hạn được đặc tả, chủ yếu để thay method bytecode trên những classes đã được nạp. Đó là cơ chế **khác** và không phải cách phổ quát để cập nhật một plugin với thay đổi API, constructor, fields, module/package layout hoặc dependencies.

Với plugin architecture ứng dụng thông thường, **một loader mới cho một generation mới** giúp cô lập static fields, dependency graph và class identity rõ ràng hơn. Nhưng loader mới tự nó **không** thực hiện data migration, switch routing, cleanup hay đảm bảo thread-safety.

## 3. Ranh giới host ↔ plugin: giữ đúng một shared contract

Giả sử host biết interface:

~~~java
package api;

public interface Plugin extends AutoCloseable {
    String version();

    @Override
    default void close() { }
}
~~~

**Tệp `api/Plugin.java`** trên Java 17+; ở production, `close()` có thể cần giải phóng resources và có contract lỗi rõ ràng. `Plugin` này phải được nạp bởi **shared/host loader** mà cả host và plugin cùng sử dụng.

Kiến trúc:

~~~text
                          Host loader
                    defines api.Plugin
                         /         \
                        /           \
              Plugin loader V1    Plugin loader V2
              defines:             defines:
              plugin.impl.Versioned plugin.impl.Versioned
              libraries V1         libraries V2

           Plugin.class của host == Plugin.class của V1
           Plugin.class của host == Plugin.class của V2

           Versioned.class ở V1 != Versioned.class ở V2
~~~

Quy tắc:

- **Shared contracts:** interfaces, DTOs, annotations và exceptions thật sự đi qua boundary nên ở host/shared loader với versioning rõ ràng.
- **Implementation và private dependencies:** ở loader của từng plugin generation; có thể khác version giữa V1 và V2.
- **Không đóng gói lại API của host vào plugin JAR** theo cách child-first loader tự define `api.Plugin`, nếu host cần cast plugin về `api.Plugin`.
- **Không chia sẻ bừa object của private class V1 với V2:** `plugin.impl.Versioned` của V1 không cast được thành cùng-named `Versioned` của V2.
- Nếu cần giao tiếp giữa các generations, dùng **shared types**, stable primitives, schema/messages hoặc serialization có version rõ ràng; không dùng Java class name làm bằng chứng tương thích.

### 3.1. Parent-first và phiên bản implementation

`URLClassLoader` mặc định tìm ở **parent trước** rồi mới tới URLs của child. Nếu vô tình đưa cả `plugin.impl.Versioned` vào host classpath, loader V1 và V2 có thể trả **class đã được parent định nghĩa**: tưởng đã swap nhưng vẫn chạy code cũ.

Với kiến trúc đơn giản:

- Host classpath chỉ có **shared API + host runtime**.
- Plugin implementation JAR chỉ đưa vào URLs của loader generation.
- Parent của plugin loader là defining loader của shared API.
- Nếu cần child-first để cô lập thư viện version khác nhau, phải giới hạn **theo package**, không làm child-first toàn bộ `java.*` hoặc shared API.

Luôn kiểm tra:

~~~java
System.out.println(implementationType.getClassLoader());
System.out.println(Plugin.class.isAssignableFrom(implementationType));
System.out.println(implementationType.getModule());
~~~

Đây là snippet với `implementationType` đã có. Một class trùng tên mà loader khác thì vẫn khác runtime identity.

## 4. Thực hành Java 17+: hot swap V1 → V2 bằng loader mới

Thí nghiệm gồm một shared API, hai JAR chứa **cùng một binary name** nhưng method trả hai phiên bản, và một host dùng `ReentrantReadWriteLock` để đảm bảo **request đồng bộ** không chạy vào lúc generation cũ đang được thay/đóng.

### 4.1. Cấu trúc thư mục

~~~text
plugin-swap-lab/
├── src/
│   ├── api/api/Plugin.java
│   ├── v1/plugin/impl/Versioned.java
│   ├── v2/plugin/impl/Versioned.java
│   └── host/HostMain.java
└── build/                         (sinh khi chạy lệnh)
    ├── api/
    ├── v1/
    ├── v2/
    ├── host/
    ├── v1.jar
    └── v2.jar
~~~

Tệp **`src/api/api/Plugin.java`**:

~~~java
package api;

public interface Plugin extends AutoCloseable {
    String version();

    @Override
    default void close() { }
}
~~~

Tệp **`src/v1/plugin/impl/Versioned.java`**:

~~~java
package plugin.impl;

import api.Plugin;

public final class Versioned implements Plugin {
    @Override
    public String version() {
        return "v1";
    }
}
~~~

Tệp **`src/v2/plugin/impl/Versioned.java`**:

~~~java
package plugin.impl;

import api.Plugin;

public final class Versioned implements Plugin {
    @Override
    public String version() {
        return "v2";
    }
}
~~~

Đây là **hai source files nằm ở hai cây thư mục khác nhau**, có **cùng package, tên class** và **khác implementation**. Chúng phải được biên dịch thành **hai JAR độc lập**, không cùng một output directory.

### 4.2. Host quản lý generation và chuyển routing

Tệp **`src/host/HostMain.java`**:

~~~java
import api.Plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class HostMain {

    static final class Generation implements AutoCloseable {
        final URLClassLoader loader;
        final Plugin plugin;
        final Class<?> type;

        private Generation(
                URLClassLoader loader,
                Plugin plugin,
                Class<?> type) {
            this.loader = loader;
            this.plugin = plugin;
            this.type = type;
        }

        static Generation open(Path jar) throws Exception {
            URL url = jar.toAbsolutePath().toUri().toURL();
            URLClassLoader loader = new URLClassLoader(
                new URL[] {url},
                Plugin.class.getClassLoader());

            try {
                Class<?> type =
                    loader.loadClass("plugin.impl.Versioned");
                Plugin instance = Plugin.class.cast(
                    type.getDeclaredConstructor().newInstance());
                return new Generation(loader, instance, type);
            } catch (Exception | LinkageError failure) {
                try {
                    loader.close();
                } catch (IOException closeError) {
                    failure.addSuppressed(closeError);
                }
                throw failure;
            }
        }

        @Override
        public void close() throws Exception {
            // Đóng plugin trước, rồi đóng file/JAR handles của loader.
            // try-with-resources giữ suppressed exceptions nếu cùng lỗi.
            try (URLClassLoader closing = loader) {
                plugin.close();
            }
        }
    }

    static final class Registry implements AutoCloseable {
        private final ReentrantReadWriteLock lock =
                new ReentrantReadWriteLock(true);
        private Generation active;

        Registry(Generation initial) {
            active = initial;
        }

        String request() {
            lock.readLock().lock();
            try {
                return active.plugin.version();
            } finally {
                lock.readLock().unlock();
            }
        }

        Class<?> activeType() {
            lock.readLock().lock();
            try {
                return active.type;
            } finally {
                lock.readLock().unlock();
            }
        }

        void replace(Path newJar) throws Exception {
            // PREPARE: làm ngoài write lock để request V1 vẫn chạy.
            Generation prepared = Generation.open(newJar);

            Generation previous;
            lock.writeLock().lock();
            try {
                previous = active;
                active = prepared;  // CUTOVER
            } finally {
                lock.writeLock().unlock();
            }

            // Khi lấy được write lock, mọi request() cũ dưới
            // read lock đã hoàn tất; giờ có thể retire V1.
            if (previous != null) {
                previous.close();
            }
        }

        @Override
        public void close() throws Exception {
            Generation previous;
            lock.writeLock().lock();
            try {
                previous = active;
                active = null;
            } finally {
                lock.writeLock().unlock();
            }
            if (previous != null) {
                previous.close();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try (Registry registry = new Registry(
                Generation.open(Path.of("build/v1.jar")))) {
            Class<?> oldType = registry.activeType();

            System.out.println("before=" + registry.request());
            registry.replace(Path.of("build/v2.jar"));

            Class<?> newType = registry.activeType();
            System.out.println("after=" + registry.request());
            System.out.println("same implementation class="
                    + (oldType == newType));
            System.out.println("shared contract="
                    + (Plugin.class == newType.getInterfaces()[0]));
        }
    }
}
~~~

### 4.3. Biên dịch và chạy

Từ thư mục `plugin-swap-lab`, dùng **POSIX shell** (Linux/macOS, Git Bash) và JDK 17 trở lên:

~~~bash
mkdir -p build/api build/v1 build/v2 build/host

# API nằm trong host classpath.
javac --release 17 -d build/api src/api/api/Plugin.java

# Hai implementation cùng tên, build riêng.
javac --release 17 -cp build/api -d build/v1 \
    src/v1/plugin/impl/Versioned.java
javac --release 17 -cp build/api -d build/v2 \
    src/v2/plugin/impl/Versioned.java

jar --create --file build/v1.jar -C build/v1 .
jar --create --file build/v2.jar -C build/v2 .

# Host KHÔNG được cho v1.jar/v2.jar vào classpath.
javac --release 17 -cp build/api -d build/host \
    src/host/HostMain.java

java -cp "build/api:build/host" HostMain
~~~

Trên Windows PowerShell/CMD, thay dấu phân tách classpath bằng `;` và tạo thư mục bằng lệnh phù hợp. Lệnh chạy:

~~~powershell
java -cp "build/api;build/host" HostMain
~~~

**Stdout đã được kiểm chứng với OpenJDK 21 khi biên dịch `--release 17`:**

~~~text
before=v1
after=v2
same implementation class=false
shared contract=true
~~~

Phân tích:

1. `v1.jar` được define bởi `URLClassLoader` thế hệ V1. `Plugin` là shared type định nghĩa bởi loader của host.
2. `Generation.open(v2.jar)` tạo **một URLClassLoader mới**, tìm cùng tên `plugin.impl.Versioned` rồi tạo object mới. Nó không đụng tới `Class<?>` của V1.
3. `Registry.replace` chỉ nhận write lock sau khi chuẩn bị V2 thành công, thay reference `active`, rồi close resources V1.
4. `oldType != newType` vì hai implementation classes do hai defining loaders khác nhau tạo.
5. `Plugin.class == newType.getInterfaces()[0]` vì implementation V2 sử dụng đúng shared API do parent loader định nghĩa.
6. `oldType` vẫn là biến local giữ `Class<?>` của V1. Do đó demo **không chứng minh unload** sau `close()`; nó chỉ chứng minh version swap và type identity.

**Giới hạn lab rất quan trọng:** Code là ví dụ **synchronous, single-administrator** với operations `request`/`replace`/`close`. Nó **chưa** xử lý nhiều thread cùng gọi `replace`/`close`, trạng thái STOPPING, plugin tự tạo background jobs, migration state, callback đa luồng hay policy timeout. Không nên copy thẳng vào sản phẩm internet-facing mà thiếu các phần lifecycle production ở các mục sau.

### 4.4. Tại sao đọc/ghi bằng lock giải quyết race trong ví dụ?

Trong `request()`, read lock được giữ cho **toàn bộ lời gọi vào plugin**. Khi `replace` lấy được write lock, không còn request cũ đang giữ read lock; việc đổi `active` diễn ra tại một điểm tuyến tính rõ. Một request mới đến sau write lock sẽ dùng V2.

Đây là trade-off: request có thể chờ trong lúc cutover, và một request V1 chạy rất lâu sẽ khiến write lock chờ lâu. Tuy nhiên example không đóng V1 loader giữa chừng. Trong hệ throughput cao, có thể thay read/write lock bằng generation **leases/reference counts** có giao thức retire atomic, nhưng không được dùng một reference swap đơn độc rồi close vội.

## 5. Thiết kế vòng đời production: chuẩn bị → publish → retire

Một plugin thế hệ nên có **state machine** tối thiểu:

~~~text
NEW
 |
 +-> PREPARING
 |      |
 |      +-> PREPARED -- health-check --> READY
 |      |                            |
 |      |                            v
 |      |                         ACTIVE
 |      |                            |
 |      |                            v
 |      |                         DRAINING
 |      |                            |
 |      |                            v
 |      |                         CLOSED
 |      |
 |      +-> FAILED -> CLEANUP -> CLOSED
 |
 +-> FAILED
~~~

Không phải API có sẵn trong JDK; đây là **quy ước container/host** cần hiện thực bằng lock, immutable generation descriptor và transitions được phép.

### 5.1. PREPARE — xây V2 mà không phá V1

Một bước chuẩn bị đầy đủ thường gồm:

1. Nhận artifact V2 từ một **đường dẫn versioned/immutable** (ví dụ tên file chứa hash hoặc version, không ghi đè v1.jar đang chạy).
2. Kiểm tra checksum/signature nguồn, content trust, minimum Java runtime version và dependency compatibility.
3. Tạo `URLClassLoader`/custom loader mới, parent là shared API loader, giữ rõ danh sách JAR dependencies.
4. Nạp entry point của V2; kiểm tra `Plugin.class.isAssignableFrom(implType)` bằng **runtime Class identity**, không chỉ so tên.
5. Khởi tạo plugin/Dependency Injection context, verify cấu hình, kết nối cần thiết và **health check** trong phạm vi tài nguyên có thể rollback.
6. Đảm bảo callback/resources tạo trong PREPARE có cơ chế cleanup nếu bất kỳ bước nào thất bại.
7. Chưa publish V2 tới request router cho đến khi trạng thái READY.

**Cảnh báo:** `ClassLoader.loadClass(name)` tự nó không bắt buộc chạy static initializer; nhưng `new`, `Class.forName(name,true,loader)`, constructor/DI và một số reflection APIs có thể kích hoạt `<clinit>` và side effects. Chuẩn bị V2 có thể tiêu thụ tài nguyên hoặc gọi external services **trước cutover**. Vì vậy PREPARE không được được quảng bá là “chỉ kiểm tra, tuyệt đối không có tác dụng phụ”.

### 5.2. COMMIT/PUBLISH — điểm chuyển traffic

Khi V2 READY, host thay **active-generation reference** bằng thao tác atomic hoặc một critical section được bảo vệ. Mỗi request phải có quy ước sử dụng **đúng một generation** trong suốt operation. Không đọc active lúc đầu, rồi đọc lại giữa request và vô tình kết hợp class V1 với state/callback của V2.

Ở lab mục 4, write lock làm điểm publish rõ ràng. Trong một hệ nhiều instance/process, một `AtomicReference` ở một JVM **không** làm cutover atomic trên toàn cluster; cần load balancer/routing/version coordination và rollout/rollback policy cấp hệ thống.

### 5.3. RETIRE — dọn V1 khi không còn công việc cũ

Khi V2 active:

1. Đánh dấu V1 là **DRAINING** và không cấp handle/request mới cho V1.
2. Chờ các **leases/in-flight requests** của V1 kết thúc hoặc cancel theo timeout/policy; không ép đóng khi class/resource của V1 đang được tìm.
3. Stop plugin-owned timers, executors, watchers và long-running background jobs.
4. Unregister event listeners, MBeans, HTTP routes, scheduled callbacks, ServiceLoader/DI caches và những registry host đang giữ objects V1.
5. Gọi `close()`/`stop()` lifecycle của V1 provider instances theo thứ tự tài nguyên phù hợp.
6. Close `URLClassLoader` V1 và file/JAR handles thuộc nó, xử lý `IOException` có thể có suppressed exceptions.
7. Bỏ strong references còn giữ `Class<?>`, `Method`, plugin objects, classloader và `ThreadLocal` values; restore TCCL về loader trước đó.
8. Theo dõi GC/class unloading một cách **quan sát được**, không dựa `System.gc()` để hứa unload ngay.

**Không đóng parent/shared loader** khi V1 kết thúc: parent vẫn phục vụ host và V2.

## 6. Lỗi concurrency tinh vi: `AtomicReference` không tự bảo vệ lifetime

Mẫu dễ sai:

~~~java
// PSEUDOCODE: sai nếu retire V1 đồng thời.
Generation generation = active.get();
return generation.plugin.call();
~~~

Song song, admin có thể làm:

~~~java
Generation old = active.getAndSet(newGeneration);
old.close();
~~~

Interleaving lỗi:

~~~text
Request R                         Admin
  g = active.get() => V1
                                    active = V2
                                    V1.close()
  g.plugin.call()                 <-- gọi V1 sau khi đã close
~~~

**Atomic swap chỉ bảo vệ reference**, không cấp “quyền sử dụng” (lifetime lease) cho request đã đọc được V1. Nếu V1 close làm đóng network client, executor hoặc resource mà method cần, request sẽ lỗi dù `Class<?>` V1 vẫn tồn tại.

Có hai hướng thiết kế:

- **Read/write lock trên toàn synchronous call:** như lab; update lấy write lock rồi mới close. Đơn giản, dễ chứng minh, nhưng request dài giữ read lock và kéo dài cutover.
- **Generation lease/reference counting đúng giao thức:** request phải **acquire lease atomically với trạng thái chưa-retired**; sau đó release trong `finally`. Admin chuyển active, đánh dấu V1 retired, đợi số lease còn lại về 0, rồi mới close. Nếu acquire V1 thất bại sau khi V1 bị retire, request retry lấy V2. Không chỉ `AtomicInteger.incrementAndGet` sau `active.get()` mà bỏ bước kiểm tra trạng thái: vẫn có cửa sổ race.

### 6.1. Sơ đồ lease đúng về ý tưởng

~~~text
Request:
  repeat:
      gen = active.get()
      lease = gen.tryAcquireUnlessRetired()
  until lease succeeds

  try:
      return gen.invoke(request)
  finally:
      lease.release()

Admin:
  newGen = prepare(version2)
  oldGen = atomicPublish(newGen)
  oldGen.markRetired()
  wait until oldGen.activeLeases == 0
  oldGen.stopAndClose()
~~~

Đây là **pseudocode**, không phải Java method có sẵn; cần thiết kế `tryAcquireUnlessRetired` và `markRetired` thành một state machine atomic/được khóa đúng. Tùy policy, admin cũng cần ngăn một request acquire oldGen trong khoảng **swap→retire** bằng gating hoặc bắt generation không còn active; nếu không, cutover semantics bị trễ dù close vẫn có thể an toàn khi đợi leases.

**Đối với async task:** Lease phải sống đến khi `CompletionStage`, callback, coroutine/virtual thread hoặc background work **thực sự kết thúc**, không chỉ khi method trả về một `Future`. Nếu plugin tự spawn thread nằm ngoài request lifecycle, cần stop/quiesce riêng.

### 6.2. Một request có được trộn V1 và V2 không?

Nên chọn một trong hai contract rõ ràng:

- **Request-affinity:** request acquire generation khi bắt đầu, dùng chính generation đó đến khi xong; các callbacks thuộc request cũng giữ lease. Phù hợp hot reload không cần ngắt request cũ.
- **Hard cutover/cancel:** admin chặn request mới, cancel/drain request cũ theo timeout rồi chuyển version. Phù hợp thay đổi giao thức/state không tương thích.

Không viết code gọi `PluginService old` rồi `PluginService new` trong cùng transaction mà thiếu quy tắc data compatibility. Việc classes có thể cùng tồn tại trong JVM không đồng nghĩa business state có thể dùng lẫn.

## 7. Rollback, failure modes và cách xử lý đúng

| Thời điểm lỗi | Trạng thái hệ thống | Xử lý |
| --- | --- | --- |
| Download/checksum/signature V2 lỗi | V1 vẫn ACTIVE | Không tạo/publish generation mới |
| V2 không nạp được class/dependency | V1 vẫn ACTIVE | Close loader V2 vừa tạo, ghi nguyên nhân |
| V2 constructor/`<clinit>` ném lỗi | V1 vẫn ACTIVE nếu chưa publish | Cleanup các tài nguyên V2 đã tạo; không ghi đè V1 |
| V2 health check thất bại | V1 vẫn ACTIVE | Mark V2 FAILED, cleanup rồi giữ router V1 |
| V2 đã publish, request V2 gặp business lỗi | V2 ACTIVE | Nếu rollback cần **policy chuyển lại V1** khi V1 còn phù hợp, hoặc tạo fresh generation của bản ổn định |
| V2 publish thành công, close V1 lỗi | V2 vẫn ACTIVE | Báo RETIREMENT_FAILED, tiếp tục cleanup/audit; **không giả vờ cutover chưa xảy ra** |
| V1 có request chạy lâu | V2 có thể đã ACTIVE | DRAIN với lease/timeout; không close sớm |
| JVM crash/process restart | Không có runtime state cũ | Khôi phục từ artifacts + external persisted state, không dựa vào classloader |

### 7.1. Trước publish: có thể rollback bằng cách bỏ V2

Trường hợp dễ nhất: PREPARE thất bại. Host không thay `active`, nên toàn bộ request còn dùng V1. Nhưng “rollback” không có nghĩa undo mọi **tác dụng phụ** mà constructor/start của V2 đã thực hiện. Nếu V2 viết database hoặc gửi message trước publish, bạn cần idempotence, transaction, compensating action hoặc staging policy riêng.

### 7.2. Sau publish: không gọi close failure là rollback

Trong lab, `replace` gán `active=prepared` rồi gọi `previous.close()`. Nếu `previous.close()` ném exception, **V2 vẫn đang active**. Code gọi `replace` có thể thấy exception, nên API production cần phân biệt ít nhất:

- `PREPARE_FAILED`: chưa publish;
- `PUBLISHED_CLEANUP_PENDING`: V2 đã publish, V1 chưa cleanup xong;
- `COMPLETE`: V2 đã publish, V1 đã retire.

Nếu dùng một boolean success/fail cho cả ba trạng thái, hệ vận hành có thể vô tình trigger retry/cutover lần hai và tạo thêm loader leak.

### 7.3. Rollback version thực tế

Nếu V2 có lỗi nghiệp vụ sau khi đã nhận traffic:

- Khi V1 **còn sống/không retired** và schema compatible, host có thể route trở lại V1 với protocol tương ứng.
- Nếu V1 **đã closed**, không giả định nó vẫn có thể tiếp tục nạp dependencies; thay vào đó **tạo loader mới** từ immutable V1 artifact nếu muốn khởi chạy lại V1.
- Database schema/messages/API có thể đã thay đổi theo V2; rollback bytecode mà không rollback/migrate state chưa chắc an toàn.
- Giữ các artifact versioned immutable để có thể reproduce. Tránh sửa trực tiếp JAR đang được loader sử dụng (trên Windows còn có thể không đổi tên/xóa do file handles).

## 8. `URLClassLoader.close()`: tài nguyên được đóng, class không biến mất

Javadoc mô tả `URLClassLoader.close()`:

- Ngăn loader tiếp tục nạp **classes/resources mới do nó tự tìm/định nghĩa** qua URL path.
- Classes/resources đã nạp trước đó **vẫn truy cập được**; classes từ parent delegation cũng vẫn truy cập.
- Đối với `jar:`/`file:`, đóng những file/JAR handles mà loader đã mở theo khả năng của implementation.
- Nếu một thread đang nạp class đồng thời thread khác gọi `close()`, **kết quả load không xác định** — tuyệt đối không dùng làm giao thức shutdown.
- `close()` có thể ném `IOException`; nếu nhiều lỗi khi đóng file, những lỗi sau có thể trở thành **suppressed exceptions**.
- Gọi `close()` lần nữa sau khi đã close không có thêm tác dụng.

**Không được suy ra:**

- `oldInstance.method()` sẽ lập tức ném lỗi vì class cũ “đã bị xóa”. Method đã loaded có thể vẫn thực thi; nhưng nếu nó lần đầu yêu cầu nạp một class/resource khác thuộc loader đã close, có thể lỗi.
- Static state của V1 tự reset.
- Background threads do V1 tạo tự dừng.
- Cache/static registry/ThreadLocal giữ V1 tự bị dọn.
- `URLClassLoader.close()` tự gọi `close()` cho từng provider, DB connection, HTTP client, executor, watcher hoặc file stream mà **application code** trực tiếp mở.
- Class unloading hay giải phóng Metaspace xảy ra đồng bộ ngay lúc close.

Với custom `ClassLoader` không extends `URLClassLoader`, **không có method `ClassLoader.close()` chuẩn**. Nếu loader tự giữ `JarFile`, channels hoặc sockets, bạn phải cung cấp `AutoCloseable` owner rõ ràng.

### 8.1. Phân biệt close loader với class unloading

Runtime class của V1 chỉ **có thể** được unload khi không còn roots/reachable objects phù hợp và JVM/GC quyết định thực hiện. Những tham chiếu giữ V1 bao gồm:

- `Class<?>`, `Method`, `Constructor` hoặc instance được host cache.
- Một object provider V1 trong `ServiceLoader`/DI container registry.
- Running thread stack frames đang thi hành bytecode V1.
- `Thread.currentThread().getContextClassLoader()` trên worker sống lâu.
- `ThreadLocal` value/key giữ class hoặc object của plugin.
- `static` map ở loader host, callback/listener/event queue giữ lambda/object V1.
- Executor, scheduler, async task, logging appender, JDBC driver và native/JNI references còn sống.
- ModuleLayer/ClassLoader references hoặc framework metadata caches.

`System.gc()` chỉ là gợi ý; không tạo SLA unload ngay. Nếu dùng HotSpot có logging hỗ trợ, có thể theo dõi class load/unload qua `-Xlog:class+load=info,class+unload=info` và heap dump/`jcmd` để tìm **strong reference path** giữ loader.

## 9. TCCL, ServiceLoader và các tài nguyên plugin dễ quên

### 9.1. Context class loader (TCCL)

Một plugin host có thể đặt TCCL để framework parent-loader tìm providers ở plugin loader:

~~~java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();
try {
    thread.setContextClassLoader(pluginLoader);
    frameworkCall();
} finally {
    thread.setContextClassLoader(previous);
}
~~~

Đây là **snippet**, cần biến `pluginLoader` và method `frameworkCall` trong dự án. Nếu quên `finally`, thread pool worker có thể giữ loader V1 sau khi đã swap sang V2, gây **provider discovery sai phiên bản** và chặn class unloading.

`new Thread(...)` thường kế thừa TCCL tại **thread construction time**; pool worker đã tạo từ trước **không tự nhận** TCCL của submitter ở mỗi `submit`. Không dùng TCCL vô tình của worker như nguồn quyết định active generation.

### 9.2. ServiceLoader cache và provider identity

`ServiceLoader.load(Service.class)` dùng TCCL ở thời điểm tạo đối tượng ServiceLoader. `ServiceLoader.load(Service.class, pluginLoader)` dùng loader tường minh, thường phù hợp plugin manager hơn. Provider cache của `ServiceLoader` không tự chuyển sang V2 khi bạn swap active loader.

- Khi thay generation, tạo **ServiceLoader mới** với loader/layer V2 nếu cần khám phá providers V2.
- `reload()` xóa provider cache của ServiceLoader hiện tại, **không** biến loaded class V1 thành class V2.
- Bỏ references và unregister providers V1 ở host; `close(pluginLoaderV1)` không tự gỡ mọi registrations.
- `ServiceLoader` instance không thread-safe; không cùng lúc duyệt và reload trên nhiều threads mà thiếu synchronization.

### 9.3. ThreadLocal, JDBC, logging, background jobs

Một lỗi hot reload thường không nằm ở `loadClass` mà nằm ở **resource ownership**:

- Plugin đăng ký JDBC driver vào `DriverManager`, MBean vào platform server, metrics exporter vào host registry — cần unregister.
- Plugin tạo executor/scheduler: stop tasks, await termination, xử lý interrupts và không để TCCL V1 trên workers chung.
- Plugin dùng `ThreadLocal`: remove khi context scope kết thúc; nếu giá trị tham chiếu object V1, worker host sống lâu có thể giữ loader V1.
- Plugin đăng ký event listener/callback vào một host singleton: unregister/cancel, tránh lambda V1 bị giữ.
- Plugin dùng JNI/native libraries: Java loader close không đồng nghĩa library OS được unload ngay; xem ràng buộc native lifetime.
- Plugin ghi log qua appender/file watcher riêng: đóng theo lifecycle của chính thư viện log, không kỳ vọng URLClassLoader quản lý.

## 10. Dependency versioning, package split và class identity

Một plugin V2 có thể sử dụng phiên bản thư viện khác V1. Loader mới giúp cô lập dependencies nếu chúng thực sự do mỗi plugin loader định nghĩa. Nhưng không phải lúc nào cũng an toàn:

- Nếu host parent đã nạp `com.example.lib.Client`, child parent-first có thể nhận **bản host** thay vì bản plugin. Hãy tách packages/loader policy nếu cần private version.
- Nếu host expose một public API dùng loại `com.example.lib.Client` trong method signature, type này là một phần **shared contract** và không thể tùy ý đổi defining loader giữa generations.
- Nếu cùng tên DTO do V1/V2 define riêng, cast/serialization trực tiếp có thể fail. Dùng shared DTO, stable schema, JSON/protobuf/records chung hoặc mapping explicit.
- `Package sealing` và signer/certificate consistency vẫn áp dụng khi nạp signed JARs; child-first tùy tiện có thể va chạm package semantics.
- Với JPMS, thêm `module-info.java` thay đổi resolution và `requires/exports/opens`; giữ đúng module graph/layer bên cạnh classloader identity.

### 10.1. Không dùng `getName()` để kiểm tra compatibility

~~~java
// Sai: hai classes có thể trùng tên mà không cast được.
boolean looksEqual =
        oldType.getName().equals(newType.getName());

// Đúng cho runtime identity:
boolean sameType = oldType == newType;

// Đúng cho shared contract:
boolean implementsApi =
        Plugin.class.isAssignableFrom(newType);
~~~

Đây là snippet với biến `oldType`/`newType` sẵn có. `isAssignableFrom` giúp xác minh class implement đúng host interface, nhưng không thay checks về binary compatibility, constructor, method behavior, resource ownership và module permissions.

## 11. ModuleLayer và plugin versions trong JPMS (Java 9+)

Nếu plugin được triển khai bằng **named modules**, chỉ tạo `URLClassLoader` mới cho JAR là không đủ để tự động thay module đã resolved trong boot layer. JPMS có mô hình **ModuleLayer** và module configurations:

1. Tìm module artifacts V2 qua `ModuleFinder`.
2. Resolve module graph với parent configuration thích hợp; xử lý `requires`, `exports`, `opens`, `provides/uses`.
3. Tạo **ModuleLayer mới** gắn modules tới một hoặc nhiều loader theo `defineModulesWithOneLoader`/`defineModulesWithManyLoaders` khi phù hợp.
4. Chỉ publish service objects của layer V2 sau validation.
5. Retire layer V1 bằng **bỏ tất cả references** tới layer/loader/classes/providers khi không còn work; không có thao tác Java SE phổ quát kiểu `ModuleLayer.unload()`.

Các lời gọi dưới đây là **pseudocode cấu trúc**, không phải chương trình có thể chạy nguyên văn:

~~~java
ModuleFinder finder = ModuleFinder.of(version2ModulePath);
Configuration config = parentLayer.configuration().resolve(
        finder, ModuleFinder.of(), Set.of("plugin.module"));

ModuleLayer v2 = parentLayer.defineModulesWithOneLoader(
        config, sharedApiLoader);
~~~

Cần import `java.lang.module.ModuleFinder`/`Configuration`, `java.util.Set` và biến `version2ModulePath`/`parentLayer`/`sharedApiLoader` đã có. Không phải mọi module graph/layer đều có thể resolve một cách tùy ý nếu packages/names/dependencies xung đột. `ModuleLayer` mới không tự đảm bảo unload V1 hoặc migration state.

**Lựa chọn kiến trúc:** Nếu ứng dụng chỉ cần plugin JAR classpath với SPI, `URLClassLoader` + shared API parent thường đơn giản hơn. Nếu muốn strong encapsulation/module descriptors/services theo JPMS, dùng module layers và kiểm tra module access riêng.

## 12. Không biến ClassLoader thành sandbox bảo mật

ClassLoader mới cho generation V2 giải quyết **class identity và dependency isolation** trong cùng JVM; nó không giới hạn file/network/CPU/threads mà code plugin được phép sử dụng. Trên JDK 24+, Security Manager đã bị vô hiệu hóa vĩnh viễn (JEP 486). Nếu plugin không tin cậy, chạy trong process/OS sandbox với quyền, resource limits và giao thức IPC phù hợp; hot swap trong cùng JVM không phải security boundary.

## 13. Checklist QA trước khi cho phép version swap

**Artifact / compatibility**

- [ ] V2 JAR path immutable/versioned; checksum/signature/provenance đã kiểm tra.
- [ ] Java bytecode version, dependency versions và module/JAR metadata phù hợp runtime.
- [ ] Shared API do host parent define, không có bản API riêng bị child define lại.
- [ ] Entry point có public constructor hoặc factory được contract cho phép; API compatible.
- [ ] Health check phân biệt verify/cấu hình và side effects do `<clinit>`/constructor.

**Cutover / concurrency**

- [ ] Có một **điểm publish atomic/locked** cho active generation.
- [ ] Request/callback đã acquire V1 được phép chạy đến đâu? Có lease/read lock/cancellation policy.
- [ ] Không đóng V1 khi `loadClass`/`getResource` hoặc methods còn chạy thiếu phối hợp.
- [ ] Nhiều admin/swap/close concurrent được serialization và kiểm tra state chính xác.
- [ ] Timeout khi draining V1; nếu timeout không tuyên bố cleanup complete.
- [ ] Callback async giữ đúng generation, không mix V1/V2 trong cùng transaction khi incompatible.

**Cleanup / observability**

- [ ] V1 ngừng timers/executors, unsubscribe listeners và unregister SPI/registries.
- [ ] Provider instances, sockets, DB pools, watchers và file handles được close đúng owner.
- [ ] TCCL của worker được restore; ThreadLocal chứa object V1 được remove.
- [ ] URLClassLoader V1 được close sau quiesce; suppressed exceptions được log.
- [ ] Host bỏ references tới `Class`, `Method`, ServiceLoader cache, plugin instance và ModuleLayer cũ.
- [ ] Đo loaders/threads/Metaspace/file handles qua nhiều lần swap, không dựa vào một lần GC.
- [ ] Báo trạng thái PREPARE_FAILED khác PUBLISHED_CLEANUP_PENDING khác COMPLETE.

## 14. Test plan nên có cho plugin manager

| Kiểm thử | Kết quả cần chứng minh |
| --- | --- |
| V1→V2 cùng binary name, loader mới | Method trả hành vi V2 và `oldType != newType` |
| Shared API identity | `Plugin.class.isAssignableFrom(v2Impl) == true` |
| Cố update file JAR nhưng giữ loader cũ | Không có giả định JVM tự tạo class mới |
| Invalid V2 JAR hoặc thiếu entry point | Không publish; V1 vẫn nhận requests |
| V2 constructor/health-check fail | Đóng resource V2 đã tạo; không chuyển active |
| 100 requests V1 đang chạy trong lúc swap | Không close V1 trước khi in-flight xong theo policy |
| Request đến sau cutover | Nhận V2, không bị route ngẫu nhiên về V1 |
| V1 close() ném lỗi | V2 vẫn active; báo retirement failure rõ |
| Hot reload lặp 100+ lần | Không có xu hướng tăng không kiểm soát của threads/loaders/handles |
| TCCL trên executor workers | Không còn reference loader V1 sau stop |
| V1 provider trong ServiceLoader/DI cache | Registry/cache cleanup đúng, không gọi provider sau retire |
| Rollback V2→stable V1 artifact | Loader mới được tạo; external schema/state compatible |
| Two concurrent admins cùng replace/close | Không orphan loader, không publish sau shutdown |
| JPMS layer V2 nếu áp dụng | Module graph/services/access đúng, không giữ layer V1 |

### 14.1. Quan sát lớp nạp và unload

Với JDK HotSpot hỗ trợ unified logging:

~~~bash
java -Xlog:class+load=info,class+unload=info \
    -cp "build/api:build/host" HostMain
~~~

Log cho biết load/unload events khi chúng xảy ra; một demo còn giữ `oldType` thì class V1 có thể chưa đủ điều kiện unload. `-Xlog` là cơ chế JVM implementation, không phải Java language API bắt buộc mọi vendor cùng format.

Có thể dùng `jcmd`/Java Flight Recorder/heap dump và thread dumps để truy vết loader retention. Kiểm tra tools có sẵn trong JDK trước khi dùng, giữ lại generation id + artifact hash + loader identity trong diagnostic logs để phân biệt V1/V2.

## 15. Những hiểu nhầm cần tránh

1. “Copy JAR mới đè lên V1.jar làm JVM tự lấy code mới.” — **Sai** với class đã load.
2. “Gọi `loader.loadClass` thêm lần nữa sẽ re-define class.” — **Sai**; cùng loader bình thường trả cùng `Class<?>`.
3. “Đổi `ProtectionDomain` hoặc `CodeSource` có thể reset class identity.” — **Sai**.
4. “Tạo loader mới nhưng parent có V1 class vẫn bảo đảm V2.” — **Sai**; delegation có thể trả bản parent.
5. “Cùng tên `api.Plugin` nghĩa là host cast được.” — **Sai** nếu defining loaders khác.
6. “Gọi `URLClassLoader.close()` lập tức unload lớp cũ.” — **Sai**.
7. “Close loader tự shutdown tất cả plugin threads/DB connections.” — **Sai**.
8. “`AtomicReference.getAndSet` rồi close ngay V1 an toàn với mọi request.” — **Sai** nếu còn request giữ reference V1.
9. “Chuẩn bị V2 có nghĩa chưa có side effects.” — **Sai**; static initializer/constructor/start có thể đã thực thi.
10. “Lỗi close V1 sau publish đồng nghĩa V2 chưa active.” — **Sai**; cần phân biệt commit và cleanup result.
11. “`ServiceLoader.reload()` hot-reload bytecode từ JAR mới.” — **Sai**; nó reset provider cache.
12. “Một loader mới tự giải quyết mọi incompatibility DB schema/API.” — **Sai**; cần migration/versioning riêng.
13. “ModuleLayer có method unload dùng khi hết plugin.” — **Không có** Java SE unload API kiểu này.
14. “Tách classloader là sandbox an toàn cho plugin không tin cậy.” — **Sai**, đặc biệt trên JDK 24+ không còn Security Manager.
15. “Một lần `System.gc()` mà chưa thấy unload là chứng minh memory leak.” — **Sai**; cần tìm strong roots và quan sát dài hạn.

## 16. Bài tập tự kiểm tra

1. V1 và V2 cùng define `plugin.impl.Versioned` từ hai `URLClassLoader` mới. Có phải một runtime class không? **Không**, nếu mỗi loader thực sự define chính class đó.
2. Plugin V2 implement `api.Plugin` do host parent define, host có cast được không? **Có**, khi implementation compatible và access hợp lệ.
3. Tại sao không đặt V1 implementation JAR vào host classpath trong lab? **Parent-first có thể trả class V1 cho cả hai loaders**.
4. `Generation.open(V2)` lỗi khi V1 active, có nên publish V2? **Không**; giữ V1 và cleanup V2.
5. Khi `active` vừa chuyển sang V2, một V1 request chưa kết thúc thì xử lý thế nào? **Draining/lease hoặc lock, không close V1 trước hạn**.
6. `URLClassLoader.close()` có xóa `Class<?>` đã load không? **Không**.
7. Nếu V1 close thất bại sau swap, active là gì? **V2**; phải báo lỗi retirement riêng.
8. Vì sao không đơn giản dùng `AtomicReference` cho mọi thao tác? **Atomic swap không bảo vệ lifetime của object đã đọc trước swap**.
9. Hot swap có dừng background executor của V1 tự động không? **Không**, cần lifecycle riêng.
10. Một instance `ServiceLoader` đang cache V1 providers sẽ tự chuyển sang V2 khi TCCL đổi không? **Không**.
11. Muốn rollback V1 sau khi loader V1 đã close, nên làm gì? **Khởi tạo generation mới từ artifact V1 immutable**.
12. Vì sao một host static map có thể gây classloader leak? **Nó giữ plugin classes/objects, kéo dài lifetime của defining loader**.
13. Có cần đóng shared parent loader sau mỗi plugin update không? **Không**, parent thuộc host và các generations khác.
14. Java 24+ có thể giới hạn plugin bằng Security Manager/ProtectionDomain như trước không? **Không**, phải dùng các boundary khác nếu cần.
15. Khi dùng named modules, phải quản lý thêm điều gì? **ModuleLayer/configuration/readability/exports/opens/provider resolution**.

## 17. Tài liệu chính thức

1. [JVMS 5.3 — Loading, defining loader và runtime type identity](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3): tên class + defining loader, initiating loaders, loading constraints.
2. [JLS 12.2 — Loading of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.2): good classloader consistency và yêu cầu cùng tên trả cùng runtime Class trong loader.
3. [ClassLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html): delegation, `findLoadedClass`, `defineClass`, `loadClass`, resource lookup.
4. [URLClassLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/URLClassLoader.html): `close`, đã-load classes, file/JAR handles, concurrent close limitations.
5. [AutoCloseable — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/AutoCloseable.html): cleanup và try-with-resources.
6. [ReentrantReadWriteLock — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/locks/ReentrantReadWriteLock.html): read/write lock và concurrency policy.
7. [Thread — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Thread.html): TCCL và thread inheritance.
8. [ServiceLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html): provider discovery/cache, `reload` và classloader/module layers.
9. [ModuleLayer — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ModuleLayer.html): nạp một configuration module trong layer mới.
10. [JEP 486 — Security Manager permanently disabled](https://openjdk.org/jeps/486): vì sao `ClassLoader`/`ProtectionDomain` không thể làm security sandbox cho plugin trên JDK 24+.

**Kết luận:** Thay plugin V1 bằng V2 là **chuyển cả một generation**, không phải cập nhật riêng một `Class<?>`. Hãy dùng **loader mới**, giữ **shared API identity**, chuẩn bị/kiểm thử V2 trước khi publish, chuyển routing theo **giao thức an toàn với in-flight requests**, rồi retire V1 theo resource ownership. `URLClassLoader.close()` giúp đóng JAR handles nhưng **không** tự dừng threads, tự unload class hay thay thế lifecycle/rollback policy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-architecture-008">Ranh giới của hot reload và trạng thái đang được sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Ranh giới của hot reload và trạng thái đang được sử dụng

## 1. Hot reload thực sự thay đổi điều gì?

**Hot reload plugin** trong cùng JVM thường được triển khai bằng cách tạo một ClassLoader mới, nạp implementation phiên bản mới, rồi chuyển những yêu cầu mới sang instance mới mà không restart toàn bộ ứng dụng. Nhưng thay đổi một biến trỏ đến plugin **không tự cập nhật** code, object, stack frame hay trạng thái mà những yêu cầu đang chạy đã nhận trước đó.

Với `URLClassLoader` và plugin JAR, cần phân biệt:

| Đối tượng/công việc | Khi chuyển từ v1 sang v2 |
|---|---|
| Lượt nhận request mới | Có thể định tuyến sang provider v2 |
| Request đã cầm reference tới provider v1 | Vẫn dùng object/class v1 nếu không có logic chuyển riêng |
| Runtime class `plugin.EchoPlugin` đã được loader v1 định nghĩa | Không đổi bytecode theo file JAR mới |
| Static field của implementation v1 | Không tự copy sang static field của implementation v2 |
| Session, callback, stream, thread do v1 tạo | Không tự hủy hoặc chuyển ownership |
| Database, file, message broker | Không tự migrate hoặc rollback vì đã thay loader |
| `URLClassLoader.close()` | Đóng nguồn lookup/JAR handles, không bảo đảm class unloading |

**Ranh giới cốt lõi:** *Chuyển tuyến cho công việc tương lai* khác với *hoàn tất, di chuyển hoặc hủy công việc/trạng thái hiện tại*. Một plugin system an toàn cần có protocol về **version/generation, pinning, drain/quiescence, migration và disposal**, không chỉ `new URLClassLoader(...)`.

## 2. Vì sao phiên bản mới cần defining loader mới?

JVM định danh một runtime class bằng **(binary name, defining loader)**. Khi `demo.plugin.EchoPlugin` V1 và V2 được **hai loader riêng thực sự định nghĩa**, chúng có thể cùng tồn tại nhưng không phải cùng `Class<?>`, nên object/static fields của V1 không tự biến thành V2. Cả hai phải implement API/DTO do **host/shared parent** định nghĩa để giao tiếp bình thường. Gọi lại `loadClass` trên loader cũ hoặc thay file JAR không tự redefine code; instrumentation là cơ chế khác với các giới hạn riêng. Phân biệt này là nền để bài tập dưới đây xét **state đã pin vào từng generation**.

## 3. Định nghĩa một Generation và ranh giới sử dụng

Một **generation** là tập hợp có cùng lifecycle:

- ID/version của bản plugin và artifact bất biến.
- ClassLoader dùng để nạp bản đó.
- Provider instance và implementation classes của bản đó.
- Bộ đếm các lượt sử dụng (*leases*, *in-flight*).
- Trạng thái vòng đời: ACTIVE, RETIRING, QUIESCENT, CLOSED.
- Các tài nguyên do generation quản lý: stream, callback, scheduler, executor, service registrations.

Một request **acquire** generation trước khi bắt đầu công việc và **release** sau khi đã hoàn toàn không còn sử dụng nó. Khi reload, host đổi nơi cấp lease mới sang v2; v1 chuyển sang **RETIRING**. Chỉ khi v1 không còn lease và không còn hoạt động ngoài lease, mới đóng nguồn class/resource của v1.

~~~text
Thời điểm t0: current = v1
  Request A -> acquire(v1) ------------------------- release(v1)
                        |
Thời điểm t1:           +-- reload / current = v2
                            Request B -> acquire(v2) -- release(v2)

v1 không cấp lease mới sau t1.
v1 có thể phục vụ A sau t1.
v1 chỉ close khi A thực sự hoàn tất và tài nguyên v1 đã được dọn.
~~~

**Không dùng `currentPlugin = newPlugin; oldLoader.close();` ngay sau đó** khi vẫn có task đang sử dụng v1. Theo Java SE 17, nếu một thread đang nạp class qua `URLClassLoader` khi thread khác gọi `close()`, kết quả của lần nạp đồng thời đó **không xác định**. Class đã nạp có thể vẫn hoạt động, nhưng lần đầu cần dependency/resource mới sau close có thể thất bại.

## 4. Tách các loại trạng thái trước khi reload

### 4.1. State nằm trong loader cũ

Object fields, static fields trong implementation, caches, `Class<?>`, reflection metadata, private singletons và phiên xử lý của v1 có thể vẫn được giữ bởi các request hoặc host. Khi class implementation v2 được loader mới định nghĩa, **static fields v2 là các fields khác**, kể cả khi mã nguồn khai báo giống hệt nhau.

### 4.2. State do host sở hữu

Shared DTO/API types, singleton ở loader parent, HTTP router, session repository, thread pool dùng chung, các bảng định tuyến generation và caches host có thể tồn tại qua reload. Chúng là **nguồn tương thích hoặc nguồn leak**, tùy có vô tình giữ old instance/class hay không.

### 4.3. State bên ngoài JVM

Database, message broker, files, distributed cache và external sessions sống độc lập với ClassLoader. Chúng không tự hỗ trợ hot migration. Nếu v1 và v2 cùng ghi vào schema đang thay đổi, có thể xảy ra lỗi nghiệp vụ ngay cả khi classloading hoàn toàn chính xác.

**Kết luận về state:** “Code v2 đã sẵn sàng” không đồng nghĩa “dữ liệu/sessions/jobs đã chuyển an toàn”. Hãy thiết kế migration protocol riêng, có versioned schema và rollback policy.

## 5. Ví dụ Java 17+: hai phiên bản plugin với một interface chung

Bài thực nghiệm sẽ chứng minh bằng stdout xác định:

1. Request A lấy plugin v1 trước reload và chạy với v1.
2. Sau reload, request B mới lấy v2.
3. v1 và v2 có implementation class cùng FQCN nhưng khác runtime identity.
4. Hai implementation dùng **một API type chung** của parent.
5. Request A còn giữ v1 vẫn gọi được v1 sau cutover.
6. Static counter của v2 bắt đầu lại, không lấy counter của v1.
7. Loader v1 chỉ đóng sau khi request A release lease; loader v2 đóng khi manager dừng.

### 5.1. Cấu trúc thư mục

~~~text
hot-reload-demo/
├── api-src/
│   └── demo/api/PluginService.java
├── host-src/
│   └── HotReloadDemo.java
├── v1-src/
│   └── demo/plugin/EchoPlugin.java
├── v2-src/
│   └── demo/plugin/EchoPlugin.java
├── v1-resources/
│   └── META-INF/services/demo.api.PluginService
└── v2-resources/
    └── META-INF/services/demo.api.PluginService
~~~

Hai file `EchoPlugin.java` có cùng package/FQCN nhưng nội dung khác và được biên dịch **vào hai output directories riêng biệt**, sau đó đóng thành hai JAR riêng. Khi chạy, **chỉ API và host** có trên application classpath.

### 5.2. Shared API

File **`api-src/demo/api/PluginService.java`**:

~~~java
package demo.api;

public interface PluginService {
    String version();
    String handle(String input);
}
~~~

Không để implementation-only classes của plugin lộ ra trong chữ ký API nếu host cần gọi qua nhiều loaders. Các DTO/exception/callback mà cả hai bên trao đổi cũng nên do loader chung định nghĩa.

### 5.3. Plugin v1

File **`v1-src/demo/plugin/EchoPlugin.java`**:

~~~java
package demo.plugin;

import demo.api.PluginService;

public final class EchoPlugin implements PluginService {
    private static int calls;

    public EchoPlugin() {
    }

    @Override
    public String version() {
        return "v1";
    }

    @Override
    public String handle(String input) {
        return "v1#" + (++calls) + ":" + input;
    }
}
~~~

`calls` là static state thuộc class v1. Demo thực hiện các lời gọi tuần tự; `++calls` không thread-safe nếu plugin xử lý nhiều requests song song. Production cần `AtomicInteger` hoặc cơ chế đồng bộ thích hợp.

### 5.4. Plugin v2

File **`v2-src/demo/plugin/EchoPlugin.java`**:

~~~java
package demo.plugin;

import demo.api.PluginService;

public final class EchoPlugin implements PluginService {
    private static int calls;

    public EchoPlugin() {
    }

    @Override
    public String version() {
        return "v2";
    }

    @Override
    public String handle(String input) {
        return "v2#" + (++calls) + ":" + input;
    }
}
~~~

Giá trị static counter v2 bắt đầu từ 0 trong runtime class v2, độc lập với số lời gọi mà v1 đã xử lý.

### 5.5. Service descriptor

Cả hai file:

- `v1-resources/META-INF/services/demo.api.PluginService`
- `v2-resources/META-INF/services/demo.api.PluginService`

đều chứa đúng một dòng:

~~~text
demo.plugin.EchoPlugin
~~~

`ServiceLoader.load(PluginService.class, child)` sẽ tìm provider trong JAR nhìn thấy bởi `child`. Interface `PluginService` được parent loader định nghĩa nên v1/v2 đều có cùng API identity.

### 5.6. Host quản lý lease và generation

File **`host-src/HotReloadDemo.java`**:

~~~java
import demo.api.PluginService;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;

public final class HotReloadDemo {
    private HotReloadDemo() {
    }

    private static final class Generation {
        final URLClassLoader loader;
        final PluginService provider;
        final String version;

        private int leases;
        private boolean retiring;
        private boolean closed;

        private Generation(URLClassLoader loader, PluginService provider) {
            this.loader = loader;
            this.provider = provider;
            this.version = provider.version();
        }

        static Generation load(Path jar) throws IOException {
            Path file = jar.toAbsolutePath().normalize();
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("Missing JAR: " + file);
            }

            URL[] urls = {file.toUri().toURL()};
            ClassLoader parent = PluginService.class.getClassLoader();
            URLClassLoader child = new URLClassLoader(urls, parent);

            try {
                PluginService plugin =
                        ServiceLoader.load(PluginService.class, child)
                                .findFirst().orElseThrow();
                if (plugin.getClass().getClassLoader() != child) {
                    throw new IllegalStateException(
                            "Provider must be defined by child");
                }
                return new Generation(child, plugin);
            } catch (RuntimeException | Error problem) {
                try {
                    child.close();
                } catch (IOException cleanup) {
                    problem.addSuppressed(cleanup);
                }
                throw problem;
            }
        }

        synchronized void pin() {
            if (retiring) {
                throw new IllegalStateException("Generation retiring");
            }
            leases++;
        }

        synchronized void unpin() throws IOException {
            if (leases <= 0) {
                throw new IllegalStateException("Unbalanced release");
            }
            leases--;
            closeIfIdle();
        }

        synchronized void retire() throws IOException {
            retiring = true;
            closeIfIdle();
        }

        private void closeIfIdle() throws IOException {
            if (retiring && leases == 0 && !closed) {
                closed = true;
                loader.close();
                System.out.println("loader closed: " + version);
            }
        }
    }

    private static final class Session implements AutoCloseable {
        private final Generation generation;
        private boolean closed;

        Session(Generation generation) {
            this.generation = generation;
        }

        synchronized String call(String input) {
            if (closed) {
                throw new IllegalStateException("Session closed");
            }
            return generation.provider.handle(input);
        }

        synchronized Class<?> implementationClass() {
            if (closed) {
                throw new IllegalStateException("Session closed");
            }
            return generation.provider.getClass();
        }

        synchronized Class<?> apiType() {
            if (closed) {
                throw new IllegalStateException("Session closed");
            }
            return generation.provider.getClass().getInterfaces()[0];
        }

        @Override
        public synchronized void close() throws IOException {
            if (!closed) {
                closed = true;
                generation.unpin();
            }
        }
    }

    private static final class PluginManager implements AutoCloseable {
        private Generation current;

        PluginManager(Path initialJar) throws IOException {
            current = Generation.load(initialJar);
        }

        synchronized Session open() {
            if (current == null) {
                throw new IllegalStateException("Manager closed");
            }
            current.pin();
            return new Session(current);
        }

        synchronized void reload(Path jar) throws IOException {
            if (current == null) {
                throw new IllegalStateException("Manager closed");
            }

            // Candidate nạp thành công rồi mới publish.
            Generation next = Generation.load(jar);
            Generation old = current;
            current = next;
            old.retire();
        }

        @Override
        public synchronized void close() throws IOException {
            if (current != null) {
                Generation old = current;
                current = null;
                old.retire();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: HotReloadDemo <v1.jar> <v2.jar>");
        }

        Path v1 = Path.of(args[0]);
        Path v2 = Path.of(args[1]);

        try (PluginManager manager = new PluginManager(v1)) {
            try (Session held = manager.open()) {
                Class<?> oldImpl = held.implementationClass();

                System.out.println("before reload: " + held.call("A"));
                manager.reload(v2);

                try (Session fresh = manager.open()) {
                    System.out.println("after reload: "
                            + fresh.call("B"));
                    System.out.println("same implementation class: "
                            + (oldImpl == fresh.implementationClass()));
                    System.out.println("shared API identity: "
                            + (held.apiType() == fresh.apiType()));
                }

                System.out.println("old pinned: " + held.call("C"));

                // Lease cuối của v1 kết thúc: bây giờ loader v1 đóng.
                held.close();
            }

            try (Session next = manager.open()) {
                System.out.println("new next: " + next.call("D"));
            }
        }
    }
}
~~~

**Tính chất trong ví dụ:**

- `PluginManager.open()/reload()` cùng `synchronized` nên thao tác chọn generation và tăng lease không bị chen bởi cutover.
- Một `Session` giữ `Generation` đã acquire. Nó **không** tra cứu lại `current` sau mỗi lời gọi.
- `Session.call()` và `Session.close()` cùng synchronized trên một session, tránh close cùng session xen giữa một lời gọi đồng bộ.
- `Generation.retire()` ngừng cấp lease mới và chỉ close loader nếu `leases == 0`.
- `Generation.load` đóng candidate loader nếu discovery provider lỗi trước khi publish; current cũ vẫn được giữ.
- `PluginManager.reload` nạp candidate **trong lock**, vì vậy các `open()` mới có thể tạm đợi. Production nên cân nhắc prepare/health-check candidate ngoài lock rồi commit bằng một bước ngắn/atomic.
- Đây là **mô hình synchronous**: lease chỉ theo dõi những công việc host biết, không tự thu hồi plugin thread, callback, `CompletableFuture` hoặc tài nguyên bên ngoài.

Một lưu ý xử lý lỗi: trong mã demo, nếu `old.retire()` ném `IOException` lúc đóng loader cũ sau cutover, `current` **đã trỏ tới v2**. Production phải ghi nhận rõ trạng thái partially successful cutover và close failure, thay vì báo “reload thất bại, vẫn ở v1” một cách sai sự thật.

## 6. Biên dịch và chạy Java 17+

Yêu cầu JDK 17+, đứng tại thư mục `hot-reload-demo/`, tạo đúng các tệp như trên.

### Bash (Linux/macOS/Git Bash)

~~~bash
mkdir -p build/api build/host build/v1 build/v2 dist

javac -encoding UTF-8 -d build/api \
  api-src/demo/api/PluginService.java

javac -encoding UTF-8 -cp build/api -d build/host \
  host-src/HotReloadDemo.java

javac -encoding UTF-8 -cp build/api -d build/v1 \
  v1-src/demo/plugin/EchoPlugin.java

javac -encoding UTF-8 -cp build/api -d build/v2 \
  v2-src/demo/plugin/EchoPlugin.java

cp -R v1-resources/META-INF build/v1/
cp -R v2-resources/META-INF build/v2/

jar --create --file dist/v1.jar -C build/v1 .
jar --create --file dist/v2.jar -C build/v2 .

java -cp "build/api:build/host" \
  HotReloadDemo dist/v1.jar dist/v2.jar
~~~

### Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force build/api,build/host,build/v1,build/v2,dist | Out-Null

javac -encoding UTF-8 -d build/api api-src/demo/api/PluginService.java

javac -encoding UTF-8 -cp build/api -d build/host host-src/HotReloadDemo.java

javac -encoding UTF-8 -cp build/api -d build/v1 v1-src/demo/plugin/EchoPlugin.java

javac -encoding UTF-8 -cp build/api -d build/v2 v2-src/demo/plugin/EchoPlugin.java

Copy-Item v1-resources/META-INF -Destination build/v1 -Recurse -Force
Copy-Item v2-resources/META-INF -Destination build/v2 -Recurse -Force

jar --create --file dist/v1.jar -C build/v1 .
jar --create --file dist/v2.jar -C build/v2 .

java -cp "build/api;build/host" HotReloadDemo dist/v1.jar dist/v2.jar
~~~

Kiểm tra JAR:

~~~bash
jar --list --file dist/v1.jar
jar --list --file dist/v2.jar
~~~

Mỗi JAR phải có ít nhất `demo/plugin/EchoPlugin.class` và `META-INF/services/demo.api.PluginService`. Không đóng gói API trong plugin JAR và không đặt plugin JAR vào classpath host của lệnh `java`.

### Stdout chính xác

~~~text
before reload: v1#1:A
after reload: v2#1:B
same implementation class: false
shared API identity: true
old pinned: v1#2:C
loader closed: v1
new next: v2#2:D
loader closed: v2
~~~

Diễn giải:

1. Session cũ nhận v1 nên output đầu là `v1#1:A`.
2. Sau reload, lượt acquire mới nhận v2; counter v2 bắt đầu từ 1.
3. Class của hai implementation không bằng nhau vì **defining loader khác nhau**.
4. Interface API mà hai implementations trực tiếp implements là cùng `PluginService.class` do parent định nghĩa.
5. Session cũ vẫn nhận `v1#2:C` sau khi current chuyển v2.
6. Chỉ khi `held.close()` giải phóng lease cuối của v1 mới có `loader closed: v1`.
7. Lượt mới sau đó dùng v2 và tăng counter v2 lên 2.
8. Khi manager kết thúc, loader v2 được đóng sau các session cuối cùng.

## 7. Cutover không chuyển đối tượng cũ sang class mới

Bước cutover về bản chất là đổi mapping cho **lượt xin lease tiếp theo**:

~~~java
// Trích mã từ PluginManager.reload.
Generation next = Generation.load(jar);
Generation old = current;
current = next;
old.retire();
~~~

Đây không phải cơ chế di chuyển code của stack frame hay object đang sống. Một object v1 vẫn là instance của class v1; static fields của class v1 vẫn thuộc defining loader v1. Hai class implementation cùng FQCN được hai loaders định nghĩa có thể tồn tại **đồng thời** trong JVM.

Đổi `current` không đảm bảo sau thời điểm cutover tất cả response đều do v2 tạo. Request v1 đã acquire trước cutover có thể hoàn tất sau đó. Với các hệ thống cần **strict version switch**, phải drain v1 trước khi publish v2 hoặc áp dụng session affinity/transaction boundary phù hợp.

## 8. Race nguy hiểm trong reference counting

Một mô hình ngây thơ:

~~~java
// Mã minh họa không an toàn nếu swap đồng thời.
Generation generation = current.get();
generation.active.incrementAndGet();
~~~

Giữa hai dòng, thread reload có thể đổi `current` sang v2, thấy count v1 đang 0 rồi close v1. Thread A mới tăng counter quá muộn và dùng loader đã đóng.

Mấu chốt không phải “có AtomicInteger là đủ”, mà là **acquire generation và ghi nhận quyền sử dụng phải nằm trong một giao thức đồng bộ** với retire/close.

Ví dụ `synchronized open()/reload()` là một giải pháp đơn giản, dễ chứng minh: việc chọn current và pin diễn ra trong một critical section; reload không thể retire cùng generation giữa hai thao tác. Các hệ thống lớn có thể dùng ReadWriteLock, CAS state machine, epoch reclamation hoặc versioned router, nhưng phải chứng minh không có race acquire-vs-retire.

Release cũng phải **exactly once**; đặt trong `try-with-resources` hoặc `finally` để exception không làm pin cũ bị giữ vô hạn. Nếu dùng asynchronous APIs, lease phải kéo dài đến khi mọi việc phụ thuộc generation thực sự xong.

## 9. Định nghĩa “không còn ai dùng v1” cẩn thận

Một plugin có thể còn hoạt động sau khi request handler trả về:

- `CompletableFuture`, background callbacks, asynchronous stream processing.
- Scheduled jobs, event bus subscriptions, message consumers.
- Thread pool tasks đang queue hoặc đang chạy.
- `ThreadLocal` và Thread Context ClassLoader (TCCL) còn giữ loader cũ.
- Listener, `Class<?>`, reflection `Method`, lambdas, proxies và cache của host.
- Stream/socket/file/JAR handle do plugin tự mở.
- Object do plugin v1 trả về, được caller giữ ngoài lease.
- Resources thuộc database/caches ngoài JVM.

Nếu một plugin trả `CompletionStage` nhưng còn xử lý bất đồng bộ, release lease **ngay khi nhận stage** có thể đóng loader quá sớm. Cần giữ ownership tới completion/cancellation thật sự và cleanup cả những task phụ thuộc.

Một manager không thể dựa vào `URLClassLoader.close()` để tự kết thúc thread hay listener. Nếu không quản lý các resources ấy, dù ref-count sessions về 0 thì hệ thống **vẫn chưa quiescent**.

## 10. Quiesce, drain và disposal an toàn

~~~text
ACTIVE v1
   |
   | chuẩn bị, validate v2 trước
   v
READY v2
   |
   | cutover atomic, v1 ngừng cấp lease mới
   v
RETIRING v1 + ACTIVE v2
   |
   | drain task v1 và ngừng scheduler/listener v1
   v
QUIESCENT v1
   |
   | close resource do plugin sở hữu, restore TCCL
   | close URLClassLoader, drop references
   v
CLOSED v1
~~~

Đừng coi `ExecutorService.shutdown()` là đã drain xong: phương thức này chỉ ngừng nhận task mới; còn phải `awaitTermination(...)` hoặc theo dõi Futures/in-flight tasks, với timeout và chính sách cancellation rõ ràng.

**Không tự close loader ngay khi timeout** nếu code cũ vẫn có khả năng thực thi/nạp thêm class. Nếu buộc hard-stop plugin không tin cậy, ranh giới tiến trình riêng thường thực tế hơn ClassLoader trong cùng JVM.

`URLClassLoader.close()` đóng nguồn tìm class/resource của chính loader và các file do loader mở, nhưng **không unload ngay** những class đã định nghĩa. Class cũ còn reachable vẫn tồn tại; code cũ còn cần dependency chưa load có thể thất bại sau close. Nếu class loading xảy ra đồng thời với close, Javadoc gọi kết quả là **undefined**.

## 11. State migration phải là hợp đồng riêng

V1 và v2 có thể có các static fields, object graphs, caches và session data hoàn toàn khác nhau. Java không copy các fields đó giữa hai loaders. Không ép kiểu `EchoPlugin` v1 sang `EchoPlugin` v2 rồi mong chuyển state: runtime types khác nhau.

Một giao thức migration thường gồm:

1. Freeze/quiesce vùng state cần snapshot.
2. Export qua DTO/schema ổn định do host/API sở hữu.
3. Version và validate snapshot; transform dữ liệu nếu cần.
4. Import/restore vào v2 trước cutover hoặc theo chính sách phiên.
5. Cutover theo consistency model đã thống nhất.
6. Dọn old state sau khi không còn yêu cầu v1.

Với state bền vững, ưu tiên database/event log hoặc một format được version hóa thay vì serialize object implementation. Java native serialization không tự đảm bảo class loader compatibility, schema migration hay độ an toàn cho dữ liệu không tin cậy.

Nếu v1/v2 chạy chồng thời gian, **không được mặc nhiên** để cả hai ghi cùng database schema hoặc queue offsets theo các contract khác nhau. Cần backward/forward compatibility, idempotency, event versioning, ownership của background jobs và rollback rules.

## 12. Rollback và lỗi trong hai thời điểm khác nhau

**Candidate lỗi trước publish:** Hãy đóng candidate loader, giữ current v1 và báo lỗi. Không đổi router sang v2 chưa sẵn sàng. Demo làm như vậy với lỗi provider discovery.

**Lỗi sau publish:** Router đã trỏ tới v2, có thể còn in-flight v1/v2. Rollback cần lựa chọn:

- Giữ v1 dạng standby nếu policy cho phép và chưa đóng; vẫn phải quản lý leases/resources.
- Hoặc tạo **generation v1 mới** từ artifact cũ đã lưu, không “hồi sinh” bytecode/class object v1 cũ.
- Nếu migration database đã không tương thích ngược, rollback code đơn thuần có thể không an toàn. Cần restore state, compensating transaction hoặc forward fix.

Một close failure của old loader sau cutover không đồng nghĩa cutover thất bại. Cần log chính xác **phase** thất bại và trạng thái current thật, tránh retry mù quáng tạo nhiều generations.

## 13. TCCL, ServiceLoader và API identity

Nếu framework dùng `ServiceLoader.load(service)` hoặc tìm resources qua TCCL, đặt plugin loader trong phạm vi invocation **trên chính thread thực thi**, rồi restore trong `finally`. Plugin provider types phải sử dụng shared API của parent; đối với JPMS, có thêm module layer/service declarations. Không để worker thread cũ giữ TCCL V1 sau cutover.

## 14. Leak và trạng thái JVM toàn cục

Dấu hiệu cần theo dõi qua nhiều lượt reload: các request vẫn trả kết quả V1, classloader/Metaspace tăng, worker threads còn chạy, JAR handles chưa đóng, các registration/`ServiceLoader` caches còn trỏ V1. Đối chiếu generation của request và defining loader trước khi kết luận cutover hỏng; dùng thread/heap dumps và GC-root paths để tìm references giữ V1. Đóng loader không đồng nghĩa unload ngay, và không thể dùng một lần `System.gc()` để xác nhận sạch.

## 15. Khi nào nên tránh hot reload?

Restart/rolling deployment có thể an toàn hơn khi:

- Shared API thay đổi không tương thích nhị phân (`NoSuchMethodError`, `AbstractMethodError`, `LinkageError`).
- Plugin dùng JNI/native libraries hoặc trạng thái process-global khó gỡ.
- Nhiều long-lived sessions cần đảm bảo một version thống nhất.
- Schema migration không hỗ trợ coexistence giữa v1 và v2.
- Plugin sinh thread và side effects mà host không thể kiểm soát.
- Resource ownership chưa rõ; không thể chứng minh quiescence.
- Cần rollback toàn hệ thống theo giao dịch hoặc ranh giới bảo mật mạnh.

Không ép hot reload nếu giá trị kinh doanh không bù được độ phức tạp về lifecycle và consistency.

## 16. Checklist triển khai

1. Shared API/DTO/exception có **một runtime identity** do loader chung định nghĩa?
2. Mỗi version có artifact bất biến, loader và generation ID riêng?
3. Candidate được load/validate/health-check trước publish?
4. Acquire lease + chọn current có đồng bộ với retire/cutover?
5. Lease bao trùm mọi việc đồng bộ và bất đồng bộ thực sự dùng plugin?
6. State migration/serialization có version và schema kiểm chứng được?
7. Có ngừng task mới, drain tasks cũ và dọn callback/thread/TCCL?
8. Có xử lý close failure, timeout, stuck tasks và rollback theo phase?
9. Dữ liệu ngoài JVM có cho phép v1/v2 coexist không?
10. Có metrics theo generation và leak detection đủ để xác nhận cleanup?
11. Có chính sách fallback restart khi hot reload không an toàn?

## 17. Tự kiểm tra

**Câu 1.** Request A đã nhận plugin v1 rồi host reload v2. A có tự chạy code v2 không?

**Đáp án:** Không. Reference và runtime class v1 của A không tự thay đổi.

**Câu 2.** Vì sao hai class `demo.plugin.EchoPlugin` từ v1/v2 có cùng tên mà `==` false?

**Đáp án:** Chúng được hai defining loaders khác nhau định nghĩa.

**Câu 3.** v2 có kế thừa static field `calls` của v1 không?

**Đáp án:** Không. Static fields của hai runtime classes độc lập; muốn chuyển state phải có migration rõ.

**Câu 4.** Sau khi current đổi sang v2, có được gọi `oldLoader.close()` ngay không?

**Đáp án:** Chỉ khi v1 thật sự quiescent, không còn task nào cần load class/resource hoặc dùng nguồn mà loader sở hữu; việc còn in-flight phải drain.

**Câu 5.** `CompletableFuture` chưa hoàn thành nhưng handler đã return, có thể release lease không?

**Đáp án:** Không nếu công việc bất đồng bộ còn phụ thuộc plugin; lease phải kéo dài tới khi việc sử dụng kết thúc.

**Câu 6.** Nếu v2 candidate lỗi trước publish, nên làm gì?

**Đáp án:** Dọn candidate và giữ current v1, không đổi định tuyến sang v2 chưa sẵn sàng.

**Câu 7.** Có đảm bảo không xuất hiện response v1 sau cutover?

**Đáp án:** Không nếu request v1 đã pin. Cần strict drain hoặc session-affinity policy nếu nghiệp vụ đòi hỏi.

## 18. Nguồn chính thức

- [Java SE 17 — URLClassLoader.close](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html#close()) — close, JAR handles và class loading đồng thời.
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html) — class identity, delegation.
- [Java SE 17 — ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html) — provider discovery.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html) — shutdown và awaitTermination.
- [Java SE 17 — AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html) — tài nguyên/lease có phạm vi.
- [JVMS 17, Chương 5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html) — loading, linking, initialization.

**Điểm cần nhớ:** Hot reload bằng ClassLoader mới chỉ thay đổi nơi **yêu cầu tương lai** lấy code. Công việc đang sử dụng v1 phải được **pin, drain và release đúng thời điểm**; state migration và side effects bên ngoài JVM phải có giao thức riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-architecture-009">Vì sao class loader không tạo thành sandbox bảo mật</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao ClassLoader không tạo thành sandbox bảo mật cho plugin Java

> **Java Core → ClassLoader → Kiến trúc plugin.** `ClassLoader` giúp kiểm soát **từ đâu tìm bytecode**, **lớp nào thuộc namespace nào** và **quan hệ delegation giữa host và plugin**. Nó không tạo ranh giới bảo mật tương đương một tiến trình, một người dùng hệ điều hành hay container. Một plugin có bytecode Java bình thường, sau khi được nạp vào cùng JVM, nhìn chung vẫn có thể gọi các API nền tảng theo quyền mà **tiến trình JVM** có. Bài học phân biệt class isolation, module encapsulation, metadata bảo mật lịch sử và sandbox thực tế; có ví dụ Java 17+ được chạy kiểm chứng.

## 1. Hiểu đúng bài toán và mô hình đe dọa

Giả sử hệ thống host cho phép tải một JAR chứa plugin:

```text
Host JVM process
├── Host application classes
├── Plugin API: com.example.api.Plugin
├── Plugin ClassLoader A
│   ├── plugin-a.jar
│   └── một số dependencies riêng
└── Plugin ClassLoader B
    ├── plugin-b.jar
    └── phiên bản dependencies khác
```

Một thiết kế như vậy có thể giải quyết các vấn đề hợp lý:

- Hai plugin dùng hai phiên bản thư viện cùng tên nhưng khác defining loader.
- Không tự đưa implementation của plugin A vào search path của plugin B.
- Cho plugin giao tiếp với host qua API chung có cùng `Class<?>` identity.
- Quản lý thế hệ plugin: tạo loader mới khi cập nhật, giải phóng references tới loader cũ nếu có thể.
- Giữ cấu trúc tìm class và resource có thể giải thích, kiểm thử.

**Nhưng:** nếu plugin A là bytecode không đáng tin, câu hỏi chuyển sang hướng khác: “Liệu plugin có thể đọc file của host, kết nối mạng, sửa global state hoặc dừng process không?” Chỉ chia tách loaders **không cung cấp lời hứa ngăn những hành động ấy**.

### Ba mô hình tin cậy khác nhau

| Mô hình plugin | Mục tiêu | ClassLoader đủ không? |
| --- | --- | --- |
| Plugin **đáng tin**, khác dependencies | Tách thư viện, tránh `ClassCastException`/collision, quản lý lifecycle. | Có ích và thường phù hợp, nhưng còn phải quản lý state. |
| Plugin **bán tin cậy**, hợp tác theo contract | Hạn chế lỗi vô tình bằng API/plugin contract, validation, timeout. | Hữu ích một phần; không đảm bảo chống mã cố ý lách contract. |
| Plugin **không tin cậy hoặc có thể độc hại** | Bảo vệ dữ liệu, file, network, process và tài nguyên khỏi mã chạy tùy ý. | **Không.** Cần ranh giới thực thi mạnh hơn như tiến trình khác với quyền OS hạn chế. |

**Threat model quyết định giải pháp:** tránh class conflict khác hoàn toàn với việc phòng plugin cố tình đánh cắp dữ liệu, phá dịch vụ hoặc vượt kiểm soát quyền.

## 2. ClassLoader thực sự cô lập những gì?

JVM định danh một class/interface thông thường bằng cặp **(binary name, defining loader)**.

```text
Loader A -> com.plugin.Widget (Class A)
Loader B -> com.plugin.Widget (Class B)

Tên nhị phân bằng nhau
Định nghĩa bởi loader khác nhau
=> Class A != Class B
```

Hệ quả:

- Cùng tên `com.plugin.Widget` không nhất thiết cùng runtime type.
- Hai lớp thuộc hai loaders khác nhau có thể gặp `ClassCastException` khi ép kiểu, kể cả code trông giống nhau.
- `Package` runtime, package-private access và metadata package chịu sự phân định theo defining loader.
- Một loader chỉ tìm những resources/classes trong phạm vi nguồn và delegation policy của nó, không tự đọc mọi JAR ngoài đường tìm.
- TCCL (`Thread.currentThread().getContextClassLoader()`) có thể được framework sử dụng để tìm provider/resource khác system loader.

Đây là **namespace và dependency isolation**. Nó là thuộc tính quan trọng cho plugin architecture, nhưng **không phải chính sách cấm sử dụng Java API**.

### Parent delegation không phải tường lửa

Đường `ClassLoader.loadClass` phổ biến dùng parent-first:

```text
pluginLoader.loadClass("java.nio.file.Files")
    |
    v
parent / platform / bootstrap lookup
    |
    '--> java.nio.file.Files được trả từ Java runtime
```

Parent delegation giúp thống nhất các lớp nền tảng và chia sẻ API, không chặn plugin dùng chính các API đó. Việc không cho plugin **định nghĩa lại** `java.lang.String` không có nghĩa đã không cho plugin **gọi** `java.lang.String`, `Files`, `Socket`, `ProcessBuilder` hoặc các phương thức khác của JDK mà nó truy cập được.

Không tự đặt `parent = null` rồi gọi đó là “sandbox”. Parent null là yêu cầu delegation đến bootstrap loader theo semantics của `ClassLoader`; nhiều API nền tảng vẫn tồn tại. Ngoài ra, class loading constraints và namespaces cần tuân thủ dù chọn parent nào.

## 3. Điều gì KHÔNG được class loader ngăn chặn?

Nếu class đã được JVM chấp nhận và bắt đầu thực thi, bytecode của nó có thể tự gọi các public APIs mà module/access rules và môi trường cho phép. Một vài ví dụ đáng chú ý:

| Khả năng của plugin | ClassLoader có tự chặn không? | Rủi ro đối với host |
| --- | --- | --- |
| `Files.readString(Path)` | Không. | Đọc file process có quyền, nếu biết/đoán đường dẫn. |
| `Files.writeString(Path, ...)` | Không. | Sửa/xóa dữ liệu mà OS account có quyền. |
| `System.getProperty`, `System.getenv` | Không. | Đọc system properties/environment có sẵn trong process. |
| `Socket`, `HttpClient` | Không. | Kết nối ra ngoài nếu network policy cho phép. |
| `ProcessBuilder` | Không. | Chạy process con nếu OS/runtime cho phép. |
| `System.exit` | ClassLoader không đảm bảo chặn. | Có thể kết thúc cả JVM chứa host. |
| Tạo thread, giữ lock, CPU loop, cấp phát bộ nhớ | Không. | Tắc nghẽn, memory pressure, denial of service. |
| Gọi API host public hay lấy references được truyền | Không. | Thay đổi trạng thái chia sẻ tùy API/objects được phơi bày. |
| Reflection | Không phải quy tắc từ ClassLoader; JPMS còn kiểm soát reflection. | Có thể truy cập thêm members khi module/package mở phù hợp. |

Điều kiện bên ngoài vẫn có tác dụng: filesystem ACL, quyền OS user, SELinux/AppArmor, container config, network policy, JPMS access và một số quyền của API chuyên biệt. Nhưng **cơ chế này không phải bảo đảm do ClassLoader tạo ra**.

Không diễn giải bảng thành “mọi lời gọi luôn thành công”: thao tác có thể thất bại vì OS permissions, path không tồn tại, network bị chặn, module encapsulation hoặc security policy khác. Điểm cần nhớ là **việc thay loader không tự thiết lập deny policy cho những thao tác ấy**.

### “Không cung cấp API nguy hiểm cho plugin” chưa đủ

Host có thể expose interface chỉ gồm:

```java
public interface PluginContext {
    String readPublicConfig();
    void emitEvent(String event);
}
```

Đây là **snippet Java** minh họa contract, không phải chương trình độc lập. Với plugin đáng tin, contract giúp thiết kế tốt. Nhưng nếu bytecode plugin được phép thực thi **mã Java tùy ý trong cùng JVM**, nó có thể gọi thẳng `Files`, `HttpClient` hoặc API khác mà không đi qua `PluginContext`. Một restricted wrapper API **không tạo sandbox** nếu không có thêm enforcement ở runtime/OS/process.

## 4. SecureClassLoader có chữ “Secure” nhưng không biến plugin thành an toàn

`java.security.SecureClassLoader` mở rộng `ClassLoader` và cung cấp API gắn `CodeSource`, `ProtectionDomain` và `PermissionCollection` cho class được định nghĩa.

```java
protected final Class<?> defineClass(
    String name, byte[] bytes, int offset, int length,
    java.security.CodeSource source);

protected java.security.PermissionCollection getPermissions(
    java.security.CodeSource source);
```

Đây là **chữ ký API rút gọn**, không copy thành file Java độc lập. Subclass có thể override `getPermissions` để cung cấp permission metadata cho domain được dựng.

Lịch sử:

```text
SecureClassLoader.defineClass(..., CodeSource)
       |
       v
CodeSource(location, certificates)
       |
       v
getPermissions(CodeSource)
       |
       v
ProtectionDomain
       |
       v
Class<?> gắn với domain
       |
       v
Legacy SecurityManager/Policy (khi từng được bật)
       |
       '--> nền tảng có thể kiểm tra permissions tại protected operations
```

**Khi Security Manager không được bật**, một tập permissions rỗng không tự bảo vệ filesystem/network. **Từ JDK 24**, Security Manager bị vô hiệu hóa vĩnh viễn theo JEP 486. Javadoc `SecureClassLoader` hiện đại cũng nêu rõ permissions không còn dùng để kiểm soát quyền truy cập tài nguyên khi Security Manager không được hỗ trợ.

`CodeSource` vẫn có thể hữu ích cho metadata/provenance, nhưng:

- `CodeSource.location` có thể là URL **do loader tự gán**; nó không chứng minh bytes đã được xác minh nguồn gốc.
- `CodeSource` không tự chứng minh JAR đã ký hoặc đáng tin; nếu có signing cần quy trình verification phù hợp.
- `ProtectionDomain.getPermissions()` có thể chứa collection rỗng nhưng điều đó không tự chặn `Files.readString` trên JDK 24+.
- Certificate consistency, class-format verification, package sealing, module access và `SecurityManager` là các khái niệm khác nhau; không thay thế cho nhau.

## 5. Vì sao SecurityManager không còn là đường xây sandbox mới?

Trước đây Java có mô hình kiểm tra permissions dựa trên Security Manager:

- `CodeSource` và domain liên kết class với một nguồn code.
- `java.security.Policy` có thể định nghĩa `FilePermission`, `SocketPermission`, `PropertyPermission`, v.v.
- Khi `SecurityManager` được kích hoạt, các API tích hợp permission checks có thể cho phép hoặc từ chối thao tác.
- `AccessController` tham gia mô hình stack-based access control và `doPrivileged`.

Tuy nhiên, cơ chế này có độ phức tạp cao, adoption hạn chế và không bao giờ tạo bảo đảm cô lập mọi vấn đề (native code, tài nguyên chia sẻ, side channels và các rủi ro khác).

Dòng thời gian:

| Java/JDK | Tình trạng |
| --- | --- |
| Java 8/11 và các bản cũ | Security Manager có thể được dùng trong môi trường bật và cấu hình đúng; mặc định ứng dụng thường không có sandbox. |
| Java 17 | **JEP 411** deprecate Security Manager **for removal**. |
| Java 17–23 | Các khả năng legacy còn có nhưng có cảnh báo/ràng buộc tùy phiên bản. |
| **JDK 24+** | **JEP 486** vô hiệu hóa Security Manager vĩnh viễn. Không thể cài/bật lại bằng launcher hoặc `System.setSecurityManager`. |

Trên JDK 24+, `System.getSecurityManager()` trả `null`; `System.setSecurityManager(...)` ném `UnsupportedOperationException`; các thuộc tính policy kiểu cũ không tạo enforcement. `AccessController.checkPermission(...)` còn có thể **ném `AccessControlException` vô điều kiện** theo API compatibility, nhưng điều đó **không** có nghĩa file/network API được bảo vệ như xưa.

Điều quan trọng khi di trú plugin framework cũ: không chỉ thay thế cú pháp, mà phải chuyển **thực thi ranh giới bảo mật** sang hệ thống thích hợp. Xem tài liệu Oracle về việc Security Manager bị vô hiệu hóa và JEP 486 ở cuối bài.

## 6. Thực hành Java 17+: plugin đọc được tệp thử nghiệm dù domain không cấp FilePermission

Thí nghiệm là một minh họa **an toàn, chỉ đọc tệp do host tự tạo trong thư mục tạm**. Host tự tạo file chứa text `training-only`, cho plugin biết đường dẫn bằng property `demo.resource.path` để kết quả tái lập; plugin dùng `Files.readString` đọc file đó **không qua plugin API của host**.

Mục tiêu không phải truy cập dữ liệu bí mật hay vượt kiểm soát OS. Mục tiêu là quan sát: **một SecureClassLoader trả permission collection rỗng không ngăn code Java trong cùng JVM đọc tài nguyên mà process được phép đọc khi không có Security Manager**.

### Cấu trúc project

```text
src/
  plugin/
    demo/
      Worker.java
  app/
    LoaderSandboxDemo.java
plugin-classes/  (javac tạo)
app-classes/     (javac tạo)
```

**File `src/plugin/demo/Worker.java`:**

```java
package plugin.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Worker {
    public static String readHostFile() throws IOException {
        String path = System.getProperty("demo.resource.path");
        return Files.readString(Path.of(path)).trim();
    }
}
```

**File `src/app/LoaderSandboxDemo.java`:**

```java
import java.io.FilePermission;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.SecureClassLoader;
import java.security.cert.Certificate;

public final class LoaderSandboxDemo {
    static final class PluginLoader extends SecureClassLoader {
        private final Path root;
        private final URL origin;

        PluginLoader(Path root) throws IOException {
            super(ClassLoader.getPlatformClassLoader());
            this.root = root.toAbsolutePath();
            this.origin = this.root.toUri().toURL();
        }

        @Override
        protected PermissionCollection getPermissions(CodeSource cs) {
            return new Permissions();
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            try {
                byte[] data = Files.readAllBytes(
                        root.resolve(name.replace('.', '/') + ".class"));
                CodeSource cs = new CodeSource(
                        origin, (Certificate[]) null);
                return defineClass(name, data, 0, data.length, cs);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path example =
                Files.createTempFile("harmless-demo-", ".txt");
        try {
            Files.writeString(
                    example, "training-only",
                    StandardCharsets.UTF_8);
            System.setProperty(
                    "demo.resource.path", example.toString());

            PluginLoader loader =
                    new PluginLoader(Path.of("plugin-classes"));
            Class<?> worker = Class.forName(
                    "plugin.demo.Worker", true, loader);

            System.out.println("owned by plugin loader: "
                    + (worker.getClassLoader() == loader));
            System.out.println("code source present: "
                    + (worker.getProtectionDomain()
                            .getCodeSource() != null));

            boolean granted = worker.getProtectionDomain()
                    .getPermissions()
                    .implies(new FilePermission(
                            example.toString(), "read"));

            System.out.println(
                    "file permission in domain: " + granted);

            String value = (String) worker
                    .getMethod("readHostFile")
                    .invoke(null);
            System.out.println("plugin file read: " + value);

            System.out.println("same class on reload: "
                    + (Class.forName(
                            "plugin.demo.Worker", false, loader)
                            == worker));
        } finally {
            System.clearProperty("demo.resource.path");
            Files.deleteIfExists(example);
        }
    }
}
```

### Biên dịch và chạy

Từ thư mục có `src`:

```bash
javac --release 17 -d plugin-classes src/plugin/demo/Worker.java
javac --release 17 -d app-classes src/app/LoaderSandboxDemo.java
java -cp app-classes LoaderSandboxDemo
```

Không đưa `plugin-classes` vào application classpath. Loader của host chỉ nhận `app-classes`, còn loader tùy chỉnh tìm bytecode `plugin.demo.Worker` từ `plugin-classes`. Chương trình dùng API sẵn có trong Java 17+.

**Stdout chính xác đã kiểm chứng trên OpenJDK 24 với `javac --release 17`:**

```text
owned by plugin loader: true
code source present: true
file permission in domain: false
plugin file read: training-only
same class on reload: true
```

### Giải thích 5 dòng đầu ra

1. `owned by plugin loader: true`: instance `PluginLoader` mới là defining loader của `plugin.demo.Worker`; parent không định nghĩa class đó.
2. `code source present: true`: overload `SecureClassLoader.defineClass` đã gắn `CodeSource`; thông tin nguồn **không phải giấy chứng nhận an toàn**.
3. `file permission in domain: false`: `getPermissions` trả collection rỗng, do đó `PermissionCollection.implies(FilePermission(path,"read"))` là `false`.
4. **`plugin file read: training-only`:** plugin vẫn đọc được file do host tạo, bằng direct `Files.readString` khi JVM không dùng Security Manager để từ chối theo domain. Đây là điều cần chứng minh.
5. `same class on reload: true`: với cùng binary name và defining loader, lần nạp tiếp theo tái sử dụng `Class<?>`; property này thuộc class loading, không thuộc authorization.

**Lưu ý môi trường:** Trên JDK 24+ không có Security Manager được bật, nên kết quả minh họa diễn ra trong cấu hình chuẩn không bị OS chặn. Trên JDK 17–23, khi **không bật Security Manager**, cùng nguyên tắc áp dụng; nếu chủ động bật Security Manager cũ với policy phù hợp, hành vi đọc file có thể khác. Ví dụ **không cần** và **không hướng dẫn** bật Security Manager.

**Lưu ý phạm vi an toàn:** Host tự cung cấp đường dẫn tới file do chính nó tạo và xóa sau test. Việc đó minh họa rằng loader không chặn thao tác OS mà process được phép làm; nó **không** kiểm thử đoán đường dẫn bí mật, ký JAR hay khai thác lỗi hệ điều hành. Đừng đặt secrets thực vào system property để mô phỏng test này.

## 7. Tại sao API host chỉ lộ các method an toàn vẫn không tạo sandbox?

Một host có thể thiết kế:

```java
public interface Plugin {
    void execute(PluginContext ctx);
}

public interface PluginContext {
    String getPublicGreeting();
}
```

Đây là **hai khai báo minh họa** cần tách thành `Plugin.java` và `PluginContext.java` nếu muốn biên dịch. Nếu plugin chỉ tự nguyện dùng `ctx.getPublicGreeting()`, API contract có tác dụng giảm misuse.

Nhưng plugin bytecode tùy ý cũng có thể viết:

```java
// Ví dụ ý tưởng bên trong code plugin:
String runtime = System.getProperty("java.version");
java.nio.file.Path path = java.nio.file.Path.of("config.txt");
// Nếu process OS có quyền thì plugin có thể dùng Files API.
```

Không có mechanism nào trong `ClassLoader` mặc định buộc plugin phải gọi `ctx` thay vì thư viện JDK. Không trao reference trực tiếp tới database client cho plugin cũng không đủ nếu nó vẫn có credentials trong environment/process hay quyền đọc tệp config chứa connection string.

**Object capability và trust boundary:** Contract API được kiểm soát có ích nếu plugin **đáng tin** hoặc thực thi trong sandbox thực sự chặn những quyền ngoài contract. Nếu plugin độc hại chạy cùng process mà vẫn gọi API JDK, chỉ hạn chế `PluginContext` là biện pháp **kỷ luật kiến trúc**, không phải phòng thủ tuyệt đối.

## 8. JPMS mạnh hơn classpath encapsulation nhưng cũng không phải sandbox

Từ Java 9, named modules có `requires`, `exports` và `opens`:

```java
module com.example.host {
    exports com.example.plugin.api;
    // Không exports/open packages implementation riêng của host.
}
```

JPMS giúp kiểm soát:

- Module nào **đọc** module nào.
- Public API của package nào được **export** cho module khác.
- Package nào được **open** cho reflective access sâu.
- Một số truy cập qua reflection/bytecode vào internals bị chặn theo module graph.

Nhưng:

- `module-info.java` không phải policy “plugin không được đọc bất kỳ file nào”.
- Plugin có thể vẫn dùng các public API của `java.base` được phép, như `java.nio.file.Files` và `System.getProperty`.
- `--add-opens` và `--add-exports` mở thêm module boundaries khi framework cần; lạm dụng chúng làm giảm encapsulation, chứ không tạo file/network isolation.
- Một module không exported có thể giảm một đường truy cập trực tiếp tới internal host classes, nhưng **không đủ** trước code plugin chạy cùng OS process với những quyền sẵn có.
- Reflection và native/JNI/FFM có các quy định truy cập riêng; không đồng nhất “có module” với “không thể làm hành động nguy hiểm”.

Hãy dùng JPMS để xây module API boundaries rõ ràng. Khi cần cách ly code không tin cậy, thêm security boundary ở tầng **process, OS và hạ tầng**.

## 9. Class-loader lifecycle không đồng nghĩa cô lập tài nguyên

Loader mới có thể nạp cùng binary name ở generation khác, nhưng không chuyển object/static state cũ hay tạo ranh giới quyền mới. `URLClassLoader.close()` chỉ đóng tài nguyên thuộc loader, không dừng threads hoặc hoàn tác filesystem/network side effects. TCCL, registries, threads và static/global state có thể giữ plugin alive hoặc cho plugin ảnh hưởng shared process. Đây là thêm một lý do phải phân biệt lifecycle cleanup với **security isolation**.

## 10. Quy trình phù hợp cho plugin đáng tin

Nếu plugin do đội ngũ kiểm soát/phê duyệt, cần nạp động và tương thích thư viện, class loaders vẫn là công cụ đúng. Kiến trúc tham khảo:

```text
Host application
    |
    +--> shared plugin API (host/parent loader)
    |
    +--> PluginLoader A -> plugin-a.jar + dependencies A
    |
    '--> PluginLoader B -> plugin-b.jar + dependencies B
```

Các nguyên tắc:

1. **API chung** do parent loader định nghĩa. Implementation trong plugin loader có thể implements interface này mà vẫn cast được về API của host.
2. **Không đóng gói trùng bản API** trong từng plugin child loader nếu child tự định nghĩa bản riêng; làm vậy tạo runtime types không tương thích.
3. **Quyết định delegation** rõ ràng. Parent-first thường dễ suy luận; child-first nếu có lý do phiên bản thư viện phải tránh shadow JDK/host API và xử lý các package/loader constraints phù hợp.
4. **Kiểm tra nguồn artifact**: version, checksum, chữ ký khi cần và provenance đáng tin. Đọc JAR thành công không chứng minh đáng tin.
5. **Quản lý vòng đời**: unregister callbacks, shutdown tasks, close I/O, dọn TCCL, bỏ references và sử dụng `URLClassLoader.close()` khi thích hợp.
6. **Có hợp đồng lỗi** và monitoring cho plugin. Một exception của plugin không nên phá state host ngoài ý muốn; dùng transactional/compensation mechanisms khi cần.
7. **Không gọi “an toàn với untrusted code”** chỉ vì loaders tách biệt. Đây là thiết kế plugin **đáng tin nhưng có dependencies riêng**.

Một loader child-first tùy chỉnh, nếu override `loadClass`, còn cần đúng đồng bộ `getClassLoadingLock`, `findLoadedClass` và tránh định nghĩa trùng cùng binary name. Đó là bài toán correctness/class identity, không phải security sandbox.

## 11. Thiết kế cho plugin không tin cậy: tiến trình riêng

Khi threat model bao gồm plugin cố tình vượt contract, kiến trúc điển hình:

```text
                     Host process (trusted)
                +----------------------------+
                | API gateway / plugin broker|
                | Authenticate + authorize   |
                | Input validation + limits  |
                +--------------+-------------+
                               |
                      IPC / network protocol
                               |
                +--------------v-------------+
                | Worker process (untrusted) |
                | Different OS identity      |
                | Limited filesystem mounts  |
                | Egress network policy      |
                | CPU/memory/time limits     |
                | Auditing and termination   |
                +----------------------------+
```

Host không trực tiếp đưa DB credentials, file descriptors, process handles hoặc host internals cho worker nếu không cần thiết. Hãy cho worker biết **chỉ thông tin và thao tác cần dùng**. Một plugin xử lý ảnh chẳng hạn có thể nhận bytes qua IPC rồi trả ảnh đã xử lý, không cần quyền đọc toàn bộ thư mục cấu hình host.

Các lớp kiểm soát nên cân nhắc:

| Ranh giới/kiểm soát | Mục tiêu |
| --- | --- |
| Process riêng | Worker crash/`System.exit` không trực tiếp dừng JVM của host. |
| OS user/account riêng | File permissions giới hạn worker khỏi dữ liệu host. |
| Read-only/masked mounts | Ngăn sửa/đọc các đường dẫn không cần thiết. |
| Network namespace / egress rules | Ngăn kết nối ngoài mục đích, giảm exfiltration. |
| CPU/memory/process limits | Giảm DoS do vòng lặp hoặc cấp phát vô hạn. |
| Wall-clock timeout + kill process | Có thể kết thúc worker không hợp tác; thread interrupt cùng JVM yếu hơn. |
| IPC validation + authorization | Không cho worker gọi host operation ngoài quyền business. |
| Logs, audit, update policy | Quan sát hành vi, gỡ lỗi, rollback khi artifact lỗi. |

**Chú ý:** chỉ tách process mà vẫn chạy cùng OS account có toàn quyền đọc host secrets thì isolation vẫn có lỗ hổng. Container “mặc định” cũng không bảo đảm an toàn khi được cấp host mounts rộng, privileged mode hoặc network mở. Thiết kế phải dựa trên threat model và cấu hình thật.

Một số hệ cần mức cô lập cao hơn: microVM/hypervisor, dedicated machine, sandbox chuyên dụng hoặc managed execution service. ClassLoader vẫn có thể dùng **bên trong worker process** để hỗ trợ tổ chức dependencies, nhưng boundary chống mã độc nằm ở lớp khác.

## 12. Rủi ro cung cấp dữ liệu và xác thực plugin

**Nguồn đáng tin không có nghĩa code không lỗi.** Một plugin có chữ ký hợp lệ có thể chứa bug logic, lỗi dependency hoặc hành vi không phù hợp. Chữ ký/hash có mục đích xác minh artifact đã được phân phối như mong đợi, không tự giới hạn quyền runtime.

Pipeline tải plugin đáng tin cậy có thể gồm:

- Chỉ chấp nhận artifacts từ repository/nhà cung cấp đã phê duyệt.
- Chốt dependency versions, kiểm tra checksum/signatures theo chain of trust phù hợp.
- Quét lỗi/phụ thuộc dễ bị tấn công và ký artifact trong supply-chain pipeline.
- Validate plugin manifest, declared capabilities, supported API version.
- Dùng allowlist packages/modules/entrypoints để tránh load nhầm implementation.
- Tạo loader mới cho một generation; không thêm JAR tùy ý vào system loader global.
- Quản lý rollback nếu plugin gây lỗi.

Các bước này giảm rủi ro **nguồn cung cấp và correctness**, nhưng **không chuyển ClassLoader thành sandbox** khi một artifact độc hại vẫn lọt qua. Không coi `CodeSource.location` do custom loader tự gán là bằng chứng xác thực.

## 13. Sai lầm phổ biến và cách sửa

| Nhận định sai | Vì sao sai | Phương án đúng |
| --- | --- | --- |
| “Plugin có ClassLoader riêng nên không thể gọi `Files`.” | `Files` thuộc JDK, vẫn được dùng theo quyền process/module. | Giới hạn qua OS/process boundary. |
| “Parent = null nghĩa không thấy Java core.” | Bootstrap classes vẫn có thể được truy cập. | Hiểu delegation; không dùng parent null làm sandbox. |
| “Trả `new Permissions()` nghĩa không có quyền OS.” | PermissionCollection không được enforce khi không có Security Manager. | Không dựa vào legacy policy để chặn I/O. |
| “`SecureClassLoader` chắc chắn an toàn vì chữ Secure.” | Nó hỗ trợ metadata domain, không phải security boundary hiện đại. | Dùng cho source/class-loading metadata; sandbox ở process/OS. |
| “`CodeSource` URL là bằng chứng chữ ký hợp lệ.” | Metadata có thể được gán thủ công. | Verify artifact/signatures từ nguồn thực. |
| “`module-info.java` không export package nên plugin không thể đọc file.” | JPMS kiểm soát package/module access, không phải OS filesystem. | Phân biệt encapsulation với capability enforcement. |
| “Không truyền DB client reference cho plugin là đủ.” | Plugin có thể đọc env/config hoặc tự dùng network API nếu được phép. | Tách credentials và process permissions; IPC kiểm soát. |
| “`URLClassLoader.close()` kill plugin.” | Close chỉ xử lý loader resources theo contract, không terminate threads/process. | Lifecycle shutdown và process termination độc lập. |
| “Catch `Throwable` quanh plugin bảo vệ host hoàn toàn.” | Không ngăn resource exhaustion, state mutation hay `System.exit`. | Process isolation và quota. |
| “Security Manager còn bật được trên JDK 24 bằng flag cũ.” | JEP 486 đã vô hiệu hóa vĩnh viễn. | Migration sang cơ chế khác. |
| “Chỉ dùng `Thread.interrupt()` để bắt plugin dừng.” | Code có thể không hợp tác hoặc không kiểm tra interrupt. | Timeout/kill worker process và limits. |
| “Chỉ scan bytecode là sandbox.” | Static scan/allowlist có thể bỏ sót dynamic behavior; không phải enforcement chung. | Kết hợp provenance, validation và sandbox cấp hệ thống. |

## 14. Checklist trước khi triển khai hệ plugin

**Đối với plugin đáng tin:**

1. Xác định package/API nào phải được host parent loader định nghĩa.
2. Đảm bảo plugin không mang bản duplicate của host interface.
3. Chọn đúng URLClassLoader/custom loader, nguồn class và resource.
4. Xử lý phiên bản, dependency conflicts, ServiceLoader/TCCL nếu có.
5. Xác minh artifact provenance phù hợp nhu cầu.
6. Có lifecycle quản lý threads, callbacks, streams, loaders và references.
7. Test `ClassCastException`, `ClassNotFoundException`, `LinkageError` và upgrade scenario.

**Đối với plugin không tin cậy, bắt buộc xem xét thêm:**

1. Ranh giới process/machine và OS identity riêng.
2. Quyền filesystem/network tối thiểu, không mount host secrets.
3. CPU/memory/thread/process limits, deadline và kill policy.
4. IPC protocol có schema, validation, authn/authz và payload limits.
5. Secrets chỉ nằm ở host trusted side khi có thể.
6. Logging, audit trail, sandbox escape threat review và updates.
7. Mô hình độ tin cậy nêu rõ loại code nào được phép vào mỗi execution tier.

Một yêu cầu “cấm plugin đọc `/etc`” phải được kiểm thử ở **filesystem/OS restriction**, không thể chứng minh bằng việc tạo custom ClassLoader rồi thấy package không có trên classpath.

## 15. Câu hỏi tự kiểm tra

1. **ClassLoader có tạo ra một OS process mới không?**  
   Không. Class được định nghĩa bởi loader khác vẫn chạy trong JVM process hiện tại.

2. **Hai loaders định nghĩa cùng `com.example.Widget` có chung runtime class không?**  
   Không, nếu defining loader khác nhau thì class identity khác nhau dù binary name trùng.

3. **Vì sao parent delegation không ngăn plugin gọi `java.nio.file.Files`?**  
   Delegation tìm và chia sẻ các lớp Java runtime; nó không cài permissions hạn chế hành vi của bytecode.

4. **`ProtectionDomain.getPermissions()` không implies `FilePermission(read)` thì `Files.readString` luôn bị từ chối?**  
   Không. Khi không có Security Manager, collection đó không tự tạo enforcement. Demo kiểm chứng điều này trên JDK 24.

5. **Từ bản Java nào Security Manager bị vô hiệu hóa vĩnh viễn?**  
   JDK 24 (JEP 486), sau giai đoạn deprecated for removal từ Java 17 (JEP 411).

6. **Một JAR được ký hợp lệ có nghĩa plugin bị giới hạn network không?**  
   Không. Signing liên quan provenance/integrity, không phải giới hạn quyền hệ điều hành.

7. **Named modules với `exports`/`opens` có bảo đảm chống plugin đọc file không?**  
   Không. Chúng kiểm soát truy cập package và reflection, không phải policy file/network.

8. **`URLClassLoader.close()` có dừng threads của plugin không?**  
   Không. Thực thi plugin và lifecycle threads cần cơ chế riêng.

9. **Nếu plugin độc hại cần xử lý dữ liệu nhưng không được truy cập host secrets, kiến trúc hợp lý là gì?**  
   Worker process riêng, OS identity/rights giới hạn, IPC được kiểm soát, không truyền secrets vô cớ.

10. **Tại sao plugin API chỉ có `getPublicGreeting` không phải sandbox đủ mạnh?**  
    Plugin bytecode có thể gọi trực tiếp JDK APIs mà không đi qua API wrapper.

11. **Nếu hai plugin tin cậy cần hai version thư viện khác nhau thì dùng gì?**  
    Separate class loader namespaces, chia sẻ đúng API qua parent, quản lý lifecycle.

12. **Tài liệu nào giải thích trạng thái Security Manager mới?**  
    JEP 486 và Oracle Java SE 24 Security Developer's Guide; xem mục 16.

## 16. Tài liệu tham khảo chính thức

- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): defining loader, `loadClass`, `defineClass`, delegation, protection domain.
- [Java SE 25 — SecureClassLoader](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureClassLoader.html): `CodeSource`, `getPermissions`, API Note về permissions khi Security Manager không còn.
- [Java SE 21 — CodeSource](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/CodeSource.html): location và certificates/signers.
- [Java SE 21 — ProtectionDomain](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/ProtectionDomain.html): metadata domain và tập permissions.
- [JEP 411 — Deprecate the Security Manager for Removal](https://openjdk.org/jeps/411): lịch sử Java 17.
- [JEP 486 — Permanently Disable the Security Manager](https://openjdk.org/jeps/486): thay đổi trong JDK 24.
- [Oracle Java SE 24 — The Security Manager Is Permanently Disabled](https://docs.oracle.com/en/java/javase/24/security/security-manager-is-permanently-disabled.html): hành vi `System`, `Policy`, `AccessController` và khuyến nghị migration.
- [Oracle Secure Coding Guidelines for Java SE](https://www.oracle.com/java/technologies/javase/seccodeguide.html): hạn chế isolation trong cùng process và khuyến nghị separate processes/OS-level controls cho code không tin cậy.
- [JVMS 5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): runtime type identity theo binary name và defining loader.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-architecture-010">Nhận diện mô hình cô lập trong container và test runner</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện mô hình cô lập trong container và test runner

## 1. Vì sao phải nhận diện đúng kiểu cô lập?

Trong Java, **cô lập (isolation)** không chỉ có một nghĩa. Một ứng dụng server có thể chạy nhiều web applications trong cùng JVM nhưng mỗi ứng dụng dùng một ClassLoader riêng. Một test runner có thể tạo đối tượng test mới cho từng phương thức nhưng vẫn giữ **cùng class loader và các static fields**. Một build tool có thể chạy tests trong **JVM forked process**; khi ấy cả heap, static fields, loader instances và nhiều trạng thái VM đều tách khỏi process build.

Nếu không biết hệ thống đang dùng tầng cô lập nào, ta dễ kết luận sai:

- “Hai ứng dụng cùng tên class nên chắc là một kiểu.”
- “JUnit tạo test object mới nên static fields được reset.”
- “Đóng URLClassLoader nghĩa là JVM đã unload class.”
- “Chạy test song song bằng nhiều thread giống chạy bằng nhiều JVM.”
- “Container đã cô lập plugin nên các plugin không thể ảnh hưởng tới nhau.”
- “Plugin class loader có thể nhìn thấy mọi class trong host.”
- “Mọi container đều dùng parent-first giống ClassLoader mặc định.”

**Mục tiêu bài học:** phân biệt và nhận diện **cô lập theo object/test instance, thread, ClassLoader, Module/ModuleLayer, JVM process và container hệ điều hành**, với các dấu hiệu runtime cụ thể để debug ClassLoader trong **application container** và **test runner**.

Chủ đề thuộc nhóm **Kiến trúc plugin**, nên trọng tâm là **ranh giới tìm/nạp class, định danh kiểu, chia sẻ API, quản lý trạng thái và vòng đời**; không coi cô lập là một công tắc bảo mật duy nhất.

## 2. Bản đồ các tầng cô lập

| Tầng | Cách hình thành | Có thể tách | Không mặc nhiên tách |
|---|---|---|---|
| Test instance/object | Tạo instance mới của class test | Instance fields của object mới | Static fields, JVM, class loader, database |
| Thread | Chạy code trên Thread khác | Call stack, thread-local state | Heap, static fields, class definitions |
| ClassLoader | Loader khác định nghĩa cùng binary name | Runtime class identity, static fields của class riêng, class namespace | Process, heap nói chung, file/socket, APIs chia sẻ |
| JPMS Module/Layer | Module graph, `requires`/`exports`/`opens` và loader mapping | Readability/encapsulation, service discovery, phạm vi module | Process, heap, bảo mật sandbox tổng quát |
| JVM process | OS process khác chạy JVM khác | Heap/GC, static state, class loader graph, system properties, process-level caches | File system, database, network, external services |
| OS container | Namespaces, cgroups, mount/network configuration | Nhiều tài nguyên ở mức OS tùy cấu hình | External resources/volumes, kernel, endpoint dùng chung |

**Không có thứ tự “tầng cao bao hàm mọi quyền bảo mật” đơn giản:** ClassLoader tách danh tính runtime type nhưng không cấm plugin tự gọi API file/network; module đóng gói API chứ không phải sandbox; JVM process tách bộ nhớ nhưng vẫn có thể dùng chung một database. Một OS container còn phụ thuộc cấu hình mount, network, capabilities và chính sách triển khai.

Để chẩn đoán tình huống cụ thể, phải xác định **cái gì được chia sẻ**, **cái gì được tái tạo**, **khi nào được làm sạch**.

## 3. ClassLoader isolation: cùng tên không đồng nghĩa cùng runtime class

Với class bình thường, JVM phân biệt các runtime classes bằng **binary name** và **defining ClassLoader**. Vì thế:

```text
Shared Parent Loader
     ├── shared.api.PluginContract
     │
     ├── AppLoader-A
     │     └── app.impl.Worker
     │
     └── AppLoader-B
           └── app.impl.Worker
```

Hai bản `app.impl.Worker` có thể cùng binary name, cùng bytes từ cùng JAR nhưng **khác runtime class** nếu được định nghĩa bởi hai loaders độc lập. Mỗi bản có **static fields riêng** và có thể khởi tạo class riêng.

Một ví dụ về danh tính:

```java
// Trích đoạn, không phải chương trình độc lập:
Class<?> a = loaderA.loadClass("app.impl.Worker");
Class<?> b = loaderB.loadClass("app.impl.Worker");

System.out.println(a.getName().equals(b.getName())); // true
System.out.println(a == b);                          // false nếu mỗi loader tự define
```

Câu thứ hai **chỉ** cho `false` nếu mỗi loader thực sự định nghĩa bản riêng. Nếu cả hai ủy quyền cho cùng parent đã có `app.impl.Worker`, `a == b` có thể là `true`.

### Defining loader khác initiating loader

`loadClass(name)` trên loader con có thể tìm class từ parent. Khi đó:
- Loader con là bên **khởi xướng yêu cầu**.
- Defining loader của class trả về có thể là **parent**.
- `type.getClassLoader()` cho biết **defining loader**, không nhất thiết là loader ban đầu được gọi.

Để nhận diện cô lập thật, **không chỉ nhìn tên loader của biến được truyền vào `loadClass`**. Hãy xem chính đối tượng `Class<?>` được trả về và defining loader của nó.

## 4. Parent-first, child-first và nguồn class thực sự được chọn

Thuật toán mặc định của `ClassLoader.loadClass` theo hướng parent-first:

```text
loadClass(name)
    │
    ▼
findLoadedClass(name)
    │ không có
    ▼
parent.loadClass(name) / bootstrap
    │ cha không tìm thấy
    ▼
findClass(name) trên loader hiện tại
    │
    ▼
Class<?> runtime
```

Trong hệ plugin, **parent-first** rất phù hợp với những **API chung**: service interfaces, DTO và protocol types phải được host và plugin hiểu như cùng một runtime class.

Ngược lại, **child-first** có thể có ích khi mỗi plugin cần bản dependency riêng, nhưng phải có quy tắc rõ:
- Không cố override những Java platform classes.
- Không định nghĩa lại host/shared API ở child.
- Xác định package nào là private của plugin và package nào thuộc container.
- Đừng catch mọi `LinkageError` rồi âm thầm fallback sang bản class khác.
- Kiểm tra package access và type identity ở đường callback giữa plugin và host.

**Child-first không đồng nghĩa cô lập hoàn toàn:** class platform, API chung và các nguồn tài nguyên có thể vẫn dùng chung; chính sách class/resource lookup thực tế còn tùy framework.

## 5. Container web application: sơ đồ ClassLoader thường gặp

Các application servers/web containers như Tomcat dùng nhiều loaders để phân chia phần runtime container và các web applications. Mô hình khái niệm thường gặp:

```text
Bootstrap / Platform
         │
       System
         │
       Common
       /    \
   Webapp-A  Webapp-B
      │         │
  WEB-INF/  WEB-INF/
  classes   classes
  + lib     + lib
```

Ở cấu hình thông thường:
- **Common loader** cung cấp thư viện container hoặc thành phần chia sẻ.
- **Webapp-A** nhìn thấy lớp/resource riêng trong `WEB-INF/classes` và JAR ở `WEB-INF/lib`.
- **Webapp-B** có vùng triển khai riêng tương ứng.
- Mỗi webapp loader có thể định nghĩa bản riêng của cùng binary name từ JAR nằm trong webapp.
- Những API/platform classes và một số API servlet/container thường được ủy quyền cho loader phía trên.

**Ví dụ Tomcat:** tài liệu Class Loader How-To mô tả Webapp loader mặc định ưu tiên các repositories nội bộ webapp đối với nhiều application classes (**local-first**), khác với parent-first mặc định của `ClassLoader`; nhưng có ngoại lệ cho JRE base và các Jakarta EE API do container cung cấp. Có thể có cấu hình `<Loader delegate="true"/>` khiến quy tắc ưu tiên delegation thay đổi.

Điều này cho thấy **“Java ClassLoader là parent-first” không đủ để đoán hành vi mọi container**. Phải đọc chính sách của runtime cụ thể và deployment setup.

### Khi nào hai webapps chia sẻ kiểu?

Giả sử cả hai webapps dùng:
- API `jakarta.servlet.Servlet` do container cung cấp từ tầng chung.
- `com.example.JsonHelper` được đóng gói riêng trong từng `WEB-INF/lib`.

Chúng có thể dùng **cùng runtime type** `jakarta.servlet.Servlet` nhưng dùng **hai runtime types khác nhau** `com.example.JsonHelper`. Nếu `JsonHelper` chứa một static cache, mỗi webapp sẽ có cache riêng **nếu** mỗi loader thật sự tự định nghĩa class đó. Nếu class được đặt ở common loader và được dùng từ tầng đó, static cache có thể chia sẻ.

**Không đoán theo JAR name**; một JAR có mặt ở hai nơi không đủ để quyết định class nào đang được dùng. Cần kiểm tra defining loader và code source.

## 6. Shared API là “cầu nối” bắt buộc giữa các loader

Nếu plugin A triển khai `api.Service` được định nghĩa bởi loader A, còn host dùng `api.Service` được định nghĩa bởi parent, hai interfaces cùng tên **không phải cùng kiểu**. Kết quả có thể là:
- `ClassCastException` khi ép kiểu.
- `ServiceConfigurationError` khi ServiceLoader thấy provider không assignable.
- `LinkageError` hoặc `IllegalAccessError` khi cấu trúc dependency khác runtime.
- Reflection thấy tên giống nhưng `==` trên `Class<?>` trả `false`.

Thiết kế tốt:

```text
             Host / Shared API Loader
                      │
                  api.Service
                      │
           ┌──────────┴─────────┐
           │                    │
      PluginLoader-A       PluginLoader-B
       impl.ServiceA        impl.ServiceB
           │                    │
           └──── implements api.Service ────┘
```

Plugin implementations riêng, **API dùng chung**. Host nhận đối tượng qua interface chung, không ép sang implementation class của một loader khác.

## 7. Demo Java 17+: hai loader, hai static states, một shared interface

Đây là phép thử thực tế để **nhận diện cô lập theo ClassLoader**. Chương trình tạo **hai `URLClassLoader` instances** trên cùng directory chứa implementation. Chúng cùng dùng host loader làm parent, nên cùng dùng một interface `CounterService`. Nhưng chúng định nghĩa hai bản class implementation riêng với static counters khác nhau.

### 7.1. Cấu trúc thư mục

```text
loader-isolation/
├── src/
│   ├── api/CounterService.java
│   ├── plugin/CounterImpl.java
│   └── host/IsolationDemo.java
├── host-out/
└── plugin-out/
```

### 7.2. src/api/CounterService.java

```java
package example.api;

public interface CounterService {
    int next();
}
```

### 7.3. src/plugin/CounterImpl.java

```java
package example.impl;

import example.api.CounterService;

public final class CounterImpl implements CounterService {
    private static int counter = 0;

    public CounterImpl() {
    }

    @Override
    public int next() {
        return ++counter;
    }
}
```

### 7.4. src/host/IsolationDemo.java

```java
package example.host;

import example.api.CounterService;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class IsolationDemo {
    public static void main(String[] args) throws Exception {
        URL location = Path.of("plugin-out").toUri().toURL();
        ClassLoader parent = IsolationDemo.class.getClassLoader();

        try (URLClassLoader a = new URLClassLoader(
                     new URL[] {location}, parent);
             URLClassLoader b = new URLClassLoader(
                     new URL[] {location}, parent)) {

            Class<?> classA = a.loadClass("example.impl.CounterImpl");
            Class<?> classB = b.loadClass("example.impl.CounterImpl");

            CounterService a1 = (CounterService)
                    classA.getDeclaredConstructor().newInstance();
            CounterService a2 = (CounterService)
                    classA.getDeclaredConstructor().newInstance();
            CounterService b1 = (CounterService)
                    classB.getDeclaredConstructor().newInstance();

            System.out.println("A first: " + a1.next());
            System.out.println("A second: " + a2.next());
            System.out.println("B first: " + b1.next());

            System.out.println("same implementation class: "
                    + (classA == classB));
            System.out.println("both use shared API: "
                    + (a1 instanceof CounterService
                       && b1 instanceof CounterService));
            System.out.println("A defined by A: "
                    + (classA.getClassLoader() == a));
            System.out.println("B defined by B: "
                    + (classB.getClassLoader() == b));

            try {
                classA.cast(b1);
                System.out.println("unexpected cross-cast");
            } catch (ClassCastException expected) {
                System.out.println("cross-loader cast rejected");
            }
        }
    }
}
```

### 7.5. Biên dịch đúng classpath

Tạo các thư mục. Trên Bash:

```bash
mkdir -p host-out plugin-out
```

Trên Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force host-out,plugin-out | Out-Null
```

Biên dịch/chạy với JDK 17+:

```bash
javac --release 17 -d host-out src/api/CounterService.java src/host/IsolationDemo.java
javac --release 17 -cp host-out -d plugin-out src/plugin/CounterImpl.java
java -cp host-out example.host.IsolationDemo
```

**Tuyệt đối không thêm `plugin-out` vào host classpath khi thử ví dụ**. Nếu có, parent loader có thể định nghĩa `example.impl.CounterImpl` trước; hai URLClassLoader sẽ ủy quyền cho cùng parent và kết quả cô lập static states không còn đúng.

Cấu trúc output:

```text
host-out/
├── example/api/CounterService.class
└── example/host/IsolationDemo.class

plugin-out/
└── example/impl/CounterImpl.class
```

### 7.6. Stdout mong đợi

```text
A first: 1
A second: 2
B first: 1
same implementation class: false
both use shared API: true
A defined by A: true
B defined by B: true
cross-loader cast rejected
```

**Giải thích:**

1. `classA` và `classB` có cùng binary name `example.impl.CounterImpl`, nhưng hai defining loader instances khác nhau; do đó `classA != classB`.
2. Static field `counter` của classA được tăng từ 0 → 1 → 2; classB có **static counter riêng**, tăng từ 0 → 1.
3. Cả hai classes implements **cùng `CounterService` do parent định nghĩa**, vì host/shared API chỉ được đặt ở `host-out`. Do đó có thể cast objects của cả hai về service interface.
4. Nhưng `classA.cast(b1)` không hợp lệ: b1 là instance của classB, không phải classA. Class cast dựa trên **type identity**, không phải tên chuỗi.
5. `URLClassLoader.close()` khi rời try-with-resources đóng tài nguyên loader quản lý; nó **không chứng minh class đã unload hoặc static state bị GC ngay**.

Đây là ví dụ **một JVM, hai loaders**. Các biến hệ thống, heap, sockets và filesystem vẫn dùng chung process. Nó không cung cấp process isolation.

## 8. Test runner: cô lập test instance không phải cô lập ClassLoader

**JUnit Jupiter** mặc định tạo **một instance mới của test class cho mỗi test method** (lifecycle `PER_METHOD`). Điều này giảm nhiễm **instance fields**, nhưng không có nghĩa:
- JVM mới cho mỗi method;
- ClassLoader mới cho mỗi method;
- static fields của class test được reset;
- database/file/temp directory được reset;
- code trong background thread tự dừng.

Mẫu JUnit 5 minh họa:

```java
// Trích đoạn test class khi dự án đã có JUnit Jupiter dependency:
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StateIsolationTest {
    private int instanceCount = 0;
    private static int sharedCount = 0;

    @Test
    void first() {
        assertEquals(1, ++instanceCount);
        ++sharedCount;
    }

    @Test
    void second() {
        assertEquals(1, ++instanceCount);
        ++sharedCount;
    }
}
```

Đây là **trích đoạn dự án JUnit**, không phải chương trình Java SE chạy độc lập; cần JUnit Jupiter dependency, engine và test runner. Hai phép assert với instance field sẽ đúng trong mặc định `PER_METHOD`. **Không dự đoán số cuối của sharedCount theo thứ tự test** nếu không đặt quy tắc execution order; giá trị static tồn tại theo runtime class, không reset chỉ vì JUnit tạo test instance mới.

Có thể dùng `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` nếu muốn một test object dùng chung; điều này càng tăng khả năng chia sẻ instance state giữa methods, nhưng **vẫn không thay đổi bản chất static hoặc JVM process isolation**.

### Static contamination qua tests

Một class:

```java
public final class GlobalState {
    public static int count = 0;
}
```

Nếu hai tests thuộc cùng defining loader trong cùng JVM, chúng có thể thấy **cùng `GlobalState.count`**. Test một tăng biến; test hai chạy sau có thể nhận giá trị khác giả định. Đó là nguyên nhân phổ biến của tests chỉ fail khi chạy cả suite, nhưng pass khi chạy riêng.

Cách sửa:
- Reset static state có kiểm soát ở `@BeforeEach`/`@AfterEach` nếu phù hợp.
- Tránh global mutable static state khi thiết kế ứng dụng.
- Dùng dependency injection/test fixtures độc lập.
- Nếu thư viện bên thứ ba không thể reset, dùng **forked JVM** hoặc lớp isolation mạnh hơn theo công cụ build.

## 9. Demo Java 17+ không cần JUnit: static state trong một process

Tạo file `ProcessStateDemo.java`:

```java
public final class ProcessStateDemo {
    private static int counter = 0;

    public static void main(String[] args) {
        System.out.println("test A: " + ++counter);
        System.out.println("test B: " + ++counter);
    }
}
```

Chạy:

```bash
javac ProcessStateDemo.java
java ProcessStateDemo
java ProcessStateDemo
```

Mỗi process in cùng hai dòng:

```text
test A: 1
test B: 2
```

Vì ta chạy `java ProcessStateDemo` **hai lần**, shell tạo **hai JVM processes**, và static state bắt đầu lại trong process thứ hai. Trong **một lần chạy**, “test A” và “test B” (đây chỉ là các nhãn in) cùng dùng class `ProcessStateDemo` nên chia sẻ counter.

Đây là phép đối chiếu **process boundary**: hai JVM khác nhau không chia sẻ cùng heap/class static fields. Dĩ nhiên, cả hai vẫn có thể thao tác cùng file hoặc database nếu code yêu cầu.

## 10. Gradle Test: forked JVM và worker reuse

Với Gradle Java/JVM testing, `Test` task thường chạy tests trong **forked JVM worker process** tách khỏi process build. Nhưng **một worker process có thể chạy nhiều test classes**, nên **forked JVM không mặc nhiên có nghĩa mỗi test class/method một JVM**.

Các cấu hình chính:

| Gradle Test property | Ý nghĩa thực tế |
|---|---|
| `maxParallelForks` | Số test JVM worker processes tối đa chạy song song cho một Test task |
| `forkEvery` | Số test classes/definitions tối đa một process xử lý trước khi được thay bằng process mới; `0` nghĩa là không áp giới hạn |
| `maxHeapSize` | Heap size của forked test JVM |
| `jvmArgs` | JVM options cho workers |
| `useJUnitPlatform()` | Chọn chạy test bằng JUnit Platform |

Ví dụ `build.gradle.kts` của dự án Gradle Kotlin DSL:

```kotlin
tasks.test {
    useJUnitPlatform()
    maxParallelForks = 2
    forkEvery = 1
    maxHeapSize = "512m"
}
```

Đây là **đoạn cấu hình Gradle**, không phải Java source tự chạy bằng `javac`. Phải có Gradle project/JUnit dependencies thích hợp.

Diễn giải:
- Cho phép tối đa hai forked test JVM workers chạy đồng thời theo giới hạn Gradle.
- `forkEvery=1` định hướng tạo process mới sau mỗi test class/definition, tăng cô lập process **giữa test classes**, nhưng tốn thời gian khởi động JVM.
- Không đồng nghĩa mỗi test **method** được cô lập bằng process khác.
- Những tests dùng chung filesystem/database/external service vẫn có thể gây nhiễm lẫn nhau, dù ở hai JVM processes.

**Cẩn trọng hiệu năng:** giá trị `forkEvery` quá nhỏ làm test suite chậm rõ rệt. Chỉ tăng process isolation khi có nhu cầu rõ ràng như library giữ static state khó reset hoặc memory/resource leaks cần hạn chế.

## 11. Maven Surefire/Failsafe: fork process và ClassLoader strategy

**Maven Surefire** có nhiều cách khởi chạy tests và dựng test classpath. Không nên coi nó luôn chỉ là một `java -cp ...` trong chính Maven JVM.

Những tùy chọn hữu ích khi debug:
- `forkCount`: cấu hình số lượng forked JVM test processes trong các phiên bản plugin hỗ trợ.
- `reuseForks`: điều khiển việc tái sử dụng forked processes giữa nhiều tests/test classes theo cấu hình.
- `useSystemClassLoader`: ảnh hưởng chiến lược sử dụng system class loader so với isolated loader trong những chế độ chạy.
- `useManifestOnlyJar`: sử dụng manifest-only JAR cho classpath dài khi phù hợp.
- `argLine`: truyền JVM arguments cho forked test process trong cấu hình thường gặp.

Trích đoạn `pom.xml`:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <version>3.5.3</version>
      <configuration>
        <forkCount>1</forkCount>
        <reuseForks>true</reuseForks>
      </configuration>
    </plugin>
  </plugins>
</build>
```

Đây là **trích đoạn POM**, không phải `pom.xml` đầy đủ để chạy độc lập. Plugin version là ví dụ cấu hình, cần đối chiếu với phiên bản dự án thực tế.

Nếu test class được nạp bởi **isolated test ClassLoader** thay vì system class loader, có thể thấy:
- `Class.forName(name)` từ test code tìm classes khác với code chạy trong Maven/plugin realm.
- Resources/providers tìm qua TCCL thay đổi.
- Reflection hoặc `instanceof` giữa thư viện trong test và Maven plugin class realm có thể không tương thích do class identity.

**Fork process và loader isolation là hai quyết định tách biệt:** fork tạo JVM mới; bên trong process đó vẫn có class loader hierarchy và chiến lược nạp riêng. Không sửa một lỗi type identity chỉ bằng việc đoán `forkCount` sẽ giải quyết mọi thứ.

## 12. Nhận diện test chạy song song bằng thread hay process

Một số cơ chế chạy song song:
- JUnit Platform có thể thực hiện methods/classes trên nhiều worker threads trong **cùng một JVM**.
- Gradle `maxParallelForks` chạy nhiều **JVM test worker processes**.
- Build system hoặc CI có thể chạy nhiều test tasks/jobs trong nhiều processes/machines.
- Một custom runner có thể tạo class loaders độc lập trong cùng JVM để thực thi test suites.

Bảng dấu hiệu:

| Dấu hiệu quan sát | Nhiều threads, một JVM | Nhiều ClassLoaders, một JVM | Nhiều JVM processes |
|---|---|---|---|
| Process ID | Giống | Giống | Khác |
| Static state của cùng runtime class | Thường chia sẻ | Riêng nếu được định nghĩa riêng | Riêng |
| `Class<?>` của cùng binary name | Cùng nếu cùng defining loader | Có thể khác | Không so sánh object trực tiếp giữa processes |
| Heap | Chung | Chung | Tách |
| ClassLoader identity | Thường cùng | Khác | Mỗi process có graph riêng |
| ThreadLocal | Riêng theo thread | Phụ thuộc thread; không phải auto loader isolation | Không chia sẻ process |
| File/database ngoài process | Có thể chung | Có thể chung | Vẫn có thể chung |

**Một JVM process** được xác nhận qua process-level diagnostics. **Hai Java objects của loader khác nhau** chưa đủ nói chúng định nghĩa hai classes riêng, vì parent delegation có thể trả cùng class.

## 13. Runtime probes: in ra điều gì để biết đang bị cô lập ở đâu?

Khi chạy trong container/test runner, tạo helper như sau:

```java
public final class RuntimeProbe {
    private RuntimeProbe() {
    }

    public static void print(Class<?> type) {
        System.out.println("pid=" + ProcessHandle.current().pid());
        System.out.println("thread=" + Thread.currentThread().getName());
        System.out.println("name=" + type.getName());
        System.out.println("loader=" + type.getClassLoader());
        System.out.println("module=" + type.getModule());
        System.out.println("TCCL="
                + Thread.currentThread().getContextClassLoader());
        System.out.println("source="
                + (type.getProtectionDomain().getCodeSource() == null
                    ? "<unknown>"
                    : type.getProtectionDomain().getCodeSource().getLocation()));
    }

    public static void main(String[] args) {
        print(RuntimeProbe.class);
    }
}
```

Biên dịch/chạy:

```bash
javac RuntimeProbe.java
java RuntimeProbe
```

**Không đưa stdout cố định** cho PID, thread name, loader `toString()` hay CodeSource: chúng phụ thuộc OS, JDK, IDE, runner và đường làm việc. Khi cần so sánh dữ liệu thật, chạy probe trong **mỗi test/application/plugin boundary** và đối chiếu.

### Giải thích từng trường

- **PID:** có thuộc cùng JVM process không? Một process có thể có nhiều class loaders.
- **Thread:** các tác vụ khác thread có thể chia sẻ class/static state.
- **Class name:** chỉ định binary name; không đủ để kết luận class identity.
- **ClassLoader:** cho biết defining loader của `type`; với bootstrap thường được biểu diễn bằng `null`.
- **Module:** runtime module của class; named/unnamed/module layer liên quan access.
- **TCCL:** loader làm **ngữ cảnh khám phá** cho một số SPI/framework, không nhất thiết là defining loader của class.
- **CodeSource:** nếu có, cho biết location gắn với protection domain; có thể `null`, synthetic hoặc không có URL hữu ích. Không coi nó là bằng chứng bảo mật tuyệt đối.

Tốt hơn việc chỉ đọc `loader.toString()`: so sánh trực tiếp **reference equality** (`a.getClassLoader() == b.getClassLoader()`) trong cùng JVM và kiểm tra **`Class<?>` identity** khi có thể.

## 14. TCCL, context switching và plugin discovery

**Thread Context ClassLoader (TCCL)** là một trường trên `Thread` để framework ở loader cha có thể khám phá providers thuộc ngữ cảnh ứng dụng/plugin.

Ví dụ:

```java
// Trích đoạn:
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);
    ServiceLoader<MyService> services =
            ServiceLoader.load(MyService.class);
    services.findFirst().ifPresent(service -> service.start());
} finally {
    thread.setContextClassLoader(previous);
}
```

Đây là **trích đoạn**, `MyService`, `pluginLoader` và `start()` là API do ứng dụng cung cấp. `ServiceLoader.load(S.class)` dùng TCCL; **`Class.forName("p.Impl")` không tự dùng TCCL**.

### Vì sao TCCL dễ gây “rò rỉ cô lập”?

Nếu webapp/plugin A đặt TCCL của một worker thread thành `Loader-A` rồi không restore:
- Request tiếp theo thuộc B có thể tìm provider của A.
- Worker thread lâu sống có thể giữ **strong reference** tới Loader-A.
- Sau undeploy, class loader cũ có thể chưa đủ điều kiện GC/unloading.

Do đó phải dùng `try/finally` khi tự thay TCCL, và container phải quản lý thread pool/worker lifecycle đúng cách.

## 15. “Cùng package” vẫn có thể là hai runtime packages

Ở JVM, cùng tên package bằng chuỗi **không đủ** để có quyền package-private giữa hai class do loaders khác nhau định nghĩa. Hai class có thể khai báo:

```java
package example.impl;
```

nhưng một class thuộc `Loader-A`, class kia thuộc `Loader-B`. Chúng không tự trở thành cùng runtime package có quyền package-private access. Trong hệ named modules, runtime package identity còn liên quan module.

Điều này gây ra tình huống:
- Compile cùng source tree thì package-private hoạt động.
- Khi deploy vào container với class loaders tách biệt, runtime báo `IllegalAccessError` hoặc lỗi liên kết/truy cập tương ứng.
- Tên package và tên class trên stack trace vẫn giống, khiến khó tìm nguyên nhân.

**Giải pháp:** không thiết kế plugin boundary dựa trên package-private access xuyên loader; dùng public shared API hoặc đặt các class phải chung runtime package vào cùng defining loader/module theo cấu hình phù hợp.

## 16. ModuleLayer isolation khác ClassLoader isolation

JPMS (Java 9+) cung cấp:
- **Module** và `module-info.java`: `requires`, `exports`, `opens`, `uses`, `provides`.
- **ModuleLayer**: grouping của modules được resolve trong runtime configuration.
- **ClassLoader mapping**: một layer có thể dùng một hoặc nhiều loaders.

Không phải mọi module có loader riêng. Một loader có thể định nghĩa classes của nhiều modules. Và module readability/encapsulation **không phải thay cho process sandbox**.

```text
Boot layer
  ├── host.app
  └── shared.api
        │
        ▼
Child layer for plugins
  ├── plugin.one  ─┐
  └── plugin.two  ─┴─ một hoặc nhiều ClassLoaders
```

Module A có thể đọc B nhưng không được access package B chưa export. Ngược lại, có `Class<?>` của class từ một module khác cũng không tự cho phép reflection private nếu package không open.

### Dấu hiệu nhận diện

- `type.getModule().getName()`: tên module hoặc `null` nếu unnamed module.
- `type.getModule().getLayer()`: layer của named module khi có.
- `type.getClassLoader()`: defining loader.
- `callerModule.canRead(targetModule)`: readability.
- `targetModule.isExported(packageName, callerModule)` / `isOpen(...)`: quyền runtime package.

**Đừng kết luận một plugin “được sandbox” chỉ vì nó ở child layer.** Cơ chế Java module không cung cấp cách ly memory/process hay chặn tùy ý filesystem/network.

## 17. Process isolation: static state reset nhưng external state có thể còn

Một test runner chạy mỗi test class trong JVM process riêng có các lợi thế:
- ClassLoader graph bắt đầu mới; classes và static fields của process cũ không còn được chia sẻ.
- Các system properties, installed runtime hooks, cached singletons của Java process mới thường bắt đầu theo cấu hình JVM đó.
- JVM crash trong worker này thường không kéo theo heap của worker khác (dù build có thể báo lỗi).
- Có thể chọn JVM arguments khác, heap limit khác.

Nhưng:
- Hai tests vẫn có thể ghi **cùng database schema**.
- Hai processes có thể tranh **cùng TCP port**, file lock hoặc thư mục temp cố định.
- Cùng OS user/credentials có thể truy cập resources ngoài process.
- Một subprocess do test cũ tạo ra có thể còn sống nếu cleanup sai.
- Process mới tốn CPU/memory và startup latency.

Vì vậy, process isolation là **bức tường đối với Java heap và class identity của process**, không phải transaction rollback tự động cho external systems.

### OS container còn là tầng khác

Docker/Kubernetes container là cơ chế cô lập ở mức **OS/runtime deployment**, có thể bao gồm namespace/network/mount/cgroup restrictions. Một container có thể chứa một hoặc nhiều processes tùy thiết kế, nhưng thông lệ JVM service thường chạy một process chính.

Nếu hai JVM processes nằm trong cùng container, chúng vẫn là **hai processes**; nếu hai JVM chạy ở hai containers khác nhau, chúng còn có những biên OS khác. Tuy nhiên, cả hai containers vẫn có thể mount cùng volume hoặc nói chuyện với cùng database.

**Không đánh đồng** “application container” (Tomcat/servlet container) với “OS container” (Docker): từ “container” được dùng cho hai loại hệ thống rất khác nhau.

## 18. Hệ plugin: quyết định cái gì cần chia sẻ

Một plugin architecture bền vững thường chia class thành:

| Thành phần | Vị trí gợi ý | Lý do |
|---|---|---|
| Java platform API | Bootstrap/platform loaders | Cùng JVM platform type |
| Shared SPI/interface/DTO | Host/common parent loader hoặc shared module | Runtime identity ổn định ở biên |
| Plugin implementation | Plugin-specific loader/module | Cho phép phiên bản riêng |
| Third-party libraries riêng | Loader plugin khi cần isolation | Tránh version conflicts với plugin khác |
| Libraries thật sự dùng chung | Common loader có chính sách version rõ | Giảm duplication; dễ gây shared state |
| Credentials/config | Quản lý theo deployment boundary | Không coi ClassLoader là nơi cất secret |
| Lifecycle resources | Plugin owner/manager | Stop/close/unregister rõ ràng |

Không đưa mọi thứ lên parent: như vậy làm mất khả năng phiên bản riêng và có thể gây library conflicts. Cũng không đẩy mọi thứ xuống child: nếu child định nghĩa lại service interface chung, khả năng cast và discovery sẽ hỏng.

## 19. Vòng đời loader và nguy cơ rò rỉ khi redeploy

Khi webapp/plugin được redeploy, loader cũ có thể vẫn reachable qua TCCL, `ThreadLocal`, listener của servlet container, scheduled executor, service provider cache hoặc references tới `Class<?>`/implementation. Shutdown cần quiesce requests, unregister callbacks, stop workers, restore context trên đúng threads, close I/O và loader khi thích hợp, rồi bỏ strong references. Nếu loaded-class count/metaspace tăng, kiểm tra **retaining paths** bằng heap/thread diagnostics; `URLClassLoader.close()` và một lần GC không chứng minh class đã unload.

## 20. Những lỗi thường gặp theo ranh giới cô lập

| Triệu chứng | Giả thuyết có giá trị kiểm tra |
|---|---|
| Test chạy riêng pass nhưng cả suite fail | Static/shared mutable state, thứ tự tests, external resource pollution |
| Một webapp cast lỗi với class từ app khác | Cùng tên nhưng khác defining ClassLoaders |
| ServiceLoader không tìm thấy provider | TCCL/explicit loader sai, thiếu `META-INF/services` hoặc `provides` |
| ClassCastException dù tên lớp giống hệt | Defining loaders khác nhau; không so string name |
| IllegalAccessError khi tách plugin | Runtime package/loader/module access khác lúc compile |
| NoSuchMethodError khi container chứa nhiều bản lib | Parent/shared vs webapp dependency khác compile-time ABI |
| Đổi JAR rồi chạy vẫn thấy behavior cũ | Loader cũ đã định nghĩa class; chưa redeploy/restart đúng |
| Không xóa được JAR sau undeploy | Loader/JarFile/file handle khác chưa đóng |
| Redeploy nhiều lần metaspace tăng | Loader còn reachable qua thread/TCCL/cache/listeners |
| Gradle test static state tồn tại giữa classes | Nhiều test classes dùng lại một JVM worker |
| Test methods chạy song song gây race | Nhiều threads dùng chung object/static/external state |
| Tests chạy ở JVM khác vẫn tranh port | Process isolation không tách external resources |
| Reflection bị InaccessibleObjectException | JPMS package không được opens phù hợp |
| Class.forName trong host không thấy plugin | Dùng loader của caller thay vì plugin loader/TCCL |

**Đừng giải quyết mọi lỗi bằng cách đổi thành child-first.** Nếu vấn đề là external shared database, concurrency hoặc class loader leak, thay delegation không xử lý nguyên nhân.

## 21. Checklist nhận diện mô hình cô lập trong một dự án thực

Khi xem một server/test suite không rõ kiến trúc:

1. **Kiểm tra process boundary.** Các tác vụ có cùng PID không? Build tool có forked JVM workers không? Xem Gradle `forkEvery`/`maxParallelForks` hoặc Surefire `forkCount`/`reuseForks`.
2. **Kiểm tra thread boundary.** Test runner chạy parallel bằng threads hay processes? TCCL có được đặt riêng theo task không?
3. **Kiểm tra test instance lifecycle.** JUnit `PER_METHOD` hay `PER_CLASS`? Điều này chỉ quyết định test object lifecycle, không tự reset static.
4. **Kiểm tra loader graph.** In defining loader của service API, plugin implementation và dependency nghi vấn.
5. **Kiểm tra delegation order.** Parent-first hay child-first? Có whitelist cho platform/shared APIs không?
6. **Kiểm tra runtime type identity.** Trong cùng JVM, so `Class<?>` bằng `==`; tên giống không đủ.
7. **Kiểm tra JPMS graph.** Type ở module/layer nào? `requires`, `exports`, `opens` thế nào?
8. **Kiểm tra classpath/code source.** Tệp class đến từ `WEB-INF/lib`, common/lib, plugin jar hay test runtime classpath? Có shadowing không?
9. **Kiểm tra shared mutable state.** Static caches, singleton, ThreadLocal, global registries, filesystem, DB, ports.
10. **Kiểm tra lifecycle.** Sau test/undeploy, loader, threads, callbacks và resource handles có được dọn không?
11. **Tạo test nhỏ kiểm chứng giả thuyết.** Hai loaders độc lập + shared API; hai JVM processes; JUnit isolated instances; đổi mỗi một biến mỗi lần.
12. **Đọc tài liệu runner/container đúng phiên bản.** Cấu hình defaults có thể thay đổi; đừng lấy giả định từ một framework khác.

## 22. Mini decision tree: tình huống này bị cô lập đến mức nào?

```text
Có hai execution contexts X và Y
            │
            ▼
Có cùng PID?
   ├─ Không → JVM process state tách nhau
   │           └─ vẫn kiểm tra shared files/DB/network
   └─ Có
        │
        ▼
X và Y dùng cùng runtime Class<?> cho type nghi vấn?
   ├─ Có → cùng static state của type đó
   │        └─ test instances/threads vẫn có thể riêng
   └─ Không
        │
        ▼
Khác defining ClassLoaders?
   ├─ Có → class namespace/static state của type đó tách
   │        └─ shared API/resources/process vẫn có thể chung
   └─ Cần kiểm tra Module/Layer hoặc class thật đã resolve
        │
        ▼
Kiểm tra TCCL, parent delegation và code source
            │
            ▼
Nếu vướng access:
   kiểm tra module readability, exports, opens
            │
            ▼
Nếu vướng state:
   kiểm tra static, ThreadLocal, cache, external resources
```

Sơ đồ là **quy trình chẩn đoán**, không phải thuật toán Java chạy ngầm. Ví dụ trong nhiều JVM processes, không thể so sánh hai `Class<?>` objects trực tiếp vì chúng ở hai heaps khác nhau.

## 23. Câu hỏi tự kiểm tra

1. **Hai webapps Tomcat cùng JVM có thể có hai bản runtime class cùng tên không?** Có, nếu hai webapp loaders riêng định nghĩa chúng.
2. **Parent-first có luôn là thứ tự mặc định của webapp loader Tomcat không?** Không; webapp loader của Tomcat có local-first cho nhiều app classes và ngoại lệ quan trọng.
3. **JVM phân biệt hai class cùng binary name dựa trên gì?** Binary name và defining loader.
4. **Cùng loader object được gọi loadClass có bảo đảm nó là defining loader không?** Không, parent có thể định nghĩa class.
5. **Hai plugin instances có static state riêng chỉ vì là hai objects?** Không; phải xem static field thuộc runtime class nào.
6. **Hai defining loaders riêng có thể giữ hai static fields riêng của cùng tên class không?** Có.
7. **Làm thế nào host nhận plugin object từ hai loaders khác nhau?** Qua shared interface/API do loader chung định nghĩa, hoặc boundary dữ liệu thích hợp.
8. **JUnit PER_METHOD có reset static fields không?** Không.
9. **JUnit chạy tests song song bằng threads có tách JVM heap không?** Không.
10. **Gradle maxParallelForks chủ yếu điều khiển gì?** Số forked test JVM worker processes có thể chạy song song.
11. **Gradle forkEvery=1 gần với cô lập theo test method hay test class?** Test class/definition, không mặc nhiên per-method.
12. **Maven Surefire forkCount=0 đồng nghĩa gì về process?** Không tạo forked test JVM theo cấu hình đó; cần xem loader strategy của phiên bản plugin.
13. **Hai JVM riêng có chia sẻ static fields không?** Không, nhưng có thể dùng chung DB/files/ports.
14. **TCCL có phải defining loader của mọi class do thread chạy không?** Không; nó chỉ là loader ngữ cảnh cho APIs/frameworks dùng nó.
15. **Đóng URLClassLoader có bảo đảm class unload ngay không?** Không.
16. **OS container và servlet container là cùng khái niệm không?** Không.
17. **Một ModuleLayer tự tạo có phải sandbox chống file/network không?** Không.
18. **Tại sao hai class cùng tên không cast được dù cùng bytecode?** Defining loaders khác nhau làm runtime type identity khác nhau.
19. **Nếu test fail chỉ khi chạy cả suite, nên kiểm tra gì trước?** Shared static/external state, test order, runner process/thread reuse, class loader boundaries.
20. **Dữ liệu nào hỗ trợ chẩn đoán hiệu quả nhất?** PID, thread, Class object/defining loader, module/layer, TCCL, class source và runner configuration.

## 24. Tài liệu chuẩn và nguồn đọc tiếp

- [Java SE 17 — ClassLoader API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, `loadClass`, `findLoadedClass` và defining loaders.
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): loader dùng URL, close và tài nguyên JAR.
- [JVMS 17 — Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): class identity, runtime packages và loading.
- [Java SE 17 — ModuleLayer API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html): module layers, module-to-loader mapping.
- [Apache Tomcat — Class Loader How-To](https://tomcat.apache.org/tomcat-11.0-doc/class-loader-howto.html): Common/Webapp class loaders, WEB-INF và delegation exceptions.
- [Gradle — Testing in Java & JVM Projects](https://docs.gradle.org/current/userguide/java_testing.html): forked JVM, `maxParallelForks`, `forkEvery` và test workers.
- [Maven Surefire — Class Loading and Forking](https://maven.apache.org/surefire/maven-surefire-plugin/examples/class-loading.html): chọn class loader strategy và fork modes.
- [JUnit 5 User Guide — Test Instance Lifecycle](https://docs.junit.org/5.12.0/user-guide/#writing-tests-test-instance-lifecycle): `PER_METHOD`/`PER_CLASS` và test object state.

**Kết luận:** Nhận diện mô hình cô lập trong container và test runner là việc xác định **ranh giới cụ thể của class definitions, static state, module access, threads, processes và external resources**. Với Java plugins, đối chiếu **defining ClassLoader** và shared API trước; với tests, phân biệt **test instance lifecycle** khỏi **forked JVM process**. Không xem bất kỳ cơ chế đơn lẻ nào là cô lập tuyệt đối hay giải pháp bảo mật thay thế.


</details>

- [Quay lại đầu trang](#back-to-top)
